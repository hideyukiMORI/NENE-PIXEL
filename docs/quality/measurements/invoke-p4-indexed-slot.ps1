[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ManifestPath,
    [Parameter(Mandatory = $true)][string]$SlotId
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'p4-indexed-preflight.ps1')
. (Join-Path $PSScriptRoot 'android-window-state.ps1')
# S1 live device-state contract. It is dot-sourced when present; every call site first asserts that the
# contract functions exist, so a missing or renamed contract fails closed at the first device admission
# instead of silently skipping the live identity comparison.
$script:P4DeviceStateScriptPath = Join-Path $PSScriptRoot 'p4-indexed-device-state.ps1'
if (Test-Path -LiteralPath $script:P4DeviceStateScriptPath -PathType Leaf) { . $script:P4DeviceStateScriptPath }
# S5 device lanes. The wrapper needs the lane PLAN (never a literal) for two cleanup-phase duties it
# owns rather than the collector: the derived collector bound (protocol v5 instrumentation budget) and
# the private-file quarantine. Both call sites assert the contract functions first.
$script:P4DeviceLanesScriptPath = Join-Path $PSScriptRoot 'p4-indexed-device-lanes.ps1'
if (Test-Path -LiteralPath $script:P4DeviceLanesScriptPath -PathType Leaf) { . $script:P4DeviceLanesScriptPath }

# Fixed slot budgets. The slot deadline is started + collector timeout + cleanup reserve + analysis
# budget; cleanup must finish before the analysis budget begins or the slot is INVALID. The collector
# timeout is the DERIVED bound of Get-P4CollectorBudget for the instrumentation lanes (protocol v5:
# the instrumentation itself keeps the full `timeout_seconds`), and `timeout_seconds` for host/frame.
$script:P4CleanupReserveSeconds = 90
$script:P4AnalysisTimeoutSeconds = 120
$script:P4CaptureDrainSeconds = 5
$script:P4CaptureSealSchema = 'nene-pixel-p4-capture-seal-v1'
$script:P4QuiescenceSchema = 'nene-pixel-p4-quiescence-v1'
$script:P4WorktreeStateSchema = 'nene-pixel-p4-worktree-state-v1'
$script:P4RestorationSchema = 'nene-pixel-p4-restoration-v1'
$script:P4AdmissionRefusalSchema = 'nene-pixel-p4-admission-refusal-v1'
# Shared-quiescence command-line markers. A match outside this process tree refuses admission.
$script:P4QuiescencePatterns = @('GradleDaemon', 'GradleWrapperMain', 'gradlew',
    'measure-m2-frame.ps1', 'collect-p4-indexed-slot.ps1', 'am instrument')
# Exactly the files this wrapper itself writes after the capture seal, and nothing else: every other
# byte in the slot directory belongs to the sealed capture. The restoration directory `restore/` is
# created after sealing, so it needs no exclusion rule.
$script:P4PostSealNames = [Collections.Generic.HashSet[string]]::new(
    [string[]]@('capture-seal.json', 'restoration.json', 'analysis.json', 'completed.json', 'invalid.json',
        'worktree-after.json', 'analyzer.log', 'analyzer.log.launcher.ps1', 'analyzer.log.invocation.json'),
    [StringComparer]::OrdinalIgnoreCase)

function Assert-P4DeviceLaneContract {
    foreach ($name in @('Get-P4DeviceLanePlan', 'Get-P4CollectorBudget', 'Invoke-P4PrivateFileQuarantine',
            'Assert-P4CollectorBoundWithinCap')) {
        if ($null -eq (Get-Command -Name $name -ErrorAction SilentlyContinue)) {
            throw "The S5 device-lane contract is unavailable: $name"
        }
    }
}

function Get-P4SlotDeviceContext {
    # The same four-field context the collector builds, so the wrapper's own adb work travels the same
    # bounded path and lands its logs inside the slot directory (and therefore inside the capture seal).
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Manifest,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Slot,
        [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$Directory
    )
    return [ordered]@{
        repository_root = [IO.Path]::GetFullPath($Manifest.roles[$Slot.role].worktree)
        output_directory = [IO.Path]::GetFullPath($Directory)
        adb_path = [IO.Path]::GetFullPath($Manifest.tools.adb.path)
        serial = [string]$Manifest.device.serial
    }
}

function Get-P4SlotCollectorBudget {
    <#
        The wrapper's outer (Job) bound for the collector. Protocol v5 bounds one instrumentation
        invocation at `timeout_seconds` and bounds install/compile/native calls separately; giving the
        whole collector only `timeout_seconds` would let the outer kill precede the inner bound. The
        instrumentation lanes therefore get the bound derived from their own plan; host and frame keep
        `timeout_seconds` (protocol:362-374 for frame, where `timeout_seconds` is itself derived from
        the slot's operation count by Get-P4FrameWrapperBound).
    #>
    param($Manifest, $Slot)
    Assert-P4DeviceLaneContract
    if ($Slot.lane -ceq 'host') {
        $hostBudget = [ordered]@{
            schema = 'nene-pixel-p4-collector-budget-v1'; lane = 'host'; slot_id = [string]$Slot.id;
            derived = $false; timeout_seconds = [int]$Slot.timeout_seconds; instrumentation_seconds = 0;
            install_seconds = 0; dexopt_seconds = 0; probe_count = 0; probe_seconds = 0;
            private_capture_seconds = 0; reserve_seconds = 0;
            cap_seconds = $script:P4BoundedNativeCapSeconds;
            collector_timeout_seconds = [int]$Slot.timeout_seconds }
        # Same ceiling check as every other lane: refuse before reservation, never at the collector's
        # Invoke-BoundedNativeCommand parameter binding, which would already have cost the attempt.
        Assert-P4CollectorBoundWithinCap -Budget $hostBudget
        return [ordered]@{ plan = $null; budget = $hostBudget }
    }
    $plan = Get-P4DeviceLanePlan -Manifest $Manifest -Slot $Slot
    $budget = $plan.collector_budget
    if ([int]$budget.collector_timeout_seconds -lt [int]$Slot.timeout_seconds) {
        throw "The derived collector bound for $($Slot.id) is shorter than its instrumentation bound."
    }
    return [ordered]@{ plan = $plan; budget = $budget }
}

function Assert-P4DeviceStateContract {
    foreach ($name in @('Get-P4LiveDeviceState', 'Assert-P4DeviceStateMatches')) {
        if ($null -eq (Get-Command -Name $name -ErrorAction SilentlyContinue)) {
            throw "The live device-state contract is unavailable: $name"
        }
    }
}

function Invoke-P4CheckedAdb {
    param($Manifest, [string]$Directory, [string]$Name, [string[]]$Arguments, [int]$Timeout = 30)
    $result = Invoke-BoundedNativeCommand -RepositoryRoot $Manifest.roles.candidate.worktree `
        -ExecutablePath $Manifest.tools.adb.path -NativeArguments (@('-s', $Manifest.device.serial) + $Arguments) `
        -LogPath (Join-Path $Directory "$Name.log") -TimeoutSeconds $Timeout
    if ($result.ExitCode -ne 0) { throw "ADB $Name failed with exit $($result.ExitCode)." }
    return ($result.OutputLines -join "`n").Trim()
}

function Get-P4ManifestPackages {
    # The remote packages are derived from the verified APK identities, never from literals. The
    # manifest keeps them per role at `roles.<role>.artifacts.<kind>`, and both roles' APKs are
    # installed during the experiment, so both role inventories are scanned.
    param($Manifest)
    if ($null -eq $Manifest -or -not $Manifest.Contains('roles')) {
        throw 'The manifest does not declare roles; remote packages cannot be derived.'
    }
    $packages = [Collections.Generic.SortedSet[string]]::new([StringComparer]::Ordinal)
    foreach ($role in @('baseline', 'candidate')) {
        if (-not $Manifest.roles.Contains($role)) {
            throw "The manifest does not declare the $role role; remote packages cannot be derived."
        }
        $source = $Manifest.roles[$role]
        if ($null -eq $source -or -not ($source -is [Collections.IDictionary]) -or -not $source.Contains('artifacts')) {
            throw "Role $role does not declare artifacts; remote packages cannot be derived."
        }
        foreach ($kind in @($source.artifacts.Keys)) {
            $artifact = $source.artifacts[$kind]
            if ($null -eq $artifact -or -not ($artifact -is [Collections.IDictionary])) {
                throw "Artifact record is not an object: $role.$kind"
            }
            foreach ($field in @('target_package', 'test_package')) {
                if (-not $artifact.Contains($field)) { throw "Artifact $role.$kind does not declare $field." }
                $value = [string]$artifact[$field]
                if ($value -ceq 'none') { continue }
                if ($value -cnotmatch '^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$') {
                    throw "Artifact $role.$kind declares an invalid $field : $value"
                }
                $packages.Add($value) | Out-Null
            }
        }
    }
    if ($packages.Count -eq 0) { throw 'No remote package could be derived from the manifest artifacts.' }
    return ([string[]]$packages)
}

function Assert-P4RemotePackageAbsent {
    # One shared rule for every absence check: `adb shell pidof <pkg>` must exit 1 with empty output.
    param($Manifest, $Slot, [string]$Directory, [string]$LogName, [string]$Package)
    $result = Invoke-BoundedNativeCommand -RepositoryRoot $Manifest.roles[$Slot.role].worktree `
        -ExecutablePath $Manifest.tools.adb.path -LogPath (Join-Path $Directory "$LogName.log") `
        -NativeArguments @('-s', $Manifest.device.serial, 'shell', 'pidof', $Package) -TimeoutSeconds 10
    if ($result.ExitCode -ne 1 -or -not [string]::IsNullOrWhiteSpace((@($result.OutputLines) -join ''))) {
        throw "Remote process absence was not confirmed (pidof exit $($result.ExitCode)): $Package"
    }
}

function Get-P4OriginalDeviceSettings {
    param($Manifest, [string]$Directory)
    $window = Invoke-P4CheckedAdb $Manifest $Directory 'original-window' @('shell', 'dumpsys', 'window')
    $numeric = Invoke-P4CheckedAdb $Manifest $Directory 'original-numeric-rotation' @('shell', 'settings', 'get', 'system', 'user_rotation')
    $awake = Invoke-P4CheckedAdb $Manifest $Directory 'original-stay-awake' @('shell', 'settings', 'get', 'global', 'stay_on_while_plugged_in')
    $rotation = ConvertFrom-NeneWindowRotationState $numeric $window
    if ($awake -cnotmatch '^(null|[0-7])$' -or $rotation.numeric_user_rotation -ne $rotation.reported_user_rotation -or
        ($rotation.mode -eq 'locked' -and $rotation.current_rotation -ne $rotation.numeric_user_rotation)) {
        throw 'Original device settings are ambiguous.'
    }
    return [ordered]@{ rotation = $rotation; stay_awake = $awake }
}

function Restore-P4OriginalDeviceSettings {
    param($Manifest, [string]$Directory, $Original)
    $errors = [Collections.Generic.List[string]]::new()
    try {
        Invoke-P4CheckedAdb $Manifest $Directory 'restore-rotation-value' @('shell', 'wm', 'user-rotation', 'lock', "$($Original.rotation.numeric_user_rotation)") | Out-Null
        if ($Original.rotation.mode -eq 'free') {
            Invoke-P4CheckedAdb $Manifest $Directory 'restore-rotation-mode' @('shell', 'wm', 'user-rotation', 'free') | Out-Null
        }
    } catch { $errors.Add($_.Exception.Message) }
    try {
        $awakeCommand = if ($Original.stay_awake -eq 'null') {
            @('shell', 'settings', 'delete', 'global', 'stay_on_while_plugged_in')
        } else { @('shell', 'settings', 'put', 'global', 'stay_on_while_plugged_in', $Original.stay_awake) }
        Invoke-P4CheckedAdb $Manifest $Directory 'restore-stay-awake' $awakeCommand | Out-Null
    } catch { $errors.Add($_.Exception.Message) }
    try {
        $check = Get-P4OriginalDeviceSettings $Manifest $Directory
        if ($check.stay_awake -cne $Original.stay_awake -or $check.rotation.mode -cne $Original.rotation.mode -or
            $check.rotation.numeric_user_rotation -ne $Original.rotation.numeric_user_rotation -or
            $check.rotation.reported_user_rotation -ne $Original.rotation.reported_user_rotation -or
            ($Original.rotation.mode -eq 'locked' -and $check.rotation.current_rotation -ne $Original.rotation.current_rotation)) {
            throw 'Original rotation/stay-awake restoration could not be verified.'
        }
    } catch { $errors.Add($_.Exception.Message) }
    if ($errors.Count -gt 0) { throw ($errors -join '; ') }
}

function Stop-P4RemoteProcesses {
    param($Manifest, $Slot, [string]$Directory)
    $packages = @(Get-P4ManifestPackages $Manifest)
    $errors = [Collections.Generic.List[string]]::new()
    $i = 0
    foreach ($package in $packages) {
        $i++
        try {
            Invoke-P4CheckedAdb $Manifest $Directory "stop-remote-$i" @('shell', 'am', 'force-stop', $package) 10 | Out-Null
            Assert-P4RemotePackageAbsent -Manifest $Manifest -Slot $Slot -Directory $Directory `
                -LogName "remote-absence-$i" -Package $package
        } catch { $errors.Add($_.Exception.Message) }
    }
    if ($errors.Count -gt 0) { throw ($errors -join '; ') }
}

function Get-P4RemotePackageAbsence {
    param($Manifest, $Slot, [string]$Directory, [string]$Stage)
    $packages = @(Get-P4ManifestPackages $Manifest)
    $observed = [ordered]@{}
    $errors = [Collections.Generic.List[string]]::new()
    $i = 0
    foreach ($package in $packages) {
        $i++
        try {
            Assert-P4RemotePackageAbsent -Manifest $Manifest -Slot $Slot -Directory $Directory `
                -LogName "$Stage-absence-$i" -Package $package
            $observed[$package] = 'absent'
        } catch {
            $observed[$package] = 'unconfirmed'
            $errors.Add($_.Exception.Message)
        }
    }
    return [ordered]@{ packages = $packages; observed = $observed; errors = @($errors) }
}

function Get-P4HostProcessQuiescence {
    # Shared build/measurement quiescence on this host. The current process tree (ancestors of this
    # process and every descendant of it) is excluded; anything else that matches refuses admission.
    $processes = @(Get-CimInstance -ClassName Win32_Process -Property ProcessId, ParentProcessId, CommandLine)
    $byId = @{}
    foreach ($process in $processes) { $byId[[int]$process.ProcessId] = $process }
    $own = [Collections.Generic.HashSet[int]]::new()
    $own.Add($PID) | Out-Null
    $grew = $true
    while ($grew) {
        $grew = $false
        foreach ($process in $processes) {
            $id = [int]$process.ProcessId
            $parent = [int]$process.ParentProcessId
            if ($id -ne $parent -and -not $own.Contains($id) -and $own.Contains($parent)) {
                $own.Add($id) | Out-Null
                $grew = $true
            }
        }
    }
    $cursor = $PID
    for ($depth = 0; $depth -lt 64; $depth++) {
        if (-not $byId.ContainsKey($cursor)) { break }
        $parent = [int]$byId[$cursor].ParentProcessId
        if ($parent -le 0 -or $parent -eq $cursor -or -not $byId.ContainsKey($parent)) { break }
        $own.Add($parent) | Out-Null
        $cursor = $parent
    }
    $offending = [Collections.Generic.List[object]]::new()
    foreach ($process in $processes) {
        $id = [int]$process.ProcessId
        if ($own.Contains($id)) { continue }
        $commandLine = [string]$process.CommandLine
        if ([string]::IsNullOrEmpty($commandLine)) { continue }
        foreach ($pattern in $script:P4QuiescencePatterns) {
            if ($commandLine.IndexOf($pattern, [StringComparison]::Ordinal) -ge 0) {
                $offending.Add([ordered]@{ process_id = $id; pattern = $pattern; command_line = $commandLine })
                break
            }
        }
    }
    return [ordered]@{ patterns = $script:P4QuiescencePatterns; own_tree_process_ids = @($own | Sort-Object);
        offending = @($offending); checked_utc = [datetime]::UtcNow.ToString('o') }
}

function Get-P4WorktreeState {
    param([string]$Worktree, [string]$Stage, [string]$Directory)
    $root = [IO.Path]::GetFullPath($Worktree)
    $head = (& git -C $root rev-parse HEAD) 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Unable to read the worktree HEAD: $root" }
    $status = @(& git -C $root status --porcelain)
    if ($LASTEXITCODE -ne 0) { throw "Unable to read the worktree status: $root" }
    $state = [ordered]@{ schema = $script:P4WorktreeStateSchema; stage = $Stage; worktree = $root;
        head = ([string]$head).Trim(); status_lines = @($status); captured_utc = [datetime]::UtcNow.ToString('o') }
    Write-NewInvocationFile (Join-Path $Directory "worktree-$Stage.json") ($state | ConvertTo-Json -Depth 8)
    return $state
}

function Assert-P4WorktreeState {
    param($State, [string]$ExpectedCommit, [string]$Context)
    if ([string]$State.head -cne $ExpectedCommit) {
        throw "Worktree HEAD does not match the reserved build commit ($Context): $($State.head)"
    }
    if (@($State.status_lines).Count -ne 0) {
        throw "Worktree is not clean ($Context): $(@($State.status_lines) -join ' | ')"
    }
}

function Get-P4SealedFileRecords {
    param([string]$Directory, [string]$Prefix = '', $ExcludedNames = $null)
    $root = [IO.Path]::GetFullPath($Directory)
    if (-not $root.EndsWith([string][IO.Path]::DirectorySeparatorChar)) { $root += [IO.Path]::DirectorySeparatorChar }
    $records = [Collections.Generic.List[object]]::new()
    foreach ($entry in @(Get-ChildItem -LiteralPath $Directory -Recurse -Force)) {
        if (($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "A reparse point cannot be sealed: $($entry.FullName)"
        }
        if ($entry.PSIsContainer) { continue }
        $full = [IO.Path]::GetFullPath($entry.FullName)
        if (-not $full.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) {
            throw "A captured file escaped the sealed directory: $full"
        }
        $relative = $full.Substring($root.Length).Replace([IO.Path]::DirectorySeparatorChar, '/')
        if ($null -ne $ExcludedNames -and $ExcludedNames.Contains($relative)) { continue }
        $records.Add([ordered]@{ relative_path = "$Prefix$relative"; byte_count = [long]$entry.Length;
            sha256 = Get-FileSha256 $full })
    }
    return $records
}

function New-P4CaptureSeal {
    param([string]$Directory, [string]$SlotId, $Slot, [bool]$RequireFrameSlot)
    $records = [Collections.Generic.List[object]]::new()
    foreach ($record in @(Get-P4SealedFileRecords -Directory $Directory -Prefix '' `
                -ExcludedNames $script:P4PostSealNames)) {
        $records.Add($record)
    }
    $externals = [Collections.Generic.List[object]]::new()
    if ($Slot.lane -ceq 'frame') {
        $framePath = Join-Path $Directory 'frame-slot.json'
        if (Test-Path -LiteralPath $framePath -PathType Leaf) {
            $frameSlot = Get-Content -LiteralPath $framePath -Raw | ConvertFrom-Json -AsHashtable
            if (-not $frameSlot.Contains('slot_directory')) { throw 'frame-slot.json does not declare slot_directory.' }
            $frameDirectory = [IO.Path]::GetFullPath([string]$frameSlot.slot_directory)
            if (-not (Test-Path -LiteralPath $frameDirectory -PathType Container)) {
                throw "The declared frame slot directory is missing: $frameDirectory"
            }
            $frameRecords = @(Get-P4SealedFileRecords -Directory $frameDirectory -Prefix 'external/frame-slot/')
            if ($frameRecords.Count -eq 0) { throw "The declared frame slot directory is empty: $frameDirectory" }
            $observed = @{}
            foreach ($record in $frameRecords) {
                $records.Add($record)
                $observed[[string]$record.relative_path] = [string]$record.sha256
            }
            if ($frameSlot.Contains('files')) {
                foreach ($declared in @($frameSlot.files)) {
                    if (-not $declared.Contains('relative_path') -or -not $declared.Contains('sha256')) {
                        throw 'frame-slot.json declares an incomplete file record.'
                    }
                    $key = "external/frame-slot/$([string]$declared.relative_path)"
                    if (-not $observed.ContainsKey($key) -or $observed[$key] -cne [string]$declared.sha256) {
                        throw "The frame collector's declared file disagrees with the sealed bytes: $($declared.relative_path)"
                    }
                }
            }
            $externals.Add([ordered]@{ name = 'frame-slot'; path = $frameDirectory })
        } elseif ($RequireFrameSlot) {
            throw 'The frame collector did not declare its external slot directory.'
        }
    }
    if ($records.Count -eq 0) { throw 'The capture seal would be empty; no capture reached the slot directory.' }
    $sorted = @($records | Sort-Object -Property { [string]$_.relative_path } -CaseSensitive)
    $seal = [ordered]@{ schema = $script:P4CaptureSealSchema; slot_id = $SlotId; lane = $Slot.lane;
        files = $sorted; external_directories = @($externals); sealed_utc = [datetime]::UtcNow.ToString('o') }
    Write-NewInvocationFile (Join-Path $Directory 'capture-seal.json') ($seal | ConvertTo-Json -Depth 8)
    return $seal
}

function Resolve-P4SealedFilePath {
    param($Seal, [string]$SlotDirectory, [string]$RelativePath)
    if ($RelativePath -cmatch '(^/|\\|(^|/)\.\.(/|$)|[\r\n\t])') { throw "Invalid sealed relative path: $RelativePath" }
    if ($RelativePath.StartsWith('external/', [StringComparison]::Ordinal)) {
        $parts = $RelativePath.Split('/', 3)
        if ($parts.Count -ne 3 -or [string]::IsNullOrWhiteSpace($parts[2])) {
            throw "Malformed sealed external path: $RelativePath"
        }
        if ($null -eq $Seal -or -not $Seal.Contains('external_directories')) {
            throw 'The capture seal does not declare its external directories.'
        }
        $match = @(@($Seal.external_directories) | Where-Object { [string]$_.name -ceq $parts[1] })
        if ($match.Count -ne 1) { throw "Sealed external directory is undeclared: $($parts[1])" }
        return (Join-Path ([string]$match[0].path) ($parts[2].Replace('/', [IO.Path]::DirectorySeparatorChar)))
    }
    return (Join-Path $SlotDirectory ($RelativePath.Replace('/', [IO.Path]::DirectorySeparatorChar)))
}

function Assert-P4AnalysisSealAgreement {
    param($Analysis, $Seal, $Slot)
    if ($null -eq $Seal) { throw 'The analyzer ran without a capture seal.' }
    $byPath = @{}
    $shas = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($file in @($Seal.files)) {
        $byPath[[string]$file.relative_path] = [string]$file.sha256
        $shas.Add([string]$file.sha256) | Out-Null
    }
    foreach ($key in @('capture_sha256', 'status_sha256')) {
        if (-not $Analysis.Contains($key)) { continue }
        $value = [string]$Analysis[$key]
        if ($value -cnotmatch '^[0-9a-f]{64}$' -or -not $shas.Contains($value)) {
            throw "The analyzer's $key is not a sealed capture hash."
        }
    }
    if ($Slot.lane -ceq 'frame') {
        if (-not $Analysis.Contains('frame_files')) { throw 'The frame analysis did not declare its sealed frame files.' }
        $files = @($Analysis.frame_files)
        if ($files.Count -eq 0) { throw 'The frame analysis declared no frame files.' }
        foreach ($file in $files) {
            if (-not $file.Contains('relative_path') -or -not $file.Contains('sha256')) {
                throw 'The frame analysis declared an incomplete file record.'
            }
            # Contract: `frame_files[].relative_path` is relative to the FRAME SLOT DIRECTORY named by
            # `frame-slot.json` (S5/S6), which the seal stores under `external/frame-slot/`. The bare
            # relative_path is still accepted so an already-prefixed value cannot silently pass unchecked.
            $matched = $false
            foreach ($candidate in @("external/frame-slot/$([string]$file.relative_path)", [string]$file.relative_path)) {
                if ($byPath.ContainsKey($candidate)) {
                    if ($byPath[$candidate] -cne [string]$file.sha256) {
                        throw "The frame analysis disagrees with the sealed bytes: $($file.relative_path)"
                    }
                    $matched = $true
                    break
                }
            }
            if (-not $matched) { throw "The frame analysis referenced an unsealed file: $($file.relative_path)" }
        }
    }
}

function Assert-P4CompletedChain {
    param([string]$Root, [object[]]$Catalog, [string]$SlotId, [string]$ManifestHash)
    foreach ($prior in $Catalog) {
        if ($prior.id -ceq $SlotId) { break }
        $priorDirectory = Join-Path $Root $prior.id
        $path = Join-Path $priorDirectory 'completed.json'
        if (-not (Test-Path -LiteralPath $path)) { throw "Prior slot is incomplete: $($prior.id)" }
        $result = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json -AsHashtable
        # Lane 5 publication: a required constants revision stops the MERGE, not the collection, so both
        # valid publication verdicts keep the chain running.
        $expected = if ($prior.lane -ceq 'host') { @('valid-descriptive') }
            elseif ($prior.lane -ceq 'frame' -and $prior.runner -ceq 'diagnostic') { @('inconclusive') }
            elseif ($prior.lane -ceq 'publication') { @('valid-constants-retained', 'valid-constants-revision-required') }
            else { @('pass') }
        if ([string]$result.slot_id -cne $prior.id -or [string]$result.verdict -cnotin $expected -or
            [string]$result.status -cne 'completed' -or [string]$result.protocol_id -cne $script:P4ProtocolId -or
            [string]$result.preflight_sha256 -cne $ManifestHash -or
            (Test-Path -LiteralPath (Join-Path $priorDirectory 'invalid.json'))) {
            throw "Prior slot stopped the experiment: $($prior.id)"
        }
        Assert-P4RequiredKeys $result @('capture_seal_sha256', 'analysis_sha256', 'restoration_sha256',
            'worktree_after_sha256') "completed.$($prior.id)"
        $sealPath = Join-Path $priorDirectory 'capture-seal.json'
        if (-not (Test-Path -LiteralPath $sealPath -PathType Leaf) -or
            (Get-FileSha256 $sealPath) -cne [string]$result.capture_seal_sha256) {
            throw "Completed chain drifted: capture seal of $($prior.id)"
        }
        $seal = Get-Content -LiteralPath $sealPath -Raw | ConvertFrom-Json -AsHashtable
        if ([string]$seal.schema -cne $script:P4CaptureSealSchema -or [string]$seal.slot_id -cne $prior.id) {
            throw "Completed chain drifted: capture seal identity of $($prior.id)"
        }
        foreach ($file in @($seal.files)) {
            $sealedPath = Resolve-P4SealedFilePath -Seal $seal -SlotDirectory $priorDirectory `
                -RelativePath ([string]$file.relative_path)
            if (-not (Test-Path -LiteralPath $sealedPath -PathType Leaf)) {
                throw "Completed chain drifted: sealed file is missing ($($prior.id)/$($file.relative_path))"
            }
            $item = Get-Item -LiteralPath $sealedPath
            if ($item.Length -ne [long]$file.byte_count -or (Get-FileSha256 $sealedPath) -cne [string]$file.sha256) {
                throw "Completed chain drifted: sealed file changed ($($prior.id)/$($file.relative_path))"
            }
        }
        $analysisPath = Join-Path $priorDirectory 'analysis.json'
        if (-not (Test-Path -LiteralPath $analysisPath -PathType Leaf) -or
            (Get-FileSha256 $analysisPath) -cne [string]$result.analysis_sha256) {
            throw "Completed chain drifted: analysis of $($prior.id)"
        }
        # The analysis, the seal bytes and the accepted result must all name the same seal.
        $priorAnalysis = Get-Content -LiteralPath $analysisPath -Raw | ConvertFrom-Json -AsHashtable
        if (-not $priorAnalysis.Contains('capture_seal_sha256') -or
            [string]$priorAnalysis.capture_seal_sha256 -cne [string]$result.capture_seal_sha256) {
            throw "Completed chain drifted: analysis capture seal binding of $($prior.id)"
        }
        if ($prior.lane -ceq 'host') {
            if ([string]$result.restoration_sha256 -cne 'not-applicable') {
                throw "Completed chain drifted: host restoration claim of $($prior.id)"
            }
        } else {
            $restorationPath = Join-Path $priorDirectory 'restoration.json'
            if (-not (Test-Path -LiteralPath $restorationPath -PathType Leaf) -or
                (Get-FileSha256 $restorationPath) -cne [string]$result.restoration_sha256) {
                throw "Completed chain drifted: restoration of $($prior.id)"
            }
        }
        $worktreePath = Join-Path $priorDirectory 'worktree-after.json'
        if (-not (Test-Path -LiteralPath $worktreePath -PathType Leaf) -or
            (Get-FileSha256 $worktreePath) -cne [string]$result.worktree_after_sha256) {
            throw "Completed chain drifted: worktree proof of $($prior.id)"
        }
    }
}

function Write-P4AdmissionRefusal {
    # Both pre-reservation gates record a refusal, and neither consumes an attempt: the completed-chain
    # re-verification ('completed-chain-drift') and the admission checks ('admission-check').
    param([string]$Root, $Slot, [string]$ReasonCode, [string]$Reason, [string]$AdmissionDirectory)
    $stamp = [datetime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
    $refusalRoot = Join-Path $Root 'admission-refusals'
    if (-not (Test-Path -LiteralPath $refusalRoot)) { New-Item -ItemType Directory -Path $refusalRoot | Out-Null }
    $text = ([ordered]@{ schema = $script:P4AdmissionRefusalSchema; slot_id = $Slot.id; lane = $Slot.lane;
        attempt_consumed = $false; reason_code = $ReasonCode; reason = $Reason;
        admission_directory = $AdmissionDirectory; refused_utc = [datetime]::UtcNow.ToString('o') }) |
        ConvertTo-Json -Depth 8
    for ($suffix = 0; $suffix -lt 64; $suffix++) {
        $name = if ($suffix -eq 0) { "$($Slot.id)-$stamp.json" } else { "$($Slot.id)-$stamp-$suffix.json" }
        $written = $false
        try { Write-NewInvocationFile (Join-Path $refusalRoot $name) $text; $written = $true }
        catch [IO.IOException] { if ($suffix -eq 63) { throw } }
        if ($written) { return }
    }
}

function Invoke-P4SlotAdmission {
    # Admission runs BEFORE reservation. A refusal consumes no attempt: it creates no slot directory and
    # records `<root>/admission-refusals/<slot>-<utc>.json`. Admission evidence is produced in the
    # immutable staging directory `<root>/admission/<slot>-<utc>/`, which the caller moves into the
    # reserved slot as `<slot>/admission/`; a refused staging directory stays where it is, as evidence.
    param($Manifest, $Slot, [string]$Root)
    $stamp = [datetime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
    $admissionRoot = Join-Path $Root 'admission'
    if (-not (Test-Path -LiteralPath $admissionRoot)) { New-Item -ItemType Directory -Path $admissionRoot | Out-Null }
    $staging = Join-Path $admissionRoot "$($Slot.id)-$stamp"
    New-Item -ItemType Directory -Path $staging -ErrorAction Stop | Out-Null
    try {
        $hostRecord = Get-P4HostProcessQuiescence
        $remoteRecord = if ($Slot.lane -cne 'host') {
            Get-P4RemotePackageAbsence -Manifest $Manifest -Slot $Slot -Directory $staging -Stage 'admission'
        } else { [ordered]@{ packages = @(); observed = [ordered]@{}; errors = @() } }
        $quiescence = [ordered]@{ schema = $script:P4QuiescenceSchema; slot_id = $Slot.id; lane = $Slot.lane;
            host = $hostRecord; remote = $remoteRecord; checked_utc = [datetime]::UtcNow.ToString('o') }
        Write-NewInvocationFile (Join-Path $staging 'quiescence.json') ($quiescence | ConvertTo-Json -Depth 10)
        $problems = @(@($hostRecord.offending) | ForEach-Object { "$($_.pattern) (pid $($_.process_id))" }) +
            @($remoteRecord.errors)
        if ($problems.Count -gt 0) { throw "Shared quiescence was not confirmed: $($problems -join '; ')" }
        $worktree = Get-P4WorktreeState -Worktree $Manifest.roles[$Slot.role].worktree -Stage 'before' -Directory $staging
        Assert-P4WorktreeState -State $worktree -ExpectedCommit $Manifest.roles[$Slot.role].build_commit `
            -Context "slot $($Slot.id) admission"
        if ($Slot.lane -cne 'host') {
            Assert-P4DeviceStateContract
            $state = Get-P4LiveDeviceState -AdbPath $Manifest.tools.adb.path -Serial $Manifest.device.serial `
                -Directory $staging -Stage 'before' -RepositoryRoot $Manifest.roles[$Slot.role].worktree
            Assert-P4DeviceStateMatches -Expected $Manifest.device -Observed $state -Context "slot $($Slot.id) admission" | Out-Null
        }
        return [ordered]@{ directory = $staging }
    } catch {
        $admissionFailure = $_
        Write-P4AdmissionRefusal -Root $Root -Slot $Slot -ReasonCode 'admission-check' `
            -Reason $admissionFailure.Exception.ToString() -AdmissionDirectory $staging
        throw $admissionFailure
    }
}

function Invoke-P4SlotRestoration {
    param($Manifest, $Slot, [string]$Directory, $Original)
    $restoreDirectory = Join-Path $Directory 'restore'
    if (-not (Test-Path -LiteralPath $restoreDirectory)) { New-Item -ItemType Directory -Path $restoreDirectory | Out-Null }
    $errors = [Collections.Generic.List[string]]::new()
    $settingsVerified = $false
    try {
        Restore-P4OriginalDeviceSettings $Manifest $restoreDirectory $Original
        $settingsVerified = $true
    } catch { $errors.Add($_.Exception.Message) }
    $deviceAfterSha = 'not-produced'
    try {
        Assert-P4DeviceStateContract
        $after = Get-P4LiveDeviceState -AdbPath $Manifest.tools.adb.path -Serial $Manifest.device.serial `
            -Directory $restoreDirectory -Stage 'after' -RepositoryRoot $Manifest.roles[$Slot.role].worktree
        Assert-P4DeviceStateMatches -Expected $Manifest.device -Observed $after -Context "slot $($Slot.id) cleanup" | Out-Null
        $deviceAfterSha = Get-FileSha256 (Join-Path $restoreDirectory 'device-state-after.json')
    } catch { $errors.Add($_.Exception.Message) }
    $restored = ($errors.Count -eq 0)
    $record = [ordered]@{ schema = $script:P4RestorationSchema; slot_id = $Slot.id; restored = $restored;
        settings_verified = $settingsVerified; device_after_sha256 = $deviceAfterSha; errors = @($errors);
        restored_utc = [datetime]::UtcNow.ToString('o') }
    $path = Join-Path $Directory 'restoration.json'
    Write-NewInvocationFile $path ($record | ConvertTo-Json -Depth 8)
    return [ordered]@{ restored = $restored; sha256 = (Get-FileSha256 $path);
        message = "Device restoration is not proven: $(@($errors) -join '; ')" }
}

function Read-P4FreshAnalysis {
    param([string]$Path, $Slot, [string]$ManifestHash, [datetime]$StartedUtc, [string]$CaptureSealSha256)
    $file = Get-Item -LiteralPath $Path
    if ($file.CreationTimeUtc -lt $StartedUtc.AddSeconds(-1) -or $file.LastWriteTimeUtc -lt $StartedUtc.AddSeconds(-1)) {
        throw 'Analyzer output is not fresh.'
    }
    $result = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json -AsHashtable
    Assert-P4RequiredKeys $result @('schema', 'protocol_id', 'slot_id', 'preflight_sha256', 'role', 'verdict',
        'created_utc', 'capture_seal_sha256') 'analysis'
    if ($result.protocol_id -cne $script:P4ProtocolId -or $result.slot_id -cne $Slot.id -or
        $result.role -cne $Slot.role -or $result.preflight_sha256 -cne $ManifestHash -or
        ([datetime]$result.created_utc).ToUniversalTime() -lt $StartedUtc.AddSeconds(-1)) {
        throw 'Analyzer identity does not match the reserved slot.'
    }
    # The analyzer must have read the sealed capture, not whatever happened to be on disk.
    if ([string]$result.capture_seal_sha256 -cne $CaptureSealSha256) {
        throw 'The analyzer did not bind the capture seal produced before it ran.'
    }
    $allowed = switch ($Slot.lane) {
        'host' { @('valid-descriptive') }
        'publication' { @('valid-constants-retained', 'valid-constants-revision-required') }
        'frame' { if ($Slot.runner -eq 'diagnostic') { @('inconclusive', 'PERFORMANCE_FAIL') } else { @('pass', 'PERFORMANCE_FAIL') } }
        default { @('pass', 'PERFORMANCE_FAIL') }
    }
    if ($result.verdict -cnotin $allowed) { throw 'Analyzer verdict is incompatible with this lane.' }
    return $result
}

function Invoke-P4IndexedSlot {
    param([string]$ManifestPath, [string]$SlotId)
    $manifestBytes = [IO.File]::ReadAllBytes($ManifestPath)
    $manifestHash = Get-Sha256Hex $manifestBytes
    $manifest = [Text.UTF8Encoding]::new($false, $true).GetString($manifestBytes) | ConvertFrom-Json -AsHashtable
    $root = [IO.Path]::GetFullPath($manifest.output_directory)
    $reserved = Join-Path $root 'preflight.json'
    if (-not (Test-Path -LiteralPath $reserved) -or (Get-FileSha256 $reserved) -cne $manifestHash) {
        throw 'Use the exact reserved preflight manifest.'
    }
    # `-Stage slot` skips the reservation-only checks (live device admission and the Issue/protocol
    # agreement query): this wrapper performs its own live device admission per slot.
    Assert-P4ManifestArtifacts $manifest $manifest.roles.candidate.worktree -Stage 'slot'
    $catalog = @(Get-P4SlotCatalog)
    $selected = @($catalog | Where-Object { $_.id -ceq $SlotId })
    if ($selected.Count -ne 1) { throw 'Unknown slot; no substitute or extra attempt is permitted.' }
    $slot = $selected[0]
    # Derived before any reservation: a plan the manifest cannot describe refuses the slot without
    # consuming it. `$lanePlan` is $null for the host lane, which owns no device plan.
    $budgetRecord = Get-P4SlotCollectorBudget -Manifest $manifest -Slot $slot
    $collectorBudget = $budgetRecord.budget
    $lanePlan = $budgetRecord.plan
    $collectorTimeoutSeconds = [int]$collectorBudget.collector_timeout_seconds
    if ($collectorTimeoutSeconds -lt [int]$slot.timeout_seconds) {
        throw "The collector bound for $SlotId is shorter than its protocol timeout."
    }
    $mutex = [Threading.Mutex]::new($false, 'Local\NenePixelP4EvidenceExclusive')
    $acquired = $false
    $directory = Join-Path $root $SlotId
    try {
        try { $acquired = $mutex.WaitOne(0) }
        catch [Threading.AbandonedMutexException] { $acquired = $true; throw 'Abandoned experiment lease requires review.' }
        if (-not $acquired) { throw 'Another experiment owns the device/build lease.' }
        try { Assert-P4CompletedChain -Root $root -Catalog $catalog -SlotId $SlotId -ManifestHash $manifestHash }
        catch {
            $chainFailure = $_
            Write-P4AdmissionRefusal -Root $root -Slot $slot -ReasonCode 'completed-chain-drift' `
                -Reason $chainFailure.Exception.ToString() -AdmissionDirectory 'not-produced'
            throw $chainFailure
        }
        if (Test-Path -LiteralPath $directory) { throw 'Slot already consumed; retries are prohibited.' }
        $admission = Invoke-P4SlotAdmission -Manifest $manifest -Slot $slot -Root $root
        New-Item -ItemType Directory -Path $directory | Out-Null
        $admissionDirectory = Join-Path $directory 'admission'
        Move-Item -LiteralPath $admission.directory -Destination $admissionDirectory
        $startedUtc = [datetime]::UtcNow
        $slotDeadlineUtc = $startedUtc.AddSeconds($collectorTimeoutSeconds + $script:P4CleanupReserveSeconds +
            $script:P4AnalysisTimeoutSeconds)
        $deviceBeforeSha = if ($slot.lane -ceq 'host') { 'not-applicable' }
            else { Get-FileSha256 (Join-Path $admissionDirectory 'device-state-before.json') }
        $started = [ordered]@{ schema = $script:P4ManifestSchema; slot_id = $SlotId; status = 'started'; attempt = 1;
            preflight_sha256 = $manifestHash; started_utc = $startedUtc.ToString('o');
            collector_timeout_seconds = $collectorTimeoutSeconds;
            protocol_timeout_seconds = [int]$slot.timeout_seconds;
            collector_budget = $collectorBudget;
            cleanup_reserve_seconds = $script:P4CleanupReserveSeconds;
            analysis_timeout_seconds = $script:P4AnalysisTimeoutSeconds;
            slot_deadline_utc = $slotDeadlineUtc.ToString('o');
            admission_directory = 'admission';
            quiescence_sha256 = (Get-FileSha256 (Join-Path $admissionDirectory 'quiescence.json'));
            worktree_before_sha256 = (Get-FileSha256 (Join-Path $admissionDirectory 'worktree-before.json'));
            device_before_sha256 = $deviceBeforeSha }
        Write-NewInvocationFile (Join-Path $directory 'started.json') ($started | ConvertTo-Json -Depth 8)
        $original = $null
        $failure = $null
        $analysisResult = $null
        $seal = $null
        $sealHash = 'not-produced'
        $analysisHash = 'not-produced'
        $worktreeAfterHash = 'not-produced'
        $restorationHash = if ($slot.lane -ceq 'host') { 'not-applicable' } else { 'not-produced' }
        $analysisPath = Join-Path $directory 'analysis.json'
        # Only this wrapper may produce these; a collector that writes one has forged slot evidence.
        $wrapperOwnedNames = @('analysis.json', 'completed.json', 'invalid.json', 'capture-seal.json',
            'restoration.json')
        try {
            if ($slot.lane -cne 'host') { $original = Get-P4OriginalDeviceSettings $manifest $directory }
            foreach ($name in $wrapperOwnedNames) {
                if (Test-Path -LiteralPath (Join-Path $directory $name)) {
                    throw "Wrapper-owned slot evidence exists before collection: $name"
                }
            }
            $execution = Invoke-BoundedNativeCommand -RepositoryRoot $manifest.roles[$slot.role].worktree `
                -ExecutablePath (Join-Path $PSHOME 'pwsh.exe') -LogPath (Join-Path $directory 'collector.log') `
                -TimeoutSeconds $collectorTimeoutSeconds -NativeArguments @('-NoProfile', '-File', $manifest.tools.runner.path,
                    '-ManifestPath', $reserved, '-SlotId', $SlotId, '-OutputDirectory', $directory)
            if ($execution.ExitCode -ne 0) { throw "Collector exited $($execution.ExitCode)." }
            foreach ($name in $wrapperOwnedNames) {
                if (Test-Path -LiteralPath (Join-Path $directory $name)) {
                    throw "Collector attempted to create wrapper-owned slot evidence: $name"
                }
            }
        } catch { $failure = $_.Exception }
        finally {
            # Cleanup always runs, bounded, collecting every error; the analyzer only runs afterwards.
            $cleanupErrors = [Collections.Generic.List[string]]::new()
            if ($slot.lane -cne 'host') {
                try { Stop-P4RemoteProcesses $manifest $slot $directory } catch { $cleanupErrors.Add($_.Exception.Message) }
            }
            # Device private-file quarantine (after the packages are stopped, before the seal, so the
            # record and its adb logs are sealed capture). It runs on success, failure and timeout
            # alike: a slot that died after the test wrote its CSV would otherwise leave the device
            # output in place and make the protocol's fail-if-present reservation refuse every later
            # slot, including the bounded recovery. Nothing is deleted; an existing quarantine throws.
            # Deliberate deviation from the frame lane's recovery quarantine: this one does NOT re-assert
            # Assert-P4RemotePackagesStopped. It runs after Stop-P4RemoteProcesses even when that step
            # failed, because a still-running package is precisely the case where the device output must
            # not be left where the next slot's fail-if-present reservation will find it; `run-as mv` of a
            # closed, already-written file is safe, and the pre-move sha256 records what was moved.
            if ($null -ne $lanePlan -and $slot.lane -cin @('command', 'memory', 'publication')) {
                try {
                    Assert-P4DeviceLaneContract
                    Invoke-P4PrivateFileQuarantine `
                        -Context (Get-P4SlotDeviceContext -Manifest $manifest -Slot $slot -Directory $directory) `
                        -Plan $lanePlan.private_file_quarantine | Out-Null
                } catch { $cleanupErrors.Add($_.Exception.Message) }
            }
            try {
                if ($script:P4CaptureDrainSeconds -gt 0) { Start-Sleep -Seconds $script:P4CaptureDrainSeconds }
                $seal = New-P4CaptureSeal -Directory $directory -SlotId $SlotId -Slot $slot `
                    -RequireFrameSlot ($null -eq $failure)
                $sealHash = Get-FileSha256 (Join-Path $directory 'capture-seal.json')
            } catch { $cleanupErrors.Add($_.Exception.Message) }
            if ($null -ne $original) {
                try {
                    $restoration = Invoke-P4SlotRestoration -Manifest $manifest -Slot $slot -Directory $directory -Original $original
                    $restorationHash = [string]$restoration.sha256
                    if (-not $restoration.restored) { $cleanupErrors.Add([string]$restoration.message) }
                } catch { $cleanupErrors.Add($_.Exception.Message) }
            }
            try {
                $after = Get-P4WorktreeState -Worktree $manifest.roles[$slot.role].worktree -Stage 'after' -Directory $directory
                Assert-P4WorktreeState -State $after -ExpectedCommit $manifest.roles[$slot.role].build_commit `
                    -Context "slot $SlotId cleanup"
                $worktreeAfterHash = Get-FileSha256 (Join-Path $directory 'worktree-after.json')
            } catch { $cleanupErrors.Add($_.Exception.Message) }
            try {
                if ([datetime]::UtcNow -gt $slotDeadlineUtc.AddSeconds(-$script:P4AnalysisTimeoutSeconds)) {
                    throw 'Cleanup overran the slot deadline; no analysis budget remains.'
                }
            } catch { $cleanupErrors.Add($_.Exception.Message) }
            if ($cleanupErrors.Count -gt 0) {
                $failure = [InvalidOperationException]::new("Slot cleanup failed: $($cleanupErrors -join '; ')", $failure)
            }
        }
        if ($null -eq $failure) {
            try {
                $analysisStarted = [datetime]::UtcNow
                $analysis = Invoke-BoundedNativeCommand -RepositoryRoot $manifest.roles.candidate.worktree `
                    -ExecutablePath (Join-Path $PSHOME 'pwsh.exe') -LogPath (Join-Path $directory 'analyzer.log') `
                    -TimeoutSeconds $script:P4AnalysisTimeoutSeconds -NativeArguments @('-NoProfile', '-File',
                        $manifest.tools.analyzer.path, '-ManifestPath', $reserved, '-SlotId', $SlotId,
                        '-OutputDirectory', $directory)
                if ($analysis.ExitCode -ne 0) { throw 'Analyzer rejected capture; preserve partial result.' }
                $analysisResult = Read-P4FreshAnalysis $analysisPath $slot $manifestHash $analysisStarted $sealHash
                Assert-P4AnalysisSealAgreement -Analysis $analysisResult -Seal $seal -Slot $slot
                $analysisHash = Get-FileSha256 $analysisPath
            } catch { $failure = $_.Exception }
        }
        if ($null -ne $failure) {
            Write-NewInvocationFile (Join-Path $directory 'invalid.json') (([ordered]@{
                schema = $script:P4ManifestSchema; slot_id = $SlotId; status = 'invalid'; verdict = 'INVALID';
                message = $failure.ToString(); capture_seal_sha256 = $sealHash; analysis_sha256 = $analysisHash;
                restoration_sha256 = $restorationHash; worktree_after_sha256 = $worktreeAfterHash;
                collector_timeout_seconds = $collectorTimeoutSeconds; collector_budget = $collectorBudget;
                slot_deadline_utc = $slotDeadlineUtc.ToString('o'); ended_utc = [datetime]::UtcNow.ToString('o')
            }) | ConvertTo-Json -Depth 8)
            throw $failure
        }
        $analysisResult.status = 'completed'
        $analysisResult.ended_utc = [datetime]::UtcNow.ToString('o')
        $analysisResult.capture_seal_sha256 = $sealHash
        $analysisResult.analysis_sha256 = $analysisHash
        $analysisResult.restoration_sha256 = $restorationHash
        $analysisResult.worktree_after_sha256 = $worktreeAfterHash
        $analysisResult.collector_timeout_seconds = $collectorTimeoutSeconds
        $analysisResult.collector_budget = $collectorBudget
        Write-NewInvocationFile (Join-Path $directory 'completed.json') ($analysisResult | ConvertTo-Json -Depth 15)
        return $analysisResult
    } finally {
        if ($acquired) { $mutex.ReleaseMutex() }
        $mutex.Dispose()
    }
}

if ($MyInvocation.InvocationName -ne '.') { Invoke-P4IndexedSlot $ManifestPath $SlotId }

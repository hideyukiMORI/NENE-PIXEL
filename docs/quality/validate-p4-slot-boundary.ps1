[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/invoke-p4-indexed-slot.ps1') -ManifestPath fixture -SlotId fixture

# All subprocess and device boundaries below are synthetic, local test replacements.
# Actual artifact admission is covered separately; no ADB, JVM collector or Git mutation runs.
# The only real Git use is one read-only `rev-parse`/`status` probe of this worktree.

$script:repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$script:buildCommit = '0123456789abcdef0123456789abcdef01234567'
$script:driftCommit = 'fedcba9876543210fedcba9876543210fedcba98'
$script:unsealedSha = 'f' * 64
$script:boundaryMode = 'success'
$script:restored = $false
$script:callLog = [Collections.Generic.List[string]]::new()
$script:catalog = @()

# --- Real-implementation probes taken before the synthetic overrides replace the boundaries. -------

$realQuiescence = Get-P4HostProcessQuiescence
foreach ($key in @('patterns', 'own_tree_process_ids', 'offending', 'checked_utc')) {
    if (-not $realQuiescence.Contains($key)) { throw "Real host quiescence record is missing $key." }
}
if (@($realQuiescence.own_tree_process_ids) -notcontains $PID) {
    throw 'Real host quiescence did not exclude this process tree.'
}
foreach ($entry in @($realQuiescence.offending)) {
    if (@($realQuiescence.own_tree_process_ids) -contains [int]$entry.process_id) {
        throw 'Real host quiescence flagged a process inside this process tree.'
    }
}

$script:probeRoot = Join-Path ([IO.Path]::GetTempPath()) ('nene-p4-slot-probe-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $script:probeRoot | Out-Null
$realWorktree = Get-P4WorktreeState -Worktree $script:repositoryRoot -Stage 'probe' -Directory $script:probeRoot
if ([string]$realWorktree.head -cnotmatch '^[0-9a-f]{40}$' -or
    -not (Test-Path -LiteralPath (Join-Path $script:probeRoot 'worktree-probe.json'))) {
    throw 'Real worktree state probe did not record a commit.'
}
$worktreeRejected = $false
try { Assert-P4WorktreeState -State $realWorktree -ExpectedCommit $script:driftCommit -Context 'probe' }
catch { $worktreeRejected = $true }
if (-not $worktreeRejected) { throw 'Worktree assertion accepted a drifted commit.' }

# Manifest-derived remote packages (never literals). The real manifest keeps artifacts per role at
# `roles.<role>.artifacts.<kind>` (see build/reports/issue-106/p4-preflight-template.json).
$script:P4ArtifactKinds = @('app_debug', 'test_debug', 'app_release_like', 'publication_test')
function New-P4RoleArtifactFixture {
    param([string]$Commit)
    $artifacts = [ordered]@{}
    foreach ($kind in $script:P4ArtifactKinds) {
        $testPackage = switch ($kind) {
            'test_debug' { 'io.github.hideyukimori.nenepixel.test' }
            'publication_test' { 'io.github.hideyukimori.nenepixel.adapters.persistence.test' }
            default { 'none' }
        }
        $artifacts[$kind] = [ordered]@{ path = "SYNTHETIC-$kind.apk"; byte_count = 1; sha256 = ('a' * 64)
            variant = 'synthetic'; target_package = 'io.github.hideyukimori.nenepixel'
            test_package = $testPackage; embedded_revision = $Commit
            requested_dexopt = 'verify'; observed_dexopt = 'verify' }
    }
    return $artifacts
}

$packageManifest = @{ roles = @{
    baseline = @{ artifacts = New-P4RoleArtifactFixture $script:buildCommit }
    candidate = @{ artifacts = New-P4RoleArtifactFixture $script:buildCommit } } }
$derived = @(Get-P4ManifestPackages $packageManifest)
$expectedPackages = @('io.github.hideyukimori.nenepixel',
    'io.github.hideyukimori.nenepixel.adapters.persistence.test', 'io.github.hideyukimori.nenepixel.test')
if (($derived -join ',') -cne ($expectedPackages -join ',')) {
    throw "Manifest package derivation drifted: $($derived -join ',')"
}
# The synthetic role inventory must be shaped the way the REAL artifacts traversal expects: the four
# fixed kinds and their mandatory identity fields (file records are not checked here, only the shape).
foreach ($role in @('baseline', 'candidate')) {
    $artifacts = $packageManifest.roles[$role].artifacts
    foreach ($kind in $script:P4ArtifactKinds) {
        if (-not $artifacts.Contains($kind)) { throw "The artifact fixture omits $role.$kind." }
        Assert-P4RequiredKeys $artifacts[$kind] @('path', 'byte_count', 'sha256', 'variant', 'target_package',
            'test_package', 'embedded_revision', 'requested_dexopt', 'observed_dexopt') "$role.$kind"
    }
}
foreach ($broken in @(
        @{ roles = @{ baseline = @{ artifacts = @{ app_debug = @{ target_package = 'none'; test_package = 'none' } } }
            candidate = @{ artifacts = @{ app_debug = @{ target_package = 'none'; test_package = 'none' } } } } },
        @{ roles = @{ baseline = @{ artifacts = @{ app_debug = @{ target_package = 'Io.Github.Bad Package'; test_package = 'none' } } }
            candidate = @{ artifacts = @{ app_debug = @{ target_package = 'none'; test_package = 'none' } } } } },
        @{ roles = @{ baseline = @{ artifacts = @{ app_debug = @{ target_package = 'io.github.hideyukimori.nenepixel' } } }
            candidate = @{ artifacts = @{ app_debug = @{ target_package = 'none'; test_package = 'none' } } } } },
        @{ roles = @{ candidate = @{ artifacts = New-P4RoleArtifactFixture $script:buildCommit } } },
        @{ roles = @{ baseline = @{ worktree = 'x' }; candidate = @{ worktree = 'x' } } },
        @{ artifacts = @{ app_debug = @{ target_package = 'io.github.hideyukimori.nenepixel'; test_package = 'none' } } },
        @{ tools = @{} })) {
    $packagesRejected = $false
    try { Get-P4ManifestPackages $broken | Out-Null } catch { $packagesRejected = $true }
    if (-not $packagesRejected) { throw 'Manifest package derivation accepted an invalid artifact record.' }
}

# --- Synthetic boundaries. ------------------------------------------------------------------------

# The 5-second capture drain is wall-clock only; synthetic captures need none. The deadline case below
# restores a real positive drain so the overrun it proves is measured, not simulated.
$script:P4CaptureDrainSeconds = 0

function Assert-P4ManifestArtifacts {
    param($Manifest, $RepositoryRoot, [string]$Stage)
    # The slot wrapper must always ask for the slot stage; the reservation stage belongs to preflight.
    if ($Stage -cne 'slot') { throw "The slot wrapper requested the wrong preflight stage: '$Stage'" }
}
function Get-P4SlotCatalog { return @($script:catalog) }

function Get-P4HostProcessQuiescence {
    $offending = if ($script:boundaryMode -ceq 'admission-quiescence-refusal') {
        @([ordered]@{ process_id = 424242; pattern = 'GradleDaemon'; command_line = 'SYNTHETIC GradleDaemon' })
    } else { @() }
    return [ordered]@{ patterns = @('SYNTHETIC'); own_tree_process_ids = @($PID); offending = $offending;
        checked_utc = [datetime]::UtcNow.ToString('o') }
}

function Get-P4RemotePackageAbsence {
    param($Manifest, $Slot, [string]$Directory, [string]$Stage)
    $packages = @(Get-P4ManifestPackages $Manifest)
    $observed = [ordered]@{}
    foreach ($package in $packages) { $observed[$package] = 'absent' }
    $script:callLog.Add("remote-absence-$Stage") | Out-Null
    return [ordered]@{ packages = $packages; observed = $observed; errors = @() }
}

function Get-P4WorktreeState {
    param([string]$Worktree, [string]$Stage, [string]$Directory)
    $head = if ($script:boundaryMode -ceq 'admission-worktree-refusal') { $script:driftCommit } else { $script:buildCommit }
    $state = [ordered]@{ schema = 'nene-pixel-p4-worktree-state-v1'; stage = $Stage; worktree = $Worktree;
        head = $head; status_lines = @(); captured_utc = [datetime]::UtcNow.ToString('o') }
    Write-NewInvocationFile (Join-Path $Directory "worktree-$Stage.json") ($state | ConvertTo-Json -Depth 8)
    $script:callLog.Add("worktree-$Stage") | Out-Null
    return $state
}

function Get-P4LiveDeviceState {
    param([string]$AdbPath, [string]$Serial, [string]$Directory, [string]$Stage, [string]$RepositoryRoot)
    $script:callLog.Add("device-state-$Stage") | Out-Null
    $state = [ordered]@{ serial = $Serial; stage = $Stage; captured_utc = [datetime]::UtcNow.ToString('o') }
    Write-NewInvocationFile (Join-Path $Directory "device-state-$Stage.json") ($state | ConvertTo-Json -Depth 5)
    return $state
}

function Assert-P4DeviceStateMatches {
    param($Expected, $Observed, [string]$Context)
    if ($script:boundaryMode -ceq 'admission-device-refusal' -and $Context -like '*admission*') {
        throw 'SYNTHETIC_DEVICE_ADMISSION_DRIFT'
    }
    if ($script:boundaryMode -ceq 'device-drift-after' -and $Context -like '*cleanup*') {
        throw 'SYNTHETIC_DEVICE_AFTER_DRIFT'
    }
}

function Get-P4OriginalDeviceSettings {
    param($Manifest, $Directory)
    $script:callLog.Add('original-settings') | Out-Null
    return @{ synthetic = $true }
}

function Stop-P4RemoteProcesses {
    param($Manifest, $Slot, $Directory)
    $script:callLog.Add('stop-remote') | Out-Null
    Get-P4ManifestPackages $Manifest | Out-Null
    if ($script:boundaryMode -ceq 'remote-stop-fails') { throw 'SYNTHETIC_REMOTE_STOP_FAILURE' }
}

function Restore-P4OriginalDeviceSettings {
    param($Manifest, $Directory, $Original)
    $script:callLog.Add('restore-settings') | Out-Null
    $script:restored = $true
}

# --- Synthetic device private storage (for the cleanup-phase private-file quarantine). -------------
# One flat map per package: "<relative path>" -> content. Directories are implicit, exactly as `ls -1`
# sees them: a directory that holds nothing is reported absent, which is what a moved-away file leaves.
$script:deviceTree = @{}
# Directories that exist while holding nothing (a key ending in '/' seeds one). `ls -1` cannot tell such
# a directory from an absent one, which is exactly the reuse hole the presence probe closes.
$script:deviceDirectories = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
# When set, every `run-as` for this package answers with the sandbox diagnostic at exit 0, the way
# `adb exec-out run-as` reports a non-debuggable or unknown package.
$script:deviceRunAsFailure = ''

function Reset-P4SyntheticDevice {
    param([hashtable]$Files = @{}, [string]$RunAsFailure = '')
    $script:deviceTree = @{}
    $script:deviceDirectories = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $script:deviceRunAsFailure = $RunAsFailure
    foreach ($key in @($Files.Keys)) {
        $parts = ([string]$key -split '\|', 2)
        if ($parts.Count -ne 2) { throw "A synthetic device file needs a '<package>|<relative path>' key: $key" }
        if ($parts[1].EndsWith('/')) {
            $script:deviceDirectories.Add("$($parts[0])|$($parts[1].TrimEnd('/'))") | Out-Null
            continue
        }
        if (-not $script:deviceTree.ContainsKey($parts[0])) { $script:deviceTree[$parts[0]] = @{} }
        $script:deviceTree[$parts[0]][$parts[1]] = [string]$Files[$key]
    }
}

function Test-P4SyntheticPresent {
    param([string]$Package, [string]$Path)
    if ($script:deviceDirectories.Contains("$Package|$Path")) { return $true }
    return ($null -ne (Get-P4SyntheticEntries -Package $Package -Path $Path))
}

function Get-P4SyntheticEntries {
    param([string]$Package, [string]$Path)
    if (-not $script:deviceTree.ContainsKey($Package)) { return $null }
    $files = $script:deviceTree[$Package]
    if ($files.ContainsKey($Path)) { return @($Path) }
    $prefix = "$Path/"
    $names = [Collections.Generic.List[string]]::new()
    foreach ($key in @($files.Keys)) {
        $entry = [string]$key
        if (-not $entry.StartsWith($prefix, [StringComparison]::Ordinal)) { continue }
        $name = ($entry.Substring($prefix.Length) -split '/', 2)[0]
        if (-not $names.Contains($name)) { $names.Add($name) }
    }
    if ($names.Count -eq 0) { return $null }
    return @($names | Sort-Object -CaseSensitive)
}

function Invoke-P4SyntheticAdb {
    param([string]$LogPath, [string[]]$Arguments)
    $script:callLog.Add("adb:$($Arguments -join ' ')") | Out-Null
    if (@($Arguments).Count -lt 4 -or $Arguments[1] -cne 'run-as') {
        throw "Unexpected synthetic adb invocation: $($Arguments -join ' ')"
    }
    $package = [string]$Arguments[2]
    $exitCode = 0
    $output = @()
    if ($script:deviceRunAsFailure -cne '' -and $package -ceq $script:deviceRunAsFailure) {
        # `run-as` writes its refusal to stdout and exec-out does not propagate the remote exit code.
        Write-NewInvocationFile $LogPath "run-as: Package '$package' is not debuggable`n"
        return @{ ExitCode = 0; OutputLines = @("run-as: Package '$package' is not debuggable") }
    }
    switch ([string]$Arguments[3]) {
        'ls' {
            $path = [string]$Arguments[$Arguments.Count - 1]
            if ($Arguments[4] -ceq '-d') {
                $output = if (Test-P4SyntheticPresent -Package $package -Path $path) { @($path) }
                    else { @("ls: ${path}: No such file or directory") }
                break
            }
            $entries = Get-P4SyntheticEntries -Package $package -Path $path
            if ($null -eq $entries) {
                $output = if (Test-P4SyntheticPresent -Package $package -Path $path) { @() }
                    else { @("ls: ${path}: No such file or directory") }
            }
            elseif ($Arguments[4] -ceq '-la') {
                $output = @('total 0') + @($entries | ForEach-Object { "-rw------- 1 u0_a1 u0_a1 4 2026-09-16 00:00 $_" })
            } else { $output = @($entries) }
        }
        'sha256sum' {
            $path = [string]$Arguments[4]
            if (-not $script:deviceTree.ContainsKey($package) -or
                -not $script:deviceTree[$package].ContainsKey($path)) {
                throw "The synthetic device has no $package/$path to hash."
            }
            $sha256 = [Security.Cryptography.SHA256]::Create()
            try {
                $bytes = [Text.UTF8Encoding]::new($false).GetBytes([string]$script:deviceTree[$package][$path])
                $digest = [Convert]::ToHexString($sha256.ComputeHash($bytes)).ToLowerInvariant()
            } finally { $sha256.Dispose() }
            $output = @("$digest  $path")
        }
        'mkdir' {
            $script:deviceDirectories.Add("$package|$([string]$Arguments[$Arguments.Count - 1])") | Out-Null
            $output = @()
        }
        'mv' {
            $source = [string]$Arguments[4]
            $target = [string]$Arguments[5]
            if (-not $script:deviceTree.ContainsKey($package) -or
                -not $script:deviceTree[$package].ContainsKey($source)) {
                $output = @("mv: bad '$source': No such file or directory"); $exitCode = 1; break
            }
            if ($script:deviceTree[$package].ContainsKey($target)) {
                throw "The synthetic quarantine would overwrite $package/$target."
            }
            $script:deviceTree[$package][$target] = [string]$script:deviceTree[$package][$source]
            $script:deviceTree[$package].Remove($source)
        }
        default { throw "Unexpected synthetic adb command: $($Arguments -join ' ')" }
    }
    Write-NewInvocationFile $LogPath ((@($output) -join "`n") + "`n")
    return @{ ExitCode = $exitCode; OutputLines = @($output) }
}

function Invoke-BoundedNativeCommand {
    param($RepositoryRoot, $ExecutablePath, $LogPath, $TimeoutSeconds, $NativeArguments)
    $directory = Split-Path -Parent $LogPath
    # The wrapper's own adb calls (`-s <serial> ...`) travel this same bounded boundary.
    if (@($NativeArguments).Count -gt 2 -and [string]$NativeArguments[0] -ceq '-s') {
        return Invoke-P4SyntheticAdb -LogPath $LogPath `
            -Arguments @($NativeArguments[2..(@($NativeArguments).Count - 1)])
    }
    $slotId = [string]$NativeArguments[6]
    $slot = @(@($script:catalog) | Where-Object { $_.id -ceq $slotId })
    if ($slot.Count -ne 1) { throw 'Unexpected synthetic slot identity.' }
    $slot = $slot[0]
    if ($NativeArguments[2] -ceq 'synthetic-collector') {
        $script:callLog.Add('collector') | Out-Null
        if ($script:boundaryMode -ceq 'collector-fails') {
            return @{ ExitCode = 9; OutputLines = @('SYNTHETIC_COLLECTOR_FAILURE') }
        }
        Write-NewInvocationFile (Join-Path $directory 'capture.csv') "synthetic,capture,$slotId`n"
        if ($script:boundaryMode -ceq 'collector-forges-completed') {
            Write-NewInvocationFile (Join-Path $directory 'completed.json') '{"forged":true}'
        }
        return @{ ExitCode = 0; OutputLines = @('SYNTHETIC_COLLECTOR_ONLY') }
    }
    if ($NativeArguments[2] -cne 'synthetic-analyzer') { throw 'Unexpected synthetic native boundary.' }
    $script:callLog.Add('analyzer') | Out-Null
    if (Test-Path -LiteralPath (Join-Path $directory 'capture-seal.json')) {
        $script:callLog.Add('seal-present-at-analysis') | Out-Null
    }
    if (Test-Path -LiteralPath (Join-Path $directory 'restoration.json')) {
        $script:callLog.Add('restoration-present-at-analysis') | Out-Null
    }
    $path = Join-Path $directory 'analysis.json'
    if ($script:boundaryMode -ceq 'missing-analysis') { return @{ ExitCode = 0; OutputLines = @('SYNTHETIC_ANALYZER_ONLY') } }
    if ($script:boundaryMode -ceq 'corrupt-analysis') {
        Write-NewInvocationFile $path '{invalid'
        return @{ ExitCode = 0; OutputLines = @('SYNTHETIC_ANALYZER_ONLY') }
    }
    $verdict = switch ($slot.lane) {
        'host' { 'valid-descriptive' }
        'publication' { 'valid-constants-revision-required' }
        default { 'pass' }
    }
    $result = [ordered]@{ schema = "nene-pixel-p4-synthetic-$($slot.lane)-v1"; protocol_id = $script:P4ProtocolId;
        slot_id = $slotId; preflight_sha256 = (Get-FileSha256 ([string]$NativeArguments[4])); role = $slot.role;
        verdict = $verdict; created_utc = [datetime]::UtcNow.ToString('o');
        capture_sha256 = (Get-FileSha256 (Join-Path $directory 'capture.csv'));
        capture_seal_sha256 = (Get-FileSha256 (Join-Path $directory 'capture-seal.json')) }
    if ($script:boundaryMode -ceq 'wrong-identity') { $result.slot_id = 'another-slot' }
    if ($script:boundaryMode -ceq 'wrong-verdict') { $result.verdict = 'PERFORMANCE_INVENTED' }
    if ($script:boundaryMode -ceq 'analysis-unsealed') { $result.capture_sha256 = $script:unsealedSha }
    if ($script:boundaryMode -ceq 'analysis-wrong-seal') { $result.capture_seal_sha256 = $script:unsealedSha }
    if ($script:boundaryMode -ceq 'analysis-no-seal-binding') { $result.Remove('capture_seal_sha256') }
    Write-NewInvocationFile $path ($result | ConvertTo-Json -Depth 8)
    return @{ ExitCode = 0; OutputLines = @('SYNTHETIC_ANALYZER_ONLY') }
}

# --- Fixtures. ------------------------------------------------------------------------------------

$script:testRoot = Join-Path ([IO.Path]::GetTempPath()) ('nene-p4-slot-fixture-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $script:testRoot | Out-Null

function New-P4SlotFixture {
    param([string]$Name, [hashtable]$DeviceFiles = @{}, [string]$RunAsFailure = '')
    $directory = Join-Path $script:testRoot $Name
    New-Item -ItemType Directory -Path $directory | Out-Null
    Reset-P4SyntheticDevice -Files $DeviceFiles -RunAsFailure $RunAsFailure
    $manifest = [ordered]@{
        # Device lanes derive their collector bound and their private-file quarantine path from the lane
        # plan, which is bound to the experiment identity; a slot the wrapper cannot plan is refused.
        experiment_id = 'nene-p4-slot-boundary-fixture'
        output_directory = $directory
        roles = [ordered]@{
            baseline = [ordered]@{ worktree = $directory; build_commit = $script:buildCommit
                artifacts = (New-P4RoleArtifactFixture $script:buildCommit) }
            candidate = [ordered]@{ worktree = $directory; build_commit = $script:buildCommit
                artifacts = (New-P4RoleArtifactFixture $script:buildCommit) } }
        tools = [ordered]@{ runner = [ordered]@{ path = 'synthetic-collector' }
            analyzer = [ordered]@{ path = 'synthetic-analyzer' }; adb = [ordered]@{ path = 'synthetic-adb' } }
        device = [ordered]@{ serial = 'SYNTHETIC-SERIAL' } }
    $manifestPath = Join-Path $directory 'preflight.json'
    Write-NewInvocationFile $manifestPath ($manifest | ConvertTo-Json -Depth 10)
    return [ordered]@{ root = $directory; manifest = $manifestPath }
}

function New-P4SlotDescriptor {
    param([string]$Id, [string]$Lane, [string]$Role, [string]$Runner, [int]$TimeoutSeconds = 180)
    return [ordered]@{ id = $Id; lane = $Lane; role = $Role; runner = $Runner; run = 1;
        timeout_seconds = $TimeoutSeconds; warmups = 5; samples = 20 }
}

function Invoke-P4BoundarySlot {
    param([string]$ManifestPath, [string]$SlotId)
    $script:lastError = ''
    try { Invoke-P4IndexedSlot $ManifestPath $SlotId | Out-Null; return $false }
    catch { $script:lastError = $_.Exception.ToString(); return $true }
}

function Assert-P4SealedCompletion {
    param([string]$SlotDirectory, [string]$Lane)
    $completed = Get-Content -LiteralPath (Join-Path $SlotDirectory 'completed.json') -Raw | ConvertFrom-Json -AsHashtable
    foreach ($key in @('capture_seal_sha256', 'analysis_sha256', 'restoration_sha256', 'worktree_after_sha256')) {
        if (-not $completed.Contains($key)) { throw "completed.json is missing $key." }
    }
    if ($completed.capture_seal_sha256 -cne (Get-FileSha256 (Join-Path $SlotDirectory 'capture-seal.json'))) {
        throw 'completed.json does not bind the capture seal.'
    }
    if ($completed.analysis_sha256 -cne (Get-FileSha256 (Join-Path $SlotDirectory 'analysis.json'))) {
        throw 'completed.json does not bind the analysis bytes.'
    }
    if ($completed.worktree_after_sha256 -cne (Get-FileSha256 (Join-Path $SlotDirectory 'worktree-after.json'))) {
        throw 'completed.json does not bind the post-slot worktree proof.'
    }
    if ($Lane -ceq 'host') {
        if ($completed.restoration_sha256 -cne 'not-applicable') { throw 'Host slot claimed a device restoration.' }
        if (Test-Path -LiteralPath (Join-Path $SlotDirectory 'restoration.json')) {
            throw 'Host slot produced a device restoration record.'
        }
    } else {
        if ($completed.restoration_sha256 -cne (Get-FileSha256 (Join-Path $SlotDirectory 'restoration.json'))) {
            throw 'completed.json does not bind the restoration proof.'
        }
    }
    $seal = Get-Content -LiteralPath (Join-Path $SlotDirectory 'capture-seal.json') -Raw | ConvertFrom-Json -AsHashtable
    $sealed = @(@($seal.files) | ForEach-Object { [string]$_.relative_path })
    foreach ($required in @('capture.csv', 'started.json', 'admission/quiescence.json', 'admission/worktree-before.json')) {
        if ($sealed -cnotcontains $required) { throw "The capture seal omitted $required." }
    }
    foreach ($forbidden in @('capture-seal.json', 'analysis.json', 'completed.json', 'worktree-after.json', 'restoration.json')) {
        if ($sealed -ccontains $forbidden) { throw "The capture seal included the post-seal file $forbidden." }
    }
    foreach ($entry in @($seal.files)) {
        if ([string]$entry.relative_path -like 'restore/*') { throw 'The capture seal included post-seal restore output.' }
    }
    if ($completed.capture_sha256 -cnotin @($seal.files | ForEach-Object { [string]$_.sha256 })) {
        throw 'The accepted analysis hash is not a sealed capture hash.'
    }
    # The analysis itself must name the same seal as the accepted result.
    $analysis = Get-Content -LiteralPath (Join-Path $SlotDirectory 'analysis.json') -Raw | ConvertFrom-Json -AsHashtable
    if (-not $analysis.Contains('capture_seal_sha256') -or
        $analysis.capture_seal_sha256 -cne $completed.capture_seal_sha256) {
        throw 'analysis.json does not bind the capture seal the result accepted.'
    }
    # Fixed, finite budgets recorded at reservation. The expected values are literals on purpose, so a
    # change to the wrapper's constants has to be made here too.
    $started = Get-Content -LiteralPath (Join-Path $SlotDirectory 'started.json') -Raw | ConvertFrom-Json -AsHashtable
    if ([int]$started.cleanup_reserve_seconds -ne 90 -or [int]$started.analysis_timeout_seconds -ne 120) {
        throw 'started.json does not record the fixed cleanup (90 s) / analysis (120 s) budgets.'
    }
    # Protocol v5 instrumentation bound: the collector Job must outlast the instrumentation it hosts, so
    # the recorded collector bound is the DERIVED one for the instrumentation lanes and the protocol
    # timeout for host/frame. The breakdown must add up to the bound the slot deadline was built from.
    Assert-P4RequiredKeys $started @('protocol_timeout_seconds', 'collector_budget') 'started.json'
    $budget = $started.collector_budget
    Assert-P4RequiredKeys $budget @('lane', 'derived', 'timeout_seconds', 'instrumentation_seconds',
        'install_seconds', 'dexopt_seconds', 'probe_count', 'probe_seconds', 'private_capture_seconds',
        'reserve_seconds', 'collector_timeout_seconds') 'started.collector_budget'
    $breakdown = [int]$budget.instrumentation_seconds + [int]$budget.install_seconds + [int]$budget.dexopt_seconds +
        [int]$budget.probe_seconds + [int]$budget.private_capture_seconds + [int]$budget.reserve_seconds
    if ([int]$budget.collector_timeout_seconds -ne [int]$started.collector_timeout_seconds -or
        [int]$budget.timeout_seconds -ne [int]$started.protocol_timeout_seconds) {
        throw 'started.json does not bind its collector bound to the recorded budget.'
    }
    # The bound is handed to Invoke-BoundedNativeCommand -TimeoutSeconds, whose ValidateRange ceiling is
    # 3600 s under protocol v5 (protocol:362-374); the derivation must refuse before reservation rather
    # than at that parameter binding.
    if ([int]$budget.collector_timeout_seconds -gt 3600 -or [int]$budget.cap_seconds -ne 3600) {
        throw 'The recorded collector bound is not held under the bounded native ValidateRange ceiling.'
    }
    if ($Lane -ceq 'host') {
        if ([bool]$budget.derived -or [int]$started.collector_timeout_seconds -ne [int]$started.protocol_timeout_seconds) {
            throw 'A host slot must keep its protocol timeout as the collector bound.'
        }
    } elseif ($Lane -cin @('command', 'memory', 'publication')) {
        if (-not [bool]$budget.derived -or $breakdown -ne [int]$budget.collector_timeout_seconds -or
            [int]$budget.instrumentation_seconds -ne [int]$started.protocol_timeout_seconds -or
            [int]$started.collector_timeout_seconds -le [int]$started.protocol_timeout_seconds) {
            throw 'An instrumentation slot did not derive a collector bound that outlasts its inner bound.'
        }
        # The quarantine runs before the seal, on success and failure alike, and is therefore capture.
        $quarantinePath = Join-Path $SlotDirectory 'private-file-quarantine.json'
        if (-not (Test-Path -LiteralPath $quarantinePath -PathType Leaf)) {
            throw 'A device slot did not record its private-file quarantine.'
        }
        if ($sealed -cnotcontains 'private-file-quarantine.json') {
            throw 'The private-file quarantine record was not sealed with the capture.'
        }
        $quarantine = Get-Content -LiteralPath $quarantinePath -Raw | ConvertFrom-Json -AsHashtable
        if ([string]$quarantine.schema -cne 'nene-pixel-p4-private-file-quarantine-v1' -or
            [string]$quarantine.slot_id -cne ([string]$started.slot_id) -or [bool]$quarantine.deleted -or
            -not [bool]$quarantine.completed -or [string]$quarantine.stage -cne 'complete') {
            throw 'The private-file quarantine record does not describe a completed slot without deleting.'
        }
    }
    $expectedDeadline = ([datetime]$started.started_utc).ToUniversalTime().AddSeconds(
        [int]$started.collector_timeout_seconds + 90 + 120)
    $recordedDeadline = ([datetime]$started.slot_deadline_utc).ToUniversalTime()
    if ([math]::Abs(($recordedDeadline - $expectedDeadline).TotalSeconds) -gt 1) {
        throw 'started.json does not record a finite slot deadline of started + timeout + reserve + analysis.'
    }
}

# --- Single-slot cases. ---------------------------------------------------------------------------

$singleCases = @(
    @{ name = 'success'; lane = 'host' },
    @{ name = 'missing-analysis'; lane = 'host' },
    @{ name = 'corrupt-analysis'; lane = 'host' },
    @{ name = 'wrong-identity'; lane = 'host' },
    @{ name = 'wrong-verdict'; lane = 'host' },
    @{ name = 'analysis-unsealed'; lane = 'host' },
    @{ name = 'analysis-wrong-seal'; lane = 'host' },
    @{ name = 'analysis-no-seal-binding'; lane = 'host' },
    @{ name = 'admission-worktree-refusal'; lane = 'host' },
    @{ name = 'device-success'; lane = 'memory' },
    @{ name = 'remote-stop-fails'; lane = 'memory' },
    @{ name = 'collector-fails'; lane = 'memory' },
    @{ name = 'device-drift-after'; lane = 'memory' },
    @{ name = 'admission-quiescence-refusal'; lane = 'memory' },
    @{ name = 'admission-device-refusal'; lane = 'memory' }
)
$refusalCases = @('admission-worktree-refusal', 'admission-quiescence-refusal', 'admission-device-refusal')

foreach ($case in $singleCases) {
    $script:boundaryMode = $case.name
    $script:restored = $false
    $script:callLog.Clear()
    $slotId = if ($case.lane -ceq 'host') { 'host-project-baseline' } else { 'memory-candidate-common-1' }
    $role = if ($case.lane -ceq 'host') { 'baseline' } else { 'candidate' }
    $script:catalog = @(New-P4SlotDescriptor -Id $slotId -Lane $case.lane -Role $role -Runner 'common' `
            -TimeoutSeconds $(if ($case.lane -ceq 'host') { 180 } else { 300 }))
    $fixture = New-P4SlotFixture $case.name
    $rejected = Invoke-P4BoundarySlot $fixture.manifest $slotId
    $slotRoot = Join-Path $fixture.root $slotId

    if ($refusalCases -ccontains $case.name) {
        if (-not $rejected) { throw "Admission refusal was accepted: $($case.name)" }
        if (Test-Path -LiteralPath $slotRoot) { throw "Admission refusal reserved a slot directory: $($case.name)" }
        $refusals = @(Get-ChildItem -LiteralPath (Join-Path $fixture.root 'admission-refusals') -File)
        if ($refusals.Count -ne 1) { throw "Admission refusal was not recorded exactly once: $($case.name)" }
        $refusal = Get-Content -LiteralPath $refusals[0].FullName -Raw | ConvertFrom-Json -AsHashtable
        if ($refusal.slot_id -cne $slotId -or $refusal.attempt_consumed -ne $false -or
            $refusal.schema -cne 'nene-pixel-p4-admission-refusal-v1' -or
            $refusal.reason_code -cne 'admission-check') {
            throw "Admission refusal record is malformed: $($case.name)"
        }
        if (-not (Test-Path -LiteralPath (Join-Path $refusal.admission_directory 'quiescence.json'))) {
            throw "Admission refusal discarded its evidence: $($case.name)"
        }
        if (@($script:callLog) -contains 'collector') { throw "Admission refusal still ran the collector: $($case.name)" }
        continue
    }

    if ($case.name -ceq 'success' -or $case.name -ceq 'device-success') {
        if ($rejected) { throw "Synthetic valid slot failed: $($case.name) : $script:lastError" }
        Assert-P4SealedCompletion -SlotDirectory $slotRoot -Lane $case.lane
        if ($case.name -ceq 'device-success') {
            # Order proof: stop, seal, restore and the post-restoration device read all precede the analyzer.
            $log = @($script:callLog)
            foreach ($required in @('device-state-before', 'collector', 'stop-remote', 'restore-settings',
                    'device-state-after', 'analyzer', 'seal-present-at-analysis', 'restoration-present-at-analysis')) {
                if ($log -cnotcontains $required) { throw "Slot order proof is missing $required." }
            }
            $analyzerIndex = $log.IndexOf('analyzer')
            foreach ($before in @('device-state-before', 'collector', 'stop-remote', 'restore-settings', 'device-state-after')) {
                if ($log.IndexOf($before) -ge $analyzerIndex) { throw "The analyzer did not run after $before." }
            }
            if ($log.IndexOf('restore-settings') -lt $log.IndexOf('stop-remote')) {
                throw 'Settings restoration preceded remote process termination.'
            }
            if (-not $script:restored) { throw 'Device settings were never restored.' }
        }
        $filesBefore = @(Get-ChildItem -LiteralPath $slotRoot -File | Sort-Object Name |
            ForEach-Object { "$($_.Name):$(Get-FileSha256 $_.FullName)" })
        $retryRejected = Invoke-P4BoundarySlot $fixture.manifest $slotId
        $filesAfter = @(Get-ChildItem -LiteralPath $slotRoot -File | Sort-Object Name |
            ForEach-Object { "$($_.Name):$(Get-FileSha256 $_.FullName)" })
        if (-not $retryRejected -or ($filesBefore -join '|') -cne ($filesAfter -join '|')) {
            throw "Retry altered accepted evidence: $($case.name)"
        }
        continue
    }

    if (-not $rejected -or -not (Test-Path -LiteralPath (Join-Path $slotRoot 'invalid.json')) -or
        (Test-Path -LiteralPath (Join-Path $slotRoot 'completed.json'))) {
        throw "Failed slot lost invalid evidence: $($case.name)"
    }
    $invalid = Get-Content -LiteralPath (Join-Path $slotRoot 'invalid.json') -Raw | ConvertFrom-Json -AsHashtable
    foreach ($key in @('capture_seal_sha256', 'analysis_sha256', 'restoration_sha256', 'worktree_after_sha256')) {
        if (-not $invalid.Contains($key)) { throw "invalid.json is missing $key : $($case.name)" }
    }
    if ($invalid.capture_seal_sha256 -cne (Get-FileSha256 (Join-Path $slotRoot 'capture-seal.json'))) {
        throw "invalid.json does not bind the capture seal produced before analysis: $($case.name)"
    }
    if ($case.lane -cne 'host') {
        if (-not $script:restored) { throw "A failed device slot skipped settings restoration: $($case.name)" }
        if ($invalid.restoration_sha256 -cne (Get-FileSha256 (Join-Path $slotRoot 'restoration.json'))) {
            throw "invalid.json does not bind the restoration proof: $($case.name)"
        }
        $restoration = Get-Content -LiteralPath (Join-Path $slotRoot 'restoration.json') -Raw | ConvertFrom-Json -AsHashtable
        $expectedRestored = ($case.name -cne 'device-drift-after')
        if ($restoration.restored -ne $expectedRestored) {
            throw "The restoration record does not describe the observed device state: $($case.name)"
        }
    }
    if ($case.name -ceq 'collector-fails' -and @($script:callLog) -contains 'analyzer') {
        throw 'The analyzer ran after a failed collector.'
    }
}

# --- A collector that forges wrapper-owned slot evidence. ------------------------------------------

$script:boundaryMode = 'collector-forges-completed'
$script:restored = $false
$script:callLog.Clear()
$script:catalog = @(New-P4SlotDescriptor -Id 'host-project-baseline' -Lane 'host' -Role 'baseline' `
        -Runner 'project' -TimeoutSeconds 180)
$fixture = New-P4SlotFixture 'collector-forges-completed'
$rejected = Invoke-P4BoundarySlot $fixture.manifest 'host-project-baseline'
$slotRoot = Join-Path $fixture.root 'host-project-baseline'
if (-not $rejected) { throw 'A collector-forged completed.json was accepted.' }
if ($script:lastError -notlike '*wrapper-owned slot evidence*') {
    throw "The forged wrapper evidence was not the recorded reason: $script:lastError"
}
if (-not (Test-Path -LiteralPath (Join-Path $slotRoot 'invalid.json'))) {
    throw 'A collector-forged completed.json lost its invalid evidence.'
}
if (@($script:callLog) -contains 'analyzer') { throw 'The analyzer ran after forged wrapper evidence.' }

# --- Cleanup deadline overrun (measured, not simulated). ------------------------------------------

$script:boundaryMode = 'deadline'
$script:restored = $false
$script:callLog.Clear()
$script:catalog = @(New-P4SlotDescriptor -Id 'memory-candidate-common-1' -Lane 'memory' -Role 'candidate' `
        -Runner 'common' -TimeoutSeconds 1)
$fixture = New-P4SlotFixture 'cleanup-overruns-deadline'
$savedReserve = $script:P4CleanupReserveSeconds
$savedAnalysis = $script:P4AnalysisTimeoutSeconds
$savedDrain = $script:P4CaptureDrainSeconds
# The collector bound is DERIVED from the lane's own per-call limits, so a deadline this case can
# actually overrun is produced by shrinking those real limits - the derivation itself still runs.
$savedLaneLimits = [ordered]@{
    install = $script:P4InstallTimeoutSeconds; dexopt = $script:P4DexoptTimeoutSeconds
    probe = $script:P4ProbeTimeoutSeconds; private = $script:P4PrivateFileTimeoutSeconds
    collector = $script:P4CollectorReserveSeconds }
try {
    $script:P4CleanupReserveSeconds = 1
    $script:P4AnalysisTimeoutSeconds = 0
    $script:P4CaptureDrainSeconds = 4
    $script:P4InstallTimeoutSeconds = 0
    $script:P4DexoptTimeoutSeconds = 0
    $script:P4ProbeTimeoutSeconds = 0
    $script:P4PrivateFileTimeoutSeconds = 0
    $script:P4CollectorReserveSeconds = 1
    $rejected = Invoke-P4BoundarySlot $fixture.manifest 'memory-candidate-common-1'
} finally {
    $script:P4CleanupReserveSeconds = $savedReserve
    $script:P4AnalysisTimeoutSeconds = $savedAnalysis
    $script:P4CaptureDrainSeconds = $savedDrain
    $script:P4InstallTimeoutSeconds = $savedLaneLimits.install
    $script:P4DexoptTimeoutSeconds = $savedLaneLimits.dexopt
    $script:P4ProbeTimeoutSeconds = $savedLaneLimits.probe
    $script:P4PrivateFileTimeoutSeconds = $savedLaneLimits.private
    $script:P4CollectorReserveSeconds = $savedLaneLimits.collector
}
$slotRoot = Join-Path $fixture.root 'memory-candidate-common-1'
if (-not $rejected -or -not (Test-Path -LiteralPath (Join-Path $slotRoot 'invalid.json')) -or
    (Test-Path -LiteralPath (Join-Path $slotRoot 'completed.json'))) {
    throw 'A cleanup that overran the slot deadline was accepted.'
}
if ($script:lastError -notlike '*overran the slot deadline*') {
    throw "The deadline overrun was not the recorded reason: $script:lastError"
}
if (@($script:callLog) -contains 'analyzer') { throw 'The analyzer ran after the slot deadline expired.' }
if (-not $script:restored) { throw 'A deadline overrun skipped settings restoration.' }

# --- Cleanup-phase private-file quarantine (decision 2). ------------------------------------------
# The protocol's fail-if-present output reservation refuses a device output left behind by an earlier
# slot, so a slot that dies after the test wrote its CSV would block every later slot - including the
# bounded recovery. Cleanup therefore MOVES (never deletes) the plan-listed private files aside, on
# success and on failure alike, before the capture seal.

$script:commandPackage = 'io.github.hideyukimori.nenepixel'
$script:commandPrivatePath = 'files/p4-measurements/p4-indexed-command-baseline-run-01.csv'
$script:commandQuarantinePath =
    'no_backup/p4-quarantine/nene-p4-slot-boundary-fixture/command-baseline'
$script:commandCatalog = @(New-P4SlotDescriptor -Id 'command-baseline' -Lane 'command' -Role 'baseline' `
        -Runner 'command' -TimeoutSeconds 300)

function New-P4CommandDeviceFiles {
    param([switch]$WithExistingQuarantine, [switch]$WithEmptyQuarantine)
    $files = @{ "$script:commandPackage|$script:commandPrivatePath" = 'device,csv,payload' }
    if ($WithExistingQuarantine) {
        $files["$script:commandPackage|$script:commandQuarantinePath/leftover.csv"] = 'older attempt'
    }
    # A trailing '/' seeds a directory that exists while holding nothing - what an earlier attempt that
    # created the quarantine and then died leaves behind. `ls -1` cannot tell it from absence.
    if ($WithEmptyQuarantine) { $files["$script:commandPackage|$script:commandQuarantinePath/"] = '' }
    return $files
}

function Get-P4QuarantineRecord {
    param([string]$SlotDirectory)
    $path = Join-Path $SlotDirectory 'private-file-quarantine.json'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "No quarantine record at $path" }
    return (Get-Content -LiteralPath $path -Raw | ConvertFrom-Json -AsHashtable)
}

$script:boundaryMode = 'command-success'
$script:restored = $false
$script:callLog.Clear()
$script:catalog = $script:commandCatalog
$fixture = New-P4SlotFixture -Name 'quarantine-success' -DeviceFiles (New-P4CommandDeviceFiles)
if (Invoke-P4BoundarySlot $fixture.manifest 'command-baseline') {
    throw "A command slot with a quarantined private file was rejected: $script:lastError"
}
$slotRoot = Join-Path $fixture.root 'command-baseline'
Assert-P4SealedCompletion -SlotDirectory $slotRoot -Lane 'command'
$quarantine = Get-P4QuarantineRecord -SlotDirectory $slotRoot
$expectedDigest = (Get-FileHash -InputStream ([IO.MemoryStream]::new(
            [Text.UTF8Encoding]::new($false).GetBytes('device,csv,payload'))) -Algorithm SHA256).Hash.ToLowerInvariant()
if (@($quarantine.files).Count -ne 1 -or
    [string]$quarantine.files[0].name -cne 'p4-indexed-command-baseline-run-01.csv' -or
    [string]$quarantine.files[0].relative_path -cne $script:commandPrivatePath -or
    [string]$quarantine.files[0].sha256 -cne $expectedDigest -or
    [string]$quarantine.files[0].quarantine_relative_path -cne
        "$script:commandQuarantinePath/p4-indexed-command-baseline-run-01.csv" -or
    [string]$quarantine.quarantine_path -cne $script:commandQuarantinePath) {
    throw 'The quarantine record does not name the moved private file, its digest and its target.'
}
if (@($quarantine.source_directories).Count -ne 1 -or
    'p4-indexed-command-baseline-run-01.csv' -cnotin @($quarantine.source_directories[0].before_entries) -or
    @($quarantine.source_directories[0].after_entries).Count -ne 0 -or
    [string]$quarantine.source_directories[0].before_listing_sha256 -ceq
        [string]$quarantine.source_directories[0].after_listing_sha256) {
    throw 'The quarantine record does not preserve the listing before and after the move.'
}
if ($script:deviceTree[$script:commandPackage].ContainsKey($script:commandPrivatePath) -or
    -not $script:deviceTree[$script:commandPackage].ContainsKey(
        "$script:commandQuarantinePath/p4-indexed-command-baseline-run-01.csv")) {
    throw 'The private file was not moved into its per-slot quarantine.'
}
if ([string]$script:deviceTree[$script:commandPackage][
        "$script:commandQuarantinePath/p4-indexed-command-baseline-run-01.csv"] -cne 'device,csv,payload') {
    throw 'The quarantine changed the bytes it moved aside.'
}
if (@($script:callLog | Where-Object { $_ -like 'adb:*rm*' -or $_ -like '*pm clear*' }).Count -ne 0) {
    throw 'The quarantine deleted device state.'
}

# A failed collector must still quarantine: that is exactly the state that would block every later slot.
$script:boundaryMode = 'collector-fails'
$script:restored = $false
$script:callLog.Clear()
$script:catalog = $script:commandCatalog
$fixture = New-P4SlotFixture -Name 'quarantine-after-collector-failure' -DeviceFiles (New-P4CommandDeviceFiles)
if (-not (Invoke-P4BoundarySlot $fixture.manifest 'command-baseline')) {
    throw 'A failed collector produced a completed command slot.'
}
$slotRoot = Join-Path $fixture.root 'command-baseline'
if (-not (Test-Path -LiteralPath (Join-Path $slotRoot 'invalid.json'))) {
    throw 'A failed command slot lost its invalid evidence.'
}
$quarantine = Get-P4QuarantineRecord -SlotDirectory $slotRoot
if (@($quarantine.files).Count -ne 1 -or
    $script:deviceTree[$script:commandPackage].ContainsKey($script:commandPrivatePath)) {
    throw 'A failed collector left its private file on the device.'
}
if (-not $script:restored) { throw 'A quarantined failure skipped settings restoration.' }

# An existing quarantine directory is a refusal, never a reuse, and nothing is removed.
$script:boundaryMode = 'command-success'
$script:restored = $false
$script:callLog.Clear()
$script:catalog = $script:commandCatalog
$fixture = New-P4SlotFixture -Name 'quarantine-already-used' `
    -DeviceFiles (New-P4CommandDeviceFiles -WithExistingQuarantine)
if (-not (Invoke-P4BoundarySlot $fixture.manifest 'command-baseline')) {
    throw 'A reused quarantine directory was accepted.'
}
if ($script:lastError -notlike '*never reused or deleted*') {
    throw "A reused quarantine produced the wrong refusal: $script:lastError"
}
if (-not $script:deviceTree[$script:commandPackage].ContainsKey($script:commandPrivatePath) -or
    -not $script:deviceTree[$script:commandPackage].ContainsKey("$script:commandQuarantinePath/leftover.csv")) {
    throw 'A refused quarantine still touched the device.'
}
$quarantine = Get-P4QuarantineRecord -SlotDirectory (Join-Path $fixture.root 'command-baseline')
if ([bool]$quarantine.completed -or [string]$quarantine.stage -cne 'moving' -or
    [string]$quarantine.error -notlike '*never reused or deleted*') {
    throw 'A refused quarantine did not record where it stopped and why.'
}

# An existing but EMPTY quarantine directory is the same refusal: `ls -1` reports it as absent, so the
# reuse check probes the entry itself.
$script:boundaryMode = 'command-success'
$script:restored = $false
$script:callLog.Clear()
$script:catalog = $script:commandCatalog
$fixture = New-P4SlotFixture -Name 'quarantine-already-created-empty' `
    -DeviceFiles (New-P4CommandDeviceFiles -WithEmptyQuarantine)
if (-not (Invoke-P4BoundarySlot $fixture.manifest 'command-baseline')) {
    throw 'An existing but empty quarantine directory was reused.'
}
if ($script:lastError -notlike '*never reused or deleted*') {
    throw "An empty quarantine directory produced the wrong refusal: $script:lastError"
}
if (-not $script:deviceTree[$script:commandPackage].ContainsKey($script:commandPrivatePath)) {
    throw 'A refused empty-quarantine slot still moved the private file.'
}

# `run-as` refusing the sandbox (not debuggable / unknown package) answers on stdout at exit 0. It must
# never read as an entry name, which would let the quarantine record a silent no-op success.
$script:boundaryMode = 'command-success'
$script:restored = $false
$script:callLog.Clear()
$script:catalog = $script:commandCatalog
$fixture = New-P4SlotFixture -Name 'quarantine-run-as-refused' `
    -DeviceFiles (New-P4CommandDeviceFiles) -RunAsFailure $script:commandPackage
if (-not (Invoke-P4BoundarySlot $fixture.manifest 'command-baseline')) {
    throw 'A run-as refusal was accepted as an empty private directory.'
}
$slotRoot = Join-Path $fixture.root 'command-baseline'
if (-not (Test-Path -LiteralPath (Join-Path $slotRoot 'invalid.json'))) {
    throw 'A run-as refusal did not make the slot INVALID.'
}
$quarantine = Get-P4QuarantineRecord -SlotDirectory $slotRoot
if ([bool]$quarantine.completed -or [string]$quarantine.stage -cne 'listing-before' -or
    @($quarantine.files).Count -ne 0 -or [string]$quarantine.error -notlike '*Unable to list*') {
    throw 'A run-as refusal did not record an incomplete quarantine.'
}
if (-not $script:deviceTree[$script:commandPackage].ContainsKey($script:commandPrivatePath)) {
    throw 'A run-as refusal still claimed to have moved the private file.'
}

# --- Completed-chain re-verification across slots. -------------------------------------------------

$script:boundaryMode = 'chain'
$script:restored = $false
$script:callLog.Clear()
$chainSlots = @(
    (New-P4SlotDescriptor -Id 'memory-candidate-common-1' -Lane 'memory' -Role 'candidate' -Runner 'common' -TimeoutSeconds 300),
    (New-P4SlotDescriptor -Id 'host-project-baseline' -Lane 'host' -Role 'baseline' -Runner 'project' -TimeoutSeconds 180),
    (New-P4SlotDescriptor -Id 'memory-candidate-common-2' -Lane 'memory' -Role 'candidate' -Runner 'common' -TimeoutSeconds 300)
)
function Assert-P4ChainDriftRefusal {
    param([string]$Root, [string]$SlotId, [string]$Case)
    $refusals = @(Get-ChildItem -LiteralPath (Join-Path $Root 'admission-refusals') -File -ErrorAction SilentlyContinue)
    if ($refusals.Count -ne 1) { throw "A completed-chain drift was not recorded exactly once: $Case" }
    $refusal = Get-Content -LiteralPath $refusals[0].FullName -Raw | ConvertFrom-Json -AsHashtable
    if ($refusal.schema -cne 'nene-pixel-p4-admission-refusal-v1' -or $refusal.slot_id -cne $SlotId -or
        $refusal.reason_code -cne 'completed-chain-drift' -or $refusal.attempt_consumed -ne $false -or
        $refusal.admission_directory -cne 'not-produced' -or $refusal.reason -notlike '*Completed chain drifted*') {
        throw "The completed-chain drift refusal record is malformed: $Case"
    }
}

$script:catalog = $chainSlots
$fixture = New-P4SlotFixture 'chain-success'
foreach ($slot in $chainSlots) {
    if (Invoke-P4BoundarySlot $fixture.manifest $slot.id) {
        throw "The completed chain rejected a valid slot ($($slot.id)): $script:lastError"
    }
    Assert-P4SealedCompletion -SlotDirectory (Join-Path $fixture.root $slot.id) -Lane $slot.lane
}
if (Test-Path -LiteralPath (Join-Path $fixture.root 'admission-refusals')) {
    throw 'A valid completed chain recorded an admission refusal.'
}

# Lane 5: a required constants revision stops the merge, not the collection, so the chain continues.
$script:boundaryMode = 'chain-publication'
$publicationChain = @(
    (New-P4SlotDescriptor -Id 'publication-baseline' -Lane 'publication' -Role 'baseline' -Runner 'publication' -TimeoutSeconds 300),
    (New-P4SlotDescriptor -Id 'host-project-baseline' -Lane 'host' -Role 'baseline' -Runner 'project' -TimeoutSeconds 180)
)
$script:catalog = $publicationChain
$fixture = New-P4SlotFixture 'chain-publication-revision-required'
foreach ($slot in $publicationChain) {
    if (Invoke-P4BoundarySlot $fixture.manifest $slot.id) {
        throw "A publication revision-required verdict stopped the chain ($($slot.id)): $script:lastError"
    }
    Assert-P4SealedCompletion -SlotDirectory (Join-Path $fixture.root $slot.id) -Lane $slot.lane
}
$publicationResult = Get-Content -LiteralPath (Join-Path $fixture.root 'publication-baseline/completed.json') -Raw |
    ConvertFrom-Json -AsHashtable
if ($publicationResult.verdict -cne 'valid-constants-revision-required') {
    throw 'The publication chain case did not exercise the revision-required verdict.'
}

$script:boundaryMode = 'chain'
$script:catalog = @($chainSlots[0], $chainSlots[1])
$fixture = New-P4SlotFixture 'chain-tampered-seal'
if (Invoke-P4BoundarySlot $fixture.manifest 'memory-candidate-common-1') {
    throw "The first chain slot failed before tampering: $script:lastError"
}
Add-Content -LiteralPath (Join-Path $fixture.root 'memory-candidate-common-1/capture.csv') `
    -Value 'tampered' -Encoding utf8NoBOM
if (-not (Invoke-P4BoundarySlot $fixture.manifest 'host-project-baseline')) {
    throw 'A tampered sealed capture did not stop the next slot.'
}
if ($script:lastError -notlike '*Completed chain drifted*') {
    throw "Sealed-file tampering produced the wrong refusal: $script:lastError"
}
if (Test-Path -LiteralPath (Join-Path $fixture.root 'host-project-baseline')) {
    throw 'A drifted completed chain still reserved the next slot.'
}
Assert-P4ChainDriftRefusal -Root $fixture.root -SlotId 'host-project-baseline' -Case 'chain-tampered-seal'

$script:catalog = @($chainSlots[0], $chainSlots[1])
$fixture = New-P4SlotFixture 'chain-tampered-analysis'
if (Invoke-P4BoundarySlot $fixture.manifest 'memory-candidate-common-1') {
    throw "The first chain slot failed before analysis tampering: $script:lastError"
}
Add-Content -LiteralPath (Join-Path $fixture.root 'memory-candidate-common-1/analysis.json') `
    -Value ' ' -Encoding utf8NoBOM
if (-not (Invoke-P4BoundarySlot $fixture.manifest 'host-project-baseline')) {
    throw 'A tampered prior analysis did not stop the next slot.'
}
if ($script:lastError -notlike '*Completed chain drifted*') {
    throw "Analysis tampering produced the wrong refusal: $script:lastError"
}
Assert-P4ChainDriftRefusal -Root $fixture.root -SlotId 'host-project-baseline' -Case 'chain-tampered-analysis'

$script:catalog = @($chainSlots[0], $chainSlots[1])
$fixture = New-P4SlotFixture 'chain-tampered-restoration'
if (Invoke-P4BoundarySlot $fixture.manifest 'memory-candidate-common-1') {
    throw "The first chain slot failed before restoration tampering: $script:lastError"
}
Add-Content -LiteralPath (Join-Path $fixture.root 'memory-candidate-common-1/restoration.json') `
    -Value ' ' -Encoding utf8NoBOM
if (-not (Invoke-P4BoundarySlot $fixture.manifest 'host-project-baseline')) {
    throw 'A tampered prior restoration proof did not stop the next slot.'
}
if ($script:lastError -notlike '*Completed chain drifted*') {
    throw "Restoration tampering produced the wrong refusal: $script:lastError"
}
Assert-P4ChainDriftRefusal -Root $fixture.root -SlotId 'host-project-baseline' -Case 'chain-tampered-restoration'

# A rewritten prior analysis whose analysis_sha256 was updated to match still cannot name another seal.
$script:catalog = @($chainSlots[0], $chainSlots[1])
$fixture = New-P4SlotFixture 'chain-reseated-analysis'
if (Invoke-P4BoundarySlot $fixture.manifest 'memory-candidate-common-1') {
    throw "The first chain slot failed before the analysis was reseated: $script:lastError"
}
$priorSlotRoot = Join-Path $fixture.root 'memory-candidate-common-1'
$priorAnalysisPath = Join-Path $priorSlotRoot 'analysis.json'
$priorAnalysis = Get-Content -LiteralPath $priorAnalysisPath -Raw | ConvertFrom-Json -AsHashtable
$priorAnalysis.capture_seal_sha256 = $script:unsealedSha
Set-Content -LiteralPath $priorAnalysisPath -Value ($priorAnalysis | ConvertTo-Json -Depth 15) -Encoding utf8NoBOM
$priorCompletedPath = Join-Path $priorSlotRoot 'completed.json'
$priorCompleted = Get-Content -LiteralPath $priorCompletedPath -Raw | ConvertFrom-Json -AsHashtable
$priorCompleted.analysis_sha256 = Get-FileSha256 $priorAnalysisPath
Set-Content -LiteralPath $priorCompletedPath -Value ($priorCompleted | ConvertTo-Json -Depth 15) -Encoding utf8NoBOM
if (-not (Invoke-P4BoundarySlot $fixture.manifest 'host-project-baseline')) {
    throw 'A reseated prior analysis did not stop the next slot.'
}
if ($script:lastError -notlike '*analysis capture seal binding*') {
    throw "Reseating the prior analysis produced the wrong refusal: $script:lastError"
}
Assert-P4ChainDriftRefusal -Root $fixture.root -SlotId 'host-project-baseline' -Case 'chain-reseated-analysis'

# --- Refusal filename collision: the typed IOException catch and the suffix fallback. --------------
# Write-NewInvocationFile opens with FileMode.CreateNew, so a same-millisecond refusal collides. The
# synthetic below forces exactly that collision instead of racing the clock.
$script:refusalCollisionArm = $false
$script:realWriteNewInvocationFile = ${function:Write-NewInvocationFile}
function Write-NewInvocationFile {
    param([Parameter(Mandatory = $true)][string]$Path, [AllowEmptyString()][string]$Text)
    if ($script:refusalCollisionArm -and $Path -like '*admission-refusals*' -and $Path -notlike '*-1.json') {
        throw [IO.IOException]::new("SYNTHETIC_REFUSAL_NAME_COLLISION: $Path")
    }
    & $script:realWriteNewInvocationFile -Path $Path -Text $Text
}
$collisionRoot = Join-Path $script:testRoot 'refusal-collision'
New-Item -ItemType Directory -Path $collisionRoot | Out-Null
$collisionSlot = New-P4SlotDescriptor -Id 'host-project-baseline' -Lane 'host' -Role 'baseline' -Runner 'project'
$script:refusalCollisionArm = $true
try {
    Write-P4AdmissionRefusal -Root $collisionRoot -Slot $collisionSlot -ReasonCode 'completed-chain-drift' `
        -Reason 'SYNTHETIC collision probe' -AdmissionDirectory 'not-produced'
} finally { $script:refusalCollisionArm = $false }
$collisionRefusals = @(Get-ChildItem -LiteralPath (Join-Path $collisionRoot 'admission-refusals') -File)
if ($collisionRefusals.Count -ne 1 -or $collisionRefusals[0].Name -notlike '*-1.json') {
    throw 'A refusal filename collision did not fall back to the next suffix.'
}

Write-Output 'P4_SLOT_BOUNDARY_VALIDATION=pass'
Write-Output ('CASES=' + (@('host-completed', 'device-completed', 'immutable-retry', 'missing-analysis',
    'corrupt-analysis', 'wrong-identity', 'wrong-verdict', 'analysis-not-sealed',
    'admission-quiescence-refusal', 'admission-device-refusal', 'admission-worktree-refusal',
    'stop-failure-independent-restoration', 'collector-failure-still-restores', 'device-drift-after',
    'cleanup-overruns-deadline', 'analyzer-after-restoration-order',
    'private-file-quarantine-moves', 'private-file-quarantine-after-collector-failure',
    'private-file-quarantine-never-reused', 'private-file-quarantine-empty-directory-refused',
    'private-file-quarantine-run-as-refused', 'derived-collector-bound', 'collector-bound-under-cap',
    'completed-chain-reverified',
    'chain-tampered-seal', 'chain-tampered-analysis', 'chain-tampered-restoration',
    'chain-drift-refusal-recorded', 'valid-chain-records-no-refusal',
    'chain-reseated-analysis', 'chain-publication-revision-required',
    'analysis-wrong-seal', 'analysis-no-seal-binding',
    'collector-forges-wrapper-evidence', 'refusal-name-collision-fallback', 'fixed-slot-budgets',
    'preflight-stage-slot', 'role-scoped-artifact-packages',
    'manifest-derived-packages', 'real-quiescence-record', 'real-worktree-probe') -join ','))
Write-Output "SYNTHETIC_FIXTURE_DIRECTORY=$script:testRoot"
Write-Output "SYNTHETIC_PROBE_DIRECTORY=$script:probeRoot"

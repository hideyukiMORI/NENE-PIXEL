# Focused host-only checks of the layer-phase run (Issue #145 R3, R7 points 3 and 6): preservation once,
# 18 slots through the real Invoke-P4IndexedSlot with the stopped cleanup before the seal, the gross
# stop, the phase deadline, and the restoration in the finally path. Native boundaries (snapshot,
# isolation, restoration, admission, collector/analyzer processes, cleanup, seal) are mocks.
# No ADB, Gradle, or device.
param([Parameter(Mandatory)][string]$OutputDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
$lab = [IO.Path]::GetFullPath((Get-NenePixelLabPath 'evidence' -StartDirectory $PSScriptRoot)).TrimEnd('\', '/')
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $OutputDirectory.StartsWith($lab + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    (Test-Path -LiteralPath $OutputDirectory)) { throw 'Fresh lab output required.' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$phasePath = Join-Path $PSScriptRoot 'measurements/invoke-p4-layer-phase.ps1'
$invokePath = Join-Path $PSScriptRoot 'measurements/invoke-p4-indexed-slot.ps1'
. $phasePath
$script:P4CaptureDrainSeconds = 0

$script:cases = [Collections.Generic.List[object]]::new()
$result = [ordered]@{ status = 'failure'; bounds = $null; cases = @() }
function Check([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Case([string]$Group, [string]$CaseName, [scriptblock]$Action) {
    $errorText = $null; try { & $Action | Out-Null } catch { $errorText = $_.ToString() }
    $script:cases.Add([ordered]@{ group = $Group; name = $CaseName; passed = ($null -eq $errorText); error = $errorText })
    Check ($null -eq $errorText) "Case failed: $Group / $CaseName : $errorText"
}
function Save-Json([string]$Path, $Value) {
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 50), [Text.UTF8Encoding]::new($false))
}

$phase = 'nene-pixel-p4-layer-phase-verification-v1'
$app = 'io.github.hideyukimori.nenepixel'
$catalog = @(Get-P4SlotCatalog -ProtocolId $phase)
$ids = @($catalog | ForEach-Object { [string]$_.id })
$originalApk = 'e' * 64

function New-PhaseManifest([string]$Root, [string]$PreservationPath, [string]$PreservationSha256) {
    $kinds = @{
        app_debug = [ordered]@{ target_package = $app; test_package = 'none' }
        test_debug = [ordered]@{ target_package = $app; test_package = "$app.test" }
        app_release_like = [ordered]@{ target_package = $app; test_package = 'none' }
        publication_test = [ordered]@{ target_package = "$app.publication"; test_package = "$app.publication" } }
    $roles = [ordered]@{}
    $digit = 0
    foreach ($entry in @(Get-P4LayerArtifactCatalog $phase)) {
        $digit++
        $artifacts = [ordered]@{}
        foreach ($kind in $entry.artifact_kinds) {
            $artifacts[$kind] = [ordered]@{ target_package = $kinds[$kind].target_package
                test_package = $kinds[$kind].test_package; sha256 = ([string]$digit) * 64 }
        }
        $roles[$entry.role] = [ordered]@{ worktree = "D:/NENE-PIXEL/clones/t6-$($entry.role)"
            production_commit = $entry.production_commit; build_commit = ([string]$digit) * 40; artifacts = $artifacts }
    }
    return [ordered]@{ protocol = [ordered]@{ id = $phase }; experiment_id = 'e145-t6'
        output_directory = $Root; roles = $roles
        tools = [ordered]@{ adb = [ordered]@{ path = 'D:/NENE-PIXEL/tools/adb.exe' }
            runner = [ordered]@{ path = 'runner.ps1' }; analyzer = [ordered]@{ path = 'analyzer.ps1' } }
        device = [ordered]@{ serial = 'T6SERIAL'; asset_preservation = [ordered]@{ path = $PreservationPath
            sha256 = $PreservationSha256; session = 's145-t6' } } }
}

# --- Scenario state and mocks (native boundaries only) -------------------------------------------
$script:events = [Collections.Generic.List[string]]::new()
$script:scenario = @{}
$script:fakeNow = [datetime]::UtcNow
$script:P4LayerPhaseClock = { $script:fakeNow }
function Reset-Scenario([hashtable]$Settings) {
    $script:events.Clear()
    $script:scenario = $Settings
    $script:fakeNow = [datetime]::new(2026, 10, 7, 0, 0, 0, [DateTimeKind]::Utc)
}
function Log([string]$Text) { $script:events.Add($Text) }

function New-P4PrivateSnapshot {
    param($Context, $Stage, $MaximumArchiveBytes, $MaximumApkBytes)
    Log 'snapshot'
    # The measured sizes the transfer bound scales with: 1,000 B of inventory files, a 2,000 B APK.
    $inventoryPath = Join-Path $Context.output_directory "$Stage-inventory-before.json"
    Save-Json $inventoryPath @([ordered]@{ Path = 'files'; Type = 'directory' },
        [ordered]@{ Path = 'files/a'; Type = 'file'; Size = 600 }, [ordered]@{ Path = 'files/b'; Type = 'file'; Size = 400 })
    $apkPath = Join-Path $Context.output_directory "$Stage-original.apk"
    [IO.File]::WriteAllBytes($apkPath, [byte[]]::new(2000))
    $record = [ordered]@{ status = 'verified-snapshot'; stage = $Stage; apk_sha256 = $originalApk
        maximum_archive_bytes = $MaximumArchiveBytes; maximum_apk_bytes = $MaximumApkBytes
        inventory_path = $inventoryPath; apk_path = $apkPath }
    Save-Json (Join-Path $Context.output_directory "$Stage-snapshot.json") $record
    return [pscustomobject]$record
}
function Invoke-P4PrivateIsolation {
    param($Context, $Session, $ExperimentId, $SnapshotPath)
    Log 'isolation'
    $path = Join-Path $Context.output_directory "p4-isolation-$Session-$ExperimentId-result.json"
    Save-Json $path ([ordered]@{ schema = 'nene-pixel-device-preservation-v2'; status = 'preserved'; session = $Session })
    return [pscustomobject]@{ ResultPath = $path; Record = [pscustomobject]@{ status = 'preserved' } }
}
function Invoke-P4PrivateRestoration {
    param($Context, $PreservationPath, $ExpectedInstalledApkHash)
    Log "restoration:$ExpectedInstalledApkHash"
    $path = Join-Path $Context.output_directory 'p4-restoration-s145-t6-e145-t6-result.json'
    if ($script:scenario.ContainsKey('restorationFails')) {
        Save-Json $path ([ordered]@{ status = 'failure'; reason = 'mock restoration failure' })
        throw 'mock restoration failure'
    }
    Save-Json $path ([ordered]@{ status = 'restored' })
    return [pscustomobject]@{ ResultPath = $path; Record = [pscustomobject]@{ status = 'restored' } }
}
function Assert-P4ManifestArtifacts { param($Manifest, $Worktree, $Stage) if ($Stage -ceq 'reservation') { Log 'reservation' } }
function Assert-P4CompletedChain { param($Root, $Catalog, $SlotId, $ManifestHash) }
function Invoke-P4SlotAdmission {
    param($Manifest, $Slot, $Root)
    $directory = Join-Path $Root "admission-pending-$($Slot.id)"
    [void][IO.Directory]::CreateDirectory($directory)
    foreach ($name in @('quiescence.json', 'worktree-before.json', 'device-state-before.json')) {
        Save-Json (Join-Path $directory $name) @{ ok = $true }
    }
    return [pscustomobject]@{ directory = $directory }
}
function Get-P4OriginalDeviceSettings { param($Manifest, $Directory) return @{ settings = 'original' } }
function Invoke-P4SlotRestoration { param($Manifest, $Slot, $Directory, $Original) return [pscustomobject]@{ sha256 = 'f' * 64; restored = $true } }
function Stop-P4RemoteProcesses { param($Manifest, $Slot, $Directory) }
function Get-P4WorktreeState { param($Worktree, $Stage, $Directory) Save-Json (Join-Path $Directory 'worktree-after.json') @{ clean = $true }; return @{} }
function Assert-P4WorktreeState { param($State, $ExpectedCommit, $Context) }
function Assert-P4AnalysisSealAgreement { param($Analysis, $Seal, $Slot) }
function Invoke-BoundedNativeCommand {
    param($RepositoryRoot, $ExecutablePath, $LogPath, $TimeoutSeconds, $NativeArguments)
    $slotId = $NativeArguments[[Array]::IndexOf([object[]]$NativeArguments, '-SlotId') + 1]
    if ([IO.Path]::GetFileName($LogPath) -ceq 'collector.log') {
        Log "collector:$slotId"
        if ($script:scenario.ContainsKey('advanceAfter') -and $script:scenario.advanceAfter -ceq $slotId) {
            $script:fakeNow = $script:fakeNow.AddSeconds(1000000)
        }
        if ($script:scenario.ContainsKey('collectorFails') -and $script:scenario.collectorFails -ceq $slotId) {
            return [pscustomobject]@{ ExitCode = 1 }
        }
        return [pscustomobject]@{ ExitCode = 0 }
    }
    Log "analyzer:$slotId"
    $output = $NativeArguments[[Array]::IndexOf([object[]]$NativeArguments, '-OutputDirectory') + 1]
    Save-Json (Join-Path $output 'analysis.json') @{ slot_id = $slotId }
    return [pscustomobject]@{ ExitCode = 0 }
}
function Invoke-P4LayerSlotCleanup {
    param($Context, $Manifest, $Slot, $ManifestSha256, $CollectorSucceeded)
    Log "cleanup:$($Slot.id):$CollectorSucceeded"
    $directory = Join-Path $Context.output_directory 'phase-cleanup'
    [void][IO.Directory]::CreateDirectory($directory)
    $now = [datetime]::UtcNow
    Save-Json (Join-Path $directory 'result.json') ([ordered]@{ status = 'stopped-and-reset'
        started_utc = $now.ToString('o'); ended_utc = $now.AddSeconds(2).ToString('o') })
    if ($script:scenario.ContainsKey('noDebugAccess') -and $script:scenario.noDebugAccess -ceq $Slot.id) { throw 'mock cleanup failed before debug access' }
    $role = Get-P4SlotArtifactRole $Manifest $Slot
    $access = Join-Path $directory 'debug-access'
    [void][IO.Directory]::CreateDirectory($access)
    $expected = [string]$Manifest.roles[$role].artifacts.app_debug.sha256
    $intentPath = Join-Path $access 'install-app_debug-intent.json'
    Save-Json $intentPath ([ordered]@{ role = $role; kind = 'app_debug'; expected_sha256 = $expected })
    $observed = if ($script:scenario.ContainsKey('foreignInstall') -and $script:scenario.foreignInstall -ceq $Slot.id) { 'd' * 64 } else { $expected }
    Save-Json (Join-Path $access 'install-app_debug.json') ([ordered]@{ role = $role; kind = 'app_debug'; status = 'success'
        expected_sha256 = $expected; observed_sha256 = $observed; intent_sha256 = Get-FileSha256 $intentPath })
    return [pscustomobject]@{ ResultPath = (Join-Path $directory 'result.json') }
}
function New-P4CaptureSeal {
    param($Directory, $SlotId, $Slot, $RequireFrameSlot)
    Log "seal:$SlotId"
    Save-Json (Join-Path $Directory 'capture-seal.json') @{ slot_id = $SlotId }
    return @{ files = @() }
}
function Read-P4FreshAnalysis {
    param($Path, $Slot, $ManifestHash, $StartedUtc, $CaptureSealSha256)
    $analysis = [ordered]@{ slot_id = $Slot.id; verdict = 'pass'; status = $null }
    if ($Slot.lane -ceq 'frame') {
        $gross = $script:scenario.ContainsKey('gross') -and $script:scenario.gross -ceq $Slot.id
        $analysis.gross_regression = $gross
        $analysis.gross_regression_basis = [ordered]@{ families = @('canvas16_tap') }
    }
    return $analysis
}
$realWriteRecord = ${function:Write-P4LayerPhaseRecord}
function Write-P4LayerPhaseRecord([string]$Path, $Record) { Log 'record'; & $realWriteRecord $Path $Record }

function Invoke-Scenario([string]$Name, [hashtable]$Settings) {
    Reset-Scenario $Settings
    $directory = Join-Path $OutputDirectory $Name
    [void][IO.Directory]::CreateDirectory($directory)
    $root = Join-Path $directory 'phase-root'
    $context = [ordered]@{ repository_root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path; output_directory = $directory
        adb_path = 'D:/NENE-PIXEL/tools/adb.exe'; serial = 'T6SERIAL'; package = $app }
    $script:currentRoot = $root
    $reserve = {
        param($Preservation)
        Log 'reserve'
        [void][IO.Directory]::CreateDirectory($script:currentRoot)
        $path = Join-Path $script:currentRoot 'preflight.json'
        Save-Json $path (New-PhaseManifest $script:currentRoot $Preservation.path $Preservation.sha256)
        return $path
    }
    $thrown = $null
    $returned = $null
    try {
        if ($Settings.ContainsKey('draft')) {
            # The canonical reservation: a draft without the preservation pin, reserved through preflight.
            $draft = New-PhaseManifest $root 'x' ('c' * 64)
            if ($Settings.draft -ceq 'pinned') { $draft.device.asset_preservation.session = 's145-t6' }
            else { [void]$draft.device.Remove('asset_preservation') }
            $draftPath = Join-Path $directory 'draft.json'
            Save-Json $draftPath $draft
            $returned = Invoke-P4LayerPhase -Context $context -Session 's145-t6' -ExperimentId 'e145-t6' -ManifestDraftPath $draftPath
        } else {
            $returned = Invoke-P4LayerPhase -Context $context -Session 's145-t6' -ExperimentId 'e145-t6' -ReserveManifest $reserve
        }
    } catch { $thrown = $_.Exception.Message }
    $recordPath = Join-Path $directory 'p4-layer-phase-s145-t6-e145-t6.json'
    $record = if (Test-Path -LiteralPath $recordPath) { Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json -AsHashtable } else { $null }
    return [pscustomobject]@{ Events = @($script:events.ToArray()); Record = $record; Thrown = $thrown; Directory = $directory
        Root = $root; RecordPath = $recordPath; Returned = $returned }
}
function Count([object[]]$Events, [string]$Prefix) { return @($Events | Where-Object { $_.StartsWith($Prefix) }).Count }
function Slot($Record, [string]$Id) { return @($Record.slots | Where-Object { $_.slot_id -ceq $Id })[0] }
function Assert-CleanupBeforeSeal([object[]]$Events) {
    foreach ($id in $ids) {
        $collector = [Array]::IndexOf([object[]]$Events, "collector:$id")
        if ($collector -lt 0) { continue }
        $cleanup = @(0..($Events.Count - 1) | Where-Object { $Events[$_].StartsWith("cleanup:${id}:") })
        $seal = [Array]::IndexOf([object[]]$Events, "seal:$id")
        Check ($cleanup.Count -eq 1 -and $seal -gt $cleanup[0] -and $cleanup[0] -gt $collector) "cleanup/seal order $id"
    }
}

try {
    # Bounds: the hang-bound table from the real planner and cleanup clock.
    Case 'Bounds' 'slot and phase hang bounds' {
        $manifest = New-PhaseManifest (Join-Path $OutputDirectory 'bounds-root') 'x' ('c' * 64)
        $bounds = Get-P4LayerPhaseBounds -Manifest $manifest -ManifestSha256 ('a' * 64) -Catalog $catalog
        $result.bounds = $bounds
        Check (@($bounds.slots).Count -eq 18) 'slot count'
        # Per-slot values from the protocol clock paragraph and the planner output; the totals alone
        # cannot catch values swapped between slots, so each slot is checked.
        $expected = @{
            'frame-1-single-baseline-decision' = @(2700, 540, 3450)
            'frame-2-single-candidate-decision' = @(2730, 600, 3540)
            'frame-3-layers16-baseline-decision' = @(3450, 660, 4320)
            'frame-4-layers16-candidate-decision' = @(3450, 660, 4320)
            'frame-5-underlay-baseline-decision' = @(2625, 660, 3495)
            'frame-6-underlay-candidate-decision' = @(2625, 660, 3495)
            'publication-layers16-candidate' = @(930, 780, 1920)
            'saf-save-layers16-candidate' = @(1290, 720, 2220)
        }
        foreach ($arm in 'baseline', 'candidate') { foreach ($n in 1..5) { $expected["memory-layers16-$arm-$n"] = @(1050, 600, 1860) } }
        Check ($expected.Count -eq 18 -and @($bounds.slots).Count -eq $expected.Count) "slot table $($expected.Count)/$(@($bounds.slots).Count)"
        $cleanupSum = 0
        foreach ($slot in $bounds.slots) {
            Check ($slot.slot_seconds -eq $slot.collector_seconds + $slot.cleanup_seconds + 90 + 120) "slot sum $($slot.slot_id)"
            $want = if ($expected.ContainsKey($slot.slot_id)) { $expected[$slot.slot_id] } else { $null }
            Check ($null -ne $want -and $slot.collector_seconds -eq $want[0] -and $slot.cleanup_seconds -eq $want[1] -and $slot.slot_seconds -eq $want[2]) "slot bound $($slot.slot_id) $($slot.collector_seconds)/$($slot.cleanup_seconds)/$($slot.slot_seconds)"
            $cleanupSum += $slot.cleanup_seconds
        }
        Check ($cleanupSum -eq 11280) "cleanup total $cleanupSum"
        $p = $bounds.preservation
        # Calls x 276 ms x 2 (+ snapshot transfers 3,204 + 4,738 ms x 2, + install 120 s), up to 60 s:
        # snapshot 26 calls 30.236 s -> 60, isolation 256 calls 141.312 s -> 180, restoration 400 calls 340.8 s -> 360.
        Check ($p.snapshot_calls -eq 26 -and $p.isolation_calls -eq 256 -and $p.restoration_calls -eq 400) "preservation calls $($p.snapshot_calls)/$($p.isolation_calls)/$($p.restoration_calls)"
        Check ($p.snapshot_seconds -eq 60 -and $p.isolation_seconds -eq 180 -and $p.restoration_seconds -eq 360) "preservation $($p.snapshot_seconds)/$($p.isolation_seconds)/$($p.restoration_seconds)"
        Check ($bounds.slots_seconds -eq 45360) "slots total $($bounds.slots_seconds)"
        Check ($bounds.phase_seconds -eq 45960 -and $bounds.phase_seconds -eq $p.preservation_seconds + $bounds.slots_seconds + $p.restoration_seconds) "phase sum $($bounds.phase_seconds)"
        Check ($bounds.kind -ceq 'hang-bound' -and $bounds.note -match 'not measured') 'hang-bound label'
        # No snapshot yet: the 10-03 sizes stand in, labelled estimated (3,204 + 4,738 ms exactly).
        Check ($p.snapshot_bytes.basis -ceq 'estimated' -and $p.snapshot_bytes.archive_bytes -eq 10335232 -and
            $p.snapshot_bytes.apk_bytes -eq 12402152 -and $p.snapshot_transfer_milliseconds -eq 7942) "estimated transfer $($p.snapshot_transfer_milliseconds)"
    }
    Case 'Bounds' 'snapshot transfers scale with the measured bytes' {
        # At the caps (268,435,456 B archive, 134,217,728 B APK): ceil(B / 10,335,232 x 3,204) = 83,218 ms,
        # ceil(B / 12,402,152 x 4,738) = 51,276 ms; (26 x 276 + 83,218 + 51,276) x 2 = 283,340 ms -> 300 s.
        $directory = Join-Path $OutputDirectory 'bounds-size'
        [void][IO.Directory]::CreateDirectory($directory)
        $inventory = Join-Path $directory 'inventory.json'
        Save-Json $inventory @([ordered]@{ Path = 'files/big'; Type = 'file'; Size = 268435456 }, [ordered]@{ Path = 'files'; Type = 'directory' })
        $apk = Join-Path $directory 'original.apk'
        $stream = [IO.File]::Open($apk, [IO.FileMode]::CreateNew); try { $stream.SetLength(134217728) } finally { $stream.Dispose() }
        $p = Get-P4LayerPhasePreservationBounds ([pscustomobject]@{ inventory_path = $inventory; apk_path = $apk })
        Check ($p.snapshot_bytes.basis -ceq 'measured' -and $p.snapshot_tar_milliseconds -eq 83218 -and
            $p.snapshot_apk_milliseconds -eq 51276 -and $p.snapshot_seconds -eq 300) "cap transfer $($p.snapshot_tar_milliseconds)/$($p.snapshot_apk_milliseconds)/$($p.snapshot_seconds)"
        $manifest = New-PhaseManifest (Join-Path $OutputDirectory 'bounds-size-root') 'x' ('c' * 64)
        $bounds = Get-P4LayerPhaseBounds -Manifest $manifest -ManifestSha256 ('a' * 64) -Catalog $catalog -Snapshot ([ordered]@{ inventory_path = $inventory; apk_path = $apk })
        Check ($bounds.phase_seconds -eq 46200) "phase at the caps $($bounds.phase_seconds)"
        Remove-Item -LiteralPath $apk
    }

    # Reservation: the canonical ReserveManifest pins this preservation into the draft and reserves it
    # through the preflight reservation stage (Assert-P4ManifestArtifacts -Stage reservation).
    Case 'Reservation' 'default reservation: draft pinned after isolation, reserved through preflight, 18 slots run' {
        $run = Invoke-Scenario 'r-default' @{ draft = 'unpinned' }
        Check ($null -eq $run.Thrown) "threw: $($run.Thrown)"
        $e = $run.Events
        Check ($e[0] -ceq 'snapshot' -and $e[1] -ceq 'isolation' -and $e[2] -ceq 'reservation' -and (Count $e 'reservation') -eq 1) "start order $($e[0..3] -join ' ')"
        $r = $run.Record
        Check ($r.status -ceq 'complete' -and (Count $e 'collector:') -eq 18) "status $($r.status)"
        $pinnedPath = Join-Path $run.Directory 'p4-layer-manifest-s145-t6-e145-t6.json'
        $pinned = Get-Content -LiteralPath $pinnedPath -Raw | ConvertFrom-Json -AsHashtable
        Check ($pinned.device.asset_preservation.session -ceq 's145-t6' -and $pinned.device.asset_preservation.sha256 -ceq $r.preservation.sha256 -and
            [IO.Path]::GetFullPath($pinned.device.asset_preservation.path) -ceq $r.preservation.path) 'pin'
        Check ((Get-FileSha256 $pinnedPath) -ceq $r.manifest_sha256 -and $r.manifest_path -ceq [IO.Path]::GetFullPath((Join-Path $run.Root 'preflight.json'))) 'reserved copy'
        Check ($r.deadline.preservation.snapshot_bytes.basis -ceq 'measured' -and $r.deadline.preservation.snapshot_bytes.archive_bytes -eq 1000 -and
            $r.deadline.preservation.snapshot_bytes.apk_bytes -eq 2000 -and $r.deadline.preservation.snapshot_transfer_milliseconds -eq 2) 'measured snapshot bytes'
    }
    Case 'Reservation' 'a draft that already pins a preservation: no reservation, no slot, original restored' {
        $run = Invoke-Scenario 'r-pinned' @{ draft = 'pinned' }
        Check ($run.Thrown -match 'without a preservation pin') "thrown $($run.Thrown)"
        Check ((Count $run.Events 'reservation') -eq 0 -and (Count $run.Events 'collector:') -eq 0 -and
            (Count $run.Events "restoration:$originalApk") -eq 1) "events $($run.Events -join ' ')"
    }
    Case 'Reservation' 'both or neither of draft and callback: refused before any device action' {
        Reset-Scenario @{}
        foreach ($pair in @(@{ name = 'neither'; args = @{} }, @{ name = 'both'; args = @{ ManifestDraftPath = 'x.json'; ReserveManifest = { 'x' } } })) {
            $directory = Join-Path $OutputDirectory "r-$($pair.name)"
            [void][IO.Directory]::CreateDirectory($directory)
            $context = [ordered]@{ repository_root = 'x'; output_directory = $directory; adb_path = 'x'; serial = 'T6SERIAL'; package = $app }
            $arguments = $pair.args
            $refused = $false
            try { Invoke-P4LayerPhase -Context $context -Session 's145-t6' -ExperimentId 'e145-t6' @arguments | Out-Null }
            catch { $refused = "$_" -match 'exactly one of ManifestDraftPath' }
            Check $refused "not refused: $($pair.name)"
            Check ($script:events.Count -eq 0 -and @(Get-ChildItem -LiteralPath $directory).Count -eq 0) "touched: $($pair.name)"
        }
    }

    # (a) all 18 succeed.
    Case 'Flow' '(a) 18 slots: snapshot 1, isolation 1, cleanup 18, restoration 1, record last' {
        $run = Invoke-Scenario 'a-all' @{}
        Check ($null -eq $run.Thrown) "threw: $($run.Thrown)"
        $e = $run.Events
        Check ((Count $e 'snapshot') -eq 1 -and (Count $e 'isolation') -eq 1 -and (Count $e 'cleanup:') -eq 18 -and
            (Count $e 'restoration:') -eq 1 -and (Count $e 'record') -eq 1) "counts $($e -join ' ')"
        Check ($e[0] -ceq 'snapshot' -and $e[1] -ceq 'isolation' -and $e[2] -ceq 'reserve') 'start order'
        Check ($e[-2].StartsWith('restoration:') -and $e[-1] -ceq 'record') 'end order'
        Check ((@($e | Where-Object { $_.StartsWith('collector:') }) | ForEach-Object { $_.Substring(10) }) -join ',' -ceq ($ids -join ',')) 'catalog order'
        Assert-CleanupBeforeSeal $e
        Check (@($e | Where-Object { $_ -match '^cleanup:.*:False$' }).Count -eq 0) 'collector success flag'
        $r = $run.Record
        Check ($r.status -ceq 'complete' -and $r.restoration.status -ceq 'restored') "status $($r.status)"
        Check (@($r.slots | Where-Object { $_.status -ceq 'completed' }).Count -eq 18) 'completed 18'
        Check ($r.manifest_sha256 -cmatch '^[0-9a-f]{64}$' -and $null -ne $r.snapshot -and $null -ne $r.preservation -and $null -ne $r.chain) 'record bindings'
        Check (@($r.slots | Where-Object { $null -eq $_.cleanup_seconds -or $null -eq $_.elapsed_seconds }).Count -eq 0) 'durations recorded'
        $candidateApk = '4' * 64
        Check ($r.restoration.expected_apk.sha256 -ceq $candidateApk -and $r.restoration.expected_apk.slot_id -ceq $ids[-1] -and
            $e[-2] -ceq "restoration:$candidateApk") "expected APK $($r.restoration.expected_apk.sha256)"
        Check ($r.deadline.kind -ceq 'hang-bound' -and $null -ne $r.deadline.deadline_utc) 'deadline accounting'
        foreach ($id in $ids) { Check ((Get-Content -LiteralPath (Join-Path $run.Root "$id/started.json") -Raw | ConvertFrom-Json).phase_cleanup_seconds -gt 0) "started cleanup $id" }
    }

    # (b) frame-2 gross.
    Case 'Flow' '(b) frame-2 gross: frame-3..6 skipped, memory and storage run' {
        $run = Invoke-Scenario 'b-gross' @{ gross = 'frame-2-single-candidate-decision' }
        Check ($null -eq $run.Thrown) "threw: $($run.Thrown)"
        $r = $run.Record; $e = $run.Events
        foreach ($id in $ids[2..5]) {
            $s = Slot $r $id
            Check ($s.status -ceq 'skipped' -and $s.reason -ceq 'gross-regression-stop' -and $s.stopped_by -ceq 'frame-2-single-candidate-decision') "skip $id"
            Check ((Count $e "collector:$id") -eq 0 -and (Count $e "cleanup:${id}:") -eq 0) "not run $id"
        }
        foreach ($id in $ids[6..17]) { Check ((Slot $r $id).status -ceq 'completed') "continue $id" }
        Check ((Slot $r $ids[1]).status -ceq 'completed' -and $null -eq (Slot $r $ids[1]).stopped_by) "no stopped_by $($ids[1])"
        Check ((Count $e 'cleanup:') -eq 14) "cleanup count $(Count $e 'cleanup:')"
        Assert-CleanupBeforeSeal $e
        Check ($r.chain.stop.slot_id -ceq 'frame-2-single-candidate-decision' -and $r.status -ceq 'complete') 'chain stop'
        Check ((Count $e 'restoration:') -eq 1) 'restoration'
    }

    # (c) slot 7 collector failure.
    Case 'Flow' '(c) slot 7 collector fails: cleanup runs, the rest continue, restoration runs' {
        $seventh = $ids[6]
        $run = Invoke-Scenario 'c-collector' @{ collectorFails = $seventh }
        Check ($null -eq $run.Thrown) "threw: $($run.Thrown)"
        $r = $run.Record; $e = $run.Events
        Check ((Count $e "cleanup:${seventh}:False") -eq 1) 'cleanup on failure'
        Assert-CleanupBeforeSeal $e
        Check ((Slot $r $seventh).status -ceq 'invalid' -and (Slot $r $seventh).reason -match 'Collector exited 1') "slot 7 $((Slot $r $seventh).status)"
        Check ((Count $e 'analyzer:') -eq 17 -and (Count $e "analyzer:$seventh") -eq 0) 'no analysis for the failed slot'
        foreach ($id in $ids[7..17]) { Check ((Slot $r $id).status -ceq 'completed') "continue $id" }
        Check ((Count $e 'cleanup:') -eq 18 -and (Count $e 'restoration:') -eq 1) 'cleanup 18 and restoration'
        Check ($r.status -ceq 'restored-with-slot-failures') "status $($r.status)"
        Check (Test-Path -LiteralPath (Join-Path $run.Root "$seventh/phase-cleanup/result.json")) 'cleanup record kept'
    }

    # (d) restoration failure.
    Case 'Restore' '(d) restoration failure: failure record kept, nothing removed' {
        $run = Invoke-Scenario 'd-restore' @{ restorationFails = $true }
        Check ($run.Thrown -match 'Phase restoration failed') "thrown $($run.Thrown)"
        $r = $run.Record
        Check ($null -ne $r -and $r.status -ceq 'failure' -and $r.restoration.status -ceq 'failure' -and
            $r.restoration.reason -match 'mock restoration failure') 'failure record'
        $preservation = Join-Path $run.Directory 'preservation-s145-t6-e145-t6'
        foreach ($name in @('p4-snapshot-s145-t6-e145-t6-snapshot.json', 'p4-isolation-s145-t6-e145-t6-result.json',
                'p4-restoration-s145-t6-e145-t6-result.json')) {
            Check (Test-Path -LiteralPath (Join-Path $preservation $name)) "kept $name"
        }
        foreach ($id in $ids) { Check (Test-Path -LiteralPath (Join-Path $run.Root "$id/capture-seal.json")) "slot kept $id" }
    }
    Case 'Restore' 'expected APK undetermined without the last cleanup install record: restoration not called' {
        $run = Invoke-Scenario 'd-undetermined' @{ noDebugAccess = $ids[-1] }
        Check ($run.Thrown -match 'Phase restoration failed') "thrown $($run.Thrown)"
        Check ((Count $run.Events 'restoration:') -eq 0) 'restoration not called with a guessed APK'
        Check ($run.Record.restoration.reason -match 'Expected APK undetermined') "reason $($run.Record.restoration.reason)"
    }
    Case 'Restore' 'an install result that observed another APK is not taken as the expectation' {
        $run = Invoke-Scenario 'd-foreign' @{ foreignInstall = $ids[-1] }
        Check ((Count $run.Events 'restoration:') -eq 0 -and $run.Record.restoration.status -ceq 'failure') 'foreign APK refused'
    }

    # (e) phase deadline.
    Case 'Deadline' '(e) deadline exceeded: no new slot, restoration runs' {
        $run = Invoke-Scenario 'e-deadline' @{ advanceAfter = $ids[2] }
        $r = $run.Record; $e = $run.Events
        Check ($null -eq $run.Thrown) "threw: $($run.Thrown)"
        Check ((Count $e 'collector:') -eq 3 -and (Count $e 'cleanup:') -eq 3) "ran $(Count $e 'collector:')"
        Check ((Slot $r $ids[2]).status -ceq 'completed') 'running slot not stopped'
        foreach ($id in $ids[3..17]) { Check ((Slot $r $id).status -ceq 'not-started' -and (Slot $r $id).reason -match 'phase-deadline') "not started $id" }
        Check ((Count $e 'restoration:') -eq 1 -and $r.restoration.status -ceq 'restored') 'restoration ran'
        Check ($r.restoration.expected_apk.slot_id -ceq $ids[2]) 'expected APK from the last started slot'
        Check ($r.status -ceq 'restored-with-slot-failures') "status $($r.status)"
    }

    # (f) an existing record is never overwritten.
    Case 'Record' '(f) existing phase record: refused before any device action' {
        $directory = Join-Path $OutputDirectory 'f-existing'
        [void][IO.Directory]::CreateDirectory($directory)
        $path = Join-Path $directory 'p4-layer-phase-s145-t6-e145-t6.json'
        [IO.File]::WriteAllText($path, 'prior')
        Reset-Scenario @{}
        $context = [ordered]@{ repository_root = 'x'; output_directory = $directory; adb_path = 'x'; serial = 'T6SERIAL'; package = $app }
        $refused = $false
        try { Invoke-P4LayerPhase -Context $context -Session 's145-t6' -ExperimentId 'e145-t6' -ReserveManifest { 'none' } | Out-Null }
        catch { $refused = "$_" -match 'never overwritten' }
        Check $refused 'not refused'
        Check ($script:events.Count -eq 0) "events $($script:events -join ' ')"
        Check ([IO.File]::ReadAllText($path) -ceq 'prior') 'record changed'
    }
    Case 'Record' 'a manifest that does not pin this preservation stops before any slot; restoration still runs' {
        Reset-Scenario @{}
        $directory = Join-Path $OutputDirectory 'f-foreign-manifest'
        [void][IO.Directory]::CreateDirectory($directory)
        $root = Join-Path $directory 'phase-root'
        $context = [ordered]@{ repository_root = 'x'; output_directory = $directory; adb_path = 'x'; serial = 'T6SERIAL'; package = $app }
        $script:currentRoot = $root
        $reserve = { param($Preservation) [void][IO.Directory]::CreateDirectory($script:currentRoot)
            $p = Join-Path $script:currentRoot 'preflight.json'
            Save-Json $p (New-PhaseManifest $script:currentRoot $Preservation.path ('0' * 64)); return $p }
        $thrown = $null
        try { Invoke-P4LayerPhase -Context $context -Session 's145-t6' -ExperimentId 'e145-t6' -ReserveManifest $reserve | Out-Null }
        catch { $thrown = "$_" }
        Check ($thrown -match 'does not pin') "thrown $thrown"
        Check ((Count $script:events 'collector:') -eq 0 -and (Count $script:events "restoration:$originalApk") -eq 1) 'no slot; original APK restored'
    }

    # Wiring: the slot wrapper's phase path calls the cleanup before the seal and bounds it.
    Case 'Wiring' 'Invoke-P4IndexedSlot: phase cleanup before the seal, in the deadline' {
        $parseErrors = $null
        $ast = [Management.Automation.Language.Parser]::ParseFile($invokePath, [ref]$null, [ref]$parseErrors)
        Check ($parseErrors.Count -eq 0) 'parse'
        $text = $ast.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and
            $n.Name -ceq 'Invoke-P4IndexedSlot' }, $true).Extent.Text
        $cleanup = $text.IndexOf('Invoke-P4LayerSlotCleanup')
        Check ($cleanup -gt 0 -and $cleanup -lt $text.IndexOf('New-P4CaptureSeal')) 'cleanup not before seal'
        Check ($text.Contains('-CollectorSucceeded ($null -eq $failure)')) 'success flag'
        Check ($text.Contains('$collectorTimeoutSeconds + $phaseCleanupSeconds +')) 'deadline'
        Check ($text.Contains('-not $route.phase -and $null -ne $lanePlan')) 'v7 quarantine gate'
    }
    Case 'Wiring' 'reservation: the wrapper default reaches the preflight reservation stage for the phase' {
        $function = { param($Path, $Name) $errs = $null; $ast = [Management.Automation.Language.Parser]::ParseFile($Path, [ref]$null, [ref]$errs)
            Check ($errs.Count -eq 0) "parse $Path"
            $ast.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $Name }, $true).Extent.Text }
        $phaseText = & $function $phasePath 'Invoke-P4LayerPhase'
        Check ($phaseText.Contains('Request-P4LayerPhaseReservation -DraftPath $draftPath')) 'default ReserveManifest'
        $request = & $function $phasePath 'Request-P4LayerPhaseReservation'
        Check ($request.Contains('New-P4ExperimentReservation -ManifestPath $pinned')) 'reservation through preflight'
        $preflightPath = Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1'
        $reservation = & $function $preflightPath 'New-P4ExperimentReservation'
        Check ($reservation.Contains('Assert-P4ManifestArtifacts $Manifest $RepositoryRoot -Stage reservation')) 'preflight reservation stage'
        $layer = & $function $preflightPath 'Assert-P4LayerManifestArtifacts'
        Check ($layer.Contains('gh issue view $script:P4LayerAgreementIssue') -and $layer.Contains('Assert-P4LiveDeviceAdmission $Manifest $RepositoryRoot $roleKinds $observed') -and
            -not $layer.Contains('is not admitted')) 'phase reservation stage open'
        Check ($script:P4LayerAgreementIssue -eq 145 -and $script:P4AgreementIssue -eq 142) 'agreement Issues (phase 145, v7 142)'
    }
    $result.status = 'success'
} finally {
    $result.cases = $script:cases.ToArray()
    Save-Json (Join-Path $OutputDirectory 'result.json') $result
    $passed = @($script:cases | Where-Object { $_.passed }).Count
    $groups = @($script:cases | Group-Object { $_.group } | ForEach-Object {
        "$($_.Name) $(@($_.Group | Where-Object { $_.passed }).Count)/$($_.Count)" }) -join ', '
    Write-Output "status=$($result.status) passed=$passed/$($script:cases.Count) groups: $groups"
}

# Issue #145 R3 / R7 points 3 and 6: one layer-phase run. Preservation once (ADR 0035 (a)(b)), the 18
# catalog slots through the existing outer slot wrapper (each slot's stopped cleanup runs inside it,
# before the seal), and the original restoration in a finally path (ADR 0035 (c)). This file is a
# library: the operator dot-sources it and calls Invoke-P4LayerPhase. It adds no collection entry
# point and does not relax any live admission (preflight admission and measure-m2-frame's refusal stay).
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# The slot wrapper is dot-sourced without running its entry point (InvocationName '.'). It brings the
# preflight catalog, the lane planner, the stopped cleanup and the ADR0035 snapshot/session/restore helpers.
. (Join-Path $PSScriptRoot 'invoke-p4-indexed-slot.ps1') -ManifestPath phase -SlotId phase

$script:P4LayerPhaseProtocolId = 'nene-pixel-p4-layer-phase-verification-v1'
$script:P4LayerPhaseRecordSchema = 'nene-pixel-p4-layer-phase-run-v1'
# The phase-start snapshot is the only archive (ADR 0035 (a)); its caps are the ones the dropped
# per-slot archive carried (commit e8962b6: 268,435,456 B archive, 134,217,728 B APK).
$script:P4LayerPhaseMaximumArchiveBytes = 268435456
$script:P4LayerPhaseMaximumApkBytes = 134217728
# The phase clock. Validators replace it; production reads the wall clock.
$script:P4LayerPhaseClock = { [datetime]::UtcNow }

# --- Hang bounds (not measured durations) -------------------------------------------------------
# The snapshot, isolation and restoration helpers own no clock constant. Their bound is derived the
# way the stopped cleanup's is (T4r2): calls x the measured worst call x margin, rounded up to whole
# minutes. The 30 s per-call timeout is not summed (that bound is over ten times reality).
#   call    = P4LayerCleanupMeasuredCallMilliseconds (276 ms: the 10-03 maximum of 37 native calls,
#             evidence 145-native-device-readonly/20261003T155545623, elapsed_milliseconds)
#   margin  = P4LayerCleanupMargin (2; also covers host-side work between calls)
# One private inventory is I calls (8 fixed + stat and hash batches); I is the cleanup's assumed
# inventory shape (P4LayerCleanupInventoryCallCount, entries <= 256, files <= 128).
# One mkdir/move step is a pre inventory, the run-as call and a post inventory: 2I + 1 calls.
# The snapshot's two transfers are not call-shaped; each counts at its measured 10-03 duration x margin
# (evidence 145-native-snapshot-device/20261003T160746439: original-original-tar.json, 10,335,232 B in
# 3,204 ms; original-original-apk.json, 12,402,152 B in 4,738 ms). A larger archive takes longer; the
# bound covers the measured size only (assumed, stated).
# The restoration APK install keeps its fixed 120 s (Invoke-P4RestorationApkInstall).
$script:P4LayerPhaseSnapshotTarMilliseconds = 3204
$script:P4LayerPhaseSnapshotApkMilliseconds = 4738
$script:P4LayerPhaseRestorationInstallSeconds = 120
# Worst step shapes (assumed, stated): isolation creates at most no_backup, p4-user-preservation,
# <session> and original (4 mkdir) and moves at most the six Get-P4MoveRoots roots (6 moves).
# Restoration creates the measurement archive and its no_backup/ and files/ parents (3 mkdir), moves
# at most seven measurement roots (the six fixed roots and no_backup/p4-layer-slots) and six originals.
$script:P4LayerPhaseIsolationSteps = 4 + 6
$script:P4LayerPhaseRestorationSteps = 3 + 7 + 6

function Get-P4LayerPhasePreservationBounds {
    $inventory = [int]$script:P4LayerCleanupInventoryCallCount
    $step = 2 * $inventory + 1
    # Snapshot: inventory before, APK path+hash, inventory after, APK path+hash (+ APK copy and tar transfers).
    $snapshotCalls = 2 * $inventory + 4
    # Isolation: APK path+hash, original inventory, steps, final inventory, APK path+hash.
    $isolationCalls = 4 + 2 * $inventory + $script:P4LayerPhaseIsolationSteps * $step
    # Restoration: 4 stopped probes, 3 x APK path+hash, pre and final inventories, steps (+ the install).
    $restorationCalls = 10 + 2 * $inventory + $script:P4LayerPhaseRestorationSteps * $step
    $call = [long]$script:P4LayerCleanupMeasuredCallMilliseconds
    $margin = [long]$script:P4LayerCleanupMargin
    $transfers = [long]$script:P4LayerPhaseSnapshotTarMilliseconds + $script:P4LayerPhaseSnapshotApkMilliseconds
    $minutes = { param([double]$Milliseconds) [int]([Math]::Ceiling($Milliseconds / 60000.0) * 60) }
    $snapshot = & $minutes (($snapshotCalls * $call + $transfers) * $margin)
    $isolation = & $minutes ($isolationCalls * $call * $margin)
    $restoration = & $minutes ($restorationCalls * $call * $margin + $script:P4LayerPhaseRestorationInstallSeconds * 1000)
    return [ordered]@{ inventory_calls = $inventory; measured_call_milliseconds = $call; margin = $margin
        snapshot_calls = $snapshotCalls; snapshot_transfer_milliseconds = $transfers; snapshot_seconds = $snapshot
        isolation_calls = $isolationCalls; isolation_seconds = $isolation
        preservation_seconds = $snapshot + $isolation
        restoration_calls = $restorationCalls; restoration_install_seconds = $script:P4LayerPhaseRestorationInstallSeconds
        restoration_seconds = $restoration
        derivation = 'calls x measured worst call (276 ms, 10-03) x margin 2, snapshot transfers at measured duration x 2, + restoration install 120 s, each rounded up to 60 s; call counts are assumed step shapes stated in invoke-p4-layer-phase.ps1' }
}

function Get-P4LayerPhaseBounds {
    # Slot bound = the Invoke-P4IndexedSlot deadline: collector budget (planner) + stopped-cleanup clock
    # (Get-P4LayerSlotCleanupTimeout) + the existing seal reserve + the analysis budget.
    param([Collections.IDictionary]$Manifest, [string]$ManifestSha256, [object[]]$Catalog)
    $slots = [Collections.Generic.List[object]]::new()
    $sum = 0
    foreach ($slot in $Catalog) {
        $collector = [int](Get-P4SlotCollectorBudget -Manifest $Manifest -Slot $slot -ManifestSha256 $ManifestSha256).budget.collector_timeout_seconds
        $cleanup = [int](Get-P4LayerSlotCleanupPlan $Manifest $slot $ManifestSha256).timeout_seconds
        $total = $collector + $cleanup + $script:P4CleanupReserveSeconds + $script:P4AnalysisTimeoutSeconds
        $slots.Add([ordered]@{ slot_id = [string]$slot.id; collector_seconds = $collector; cleanup_seconds = $cleanup
            seal_reserve_seconds = $script:P4CleanupReserveSeconds; analysis_seconds = $script:P4AnalysisTimeoutSeconds
            slot_seconds = $total })
        $sum += $total
    }
    $preservation = Get-P4LayerPhasePreservationBounds
    return [ordered]@{ kind = 'hang-bound'
        note = 'Hang bounds, not measured or estimated durations.'
        preservation = $preservation; slots = $slots.ToArray(); slots_seconds = $sum
        phase_seconds = $preservation.preservation_seconds + $sum + $preservation.restoration_seconds }
}

# --- Records ------------------------------------------------------------------------------------
function Write-P4LayerPhaseRecord([string]$Path, $Record) {
    # CreateNew: an existing phase record is never overwritten.
    Write-NewInvocationFile $Path ($Record | ConvertTo-Json -Depth 30)
}

function Get-P4LayerPhaseCleanupSeconds([string]$SlotDirectory) {
    $path = Join-Path $SlotDirectory 'phase-cleanup/result.json'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return $null }
    $cleanup = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json -AsHashtable
    if ([string]::IsNullOrEmpty([string]$cleanup.started_utc) -or [string]::IsNullOrEmpty([string]$cleanup.ended_utc)) { return $null }
    $started = [datetime]::Parse([string]$cleanup.started_utc, $null, [Globalization.DateTimeStyles]::RoundtripKind)
    $ended = [datetime]::Parse([string]$cleanup.ended_utc, $null, [Globalization.DateTimeStyles]::RoundtripKind)
    return [Math]::Round(($ended - $started).TotalSeconds, 3)
}

function Get-P4LayerPhaseExpectedApk {
    # The APK restoration expects is decided from recorded install intent/result, never from the hash
    # the device shows. The last started slot's stopped cleanup installs (or verifies) its artifact
    # role's app_debug; with no started slot, nothing was installed and the snapshot's original stays.
    param([Collections.IDictionary]$Manifest, [string]$Root, [object[]]$Started, $Snapshot)
    if (@($Started).Count -eq 0) {
        return [ordered]@{ sha256 = [string]$Snapshot.apk_sha256; source = 'original-snapshot'; slot_id = $null }
    }
    $last = @($Started)[-1]
    $access = Join-Path (Join-Path $Root $last.slot_id) 'phase-cleanup/debug-access'
    $intentPath = Join-Path $access 'install-app_debug-intent.json'
    $resultPath = Join-Path $access 'install-app_debug.json'
    foreach ($path in @($intentPath, $resultPath)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Expected APK undetermined: the last started slot $($last.slot_id) has no cleanup install record."
        }
    }
    $intent = Get-Content -LiteralPath $intentPath -Raw | ConvertFrom-Json -AsHashtable
    $result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json -AsHashtable
    $expected = [string]$Manifest.roles[$last.artifact_role].artifacts.app_debug.sha256
    if ($expected -cnotmatch '^[0-9a-f]{64}$' -or $intent.role -cne $last.artifact_role -or $intent.kind -cne 'app_debug' -or
        $intent.expected_sha256 -cne $expected -or $result.status -cne 'success' -or $result.role -cne $last.artifact_role -or
        $result.expected_sha256 -cne $expected -or $result.observed_sha256 -cne $expected -or
        $result.intent_sha256 -cne (Get-FileSha256 $intentPath)) {
        throw "Expected APK undetermined: the install intent/result of $($last.slot_id) does not bind $($last.artifact_role).app_debug."
    }
    return [ordered]@{ sha256 = $expected; source = 'slot-cleanup-install'; slot_id = $last.slot_id
        intent_sha256 = Get-FileSha256 $intentPath; result_sha256 = Get-FileSha256 $resultPath }
}

function Assert-P4LayerPhaseManifest {
    param([string]$Path, $Isolation, [string]$Session, [string]$ExperimentId, [string]$Serial)
    if ([IO.Path]::GetFileName($Path) -cne 'preflight.json') { throw 'The phase runs on the reserved preflight.json only.' }
    $bytes = [IO.File]::ReadAllBytes($Path)
    $manifest = [Text.UTF8Encoding]::new($false, $true).GetString($bytes) | ConvertFrom-Json -AsHashtable
    $preservation = $manifest.device.asset_preservation
    if ($manifest.protocol.id -cne $script:P4LayerPhaseProtocolId) { throw 'The reserved manifest is not the layer phase.' }
    if (-not [string]::Equals([IO.Path]::GetFullPath((Join-Path $manifest.output_directory 'preflight.json')),
            [IO.Path]::GetFullPath($Path), [StringComparison]::OrdinalIgnoreCase)) { throw 'The manifest is not its reserved copy.' }
    if ($manifest.experiment_id -cne $ExperimentId -or $manifest.device.serial -cne $Serial -or
        $preservation.session -cne $Session -or $preservation.sha256 -cne (Get-FileSha256 $Isolation.ResultPath) -or
        -not [string]::Equals([IO.Path]::GetFullPath($preservation.path), [IO.Path]::GetFullPath($Isolation.ResultPath),
            [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The reserved manifest does not pin this phase''s preservation-v2 record.'
    }
    return [ordered]@{ manifest = $manifest; sha256 = Get-Sha256Hex $bytes }
}

# --- The phase ----------------------------------------------------------------------------------
function Invoke-P4LayerPhase {
    <#
        Context: repository_root, adb_path, serial, package (the original app) and output_directory (an
        existing phase directory under lab evidence). ReserveManifest receives the verified
        preservation-v2 record {path, sha256, session, experiment_id} and returns the reserved
        preflight.json that pins it (the manifest binds the preservation, so reservation follows isolation).
        Slot failures are recorded and the next slot runs; preservation or restoration failure throws
        after the record is written. Nothing is deleted.
    #>
    param([Parameter(Mandatory)][Collections.IDictionary]$Context, [Parameter(Mandatory)][string]$Session,
        [Parameter(Mandatory)][string]$ExperimentId, [Parameter(Mandatory)][scriptblock]$ReserveManifest)
    Assert-P4Session $Session
    Assert-P4Session $ExperimentId
    foreach ($key in @('repository_root', 'output_directory', 'adb_path', 'serial', 'package')) {
        if (-not $Context.Contains($key) -or [string]::IsNullOrWhiteSpace([string]$Context[$key])) { throw "Missing phase context: $key" }
    }
    $phaseDirectory = [IO.Path]::GetFullPath($Context.output_directory)
    $recordPath = Join-Path $phaseDirectory "p4-layer-phase-$Session-$ExperimentId.json"
    $preservationDirectory = Join-Path $phaseDirectory "preservation-$Session-$ExperimentId"
    if ((Test-Path -LiteralPath $recordPath) -or (Test-Path -LiteralPath $preservationDirectory)) {
        throw 'The phase record or its preservation directory already exists; a phase record is never overwritten.'
    }
    [void][IO.Directory]::CreateDirectory($preservationDirectory)
    $native = [ordered]@{}
    foreach ($key in $Context.Keys) { $native[$key] = $Context[$key] }
    $native.output_directory = $preservationDirectory
    $startedUtc = & $script:P4LayerPhaseClock
    $record = [ordered]@{ schema = $script:P4LayerPhaseRecordSchema; protocol_id = $script:P4LayerPhaseProtocolId
        status = 'failure'; session = $Session; experiment_id = $ExperimentId; serial = [string]$Context.serial
        package = [string]$Context.package; started_utc = $startedUtc.ToString('o'); ended_utc = $null
        manifest_path = $null; manifest_sha256 = $null
        snapshot = $null; preservation = $null; slots = @(); chain = $null; restoration = $null
        deadline = $null; error = $null }
    $snapshot = $null; $isolation = $null; $manifest = $null; $root = $null
    $started = [Collections.Generic.List[object]]::new()
    $failure = $null
    try {
        $snapshotStage = "p4-snapshot-$Session-$ExperimentId"
        $snapshot = New-P4PrivateSnapshot -Context $native -Stage $snapshotStage `
            -MaximumArchiveBytes $script:P4LayerPhaseMaximumArchiveBytes -MaximumApkBytes $script:P4LayerPhaseMaximumApkBytes
        $snapshotPath = Join-Path $preservationDirectory "$snapshotStage-snapshot.json"
        $record.snapshot = [ordered]@{ path = $snapshotPath; sha256 = Get-FileSha256 $snapshotPath
            original_apk_sha256 = [string]$snapshot.apk_sha256 }
        $isolation = Invoke-P4PrivateIsolation -Context $native -Session $Session -ExperimentId $ExperimentId -SnapshotPath $snapshotPath
        $record.preservation = [ordered]@{ path = [IO.Path]::GetFullPath($isolation.ResultPath)
            sha256 = Get-FileSha256 $isolation.ResultPath; session = $Session; experiment_id = $ExperimentId }
        $manifestPath = & $ReserveManifest ([pscustomobject]$record.preservation)
        $admitted = Assert-P4LayerPhaseManifest -Path ([string]$manifestPath) -Isolation $isolation -Session $Session `
            -ExperimentId $ExperimentId -Serial ([string]$Context.serial)
        $manifest = $admitted.manifest
        $record.manifest_path = [IO.Path]::GetFullPath([string]$manifestPath)
        $record.manifest_sha256 = $admitted.sha256
        $root = [IO.Path]::GetFullPath($manifest.output_directory)
        $catalog = @(Get-P4SlotCatalog -ProtocolId $script:P4LayerPhaseProtocolId)
        $bounds = Get-P4LayerPhaseBounds -Manifest $manifest -ManifestSha256 $admitted.sha256 -Catalog $catalog
        # The phase deadline counts from the phase start, so the preservation time already spent is inside it.
        $deadlineUtc = $startedUtc.AddSeconds($bounds.phase_seconds)
        $bounds.started_utc = $startedUtc.ToString('o')
        $bounds.deadline_utc = $deadlineUtc.ToString('o')
        $bounds.slot_start_rule = 'A slot starts only if now + its slot bound <= phase deadline - restoration bound; a running slot is never stopped, and cleanup and restoration always run.'
        $record.deadline = $bounds
        $analyses = @{}
        $slotRecords = [Collections.Generic.List[object]]::new()
        $record.slots = $slotRecords
        $deadlineHit = $false
        foreach ($slot in $catalog) {
            $id = [string]$slot.id
            $bound = @($bounds.slots | Where-Object { $_.slot_id -ceq $id })[0]
            $entry = [ordered]@{ slot_id = $id; lane = [string]$slot.lane
                artifact_role = [string](Get-P4SlotArtifactRole $manifest $slot); status = $null; reason = $null
                verdict = $null; bound_seconds = $bound.slot_seconds; elapsed_seconds = $null; cleanup_seconds = $null }
            $slotRecords.Add($entry)
            if ($slot.lane -ceq 'frame') {
                $chain = Get-P4PhaseFrameChainRecord -Catalog $catalog -Analyses $analyses
                $position = @($chain.slots | Where-Object { $_.slot_id -ceq $id })[0]
                if ($position.chain -ceq 'stopped') {
                    $entry.status = 'skipped'
                    $entry.reason = "gross-regression stop by $($position.stopped_by.slot_id)"
                    continue
                }
            }
            $now = & $script:P4LayerPhaseClock
            if ($deadlineHit -or $now.AddSeconds($bound.slot_seconds) -gt $deadlineUtc.AddSeconds(-$bounds.preservation.restoration_seconds)) {
                $deadlineHit = $true
                $entry.status = 'not-started'
                $entry.reason = 'phase-deadline: the slot bound no longer fits before the restoration bound'
                continue
            }
            $directory = Join-Path $root $id
            $timer = [Diagnostics.Stopwatch]::StartNew()
            try {
                $analysis = Invoke-P4IndexedSlot -ManifestPath $record.manifest_path -SlotId $id
                $entry.status = 'completed'
                $entry.verdict = [string]$analysis.verdict
                $analyses[$id] = $analysis
            } catch {
                $entry.reason = $_.Exception.Message
                $entry.status = if (Test-Path -LiteralPath (Join-Path $directory 'invalid.json')) { 'invalid' }
                    elseif (-not (Test-Path -LiteralPath $directory)) { 'refused' } else { 'failed' }
            } finally {
                $timer.Stop()
                $entry.elapsed_seconds = [Math]::Round($timer.Elapsed.TotalSeconds, 3)
                if (Test-Path -LiteralPath (Join-Path $directory 'started.json')) {
                    $started.Add([ordered]@{ slot_id = $id; artifact_role = $entry.artifact_role })
                }
                $entry.cleanup_seconds = Get-P4LayerPhaseCleanupSeconds $directory
            }
        }
        $record.chain = Get-P4PhaseFrameChainRecord -Catalog $catalog -Analyses $analyses
    } catch {
        $failure = $_
        $record.error = $_.Exception.Message
    } finally {
        # ADR 0035 (c): restoration on success and on failure, once preservation-v2 exists.
        $restoration = [ordered]@{ status = 'not-attempted'; reason = $null; expected_apk = $null
            result_path = $null; result_sha256 = $null }
        if ($null -eq $isolation) {
            $restoration.reason = 'No verified preservation-v2 record; nothing was isolated by this phase, or a failed isolation is kept for review.'
        } else {
            try {
                $expected = Get-P4LayerPhaseExpectedApk -Manifest $manifest -Root $root -Started $started.ToArray() -Snapshot $snapshot
                $restoration.expected_apk = $expected
                $restored = Invoke-P4PrivateRestoration -Context $native -PreservationPath $isolation.ResultPath `
                    -ExpectedInstalledApkHash $expected.sha256
                $restoration.result_path = [IO.Path]::GetFullPath($restored.ResultPath)
                $restoration.result_sha256 = Get-FileSha256 $restored.ResultPath
                $restoration.status = [string]$restored.Record.status
            } catch {
                $restoration.status = 'failure'
                $restoration.reason = $_.Exception.Message
            }
        }
        $record.restoration = $restoration
        $slotFailures = @($record.slots | Where-Object { $_.status -cnotin @('completed', 'skipped') }).Count
        $record.status = if ($null -ne $failure -or $restoration.status -cne 'restored') { 'failure' }
            elseif ($slotFailures -gt 0) { 'restored-with-slot-failures' } else { 'complete' }
        $record.ended_utc = (& $script:P4LayerPhaseClock).ToString('o')
        Write-P4LayerPhaseRecord $recordPath $record
    }
    if ($null -ne $failure) { throw $failure }
    if ($record.restoration.status -cne 'restored') { throw "Phase restoration failed: $($record.restoration.reason)" }
    return [pscustomobject]@{ RecordPath = $recordPath; Record = $record }
}

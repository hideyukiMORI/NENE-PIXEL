# The existing outer slot wrapper owns this composition. No collection entry point is added.
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'p4-indexed-preflight.ps1')
. (Join-Path $PSScriptRoot 'p4-layer-slot-routing.ps1')
. (Join-Path $PSScriptRoot 'p4-device-private-slot-reset.ps1')
$script:P4LayerCleanupHelperPath = $PSCommandPath

# The native termination/drain reserve that ConvertTo-P4OperationTimeout keeps back from the clock.
$script:P4LayerCleanupNativeReserveSeconds = 15
# The cleanup clock is a hang bound, not the sum of every native call's own 30 s limit (that sum is
# 16 + 24 x I calls x 30 s >= 7680 s and cannot fit the 3600 s budget).
# Measured source: the 2026-10-03 device records 145-native-device-readonly/20261003T155545623 and
# 145-native-snapshot-device/20261003T160746439 (one tablet, 149 entries, 90 files, three
# inventories plus four APK identity calls). Their elapsed_milliseconds give a per-call median of
# 0.180 s and a maximum of 0.276 s (37 calls); one inventory took 1.8-2.0 s of call time (2.7 s wall).
# 149 entries and 90 files make I = 8 + ceil(149/128) + ceil(90/128) = 11 calls per inventory.
$script:P4LayerCleanupMeasuredCallMilliseconds = 276
$script:P4LayerCleanupInventoryCallCount = 11
# Assumed, not measured: later slot data keeps I = 11 (entries <= 256, files <= 128), and the
# reset runs its worst shape of 16 + 24 x I native calls (five mkdir and six move steps).
$script:P4LayerCleanupResetCallCount = 16 + 24 * $script:P4LayerCleanupInventoryCallCount
$script:P4LayerCleanupMargin = 2
# One shared cleanup clock, rounded up to whole minutes:
#   reset calls x measured call x P4LayerCleanupMargin      (280 x 0.276 s x 2 = 154.56 s)
# + the parts with no measurement, kept at their T4 upper bounds:
#   3 x W x P4ProbeTimeoutSeconds   writer force-stop, absence, still-stopped (W = writer packages)
#   P4InstallTimeoutSeconds         role debug APK install
#   4 x P4ProbeTimeoutSeconds       installed APK identity probes
#   sum(private_files.timeout_seconds) report recovery (30 s each)
#   P4LayerCleanupNativeReserveSeconds
# W is the device plan's quiescence_packages count, i.e. the packages the slot's role installs:
# app always, test package when the role declares test_debug, publication package on the
# publication slot only. Clock = ceil((154.56 + 90 x W + 255 + 30 x N) / 60) x 60:
#   slot kind                         W  N  seconds     slot kind                  W  N  seconds
#   frame single, baseline_single     1  0  540         memory (10)                2  0  600
#   frame single, candidate           2  0  600         publication (candidate)    3  3  780
#   frame staged layers16/underlay    2  1  660         saf-save (candidate)       2  4  720
# 18 slots (single 2, staged 4, memory 10, publication 1, saf-save 1) total 11280 s.
function Get-P4LayerSlotCleanupTimeout([Collections.IDictionary] $DevicePlan) {
    $writers = @($DevicePlan.quiescence_packages).Count
    $reports = 0
    foreach ($file in @($DevicePlan.private_files)) { $reports += [int]$file.timeout_seconds }
    $fixedSeconds = 3 * $writers * $script:P4ProbeTimeoutSeconds + $script:P4InstallTimeoutSeconds +
        4 * $script:P4ProbeTimeoutSeconds + $reports + $script:P4LayerCleanupNativeReserveSeconds
    $resetMilliseconds = [long]$script:P4LayerCleanupResetCallCount * $script:P4LayerCleanupMeasuredCallMilliseconds *
        $script:P4LayerCleanupMargin
    $totalMilliseconds = [long]$fixedSeconds * 1000 + $resetMilliseconds
    return [int]([Math]::Ceiling($totalMilliseconds / 60000.0) * 60)
}

function Get-P4LayerSlotCleanupPlan {
    param([Collections.IDictionary] $Manifest, [Collections.IDictionary] $Slot, [string] $ManifestSha256)
    $plan = Get-P4LayerDeviceLanePlan $Manifest $Slot $ManifestSha256
    $packages = Get-P4LanePackages $Manifest $plan.role $Manifest.protocol.id
    return [ordered]@{ schema = 'nene-pixel-p4-layer-slot-cleanup-plan-v2'; slot_id = $Slot.id;
        artifact_role = $plan.role; comparison_role = $Slot.role; device_plan = $plan;
        debug_package = $packages.application; timeout_seconds = Get-P4LayerSlotCleanupTimeout $plan }
}

function New-P4LayerCleanupContext([Collections.IDictionary] $Context, [string] $Directory, [string] $Package) {
    if (Test-Path -LiteralPath $Directory) { throw 'Phase cleanup component directory already exists' }
    Assert-P4SealPathNotLinked $Directory
    [void][IO.Directory]::CreateDirectory($Directory)
    $child = [ordered]@{}
    foreach ($key in $Context.Keys) { $child[$key] = $Context[$key] }
    $child.output_directory = [IO.Path]::GetFullPath($Directory)
    $child.package = $Package
    return $child
}

function Get-P4LayerCleanupFileRecord([string] $Path) {
    Assert-P4SealPathNotLinked $Path
    return [ordered]@{ path = [IO.Path]::GetFullPath($Path); sha256 = Get-P4SnapshotHash $Path }
}

function Complete-P4LayerPartialFrameRecord {
    param($Manifest, $Slot, [string] $ManifestSha256, $Context)
    if ($Slot.lane -cne 'frame') { return $null }
    $contract = Get-P4FrameExecutionContract $Manifest.protocol.id $Slot.id
    $directory = [IO.Path]::GetFullPath((Join-Path $Manifest.frame_experiment.directory $contract.frame_directory_name))
    $path = Join-Path $Context.output_directory 'frame-slot.json'
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
        if (Test-Path -LiteralPath $path) { throw 'Frame record exists without its canonical directory' }
        return $null
    }
    Assert-P4SealPathNotLinked $directory
    $frameContext = Get-P4LayerFramePhaseContext $Manifest $Slot $ManifestSha256
    if (-not (Test-Path -LiteralPath $path)) {
        New-P4FrameSlotRecord -Context $Context -FrameSlotDirectory $directory -PhaseContext $frameContext | Out-Null
    } else {
        Assert-P4SealPathNotLinked $path
        $record = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json -AsHashtable
        if ($record.schema -cne 'nene-pixel-p4-frame-slot-v2' -or $record.slot_id -cne $Slot.id -or
            $record.artifact_role -cne $contract.artifact_role -or
            -not [string]::Equals([IO.Path]::GetFullPath($record.slot_directory), $directory, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Existing frame record differs from the canonical phase slot'
        }
        Assert-P4FrameExactProjection $record.phase_context $frameContext 'phase cleanup frame context'
    }
    return Get-P4LayerCleanupFileRecord $path
}

function Invoke-P4LayerSlotCleanup {
    param([Collections.IDictionary] $Context, [Collections.IDictionary] $Manifest,
        [Collections.IDictionary] $Slot, [string] $ManifestSha256, [bool] $CollectorSucceeded)
    $cleanupPlan = Get-P4LayerSlotCleanupPlan $Manifest $Slot $ManifestSha256
    $plan = $cleanupPlan.device_plan; $role = $cleanupPlan.artifact_role
    $expectedRoot = [IO.Path]::GetFullPath((Join-Path $Manifest.output_directory $Slot.id))
    foreach ($pair in @(@('output_directory', $expectedRoot), @('repository_root', $Manifest.roles[$role].worktree),
            @('adb_path', $Manifest.tools.adb.path))) {
        if (-not [string]::Equals([IO.Path]::GetFullPath($Context[$pair[0]]), [IO.Path]::GetFullPath($pair[1]),
                [StringComparison]::OrdinalIgnoreCase)) { throw "Phase cleanup context differs: $($pair[0])" }
    }
    if ($Context.serial -cne $Manifest.device.serial -or $Context.Contains('operation_budget')) {
        throw 'Phase cleanup must own a fresh clock for the pinned device'
    }
    Assert-P4SealPathNotLinked $expectedRoot
    $package = $cleanupPlan.debug_package
    $child = New-P4LayerCleanupContext $Context (Join-Path $expectedRoot 'phase-cleanup') $package
    $child.operation_budget = New-P4OperationBudget $cleanupPlan.timeout_seconds
    Assert-P4NativeContext $child 'phase-cleanup'
    $captureContext = [ordered]@{}
    foreach ($key in $child.Keys) { $captureContext[$key] = $child[$key] }
    $captureContext.output_directory = $expectedRoot
    $recordPath = Join-Path $child.output_directory 'result.json'
    $errors = [Collections.Generic.List[string]]::new()
    $record = [ordered]@{ schema = 'nene-pixel-p4-layer-slot-cleanup-v2'; status = 'failure';
        protocol_id = $Manifest.protocol.id; slot_id = $Slot.id; artifact_role = $role;
        preflight_sha256 = $ManifestSha256; preservation_sha256 = $Manifest.device.asset_preservation.sha256;
        session = $Manifest.device.asset_preservation.session; experiment_id = $Manifest.experiment_id;
        collector_succeeded = $CollectorSucceeded; plan = $cleanupPlan; packages_stopped = $false;
        debug_access = $null; report_capture = $null; reset = $null; frame_record = $null;
        source_sha256 = [ordered]@{ snapshot = Get-P4SnapshotSources;
            reset_helper = Get-P4SnapshotHash $script:P4SlotResetHelperPath;
            session_helper = Get-P4SnapshotHash $script:P4SessionHelperPath;
            restoration_helper = Get-P4SnapshotHash $script:P4RestorationHelperPath;
            routing_helper = Get-P4SnapshotHash (Join-Path $PSScriptRoot 'p4-layer-slot-routing.ps1');
            cleanup_helper = Get-P4SnapshotHash $script:P4LayerCleanupHelperPath };
        started_utc = [datetime]::UtcNow.ToString('o'); ended_utc = $null; errors = @() }
    try {
        $index = 0
        foreach ($writer in $plan.quiescence_packages) {
            $index++
            try {
                $stop = Invoke-P4BoundedAdb $child "stop-$index.log" @('shell', 'am', 'force-stop', $writer) $script:P4ProbeTimeoutSeconds
                if ($stop.ExitCode -ne 0) { throw "Writer stop failed: $writer" }
                $absent = Invoke-P4BoundedAdb $child "absent-$index.log" @('shell', 'pidof', $writer) $script:P4ProbeTimeoutSeconds
                if ($absent.ExitCode -ne 1 -or -not [string]::IsNullOrWhiteSpace((@($absent.OutputLines) -join ''))) {
                    throw "Writer absence unconfirmed: $writer"
                }
            } catch { $errors.Add($_.Exception.Message) }
        }
        $record.packages_stopped = $errors.Count -eq 0
        try {
            $record.frame_record = Complete-P4LayerPartialFrameRecord $Manifest $Slot $ManifestSha256 $captureContext
            if ($Slot.lane -ceq 'frame' -and $CollectorSucceeded -and $null -eq $record.frame_record) {
                throw 'Successful frame collector has no canonical capture directory'
            }
        }
        catch { $errors.Add($_.Exception.Message) }
        if ($record.packages_stopped) {
            $access = $false
            try {
                $preservation = $Manifest.device.asset_preservation
                Assert-P4SealPathNotLinked $preservation.path
                if ((Get-P4SnapshotHash $preservation.path) -cne $preservation.sha256) { throw 'Cleanup preservation hash differs' }
                $proofContext = @{}
                foreach ($key in $child.Keys) { $proofContext[$key] = $child[$key] }
                $proofContext.output_directory = [IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($preservation.path))
                $verified = Read-P4VerifiedPreservation $preservation.path $proofContext
                if ($verified.Hash -cne $preservation.sha256 -or $verified.Record.session -cne $preservation.session -or
                    $verified.Record.experiment_id -cne $Manifest.experiment_id) { throw 'Cleanup preservation session/experiment differs' }
                $installContext = New-P4LayerCleanupContext $child (Join-Path $child.output_directory 'debug-access') $package
                $install = Assert-P4InstalledApk $installContext $Manifest $role 'app_debug'
                if ($install.status -cne 'success') { throw 'Cleanup debug access did not complete' }
                $record.debug_access = Get-P4LayerCleanupFileRecord (Join-Path $installContext.output_directory 'install-app_debug.json')
                Assert-P4RemotePackagesStopped $child $plan.quiescence_packages 'after-debug-access'
                $access = $true
            } catch { $errors.Add($_.Exception.Message) }
            if ($access) {
                try {
                    if ($Slot.lane -ceq 'frame' -and $CollectorSucceeded) {
                        $frameContext = Get-P4LayerFramePhaseContext $Manifest $Slot $ManifestSha256
                        $staging = Read-P4LayerFrameStagingEvidence $Manifest $Slot $ManifestSha256 $expectedRoot $frameContext
                        $record.report_capture = [ordered]@{ mode = 'captured-frame-fixture'; evidence = Get-P4LayerCleanupFileRecord $staging }
                    } elseif ($Slot.lane -ceq 'frame' -and @($plan.private_files).Count -eq 0) {
                        $record.report_capture = [ordered]@{ mode = 'not-required'; evidence = $null }
                    } else {
                        $reportContext = if ($Slot.lane -ceq 'frame') {
                            New-P4LayerCleanupContext $child (Join-Path $child.output_directory 'frame-fixture-recovery') $package
                        } else { $captureContext }
                        Copy-P4LayerPrivateReports $reportContext $plan | Out-Null
                        $record.report_capture = [ordered]@{ mode = $(if ($Slot.lane -ceq 'frame') { 'diagnostic-fixture-recovery' } else { 'stopped-reports' });
                            evidence = Get-P4LayerCleanupFileRecord (Join-Path $reportContext.output_directory 'private-report-capture.json') }
                    }
                } catch { $errors.Add($_.Exception.Message) }
                # The reset starts only after every stop, debug access and report recovery succeeded.
                if ($errors.Count -eq 0) {
                    try {
                        $resetContext = New-P4LayerCleanupContext $child (Join-Path $child.output_directory 'reset') $package
                        $reset = Invoke-P4PrivateSlotReset $resetContext $preservation.path $preservation.sha256 `
                            $ManifestSha256 $Slot.id $Manifest.roles[$role].artifacts.app_debug.sha256
                        if ($reset.Record.status -cne 'reset') { throw 'Cleanup reset did not complete' }
                        $record.reset = Get-P4LayerCleanupFileRecord $reset.ResultPath
                    } catch { $errors.Add($_.Exception.Message) }
                }
            }
        }
        if ($errors.Count -eq 0) { Assert-P4OperationActive $child; $record.status = 'stopped-and-reset' }
    } catch { $errors.Add($_.Exception.Message) }
    finally {
        $record.errors = $errors.ToArray()
        $record.ended_utc = [datetime]::UtcNow.ToString('o')
        Write-P4SessionJson $recordPath $record
    }
    if ($errors.Count -gt 0) { throw "Phase cleanup failed: $($errors -join '; ')" }
    return [pscustomobject]@{ Record = $record; ResultPath = $recordPath; Sha256 = Get-P4SnapshotHash $recordPath }
}

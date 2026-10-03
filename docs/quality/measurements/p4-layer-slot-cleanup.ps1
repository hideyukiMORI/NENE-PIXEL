# The existing outer slot wrapper owns this composition. No collection entry point is added.
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'p4-indexed-preflight.ps1')
. (Join-Path $PSScriptRoot 'p4-layer-slot-routing.ps1')
. (Join-Path $PSScriptRoot 'p4-device-private-slot-reset.ps1')
$script:P4LayerCleanupHelperPath = $PSCommandPath

function Get-P4LayerSlotCleanupPlan {
    param([Collections.IDictionary] $Manifest, [Collections.IDictionary] $Slot, [string] $ManifestSha256)
    $plan = Get-P4LayerDeviceLanePlan $Manifest $Slot $ManifestSha256
    $packages = Get-P4LanePackages $Manifest $plan.role $Manifest.protocol.id
    $archives = @([ordered]@{ name = 'app'; package = $packages.application; kind = 'app_debug' })
    if ($plan.install_kinds -ccontains 'test_debug') {
        $archives += [ordered]@{ name = 'test-provider'; package = $packages.application_test; kind = 'test_debug' }
    }
    if ($plan.install_kinds -ccontains 'publication_test') {
        $archives += [ordered]@{ name = 'publication'; package = $packages.publication_test; kind = 'publication_test' }
    }
    return [ordered]@{ schema = 'nene-pixel-p4-layer-slot-cleanup-plan-v1'; slot_id = $Slot.id;
        artifact_role = $plan.role; comparison_role = $Slot.role; device_plan = $plan;
        timeout_seconds = 3000; maximum_archive_bytes = 268435456L; maximum_apk_bytes = 134217728L;
        archives = $archives }
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
    $package = $cleanupPlan.archives[0].package
    $child = New-P4LayerCleanupContext $Context (Join-Path $expectedRoot 'phase-cleanup') $package
    $child.operation_budget = New-P4OperationBudget $cleanupPlan.timeout_seconds
    Assert-P4NativeContext $child 'phase-cleanup'
    $captureContext = [ordered]@{}
    foreach ($key in $child.Keys) { $captureContext[$key] = $child[$key] }
    $captureContext.output_directory = $expectedRoot
    $recordPath = Join-Path $child.output_directory 'result.json'
    $errors = [Collections.Generic.List[string]]::new()
    $archives = [Collections.Generic.List[object]]::new()
    $record = [ordered]@{ schema = 'nene-pixel-p4-layer-slot-cleanup-v1'; status = 'failure';
        protocol_id = $Manifest.protocol.id; slot_id = $Slot.id; artifact_role = $role;
        preflight_sha256 = $ManifestSha256; preservation_sha256 = $Manifest.device.asset_preservation.sha256;
        session = $Manifest.device.asset_preservation.session; experiment_id = $Manifest.experiment_id;
        collector_succeeded = $CollectorSucceeded; plan = $cleanupPlan; packages_stopped = $false;
        debug_access = $null; report_capture = $null; archives = @(); reset = $null; frame_record = $null;
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
                $stop = Invoke-P4BoundedAdb $child "stop-$index.log" @('shell', 'am', 'force-stop', $writer) 10
                if ($stop.ExitCode -ne 0) { throw "Writer stop failed: $writer" }
                $absent = Invoke-P4BoundedAdb $child "absent-$index.log" @('shell', 'pidof', $writer) 10
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
                # Archive every required source even if another component failed; never retry or replace.
                foreach ($archive in $cleanupPlan.archives) {
                    $component = [ordered]@{ name = $archive.name; package = $archive.package;
                        status = 'failure'; snapshot = $null; error = $null }
                    try {
                        $archiveContext = New-P4LayerCleanupContext $child (Join-Path $child.output_directory ("archive-" + $archive.name)) $archive.package
                        $snapshot = New-P4PrivateSnapshot -Context $archiveContext -Stage 'capture' `
                            -MaximumArchiveBytes $cleanupPlan.maximum_archive_bytes -MaximumApkBytes $cleanupPlan.maximum_apk_bytes
                        $component.snapshot = Get-P4LayerCleanupFileRecord (Join-Path $archiveContext.output_directory 'capture-snapshot.json')
                        if ($snapshot.status -cne 'verified-snapshot' -or $snapshot.package -cne $archive.package -or
                            $snapshot.serial -cne $Manifest.device.serial -or
                            $snapshot.apk_sha256 -cne $Manifest.roles[$role].artifacts[$archive.kind].sha256) {
                            throw "Cleanup snapshot identity differs: $($archive.name)"
                        }
                        $component.status = 'archived'
                    } catch { $component.error = $_.Exception.Message; $errors.Add($_.Exception.Message) }
                    $archives.Add($component)
                }
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
        if ($errors.Count -eq 0) { Assert-P4OperationActive $child; $record.status = 'captured-and-reset' }
    } catch { $errors.Add($_.Exception.Message) }
    finally {
        $record.archives = $archives.ToArray(); $record.errors = $errors.ToArray()
        $record.ended_utc = [datetime]::UtcNow.ToString('o')
        Write-P4SessionJson $recordPath $record
    }
    if ($errors.Count -gt 0) { throw "Phase cleanup failed: $($errors -join '; ')" }
    return [pscustomobject]@{ Record = $record; ResultPath = $recordPath; Sha256 = Get-P4SnapshotHash $recordPath }
}

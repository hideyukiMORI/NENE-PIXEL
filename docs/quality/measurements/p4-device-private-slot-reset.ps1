# Per-slot measurement reset consumes the canonical ADR0035 policy and native move boundary.
# This helper is not a collector or phase admission entry point.
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'p4-device-private-restore.ps1')
$script:P4SlotResetHelperPath = $PSCommandPath

function Invoke-P4PrivateSlotReset {
    param([Parameter(Mandatory)][Collections.IDictionary] $Context,
        [Parameter(Mandatory)][string] $PreservationPath,
        [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{64}$')][string] $PreservationSha256,
        [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{64}$')][string] $PreflightSha256,
        [Parameter(Mandatory)][string] $SlotId,
        [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{64}$')][string] $ExpectedInstalledApkHash)
    Assert-P4Session $SlotId
    foreach ($hash in @($PreservationSha256, $PreflightSha256, $ExpectedInstalledApkHash)) {
        if ($hash -cnotmatch '^[0-9a-f]{64}$') { throw 'Slot reset hashes must be lowercase SHA-256' }
    }
    $stage = "p4-slot-reset-$SlotId"
    Assert-P4NativeContext $Context $stage
    $budget = Get-P4OperationBudget $Context
    if ($null -eq $budget) { throw 'Slot reset requires an explicit operation budget' }
    Assert-P4OperationActive $Context
    foreach ($path in [IO.Directory]::EnumerateFileSystemEntries($Context.output_directory)) {
        if ([IO.Path]::GetFileName($path).StartsWith("$stage-", [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Slot reset evidence stage already exists'
        }
    }
    $resultPath = Join-Path $Context.output_directory "$stage-result.json"
    $steps = [Collections.Generic.List[object]]::new()
    $record = [ordered]@{ schema = 'nene-pixel-device-slot-reset-v1'; status = 'failure';
        protocol_id = 'nene-pixel-p4-layer-phase-verification-v1'; serial = $Context.serial;
        package = $Context.package; slot_id = $SlotId; preflight_sha256 = $PreflightSha256;
        preservation_path = [IO.Path]::GetFullPath($PreservationPath); preservation_sha256 = $PreservationSha256;
        session = $null; experiment_id = $null; expected_apk_sha256 = $ExpectedInstalledApkHash;
        before_apk_sha256 = $null; final_apk_sha256 = $null; operation_timeout_seconds = $budget.timeout_seconds;
        source_sha256 = [ordered]@{ snapshot = Get-P4SnapshotSources;
            session_helper = Get-P4SnapshotHash $script:P4SessionHelperPath;
            reset_helper = Get-P4SnapshotHash $script:P4SlotResetHelperPath };
        plan_path = $null; plan_sha256 = $null; pre_inventory_path = $null; pre_inventory_sha256 = $null;
        final_inventory_path = $null; final_inventory_sha256 = $null;
        created_utc = [DateTimeOffset]::UtcNow.ToString('o'); steps = @(); reason = $null }
    $failure = $null
    try {
        if ((Get-P4SnapshotHash $PreservationPath) -cne $PreservationSha256) { throw 'Slot reset preservation hash differs' }
        $proofContext = @{}
        foreach ($key in $Context.Keys) { $proofContext[$key] = $Context[$key] }
        $proofContext.output_directory = [IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($PreservationPath))
        $preserved = Read-P4VerifiedPreservation $PreservationPath $proofContext
        if ($preserved.Hash -cne $PreservationSha256) { throw 'Slot reset preservation changed during verification' }
        $record.session = $preserved.Record.session; $record.experiment_id = $preserved.Record.experiment_id
        $original = $preserved.Snapshot.Original
        $apk = Get-P4SnapshotApkIdentity $Context $stage 'before'
        $record.before_apk_sha256 = $apk.Hash
        if ($apk.Hash -cne $ExpectedInstalledApkHash) { throw 'Slot reset installed APK differs' }
        $pre = Get-P4NativePrivateInventory $Context "$stage-pre"
        Assert-P4OperationInventoryLimit $Context $pre.Count
        $prePath = Join-Path $Context.output_directory "$stage-pre-inventory.json"
        Write-P4SessionJson $prePath @($pre)
        $record.pre_inventory_path = $prePath; $record.pre_inventory_sha256 = Get-P4SnapshotHash $prePath
        $plan = New-P4SlotResetPlan $original $record.session $SlotId $pre
        Assert-P4OperationInventoryLimit $Context $plan.Expected.Count
        $planPath = Join-Path $Context.output_directory "$stage-plan.json"
        Write-P4SessionJson $planPath $plan
        $record.plan_path = $planPath; $record.plan_sha256 = Get-P4SnapshotHash $planPath
        $expected = $pre; $map = ConvertTo-P4InventoryMap $pre
        $zero = ConvertTo-P4InventoryMap (New-P4SlotResetPlan $original $record.session $SlotId $pre 0).Expected
        $number = 0
        foreach ($directory in @($zero.Keys | Where-Object { -not $map.ContainsKey($_) } |
                Sort-Object @{ Expression = { $_.Split('/').Length } }, @{ Expression = { $_ } })) {
            $nextMap = ConvertTo-P4InventoryMap @(Get-P4Items $map)
            Add-P4Directory $nextMap $directory
            $next = @(Get-P4Items $nextMap)
            Invoke-P4IsolationStep $Context $stage "mkdir-$number" 'mkdir' `
                (New-P4IsolationMkdirCommand $Context.package $directory) $expected $next $steps
            $map = $nextMap; $expected = $next; $number++
        }
        Assert-P4ExactMap $zero (ConvertTo-P4InventoryMap $expected)
        for ($i = 0; $i -lt $plan.Moves.Count; $i++) {
            $move = $plan.Moves[$i]
            $next = (New-P4SlotResetPlan $original $record.session $SlotId $pre ($i + 1)).Expected
            $type = (ConvertTo-P4InventoryMap $expected)[$move.Source].Type
            Invoke-P4IsolationStep $Context $stage "move-$i" 'move' `
                (New-P4IsolationMoveCommand $Context.package $move.Source $move.Destination $type) $expected $next $steps
            $expected = $next
        }
        $final = Get-P4NativePrivateInventory $Context "$stage-final"
        Assert-P4OperationInventoryLimit $Context $final.Count
        Assert-P4ExactMap (ConvertTo-P4InventoryMap $plan.Expected) (ConvertTo-P4InventoryMap $final)
        [void](Get-P4IsolatedState $original $record.session $final)
        $apkAfter = Get-P4SnapshotApkIdentity $Context $stage 'final'
        $record.final_apk_sha256 = $apkAfter.Hash
        if ($apkAfter.Hash -cne $ExpectedInstalledApkHash -or $apkAfter.Path -cne $apk.Path) {
            throw 'Slot reset APK identity changed'
        }
        Assert-P4RestorationStopped $Context $stage 'stopped-final'
        $finalPath = Join-Path $Context.output_directory "$stage-final-inventory.json"
        Write-P4SessionJson $finalPath @($final)
        $record.final_inventory_path = $finalPath; $record.final_inventory_sha256 = Get-P4SnapshotHash $finalPath
        Assert-P4OperationActive $Context
        $record.status = 'reset'
    } catch { $failure = $_; $record.reason = $_.Exception.Message }
    finally { $record.steps = @($steps.ToArray()); Write-P4SessionJson $resultPath $record }
    if ($null -ne $failure) { throw $failure }
    return [pscustomobject]@{ ResultPath = $resultPath; Record = [pscustomobject]$record }
}

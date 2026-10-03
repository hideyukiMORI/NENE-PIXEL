# Restoration consumes immutable successful isolation evidence. No recovery/resume entry point.
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'p4-device-private-session.ps1')
$script:P4RestorationHelperPath = $PSCommandPath

function Assert-P4RestorationMeaning($Expected, $Actual) {
    if ($null -eq $Expected -or $null -eq $Actual) {
        if ($null -ne $Expected -or $null -ne $Actual) { throw 'Evidence meaning differs' }
        return
    }
    if ($Expected -is [array]) {
        if ($Actual -isnot [array] -or $Expected.Count -ne $Actual.Count) { throw 'Evidence array differs' }
        for ($i = 0; $i -lt $Expected.Count; $i++) { Assert-P4RestorationMeaning $Expected[$i] $Actual[$i] }
    } elseif ($Expected -is [Collections.IDictionary] -or $Expected -is [pscustomobject]) {
        $names = if ($Expected -is [Collections.IDictionary]) { @($Expected.Keys) }
            else { @($Expected.PSObject.Properties.Name) }
        $actualNames = if ($Actual -is [Collections.IDictionary]) { @($Actual.Keys) }
            elseif ($Actual -is [pscustomobject]) { @($Actual.PSObject.Properties.Name) } else { @() }
        if ($names.Count -ne $actualNames.Count) { throw 'Evidence property set differs' }
        foreach ($name in $names) {
            if ($name -cnotin $actualNames) { throw "Missing evidence property: $name" }
            Assert-P4RestorationMeaning $Expected.$name $Actual.$name
        }
    } elseif ($Expected -is [bool]) {
        if ($Actual -isnot [bool] -or $Actual -ne $Expected) { throw 'Evidence boolean differs' }
    } elseif ($Expected -is [string]) {
        if ($Actual -isnot [string] -or $Actual -cne $Expected) { throw 'Evidence text differs' }
    } elseif ($Actual -is [string] -or $Actual -ne $Expected) { throw 'Evidence number differs' }
}

function Read-P4RestorationArtifact([string] $Path, [string] $Hash,
    [string] $Directory, [string] $Leaf) {
    $full = Assert-P4SessionArtifact $Path $Hash $Directory
    if ([IO.Path]::GetFileName($full) -cne $Leaf) { throw 'Preservation fixed artifact path mismatch' }
    return (ConvertFrom-Json -InputObject (Get-Content -LiteralPath $full -Raw) -NoEnumerate)
}

function Read-P4VerifiedPreservation([string] $Path, [Collections.IDictionary] $Context) {
    $path = [IO.Path]::GetFullPath($Path)
    $directory = [IO.Path]::GetFullPath($Context.output_directory)
    $record = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    $names = @('schema', 'status', 'serial', 'package', 'session', 'experiment_id', 'created_utc',
        'snapshot_path', 'snapshot_sha256', 'plan_path', 'plan_sha256', 'isolation_inventory_path',
        'isolation_inventory_sha256', 'original_apk_sha256', 'source_sha256', 'steps', 'reason')
    if (@($record.PSObject.Properties).Count -ne $names.Count) { throw 'Preservation property set changed' }
    foreach ($name in $names) {
        if ($name -cnotin @($record.PSObject.Properties.Name)) { throw "Preservation binding missing: $name" }
    }
    Assert-P4Session $record.session
    Assert-P4Session $record.experiment_id
    $stage = "p4-isolation-$($record.session)-$($record.experiment_id)"
    if ($record.schema -cne 'nene-pixel-device-preservation-v2' -or $record.status -cne 'preserved' -or
        $record.serial -cne $Context.serial -or $record.package -cne $Context.package -or
        $null -ne $record.reason -or
        -not [string]::Equals([IO.Path]::GetDirectoryName($path), $directory, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($path) -cne "$stage-result.json") { throw 'Preservation binding/status differs' }
    [void](Assert-P4SessionArtifact $record.snapshot_path $record.snapshot_sha256 $directory)
    $snapshot = Read-P4VerifiedSnapshot $record.snapshot_path $Context
    if ($record.original_apk_sha256 -cne $snapshot.Record.apk_sha256) { throw 'Preservation original APK differs' }
    Assert-P4RestorationMeaning ([ordered]@{ snapshot = $snapshot.Sources;
        session_helper = Get-P4SnapshotHash $script:P4SessionHelperPath }) $record.source_sha256
    $plan = New-P4IsolationPlan $snapshot.Original $record.session
    $savedPlan = Read-P4RestorationArtifact $record.plan_path $record.plan_sha256 $directory "$stage-plan.json"
    Assert-P4RestorationMeaning $plan $savedPlan
    $inventory = Read-P4RestorationArtifact $record.isolation_inventory_path `
        $record.isolation_inventory_sha256 $directory "$stage-inventory.json"
    if ($inventory -isnot [array]) { throw 'Isolation inventory must be an array' }
    Assert-P4ExactMap (ConvertTo-P4InventoryMap $plan.Expected) (ConvertTo-P4InventoryMap $inventory)
    # Reconstruct the successful isolation sequence; no evidence-supplied move list is trusted.
    $expected = $snapshot.Original
    $map = ConvertTo-P4InventoryMap $expected
    $zero = ConvertTo-P4InventoryMap (New-P4IsolationPlan $snapshot.Original $record.session 0).Expected
    $descriptors = [Collections.Generic.List[object]]::new()
    $number = 0
    foreach ($directoryPath in @($zero.Keys | Where-Object { -not $map.ContainsKey($_) } |
            Sort-Object @{ Expression = { $_.Split('/').Length } }, @{ Expression = { $_ } })) {
        $nextMap = ConvertTo-P4InventoryMap @(Get-P4Items $map)
        Add-P4Directory $nextMap $directoryPath
        $next = @(Get-P4Items $nextMap)
        $descriptors.Add([ordered]@{ kind = 'mkdir'; name = "mkdir-$number";
            script = New-P4IsolationMkdirCommand $Context.package $directoryPath;
            expected_inventory = $expected; next_inventory = $next })
        $map = $nextMap; $expected = $next; $number++
    }
    for ($i = 0; $i -lt $plan.Moves.Count; $i++) {
        $move = $plan.Moves[$i]
        $next = (New-P4IsolationPlan $snapshot.Original $record.session ($i + 1)).Expected
        $descriptors.Add([ordered]@{ kind = 'move'; name = "move-$i";
            script = New-P4IsolationMoveCommand $Context.package $move.Source $move.Destination `
                (ConvertTo-P4InventoryMap $expected)[$move.Source].Type;
            expected_inventory = $expected; next_inventory = $next })
        $expected = $next
    }
    if ($record.steps -isnot [array] -or $record.steps.Count -ne $descriptors.Count) { throw 'Preservation step set differs' }
    for ($i = 0; $i -lt $descriptors.Count; $i++) {
        $descriptor = $descriptors[$i]; $step = $record.steps[$i]
        $intent = Read-P4RestorationArtifact $step.intent_path $step.intent_sha256 $directory "$stage-$($descriptor.name)-intent.json"
        Assert-P4RestorationMeaning $descriptor $intent
        $result = Read-P4RestorationArtifact $step.result_path $step.result_sha256 $directory "$stage-$($descriptor.name)-result.json"
        Assert-P4RestorationMeaning ([ordered]@{ status = 'success'; reason = $null;
            intent_path = $step.intent_path; intent_sha256 = $step.intent_sha256 }) $result
        Assert-P4RestorationMeaning ([ordered]@{ kind = $descriptor.kind; name = $descriptor.name;
            intent_path = $step.intent_path; intent_sha256 = $step.intent_sha256;
            result_path = $step.result_path; result_sha256 = $step.result_sha256;
            status = 'success'; reason = $null }) $step
    }
    return [pscustomobject]@{ Record = $record; Snapshot = $snapshot; Path = $path; Hash = Get-P4SnapshotHash $path }
}

function Assert-P4RestorationStopped([Collections.IDictionary] $Context, [string] $Stage, [string] $Step) {
    [void](Invoke-P4NativeCapture -Context $Context -Stage $Stage -Step $Step `
        -AdbArguments @('shell', '-T', '-n', ('pidof ' + (ConvertTo-P4ShellWord $Context.package))) `
        -MaximumBytes 1024 -ExpectedExit 1 -ExpectedStdout '' -ExpectedStderr '')
}

function Invoke-P4RestorationApkInstall([Collections.IDictionary] $Context, [string] $Stage, $Snapshot) {
    $destination = Join-Path $Context.output_directory "$Stage-install.bin"
    $rawPath = Join-Path $Context.output_directory "$Stage-install.json"
    $nativeTimeout = Get-P4OperationTimeout $Context 120
    Write-P4SessionJson (Join-Path $Context.output_directory "$Stage-install-intent.json") `
        ([ordered]@{ apk_path = $Snapshot.apk_path; apk_sha256 = $Snapshot.apk_sha256;
            arguments = @('-s', $Context.serial, 'install', '-r', '-d', '-t', $Snapshot.apk_path); timeout_seconds = $nativeTimeout })
    Assert-P4OperationActive $Context
    [void](Invoke-P4RawAdbCapture -AdbPath $Context.adb_path `
        -AdbArguments @('-s', $Context.serial, 'install', '-r', '-d', '-t', $Snapshot.apk_path) `
        -TimeoutSeconds $nativeTimeout -DestinationPath $destination -RecordPath $rawPath -MaximumBytes 4096)
    $raw = Get-Content -LiteralPath $rawPath -Raw | ConvertFrom-Json
    if ($raw.status -cne 'success' -or $raw.exit_code -ne 0 -or $null -ne $raw.error -or
        $raw.stderr -cne '' -or $raw.timed_out -or $raw.byte_limit_exceeded -or
        $raw.destination_path -cne [IO.Path]::GetFullPath($destination) -or
        $raw.byte_count -ne ([IO.FileInfo]::new($destination)).Length -or
        $raw.sha256 -cne (Get-P4SnapshotHash $destination)) { throw 'Original APK install failed' }
    $lines = ConvertFrom-P4NativeLines (Read-P4NativeBytes $destination) -AllowCrLf
    if (($lines -join "`n") -cnotin @('Success', "Performing Streamed Install`nSuccess")) {
        throw 'Unexpected original APK install output'
    }
    Assert-P4OperationActive $Context
}

function Invoke-P4PrivateRestoration {
    param([Parameter(Mandatory)][Collections.IDictionary] $Context,
        [Parameter(Mandatory)][string] $PreservationPath,
        [Parameter(Mandatory)][string] $ExpectedInstalledApkHash)
    if ($ExpectedInstalledApkHash -cnotmatch '^[0-9a-f]{64}$') { throw 'Expected installed APK hash must be lowercase SHA-256' }
    # Read just enough identity to reject an occupied host session before any device call.
    $identity = Get-Content -LiteralPath $PreservationPath -Raw | ConvertFrom-Json
    Assert-P4Session $identity.session; Assert-P4Session $identity.experiment_id
    $stage = "p4-restoration-$($identity.session)-$($identity.experiment_id)"
    Assert-P4NativeContext $Context $stage
    foreach ($path in [IO.Directory]::EnumerateFileSystemEntries($Context.output_directory)) {
        if ([IO.Path]::GetFileName($path).StartsWith("p4-restoration-$($identity.session)-", [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Restoration host session is occupied'
        }
    }
    $steps = [Collections.Generic.List[object]]::new()
    $resultPath = Join-Path $Context.output_directory "$stage-result.json"
    $record = [ordered]@{ schema = 'nene-pixel-device-restoration-v2'; status = 'failure';
        serial = $Context.serial; package = $Context.package; session = $identity.session;
        experiment_id = $identity.experiment_id; created_utc = [DateTimeOffset]::UtcNow.ToString('o');
        expected_installed_apk_sha256 = $ExpectedInstalledApkHash;
        preservation_path = [IO.Path]::GetFullPath($PreservationPath); preservation_sha256 = $null;
        snapshot_path = $null; snapshot_sha256 = $null; plan_path = $null; plan_sha256 = $null;
        pre_restore_inventory_path = $null; pre_restore_inventory_sha256 = $null;
        final_inventory_path = $null; final_inventory_sha256 = $null;
        original_apk_sha256 = $null; final_apk_sha256 = $null; apk_install = $null;
        originally_absent_no_backup = $null; source_sha256 = $null; steps = @(); reason = $null }
    $failure = $null
    try {
        $record.preservation_sha256 = Get-P4SnapshotHash $PreservationPath
        Write-P4SessionJson (Join-Path $Context.output_directory "$stage-intent.json") `
            ([ordered]@{ preservation_path = $record.preservation_path;
                preservation_sha256 = $record.preservation_sha256;
                expected_installed_apk_sha256 = $ExpectedInstalledApkHash })
        $preserved = Read-P4VerifiedPreservation $PreservationPath $Context
        $snapshot = $preserved.Snapshot
        $record.snapshot_path = $snapshot.Path; $record.snapshot_sha256 = $snapshot.Hash
        $record.original_apk_sha256 = $snapshot.Record.apk_sha256
        $record.source_sha256 = [ordered]@{ snapshot = $snapshot.Sources;
            session_helper = Get-P4SnapshotHash $script:P4SessionHelperPath;
            restoration_helper = Get-P4SnapshotHash $script:P4RestorationHelperPath }
        Assert-P4RestorationStopped $Context $stage 'stopped-before-apk'
        $apk = Get-P4SnapshotApkIdentity $Context $stage 'before-restoration'
        if ($apk.Hash -cne $ExpectedInstalledApkHash) { throw 'Unexpected installed measurement APK' }
        $install = [ordered]@{ status = 'skipped'; before_hash = $apk.Hash; after_hash = $null;
            raw_record_path = $null; raw_record_sha256 = $null; intent_path = $null; intent_sha256 = $null }
        $record.apk_install = $install
        if ($apk.Hash -cne $snapshot.Record.apk_sha256) {
            $install.status = 'failure'
            $install.raw_record_path = Join-Path $Context.output_directory "$stage-install.json"
            $install.intent_path = Join-Path $Context.output_directory "$stage-install-intent.json"
            Assert-P4RestorationStopped $Context $stage 'stopped-before-install'
            try { Invoke-P4RestorationApkInstall $Context $stage $snapshot.Record }
            finally {
                if ([IO.File]::Exists($install.raw_record_path)) { $install.raw_record_sha256 = Get-P4SnapshotHash $install.raw_record_path }
                if ([IO.File]::Exists($install.intent_path)) { $install.intent_sha256 = Get-P4SnapshotHash $install.intent_path }
            }
        }
        Assert-P4RestorationStopped $Context $stage 'stopped-after-apk'
        $restoredApk = Get-P4SnapshotApkIdentity $Context $stage 'after-apk'
        $install.after_hash = $restoredApk.Hash
        if ($restoredApk.Hash -cne $snapshot.Record.apk_sha256) { throw 'Installed original APK hash differs' }
        if ($install.status -ceq 'failure') { $install.status = 'success' }
        $pre = Get-P4NativePrivateInventory -Context $Context -Stage "$stage-pre-restore"
        $prePath = Join-Path $Context.output_directory "$stage-pre-inventory.json"
        Write-P4SessionJson $prePath @(Get-P4Items (ConvertTo-P4InventoryMap $pre))
        $record.pre_restore_inventory_path = $prePath; $record.pre_restore_inventory_sha256 = Get-P4SnapshotHash $prePath
        $plan = New-P4RestorationPlan $snapshot.Original $identity.session $pre
        $planPath = Join-Path $Context.output_directory "$stage-plan.json"
        Write-P4SessionJson $planPath $plan
        $record.plan_path = $planPath; $record.plan_sha256 = Get-P4SnapshotHash $planPath
        $record.originally_absent_no_backup = $plan.OriginallyAbsentNoBackup
        $expected = $pre; $map = ConvertTo-P4InventoryMap $expected
        $zero = ConvertTo-P4InventoryMap (New-P4RestorationPlan $snapshot.Original $identity.session $pre 0 0).Expected
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
        for ($i = 0; $i -lt $plan.MeasurementMoves.Count; $i++) {
            $move = $plan.MeasurementMoves[$i]
            $next = (New-P4RestorationPlan $snapshot.Original $identity.session $pre ($i + 1) 0).Expected
            $type = (ConvertTo-P4InventoryMap $expected)[$move.Source].Type
            Invoke-P4IsolationStep $Context $stage "measurement-move-$i" 'measurement-move' `
                (New-P4IsolationMoveCommand $Context.package $move.Source $move.Destination $type) $expected $next $steps
            $expected = $next
        }
        for ($i = 0; $i -lt $plan.OriginalMoves.Count; $i++) {
            $move = $plan.OriginalMoves[$i]
            $next = (New-P4RestorationPlan $snapshot.Original $identity.session $pre $plan.MeasurementMoves.Count ($i + 1)).Expected
            $type = (ConvertTo-P4InventoryMap $expected)[$move.Source].Type
            Invoke-P4IsolationStep $Context $stage "original-move-$i" 'original-move' `
                (New-P4IsolationMoveCommand $Context.package $move.Source $move.Destination $type) $expected $next $steps
            $expected = $next
        }
        $final = Get-P4NativePrivateInventory -Context $Context -Stage "$stage-final"
        [void](Assert-P4Restored $snapshot.Original $identity.session $pre $final)
        $finalApk = Get-P4SnapshotApkIdentity $Context $stage 'final-apk'
        $record.final_apk_sha256 = $finalApk.Hash
        if ($finalApk.Hash -cne $snapshot.Record.apk_sha256) { throw 'Final original APK hash differs' }
        Assert-P4RestorationStopped $Context $stage 'stopped-final'
        $finalPath = Join-Path $Context.output_directory "$stage-final-inventory.json"
        Write-P4SessionJson $finalPath @(Get-P4Items (ConvertTo-P4InventoryMap $final))
        $record.final_inventory_path = $finalPath; $record.final_inventory_sha256 = Get-P4SnapshotHash $finalPath
        Assert-P4OperationActive $Context
        $record.status = 'restored'
    } catch { $failure = $_; $record.reason = $_.Exception.Message }
    finally { $record.steps = @($steps.ToArray()); Write-P4SessionJson $resultPath $record }
    if ($null -ne $failure) { throw $failure }
    return [pscustomobject]@{ ResultPath = $resultPath; Record = [pscustomobject]$record }
}

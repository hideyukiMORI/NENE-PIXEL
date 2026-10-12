# Legacy v1 session termination (Issue #190, 案 1): move-only, markers retained, no recovery/resume entry point.
# Host part: pinned inputs and the pure plan. The device executor follows below them.
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'p4-device-private-session.ps1')
. (Join-Path $PSScriptRoot 'p4-device-private-restore.ps1')

$script:P4LegacySession   = 'legacy-190-20261008-2241'
$script:P4LegacyGuard     = 'no_backup/issue-190-user-recovery-20261008-2241'
$script:P4LegacyRecovery  = 'no_backup/nene-pixel-recovery-v1'
$script:P4LegacyArchive   = 'no_backup/p4-user-preservation/legacy-190-20261008-2241'
$script:P4LegacyMarkers   = @('files/profileInstalled', 'files/profileinstaller_profileWrittenFor_lastUpdateTime.dat')

function Get-P4LegacyField($Object, [string] $Name) {
    if ($null -eq $Object -or $Object -isnot [pscustomobject]) { return $null }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Read-P4LegacyInputs {
    param([Collections.IDictionary] $Context, [string] $ExpectedInventoryPath, [string] $ExpectedInventorySha256,
        [string] $OriginalManifestPath, [string] $OriginalManifestSha256, [string] $ExpectedInstalledApkHash)
    foreach ($hash in @($ExpectedInventorySha256, $OriginalManifestSha256, $ExpectedInstalledApkHash)) {
        if ($hash -cnotmatch '^[0-9a-f]{64}$') { throw 'Legacy input hash must be lowercase SHA-256' }
    }
    if ((Get-P4SnapshotHash $ExpectedInventoryPath) -cne $ExpectedInventorySha256) {
        throw 'Legacy expected inventory hash differs'
    }
    if ((Get-P4SnapshotHash $OriginalManifestPath) -cne $OriginalManifestSha256) {
        throw 'Legacy original manifest hash differs'
    }
    $json = Get-Content -LiteralPath $ExpectedInventoryPath -Raw | ConvertFrom-Json
    if ((Get-P4LegacyField $json 'schema') -cne 'nene-pixel-legacy-readonly-metadata-v1' -or
        (Get-P4LegacyField $json 'serial') -cne $Context.serial -or
        (Get-P4LegacyField $json 'package') -cne $Context.package -or
        (Get-P4LegacyField $json 'installed_apk_sha256') -cne $ExpectedInstalledApkHash -or
        $null -eq (Get-P4LegacyField $json 'inventory')) { throw 'Legacy expected inventory binding differs' }
    $manifest = Get-Content -LiteralPath $OriginalManifestPath -Raw | ConvertFrom-Json
    if ((Get-P4LegacyField $manifest 'schema') -cne 'nene-pixel-device-preservation-v1' -or
        (Get-P4LegacyField $manifest 'state') -cne 'preserved' -or
        (Get-P4LegacyField $manifest 'serial') -cne $Context.serial -or
        (Get-P4LegacyField $manifest 'package') -cne $Context.package -or
        (Get-P4LegacyField $manifest 'guard') -cne $script:P4LegacyGuard) {
        throw 'Legacy original manifest binding differs'
    }
    $expected = ConvertTo-P4InventoryMap @($json.inventory)
    $originalRecovery = Get-P4LegacyField (Get-P4LegacyField $manifest 'recovery') $script:P4LegacyRecovery
    if ($null -eq $originalRecovery) { throw 'Legacy original recovery missing' }
    $guarded = "$script:P4LegacyGuard/nene-pixel-recovery-v1"
    if (-not $expected.ContainsKey($guarded) -or $expected[$guarded].Type -cne 'file' -or
        $expected[$guarded].Hash -cne (Get-P4LegacyField $originalRecovery 'sha256') -or
        $expected[$guarded].Size -ne (Get-P4LegacyField $originalRecovery 'bytes')) {
        throw 'Legacy guarded recovery differs from original'
    }
    $live = $script:P4LegacyRecovery
    if (-not $expected.ContainsKey($live) -or $expected[$live].Type -cne 'file' -or
        $expected[$live].Hash -ceq (Get-P4LegacyField $originalRecovery 'sha256')) {
        throw 'Legacy live recovery is ambiguous'
    }
    $original = Get-P4LegacyField $manifest 'original'
    if ($null -eq $original -or $original -isnot [pscustomobject]) { throw 'Legacy marker missing' }
    $markers = [Collections.Generic.List[object]]::new()
    foreach ($marker in $script:P4LegacyMarkers) {
        $record = Get-P4LegacyField $original $marker
        if ($null -eq $record -or -not $expected.ContainsKey($marker) -or $expected[$marker].Type -cne 'file') {
            throw 'Legacy marker missing'
        }
        $current = $expected[$marker]
        $markers.Add([ordered]@{ path = $marker; original_sha256 = (Get-P4LegacyField $record 'sha256');
            original_bytes = (Get-P4LegacyField $record 'bytes'); current_sha256 = $current.Hash;
            current_size = $current.Size; current_mtime_ns = $current.MtimeNs })
    }
    $identical = 0
    foreach ($property in $original.PSObject.Properties) {
        $path = $property.Name
        if (-not $expected.ContainsKey($path) -or $expected[$path].Type -cne 'file') {
            throw "Legacy original path missing: $path"
        }
        if ($path -cin $script:P4LegacyMarkers -or $path -ceq $script:P4LegacyRecovery) { continue }
        if ($expected[$path].Hash -cne (Get-P4LegacyField $property.Value 'sha256') -or
            $expected[$path].Size -ne (Get-P4LegacyField $property.Value 'bytes')) {
            throw "Legacy original file changed: $path"
        }
        $identical++
    }
    if ($identical -ne 87) { throw 'Legacy identical count differs' }
    foreach ($path in $expected.Keys) {
        if (Test-P4Below $path $script:P4LegacyArchive) { throw 'Legacy archive occupied' }
    }
    return [pscustomobject]@{ Expected = @(Get-P4Items $expected); ExpectedMap = $expected; Manifest = $manifest
        Inventory = $json; OriginalRecovery = $originalRecovery; IdenticalCount = $identical
        Markers = @($markers.ToArray()) }
}

function Move-P4LegacyEntry($Map, [string] $Source, [string] $Destination) {
    if (-not $Map.ContainsKey($Source) -or $Map[$Source].Type -cne 'file') {
        throw "Legacy plan source missing: $Source"
    }
    if ($Map.ContainsKey($Destination)) { throw "Legacy plan destination occupied: $Destination" }
    $entry = $Map[$Source]
    [void] $Map.Remove($Source)
    $Map.Add($Destination, [pscustomobject]@{ Path = $Destination; Type = 'file'; Size = $entry.Size;
        Hash = $entry.Hash; MtimeNs = $entry.MtimeNs; LinkCount = $entry.LinkCount })
}

function New-P4LegacyPlan([object[]] $Expected, [string] $Package) {
    $map = ConvertTo-P4InventoryMap $Expected
    $parts = "$script:P4LegacyArchive/measurement/no_backup".Split('/')
    $directories = [Collections.Generic.List[string]]::new()
    for ($i = 1; $i -le $parts.Length; $i++) {
        $path = $parts[0..($i - 1)] -join '/'
        if (-not $map.ContainsKey($path)) { $directories.Add($path) }
    }
    $measurement = [ordered]@{ Source = $script:P4LegacyRecovery
        Destination = "$script:P4LegacyArchive/measurement/no_backup/nene-pixel-recovery-v1" }
    $originalMove = [ordered]@{ Source = "$script:P4LegacyGuard/nene-pixel-recovery-v1"
        Destination = $script:P4LegacyRecovery }
    $steps = [Collections.Generic.List[object]]::new()
    for ($n = 0; $n -lt $directories.Count; $n++) {
        $before = @(Get-P4Items $map)
        $map = ConvertTo-P4InventoryMap $before
        Add-P4Directory $map $directories[$n]
        $steps.Add([ordered]@{ kind = 'mkdir'; name = "mkdir-$n"
            script = New-P4IsolationMkdirCommand $Package $directories[$n]
            expected = $before; next = @(Get-P4Items $map) })
    }
    $moves = @([ordered]@{ kind = 'measurement-move'; move = $measurement },
        [ordered]@{ kind = 'original-move'; move = $originalMove })
    foreach ($item in $moves) {
        $before = @(Get-P4Items $map)
        $map = ConvertTo-P4InventoryMap $before
        Move-P4LegacyEntry $map $item.move.Source $item.move.Destination
        $steps.Add([ordered]@{ kind = $item.kind; name = "$($item.kind)-0"
            script = New-P4IsolationMoveCommand $Package $item.move.Source $item.move.Destination 'file'
            expected = $before; next = @(Get-P4Items $map) })
    }
    return [pscustomobject]@{ Directories = [string[]] $directories.ToArray(); MeasurementMove = $measurement
        OriginalMove = $originalMove; Steps = [object[]] $steps.ToArray(); Final = @(Get-P4Items $map) }
}

function Invoke-P4LegacyTermination {
    param([Parameter(Mandatory)][Collections.IDictionary] $Context,
        [Parameter(Mandatory)][string] $ExpectedInventoryPath, [Parameter(Mandatory)][string] $ExpectedInventorySha256,
        [Parameter(Mandatory)][string] $OriginalManifestPath, [Parameter(Mandatory)][string] $OriginalManifestSha256,
        [Parameter(Mandatory)][string] $ExpectedInstalledApkHash)
    $stage = "p4-legacy-$script:P4LegacySession"
    Assert-P4NativeContext $Context $stage
    foreach ($path in [IO.Directory]::EnumerateFileSystemEntries($Context.output_directory)) {
        if ([IO.Path]::GetFileName($path).StartsWith($stage, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Legacy host session is occupied'
        }
    }
    # Every host refusal happens here, before any device call.
    $inputs = Read-P4LegacyInputs $Context $ExpectedInventoryPath $ExpectedInventorySha256 `
        $OriginalManifestPath $OriginalManifestSha256 $ExpectedInstalledApkHash
    $plan = New-P4LegacyPlan $inputs.Expected $Context.package
    $steps = [Collections.Generic.List[object]]::new()
    $resultPath = Join-Path $Context.output_directory "$stage-result.json"
    $record = [ordered]@{ schema = 'nene-pixel-legacy-termination-v1'; status = 'failure';
        serial = $Context.serial; package = $Context.package; session = $script:P4LegacySession;
        created_utc = [DateTimeOffset]::UtcNow.ToString('o');
        expected_inventory_path = [IO.Path]::GetFullPath($ExpectedInventoryPath);
        expected_inventory_sha256 = $ExpectedInventorySha256;
        original_manifest_path = [IO.Path]::GetFullPath($OriginalManifestPath);
        original_manifest_sha256 = $OriginalManifestSha256;
        expected_installed_apk_sha256 = $ExpectedInstalledApkHash;
        identical_original_files = $inputs.IdenticalCount; markers = @($inputs.Markers);
        plan_path = $null; plan_sha256 = $null; pre_inventory_path = $null; pre_inventory_sha256 = $null;
        final_inventory_path = $null; final_inventory_sha256 = $null; final_apk_sha256 = $null;
        exceptions = @(
            [ordered]@{ kind = 'profile-installer-markers-retained'; paths = @($script:P4LegacyMarkers);
                statement = 'Both ProfileInstaller markers keep their current bytes; the library rewrites them ' +
                    'on the next launch after an install change.' },
            [ordered]@{ kind = 'fractional-mtime-unproven';
                statement = 'The v1 record stores integer seconds only; seconds equality and relative underlay ' +
                    'LRU order are asserted, exact original fractional mtimes are not.' });
        steps = @(); reason = $null }
    $planPath = Join-Path $Context.output_directory "$stage-plan.json"
    Write-P4SessionJson (Join-Path $Context.output_directory "$stage-intent.json") `
        ([ordered]@{ expected_inventory_path = $record.expected_inventory_path
            expected_inventory_sha256 = $ExpectedInventorySha256
            original_manifest_path = $record.original_manifest_path; original_manifest_sha256 = $OriginalManifestSha256
            expected_installed_apk_sha256 = $ExpectedInstalledApkHash; plan_path = $planPath })
    Write-P4SessionJson $planPath $plan
    $record.plan_path = $planPath; $record.plan_sha256 = Get-P4SnapshotHash $planPath
    $failure = $null
    try {
        Assert-P4RestorationStopped $Context $stage 'stopped-before'
        $apk = Get-P4SnapshotApkIdentity $Context $stage 'apk'
        if ($apk.Hash -cne $ExpectedInstalledApkHash) { throw 'Installed APK differs from expected' }
        $pre = Get-P4NativePrivateInventory -Context $Context -Stage "$stage-pre"
        Assert-P4ExactMap $inputs.ExpectedMap (ConvertTo-P4InventoryMap $pre)
        $prePath = Join-Path $Context.output_directory "$stage-pre-inventory.json"
        Write-P4SessionJson $prePath @(Get-P4Items (ConvertTo-P4InventoryMap $pre))
        $record.pre_inventory_path = $prePath; $record.pre_inventory_sha256 = Get-P4SnapshotHash $prePath
        foreach ($step in $plan.Steps) {
            Invoke-P4IsolationStep $Context $stage $step.name $step.kind $step.script $step.expected $step.next $steps
        }
        $final = Get-P4NativePrivateInventory -Context $Context -Stage "$stage-final"
        $finalMap = ConvertTo-P4InventoryMap $final
        Assert-P4ExactMap (ConvertTo-P4InventoryMap $plan.Final) $finalMap
        if (-not $finalMap.ContainsKey($script:P4LegacyRecovery) -or
            $finalMap[$script:P4LegacyRecovery].Hash -cne (Get-P4LegacyField $inputs.OriginalRecovery 'sha256') -or
            -not $finalMap.ContainsKey($script:P4LegacyGuard) -or
            $finalMap[$script:P4LegacyGuard].Type -cne 'directory') { throw 'Legacy final state differs' }
        $finalApk = Get-P4SnapshotApkIdentity $Context $stage 'final-apk'
        $record.final_apk_sha256 = $finalApk.Hash
        if ($finalApk.Hash -cne $ExpectedInstalledApkHash) { throw 'Final installed APK differs from expected' }
        Assert-P4RestorationStopped $Context $stage 'stopped-final'
        $finalPath = Join-Path $Context.output_directory "$stage-final-inventory.json"
        Write-P4SessionJson $finalPath @(Get-P4Items $finalMap)
        $record.final_inventory_path = $finalPath; $record.final_inventory_sha256 = Get-P4SnapshotHash $finalPath
        Assert-P4OperationActive $Context
        $record.status = 'terminated'
    } catch { $failure = $_; $record.reason = $_.Exception.Message }
    finally { $record.steps = @($steps.ToArray()); Write-P4SessionJson $resultPath $record }
    if ($null -ne $failure) { throw $failure }
    return [pscustomobject]@{ ResultPath = $resultPath; Record = [pscustomobject]$record }
}

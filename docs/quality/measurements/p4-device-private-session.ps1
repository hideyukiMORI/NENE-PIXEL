# Native isolation of one verified, stopped private snapshot. No restore or device entry point.
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'p4-device-private-snapshot.ps1')
$script:P4SessionHelperPath = $PSCommandPath

function Assert-P4SessionArtifact([string] $Path, [string] $ExpectedHash, [string] $Directory) {
    $full = [IO.Path]::GetFullPath($Path)
    if (-not [string]::Equals([IO.Path]::GetDirectoryName($full), $Directory,
            [StringComparison]::OrdinalIgnoreCase) -or -not [IO.File]::Exists($full) -or
        $ExpectedHash -cnotmatch '^[0-9a-f]{64}$' -or
        (Get-P4SnapshotHash $full) -cne $ExpectedHash) {
        throw "Snapshot artifact binding failed: $Path"
    }
    return $full
}

function Read-P4VerifiedSnapshot {
    param([string] $SnapshotPath, [System.Collections.IDictionary] $Context)
    $path = [IO.Path]::GetFullPath($SnapshotPath)
    if (-not [IO.File]::Exists($path)) { throw 'Snapshot record missing' }
    $snapshot = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    if ($snapshot.schema -cne 'nene-pixel-device-private-snapshot-v1' -or
        $snapshot.status -cne 'verified-snapshot' -or $snapshot.serial -cne $Context.serial -or
        $snapshot.package -cne $Context.package) { throw 'Snapshot status/device/package binding failed' }
    Assert-P4Session $snapshot.stage
    $directory = [IO.Path]::GetDirectoryName($path)
    if (-not [string]::Equals($directory, [IO.Path]::GetFullPath($Context.output_directory),
            [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($path) -cne "$($snapshot.stage)-snapshot.json") {
        throw 'Snapshot record is outside its fixed evidence stage'
    }
    $fixed = [ordered]@{
        inventory = @('inventory_path', 'inventory_sha256', 'inventory-before.json')
        inventory_after = @('inventory_after_path', 'inventory_after_sha256', 'inventory-after.json')
        archive = @('archive_path', 'archive_sha256', 'original.tar')
        apk = @('apk_path', 'apk_sha256', 'original.apk')
    }
    foreach ($item in $fixed.Values) {
        $expected = Join-Path $directory "$($snapshot.stage)-$($item[2])"
        if (-not [string]::Equals([IO.Path]::GetFullPath($snapshot.($item[0])), $expected,
                [StringComparison]::OrdinalIgnoreCase)) { throw 'Snapshot fixed artifact path mismatch' }
        [void](Assert-P4SessionArtifact $snapshot.($item[0]) $snapshot.($item[1]) $directory)
    }
    foreach ($item in @(@('apk_transfer_record_sha256', 'original-apk.json'),
            @('archive_transfer_record_sha256', 'original-tar.json'))) {
        $record = Join-Path $directory "$($snapshot.stage)-$($item[1])"
        if ($null -ne $snapshot.($item[0])) {
            [void](Assert-P4SessionArtifact $record $snapshot.($item[0]) $directory)
        } elseif ($item[1] -ceq 'original-apk.json' -or
            (([IO.FileInfo]::new($snapshot.archive_path)).Length -ne 1024)) {
            throw 'Snapshot transfer record missing'
        }
    }
    Assert-P4SnapshotApkPath $snapshot.installed_apk_path
    if ($snapshot.apk_sha256 -cne (Get-P4SnapshotHash $snapshot.apk_path)) {
        throw 'Original APK hash mismatch'
    }
    $before = ConvertFrom-Json -InputObject (Get-Content -LiteralPath $snapshot.inventory_path -Raw) -NoEnumerate
    $after = ConvertFrom-Json -InputObject (Get-Content -LiteralPath $snapshot.inventory_after_path -Raw) -NoEnumerate
    if ($before -isnot [array] -or $after -isnot [array]) {
        throw 'Snapshot inventories must be JSON arrays'
    }
    $beforeMap = ConvertTo-P4InventoryMap $before
    Assert-P4ExactMap $beforeMap (ConvertTo-P4InventoryMap $after)
    $sources = Get-P4SnapshotSources
    if (@($snapshot.source_sha256.PSObject.Properties).Count -ne $sources.Count) {
        throw 'Snapshot source set changed'
    }
    foreach ($key in $sources.Keys) {
        if ($snapshot.source_sha256.$key -cne $sources[$key]) { throw "Snapshot source changed: $key" }
    }
    return [pscustomobject]@{ Record = $snapshot; Path = $path; Hash = (Get-P4SnapshotHash $path);
        Original = @(Get-P4Items $beforeMap); Sources = $sources }
}

function Write-P4SessionJson([string] $Path, $Value) {
    Write-NewInvocationFile $Path (ConvertTo-Json -InputObject $Value -Depth 20)
}

function Assert-P4IsolationOutputsVacant([System.Collections.IDictionary] $Context,
    [string] $Session, [string] $Stage) {
    $prefix = "p4-isolation-$Session-"
    foreach ($path in [IO.Directory]::EnumerateFileSystemEntries($Context.output_directory)) {
        $name = [IO.Path]::GetFileName($path)
        if ($name.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) -or
            $name.StartsWith("$Stage-", [StringComparison]::OrdinalIgnoreCase)) {
            throw "Isolation host session is occupied: $Session"
        }
    }
}

function Get-P4IsolationPidGuard([string] $Package) {
    return 'p4_pid=$(pidof ' + (ConvertTo-P4ShellWord $Package) +
        ' 2>&1); p4_status=$?; [ "$p4_status" -eq 1 ] && [ -z "$p4_pid" ] || exit 73; '
}

function New-P4IsolationMkdirCommand([string] $Package, [string] $Path) {
    $word = ConvertTo-P4ShellWord $Path
    $slash = $Path.LastIndexOf('/')
    $parent = ConvertTo-P4ShellWord $(if ($slash -lt 0) { '.' } else { $Path.Substring(0, $slash) })
    return (Get-P4IsolationPidGuard $Package) +
        '[ -d ' + $parent + ' ] && [ ! -L ' + $parent + ' ] || exit 74; ' +
        '[ ! -e ' + $word + ' ] && [ ! -L ' + $word + ' ] || exit 74; mkdir ' + $word
}

function New-P4IsolationMoveCommand([string] $Package, [string] $Source,
    [string] $Destination, [string] $Type) {
    $src = ConvertTo-P4ShellWord $Source
    $dst = ConvertTo-P4ShellWord $Destination
    $parent = ConvertTo-P4ShellWord ($Destination.Substring(0, $Destination.LastIndexOf('/')))
    $kind = if ($Type -ceq 'file') { '-f' } else { '-d' }
    return (Get-P4IsolationPidGuard $Package) +
        '[ ' + $kind + ' ' + $src + ' ] && [ ! -L ' + $src + ' ] || exit 74; ' +
        '[ -d ' + $parent + ' ] && [ ! -L ' + $parent + ' ] || exit 74; ' +
        '[ ! -e ' + $dst + ' ] && [ ! -L ' + $dst + ' ] || exit 74; ' +
        'p4_src_dev=$(stat -c %d ' + $src + ' 2>/dev/null) || exit 74; ' +
        'p4_dst_dev=$(stat -c %d ' + $parent + ' 2>/dev/null) || exit 74; ' +
        'case "$p4_src_dev:$p4_dst_dev" in *[!0-9:]*|:*) exit 74;; esac; ' +
        '[ -n "$p4_src_dev" ] && [ -n "$p4_dst_dev" ] && ' +
        '[ "$p4_src_dev" = "$p4_dst_dev" ] || exit 74; mv -nT ' + $src + ' ' + $dst
}

function Invoke-P4IsolationStep {
    param([System.Collections.IDictionary] $Context, [string] $Stage, [string] $Name,
        [string] $Kind, [string] $Script, [object[]] $Expected, [object[]] $Next,
        [System.Collections.Generic.List[object]] $Steps)
    $current = Get-P4NativePrivateInventory -Context $Context -Stage "$Stage-$Name-pre"
    Assert-P4ExactMap (ConvertTo-P4InventoryMap $Expected) (ConvertTo-P4InventoryMap $current)
    $intentPath = Join-Path $Context.output_directory "$Stage-$Name-intent.json"
    $resultPath = Join-Path $Context.output_directory "$Stage-$Name-result.json"
    Write-P4SessionJson $intentPath ([ordered]@{ kind = $Kind; name = $Name; script = $Script;
        expected_inventory = $Expected; next_inventory = $Next })
    $step = [ordered]@{ kind = $Kind; name = $Name; intent_path = $intentPath;
        intent_sha256 = Get-P4SnapshotHash $intentPath; result_path = $resultPath;
        result_sha256 = $null; status = 'failure'; reason = $null }
    try {
        $bytes = Invoke-P4NativeRunAs -Context $Context -Stage $Stage -Step $Name -Script $Script -MaximumBytes 256
        if ($bytes.Length -ne 0) { throw 'Mutation command emitted stdout' }
        $observed = Get-P4NativePrivateInventory -Context $Context -Stage "$Stage-$Name-post"
        Assert-P4ExactMap (ConvertTo-P4InventoryMap $Next) (ConvertTo-P4InventoryMap $observed)
        $step.status = 'success'
    } catch { $step.reason = $_.Exception.Message; throw }
    finally {
        Write-P4SessionJson $resultPath ([ordered]@{ status = $step.status; reason = $step.reason;
            intent_path = $intentPath; intent_sha256 = $step.intent_sha256 })
        $step.result_sha256 = Get-P4SnapshotHash $resultPath
        $Steps.Add([pscustomobject]$step)
    }
}

function Invoke-P4PrivateIsolation {
    param([Parameter(Mandatory)][System.Collections.IDictionary] $Context,
        [Parameter(Mandatory)][string] $Session,
        [Parameter(Mandatory)][string] $ExperimentId,
        [Parameter(Mandatory)][string] $SnapshotPath)
    Assert-P4Session $Session
    Assert-P4Session $ExperimentId
    $stage = "p4-isolation-$Session-$ExperimentId"
    Assert-P4NativeContext $Context $stage
    Assert-P4IsolationOutputsVacant $Context $Session $stage
    $resultPath = Join-Path $Context.output_directory "$stage-result.json"
    $intentPath = Join-Path $Context.output_directory "$stage-intent.json"
    $planPath = Join-Path $Context.output_directory "$stage-plan.json"
    $inventoryPath = Join-Path $Context.output_directory "$stage-inventory.json"
    $steps = [Collections.Generic.List[object]]::new()
    $record = [ordered]@{ schema = 'nene-pixel-device-preservation-v2'; status = 'failure';
        serial = $Context.serial; package = $Context.package; session = $Session;
        experiment_id = $ExperimentId; created_utc = [DateTimeOffset]::UtcNow.ToString('o');
        snapshot_path = $null; snapshot_sha256 = $null; plan_path = $null; plan_sha256 = $null;
        isolation_inventory_path = $null; isolation_inventory_sha256 = $null;
        original_apk_sha256 = $null; source_sha256 = $null; steps = @(); reason = $null }
    $failure = $null
    try {
        Write-P4SessionJson $intentPath ([ordered]@{ session = $Session; experiment_id = $ExperimentId;
            serial = $Context.serial; package = $Context.package; snapshot_path = [IO.Path]::GetFullPath($SnapshotPath);
            snapshot_sha256 = Get-P4SnapshotHash $SnapshotPath })
        $snapshot = Read-P4VerifiedSnapshot $SnapshotPath $Context
        $record.snapshot_path = $snapshot.Path
        $record.snapshot_sha256 = $snapshot.Hash
        $record.original_apk_sha256 = $snapshot.Record.apk_sha256
        $record.source_sha256 = [ordered]@{ snapshot = $snapshot.Sources;
            session_helper = Get-P4SnapshotHash $script:P4SessionHelperPath }
        $apk = Get-P4SnapshotApkIdentity $Context $stage 'pre-isolation'
        if ($apk.Path -cne $snapshot.Record.installed_apk_path -or
            $apk.Hash -cne $snapshot.Record.apk_sha256) { throw 'Installed original APK identity changed' }
        $current = Get-P4NativePrivateInventory -Context $Context -Stage "$stage-original"
        Assert-P4ExactMap (ConvertTo-P4InventoryMap $snapshot.Original) (ConvertTo-P4InventoryMap $current)
        $plan = New-P4IsolationPlan $snapshot.Original $Session
        Write-P4SessionJson $planPath $plan
        $record.plan_path = $planPath
        $record.plan_sha256 = Get-P4SnapshotHash $planPath
        $expected = $snapshot.Original
        $zero = (New-P4IsolationPlan $snapshot.Original $Session 0).Expected
        $zeroMap = ConvertTo-P4InventoryMap $zero
        $scaffold = ConvertTo-P4InventoryMap $expected
        $directories = @($zeroMap.Keys | Where-Object { -not $scaffold.ContainsKey($_) } |
            Sort-Object @{ Expression = { $_.Split('/').Length } }, @{ Expression = { $_ } })
        $number = 0
        foreach ($directory in $directories) {
            $nextMap = ConvertTo-P4InventoryMap @(Get-P4Items $scaffold)
            Add-P4Directory $nextMap $directory
            $next = @(Get-P4Items $nextMap)
            Invoke-P4IsolationStep $Context $stage "mkdir-$number" 'mkdir' `
                (New-P4IsolationMkdirCommand $Context.package $directory) $expected $next $steps
            $expected = $next
            $scaffold = $nextMap
            $number++
        }
        Assert-P4ExactMap $zeroMap (ConvertTo-P4InventoryMap $expected)
        for ($i = 0; $i -lt $plan.Moves.Count; $i++) {
            $move = $plan.Moves[$i]
            $next = (New-P4IsolationPlan $snapshot.Original $Session ($i + 1)).Expected
            $type = (ConvertTo-P4InventoryMap $snapshot.Original)[$move.Source].Type
            Invoke-P4IsolationStep $Context $stage "move-$i" 'move' `
                (New-P4IsolationMoveCommand $Context.package $move.Source $move.Destination $type) `
                $expected $next $steps
            $expected = $next
        }
        $final = Get-P4NativePrivateInventory -Context $Context -Stage "$stage-final"
        [void](Assert-P4Isolation $snapshot.Original $Session $final)
        $apkFinal = Get-P4SnapshotApkIdentity $Context $stage 'post-isolation'
        if ($apkFinal.Path -cne $apk.Path -or $apkFinal.Hash -cne $apk.Hash) {
            throw 'Installed original APK identity changed after isolation'
        }
        Write-P4SessionJson $inventoryPath @(Get-P4Items (ConvertTo-P4InventoryMap $final))
        $record.isolation_inventory_path = $inventoryPath
        $record.isolation_inventory_sha256 = Get-P4SnapshotHash $inventoryPath
        $record.status = 'preserved'
    } catch { $failure = $_; $record.reason = $_.Exception.Message }
    finally {
        $record.steps = @($steps.ToArray())
        Write-P4SessionJson $resultPath $record
    }
    if ($null -ne $failure) { throw $failure }
    return [pscustomobject]@{ ResultPath = $resultPath; Record = [pscustomobject]$record }
}

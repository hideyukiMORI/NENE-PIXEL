# Read-only composition of the native observer, encoded transfer and tar verifier.
# This is a host evidence operation, not isolation or phase admission.
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'p4-device-private-native.ps1')

function Get-P4SnapshotHash([string] $Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-P4SnapshotSources {
    $relative = @(
        'p4-device-private-snapshot.ps1', 'p4-device-private-native.ps1',
        'p4-device-private-observation.ps1', 'p4-device-private-preservation.ps1',
        'p4-device-private-transport.ps1', 'p4-indexed-device-lanes.ps1',
        'p4-operation-budget.ps1',
        '../bounded-native-command.ps1')
    $items = [ordered]@{}
    foreach ($name in $relative) {
        $path = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot $name))
        $items[$name] = Get-P4SnapshotHash $path
    }
    return $items
}

function Assert-P4SnapshotApkPath([string] $Path) {
    if ($Path -cnotmatch '^/data/app/(?:[A-Za-z0-9._~+=-]+/)+base\.apk$') {
        throw 'Installed APK path is not a canonical single base APK path'
    }
    foreach ($part in $Path.Split('/')) {
        if ($part -ceq '.' -or $part -ceq '..') { throw 'Unsafe installed APK component' }
    }
}

function Invoke-P4SnapshotEncoded {
    param([System.Collections.IDictionary] $Context, [string] $Stage, [string] $Step,
        [string] $Script, [long] $MaximumBytes)
    $destination = Join-Path $Context.output_directory "$Stage-$Step.bin"
    $record = Join-Path $Context.output_directory "$Stage-$Step.json"
    $nativeTimeout = Get-P4OperationTimeout $Context 30
    [void] (Invoke-P4EncodedShellCapture -AdbPath $Context.adb_path -Serial $Context.serial `
        -Script $Script -TimeoutSeconds $nativeTimeout -DestinationPath $destination `
        -RecordPath $record -MaximumBytes $MaximumBytes)
    Assert-P4OperationActive $Context
    return $destination
}

function Get-P4SnapshotApkIdentity {
    param([System.Collections.IDictionary] $Context, [string] $Stage, [string] $Step)
    $package = ConvertTo-P4ShellWord $Context.package
    $pathFile = Invoke-P4SnapshotEncoded $Context $Stage "$Step-apk-path" ('pm path ' + $package) 4096
    $lines = ConvertFrom-P4NativeLines (Read-P4NativeBytes $pathFile)
    if ($lines.Count -ne 1 -or $lines[0] -cnotmatch '^package:(.+)$') {
        throw 'Expected exactly one installed base APK path'
    }
    $path = $Matches[1]
    Assert-P4SnapshotApkPath $path
    $hashFile = Invoke-P4SnapshotEncoded $Context $Stage "$Step-apk-hash" `
        ('sha256sum ' + (ConvertTo-P4ShellWord $path)) 4096
    $hashLines = ConvertFrom-P4NativeLines (Read-P4NativeBytes $hashFile)
    if ($hashLines.Count -ne 1 -or $hashLines[0] -cnotmatch '^([0-9a-f]{64})  (.+)$' -or
        $Matches[2] -cne $path) { throw 'Installed APK hash output is invalid' }
    return [pscustomobject]@{ Path = $path; Hash = $Matches[1] }
}

function Get-P4SnapshotFixedPaths([System.Collections.IDictionary] $Context, [string] $Stage) {
    $names = [Collections.Generic.List[string]]::new()
    foreach ($leaf in @('inventory-before.json', 'inventory-after.json', 'original.apk',
            'original.tar', 'snapshot.json')) { $names.Add("$Stage-$leaf") }
    foreach ($step in @('before-apk-path', 'before-apk-hash', 'after-apk-path',
            'after-apk-hash', 'original-apk', 'original-tar')) {
        $leaf = "$Stage-$step"
        foreach ($suffix in @('.bin', '.json', '.bin.base64', '.json.encoded.json',
                '.json.encoded.json.command.json')) { $names.Add($leaf + $suffix) }
    }
    foreach ($observation in @('before', 'after')) {
        $prefix = "$Stage-$observation"
        foreach ($step in @('features', 'shell-proof', 'stopped-before', 'encoded-byte-proof',
                'encoded-upstream-proof', 'paths-before', 'paths-after', 'stopped-after')) {
            foreach ($suffix in @('.bin', '.json', '.json.command.json', '.bin.base64',
                    '.json.encoded.json', '.json.encoded.json.command.json')) {
                $names.Add("$prefix-$step$suffix")
            }
        }
    }
    return ,@($names | ForEach-Object { Join-Path $Context.output_directory $_ })
}

function Assert-P4SnapshotVacant([System.Collections.IDictionary] $Context, [string] $Stage) {
    foreach ($path in (Get-P4SnapshotFixedPaths $Context $Stage)) {
        if ([IO.File]::Exists($path) -or [IO.Directory]::Exists($path)) {
            throw "Snapshot output path already exists: $path"
        }
    }
    # Legacy native stat/hash batches have no entry cap. Reject any old evidence under this
    # unique stage prefix as well, including directories and partial prior attempts.
    $prefix = "$Stage-"
    foreach ($path in [IO.Directory]::EnumerateFileSystemEntries($Context.output_directory)) {
        if ([IO.Path]::GetFileName($path).StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Snapshot stage is occupied: $Stage"
        }
    }
}

function Write-P4SnapshotJson([string] $Path, $Value) {
    Write-NewInvocationFile $Path (ConvertTo-Json -InputObject $Value -Depth 12)
}

function New-P4PrivateSnapshot {
    param([Parameter(Mandatory)][System.Collections.IDictionary] $Context,
        [Parameter(Mandatory)][string] $Stage,
        [Parameter(Mandatory)][long] $MaximumArchiveBytes,
        [Parameter(Mandatory)][long] $MaximumApkBytes)
    Assert-P4NativeContext $Context $Stage
    $sources = Get-P4SnapshotSources
    $resultPath = Join-Path $Context.output_directory "$Stage-snapshot.json"
    $result = [ordered]@{
        schema = 'nene-pixel-device-private-snapshot-v1'
        status = 'failure'
        serial = $Context.serial
        package = $Context.package
        stage = $Stage
        created_utc = [DateTimeOffset]::UtcNow.ToString('o')
        source_sha256 = $sources
        maximum_archive_bytes = $MaximumArchiveBytes
        maximum_apk_bytes = $MaximumApkBytes
        inventory_path = $null
        inventory_sha256 = $null
        inventory_after_path = $null
        inventory_after_sha256 = $null
        archive_path = $null
        archive_sha256 = $null
        archive_transfer_record_sha256 = $null
        apk_path = $null
        apk_sha256 = $null
        apk_transfer_record_sha256 = $null
        installed_apk_path = $null
        fractional_file_mtime_observed = $false
        reason = $null
    }
    $failure = $null
    try {
        Assert-P4SnapshotVacant $Context $Stage
        if ($MaximumArchiveBytes -lt 1024 -or $MaximumApkBytes -lt 1) {
            throw 'Snapshot archive/APK caps must be explicit positive bounds (archive at least 1024)'
        }
        $before = Get-P4NativePrivateInventory -Context $Context -Stage "$Stage-before"
        $beforeMap = ConvertTo-P4InventoryMap $before
        $inventoryPath = Join-Path $Context.output_directory "$Stage-inventory-before.json"
        Write-P4SnapshotJson $inventoryPath @(Get-P4Items $beforeMap)
        $result.inventory_path = $inventoryPath
        $result.inventory_sha256 = Get-P4SnapshotHash $inventoryPath
        foreach ($entry in $beforeMap.Values) {
            if ($entry.Type -ceq 'file' -and
                ([bigint]::Parse($entry.MtimeNs) % [bigint]1000000000) -ne 0) {
                $result.fractional_file_mtime_observed = $true
            }
        }
        $apkBefore = Get-P4SnapshotApkIdentity $Context $Stage 'before'
        $apkTransfer = Invoke-P4SnapshotEncoded $Context $Stage 'original-apk' `
            ('cat ' + (ConvertTo-P4ShellWord $apkBefore.Path)) $MaximumApkBytes
        $result.apk_transfer_record_sha256 = Get-P4SnapshotHash `
            (Join-Path $Context.output_directory "$Stage-original-apk.json")
        if ((Get-P4SnapshotHash $apkTransfer) -cne $apkBefore.Hash) {
            throw 'Transferred APK differs from installed APK digest'
        }
        $apkPath = Join-Path $Context.output_directory "$Stage-original.apk"
        [IO.File]::Copy($apkTransfer, $apkPath, $false)
        $result.apk_path = $apkPath
        $result.apk_sha256 = Get-P4SnapshotHash $apkPath
        $result.installed_apk_path = $apkBefore.Path
        $archivePath = Join-Path $Context.output_directory "$Stage-original.tar"
        $roots = @(@('files', 'no_backup', 'shared_prefs', 'databases') |
            Where-Object { $beforeMap.ContainsKey($_) })
        if ($roots.Count -eq 0) {
            $empty = [IO.File]::Open($archivePath, [IO.FileMode]::CreateNew,
                [IO.FileAccess]::Write, [IO.FileShare]::Read)
            try { $empty.SetLength(1024) } finally { $empty.Dispose() }
        } else {
            $words = @($roots | ForEach-Object { ConvertTo-P4ShellWord $_ }) -join ' '
            $script = 'run-as ' + (ConvertTo-P4ShellWord $Context.package) + ' sh -c ' +
                (ConvertTo-P4ShellWord ('tar -cf - ' + $words))
            $archiveTransfer = Invoke-P4SnapshotEncoded $Context $Stage 'original-tar' `
                $script $MaximumArchiveBytes
            $result.archive_transfer_record_sha256 = Get-P4SnapshotHash `
                (Join-Path $Context.output_directory "$Stage-original-tar.json")
            [IO.File]::Copy($archiveTransfer, $archivePath, $false)
        }
        Assert-P4ArchiveInventory $archivePath $before
        $result.archive_path = $archivePath
        $result.archive_sha256 = Get-P4SnapshotHash $archivePath
        $after = Get-P4NativePrivateInventory -Context $Context -Stage "$Stage-after"
        $afterMap = ConvertTo-P4InventoryMap $after
        Assert-P4ExactMap $beforeMap $afterMap
        $afterPath = Join-Path $Context.output_directory "$Stage-inventory-after.json"
        Write-P4SnapshotJson $afterPath @(Get-P4Items $afterMap)
        $result.inventory_after_path = $afterPath
        $result.inventory_after_sha256 = Get-P4SnapshotHash $afterPath
        $apkAfter = Get-P4SnapshotApkIdentity $Context $Stage 'after'
        if ($apkAfter.Path -cne $apkBefore.Path -or $apkAfter.Hash -cne $apkBefore.Hash) {
            throw 'Installed APK changed during snapshot'
        }
        $currentSources = Get-P4SnapshotSources
        foreach ($name in $sources.Keys) {
            if ($sources[$name] -cne $currentSources[$name]) { throw 'Snapshot source changed during capture' }
        }
        Assert-P4OperationActive $Context
        $result.status = 'verified-snapshot'
    } catch {
        $failure = $_
        $result.reason = $_.Exception.Message
    } finally {
        if (-not [IO.File]::Exists($resultPath) -and -not [IO.Directory]::Exists($resultPath)) {
            Write-P4SnapshotJson $resultPath $result
        }
    }
    if ($null -ne $failure) { throw $failure }
    return [pscustomobject]$result
}

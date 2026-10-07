param([string] $OutputDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/p4-device-private-snapshot.ps1')

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Get-NenePixelLabPath ('evidence/145-readonly-snapshot/' +
        (Get-Date -Format 'yyyyMMddTHHmmssfff') + '-' + [guid]::NewGuid().ToString('N')) -StartDirectory $PSScriptRoot
}
if ([IO.File]::Exists($OutputDirectory) -or [IO.Directory]::Exists($OutputDirectory)) {
    throw 'Snapshot validator evidence directory exists'
}
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$script:checks = 0
$script:case = ''
$script:calls = [Collections.Generic.List[string]]::new()
$script:inventoryCalls = 0
$script:apkBytes = [byte[]](0, 10, 13, 255, 73, 0, 1)
$script:apkHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($script:apkBytes)).ToLowerInvariant()
$script:fileBytes = [byte[]](0, 10, 13, 255)
$script:fileHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($script:fileBytes)).ToLowerInvariant()
$script:apkPath = '/data/app/~~ab12/io.github.hideyukimori.nenepixel-xy_1/base.apk'
$script:ns = '1720000000123456789'
$script:repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$script:tarGood = Join-Path $OutputDirectory 'good-fixture.tar'
$script:tarMissing = Join-Path $OutputDirectory 'missing-fixture.tar'

function Check([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw "Snapshot validator: $Message" }
    $script:checks++
}
function Bytes([string] $Text) { return ,([Text.UTF8Encoding]::new($false).GetBytes($Text)) }
function Write-TarFixture([string] $Path, [bool] $WithFile) {
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    $writer = [System.Formats.Tar.TarWriter]::new($stream, [System.Formats.Tar.TarEntryFormat]::Pax, $true)
    try {
        $directory = [System.Formats.Tar.PaxTarEntry]::new([System.Formats.Tar.TarEntryType]::Directory, 'files/')
        $writer.WriteEntry($directory)
        if ($WithFile) {
            $entry = [System.Formats.Tar.PaxTarEntry]::new([System.Formats.Tar.TarEntryType]::RegularFile, 'files/pixel')
            $entry.DataStream = [IO.MemoryStream]::new($script:fileBytes, $false)
            try { $writer.WriteEntry($entry) } finally { $entry.DataStream.Dispose() }
        }
    } finally { $writer.Dispose(); $stream.Dispose() }
}
Write-TarFixture $script:tarGood $true
Write-TarFixture $script:tarMissing $false

function New-Case([string] $Name) {
    $directory = Join-Path $OutputDirectory $Name
    [void][IO.Directory]::CreateDirectory($directory)
    $script:case = $Name
    $script:calls.Clear()
    $script:inventoryCalls = 0
    return @{ repository_root = $script:repository; output_directory = $directory;
        adb_path = (Get-Command pwsh).Source; serial = 'serial-01:5555';
        package = 'io.github.hideyukimori.nenepixel' }
}

# The native observer and encoded transport are mocked at their public boundaries.
# The production canonical inventory map and real tar validator still execute.
function Get-P4NativePrivateInventory {
    param([System.Collections.IDictionary] $Context, [string] $Stage)
    $script:calls.Add("inventory:$Stage")
    $script:inventoryCalls++
    if ($script:case -eq 'empty') { return ,([object[]]@()) }
    if ($script:case -eq 'single-directory') {
        return ,([object[]]@([pscustomobject]@{ Path = 'files'; Type = 'directory' }))
    }
    $mtime = $script:ns
    if ($script:case -eq 'mtime-drift' -and $script:inventoryCalls -eq 2) {
        $mtime = '1720000000123456790'
    }
    $hash = $script:fileHash
    if ($script:case -eq 'inventory-drift' -and $script:inventoryCalls -eq 2) { $hash = 'a' * 64 }
    return ,([object[]]@(
        [pscustomobject]@{ Path = 'files'; Type = 'directory' },
        [pscustomobject]@{ Path = 'files/pixel'; Type = 'file'; Size = [long]4;
            Hash = $hash; MtimeNs = $mtime; LinkCount = 1 }
    ))
}
function Invoke-P4EncodedShellCapture {
    param([string] $AdbPath, [string] $Serial, [string] $Script,
        [int] $TimeoutSeconds, [string] $DestinationPath, [string] $RecordPath,
        [long] $MaximumBytes)
    $leaf = [IO.Path]::GetFileName($DestinationPath)
    $script:calls.Add("capture:$leaf")
    if ([IO.File]::Exists($DestinationPath) -or [IO.File]::Exists($RecordPath)) {
        throw 'Mock output collision'
    }
    $payload = [byte[]]::new(0)
    if ($leaf.EndsWith('-apk-path.bin', [StringComparison]::Ordinal)) {
        Check ($Script -ceq "pm path 'io.github.hideyukimori.nenepixel'") 'package-bound pm path command'
        $path = $script:apkPath
        if ($script:case -eq 'unsafe-path') { $path = '/data/app/../base.apk' }
        if ($script:case -eq 'changed-path' -and $leaf.Contains('-after-')) {
            $path = '/data/app/~~ab12/other/base.apk'
        }
        $line = "package:$path`n"
        if ($script:case -eq 'split') { $line += "package:$path`n" }
        $payload = Bytes $line
    } elseif ($leaf.EndsWith('-apk-hash.bin', [StringComparison]::Ordinal)) {
        $hash = $script:apkHash
        if ($script:case -eq 'changed-hash' -and $leaf.Contains('-after-')) { $hash = 'b' * 64 }
        if ($script:case -eq 'wrong-before-hash' -and $leaf.Contains('-before-')) { $hash = 'b' * 64 }
        $path = $script:apkPath
        if ($script:case -eq 'changed-path' -and $leaf.Contains('-after-')) {
            $path = '/data/app/~~ab12/other/base.apk'
        }
        Check ($Script -ceq ("sha256sum '" + $path + "'")) 'observed-path hash command'
        $payload = Bytes "$hash  $path`n"
    } elseif ($leaf.EndsWith('-original-apk.bin', [StringComparison]::Ordinal)) {
        Check ($Script -ceq ("cat '" + $script:apkPath + "'")) 'observed-path APK capture command'
        $payload = $script:apkBytes
    } elseif ($leaf.EndsWith('-original-tar.bin', [StringComparison]::Ordinal)) {
        $expectedTar = 'run-as ' + (ConvertTo-P4ShellWord 'io.github.hideyukimori.nenepixel') +
            ' sh -c ' + (ConvertTo-P4ShellWord ('tar -cf - ' + (ConvertTo-P4ShellWord 'files')))
        Check ($Script -ceq $expectedTar) 'run-as protected-root tar command'
        $source = if ($script:case -in @('wrong-archive', 'single-directory')) {
            $script:tarMissing
        } else { $script:tarGood }
        $payload = [IO.File]::ReadAllBytes($source)
    } else { throw "Unexpected encoded source: $leaf" }
    if ($MaximumBytes -lt $payload.Length) { throw 'Mock decoded cap exceeded' }
    if ($script:case -eq 'failed-transfer' -and $leaf.EndsWith('-original-tar.bin', [StringComparison]::Ordinal)) {
        [IO.File]::WriteAllBytes($DestinationPath, [byte[]](1,2,3))
        [IO.File]::WriteAllText($RecordPath, '{"status":"failure"}')
        throw 'Synthetic transfer failure'
    }
    [IO.File]::WriteAllBytes($DestinationPath, $payload)
    [IO.File]::WriteAllText($RecordPath, '{"status":"success"}')
    return [pscustomobject]@{ status = 'success' }
}

function Invoke-P4RawAdbCapture {
    $script:calls.Add('raw-capture')
    throw 'Unexpected raw capture in snapshot validator'
}

function Check-SavedInventory($Result, [object[]] $Expected, [string] $Label) {
    $expectedMap = ConvertTo-P4InventoryMap $Expected
    foreach ($item in @(
            @{ Path = $Result.inventory_path; Hash = $Result.inventory_sha256; Name = 'before' },
            @{ Path = $Result.inventory_after_path; Hash = $Result.inventory_after_sha256; Name = 'after' })) {
        $json = [IO.File]::ReadAllText($item.Path)
        $saved = ConvertFrom-Json -InputObject $json -NoEnumerate
        Check ($saved -is [array]) "$Label $($item.Name) is a JSON array"
        Check ($saved.Count -eq $Expected.Count) "$Label $($item.Name) entry count"
        $savedMap = ConvertTo-P4InventoryMap $saved
        Assert-P4ExactMap $expectedMap $savedMap
        Check ($savedMap.Count -eq $expectedMap.Count) "$Label $($item.Name) canonical inventory"
        Check ((Get-P4SnapshotHash $item.Path) -ceq $item.Hash) "$Label $($item.Name) recorded hash"
    }
}

$valid = New-Case 'valid-binary'
$success = New-P4PrivateSnapshot -Context $valid -Stage 'valid-binary' `
    -MaximumArchiveBytes 1048576 -MaximumApkBytes 1024
Check ($success.status -ceq 'verified-snapshot') 'verified result'
Check ($success.schema -ceq 'nene-pixel-device-private-snapshot-v1') 'snapshot schema'
Check ($success.apk_sha256 -ceq $script:apkHash) 'binary APK hash'
Check ($success.fractional_file_mtime_observed -eq $true) 'observed fractional mtime'
Check ($script:inventoryCalls -eq 2) 'both inventories'
Check (([IO.File]::ReadAllBytes($success.apk_path) -join ',') -ceq ($script:apkBytes -join ',')) 'exact APK bytes'
Check ((Get-Content (Join-Path $valid.output_directory 'valid-binary-snapshot.json') -Raw | ConvertFrom-Json).status -ceq 'verified-snapshot') 'retained verified record'
Check-SavedInventory $success @(
    [pscustomobject]@{ Path = 'files'; Type = 'directory' },
    [pscustomobject]@{ Path = 'files/pixel'; Type = 'file'; Size = [long]4;
        Hash = $script:fileHash; MtimeNs = $script:ns; LinkCount = 1 }
) 'valid-binary'

$empty = New-Case 'empty'
$emptyResult = New-P4PrivateSnapshot -Context $empty -Stage 'empty' `
    -MaximumArchiveBytes 1024 -MaximumApkBytes 1024
Check ($emptyResult.status -ceq 'verified-snapshot') 'empty inventory verified'
Check (([IO.FileInfo]::new($emptyResult.archive_path)).Length -eq 1024) 'exact empty tar size'
Check (-not (@($script:calls | Where-Object { $_ -like '*original-tar*' }).Count)) 'no remote tar for empty inventory'
Check ($emptyResult.fractional_file_mtime_observed -eq $false) 'no fabricated fractional precision'
Check-SavedInventory $emptyResult @() 'empty'

$single = New-Case 'single-directory'
$singleResult = New-P4PrivateSnapshot -Context $single -Stage 'single-directory' `
    -MaximumArchiveBytes 1048576 -MaximumApkBytes 1024
Check ($singleResult.status -ceq 'verified-snapshot') 'single directory verified'
Check-SavedInventory $singleResult @([pscustomobject]@{
    Path = 'files'; Type = 'directory'
}) 'single-directory'

foreach ($name in @('split', 'unsafe-path', 'changed-path', 'changed-hash',
        'wrong-before-hash', 'inventory-drift', 'mtime-drift', 'wrong-archive',
        'failed-transfer')) {
    $context = New-Case $name
    $rejected = $false
    try { [void](New-P4PrivateSnapshot -Context $context -Stage $name `
        -MaximumArchiveBytes 1048576 -MaximumApkBytes 1024) } catch { $rejected = $true }
    Check $rejected "$name refusal"
    $record = Get-Content (Join-Path $context.output_directory "$name-snapshot.json") -Raw | ConvertFrom-Json
    Check ($record.status -ceq 'failure' -and -not [string]::IsNullOrWhiteSpace($record.reason)) "$name failure record"
    if ($name -eq 'failed-transfer') {
        Check ([IO.File]::Exists((Join-Path $context.output_directory "$name-original-tar.bin"))) 'partial transfer retained'
        Check ([IO.File]::Exists((Join-Path $context.output_directory "$name-original-tar.json"))) 'failed transfer record retained'
    }
}

$collision = New-Case 'collision'
[IO.File]::WriteAllText((Join-Path $collision.output_directory 'collision-original.apk'), 'occupied')
$rejected = $false
try { [void](New-P4PrivateSnapshot -Context $collision -Stage 'collision' `
    -MaximumArchiveBytes 1048576 -MaximumApkBytes 1024) } catch { $rejected = $true }
Check $rejected 'fixed output collision refused'
Check ($script:calls.Count -eq 0) 'collision refused before command'
Check ((Get-Content (Join-Path $collision.output_directory 'collision-snapshot.json') -Raw |
    ConvertFrom-Json).status -ceq 'failure') 'collision records failure'
Check ((Get-Content (Join-Path $collision.output_directory 'collision-original.apk') -Raw) -ceq 'occupied') 'collision leaves occupied output intact'

foreach ($batch in @('before-stat-17.bin', 'after-hash-19.json.encoded.json.command.json')) {
    $context = New-Case "batch-collision-$batch"
    $stage = 'batch-collision'
    $occupied = Join-Path $context.output_directory "BaTcH-cOlLiSiOn-$batch"
    [IO.File]::WriteAllBytes($occupied, [byte[]](0, 10, 13, 255))
    $occupiedHash = Get-P4SnapshotHash $occupied
    Check (-not (@(Get-P4SnapshotFixedPaths $context $stage).Contains($occupied))) "$batch is outside fixed snapshot outputs"
    $reason = $null
    try { [void](New-P4PrivateSnapshot -Context $context -Stage $stage `
        -MaximumArchiveBytes 1048576 -MaximumApkBytes 1024) }
    catch { $reason = $_.Exception.Message }
    Check ($null -ne $reason -and $reason.StartsWith('Snapshot stage is occupied:')) "$batch mixed-case stage collision refused"
    Check ($script:calls.Count -eq 0 -and $script:inventoryCalls -eq 0) "$batch collision has zero raw/native calls"
    Check ((Get-P4SnapshotHash $occupied) -ceq $occupiedHash) "$batch collision bytes preserved"
    $recordPath = Join-Path $context.output_directory "$stage-snapshot.json"
    $record = Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
    Check ($record.status -ceq 'failure' -and $record.reason -ceq $reason) "$batch collision retains failure record"
    $outputs = @([IO.Directory]::EnumerateFileSystemEntries($context.output_directory) | Sort-Object)
    $expected = @($occupied, $recordPath) | Sort-Object
    Check (($outputs -join "`n") -ceq ($expected -join "`n")) "$batch collision creates no capture/inventory outputs"
}

$invalidCap = New-Case 'invalid-cap'
$rejected = $false
try { [void](New-P4PrivateSnapshot -Context $invalidCap -Stage 'invalid-cap' `
    -MaximumArchiveBytes 1023 -MaximumApkBytes 1024) } catch { $rejected = $true }
Check $rejected 'invalid archive cap refused'
Check ($script:calls.Count -eq 0) 'invalid cap refused before command'
Check ((Get-Content (Join-Path $invalidCap.output_directory 'invalid-cap-snapshot.json') -Raw |
    ConvertFrom-Json).status -ceq 'failure') 'invalid cap records failure'

$summary = [ordered]@{ schema = 'nene-pixel-p4-snapshot-host-validator-v1'; checks = $script:checks;
    status = 'pass'; source_sha256 = Get-P4SnapshotSources }
Write-NewInvocationFile (Join-Path $OutputDirectory 'validation.json') ($summary | ConvertTo-Json -Depth 6)
Write-Output "PASS: $($script:checks) snapshot composition checks."

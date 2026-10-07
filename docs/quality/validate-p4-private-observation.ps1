Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/p4-device-private-observation.ps1')
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')

$script:checks = 0
function Check([bool] $condition, [string] $label) {
    $script:checks++
    if (-not $condition) { throw "FAIL: $label" }
}
function Reject([scriptblock] $action, [string] $label) {
    $failed = $false
    try { & $action | Out-Null } catch { $failed = $true }
    Check $failed $label
}
function Paths([string[]] $names) {
    $bytes = [System.Collections.Generic.List[byte]]::new()
    foreach ($name in $names) {
        $bytes.AddRange([System.Text.Encoding]::UTF8.GetBytes($name))
        $bytes.Add(0)
    }
    return ,$bytes.ToArray()
}
function Meta([string] $path, [string] $mode, [string] $size = '0', [string] $when = '2024-07-03 18:46:40.123456789 +0900', [string] $epoch = '1720000000', [string] $links = '1') {
    return "$path|$mode|$links|$size|$epoch|$when"
}
function Hash([byte[]] $bytes) { return [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant() }
function WriteTar([string] $path, [object[]] $entries) {
    $stream = [System.IO.File]::Open($path, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write)
    try {
        $writer = [System.Formats.Tar.TarWriter]::new($stream, [System.Formats.Tar.TarEntryFormat]::Ustar, $true)
        try {
            foreach ($spec in $entries) {
                $entry = [System.Formats.Tar.UstarTarEntry]::new($spec.Kind, $spec.Name)
                if ($spec.Kind -eq [System.Formats.Tar.TarEntryType]::SymbolicLink -or
                    $spec.Kind -eq [System.Formats.Tar.TarEntryType]::HardLink) { $entry.LinkName = 'files/blob' }
                if ($null -ne $spec.Bytes) { $entry.DataStream = [System.IO.MemoryStream]::new([byte[]] $spec.Bytes, $false) }
                $writer.WriteEntry($entry)
            }
        } finally { $writer.Dispose() }
    } finally { $stream.Dispose() }
}
function WriteExtendedTar([string] $path, [object[]] $entries, [System.Formats.Tar.TarEntryFormat] $format) {
    $stream = [System.IO.File]::Open($path, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write)
    try {
        $writer = [System.Formats.Tar.TarWriter]::new($stream, $format, $true)
        try {
            foreach ($spec in $entries) {
                if ($format -eq [System.Formats.Tar.TarEntryFormat]::Gnu) {
                    $entry = [System.Formats.Tar.GnuTarEntry]::new($spec.Kind, $spec.Name)
                } else {
                    $entry = [System.Formats.Tar.PaxTarEntry]::new($spec.Kind, $spec.Name)
                }
                if ($null -ne $spec.Bytes) { $entry.DataStream = [System.IO.MemoryStream]::new([byte[]] $spec.Bytes, $false) }
                $writer.WriteEntry($entry)
            }
        } finally { $writer.Dispose() }
    } finally { $stream.Dispose() }
}
function Spec([string] $name, [System.Formats.Tar.TarEntryType] $kind, [byte[]] $bytes = $null) {
    return [pscustomobject]@{ Name = $name; Kind = $kind; Bytes = $bytes }
}

$stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssfff') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$dir = Get-NenePixelLabPath "evidence/lab145-extended-tar/$stamp" -StartDirectory $PSScriptRoot
[void] [System.IO.Directory]::CreateDirectory($dir)
$binary = [byte[]] (0, 255, 1, 0, 128)
$empty = [byte[]]::new(0)
$names = @('files', 'files/zero', 'files/blob', 'no_backup')
$paths = Paths $names
$meta = @((Meta 'files' '41ed'), (Meta 'files/zero' '81a4'), (Meta 'files/blob' '81a4' '5'), (Meta 'no_backup' '41ed'))
$sha = @("$(Hash $empty)  files/zero", "$(Hash $binary)  files/blob")
$inventory = ConvertFrom-P4NativeInventory $paths $meta $sha
Check ($inventory.Count -eq 4) 'exact native coverage'
Check (($inventory | Where-Object Path -EQ 'files/zero').Size -eq 0) 'empty regular file'
Check (($inventory | Where-Object Path -EQ 'files/blob').MtimeNs -ceq '1720000000123456789') 'nine fractional digits'
$otherZone = @($meta[0], $meta[1], (Meta 'files/blob' '81a4' '5' -when '2024-07-03 04:46:40.123456789 -0500'), $meta[3])
$sameInstant = ConvertFrom-P4NativeInventory $paths $otherZone $sha
Check (($sameInstant | Where-Object Path -EQ 'files/blob').MtimeNs -ceq '1720000000123456789') 'timezone equivalent instant'
$tarEntries = @(
    (Spec 'files/' Directory), (Spec 'files/zero' RegularFile $empty),
    (Spec 'files/blob' RegularFile $binary), (Spec 'no_backup/' Directory)
)
$valid = Join-Path $dir 'valid.tar'
WriteTar $valid $tarEntries
Assert-P4ArchiveInventory $valid $inventory
Check $true 'streamed valid tar'

$longNames = @('files', ('a' * 40), ('b' * 40), ('c' * 40))
$longPath = $longNames -join '/'
$longFile = "$longPath/blob"
$longDirs = @("$($longNames[0])/$($longNames[1])", "$($longNames[0])/$($longNames[1])/$($longNames[2])", $longPath)
$extendedNames = @('files', 'files/zero', 'files/blob') + $longDirs + @($longFile, 'no_backup')
$extendedPaths = Paths $extendedNames
$extendedMeta = @((Meta 'files' '41ed'), (Meta 'files/zero' '81a4'), (Meta 'files/blob' '81a4' '5'))
foreach ($path in $longDirs) { $extendedMeta += (Meta $path '41ed') }
$extendedMeta += (Meta $longFile '81a4' '5'), (Meta 'no_backup' '41ed')
$extendedSha = @("$(Hash $empty)  files/zero", "$(Hash $binary)  files/blob", "$(Hash $binary)  $longFile")
$extendedInventory = ConvertFrom-P4NativeInventory $extendedPaths $extendedMeta $extendedSha
$extendedEntries = @((Spec 'files/' Directory), (Spec 'files/zero' RegularFile $empty), (Spec 'files/blob' RegularFile $binary))
foreach ($path in $longDirs) { $extendedEntries += (Spec "$path/" Directory) }
$extendedEntries += (Spec $longFile RegularFile $binary), (Spec 'no_backup/' Directory)
function CheckExtendedTar([string] $name, [System.Formats.Tar.TarEntryFormat] $format, [int] $headerFlag) {
    $path = Join-Path $dir "$name.tar"
    WriteExtendedTar $path $extendedEntries $format
    $rawExtended = [System.IO.File]::ReadAllBytes($path)
    $hasExtensionHeader = $false
    for ($offset = 0; $offset -lt $rawExtended.Length; $offset += 512) {
        if ($rawExtended[$offset + 156] -eq $headerFlag) { $hasExtensionHeader = $true; break }
    }
    Check $hasExtensionHeader "$name extension header present"
    Assert-P4ArchiveInventory $path $extendedInventory
    Check $true "$name long-path archive accepted"
}
CheckExtendedTar 'gnu-longname' ([System.Formats.Tar.TarEntryFormat]::Gnu) 0x4c
CheckExtendedTar 'pax-extended' ([System.Formats.Tar.TarEntryFormat]::Pax) 0x78

Reject { ConvertFrom-P4NulPaths ([byte[]] (102, 105, 108, 101, 115)) } 'missing terminal NUL'
Reject { ConvertFrom-P4NulPaths ([byte[]] (0)) } 'empty component'
Reject { ConvertFrom-P4NulPaths ([byte[]] (0xC3, 0x28, 0)) } 'invalid UTF-8'
Reject { ConvertFrom-P4NulPaths (Paths @('files', 'files')) } 'duplicate path'
Reject { ConvertFrom-P4NulPaths (Paths @('Files')) } 'case-sensitive root'
Reject { ConvertFrom-P4NulPaths (Paths @('files/../bad')) } 'unsafe path'
Reject { ConvertFrom-P4NulPaths (Paths @('files/zero ')) } 'no trimming'
Reject { ConvertFrom-P4NativeInventory $paths $meta[0..2] $sha } 'missing metadata'
Reject { ConvertFrom-P4NativeInventory $paths ($meta + $meta[0]) $sha } 'duplicate metadata'
Reject { ConvertFrom-P4NativeInventory $paths @($meta[0] + '|extra', $meta[1], $meta[2], $meta[3]) $sha } 'extra metadata field'
Reject { ConvertFrom-P4NativeInventory $paths $meta $sha[0..0] } 'missing hash'
Reject { ConvertFrom-P4NativeInventory $paths $meta ($sha + $sha[0]) } 'duplicate hash'
Reject { ConvertFrom-P4NativeInventory $paths $meta (@($sha[0], ('A' * 64 + '  files/blob'))) } 'uppercase hash'
Reject { ConvertFrom-P4NativeInventory $paths $meta (@($sha[0], ('a' * 64 + '  no_backup'))) } 'directory hash'
Reject { ConvertFrom-P4NativeInventory $paths @($meta[0], $meta[1], (Meta 'files/blob' 'a1ff' '5'), $meta[3]) $sha } 'symbolic link mode'
Reject { ConvertFrom-P4NativeInventory $paths @($meta[0], $meta[1], (Meta 'files/blob' '81a4' '5' -links '2'), $meta[3]) $sha } 'hard-linked regular file'
Reject { ConvertFrom-P4NativeInventory $paths @($meta[0], $meta[1], (Meta 'files/blob' '81a4' '5' -epoch '1720000001'), $meta[3]) $sha } 'calendar/epoch mismatch'
Reject { ConvertFrom-P4NativeInventory $paths @($meta[0], $meta[1], (Meta 'files/blob' '81a4' '5' -when '2024-07-03 00:46:40.123456789 +0000'), $meta[3]) $sha } 'timezone mismatch'
Reject { ConvertFrom-P4NativeInventory $paths @($meta[0], $meta[1], (Meta 'files/blob' '81a4' '5' -when '2024-07-03 18:46:40.12345678 +0900'), $meta[3]) $sha } 'fraction precision'
Reject { ConvertFrom-P4NativeInventory (Paths @('files/blob')) @((Meta 'files/blob' '81a4' '5')) @($sha[1]) } 'missing parent/root'

$cases = @(
    @{ Name = 'missing'; Entries = @($tarEntries[0], $tarEntries[1], $tarEntries[3]) },
    @{ Name = 'extra'; Entries = @($tarEntries + (Spec 'files/other' RegularFile $empty)) },
    @{ Name = 'duplicate'; Entries = @($tarEntries + $tarEntries[1]) },
    @{ Name = 'link'; Entries = @($tarEntries[0], (Spec 'files/zero' SymbolicLink), $tarEntries[2], $tarEntries[3]) },
    @{ Name = 'hard-link'; Entries = @($tarEntries[0], (Spec 'files/zero' HardLink), $tarEntries[2], $tarEntries[3]) },
    @{ Name = 'wrong-bytes'; Entries = @($tarEntries[0], $tarEntries[1], (Spec 'files/blob' RegularFile ([byte[]] (0,255,2,0,128))), $tarEntries[3]) }
)
foreach ($case in $cases) {
    $path = Join-Path $dir "$($case.Name).tar"
    WriteTar $path $case.Entries
    Reject { Assert-P4ArchiveInventory $path $inventory } "tar $($case.Name)"
}
$raw = [System.IO.File]::ReadAllBytes($valid)
function Corrupt([string] $name, [byte[]] $bytes) {
    $path = Join-Path $dir "$name.tar"
    [System.IO.File]::WriteAllBytes($path, $bytes)
    Reject { Assert-P4ArchiveInventory $path $inventory } "tar $name"
}
$badHeader = [byte[]] $raw.Clone(); $badHeader[0] = $badHeader[0] -bxor 1
Corrupt 'header-checksum' $badHeader
$badData = [byte[]] $raw.Clone(); $badData[1536] = $badData[1536] -bxor 1
Corrupt 'data-hash' $badData
$truncated = [byte[]]::new($raw.Length - 512); [Array]::Copy($raw, $truncated, $truncated.Length)
Corrupt 'truncated' $truncated
$badTail = [byte[]] $raw.Clone(); $badTail[$badTail.Length - 1] = 1
Corrupt 'nonzero-tail' $badTail
$badMarker = [byte[]] $raw.Clone(); $badMarker[$badMarker.Length - 1024] = 1
Corrupt 'nonzero-end-block' $badMarker

$report = "PASS $script:checks checks`nGNU/PAX archives include >100-byte inventory path $longFile`n"
$report += "GNU/PAX extension-header assertions and parser acceptance passed."
[System.IO.File]::WriteAllText((Join-Path $dir 'validator.log'), $report + "`n")
$report

# Host-only observation contract for ADR 0035. No device command or extraction lives here.
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'p4-device-private-preservation.ps1')

function ConvertFrom-P4NulPaths([byte[]] $Bytes) {
    if ($null -eq $Bytes) { throw 'Path bytes are null' }
    $decoder = [System.Text.UTF8Encoding]::new($false, $true)
    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $paths = [System.Collections.Generic.List[string]]::new()
    $begin = 0
    for ($i = 0; $i -lt $Bytes.Length; $i++) {
        if ($Bytes[$i] -ne 0) { continue }
        if ($i -eq $begin) { throw 'Empty NUL-delimited path' }
        $path = $decoder.GetString($Bytes, $begin, $i - $begin)
        Assert-P4Path $path
        if (-not $seen.Add($path)) { throw "Duplicate path: $path" }
        $paths.Add($path)
        $begin = $i + 1
    }
    if ($begin -ne $Bytes.Length) { throw 'Unterminated NUL-delimited path' }
    return ,$paths.ToArray()
}

function ConvertFrom-P4NativeInventory([byte[]] $PathBytes, [string[]] $MetadataLines, [string[]] $Sha256Lines) {
    $paths = ConvertFrom-P4NulPaths $PathBytes
    if ($null -eq $MetadataLines -or $null -eq $Sha256Lines) { throw 'Native record list is null' }
    $discovered = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($path in $paths) { [void] $discovered.Add($path) }
    $meta = New-P4Map
    foreach ($line in $MetadataLines) {
        if ($null -eq $line) { throw 'Null metadata line' }
        $fields = $line.Split('|')
        if ($fields.Length -ne 6) { throw 'Metadata must have exactly six fields' }
        $path = $fields[0]
        Assert-P4Path $path
        if (-not $discovered.Contains($path) -or $meta.ContainsKey($path)) { throw "Unlisted or duplicate metadata: $path" }
        if ($fields[1] -cnotmatch '^[0-9a-fA-F]+$') { throw "Invalid mode: $path" }
        try { $mode = [Convert]::ToUInt32($fields[1], 16) } catch { throw "Invalid mode: $path" }
        $kind = $mode -band 0xF000
        if ($kind -ne 0x4000 -and $kind -ne 0x8000) { throw "Unsupported native mode: $path" }
        if ($fields[2] -cnotmatch '^(0|[1-9][0-9]*)$' -or $fields[3] -cnotmatch '^(0|[1-9][0-9]*)$' -or
            $fields[4] -cnotmatch '^(0|[1-9][0-9]*)$') { throw "Invalid integral metadata: $path" }
        try {
            $links = [long]::Parse($fields[2], [System.Globalization.CultureInfo]::InvariantCulture)
            $size = [long]::Parse($fields[3], [System.Globalization.CultureInfo]::InvariantCulture)
            $seconds = [long]::Parse($fields[4], [System.Globalization.CultureInfo]::InvariantCulture)
        } catch { throw "Out-of-range integral metadata: $path" }
        if ($fields[5] -cnotmatch '^(?<y>[0-9]{4})-(?<mo>[0-9]{2})-(?<d>[0-9]{2}) (?<h>[0-9]{2}):(?<mi>[0-9]{2}):(?<s>[0-9]{2})\.(?<frac>[0-9]{9}) (?<sign>[+-])(?<oh>[0-9]{2})(?<om>[0-9]{2})$') {
            throw "Invalid fractional timestamp: $path"
        }
        $date = $Matches
        try {
            $offsetMinutes = ([int] $date.oh * 60 + [int] $date.om) * $(if ($date.sign -ceq '-') { -1 } else { 1 })
            if ([int] $date.om -ge 60) { throw 'Invalid offset minute' }
            $calendar = [DateTimeOffset]::new([int] $date.y, [int] $date.mo, [int] $date.d,
                [int] $date.h, [int] $date.mi, [int] $date.s, [TimeSpan]::FromMinutes($offsetMinutes))
        } catch { throw "Invalid calendar timestamp: $path" }
        if ($calendar.ToUnixTimeSeconds() -ne $seconds) { throw "Epoch/calendar mismatch: $path" }
        if ($kind -eq 0x4000) {
            $meta.Add($path, [pscustomobject]@{ Path = $path; Type = 'directory' })
        } else {
            if ($links -ne 1) { throw "Non-single-link file: $path" }
            $ns = ([System.Numerics.BigInteger] $seconds * [System.Numerics.BigInteger] 1000000000) +
                [System.Numerics.BigInteger]::Parse($date.frac, [System.Globalization.CultureInfo]::InvariantCulture)
            $meta.Add($path, [pscustomobject]@{ Path = $path; Type = 'file'; Size = $size;
                MtimeNs = $ns.ToString([System.Globalization.CultureInfo]::InvariantCulture); LinkCount = 1 })
        }
    }
    if ($meta.Count -ne $discovered.Count) { throw 'Metadata does not cover discovered paths' }
    $hashes = New-P4Map
    foreach ($line in $Sha256Lines) {
        if ($null -eq $line -or $line -cnotmatch '^([0-9a-f]{64})  (.+)$') { throw 'Invalid SHA-256 line' }
        $hash = $Matches[1]; $path = $Matches[2]
        Assert-P4Path $path
        if (-not $meta.ContainsKey($path) -or $meta[$path].Type -cne 'file' -or $hashes.ContainsKey($path)) {
            throw "Unlisted, non-file or duplicate SHA-256: $path"
        }
        $hashes.Add($path, $hash)
    }
    $items = [System.Collections.Generic.List[object]]::new()
    foreach ($path in $paths) {
        $entry = $meta[$path]
        if ($entry.Type -ceq 'file') {
            if (-not $hashes.ContainsKey($path)) { throw "Missing SHA-256: $path" }
            $entry | Add-Member -NotePropertyName Hash -NotePropertyValue $hashes[$path]
        }
        $items.Add($entry)
    }
    [void] (ConvertTo-P4InventoryMap $items.ToArray())
    return ,$items.ToArray()
}

function Assert-P4ArchiveInventory([string] $ArchivePath, [object[]] $Inventory) {
    $expected = ConvertTo-P4InventoryMap $Inventory
    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $stream = [System.IO.File]::Open($ArchivePath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::Read)
    try {
        if ($stream.Length % 512 -ne 0 -or $stream.Length -lt 1024) { throw 'Invalid tar block length' }
        $reader = [System.Formats.Tar.TarReader]::new($stream, $true)
        try {
            $endOffset = [long] 0
            while ($null -ne ($entry = $reader.GetNextEntry())) {
                $kind = $entry.EntryType
                if ($kind -ne [System.Formats.Tar.TarEntryType]::Directory -and
                    $kind -ne [System.Formats.Tar.TarEntryType]::RegularFile -and
                    $kind -ne [System.Formats.Tar.TarEntryType]::V7RegularFile) { throw "Unsupported tar entry: $($entry.Name)" }
                if ($entry.PSObject.Properties.Name -contains 'ExtendedAttributes' -and $entry.ExtendedAttributes) {
                    foreach ($key in $entry.ExtendedAttributes.Keys) {
                        if ($key.StartsWith('GNU.sparse.', [System.StringComparison]::Ordinal) -or
                            $key.StartsWith('SCHILY.realsize', [System.StringComparison]::Ordinal)) { throw 'Sparse tar entry' }
                    }
                }
                $name = $entry.Name
                if ($kind -eq [System.Formats.Tar.TarEntryType]::Directory -and $name.EndsWith('/')) {
                    $name = $name.Substring(0, $name.Length - 1)
                }
                Assert-P4Path $name
                if (-not $expected.ContainsKey($name) -or -not $seen.Add($name)) { throw "Extra or duplicate tar entry: $name" }
                $wanted = $expected[$name]
                if ($kind -eq [System.Formats.Tar.TarEntryType]::Directory) {
                    if ($wanted.Type -cne 'directory' -or $entry.Length -ne 0) { throw "Changed directory: $name" }
                } else {
                    if ($entry.Name.EndsWith('/') -or $wanted.Type -cne 'file' -or $entry.Length -ne $wanted.Size) {
                        throw "Changed tar file: $name"
                    }
                    $sha = [System.Security.Cryptography.IncrementalHash]::CreateHash(
                        [System.Security.Cryptography.HashAlgorithmName]::SHA256)
                    try {
                        $buffer = [byte[]]::new(65536)
                        $readTotal = [long] 0
                        if ($null -ne $entry.DataStream) {
                            while (($n = $entry.DataStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                                $readTotal += $n
                                if ($readTotal -gt $entry.Length) { throw "Oversize tar payload: $name" }
                                $sha.AppendData($buffer, 0, $n)
                            }
                        }
                        if ($readTotal -ne $entry.Length -or
                            [Convert]::ToHexString($sha.GetHashAndReset()).ToLowerInvariant() -cne $wanted.Hash) {
                            throw "Tar payload mismatch: $name"
                        }
                    } finally { $sha.Dispose() }
                }
                $endOffset = [long] ($stream.Position + ((512 - ($stream.Position % 512)) % 512))
            }
            if ($seen.Count -ne $expected.Count) { throw 'Tar inventory incomplete' }
            if ($endOffset -gt $stream.Length - 1024) { throw 'Missing tar end marker' }
            $stream.Position = $endOffset
            $tail = [byte[]]::new(65536)
            while (($n = $stream.Read($tail, 0, $tail.Length)) -gt 0) {
                for ($i = 0; $i -lt $n; $i++) { if ($tail[$i] -ne 0) { throw 'Nonzero tar end marker or tail' } }
            }
        } finally { $reader.Dispose() }
    } finally { $stream.Dispose() }
}

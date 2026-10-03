Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../baseline-profile-evidence.ps1')

# Shared read-only boundary for the slot chain and phase frame baseline reader.
$script:P4CaptureSealSchema = 'nene-pixel-p4-capture-seal-v1'

function Resolve-P4SealedFilePath {
    param($Seal, [string]$SlotDirectory, [string]$RelativePath)
    if ([string]::IsNullOrWhiteSpace($RelativePath) -or
        $RelativePath -cmatch '[\\:\x00-\x1f]' -or
        @($RelativePath.Split('/') | Where-Object { $_ -ceq '' -or $_ -cin @('.', '..') -or $_ -match '[ .]$' }).Count -gt 0) {
        throw "Invalid sealed relative path: $RelativePath"
    }
    $base = $SlotDirectory
    $relative = $RelativePath
    if ($RelativePath.StartsWith('external/', [StringComparison]::Ordinal)) {
        $parts = $RelativePath.Split('/', 3)
        if ($parts.Count -ne 3 -or [string]::IsNullOrWhiteSpace($parts[2])) {
            throw "Malformed sealed external path: $RelativePath"
        }
        if ($null -eq $Seal -or -not $Seal.Contains('external_directories')) {
            throw 'The capture seal does not declare its external directories.'
        }
        $records = @(@($Seal.external_directories) | Where-Object { [string]$_.name -ceq $parts[1] })
        if ($records.Count -ne 1) { throw "Sealed external directory is undeclared: $($parts[1])" }
        $base = [string]$records[0].path
        $relative = $parts[2]
    }
    $root = [IO.Path]::GetFullPath($base)
    $path = [IO.Path]::GetFullPath((Join-Path $root ($relative.Replace('/', [IO.Path]::DirectorySeparatorChar))))
    $prefix = $root.TrimEnd([char]'\', [char]'/') + [IO.Path]::DirectorySeparatorChar
    if (-not $path.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Sealed file escaped its declared root: $RelativePath"
    }
    return $path
}

function Assert-P4SealPathNotLinked {
    param([Parameter(Mandatory)][string]$Path)
    $current = [IO.Path]::GetFullPath($Path)
    while ($null -ne $current) {
        if (Test-Path -LiteralPath $current) {
            $item = Get-Item -LiteralPath $current -Force
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "A sealed evidence path is a link: $current"
            }
        }
        $parent = [IO.Directory]::GetParent($current)
        $current = if ($null -ne $parent) { $parent.FullName } else { $null }
    }
}

function Read-P4VerifiedCaptureSeal {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$SlotDirectory,
        [Parameter(Mandatory)][string]$SlotId,
        [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{64}$')][string]$ExpectedSha256
    )
    $root = [IO.Path]::GetFullPath($SlotDirectory)
    $sealPath = Join-Path $root 'capture-seal.json'
    Assert-P4SealPathNotLinked $sealPath
    if (-not (Test-Path -LiteralPath $sealPath -PathType Leaf) -or
        (Get-FileSha256 $sealPath) -cne $ExpectedSha256) {
        throw "Completed chain drifted: capture seal of $SlotId"
    }
    $seal = Get-Content -LiteralPath $sealPath -Raw | ConvertFrom-Json -AsHashtable -NoEnumerate
    if ($seal -isnot [System.Collections.IDictionary] -or
        -not $seal.Contains('schema') -or -not $seal.Contains('slot_id') -or
        $seal.schema -isnot [string] -or $seal.slot_id -isnot [string] -or
        $seal.schema -cne $script:P4CaptureSealSchema -or $seal.slot_id -cne $SlotId -or
        -not $seal.Contains('files') -or $seal.files -isnot [System.Collections.IList] -or
        $seal.files.Count -eq 0 -or -not $seal.Contains('external_directories') -or
        $seal.external_directories -isnot [System.Collections.IList]) {
        throw "Completed chain drifted: capture seal identity or inventory of $SlotId"
    }
    $externalNames = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($external in $seal.external_directories) {
        if ($external -isnot [System.Collections.IDictionary] -or
            -not $external.Contains('name') -or -not $external.Contains('path') -or
            $external.name -isnot [string] -or $external.path -isnot [string] -or
            $external.name -cnotmatch '^[a-z][a-z0-9-]*$' -or
            -not $externalNames.Add($external.name) -or -not [IO.Path]::IsPathFullyQualified($external.path) -or
            -not (Test-Path -LiteralPath $external.path -PathType Container)) {
            throw "Completed chain drifted: external evidence root of $SlotId"
        }
        Assert-P4SealPathNotLinked $external.path
    }
    $paths = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $resolvedPaths = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($file in $seal.files) {
        if ($file -isnot [System.Collections.IDictionary] -or
            -not $file.Contains('relative_path') -or -not $file.Contains('byte_count') -or
            -not $file.Contains('sha256') -or $file.relative_path -isnot [string] -or
            $file.sha256 -isnot [string] -or $file.sha256 -cnotmatch '^[0-9a-f]{64}$' -or
            -not $paths.Add($file.relative_path)) {
            throw "Completed chain drifted: malformed or duplicate sealed file of $SlotId"
        }
        $size = [long]0
        if (($file.byte_count -isnot [byte] -and $file.byte_count -isnot [int16] -and
                $file.byte_count -isnot [int32] -and $file.byte_count -isnot [int64] -and
                $file.byte_count -isnot [uint16] -and $file.byte_count -isnot [uint32] -and
                $file.byte_count -isnot [uint64]) -or
            -not [long]::TryParse([string]$file.byte_count, [Globalization.NumberStyles]::None,
                [Globalization.CultureInfo]::InvariantCulture, [ref]$size)) {
            throw "Completed chain drifted: invalid sealed byte count of $SlotId"
        }
        $sealedPath = Resolve-P4SealedFilePath -Seal $seal -SlotDirectory $root -RelativePath $file.relative_path
        if (-not $resolvedPaths.Add($sealedPath)) { throw "Completed chain drifted: aliased sealed file of $SlotId" }
        Assert-P4SealPathNotLinked $sealedPath
        if (-not (Test-Path -LiteralPath $sealedPath -PathType Leaf)) {
            throw "Completed chain drifted: sealed file is missing ($SlotId/$($file.relative_path))"
        }
        $item = Get-Item -LiteralPath $sealedPath -Force
        if ($item.Length -ne $size -or (Get-FileSha256 $sealedPath) -cne $file.sha256) {
            throw "Completed chain drifted: sealed file changed ($SlotId/$($file.relative_path))"
        }
    }
    return $seal
}

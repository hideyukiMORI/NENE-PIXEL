# Resolves the NENE-PIXEL-LAB folder that holds development clones, worktrees and evidence.
# Dot-source this file. Resolution order: NENE_PIXEL_LAB, then the nearest ancestor holding the
# `.nene-pixel-lab` marker, then the `NENE-PIXEL-LAB` sibling of the nearest ancestor holding `.git`.
# Whichever is chosen must hold the `.nene-pixel-lab` marker file.

function Get-NenePixelLabRoot {
    param([string]$StartDirectory = $PSScriptRoot)
    $hint = 'Set NENE_PIXEL_LAB to the lab folder, or place NENE-PIXEL-LAB next to the repository and create the .nene-pixel-lab marker file in it.'
    $root = $null
    $fromEnvironment = [Environment]::GetEnvironmentVariable('NENE_PIXEL_LAB')
    if (-not [string]::IsNullOrWhiteSpace($fromEnvironment)) {
        $root = [IO.Path]::GetFullPath($fromEnvironment)
    } elseif (-not [string]::IsNullOrWhiteSpace($StartDirectory)) {
        $start = [IO.Path]::GetFullPath($StartDirectory)
        for ($directory = [IO.DirectoryInfo]::new($start); $null -ne $directory; $directory = $directory.Parent) {
            if (Test-Path -LiteralPath (Join-Path $directory.FullName '.nene-pixel-lab') -PathType Leaf) {
                $root = $directory.FullName
                break
            }
        }
        if ($null -eq $root) {
            for ($directory = [IO.DirectoryInfo]::new($start); $null -ne $directory; $directory = $directory.Parent) {
                if ((Test-Path -LiteralPath (Join-Path $directory.FullName '.git')) -and $null -ne $directory.Parent) {
                    $root = Join-Path $directory.Parent.FullName 'NENE-PIXEL-LAB'
                    break
                }
            }
        }
    }
    if ($null -eq $root -or -not (Test-Path -LiteralPath (Join-Path $root '.nene-pixel-lab') -PathType Leaf)) {
        throw "The NENE-PIXEL-LAB folder with its .nene-pixel-lab marker was not found. $hint"
    }
    $root = $root.Replace('\', '/')
    if ($root.Length -gt 3) { $root = $root.TrimEnd('/') }
    return $root
}

function Get-NenePixelLabPath {
    param([Parameter(Mandatory)][string]$RelativePath, [string]$StartDirectory = $PSScriptRoot)
    if ([IO.Path]::IsPathRooted($RelativePath) -or $RelativePath -match '^[A-Za-z]:') {
        throw "The lab path must be relative to the lab folder: $RelativePath"
    }
    if ($RelativePath.Contains('..')) { throw "The lab path must not contain '..': $RelativePath" }
    $root = Get-NenePixelLabRoot -StartDirectory $StartDirectory
    return "$root/$($RelativePath.Replace('\', '/'))"
}

function Assert-NenePixelLabPathHasNoLink {
    param([Parameter(Mandatory)][string]$Path)
    for ($directory = [IO.DirectoryInfo]::new([IO.Path]::GetFullPath($Path));
        $null -ne $directory; $directory = $directory.Parent) {
        if (Test-Path -LiteralPath $directory.FullName) {
            $item = Get-Item -LiteralPath $directory.FullName -Force
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw "Temporary lab path contains a link: $($directory.FullName)"
            }
        }
    }
}

function New-NenePixelLabTemporaryDirectory {
    param(
        [Parameter(Mandatory)][ValidatePattern('^[a-z][a-z0-9-]{0,47}$')][string]$Prefix,
        [string]$StartDirectory = $PSScriptRoot
    )
    $labRoot = [IO.Path]::GetFullPath((Get-NenePixelLabRoot -StartDirectory $StartDirectory))
    $parent = Join-Path $labRoot 'outputs/temporary'
    $name = "$Prefix-$([guid]::NewGuid().ToString('N'))"
    $path = [IO.Path]::GetFullPath((Join-Path $parent $name))
    Assert-NenePixelLabPathHasNoLink $path
    [IO.Directory]::CreateDirectory($parent) | Out-Null
    New-Item -ItemType Directory -Path $path -ErrorAction Stop | Out-Null
    # Keep the original authority for cleanup even if a resolver test changes NENE_PIXEL_LAB.
    return [pscustomobject]@{ LabRoot = $labRoot; Name = $name; Path = $path }
}

function Remove-NenePixelLabTemporaryDirectory {
    param([Parameter(Mandatory)][psobject]$Directory)
    $labRoot = [IO.Path]::GetFullPath([string]$Directory.LabRoot)
    $name = [string]$Directory.Name
    if ($name -cnotmatch '^[a-z][a-z0-9-]{0,47}-[a-f0-9]{32}$') {
        throw 'Temporary lab directory name is not an allocated identity.'
    }
    $path = [IO.Path]::GetFullPath([string]$Directory.Path)
    $expected = [IO.Path]::GetFullPath((Join-Path (Join-Path $labRoot 'outputs/temporary') $name))
    if ($path -cne $expected -or
        -not (Test-Path -LiteralPath (Join-Path $labRoot '.nene-pixel-lab') -PathType Leaf)) {
        throw 'Temporary lab cleanup target differs from its allocated directory.'
    }
    Assert-NenePixelLabPathHasNoLink $path
    if (-not (Test-Path -LiteralPath $path)) { return }
    if (-not (Test-Path -LiteralPath $path -PathType Container)) {
        throw 'Temporary lab cleanup target is not a directory.'
    }
    $links = @(Get-ChildItem -LiteralPath $path -Recurse -Force -ErrorAction Stop |
        Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint })
    if ($links.Count -gt 0) { throw 'Temporary lab cleanup tree contains a link.' }
    Remove-Item -LiteralPath $path -Recurse -Force -ErrorAction Stop
}

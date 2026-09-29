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

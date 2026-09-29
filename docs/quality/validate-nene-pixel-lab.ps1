[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')

# Synthetic trees under a temporary directory only; the real lab folder is never read or changed.

function Assert-NenePixelLabRejects {
    param([scriptblock]$Action, [string]$Case)
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw "Lab resolver accepted: $Case" }
}

function Assert-NenePixelLabEquals {
    param([string]$Actual, [string]$Expected, [string]$Case)
    if ($Actual -cne $Expected) { throw "Lab resolver case '$Case' returned '$Actual', expected '$Expected'." }
}

function New-NenePixelLabDirectory {
    param([string]$Path)
    New-Item -ItemType Directory -Path $Path -Force | Out-Null
    return ([IO.Path]::GetFullPath($Path).Replace('\', '/').TrimEnd('/'))
}

$savedLab = [Environment]::GetEnvironmentVariable('NENE_PIXEL_LAB')
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('nene-pixel-lab-test-' + [guid]::NewGuid().ToString('N'))
try {
    [Environment]::SetEnvironmentVariable('NENE_PIXEL_LAB', $null)

    $environmentLab = New-NenePixelLabDirectory (Join-Path $testRoot 'environment-lab')
    New-Item -ItemType File -Path (Join-Path $environmentLab '.nene-pixel-lab') | Out-Null
    $markedLab = New-NenePixelLabDirectory (Join-Path $testRoot 'marked-lab')
    New-Item -ItemType File -Path (Join-Path $markedLab '.nene-pixel-lab') | Out-Null
    $deepStart = New-NenePixelLabDirectory (Join-Path $markedLab 'a/b/c')
    $siblingLab = New-NenePixelLabDirectory (Join-Path $testRoot 'repos/NENE-PIXEL-LAB')
    New-Item -ItemType File -Path (Join-Path $siblingLab '.nene-pixel-lab') | Out-Null
    $repository = New-NenePixelLabDirectory (Join-Path $testRoot 'repos/NENE-PIXEL')
    New-Item -ItemType Directory -Path (Join-Path $repository '.git') | Out-Null
    $repositoryStart = New-NenePixelLabDirectory (Join-Path $repository 'docs/quality')
    $worktree = New-NenePixelLabDirectory (Join-Path $testRoot 'lonely/NENE-PIXEL')
    New-Item -ItemType File -Path (Join-Path $worktree '.git') | Out-Null
    $unmarked = New-NenePixelLabDirectory (Join-Path $testRoot 'unmarked')
    $nowhere = New-NenePixelLabDirectory (Join-Path $testRoot 'nowhere/p/q')

    # 1. The environment variable wins over a marked ancestor.
    [Environment]::SetEnvironmentVariable('NENE_PIXEL_LAB', $environmentLab)
    Assert-NenePixelLabEquals (Get-NenePixelLabRoot -StartDirectory $deepStart) $environmentLab 'environment first'
    [Environment]::SetEnvironmentVariable('NENE_PIXEL_LAB', $null)

    # 2. Without the variable, the nearest marked ancestor is found from three levels down.
    Assert-NenePixelLabEquals (Get-NenePixelLabRoot -StartDirectory $deepStart) $markedLab 'marked ancestor'

    # 3. Without a marked ancestor, the marked NENE-PIXEL-LAB next to the `.git` directory is used.
    Assert-NenePixelLabEquals (Get-NenePixelLabRoot -StartDirectory $repositoryStart) $siblingLab 'repository sibling'
    Assert-NenePixelLabEquals (Get-NenePixelLabPath -RelativePath 'clones/n120-candidate' -StartDirectory $repositoryStart) `
        "$siblingLab/clones/n120-candidate" 'lab path'

    # 4. An environment variable naming an unmarked directory is refused.
    [Environment]::SetEnvironmentVariable('NENE_PIXEL_LAB', $unmarked)
    Assert-NenePixelLabRejects { Get-NenePixelLabRoot -StartDirectory $deepStart } 'environment without marker'
    [Environment]::SetEnvironmentVariable('NENE_PIXEL_LAB', $null)

    # 5. No marker, and no marked sibling of a `.git` (file or directory), is refused.
    Assert-NenePixelLabRejects { Get-NenePixelLabRoot -StartDirectory $nowhere } 'no lab anywhere'
    Assert-NenePixelLabRejects { Get-NenePixelLabRoot -StartDirectory $worktree } '.git file without a lab sibling'

    # 6. Lab paths must be relative and free of '..'.
    Assert-NenePixelLabRejects { Get-NenePixelLabPath -RelativePath $environmentLab -StartDirectory $deepStart } 'absolute path'
    Assert-NenePixelLabRejects { Get-NenePixelLabPath -RelativePath 'C:/outside' -StartDirectory $deepStart } 'drive path'
    Assert-NenePixelLabRejects { Get-NenePixelLabPath -RelativePath '../outside' -StartDirectory $deepStart } 'parent path'
    Assert-NenePixelLabRejects { Get-NenePixelLabPath -RelativePath 'clones/../../outside' -StartDirectory $deepStart } 'nested parent path'
}
finally {
    [Environment]::SetEnvironmentVariable('NENE_PIXEL_LAB', $savedLab)
    if (Test-Path -LiteralPath $testRoot) { Remove-Item -LiteralPath $testRoot -Recurse -Force }
}

Write-Output 'NENE_PIXEL_LAB_RESOLVER=PASS'
exit 0

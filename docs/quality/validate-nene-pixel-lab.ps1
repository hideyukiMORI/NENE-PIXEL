[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')

# Synthetic resolver trees live in one fresh lab output; unrelated lab contents remain untouched.

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
$testDirectory = New-NenePixelLabTemporaryDirectory -Prefix 'nene-pixel-lab-test' -StartDirectory $PSScriptRoot
$testRoot = $testDirectory.Path
$script:isolateFixtureAncestors = $true
function Test-Path {
    param([string]$LiteralPath, [string]$PathType = 'Any')
    # The fixture now lives beneath the real marked lab. Hide only marker observations above
    # the synthetic root, so sibling/no-marker cases still exercise their intended ancestry.
    $fullPath = [IO.Path]::GetFullPath($LiteralPath)
    $insideFixture = $fullPath.StartsWith($testRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::Ordinal)
    if ($script:isolateFixtureAncestors -and [IO.Path]::GetFileName($fullPath) -ceq '.nene-pixel-lab' -and
        -not $insideFixture) { return $false }
    return Microsoft.PowerShell.Management\Test-Path -LiteralPath $LiteralPath -PathType $PathType
}
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

    # 7. Fixture allocation uses the same lab authority and requires a usable marked lab.
    [Environment]::SetEnvironmentVariable('NENE_PIXEL_LAB', $unmarked)
    Assert-NenePixelLabRejects { New-NenePixelLabTemporaryDirectory -Prefix 'fixture' } 'temporary without marker'
    if (Test-Path -LiteralPath (Join-Path $unmarked 'outputs')) { throw 'Rejected lab was modified.' }
    [Environment]::SetEnvironmentVariable('NENE_PIXEL_LAB', $environmentLab)
    Assert-NenePixelLabRejects { New-NenePixelLabTemporaryDirectory -Prefix '../escape' } 'temporary prefix escape'
    $first = New-NenePixelLabTemporaryDirectory -Prefix 'fixture'
    $second = New-NenePixelLabTemporaryDirectory -Prefix 'fixture'
    $expectedParent = [IO.Path]::GetFullPath((Join-Path $environmentLab 'outputs/temporary'))
    Assert-NenePixelLabEquals ([IO.Path]::GetDirectoryName($first.Path)) $expectedParent 'temporary in lab'
    if ($first.Path -ceq $second.Path) { throw 'Temporary allocations share a directory.' }
    $payload = Join-Path $first.Path 'payload.txt'
    [IO.File]::WriteAllText($payload, 'owned')
    $sentinel = Join-Path $environmentLab 'unrelated.txt'
    [IO.File]::WriteAllText($sentinel, 'keep')

    # 8. A changed target must never delete a lab root, sibling allocation or unrelated output.
    foreach ($target in @($environmentLab, $expectedParent, (Join-Path $environmentLab 'evidence'), $second.Path)) {
        $changed = [pscustomobject]@{ LabRoot = $first.LabRoot; Name = $first.Name; Path = $target }
        Assert-NenePixelLabRejects { Remove-NenePixelLabTemporaryDirectory $changed } 'cleanup target drift'
    }
    if ([IO.File]::ReadAllText($payload) -cne 'owned' -or [IO.File]::ReadAllText($sentinel) -cne 'keep') {
        throw 'Rejected cleanup changed owned or unrelated data.'
    }

    # 9. Both an ancestor link and a link inside an allocated tree are refused before mutation.
    $linkedLab = Join-Path $testRoot 'linked-lab'
    $linkedChild = Join-Path $first.Path 'linked-child'
    $linkType = if ($IsWindows) { 'Junction' } else { 'SymbolicLink' }
    New-Item -ItemType $linkType -Path $linkedLab -Target $environmentLab | Out-Null
    try {
        [Environment]::SetEnvironmentVariable('NENE_PIXEL_LAB', $linkedLab)
        Assert-NenePixelLabRejects { New-NenePixelLabTemporaryDirectory -Prefix 'fixture' } 'linked lab'
    } finally {
        [IO.Directory]::Delete($linkedLab)
        [Environment]::SetEnvironmentVariable('NENE_PIXEL_LAB', $environmentLab)
    }
    New-Item -ItemType $linkType -Path $linkedChild -Target $second.Path | Out-Null
    try {
        Assert-NenePixelLabRejects { Remove-NenePixelLabTemporaryDirectory $first } 'linked child'
        if (-not (Test-Path -LiteralPath $second.Path) -or [IO.File]::ReadAllText($payload) -cne 'owned') {
            throw 'Linked cleanup modified a target before refusal.'
        }
    } finally {
        [IO.Directory]::Delete($linkedChild)
    }

    # 10. Cleanup retains its original lab even when a resolver fixture changes the environment.
    [Environment]::SetEnvironmentVariable('NENE_PIXEL_LAB', $markedLab)
    Remove-NenePixelLabTemporaryDirectory $first
    Remove-NenePixelLabTemporaryDirectory $second
    Remove-NenePixelLabTemporaryDirectory $first
    if ((Test-Path -LiteralPath $first.Path) -or (Test-Path -LiteralPath $second.Path) -or
        [IO.File]::ReadAllText($sentinel) -cne 'keep') { throw 'Owned cleanup did not preserve its boundary.' }
}
finally {
    $script:isolateFixtureAncestors = $false
    [Environment]::SetEnvironmentVariable('NENE_PIXEL_LAB', $savedLab)
    Remove-NenePixelLabTemporaryDirectory -Directory $testDirectory
}

Write-Output 'NENE_PIXEL_LAB_RESOLVER=PASS'
exit 0

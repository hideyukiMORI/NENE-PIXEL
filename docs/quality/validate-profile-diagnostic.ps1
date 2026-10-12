[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
. (Join-Path $PSScriptRoot 'prepare-profile-diagnostic.ps1')
. (Join-Path $PSScriptRoot 'generate-baseline-profile.ps1')

function Assert-Rejected {
    param(
        [Parameter(Mandatory)][scriptblock]$Action,
        [Parameter(Mandatory)][string]$Case,
        [Parameter(Mandatory)][string]$ExpectedMessage
    )
    try {
        & $Action | Out-Null
    } catch {
        if ($_.Exception.Message.Contains($ExpectedMessage)) { return }
        throw "Wrong rejection for $($Case): $($_.Exception.Message)"
    }
    throw "Expected diagnostic rejection: $Case"
}

function Invoke-TestGit {
    param([Parameter(Mandatory)][string]$Root, [Parameter(Mandatory)][string[]]$Arguments)
    $result = @(& git -C $Root @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "fixture git $($Arguments[0]) failed: $([string]::Join(' | ', [string[]]$result))"
    }
    return $result
}

$temporary = New-NenePixelLabTemporaryDirectory -Prefix 'profile-diag' -StartDirectory $PSScriptRoot
$seed = Join-Path $temporary.Path 'seed'
$worktrees = [Collections.Generic.List[string]]::new()
$caseCount = 0
try {
    New-Item -ItemType Directory -Path $seed | Out-Null
    Invoke-TestGit $seed @('init', '-q') | Out-Null
    Invoke-TestGit $seed @('config', 'user.email', 'synthetic@example.invalid') | Out-Null
    Invoke-TestGit $seed @('config', 'user.name', 'Synthetic') | Out-Null
    [IO.File]::WriteAllText((Join-Path $seed 'sample.txt'), "before`n",
        [Text.UTF8Encoding]::new($false))
    Invoke-TestGit $seed @('add', 'sample.txt') | Out-Null
    Invoke-TestGit $seed @('commit', '-qm', 'fixture') | Out-Null
    $revision = [string](Invoke-TestGit $seed @('rev-parse', 'HEAD'))
    $tree = [string](Invoke-TestGit $seed @('show', '-s', '--format=%T', 'HEAD'))
    $patchcraft = Join-Path $temporary.Path 'patchcraft'
    Invoke-TestGit $seed @('worktree', 'add', '--detach', '-q', $patchcraft, 'HEAD') | Out-Null
    $worktrees.Add($patchcraft)
    $pre = Get-DiagnosticSha256 (Join-Path $patchcraft 'sample.txt')
    [IO.File]::WriteAllText((Join-Path $patchcraft 'sample.txt'), "after`n",
        [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $patchcraft 'observer.txt'), "observer`n",
        [Text.UTF8Encoding]::new($false))
    Invoke-TestGit $patchcraft @('add', '-N', 'observer.txt') | Out-Null
    $patch = Join-Path $temporary.Path 'observer.patch'
    $patchText = [string]::Join("`n",
        [string[]](Invoke-TestGit $patchcraft @('diff', '--binary', '--', 'sample.txt', 'observer.txt')))
    [IO.File]::WriteAllText($patch, "$patchText`n", [Text.UTF8Encoding]::new($false))
    $trial = Join-Path $temporary.Path 'trial'
    Invoke-TestGit $seed @('worktree', 'add', '--detach', '-q', $trial, 'HEAD') | Out-Null
    $worktrees.Add($trial)
    Invoke-TestGit $trial @('apply', $patch) | Out-Null
    $post = Get-DiagnosticSha256 (Join-Path $trial 'sample.txt')
    $observerPost = Get-DiagnosticSha256 (Join-Path $trial 'observer.txt')
    $manifestPath = Join-Path $temporary.Path 'manifest.json'
    $manifest = [ordered]@{
        schema = 'nene-pixel-profile-diagnostic-patch-v1'
        source_revision = $revision
        source_tree = $tree
        diagnostic_schema = 'nene-pixel-profile-diagnostic-v1'
        marker = '.nene-pixel-profile-diagnostic.json'
        patch_sha256 = Get-DiagnosticSha256 $patch
        files = @(
            [ordered]@{ path = 'sample.txt'; pre_sha256 = $pre; post_sha256 = $post },
            [ordered]@{ path = 'observer.txt'; pre_sha256 = $null; post_sha256 = $observerPost }
        )
    }
    $syntheticAllowedFiles = @(
        @{ Path = 'sample.txt'; PreExists = $true },
        @{ Path = 'observer.txt'; PreExists = $false }
    )
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifestPath -Encoding utf8
    $newWorktree = {
        $path = Join-Path $temporary.Path ([guid]::NewGuid().ToString('N'))
        Invoke-TestGit $seed @('worktree', 'add', '--detach', '-q', $path, 'HEAD') | Out-Null
        $worktrees.Add($path)
        return $path
    }
    $prepare = {
        param($root, $manifestFile)
        Invoke-PrepareProfileDiagnostic -RepositoryRoot $root -ManifestPath $manifestFile `
            -PatchPath $patch -ExpectedRevision $revision -ExpectedTree $tree `
            -AllowedFiles $syntheticAllowedFiles
    }

    $success = & $newWorktree
    $markerPath = & $prepare $success $manifestPath
    $actual = Get-Content -LiteralPath $markerPath -Raw | ConvertFrom-Json
    if ($actual.status -cne 'prepared' -or
        (Get-DiagnosticSha256 (Join-Path $success 'sample.txt')) -cne $post -or
        (Get-DiagnosticSha256 (Join-Path $success 'observer.txt')) -cne $observerPost) {
        throw 'Prepared fixture has wrong marker or post hash.'
    }
    $caseCount++
    Assert-Rejected { & $prepare $success $manifestPath } 'reapply' 'Diagnostic marker already exists.'
    $caseCount++

    $badPin = & $newWorktree
    $wrongManifest = Join-Path $temporary.Path 'wrong-pin.json'
    $manifest.source_revision = '0000000000000000000000000000000000000000'
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $wrongManifest -Encoding utf8
    Assert-Rejected { & $prepare $badPin $wrongManifest } 'source pin' 'Diagnostic manifest schema, source or marker mismatch.'
    if (Test-Path -LiteralPath (Join-Path $badPin '.nene-pixel-profile-diagnostic.json')) {
        throw 'Bad pin created a marker.'
    }
    $caseCount++
    $manifest.source_revision = $revision
    $manifest.diagnostic_schema = 'wrong-diagnostic-schema'
    $wrongSchema = Join-Path $temporary.Path 'wrong-schema.json'
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $wrongSchema -Encoding utf8
    Assert-Rejected { & $prepare $badPin $wrongSchema } 'event schema' 'Diagnostic manifest schema, source or marker mismatch.'
    $caseCount++
    $manifest.diagnostic_schema = 'nene-pixel-profile-diagnostic-v1'
    $manifest.patch_sha256 = '0' * 64
    $wrongHash = Join-Path $temporary.Path 'wrong-hash.json'
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $wrongHash -Encoding utf8
    Assert-Rejected { & $prepare $badPin $wrongHash } 'patch hash' 'Diagnostic patch SHA-256 mismatch.'
    $caseCount++
    $manifest.patch_sha256 = Get-DiagnosticSha256 $patch
    $manifest.files[0].pre_sha256 = '0' * 64
    $wrongPre = Join-Path $temporary.Path 'wrong-pre.json'
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $wrongPre -Encoding utf8
    Assert-Rejected { & $prepare $badPin $wrongPre } 'input hash' 'Diagnostic input hash mismatch: sample.txt'
    $caseCount++
    $manifest.files[0].pre_sha256 = $pre
    $manifest.files += [ordered]@{ path = 'extra.txt'; pre_sha256 = $null; post_sha256 = '0' * 64 }
    $wrongPaths = Join-Path $temporary.Path 'wrong-paths.json'
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $wrongPaths -Encoding utf8
    Assert-Rejected { & $prepare $badPin $wrongPaths } 'forbidden manifest path' 'Unlisted diagnostic file'
    $caseCount++
    $manifest.files = @($manifest.files[0], $manifest.files[1])
    $missingPath = Join-Path $temporary.Path 'missing-path.json'
    $manifest.files = @($manifest.files[0])
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $missingPath -Encoding utf8
    Assert-Rejected { & $prepare $badPin $missingPath } 'incomplete file set' 'does not include the fixed file set'
    $caseCount++
    $manifest.files = @(
        [ordered]@{ path = 'sample.txt'; pre_sha256 = $pre; post_sha256 = $post },
        [ordered]@{ path = 'observer.txt'; pre_sha256 = '0' * 64; post_sha256 = $observerPost }
    )
    $wrongCategory = Join-Path $temporary.Path 'wrong-category.json'
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $wrongCategory -Encoding utf8
    Assert-Rejected { & $prepare $badPin $wrongCategory } 'existing/new category' 'wrong existing/new category'
    $caseCount++
    $manifest.files[1].pre_sha256 = $null
    $forbiddenPath = 'docs/quality/generate-baseline-profile.ps1'
    $forbiddenPatch = Join-Path $temporary.Path 'forbidden-path.patch'
    [IO.File]::WriteAllText($forbiddenPatch,
        ([IO.File]::ReadAllText($patch).Replace('observer.txt', $forbiddenPath)),
        [Text.UTF8Encoding]::new($false))
    $manifest.files[1].path = $forbiddenPath
    $manifest.patch_sha256 = Get-DiagnosticSha256 $forbiddenPatch
    $forbiddenManifest = Join-Path $temporary.Path 'forbidden-path.json'
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $forbiddenManifest -Encoding utf8
    Assert-Rejected {
        Invoke-PrepareProfileDiagnostic -RepositoryRoot $badPin -ManifestPath $forbiddenManifest `
            -PatchPath $forbiddenPatch -ExpectedRevision $revision -ExpectedTree $tree `
            -AllowedFiles $syntheticAllowedFiles
    } 'self-consistent forbidden patch' 'Unlisted diagnostic file'
    $caseCount++
    $manifest.files[1].path = 'observer.txt'
    $manifest.patch_sha256 = Get-DiagnosticSha256 $patch
    $shortPatch = Join-Path $temporary.Path 'missing-patch-path.patch'
    Invoke-TestGit $patchcraft @('diff', '--binary', "--output=$shortPatch", 'HEAD', '--', 'sample.txt') |
        Out-Null
    $manifest.patch_sha256 = Get-DiagnosticSha256 $shortPatch
    $shortPatchManifest = Join-Path $temporary.Path 'missing-patch-path.json'
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $shortPatchManifest -Encoding utf8
    Assert-Rejected {
        Invoke-PrepareProfileDiagnostic -RepositoryRoot $badPin -ManifestPath $shortPatchManifest `
            -PatchPath $shortPatch -ExpectedRevision $revision -ExpectedTree $tree `
            -AllowedFiles $syntheticAllowedFiles
    } 'patch path set' 'Diagnostic patch path set mismatch.'
    $caseCount++
    $manifest.patch_sha256 = Get-DiagnosticSha256 $patch
    $manifest.files[0].post_sha256 = '0' * 64
    $wrongPost = Join-Path $temporary.Path 'wrong-post.json'
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $wrongPost -Encoding utf8
    $partial = & $newWorktree
    Assert-Rejected { & $prepare $partial $wrongPost } 'output hash' 'Diagnostic output hash mismatch: sample.txt'
    $partialMarker = Get-Content -LiteralPath (Join-Path $partial '.nene-pixel-profile-diagnostic.json') -Raw |
        ConvertFrom-Json
    if ($partialMarker.status -cne 'invalid-partial') { throw 'Partial patch lost its invalid marker.' }
    $caseCount++
    $manifest.files[0].post_sha256 = $post
    $manifest.files[0].path = 'AUX.txt'
    $wrongPathName = Join-Path $temporary.Path 'wrong-path-name.json'
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $wrongPathName -Encoding utf8
    Assert-Rejected { & $prepare $badPin $wrongPathName } 'reserved path' 'Unsafe diagnostic path segment: AUX.txt'
    $caseCount++
    $manifest.files[0].path = 'sample.txt'
    $manifest.files += [ordered]@{ path = 'Sample.txt'; pre_sha256 = $pre; post_sha256 = $post }
    $caseCollision = Join-Path $temporary.Path 'case-collision.json'
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $caseCollision -Encoding utf8
    Assert-Rejected { & $prepare $badPin $caseCollision } 'case collision' 'Duplicate diagnostic file: Sample.txt'
    $caseCount++
    $manifest.files = @($manifest.files[0], $manifest.files[1])

    $wrongTree = Join-Path $temporary.Path 'wrong-tree.json'
    $manifest.source_tree = '0' * 40
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $wrongTree -Encoding utf8
    Assert-Rejected { & $prepare $badPin $wrongTree } 'tree pin' 'Diagnostic manifest schema, source or marker mismatch.'
    $caseCount++
    $manifest.source_tree = $tree
    $drift = & $newWorktree
    [IO.File]::WriteAllText((Join-Path $drift 'drift.txt'), 'drift')
    Invoke-TestGit $drift @('add', 'drift.txt') | Out-Null
    Invoke-TestGit $drift @('commit', '-qm', 'drift') | Out-Null
    Assert-Rejected { & $prepare $drift $manifestPath } 'actual HEAD drift' 'Diagnostic destination HEAD or tree differs'
    $caseCount++
    $conflict = & $newWorktree
    [IO.File]::WriteAllText((Join-Path $conflict 'observer.txt'), 'conflict')
    Assert-Rejected { & $prepare $conflict $manifestPath } 'conflicting new file' 'must be clean'
    $caseCount++
    foreach ($mode in @('100755', '120000')) {
        $modePatch = Join-Path $temporary.Path "mode-$mode.patch"
        $originalPatch = [IO.File]::ReadAllText($patch)
        if (-not $originalPatch.Contains('new file mode 100644')) {
            throw 'Synthetic patch lacks the expected new-file mode header.'
        }
        [IO.File]::WriteAllText($modePatch,
            $originalPatch.Replace('new file mode 100644', "new file mode $mode"),
            [Text.UTF8Encoding]::new($false))
        $manifest.patch_sha256 = Get-DiagnosticSha256 $modePatch
        $modeManifest = Join-Path $temporary.Path "mode-$mode.json"
        $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $modeManifest -Encoding utf8
        $modeRoot = & $newWorktree
        Assert-Rejected {
            Invoke-PrepareProfileDiagnostic -RepositoryRoot $modeRoot -ManifestPath $modeManifest `
                -PatchPath $modePatch -ExpectedRevision $revision -ExpectedTree $tree `
                -AllowedFiles $syntheticAllowedFiles
        } "file mode $mode" 'changes file type, mode, identity or deletes'
        $caseCount++
    }
    $manifest.patch_sha256 = Get-DiagnosticSha256 $patch
    [IO.File]::WriteAllText((Join-Path $badPin 'untracked.txt'), 'dirty')
    Assert-Rejected { & $prepare $badPin $manifestPath } 'dirty' 'must be clean'
    $caseCount++
    $outsideLab = [IO.Path]::GetPathRoot($temporary.Path)
    Assert-Rejected { & $prepare $outsideLab $manifestPath } 'outside lab' 'must be an existing lab worktree'
    $caseCount++

    $linked = Join-Path $temporary.Path 'linked'
    New-Item -ItemType Junction -Path $linked -Target $badPin | Out-Null
    Assert-Rejected { & $prepare $linked $manifestPath } 'linked destination' 'contains a link'
    Remove-Item -LiteralPath $linked -Force
    $caseCount++

    $script:DiagnosticTestCallbackState = [pscustomobject]@{ Calls = 0 }
    $invoker = {
        $script:DiagnosticTestCallbackState.Calls++
        throw 'Gradle callback must not run.'
    }
    foreach ($relative in @(
        '.nene-pixel-profile-diagnostic.json',
        'core/application/src/main/kotlin/io/github/hideyukimori/nenepixel/core/application/persistence/AutosaveDiagnosticObserver.kt',
        'app/android/src/main/kotlin/io/github/hideyukimori/nenepixel/ProfileDiagnosticRecorder.kt',
        'app/android/src/main/kotlin/io/github/hideyukimori/nenepixel/AutosaveSchedulerDiagnostics.kt',
        'app/android/src/main/kotlin/io/github/hideyukimori/nenepixel/ProfileDiagnosticAndroid.kt',
        'quality/baseline-profile/src/main/kotlin/io/github/hideyukimori/nenepixel/quality/baselineprofile/ProducerProfileDiagnosticRecorder.kt'
    )) {
        $guardRoot = & $newWorktree
        $source = Join-Path $guardRoot $relative
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($source)) | Out-Null
        [IO.File]::WriteAllText($source, '{}')
        Invoke-TestGit $guardRoot @('add', '--', $relative) | Out-Null
        Invoke-TestGit $guardRoot @('commit', '-qm', 'mistaken diagnostic commit') | Out-Null
        Assert-Rejected {
            Invoke-BaselineProfileEvidenceGeneration -RepositoryRoot $guardRoot `
                -EvidenceIdentity 'synthetic-guard' -GradleInvoker $invoker
        } "committed guard $relative" 'Diagnostic source is forbidden in canonical Baseline Profile generation'
        if (Test-Path -LiteralPath (Join-Path $guardRoot 'build/reports/baseline-profile-generation/synthetic-guard')) {
            throw 'Canonical wrapper created evidence before diagnostic rejection.'
        }
        $caseCount++
    }
    if ($script:DiagnosticTestCallbackState.Calls -ne 0) {
        throw "Gradle callback count was $($script:DiagnosticTestCallbackState.Calls)."
    }
    Write-Output "PROFILE_DIAGNOSTIC_HOST=pass cases=$caseCount gradle_callbacks=$($script:DiagnosticTestCallbackState.Calls)"
}
finally {
    # These worktrees and the seed are synthetic test fixtures created under one validated lab root.
    foreach ($path in $worktrees) {
        if ([IO.Path]::GetFullPath($path).StartsWith(
            ([IO.Path]::GetFullPath($temporary.Path) + [IO.Path]::DirectorySeparatorChar),
            [StringComparison]::OrdinalIgnoreCase)) {
            & git -C $seed worktree remove --force $path 2>&1 | Out-Null
        }
    }
    Remove-NenePixelLabTemporaryDirectory $temporary
}

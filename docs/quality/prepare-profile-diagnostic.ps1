[CmdletBinding()]
param([string]$RepositoryRoot = '')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')

$script:DiagnosticManifestSchema = 'nene-pixel-profile-diagnostic-patch-v1'
$script:DiagnosticEventSchema = 'nene-pixel-profile-diagnostic-v1'
$script:DiagnosticMarker = '.nene-pixel-profile-diagnostic.json'
$script:DiagnosticRevision = '314bcbdacc3b0816e56d5c7e2cd7f8d020c5b511'
$script:DiagnosticTree = '323df6a7364d2097adcd5360aea6519b11bcac20'
$script:DiagnosticAllowedFiles = @(
    @{ Path = 'app/android/src/main/kotlin/io/github/hideyukimori/nenepixel/AutosaveSchedule.kt'; PreExists = $true },
    @{ Path = 'app/android/src/main/kotlin/io/github/hideyukimori/nenepixel/AutosaveScheduler.kt'; PreExists = $true },
    @{ Path = 'app/android/src/main/kotlin/io/github/hideyukimori/nenepixel/EditorComposition.kt'; PreExists = $true },
    @{ Path = 'app/android/src/main/kotlin/io/github/hideyukimori/nenepixel/EditorRuntimeViewModel.kt'; PreExists = $true },
    @{ Path = 'app/android/src/main/kotlin/io/github/hideyukimori/nenepixel/MainActivity.kt'; PreExists = $true },
    @{ Path = 'app/android/src/test/kotlin/io/github/hideyukimori/nenepixel/AutosaveScheduleTest.kt'; PreExists = $true },
    @{ Path = 'app/android/src/test/kotlin/io/github/hideyukimori/nenepixel/AutosaveSchedulerTest.kt'; PreExists = $true },
    @{ Path = 'core/application/src/main/kotlin/io/github/hideyukimori/nenepixel/core/application/editor/EditorRuntime.kt'; PreExists = $true },
    @{ Path = 'core/application/src/main/kotlin/io/github/hideyukimori/nenepixel/core/application/persistence/EditorPersistenceWorkflow.kt'; PreExists = $true },
    @{ Path = 'core/application/src/main/kotlin/io/github/hideyukimori/nenepixel/core/application/persistence/PersistenceAutosaveFlow.kt'; PreExists = $true },
    @{ Path = 'quality/baseline-profile/src/main/kotlin/io/github/hideyukimori/nenepixel/quality/baselineprofile/PencilUndoBaselineProfile.kt'; PreExists = $true },
    @{ Path = 'app/android/src/main/kotlin/io/github/hideyukimori/nenepixel/AutosaveSchedulerDiagnostics.kt'; PreExists = $false },
    @{ Path = 'app/android/src/main/kotlin/io/github/hideyukimori/nenepixel/ProfileDiagnosticAndroid.kt'; PreExists = $false },
    @{ Path = 'app/android/src/main/kotlin/io/github/hideyukimori/nenepixel/ProfileDiagnosticRecorder.kt'; PreExists = $false },
    @{ Path = 'app/android/src/test/kotlin/io/github/hideyukimori/nenepixel/ProfileDiagnosticRecorderTest.kt'; PreExists = $false },
    @{ Path = 'core/application/src/main/kotlin/io/github/hideyukimori/nenepixel/core/application/persistence/AutosaveDiagnosticObserver.kt'; PreExists = $false },
    @{ Path = 'quality/baseline-profile/src/main/kotlin/io/github/hideyukimori/nenepixel/quality/baselineprofile/ProducerProfileDiagnosticRecorder.kt'; PreExists = $false }
)

function Get-DiagnosticSha256 {
    param([Parameter(Mandatory)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-DiagnosticPathHasNoLink {
    param([Parameter(Mandatory)][string]$Path)
    $current = [IO.Path]::GetFullPath($Path)
    while ($true) {
        $item = Get-Item -LiteralPath $current -Force -ErrorAction SilentlyContinue
        if ($null -ne $item) {
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw "Diagnostic path contains a link: $current"
            }
        }
        $parent = [IO.Path]::GetDirectoryName($current)
        if ([string]::IsNullOrEmpty($parent) -or $parent -eq $current) { break }
        $current = $parent
    }
}

function Assert-DiagnosticRelativePath {
    param([Parameter(Mandatory)][string]$Path)
    if ($Path.Length -gt 240 -or $Path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $Path.StartsWith('/') -or
        $Path.Contains('//') -or @($Path.Split('/')) -contains '..' -or
        @($Path.Split('/')) -contains '.' -or $Path -eq $script:DiagnosticMarker) {
        throw "Unsafe diagnostic file path: $Path"
    }
    foreach ($segment in $Path.Split('/')) {
        if ($segment.EndsWith('.') -or $segment -ieq '.git' -or
            $segment -imatch '^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\..*)?$') {
            throw "Unsafe diagnostic path segment: $segment"
        }
    }
}

function Invoke-DiagnosticGit {
    param([Parameter(Mandatory)][string]$Root, [Parameter(Mandatory)][string[]]$Arguments)
    $output = @(& git -C $Root @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments[0]) failed: $([string]::Join(' | ', [string[]]$output))"
    }
    return $output
}

function Invoke-PrepareProfileDiagnostic {
    param(
        [Parameter(Mandatory)][string]$RepositoryRoot,
        [Parameter(Mandatory)][string]$ManifestPath,
        [Parameter(Mandatory)][string]$PatchPath,
        [string]$ExpectedRevision = $script:DiagnosticRevision,
        [string]$ExpectedTree = $script:DiagnosticTree,
        [object[]]$AllowedFiles = $script:DiagnosticAllowedFiles
    )

    $root = [IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\', '/')
    $lab = [IO.Path]::GetFullPath((Get-NenePixelLabRoot -StartDirectory $root)).TrimEnd('\', '/')
    $underLab = $root.StartsWith("$lab$([IO.Path]::DirectorySeparatorChar)",
        [StringComparison]::OrdinalIgnoreCase)
    if (-not $underLab -or -not (Test-Path -LiteralPath $root -PathType Container)) {
        throw 'Diagnostic destination must be an existing lab worktree.'
    }
    Assert-DiagnosticPathHasNoLink $root
    Assert-DiagnosticPathHasNoLink $ManifestPath
    Assert-DiagnosticPathHasNoLink $PatchPath
    $owner = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\', '/')
    if ($root.Equals($owner, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Diagnostic destination cannot be the tools repository.'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $root '.git') -PathType Leaf)) {
        throw 'Diagnostic destination must be a separate linked worktree.'
    }
    Assert-DiagnosticPathHasNoLink (Join-Path $root '.git')
    $top = [IO.Path]::GetFullPath([string](Invoke-DiagnosticGit $root @('rev-parse', '--show-toplevel'))).TrimEnd('\', '/')
    if (-not $top.Equals($root, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Diagnostic destination is not the worktree root.'
    }
    $marker = Join-Path $root $script:DiagnosticMarker
    Assert-DiagnosticPathHasNoLink $marker
    if (Test-Path -LiteralPath $marker) { throw 'Diagnostic marker already exists.' }
    $head = [string](Invoke-DiagnosticGit $root @('rev-parse', 'HEAD'))
    $tree = [string](Invoke-DiagnosticGit $root @('show', '-s', '--format=%T', 'HEAD'))
    if ($head -cne $ExpectedRevision -or $tree -cne $ExpectedTree) {
        throw 'Diagnostic destination HEAD or tree differs from the pinned source.'
    }
    $status = @(Invoke-DiagnosticGit $root @('status', '--porcelain', '--untracked-files=all'))
    if ($status.Count -ne 0) { throw 'Diagnostic destination must be clean, including untracked files.' }
    if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $PatchPath -PathType Leaf)) {
        throw 'Diagnostic manifest or patch is missing.'
    }
    if ((Get-Item -LiteralPath $ManifestPath).Length -gt 131072 -or
        (Get-Item -LiteralPath $PatchPath).Length -gt 524288) {
        throw 'Diagnostic manifest or patch exceeds its host preparation bound.'
    }
    $manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
    if ($manifest.schema -cne $script:DiagnosticManifestSchema -or
        $manifest.source_revision -cne $ExpectedRevision -or
        $manifest.source_tree -cne $ExpectedTree -or
        $manifest.marker -cne $script:DiagnosticMarker -or
        $manifest.diagnostic_schema -cne $script:DiagnosticEventSchema) {
        throw 'Diagnostic manifest schema, source or marker mismatch.'
    }
    $patchHash = Get-DiagnosticSha256 $PatchPath
    if ($manifest.patch_sha256 -cne $patchHash) { throw 'Diagnostic patch SHA-256 mismatch.' }
    $files = @($manifest.files)
    if ($files.Count -eq 0 -or $files.Count -gt 64) {
        throw 'Diagnostic manifest file count is outside 1..64.'
    }
    $manifestPaths = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($file in $files) {
        $relative = [string]$file.path
        Assert-DiagnosticRelativePath $relative
        if (-not $manifestPaths.Add($relative)) { throw "Duplicate diagnostic file: $relative" }
        $allowed = @($AllowedFiles | Where-Object { $_.Path -ceq $relative })
        if ($allowed.Count -ne 1 -or
            [bool]$allowed[0].PreExists -ne ($null -ne $file.pre_sha256)) {
            throw "Unlisted diagnostic file or wrong existing/new category: $relative"
        }
        $target = Join-Path $root $relative
        Assert-DiagnosticPathHasNoLink $target
        if ([string]$file.post_sha256 -cnotmatch '^[a-f0-9]{64}$') {
            throw "Missing or invalid post hash: $relative"
        }
        $exists = Test-Path -LiteralPath $target
        if ($null -eq $file.pre_sha256) {
            if ($exists) { throw "Diagnostic new file already exists: $relative" }
        } else {
            if ([string]$file.pre_sha256 -cnotmatch '^[a-f0-9]{64}$' -or
                -not (Test-Path -LiteralPath $target -PathType Leaf) -or
                (Get-DiagnosticSha256 $target) -cne [string]$file.pre_sha256) {
                throw "Diagnostic input hash mismatch: $relative"
            }
        }
    }
    if ($manifestPaths.Count -ne $AllowedFiles.Count) {
        throw 'Diagnostic manifest does not include the fixed file set.'
    }
    $statistics = @(Invoke-DiagnosticGit $root @('apply', '--numstat', $PatchPath))
    $patchPaths = @($statistics | ForEach-Object {
        if ($_ -cnotmatch '^[0-9]+\t[0-9]+\t([A-Za-z0-9_./-]+)$') {
            throw "Unsafe or non-text diagnostic patch record: $_"
        }
        $Matches[1]
    })
    if ($patchPaths.Count -ne $manifestPaths.Count) { throw 'Diagnostic patch path set mismatch.' }
    $patchPathSet = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($path in $patchPaths) {
        Assert-DiagnosticRelativePath ([string]$path)
        if (-not $patchPathSet.Add([string]$path) -or
            -not @($files | Where-Object { $_.path -ceq [string]$path }).Count) {
            throw "Unlisted or repeated diagnostic patch path: $path"
        }
    }
    $summary = @(Invoke-DiagnosticGit $root @('apply', '--summary', $PatchPath))
    foreach ($line in $summary) {
        if ($line -cnotmatch '^ create mode 100644 ([A-Za-z0-9_./-]+)$') {
            throw "Diagnostic patch changes file type, mode, identity or deletes a file: $line"
        }
        $createdPath = $Matches[1]
        $record = @($files | Where-Object { $_.path -ceq $createdPath })
        if ($record.Count -ne 1 -or $null -ne $record[0].pre_sha256) {
            throw "Diagnostic patch creation differs from the manifest: $createdPath"
        }
    }
    Invoke-DiagnosticGit $root @('apply', '--check', '--no-3way', '--whitespace=error', $PatchPath) | Out-Null

    $pending = [ordered]@{
        schema = $script:DiagnosticManifestSchema
        diagnostic_schema = [string]$manifest.diagnostic_schema
        status = 'invalid-partial'
        source_revision = $head
        source_tree = $tree
        patch_sha256 = $patchHash
        files = $files
    }
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($pending | ConvertTo-Json -Depth 8) + "`n")
    $stream = [IO.FileStream]::new($marker, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write,
        [IO.FileShare]::None)
    try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    Invoke-DiagnosticGit $root @('apply', '--no-3way', '--whitespace=error', $PatchPath) | Out-Null
    $actual = @()
    foreach ($file in $files) {
        $target = Join-Path $root ([string]$file.path)
        Assert-DiagnosticPathHasNoLink $target
        if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
            throw "Diagnostic output missing: $($file.path)"
        }
        $hash = Get-DiagnosticSha256 $target
        if ($hash -cne [string]$file.post_sha256) {
            throw "Diagnostic output hash mismatch: $($file.path)"
        }
        $actual += [ordered]@{ path = [string]$file.path; sha256 = $hash }
    }
    $pending.status = 'prepared'
    $pending.actual_files = $actual
    [IO.File]::WriteAllText($marker, (($pending | ConvertTo-Json -Depth 8) + "`n"),
        [Text.UTF8Encoding]::new($false))
    return $marker
}

if ($MyInvocation.InvocationName -ne '.') {
    if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) { throw 'RepositoryRoot is required.' }
    $manifestPath = Join-Path $PSScriptRoot 'profile-diagnostic/manifest.json'
    $patchPath = Join-Path $PSScriptRoot 'profile-diagnostic/observer.patch'
    Invoke-PrepareProfileDiagnostic -RepositoryRoot $RepositoryRoot -ManifestPath $manifestPath `
        -PatchPath $patchPath
}

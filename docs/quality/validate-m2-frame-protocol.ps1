[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$collector = Join-Path $PSScriptRoot "measurements/measure-m2-frame.ps1"
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("nene-frame-protocol-" + [guid]::NewGuid().ToString("N"))
$baselineCommit = "1" * 40
$candidateCommit = "2" * 40
$baselineProductionCommit = "2dd4e01e3bbe88967237cde4e28412d2962fd590"
$baselineProductionTreeHash = "5" * 64
$candidateProductionTreeHash = "6" * 64
$baselineHash = "a" * 64
$candidateHash = "b" * 64
$baselineProfileSource = "3" * 40
$candidateProfileSource = "4" * 40
$baselineProfileAppHash = "c" * 64
$candidateProfileAppHash = "d" * 64
$baselineProfileTestHash = "e" * 64
$candidateProfileTestHash = "f" * 64
$baselineProfHash = "9" * 64
$candidateProfHash = "a" * 64
$baselineProfmHash = "b" * 64
$candidateProfmHash = "c" * 64
$frameFixtureGlobalsOwned = $false

New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
$experimentRoot = Join-Path $temporaryRoot "experiment"

. (Join-Path $PSScriptRoot "baseline-profile-evidence.ps1")
. (Join-Path $PSScriptRoot "measurements/m2-package-dexopt.ps1")

function Write-FixtureJson {
    param(
        [Parameter(Mandatory = $true)]$Value,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $Value | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $Path -Encoding utf8NoBOM
}

function Get-RetainedFixtureRecord {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$RelativePath
    )

    $path = Join-Path $Root $RelativePath
    $item = Get-Item -LiteralPath $path
    return [ordered]@{
        path = $RelativePath.Replace('\', '/')
        byte_count = $item.Length
        sha256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

function New-AcceptanceFixture {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$SourceCommit,
        [Parameter(Mandatory = $true)][string]$AppApkSha256,
        [Parameter(Mandatory = $true)][string]$TestApkSha256,
        [Parameter(Mandatory = $true)][string]$ProfileText
    )

    $root = Join-Path $temporaryRoot "$Name-evidence"
    New-Item -ItemType Directory -Path $root | Out-Null
    $profileBytes = [System.Text.UTF8Encoding]::new($false).GetBytes($ProfileText)
    $profileRecord = $null
    $invocationHashes = @()
    foreach ($ordinal in 1..2) {
        $invocationRoot = Join-Path $root "invocation-$ordinal"
        $producerPath = 'producer-output/device/journey-baseline-prof.txt'
        $exitPath = 'producer-results/test-result-exit-code.txt'
        foreach ($relative in @('source-profile.txt', 'merged-profile.txt', $producerPath)) {
            $path = Join-Path $invocationRoot $relative
            New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
            [System.IO.File]::WriteAllBytes($path, $profileBytes)
        }
        Set-Content -LiteralPath (Join-Path $invocationRoot 'gradle-output.log') `
            -Value '> Task :quality:baseline-profile:connectedNonMinifiedReleaseAndroidTest' `
            -Encoding utf8NoBOM
        New-Item -ItemType Directory -Path (Split-Path -Parent (Join-Path $invocationRoot $exitPath)) `
            -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $invocationRoot $exitPath) -Value '0' `
            -NoNewline -Encoding ascii
        $profileRecord = Get-CanonicalProfileRecord (Join-Path $invocationRoot 'source-profile.txt')
        $manifestRecord = [ordered]@{
            raw_byte_count = $profileRecord.RawByteCount
            raw_sha256 = $profileRecord.RawSha256
            rule_count = $profileRecord.RuleCount
            canonical_byte_count = $profileRecord.CanonicalByteCount
            canonical_sha256 = $profileRecord.CanonicalSha256
        }
        $manifest = [ordered]@{
            schema = 'nene-pixel-baseline-profile-evidence-v1'
            evidence_id = "$Name-evidence"
            invocation = $ordinal
            status = 'valid'
            failure = $null
            started_utc = "2026-09-06T0$ordinal`:00:00.0000000Z"
            ended_utc = "2026-09-06T0$ordinal`:00:01.0000000Z"
            source_revision = $SourceCommit
            timeout_seconds = 1800
            restoration_blocked = $false
            gradle_exit_code = 0
            task_outcomes = [ordered]@{
                ':quality:baseline-profile:connectedNonMinifiedReleaseAndroidTest' = 'EXECUTED'
            }
            producer_profiles = @($manifestRecord)
            merged_profile = $manifestRecord
            source_profile = $manifestRecord
            app_apk_sha256 = $AppApkSha256
            test_apk_sha256 = $TestApkSha256
            retained_files = @(
                'gradle-output.log',
                'merged-profile.txt',
                $producerPath,
                $exitPath,
                'source-profile.txt'
            ) | ForEach-Object { Get-RetainedFixtureRecord $invocationRoot $_ }
        }
        $manifestPath = Join-Path $invocationRoot 'manifest.json'
        Write-FixtureJson $manifest $manifestPath
        $invocationHashes += (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    $pair = [ordered]@{
        schema = 'nene-pixel-baseline-profile-evidence-v1'
        evidence_id = "$Name-evidence"
        status = 'matched'
        failure = $null
        source_revision = $SourceCommit
        app_apk_sha256 = $AppApkSha256
        test_apk_sha256 = $TestApkSha256
        canonical_rule_count = $profileRecord.RuleCount
        canonical_sha256 = $profileRecord.CanonicalSha256
        producer_timeout_seconds = 1800
        validation_timeout_seconds = 300
        invocation_manifest_sha256 = $invocationHashes
    }
    $pairPath = Join-Path $root 'pair-manifest.json'
    Write-FixtureJson $pair $pairPath
    $pairHash = (Get-FileHash -LiteralPath $pairPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $validationPath = Join-Path $root 'validation-output.log'
    Set-Content -LiteralPath $validationPath -Value '> Task :validateBaselineProfile' -Encoding utf8NoBOM
    $acceptance = [ordered]@{
        schema = 'nene-pixel-baseline-profile-evidence-v1'
        evidence_id = "$Name-evidence"
        status = 'accepted'
        pair_manifest_sha256 = $pairHash
        validation_output_sha256 =
            (Get-FileHash -LiteralPath $validationPath -Algorithm SHA256).Hash.ToLowerInvariant()
        canonical_sha256 = $profileRecord.CanonicalSha256
        producer_timeout_seconds = 1800
        validation_timeout_seconds = 300
    }
    $acceptancePath = Join-Path $root 'acceptance-manifest.json'
    Write-FixtureJson $acceptance $acceptancePath
    return [pscustomobject]@{
        Path = $acceptancePath
        Hash = (Get-FileHash -LiteralPath $acceptancePath -Algorithm SHA256).Hash.ToLowerInvariant()
        PairHash = $pairHash
        CanonicalHash = $profileRecord.CanonicalSha256
    }
}

function Get-BytesSha256 {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [System.BitConverter]::ToString($sha256.ComputeHash($Bytes)).Replace("-", "").ToLowerInvariant()
    }
    finally {
        $sha256.Dispose()
    }
}

function Add-ApkFixtureEntry {
    param(
        [Parameter(Mandatory = $true)][System.IO.Compression.ZipArchive]$Archive,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][byte[]]$Bytes
    )

    $entry = $Archive.CreateEntry($Name)
    $stream = $entry.Open()
    try {
        $stream.Write($Bytes, 0, $Bytes.Length)
    }
    finally {
        $stream.Dispose()
    }
}

function New-ApkFixture {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$SourceCommit,
        [Parameter(Mandatory = $true)][byte[]]$ProfBytes,
        [Parameter(Mandatory = $true)][byte[]]$ProfmBytes,
        [switch]$OmitProfm
    )

    $path = Join-Path $temporaryRoot "$Name.apk"
    $archive = [System.IO.Compression.ZipFile]::Open($path, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        Add-ApkFixtureEntry -Archive $archive -Name "assets/dexopt/baseline.prof" -Bytes $ProfBytes
        if (-not $OmitProfm) {
            Add-ApkFixtureEntry -Archive $archive -Name "assets/dexopt/baseline.profm" -Bytes $ProfmBytes
        }
        Add-ApkFixtureEntry -Archive $archive -Name "META-INF/version-control-info.textproto" `
            -Bytes ([System.Text.Encoding]::UTF8.GetBytes("revision: `"$SourceCommit`"`n"))
    }
    finally {
        $archive.Dispose()
    }
    return $path
}

$baselineAcceptance = New-AcceptanceFixture -Name "baseline" -SourceCommit $baselineProfileSource `
    -AppApkSha256 $baselineProfileAppHash -TestApkSha256 $baselineProfileTestHash `
    -ProfileText "HSPLexample/Baseline;->draw()V`nSPLexample/Undo;->run()V"
$candidateAcceptance = New-AcceptanceFixture -Name "candidate" -SourceCommit $candidateProfileSource `
    -AppApkSha256 $candidateProfileAppHash -TestApkSha256 $candidateProfileTestHash `
    -ProfileText "HSPLexample/Candidate;->draw()V`nSPLexample/Undo;->run()V"
$common = @{
    DeviceSerial = "offline-validation"
    ApkPath = "offline-validation.apk"
    ExperimentDirectory = $experimentRoot
    ExperimentId = "offline-protocol-validation"
    BaselineSourceCommit = $baselineCommit
    CandidateSourceCommit = $candidateCommit
    BaselineProductionCommit = $baselineProductionCommit
    CandidateProductionCommit = $candidateCommit
    BaselineProductionTreeSha256 = $baselineProductionTreeHash
    CandidateProductionTreeSha256 = $candidateProductionTreeHash
    BaselineCanvas16SurfaceBounds = "[688,615][1232,1159]"
    BaselineCanvas256SurfaceBounds = "[688,615][1232,1159]"
    CandidateCanvas16SurfaceBounds = "[688,615][1232,1159]"
    CandidateCanvas256SurfaceBounds = "[688,615][1232,1159]"
    BaselineApkSha256 = $baselineHash
    CandidateApkSha256 = $candidateHash
    BaselineProfileGenerationSourceCommit = $baselineProfileSource
    CandidateProfileGenerationSourceCommit = $candidateProfileSource
    BaselineProfileGenerationAppApkSha256 = $baselineProfileAppHash
    CandidateProfileGenerationAppApkSha256 = $candidateProfileAppHash
    BaselineProfileGenerationTestApkSha256 = $baselineProfileTestHash
    CandidateProfileGenerationTestApkSha256 = $candidateProfileTestHash
    BaselineProfileAcceptanceManifestPath = $baselineAcceptance.Path
    CandidateProfileAcceptanceManifestPath = $candidateAcceptance.Path
    BaselineProfileAcceptanceManifestSha256 = $baselineAcceptance.Hash
    CandidateProfileAcceptanceManifestSha256 = $candidateAcceptance.Hash
    BaselineProfilePairManifestSha256 = $baselineAcceptance.PairHash
    CandidateProfilePairManifestSha256 = $candidateAcceptance.PairHash
    BaselineCanonicalProfileSha256 = $baselineAcceptance.CanonicalHash
    CandidateCanonicalProfileSha256 = $candidateAcceptance.CanonicalHash
    BaselinePackagedProfSha256 = $baselineProfHash
    CandidatePackagedProfSha256 = $candidateProfHash
    BaselinePackagedProfmSha256 = $baselineProfmHash
    CandidatePackagedProfmSha256 = $candidateProfmHash
    CandidateHypothesis = "fixed candidate hypothesis"
    ExpectedAffectedCost = "fixed expected affected cost"
    CorrectnessRisk = "fixed correctness risk"
    StopConditions = "fixed stopping conditions"
    Attempt = 1
    ValidateExperimentOnly = $true
}

function Invoke-ExpectedPass {
    param([Parameter(Mandatory = $true)][hashtable]$Arguments)

    & $collector @Arguments | Out-Null
}

function Invoke-ExpectedFailure {
    param([Parameter(Mandatory = $true)][hashtable]$Arguments)

    try {
        & $collector @Arguments | Out-Null
    }
    catch {
        return
    }
    throw "Expected protocol validation to reject the invocation."
}

function Write-State {
    param(
        [Parameter(Mandatory = $true)][string]$Slot,
        [Parameter(Mandatory = $true)][int]$Attempt,
        [Parameter(Mandatory = $true)][string]$Status,
        [Parameter(Mandatory = $true)][string]$Verdict,
        [Parameter(Mandatory = $true)][int]$MeasuredDownCount
    )

    $directory = Join-Path $experimentRoot "$Slot-attempt-$Attempt"
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    [ordered]@{
        schema = "nene-pixel-p4-indexed-frame-experiment-v4"
        experiment_id = "offline-protocol-validation"
        comparison_sequence_index = [int]$Slot.Substring(5, 2)
        attempt = $Attempt
        status = $Status
        verdict = $Verdict
        workload_order = @("canvas16_tap", "canvas256_repeated_diagonal")
        measured_workload_counts = [ordered]@{
            canvas16_tap = $MeasuredDownCount
            canvas256_repeated_diagonal = $MeasuredDownCount
        }
        measured_operation_count = $MeasuredDownCount * 2
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory "run-state.json") -Encoding utf8NoBOM
}

try {
    $exactDexopt = Assert-M2PackageSpeedProfile `
        -DexoptText "[io.github.hideyukimori.nenepixel]`n    arm64: [status=speed-profile]`n[example.decoy]`n    arm64: [status=verify]" `
        -PackageName 'io.github.hideyukimori.nenepixel'
    if ($exactDexopt -match 'example.decoy') {
        throw 'The shared dexopt helper crossed the exact target package boundary.'
    }
    foreach ($invalidDexopt in @(
            "[io.github.hideyukimori.nenepixel.debug]`n    arm64: [status=speed-profile]`n[io.github.hideyukimori.nenepixel]`n    arm64: [status=verify]",
            "[io.github.hideyukimori.nenepixel]`n    arm64: [status=speed-profile]`n[io.github.hideyukimori.nenepixel]`n    arm64: [status=speed-profile]",
            "[example.decoy]`n    arm64: [status=speed-profile]"
        )) {
        $rejected = $false
        try {
            Assert-M2PackageSpeedProfile -DexoptText $invalidDexopt `
                -PackageName 'io.github.hideyukimori.nenepixel' | Out-Null
        }
        catch {
            $rejected = $true
        }
        if (-not $rejected) {
            throw 'The shared dexopt helper accepted an ambiguous or foreign package block.'
        }
    }

    $legacyRoot = Join-Path $temporaryRoot "legacy-v3"
    New-Item -ItemType Directory -Path $legacyRoot | Out-Null
    [ordered]@{ schema = "nene-pixel-m2-frame-experiment-v3" } |
        ConvertTo-Json |
        Set-Content -LiteralPath (Join-Path $legacyRoot "experiment.json") -Encoding utf8NoBOM
    $legacyInvocation = $common.Clone()
    $legacyInvocation.ExperimentDirectory = $legacyRoot
    $legacyInvocation.ExperimentId = "legacy-protocol-validation"
    $legacyInvocation += @{
        Variant = "release-like"
        CompilationMode = "speed-profile"
        SourceCommit = $baselineCommit
        RunKind = "diagnostic"
        CandidateRole = "baseline"
        ComparisonSequenceIndex = 1
        SampleCount = 10
    }
    Invoke-ExpectedFailure -Arguments $legacyInvocation

    $missingAcceptance = $common.Clone()
    $missingAcceptance.BaselineProfileAcceptanceManifestPath = Join-Path $temporaryRoot "missing.json"
    $missingAcceptance.ExperimentDirectory = Join-Path $temporaryRoot "missing-acceptance"
    $missingAcceptance += @{
        Variant = "release-like"
        CompilationMode = "speed-profile"
        SourceCommit = $baselineCommit
        RunKind = "diagnostic"
        CandidateRole = "baseline"
        ComparisonSequenceIndex = 1
        SampleCount = 10
    }
    Invoke-ExpectedFailure -Arguments $missingAcceptance

    $falseAcceptanceEvidence = New-AcceptanceFixture -Name "false" -SourceCommit $baselineProfileSource `
        -AppApkSha256 $baselineProfileAppHash -TestApkSha256 $baselineProfileTestHash `
        -ProfileText "HSPLexample/Baseline;->draw()V`nSPLexample/Undo;->run()V"
    $falseAcceptanceManifest = Get-Content -LiteralPath $falseAcceptanceEvidence.Path -Raw | ConvertFrom-Json
    $falseAcceptanceManifest.status = 'rejected'
    Write-FixtureJson $falseAcceptanceManifest $falseAcceptanceEvidence.Path
    $falseAcceptance = $common.Clone()
    $falseAcceptance.BaselineProfileAcceptanceManifestPath = $falseAcceptanceEvidence.Path
    $falseAcceptance.BaselineProfileAcceptanceManifestSha256 =
        (Get-FileHash -Algorithm SHA256 $falseAcceptanceEvidence.Path).Hash.ToLowerInvariant()
    $falseAcceptance.ExperimentDirectory = Join-Path $temporaryRoot "false-acceptance"
    $falseAcceptance += @{
        Variant = "release-like"
        CompilationMode = "speed-profile"
        SourceCommit = $baselineCommit
        RunKind = "diagnostic"
        CandidateRole = "baseline"
        ComparisonSequenceIndex = 1
        SampleCount = 10
    }
    Invoke-ExpectedFailure -Arguments $falseAcceptance

    $slot1 = $common.Clone()
    $slot1 += @{
        Variant = "release-like"
        CompilationMode = "speed-profile"
        SourceCommit = $baselineCommit
        RunKind = "diagnostic"
        CandidateRole = "baseline"
        ComparisonSequenceIndex = 1
        SampleCount = 10
    }
    $modelValidation = @(& $collector @slot1)
    if (
        $modelValidation.Count -ne 1 -or
        $modelValidation[0].validation -ne "pass" -or
        [double]$modelValidation[0].model_input_to_committed_result_ms -ne 25.0 -or
        [double]$modelValidation[0].model_down_to_committed_result_ms -ne 145.0
    ) {
        throw "The shared operation model did not exclude the intentional preview dwell from committed-result latency."
    }
    $freshManifestPath = Join-Path $experimentRoot "experiment.json"
    $freshManifest = Get-Content -Raw -LiteralPath $freshManifestPath | ConvertFrom-Json
    if (
        $freshManifest.schema -cne "nene-pixel-p4-indexed-frame-experiment-v4" -or
        [int]$freshManifest.maximum_attempts_per_slot -ne 1 -or
        $freshManifest.replacement_rule -cne "none" -or
        (@($freshManifest.workload_order) -join "|") -cne "canvas16_tap|canvas256_repeated_diagonal" -or
        $freshManifest.geometry.id -cne "initial-fit-centered-v1" -or
        $freshManifest.baseline_production_commit -cne $baselineProductionCommit -or
        $freshManifest.candidate_production_commit -cne $candidateCommit -or
        $freshManifest.candidate_measurement_build_commit -cne $candidateCommit
    ) {
        throw "A new v4 experiment did not publish its fixed attempts, workloads, geometry, and production/build identities."
    }

    $separateBaseline = $slot1.Clone()
    $separateBaseline.CandidateSourceCommit = "7" * 40
    $separateBaseline.ExperimentDirectory = Join-Path $temporaryRoot "separate-candidate-build"
    $separateBaseline.ExperimentId = "separate-candidate-build"
    Invoke-ExpectedPass -Arguments $separateBaseline
    $separateStateDirectory = Join-Path $separateBaseline.ExperimentDirectory "slot-01-diagnostic-baseline-attempt-1"
    New-Item -ItemType Directory -Path $separateStateDirectory -Force | Out-Null
    Write-FixtureJson `
        -Path (Join-Path $separateStateDirectory "run-state.json") `
        -Value ([ordered]@{
            schema = "nene-pixel-p4-indexed-frame-experiment-v4"
            experiment_id = $separateBaseline.ExperimentId
            comparison_sequence_index = 1
            attempt = 1
            status = "completed"
            verdict = "inconclusive"
            workload_order = @("canvas16_tap", "canvas256_repeated_diagonal")
            measured_workload_counts = [ordered]@{ canvas16_tap = 10; canvas256_repeated_diagonal = 10 }
            measured_operation_count = 20
        })
    $separateCandidateBuild = $separateBaseline.Clone()
    $separateCandidateBuild.SourceCommit = $separateCandidateBuild.CandidateSourceCommit
    $separateCandidateBuild.CandidateRole = "candidate"
    $separateCandidateBuild.ComparisonSequenceIndex = 2
    Invoke-ExpectedPass -Arguments $separateCandidateBuild

    $baselineWithoutOverlay = $slot1.Clone()
    $baselineWithoutOverlay.BaselineSourceCommit = $baselineProductionCommit
    $baselineWithoutOverlay.SourceCommit = $baselineProductionCommit
    $baselineWithoutOverlay.ExperimentDirectory = Join-Path $temporaryRoot "baseline-without-overlay"
    $baselineWithoutOverlay.ExperimentId = "baseline-without-overlay"
    Invoke-ExpectedFailure -Arguments $baselineWithoutOverlay

    $wrongGeometry = $slot1.Clone()
    $wrongGeometry.BaselineCanvas256SurfaceBounds = "[0,0][1,1]"
    $wrongGeometry.ExperimentDirectory = Join-Path $temporaryRoot "wrong-geometry"
    $wrongGeometry.ExperimentId = "wrong-geometry"
    Invoke-ExpectedPass -Arguments $wrongGeometry
    $wrongGeometryManifest = Get-Content -Raw -LiteralPath (Join-Path $wrongGeometry.ExperimentDirectory "experiment.json") | ConvertFrom-Json
    if ($wrongGeometryManifest.geometry.baseline.canvas256_repeated_diagonal -cne "[0,0][1,1]") {
        throw "The v4 manifest did not bind the supplied per-role/per-family surface geometry."
    }

    $historicalRoot = Join-Path $temporaryRoot "historical-v3-max-two"
    New-Item -ItemType Directory -Path $historicalRoot | Out-Null
    $historicalManifestPath = Join-Path $historicalRoot "experiment.json"
    $historicalManifest = Get-Content -Raw -LiteralPath $freshManifestPath | ConvertFrom-Json
    $historicalManifest.schema = "nene-pixel-m2-frame-experiment-v3"
    $historicalManifest.maximum_attempts_per_slot = 2
    $historicalManifest.replacement_rule =
        "attempt 2 only after attempt 1 is invalid before the first measured DOWN"
    Write-FixtureJson $historicalManifest $historicalManifestPath
    $historicalManifestHash =
        (Get-FileHash -LiteralPath $historicalManifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $historicalRead = $slot1.Clone()
    $historicalRead.ExperimentDirectory = $historicalRoot
    Invoke-ExpectedFailure -Arguments $historicalRead
    if (
        (Get-FileHash -LiteralPath $historicalManifestPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne
            $historicalManifestHash -or
        @(Get-ChildItem -LiteralPath $historicalRoot).Count -ne 1
    ) {
        throw "Rejected historical v3 validation changed the retained artifact."
    }

    foreach ($contradictoryPolicy in @(
            [ordered]@{
                maximum_attempts_per_slot = 1
                replacement_rule =
                    "attempt 2 only after attempt 1 is invalid before the first measured DOWN"
            },
            [ordered]@{
                maximum_attempts_per_slot = 2
                replacement_rule = "none"
            },
            [ordered]@{
                maximum_attempts_per_slot = 3
                replacement_rule = "none"
            }
        )) {
        $contradictoryRoot = Join-Path $temporaryRoot (
            "contradictory-attempt-policy-" + [guid]::NewGuid().ToString("N")
        )
        New-Item -ItemType Directory -Path $contradictoryRoot | Out-Null
        $contradictoryManifest = Get-Content -Raw -LiteralPath $freshManifestPath | ConvertFrom-Json
        $contradictoryManifest.maximum_attempts_per_slot = $contradictoryPolicy.maximum_attempts_per_slot
        $contradictoryManifest.replacement_rule = $contradictoryPolicy.replacement_rule
        Write-FixtureJson $contradictoryManifest (Join-Path $contradictoryRoot "experiment.json")
        $contradictoryRead = $slot1.Clone()
        $contradictoryRead.ExperimentDirectory = $contradictoryRoot
        Invoke-ExpectedFailure -Arguments $contradictoryRead
    }

    $wrongVariant = $slot1.Clone()
    $wrongVariant.Variant = "debug"
    $wrongVariant.CompilationMode = "speed"
    $wrongVariant.ExperimentDirectory = Join-Path $temporaryRoot "wrong-variant"
    Invoke-ExpectedFailure -Arguments $wrongVariant

    $wrongCompilation = $slot1.Clone()
    $wrongCompilation.CompilationMode = "speed"
    $wrongCompilation.ExperimentDirectory = Join-Path $temporaryRoot "wrong-compilation"
    Invoke-ExpectedFailure -Arguments $wrongCompilation

    $profBytes = [System.Text.Encoding]::UTF8.GetBytes("compiled-profile")
    $profmBytes = [System.Text.Encoding]::UTF8.GetBytes("compiled-profile-metadata")
    $artifactApk = New-ApkFixture -Name "artifact-pass" -SourceCommit $candidateCommit `
        -ProfBytes $profBytes -ProfmBytes $profmBytes
    $artifact = $common.Clone()
    $artifact.Remove("ValidateExperimentOnly")
    $artifact.Variant = "release-like"
    $artifact.CompilationMode = "speed-profile"
    $artifact.SourceCommit = $candidateCommit
    $artifact.ApkPath = $artifactApk
    $artifact.CandidateApkSha256 = (Get-FileHash -Algorithm SHA256 $artifactApk).Hash.ToLowerInvariant()
    $artifact.CandidatePackagedProfSha256 = Get-BytesSha256 -Bytes $profBytes
    $artifact.CandidatePackagedProfmSha256 = Get-BytesSha256 -Bytes $profmBytes
    $artifact.ExperimentDirectory = Join-Path $temporaryRoot "artifact-pass-experiment"
    $artifact.ExperimentId = "artifact-pass-validation"
    $artifact.RunKind = "diagnostic"
    $artifact.CandidateRole = "candidate"
    $artifact.ComparisonSequenceIndex = 2
    $artifact.SampleCount = 10
    $artifact.ValidateArtifactOnly = $true
    Invoke-ExpectedPass -Arguments $artifact
    if (Test-Path -LiteralPath $artifact.ExperimentDirectory) {
        throw 'Artifact-only candidate validation must not create an experiment directory.'
    }

    $preDeviceRepository = Join-Path $temporaryRoot 'pre-device-repository'
    New-Item -ItemType Directory -Path $preDeviceRepository | Out-Null
    & git -C $preDeviceRepository init --quiet
    & git -C $preDeviceRepository config user.name 'NENE fixture'
    & git -C $preDeviceRepository config user.email 'fixture@example.invalid'
    & git -C $preDeviceRepository commit --allow-empty --quiet -m 'pre-device fixture'
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to create the clean source repository for pre-device validation.'
    }
    $preDeviceSource = (& git -C $preDeviceRepository rev-parse HEAD).Trim()
    $preDeviceApk = New-ApkFixture -Name 'pre-device-pass' -SourceCommit $preDeviceSource `
        -ProfBytes $profBytes -ProfmBytes $profmBytes
    $preDevice = $artifact.Clone()
    $preDevice.Remove('ValidateArtifactOnly')
    $preDevice.SourceCommit = $preDeviceSource
    $preDevice.ApkPath = $preDeviceApk
    $preDeviceApkSha256 = (Get-FileHash -Algorithm SHA256 $preDeviceApk).Hash.ToLowerInvariant()
    $preDeviceApkBytes = (Get-Item -LiteralPath $preDeviceApk).Length
    $preDevice.ExperimentDirectory = Join-Path $temporaryRoot 'pre-device-experiment'
    $preDevice.ExperimentId = 'pre-device-validation'
    $preDevice.ComparisonSequenceIndex = 1
    $preDevice.CandidateRole = 'baseline'
    $preDevice.BaselineSourceCommit = $preDeviceSource
    $preDevice.BaselineApkSha256 = $preDeviceApkSha256
    $preDevice.CandidateApkSha256 = 'd' * 64
    $preDevice.BaselinePackagedProfSha256 = $preDevice.CandidatePackagedProfSha256
    $preDevice.BaselinePackagedProfmSha256 = $preDevice.CandidatePackagedProfmSha256
    $global:neneFrameFixtureState = [pscustomobject]@{
        ExpectedApk = (Resolve-Path -LiteralPath $preDeviceApk).Path
        InstallSeen = $false
        Committed = $false
        RedoAvailable = $false
        CanvasEdge = 16
        MenuOpen = $false
        DialogOpen = $false
        FocusField = ""
        WidthInput = "16"
        HeightInput = "16"
        MotionCount = 0
        MeasuredStarted = $false
        Frame = 0
        FrameCaptureCount = 0
        CurrentPhaseFrame = 0
        Mode = 'success'
        RotationMode = 'free'
        NumericRotation = 0
        CurrentRotation = 0
        PinSeen = $false
        Commands = [System.Collections.Generic.List[string]]::new()
        CollisionPath = $null
        StayAwake = '0'
    }
    function global:Start-Sleep {
        param([int]$Milliseconds, [int]$Seconds)
    }
    function global:Get-NeneFrameFixtureUi {
        $dirty = if ($global:neneFrameFixtureState.Committed) { '未保存' } else { '保存済み' }
        $dirtyIdentity = if ($global:neneFrameFixtureState.Committed) { 'editor_dirty_document' } else { 'editor_clean_document' }
        $undo = $global:neneFrameFixtureState.Committed.ToString().ToLowerInvariant()
        $redo = $global:neneFrameFixtureState.RedoAvailable.ToString().ToLowerInvariant()
        $edge = $global:neneFrameFixtureState.CanvasEdge
        $rotation =
            if (
                $global:neneFrameFixtureState.MeasuredStarted -and
                $global:neneFrameFixtureState.Committed -and
                $global:neneFrameFixtureState.Mode -eq 'rotation-mismatch'
            ) { 0 } else { 1 }
        $canvasBounds =
            if (
                $global:neneFrameFixtureState.MeasuredStarted -and
                $global:neneFrameFixtureState.Committed -and
                $global:neneFrameFixtureState.Mode -eq 'geometry-drift'
            ) { '[689,615][1232,1159]' } else { '[688,615][1232,1159]' }
        $fileMenu =
            if ($global:neneFrameFixtureState.MenuOpen) {
                '<node resource-id="editor_new_document" bounds="[180,80][300,140]" />'
            } else { '' }
        $dialog =
            if ($global:neneFrameFixtureState.DialogOpen) {
                @"
<node resource-id="editor_create_document_title" bounds="[400,250][800,300]" />
<node resource-id="editor_document_width" text="$($global:neneFrameFixtureState.WidthInput)" bounds="[450,320][600,400]" />
<node resource-id="editor_document_height" text="$($global:neneFrameFixtureState.HeightInput)" bounds="[650,320][800,400]" />
<node resource-id="editor_create" bounds="[550,450][650,510]" />
"@
            } else { '' }
        return @"
<hierarchy rotation="$rotation"><node bounds="[0,0][1920,1200]">
<node resource-id="$dirtyIdentity" content-desc="文書の状態" text="$dirty" />
<node enabled="$undo" bounds="[823,506][953,588]"><node resource-id="editor_undo" text="元に戻す" /></node>
<node enabled="$redo"><node resource-id="editor_redo" text="やり直す" /></node>
<node checked="true"><node resource-id="editor_pencil_tool" content-desc="鉛筆" /></node>
<node resource-id="editor_file" bounds="[80,80][160,140]" />
$fileMenu
$dialog
<node resource-id="editor_canvas_${edge}_${edge}" content-desc="$edge by $edge pixel canvas" bounds="$canvasBounds" />
</node></hierarchy>
"@
    }
    $frameFixtureGlobalsOwned = $true
    function global:adb {
        param(
            [string]$s,
            [Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArguments
        )

        $global:LASTEXITCODE = 0
        if ($s -ne 'offline-validation') {
            throw 'The end-to-end fixture received an unexpected device route.'
        }
        $command = $AdbArguments -join ' '
        $global:neneFrameFixtureState.Commands.Add($command)
        switch -Regex ($command) {
            '^shell getprop ro\.kernel\.qemu$' { '0'; return }
            '^shell getprop ro\.product\.manufacturer$' { 'ALLDOCUBE'; return }
            '^shell getprop ro\.product\.model$' { 'iPlay80miniPro'; return }
            '^shell getprop ro\.product\.name$' { 'iPlay80miniPro'; return }
            '^shell getprop ro\.product\.device$' { 'T830'; return }
            '^shell getprop ro\.build\.version\.sdk$' { '36'; return }
            '^shell getprop ro\.build\.fingerprint$' { 'fixture/fingerprint'; return }
            '^shell getprop ro\.build\.version\.security_patch$' { '2026-09-01'; return }
            '^shell settings get global stay_on_while_plugged_in$' {
                $global:neneFrameFixtureState.StayAwake
                return
            }
            '^shell settings delete global stay_on_while_plugged_in$' { return }
            '^shell settings get system user_rotation$' { "$($global:neneFrameFixtureState.NumericRotation)"; return }
            '^shell dumpsys window$' {
                $mode = if ($global:neneFrameFixtureState.RotationMode -eq 'locked') { 'USER_ROTATION_LOCKED' } else { 'USER_ROTATION_FREE' }
                $reportedDegrees = $global:neneFrameFixtureState.NumericRotation * 90
                $logicalWidth = if ($global:neneFrameFixtureState.CurrentRotation % 2 -eq 0) { 1200 } else { 1920 }
                $logicalHeight = if ($global:neneFrameFixtureState.CurrentRotation % 2 -eq 0) { 1920 } else { 1200 }
                "  mRotation=$($global:neneFrameFixtureState.CurrentRotation) mDeferredRotationPauseCount=0"
                "    mUserRotationMode=$mode mUserRotation=ROTATION_$reportedDegrees"
                "  DisplayFrames w=$logicalWidth h=$logicalHeight r=$($global:neneFrameFixtureState.CurrentRotation)"
                return
            }
            '^shell wm user-rotation lock ([0-3])$' {
                $rotation = [int]([regex]::Match($command, '([0-3])$').Groups[1].Value)
                $global:neneFrameFixtureState.RotationMode = 'locked'
                $global:neneFrameFixtureState.NumericRotation = $rotation
                $global:neneFrameFixtureState.CurrentRotation = $rotation
                $global:neneFrameFixtureState.PinSeen = $true
                return
            }
            '^shell wm user-rotation free$' {
                if ($global:neneFrameFixtureState.Mode -notin @('restore-failure', 'semantic-and-restore-failure')) {
                    $global:neneFrameFixtureState.RotationMode = 'free'
                }
                return
            }
            '^shell wm size$' { 'Physical size: 1200x1920'; return }
            '^shell dumpsys display$' {
                'DisplayDeviceInfo{fixture, 1200 x 1920, modeId 1, supportedModes [{id=1, width=1200, height=1920, fps=90.0}]}'
                return
            }
            '^shell dumpsys thermalservice$' { 'Thermal Status: 0'; return }
            '^shell settings get global low_power$' { '0'; return }
            '^shell dumpsys power$' { 'mWakefulness=Awake'; return }
            '^shell dumpsys battery$' { "USB powered: true`n  level: 100"; return }
            '^install -r -d ' {
                $actualPath = $AdbArguments[3]
                if (-not [string]::Equals(
                        $actualPath,
                        $global:neneFrameFixtureState.ExpectedApk,
                        [System.StringComparison]::OrdinalIgnoreCase
                    )) {
                    throw "Pre-device installation received the wrong APK path: $actualPath"
                }
                $global:neneFrameFixtureState.InstallSeen = $true
                return 'Success'
            }
            '^shell am broadcast ' { 'Broadcast completed: result=1'; return }
            '^shell cmd package compile ' { 'Success'; return }
            '^shell dumpsys package dexopt$' {
                "[io.github.hideyukimori.nenepixel]`n    arm64: [status=speed-profile]`n[example.decoy]`n    arm64: [status=verify]"
                return
            }
            '^shell svc power stayon usb$' { return }
            '^shell cmd input keyevent WAKEUP$' { return }
            '^shell am force-stop ' { return }
            '^shell am start ' { return }
            '^shell cmd input tap ' {
                $x = [int]$AdbArguments[4]
                if ($x -eq 120) {
                    $global:neneFrameFixtureState.MenuOpen = $true
                }
                elseif ($x -eq 240 -and $global:neneFrameFixtureState.MenuOpen) {
                    $global:neneFrameFixtureState.MenuOpen = $false
                    $global:neneFrameFixtureState.DialogOpen = $true
                }
                elseif ($x -eq 525 -and $global:neneFrameFixtureState.DialogOpen) {
                    $global:neneFrameFixtureState.FocusField = 'width'
                }
                elseif ($x -eq 725 -and $global:neneFrameFixtureState.DialogOpen) {
                    $global:neneFrameFixtureState.FocusField = 'height'
                }
                elseif ($x -eq 600 -and $global:neneFrameFixtureState.DialogOpen) {
                    if (
                        $global:neneFrameFixtureState.WidthInput -cne $global:neneFrameFixtureState.HeightInput -or
                        $global:neneFrameFixtureState.WidthInput -notin @('16', '256')
                    ) {
                        throw 'The fixture Create control received dimensions outside the fixed 16 or 256 square families.'
                    }
                    $global:neneFrameFixtureState.CanvasEdge = [int]$global:neneFrameFixtureState.WidthInput
                    $global:neneFrameFixtureState.DialogOpen = $false
                    $global:neneFrameFixtureState.FocusField = ''
                    $global:neneFrameFixtureState.Committed = $false
                    $global:neneFrameFixtureState.RedoAvailable = $false
                }
                elseif ($x -eq 888) {
                    $global:neneFrameFixtureState.Committed = $false
                    $global:neneFrameFixtureState.RedoAvailable = $true
                }
                else {
                    throw "The end-to-end fixture received an unexpected tap: $command"
                }
                return
            }
            '^shell cmd input keyevent KEYCODE_MOVE_END$' { return }
            '^shell cmd input keyevent KEYCODE_DEL$' {
                $field = $global:neneFrameFixtureState.FocusField
                if ($field -eq 'width') {
                    $value = $global:neneFrameFixtureState.WidthInput
                    $global:neneFrameFixtureState.WidthInput = if ($value.Length -gt 0) { $value.Substring(0, $value.Length - 1) } else { '' }
                }
                elseif ($field -eq 'height') {
                    $value = $global:neneFrameFixtureState.HeightInput
                    $global:neneFrameFixtureState.HeightInput = if ($value.Length -gt 0) { $value.Substring(0, $value.Length - 1) } else { '' }
                }
                else {
                    throw 'The fixture received DEL without a focused bounded dimension field.'
                }
                return
            }
            '^shell cmd input text (\d{1,3})$' {
                $typed = [regex]::Match($command, '(\d{1,3})$').Groups[1].Value
                $field = $global:neneFrameFixtureState.FocusField
                if ($field -eq 'width') {
                    $global:neneFrameFixtureState.WidthInput += $typed
                }
                elseif ($field -eq 'height') {
                    $global:neneFrameFixtureState.HeightInput += $typed
                }
                else {
                    throw 'The fixture received text without a focused bounded dimension field.'
                }
                return
            }
            '^shell cmd input motionevent ' {
                $global:neneFrameFixtureState.MotionCount += 1
                if ($AdbArguments[4] -eq 'UP') {
                    $global:neneFrameFixtureState.Committed = $true
                    $global:neneFrameFixtureState.RedoAvailable = $false
                }
                return
            }
            '^shell uiautomator dump ' { 'UI dumped'; return }
            '^shell cat ' {
                if (
                    $global:neneFrameFixtureState.MeasuredStarted -and
                    $global:neneFrameFixtureState.Committed -and
                    $global:neneFrameFixtureState.Mode -eq 'malformed'
                ) {
                    '<malformed'
                    return
                }
                if (
                    $global:neneFrameFixtureState.MeasuredStarted -and
                    $global:neneFrameFixtureState.Committed -and
                    $global:neneFrameFixtureState.Mode -eq 'persistence-failure'
                ) {
                    [System.IO.File]::WriteAllText(
                        $global:neneFrameFixtureState.CollisionPath,
                        'fixture collision',
                        [System.Text.UTF8Encoding]::new($false)
                    )
                }
                $ui = Get-NeneFrameFixtureUi
                if (
                    $global:neneFrameFixtureState.MeasuredStarted -and
                    $global:neneFrameFixtureState.Committed -and
                    $global:neneFrameFixtureState.Mode -in @('semantic-mismatch', 'persistence-failure', 'semantic-and-restore-failure')
                ) {
                    $ui = $ui.Replace('resource-id="editor_dirty_document"', 'resource-id="editor_clean_document"')
                }
                $ui
                return
            }
            '^shell dumpsys gfxinfo .* reset$' {
                $global:neneFrameFixtureState.MeasuredStarted = $true
                $global:neneFrameFixtureState.CurrentPhaseFrame = 0
                'reset'
                return
            }
            '^shell dumpsys gfxinfo .* framestats$' {
                $global:neneFrameFixtureState.FrameCaptureCount += 1
                if ($global:neneFrameFixtureState.CurrentPhaseFrame -eq 0) {
                    $global:neneFrameFixtureState.Frame += 1
                    $global:neneFrameFixtureState.CurrentPhaseFrame = $global:neneFrameFixtureState.Frame
                }
                $frame = [long]$global:neneFrameFixtureState.CurrentPhaseFrame
                if (
                    $global:neneFrameFixtureState.Mode -eq 'late-commit' -and
                    $global:neneFrameFixtureState.Committed -and
                    $global:neneFrameFixtureState.FrameCaptureCount % 3 -eq 0
                ) {
                    $frame += 1
                }
                $base = $frame * 100000000L
                $header = 'Flags,FrameTimelineVsyncId,IntendedVsync,FrameStartTime,HandleInputStart,AnimationStart,PerformTraversalsStart,DrawStart,SyncQueued,SyncStart,IssueDrawCommandsStart,SwapBuffers,SwapBuffersCompleted,FrameDeadline,FrameCompleted,DisplayPresentTime'
                $row = @(
                    $(if ($global:neneFrameFixtureState.Mode -eq 'flagged-frame') { 1 } else { 0 }),
                    $(if ($global:neneFrameFixtureState.Mode -eq 'duplicate-frame-id') { 1 } else { $frame }),
                    $base,
                    ($base + 1000000L),
                    ($base + 2000000L),
                    ($base + 3000000L),
                    ($base + 4000000L),
                    ($base + 5000000L),
                    ($base + 6000000L),
                    ($base + 6500000L),
                    ($base + 7000000L),
                    ($base + 8000000L),
                    ($base + 9000000L),
                    ($base + 16666667L),
                    $(if (
                            $global:neneFrameFixtureState.Mode -eq 'first-family-gross' -and
                            $global:neneFrameFixtureState.CanvasEdge -eq 16
                        ) { $base + 60000000L } else { $base + 10000000L }),
                    ($base + 12000000L)
                ) -join ','
                $reportedTotal = if ($global:neneFrameFixtureState.Mode -eq 'ring-loss') { 2 } else { 1 }
                $profileRows = if ($global:neneFrameFixtureState.Mode -eq 'zero-rows') { @() } else { @($row) }
                if ($global:neneFrameFixtureState.Mode -eq 'duplicate-header') {
                    return @(
                        'Applications Graphics Acceleration Info:',
                        'Total frames rendered: 1',
                        'Janky frames: 0',
                        'Number Frame deadline missed: 0',
                        '---PROFILEDATA---',
                        $header,
                        $row,
                        '---PROFILEDATA---',
                        $header,
                        $row,
                        '---PROFILEDATA---',
                        ''
                    )
                }
                return @(
                    'Applications Graphics Acceleration Info:',
                    "Total frames rendered: $reportedTotal",
                    'Janky frames: 0',
                    'Number Frame deadline missed: 0',
                    '---PROFILEDATA---',
                    $header,
                    $profileRows,
                    '---PROFILEDATA---',
                    ''
                )
            }
            '^pull ' {
                $localPath = $AdbArguments[2]
                if ($localPath.EndsWith('.xml')) {
                    [System.IO.File]::WriteAllText(
                        $localPath,
                        (Get-NeneFrameFixtureUi),
                        [System.Text.UTF8Encoding]::new($false)
                    )
                }
                elseif ($localPath.EndsWith('.png')) {
                    [System.IO.File]::WriteAllBytes($localPath, [byte[]]@(1, 2, 3, 4))
                }
                else {
                    throw "The end-to-end fixture received an unexpected pull: $command"
                }
                return 'pulled'
            }
            '^logcat -c$' { return }
            '^logcat -d -v threadtime$' {
                if (
                    $global:neneFrameFixtureState.Mode -eq 'fatal-after-first-family' -and
                    $global:neneFrameFixtureState.CanvasEdge -eq 16
                ) {
                    'FATAL EXCEPTION: fixture'
                }
                else {
                    'fixture: no app fatal events'
                }
                return
            }
            '^shell screencap -p ' { return }
            '^shell settings put global stay_on_while_plugged_in ' { return }
            '^shell rm -f ' { return }
            default { throw "The host end-to-end fixture received an unexpected adb command: $command" }
        }
    }
    $endToEndOutput = @()
    Push-Location $preDeviceRepository
    try {
        $endToEndOutput = @(& $collector @preDevice)
    }
    finally {
        Pop-Location
    }
    if (
        -not $global:neneFrameFixtureState.InstallSeen -or
        $global:neneFrameFixtureState.Frame -ne 40 -or
        $global:neneFrameFixtureState.FrameCaptureCount -ne 60 -or
        $global:neneFrameFixtureState.MotionCount -ne 300
    ) {
        throw 'The end-to-end path did not install the exact verified APK or retain the fixed two-family event and phase populations.'
    }
    $successCommands = @($global:neneFrameFixtureState.Commands)
    if (@($successCommands | Where-Object { $_ -like 'shell pm clear *' }).Count -ne 0) {
        throw 'The actual-app collector erased package user data before measurement.'
    }
    $actualMotionCommands = @($successCommands | Where-Object { $_ -like 'shell cmd input motionevent *' })
    $expectedMotionCommands = [System.Collections.Generic.List[string]]::new()
    foreach ($operation in 1..15) {
        $expectedMotionCommands.Add('shell cmd input motionevent DOWN 705 632')
        $expectedMotionCommands.Add('shell cmd input motionevent UP 705 632')
    }
    foreach ($operation in 1..15) {
        $expectedMotionCommands.Add('shell cmd input motionevent DOWN 689.0625 616.0625')
        foreach ($move in 1..16) {
            if ($move % 2 -eq 1) {
                $expectedMotionCommands.Add('shell cmd input motionevent MOVE 1230.9375 1157.9375')
            }
            else {
                $expectedMotionCommands.Add('shell cmd input motionevent MOVE 689.0625 616.0625')
            }
        }
        $expectedMotionCommands.Add('shell cmd input motionevent UP 689.0625 616.0625')
    }
    if (
        $actualMotionCommands.Count -ne $expectedMotionCommands.Count -or
        @(Compare-Object @($expectedMotionCommands) $actualMotionCommands -SyncWindow 0).Count -ne 0
    ) {
        throw 'The end-to-end path changed the ordered tap or repeated-diagonal motion-event families.'
    }
    foreach ($requiredUiCommand in @(
            'shell cmd input tap 120 110',
            'shell cmd input tap 240 110',
            'shell cmd input tap 525 360',
            'shell cmd input tap 725 360',
            'shell cmd input tap 600 480'
        )) {
        if (@($successCommands | Where-Object { $_ -ceq $requiredUiCommand }).Count -ne 2) {
            throw "The end-to-end path did not use the exact actual-app New-document control: $requiredUiCommand"
        }
    }
    if (
        @($successCommands | Where-Object { $_ -ceq 'shell cmd input keyevent KEYCODE_MOVE_END' }).Count -ne 4 -or
        @($successCommands | Where-Object { $_ -ceq 'shell cmd input keyevent KEYCODE_DEL' }).Count -ne 12 -or
        @($successCommands | Where-Object { $_ -ceq 'shell cmd input text 16' }).Count -ne 2 -or
        @($successCommands | Where-Object { $_ -ceq 'shell cmd input text 256' }).Count -ne 2
    ) {
        throw 'The end-to-end path changed the bounded clear-and-type dimension entry sequence.'
    }
    $preDeviceState = Get-Content -Raw -LiteralPath (
        Join-Path $preDevice.ExperimentDirectory 'slot-01-diagnostic-baseline-attempt-1/run-state.json'
    ) | ConvertFrom-Json
    $preDeviceMetadata = Get-Content -LiteralPath (
        Join-Path $preDevice.ExperimentDirectory 'slot-01-diagnostic-baseline-attempt-1/metadata.txt'
    )
    $preDeviceFrames = @(Import-Csv -LiteralPath (
        Join-Path $preDevice.ExperimentDirectory 'slot-01-diagnostic-baseline-attempt-1/frames.csv'
    ))
    $preDeviceSamples = @(Import-Csv -LiteralPath (
        Join-Path $preDevice.ExperimentDirectory 'slot-01-diagnostic-baseline-attempt-1/samples.csv'
    ))
    if (
        $preDeviceState.status -ne 'completed' -or
        $preDeviceState.verdict -ne 'inconclusive' -or
        (@($preDeviceState.workload_order) -join '|') -cne 'canvas16_tap|canvas256_repeated_diagonal' -or
        [int]$preDeviceState.measured_workload_counts.canvas16_tap -ne 10 -or
        [int]$preDeviceState.measured_workload_counts.canvas256_repeated_diagonal -ne 10 -or
        [int]$preDeviceState.measured_operation_count -ne 20 -or
        "apk_embedded_source_commit=$preDeviceSource" -notin $preDeviceMetadata -or
        "apk_bytes=$preDeviceApkBytes" -notin $preDeviceMetadata -or
        "apk_sha256=$preDeviceApkSha256" -notin $preDeviceMetadata -or
        "packaged_prof_sha256=$($preDevice.BaselinePackagedProfSha256)" -notin $preDeviceMetadata -or
        "packaged_profm_sha256=$($preDevice.BaselinePackagedProfmSha256)" -notin $preDeviceMetadata -or
        'samples_per_workload=10' -notin $preDeviceMetadata -or
        'measured_operation_count=20' -notin $preDeviceMetadata -or
        'measured_canvas16_tap=10' -notin $preDeviceMetadata -or
        'measured_canvas256_repeated_diagonal=10' -notin $preDeviceMetadata -or
        'geometry_id=initial-fit-centered-v1' -notin $preDeviceMetadata -or
        'raw_frame_rows=40' -notin $preDeviceMetadata -or
        'limitation=app-issued gfxinfo framestats rows only; no strict SurfaceFlinger physical-present correlation; diagnostic results are never acceptance PASS' -notin $preDeviceMetadata -or
        $preDeviceFrames.Count -ne 40 -or
        $preDeviceSamples.Count -ne 20 -or
        'status=inconclusive' -notin $endToEndOutput
    ) {
        throw 'The host end-to-end fixture did not publish the typed artifact identity and final diagnostic verdict.'
    }
    foreach ($workload in @('canvas16_tap', 'canvas256_repeated_diagonal')) {
        $workloadFrames = @($preDeviceFrames | Where-Object { $_.workload -ceq $workload })
        $workloadSamples = @($preDeviceSamples | Where-Object { $_.workload -ceq $workload })
        if (
            $workloadFrames.Count -ne 20 -or
            $workloadSamples.Count -ne 10 -or
            (@($workloadSamples.sample_index) -join '|') -cne ((1..10) -join '|') -or
            @($workloadSamples | Where-Object {
                    $_.operation -cne "$workload#$($_.sample_index)" -or
                    [int]$_.preview_raw_frame_count -ne 1 -or
                    [int]$_.preview_valid_frame_count -ne 1 -or
                    [int]$_.commit_raw_frame_count -ne 1 -or
                    [int]$_.commit_valid_frame_count -ne 1
                }).Count -ne 0
        ) {
            throw "The host end-to-end fixture did not retain the exact $workload row population and order."
        }
    }
    $initialRotation = Get-Content -LiteralPath (Join-Path $preDevice.ExperimentDirectory 'slot-01-diagnostic-baseline-attempt-1/rotation-original.txt')
    $restoredRotation = Get-Content -LiteralPath (Join-Path $preDevice.ExperimentDirectory 'slot-01-diagnostic-baseline-attempt-1/rotation-restore.txt')
    if (
        'mode=free' -notin $initialRotation -or
        'numeric_user_rotation=0' -notin $initialRotation -or
        'mode=free' -notin $restoredRotation -or
        'numeric_user_rotation=0' -notin $restoredRotation -or
        'command=wm user-rotation lock 0' -notin $restoredRotation -or
        'command=wm user-rotation free' -notin $restoredRotation -or
        'restore_verified=true' -notin $restoredRotation
    ) {
        throw 'The normal fixture did not capture before pin and restore the original free rotation state.'
    }
    $originalRotationReadIndex = $global:neneFrameFixtureState.Commands.IndexOf('shell settings get system user_rotation')
    $pinIndex = $global:neneFrameFixtureState.Commands.IndexOf('shell wm user-rotation lock 1')
    if ($originalRotationReadIndex -lt 0 -or $pinIndex -le $originalRotationReadIndex) {
        throw 'The normal fixture did not capture the original rotation before pinning it.'
    }

    $wrongDeviceGeometry = $preDevice.Clone()
    $wrongDeviceGeometry.ExperimentDirectory = Join-Path $temporaryRoot 'wrong-device-geometry'
    $wrongDeviceGeometry.ExperimentId = 'wrong-device-geometry'
    $wrongDeviceGeometry.BaselineCanvas16SurfaceBounds = '[687,615][1232,1159]'
    $global:neneFrameFixtureState.InstallSeen = $false
    $global:neneFrameFixtureState.Frame = 0
    $global:neneFrameFixtureState.FrameCaptureCount = 0
    $global:neneFrameFixtureState.CurrentPhaseFrame = 0
    $global:neneFrameFixtureState.MotionCount = 0
    $global:neneFrameFixtureState.MeasuredStarted = $false
    $global:neneFrameFixtureState.Mode = 'success'
    $global:neneFrameFixtureState.RotationMode = 'free'
    $global:neneFrameFixtureState.NumericRotation = 0
    $global:neneFrameFixtureState.CurrentRotation = 0
    $global:neneFrameFixtureState.Commands = [System.Collections.Generic.List[string]]::new()
    $wrongDeviceGeometryError = $null
    Push-Location $preDeviceRepository
    try {
        & $collector @wrongDeviceGeometry | Out-Null
    }
    catch {
        $wrongDeviceGeometryError = $_
    }
    finally {
        Pop-Location
    }
    $wrongDeviceGeometryState = Get-Content -Raw -LiteralPath (
        Join-Path $wrongDeviceGeometry.ExperimentDirectory 'slot-01-diagnostic-baseline-attempt-1/run-state.json'
    ) | ConvertFrom-Json
    if (
        $null -eq $wrongDeviceGeometryError -or
        $wrongDeviceGeometryError.Exception.Message -cne 'The editor UI or pinned canvas16_tap surface geometry drifted from preflight.' -or
        $wrongDeviceGeometryState.status -cne 'invalid-before-samples' -or
        [int]$wrongDeviceGeometryState.measured_operation_count -ne 0
    ) {
        throw 'The device fixture accepted mismatched pinned per-role/per-family surface geometry.'
    }

    $malformedFixtureError = $null
    try {
        [xml]$null = '<malformed'
    }
    catch {
        $malformedFixtureError = $_
    }
    if ($null -eq $malformedFixtureError) {
        throw 'Malformed XML fixture did not establish its native parser error.'
    }
    $failureCommandLedgers = @{}
    foreach ($failureCase in @(
            [pscustomobject]@{
                Name = 'semantic-mismatch'
                ExpectedError = "The editor UI must expose exactly one 'editor_dirty_document' resource identity."
                ExpectedRaw = $null
                PersistenceFailure = $false
            },
            [pscustomobject]@{
                Name = 'malformed'
                ExpectedError = $malformedFixtureError.Exception.Message
                ExpectedRaw = '<malformed'
                PersistenceFailure = $false
            },
            [pscustomobject]@{
                Name = 'persistence-failure'
                ExpectedError = "The editor UI must expose exactly one 'editor_dirty_document' resource identity."
                ExpectedRaw = $null
                PersistenceFailure = $true
            },
            [pscustomobject]@{
                Name = 'rotation-mismatch'
                ExpectedError = 'The editor root geometry or rotation drifted from the pinned physical contract.'
                ExpectedRaw = $null
                PersistenceFailure = $false
            },
            [pscustomobject]@{
                Name = 'geometry-drift'
                ExpectedError = 'The editor UI or pinned canvas16_tap surface geometry drifted from preflight.'
                ExpectedRaw = $null
                PersistenceFailure = $false
            },
            [pscustomobject]@{
                Name = 'semantic-and-restore-failure'
                ExpectedError = "The editor UI must expose exactly one 'editor_dirty_document' resource identity."
                ExpectedRaw = $null
                PersistenceFailure = $false
            }
        )) {
        $failure = $preDevice.Clone()
        $failure.ExperimentDirectory = Join-Path $temporaryRoot "failure-evidence-$($failureCase.Name)"
        $failure.ExperimentId = "failure-evidence-$($failureCase.Name)"
        $slotDirectory = Join-Path $failure.ExperimentDirectory 'slot-01-diagnostic-baseline-attempt-1'
        $global:neneFrameFixtureState.InstallSeen = $false
        $global:neneFrameFixtureState.Committed = $false
        $global:neneFrameFixtureState.Frame = 0
        $global:neneFrameFixtureState.FrameCaptureCount = 0
        $global:neneFrameFixtureState.CurrentPhaseFrame = 0
        $global:neneFrameFixtureState.MotionCount = 0
        $global:neneFrameFixtureState.MeasuredStarted = $false
        $global:neneFrameFixtureState.Mode = $failureCase.Name
        $global:neneFrameFixtureState.RotationMode = 'free'
        $global:neneFrameFixtureState.NumericRotation = 0
        $global:neneFrameFixtureState.CurrentRotation = 0
        $global:neneFrameFixtureState.PinSeen = $false
        $global:neneFrameFixtureState.Commands = [System.Collections.Generic.List[string]]::new()
        $global:neneFrameFixtureState.CollisionPath =
            Join-Path $slotDirectory 'raw/failure-latest-committed-result.xml'
        $failureError = $null
        Push-Location $preDeviceRepository
        try {
            & $collector @failure | Out-Null
        }
        catch {
            $failureError = $_
        }
        finally {
            Pop-Location
        }
        if ($null -eq $failureError) {
            throw "The $($failureCase.Name) fixture did not retain its source failure."
        }
        if ($failureCase.Name -eq 'semantic-and-restore-failure' -and -not (Test-Path -LiteralPath (Join-Path $slotDirectory 'rotation-restore-failure.txt'))) {
            throw 'The source-error fixture did not retain its isolated rotation restore failure.'
        }
        if (
            $null -ne $failureCase.ExpectedError -and
            $failureError.Exception.Message -cne $failureCase.ExpectedError
        ) {
            throw "The $($failureCase.Name) fixture replaced its source semantic error: $($failureError.Exception.Message)"
        }
        $failureState = Get-Content -Raw -LiteralPath (Join-Path $slotDirectory 'run-state.json') | ConvertFrom-Json
        if ($failureState.status -cne 'invalid-after-samples' -or $failureState.verdict -cne 'invalid') {
            throw "The $($failureCase.Name) fixture changed the existing INVALID guard."
        }
        $commitRawPath = Join-Path $slotDirectory 'raw/failure-latest-committed-result.xml'
        $commitMetadataPath = Join-Path $slotDirectory 'raw/failure-latest-committed-result.metadata.txt'
        $undoRawPath = Join-Path $slotDirectory 'raw/failure-latest-undo-reset.xml'
        $undoMetadataPath = Join-Path $slotDirectory 'raw/failure-latest-undo-reset.metadata.txt'
        if ($failureCase.PersistenceFailure) {
            if (
                (Get-Content -Raw -LiteralPath $commitRawPath) -cne 'fixture collision' -or
                (Test-Path -LiteralPath $commitMetadataPath)
            ) {
                throw 'Persistence failure did not preserve the collision or suppress partial metadata.'
            }
        }
        else {
            $commitRaw = Get-Content -Raw -LiteralPath $commitRawPath
            $commitMetadata = Get-Content -LiteralPath $commitMetadataPath
            if (
                'kind=committed-result' -notin $commitMetadata -or
                'phase=sample-commit' -notin $commitMetadata -or
                'workload=canvas16_tap' -notin $commitMetadata -or
                'sample_index=1' -notin $commitMetadata -or
                'warmup_index=' -notin $commitMetadata -or
                'undo_attempt=' -notin $commitMetadata -or
                @($commitMetadata | Where-Object { $_ -eq "source_error=$($failureError.Exception.Message -replace '[\r\n]+', ' ')" }).Count -ne 1
            ) {
                throw "The $($failureCase.Name) committed-result metadata is incomplete."
            }
            if ($null -ne $failureCase.ExpectedRaw -and $commitRaw -cne $failureCase.ExpectedRaw) {
                throw "The $($failureCase.Name) raw XML changed before persistence."
            }
        }
        $undoMetadata = Get-Content -LiteralPath $undoMetadataPath
        if (
            -not (Test-Path -LiteralPath $undoRawPath) -or
            'kind=undo-reset' -notin $undoMetadata -or
            'phase=warmup-reset' -notin $undoMetadata -or
            'workload=canvas16_tap' -notin $undoMetadata -or
            'sample_index=' -notin $undoMetadata -or
            'warmup_index=5' -notin $undoMetadata -or
            'undo_attempt=1' -notin $undoMetadata
        ) {
            throw "The $($failureCase.Name) latest Undo/reset evidence is incomplete."
        }
        $failureCommandLedgers[$failureCase.Name] =
            (@($global:neneFrameFixtureState.Commands | ForEach-Object {
                        $_ -replace 'failure-evidence-(semantic-mismatch|malformed|persistence-failure|rotation-mismatch|geometry-drift|semantic-and-restore-failure)', 'failure-evidence-case'
                    }) -join "`n")
    }
    if (
        $failureCommandLedgers['semantic-mismatch'] -cne $failureCommandLedgers['malformed'] -or
        $failureCommandLedgers['semantic-mismatch'] -cne $failureCommandLedgers['persistence-failure'] -or
        $failureCommandLedgers['semantic-mismatch'] -cne $failureCommandLedgers['rotation-mismatch']
    ) {
        throw 'Failure-evidence retention added or reordered device calls between failure modes.'
    }

    foreach ($frameFailureCase in @(
            [pscustomobject]@{
                Name = 'ring-loss'
                ExpectedError = 'Sample 1 preview raw row count must equal Total frames rendered.'
            },
            [pscustomobject]@{
                Name = 'zero-rows'
                ExpectedError = 'Sample 1 preview raw row count must equal Total frames rendered.'
            },
            [pscustomobject]@{
                Name = 'flagged-frame'
                ExpectedError = 'Sample 1 preview requires every raw PROFILEDATA row to be valid.'
            },
            [pscustomobject]@{
                Name = 'duplicate-header'
                ExpectedError = 'Sample 1 must expose exactly one PROFILEDATA header.'
            },
            [pscustomobject]@{
                Name = 'duplicate-frame-id'
                ExpectedError = 'Operation frame timestamps do not preserve DOWN, UP, and committed-result order.'
            },
            [pscustomobject]@{
                Name = 'late-commit'
                ExpectedError = 'Sample 1 for canvas16_tap rendered an unassociated commit frame during UI verification.'
            }
        )) {
        $frameFailure = $preDevice.Clone()
        $frameFailure.ExperimentDirectory = Join-Path $temporaryRoot "frame-failure-$($frameFailureCase.Name)"
        $frameFailure.ExperimentId = "frame-failure-$($frameFailureCase.Name)"
        $global:neneFrameFixtureState.InstallSeen = $false
        $global:neneFrameFixtureState.Frame = 0
        $global:neneFrameFixtureState.FrameCaptureCount = 0
        $global:neneFrameFixtureState.CurrentPhaseFrame = 0
        $global:neneFrameFixtureState.MotionCount = 0
        $global:neneFrameFixtureState.MeasuredStarted = $false
        $global:neneFrameFixtureState.Mode = $frameFailureCase.Name
        $global:neneFrameFixtureState.RotationMode = 'free'
        $global:neneFrameFixtureState.NumericRotation = 0
        $global:neneFrameFixtureState.CurrentRotation = 0
        $global:neneFrameFixtureState.Commands = [System.Collections.Generic.List[string]]::new()
        $frameFailureError = $null
        Push-Location $preDeviceRepository
        try {
            & $collector @frameFailure | Out-Null
        }
        catch {
            $frameFailureError = $_
        }
        finally {
            Pop-Location
        }
        $frameFailureState = Get-Content -Raw -LiteralPath (
            Join-Path $frameFailure.ExperimentDirectory 'slot-01-diagnostic-baseline-attempt-1/run-state.json'
        ) | ConvertFrom-Json
        if (
            $null -eq $frameFailureError -or
            $frameFailureError.Exception.Message -cne $frameFailureCase.ExpectedError -or
            $frameFailureState.status -cne 'invalid-after-samples' -or
            [int]$frameFailureState.measured_workload_counts.canvas16_tap -ne 1 -or
            [int]$frameFailureState.measured_workload_counts.canvas256_repeated_diagonal -ne 0
        ) {
            throw "The $($frameFailureCase.Name) fixture did not reject missing, invalid, or ambiguous frame rows at the exact operation boundary."
        }
    }

    foreach ($earlyStopCase in @(
            [pscustomobject]@{
                Name = 'first-family-gross'
                ExpectedError = $null
                ExpectedStateStatus = 'completed'
                ExpectedVerdict = 'gross-regression'
            },
            [pscustomobject]@{
                Name = 'fatal-after-first-family'
                ExpectedError = 'Fatal, ANR, signal, or process-death evidence was observed after canvas16_tap.'
                ExpectedStateStatus = 'invalid-after-samples'
                ExpectedVerdict = 'invalid'
            }
        )) {
        $earlyStop = $preDevice.Clone()
        $earlyStop.ExperimentDirectory = Join-Path $temporaryRoot "early-stop-$($earlyStopCase.Name)"
        $earlyStop.ExperimentId = "early-stop-$($earlyStopCase.Name)"
        $slotDirectory = Join-Path $earlyStop.ExperimentDirectory 'slot-01-diagnostic-baseline-attempt-1'
        $global:neneFrameFixtureState.InstallSeen = $false
        $global:neneFrameFixtureState.Committed = $false
        $global:neneFrameFixtureState.RedoAvailable = $false
        $global:neneFrameFixtureState.Frame = 0
        $global:neneFrameFixtureState.FrameCaptureCount = 0
        $global:neneFrameFixtureState.CurrentPhaseFrame = 0
        $global:neneFrameFixtureState.MotionCount = 0
        $global:neneFrameFixtureState.MeasuredStarted = $false
        $global:neneFrameFixtureState.Mode = $earlyStopCase.Name
        $global:neneFrameFixtureState.RotationMode = 'free'
        $global:neneFrameFixtureState.NumericRotation = 0
        $global:neneFrameFixtureState.CurrentRotation = 0
        $global:neneFrameFixtureState.PinSeen = $false
        $global:neneFrameFixtureState.Commands = [System.Collections.Generic.List[string]]::new()
        $earlyStopError = $null
        $earlyStopOutput = @()
        Push-Location $preDeviceRepository
        try {
            $earlyStopOutput = @(& $collector @earlyStop)
        }
        catch {
            $earlyStopError = $_
        }
        finally {
            Pop-Location
        }
        if (
            ($null -eq $earlyStopCase.ExpectedError -and $null -ne $earlyStopError) -or
            ($null -ne $earlyStopCase.ExpectedError -and
                ($null -eq $earlyStopError -or $earlyStopError.Exception.Message -cne $earlyStopCase.ExpectedError))
        ) {
            throw "The $($earlyStopCase.Name) fixture changed the exact early-stop error contract."
        }
        $earlyStopState = Get-Content -Raw -LiteralPath (Join-Path $slotDirectory 'run-state.json') | ConvertFrom-Json
        $earlyStopFrames = @(Import-Csv -LiteralPath (Join-Path $slotDirectory 'frames.csv'))
        $earlyStopSamples = @(Import-Csv -LiteralPath (Join-Path $slotDirectory 'samples.csv'))
        $earlyStopCommands = @($global:neneFrameFixtureState.Commands)
        if (
            $earlyStopState.status -cne $earlyStopCase.ExpectedStateStatus -or
            $earlyStopState.verdict -cne $earlyStopCase.ExpectedVerdict -or
            [int]$earlyStopState.measured_workload_counts.canvas16_tap -ne 10 -or
            [int]$earlyStopState.measured_workload_counts.canvas256_repeated_diagonal -ne 0 -or
            [int]$earlyStopState.measured_operation_count -ne 10 -or
            $earlyStopFrames.Count -ne 20 -or
            $earlyStopSamples.Count -ne 10 -or
            $global:neneFrameFixtureState.Frame -ne 20 -or
            $global:neneFrameFixtureState.FrameCaptureCount -ne 30 -or
            @($earlyStopCommands | Where-Object { $_ -ceq 'shell cmd input tap 120 110' }).Count -ne 1 -or
            @($earlyStopCommands | Where-Object { $_ -ceq 'shell cmd input text 256' }).Count -ne 0
        ) {
            throw "The $($earlyStopCase.Name) fixture did not retain exactly one complete family and block the later family."
        }
        if (
            $earlyStopCase.Name -eq 'first-family-gross' -and
            ('status=gross-regression' -notin $earlyStopOutput -or
                -not (Test-Path -LiteralPath (Join-Path $slotDirectory 'metadata.txt')))
        ) {
            throw 'The complete first-family numeric miss was not preserved as a published performance failure.'
        }
    }

    $restoreFailure = $preDevice.Clone()
    $restoreFailure.ExperimentDirectory = Join-Path $temporaryRoot 'normal-restore-failure'
    $restoreFailure.ExperimentId = 'normal-restore-failure'
    $global:neneFrameFixtureState.InstallSeen = $false
    $global:neneFrameFixtureState.Committed = $false
    $global:neneFrameFixtureState.Frame = 0
    $global:neneFrameFixtureState.FrameCaptureCount = 0
    $global:neneFrameFixtureState.CurrentPhaseFrame = 0
    $global:neneFrameFixtureState.MotionCount = 0
    $global:neneFrameFixtureState.MeasuredStarted = $false
    $global:neneFrameFixtureState.Mode = 'restore-failure'
    $global:neneFrameFixtureState.RotationMode = 'free'
    $global:neneFrameFixtureState.NumericRotation = 0
    $global:neneFrameFixtureState.CurrentRotation = 0
    $global:neneFrameFixtureState.PinSeen = $false
    $global:neneFrameFixtureState.Commands = [System.Collections.Generic.List[string]]::new()
    $restoreFailureError = $null
    Push-Location $preDeviceRepository
    try {
        & $collector @restoreFailure | Out-Null
    }
    catch {
        $restoreFailureError = $_
    }
    finally {
        Pop-Location
    }
    $restoreFailureSlot = Join-Path $restoreFailure.ExperimentDirectory 'slot-01-diagnostic-baseline-attempt-1'
    $restoreFailureState = Get-Content -Raw -LiteralPath (Join-Path $restoreFailureSlot 'run-state.json') | ConvertFrom-Json
    if (
        $null -eq $restoreFailureError -or
        $restoreFailureError.Exception.Message -notlike 'The frame result is invalid because rotation restoration was not verified:*' -or
        $restoreFailureState.status -cne 'invalid-after-samples' -or
        $restoreFailureState.verdict -cne 'invalid' -or
        -not (Test-Path -LiteralPath (Join-Path $restoreFailureSlot 'rotation-restore-failure.txt'))
    ) {
        throw 'A normally completed result did not become INVALID after rotation restore failure.'
    }

    $lockedRestore = $preDevice.Clone()
    $lockedRestore.ExperimentDirectory = Join-Path $temporaryRoot 'locked-rotation-restore'
    $lockedRestore.ExperimentId = 'locked-rotation-restore'
    $global:neneFrameFixtureState.InstallSeen = $false
    $global:neneFrameFixtureState.Committed = $false
    $global:neneFrameFixtureState.Frame = 0
    $global:neneFrameFixtureState.FrameCaptureCount = 0
    $global:neneFrameFixtureState.CurrentPhaseFrame = 0
    $global:neneFrameFixtureState.MotionCount = 0
    $global:neneFrameFixtureState.MeasuredStarted = $false
    $global:neneFrameFixtureState.Mode = 'success'
    $global:neneFrameFixtureState.RotationMode = 'locked'
    $global:neneFrameFixtureState.NumericRotation = 2
    $global:neneFrameFixtureState.CurrentRotation = 2
    $global:neneFrameFixtureState.PinSeen = $false
    $global:neneFrameFixtureState.Commands = [System.Collections.Generic.List[string]]::new()
    Push-Location $preDeviceRepository
    try {
        $lockedOutput = @(& $collector @lockedRestore)
    }
    finally {
        Pop-Location
    }
    $lockedRestoreLines = Get-Content -LiteralPath (Join-Path $lockedRestore.ExperimentDirectory 'slot-01-diagnostic-baseline-attempt-1/rotation-restore.txt')
    if (
        'status=inconclusive' -notin $lockedOutput -or
        'mode=locked' -notin $lockedRestoreLines -or
        'numeric_user_rotation=2' -notin $lockedRestoreLines -or
        'current_rotation=2' -notin $lockedRestoreLines -or
        'command=wm user-rotation lock 2' -notin $lockedRestoreLines -or
        'command=wm user-rotation free' -in $lockedRestoreLines -or
        'restore_verified=true' -notin $lockedRestoreLines
    ) {
        throw 'The locked-origin fixture did not restore the exact mode and numeric rotation.'
    }

    function Reset-NeneFrameFixtureForInspection {
        $global:neneFrameFixtureState.InstallSeen = $false
        $global:neneFrameFixtureState.Committed = $false
        $global:neneFrameFixtureState.RedoAvailable = $false
        $global:neneFrameFixtureState.CanvasEdge = 16
        $global:neneFrameFixtureState.MenuOpen = $false
        $global:neneFrameFixtureState.DialogOpen = $false
        $global:neneFrameFixtureState.FocusField = ''
        $global:neneFrameFixtureState.WidthInput = '16'
        $global:neneFrameFixtureState.HeightInput = '16'
        $global:neneFrameFixtureState.Frame = 0
        $global:neneFrameFixtureState.FrameCaptureCount = 0
        $global:neneFrameFixtureState.CurrentPhaseFrame = 0
        $global:neneFrameFixtureState.MotionCount = 0
        $global:neneFrameFixtureState.MeasuredStarted = $false
        $global:neneFrameFixtureState.Mode = 'success'
        $global:neneFrameFixtureState.RotationMode = 'free'
        $global:neneFrameFixtureState.NumericRotation = 0
        $global:neneFrameFixtureState.CurrentRotation = 0
        $global:neneFrameFixtureState.PinSeen = $false
        $global:neneFrameFixtureState.StayAwake = '0'
        $global:neneFrameFixtureState.Commands = [System.Collections.Generic.List[string]]::new()
    }

    $inspectExclusive = $preDevice.Clone()
    $inspectExclusive.ExperimentDirectory = Join-Path $temporaryRoot 'inspect-exclusive'
    $inspectExclusive.ExperimentId = 'inspect-exclusive'
    $inspectExclusive.InspectGeometryOnly = $true
    $inspectExclusive.InspectionDirectory = Join-Path $temporaryRoot 'inspect-exclusive-records'
    $inspectExclusive.ValidateArtifactOnly = $true
    Reset-NeneFrameFixtureForInspection
    Invoke-ExpectedFailure -Arguments $inspectExclusive
    if (
        (Test-Path -LiteralPath $inspectExclusive.ExperimentDirectory) -or
        (Test-Path -LiteralPath $inspectExclusive.InspectionDirectory)
    ) {
        throw 'A rejected inspection invocation must not create an experiment or inspection directory.'
    }

    $inspectWithoutDirectory = $preDevice.Clone()
    $inspectWithoutDirectory.ExperimentDirectory = Join-Path $temporaryRoot 'inspect-no-directory'
    $inspectWithoutDirectory.ExperimentId = 'inspect-no-directory'
    $inspectWithoutDirectory.InspectGeometryOnly = $true
    Reset-NeneFrameFixtureForInspection
    Invoke-ExpectedFailure -Arguments $inspectWithoutDirectory
    if (Test-Path -LiteralPath $inspectWithoutDirectory.ExperimentDirectory) {
        throw 'An inspection without its own records directory must not create an experiment directory.'
    }

    $inspectSameDirectory = $preDevice.Clone()
    $inspectSameDirectory.ExperimentDirectory = Join-Path $temporaryRoot 'inspect-same-directory'
    $inspectSameDirectory.ExperimentId = 'inspect-same-directory'
    $inspectSameDirectory.InspectGeometryOnly = $true
    $inspectSameDirectory.InspectionDirectory = $inspectSameDirectory.ExperimentDirectory
    Reset-NeneFrameFixtureForInspection
    Invoke-ExpectedFailure -Arguments $inspectSameDirectory
    if (Test-Path -LiteralPath $inspectSameDirectory.ExperimentDirectory) {
        throw 'An inspection aimed at the experiment directory must not create it.'
    }

    $inspectionWithoutSwitch = $preDevice.Clone()
    $inspectionWithoutSwitch.ExperimentDirectory = Join-Path $temporaryRoot 'inspection-directory-without-switch'
    $inspectionWithoutSwitch.ExperimentId = 'inspection-without-switch'
    $inspectionWithoutSwitch.InspectionDirectory = Join-Path $temporaryRoot 'stray-inspection-records'
    Reset-NeneFrameFixtureForInspection
    Invoke-ExpectedFailure -Arguments $inspectionWithoutSwitch

    $inspect = $preDevice.Clone()
    $inspect.ExperimentDirectory = Join-Path $temporaryRoot 'no-sample-inspection-experiment'
    $inspect.ExperimentId = 'no-sample-inspection'
    $inspect.InspectGeometryOnly = $true
    $inspect.InspectionDirectory = Join-Path $temporaryRoot 'no-sample-inspection-records'
    New-Item -ItemType Directory -Path $inspect.InspectionDirectory | Out-Null
    $foreignInspection = [ordered]@{
        schema = 'nene-pixel-p4-no-sample-inspection-v1'
        role = 'candidate'
        apk_sha256 = 'd' * 64
        embedded_source_commit = $candidateCommit
        rotation = 1
        root_bounds = '[0,0][1920,1200]'
        canvas16_bounds = '[688,615][1232,1159]'
        canvas256_bounds = '[688,615][1232,1159]'
        geometry_id = 'initial-fit-centered-v1'
        ui_dump_sha256s = [ordered]@{ 'ui-inspect-16x16.xml' = '0' * 64; 'ui-inspect-256x256.xml' = '1' * 64 }
        captured_utc = '2026-09-15T00:00:00.0000000Z'
    }
    Write-FixtureJson $foreignInspection (Join-Path $inspect.InspectionDirectory 'no-sample-inspection-candidate.json')
    Reset-NeneFrameFixtureForInspection
    $inspectOutput = @()
    Push-Location $preDeviceRepository
    try {
        $inspectOutput = @(& $collector @inspect)
    }
    finally {
        Pop-Location
    }
    $inspectionPath = Join-Path $inspect.InspectionDirectory 'no-sample-inspection-baseline.json'
    $combinedPath = Join-Path $inspect.InspectionDirectory 'no-sample-inspection.json'
    if (-not (Test-Path -LiteralPath $inspectionPath -PathType Leaf)) {
        throw 'The no-sample inspection did not publish its per-role record.'
    }
    $inspection = Get-Content -Raw -LiteralPath $inspectionPath | ConvertFrom-Json
    if (
        $inspectOutput.Count -ne 1 -or
        $inspection.schema -cne 'nene-pixel-p4-no-sample-inspection-v1' -or
        $inspection.role -cne 'baseline' -or
        $inspection.apk_sha256 -cne $preDeviceApkSha256 -or
        $inspection.embedded_source_commit -cne $preDeviceSource -or
        [int]$inspection.rotation -ne 1 -or
        $inspection.root_bounds -cne '[0,0][1920,1200]' -or
        $inspection.canvas16_bounds -cne '[688,615][1232,1159]' -or
        $inspection.canvas256_bounds -cne '[688,615][1232,1159]' -or
        $inspection.geometry_id -cne 'initial-fit-centered-v1' -or
        @($inspection.ui_dump_sha256s.PSObject.Properties.Name) -join '|' -cne 'ui-inspect-16x16.xml|ui-inspect-256x256.xml' -or
        [string]::IsNullOrWhiteSpace($inspection.captured_utc)
    ) {
        throw 'The no-sample inspection record did not publish the pinned geometry of both canvas families.'
    }
    if (-not (Test-Path -LiteralPath $combinedPath -PathType Leaf)) {
        throw 'The no-sample inspection did not publish the combined record once both roles existed.'
    }
    $combined = Get-Content -Raw -LiteralPath $combinedPath | ConvertFrom-Json
    if (
        $combined.schema -cne 'nene-pixel-p4-no-sample-inspection-v1' -or
        $combined.baseline.role -cne 'baseline' -or
        $combined.candidate.role -cne 'candidate' -or
        $combined.baseline.apk_sha256 -cne $preDeviceApkSha256 -or
        $combined.candidate.apk_sha256 -cne ('d' * 64)
    ) {
        throw 'The combined no-sample inspection record did not carry both role records.'
    }
    $inspectCommands = @($global:neneFrameFixtureState.Commands)
    if (
        -not $global:neneFrameFixtureState.InstallSeen -or
        $global:neneFrameFixtureState.MotionCount -ne 0 -or
        $global:neneFrameFixtureState.FrameCaptureCount -ne 0 -or
        @($inspectCommands | Where-Object { $_ -like 'shell dumpsys gfxinfo *' }).Count -ne 0 -or
        @($inspectCommands | Where-Object { $_ -like 'shell cmd input motionevent *' }).Count -ne 0 -or
        @($inspectCommands | Where-Object { $_ -ceq 'shell wm user-rotation lock 1' }).Count -ne 1 -or
        @($inspectCommands | Where-Object { $_ -ceq 'shell settings put global stay_on_while_plugged_in 0' }).Count -ne 1 -or
        @($inspectCommands | Where-Object { $_ -ceq 'shell cmd input text 16' }).Count -ne 2 -or
        @($inspectCommands | Where-Object { $_ -ceq 'shell cmd input text 256' }).Count -ne 2 -or
        $global:neneFrameFixtureState.RotationMode -ne 'free'
    ) {
        throw 'The no-sample inspection sampled frames or did not restore the original device state.'
    }
    if (Test-Path -LiteralPath $inspect.ExperimentDirectory) {
        throw 'The no-sample inspection reached into the reserved experiment directory.'
    }
    $inspectionRawDirectories =
        @(Get-ChildItem -LiteralPath $inspect.InspectionDirectory -Directory |
            Where-Object { $_.Name -like 'no-sample-inspection-baseline-raw-*' })
    if ($inspectionRawDirectories.Count -ne 1) {
        throw 'The no-sample inspection did not retain exactly one raw evidence directory.'
    }
    $inspectionRestore = Get-Content -LiteralPath (Join-Path $inspectionRawDirectories[0].FullName 'rotation-restore.txt')
    if (
        'restore_verified=true' -notin $inspectionRestore -or
        -not (Test-Path -LiteralPath (Join-Path $inspectionRawDirectories[0].FullName 'rotation-pin.txt')) -or
        -not (Test-Path -LiteralPath (Join-Path $inspectionRawDirectories[0].FullName 'raw/ui-inspect-256x256.xml')) -or
        (Test-Path -LiteralPath (Join-Path $inspectionRawDirectories[0].FullName 'run-state.json'))
    ) {
        throw 'The no-sample inspection evidence directory is incomplete or reserved a run state.'
    }

    Reset-NeneFrameFixtureForInspection
    $inspectRepeat = $inspect.Clone()
    Push-Location $preDeviceRepository
    try {
        Invoke-ExpectedFailure -Arguments $inspectRepeat
    }
    finally {
        Pop-Location
    }

    $inspectMismatch = $preDevice.Clone()
    $inspectMismatch.ExperimentDirectory = Join-Path $temporaryRoot 'no-sample-inspection-mismatch-experiment'
    $inspectMismatch.ExperimentId = 'inspect-mismatch'
    $inspectMismatch.InspectGeometryOnly = $true
    $inspectMismatch.InspectionDirectory = Join-Path $temporaryRoot 'no-sample-inspection-mismatch-records'
    $inspectMismatch.BaselineCanvas16SurfaceBounds = '[687,615][1232,1159]'
    Reset-NeneFrameFixtureForInspection
    $inspectMismatchError = $null
    Push-Location $preDeviceRepository
    try {
        & $collector @inspectMismatch | Out-Null
    }
    catch {
        $inspectMismatchError = $_
    }
    finally {
        Pop-Location
    }
    $mismatchRecordPath = Join-Path $inspectMismatch.InspectionDirectory 'no-sample-inspection-baseline.json'
    if (
        $null -eq $inspectMismatchError -or
        $inspectMismatchError.Exception.Message -notlike 'The inspected baseline surface bounds do not match the supplied geometry;*' -or
        -not (Test-Path -LiteralPath $mismatchRecordPath -PathType Leaf) -or
        $global:neneFrameFixtureState.RotationMode -ne 'free'
    ) {
        throw 'A mismatched inspection did not publish the observed geometry before failing, or did not restore rotation.'
    }
    $mismatchRecord = Get-Content -Raw -LiteralPath $mismatchRecordPath | ConvertFrom-Json
    if ($mismatchRecord.canvas16_bounds -cne '[688,615][1232,1159]') {
        throw 'The rejected inspection did not record the bounds the operator must copy into the manifest.'
    }

    $unsetStayAwake = $preDevice.Clone()
    $unsetStayAwake.ExperimentDirectory = Join-Path $temporaryRoot 'unset-stay-awake-experiment'
    $unsetStayAwake.ExperimentId = 'unset-stay-awake'
    $unsetStayAwake.InspectGeometryOnly = $true
    $unsetStayAwake.InspectionDirectory = Join-Path $temporaryRoot 'unset-stay-awake-records'
    Reset-NeneFrameFixtureForInspection
    $global:neneFrameFixtureState.StayAwake = 'null'
    Push-Location $preDeviceRepository
    try {
        & $collector @unsetStayAwake | Out-Null
    }
    finally {
        Pop-Location
    }
    $unsetStayAwakeCommands = @($global:neneFrameFixtureState.Commands)
    if (
        @($unsetStayAwakeCommands | Where-Object { $_ -ceq 'shell settings delete global stay_on_while_plugged_in' }).Count -ne 1 -or
        @($unsetStayAwakeCommands | Where-Object { $_ -like 'shell settings put global stay_on_while_plugged_in *' }).Count -ne 0
    ) {
        throw 'An originally unset stay-awake global was restored by writing the literal text "null".'
    }

    $sampledStayAwake = $preDevice.Clone()
    $sampledStayAwake.ExperimentDirectory = Join-Path $temporaryRoot 'unset-stay-awake-samples'
    $sampledStayAwake.ExperimentId = 'unset-stay-awake-samples'
    Reset-NeneFrameFixtureForInspection
    $global:neneFrameFixtureState.StayAwake = 'null'
    Push-Location $preDeviceRepository
    try {
        & $collector @sampledStayAwake | Out-Null
    }
    finally {
        Pop-Location
    }
    $sampledStayAwakeCommands = @($global:neneFrameFixtureState.Commands)
    if (
        @($sampledStayAwakeCommands | Where-Object { $_ -ceq 'shell settings delete global stay_on_while_plugged_in' }).Count -ne 1 -or
        @($sampledStayAwakeCommands | Where-Object { $_ -like 'shell settings put global stay_on_while_plugged_in *' }).Count -ne 0
    ) {
        throw 'The sample path restored an originally unset stay-awake global by writing the literal text "null".'
    }

    Remove-Item Function:\global:adb -ErrorAction SilentlyContinue
    Remove-Item Function:\global:Start-Sleep -ErrorAction SilentlyContinue
    Remove-Item Function:\global:Get-NeneFrameFixtureUi -ErrorAction SilentlyContinue
    Remove-Variable neneFrameFixtureState -Scope Global -ErrorAction SilentlyContinue
    $frameFixtureGlobalsOwned = $false

    $wrongProf = $artifact.Clone()
    $wrongProf.CandidatePackagedProfSha256 = "0" * 64
    $wrongProf.ExperimentDirectory = Join-Path $temporaryRoot "wrong-prof-experiment"
    $wrongProf.ExperimentId = "wrong-prof-validation"
    Invoke-ExpectedFailure -Arguments $wrongProf
    if (Test-Path -LiteralPath $wrongProf.ExperimentDirectory) {
        throw 'Rejected artifact-only validation must not create an experiment directory.'
    }

    $wrongProfm = $artifact.Clone()
    $wrongProfm.CandidatePackagedProfmSha256 = "0" * 64
    $wrongProfm.ExperimentDirectory = Join-Path $temporaryRoot "wrong-profm-experiment"
    $wrongProfm.ExperimentId = "wrong-profm-validation"
    Invoke-ExpectedFailure -Arguments $wrongProfm
    if (Test-Path -LiteralPath $wrongProfm.ExperimentDirectory) {
        throw 'Rejected artifact-only validation must not create an experiment directory.'
    }

    $missingProfmApk = New-ApkFixture -Name "missing-profm" -SourceCommit $candidateCommit `
        -ProfBytes $profBytes -ProfmBytes $profmBytes -OmitProfm
    $missingProfm = $artifact.Clone()
    $missingProfm.ApkPath = $missingProfmApk
    $missingProfm.CandidateApkSha256 = (Get-FileHash -Algorithm SHA256 $missingProfmApk).Hash.ToLowerInvariant()
    $missingProfm.ExperimentDirectory = Join-Path $temporaryRoot "missing-profm-experiment"
    $missingProfm.ExperimentId = "missing-profm-validation"
    Invoke-ExpectedFailure -Arguments $missingProfm
    if (Test-Path -LiteralPath $missingProfm.ExperimentDirectory) {
        throw 'Rejected artifact-only validation must not create an experiment directory.'
    }

    $wrongCount = $slot1.Clone()
    $wrongCount.SampleCount = 50
    Invoke-ExpectedFailure -Arguments $wrongCount

    $slot2 = $common.Clone()
    $slot2 += @{
        Variant = "release-like"
        CompilationMode = "speed-profile"
        SourceCommit = $candidateCommit
        RunKind = "diagnostic"
        CandidateRole = "candidate"
        ComparisonSequenceIndex = 2
        SampleCount = 10
    }
    Invoke-ExpectedFailure -Arguments $slot2
    $oldTapStateDirectory = Join-Path $experimentRoot 'slot-01-diagnostic-baseline-attempt-1'
    New-Item -ItemType Directory -Path $oldTapStateDirectory -Force | Out-Null
    $oldTapStatePath = Join-Path $oldTapStateDirectory 'run-state.json'
    Write-FixtureJson `
        -Path $oldTapStatePath `
        -Value ([ordered]@{
            schema = 'nene-pixel-p4-indexed-frame-experiment-v4'
            experiment_id = 'offline-protocol-validation'
            comparison_sequence_index = 1
            attempt = 1
            status = 'completed'
            verdict = 'inconclusive'
            measured_down_count = 10
        })
    $oldTapStateHash = (Get-FileHash -LiteralPath $oldTapStatePath -Algorithm SHA256).Hash.ToLowerInvariant()
    Invoke-ExpectedFailure -Arguments $slot2
    if ((Get-FileHash -LiteralPath $oldTapStatePath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $oldTapStateHash) {
        throw 'The rejected old tap-only run state was modified.'
    }
    Write-State -Slot "slot-01-diagnostic-baseline" -Attempt 1 -Status "completed" -Verdict "inconclusive" -MeasuredDownCount 10
    Invoke-ExpectedPass -Arguments $slot2

    $slot1StatePath = Join-Path $experimentRoot "slot-01-diagnostic-baseline-attempt-1/run-state.json"
    $foreignState = Get-Content -Raw -LiteralPath $slot1StatePath | ConvertFrom-Json
    $foreignState.experiment_id = "foreign-experiment"
    $foreignState | ConvertTo-Json | Set-Content -LiteralPath $slot1StatePath -Encoding utf8NoBOM
    Invoke-ExpectedFailure -Arguments $slot2
    Write-State -Slot "slot-01-diagnostic-baseline" -Attempt 1 -Status "completed" -Verdict "inconclusive" -MeasuredDownCount 10

    $changedPair = $slot2.Clone()
    $changedPair.CandidateApkSha256 = "c" * 64
    Invoke-ExpectedFailure -Arguments $changedPair

    $slot2Attempt2 = $slot2.Clone()
    $slot2Attempt2.Attempt = 2
    Invoke-ExpectedFailure -Arguments $slot2Attempt2
    Write-State -Slot "slot-02-diagnostic-candidate" -Attempt 1 -Status "invalid-before-samples" -Verdict "invalid" -MeasuredDownCount 0
    Invoke-ExpectedFailure -Arguments $slot2Attempt2
    if (Test-Path -LiteralPath (Join-Path $experimentRoot "slot-02-diagnostic-candidate-attempt-2")) {
        throw "The max-one writer created attempt 2 output after rejecting the invocation."
    }
    Write-State -Slot "slot-02-diagnostic-candidate" -Attempt 1 -Status "completed" -Verdict "inconclusive" -MeasuredDownCount 10

    $slot3 = $common.Clone()
    $slot3 += @{
        Variant = "release-like"
        CompilationMode = "speed-profile"
        SourceCommit = $candidateCommit
        RunKind = "decision"
        CandidateRole = "candidate"
        ComparisonSequenceIndex = 3
        SampleCount = 50
    }
    Invoke-ExpectedPass -Arguments $slot3
    Write-State -Slot "slot-03-decision-candidate" -Attempt 1 -Status "completed" -Verdict "fail" -MeasuredDownCount 50

    $slot4 = $common.Clone()
    $slot4 += @{
        Variant = "release-like"
        CompilationMode = "speed-profile"
        SourceCommit = $baselineCommit
        RunKind = "decision"
        CandidateRole = "baseline"
        ComparisonSequenceIndex = 4
        SampleCount = 50
    }
    Invoke-ExpectedFailure -Arguments $slot4
    Write-State -Slot "slot-03-decision-candidate" -Attempt 1 -Status "completed" -Verdict "pass" -MeasuredDownCount 50
    Invoke-ExpectedPass -Arguments $slot4

    Write-Output "P4 indexed frame protocol state validation: PASS"
}
finally {
    if ($frameFixtureGlobalsOwned) {
        Remove-Item Function:\global:adb -ErrorAction SilentlyContinue
        Remove-Item Function:\global:Start-Sleep -ErrorAction SilentlyContinue
        Remove-Item Function:\global:Get-NeneFrameFixtureUi -ErrorAction SilentlyContinue
        Remove-Variable neneFrameFixtureState -Scope Global -ErrorAction SilentlyContinue
    }
    $resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)
    $resolvedSystemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTemporaryRoot.StartsWith($resolvedSystemTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

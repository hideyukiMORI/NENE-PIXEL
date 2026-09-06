[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$collector = Join-Path $PSScriptRoot "measurements/measure-m2-frame.ps1"
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("nene-frame-protocol-" + [guid]::NewGuid().ToString("N"))
$baselineCommit = "1" * 40
$candidateCommit = "2" * 40
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
        schema = "nene-pixel-m2-frame-experiment-v3"
        experiment_id = "offline-protocol-validation"
        comparison_sequence_index = [int]$Slot.Substring(5, 2)
        attempt = $Attempt
        status = $Status
        verdict = $Verdict
        measured_down_count = $MeasuredDownCount
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

    $legacyRoot = Join-Path $temporaryRoot "legacy-v2"
    New-Item -ItemType Directory -Path $legacyRoot | Out-Null
    [ordered]@{ schema = "nene-pixel-m2-frame-experiment-v2" } |
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
        [int]$freshManifest.maximum_attempts_per_slot -ne 1 -or
        $freshManifest.replacement_rule -cne "none"
    ) {
        throw "A new v3 experiment did not publish the fixed max-one attempt policy."
    }

    $historicalRoot = Join-Path $temporaryRoot "historical-max-two"
    New-Item -ItemType Directory -Path $historicalRoot | Out-Null
    $historicalManifestPath = Join-Path $historicalRoot "experiment.json"
    $historicalManifest = Get-Content -Raw -LiteralPath $freshManifestPath | ConvertFrom-Json
    $historicalManifest.maximum_attempts_per_slot = 2
    $historicalManifest.replacement_rule =
        "attempt 2 only after attempt 1 is invalid before the first measured DOWN"
    Write-FixtureJson $historicalManifest $historicalManifestPath
    $historicalManifestHash =
        (Get-FileHash -LiteralPath $historicalManifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $historicalRead = $slot1.Clone()
    $historicalRead.ExperimentDirectory = $historicalRoot
    Invoke-ExpectedPass -Arguments $historicalRead
    if (
        (Get-FileHash -LiteralPath $historicalManifestPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne
            $historicalManifestHash -or
        @(Get-ChildItem -LiteralPath $historicalRoot).Count -ne 1
    ) {
        throw "Read-only validation changed the retained historical max-two experiment."
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
        Frame = 0
    }
    function global:Start-Sleep {
        param([int]$Milliseconds, [int]$Seconds)
    }
    function global:Get-NeneFrameFixtureUi {
        $dirty = if ($global:neneFrameFixtureState.Committed) { 'Unsaved changes' } else { 'No unsaved changes' }
        $undo = $global:neneFrameFixtureState.Committed.ToString().ToLowerInvariant()
        return @"
<hierarchy><node>
<node content-desc="Document dirty status" text="$dirty" />
<node enabled="$undo" bounds="[200,0][300,100]"><node text="Undo" /></node>
<node enabled="false"><node text="Redo" /></node>
<node checked="true"><node content-desc="Pencil tool" /></node>
<node content-desc="16 by 16 pixel canvas" bounds="[0,0][160,160]" />
</node></hierarchy>
"@
    }
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
        switch -Regex ($command) {
            '^shell getprop ro\.kernel\.qemu$' { '0'; return }
            '^shell getprop ro\.product\.manufacturer$' { 'ALLDOCUBE'; return }
            '^shell getprop ro\.product\.model$' { 'iPlay80miniPro'; return }
            '^shell getprop ro\.product\.name$' { 'iPlay80miniPro'; return }
            '^shell getprop ro\.product\.device$' { 'T830'; return }
            '^shell getprop ro\.build\.version\.sdk$' { '36'; return }
            '^shell getprop ro\.build\.fingerprint$' { 'fixture/fingerprint'; return }
            '^shell getprop ro\.build\.version\.security_patch$' { '2026-09-01'; return }
            '^shell settings get global stay_on_while_plugged_in$' { '0'; return }
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
            '^shell pm clear ' {
                $global:neneFrameFixtureState.Committed = $false
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
                if ([int]$AdbArguments[4] -eq 5) {
                    $global:neneFrameFixtureState.Committed = $true
                }
                elseif ([int]$AdbArguments[4] -eq 250) {
                    $global:neneFrameFixtureState.Committed = $false
                }
                else {
                    throw "The end-to-end fixture received an unexpected tap: $command"
                }
                return
            }
            '^shell cmd input motionevent ' {
                if ($AdbArguments[4] -eq 'UP') {
                    $global:neneFrameFixtureState.Committed = $true
                }
                return
            }
            '^shell uiautomator dump ' { 'UI dumped'; return }
            '^shell cat ' { Get-NeneFrameFixtureUi; return }
            '^shell dumpsys gfxinfo .* reset$' { 'reset'; return }
            '^shell dumpsys gfxinfo .* framestats$' {
                $global:neneFrameFixtureState.Frame += 1
                $frame = [long]$global:neneFrameFixtureState.Frame
                $base = $frame * 100000000L
                $header = 'Flags,FrameTimelineVsyncId,IntendedVsync,FrameStartTime,HandleInputStart,AnimationStart,PerformTraversalsStart,DrawStart,SyncQueued,SyncStart,IssueDrawCommandsStart,SwapBuffers,SwapBuffersCompleted,FrameDeadline,FrameCompleted,DisplayPresentTime'
                $row = @(
                    0,
                    $frame,
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
                    ($base + 10000000L),
                    ($base + 12000000L)
                ) -join ','
                return @(
                    'Applications Graphics Acceleration Info:',
                    'Total frames rendered: 1',
                    'Janky frames: 0',
                    'Number Frame deadline missed: 0',
                    '---PROFILEDATA---',
                    $header,
                    $row,
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
            '^logcat -d -v threadtime$' { 'fixture: no app fatal events'; return }
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
        Remove-Item Function:\global:adb -ErrorAction SilentlyContinue
        Remove-Item Function:\global:Start-Sleep -ErrorAction SilentlyContinue
        Remove-Item Function:\global:Get-NeneFrameFixtureUi -ErrorAction SilentlyContinue
    }
    if (
        -not $global:neneFrameFixtureState.InstallSeen -or
        $global:neneFrameFixtureState.Frame -ne 20
    ) {
        throw 'The end-to-end path did not install the exact verified APK or retain twenty phase frames.'
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
        [int]$preDeviceState.measured_down_count -ne 10 -or
        "apk_embedded_source_commit=$preDeviceSource" -notin $preDeviceMetadata -or
        "apk_bytes=$preDeviceApkBytes" -notin $preDeviceMetadata -or
        "apk_sha256=$preDeviceApkSha256" -notin $preDeviceMetadata -or
        "packaged_prof_sha256=$($preDevice.BaselinePackagedProfSha256)" -notin $preDeviceMetadata -or
        "packaged_profm_sha256=$($preDevice.BaselinePackagedProfmSha256)" -notin $preDeviceMetadata -or
        'sample_count=10' -notin $preDeviceMetadata -or
        'raw_frame_rows=20' -notin $preDeviceMetadata -or
        'limitation=app-issued gfxinfo FrameTimeline only; no strict SurfaceFlinger physical-present correlation; diagnostic results are never acceptance PASS' -notin $preDeviceMetadata -or
        $preDeviceFrames.Count -ne 20 -or
        $preDeviceSamples.Count -ne 10 -or
        'status=inconclusive' -notin $endToEndOutput
    ) {
        throw 'The host end-to-end fixture did not publish the typed artifact identity and final diagnostic verdict.'
    }
    Remove-Variable neneFrameFixtureState -Scope Global -ErrorAction SilentlyContinue

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

    Write-Output "M2 frame protocol state validation: PASS"
}
finally {
    $resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)
    $resolvedSystemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTemporaryRoot.StartsWith($resolvedSystemTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

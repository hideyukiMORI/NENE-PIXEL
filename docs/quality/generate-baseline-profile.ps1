[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$')]
    [string]$EvidenceId = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:EvidenceSchema = 'nene-pixel-baseline-profile-evidence-v1'
$script:ProducerTask = ':quality:baseline-profile:connectedNonMinifiedReleaseAndroidTest'
$script:ProfileRelativePath = 'app/android/src/main/generated/baselineProfiles/baseline-prof.txt'
$script:HashRelativePath = 'app/android/src/main/generated/baselineProfiles.sha256'
$script:MergedRelativePath = 'app/android/build/intermediates/baselineprofiles/main/merged/baseline-prof.txt'
$script:ProducerOutputRelativePath =
    'quality/baseline-profile/build/outputs/connected_android_test_additional_output/nonMinifiedRelease'
$script:ProducerResultsRelativePath =
    'quality/baseline-profile/build/outputs/androidTest-results/connected/nonMinifiedRelease'
$script:AppApkRelativePath = 'app/android/build/outputs/apk/nonMinifiedRelease/android-nonMinifiedRelease.apk'
$script:TestApkRelativePath =
    'quality/baseline-profile/build/outputs/apk/nonMinifiedRelease/baseline-profile-nonMinifiedRelease.apk'

function Get-Sha256Hex {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [System.BitConverter]::ToString($sha256.ComputeHash($Bytes)).Replace('-', '').ToLowerInvariant()
    }
    finally {
        $sha256.Dispose()
    }
}

function Get-FileSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required artifact is missing: $Path"
    }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-FileState {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        return [pscustomobject]@{ Exists = $true; Bytes = [System.IO.File]::ReadAllBytes($Path) }
    }
    return [pscustomobject]@{ Exists = $false; Bytes = $null }
}

function Restore-FileState {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$State
    )

    if ($State.Exists) {
        [System.IO.File]::WriteAllBytes($Path, $State.Bytes)
    } elseif (Test-Path -LiteralPath $Path -PathType Leaf) {
        Remove-Item -LiteralPath $Path -Force
    }
}

function Get-CanonicalProfileRecord {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Baseline Profile is missing: $Path"
    }
    $utf8 = [System.Text.UTF8Encoding]::new($false, $true)
    $rawBytes = [System.IO.File]::ReadAllBytes($Path)
    $text = $utf8.GetString($rawBytes)
    if ($text.StartsWith([char]0xFEFF)) {
        throw "Baseline Profile must be UTF-8 without a byte-order mark: $Path"
    }
    $normalized = $text.Replace("`r`n", "`n")
    if ($normalized.Contains("`r")) {
        throw "Baseline Profile permits only LF or CRLF line separators: $Path"
    }
    if ($normalized.EndsWith("`n", [System.StringComparison]::Ordinal)) {
        $normalized = $normalized.Substring(0, $normalized.Length - 1)
    }
    if ([string]::IsNullOrWhiteSpace($normalized)) {
        throw "Baseline Profile must contain at least one rule: $Path"
    }
    $rules = @($normalized -split "`n")
    if ($rules.Where({ [string]::IsNullOrEmpty($_) }).Count -ne 0) {
        throw "Baseline Profile must not contain blank rules: $Path"
    }
    $uniqueRules = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($rule in $rules) {
        if (-not $uniqueRules.Add($rule)) {
            throw "Baseline Profile must not contain duplicate rules: $rule"
        }
    }
    $canonicalBytes = $utf8.GetBytes([string]::Join("`n", $rules))
    return [pscustomobject]@{
        Path = $Path
        RawByteCount = $rawBytes.Length
        RawSha256 = Get-Sha256Hex -Bytes $rawBytes
        RuleCount = $rules.Count
        CanonicalByteCount = $canonicalBytes.Length
        CanonicalSha256 = Get-Sha256Hex -Bytes $canonicalBytes
        CanonicalBytes = $canonicalBytes
    }
}

function Get-GradleTaskOutcomes {
    param([Parameter(Mandatory = $true)][string[]]$OutputLines)

    $outcomes = [ordered]@{}
    foreach ($line in $OutputLines) {
        $match = [regex]::Match(
            $line,
            '^> Task (?<task>\S+?)(?: (?<outcome>UP-TO-DATE|FROM-CACHE|SKIPPED|NO-SOURCE|FAILED))?$'
        )
        if ($match.Success) {
            $outcome = $match.Groups['outcome'].Value
            if ($outcome.Length -eq 0) {
                $outcome = 'EXECUTED'
            }
            $outcomes[$match.Groups['task'].Value] = $outcome
        }
    }
    return $outcomes
}

function Test-CanonicalProfileEquality {
    param(
        [Parameter(Mandatory = $true)]$First,
        [Parameter(Mandatory = $true)]$Second
    )

    if ($First.CanonicalSha256 -ne $Second.CanonicalSha256) {
        return $false
    }
    $firstBase64 = [System.Convert]::ToBase64String($First.CanonicalBytes)
    $secondBase64 = [System.Convert]::ToBase64String($Second.CanonicalBytes)
    return $firstBase64 -ceq $secondBase64
}

function Assert-FreshProducerOutput {
    param(
        [Parameter(Mandatory = $true)][int]$ExitCode,
        [Parameter(Mandatory = $true)][string[]]$OutputLines,
        [Parameter(Mandatory = $true)][datetime]$StartedUtc,
        [Parameter(Mandatory = $true)][string]$ProducerOutputDirectory
    )

    if ($ExitCode -ne 0) {
        throw "Baseline Profile Gradle invocation failed with exit code $ExitCode."
    }
    $joinedOutput = [string]::Join("`n", $OutputLines)
    if ($joinedOutput -match '(?i)failed to pull') {
        throw 'Baseline Profile output pull failed.'
    }
    $outcomes = Get-GradleTaskOutcomes -OutputLines $OutputLines
    if (-not $outcomes.Contains($script:ProducerTask)) {
        throw "Gradle output did not report the producer task: $($script:ProducerTask)"
    }
    if ($outcomes[$script:ProducerTask] -ne 'EXECUTED') {
        throw "The producer task did not execute: $($outcomes[$script:ProducerTask])"
    }
    if (-not (Test-Path -LiteralPath $ProducerOutputDirectory -PathType Container)) {
        throw 'The producer output directory is missing.'
    }
    $freshProfiles = @(
        Get-ChildItem -LiteralPath $ProducerOutputDirectory -Recurse -File -Filter '*baseline-prof*.txt' |
            Where-Object { $_.LastWriteTimeUtc -ge $StartedUtc }
    )
    if ($freshProfiles.Count -eq 0) {
        throw 'No fresh pulled producer profile was written during this invocation.'
    }
    $records = @($freshProfiles | ForEach-Object { Get-CanonicalProfileRecord -Path $_.FullName })
    foreach ($record in $records | Select-Object -Skip 1) {
        if (-not (Test-CanonicalProfileEquality -First $records[0] -Second $record)) {
            throw 'Fresh producer profile copies disagree within one invocation.'
        }
    }
    return [pscustomobject]@{
        TaskOutcomes = $outcomes
        Profiles = $records
        CanonicalRecord = $records[0]
    }
}

function Assert-InvocationContent {
    param(
        [Parameter(Mandatory = $true)]$ProducerResult,
        [Parameter(Mandatory = $true)][string]$MergedProfilePath,
        [Parameter(Mandatory = $true)][string]$SourceProfilePath
    )

    $merged = Get-CanonicalProfileRecord -Path $MergedProfilePath
    $source = Get-CanonicalProfileRecord -Path $SourceProfilePath
    $producer = $ProducerResult.CanonicalRecord
    if (
        -not (Test-CanonicalProfileEquality -First $producer -Second $merged) -or
        -not (Test-CanonicalProfileEquality -First $producer -Second $source)
    ) {
        throw 'Fresh producer, merged, and source profiles must have exact canonical rule and flag equality.'
    }
    return [pscustomobject]@{
        Producer = $ProducerResult.CanonicalRecord
        Merged = $merged
        Source = $source
    }
}

function Assert-GenerationPair {
    param(
        [Parameter(Mandatory = $true)]$First,
        [Parameter(Mandatory = $true)]$Second
    )

    if ($First.SourceRevision -ne $Second.SourceRevision) {
        throw 'Generation source revisions differ.'
    }
    if ($First.AppApkSha256 -ne $Second.AppApkSha256 -or $First.TestApkSha256 -ne $Second.TestApkSha256) {
        throw 'Generation APK identities differ.'
    }
    if (-not (Test-CanonicalProfileEquality -First $First.Profile.Source -Second $Second.Profile.Source)) {
        throw 'Baseline Profile generation was not reproducible with exact rule and flag equality.'
    }
}

function New-ExclusiveEvidenceDirectory {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (Test-Path -LiteralPath $Path) {
        throw "Evidence path already exists and will not be overwritten: $Path"
    }
    New-Item -ItemType Directory -Path $Path | Out-Null
}

function Copy-AvailableEvidenceTree {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    if (-not (Test-Path -LiteralPath $Source -PathType Container)) {
        return
    }
    New-Item -ItemType Directory -Path $Destination | Out-Null
    Get-ChildItem -LiteralPath $Source -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $Destination -Recurse
    }
}

function Move-PreexistingEvidenceTree {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    if (-not (Test-Path -LiteralPath $Source -PathType Container)) {
        return
    }
    if (Test-Path -LiteralPath $Destination) {
        throw "Preexisting evidence destination already exists: $Destination"
    }
    Move-Item -LiteralPath $Source -Destination $Destination
}

function Write-JsonFile {
    param(
        [Parameter(Mandatory = $true)]$Value,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $Value | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $Path -Encoding utf8NoBOM
}

function Get-RetainedFileRecords {
    param([Parameter(Mandatory = $true)][string]$EvidenceDirectory)

    $root = [System.IO.Path]::GetFullPath($EvidenceDirectory)
    return @(
        Get-ChildItem -LiteralPath $root -Recurse -File | Sort-Object FullName | ForEach-Object {
            [ordered]@{
                path = [System.IO.Path]::GetRelativePath($root, $_.FullName).Replace('\', '/')
                byte_count = $_.Length
                sha256 = Get-FileSha256 -Path $_.FullName
            }
        }
    )
}

function Invoke-GradleCommand {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$LogPath,
        [Parameter(Mandatory = $true)][string[]]$GradleArguments
    )

    $previousNativePreference = $PSNativeCommandUseErrorActionPreference
    $PSNativeCommandUseErrorActionPreference = $false
    Push-Location $RepositoryRoot
    try {
        $output = @(& .\gradlew.bat @GradleArguments 2>&1)
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
        $PSNativeCommandUseErrorActionPreference = $previousNativePreference
    }
    $outputLines = @($output | ForEach-Object { $_.ToString() })
    $outputLines | Set-Content -LiteralPath $LogPath -Encoding utf8NoBOM
    $outputLines | ForEach-Object { Write-Host $_ }
    return [pscustomobject]@{ ExitCode = $exitCode; OutputLines = $outputLines }
}

function Get-ManifestProfileRecord {
    param([Parameter(Mandatory = $true)]$Record)

    return [ordered]@{
        raw_byte_count = $Record.RawByteCount
        raw_sha256 = $Record.RawSha256
        rule_count = $Record.RuleCount
        canonical_byte_count = $Record.CanonicalByteCount
        canonical_sha256 = $Record.CanonicalSha256
    }
}

function Invoke-GenerationInvocation {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$EvidenceRoot,
        [Parameter(Mandatory = $true)][string]$EvidenceIdentity,
        [Parameter(Mandatory = $true)][int]$Ordinal,
        [Parameter(Mandatory = $true)][scriptblock]$GradleInvoker
    )

    $invocationDirectory = Join-Path $EvidenceRoot ("invocation-{0}" -f $Ordinal)
    New-ExclusiveEvidenceDirectory -Path $invocationDirectory
    $producerOutput = Join-Path $RepositoryRoot $script:ProducerOutputRelativePath
    $producerResults = Join-Path $RepositoryRoot $script:ProducerResultsRelativePath
    Move-PreexistingEvidenceTree -Source $producerOutput `
        -Destination (Join-Path $invocationDirectory 'preexisting-producer-output')
    Move-PreexistingEvidenceTree -Source $producerResults `
        -Destination (Join-Path $invocationDirectory 'preexisting-producer-results')
    $startedUtc = [datetime]::UtcNow
    $sourceRevision = (& git -C $RepositoryRoot rev-parse HEAD).Trim()
    $logPath = Join-Path $invocationDirectory 'gradle-output.log'
    $gradle = $null
    $failure = $null
    try {
        $gradle = & $GradleInvoker -RepositoryRoot $RepositoryRoot -LogPath $logPath `
            -GradleArguments @(':app:android:generateBaselineProfile', '--console=plain')
    }
    catch {
        $failure = $_.Exception.Message
        $_.Exception.ToString() | Set-Content -LiteralPath $logPath -Encoding utf8NoBOM
    }
    $endedUtc = [datetime]::UtcNow

    Copy-AvailableEvidenceTree -Source $producerOutput -Destination (Join-Path $invocationDirectory 'producer-output')
    Copy-AvailableEvidenceTree -Source $producerResults -Destination (Join-Path $invocationDirectory 'producer-results')

    $mergedPath = Join-Path $RepositoryRoot $script:MergedRelativePath
    $sourcePath = Join-Path $RepositoryRoot $script:ProfileRelativePath
    foreach ($entry in @(@($mergedPath, 'merged-profile.txt'), @($sourcePath, 'source-profile.txt'))) {
        if (Test-Path -LiteralPath $entry[0] -PathType Leaf) {
            Copy-Item -LiteralPath $entry[0] -Destination (Join-Path $invocationDirectory $entry[1])
        }
    }

    $producer = $null
    $profile = $null
    $appApkSha256 = $null
    $testApkSha256 = $null
    if ($null -eq $failure) {
        try {
            $producer = Assert-FreshProducerOutput -ExitCode $gradle.ExitCode `
                -OutputLines $gradle.OutputLines -StartedUtc $startedUtc `
                -ProducerOutputDirectory $producerOutput
            $profile = Assert-InvocationContent -ProducerResult $producer -MergedProfilePath $mergedPath `
                -SourceProfilePath $sourcePath
            $appApkSha256 = Get-FileSha256 -Path (Join-Path $RepositoryRoot $script:AppApkRelativePath)
            $testApkSha256 = Get-FileSha256 -Path (Join-Path $RepositoryRoot $script:TestApkRelativePath)
        }
        catch {
            $failure = $_.Exception.Message
        }
    }

    $gradleExitCode = if ($null -eq $gradle) { $null } else { $gradle.ExitCode }

    $manifest = [ordered]@{
        schema = $script:EvidenceSchema
        evidence_id = $EvidenceIdentity
        invocation = $Ordinal
        status = if ($null -eq $failure) { 'valid' } else { 'invalid' }
        failure = $failure
        started_utc = $startedUtc.ToString('o')
        ended_utc = $endedUtc.ToString('o')
        source_revision = $sourceRevision
        gradle_exit_code = $gradleExitCode
        task_outcomes = if ($null -eq $gradle) {
            [ordered]@{}
        } else {
            Get-GradleTaskOutcomes -OutputLines @($gradle.OutputLines)
        }
        producer_profiles = if ($null -eq $producer) {
            $null
        } else {
            @($producer.Profiles | ForEach-Object { Get-ManifestProfileRecord $_ })
        }
        merged_profile = if ($null -eq $profile) { $null } else { Get-ManifestProfileRecord $profile.Merged }
        source_profile = if ($null -eq $profile) { $null } else { Get-ManifestProfileRecord $profile.Source }
        app_apk_sha256 = $appApkSha256
        test_apk_sha256 = $testApkSha256
        retained_files = Get-RetainedFileRecords -EvidenceDirectory $invocationDirectory
    }
    $manifestPath = Join-Path $invocationDirectory 'manifest.json'
    Write-JsonFile -Value $manifest -Path $manifestPath
    if ($null -ne $failure) {
        throw "Generation invocation $Ordinal is invalid: $failure"
    }
    return [pscustomobject]@{
        SourceRevision = $sourceRevision
        AppApkSha256 = $appApkSha256
        TestApkSha256 = $testApkSha256
        Profile = $profile
        ManifestSha256 = Get-FileSha256 -Path $manifestPath
    }
}

function Invoke-BaselineProfileEvidenceGeneration {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$EvidenceIdentity,
        [scriptblock]$GradleInvoker = {
            param(
                [Parameter(Mandatory = $true)][string]$RepositoryRoot,
                [Parameter(Mandatory = $true)][string]$LogPath,
                [Parameter(Mandatory = $true)][string[]]$GradleArguments
            )

            Invoke-GradleCommand -RepositoryRoot $RepositoryRoot -LogPath $LogPath `
                -GradleArguments $GradleArguments
        }
    )

    if (@(& git -C $RepositoryRoot status --porcelain).Count -ne 0) {
        throw 'Baseline Profile generation must start from a clean standalone clone.'
    }
    $evidenceRoot = Join-Path $RepositoryRoot "build/reports/baseline-profile-generation/$EvidenceIdentity"
    New-ExclusiveEvidenceDirectory -Path $evidenceRoot
    $profilePath = Join-Path $RepositoryRoot $script:ProfileRelativePath
    $hashPath = Join-Path $RepositoryRoot $script:HashRelativePath
    $originalProfile = Get-FileState -Path $profilePath
    $originalHash = Get-FileState -Path $hashPath
    if ($originalProfile.Exists) {
        [System.IO.File]::WriteAllBytes(
            (Join-Path $evidenceRoot 'pre-generation-source-profile.txt'),
            $originalProfile.Bytes
        )
    }
    if ($originalHash.Exists) {
        [System.IO.File]::WriteAllBytes(
            (Join-Path $evidenceRoot 'pre-generation-hash.txt'),
            $originalHash.Bytes
        )
    }

    try {
        $first = Invoke-GenerationInvocation -RepositoryRoot $RepositoryRoot -EvidenceRoot $evidenceRoot `
            -EvidenceIdentity $EvidenceIdentity -Ordinal 1 -GradleInvoker $GradleInvoker
        $second = Invoke-GenerationInvocation -RepositoryRoot $RepositoryRoot -EvidenceRoot $evidenceRoot `
            -EvidenceIdentity $EvidenceIdentity -Ordinal 2 -GradleInvoker $GradleInvoker

        $pairFailure = $null
        try {
            Assert-GenerationPair -First $first -Second $second
        }
        catch {
            $pairFailure = $_.Exception.Message
        }
        $pairManifest = [ordered]@{
            schema = $script:EvidenceSchema
            evidence_id = $EvidenceIdentity
            status = if ($null -eq $pairFailure) { 'matched' } else { 'mismatch' }
            failure = $pairFailure
            source_revision = $first.SourceRevision
            app_apk_sha256 = $first.AppApkSha256
            test_apk_sha256 = $first.TestApkSha256
            canonical_rule_count = $first.Profile.Source.RuleCount
            canonical_sha256 = $first.Profile.Source.CanonicalSha256
            invocation_manifest_sha256 = @($first.ManifestSha256, $second.ManifestSha256)
        }
        $pairManifestPath = Join-Path $evidenceRoot 'pair-manifest.json'
        Write-JsonFile -Value $pairManifest -Path $pairManifestPath
        if ($null -ne $pairFailure) {
            throw $pairFailure
        }

        [System.IO.File]::WriteAllBytes($profilePath, $second.Profile.Source.CanonicalBytes)
        Set-Content -LiteralPath $hashPath -Value $second.Profile.Source.CanonicalSha256 -Encoding ascii

        $validationLogPath = Join-Path $evidenceRoot 'validation-output.log'
        $validation = & $GradleInvoker -RepositoryRoot $RepositoryRoot -LogPath $validationLogPath `
            -GradleArguments @('validateBaselineProfile', '--console=plain')
        if ($validation.ExitCode -ne 0) {
            throw "validateBaselineProfile failed with exit code $($validation.ExitCode)."
        }
        $acceptanceManifest = [ordered]@{
            schema = $script:EvidenceSchema
            evidence_id = $EvidenceIdentity
            status = 'accepted'
            pair_manifest_sha256 = Get-FileSha256 -Path $pairManifestPath
            validation_output_sha256 = Get-FileSha256 -Path $validationLogPath
            canonical_sha256 = $second.Profile.Source.CanonicalSha256
        }
        Write-JsonFile -Value $acceptanceManifest -Path (Join-Path $evidenceRoot 'acceptance-manifest.json')
    }
    catch {
        Restore-FileState -Path $profilePath -State $originalProfile
        Restore-FileState -Path $hashPath -State $originalHash
        throw
    }
    Write-Output "BASELINE_PROFILE_EVIDENCE=pass"
    Write-Output "EVIDENCE_SCHEMA=$($script:EvidenceSchema)"
    Write-Output "EVIDENCE_DIRECTORY=$evidenceRoot"
    Write-Output "CANONICAL_SHA256=$($second.Profile.Source.CanonicalSha256)"
}

if ($MyInvocation.InvocationName -ne '.') {
    if ([string]::IsNullOrWhiteSpace($EvidenceId)) {
        throw 'EvidenceId is required.'
    }
    $repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
    Invoke-BaselineProfileEvidenceGeneration -RepositoryRoot $repositoryRoot -EvidenceIdentity $EvidenceId
}

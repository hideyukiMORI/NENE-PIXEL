Set-StrictMode -Version Latest

$script:BaselineProfileEvidenceSchema = 'nene-pixel-baseline-profile-evidence-v1'
$script:BaselineProfileProducerTask = ':quality:baseline-profile:connectedNonMinifiedReleaseAndroidTest'

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

function Assert-BaselineProfileEvidenceValue {
    param(
        [Parameter(Mandatory = $true)][AllowNull()]$Actual,
        [Parameter(Mandatory = $true)][AllowNull()]$Expected,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if ($Actual -cne $Expected) {
        throw "$Description differs. Expected=$Expected Actual=$Actual"
    }
}

function Read-BaselineProfileEvidenceJson {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Baseline Profile evidence manifest is missing: $Path"
    }
    try {
        return Get-Content -LiteralPath $Path -Raw -Encoding utf8 | ConvertFrom-Json
    }
    catch {
        throw "Baseline Profile evidence manifest is invalid JSON: $Path. $($_.Exception.Message)"
    }
}

function Assert-BaselineProfileManifestRecord {
    param(
        [Parameter(Mandatory = $true)]$Expected,
        [Parameter(Mandatory = $true)]$Actual,
        [Parameter(Mandatory = $true)][string]$Description
    )

    Assert-BaselineProfileEvidenceValue $Actual.raw_byte_count $Expected.RawByteCount "$Description raw byte count"
    Assert-BaselineProfileEvidenceValue $Actual.raw_sha256 $Expected.RawSha256 "$Description raw SHA-256"
    Assert-BaselineProfileEvidenceValue $Actual.rule_count $Expected.RuleCount "$Description rule count"
    Assert-BaselineProfileEvidenceValue $Actual.canonical_byte_count $Expected.CanonicalByteCount `
        "$Description canonical byte count"
    Assert-BaselineProfileEvidenceValue $Actual.canonical_sha256 $Expected.CanonicalSha256 `
        "$Description canonical SHA-256"
}

function Assert-BaselineProfileEvidenceTimeout {
    param(
        [Parameter(Mandatory = $true)]$Value,
        [Parameter(Mandatory = $true)][int]$Maximum,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $seconds = [int]$Value
    if ($seconds -le 0 -or $seconds -gt $Maximum) {
        throw "$Description must be positive and no greater than $Maximum seconds. Actual=$seconds"
    }
    return $seconds
}

function Assert-BaselineProfileRetainedFiles {
    param(
        [Parameter(Mandatory = $true)][string]$InvocationDirectory,
        [Parameter(Mandatory = $true)][object[]]$Records
    )

    $resolvedRoot = [System.IO.Path]::GetFullPath($InvocationDirectory).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    )
    $rootPrefix = $resolvedRoot + [System.IO.Path]::DirectorySeparatorChar
    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($record in $Records) {
        if ([string]::IsNullOrWhiteSpace([string]$record.path)) {
            throw 'Retained evidence path must be non-empty.'
        }
        $resolvedPath = [System.IO.Path]::GetFullPath((Join-Path $resolvedRoot ([string]$record.path)))
        if (-not $resolvedPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Retained evidence path escapes its invocation directory: $($record.path)"
        }
        if (-not $seen.Add($resolvedPath)) {
            throw "Retained evidence path is duplicated: $($record.path)"
        }
        if (-not (Test-Path -LiteralPath $resolvedPath -PathType Leaf)) {
            throw "Retained evidence file is missing: $resolvedPath"
        }
        $file = Get-Item -LiteralPath $resolvedPath
        Assert-BaselineProfileEvidenceValue $file.Length ([long]$record.byte_count) `
            "Retained evidence byte count for $($record.path)"
        Assert-BaselineProfileEvidenceValue (Get-FileSha256 $resolvedPath) ([string]$record.sha256) `
            "Retained evidence SHA-256 for $($record.path)"
    }
}

function Read-BaselineProfileAcceptanceEvidence {
    param(
        [Parameter(Mandatory = $true)][string]$AcceptanceManifestPath,
        [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-f]{64}$')]
        [string]$ExpectedAcceptanceManifestSha256,
        [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-f]{40}$')]
        [string]$ExpectedSourceRevision,
        [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-f]{64}$')]
        [string]$ExpectedAppApkSha256,
        [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-f]{64}$')]
        [string]$ExpectedTestApkSha256,
        [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-f]{64}$')]
        [string]$ExpectedPairManifestSha256,
        [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-f]{64}$')]
        [string]$ExpectedCanonicalSha256,
        [string]$EvidenceRoot = ''
    )

    $resolvedAcceptance = (Resolve-Path -LiteralPath $AcceptanceManifestPath).Path
    if ([string]::IsNullOrWhiteSpace($EvidenceRoot) -and
        [System.IO.Path]::GetFileName($resolvedAcceptance) -cne 'acceptance-manifest.json') {
        throw 'Acceptance evidence must use the canonical acceptance-manifest.json path.'
    }
    Assert-BaselineProfileEvidenceValue (Get-FileSha256 $resolvedAcceptance) `
        $ExpectedAcceptanceManifestSha256 'Acceptance manifest SHA-256'
    $evidenceRoot = if ([string]::IsNullOrWhiteSpace($EvidenceRoot)) {
        Split-Path -Parent $resolvedAcceptance
    } else {
        (Resolve-Path -LiteralPath $EvidenceRoot).Path
    }
    $acceptance = Read-BaselineProfileEvidenceJson $resolvedAcceptance
    Assert-BaselineProfileEvidenceValue $acceptance.schema $script:BaselineProfileEvidenceSchema `
        'Acceptance manifest schema'
    Assert-BaselineProfileEvidenceValue $acceptance.status 'accepted' 'Acceptance manifest status'
    if ([string]::IsNullOrWhiteSpace([string]$acceptance.evidence_id)) {
        throw 'Acceptance manifest evidence ID must be non-empty.'
    }
    Assert-BaselineProfileEvidenceValue $acceptance.pair_manifest_sha256 `
        $ExpectedPairManifestSha256 'Acceptance pair manifest SHA-256'
    Assert-BaselineProfileEvidenceValue $acceptance.canonical_sha256 `
        $ExpectedCanonicalSha256 'Acceptance canonical SHA-256'
    $producerTimeoutSeconds = Assert-BaselineProfileEvidenceTimeout `
        $acceptance.producer_timeout_seconds 1800 'Acceptance producer timeout'
    $validationTimeoutSeconds = Assert-BaselineProfileEvidenceTimeout `
        $acceptance.validation_timeout_seconds 300 'Acceptance validation timeout'

    $pairPath = Join-Path $evidenceRoot 'pair-manifest.json'
    Assert-BaselineProfileEvidenceValue (Get-FileSha256 $pairPath) `
        $ExpectedPairManifestSha256 'Pair manifest SHA-256'
    $pair = Read-BaselineProfileEvidenceJson $pairPath
    Assert-BaselineProfileEvidenceValue $pair.schema $script:BaselineProfileEvidenceSchema 'Pair manifest schema'
    Assert-BaselineProfileEvidenceValue $pair.evidence_id $acceptance.evidence_id 'Pair evidence ID'
    Assert-BaselineProfileEvidenceValue $pair.status 'matched' 'Pair status'
    Assert-BaselineProfileEvidenceValue $pair.failure $null 'Pair failure'
    Assert-BaselineProfileEvidenceValue $pair.source_revision $ExpectedSourceRevision 'Pair source revision'
    Assert-BaselineProfileEvidenceValue $pair.app_apk_sha256 $ExpectedAppApkSha256 'Pair app APK SHA-256'
    Assert-BaselineProfileEvidenceValue $pair.test_apk_sha256 $ExpectedTestApkSha256 'Pair test APK SHA-256'
    Assert-BaselineProfileEvidenceValue $pair.canonical_sha256 $ExpectedCanonicalSha256 `
        'Pair canonical SHA-256'
    Assert-BaselineProfileEvidenceValue $pair.producer_timeout_seconds $producerTimeoutSeconds `
        'Pair producer timeout'
    Assert-BaselineProfileEvidenceValue $pair.validation_timeout_seconds $validationTimeoutSeconds `
        'Pair validation timeout'
    if (@($pair.invocation_manifest_sha256).Count -ne 2) {
        throw 'Pair manifest must link exactly two invocation manifests.'
    }

    $validationLogPath = Join-Path $evidenceRoot 'validation-output.log'
    Assert-BaselineProfileEvidenceValue (Get-FileSha256 $validationLogPath) `
        $acceptance.validation_output_sha256 'Final validation output SHA-256'

    $canonicalRecords = @()
    $invocations = @()
    foreach ($ordinal in 1..2) {
        $invocationDirectory = Join-Path $evidenceRoot "invocation-$ordinal"
        $manifestPath = Join-Path $invocationDirectory 'manifest.json'
        Assert-BaselineProfileEvidenceValue (Get-FileSha256 $manifestPath) `
            ([string]$pair.invocation_manifest_sha256[$ordinal - 1]) `
            "Invocation $ordinal manifest SHA-256"
        $invocation = Read-BaselineProfileEvidenceJson $manifestPath
        Assert-BaselineProfileEvidenceValue $invocation.schema $script:BaselineProfileEvidenceSchema `
            "Invocation $ordinal schema"
        Assert-BaselineProfileEvidenceValue $invocation.evidence_id $acceptance.evidence_id `
            "Invocation $ordinal evidence ID"
        Assert-BaselineProfileEvidenceValue $invocation.invocation $ordinal "Invocation $ordinal ordinal"
        Assert-BaselineProfileEvidenceValue $invocation.status 'valid' "Invocation $ordinal status"
        Assert-BaselineProfileEvidenceValue $invocation.failure $null "Invocation $ordinal failure"
        Assert-BaselineProfileEvidenceValue $invocation.restoration_blocked $false `
            "Invocation $ordinal restoration state"
        Assert-BaselineProfileEvidenceValue $invocation.gradle_exit_code 0 `
            "Invocation $ordinal Gradle exit code"
        Assert-BaselineProfileEvidenceValue $invocation.source_revision $ExpectedSourceRevision `
            "Invocation $ordinal source revision"
        Assert-BaselineProfileEvidenceValue $invocation.app_apk_sha256 $ExpectedAppApkSha256 `
            "Invocation $ordinal app APK SHA-256"
        Assert-BaselineProfileEvidenceValue $invocation.test_apk_sha256 $ExpectedTestApkSha256 `
            "Invocation $ordinal test APK SHA-256"
        Assert-BaselineProfileEvidenceValue $invocation.timeout_seconds $producerTimeoutSeconds `
            "Invocation $ordinal producer timeout"
        Assert-BaselineProfileEvidenceValue `
            $invocation.task_outcomes.PSObject.Properties[$script:BaselineProfileProducerTask].Value `
            'EXECUTED' "Invocation $ordinal producer task outcome"
        $started = [datetime]::Parse(
            [string]$invocation.started_utc,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::RoundtripKind
        )
        $ended = [datetime]::Parse(
            [string]$invocation.ended_utc,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::RoundtripKind
        )
        if ($ended -lt $started) {
            throw "Invocation $ordinal end precedes its start."
        }
        Assert-BaselineProfileRetainedFiles $invocationDirectory @($invocation.retained_files)
        $sourcePath = Join-Path $invocationDirectory 'source-profile.txt'
        $canonical = Get-CanonicalProfileRecord $sourcePath
        Assert-BaselineProfileManifestRecord $canonical $invocation.source_profile `
            "Invocation $ordinal source profile"
        Assert-BaselineProfileEvidenceValue $canonical.CanonicalSha256 $ExpectedCanonicalSha256 `
            "Invocation $ordinal canonical SHA-256"
        Assert-BaselineProfileEvidenceValue $invocation.source_profile.rule_count `
            $pair.canonical_rule_count "Invocation $ordinal canonical rule count"
        foreach ($producer in @($invocation.producer_profiles)) {
            Assert-BaselineProfileEvidenceValue $producer.canonical_sha256 $ExpectedCanonicalSha256 `
                "Invocation $ordinal producer canonical SHA-256"
            Assert-BaselineProfileEvidenceValue $producer.rule_count $pair.canonical_rule_count `
                "Invocation $ordinal producer rule count"
        }
        Assert-BaselineProfileEvidenceValue $invocation.merged_profile.canonical_sha256 `
            $ExpectedCanonicalSha256 "Invocation $ordinal merged canonical SHA-256"
        Assert-BaselineProfileEvidenceValue $invocation.merged_profile.rule_count `
            $pair.canonical_rule_count "Invocation $ordinal merged rule count"
        $mergedCanonical = Get-CanonicalProfileRecord (Join-Path $invocationDirectory 'merged-profile.txt')
        Assert-BaselineProfileManifestRecord $mergedCanonical $invocation.merged_profile `
            "Invocation $ordinal merged profile"
        $retainedPaths = @($invocation.retained_files | ForEach-Object { [string]$_.path })
        foreach ($requiredPath in @('gradle-output.log', 'merged-profile.txt', 'source-profile.txt')) {
            if ($retainedPaths -notcontains $requiredPath) {
                throw "Invocation $ordinal retained evidence omits $requiredPath."
            }
        }
        if (@($retainedPaths | Where-Object { $_ -like 'producer-results/*test-result-exit-code.txt' }).Count -ne 1) {
            throw "Invocation $ordinal must retain one producer test exit-code artifact."
        }
        $testExitRelativePath = @(
            $retainedPaths | Where-Object { $_ -like 'producer-results/*test-result-exit-code.txt' }
        )[0]
        $testExitBytes = [System.IO.File]::ReadAllBytes(
            (Join-Path $invocationDirectory $testExitRelativePath)
        )
        if ($testExitBytes.Length -ne 1 -or $testExitBytes[0] -ne [byte][char]'0') {
            throw "Invocation $ordinal producer test exit code must be exact one-byte ASCII 0."
        }
        $producerProfilePaths = @(
            $retainedPaths | Where-Object { $_ -like 'producer-output/*baseline-prof*.txt' }
        )
        if ($producerProfilePaths.Count -ne @($invocation.producer_profiles).Count -or
            $producerProfilePaths.Count -eq 0) {
            throw "Invocation $ordinal retained producer profiles do not match its manifest."
        }
        for ($producerIndex = 0; $producerIndex -lt $producerProfilePaths.Count; $producerIndex++) {
            $producerCanonical = Get-CanonicalProfileRecord `
                (Join-Path $invocationDirectory $producerProfilePaths[$producerIndex])
            Assert-BaselineProfileManifestRecord $producerCanonical `
                $invocation.producer_profiles[$producerIndex] `
                "Invocation $ordinal producer profile $($producerIndex + 1)"
        }
        $canonicalRecords += $canonical
        $invocations += $invocation
    }
    if (-not (Test-CanonicalProfileEquality $canonicalRecords[0] $canonicalRecords[1])) {
        throw 'Accepted invocation source profiles differ in canonical rules or flags.'
    }
    if ([datetime]::Parse([string]$invocations[1].started_utc) -lt `
        [datetime]::Parse([string]$invocations[0].ended_utc)) {
        throw 'Invocation 2 starts before invocation 1 finishes.'
    }

    return [pscustomobject]@{
        EvidenceRoot = $evidenceRoot
        EvidenceId = [string]$acceptance.evidence_id
        AcceptanceManifestPath = $resolvedAcceptance
        AcceptanceManifestSha256 = $ExpectedAcceptanceManifestSha256
        PairManifestSha256 = $ExpectedPairManifestSha256
        InvocationManifestSha256 = @($pair.invocation_manifest_sha256)
        ValidationOutputSha256 = [string]$acceptance.validation_output_sha256
        SourceRevision = $ExpectedSourceRevision
        AppApkSha256 = $ExpectedAppApkSha256
        TestApkSha256 = $ExpectedTestApkSha256
        CanonicalRuleCount = [int]$pair.canonical_rule_count
        CanonicalSha256 = $ExpectedCanonicalSha256
    }
}

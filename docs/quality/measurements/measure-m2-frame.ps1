# Lane 3 revision 2026-09-23 (#120): order, no fail-fast, verdict by analyzer only, experiment schema v5, window_x2 diagnostic family
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("debug", "release-like")]
    [string]$Variant,

    [Parameter(Mandatory = $true)]
    [string]$DeviceSerial,

    [Parameter(Mandatory = $true)]
    [string]$ApkPath,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{40}$")]
    [string]$SourceCommit,

    [Parameter(Mandatory = $true)]
    [string]$ExperimentDirectory,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[a-z0-9][a-z0-9-]{2,63}$")]
    [string]$ExperimentId,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{40}$")]
    [string]$BaselineSourceCommit,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{40}$")]
    [string]$CandidateSourceCommit,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{40}$")]
    [string]$BaselineProductionCommit,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{40}$")]
    [string]$CandidateProductionCommit,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$BaselineProductionTreeSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$CandidateProductionTreeSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^\[\d+,\d+\]\[\d+,\d+\]$")]
    [string]$BaselineCanvas16SurfaceBounds,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^\[\d+,\d+\]\[\d+,\d+\]$")]
    [string]$BaselineCanvas256SurfaceBounds,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^\[\d+,\d+\]\[\d+,\d+\]$")]
    [string]$CandidateCanvas16SurfaceBounds,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^\[\d+,\d+\]\[\d+,\d+\]$")]
    [string]$CandidateCanvas256SurfaceBounds,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$BaselineApkSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$CandidateApkSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{40}$")]
    [string]$BaselineProfileGenerationSourceCommit,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{40}$")]
    [string]$CandidateProfileGenerationSourceCommit,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$BaselineProfileGenerationAppApkSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$CandidateProfileGenerationAppApkSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$BaselineProfileGenerationTestApkSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$CandidateProfileGenerationTestApkSha256,

    [Parameter(Mandatory = $true)]
    [string]$BaselineProfileAcceptanceManifestPath,

    [Parameter(Mandatory = $true)]
    [string]$CandidateProfileAcceptanceManifestPath,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$BaselineProfileAcceptanceManifestSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$CandidateProfileAcceptanceManifestSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$BaselineProfilePairManifestSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$CandidateProfilePairManifestSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$BaselineCanonicalProfileSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$CandidateCanonicalProfileSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$BaselinePackagedProfSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$CandidatePackagedProfSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$BaselinePackagedProfmSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$CandidatePackagedProfmSha256,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$CandidateHypothesis,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$ExpectedAffectedCost,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$CorrectnessRisk,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$StopConditions,

    [Parameter(Mandatory = $true)]
    [ValidateSet("diagnostic", "decision")]
    [string]$RunKind,

    [Parameter(Mandatory = $true)]
    [ValidateSet("baseline", "candidate")]
    [string]$CandidateRole,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 4)]
    [int]$ComparisonSequenceIndex,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 2)]
    [int]$Attempt,

    [ValidateSet("speed-profile", "speed")]
    [string]$CompilationMode,

    [Parameter(Mandatory = $true)]
    [ValidateSet(10, 50)]
    [int]$SampleCount,

    [switch]$ValidateExperimentOnly,

    [switch]$ValidateArtifactOnly,

    [switch]$InspectGeometryOnly,

    [string]$InspectionDirectory,

    [string]$PhysicalPresentTraceProcessorPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
# adb writes UTF-8; the localized editor UI (ja/zh strings in `uiautomator dump`) becomes invalid XML
# when the host console decodes native output with a legacy code page such as Shift_JIS.
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

. (Join-Path $PSScriptRoot "m2-package-dexopt.ps1")
. (Join-Path $PSScriptRoot "android-window-state.ps1")
. (Join-Path $PSScriptRoot "../baseline-profile-evidence.ps1")

if (-not $PSBoundParameters.ContainsKey("CompilationMode")) {
    $CompilationMode = if ($Variant -eq "release-like") { "speed-profile" } else { "speed" }
}

$packageName = "io.github.hideyukimori.nenepixel"
$activityName = "$packageName/.MainActivity"
$physicalProfileId = "NENE-P2-ALLDOCUBE-IPL80MP-A16-API36"
$expectedManufacturer = "ALLDOCUBE"
$expectedModel = "iPlay80miniPro"
$expectedProduct = "iPlay80miniPro"
$expectedDevice = "T830"
$expectedApiLevel = 36
$expectedDisplayWidth = 1200
$expectedDisplayHeight = 1920
$expectedDisplayModeId = 1
$expectedRefreshRateHertz = 90.0
$refreshRateToleranceHertz = 0.1
$maximumThermalStatus = 1
$warmupCount = 5
$previewWaitMilliseconds = 100
$moveWaitMilliseconds = 20
$postMovePreviewWaitMilliseconds = 100
$drawWaitMilliseconds = 350
$undoWaitMilliseconds = 150
$undoAttemptLimit = 3
$requiredRotation = 1
$requiredLogicalWidth = 1920
$requiredLogicalHeight = 1200
$requiredRootBounds = "[0,0][1920,1200]"
$geometryId = "initial-fit-centered-v1"
$profileInstallSuccessResult = 1
$inputInjection = "cmd-input-service-direct"
$remotePrefix = "/data/local/tmp/nene-m2-frame-$Variant-$CompilationMode"
$resolvedExperiment =
    if ([System.IO.Path]::IsPathRooted($ExperimentDirectory)) {
        [System.IO.Path]::GetFullPath($ExperimentDirectory)
    } else {
        [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $ExperimentDirectory))
    }
$physicalPresentEnabled = $PSBoundParameters.ContainsKey("PhysicalPresentTraceProcessorPath")
$physicalPresentSchema = "nene-pixel-m2-physical-present-v2"
$frameSchema = "nene-pixel-p4-indexed-actual-app-frame-v8"
$experimentSchema = "nene-pixel-p4-indexed-frame-experiment-v5"
$noSampleInspectionSchema = "nene-pixel-p4-no-sample-inspection-v1"
$baselineProductionCommitRequired = "2f0b617e56f7bcf3d71b5a258a48e0edead354d9"
$workloadCatalog = @(
    [ordered]@{
        workload = "canvas16_tap"
        canvas_width = 16
        canvas_height = 16
        move_event_count = 0
        motion_event_count = 2
        preview_event_count = 1
        commit_event_count = 1
        raw_position_count = 1
        effective_change_count = 1
    },
    [ordered]@{
        workload = "canvas256_repeated_diagonal"
        canvas_width = 256
        canvas_height = 256
        move_event_count = 16
        motion_event_count = 18
        preview_event_count = 17
        commit_event_count = 1
        raw_position_count = 4081
        effective_change_count = 256
    }
)
$workloadOrder = @($workloadCatalog | ForEach-Object { $_.workload })
# Diagnostic slots only (Lane 3 family 3): the exact event, dwell, reset and warmup sequence of
# canvas256_repeated_diagonal on the same clean document, with the actual-size window shown at X2.
# Only the family name and the window preparation/teardown differ; it is never a decision input.
$windowDiagnosticWorkload = "canvas256_repeated_diagonal_window_x2"
$windowDiagnosticScale = "x2"
$windowDiagnosticSpec = [ordered]@{}
foreach ($entry in $workloadCatalog[1].GetEnumerator()) {
    $windowDiagnosticSpec[$entry.Key] = $entry.Value
}
$windowDiagnosticSpec.workload = $windowDiagnosticWorkload
$diagnosticWorkloadCatalog = @($workloadCatalog) + @($windowDiagnosticSpec)
$diagnosticWorkloadOrder = @($diagnosticWorkloadCatalog | ForEach-Object { $_.workload })
$slotWorkloadCatalog = if ($RunKind -eq "diagnostic") { $diagnosticWorkloadCatalog } else { $workloadCatalog }
$slotWorkloadOrder = @($slotWorkloadCatalog | ForEach-Object { $_.workload })
$physicalPresentAnalyzer = Join-Path $PSScriptRoot "analyze-m2-physical-present.ps1"
$physicalTraceState = $null
$physicalAnalysis = $null

if ($physicalPresentEnabled) {
    throw "$physicalPresentSchema collection is exhausted and retained for historical analysis only."
}
# -Variant is a declaration, not evidence; the evidence is the installed APK SHA-256 (Assert-M2InstalledApkIdentity).
if ($Variant -ne "release-like" -or $CompilationMode -ne "speed-profile") {
    throw "The v4 comparison requires release-like and speed-profile for every slot."
}
if ($Attempt -ne 1) {
    throw "The current v4 writer permits only attempt 1; any invalid result stops the experiment."
}

if ($BaselineProductionCommit -cne $baselineProductionCommitRequired) {
    throw "The baseline production commit must be the accepted Lane 3 baseline (main at collection time)."
}
if ($BaselineSourceCommit -ceq $BaselineProductionCommit) {
    throw "The baseline measurement build must be a distinct immutable collector overlay commit."
}
if (
    $BaselineSourceCommit -ceq $CandidateSourceCommit -or
    $BaselineProductionCommit -ceq $CandidateProductionCommit -or
    $BaselineProductionTreeSha256 -ceq $CandidateProductionTreeSha256
) {
    throw "Baseline and candidate production/build identities must be distinct."
}

$maximumAttemptsPerSlot = 1
$replacementRule = "none"

function Assert-M2ExperimentAttemptPolicy {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Manifest
    )

    $propertyNames = @($Manifest.PSObject.Properties.Name)
    if (
        "maximum_attempts_per_slot" -notin $propertyNames -or
        "replacement_rule" -notin $propertyNames
    ) {
        throw "The v4 experiment manifest is missing its attempt policy."
    }
    $rawMaximum = $Manifest.maximum_attempts_per_slot
    if ($rawMaximum -isnot [int] -and $rawMaximum -isnot [long]) {
        throw "The v4 experiment manifest attempt maximum must be an integer."
    }
    $maximum = [int]$rawMaximum
    if ($maximum -ne $maximumAttemptsPerSlot) {
        throw "The v4 experiment manifest attempt maximum must be 1."
    }
    $expectedReplacement = $replacementRule
    if ($Manifest.replacement_rule -isnot [string] -or $Manifest.replacement_rule -cne $expectedReplacement) {
        throw "The v4 experiment manifest replacement rule contradicts its attempt maximum."
    }
    return [pscustomobject]@{
        maximum_attempts_per_slot = $maximum
        replacement_rule = $expectedReplacement
    }
}

function Get-M2ZipEntrySha256 {
    param([Parameter(Mandatory = $true)][System.IO.Compression.ZipArchiveEntry]$Entry)

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    $stream = $Entry.Open()
    try {
        return [System.BitConverter]::ToString($sha256.ComputeHash($stream)).Replace("-", "").ToLowerInvariant()
    }
    finally {
        $stream.Dispose()
        $sha256.Dispose()
    }
}

function Assert-M2PackagedArtifact {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$ExpectedSourceCommit,
        [Parameter(Mandatory = $true)][string]$ExpectedApkSha256,
        [Parameter(Mandatory = $true)][string]$ExpectedProfSha256,
        [Parameter(Mandatory = $true)][string]$ExpectedProfmSha256,
        [Parameter(Mandatory = $true)][string]$Role
    )

    $resolvedApk = (Resolve-Path -LiteralPath $Path).Path
    $requiredProfileEntries = @("assets/dexopt/baseline.prof", "assets/dexopt/baseline.profm")
    $embeddedSourceCommit = $null
    $packagedProfSha256 = $null
    $packagedProfmSha256 = $null
    $apkArchive = [System.IO.Compression.ZipFile]::OpenRead($resolvedApk)
    try {
        $apkEntryNames = @($apkArchive.Entries | ForEach-Object { $_.FullName })
        $profEntry = $apkArchive.GetEntry("assets/dexopt/baseline.prof")
        $profmEntry = $apkArchive.GetEntry("assets/dexopt/baseline.profm")
        if ($null -ne $profEntry) {
            $packagedProfSha256 = Get-M2ZipEntrySha256 -Entry $profEntry
        }
        if ($null -ne $profmEntry) {
            $packagedProfmSha256 = Get-M2ZipEntrySha256 -Entry $profmEntry
        }
        $versionControlEntry = $apkArchive.GetEntry("META-INF/version-control-info.textproto")
        if ($null -ne $versionControlEntry) {
            $versionControlReader = [System.IO.StreamReader]::new($versionControlEntry.Open())
            try {
                $versionControlText = $versionControlReader.ReadToEnd()
            }
            finally {
                $versionControlReader.Dispose()
            }
            $revisionMatch = [regex]::Match($versionControlText, '(?m)^\s*revision:\s*"([0-9a-f]{40})"\s*$')
            if ($revisionMatch.Success) {
                $embeddedSourceCommit = $revisionMatch.Groups[1].Value
            }
        }
    }
    finally {
        $apkArchive.Dispose()
    }
    if ($null -eq $embeddedSourceCommit -or $embeddedSourceCommit -ne $ExpectedSourceCommit) {
        throw "The release-like APK Git revision does not match the supplied source commit."
    }
    $missingProfileEntries = @($requiredProfileEntries | Where-Object { $_ -notin $apkEntryNames })
    if ($missingProfileEntries.Count -gt 0) {
        throw "speed-profile requires packaged APK entries: $($missingProfileEntries -join ', ')."
    }
    if ($packagedProfSha256 -cne $ExpectedProfSha256 -or $packagedProfmSha256 -cne $ExpectedProfmSha256) {
        throw "The packaged Baseline Profile assets do not match the fixed $Role identities."
    }
    $apkHash = Get-FileSha256 -Path $resolvedApk
    if ($apkHash -cne $ExpectedApkSha256) {
        throw "The APK does not match the fixed $Role SHA-256 identity."
    }
    return [pscustomobject]@{
        validation = "pass"
        candidate_role = $Role
        resolved_apk_path = $resolvedApk
        embedded_source_commit = $embeddedSourceCommit
        apk_byte_count = (Get-Item -LiteralPath $resolvedApk).Length
        apk_sha256 = $apkHash
        packaged_prof_sha256 = $packagedProfSha256
        packaged_profm_sha256 = $packagedProfmSha256
    }
}

$profileAcceptanceReaderPath = Join-Path $PSScriptRoot "../baseline-profile-evidence.ps1"
$profileAcceptanceReaderSha256 = Get-FileSha256 -Path $profileAcceptanceReaderPath
$baselineAcceptance = Read-BaselineProfileAcceptanceEvidence `
    -AcceptanceManifestPath $BaselineProfileAcceptanceManifestPath `
    -ExpectedAcceptanceManifestSha256 $BaselineProfileAcceptanceManifestSha256 `
    -ExpectedSourceRevision $BaselineProfileGenerationSourceCommit `
    -ExpectedAppApkSha256 $BaselineProfileGenerationAppApkSha256 `
    -ExpectedTestApkSha256 $BaselineProfileGenerationTestApkSha256 `
    -ExpectedPairManifestSha256 $BaselineProfilePairManifestSha256 `
    -ExpectedCanonicalSha256 $BaselineCanonicalProfileSha256
$candidateAcceptance = Read-BaselineProfileAcceptanceEvidence `
    -AcceptanceManifestPath $CandidateProfileAcceptanceManifestPath `
    -ExpectedAcceptanceManifestSha256 $CandidateProfileAcceptanceManifestSha256 `
    -ExpectedSourceRevision $CandidateProfileGenerationSourceCommit `
    -ExpectedAppApkSha256 $CandidateProfileGenerationAppApkSha256 `
    -ExpectedTestApkSha256 $CandidateProfileGenerationTestApkSha256 `
    -ExpectedPairManifestSha256 $CandidateProfilePairManifestSha256 `
    -ExpectedCanonicalSha256 $CandidateCanonicalProfileSha256
$resolvedBaselineAcceptanceManifest = $baselineAcceptance.AcceptanceManifestPath
$resolvedCandidateAcceptanceManifest = $candidateAcceptance.AcceptanceManifestPath

$expectedSampleCount = if ($RunKind -eq "diagnostic") { 10 } else { 50 }
if ($SampleCount -ne $expectedSampleCount) {
    throw "$RunKind collection requires exactly $expectedSampleCount operation samples."
}
$comparisonOrder = @(
    "decision:baseline",
    "decision:candidate",
    "diagnostic:baseline",
    "diagnostic:candidate"
)
$comparisonIdentity = "$RunKind`:$CandidateRole"
if ($comparisonOrder[$ComparisonSequenceIndex - 1] -ne $comparisonIdentity) {
    throw "Comparison sequence $ComparisonSequenceIndex requires '$($comparisonOrder[$ComparisonSequenceIndex - 1])'."
}
if ($BaselineSourceCommit -eq $CandidateSourceCommit -or $BaselineApkSha256 -eq $CandidateApkSha256) {
    throw "The prospective experiment requires distinct baseline and candidate source/APK identities."
}
$expectedSourceCommit = if ($CandidateRole -eq "baseline") { $BaselineSourceCommit } else { $CandidateSourceCommit }
$expectedApkSha256 = if ($CandidateRole -eq "baseline") { $BaselineApkSha256 } else { $CandidateApkSha256 }
$expectedPackagedProfSha256 =
    if ($CandidateRole -eq "baseline") { $BaselinePackagedProfSha256 } else { $CandidatePackagedProfSha256 }
$expectedPackagedProfmSha256 =
    if ($CandidateRole -eq "baseline") { $BaselinePackagedProfmSha256 } else { $CandidatePackagedProfmSha256 }
$expectedSurfaceBoundsByWorkload =
    if ($CandidateRole -eq "baseline") {
        [ordered]@{
            canvas16_tap = $BaselineCanvas16SurfaceBounds
            canvas256_repeated_diagonal = $BaselineCanvas256SurfaceBounds
            canvas256_repeated_diagonal_window_x2 = $BaselineCanvas256SurfaceBounds
        }
    } else {
        [ordered]@{
            canvas16_tap = $CandidateCanvas16SurfaceBounds
            canvas256_repeated_diagonal = $CandidateCanvas256SurfaceBounds
            canvas256_repeated_diagonal_window_x2 = $CandidateCanvas256SurfaceBounds
        }
    }
if ($SourceCommit -ne $expectedSourceCommit) {
    throw "The supplied source commit does not match the fixed $CandidateRole source identity."
}
if ($ValidateArtifactOnly -and $ValidateExperimentOnly) {
    throw "Artifact-only and experiment-only validation are mutually exclusive."
}
if ($InspectGeometryOnly -and ($ValidateArtifactOnly -or $ValidateExperimentOnly)) {
    throw "No-sample geometry inspection and the validation-only switches are mutually exclusive."
}
$resolvedInspection = $null
if ($InspectGeometryOnly) {
    if ([string]::IsNullOrWhiteSpace($InspectionDirectory)) {
        throw "No-sample geometry inspection requires its own -InspectionDirectory."
    }
    $resolvedInspection =
        if ([System.IO.Path]::IsPathRooted($InspectionDirectory)) {
            [System.IO.Path]::GetFullPath($InspectionDirectory)
        } else {
            [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $InspectionDirectory))
        }
    # The inspection precedes the experiment and must never reach into the reserved experiment tree.
    $experimentPrefix = $resolvedExperiment.TrimEnd([System.IO.Path]::DirectorySeparatorChar) +
        [System.IO.Path]::DirectorySeparatorChar
    if ($resolvedInspection -eq $resolvedExperiment -or
        $resolvedInspection.StartsWith($experimentPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "The inspection directory must be distinct from, and outside, the experiment directory."
    }
}
elseif ($PSBoundParameters.ContainsKey("InspectionDirectory")) {
    throw "-InspectionDirectory is only accepted with -InspectGeometryOnly."
}
if (-not $ValidateExperimentOnly) {
    $artifactIdentity = Assert-M2PackagedArtifact -Path $ApkPath `
        -ExpectedSourceCommit $expectedSourceCommit `
        -ExpectedApkSha256 $expectedApkSha256 `
        -ExpectedProfSha256 $expectedPackagedProfSha256 `
        -ExpectedProfmSha256 $expectedPackagedProfmSha256 `
        -Role $CandidateRole
    if ($ValidateArtifactOnly) {
        $artifactIdentity
        return
    }
}
$slotName = "slot-{0:D2}-{1}-{2}" -f $ComparisonSequenceIndex, $RunKind, $CandidateRole
$resolvedOutput = Join-Path $resolvedExperiment "$slotName-attempt-$Attempt"
if ($InspectGeometryOnly) {
    # No-sample inspection never reserves an acceptance slot; its device evidence is kept in its own
    # timestamped directory so that a rejected inspection can be repeated without deleting evidence.
    $resolvedOutput =
        Join-Path $resolvedInspection (
            "no-sample-inspection-$CandidateRole-raw-" + [datetime]::UtcNow.ToString("yyyyMMdd'T'HHmmss'Z'")
        )
}
$experimentManifestPath = Join-Path $resolvedExperiment "experiment.json"
$experimentManifest =
    [ordered]@{
        schema = $experimentSchema
        experiment_id = $ExperimentId
        baseline_production_commit = $BaselineProductionCommit
        candidate_production_commit = $CandidateProductionCommit
        baseline_production_tree_sha256 = $BaselineProductionTreeSha256
        candidate_production_tree_sha256 = $CandidateProductionTreeSha256
        baseline_measurement_build_commit = $BaselineSourceCommit
        candidate_measurement_build_commit = $CandidateSourceCommit
        baseline_apk_sha256 = $BaselineApkSha256
        candidate_apk_sha256 = $CandidateApkSha256
        variant = "release-like"
        compilation_mode = "speed-profile"
        profile_acceptance_reader_sha256 = $profileAcceptanceReaderSha256
        baseline_profile = [ordered]@{
            evidence_id = $baselineAcceptance.EvidenceId
            generation_source_commit = $BaselineProfileGenerationSourceCommit
            generation_app_apk_sha256 = $BaselineProfileGenerationAppApkSha256
            generation_test_apk_sha256 = $BaselineProfileGenerationTestApkSha256
            acceptance_manifest_path = $resolvedBaselineAcceptanceManifest
            acceptance_manifest_sha256 = $BaselineProfileAcceptanceManifestSha256
            pair_manifest_sha256 = $BaselineProfilePairManifestSha256
            invocation_manifest_sha256 = @($baselineAcceptance.InvocationManifestSha256)
            validation_output_sha256 = $baselineAcceptance.ValidationOutputSha256
            canonical_sha256 = $BaselineCanonicalProfileSha256
            packaged_prof_sha256 = $BaselinePackagedProfSha256
            packaged_profm_sha256 = $BaselinePackagedProfmSha256
        }
        candidate_profile = [ordered]@{
            evidence_id = $candidateAcceptance.EvidenceId
            generation_source_commit = $CandidateProfileGenerationSourceCommit
            generation_app_apk_sha256 = $CandidateProfileGenerationAppApkSha256
            generation_test_apk_sha256 = $CandidateProfileGenerationTestApkSha256
            acceptance_manifest_path = $resolvedCandidateAcceptanceManifest
            acceptance_manifest_sha256 = $CandidateProfileAcceptanceManifestSha256
            pair_manifest_sha256 = $CandidateProfilePairManifestSha256
            invocation_manifest_sha256 = @($candidateAcceptance.InvocationManifestSha256)
            validation_output_sha256 = $candidateAcceptance.ValidationOutputSha256
            canonical_sha256 = $CandidateCanonicalProfileSha256
            packaged_prof_sha256 = $CandidatePackagedProfSha256
            packaged_profm_sha256 = $CandidatePackagedProfmSha256
        }
        candidate_hypothesis = $CandidateHypothesis
        expected_affected_cost = $ExpectedAffectedCost
        correctness_risk = $CorrectnessRisk
        stop_conditions = $StopConditions
        comparison_order = $comparisonOrder
        workload_order = $workloadOrder
        workload_catalog = $workloadCatalog
        diagnostic_workload_order = $diagnosticWorkloadOrder
        diagnostic_window_family = [ordered]@{
            workload = $windowDiagnosticWorkload
            event_sequence_of = $workloadCatalog[1].workload
            window_scale = $windowDiagnosticScale
            window_anchor = "default"
        }
        geometry = [ordered]@{
            id = $geometryId
            baseline = [ordered]@{
                canvas16_tap = $BaselineCanvas16SurfaceBounds
                canvas256_repeated_diagonal = $BaselineCanvas256SurfaceBounds
            }
            candidate = [ordered]@{
                canvas16_tap = $CandidateCanvas16SurfaceBounds
                canvas256_repeated_diagonal = $CandidateCanvas256SurfaceBounds
            }
        }
        warmups_per_workload = $warmupCount
        diagnostic_samples_per_workload = 10
        decision_samples_per_workload = 50
        slot_budget = 4
        maximum_attempts_per_slot = $maximumAttemptsPerSlot
        replacement_rule = $replacementRule
    }
$existingManifest = $null
if (Test-Path -LiteralPath $experimentManifestPath -PathType Leaf) {
    $existingManifest = Get-Content -Raw -LiteralPath $experimentManifestPath | ConvertFrom-Json
    Assert-M2ExperimentAttemptPolicy -Manifest $existingManifest | Out-Null
}
$expectedManifestText = $experimentManifest | ConvertTo-Json -Depth 4
if ($InspectGeometryOnly) {
    # The inspection precedes the experiment; it never creates, writes or reserves anything under the
    # experiment directory, which must still be absent when the experiment is reserved.
    if (-not (Test-Path -LiteralPath $resolvedInspection)) {
        New-Item -ItemType Directory -Path $resolvedInspection -Force | Out-Null
    }
}
elseif ($ComparisonSequenceIndex -eq 1 -and $Attempt -eq 1) {
    if (-not (Test-Path -LiteralPath $resolvedExperiment)) {
        New-Item -ItemType Directory -Path $resolvedExperiment | Out-Null
        [System.IO.File]::WriteAllText(
            $experimentManifestPath,
            $expectedManifestText,
            [System.Text.UTF8Encoding]::new($false)
        )
    }
    elseif (-not (Test-Path -LiteralPath $experimentManifestPath -PathType Leaf)) {
        throw "An existing experiment directory must contain its fixed manifest."
    }
    else {
        if (($existingManifest | ConvertTo-Json -Depth 4) -cne $expectedManifestText) {
            throw "The invocation does not match the fixed experiment manifest."
        }
    }
}
elseif (-not (Test-Path -LiteralPath $experimentManifestPath -PathType Leaf)) {
    throw "The fixed experiment manifest is missing: $experimentManifestPath"
}
else {
    if (($existingManifest | ConvertTo-Json -Depth 4) -cne $expectedManifestText) {
        throw "The invocation does not match the fixed experiment manifest."
    }
}

function Get-RunState {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Directory
    )

    $path = Join-Path $Directory "run-state.json"
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        return $null
    }
    return Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
}

function Test-RunStateIdentity {
    param(
        [object]$State,
        [Parameter(Mandatory = $true)][int]$SequenceIndex,
        [Parameter(Mandatory = $true)][int]$ExpectedAttempt
    )

    $expectedCount = if ($SequenceIndex -le 2) { 50 } else { 10 }
    $expectedOrder = if ($SequenceIndex -le 2) { $workloadOrder } else { $diagnosticWorkloadOrder }
    $propertyNames = if ($null -eq $State) { @() } else { @($State.PSObject.Properties.Name) }
    $countsMatch =
        $null -ne $State -and
        "measured_workload_counts" -in $propertyNames -and
        @($expectedOrder | Where-Object { [int]$State.measured_workload_counts.$_ -ne $expectedCount }).Count -eq 0
    return (
        $null -ne $State -and
        $State.schema -eq $experimentSchema -and
        $State.experiment_id -eq $ExperimentId -and
        [int]$State.comparison_sequence_index -eq $SequenceIndex -and
        [int]$State.attempt -eq $ExpectedAttempt -and
        "workload_order" -in $propertyNames -and
        "measured_workload_counts" -in $propertyNames -and
        "measured_operation_count" -in $propertyNames -and
        (@($State.workload_order) -join "|") -ceq ($expectedOrder -join "|") -and
        $countsMatch -and
        [int]$State.measured_operation_count -eq ($expectedCount * $expectedOrder.Count)
    )
}

function Get-OperationTiming {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$PreviewRows,

        [Parameter(Mandatory = $true)]
        [object[]]$CommitRows
    )

    if ($PreviewRows.Count -lt 1 -or $CommitRows.Count -lt 1) {
        throw "Operation timing requires at least one preview and commit frame."
    }
    $previewInputStart = [long]($PreviewRows.handle_input_start_nanos | Measure-Object -Minimum).Minimum
    $commitInputStart = [long]($CommitRows.handle_input_start_nanos | Measure-Object -Minimum).Minimum
    $previewCompletion = [long]($PreviewRows.frame_completed_nanos | Measure-Object -Maximum).Maximum
    $committedResultCompletion = [long]($CommitRows.frame_completed_nanos | Measure-Object -Maximum).Maximum
    $timelineIds = @($PreviewRows.frame_timeline_vsync_id) + @($CommitRows.frame_timeline_vsync_id)
    if (
        @($timelineIds | Sort-Object -Unique).Count -ne $timelineIds.Count -or
        $previewInputStart -le 0 -or
        $previewCompletion -le $previewInputStart -or
        $commitInputStart -le $previewCompletion -or
        $committedResultCompletion -le $commitInputStart
    ) {
        throw "Operation frame timestamps do not preserve DOWN, UP, and committed-result order."
    }
    return [pscustomobject]@{
        preview_input_start_nanos = $previewInputStart
        commit_input_start_nanos = $commitInputStart
        committed_result_completion_nanos = $committedResultCompletion
        input_to_committed_result_ms = ($committedResultCompletion - $commitInputStart) / 1000000.0
        down_to_committed_result_ms = ($committedResultCompletion - $previewInputStart) / 1000000.0
    }
}

function Get-CompletedFamilyResult {
    param(
        [Parameter(Mandatory = $true)][object]$Spec,
        [Parameter(Mandatory = $true)][object[]]$Summaries,
        [Parameter(Mandatory = $true)][object[]]$Frames,
        [Parameter(Mandatory = $true)][string]$ExpectedProductionCommit
    )

    $workload = $Spec.workload
    $expectedSamples = @(1..$sampleCount)
    $actualSamples = @($Summaries | ForEach-Object { [int]$_.sample_index })
    if (
        $Summaries.Count -ne $sampleCount -or
        @(Compare-Object $expectedSamples $actualSamples).Count -ne 0 -or
        @($Summaries | Where-Object {
                $_.production_commit -cne $ExpectedProductionCommit -or
                [int]$_.motion_event_count -ne $Spec.motion_event_count -or
                [int]$_.preview_event_count -ne $Spec.preview_event_count -or
                [int]$_.commit_event_count -ne $Spec.commit_event_count -or
                [int]$_.raw_position_count -ne $Spec.raw_position_count -or
                [int]$_.effective_change_count -ne $Spec.effective_change_count -or
                $_.committed_ui_verified -ne $true
            }).Count -gt 0
    ) {
        throw "The $workload summary population drifted from its fixed identity or sequence."
    }
    foreach ($summary in $Summaries) {
        $operationFrames = @($Frames | Where-Object { [int]$_.sample_index -eq [int]$summary.sample_index })
        $previewRows = @($operationFrames | Where-Object { $_.phase -ceq "preview" })
        $commitRows = @($operationFrames | Where-Object { $_.phase -ceq "commit" })
        if (
            $previewRows.Count -ne [int]$summary.preview_frame_count -or
            $previewRows.Count -ne [int]$summary.preview_raw_frame_count -or
            $previewRows.Count -ne [int]$summary.preview_valid_frame_count -or
            $commitRows.Count -ne [int]$summary.commit_frame_count -or
            $commitRows.Count -ne [int]$summary.commit_raw_frame_count -or
            $commitRows.Count -ne [int]$summary.commit_valid_frame_count -or
            $operationFrames.Count -ne [int]$summary.raw_row_count -or
            @($previewRows | Where-Object { [int]$_.event_count -ne $Spec.preview_event_count }).Count -gt 0 -or
            @($commitRows | Where-Object { [int]$_.event_count -ne $Spec.commit_event_count }).Count -gt 0
        ) {
            throw "The $workload operation $($summary.sample_index) frame rows are missing or ambiguous."
        }
    }
    $overrunP95 = Get-NearestRank -Values @($Frames.frame_overrun_ms) -Percentile 0.95
    $overrunP99 = Get-NearestRank -Values @($Frames.frame_overrun_ms) -Percentile 0.99
    $inputP95 = Get-NearestRank -Values @($Summaries.input_to_committed_result_ms) -Percentile 0.95
    $maximumFrameOverrun = ($Frames.frame_overrun_ms | Measure-Object -Maximum).Maximum
    $maximumInputToCommitted = ($Summaries.input_to_committed_result_ms | Measure-Object -Maximum).Maximum
    return [pscustomobject]@{
        Workload = $workload
        FrameCount = $Frames.Count
        OverrunP95 = $overrunP95
        OverrunP99 = $overrunP99
        InputP95 = $inputP95
        MaximumFrameOverrun = $maximumFrameOverrun
        MaximumInputToCommitted = $maximumInputToCommitted
        Passed = $overrunP95 -le 0.0 -and $overrunP99 -le 16.67 -and $inputP95 -le 33.33
        GrossRegression = $maximumFrameOverrun -gt 33.34 -or $maximumInputToCommitted -gt 100.0
    }
}

if ($ComparisonSequenceIndex -gt 1 -and -not $InspectGeometryOnly) {
    $previousIdentity = $comparisonOrder[$ComparisonSequenceIndex - 2].Split(":")
    $previousSlot = "slot-{0:D2}-{1}-{2}" -f ($ComparisonSequenceIndex - 1), $previousIdentity[0], $previousIdentity[1]
    $previousAttempt = 1
    $previousState = Get-RunState -Directory (Join-Path $resolvedExperiment "$previousSlot-attempt-1")
    if (
        -not (Test-RunStateIdentity -State $previousState -SequenceIndex ($ComparisonSequenceIndex - 1) -ExpectedAttempt $previousAttempt) -or
        $previousState.status -ne "completed" -or
        $previousState.complete_run -ne $true -or
        $previousState.verdict -eq "gross-regression"
    ) {
        throw "Sequence slot $ComparisonSequenceIndex requires complete slot $($ComparisonSequenceIndex - 1) that did not stop the experiment."
    }
}
# -Variant is a declaration, not evidence; the evidence is the installed APK SHA-256 (Assert-M2InstalledApkIdentity).
if ($RunKind -eq "decision" -and ($Variant -ne "release-like" -or $CompilationMode -ne "speed-profile")) {
    throw "Decision collection requires release-like and speed-profile."
}
if ($ValidateExperimentOnly) {
    $modelTiming = Get-OperationTiming -PreviewRows @(
        [pscustomobject]@{
            frame_timeline_vsync_id = 1L
            handle_input_start_nanos = 1000000000L
            frame_completed_nanos = 1010000000L
        }
    ) -CommitRows @(
        [pscustomobject]@{
            frame_timeline_vsync_id = 2L
            handle_input_start_nanos = 1120000000L
            frame_completed_nanos = 1140000000L
        },
        [pscustomobject]@{
            frame_timeline_vsync_id = 3L
            handle_input_start_nanos = 1121000000L
            frame_completed_nanos = 1145000000L
        }
    )
    [pscustomobject]@{
        experiment_id = $ExperimentId
        slot = $slotName
        attempt = $Attempt
        validation = "pass"
        model_input_to_committed_result_ms = $modelTiming.input_to_committed_result_ms
        model_down_to_committed_result_ms = $modelTiming.down_to_committed_result_ms
    }
    return
}

$trackedStatus = @(& git status --porcelain --untracked-files=no 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "Unable to verify the repository worktree before frame collection."
}
if ($trackedStatus.Count -gt 0) {
    throw "Frame collection requires a clean tracked worktree."
}
$repositoryHead = (& git rev-parse HEAD 2>&1 | Select-Object -First 1).Trim()
if ($LASTEXITCODE -ne 0 -or $repositoryHead -ne $SourceCommit) {
    throw "The supplied source commit does not match the repository HEAD."
}

if (Test-Path -LiteralPath $resolvedOutput) {
    throw "Frame output already exists: $resolvedOutput"
}
if ($InspectGeometryOnly) {
    $inspectionRecordPath = Join-Path $resolvedInspection "no-sample-inspection-$CandidateRole.json"
    if (Test-Path -LiteralPath $inspectionRecordPath) {
        throw "A no-sample inspection record already exists; move it aside before inspecting again: $inspectionRecordPath"
    }
}

New-Item -ItemType Directory -Path $resolvedOutput | Out-Null
New-Item -ItemType Directory -Path (Join-Path $resolvedOutput "raw") | Out-Null
$runStatePath = Join-Path $resolvedOutput "run-state.json"
$script:measuredWorkloadCounts = [ordered]@{}
foreach ($slotWorkload in $slotWorkloadOrder) {
    $script:measuredWorkloadCounts[$slotWorkload] = 0
}
function Get-MeasuredOperationCount {
    return [int](($script:measuredWorkloadCounts.Values | Measure-Object -Sum).Sum)
}
function Write-RunState {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Status,

        [Parameter(Mandatory = $true)]
        [string]$Verdict,

        [bool]$CompleteRun = $false
    )

    $state =
        [ordered]@{
            schema = $experimentSchema
            experiment_id = $ExperimentId
            comparison_sequence_index = $ComparisonSequenceIndex
            attempt = $Attempt
            status = $Status
            verdict = $Verdict
            complete_run = $CompleteRun
            workload_order = $slotWorkloadOrder
            measured_workload_counts = $script:measuredWorkloadCounts
            measured_operation_count = Get-MeasuredOperationCount
        }
    [System.IO.File]::WriteAllText(
        $runStatePath,
        ($state | ConvertTo-Json),
        [System.Text.UTF8Encoding]::new($false)
    )
}
if (-not $InspectGeometryOnly) {
    Write-RunState -Status "running" -Verdict "unavailable"
}

function Invoke-TargetAdb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$AdbArguments
    )

    $commandOutput = @(& adb -s $DeviceSerial @AdbArguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed ($LASTEXITCODE): adb -s <physical-device> $($AdbArguments -join ' ')`n$($commandOutput -join "`n")"
    }
    return $commandOutput | ForEach-Object { $_.ToString() }
}

function Assert-M2InstalledApkIdentity {
    <#
        Protocol Lane 3: the evidence of the installed variant is the installed package itself, never
        the host file or the declared -Variant string. Reads the package's base APK from the device
        (`pm path` + `sha256sum`) and requires its SHA-256 to equal the role's app_release_like identity.
        A mismatch throws before any warmup, so the slot is INVALID before samples.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$ExpectedApkSha256,
        [Parameter(Mandatory = $true)][string]$Role
    )
    if ($ExpectedApkSha256 -cnotmatch '^[0-9a-f]{64}$') {
        throw "INVALID: the $Role release-like APK has no fixed SHA-256 identity."
    }
    $pathLines = @(Invoke-TargetAdb -AdbArguments @("shell", "pm", "path", $packageName) |
            ForEach-Object { $_.Trim() } | Where-Object { $_ -cmatch '^package:/' })
    $basePaths = @($pathLines | ForEach-Object { $_.Substring("package:".Length) } |
            Where-Object { $_.EndsWith("/base.apk") })
    if ($basePaths.Count -ne 1) {
        throw "INVALID: $packageName does not expose exactly one installed base APK."
    }
    $digests = @(Invoke-TargetAdb -AdbArguments @("shell", "sha256sum", $basePaths[0]) | ForEach-Object {
            $match = [regex]::Match($_, '^([0-9a-f]{64})\s')
            if ($match.Success) { $match.Groups[1].Value }
        })
    if ($digests.Count -ne 1) {
        throw "INVALID: the installed base APK of $packageName did not report exactly one digest."
    }
    if ($digests[0] -cne $ExpectedApkSha256) {
        throw "INVALID: the installed $packageName base APK is not the $Role release-like artifact."
    }
    return [pscustomobject]@{
        expected_apk_sha256 = $ExpectedApkSha256
        installed_apk_path = $basePaths[0]
        installed_apk_sha256 = $digests[0]
    }
}

function Get-TargetProperty {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $value = (Invoke-TargetAdb -AdbArguments @("shell", "getprop", $Name) | Select-Object -First 1).Trim()
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Physical device property $Name is unavailable."
    }
    return $value
}

function Get-PhysicalDeviceIdentity {
    $emulatorFlag = (Invoke-TargetAdb -AdbArguments @("shell", "getprop", "ro.kernel.qemu") | Select-Object -First 1).Trim()
    if ($emulatorFlag -eq "1") {
        throw "The fixed frame protocol requires a physical device."
    }

    $manufacturer = Get-TargetProperty -Name "ro.product.manufacturer"
    $model = Get-TargetProperty -Name "ro.product.model"
    $product = Get-TargetProperty -Name "ro.product.name"
    $device = Get-TargetProperty -Name "ro.product.device"
    $apiLevelText = Get-TargetProperty -Name "ro.build.version.sdk"
    $apiLevel = 0
    if (-not [int]::TryParse($apiLevelText, [ref]$apiLevel)) {
        throw "Physical device API level is invalid: $apiLevelText."
    }
    $identityMatches =
        [string]::Equals($manufacturer, $expectedManufacturer, [System.StringComparison]::OrdinalIgnoreCase) -and
        [string]::Equals($model, $expectedModel, [System.StringComparison]::Ordinal) -and
        [string]::Equals($product, $expectedProduct, [System.StringComparison]::Ordinal) -and
        [string]::Equals($device, $expectedDevice, [System.StringComparison]::Ordinal) -and
        $apiLevel -eq $expectedApiLevel
    if (-not $identityMatches) {
        throw "The connected hardware does not match physical profile $physicalProfileId."
    }

    return [pscustomobject]@{
        manufacturer = $manufacturer
        model = $model
        product = $product
        device = $device
        api_level = $apiLevel
        build_fingerprint = Get-TargetProperty -Name "ro.build.fingerprint"
        security_patch = Get-TargetProperty -Name "ro.build.version.security_patch"
    }
}

function Get-WindowRotationState {
    $numericText =
        (Invoke-TargetAdb -AdbArguments @("shell", "settings", "get", "system", "user_rotation") |
            Select-Object -First 1).Trim()
    $windowText = (Invoke-TargetAdb -AdbArguments @("shell", "dumpsys", "window")) -join "`n"
    ConvertFrom-NeneWindowRotationState -NumericText $numericText -WindowText $windowText
}

function Assert-PinnedLandscapeRotation {
    param([Parameter(Mandatory = $true)][object]$State)

    if (
        $State.mode -ne "locked" -or
        $State.numeric_user_rotation -ne $requiredRotation -or
        $State.reported_user_rotation -ne $requiredRotation -or
        $State.current_rotation -ne $requiredRotation -or
        $State.logical_width -ne $requiredLogicalWidth -or
        $State.logical_height -ne $requiredLogicalHeight
    ) {
        throw "WindowManager did not retain the required locked landscape rotation."
    }
}

function Get-PhysicalCheckpoint {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $wmText = (Invoke-TargetAdb -AdbArguments @("shell", "wm", "size")) -join "`n"
    $physicalSizeMatch = [regex]::Match($wmText, "(?m)^Physical size:\s*(\d+)x(\d+)\s*$")
    if (-not $physicalSizeMatch.Success) {
        throw "Physical display size is unavailable at checkpoint $Name."
    }
    $physicalWidth = [int]$physicalSizeMatch.Groups[1].Value
    $physicalHeight = [int]$physicalSizeMatch.Groups[2].Value

    $displayText = (Invoke-TargetAdb -AdbArguments @("shell", "dumpsys", "display")) -join "`n"
    $displayMatch =
        [regex]::Match(
            $displayText,
            "DisplayDeviceInfo\{.*?,\s*(\d+)\s+x\s+(\d+),\s+modeId\s+(\d+),.*?supportedModes\s+\[(.*?)\]",
            [System.Text.RegularExpressions.RegexOptions]::Singleline
        )
    if (-not $displayMatch.Success) {
        throw "Active display mode is unavailable at checkpoint $Name."
    }
    $activeWidth = [int]$displayMatch.Groups[1].Value
    $activeHeight = [int]$displayMatch.Groups[2].Value
    $displayModeId = [int]$displayMatch.Groups[3].Value
    $activeModeMatch =
        [regex]::Match(
            $displayMatch.Groups[4].Value,
            "\{id=$displayModeId,\s*width=$activeWidth,\s*height=$activeHeight,\s*fps=([0-9.]+)"
        )
    if (-not $activeModeMatch.Success) {
        throw "Active display refresh rate is unavailable at checkpoint $Name."
    }
    $refreshRateHertz =
        [double]::Parse($activeModeMatch.Groups[1].Value, [System.Globalization.CultureInfo]::InvariantCulture)

    $rotationState = Get-WindowRotationState
    Assert-PinnedLandscapeRotation -State $rotationState

    $thermalText = (Invoke-TargetAdb -AdbArguments @("shell", "dumpsys", "thermalservice")) -join "`n"
    $thermalMatch = [regex]::Match($thermalText, "Thermal Status:\s*(\d+)")
    if (-not $thermalMatch.Success) {
        throw "Thermal status is unavailable at checkpoint $Name."
    }
    $thermalStatus = [int]$thermalMatch.Groups[1].Value

    $lowPowerText = (Invoke-TargetAdb -AdbArguments @("shell", "settings", "get", "global", "low_power") | Select-Object -First 1).Trim()
    if ($lowPowerText -notin @("0", "1")) {
        throw "Power-save state is unavailable at checkpoint $Name."
    }
    $powerSaveMode = $lowPowerText -eq "1"

    $powerText = (Invoke-TargetAdb -AdbArguments @("shell", "dumpsys", "power")) -join "`n"
    $interactive = $powerText -match "mWakefulness=Awake"

    $batteryText = (Invoke-TargetAdb -AdbArguments @("shell", "dumpsys", "battery")) -join "`n"
    $usbPoweredMatch = [regex]::Match($batteryText, "(?m)^\s*USB powered:\s*(true|false)\s*$")
    $batteryLevelMatch = [regex]::Match($batteryText, "(?m)^\s*level:\s*(\d+)\s*$")
    if (-not $usbPoweredMatch.Success -or -not $batteryLevelMatch.Success) {
        throw "Battery state is unavailable at checkpoint $Name."
    }
    $usbPowered = $usbPoweredMatch.Groups[1].Value -eq "true"
    $batteryLevel = [int]$batteryLevelMatch.Groups[1].Value

    if (
        $physicalWidth -ne $expectedDisplayWidth -or
        $physicalHeight -ne $expectedDisplayHeight -or
        $activeWidth -ne $expectedDisplayWidth -or
        $activeHeight -ne $expectedDisplayHeight -or
        $displayModeId -ne $expectedDisplayModeId -or
        [Math]::Abs($refreshRateHertz - $expectedRefreshRateHertz) -gt $refreshRateToleranceHertz
    ) {
        throw "Display state does not match physical profile $physicalProfileId at checkpoint $Name."
    }
    if ($thermalStatus -gt $maximumThermalStatus) {
        throw "Thermal status $thermalStatus exceeds the physical evidence limit at checkpoint $Name."
    }
    if ($powerSaveMode -or -not $interactive -or -not $usbPowered -or $batteryLevel -lt 0 -or $batteryLevel -gt 100) {
        throw "Power state does not match physical profile $physicalProfileId at checkpoint $Name."
    }

    return [pscustomobject]@{
        checkpoint = $Name
        host_timestamp = (Get-Date).ToString("o")
        physical_width = $physicalWidth
        physical_height = $physicalHeight
        active_width = $activeWidth
        active_height = $activeHeight
        display_mode_id = $displayModeId
        refresh_rate_hertz = $refreshRateHertz
        rotation_mode = $rotationState.mode
        numeric_user_rotation = $rotationState.numeric_user_rotation
        reported_user_rotation = $rotationState.reported_user_rotation
        current_rotation = $rotationState.current_rotation
        logical_width = $rotationState.logical_width
        logical_height = $rotationState.logical_height
        thermal_status = $thermalStatus
        power_save_mode = $powerSaveMode
        interactive = $interactive
        usb_powered = $usbPowered
        battery_level_percent = $batteryLevel
    }
}

function Get-ResourceNode {
    param(
        [Parameter(Mandatory = $true)][xml]$Ui,
        [Parameter(Mandatory = $true)][string]$Identity
    )

    $matches = @(
        $Ui.SelectNodes("//*[@resource-id]") |
            Where-Object {
                $resourceId = $_.GetAttribute("resource-id")
                $separatorIndex = $resourceId.LastIndexOf(":id/", [System.StringComparison]::Ordinal)
                $normalized = if ($separatorIndex -ge 0) { $resourceId.Substring($separatorIndex + 4) } else { $resourceId }
                $normalized -ceq $Identity
            }
    )
    if ($matches.Count -ne 1) {
        throw "The editor UI must expose exactly one '$Identity' resource identity."
    }
    return $matches[0]
}

function Get-ResourceNodeAttribute {
    param(
        [Parameter(Mandatory = $true)][System.Xml.XmlElement]$Node,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $current = $Node
    while ($null -ne $current) {
        $value = $current.GetAttribute($Name)
        if (-not [string]::IsNullOrEmpty($value)) {
            return $value
        }
        $current = if ($current.ParentNode -is [System.Xml.XmlElement]) { $current.ParentNode } else { $null }
    }
    throw "The '$Name' state is unavailable for resource '$($Node.GetAttribute('resource-id'))'."
}

function Get-ResourceControlNode {
    param([Parameter(Mandatory = $true)][System.Xml.XmlElement]$Node)

    $current = $Node
    while ($null -ne $current) {
        if ($current.GetAttribute("bounds") -match "^\[\d+,\d+\]\[\d+,\d+\]$") {
            return $current
        }
        $current = if ($current.ParentNode -is [System.Xml.XmlElement]) { $current.ParentNode } else { $null }
    }
    throw "Interactive bounds are unavailable for resource '$($Node.GetAttribute('resource-id'))'."
}

function Assert-LandscapeRootUi {
    param([Parameter(Mandatory = $true)][xml]$Ui)

    $rootNode = $Ui.DocumentElement.SelectSingleNode("./node")
    if (
        $Ui.DocumentElement.GetAttribute("rotation") -ne "$requiredRotation" -or
        $null -eq $rootNode -or
        $rootNode.GetAttribute("bounds") -ne $requiredRootBounds
    ) {
        throw "The editor root geometry or rotation drifted from the pinned physical contract."
    }
}

function Get-WorkloadSpec {
    param([Parameter(Mandatory = $true)][string]$Workload)

    $matches = @($slotWorkloadCatalog | Where-Object { $_.workload -ceq $Workload })
    if ($matches.Count -ne 1) {
        throw "Unknown or ambiguous frame workload '$Workload'."
    }
    return $matches[0]
}

function Assert-LandscapeEditorUi {
    param(
        [Parameter(Mandatory = $true)][xml]$Ui,
        [Parameter(Mandatory = $true)][string]$Workload
    )

    $spec = Get-WorkloadSpec -Workload $Workload
    Assert-LandscapeRootUi -Ui $Ui
    $canvasIdentity = "editor_canvas_$($spec.canvas_width)_$($spec.canvas_height)"
    $canvasNode = Get-ResourceNode -Ui $Ui -Identity $canvasIdentity
    $expectedBounds = $expectedSurfaceBoundsByWorkload[$Workload]
    if (
        $canvasNode.GetAttribute("bounds") -cne $expectedBounds
    ) {
        throw "The editor UI or pinned $Workload surface geometry drifted from preflight."
    }
    return Get-InitialFitGeometry -SurfaceBounds (Get-Bounds -Node $canvasNode) -Spec $spec
}

function Get-InitialFitGeometry {
    param(
        [Parameter(Mandatory = $true)][object]$SurfaceBounds,
        [Parameter(Mandatory = $true)][object]$Spec
    )

    $surfaceWidth = $SurfaceBounds.Right - $SurfaceBounds.Left
    $surfaceHeight = $SurfaceBounds.Bottom - $SurfaceBounds.Top
    if ($surfaceWidth -le 0 -or $surfaceHeight -le 0) {
        throw "The pinned canvas surface bounds must have positive integer extents."
    }
    $fit = [Math]::Min($surfaceWidth / [double]$Spec.canvas_width, $surfaceHeight / [double]$Spec.canvas_height)
    $projectedWidth = $fit * $Spec.canvas_width
    $projectedHeight = $fit * $Spec.canvas_height
    $originX = $SurfaceBounds.Left + (($surfaceWidth - $projectedWidth) / 2.0)
    $originY = $SurfaceBounds.Top + (($surfaceHeight - $projectedHeight) / 2.0)
    $firstX = $originX + ($fit / 2.0)
    $firstY = $originY + ($fit / 2.0)
    $lastX = $originX + (($Spec.canvas_width - 0.5) * $fit)
    $lastY = $originY + (($Spec.canvas_height - 0.5) * $fit)
    $values = @($fit, $originX, $originY, $firstX, $firstY, $lastX, $lastY)
    if (
        @($values | Where-Object { [double]::IsNaN($_) -or [double]::IsInfinity($_) }).Count -gt 0 -or
        $fit -le 0.0 -or
        $SurfaceBounds.Left -lt 0 -or
        $SurfaceBounds.Top -lt 0 -or
        $SurfaceBounds.Right -gt $requiredLogicalWidth -or
        $SurfaceBounds.Bottom -gt $requiredLogicalHeight -or
        $firstX -le $SurfaceBounds.Left -or
        $firstY -le $SurfaceBounds.Top -or
        $lastX -ge $SurfaceBounds.Right -or
        $lastY -ge $SurfaceBounds.Bottom
    ) {
        throw "The $geometryId projection did not produce target-cell centers strictly inside the pinned surface."
    }
    return [pscustomobject]@{
        Id = $geometryId
        SurfaceBounds = "[$($SurfaceBounds.Left),$($SurfaceBounds.Top)][$($SurfaceBounds.Right),$($SurfaceBounds.Bottom)]"
        Fit = $fit
        OriginX = $originX
        OriginY = $originY
        FirstX = $firstX
        FirstY = $firstY
        LastX = $lastX
        LastY = $lastY
    }
}

function Format-InputCoordinate {
    param([Parameter(Mandatory = $true)][double]$Value)

    return $Value.ToString("0.######", [System.Globalization.CultureInfo]::InvariantCulture)
}

function Get-RequiredMatchValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Text,

        [Parameter(Mandatory = $true)]
        [string]$Pattern,

        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $match = [regex]::Match($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if (-not $match.Success) {
        throw "Missing $Name in gfxinfo output."
    }
    return [long]$match.Groups[1].Value
}

function Get-Bounds {
    param(
        [Parameter(Mandatory = $true)]
        [System.Xml.XmlElement]$Node
    )

    $match = [regex]::Match($Node.GetAttribute("bounds"), "^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$")
    if (-not $match.Success) {
        throw "UI node has invalid bounds."
    }
    return [pscustomobject]@{
        Left = [int]$match.Groups[1].Value
        Top = [int]$match.Groups[2].Value
        Right = [int]$match.Groups[3].Value
        Bottom = [int]$match.Groups[4].Value
    }
}

function Get-NearestRank {
    param(
        [Parameter(Mandatory = $true)]
        [double[]]$Values,

        [Parameter(Mandatory = $true)]
        [double]$Percentile
    )

    if ($Values.Count -eq 0) {
        throw "Cannot calculate a percentile from an empty collection."
    }
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Ceiling($sorted.Count * $Percentile) - 1
    return $sorted[$index]
}

function Get-FrameRows {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string[]]$GfxInfo,

        [Parameter(Mandatory = $true)]
        [int]$SampleIndex,

        [Parameter(Mandatory = $true)]
        [string]$Workload,

        [Parameter(Mandatory = $true)]
        [int]$OperationOrdinal,

        [Parameter(Mandatory = $true)]
        [int]$EventCount,

        [Parameter(Mandatory = $true)]
        [ValidateSet("preview", "commit")]
        [string]$Phase
    )

    $headerIndexes = @(
        0..($GfxInfo.Count - 1) |
            Where-Object { $GfxInfo[$_] -like "Flags,FrameTimelineVsyncId,*" }
    )
    if ($headerIndexes.Count -ne 1) {
        throw "Sample $SampleIndex must expose exactly one PROFILEDATA header."
    }
    $headerIndex = $headerIndexes[0]

    $rows = [System.Collections.Generic.List[object]]::new()
    for ($lineIndex = $headerIndex + 1; $lineIndex -lt $GfxInfo.Count; $lineIndex += 1) {
        $line = $GfxInfo[$lineIndex]
        if ($line -eq "---PROFILEDATA---") {
            break
        }
        if ($line -notmatch "^\d+,") {
            continue
        }
        $frame = @($GfxInfo[$headerIndex], $line) | ConvertFrom-Csv
        $completed = [long]$frame.FrameCompleted
        $started = [long]$frame.FrameStartTime
        $intended = [long]$frame.IntendedVsync
        $deadline = [long]$frame.FrameDeadline
        $inputStarted = [long]$frame.HandleInputStart
        $frameTimelineVsyncId = [long]$frame.FrameTimelineVsyncId
        if (
            $frameTimelineVsyncId -le 0 -or
            $completed -le 0 -or
            $started -le 0 -or
            $intended -le 0 -or
            $deadline -le 0 -or
            $inputStarted -le 0
        ) {
            throw "Sample $SampleIndex contains unavailable required frame fields."
        }
        $rows.Add(
            [pscustomobject]@{
                variant = $Variant
                source_commit = $SourceCommit
                production_commit = if ($CandidateRole -eq "baseline") { $BaselineProductionCommit } else { $CandidateProductionCommit }
                workload = $Workload
                operation = "$Workload#$SampleIndex"
                operation_ordinal = $OperationOrdinal
                sample_index = $SampleIndex
                phase = $Phase
                event_count = $EventCount
                row_index = $rows.Count + 1
                flags = [int]$frame.Flags
                frame_timeline_vsync_id = $frameTimelineVsyncId
                intended_vsync_nanos = $intended
                frame_start_nanos = $started
                handle_input_start_nanos = $inputStarted
                draw_start_nanos = [long]$frame.DrawStart
                frame_deadline_nanos = $deadline
                frame_completed_nanos = $completed
                display_present_time_nanos = [long]$frame.DisplayPresentTime
                frame_duration_cpu_ms = ($completed - $started) / 1000000.0
                app_frame_total_ms = ($completed - $intended) / 1000000.0
                frame_overrun_ms = ($completed - $deadline) / 1000000.0
                input_start_to_completion_ms = ($completed - $inputStarted) / 1000000.0
            }
        )
    }
    return $rows
}

function Get-OperationPhaseCapture {
    param(
        [Parameter(Mandatory = $true)]
        [int]$SampleIndex,

        [Parameter(Mandatory = $true)]
        [string]$Workload,

        [Parameter(Mandatory = $true)]
        [int]$OperationOrdinal,

        [Parameter(Mandatory = $true)]
        [ValidateSet("preview", "commit")]
        [string]$Phase,

        [Parameter(Mandatory = $true)]
        [int]$EventCount,

        [string]$ArtifactPhase = $Phase
    )

    $gfxInfo = @(Invoke-TargetAdb -AdbArguments @("shell", "dumpsys", "gfxinfo", $packageName, "framestats"))
    $gfxText = $gfxInfo -join "`n"
    [System.IO.File]::WriteAllLines(
        (Join-Path $resolvedOutput ("raw/{0}-sample-{1:D2}-{2}.txt" -f $Workload, $SampleIndex, $ArtifactPhase)),
        $gfxInfo
    )

    $rows = @(
        Get-FrameRows `
            -GfxInfo $gfxInfo `
            -SampleIndex $SampleIndex `
            -Workload $Workload `
            -OperationOrdinal $OperationOrdinal `
            -EventCount $EventCount `
            -Phase $Phase
    )
    $validRows = @($rows | Where-Object { $_.flags -eq 0 })
    $totalFramesRendered =
        [int](Get-RequiredMatchValue -Text $gfxText -Pattern "^Total frames rendered:\s+(\d+)" -Name "total frame count")
    if ($totalFramesRendered -lt 1) {
        throw "Sample $SampleIndex $Phase phase must render at least one frame."
    }
    if ($rows.Count -ne $totalFramesRendered) {
        throw "Sample $SampleIndex $Phase raw row count must equal Total frames rendered."
    }
    if ($validRows.Count -ne $rows.Count) {
        throw "Sample $SampleIndex $Phase requires every raw PROFILEDATA row to be valid."
    }

    return [pscustomobject]@{
        Rows = $rows
        TotalFramesRendered = $totalFramesRendered
        JankyFrames =
            [int](Get-RequiredMatchValue -Text $gfxText -Pattern "^Janky frames:\s+(\d+)" -Name "janky frame count")
        DeadlineMissedFrames =
            [int](Get-RequiredMatchValue -Text $gfxText -Pattern "^Number Frame deadline missed:\s+(\d+)\s*$" -Name "deadline miss count")
    }
}

function Assert-UnchangedPhaseCapture {
    param(
        [Parameter(Mandatory = $true)][object]$BeforeUiVerification,
        [Parameter(Mandatory = $true)][object]$AfterUiVerification,
        [Parameter(Mandatory = $true)][int]$SampleIndex,
        [Parameter(Mandatory = $true)][string]$Workload
    )

    $beforeRows = @($BeforeUiVerification.Rows | ConvertTo-Csv -NoTypeInformation)
    $afterRows = @($AfterUiVerification.Rows | ConvertTo-Csv -NoTypeInformation)
    if (
        $BeforeUiVerification.TotalFramesRendered -ne $AfterUiVerification.TotalFramesRendered -or
        $BeforeUiVerification.JankyFrames -ne $AfterUiVerification.JankyFrames -or
        $BeforeUiVerification.DeadlineMissedFrames -ne $AfterUiVerification.DeadlineMissedFrames -or
        @(Compare-Object $beforeRows $afterRows -SyncWindow 0).Count -ne 0
    ) {
        throw "Sample $SampleIndex for $Workload rendered an unassociated commit frame during UI verification."
    }
}

function Set-LatestUiFailureSnapshot {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("committed-result", "undo-reset")]
        [string]$Kind,

        [Parameter(Mandatory = $true)]
        [ValidateSet("sample-commit", "warmup-commit", "sample-reset", "warmup-reset")]
        [string]$Phase,

        [Parameter(Mandatory = $true)]
        [string]$Workload,

        [AllowNull()]
        [Nullable[int]]$SampleIndex,

        [AllowNull()]
        [Nullable[int]]$WarmupIndex,

        [AllowNull()]
        [Nullable[int]]$UndoAttempt,

        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$RawXml
    )

    $snapshot = [pscustomobject]@{
        Kind = $Kind
        Phase = $Phase
        Workload = $Workload
        SampleIndex = $SampleIndex
        WarmupIndex = $WarmupIndex
        UndoAttempt = $UndoAttempt
        RawXml = $RawXml
    }
    if ($Kind -eq "committed-result") {
        $script:latestCommittedResultSnapshot = $snapshot
    }
    else {
        $script:latestUndoResetSnapshot = $snapshot
    }
}

function Write-Utf8CreateNew {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text
    )

    $stream = [System.IO.File]::Open(
        $Path,
        [System.IO.FileMode]::CreateNew,
        [System.IO.FileAccess]::Write,
        [System.IO.FileShare]::None
    )
    try {
        $writer = [System.IO.StreamWriter]::new($stream, [System.Text.UTF8Encoding]::new($false))
        try {
            $writer.Write($Text)
        }
        finally {
            $writer.Dispose()
        }
    }
    finally {
        $stream.Dispose()
    }
}

function Save-LatestUiFailureSnapshots {
    param(
        [Parameter(Mandatory = $true)][string]$RawDirectory,
        [Parameter(Mandatory = $true)][System.Management.Automation.ErrorRecord]$SourceError
    )

    foreach ($snapshot in @($script:latestCommittedResultSnapshot, $script:latestUndoResetSnapshot)) {
        if ($null -eq $snapshot) {
            continue
        }
        try {
            $stem = "failure-latest-$($snapshot.Kind)"
            Write-Utf8CreateNew -Path (Join-Path $RawDirectory "$stem.xml") -Text $snapshot.RawXml
            $metadata = @(
                "kind=$($snapshot.Kind)",
                "phase=$($snapshot.Phase)",
                "workload=$($snapshot.Workload)",
                "sample_index=$(if ($null -eq $snapshot.SampleIndex) { '' } else { $snapshot.SampleIndex })",
                "warmup_index=$(if ($null -eq $snapshot.WarmupIndex) { '' } else { $snapshot.WarmupIndex })",
                "undo_attempt=$(if ($null -eq $snapshot.UndoAttempt) { '' } else { $snapshot.UndoAttempt })",
                "source_error=$($SourceError.Exception.Message -replace '[\r\n]+', ' ')"
            ) -join "`n"
            Write-Utf8CreateNew -Path (Join-Path $RawDirectory "$stem.metadata.txt") -Text ($metadata + "`n")
        }
        catch {
            # Failure evidence is best effort and must not replace the source failure.
        }
    }
}

function Invoke-UndoToCleanCheckpoint {
    param(
        [Parameter(Mandatory = $true)]
        [int]$UndoX,

        [Parameter(Mandatory = $true)]
        [int]$UndoY,

        [Parameter(Mandatory = $true)]
        [ValidateSet("sample-reset", "warmup-reset")]
        [string]$Phase,

        [Parameter(Mandatory = $true)]
        [int]$JourneyIndex,
        [Parameter(Mandatory = $true)]
        [string]$Workload
    )

    $checkpointRemote = "$remotePrefix-checkpoint.xml"
    foreach ($attempt in 1..$undoAttemptLimit) {
        Invoke-TargetAdb -AdbArguments @("shell", "cmd", "input", "tap", "$UndoX", "$UndoY") | Out-Null
        Start-Sleep -Milliseconds $undoWaitMilliseconds
        Invoke-TargetAdb -AdbArguments @("shell", "uiautomator", "dump", $checkpointRemote) | Out-Null
        $checkpointText =
            (Invoke-TargetAdb -AdbArguments @("shell", "cat", $checkpointRemote)) -join "`n"
        Set-LatestUiFailureSnapshot `
            -Kind "undo-reset" `
            -Phase $Phase `
            -Workload $Workload `
            -SampleIndex $(if ($Phase -eq "sample-reset") { $JourneyIndex } else { $null }) `
            -WarmupIndex $(if ($Phase -eq "warmup-reset") { $JourneyIndex } else { $null }) `
            -UndoAttempt $attempt `
            -RawXml $checkpointText
        [xml]$checkpointUi = $checkpointText
        Assert-LandscapeEditorUi -Ui $checkpointUi -Workload $Workload | Out-Null
        $cleanNode = Get-ResourceNode -Ui $checkpointUi -Identity "editor_clean_document"
        $undoNode = Get-ResourceNode -Ui $checkpointUi -Identity "editor_undo"
        if (
            $null -ne $cleanNode -and
            (Get-ResourceNodeAttribute -Node $undoNode -Name "enabled") -eq "false"
        ) {
            return
        }
    }
    throw "Undo did not restore the clean checkpoint after $undoAttemptLimit attempts."
}

function Assert-CommittedResult {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("sample", "warmup")]
        [string]$JourneyKind,

        [Parameter(Mandatory = $true)]
        [int]$JourneyIndex,

        [Parameter(Mandatory = $true)]
        [string]$Workload
    )

    $checkpointRemote = "$remotePrefix-checkpoint.xml"
    Invoke-TargetAdb -AdbArguments @("shell", "uiautomator", "dump", $checkpointRemote) | Out-Null
    $checkpointText =
        (Invoke-TargetAdb -AdbArguments @("shell", "cat", $checkpointRemote)) -join "`n"
    Set-LatestUiFailureSnapshot `
        -Kind "committed-result" `
        -Phase "$JourneyKind-commit" `
        -Workload $Workload `
        -SampleIndex $(if ($JourneyKind -eq "sample") { $JourneyIndex } else { $null }) `
        -WarmupIndex $(if ($JourneyKind -eq "warmup") { $JourneyIndex } else { $null }) `
        -UndoAttempt $null `
        -RawXml $checkpointText
    [xml]$checkpointUi = $checkpointText
    Assert-LandscapeEditorUi -Ui $checkpointUi -Workload $Workload | Out-Null
    $dirtyNode = Get-ResourceNode -Ui $checkpointUi -Identity "editor_dirty_document"
    $undoNode = Get-ResourceNode -Ui $checkpointUi -Identity "editor_undo"
    $redoNode = Get-ResourceNode -Ui $checkpointUi -Identity "editor_redo"
    if (
        $null -eq $dirtyNode -or
        (Get-ResourceNodeAttribute -Node $undoNode -Name "enabled") -ne "true" -or
        (Get-ResourceNodeAttribute -Node $redoNode -Name "enabled") -ne "false"
    ) {
        throw "$JourneyKind $JourneyIndex for $Workload did not expose the committed Pencil result."
    }
}

function Get-CurrentEditorUi {
    $remote = "$remotePrefix-current.xml"
    Invoke-TargetAdb -AdbArguments @("shell", "uiautomator", "dump", $remote) | Out-Null
    $text = (Invoke-TargetAdb -AdbArguments @("shell", "cat", $remote)) -join "`n"
    return [xml]$text
}

function Assert-CleanWorkloadReady {
    param(
        [Parameter(Mandatory = $true)][xml]$Ui,
        [Parameter(Mandatory = $true)][string]$Workload
    )

    $geometry = Assert-LandscapeEditorUi -Ui $Ui -Workload $Workload
    $cleanNode = Get-ResourceNode -Ui $Ui -Identity "editor_clean_document"
    $undoNode = Get-ResourceNode -Ui $Ui -Identity "editor_undo"
    $pencilNode = Get-ResourceNode -Ui $Ui -Identity "editor_pencil_tool"
    if (
        $null -eq $cleanNode -or
        (Get-ResourceNodeAttribute -Node $undoNode -Name "enabled") -ne "false" -or
        (Get-ResourceNodeAttribute -Node $pencilNode -Name "checked") -ne "true"
    ) {
        throw "The $Workload operation did not start from its clean Pencil checkpoint."
    }
    return $geometry
}

function Invoke-NodeTap {
    param([Parameter(Mandatory = $true)][System.Xml.XmlElement]$Node)

    $bounds = Get-Bounds -Node $Node
    $x = [Math]::Floor(($bounds.Left + $bounds.Right) / 2.0)
    $y = [Math]::Floor(($bounds.Top + $bounds.Bottom) / 2.0)
    Invoke-TargetAdb -AdbArguments @("shell", "cmd", "input", "tap", "$x", "$y") | Out-Null
}

function Set-BoundedDimensionField {
    param(
        [Parameter(Mandatory = $true)][System.Xml.XmlElement]$Node,
        [Parameter(Mandatory = $true)][ValidatePattern("^\d{1,3}$")][string]$Value
    )

    Invoke-NodeTap -Node $Node
    Invoke-TargetAdb -AdbArguments @("shell", "cmd", "input", "keyevent", "KEYCODE_MOVE_END") | Out-Null
    foreach ($unused in 1..3) {
        Invoke-TargetAdb -AdbArguments @("shell", "cmd", "input", "keyevent", "KEYCODE_DEL") | Out-Null
    }
    Invoke-TargetAdb -AdbArguments @("shell", "cmd", "input", "text", $Value) | Out-Null
}

function New-DocumentThroughUi {
    param(
        [Parameter(Mandatory = $true)][object]$Spec,

        # No-sample inspection needs the created UI itself, because the pinned surface bounds are what
        # the inspection is measuring; the sample path keeps its unchanged pinned-geometry assertion.
        [switch]$GeometryInspection
    )

    $initialUi = Get-CurrentEditorUi
    Assert-LandscapeRootUi -Ui $initialUi
    Invoke-NodeTap -Node (Get-ResourceNode -Ui $initialUi -Identity "editor_file")
    $fileUi = Get-CurrentEditorUi
    Invoke-NodeTap -Node (Get-ResourceNode -Ui $fileUi -Identity "editor_new_document")
    $dialogUi = Get-CurrentEditorUi
    Get-ResourceNode -Ui $dialogUi -Identity "editor_create_document_title" | Out-Null
    Set-BoundedDimensionField `
        -Node (Get-ResourceNode -Ui $dialogUi -Identity "editor_document_width") `
        -Value "$($Spec.canvas_width)"
    $dialogUi = Get-CurrentEditorUi
    Set-BoundedDimensionField `
        -Node (Get-ResourceNode -Ui $dialogUi -Identity "editor_document_height") `
        -Value "$($Spec.canvas_height)"
    $typedUi = Get-CurrentEditorUi
    $widthNode = Get-ResourceNode -Ui $typedUi -Identity "editor_document_width"
    $heightNode = Get-ResourceNode -Ui $typedUi -Identity "editor_document_height"
    if (
        $widthNode.GetAttribute("text") -cne "$($Spec.canvas_width)" -or
        $heightNode.GetAttribute("text") -cne "$($Spec.canvas_height)"
    ) {
        throw "The actual New-document UI did not retain the bounded $($Spec.canvas_width) by $($Spec.canvas_height) dimensions."
    }
    Invoke-NodeTap -Node (Get-ResourceNode -Ui $typedUi -Identity "editor_create")
    Start-Sleep -Milliseconds $drawWaitMilliseconds
    $createdUi = Get-CurrentEditorUi
    if (@($createdUi.SelectNodes("//*[@resource-id]") | Where-Object { $_.GetAttribute("resource-id") -like "*editor_create_document_title" }).Count -ne 0) {
        throw "The New-document dialog remained visible after creating the $($Spec.canvas_width) by $($Spec.canvas_height) document."
    }
    if ($GeometryInspection) {
        return $createdUi
    }
    return Assert-CleanWorkloadReady -Ui $createdUi -Workload $Spec.workload
}

function Invoke-MotionEvent {
    param(
        [Parameter(Mandatory = $true)][ValidateSet("DOWN", "MOVE", "UP")][string]$Event,
        [Parameter(Mandatory = $true)][double]$X,
        [Parameter(Mandatory = $true)][double]$Y
    )

    Invoke-TargetAdb -AdbArguments @(
        "shell",
        "cmd",
        "input",
        "motionevent",
        $Event,
        (Format-InputCoordinate -Value $X),
        (Format-InputCoordinate -Value $Y)
    ) | Out-Null
}

function Invoke-PreviewEventSequence {
    param(
        [Parameter(Mandatory = $true)][object]$Spec,
        [Parameter(Mandatory = $true)][object]$Geometry,
        [switch]$Measured
    )

    Invoke-MotionEvent -Event "DOWN" -X $Geometry.FirstX -Y $Geometry.FirstY
    if ($Measured) {
        $script:measuredWorkloadCounts[$Spec.workload] =
            [int]$script:measuredWorkloadCounts[$Spec.workload] + 1
        Write-RunState -Status "running" -Verdict "unavailable"
    }
    Start-Sleep -Milliseconds $previewWaitMilliseconds
    if ($Spec.move_event_count -gt 0) {
        foreach ($moveIndex in 1..$Spec.move_event_count) {
            $useLast = $moveIndex % 2 -eq 1
            Invoke-MotionEvent `
                -Event "MOVE" `
                -X $(if ($useLast) { $Geometry.LastX } else { $Geometry.FirstX }) `
                -Y $(if ($useLast) { $Geometry.LastY } else { $Geometry.FirstY })
            Start-Sleep -Milliseconds $moveWaitMilliseconds
        }
        Start-Sleep -Milliseconds $postMovePreviewWaitMilliseconds
    }
}

function Invoke-CommitEventSequence {
    param(
        [Parameter(Mandatory = $true)][object]$Spec,
        [Parameter(Mandatory = $true)][object]$Geometry
    )

    $commitAtFirst = $Spec.move_event_count % 2 -eq 0
    Invoke-MotionEvent `
        -Event "UP" `
        -X $(if ($commitAtFirst) { $Geometry.FirstX } else { $Geometry.LastX }) `
        -Y $(if ($commitAtFirst) { $Geometry.FirstY } else { $Geometry.LastY })
    Start-Sleep -Milliseconds $drawWaitMilliseconds
}

function Add-ActualSizeWindowLog {
    param(
        [Parameter(Mandatory = $true)][string]$Workload,
        [Parameter(Mandatory = $true)][string]$Line
    )

    [System.IO.File]::AppendAllText(
        (Join-Path $resolvedOutput "window-$Workload.log"),
        "$([datetime]::UtcNow.ToString('o')) $Line`n",
        [System.Text.UTF8Encoding]::new($false)
    )
}

function Get-ActualSizeWindowUiStep {
    param(
        [Parameter(Mandatory = $true)][string]$Workload,
        [Parameter(Mandatory = $true)][string]$Step
    )

    Start-Sleep -Milliseconds $drawWaitMilliseconds
    $ui = Get-CurrentEditorUi
    [System.IO.File]::WriteAllText(
        (Join-Path $resolvedOutput ("raw/{0}-window-{1}.xml" -f $Workload, $Step)),
        $ui.OuterXml,
        [System.Text.UTF8Encoding]::new($false)
    )
    Add-ActualSizeWindowLog -Workload $Workload -Line ("dump step=$Step")
    return $ui
}

function Get-ActualSizeWindowNodes {
    param([Parameter(Mandatory = $true)][xml]$Ui)

    return @(
        $Ui.SelectNodes("//*[@resource-id]") |
            Where-Object { $_.GetAttribute("resource-id") -like "*editor_actual_size_window*" -and
                $_.GetAttribute("resource-id") -notlike "*editor_actual_size_window_toggle" }
    )
}

function Show-ActualSizeWindowAtScale {
    # Lane 3 family 3 preparation: show the window through the dock control, cycle the chip until the
    # window node describes the required scale (at most six taps, each verified through a dump), and
    # require every family input point to lie outside the window bounds. Any failure is INVALID.
    param(
        [Parameter(Mandatory = $true)][string]$Workload,
        [Parameter(Mandatory = $true)][object]$Geometry
    )

    $ui = Get-CurrentEditorUi
    Add-ActualSizeWindowLog -Workload $Workload -Line ("tap editor_actual_size_window_toggle (show)")
    Invoke-NodeTap -Node (Get-ResourceControlNode -Node (Get-ResourceNode -Ui $ui -Identity "editor_actual_size_window_toggle"))
    $ui = Get-ActualSizeWindowUiStep -Workload $Workload -Step "shown"
    $chipTaps = 0
    while ($true) {
        $windowNode = Get-ResourceNode -Ui $ui -Identity "editor_actual_size_window"
        $description = $windowNode.GetAttribute("content-desc")
        $scaleMatch = [regex]::Match($description, "x(\d+)$")
        $scale = if ($scaleMatch.Success) { "x$($scaleMatch.Groups[1].Value)" } else { "" }
        Add-ActualSizeWindowLog -Workload $Workload -Line ("window scale=$scale chip_taps=$chipTaps")
        if ($scale -ceq $windowDiagnosticScale) {
            break
        }
        if ($chipTaps -ge 6) {
            throw "INVALID: the actual-size window did not describe $windowDiagnosticScale within six chip taps."
        }
        Add-ActualSizeWindowLog -Workload $Workload -Line ("tap editor_actual_size_window_chip")
        Invoke-NodeTap -Node (Get-ResourceControlNode -Node (Get-ResourceNode -Ui $ui -Identity "editor_actual_size_window_chip"))
        $chipTaps += 1
        $ui = Get-ActualSizeWindowUiStep -Workload $Workload -Step "chip-$chipTaps"
    }
    $windowBoundsText = $windowNode.GetAttribute("bounds")
    $windowBounds = Get-Bounds -Node $windowNode
    Add-ActualSizeWindowLog -Workload $Workload -Line ("window bounds=$windowBoundsText")
    foreach ($point in @(
            @($Geometry.FirstX, $Geometry.FirstY),
            @($Geometry.LastX, $Geometry.LastY)
        )) {
        if (
            $point[0] -ge $windowBounds.Left -and $point[0] -le $windowBounds.Right -and
            $point[1] -ge $windowBounds.Top -and $point[1] -le $windowBounds.Bottom
        ) {
            throw "INVALID: the $Workload input point ($($point[0]),$($point[1])) lies inside the actual-size window $windowBoundsText."
        }
    }
    return [pscustomobject]@{
        Scale = $scale
        Bounds = $windowBoundsText
        ChipTaps = $chipTaps
    }
}

function Hide-ActualSizeWindow {
    param(
        [Parameter(Mandatory = $true)][string]$Workload
    )

    $ui = Get-CurrentEditorUi
    Add-ActualSizeWindowLog -Workload $Workload -Line ("tap editor_actual_size_window_toggle (hide)")
    Invoke-NodeTap -Node (Get-ResourceControlNode -Node (Get-ResourceNode -Ui $ui -Identity "editor_actual_size_window_toggle"))
    $ui = Get-ActualSizeWindowUiStep -Workload $Workload -Step "hidden"
    $remaining = @(Get-ActualSizeWindowNodes -Ui $ui)
    Add-ActualSizeWindowLog -Workload $Workload -Line ("window nodes after hide=$($remaining.Count)")
    if ($remaining.Count -ne 0) {
        throw "INVALID: the actual-size window remained visible after $Workload."
    }
}

function Write-RotationStateArtifact {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][object]$State,
        [string[]]$AdditionalLines = @()
    )

    $lines = @(
        "mode=$($State.mode)",
        "numeric_user_rotation=$($State.numeric_user_rotation)",
        "reported_user_rotation=$($State.reported_user_rotation)",
        "current_rotation=$($State.current_rotation)",
        "logical_width=$($State.logical_width)",
        "logical_height=$($State.logical_height)"
    ) + $AdditionalLines
    [System.IO.File]::WriteAllLines(
        (Join-Path $resolvedOutput $Name),
        $lines,
        [System.Text.UTF8Encoding]::new($false)
    )
}

function Initialize-PinnedRotation {
    $script:rotationPinAttempted = $true
    Invoke-TargetAdb -AdbArguments @("shell", "wm", "user-rotation", "lock", "$requiredRotation") | Out-Null
    $pinned = Get-WindowRotationState
    Write-RotationStateArtifact `
        -Name "rotation-pin.txt" `
        -State $pinned `
        -AdditionalLines @("command=wm user-rotation lock $requiredRotation")
    if (
        $pinned.mode -ne "locked" -or
        $pinned.numeric_user_rotation -ne $requiredRotation -or
        $pinned.reported_user_rotation -ne $requiredRotation
    ) {
        throw "WindowManager did not accept the required landscape rotation lock."
    }
}

function Restore-OriginalRotation {
    param([Parameter(Mandatory = $true)][object]$Original)

    $commands = [System.Collections.Generic.List[string]]::new()
    if ($Original.mode -eq "locked") {
        $commands.Add("wm user-rotation lock $($Original.numeric_user_rotation)")
        Invoke-TargetAdb -AdbArguments @(
            "shell", "wm", "user-rotation", "lock", "$($Original.numeric_user_rotation)"
        ) | Out-Null
    }
    else {
        $commands.Add("wm user-rotation lock $($Original.numeric_user_rotation)")
        Invoke-TargetAdb -AdbArguments @(
            "shell", "wm", "user-rotation", "lock", "$($Original.numeric_user_rotation)"
        ) | Out-Null
        $commands.Add("wm user-rotation free")
        Invoke-TargetAdb -AdbArguments @("shell", "wm", "user-rotation", "free") | Out-Null
    }

    $restored = Get-WindowRotationState
    $verified =
        $restored.mode -eq $Original.mode -and
        $restored.numeric_user_rotation -eq $Original.numeric_user_rotation -and
        $restored.reported_user_rotation -eq $Original.numeric_user_rotation -and
        ($Original.mode -eq "free" -or $restored.current_rotation -eq $Original.current_rotation)
    Write-RotationStateArtifact `
        -Name "rotation-restore.txt" `
        -State $restored `
        -AdditionalLines (@($commands | ForEach-Object { "command=$_" }) + @(
            "original_mode=$($Original.mode)",
            "original_numeric_user_rotation=$($Original.numeric_user_rotation)",
            "original_current_rotation=$($Original.current_rotation)",
            "restore_verified=$(([string]$verified).ToLowerInvariant())"
        ))
    if (-not $verified) {
        throw "WindowManager rotation restoration could not be verified."
    }
}

function Test-RemotePathExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $result =
        (Invoke-TargetAdb -AdbArguments @("shell", "if [ -e '$Path' ]; then echo exists; else echo absent; fi") |
            Select-Object -First 1).Trim()
    if ($result -notin @("exists", "absent")) {
        throw "Unable to determine whether an exact physical-present staging path exists."
    }
    return $result -eq "exists"
}

function Get-PerfettoSessionMatchCount {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SessionName
    )

    $serviceState = @(Invoke-TargetAdb -AdbArguments @("shell", "perfetto", "--query", "--long"))
    return @($serviceState | Where-Object { $_ -like "*$SessionName*" }).Count
}

function Start-PhysicalPresentTrace {
    $batchId = [guid]::NewGuid().ToString("D")
    $triggerName = "nene-m2-physical-present-$batchId"
    $remoteConfig = "/data/misc/perfetto-configs/nene-m2-physical-present-$batchId.txtpb"
    $remoteTrace = "/data/misc/perfetto-traces/nene-m2-physical-present-$batchId.perfetto-trace"
    $localConfig = Join-Path $resolvedOutput "physical-present-config.txtpb"
    $localTrace = Join-Path $resolvedOutput "physical-present.perfetto-trace"
    $toolPath = Join-Path $resolvedOutput "physical-present-tool.txt"
    foreach ($localPath in @($localConfig, $localTrace, $toolPath)) {
        if (Test-Path -LiteralPath $localPath) {
            throw "Physical-present collection refuses to overwrite $localPath."
        }
    }
    foreach ($remotePath in @($remoteConfig, $remoteTrace)) {
        if (Test-RemotePathExists -Path $remotePath) {
            throw "Physical-present collection found a pre-existing exact device staging path."
        }
    }

    $state = [pscustomobject]@{
        BatchId = $batchId
        TriggerName = $triggerName
        RemoteConfig = $remoteConfig
        RemoteTrace = $remoteTrace
        LocalConfig = $localConfig
        LocalTrace = $localTrace
        ToolPath = $toolPath
        LauncherReportedPid = 0
        Active = $false
        StopRequested = $false
    }
    $script:physicalTraceState = $state

    $config = @"
unique_session_name: "$triggerName"
buffers {
  size_kb: 65536
  fill_policy: RING_BUFFER
}
data_sources {
  config {
    name: "android.surfaceflinger.frametimeline"
    target_buffer: 0
  }
}
data_sources {
  config {
    name: "linux.ftrace"
    target_buffer: 0
    ftrace_config {
      ftrace_events: "sched/sched_switch"
      ftrace_events: "sched/sched_waking"
      ftrace_events: "sched/sched_wakeup_new"
      ftrace_events: "power/cpu_frequency"
      ftrace_events: "power/cpu_idle"
      atrace_categories: "am"
      atrace_categories: "binder_driver"
      atrace_categories: "freq"
      atrace_categories: "gfx"
      atrace_categories: "hal"
      atrace_categories: "idle"
      atrace_categories: "input"
      atrace_categories: "sched"
      atrace_categories: "view"
      atrace_apps: "$packageName"
      buffer_size_kb: 16384
      drain_period_ms: 250
      compact_sched {
        enabled: true
      }
    }
  }
}
data_sources {
  config {
    name: "linux.process_stats"
    target_buffer: 0
    process_stats_config {
      scan_all_processes_on_start: true
      proc_stats_poll_ms: 1000
    }
  }
}
builtin_data_sources {
  disable_clock_snapshotting: false
  disable_trace_config: false
  disable_system_info: false
}
flush_period_ms: 5000
trigger_config {
  trigger_mode: STOP_TRACING
  trigger_timeout_ms: 300000
  triggers {
    name: "$triggerName"
    stop_delay_ms: 1000
  }
}
"@
    [System.IO.File]::WriteAllText($localConfig, $config, [System.Text.UTF8Encoding]::new($false))
    Invoke-TargetAdb -AdbArguments @("push", $localConfig, $remoteConfig) | Out-Null
    $startCommand = "perfetto --background-wait --txt -c $remoteConfig -o $remoteTrace"
    $startOutput = @(Invoke-TargetAdb -AdbArguments @("shell", "perfetto", "--background-wait", "--txt", "-c", $remoteConfig, "-o", $remoteTrace))
    $pidMatches = @($startOutput | Where-Object { $_.Trim() -match "^\d+$" })
    if ($pidMatches.Count -ne 1) {
        throw "Perfetto did not return exactly one background tracing PID."
    }
    $launcherReportedPid = [int]$pidMatches[0].Trim()
    $state.LauncherReportedPid = $launcherReportedPid
    $state.Active = $true

    $toolLines = @(
        "schema=$physicalPresentSchema",
        "batch_id=$batchId",
        "source_commit=$SourceCommit",
        "trace_trigger=$triggerName",
        "remote_config=$remoteConfig",
        "remote_trace=$remoteTrace",
        "start_command=adb -s <physical-device> shell $startCommand",
        "launcher_reported_pid=$launcherReportedPid",
        "liveness_check=exact unique session name through perfetto --query --long",
        "stop_command=adb -s <physical-device> shell /system/bin/trigger_perfetto $triggerName",
        "normal_stop=trigger-only; no manual process kill"
    )
    [System.IO.File]::WriteAllLines($toolPath, $toolLines, [System.Text.UTF8Encoding]::new($false))
    $activeSessionCount = Get-PerfettoSessionMatchCount -SessionName $triggerName
    if ($activeSessionCount -ne 1) {
        throw "Perfetto service state does not expose exactly one named physical-present session before samples."
    }

    return $state
}

function Stop-PhysicalPresentTrace {
    param(
        [Parameter(Mandatory = $true)]
        [object]$State
    )

    if (-not $State.Active) {
        throw "Physical-present trace is not active."
    }
    if (-not $State.StopRequested) {
        Invoke-TargetAdb -AdbArguments @("shell", "/system/bin/trigger_perfetto", $State.TriggerName) | Out-Null
        $State.StopRequested = $true
    }
    $deadline = (Get-Date).AddSeconds(30)
    do {
        $activeSessionCount = Get-PerfettoSessionMatchCount -SessionName $State.TriggerName
        if ($activeSessionCount -eq 0) {
            break
        }
        if ($activeSessionCount -ne 1) {
            throw "Perfetto service state exposes an ambiguous physical-present session count."
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    if ($activeSessionCount -ne 0) {
        throw "Perfetto did not finalize within 30 seconds after the stop trigger."
    }

    $remoteBytesText =
        (Invoke-TargetAdb -AdbArguments @("shell", "stat", "-c", "%s", $State.RemoteTrace) |
            Select-Object -First 1).Trim()
    $remoteBytes = 0L
    if (-not [long]::TryParse($remoteBytesText, [ref]$remoteBytes) -or $remoteBytes -le 0) {
        throw "Perfetto finalized without a non-empty physical-present trace."
    }
    Invoke-TargetAdb -AdbArguments @("pull", $State.RemoteTrace, $State.LocalTrace) | Out-Null
    $localBytes = (Get-Item -LiteralPath $State.LocalTrace).Length
    if ($localBytes -ne $remoteBytes) {
        throw "The pulled physical-present trace length does not match the finalized device file."
    }
    $State.Active = $false
    $toolWriter = [System.IO.StreamWriter]::new(
        $State.ToolPath,
        $true,
        [System.Text.UTF8Encoding]::new($false)
    )
    try {
        $toolWriter.WriteLine("trace_finalized_before_pull=true")
        $toolWriter.WriteLine("remote_trace_bytes=$remoteBytes")
        $toolWriter.WriteLine("local_trace_bytes=$localBytes")
    }
    finally {
        $toolWriter.Dispose()
    }
}

function Get-NoSampleCanvasBounds {
    param(
        [Parameter(Mandatory = $true)][xml]$Ui,
        [Parameter(Mandatory = $true)][object]$Spec
    )

    $identity = "editor_canvas_$($Spec.canvas_width)_$($Spec.canvas_height)"
    $bounds = Get-Bounds -Node (Get-ResourceNode -Ui $Ui -Identity $identity)
    if (
        $bounds.Left -lt 0 -or
        $bounds.Top -lt 0 -or
        $bounds.Right -le $bounds.Left -or
        $bounds.Bottom -le $bounds.Top -or
        $bounds.Right -gt $requiredLogicalWidth -or
        $bounds.Bottom -gt $requiredLogicalHeight
    ) {
        throw "The inspected $identity surface is not a positive rectangle inside the pinned landscape root."
    }
    return "[$($bounds.Left),$($bounds.Top)][$($bounds.Right),$($bounds.Bottom)]"
}

function ConvertTo-NoSampleInspectionRecord {
    param(
        [Parameter(Mandatory = $true)][object]$Value,
        [Parameter(Mandatory = $true)][string]$Role
    )

    $required = @(
        "schema", "role", "apk_sha256", "embedded_source_commit", "rotation", "root_bounds",
        "canvas16_bounds", "canvas256_bounds", "geometry_id", "ui_dump_sha256s", "captured_utc"
    )
    $propertyNames = @($Value.PSObject.Properties.Name)
    foreach ($key in $required) {
        if ($key -notin $propertyNames) {
            throw "The existing $Role no-sample inspection record is missing '$key'."
        }
    }
    if ($Value.schema -cne $noSampleInspectionSchema -or $Value.role -cne $Role) {
        throw "The existing $Role no-sample inspection record does not carry its own schema and role."
    }
    $dumps = [ordered]@{}
    foreach ($property in $Value.ui_dump_sha256s.PSObject.Properties) {
        $dumps[$property.Name] = [string]$property.Value
    }
    return [ordered]@{
        schema = [string]$Value.schema
        role = [string]$Value.role
        apk_sha256 = [string]$Value.apk_sha256
        embedded_source_commit = [string]$Value.embedded_source_commit
        rotation = [int]$Value.rotation
        root_bounds = [string]$Value.root_bounds
        canvas16_bounds = [string]$Value.canvas16_bounds
        canvas256_bounds = [string]$Value.canvas256_bounds
        geometry_id = [string]$Value.geometry_id
        ui_dump_sha256s = $dumps
        captured_utc = [string]$Value.captured_utc
    }
}

$script:inspectionRotationRestoreError = $null
$script:inspectionCleanupErrors = [System.Collections.Generic.List[string]]::new()

function Restore-OriginalStayAwake {
    param([AllowNull()][Parameter(Mandatory = $true)][AllowEmptyString()][string]$Original)

    if ([string]::IsNullOrEmpty($Original)) {
        return
    }
    # `settings get` reports an absent global as the literal text "null"; putting that back would
    # store the four-character string instead of restoring the unset state.
    if ($Original -ceq "null") {
        Invoke-TargetAdb -AdbArguments @(
            "shell", "settings", "delete", "global", "stay_on_while_plugged_in"
        ) | Out-Null
        return
    }
    Invoke-TargetAdb -AdbArguments @(
        "shell", "settings", "put", "global", "stay_on_while_plugged_in", $Original
    ) | Out-Null
}

function Invoke-NoSampleGeometryInspection {
    $inspectionPath = Join-Path $resolvedInspection "no-sample-inspection-$CandidateRole.json"
    $combinedPath = Join-Path $resolvedInspection "no-sample-inspection.json"
    $originalRotation = $null
    $stayAwake = $null
    $pinAttempted = $false
    $record = $null
    try {
        Get-PhysicalDeviceIdentity | Out-Null
        $originalRotation = Get-WindowRotationState
        Write-RotationStateArtifact -Name "rotation-original.txt" -State $originalRotation
        if (
            $originalRotation.numeric_user_rotation -ne $originalRotation.reported_user_rotation -or
            ($originalRotation.mode -eq "locked" -and
                $originalRotation.current_rotation -ne $originalRotation.numeric_user_rotation)
        ) {
            throw "The original WindowManager rotation state is internally inconsistent."
        }
        $pinAttempted = $true
        Initialize-PinnedRotation
        $stayAwake =
            (Invoke-TargetAdb -AdbArguments @("shell", "settings", "get", "global", "stay_on_while_plugged_in") |
                Select-Object -First 1).Trim()
        Invoke-TargetAdb -AdbArguments @("shell", "svc", "power", "stayon", "usb") | Out-Null
        Invoke-TargetAdb -AdbArguments @("shell", "cmd", "input", "keyevent", "WAKEUP") | Out-Null
        Start-Sleep -Milliseconds 250
        Invoke-TargetAdb -AdbArguments @("install", "-r", "-d", $artifactIdentity.resolved_apk_path) | Out-Null
        Invoke-TargetAdb -AdbArguments @("shell", "am", "force-stop", $packageName) | Out-Null
        Invoke-TargetAdb -AdbArguments @("shell", "am", "start", "-W", "-n", $activityName) | Out-Null
        Start-Sleep -Milliseconds 1500

        $dumpHashes = [ordered]@{}
        $observedBounds = [ordered]@{}
        $rootBounds = $null
        foreach ($spec in $workloadCatalog) {
            $createdUi = New-DocumentThroughUi -Spec $spec -GeometryInspection
            Assert-LandscapeRootUi -Ui $createdUi
            $currentRootBounds = $createdUi.DocumentElement.SelectSingleNode("./node").GetAttribute("bounds")
            if ($null -ne $rootBounds -and $currentRootBounds -cne $rootBounds) {
                throw "The pinned editor root bounds changed between the inspected canvas families."
            }
            $rootBounds = $currentRootBounds
            $observedBounds[$spec.workload] = Get-NoSampleCanvasBounds -Ui $createdUi -Spec $spec
            $dumpName = "ui-inspect-$($spec.canvas_width)x$($spec.canvas_height).xml"
            $dumpPath = Join-Path $resolvedOutput "raw/$dumpName"
            Write-Utf8CreateNew -Path $dumpPath -Text $createdUi.OuterXml
            $dumpHashes[$dumpName] = Get-FileSha256 -Path $dumpPath
        }

        $record = [ordered]@{
            schema = $noSampleInspectionSchema
            role = $CandidateRole
            apk_sha256 = $artifactIdentity.apk_sha256
            embedded_source_commit = $artifactIdentity.embedded_source_commit
            rotation = $requiredRotation
            root_bounds = $rootBounds
            canvas16_bounds = $observedBounds["canvas16_tap"]
            canvas256_bounds = $observedBounds["canvas256_repeated_diagonal"]
            geometry_id = $geometryId
            ui_dump_sha256s = $dumpHashes
            captured_utc = [datetime]::UtcNow.ToString("o")
        }
        # The observed record is published before any comparison, so a mismatch leaves the operator the
        # exact values to fix the manifest with.
        Write-Utf8CreateNew -Path $inspectionPath -Text (($record | ConvertTo-Json -Depth 5) + "`n")
        if ($record.root_bounds -cne $requiredRootBounds) {
            throw "The inspected editor root bounds are not the pinned $requiredRootBounds contract; see $inspectionPath."
        }
        if (
            $record.canvas16_bounds -cne $expectedSurfaceBoundsByWorkload.canvas16_tap -or
            $record.canvas256_bounds -cne $expectedSurfaceBoundsByWorkload.canvas256_repeated_diagonal
        ) {
            throw "The inspected $CandidateRole surface bounds do not match the supplied geometry; fill the manifest from $inspectionPath and inspect again."
        }
        $otherRole = if ($CandidateRole -eq "baseline") { "candidate" } else { "baseline" }
        $otherPath = Join-Path $resolvedInspection "no-sample-inspection-$otherRole.json"
        if ((Test-Path -LiteralPath $otherPath -PathType Leaf) -and -not (Test-Path -LiteralPath $combinedPath)) {
            $otherRecord =
                ConvertTo-NoSampleInspectionRecord `
                    -Value (Get-Content -Raw -LiteralPath $otherPath | ConvertFrom-Json) `
                    -Role $otherRole
            $combined = [ordered]@{
                schema = $noSampleInspectionSchema
                baseline = if ($CandidateRole -eq "baseline") { $record } else { $otherRecord }
                candidate = if ($CandidateRole -eq "candidate") { $record } else { $otherRecord }
            }
            Write-Utf8CreateNew -Path $combinedPath -Text (($combined | ConvertTo-Json -Depth 6) + "`n")
        }
    }
    finally {
        if ($pinAttempted -and $null -ne $originalRotation) {
            try {
                Restore-OriginalRotation -Original $originalRotation
            }
            catch {
                $script:inspectionRotationRestoreError = $_.Exception.Message
                try {
                    [System.IO.File]::WriteAllLines(
                        (Join-Path $resolvedOutput "rotation-restore-failure.txt"),
                        @(
                            "restore_verified=false",
                            "source_error=$($script:inspectionRotationRestoreError -replace '[\r\n]+', ' ')"
                        ),
                        [System.Text.UTF8Encoding]::new($false)
                    )
                }
                catch {
                    # Cleanup evidence must not replace the source inspection result.
                }
            }
        }
        # Cleanup must never replace the source inspection result; failures are recorded, not thrown.
        try {
            Restore-OriginalStayAwake -Original $stayAwake
        }
        catch {
            $script:inspectionCleanupErrors.Add("stay_awake_restore=$($_.Exception.Message -replace '[\r\n]+', ' ')")
        }
        try {
            Invoke-TargetAdb -AdbArguments @(
                "shell",
                "rm",
                "-f",
                "$remotePrefix-checkpoint.xml",
                "$remotePrefix-current.xml"
            ) | Out-Null
        }
        catch {
            $script:inspectionCleanupErrors.Add("remote_cleanup=$($_.Exception.Message -replace '[\r\n]+', ' ')")
        }
        if ($script:inspectionCleanupErrors.Count -gt 0) {
            try {
                [System.IO.File]::WriteAllLines(
                    (Join-Path $resolvedOutput "cleanup-failure.txt"),
                    @($script:inspectionCleanupErrors),
                    [System.Text.UTF8Encoding]::new($false)
                )
            }
            catch {
                # Cleanup evidence must not replace the source inspection result.
            }
        }
    }
    return $record
}

if ($InspectGeometryOnly) {
    $inspectionRecord = Invoke-NoSampleGeometryInspection
    if ($null -ne $script:inspectionRotationRestoreError) {
        throw "The no-sample inspection is invalid because rotation restoration was not verified: $($script:inspectionRotationRestoreError)"
    }
    return [pscustomobject]$inspectionRecord
}

$deviceIdentity = $null
$installedApkIdentity = $null
$originalStayAwake = $null
$environmentRows = [System.Collections.Generic.List[object]]::new()
$environmentPath = Join-Path $resolvedOutput "environment.csv"
$frameRows = [System.Collections.Generic.List[object]]::new()
$sampleSummaries = [System.Collections.Generic.List[object]]::new()
$familyResults = [System.Collections.Generic.List[object]]::new()
$earlyStopStatus = $null
$lastCompletedWorkload = $null
$isDecisionLane = $Variant -eq "release-like" -and $CompilationMode -eq "speed-profile" -and $RunKind -eq "decision"
$expectedProductionCommit =
    if ($CandidateRole -eq "baseline") { $BaselineProductionCommit } else { $CandidateProductionCommit }
$framesPath = Join-Path $resolvedOutput "frames.csv"
$summariesPath = Join-Path $resolvedOutput "samples.csv"
$script:latestCommittedResultSnapshot = $null
$script:latestUndoResetSnapshot = $null
$script:rotationPinAttempted = $false
$originalRotationState = $null
$rotationRestoreError = $null
$successfulMetadata = $null
$script:collectionCleanupErrors = [System.Collections.Generic.List[string]]::new()

try {
    $deviceIdentity = Get-PhysicalDeviceIdentity
    $originalRotationState = Get-WindowRotationState
    Write-RotationStateArtifact -Name "rotation-original.txt" -State $originalRotationState
    if (
        $originalRotationState.numeric_user_rotation -ne $originalRotationState.reported_user_rotation -or
        ($originalRotationState.mode -eq "locked" -and
            $originalRotationState.current_rotation -ne $originalRotationState.numeric_user_rotation)
    ) {
        throw "The original WindowManager rotation state is internally inconsistent."
    }
    Initialize-PinnedRotation
    $originalStayAwake =
        (Invoke-TargetAdb -AdbArguments @("shell", "settings", "get", "global", "stay_on_while_plugged_in") |
            Select-Object -First 1).Trim()
    Invoke-TargetAdb -AdbArguments @("shell", "svc", "power", "stayon", "usb") | Out-Null
    Invoke-TargetAdb -AdbArguments @("shell", "cmd", "input", "keyevent", "WAKEUP") | Out-Null
    Start-Sleep -Milliseconds 250
    Invoke-TargetAdb -AdbArguments @("install", "-r", "-d", $artifactIdentity.resolved_apk_path) | Out-Null
    # Slot admission: the installed package, not the host file, must be the role's release-like artifact.
    $installedApkIdentity = Assert-M2InstalledApkIdentity -ExpectedApkSha256 $expectedApkSha256 -Role $CandidateRole
    Invoke-TargetAdb -AdbArguments @("shell", "cmd", "package", "compile", "--reset", $packageName) | Out-Null
    $profileInstallResult = "not-requested"
    if ($CompilationMode -eq "speed-profile") {
        $receiver = "$packageName/androidx.profileinstaller.ProfileInstallReceiver"
        $installOutput =
            Invoke-TargetAdb -AdbArguments @(
                "shell",
                "am",
                "broadcast",
                "-a",
                "androidx.profileinstaller.action.INSTALL_PROFILE",
                $receiver
            )
        $installText = $installOutput -join "`n"
        if ($installText -notmatch "result=$profileInstallSuccessResult(?:\D|$)") {
            throw "Packaged Baseline Profile installation did not report result $profileInstallSuccessResult."
        }
        $profileInstallResult = "success"
        Invoke-TargetAdb -AdbArguments @("shell", "am", "force-stop", $packageName) | Out-Null
    }
    $compileResult =
        Invoke-TargetAdb -AdbArguments @("shell", "cmd", "package", "compile", "-m", $CompilationMode, "-f", $packageName)
    if (($compileResult -join "`n") -notmatch "Success") {
        throw "$CompilationMode compilation did not report success."
    }
    $dexoptText = (Invoke-TargetAdb -AdbArguments @("shell", "dumpsys", "package", "dexopt")) -join "`n"
    $packageDexopt = Assert-M2PackageSpeedProfile -DexoptText $dexoptText -PackageName $packageName
    [System.IO.File]::WriteAllText(
        (Join-Path $resolvedOutput "compile-state.txt"),
        $packageDexopt,
        [System.Text.UTF8Encoding]::new($false)
    )

    Invoke-TargetAdb -AdbArguments @("shell", "am", "force-stop", $packageName) | Out-Null
    Invoke-TargetAdb -AdbArguments @("shell", "am", "start", "-W", "-n", $activityName) | Out-Null
    Start-Sleep -Milliseconds 1500
    $environmentRows.Add((Get-PhysicalCheckpoint -Name "before_warmups"))

    New-DocumentThroughUi -Spec $workloadCatalog[0] | Out-Null

    $beforeRemote = "$remotePrefix-before.xml"
    $beforeLocal = Join-Path $resolvedOutput "ui-before.xml"
    Invoke-TargetAdb -AdbArguments @("shell", "uiautomator", "dump", $beforeRemote) | Out-Null
    Invoke-TargetAdb -AdbArguments @("pull", $beforeRemote, $beforeLocal) | Out-Null
    [xml]$beforeUi = Get-Content -Raw -LiteralPath $beforeLocal
    Assert-CleanWorkloadReady -Ui $beforeUi -Workload "canvas16_tap" | Out-Null
    $undoNode = Get-ResourceNode -Ui $beforeUi -Identity "editor_undo"
    $redoNode = Get-ResourceNode -Ui $beforeUi -Identity "editor_redo"
    if (
        (Get-ResourceNodeAttribute -Node $undoNode -Name "enabled") -ne "false" -or
        (Get-ResourceNodeAttribute -Node $redoNode -Name "enabled") -ne "false"
    ) {
        throw "The frame journey did not start with empty Undo and Redo history."
    }
    $undoBounds = Get-Bounds -Node (Get-ResourceControlNode -Node $undoNode)
    $undoX = [int][Math]::Floor(($undoBounds.Left + $undoBounds.Right) / 2.0)
    $undoY = [int][Math]::Floor(($undoBounds.Top + $undoBounds.Bottom) / 2.0)

    Invoke-TargetAdb -AdbArguments @("logcat", "-c") | Out-Null
    if ($physicalPresentEnabled) {
        $physicalTraceState = Start-PhysicalPresentTrace
    }
    $operationOrdinal = 0
    $windowRecords = [ordered]@{}
    foreach ($spec in $slotWorkloadCatalog) {
        $workload = $spec.workload
        $isWindowFamily = $workload -ceq $windowDiagnosticWorkload
        # The window family reuses the clean 256 by 256 document left by canvas256_repeated_diagonal.
        if ($workload -ne $workloadCatalog[0].workload -and -not $isWindowFamily) {
            New-DocumentThroughUi -Spec $spec | Out-Null
        }
        if ($isWindowFamily) {
            $windowRecords[$workload] =
                Show-ActualSizeWindowAtScale `
                    -Workload $workload `
                    -Geometry (Assert-CleanWorkloadReady -Ui (Get-CurrentEditorUi) -Workload $workload)
        }
        foreach ($warmupIndex in 1..$warmupCount) {
            $geometry = Assert-CleanWorkloadReady -Ui (Get-CurrentEditorUi) -Workload $workload
            Invoke-PreviewEventSequence -Spec $spec -Geometry $geometry
            Invoke-CommitEventSequence -Spec $spec -Geometry $geometry
            Assert-CommittedResult -JourneyKind "warmup" -JourneyIndex $warmupIndex -Workload $workload
            Invoke-UndoToCleanCheckpoint `
                -UndoX $undoX `
                -UndoY $undoY `
                -Phase "warmup-reset" `
                -JourneyIndex $warmupIndex `
                -Workload $workload
        }
        $environmentRows.Add((Get-PhysicalCheckpoint -Name "before_${workload}_samples"))
        foreach ($sampleIndex in 1..$sampleCount) {
            $operationOrdinal += 1
            $geometry = Assert-CleanWorkloadReady -Ui (Get-CurrentEditorUi) -Workload $workload
            Invoke-TargetAdb -AdbArguments @("shell", "dumpsys", "gfxinfo", $packageName, "reset") | Out-Null
            Invoke-PreviewEventSequence -Spec $spec -Geometry $geometry -Measured
            $previewCapture =
                Get-OperationPhaseCapture `
                    -SampleIndex $sampleIndex `
                    -Workload $workload `
                    -OperationOrdinal $operationOrdinal `
                    -Phase "preview" `
                    -EventCount $spec.preview_event_count
            Assert-LandscapeEditorUi -Ui (Get-CurrentEditorUi) -Workload $workload | Out-Null
            Invoke-TargetAdb -AdbArguments @("shell", "dumpsys", "gfxinfo", $packageName, "reset") | Out-Null
            Invoke-CommitEventSequence -Spec $spec -Geometry $geometry
            $commitCapture =
                Get-OperationPhaseCapture `
                    -SampleIndex $sampleIndex `
                    -Workload $workload `
                    -OperationOrdinal $operationOrdinal `
                    -Phase "commit" `
                    -EventCount $spec.commit_event_count
            Assert-CommittedResult -JourneyKind "sample" -JourneyIndex $sampleIndex -Workload $workload
            $verifiedCommitCapture =
                Get-OperationPhaseCapture `
                    -SampleIndex $sampleIndex `
                    -Workload $workload `
                    -OperationOrdinal $operationOrdinal `
                    -Phase "commit" `
                    -EventCount $spec.commit_event_count `
                    -ArtifactPhase "commit-after-ui-verification"
            Assert-UnchangedPhaseCapture `
                -BeforeUiVerification $commitCapture `
                -AfterUiVerification $verifiedCommitCapture `
                -SampleIndex $sampleIndex `
                -Workload $workload
            @($previewCapture.Rows) + @($commitCapture.Rows) | ForEach-Object { $frameRows.Add($_) }
            $operationTiming =
                Get-OperationTiming -PreviewRows @($previewCapture.Rows) -CommitRows @($commitCapture.Rows)
            $sampleSummaries.Add(
                [pscustomobject]@{
                    variant = $Variant
                    source_commit = $SourceCommit
                    production_commit =
                        if ($CandidateRole -eq "baseline") { $BaselineProductionCommit } else { $CandidateProductionCommit }
                    workload = $workload
                    operation = "$workload#$sampleIndex"
                    operation_ordinal = $operationOrdinal
                    sample_index = $sampleIndex
                    motion_event_count = $spec.motion_event_count
                    preview_event_count = $spec.preview_event_count
                    commit_event_count = $spec.commit_event_count
                    raw_position_count = $spec.raw_position_count
                    effective_change_count = $spec.effective_change_count
                    preview_frame_count = $previewCapture.Rows.Count
                    preview_raw_frame_count = $previewCapture.Rows.Count
                    preview_valid_frame_count = $previewCapture.Rows.Count
                    commit_frame_count = $commitCapture.Rows.Count
                    commit_raw_frame_count = $commitCapture.Rows.Count
                    commit_valid_frame_count = $commitCapture.Rows.Count
                    total_frames_rendered =
                        $previewCapture.TotalFramesRendered + $commitCapture.TotalFramesRendered
                    janky_frames = $previewCapture.JankyFrames + $commitCapture.JankyFrames
                    deadline_missed_frames =
                        $previewCapture.DeadlineMissedFrames + $commitCapture.DeadlineMissedFrames
                    raw_row_count = $previewCapture.Rows.Count + $commitCapture.Rows.Count
                    valid_row_count = $previewCapture.Rows.Count + $commitCapture.Rows.Count
                    committed_ui_verified = $true
                    preview_input_start_nanos = $operationTiming.preview_input_start_nanos
                    commit_input_start_nanos = $operationTiming.commit_input_start_nanos
                    committed_result_completion_nanos = $operationTiming.committed_result_completion_nanos
                    input_to_committed_result_ms = $operationTiming.input_to_committed_result_ms
                    down_to_committed_result_ms = $operationTiming.down_to_committed_result_ms
                }
            )
            if ($operationOrdinal % 10 -eq 0) {
                $environmentRows.Add((Get-PhysicalCheckpoint -Name "after_operation_$operationOrdinal"))
            }
            Invoke-UndoToCleanCheckpoint `
                -UndoX $undoX `
                -UndoY $undoY `
                -Phase "sample-reset" `
                -JourneyIndex $sampleIndex `
                -Workload $workload
        }
        if ($isWindowFamily) {
            Hide-ActualSizeWindow -Workload $workload
        }
        $familySummaries = @($sampleSummaries | Where-Object { $_.workload -ceq $workload })
        $familyFrames = @($frameRows | Where-Object { $_.workload -ceq $workload })
        $familyResult =
            Get-CompletedFamilyResult `
                -Spec $spec `
                -Summaries $familySummaries `
                -Frames $familyFrames `
                -ExpectedProductionCommit $expectedProductionCommit
        $familyResults.Add($familyResult)
        $lastCompletedWorkload = $workload
        $frameRows | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath $framesPath
        $sampleSummaries | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath $summariesPath
        $familyLogcat = @(Invoke-TargetAdb -AdbArguments @("logcat", "-d", "-v", "threadtime"))
        [System.IO.File]::WriteAllLines((Join-Path $resolvedOutput "logcat-after-$workload.txt"), $familyLogcat)
        if (@($familyLogcat | Select-String -Pattern "FATAL EXCEPTION|ANR in|Fatal signal|Process .* has died").Count -gt 0) {
            throw "Fatal, ANR, signal, or process-death evidence was observed after $workload."
        }
        if ($RunKind -eq "diagnostic" -and $familyResult.GrossRegression) {
            $earlyStopStatus = "gross-regression"
            break
        }
    }

    $afterRemote = "$remotePrefix-after.xml"
    $screenRemote = "$remotePrefix-after.png"
    $afterLocal = Join-Path $resolvedOutput "ui-after.xml"
    $screenLocal = Join-Path $resolvedOutput "frame-after.png"
    Invoke-TargetAdb -AdbArguments @("shell", "uiautomator", "dump", $afterRemote) | Out-Null
    Invoke-TargetAdb -AdbArguments @("shell", "screencap", "-p", $screenRemote) | Out-Null
    Invoke-TargetAdb -AdbArguments @("pull", $afterRemote, $afterLocal) | Out-Null
    Invoke-TargetAdb -AdbArguments @("pull", $screenRemote, $screenLocal) | Out-Null
    [xml]$afterUi = Get-Content -Raw -LiteralPath $afterLocal
    Assert-CleanWorkloadReady -Ui $afterUi -Workload $lastCompletedWorkload | Out-Null
    $afterRedo = Get-ResourceNode -Ui $afterUi -Identity "editor_redo"
    if ((Get-ResourceNodeAttribute -Node $afterRedo -Name "enabled") -ne "true") {
        throw "The final $lastCompletedWorkload editor UI did not retain the exact clean Undo checkpoint."
    }

    $environmentRows.Add((Get-PhysicalCheckpoint -Name "after_samples"))

    $frameRows | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath $framesPath
    $sampleSummaries | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath $summariesPath
    if ($physicalPresentEnabled) {
        Stop-PhysicalPresentTrace -State $physicalTraceState
        $physicalAnalysis =
            & $physicalPresentAnalyzer `
                -TracePath $physicalTraceState.LocalTrace `
                -FramesPath $framesPath `
                -TraceProcessorPath $resolvedPhysicalTraceProcessor `
                -OutputDirectory $resolvedOutput `
                -ExpectedSampleCount ($sampleCount * $familyResults.Count)
    }

    $logcat = @(Invoke-TargetAdb -AdbArguments @("logcat", "-d", "-v", "threadtime"))
    $logcatPath = Join-Path $resolvedOutput "logcat.txt"
    [System.IO.File]::WriteAllLines($logcatPath, $logcat)
    $fatalCount = @($logcat | Select-String -Pattern "FATAL EXCEPTION|ANR in|Fatal signal|Process .* has died").Count
    if ($fatalCount -gt 0) {
        throw "Fatal, ANR, signal, or process-death evidence was observed after collection."
    }

    $validFrames = @($frameRows | Where-Object { $_.flags -eq 0 })
    $totalRendered = ($sampleSummaries.total_frames_rendered | Measure-Object -Sum).Sum
    $totalJanky = ($sampleSummaries.janky_frames | Measure-Object -Sum).Sum
    $totalDeadlineMissed = ($sampleSummaries.deadline_missed_frames | Measure-Object -Sum).Sum
    if (
        $sampleSummaries.Count -ne ($sampleCount * $familyResults.Count) -or
        $frameRows.Count -lt ($sampleCount * $familyResults.Count * 2) -or
        $totalRendered -ne $frameRows.Count -or
        $validFrames.Count -ne $frameRows.Count -or
        @($frameRows.frame_timeline_vsync_id | Sort-Object -Unique).Count -ne $frameRows.Count -or
        @($sampleSummaries | Where-Object { $_.preview_frame_count -lt 1 -or $_.commit_frame_count -lt 1 }).Count -gt 0
    ) {
        throw "Aggregate frame counts must retain every preview and committed-result frame."
    }
    $summaryCursor = 0
    foreach ($spec in @($slotWorkloadCatalog | Select-Object -First $familyResults.Count)) {
        foreach ($sampleIndex in 1..$sampleCount) {
            $expectedOrdinal = $summaryCursor + 1
            $summary = $sampleSummaries[$summaryCursor]
            $operationFrames = @($frameRows | Where-Object { [int]$_.operation_ordinal -eq $expectedOrdinal })
            $previewRows = @($operationFrames | Where-Object { $_.phase -ceq "preview" })
            $commitRows = @($operationFrames | Where-Object { $_.phase -ceq "commit" })
            if (
                $summary.workload -cne $spec.workload -or
                [int]$summary.sample_index -ne $sampleIndex -or
                [int]$summary.operation_ordinal -ne $expectedOrdinal -or
                @($operationFrames | Where-Object {
                        $_.workload -cne $spec.workload -or
                        [int]$_.sample_index -ne $sampleIndex -or
                        $_.operation -cne "$($spec.workload)#$sampleIndex"
                    }).Count -gt 0 -or
                @(Compare-Object @(1..$previewRows.Count) @($previewRows.row_index) -SyncWindow 0).Count -ne 0 -or
                @(Compare-Object @(1..$commitRows.Count) @($commitRows.row_index) -SyncWindow 0).Count -ne 0
            ) {
                throw "Frame workload, operation, phase, or row ordering drifted at ordinal $expectedOrdinal."
            }
            $summaryCursor += 1
        }
    }
    $completeRun =
        $familyResults.Count -eq $slotWorkloadCatalog.Count -and
        $fatalCount -eq 0
    $grossRegression = @($familyResults | Where-Object { $_.GrossRegression }).Count -gt 0
    $acceptanceLane =
        if ($physicalPresentEnabled) { "attribution" } elseif ($isDecisionLane) { "decision" } else { "diagnostic" }
    $status =
        if ($null -ne $earlyStopStatus) {
            $earlyStopStatus
        }
        elseif ($physicalPresentEnabled) {
            $physicalAnalysis.Status
        }
        elseif (-not $isDecisionLane -and $grossRegression) {
            "gross-regression"
        }
        else {
            "inconclusive"
        }
    $metadata = [System.Collections.Generic.List[string]]::new()
    @(
        "schema=$frameSchema",
        "experiment_schema=$experimentSchema",
        "experiment_id=$ExperimentId",
        "status=$status",
        "acceptance_lane=$acceptanceLane",
        "complete_run=$(if ($completeRun) { 'true' } else { 'false' })",
        "threshold_status=$(if ($null -ne $earlyStopStatus) { $earlyStopStatus } elseif (-not $isDecisionLane -and $grossRegression) { 'gross-regression' } else { 'inconclusive' })",
        "variant=$Variant",
        "run_kind=$RunKind",
        "candidate_role=$CandidateRole",
        "comparison_sequence_index=$ComparisonSequenceIndex",
        "comparison_order=$($comparisonOrder -join '|')",
        "workload_order=$($slotWorkloadOrder -join '|')",
        "input_injection=$inputInjection",
        "source_commit=$SourceCommit",
        "production_commit=$expectedProductionCommit",
        "production_tree_sha256=$(if ($CandidateRole -eq 'baseline') { $BaselineProductionTreeSha256 } else { $CandidateProductionTreeSha256 })",
        "physical_profile_id=$physicalProfileId",
        "device_evidence_class=physical_device",
        "device_manufacturer=$($deviceIdentity.manufacturer)",
        "device_model=$($deviceIdentity.model)",
        "device_product=$($deviceIdentity.product)",
        "device_name=$($deviceIdentity.device)",
        "device_api_level=$($deviceIdentity.api_level)",
        "device_build_fingerprint=$($deviceIdentity.build_fingerprint)",
        "device_security_patch=$($deviceIdentity.security_patch)",
        "apk_embedded_source_commit=$($artifactIdentity.embedded_source_commit)",
        "apk_bytes=$($artifactIdentity.apk_byte_count)",
        "apk_sha256=$($artifactIdentity.apk_sha256)",
        "installed_apk_path=$($installedApkIdentity.installed_apk_path)",
        "installed_apk_sha256=$($installedApkIdentity.installed_apk_sha256)",
        "profile_acceptance_reader_sha256=$profileAcceptanceReaderSha256",
        "profile_generation_source_commit=$(if ($CandidateRole -eq 'baseline') { $BaselineProfileGenerationSourceCommit } else { $CandidateProfileGenerationSourceCommit })",
        "profile_generation_app_apk_sha256=$(if ($CandidateRole -eq 'baseline') { $BaselineProfileGenerationAppApkSha256 } else { $CandidateProfileGenerationAppApkSha256 })",
        "profile_generation_test_apk_sha256=$(if ($CandidateRole -eq 'baseline') { $BaselineProfileGenerationTestApkSha256 } else { $CandidateProfileGenerationTestApkSha256 })",
        "profile_acceptance_manifest_sha256=$(if ($CandidateRole -eq 'baseline') { $BaselineProfileAcceptanceManifestSha256 } else { $CandidateProfileAcceptanceManifestSha256 })",
        "profile_pair_manifest_sha256=$(if ($CandidateRole -eq 'baseline') { $BaselineProfilePairManifestSha256 } else { $CandidateProfilePairManifestSha256 })",
        "canonical_profile_sha256=$(if ($CandidateRole -eq 'baseline') { $BaselineCanonicalProfileSha256 } else { $CandidateCanonicalProfileSha256 })",
        "packaged_prof_sha256=$($artifactIdentity.packaged_prof_sha256)",
        "packaged_profm_sha256=$($artifactIdentity.packaged_profm_sha256)",
        "compile_mode=$CompilationMode",
        "packaged_profile_install=$profileInstallResult",
        "warmups_per_workload=$warmupCount",
        "samples_per_workload=$sampleCount",
        "measured_operation_count=$($sampleSummaries.Count)",
        "measured_canvas16_tap=$($script:measuredWorkloadCounts.canvas16_tap)",
        "measured_canvas256_repeated_diagonal=$($script:measuredWorkloadCounts.canvas256_repeated_diagonal)",
        "percentile_method=nearest-rank; diagnostic-p95-rank=10; decision-p95-rank=48",
        "environment_checkpoint_count=$($environmentRows.Count)",
        "raw_frame_rows=$($frameRows.Count)",
        "valid_frame_rows=$($validFrames.Count)",
        "aggregate_frames_rendered=$totalRendered",
        "aggregate_janky_frames=$totalJanky",
        "aggregate_deadline_missed_frames=$totalDeadlineMissed",
        "diagnostic_gross_frame_overrun_boundary_ms=33.34-exclusive",
        "diagnostic_gross_input_to_committed_boundary_ms=100.0-exclusive",
        "fatal_anr_matches=$fatalCount",
        "geometry_id=$geometryId",
        "canvas16_tap_surface_bounds=$($expectedSurfaceBoundsByWorkload.canvas16_tap)",
        "canvas256_repeated_diagonal_surface_bounds=$($expectedSurfaceBoundsByWorkload.canvas256_repeated_diagonal)",
        "display_present_time_available=$(@($validFrames | Where-Object { $_.display_present_time_nanos -gt 0 }).Count -gt 0)",
        "boundary=DOWN preview plus UP commit; every phase frame retained; acceptance latency starts at earliest UP HandleInputStart and completes at latest UP-associated FrameCompleted after committed UI verification; DOWN-to-commit including the intentional preview dwell is diagnostic only"
    ) | ForEach-Object { $metadata.Add($_) }
    if ($slotWorkloadOrder -ccontains $windowDiagnosticWorkload) {
        $metadata.Add("measured_$windowDiagnosticWorkload=$($script:measuredWorkloadCounts[$windowDiagnosticWorkload])")
    }
    foreach ($family in $familyResults) {
        $prefix = $family.Workload
        if ($windowRecords.Contains($prefix)) {
            $metadata.Add("${prefix}_window_scale=$($windowRecords[$prefix].Scale)")
            $metadata.Add("${prefix}_window_bounds=$($windowRecords[$prefix].Bounds)")
        }
        else {
            $metadata.Add("${prefix}_window_scale=none")
        }
        $metadata.Add("${prefix}_frame_count=$($family.FrameCount)")
        $metadata.Add("${prefix}_frame_overrun_p95_ms=$('{0:F6}' -f $family.OverrunP95)")
        $metadata.Add("${prefix}_frame_overrun_p99_ms=$('{0:F6}' -f $family.OverrunP99)")
        $metadata.Add("${prefix}_input_to_committed_result_p95_ms=$('{0:F6}' -f $family.InputP95)")
        $metadata.Add("${prefix}_maximum_frame_overrun_ms=$('{0:F6}' -f $family.MaximumFrameOverrun)")
        $metadata.Add("${prefix}_maximum_input_to_committed_result_ms=$('{0:F6}' -f $family.MaximumInputToCommitted)")
        $metadata.Add("${prefix}_threshold_status=measured")
        $metadata.Add("${prefix}_diagnostic_gross_regression=$(if ($family.GrossRegression) { 'true' } else { 'false' })")
    }
    if ($physicalPresentEnabled) {
        $metadata.Add("base_frame_row_schema=nene-pixel-m2-actual-app-frame-v5-fields")
        $metadata.Add("physical_present_schema=$($physicalAnalysis.Schema)")
        $metadata.Add("physical_present_status=$($physicalAnalysis.Status)")
        $metadata.Add("physical_present_trace_bytes=$($physicalAnalysis.TraceBytes)")
        $metadata.Add("physical_present_trace_sha256=$($physicalAnalysis.TraceSha256)")
        $metadata.Add("physical_present_correlated_frame_count=$($physicalAnalysis.CorrelatedFrameCount)")
        $metadata.Add("physical_present_minimum_frames_per_sample=$($physicalAnalysis.MinimumFramesPerSample)")
        $metadata.Add("physical_present_maximum_frames_per_sample=$($physicalAnalysis.MaximumFramesPerSample)")
        $metadata.Add("physical_input_to_present_p50_ms=$('{0:F6}' -f $physicalAnalysis.PhysicalP50Milliseconds)")
        $metadata.Add("physical_input_to_present_p95_ms=$('{0:F6}' -f $physicalAnalysis.PhysicalP95Milliseconds)")
        $metadata.Add("physical_input_to_present_p99_ms=$('{0:F6}' -f $physicalAnalysis.PhysicalP99Milliseconds)")
        $metadata.Add("physical_present_attribution_counts=$($physicalAnalysis.ClassCounts)")
        $metadata.Add("limitation=strict physical-present correlation retained for this ten-sample attribution population; does not replace the fifty-sample decision lane")
    }
    else {
        $limitation = "app-issued gfxinfo framestats rows only; no strict SurfaceFlinger physical-present correlation"
        if ($RunKind -eq "diagnostic") {
            $limitation += "; diagnostic results are never acceptance PASS"
        }
        $metadata.Add("limitation=$limitation")
    }
    [System.IO.File]::WriteAllLines((Join-Path $resolvedOutput "metadata.txt"), $metadata)
    Write-RunState -Status "completed" -Verdict $status -CompleteRun $completeRun

    $successfulMetadata = @($metadata)
}
catch {
    $sourceError = $_
    Save-LatestUiFailureSnapshots `
        -RawDirectory (Join-Path $resolvedOutput "raw") `
        -SourceError $sourceError
    $currentState = Get-RunState -Directory $resolvedOutput
    if ($null -eq $currentState -or $currentState.status -ne "completed") {
        $measuredOperationCount = Get-MeasuredOperationCount
        $invalidStatus = if ($measuredOperationCount -eq 0) { "invalid-before-samples" } else { "invalid-after-samples" }
        Write-RunState -Status $invalidStatus -Verdict "invalid"
    }
    throw $sourceError
}
finally {
    if ($null -ne $physicalTraceState -and $physicalTraceState.Active) {
        try {
            Stop-PhysicalPresentTrace -State $physicalTraceState
        }
        catch {
            Write-Warning "Unable to finalize the active physical-present trace through its declared trigger-only stop path."
        }
    }
    if ($script:rotationPinAttempted -and $null -ne $originalRotationState) {
        try {
            Restore-OriginalRotation -Original $originalRotationState
        }
        catch {
            $rotationRestoreError = $_.Exception.Message
            try {
                [System.IO.File]::WriteAllLines(
                    (Join-Path $resolvedOutput "rotation-restore-failure.txt"),
                    @(
                        "restore_verified=false",
                        "source_error=$($rotationRestoreError -replace '[\r\n]+', ' ')"
                    ),
                    [System.Text.UTF8Encoding]::new($false)
                )
            }
            catch {
                # Cleanup evidence must not replace the source measurement result.
            }
        }
    }
    if ($environmentRows.Count -gt 0) {
        $environmentRows | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath $environmentPath
    }
    # Cleanup failures are recorded, never allowed to replace the source measurement result.
    try {
        Restore-OriginalStayAwake -Original $originalStayAwake
    }
    catch {
        $script:collectionCleanupErrors.Add("stay_awake_restore=$($_.Exception.Message -replace '[\r\n]+', ' ')")
    }
    try {
        Invoke-TargetAdb -AdbArguments @(
            "shell",
            "rm",
            "-f",
            "$remotePrefix-before.xml",
            "$remotePrefix-after.xml",
            "$remotePrefix-after.png",
            "$remotePrefix-checkpoint.xml",
            "$remotePrefix-current.xml"
        ) | Out-Null
    }
    catch {
        $script:collectionCleanupErrors.Add("remote_cleanup=$($_.Exception.Message -replace '[\r\n]+', ' ')")
    }
    if ($null -ne $physicalTraceState -and -not $physicalTraceState.Active) {
        try {
            Invoke-TargetAdb -AdbArguments @(
                "shell",
                "rm",
                "-f",
                $physicalTraceState.RemoteConfig,
                $physicalTraceState.RemoteTrace
            ) | Out-Null
        }
        catch {
            $script:collectionCleanupErrors.Add("trace_cleanup=$($_.Exception.Message -replace '[\r\n]+', ' ')")
        }
    }
    if ($script:collectionCleanupErrors.Count -gt 0) {
        try {
            [System.IO.File]::WriteAllLines(
                (Join-Path $resolvedOutput "cleanup-failure.txt"),
                @($script:collectionCleanupErrors),
                [System.Text.UTF8Encoding]::new($false)
            )
        }
        catch {
            # Cleanup evidence must not replace the source measurement result.
        }
    }
}

if ($null -ne $rotationRestoreError) {
    Write-RunState -Status "invalid-after-samples" -Verdict "invalid"
    throw "The frame result is invalid because rotation restoration was not verified: $rotationRestoreError"
}
$successfulMetadata

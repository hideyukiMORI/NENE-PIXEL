[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'measurements/p4-indexed-frame-analysis.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-device-lanes.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1')

$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ("nene-p4-frame-analysis-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporaryRoot | Out-Null

$baselineCommit = '1' * 40
$candidateCommit = '2' * 40
$baselineProduction = '2dd4e01e3bbe88967237cde4e28412d2962fd590'
$candidateProduction = '3' * 40
$baselineApk = 'a' * 64
$candidateApk = 'b' * 64
$fixtureExperimentId = 'offline-frame-analysis'
$canvas16Bounds = '[688,615][1232,1159]'
$canvas256Bounds = '[688,615][1232,1159]'
$workloadOrder = @('canvas16_tap', 'canvas256_repeated_diagonal')
$applicationPackage = 'io.github.hideyukimori.nenepixel'
$applicationTestPackage = 'io.github.hideyukimori.nenepixel.test'
$publicationTestPackage = 'io.github.hideyukimori.nenepixel.adapters.persistence.test'
$junitRunner = 'androidx.test.runner.AndroidJUnitRunner'
$profileId = 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36'

function Assert-P4FrameRejects {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Action,
        [Parameter(Mandatory = $true)][string]$Case
    )
    try { & $Action | Out-Null }
    catch { return }
    throw "The P4 frame validator accepted an invalid case: $Case"
}

function New-P4FrameSlotFixture {
    <#
        Writes a synthetic frame slot exactly as measure-m2-frame.ps1 publishes one. Every published
        metric is computed with the analyzer's own nearest-rank helpers, so a fixture is self-consistent
        unless a negative case deliberately perturbs it.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][ValidateSet('baseline', 'candidate')][string]$Role,
        [Parameter(Mandatory = $true)][ValidateSet('diagnostic', 'decision')][string]$Runner,
        [Parameter(Mandatory = $true)][int]$SequenceIndex,
        [Parameter(Mandatory = $true)][string[]]$Families,
        [double]$CommitOverrunMs = -1.0,
        [double]$PreviewOverrunMs = -1.0,
        [double]$InputMs = 20.0,
        [int]$ElevatedCommitCount = 0,
        [double]$ElevatedOverrunMs = 5.0,
        [string]$StatusOverride = ''
    )

    $samplesPerFamily = if ($Runner -eq 'diagnostic') { 10 } else { 50 }
    $buildCommit = if ($Role -eq 'baseline') { $baselineCommit } else { $candidateCommit }
    $productionCommit = if ($Role -eq 'baseline') { $baselineProduction } else { $candidateProduction }
    $apkSha256 = if ($Role -eq 'baseline') { $baselineApk } else { $candidateApk }
    $root = Join-Path $temporaryRoot $Name
    $slotDirectory = Join-Path $root ('slot-{0:D2}-{1}-{2}-attempt-1' -f $SequenceIndex, $Runner, $Role)
    New-Item -ItemType Directory -Path (Join-Path $slotDirectory 'raw') -Force | Out-Null

    $frames = [Collections.Generic.List[object]]::new()
    $samples = [Collections.Generic.List[object]]::new()
    $ordinal = 0
    $vsync = 0
    $elevatedRemaining = $ElevatedCommitCount
    foreach ($family in $Families) {
        foreach ($sampleIndex in 1..$samplesPerFamily) {
            $ordinal += 1
            $base = [long]$ordinal * 1000000000L
            $commitOverrun = $CommitOverrunMs
            if ($elevatedRemaining -gt 0) {
                $commitOverrun = $ElevatedOverrunMs
                $elevatedRemaining -= 1
            }
            foreach ($phase in @('preview', 'commit')) {
                $vsync += 1
                $inputStart = if ($phase -eq 'preview') { $base } else { $base + 100000000L }
                $completed = if ($phase -eq 'preview') { $base + 10000000L } else { $inputStart + [long]($InputMs * 1000000.0) }
                $overrun = if ($phase -eq 'preview') { $PreviewOverrunMs } else { $commitOverrun }
                $frames.Add(
                    [pscustomobject]@{
                        variant = 'release-like'
                        source_commit = $buildCommit
                        production_commit = $productionCommit
                        workload = $family
                        operation = "$family#$sampleIndex"
                        operation_ordinal = $ordinal
                        sample_index = $sampleIndex
                        phase = $phase
                        event_count = 1
                        row_index = 1
                        flags = 0
                        frame_timeline_vsync_id = $vsync
                        intended_vsync_nanos = $base
                        frame_start_nanos = $base + 1000000L
                        handle_input_start_nanos = $inputStart
                        draw_start_nanos = $base + 5000000L
                        frame_deadline_nanos = $completed - [long]($overrun * 1000000.0)
                        frame_completed_nanos = $completed
                        display_present_time_nanos = $completed + 1000000L
                        frame_duration_cpu_ms = 9.0
                        app_frame_total_ms = 10.0
                        frame_overrun_ms = $overrun
                        input_start_to_completion_ms = ($completed - $inputStart) / 1000000.0
                    }
                )
            }
            $samples.Add(
                [pscustomobject]@{
                    variant = 'release-like'
                    source_commit = $buildCommit
                    production_commit = $productionCommit
                    workload = $family
                    operation = "$family#$sampleIndex"
                    operation_ordinal = $ordinal
                    sample_index = $sampleIndex
                    motion_event_count = 2
                    preview_event_count = 1
                    commit_event_count = 1
                    raw_position_count = 1
                    effective_change_count = 1
                    preview_frame_count = 1
                    preview_raw_frame_count = 1
                    preview_valid_frame_count = 1
                    commit_frame_count = 1
                    commit_raw_frame_count = 1
                    commit_valid_frame_count = 1
                    total_frames_rendered = 2
                    janky_frames = 0
                    deadline_missed_frames = 0
                    raw_row_count = 2
                    valid_row_count = 2
                    committed_ui_verified = $true
                    preview_input_start_nanos = $base
                    commit_input_start_nanos = $base + 100000000L
                    committed_result_completion_nanos = $base + 100000000L + [long]($InputMs * 1000000.0)
                    input_to_committed_result_ms = $InputMs
                    down_to_committed_result_ms = $InputMs + 100.0
                }
            )
        }
    }
    $frames | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath (Join-Path $slotDirectory 'frames.csv')
    $samples | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath (Join-Path $slotDirectory 'samples.csv')

    $familyMetadata = [Collections.Generic.List[string]]::new()
    $allPassed = $Families.Count -eq $workloadOrder.Count
    $anyGross = $false
    foreach ($family in $Families) {
        $familyFrames = @($frames | Where-Object { $_.workload -ceq $family })
        $familySamples = @($samples | Where-Object { $_.workload -ceq $family })
        $overruns = [double[]]@($familyFrames | ForEach-Object { [double]$_.frame_overrun_ms })
        $inputs = [double[]]@($familySamples | ForEach-Object { [double]$_.input_to_committed_result_ms })
        $p95 = Get-P4FrameNearestRank -Values $overruns -Percentile 0.95
        $p99 = Get-P4FrameNearestRank -Values $overruns -Percentile 0.99
        $inputP95 = Get-P4FrameNearestRank -Values $inputs -Percentile 0.95
        $maximumOverrun = ($overruns | Measure-Object -Maximum).Maximum
        $maximumInput = ($inputs | Measure-Object -Maximum).Maximum
        $passed = $p95 -le 0.0 -and $p99 -le 16.67 -and $inputP95 -le 33.33
        $gross = $maximumOverrun -gt 33.34 -or $maximumInput -gt 100.0
        if (-not $passed) { $allPassed = $false }
        if ($gross) { $anyGross = $true }
        $familyMetadata.Add("${family}_frame_count=$($familyFrames.Count)")
        $familyMetadata.Add("${family}_frame_overrun_p95_ms=$(Format-P4FrameMetric $p95)")
        $familyMetadata.Add("${family}_frame_overrun_p99_ms=$(Format-P4FrameMetric $p99)")
        $familyMetadata.Add("${family}_input_to_committed_result_p95_ms=$(Format-P4FrameMetric $inputP95)")
        $familyMetadata.Add("${family}_maximum_frame_overrun_ms=$(Format-P4FrameMetric $maximumOverrun)")
        $familyMetadata.Add("${family}_maximum_input_to_committed_result_ms=$(Format-P4FrameMetric $maximumInput)")
        $familyMetadata.Add("${family}_threshold_status=$(if ($passed) { 'pass' } else { 'fail' })")
        $familyMetadata.Add("${family}_diagnostic_gross_regression=$(if ($gross) { 'true' } else { 'false' })")
    }
    $status =
        if (-not [string]::IsNullOrEmpty($StatusOverride)) { $StatusOverride }
        elseif ($Runner -eq 'decision') { if ($allPassed) { 'pass' } else { 'fail' } }
        elseif ($anyGross) { 'gross-regression' }
        else { 'inconclusive' }

    $metadata = [Collections.Generic.List[string]]::new()
    @(
        "schema=nene-pixel-p4-indexed-actual-app-frame-v8",
        "experiment_schema=nene-pixel-p4-indexed-frame-experiment-v4",
        "experiment_id=$fixtureExperimentId",
        "status=$status",
        "acceptance_lane=$Runner",
        "threshold_status=$status",
        "variant=release-like",
        "run_kind=$Runner",
        "candidate_role=$Role",
        "comparison_sequence_index=$SequenceIndex",
        "comparison_order=diagnostic:baseline|diagnostic:candidate|decision:candidate|decision:baseline",
        "workload_order=$($workloadOrder -join '|')",
        "input_injection=cmd-input-service-direct",
        "source_commit=$buildCommit",
        "production_commit=$productionCommit",
        "physical_profile_id=$profileId",
        "device_evidence_class=physical_device",
        "apk_embedded_source_commit=$buildCommit",
        "apk_sha256=$apkSha256",
        "compile_mode=speed-profile",
        "warmups_per_workload=5",
        "samples_per_workload=$samplesPerFamily",
        "measured_operation_count=$($samples.Count)",
        "measured_canvas16_tap=$(@($samples | Where-Object { $_.workload -ceq 'canvas16_tap' }).Count)",
        "measured_canvas256_repeated_diagonal=$(@($samples | Where-Object { $_.workload -ceq 'canvas256_repeated_diagonal' }).Count)",
        "percentile_method=nearest-rank; diagnostic-p95-rank=10; decision-p95-rank=48",
        "raw_frame_rows=$($frames.Count)",
        "valid_frame_rows=$($frames.Count)",
        "aggregate_frames_rendered=$($frames.Count)",
        "fatal_anr_matches=0",
        "geometry_id=initial-fit-centered-v1",
        "canvas16_tap_surface_bounds=$canvas16Bounds",
        "canvas256_repeated_diagonal_surface_bounds=$canvas256Bounds"
    ) | ForEach-Object { $metadata.Add($_) }
    $familyMetadata | ForEach-Object { $metadata.Add($_) }
    [IO.File]::WriteAllLines((Join-Path $slotDirectory 'metadata.txt'), $metadata)

    $measuredCounts = [ordered]@{}
    foreach ($family in $workloadOrder) {
        $measuredCounts[$family] = @($samples | Where-Object { $_.workload -ceq $family }).Count
    }
    [ordered]@{
        schema = 'nene-pixel-p4-indexed-frame-experiment-v4'
        experiment_id = $fixtureExperimentId
        comparison_sequence_index = $SequenceIndex
        attempt = 1
        status = 'completed'
        verdict = $status
        workload_order = $workloadOrder
        measured_workload_counts = $measuredCounts
        measured_operation_count = $samples.Count
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $slotDirectory 'run-state.json') -Encoding utf8NoBOM
    return $slotDirectory
}

function Invoke-P4FrameFixtureAnalysis {
    param(
        [Parameter(Mandatory = $true)][string]$SlotDirectory,
        [Parameter(Mandatory = $true)][string]$Role,
        [Parameter(Mandatory = $true)][string]$Runner,
        [Parameter(Mandatory = $true)][int]$SequenceIndex,
        [string]$BuildCommit = '',
        [string]$ApkSha256 = '',
        [string]$ExperimentId = ''
    )
    if ([string]::IsNullOrEmpty($BuildCommit)) {
        $BuildCommit = if ($Role -eq 'baseline') { $baselineCommit } else { $candidateCommit }
    }
    if ([string]::IsNullOrEmpty($ApkSha256)) {
        $ApkSha256 = if ($Role -eq 'baseline') { $baselineApk } else { $candidateApk }
    }
    if ([string]::IsNullOrEmpty($ExperimentId)) { $ExperimentId = $fixtureExperimentId }
    return Test-P4FrameCapture -SlotDirectory $SlotDirectory -Role $Role -Runner $Runner `
        -SequenceIndex $SequenceIndex -BuildCommit $BuildCommit -ExpectedApkSha256 $ApkSha256 `
        -ExpectedBounds ([ordered]@{ canvas16_tap = $canvas16Bounds; canvas256_repeated_diagonal = $canvas256Bounds }) `
        -ExperimentId $ExperimentId
}

function Set-P4FrameMetadataLine {
    param(
        [Parameter(Mandatory = $true)][string]$SlotDirectory,
        [Parameter(Mandatory = $true)][string]$Key,
        [Parameter(Mandatory = $true)][string]$Value
    )
    $path = Join-Path $SlotDirectory 'metadata.txt'
    $lines = @(Get-Content -LiteralPath $path)
    $replaced = 0
    $updated = foreach ($line in $lines) {
        if ($line.StartsWith("$Key=", [StringComparison]::Ordinal)) {
            $replaced += 1
            "$Key=$Value"
        } else { $line }
    }
    if ($replaced -ne 1) { throw "Fixture metadata key '$Key' is not uniquely present." }
    [IO.File]::WriteAllLines($path, @($updated))
}

function New-P4LaneManifest {
    $roles = [ordered]@{}
    foreach ($role in @('baseline', 'candidate')) {
        $commit = if ($role -eq 'baseline') { $baselineCommit } else { $candidateCommit }
        $apk = if ($role -eq 'baseline') { $baselineApk } else { $candidateApk }
        $roles[$role] = [ordered]@{
            worktree = Join-Path $temporaryRoot "worktree-$role"
            production_commit = if ($role -eq 'baseline') { $baselineProduction } else { $candidateProduction }
            build_commit = $commit
            production_tree_sha256 = if ($role -eq 'baseline') { '5' * 64 } else { '6' * 64 }
            artifacts = [ordered]@{
                app_debug = [ordered]@{
                    path = "C:/fixture/$role-app-debug.apk"; sha256 = ('c' * 64); variant = 'debug'
                    target_package = $applicationPackage; test_package = 'none'
                }
                test_debug = [ordered]@{
                    path = "C:/fixture/$role-test-debug.apk"; sha256 = ('d' * 64); variant = 'debugAndroidTest'
                    target_package = $applicationPackage; test_package = $applicationTestPackage
                }
                app_release_like = [ordered]@{
                    path = "C:/fixture/$role-app-release-like.apk"; sha256 = $apk; variant = 'benchmarkRelease'
                    target_package = $applicationPackage; test_package = 'none'
                }
                publication_test = [ordered]@{
                    path = "C:/fixture/$role-publication-test.apk"; sha256 = ('e' * 64); variant = 'debugAndroidTest'
                    target_package = $publicationTestPackage; test_package = $publicationTestPackage
                }
            }
            profile = [ordered]@{
                generation_commit = if ($role -eq 'baseline') { '7' * 40 } else { '8' * 40 }
                generation_app_sha256 = '1' * 64
                generation_test_sha256 = '2' * 64
                acceptance = [ordered]@{ path = "C:/fixture/$role-acceptance.json"; sha256 = '3' * 64 }
                pair = [ordered]@{ path = "C:/fixture/$role-pair.json"; sha256 = '4' * 64 }
                canonical = [ordered]@{ path = "C:/fixture/$role-canonical.txt"; sha256 = '9' * 64 }
                packaged_prof_sha256 = if ($role -eq 'baseline') { '0' * 64 } else { 'f' * 64 }
                packaged_profm_sha256 = if ($role -eq 'baseline') { 'a' * 64 } else { 'b' * 64 }
            }
        }
    }
    return [ordered]@{
        experiment_id = $fixtureExperimentId
        output_directory = Join-Path $temporaryRoot 'acceptance'
        roles = $roles
        device = [ordered]@{
            serial = 'FIXTURESERIAL'
            baseline_canvas16_bounds = $canvas16Bounds
            baseline_canvas256_bounds = $canvas256Bounds
            candidate_canvas16_bounds = $canvas16Bounds
            candidate_canvas256_bounds = $canvas256Bounds
        }
        frame_experiment = [ordered]@{
            directory = Join-Path $temporaryRoot 'frame-experiment'
            hypothesis = 'fixed candidate hypothesis'
            expected_affected_cost = 'fixed expected affected cost'
            correctness_risk = 'fixed correctness risk'
            stop_conditions = 'fixed stopping conditions'
        }
    }
}

function Get-P4CatalogSlot {
    param([Parameter(Mandatory = $true)][string]$SlotId)
    $matched = @(Get-P4SlotCatalog | Where-Object { $_.id -ceq $SlotId })
    if ($matched.Count -ne 1) { throw "Unknown fixture slot '$SlotId'." }
    return $matched[0]
}

try {
    # --- S6 frame analyzer -------------------------------------------------------------------------
    $diagnosticPass = New-P4FrameSlotFixture -Name 'diagnostic-pass' -Role 'baseline' -Runner 'diagnostic' `
        -SequenceIndex 1 -Families $workloadOrder
    $diagnosticResult = Invoke-P4FrameFixtureAnalysis -SlotDirectory $diagnosticPass -Role 'baseline' `
        -Runner 'diagnostic' -SequenceIndex 1
    if (
        $diagnosticResult.verdict -cne 'inconclusive' -or
        $diagnosticResult.role -cne 'baseline' -or
        $diagnosticResult.schema -cne 'nene-pixel-p4-indexed-actual-app-frame-v8' -or
        [int]$diagnosticResult.samples_per_workload -ne 10 -or
        [int]$diagnosticResult.raw_frame_rows -ne 40 -or
        $diagnosticResult.families.Count -ne 2 -or
        $diagnosticResult.families.canvas16_tap.threshold_status -cne 'pass' -or
        @($diagnosticResult.frame_files).Count -lt 4
    ) {
        throw 'The diagnostic pass fixture did not produce the expected inconclusive frame analysis.'
    }
    foreach ($required in @('run-state.json', 'metadata.txt', 'frames.csv', 'samples.csv')) {
        if ($required -cnotin @($diagnosticResult.frame_files | ForEach-Object { $_.relative_path })) {
            throw "The frame file inventory omitted $required."
        }
    }

    $decisionPass = New-P4FrameSlotFixture -Name 'decision-pass' -Role 'candidate' -Runner 'decision' `
        -SequenceIndex 3 -Families $workloadOrder
    $decisionResult = Invoke-P4FrameFixtureAnalysis -SlotDirectory $decisionPass -Role 'candidate' `
        -Runner 'decision' -SequenceIndex 3
    if (
        $decisionResult.verdict -cne 'pass' -or
        [int]$decisionResult.samples_per_workload -ne 50 -or
        [int]$decisionResult.measured_operation_count -ne 100
    ) {
        throw 'The decision pass fixture did not produce the expected passing frame analysis.'
    }

    $decisionFail = New-P4FrameSlotFixture -Name 'decision-fail-p95' -Role 'candidate' -Runner 'decision' `
        -SequenceIndex 3 -Families $workloadOrder -ElevatedCommitCount 20
    $decisionFailResult = Invoke-P4FrameFixtureAnalysis -SlotDirectory $decisionFail -Role 'candidate' `
        -Runner 'decision' -SequenceIndex 3
    if (
        $decisionFailResult.verdict -cne 'PERFORMANCE_FAIL' -or
        $decisionFailResult.families.canvas16_tap.threshold_status -cne 'fail' -or
        $decisionFailResult.families.canvas16_tap.gross_regression
    ) {
        throw 'A decision p95 overrun miss did not become PERFORMANCE_FAIL.'
    }

    $diagnosticFailP95 = New-P4FrameSlotFixture -Name 'diagnostic-fail-p95' -Role 'baseline' `
        -Runner 'diagnostic' -SequenceIndex 1 -Families $workloadOrder -ElevatedCommitCount 5
    $diagnosticFailP95Result = Invoke-P4FrameFixtureAnalysis -SlotDirectory $diagnosticFailP95 `
        -Role 'baseline' -Runner 'diagnostic' -SequenceIndex 1
    if ($diagnosticFailP95Result.verdict -cne 'inconclusive') {
        throw 'A diagnostic slot without a gross regression must stay inconclusive.'
    }

    $grossDiagnostic = New-P4FrameSlotFixture -Name 'diagnostic-gross' -Role 'baseline' -Runner 'diagnostic' `
        -SequenceIndex 1 -Families @('canvas16_tap') -ElevatedCommitCount 1 -ElevatedOverrunMs 40.0
    $grossResult = Invoke-P4FrameFixtureAnalysis -SlotDirectory $grossDiagnostic -Role 'baseline' `
        -Runner 'diagnostic' -SequenceIndex 1
    if (
        $grossResult.verdict -cne 'PERFORMANCE_FAIL' -or
        $grossResult.complete_run -or
        -not $grossResult.families.canvas16_tap.gross_regression
    ) {
        throw 'A diagnostic gross regression did not stop the experiment with PERFORMANCE_FAIL.'
    }

    $wrongRole = New-P4FrameSlotFixture -Name 'wrong-role' -Role 'candidate' -Runner 'diagnostic' `
        -SequenceIndex 2 -Families $workloadOrder
    Set-P4FrameMetadataLine -SlotDirectory $wrongRole -Key 'candidate_role' -Value 'baseline'
    Assert-P4FrameRejects {
        Invoke-P4FrameFixtureAnalysis -SlotDirectory $wrongRole -Role 'candidate' -Runner 'diagnostic' -SequenceIndex 2
    } 'metadata role drift'

    $foreignDirectory = New-P4FrameSlotFixture -Name 'foreign-slot' -Role 'baseline' -Runner 'diagnostic' `
        -SequenceIndex 1 -Families $workloadOrder
    Assert-P4FrameRejects {
        Invoke-P4FrameFixtureAnalysis -SlotDirectory $foreignDirectory -Role 'candidate' -Runner 'diagnostic' -SequenceIndex 1
    } 'foreign slot directory for the requested role'

    $missingFamily = New-P4FrameSlotFixture -Name 'missing-family' -Role 'baseline' -Runner 'diagnostic' `
        -SequenceIndex 1 -Families @('canvas16_tap')
    Assert-P4FrameRejects {
        Invoke-P4FrameFixtureAnalysis -SlotDirectory $missingFamily -Role 'baseline' -Runner 'diagnostic' -SequenceIndex 1
    } 'missing family without an early stop'

    $wrongOrderFamily = New-P4FrameSlotFixture -Name 'suffix-family' -Role 'baseline' -Runner 'diagnostic' `
        -SequenceIndex 1 -Families @('canvas256_repeated_diagonal') -StatusOverride 'gross-regression'
    Assert-P4FrameRejects {
        Invoke-P4FrameFixtureAnalysis -SlotDirectory $wrongOrderFamily -Role 'baseline' -Runner 'diagnostic' -SequenceIndex 1
    } 'measured families outside the fixed workload prefix'

    $pooled = New-P4FrameSlotFixture -Name 'pooled-counts' -Role 'baseline' -Runner 'diagnostic' `
        -SequenceIndex 1 -Families $workloadOrder
    $pooledFramesPath = Join-Path $pooled 'frames.csv'
    $pooledFrames = @(Import-Csv -LiteralPath $pooledFramesPath)
    $pooledFrames[20].workload = 'canvas16_tap'
    $pooledFrames | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath $pooledFramesPath
    Assert-P4FrameRejects {
        Invoke-P4FrameFixtureAnalysis -SlotDirectory $pooled -Role 'baseline' -Runner 'diagnostic' -SequenceIndex 1
    } 'pooled frame rows across families'

    $shortFamily = New-P4FrameSlotFixture -Name 'short-family' -Role 'baseline' -Runner 'diagnostic' `
        -SequenceIndex 1 -Families $workloadOrder
    Set-P4FrameMetadataLine -SlotDirectory $shortFamily -Key 'measured_canvas16_tap' -Value '9'
    Assert-P4FrameRejects {
        Invoke-P4FrameFixtureAnalysis -SlotDirectory $shortFamily -Role 'baseline' -Runner 'diagnostic' -SequenceIndex 1
    } 'measured family count drift'

    $disagreement = New-P4FrameSlotFixture -Name 'metadata-disagreement' -Role 'baseline' -Runner 'diagnostic' `
        -SequenceIndex 1 -Families $workloadOrder
    Set-P4FrameMetadataLine -SlotDirectory $disagreement -Key 'canvas16_tap_frame_overrun_p95_ms' -Value '0.000000'
    Assert-P4FrameRejects {
        Invoke-P4FrameFixtureAnalysis -SlotDirectory $disagreement -Role 'baseline' -Runner 'diagnostic' -SequenceIndex 1
    } 'metadata percentile disagreeing with the raw rows'

    $inputDisagreement = New-P4FrameSlotFixture -Name 'input-disagreement' -Role 'baseline' -Runner 'diagnostic' `
        -SequenceIndex 1 -Families $workloadOrder
    $inputSamplesPath = Join-Path $inputDisagreement 'samples.csv'
    $inputSamples = @(Import-Csv -LiteralPath $inputSamplesPath)
    $inputSamples[0].input_to_committed_result_ms = '19'
    $inputSamples | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath $inputSamplesPath
    Assert-P4FrameRejects {
        Invoke-P4FrameFixtureAnalysis -SlotDirectory $inputDisagreement -Role 'baseline' -Runner 'diagnostic' -SequenceIndex 1
    } 'published committed-result latency disagreeing with the raw commit frames'

    $fatal = New-P4FrameSlotFixture -Name 'fatal-match' -Role 'baseline' -Runner 'diagnostic' `
        -SequenceIndex 1 -Families $workloadOrder
    Set-P4FrameMetadataLine -SlotDirectory $fatal -Key 'fatal_anr_matches' -Value '1'
    Assert-P4FrameRejects {
        Invoke-P4FrameFixtureAnalysis -SlotDirectory $fatal -Role 'baseline' -Runner 'diagnostic' -SequenceIndex 1
    } 'fatal or ANR evidence'

    $wrongApk = New-P4FrameSlotFixture -Name 'wrong-apk' -Role 'baseline' -Runner 'diagnostic' `
        -SequenceIndex 1 -Families $workloadOrder
    Assert-P4FrameRejects {
        Invoke-P4FrameFixtureAnalysis -SlotDirectory $wrongApk -Role 'baseline' -Runner 'diagnostic' `
            -SequenceIndex 1 -ApkSha256 ('9' * 64)
    } 'APK identity drift'
    Assert-P4FrameRejects {
        Invoke-P4FrameFixtureAnalysis -SlotDirectory $wrongApk -Role 'baseline' -Runner 'diagnostic' `
            -SequenceIndex 1 -BuildCommit ('4' * 40)
    } 'measurement build commit drift'
    Assert-P4FrameRejects {
        Invoke-P4FrameFixtureAnalysis -SlotDirectory $wrongApk -Role 'baseline' -Runner 'diagnostic' `
            -SequenceIndex 1 -ExperimentId 'other-experiment'
    } 'experiment identity drift'

    $incomplete = New-P4FrameSlotFixture -Name 'incomplete-run-state' -Role 'baseline' -Runner 'diagnostic' `
        -SequenceIndex 1 -Families $workloadOrder
    $incompleteStatePath = Join-Path $incomplete 'run-state.json'
    $incompleteState = Get-Content -Raw -LiteralPath $incompleteStatePath | ConvertFrom-Json
    $incompleteState.status = 'invalid-after-samples'
    $incompleteState | ConvertTo-Json | Set-Content -LiteralPath $incompleteStatePath -Encoding utf8NoBOM
    Assert-P4FrameRejects {
        Invoke-P4FrameFixtureAnalysis -SlotDirectory $incomplete -Role 'baseline' -Runner 'diagnostic' -SequenceIndex 1
    } 'run state that never completed'

    $wrongBounds = New-P4FrameSlotFixture -Name 'wrong-bounds' -Role 'baseline' -Runner 'diagnostic' `
        -SequenceIndex 1 -Families $workloadOrder
    Set-P4FrameMetadataLine -SlotDirectory $wrongBounds -Key 'canvas256_repeated_diagonal_surface_bounds' `
        -Value '[689,615][1232,1159]'
    Assert-P4FrameRejects {
        Invoke-P4FrameFixtureAnalysis -SlotDirectory $wrongBounds -Role 'baseline' -Runner 'diagnostic' -SequenceIndex 1
    } 'pinned surface geometry drift'

    $flagged = New-P4FrameSlotFixture -Name 'flagged-row' -Role 'baseline' -Runner 'diagnostic' `
        -SequenceIndex 1 -Families $workloadOrder
    $flaggedPath = Join-Path $flagged 'frames.csv'
    $flaggedRows = @(Import-Csv -LiteralPath $flaggedPath)
    $flaggedRows[0].flags = '1'
    $flaggedRows | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath $flaggedPath
    Assert-P4FrameRejects {
        Invoke-P4FrameFixtureAnalysis -SlotDirectory $flagged -Role 'baseline' -Runner 'diagnostic' -SequenceIndex 1
    } 'a flagged frame row'

    $duplicateVsync = New-P4FrameSlotFixture -Name 'duplicate-vsync' -Role 'baseline' -Runner 'diagnostic' `
        -SequenceIndex 1 -Families $workloadOrder
    $duplicatePath = Join-Path $duplicateVsync 'frames.csv'
    $duplicateRows = @(Import-Csv -LiteralPath $duplicatePath)
    $duplicateRows[1].frame_timeline_vsync_id = $duplicateRows[0].frame_timeline_vsync_id
    $duplicateRows | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath $duplicatePath
    Assert-P4FrameRejects {
        Invoke-P4FrameFixtureAnalysis -SlotDirectory $duplicateVsync -Role 'baseline' -Runner 'diagnostic' -SequenceIndex 1
    } 'a repeated FrameTimeline vsync identity'

    # --- S5 device-lane argument assembly (dry run, no device) --------------------------------------
    $laneManifest = New-P4LaneManifest

    $commandPlan = Get-P4DeviceLanePlan -Manifest $laneManifest -Slot (Get-P4CatalogSlot 'command-baseline')
    $expectedCommandArguments = @(
        'shell', 'am', 'instrument', '-w', '-r',
        '-e', 'class', 'io.github.hideyukimori.nenepixel.measurement.P2AndroidFinalCommandMeasurementTest',
        '-e', 'nene.p4.commandRole', 'p4-indexed-command-baseline-v1',
        '-e', 'nene.p4.commandRunIndex', '1',
        '-e', 'nene.p4.measurementBuildCommit', $baselineCommit,
        '-e', 'nene.p2.physicalProfileId', $profileId,
        '-e', 'nene.p2.warmupIterations', '5',
        '-e', 'nene.p2.sampleCount', '200',
        "$applicationTestPackage/$junitRunner"
    )
    if (
        (@($commandPlan.adb_arguments) -join ' ') -cne ($expectedCommandArguments -join ' ') -or
        (@($commandPlan.install_kinds) -join '|') -cne 'app_debug|test_debug' -or
        (@($commandPlan.dexopt_packages) -join '|') -cne $applicationPackage -or
        [int]$commandPlan.timeout_seconds -ne 300 -or
        @($commandPlan.private_files).Count -ne 1 -or
        $commandPlan.private_files[0].package -cne $applicationPackage -or
        $commandPlan.private_files[0].relative_path -cne 'files/p4-measurements/p4-indexed-command-baseline-run-01.csv' -or
        $commandPlan.private_files[0].destination_name -cne 'p4-indexed-command-baseline-run-01.csv' -or
        (@($commandPlan.quiescence_packages) -join '|') -cne
            "$applicationPackage|$applicationTestPackage|$publicationTestPackage" -or
        [int]$commandPlan.expected_test_count -ne 1 -or
        [int]$commandPlan.inner_timeout_seconds -ne 280
    ) {
        throw 'The command lane assembled a different instrumentation invocation than its sources declare.'
    }
    foreach ($laneSlotId in @('command-candidate', 'memory-candidate-common-2', 'publication-candidate')) {
        $boundedPlan = Get-P4DeviceLanePlan -Manifest $laneManifest -Slot (Get-P4CatalogSlot $laneSlotId)
        if (
            [int]$boundedPlan.inner_timeout_seconds -ne ([int]$boundedPlan.timeout_seconds - 20) -or
            [int]$boundedPlan.expected_test_count -ne 1
        ) {
            throw "Lane '$laneSlotId' does not reserve the fixed cleanup budget inside its slot bound."
        }
    }

    $palettePlan = Get-P4DeviceLanePlan -Manifest $laneManifest -Slot (Get-P4CatalogSlot 'memory-candidate-palette-3')
    $expectedPaletteArguments = @(
        'shell', 'am', 'instrument', '-w', '-r',
        '-e', 'class', 'io.github.hideyukimori.nenepixel.measurement.P2ProductionHistoryRetentionMeasurementTest',
        '-e', 'nene.p4.memoryFamily', 'candidate-palette-history',
        '-e', 'nene.p4.memoryRunIndex', '3',
        '-e', 'nene.p4.measurementBuildCommit', $candidateCommit,
        '-e', 'nene.p2.physicalProfileId', $profileId,
        "$applicationTestPackage/$junitRunner"
    )
    if (
        (@($palettePlan.adb_arguments) -join ' ') -cne ($expectedPaletteArguments -join ' ') -or
        @($palettePlan.private_files).Count -ne 0
    ) {
        throw 'The palette memory lane assembled a different instrumentation invocation.'
    }

    $importPlan = Get-P4DeviceLanePlan -Manifest $laneManifest -Slot (Get-P4CatalogSlot 'memory-candidate-import-1')
    $expectedImportArguments = @(
        'shell', 'am', 'instrument', '-w', '-r',
        '-e', 'class', 'io.github.hideyukimori.nenepixel.measurement.P4LegacyImportRetentionMeasurementTest',
        '-e', 'nene.p4.memoryFamily', 'candidate-legacy-import',
        '-e', 'nene.p4.memoryRunIndex', '1',
        '-e', 'nene.p4.measurementBuildCommit', $candidateCommit,
        '-e', 'nene.p2.physicalProfileId', $profileId,
        "$applicationTestPackage/$junitRunner"
    )
    if ((@($importPlan.adb_arguments) -join ' ') -cne ($expectedImportArguments -join ' ')) {
        throw 'The legacy-import memory lane assembled a different instrumentation invocation.'
    }

    $baselineMemoryPlan = Get-P4DeviceLanePlan -Manifest $laneManifest -Slot (Get-P4CatalogSlot 'memory-baseline-common-5')
    if (
        $baselineMemoryPlan.instrumentation_arguments['nene.p4.memoryFamily'] -cne 'baseline-common-drawing-history' -or
        $baselineMemoryPlan.instrumentation_arguments['nene.p4.memoryRunIndex'] -cne '5' -or
        $baselineMemoryPlan.class -cne 'io.github.hideyukimori.nenepixel.measurement.P2ProductionHistoryRetentionMeasurementTest'
    ) {
        throw 'The baseline common-history memory lane drifted from the baseline overlay contract.'
    }

    $publicationPlan = Get-P4DeviceLanePlan -Manifest $laneManifest -Slot (Get-P4CatalogSlot 'publication-baseline')
    $expectedPublicationArguments = @(
        'shell', 'am', 'instrument', '-w', '-r',
        '-e', 'class', 'io.github.hideyukimori.nenepixel.adapters.persistence.AutosavePublicationDeviceEvidence',
        '-e', 'nene.p4.publicationEvidence', 'collect',
        '-e', 'nene.p4.publicationEvidenceRole', 'baseline',
        "$publicationTestPackage/$junitRunner"
    )
    if (
        (@($publicationPlan.adb_arguments) -join ' ') -cne ($expectedPublicationArguments -join ' ') -or
        (@($publicationPlan.install_kinds) -join '|') -cne 'publication_test' -or
        (@($publicationPlan.dexopt_packages) -join '|') -cne $publicationTestPackage -or
        @($publicationPlan.private_files).Count -ne 2 -or
        $publicationPlan.private_files[0].relative_path -cne 'files/p4-indexed-publication-device-baseline-v1.csv' -or
        $publicationPlan.private_files[1].relative_path -cne 'files/p4-indexed-publication-device-baseline-v1.status' -or
        $publicationPlan.private_files[0].package -cne $publicationTestPackage
    ) {
        throw 'The publication lane assembled a different instrumentation invocation or private-file set.'
    }

    $targetedPublication = New-P4LaneManifest
    $targetedPublication.roles.baseline.artifacts.publication_test.target_package = $applicationPackage
    $targetedPublicationPlan =
        Get-P4DeviceLanePlan -Manifest $targetedPublication -Slot (Get-P4CatalogSlot 'publication-baseline')
    if ((@($targetedPublicationPlan.install_kinds) -join '|') -cne 'app_debug|publication_test') {
        throw 'A publication APK that targets the application did not also install the application.'
    }

    $framePlan = Get-P4DeviceLanePlan -Manifest $laneManifest -Slot (Get-P4CatalogSlot 'frame-3-candidate-decision')
    $frameParameters = $framePlan.frame_parameters
    if (
        $frameParameters.Variant -cne 'release-like' -or
        $frameParameters.CompilationMode -cne 'speed-profile' -or
        $frameParameters.RunKind -cne 'decision' -or
        $frameParameters.CandidateRole -cne 'candidate' -or
        [int]$frameParameters.ComparisonSequenceIndex -ne 3 -or
        [int]$frameParameters.Attempt -ne 1 -or
        [int]$frameParameters.SampleCount -ne 50 -or
        $frameParameters.ExperimentId -cne $fixtureExperimentId -or
        $frameParameters.SourceCommit -cne $candidateCommit -or
        $frameParameters.ApkPath -cne 'C:/fixture/candidate-app-release-like.apk' -or
        $frameParameters.CandidateApkSha256 -cne $candidateApk -or
        $frameParameters.BaselineCanvas16SurfaceBounds -cne $canvas16Bounds -or
        $frameParameters.DeviceSerial -cne 'FIXTURESERIAL' -or
        (Split-Path -Leaf $framePlan.frame_slot_directory) -cne 'slot-03-decision-candidate-attempt-1' -or
        @($framePlan.install_kinds).Count -ne 0
    ) {
        throw 'The frame lane assembled a different measure-m2-frame.ps1 invocation than the manifest declares.'
    }
    $collectorParameters = @((Get-Command (Join-Path $PSScriptRoot 'measurements/measure-m2-frame.ps1')).Parameters.Keys)
    foreach ($name in $frameParameters.Keys) {
        if ($name -cnotin $collectorParameters) {
            throw "The frame lane passes '$name', which measure-m2-frame.ps1 does not declare."
        }
    }
    foreach ($name in @($collectorParameters | Where-Object {
                (Get-Command (Join-Path $PSScriptRoot 'measurements/measure-m2-frame.ps1')).Parameters[$_].Attributes |
                    Where-Object { $_ -is [Parameter] -and $_.Mandatory }
            })) {
        if ($name -cnotin @($frameParameters.Keys)) {
            throw "The frame lane omits the mandatory collector parameter '$name'."
        }
    }

    # --- frame lane recovery quarantine (dry run) ---------------------------------------------------
    $expectedLiveNames = 'nene-pixel-recovery-v1|nene-pixel-recovery-v1.new|nene-pixel-recovery-v1.bak'
    $candidateQuarantine = $framePlan.recovery_quarantine
    if (
        $null -eq $candidateQuarantine -or
        $candidateQuarantine.package -cne $applicationPackage -or
        $candidateQuarantine.install_kind -cne 'app_debug' -or
        $candidateQuarantine.source_directory -cne 'no_backup' -or
        (@($candidateQuarantine.live_names) -join '|') -cne $expectedLiveNames -or
        $candidateQuarantine.quarantine_path -cne
            "no_backup/p4-quarantine/$fixtureExperimentId/frame-3-candidate-decision"
    ) {
        throw 'The frame lane did not plan the fixed per-slot recovery quarantine.'
    }
    $baselineFramePlan = Get-P4DeviceLanePlan -Manifest $laneManifest -Slot (Get-P4CatalogSlot 'frame-4-baseline-decision')
    $baselineQuarantine = $baselineFramePlan.recovery_quarantine
    if (
        $baselineQuarantine.package -cne $applicationPackage -or
        $baselineQuarantine.quarantine_path -cne
            "no_backup/p4-quarantine/$fixtureExperimentId/frame-4-baseline-decision" -or
        $baselineQuarantine.quarantine_path -ceq $candidateQuarantine.quarantine_path
    ) {
        throw 'Each frame slot must quarantine into its own directory under the experiment.'
    }
    foreach ($guarded in @('issue-106-user-recovery-20260916-0103', 'issue-89-evidence', 'issue-102-evidence')) {
        if ($guarded -cin @($candidateQuarantine.live_names) -or
            $candidateQuarantine.quarantine_path.Contains($guarded)) {
            throw 'The quarantine plan reaches a guarded no_backup entry.'
        }
    }
    foreach ($nonFrameSlotId in @('command-baseline', 'memory-candidate-import-1', 'publication-candidate')) {
        $nonFramePlan = Get-P4DeviceLanePlan -Manifest $laneManifest -Slot (Get-P4CatalogSlot $nonFrameSlotId)
        if ($null -ne $nonFramePlan.recovery_quarantine) {
            throw "Lane '$nonFrameSlotId' must not quarantine the recovery record."
        }
    }
    $badExperiment = New-P4LaneManifest
    $badExperiment.experiment_id = 'Not A Valid Id'
    Assert-P4FrameRejects {
        Get-P4RecoveryQuarantinePlan -Manifest $badExperiment -Role 'baseline' -SlotId 'frame-4-baseline-decision'
    } 'a quarantine path built from an unconstrained experiment identity'
    Assert-P4FrameRejects {
        Get-P4RecoveryQuarantinePlan -Manifest $laneManifest -Role 'baseline' -SlotId '../escape'
    } 'a quarantine path built from an unconstrained slot identity'

    $hostSlot = Get-P4CatalogSlot 'host-project-baseline'
    Assert-P4FrameRejects { Get-P4DeviceLanePlan -Manifest $laneManifest -Slot $hostSlot } 'a host slot routed to a device lane'

    $collidingPackages = New-P4LaneManifest
    $collidingPackages.roles.candidate.artifacts.publication_test.test_package = $applicationTestPackage
    Assert-P4FrameRejects {
        Get-P4DeviceLanePlan -Manifest $collidingPackages -Slot (Get-P4CatalogSlot 'publication-candidate')
    } 'a reused device package identity'

    $foreignTarget = New-P4LaneManifest
    $foreignTarget.roles.candidate.artifacts.test_debug.target_package = 'io.github.hideyukimori.other'
    Assert-P4FrameRejects {
        Get-P4DeviceLanePlan -Manifest $foreignTarget -Slot (Get-P4CatalogSlot 'command-candidate')
    } 'a test APK that targets a foreign application'

    $missingExperiment = New-P4LaneManifest
    $missingExperiment.frame_experiment.stop_conditions = ''
    Assert-P4FrameRejects {
        Get-P4DeviceLanePlan -Manifest $missingExperiment -Slot (Get-P4CatalogSlot 'frame-1-baseline-diagnostic')
    } 'an incomplete frame experiment declaration'

    Assert-P4FrameRejects {
        Get-P4InstrumentationArguments -TestPackage $applicationTestPackage -Class $script:P4CommandClass `
            -Arguments ([ordered]@{ 'nene.p4.commandRole' = 'two words' })
    } 'an instrumentation argument that is not a single token'

    Write-Output 'P4 frame analyzer and device-lane assembly validation: PASS'
}
finally {
    $resolvedTemporaryRoot = [IO.Path]::GetFullPath($temporaryRoot)
    $resolvedSystemTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if ($resolvedTemporaryRoot.StartsWith($resolvedSystemTemp, [StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

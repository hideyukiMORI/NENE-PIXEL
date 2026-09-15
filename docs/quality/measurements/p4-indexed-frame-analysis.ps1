Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../baseline-profile-evidence.ps1')

$script:P4FrameSchema = 'nene-pixel-p4-indexed-actual-app-frame-v8'
$script:P4FrameExperimentSchema = 'nene-pixel-p4-indexed-frame-experiment-v4'
$script:P4FrameGeometryId = 'initial-fit-centered-v1'
$script:P4FrameWorkloadOrder = @('canvas16_tap', 'canvas256_repeated_diagonal')
$script:P4FrameWarmups = 5
$script:P4FrameOverrunP95Gate = 0.0
$script:P4FrameOverrunP99Gate = 16.67
$script:P4FrameInputP95Gate = 33.33
$script:P4FrameGrossOverrun = 33.34
$script:P4FrameGrossInput = 100.0

function Get-P4FrameNearestRank {
    param(
        [Parameter(Mandatory = $true)][double[]]$Values,
        [Parameter(Mandatory = $true)][double]$Percentile
    )
    if ($Values.Count -eq 0) { throw 'A frame percentile requires a nonempty population.' }
    $sorted = [double[]]$Values.Clone()
    [Array]::Sort($sorted)
    $index = [Math]::Ceiling($sorted.Count * $Percentile) - 1
    return $sorted[$index]
}

function Format-P4FrameMetric {
    param([Parameter(Mandatory = $true)][double]$Value)
    # Culture-independent: the published metric text must not depend on the operator's locale.
    return $Value.ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
}

function Get-P4FrameObjectMember {
    param(
        [AllowNull()][Parameter(Mandatory = $true)][object]$Value,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Context
    )
    if ($null -eq $Value -or $Name -cnotin @($Value.PSObject.Properties.Name)) {
        throw "$Context is missing '$Name'."
    }
    return $Value.$Name
}

function Read-P4FrameMetadata {
    param([Parameter(Mandatory = $true)][string[]]$Lines)
    $metadata = [ordered]@{}
    foreach ($line in $Lines) {
        if ([string]::IsNullOrWhiteSpace($line)) { throw 'Frame metadata contains a blank record.' }
        $separator = $line.IndexOf('=', [StringComparison]::Ordinal)
        if ($separator -lt 1) { throw "Frame metadata is not a key=value record: $line" }
        $key = $line.Substring(0, $separator)
        if ($metadata.Contains($key)) { throw "Frame metadata repeats '$key'." }
        $metadata[$key] = $line.Substring($separator + 1)
    }
    if ($metadata.Count -eq 0) { throw 'Frame metadata is empty.' }
    return $metadata
}

function Get-P4FrameMetadataValue {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Metadata,
        [Parameter(Mandatory = $true)][string]$Key
    )
    if (-not $Metadata.Contains($Key)) { throw "Frame metadata is missing '$Key'." }
    $value = [string]$Metadata[$Key]
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Frame metadata '$Key' is empty." }
    return $value
}

function Assert-P4FrameMetadataValue {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Metadata,
        [Parameter(Mandatory = $true)][string]$Key,
        [Parameter(Mandatory = $true)][string]$Expected
    )
    $actual = Get-P4FrameMetadataValue -Metadata $Metadata -Key $Key
    if ($actual -cne $Expected) { throw "Frame metadata '$Key' is '$actual' but must be '$Expected'." }
    return $actual
}

function Get-P4FrameFileInventory {
    param([Parameter(Mandatory = $true)][string]$SlotDirectory)
    $root = [IO.Path]::GetFullPath($SlotDirectory)
    $files = [Collections.Generic.List[object]]::new()
    foreach ($item in @(Get-ChildItem -LiteralPath $root -Recurse -File -Force | Sort-Object -Property FullName)) {
        $full = [IO.Path]::GetFullPath($item.FullName)
        if (-not $full.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'A frame slot file escaped its slot directory.'
        }
        if ($null -ne $item.LinkType) { throw "A frame slot file is a link: $full" }
        $relative = $full.Substring($root.Length).TrimStart([char]'\', [char]'/').Replace('\', '/')
        $files.Add([ordered]@{ relative_path = $relative; byte_count = $item.Length; sha256 = Get-FileSha256 $full })
    }
    if ($files.Count -eq 0) { throw 'A frame slot directory must retain its collected evidence.' }
    return $files.ToArray()
}

function Get-P4FrameSampleInput {
    param([Parameter(Mandatory = $true)][object[]]$CommitRows)
    if ($CommitRows.Count -lt 1) { throw 'A committed-result sample requires at least one commit frame.' }
    $start = [long]($CommitRows | ForEach-Object { [long]$_.handle_input_start_nanos } | Measure-Object -Minimum).Minimum
    $completion = [long]($CommitRows | ForEach-Object { [long]$_.frame_completed_nanos } | Measure-Object -Maximum).Maximum
    if ($start -le 0 -or $completion -le $start) {
        throw 'A committed-result sample does not preserve UP input to completion order.'
    }
    return ($completion - $start) / 1000000.0
}

function Test-P4FrameCapture {
    param(
        [Parameter(Mandatory = $true)][string]$SlotDirectory,
        [Parameter(Mandatory = $true)][ValidateSet('baseline', 'candidate')][string]$Role,
        [Parameter(Mandatory = $true)][ValidateSet('diagnostic', 'decision')][string]$Runner,
        [Parameter(Mandatory = $true)][ValidateRange(1, 4)][int]$SequenceIndex,
        [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-f]{40}$')][string]$BuildCommit,
        [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-f]{64}$')][string]$ExpectedApkSha256,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$ExpectedBounds,
        [Parameter(Mandatory = $true)][string]$ExperimentId
    )

    foreach ($workload in $script:P4FrameWorkloadOrder) {
        if (-not $ExpectedBounds.Contains($workload)) { throw "Expected frame bounds are missing '$workload'." }
        if ([string]$ExpectedBounds[$workload] -cnotmatch '^\[\d+,\d+\]\[\d+,\d+\]$') {
            throw "Expected frame bounds for '$workload' are not an integer rectangle."
        }
    }
    $expectedSamples = if ($Runner -eq 'diagnostic') { 10 } else { 50 }
    $expectedSlotName = 'slot-{0:D2}-{1}-{2}-attempt-1' -f $SequenceIndex, $Runner, $Role
    $root = [IO.Path]::GetFullPath($SlotDirectory)
    if (-not (Test-Path -LiteralPath $root -PathType Container)) { throw "Frame slot directory is missing: $root" }
    if ((Split-Path -Leaf $root) -cne $expectedSlotName) {
        throw "Frame slot directory '$(Split-Path -Leaf $root)' is not the expected '$expectedSlotName'."
    }

    $statePath = Join-Path $root 'run-state.json'
    $metadataPath = Join-Path $root 'metadata.txt'
    $framesPath = Join-Path $root 'frames.csv'
    $samplesPath = Join-Path $root 'samples.csv'
    foreach ($path in @($statePath, $metadataPath, $framesPath, $samplesPath)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Frame evidence is missing: $path" }
    }

    $state = Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json
    $stateNames = @($state.PSObject.Properties.Name)
    foreach ($key in @('schema', 'experiment_id', 'comparison_sequence_index', 'attempt', 'status', 'verdict',
            'workload_order', 'measured_workload_counts', 'measured_operation_count')) {
        if ($key -notin $stateNames) { throw "Frame run state is missing '$key'." }
    }
    if (
        $state.schema -cne $script:P4FrameExperimentSchema -or
        $state.experiment_id -cne $ExperimentId -or
        [int]$state.comparison_sequence_index -ne $SequenceIndex -or
        [int]$state.attempt -ne 1 -or
        $state.status -cne 'completed' -or
        (@($state.workload_order) -join '|') -cne ($script:P4FrameWorkloadOrder -join '|')
    ) {
        throw 'Frame run state identity, attempt, completion or workload order drifted.'
    }

    $metadata = Read-P4FrameMetadata -Lines @(Get-Content -LiteralPath $metadataPath)
    Assert-P4FrameMetadataValue $metadata 'schema' $script:P4FrameSchema | Out-Null
    Assert-P4FrameMetadataValue $metadata 'experiment_schema' $script:P4FrameExperimentSchema | Out-Null
    Assert-P4FrameMetadataValue $metadata 'experiment_id' $ExperimentId | Out-Null
    Assert-P4FrameMetadataValue $metadata 'variant' 'release-like' | Out-Null
    Assert-P4FrameMetadataValue $metadata 'compile_mode' 'speed-profile' | Out-Null
    Assert-P4FrameMetadataValue $metadata 'run_kind' $Runner | Out-Null
    Assert-P4FrameMetadataValue $metadata 'candidate_role' $Role | Out-Null
    Assert-P4FrameMetadataValue $metadata 'comparison_sequence_index' ([string]$SequenceIndex) | Out-Null
    Assert-P4FrameMetadataValue $metadata 'workload_order' ($script:P4FrameWorkloadOrder -join '|') | Out-Null
    Assert-P4FrameMetadataValue $metadata 'source_commit' $BuildCommit | Out-Null
    Assert-P4FrameMetadataValue $metadata 'apk_sha256' $ExpectedApkSha256 | Out-Null
    Assert-P4FrameMetadataValue $metadata 'apk_embedded_source_commit' $BuildCommit | Out-Null
    Assert-P4FrameMetadataValue $metadata 'geometry_id' $script:P4FrameGeometryId | Out-Null
    Assert-P4FrameMetadataValue $metadata 'device_evidence_class' 'physical_device' | Out-Null
    Assert-P4FrameMetadataValue $metadata 'warmups_per_workload' ([string]$script:P4FrameWarmups) | Out-Null
    Assert-P4FrameMetadataValue $metadata 'samples_per_workload' ([string]$expectedSamples) | Out-Null
    Assert-P4FrameMetadataValue $metadata 'fatal_anr_matches' '0' | Out-Null
    foreach ($workload in $script:P4FrameWorkloadOrder) {
        Assert-P4FrameMetadataValue $metadata "${workload}_surface_bounds" ([string]$ExpectedBounds[$workload]) | Out-Null
    }
    if ((Get-P4FrameMetadataValue -Metadata $metadata -Key 'percentile_method') -cnotmatch '^nearest-rank;') {
        throw 'Frame metadata does not record the nearest-rank percentile method.'
    }
    $status = Get-P4FrameMetadataValue -Metadata $metadata -Key 'status'
    if ($status -cne [string]$state.verdict) { throw 'Frame metadata status and run-state verdict disagree.' }
    $acceptanceLane = Get-P4FrameMetadataValue -Metadata $metadata -Key 'acceptance_lane'
    if ($acceptanceLane -cne $Runner) { throw "Frame acceptance lane '$acceptanceLane' is not '$Runner'." }

    $frames = @(Import-Csv -LiteralPath $framesPath)
    $samples = @(Import-Csv -LiteralPath $samplesPath)
    if ($frames.Count -eq 0 -or $samples.Count -eq 0) { throw 'Frame or sample rows are missing.' }
    if (
        [int](Get-P4FrameMetadataValue -Metadata $metadata -Key 'raw_frame_rows') -ne $frames.Count -or
        [int](Get-P4FrameMetadataValue -Metadata $metadata -Key 'valid_frame_rows') -ne $frames.Count -or
        [int](Get-P4FrameMetadataValue -Metadata $metadata -Key 'aggregate_frames_rendered') -ne $frames.Count -or
        [int](Get-P4FrameMetadataValue -Metadata $metadata -Key 'measured_operation_count') -ne $samples.Count -or
        [int]$state.measured_operation_count -ne $samples.Count
    ) {
        throw 'Aggregate frame or operation counts disagree with the retained rows.'
    }
    if (@($frames | ForEach-Object { [long]$_.frame_timeline_vsync_id } | Sort-Object -Unique).Count -ne $frames.Count) {
        throw 'Frame rows repeat a FrameTimeline vsync identity.'
    }
    foreach ($row in $frames) {
        if (
            [int]$row.flags -ne 0 -or
            $row.variant -cne 'release-like' -or
            $row.source_commit -cne $BuildCommit -or
            $row.phase -cnotin @('preview', 'commit')
        ) {
            throw 'A frame row is invalid, foreign or outside the fixed preview/commit phases.'
        }
    }
    foreach ($row in $samples) {
        if (
            $row.source_commit -cne $BuildCommit -or
            $row.variant -cne 'release-like' -or
            [string]$row.committed_ui_verified -cnotin @('True', 'true')
        ) {
            throw 'A sample row is foreign or was not verified against the committed UI.'
        }
    }
    if (($samples | ForEach-Object { [int]$_.raw_row_count } | Measure-Object -Sum).Sum -ne $frames.Count) {
        throw 'Sample raw row counts do not account for every retained frame row.'
    }

    $presentWorkloads = @($samples | ForEach-Object { $_.workload } | Select-Object -Unique)
    $expectedPrefix = @($script:P4FrameWorkloadOrder | Select-Object -First $presentWorkloads.Count)
    if (
        $presentWorkloads.Count -lt 1 -or
        ($presentWorkloads -join '|') -cne ($expectedPrefix -join '|')
    ) {
        throw 'The measured frame families are not a prefix of the fixed workload order.'
    }
    $completeRun = $presentWorkloads.Count -eq $script:P4FrameWorkloadOrder.Count
    if (-not $completeRun -and $status -cnotin @('fail', 'gross-regression')) {
        throw 'A frame family is missing without a recorded early stop.'
    }
    if (@($frames | ForEach-Object { $_.workload } | Select-Object -Unique |
                Where-Object { $_ -cnotin $presentWorkloads }).Count -ne 0) {
        throw 'Frame rows pooled a family that produced no measured operations.'
    }

    $families = [ordered]@{}
    $ordinal = 0
    foreach ($workload in $presentWorkloads) {
        $familySamples = @($samples | Where-Object { $_.workload -ceq $workload })
        $familyFrames = @($frames | Where-Object { $_.workload -ceq $workload })
        if ($familySamples.Count -ne $expectedSamples) {
            throw "Family '$workload' retained $($familySamples.Count) operations instead of $expectedSamples."
        }
        $measuredKey = "measured_$workload"
        $stateCount = Get-P4FrameObjectMember -Value $state.measured_workload_counts -Name $workload `
            -Context 'Frame run state measured_workload_counts'
        if (
            [int](Get-P4FrameMetadataValue -Metadata $metadata -Key $measuredKey) -ne $expectedSamples -or
            [int]$stateCount -ne $expectedSamples
        ) {
            throw "Measured operation counts for '$workload' disagree with its retained population."
        }
        $inputValues = [Collections.Generic.List[double]]::new()
        foreach ($sampleIndex in 1..$expectedSamples) {
            $ordinal += 1
            $sample = $familySamples[$sampleIndex - 1]
            if (
                [int]$sample.sample_index -ne $sampleIndex -or
                [int]$sample.operation_ordinal -ne $ordinal -or
                $sample.operation -cne "$workload#$sampleIndex"
            ) {
                throw "Family '$workload' operation $sampleIndex is missing, duplicated or reordered."
            }
            $operationFrames = @($familyFrames | Where-Object { [int]$_.operation_ordinal -eq $ordinal })
            if (
                @($operationFrames | Where-Object { $_.operation -cne "$workload#$sampleIndex" }).Count -gt 0 -or
                $operationFrames.Count -ne [int]$sample.raw_row_count -or
                $operationFrames.Count -ne [int]$sample.valid_row_count
            ) {
                throw "Family '$workload' operation $sampleIndex pooled or lost its frame rows."
            }
            $previewRows = @($operationFrames | Where-Object { $_.phase -ceq 'preview' })
            $commitRows = @($operationFrames | Where-Object { $_.phase -ceq 'commit' })
            if (
                $previewRows.Count -ne [int]$sample.preview_frame_count -or
                $previewRows.Count -ne [int]$sample.preview_valid_frame_count -or
                $commitRows.Count -ne [int]$sample.commit_frame_count -or
                $commitRows.Count -ne [int]$sample.commit_valid_frame_count -or
                $previewRows.Count -lt 1 -or
                $commitRows.Count -lt 1
            ) {
                throw "Family '$workload' operation $sampleIndex lost a preview or committed-result phase."
            }
            $recomputedInput = Get-P4FrameSampleInput -CommitRows $commitRows
            if ((Format-P4FrameMetric $recomputedInput) -cne (Format-P4FrameMetric ([double]$sample.input_to_committed_result_ms))) {
                throw "Family '$workload' operation $sampleIndex committed-result latency disagrees with its raw frames."
            }
            $inputValues.Add($recomputedInput)
        }
        $overruns = [double[]]@($familyFrames | ForEach-Object { [double]$_.frame_overrun_ms })
        $inputs = [double[]]$inputValues.ToArray()
        $overrunP95 = Get-P4FrameNearestRank -Values $overruns -Percentile 0.95
        $overrunP99 = Get-P4FrameNearestRank -Values $overruns -Percentile 0.99
        $inputP95 = Get-P4FrameNearestRank -Values $inputs -Percentile 0.95
        $maximumOverrun = ($overruns | Measure-Object -Maximum).Maximum
        $maximumInput = ($inputs | Measure-Object -Maximum).Maximum
        $recomputed = [ordered]@{
            "${workload}_frame_count" = [string]$familyFrames.Count
            "${workload}_frame_overrun_p95_ms" = Format-P4FrameMetric $overrunP95
            "${workload}_frame_overrun_p99_ms" = Format-P4FrameMetric $overrunP99
            "${workload}_input_to_committed_result_p95_ms" = Format-P4FrameMetric $inputP95
            "${workload}_maximum_frame_overrun_ms" = Format-P4FrameMetric $maximumOverrun
            "${workload}_maximum_input_to_committed_result_ms" = Format-P4FrameMetric $maximumInput
        }
        foreach ($key in $recomputed.Keys) {
            $published = Get-P4FrameMetadataValue -Metadata $metadata -Key $key
            if ($published -cne $recomputed[$key]) {
                throw "Recomputed '$key' is '$($recomputed[$key])' but the collector published '$published'."
            }
        }
        $passed =
            $overrunP95 -le $script:P4FrameOverrunP95Gate -and
            $overrunP99 -le $script:P4FrameOverrunP99Gate -and
            $inputP95 -le $script:P4FrameInputP95Gate
        $gross = $maximumOverrun -gt $script:P4FrameGrossOverrun -or $maximumInput -gt $script:P4FrameGrossInput
        Assert-P4FrameMetadataValue $metadata "${workload}_threshold_status" $(if ($passed) { 'pass' } else { 'fail' }) | Out-Null
        Assert-P4FrameMetadataValue $metadata "${workload}_diagnostic_gross_regression" $(if ($gross) { 'true' } else { 'false' }) | Out-Null
        $families[$workload] = [ordered]@{
            frame_count = $familyFrames.Count
            operation_count = $expectedSamples
            frame_overrun_p95_ms = $recomputed["${workload}_frame_overrun_p95_ms"]
            frame_overrun_p99_ms = $recomputed["${workload}_frame_overrun_p99_ms"]
            input_to_committed_result_p95_ms = $recomputed["${workload}_input_to_committed_result_p95_ms"]
            maximum_frame_overrun_ms = $recomputed["${workload}_maximum_frame_overrun_ms"]
            maximum_input_to_committed_result_ms = $recomputed["${workload}_maximum_input_to_committed_result_ms"]
            threshold_status = if ($passed) { 'pass' } else { 'fail' }
            gross_regression = $gross
        }
    }

    $allPassed = $completeRun -and @($families.Keys | Where-Object { $families[$_].threshold_status -cne 'pass' }).Count -eq 0
    $anyGross = @($families.Keys | Where-Object { $families[$_].gross_regression }).Count -gt 0
    $verdict =
        if ($Runner -eq 'decision') {
            if ($allPassed) { 'pass' } else { 'PERFORMANCE_FAIL' }
        }
        elseif ($anyGross) { 'PERFORMANCE_FAIL' }
        else {
            if (-not $completeRun) { throw 'A diagnostic frame slot stopped early without a gross regression.' }
            'inconclusive'
        }

    return [ordered]@{
        verdict = $verdict
        schema = $script:P4FrameSchema
        experiment_schema = $script:P4FrameExperimentSchema
        experiment_id = $ExperimentId
        role = $Role
        runner = $Runner
        sequence_index = $SequenceIndex
        attempt = 1
        slot_directory = $root
        collector_status = $status
        acceptance_lane = $acceptanceLane
        build_commit = $BuildCommit
        apk_sha256 = $ExpectedApkSha256
        geometry_id = $script:P4FrameGeometryId
        warmups_per_workload = $script:P4FrameWarmups
        samples_per_workload = $expectedSamples
        measured_operation_count = $samples.Count
        raw_frame_rows = $frames.Count
        valid_frame_rows = $frames.Count
        fatal_anr_matches = 0
        complete_run = $completeRun
        families = $families
        frame_files = @(Get-P4FrameFileInventory -SlotDirectory $root)
    }
}

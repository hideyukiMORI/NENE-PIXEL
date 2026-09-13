[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-command-analysis.ps1')

$P4SyntheticColumns = @(
    'record_type', 'name', 'value', 'evidence_class', 'physical_profile_id', 'candidate_id', 'run_index',
    'measurement_build_commit', 'canvas_width', 'canvas_height', 'position_count', 'warmup_iterations',
    'sample_count', 'local_sample_index', 'global_sample_index', 'latency_nanos', 'result_kind',
    'source_revision', 'revision_after', 'history_after', 'document_hash', 'snapshot_hash',
    'change_set_before_revision', 'change_set_after_revision', 'render_invalidation_origin_x',
    'render_invalidation_origin_y', 'render_invalidation_width', 'render_invalidation_height',
    'definition_transition', 'default_index_before', 'default_index_after', 'expected_definition_identity',
    'unchanged_state_identity', 'art_allocated_bytes_before', 'art_allocated_bytes_after',
    'art_allocated_bytes_delta', 'art_gc_count_delta', 'art_gc_time_ms_delta', 'art_blocking_gc_count_delta',
    'art_blocking_gc_time_ms_delta', 'post_gc_java_heap_used_bytes', 'post_gc_java_heap_committed_bytes',
    'total_pss_kb', 'dalvik_pss_kb', 'native_pss_kb', 'other_pss_kb', 'total_private_dirty_kb',
    'total_shared_dirty_kb', 'display_mode_id', 'display_width_pixels', 'display_height_pixels',
    'refresh_rate_hertz', 'thermal_status', 'power_save_mode', 'interactive', 'usb_powered',
    'battery_level_percent', 'boundary'
)

function Convert-P4SyntheticRow {
    param([Parameter(Mandatory = $true)][hashtable]$Values)
    return (($P4SyntheticColumns | ForEach-Object {
                $value = if ($Values.ContainsKey($_)) { [string]$Values[$_] } else { '' }
                '"' + $value.Replace('"', '""') + '"'
            }) -join ',')
}

function New-P4SyntheticCommandCapture {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('baseline', 'candidate')][string]$Role,
        [long]$LatencyNanos = 1000000,
        [int]$BlockingRows = 0,
        [switch]$BoundaryLatencies
    )
    $build = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
    $schema = 'nene-pixel-p4-indexed-command-latency-v1'
    $candidateId = "p4-indexed-command-$Role-v1"
    $workloads = @(Get-P4CommandWorkloads $Role)
    $totalSamples = $workloads.Count * 200
    $lines = [Collections.Generic.List[string]]::new()
    $lines.Add(($P4SyntheticColumns | ForEach-Object { '"' + $_ + '"' }) -join ',')
    $metadata = [ordered]@{
        schema = $schema
        output_identity = "p4-indexed-command-$Role-run-01"
        run_status = 'valid'
        candidate_id = $candidateId
        run_index = '1'
        measurement_build_commit = $build
        app_variant = 'debug'
        test_variant = 'debugAndroidTest'
        evidence_class = 'synthetic'
        physical_profile_id = 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36'
        build_fingerprint = 'ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94111:user/release-keys'
        security_patch = '2026-08-05'
        runtime_max_memory_bytes = '268435456'
        memory_class_mib = '256'
        canvas = '256x256'
        warmup_iterations_per_workload = '5'
        sample_count_per_workload = '200'
        sample_count_total = [string]$totalSamples
        workload_order = $workloads -join '|'
        sample_indices = "local=1..200;global=1..$totalSamples"
        checkpoint_interval_global_samples = '25'
        checkpoint_row_count = [string](2 + ($totalSamples / 25))
    }
    foreach ($entry in $metadata.GetEnumerator()) {
        $lines.Add((Convert-P4SyntheticRow @{ record_type = 'metadata'; name = $entry.Key; value = $entry.Value }))
    }
    foreach ($workload in $workloads) {
        $values = @{
            record_type = 'correctness'; name = $workload; evidence_class = 'synthetic';
            physical_profile_id = $metadata.physical_profile_id; candidate_id = $candidateId; run_index = '1';
            measurement_build_commit = $build; canvas_width = '256'; canvas_height = '256';
            position_count = if ($workload -eq 'sparse_apply_stroke') { '256' } else { '65536' };
            document_hash = '111'; snapshot_hash = '222'; boundary = 'synthetic-boundary'
        }
        $noOp = $workload -eq 'dense_same_target_no_op'
        $undo = $workload -in @('dense_undo', 'palette_many_to_one_undo')
        $palette = $workload -in @(
            'palette_recolor_full', 'palette_default_only', 'palette_many_to_one_dense',
            'palette_many_to_one_undo', 'palette_many_to_one_redo'
        )
        $values.result_kind = if ($noOp) { 'rejected_no_effective_change' } else { 'applied' }
        $values.source_revision = if ($undo) { '1' } else { '0' }
        $values.revision_after = if ($undo -or $noOp) { '0' } else { '1' }
        $values.history_after = if ($undo) { 'redo_available' } elseif ($noOp) { 'none' } else { 'undo_available' }
        if ($Role -eq 'baseline') {
            $values.definition_transition = 'not_applicable'
        } else {
            $values.definition_transition = if ($palette) { 'changed' } else { 'unchanged' }
            $values.default_index_before = if ($palette) { '0' } else { '2' }
            $values.default_index_after = if ($workload -eq 'palette_default_only') { '255' } else { $values.default_index_before }
            $values.expected_definition_identity = 'true'
        }
        $values.unchanged_state_identity = if ($noOp) { 'true' } else { 'false' }
        if (-not $noOp) {
            $values.change_set_before_revision = if ($undo) { '1' } else { '0' }
            $values.change_set_after_revision = if ($undo) { '0' } else { '1' }
            $values.render_invalidation_origin_x = '0'; $values.render_invalidation_origin_y = '0'
            $values.render_invalidation_width = '256'; $values.render_invalidation_height = '256'
        }
        $lines.Add((Convert-P4SyntheticRow $values))
    }
    $baselineValues = @{
        record_type = 'baseline'; name = 'process_post_gc_before_samples'; evidence_class = 'synthetic';
        physical_profile_id = $metadata.physical_profile_id; candidate_id = $candidateId; run_index = '1';
        measurement_build_commit = $build; post_gc_java_heap_used_bytes = '100';
        post_gc_java_heap_committed_bytes = '200'; total_pss_kb = '300'; dalvik_pss_kb = '100';
        native_pss_kb = '100'; other_pss_kb = '100'; total_private_dirty_kb = '200'; total_shared_dirty_kb = '100';
        boundary = 'synthetic-boundary'
    }
    $lines.Add((Convert-P4SyntheticRow $baselineValues))
    $checkpointIndexes = @(0) + @(25..$totalSamples | Where-Object { $_ % 25 -eq 0 }) + @($totalSamples)
    $checkpointNames = @('before_samples') + @(25..$totalSamples | Where-Object { $_ % 25 -eq 0 } | ForEach-Object { "after_$_" }) + @('after_samples')
    for ($i = 0; $i -lt $checkpointIndexes.Count; $i++) {
        $values = @{
            record_type = 'checkpoint'; name = $checkpointNames[$i]; evidence_class = 'synthetic';
            physical_profile_id = $metadata.physical_profile_id; candidate_id = $candidateId; run_index = '1';
            measurement_build_commit = $build; global_sample_index = [string]$checkpointIndexes[$i];
            display_mode_id = '1'; display_width_pixels = '1200'; display_height_pixels = '1920';
            refresh_rate_hertz = '90.0'; thermal_status = '0'; power_save_mode = 'false';
            interactive = 'true'; usb_powered = 'true'; battery_level_percent = '75'
        }
        $lines.Add((Convert-P4SyntheticRow $values))
    }
    $sampleNumber = 0
    foreach ($workload in $workloads) {
        for ($local = 1; $local -le 200; $local++) {
            $sampleNumber++
            $latency = $LatencyNanos
            if ($BoundaryLatencies) { $latency = if ($local -le 190) { 8000000 } else { 16670000 } }
            $blocking = if ($sampleNumber -le $BlockingRows) { '1' } else { '0' }
            $values = @{
                record_type = 'sample'; name = $workload; evidence_class = 'synthetic';
                physical_profile_id = $metadata.physical_profile_id; candidate_id = $candidateId; run_index = '1';
                measurement_build_commit = $build; canvas_width = '256'; canvas_height = '256';
                position_count = if ($workload -eq 'sparse_apply_stroke') { '256' } else { '65536' };
                warmup_iterations = '5'; sample_count = '200'; local_sample_index = [string]$local;
                global_sample_index = [string]$sampleNumber; latency_nanos = [string]$latency;
                art_allocated_bytes_before = '10'; art_allocated_bytes_after = '11'; art_allocated_bytes_delta = '1';
                art_gc_count_delta = '0'; art_gc_time_ms_delta = '0'; art_blocking_gc_count_delta = $blocking;
                art_blocking_gc_time_ms_delta = '0'; boundary = 'synthetic-boundary'
            }
            $noOp = $workload -eq 'dense_same_target_no_op'
            $undo = $workload -in @('dense_undo', 'palette_many_to_one_undo')
            $palette = $workload -in @(
                'palette_recolor_full', 'palette_default_only', 'palette_many_to_one_dense',
                'palette_many_to_one_undo', 'palette_many_to_one_redo'
            )
            $values.result_kind = if ($noOp) { 'rejected_no_effective_change' } else { 'applied' }
            $values.source_revision = if ($undo) { '1' } else { '0' }
            $values.revision_after = if ($undo -or $noOp) { '0' } else { '1' }
            $values.history_after = if ($undo) { 'redo_available' } elseif ($noOp) { 'none' } else { 'undo_available' }
            if ($Role -eq 'baseline') { $values.definition_transition = 'not_applicable' }
            else {
                $values.definition_transition = if ($palette) { 'changed' } else { 'unchanged' }
                $values.default_index_before = if ($palette) { '0' } else { '2' }
                $values.default_index_after = if ($workload -eq 'palette_default_only') { '255' } else { $values.default_index_before }
                $values.expected_definition_identity = 'true'
            }
            $values.unchanged_state_identity = if ($noOp) { 'true' } else { 'false' }
            if (-not $noOp) {
                $values.change_set_before_revision = if ($undo) { '1' } else { '0' }
                $values.change_set_after_revision = if ($undo) { '0' } else { '1' }
                $values.render_invalidation_origin_x = '0'; $values.render_invalidation_origin_y = '0'
                $values.render_invalidation_width = '256'; $values.render_invalidation_height = '256'
            }
            $lines.Add((Convert-P4SyntheticRow $values))
        }
    }
    return [pscustomobject]@{ Lines = $lines.ToArray(); BuildCommit = $build }
}

function Assert-P4CommandRejects {
    param([Parameter(Mandatory = $true)][scriptblock]$Action, [Parameter(Mandatory = $true)][string]$Case)
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw "Negative command fixture was accepted: $Case" }
}

function Set-P4SyntheticField {
    param([Parameter(Mandatory = $true)][string[]]$Lines, [Parameter(Mandatory = $true)][int]$Row,
        [Parameter(Mandatory = $true)][string]$Field, [Parameter(Mandatory = $true)][string]$Value)
    $copy = [string[]]$Lines.Clone()
    $parts = @($copy[$Row] -split ',')
    $fieldIndex = [Array]::IndexOf([object[]]$P4SyntheticColumns, $Field)
    $parts[$fieldIndex] = '"' + $Value.Replace('"', '""') + '"'
    $copy[$Row] = $parts -join ','
    return $copy
}

$commit = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
$candidate = New-P4SyntheticCommandCapture candidate
$candidateResult = Test-P4CommandCapture $candidate.Lines candidate $commit
if ($candidateResult.verdict -cne 'pass' -or $candidateResult.sample_count -ne 2200 -or $candidateResult.workload_count -ne 11) {
    throw 'Valid candidate command result identity drifted.'
}

$baseline = New-P4SyntheticCommandCapture baseline
$baselineResult = Test-P4CommandCapture $baseline.Lines baseline $commit
if ($baselineResult.verdict -cne 'pass' -or $baselineResult.sample_count -ne 1200 -or $baselineResult.workload_count -ne 6) {
    throw 'Valid baseline command overlay was rejected.'
}

$boundary = New-P4SyntheticCommandCapture candidate -BoundaryLatencies
if ((Test-P4CommandCapture $boundary.Lines candidate $commit).verdict -cne 'pass') {
    throw 'Nearest-rank threshold boundary must pass.'
}
$numericMiss = New-P4SyntheticCommandCapture candidate -LatencyNanos 9000000
if ((Test-P4CommandCapture $numericMiss.Lines candidate $commit).verdict -cne 'PERFORMANCE_FAIL') {
    throw 'A valid numeric threshold miss must be PERFORMANCE_FAIL.'
}
$gcMiss = New-P4SyntheticCommandCapture candidate -BlockingRows 11
if ((Test-P4CommandCapture $gcMiss.Lines candidate $commit).verdict -cne 'PERFORMANCE_FAIL') {
    throw 'A valid ART blocking-GC miss must be PERFORMANCE_FAIL.'
}

$truncated = [string[]]$candidate.Lines[0..($candidate.Lines.Count - 2)]
Assert-P4CommandRejects { Test-P4CommandCapture $truncated candidate $commit } 'truncated rows'
Assert-P4CommandRejects { Test-P4CommandCapture ($candidate.Lines + 'extra') candidate $commit } 'extra row'
Assert-P4CommandRejects { Test-P4CommandCapture (Set-P4SyntheticField $candidate.Lines 1 'name' 'wrong') candidate $commit } 'identity/order'
Assert-P4CommandRejects { Test-P4CommandCapture (Set-P4SyntheticField $candidate.Lines 130 'global_sample_index' '999') candidate $commit } 'sample index'
Assert-P4CommandRejects { Test-P4CommandCapture (Set-P4SyntheticField $candidate.Lines 130 'latency_nanos' 'NaN') candidate $commit } 'non-finite latency'
Assert-P4CommandRejects { Test-P4CommandCapture (Set-P4SyntheticField $candidate.Lines 130 'art_blocking_gc_count_delta' '-1') candidate $commit } 'negative ART delta'
Assert-P4CommandRejects { Test-P4CommandCapture (Set-P4SyntheticField $candidate.Lines 130 'document_hash' '123') candidate $commit } 'sample hash population'
Assert-P4CommandRejects { Test-P4CommandCapture (Set-P4SyntheticField $candidate.Lines 24 'document_hash' '') candidate $commit } 'correctness hash absence'
Assert-P4CommandRejects { Test-P4CommandCapture (Set-P4SyntheticField $candidate.Lines 1 'record_type' 'sample') candidate $commit } 'record order'
Assert-P4CommandRejects { Test-P4CommandCapture (Set-P4SyntheticField $candidate.Lines 130 'local_sample_index' '4') candidate $commit } 'duplicate local index'
$swapped = [string[]]$candidate.Lines.Clone()
$swapped[126], $swapped[127] = $swapped[127], $swapped[126]
Assert-P4CommandRejects { Test-P4CommandCapture $swapped candidate $commit } 'reordered sample rows'
Assert-P4CommandRejects { Test-P4CommandCapture (Set-P4SyntheticField $candidate.Lines 726 'result_kind' 'applied') candidate $commit } 'cheap outcome fact'
Assert-P4CommandRejects { Test-P4CommandCapture (Set-P4SyntheticField $candidate.Lines 130 'candidate_id' 'other-candidate') candidate $commit } 'row candidate identity'
Assert-P4CommandRejects { Test-P4CommandCapture (Set-P4SyntheticField $candidate.Lines 130 'measurement_build_commit' ('b' * 40)) candidate $commit } 'row source commit'
Assert-P4CommandRejects { Test-P4CommandCapture (Set-P4SyntheticField $candidate.Lines 36 'refresh_rate_hertz' '88.0') candidate $commit } 'checkpoint profile'

$wrongHeader = [string[]]$candidate.Lines.Clone()
$wrongHeader[0] = $wrongHeader[0].Replace('"latency_nanos"', '"latency"')
Assert-P4CommandRejects { Test-P4CommandCapture $wrongHeader candidate $commit } 'schema/header'

Write-Output 'P4_COMMAND_NO_DEVICE_VALIDATION=pass'
Write-Output 'CASES=valid-baseline,valid-candidate,nearest-rank-boundary,numeric-performance-fail,art-performance-fail,truncated,extra,identity,index,nonfinite,art-negative,sample-hash,correctness-hash,order,duplicate,reordered,cheap-outcome,row-identity,row-commit,checkpoint-profile,header'

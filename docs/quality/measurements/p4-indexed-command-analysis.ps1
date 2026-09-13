Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-P4CommandInt64 {
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][string]$Name,
        [bool]$AllowZero = $false
    )
    if ($Text -cnotmatch '^[0-9]+$') { throw "Invalid command integer: $Name=$Text" }
    $value = [long]0
    if (-not [long]::TryParse($Text, [ref]$value) -or (-not $AllowZero -and $value -le [long]0)) {
        throw "Invalid command integer: $Name=$Text"
    }
    return $value
}

function Read-P4CommandInt32 {
    param([Parameter(Mandatory = $true)][string]$Text, [Parameter(Mandatory = $true)][string]$Name)
    $value = 0
    if ($Text -cnotmatch '^-?[0-9]+$' -or -not [int]::TryParse($Text, [ref]$value)) {
        throw "Invalid command 32-bit integer: $Name=$Text"
    }
    return $value
}

function Assert-P4CommandField {
    param(
        [Parameter(Mandatory = $true)]$Row,
        [Parameter(Mandatory = $true)][string]$Name,
        [AllowNull()][string]$Expected
    )
    $actual = [string]$Row.$Name
    if ($actual -cne $Expected) { throw "Command field mismatch: $Name=$actual expected=$Expected" }
}

function Assert-P4CommandIdentity {
    param([Parameter(Mandatory = $true)]$Row, [Parameter(Mandatory = $true)]$Metadata)
    foreach ($name in @('evidence_class', 'physical_profile_id', 'candidate_id', 'run_index', 'measurement_build_commit')) {
        Assert-P4CommandField $Row $name ([string]$Metadata[$name])
    }
}

function Assert-P4CommandPresent {
    param([Parameter(Mandatory = $true)]$Row, [Parameter(Mandatory = $true)][string]$Name)
    if ([string]::IsNullOrWhiteSpace([string]$Row.$Name)) { throw "Command field is empty: $Name" }
}

function Get-P4CommandWorkloads {
    param([Parameter(Mandatory = $true)][ValidateSet('baseline', 'candidate')][string]$Role)
    $common = @(
        'sparse_apply_stroke', 'dense_apply_stroke', 'dense_eraser_stroke',
        'dense_same_target_no_op', 'dense_undo', 'dense_redo'
    )
    if ($Role -eq 'baseline') { return $common }
    return $common + @(
        'palette_recolor_full', 'palette_default_only', 'palette_many_to_one_dense',
        'palette_many_to_one_undo', 'palette_many_to_one_redo'
    )
}

function Assert-P4CommandOutcome {
    param(
        [Parameter(Mandatory = $true)]$Row,
        [Parameter(Mandatory = $true)][string]$Workload,
        [Parameter(Mandatory = $true)][ValidateSet('baseline', 'candidate')][string]$Role
    )
    $noOp = $Workload -eq 'dense_same_target_no_op'
    $undo = $Workload -in @('dense_undo', 'palette_many_to_one_undo')
    $palette = $Workload -in @(
        'palette_recolor_full', 'palette_default_only', 'palette_many_to_one_dense',
        'palette_many_to_one_undo', 'palette_many_to_one_redo'
    )
    $sourceRevision = if ($undo) { '1' } else { '0' }
    $revision = if ($undo -or $noOp) { '0' } else { '1' }
    $history = if ($undo) { 'redo_available' } elseif ($noOp) { 'none' } else { 'undo_available' }
    Assert-P4CommandField $Row 'result_kind' $(if ($noOp) { 'rejected_no_effective_change' } else { 'applied' })
    Assert-P4CommandField $Row 'source_revision' $sourceRevision
    Assert-P4CommandField $Row 'revision_after' $revision
    Assert-P4CommandField $Row 'history_after' $history
    if ($Role -eq 'baseline') {
        Assert-P4CommandField $Row 'definition_transition' 'not_applicable'
        Assert-P4CommandField $Row 'default_index_before' ''
        Assert-P4CommandField $Row 'default_index_after' ''
        Assert-P4CommandField $Row 'expected_definition_identity' ''
    } else {
        Assert-P4CommandField $Row 'definition_transition' $(if ($palette) { 'changed' } else { 'unchanged' })
        $defaultBefore = if ($palette) { '0' } else { '2' }
        $defaultAfter = if ($Workload -eq 'palette_default_only') { '255' } else { $defaultBefore }
        Assert-P4CommandField $Row 'default_index_before' $defaultBefore
        Assert-P4CommandField $Row 'default_index_after' $defaultAfter
        Assert-P4CommandField $Row 'expected_definition_identity' 'true'
    }
    Assert-P4CommandField $Row 'unchanged_state_identity' $(if ($noOp) { 'true' } else { 'false' })
    if ($noOp) {
        foreach ($name in @(
                'change_set_before_revision', 'change_set_after_revision',
                'render_invalidation_origin_x', 'render_invalidation_origin_y',
                'render_invalidation_width', 'render_invalidation_height'
            )) { Assert-P4CommandField $Row $name '' }
    } else {
        $before = if ($undo) { '1' } else { '0' }
        $after = if ($undo) { '0' } else { '1' }
        Assert-P4CommandField $Row 'change_set_before_revision' $before
        Assert-P4CommandField $Row 'change_set_after_revision' $after
        foreach ($name in @('render_invalidation_origin_x', 'render_invalidation_origin_y')) {
            Assert-P4CommandField $Row $name '0'
        }
        foreach ($name in @('render_invalidation_width', 'render_invalidation_height')) {
            Assert-P4CommandField $Row $name '256'
        }
    }
}

function Assert-P4CommandSampleShape {
    param(
        [Parameter(Mandatory = $true)]$Row,
        [Parameter(Mandatory = $true)]$Metadata,
        [Parameter(Mandatory = $true)][string]$Workload,
        [Parameter(Mandatory = $true)][ValidateSet('baseline', 'candidate')][string]$Role,
        [Parameter(Mandatory = $true)][int]$LocalIndex,
        [Parameter(Mandatory = $true)][int]$GlobalIndex
    )
    Assert-P4CommandIdentity $Row $Metadata
    Assert-P4CommandField $Row 'record_type' 'sample'
    Assert-P4CommandField $Row 'name' $Workload
    Assert-P4CommandField $Row 'canvas_width' '256'
    Assert-P4CommandField $Row 'canvas_height' '256'
    Assert-P4CommandField $Row 'position_count' $(if ($Workload -eq 'sparse_apply_stroke') { '256' } else { '65536' })
    Assert-P4CommandField $Row 'warmup_iterations' '5'
    Assert-P4CommandField $Row 'sample_count' '200'
    Assert-P4CommandField $Row 'local_sample_index' ([string]$LocalIndex)
    Assert-P4CommandField $Row 'global_sample_index' ([string]$GlobalIndex)
    [void](Read-P4CommandInt64 $Row.latency_nanos 'latency_nanos')
    Assert-P4CommandField $Row 'document_hash' ''
    Assert-P4CommandField $Row 'snapshot_hash' ''
    Assert-P4CommandPresent $Row 'boundary'
    foreach ($name in @(
            'art_allocated_bytes_before', 'art_allocated_bytes_after', 'art_allocated_bytes_delta',
            'art_gc_count_delta', 'art_gc_time_ms_delta', 'art_blocking_gc_count_delta',
            'art_blocking_gc_time_ms_delta'
        )) { [void](Read-P4CommandInt64 $Row.$name $name $true) }
    foreach ($name in @(
            'post_gc_java_heap_used_bytes', 'post_gc_java_heap_committed_bytes', 'total_pss_kb',
            'dalvik_pss_kb', 'native_pss_kb', 'other_pss_kb', 'total_private_dirty_kb', 'total_shared_dirty_kb'
        )) { Assert-P4CommandField $Row $name '' }
    Assert-P4CommandOutcome $Row $Workload $Role
}

function Test-P4CommandCapture {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Lines,
        [Parameter(Mandatory = $true)][ValidateSet('baseline', 'candidate')][string]$Role,
        [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-f]{40}$')][string]$BuildCommit
    )
    $schema = 'nene-pixel-p4-indexed-command-latency-v1'
    $columns = @(
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
    $expectedHeader = ($columns | ForEach-Object { '"' + $_ + '"' }) -join ','
    if ($Lines.Count -lt 2 -or $Lines[0] -cne $expectedHeader) { throw 'Command capture header is missing or reordered.' }
    if (@($Lines | Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count -ne 0) {
        throw 'Command capture contains a blank row.'
    }
    try { $records = @($Lines | ConvertFrom-Csv) } catch { throw "Command CSV parsing failed: $($_.Exception.Message)" }
    if ($records.Count -ne ($Lines.Count - 1)) { throw 'Command CSV row population changed during parsing.' }
    foreach ($record in $records) {
        if ([string]$record.record_type -notin @('metadata', 'correctness', 'baseline', 'checkpoint', 'sample')) {
            throw "Unknown command record type: $($record.record_type)"
        }
    }
    $metadata = @{}
    foreach ($record in @($records | Where-Object record_type -eq 'metadata')) {
        $name = [string]$record.name
        if ([string]::IsNullOrWhiteSpace($name) -or $metadata.ContainsKey($name)) {
            throw 'Command metadata is empty or duplicated.'
        }
        $metadata[$name] = [string]$record.value
    }
    $commonMetadata = [ordered]@{
        schema = $schema; output_identity = "p4-indexed-command-$Role-run-01"; run_status = 'valid'
        candidate_id = "p4-indexed-command-$Role-v1"; run_index = '1'; measurement_build_commit = $BuildCommit
        app_variant = 'debug'; test_variant = 'debugAndroidTest'; physical_profile_id = 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36'
        canvas = '256x256'; warmup_iterations_per_workload = '5'; sample_count_per_workload = '200'
        sample_indices = ''; checkpoint_interval_global_samples = '25'
    }
    $workloads = @(Get-P4CommandWorkloads $Role)
    $totalSamples = $workloads.Count * 200
    $commonMetadata.sample_count_total = [string]$totalSamples
    $commonMetadata.workload_order = $workloads -join '|'
    $commonMetadata.sample_indices = "local=1..200;global=1..$totalSamples"
    $commonMetadata.checkpoint_row_count = [string](2 + ($totalSamples / 25))
    foreach ($entry in $commonMetadata.GetEnumerator()) {
        if (-not $metadata.ContainsKey($entry.Key) -or $metadata[$entry.Key] -cne $entry.Value) {
            throw "Command metadata mismatch: $($entry.Key)"
        }
    }
    foreach ($name in @('evidence_class', 'build_fingerprint', 'security_patch', 'runtime_max_memory_bytes', 'memory_class_mib')) {
        if (-not $metadata.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($metadata[$name])) {
            throw "Required command metadata is missing: $name"
        }
    }
    if ($metadata.build_fingerprint -cne 'ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94111:user/release-keys' -or
        $metadata.security_patch -cne '2026-08-05' -or $metadata.runtime_max_memory_bytes -cne '268435456' -or
        $metadata.memory_class_mib -cne '256') { throw 'Physical command profile metadata mismatch.' }
    $metadataRecords = @($records | Where-Object record_type -eq 'metadata')
    $firstNonMetadata = [Array]::FindIndex([object[]]$records, [Predicate[object]]{
            param($item) $item.record_type -ne 'metadata'
        })
    if ($firstNonMetadata -lt 0 -or @($records | Select-Object -Skip $firstNonMetadata | Where-Object record_type -eq 'metadata').Count -ne 0) {
        throw 'Command metadata must be a single leading block.'
    }
    $correctness = @($records | Where-Object record_type -eq 'correctness')
    $baseline = @($records | Where-Object record_type -eq 'baseline')
    $checkpoints = @($records | Where-Object record_type -eq 'checkpoint')
    $samples = @($records | Where-Object record_type -eq 'sample')
    if ($correctness.Count -ne $workloads.Count -or $baseline.Count -ne 1 -or
        $checkpoints.Count -ne (2 + $totalSamples / 25) -or $samples.Count -ne $totalSamples) {
        throw 'Command correctness, checkpoint, or sample population is incomplete.'
    }
    $expectedTypes = (@('correctness') * $workloads.Count) + @('baseline') +
        (@('checkpoint') * (2 + $totalSamples / 25)) + (@('sample') * $totalSamples)
    $actualTypes = @($records | Select-Object -Skip $firstNonMetadata | ForEach-Object { [string]$_.record_type })
    if (($actualTypes -join ',') -cne ($expectedTypes -join ',')) {
        throw 'Command record type order is not correctness, baseline, checkpoint, sample.'
    }
    for ($i = 0; $i -lt $workloads.Count; $i++) {
        $row = $correctness[$i]
        Assert-P4CommandIdentity $row $metadata
        Assert-P4CommandField $row 'record_type' 'correctness'
        Assert-P4CommandField $row 'name' $workloads[$i]
        Assert-P4CommandField $row 'canvas_width' '256'
        Assert-P4CommandField $row 'canvas_height' '256'
        Assert-P4CommandField $row 'position_count' $(if ($workloads[$i] -eq 'sparse_apply_stroke') { '256' } else { '65536' })
        if ([string]$row.document_hash -notmatch '^-?[0-9]+$' -or [string]$row.snapshot_hash -notmatch '^-?[0-9]+$') {
            throw 'Correctness rows require integer document and snapshot hashes.'
        }
        Assert-P4CommandPresent $row 'boundary'
        Assert-P4CommandOutcome $row $workloads[$i] $Role
    }
    $baselineRow = $baseline[0]
    Assert-P4CommandIdentity $baselineRow $metadata
    Assert-P4CommandField $baselineRow 'name' 'process_post_gc_before_samples'
        foreach ($name in @(
            'post_gc_java_heap_used_bytes', 'post_gc_java_heap_committed_bytes', 'total_pss_kb',
            'dalvik_pss_kb', 'native_pss_kb', 'other_pss_kb', 'total_private_dirty_kb', 'total_shared_dirty_kb'
        )) { [void](Read-P4CommandInt64 $baselineRow.$name $name $true) }
    Assert-P4CommandPresent $baselineRow 'boundary'
    $expectedCheckpoints = @('before_samples') + @(25..$totalSamples | Where-Object { $_ % 25 -eq 0 } | ForEach-Object { "after_$_" }) +
        @('after_samples')
    $expectedCheckpointIndexes = @(0) + @(25..$totalSamples | Where-Object { $_ % 25 -eq 0 }) + @($totalSamples)
    for ($i = 0; $i -lt $checkpoints.Count; $i++) {
        $row = $checkpoints[$i]
        Assert-P4CommandIdentity $row $metadata
        Assert-P4CommandField $row 'record_type' 'checkpoint'
        Assert-P4CommandField $row 'name' $expectedCheckpoints[$i]
        Assert-P4CommandField $row 'global_sample_index' ([string]$expectedCheckpointIndexes[$i])
        foreach ($name in @('display_mode_id', 'display_width_pixels', 'display_height_pixels', 'refresh_rate_hertz',
                'thermal_status', 'power_save_mode', 'interactive', 'usb_powered', 'battery_level_percent')) {
            Assert-P4CommandPresent $row $name
        }
        Assert-P4CommandField $row 'display_mode_id' '1'
        Assert-P4CommandField $row 'display_width_pixels' '1200'
        Assert-P4CommandField $row 'display_height_pixels' '1920'
        $refreshText = [string]$row.refresh_rate_hertz
        if ($refreshText -notmatch '^[0-9]+(\.[0-9]+)?$') { throw 'Checkpoint refresh rate is not finite numeric data.' }
        $refresh = [double]::Parse($refreshText, [Globalization.CultureInfo]::InvariantCulture)
        if ([double]::IsNaN($refresh) -or [double]::IsInfinity($refresh) -or [Math]::Abs($refresh - 90.0) -gt 0.5) {
            throw 'Checkpoint refresh rate is outside the P4 tolerance.'
        }
        $thermal = Read-P4CommandInt32 $row.thermal_status 'thermal_status'
        if ($thermal -gt 1) { throw 'Checkpoint thermal status exceeds the P4 bound.' }
        Assert-P4CommandField $row 'power_save_mode' 'false'
        Assert-P4CommandField $row 'interactive' 'true'
        Assert-P4CommandField $row 'usb_powered' 'true'
        $battery = Read-P4CommandInt32 $row.battery_level_percent 'battery_level_percent'
        if ($battery -lt 0 -or $battery -gt 100) { throw 'Checkpoint battery level is outside 0..100.' }
    }
    $latencies = @{}
    $blockingZero = @{}
    for ($i = 0; $i -lt $samples.Count; $i++) {
        $workloadIndex = [Math]::Floor($i / 200)
        $localIndex = ($i % 200) + 1
        Assert-P4CommandSampleShape $samples[$i] $metadata $workloads[$workloadIndex] $Role $localIndex ($i + 1)
        $latency = Read-P4CommandInt64 $samples[$i].latency_nanos 'latency_nanos'
        $blocking = Read-P4CommandInt64 $samples[$i].art_blocking_gc_count_delta 'art_blocking_gc_count_delta' $true
        if (-not $latencies.ContainsKey($workloads[$workloadIndex])) {
            $latencies[$workloads[$workloadIndex]] = [Collections.Generic.List[long]]::new()
            $blockingZero[$workloads[$workloadIndex]] = 0
        }
        $latencies[$workloads[$workloadIndex]].Add($latency)
        if ($blocking -eq 0) { $blockingZero[$workloads[$workloadIndex]]++ }
    }
    $metrics = [ordered]@{}
    $numericMiss = $false
    foreach ($workload in $workloads) {
        $sorted = @($latencies[$workload] | Sort-Object)
        $p95 = $sorted[189]
        $p99 = $sorted[197]
        $passes = $p95 -le 8000000 -and $p99 -le 16670000 -and $blockingZero[$workload] -ge 190
        if (-not $passes) { $numericMiss = $true }
        $metrics[$workload] = [ordered]@{
            sample_count = 200; p95_nanos = $p95; p99_nanos = $p99
            zero_blocking_gc_count = $blockingZero[$workload]
        }
    }
    return [ordered]@{
        schema = $schema
        role = $Role
        candidate_id = $metadata.candidate_id
        run_index = 1
        source_commit = $BuildCommit
        workload_count = $workloads.Count
        sample_count = $totalSamples
        verdict = if ($numericMiss) { 'PERFORMANCE_FAIL' } else { 'pass' }
        workloads = $metrics
    }
}

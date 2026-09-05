[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$TracePath,

    [Parameter(Mandatory = $true)]
    [string]$FramesPath,

    [Parameter(Mandatory = $true)]
    [string]$TraceProcessorPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory,

    [ValidateRange(1, 200)]
    [int]$ExpectedSampleCount = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$schema = "nene-pixel-m2-physical-present-v2"
$packageName = "io.github.hideyukimori.nenepixel"
$surfaceFlingerProcess = "/system/bin/surfaceflinger"
$expectedTraceProcessorBytes = 10479616
$expectedTraceProcessorSha256 = "a881f3e2d4c6131493e85bfd1f36d1efe58e1478e2991825418d5d21614c1e48"
$expectedTraceProcessorVersion =
    "Perfetto v49.0-33a4fd078 (33a4fd07897a9a648664926ea27769278a19ff13)"

$resolvedTrace = (Resolve-Path -LiteralPath $TracePath).Path
$resolvedFrames = (Resolve-Path -LiteralPath $FramesPath).Path
$resolvedTraceProcessor = (Resolve-Path -LiteralPath $TraceProcessorPath).Path
$resolvedOutput = (Resolve-Path -LiteralPath $OutputDirectory).Path

if ((Get-Item -LiteralPath $resolvedTrace).Length -le 0) {
    throw "Physical-present analysis requires a non-empty closed trace."
}

$traceProcessorItem = Get-Item -LiteralPath $resolvedTraceProcessor
$traceProcessorHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedTraceProcessor).Hash.ToLowerInvariant()
if (
    $traceProcessorItem.Length -ne $expectedTraceProcessorBytes -or
    $traceProcessorHash -ne $expectedTraceProcessorSha256
) {
    throw "Physical-present analysis requires the pinned Trace Processor v49.0 binary."
}
$traceProcessorVersion = @(& $resolvedTraceProcessor --version 2>&1)
if ($LASTEXITCODE -ne 0 -or $traceProcessorVersion.Count -eq 0 -or $traceProcessorVersion[0] -ne $expectedTraceProcessorVersion) {
    throw "Pinned Trace Processor version output is unavailable or unexpected."
}

$artifactNames = @(
    "physical-present-correlation.sql",
    "physical-present-correlation.csv",
    "physical-present-samples.csv",
    "physical-present-scheduler.sql",
    "physical-present-scheduler.csv",
    "physical-present-workloads.sql",
    "physical-present-workloads.csv",
    "physical-present-stats.sql",
    "physical-present-stats.csv",
    "physical-present-clock-snapshots.sql",
    "physical-present-clock-snapshots.csv",
    "physical-present-trace-processor.log",
    "physical-present-metadata.txt"
)
foreach ($artifactName in $artifactNames) {
    $artifactPath = Join-Path $resolvedOutput $artifactName
    if (Test-Path -LiteralPath $artifactPath) {
        throw "Physical-present analysis refuses to overwrite $artifactPath."
    }
}

$frames = @(Import-Csv -LiteralPath $resolvedFrames)
if ($frames.Count -lt $ExpectedSampleCount) {
    throw "Physical-present analysis expected at least $ExpectedSampleCount complete frame rows; found $($frames.Count)."
}

$requests = [System.Collections.Generic.List[object]]::new()
foreach ($frame in $frames) {
    $sampleIndex = 0
    $rowIndex = 0
    $surfaceFrameToken = 0L
    $inputStartNanos = 0L
    $gfxFrameOverrunNanos = 0L
    if (
        -not [int]::TryParse($frame.sample_index, [ref]$sampleIndex) -or
        -not [int]::TryParse($frame.row_index, [ref]$rowIndex) -or
        -not [long]::TryParse($frame.frame_timeline_vsync_id, [ref]$surfaceFrameToken) -or
        -not [long]::TryParse($frame.handle_input_start_nanos, [ref]$inputStartNanos) -or
        -not [long]::TryParse($frame.frame_deadline_nanos, [ref]$gfxFrameOverrunNanos)
    ) {
        throw "Physical-present input contains a non-integral required frame field."
    }
    $frameCompletedNanos = 0L
    if (-not [long]::TryParse($frame.frame_completed_nanos, [ref]$frameCompletedNanos)) {
        throw "Physical-present input contains a non-integral frame completion field."
    }
    if ($sampleIndex -le 0 -or $rowIndex -le 0 -or $surfaceFrameToken -le 0 -or $inputStartNanos -le 0) {
        throw "Physical-present input requires positive sample, row, FrameTimeline, and input-start values."
    }
    $requests.Add(
        [pscustomobject]@{
            sample_index = $sampleIndex
            row_index = $rowIndex
            surface_frame_token = $surfaceFrameToken
            input_start_nanos = $inputStartNanos
            gfx_frame_overrun_nanos = $frameCompletedNanos - $gfxFrameOverrunNanos
        }
    )
}

$expectedIndexes = @(1..$ExpectedSampleCount)
$actualIndexes = @($requests.sample_index | Sort-Object -Unique)
if (($actualIndexes -join ",") -ne ($expectedIndexes -join ",")) {
    throw "Physical-present input sample indexes are not the exact contiguous requested population."
}
foreach ($sampleIndex in $expectedIndexes) {
    $sampleRequests = @($requests | Where-Object { $_.sample_index -eq $sampleIndex } | Sort-Object row_index)
    $sampleRowIndexes = @($sampleRequests.row_index)
    $expectedRowIndexes = @(1..$sampleRequests.Count)
    if ($sampleRequests.Count -lt 1 -or ($sampleRowIndexes -join ",") -ne ($expectedRowIndexes -join ",")) {
        throw "Physical-present input sample $sampleIndex does not contain a contiguous complete row population."
    }
}
if (@($requests.surface_frame_token | Sort-Object -Unique).Count -ne $requests.Count) {
    throw "Physical-present input FrameTimeline vsync IDs must be positive and unique."
}

$requestedRows = @(
    $requests |
        Sort-Object sample_index, row_index |
        ForEach-Object {
            "SELECT $($_.sample_index) AS sample_index, $($_.row_index) AS row_index, " +
                "$($_.surface_frame_token) AS surface_frame_token, " +
                "$($_.input_start_nanos) AS input_start_nanos, $($_.gfx_frame_overrun_nanos) AS gfx_frame_overrun_nanos"
        }
) -join "`nUNION ALL`n"

$correlationSql = @"
-- Schema: $schema
-- The requested population is copied verbatim from the completed schema-v5 frames.csv.
WITH requested_samples AS (
$requestedRows
),
app_actual_candidates AS (
  SELECT a.*
  FROM actual_frame_timeline_slice a
  JOIN process p USING (upid)
  WHERE p.name = '$packageName'
    AND EXISTS (
      SELECT 1 FROM args
      WHERE args.arg_set_id = a.arg_set_id
        AND args.key = 'Is Buffer?'
        AND args.string_value = 'Yes'
    )
),
app_actual AS (
  SELECT
    r.sample_index,
    r.row_index,
    COUNT(a.id) AS app_actual_count,
    MIN(a.ts) AS app_actual_start_trace_ns,
    MIN(a.dur) AS app_actual_duration_ns,
    MIN(a.ts + a.dur) AS app_actual_end_trace_ns,
    MIN(a.display_frame_token) AS display_frame_token,
    MIN(a.layer_name) AS app_layer_name,
    MIN(a.present_type) AS app_present_type,
    MIN(a.on_time_finish) AS app_on_time_finish,
    MIN(a.gpu_composition) AS app_gpu_composition,
    MIN(a.jank_type) AS app_jank_type,
    MIN(a.jank_severity_type) AS app_jank_severity_type,
    MIN(a.prediction_type) AS app_prediction_type
  FROM requested_samples r
  LEFT JOIN app_actual_candidates a ON a.surface_frame_token = r.surface_frame_token
  GROUP BY r.sample_index, r.row_index
),
app_expected AS (
  SELECT
    r.sample_index,
    r.row_index,
    COUNT(e.id) AS app_expected_count,
    MIN(e.ts) AS app_expected_start_trace_ns,
    MIN(e.dur) AS app_expected_duration_ns,
    MIN(e.ts + e.dur) AS app_expected_end_trace_ns
  FROM requested_samples r
  LEFT JOIN expected_frame_timeline_slice e ON e.surface_frame_token = r.surface_frame_token
  LEFT JOIN process p ON p.upid = e.upid AND p.name = '$packageName'
  WHERE e.id IS NULL OR p.upid IS NOT NULL
  GROUP BY r.sample_index, r.row_index
),
sf_actual AS (
  SELECT
    a.sample_index,
    a.row_index,
    COUNT(s.id) AS sf_actual_count,
    MIN(s.ts) AS sf_actual_start_trace_ns,
    MIN(s.dur) AS sf_actual_duration_ns,
    MIN(s.ts + s.dur) AS sf_actual_end_trace_ns,
    MIN(s.present_type) AS sf_present_type,
    MIN(s.on_time_finish) AS sf_on_time_finish,
    MIN(s.gpu_composition) AS sf_gpu_composition,
    MIN(s.jank_type) AS sf_jank_type,
    MIN(s.jank_severity_type) AS sf_jank_severity_type,
    MIN(s.prediction_type) AS sf_prediction_type
  FROM app_actual a
  LEFT JOIN actual_frame_timeline_slice s ON s.display_frame_token = a.display_frame_token
  LEFT JOIN process p ON p.upid = s.upid AND p.name = '$surfaceFlingerProcess'
  WHERE s.id IS NULL OR p.upid IS NOT NULL
  GROUP BY a.sample_index, a.row_index
),
sf_expected AS (
  SELECT
    a.sample_index,
    a.row_index,
    COUNT(e.id) AS sf_expected_count,
    MIN(e.ts) AS sf_expected_start_trace_ns,
    MIN(e.dur) AS sf_expected_duration_ns,
    MIN(e.ts + e.dur) AS sf_expected_end_trace_ns
  FROM app_actual a
  LEFT JOIN expected_frame_timeline_slice e ON e.display_frame_token = a.display_frame_token
  LEFT JOIN process p ON p.upid = e.upid AND p.name = '$surfaceFlingerProcess'
  WHERE e.id IS NULL OR p.upid IS NOT NULL
  GROUP BY a.sample_index, a.row_index
)
SELECT
  r.sample_index,
  r.row_index,
  r.surface_frame_token AS app_surface_frame_token,
  r.input_start_nanos,
  r.gfx_frame_overrun_nanos,
  a.app_actual_count,
  e.app_expected_count,
  a.display_frame_token,
  a.app_layer_name,
  a.app_actual_start_trace_ns,
  a.app_actual_duration_ns,
  a.app_actual_end_trace_ns,
  e.app_expected_start_trace_ns,
  e.app_expected_duration_ns,
  e.app_expected_end_trace_ns,
  a.app_actual_end_trace_ns - e.app_expected_end_trace_ns AS app_frame_overrun_nanos,
  a.app_present_type,
  a.app_on_time_finish,
  a.app_gpu_composition,
  a.app_jank_type,
  a.app_jank_severity_type,
  a.app_prediction_type,
  s.sf_actual_count,
  x.sf_expected_count,
  s.sf_actual_start_trace_ns,
  s.sf_actual_duration_ns,
  s.sf_actual_end_trace_ns,
  x.sf_expected_start_trace_ns,
  x.sf_expected_duration_ns,
  x.sf_expected_end_trace_ns,
  s.sf_actual_end_trace_ns - x.sf_expected_end_trace_ns AS sf_frame_overrun_nanos,
  s.sf_present_type,
  s.sf_on_time_finish,
  s.sf_gpu_composition,
  s.sf_jank_type,
  s.sf_jank_severity_type,
  s.sf_prediction_type,
  to_monotonic(s.sf_actual_end_trace_ns) AS physical_present_end_monotonic_ns,
  to_monotonic(s.sf_actual_end_trace_ns) - r.input_start_nanos AS input_start_to_physical_present_nanos
FROM requested_samples r
JOIN app_actual a USING (sample_index, row_index)
JOIN app_expected e USING (sample_index, row_index)
JOIN sf_actual s USING (sample_index, row_index)
JOIN sf_expected x USING (sample_index, row_index)
ORDER BY r.sample_index, r.row_index;
"@

$schedulerSql = @"
-- Schema: $schema scheduler attribution
WITH requested_samples AS (
$requestedRows
),
app_frames AS (
  SELECT r.sample_index, r.row_index, a.ts AS app_start, a.ts + a.dur AS app_end, a.display_frame_token
  FROM requested_samples r
  JOIN actual_frame_timeline_slice a ON a.surface_frame_token = r.surface_frame_token
  JOIN process p ON p.upid = a.upid AND p.name = '$packageName'
  WHERE EXISTS (
    SELECT 1 FROM args
    WHERE args.arg_set_id = a.arg_set_id
      AND args.key = 'Is Buffer?'
      AND args.string_value = 'Yes'
  )
),
windows AS (
  SELECT sample_index, row_index, 'app' AS window_kind, app_start AS window_start, app_end AS window_end
  FROM app_frames
  UNION ALL
  SELECT a.sample_index, a.row_index, 'surfaceflinger', s.ts, s.ts + s.dur
  FROM app_frames a
  JOIN actual_frame_timeline_slice s ON s.display_frame_token = a.display_frame_token
  JOIN process p ON p.upid = s.upid AND p.name = '$surfaceFlingerProcess'
),
relevant_threads AS (
  SELECT t.utid, t.name AS thread_name, t.tid, t.is_main_thread, p.name AS process_name, p.pid
  FROM thread t
  JOIN process p USING (upid)
  WHERE
    (p.name = '$packageName' AND (t.is_main_thread = 1 OR t.name = 'RenderThread'))
    OR p.name = '$surfaceFlingerProcess'
)
SELECT
  w.sample_index,
  w.row_index,
  w.window_kind,
  rt.process_name,
  rt.pid,
  rt.thread_name,
  rt.tid,
  rt.is_main_thread,
  st.state,
  st.io_wait,
  SUM(MIN(st.ts + st.dur, w.window_end) - MAX(st.ts, w.window_start)) AS overlap_duration_ns
FROM windows w
JOIN relevant_threads rt ON
  (w.window_kind = 'app' AND rt.process_name = '$packageName')
  OR (w.window_kind = 'surfaceflinger' AND rt.process_name = '$surfaceFlingerProcess')
JOIN thread_state st ON st.utid = rt.utid
  AND st.dur > 0
  AND st.ts < w.window_end
  AND st.ts + st.dur > w.window_start
WHERE
  st.ts < w.window_end
GROUP BY w.sample_index, w.row_index, w.window_kind, rt.process_name, rt.pid, rt.thread_name, rt.tid, rt.is_main_thread, st.state, st.io_wait
ORDER BY w.sample_index, w.row_index, w.window_kind, rt.process_name, rt.thread_name, st.state;
"@

$workloadsSql = @"
-- Schema: $schema RenderThread, SurfaceFlinger, and HWC workload attribution
WITH requested_samples AS (
$requestedRows
),
windows AS (
  SELECT r.sample_index, r.row_index, a.ts AS window_start, s.ts + s.dur AS window_end
  FROM requested_samples r
  JOIN actual_frame_timeline_slice a ON a.surface_frame_token = r.surface_frame_token
  JOIN process app_process ON app_process.upid = a.upid AND app_process.name = '$packageName'
  JOIN actual_frame_timeline_slice s ON s.display_frame_token = a.display_frame_token
  JOIN process sf_process ON sf_process.upid = s.upid AND sf_process.name = '$surfaceFlingerProcess'
  WHERE EXISTS (
    SELECT 1 FROM args
    WHERE args.arg_set_id = a.arg_set_id
      AND args.key = 'Is Buffer?'
      AND args.string_value = 'Yes'
  )
),
relevant_tracks AS (
  SELECT
    tt.id AS track_id,
    p.name AS process_name,
    p.pid,
    t.name AS thread_name,
    t.tid
  FROM thread_track tt
  JOIN thread t USING (utid)
  JOIN process p USING (upid)
  WHERE
    p.name IN ('$packageName', '$surfaceFlingerProcess')
    OR LOWER(p.name) LIKE '%composer%'
    OR LOWER(t.name) LIKE '%composer%'
    OR LOWER(t.name) LIKE '%hwc%'
)
SELECT
  w.sample_index,
  w.row_index,
  rt.process_name,
  rt.pid,
  rt.thread_name,
  rt.tid,
  s.ts AS slice_start_trace_ns,
  s.dur AS slice_duration_ns,
  s.category,
  s.name AS slice_name,
  s.depth,
  s.parent_id
FROM windows w
CROSS JOIN relevant_tracks rt
JOIN slice s ON s.track_id = rt.track_id
  AND s.dur >= 0
  AND s.ts < w.window_end
  AND s.ts + MAX(s.dur, 1) > w.window_start
ORDER BY w.sample_index, w.row_index, s.ts, s.depth, s.id;
"@

$statsSql = @"
-- Schema: $schema trace-integrity statistics
SELECT name, severity, source, SUM(value) AS value
FROM stats
WHERE
  name IN (
    'frame_timeline_event_parser_errors',
    'frame_timeline_unpaired_end_event',
    'traced_buf_bytes_overwritten',
    'traced_buf_chunks_discarded',
    'traced_buf_chunks_overwritten',
    'traced_buf_incremental_sequences_dropped',
    'traced_buf_sequence_packet_loss',
    'traced_buf_trace_writer_packet_loss',
    'traced_buf_write_wrap_count',
    'traced_chunks_discarded',
    'traced_final_flush_failed',
    'traced_final_flush_succeeded',
    'traced_flushes_failed',
    'traced_patches_discarded'
  )
  OR name GLOB 'ftrace_cpu_commit_overrun_*'
  OR name GLOB 'ftrace_cpu_dropped_events_*'
  OR name GLOB 'ftrace_cpu_overrun_*'
  OR (severity IN ('data_loss', 'error') AND value != 0)
GROUP BY name, severity, source
ORDER BY name, severity, source;
"@

$clockSql = @"
-- Schema: $schema clock-snapshot evidence
SELECT snapshot_id, clock_id, clock_name, clock_value, machine_id
FROM clock_snapshot
ORDER BY snapshot_id, clock_id, machine_id;
"@

function Invoke-TraceProcessorQuery {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$Sql
    )

    $queryPath = Join-Path $resolvedOutput "physical-present-$Name.sql"
    $csvPath = Join-Path $resolvedOutput "physical-present-$Name.csv"
    $stderrPath = Join-Path $resolvedOutput "physical-present-$Name.trace-processor.log"
    [System.IO.File]::WriteAllText($queryPath, $Sql, [System.Text.UTF8Encoding]::new($false))
    $queryOutput = @(& $resolvedTraceProcessor --query-file $queryPath $resolvedTrace 2> $stderrPath)
    if ($LASTEXITCODE -ne 0) {
        throw "Trace Processor failed for physical-present $Name analysis."
    }
    if ($queryOutput.Count -eq 0) {
        throw "Trace Processor returned no CSV for physical-present $Name analysis."
    }
    [System.IO.File]::WriteAllLines($csvPath, [string[]]$queryOutput, [System.Text.UTF8Encoding]::new($false))
    return [pscustomobject]@{
        CsvPath = $csvPath
        StderrPath = $stderrPath
    }
}

$queryResults = [System.Collections.Generic.List[object]]::new()
$queryResults.Add((Invoke-TraceProcessorQuery -Name "correlation" -Sql $correlationSql))
$queryResults.Add((Invoke-TraceProcessorQuery -Name "scheduler" -Sql $schedulerSql))
$queryResults.Add((Invoke-TraceProcessorQuery -Name "workloads" -Sql $workloadsSql))
$queryResults.Add((Invoke-TraceProcessorQuery -Name "stats" -Sql $statsSql))
$queryResults.Add((Invoke-TraceProcessorQuery -Name "clock-snapshots" -Sql $clockSql))

$combinedTraceProcessorLog = Join-Path $resolvedOutput "physical-present-trace-processor.log"
$combinedLogLines = [System.Collections.Generic.List[string]]::new()
foreach ($queryResult in $queryResults) {
    $combinedLogLines.Add("[$([System.IO.Path]::GetFileName($queryResult.StderrPath))]")
    Get-Content -LiteralPath $queryResult.StderrPath | ForEach-Object { $combinedLogLines.Add($_) }
    Remove-Item -LiteralPath $queryResult.StderrPath
}
[System.IO.File]::WriteAllLines($combinedTraceProcessorLog, $combinedLogLines, [System.Text.UTF8Encoding]::new($false))

$correlationPath = Join-Path $resolvedOutput "physical-present-correlation.csv"
$schedulerPath = Join-Path $resolvedOutput "physical-present-scheduler.csv"
$statsPath = Join-Path $resolvedOutput "physical-present-stats.csv"
$correlations = @(Import-Csv -LiteralPath $correlationPath)
$schedulerRows = @(Import-Csv -LiteralPath $schedulerPath)
$statsRows = @(Import-Csv -LiteralPath $statsPath)
if ($correlations.Count -ne $requests.Count) {
    throw "Physical-present correlation must preserve all $($requests.Count) frame rows; found $($correlations.Count)."
}

$recognizedPresentTypes = @("Early Present", "On-time Present", "Late Present")
$stableLayer = $null
$physicalDurations = [System.Collections.Generic.List[long]]::new()
foreach ($row in $correlations) {
    foreach ($countName in @("app_actual_count", "app_expected_count", "sf_actual_count", "sf_expected_count")) {
        if ([int]$row.$countName -ne 1) {
            throw "Physical-present sample $($row.sample_index) has $countName=$($row.$countName); exactly one is required."
        }
    }
    if ([long]$row.display_frame_token -le 0) {
        throw "Physical-present sample $($row.sample_index) has no positive display-frame token."
    }
    if ([string]::IsNullOrWhiteSpace($row.app_layer_name)) {
        throw "Physical-present sample $($row.sample_index) has no app layer identity."
    }
    if ($null -eq $stableLayer) {
        $stableLayer = $row.app_layer_name
    }
    elseif ($row.app_layer_name -ne $stableLayer) {
        throw "Physical-present app layer identity changed within the requested population."
    }
    if ($row.app_present_type -notin $recognizedPresentTypes -or $row.sf_present_type -notin $recognizedPresentTypes) {
        throw "Physical-present sample $($row.sample_index) has a dropped, unknown, or unrecognized present type."
    }
    foreach ($durationName in @("app_actual_duration_ns", "app_expected_duration_ns", "sf_actual_duration_ns", "sf_expected_duration_ns")) {
        if ([long]$row.$durationName -lt 0) {
            throw "Physical-present sample $($row.sample_index) has a negative $durationName."
        }
    }
    if ([long]$row.physical_present_end_monotonic_ns -le 0 -or [long]$row.input_start_to_physical_present_nanos -le 0) {
        throw "Physical-present sample $($row.sample_index) has no positive monotonic presentation duration."
    }

    $appRunnable = [long](
        ($schedulerRows |
            Where-Object {
                $_.sample_index -eq $row.sample_index -and
                $_.row_index -eq $row.row_index -and
                $_.window_kind -eq "app" -and
                $_.state -in @("R", "R+")
            } |
            Measure-Object -Property overlap_duration_ns -Sum).Sum
    )
    $sfRunnable = [long](
        ($schedulerRows |
            Where-Object {
                $_.sample_index -eq $row.sample_index -and
                $_.row_index -eq $row.row_index -and
                $_.window_kind -eq "surfaceflinger" -and
                $_.state -in @("R", "R+")
            } |
            Measure-Object -Property overlap_duration_ns -Sum).Sum
    )
    $appLate = $row.app_present_type -eq "Late Present" -or [int]$row.app_on_time_finish -ne 1
    $sfLate = $row.sf_present_type -eq "Late Present" -or [int]$row.sf_on_time_finish -ne 1
    $classification = "on-time"
    if ($appLate) {
        $appOverrun = [long]$row.app_frame_overrun_nanos
        $classification =
            if ($appOverrun -le 0) {
                "unresolved"
            }
            elseif ($appRunnable -ge $appOverrun) {
                "scheduler"
            }
            else {
                "app"
            }
    }
    elseif ($sfLate) {
        $sfOverrun = [long]$row.sf_frame_overrun_nanos
        $classification =
            if ($sfOverrun -le 0) {
                "unresolved"
            }
            elseif ($sfRunnable -ge $sfOverrun) {
                "scheduler"
            }
            else {
                "surfaceflinger"
            }
    }
    $row | Add-Member -NotePropertyName app_relevant_thread_runnable_nanos -NotePropertyValue $appRunnable
    $row | Add-Member -NotePropertyName sf_thread_runnable_nanos -NotePropertyValue $sfRunnable
    $row | Add-Member -NotePropertyName attribution_class -NotePropertyValue $classification
}
$correlations | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath $correlationPath

$sampleRows = [System.Collections.Generic.List[object]]::new()
foreach ($sampleIndex in $expectedIndexes) {
    $sampleCorrelations = @($correlations | Where-Object { [int]$_.sample_index -eq $sampleIndex } | Sort-Object { [int]$_.row_index })
    $sampleRequestCount = @($requests | Where-Object { $_.sample_index -eq $sampleIndex }).Count
    if ($sampleCorrelations.Count -ne $sampleRequestCount -or $sampleCorrelations.Count -lt 1) {
        throw "Physical-present sample $sampleIndex does not preserve its complete correlated frame population."
    }
    $earliestInput = [long](
        ($sampleCorrelations | ForEach-Object { [long]$_.input_start_nanos } | Measure-Object -Minimum).Minimum
    )
    $latestPhysicalPresent = [long](
        ($sampleCorrelations |
            ForEach-Object { [long]$_.physical_present_end_monotonic_ns } |
            Measure-Object -Maximum).Maximum
    )
    $sampleDuration = $latestPhysicalPresent - $earliestInput
    if ($sampleDuration -le 0) {
        throw "Physical-present sample $sampleIndex has no positive conservative input-to-present duration."
    }
    $physicalDurations.Add($sampleDuration)
    $sampleClasses = @($sampleCorrelations.attribution_class | Sort-Object -Unique)
    $sampleRows.Add(
        [pscustomobject]@{
            sample_index = $sampleIndex
            correlated_frame_count = $sampleCorrelations.Count
            earliest_input_start_nanos = $earliestInput
            latest_physical_present_end_monotonic_ns = $latestPhysicalPresent
            input_start_to_last_physical_present_nanos = $sampleDuration
            input_start_to_last_physical_present_ms = $sampleDuration / 1000000.0
            attribution_class_set = $sampleClasses -join "+"
            app_surface_frame_tokens = $sampleCorrelations.app_surface_frame_token -join ";"
            display_frame_tokens = $sampleCorrelations.display_frame_token -join ";"
        }
    )
}
$samplesPath = Join-Path $resolvedOutput "physical-present-samples.csv"
$sampleRows | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath $samplesPath

$strictZeroStatNames = @(
    "frame_timeline_event_parser_errors",
    "frame_timeline_unpaired_end_event",
    "traced_buf_bytes_overwritten",
    "traced_buf_chunks_discarded",
    "traced_buf_chunks_overwritten",
    "traced_buf_incremental_sequences_dropped",
    "traced_buf_sequence_packet_loss",
    "traced_buf_trace_writer_packet_loss",
    "traced_buf_write_wrap_count",
    "traced_final_flush_failed",
    "traced_flushes_failed"
)
$invalidZeroStats = @(
    $statsRows |
        Where-Object {
            [long]$_.value -ne 0 -and
            (
                $_.severity -in @("data_loss", "error") -or
                $_.name -in $strictZeroStatNames -or
                $_.name -like "ftrace_cpu_commit_overrun_*" -or
                $_.name -like "ftrace_cpu_dropped_events_*" -or
                $_.name -like "ftrace_cpu_overrun_*"
            )
        }
)
if ($invalidZeroStats.Count -gt 0) {
    throw "Physical-present trace integrity statistics contain nonzero loss, parser, flush, or overrun values."
}
$requiredStats = @(
    "frame_timeline_event_parser_errors",
    "frame_timeline_unpaired_end_event",
    "traced_buf_incremental_sequences_dropped",
    "traced_buf_sequence_packet_loss",
    "traced_buf_trace_writer_packet_loss",
    "traced_final_flush_failed",
    "traced_final_flush_succeeded"
)
foreach ($requiredStat in $requiredStats) {
    if (@($statsRows | Where-Object { $_.name -eq $requiredStat }).Count -eq 0) {
        throw "Physical-present trace does not expose required integrity statistic $requiredStat."
    }
}
$finalFlush = @($statsRows | Where-Object { $_.name -eq "traced_final_flush_succeeded" })
if ($finalFlush.Count -ne 1 -or [long]$finalFlush[0].value -lt 1) {
    throw "Physical-present trace does not prove a successful final flush."
}

function Get-NearestRank {
    param(
        [Parameter(Mandatory = $true)]
        [long[]]$Values,

        [Parameter(Mandatory = $true)]
        [double]$Percentile
    )

    $sorted = @($Values | Sort-Object)
    return $sorted[[Math]::Ceiling($sorted.Count * $Percentile) - 1]
}

$physicalP50 = Get-NearestRank -Values @($physicalDurations) -Percentile 0.50
$physicalP95 = Get-NearestRank -Values @($physicalDurations) -Percentile 0.95
$physicalP99 = Get-NearestRank -Values @($physicalDurations) -Percentile 0.99
$traceItem = Get-Item -LiteralPath $resolvedTrace
$traceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedTrace).Hash.ToLowerInvariant()
$classCounts = @($correlations | Group-Object attribution_class | Sort-Object Name)
$minimumFramesPerSample = ($sampleRows.correlated_frame_count | Measure-Object -Minimum).Minimum
$maximumFramesPerSample = ($sampleRows.correlated_frame_count | Measure-Object -Maximum).Maximum
$serviceChunksDiscarded = [long](
    ($statsRows | Where-Object { $_.name -eq "traced_chunks_discarded" } | Measure-Object -Property value -Sum).Sum
)
$servicePatchesDiscarded = [long](
    ($statsRows | Where-Object { $_.name -eq "traced_patches_discarded" } | Measure-Object -Property value -Sum).Sum
)
$metadata = [System.Collections.Generic.List[string]]::new()
$metadata.Add("schema=$schema")
$metadata.Add("status=valid-attribution")
$metadata.Add("sample_count=$ExpectedSampleCount")
$metadata.Add("correlated_frame_count=$($correlations.Count)")
$metadata.Add("minimum_frames_per_sample=$minimumFramesPerSample")
$metadata.Add("maximum_frames_per_sample=$maximumFramesPerSample")
$metadata.Add("trace_bytes=$($traceItem.Length)")
$metadata.Add("trace_sha256=$traceHash")
$metadata.Add("trace_processor_bytes=$($traceProcessorItem.Length)")
$metadata.Add("trace_processor_sha256=$traceProcessorHash")
$metadata.Add("trace_processor_version=$expectedTraceProcessorVersion")
$metadata.Add("stable_app_layer=$stableLayer")
$metadata.Add("informational_service_chunks_discarded=$serviceChunksDiscarded")
$metadata.Add("informational_service_patches_discarded=$servicePatchesDiscarded")
$metadata.Add("physical_input_to_present_p50_ms=$('{0:F6}' -f ($physicalP50 / 1000000.0))")
$metadata.Add("physical_input_to_present_p95_ms=$('{0:F6}' -f ($physicalP95 / 1000000.0))")
$metadata.Add("physical_input_to_present_p99_ms=$('{0:F6}' -f ($physicalP99 / 1000000.0))")
foreach ($classCount in $classCounts) {
    $metadata.Add("attribution_$($classCount.Name)_count=$($classCount.Count)")
}
$metadata.Add("boundary=exact gfxinfo FrameTimeline surface token to app surface frame to SurfaceFlinger display frame to monotonic physical-present end")
$metadata.Add("limitation=ten-sample attribution population; does not replace the predeclared fifty-sample decision lane")
[System.IO.File]::WriteAllLines(
    (Join-Path $resolvedOutput "physical-present-metadata.txt"),
    $metadata,
    [System.Text.UTF8Encoding]::new($false)
)

[pscustomobject]@{
    Schema = $schema
    Status = "valid-attribution"
    TraceBytes = $traceItem.Length
    TraceSha256 = $traceHash
    PhysicalP50Milliseconds = $physicalP50 / 1000000.0
    PhysicalP95Milliseconds = $physicalP95 / 1000000.0
    PhysicalP99Milliseconds = $physicalP99 / 1000000.0
    StableAppLayer = $stableLayer
    CorrelatedFrameCount = $correlations.Count
    MinimumFramesPerSample = $minimumFramesPerSample
    MaximumFramesPerSample = $maximumFramesPerSample
    ClassCounts = ($classCounts | ForEach-Object { "$($_.Name):$($_.Count)" }) -join ","
}

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$TracePath,
    [Parameter(Mandatory = $true)][string]$FramesPath,
    [Parameter(Mandatory = $true)][string]$TraceProcessorPath,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$schema = "nene-pixel-m2-commit-front-half-attribution-v3"
$packageName = "io.github.hideyukimori.nenepixel"
$surfaceFlingerProcess = "/system/bin/surfaceflinger"
$expectedTraceProcessorSha256 = "a881f3e2d4c6131493e85bfd1f36d1efe58e1478e2991825418d5d21614c1e48"
$resolvedTrace = (Resolve-Path -LiteralPath $TracePath).Path
$resolvedFrames = (Resolve-Path -LiteralPath $FramesPath).Path
$resolvedTraceProcessor = (Resolve-Path -LiteralPath $TraceProcessorPath).Path
$resolvedOutput = (Resolve-Path -LiteralPath $OutputDirectory).Path

if ((Get-Item -LiteralPath $resolvedTrace).Length -le 0) {
    throw "Commit-front-half analysis requires a non-empty finalized trace."
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedTraceProcessor).Hash.ToLowerInvariant() -ne $expectedTraceProcessorSha256) {
    throw "Commit-front-half analysis requires the pinned Trace Processor v49.0 binary."
}
$artifactNames = @(
    "commit-front-half-correlation.sql", "commit-front-half-correlation.csv",
    "commit-front-half-thread-states.sql", "commit-front-half-thread-states.csv",
    "commit-front-half-sched.sql", "commit-front-half-sched.csv",
    "commit-front-half-slices.sql", "commit-front-half-slices.csv",
    "commit-front-half-integrity.sql", "commit-front-half-integrity.csv",
    "commit-front-half-analysis-metadata.txt", "commit-front-half-trace-processor.log"
)
foreach ($artifactName in $artifactNames) {
    if (Test-Path -LiteralPath (Join-Path $resolvedOutput $artifactName)) {
        throw "Commit-front-half analysis refuses to overwrite $artifactName."
    }
}

$frames = @(Import-Csv -LiteralPath $resolvedFrames)
if ($frames.Count -lt 20) {
    throw "Commit-front-half analysis requires every frame from 10 preview and commit phases."
}
$requests = [System.Collections.Generic.List[object]]::new()
foreach ($frame in $frames) {
    $sample = 0
    $row = 0
    $token = 0L
    if (
        -not [int]::TryParse($frame.sample_index, [ref]$sample) -or
        -not [int]::TryParse($frame.row_index, [ref]$row) -or
        -not [long]::TryParse($frame.frame_timeline_vsync_id, [ref]$token) -or
        $sample -le 0 -or $row -le 0 -or $token -le 0 -or
        $frame.phase -notin @("preview", "commit")
    ) {
        throw "Commit-front-half frames contain an invalid required identity."
    }
    $requests.Add(
        [pscustomobject]@{
            sample = $sample
            phase = $frame.phase
            row = $row
            token = $token
            late = if ([double]$frame.frame_overrun_ms -gt 0.0) { 1 } else { 0 }
            gfx_overrun_ns = [long][Math]::Round([double]$frame.frame_overrun_ms * 1000000.0)
            anim_to_traversal_ns = [long]$frame.perform_traversals_start_nanos - [long]$frame.animation_start_nanos
            draw_to_sync_queued_ns = [long]$frame.sync_queued_nanos - [long]$frame.draw_start_nanos
            issue_to_swap_ns = [long]$frame.swap_buffers_nanos - [long]$frame.issue_draw_commands_start_nanos
        }
    )
}
if ((@($requests.sample | Sort-Object -Unique) -join ",") -ne ((1..10) -join ",")) {
    throw "Commit-front-half sample indexes are not the fixed contiguous 1..10 population."
}
foreach ($sample in 1..10) {
    foreach ($phase in @("preview", "commit")) {
        $phaseRows = @($requests | Where-Object { $_.sample -eq $sample -and $_.phase -eq $phase } | Sort-Object row)
        if ($phaseRows.Count -lt 1 -or (($phaseRows.row -join ",") -ne ((1..$phaseRows.Count) -join ","))) {
            throw "Sample $sample $phase is absent or non-contiguous."
        }
    }
}
if (@($requests.token | Sort-Object -Unique).Count -ne $requests.Count) {
    throw "Commit-front-half FrameTimeline IDs must be globally unique."
}

$requestedRows = @(
    $requests | Sort-Object sample, phase, row | ForEach-Object {
        "SELECT $($_.sample) sample_index, '$($_.phase)' phase, $($_.row) row_index, " +
            "$($_.token) surface_frame_token, $($_.late) late, $($_.gfx_overrun_ns) gfx_overrun_ns, " +
            "$($_.anim_to_traversal_ns) gfx_anim_to_traversal_ns, " +
            "$($_.draw_to_sync_queued_ns) gfx_draw_to_sync_queued_ns, $($_.issue_to_swap_ns) gfx_issue_to_swap_ns"
    }
) -join "`nUNION ALL`n"

$correlationSql = @"
-- Schema: $schema; all requested preview/commit frames.
WITH requested AS (
$requestedRows
), app_actual AS (
  SELECT r.*, COUNT(a.id) app_actual_count, MIN(a.ts) app_start, MIN(a.ts + a.dur) app_end,
         MIN(a.display_frame_token) display_frame_token, MIN(a.present_type) app_present_type,
         MIN(a.on_time_finish) app_on_time_finish, MIN(a.gpu_composition) app_gpu_composition,
         MIN(a.jank_type) app_jank_type
  FROM requested r LEFT JOIN actual_frame_timeline_slice a ON a.surface_frame_token = r.surface_frame_token
  LEFT JOIN process p ON p.upid = a.upid AND p.name = '$packageName'
  WHERE a.id IS NULL OR (p.upid IS NOT NULL AND EXISTS (
    SELECT 1 FROM args WHERE args.arg_set_id = a.arg_set_id AND args.key = 'Is Buffer?' AND args.string_value = 'Yes'))
  GROUP BY r.sample_index, r.phase, r.row_index
), app_expected AS (
  SELECT r.sample_index, r.phase, r.row_index, COUNT(e.id) app_expected_count,
         MIN(e.ts) app_expected_start, MIN(e.ts + e.dur) app_expected_end
  FROM requested r LEFT JOIN expected_frame_timeline_slice e ON e.surface_frame_token = r.surface_frame_token
  LEFT JOIN process p ON p.upid = e.upid AND p.name = '$packageName'
  WHERE e.id IS NULL OR p.upid IS NOT NULL
  GROUP BY r.sample_index, r.phase, r.row_index
), sf AS (
  SELECT a.sample_index, a.phase, a.row_index, COUNT(s.id) sf_actual_count,
         MIN(s.ts) sf_start, MIN(s.ts + s.dur) sf_end, MIN(s.present_type) sf_present_type,
         MIN(s.on_time_finish) sf_on_time_finish, MIN(s.gpu_composition) sf_gpu_composition,
         MIN(s.jank_type) sf_jank_type
  FROM app_actual a LEFT JOIN actual_frame_timeline_slice s ON s.display_frame_token = a.display_frame_token
  LEFT JOIN process p ON p.upid = s.upid AND p.name = '$surfaceFlingerProcess'
  WHERE s.id IS NULL OR p.upid IS NOT NULL
  GROUP BY a.sample_index, a.phase, a.row_index
)
SELECT a.*, e.app_expected_count, e.app_expected_start, e.app_expected_end,
       a.app_end - e.app_expected_end trace_app_overrun_ns,
       s.sf_actual_count, s.sf_start, s.sf_end, s.sf_present_type, s.sf_on_time_finish,
       s.sf_gpu_composition, s.sf_jank_type
FROM app_actual a JOIN app_expected e USING(sample_index,phase,row_index)
JOIN sf s USING(sample_index,phase,row_index)
ORDER BY a.sample_index, CASE a.phase WHEN 'preview' THEN 0 ELSE 1 END, a.row_index;
"@

$threadStateSql = @"
-- Thread states overlap app actual-frame windows; states overlap in time and are not additive with slices.
WITH requested AS (
$requestedRows
), windows AS (
  SELECT r.*, a.ts window_start, a.ts + a.dur window_end
  FROM requested r JOIN actual_frame_timeline_slice a ON a.surface_frame_token = r.surface_frame_token
  JOIN process p ON p.upid = a.upid AND p.name = '$packageName'
  WHERE EXISTS (SELECT 1 FROM args WHERE args.arg_set_id = a.arg_set_id AND args.key = 'Is Buffer?' AND args.string_value = 'Yes')
), threads AS (
  SELECT t.utid, t.tid, t.name thread_name, t.is_main_thread
  FROM thread t JOIN process p USING(upid)
  WHERE p.name = '$packageName' AND (t.is_main_thread = 1 OR t.name = 'RenderThread')
)
SELECT w.sample_index,w.phase,w.row_index,w.late,w.surface_frame_token,
       t.thread_name,t.tid,t.is_main_thread,st.state,st.io_wait,
       SUM(MIN(st.ts+st.dur,w.window_end)-MAX(st.ts,w.window_start)) overlap_duration_ns
FROM windows w JOIN threads t
JOIN thread_state st ON st.utid=t.utid AND st.dur>0 AND st.ts<w.window_end AND st.ts+st.dur>w.window_start
GROUP BY w.sample_index,w.phase,w.row_index,t.thread_name,t.tid,t.is_main_thread,st.state,st.io_wait
ORDER BY w.sample_index,w.phase,w.row_index,t.is_main_thread DESC,t.thread_name,st.state;
"@

$schedSql = @"
-- Scheduled Running intervals identify CPU/core residency; frequency events remain in the raw trace.
WITH requested AS (
$requestedRows
), windows AS (
  SELECT r.*, a.ts window_start, a.ts + a.dur window_end
  FROM requested r JOIN actual_frame_timeline_slice a ON a.surface_frame_token = r.surface_frame_token
  JOIN process p ON p.upid = a.upid AND p.name = '$packageName'
  WHERE EXISTS (SELECT 1 FROM args WHERE args.arg_set_id = a.arg_set_id AND args.key = 'Is Buffer?' AND args.string_value = 'Yes')
), threads AS (
  SELECT t.utid,t.tid,t.name thread_name,t.is_main_thread
  FROM thread t JOIN process p USING(upid)
  WHERE p.name='$packageName' AND (t.is_main_thread=1 OR t.name='RenderThread')
)
SELECT w.sample_index,w.phase,w.row_index,w.late,w.surface_frame_token,
       t.thread_name,t.tid,t.is_main_thread,s.cpu,
       MAX(s.ts,w.window_start) running_start,
       MIN(s.ts+s.dur,w.window_end)-MAX(s.ts,w.window_start) running_duration_ns,
       s.priority
FROM windows w JOIN threads t JOIN sched s ON s.utid=t.utid
WHERE s.dur>0 AND s.ts<w.window_end AND s.ts+s.dur>w.window_start
ORDER BY w.sample_index,w.phase,w.row_index,t.is_main_thread DESC,t.thread_name,running_start;
"@

$slicesSql = @"
-- All app main/RenderThread slices overlapping requested frames; absent names mean unavailable trace data.
WITH requested AS (
$requestedRows
), windows AS (
  SELECT r.*, a.ts window_start, a.ts + a.dur window_end
  FROM requested r JOIN actual_frame_timeline_slice a ON a.surface_frame_token=r.surface_frame_token
  JOIN process p ON p.upid=a.upid AND p.name='$packageName'
  WHERE EXISTS (SELECT 1 FROM args WHERE args.arg_set_id=a.arg_set_id AND args.key='Is Buffer?' AND args.string_value='Yes')
), app_slices AS (
  SELECT s.ts,s.dur,s.name,s.depth,t.name thread_name,t.is_main_thread
  FROM slice s JOIN thread_track tt ON s.track_id=tt.id JOIN thread t USING(utid) JOIN process p USING(upid)
  WHERE p.name='$packageName' AND (t.is_main_thread=1 OR t.name='RenderThread')
)
SELECT w.sample_index,w.phase,w.row_index,w.late,w.surface_frame_token,
       s.thread_name,s.is_main_thread,s.name slice_name,s.depth,s.ts slice_start,s.dur slice_duration_ns,
       MIN(s.ts+s.dur,w.window_end)-MAX(s.ts,w.window_start) overlap_duration_ns
FROM windows w JOIN app_slices s ON s.dur>0 AND s.ts<w.window_end AND s.ts+s.dur>w.window_start
ORDER BY w.sample_index,w.phase,w.row_index,s.is_main_thread DESC,s.thread_name,s.ts,s.depth;
"@

$integritySql = @"
SELECT name,value,severity,source FROM stats
WHERE severity IN ('data_loss','error') OR name GLOB '*loss*' OR name GLOB '*overrun*'
   OR name GLOB '*discard*' OR name GLOB '*wrap*' OR name GLOB '*flush*'
   OR name GLOB 'frame_timeline_*' OR name GLOB '*overwritten*' OR name GLOB '*dropped*'
ORDER BY name;
"@

function Invoke-Query {
    param([Parameter(Mandatory = $true)][string]$Name, [Parameter(Mandatory = $true)][string]$Sql)

    $sqlPath = Join-Path $resolvedOutput "commit-front-half-$Name.sql"
    $csvPath = Join-Path $resolvedOutput "commit-front-half-$Name.csv"
    $logPath = Join-Path $resolvedOutput "commit-front-half-$Name.trace-processor.log"
    [System.IO.File]::WriteAllText($sqlPath, $Sql, [System.Text.UTF8Encoding]::new($false))
    $output = @(& $resolvedTraceProcessor --query-file $sqlPath $resolvedTrace 2> $logPath)
    if ($LASTEXITCODE -ne 0 -or $output.Count -eq 0) {
        throw "Trace Processor failed or returned no output for $Name."
    }
    [System.IO.File]::WriteAllLines($csvPath, [string[]]$output, [System.Text.UTF8Encoding]::new($false))
    return $logPath
}

$logs = [System.Collections.Generic.List[string]]::new()
$logs.Add((Invoke-Query -Name "correlation" -Sql $correlationSql))
$logs.Add((Invoke-Query -Name "thread-states" -Sql $threadStateSql))
$logs.Add((Invoke-Query -Name "sched" -Sql $schedSql))
$logs.Add((Invoke-Query -Name "slices" -Sql $slicesSql))
$logs.Add((Invoke-Query -Name "integrity" -Sql $integritySql))

$combinedLog = [System.Collections.Generic.List[string]]::new()
foreach ($log in $logs) {
    $combinedLog.Add("[$([System.IO.Path]::GetFileName($log))]")
    Get-Content -LiteralPath $log | ForEach-Object { $combinedLog.Add($_) }
    Remove-Item -LiteralPath $log
}
[System.IO.File]::WriteAllLines(
    (Join-Path $resolvedOutput "commit-front-half-trace-processor.log"),
    $combinedLog,
    [System.Text.UTF8Encoding]::new($false)
)

$correlations = @(Import-Csv -LiteralPath (Join-Path $resolvedOutput "commit-front-half-correlation.csv"))
$integrity = @(Import-Csv -LiteralPath (Join-Path $resolvedOutput "commit-front-half-integrity.csv"))
$invalidStats = @($integrity | Where-Object {
    [long]$_.value -ne 0 -and (
        $_.severity -in @("error", "data_loss") -or
        $_.name -match "loss|overrun|wrap|parse.*error|unpaired|overwritten|dropped|flush.*fail" -or
        ($_.name -match "discard" -and $_.name -notin @("traced_chunks_discarded", "traced_patches_discarded"))
    )
})
foreach ($requiredStat in @("frame_timeline_event_parser_errors", "frame_timeline_unpaired_end_event",
    "traced_buf_incremental_sequences_dropped", "traced_buf_sequence_packet_loss",
    "traced_buf_trace_writer_packet_loss", "traced_final_flush_failed", "traced_final_flush_succeeded")) {
    if (@($integrity | Where-Object name -eq $requiredStat).Count -ne 1) {
        throw "Required integrity statistic is missing or ambiguous: $requiredStat."
    }
}
if ($invalidStats.Count -gt 0 -or @($integrity | Where-Object { $_.name -eq "traced_final_flush_succeeded" -and [long]$_.value -eq 1 }).Count -ne 1) {
    throw "Trace integrity or final flush failed; attribution is invalid."
}
if (
    $correlations.Count -ne $requests.Count -or
    @($correlations | Where-Object {
        [int]$_.app_actual_count -ne 1 -or
        [int]$_.app_expected_count -ne 1 -or
        [int]$_.sf_actual_count -ne 1
    }).Count -gt 0
) {
    throw "Every requested frame must join exactly one app actual, app expected, and SurfaceFlinger actual FrameTimeline row."
}
$lateCommits = @($correlations | Where-Object { $_.phase -eq "commit" -and [int]$_.late -eq 1 }).Count
$status = if ($lateCommits -eq 0) { "inconclusive-no-late-commit" } else { "attribution-complete" }
$metadata = @(
    "schema=$schema",
    "status=$status",
    "acceptance_evidence=false",
    "requested_frame_count=$($requests.Count)",
    "preview_frame_count=$(@($requests | Where-Object { $_.phase -eq 'preview' }).Count)",
    "commit_frame_count=$(@($requests | Where-Object { $_.phase -eq 'commit' }).Count)",
    "late_commit_frame_count=$lateCommits",
    "thread_state_rows=$(@(Import-Csv (Join-Path $resolvedOutput 'commit-front-half-thread-states.csv')).Count)",
    "sched_rows=$(@(Import-Csv (Join-Path $resolvedOutput 'commit-front-half-sched.csv')).Count)",
    "slice_rows=$(@(Import-Csv (Join-Path $resolvedOutput 'commit-front-half-slices.csv')).Count)",
    "limitation=intrusive attribution of fixed old source/APK; overlapping slices are not additive; missing markers are unavailable, not zero; not current-main timing or v7 acceptance"
)
[System.IO.File]::WriteAllLines(
    (Join-Path $resolvedOutput "commit-front-half-analysis-metadata.txt"),
    $metadata,
    [System.Text.UTF8Encoding]::new($false)
)
Write-Output "commit-front-half-attribution-analysis:$status"

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][string]$TraceProcessorPath,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$fixtureRoot = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $fixtureRoot) { throw "Fixture refuses to overwrite evidence." }
New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
$collector = Join-Path $PSScriptRoot "measurements/collect-m2-commit-front-half-attribution.ps1"
$processor = (Resolve-Path -LiteralPath $TraceProcessorPath).Path
$results = [Collections.Generic.List[object]]::new()

function Start-Sleep { param([int]$Milliseconds, [int]$Seconds) }

function New-FixtureUi {
    $dirty = if ($global:NeneAttributionFixtureState.Committed) { 'Unsaved changes' } else { 'No unsaved changes' }
    $undo = $global:NeneAttributionFixtureState.Committed.ToString().ToLowerInvariant()
    $redo = ($global:NeneAttributionFixtureState.Undone -and -not $global:NeneAttributionFixtureState.Committed).ToString().ToLowerInvariant()
    if ($global:NeneAttributionFixtureState.Mode -eq 'wrong-ui' -and $global:NeneAttributionFixtureState.TraceActive -and $global:NeneAttributionFixtureState.Committed) { $dirty = 'No unsaved changes' }
    return @"
<hierarchy><node>
<node content-desc="Document dirty status" text="$dirty" />
<node enabled="$undo" bounds="[200,0][300,100]"><node text="Undo" /></node>
<node enabled="$redo"><node text="Redo" /></node>
<node checked="true"><node content-desc="Pencil tool" /></node>
<node content-desc="16 by 16 pixel canvas" bounds="[0,0][160,160]" />
</node></hierarchy>
"@
}

# These command functions shadow native processes only within this fixture process.
# Every unknown command fails instead of falling through to an attached device.
function adb {
    $arguments = @($args)
    $global:LASTEXITCODE = 0
    if ($arguments[0] -ne '-s' -or $arguments[1] -ne 'fixture-device') { throw 'Unexpected device route.' }
    $a = @($arguments | Select-Object -Skip 2)
    $command = $a -join ' '
    $global:NeneAttributionFixtureState.Commands.Add($command)
    if ($command -like 'shell getprop *') {
        $props = @{ 'ro.kernel.qemu'='0'; 'ro.product.manufacturer'='ALLDOCUBE'; 'ro.product.model'='iPlay80miniPro'; 'ro.product.name'='iPlay80miniPro'; 'ro.product.device'='T830'; 'ro.build.version.sdk'='36' }
        if (-not $props.ContainsKey($a[2])) { throw 'Unexpected device property.' }
        return $props[$a[2]]
    }
    switch ($command) {
        'shell wm size' { return 'Physical size: 1200x1920' }
        'shell dumpsys display' { return '1200 x 1920, modeId 1 fps=90.0,' }
        'shell dumpsys thermalservice' { return 'Thermal Status: 1' }
        'shell dumpsys power' { return 'mWakefulness=Awake' }
        'shell dumpsys window policy' { return 'mIsShowing=false' }
        'shell dumpsys battery' { return 'USB powered: true' }
        'shell settings get global low_power' { return '0' }
        'shell dumpsys package dexopt' {
            if ($global:NeneAttributionFixtureState.Mode -eq 'wrong-dexopt') {
                return "[io.github.hideyukimori.nenepixel]`n    arm64: [status=verify]`n[example.decoy]`n    arm64: [status=speed-profile]"
            }
            return "[io.github.hideyukimori.nenepixel]`n    arm64: [status=speed-profile]`n[example.decoy]`n    arm64: [status=verify]"
        }
        'shell perfetto --query --long' { if ($global:NeneAttributionFixtureState.TraceActive) { return "unique_session_name: $($global:NeneAttributionFixtureState.SessionName)" }; return }
        'logcat -c' { return }
        'logcat -d -v threadtime' { return 'fixture: no app fatal events' }
    }
    if ($command -like 'install *' -or $command -like 'shell cmd package compile *') { return 'Success' }
    if ($command -like 'shell am broadcast *') { return 'Broadcast completed: result=1' }
    if ($command -like 'shell am force-stop *' -or $command -like 'shell am start *' -or $command -eq 'shell cmd input keyevent WAKEUP' -or $command -eq 'shell wm dismiss-keyguard') { return 'OK' }
    if ($command -like 'shell uiautomator dump *') { return 'UI dumped' }
    if ($command -like 'shell cmd input tap *') {
        if ([int]$a[4] -eq 5) { $global:NeneAttributionFixtureState.Committed = $true; $global:NeneAttributionFixtureState.Undone = $false }
        elseif ([int]$a[4] -eq 250) { $global:NeneAttributionFixtureState.Committed = $false; $global:NeneAttributionFixtureState.Undone = $true }
        else { throw 'Unexpected tap coordinates.' }
        return
    }
    if ($command -like 'shell cmd input motionevent *') {
        if (-not $global:NeneAttributionFixtureState.TraceActive) { throw 'Diagnostic input outside trace.' }
        $global:NeneAttributionFixtureState.Motion.Add($a[4])
        if ($a[4] -eq 'UP') { $global:NeneAttributionFixtureState.Committed = $true; $global:NeneAttributionFixtureState.Undone = $false }
        return
    }
    if ($command -like 'shell dumpsys gfxinfo * reset') { return 'reset' }
    if ($command -like 'shell dumpsys gfxinfo * framestats') {
        $global:NeneAttributionFixtureState.Frame += 1
        $token = 1000 + $global:NeneAttributionFixtureState.Frame
        $flags = if ($global:NeneAttributionFixtureState.Mode -eq 'flagged-frame' -and $global:NeneAttributionFixtureState.Frame -eq 3) { 1 } else { 0 }
        $headers = 'Flags,FrameTimelineVsyncId,IntendedVsync,FrameStartTime,HandleInputStart,AnimationStart,PerformTraversalsStart,DrawStart,SyncQueued,SyncStart,IssueDrawCommandsStart,SwapBuffers,SwapBuffersCompleted,FrameDeadline,FrameCompleted'
        $line = "$flags,$token,100,101,102,103,104,105,106,107,108,109,110,120,125"
        if ($global:NeneAttributionFixtureState.Mode -eq 'malformed-frame' -and $global:NeneAttributionFixtureState.Frame -eq 3) { $line = '0,missing' }
        return @('Applications Graphics Acceleration Info:', '', 'Total frames rendered: 1', '', '---PROFILEDATA---', $headers, $line, '---PROFILEDATA---', '')
    }
    if ($a[0] -eq 'shell' -and $a[1].StartsWith('if [ -e ')) { return 'absent' }
    if ($a[0] -eq 'push') { return 'pushed' }
    if ($command -like 'shell perfetto --background-wait *') {
        if (-not (Test-Path -LiteralPath (Join-Path $global:NeneAttributionFixtureState.Output 'manifest.json'))) { throw 'Manifest absent before producer.' }
        $global:NeneAttributionFixtureState.SessionName = [IO.Path]::GetFileNameWithoutExtension($a[5])
        $global:NeneAttributionFixtureState.TraceActive = $true
        $global:NeneAttributionFixtureState.Starts += 1
        if ($global:NeneAttributionFixtureState.Mode -eq 'malformed-start') { return 'not-a-pid' }
        return '2468'
    }
    if ($command -like 'shell /system/bin/trigger_perfetto *') {
        if ($a[2] -ne $global:NeneAttributionFixtureState.SessionName) { throw 'Wrong stop session.' }
        $global:NeneAttributionFixtureState.Stops += 1
        $global:NeneAttributionFixtureState.TraceActive = $false
        return 'triggered'
    }
    if ($command -like 'shell stat -c %s *') {
        if ($global:NeneAttributionFixtureState.TraceActive) { throw 'Stat before finalization.' }
        return '4'
    }
    if ($a[0] -eq 'pull') {
        if ($a[1].EndsWith('.xml')) { [IO.File]::WriteAllText($a[2], (New-FixtureUi), [Text.UTF8Encoding]::new($false)); return 'pulled' }
        if ($global:NeneAttributionFixtureState.TraceActive -or $global:NeneAttributionFixtureState.Stops -ne 1) { throw 'Trace pull before exact single stop.' }
        [IO.File]::WriteAllBytes($a[2], [byte[]]@(1,2,3,4)); return 'pulled'
    }
    throw "Unexpected native adb command: $command"
}

$nativeQuery = {
    $a = @($args)
    $global:LASTEXITCODE = 0
    if ($a.Count -ne 3 -or $a[0] -ne '--query-file') { throw 'Unexpected processor command.' }
    $global:NeneAttributionFixtureState.Queries.Add($a[1])
    if ($global:NeneAttributionFixtureState.Mode -eq 'analyzer-failure') { $global:LASTEXITCODE = 7; return }
    $sql = [IO.File]::ReadAllText($a[1])
    $name = [IO.Path]::GetFileName($a[1])
    if ($name -eq 'commit-front-half-integrity.sql') {
        return @('name,value,severity,source', 'traced_final_flush_succeeded,1,info,trace',
            'frame_timeline_event_parser_errors,0,info,analysis', 'frame_timeline_unpaired_end_event,0,info,analysis',
            'traced_buf_incremental_sequences_dropped,0,data_loss,trace', 'traced_buf_sequence_packet_loss,0,data_loss,trace',
            'traced_buf_trace_writer_packet_loss,0,data_loss,trace', 'traced_final_flush_failed,0,data_loss,trace',
            ('fixture_loss,' + $(if ($global:NeneAttributionFixtureState.Mode -eq 'data-loss') { '1' } else { '0' }) + ',data_loss,trace'))
    }
    foreach ($token in 1001..1020) {
        if ($sql -notmatch "\b$token surface_frame_token\b") { throw "Actual analyzer SQL omitted token $token." }
    }
    if ($name -eq 'commit-front-half-correlation.sql') {
        $output = [Collections.Generic.List[string]]::new()
        $output.Add('sample_index,phase,row_index,late,surface_frame_token,app_actual_count,app_expected_count,sf_actual_count')
        foreach ($sample in 1..10) {
            foreach ($phase in @('preview','commit')) {
                $token = 1000 + (($sample-1)*2) + $(if ($phase -eq 'preview') { 1 } else { 2 })
                $count = if ($global:NeneAttributionFixtureState.Mode -eq 'duplicate-correlation' -and $sample -eq 1) { 2 } else { 1 }
                $sfCount = if ($global:NeneAttributionFixtureState.Mode -eq 'duplicate-sf-correlation' -and $sample -eq 1) { 2 } else { 1 }
                $output.Add("$sample,$phase,1,1,$token,$count,1,$sfCount")
            }
        }
        return $output.ToArray()
    }
    if ($name -eq 'commit-front-half-thread-states.sql') { return @('sample_index,phase,state,overlap_duration_ns', '1,preview,Running,1') }
    if ($name -eq 'commit-front-half-sched.sql') { return @('sample_index,phase,cpu,running_duration_ns', '1,preview,0,1') }
    if ($name -eq 'commit-front-half-slices.sql') { return @('sample_index,phase,slice_name,overlap_duration_ns', '1,preview,fixture,1') }
    throw "Unexpected processor query: $name"
}
Set-Item -LiteralPath ('Function:\' + $processor) -Value $nativeQuery

foreach ($mode in @('success','malformed-frame','flagged-frame','wrong-ui','wrong-dexopt','malformed-start','duplicate-correlation','duplicate-sf-correlation','data-loss','analyzer-failure')) {
    $output = Join-Path $fixtureRoot $mode
    $global:NeneAttributionFixtureState = @{
        Mode=$mode; Output=$output; Committed=$false; Undone=$false; TraceActive=$false;
        SessionName=''; Starts=0; Stops=0; Frame=0;
        Commands=[Collections.Generic.List[string]]::new(); Motion=[Collections.Generic.List[string]]::new();
        Queries=[Collections.Generic.List[string]]::new()
    }
    $failure = $null
    try { & $collector -DeviceSerial 'fixture-device' -ApkPath $ApkPath -TraceProcessorPath $processor -OutputDirectory $output | Out-Null }
    catch { $failure = $_.Exception.Message }
    $state = Get-Content -Raw -LiteralPath (Join-Path $output 'run-state.json') | ConvertFrom-Json
    if ($mode -eq 'wrong-dexopt') {
        if (
            $null -eq $failure -or
            $failure -notlike '*fixed speed-profile runtime state*' -or
            $state.status -ne 'invalid-before-trace-start' -or
            $state.trace_started -or
            $global:NeneAttributionFixtureState.Starts -ne 0 -or
            $global:NeneAttributionFixtureState.Stops -ne 0
        ) {
            throw "A decoy package profile was accepted or started tracing: $failure"
        }
    } elseif ($global:NeneAttributionFixtureState.Starts -ne 1 -or $global:NeneAttributionFixtureState.Stops -ne 1 -or $global:NeneAttributionFixtureState.TraceActive) {
        throw "$mode did not finalize exactly one producer: $failure"
    } elseif ($mode -eq 'success') {
        if ($null -ne $failure -or $state.completed_operations -ne 10 -or $state.status -ne 'collected-pending-analysis') { throw "Full collector failed: $failure" }
        $expectedMotion = (1..10 | ForEach-Object { 'DOWN'; 'UP' }) -join ','
        if (($global:NeneAttributionFixtureState.Motion -join ',') -ne $expectedMotion -or $global:NeneAttributionFixtureState.Queries.Count -ne 5) { throw 'Full workload/analyzer order changed.' }
        if (@(Import-Csv (Join-Path $output 'frames.csv')).Count -ne 20) { throw 'Full frame population was not preserved.' }
    } else {
        if ($null -eq $failure -or $state.status -ne 'invalid-after-trace-start' -or -not $state.trace_started) { throw "$mode was not preserved as invalid-after-start." }
        if ($mode -in @('malformed-frame','flagged-frame') -and ($state.completed_operations -ne 1 -or @(Import-Csv (Join-Path $output 'frames.csv')).Count -ne 2)) { throw "$mode lost completed operation evidence." }
    }
    $global:NeneAttributionFixtureState.Commands | Set-Content -LiteralPath (Join-Path $output 'fixture-native-commands.txt') -Encoding utf8NoBOM
    $results.Add([pscustomobject]@{case=$mode;result='PASS';status=$state.status;operations=$state.completed_operations;error=$failure})
}
$results | Export-Csv -LiteralPath (Join-Path $fixtureRoot 'fixture-results.csv') -NoTypeInformation -Encoding utf8
$results | Format-Table -AutoSize
Write-Output 'Full collector/analyzer orchestration: PASS (10 scenarios; zero device calls; synthetic timing is not performance evidence)'

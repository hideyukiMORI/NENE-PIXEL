[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$DeviceSerial,
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][string]$TraceProcessorPath,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "m2-perfetto-session.ps1")

$schema = "nene-pixel-m2-commit-front-half-attribution-v1"
$sourceCommit = "efb8c36003a1c62e958da92cf4fb28c2b35dc261"
$expectedApkSha256 = "359a8f5a6975afae6f29e8680a69ae14f28164db72d36b250225f03d8f3de959"
$expectedApkBytes = 8410691L
$expectedTraceProcessorSha256 = "a881f3e2d4c6131493e85bfd1f36d1efe58e1478e2991825418d5d21614c1e48"
$physicalProfileId = "NENE-P2-ALLDOCUBE-IPL80MP-A16-API36"
$packageName = "io.github.hideyukimori.nenepixel"
$activityName = "$packageName/.MainActivity"
$sampleCount = 20
$warmupCount = 5
$previewWaitMilliseconds = 100
$commitWaitMilliseconds = 350
$quietAfterUndoMilliseconds = 1200
$traceTimeoutMilliseconds = 120000
$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
$resolvedTraceProcessor = (Resolve-Path -LiteralPath $TraceProcessorPath).Path
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
$traceState = $null
$traceStarted = $false
$frameRows = [System.Collections.Generic.List[object]]::new()
$environmentRows = [System.Collections.Generic.List[object]]::new()

function Invoke-TargetAdb {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = @(& adb -s $DeviceSerial @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed: adb -s <physical-device> $($Arguments -join ' ')`n$($output -join "`n")"
    }
    return $output | ForEach-Object { $_.ToString() }
}

$adbInvoker = {
    param([string[]]$Arguments)
    return @(Invoke-TargetAdb -Arguments $Arguments)
}

function Get-DeviceProperty {
    param([Parameter(Mandatory = $true)][string]$Name)

    return (Invoke-TargetAdb -Arguments @("shell", "getprop", $Name) | Select-Object -First 1).Trim()
}

function Get-EnvironmentCheckpoint {
    param([Parameter(Mandatory = $true)][string]$Name)

    if ((Get-DeviceProperty -Name "ro.kernel.qemu") -eq "1") {
        throw "The fixed attribution profile requires a physical device."
    }
    $manufacturer = Get-DeviceProperty -Name "ro.product.manufacturer"
    $model = Get-DeviceProperty -Name "ro.product.model"
    $product = Get-DeviceProperty -Name "ro.product.name"
    $device = Get-DeviceProperty -Name "ro.product.device"
    $api = Get-DeviceProperty -Name "ro.build.version.sdk"
    if (
        $manufacturer -ine "ALLDOCUBE" -or
        $model -cne "iPlay80miniPro" -or
        $product -cne "iPlay80miniPro" -or
        $device -cne "T830" -or
        $api -ne "36"
    ) {
        throw "The physical device identity does not match $physicalProfileId."
    }
    $wm = (Invoke-TargetAdb -Arguments @("shell", "wm", "size")) -join "`n"
    $display = (Invoke-TargetAdb -Arguments @("shell", "dumpsys", "display")) -join "`n"
    $thermal = (Invoke-TargetAdb -Arguments @("shell", "dumpsys", "thermalservice")) -join "`n"
    $power = (Invoke-TargetAdb -Arguments @("shell", "dumpsys", "power")) -join "`n"
    $windowPolicy = (Invoke-TargetAdb -Arguments @("shell", "dumpsys", "window", "policy")) -join "`n"
    $battery = (Invoke-TargetAdb -Arguments @("shell", "dumpsys", "battery")) -join "`n"
    $lowPower = (Invoke-TargetAdb -Arguments @("shell", "settings", "get", "global", "low_power") | Select-Object -First 1).Trim()
    if ($wm -notmatch "Physical size:\s*1200x1920") {
        throw "Display size changed at $Name."
    }
    if ($display -notmatch "1200\s+x\s+1920,\s+modeId\s+1" -or $display -notmatch "fps=90(?:\.0+)?[,}]") {
        throw "Display mode changed at $Name."
    }
    if ($thermal -notmatch "Thermal Status:\s*(\d+)") {
        throw "Thermal status is unavailable at $Name."
    }
    $thermalStatus = [int]$Matches[1]
    if ($thermalStatus -gt 1) {
        throw "Thermal status is outside the fixed limit at $Name."
    }
    if ($lowPower -ne "0" -or $power -notmatch "mWakefulness=Awake" -or $battery -notmatch "USB powered:\s*true") {
        throw "Interactive USB-powered non-power-save state is required at $Name."
    }
    if ($windowPolicy -notmatch "mIsShowing=false") {
        throw "The secure keyguard must be unlocked before attribution preflight."
    }
    return [pscustomobject]@{
        checkpoint = $Name
        host_timestamp = (Get-Date).ToString("o")
        physical_profile_id = $physicalProfileId
        refresh_rate_hertz = 90
        thermal_status = $thermalStatus
        power_save_mode = $false
        interactive = $true
        usb_powered = $true
    }
}

function Get-Bounds {
    param([Parameter(Mandatory = $true)][System.Xml.XmlElement]$Node)

    $match = [regex]::Match($Node.GetAttribute("bounds"), "^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$")
    if (-not $match.Success) {
        throw "A required UI node has invalid bounds."
    }
    return [pscustomobject]@{
        Left = [int]$match.Groups[1].Value
        Top = [int]$match.Groups[2].Value
        Right = [int]$match.Groups[3].Value
        Bottom = [int]$match.Groups[4].Value
    }
}

function Save-Ui {
    param([Parameter(Mandatory = $true)][string]$Name)

    $remote = "/data/local/tmp/nene-commit-front-half-$Name.xml"
    $local = Join-Path $resolvedOutput "$Name.xml"
    if (Test-Path -LiteralPath $local) {
        throw "UI evidence refuses to overwrite $local."
    }
    Invoke-TargetAdb -Arguments @("shell", "uiautomator", "dump", $remote) | Out-Null
    Invoke-TargetAdb -Arguments @("pull", $remote, $local) | Out-Null
    [xml]$ui = Get-Content -Raw -LiteralPath $local
    return $ui
}

function Assert-CleanUi {
    param([Parameter(Mandatory = $true)][xml]$Ui)

    $dirty = $Ui.SelectSingleNode("//node[@content-desc='Document dirty status']")
    $undo = $Ui.SelectSingleNode("//node[@text='Undo']")
    $redo = $Ui.SelectSingleNode("//node[@text='Redo']")
    $pencil = $Ui.SelectSingleNode("//node[@content-desc='Pencil tool']")
    $canvas = $Ui.SelectSingleNode("//node[@content-desc='16 by 16 pixel canvas']")
    if (
        $null -eq $dirty -or $null -eq $undo -or $null -eq $redo -or $null -eq $pencil -or $null -eq $canvas -or
        $dirty.GetAttribute("text") -ne "No unsaved changes" -or
        $undo.ParentNode.GetAttribute("enabled") -ne "false" -or
        $redo.ParentNode.GetAttribute("enabled") -ne "false" -or
        $pencil.ParentNode.GetAttribute("checked") -ne "true"
    ) {
        throw "The editor is not at the canonical clean Pencil checkpoint."
    }
    return [pscustomobject]@{ Canvas = $canvas; Undo = $undo.ParentNode }
}

function Assert-CommittedUi {
    param([Parameter(Mandatory = $true)][xml]$Ui)

    $dirty = $Ui.SelectSingleNode("//node[@content-desc='Document dirty status']")
    $undo = $Ui.SelectSingleNode("//node[@text='Undo']")
    $redo = $Ui.SelectSingleNode("//node[@text='Redo']")
    if (
        $null -eq $dirty -or $null -eq $undo -or $null -eq $redo -or
        $dirty.GetAttribute("text") -ne "Unsaved changes" -or
        $undo.ParentNode.GetAttribute("enabled") -ne "true" -or
        $redo.ParentNode.GetAttribute("enabled") -ne "false"
    ) {
        throw "The committed Pencil state is not visible."
    }
}

function Get-FrameRows {
    param(
        [Parameter(Mandatory = $true)][string[]]$Text,
        [Parameter(Mandatory = $true)][int]$Sample,
        [Parameter(Mandatory = $true)][ValidateSet("preview", "commit")][string]$Phase
    )

    $start = [Array]::IndexOf($Text, "---PROFILEDATA---")
    if ($start -lt 0) {
        throw "Missing PROFILEDATA for sample $Sample $Phase."
    }
    $headers = $Text[$start + 1].TrimEnd(",").Split(",")
    $rows = [System.Collections.Generic.List[object]]::new()
    for ($index = $start + 2; $index -lt $Text.Count -and $Text[$index] -ne "---PROFILEDATA---"; $index += 1) {
        if ([string]::IsNullOrWhiteSpace($Text[$index])) { continue }
        $values = $Text[$index].TrimEnd(",").Split(",")
        if ($values.Count -ne $headers.Count) {
            throw "Malformed PROFILEDATA for sample $Sample $Phase."
        }
        $map = @{}
        for ($column = 0; $column -lt $headers.Count; $column += 1) {
            $map[$headers[$column]] = $values[$column]
        }
        if ([int]$map.Flags -ne 0 -or [long]$map.FrameTimelineVsyncId -le 0) {
            throw "A flagged or unassociated frame occurred for sample $Sample $Phase."
        }
        $rows.Add(
            [pscustomobject]@{
                sample_index = $Sample
                phase = $Phase
                row_index = $rows.Count + 1
                flags = [int]$map.Flags
                frame_timeline_vsync_id = [long]$map.FrameTimelineVsyncId
                intended_vsync_nanos = [long]$map.IntendedVsync
                frame_start_nanos = [long]$map.FrameStartTime
                handle_input_start_nanos = [long]$map.HandleInputStart
                animation_start_nanos = [long]$map.AnimationStart
                perform_traversals_start_nanos = [long]$map.PerformTraversalsStart
                draw_start_nanos = [long]$map.DrawStart
                sync_queued_nanos = [long]$map.SyncQueued
                sync_start_nanos = [long]$map.SyncStart
                issue_draw_commands_start_nanos = [long]$map.IssueDrawCommandsStart
                swap_buffers_nanos = [long]$map.SwapBuffers
                swap_buffers_completed_nanos = [long]$map.SwapBuffersCompleted
                frame_deadline_nanos = [long]$map.FrameDeadline
                frame_completed_nanos = [long]$map.FrameCompleted
                frame_overrun_ms = ([long]$map.FrameCompleted - [long]$map.FrameDeadline) / 1000000.0
            }
        )
    }
    $totalMatch = [regex]::Match(($Text -join "`n"), "(?m)^Total frames rendered:\s*(\d+)\s*$")
    if (-not $totalMatch.Success -or [int]$totalMatch.Groups[1].Value -ne $rows.Count -or $rows.Count -lt 1) {
        throw "Frame cardinality mismatch for sample $Sample $Phase."
    }
    return $rows
}

function Capture-Phase {
    param(
        [Parameter(Mandatory = $true)][int]$Sample,
        [Parameter(Mandatory = $true)][ValidateSet("preview", "commit")][string]$Phase,
        [Parameter(Mandatory = $true)][ValidateSet("DOWN", "UP")][string]$Motion,
        [Parameter(Mandatory = $true)][int]$X,
        [Parameter(Mandatory = $true)][int]$Y,
        [Parameter(Mandatory = $true)][int]$WaitMilliseconds
    )

    Invoke-TargetAdb -Arguments @("shell", "dumpsys", "gfxinfo", $packageName, "reset") | Out-Null
    Invoke-TargetAdb -Arguments @("shell", "cmd", "input", "motionevent", $Motion, "$X", "$Y") | Out-Null
    Start-Sleep -Milliseconds $WaitMilliseconds
    $text = @(Invoke-TargetAdb -Arguments @("shell", "dumpsys", "gfxinfo", $packageName, "framestats"))
    $path = Join-Path $resolvedOutput ("raw/sample-{0:D2}-{1}.txt" -f $Sample, $Phase)
    [System.IO.File]::WriteAllLines($path, $text, [System.Text.UTF8Encoding]::new($false))
    return Get-FrameRows -Text $text -Sample $Sample -Phase $Phase
}

function Write-RunState {
    param([Parameter(Mandatory = $true)][string]$Status, [Parameter(Mandatory = $true)][int]$CompletedOperations)

    [ordered]@{
        schema = $schema
        status = $Status
        trace_started = $traceStarted
        completed_operations = $CompletedOperations
        invocation_budget = 1
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $resolvedOutput "run-state.json") -Encoding utf8NoBOM
}

$apkItem = Get-Item -LiteralPath $resolvedApk
$apkHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedApk).Hash.ToLowerInvariant()
if ($apkItem.Length -ne $expectedApkBytes -or $apkHash -ne $expectedApkSha256) {
    throw "The APK does not match the fixed attribution identity."
}
$traceProcessorHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedTraceProcessor).Hash.ToLowerInvariant()
if ($traceProcessorHash -ne $expectedTraceProcessorSha256) {
    throw "The attribution analysis requires the pinned Trace Processor v49.0 binary."
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($resolvedApk)
try {
    foreach ($entryName in @("assets/dexopt/baseline.prof", "assets/dexopt/baseline.profm")) {
        if ($null -eq $archive.GetEntry($entryName)) {
            throw "The fixed APK is missing $entryName."
        }
    }
    $revisionEntry = $archive.GetEntry("META-INF/version-control-info.textproto")
    if ($null -eq $revisionEntry) {
        throw "The fixed APK is missing its embedded source revision."
    }
    $reader = [System.IO.StreamReader]::new($revisionEntry.Open())
    try { $revisionText = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($revisionText -notmatch "revision:\s*`"$sourceCommit`"") {
        throw "The APK embedded source revision does not match the fixed attribution source."
    }
}
finally {
    $archive.Dispose()
}

$invocation = New-NeneAttributionInvocation `
    -OutputDirectory $resolvedOutput `
    -Manifest ([ordered]@{
        schema = $schema
        parent_issue = 54
        focused_issue = 67
        source_commit = $sourceCommit
        apk_sha256 = $expectedApkSha256
        apk_bytes = $expectedApkBytes
        trace_processor_sha256 = $expectedTraceProcessorSha256
        physical_profile_id = $physicalProfileId
        compile_mode = "speed-profile"
        warmup_count = $warmupCount
        operation_count = $sampleCount
        preview_wait_ms = $previewWaitMilliseconds
        commit_wait_ms = $commitWaitMilliseconds
        quiet_after_undo_ms = $quietAfterUndoMilliseconds
        trace_timeout_ms = $traceTimeoutMilliseconds
        maximum_trace_invocations = 1
        invalid_recovery = "none after trace start"
        classification = "frame_overrun_ms > 0 means late; attribution only"
        hypothesis = "late commits contain additional main-thread CPU execution or scheduler delay before/during traversal"
        stop = "one trace; no late commit or ambiguous association is inconclusive; no retry"
    })
New-Item -ItemType Directory -Path (Join-Path $resolvedOutput "raw") | Out-Null
Write-RunState -Status "preflight" -CompletedOperations 0

$completedOperations = 0
try {
    Invoke-TargetAdb -Arguments @("shell", "cmd", "input", "keyevent", "WAKEUP") | Out-Null
    Invoke-TargetAdb -Arguments @("shell", "wm", "dismiss-keyguard") | Out-Null
    Start-Sleep -Milliseconds 250
    $environmentRows.Add((Get-EnvironmentCheckpoint -Name "before_install"))
    Invoke-TargetAdb -Arguments @("install", "-r", "-d", $resolvedApk) | Out-Null
    Invoke-TargetAdb -Arguments @("shell", "cmd", "package", "compile", "--reset", $packageName) | Out-Null
    $receiver = "$packageName/androidx.profileinstaller.ProfileInstallReceiver"
    $profileResult = Invoke-TargetAdb -Arguments @(
        "shell", "am", "broadcast", "-a", "androidx.profileinstaller.action.INSTALL_PROFILE", $receiver
    )
    if (($profileResult -join "`n") -notmatch "result=1(?:\D|$)") {
        throw "Packaged Baseline Profile installation did not report result 1."
    }
    $compileResult = Invoke-TargetAdb -Arguments @("shell", "cmd", "package", "compile", "-m", "speed-profile", "-f", $packageName)
    if (($compileResult -join "`n") -notmatch "Success") {
        throw "speed-profile compilation did not report success."
    }
    $dexoptText = (Invoke-TargetAdb -Arguments @("shell", "dumpsys", "package", "dexopt")) -join "`n"
    $packageMarker = "[$packageName]"
    $packageStart = $dexoptText.IndexOf($packageMarker, [StringComparison]::Ordinal)
    $nextPackage =
        if ($packageStart -ge 0) {
            $dexoptText.IndexOf("`n  [", $packageStart + $packageMarker.Length, [StringComparison]::Ordinal)
        } else {
            -1
        }
    $packageDexopt =
        if ($packageStart -lt 0) {
            ""
        } elseif ($nextPackage -lt 0) {
            $dexoptText.Substring($packageStart)
        } else {
            $dexoptText.Substring($packageStart, $nextPackage - $packageStart)
        }
    if ($packageDexopt -notmatch "\[status=speed-profile\]") {
        throw "The installed package does not report the fixed speed-profile runtime state."
    }
    [System.IO.File]::WriteAllText(
        (Join-Path $resolvedOutput "compile-state.txt"),
        $packageDexopt,
        [System.Text.UTF8Encoding]::new($false)
    )
    Invoke-TargetAdb -Arguments @("shell", "am", "force-stop", $packageName) | Out-Null
    Invoke-TargetAdb -Arguments @("shell", "am", "start", "-W", "-n", $activityName) | Out-Null
    Start-Sleep -Milliseconds 1500

    $before = Save-Ui -Name "ui-before"
    $cleanNodes = Assert-CleanUi -Ui $before
    $canvasBounds = Get-Bounds -Node $cleanNodes.Canvas
    $undoBounds = Get-Bounds -Node $cleanNodes.Undo
    $canvasX = [int][Math]::Floor($canvasBounds.Left + (($canvasBounds.Right - $canvasBounds.Left) / 32.0))
    $canvasY = [int][Math]::Floor($canvasBounds.Top + (($canvasBounds.Bottom - $canvasBounds.Top) / 32.0))
    $undoX = [int][Math]::Floor(($undoBounds.Left + $undoBounds.Right) / 2.0)
    $undoY = [int][Math]::Floor(($undoBounds.Top + $undoBounds.Bottom) / 2.0)

    foreach ($warmup in 1..$warmupCount) {
        Invoke-TargetAdb -Arguments @("shell", "cmd", "input", "tap", "$canvasX", "$canvasY") | Out-Null
        Start-Sleep -Milliseconds $commitWaitMilliseconds
        Invoke-TargetAdb -Arguments @("shell", "cmd", "input", "tap", "$undoX", "$undoY") | Out-Null
        Start-Sleep -Milliseconds $quietAfterUndoMilliseconds
    }
    Assert-CleanUi -Ui (Save-Ui -Name "ui-after-warmups") | Out-Null
    $environmentRows.Add((Get-EnvironmentCheckpoint -Name "before_trace"))
    Invoke-TargetAdb -Arguments @("logcat", "-c") | Out-Null

    $config = @"
unique_session_name: "__SESSION_NAME__"
buffers { size_kb: 65536 fill_policy: RING_BUFFER }
data_sources { config { name: "android.surfaceflinger.frametimeline" target_buffer: 0 } }
data_sources { config { name: "linux.ftrace" target_buffer: 0 ftrace_config {
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
  compact_sched { enabled: true }
} } }
data_sources { config { name: "linux.process_stats" target_buffer: 0 process_stats_config {
  scan_all_processes_on_start: true
  proc_stats_poll_ms: 1000
} } }
builtin_data_sources { disable_clock_snapshotting: false disable_trace_config: false disable_system_info: false }
flush_period_ms: 5000
trigger_config { trigger_mode: STOP_TRACING trigger_timeout_ms: $traceTimeoutMilliseconds triggers {
  name: "__SESSION_NAME__"
  stop_delay_ms: 1000
} }
"@
    $traceState = Start-NenePerfettoSession `
        -Invoker $adbInvoker `
        -OutputDirectory $resolvedOutput `
        -SessionPrefix "nene-m2-commit-front-half" `
        -Schema $schema `
        -ConfigTemplate $config `
        -ArtifactPrefix "commit-front-half" `
        -OnStarted { $script:traceStarted = $true }
    Write-RunState -Status "trace-started" -CompletedOperations 0

    foreach ($sample in 1..$sampleCount) {
        @(Capture-Phase -Sample $sample -Phase "preview" -Motion "DOWN" -X $canvasX -Y $canvasY -WaitMilliseconds $previewWaitMilliseconds) |
            ForEach-Object { $frameRows.Add($_) }
        @(Capture-Phase -Sample $sample -Phase "commit" -Motion "UP" -X $canvasX -Y $canvasY -WaitMilliseconds $commitWaitMilliseconds) |
            ForEach-Object { $frameRows.Add($_) }
        Assert-CommittedUi -Ui (Save-Ui -Name ("ui-committed-{0:D2}" -f $sample))
        Invoke-TargetAdb -Arguments @("shell", "cmd", "input", "tap", "$undoX", "$undoY") | Out-Null
        Start-Sleep -Milliseconds $quietAfterUndoMilliseconds
        Assert-CleanUi -Ui (Save-Ui -Name ("ui-clean-{0:D2}" -f $sample)) | Out-Null
        $completedOperations = $sample
        Write-RunState -Status "trace-started" -CompletedOperations $completedOperations
    }

    $frameRows | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath (Join-Path $resolvedOutput "frames.csv")
    Stop-NenePerfettoSession -Invoker $adbInvoker -State $traceState | Out-Null
    $analyzer = Join-Path $PSScriptRoot "analyze-m2-commit-front-half-attribution.ps1"
    & $analyzer `
        -TracePath $traceState.LocalTrace `
        -FramesPath (Join-Path $resolvedOutput "frames.csv") `
        -TraceProcessorPath $resolvedTraceProcessor `
        -OutputDirectory $resolvedOutput
    $environmentRows.Add((Get-EnvironmentCheckpoint -Name "after_trace"))
    $environmentRows | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath (Join-Path $resolvedOutput "environment.csv")
    $logcat = @(Invoke-TargetAdb -Arguments @("logcat", "-d", "-v", "threadtime"))
    [System.IO.File]::WriteAllLines((Join-Path $resolvedOutput "logcat.txt"), $logcat, [System.Text.UTF8Encoding]::new($false))
    if (@($logcat | Select-String -Pattern "FATAL EXCEPTION|ANR in|Fatal signal|Process .* has died").Count -gt 0) {
        throw "Fatal or ANR evidence occurred during attribution."
    }
    if (
        $completedOperations -ne $sampleCount -or
        @($frameRows | Where-Object { $_.phase -eq "preview" }).Count -lt $sampleCount -or
        @($frameRows | Where-Object { $_.phase -eq "commit" }).Count -lt $sampleCount -or
        @($frameRows.frame_timeline_vsync_id | Sort-Object -Unique).Count -ne $frameRows.Count
    ) {
        throw "The attribution population is incomplete or ambiguously associated."
    }
    Write-RunState -Status "collected-pending-analysis" -CompletedOperations $completedOperations
    Write-Output "commit-front-half-attribution-collected:$resolvedOutput"
}
catch {
    $status = if ($traceStarted) { "invalid-after-trace-start" } else { "invalid-before-trace-start" }
    Write-RunState -Status $status -CompletedOperations $completedOperations
    if ($environmentRows.Count -gt 0) {
        $environmentRows | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath (Join-Path $resolvedOutput "environment.csv")
    }
    throw
}

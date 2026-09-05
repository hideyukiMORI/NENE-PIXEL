Set-StrictMode -Version Latest

function New-NeneAttributionInvocation {
    param(
        [Parameter(Mandatory = $true)][string]$OutputDirectory,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Manifest
    )

    $resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
    if (Test-Path -LiteralPath $resolvedOutput) {
        throw "Attribution collection refuses to overwrite $resolvedOutput."
    }
    New-Item -ItemType Directory -Path $resolvedOutput | Out-Null
    $manifestPath = Join-Path $resolvedOutput "manifest.json"
    [System.IO.File]::WriteAllText(
        $manifestPath,
        ($Manifest | ConvertTo-Json -Depth 6),
        [System.Text.UTF8Encoding]::new($false)
    )
    return [pscustomobject]@{
        OutputDirectory = $resolvedOutput
        ManifestPath = $manifestPath
    }
}

function Invoke-NenePerfettoAdb {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Invoker,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    return @(& $Invoker $Arguments) | ForEach-Object { $_.ToString() }
}

function Get-NenePerfettoSessionMatchCount {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Invoker,
        [Parameter(Mandatory = $true)][string]$SessionName
    )

    $serviceState = @(Invoke-NenePerfettoAdb -Invoker $Invoker -Arguments @("shell", "perfetto", "--query", "--long"))
    return @($serviceState | Where-Object { $_ -like "*$SessionName*" }).Count
}

function Start-NenePerfettoSession {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Invoker,
        [Parameter(Mandatory = $true)][string]$OutputDirectory,
        [Parameter(Mandatory = $true)][ValidatePattern("^[a-z0-9][a-z0-9-]{2,48}$")][string]$SessionPrefix,
        [Parameter(Mandatory = $true)][string]$Schema,
        [Parameter(Mandatory = $true)][string]$ConfigTemplate,
        [Parameter(Mandatory = $true)][string]$ArtifactPrefix,
        [scriptblock]$OnStarted
    )

    $resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
    if (-not (Test-Path -LiteralPath $resolvedOutput -PathType Container)) {
        throw "Perfetto output directory is missing: $resolvedOutput"
    }
    $batchId = [guid]::NewGuid().ToString("D")
    $sessionName = "$SessionPrefix-$batchId"
    if ($ConfigTemplate.IndexOf("__SESSION_NAME__", [StringComparison]::Ordinal) -lt 0) {
        throw "Perfetto config template is missing the unique session placeholder."
    }
    $remoteConfig = "/data/misc/perfetto-configs/$sessionName.txtpb"
    $remoteTrace = "/data/misc/perfetto-traces/$sessionName.perfetto-trace"
    $localConfig = Join-Path $resolvedOutput "$ArtifactPrefix-config.txtpb"
    $localTrace = Join-Path $resolvedOutput "$ArtifactPrefix.perfetto-trace"
    $toolPath = Join-Path $resolvedOutput "$ArtifactPrefix-tool.txt"
    foreach ($localPath in @($localConfig, $localTrace, $toolPath)) {
        if (Test-Path -LiteralPath $localPath) {
            throw "Perfetto collection refuses to overwrite $localPath."
        }
    }

    foreach ($remotePath in @($remoteConfig, $remoteTrace)) {
        $exists = @(
            Invoke-NenePerfettoAdb `
                -Invoker $Invoker `
                -Arguments @("shell", "if [ -e '$remotePath' ]; then echo exists; else echo absent; fi")
        )
        if ($exists.Count -ne 1 -or $exists[0].Trim() -notin @("exists", "absent")) {
            throw "Unable to determine whether an exact Perfetto staging path exists."
        }
        if ($exists[0].Trim() -eq "exists") {
            throw "Perfetto collection found a pre-existing exact device staging path."
        }
    }

    $config = $ConfigTemplate.Replace("__SESSION_NAME__", $sessionName)
    [System.IO.File]::WriteAllText($localConfig, $config, [System.Text.UTF8Encoding]::new($false))
    Invoke-NenePerfettoAdb -Invoker $Invoker -Arguments @("push", $localConfig, $remoteConfig) | Out-Null
    $state = [pscustomobject]@{
        Schema = $Schema
        BatchId = $batchId
        SessionName = $sessionName
        RemoteConfig = $remoteConfig
        RemoteTrace = $remoteTrace
        LocalConfig = $localConfig
        LocalTrace = $localTrace
        ToolPath = $toolPath
        LauncherReportedPid = 0
        Active = $true
        StopRequested = $false
        Finalized = $false
    }
    if ($null -ne $OnStarted) {
        & $OnStarted $state
    }
    $startOutput = @(
        Invoke-NenePerfettoAdb `
            -Invoker $Invoker `
            -Arguments @("shell", "perfetto", "--background-wait", "--txt", "-c", $remoteConfig, "-o", $remoteTrace)
    )
    $pidMatches = @($startOutput | Where-Object { $_.Trim() -match "^\d+$" })
    if ($pidMatches.Count -ne 1) {
        throw "Perfetto did not return exactly one background tracing PID."
    }
    $state.LauncherReportedPid = [int]$pidMatches[0].Trim()
    $toolLines = @(
        "schema=$Schema",
        "batch_id=$batchId",
        "session_name=$sessionName",
        "remote_config=$remoteConfig",
        "remote_trace=$remoteTrace",
        "start_command=adb -s <physical-device> shell perfetto --background-wait --txt -c $remoteConfig -o $remoteTrace",
        "launcher_reported_pid=$($state.LauncherReportedPid)",
        "liveness_check=exact unique session name through perfetto --query --long",
        "stop_command=adb -s <physical-device> shell /system/bin/trigger_perfetto $sessionName",
        "normal_stop=trigger-only; wait for named-session disappearance before stat and pull"
    )
    [System.IO.File]::WriteAllLines($toolPath, $toolLines, [System.Text.UTF8Encoding]::new($false))
    $activeSessionCount = Get-NenePerfettoSessionMatchCount -Invoker $Invoker -SessionName $sessionName
    if ($activeSessionCount -ne 1) {
        throw "Perfetto service state does not expose exactly one named session before collection."
    }
    return $state
}

function Stop-NenePerfettoSession {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Invoker,
        [Parameter(Mandatory = $true)][object]$State,
        [ValidateRange(1, 120)][int]$CompletionTimeoutSeconds = 30,
        [ValidateRange(1, 5000)][int]$PollMilliseconds = 250
    )

    if (-not $State.Active) {
        throw "Perfetto trace is not active."
    }
    if (-not $State.StopRequested) {
        Invoke-NenePerfettoAdb `
            -Invoker $Invoker `
            -Arguments @("shell", "/system/bin/trigger_perfetto", $State.SessionName) | Out-Null
        $State.StopRequested = $true
    }

    $deadline = (Get-Date).AddSeconds($CompletionTimeoutSeconds)
    do {
        $activeSessionCount = Get-NenePerfettoSessionMatchCount -Invoker $Invoker -SessionName $State.SessionName
        if ($activeSessionCount -eq 0) {
            $State.Active = $false
            break
        }
        if ($activeSessionCount -ne 1) {
            throw "Perfetto service state exposes an ambiguous named-session count."
        }
        Start-Sleep -Milliseconds $PollMilliseconds
    } while ((Get-Date) -lt $deadline)
    if ($activeSessionCount -ne 0) {
        throw "Perfetto did not finalize within $CompletionTimeoutSeconds seconds after the stop trigger."
    }

    $remoteBytesText = @(
        Invoke-NenePerfettoAdb `
            -Invoker $Invoker `
            -Arguments @("shell", "stat", "-c", "%s", $State.RemoteTrace)
    )
    $remoteBytes = 0L
    if (
        $remoteBytesText.Count -ne 1 -or
        -not [long]::TryParse($remoteBytesText[0].Trim(), [ref]$remoteBytes) -or
        $remoteBytes -le 0
    ) {
        throw "Perfetto finalized without a non-empty remote trace."
    }

    Invoke-NenePerfettoAdb -Invoker $Invoker -Arguments @("pull", $State.RemoteTrace, $State.LocalTrace) | Out-Null
    if (-not (Test-Path -LiteralPath $State.LocalTrace -PathType Leaf)) {
        throw "Perfetto pull did not create the local trace."
    }
    $localBytes = (Get-Item -LiteralPath $State.LocalTrace).Length
    if ($localBytes -le 0 -or $localBytes -ne $remoteBytes) {
        throw "The pulled Perfetto trace length does not match the finalized device file."
    }
    $State.Finalized = $true
    $writer = [System.IO.StreamWriter]::new(
        $State.ToolPath,
        $true,
        [System.Text.UTF8Encoding]::new($false)
    )
    try {
        $writer.WriteLine("trace_finalized_before_pull=true")
        $writer.WriteLine("remote_trace_bytes=$remoteBytes")
        $writer.WriteLine("local_trace_bytes=$localBytes")
    }
    finally {
        $writer.Dispose()
    }
    return $State
}

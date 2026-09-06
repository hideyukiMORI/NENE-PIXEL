[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "measurements/m2-perfetto-session.ps1")

$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("nene-perfetto-session-" + [guid]::NewGuid().ToString("N"))

function Invoke-ExpectedFailure {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Action,
        [Parameter(Mandatory = $true)][string]$MessagePattern
    )

    try {
        & $Action
    }
    catch {
        if ($_.Exception.Message -notlike $MessagePattern) {
            throw "Unexpected failure: $($_.Exception.Message)"
        }
        return
    }
    throw "Expected failure matching '$MessagePattern'."
}

function New-FakeInvoker {
    param(
        [Parameter(Mandatory = $true)][hashtable]$State,
        [Parameter(Mandatory = $true)][string]$ManifestPath
    )

    return {
        param([string[]]$Arguments)

        $command = $Arguments -join " "
        $State.Commands.Add($command)
        if (
            $Arguments[0] -eq "shell" -and
            $Arguments[1].StartsWith("if [ -e ", [StringComparison]::Ordinal)
        ) {
            return "absent"
        }
        if ($command -like "push *") {
            return "pushed"
        }
        if ($command -like "shell perfetto --background-wait*") {
            if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
                throw "Manifest was not persisted before producer start."
            }
            $configName = [System.IO.Path]::GetFileNameWithoutExtension($Arguments[5])
            $State.SessionName = $configName
            $State.Active = $true
            if ($State.MalformedAcknowledgement) { return "malformed acknowledgement" }
            return "2468"
        }
        if ($command -eq "shell perfetto --query --long") {
            if ($State.AlwaysActive -or $State.Active) {
                return "unique_session_name: $($State.SessionName)"
            }
            return @()
        }
        if ($command -like "shell /system/bin/trigger_perfetto *") {
            $State.StopCount += 1
            if (-not $State.AlwaysActive) {
                $State.Active = $false
            }
            return "triggered"
        }
        if ($command -like "shell stat -c %s *") {
            return "$($State.RemoteBytes)"
        }
        if ($Arguments[0] -eq "pull") {
            $bytes = [byte[]](1..$State.LocalBytes)
            [System.IO.File]::WriteAllBytes($Arguments[2], $bytes)
            return "pulled"
        }
        throw "Unexpected fake adb command: $command"
    }.GetNewClosure()
}

function New-State {
    param([long]$RemoteBytes = 4, [int]$LocalBytes = 4, [bool]$AlwaysActive = $false)

    return @{
        Active = $false
        AlwaysActive = $AlwaysActive
        SessionName = ""
        RemoteBytes = $RemoteBytes
        LocalBytes = $LocalBytes
        StopCount = 0
        MalformedAcknowledgement = $false
        Commands = [System.Collections.Generic.List[string]]::new()
    }
}

function Start-FixtureSession {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][hashtable]$State
    )

    $invocation = New-NeneAttributionInvocation `
        -OutputDirectory (Join-Path $temporaryRoot $Name) `
        -Manifest ([ordered]@{ schema = "fixture-v1"; invocation = $Name })
    $invoker = New-FakeInvoker -State $State -ManifestPath $invocation.ManifestPath
    $session = Start-NenePerfettoSession `
        -Invoker $invoker `
        -OutputDirectory $invocation.OutputDirectory `
        -SessionPrefix "nene-fixture" `
        -Schema "fixture-v1" `
        -ArtifactPrefix "trace" `
        -ConfigTemplate "unique_session_name: `"__SESSION_NAME__`""
    return [pscustomobject]@{ Invocation = $invocation; Invoker = $invoker; Session = $session }
}

try {
    New-Item -ItemType Directory -Path $temporaryRoot | Out-Null

    $successState = New-State
    $success = Start-FixtureSession -Name "success" -State $successState
    Stop-NenePerfettoSession `
        -Invoker $success.Invoker `
        -State $success.Session `
        -CompletionTimeoutSeconds 1 `
        -PollMilliseconds 1 | Out-Null
    if (
        -not $success.Session.Finalized -or
        $successState.StopCount -ne 1 -or
        (Get-Item -LiteralPath $success.Session.LocalTrace).Length -ne 4
    ) {
        throw "A finalized same-length trace was not accepted."
    }
    $commands = $successState.Commands -join "|"
    if (
        $commands.IndexOf("trigger_perfetto", [StringComparison]::Ordinal) -gt
            $commands.IndexOf("stat -c %s", [StringComparison]::Ordinal) -or
        $commands.IndexOf("stat -c %s", [StringComparison]::Ordinal) -gt
            $commands.IndexOf("pull ", [StringComparison]::Ordinal)
    ) {
        throw "Trace completion, stat, and pull ordering changed."
    }

    $emptyState = New-State -RemoteBytes 0 -LocalBytes 0
    $empty = Start-FixtureSession -Name "empty" -State $emptyState
    Invoke-ExpectedFailure `
        -MessagePattern "*non-empty remote trace*" `
        -Action { Stop-NenePerfettoSession -Invoker $empty.Invoker -State $empty.Session -CompletionTimeoutSeconds 1 -PollMilliseconds 1 }

    $mismatchState = New-State -RemoteBytes 4 -LocalBytes 3
    $mismatch = Start-FixtureSession -Name "mismatch" -State $mismatchState
    Invoke-ExpectedFailure `
        -MessagePattern "*length does not match*" `
        -Action { Stop-NenePerfettoSession -Invoker $mismatch.Invoker -State $mismatch.Session -CompletionTimeoutSeconds 1 -PollMilliseconds 1 }
    if ((Get-Item -LiteralPath $mismatch.Session.LocalTrace).Length -ne 3) {
        throw "A mismatched pulled artifact was not retained for diagnosis."
    }

    $timeoutState = New-State -AlwaysActive $true
    $timeout = Start-FixtureSession -Name "timeout" -State $timeoutState
    Invoke-ExpectedFailure `
        -MessagePattern "*did not finalize*" `
        -Action { Stop-NenePerfettoSession -Invoker $timeout.Invoker -State $timeout.Session -CompletionTimeoutSeconds 1 -PollMilliseconds 1 }
    if ($timeoutState.StopCount -ne 1) {
        throw "A timeout retriggered the producer."
    }

    $occupied = Join-Path $temporaryRoot "occupied"
    New-NeneAttributionInvocation -OutputDirectory $occupied -Manifest ([ordered]@{ schema = "fixture-v1" }) | Out-Null
    Invoke-ExpectedFailure `
        -MessagePattern "*refuses to overwrite*" `
        -Action { New-NeneAttributionInvocation -OutputDirectory $occupied -Manifest ([ordered]@{ schema = "fixture-v1" }) }

    $malformedState = New-State
    $malformedState.MalformedAcknowledgement = $true
    $malformedInvocation = New-NeneAttributionInvocation -OutputDirectory (Join-Path $temporaryRoot "malformed") -Manifest ([ordered]@{ schema = "fixture-v1" })
    $malformedInvoker = New-FakeInvoker -State $malformedState -ManifestPath $malformedInvocation.ManifestPath
    $startedEvidence = @{ State = $null }
    Invoke-ExpectedFailure -MessagePattern "*exactly one background tracing PID*" -Action {
        Start-NenePerfettoSession -Invoker $malformedInvoker -OutputDirectory $malformedInvocation.OutputDirectory `
            -SessionPrefix "nene-fixture" -Schema "fixture-v1" -ArtifactPrefix "trace" `
            -ConfigTemplate 'unique_session_name: "__SESSION_NAME__"' `
            -OnStarted { param($state) $startedEvidence.State = $state }
    }
    if ($null -eq $startedEvidence.State) { throw "Malformed acknowledgement lost consumed-invocation state." }
    Stop-NenePerfettoSession -Invoker $malformedInvoker -State $startedEvidence.State -CompletionTimeoutSeconds 1 -PollMilliseconds 1 | Out-Null
    if (-not $startedEvidence.State.Finalized -or $malformedState.StopCount -ne 1) {
        throw "Malformed acknowledgement did not preserve a finalized trace with one stop."
    }

    Write-Output "M2 Perfetto lifecycle fixture validation: PASS"
}
finally {
    $resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)
    $resolvedSystemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTemporaryRoot.StartsWith($resolvedSystemTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$collector = Join-Path $PSScriptRoot "measurements/measure-m2-frame.ps1"
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("nene-frame-protocol-" + [guid]::NewGuid().ToString("N"))
$baselineCommit = "1" * 40
$candidateCommit = "2" * 40
$baselineHash = "a" * 64
$candidateHash = "b" * 64
$common = @{
    DeviceSerial = "offline-validation"
    ApkPath = "offline-validation.apk"
    ExperimentDirectory = $temporaryRoot
    ExperimentId = "offline-protocol-validation"
    BaselineSourceCommit = $baselineCommit
    CandidateSourceCommit = $candidateCommit
    BaselineApkSha256 = $baselineHash
    CandidateApkSha256 = $candidateHash
    CandidateHypothesis = "fixed candidate hypothesis"
    ExpectedAffectedCost = "fixed expected affected cost"
    CorrectnessRisk = "fixed correctness risk"
    StopConditions = "fixed stopping conditions"
    Attempt = 1
    ValidateExperimentOnly = $true
}

function Invoke-ExpectedPass {
    param([Parameter(Mandatory = $true)][hashtable]$Arguments)

    & $collector @Arguments | Out-Null
}

function Invoke-ExpectedFailure {
    param([Parameter(Mandatory = $true)][hashtable]$Arguments)

    try {
        & $collector @Arguments | Out-Null
    }
    catch {
        return
    }
    throw "Expected protocol validation to reject the invocation."
}

function Write-State {
    param(
        [Parameter(Mandatory = $true)][string]$Slot,
        [Parameter(Mandatory = $true)][int]$Attempt,
        [Parameter(Mandatory = $true)][string]$Status,
        [Parameter(Mandatory = $true)][string]$Verdict,
        [Parameter(Mandatory = $true)][int]$MeasuredDownCount
    )

    $directory = Join-Path $temporaryRoot "$Slot-attempt-$Attempt"
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    [ordered]@{
        schema = "nene-pixel-m2-frame-experiment-v2"
        experiment_id = "offline-protocol-validation"
        comparison_sequence_index = [int]$Slot.Substring(5, 2)
        attempt = $Attempt
        status = $Status
        verdict = $Verdict
        measured_down_count = $MeasuredDownCount
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory "run-state.json") -Encoding utf8NoBOM
}

try {
    $slot1 = $common.Clone()
    $slot1 += @{
        Variant = "debug"
        SourceCommit = $baselineCommit
        RunKind = "diagnostic"
        CandidateRole = "baseline"
        ComparisonSequenceIndex = 1
        SampleCount = 10
    }
    $modelValidation = @(& $collector @slot1)
    if (
        $modelValidation.Count -ne 1 -or
        $modelValidation[0].validation -ne "pass" -or
        [double]$modelValidation[0].model_input_to_committed_result_ms -ne 25.0 -or
        [double]$modelValidation[0].model_down_to_committed_result_ms -ne 145.0
    ) {
        throw "The shared operation model did not exclude the intentional preview dwell from committed-result latency."
    }

    $wrongCount = $slot1.Clone()
    $wrongCount.SampleCount = 50
    Invoke-ExpectedFailure -Arguments $wrongCount

    $slot2 = $common.Clone()
    $slot2 += @{
        Variant = "debug"
        SourceCommit = $candidateCommit
        RunKind = "diagnostic"
        CandidateRole = "candidate"
        ComparisonSequenceIndex = 2
        SampleCount = 10
    }
    Invoke-ExpectedFailure -Arguments $slot2
    Write-State -Slot "slot-01-diagnostic-baseline" -Attempt 1 -Status "completed" -Verdict "inconclusive" -MeasuredDownCount 10
    Invoke-ExpectedPass -Arguments $slot2

    $slot1StatePath = Join-Path $temporaryRoot "slot-01-diagnostic-baseline-attempt-1/run-state.json"
    $foreignState = Get-Content -Raw -LiteralPath $slot1StatePath | ConvertFrom-Json
    $foreignState.experiment_id = "foreign-experiment"
    $foreignState | ConvertTo-Json | Set-Content -LiteralPath $slot1StatePath -Encoding utf8NoBOM
    Invoke-ExpectedFailure -Arguments $slot2
    Write-State -Slot "slot-01-diagnostic-baseline" -Attempt 1 -Status "completed" -Verdict "inconclusive" -MeasuredDownCount 10

    $changedPair = $slot2.Clone()
    $changedPair.CandidateApkSha256 = "c" * 64
    Invoke-ExpectedFailure -Arguments $changedPair

    $slot2Attempt2 = $slot2.Clone()
    $slot2Attempt2.Attempt = 2
    Invoke-ExpectedFailure -Arguments $slot2Attempt2
    Write-State -Slot "slot-02-diagnostic-candidate" -Attempt 1 -Status "invalid-before-samples" -Verdict "invalid" -MeasuredDownCount 0
    Invoke-ExpectedPass -Arguments $slot2Attempt2
    Write-State -Slot "slot-02-diagnostic-candidate" -Attempt 2 -Status "completed" -Verdict "inconclusive" -MeasuredDownCount 10

    $slot3 = $common.Clone()
    $slot3 += @{
        Variant = "release-like"
        CompilationMode = "speed-profile"
        SourceCommit = $candidateCommit
        RunKind = "decision"
        CandidateRole = "candidate"
        ComparisonSequenceIndex = 3
        SampleCount = 50
    }
    Invoke-ExpectedPass -Arguments $slot3
    Write-State -Slot "slot-03-decision-candidate" -Attempt 1 -Status "completed" -Verdict "fail" -MeasuredDownCount 50

    $slot4 = $common.Clone()
    $slot4 += @{
        Variant = "release-like"
        CompilationMode = "speed-profile"
        SourceCommit = $baselineCommit
        RunKind = "decision"
        CandidateRole = "baseline"
        ComparisonSequenceIndex = 4
        SampleCount = 50
    }
    Invoke-ExpectedFailure -Arguments $slot4
    Write-State -Slot "slot-03-decision-candidate" -Attempt 1 -Status "completed" -Verdict "pass" -MeasuredDownCount 50
    Invoke-ExpectedPass -Arguments $slot4

    Write-Output "M2 frame protocol state validation: PASS"
}
finally {
    $resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)
    $resolvedSystemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTemporaryRoot.StartsWith($resolvedSystemTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

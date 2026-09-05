[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$workflowPath = Join-Path $repositoryRoot ".github/workflows/ci.yml"
$guardPath = Join-Path $repositoryRoot ".github/scripts/require-merge-ready.sh"
if (-not (Test-Path -LiteralPath $guardPath -PathType Leaf)) {
    throw "The merge-readiness guard is missing."
}
$workflow = (Get-Content -Raw -LiteralPath $workflowPath).Replace("`r`n", "`n")

$expectedTrigger = (@"
on:
  pull_request:
    branches:
      - main
    types:
      - opened
      - reopened
      - synchronize
      - edited
      - ready_for_review
      - converted_to_draft
"@).Replace("`r`n", "`n")
$triggerMatch = [regex]::Match($workflow, '(?ms)^on:\n.*?(?=^\S)')
if (-not $triggerMatch.Success -or $triggerMatch.Value.TrimEnd() -ne $expectedTrigger.TrimEnd()) {
    throw "CI must use the exact merge-ready pull-request trigger matrix."
}

$requiredFragments = @(
    "permissions:`n  contents: read",
    'group: ci-${{ github.workflow }}-${{ github.event.pull_request.number }}',
    "jobs:`n  quality:`n    name: quality",
    "- name: Require a merge-ready pull request`n        env:`n          " +
        'PR_DRAFT: ${{ github.event.pull_request.draft }}' +
        "`n        run: bash .github/scripts/require-merge-ready.sh",
    "cache-provider: basic`n          cache-read-only: true",
    "run: ./gradlew check :app:android:assembleDebug --stacktrace"
)
foreach ($fragment in $requiredFragments) {
    if (-not $workflow.Contains($fragment)) {
        throw "CI workflow is missing required fragment: $fragment"
    }
}

$checkoutIndex = $workflow.IndexOf("- name: Check out sources")
$guardIndex = $workflow.IndexOf("- name: Require a merge-ready pull request")
$jdkIndex = $workflow.IndexOf("- name: Set up JDK 21")
$canonicalIndex = $workflow.IndexOf("- name: Run canonical checks and build the supported artifact")
if (-1 -in @($checkoutIndex, $guardIndex, $jdkIndex, $canonicalIndex)) {
    throw "CI workflow is missing a required ordered step."
}
if (-not ($checkoutIndex -lt $guardIndex -and $guardIndex -lt $jdkIndex -and $jdkIndex -lt $canonicalIndex)) {
    throw "The draft guard must run after checkout and before toolchain or canonical work."
}
if ([regex]::Matches($workflow, '(?m)^\s+run:\s+\.\/gradlew\s+').Count -ne 1) {
    throw "CI must expose exactly one Gradle invocation: the canonical merge gate."
}

$forbiddenPatterns = @(
    '(?m)^\s{2}push:',
    '(?m)^\s{2}workflow_dispatch:',
    '(?m)^\s+if:',
    '(?m)^\s+continue-on-error:'
)
foreach ($pattern in $forbiddenPatterns) {
    if ($workflow -match $pattern) {
        throw "CI workflow contains a forbidden bypass or duplicate trigger: $pattern"
    }
}

$actionLines = @($workflow -split "`n" | Where-Object { $_ -match '^\s+uses:\s+' })
if ($actionLines.Count -ne 3) {
    throw "CI must retain exactly three external action uses."
}
foreach ($line in $actionLines) {
    if ($line -notmatch '^\s+uses:\s+[^@\s]+@[0-9a-f]{40}\s+#\s+.+$') {
        throw "Every external action must remain pinned to an immutable SHA with a version comment."
    }
}

function Invoke-GuardCase {
    param(
        [AllowEmptyString()]
        [string]$DraftState,

        [Parameter(Mandatory = $true)]
        [int]$ExpectedExitCode
    )

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = "bash"
    $startInfo.ArgumentList.Add(".github/scripts/require-merge-ready.sh")
    if ($DraftState.Length -gt 0) {
        $startInfo.ArgumentList.Add($DraftState)
    }
    $startInfo.WorkingDirectory = $repositoryRoot
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($startInfo)
    $standardOutput = $process.StandardOutput.ReadToEnd()
    $standardError = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne $ExpectedExitCode) {
        throw "Guard state '$DraftState' returned $($process.ExitCode), expected $ExpectedExitCode. " +
            "stdout=$standardOutput stderr=$standardError"
    }
}

Invoke-GuardCase -DraftState "false" -ExpectedExitCode 0
Invoke-GuardCase -DraftState "true" -ExpectedExitCode 1
Invoke-GuardCase -DraftState "" -ExpectedExitCode 1
Invoke-GuardCase -DraftState "invalid" -ExpectedExitCode 1

Write-Output "CI_TRIGGER_VALIDATION=pass"
Write-Output "FULL_QUALITY_EVENTS=opened,reopened,synchronize,edited,ready_for_review when draft=false"
Write-Output "FAIL_CLOSED_EVENTS=opened,reopened,synchronize,edited,converted_to_draft when draft=true"

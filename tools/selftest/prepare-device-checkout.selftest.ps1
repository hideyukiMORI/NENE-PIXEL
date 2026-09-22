<#
tools/selftest/prepare-device-checkout.selftest.ps1 - self-test for tools/prepare-device-checkout.ps1.
Runs two cases without git, Gradle or adb: -WhatIf against this worktree (exit 0, JSON with a
steps array free of am start / pm clear / uninstall / rm, and manifest_shape with every manifest
key), and a run without -Issue (exit 2).

Output: FAIL lines on stderr, then {"cases":2,"passed":N} on stdout.
Exit codes: 0 all cases passed, 1 any case failed.
Example: pwsh -NoProfile -File tools/selftest/prepare-device-checkout.selftest.ps1
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$target = Join-Path (Split-Path -Parent $PSScriptRoot) 'prepare-device-checkout.ps1'
$worktree = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$id = 'selftest-' + [guid]::NewGuid().ToString('N')
$passed = 0
$failures = [System.Collections.Generic.List[string]]::new()

function Invoke-Target([string[]]$Arguments) {
    $output = & pwsh -NoProfile -File $target @Arguments 2>$null
    return [pscustomobject]@{ exit_code = $LASTEXITCODE; stdout = ($output -join "`n") }
}

# Case 1: -WhatIf prints the plan and the manifest shape.
$whatIf = Invoke-Target @('-Worktree', $worktree, '-Issue', '128', '-Id', $id, '-WhatIf')
$problems = @()
if ($whatIf.exit_code -ne 0) { $problems += "exit $($whatIf.exit_code)" }
else {
    $plan = $whatIf.stdout | ConvertFrom-Json
    $steps = @($plan.steps)
    if ($steps.Count -eq 0) { $problems += 'steps is empty' }
    foreach ($step in $steps) {
        if ($step -match 'am start|pm clear|uninstall|\brm\b') { $problems += "forbidden step: $step" }
    }
    $shapeKeys = @($plan.manifest_shape.PSObject.Properties.Name)
    foreach ($key in 'worktree', 'commit', 'branch', 'dirty', 'apk', 'device', 'guard', 'preserved', 'snapshot', 'created_utc') {
        if ($shapeKeys -notcontains $key) { $problems += "manifest_shape lacks $key" }
    }
    if (@($plan.manifest_shape.apk.PSObject.Properties.Name) -notcontains 'sha256') { $problems += 'manifest_shape.apk lacks sha256' }
    if (@($plan.manifest_shape.device.PSObject.Properties.Name) -notcontains 'serial') { $problems += 'manifest_shape.device lacks serial' }
    if (Test-Path -LiteralPath $plan.output_directory) { $problems += 'WhatIf created the output directory' }
}
if ($problems.Count -eq 0) { $passed++ } else { $failures.Add("FAIL whatif: $($problems -join '; ')") }

# Case 2: missing -Issue is a precondition failure.
$missing = Invoke-Target @('-Worktree', $worktree, '-Id', $id, '-WhatIf')
if ($missing.exit_code -eq 2) { $passed++ } else { $failures.Add("FAIL missing-issue: exit $($missing.exit_code)") }

foreach ($failure in $failures) { [Console]::Error.WriteLine($failure) }
[ordered]@{ cases = 2; passed = $passed } | ConvertTo-Json -Compress | Write-Output
exit $(if ($passed -eq 2) { 0 } else { 1 })

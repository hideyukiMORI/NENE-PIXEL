<#
tools/selftest/assert-protected-unchanged.selftest.ps1
Runs tools/assert-protected-unchanged.ps1 against a throwaway Git repository in a temporary
directory (it never touches this repository). Prints {"cases":6,"passed":6} and exits 0 when every
case passes; otherwise prints each failed case name and exits 1.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$tool = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../assert-protected-unchanged.ps1')).Path
$work = Join-Path ([System.IO.Path]::GetTempPath()) ("nene-assert-selftest-" + [guid]::NewGuid().ToString('N'))
$repo = Join-Path $work 'repo'
$failures = [System.Collections.Generic.List[string]]::new()
$cases = 0

function Invoke-Tool {
    param([string[]]$Arguments)
    $out = & pwsh -NoProfile -File $tool @Arguments 2>&1
    return [pscustomobject]@{ Code = $LASTEXITCODE; Text = ($out | ForEach-Object { [string]$_ }) -join "`n" }
}

function Invoke-Case {
    param([string]$Name, [scriptblock]$Body)
    $script:cases++
    try {
        $message = & $Body
        if ($message) { $script:failures.Add("FAIL ${Name}: $message") }
    }
    catch { $script:failures.Add("FAIL ${Name}: $($_.Exception.Message)") }
}

function Invoke-QuietGit {
    param([string[]]$Arguments)
    $out = & git -C $repo @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "git $($Arguments -join ' '): $out" }
}

try {
    New-Item -ItemType Directory -Path (Join-Path $repo 'docs/quality') -Force | Out-Null
    Invoke-QuietGit @('init', '-q', '-b', 'main')
    Invoke-QuietGit @('config', 'user.name', 'selftest')
    Invoke-QuietGit @('config', 'user.email', 'selftest@example.invalid')
    Invoke-QuietGit @('config', 'core.autocrlf', 'false')
    Set-Content -LiteralPath (Join-Path $repo 'docs/quality/A_EVIDENCE.md') -Value 'evidence v1'
    Set-Content -LiteralPath (Join-Path $repo 'README.md') -Value 'readme'
    Invoke-QuietGit @('add', '-A')
    Invoke-QuietGit @('commit', '-q', '-m', 'base')
    Invoke-QuietGit @('tag', 'base')

    Invoke-Case 'a-path-no-diff' {
        Set-Content -LiteralPath (Join-Path $repo 'README.md') -Value 'readme v2'
        Invoke-QuietGit @('commit', '-q', '-am', 'unprotected change')
        $r = Invoke-Tool @('-BaseRef', 'base', '-Repository', $repo)
        if ($r.Code -ne 0) { "exit $($r.Code): $($r.Text)" }
    }

    Invoke-Case 'b-path-committed-diff' {
        Set-Content -LiteralPath (Join-Path $repo 'docs/quality/A_EVIDENCE.md') -Value 'evidence v2'
        Invoke-QuietGit @('commit', '-q', '-am', 'protected change')
        $r = Invoke-Tool @('-BaseRef', 'base', '-Repository', $repo)
        if ($r.Code -ne 1) { return "exit $($r.Code): $($r.Text)" }
        $v = @(($r.Text | ConvertFrom-Json).violations)
        if ($v -notcontains 'docs/quality/A_EVIDENCE.md') { "violations: $($v -join ',')" }
    }

    $left = Join-Path $work 'left.json'
    Set-Content -LiteralPath $left -Value '{"generated_utc":"2026-09-01T00:00:00Z","summary":{"byte_count":10,"frames":[1,2]},"note":null}'

    Invoke-Case 'c-compare-allowed-only' {
        $right = Join-Path $work 'right-c.json'
        Set-Content -LiteralPath $right -Value '{"generated_utc":"2026-09-23T00:00:00Z","summary":{"byte_count":12,"frames":[1,2.0]},"note":null}'
        $r = Invoke-Tool @('-Compare', "$left,$right", '-AllowedFields', 'generated_utc,summary.byte_count')
        if ($r.Code -ne 0) { "exit $($r.Code): $($r.Text)" }
    }

    Invoke-Case 'd-compare-numeric-violation' {
        $right = Join-Path $work 'right-d.json'
        Set-Content -LiteralPath $right -Value '{"generated_utc":"2026-09-01T00:00:00Z","summary":{"byte_count":10,"frames":[1,3]},"note":null}'
        $r = Invoke-Tool @('-Compare', "$left,$right", '-AllowedFields', 'generated_utc,summary.byte_count')
        if ($r.Code -ne 1) { return "exit $($r.Code): $($r.Text)" }
        $v = @(($r.Text | ConvertFrom-Json).violations)
        if ($v.Count -ne 1 -or $v[0].path -ne 'summary.frames[1]' -or $v[0].left -ne 2 -or $v[0].right -ne 3) { "violations: $($r.Text)" }
    }

    $evidence = Join-Path $work 'evidence'
    New-Item -ItemType Directory -Path (Join-Path $evidence 'sub') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $evidence 'run.json') -Value '{"ok":true}'
    Set-Content -LiteralPath (Join-Path $evidence 'sub/trace.txt') -Value 'trace'
    $ledger = Join-Path $work 'ledger.json'

    Invoke-Case 'e-ledger-write-check' {
        $w = Invoke-Tool @('-WriteLedger', $evidence, '-Ledger', $ledger)
        if ($w.Code -ne 0) { return "write exit $($w.Code): $($w.Text)" }
        $again = Invoke-Tool @('-WriteLedger', $evidence, '-Ledger', $ledger)
        if ($again.Code -ne 2) { return "overwrite exit $($again.Code)" }
        $r = Invoke-Tool @('-CheckLedger', $ledger)
        if ($r.Code -ne 0) { "check exit $($r.Code): $($r.Text)" }
    }

    Invoke-Case 'f-ledger-modified' {
        Set-Content -LiteralPath (Join-Path $evidence 'sub/trace.txt') -Value 'tampered'
        $r = Invoke-Tool @('-CheckLedger', $ledger)
        if ($r.Code -ne 1) { return "exit $($r.Code): $($r.Text)" }
        $v = @(($r.Text | ConvertFrom-Json).violations)
        if ($v.Count -ne 1 -or $v[0].path -ne 'sub/trace.txt' -or $v[0].kind -ne 'modified') { "violations: $($r.Text)" }
    }
}
catch {
    $failures.Add("FAIL setup: $($_.Exception.Message)")
}
finally {
    if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force }
}

foreach ($f in $failures) { Write-Output $f }
[ordered]@{ cases = $cases; passed = $cases - @($failures | Where-Object { $_ -notlike 'FAIL setup*' }).Count } |
    ConvertTo-Json -Compress | Write-Output
if ($failures.Count -gt 0) { exit 1 }
exit 0

param([Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$SyntheticCapture)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw 'A new evidence directory is required.' }
New-Item -ItemType Directory -Path $output | Out-Null
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$analysisPath = Join-Path $PSScriptRoot 'measurements/p4-indexed-memory-analysis.ps1'
. $analysisPath
$cases = [Collections.Generic.List[object]]::new()
$timer = [Diagnostics.Stopwatch]::StartNew()
function Assert-Equal { param($Actual, $Expected, [string]$Name)
    if ((($Actual -join "`n") -replace "`r`n", "`n") -cne
        (($Expected -join "`n") -replace "`r`n", "`n")) { throw "Source contract differs: $Name" }
    $cases.Add([ordered]@{ case = $Name; result = 'pass' })
}
function Read-Ast { param([string]$Path)
    $tokens = $null; $parseErrors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile($Path, [ref]$tokens, [ref]$parseErrors)
    if ($parseErrors.Count -ne 0) { throw "Invalid script syntax: $Path" }
    return $ast
}
function Find-Function { param($Ast, [string]$Name)
    return $Ast.Find({ param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $Name
    }, $true)
}
try {
    $historical = & git -C $repo show 'de2b8e24f1944b675777cdc82dedc4761a631da2:docs/quality/measurements/p4-indexed-memory-analysis.ps1'
    if ($LASTEXITCODE -ne 0) { throw 'Pinned historical analyzer must be available.' }
    $legacyPath = Join-Path $output 'historical-analyzer.ps1'
    $historical | Set-Content -LiteralPath $legacyPath
    $legacyAst = Read-Ast $legacyPath; $currentAst = Read-Ast $analysisPath
    foreach ($name in @('Get-P4MemoryStatusBundles', 'Get-P4RequiredStatusValue', 'ConvertFrom-P4MemoryReport',
        'Get-P4RequiredInt64', 'Assert-P4MemoryReportValue')) {
        Assert-Equal (Find-Function $currentAst $name).Extent.Text (Find-Function $legacyAst $name).Extent.Text "unchanged $name"
    }
    $oldBody = (Find-Function $legacyAst 'Test-P4MemoryCapture').Body.EndBlock.Statements
    $newBody = (Find-Function $currentAst 'Test-P4MemoryCapture').Body.EndBlock.Statements
    Assert-Equal @($newBody | Select-Object -Skip 1 | ForEach-Object { $_.Extent.Text }) `
        @($oldBody | ForEach-Object { $_.Extent.Text }) 'historical dispatch body unchanged after explicit phase branch'
    $fixtureAst = Read-Ast (Join-Path $PSScriptRoot 'validate-p4-memory-evidence.ps1')
    . ([scriptblock]::Create((Find-Function $fixtureAst 'New-P4MemoryLines').Extent.Text))
    $commit = '0123456789abcdef0123456789abcdef01234567'
    $profile = 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36'
    foreach ($family in @('baseline-common-drawing-history', 'candidate-common-indexed-history',
        'candidate-palette-history', 'candidate-legacy-import')) {
        $lines = New-P4MemoryLines -Family $family
        $lines | Set-Content -LiteralPath (Join-Path $output "$family.txt")
        $actual = Test-P4MemoryCapture $lines $family 1 $commit
        $expected = & { . $legacyPath; Test-P4MemoryCapture $lines $family 1 $commit }
        Assert-Equal ($actual | ConvertTo-Json -Depth 10) ($expected | ConvertTo-Json -Depth 10) "$family unchanged result"
        Assert-Equal $actual.verdict 'pass' "$family valid"
    }
    $report = ConvertFrom-P4MemoryReport ((Get-Content -LiteralPath $SyntheticCapture |
        Where-Object { $_ -match '^INSTRUMENTATION_STATUS: p4MemoryReport=' }) -replace '^INSTRUMENTATION_STATUS: p4MemoryReport=', '')
    $measurement = Join-Path $repo 'app/android/src/androidTest/kotlin/io/github/hideyukimori/nenepixel/measurement'
    $journal = Get-Content -LiteralPath (Join-Path $measurement 'P4LayerMemoryJournal.kt') -Raw
    $admission = Get-Content -LiteralPath (Join-Path $measurement 'P4LayerRunAdmission.kt') -Raw
    $admissionKeys = @([regex]::Matches($admission, '"([a-z_0-9]+)" to ') | ForEach-Object { $_.Groups[1].Value })
    Assert-Equal $admissionKeys $script:P4LayerMemoryIdentityFields 'Android admission field order'
    $checkpoints = [regex]::Match($journal, 'listOf\("empty_idle"[^\r\n]+').Value
    $names = @([regex]::Matches($checkpoints, '"([a-z_]+)"') | ForEach-Object { $_.Groups[1].Value })
    Assert-Equal $names @('empty_idle', 'maximum_loaded_idle', 'long_preview_held', 'committed_idle', 'post_cycles_idle') 'Android checkpoint order'
    $capture = $journal.Substring($journal.IndexOf('fun capture('), $journal.IndexOf('fun finish(') - $journal.IndexOf('fun capture('))
    $keys = @([regex]::Matches($capture, '"([a-z_]+)" to ') | ForEach-Object { $_.Groups[1].Value })
    Assert-Equal $keys @('run', 'slot_id', 'index', 'checkpoint', 'java_bytes', 'java_committed_bytes', 'pss_kib',
        'dalvik_pss_kib', 'native_pss_kib', 'other_pss_kib', 'private_dirty_kib', 'shared_dirty_kib') 'Android raw checkpoint fields'
    foreach ($match in [regex]::Matches($journal, '"([a-z_]+)" to "([^"$]+)"')) {
        Assert-Equal $report[$match.Groups[1].Value] $match.Groups[2].Value "Android fixed report fact $($match.Groups[1].Value)"
    }
    $cases | ForEach-Object { "PASS $($_.case)" }
} catch {
    $cases.Add([ordered]@{ case = 'compatibility'; result = 'FAIL'; error = $_.ToString() })
    throw
} finally {
    [ordered]@{ wall_seconds = $timer.Elapsed.TotalSeconds; cases = $cases.ToArray() } |
        ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $output 'results.json')
}

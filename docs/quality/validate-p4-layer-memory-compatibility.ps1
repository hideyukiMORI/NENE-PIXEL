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
    # Pinned by hash, not by commit (a commit pin breaks under rebase/squash; #145 T7c). SHA-256 of the
    # LF-normalised text of the historical analyzer's (de2b8e2) functions, of its Test-P4MemoryCapture statements
    # (joined by LF), and of its four result objects (ConvertTo-Json -Depth 10), recorded from HEAD after checking
    # HEAD equal to de2b8e2. Changing the historical baseline needs a commit that changes these tables.
    $historicalFunctionSha256 = [ordered]@{
        'Get-P4MemoryStatusBundles' = '9997663ffa428f2d7fe4851b87ba80f6ecb19cbdb9aa46d11d6bcf9cd11c6316'
        'Get-P4RequiredStatusValue' = 'a554622a022d0a93594c8c89dfbfb884ac9a4aec0e2400eb8bfec52e76f9868c'
        'ConvertFrom-P4MemoryReport' = '8194332662e97110ceb08bec82a6eae58200c2888e001662f354b1700663e594'
        'Get-P4RequiredInt64' = '5d38a1b9ffd269b327f6d61c23af57cab11b9c5a094f525e70e75543c5bf4822'
        'Assert-P4MemoryReportValue' = '8af6f5f4e873117bb71732d201cda7ac527ecbbda34aa319cf1353c1b0631f28'
    }
    $historicalDispatchSha256 = 'da3ec0e92e72cad8cc4df72388248a8e9979cdec0f9f5d3a31d1cf87d57b02a9'
    $historicalResultSha256 = [ordered]@{
        'baseline-common-drawing-history' = 'f1faefd05156baf3150fcbb2a3b81eb74f45017442d399e54e1940f11fd44210'
        'candidate-common-indexed-history' = '3ef4a505a88db14af94ab937954e54b234f457ee4cf10af690b944d33291a3fd'
        'candidate-palette-history' = 'c318e900285c519ffbdfb7e018914887865ee4726efeac1399cd700aec12cd06'
        'candidate-legacy-import' = '5d53eee696b038f54de68bd336af634fac10b479318cfc663d3c558e88f68347'
    }
    function Get-TextSha256 { param([string]$Text)
        [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($Text.Replace("`r`n", "`n")))).ToLowerInvariant()
    }
    $currentAst = Read-Ast $analysisPath
    foreach ($name in $historicalFunctionSha256.Keys) {
        Assert-Equal (Get-TextSha256 (Find-Function $currentAst $name).Extent.Text) $historicalFunctionSha256[$name] "unchanged $name"
    }
    $newBody = (Find-Function $currentAst 'Test-P4MemoryCapture').Body.EndBlock.Statements
    Assert-Equal (Get-TextSha256 (@($newBody | Select-Object -Skip 1 | ForEach-Object { $_.Extent.Text }) -join "`n")) `
        $historicalDispatchSha256 'historical dispatch body unchanged after explicit phase branch'
    $fixtureAst = Read-Ast (Join-Path $PSScriptRoot 'validate-p4-memory-evidence.ps1')
    . ([scriptblock]::Create((Find-Function $fixtureAst 'New-P4MemoryLines').Extent.Text))
    $commit = '0123456789abcdef0123456789abcdef01234567'
    $profile = 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36'
    foreach ($family in @('baseline-common-drawing-history', 'candidate-common-indexed-history',
        'candidate-palette-history', 'candidate-legacy-import')) {
        $lines = New-P4MemoryLines -Family $family
        $lines | Set-Content -LiteralPath (Join-Path $output "$family.txt")
        $actual = Test-P4MemoryCapture $lines $family 1 $commit
        Assert-Equal (Get-TextSha256 ($actual | ConvertTo-Json -Depth 10)) $historicalResultSha256[$family] "$family unchanged result"
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

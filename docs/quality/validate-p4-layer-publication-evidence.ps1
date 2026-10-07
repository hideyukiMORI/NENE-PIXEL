param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw 'A fresh evidence directory is required.' }
New-Item -ItemType Directory -Path $output | Out-Null
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-publication-analysis.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1')
$cases = [Collections.Generic.List[object]]::new()
$script:captureNumber = 0
$timer = [Diagnostics.Stopwatch]::StartNew()
$context = [ordered]@{
    protocol_id = 'nene-pixel-p4-layer-phase-verification-v1'; experiment_id = 'synthetic-layer-experiment'
    preflight_sha256 = 'a' * 64; preservation_sha256 = 'b' * 64; session = 'synthetic-layer-session'
    slot_id = 'publication-layers16-candidate'; artifact_role = 'candidate'; measurement_build_commit = 'c' * 40
    production_commit = 'd' * 40; app_apk_sha256 = '1' * 64; test_apk_sha256 = '2' * 64; publication_apk_sha256 = '3' * 64
}
function Assert-Contract { param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}
function Assert-Rejected { param([scriptblock]$Action)
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw 'Invalid publication evidence accepted.' }
}
function Copy-Context {
    $copy = [ordered]@{}
    foreach ($name in $context.Keys) { $copy[$name] = $context[$name] }
    return $copy
}
function Invoke-Case { param([string]$Name, [scriptblock]$Action)
    try {
        & $Action | Out-Null
        $cases.Add([ordered]@{ case = $Name; result = 'pass' }); "PASS $Name"
    } catch {
        $cases.Add([ordered]@{ case = $Name; result = 'FAIL'; error = $_.ToString() }); throw
    } finally {
        [ordered]@{ wall_seconds = $timer.Elapsed.TotalSeconds; cases = $cases.ToArray() } |
            ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $output 'results.json')
    }
}
function Read-Ast { param([string]$Path)
    $tokens = $null; $parseErrors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile($Path, [ref]$tokens, [ref]$parseErrors)
    if ($parseErrors.Count -ne 0) { throw "Parse error in $Path" }
    return $ast
}
function Get-FunctionText { param($Ast, [string]$Name)
    return $Ast.Find({ param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $Name
    }, $true).Extent.Text
}
$legacyFixtureAst = Read-Ast (Join-Path $PSScriptRoot 'validate-p4-publication-evidence.ps1')
foreach ($function in @('New-P4SyntheticPublicationCapture', 'Set-P4PublicationField')) {
    . ([scriptblock]::Create((Get-FunctionText $legacyFixtureAst $function)))
}
function New-Lines { param([long]$MaximumNanos = 200000000)
    $old = New-P4SyntheticPublicationCapture candidate $MaximumNanos
    return @($old.Lines -replace 'nene-pixel-p4-indexed-publication-device-v1', 'nene-pixel-p4-layer-publication-device-v1' `
        -replace 'candidate_v2_', 'candidate_v3_')
}
function New-Identity {
    $fields = [ordered]@{}
    foreach ($name in $context.Keys) { $fields[$name] = $context[$name] }
    $facts = [ordered]@{
        process_id = '3001'; process_start_elapsed_realtime_ms = '4001'; target_uid = '10005'
        target_package = 'io.github.hideyukimori.nenepixel.adapters.persistence.test'
        profile = 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36'
        fixture_sha256 = '165f62d180533849ce1a4ef1625cd3971e445f2dca60ef7b9b46fedaafa0b3ec'
        maximum_candidate_bytes = '1182885'; minimum_candidate_bytes = '85'
        warmup_count_per_group = '5'; sample_count_per_group = '20'; journal_rows = '54'
        worker_timeout_seconds = '60'; native_timeout_seconds = '300'; sample_anomaly_nanos = '5000000000'
        work_relative_path = 'no_backup/p4-layer-publication-aaaaaaaaaaaa'
        report_relative_path = 'files/p4-layer-publication-aaaaaaaaaaaa'
    }
    foreach ($name in $facts.Keys) { $fields[$name] = $facts[$name] }
    return 'P4_LAYER_PUBLICATION_IDENTITY ' + (@($fields.Keys | ForEach-Object { "$_=$($fields[$_])" }) -join ' ')
}
function New-Instrumentation { param([string]$Identity = (New-Identity))
    return @('INSTRUMENTATION_STATUS: class=publication', 'INSTRUMENTATION_STATUS_CODE: 1',
        "INSTRUMENTATION_STATUS: p4LayerPublicationIdentity=$Identity", 'INSTRUMENTATION_STATUS_CODE: 3',
        'INSTRUMENTATION_STATUS: class=publication', 'INSTRUMENTATION_STATUS_CODE: 0', 'INSTRUMENTATION_CODE: -1')
}
function Invoke-Capture {
    param([string[]]$Lines = (New-Lines), [string[]]$Status = @('complete'),
        [string[]]$Identity = @((New-Identity)), [string[]]$Instrumentation = (New-Instrumentation),
        $PhaseContext = $context, [string]$Role = 'candidate')
    $script:captureNumber++
    $stem = 'capture-{0:d4}' -f $script:captureNumber
    [ordered]@{ lines = $Lines; status = $Status; identity = $Identity; instrumentation = $Instrumentation;
        phase_context = $PhaseContext; role = $Role } | ConvertTo-Json -Depth 12 |
        Set-Content -LiteralPath (Join-Path $output "$stem-input.json")
    $result = Test-P4PublicationCapture -Lines $Lines -StatusLines $Status -Role $Role `
        -PhaseContext $PhaseContext -IdentityLines $Identity -InstrumentationLines $Instrumentation
    $result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $output "$stem-result.json")
    return $result
}

Invoke-Case 'fixed two storage slots after memory' {
    $slots = @(Get-P4LayerStorageSlotCatalog $context.protocol_id)
    Assert-Contract ($slots.Count -eq 2 -and $slots[0].id -ceq $context.slot_id -and $slots[0].sequence_index -eq 17 -and
        $slots[0].timeout_seconds -eq 300 -and $slots[0].journal_rows -eq 54 -and
        $slots[1].id -ceq 'saf-save-layers16-candidate' -and $slots[1].sequence_index -eq 18 -and
        $slots[1].timeout_seconds -eq 420 -and $slots[1].journal_rows -eq 27) 'Storage slot drift.'
    Assert-Rejected { Get-P4LayerStorageSlotCatalog 'wrong-protocol' }
}
foreach ($maximum in @(250000000L, 250000001L, 5000000000L)) {
    Invoke-Case "publication 250ms/5s boundary $maximum" {
        $result = Invoke-Capture -Lines (New-Lines $maximum)
        $expected = if ($maximum -eq 250000000L) { 'valid-constants-retained' } else { 'valid-constants-revision-required' }
        Assert-Contract ($result.verdict -ceq $expected -and $result.maximum_document_structural_byte_count -eq 1182885 -and
            $result.groups.candidate_v3_min.structural_byte_count -eq 85 -and $result.sample_count -eq 40) 'Wrong phase decision.'
        if ($maximum -eq 250000001L) {
            Assert-Contract ($result.rederived_quiet_ms -eq 1500 -and $result.rederived_latency_cap_ms -eq 5500) 'Wrong ADR0018 formula.'
        }
    }
}

$valid = New-Lines
$mutations = [ordered]@{
    'missing raw row' = { @($valid[0..5]) + @($valid[7..54]) }
    'extra raw row' = { $valid + $valid[54] }
    'swapped warmups' = { @($valid[0], $valid[2], $valid[1]) + @($valid[3..54]) }
    'wrong generation' = { Set-P4PublicationField $valid 6 6 '7' }
    'wrong sample index' = { Set-P4PublicationField $valid 6 3 '1' }
    'warmup marked sample' = { Set-P4PublicationField $valid 1 4 'sample' }
    'old schema relabel' = { $valid -replace 'layer-publication-device', 'indexed-publication-device' }
    'wrong group order' = { $valid -replace 'candidate_v3_max', 'candidate_v3_min' }
    'failed write row' = { Set-P4PublicationField $valid 6 7 'failed' }
    'summary value disagreement' = { Set-P4PublicationField $valid 27 5 '200000001' }
    'summary generation disagreement' = { Set-P4PublicationField $valid 27 6 '24' }
    'summary index disagreement' = { Set-P4PublicationField $valid 27 3 '18' }
    'maximum over five seconds' = { New-Lines 5000000001 }
    'warmup over five seconds' = { Set-P4PublicationField $valid 1 5 '5000000001' }
    'zero elapsed' = { Set-P4PublicationField $valid 1 5 '0' }
    'overflow elapsed' = { Set-P4PublicationField $valid 1 5 '9223372036854775808' }
    'fractional elapsed' = { Set-P4PublicationField $valid 1 5 '1.5' }
    'exponent elapsed' = { Set-P4PublicationField $valid 1 5 '1e3' }
    'noncanonical index' = { Set-P4PublicationField $valid 1 3 '00' }
    'negative generation' = { Set-P4PublicationField $valid 1 6 '-1' }
    'extra CSV field' = { @($valid[0], ($valid[1] + ',extra')) + @($valid[2..54]) }
}
foreach ($name in $mutations.Keys) { Invoke-Case $name { Assert-Rejected { Invoke-Capture -Lines (& $mutations[$name]) } } }
Invoke-Case 'all elapsed ties select the first measured sample' {
    $lines = [string[]]$valid.Clone()
    foreach ($index in 1..54) { $lines = Set-P4PublicationField $lines $index 5 '1000000' }
    foreach ($index in @(26, 27, 53, 54)) {
        $lines = Set-P4PublicationField $lines $index 3 '0'
        $lines = Set-P4PublicationField $lines $index 6 $(if ($index -le 27) { '6' } else { '31' })
    }
    $result = Invoke-Capture -Lines $lines
    Assert-Contract ($result.groups.candidate_v3_max.maximum_index -eq 0 -and $result.groups.candidate_v3_min.minimum_index -eq 0) 'Tie ordering changed.'
    $lines = Set-P4PublicationField $lines 27 3 '19'
    $lines = Set-P4PublicationField $lines 27 6 '25'
    Assert-Rejected { Invoke-Capture -Lines $lines }
}
Invoke-Case 'warmup and minimum-document values do not change maximum-document constant decision' {
    $lines = Set-P4PublicationField $valid 1 5 '400000000'
    $lines = Set-P4PublicationField $lines 52 5 '400000000'
    $lines = Set-P4PublicationField $lines 54 5 '400000000'
    $result = Invoke-Capture -Lines $lines
    Assert-Contract ($result.verdict -ceq 'valid-constants-retained' -and $result.maximum_document_maximum_nanos -eq 200000000) 'Populations pooled.'
}
foreach ($key in $context.Keys) {
    Invoke-Case "foreign or nonscalar $key" {
        $foreign = Copy-Context; $foreign[$key] = 'wrong'
        Assert-Rejected { Invoke-Capture -PhaseContext $foreign }
        $foreign = Copy-Context; $foreign[$key] = @($foreign[$key])
        Assert-Rejected { Invoke-Capture -PhaseContext $foreign }
    }
}
$identity = New-Identity
$identityMutations = [ordered]@{
    'fixture mismatch' = { $identity -replace 'fixture_sha256=[0-9a-f]{64}', ('fixture_sha256=' + 'f' * 64) }
    'wrong envelope size' = { $identity -replace 'maximum_candidate_bytes=1182885', 'maximum_candidate_bytes=1182884' }
    'wrong minimum size' = { $identity -replace 'minimum_candidate_bytes=85', 'minimum_candidate_bytes=77' }
    'foreign publication APK' = { $identity -replace 'publication_apk_sha256=3{64}', ('publication_apk_sha256=' + 'f' * 64) }
    'foreign test package' = { $identity -replace 'target_package=\S+', 'target_package=other.test' }
    'zero UID' = { $identity -replace 'target_uid=10005', 'target_uid=0' }
    'impossible PID' = { $identity -replace 'process_id=3001', 'process_id=2147483648' }
    'fractional start' = { $identity -replace 'process_start_elapsed_realtime_ms=4001', 'process_start_elapsed_realtime_ms=4001.1' }
    'missing identity field' = { $identity -replace ' profile=\S+', '' }
    'extra identity field' = { "$identity extra=1" }
    'duplicate identity field' = { "$identity journal_rows=54" }
    'changed bound' = { $identity -replace 'worker_timeout_seconds=60', 'worker_timeout_seconds=61' }
    'wrong work path' = { $identity -replace 'work_relative_path=no_backup/', 'work_relative_path=files/' }
    'wrong report prefix' = { $identity -replace 'report_relative_path=files/p4-layer', 'report_relative_path=files/wrong-layer' }
}
foreach ($name in $identityMutations.Keys) {
    Invoke-Case $name {
        $line = & $identityMutations[$name]
        Assert-Rejected { Invoke-Capture -Identity @($line) -Instrumentation (New-Instrumentation $line) }
    }
}
Invoke-Case 'persisted and emitted identities must be byte-equal' {
    Assert-Rejected { Invoke-Capture -Identity @($identity -replace 'process_id=3001', 'process_id=3002') }
    Assert-Rejected { Invoke-Capture -Identity @($identity, $identity) }
}
Invoke-Case 'complete status is exact and mandatory' {
    foreach ($status in @(@('invalid'), @('complete', 'complete'), @('Complete'), @(' complete'))) {
        Assert-Rejected { Invoke-Capture -Status $status }
    }
}
Invoke-Case 'phase selection is explicit and candidate only' {
    Assert-Rejected { Invoke-Capture -PhaseContext $null }
    Assert-Rejected { Invoke-Capture -Role baseline }
    Assert-Rejected { Test-P4PublicationCapture -Lines $valid -StatusLines @('complete') -Role candidate }
}
$instrumentation = New-Instrumentation
foreach ($replacement in @('INSTRUMENTATION_CODE: 0', 'INSTRUMENTATION_CODE: malformed', 'INSTRUMENTATION_FAILED', 'FATAL EXCEPTION')) {
    Invoke-Case "instrumentation failure $replacement" {
        Assert-Rejected { Invoke-Capture -Instrumentation (@($instrumentation[0..5]) + $replacement) }
    }
}
Invoke-Case 'missing duplicate or reordered instrumentation identity' {
    Assert-Rejected { Invoke-Capture -Instrumentation (@($instrumentation[0..1]) + @($instrumentation[4..6])) }
    Assert-Rejected { Invoke-Capture -Instrumentation (@($instrumentation[0..3]) + @($instrumentation[2..6])) }
    Assert-Rejected { Invoke-Capture -Instrumentation (@($instrumentation[0..1]) + @($instrumentation[4..5]) + @($instrumentation[2..3]) + $instrumentation[6]) }
    Assert-Rejected { Invoke-Capture -Instrumentation ($instrumentation + 'INSTRUMENTATION_CODE: -1') }
}
Invoke-Case 'historical helper sources and result objects unchanged' {
    # Pinned by hash, not by commit (a commit pin breaks under rebase/squash; #145 T7c). SHA-256 of the
    # LF-normalised text of the historical analyzer's (de2b8e2) helpers and of its four result objects
    # (ConvertTo-Json -Depth 10), recorded from HEAD after checking HEAD equal to de2b8e2. Changing the historical
    # baseline needs a commit that changes these tables.
    $historicalHelperSha256 = [ordered]@{
        'Read-P4PublicationPositiveInt64' = 'd14cc4ac2f85b621cd7a711fcc3e149a7c3cc79c3982ac43ab2af6cf29165dac'
        'Get-P4PublicationGroups' = 'ccd164d19a7a3adbd21d2ac0f0458c546476d7a42c0dd13630aa9ee244976d00'
        'Read-P4PublicationRow' = 'e251a72763bc38d84d43ccc319c14e69ef2147a54c06bac5bd36a79565b0ea2e'
    }
    $historicalResultSha256 = [ordered]@{
        'baseline-250000000' = 'cf1541e50db04cc5a37e7b4e1c4e9d940c242cea4ee7dfd45f3648f4917b9b67'
        'baseline-250000001' = 'd180f327b4f8568c46330229bafbfee1024fdccefdb2ef0980c68a2cfde8b8da'
        'candidate-250000000' = '1c3894c919729efa6b6e9e0a7c284ad12caef829193e21cb99127452c703c907'
        'candidate-250000001' = '9254f19f4f4453fcf25394f55716f949ecb1c078c5627a97883c99c126850509'
    }
    function Get-TextSha256 { param([string]$Text)
        [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($Text.Replace("`r`n", "`n")))).ToLowerInvariant()
    }
    $newAst = Read-Ast (Join-Path $PSScriptRoot 'measurements/p4-indexed-publication-analysis.ps1')
    foreach ($name in $historicalHelperSha256.Keys) {
        Assert-Contract ((Get-TextSha256 (Get-FunctionText $newAst $name)) -ceq $historicalHelperSha256[$name]) "Historical helper changed: $name"
    }
    foreach ($role in @('baseline', 'candidate')) {
        foreach ($maximum in @(250000000L, 250000001L)) {
            $fixture = New-P4SyntheticPublicationCapture $role $maximum
            $fixture | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $output "legacy-$role-$maximum-input.json")
            $actual = Test-P4PublicationCapture $fixture.Lines $fixture.StatusLines $role
            Assert-Contract ((Get-TextSha256 ($actual | ConvertTo-Json -Depth 10)) -ceq $historicalResultSha256["$role-$maximum"]) 'Historical result changed.'
            $actual | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $output "legacy-$role-$maximum-result.json")
        }
    }
}
Invoke-Case 'Android identity field and fixed-fact agreement' {
    $repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
    $source = Join-Path $repo 'adapters/persistence/src/androidTest/kotlin/io/github/hideyukimori/nenepixel/adapters/persistence/AutosaveLayerPublicationAdmission.kt'
    $kotlin = Get-Content -LiteralPath $source -Raw
    $fields = @([regex]::Matches($kotlin, '"([a-z_0-9]+)" to') | ForEach-Object { $_.Groups[1].Value })
    $expected = [ordered]@{}
    foreach ($token in @($identity -split ' ' | Select-Object -Skip 1)) {
        $pair = $token.Split('=', 2); $expected[$pair[0]] = $pair[1]
    }
    Assert-Contract ((($fields | Sort-Object) -join ',') -ceq (($expected.Keys | Sort-Object) -join ',')) 'Android identity fields differ.'
    foreach ($match in [regex]::Matches($kotlin, '"([a-z_0-9]+)" to "([^"$]+)"')) {
        Assert-Contract ($expected[$match.Groups[1].Value] -ceq $match.Groups[2].Value) 'Android fixed publication fact differs.'
    }
    Assert-Contract ($kotlin.Contains('const val MAXIMUM_CANDIDATE_BYTES: Int = 1_182_885')) 'Maximum envelope constant differs.'
    Assert-Contract ($kotlin.Contains('"165f62d180533849ce1a4ef1625cd3971e445f2dca60ef7b9b46fedaafa0b3ec"')) 'Fixture constant differs.'
}

param([Parameter(Mandatory)][string]$OutputDirectory, [Parameter(Mandatory)][string]$PublicationEvidenceDirectory)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw 'Fresh evidence directory required.' }
New-Item -ItemType Directory -Path $output | Out-Null
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-publication-analysis.ps1')
$cases = [Collections.Generic.List[object]]::new()
$script:number = 0
$timer = [Diagnostics.Stopwatch]::StartNew()
$context = [ordered]@{
    protocol_id = 'nene-pixel-p4-layer-phase-verification-v1'; experiment_id = 'synthetic-layer-experiment'
    preflight_sha256 = 'a' * 64; preservation_sha256 = 'b' * 64; session = 'synthetic-layer-session'
    slot_id = 'saf-save-layers16-candidate'; artifact_role = 'candidate'; measurement_build_commit = 'c' * 40
    production_commit = 'd' * 40; app_apk_sha256 = '1' * 64; test_apk_sha256 = '2' * 64
}
function Assert-Contract { param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}
function Assert-Rejected { param([scriptblock]$Action)
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw 'Invalid SAF evidence accepted.' }
}
function Copy-Record { param($Record)
    return ConvertFrom-Json -InputObject (ConvertTo-Json -InputObject $Record -Depth 20) -AsHashtable
}
function Invoke-Case { param([string]$Name, [scriptblock]$Action)
    try {
        & $Action | Out-Null
        $cases.Add([ordered]@{ case = $Name; result = 'pass' }); "PASS $Name"
    } catch {
        $cases.Add([ordered]@{ case = $Name; result = 'FAIL'; error = $_.ToString() }); throw
    } finally {
        [ordered]@{wall_seconds = $timer.Elapsed.TotalSeconds; cases = $cases.ToArray()} | ConvertTo-Json -Depth 10 |
            Set-Content -LiteralPath (Join-Path $output 'results.json')
    }
}
function New-Identity {
    $fields = Copy-Record $context
    $facts = [ordered]@{
        process_id = '3001'; process_start_elapsed_realtime_ms = '4001'; profile = 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36'
        fixture_uri = 'content://io.github.hideyukimori.nenepixel.test.acceptance.documents/document/i89-145-aaaaaaaaaaaa-saf-source.nenepixel'
        fixture_sha256 = '165f62d180533849ce1a4ef1625cd3971e445f2dca60ef7b9b46fedaafa0b3ec'
        fixture_bytes = '1182862'; grantee_uid = '10001'; provider_uid = '10002'; destination_count = '25'
        warmup_count = '5'; sample_count = '20'; journal_rows = '27'; setup_timeout_seconds = '300'
        worker_timeout_seconds = '60'; native_timeout_seconds = '420'; sample_anomaly_nanos = '5000000000'
        report_relative_path = 'files/p4-layer-saf-aaaaaaaaaaaa'
    }
    foreach ($name in $facts.Keys) { $fields[$name] = $facts[$name] }
    return 'P4_LAYER_SAF_IDENTITY ' + (@($fields.Keys | ForEach-Object { "$_=$($fields[$_])" }) -join ' ')
}
function New-Destination { param([int]$Ordinal)
    $kind = if ($Ordinal -lt 5) { 'warmup' } else { 'sample' }
    $index = if ($Ordinal -lt 5) { $Ordinal } else { $Ordinal - 5 }
    return [ordered]@{ kind = $kind; index = $index;
        uri = "content://io.github.hideyukimori.nenepixel.test.acceptance.documents/document/i89-145-aaaaaaaaaaaa-saf-$kind-$($index + 1).nenepixel" }
}
function New-Instrumentation { param([string]$Identity)
    return @('INSTRUMENTATION_STATUS: class=saf', 'INSTRUMENTATION_STATUS_CODE: 1',
        "INSTRUMENTATION_STATUS: p4LayerSafIdentity=$Identity", 'INSTRUMENTATION_STATUS_CODE: 3',
        'INSTRUMENTATION_STATUS: class=saf', 'INSTRUMENTATION_STATUS_CODE: 0', 'INSTRUMENTATION_CODE: -1')
}
function New-Dataset { param([long]$MaximumNanos = 2000000, [switch]$Ties)
    $identity = New-Identity
    $lines = [Collections.Generic.List[string]]::new()
    $lines.Add('schema,role,index,kind,elapsed_ns,destination_uri,grantee_uid,provider_uid,initial_byte_count,accepted_byte_count,picker_consumed,outcome,cleanup')
    $setup = [Collections.Generic.List[string]]::new()
    $setup.Add('kind,index,destination_uri,grantee_uid,provider_uid,byte_count')
    $setup.Add('source,0,content://io.github.hideyukimori.nenepixel.test.acceptance.documents/document/i89-145-aaaaaaaaaaaa-saf-source.nenepixel,10001,10002,1182862')
    $values = [long[]](0..19 | ForEach-Object {
        if ($Ties) { 1000000 } elseif ($_ -eq 19) { $MaximumNanos } else { 1000000 + $_ }
    })
    foreach ($ordinal in 0..24) {
        $destination = New-Destination $ordinal
        $elapsed = if ($ordinal -lt 5) { 400000000 } else { $values[$ordinal - 5] }
        $lines.Add("nene-pixel-p4-layer-saf-save-device-v1,candidate,$($destination.index),$($destination.kind),$elapsed,$($destination.uri),10001,10002,0,1182862,true,saved,not_needed")
        $setup.Add("$($destination.kind),$($destination.index),$($destination.uri),10001,10002,0")
    }
    $minimum = [array]::IndexOf($values, [long]($values | Measure-Object -Minimum).Minimum)
    $maximum = [array]::IndexOf($values, [long]($values | Measure-Object -Maximum).Maximum)
    $lines.Add($lines[$minimum + 6].Replace(',sample,', ',summary_min,'))
    $lines.Add($lines[$maximum + 6].Replace(',sample,', ',summary_max,'))
    return [ordered]@{ lines = $lines.ToArray(); status = @('complete'); identity = @($identity);
        setup = $setup.ToArray(); instrumentation = (New-Instrumentation $identity); phase_context = (Copy-Record $context) }
}
function Invoke-Capture { param($Dataset)
    $script:number++
    $stem = 'capture-{0:d4}' -f $script:number
    $Dataset | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $output "$stem-input.json")
    $result = Test-P4SafSaveCapture -Lines $Dataset.lines -StatusLines $Dataset.status -IdentityLines $Dataset.identity `
        -SetupLines $Dataset.setup -InstrumentationLines $Dataset.instrumentation -PhaseContext $Dataset.phase_context
    $result | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $output "$stem-result.json")
    return $result
}
function Set-CsvField { param([string]$Line, [int]$Index, [string]$Value)
    $fields = @($Line -split ','); $fields[$Index] = $Value; return $fields -join ','
}
$valid = New-Dataset
Invoke-Case 'full setup journal and descriptive extrema' {
    $result = Invoke-Capture $valid
    Assert-Contract ($result.verdict -ceq 'valid-descriptive' -and $result.sample_count -eq 20 -and $result.destination_count -eq 25 -and
        $result.minimum_nanos -eq 1000000 -and $result.maximum_nanos -eq 2000000 -and $result.maximum_index -eq 19) 'Wrong SAF summary.'
}
foreach ($maximum in @(250000001L, 5000000000L)) {
    Invoke-Case "SAF descriptive success at $maximum nanoseconds" {
        Assert-Contract ((Invoke-Capture (New-Dataset $maximum)).verdict -ceq 'valid-descriptive') 'Autosave gate incorrectly applied to SAF.'
    }
}
Invoke-Case 'SAF first tie is used for both summaries' {
    $fixture = New-Dataset -Ties
    $result = Invoke-Capture $fixture
    Assert-Contract ($result.minimum_index -eq 0 -and $result.maximum_index -eq 0) 'SAF tie order changed.'
    $fixture.lines[27] = $fixture.lines[25].Replace(',sample,', ',summary_max,')
    Assert-Rejected { Invoke-Capture $fixture }
}
$mutations = [ordered]@{
    'missing operation' = { $script:bad.lines = @($script:bad.lines[0..5]) + @($script:bad.lines[7..27]) }
    'extra operation' = { $script:bad.lines += $script:bad.lines[25] }
    'wrong row order' = { $script:bad.lines[1] = $script:bad.lines[2] }
    'wrong sample kind' = { $script:bad.lines[6] = Set-CsvField $script:bad.lines[6] 3 'warmup' }
    'wrong sample index' = { $script:bad.lines[6] = Set-CsvField $script:bad.lines[6] 2 '1' }
    'URI reused' = { $script:bad.lines[7] = Set-CsvField $script:bad.lines[7] 5 ((New-Destination 5).uri) }
    'foreign URI' = { $script:bad.lines[6] = Set-CsvField $script:bad.lines[6] 5 'content://other/foreign' }
    'wrong grantee' = { $script:bad.lines[6] = Set-CsvField $script:bad.lines[6] 6 '10003' }
    'wrong provider' = { $script:bad.lines[6] = Set-CsvField $script:bad.lines[6] 7 '10003' }
    'nonempty initial file' = { $script:bad.lines[6] = Set-CsvField $script:bad.lines[6] 8 '1' }
    'wrong accepted size' = { $script:bad.lines[6] = Set-CsvField $script:bad.lines[6] 9 '1182861' }
    'unverified size' = { $script:bad.lines[6] = Set-CsvField $script:bad.lines[6] 9 'not_verified' }
    'picker unconsumed' = { $script:bad.lines[6] = Set-CsvField $script:bad.lines[6] 10 'false' }
    'failed save' = { $script:bad.lines[6] = Set-CsvField $script:bad.lines[6] 11 'failed' }
    'cancelled save' = { $script:bad.lines[6] = Set-CsvField $script:bad.lines[6] 11 'cancelled' }
    'cleanup invoked' = { $script:bad.lines[6] = Set-CsvField $script:bad.lines[6] 12 'deleted' }
    'summary altered' = { $script:bad.lines[27] = Set-CsvField $script:bad.lines[27] 4 '2000001' }
    'summary URI altered' = { $script:bad.lines[27] = Set-CsvField $script:bad.lines[27] 5 ((New-Destination 23).uri) }
    'partial setup' = { $script:bad.setup = @($script:bad.setup[0..25]) }
    'duplicate setup' = { $script:bad.setup[2] = $script:bad.setup[1] }
    'setup reordered' = { $script:bad.setup[2] = $script:bad.setup[3] }
    'source size changed' = { $script:bad.setup[1] = Set-CsvField $script:bad.setup[1] 5 '1182861' }
    'setup destination nonempty' = { $script:bad.setup[2] = Set-CsvField $script:bad.setup[2] 5 '1' }
    'setup grant changed' = { $script:bad.setup[2] = Set-CsvField $script:bad.setup[2] 3 '10003' }
    'status incomplete' = { $script:bad.status = @('invalid') }
    'status duplicate' = { $script:bad.status = @('complete', 'complete') }
    'persisted identity missing' = { $script:bad.identity = @() }
    'persisted identity duplicate' = { $script:bad.identity += $script:bad.identity[0] }
    'saved/emitted identity drift' = { $script:bad.identity[0] = $script:bad.identity[0] -replace 'process_id=3001', 'process_id=3002' }
    'instrumentation failure' = { $script:bad.instrumentation[-1] = 'INSTRUMENTATION_CODE: 0' }
    'instrumentation malformed completion' = { $script:bad.instrumentation += 'INSTRUMENTATION_CODE: broken' }
    'instrumentation identity absent' = { $script:bad.instrumentation = @($script:bad.instrumentation[0..1]) + @($script:bad.instrumentation[4..6]) }
    'instrumentation identity extra' = { $script:bad.instrumentation = @($script:bad.instrumentation[0..3]) + @($script:bad.instrumentation[2..6]) }
    'instrumentation malformed status' = { $script:bad.instrumentation[3] = 'INSTRUMENTATION_STATUS_CODE: broken' }
    'instrumentation fatal' = { $script:bad.instrumentation = @('FATAL EXCEPTION') + $script:bad.instrumentation }
}
foreach ($name in $mutations.Keys) {
    Invoke-Case $name {
        $script:bad = Copy-Record $valid
        & $mutations[$name]
        Assert-Rejected { Invoke-Capture $script:bad }
    }
}
foreach ($elapsed in @('0', '-1', '1.5', '1e3', '01', '5000000001', '9223372036854775808')) {
    Invoke-Case "invalid elapsed $elapsed" {
        $fixture = Copy-Record $valid; $fixture.lines[6] = Set-CsvField $fixture.lines[6] 4 $elapsed
        Assert-Rejected { Invoke-Capture $fixture }
    }
}
foreach ($key in $context.Keys) {
    Invoke-Case "SAF context $key refuses foreign values and arrays" {
        $fixture = Copy-Record $valid; $fixture.phase_context[$key] = 'wrong'
        Assert-Rejected { Invoke-Capture $fixture }
        $fixture = Copy-Record $valid; $fixture.phase_context[$key] = @($fixture.phase_context[$key])
        Assert-Rejected { Invoke-Capture $fixture }
    }
}
foreach ($mutation in @(@('provider_uid=10002', 'provider_uid=10001'), @('grantee_uid=10001', 'grantee_uid=0'),
    @('fixture_bytes=1182862', 'fixture_bytes=1182861'), @('worker_timeout_seconds=60', 'worker_timeout_seconds=61'),
    @('process_id=3001', 'process_id=2147483648'), @('journal_rows=27', 'journal_rows=28'),
    @('destination_count=25', 'destination_count=24'), @('sample_count=20', 'sample_count=19'))) {
    Invoke-Case "SAF identity refuses $($mutation[1])" {
        $fixture = Copy-Record $valid
        $fixture.identity[0] = $fixture.identity[0].Replace($mutation[0], $mutation[1])
        $fixture.instrumentation = New-Instrumentation $fixture.identity[0]
        Assert-Rejected { Invoke-Capture $fixture }
    }
}
Invoke-Case 'SAF explicit null phase context refuses' {
    $fixture = Copy-Record $valid; $fixture.phase_context = $null
    Assert-Rejected { Invoke-Capture $fixture }
}
$publication = Get-Content -LiteralPath (Join-Path $PublicationEvidenceDirectory 'capture-0001-input.json') -Raw | ConvertFrom-Json -AsHashtable
function Invoke-Publication { param($Fixture)
    return Test-P4PublicationCapture -Lines $Fixture.lines -StatusLines $Fixture.status -Role $Fixture.role `
        -PhaseContext $Fixture.phase_context -IdentityLines $Fixture.identity -InstrumentationLines $Fixture.instrumentation
}
Invoke-Case 'publication shared context dispatch preserves the recorded result' {
    $expected = Get-Content -LiteralPath (Join-Path $PublicationEvidenceDirectory 'capture-0001-result.json') -Raw | ConvertFrom-Json -AsHashtable
    $actual = Invoke-Publication $publication
    Assert-Contract (($actual | ConvertTo-Json -Depth 20) -ceq ($expected | ConvertTo-Json -Depth 20)) 'Shared dispatch changed publication result.'
    $actual | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $output 'publication-dispatch-result.json')
}
foreach ($key in $publication.phase_context.Keys) {
    Invoke-Case "publication shared context rejects $key drift" {
        $foreign = Copy-Record $publication; $foreign.phase_context[$key] = 'wrong'
        Assert-Rejected { Invoke-Publication $foreign }
    }
}
Invoke-Case 'shared storage status helper rejects swapped lane keys' {
    $foreign = Copy-Record $publication
    $foreign.instrumentation = $foreign.instrumentation -replace 'p4LayerPublicationIdentity', 'p4LayerSafIdentity'
    Assert-Rejected { Invoke-Publication $foreign }
    $foreign = Copy-Record $valid
    $foreign.instrumentation = $foreign.instrumentation -replace 'p4LayerSafIdentity', 'p4LayerPublicationIdentity'
    Assert-Rejected { Invoke-Capture $foreign }
}
Invoke-Case 'Android SAF fixed identity facts agree with the synthetic contract' {
    $repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
    $path = Join-Path $repo 'app/android/src/androidTest/kotlin/io/github/hideyukimori/nenepixel/measurement/P4LayerSafIdentity.kt'
    $kotlin = Get-Content -LiteralPath $path -Raw
    $facts = [ordered]@{}
    foreach ($token in @($valid.identity[0] -split ' ' | Select-Object -Skip 1)) {
        $pair = $token.Split('=', 2); $facts[$pair[0]] = $pair[1]
    }
    $keys = @([regex]::Matches($kotlin, '"([a-z_0-9]+)" to') | ForEach-Object { $_.Groups[1].Value })
    $expected = @($facts.Keys | Where-Object { $_ -cnotin $context.Keys })
    Assert-Contract ((($keys | Sort-Object) -join ',') -ceq (($expected | Sort-Object) -join ',')) 'Android SAF field set differs.'
    foreach ($match in [regex]::Matches($kotlin, '"([a-z_0-9]+)" to "([^"$]+)"')) {
        Assert-Contract ($facts[$match.Groups[1].Value] -ceq $match.Groups[2].Value) 'Android SAF fixed fact differs.'
    }
}

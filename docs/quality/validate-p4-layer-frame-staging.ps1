# #145 functional fixture identity only; no device call or performance population.
param([Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$HostContractDirectory,
    [switch]$SourceAgreementOnly)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
$repository = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$lab = [IO.Path]::GetFullPath((Get-NenePixelLabPath 'evidence' -StartDirectory $PSScriptRoot)).TrimEnd('\', '/')
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $OutputDirectory.StartsWith($lab + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    [IO.File]::Exists($OutputDirectory) -or [IO.Directory]::Exists($OutputDirectory)) { throw 'Fresh lab output required.' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-frame-analysis.ps1')
$phase = 'nene-pixel-p4-layer-phase-verification-v1'
$script:cases = [Collections.Generic.List[object]]::new()
$watch = [Diagnostics.Stopwatch]::StartNew()
$result = [ordered]@{ status = 'failure'; cases = @(); elapsed_seconds = 0; source_sha256 = [ordered]@{} }

function Check([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Copy-Evidence($Evidence) { ConvertFrom-Json -AsHashtable -InputObject ($Evidence | ConvertTo-Json -Depth 15) }
function New-Evidence($Slot) {
    $artifact = @(Get-P4LayerArtifactCatalog $phase | Where-Object { $_.role -ceq $Slot.artifact_role })[0]
    $context = [ordered]@{ protocol_id = $phase; experiment_id = 'phase-staging-contract'; preflight_sha256 = 'a' * 64
        preservation_sha256 = 'b' * 64; session = 'phase-staging-contract'; slot_id = $Slot.id; artifact_role = $Slot.artifact_role
        measurement_build_commit = 'c' * 40; production_commit = $artifact.production_commit
        app_apk_sha256 = 'd' * 64; test_apk_sha256 = 'e' * 64 }
    $layered = $Slot.group_id -ceq 'layers16'
    $name = "i89-145-$('a' * 12)-frame-$($Slot.run)-$($Slot.group_id)." + $(if ($layered) { 'nenepixel' } else { 'png' })
    $fields = [ordered]@{}
    foreach ($key in $context.Keys) { $fields[$key] = $context[$key] }
    $fields.group_id = $Slot.group_id; $fields.fixture_name = $name
    $fields.fixture_asset = if ($layered) { 'maximum-layered.nenepixel' } else { 'underlay-grid.png' }
    $fields.fixture_bytes = if ($layered) { '1182862' } else { '184323' }
    $fields.fixture_sha256 = if ($layered) { '165f62d180533849ce1a4ef1625cd3971e445f2dca60ef7b9b46fedaafa0b3ec' }
        else { '05efb3fc8edf43f45dc5a8b7cae3be148680c5694b47c47d1cf4eb7cbe26c6fb' }
    $fields.fixture_uri = "content://io.github.hideyukimori.nenepixel.test.acceptance.documents/document/$name"
    $fields.grantee_uid = '10100'; $fields.provider_uid = '10101'; $fields.process_id = '2501'
    $fields.process_start_elapsed_realtime_millis = '123456789'
    $fields.report_path = "files/p4-layer-frame-fixture-$('a' * 12)-$($Slot.run)/fixture.txt"
    return [ordered]@{ PhaseContext = $context; fields = $fields }
}
function Invoke-Case([string]$Name, $Evidence, [switch]$Refuse, [scriptblock]$Mutate = {}) {
    $id = 'case-{0:D3}' -f ($script:cases.Count + 1)
    $line = 'P4_LAYER_FRAME_FIXTURE ' + (($Evidence.fields.Keys | ForEach-Object { "$_=$($Evidence.fields[$_])" }) -join ' ')
    $capture = [ordered]@{
        PhaseContext = $Evidence.PhaseContext; IdentityLines = @($line)
        InstrumentationLines = @('INSTRUMENTATION_STATUS: class=fixture', 'INSTRUMENTATION_STATUS_CODE: 1',
            "INSTRUMENTATION_STATUS: p4LayerFrameFixture=$line", 'INSTRUMENTATION_STATUS_CODE: 3',
            'INSTRUMENTATION_STATUS: class=fixture', 'INSTRUMENTATION_STATUS_CODE: 0', 'OK (1 test)', 'INSTRUMENTATION_CODE: -1')
    }
    & $Mutate $capture
    [IO.File]::WriteAllText((Join-Path $OutputDirectory "$id-input.json"), ($capture | ConvertTo-Json -Depth 12))
    $failure = $null; $observed = $null
    try { $observed = Test-P4LayerFrameFixturePreparation @capture } catch { $failure = $_.Exception.ToString() }
    $pass = if ($Refuse) { $null -ne $failure } else { $null -eq $failure -and $observed.verdict -ceq 'prepared-source' }
    $record = [ordered]@{ name = $Name; passed = $pass; expected_refusal = [bool]$Refuse; result = $observed; error = $failure }
    $script:cases.Add($record)
    [IO.File]::WriteAllText((Join-Path $OutputDirectory "$id-result.json"), ($record | ConvertTo-Json -Depth 15))
    Check $pass "Staging case failed: $Name : $failure"
}

try {
    $hostRows = @(Import-Csv -LiteralPath (Join-Path $HostContractDirectory 'fixture-specs.csv'))
    $slots = @(Get-P4FrameSlotCatalog $phase | Where-Object { $_.group_id -cne 'single' })
    $base = New-Evidence $slots[0]
    if (-not $SourceAgreementOnly) {
    Check ($hostRows.Count -eq 8 -and $slots.Count -eq 8) 'Host/phase population differs'
    for ($i = 0; $i -lt 8; $i++) {
        $evidence = New-Evidence $slots[$i]
        $row = $hostRows[$i]
        Check ($row.slot_id -ceq $slots[$i].id -and $row.artifact_role -ceq $slots[$i].artifact_role -and
            $row.fixture_name -ceq $evidence.fields.fixture_name -and $row.fixture_asset -ceq $evidence.fields.fixture_asset -and
            ('files/' + $row.report_directory + '/fixture.txt') -ceq $evidence.fields.report_path) 'Actual Kotlin spec disagrees with canonical host catalog'
        Invoke-Case "valid staged source $($slots[$i].id)" $evidence
    }
    foreach ($key in $base.PhaseContext.Keys) {
        $bad = Copy-Evidence $base
        $bad.PhaseContext[$key] = if ($key -like '*sha256') { 'f' * 64 }
            elseif ($key -like '*commit') { 'f' * 40 } else { 'foreign' }
        Invoke-Case "foreign context $key" $bad -Refuse
    }
    foreach ($key in @('group_id', 'fixture_name', 'fixture_asset', 'fixture_bytes', 'fixture_sha256', 'fixture_uri', 'report_path')) {
        $bad = Copy-Evidence $base; $bad.fields[$key] = 'foreign'
        Invoke-Case "wrong fixed fact $key" $bad -Refuse
    }
    foreach ($key in @('process_id', 'process_start_elapsed_realtime_millis', 'grantee_uid', 'provider_uid')) {
        foreach ($number in @('0', '01', '1.1')) {
            $bad = Copy-Evidence $base; $bad.fields[$key] = $number
            Invoke-Case "invalid integer $key $number" $bad -Refuse
        }
    }
    $bad = Copy-Evidence $base; $bad.fields.provider_uid = $bad.fields.grantee_uid
    Invoke-Case 'in-process provider refused' $bad -Refuse
    $bad = Copy-Evidence $base; $bad.fields.process_id = '2147483648'
    Invoke-Case 'out-of-range process refused' $bad -Refuse
    Invoke-Case 'missing persisted identity' $base -Refuse -Mutate { param($c) $c.IdentityLines = @() }
    Invoke-Case 'duplicate persisted identity' $base -Refuse -Mutate { param($c) $c.IdentityLines += $c.IdentityLines[0] }
    Invoke-Case 'saved-emitted identity differs' $base -Refuse -Mutate { param($c) $c.InstrumentationLines[2] += ' extra=value' }
    Invoke-Case 'wrong emitted key' $base -Refuse -Mutate { param($c) $c.InstrumentationLines[2] = $c.InstrumentationLines[2].Replace('p4LayerFrameFixture=', 'p4LayerSafIdentity=') }
    Invoke-Case 'late activity-close/native failure' $base -Refuse -Mutate { param($c) $c.InstrumentationLines[5] = 'INSTRUMENTATION_STATUS_CODE: -2' }
    Invoke-Case 'post-terminal evidence refused' $base -Refuse -Mutate { param($c) $c.InstrumentationLines += 'trailing' }
    Invoke-Case 'unclosed instrumentation' $base -Refuse -Mutate { param($c) $c.InstrumentationLines = @($c.InstrumentationLines[0..6]) }
    Invoke-Case 'duplicate report field' $base -Refuse -Mutate { param($c) $c.IdentityLines[0] += ' process_id=2501' }
    Invoke-Case 'null context' $base -Refuse -Mutate { param($c) $c.PhaseContext = $null }
    }

    $measurement = Join-Path $repository 'app/android/src/androidTest/kotlin/io/github/hideyukimori/nenepixel/measurement'
    $producer = Get-Content -Raw (Join-Path $measurement 'P4LayerFrameFixturePreparationTest.kt')
    $keys = @([regex]::Matches($producer, '"([a-z0-9_]+)" to ') | ForEach-Object { $_.Groups[1].Value })
    Check (($keys | Sort-Object) -join ',' -ceq ((@($base.fields.Keys | Where-Object { $_ -cnotin $base.PhaseContext.Keys }) | Sort-Object) -join ',')) 'Android emitted field set differs'
    Check ($producer.Contains('Timeout.seconds(240)') -and $producer.Contains('p4LayerFrameFixture') -and
        $producer.Contains('P4_LAYER_FRAME_FIXTURE ') -and $producer.Contains('documents.stage(spec.name, spec.fixture)')) 'Android setup binding differs'
    $journal = Get-Content -Raw (Join-Path $measurement 'P4LayerMemoryJournal.kt')
    $guardOffset = $journal.IndexOf('check(admission.artifactRole == "baseline_layers16" || admission.artifactRole == "candidate")')
    Check ($guardOffset -ge 0 -and $guardOffset -lt $journal.IndexOf('val processId = Process.myPid()')) 'Memory role guard must precede process evidence'
    $required = @(Get-P4LayerRequiredMeasurementPaths $phase)
    foreach ($name in @('P4LayerFrameFixtureSpec.kt', 'P4LayerFrameFixturePreparationTest.kt')) {
        Check (@($required | Where-Object { $_.EndsWith('/' + $name, [StringComparison]::Ordinal) }).Count -eq 1) 'New mandatory inventory path missing'
    }
    foreach ($path in @($PSCommandPath, (Join-Path $PSScriptRoot 'measurements/p4-indexed-frame-analysis.ps1'),
            (Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1'),
            (Join-Path $PSScriptRoot 'measurements/p4-indexed-memory-analysis.ps1'),
            (Join-Path $PSScriptRoot 'measurements/p4-indexed-publication-analysis.ps1'),
            (Join-Path $HostContractDirectory 'fixture-specs.csv'))) {
        $result.source_sha256[$path] = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
    }
    $result.status = 'pass'
} finally {
    $watch.Stop(); $result.elapsed_seconds = $watch.Elapsed.TotalSeconds; $result.cases = $script:cases.ToArray()
    [IO.File]::WriteAllText((Join-Path $OutputDirectory 'results.json'), ($result | ConvertTo-Json -Depth 18), [Text.UTF8Encoding]::new($false))
}
if ($SourceAgreementOnly) { 'PASS: Android source binding only; retained identity/catalog cases were not rerun' }
else { "PASS: $($script:cases.Count) fixture identity cases, actual Kotlin/host catalog agreement and Android source binding" }

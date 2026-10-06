param([Parameter(Mandatory)][string]$OutputDirectory,
    [ValidateSet('All', 'Integrity')][string]$CaseGroup = 'All', [string]$PriorCaptureDirectory)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw 'A new evidence directory is required.' }
New-Item -ItemType Directory -Path $output | Out-Null
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-memory-analysis.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1')
$script:cases = [Collections.Generic.List[object]]::new()
$script:captureNumber = 0
$timer = [Diagnostics.Stopwatch]::StartNew()
$protocol = 'nene-pixel-p4-layer-phase-verification-v1'
$checkpoints = @('empty_idle', 'maximum_loaded_idle', 'long_preview_held', 'committed_idle', 'post_cycles_idle')

function Assert-Contract { param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}
function Assert-Rejected { param([scriptblock]$Action)
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw 'Invalid evidence was accepted.' }
}
function Copy-Record { param($Record)
    return ConvertFrom-Json -InputObject (ConvertTo-Json -InputObject $Record -Depth 30) -AsHashtable
}
function Invoke-Case { param([string]$Name, [scriptblock]$Action)
    try {
        & $Action | Out-Null
        $script:cases.Add([ordered]@{ case = $Name; result = 'pass' })
        Write-Output "PASS $Name"
    } catch {
        $script:cases.Add([ordered]@{ case = $Name; result = 'FAIL'; error = $_.ToString() })
        throw
    } finally {
        [ordered]@{ wall_seconds = $timer.Elapsed.TotalSeconds; cases = $script:cases.ToArray() } |
            ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $output 'results.json')
    }
}
function New-Context { param([int]$Sequence = 1)
    $baseline = $Sequence -le 5
    $role = if ($baseline) { 'baseline' } else { 'candidate' }
    $run = if ($baseline) { $Sequence } else { $Sequence - 5 }
    return [ordered]@{
        protocol_id = $protocol; experiment_id = 'synthetic-layer-experiment'; preflight_sha256 = 'a' * 64
        preservation_sha256 = 'b' * 64; session = 'synthetic-layer-session'; slot_id = "memory-layers16-$role-$run"
        artifact_role = if ($baseline) { 'baseline_layers16' } else { 'candidate' }
        measurement_build_commit = if ($baseline) { 'c' * 40 } else { 'd' * 40 }
        production_commit = if ($baseline) { '169b59287ca60e77e07ac91690450dd1a44b9ba4' } else { 'e' * 40 }
        app_apk_sha256 = if ($baseline) { '1' * 64 } else { '2' * 64 }
        test_apk_sha256 = if ($baseline) { '3' * 64 } else { '4' * 64 }
    }
}
function Format-Record { param([string]$Prefix, $Fields)
    return $Prefix + ' ' + (@($Fields.Keys | ForEach-Object { "$_=$($Fields[$_])" }) -join ' ')
}
function New-Lines {
    param([int]$Sequence = 1, [long[]]$Java = @(10MB, 20MB, 20MB, 20MB, 20MB),
        [long[]]$Pss = @(10000, 20000, 20000, 20000, 20000),
        [long]$Maximum = 100MB, [long]$MemoryClass = 100, $Context = (New-Context $Sequence))
    $run = ($Sequence - 1) % 5 + 1
    $role = if ($Sequence -le 5) { 'baseline' } else { 'candidate' }
    $family = "$role-layer-editor-retention"
    $lines = [Collections.Generic.List[string]]::new()
    $identity = [ordered]@{
        p4MemoryFamily = $family; p4MemoryBuildCommit = $Context.measurement_build_commit; p4MemoryRunIndex = $run
        p4MemoryProcessId = 1000 + $Sequence; p4MemoryProcessStartElapsedRealtimeMillis = 2000 + $Sequence
        p4MemoryRuntimeMaxMemoryBytes = $Maximum; p4MemoryClassMebibytes = $MemoryClass
        p4LayerIdentity = Format-Record 'P4_LAYER_IDENTITY' $Context
    }
    foreach ($key in $identity.Keys) { $lines.Add("INSTRUMENTATION_STATUS: $key=$($identity[$key])") }
    $lines.Add('INSTRUMENTATION_STATUS_CODE: 3')
    foreach ($index in 0..4) {
        $observation = [ordered]@{
            run = $run; slot_id = $Context.slot_id; index = $index; checkpoint = $checkpoints[$index]
            java_bytes = $Java[$index]; java_committed_bytes = $Maximum; pss_kib = $Pss[$index]
            dalvik_pss_kib = 0; native_pss_kib = 0; other_pss_kib = 0; private_dirty_kib = 0; shared_dirty_kib = 0
        }
        $lines.Add('INSTRUMENTATION_STATUS: p4LayerMemoryCheckpoint=' + (Format-Record 'P4_LAYER_CHECKPOINT' $observation))
        $lines.Add('INSTRUMENTATION_STATUS_CODE: 5')
    }
    $facts = [ordered]@{
        schema = 'nene-pixel-p4-layer-editor-retention-v1'; family = $family; run = $run; run_status = 'valid'
        profile = 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36'
        fixture_sha256 = '165f62d180533849ce1a4ef1625cd3971e445f2dca60ef7b9b46fedaafa0b3ec'
        fixture_uri = "content://io.github.hideyukimori.nenepixel.test.acceptance.documents/document/i89-145-aaaaaaaaaaaa-$role-$run.nenepixel"
        grantee_uid = 10001; provider_uid = 10002; checkpoint_count = 5; document_layers = 16; canvas_width = 256
        canvas_height = 256; preview_move_count = 16; preview_raw_position_count = 4081; preview_unique_position_count = 256
        undo_redo_cycles = 10; gc_passes = 2; published_position_verified = 'true'
        owner_inventory = 'main_activity:1,viewmodel:1,compose_canvas:1'
    }
    foreach ($key in $Context.Keys) { $facts[$key] = $Context[$key] }
    foreach ($index in 0..4) {
        $facts["$($checkpoints[$index])_java_bytes"] = $Java[$index]
        $facts["$($checkpoints[$index])_pss_kib"] = $Pss[$index]
    }
    $lines.Add('INSTRUMENTATION_STATUS: p4MemoryReport=' + (Format-Record 'P4_LAYER_EDITOR_RETENTION' $facts))
    $lines.Add('INSTRUMENTATION_STATUS_CODE: 4')
    $lines.Add('INSTRUMENTATION_CODE: -1')
    return $lines.ToArray()
}
function Invoke-Capture {
    param([int]$Sequence = 1, [string[]]$Lines = (New-Lines $Sequence),
        $Context = (New-Context $Sequence), [object[]]$Prior = @())
    $script:captureNumber++
    $stem = 'capture-{0:d4}' -f $script:captureNumber
    $Lines | Set-Content -LiteralPath (Join-Path $output "$stem.txt")
    [ordered]@{ phase_context = $Context; prior = $Prior } | ConvertTo-Json -Depth 30 |
        Set-Content -LiteralPath (Join-Path $output "$stem-input.json")
    $family = if ($Sequence -le 5) { 'baseline-layer-editor-retention' } else { 'candidate-layer-editor-retention' }
    $result = Test-P4MemoryCapture -Lines $Lines -Family $family -RunIndex (($Sequence - 1) % 5 + 1) `
        -BuildCommit $Context.measurement_build_commit -PhaseContext $Context -PriorRuns $Prior
    $result | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath (Join-Path $output "$stem-result.json")
    return $result
}

if ($CaseGroup -ceq 'All') {
Invoke-Case 'fixed ten-slot catalog' {
    $slots = @(Get-P4LayerMemorySlotCatalog -ProtocolId $protocol)
    Assert-Contract ($slots.Count -eq 10) 'Wrong slot count.'
    foreach ($sequence in 1..10) {
        $slot = $slots[$sequence - 1]; $context = New-Context $sequence
        Assert-Contract ($slot.id -ceq $context.slot_id -and $slot.artifact_role -ceq $context.artifact_role -and
            $slot.sequence_index -eq 6 + $sequence -and $slot.memory_sequence_index -eq $sequence -and
            $slot.run -eq ($sequence - 1) % 5 + 1 -and $slot.timeout_seconds -eq 300 -and
            ($slot.checkpoints -join ',') -ceq ($checkpoints -join ',')) 'Catalog drift.'
    }
    Assert-Rejected { Get-P4LayerMemorySlotCatalog -ProtocolId 'historical-or-unknown' }
}
$script:chain = @()
Invoke-Case 'ten fresh processes and serialized predecessor chains' {
    foreach ($sequence in 1..10) {
        $result = Invoke-Capture -Sequence $sequence -Prior $script:chain
        Assert-Contract ($result.verdict -ceq 'pass') 'Valid phase capture failed.'
        $isFifth = $sequence % 5 -eq 0
        foreach ($value in $result.family_median_pss_delta_kib.Values) {
            Assert-Contract (($isFifth -and $value -eq 10000) -or (-not $isFifth -and $null -eq $value)) 'Invalid median population.'
        }
        $script:chain += Copy-Record $result
    }
}

foreach ($index in 1..4) {
    Invoke-Case "C$index Java half-heap inclusive boundary" {
        $java = @(10MB, 20MB, 20MB, 20MB, 20MB)
        $java[$index] = 50MB
        if ($index -eq 4) { $java[3] = 50MB }
        $result = Invoke-Capture -Lines (New-Lines -Java $java)
        Assert-Contract ($result.verdict -ceq 'pass') 'Exact Java boundary rejected.'
        $java[$index]++
        $result = Invoke-Capture -Lines (New-Lines -Java $java)
        Assert-Contract ($result.verdict -ceq 'PERFORMANCE_FAIL') 'Java excess accepted.'
    }
    Invoke-Case "C$index individual PSS inclusive boundary" {
        $pss = @(10000, 20000, 20000, 20000, 20000); $pss[$index] = 71440
        Assert-Contract ((Invoke-Capture -Lines (New-Lines -Pss $pss)).verdict -ceq 'pass') 'Exact PSS boundary rejected.'
        $pss[$index]++
        Assert-Contract ((Invoke-Capture -Lines (New-Lines -Pss $pss)).verdict -ceq 'PERFORMANCE_FAIL') 'PSS excess accepted.'
    }
    Invoke-Case "C$index five-run median inclusive boundary and no pooling" {
        foreach ($delta in @(51200, 51201)) {
            $prior = @()
            foreach ($sequence in 1..5) {
                $pss = @(10000, 20000, 20000, 20000, 20000)
                if ($sequence -le 3) { $pss[$index] = 10000 + $delta }
                $result = Invoke-Capture -Sequence $sequence -Lines (New-Lines -Sequence $sequence -Pss $pss) -Prior $prior
                $expected = if ($sequence -eq 5 -and $delta -eq 51201) { 'PERFORMANCE_FAIL' } else { 'pass' }
                Assert-Contract ($result.verdict -ceq $expected) 'Incorrect median verdict.'
                $prior += Copy-Record $result
            }
            Assert-Contract ($result.family_median_pss_delta_kib[$checkpoints[$index]] -eq $delta) 'Wrong nearest middle value.'
        }
    }
}
foreach ($maximum in @(99MB, 201MB)) {
    Invoke-Case "growth floor and inclusive boundary for heap $maximum" {
        $growthLimit = [long][math]::Max(1MB, [long][decimal]::Floor([decimal]$maximum / 100))
        $java = @(10MB, 20MB, 20MB, 20MB, (20MB + $growthLimit))
        $result = Invoke-Capture -Lines (New-Lines -Maximum $maximum -Java $java)
        Assert-Contract ($result.verdict -ceq 'pass' -and $result.limits.post_cycle_growth_limit_bytes -eq $growthLimit) 'Growth boundary failed.'
        $java[4]++
        Assert-Contract ((Invoke-Capture -Lines (New-Lines -Maximum $maximum -Java $java)).verdict -ceq 'PERFORMANCE_FAIL') 'Growth excess accepted.'
    }
}
Invoke-Case 'negative deltas and odd limits use exact integer floors' {
    $result = Invoke-Capture -Lines (New-Lines -Maximum 104857601 -MemoryClass 101 -Pss @(30000, 20000, 20000, 20000, 20000))
    Assert-Contract ($result.verdict -ceq 'pass' -and $result.limits.java_limit_bytes -eq 52428800 -and
        $result.limits.pss_limit_kib -eq 92054) 'Fractional floor or negative delta changed.'
}
} else {
    if (-not (Test-Path -LiteralPath $PriorCaptureDirectory -PathType Container)) { throw 'Existing synthetic chain required.' }
    $script:chain = @(foreach ($number in 1..10) {
        $path = Join-Path $PriorCaptureDirectory ('capture-{0:d4}-result.json' -f $number)
        Get-Content -LiteralPath $path -Raw | ConvertFrom-Json -AsHashtable
    })
}

$validLines = New-Lines
$mutations = [ordered]@{
    'missing checkpoint' = { @($validLines[0..8]) + @($validLines[11..21]) }
    'duplicate checkpoint' = { @($validLines[0..10]) + @($validLines[9..10]) + @($validLines[11..21]) }
    'reordered checkpoints' = { @($validLines[0..8]) + @($validLines[11..12]) + @($validLines[9..10]) + @($validLines[13..21]) }
    'mismatched raw and final numbers' = { $validLines -replace 'empty_idle_java_bytes=10485760', 'empty_idle_java_bytes=10485761' }
    'checkpoint field missing' = { $validLines -replace ' shared_dirty_kib=0', '' }
    'checkpoint field extra' = { $validLines -replace ' shared_dirty_kib=0', ' shared_dirty_kib=0 extra=0' }
    'checkpoint field duplicate' = { $validLines -replace ' shared_dirty_kib=0', ' shared_dirty_kib=0 shared_dirty_kib=0' }
    'checkpoint case drift' = { $validLines -creplace 'java_committed_bytes=', 'JAVA_committed_bytes=' }
    'checkpoint slot drift' = { $validLines -replace 'index=2 checkpoint=long_preview_held', 'index=2 checkpoint=committed_idle' }
    'wrong workload facts' = { $validLines -replace 'preview_raw_position_count=4081', 'preview_raw_position_count=4080' }
    'wrong fixture bytes' = { $validLines -replace 'fixture_sha256=[0-9a-f]{64}', ('fixture_sha256=' + '0' * 64) }
    'wrong fixture URI' = { $validLines -replace 'document/i89-145-', 'document/i89-146-' }
    'provider equals app UID' = { $validLines -replace 'provider_uid=10002', 'provider_uid=10001' }
    'zero actual grantee UID' = { $validLines -replace 'grantee_uid=10001', 'grantee_uid=0' }
    'report schema drift' = { $validLines -replace 'schema=nene-pixel-p4-layer-editor-retention-v1', 'schema=wrong' }
    'unpublished final position' = { $validLines -replace 'published_position_verified=true', 'published_position_verified=false' }
    'missing identity' = { $validLines[9..21] }
    'missing summary' = { @($validLines[0..18]) + $validLines[21] }
    'missing completion' = { $validLines[0..20] }
    'duplicate completion' = { $validLines + 'INSTRUMENTATION_CODE: -1' }
    'malformed additional completion' = { $validLines + 'INSTRUMENTATION_CODE: broken' }
    'malformed status' = { @($validLines[0..8]) + 'INSTRUMENTATION_STATUS_CODE: broken' + @($validLines[9..21]) }
    'trailing status after completion' = { $validLines + 'INSTRUMENTATION_STATUS_CODE: 0' }
    'JUnit assertion failure' = { @($validLines[0..20]) + 'INSTRUMENTATION_STATUS_CODE: -2' + $validLines[21] }
    'crash' = { 'FATAL EXCEPTION' + $validLines }
    'ANR' = { 'ANR in app' + $validLines }
    'foreign final artifact identity' = { $validLines -replace 'test_apk_sha256=3{64}', ('test_apk_sha256=' + '9' * 64) }
}
foreach ($name in $mutations.Keys) {
    Invoke-Case $name { Assert-Rejected { Invoke-Capture -Lines (& $mutations[$name]) } }
}
foreach ($number in @('-1', '1.5', '1e3', '+1', '01', 'NaN', '9223372036854775808')) {
    Invoke-Case "raw scalar integer rejects $number" {
        Assert-Rejected { Invoke-Capture -Lines ($validLines -replace 'java_committed_bytes=104857600', "java_committed_bytes=$number") }
    }
}
foreach ($replacement in @('java_bytes=104857601', 'java_committed_bytes=104857601', 'pss_kib=0')) {
    Invoke-Case "impossible checkpoint $replacement" {
        $key = $replacement.Split('=')[0]
        Assert-Rejected { Invoke-Capture -Lines ($validLines -replace "\b$key=[0-9]+", $replacement) }
    }
}
foreach ($key in (New-Context).Keys) {
    Invoke-Case "identity $key must match and be scalar" {
        $context = New-Context; $context[$key] = 'wrong'
        Assert-Rejected { Invoke-Capture -Context $context }
        $context = New-Context; $context[$key] = @($context[$key], $context[$key])
        Assert-Rejected { Invoke-Capture -Context $context }
    }
}
Invoke-Case 'explicit null context and omitted phase context refuse phase capture' {
    Assert-Rejected { Test-P4MemoryCapture $validLines 'baseline-layer-editor-retention' 1 ('c' * 40) -PhaseContext $null }
    Assert-Rejected { Test-P4MemoryCapture $validLines 'baseline-layer-editor-retention' 1 ('c' * 40) }
}
$priorMutations = [ordered]@{
    'missing predecessor' = { $script:bad = @($script:bad[1..4]) }
    'extra predecessor' = { $script:bad += Copy-Record $script:bad[4] }
    'reordered predecessors' = { $script:bad = @($script:bad[1], $script:bad[0]) + @($script:bad[2..4]) }
    'foreign experiment' = { $script:bad[0].phase_context.experiment_id = 'foreign-experiment' }
    'foreign preservation' = { $script:bad[0].phase_context.preservation_sha256 = 'f' * 64 }
    'foreign installed APK' = { $script:bad[1].phase_context.app_apk_sha256 = 'f' * 64 }
    'prior failure' = { $script:bad[0].verdict = 'PERFORMANCE_FAIL' }
    'false numeric pass' = { $script:bad[0].checkpoint_observations[2].java_bytes = 51MB }
    'false median pass' = { foreach ($i in 0..2) { $script:bad[$i].checkpoint_observations[2].pss_kib = 61201 } }
    'missing prior checkpoint' = { $script:bad[0].checkpoint_observations = $script:bad[0].checkpoint_observations[0..3] }
    'fractional prior checkpoint' = { $script:bad[0].checkpoint_observations[1].java_bytes = 20971520.1 }
    'string prior checkpoint' = { $script:bad[0].checkpoint_observations[1].java_bytes = '20971520' }
    'boolean prior checkpoint' = { $script:bad[0].checkpoint_observations[1].java_bytes = $true }
    'array prior checkpoint' = { $script:bad[0].checkpoint_observations[1].java_bytes = @(20971520) }
    'changed role memory capacity' = { $script:bad[1].memory_class_mib = 101 }
    'reused prior process' = { $script:bad[1].process_id = $script:bad[0].process_id; $script:bad[1].process_start_elapsed_realtime_ms = $script:bad[0].process_start_elapsed_realtime_ms }
    'reused process across artifact roles' = { $script:bad[0].process_id = 1006; $script:bad[0].process_start_elapsed_realtime_ms = 2006 }
    'impossible prior Android PID' = { $script:bad[0].process_id = 2147483648 }
}
foreach ($name in $priorMutations.Keys) {
    Invoke-Case $name {
        $script:bad = @(Copy-Record @($script:chain[0..4]))
        & $priorMutations[$name]
        Assert-Rejected { Invoke-Capture -Sequence 6 -Prior $script:bad }
    }
}
Invoke-Case 'same PID with a distinct positive process start remains fresh' {
    $lines = (New-Lines -Sequence 6) -replace 'p4MemoryProcessId=1006', 'p4MemoryProcessId=1001'
    Assert-Contract ((Invoke-Capture -Sequence 6 -Lines $lines -Prior @($script:chain[0..4])).verdict -ceq 'pass') 'Fresh start pair rejected.'
}

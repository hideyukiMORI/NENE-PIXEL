[CmdletBinding()]
param([switch]$GrossBoundariesOnly, [switch]$BaselineBindingOnly, [switch]$StateNativeIntegersOnly, [switch]$BaselineGrossReferenceOnly)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-frame-analysis.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1')
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')

# Fresh synthetic evidence only. No device or unrelated device-plan test body is executed.
$evidenceRelative = 'evidence/145-layer-frame-analysis/' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N')
$evidenceRoot = Get-NenePixelLabPath $evidenceRelative
New-Item -ItemType Directory -Path $evidenceRoot | Out-Null
$script:assertions = 0
function Assert-LayerFrame {
    param([bool]$Condition, [string]$Name)
    if (-not $Condition) { throw "FAIL: $Name" }
    $script:assertions++
}
function Assert-LayerRefuses {
    param([scriptblock]$Action, [string]$Name)
    $refused = $false
    try { & $Action | Out-Null }
    catch { $refused = $true; "REFUSED $Name : $($_.Exception.Message)" | Add-Content (Join-Path $evidenceRoot 'refusals.log') }
    Assert-LayerFrame $refused $Name
}
foreach ($path in @($PSCommandPath, (Join-Path $PSScriptRoot 'measurements/p4-indexed-frame-analysis.ps1'))) {
    $tokens = $null; $errors = $null
    [Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors) | Out-Null
    Assert-LayerFrame ($errors.Count -eq 0) "AST $path"
}
$protocol = 'nene-pixel-p4-layer-phase-verification-v1'
$experimentId = 'offline-layer-frame-analysis'
$preflightHash = 'c' * 64
$build = '2' * 40
$apk = 'b' * 64
$candidateProduction = '3' * 40
$boundsText = '[688,615][1232,1159]'
$primaryRoot = Join-Path $evidenceRoot 'positive'
New-Item -ItemType Directory -Path $primaryRoot | Out-Null
$experiment = Get-P4LayerFrameExperimentContract -ExperimentId $experimentId -PreflightSha256 $preflightHash
$experiment | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $primaryRoot 'experiment.json') -Encoding utf8NoBOM
$experimentHash = Get-FileSha256 (Join-Path $primaryRoot 'experiment.json')

function New-LayerFrameFixture {
    param([System.Collections.IDictionary]$Contract, [string]$Root = $primaryRoot,
        [long]$InputNs = 16000000L, [long]$CommitOverrunNs = -1000000L, [string[]]$Families = @())
    if ($Families.Count -eq 0) { $Families = @($Contract.workload_order) }
    $slot = Join-Path $Root $Contract.frame_directory_name
    New-Item -ItemType Directory -Path $slot | Out-Null
    $production = if ($Contract.role -ceq 'baseline') { $Contract.baseline_production_commit } else { $candidateProduction }
    $identity = [ordered]@{ protocol_id = $protocol; preflight_sha256 = $preflightHash; group_id = $Contract.group_id
        slot_id = $Contract.slot_id; artifact_role = $Contract.artifact_role; experiment_id = $experimentId }
    $frames = [Collections.Generic.List[object]]::new(); $samples = [Collections.Generic.List[object]]::new()
    $ordinal = 0; $id = 0
    foreach ($workload in $Families) {
        $spec = @($Contract.workload_catalog | Where-Object { $_.workload -ceq $workload })[0]
        foreach ($index in 1..$Contract.samples) {
            $ordinal++; $base = [long]$ordinal * 1000000000L
            foreach ($position in 0..2) {
                $id++; $phase = if ($position -eq 2) { 'commit' } else { 'preview' }
                $offset = @(0L, 20000000L, 100000000L)[$position]
                $intended = $base + $offset; $start = $intended + 1000000L; $input = $intended + 2000000L
                $serviceNs = if ($position -eq 0) { 10000000L } elseif ($position -eq 1) { 2000000L } else { $InputNs }
                $completed = $input + $serviceNs
                $overrunNs = if ($position -eq 2) { $CommitOverrunNs } else { -1000000L }
                $record = [ordered]@{}
                foreach ($key in $identity.Keys) { $record[$key] = $identity[$key] }
                $record.variant = 'release-like'; $record.source_commit = $build; $record.production_commit = $production
                $record.workload = $workload; $record.operation = "$workload#$index"; $record.operation_ordinal = $ordinal; $record.sample_index = $index
                $record.phase = $phase; $record.event_count = $spec["${phase}_event_count"]; $record.row_index = if ($phase -ceq 'preview') { $position + 1 } else { 1 }
                $record.flags = 0; $record.frame_timeline_vsync_id = $id; $record.intended_vsync_nanos = $intended
                $record.frame_start_nanos = $start; $record.handle_input_start_nanos = $input; $record.frame_completed_nanos = $completed
                $record.frame_deadline_nanos = $completed - $overrunNs
                # Independent writer: no analyzer timing helper or percentile computation.
                $record.frame_duration_cpu_ms = (([decimal]$serviceNs + 1000000) / 1000000).ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
                $record.app_frame_total_ms = (([decimal]$serviceNs + 2000000) / 1000000).ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
                $record.input_start_to_completion_ms = ([decimal]$serviceNs / 1000000).ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
                $record.frame_overrun_ms = ([decimal]$overrunNs / 1000000).ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
                $frames.Add([pscustomobject]$record)
            }
            $record = [ordered]@{}
            foreach ($key in $identity.Keys) { $record[$key] = $identity[$key] }
            $record.variant = 'release-like'; $record.source_commit = $build; $record.production_commit = $production
            $record.workload = $workload; $record.operation = "$workload#$index"; $record.operation_ordinal = $ordinal; $record.sample_index = $index
            foreach ($key in @('motion_event_count', 'preview_event_count', 'commit_event_count', 'raw_position_count', 'effective_change_count')) { $record[$key] = $spec[$key] }
            foreach ($key in @('preview_frame_count', 'preview_raw_frame_count', 'preview_valid_frame_count')) { $record[$key] = 2 }
            foreach ($key in @('commit_frame_count', 'commit_raw_frame_count', 'commit_valid_frame_count')) { $record[$key] = 1 }
            foreach ($key in @('total_frames_rendered', 'raw_row_count', 'valid_row_count')) { $record[$key] = 3 }
            $record.janky_frames = 0; $record.deadline_missed_frames = 0; $record.committed_ui_verified = $true
            $record.preview_input_start_nanos = $base + 2000000L; $record.commit_input_start_nanos = $base + 102000000L
            $record.committed_result_completion_nanos = $base + 102000000L + $InputNs
            $record.input_to_committed_result_ms = ([decimal]$InputNs / 1000000).ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
            $record.down_to_committed_result_ms = (([decimal]$InputNs + 100000000) / 1000000).ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
            foreach ($key in @('first_preview_frame_timeline_vsync_id', 'first_preview_row_index', 'first_preview_handle_input_start_nanos',
                    'first_preview_frame_completed_nanos', 'first_preview_deadline_nanos', 'first_preview_service_ms', 'first_preview_overrun_ms')) { $record[$key] = '' }
            if ($workload -ceq 'canvas256_layers16_tap') {
                $record.first_preview_frame_timeline_vsync_id = $id - 2; $record.first_preview_row_index = 1
                $record.first_preview_handle_input_start_nanos = $base + 2000000L; $record.first_preview_frame_completed_nanos = $base + 12000000L
                $record.first_preview_deadline_nanos = $base + 13000000L; $record.first_preview_service_ms = '10.000000'; $record.first_preview_overrun_ms = '-1.000000'
            }
            $samples.Add([pscustomobject]$record)
        }
    }
    $frames | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath (Join-Path $slot 'frames.csv')
    $samples | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath (Join-Path $slot 'samples.csv')
    $complete = $Families.Count -eq $Contract.workload_order.Count
    $gross = $CommitOverrunNs -gt 33340000L -or $InputNs -gt 100000000L
    $status = if ($Contract.runner -ceq 'diagnostic' -and $gross) { 'gross-regression' } else { 'inconclusive' }
    $metadata = [ordered]@{}
    foreach ($key in $identity.Keys) { $metadata[$key] = $identity[$key] }
    $metadata.schema = $Contract.frame_schema; $metadata.experiment_schema = $Contract.experiment_schema
    $metadata.experiment_sha256 = $experimentHash; $metadata.variant = 'release-like'; $metadata.compile_mode = 'speed-profile'
    $metadata.run_kind = $Contract.runner; $metadata.candidate_role = $Contract.role; $metadata.comparison_sequence_index = $Contract.sequence_index
    $metadata.comparison_order = $Contract.comparison_order -join '|'; $metadata.workload_order = $Contract.workload_order -join '|'
    $metadata.source_commit = $build; $metadata.production_commit = $production; $metadata.apk_sha256 = $apk; $metadata.apk_embedded_source_commit = $build
    $metadata.geometry_id = $Contract.geometry_id; $metadata.device_evidence_class = 'physical_device'; $metadata.warmups_per_workload = 5
    $metadata.samples_per_workload = $Contract.samples; $metadata.fatal_anr_matches = 0; $metadata.percentile_method = 'nearest-rank; synthetic'
    $metadata.status = $status; $metadata.acceptance_lane = $Contract.runner; $metadata.complete_run = if ($complete) { 'true' } else { 'false' }
    $metadata.raw_frame_rows = $frames.Count; $metadata.valid_frame_rows = $frames.Count; $metadata.aggregate_frames_rendered = $frames.Count
    $metadata.measured_operation_count = $samples.Count
    $counts = [ordered]@{}
    foreach ($workload in $Contract.workload_order) {
        $measured = $workload -cin $Families; $counts[$workload] = if ($measured) { $Contract.samples } else { 0 }
        $metadata["measured_$workload"] = $counts[$workload]
        if ($workload -cne 'canvas256_repeated_diagonal_window_x2') { $metadata["${workload}_surface_bounds"] = $boundsText }
        if (-not $measured) { continue }
        $overrun = ([decimal][Math]::Max(-1000000L, $CommitOverrunNs) / 1000000).ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
        $latency = ([decimal]$InputNs / 1000000).ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
        $metadata["${workload}_threshold_status"] = 'measured'; $metadata["${workload}_frame_count"] = 3 * $Contract.samples
        foreach ($suffix in @('frame_overrun_p95_ms', 'frame_overrun_p99_ms', 'maximum_frame_overrun_ms')) { $metadata["${workload}_$suffix"] = $overrun }
        foreach ($suffix in @('input_to_committed_result_p95_ms', 'maximum_input_to_committed_result_ms')) { $metadata["${workload}_$suffix"] = $latency }
        $metadata["${workload}_diagnostic_gross_regression"] = if ($gross) { 'true' } else { 'false' }
    }
    @($metadata.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) | Set-Content (Join-Path $slot 'metadata.txt') -Encoding utf8NoBOM
    $state = [ordered]@{}
    foreach ($key in $identity.Keys) { $state[$key] = $identity[$key] }
    $state.schema = $Contract.experiment_schema; $state.experiment_sha256 = $experimentHash; $state.comparison_sequence_index = $Contract.sequence_index
    $state.attempt = 1; $state.status = 'completed'; $state.verdict = $status; $state.complete_run = $complete
    $state.workload_order = $Contract.workload_order; $state.measured_workload_counts = $counts; $state.measured_operation_count = $samples.Count
    $state | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $slot 'run-state.json') -Encoding utf8NoBOM
    return $slot
}

function Get-LayerFixtureArguments {
    param([System.Collections.IDictionary]$Contract, [string]$Slot)
    $production = if ($Contract.role -ceq 'baseline') { $Contract.baseline_production_commit } else { $candidateProduction }
    $bounds = [ordered]@{}
    foreach ($workload in $Contract.workload_order) { $bounds[$workload] = $boundsText }
    $context = [ordered]@{ protocol_id = $protocol; slot_id = $Contract.slot_id; preflight_sha256 = $preflightHash
        production_commit = $production; baseline_reference = $null }
    $args = @{ SlotDirectory = $Slot; Role = $Contract.role; Runner = $Contract.runner; SequenceIndex = $Contract.sequence_index
        BuildCommit = $build; ExpectedApkSha256 = $apk; ExpectedBounds = $bounds; ExperimentId = $experimentId; PhaseContext = $context }
    if ($Contract.role -ceq 'candidate' -and $Contract.runner -ceq 'decision') {
        $baseline = Get-P4FrameExecutionContract -ProtocolId $protocol -SlotId $Contract.baseline_slot_id
        $baselineSlot = Join-Path (Join-Path (Split-Path -Parent $Slot) 'outer-slots') $baseline.slot_id
        $path = Join-Path $baselineSlot 'analysis.json'
        $args.BaselineAnalysisPath = $path
        $context.baseline_reference = [ordered]@{ build_commit = $build; production_commit = $Contract.baseline_production_commit; apk_sha256 = $apk
            analysis_sha256 = Get-FileSha256 $path; capture_seal_sha256 = Get-FileSha256 (Join-Path $baselineSlot 'capture-seal.json') }
    }
    return $args
}
function Save-LayerBaseline {
    param([System.Collections.IDictionary]$Analysis, [string]$Slot)
    $outer = Join-Path (Join-Path (Split-Path -Parent $Slot) 'outer-slots') $Analysis.slot_id
    New-Item -ItemType Directory -Path $outer -Force | Out-Null
    $files = @(Get-ChildItem -LiteralPath $Slot -File | ForEach-Object {
        [ordered]@{ relative_path = "external/frame-slot/$($_.Name)"; byte_count = $_.Length; sha256 = Get-FileSha256 $_.FullName }
    })
    [ordered]@{ schema = 'nene-pixel-p4-capture-seal-v1'; slot_id = $Analysis.slot_id
        external_directories = @([ordered]@{ name = 'frame-slot'; path = [IO.Path]::GetFullPath($Slot) }); files = $files } |
        ConvertTo-Json -Depth 8 | Set-Content (Join-Path $outer 'capture-seal.json') -Encoding utf8NoBOM
    $Analysis.capture_seal_sha256 = Get-FileSha256 (Join-Path $outer 'capture-seal.json')
    $Analysis.slot_directory = [IO.Path]::GetFullPath($Slot)
    $Analysis | ConvertTo-Json -Depth 20 | Set-Content (Join-Path $outer 'analysis.json') -Encoding utf8NoBOM
}
$contracts = @(Get-P4FrameSlotCatalog -ProtocolId $protocol | ForEach-Object { Get-P4FrameExecutionContract -ProtocolId $protocol -SlotId $_.id })
if ($BaselineGrossReferenceOnly -or -not ($GrossBoundariesOnly -or $BaselineBindingOnly -or $StateNativeIntegersOnly)) {
$grossRoot = Join-Path $evidenceRoot 'decision-baseline-gross-reference'; New-Item -ItemType Directory $grossRoot | Out-Null
Copy-Item (Join-Path $primaryRoot 'experiment.json') $grossRoot
$grossSlot = New-LayerFrameFixture -Contract $contracts[2] -Root $grossRoot
$path = Join-Path $grossSlot 'frames.csv'; $rows = @(Import-Csv $path)
# One true outlier among150 tap frames leaves nearest-rank p95/p99=-1ms; UP p95 stays16ms.
$rows[2].frame_completed_nanos = '1152000000'; $rows[2].frame_deadline_nanos = '1112000000'
$rows[2].frame_duration_cpu_ms = '51.000000'; $rows[2].app_frame_total_ms = '52.000000'
$rows[2].input_start_to_completion_ms = '50.000000'; $rows[2].frame_overrun_ms = '40.000000'
$rows | Export-Csv -NoTypeInformation -Encoding utf8 $path
$path = Join-Path $grossSlot 'samples.csv'; $rows = @(Import-Csv $path)
$rows[0].committed_result_completion_nanos = '1152000000'
$rows[0].input_to_committed_result_ms = '50.000000'; $rows[0].down_to_committed_result_ms = '150.000000'
$rows | Export-Csv -NoTypeInformation -Encoding utf8 $path
$replacements = @{ canvas256_layers16_tap_maximum_frame_overrun_ms = '40.000000'
    canvas256_layers16_tap_maximum_input_to_committed_result_ms = '50.000000'; canvas256_layers16_tap_diagnostic_gross_regression = 'true' }
$path = Join-Path $grossSlot 'metadata.txt'
@(Get-Content $path | ForEach-Object {
    $key = $_.Split('=', 2)[0]
    if ($replacements.ContainsKey($key)) { "$key=$($replacements[$key])" } else { $_ }
}) | Set-Content $path -Encoding utf8NoBOM
$args = Get-LayerFixtureArguments $contracts[2] $grossSlot
$grossResult = Test-P4FrameCapture @args
Assert-LayerFrame ($grossResult.verdict -ceq 'baseline-recorded' -and $grossResult.families.canvas256_layers16_tap.gross_regression) 'baseline guard permits true outlier gross with valid p95'
Assert-LayerFrame ($grossResult.gross_regression -eq $true -and $grossResult.gross_regression_basis.maximum_frame_overrun_ms -ceq '40.000000' -and
    $grossResult.gross_regression_basis.maximum_input_to_committed_result_ms -ceq '50.000000' -and
    (@($grossResult.gross_regression_basis.families) -join '|') -ceq 'canvas256_layers16_tap') 'decision baseline records slot gross_regression with its basis'
Save-LayerBaseline $grossResult $grossSlot
$slot = New-LayerFrameFixture -Contract $contracts[3] -Root $grossRoot
$args = Get-LayerFixtureArguments $contracts[3] $slot
Assert-LayerFrame ((Test-P4FrameCapture @args).verdict -ceq 'pass') 'candidate reference accepts valid baseline gross true'
}
if ($BaselineGrossReferenceOnly) { "PASS: $script:assertions assertions; baseline gross reference only; evidence $evidenceRelative"; return }
if ($StateNativeIntegersOnly -or -not ($GrossBoundariesOnly -or $BaselineBindingOnly -or $BaselineGrossReferenceOnly)) {
foreach ($field in @('comparison_sequence_index', 'attempt', 'measured_operation_count',
        'canvas256_layers16_tap', 'canvas256_layers16_repeated_diagonal', 'positive')) {
    $root = Join-Path $evidenceRoot ('native-state-' + $field); New-Item -ItemType Directory $root | Out-Null
    Copy-Item (Join-Path $primaryRoot 'experiment.json') $root
    $slot = New-LayerFrameFixture -Contract $contracts[2] -Root $root
    $args = Get-LayerFixtureArguments $contracts[2] $slot
    if ($field -ceq 'positive') { Assert-LayerFrame ((Test-P4FrameCapture @args).verdict -ceq 'baseline-recorded') 'native JSON integers positive'; continue }
    $path = Join-Path $slot 'run-state.json'; $state = Get-Content -Raw $path | ConvertFrom-Json -AsHashtable
    if ($field.StartsWith('canvas', [StringComparison]::Ordinal)) { $state.measured_workload_counts[$field] = [string]$state.measured_workload_counts[$field] }
    else { $state[$field] = [string]$state[$field] }
    $state | ConvertTo-Json -Depth 10 | Set-Content $path -Encoding utf8NoBOM
    Assert-LayerRefuses { Test-P4FrameCapture @args } "native JSON integer rejects numeric string $field"
}
}
if ($StateNativeIntegersOnly) { "PASS: $script:assertions assertions; state native integers only; evidence $evidenceRelative"; return }
if (-not $BaselineBindingOnly) {
# New direct concern uses this narrow selector; the already passing 295-case population is reusable.
# R2: the decision slot itself owns the gross boundary (> 33.34 ms overrun or > 100 ms UP-to-committed).
# The verdict is unchanged (input p95 gate); only the slot gross_regression record moves.
foreach ($case in @(
        @{ name = 'decision-gross-overrun-inclusive'; input = 32000000L; overrun = 33340000L; verdict = 'baseline-recorded'; gross = $false; overrunText = '33.340000'; inputText = '32.000000' },
        @{ name = 'decision-gross-overrun-one-nanosecond'; input = 32000000L; overrun = 33340001L; verdict = 'baseline-recorded'; gross = $true; overrunText = '33.340001'; inputText = '32.000000' },
        @{ name = 'decision-gross-input-inclusive'; input = 100000000L; overrun = -1000000L; verdict = 'baseline-invalid'; gross = $false; overrunText = '-1.000000'; inputText = '100.000000' },
        @{ name = 'decision-gross-input-one-nanosecond'; input = 100000001L; overrun = -1000000L; verdict = 'baseline-invalid'; gross = $true; overrunText = '-1.000000'; inputText = '100.000001' })) {
    $root = Join-Path $evidenceRoot $case.name; New-Item -ItemType Directory $root | Out-Null
    Copy-Item (Join-Path $primaryRoot 'experiment.json') $root
    $slot = New-LayerFrameFixture -Contract $contracts[4] -Root $root -InputNs $case.input -CommitOverrunNs $case.overrun
    $args = Get-LayerFixtureArguments $contracts[4] $slot
    $result = Test-P4FrameCapture @args
    $basis = $result.gross_regression_basis
    Assert-LayerFrame ($result.verdict -ceq $case.verdict -and $result.gross_regression -eq $case.gross -and
        $basis.maximum_frame_overrun_ms -ceq $case.overrunText -and $basis.maximum_input_to_committed_result_ms -ceq $case.inputText -and
        $basis.gross_overrun_threshold_ms -ceq '33.340000' -and $basis.gross_input_threshold_ms -ceq '100.000000' -and
        @($basis.families).Count -eq $(if ($case.gross) { 1 } else { 0 })) $case.name
}
}
if ($GrossBoundariesOnly) { "PASS: $script:assertions assertions; decision gross boundaries only; evidence $evidenceRelative"; return }
# Direct isolated source import proves phase functions do not depend on the caller importing preflight.
$isolated = & {
    . (Join-Path $PSScriptRoot 'measurements/p4-indexed-frame-analysis.ps1')
    Get-P4FrameCaptureContract -Role baseline -Runner decision -SequenceIndex 5 -BuildCommit $build -ExpectedApkSha256 $apk `
        -ExperimentId $experimentId -PhaseContext ([ordered]@{ protocol_id = $protocol; slot_id = $contracts[4].slot_id
            preflight_sha256 = $preflightHash; production_commit = $contracts[4].baseline_production_commit; baseline_reference = $null })
}
Assert-LayerFrame ($isolated.slot_id -ceq $contracts[4].slot_id) 'direct import resolves canonical underlay contract'
Assert-LayerFrame ($contracts.Count -eq 6 -and @($contracts | Where-Object { $_.runner -cne 'decision' }).Count -eq 0) 'phase frame catalog is six decision slots'
foreach ($contract in $contracts) {
    $slot = New-LayerFrameFixture $contract
    $args = Get-LayerFixtureArguments $contract $slot
    $result = Test-P4FrameCapture @args
    $expectedVerdict = if ($contract.role -ceq 'baseline') { 'baseline-recorded' } else { 'pass' }
    Assert-LayerFrame ($result.verdict -ceq $expectedVerdict) "6 slot verdict $($contract.slot_id)"
    Assert-LayerFrame ($result.gross_regression -eq $false -and $result.gross_regression_basis.maximum_frame_overrun_ms -ceq '-1.000000' -and
        $result.gross_regression_basis.maximum_input_to_committed_result_ms -ceq '16.000000' -and @($result.gross_regression_basis.families).Count -eq 0) "6 slot gross record $($contract.slot_id)"
    foreach ($key in @('protocol_id', 'group_id', 'slot_id', 'artifact_role')) { Assert-LayerFrame ($result[$key] -ceq $contract[$key]) "$key $($contract.slot_id)" }
    Assert-LayerFrame ($result.experiment_sha256 -ceq $experimentHash -and $result.preflight_sha256 -ceq $preflightHash) "experiment binding $($contract.slot_id)"
    foreach ($workload in $contract.workload_order) {
        $family = $result.families[$workload]
        Assert-LayerFrame ($family.frame_count -eq 3 * $contract.samples -and $family.operation_count -eq $contract.samples) "retained full population $($contract.slot_id) $workload"
        Assert-LayerFrame ($family.input_to_committed_result_p95_ms -ceq '16.000000' -and $family.frame_overrun_p95_ms -ceq '-1.000000') "literal metrics $($contract.slot_id) $workload"
        if ($workload -ceq 'canvas256_layers16_tap') {
            Assert-LayerFrame ($family.first_preview_operation_count -eq $contract.samples -and $family.first_preview_service_p95_ms -ceq '10.000000' -and
                $family.first_preview_overrun_p95_ms -ceq '-1.000000' -and $family.first_preview_overrun_p99_ms -ceq '-1.000000') 'first row is not fastest; full50/10 populations'
        } else { Assert-LayerFrame (-not $family.Contains('first_preview_operation_count')) "no association result $workload" }
    }
    if ($contract.role -ceq 'baseline' -and $contract.runner -ceq 'decision') { Save-LayerBaseline $result $slot }
}
# Fresh runspace has no caller-sourced functions; Test-P4FrameCapture must import its own resolver.
$directArguments = Get-LayerFixtureArguments $contracts[4] (Join-Path $primaryRoot $contracts[4].frame_directory_name)
$isolatedShell = [PowerShell]::Create()
try {
    $isolatedShell.AddScript('param($Source, $Arguments) . $Source; Test-P4FrameCapture @Arguments').
        AddArgument((Join-Path $PSScriptRoot 'measurements/p4-indexed-frame-analysis.ps1')).AddArgument($directArguments) | Out-Null
    $directResult = @($isolatedShell.Invoke())
    Assert-LayerFrame (-not $isolatedShell.HadErrors -and $directResult.Count -eq 1 -and $directResult[0].verdict -ceq 'baseline-recorded') 'isolated direct analyzer phase import'
} finally { $isolatedShell.Dispose() }

function Copy-LayerCase {
    param([string]$Name, [int]$ContractIndex = 3)
    $root = Join-Path $evidenceRoot $Name
    New-Item -ItemType Directory -Path $root | Out-Null
    Copy-Item -LiteralPath (Join-Path $primaryRoot 'experiment.json') -Destination $root
    $contract = $contracts[$ContractIndex]
    Copy-Item -LiteralPath (Join-Path $primaryRoot $contract.frame_directory_name) -Destination $root -Recurse
    if ($null -ne $contract.baseline_slot_id) {
        $baseline = Get-P4FrameExecutionContract -ProtocolId $protocol -SlotId $contract.baseline_slot_id
        $copiedRaw = Join-Path $root $baseline.frame_directory_name
        Copy-Item -LiteralPath (Join-Path $primaryRoot $baseline.frame_directory_name) -Destination $root -Recurse
        $baselineAnalysisPath = Join-Path (Join-Path (Join-Path $primaryRoot 'outer-slots') $baseline.slot_id) 'analysis.json'
        $baselineAnalysis = Get-Content -Raw $baselineAnalysisPath | ConvertFrom-Json -AsHashtable
        Save-LayerBaseline $baselineAnalysis $copiedRaw
    }
    return Get-LayerFixtureArguments $contract (Join-Path $root $contract.frame_directory_name)
}
function Set-LayerMetadata {
    param([string]$Slot, [string]$Key, [string]$Value)
    $path = Join-Path $Slot 'metadata.txt'
    @(Get-Content $path | ForEach-Object { if ($_.StartsWith("$Key=", [StringComparison]::Ordinal)) { "$Key=$Value" } else { $_ } }) |
        Set-Content $path -Encoding utf8NoBOM
}
function Set-LayerCsvField {
    param([string]$Slot, [string]$File, [string]$Field, [string]$Value, [int]$Index = 0)
    $path = Join-Path $Slot $File; $rows = @(Import-Csv $path)
    $rows[$Index] | Add-Member -NotePropertyName $Field -NotePropertyValue $Value -Force
    $rows | Export-Csv -NoTypeInformation -Encoding utf8 $path
}
function Set-LayerBaselineField {
    param([hashtable]$Arguments, [string]$Field, $Value)
    $path = $Arguments.BaselineAnalysisPath
    $analysis = Get-Content -Raw $path | ConvertFrom-Json -AsHashtable
    $analysis[$Field] = $Value
    $analysis | ConvertTo-Json -Depth 20 | Set-Content $path -Encoding utf8NoBOM
    $Arguments.PhaseContext.baseline_reference.analysis_sha256 = Get-FileSha256 $path
}

# Decimal boundaries are encoded with integer nanoseconds, with independent literal expectations.
foreach ($case in @(
        @{ name = 'candidate-16670000'; index = 3; ns = 16670000L; verdict = 'pass' },
        @{ name = 'candidate-16670001'; index = 3; ns = 16670001L; verdict = 'PERFORMANCE_FAIL' },
        @{ name = 'baseline-33330000'; index = 2; ns = 33330000L; verdict = 'baseline-recorded' },
        @{ name = 'baseline-33330001'; index = 2; ns = 33330001L; verdict = 'baseline-invalid' })) {
    $root = Join-Path $evidenceRoot $case.name; New-Item -ItemType Directory $root | Out-Null
    Copy-Item (Join-Path $primaryRoot 'experiment.json') $root
    if ($case.index -eq 3) {
        Copy-Item (Join-Path $primaryRoot $contracts[2].frame_directory_name) $root -Recurse
        $record = Get-Content -Raw (Join-Path (Join-Path (Join-Path $primaryRoot 'outer-slots') $contracts[2].slot_id) 'analysis.json') | ConvertFrom-Json -AsHashtable
        Save-LayerBaseline $record (Join-Path $root $contracts[2].frame_directory_name)
    }
    $slot = New-LayerFrameFixture -Contract $contracts[$case.index] -Root $root -InputNs $case.ns
    $args = Get-LayerFixtureArguments $contracts[$case.index] $slot
    Assert-LayerFrame ((Test-P4FrameCapture @args).verdict -ceq $case.verdict) $case.name
}
foreach ($case in @(@{ name = 'relative-p95-inclusive'; ns = 0L; input = 16000000L; verdict = 'pass'; gross = $false },
        @{ name = 'relative-p95-plus-nanosecond'; ns = 1L; input = 16000000L; verdict = 'PERFORMANCE_FAIL'; gross = $false },
        @{ name = 'decision-candidate-gross'; ns = 33340001L; input = 32000000L; verdict = 'PERFORMANCE_FAIL'; gross = $true })) {
    $root = Join-Path $evidenceRoot $case.name; New-Item -ItemType Directory $root | Out-Null
    Copy-Item (Join-Path $primaryRoot 'experiment.json') $root
    Copy-Item (Join-Path $primaryRoot $contracts[2].frame_directory_name) $root -Recurse
    $record = Get-Content -Raw (Join-Path (Join-Path (Join-Path $primaryRoot 'outer-slots') $contracts[2].slot_id) 'analysis.json') | ConvertFrom-Json -AsHashtable
    Save-LayerBaseline $record (Join-Path $root $contracts[2].frame_directory_name)
    $slot = New-LayerFrameFixture -Contract $contracts[3] -Root $root -CommitOverrunNs $case.ns -InputNs $case.input
    $args = Get-LayerFixtureArguments $contracts[3] $slot
    $result = Test-P4FrameCapture @args
    Assert-LayerFrame ($result.verdict -ceq $case.verdict -and $result.gross_regression -eq $case.gross) $case.name
}

foreach ($field in @('schema', 'experiment_schema', 'verdict_rule', 'protocol_id', 'preflight_sha256', 'group_id', 'slot_id', 'artifact_role',
        'experiment_id', 'experiment_sha256', 'build_commit', 'production_commit', 'apk_sha256', 'capture_seal_sha256')) {
    $args = Copy-LayerCase "foreign-baseline-$field"; Set-LayerBaselineField $args $field 'foreign'
    Assert-LayerRefuses { Test-P4FrameCapture @args } "foreign baseline $field with refreshed file hash"
}
foreach ($field in @('complete_run', 'samples_per_workload', 'warmups_per_workload', 'fatal_anr_matches', 'measured_operation_count', 'raw_frame_rows')) {
    $args = Copy-LayerCase "invalid-baseline-$field"
    $value = if ($field -ceq 'complete_run') { 'true' } else { 1.5 }
    Set-LayerBaselineField $args $field $value
    Assert-LayerRefuses { Test-P4FrameCapture @args } "strict baseline $field"
}
foreach ($case in @('foreign-family', 'operation-count', 'fractional-frame-count', 'slow-input', 'first-count')) {
    $args = Copy-LayerCase ('baseline-family-' + $case); $path = $args.BaselineAnalysisPath
    $record = Get-Content -Raw $path | ConvertFrom-Json -AsHashtable
    switch ($case) {
        'foreign-family' { $record.families.canvas16_tap = $record.families.canvas256_layers16_tap; $record.families.Remove('canvas256_layers16_tap') | Out-Null }
        'operation-count' { $record.families.canvas256_layers16_tap.operation_count = 49 }
        'fractional-frame-count' { $record.families.canvas256_layers16_tap.frame_count = 150.5 }
        'slow-input' { $record.families.canvas256_layers16_tap.input_to_committed_result_p95_ms = '33.330001' }
        'first-count' { $record.families.canvas256_layers16_tap.first_preview_operation_count = 49 }
    }
    $record | ConvertTo-Json -Depth 20 | Set-Content $path -Encoding utf8NoBOM
    $args.PhaseContext.baseline_reference.analysis_sha256 = Get-FileSha256 $path
    Assert-LayerRefuses { Test-P4FrameCapture @args } "baseline family $case"
}
$args = Copy-LayerCase 'baseline-analysis-byte-hash'; Add-Content $args.BaselineAnalysisPath ' '
Assert-LayerRefuses { Test-P4FrameCapture @args } 'baseline analysis actual byte hash'
$args = Copy-LayerCase 'baseline-seal-byte-hash'; Add-Content (Join-Path (Split-Path -Parent $args.BaselineAnalysisPath) 'capture-seal.json') ' '
Assert-LayerRefuses { Test-P4FrameCapture @args } 'baseline seal actual byte hash'
$args = Copy-LayerCase 'baseline-sealed-file'; Add-Content (Join-Path (Join-Path (Split-Path -Parent $args.SlotDirectory) $contracts[2].frame_directory_name) 'metadata.txt') 'tampered=true'
Assert-LayerRefuses { Test-P4FrameCapture @args } 'baseline seal verifies listed file bytes'
$args = Copy-LayerCase 'baseline-foreign-path'; $args.BaselineAnalysisPath = Join-Path (Join-Path (Join-Path $primaryRoot 'outer-slots') $contracts[0].slot_id) 'analysis.json'
$args.PhaseContext.baseline_reference.analysis_sha256 = Get-FileSha256 $args.BaselineAnalysisPath
Assert-LayerRefuses { Test-P4FrameCapture @args } 'foreign single-group path with identical metrics'
$args = Copy-LayerCase 'baseline-analysis-in-raw'; $rawPath = Join-Path (Join-Path (Split-Path -Parent $args.SlotDirectory) $contracts[2].frame_directory_name) 'analysis.json'
Copy-Item -LiteralPath $args.BaselineAnalysisPath -Destination $rawPath
$args.BaselineAnalysisPath = $rawPath
Assert-LayerRefuses { Test-P4FrameCapture @args } 'analysis cannot substitute raw frame directory for canonical outer slot'

# 150 retained frames per family: exactly two elevated commit rows affect p99 rank149, not p95 rank143.
foreach ($case in @(@{ name = 'relative-p99-inclusive'; overrun = '1.000000'; ns = 1000000L; verdict = 'pass' },
        @{ name = 'relative-p99-plus-nanosecond'; overrun = '1.000001'; ns = 1000001L; verdict = 'PERFORMANCE_FAIL' })) {
    $args = Copy-LayerCase $case.name; $path = Join-Path $args.SlotDirectory 'frames.csv'; $rows = @(Import-Csv $path)
    foreach ($index in @(2, 5, 152, 155)) {
        $rows[$index].frame_overrun_ms = $case.overrun
        $rows[$index].frame_deadline_nanos = ([long]$rows[$index].frame_completed_nanos - $case.ns).ToString()
    }
    $rows | Export-Csv -NoTypeInformation -Encoding utf8 $path
    foreach ($workload in $contracts[3].workload_order) {
        Set-LayerMetadata $args.SlotDirectory "${workload}_frame_overrun_p99_ms" $case.overrun
        Set-LayerMetadata $args.SlotDirectory "${workload}_maximum_frame_overrun_ms" $case.overrun
    }
    $result = Test-P4FrameCapture @args
    Assert-LayerFrame ($result.verdict -ceq $case.verdict -and $result.families.canvas256_layers16_tap.p95_margin_ms -ceq '0.000000') $case.name
}
foreach ($kind in @('state', 'experiment', 'baseline')) {
    if ($BaselineBindingOnly -and $kind -cne 'baseline') { continue }
    $args = Copy-LayerCase ('root-array-' + $kind)
    $path = switch ($kind) {
        'state' { Join-Path $args.SlotDirectory 'run-state.json' }
        'experiment' { Join-Path (Split-Path -Parent $args.SlotDirectory) 'experiment.json' }
        'baseline' { $args.BaselineAnalysisPath }
    }
    $json = Get-Content -Raw $path
    ('[' + $json + ']') | Set-Content $path -Encoding utf8NoBOM
    if ($kind -ceq 'baseline') { $args.PhaseContext.baseline_reference.analysis_sha256 = Get-FileSha256 $path }
    Assert-LayerRefuses { Test-P4FrameCapture @args } "one-element root array $kind"
}

if ($BaselineBindingOnly) { "PASS: $script:assertions assertions; baseline binding only; evidence $evidenceRelative"; return }

foreach ($field in @('protocol_id', 'preflight_sha256', 'group_id', 'slot_id', 'artifact_role', 'experiment_id', 'production_commit', 'source_commit')) {
    foreach ($file in @('frames.csv', 'samples.csv')) {
        $args = Copy-LayerCase ('row-' + $file + '-' + $field); Set-LayerCsvField $args.SlotDirectory $file $field 'foreign'
        Assert-LayerRefuses { Test-P4FrameCapture @args } "$file foreign $field"
    }
    $args = Copy-LayerCase ('metadata-' + $field); Set-LayerMetadata $args.SlotDirectory $field 'foreign'
    Assert-LayerRefuses { Test-P4FrameCapture @args } "metadata foreign $field"
}
foreach ($case in @(
        @{ field = 'row_index'; value = '1.5' }, @{ field = 'sample_index'; value = '1e0' }, @{ field = 'operation_ordinal'; value = '+1' },
        @{ field = 'frame_timeline_vsync_id'; value = '9223372036854775808' }, @{ field = 'flags'; value = '0.1' },
        @{ field = 'intended_vsync_nanos'; value = '1000000000.0' }, @{ field = 'frame_start_nanos'; value = 'NaN' },
        @{ field = 'handle_input_start_nanos'; value = '0' }, @{ field = 'frame_completed_nanos'; value = '1' },
        @{ field = 'frame_deadline_nanos'; value = '1' }, @{ field = 'event_count'; value = '17' },
        @{ field = 'frame_duration_cpu_ms'; value = '12.000000' }, @{ field = 'app_frame_total_ms'; value = '11.000000' },
        @{ field = 'input_start_to_completion_ms'; value = '2.000000' }, @{ field = 'frame_overrun_ms'; value = 'Infinity' })) {
    $args = Copy-LayerCase ('raw-' + $case.field); Set-LayerCsvField $args.SlotDirectory 'frames.csv' $case.field $case.value
    Assert-LayerRefuses { Test-P4FrameCapture @args } "strict raw $($case.field)"
}
foreach ($field in @('motion_event_count', 'preview_event_count', 'commit_event_count', 'raw_position_count', 'effective_change_count',
        'preview_frame_count', 'preview_raw_frame_count', 'preview_valid_frame_count', 'commit_frame_count', 'commit_raw_frame_count', 'commit_valid_frame_count',
        'total_frames_rendered', 'raw_row_count', 'valid_row_count', 'operation_ordinal', 'sample_index', 'janky_frames', 'deadline_missed_frames',
        'preview_input_start_nanos', 'commit_input_start_nanos', 'committed_result_completion_nanos', 'input_to_committed_result_ms', 'down_to_committed_result_ms')) {
    $args = Copy-LayerCase ('sample-' + $field); Set-LayerCsvField $args.SlotDirectory 'samples.csv' $field '0.5'
    Assert-LayerRefuses { Test-P4FrameCapture @args } "strict sample $field"
}
foreach ($field in @('first_preview_frame_timeline_vsync_id', 'first_preview_row_index', 'first_preview_handle_input_start_nanos',
        'first_preview_frame_completed_nanos', 'first_preview_deadline_nanos', 'first_preview_service_ms', 'first_preview_overrun_ms')) {
    $args = Copy-LayerCase ('association-' + $field); Set-LayerCsvField $args.SlotDirectory 'samples.csv' $field '1'
    Assert-LayerRefuses { Test-P4FrameCapture @args } "first-preview mismatch $field"
}
$args = Copy-LayerCase 'missing-first-preview'; Set-LayerCsvField $args.SlotDirectory 'samples.csv' 'first_preview_service_ms' ''
Assert-LayerRefuses { Test-P4FrameCapture @args } 'missing tap association field'
$args = Copy-LayerCase 'unknown-first-preview'; Set-LayerCsvField $args.SlotDirectory 'samples.csv' 'first_preview_unknown' '1'
Assert-LayerRefuses { Test-P4FrameCapture @args } 'unknown tap association key'
$args = Copy-LayerCase 'unknown-empty-first-preview'; Set-LayerCsvField $args.SlotDirectory 'samples.csv' 'first_preview_unknown' '' 50
Assert-LayerRefuses { Test-P4FrameCapture @args } 'unknown empty association key'
$args = Copy-LayerCase 'orphan-first-preview'; Set-LayerCsvField $args.SlotDirectory 'samples.csv' 'first_preview_service_ms' '1.000000' 50
Assert-LayerRefuses { Test-P4FrameCapture @args } 'orphan diagonal association'
$args = Copy-LayerCase 'raw-reordered'; $path = Join-Path $args.SlotDirectory 'frames.csv'; $rows = @(Import-Csv $path)
$tmp = $rows[0]; $rows[0] = $rows[1]; $rows[1] = $tmp; $rows | Export-Csv -NoTypeInformation -Encoding utf8 $path
Assert-LayerRefuses { Test-P4FrameCapture @args } 'reordered preview rows never sorted'
$args = Copy-LayerCase 'raw-lost'; $path = Join-Path $args.SlotDirectory 'frames.csv'
@(Import-Csv $path | Select-Object -Skip 1) | Export-Csv -NoTypeInformation -Encoding utf8 $path
Assert-LayerRefuses { Test-P4FrameCapture @args } 'lost raw frame'
$args = Copy-LayerCase 'raw-phase-reordered'; $path = Join-Path $args.SlotDirectory 'frames.csv'; $rows = @(Import-Csv $path)
$tmp = $rows[0]; $rows[0] = $rows[2]; $rows[2] = $tmp; $rows | Export-Csv -NoTypeInformation -Encoding utf8 $path
Assert-LayerRefuses { Test-P4FrameCapture @args } 'commit before preview'
$args = Copy-LayerCase 'sample-reordered'; $path = Join-Path $args.SlotDirectory 'samples.csv'; $rows = @(Import-Csv $path)
$tmp = $rows[0]; $rows[0] = $rows[50]; $rows[50] = $tmp; $rows | Export-Csv -NoTypeInformation -Encoding utf8 $path
Assert-LayerRefuses { Test-P4FrameCapture @args } 'sample operations reordered across families'
$args = Copy-LayerCase 'diagonal-own-row'; Set-LayerCsvField $args.SlotDirectory 'frames.csv' 'frame_duration_cpu_ms' '0.000000' 152
Assert-LayerRefuses { Test-P4FrameCapture @args } 'own-row arithmetic validates diagonal commit'
$args = Copy-LayerCase 'metadata-counter-drift'; Add-Content (Join-Path $args.SlotDirectory 'metadata.txt') 'aggregate_janky_frames=1'
Assert-LayerRefuses { Test-P4FrameCapture @args } 'metadata gfxinfo counter aggregate drift'
$args = Copy-LayerCase 'metadata-counter-valid'; Add-Content (Join-Path $args.SlotDirectory 'metadata.txt') 'aggregate_janky_frames=0'
Assert-LayerFrame ((Test-P4FrameCapture @args).verdict -ceq 'pass') 'metadata gfxinfo counter aggregate agreement'
foreach ($field in @('comparison_sequence_index', 'attempt', 'measured_operation_count')) {
    $args = Copy-LayerCase ('state-' + $field); $path = Join-Path $args.SlotDirectory 'run-state.json'
    $state = Get-Content -Raw $path | ConvertFrom-Json -AsHashtable; $state[$field] = 1.5
    $state | ConvertTo-Json -Depth 10 | Set-Content $path -Encoding utf8NoBOM
    Assert-LayerRefuses { Test-P4FrameCapture @args } "strict state $field"
}
foreach ($field in @('schema', 'experiment_sha256', 'status', 'verdict')) {
    $args = Copy-LayerCase ('state-container-' + $field); $path = Join-Path $args.SlotDirectory 'run-state.json'
    $state = Get-Content -Raw $path | ConvertFrom-Json -AsHashtable; $state[$field] = @($state[$field])
    $state | ConvertTo-Json -Depth 10 | Set-Content $path -Encoding utf8NoBOM
    Assert-LayerRefuses { Test-P4FrameCapture @args } "state container $field"
}
foreach ($mutation in @('extra-key', 'fractional-count', 'missing-key')) {
    $args = Copy-LayerCase ('state-count-' + $mutation); $path = Join-Path $args.SlotDirectory 'run-state.json'
    $state = Get-Content -Raw $path | ConvertFrom-Json -AsHashtable
    if ($mutation -ceq 'extra-key') { $state.measured_workload_counts.extra = 0 }
    elseif ($mutation -ceq 'fractional-count') { $state.measured_workload_counts.canvas256_layers16_tap = 50.1 }
    else { $state.measured_workload_counts.Remove('canvas256_layers16_tap') | Out-Null }
    $state | ConvertTo-Json -Depth 10 | Set-Content $path -Encoding utf8NoBOM
    Assert-LayerRefuses { Test-P4FrameCapture @args } "state exact count keys $mutation"
}
foreach ($mutation in @('extra-key', 'slot-budget-fraction', 'numeric-string', 'catalog-group', 'order', 'hash')) {
    $args = Copy-LayerCase ('experiment-' + $mutation); $path = Join-Path (Split-Path -Parent $args.SlotDirectory) 'experiment.json'
    $record = Get-Content -Raw $path | ConvertFrom-Json -AsHashtable
    if ($mutation -ceq 'extra-key') { $record.extra = 1 }
    elseif ($mutation -ceq 'slot-budget-fraction') { $record.slot_budget = 12.1 }
    elseif ($mutation -ceq 'numeric-string') { $record.slot_budget = '12' }
    elseif ($mutation -ceq 'catalog-group') { $record.slot_catalog[4].group_id = 'single' }
    elseif ($mutation -ceq 'order') { $record.comparison_order[0] = 'layers16:decision:baseline' }
    else { Add-Content $path ' '; Assert-LayerRefuses { Test-P4FrameCapture @args } 'experiment actual bytes hash'; continue }
    $record | ConvertTo-Json -Depth 30 | Set-Content $path -Encoding utf8NoBOM
    Assert-LayerRefuses { Test-P4FrameCapture @args } "experiment exact projection $mutation"
}
foreach ($mutation in @('explicit-null', 'extra-key', 'array-hash', 'protocol', 'baseline-null', 'sequence', 'role', 'production', 'production-newline', 'preflight-newline')) {
    $args = Copy-LayerCase ('context-' + $mutation)
    switch ($mutation) {
        'explicit-null' { $args.PhaseContext = $null }
        'extra-key' { $args.PhaseContext.extra = 1 }
        'array-hash' { $args.PhaseContext.preflight_sha256 = @($preflightHash) }
        'protocol' { $args.PhaseContext.protocol_id = 'nene-pixel-p4-indexed-cutover-verification-v7' }
        'baseline-null' { $args.PhaseContext.baseline_reference = $null }
        'sequence' { $args.SequenceIndex = 2 }
        'role' { $args.Role = 'baseline' }
        'production' { $args.PhaseContext.production_commit = @($candidateProduction) }
        'production-newline' { $args.PhaseContext.production_commit = $candidateProduction + "`n" }
        'preflight-newline' { $args.PhaseContext.preflight_sha256 = $preflightHash + "`n" }
    }
    Assert-LayerRefuses { Test-P4FrameCapture @args } "context $mutation"
}
# Phase slots are decision-only: a family prefix is refused even with a gross regression (R2 records, never truncates).
foreach ($case in @(@{ name = 'decision-gross-prefix'; index = 2; ns = 33340001L; verdict = 'refuse' },
        @{ name = 'decision-no-gross-prefix'; index = 2; ns = -1000000L; verdict = 'refuse' })) {
    $root = Join-Path $evidenceRoot $case.name; New-Item -ItemType Directory $root | Out-Null
    Copy-Item (Join-Path $primaryRoot 'experiment.json') $root
    $inputNs = if ($case.ns -gt 0) { 50000000L } else { 16000000L }
    $slot = New-LayerFrameFixture -Contract $contracts[$case.index] -Root $root -Families @($contracts[$case.index].workload_order[0]) -CommitOverrunNs $case.ns -InputNs $inputNs
    $args = Get-LayerFixtureArguments $contracts[$case.index] $slot
    if ($case.verdict -ceq 'refuse') { Assert-LayerRefuses { Test-P4FrameCapture @args } $case.name }
    else { $result = Test-P4FrameCapture @args; Assert-LayerFrame ($result.verdict -ceq $case.verdict -and -not $result.complete_run) $case.name }
}

# Import only fixture functions from legacy AST. Its unrelated device-plan/preservation body never runs.
$legacyPath = Join-Path $PSScriptRoot 'validate-p4-frame-analysis.ps1'
$tokens = $null; $errors = $null; $legacyAst = [Management.Automation.Language.Parser]::ParseFile($legacyPath, [ref]$tokens, [ref]$errors)
foreach ($name in @('New-P4FrameSlotFixture', 'Invoke-P4FrameFixtureAnalysis', 'Save-P4FrameAnalysis')) {
    $function = $legacyAst.Find({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $name }, $false)
    . ([scriptblock]::Create($function.Extent.Text))
}
$temporaryRoot = Join-Path $evidenceRoot 'legacy'; New-Item -ItemType Directory $temporaryRoot | Out-Null
$baselineCommit = '1' * 40; $candidateCommit = '2' * 40; $baselineProduction = '2dd4e01e3bbe88967237cde4e28412d2962fd590'
$baselineApk = 'a' * 64; $candidateApk = 'b' * 64; $fixtureExperimentId = 'offline-frame-analysis'
$canvas16Bounds = $boundsText; $canvas256Bounds = $boundsText; $workloadOrder = @('canvas16_tap', 'canvas256_repeated_diagonal')
$windowFamily = 'canvas256_repeated_diagonal_window_x2'; $diagnosticOrder = @($workloadOrder + $windowFamily); $windowBounds = '[744,671][1176,1103]'
$comparisonOrder = 'decision:baseline|decision:candidate|diagnostic:baseline|diagnostic:candidate'; $experimentSchema = 'nene-pixel-p4-indexed-frame-experiment-v5'
$profileId = 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36'
$slot = New-P4FrameSlotFixture -Name 'baseline' -Role baseline -Runner decision -SequenceIndex 1 -Families $workloadOrder
$result = Invoke-P4FrameFixtureAnalysis -SlotDirectory $slot -Role baseline -Runner decision -SequenceIndex 1
Assert-LayerFrame ($result.verdict -ceq 'baseline-recorded' -and $result.schema -ceq 'nene-pixel-p4-indexed-actual-app-frame-v8' -and -not $result.Contains('protocol_id') -and -not $result.Contains('gross_regression')) 'legacy unchanged v8 shape and20ms admission'
$baselinePath = Save-P4FrameAnalysis $result (Join-Path $temporaryRoot 'baseline-analysis.json')
$slot = New-P4FrameSlotFixture -Name 'candidate' -Role candidate -Runner decision -SequenceIndex 2 -Families $workloadOrder
$result = Invoke-P4FrameFixtureAnalysis -SlotDirectory $slot -Role candidate -Runner decision -SequenceIndex 2 -BaselineAnalysisPath $baselinePath
Assert-LayerFrame ($result.verdict -ceq 'pass' -and $result.verdict_rule -ceq 'lane3-2026-09-23-relative') 'legacy candidate20ms original33.33 guard'
$slot = New-P4FrameSlotFixture -Name 'diagnostic' -Role baseline -Runner diagnostic -SequenceIndex 3 -Families $diagnosticOrder
$result = Invoke-P4FrameFixtureAnalysis -SlotDirectory $slot -Role baseline -Runner diagnostic -SequenceIndex 3
Assert-LayerFrame ($result.verdict -ceq 'inconclusive' -and $result.families.Count -eq 3) 'legacy diagnostic original catalog'
Assert-LayerRefuses { Invoke-P4FrameFixtureAnalysis -SlotDirectory $slot -Role baseline -Runner diagnostic -SequenceIndex 5 } 'legacy sequence greater than4'

"PASS: $script:assertions assertions; evidence $evidenceRelative"

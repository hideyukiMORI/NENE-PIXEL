# Device-free #145 shared collector/analyzer execution contract; immutable lab evidence only.
# Usage: pwsh -NoProfile -File docs/quality/validate-p4-frame-execution-contract.ps1
param([string]$OutputDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Get-NenePixelLabPath ('evidence/145-frame-execution-contract/' +
        (Get-Date -Format 'yyyyMMddTHHmmssfff') + '-' + [guid]::NewGuid().ToString('N')) -StartDirectory $PSScriptRoot
}
$evidenceRoot = [IO.Path]::GetFullPath((Get-NenePixelLabPath 'evidence' -StartDirectory $PSScriptRoot)).TrimEnd('\', '/')
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $OutputDirectory.StartsWith($evidenceRoot + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) { throw 'Contract evidence must be inside development lab evidence.' }
if ([IO.File]::Exists($OutputDirectory) -or [IO.Directory]::Exists($OutputDirectory)) { throw 'Contract evidence already exists.' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$preflightPath = Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1'
$phase = 'nene-pixel-p4-layer-phase-verification-v1'
$legacy = 'nene-pixel-p4-indexed-cutover-verification-v7'
$script:checks = 0
$timer = [Diagnostics.Stopwatch]::StartNew()
$result = [ordered]@{
    schema = 'nene-pixel-p4-frame-execution-contract-validator-v1'; status = 'FAIL'; checks = 0
    elapsed_seconds = 0; command = 'pwsh -NoProfile -File docs/quality/validate-p4-frame-execution-contract.ps1'
    runtime = $PSVersionTable.PSVersion.ToString(); source_sha256 = [ordered]@{}; direct_imports = [ordered]@{}
}
function Check([bool]$Condition, [string]$Name) {
    if (-not $Condition) { throw "Frame execution contract: $Name" }
    $script:checks++
}
function Equal($Actual, $Expected, [string]$Name) {
    Check ((ConvertTo-Json -InputObject $Actual -Depth 20 -Compress) -ceq
        (ConvertTo-Json -InputObject $Expected -Depth 20 -Compress)) $Name
}
function Refuses([scriptblock]$Action, [string]$Name) {
    $refused = $false
    try { & $Action | Out-Null } catch { $refused = $true }
    Check $refused $Name
}
function Check-Record($Actual, $Expected, [string]$Name) {
    Check ($Actual -is [Collections.Specialized.OrderedDictionary]) "$Name ordered dictionary"
    Equal @($Actual.Keys) @($Expected.Keys) "$Name exact ordered keys"
    foreach ($key in $Expected.Keys) { Equal $Actual[$key] $Expected[$key] "$Name.$key" }
}
# Independent literal event oracle; neither the resolver nor its workload helper builds this data.
function Expected-Workloads([string[]]$Names) {
    foreach ($name in $Names) {
        $row = switch -CaseSensitive ($name) {
            'canvas16_tap' { @(16, 16, 0, 2, 1, 1, 1, 1) }
            'canvas256_layers16_tap' { @(256, 256, 0, 2, 1, 1, 1, 1) }
            'canvas256_repeated_diagonal' { @(256, 256, 16, 18, 17, 1, 4081, 256) }
            'canvas256_repeated_diagonal_window_x2' { @(256, 256, 16, 18, 17, 1, 4081, 256) }
            'canvas256_layers16_repeated_diagonal' { @(256, 256, 16, 18, 17, 1, 4081, 256) }
            'canvas256_underlay_repeated_diagonal' { @(256, 256, 16, 18, 17, 1, 4081, 256) }
            default { throw 'Unexpected oracle workload.' }
        }
        [ordered]@{ workload = $name; canvas_width = $row[0]; canvas_height = $row[1]
            move_event_count = $row[2]; motion_event_count = $row[3]; preview_event_count = $row[4]
            commit_event_count = $row[5]; raw_position_count = $row[6]; effective_change_count = $row[7] }
    }
}
try {
    $sources = @($preflightPath, $PSCommandPath, (Join-Path $PSScriptRoot 'P4_LAYER_PHASE_PROTOCOL.md'),
        (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1'),
        (Join-Path $PSScriptRoot 'bounded-native-command.ps1'),
        (Join-Path $PSScriptRoot 'baseline-profile-evidence.ps1'),
        (Join-Path $PSScriptRoot 'measurements/p4-indexed-device-state.ps1'),
        (Join-Path $PSScriptRoot 'measurements/android-window-state.ps1'),
        (Join-Path $PSScriptRoot 'measurements/m2-package-dexopt.ps1'))
    foreach ($source in $sources) {
        $relative = [IO.Path]::GetRelativePath((Split-Path -Parent $PSScriptRoot), $source).Replace('\', '/')
        $result.source_sha256[$relative] = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($source.EndsWith('.ps1', [StringComparison]::Ordinal)) {
            $tokens = $null; $errors = $null
            $ast = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$errors)
            Check (@($errors).Count -eq 0) "AST $relative"
            $imports = @($ast.FindAll({ param($node)
                $node -is [Management.Automation.Language.CommandAst] -and
                $node.InvocationOperator -eq [Management.Automation.Language.TokenKind]::Dot
            }, $true) | ForEach-Object { $_.Extent.Text })
            $result.direct_imports[$relative] = $imports
        }
    }
    . $preflightPath
    $groupRows = @(
        @('single', 'baseline_single', '8120c06fae1a372b23d2a7af4f50aa2b9cdfeff9',
            @('canvas16_tap', 'canvas256_repeated_diagonal'),
            @('canvas16_tap', 'canvas256_repeated_diagonal', 'canvas256_repeated_diagonal_window_x2'),
            [ordered]@{ canvas16_tap = 'empty'; canvas256_repeated_diagonal = 'empty'; canvas256_repeated_diagonal_window_x2 = 'window_x2' }),
        @('layers16', 'baseline_layers16', '169b59287ca60e77e07ac91690450dd1a44b9ba4',
            @('canvas256_layers16_tap', 'canvas256_layers16_repeated_diagonal'),
            @('canvas256_layers16_tap', 'canvas256_layers16_repeated_diagonal'),
            [ordered]@{ canvas256_layers16_tap = 'maximum_layers16'; canvas256_layers16_repeated_diagonal = 'maximum_layers16' }),
        @('underlay', 'baseline_underlay', 'f92b1006be5f7145a32258446474f8640b14b60b',
            @('canvas256_underlay_repeated_diagonal'), @('canvas256_underlay_repeated_diagonal'),
            [ordered]@{ canvas256_underlay_repeated_diagonal = 'underlay' })
    )
    $legacyOrder = @('decision:baseline', 'decision:candidate', 'diagnostic:baseline', 'diagnostic:candidate')
    $phaseOrder = @(
        'single:decision:baseline', 'single:decision:candidate', 'single:diagnostic:baseline', 'single:diagnostic:candidate',
        'layers16:decision:baseline', 'layers16:decision:candidate', 'layers16:diagnostic:baseline', 'layers16:diagnostic:candidate',
        'underlay:decision:baseline', 'underlay:decision:candidate', 'underlay:diagnostic:baseline', 'underlay:diagnostic:candidate'
    )
    $phaseBounds = @(1950, 1950, 975, 975, 1950, 1950, 750, 750, 1125, 1125, 525, 525)
    $measured = 0; $warmups = 0; $bounds = 0
    foreach ($protocol in @($legacy, $phase)) {
        $isPhase = $protocol -ceq $phase
        $count = if ($isPhase) { 12 } else { 4 }
        for ($sequence = 1; $sequence -le $count; $sequence++) {
            $position = ($sequence - 1) % 4 + 1
            $row = $groupRows[$(if ($isPhase) { [int][Math]::Floor(($sequence - 1) / 4) } else { 0 })]
            $role = if ($position % 2 -eq 1) { 'baseline' } else { 'candidate' }
            $runner = if ($position -le 2) { 'decision' } else { 'diagnostic' }
            $slotId = if ($isPhase) { "frame-$sequence-$($row[0])-$role-$runner" } else { "frame-$sequence-$role-$runner" }
            $names = @($(if ($runner -ceq 'decision') { $row[3] } else { $row[4] }))
            $expected = [ordered]@{
                protocol_id = $protocol; slot_id = $slotId; group_id = if ($isPhase) { $row[0] } else { $null }
                artifact_role = if ($isPhase -and $role -ceq 'baseline') { $row[1] } else { $role }
                role = $role; runner = $runner; sequence_index = $sequence; group_sequence_index = $position; attempt = 1
                frame_schema = if ($isPhase) { 'nene-pixel-p4-indexed-actual-app-frame-v9' } else { 'nene-pixel-p4-indexed-actual-app-frame-v8' }
                experiment_schema = if ($isPhase) { 'nene-pixel-p4-indexed-frame-experiment-v6' } else { 'nene-pixel-p4-indexed-frame-experiment-v5' }
                verdict_id = if ($isPhase) { 'layer-phase-2026-10-03-relative-m5' } else { 'lane3-2026-09-23-relative' }
                frame_directory_name = if ($isPhase) { 'slot-{0:D2}-{1}-{2}-{3}-attempt-1' -f $sequence, $row[0], $runner, $role }
                    else { 'slot-{0:D2}-{1}-{2}-attempt-1' -f $sequence, $runner, $role }
                baseline_slot_id = if ($position -eq 2) {
                    if ($isPhase) { "frame-$($sequence - 1)-$($row[0])-baseline-decision" } else { 'frame-1-baseline-decision' }
                } else { $null }
                baseline_production_commit = $row[2]; workload_order = $names
                decision_workload_order = $row[3]; diagnostic_workload_order = $row[4]
                workload_catalog = @(Expected-Workloads $names); decision_workload_catalog = @(Expected-Workloads $row[3])
                diagnostic_workload_catalog = @(Expected-Workloads $row[4]); warmups = 5
                samples = if ($runner -ceq 'decision') { 50 } else { 10 }
                timeout_seconds = if ($isPhase) { $phaseBounds[$sequence - 1] } elseif ($runner -ceq 'decision') { 1950 } else { 975 }
                comparison_order = if ($isPhase) { $phaseOrder } else { $legacyOrder }
                input_p95_gate_ms = if ($isPhase -and $role -ceq 'candidate') { [double]16.67 } else { [double]33.33 }
                relative_p95_tolerance_ms = [double]1.0; relative_p99_tolerance_ms = [double]2.0
                gross_overrun_ms = [double]33.34; gross_input_ms = [double]100.0; geometry_id = 'initial-fit-centered-v1'
                association_workload = if ($isPhase -and $row[0] -ceq 'layers16') { 'canvas256_layers16_tap' } else { $null }
                window_diagnostic_workload = if ($row[0] -ceq 'single') { 'canvas256_repeated_diagonal_window_x2' } else { $null }
                setup_by_workload = $row[5]
            }
            $actual = Get-P4FrameExecutionContract -ProtocolId $protocol -SlotId $slotId
            Check-Record $actual $expected $slotId
            foreach ($field in @('input_p95_gate_ms', 'relative_p95_tolerance_ms', 'relative_p99_tolerance_ms', 'gross_overrun_ms', 'gross_input_ms')) {
                Check (($actual[$field] -is [double] -or $actual[$field] -is [decimal]) -and
                    -not [double]::IsNaN($actual[$field]) -and -not [double]::IsInfinity($actual[$field])) "$slotId $field finite numeric"
            }
            foreach ($field in @('workload_catalog', 'decision_workload_catalog', 'diagnostic_workload_catalog')) {
                $oracle = @($expected[$field]); $catalog = @($actual[$field])
                for ($i = 0; $i -lt $oracle.Count; $i++) { Check-Record $catalog[$i] $oracle[$i] "$slotId $field $i" }
            }
            if (-not $isPhase) { Check-Record (Get-P4FrameExecutionContract -SlotId $slotId) $expected "default $slotId" }
            if ($isPhase) { $measured += $names.Count * $actual.samples; $warmups += $names.Count * $actual.warmups; $bounds += $actual.timeout_seconds }
            # Mutate every returned mutable branch and require an exact pristine next invocation.
            foreach ($field in @('workload_order', 'decision_workload_order', 'diagnostic_workload_order', 'comparison_order')) { $actual[$field][0] = 'foreign' }
            foreach ($field in @('workload_catalog', 'decision_workload_catalog', 'diagnostic_workload_catalog')) { $actual[$field][0].workload = 'foreign'; $actual[$field][0].canvas_width = 999 }
            $actual.setup_by_workload[$row[4][0]] = 'foreign'; $actual.slot_id = 'foreign'
            Check-Record (Get-P4FrameExecutionContract -ProtocolId $protocol -SlotId $slotId) $expected "isolated $slotId"
        }
    }
    Check ($measured -eq 620 -and $warmups -eq 110 -and $bounds -eq 14550) 'phase population and bound totals'
    foreach ($unknown in @($null, '', 'unknown', $legacy.ToUpperInvariant(), $phase.ToUpperInvariant(), "$phase-extra")) {
        Refuses { Get-P4FrameExecutionContract -ProtocolId $unknown -SlotId 'frame-1-baseline-decision' } 'unknown protocol refusal'
    }
    foreach ($unknown in @($null, '', 'unknown', 'frame-1-single-baseline-decision', 'FRAME-1-BASELINE-DECISION')) {
        Refuses { Get-P4FrameExecutionContract -ProtocolId $legacy -SlotId $unknown } 'unknown or mixed legacy slot refusal'
    }
    foreach ($unknown in @($null, '', 'unknown', 'frame-1-baseline-decision', 'frame-1-underlay-baseline-decision')) {
        Refuses { Get-P4FrameExecutionContract -ProtocolId $phase -SlotId $unknown } 'unknown or mixed phase slot refusal'
    }
    foreach ($unknown in @($null, @(), @(''), @('foreign'), @('CANVAS16_TAP'))) {
        Refuses { Get-P4FrameWorkloadCatalog -WorkloadOrder $unknown } 'unknown workload refusal'
    }
    $allNames = @('canvas16_tap', 'canvas256_repeated_diagonal', 'canvas256_repeated_diagonal_window_x2',
        'canvas256_layers16_tap', 'canvas256_layers16_repeated_diagonal', 'canvas256_underlay_repeated_diagonal')
    Equal @(Get-P4FrameWorkloadCatalog -WorkloadOrder $allNames) @(Expected-Workloads $allNames) 'shared helper full event specifications'
    $experimentId = 'p4-layer-20261003'; $hash = 'a' * 64
    $projection = Get-P4LayerFrameExperimentContract -ExperimentId $experimentId -PreflightSha256 $hash
    $expectedProjection = [ordered]@{
        schema = 'nene-pixel-p4-indexed-frame-experiment-v6'; protocol_id = $phase; experiment_id = $experimentId
        preflight_sha256 = $hash; comparison_order = $phaseOrder; slot_catalog = @(Get-P4FrameSlotCatalog -ProtocolId $phase)
        slot_budget = 12; maximum_attempts_per_slot = 1; replacement_rule = 'none'
    }
    Check-Record $projection $expectedProjection 'phase experiment projection'
    $projection.comparison_order[0] = 'foreign'; $projection.slot_catalog[0].id = 'foreign'
    $projection.slot_catalog[0].families[0] = 'foreign'; $projection.preflight_sha256 = 'foreign'
    Check-Record (Get-P4LayerFrameExperimentContract -ExperimentId $experimentId -PreflightSha256 $hash) $expectedProjection 'projection mutation isolation'
    foreach ($badId in @($null, '', 'ab', 'Aaa', '-abc', 'a_b', ('a' * 65), "abc`n")) {
        Refuses { Get-P4LayerFrameExperimentContract -ExperimentId $badId -PreflightSha256 $hash } 'experiment identity refusal'
    }
    foreach ($badHash in @($null, '', ('a' * 63), ('a' * 65), ('A' * 64), ('g' * 64), ($hash + "`n"))) {
        Refuses { Get-P4LayerFrameExperimentContract -ExperimentId $experimentId -PreflightSha256 $badHash } 'preflight hash refusal'
    }
    foreach ($goodId in @('a00', ('a' * 64))) {
        Equal (Get-P4LayerFrameExperimentContract -ExperimentId $goodId -PreflightSha256 $hash).experiment_id $goodId 'experiment identity allowed boundary'
    }
    $result.status = 'PASS'; $result.contract_count = 16; $result.phase_measured = $measured
    $result.phase_warmups = $warmups; $result.phase_collector_bound_seconds = $bounds
} catch {
    $result.error = $_.Exception.Message
    throw
} finally {
    $timer.Stop(); $result.checks = $script:checks; $result.elapsed_seconds = $timer.Elapsed.TotalSeconds
    $result | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'validation.json') -Encoding utf8NoBOM
}
Write-Output "PASS: $($script:checks) frame execution contract checks. Evidence: $OutputDirectory"

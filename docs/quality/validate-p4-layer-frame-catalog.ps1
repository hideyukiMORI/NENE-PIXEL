# Device-free Issue #145 frame catalog contract. Writes fresh lab evidence; never collects samples.
# Usage: pwsh -NoProfile -File docs/quality/validate-p4-layer-frame-catalog.ps1 [-OutputDirectory <fresh-lab-path>]
# Exit 0: exact catalog PASS. Any refusal/failure throws and preserves validation.json plus caller log.
param([string]$OutputDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Get-NenePixelLabPath ('evidence/145-frame-catalog/' +
        (Get-Date -Format 'yyyyMMddTHHmmssfff') + '-' + [guid]::NewGuid().ToString('N')) -StartDirectory $PSScriptRoot
}
$labEvidence = [IO.Path]::GetFullPath((Get-NenePixelLabPath 'evidence' -StartDirectory $PSScriptRoot)).TrimEnd('\', '/')
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $OutputDirectory.StartsWith($labEvidence + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) { throw 'Catalog evidence must be inside the development lab evidence directory.' }
if ([IO.File]::Exists($OutputDirectory) -or [IO.Directory]::Exists($OutputDirectory)) { throw 'Validator evidence already exists.' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$preflightPath = Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1'
$protocolPath = Join-Path $PSScriptRoot 'P4_LAYER_PHASE_PROTOCOL.md'
$script:checks = 0
$phase = 'nene-pixel-p4-layer-phase-verification-v1'
$historical = 'nene-pixel-p4-indexed-cutover-verification-v7'
$clock = [Diagnostics.Stopwatch]::StartNew()
$summary = [ordered]@{ schema = 'nene-pixel-p4-layer-frame-catalog-validator-v1'; status = 'failure';
    checks = 0; elapsed_seconds = 0; source_sha256 = [ordered]@{} }

function Check([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Frame catalog validator: $Message" }
    $script:checks++
}
function Check-Record($Actual, $Expected, [string]$Name) {
    Check ($Actual -is [Collections.IDictionary]) "$Name record type"
    $actualKeys = ($Actual.Keys | Sort-Object) -join ','
    $expectedKeys = ($Expected.Keys | Sort-Object) -join ','
    Check ($actualKeys -ceq $expectedKeys) "$Name exact keys"
    foreach ($key in $Expected.Keys) {
        $actualJson = ConvertTo-Json -InputObject $Actual[$key] -Depth 5 -Compress
        $expectedJson = ConvertTo-Json -InputObject $Expected[$key] -Depth 5 -Compress
        Check ($actualJson -ceq $expectedJson) "$Name.$key exact value"
    }
}
function Expect-Refusal([scriptblock]$Action, [string]$Message, [string]$ExpectedError) {
    $refusal = $null
    try { & $Action | Out-Null } catch { $refusal = $_.Exception.Message }
    Check ($null -ne $refusal) "$Message refused"
    Check ($refusal -ceq $ExpectedError) "$Message refusal boundary"
}

try {
    foreach ($source in @($preflightPath, $PSCommandPath)) {
        $tokens = $null; $parseErrors = $null
        [void][Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$parseErrors)
        Check (@($parseErrors).Count -eq 0) "AST parse $(Split-Path -Leaf $source)"
    }
    foreach ($source in @($preflightPath, $PSCommandPath, $protocolPath,
            (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1'),
            (Join-Path $PSScriptRoot 'bounded-native-command.ps1'),
            (Join-Path $PSScriptRoot 'baseline-profile-evidence.ps1'),
            (Join-Path $PSScriptRoot 'measurements/p4-indexed-device-state.ps1'))) {
        $summary.source_sha256[(Split-Path -Leaf $source)] = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    . $preflightPath

    # Literal historical oracle: all values and the exact old key set, including omitted new fields.
    $oldExpected = @(
        [ordered]@{ id = 'frame-1-baseline-decision'; lane = 'frame'; role = 'baseline'; runner = 'decision';
            run = 1; timeout_seconds = 1950; warmups = 5; samples = 50 },
        [ordered]@{ id = 'frame-2-candidate-decision'; lane = 'frame'; role = 'candidate'; runner = 'decision';
            run = 2; timeout_seconds = 1950; warmups = 5; samples = 50 },
        [ordered]@{ id = 'frame-3-baseline-diagnostic'; lane = 'frame'; role = 'baseline'; runner = 'diagnostic';
            run = 3; timeout_seconds = 975; warmups = 5; samples = 10 },
        [ordered]@{ id = 'frame-4-candidate-diagnostic'; lane = 'frame'; role = 'candidate'; runner = 'diagnostic';
            run = 4; timeout_seconds = 975; warmups = 5; samples = 10 }
    )
    $defaultSlots = @(Get-P4FrameSlotCatalog)
    $explicitOldSlots = @(Get-P4FrameSlotCatalog -ProtocolId $historical)
    Check ($defaultSlots.Count -eq 4 -and $explicitOldSlots.Count -eq 4) 'historical population'
    for ($i = 0; $i -lt 4; $i++) {
        Check-Record $defaultSlots[$i] $oldExpected[$i] "default[$i]"
        Check-Record $explicitOldSlots[$i] $oldExpected[$i] "explicit-v7[$i]"
    }

    foreach ($unknown in @('', 'unknown', "$phase-extra", $phase.ToUpperInvariant(), $historical.ToUpperInvariant())) {
        Expect-Refusal { Get-P4FrameSlotCatalog -ProtocolId $unknown } "unknown slots '$unknown'" 'Unknown P4 frame slot protocol.'
        Expect-Refusal { Get-P4FrameGroupCatalog -ProtocolId $unknown } "unknown groups '$unknown'" 'Unknown P4 frame group protocol.'
    }
    Expect-Refusal { Get-P4FrameGroupCatalog -ProtocolId $historical } 'historical group request' 'Unknown P4 frame group protocol.'

    # Expected names, commits, order and families come directly from the normative table.
    $groupRows = @(
        @('single', 'baseline_single', '8120c06fae1a372b23d2a7af4f50aa2b9cdfeff9',
            @('canvas16_tap', 'canvas256_repeated_diagonal'),
            @('canvas16_tap', 'canvas256_repeated_diagonal', 'canvas256_repeated_diagonal_window_x2'), $null, 'layers16'),
        @('layers16', 'baseline_layers16', '169b59287ca60e77e07ac91690450dd1a44b9ba4',
            @('canvas256_layers16_tap', 'canvas256_layers16_repeated_diagonal'),
            @('canvas256_layers16_tap', 'canvas256_layers16_repeated_diagonal'), 'single', 'underlay'),
        @('underlay', 'baseline_underlay', 'f92b1006be5f7145a32258446474f8640b14b60b',
            @('canvas256_underlay_repeated_diagonal'), @('canvas256_underlay_repeated_diagonal'), 'layers16', $null)
    )
    $groups = @(Get-P4FrameGroupCatalog -ProtocolId $phase)
    Check ($groups.Count -eq 3) 'three groups'
    for ($i = 0; $i -lt $groupRows.Count; $i++) {
        $row = $groupRows[$i]
        $expected = [ordered]@{ id = $row[0]; baseline_artifact_role = $row[1]; baseline_production_commit = $row[2];
            decision_families = $row[3]; diagnostic_families = $row[4]; sequence = $i + 1;
            preceding_group_id = $row[5]; following_group_id = $row[6]; candidate_artifact_role = 'candidate';
            protocol_id = $phase; frame_schema = 'nene-pixel-p4-indexed-actual-app-frame-v9';
            experiment_schema = 'nene-pixel-p4-indexed-frame-experiment-v6'; verdict_id = 'layer-phase-2026-10-03-relative-m5' }
        Check-Record $groups[$i] $expected "group[$i]"
    }

    # Six independently pinned decision rows: group, comparison role, artifact role, runner, samples, bound, comparator.
    $slotRows = @(
        @('single', 'baseline', 'baseline_single', 'decision', 50, 1950, $null),
        @('single', 'candidate', 'candidate', 'decision', 50, 1950, 'frame-1-single-baseline-decision'),
        @('layers16', 'baseline', 'baseline_layers16', 'decision', 50, 1950, $null),
        @('layers16', 'candidate', 'candidate', 'decision', 50, 1950, 'frame-3-layers16-baseline-decision'),
        @('underlay', 'baseline', 'baseline_underlay', 'decision', 50, 1125, $null),
        @('underlay', 'candidate', 'candidate', 'decision', 50, 1125, 'frame-5-underlay-baseline-decision')
    )
    $expectedIds = @(
        'frame-1-single-baseline-decision', 'frame-2-single-candidate-decision',
        'frame-3-layers16-baseline-decision', 'frame-4-layers16-candidate-decision',
        'frame-5-underlay-baseline-decision', 'frame-6-underlay-candidate-decision'
    )
    $slots = @(Get-P4FrameSlotCatalog -ProtocolId $phase)
    Check ($slots.Count -eq 6) 'six phase slots'
    Check (@($slots.id | Select-Object -Unique).Count -eq 6) 'unique slot identities'
    Check (@($slots | Where-Object { $_.runner -cne 'decision' }).Count -eq 0) 'no phase diagnostic slot'
    for ($i = 0; $i -lt $slotRows.Count; $i++) {
        $row = $slotRows[$i]
        $groupRow = $groupRows[[int][Math]::Floor($i / 2)]
        $families = @($groupRow[3])
        $expected = [ordered]@{ id = $expectedIds[$i]; lane = 'frame'; group_id = $row[0]; role = $row[1];
            artifact_role = $row[2]; runner = $row[3]; run = $i + 1; attempt = 1;
            protocol_id = $phase; frame_schema = 'nene-pixel-p4-indexed-actual-app-frame-v9';
            experiment_schema = 'nene-pixel-p4-indexed-frame-experiment-v6'; verdict_id = 'layer-phase-2026-10-03-relative-m5';
            baseline_production_commit = $groupRow[2]; families = $families; baseline_slot_id = $row[6];
            preceding_slot_id = if ($i -gt 0) { $expectedIds[$i - 1] } else { $null };
            following_slot_id = if ($i -lt 5) { $expectedIds[$i + 1] } else { $null };
            timeout_seconds = $row[5]; warmups = 5; samples = $row[4] }
        Check-Record $slots[$i] $expected "slot[$i]"
        $operationCount = @($slots[$i].families).Count * ($slots[$i].warmups + $slots[$i].samples)
        Check ($slots[$i].timeout_seconds -eq 300 + 15 * $operationCount) "slot[$i] bound arithmetic"
    }

    $measured = 0; $warmup = 0; $bound = 0
    $expectedTotals = @(@('single', 200, 20, 220), @('layers16', 200, 20, 220), @('underlay', 100, 10, 110))
    foreach ($row in $expectedTotals) {
        $groupMeasured = 0; $groupWarmup = 0
        foreach ($slot in @($slots | Where-Object { $_.group_id -ceq $row[0] })) {
            $groupMeasured += @($slot.families).Count * $slot.samples
            $groupWarmup += @($slot.families).Count * $slot.warmups
            $bound += $slot.timeout_seconds
        }
        Check ($groupMeasured -eq $row[1]) "$($row[0]) measured total"
        Check ($groupWarmup -eq $row[2]) "$($row[0]) warmup total"
        Check ($groupMeasured + $groupWarmup -eq $row[3]) "$($row[0]) operation total"
        $measured += $groupMeasured; $warmup += $groupWarmup
    }
    Check ($measured -eq 500 -and $warmup -eq 50 -and $measured + $warmup -eq 550) 'phase operation totals'
    Check ($bound -eq 10050) 'phase collector bound total'
    foreach ($candidate in @($slots | Where-Object { $_.role -ceq 'candidate' -and $_.runner -ceq 'decision' })) {
        $baseline = @($slots | Where-Object { $_.id -ceq $candidate.baseline_slot_id })
        Check ($baseline.Count -eq 1) "$($candidate.id) one comparison baseline"
        Check ($baseline[0].group_id -ceq $candidate.group_id -and $baseline[0].role -ceq 'baseline' -and
            $baseline[0].runner -ceq 'decision') "$($candidate.id) group and comparison roles"
        Check ($baseline[0].baseline_production_commit -ceq $candidate.baseline_production_commit -and
            ($baseline[0].families -join ',') -ceq ($candidate.families -join ',')) "$($candidate.id) comparator commit/families"
    }
    Check ($slots[2].preceding_slot_id -ceq 'frame-2-single-candidate-decision' -and $null -eq $slots[2].baseline_slot_id) 'layers group ordering is not a comparison binding'
    Check ($slots[4].preceding_slot_id -ceq 'frame-4-layers16-candidate-decision' -and $null -eq $slots[4].baseline_slot_id) 'underlay group ordering is not a comparison binding'

    # Whole phase composition: frame 1-6, memory 7-16, publication 17, SAF 18.
    $memorySlots = @(Get-P4LayerMemorySlotCatalog -ProtocolId $phase)
    Check ($memorySlots.Count -eq 10 -and (($memorySlots.sequence_index) -join ',') -ceq '7,8,9,10,11,12,13,14,15,16') 'memory sequence 7-16'
    $storageSlots = @(Get-P4LayerStorageSlotCatalog -ProtocolId $phase)
    Check ($storageSlots.Count -eq 2 -and $storageSlots[0].id -ceq 'publication-layers16-candidate' -and
        $storageSlots[0].sequence_index -eq 17 -and $storageSlots[1].id -ceq 'saf-save-layers16-candidate' -and
        $storageSlots[1].sequence_index -eq 18) 'storage sequence 17-18'
    $phaseSlots = @(Get-P4SlotCatalog -ProtocolId $phase)
    Check ($phaseSlots.Count -eq 18) 'eighteen phase slots'
    Check ((@($phaseSlots[0..5] | ForEach-Object { $_.id }) -join ',') -ceq ($expectedIds -join ',')) 'phase composition starts with six frame slots'
    $experiment = Get-P4LayerFrameExperimentContract -ExperimentId 'offline-frame-catalog' -PreflightSha256 ('0' * 64)
    Check ($experiment.slot_budget -eq 6) 'experiment slot budget 6'
    Check ((@($experiment.comparison_order) -join ',') -ceq ('single:decision:baseline,single:decision:candidate,' +
        'layers16:decision:baseline,layers16:decision:candidate,underlay:decision:baseline,underlay:decision:candidate')) 'experiment comparison order'

    # Historical execution contract keeps its diagnostic workloads; the phase contract has none.
    $oldDiagnostic = Get-P4FrameExecutionContract -SlotId 'frame-3-baseline-diagnostic'
    Check ((@($oldDiagnostic.workload_order) -join ',') -ceq 'canvas16_tap,canvas256_repeated_diagonal,canvas256_repeated_diagonal_window_x2' -and
        $oldDiagnostic.window_diagnostic_workload -ceq 'canvas256_repeated_diagonal_window_x2' -and
        @($oldDiagnostic.diagnostic_workload_catalog).Count -eq 3 -and @($oldDiagnostic.setup_by_workload.Keys).Count -eq 3) 'historical diagnostic contract unchanged'
    foreach ($slot in $slots) {
        $contract = Get-P4FrameExecutionContract -ProtocolId $phase -SlotId $slot.id
        Check (@($contract.diagnostic_workload_order).Count -eq 0 -and @($contract.diagnostic_workload_catalog).Count -eq 0 -and
            $null -eq $contract.window_diagnostic_workload -and
            (@($contract.setup_by_workload.Keys) -join ',') -ceq (@($slot.families) -join ',') -and
            @($contract.comparison_order).Count -eq 6) "$($slot.id) phase contract has no diagnostic workload"
    }

    # Fully populated required top-level shape reaches the unchanged identity gate before any file/device check.
    $manifest = [ordered]@{ schema = $script:P4ManifestSchema; protocol = [ordered]@{ id = $phase };
        created_utc = '2026-10-03T00:00:00Z'; experiment_id = 'offline-frame-catalog'; output_directory = $OutputDirectory;
        roles = 'unused'; tools = 'unused'; toolchain = 'unused'; device = 'unused'; frame_experiment = 'unused';
        correctness = 'unused'; collector_contracts = 'unused'; slots = $oldExpected }
    Expect-Refusal { Assert-P4ManifestContract $manifest } 'phase manifest' 'Wrong P4 manifest/protocol identity.'

    # Fresh returned collections cannot poison a later caller's fixed contract.
    $groups[0].decision_families[0] = 'mutated'; $slots[0].families[0] = 'mutated'
    Check (@(Get-P4FrameGroupCatalog -ProtocolId $phase)[0].decision_families[0] -ceq 'canvas16_tap') 'fresh group families'
    Check (@(Get-P4FrameSlotCatalog -ProtocolId $phase)[0].families[0] -ceq 'canvas16_tap') 'fresh slot families'
    $summary.status = 'pass'
    $summary.measured_operations = $measured; $summary.warmup_operations = $warmup
    $summary.total_operations = $measured + $warmup; $summary.collector_bound_seconds = $bound
    $summary.group_count = 3; $summary.slot_count = 6; $summary.phase_slot_count = 18
} catch {
    $summary.error = $_.Exception.Message
    throw
} finally {
    $clock.Stop()
    $summary.checks = $script:checks; $summary.elapsed_seconds = $clock.Elapsed.TotalSeconds
    $summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'validation.json') -Encoding utf8NoBOM
}
Write-Output "PASS: $($script:checks) frame catalog checks. Evidence: $OutputDirectory"

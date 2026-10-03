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

    # Twelve independently pinned rows: group, comparison role, artifact role, runner, samples, bound, comparator.
    $slotRows = @(
        @('single', 'baseline', 'baseline_single', 'decision', 50, 1950, $null),
        @('single', 'candidate', 'candidate', 'decision', 50, 1950, 'frame-1-single-baseline-decision'),
        @('single', 'baseline', 'baseline_single', 'diagnostic', 10, 975, $null),
        @('single', 'candidate', 'candidate', 'diagnostic', 10, 975, $null),
        @('layers16', 'baseline', 'baseline_layers16', 'decision', 50, 1950, $null),
        @('layers16', 'candidate', 'candidate', 'decision', 50, 1950, 'frame-5-layers16-baseline-decision'),
        @('layers16', 'baseline', 'baseline_layers16', 'diagnostic', 10, 750, $null),
        @('layers16', 'candidate', 'candidate', 'diagnostic', 10, 750, $null),
        @('underlay', 'baseline', 'baseline_underlay', 'decision', 50, 1125, $null),
        @('underlay', 'candidate', 'candidate', 'decision', 50, 1125, 'frame-9-underlay-baseline-decision'),
        @('underlay', 'baseline', 'baseline_underlay', 'diagnostic', 10, 525, $null),
        @('underlay', 'candidate', 'candidate', 'diagnostic', 10, 525, $null)
    )
    $expectedIds = @(
        'frame-1-single-baseline-decision', 'frame-2-single-candidate-decision',
        'frame-3-single-baseline-diagnostic', 'frame-4-single-candidate-diagnostic',
        'frame-5-layers16-baseline-decision', 'frame-6-layers16-candidate-decision',
        'frame-7-layers16-baseline-diagnostic', 'frame-8-layers16-candidate-diagnostic',
        'frame-9-underlay-baseline-decision', 'frame-10-underlay-candidate-decision',
        'frame-11-underlay-baseline-diagnostic', 'frame-12-underlay-candidate-diagnostic'
    )
    $slots = @(Get-P4FrameSlotCatalog -ProtocolId $phase)
    Check ($slots.Count -eq 12) 'twelve phase slots'
    Check (@($slots.id | Select-Object -Unique).Count -eq 12) 'unique slot identities'
    for ($i = 0; $i -lt $slotRows.Count; $i++) {
        $row = $slotRows[$i]
        $groupRow = $groupRows[[int][Math]::Floor($i / 4)]
        $families = @(if ($row[3] -ceq 'decision') { $groupRow[3] } else { $groupRow[4] })
        $expected = [ordered]@{ id = $expectedIds[$i]; lane = 'frame'; group_id = $row[0]; role = $row[1];
            artifact_role = $row[2]; runner = $row[3]; run = $i + 1; attempt = 1;
            protocol_id = $phase; frame_schema = 'nene-pixel-p4-indexed-actual-app-frame-v9';
            experiment_schema = 'nene-pixel-p4-indexed-frame-experiment-v6'; verdict_id = 'layer-phase-2026-10-03-relative-m5';
            baseline_production_commit = $groupRow[2]; families = $families; baseline_slot_id = $row[6];
            preceding_slot_id = if ($i -gt 0) { $expectedIds[$i - 1] } else { $null };
            following_slot_id = if ($i -lt 11) { $expectedIds[$i + 1] } else { $null };
            timeout_seconds = $row[5]; warmups = 5; samples = $row[4] }
        Check-Record $slots[$i] $expected "slot[$i]"
        $operationCount = @($slots[$i].families).Count * ($slots[$i].warmups + $slots[$i].samples)
        Check ($slots[$i].timeout_seconds -eq 300 + 15 * $operationCount) "slot[$i] bound arithmetic"
    }

    $measured = 0; $warmup = 0; $bound = 0
    $expectedTotals = @(@('single', 260, 50, 310), @('layers16', 240, 40, 280), @('underlay', 120, 20, 140))
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
    Check ($measured -eq 620 -and $warmup -eq 110 -and $measured + $warmup -eq 730) 'phase operation totals'
    Check ($bound -eq 14550) 'phase collector bound total'
    foreach ($candidate in @($slots | Where-Object { $_.role -ceq 'candidate' -and $_.runner -ceq 'decision' })) {
        $baseline = @($slots | Where-Object { $_.id -ceq $candidate.baseline_slot_id })
        Check ($baseline.Count -eq 1) "$($candidate.id) one comparison baseline"
        Check ($baseline[0].group_id -ceq $candidate.group_id -and $baseline[0].role -ceq 'baseline' -and
            $baseline[0].runner -ceq 'decision') "$($candidate.id) group and comparison roles"
        Check ($baseline[0].baseline_production_commit -ceq $candidate.baseline_production_commit -and
            ($baseline[0].families -join ',') -ceq ($candidate.families -join ',')) "$($candidate.id) comparator commit/families"
    }
    Check ($slots[4].preceding_slot_id -ceq 'frame-4-single-candidate-diagnostic' -and $null -eq $slots[4].baseline_slot_id) 'layers group ordering is not a comparison binding'
    Check ($slots[8].preceding_slot_id -ceq 'frame-8-layers16-candidate-diagnostic' -and $null -eq $slots[8].baseline_slot_id) 'underlay group ordering is not a comparison binding'
    Check ($null -eq $slots[3].baseline_slot_id -and $slots[3].preceding_slot_id -ceq 'frame-3-single-baseline-diagnostic') 'diagnostic predecessor is not a decision comparator'

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
    $summary.group_count = 3; $summary.slot_count = 12
} catch {
    $summary.error = $_.Exception.Message
    throw
} finally {
    $clock.Stop()
    $summary.checks = $script:checks; $summary.elapsed_seconds = $clock.Elapsed.TotalSeconds
    $summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'validation.json') -Encoding utf8NoBOM
}
Write-Output "PASS: $($script:checks) frame catalog checks. Evidence: $OutputDirectory"

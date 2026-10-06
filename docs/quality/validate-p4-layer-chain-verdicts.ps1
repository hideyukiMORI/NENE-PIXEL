# Focused host-only checks of the frame chain verdicts (Issue #145 R2). No ADB, Gradle, or device.
param([Parameter(Mandatory)][string]$OutputDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1')
$lab = [IO.Path]::GetFullPath((Get-NenePixelLabPath 'evidence' -StartDirectory $PSScriptRoot)).TrimEnd('\', '/')
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $OutputDirectory.StartsWith($lab + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    (Test-Path -LiteralPath $OutputDirectory)) { throw 'Fresh lab output required.' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$script:cases = [Collections.Generic.List[object]]::new()
$result = [ordered]@{ status = 'failure'; cases = @() }
function Check([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Case([string]$CaseName, [scriptblock]$Action) {
    $errorText = $null; try { & $Action | Out-Null } catch { $errorText = $_.ToString() }
    $script:cases.Add([ordered]@{ name = $CaseName; passed = ($null -eq $errorText); error = $errorText })
    Check ($null -eq $errorText) "Case failed: $CaseName : $errorText"
}
function Save-Json([string]$Path, $Value) {
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 50), [Text.UTF8Encoding]::new($false))
}
function Get-ChainFailure([scriptblock]$Action) {
    try { & $Action | Out-Null } catch { return $_ }
    throw 'Expected the chain to refuse.'
}

# The functions under test, taken from the wrapper without running its entry point.
$invokePath = Join-Path $PSScriptRoot 'measurements/invoke-p4-indexed-slot.ps1'
$parseErrors = $null
$invokeAst = [Management.Automation.Language.Parser]::ParseFile($invokePath, [ref]$null, [ref]$parseErrors)
Check ($parseErrors.Count -eq 0) 'Wrapper parse failure'
function Function-Ast([string]$Name) {
    $node = $invokeAst.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $Name }, $true)
    Check ($null -ne $node) "Missing function $Name"; return $node
}
foreach ($name in @('Get-P4FrameContinuingVerdicts', 'Get-P4FrameAnalyzerVerdicts', 'Test-P4PhaseFrameSlot',
        'Get-P4PhaseFrameGrossStop', 'Get-P4PhaseFrameChainRecord', 'Assert-P4CompletedChain',
        'Get-P4SlotProtocolId', 'Assert-P4PhaseAnalysisIdentity')) {
    . ([scriptblock]::Create((Function-Ast $name).Extent.Text))
}
# The capture seal is outside this check: the chain's own seal reading is covered by its existing validators.
function Read-P4VerifiedCaptureSeal { param($SlotDirectory, $SlotId, $ExpectedSha256) }

$phase = 'nene-pixel-p4-layer-phase-verification-v1'
$hash = 'a' * 64
$seal = 'b' * 64
$phaseCatalog = @(Get-P4SlotCatalog $phase)
$v7Frames = @(Get-P4FrameSlotCatalog)
$basis = [ordered]@{ maximum_frame_overrun_ms = '41.000000'; maximum_input_to_committed_result_ms = '20.000000'
    gross_overrun_threshold_ms = '33.340000'; gross_input_threshold_ms = '100.000000'; families = @('canvas16_tap') }

function New-Analysis($Slot, [bool]$Phase, [bool]$Gross) {
    $analysis = [ordered]@{ slot_id = $Slot.id; capture_seal_sha256 = $seal }
    if ($Phase -and $Slot.Contains('protocol_id')) {
        # T5b: a phase analysis names its protocol, comparison role, manifest and artifact role.
        $analysis.protocol_id = $Slot.protocol_id; $analysis.role = $Slot.role; $analysis.preflight_sha256 = $hash
        if ($Slot.lane -ceq 'frame') { $analysis.artifact_role = $Slot.artifact_role }
        else { $analysis.phase_context = [ordered]@{ protocol_id = $Slot.protocol_id; slot_id = $Slot.id
            preflight_sha256 = $hash; artifact_role = $Slot.artifact_role } }
    }
    if ($Phase -and $Slot.lane -ceq 'frame') {
        $analysis.gross_regression = $Gross
        $analysis.gross_regression_basis = if ($Gross) { $basis } else {
            [ordered]@{ maximum_frame_overrun_ms = '10.000000'; maximum_input_to_committed_result_ms = '12.000000'
                gross_overrun_threshold_ms = '33.340000'; gross_input_threshold_ms = '100.000000'; families = @() } }
    }
    return $analysis
}

function Complete-Slot([string]$Root, $Slot, $Analysis, [string]$Verdict) {
    $directory = Join-Path $Root $Slot.id
    [void][IO.Directory]::CreateDirectory($directory)
    Save-Json (Join-Path $directory 'analysis.json') $Analysis
    Save-Json (Join-Path $directory 'restoration.json') @{ restored = $true }
    Save-Json (Join-Path $directory 'worktree-after.json') @{ clean = $true }
    $completed = [ordered]@{ slot_id = $Slot.id; verdict = $Verdict; status = 'completed'; protocol_id = (Get-P4SlotProtocolId $Slot)
        preflight_sha256 = $hash; capture_seal_sha256 = $seal
        analysis_sha256 = Get-FileSha256 (Join-Path $directory 'analysis.json')
        restoration_sha256 = Get-FileSha256 (Join-Path $directory 'restoration.json')
        worktree_after_sha256 = Get-FileSha256 (Join-Path $directory 'worktree-after.json') }
    if ($Slot.lane -ceq 'frame') { $completed.complete_run = $true }
    Save-Json (Join-Path $directory 'completed.json') $completed
}

function Get-DefaultVerdict($Slot) {
    if ($Slot.lane -ceq 'frame') { return @(Get-P4FrameContinuingVerdicts $Slot)[0] }
    if ($Slot.lane -ceq 'publication') { return 'valid-constants-retained' }
    if ($Slot.lane -ceq 'saf-save') { return 'valid-descriptive' }
    return 'pass'
}

# Runs the phase chain in order: each slot is admitted (or refused) by Assert-P4CompletedChain against
# the completed records on disk, then completed unless the chain stopped it.
function Invoke-PhaseChain([string]$Name, [string]$GrossSlotId) {
    $root = Join-Path $OutputDirectory $Name
    [void][IO.Directory]::CreateDirectory($root)
    $outcome = [ordered]@{ ran = [Collections.Generic.List[string]]::new(); stopped = [ordered]@{} }
    foreach ($slot in $phaseCatalog) {
        $failure = $null
        try { Assert-P4CompletedChain -Root $root -Catalog $phaseCatalog -SlotId $slot.id -ManifestHash $hash }
        catch { $failure = $_ }
        if ($null -ne $failure) {
            $data = $failure.Exception.Data['p4_chain_stop']
            Check ($null -ne $data) "Chain refused without a gross stop: $($slot.id): $failure"
            $outcome.stopped[$slot.id] = $data | ConvertFrom-Json -AsHashtable
            continue
        }
        $gross = $slot.id -ceq $GrossSlotId
        $verdict = if ($gross -and $slot.role -ceq 'candidate') { 'PERFORMANCE_FAIL' } else { Get-DefaultVerdict $slot }
        Complete-Slot $root $slot (New-Analysis $slot $true $gross) $verdict
        $outcome.ran.Add($slot.id)
    }
    return $outcome
}

function Get-Analyses([string]$GrossSlotId, [int]$Through) {
    $analyses = @{}
    foreach ($slot in @($phaseCatalog | Where-Object { $_.lane -ceq 'frame' } | Select-Object -First $Through)) {
        $analyses[$slot.id] = New-Analysis $slot $true ($slot.id -ceq $GrossSlotId)
    }
    return $analyses
}

$frameIds = @($phaseCatalog | Where-Object { $_.lane -ceq 'frame' } | ForEach-Object { $_.id })
$otherIds = @($phaseCatalog | Where-Object { $_.lane -cne 'frame' } | ForEach-Object { $_.id })
try {
    Case 'phase catalog has six decision frame slots and no diagnostic runner' {
        Check ($frameIds.Count -eq 6 -and $otherIds.Count -eq 12) 'Phase catalog shape changed'
        Check (@($phaseCatalog | Where-Object { $_.lane -ceq 'frame' -and $_.runner -cne 'decision' }).Count -eq 0) 'Diagnostic slot reachable'
        Check (@($phaseCatalog | Where-Object { $_.lane -ceq 'frame' -and -not (Test-P4PhaseFrameSlot $_) }).Count -eq 0) 'Phase slot not recognized'
    }
    Case 'phase verdicts never reach the diagnostic branch' {
        foreach ($slot in @($phaseCatalog | Where-Object { $_.lane -ceq 'frame' })) {
            $expected = if ($slot.role -ceq 'baseline') { 'baseline-recorded' } else { 'pass,PERFORMANCE_FAIL' }
            Check ((@(Get-P4FrameContinuingVerdicts $slot) -join ',') -ceq $expected) "Continuing verdicts $($slot.id)"
            $analyzer = if ($slot.role -ceq 'baseline') { 'baseline-recorded,baseline-invalid' } else { 'pass,PERFORMANCE_FAIL' }
            Check ((@(Get-P4FrameAnalyzerVerdicts $slot) -join ',') -ceq $analyzer) "Analyzer verdicts $($slot.id)"
        }
    }

    # (a) no gross: all six frame slots and every memory/storage slot continue.
    Case '(a) record: no gross keeps every slot' {
        $record = Get-P4PhaseFrameChainRecord $phaseCatalog (Get-Analyses '' 6)
        Check ($null -eq $record.stop) 'Unexpected stop'
        Check (@($record.slots | Where-Object { $_.chain -cne 'continue' }).Count -eq 0) 'A slot stopped'
        Check ((@($record.groups.Values) -join ',') -ceq 'comparable,comparable,comparable') 'Groups not comparable'
    }
    Case '(a) chain: no gross runs all 18 slots' {
        $outcome = Invoke-PhaseChain 'a-no-gross' ''
        Check ($outcome.ran.Count -eq 18 -and $outcome.stopped.Count -eq 0) "Ran $($outcome.ran.Count), stopped $($outcome.stopped.Count)"
    }

    # (b) frame-2 (single candidate) gross: frame-3..6 stop, memory/storage 7..18 continue.
    Case '(b) record: candidate gross stops later frames only' {
        $record = Get-P4PhaseFrameChainRecord $phaseCatalog (Get-Analyses $frameIds[1] 2)
        Check ($record.stop.slot_id -ceq $frameIds[1] -and $record.stop.group_comparability -ceq 'comparable') 'Wrong stop'
        Check ($record.stop.gross_regression_basis.maximum_frame_overrun_ms -ceq '41.000000') 'Basis missing'
        $stopped = @($record.slots | Where-Object { $_.chain -ceq 'stopped' } | ForEach-Object { $_.slot_id })
        Check (($stopped -join ',') -ceq ($frameIds[2..5] -join ',')) "Stopped $($stopped -join ',')"
        Check ($record.groups.single -ceq 'comparable' -and $record.groups.layers16 -ceq 'not-collected' -and
            $record.groups.underlay -ceq 'not-collected') 'Wrong group record'
    }
    Case '(b) chain: frame-2 gross stops frame-3..6 and records the basis' {
        $outcome = Invoke-PhaseChain 'b-frame2' $frameIds[1]
        Check ((@($outcome.stopped.Keys) -join ',') -ceq ($frameIds[2..5] -join ',')) "Stopped $(@($outcome.stopped.Keys) -join ',')"
        Check ((@($outcome.ran) -join ',') -ceq ((@($frameIds[0..1]) + $otherIds) -join ',')) 'Memory/storage did not continue'
        foreach ($id in $frameIds[2..5]) {
            $stop = $outcome.stopped[$id]
            Check ($stop.slot_id -ceq $id -and $stop.stopped_by.slot_id -ceq $frameIds[1] -and
                $stop.stopped_by.gross_regression_basis.gross_overrun_threshold_ms -ceq '33.340000') "Stop record $id"
        }
    }

    # (c) frame-3 (layers16 baseline) gross: layers16 not comparable, frame-4..6 stop.
    Case '(c) record: baseline gross marks the group not comparable' {
        $record = Get-P4PhaseFrameChainRecord $phaseCatalog (Get-Analyses $frameIds[2] 3)
        Check ($record.stop.slot_id -ceq $frameIds[2] -and $record.stop.group_comparability -ceq 'not-comparable') 'Wrong stop'
        $stopped = @($record.slots | Where-Object { $_.chain -ceq 'stopped' } | ForEach-Object { $_.slot_id })
        Check (($stopped -join ',') -ceq ($frameIds[3..5] -join ',')) "Stopped $($stopped -join ',')"
        Check ($record.groups.single -ceq 'comparable' -and $record.groups.layers16 -ceq 'not-comparable' -and
            $record.groups.underlay -ceq 'not-collected') 'Wrong group record'
    }
    Case '(c) chain: frame-3 gross stops frame-4..6, memory/storage continue' {
        $outcome = Invoke-PhaseChain 'c-frame3' $frameIds[2]
        Check ((@($outcome.stopped.Keys) -join ',') -ceq ($frameIds[3..5] -join ',')) "Stopped $(@($outcome.stopped.Keys) -join ',')"
        Check ($outcome.ran.Count -eq 15) "Ran $($outcome.ran.Count)"
        Check ($outcome.stopped[$frameIds[3]].groups.layers16 -ceq 'not-comparable') 'Group not recorded as not comparable'
    }

    Case 'boundary: a stopped frame slot that was consumed is drift' {
        $root = Join-Path $OutputDirectory 'consumed-stopped'
        foreach ($slot in $phaseCatalog[0..1]) {
            Complete-Slot $root $slot (New-Analysis $slot $true ($slot.id -ceq $frameIds[1])) (Get-DefaultVerdict $slot)
        }
        [void][IO.Directory]::CreateDirectory((Join-Path $root $frameIds[2]))
        $failure = Get-ChainFailure { Assert-P4CompletedChain -Root $root -Catalog $phaseCatalog -SlotId $otherIds[0] -ManifestHash $hash }
        Check ($null -eq $failure.Exception.Data['p4_chain_stop'] -and "$failure" -match 'stopped by gross regression was consumed') "$failure"
    }
    Case 'boundary: a phase frame analysis without gross_regression is refused' {
        $root = Join-Path $OutputDirectory 'missing-gross'
        $analysis = New-Analysis $phaseCatalog[0] $true $false
        $analysis.Remove('gross_regression'); $analysis.Remove('gross_regression_basis')
        Complete-Slot $root $phaseCatalog[0] $analysis 'baseline-recorded'
        $failure = Get-ChainFailure { Assert-P4CompletedChain -Root $root -Catalog $phaseCatalog -SlotId $frameIds[1] -ManifestHash $hash }
        Check ("$failure" -match 'does not declare gross_regression') "$failure"
    }
    Case 'boundary: a baseline-invalid phase slot still stops every later slot' {
        $root = Join-Path $OutputDirectory 'baseline-invalid'
        Complete-Slot $root $phaseCatalog[0] (New-Analysis $phaseCatalog[0] $true $false) 'baseline-invalid'
        $failure = Get-ChainFailure { Assert-P4CompletedChain -Root $root -Catalog $phaseCatalog -SlotId $otherIds[0] -ManifestHash $hash }
        Check ($null -eq $failure.Exception.Data['p4_chain_stop'] -and "$failure" -match 'Prior slot stopped the experiment') "$failure"
    }
    Case 'wrapper records the gross stop as its own refusal reason' {
        $text = (Function-Ast 'Invoke-P4IndexedSlot').Extent.Text
        Check ($text.Contains("Data['p4_chain_stop']") -and $text.Contains("-ReasonCode 'gross-regression-stop'") -and
            $text.Contains("-ReasonCode 'completed-chain-drift'")) 'Refusal routing changed'
    }

    # (d) the historical v7 four-slot path is unchanged.
    Case '(d) v7 verdict tables unchanged' {
        $expected = @{ 'baseline-decision' = @('baseline-recorded', 'baseline-recorded,baseline-invalid')
            'candidate-decision' = @('pass,PERFORMANCE_FAIL', 'pass,PERFORMANCE_FAIL')
            'baseline-diagnostic' = @('inconclusive', 'inconclusive,PERFORMANCE_FAIL')
            'candidate-diagnostic' = @('inconclusive', 'inconclusive,PERFORMANCE_FAIL') }
        Check ($v7Frames.Count -eq 4) 'v7 frame catalog changed'
        foreach ($slot in $v7Frames) {
            $key = "$($slot.role)-$($slot.runner)"
            Check (-not (Test-P4PhaseFrameSlot $slot)) "v7 slot treated as phase: $($slot.id)"
            Check ((@(Get-P4FrameContinuingVerdicts $slot) -join ',') -ceq $expected[$key][0]) "Continuing $($slot.id)"
            Check ((@(Get-P4FrameAnalyzerVerdicts $slot) -join ',') -ceq $expected[$key][1]) "Analyzer $($slot.id)"
        }
    }
    Case '(d) v7 chain admits a completed run without gross fields' {
        $root = Join-Path $OutputDirectory 'v7-complete'
        foreach ($slot in $v7Frames[0..2]) { Complete-Slot $root $slot (New-Analysis $slot $false $false) (Get-DefaultVerdict $slot) }
        Assert-P4CompletedChain -Root $root -Catalog $v7Frames -SlotId $v7Frames[3].id -ManifestHash $hash
    }
    Case '(d) v7 diagnostic PERFORMANCE_FAIL still stops the next slot' {
        $root = Join-Path $OutputDirectory 'v7-diagnostic-fail'
        foreach ($slot in $v7Frames[0..1]) { Complete-Slot $root $slot (New-Analysis $slot $false $false) (Get-DefaultVerdict $slot) }
        Complete-Slot $root $v7Frames[2] (New-Analysis $v7Frames[2] $false $false) 'PERFORMANCE_FAIL'
        $failure = Get-ChainFailure { Assert-P4CompletedChain -Root $root -Catalog $v7Frames -SlotId $v7Frames[3].id -ManifestHash $hash }
        Check ($null -eq $failure.Exception.Data['p4_chain_stop'] -and "$failure" -match 'Prior slot stopped the experiment') "$failure"
    }
    Case '(d) v7 decision PERFORMANCE_FAIL still continues' {
        $root = Join-Path $OutputDirectory 'v7-decision-fail'
        Complete-Slot $root $v7Frames[0] (New-Analysis $v7Frames[0] $false $false) 'baseline-recorded'
        Complete-Slot $root $v7Frames[1] (New-Analysis $v7Frames[1] $false $false) 'PERFORMANCE_FAIL'
        Assert-P4CompletedChain -Root $root -Catalog $v7Frames -SlotId $v7Frames[2].id -ManifestHash $hash
    }
    Case '(d) the phase stop rule refuses the v7 catalog' {
        $failure = Get-ChainFailure { Get-P4PhaseFrameChainRecord $v7Frames @{} }
        Check ("$failure" -match 'phase decision frame slots only') "$failure"
    }
    $result.status = 'success'
} finally {
    $result.cases = $script:cases.ToArray()
    Save-Json (Join-Path $OutputDirectory 'result.json') $result
    $passed = @($script:cases | Where-Object { $_.passed }).Count
    Write-Output "status=$($result.status) passed=$passed/$($script:cases.Count)"
}

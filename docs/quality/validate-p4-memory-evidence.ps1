$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot 'measurements/p4-indexed-memory-analysis.ps1')

$commit = '0123456789abcdef0123456789abcdef01234567'
$profile = 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36'

function Assert-P4MemoryRejects {
    param([Parameter(Mandatory)][scriptblock]$Action, [Parameter(Mandatory)][string]$Case)
    try {
        & $Action | Out-Null
    } catch {
        return
    }
    throw "P4 memory validator accepted invalid case: $Case"
}

function New-P4MemoryLines {
    param(
        [string]$Family = 'candidate-common-indexed-history',
        [int]$RunIndex = 1,
        [long]$ProcessId = 1001,
        [long]$ProcessStart = 2001,
        [long]$RuntimeMax = 104857600,
        [long]$MemoryClass = 100,
        [long]$BaselineJava = 10485760,
        [long]$RetainedJava = 20971520,
        [long]$AfterCyclesJava = 20971520,
        [long]$BaselinePss = 10000,
        [long]$RetainedPss = 20000,
        [long]$AfterCyclesPss = 20000,
        [string]$BuildCommit = $commit,
        [string]$Schema,
        [switch]$DuplicateIdentity,
        [switch]$OmitReport
    )
    $contracts = @{
        'baseline-common-drawing-history' = @{
            schema = 'nene-pixel-p4-indexed-history-retention-v1'
            prefix = 'P4_HISTORY_RETENTION'
            facts = 'production_commit=2dd4e01e3bbe88967237cde4e28412d2962fd590 entries=64 changes=524288 logical_bytes=not_applicable_baseline_rgba owner_inventory=current_document:1,history:64'
        }
        'candidate-common-indexed-history' = @{
            schema = 'nene-pixel-p4-indexed-history-retention-v1'
            prefix = 'P4_HISTORY_RETENTION'
            facts = 'entries=64 changes=524288 logical_bytes=3147776 owner_inventory=current_document:1,history:64'
        }
        'candidate-palette-history' = @{
            schema = 'nene-pixel-p4-palette-history-retention-v1'
            prefix = 'P4_HISTORY_RETENTION'
            facts = 'entries=64 changes=524288 logical_bytes=3279360 owner_inventory=current_document:1,history:64'
        }
        'candidate-legacy-import' = @{
            schema = 'nene-pixel-p4-legacy-import-retention-v1'
            prefix = 'P4_LEGACY_IMPORT_RETENTION'
            facts = 'current_pixels=65536 legacy_pixels=65536 distinct_rgba=65536 destination_entries=256 owner_inventory=current_document:1,operation:1,source:1,selected_destination:1,reduction:1 fixture_inventory=destination_definitions:2,source_copy:0,preview_copy:0 old_projection_weak_refs_cleared=10'
        }
    }
    $contract = $contracts[$Family]
    if ([string]::IsNullOrEmpty($Schema)) { $Schema = $contract.schema }
    $identity = @(
        "INSTRUMENTATION_STATUS: p4MemoryFamily=$Family",
        "INSTRUMENTATION_STATUS: p4MemoryBuildCommit=$BuildCommit",
        "INSTRUMENTATION_STATUS: p4MemoryRunIndex=$RunIndex",
        "INSTRUMENTATION_STATUS: p4MemoryProcessId=$ProcessId",
        "INSTRUMENTATION_STATUS: p4MemoryProcessStartElapsedRealtimeMillis=$ProcessStart",
        "INSTRUMENTATION_STATUS: p4MemoryRuntimeMaxMemoryBytes=$RuntimeMax",
        "INSTRUMENTATION_STATUS: p4MemoryClassMebibytes=$MemoryClass",
        'INSTRUMENTATION_STATUS_CODE: 3'
    )
    $report = "$($contract.prefix) schema=$Schema family=$Family run=$RunIndex run_status=valid " +
        "measurement_build_commit=$BuildCommit profile=$profile $($contract.facts) " +
        "baseline_java_bytes=$BaselineJava retained_java_bytes=$RetainedJava " +
        "retained_java_delta_bytes=$($RetainedJava - $BaselineJava) after_cycles_java_bytes=$AfterCyclesJava " +
        "baseline_pss_kib=$BaselinePss retained_pss_kib=$RetainedPss " +
        "retained_pss_delta_kib=$($RetainedPss - $BaselinePss) after_cycles_pss_kib=$AfterCyclesPss"
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.AddRange([string[]]$identity)
    if ($DuplicateIdentity) { $lines.AddRange([string[]]$identity) }
    if (-not $OmitReport) {
        $lines.Add("INSTRUMENTATION_STATUS: p4MemoryReport=$report")
        $lines.Add('INSTRUMENTATION_STATUS_CODE: 4')
    }
    $lines.Add('INSTRUMENTATION_CODE: -1')
    return @($lines)
}

$validCount = 0
foreach ($family in @(
    'baseline-common-drawing-history',
    'candidate-common-indexed-history',
    'candidate-palette-history',
    'candidate-legacy-import'
)) {
    $result = Test-P4MemoryCapture -Lines (New-P4MemoryLines -Family $family) -Family $family -RunIndex 1 -BuildCommit $commit
    if ($result.verdict -cne 'pass') { throw "Expected valid synthetic family '$family'." }
    $validCount += 1
}
if ($validCount -ne 4) { throw 'Every fixed memory family must have one valid synthetic capture.' }

Assert-P4MemoryRejects {
    Test-P4MemoryCapture (New-P4MemoryLines -DuplicateIdentity) 'candidate-common-indexed-history' 1 $commit
} 'duplicate identity bundle'
Assert-P4MemoryRejects {
    Test-P4MemoryCapture (New-P4MemoryLines -OmitReport) 'candidate-common-indexed-history' 1 $commit
} 'missing report bundle'
Assert-P4MemoryRejects {
    Test-P4MemoryCapture ((New-P4MemoryLines) | Where-Object { $_ -notmatch '^INSTRUMENTATION_CODE:' }) `
        'candidate-common-indexed-history' 1 $commit
} 'missing instrumentation completion'
Assert-P4MemoryRejects {
    Test-P4MemoryCapture (New-P4MemoryLines -BaselineJava -1) 'candidate-common-indexed-history' 1 $commit
} 'negative checkpoint'
Assert-P4MemoryRejects {
    Test-P4MemoryCapture (New-P4MemoryLines -Schema wrong) 'candidate-common-indexed-history' 1 $commit
} 'wrong schema'
Assert-P4MemoryRejects {
    Test-P4MemoryCapture (New-P4MemoryLines -BuildCommit ('f' * 40)) 'candidate-common-indexed-history' 1 $commit
} 'wrong source identity'
Assert-P4MemoryRejects {
    Test-P4MemoryCapture ((New-P4MemoryLines) + 'FAILURES!!!') 'candidate-common-indexed-history' 1 $commit
} 'JUnit correctness failure'
$drifted = @(
    New-P4MemoryLines | ForEach-Object {
        if ($_ -match '^INSTRUMENTATION_STATUS: p4MemoryReport=') { "$_ unexpected=1" } else { $_ }
    }
)
Assert-P4MemoryRejects {
    Test-P4MemoryCapture $drifted 'candidate-common-indexed-history' 1 $commit
} 'unversioned report field drift'

$javaBoundary = Test-P4MemoryCapture `
    (New-P4MemoryLines -RetainedJava 52428800 -AfterCyclesJava 52428800) `
    'candidate-common-indexed-history' 1 $commit
if ($javaBoundary.verdict -cne 'pass') { throw 'The retained-Java boundary must pass.' }
$javaMiss = Test-P4MemoryCapture `
    (New-P4MemoryLines -RetainedJava 52428801 -AfterCyclesJava 52428801) `
    'candidate-common-indexed-history' 1 $commit
if ($javaMiss.verdict -cne 'PERFORMANCE_FAIL') { throw 'A retained-Java miss must be PERFORMANCE_FAIL.' }

$pssBoundary = Test-P4MemoryCapture `
    (New-P4MemoryLines -RetainedPss 71440 -AfterCyclesPss 71440) `
    'candidate-common-indexed-history' 1 $commit
if ($pssBoundary.verdict -cne 'pass') { throw 'The individual PSS boundary must pass.' }
$pssMiss = Test-P4MemoryCapture `
    (New-P4MemoryLines -RetainedPss 71441 -AfterCyclesPss 71441) `
    'candidate-common-indexed-history' 1 $commit
if ($pssMiss.verdict -cne 'PERFORMANCE_FAIL') { throw 'An individual PSS miss must be PERFORMANCE_FAIL.' }

$growthBoundary = Test-P4MemoryCapture `
    (New-P4MemoryLines -AfterCyclesJava (20971520 + 1048576)) `
    'candidate-common-indexed-history' 1 $commit
if ($growthBoundary.verdict -cne 'pass') { throw 'The post-cycle growth boundary must pass.' }
$growthMiss = Test-P4MemoryCapture `
    (New-P4MemoryLines -AfterCyclesJava (20971520 + 1048577)) `
    'candidate-common-indexed-history' 1 $commit
if ($growthMiss.verdict -cne 'PERFORMANCE_FAIL') { throw 'A post-cycle growth miss must be PERFORMANCE_FAIL.' }

$prior = @()
foreach ($run in 1..4) {
    $delta = @(48000, 50000, 52000, 54000)[$run - 1]
    $lines = New-P4MemoryLines -RunIndex $run -ProcessId (3000 + $run) -ProcessStart (4000 + $run) `
        -RetainedPss (10000 + $delta) -AfterCyclesPss (10000 + $delta)
    $prior += Test-P4MemoryCapture $lines 'candidate-common-indexed-history' $run $commit $prior
}
$fifthLines = New-P4MemoryLines -RunIndex 5 -ProcessId 3005 -ProcessStart 4005 `
    -RetainedPss 66000 -AfterCyclesPss 66000
$medianMiss = Test-P4MemoryCapture $fifthLines 'candidate-common-indexed-history' 5 $commit $prior
if ($medianMiss.family_median_pss_delta_kib -ne 52000 -or $medianMiss.verdict -cne 'PERFORMANCE_FAIL') {
    throw 'The fifth-run family median must enforce the 50%-of-memory-class gate.'
}

$boundaryPrior = @()
foreach ($run in 1..4) {
    $delta = @(48000, 50000, 51200, 54000)[$run - 1]
    $lines = New-P4MemoryLines -RunIndex $run -ProcessId (5000 + $run) -ProcessStart (6000 + $run) `
        -RetainedPss (10000 + $delta) -AfterCyclesPss (10000 + $delta)
    $boundaryPrior += Test-P4MemoryCapture $lines 'candidate-common-indexed-history' $run $commit $boundaryPrior
}
$medianBoundaryLines = New-P4MemoryLines -RunIndex 5 -ProcessId 5005 -ProcessStart 6005 `
    -RetainedPss 66000 -AfterCyclesPss 66000
$medianBoundary = Test-P4MemoryCapture `
    $medianBoundaryLines 'candidate-common-indexed-history' 5 $commit $boundaryPrior
if ($medianBoundary.family_median_pss_delta_kib -ne 51200 -or $medianBoundary.verdict -cne 'pass') {
    throw 'The exact fifth-run family median boundary must pass.'
}

Assert-P4MemoryRejects {
    Test-P4MemoryCapture `
        (New-P4MemoryLines -RunIndex 2 -ProcessId $prior[0].process_id -ProcessStart $prior[0].process_start_elapsed_realtime_ms) `
        'candidate-common-indexed-history' 2 $commit @($prior[0])
} 'reused process identity pair'

Write-Output 'P4 memory evidence analyzer contract: PASS'

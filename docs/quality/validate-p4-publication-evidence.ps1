[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-publication-analysis.ps1')

function Assert-P4PublicationRejects {
    param([Parameter(Mandatory = $true)][scriptblock]$Action, [Parameter(Mandatory = $true)][string]$Case)
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw "Negative publication fixture was accepted: $Case" }
}

function New-P4SyntheticPublicationCapture {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('baseline', 'candidate')][string]$Role,
        [Parameter(Mandatory = $true)][long]$MaximumNanos
    )
    $schema = 'nene-pixel-p4-indexed-publication-device-v1'
    $lines = [Collections.Generic.List[string]]::new()
    $lines.Add('schema,role,group,index,kind,elapsed_ns,generation,outcome')
    $generation = [long]1
    foreach ($contract in @(Get-P4PublicationGroups $Role)) {
        $sampleValues = [long[]](0..19 | ForEach-Object {
                if ($contract.name -match '_max$' -and $_ -eq 19) { $MaximumNanos } else { 1000000 + $_ }
            })
        foreach ($index in 0..4) {
            $lines.Add("$schema,$Role,$($contract.name),$index,warmup,${generation}000,$generation,written")
            $generation++
        }
        foreach ($index in 0..19) {
            $lines.Add("$schema,$Role,$($contract.name),$index,sample,$($sampleValues[$index]),$generation,written")
            $generation++
        }
        $minimumIndex = 0
        $maximumIndex = 0
        for ($index = 1; $index -lt $sampleValues.Count; $index++) {
            if ($sampleValues[$index] -lt $sampleValues[$minimumIndex]) { $minimumIndex = $index }
            if ($sampleValues[$index] -gt $sampleValues[$maximumIndex]) { $maximumIndex = $index }
        }
        $sampleStartGeneration = $generation - 20
        $lines.Add(
            "$schema,$Role,$($contract.name),$minimumIndex,summary_min," +
                "$($sampleValues[$minimumIndex]),$($sampleStartGeneration + $minimumIndex),written"
        )
        $lines.Add(
            "$schema,$Role,$($contract.name),$maximumIndex,summary_max," +
                "$($sampleValues[$maximumIndex]),$($sampleStartGeneration + $maximumIndex),written"
        )
    }
    return [pscustomobject]@{ Lines = $lines.ToArray(); StatusLines = @('complete') }
}

function Set-P4PublicationField {
    param([Parameter(Mandatory = $true)][string[]]$Lines, [Parameter(Mandatory = $true)][int]$Index,
        [Parameter(Mandatory = $true)][int]$Field, [Parameter(Mandatory = $true)][string]$Value)
    $copy = [string[]]$Lines.Clone()
    $parts = @($copy[$Index] -split ',')
    $parts[$Field] = $Value
    $copy[$Index] = $parts -join ','
    return $copy
}

$candidate = New-P4SyntheticPublicationCapture candidate 200000000
$candidateResult = Test-P4PublicationCapture $candidate.Lines $candidate.StatusLines candidate
if ($candidateResult.schema -cne 'nene-pixel-p4-indexed-publication-device-v1' -or
    $candidateResult.role -cne 'candidate' -or $candidateResult.verdict -cne 'valid-constants-retained' -or
    $candidateResult.sample_count -ne 40 -or $candidateResult.groups.candidate_v2_max.structural_byte_count -ne 66628) {
    throw 'Valid candidate publication result identity drifted.'
}

$baseline = New-P4SyntheticPublicationCapture baseline 251000000
$baselineResult = Test-P4PublicationCapture $baseline.Lines $baseline.StatusLines baseline
if ($baselineResult.verdict -cne 'valid-constants-retained' -or
    $baselineResult.groups.baseline_v1_max.structural_byte_count -ne 262209) {
    throw 'Baseline publication must retain constants without a candidate decision.'
}

$boundary = New-P4SyntheticPublicationCapture candidate 250000000
if ((Test-P4PublicationCapture $boundary.Lines $boundary.StatusLines candidate).verdict -cne
    'valid-constants-retained') {
    throw 'The 250 ms candidate boundary must retain constants.'
}
$aboveBoundary = New-P4SyntheticPublicationCapture candidate 250000001
if ((Test-P4PublicationCapture $aboveBoundary.Lines $aboveBoundary.StatusLines candidate).verdict -cne
    'valid-constants-revision-required') {
    throw 'A candidate maximum above 250 ms must require constant revision.'
}
$aboveBoundaryResult = Test-P4PublicationCapture $aboveBoundary.Lines $aboveBoundary.StatusLines candidate
if ($aboveBoundaryResult.rederived_quiet_ms -ne 1500 -or $aboveBoundaryResult.rederived_latency_cap_ms -ne 5500) {
    throw 'Candidate constant re-derivation did not use the exact 4x/20x 500 ms rule.'
}

$truncated = [string[]]$candidate.Lines[0..53]
Assert-P4PublicationRejects { Test-P4PublicationCapture $truncated $candidate.StatusLines candidate } 'truncated rows'
Assert-P4PublicationRejects {
    Test-P4PublicationCapture ($candidate.Lines + 'extra') $candidate.StatusLines candidate
} 'extra row'
Assert-P4PublicationRejects { Test-P4PublicationCapture $candidate.Lines @('invalid') candidate } 'incomplete status'
Assert-P4PublicationRejects {
    Test-P4PublicationCapture $candidate.Lines @('complete', 'complete') candidate
} 'duplicate status'

$reordered = [string[]]$candidate.Lines.Clone()
$reordered[1], $reordered[2] = $reordered[2], $reordered[1]
Assert-P4PublicationRejects { Test-P4PublicationCapture $reordered $candidate.StatusLines candidate } 'reordered rows'
Assert-P4PublicationRejects {
    Test-P4PublicationCapture (Set-P4PublicationField $candidate.Lines 6 5 '5000000001') `
        $candidate.StatusLines candidate
} 'five-second timeout bound'
Assert-P4PublicationRejects {
    Test-P4PublicationCapture (Set-P4PublicationField $candidate.Lines 54 5 '1000001') $candidate.StatusLines candidate
} 'summary mismatch'
Assert-P4PublicationRejects {
    Test-P4PublicationCapture (Set-P4PublicationField $candidate.Lines 1 1 'baseline') $candidate.StatusLines candidate
} 'role mismatch'
Assert-P4PublicationRejects {
    Test-P4PublicationCapture (Set-P4PublicationField $candidate.Lines 1 7 'failed') $candidate.StatusLines candidate
} 'outcome mismatch'
Assert-P4PublicationRejects {
    Test-P4PublicationCapture (Set-P4PublicationField $candidate.Lines 6 6 '1') $candidate.StatusLines candidate
} 'generation mismatch'
Assert-P4PublicationRejects {
    Test-P4PublicationCapture (Set-P4PublicationField $candidate.Lines 6 5 'NaN') $candidate.StatusLines candidate
} 'non-finite elapsed value'
Assert-P4PublicationRejects {
    Test-P4PublicationCapture (Set-P4PublicationField $candidate.Lines 6 5 '0') $candidate.StatusLines candidate
} 'non-positive elapsed value'

Write-Output 'P4_PUBLICATION_NO_DEVICE_VALIDATION=pass'
Write-Output (
    'CASES=valid-baseline,valid-candidate,structural-bytes,threshold-boundary,revision-boundary,' +
        'truncated,extra,status,order,timeout,summary,role,outcome,generation,nonfinite,nonpositive'
)

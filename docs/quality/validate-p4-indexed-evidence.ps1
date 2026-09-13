[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1')
. (Join-Path $PSScriptRoot 'measurements/analyze-p4-indexed-slot.ps1') -ManifestPath fixture -SlotId fixture -OutputDirectory fixture

function Assert-P4TestRejects {
    param([scriptblock]$Action, [string]$Case)
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw "Negative fixture was accepted: $Case" }
}

function New-P4SyntheticHostRows {
    param([string]$Runner, [string]$Role)
    $schema = switch ($Runner) { 'project' { 'project-format' }; 'recovery' { 'recovery-record' }; 'legacy' { 'legacy-import' } }
    $lines = [Collections.Generic.List[string]]::new()
    foreach ($line in @("schema,nene-pixel-p4-$schema-host-v1", "role,$Role", 'java_version,synthetic',
        'java_vm,synthetic', 'os,synthetic', 'warmups,5', 'samples_per_group,20', 'group,sample,latency_nanos')) { $lines.Add($line) }
    $groups = @(Get-P4HostGroups $Runner $Role)
    foreach ($group in $groups) { foreach ($i in 1..20) { $lines.Add("$group,$i,$(1000 + $i)") } }
    foreach ($group in $groups) { $lines.Add("summary_min,$group,1001"); $lines.Add("summary_max,$group,1020") }
    return $lines.ToArray()
}

$catalog = @(Get-P4SlotCatalog)
if ($catalog.Count -ne 33 -or $catalog[0].id -cne 'host-project-baseline' -or
    $catalog[28].id -cne 'publication-candidate' -or $catalog[32].id -cne 'frame-4-baseline-decision') {
    throw 'Fixed 33-slot operation catalog is incorrect.'
}
if (@($catalog | Where-Object lane -eq 'memory').Count -ne 20 -or
    @($catalog | Where-Object { $_.timeout_seconds -eq 600 }).Count -ne 2) { throw 'Finite lane budget drift.' }
foreach ($runner in @('project', 'recovery', 'legacy')) {
    $roles = if ($runner -eq 'legacy') { @('candidate') } else { @('baseline', 'candidate') }
    foreach ($role in $roles) {
        $rows = @(New-P4SyntheticHostRows $runner $role)
        $result = Test-P4HostCapture $rows $runner $role
        if ($result.verdict -cne 'valid-descriptive') { throw 'Host observations cannot yield a product performance pass.' }
        $incomplete = $rows[0..($rows.Count - 2)]
        Assert-P4TestRejects { Test-P4HostCapture $incomplete $runner $role } 'truncated host'
        $duplicate = [string[]]$rows.Clone(); $duplicate[9] = $duplicate[8]
        Assert-P4TestRejects { Test-P4HostCapture $duplicate $runner $role } 'duplicate sample'
        $reordered = [string[]]$rows.Clone(); $reordered[8] = $rows[9]; $reordered[9] = $rows[8]
        Assert-P4TestRejects { Test-P4HostCapture $reordered $runner $role } 'reordered sample'
        $anomaly = [string[]]$rows.Clone(); $anomaly[8] = $anomaly[8] -replace ',1001$', ',1000000001'
        Assert-P4TestRejects { Test-P4HostCapture $anomaly $runner $role } 'completed gross anomaly'
        $negative = [string[]]$rows.Clone(); $negative[8] = $negative[8] -replace ',1001$', ',-1'
        Assert-P4TestRejects { Test-P4HostCapture $negative $runner $role } 'negative duration'
        $nan = [string[]]$rows.Clone(); $nan[8] = $nan[8] -replace ',1001$', ',NaN'
        Assert-P4TestRejects { Test-P4HostCapture $nan $runner $role } 'nonfinite duration'
        $wrongSummary = [string[]]$rows.Clone(); $wrongSummary[-1] = $wrongSummary[-1] -replace ',1020$', ',1019'
        Assert-P4TestRejects { Test-P4HostCapture $wrongSummary $runner $role } 'fabricated summary'
        $wrongRole = [string[]]$rows.Clone(); $wrongRole[1] = 'role,other'
        Assert-P4TestRejects { Test-P4HostCapture $wrongRole $runner $role } 'role mismatch'
        $extra = $rows + 'unused,1,1000'
        Assert-P4TestRejects { Test-P4HostCapture $extra $runner $role } 'extra row'
    }
}
Assert-P4TestRejects { Get-P4HostGroups legacy baseline } 'fabricated baseline import'
Assert-P4TestRejects { Assert-P4RequiredValue @{ valid = 'yes'; nested = @('UNSET_PREFLIGHT') } root } 'nested unset identity'
Assert-P4TestRejects { Assert-P4ManifestContract @{ schema = $script:P4ManifestSchema } } 'missing manifest fields'
Assert-P4TestRejects { Assert-P4RequiredValue @{} empty } 'empty inventory'
Assert-P4TestRejects { Read-P4PositiveInt64 '9223372036854775808' } 'overflow'

$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('nene-p4-contract-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$source = Join-Path $testRoot 'immutable.txt'
Write-NewInvocationFile $source 'source'
$record = [ordered]@{ relative_path = 'fixture/immutable.txt'; path = $source; byte_count = 6; sha256 = Get-FileSha256 $source }
$hash = Get-P4FileInventoryHash @($record)
if ($hash -cnotmatch '^[0-9a-f]{64}$') { throw 'Invalid inventory digest.' }
Assert-P4TestRejects { Write-NewInvocationFile $source 'replacement' } 'exclusive output collision'
if ([IO.File]::ReadAllText($source) -cne 'source') { throw 'Collision damaged existing evidence.' }
Assert-P4TestRejects { Get-P4FileInventoryHash @($record, $record) } 'duplicate artifact identity'
$escaping = [ordered]@{}; foreach ($key in $record.Keys) { $escaping[$key] = $record[$key] }; $escaping.relative_path = '../escape'
Assert-P4TestRejects { Get-P4FileInventoryHash @($escaping) } 'escaping aggregate path'
[IO.File]::WriteAllText($source, 'edited')
Assert-P4TestRejects { Assert-P4FileRecord $record fixture } 'same-length artifact tamper'
Write-Output 'P4_NO_DEVICE_CONTRACT_VALIDATION=pass'
Write-Output 'CASES=fixed-budget,five-host-populations,truncation,duplicate,reorder,gross-anomaly,negative,NaN,summary,role,extra-row,unfabricated-baseline,unset,missing,empty,overflow,exclusive-output,duplicate-artifact,path-escape,tamper'
Write-Output "SYNTHETIC_FIXTURE_DIRECTORY=$testRoot"

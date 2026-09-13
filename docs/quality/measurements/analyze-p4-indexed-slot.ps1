[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ManifestPath,
    [Parameter(Mandatory = $true)][string]$SlotId,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'p4-indexed-preflight.ps1')
. (Join-Path $PSScriptRoot 'p4-indexed-publication-analysis.ps1')
. (Join-Path $PSScriptRoot 'p4-indexed-command-analysis.ps1')
. (Join-Path $PSScriptRoot 'p4-indexed-memory-analysis.ps1')

function Get-P4HostGroups {
    param([string]$Runner, [string]$Role)
    switch ("$Runner-$Role") {
        'project-baseline' { return @('v1_max_encode', 'v1_max_decode') }
        'project-candidate' { return @('v1_max_exact_original_encode', 'v1_max_decode_legacy',
            'v2_min_encode', 'v2_min_decode', 'v2_max_encode', 'v2_max_decode') }
        'recovery-baseline' { return @('v1_retired_encode', 'v1_retired_decode',
            'v1_max_candidate_encode', 'v1_max_candidate_decode', 'v1_retirement_publish') }
        'recovery-candidate' { return @('v2_retired_encode', 'v2_retired_decode', 'v1_max_candidate_decode_legacy',
            'v2_max_candidate_encode', 'v2_max_candidate_decode_current', 'v2_max_candidate_publish') }
        'legacy-candidate' { return @('classify_256_lossless', 'classify_257_conversion_required',
            'classify_65536_conversion_required', 'reduce_65536_to_256') }
        default { throw 'Unknown host role/runner.' }
    }
}

function Read-P4PositiveInt64 {
    param([string]$Text, [bool]$AllowZero = $false)
    $number = 0L
    if ($Text -cnotmatch '^\d+$' -or -not [long]::TryParse($Text, [ref]$number) -or
        $number -lt $(if ($AllowZero) { 0 } else { 1 })) { throw "Invalid numeric evidence: $Text" }
    return $number
}

function Test-P4HostCapture {
    param([string[]]$Lines, [string]$Runner, [string]$Role)
    $groups = @(Get-P4HostGroups $Runner $Role)
    $schema = switch ($Runner) {
        'project' { 'nene-pixel-p4-project-format-host-v1' }
        'recovery' { 'nene-pixel-p4-recovery-record-host-v1' }
        'legacy' { 'nene-pixel-p4-legacy-import-host-v1' }
    }
    if ($Lines.Count -ne (8 + 22 * $groups.Count)) { throw 'Incomplete or excess host population.' }
    $metadataNames = @('schema', 'role', 'java_version', 'java_vm', 'os', 'warmups', 'samples_per_group')
    $metadata = [ordered]@{}
    for ($i = 0; $i -lt $metadataNames.Count; $i++) {
        $parts = $Lines[$i] -split ',', 2
        if ($parts.Count -ne 2 -or $parts[0] -cne $metadataNames[$i] -or [string]::IsNullOrWhiteSpace($parts[1])) {
            throw 'Host metadata is missing or reordered.'
        }
        $metadata[$parts[0]] = $parts[1]
    }
    if ($metadata.schema -cne $schema -or $metadata.role -cne $Role -or $metadata.warmups -cne '5' -or
        $metadata.samples_per_group -cne '20' -or $Lines[7] -cne 'group,sample,latency_nanos') {
        throw 'Host schema/role/budget mismatch.'
    }
    $observed = [ordered]@{}
    $offset = 8
    foreach ($group in $groups) {
        $times = [Collections.Generic.List[long]]::new()
        foreach ($sample in 1..20) {
            $parts = $Lines[$offset] -split ','
            $offset++
            if ($parts.Count -ne 3 -or $parts[0] -cne $group -or $parts[1] -cne [string]$sample) {
                throw 'Host rows are missing, duplicated or reordered.'
            }
            $latency = Read-P4PositiveInt64 $parts[2]
            if ($latency -gt 1000000000L) { throw 'Host completed-operation gross anomaly exceeded 1 second.' }
            $times.Add($latency)
        }
        $observed[$group] = [ordered]@{ sample_count = 20; minimum_nanos = ($times | Measure-Object -Minimum).Minimum;
            maximum_nanos = ($times | Measure-Object -Maximum).Maximum }
    }
    foreach ($group in $groups) {
        foreach ($kind in @('min', 'max')) {
            $parts = $Lines[$offset] -split ','
            $offset++
            $key = if ($kind -eq 'min') { 'minimum_nanos' } else { 'maximum_nanos' }
            if ($parts.Count -ne 3 -or $parts[0] -cne "summary_$kind" -or $parts[1] -cne $group -or
                (Read-P4PositiveInt64 $parts[2]) -ne $observed[$group][$key]) { throw 'Host summary disagrees with full raw population.' }
        }
    }
    return [ordered]@{ verdict = 'valid-descriptive'; schema = $schema; role = $Role;
        sample_count = 20 * $groups.Count; groups = $observed; metadata = $metadata }
}

function Invoke-P4SlotAnalysis {
    param([string]$ManifestPath, [string]$SlotId, [string]$OutputDirectory)
    $manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json -AsHashtable
    Assert-P4ManifestContract $manifest
    $slot = @(Get-P4SlotCatalog | Where-Object { $_.id -ceq $SlotId })
    if ($slot.Count -ne 1) { throw 'Unknown slot.' }
    $slot = $slot[0]
    switch ($slot.lane) {
        'host' {
            $stem = switch ($slot.runner) { 'project' { 'project-format' }; 'recovery' { 'recovery-record' }; 'legacy' { 'legacy-import' } }
            $path = Join-Path $OutputDirectory "p4-$stem-host-$($slot.role).csv"
            $result = Test-P4HostCapture @(Get-Content -LiteralPath $path) $slot.runner $slot.role
            $result.capture_sha256 = Get-FileSha256 $path
        }
        'publication' {
            $path = Join-Path $OutputDirectory "p4-indexed-publication-device-$($slot.role)-v1.csv"
            $statusPath = Join-Path $OutputDirectory "p4-indexed-publication-device-$($slot.role)-v1.status"
            $result = Test-P4PublicationCapture -Lines @(Get-Content -LiteralPath $path) `
                -StatusLines @(Get-Content -LiteralPath $statusPath) -Role $slot.role
            $result.capture_sha256 = Get-FileSha256 $path
            $result.status_sha256 = Get-FileSha256 $statusPath
        }
        'command' {
            $path = Join-Path $OutputDirectory "p4-indexed-command-$($slot.role)-run-01.csv"
            $result = Test-P4CommandCapture -Lines @(Get-Content -LiteralPath $path) -Role $slot.role `
                -BuildCommit $manifest.roles[$slot.role].build_commit
            $result.capture_sha256 = Get-FileSha256 $path
        }
        'memory' {
            $family = switch ("$($slot.role)-$($slot.runner)") {
                'baseline-common' { 'baseline-common-drawing-history' }
                'candidate-common' { 'candidate-common-indexed-history' }
                'candidate-palette' { 'candidate-palette-history' }
                'candidate-import' { 'candidate-legacy-import' }
            }
            $prior = @()
            foreach ($earlier in @(Get-P4SlotCatalog)) {
                if ($earlier.id -ceq $SlotId) { break }
                if ($earlier.lane -eq 'memory' -and $earlier.role -ceq $slot.role -and $earlier.runner -ceq $slot.runner) {
                    $prior += Get-Content -LiteralPath (Join-Path $manifest.output_directory "$($earlier.id)/completed.json") `
                        -Raw | ConvertFrom-Json -AsHashtable
                }
            }
            $path = Join-Path $OutputDirectory 'instrumentation.log'
            $result = Test-P4MemoryCapture -Lines @(Get-Content -LiteralPath $path) -Family $family `
                -RunIndex $slot.run -BuildCommit $manifest.roles[$slot.role].build_commit -PriorRuns $prior
            $result = $result | ConvertTo-Json -Depth 10 | ConvertFrom-Json -AsHashtable
            $result.capture_sha256 = Get-FileSha256 $path
        }
        default { throw 'This lane analyzer is not yet implemented; acceptance is blocked.' }
    }
    $result.protocol_id = $script:P4ProtocolId
    $result.slot_id = $SlotId
    $result.preflight_sha256 = Get-FileSha256 $ManifestPath
    $result.created_utc = [datetime]::UtcNow.ToString('o')
    Write-NewInvocationFile (Join-Path $OutputDirectory 'analysis.json') ($result | ConvertTo-Json -Depth 15)
}

if ($MyInvocation.InvocationName -ne '.') { Invoke-P4SlotAnalysis $ManifestPath $SlotId $OutputDirectory }

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ManifestPath,
    [Parameter(Mandatory = $true)][string]$SlotId,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    # Frame decision candidate only: the decision baseline slot's analysis.json. When omitted it is
    # resolved from the same experiment root; any other slot refuses it.
    [string]$BaselineAnalysisPath = ''
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'p4-indexed-preflight.ps1')
. (Join-Path $PSScriptRoot 'p4-indexed-publication-analysis.ps1')
. (Join-Path $PSScriptRoot 'p4-indexed-command-analysis.ps1')
. (Join-Path $PSScriptRoot 'p4-indexed-memory-analysis.ps1')
. (Join-Path $PSScriptRoot 'p4-indexed-frame-analysis.ps1')

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
        foreach ($sample in 0..19) {
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

function Resolve-P4FrameBaselineAnalysisPath {
    <#
        Lane 3 (#120): only the decision candidate is judged against the decision baseline. The
        baseline reference is that slot's wrapper analysis.json under the same experiment root; an
        explicit path is accepted only for the decision candidate. Refusal is exit 2, like the
        analyzer's own argument check.
    #>
    param($Manifest, $Slot, [string]$BaselineAnalysisPath)
    $isDecisionCandidate = $Slot.lane -ceq 'frame' -and $Slot.runner -ceq 'decision' -and $Slot.role -ceq 'candidate'
    if (-not $isDecisionCandidate) {
        if (-not [string]::IsNullOrEmpty($BaselineAnalysisPath)) {
            [Console]::Error.WriteLine('-BaselineAnalysisPath is accepted only for the frame decision candidate.')
            exit 2
        }
        return ''
    }
    if ([string]::IsNullOrEmpty($BaselineAnalysisPath)) {
        $baselineSlot = @(Get-P4FrameSlotCatalog | Where-Object { $_.runner -ceq 'decision' -and $_.role -ceq 'baseline' })
        $BaselineAnalysisPath = Join-Path (Join-Path ([string]$Manifest.output_directory) $baselineSlot[0].id) 'analysis.json'
    }
    if (-not (Test-Path -LiteralPath $BaselineAnalysisPath -PathType Leaf)) {
        [Console]::Error.WriteLine("The decision baseline analysis is missing: $BaselineAnalysisPath")
        exit 2
    }
    return [IO.Path]::GetFullPath($BaselineAnalysisPath)
}

function Invoke-P4SlotAnalysis {
    param([string]$ManifestPath, [string]$SlotId, [string]$OutputDirectory, [string]$BaselineAnalysisPath = '')
    $manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json -AsHashtable
    Assert-P4ManifestContract $manifest
    # Frame slots belong to Issue #120's own four-slot catalog, outside Issue #106's order.
    $slot = @(@(Get-P4SlotCatalog) + @(Get-P4FrameSlotCatalog) | Where-Object { $_.id -ceq $SlotId })
    if ($slot.Count -ne 1) { throw 'Unknown slot.' }
    $slot = $slot[0]
    $BaselineAnalysisPath = Resolve-P4FrameBaselineAnalysisPath -Manifest $manifest -Slot $slot `
        -BaselineAnalysisPath $BaselineAnalysisPath
    switch ($slot.lane) {
        'host' {
            $stem = switch ($slot.runner) { 'project' { 'project-format' }; 'recovery' { 'recovery-record' }; 'legacy' { 'legacy-import' } }
            $path = Join-Path $OutputDirectory "p4-$stem-host-$($slot.role).csv"
            $result = Test-P4HostCapture @(Get-Content -LiteralPath $path) $slot.runner $slot.role
            $result.capture_sha256 = Get-FileSha256 $path
            Assert-P4HostClasspathAgreement -OutputDirectory $OutputDirectory -Role $slot.role -Manifest $manifest
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
        'frame' {
            $slotRecordPath = Join-Path $OutputDirectory 'frame-slot.json'
            $slotRecord = Get-Content -Raw -LiteralPath $slotRecordPath | ConvertFrom-Json
            if ($slotRecord.schema -cne 'nene-pixel-p4-frame-slot-v1') { throw 'Wrong frame slot record schema.' }
            $frameExperimentRoot = [IO.Path]::GetFullPath([string]$manifest.frame_experiment.directory)
            $frameSlotRoot = [IO.Path]::GetFullPath([string]$slotRecord.slot_directory)
            if (-not $frameSlotRoot.StartsWith(
                    ($frameExperimentRoot.TrimEnd([char]'\', [char]'/') + [IO.Path]::DirectorySeparatorChar),
                    [StringComparison]::OrdinalIgnoreCase)) {
                throw 'The frame slot directory is outside the declared frame experiment directory.'
            }
            # The window family draws on the 256 by 256 surface, so it reuses the canvas256 bounds.
            $bounds = [ordered]@{
                canvas16_tap = [string]$manifest.device["$($slot.role)_canvas16_bounds"]
                canvas256_repeated_diagonal = [string]$manifest.device["$($slot.role)_canvas256_bounds"]
                canvas256_repeated_diagonal_window_x2 = [string]$manifest.device["$($slot.role)_canvas256_bounds"]
            }
            $result = Test-P4FrameCapture -SlotDirectory ([string]$slotRecord.slot_directory) -Role $slot.role `
                -Runner $slot.runner -SequenceIndex ([int]$slot.run) `
                -BuildCommit $manifest.roles[$slot.role].build_commit `
                -ExpectedApkSha256 ([string]$manifest.roles[$slot.role].artifacts.app_release_like.sha256) `
                -ExpectedBounds $bounds -ExperimentId ([string]$manifest.experiment_id) `
                -BaselineAnalysisPath $BaselineAnalysisPath
            $recorded = @($slotRecord.files | ForEach-Object { "$($_.relative_path)`t$($_.sha256)" })
            $observed = @($result.frame_files | ForEach-Object { "$($_.relative_path)`t$($_.sha256)" })
            if (@(Compare-Object $recorded $observed).Count -ne 0) {
                throw 'The frame slot inventory drifted from the sealed collector record.'
            }
            $result.capture_sha256 = Get-FileSha256 $slotRecordPath
        }
        default { throw 'This lane analyzer is not yet implemented; acceptance is blocked.' }
    }
    # The wrapper seals the slot before analysis (S3 step 7b, then 8). Binding the seal hash into the
    # analysis lets the completed chain prove that the analyzed bytes are the sealed bytes.
    $sealPath = Join-Path $OutputDirectory 'capture-seal.json'
    if (-not (Test-Path -LiteralPath $sealPath -PathType Leaf)) {
        throw 'Analysis requires the capture seal written before the analyzer runs.'
    }
    $result.capture_seal_sha256 = Get-FileSha256 $sealPath
    $result.protocol_id = $script:P4ProtocolId
    $result.slot_id = $SlotId
    $result.preflight_sha256 = Get-FileSha256 $ManifestPath
    $result.created_utc = [datetime]::UtcNow.ToString('o')
    Write-NewInvocationFile (Join-Path $OutputDirectory 'analysis.json') ($result | ConvertTo-Json -Depth 15)
}

if ($MyInvocation.InvocationName -ne '.') { Invoke-P4SlotAnalysis $ManifestPath $SlotId $OutputDirectory $BaselineAnalysisPath }

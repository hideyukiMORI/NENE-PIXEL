Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../baseline-profile-evidence.ps1')
. (Join-Path $PSScriptRoot 'p4-indexed-preflight.ps1')
# Lane 3 revision 2026-09-23 (#120): baseline-relative verdict.

$script:P4FrameSchema = 'nene-pixel-p4-indexed-actual-app-frame-v8'
$script:P4FrameExperimentSchema = 'nene-pixel-p4-indexed-frame-experiment-v5'
$script:P4FrameVerdictRule = 'lane3-2026-09-23-relative'
$script:P4FrameGeometryId = 'initial-fit-centered-v1'
# Decision families; diagnostic slots may additionally declare the third family below.
$script:P4FrameWorkloadOrder = @('canvas16_tap', 'canvas256_repeated_diagonal')
$script:P4FrameDiagnosticWorkloadOrder = @('canvas16_tap', 'canvas256_repeated_diagonal',
    'canvas256_repeated_diagonal_window_x2')
$script:P4FrameWarmups = 5
# The v6 absolute overrun gates (p95 <= 0.0 ms, p99 <= 16.67 ms) moved to the M5 performance budget.
$script:P4FrameRelativeP95ToleranceMs = 1.0
$script:P4FrameRelativeP99ToleranceMs = 2.0
$script:P4FrameInputP95Gate = 33.33
$script:P4FrameGrossOverrun = 33.34
$script:P4FrameGrossInput = 100.0

function Get-P4FrameExactMember {
    param([AllowNull()]$Value, [string]$Name)
    if ($null -eq $Value) { throw "Frame record is null (required '$Name')." }
    if ($Value -is [System.Collections.IDictionary]) {
        if (-not $Value.Contains($Name)) { throw "Frame record is missing '$Name'." }
        return ,$Value[$Name]
    }
    if ($Name -cnotin @($Value.PSObject.Properties.Name)) { throw "Frame record is missing '$Name'." }
    return ,$Value.$Name
}

function ConvertTo-P4FrameInteger {
    param([AllowNull()]$Value, [string]$Name, [bool]$AllowZero = $false)
    if ($null -eq $Value -or ($Value -isnot [string] -and $Value.GetType() -notin @([byte], [sbyte], [int16], [uint16],
            [int32], [uint32], [int64], [uint64]))) { throw "Frame '$Name' is not an exact integer." }
    $parsed = [long]0
    if ([string]$Value -cnotmatch '^[0-9]+$' -or
        -not [long]::TryParse([string]$Value, [Globalization.NumberStyles]::None,
            [Globalization.CultureInfo]::InvariantCulture, [ref]$parsed) -or
        (-not $AllowZero -and $parsed -eq 0)) { throw "Frame '$Name' is not an in-range integer." }
    return $parsed
}

function ConvertTo-P4FrameFiniteMetric {
    param([AllowNull()]$Value, [string]$Name)
    if ($null -eq $Value -or $Value.GetType() -notin @([string], [byte], [sbyte], [int16], [uint16],
            [int32], [uint32], [int64], [uint64], [float], [double], [decimal])) {
        throw "Frame '$Name' must be scalar numeric/text."
    }
    $text = if ($Value -is [IFormattable]) {
        $Value.ToString($null, [Globalization.CultureInfo]::InvariantCulture)
    } else { [string]$Value }
    $parsed = [decimal]0
    if ($text -cnotmatch '^-?[0-9]+(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?\z' -or
        -not [decimal]::TryParse($text, [Globalization.NumberStyles]::Float,
            [Globalization.CultureInfo]::InvariantCulture, [ref]$parsed)) {
        throw "Frame '$Name' is not an invariant finite decimal metric."
    }
    return $parsed.ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
}

function ConvertTo-P4FrameJsonInteger {
    param([AllowNull()]$Value, [string]$Name, [bool]$AllowZero = $false)
    # CSV and key=value records contain text; JSON run-state publishes native integer tokens.
    if ($null -eq $Value -or $Value.GetType() -notin @([byte], [sbyte], [int16], [uint16],
            [int32], [uint32], [int64], [uint64])) { throw "Frame JSON '$Name' requires a native integer." }
    return ConvertTo-P4FrameInteger $Value $Name $AllowZero
}

function Assert-P4FramePhaseRows {
    # Lossless own-row primitive shared by every v9 phase and the historical pure tap helper.
    param([Parameter(Mandatory)][AllowNull()][AllowEmptyCollection()][object[]]$Rows,
        [Parameter(Mandatory)][System.Collections.IDictionary]$ExpectedCapture,
        [Parameter(Mandatory)][ValidateSet('preview', 'commit')][string]$Phase,
        [Parameter(Mandatory)][long]$EventCount, [switch]$FullMetrics)
    $count = ConvertTo-P4FrameInteger (Get-P4FrameExactMember $ExpectedCapture 'raw_row_count') 'raw_row_count'
    if ($null -eq $Rows -or $Rows.Count -ne $count) { throw 'Frame phase is empty or has lost/extra rows.' }
    $ids = [Collections.Generic.HashSet[long]]::new()
    $previous = $null
    for ($index = 0; $index -lt $Rows.Count; $index++) {
        $row = $Rows[$index]
        foreach ($name in @('workload', 'operation', 'source_commit', 'production_commit', 'variant')) {
            $actual = Get-P4FrameExactMember $row $name
            if ($actual -isnot [string] -or $actual -cne (Get-P4FrameExactMember $ExpectedCapture $name)) {
                throw "Frame row has foreign '$name'."
            }
        }
        foreach ($name in @('operation_ordinal', 'sample_index')) {
            if ((ConvertTo-P4FrameInteger (Get-P4FrameExactMember $row $name) $name) -ne
                (ConvertTo-P4FrameInteger (Get-P4FrameExactMember $ExpectedCapture $name) $name)) {
                throw "Frame row has foreign '$name'."
            }
        }
        $rowPhase = Get-P4FrameExactMember $row 'phase'
        if ($rowPhase -isnot [string] -or $rowPhase -cne $Phase -or
            (ConvertTo-P4FrameInteger (Get-P4FrameExactMember $row 'event_count') 'event_count') -ne $EventCount -or
            (ConvertTo-P4FrameInteger (Get-P4FrameExactMember $row 'flags') 'flags' $true) -ne 0 -or
            (ConvertTo-P4FrameInteger (Get-P4FrameExactMember $row 'row_index') 'row_index') -ne $index + 1) {
            throw 'Frame row has a wrong phase/event, flags or nonconsecutive row index.'
        }
        $values = @{}
        foreach ($name in @('frame_timeline_vsync_id', 'intended_vsync_nanos', 'frame_start_nanos',
                'handle_input_start_nanos', 'frame_completed_nanos', 'frame_deadline_nanos')) {
            $values[$name] = ConvertTo-P4FrameInteger (Get-P4FrameExactMember $row $name) $name
        }
        if (-not $ids.Add($values.frame_timeline_vsync_id)) { throw 'Frame phase repeats a frame identity.' }
        if ($values.intended_vsync_nanos -gt $values.frame_start_nanos -or
            $values.frame_start_nanos -gt $values.handle_input_start_nanos -or
            $values.handle_input_start_nanos -ge $values.frame_completed_nanos -or
            $values.frame_deadline_nanos -le $values.intended_vsync_nanos) {
            throw 'Frame row timestamps do not preserve required within-row order.'
        }
        foreach ($name in @('intended_vsync_nanos', 'frame_start_nanos', 'handle_input_start_nanos', 'frame_completed_nanos')) {
            if ($null -ne $previous -and $values[$name] -le $previous[$name]) { throw "Frame '$name' is tied or reordered." }
        }
        $starts = [ordered]@{ input_start_to_completion_ms = 'handle_input_start_nanos'; frame_overrun_ms = 'frame_deadline_nanos' }
        if ($FullMetrics) { $starts.frame_duration_cpu_ms = 'frame_start_nanos'; $starts.app_frame_total_ms = 'intended_vsync_nanos' }
        foreach ($metric in $starts.Keys) {
            $derived = (([decimal]$values.frame_completed_nanos - [decimal]$values[$starts[$metric]]) /
                [decimal]1000000).ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
            if ((ConvertTo-P4FrameFiniteMetric (Get-P4FrameExactMember $row $metric) $metric) -cne $derived) {
                throw "Frame '$metric' disagrees with its own row timestamps."
            }
        }
        $previous = $values
    }
}

function Get-P4FrameOperationTiming {
    param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$PreviewRows,
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$CommitRows)
    if ($PreviewRows.Count -lt 1 -or $CommitRows.Count -lt 1) { throw 'Operation timing requires preview and commit frames.' }
    $ids = [Collections.Generic.HashSet[long]]::new()
    $previewInput = [long]::MaxValue; $commitInput = [long]::MaxValue
    $previewCompletion = [long]0; $completion = [long]0
    foreach ($phase in @('preview', 'commit')) {
        $rows = if ($phase -ceq 'preview') { $PreviewRows } else { $CommitRows }
        foreach ($row in $rows) {
            $id = ConvertTo-P4FrameInteger (Get-P4FrameExactMember $row 'frame_timeline_vsync_id') 'frame_timeline_vsync_id'
            if (-not $ids.Add($id)) { throw 'Operation timing repeats a frame identity.' }
            $input = ConvertTo-P4FrameInteger (Get-P4FrameExactMember $row 'handle_input_start_nanos') 'handle_input_start_nanos'
            $end = ConvertTo-P4FrameInteger (Get-P4FrameExactMember $row 'frame_completed_nanos') 'frame_completed_nanos'
            if ($end -le $input) { throw 'Operation completion precedes its own input.' }
            if ($phase -ceq 'preview') { $previewInput = [Math]::Min($previewInput, $input); $previewCompletion = [Math]::Max($previewCompletion, $end) }
            else { $commitInput = [Math]::Min($commitInput, $input); $completion = [Math]::Max($completion, $end) }
        }
    }
    if ($previewCompletion -ge $commitInput) { throw 'Operation preview completion must precede UP input.' }
    return [pscustomobject][ordered]@{
        preview_input_start_nanos = $previewInput
        commit_input_start_nanos = $commitInput
        committed_result_completion_nanos = $completion
        input_to_committed_result_ms = ([decimal]$completion - [decimal]$commitInput) / [decimal]1000000
        down_to_committed_result_ms = ([decimal]$completion - [decimal]$previewInput) / [decimal]1000000
    }
}

function Get-P4FirstPreviewAssociation {
    # Prospective #145 preparation only; historical capture analysis does not call this helper.
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowNull()][AllowEmptyCollection()][object[]]$PreviewRows,
        [Parameter(Mandatory)][System.Collections.IDictionary]$ExpectedCapture
    )

    $member = { param($Value, $Name) Get-P4FrameExactMember $Value $Name }
    $integer = { param($Value, $Name, $AllowZero = $false) ConvertTo-P4FrameInteger $Value $Name $AllowZero }
    $expected = @{}
    foreach ($name in @('workload', 'operation', 'source_commit', 'production_commit', 'variant')) {
        $expected[$name] = & $member $ExpectedCapture $name
        if ($expected[$name] -isnot [string] -or [string]::IsNullOrWhiteSpace($expected[$name])) {
            throw "First-preview expected '$name' must be nonempty text."
        }
    }
    foreach ($name in @('operation_ordinal', 'sample_index', 'raw_row_count')) {
        $expected[$name] = & $integer (& $member $ExpectedCapture $name) $name
    }
    if ($expected.workload -cne 'canvas256_layers16_tap' -or
        $expected.variant -cne 'release-like' -or
        $expected.operation -cne "$($expected.workload)#$($expected.sample_index)" -or
        $expected.source_commit -cnotmatch '^[0-9a-fA-F]{40}$' -or
        $expected.production_commit -cnotmatch '^[0-9a-fA-F]{40}$') {
        throw 'First-preview expected identity is outside the DOWN-only capture contract.'
    }
    if ($null -eq $PreviewRows -or $PreviewRows.Count -eq 0 -or
        $PreviewRows.Count -ne $expected.raw_row_count) {
        throw 'First-preview capture is empty or has lost/extra rows.'
    }

    Assert-P4FramePhaseRows -Rows $PreviewRows -ExpectedCapture $expected -Phase preview -EventCount 1
    $row = $PreviewRows[0]
    return [pscustomobject][ordered]@{
        first_preview_frame_timeline_vsync_id = & $integer (& $member $row 'frame_timeline_vsync_id') 'frame_timeline_vsync_id'
        first_preview_row_index = [long]1
        first_preview_handle_input_start_nanos = & $integer (& $member $row 'handle_input_start_nanos') 'handle_input_start_nanos'
        first_preview_frame_completed_nanos = & $integer (& $member $row 'frame_completed_nanos') 'frame_completed_nanos'
        first_preview_deadline_nanos = & $integer (& $member $row 'frame_deadline_nanos') 'frame_deadline_nanos'
        first_preview_service_ms = ConvertTo-P4FrameFiniteMetric (& $member $row 'input_start_to_completion_ms') 'input_start_to_completion_ms'
        first_preview_overrun_ms = ConvertTo-P4FrameFiniteMetric (& $member $row 'frame_overrun_ms') 'frame_overrun_ms'
    }
}

function Get-P4FrameNearestRank {
    param(
        [Parameter(Mandatory = $true)][double[]]$Values,
        [Parameter(Mandatory = $true)][double]$Percentile
    )
    if ($Values.Count -eq 0) { throw 'A frame percentile requires a nonempty population.' }
    $sorted = [double[]]$Values.Clone()
    [Array]::Sort($sorted)
    $index = [Math]::Ceiling($sorted.Count * $Percentile) - 1
    return $sorted[$index]
}

function Format-P4FrameMetric {
    param([Parameter(Mandatory = $true)][double]$Value)
    # Culture-independent: the published metric text must not depend on the operator's locale.
    return $Value.ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
}

function Get-P4FrameObjectMember {
    param(
        [AllowNull()][Parameter(Mandatory = $true)][object]$Value,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Context
    )
    if ($null -eq $Value -or $Name -cnotin @($Value.PSObject.Properties.Name)) {
        throw "$Context is missing '$Name'."
    }
    return $Value.$Name
}

function Read-P4FrameMetadata {
    # [AllowEmptyString()] so a blank record reaches the explicit blank-record refusal below instead of
    # failing parameter binding with ParameterArgumentValidationErrorEmptyStringNotAllowed.
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string[]]$Lines)
    $metadata = [ordered]@{}
    foreach ($line in $Lines) {
        if ([string]::IsNullOrWhiteSpace($line)) { throw 'Frame metadata contains a blank record.' }
        $separator = $line.IndexOf('=', [StringComparison]::Ordinal)
        if ($separator -lt 1) { throw "Frame metadata is not a key=value record: $line" }
        $key = $line.Substring(0, $separator)
        if ($metadata.Contains($key)) { throw "Frame metadata repeats '$key'." }
        $metadata[$key] = $line.Substring($separator + 1)
    }
    if ($metadata.Count -eq 0) { throw 'Frame metadata is empty.' }
    return $metadata
}

function Get-P4FrameMetadataValue {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Metadata,
        [Parameter(Mandatory = $true)][string]$Key
    )
    if (-not $Metadata.Contains($Key)) { throw "Frame metadata is missing '$Key'." }
    $value = [string]$Metadata[$Key]
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Frame metadata '$Key' is empty." }
    return $value
}

function Assert-P4FrameMetadataValue {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Metadata,
        [Parameter(Mandatory = $true)][string]$Key,
        [Parameter(Mandatory = $true)][string]$Expected
    )
    $actual = Get-P4FrameMetadataValue -Metadata $Metadata -Key $Key
    if ($actual -cne $Expected) { throw "Frame metadata '$Key' is '$actual' but must be '$Expected'." }
    return $actual
}

function Get-P4FrameFileInventory {
    param([Parameter(Mandatory = $true)][string]$SlotDirectory)
    $root = [IO.Path]::GetFullPath($SlotDirectory)
    $files = [Collections.Generic.List[object]]::new()
    foreach ($item in @(Get-ChildItem -LiteralPath $root -Recurse -File -Force | Sort-Object -Property FullName)) {
        $full = [IO.Path]::GetFullPath($item.FullName)
        if (-not $full.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'A frame slot file escaped its slot directory.'
        }
        if ($null -ne $item.LinkType) { throw "A frame slot file is a link: $full" }
        $relative = $full.Substring($root.Length).TrimStart([char]'\', [char]'/').Replace('\', '/')
        $files.Add([ordered]@{ relative_path = $relative; byte_count = $item.Length; sha256 = Get-FileSha256 $full })
    }
    if ($files.Count -eq 0) { throw 'A frame slot directory must retain its collected evidence.' }
    return $files.ToArray()
}

function Get-P4FrameSampleInput {
    param([Parameter(Mandatory = $true)][object[]]$CommitRows)
    if ($CommitRows.Count -lt 1) { throw 'A committed-result sample requires at least one commit frame.' }
    $start = [long]($CommitRows | ForEach-Object { [long]$_.handle_input_start_nanos } | Measure-Object -Minimum).Minimum
    $completion = [long]($CommitRows | ForEach-Object { [long]$_.frame_completed_nanos } | Measure-Object -Maximum).Maximum
    if ($start -le 0 -or $completion -le $start) {
        throw 'A committed-result sample does not preserve UP input to completion order.'
    }
    return ($completion - $start) / 1000000.0
}

function ConvertTo-P4FrameDecimal {
    param([Parameter(Mandatory = $true)][string]$Text, [Parameter(Mandatory = $true)][string]$Context)
    $value = [decimal]0
    if (
        $Text -cnotmatch '^-?\d+\.\d{6}$' -or
        -not [decimal]::TryParse($Text, [Globalization.NumberStyles]::Number,
            [Globalization.CultureInfo]::InvariantCulture, [ref]$value)
    ) {
        throw "$Context is not a published six-decimal metric: $Text"
    }
    return $value
}

function Read-P4FrameBaselineReference {
    param(
        [Parameter(Mandatory = $true)][string]$BaselineAnalysisPath,
        [Parameter(Mandatory = $true)][string]$ExperimentId
    )
    $path = [IO.Path]::GetFullPath($BaselineAnalysisPath)
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Baseline frame analysis is missing: $path" }
    $baseline = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
    $context = 'Baseline frame analysis'
    $expected = [ordered]@{
        role = 'baseline'; runner = 'decision'; verdict = 'baseline-recorded'; experiment_id = $ExperimentId
    }
    foreach ($key in $expected.Keys) {
        $actual = Get-P4FrameObjectMember -Value $baseline -Name $key -Context $context
        if ([string]$actual -cne $expected[$key]) { throw "$context '$key' is '$actual' but must be '$($expected[$key])'." }
    }
    if ((Get-P4FrameObjectMember -Value $baseline -Name 'complete_run' -Context $context) -isnot [bool] -or
        -not $baseline.complete_run) {
        throw "$context is not a complete decision run."
    }
    $families = Get-P4FrameObjectMember -Value $baseline -Name 'families' -Context $context
    if ((@($families.PSObject.Properties.Name) -join '|') -cne ($script:P4FrameWorkloadOrder -join '|')) {
        throw "$context families are not exactly the decision families."
    }
    $reference = [ordered]@{}
    foreach ($workload in $script:P4FrameWorkloadOrder) {
        $family = $families.$workload
        $reference[$workload] = [ordered]@{
            p95 = ConvertTo-P4FrameDecimal ([string](Get-P4FrameObjectMember -Value $family -Name 'frame_overrun_p95_ms' `
                        -Context "$context family '$workload'")) "$context '$workload' p95"
            p99 = ConvertTo-P4FrameDecimal ([string](Get-P4FrameObjectMember -Value $family -Name 'frame_overrun_p99_ms' `
                        -Context "$context family '$workload'")) "$context '$workload' p99"
        }
    }
    return [ordered]@{ path = $path; families = $reference }
}

function Assert-P4FrameExactKeys {
    param($Value, [string[]]$Keys, [string]$Context)
    $actual = @(if ($Value -is [System.Collections.IDictionary]) { @($Value.Keys) }
        elseif ($null -ne $Value -and $Value -is [pscustomobject]) { @($Value.PSObject.Properties.Name) }
        else { throw "$Context must be an object." })
    if ($actual.Count -ne $Keys.Count -or @($actual | Where-Object { $_ -cnotin $Keys }).Count -gt 0) {
        throw "$Context has missing or extra keys."
    }
}

function Assert-P4FrameExactProjection {
    param($Actual, $Expected, [string]$Context)
    if ($Expected -is [System.Collections.IDictionary]) {
        Assert-P4FrameExactKeys $Actual @($Expected.Keys) $Context
        foreach ($key in $Expected.Keys) {
            Assert-P4FrameExactProjection (Get-P4FrameExactMember $Actual $key) $Expected[$key] "$Context.$key"
        }
    } elseif ($Expected -is [array]) {
        if ($Actual -isnot [array] -or $Actual.Count -ne $Expected.Count) { throw "$Context array drifted." }
        for ($i = 0; $i -lt $Expected.Count; $i++) { Assert-P4FrameExactProjection $Actual[$i] $Expected[$i] "$Context[$i]" }
    } elseif ($null -eq $Expected) {
        if ($null -ne $Actual) { throw "$Context must be null." }
    } elseif ($Expected -is [string]) {
        if ($Actual -isnot [string] -or $Actual -cne $Expected) { throw "$Context text drifted." }
    } elseif ($Expected -is [bool]) {
        if ($Actual -isnot [bool] -or $Actual -ne $Expected) { throw "$Context boolean drifted." }
    } else {
        if ($Actual -is [string]) { throw "$Context requires a JSON integer, not numeric text." }
        if ((ConvertTo-P4FrameInteger $Actual $Context $true) -ne $Expected) { throw "$Context integer drifted." }
    }
}

function Assert-P4FramePhaseIdentity {
    param($Value, [System.Collections.IDictionary]$Identity, [string]$Context)
    foreach ($key in $Identity.Keys) {
        $actual = Get-P4FrameExactMember $Value $key
        if ($actual -isnot [string] -or $actual -cne $Identity[$key]) { throw "$Context has foreign '$key'." }
    }
}

function Read-P4LayerFrameBaselineReference {
    param([string]$BaselineAnalysisPath, [string]$ExperimentId, [string]$ExperimentSha256,
        [System.Collections.IDictionary]$Contract, [System.Collections.IDictionary]$PhaseContext)
    $pinned = $PhaseContext.baseline_reference
    Assert-P4FrameExactKeys $pinned @('build_commit', 'production_commit', 'apk_sha256', 'analysis_sha256', 'capture_seal_sha256') 'Phase baseline_reference'
    foreach ($key in @('build_commit', 'production_commit', 'apk_sha256', 'analysis_sha256', 'capture_seal_sha256')) {
        $value = Get-P4FrameExactMember $pinned $key
        $length = if ($key -cin @('build_commit', 'production_commit')) { 40 } else { 64 }
        if ($value -isnot [string] -or $value -cnotmatch "^[0-9a-f]{$length}\z") { throw "Phase baseline '$key' is malformed." }
    }
    if ($pinned.production_commit -cne $Contract.baseline_production_commit) { throw 'Phase baseline production is outside its group.' }
    $baselineContract = Get-P4FrameExecutionContract -ProtocolId $Contract.protocol_id -SlotId $Contract.baseline_slot_id
    $path = [IO.Path]::GetFullPath($BaselineAnalysisPath)
    $directory = Split-Path -Parent $path
    if ((Split-Path -Leaf $path) -cne 'analysis.json' -or
        (Split-Path -Leaf $directory) -cne $baselineContract.slot_id -or
        -not (Test-Path -LiteralPath $path -PathType Leaf)) { throw 'Phase baseline path is not the canonical analysis slot.' }
    if ((Get-FileSha256 $path) -cne $pinned.analysis_sha256) { throw 'Phase baseline analysis file hash drifted.' }
    . (Join-Path $PSScriptRoot 'p4-indexed-capture-seal.ps1')
    Read-P4VerifiedCaptureSeal -SlotDirectory $directory -SlotId $baselineContract.slot_id -ExpectedSha256 $pinned.capture_seal_sha256 | Out-Null
    $baseline = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json -NoEnumerate
    $header = [ordered]@{
        schema = $Contract.frame_schema; experiment_schema = $Contract.experiment_schema; verdict_rule = $Contract.verdict_id
        protocol_id = $Contract.protocol_id; preflight_sha256 = $PhaseContext.preflight_sha256
        group_id = $Contract.group_id; slot_id = $baselineContract.slot_id; artifact_role = $baselineContract.artifact_role
        experiment_id = $ExperimentId; experiment_sha256 = $ExperimentSha256; role = 'baseline'; runner = 'decision'
        build_commit = $pinned.build_commit; production_commit = $pinned.production_commit; apk_sha256 = $pinned.apk_sha256
        capture_seal_sha256 = $pinned.capture_seal_sha256; verdict = 'baseline-recorded'; geometry_id = $Contract.geometry_id
    }
    Assert-P4FramePhaseIdentity $baseline $header 'Phase baseline analysis'
    if ((Get-P4FrameExactMember $baseline 'complete_run') -isnot [bool] -or -not $baseline.complete_run) { throw 'Phase baseline is incomplete.' }
    foreach ($entry in @{
            sequence_index = $baselineContract.sequence_index; attempt = 1; warmups_per_workload = 5
            samples_per_workload = 50; fatal_anr_matches = 0
            measured_operation_count = 50 * $Contract.decision_workload_order.Count
        }.GetEnumerator()) {
        if ((ConvertTo-P4FrameInteger (Get-P4FrameExactMember $baseline $entry.Key) $entry.Key $true) -ne $entry.Value) {
            throw "Phase baseline '$($entry.Key)' drifted."
        }
    }
    $families = Get-P4FrameExactMember $baseline 'families'
    if ((@($families.PSObject.Properties.Name) -join '|') -cne ($Contract.decision_workload_order -join '|')) { throw 'Phase baseline family catalog drifted.' }
    $reference = [ordered]@{}
    foreach ($workload in $Contract.decision_workload_order) {
        $family = Get-P4FrameExactMember $families $workload
        if ((ConvertTo-P4FrameInteger (Get-P4FrameExactMember $family 'operation_count') 'operation_count') -ne 50 -or
            (ConvertTo-P4FrameInteger (Get-P4FrameExactMember $family 'frame_count') 'frame_count') -lt 100 -or
            (Get-P4FrameExactMember $family 'threshold_status') -cne 'pass' -or
            (Get-P4FrameExactMember $family 'gross_regression') -isnot [bool]) { throw 'Phase baseline family population or verdict drifted.' }
        $input = ConvertTo-P4FrameDecimal ([string](Get-P4FrameExactMember $family 'input_to_committed_result_p95_ms')) 'Baseline input p95'
        if ($input -gt [decimal]33.33) { throw 'Phase baseline admission guard failed.' }
        if ($workload -ceq $Contract.association_workload) {
            if ((ConvertTo-P4FrameInteger (Get-P4FrameExactMember $family 'first_preview_operation_count') 'first_preview_operation_count') -ne 50) {
                throw 'Phase baseline first-preview population drifted.'
            }
            foreach ($key in @('first_preview_service_p95_ms', 'first_preview_overrun_p95_ms', 'first_preview_overrun_p99_ms')) {
                ConvertTo-P4FrameDecimal ([string](Get-P4FrameExactMember $family $key)) "Baseline $key" | Out-Null
            }
        }
        $reference[$workload] = [ordered]@{
            p95 = ConvertTo-P4FrameDecimal ([string](Get-P4FrameExactMember $family 'frame_overrun_p95_ms')) 'Baseline p95'
            p99 = ConvertTo-P4FrameDecimal ([string](Get-P4FrameExactMember $family 'frame_overrun_p99_ms')) 'Baseline p99'
        }
    }
    $frameCount = 0L
    foreach ($workload in $Contract.decision_workload_order) { $frameCount += ConvertTo-P4FrameInteger $families.$workload.frame_count 'frame_count' }
    foreach ($key in @('raw_frame_rows', 'valid_frame_rows')) {
        if ((ConvertTo-P4FrameInteger (Get-P4FrameExactMember $baseline $key) $key) -ne $frameCount) { throw 'Phase baseline frame totals drifted.' }
    }
    return [ordered]@{ path = $path; families = $reference }
}

function Get-P4FrameCaptureContract {
    # Shared pure caller validation; file/experiment/seal admission belongs to the analyzer below.
    [CmdletBinding()]
    param([Parameter(Mandatory)][ValidateSet('baseline', 'candidate')][string]$Role,
        [Parameter(Mandatory)][ValidateSet('decision', 'diagnostic')][string]$Runner,
        [Parameter(Mandatory)][ValidateRange(1, 12)][int]$SequenceIndex,
        [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}\z')][string]$BuildCommit,
        [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{64}\z')][string]$ExpectedApkSha256,
        [Parameter(Mandatory)][string]$ExperimentId,
        [AllowNull()][System.Collections.IDictionary]$PhaseContext)
    if (-not $PSBoundParameters.ContainsKey('PhaseContext')) {
        if ($SequenceIndex -gt 4) { throw 'Historical frame sequence must be within 1..4.' }
        $slot = @(Get-P4FrameSlotCatalog | Where-Object { $_.role -ceq $Role -and $_.runner -ceq $Runner -and $_.run -eq $SequenceIndex })
        if ($slot.Count -ne 1) { throw 'Historical frame arguments disagree with slot catalog.' }
        return Get-P4FrameExecutionContract -SlotId $slot[0].id
    }
    Assert-P4FrameExactKeys $PhaseContext @('protocol_id', 'slot_id', 'preflight_sha256', 'production_commit', 'baseline_reference') 'PhaseContext'
    foreach ($key in @('protocol_id', 'slot_id', 'preflight_sha256', 'production_commit')) {
        $value = Get-P4FrameExactMember $PhaseContext $key
        if ($value -isnot [string] -or [string]::IsNullOrWhiteSpace($value)) { throw "PhaseContext '$key' must be nonempty text." }
    }
    if ($PhaseContext.protocol_id -cne 'nene-pixel-p4-layer-phase-verification-v1' -or
        $PhaseContext.preflight_sha256 -cnotmatch '^[0-9a-f]{64}\z' -or $PhaseContext.production_commit -cnotmatch '^[0-9a-f]{40}\z') {
        throw 'PhaseContext protocol or hash identity is malformed.'
    }
    $contract = Get-P4FrameExecutionContract -ProtocolId $PhaseContext.protocol_id -SlotId $PhaseContext.slot_id
    if ($Role -cne $contract.role -or $Runner -cne $contract.runner -or $SequenceIndex -ne $contract.sequence_index -or
        ($Role -ceq 'baseline' -and $PhaseContext.production_commit -cne $contract.baseline_production_commit)) {
        throw 'Phase arguments disagree with the canonical execution contract.'
    }
    Get-P4LayerFrameExperimentContract -ExperimentId $ExperimentId -PreflightSha256 $PhaseContext.preflight_sha256 | Out-Null
    if ($Role -ceq 'candidate' -and $Runner -ceq 'decision') {
        $pinned = $PhaseContext.baseline_reference
        Assert-P4FrameExactKeys $pinned @('build_commit', 'production_commit', 'apk_sha256', 'analysis_sha256', 'capture_seal_sha256') 'Phase baseline_reference'
        foreach ($key in @('build_commit', 'production_commit', 'apk_sha256', 'analysis_sha256', 'capture_seal_sha256')) {
            $value = Get-P4FrameExactMember $pinned $key
            $length = if ($key -cin @('build_commit', 'production_commit')) { 40 } else { 64 }
            if ($value -isnot [string] -or $value -cnotmatch "^[0-9a-f]{$length}\z") { throw "Phase baseline '$key' is malformed." }
        }
        if ($pinned.production_commit -cne $contract.baseline_production_commit) { throw 'Phase baseline production is outside its group.' }
    } elseif ($null -ne $PhaseContext.baseline_reference) { throw 'Phase baseline_reference is forbidden outside the decision candidate.' }
    return $contract
}

function Test-P4FrameCapture {
    param(
        [Parameter(Mandatory = $true)][string]$SlotDirectory,
        [Parameter(Mandatory = $true)][ValidateSet('baseline', 'candidate')][string]$Role,
        [Parameter(Mandatory = $true)][ValidateSet('diagnostic', 'decision')][string]$Runner,
        [Parameter(Mandatory = $true)][ValidateRange(1, 12)][int]$SequenceIndex,
        [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-f]{40}\z')][string]$BuildCommit,
        [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-f]{64}\z')][string]$ExpectedApkSha256,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$ExpectedBounds,
        [Parameter(Mandatory = $true)][string]$ExperimentId,
        # Required for the decision candidate only: the decision baseline slot's analysis.json.
        [string]$BaselineAnalysisPath = '',
        [AllowNull()][System.Collections.IDictionary]$PhaseContext
    )

    $phase = $PSBoundParameters.ContainsKey('PhaseContext')
    $contract = $null; $identity = $null; $experimentSha256 = $null
    $frameSchema = $script:P4FrameSchema; $experimentSchema = $script:P4FrameExperimentSchema
    $verdictRule = $script:P4FrameVerdictRule; $geometryId = $script:P4FrameGeometryId
    $decisionOrder = $script:P4FrameWorkloadOrder; $diagnosticOrder = $script:P4FrameDiagnosticWorkloadOrder
    $warmups = $script:P4FrameWarmups; $inputGate = $script:P4FrameInputP95Gate
    $relativeP95 = $script:P4FrameRelativeP95ToleranceMs; $relativeP99 = $script:P4FrameRelativeP99ToleranceMs
    $grossOverrun = $script:P4FrameGrossOverrun; $grossInput = $script:P4FrameGrossInput
    if ($phase) {
        $contract = Get-P4FrameCaptureContract -Role $Role -Runner $Runner -SequenceIndex $SequenceIndex `
            -BuildCommit $BuildCommit -ExpectedApkSha256 $ExpectedApkSha256 -ExperimentId $ExperimentId -PhaseContext $PhaseContext
        $frameSchema = $contract.frame_schema; $experimentSchema = $contract.experiment_schema
        $verdictRule = $contract.verdict_id; $geometryId = $contract.geometry_id
        $decisionOrder = $contract.decision_workload_order; $diagnosticOrder = $contract.diagnostic_workload_order
        $warmups = $contract.warmups; $inputGate = $contract.input_p95_gate_ms
        $relativeP95 = $contract.relative_p95_tolerance_ms; $relativeP99 = $contract.relative_p99_tolerance_ms
        $grossOverrun = $contract.gross_overrun_ms; $grossInput = $contract.gross_input_ms
        $identity = [ordered]@{ protocol_id = $contract.protocol_id; preflight_sha256 = $PhaseContext.preflight_sha256
            group_id = $contract.group_id; slot_id = $contract.slot_id; artifact_role = $contract.artifact_role; experiment_id = $ExperimentId }
        $experimentPath = Join-Path (Split-Path -Parent ([IO.Path]::GetFullPath($SlotDirectory))) 'experiment.json'
        if (-not (Test-Path -LiteralPath $experimentPath -PathType Leaf)) { throw 'Phase experiment.json is missing.' }
        $experiment = Get-Content -Raw -LiteralPath $experimentPath | ConvertFrom-Json -NoEnumerate
        $expectedExperiment = Get-P4LayerFrameExperimentContract -ExperimentId $ExperimentId -PreflightSha256 $PhaseContext.preflight_sha256
        Assert-P4FrameExactProjection $experiment $expectedExperiment 'Phase experiment'
        $experimentSha256 = Get-FileSha256 $experimentPath
    } elseif ($SequenceIndex -gt 4) { throw 'Historical frame sequence must be within 1..4.' }

    $isRelativeCandidate = $Runner -eq 'decision' -and $Role -eq 'candidate'
    if ($isRelativeCandidate -ne (-not [string]::IsNullOrEmpty($BaselineAnalysisPath))) {
        [Console]::Error.WriteLine('-BaselineAnalysisPath is required for the decision candidate and forbidden otherwise.')
        exit 2
    }
    if ($phase -and -not $isRelativeCandidate -and $null -ne $PhaseContext.baseline_reference) { throw 'Phase baseline_reference is forbidden outside the decision candidate.' }
    $baselineReference = if ($isRelativeCandidate) {
        if ($phase) { Read-P4LayerFrameBaselineReference -BaselineAnalysisPath $BaselineAnalysisPath -ExperimentId $ExperimentId `
            -ExperimentSha256 $experimentSha256 -Contract $contract -PhaseContext $PhaseContext }
        else { Read-P4FrameBaselineReference -BaselineAnalysisPath $BaselineAnalysisPath -ExperimentId $ExperimentId }
    }
    else { $null }

    foreach ($workload in $decisionOrder) {
        if (-not $ExpectedBounds.Contains($workload)) { throw "Expected frame bounds are missing '$workload'." }
        if ([string]$ExpectedBounds[$workload] -cnotmatch '^\[\d+,\d+\]\[\d+,\d+\]$') {
            throw "Expected frame bounds for '$workload' are not an integer rectangle."
        }
    }
    $expectedSamples = if ($Runner -eq 'diagnostic') { 10 } else { 50 }
    $expectedSlotName = if ($phase) { $contract.frame_directory_name } else { 'slot-{0:D2}-{1}-{2}-attempt-1' -f $SequenceIndex, $Runner, $Role }
    $root = [IO.Path]::GetFullPath($SlotDirectory)
    if (-not (Test-Path -LiteralPath $root -PathType Container)) { throw "Frame slot directory is missing: $root" }
    if ((Split-Path -Leaf $root) -cne $expectedSlotName) {
        throw "Frame slot directory '$(Split-Path -Leaf $root)' is not the expected '$expectedSlotName'."
    }

    $statePath = Join-Path $root 'run-state.json'
    $metadataPath = Join-Path $root 'metadata.txt'
    $framesPath = Join-Path $root 'frames.csv'
    $samplesPath = Join-Path $root 'samples.csv'
    foreach ($path in @($statePath, $metadataPath, $framesPath, $samplesPath)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Frame evidence is missing: $path" }
    }

    $state = if ($phase) { Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json -NoEnumerate } else { Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json }
    if ($phase) {
        Assert-P4FramePhaseIdentity $state $identity 'Phase run state'
        foreach ($key in @('schema', 'experiment_sha256', 'status', 'verdict')) {
            if ((Get-P4FrameExactMember $state $key) -isnot [string]) { throw "Phase run state '$key' must be scalar text." }
        }
        if ((Get-P4FrameExactMember $state 'experiment_sha256') -cne $experimentSha256) { throw 'Phase run state experiment hash drifted.' }
        foreach ($key in @('comparison_sequence_index', 'attempt', 'measured_operation_count')) {
            ConvertTo-P4FrameJsonInteger (Get-P4FrameExactMember $state $key) $key | Out-Null
        }
        Assert-P4FrameExactKeys (Get-P4FrameExactMember $state 'measured_workload_counts') @($contract.workload_order) 'Phase measured_workload_counts'
        foreach ($key in $contract.workload_order) { ConvertTo-P4FrameJsonInteger (Get-P4FrameExactMember $state.measured_workload_counts $key) $key $true | Out-Null }
        if ($state.workload_order -isnot [array] -or @($state.workload_order | Where-Object { $_ -isnot [string] }).Count -gt 0) { throw 'Phase workload order must be an array of text.' }
        if ((Get-P4FrameExactMember $state 'complete_run') -isnot [bool]) { throw 'Phase complete_run must be boolean.' }
    }
    $stateNames = @($state.PSObject.Properties.Name)
    foreach ($key in @('schema', 'experiment_id', 'comparison_sequence_index', 'attempt', 'status', 'verdict',
            'workload_order', 'measured_workload_counts', 'measured_operation_count')) {
        if ($key -notin $stateNames) { throw "Frame run state is missing '$key'." }
    }
    # Decision slots declare exactly families 1-2; diagnostic slots declare exactly families 1-3.
    $declaredOrder = @($state.workload_order | ForEach-Object { [string]$_ })
    $allowedOrder = if ($Runner -eq 'decision') { $decisionOrder } else { $diagnosticOrder }
    $declaredOrderValid = ($declaredOrder -join '|') -ceq ($allowedOrder -join '|')
    if (
        $state.schema -cne $experimentSchema -or
        $state.experiment_id -cne $ExperimentId -or
        [int]$state.comparison_sequence_index -ne $SequenceIndex -or
        [int]$state.attempt -ne 1 -or
        $state.status -cne 'completed' -or
        -not $declaredOrderValid
    ) {
        throw 'Frame run state identity, attempt, completion or workload order drifted.'
    }
    foreach ($workload in $declaredOrder) {
        if (-not $ExpectedBounds.Contains($workload) -or
            [string]$ExpectedBounds[$workload] -cnotmatch '^\[\d+,\d+\]\[\d+,\d+\]$') {
            throw "Expected frame bounds for '$workload' are missing or not an integer rectangle."
        }
    }

    $metadata = Read-P4FrameMetadata -Lines @(Get-Content -LiteralPath $metadataPath)
    if ($phase) {
        Assert-P4FramePhaseIdentity $metadata $identity 'Phase metadata'
        Assert-P4FrameMetadataValue $metadata 'production_commit' $PhaseContext.production_commit | Out-Null
        Assert-P4FrameMetadataValue $metadata 'experiment_sha256' $experimentSha256 | Out-Null
        Assert-P4FrameMetadataValue $metadata 'comparison_order' ($contract.comparison_order -join '|') | Out-Null
        foreach ($key in @('comparison_sequence_index', 'warmups_per_workload', 'samples_per_workload', 'fatal_anr_matches',
                'raw_frame_rows', 'valid_frame_rows', 'aggregate_frames_rendered', 'measured_operation_count')) {
            ConvertTo-P4FrameInteger (Get-P4FrameMetadataValue $metadata $key) $key $true | Out-Null
        }
        foreach ($key in $contract.workload_order) { ConvertTo-P4FrameInteger (Get-P4FrameMetadataValue $metadata "measured_$key") $key $true | Out-Null }
    }
    Assert-P4FrameMetadataValue $metadata 'schema'  $frameSchema | Out-Null
    Assert-P4FrameMetadataValue $metadata 'experiment_schema' $experimentSchema | Out-Null
    Assert-P4FrameMetadataValue $metadata 'experiment_id' $ExperimentId | Out-Null
    Assert-P4FrameMetadataValue $metadata 'variant' 'release-like' | Out-Null
    Assert-P4FrameMetadataValue $metadata 'compile_mode' 'speed-profile' | Out-Null
    Assert-P4FrameMetadataValue $metadata 'run_kind' $Runner | Out-Null
    Assert-P4FrameMetadataValue $metadata 'candidate_role' $Role | Out-Null
    Assert-P4FrameMetadataValue $metadata 'comparison_sequence_index' ([string]$SequenceIndex) | Out-Null
    Assert-P4FrameMetadataValue $metadata 'workload_order' ($declaredOrder -join '|') | Out-Null
    Assert-P4FrameMetadataValue $metadata 'source_commit' $BuildCommit | Out-Null
    Assert-P4FrameMetadataValue $metadata 'apk_sha256' $ExpectedApkSha256 | Out-Null
    Assert-P4FrameMetadataValue $metadata 'apk_embedded_source_commit' $BuildCommit | Out-Null
    Assert-P4FrameMetadataValue $metadata 'geometry_id' $geometryId | Out-Null
    Assert-P4FrameMetadataValue $metadata 'device_evidence_class' 'physical_device' | Out-Null
    Assert-P4FrameMetadataValue $metadata 'warmups_per_workload' ([string]$warmups) | Out-Null
    Assert-P4FrameMetadataValue $metadata 'samples_per_workload' ([string]$expectedSamples) | Out-Null
    $fatalText = Get-P4FrameMetadataValue -Metadata $metadata -Key 'fatal_anr_matches'
    if ($fatalText -cnotmatch '^\d+$') { throw "Frame metadata fatal_anr_matches is not a count: $fatalText" }
    $fatalMatches = [int]$fatalText
    # Only a decision slot turns fatal matches into a verdict; a diagnostic slot still refuses them.
    if ($Runner -eq 'diagnostic' -and $fatalMatches -ne 0) { throw 'A diagnostic frame slot recorded fatal matches.' }
    foreach ($workload in $declaredOrder) {
        # The window family draws on the same 256 by 256 surface; the collector records that surface
        # once, under canvas256_repeated_diagonal_surface_bounds.
        if ($workload -ceq 'canvas256_repeated_diagonal_window_x2') {
            if ([string]$ExpectedBounds[$workload] -cne [string]$ExpectedBounds['canvas256_repeated_diagonal']) {
                throw "Expected frame bounds for '$workload' must equal the canvas256 surface bounds."
            }
            continue
        }
        Assert-P4FrameMetadataValue $metadata "${workload}_surface_bounds" ([string]$ExpectedBounds[$workload]) | Out-Null
    }
    # The collector never judges: every measured family's threshold_status is `measured`.
    foreach ($workload in @($state.measured_workload_counts.PSObject.Properties |
                Where-Object { [int]$_.Value -gt 0 } | ForEach-Object { $_.Name })) {
        Assert-P4FrameMetadataValue $metadata "${workload}_threshold_status" 'measured' | Out-Null
    }
    if ((Get-P4FrameMetadataValue -Metadata $metadata -Key 'percentile_method') -cnotmatch '^nearest-rank;') {
        throw 'Frame metadata does not record the nearest-rank percentile method.'
    }
    $status = Get-P4FrameMetadataValue -Metadata $metadata -Key 'status'
    if ($status -cne [string]$state.verdict) { throw 'Frame metadata status and run-state verdict disagree.' }
    $acceptanceLane = Get-P4FrameMetadataValue -Metadata $metadata -Key 'acceptance_lane'
    if ($acceptanceLane -cne $Runner) { throw "Frame acceptance lane '$acceptanceLane' is not '$Runner'." }

    $frames = @(Import-Csv -LiteralPath $framesPath)
    $samples = @(Import-Csv -LiteralPath $samplesPath)
    if ($frames.Count -eq 0 -or $samples.Count -eq 0) { throw 'Frame or sample rows are missing.' }
    if ($phase) {
        $previousOrdinal = 0L; $seenCommit = $false
        foreach ($row in @($frames) + @($samples)) {
            Assert-P4FramePhaseIdentity $row $identity 'Phase raw/sample row'
            if ((Get-P4FrameExactMember $row 'production_commit') -cne $PhaseContext.production_commit) { throw 'Phase row production drifted.' }
        }
        foreach ($row in $frames) {
            foreach ($key in @('operation_ordinal', 'sample_index', 'event_count', 'row_index', 'frame_timeline_vsync_id',
                    'intended_vsync_nanos', 'frame_start_nanos', 'handle_input_start_nanos', 'frame_completed_nanos', 'frame_deadline_nanos')) {
                ConvertTo-P4FrameInteger (Get-P4FrameExactMember $row $key) $key | Out-Null
            }
            ConvertTo-P4FrameInteger (Get-P4FrameExactMember $row 'flags') 'flags' $true | Out-Null
            $currentOrdinal = ConvertTo-P4FrameInteger $row.operation_ordinal 'operation_ordinal'
            if ($currentOrdinal -ne $previousOrdinal) {
                if ($currentOrdinal -ne $previousOrdinal + 1) { throw 'Phase frame operations are lost or reordered.' }
                $previousOrdinal = $currentOrdinal; $seenCommit = $false
            }
            if ($row.phase -ceq 'commit') { $seenCommit = $true }
            elseif ($seenCommit) { throw 'Phase preview rows follow committed rows.' }
        }
        $sampleOrdinal = 0L
        foreach ($row in $samples) {
            $sampleOrdinal++
            if ((ConvertTo-P4FrameInteger (Get-P4FrameExactMember $row 'operation_ordinal') 'operation_ordinal') -ne $sampleOrdinal) {
                throw 'Phase sample operations are lost or reordered.'
            }
            foreach ($key in @('operation_ordinal', 'sample_index', 'motion_event_count', 'preview_event_count', 'commit_event_count',
                    'raw_position_count', 'effective_change_count', 'preview_frame_count', 'preview_raw_frame_count', 'preview_valid_frame_count',
                    'commit_frame_count', 'commit_raw_frame_count', 'commit_valid_frame_count', 'total_frames_rendered', 'janky_frames',
                    'deadline_missed_frames', 'raw_row_count', 'valid_row_count')) {
                ConvertTo-P4FrameInteger (Get-P4FrameExactMember $row $key) $key $true | Out-Null
            }
            if ([long]$row.janky_frames -gt [long]$row.total_frames_rendered -or [long]$row.deadline_missed_frames -gt [long]$row.total_frames_rendered) {
                throw 'Phase sample gfxinfo counters exceed the rendered population.'
            }
            $associationKeys = @('first_preview_frame_timeline_vsync_id', 'first_preview_row_index',
                'first_preview_handle_input_start_nanos', 'first_preview_frame_completed_nanos',
                'first_preview_deadline_nanos', 'first_preview_service_ms', 'first_preview_overrun_ms')
            foreach ($property in $row.PSObject.Properties) {
                if ($property.Name.StartsWith('first_preview_', [StringComparison]::Ordinal)) {
                    if ($property.Name -cnotin $associationKeys -or
                        ($row.workload -cne $contract.association_workload -and -not [string]::IsNullOrEmpty([string]$property.Value))) {
                        throw 'Phase sample contains unknown or orphan first-preview fields.'
                    }
                }
            }
        }
    }
    if (
        [int](Get-P4FrameMetadataValue -Metadata $metadata -Key 'raw_frame_rows') -ne $frames.Count -or
        [int](Get-P4FrameMetadataValue -Metadata $metadata -Key 'valid_frame_rows') -ne $frames.Count -or
        [int](Get-P4FrameMetadataValue -Metadata $metadata -Key 'aggregate_frames_rendered') -ne $frames.Count -or
        [int](Get-P4FrameMetadataValue -Metadata $metadata -Key 'measured_operation_count') -ne $samples.Count -or
        [int]$state.measured_operation_count -ne $samples.Count
    ) {
        throw 'Aggregate frame or operation counts disagree with the retained rows.'
    }
    if (@($frames | ForEach-Object { [long]$_.frame_timeline_vsync_id } | Sort-Object -Unique).Count -ne $frames.Count) {
        throw 'Frame rows repeat a FrameTimeline vsync identity.'
    }
    foreach ($row in $frames) {
        if (
            [int]$row.flags -ne 0 -or
            $row.variant -cne 'release-like' -or
            $row.source_commit -cne $BuildCommit -or
            $row.phase -cnotin @('preview', 'commit')
        ) {
            throw 'A frame row is invalid, foreign or outside the fixed preview/commit phases.'
        }
    }
    foreach ($row in $samples) {
        if (
            $row.source_commit -cne $BuildCommit -or
            $row.variant -cne 'release-like' -or
            [string]$row.committed_ui_verified -cnotin @('True', 'true')
        ) {
            throw 'A sample row is foreign or was not verified against the committed UI.'
        }
    }
    if (($samples | ForEach-Object { [int]$_.raw_row_count } | Measure-Object -Sum).Sum -ne $frames.Count) {
        throw 'Sample raw row counts do not account for every retained frame row.'
    }

    if ($phase) {
        foreach ($key in @('janky_frames', 'deadline_missed_frames')) {
            $metadataKey = "aggregate_$key"
            if ($metadata.Contains($metadataKey)) {
                $sum = [long]0
                foreach ($sample in $samples) { $sum += ConvertTo-P4FrameInteger $sample.$key $key $true }
                if ((ConvertTo-P4FrameInteger (Get-P4FrameMetadataValue $metadata $metadataKey) $metadataKey $true) -ne $sum) {
                    throw "Phase '$metadataKey' disagrees with the sample gfxinfo summaries."
                }
            }
        }
    }

    $presentWorkloads = @($samples | ForEach-Object { $_.workload } | Select-Object -Unique)
    # Decision: a prefix of families 1-2. Diagnostic: a prefix of families 1-3 (a gross regression stops early).
    $expectedPrefix = @($declaredOrder | Select-Object -First $presentWorkloads.Count)
    if (
        $presentWorkloads.Count -lt 1 -or
        $presentWorkloads.Count -gt $declaredOrder.Count -or
        ($presentWorkloads -join '|') -cne ($expectedPrefix -join '|')
    ) {
        throw 'The measured frame families are not a prefix of the fixed workload order.'
    }
    $completeRun = $presentWorkloads.Count -eq $declaredOrder.Count
    if (-not $completeRun -and $Runner -eq 'decision') {
        throw 'A decision frame slot must measure every decision family before the verdict.'
    }
    if (-not $completeRun -and $status -cnotin @('fail', 'gross-regression')) {
        throw 'A frame family is missing without a recorded early stop.'
    }
    if (@($frames | ForEach-Object { $_.workload } | Select-Object -Unique |
                Where-Object { $_ -cnotin $presentWorkloads }).Count -ne 0) {
        throw 'Frame rows pooled a family that produced no measured operations.'
    }

    if ($phase) {
        if ($state.complete_run -ne $completeRun) { throw 'Phase run state completeness disagrees with retained families.' }
        Assert-P4FrameMetadataValue $metadata 'complete_run' $(if ($completeRun) { 'true' } else { 'false' }) | Out-Null
        foreach ($workload in $declaredOrder) {
            $actualCount = @($samples | Where-Object { $_.workload -ceq $workload }).Count
            if ([long]$state.measured_workload_counts.$workload -ne $actualCount -or
                [long](Get-P4FrameMetadataValue $metadata "measured_$workload") -ne $actualCount) { throw 'Phase declared family counts disagree with retained samples.' }
        }
    }
    $families = [ordered]@{}
    $ordinal = 0
    foreach ($workload in $presentWorkloads) {
        $familySamples = @($samples | Where-Object { $_.workload -ceq $workload })
        $familyFrames = @($frames | Where-Object { $_.workload -ceq $workload })
        if ($familySamples.Count -ne $expectedSamples) {
            throw "Family '$workload' retained $($familySamples.Count) operations instead of $expectedSamples."
        }
        $measuredKey = "measured_$workload"
        $stateCount = Get-P4FrameObjectMember -Value $state.measured_workload_counts -Name $workload `
            -Context 'Frame run state measured_workload_counts'
        if (
            [int](Get-P4FrameMetadataValue -Metadata $metadata -Key $measuredKey) -ne $expectedSamples -or
            [int]$stateCount -ne $expectedSamples
        ) {
            throw "Measured operation counts for '$workload' disagree with its retained population."
        }
        $firstServices = [Collections.Generic.List[double]]::new(); $firstOverruns = [Collections.Generic.List[double]]::new()
        $inputValues = [Collections.Generic.List[double]]::new()
        foreach ($sampleIndex in 1..$expectedSamples) {
            $ordinal += 1
            $sample = $familySamples[$sampleIndex - 1]
            if (
                [int]$sample.sample_index -ne $sampleIndex -or
                [int]$sample.operation_ordinal -ne $ordinal -or
                $sample.operation -cne "$workload#$sampleIndex"
            ) {
                throw "Family '$workload' operation $sampleIndex is missing, duplicated or reordered."
            }
            $operationFrames = @($familyFrames | Where-Object { [int]$_.operation_ordinal -eq $ordinal })
            if (
                @($operationFrames | Where-Object { $_.operation -cne "$workload#$sampleIndex" }).Count -gt 0 -or
                $operationFrames.Count -ne [int]$sample.raw_row_count -or
                $operationFrames.Count -ne [int]$sample.valid_row_count
            ) {
                throw "Family '$workload' operation $sampleIndex pooled or lost its frame rows."
            }
            $previewRows = @($operationFrames | Where-Object { $_.phase -ceq 'preview' })
            $commitRows = @($operationFrames | Where-Object { $_.phase -ceq 'commit' })
            if (
                $previewRows.Count -ne [int]$sample.preview_frame_count -or
                $previewRows.Count -ne [int]$sample.preview_valid_frame_count -or
                $commitRows.Count -ne [int]$sample.commit_frame_count -or
                $commitRows.Count -ne [int]$sample.commit_valid_frame_count -or
                $previewRows.Count -lt 1 -or
                $commitRows.Count -lt 1
            ) {
                throw "Family '$workload' operation $sampleIndex lost a preview or committed-result phase."
            }
            if ($phase) {
                $spec = @($contract.workload_catalog | Where-Object { $_.workload -ceq $workload })[0]
                foreach ($key in @('motion_event_count', 'preview_event_count', 'commit_event_count', 'raw_position_count', 'effective_change_count')) {
                    if ((ConvertTo-P4FrameInteger $sample.$key $key) -ne $spec[$key]) { throw "Phase sample '$key' differs from workload event specification." }
                }
                foreach ($entry in @{ preview_raw_frame_count = $previewRows.Count; commit_raw_frame_count = $commitRows.Count
                        total_frames_rendered = $operationFrames.Count }.GetEnumerator()) {
                    if ((ConvertTo-P4FrameInteger $sample.($entry.Key) $entry.Key) -ne $entry.Value) { throw 'Phase sample counts lost retained raw rows.' }
                }
                foreach ($phaseName in @('preview', 'commit')) {
                    $rows = @(if ($phaseName -ceq 'preview') { $previewRows } else { $commitRows })
                    $expectedCapture = [ordered]@{ workload = $workload; operation = "$workload#$sampleIndex"; sample_index = $sampleIndex
                        operation_ordinal = $ordinal; source_commit = $BuildCommit; production_commit = $PhaseContext.production_commit
                        variant = 'release-like'; raw_row_count = $rows.Count }
                    Assert-P4FramePhaseRows -Rows $rows -ExpectedCapture $expectedCapture -Phase $phaseName -EventCount $spec["${phaseName}_event_count"] -FullMetrics
                }
                $timing = Get-P4FrameOperationTiming -PreviewRows $previewRows -CommitRows $commitRows
                foreach ($key in @('preview_input_start_nanos', 'commit_input_start_nanos', 'committed_result_completion_nanos')) {
                    if ((ConvertTo-P4FrameInteger (Get-P4FrameExactMember $sample $key) $key) -ne $timing.$key) { throw "Phase sample '$key' disagrees with raw timing." }
                }
                foreach ($key in @('input_to_committed_result_ms', 'down_to_committed_result_ms')) {
                    $actual = Get-P4FrameExactMember $sample $key
                    $derived = $timing.$key.ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
                    if ((ConvertTo-P4FrameFiniteMetric $actual $key) -cne $derived) { throw "Phase sample '$key' disagrees with raw timing." }
                }
                if ($workload -ceq $contract.association_workload) {
                    $expectedCapture.raw_row_count = $previewRows.Count
                    $association = Get-P4FirstPreviewAssociation -PreviewRows $previewRows -ExpectedCapture $expectedCapture
                    $associationKeys = @($association.PSObject.Properties.Name)
                    foreach ($property in $sample.PSObject.Properties) {
                        if ($property.Name.StartsWith('first_preview_', [StringComparison]::Ordinal) -and $property.Name -cnotin $associationKeys) {
                            throw 'Phase tap sample contains unknown first-preview fields.'
                        }
                    }
                    foreach ($key in $associationKeys) {
                        $actual = Get-P4FrameExactMember $sample $key
                        if ($key.EndsWith('_ms', [StringComparison]::Ordinal)) {
                            if ($actual -isnot [string] -or $actual -cne $association.$key) { throw "Phase sample '$key' disagrees with first-preview raw association." }
                        } elseif ((ConvertTo-P4FrameInteger $actual $key) -ne $association.$key) { throw "Phase sample '$key' disagrees with first-preview raw association." }
                    }
                    $firstServices.Add([double]$association.first_preview_service_ms); $firstOverruns.Add([double]$association.first_preview_overrun_ms)
                }
                $recomputedInput = [double]$timing.input_to_committed_result_ms
            } else { $recomputedInput = Get-P4FrameSampleInput -CommitRows $commitRows }
            if ((Format-P4FrameMetric $recomputedInput) -cne (Format-P4FrameMetric ([double]$sample.input_to_committed_result_ms))) {
                throw "Family '$workload' operation $sampleIndex committed-result latency disagrees with its raw frames."
            }
            $inputValues.Add($recomputedInput)
        }
        $overruns = [double[]]@($familyFrames | ForEach-Object { [double]$_.frame_overrun_ms })
        $inputs = [double[]]$inputValues.ToArray()
        $overrunP95 = Get-P4FrameNearestRank -Values $overruns -Percentile 0.95
        $overrunP99 = Get-P4FrameNearestRank -Values $overruns -Percentile 0.99
        $inputP95 = Get-P4FrameNearestRank -Values $inputs -Percentile 0.95
        $maximumOverrun = ($overruns | Measure-Object -Maximum).Maximum
        $maximumInput = ($inputs | Measure-Object -Maximum).Maximum
        $recomputed = [ordered]@{
            "${workload}_frame_count" = [string]$familyFrames.Count
            "${workload}_frame_overrun_p95_ms" = Format-P4FrameMetric $overrunP95
            "${workload}_frame_overrun_p99_ms" = Format-P4FrameMetric $overrunP99
            "${workload}_input_to_committed_result_p95_ms" = Format-P4FrameMetric $inputP95
            "${workload}_maximum_frame_overrun_ms" = Format-P4FrameMetric $maximumOverrun
            "${workload}_maximum_input_to_committed_result_ms" = Format-P4FrameMetric $maximumInput
        }
        foreach ($key in $recomputed.Keys) {
            $published = Get-P4FrameMetadataValue -Metadata $metadata -Key $key
            if ($published -cne $recomputed[$key]) {
                throw "Recomputed '$key' is '$($recomputed[$key])' but the collector published '$published'."
            }
        }
        # The collector records gross regression only; the analyzer alone owns pass/fail (Lane 3).
        $inputPassed = if ($phase) { (ConvertTo-P4FrameDecimal $recomputed["${workload}_input_to_committed_result_p95_ms"] 'Phase input p95') -le [decimal]$inputGate } else { $inputP95 -le $inputGate }
        $gross = $maximumOverrun -gt $grossOverrun -or $maximumInput -gt $grossInput
        Assert-P4FrameMetadataValue $metadata "${workload}_diagnostic_gross_regression" $(if ($gross) { 'true' } else { 'false' }) | Out-Null
        $baselineP95 = $null; $baselineP99 = $null; $p95Margin = $null; $p99Margin = $null; $relativeStatus = $null
        if ($isRelativeCandidate) {
            # Compare the published six-decimal texts in decimal so the tolerance boundary is exact.
            $reference = $baselineReference.families[$workload]
            $candidateP95 = ConvertTo-P4FrameDecimal $recomputed["${workload}_frame_overrun_p95_ms"] "'$workload' p95"
            $candidateP99 = ConvertTo-P4FrameDecimal $recomputed["${workload}_frame_overrun_p99_ms"] "'$workload' p99"
            $p95Delta = $candidateP95 - $reference.p95
            $p99Delta = $candidateP99 - $reference.p99
            $relativePassed =
                $p95Delta -le [decimal]$relativeP95 -and
                $p99Delta -le [decimal]$relativeP99 -and
                $inputPassed -and
                $fatalMatches -eq 0
            $baselineP95 = $reference.p95.ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
            $baselineP99 = $reference.p99.ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
            $p95Margin = $p95Delta.ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
            $p99Margin = $p99Delta.ToString('F6', [Globalization.CultureInfo]::InvariantCulture)
            $relativeStatus = if ($relativePassed) { 'pass' } else { 'fail' }
        }
        # threshold_status: the relative status for the decision candidate, otherwise the input p95 gate.
        $thresholdStatus = if ($isRelativeCandidate) { $relativeStatus } elseif ($inputPassed) { 'pass' } else { 'fail' }
        $families[$workload] = [ordered]@{
            frame_count = $familyFrames.Count
            operation_count = $expectedSamples
            frame_overrun_p95_ms = $recomputed["${workload}_frame_overrun_p95_ms"]
            frame_overrun_p99_ms = $recomputed["${workload}_frame_overrun_p99_ms"]
            input_to_committed_result_p95_ms = $recomputed["${workload}_input_to_committed_result_p95_ms"]
            maximum_frame_overrun_ms = $recomputed["${workload}_maximum_frame_overrun_ms"]
            maximum_input_to_committed_result_ms = $recomputed["${workload}_maximum_input_to_committed_result_ms"]
            threshold_status = $thresholdStatus
            gross_regression = $gross
            baseline_p95_ms = $baselineP95
            baseline_p99_ms = $baselineP99
            p95_margin_ms = $p95Margin
            p99_margin_ms = $p99Margin
            relative_status = $relativeStatus
        }
        if ($phase -and $workload -ceq $contract.association_workload) {
            $families[$workload].first_preview_operation_count = $firstServices.Count
            $families[$workload].first_preview_service_p95_ms = Format-P4FrameMetric (Get-P4FrameNearestRank $firstServices.ToArray() 0.95)
            $families[$workload].first_preview_overrun_p95_ms = Format-P4FrameMetric (Get-P4FrameNearestRank $firstOverruns.ToArray() 0.95)
            $families[$workload].first_preview_overrun_p99_ms = Format-P4FrameMetric (Get-P4FrameNearestRank $firstOverruns.ToArray() 0.99)
        }
    }

    $failedFamilies = @($families.Keys | Where-Object { $families[$_].threshold_status -cne 'pass' })
    $anyGross = @($families.Keys | Where-Object { $families[$_].gross_regression }).Count -gt 0
    $verdict =
        if ($Runner -eq 'decision' -and $Role -eq 'baseline') {
            if ($fatalMatches -eq 0 -and $failedFamilies.Count -eq 0) { 'baseline-recorded' } else { 'baseline-invalid' }
        }
        elseif ($isRelativeCandidate) {
            if ($failedFamilies.Count -eq 0) { 'pass' } else { 'PERFORMANCE_FAIL' }
        }
        elseif ($anyGross) { 'PERFORMANCE_FAIL' }
        else {
            if (-not $completeRun) { throw 'A diagnostic frame slot stopped early without a gross regression.' }
            'inconclusive'
        }

    $result = [ordered]@{
        verdict = $verdict
        verdict_rule = $verdictRule
        schema = $frameSchema
        experiment_schema = $experimentSchema
        experiment_id = $ExperimentId
        role = $Role
        runner = $Runner
        sequence_index = $SequenceIndex
        attempt = 1
        slot_directory = $root
        collector_status = $status
        acceptance_lane = $acceptanceLane
        build_commit = $BuildCommit
        apk_sha256 = $ExpectedApkSha256
        geometry_id = $geometryId
        warmups_per_workload = $warmups
        samples_per_workload = $expectedSamples
        measured_operation_count = $samples.Count
        raw_frame_rows = $frames.Count
        valid_frame_rows = $frames.Count
        fatal_anr_matches = $fatalMatches
        complete_run = $completeRun
        baseline_analysis_path = if ($isRelativeCandidate) { $baselineReference.path } else { $null }
        failed_families = if ($isRelativeCandidate) { $failedFamilies } else { $null }
        families = $families
        frame_files = @(Get-P4FrameFileInventory -SlotDirectory $root)
    }
    if ($phase) {
        foreach ($key in $identity.Keys) { $result[$key] = $identity[$key] }
        $result.production_commit = $PhaseContext.production_commit
        $result.experiment_sha256 = $experimentSha256
    }
    return $result
}

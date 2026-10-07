[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$source = Join-Path $PSScriptRoot 'measurements/p4-indexed-frame-analysis.ps1'
$script:assertions = 0
function Assert-Association {
    param([bool]$Condition, [string]$Name)
    if (-not $Condition) { throw "FAIL: $Name" }
    $script:assertions += 1
}
foreach ($path in @($source, $PSCommandPath)) {
    $tokens = $null
    $errors = $null
    [Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors) | Out-Null
    Assert-Association ($errors.Count -eq 0) "AST parse: $path"
}
. $source

function New-ExpectedCapture {
    return [ordered]@{
        workload = 'canvas256_layers16_tap'; operation = 'canvas256_layers16_tap#7'
        operation_ordinal = 57; sample_index = 7; source_commit = 'a' * 40
        production_commit = 'b' * 40; variant = 'release-like'; raw_row_count = 2
    }
}
function New-PreviewRows {
    return @(
        [pscustomobject][ordered]@{
            workload = 'canvas256_layers16_tap'; operation = 'canvas256_layers16_tap#7'
            operation_ordinal = 57; sample_index = 7; source_commit = 'a' * 40
            production_commit = 'b' * 40; variant = 'release-like'; phase = 'preview'
            event_count = 1; flags = 0; row_index = 1; frame_timeline_vsync_id = 91
            intended_vsync_nanos = 1000000001L; frame_start_nanos = 1001000001L
            handle_input_start_nanos = 1002000001L; frame_completed_nanos = 1012000001L
            frame_deadline_nanos = 1013000001L; input_start_to_completion_ms = '10'
            frame_overrun_ms = '-1'
        },
        [pscustomobject][ordered]@{
            workload = 'canvas256_layers16_tap'; operation = 'canvas256_layers16_tap#7'
            operation_ordinal = 57; sample_index = 7; source_commit = 'a' * 40
            production_commit = 'b' * 40; variant = 'release-like'; phase = 'preview'
            event_count = 1; flags = 0; row_index = 2; frame_timeline_vsync_id = 92
            intended_vsync_nanos = 1014000001L; frame_start_nanos = 1015000001L
            handle_input_start_nanos = 1016000001L; frame_completed_nanos = 1017000001L
            frame_deadline_nanos = 1018000001L; input_start_to_completion_ms = '1.000000'
            frame_overrun_ms = '-1.000000'
        }
    )
}
function Assert-Refused {
    param([object[]]$Rows, [System.Collections.IDictionary]$Expected, [string]$Name)
    $refused = $false
    try { Get-P4FirstPreviewAssociation -PreviewRows $Rows -ExpectedCapture $Expected | Out-Null }
    catch { $refused = $true }
    Assert-Association $refused $Name
}
function Assert-RowRefused {
    param([string]$Field, $Value, [string]$Name, [int]$Index = 1)
    $rows = New-PreviewRows
    $rows[$Index].$Field = $Value
    Assert-Refused $rows (New-ExpectedCapture) $Name
}

$rows = New-PreviewRows
$before = ConvertTo-Json -InputObject $rows -Depth 8 -Compress
$expected = New-ExpectedCapture
$expectedBefore = ConvertTo-Json -InputObject $expected -Compress
$result = Get-P4FirstPreviewAssociation -PreviewRows $rows -ExpectedCapture $expected
foreach ($field in @{
        first_preview_frame_timeline_vsync_id = 91L; first_preview_row_index = 1L
        first_preview_handle_input_start_nanos = 1002000001L
        first_preview_frame_completed_nanos = 1012000001L; first_preview_deadline_nanos = 1013000001L
        first_preview_service_ms = '10.000000'; first_preview_overrun_ms = '-1.000000'
    }.GetEnumerator()) {
    Assert-Association ($result.($field.Key) -ceq $field.Value) "first own-row $($field.Key)"
}
Assert-Association ($result.PSObject.Properties.Name.Count -eq 7) 'only declared first-preview fields'
Assert-Association ((ConvertTo-Json -InputObject $rows -Depth 8 -Compress) -ceq $before) 'rows are unchanged'
Assert-Association ((ConvertTo-Json -InputObject $expected -Compress) -ceq $expectedBefore) 'expected identity unchanged'

# A single row, equal intended/start/input, positive and zero overrun are valid boundaries.
$expected = New-ExpectedCapture
$expected.raw_row_count = 1
$rows = @((New-PreviewRows)[0])
$rows[0].frame_start_nanos = $rows[0].intended_vsync_nanos
$rows[0].handle_input_start_nanos = $rows[0].intended_vsync_nanos
$rows[0].input_start_to_completion_ms = '12'
$rows[0].frame_deadline_nanos = 1011000001L
$rows[0].frame_overrun_ms = '1'
$result = Get-P4FirstPreviewAssociation $rows $expected
Assert-Association ($result.first_preview_overrun_ms -ceq '1.000000') 'positive overrun / equal start boundary'
$rows[0].frame_deadline_nanos = $rows[0].frame_completed_nanos
$rows[0].frame_overrun_ms = 0.0
Assert-Association ((Get-P4FirstPreviewAssociation $rows $expected).first_preview_overrun_ms -ceq '0.000000') 'zero overrun'

# Lossless subtraction near Int64.MaxValue and exactly one nanosecond of service/negative overrun.
$rows[0].intended_vsync_nanos = '9223372036854775803'
$rows[0].frame_start_nanos = '9223372036854775804'
$rows[0].handle_input_start_nanos = '9223372036854775805'
$rows[0].frame_completed_nanos = '9223372036854775806'
$rows[0].frame_deadline_nanos = '9223372036854775807'
$rows[0].input_start_to_completion_ms = '1e-6'
$rows[0].frame_overrun_ms = '-1E-6'
$result = Get-P4FirstPreviewAssociation $rows $expected
Assert-Association ($result.first_preview_service_ms -ceq '0.000001') 'one ns service near Int64 limit'
Assert-Association ($result.first_preview_overrun_ms -ceq '-0.000001') 'negative one ns scientific metric'

# Raw metrics need not have fixed text precision, but comparison is F6, including signed midpoints.
foreach ($case in @(
        @{ field = 'input_start_to_completion_ms'; nanos = 1000001L; value = '1.0000014'; valid = $true },
        @{ field = 'input_start_to_completion_ms'; nanos = 1000001L; value = '1.0000015'; valid = $false },
        @{ field = 'input_start_to_completion_ms'; nanos = 1000001L; value = '1.000000'; valid = $false },
        @{ field = 'frame_overrun_ms'; nanos = 1000001L; value = '-1.0000014'; valid = $true },
        @{ field = 'frame_overrun_ms'; nanos = 1000001L; value = '-1.0000015'; valid = $false },
        @{ field = 'frame_overrun_ms'; nanos = 1000001L; value = '-1.000000'; valid = $false }
    )) {
    $rows = @((New-PreviewRows)[0])
    if ($case.field -ceq 'input_start_to_completion_ms') {
        $rows[0].handle_input_start_nanos = $rows[0].frame_completed_nanos - $case.nanos
    } else { $rows[0].frame_deadline_nanos = $rows[0].frame_completed_nanos + $case.nanos }
    $rows[0].($case.field) = $case.value
    if ($case.valid) {
        $result = Get-P4FirstPreviewAssociation $rows $expected
        Assert-Association ($null -ne $result) "six-decimal accepted $($case.value)"
    } else { Assert-Refused $rows $expected "six-decimal refused $($case.value)" }
}

Assert-Refused @() (New-ExpectedCapture) 'empty capture'
Assert-Refused $null (New-ExpectedCapture) 'null capture'
Assert-Refused @((New-PreviewRows)[0]) (New-ExpectedCapture) 'lost second row'
Assert-Refused @((New-PreviewRows)[0], $null) (New-ExpectedCapture) 'null later row'
foreach ($count in @(0, 1, 3, '2.5', '9223372036854775808', $true, 2.0)) {
    $expected = New-ExpectedCapture
    $expected.raw_row_count = $count
    Assert-Refused (New-PreviewRows) $expected "expected count $count"
}
foreach ($field in @('workload', 'operation', 'source_commit', 'production_commit', 'variant',
        'operation_ordinal', 'sample_index', 'phase', 'event_count', 'flags', 'row_index',
        'frame_timeline_vsync_id', 'intended_vsync_nanos', 'frame_start_nanos',
        'handle_input_start_nanos', 'frame_completed_nanos', 'frame_deadline_nanos',
        'input_start_to_completion_ms', 'frame_overrun_ms')) {
    $rows = New-PreviewRows
    $rows[1].PSObject.Properties.Remove($field)
    Assert-Refused $rows (New-ExpectedCapture) "missing later-row $field"
}
foreach ($field in @('workload', 'operation', 'source_commit', 'production_commit', 'variant')) {
    Assert-RowRefused $field 'foreign' "foreign $field"
}
foreach ($field in @('operation_ordinal', 'sample_index')) {
    Assert-RowRefused $field 8 "foreign $field"
}
foreach ($value in @('commit', 'Preview', '', $null)) { Assert-RowRefused 'phase' $value "phase '$value'" }
Assert-RowRefused 'phase' @('preview') 'phase must be scalar text'
Assert-RowRefused 'phase' @() 'phase must not be an empty array'
Assert-RowRefused 'event_count' @(1) 'event count must be a scalar integer'
Assert-RowRefused 'frame_timeline_vsync_id' @('92') 'frame identity must be a scalar integer'
foreach ($value in @(0, 2, 17)) { Assert-RowRefused 'event_count' $value "event count $value" }
Assert-RowRefused 'flags' 1 'flagged later row'
Assert-RowRefused 'row_index' 1 'duplicate row index'
Assert-RowRefused 'row_index' 3 'index gap'
Assert-RowRefused 'row_index' 2 'non-one first index' 0
Assert-RowRefused 'frame_timeline_vsync_id' 91 'duplicate frame identity'
Assert-Refused @((New-PreviewRows)[1], (New-PreviewRows)[0]) (New-ExpectedCapture) 'reordered original rows'

foreach ($field in @('operation_ordinal', 'sample_index', 'event_count', 'flags', 'row_index',
        'frame_timeline_vsync_id', 'intended_vsync_nanos', 'frame_start_nanos',
        'handle_input_start_nanos', 'frame_completed_nanos', 'frame_deadline_nanos')) {
    foreach ($bad in @($null, $true, '1.5', 1.5, 1.0, [decimal]1.5, '-1',
            '9223372036854775808', '1e3', ' 1', '1,000', 'NaN')) {
        Assert-RowRefused $field $bad "invalid integer $field/$bad"
    }
    if ($field -cne 'flags') { Assert-RowRefused $field 0 "nonpositive integer $field" }
}
foreach ($field in @('intended_vsync_nanos', 'frame_start_nanos', 'handle_input_start_nanos',
        'frame_completed_nanos')) {
    foreach ($offset in @(0L, -1L)) {
        $rows = New-PreviewRows
        # Keep all within-row constraints valid, so each independent inter-row ordering guard is exercised.
        $rows[1].intended_vsync_nanos = 1004000001L
        $rows[1].frame_start_nanos = 1005000001L
        $rows[1].handle_input_start_nanos = 1006000001L
        $rows[1].frame_completed_nanos = 1014000001L
        $rows[1].frame_deadline_nanos = 1015000001L
        $rows[1].$field = $rows[0].$field + $offset
        if ($field -ceq 'frame_start_nanos') { $rows[1].intended_vsync_nanos = 1000000002L }
        if ($field -ceq 'handle_input_start_nanos') {
            $rows[1].intended_vsync_nanos = 1000000002L; $rows[1].frame_start_nanos = 1001000002L
        }
        $rows[1].input_start_to_completion_ms =
            ([decimal]($rows[1].frame_completed_nanos - $rows[1].handle_input_start_nanos) / 1000000).ToString(
                'F6', [Globalization.CultureInfo]::InvariantCulture)
        $rows[1].frame_overrun_ms =
            ([decimal]($rows[1].frame_completed_nanos - $rows[1].frame_deadline_nanos) / 1000000).ToString(
                'F6', [Globalization.CultureInfo]::InvariantCulture)
        Assert-Refused $rows (New-ExpectedCapture) "inter-row tie/reorder $field/$offset"
    }
}
foreach ($pair in @(
        @('intended_vsync_nanos', 1016000002L), @('frame_start_nanos', 1016000002L),
        @('handle_input_start_nanos', 1017000001L), @('frame_completed_nanos', 1016000001L),
        @('frame_deadline_nanos', 1014000001L), @('frame_deadline_nanos', 1014000000L)
    )) { Assert-RowRefused $pair[0] $pair[1] "within-row order $($pair[0])/$($pair[1])" }
foreach ($field in @('input_start_to_completion_ms', 'frame_overrun_ms')) {
    [object[]]$singleMetric = if ($field -ceq 'frame_overrun_ms') { '-1.000000' } else { '1.000000' }
    Assert-RowRefused $field $singleMetric "metric must be scalar $field"
    Assert-RowRefused $field @() "metric must not be an empty array $field"
    foreach ($bad in @($null, $true, 'NaN', 'Infinity', '-Infinity', '1,000', '1,5', '1e1000',
            '1.000001', '-1.000001', [double]::NaN, [double]::PositiveInfinity)) {
        Assert-RowRefused $field $bad "invalid/altered metric $field/$bad"
    }
}
$rows = New-PreviewRows
$rows[0].input_start_to_completion_ms = '15' # first input plus the other row's completion
Assert-Refused $rows (New-ExpectedCapture) 'cross-row timestamp pairing'
foreach ($field in @('workload', 'operation', 'operation_ordinal', 'sample_index', 'source_commit',
        'production_commit', 'variant', 'raw_row_count')) {
    $expected = New-ExpectedCapture
    $expected.Remove($field)
    Assert-Refused (New-PreviewRows) $expected "missing expected $field"
}
foreach ($pair in @(
        @('workload', 'canvas256_layers16_repeated_diagonal'), @('workload', 'canvas16_tap'),
        @('variant', 'debug'), @('operation', 'canvas256_layers16_tap#07'),
        @('source_commit', ('g' * 40)), @('production_commit', ('a' * 39)),
        @('operation_ordinal', 0), @('sample_index', 0), @('operation_ordinal', 1.5),
        @('sample_index', '7.5'), @('source_commit', $null), @('variant', $true)
    )) {
    $expected = New-ExpectedCapture
    $expected[$pair[0]] = $pair[1]
    $rows = New-PreviewRows
    foreach ($row in $rows) { $row.($pair[0]) = $pair[1] }
    Assert-Refused $rows $expected "equally foreign expected and rows $($pair[0])/$($pair[1])"
}
Write-Output "PASS: first-preview association $script:assertions assertions; device-free synthetic/AST only."

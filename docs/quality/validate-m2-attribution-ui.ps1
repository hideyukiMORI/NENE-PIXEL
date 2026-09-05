[CmdletBinding()]
param([string]$RetainedRawFramePath)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# Load the actual assertions without running the device collector's top-level entry.
$collector = Join-Path $PSScriptRoot "measurements/collect-m2-commit-front-half-attribution.ps1"
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($collector, [ref]$tokens, [ref]$errors)
if ($errors.Count -ne 0) { throw "Collector syntax is invalid." }
foreach ($name in @("Assert-CleanUi", "Assert-CommittedUi", "Get-FrameRows")) {
    $functions = @($ast.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] }, $true) |
        Where-Object Name -eq $name)
    if ($functions.Count -ne 1) { throw "Expected one canonical $name assertion." }
    . ([scriptblock]::Create($functions[0].Extent.Text))
}

function New-UiFixture {
    param([string]$Dirty, [string]$Undo, [string]$Redo)

    return [xml]@"
<hierarchy><node>
  <node content-desc="Document dirty status" text="$Dirty" />
  <node enabled="$Undo"><node text="Undo" /></node>
  <node enabled="$Redo"><node text="Redo" /></node>
  <node checked="true"><node content-desc="Pencil tool" /></node>
  <node content-desc="16 by 16 pixel canvas" bounds="[0,0][160,160]" />
</node></hierarchy>
"@
}

function Assert-Rejected {
    param([scriptblock]$Action)

    try { & $Action | Out-Null } catch { return }
    throw "An invalid UI checkpoint was accepted."
}

$initial = New-UiFixture "No unsaved changes" "false" "false"
$undone = New-UiFixture "No unsaved changes" "false" "true"
$committed = New-UiFixture "Unsaved changes" "true" "false"
Assert-CleanUi -Ui $initial | Out-Null
Assert-CleanUi -Ui $undone -AfterUndo | Out-Null
Assert-CommittedUi -Ui $committed
Assert-Rejected { Assert-CleanUi -Ui $initial -AfterUndo }
Assert-Rejected { Assert-CleanUi -Ui $undone }
Assert-Rejected { Assert-CleanUi -Ui $committed -AfterUndo }
Assert-Rejected { Assert-CommittedUi -Ui $undone }
Assert-Rejected { Assert-CleanUi -Ui ([xml]' <hierarchy><node text="PIN" /></hierarchy>') }
Write-Output "Attribution UI checkpoint regression: PASS (3 valid, 5 rejected)"

# Real gfxinfo output includes empty lines before the PROFILEDATA section. Preserve them at binding.
$headers = @("Flags", "FrameTimelineVsyncId", "IntendedVsync", "FrameStartTime", "HandleInputStart",
    "AnimationStart", "PerformTraversalsStart", "DrawStart", "SyncQueued", "SyncStart",
    "IssueDrawCommandsStart", "SwapBuffers", "SwapBuffersCompleted", "FrameDeadline", "FrameCompleted")
$values = @("0", "123", "100", "101", "102", "103", "104", "105", "106", "107", "108", "109", "110", "120", "125")
$raw = @("Applications Graphics Acceleration Info:", "", "Total frames rendered: 1", "",
    "---PROFILEDATA---", ($headers -join ','), ($values -join ','), "---PROFILEDATA---", "")
$rows = @(Get-FrameRows -Text $raw -Sample 1 -Phase preview)
if ($rows.Count -ne 1 -or $rows[0].frame_timeline_vsync_id -ne 123 -or $rows[0].frame_overrun_ms -ne 0.000005) {
    throw "Blank-containing gfxinfo was not preserved exactly."
}
Assert-Rejected { Get-FrameRows -Text ($raw -replace 'Total frames rendered: 1', 'Total frames rendered: 2') -Sample 1 -Phase preview }
$flagged = $raw.Clone()
$flagged[6] = '1,' + (($values | Select-Object -Skip 1) -join ',')
Assert-Rejected { Get-FrameRows -Text $flagged -Sample 1 -Phase preview }
if ($RetainedRawFramePath) {
    $actualRows = @(Get-FrameRows -Text ([string[]](Get-Content -LiteralPath $RetainedRawFramePath)) -Sample 1 -Phase preview)
    Write-Output "Retained raw parser regression: PASS ($($actualRows.Count) rows; invalid run remains invalid)"
}
Write-Output "Attribution raw-frame regression: PASS (blank lines accepted; cardinality and flags rejected)"

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# Load the actual assertions without running the device collector's top-level entry.
$collector = Join-Path $PSScriptRoot "measurements/collect-m2-commit-front-half-attribution.ps1"
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($collector, [ref]$tokens, [ref]$errors)
if ($errors.Count -ne 0) { throw "Collector syntax is invalid." }
foreach ($name in @("Assert-CleanUi", "Assert-CommittedUi")) {
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

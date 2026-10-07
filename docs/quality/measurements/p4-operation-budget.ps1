# Optional phase-owned operation clock. No native action is performed by this helper.
Set-StrictMode -Version Latest

function New-P4OperationBudget {
    param([Parameter(Mandatory)][ValidateRange(1, 3600)][int] $TimeoutSeconds)
    return [ordered]@{ schema = 'nene-pixel-p4-operation-budget-v1'; timeout_seconds = $TimeoutSeconds;
        timer = [Diagnostics.Stopwatch]::StartNew() }
}

function Get-P4OperationBudget([Collections.IDictionary] $Context) {
    if (-not $Context.Contains('operation_budget')) { return $null }
    $budget = $Context.operation_budget
    if ($budget -isnot [Collections.IDictionary] -or $budget.Count -ne 3) {
        throw 'Malformed phase operation budget'
    }
    foreach ($key in @('schema', 'timeout_seconds', 'timer')) {
        if (-not $budget.Contains($key)) { throw 'Malformed phase operation budget keys' }
    }
    if ($budget.schema -isnot [string] -or $budget.schema -cne 'nene-pixel-p4-operation-budget-v1' -or
        $budget.timeout_seconds -isnot [int] -or $budget.timeout_seconds -lt 1 -or
        $budget.timeout_seconds -gt 3600 -or $budget.timer -isnot [Diagnostics.Stopwatch] -or
        -not $budget.timer.IsRunning) { throw 'Malformed phase operation clock' }
    return $budget
}

function ConvertTo-P4OperationTimeout {
    param([ValidateRange(1, 3600)][int] $TimeoutSeconds, [double] $ElapsedSeconds,
        [ValidateRange(1, 3600)][int] $MaximumSeconds)
    if ([double]::IsNaN($ElapsedSeconds) -or [double]::IsInfinity($ElapsedSeconds) -or
        $ElapsedSeconds -lt 0) { throw 'Invalid phase operation elapsed time' }
    $remaining = [Math]::Floor($TimeoutSeconds - $ElapsedSeconds - 15)
    if ($remaining -lt 1) { throw 'Phase operation has no native time remaining (15 seconds reserved)' }
    return [int][Math]::Min($MaximumSeconds, $remaining)
}

function Get-P4OperationTimeout([Collections.IDictionary] $Context,
    [ValidateRange(1, 3600)][int] $MaximumSeconds) {
    $budget = Get-P4OperationBudget $Context
    if ($null -eq $budget) { return $MaximumSeconds }
    return ConvertTo-P4OperationTimeout $budget.timeout_seconds $budget.timer.Elapsed.TotalSeconds $MaximumSeconds
}

function Assert-P4OperationActive([Collections.IDictionary] $Context) {
    $budget = Get-P4OperationBudget $Context
    if ($null -ne $budget -and $budget.timer.Elapsed.TotalSeconds -ge $budget.timeout_seconds) {
        throw 'Phase operation time expired'
    }
}

function Assert-P4OperationInventoryLimit([Collections.IDictionary] $Context, [int] $Count) {
    $budget = Get-P4OperationBudget $Context
    if ($null -eq $budget) { return }
    Assert-P4OperationActive $Context
    if ($Count -lt 0 -or $Count -gt 4096) { throw 'Phase private inventory exceeds the 4096-entry limit' }
}

[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/invoke-p4-indexed-slot.ps1') -ManifestPath fixture -SlotId fixture

# All subprocess and device boundaries below are synthetic, local test replacements.
# Actual artifact admission is covered separately; no ADB, JVM collector or Git operation runs.
$script:boundaryMode = 'success'
$script:restored = $false
$script:slotFixture = [ordered]@{ id = 'host-project-baseline'; lane = 'host'; role = 'baseline'; runner = 'project'; timeout_seconds = 180 }
function Assert-P4ManifestArtifacts { param($Manifest, $RepositoryRoot) }
function Get-P4SlotCatalog { return @($script:slotFixture) }
function Get-P4OriginalDeviceSettings { param($Manifest, $Directory); return @{ synthetic = $true } }
function Stop-P4RemoteProcesses { param($Manifest, $Slot, $Directory); throw 'SYNTHETIC_REMOTE_STOP_FAILURE' }
function Restore-P4OriginalDeviceSettings { param($Manifest, $Directory, $Original); $script:restored = $true }
function Invoke-BoundedNativeCommand {
    param($RepositoryRoot, $ExecutablePath, $LogPath, $TimeoutSeconds, $NativeArguments)
    if ($NativeArguments[2] -eq 'synthetic-collector') { return @{ ExitCode = 0; OutputLines = @('SYNTHETIC_COLLECTOR_ONLY') } }
    if ($NativeArguments[2] -ne 'synthetic-analyzer') { throw 'Unexpected synthetic native boundary.' }
    $path = Join-Path (Split-Path -Parent $LogPath) 'analysis.json'
    if ($script:boundaryMode -ne 'missing-analysis') {
        if ($script:boundaryMode -eq 'corrupt-analysis') { Write-NewInvocationFile $path '{invalid' }
        else {
            $manifest = $NativeArguments[4]
            $result = [ordered]@{ schema = 'nene-pixel-p4-project-format-host-v1'; protocol_id = $script:P4ProtocolId;
                slot_id = $script:slotFixture.id; preflight_sha256 = Get-FileSha256 $manifest;
                role = $script:slotFixture.role; verdict = 'valid-descriptive'; created_utc = [datetime]::UtcNow.ToString('o') }
            if ($script:boundaryMode -eq 'wrong-identity') { $result.slot_id = 'another-slot' }
            if ($script:boundaryMode -eq 'wrong-verdict') { $result.verdict = 'pass' }
            if ($script:boundaryMode -eq 'remote-stop-fails') { $result.verdict = 'pass' }
            Write-NewInvocationFile $path ($result | ConvertTo-Json)
        }
    }
    return @{ ExitCode = 0; OutputLines = @('SYNTHETIC_ANALYZER_ONLY') }
}

$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('nene-p4-slot-fixture-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
foreach ($case in @('success', 'missing-analysis', 'corrupt-analysis', 'wrong-identity', 'wrong-verdict', 'remote-stop-fails')) {
    $script:boundaryMode = $case
    $script:restored = $false
    $script:slotFixture.lane = if ($case -eq 'remote-stop-fails') { 'memory' } else { 'host' }
    $directory = Join-Path $testRoot $case
    New-Item -ItemType Directory -Path $directory | Out-Null
    $manifest = [ordered]@{ output_directory = $directory;
        roles = @{ baseline = @{ worktree = $directory }; candidate = @{ worktree = $directory } };
        tools = @{ runner = @{ path = 'synthetic-collector' }; analyzer = @{ path = 'synthetic-analyzer' } } }
    $manifestPath = Join-Path $directory 'preflight.json'
    Write-NewInvocationFile $manifestPath ($manifest | ConvertTo-Json -Depth 8)
    $rejected = $false
    try { Invoke-P4IndexedSlot $manifestPath $script:slotFixture.id | Out-Null } catch { $rejected = $true }
    $slotRoot = Join-Path $directory $script:slotFixture.id
    if ($case -eq 'success') {
        if ($rejected -or -not (Test-Path -LiteralPath (Join-Path $slotRoot 'completed.json'))) { throw 'Synthetic valid slot failed.' }
        $filesBefore = @(Get-ChildItem -LiteralPath $slotRoot -File | Sort-Object Name | ForEach-Object { "$($_.Name):$(Get-FileSha256 $_.FullName)" })
        $retryRejected = $false
        try { Invoke-P4IndexedSlot $manifestPath $script:slotFixture.id | Out-Null } catch { $retryRejected = $true }
        $filesAfter = @(Get-ChildItem -LiteralPath $slotRoot -File | Sort-Object Name | ForEach-Object { "$($_.Name):$(Get-FileSha256 $_.FullName)" })
        if (-not $retryRejected -or ($filesBefore -join '|') -cne ($filesAfter -join '|')) { throw 'Retry altered accepted evidence.' }
    } else {
        if (-not $rejected -or -not (Test-Path -LiteralPath (Join-Path $slotRoot 'invalid.json')) -or
            (Test-Path -LiteralPath (Join-Path $slotRoot 'completed.json'))) { throw "Failed slot lost invalid evidence: $case" }
        if ($case -eq 'remote-stop-fails' -and -not $script:restored) { throw 'Remote stop failure prevented independent settings restoration.' }
    }
}
Write-Output 'P4_SLOT_BOUNDARY_VALIDATION=pass'
Write-Output 'CASES=completed,immutable-retry,missing-analysis,corrupt-analysis,wrong-identity,wrong-verdict,stop-failure-independent-restoration'
Write-Output "SYNTHETIC_FIXTURE_DIRECTORY=$testRoot"

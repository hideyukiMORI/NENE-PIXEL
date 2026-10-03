# Device-free collector contract and CSV writer checks for #145. No adb or acceptance samples.
param([string]$OutputDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Get-NenePixelLabPath ('evidence/145-frame-collector-contract/' +
        (Get-Date -Format 'yyyyMMddTHHmmssfff') + '-' + [guid]::NewGuid().ToString('N')) -StartDirectory $PSScriptRoot
}
$evidenceRoot = [IO.Path]::GetFullPath((Get-NenePixelLabPath 'evidence' -StartDirectory $PSScriptRoot)).TrimEnd('\', '/')
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $OutputDirectory.StartsWith($evidenceRoot + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $OutputDirectory)) {
    throw 'Collector fixtures require a fresh development lab evidence directory.'
}
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$collector = Join-Path $PSScriptRoot 'measurements/measure-m2-frame.ps1'
$fixtureSource = Join-Path $PSScriptRoot 'validate-m2-frame-protocol.ps1'
$temporaryRoot = Join-Path $OutputDirectory 'fixtures'
$phase = 'nene-pixel-p4-layer-phase-verification-v1'
$legacy = 'nene-pixel-p4-indexed-cutover-verification-v7'
$script:checks = 0
$timer = [Diagnostics.Stopwatch]::StartNew()
$result = [ordered]@{
    schema = 'nene-pixel-p4-frame-collector-contract-validator-v1'; status = 'FAIL'; checks = 0
    elapsed_seconds = 0; runtime = $PSVersionTable.PSVersion.ToString(); source_sha256 = [ordered]@{}
    command = 'pwsh -NoProfile -File docs/quality/validate-p4-frame-collector-contract.ps1'
}
function Check([bool]$Condition, [string]$Name) {
    if (-not $Condition) { throw "Collector contract: $Name" }; $script:checks++
}
function Refuses([scriptblock]$Action, [string]$Name) {
    $refused = $false
    try { & $Action | Out-Null } catch { $refused = $true }
    Check $refused $Name
}
function Load-Functions([string]$Path, [string[]]$Names) {
    $tokens = $null; $errors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile($Path, [ref]$tokens, [ref]$errors)
    Check ($errors.Count -eq 0) "AST $Path"
    foreach ($name in $Names) {
        $nodes = @($ast.FindAll({ param($node)
            $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $name
        }, $true))
        Check ($nodes.Count -eq 1) "one source function $name"
        # Return declarations for the caller's scope; the executable collector body is never loaded.
        $nodes[0].Extent.Text
    }
}
try {
    foreach ($path in @($PSCommandPath, $collector, $fixtureSource,
            (Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1'),
            (Join-Path $PSScriptRoot 'measurements/p4-indexed-frame-analysis.ps1'),
            (Join-Path $PSScriptRoot 'baseline-profile-evidence.ps1'),
            (Join-Path $PSScriptRoot 'measurements/m2-package-dexopt.ps1'),
            (Join-Path $PSScriptRoot 'measurements/android-window-state.ps1'),
            (Join-Path $PSScriptRoot 'measurements/p4-indexed-device-state.ps1'),
            (Join-Path $PSScriptRoot 'bounded-native-command.ps1'))) {
        $result.source_sha256[$path] = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    # Reuse only the old validator's static profile fixture setup, ending before its test entry point.
    # Its device mocks, assertions and cleanup are never evaluated. Every fixture is retained here.
    $fixtureText = Get-Content -LiteralPath $fixtureSource -Raw
    $start = $fixtureText.IndexOf('$baselineCommit =', [StringComparison]::Ordinal)
    $end = $fixtureText.IndexOf('function Invoke-ExpectedPass', [StringComparison]::Ordinal)
    Check ($start -ge 0 -and $end -gt $start) 'existing fixture setup boundary'
    $qualityRoot = $PSScriptRoot
    . ([scriptblock]::Create($fixtureText.Substring($start, $end - $start).Replace('$PSScriptRoot', '$qualityRoot')))
    . (Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1')
    . (Join-Path $PSScriptRoot 'measurements/p4-indexed-frame-analysis.ps1')
    $names = @('Get-RunState', 'Test-RunStateIdentity', 'Get-OperationTiming', 'Complete-FrameSampleRecord',
        'Get-WorkloadSpec', 'Get-FrameRows', 'Get-MeasuredOperationCount', 'Write-RunState')
    $declarations = @(Load-Functions $collector $names)
    . ([scriptblock]::Create($declarations -join "`n"))
    $Variant = 'release-like'; $Attempt = 1
    foreach ($protocol in @($legacy, $phase)) {
        $isLayerPhase = $protocol -ceq $phase
        $root = Join-Path $temporaryRoot $(if ($isLayerPhase) { 'phase' } else { 'legacy' })
        $ExperimentId = if ($isLayerPhase) { 'offline-layer-collector' } else { 'offline-legacy-collector' }
        foreach ($slot in @(Get-P4FrameSlotCatalog -ProtocolId $protocol)) {
            $frameContract = Get-P4FrameExecutionContract -ProtocolId $protocol -SlotId $slot.id
            $ComparisonSequenceIndex = $frameContract.sequence_index
            $CandidateRole = $frameContract.role; $RunKind = $frameContract.runner
            $SourceCommit = if ($CandidateRole -ceq 'baseline') { $baselineCommit } else { $candidateCommit }
            $BaselineProductionCommit = $frameContract.baseline_production_commit
            $CandidateProductionCommit = $candidateCommit
            $PhaseContext = [ordered]@{
                protocol_id = $phase; slot_id = $slot.id; preflight_sha256 = '8' * 64
                production_commit = if ($CandidateRole -ceq 'baseline') { $BaselineProductionCommit } else { $CandidateProductionCommit }
                baseline_reference = $null
            }
            if ($CandidateRole -ceq 'candidate' -and $RunKind -ceq 'decision') {
                $PhaseContext.baseline_reference = [ordered]@{
                    build_commit = $baselineCommit; production_commit = $BaselineProductionCommit
                    apk_sha256 = $baselineHash; analysis_sha256 = '7' * 64; capture_seal_sha256 = '9' * 64
                }
            }
            $arguments = $common.Clone()
            $arguments.ExperimentDirectory = $root; $arguments.ExperimentId = $ExperimentId
            $arguments.BaselineProductionCommit = $BaselineProductionCommit
            $arguments += @{ Variant = $Variant; CompilationMode = 'speed-profile'; SourceCommit = $SourceCommit
                CandidateRole = $CandidateRole; RunKind = $RunKind; ComparisonSequenceIndex = $ComparisonSequenceIndex
                SampleCount = $frameContract.samples }
            if ($isLayerPhase) { $arguments.PhaseContext = $PhaseContext }
            $actual = @(& $collector @arguments)
            Check ($actual.Count -eq 1 -and $actual[0].validation -ceq 'pass') "$protocol slot $ComparisonSequenceIndex invocation"
            Check ($actual[0].model_input_to_committed_result_ms -eq 25 -and
                $actual[0].model_down_to_committed_result_ms -eq 145) 'intentional preview dwell remains excluded'
            Check ($actual[0].slot -ceq $frameContract.frame_directory_name.Replace('-attempt-1', '')) 'directory identity'
            $experimentSchema = $frameContract.experiment_schema
            $experimentManifestSha256 = Get-FileSha256 (Join-Path $root 'experiment.json')
            $manifest = Get-Content -LiteralPath (Join-Path $root 'experiment.json') -Raw | ConvertFrom-Json -NoEnumerate
            Check ($manifest.schema -ceq $experimentSchema) 'manifest schema'
            $slotWorkloadCatalog = @($frameContract.workload_catalog)
            $slotWorkloadOrder = @($frameContract.workload_order)
            $phaseIdentity = [ordered]@{
                protocol_id = $phase; preflight_sha256 = $PhaseContext.preflight_sha256
                group_id = $frameContract.group_id; slot_id = $slot.id; artifact_role = $frameContract.artifact_role
                experiment_id = $ExperimentId
            }
            $slotPath = Join-Path $root $frameContract.frame_directory_name
            [void][IO.Directory]::CreateDirectory($slotPath)
            $runStatePath = Join-Path $slotPath 'run-state.json'
            $script:measuredWorkloadCounts = [ordered]@{}
            foreach ($name in $slotWorkloadOrder) { $script:measuredWorkloadCounts[$name] = $frameContract.samples }
            Write-RunState -Status completed -Verdict inconclusive -CompleteRun $true
            $state = Get-RunState $slotPath
            Check (Test-RunStateIdentity $state $ComparisonSequenceIndex 1) 'actual state writer/read boundary'
            if ($isLayerPhase) {
                Assert-P4FrameExactProjection $manifest (Get-P4LayerFrameExperimentContract $ExperimentId $PhaseContext.preflight_sha256) 'collector experiment'
                $live = $arguments.Clone(); $live.Remove('ValidateExperimentOnly')
                $live.ExperimentDirectory = Join-Path $temporaryRoot ('unadmitted-' + $ComparisonSequenceIndex)
                Refuses { & $collector @live } 'phase live collection refused'
                Check (-not (Test-Path -LiteralPath $live.ExperimentDirectory)) 'live refusal before experiment output'
                $bad = $state | ConvertTo-Json -Depth 12 | ConvertFrom-Json
                $bad.complete_run = 'true'
                Check (-not (Test-RunStateIdentity $bad $ComparisonSequenceIndex 1)) 'string complete flag refused'
                $bad = $state | ConvertTo-Json -Depth 12 | ConvertFrom-Json
                $bad.measured_operation_count = [double]$bad.measured_operation_count
                Check (-not (Test-RunStateIdentity $bad $ComparisonSequenceIndex 1)) 'floating operation count refused'
                foreach ($name in @('group_id', 'slot_id', 'artifact_role', 'experiment_sha256', 'preflight_sha256')) {
                    $bad = $state | ConvertTo-Json -Depth 12 | ConvertFrom-Json
                    $bad.$name = 'foreign'
                    Check (-not (Test-RunStateIdentity $bad $ComparisonSequenceIndex 1)) "state foreign $name refused"
                }
                Check (-not (Test-RunStateIdentity @($state) $ComparisonSequenceIndex 1)) 'root-array state refused'
            }
            foreach ($spec in $slotWorkloadCatalog) {
                $header = 'Flags,FrameTimelineVsyncId,IntendedVsync,FrameStartTime,HandleInputStart,DrawStart,FrameDeadline,FrameCompleted,DisplayPresentTime'
                $gfx = @($header, '0,9007199254740993,1000000000,1001000000,1002000000,0,1016666667,1007000001,0', '---PROFILEDATA---')
                $rows = @(Get-FrameRows -GfxInfo $gfx -SampleIndex 1 -Workload $spec.workload -OperationOrdinal 1 -EventCount $spec.preview_event_count -Phase preview)
                Check ($rows.Count -eq 1 -and $rows[0].frame_timeline_vsync_id -eq 9007199254740993L) 'lossless frame identity'
                $record = [ordered]@{ workload = $spec.workload; operation = "$($spec.workload)#1"; operation_ordinal = 1; sample_index = 1 }
                $sample = Complete-FrameSampleRecord -Record $record -PreviewRows $rows
                if ($isLayerPhase) {
                    foreach ($name in $phaseIdentity.Keys) {
                        Check ($rows[0].$name -ceq $phaseIdentity[$name] -and $sample.$name -ceq $phaseIdentity[$name]) "raw/sample identity $name"
                    }
                    Check ($rows[0].input_start_to_completion_ms -eq [decimal]'5.000001') 'own-row metric preserves nanosecond delta'
                    Check ($sample.first_preview_service_ms -ceq $(if ($spec.workload -ceq 'canvas256_layers16_tap') { '5.000001' } else { '' })) 'tap-only association'
                    $csvPath = Join-Path $slotPath ($spec.workload + '-raw.csv')
                    $rows | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding utf8NoBOM
                    $csvRows = @(Import-Csv -LiteralPath $csvPath)
                    $expected = [ordered]@{ workload = $spec.workload; operation = "$($spec.workload)#1"; operation_ordinal = 1
                        sample_index = 1; source_commit = $SourceCommit; production_commit = $PhaseContext.production_commit
                        variant = $Variant; raw_row_count = 1 }
                    Assert-P4FramePhaseRows -Rows $csvRows -ExpectedCapture $expected -Phase preview -EventCount $spec.preview_event_count -FullMetrics
                    Check ($true) 'actual CSV writer to analyzer row boundary'
                    foreach ($line in @('0,9.1,1000000000,1001000000,1002000000,0,1016666667,1007000001,0',
                            '-1,9,1000000000,1001000000,1002000000,0,1016666667,1007000001,0',
                            '1,9,1000000000,1001000000,1002000000,0,1016666667,1007000001,0')) {
                        Refuses { Get-FrameRows -GfxInfo @($header, $line, '---PROFILEDATA---') -SampleIndex 1 -Workload $spec.workload -OperationOrdinal 1 -EventCount $spec.preview_event_count -Phase preview } 'bad integer or flag refused'
                    }
                    Refuses { Get-FrameRows -GfxInfo $gfx[0..1] -SampleIndex 1 -Workload $spec.workload -OperationOrdinal 1 -EventCount $spec.preview_event_count -Phase preview } 'truncated PROFILEDATA refused'
                } else {
                    Check ('protocol_id' -cnotin @($rows[0].PSObject.Properties.Name) -and
                        'first_preview_service_ms' -cnotin @($sample.PSObject.Properties.Name)) 'legacy columns unchanged'
                }
            }
        }
    }
    foreach ($path in $result.source_sha256.Keys) {
        Check ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ceq $result.source_sha256[$path]) 'source unchanged during check'
    }
    $result.status = 'PASS'
} catch {
    $result.failure = $_.Exception.Message
    throw
} finally {
    $timer.Stop(); $result.checks = $script:checks; $result.elapsed_seconds = $timer.Elapsed.TotalSeconds
    $result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'validation.json') -Encoding utf8NoBOM
    Write-Output "$($result.status): $script:checks collector assertions; evidence=$OutputDirectory"
}

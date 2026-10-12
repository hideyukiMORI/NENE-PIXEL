[CmdletBinding()]
param([Parameter(Mandatory)][string]$FixtureDirectory)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$analyzer = Join-Path $PSScriptRoot 'analyze-profile-diagnostic.ps1'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
$root = [IO.Path]::GetFullPath($FixtureDirectory)
$lab = [IO.Path]::GetFullPath((Get-NenePixelLabRoot -StartDirectory $PSScriptRoot)).TrimEnd('\','/')
if ($IsWindows -and -not $lab.StartsWith('D:\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Lab must be on D drive.' }
if (-not $root.StartsWith("$lab$([IO.Path]::DirectorySeparatorChar)", [StringComparison]::OrdinalIgnoreCase)) {
    throw 'FixtureDirectory must be under the project lab.'
}
Assert-NenePixelLabPathHasNoLink $root
[IO.Directory]::CreateDirectory($root) | Out-Null
$run = Join-Path $root ([guid]::NewGuid().ToString('N'))
Assert-NenePixelLabPathHasNoLink $run
[IO.Directory]::CreateDirectory($run) | Out-Null
$utf8 = [Text.UTF8Encoding]::new($false)
$sentinel = 'PRIVATE_SENTINEL_DO_NOT_ECHO_9347'
$producer0 = '{"schema":"nene-pixel-profile-diagnostic-v1","session":"fixture","iteration":0,"role":"producer","pid":100,"origin_ns":1,"seq":1,"t_ns":2,"event":"process_begin"}'
$producer1 = '{"schema":"nene-pixel-profile-diagnostic-v1","session":"fixture","iteration":1,"role":"producer","pid":100,"origin_ns":1,"seq":2,"t_ns":3,"event":"iteration_begin"}'
$producer2 = '{"schema":"nene-pixel-profile-diagnostic-v1","session":"fixture","iteration":1,"role":"producer","pid":100,"origin_ns":1,"seq":3,"t_ns":4,"event":"launch_begin"}'
$app0 = '{"schema":"nene-pixel-profile-diagnostic-v1","session":"fixture","iteration":1,"role":"app","pid":101,"origin_ns":3,"seq":1,"t_ns":4,"event":"process_begin"}'
$portBegin = '{"schema":"nene-pixel-profile-diagnostic-v1","session":"fixture","iteration":1,"role":"app","pid":101,"origin_ns":3,"seq":2,"t_ns":5,"event":"publication_port_begin","token":1,"operation":1}'
$runtime = '{"schema":"nene-pixel-profile-diagnostic-v1","session":"fixture","iteration":1,"role":"app","pid":101,"origin_ns":3,"seq":2,"t_ns":5,"event":"runtime_operation_set","set_id":1,"stage":"intent","old_gate":1,"new_gate":2,"old_phase":"idle","old_recovery":"clear","old_last_outcome":"none","new_phase":"discarding","new_recovery":"unknown","new_last_outcome":"recovery_declined","changed":true}'
$sample = '{"schema":"nene-pixel-profile-diagnostic-v1","session":"fixture","iteration":1,"role":"app","pid":101,"origin_ns":3,"seq":2,"t_ns":5,"event":"scheduler_sample","sample_id":1,"pending":1,"published":null,"publishing":null,"gate":1,"gate_phase":"idle","gate_recovery":"clear","gate_last_outcome":"none","flushes":1,"requestable":true}'
$fold = '{"schema":"nene-pixel-profile-diagnostic-v1","session":"fixture","iteration":1,"role":"app","pid":101,"origin_ns":3,"seq":3,"t_ns":6,"event":"fold_branch","sample_id":1,"attempt_id":1,"branch":"flush","fold_now_ns":100,"deadline_clock":"scheduler_nano_time","helper_result":null}'
$commit = '{"schema":"nene-pixel-profile-diagnostic-v1","session":"fixture","iteration":1,"role":"app","pid":101,"origin_ns":3,"seq":4,"t_ns":7,"event":"fold_committed","sample_id":1,"attempt_id":1,"pending":1,"published":null,"publishing":null,"gate":1,"gate_phase":"idle","gate_recovery":"clear","gate_last_outcome":"none","flushes":1,"requestable":true,"quiet_deadline_ns":100,"cap_deadline_ns":100,"deadline_clock":"scheduler_nano_time","suspended":false}'
$due = '{"schema":"nene-pixel-profile-diagnostic-v1","session":"fixture","iteration":1,"role":"app","pid":101,"origin_ns":3,"seq":5,"t_ns":8,"event":"scheduler_decision","branch":"due","decision_now_ns":100,"due_ns":0,"pending":1,"published":null,"publishing":null,"gate":1,"gate_phase":"idle","gate_recovery":"clear","gate_last_outcome":"none","flushes":1,"requestable":true,"quiet_deadline_ns":100,"cap_deadline_ns":100,"deadline_clock":"scheduler_nano_time","suspended":false}'
$requestBegin = '{"schema":"nene-pixel-profile-diagnostic-v1","session":"fixture","iteration":1,"role":"app","pid":101,"origin_ns":3,"seq":6,"t_ns":9,"event":"scheduler_request_begin","token":1}'
$requestEnd = '{"schema":"nene-pixel-profile-diagnostic-v1","session":"fixture","iteration":1,"role":"app","pid":101,"origin_ns":3,"seq":7,"t_ns":10,"event":"scheduler_request_end","token":1,"result":"no_capture"}'
$prefix = @($producer0,$producer1,$producer2,$app0)
$results = [System.Collections.Generic.List[object]]::new()

function Check([string]$Name, [string[]]$Lines, [int]$ExpectedExit, [string]$ExpectedReason = '') {
    $inputFile = Join-Path $run "$Name.jsonl"
    $outputFile = Join-Path $run "$Name.report.json"
    [IO.File]::WriteAllLines($inputFile, $Lines, $utf8)
    $native = & pwsh -NoProfile -File $analyzer -InputPath $inputFile -Session fixture -OutputPath $outputFile 2>&1 | Out-String
    $exit = $LASTEXITCODE
    if ($exit -ne $ExpectedExit -or -not [IO.File]::Exists($outputFile)) { throw "Case $Name exit/report mismatch." }
    $rawReport = [IO.File]::ReadAllText($outputFile, $utf8)
    if ($rawReport.Contains($sentinel) -or $native.Contains($sentinel)) { throw "Case $Name leaked synthetic private text." }
    $report = $rawReport | ConvertFrom-Json
    if ($ExpectedExit -eq 0) {
        if ($report.status -cne 'structurally_valid_observed_prefix' -or $report.publication_absence -cne 'unknown' -or $report.tail_completeness -cne 'unproven' -or $report.profile_acceptance) { throw "Case $Name verdict mismatch." }
    } elseif ($report.status -cne 'invalid' -or $report.reason -cne $ExpectedReason -or $report.line -lt 0 -or $native -cnotmatch "invalid $ExpectedReason line [0-9]+") {
        throw "Case $Name reason mismatch."
    }
    $results.Add([ordered]@{ case=$Name; exit=$exit; reason=$ExpectedReason; input_sha256=(Get-FileHash -LiteralPath $inputFile -Algorithm SHA256).Hash.ToLowerInvariant(); input_bytes=([IO.FileInfo]::new($inputFile)).Length })
}

Check 'valid-open-port' ($prefix + @($portBegin)) 0
Check 'valid-runtime-tail' ($prefix + @($runtime)) 0
Check 'valid-scheduler-request' ($prefix + @($sample,$fold,$commit,$due,$requestBegin,$requestEnd)) 0
$portEnd = ($portBegin -replace '"seq":2','"seq":3' -replace '"t_ns":5','"t_ns":6' -replace 'publication_port_begin','publication_port_end' -replace '"operation":1\}$','"operation":1,"result":"published"}')
$runtimeStale = ($portEnd -replace '"seq":3','"seq":4' -replace '"t_ns":6','"t_ns":7' -replace 'publication_port_end','publication_runtime_complete' -replace '"result":"published"','"result":"stale"')
Check 'valid-port-runtime-distinct-outcomes' ($prefix + @($portBegin,$portEnd,$runtimeStale)) 0
$operationComplete = ($runtime -replace '"seq":2','"seq":3' -replace '"t_ns":5','"t_ns":6' -replace '"stage":"intent"','"stage":"complete"')
$interleavedSample = ($sample -replace '"seq":2','"seq":4' -replace '"t_ns":5','"t_ns":7')
$autosaveIntent = '{"schema":"nene-pixel-profile-diagnostic-v1","session":"fixture","iteration":1,"role":"app","pid":101,"origin_ns":3,"seq":5,"t_ns":8,"event":"runtime_autosave_set","set_id":1,"stage":"intent","old_pending":null,"old_published":null,"old_publishing":null,"new_pending":null,"new_published":null,"new_publishing":null,"old_outcome":"none","new_outcome":"none","changed":false}'
$autosaveComplete = ($autosaveIntent -replace '"seq":5','"seq":6' -replace '"t_ns":8','"t_ns":9' -replace '"stage":"intent"','"stage":"complete"')
Check 'valid-runtime-four-stages-interleaved' ($prefix + @($runtime,$operationComplete,$interleavedSample,$autosaveIntent,$autosaveComplete)) 0
$retryBranch = ($fold -replace '"seq":3','"seq":4' -replace '"t_ns":6','"t_ns":7' -replace '"attempt_id":1','"attempt_id":2')
$retryCommit = ($commit -replace '"seq":4','"seq":5' -replace '"t_ns":7','"t_ns":8' -replace '"attempt_id":1','"attempt_id":2')
Check 'valid-fold-retry' ($prefix + @($sample,$fold,$retryBranch,$retryCommit)) 0
Check 'duplicate-property' ($prefix + @(($portBegin -replace '\}$',',"token":1}'))) 1 'duplicate_key'
Check 'unknown-private-field' ($prefix + @(($portBegin -replace '\}$',',"private_message":"PRIVATE_SENTINEL_DO_NOT_ECHO_9347"}'))) 1 'fields'
Check 'wrong-token-type' ($prefix + @(($portBegin -replace '"token":1','"token":"1"'))) 1 'type'
Check 'wrong-gate-enum' ($prefix + @(($runtime -replace '"old_last_outcome":"none"','"old_last_outcome":"published"'))) 1 'enum'
Check 'null-runtime-gate' ($prefix + @(($runtime -replace '"old_gate":1','"old_gate":null'))) 1 'type'
Check 'contradictory-operation-changed' ($prefix + @(($runtime -replace '"changed":true','"changed":false'))) 1 'contradiction'
Check 'missing-port-begin' ($prefix + @(($portBegin -replace 'publication_port_begin','publication_port_end' -replace '"operation":1\}$','"operation":1,"result":"published"}'))) 1 'pairing'
Check 'app-sequence-reordered' ($prefix[0..2] + @($portBegin,$app0)) 1 'sequence'
Check 'app-sequence-gap' ($prefix + @(($portBegin -replace '"seq":2','"seq":3'))) 1 'sequence'
Check 'mixed-session' ($prefix + @(($portBegin -replace '"session":"fixture"','"session":"different"'))) 1 'identity'
Check 'invalid-clock' ($prefix + @(($portBegin -replace '"t_ns":5','"t_ns":2'))) 1 'clock'
Check 'explicit-invalidation' ($prefix + @(($portBegin -replace 'publication_port_begin","token":1,"operation":1','diagnostic_invalid","reason":"event_limit"'))) 1 'diagnostic_invalid'
Check 'malformed-private-json' ($prefix + @(($portBegin -replace '\}$',',"private":"PRIVATE_SENTINEL_DO_NOT_ECHO_9347"'))) 1 'json'
Check 'enum-private-text' ($prefix + @($portBegin,($portEnd -replace '"result":"published"','"result":"PRIVATE_SENTINEL_DO_NOT_ECHO_9347"'))) 1 'enum'
Check 'decimal-number' ($prefix + @(($portBegin -replace '"token":1','"token":1.0'))) 1 'type'
Check 'exponent-number' ($prefix + @(($portBegin -replace '"token":1','"token":1e0'))) 1 'type'
Check 'token-above-limit' ($prefix + @(($portBegin -replace '"token":1','"token":129'))) 1 'bounds'
Check 'line-above-limit' ($prefix + @(($portBegin -replace '\}$',(",`"padding`":`"" + ('X' * 17000) + $sentinel + '`"}')))) 1 'line_bound'
Check 'noncontiguous-ordinal' ($prefix + @(($portBegin -replace '"token":1','"token":2'))) 1 'ordinal'
Check 'conflicting-gate-label' ($prefix + @($sample,$fold,($commit -replace '"gate_phase":"idle"','"gate_phase":"discarding"'))) 1 'contradiction'
Check 'request-without-due' ($prefix + @($sample,$fold,$commit,($requestBegin -replace '"seq":6','"seq":5' -replace '"t_ns":9','"t_ns":8'))) 1 'pairing'
Check 'request-wrong-token' ($prefix + @($sample,$fold,$commit,$due,($requestBegin -replace '"token":1','"token":2'))) 1 'pairing'

$badUtf8Input = Join-Path $run 'bad-utf8.jsonl'
$badUtf8Output = Join-Path $run 'bad-utf8.report.json'
[IO.File]::WriteAllBytes($badUtf8Input, [byte[]](0xff,0x0a))
$badUtf8Native = & pwsh -NoProfile -File $analyzer -InputPath $badUtf8Input -Session fixture -OutputPath $badUtf8Output 2>&1 | Out-String
if ($LASTEXITCODE -ne 1 -or -not [IO.File]::Exists($badUtf8Output) -or $badUtf8Native -cnotmatch 'invalid utf8 line 1' -or ((Get-Content -LiteralPath $badUtf8Output -Raw | ConvertFrom-Json).reason -cne 'utf8')) { throw 'Case bad-utf8 mismatch.' }
$results.Add([ordered]@{ case='bad-utf8'; exit=1; reason='utf8'; input_sha256=(Get-FileHash -LiteralPath $badUtf8Input -Algorithm SHA256).Hash.ToLowerInvariant(); input_bytes=2 })

$overflow = [System.Collections.Generic.List[string]]::new()
foreach ($line in $prefix) { $overflow.Add($line) }
$overflow.Add($app0)
for ($n = 2; $n -le 4097; $n++) {
    $overflow.Add("{`"schema`":`"nene-pixel-profile-diagnostic-v1`",`"session`":`"fixture`",`"iteration`":1,`"role`":`"app`",`"pid`":101,`"origin_ns`":3,`"seq`":$n,`"t_ns`":$($n+3),`"event`":`"scheduler_flush`",`"count`":$n}")
}
Check 'app-event-limit' $overflow.ToArray() 1 'stream_bound'

$occupiedInput = Join-Path $run 'occupied-output.jsonl'
$occupiedOutput = Join-Path $run 'occupied-output.report.json'
[IO.File]::WriteAllLines($occupiedInput, ($prefix + @($portBegin)), $utf8)
$occupiedSentinel = 'OCCUPIED_OUTPUT_MUST_STAY'
[IO.File]::WriteAllText($occupiedOutput, $occupiedSentinel, $utf8)
$occupiedNative = & pwsh -NoProfile -File $analyzer -InputPath $occupiedInput -Session fixture -OutputPath $occupiedOutput 2>&1 | Out-String
if ($LASTEXITCODE -ne 1 -or $occupiedNative -cnotmatch 'invalid path line 0' -or [IO.File]::ReadAllText($occupiedOutput, $utf8) -cne $occupiedSentinel) { throw 'Case occupied-output mismatch.' }
$results.Add([ordered]@{ case='occupied-output'; exit=1; reason='path'; input_sha256=(Get-FileHash -LiteralPath $occupiedInput -Algorithm SHA256).Hash.ToLowerInvariant(); input_bytes=([IO.FileInfo]::new($occupiedInput)).Length })

$record = [ordered]@{ analyzer_sha256=(Get-FileHash -LiteralPath $analyzer -Algorithm SHA256).Hash.ToLowerInvariant(); validator_sha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant(); cases=$results }
$recordPath = Join-Path $run 'validation-record.json'
[IO.File]::WriteAllText($recordPath, ((ConvertTo-Json -InputObject $record -Depth 8 -Compress) + "`n"), $utf8)
Write-Output "PASS $($results.Count) cases; record=$recordPath"

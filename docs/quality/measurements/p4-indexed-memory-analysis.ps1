Set-StrictMode -Version Latest

$script:P4MemoryProfile = 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36'
$script:P4MemoryAnalysisContract = 'nene-pixel-p4-memory-analysis-v1'
$script:P4MemoryFamilies = @{
    'baseline-common-drawing-history' = @{
        role = 'baseline'
        schema = 'nene-pixel-p4-indexed-history-retention-v1'
        prefix = 'P4_HISTORY_RETENTION'
        facts = @{
            production_commit = '2dd4e01e3bbe88967237cde4e28412d2962fd590'
            entries = '64'
            changes = '524288'
            logical_bytes = 'not_applicable_baseline_rgba'
            owner_inventory = 'current_document:1,history:64'
        }
    }
    'candidate-common-indexed-history' = @{
        role = 'candidate'
        schema = 'nene-pixel-p4-indexed-history-retention-v1'
        prefix = 'P4_HISTORY_RETENTION'
        facts = @{
            entries = '64'
            changes = '524288'
            logical_bytes = '3147776'
            owner_inventory = 'current_document:1,history:64'
        }
    }
    'candidate-palette-history' = @{
        role = 'candidate'
        schema = 'nene-pixel-p4-palette-history-retention-v1'
        prefix = 'P4_HISTORY_RETENTION'
        facts = @{
            entries = '64'
            changes = '524288'
            logical_bytes = '3279360'
            owner_inventory = 'current_document:1,history:64'
        }
    }
    'candidate-legacy-import' = @{
        role = 'candidate'
        schema = 'nene-pixel-p4-legacy-import-retention-v1'
        prefix = 'P4_LEGACY_IMPORT_RETENTION'
        facts = @{
            current_pixels = '65536'
            legacy_pixels = '65536'
            distinct_rgba = '65536'
            destination_entries = '256'
            owner_inventory = 'current_document:1,operation:1,source:1,selected_destination:1,reduction:1'
            fixture_inventory = 'destination_definitions:2,source_copy:0,preview_copy:0'
            old_projection_weak_refs_cleared = '10'
        }
    }
}

function Get-P4MemoryStatusBundles {
    param([Parameter(Mandatory)][AllowEmptyString()][string[]]$Lines)

    $bundles = [System.Collections.Generic.List[object]]::new()
    $values = [ordered]@{}
    foreach ($line in $Lines) {
        if ($line -match '^INSTRUMENTATION_STATUS:\s*([^=]+)=(.*)$') {
            $name = $Matches[1].Trim()
            if ($values.Contains($name)) {
                throw "Duplicate instrumentation status field '$name'."
            }
            $values[$name] = $Matches[2]
            continue
        }
        if ($line -match '^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$') {
            $snapshot = [ordered]@{}
            foreach ($name in $values.Keys) {
                $snapshot[$name] = $values[$name]
            }
            $bundles.Add([pscustomobject]@{ code = [int]$Matches[1]; values = $snapshot })
            $values = [ordered]@{}
        }
    }
    if ($values.Count -ne 0) {
        throw 'Instrumentation status fields were not terminated by a status code.'
    }
    return @($bundles)
}

function Get-P4RequiredStatusValue {
    param(
        [Parameter(Mandatory)]$Bundle,
        [Parameter(Mandatory)][string]$Name
    )
    if (-not $Bundle.values.Contains($Name)) {
        throw "Instrumentation status code $($Bundle.code) is missing '$Name'."
    }
    $value = [string]$Bundle.values[$Name]
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Instrumentation status field '$Name' is blank."
    }
    return $value
}

function ConvertFrom-P4MemoryReport {
    param([Parameter(Mandatory)][string]$Text)

    $tokens = @($Text.Trim() -split '\s+')
    if ($tokens.Count -lt 2) {
        throw 'The P4 memory report is incomplete.'
    }
    $values = [ordered]@{ prefix = $tokens[0] }
    foreach ($token in $tokens[1..($tokens.Count - 1)]) {
        $separator = $token.IndexOf('=')
        if ($separator -le 0 -or $separator -eq $token.Length - 1) {
            throw "Malformed P4 memory report token '$token'."
        }
        $name = $token.Substring(0, $separator)
        if ($values.Contains($name)) {
            throw "Duplicate P4 memory report field '$name'."
        }
        $values[$name] = $token.Substring($separator + 1)
    }
    return $values
}

function Get-P4RequiredInt64 {
    param(
        [Parameter(Mandatory)]$Values,
        [Parameter(Mandatory)][string]$Name,
        [switch]$AllowNegative
    )
    if (-not $Values.Contains($Name)) {
        throw "P4 memory evidence is missing '$Name'."
    }
    $parsed = 0L
    if (-not [long]::TryParse([string]$Values[$Name], [ref]$parsed)) {
        throw "P4 memory evidence field '$Name' is not an Int64."
    }
    if (-not $AllowNegative -and $parsed -le 0L) {
        throw "P4 memory evidence field '$Name' must be positive."
    }
    return $parsed
}

function Assert-P4MemoryReportValue {
    param(
        [Parameter(Mandatory)]$Values,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Expected
    )
    if (-not $Values.Contains($Name) -or [string]$Values[$Name] -cne $Expected) {
        throw "P4 memory evidence field '$Name' does not match the fixed contract."
    }
}

function Test-P4MemoryCapture {
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string[]]$Lines,
        [Parameter(Mandatory)][string]$Family,
        [Parameter(Mandatory)][ValidateRange(1, 5)][int]$RunIndex,
        [Parameter(Mandatory)][string]$BuildCommit,
        [object[]]$PriorRuns = @()
    )

    if (-not $script:P4MemoryFamilies.ContainsKey($Family)) {
        throw "Unsupported P4 memory family '$Family'."
    }
    if ($BuildCommit -cnotmatch '^[0-9a-f]{40}$') {
        throw 'The P4 memory build commit must be a lowercase full commit.'
    }
    $fatalPattern = '(?i)(FAILURES!!!|INSTRUMENTATION_FAILED|ANR in |FATAL EXCEPTION|Process crashed)'
    if (($Lines -join "`n") -match $fatalPattern) {
        throw 'Fatal, ANR, crash, or JUnit failure output invalidates the P4 memory capture.'
    }
    $completionCodes = @(
        $Lines | ForEach-Object {
            if ($_ -match '^INSTRUMENTATION_CODE:\s*(-?\d+)\s*$') { [int]$Matches[1] }
        }
    )
    if ($completionCodes.Count -ne 1 -or $completionCodes[0] -ne -1) {
        throw 'P4 memory evidence requires one successful instrumentation completion code.'
    }

    $bundles = @(Get-P4MemoryStatusBundles -Lines $Lines)
    if (@($bundles | Where-Object { $_.code -in @(-1, -2) }).Count -ne 0) {
        throw 'JUnit error or failure status invalidates the P4 memory capture.'
    }
    $identityBundles = @($bundles | Where-Object { $_.code -eq 3 })
    $reportBundles = @($bundles | Where-Object { $_.code -eq 4 })
    if ($identityBundles.Count -ne 1 -or $reportBundles.Count -ne 1) {
        throw 'P4 memory evidence requires exactly one status-code 3 identity and one status-code 4 report.'
    }

    $identity = $identityBundles[0]
    $identityFields = @(
        'p4MemoryFamily', 'p4MemoryBuildCommit', 'p4MemoryRunIndex', 'p4MemoryProcessId',
        'p4MemoryProcessStartElapsedRealtimeMillis', 'p4MemoryRuntimeMaxMemoryBytes',
        'p4MemoryClassMebibytes'
    )
    if ($identity.values.Count -ne $identityFields.Count -or
        @($identity.values.Keys | Where-Object { $_ -notin $identityFields }).Count -ne 0) {
        throw 'The status-code 3 P4 memory identity field set is invalid.'
    }
    if ($reportBundles[0].values.Count -ne 1 -or -not $reportBundles[0].values.Contains('p4MemoryReport')) {
        throw 'The status-code 4 P4 memory report bundle must contain only p4MemoryReport.'
    }
    $identityFamily = Get-P4RequiredStatusValue $identity 'p4MemoryFamily'
    $identityCommit = Get-P4RequiredStatusValue $identity 'p4MemoryBuildCommit'
    $identityRun = Get-P4RequiredInt64 $identity.values 'p4MemoryRunIndex'
    $processId = Get-P4RequiredInt64 $identity.values 'p4MemoryProcessId'
    $processStart = Get-P4RequiredInt64 $identity.values 'p4MemoryProcessStartElapsedRealtimeMillis'
    $runtimeMax = Get-P4RequiredInt64 $identity.values 'p4MemoryRuntimeMaxMemoryBytes'
    $memoryClass = Get-P4RequiredInt64 $identity.values 'p4MemoryClassMebibytes'
    if ($identityFamily -cne $Family -or $identityCommit -cne $BuildCommit -or $identityRun -ne $RunIndex) {
        throw 'The P4 memory identity bundle does not match the requested family, run, and build.'
    }

    $reportText = Get-P4RequiredStatusValue $reportBundles[0] 'p4MemoryReport'
    $report = ConvertFrom-P4MemoryReport $reportText
    $contract = $script:P4MemoryFamilies[$Family]
    Assert-P4MemoryReportValue $report 'prefix' $contract.prefix
    Assert-P4MemoryReportValue $report 'schema' $contract.schema
    Assert-P4MemoryReportValue $report 'family' $Family
    Assert-P4MemoryReportValue $report 'run' $RunIndex.ToString()
    Assert-P4MemoryReportValue $report 'run_status' 'valid'
    Assert-P4MemoryReportValue $report 'measurement_build_commit' $BuildCommit
    Assert-P4MemoryReportValue $report 'profile' $script:P4MemoryProfile
    foreach ($name in $contract.facts.Keys) {
        Assert-P4MemoryReportValue $report $name ([string]$contract.facts[$name])
    }
    $expectedReportFields = @(
        'prefix', 'schema', 'family', 'run', 'run_status', 'measurement_build_commit', 'profile'
    ) + @($contract.facts.Keys) + @(
        'baseline_java_bytes', 'retained_java_bytes', 'retained_java_delta_bytes',
        'after_cycles_java_bytes', 'baseline_pss_kib', 'retained_pss_kib',
        'retained_pss_delta_kib', 'after_cycles_pss_kib'
    )
    if ($report.Count -ne $expectedReportFields.Count -or
        @($report.Keys | Where-Object { $_ -notin $expectedReportFields }).Count -ne 0) {
        throw 'The P4 memory report field set does not match its versioned family schema.'
    }

    $baselineJava = Get-P4RequiredInt64 $report 'baseline_java_bytes'
    $retainedJava = Get-P4RequiredInt64 $report 'retained_java_bytes'
    $retainedJavaDelta = Get-P4RequiredInt64 $report 'retained_java_delta_bytes' -AllowNegative
    $afterCyclesJava = Get-P4RequiredInt64 $report 'after_cycles_java_bytes'
    $baselinePss = Get-P4RequiredInt64 $report 'baseline_pss_kib'
    $retainedPss = Get-P4RequiredInt64 $report 'retained_pss_kib'
    $retainedPssDelta = Get-P4RequiredInt64 $report 'retained_pss_delta_kib' -AllowNegative
    $afterCyclesPss = Get-P4RequiredInt64 $report 'after_cycles_pss_kib'
    if ($retainedJavaDelta -ne ($retainedJava - $baselineJava) -or
        $retainedPssDelta -ne ($retainedPss - $baselinePss)) {
        throw 'Reported retained-memory deltas do not match their checkpoints.'
    }

    if ($PriorRuns.Count -ne ($RunIndex - 1)) {
        throw 'PriorRuns must contain every actual preceding analysis for this family.'
    }
    $allRuns = [System.Collections.Generic.List[object]]::new()
    for ($index = 0; $index -lt $PriorRuns.Count; $index += 1) {
        $prior = $PriorRuns[$index]
        if ($prior.analysis_contract -cne $script:P4MemoryAnalysisContract -or
            $prior.family -cne $Family -or $prior.run_index -ne ($index + 1) -or
            $prior.build_commit -cne $BuildCommit -or $prior.schema -cne $contract.schema -or
            $prior.profile -cne $script:P4MemoryProfile -or $prior.verdict -cne 'pass' -or
            $prior.runtime_max_memory_bytes -ne $runtimeMax -or $prior.memory_class_mib -ne $memoryClass) {
            throw 'PriorRuns contains a mismatched, incomplete, or failed analysis.'
        }
        $allRuns.Add($prior)
    }
    $processPairs = @($allRuns | ForEach-Object { "$($_.process_id)|$($_.process_start_elapsed_realtime_ms)" })
    $currentPair = "$processId|$processStart"
    if ($processPairs -contains $currentPair) {
        throw 'P4 memory runs must use distinct positive process identity pairs.'
    }

    $javaLimit = [long][math]::Floor($runtimeMax / 2.0)
    $individualPssLimit = $baselinePss + [long][math]::Floor(($memoryClass * 1024L * 3L) / 5L)
    $growthLimit = [math]::Max(1MB, [long][math]::Floor($runtimeMax / 100.0))
    $heapGrowth = $afterCyclesJava - $retainedJava
    $numericMiss = $retainedJava -gt $javaLimit -or $retainedPss -gt $individualPssLimit -or $heapGrowth -gt $growthLimit

    $median = $null
    if ($RunIndex -eq 5) {
        $deltas = @($allRuns | ForEach-Object { [long]$_.retained_pss_delta_kib }) + @($retainedPssDelta)
        $sorted = @($deltas | Sort-Object)
        $median = [long]$sorted[2]
        $medianLimit = [long][math]::Floor(($memoryClass * 1024L) / 2L)
        $numericMiss = $numericMiss -or $median -gt $medianLimit
    }

    return [pscustomobject]@{
        analysis_contract = $script:P4MemoryAnalysisContract
        role = $contract.role
        schema = $contract.schema
        family = $Family
        run_index = $RunIndex
        build_commit = $BuildCommit
        profile = $script:P4MemoryProfile
        process_id = $processId
        process_start_elapsed_realtime_ms = $processStart
        runtime_max_memory_bytes = $runtimeMax
        memory_class_mib = $memoryClass
        baseline_java_bytes = $baselineJava
        retained_java_bytes = $retainedJava
        retained_java_delta_bytes = $retainedJavaDelta
        after_cycles_java_bytes = $afterCyclesJava
        heap_growth_bytes = $heapGrowth
        baseline_pss_kib = $baselinePss
        retained_pss_kib = $retainedPss
        retained_pss_delta_kib = $retainedPssDelta
        after_cycles_pss_kib = $afterCyclesPss
        retained_java_limit_bytes = $javaLimit
        retained_pss_limit_kib = $individualPssLimit
        post_cycle_growth_limit_bytes = $growthLimit
        family_median_pss_delta_kib = $median
        verdict = if ($numericMiss) { 'PERFORMANCE_FAIL' } else { 'pass' }
    }
}

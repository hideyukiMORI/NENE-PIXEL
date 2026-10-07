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

$script:P4LayerMemoryIdentityFields = @(
    'protocol_id', 'experiment_id', 'preflight_sha256', 'preservation_sha256', 'session',
    'slot_id', 'artifact_role', 'measurement_build_commit', 'production_commit', 'app_apk_sha256', 'test_apk_sha256'
)

function Assert-P4LayerMemoryFields {
    param($Values, [string[]]$Names, [string]$Label)
    if ($Values -isnot [Collections.IDictionary] -or $Values.Count -ne $Names.Count -or
        @($Values.Keys | Where-Object { $_ -isnot [string] -or $_ -cnotin $Names }).Count -ne 0) {
        throw "Unexpected $Label field set."
    }
}

function Read-P4LayerMemoryInteger {
    param($Value, [string]$Name, [switch]$AllowZero, [switch]$Native)
    $integer = $Value -is [byte] -or $Value -is [sbyte] -or $Value -is [int16] -or $Value -is [uint16] -or
        $Value -is [int32] -or $Value -is [uint32] -or $Value -is [int64] -or $Value -is [uint64]
    if (($Native -and -not $integer) -or (-not $Native -and $Value -isnot [string])) {
        throw "Non-scalar or wrong numeric type: $Name"
    }
    $text = [string]$Value
    $parsed = 0L
    if ($text -cnotmatch '\A(0|[1-9][0-9]*)\z' -or -not [long]::TryParse($text, [ref]$parsed) -or
        ($parsed -eq 0 -and -not $AllowZero)) { throw "Invalid phase memory integer: $Name" }
    return $parsed
}

function Get-P4LayerMemoryContract {
    param($Context, [string]$Family, [int]$RunIndex, [string]$BuildCommit)
    Assert-P4LayerMemoryFields $Context $script:P4LayerMemoryIdentityFields 'phase identity'
    foreach ($name in $script:P4LayerMemoryIdentityFields) {
        $value = $Context[$name]
        if ($value -isnot [string]) { throw "Phase identity must contain scalar strings: $name" }
        $pattern = if ($name -like '*sha256') { '\A[0-9a-f]{64}\z' }
            elseif ($name -like '*commit') { '\A[0-9a-f]{40}\z' }
            elseif ($name -ceq 'artifact_role') { '\A(baseline_layers16|candidate)\z' }
            elseif ($name -ceq 'protocol_id') { '\Anene-pixel-p4-layer-phase-verification-v1\z' }
            else { '\A[a-z0-9][a-z0-9-]{2,63}\z' }
        if ($value -cnotmatch $pattern) { throw "Invalid phase identity: $name" }
    }
    . (Join-Path $PSScriptRoot 'p4-indexed-preflight.ps1')
    $slots = @(Get-P4LayerMemorySlotCatalog -ProtocolId $Context.protocol_id)
    $selected = @($slots | Where-Object { $_.id -ceq $Context.slot_id })
    if ($selected.Count -ne 1) { throw 'Unknown phase memory slot.' }
    $slot = $selected[0]
    if ($slot.family -cne $Family -or $slot.run -ne $RunIndex -or
        $slot.artifact_role -cne $Context.artifact_role -or $Context.measurement_build_commit -cne $BuildCommit -or
        ($slot.role -ceq 'baseline' -and $Context.production_commit -cne $slot.baseline_production_commit)) {
        throw 'Phase memory caller, artifact and catalog disagree.'
    }
    return $slot
}

function Assert-P4LayerMemoryText {
    param($Values, [string]$Name, [string]$Expected)
    if (-not $Values.Contains($Name) -or $Values[$Name] -isnot [string] -or $Values[$Name] -cne $Expected) {
        throw "Phase memory value differs: $Name"
    }
}

function Read-P4LayerMemoryObservation {
    param($Values, $Slot, [int]$Index, [long]$RuntimeMax, [switch]$Native)
    $metrics = @('java_bytes', 'java_committed_bytes', 'pss_kib', 'dalvik_pss_kib',
        'native_pss_kib', 'other_pss_kib', 'private_dirty_kib', 'shared_dirty_kib')
    Assert-P4LayerMemoryFields $Values (@('prefix', 'run', 'slot_id', 'index', 'checkpoint') + $metrics) 'checkpoint'
    Assert-P4LayerMemoryText $Values 'prefix' 'P4_LAYER_CHECKPOINT'
    Assert-P4LayerMemoryText $Values 'slot_id' $Slot.id
    Assert-P4LayerMemoryText $Values 'checkpoint' $Slot.checkpoints[$Index]
    $run = Read-P4LayerMemoryInteger $Values.run 'checkpoint.run' -Native:$Native
    $observedIndex = Read-P4LayerMemoryInteger $Values.index 'checkpoint.index' -AllowZero -Native:$Native
    if ($run -ne $Slot.run -or $observedIndex -ne $Index) { throw 'Phase checkpoints are missing or reordered.' }
    $result = [ordered]@{ prefix = 'P4_LAYER_CHECKPOINT'; run = $run; slot_id = $Slot.id;
        index = $observedIndex; checkpoint = $Slot.checkpoints[$Index] }
    foreach ($metric in $metrics) {
        $result[$metric] = Read-P4LayerMemoryInteger $Values[$metric] $metric -Native:$Native `
            -AllowZero:($metric -cnotin @('java_bytes', 'java_committed_bytes', 'pss_kib'))
    }
    if ($result.java_bytes -gt $result.java_committed_bytes -or $result.java_committed_bytes -gt $RuntimeMax) {
        throw 'Phase Java heap fields are internally inconsistent.'
    }
    return $result
}

function Get-P4LayerMemoryLimits {
    param([object[]]$Observations, [long]$RuntimeMax, [long]$MemoryClass)
    $javaLimit = [long][decimal]::Floor([decimal]$RuntimeMax / 2)
    $pssLimit = [long]([decimal]$Observations[0].pss_kib + [decimal]::Floor([decimal]$MemoryClass * 1024 * 3 / 5))
    $growthLimit = [long][math]::Max(1MB, [long][decimal]::Floor([decimal]$RuntimeMax / 100))
    $growth = [long]$Observations[4].java_bytes - [long]$Observations[3].java_bytes
    $miss = $growth -gt $growthLimit
    foreach ($observation in $Observations[1..4]) {
        $miss = $miss -or $observation.java_bytes -gt $javaLimit -or $observation.pss_kib -gt $pssLimit
    }
    return [ordered]@{ java_limit_bytes = $javaLimit; pss_limit_kib = $pssLimit;
        post_cycle_growth_limit_bytes = $growthLimit; heap_growth_bytes = $growth; numeric_miss = $miss }
}

function Read-P4LayerMemoryBundles {
    param([string[]]$Lines, $Slot, $Context)
    if (($Lines -join "`n") -match '(?i)(FAILURES!!!|INSTRUMENTATION_FAILED|ANR in |FATAL EXCEPTION|Process crashed)') {
        throw 'Failed instrumentation cannot produce a phase memory result.'
    }
    $codes = @($Lines | Where-Object { $_ -match '^INSTRUMENTATION_CODE:' })
    if ($codes.Count -ne 1 -or $codes[0] -notmatch '^INSTRUMENTATION_CODE:\s*-1\s*$') {
        throw 'Phase memory requires one successful completion.'
    }
    $nonempty = @($Lines | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($nonempty[-1] -cne $codes[0] -or @($Lines | Where-Object {
        ($_ -match '^INSTRUMENTATION_STATUS_CODE:' -and $_ -notmatch '^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$') -or
        ($_ -match '^INSTRUMENTATION_STATUS:' -and $_ -notmatch '^INSTRUMENTATION_STATUS:\s*([^=]+)=(.*)$')
    }).Count -ne 0) { throw 'Malformed or non-terminal instrumentation record.' }
    $bundles = @(Get-P4MemoryStatusBundles -Lines $Lines)
    if (@($bundles | Where-Object { $_.code -notin @(0, 1, 3, 4, 5) }).Count -ne 0) {
        throw 'Unexpected or failing phase instrumentation status.'
    }
    $selected = @($bundles | Where-Object { $_.code -in @(3, 4, 5) })
    if ($selected.Count -ne 7 -or (@($selected.code) -join ',') -cne '3,5,5,5,5,5,4') {
        throw 'Phase identity, checkpoints or final report are missing, repeated or reordered.'
    }
    $identity = $selected[0].values
    Assert-P4LayerMemoryFields $identity @('p4MemoryFamily', 'p4MemoryBuildCommit', 'p4MemoryRunIndex',
        'p4MemoryProcessId', 'p4MemoryProcessStartElapsedRealtimeMillis', 'p4MemoryRuntimeMaxMemoryBytes',
        'p4MemoryClassMebibytes', 'p4LayerIdentity') 'process identity'
    Assert-P4LayerMemoryText $identity 'p4MemoryFamily' $Slot.family
    Assert-P4LayerMemoryText $identity 'p4MemoryBuildCommit' $Context.measurement_build_commit
    $projection = ConvertFrom-P4MemoryReport $identity.p4LayerIdentity
    Assert-P4LayerMemoryFields $projection (@('prefix') + $script:P4LayerMemoryIdentityFields) 'admission projection'
    Assert-P4LayerMemoryText $projection 'prefix' 'P4_LAYER_IDENTITY'
    foreach ($name in $script:P4LayerMemoryIdentityFields) { Assert-P4LayerMemoryText $projection $name $Context[$name] }
    $run = Read-P4LayerMemoryInteger $identity.p4MemoryRunIndex 'run'
    if ($run -ne $Slot.run) { throw 'Wrong phase run index.' }
    $processId = Read-P4LayerMemoryInteger $identity.p4MemoryProcessId 'process_id'
    $start = Read-P4LayerMemoryInteger $identity.p4MemoryProcessStartElapsedRealtimeMillis 'process_start'
    $maximum = Read-P4LayerMemoryInteger $identity.p4MemoryRuntimeMaxMemoryBytes 'runtime_max'
    $memoryClass = Read-P4LayerMemoryInteger $identity.p4MemoryClassMebibytes 'memory_class'
    if ($processId -gt [int]::MaxValue -or $memoryClass -gt [int]::MaxValue) { throw 'Impossible Android integer identity.' }
    $observations = @()
    foreach ($index in 0..4) {
        $checkpoint = $selected[$index + 1].values
        Assert-P4LayerMemoryFields $checkpoint @('p4LayerMemoryCheckpoint') 'checkpoint bundle'
        $raw = ConvertFrom-P4MemoryReport $checkpoint.p4LayerMemoryCheckpoint
        $observations += Read-P4LayerMemoryObservation $raw $Slot $index $maximum
    }
    Assert-P4LayerMemoryFields $selected[6].values @('p4MemoryReport') 'final bundle'
    return [ordered]@{ process_id = $processId; process_start_elapsed_realtime_ms = $start;
        runtime_max_memory_bytes = $maximum; memory_class_mib = $memoryClass;
        checkpoint_observations = $observations;
        report = (ConvertFrom-P4MemoryReport $selected[6].values.p4MemoryReport) }
}

function Assert-P4LayerMemoryReport {
    param($Capture, $Slot, $Context)
    $facts = [ordered]@{
        prefix = 'P4_LAYER_EDITOR_RETENTION'; schema = $Slot.schema; family = $Slot.family;
        run = [string]$Slot.run; run_status = 'valid'; profile = $Slot.profile;
        fixture_sha256 = $Slot.fixture_sha256; checkpoint_count = '5'; document_layers = '16';
        canvas_width = '256'; canvas_height = '256'; preview_move_count = '16';
        preview_raw_position_count = '4081'; preview_unique_position_count = '256'; undo_redo_cycles = '10';
        gc_passes = '2'; published_position_verified = 'true'; owner_inventory = 'main_activity:1,viewmodel:1,compose_canvas:1'
    }
    $metrics = @($Slot.checkpoints | ForEach-Object { "${_}_java_bytes"; "${_}_pss_kib" })
    $names = @($facts.Keys) + $script:P4LayerMemoryIdentityFields + @('fixture_uri', 'grantee_uid', 'provider_uid') + $metrics
    $report = $Capture.report
    Assert-P4LayerMemoryFields $report $names 'final report'
    foreach ($name in $facts.Keys) { Assert-P4LayerMemoryText $report $name $facts[$name] }
    foreach ($name in $script:P4LayerMemoryIdentityFields) { Assert-P4LayerMemoryText $report $name $Context[$name] }
    $fixtureName = "i89-145-$($Context.preflight_sha256.Substring(0, 12))-$($Slot.role)-$($Slot.run).nenepixel"
    $uri = "content://io.github.hideyukimori.nenepixel.test.acceptance.documents/document/$fixtureName"
    Assert-P4LayerMemoryText $report 'fixture_uri' $uri
    $grantee = Read-P4LayerMemoryInteger $report.grantee_uid 'grantee_uid'
    $provider = Read-P4LayerMemoryInteger $report.provider_uid 'provider_uid'
    if ($grantee -eq $provider -or $grantee -gt [int]::MaxValue -or $provider -gt [int]::MaxValue) {
        throw 'The provider is not a distinct valid UID from the sampled app.'
    }
    foreach ($index in 0..4) {
        $checkpoint = $Slot.checkpoints[$index]
        $java = Read-P4LayerMemoryInteger $report["${checkpoint}_java_bytes"] 'summary.java'
        $pss = Read-P4LayerMemoryInteger $report["${checkpoint}_pss_kib"] 'summary.pss'
        if ($java -ne $Capture.checkpoint_observations[$index].java_bytes -or
            $pss -ne $Capture.checkpoint_observations[$index].pss_kib) {
            throw 'Final memory values disagree with their raw checkpoint.'
        }
    }
}

function Get-P4LayerMemoryMedians {
    param([object[]]$Runs, [string[]]$Checkpoints)
    if ($Runs.Count -gt 5) { throw 'A phase memory family has more than five runs.' }
    $medians = [ordered]@{}
    foreach ($index in 1..4) {
        $medians[$Checkpoints[$index]] = $null
        if ($Runs.Count -eq 5) {
            $deltas = @($Runs | ForEach-Object {
                [long]$_.checkpoint_observations[$index].pss_kib - [long]$_.checkpoint_observations[0].pss_kib
            } | Sort-Object)
            $medians[$Checkpoints[$index]] = [long]$deltas[2]
        }
    }
    return $medians
}

function Read-P4LayerMemoryPriorRuns {
    param([object[]]$PriorRuns, $Slot, $Context, $Capture)
    if ($PriorRuns.Count -ne $Slot.memory_sequence_index - 1) {
        throw 'Every preceding phase memory slot is required, in order.'
    }
    $verified = [Collections.Generic.List[object]]::new()
    $artifactIdentities = @{}
    $artifactIdentities[$Context.artifact_role] = $Context
    $runtimeIdentities = @{}
    $runtimeIdentities[$Context.artifact_role] = @($Capture.runtime_max_memory_bytes, $Capture.memory_class_mib)
    $pairs = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    [void]$pairs.Add("$($Capture.process_id)|$($Capture.process_start_elapsed_realtime_ms)")
    foreach ($prior in $PriorRuns) {
        if ($prior -isnot [Collections.IDictionary]) { throw 'Prior phase memory analysis must be an object.' }
        $run = Read-P4LayerMemoryInteger $prior.run_index 'prior.run' -Native
        $priorSlot = Get-P4LayerMemoryContract $prior.phase_context $prior.family $run $prior.build_commit
        if ($priorSlot.memory_sequence_index -ne $verified.Count + 1) { throw 'Prior phase slots are reordered.' }
        foreach ($name in @('protocol_id', 'experiment_id', 'preflight_sha256', 'preservation_sha256', 'session')) {
            Assert-P4LayerMemoryText $prior.phase_context $name $Context[$name]
        }
        foreach ($pair in @(@('analysis_contract', $Slot.analysis_contract), @('schema', $Slot.schema),
            @('profile', $Slot.profile), @('verdict', 'pass'))) {
            Assert-P4LayerMemoryText $prior $pair[0] $pair[1]
        }
        $role = $prior.phase_context.artifact_role
        if ($artifactIdentities.ContainsKey($role)) {
            foreach ($name in @('measurement_build_commit', 'production_commit', 'app_apk_sha256', 'test_apk_sha256')) {
                Assert-P4LayerMemoryText $prior.phase_context $name $artifactIdentities[$role][$name]
            }
        } else { $artifactIdentities[$role] = $prior.phase_context }
        $priorProcessId = Read-P4LayerMemoryInteger $prior.process_id 'prior.pid' -Native
        $start = Read-P4LayerMemoryInteger $prior.process_start_elapsed_realtime_ms 'prior.start' -Native
        if (-not $pairs.Add("$priorProcessId|$start")) { throw 'A phase memory process identity was reused.' }
        $maximum = Read-P4LayerMemoryInteger $prior.runtime_max_memory_bytes 'prior.runtime_max' -Native
        $memoryClass = Read-P4LayerMemoryInteger $prior.memory_class_mib 'prior.memory_class' -Native
        if ($priorProcessId -gt [int]::MaxValue -or $memoryClass -gt [int]::MaxValue) { throw 'Impossible prior Android integer.' }
        if ($runtimeIdentities.ContainsKey($role)) {
            if ($maximum -ne $runtimeIdentities[$role][0] -or $memoryClass -ne $runtimeIdentities[$role][1]) {
                throw 'Runtime memory capacity changed within one artifact role.'
            }
        } else { $runtimeIdentities[$role] = @($maximum, $memoryClass) }
        if ($prior.checkpoint_observations -isnot [array] -or $prior.checkpoint_observations.Count -ne 5) {
            throw 'Prior phase checkpoint population is incomplete.'
        }
        foreach ($index in 0..4) {
            [void](Read-P4LayerMemoryObservation $prior.checkpoint_observations[$index] $priorSlot $index $maximum -Native)
        }
        $limits = Get-P4LayerMemoryLimits $prior.checkpoint_observations $maximum $memoryClass
        if ($limits.numeric_miss) { throw 'Prior pass contains a numeric failure.' }
        $verified.Add($prior)
        if ($run -eq 5) {
            $group = @($verified | Where-Object { $_.phase_context.artifact_role -ceq $role })
            $medians = Get-P4LayerMemoryMedians $group $Slot.checkpoints
            $limit = [long][decimal]::Floor([decimal]$memoryClass * 1024 / 2)
            if (@($medians.Values | Where-Object { $_ -gt $limit }).Count -ne 0) {
                throw 'Prior pass contains a five-run median failure.'
            }
        }
    }
    return $verified.ToArray()
}

function Test-P4LayerMemoryCapture {
    param([string[]]$Lines, [string]$Family, [int]$RunIndex, [string]$BuildCommit,
        [object[]]$PriorRuns, $PhaseContext)
    $slot = Get-P4LayerMemoryContract $PhaseContext $Family $RunIndex $BuildCommit
    $capture = Read-P4LayerMemoryBundles $Lines $slot $PhaseContext
    Assert-P4LayerMemoryReport $capture $slot $PhaseContext
    $previous = @(Read-P4LayerMemoryPriorRuns $PriorRuns $slot $PhaseContext $capture)
    $limits = Get-P4LayerMemoryLimits $capture.checkpoint_observations `
        $capture.runtime_max_memory_bytes $capture.memory_class_mib
    $group = @($previous | Where-Object { $_.phase_context.artifact_role -ceq $PhaseContext.artifact_role })
    if ($group.Count -ne $RunIndex - 1) { throw 'Wrong phase memory family population.' }
    $medians = Get-P4LayerMemoryMedians ($group + @($capture)) $slot.checkpoints
    $medianLimit = [long][decimal]::Floor([decimal]$capture.memory_class_mib * 1024 / 2)
    $medianMiss = @($medians.Values | Where-Object { $null -ne $_ -and $_ -gt $medianLimit }).Count -ne 0
    $projection = [ordered]@{}
    foreach ($name in $script:P4LayerMemoryIdentityFields) { $projection[$name] = $PhaseContext[$name] }
    return [ordered]@{
        analysis_contract = $slot.analysis_contract
        role = $slot.role
        schema = $slot.schema
        family = $Family
        run_index = $RunIndex
        build_commit = $BuildCommit
        profile = $slot.profile
        phase_context = $projection
        process_id = $capture.process_id
        process_start_elapsed_realtime_ms = $capture.process_start_elapsed_realtime_ms
        runtime_max_memory_bytes = $capture.runtime_max_memory_bytes
        memory_class_mib = $capture.memory_class_mib
        checkpoint_observations = $capture.checkpoint_observations
        limits = $limits
        family_median_pss_delta_kib = $medians
        family_median_limit_kib = $medianLimit
        fixture_uri = $capture.report.fixture_uri
        grantee_uid = Read-P4LayerMemoryInteger $capture.report.grantee_uid 'grantee_uid'
        provider_uid = Read-P4LayerMemoryInteger $capture.report.provider_uid 'provider_uid'
        verdict = if ($limits.numeric_miss -or $medianMiss) { 'PERFORMANCE_FAIL' } else { 'pass' }
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
        [object[]]$PriorRuns = @(),
        [AllowNull()][Collections.IDictionary]$PhaseContext
    )

    if ($PSBoundParameters.ContainsKey('PhaseContext')) {
        return Test-P4LayerMemoryCapture -Lines $Lines -Family $Family -RunIndex $RunIndex `
            -BuildCommit $BuildCommit -PriorRuns $PriorRuns -PhaseContext $PhaseContext
    }
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

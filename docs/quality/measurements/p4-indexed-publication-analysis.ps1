Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-P4LayerStorageContext {
    param($Context, [ValidateSet('publication', 'saf-save')][string]$Lane)
    $identityFields = $script:P4LayerMemoryIdentityFields
    if ($Lane -ceq 'publication') { $identityFields += @('publication_apk_sha256') }
    Assert-P4LayerMemoryFields $Context $identityFields 'storage phase identity'
    foreach ($name in $identityFields) {
        $pattern = if ($name -like '*sha256') { '\A[0-9a-f]{64}\z' }
            elseif ($name -like '*commit') { '\A[0-9a-f]{40}\z' }
            else { '\A[a-z0-9][a-z0-9-]{2,63}\z' }
        if ($Context[$name] -isnot [string] -or $Context[$name] -cnotmatch $pattern) {
            throw "Invalid phase storage context: $name"
        }
    }
    $slot = @(Get-P4LayerStorageSlotCatalog $Context.protocol_id | Where-Object { $_.lane -ceq $Lane })[0]
    if ($Context.artifact_role -cne $slot.artifact_role -or $Context.slot_id -cne $slot.id) {
        throw 'Storage role/slot differs from its phase catalog.'
    }
    return $slot
}

function Read-P4LayerPublicationIdentity {
    param($Context, [string[]]$IdentityLines, [string[]]$InstrumentationLines, [string]$Role)
    $slot = Get-P4LayerStorageContext $Context 'publication'
    if ($Role -cne 'candidate') { throw 'Layer publication must use the candidate.' }
    $identityFields = $script:P4LayerMemoryIdentityFields + @('publication_apk_sha256')
    if ($IdentityLines.Count -ne 1) { throw 'Publication identity must have exactly one line.' }
    $identity = ConvertFrom-P4MemoryReport $IdentityLines[0]
    $prefix = 'p4-layer-publication-' + $Context.preflight_sha256.Substring(0, 12)
    $facts = [ordered]@{
        prefix = 'P4_LAYER_PUBLICATION_IDENTITY'
        target_package = 'io.github.hideyukimori.nenepixel.adapters.persistence.test'
        profile = 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36'
        fixture_sha256 = '165f62d180533849ce1a4ef1625cd3971e445f2dca60ef7b9b46fedaafa0b3ec'
        maximum_candidate_bytes = '1182885'; minimum_candidate_bytes = '85'
        warmup_count_per_group = '5'; sample_count_per_group = '20'; journal_rows = '54'
        worker_timeout_seconds = '60'; native_timeout_seconds = '300'; sample_anomaly_nanos = '5000000000'
        work_relative_path = "no_backup/$prefix"; report_relative_path = "files/$prefix"
    }
    $numeric = @('process_id', 'process_start_elapsed_realtime_ms', 'target_uid')
    Assert-P4LayerMemoryFields $identity (@($facts.Keys) + $identityFields + $numeric) 'publication identity projection'
    foreach ($name in $facts.Keys) { Assert-P4LayerMemoryText $identity $name $facts[$name] }
    foreach ($name in $identityFields) { Assert-P4LayerMemoryText $identity $name $Context[$name] }
    foreach ($name in $numeric) {
        $value = Read-P4LayerMemoryInteger $identity[$name] $name
        if ($name -cne 'process_start_elapsed_realtime_ms' -and $value -gt [int]::MaxValue) {
            throw 'Impossible publication process identity.'
        }
    }
    Assert-P4LayerStorageInstrumentation $InstrumentationLines $IdentityLines[0] 'p4LayerPublicationIdentity'
    return [ordered]@{ slot = $slot; identity = $identity }
}

function Assert-P4LayerStorageInstrumentation {
    param([string[]]$Lines, [string]$IdentityLine, [string]$IdentityKey)
    if (($Lines -join "`n") -match '(?i)(FAILURES!!!|INSTRUMENTATION_FAILED|ANR in |FATAL EXCEPTION|Process crashed)') {
        throw 'Failed instrumentation cannot produce a publication result.'
    }
    $codes = @($Lines | Where-Object { $_ -match '^INSTRUMENTATION_CODE:' })
    $nonempty = @($Lines | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($codes.Count -ne 1 -or $codes[0] -notmatch '^INSTRUMENTATION_CODE:\s*-1\s*$' -or
        $nonempty[-1] -cne $codes[0]) { throw 'Publication requires one terminal successful completion.' }
    if (@($Lines | Where-Object {
        ($_ -match '^INSTRUMENTATION_STATUS_CODE:' -and $_ -notmatch '^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$') -or
        ($_ -match '^INSTRUMENTATION_STATUS:' -and $_ -notmatch '^INSTRUMENTATION_STATUS:\s*([^=]+)=(.*)$')
    }).Count -ne 0) { throw 'Malformed publication instrumentation record.' }
    $bundles = @(Get-P4MemoryStatusBundles $Lines)
    if ($bundles.Count -ne 3 -or (@($bundles.code) -join ',') -cne '1,3,0') {
        throw 'Publication requires one started test, identity and completed test in order.'
    }
    Assert-P4LayerMemoryFields $bundles[1].values @($IdentityKey) 'storage status identity'
    Assert-P4LayerMemoryText $bundles[1].values $IdentityKey $IdentityLine
}

function Read-P4LayerSafIdentity {
    param($Context, [string[]]$IdentityLines, [string[]]$InstrumentationLines)
    [void](Get-P4LayerStorageContext $Context 'saf-save')
    if ($IdentityLines.Count -ne 1) { throw 'SAF identity must have exactly one line.' }
    $identity = ConvertFrom-P4MemoryReport $IdentityLines[0]
    $prefix = $Context.preflight_sha256.Substring(0, 12)
    $authority = 'content://io.github.hideyukimori.nenepixel.test.acceptance.documents/document/'
    $facts = [ordered]@{
        prefix = 'P4_LAYER_SAF_IDENTITY'; profile = 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36'
        fixture_uri = "${authority}i89-145-$prefix-saf-source.nenepixel"
        fixture_sha256 = '165f62d180533849ce1a4ef1625cd3971e445f2dca60ef7b9b46fedaafa0b3ec'
        fixture_bytes = '1182862'; destination_count = '25'; warmup_count = '5'; sample_count = '20'; journal_rows = '27'
        setup_timeout_seconds = '300'; worker_timeout_seconds = '60'; native_timeout_seconds = '420'
        sample_anomaly_nanos = '5000000000'; report_relative_path = "files/p4-layer-saf-$prefix"
    }
    $numbers = @('process_id', 'process_start_elapsed_realtime_ms', 'grantee_uid', 'provider_uid')
    Assert-P4LayerMemoryFields $identity (@($facts.Keys) + $script:P4LayerMemoryIdentityFields + $numbers) 'SAF identity'
    foreach ($name in $facts.Keys) { Assert-P4LayerMemoryText $identity $name $facts[$name] }
    foreach ($name in $script:P4LayerMemoryIdentityFields) { Assert-P4LayerMemoryText $identity $name $Context[$name] }
    foreach ($name in $numbers) {
        $value = Read-P4LayerMemoryInteger $identity[$name] $name
        if ($name -cne 'process_start_elapsed_realtime_ms' -and $value -gt [int]::MaxValue) {
            throw 'Impossible SAF process or grant identity.'
        }
    }
    if ($identity.grantee_uid -ceq $identity.provider_uid) { throw 'SAF provider must have a distinct UID.' }
    Assert-P4LayerStorageInstrumentation $InstrumentationLines $IdentityLines[0] 'p4LayerSafIdentity'
    return $identity
}

function Read-P4LayerSafSetup {
    param([string[]]$Lines, $Identity, $Context)
    if ($Lines.Count -ne 27 -or $Lines[0] -cne 'kind,index,destination_uri,grantee_uid,provider_uid,byte_count') {
        throw 'SAF setup requires the source plus 25 granted fresh destinations.'
    }
    $prefix = 'content://io.github.hideyukimori.nenepixel.test.acceptance.documents/document/i89-145-' +
        $Context.preflight_sha256.Substring(0, 12) + '-saf-'
    $destinations = @()
    foreach ($ordinal in 0..25) {
        $kind = if ($ordinal -eq 0) { 'source' } elseif ($ordinal -le 5) { 'warmup' } else { 'sample' }
        $index = if ($ordinal -eq 0) { 0 } elseif ($ordinal -le 5) { $ordinal - 1 } else { $ordinal - 6 }
        $suffix = if ($ordinal -eq 0) { 'source.nenepixel' } else { "$kind-$($index + 1).nenepixel" }
        $bytes = if ($ordinal -eq 0) { '1182862' } else { '0' }
        $expected = @($kind, [string]$index, ($prefix + $suffix), $Identity.grantee_uid, $Identity.provider_uid, $bytes) -join ','
        if ($Lines[$ordinal + 1] -cne $expected) { throw 'SAF setup order, grant identity, freshness or URI differs.' }
        if ($ordinal -gt 0) {
            $destinations += [ordered]@{ kind = $kind; index = $index; uri = $prefix + $suffix;
                grantee_uid = $Identity.grantee_uid; provider_uid = $Identity.provider_uid }
        }
    }
    return $destinations
}

function Read-P4LayerSafRow {
    param([string]$Line, $Destination, [string]$Kind)
    $fields = @($Line -split ',')
    if ($fields.Count -ne 13) { throw 'SAF journal row has the wrong field count.' }
    $elapsed = Read-P4LayerMemoryInteger $fields[4] 'SAF elapsed nanoseconds'
    if ($elapsed -gt 5000000000L) { throw 'SAF sample exceeded five seconds.' }
    $expected = @('nene-pixel-p4-layer-saf-save-device-v1', 'candidate', [string]$Destination.index, $Kind,
        [string]$elapsed, $Destination.uri, $Destination.grantee_uid, $Destination.provider_uid,
        '0', '1182862', 'true', 'saved', 'not_needed') -join ','
    if ($Line -cne $expected) { throw 'SAF sample identity, grant, freshness, accepted bytes or outcome differs.' }
    return [ordered]@{ index = $Destination.index; kind = $Kind; elapsed_ns = $elapsed;
        destination_uri = $Destination.uri; grantee_uid = [int]$Destination.grantee_uid;
        provider_uid = [int]$Destination.provider_uid; accepted_byte_count = 1182862 }
}

function Test-P4SafSaveCapture {
    param([AllowEmptyCollection()][string[]]$Lines, [AllowEmptyCollection()][string[]]$StatusLines,
        [AllowEmptyCollection()][string[]]$IdentityLines, [AllowEmptyCollection()][string[]]$SetupLines,
        [AllowEmptyCollection()][string[]]$InstrumentationLines, [AllowNull()][Collections.IDictionary]$PhaseContext)
    . (Join-Path $PSScriptRoot 'p4-indexed-memory-analysis.ps1')
    . (Join-Path $PSScriptRoot 'p4-indexed-preflight.ps1')
    $identity = Read-P4LayerSafIdentity $PhaseContext $IdentityLines $InstrumentationLines
    $destinations = @(Read-P4LayerSafSetup $SetupLines $identity $PhaseContext)
    $header = 'schema,role,index,kind,elapsed_ns,destination_uri,grantee_uid,provider_uid,' +
        'initial_byte_count,accepted_byte_count,picker_consumed,outcome,cleanup'
    if ($Lines.Count -ne 28 -or $Lines[0] -cne $header) { throw 'SAF journal requires its exact header and 27 rows.' }
    if ($StatusLines.Count -ne 1 -or $StatusLines[0] -cne 'complete') { throw 'SAF journal is not complete.' }
    $observations = @(foreach ($index in 0..24) {
        Read-P4LayerSafRow $Lines[$index + 1] $destinations[$index] $destinations[$index].kind
    })
    $measured = @($observations[5..24])
    $minimum = 0; $maximum = 0
    foreach ($index in 1..19) {
        if ($measured[$index].elapsed_ns -lt $measured[$minimum].elapsed_ns) { $minimum = $index }
        if ($measured[$index].elapsed_ns -gt $measured[$maximum].elapsed_ns) { $maximum = $index }
    }
    $summaryMinimum = Read-P4LayerSafRow $Lines[26] $destinations[$minimum + 5] 'summary_min'
    $summaryMaximum = Read-P4LayerSafRow $Lines[27] $destinations[$maximum + 5] 'summary_max'
    if ($summaryMinimum.elapsed_ns -ne $measured[$minimum].elapsed_ns -or
        $summaryMaximum.elapsed_ns -ne $measured[$maximum].elapsed_ns) { throw 'SAF summaries disagree with raw samples.' }
    $projection = [ordered]@{}
    foreach ($name in $script:P4LayerMemoryIdentityFields) { $projection[$name] = $PhaseContext[$name] }
    return [ordered]@{
        analysis_contract = 'nene-pixel-p4-layer-saf-save-analysis-v1'
        schema = 'nene-pixel-p4-layer-saf-save-device-v1'; role = 'candidate'; verdict = 'valid-descriptive'
        phase_context = $projection; saf_identity = $identity
        warmup_count = 5; sample_count = 20; journal_rows = 27; destination_count = 25; structural_byte_count = 1182862
        minimum_nanos = $measured[$minimum].elapsed_ns; maximum_nanos = $measured[$maximum].elapsed_ns
        minimum_index = $minimum; maximum_index = $maximum
        checkpoint_observations = $observations
    }
}

function Read-P4PublicationPositiveInt64 {
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][string]$Name
    )

    if ($Text -cnotmatch '^[0-9]+$') {
        throw "Invalid positive publication value: $Name=$Text"
    }
    $value = [long]0
    if (-not [long]::TryParse($Text, [ref]$value) -or $value -le [long]0) {
        throw "Invalid positive publication value: $Name=$Text"
    }
    return $value
}

function Get-P4PublicationGroups {
    param([Parameter(Mandatory = $true)][ValidateSet('baseline', 'candidate')][string]$Role)

    if ($Role -eq 'baseline') {
        return @(
            [ordered]@{ name = 'baseline_v1_max'; structural_byte_count = 262209 },
            [ordered]@{ name = 'baseline_v1_min'; structural_byte_count = 69 }
        )
    }
    return @(
        [ordered]@{ name = 'candidate_v2_max'; structural_byte_count = 66628 },
        [ordered]@{ name = 'candidate_v2_min'; structural_byte_count = 77 }
    )
}

function Read-P4PublicationRow {
    param(
        [Parameter(Mandatory = $true)][string]$Line,
        [Parameter(Mandatory = $true)][string]$ExpectedSchema,
        [Parameter(Mandatory = $true)][string]$ExpectedRole
    )

    $fields = @($Line -split ',')
    if ($fields.Count -ne 8 -or $fields[0] -cne $ExpectedSchema -or $fields[1] -cne $ExpectedRole) {
        throw 'Publication row schema or role mismatch.'
    }
    if ($fields[2].Length -eq 0 -or $fields[4].Length -eq 0 -or $fields[7].Length -eq 0) {
        throw 'Publication row contains an empty identity field.'
    }
    $index = [long]0
    $generation = [long]0
    if ($fields[3] -cnotmatch '^[0-9]+$' -or -not [long]::TryParse($fields[3], [ref]$index) -or $index -lt [long]0) {
        throw "Invalid publication row index: $($fields[3])"
    }
    if ($fields[6] -cnotmatch '^[0-9]+$' -or -not [long]::TryParse($fields[6], [ref]$generation) -or
        $generation -le [long]0) {
        throw "Invalid publication row generation: $($fields[6])"
    }
    $elapsed = Read-P4PublicationPositiveInt64 $fields[5] 'elapsed_ns'
    return [pscustomobject]@{
        Schema = $fields[0]
        Role = $fields[1]
        Group = $fields[2]
        Index = $index
        Kind = $fields[4]
        ElapsedNs = $elapsed
        Generation = $generation
        Outcome = $fields[7]
    }
}

function Test-P4PublicationCapture {
    param(
        # [AllowEmptyString()] so a blank line inside the pulled publication CSV/status reaches the
        # row checks below instead of failing parameter binding with
        # ParameterArgumentValidationErrorEmptyStringNotAllowed. No contract change.
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][AllowEmptyString()][string[]]$Lines,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][AllowEmptyString()][string[]]$StatusLines,
        [Parameter(Mandatory = $true)][ValidateSet('baseline', 'candidate')][string]$Role,
        [AllowNull()][Collections.IDictionary]$PhaseContext,
        [AllowNull()][AllowEmptyCollection()][string[]]$IdentityLines,
        [AllowNull()][AllowEmptyCollection()][string[]]$InstrumentationLines
    )

    $phase = $null
    if ($PSBoundParameters.ContainsKey('PhaseContext')) {
        . (Join-Path $PSScriptRoot 'p4-indexed-memory-analysis.ps1')
        . (Join-Path $PSScriptRoot 'p4-indexed-preflight.ps1')
        $phase = Read-P4LayerPublicationIdentity $PhaseContext $IdentityLines $InstrumentationLines $Role
    } elseif ($PSBoundParameters.ContainsKey('IdentityLines') -or $PSBoundParameters.ContainsKey('InstrumentationLines')) {
        throw 'Publication phase evidence requires an explicit phase context.'
    }
    $schema = if ($null -eq $phase) { 'nene-pixel-p4-indexed-publication-device-v1' } else { $phase.slot.schema }
    $header = 'schema,role,group,index,kind,elapsed_ns,generation,outcome'
    if ($Lines.Count -ne 55 -or $Lines[0] -cne $header) {
        throw 'Publication capture must contain the exact header and 54 journal rows.'
    }
    if ($StatusLines.Count -ne 1 -or $StatusLines[0] -cne 'complete') {
        throw 'Publication journal status must be exactly complete.'
    }
    if ($null -ne $phase) {
        foreach ($line in $Lines[1..54]) {
            $fields = @($line -split ',')
            if ($fields.Count -ne 8) { throw 'Malformed phase publication CSV row.' }
            foreach ($index in @(3, 5, 6)) {
                [void](Read-P4LayerMemoryInteger $fields[$index] 'publication row integer' -AllowZero:($index -eq 3))
            }
        }
    }

    $groups = [ordered]@{}
    $expectedGroups = if ($null -eq $phase) { @(Get-P4PublicationGroups $Role) } else { $phase.slot.groups }
    $offset = 1
    $expectedGeneration = [long]1
    foreach ($groupContract in $expectedGroups) {
        $warmups = [Collections.Generic.List[object]]::new()
        $samples = [Collections.Generic.List[object]]::new()
        foreach ($index in 0..4) {
            $row = Read-P4PublicationRow $Lines[$offset++] $schema $Role
            if ($row.Group -cne $groupContract.name -or $row.Kind -cne 'warmup' -or $row.Index -ne $index -or
                $row.Generation -ne $expectedGeneration -or $row.Outcome -cne 'written') {
                throw "Publication warmup order or identity mismatch in $($groupContract.name)."
            }
            if ($row.ElapsedNs -gt [long]5000000000) { throw 'Publication sample exceeded five seconds.' }
            $warmups.Add($row)
            $expectedGeneration++
        }
        foreach ($index in 0..19) {
            $row = Read-P4PublicationRow $Lines[$offset++] $schema $Role
            if ($row.Group -cne $groupContract.name -or $row.Kind -cne 'sample' -or $row.Index -ne $index -or
                $row.Generation -ne $expectedGeneration -or $row.Outcome -cne 'written') {
                throw "Publication sample order or identity mismatch in $($groupContract.name)."
            }
            if ($row.ElapsedNs -gt [long]5000000000) { throw 'Publication sample exceeded five seconds.' }
            $samples.Add($row)
            $expectedGeneration++
        }
        $minimumIndex = 0
        $maximumIndex = 0
        for ($index = 1; $index -lt $samples.Count; $index++) {
            if ($samples[$index].ElapsedNs -lt $samples[$minimumIndex].ElapsedNs) { $minimumIndex = $index }
            if ($samples[$index].ElapsedNs -gt $samples[$maximumIndex].ElapsedNs) { $maximumIndex = $index }
        }
        $summaryMinimum = Read-P4PublicationRow $Lines[$offset++] $schema $Role
        $summaryMaximum = Read-P4PublicationRow $Lines[$offset++] $schema $Role
        if ($summaryMinimum.Group -cne $groupContract.name -or $summaryMinimum.Kind -cne 'summary_min' -or
            $summaryMinimum.Index -ne $minimumIndex -or
            $summaryMinimum.Generation -ne $samples[$minimumIndex].Generation -or
            $summaryMinimum.ElapsedNs -ne $samples[$minimumIndex].ElapsedNs -or
            $summaryMinimum.Outcome -cne 'written') {
            throw "Publication minimum summary disagrees with raw samples in $($groupContract.name)."
        }
        if ($summaryMaximum.Group -cne $groupContract.name -or $summaryMaximum.Kind -cne 'summary_max' -or
            $summaryMaximum.Index -ne $maximumIndex -or
            $summaryMaximum.Generation -ne $samples[$maximumIndex].Generation -or
            $summaryMaximum.ElapsedNs -ne $samples[$maximumIndex].ElapsedNs -or
            $summaryMaximum.Outcome -cne 'written') {
            throw "Publication maximum summary disagrees with raw samples in $($groupContract.name)."
        }
        $groups[$groupContract.name] = [ordered]@{
            structural_byte_count = $groupContract.structural_byte_count
            sample_count = $samples.Count
            minimum_nanos = $samples[$minimumIndex].ElapsedNs
            maximum_nanos = $samples[$maximumIndex].ElapsedNs
            minimum_index = $minimumIndex
            maximum_index = $maximumIndex
        }
    }
    if ($offset -ne $Lines.Count -or $expectedGeneration -ne [long]51) {
        throw 'Publication journal contains an unexpected row population.'
    }
    $maximumNs = $groups[$expectedGroups[0].name].maximum_nanos
    $verdict = if ($Role -eq 'baseline' -or $maximumNs -le [long]250000000) {
        'valid-constants-retained'
    } else {
        'valid-constants-revision-required'
    }
    $rederivedQuietMs = $null
    $rederivedLatencyCapMs = $null
    if ($verdict -eq 'valid-constants-revision-required') {
        $rederivedQuietMs = [long]([Math]::Ceiling(([double]$maximumNs * 4.0) / 500000000.0) * 500.0)
        $rederivedLatencyCapMs = [long]([Math]::Ceiling(([double]$maximumNs * 20.0) / 500000000.0) * 500.0)
    }
    $analysis = [ordered]@{
        schema = $schema
        role = $Role
        verdict = $verdict
        sample_count = 40
        groups = $groups
        maximum_document_maximum_nanos = $maximumNs
        maximum_document_structural_byte_count = $expectedGroups[0].structural_byte_count
        rederived_quiet_ms = $rederivedQuietMs
        rederived_latency_cap_ms = $rederivedLatencyCapMs
    }
    if ($null -ne $phase) {
        $analysis.analysis_contract = 'nene-pixel-p4-layer-publication-analysis-v1'
        $analysis.phase_context = [ordered]@{}
        foreach ($name in $PhaseContext.Keys) { $analysis.phase_context[$name] = $PhaseContext[$name] }
        $analysis.publication_identity = $phase.identity
    }
    return $analysis
}

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

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
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Lines,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$StatusLines,
        [Parameter(Mandatory = $true)][ValidateSet('baseline', 'candidate')][string]$Role
    )

    $schema = 'nene-pixel-p4-indexed-publication-device-v1'
    $header = 'schema,role,group,index,kind,elapsed_ns,generation,outcome'
    if ($Lines.Count -ne 55 -or $Lines[0] -cne $header) {
        throw 'Publication capture must contain the exact header and 54 journal rows.'
    }
    if ($StatusLines.Count -ne 1 -or $StatusLines[0] -cne 'complete') {
        throw 'Publication journal status must be exactly complete.'
    }

    $groups = [ordered]@{}
    $expectedGroups = @(Get-P4PublicationGroups $Role)
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
    return [ordered]@{
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
}

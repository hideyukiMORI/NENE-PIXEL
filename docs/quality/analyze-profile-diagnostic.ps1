[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$InputPath,
    [Parameter(Mandatory)][string]$Session,
    [Parameter(Mandatory)][string]$OutputPath,
    [string]$AppApkSha256 = '',
    [string]$TestApkSha256 = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# This is an offline interpreter of the pinned observer's *observed prefix*.
# It deliberately has no logcat reader and no negative-publication verdict.
$script:ManifestSha = 'bb7e189835a8edbc2a7ab5cd94521759c1622cd72273df5987bc436efd2aa26c'
$script:PatchSha = '94f5dc8d8f4f93087832b0c10f422837ecb90a01592462816a63acf11ee63d4d'
$script:Revision = '314bcbdacc3b0816e56d5c7e2cd7f8d020c5b511'
$script:Tree = '323df6a7364d2097adcd5360aea6519b11bcac20'
$script:Schema = 'nene-pixel-profile-diagnostic-v1'
$script:Base = @('schema','session','iteration','role','pid','origin_ns','seq','t_ns','event')
$script:Observation = @('pending','published','publishing','gate','flushes','requestable')
$script:Deadlines = @('quiet_deadline_ns','cap_deadline_ns','deadline_clock','suspended')
$script:AppEvents = @('process_begin','runtime_operation_set','runtime_autosave_set','publication_port_begin','publication_port_end','publication_runtime_complete','publication_incomplete','scheduler_sample','fold_branch','fold_committed','scheduler_flush','scheduler_decision','scheduler_request_begin','scheduler_request_end','scheduler_request_incomplete','diagnostic_invalid')
$script:ProducerEvents = @('process_begin','iteration_begin','launch_begin','launch_end','recovery_discard_begin','recovery_discard_end','pencil_begin','pencil_end','dirty_observed','undo_begin','undo_end','clean_observed','ui_block_end','diagnostic_invalid')
$script:Outcome = @('none','published','deferred','offer_pending','stale','generation_exhausted','failed','uncertain')
$script:PortOutcome = @('published','stale','generation_exhausted','failed','uncertain')
$script:RequestOutcome = @('published','no_capture','deferred','offer_pending','unavailable','stale','generation_exhausted','identity_exhausted','failed','uncertain')
$script:Line = 0

function Fail([string]$Code, [int]$At = $script:Line) { throw [System.Exception]::new("DIAG:$Code`:$At") }
function Sha([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Has([System.Collections.IDictionary]$Map, [string]$Key) { $Map.Contains($Key) }
function New-Map { [System.Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal) }

function Fields([string]$Event, [string]$Role, [System.Collections.IDictionary]$Map) {
    $fields = @($script:Base)
    if ($Role -ceq 'producer') {
        if ($Event -ceq 'diagnostic_invalid') { $fields += 'reason' }
        return $fields
    }
    switch -CaseSensitive ($Event) {
        'runtime_operation_set' {
            $fields += @('set_id','stage','old_gate','new_gate','changed')
            foreach ($prefix in @('old','new')) { $fields += @("${prefix}_phase","${prefix}_recovery","${prefix}_last_outcome") }
        }
        'runtime_autosave_set' { $fields += @('set_id','stage','old_pending','old_published','old_publishing','new_pending','new_published','new_publishing','old_outcome','new_outcome','changed') }
        'publication_port_begin' { $fields += @('token','operation') }
        'publication_port_end' { $fields += @('token','operation','result') }
        'publication_runtime_complete' { $fields += @('token','operation','result') }
        'publication_incomplete' { $fields += @('token','operation','reason') }
        'scheduler_sample' { $fields += @('sample_id') + $script:Observation; if ($Map['gate'].ValueKind -ne [System.Text.Json.JsonValueKind]::Null) { $fields += @('gate_phase','gate_recovery','gate_last_outcome') } }
        'fold_branch' { $fields += @('sample_id','attempt_id','branch','fold_now_ns','deadline_clock','helper_result') }
        'fold_committed' { $fields += @('sample_id','attempt_id') + $script:Observation + $script:Deadlines; if ($Map['gate'].ValueKind -ne [System.Text.Json.JsonValueKind]::Null) { $fields += @('gate_phase','gate_recovery','gate_last_outcome') } }
        'scheduler_flush' { $fields += 'count' }
        'scheduler_decision' { $fields += @('branch','decision_now_ns','due_ns') + $script:Observation + $script:Deadlines; if ($Map['gate'].ValueKind -ne [System.Text.Json.JsonValueKind]::Null) { $fields += @('gate_phase','gate_recovery','gate_last_outcome') } }
        'scheduler_request_begin' { $fields += 'token' }
        'scheduler_request_end' { $fields += @('token','result') }
        'scheduler_request_incomplete' { $fields += 'token' }
        'diagnostic_invalid' { $fields += 'reason' }
    }
    return $fields
}

function Read-Field([System.Text.Json.JsonElement]$Element, [string]$Name, [string]$Event, [string]$Role) {
    $kind = $Element.ValueKind
    $nullable = @('old_pending','old_published','old_publishing','new_pending','new_published','new_publishing','pending','published','publishing','gate','quiet_deadline_ns','cap_deadline_ns','due_ns','helper_result') -ccontains $Name
    if ($kind -eq [System.Text.Json.JsonValueKind]::Null) {
        if (-not $nullable) { Fail 'type' }
        return $null
    }
    $boolean = @('changed','requestable','suspended','helper_result') -ccontains $Name
    $numeric = @('iteration','pid','origin_ns','seq','t_ns','set_id','old_gate','new_gate','old_pending','old_published','old_publishing','new_pending','new_published','new_publishing','token','operation','sample_id','attempt_id','fold_now_ns','pending','published','publishing','gate','flushes','quiet_deadline_ns','cap_deadline_ns','count','decision_now_ns','due_ns') -ccontains $Name
    if ($boolean) {
        if ($kind -eq [System.Text.Json.JsonValueKind]::True) { return $true }
        if ($kind -eq [System.Text.Json.JsonValueKind]::False) { return $false }
        Fail 'type'
    }
    if ($numeric) {
        if ($kind -ne [System.Text.Json.JsonValueKind]::Number) { Fail 'type' }
        $value = [long]0
        if (-not $Element.TryGetInt64([ref]$value)) { Fail 'type' }
        if ((@('token','operation','gate','old_gate','new_gate','pending','published','publishing','old_pending','old_published','old_publishing','new_pending','new_published','new_publishing') -ccontains $Name) -and ($value -lt 1 -or $value -gt 128)) { Fail 'bounds' }
        if ((@('seq','pid','set_id','sample_id','attempt_id') -ccontains $Name) -and $value -lt 1) { Fail 'bounds' }
        if ($Name -ceq 'pid' -and $value -gt [int]::MaxValue) { Fail 'bounds' }
        if ((@('flushes','count','due_ns') -ccontains $Name) -and $value -lt 0) { Fail 'bounds' }
        if ($Name -ceq 'iteration' -and ($value -lt 0 -or $value -gt 15)) { Fail 'bounds' }
        if ((@('origin_ns','t_ns') -ccontains $Name) -and $value -lt 0) { Fail 'bounds' }
        return $value
    }
    if ($kind -ne [System.Text.Json.JsonValueKind]::String) { Fail 'type' }
    $value = $Element.GetString()
    $allowed = switch -CaseSensitive ($Name) {
        'schema' { @($script:Schema) }
        'role' { @('producer','app') }
        'event' { if ($Role -ceq 'producer') { $script:ProducerEvents } else { $script:AppEvents } }
        'stage' { @('intent','complete') }
        'branch' { if ($Event -ceq 'fold_branch') { @('no_requestable','flush','retimed','previous_pending_absent','same_pending','publishing_short_circuit','published_helper') } else { @('no_due','wait','due','cas_retry') } }
        'deadline_clock' { @('scheduler_nano_time') }
        'result' { if ($Event -ceq 'publication_port_end') { $script:PortOutcome } else { $script:RequestOutcome } }
        'reason' { if ($Event -ceq 'publication_incomplete') { @('cancelled','threw') } elseif ($Role -ceq 'producer') { @('event_limit') } else { @('token_limit','operation_limit','unsupported_gate','event_limit') } }
        { $_ -cin @('old_outcome','new_outcome') } { $script:Outcome }
        { $_ -cin @('old_last_outcome','new_last_outcome','gate_last_outcome') } { @('none','recovery_declined') }
        { $_ -clike '*_phase' } { @('initializing','idle','discarding') }
        { $_ -clike '*_recovery' } { @('initializing','clear','unadopted_candidate','unknown') }
        default { @() }
    }
    if ($Name -ceq 'session') {
        if ($value -cnotmatch '^[A-Za-z0-9_-]{1,64}$') { Fail 'type' }
    } elseif ($allowed -cnotcontains $value) { Fail 'enum' }
    return $value
}

function Parse-Event([string]$Json) {
    $options = [System.Text.Json.JsonDocumentOptions]::new()
    $options.MaxDepth = 4
    try { $doc = [System.Text.Json.JsonDocument]::Parse($Json, $options) } catch { Fail 'json' }
    try {
        if ($doc.RootElement.ValueKind -ne [System.Text.Json.JsonValueKind]::Object) { Fail 'json' }
        $elements = New-Map
        foreach ($property in $doc.RootElement.EnumerateObject()) {
            if ($elements.ContainsKey($property.Name)) { Fail 'duplicate_key' }
            $elements.Add($property.Name, $property.Value)
        }
        foreach ($name in $script:Base) { if (-not $elements.ContainsKey($name)) { Fail 'fields' } }
        $roleElement = [System.Text.Json.JsonElement]$elements['role']
        $role = Read-Field $roleElement 'role' '' ''
        $eventElement = [System.Text.Json.JsonElement]$elements['event']
        $event = Read-Field $eventElement 'event' '' $role
        $required = @(Fields $event $role $elements)
        if ($elements.Count -ne $required.Count) { Fail 'fields' }
        foreach ($key in $elements.Keys) { if ($required -cnotcontains $key) { Fail 'fields' } }
        $record = New-Map
        foreach ($key in $required) { $record.Add($key, (Read-Field ([System.Text.Json.JsonElement]$elements[$key]) $key $event $role)) }
        if ($record['schema'] -cne $script:Schema -or $record['session'] -cne $Session) { Fail 'identity' }
        if ($role -ceq 'app' -and $record['iteration'] -lt 1) { Fail 'identity' }
        if ($role -ceq 'producer' -and $record['iteration'] -eq 0 -and $event -cne 'process_begin') { Fail 'identity' }
        if ($record['t_ns'] -lt $record['origin_ns']) { Fail 'clock' }
        if ($event -ceq 'runtime_operation_set' -and $record['changed'] -ne ($record['old_gate'] -ne $record['new_gate'])) { Fail 'contradiction' }
        foreach ($prefix in @('old','new','gate')) {
            $ordinal = if ($prefix -ceq 'gate') { 'gate' } else { "${prefix}_gate" }
            if ($record.ContainsKey($ordinal) -and $null -ne $record[$ordinal]) {
                foreach ($suffix in @('phase','recovery','last_outcome')) { if (-not $record.ContainsKey("${prefix}_$suffix")) { Fail 'fields' } }
            }
        }
        if ($record.ContainsKey('requestable') -and $record['requestable'] -ne (($null -ne $record['pending']) -and ($record['pending'] -ne $record['publishing']))) { Fail 'contradiction' }
        if ($event -ceq 'fold_branch' -and (($record['branch'] -ceq 'published_helper') -ne ($null -ne $record['helper_result']))) { Fail 'contradiction' }
        if ($event -ceq 'scheduler_decision') {
            $due = $record['due_ns']; $branch = $record['branch']
            if (($branch -ceq 'no_due' -and $null -ne $due) -or ($branch -ceq 'wait' -and ($null -eq $due -or $due -le 0)) -or ((@('due','cas_retry') -ccontains $branch) -and $due -ne 0)) { Fail 'contradiction' }
        }
        $record.Add('_line', $script:Line)
        return $record
    } finally { $doc.Dispose() }
}

function Validate-Stream([object[]]$Events, [string]$Role) {
    # JSONL export preserves records; sorting would repair a corrupted stream.
    $ordered = @($Events)
    $first = $ordered[0]
    if ($first['seq'] -ne 1 -or $first['event'] -cne 'process_begin') { $script:Line = $first['_line']; Fail 'sequence' }
    if ($Role -ceq 'producer' -and $first['iteration'] -ne 0) { $script:Line = $first['_line']; Fail 'identity' }
    $previous = $null
    $runtime = @{}; $samples = @{}; $requests = @{}; $ports = @{}
    $setId = 0; $setPhase = 0; $sampleId = 0; $openRequest = $null; $duePending = $null
    $maxToken = 0; $maxOperation = 0; $maxGate = 0
    $gateLabels = @{}; $operationTokens = @{}; $usedPortOperations = @{}
    $producerPhase = 'ready'; $producerIteration = 0
    for ($i = 0; $i -lt $ordered.Count; $i++) {
        $e = $ordered[$i]; $script:Line = $e['_line']
        if ($i -gt 0) {
            if ($e['seq'] -ne ($previous['seq'] + 1)) { Fail 'sequence' }
            if ($e['t_ns'] -lt $previous['t_ns']) { Fail 'clock' }
            if ($previous['event'] -ceq 'diagnostic_invalid') { Fail 'sequence' }
        }
        if ($e['event'] -ceq 'process_begin' -and $i -ne 0) { Fail 'sequence' }
        if ($e['event'] -ceq 'diagnostic_invalid') { Fail 'diagnostic_invalid' }
        if ($Role -ceq 'app') {
            foreach ($field in @('old_pending','old_published','old_publishing','new_pending','new_published','new_publishing','pending','published','publishing','token')) {
                if ($e.ContainsKey($field) -and $null -ne $e[$field] -and $e[$field] -gt $maxToken) {
                    if ($e[$field] -ne ($maxToken + 1)) { Fail 'ordinal' }
                    $maxToken = $e[$field]
                }
            }
            foreach ($field in @('old_gate','new_gate','gate')) {
                if ($e.ContainsKey($field) -and $null -ne $e[$field]) {
                    if ($e[$field] -gt $maxGate) {
                        if ($e[$field] -ne ($maxGate + 1)) { Fail 'ordinal' }
                        $maxGate = $e[$field]
                    }
                    $prefix = if ($field -ceq 'gate') { 'gate' } else { $field.Substring(0, $field.IndexOf('_')) }
                    $labels = "$($e["${prefix}_phase"])|$($e["${prefix}_recovery"])|$($e["${prefix}_last_outcome"])"
                    $ordinal = [string]$e[$field]
                    if ($gateLabels.ContainsKey($ordinal) -and $gateLabels[$ordinal] -cne $labels) { Fail 'contradiction' }
                    $gateLabels[$ordinal] = $labels
                }
            }
            if ($e.ContainsKey('operation')) {
                if ($e['operation'] -gt $maxOperation) {
                    if ($e['operation'] -ne ($maxOperation + 1)) { Fail 'ordinal' }
                    $maxOperation = $e['operation']
                }
                $operation = [string]$e['operation']
                if ($operationTokens.ContainsKey($operation) -and $operationTokens[$operation] -ne $e['token']) { Fail 'contradiction' }
                $operationTokens[$operation] = $e['token']
            }
        }
        if ($Role -ceq 'producer') {
            $name = $e['event']
            if ($i -gt 0) {
                if ($name -ceq 'iteration_begin') {
                    if ($producerPhase -notin @('ready','done') -or $e['iteration'] -ne ($producerIteration + 1)) { Fail 'pairing' }
                    $producerIteration = $e['iteration']; $producerPhase = 'launch_begin'
                } else {
                    if ($e['iteration'] -ne $producerIteration) { Fail 'identity' }
                    $next = switch ($producerPhase) {
                        'launch_begin' { @('launch_begin') }
                        'launch_end' { @('launch_end') }
                        'discard_or_pencil' { @('recovery_discard_begin','pencil_begin') }
                        'discard_end' { @('recovery_discard_end') }
                        'pencil_after_discard' { @('pencil_begin') }
                        'pencil_end' { @('pencil_end') }
                        'dirty' { @('dirty_observed') }
                        'undo' { @('undo_begin') }
                        'undo_end' { @('undo_end') }
                        'clean' { @('clean_observed') }
                        'ui' { @('ui_block_end') }
                        default { @() }
                    }
                    if ($next -cnotcontains $name) { Fail 'pairing' }
                    $producerPhase = switch -CaseSensitive ($name) {
                        'launch_begin' { 'launch_end' }; 'launch_end' { 'discard_or_pencil' }
                        'recovery_discard_begin' { 'discard_end' }; 'recovery_discard_end' { 'pencil_after_discard' }
                        'pencil_begin' { 'pencil_end' }; 'pencil_end' { 'dirty' }
                        'dirty_observed' { 'undo' }; 'undo_begin' { 'undo_end' }
                        'undo_end' { 'clean' }; 'clean_observed' { 'ui' }
                        'ui_block_end' { 'done' }
                    }
                }
            }
        } elseif ($i -gt 0) {
            $name = $e['event']
            switch -CaseSensitive ($name) {
                { $_ -cin @('runtime_operation_set','runtime_autosave_set') } {
                    $key = "$name/$($e['set_id'])"
                    $expected = @(@('runtime_operation_set','intent'),@('runtime_operation_set','complete'),@('runtime_autosave_set','intent'),@('runtime_autosave_set','complete'))
                    if ($setPhase -eq 0) {
                        if ($e['set_id'] -ne ($setId + 1)) { Fail 'pairing' }
                        $setId = $e['set_id']
                    } elseif ($e['set_id'] -ne $setId) { Fail 'pairing' }
                    if ($name -cne $expected[$setPhase][0] -or $e['stage'] -cne $expected[$setPhase][1]) { Fail 'pairing' }
                    $setPhase = ($setPhase + 1) % 4
                    if ($e['stage'] -ceq 'intent') { if ($runtime.ContainsKey($key)) { Fail 'pairing' }; $runtime[$key] = $e }
                    else {
                        if (-not $runtime.ContainsKey($key) -or $runtime[$key]['stage'] -cne 'intent') { Fail 'pairing' }
                        foreach ($field in $e.Keys) { if ($field -cnotin @('stage','seq','t_ns','_line') -and -not [object]::Equals($e[$field], $runtime[$key][$field])) { Fail 'contradiction' } }
                        $runtime[$key] = $e
                    }
                }
                'scheduler_sample' {
                    $key = [string]$e['sample_id']; if ($samples.ContainsKey($key)) { Fail 'pairing' }
                    if ($e['sample_id'] -ne ($sampleId + 1) -or ($sampleId -gt 0 -and -not $samples[[string]$sampleId].committed)) { Fail 'pairing' }
                    $sampleId = $e['sample_id']
                    $samples[$key] = @{ sample = $e; attempts = @{}; attemptTimes = @{}; committed = $false }
                }
                'fold_branch' {
                    $key = [string]$e['sample_id']; if (-not $samples.ContainsKey($key) -or $samples[$key].committed) { Fail 'pairing' }
                    $attempt = [string]$e['attempt_id']; if (-not $samples[$key].attempts.ContainsKey($attempt)) { $samples[$key].attempts[$attempt] = @() }
                    $branches = @($samples[$key].attempts[$attempt]); $branch = $e['branch']
                    if ($branches.Count -eq 0 -and $e['attempt_id'] -ne $samples[$key].attempts.Count) { Fail 'pairing' }
                    if ($branches.Count -eq 0 -and $e['attempt_id'] -gt 1 -and $samples[$key].attempts[[string]($e['attempt_id'] - 1)][0] -ceq 'retimed' -and $samples[$key].attempts[[string]($e['attempt_id'] - 1)].Count -ne 2) { Fail 'pairing' }
                    if ($branches.Count -eq 0 -and $branch -notin @('no_requestable','flush','retimed')) { Fail 'pairing' }
                    if ($branches.Count -eq 1 -and ($branches[0] -cne 'retimed' -or $branch -notin @('previous_pending_absent','same_pending','publishing_short_circuit','published_helper'))) { Fail 'pairing' }
                    if ($branches.Count -gt 1) { Fail 'pairing' }
                    if ($branches.Count -eq 1 -and $samples[$key].attemptTimes[$attempt] -ne $e['fold_now_ns']) { Fail 'clock' }
                    if ($branches.Count -eq 0) { $samples[$key].attemptTimes[$attempt] = $e['fold_now_ns'] }
                    $samples[$key].attempts[$attempt] = @($branches) + @($branch)
                }
                'fold_committed' {
                    $key = [string]$e['sample_id']; $attempt = [string]$e['attempt_id']
                    if (-not $samples.ContainsKey($key) -or $samples[$key].committed -or -not $samples[$key].attempts.ContainsKey($attempt)) { Fail 'pairing' }
                    if ($e['attempt_id'] -ne $samples[$key].attempts.Count -or ($samples[$key].attempts[$attempt][0] -ceq 'retimed' -and $samples[$key].attempts[$attempt].Count -ne 2)) { Fail 'pairing' }
                    foreach ($field in $script:Observation) { if (-not [object]::Equals($e[$field], $samples[$key].sample[$field])) { Fail 'contradiction' } }
                    foreach ($field in @('gate_phase','gate_recovery','gate_last_outcome')) { if ($e.ContainsKey($field) -ne $samples[$key].sample.ContainsKey($field)) { Fail 'contradiction' }; if ($e.ContainsKey($field) -and $e[$field] -cne $samples[$key].sample[$field]) { Fail 'contradiction' } }
                    $samples[$key].committed = $true
                }
                'scheduler_decision' {
                    if ($null -ne $openRequest -or $null -ne $duePending) { Fail 'pairing' }
                    if ($e['branch'] -ceq 'due') { if ($null -eq $e['pending']) { Fail 'contradiction' }; $duePending = [string]$e['pending'] }
                }
                'scheduler_request_begin' { $key = [string]$e['token']; if ($null -ne $openRequest -or $null -eq $duePending -or $duePending -cne $key) { Fail 'pairing' }; $requests[$key] = 'open'; $openRequest = $key; $duePending = $null }
                { $_ -cin @('scheduler_request_end','scheduler_request_incomplete') } { $key = [string]$e['token']; if ($null -eq $openRequest -or $openRequest -cne $key -or -not $requests.ContainsKey($key) -or $requests[$key] -ne 'open') { Fail 'pairing' }; $requests[$key] = 'closed'; $openRequest = $null }
                'publication_port_begin' { $key = "$($e['token'])/$($e['operation'])"; $operation = [string]$e['operation']; if ($ports.ContainsKey($key) -or $usedPortOperations.ContainsKey($operation)) { Fail 'pairing' }; $ports[$key] = 'open'; $usedPortOperations[$operation] = $true }
                'publication_port_end' { $key = "$($e['token'])/$($e['operation'])"; if (-not $ports.ContainsKey($key) -or $ports[$key] -ne 'open') { Fail 'pairing' }; $ports[$key] = 'port_end' }
                'publication_runtime_complete' { $key = "$($e['token'])/$($e['operation'])"; if (-not $ports.ContainsKey($key) -or $ports[$key] -ne 'port_end') { Fail 'pairing' }; $ports[$key] = 'closed' }
                'publication_incomplete' { $key = "$($e['token'])/$($e['operation'])"; if (-not $ports.ContainsKey($key) -or $ports[$key] -notin @('open','port_end')) { Fail 'pairing' }; $ports[$key] = 'closed' }
            }
        }
        $previous = $e
    }
    return $ordered
}

function Write-Report([object]$Report, [string]$Path) {
    $json = ConvertTo-Json -InputObject $Report -Depth 12 -Compress
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($json + "`n")
    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
    try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
}

$report = $null
try {
    if ($Session -cnotmatch '^[A-Za-z0-9_-]{1,64}$') { Fail 'argument' 0 }
    foreach ($hash in @($AppApkSha256,$TestApkSha256)) { if ($hash -ne '' -and $hash -cnotmatch '^[a-fA-F0-9]{64}$') { Fail 'argument' 0 } }
    if (-not [System.IO.File]::Exists($InputPath) -or [System.IO.File]::Exists($OutputPath)) { Fail 'path' 0 }
    $manifestPath = Join-Path $PSScriptRoot 'profile-diagnostic/manifest.json'
    $patchPath = Join-Path $PSScriptRoot 'profile-diagnostic/observer.patch'
    if ((Sha $manifestPath) -cne $script:ManifestSha -or (Sha $patchPath) -cne $script:PatchSha) { Fail 'kit_identity' 0 }
    $inputStream = [System.IO.File]::Open($InputPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::Read)
    try {
        if ($inputStream.Length -gt 134217728 -or $inputStream.Length -eq 0) { Fail 'file_bound' 0 }
        $bytes = [byte[]]::new($inputStream.Length)
        $inputStream.ReadExactly($bytes)
    } finally { $inputStream.Dispose() }
    if ($bytes.Length -gt 134217728) { Fail 'file_bound' 0 }
    $inputHash = [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
    $streams = @{}; $events = [System.Collections.Generic.List[object]]::new(); $start = 0
    while ($start -lt $bytes.Length) {
        $pos = [Array]::IndexOf[byte]($bytes, [byte]10, $start)
        if ($pos -lt 0) { $pos = $bytes.Length }
        $script:Line++
        $length = $pos - $start
        if ($length -gt 16384) { Fail 'line_bound' }
        if ($length -gt 0 -and $bytes[$pos - 1] -eq 13) { $length-- }
        if ($length -eq 0) { Fail 'json' }
        try { $lineText = [System.Text.UTF8Encoding]::new($false, $true).GetString($bytes, $start, $length) } catch { Fail 'utf8' }
        $e = Parse-Event $lineText
        if ($events.Count -ge 61952) { Fail 'event_bound' }
        $events.Add($e)
        $key = "$($e['role'])/$($e['pid'])/$($e['origin_ns'])"
        if (-not $streams.ContainsKey($key)) { $streams[$key] = [System.Collections.Generic.List[object]]::new() }
        $streams[$key].Add($e)
        $start = $pos + 1
    }
    if ($streams.Count -gt 16) { Fail 'stream_bound' 0 }
    $producer = $null; $appIterations = @{}; $identities = @{}; $streamSummaries = @(); $facts = [System.Collections.Generic.List[object]]::new(); $producerEvents = @()
    foreach ($entry in $streams.GetEnumerator()) {
        $list = @($entry.Value.ToArray()); $first = $list[0]; $role = $first['role']
        $identity = "$($first['pid'])/$($first['origin_ns'])"
        if ($identities.ContainsKey($identity) -and $identities[$identity] -cne $role) { $script:Line = $first['_line']; Fail 'identity' }
        $identities[$identity] = $role
        foreach ($e in $list) { if ($e['iteration'] -ne $first['iteration'] -and $role -ceq 'app') { $script:Line = $e['_line']; Fail 'identity' } }
        if ($role -ceq 'producer') { if ($null -ne $producer -or $list.Count -gt 512) { Fail 'stream_bound' 0 }; $producer = $entry.Key }
        else {
            if ($list.Count -gt 4096 -or $appIterations.ContainsKey([string]$first['iteration'])) { $script:Line = $first['_line']; Fail 'stream_bound' }
            $appIterations[[string]$first['iteration']] = $entry.Key
        }
        $ordered = @(Validate-Stream $list $role)
        if ($role -ceq 'producer') { $producerEvents = $ordered }
        $streamSummaries += [ordered]@{ kind='recorder_stream'; role=$role; pid=$first['pid']; origin_ns=$first['origin_ns']; iteration=$(if ($role -ceq 'app') { $first['iteration'] } else { $null }); count=$list.Count; first_seq=$ordered[0]['seq']; last_seq=$ordered[-1]['seq']; first_t_ns=$ordered[0]['t_ns']; last_t_ns=$ordered[-1]['t_ns'] }
        foreach ($e in $ordered) {
            if ($e['event'] -cin @('runtime_operation_set','runtime_autosave_set','scheduler_sample','fold_branch','fold_committed','scheduler_flush','scheduler_decision','scheduler_request_begin','scheduler_request_end','scheduler_request_incomplete','publication_port_begin','publication_port_end','publication_runtime_complete','publication_incomplete','iteration_begin','launch_begin','launch_end','recovery_discard_begin','recovery_discard_end','pencil_begin','pencil_end','dirty_observed','undo_begin','undo_end','clean_observed','ui_block_end')) {
                $details = [ordered]@{}
                foreach ($key in @('set_id','stage','changed','old_gate','new_gate','old_phase','old_recovery','old_last_outcome','new_phase','new_recovery','new_last_outcome','old_pending','old_published','old_publishing','new_pending','new_published','new_publishing','old_outcome','new_outcome','sample_id','attempt_id','branch','fold_now_ns','decision_now_ns','due_ns','count','deadline_clock','helper_result','pending','published','publishing','gate','gate_phase','gate_recovery','gate_last_outcome','flushes','requestable','quiet_deadline_ns','cap_deadline_ns','suspended','token','operation','result','reason')) { if ($e.ContainsKey($key)) { $details[$key] = $e[$key] } }
                $facts.Add([ordered]@{ event=$e['event']; role=$role; iteration=$e['iteration']; pid=$e['pid']; origin_ns=$e['origin_ns']; seq=$e['seq']; t_ns=$e['t_ns']; details=$details })
            }
        }
    }
    if ($null -eq $producer) { Fail 'producer_missing' 0 }
    foreach ($iteration in $appIterations.Keys) {
        $begin = @($producerEvents | Where-Object { $_['iteration'] -eq [int]$iteration -and $_['event'] -ceq 'iteration_begin' }).Count -gt 0
        $launch = @($producerEvents | Where-Object { $_['iteration'] -eq [int]$iteration -and $_['event'] -ceq 'launch_begin' }).Count -gt 0
        if (-not ($begin -and $launch)) { Fail 'identity' 0 }
    }
    $iterationSummaries = @()
    for ($iteration = 1; $iteration -le 15; $iteration++) {
        $p = @($producerEvents | Where-Object { $_['iteration'] -eq $iteration -and $_['event'] -cne 'process_begin' })
        $iterationSummaries += [ordered]@{ iteration=$iteration; producer_boundary_count=$p.Count; producer_ui_block_end_observed=(@($p | Where-Object { $_['event'] -ceq 'ui_block_end' }).Count -eq 1); app_stream_observed=$appIterations.ContainsKey([string]$iteration); absent_events='unknown' }
    }
    $report = [ordered]@{ schema='nene-pixel-profile-diagnostic-analysis-v1'; status='structurally_valid_observed_prefix'; tail_completeness='unproven'; publication_absence='unknown'; profile_acceptance=$false; session=$Session; analyzer_sha256=(Sha $PSCommandPath); source_revision=$script:Revision; source_tree=$script:Tree; observer_patch_sha256=$script:PatchSha; manifest_sha256=$script:ManifestSha; jsonl_sha256=$inputHash; jsonl_bytes=$bytes.Length; app_apk_sha256=$(if ($AppApkSha256) { $AppApkSha256.ToLowerInvariant() } else { $null }); test_apk_sha256=$(if ($TestApkSha256) { $TestApkSha256.ToLowerInvariant() } else { $null }); apk_identity_authentication='unproven'; event_count=$events.Count; stream_count=$streams.Count; streams=$streamSummaries; iterations=$iterationSummaries; observed_facts=$facts.ToArray() }
    Write-Report $report $OutputPath
    exit 0
} catch {
    $message = $_.Exception.Message
    $code = 'internal_error'; $at = 0
    if ($message -match '^DIAG:([a-z0-9_]+):([0-9]+)$') { $code = $Matches[1]; $at = [int]$Matches[2] }
    $failure = [ordered]@{ schema='nene-pixel-profile-diagnostic-analysis-v1'; status='invalid'; reason=$code; line=$at; profile_acceptance=$false }
    try { if ($OutputPath -and -not [System.IO.File]::Exists($OutputPath)) { Write-Report $failure $OutputPath } } catch { }
    [Console]::Error.WriteLine("invalid $code line $at")
    exit 1
}

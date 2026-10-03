param([Parameter(Mandatory)][string] $OutputDirectory,
    [ValidateSet('Maps', 'Flow', 'Failures', 'Frame', 'Compatibility')][string] $CaseGroup = 'Maps',
    [ValidateRange(0, 5)][int] $FlowStartIndex = 0)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/p4-layer-slot-cleanup.ps1')
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Cleanup validator output exists' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$repository = Split-Path (Split-Path $PSScriptRoot)
$script:cleanupValidatorDirectory = $PSScriptRoot
$phase = 'nene-pixel-p4-layer-phase-verification-v1'; $hash = 'a' * 64
$script:checks = [Collections.Generic.List[string]]::new()
$script:refusals = [Collections.Generic.List[string]]::new()
$script:events = [Collections.Generic.List[string]]::new()
$script:clock = $null; $script:mode = ''; $script:activeManifest = $null
$watch = [Diagnostics.Stopwatch]::StartNew(); $status = 'failure'; $failure = $null
function Check([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw "Cleanup validator: $Message" }
    $script:checks.Add($Message)
}
function Refuses([scriptblock] $Action, [string] $Pattern, [string] $Message) {
    $caught = $null
    try { & $Action | Out-Null } catch { $caught = $_.Exception.Message }
    if ($null -eq $caught -or $caught -notlike $Pattern) { throw "Expected $Pattern for $Message; observed $caught" }
    $script:refusals.Add("$Message : $caught"); $script:checks.Add($Message)
}
function Read-Ast([string] $Path) {
    $tokens = $null; $errors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile($Path, [ref]$tokens, [ref]$errors)
    if ($errors.Count -ne 0) { throw "Parse failed: $Path" }
    return $ast
}
function Function-Text($Ast, [string] $Name) {
    $node = $Ast.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $Name }, $true)
    if ($null -eq $node) { throw "Function missing: $Name" }
    return $node.Extent.Text
}
function Save-Json([string] $Path, $Value) { Write-P4SessionJson $Path $Value }
$fixtureAst = Read-Ast (Join-Path $PSScriptRoot 'validate-p4-layer-device-lanes.ps1')
. ([scriptblock]::Create((Function-Text $fixtureAst 'New-Manifest')))
$fixtureAst = Read-Ast (Join-Path $PSScriptRoot 'validate-p4-layer-slot-routing.ps1')
. ([scriptblock]::Create((Function-Text $fixtureAst 'New-StagingFixture').Replace('$PSScriptRoot', '$script:cleanupValidatorDirectory')))
function Fixture([string] $Name, [string] $SlotId, [string] $Mode = '', [switch] $NoFrame) {
    $script:mode = $Mode; $script:events.Clear(); $script:clock = $null
    $manifest = New-Manifest
    $base = Join-Path $OutputDirectory $Name
    [void][IO.Directory]::CreateDirectory($base)
    $manifest.output_directory = Join-Path $base 'slots'
    $manifest.frame_experiment = @{ directory = Join-Path $base 'frames' }
    $manifest.tools = @{ adb = @{ path = (Get-Command pwsh).Source } }
    $proof = Join-Path $base 'preservation.json'; Save-Json $proof @{ fixture = $true }
    $manifest.device.asset_preservation.path = $proof
    $manifest.device.asset_preservation.sha256 = Get-P4SnapshotHash $proof
    $slot = Get-P4ExecutionSlot $phase $SlotId
    $directory = Join-Path $manifest.output_directory $slot.id
    [void][IO.Directory]::CreateDirectory($directory)
    $context = @{ repository_root = $repository; output_directory = $directory;
        adb_path = $manifest.tools.adb.path; serial = $manifest.device.serial }
    if ($slot.lane -ceq 'frame' -and -not $NoFrame) {
        $contract = Get-P4FrameExecutionContract $phase $slot.id
        $frame = Join-Path $manifest.frame_experiment.directory $contract.frame_directory_name
        [void][IO.Directory]::CreateDirectory($frame)
        [IO.File]::WriteAllText((Join-Path $frame 'partial.txt'), 'retained frame bytes')
        $plan = Get-P4DeviceLanePlan $manifest $slot $hash
        New-StagingFixture $slot $plan (Join-Path $directory 'fixture-preparation') | Out-Null
    }
    $script:activeManifest = $manifest
    return @{ Manifest = $manifest; Slot = $slot; Context = $context }
}
function Event([string] $Name, $Context) {
    if ($null -ne $Context) {
        if ($Context.operation_budget.timeout_seconds -ne 3000) { throw 'Component got a different operation allowance' }
        if ($null -eq $script:clock) { $script:clock = $Context.operation_budget.timer }
        elseif (-not [object]::ReferenceEquals($script:clock, $Context.operation_budget.timer)) { throw 'Component restarted the operation clock' }
    }
    $script:events.Add($Name)
}
function Invoke-BoundedNativeCommand {
    param($RepositoryRoot, $LogPath, $ExecutablePath, $NativeArguments, $TimeoutSeconds)
    $package = $NativeArguments[-1]
    $stop = $NativeArguments -ccontains 'force-stop'
    $kind = if ($stop) { 'stop' } else { 'pidof' }
    Event "$kind`:$package" $null
    Save-Json $LogPath @{ arguments = $NativeArguments; timeout = $TimeoutSeconds }
    $first = $script:activeManifest.roles.candidate.artifacts.app_debug.target_package
    if ($script:mode -ceq 'stop' -and $stop -and $package -ceq $first) {
        return [pscustomobject]@{ ExitCode = 2; OutputLines = @('synthetic stop failure') }
    }
    if (-not $stop -and $package -ceq $first -and ($script:mode -ceq 'absence' -or
            ($script:mode -ceq 'restart' -and $LogPath -like '*after-debug-access*'))) {
        return [pscustomobject]@{ ExitCode = 0; OutputLines = @('123') }
    }
    return [pscustomobject]@{ ExitCode = $(if ($stop) { 0 } else { 1 }); OutputLines = @() }
}
function Read-P4VerifiedPreservation {
    param($Path, $Context)
    Event 'preservation' $Context
    if ([IO.Path]::GetDirectoryName($Path) -cne $Context.output_directory) { throw 'Wrong preservation proof context directory' }
    if ($script:mode -ceq 'preservation') { throw 'Synthetic preservation refusal' }
    return [pscustomobject]@{ Hash = Get-P4SnapshotHash $Path; Record = [pscustomobject]@{
        session = $script:activeManifest.device.asset_preservation.session; experiment_id = $script:activeManifest.experiment_id } }
}
function Assert-P4InstalledApk {
    param($Context, $Manifest, $Role, $Kind)
    Event "install:$Role/$Kind" $Context
    if ($Kind -cne 'app_debug') { throw 'Cleanup installed an auxiliary APK' }
    Save-Json (Join-Path $Context.output_directory 'install-app_debug-intent.json') @{ role = $Role; kind = $Kind }
    $record = @{ status = $(if ($script:mode -ceq 'install') { 'failure' } else { 'success' }) }
    Save-Json (Join-Path $Context.output_directory 'install-app_debug.json') $record
    if ($script:mode -ceq 'install') { throw 'Synthetic install failure' }
    return $record
}
function Copy-P4LayerPrivateReports {
    param($Context, $Plan)
    [void](Get-P4OperationTimeout $Context 30)
    Event 'reports' $Context
    Save-Json (Join-Path $Context.output_directory 'private-report-capture.json') @{ fixture = $true }
    [IO.File]::WriteAllText((Join-Path $Context.output_directory 'retained-report-partial.txt'), 'partial')
    if ($script:mode -ceq 'reports') { throw 'Synthetic report failure' }
    if ($script:mode -ceq 'expired') { $Context.operation_budget.timer.Stop() }
}
function New-P4PrivateSnapshot {
    param($Context, $Stage, $MaximumArchiveBytes, $MaximumApkBytes)
    [void](Get-P4OperationTimeout $Context 30)
    $name = Split-Path -Leaf $Context.output_directory
    Event $name $Context
    if ($MaximumArchiveBytes -ne 268435456 -or $MaximumApkBytes -ne 134217728) { throw 'Archive byte caps differ' }
    [IO.File]::WriteAllText((Join-Path $Context.output_directory 'retained-archive-partial.bin'), 'partial')
    $snapshot = @{ status = 'verified-snapshot'; serial = $Context.serial; package = $Context.package;
        apk_sha256 = $(if ($script:mode -ceq 'archive-identity' -and $name -ceq 'archive-app') { 'f' * 64 } else { 'd' * 64 }) }
    Save-Json (Join-Path $Context.output_directory 'capture-snapshot.json') $snapshot
    if ($script:mode -ceq 'archive' -and $name -ceq 'archive-app') { throw 'Synthetic archive failure' }
    return $snapshot
}
function Invoke-P4PrivateSlotReset {
    param($Context, $PreservationPath, $PreservationSha256, $PreflightSha256, $SlotId, $ExpectedInstalledApkHash)
    [void](Get-P4OperationTimeout $Context 30)
    Event 'reset' $Context
    if ($PreservationSha256 -cne $script:activeManifest.device.asset_preservation.sha256 -or
        $PreflightSha256 -cne $hash -or $ExpectedInstalledApkHash -cne ('d' * 64)) { throw 'Reset identity arguments differ' }
    $path = Join-Path $Context.output_directory 'result.json'
    Save-Json $path @{ status = $(if ($script:mode -ceq 'reset') { 'failure' } else { 'reset' }) }
    if ($script:mode -ceq 'reset') { throw 'Synthetic reset failure' }
    return [pscustomobject]@{ ResultPath = $path; Record = @{ status = 'reset' } }
}
try {
    if ($CaseGroup -ceq 'Maps') {
        $manifest = New-Manifest
        foreach ($slot in @(Get-P4SlotCatalog $phase)) {
            $plan = Get-P4LayerSlotCleanupPlan $manifest $slot $hash
            $expected = if ($slot.lane -ceq 'publication') { 'app|publication' }
                elseif ($slot.lane -ceq 'frame' -and $slot.group_id -ceq 'single') { 'app' } else { 'app|test-provider' }
            Check (($plan.archives.name -join '|') -ceq $expected -and $plan.artifact_role -ceq $slot.artifact_role -and
                $plan.comparison_role -ceq $slot.role -and $plan.timeout_seconds -eq 3000 -and
                $plan.maximum_archive_bytes -eq 268435456 -and $plan.maximum_apk_bytes -eq 134217728) "cleanup map $($slot.id)"
        }
        $slot = Get-P4ExecutionSlot $phase 'memory-layers16-candidate-1'
        Refuses { Get-P4LayerSlotCleanupPlan $manifest $slot 'foreign' } '*ManifestSha256*' 'cleanup rejects unbound preflight'
        $bad = @{}; foreach ($key in $slot.Keys) { $bad[$key] = $slot[$key] }; $bad.role = 'baseline'
        Refuses { Get-P4LayerSlotCleanupPlan $manifest $bad $hash } '*Phase slot drift*' 'cleanup rejects a changed comparison role'
    }
    if ($CaseGroup -ceq 'Flow') {
        $flowIds = @('memory-layers16-candidate-1', 'publication-layers16-candidate', 'saf-save-layers16-candidate',
            'frame-1-single-baseline-decision', 'frame-5-layers16-baseline-decision', 'frame-9-underlay-baseline-decision')
        foreach ($id in @($flowIds | Select-Object -Skip $FlowStartIndex)) {
            $fixture = Fixture $id $id
            $result = Invoke-P4LayerSlotCleanup $fixture.Context $fixture.Manifest $fixture.Slot $hash $true
            $record = $result.Record
            Check ($record.status -ceq 'captured-and-reset' -and $record.packages_stopped -and $record.errors.Count -eq 0) "successful cleanup $id"
            $observedEvents = $script:events.ToArray()
            Check (@($observedEvents | Where-Object { $_ -like 'stop:*' }).Count -eq 3 -and
                [array]::IndexOf($observedEvents, 'preservation') -gt [array]::LastIndexOf($observedEvents, "stop:$($fixture.Manifest.roles.candidate.artifacts.publication_test.test_package)") -and
                $observedEvents[-1] -ceq 'reset' -and $null -ne $script:clock) "all writer stops precede shared-clock capture and final reset $id"
            $expectedPlan = Get-P4LayerSlotCleanupPlan $fixture.Manifest $fixture.Slot $hash
            $archives = @($expectedPlan.archives)
            Check ($record.archives.Count -eq $archives.Count -and @($record.archives | Where-Object status -cne 'archived').Count -eq 0 -and
                $record.reset.sha256 -ceq (Get-P4SnapshotHash $record.reset.path)) "required archives/reset bound $id"
            $frame = $fixture.Slot.lane -ceq 'frame'
            Check (($frame -and $record.report_capture.mode -ceq 'captured-frame-fixture' -and $record.frame_record.sha256 -ceq
                    (Get-P4SnapshotHash (Join-Path $fixture.Context.output_directory 'frame-slot.json')) -and $observedEvents -cnotcontains 'reports') -or
                (-not $frame -and $record.report_capture.mode -ceq 'stopped-reports' -and $observedEvents -ccontains 'reports')) "canonical report path $id"
            $before = $script:events.Count
            Refuses { Invoke-P4LayerSlotCleanup $fixture.Context $fixture.Manifest $fixture.Slot $hash $true } '*component directory already exists*' "cleanup cannot repeat $id"
            Check ($script:events.Count -eq $before) "occupied cleanup starts no device work $id"
        }
    }
    if ($CaseGroup -ceq 'Failures') {
        foreach ($mode in @('stop', 'absence', 'preservation', 'install', 'restart', 'reports', 'archive', 'archive-identity', 'expired', 'reset')) {
            $fixture = Fixture $mode 'memory-layers16-candidate-1' $mode
            Refuses { Invoke-P4LayerSlotCleanup $fixture.Context $fixture.Manifest $fixture.Slot $hash $true } '*Phase cleanup failed*' "$mode propagates cleanup failure"
            $record = Get-Content (Join-Path $fixture.Context.output_directory 'phase-cleanup/result.json') -Raw | ConvertFrom-Json
            Check ($record.status -ceq 'failure' -and $record.errors.Count -gt 0) "$mode retains failed cleanup record"
            if ($mode -in @('stop', 'absence')) {
                Check (-not $record.packages_stopped -and $script:events -cnotcontains 'preservation' -and
                    @($script:events | Where-Object { $_ -like 'stop:*' }).Count -eq 3) "$mode attempts every stop without private work"
            }
            if ($mode -cne 'reset') { Check ($script:events -cnotcontains 'reset') "$mode cannot reset incomplete capture" }
            if ($mode -in @('reports', 'archive', 'archive-identity')) {
                Check ($script:events -ccontains 'archive-app' -and $script:events -ccontains 'archive-test-provider' -and
                    (Test-Path (Join-Path $fixture.Context.output_directory 'phase-cleanup/archive-app/retained-archive-partial.bin'))) "$mode retains partial archives and attempts remaining source"
            }
            if ($mode -ceq 'expired') {
                Check (@($script:events | Where-Object { $_ -like 'archive-*' }).Count -eq 0) 'expired shared clock prevents new archive native work'
            }
        }
        foreach ($key in @('output_directory', 'repository_root', 'adb_path', 'serial')) {
            $fixture = Fixture "context-$key" 'memory-layers16-candidate-1'
            $fixture.Context[$key] = $(if ($key -ceq 'serial') { 'foreign' } else { Join-Path $OutputDirectory 'foreign' })
            Refuses { Invoke-P4LayerSlotCleanup $fixture.Context $fixture.Manifest $fixture.Slot $hash $true } '*Phase cleanup*' "foreign context $key refused"
            Check ($script:events.Count -eq 0) "foreign context $key starts no native work"
        }
    }
    if ($CaseGroup -ceq 'Frame') {
        $fixture = Fixture 'existing-record' 'frame-1-single-baseline-decision'
        $first = Complete-P4LayerPartialFrameRecord $fixture.Manifest $fixture.Slot $hash $fixture.Context
        $second = Complete-P4LayerPartialFrameRecord $fixture.Manifest $fixture.Slot $hash $fixture.Context
        Check ($first.sha256 -ceq $second.sha256) 'partial frame inventory reuses an existing record without overwrite'
        $raw = Get-Content $first.path -Raw | ConvertFrom-Json
        Check ($raw.schema -ceq 'nene-pixel-p4-frame-slot-v2' -and $raw.files.Count -eq 1 -and
            $raw.files[0].relative_path -ceq 'partial.txt') 'outer-kill recovery inventories canonical partial bytes'
        foreach ($field in @('schema', 'slot_id', 'artifact_role', 'slot_directory', 'phase_context')) {
            $fixture = Fixture "foreign-$field" 'frame-1-single-baseline-decision'
            $record = Complete-P4LayerPartialFrameRecord $fixture.Manifest $fixture.Slot $hash $fixture.Context
            $bad = Get-Content $record.path -Raw | ConvertFrom-Json -AsHashtable
            if ($field -ceq 'phase_context') { $bad.phase_context.preflight_sha256 = 'b' * 64 }
            elseif ($field -ceq 'slot_directory') { $bad[$field] = $OutputDirectory }
            else { $bad[$field] = 'foreign' }
            [IO.File]::WriteAllText($record.path, (ConvertTo-Json $bad -Depth 12))
            $before = Get-P4SnapshotHash $record.path
            Refuses { Complete-P4LayerPartialFrameRecord $fixture.Manifest $fixture.Slot $hash $fixture.Context } '*' "existing frame refuses foreign $field"
            Check ((Get-P4SnapshotHash $record.path) -ceq $before) "foreign frame $field stays retained"
        }
        $fixture = Fixture 'failed-frame' 'frame-5-layers16-baseline-decision'
        $result = Invoke-P4LayerSlotCleanup $fixture.Context $fixture.Manifest $fixture.Slot $hash $false
        Check ($result.Record.report_capture.mode -ceq 'diagnostic-fixture-recovery' -and $script:events -ccontains 'reports' -and
            (Test-Path (Join-Path $fixture.Context.output_directory 'phase-cleanup/frame-fixture-recovery/private-report-capture.json'))) 'failed frame recovers fixture into a fresh diagnostic directory'
        $fixture = Fixture 'successful-frame-missing' 'frame-1-single-baseline-decision' -NoFrame
        Refuses { Invoke-P4LayerSlotCleanup $fixture.Context $fixture.Manifest $fixture.Slot $hash $true } '*no canonical capture directory*' 'successful frame cannot omit capture directory'
        Check ($script:events -cnotcontains 'reset' -and $script:events -ccontains 'archive-app') 'missing frame retains archive without resetting'
        $fixture = Fixture 'early-failed-single' 'frame-1-single-baseline-decision' -NoFrame
        $result = Invoke-P4LayerSlotCleanup $fixture.Context $fixture.Manifest $fixture.Slot $hash $false
        Check ($null -eq $result.Record.frame_record -and $result.Record.report_capture.mode -ceq 'not-required' -and
            $result.Record.collector_succeeded -eq $false) 'early failed single keeps cleanup distinct from collector success'
    }
    if ($CaseGroup -ceq 'Compatibility') {
        $routingPath = Join-Path $PSScriptRoot 'measurements/p4-layer-slot-routing.ps1'
        $currentAst = Read-Ast $routingPath
        $oldText = (& git -C $repository show '52606fa:docs/quality/measurements/p4-layer-slot-routing.ps1') -join "`n"
        if ($LASTEXITCODE -ne 0) { throw 'Previous routing source unavailable' }
        $tokens = $null; $errors = $null
        $oldAst = [Management.Automation.Language.Parser]::ParseInput($oldText, [ref]$tokens, [ref]$errors)
        if ($errors.Count -ne 0) { throw 'Previous routing parse failed' }
        foreach ($node in $oldAst.FindAll({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] }, $false)) {
            if ($node.Name -ceq 'Invoke-P4LayerFrameAnalysis') { continue }
            Check ((Function-Text $currentAst $node.Name).Replace("`r`n", "`n") -ceq $node.Extent.Text.Replace("`r`n", "`n")) "routing function unchanged $($node.Name)"
        }
        $old = (Function-Text $oldAst 'Invoke-P4LayerFrameAnalysis').Replace("`r`n", "`n")
        $current = (Function-Text $currentAst 'Invoke-P4LayerFrameAnalysis').Replace("`r`n", "`n")
        $reader = (Function-Text $currentAst 'Read-P4LayerFrameStagingEvidence').Replace("`r`n", "`n")
        $start = $old.IndexOf('$stagingPath ='); $end = $old.IndexOf('$result.capture_sha256 =')
        $body = $old.Substring($start, $end - $start)
        $readerStart = $reader.IndexOf('$stagingPath ='); $readerEnd = $reader.IndexOf('return $stagingPath')
        Check ($body.TrimEnd() -ceq $reader.Substring($readerStart, $readerEnd - $readerStart).TrimEnd()) 'shared staging reader keeps the exact accepted verification body'
        $delegated = $old.Substring(0, $start) + 'Read-P4LayerFrameStagingEvidence $Manifest $Slot $ManifestSha256 $Directory $context | Out-Null' + "`n    " + $old.Substring($end)
        Check ($delegated -ceq $current) 'analyzer differs only by delegation with the same arguments'
        foreach ($file in @('p4-device-private-snapshot.ps1', 'p4-device-private-slot-reset.ps1', 'p4-device-private-transport.ps1',
                'p4-indexed-device-lanes.ps1', 'p4-indexed-frame-analysis.ps1', 'p4-indexed-memory-analysis.ps1', 'p4-indexed-publication-analysis.ps1')) {
            $diff = & git -C $repository diff 52606fa -- "docs/quality/measurements/$file"
            Check ($LASTEXITCODE -eq 0 -and @($diff).Count -eq 0) "accepted dependency unchanged $file"
        }
        [void](Read-Ast (Join-Path $PSScriptRoot 'measurements/p4-layer-slot-cleanup.ps1'))
        [void](Read-Ast $PSCommandPath)
        Check $true 'cleanup and validator parse'
        foreach ($field in @('status', 'slot_id', 'native_accepted')) {
            $fixture = Fixture "staging-$field" 'frame-5-layers16-baseline-decision'
            $path = Join-Path $fixture.Context.output_directory 'fixture-preparation/staging-result.json'
            $record = Get-Content $path -Raw | ConvertFrom-Json -AsHashtable
            $record[$field] = 'wrong'
            [IO.File]::WriteAllText($path, (ConvertTo-Json $record -Depth 20))
            $context = Get-P4LayerFramePhaseContext $fixture.Manifest $fixture.Slot $hash
            Refuses { Read-P4LayerFrameStagingEvidence $fixture.Manifest $fixture.Slot $hash $fixture.Context.output_directory $context } '*not successful*' "shared staging reader refuses $field"
        }
    }
    $status = 'pass'
} catch { $failure = $_; throw }
finally {
    $result = [ordered]@{ group = $CaseGroup; flow_start_index = $FlowStartIndex; status = $status; checks = $script:checks.Count;
        accepted = $script:checks.ToArray(); refusals = $script:refusals.ToArray(); events = $script:events.ToArray();
        elapsed_seconds = $watch.Elapsed.TotalSeconds; source_sha256 = [ordered]@{
            snapshot = Get-P4SnapshotSources; cleanup = Get-P4SnapshotHash $script:P4LayerCleanupHelperPath;
            reset = Get-P4SnapshotHash $script:P4SlotResetHelperPath;
            routing = Get-P4SnapshotHash (Join-Path $PSScriptRoot 'measurements/p4-layer-slot-routing.ps1') };
        error = $(if ($null -eq $failure) { $null } else { [string]$failure }) }
    Write-P4SessionJson (Join-Path $OutputDirectory 'result.json') $result
    [pscustomobject]$result | Select-Object group, status, checks, elapsed_seconds | ConvertTo-Json -Compress | Write-Output
}

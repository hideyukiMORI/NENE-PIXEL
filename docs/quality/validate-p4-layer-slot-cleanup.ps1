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
$script:clock = $null; $script:mode = ''; $script:activeManifest = $null; $script:expectedTimeout = 0
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
# Independent restatement of the cleanup clock: W writers x (stop + absence + post-access absence) x 30,
# debug install 120, APK identity 4 x 30, reports 30 each, reserve 15, plus the measured reset bound.
# W = the packages the slot's role installs: app; test when the role declares test_debug; publication
# on the publication slot only.
function Expected-Writers([Collections.IDictionary] $Manifest, $DevicePlan) {
    $kinds = @($Manifest.roles[$DevicePlan.role].artifacts.Keys)
    $writers = 1 + $(if ($kinds -ccontains 'test_debug') { 1 } else { 0 }) + $(if ($DevicePlan.lane -ceq 'publication') { 1 } else { 0 })
    if (@($DevicePlan.quiescence_packages).Count -ne $writers) { throw "Cleanup validator expects $writers writer packages" }
    return $writers
}
function Expected-CleanupSeconds($DevicePlan, [Collections.IDictionary] $Manifest) {
    $writers = Expected-Writers $Manifest $DevicePlan
    # Hang bound: 280 reset calls (16 + 24 x 11) x 0.276 s measured x 2, plus the unmeasured fixed parts
    # (3W writer probes, install, 4 identity probes, 30 s per report, 15 s reserve), in whole minutes.
    $milliseconds = (3 * $writers * 30 + 120 + 4 * 30 + 30 * @($DevicePlan.private_files).Count + 15) * 1000 + 280 * 276 * 2
    return [int]([Math]::Ceiling($milliseconds / 60000.0) * 60)
}
# Slots are chosen from the catalog by meaning, never by a fixed id or count.
function Catalog-Slot([string] $Lane, [string] $Group = '', [string] $Role = 'baseline') {
    $found = @(Get-P4SlotCatalog $phase | Where-Object { $_.lane -ceq $Lane -and $_.role -ceq $Role -and
        ($Group -ceq '' -or ($_.Contains('group_id') -and $_.group_id -ceq $Group)) -and
        ($Lane -cne 'frame' -or $_.id -clike '*-decision') })
    if ($found.Count -eq 0) { throw "Catalog has no $Lane/$Group/$Role slot" }
    return $found[0].id
}
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
    $script:expectedTimeout = Expected-CleanupSeconds (Get-P4LayerSlotCleanupPlan $manifest $slot $hash).device_plan $manifest
    return @{ Manifest = $manifest; Slot = $slot; Context = $context }
}
function Event([string] $Name, $Context) {
    if ($null -ne $Context) {
        if ($Context.operation_budget.timeout_seconds -ne $script:expectedTimeout) { throw 'Component got a different operation allowance' }
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
    Event 'archive' $null
    throw 'Per-slot cleanup must not archive private data or APKs'
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
        $catalog = @(Get-P4SlotCatalog $phase); $mapped = 0; $total = 0
        foreach ($slot in $catalog) {
            $plan = Get-P4LayerSlotCleanupPlan $manifest $slot $hash
            $expectedSeconds = Expected-CleanupSeconds $plan.device_plan $manifest
            Check ($plan.schema -ceq 'nene-pixel-p4-layer-slot-cleanup-plan-v2' -and -not $plan.Contains('archives') -and
                -not $plan.Contains('maximum_archive_bytes') -and -not $plan.Contains('maximum_apk_bytes') -and
                $plan.artifact_role -ceq $slot.artifact_role -and $plan.comparison_role -ceq $slot.role -and
                $plan.debug_package -ceq (Get-P4LanePackages $manifest $plan.artifact_role $phase).application -and
                $plan.timeout_seconds -eq $expectedSeconds -and $plan.timeout_seconds -le 3600) "cleanup map $($slot.id) = $expectedSeconds s"
            $mapped++; $total += $plan.timeout_seconds
        }
        Check ($catalog.Count -gt 0 -and $mapped -eq $catalog.Count) "cleanup map covers every catalog slot ($($catalog.Count))"
        Check ($catalog.Count -eq 18 -and $total -eq 11280) "18 slot cleanup clocks total 11280 s ($total s)"
        $single = Get-P4LayerSlotCleanupPlan $manifest (Get-P4ExecutionSlot $phase (Catalog-Slot 'frame' 'single')) $hash
        Check ($single.artifact_role -ceq 'baseline_single' -and
            (@($single.device_plan.quiescence_packages) -join '|') -ceq $manifest.roles.baseline_single.artifacts.app_debug.target_package -and
            $single.timeout_seconds -eq 540) 'baseline_single stops the app alone and its cleanup clock is 540 s'
        $publication = Get-P4LayerSlotCleanupPlan $manifest (Get-P4ExecutionSlot $phase (Catalog-Slot 'publication' '' 'candidate')) $hash
        $candidate = $manifest.roles.candidate.artifacts
        Check ((@($publication.device_plan.quiescence_packages) -join '|') -ceq (@($candidate.app_debug.target_package,
            $candidate.test_debug.test_package, $candidate.publication_test.test_package) -join '|') -and
            $publication.timeout_seconds -eq 780) 'publication stops app, test and publication packages; clock 780 s'
        $memory = Get-P4LayerSlotCleanupPlan $manifest (Get-P4ExecutionSlot $phase (Catalog-Slot 'memory' '' 'candidate')) $hash
        Check (@($memory.device_plan.quiescence_packages) -cnotcontains $candidate.publication_test.test_package -and
            @($memory.device_plan.quiescence_packages).Count -eq 2 -and $memory.timeout_seconds -eq 600) 'non-publication slot does not stop the publication package; clock 600 s'
        $slot = Get-P4ExecutionSlot $phase (Catalog-Slot 'memory' '' 'candidate')
        Refuses { Get-P4LayerSlotCleanupPlan $manifest $slot 'foreign' } '*ManifestSha256*' 'cleanup rejects unbound preflight'
        $bad = @{}; foreach ($key in $slot.Keys) { $bad[$key] = $slot[$key] }; $bad.role = 'baseline'
        Refuses { Get-P4LayerSlotCleanupPlan $manifest $bad $hash } '*Phase slot drift*' 'cleanup rejects a changed comparison role'
    }
    if ($CaseGroup -ceq 'Flow') {
        $flowIds = @((Catalog-Slot 'memory' '' 'candidate'), (Catalog-Slot 'publication' '' 'candidate'),
            (Catalog-Slot 'saf-save' '' 'candidate'), (Catalog-Slot 'frame' 'single'), (Catalog-Slot 'frame' 'layers16'),
            (Catalog-Slot 'frame' 'underlay'))
        foreach ($id in @($flowIds | Select-Object -Skip $FlowStartIndex)) {
            $fixture = Fixture $id $id
            $result = Invoke-P4LayerSlotCleanup $fixture.Context $fixture.Manifest $fixture.Slot $hash $true
            $record = $result.Record
            Check ($record.status -ceq 'stopped-and-reset' -and $record.schema -ceq 'nene-pixel-p4-layer-slot-cleanup-v2' -and
                $record.packages_stopped -and $record.errors.Count -eq 0 -and -not $record.Contains('archives')) "successful cleanup $id"
            $observedEvents = $script:events.ToArray()
            $writers = @($record.plan.device_plan.quiescence_packages)
            $lastStop = [array]::LastIndexOf($observedEvents, "stop:$($writers[-1])")
            $preservationAt = [array]::IndexOf($observedEvents, 'preservation')
            $installAt = [array]::IndexOf($observedEvents, "install:$($record.artifact_role)/app_debug")
            $reportsAt = [array]::IndexOf($observedEvents, 'reports')
            Check (@($observedEvents | Where-Object { $_ -like 'stop:*' }).Count -eq $writers.Count -and $lastStop -ge 0 -and
                $preservationAt -gt $lastStop -and $installAt -gt $preservationAt -and
                ($reportsAt -lt 0 -or $reportsAt -gt $installAt) -and $observedEvents[-1] -ceq 'reset' -and
                [array]::IndexOf($observedEvents, 'reset') -eq ($observedEvents.Count - 1) -and $null -ne $script:clock) "stop, preservation, debug access and reports precede the single final reset $id"
            Check ($observedEvents -cnotcontains 'archive' -and -not (Test-Path (Join-Path $fixture.Context.output_directory 'phase-cleanup/archive-app'))) "no per-slot archive $id"
            Check ($record.plan.timeout_seconds -eq $script:expectedTimeout -and $record.reset.sha256 -ceq (Get-P4SnapshotHash $record.reset.path) -and
                $record.debug_access.sha256 -ceq (Get-P4SnapshotHash $record.debug_access.path) -and
                ($null -eq $record.report_capture.evidence -or $record.report_capture.evidence.sha256 -ceq (Get-P4SnapshotHash $record.report_capture.evidence.path))) "cleanup record binds clock, debug access, reports and reset $id"
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
        foreach ($mode in @('stop', 'absence', 'preservation', 'install', 'restart', 'reports', 'expired', 'reset')) {
            $fixture = Fixture $mode (Catalog-Slot 'memory' '' 'candidate') $mode
            Refuses { Invoke-P4LayerSlotCleanup $fixture.Context $fixture.Manifest $fixture.Slot $hash $true } '*Phase cleanup failed*' "$mode propagates cleanup failure"
            $record = Get-Content (Join-Path $fixture.Context.output_directory 'phase-cleanup/result.json') -Raw | ConvertFrom-Json
            Check ($record.status -ceq 'failure' -and $record.errors.Count -gt 0) "$mode retains failed cleanup record"
            if ($mode -in @('stop', 'absence')) {
                Check (-not $record.packages_stopped -and $script:events -cnotcontains 'preservation' -and
                    @($script:events | Where-Object { $_ -like 'stop:*' }).Count -eq @($record.plan.device_plan.quiescence_packages).Count -and
                    @($record.plan.device_plan.quiescence_packages).Count -eq 2) "$mode attempts every stop without private work"
            }
            if ($mode -cne 'reset') { Check ($script:events -cnotcontains 'reset') "$mode cannot reset incomplete capture" }
            Check ($script:events -cnotcontains 'archive' -and $null -eq $record.PSObject.Properties['archives']) "$mode takes no archive"
            Check (@($record.errors | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count -eq $record.errors.Count) "$mode errors are bound in the cleanup record"
            if ($mode -ceq 'reports') {
                Check ($null -eq $record.reset -and @($record.errors | Where-Object { $_ -like '*Synthetic report failure*' }).Count -eq 1 -and
                    (Test-Path (Join-Path $fixture.Context.output_directory 'retained-report-partial.txt'))) 'failed report recovery keeps partial output and does not reset'
            }
            if ($mode -ceq 'reset') {
                Check ($script:events -ccontains 'reset' -and $null -eq $record.reset -and
                    @($record.errors | Where-Object { $_ -like '*Synthetic reset failure*' }).Count -eq 1 -and
                    (Test-Path (Join-Path $fixture.Context.output_directory 'phase-cleanup/reset/result.json'))) 'failed reset keeps its partial result'
            }
            if ($mode -ceq 'expired') {
                Check ($script:events -ccontains 'reports' -and $script:events -cnotcontains 'reset') 'expired shared clock prevents reset native work'
            }
        }
        foreach ($key in @('output_directory', 'repository_root', 'adb_path', 'serial')) {
            $fixture = Fixture "context-$key" (Catalog-Slot 'memory' '' 'candidate')
            $fixture.Context[$key] = $(if ($key -ceq 'serial') { 'foreign' } else { Join-Path $OutputDirectory 'foreign' })
            Refuses { Invoke-P4LayerSlotCleanup $fixture.Context $fixture.Manifest $fixture.Slot $hash $true } '*Phase cleanup*' "foreign context $key refused"
            Check ($script:events.Count -eq 0) "foreign context $key starts no native work"
        }
    }
    if ($CaseGroup -ceq 'Frame') {
        $fixture = Fixture 'existing-record' (Catalog-Slot 'frame' 'single')
        $first = Complete-P4LayerPartialFrameRecord $fixture.Manifest $fixture.Slot $hash $fixture.Context
        $second = Complete-P4LayerPartialFrameRecord $fixture.Manifest $fixture.Slot $hash $fixture.Context
        Check ($first.sha256 -ceq $second.sha256) 'partial frame inventory reuses an existing record without overwrite'
        $raw = Get-Content $first.path -Raw | ConvertFrom-Json
        Check ($raw.schema -ceq 'nene-pixel-p4-frame-slot-v2' -and $raw.files.Count -eq 1 -and
            $raw.files[0].relative_path -ceq 'partial.txt') 'outer-kill recovery inventories canonical partial bytes'
        foreach ($field in @('schema', 'slot_id', 'artifact_role', 'slot_directory', 'phase_context')) {
            $fixture = Fixture "foreign-$field" (Catalog-Slot 'frame' 'single')
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
        $fixture = Fixture 'failed-frame' (Catalog-Slot 'frame' 'layers16')
        $result = Invoke-P4LayerSlotCleanup $fixture.Context $fixture.Manifest $fixture.Slot $hash $false
        Check ($result.Record.report_capture.mode -ceq 'diagnostic-fixture-recovery' -and $script:events -ccontains 'reports' -and
            (Test-Path (Join-Path $fixture.Context.output_directory 'phase-cleanup/frame-fixture-recovery/private-report-capture.json'))) 'failed frame recovers fixture into a fresh diagnostic directory'
        $fixture = Fixture 'successful-frame-missing' (Catalog-Slot 'frame' 'single') -NoFrame
        Refuses { Invoke-P4LayerSlotCleanup $fixture.Context $fixture.Manifest $fixture.Slot $hash $true } '*no canonical capture directory*' 'successful frame cannot omit capture directory'
        $record = Get-Content (Join-Path $fixture.Context.output_directory 'phase-cleanup/result.json') -Raw | ConvertFrom-Json
        Check ($script:events -cnotcontains 'reset' -and $script:events -cnotcontains 'archive' -and $record.status -ceq 'failure' -and
            @($record.errors | Where-Object { $_ -like '*no canonical capture directory*' }).Count -eq 1) 'missing frame records its error without resetting'
        $fixture = Fixture 'early-failed-single' (Catalog-Slot 'frame' 'single') -NoFrame
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
            $fixture = Fixture "staging-$field" (Catalog-Slot 'frame' 'layers16')
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

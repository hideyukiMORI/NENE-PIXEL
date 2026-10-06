param([Parameter(Mandatory)][string] $OutputDirectory,
    [ValidateSet('Math', 'Dispatch', 'Inventory', 'Records', 'Compatibility', 'Legacy')][string] $CaseGroup = 'Math')
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/p4-device-private-restore.ps1')
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Budget validator output already exists' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$script:budgetChecks = [Collections.Generic.List[string]]::new()
$script:budgetRefusals = [Collections.Generic.List[string]]::new()
$script:budgetCalls = [Collections.Generic.List[object]]::new()
$script:budgetMode = 'success'
$script:budgetContext = $null
$script:expiredTimer = [Diagnostics.Stopwatch]::StartNew()
$runTimer = [Diagnostics.Stopwatch]::StartNew()
$status = 'failure'; $failure = $null
function Check([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw "Budget validator: $Message" }
    $script:budgetChecks.Add($Message)
}
function Reject([scriptblock] $Action, [string] $Pattern, [string] $Message) {
    $caught = $null
    try { & $Action | Out-Null } catch { $caught = $_.Exception.Message }
    if ($null -eq $caught -or $caught -notlike $Pattern) { throw "Expected $Pattern for $Message; observed $caught" }
    $script:budgetRefusals.Add("$Message : $caught")
    $script:budgetChecks.Add($Message)
}
function New-Context([string] $Name, [int] $Seconds = 0) {
    $directory = Join-Path $OutputDirectory $Name
    [void][IO.Directory]::CreateDirectory($directory)
    $context = @{ repository_root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path;
        output_directory = $directory; adb_path = (Get-Command pwsh).Source;
        serial = 'budget-serial'; package = 'io.github.hideyukimori.nenepixel' }
    if ($Seconds -gt 0) { $context.operation_budget = New-P4OperationBudget $Seconds }
    return $context
}
function Set-Expired([Collections.IDictionary] $Context) {
    if ($script:expiredTimer.Elapsed.TotalSeconds -lt 1.05) {
        Start-Sleep -Milliseconds ([int][Math]::Ceiling((1.05 - $script:expiredTimer.Elapsed.TotalSeconds) * 1000))
    }
    $Context.operation_budget = [ordered]@{ schema = 'nene-pixel-p4-operation-budget-v1';
        timeout_seconds = 1; timer = $script:expiredTimer }
}
function Save-Bytes([string] $Path, [byte[]] $Bytes) { [IO.File]::WriteAllBytes($Path, $Bytes) }
function Mock-Raw {
    param([string] $AdbPath, [string[]] $AdbArguments, [int] $TimeoutSeconds,
        [string] $DestinationPath, [string] $RecordPath, [long] $MaximumBytes)
    $script:budgetCalls.Add([pscustomobject]@{ kind = 'raw'; timeout = $TimeoutSeconds;
        arguments = $AdbArguments; maximum = $MaximumBytes; destination = $DestinationPath })
    $bytes = [Text.Encoding]::UTF8.GetBytes($(if ($AdbArguments -contains 'install') { "Success`n" } else { 'native' }))
    Save-Bytes $DestinationPath $bytes
    $failed = $script:budgetMode -ceq 'failure'
    $record = [ordered]@{ status = $(if ($failed) { 'failure' } else { 'success' }); exit_code = 0;
        error = $(if ($failed) { 'synthetic timeout' } else { $null }); timed_out = $failed;
        byte_limit_exceeded = $false; stderr = ''; destination_path = [IO.Path]::GetFullPath($DestinationPath);
        byte_count = $bytes.Length; sha256 = Get-P4SnapshotHash $DestinationPath; timeout_seconds = $TimeoutSeconds }
    Write-P4SnapshotJson $RecordPath $record
    if ($failed) { throw 'synthetic timeout' }
    if ($script:budgetMode -ceq 'expire') { Set-Expired $script:budgetContext }
    return [pscustomobject]$record
}
try {
    if ($CaseGroup -ceq 'Math') {
        foreach ($case in @(@(120, 0, 30, 30), @(120, 75.1, 120, 29), @(120, 104, 120, 1),
                @(3600, 0, 3600, 3585), @(31, 0, 30, 16))) {
            Check ((ConvertTo-P4OperationTimeout $case[0] $case[1] $case[2]) -eq $case[3]) "remaining $($case -join ',')"
        }
        foreach ($elapsed in @(104.001, 105, 120, 200)) {
            Reject { ConvertTo-P4OperationTimeout 120 $elapsed 30 } '*no native time*' "reserve/expiry $elapsed"
        }
        foreach ($elapsed in @(-1, [double]::NaN, [double]::PositiveInfinity, [double]::NegativeInfinity)) {
            Reject { ConvertTo-P4OperationTimeout 120 $elapsed 30 } '*Invalid*elapsed*' "invalid elapsed $elapsed"
        }
        $legacy = @{}
        Check ((Get-P4OperationTimeout $legacy 120) -eq 120) 'omitted budget keeps 120 seconds'
        foreach ($bad in @($null, @{}, 'budget', @{ schema = 'wrong'; timeout_seconds = 60; timer = [Diagnostics.Stopwatch]::StartNew() },
                @{ schema = 'nene-pixel-p4-operation-budget-v1'; timeout_seconds = '60'; timer = [Diagnostics.Stopwatch]::StartNew() },
                @{ schema = 'nene-pixel-p4-operation-budget-v1'; timeout_seconds = 60; timer = [Diagnostics.Stopwatch]::new() })) {
            Reject { Get-P4OperationTimeout @{ operation_budget = $bad } 30 } '*Malformed*' "malformed budget $($script:budgetRefusals.Count)"
        }
        foreach ($seconds in @(0, 3601, -1)) {
            $budget = New-P4OperationBudget 60; $budget.timeout_seconds = $seconds
            Reject { Get-P4OperationTimeout @{ operation_budget = $budget } 30 } '*Malformed*' "invalid allowance $seconds"
        }
        $context = New-Context 'active' 300
        Check ((Get-P4OperationTimeout $context 120) -eq 120) 'running clock preserves available ceiling'
        Assert-P4OperationInventoryLimit $context 4096
        Check $true '4096 entries accepted'
        Reject { Assert-P4OperationInventoryLimit $context 4097 } '*4096-entry*' '4097 entries refused'
        Assert-P4OperationInventoryLimit $legacy 4097
        Check $true 'legacy entry count unchanged'
        Set-Expired $context
        Reject { Assert-P4OperationActive $context } '*time expired*' 'host verification refuses expiry'
        Reject { Get-P4OperationTimeout $context 30 } '*no native time*' 'native dispatch refuses expiry'
    }
    if ($CaseGroup -ceq 'Dispatch') { & {
        function Invoke-P4RawAdbCapture { Mock-Raw @args }
        function Invoke-P4EncodedShellCapture {
            param($AdbPath, $Serial, $Script, $TimeoutSeconds, $DestinationPath, $RecordPath, $MaximumBytes)
            return Mock-Raw -AdbPath $AdbPath -AdbArguments @('-s', $Serial, 'shell', $Script) `
                -TimeoutSeconds $TimeoutSeconds -DestinationPath $DestinationPath -RecordPath $RecordPath -MaximumBytes $MaximumBytes
        }
        function Invoke-BoundedNativeCommand {
            param($RepositoryRoot, $LogPath, $ExecutablePath, $NativeArguments, $TimeoutSeconds)
            $script:budgetCalls.Add([pscustomobject]@{ kind = 'bounded'; timeout = $TimeoutSeconds;
                arguments = $NativeArguments; log = $LogPath; repository = $RepositoryRoot; executable = $ExecutablePath })
            if ($script:budgetMode -ceq 'expire') { Set-Expired $script:budgetContext }
            return [pscustomobject]@{ ExitCode = 0; OutputLines = @('native') }
        }
        $actions = [ordered]@{
            bounded = { param($c) Invoke-P4BoundedAdb $c 'native.log' @('shell', 'echo', 'native') 120 }
            capture = { param($c) Invoke-P4NativeCapture $c 'dispatch' 'raw' @('features') 1024 }
            runas = { param($c) Invoke-P4NativeRunAs $c 'dispatch' 'runas' 'printf native' 1024 }
            snapshot = { param($c) Invoke-P4SnapshotEncoded $c 'dispatch' 'snapshot' 'printf native' 1024 }
        }
        foreach ($name in $actions.Keys) {
            $context = New-Context "$name-legacy"
            $value = & $actions[$name] $context
            $ceiling = if ($name -ceq 'bounded') { 120 } else { 30 }
            Check ($script:budgetCalls[-1].timeout -eq $ceiling) "$name default native ceiling"
            $context = New-Context "$name-clamped" 40
            $value = & $actions[$name] $context
            Check ($script:budgetCalls[-1].timeout -ge 23 -and $script:budgetCalls[-1].timeout -le 25) "$name clamps remaining time"
            $before = $script:budgetCalls.Count
            $context = New-Context "$name-expired"; Set-Expired $context
            Reject { & $actions[$name] $context } '*no native time*' "$name expiry refused"
            Check ($script:budgetCalls.Count -eq $before) "$name launches no command after expiry"
            $context = New-Context "$name-null"; $context.operation_budget = $null
            Reject { & $actions[$name] $context } '*Malformed*' "$name null refused"
            Check ($script:budgetCalls.Count -eq $before) "$name launches no command after malformed budget"
            $context = New-Context "$name-overrun" 60
            $script:budgetContext = $context; $script:budgetMode = 'expire'
            Reject { & $actions[$name] $context } '*time expired*' "$name cannot report success after overrun"
            $script:budgetMode = 'success'
        }
        $context = New-Context 'bounded-arguments'
        $value = Invoke-P4BoundedAdb $context 'arguments.log' @('shell', 'a b', 'value') 30
        $call = $script:budgetCalls[-1]
        Check (($call.arguments -join '|') -ceq '-s|budget-serial|shell|a b|value' -and
            $call.repository -ceq $context.repository_root -and $call.executable -ceq $context.adb_path -and
            $call.log -ceq (Join-Path $context.output_directory 'arguments.log') -and $value.ExitCode -eq 0) 'bounded native paths/arguments/result unchanged'
    } }
    if ($CaseGroup -ceq 'Inventory') { & {
        $script:inventoryCount = 3; $script:inventoryAfterExtra = $false
        function Invoke-P4NativeCapture {
            param($Context, $Stage, $Step, $AdbArguments, $MaximumBytes, $ExpectedExit, $ExpectedStdout, $ExpectedStderr)
            [void](Get-P4OperationTimeout $Context 30)
            $script:budgetCalls.Add($Step)
            return ,([Text.Encoding]::UTF8.GetBytes($(if ($Step -ceq 'features') { "shell_v2`n" } else { $ExpectedStdout })))
        }
        function Invoke-P4NativeRunAs {
            param($Context, $Stage, $Step, $Script, $MaximumBytes)
            [void](Get-P4OperationTimeout $Context 30)
            $script:budgetCalls.Add($Step)
            $count = $script:inventoryCount
            if ($Step -ceq 'paths-after' -and $script:inventoryAfterExtra) { $count = 4097 }
            [byte[]]$bytes = switch -Regex ($Step) {
                '^encoded-byte-proof$' { [byte[]]@(0..255); break }
                '^paths-' {
                    $names = @('files') + @(1..($count - 1) | ForEach-Object { "files/item$_" })
                    [Text.Encoding]::UTF8.GetBytes(($names -join [char]0) + [char]0); break
                }
                '^stat-0$' {
                    [Text.Encoding]::UTF8.GetBytes("files|41ed|2|0|1720000000|2024-07-03 09:46:40.123456789 +0000`n" +
                        "files/item1|81a4|1|1|1720000000|2024-07-03 09:46:40.123456789 +0000`n" +
                        "files/item2|81a4|1|1|1720000000|2024-07-03 09:46:40.123456789 +0000`n"); break
                }
                '^hash-0$' { [Text.Encoding]::UTF8.GetBytes((('a' * 64) + "  files/item1`n" + ('b' * 64) + "  files/item2`n")); break }
                default { throw "Unexpected inventory mock step $Step" }
            }
            Save-Bytes (Join-Path $Context.output_directory "$Stage-$Step.bin") $bytes
            if ($script:budgetMode -ceq 'expire' -and $Step -ceq 'paths-before') { Set-Expired $Context }
            return ,$bytes
        }
        foreach ($phase in @($false, $true)) {
            $context = New-Context "normal-$phase" $(if ($phase) { 60 } else { 0 })
            $inventory = Get-P4NativePrivateInventory $context 'normal'
            Check ($inventory.Count -eq 3 -and $inventory[1].Hash -ceq ('a' * 64)) "actual inventory unchanged with phase=$phase"
        }
        $script:budgetCalls.Clear(); $script:inventoryCount = 4097
        $context = New-Context 'oversize' 60
        Reject { Get-P4NativePrivateInventory $context 'oversize' } '*4096-entry*' 'actual discovered inventory cap'
        Check (@($script:budgetCalls | Where-Object { $_ -like 'stat-*' -or $_ -like 'hash-*' }).Count -eq 0 -and
            (Test-Path -LiteralPath (Join-Path $context.output_directory 'oversize-paths-before.bin'))) 'cap preserves discovery and prevents stat/hash work'
        $script:inventoryCount = 3; $script:inventoryAfterExtra = $true
        $context = New-Context 'after-cap' 60
        Reject { Get-P4NativePrivateInventory $context 'after' } '*4096-entry*' 'after-scan cap refuses drift'
        $script:inventoryAfterExtra = $false; $script:budgetMode = 'expire'; $script:budgetCalls.Clear()
        $context = New-Context 'discovery-expiry' 60
        Reject { Get-P4NativePrivateInventory $context 'expire' } '*time expired*' 'expiry after discovery'
        Check (@($script:budgetCalls | Where-Object { $_ -like 'stat-*' -or $_ -like 'hash-*' }).Count -eq 0) 'expired discovery starts no metadata batch'
    } }
    if ($CaseGroup -ceq 'Records') { & {
        function Invoke-P4RawAdbCapture { Mock-Raw @args }
        $snapshot = @{ apk_path = 'retained-original.apk'; apk_sha256 = 'a' * 64 }
        foreach ($seconds in @(0, 40)) {
            $context = New-Context "install-$seconds" $seconds
            Invoke-P4RestorationApkInstall $context 'restore' $snapshot
            $intent = Get-Content (Join-Path $context.output_directory 'restore-install-intent.json') -Raw | ConvertFrom-Json
            $raw = Get-Content (Join-Path $context.output_directory 'restore-install.json') -Raw | ConvertFrom-Json
            Check ($intent.timeout_seconds -eq $script:budgetCalls[-1].timeout -and $raw.timeout_seconds -eq $intent.timeout_seconds -and
                (($seconds -eq 0 -and $intent.timeout_seconds -eq 120) -or ($seconds -gt 0 -and $intent.timeout_seconds -in 23..25)) -and
                ($intent.arguments -join '|') -ceq '-s|budget-serial|install|-r|-d|-t|retained-original.apk') "install $seconds intent pins actual timeout/arguments"
        }
        $context = New-Context 'install-expired'; Set-Expired $context; $before = $script:budgetCalls.Count
        Reject { Invoke-P4RestorationApkInstall $context 'expired' $snapshot } '*no native time*' 'expired restore install refused'
        Check ($script:budgetCalls.Count -eq $before -and @(Get-ChildItem $context.output_directory).Count -eq 0) 'expired restore has no install intent or native call'
        $context = New-Context 'install-failure' 40; $script:budgetMode = 'failure'
        Reject { Invoke-P4RestorationApkInstall $context 'failed' $snapshot } '*synthetic timeout*' 'restore install timeout propagates'
        Check ((Test-Path (Join-Path $context.output_directory 'failed-install-intent.json')) -and
            (Test-Path (Join-Path $context.output_directory 'failed-install.bin')) -and
            (Test-Path (Join-Path $context.output_directory 'failed-install.json'))) 'failed install retains intent/raw/partial bytes'
        $script:budgetMode = 'success'
        function Invoke-P4EncodedShellCapture {
            param($AdbPath, $Serial, $Script, $TimeoutSeconds, $DestinationPath, $RecordPath, $MaximumBytes)
            return Mock-Raw -AdbPath $AdbPath -AdbArguments @('-s', $Serial, 'shell', $Script) -TimeoutSeconds $TimeoutSeconds `
                -DestinationPath $DestinationPath -RecordPath $RecordPath -MaximumBytes $MaximumBytes
        }
        function Invoke-BoundedNativeCommand {
            param($RepositoryRoot, $LogPath, $ExecutablePath, $NativeArguments, $TimeoutSeconds)
            $script:budgetCalls.Add([pscustomobject]@{ kind = 'stopped'; timeout = $TimeoutSeconds })
            return [pscustomobject]@{ ExitCode = 1; OutputLines = @() }
        }
        $plan = @{ protocol_id = 'nene-pixel-p4-layer-phase-verification-v1'; slot_id = 'budget-slot'; report_capture_after_stop = $true;
            quiescence_packages = @('io.example.app', 'io.example.test', 'io.example.pub');
            private_files = @(@{ package = 'io.example.app'; relative_path = 'files/p4-layer/publication.csv';
                destination_name = 'publication.csv'; timeout_seconds = 30; maximum_bytes = 1024 },
                @{ package = 'io.example.app'; relative_path = 'files/p4-layer/publication.status';
                destination_name = 'publication.status'; timeout_seconds = 30; maximum_bytes = 1024 }) }
        $context = New-Context 'report-success' 40
        $report = Copy-P4LayerPrivateReports $context $plan
        Check ($report.status -ceq 'success' -and $report.reports.Count -eq 2 -and
            $script:budgetCalls[-1].timeout -in 23..25) 'stopped phase reports use remaining timeout'
        $context = New-Context 'report-expire' 40; $script:budgetContext = $context; $script:budgetMode = 'expire'
        $before = $script:budgetCalls.Count
        Reject { Copy-P4LayerPrivateReports $context $plan } '*time expired*' 'phase report expiry propagates'
        $report = Get-Content (Join-Path $context.output_directory 'private-report-capture.json') -Raw | ConvertFrom-Json
        Check ($script:budgetCalls.Count -eq $before + 4 -and $report.status -ceq 'failure' -and
            $report.reports.Count -eq 2 -and $report.reports[1].error -like '*no native time*' -and
            (Test-Path (Join-Path $context.output_directory 'publication.csv'))) 'expired report retains partial file and starts no second transfer'
        $script:budgetMode = 'success'
        function Get-P4NativePrivateInventory { param($Context, $Stage) return ,([object[]]@()) }
        function Invoke-P4NativeRunAs { param($Context, $Stage, $Step, $Script, $MaximumBytes)
            Set-Expired $Context; return ,([byte[]]@()) }
        $context = New-Context 'step-expire' 40
        $steps = [Collections.Generic.List[object]]::new()
        Reject { Invoke-P4IsolationStep $context 'step' 'move' 'move' 'mock-command' @() @() $steps } '*time expired*' 'step expiry prevents successful mutation record'
        Check ($steps.Count -eq 1 -and $steps[0].status -ceq 'failure' -and
            (Test-Path $steps[0].intent_path) -and (Test-Path $steps[0].result_path)) 'step expiry retains intent and failed result'
    } }
    if ($CaseGroup -ceq 'Compatibility') { & {
        $context = New-Context 'snapshot'
        $sources = Get-P4SnapshotSources
        Check ($sources.Count -eq 8 -and $sources['p4-operation-budget.ps1'] -ceq
            (Get-P4SnapshotHash (Join-Path $PSScriptRoot 'measurements/p4-operation-budget.ps1'))) 'snapshot binds the eighth budget source'
        $stage = 'compat'
        $snapshot = [ordered]@{ schema = 'nene-pixel-device-private-snapshot-v1'; status = 'verified-snapshot';
            serial = $context.serial; package = $context.package; stage = $stage;
            installed_apk_path = '/data/app/fixture/base.apk'; source_sha256 = $sources;
            apk_transfer_record_sha256 = $null; archive_transfer_record_sha256 = $null }
        foreach ($item in @(@('inventory', 'inventory-before.json'), @('inventory_after', 'inventory-after.json'),
                @('archive', 'original.tar'), @('apk', 'original.apk'))) {
            $path = Join-Path $context.output_directory "$stage-$($item[1])"
            $bytes = if ($item[0] -in @('inventory', 'inventory_after')) { [Text.Encoding]::UTF8.GetBytes('[]') }
                elseif ($item[0] -ceq 'archive') { [byte[]]::new(1024) } else { [byte[]]@(1, 2, 3) }
            Save-Bytes $path $bytes
            $snapshot["$($item[0])_path"] = $path; $snapshot["$($item[0])_sha256"] = Get-P4SnapshotHash $path
        }
        $path = Join-Path $context.output_directory "$stage-original-apk.json"
        Write-P4SnapshotJson $path @{ status = 'fixture' }
        $snapshot.apk_transfer_record_sha256 = Get-P4SnapshotHash $path
        $recordPath = Join-Path $context.output_directory "$stage-snapshot.json"
        Write-P4SnapshotJson $recordPath $snapshot
        $verified = Read-P4VerifiedSnapshot $recordPath $context
        Check ($verified.Sources.Count -eq 8) 'current source inventory accepted by actual snapshot reader'
        # These are separate synthetic records in distinct directories, never edits of retained evidence.
        $context2 = New-Context 'old-source-snapshot'
        foreach ($path in [IO.Directory]::EnumerateFiles($context.output_directory)) {
            [IO.File]::Copy($path, (Join-Path $context2.output_directory ([IO.Path]::GetFileName($path))))
        }
        $recordPath2 = Join-Path $context2.output_directory "$stage-snapshot.json"
        $old = Get-Content $recordPath2 -Raw | ConvertFrom-Json -AsHashtable
        foreach ($key in @('inventory_path', 'inventory_after_path', 'archive_path', 'apk_path')) {
            $old[$key] = Join-Path $context2.output_directory ([IO.Path]::GetFileName($old[$key]))
        }
        $old.source_sha256.Remove('p4-operation-budget.ps1')
        [IO.File]::WriteAllText($recordPath2, (ConvertTo-Json -InputObject $old -Depth 10))
        Reject { Read-P4VerifiedSnapshot $recordPath2 $context2 } '*source set changed*' 'historical seven-source snapshot refused'
        $repository = $context.repository_root
        # Accepted planner/transport inputs pinned by git blob hash (git hash-object), not by a commit, so a squash
        # merge keeps the baseline. Changing the baseline requires a commit that changes this table.
        # preservation differs from 23a1e53 only by New-P4SlotResetPlan (#145, 52606fa); the others equal 23a1e53.
        $acceptedBlobs = [ordered]@{
            'p4-device-private-preservation.ps1' = '26280d570ac9b3691bd3980342ddb92e66d099f4'
            'p4-device-private-observation.ps1' = '0798480071e6b6ba715e58cdd13848dd5799d2b4'
            'p4-device-private-transport.ps1' = 'ed4572eb8945fba06e20e312016df547a8bd05ac'
        }
        foreach ($name in $acceptedBlobs.Keys) {
            $blob = & git -C $repository hash-object -- "docs/quality/measurements/$name"
            Check ($LASTEXITCODE -eq 0 -and $blob -ceq $acceptedBlobs[$name]) "$name matches accepted planner/transport inputs"
        }
        $files = @('p4-operation-budget.ps1', 'p4-indexed-device-lanes.ps1', 'p4-device-private-native.ps1',
            'p4-device-private-snapshot.ps1', 'p4-device-private-session.ps1', 'p4-device-private-restore.ps1')
        foreach ($name in $files) {
            $tokens = $null; $errors = $null
            [void][Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot "measurements/$name"), [ref]$tokens, [ref]$errors)
            Check ($errors.Count -eq 0) "$name parses"
        }
    } }
    if ($CaseGroup -ceq 'Legacy') { & {
        $names = @('Invoke-P4BoundedAdb', 'Invoke-P4NativeCapture', 'Invoke-P4NativeRunAs',
            'Invoke-P4SnapshotEncoded', 'Invoke-P4RestorationApkInstall', 'Copy-P4PrivateFile')
        $definitions = [Collections.Generic.List[string]]::new()
        foreach ($file in @('p4-indexed-device-lanes.ps1', 'p4-device-private-native.ps1',
                'p4-device-private-snapshot.ps1', 'p4-device-private-restore.ps1')) {
            $source = (& git -C (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path show "23a1e53:docs/quality/measurements/$file") -join "`n"
            if ($LASTEXITCODE -ne 0) { throw 'Could not read historical source' }
            $tokens = $null; $errors = $null
            $ast = [Management.Automation.Language.Parser]::ParseInput($source, [ref]$tokens, [ref]$errors)
            if ($errors.Count -ne 0) { throw 'Historical source parse failed' }
            foreach ($node in $ast.FindAll({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] }, $false)) {
                if ($node.Name -cin $names) { $definitions.Add($node.Extent.Text) }
            }
        }
        if ($definitions.Count -ne $names.Count) { throw 'Historical function coverage differs' }
        $runs = @{}
        foreach ($version in @('current', 'historical')) {
            $runs[$version] = & {
                if ($version -ceq 'historical') { foreach ($definition in $definitions) { Invoke-Expression $definition } }
                function Invoke-P4RawAdbCapture {
                    param($AdbPath, $AdbArguments, $TimeoutSeconds, $DestinationPath, $RecordPath, $MaximumBytes)
                    if ([string]::IsNullOrEmpty($DestinationPath)) {
                        $script:budgetCalls.Add([pscustomobject]@{ kind = 'legacy-bytes'; arguments = $AdbArguments; timeout = $TimeoutSeconds })
                        return ,([byte[]]@(3, 4, 5))
                    }
                    return Mock-Raw @PSBoundParameters
                }
                function Invoke-P4EncodedShellCapture {
                    param($AdbPath, $Serial, $Script, $TimeoutSeconds, $DestinationPath, $RecordPath, $MaximumBytes)
                    return Mock-Raw -AdbPath $AdbPath -AdbArguments @('-s', $Serial, 'shell', $Script) -TimeoutSeconds $TimeoutSeconds `
                        -DestinationPath $DestinationPath -RecordPath $RecordPath -MaximumBytes $MaximumBytes
                }
                function Invoke-BoundedNativeCommand {
                    param($RepositoryRoot, $LogPath, $ExecutablePath, $NativeArguments, $TimeoutSeconds)
                    $script:budgetCalls.Add([pscustomobject]@{ kind = 'bounded'; arguments = $NativeArguments; timeout = $TimeoutSeconds;
                        repository = $RepositoryRoot; executable = $ExecutablePath; log = $LogPath })
                    return [pscustomobject]@{ ExitCode = 0; OutputLines = @('native') }
                }
                $observed = [ordered]@{}
                foreach ($name in $names) {
                    $context = New-Context "$version-$name"
                    $start = $script:budgetCalls.Count
                    $value = switch ($name) {
                        'Invoke-P4BoundedAdb' { Invoke-P4BoundedAdb $context 'native.log' @('shell', 'a b') 120 }
                        'Invoke-P4NativeCapture' { [Convert]::ToBase64String((Invoke-P4NativeCapture $context 'legacy' 'raw' @('features') 1024)) }
                        'Invoke-P4NativeRunAs' { [Convert]::ToBase64String((Invoke-P4NativeRunAs $context 'legacy' 'runas' 'printf native' 1024)) }
                        'Invoke-P4SnapshotEncoded' { Invoke-P4SnapshotEncoded $context 'legacy' 'snapshot' 'printf native' 1024 }
                        'Invoke-P4RestorationApkInstall' { Invoke-P4RestorationApkInstall $context 'restore' @{ apk_path = 'original.apk'; apk_sha256 = 'a' * 64 } }
                        'Copy-P4PrivateFile' { Copy-P4PrivateFile $context $context.package 'files/original' (Join-Path $context.output_directory 'copy.bin') }
                    }
                    $calls = @($script:budgetCalls.ToArray())[$start..($script:budgetCalls.Count - 1)]
                    $projection = ConvertTo-Json -InputObject @{ result = $value; calls = $calls } -Depth 12 -Compress
                    $escapedPath = (ConvertTo-Json $context.output_directory -Compress).Trim('"')
                    $observed[$name] = $projection.Replace($escapedPath, '<output>')
                }
                return $observed
            }
        }
        foreach ($name in $names) {
            Check ($runs.current[$name] -ceq $runs.historical[$name]) "$name omitted-budget native parameters and results match 23a1e53"
        }
    } }
    # Separate groups keep successful checks reusable if a later fixture needs correction.
    $status = 'pass'
} catch { $failure = $_; throw }
finally {
    $result = [ordered]@{ group = $CaseGroup; status = $status; checks = $script:budgetChecks.Count;
        accepted = $script:budgetChecks.ToArray(); refusals = $script:budgetRefusals.ToArray();
        calls = $script:budgetCalls.ToArray(); elapsed_seconds = $runTimer.Elapsed.TotalSeconds;
        source_sha256 = Get-P4SnapshotSources; error = $(if ($null -eq $failure) { $null } else { [string]$failure }) }
    Write-P4SnapshotJson (Join-Path $OutputDirectory 'result.json') $result
    [pscustomobject]$result | Select-Object group, status, checks, elapsed_seconds | ConvertTo-Json -Compress | Write-Output
}

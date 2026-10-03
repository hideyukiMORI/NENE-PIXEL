# #145 device-free lane routing and native-call orchestration. Never contacts ADB.
param([Parameter(Mandatory)][string]$OutputDirectory,
    [ValidateSet('Plans', 'FrameBudgets', 'NativeBoundaries', 'Compatibility', 'SourceBindings', 'LaneDispatch')][string]$CaseGroup = 'Plans')
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-device-lanes.ps1')
$repository = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$lab = [IO.Path]::GetFullPath((Get-NenePixelLabPath 'evidence' -StartDirectory $PSScriptRoot)).TrimEnd('\', '/')
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $OutputDirectory.StartsWith($lab + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    [IO.File]::Exists($OutputDirectory) -or [IO.Directory]::Exists($OutputDirectory)) { throw 'Fresh lab output required.' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$script:cases = [Collections.Generic.List[object]]::new()
$phase = 'nene-pixel-p4-layer-phase-verification-v1'
$hash = 'a' * 64
$watch = [Diagnostics.Stopwatch]::StartNew()
$result = [ordered]@{ status = 'failure'; group = $CaseGroup; cases = @(); elapsed_seconds = 0 }

function Check([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Clone($Value) { ConvertFrom-Json -AsHashtable -InputObject ($Value | ConvertTo-Json -Depth 25) }
function Case([string]$Name, [scriptblock]$Body, [switch]$Refuse) {
    $failure = $null
    try { & $Body } catch { $failure = $_.Exception.Message }
    $passed = if ($Refuse) { $null -ne $failure } else { $null -eq $failure }
    $script:cases.Add([ordered]@{ name = $Name; passed = $passed; refusal = [bool]$Refuse; error = $failure })
    Check $passed "Case failed: $Name : $failure"
}
function New-Manifest {
    $manifest = [ordered]@{ protocol = @{ id = $phase }; experiment_id = 'layer-lane-contract';
        roles = [ordered]@{}; device = @{ serial = 'contract-only'; asset_preservation = @{ sha256 = 'b' * 64; session = 'layer-lane-contract' } } }
    foreach ($role in @(Get-P4LayerArtifactCatalog $phase)) {
        $artifacts = [ordered]@{}
        foreach ($kind in $role.artifact_kinds) {
            $app = 'io.github.hideyukimori.nenepixel'
            $test = if ($kind -ceq 'test_debug') { "$app.test" }
                elseif ($kind -ceq 'publication_test') { "$app.adapters.persistence.test" } else { 'none' }
            $target = if ($kind -ceq 'publication_test') { $test } else { $app }
            $artifacts[$kind] = [ordered]@{ target_package = $target; test_package = $test; sha256 = 'd' * 64; path = 'unused.apk' }
        }
        $manifest.roles[$role.role] = [ordered]@{ build_commit = 'c' * 40; production_commit = $role.production_commit;
            artifacts = $artifacts; worktree = $repository }
    }
    return $manifest
}

try {
    $manifest = New-Manifest
    $slots = @(Get-P4SlotCatalog $phase)
    if ($CaseGroup -ceq 'Plans') {
        $plans = @(foreach ($slot in $slots) {
            $plan = Get-P4DeviceLanePlan $manifest $slot $hash
            Case "canonical plan $($slot.id)" {
                Check ($plan.role -ceq $slot.artifact_role -and $plan.comparison_role -ceq $slot.role) 'Role mapping differs'
                Check ($plan.phase_context.slot_id -ceq $slot.id -and $plan.phase_context.preflight_sha256 -ceq $hash) 'Context mapping differs'
                Check ($plan.report_capture_after_stop -and $null -eq $plan.private_file_quarantine) 'Phase must retain reports for stopped cleanup'
                $budget = $plan.collector_budget
                Check ($budget.collector_timeout_seconds -le 3600 -and $budget.collector_timeout_seconds -gt $slot.timeout_seconds) 'Collector bound differs'
                Check ($budget.private_capture_seconds -eq 0) 'Capture belongs to cleanup'
                $expected = switch ($slot.lane) {
                    'memory' { 1080 }
                    'publication' { 930 }
                    'saf-save' { 1320 }
                    'frame' { $slot.timeout_seconds + 420 + $(if ($slot.group_id -ceq 'single') { 390 } else { 1170 }) }
                }
                Check ($budget.collector_timeout_seconds -eq $expected) "Unexpected exact bound $($budget.collector_timeout_seconds) / $expected"
                foreach ($file in $plan.private_files) {
                    Check ($file.relative_path -clike "files/p4-layer-*$($hash.Substring(0,12))*" -and $file.timeout_seconds -eq 30 -and $file.maximum_bytes -eq 1048576) 'Private report contract differs'
                }
                if ($slot.lane -ceq 'frame' -and $slot.group_id -ceq 'single') {
                    Check ($plan.class -ceq '' -and $plan.adb_arguments.Count -eq 0 -and $plan.private_files.Count -eq 0) 'Single frame staged a fixture'
                } else {
                    Check ($plan.adb_arguments[-1] -ceq "$($plan.test_package)/androidx.test.runner.AndroidJUnitRunner") 'Wrong instrumentation package'
                    Check ($plan.instrumentation_arguments.p4LayerArtifactRole -ceq $slot.artifact_role) 'Wrong Android role argument'
                }
            }
            $plan
        })
        Write-NewInvocationFile (Join-Path $OutputDirectory 'plans.json') ($plans | ConvertTo-Json -Depth 20)
        foreach ($field in @('role', 'artifact_role', 'lane', 'timeout_seconds', 'id')) {
            $bad = Clone $slots[0]; $bad[$field] = 'foreign'
            Case "slot tamper $field" { Get-P4DeviceLanePlan $manifest $bad $hash | Out-Null } -Refuse
        }
        Case 'missing slot field' { $bad = Clone $slots[0]; $bad.Remove('role'); Get-P4DeviceLanePlan $manifest $bad $hash | Out-Null } -Refuse
        Case 'extra slot field' { $bad = Clone $slots[0]; $bad.extra = 1; Get-P4DeviceLanePlan $manifest $bad $hash | Out-Null } -Refuse
        Case 'missing manifest hash' { Get-P4DeviceLanePlan $manifest $slots[0] | Out-Null } -Refuse
        Case 'wrong manifest hash' { Get-P4DeviceLanePlan $manifest $slots[0] 'foreign' | Out-Null } -Refuse
        foreach ($field in @('session', 'sha256')) {
            $bad = Clone $manifest; $bad.device.asset_preservation[$field] = 'foreign/'
            Case "preservation $field" { Get-P4DeviceLanePlan $bad $slots[0] $hash | Out-Null } -Refuse
        }
        foreach ($field in @('production_commit', 'build_commit')) {
            $bad = Clone $manifest; $bad.roles.baseline_single[$field] = 'foreign'
            Case "source $field" { Get-P4DeviceLanePlan $bad $slots[0] $hash | Out-Null } -Refuse
        }
        Case 'baseline unused publication forbidden' {
            $bad = Clone $manifest; $bad.roles.baseline_single.artifacts.publication_test = $bad.roles.candidate.artifacts.publication_test
            Get-P4DeviceLanePlan $bad $slots[0] $hash | Out-Null
        } -Refuse
        Case 'missing candidate publication forbidden' {
            $bad = Clone $manifest; $bad.roles.candidate.artifacts.Remove('publication_test')
            Get-P4DeviceLanePlan $bad $slots[0] $hash | Out-Null
        } -Refuse
        Case 'publication target must be self' {
            $bad = Clone $manifest; $bad.roles.candidate.artifacts.publication_test.target_package = 'io.foreign.app'
            Get-P4DeviceLanePlan $bad $slots[22] $hash | Out-Null
        } -Refuse
        Case 'role app identity must agree' {
            $bad = Clone $manifest; $bad.roles.baseline_single.artifacts.test_debug.test_package = 'io.foreign.test'
            Get-P4DeviceLanePlan $bad $slots[0] $hash | Out-Null
        } -Refuse
        Case 'unknown package protocol refused' { Get-P4LanePackages $manifest 'candidate' 'foreign' | Out-Null } -Refuse
        Case 'bound above native cap refused' { Assert-P4CollectorBoundWithinCap @{ lane = 'frame'; collector_timeout_seconds = 3601 } } -Refuse
    }
    if ($CaseGroup -ceq 'FrameBudgets') {
        foreach ($slot in @($slots | Where-Object { $_.lane -ceq 'frame' })) {
            Case "frame setup budget $($slot.id)" {
                $plan = Get-P4DeviceLanePlan $manifest $slot $hash
                $budget = $plan.collector_budget
                $expected = $slot.timeout_seconds + 420 + $(if ($slot.group_id -ceq 'single') { 390 } else { 1170 })
                Check ($budget.ui_setup_seconds -eq 300 -and $budget.collector_timeout_seconds -eq $expected -and
                    $budget.collector_timeout_seconds -le 3600) 'Frame UI allowance differs'
            }
        }
    }
    if ($CaseGroup -ceq 'NativeBoundaries') {
        $script:mode = ''; $script:events = [Collections.Generic.List[string]]::new()
        $script:currentDirectory = ''; $script:phaseInstall = $true
        function Get-P4InstalledApkSha256($Context, $Package, $Stage) {
            $script:events.Add($Stage)
            if ($Stage.EndsWith('-before')) { return $(if ($script:mode -ceq 'existing') { 'd' * 64 } else { 'e' * 64 }) }
            if ($script:mode -ceq 'readback-failure') { throw 'simulated readback failure' }
            if ($script:mode -ceq 'mismatch') { return 'f' * 64 }
            return 'd' * 64
        }
        function Invoke-P4BoundedAdb($Context, $LogName, $AdbArguments, $TimeoutSeconds) {
            $script:events.Add('install')
            if ($script:phaseInstall) {
                $intent = Get-Content -Raw (Join-Path $Context.output_directory 'install-app_debug-intent.json') | ConvertFrom-Json
                Check ($intent.action -ceq 'install' -and $intent.before_sha256 -ceq ('e' * 64)) 'Install began before correct intent'
            }
            Check ($TimeoutSeconds -eq 120 -and ($AdbArguments[0..2] -join ',') -ceq 'install,-r,-t') 'Install boundary changed'
            if ($script:mode -ceq 'timeout') { throw 'simulated native timeout' }
            return @{ ExitCode = $(if ($script:mode -ceq 'exit-failure') { 6 } else { 0 }) }
        }
        foreach ($mode in @('existing', 'success', 'exit-failure', 'timeout', 'readback-failure', 'mismatch')) {
            Case "phase install $mode" {
                $script:mode = $mode; $script:events.Clear()
                $directory = Join-Path $OutputDirectory "install-$mode"
                [void][IO.Directory]::CreateDirectory($directory)
                $context = @{ output_directory = $directory }
                $failure = $null
                try { Assert-P4InstalledApk $context $manifest 'candidate' 'app_debug' | Out-Null } catch { $failure = $_ }
                $record = Get-Content -Raw (Join-Path $directory 'install-app_debug.json') | ConvertFrom-Json
                $success = $mode -cin @('existing', 'success')
                Check (($null -eq $failure) -eq $success) 'Install outcome differs'
                Check ($record.status -ceq $(if ($success) { 'success' } else { 'failure' })) 'Retained install status differs'
                Check ($record.schema -ceq 'nene-pixel-p4-device-install-v2') 'Phase record schema differs'
                $expectedEvents = if ($mode -ceq 'existing') { 'app_debug-before' } else { 'app_debug-before,install,app_debug-after' }
                Check (($script:events -join ',') -ceq $expectedEvents) 'Install/readback sequence differs'
                if ($mode -ceq 'readback-failure') { Check ($record.observed_sha256 -ceq 'unconfirmed') 'Failed readback claimed old identity' }
                if ($mode -cin @('exit-failure', 'timeout')) {
                    Check ($record.observed_sha256 -ceq ('d' * 64) -and $record.attempted) 'Failed install must retain actual replaced APK identity'
                }
            }
        }
        Case 'occupied install intent preserves bytes and prevents mutation' {
            $directory = Join-Path $OutputDirectory 'install-occupied'; [void][IO.Directory]::CreateDirectory($directory)
            $path = Join-Path $directory 'install-app_debug-intent.json'; Write-NewInvocationFile $path 'retain'
            $script:mode = 'success'; $script:events.Clear(); $failure = $null
            try { Assert-P4InstalledApk @{ output_directory = $directory } $manifest 'candidate' 'app_debug' | Out-Null } catch { $failure = $_ }
            Check ($null -ne $failure -and 'install' -cnotin $script:events -and [IO.File]::ReadAllText($path) -ceq 'retain') 'Occupied install evidence was replaced'
        }
        Case 'legacy successful install keeps v1 record and no intent' {
            $directory = Join-Path $OutputDirectory 'install-legacy'; [void][IO.Directory]::CreateDirectory($directory)
            $legacy = Clone $manifest; $legacy.protocol.id = 'nene-pixel-p4-indexed-cutover-verification-v7'
            $script:mode = 'success'; $script:events.Clear(); $script:phaseInstall = $false
            $record = Assert-P4InstalledApk @{ output_directory = $directory } $legacy 'candidate' 'app_debug'
            Check ($record.schema -ceq 'nene-pixel-p4-device-install-v1' -and $record.installed -and
                -not (Test-Path (Join-Path $directory 'install-app_debug-intent.json'))) 'Legacy install behavior differs'
        }
        function Assert-P4RemotePackagesStopped($Context, $Packages, $Stage) {
            $script:events.Add('stopped')
            Check ($Packages.Count -eq 3 -and $Stage -ceq 'phase-report-capture') 'Capture stop coverage differs'
            if ($script:mode -ceq 'running') { throw 'simulated process remains running' }
        }
        function Invoke-P4EncodedShellCapture($AdbPath, $Serial, $Script, $TimeoutSeconds, $DestinationPath, $RecordPath, $MaximumBytes) {
            $leaf = [IO.Path]::GetFileName($DestinationPath); $script:events.Add($leaf)
            Check ($script:events[0] -ceq 'stopped') 'Copy preceded stop proof'
            Check ($TimeoutSeconds -eq 30 -and $MaximumBytes -eq 1048576) 'Transfer bound differs'
            Check ([regex]::Matches($Script, 'pidof ').Count -eq 2 -and $Script.Contains('[ ! -L ') -and
                $Script.Contains('stat -c %h ') -and $Script.Contains('p4-report-missing') -and
                -not $Script.Contains(' mv ') -and -not $Script.Contains(' rm ')) 'Remote read guard differs'
            $bytes = [byte[]]@(0, 10, 13, 255)
            [IO.File]::WriteAllBytes($DestinationPath, $bytes)
            Write-NewInvocationFile $RecordPath 'retained transport boundary fixture'
            if ($script:mode -ceq 'partial' -and $leaf -ceq 'publication.csv') { throw 'simulated partial capture' }
            return @{ byte_count = 4; sha256 = Get-FileSha256 $DestinationPath }
        }
        $plan = Get-P4DeviceLanePlan $manifest $slots[22] $hash
        foreach ($mode in @('complete', 'partial', 'running')) {
            Case "stopped capture $mode" {
                $script:mode = $mode; $script:events.Clear()
                $directory = Join-Path $OutputDirectory "capture-$mode"; [void][IO.Directory]::CreateDirectory($directory)
                $context = @{ output_directory = $directory; adb_path = 'never-invoked'; serial = 'host-only' }
                $failure = $null
                try { Copy-P4LayerPrivateReports $context $plan | Out-Null } catch { $failure = $_ }
                $record = Get-Content -Raw (Join-Path $directory 'private-report-capture.json') | ConvertFrom-Json
                Check (($null -eq $failure) -eq ($mode -ceq 'complete')) 'Capture outcome differs'
                $expected = if ($mode -ceq 'running') { 'stopped' } else { 'stopped,publication.csv,publication.status,identity.txt' }
                Check (($script:events -join ',') -ceq $expected) 'Failed capture skipped remaining reports or read live process'
                Check ($record.expected_count -eq 3 -and -not $record.deleted) 'Capture inventory/deletion claim differs'
                if ($mode -ceq 'partial') {
                    Check ($record.reports[0].status -ceq 'failure' -and $record.reports[2].status -ceq 'captured' -and
                        [IO.File]::ReadAllBytes((Join-Path $directory 'publication.csv')).Length -eq 4) 'Partial report was discarded'
                }
            }
        }
        Case 'occupied report prevents all native calls and preserves bytes' {
            $directory = Join-Path $OutputDirectory 'capture-occupied'; [void][IO.Directory]::CreateDirectory($directory)
            Write-NewInvocationFile (Join-Path $directory 'publication.csv') 'retain'; $script:events.Clear()
            $failure = $null
            try { Copy-P4LayerPrivateReports @{ output_directory = $directory } $plan | Out-Null } catch { $failure = $_ }
            Check ($null -ne $failure -and $script:events.Count -eq 0 -and
                [IO.File]::ReadAllText((Join-Path $directory 'publication.csv')) -ceq 'retain') 'Report collision changed state'
        }
    }
    if ($CaseGroup -cin @('Compatibility', 'SourceBindings')) {
        if ($CaseGroup -ceq 'Compatibility') {
        # Compare exact legacy plan outputs against the pre-change implementation in an isolated module.
        $path = 'docs/quality/measurements/p4-indexed-device-lanes.ps1'
        $oldText = (git -C $repository show "749124f6f5eb806cf0fca52880e694ae60d24af2:$path") -join "`n"
        Check ($LASTEXITCODE -eq 0) 'Cannot read pinned legacy source'
        Write-NewInvocationFile (Join-Path $OutputDirectory 'legacy-device-lanes.ps1') $oldText
        $oldText = $oldText.Replace(". (Join-Path `$PSScriptRoot '../bounded-native-command.ps1')",
            ". '" + (Join-Path $repository 'docs/quality/bounded-native-command.ps1').Replace("'", "''") + "'")
        $legacyModule = New-Module -ScriptBlock ([scriptblock]::Create($oldText))
        $fixtureAst = [Management.Automation.Language.Parser]::ParseFile(
            (Join-Path $PSScriptRoot 'validate-p4-frame-analysis.ps1'), [ref]$null, [ref]$null)
        $factory = @($fixtureAst.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
            $node.Name -ceq 'New-P4LaneManifest' }, $true))
        Check ($factory.Count -eq 1) 'Legacy fixture factory missing'
        . ([scriptblock]::Create($factory[0].Extent.Text))
        $temporaryRoot = $OutputDirectory; $baselineCommit = '1' * 40; $candidateCommit = '2' * 40
        $baselineApk = '3' * 64; $candidateApk = '4' * 64
        $baselineProduction = '5' * 40; $candidateProduction = '6' * 40
        $applicationPackage = 'io.github.hideyukimori.nenepixel'; $applicationTestPackage = "$applicationPackage.test"
        $publicationTestPackage = "$applicationPackage.adapters.persistence.test"
        $fixtureExperimentId = 'legacy-plan-contract'; $canvas16Bounds = '[100,200][300,400]'; $canvas256Bounds = '[80,180][320,420]'
        $legacyManifest = New-P4LaneManifest
        $legacySlots = @(@(Get-P4SlotCatalog) + @(Get-P4FrameSlotCatalog) | Where-Object { $_.lane -cne 'host' })
        $beforePlans = @(& $legacyModule { param($m, $slots)
            foreach ($s in $slots) { Get-P4DeviceLanePlan $m $s }
        } $legacyManifest $legacySlots)
        # Dynamic-module exports may enter command resolution: explicitly restore the current source.
        . (Join-Path $PSScriptRoot 'measurements/p4-indexed-device-lanes.ps1')
        Check ((Get-Command Get-P4DeviceLanePlan).Parameters.ContainsKey('ManifestSha256')) 'Current plan function not restored'
        $legacyResults = @(for ($i = 0; $i -lt $legacySlots.Count; $i++) {
            $slot = $legacySlots[$i]; $before = $beforePlans[$i]
            $after = Get-P4DeviceLanePlan $legacyManifest $slot
            Case "legacy exact plan $($slot.id)" {
                Check (($before | ConvertTo-Json -Depth 20 -Compress) -ceq ($after | ConvertTo-Json -Depth 20 -Compress)) 'Legacy plan changed'
            }
            $after
        })
        Write-NewInvocationFile (Join-Path $OutputDirectory 'legacy-plans.json') ($legacyResults | ConvertTo-Json -Depth 20)
        }
        $measurement = Join-Path $repository 'app/android/src/androidTest/kotlin/io/github/hideyukimori/nenepixel/measurement'
        Case 'common Android argument names agree' {
            $source = Get-Content -Raw (Join-Path $measurement 'P4LayerRunAdmission.kt')
            $keys = @([regex]::Matches($source, '"(p4Layer[A-Za-z0-9]+)"') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
            $plan = Get-P4DeviceLanePlan $manifest $slots[12] $hash
            Check ($keys.Count -eq 12) 'Common Android argument count changed'
            foreach ($key in $keys) { Check ($plan.instrumentation_arguments.Contains($key)) "Missing actual Android key $key" }
        }
        foreach ($index in @(4, 12, 22, 23)) {
            $plan = Get-P4DeviceLanePlan $manifest $slots[$index] $hash
            Case "actual runner binding $($plan.lane)" {
                $className = $plan.class.Split('.')[-1]
                $path = if ($plan.lane -ceq 'publication') {
                    Join-Path $repository "adapters/persistence/src/androidTest/kotlin/io/github/hideyukimori/nenepixel/adapters/persistence/$className.kt"
                } else { Join-Path $measurement "$className.kt" }
                $source = Get-Content -Raw $path
                Check ($source.Contains("class $className") -and [regex]::Matches($source, '(?m)^\s*@Test\s*$').Count -eq 1) 'Selected class is not exactly one actual test'
                if ($plan.lane -ceq 'publication') {
                    $source += Get-Content -Raw (Join-Path (Split-Path -Parent $path) 'AutosaveLayerPublicationAdmission.kt')
                }
                Check ($source.Contains('"' + $plan.instrumentation_arguments.p4LayerCollect + '"')) 'Actual opt-in differs'
            }
        }
        Case 'numerical analyzers and encoded transport unchanged' {
            foreach ($path in @('p4-indexed-memory-analysis.ps1', 'p4-indexed-publication-analysis.ps1', 'p4-indexed-frame-analysis.ps1', 'p4-device-private-transport.ps1')) {
                $diff = @(git -C $repository diff 749124f6f5eb806cf0fca52880e694ae60d24af2 -- "docs/quality/measurements/$path")
                Check ($LASTEXITCODE -eq 0 -and $diff.Count -eq 0) "Unchanged evidence no longer reusable: $path"
            }
        }
    }
    if ($CaseGroup -ceq 'LaneDispatch') {
        $script:events = [Collections.Generic.List[string]]::new(); $script:failInstrumentation = $false
        function Assert-P4RemotePackagesStopped($Context, $Packages, $Stage) { $script:events.Add('stopped') }
        function Assert-P4InstalledApk($Context, $Manifest, $Role, $Kind) { $script:events.Add("install:$Role/$Kind") }
        function Set-P4Dexopt($Context, $Package, $Mode) { $script:events.Add("compile:$Mode/$Package") }
        function Assert-P4PrivateFileAbsent($Context, $Package, $RelativePath, $Stage) { $script:events.Add("reserve:$RelativePath") }
        function Invoke-P4Instrumentation($Context, $AdbArguments, $TimeoutSeconds, $ExpectedTestCount) {
            $script:events.Add("instrument:$TimeoutSeconds/$ExpectedTestCount")
            if ($script:failInstrumentation) { throw 'simulated failed instrument' }
        }
        function Copy-P4PrivateFile { throw 'Live private copy must never run for phase' }
        foreach ($index in @(12, 22, 23)) {
            $plan = Get-P4DeviceLanePlan $manifest $slots[$index] $hash
            foreach ($fail in @($false, $true)) {
                Case "instrumentation dispatch $($plan.lane) failure=$fail" {
                    $script:events.Clear(); $script:failInstrumentation = $fail
                    $failure = $null
                    try { Invoke-P4InstrumentationLane @{} $manifest $plan | Out-Null } catch { $failure = $_ }
                    Check (($null -ne $failure) -eq $fail) 'Instrumentation failure was swallowed'
                    $expected = @('stopped') + @($plan.install_kinds | ForEach-Object { "install:$($plan.role)/$_" }) +
                        @($plan.dexopt_packages | ForEach-Object { "compile:verify/$_" }) +
                        @($plan.private_files | ForEach-Object { "reserve:$($_.relative_path)" }) + @("instrument:$($plan.timeout_seconds)/1")
                    Check (($script:events -join ',') -ceq ($expected -join ',')) 'Actual instrumentation route differs'
                }
            }
        }
    }
    Check ($script:cases.Count -gt 0) 'No cases ran'
    $result.status = 'pass'
} finally {
    $watch.Stop(); $result.cases = $script:cases.ToArray(); $result.elapsed_seconds = $watch.Elapsed.TotalSeconds
    $result.source_sha256 = (Get-FileHash (Join-Path $PSScriptRoot 'measurements/p4-indexed-device-lanes.ps1') -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-NewInvocationFile (Join-Path $OutputDirectory 'results.json') ($result | ConvertTo-Json -Depth 15)
}
"PASS: $($script:cases.Count) $CaseGroup cases"

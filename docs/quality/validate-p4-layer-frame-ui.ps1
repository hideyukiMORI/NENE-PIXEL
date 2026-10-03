# Focused host checks for #145 setup. No ADB or physical performance operation.
param([Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][ValidateSet('Pixels', 'Fixtures', 'Ui', 'UiRetained', 'Native', 'WorkFlow', 'WorkFlowUnderlay', 'Compatibility', 'CompatibilityLoops', 'Sources')][string]$CaseGroup,
    [string]$StagingDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-frame-analysis.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-layer-frame-preparation.ps1')
$repository = Split-Path (Split-Path $PSScriptRoot)
$lab = [IO.Path]::GetFullPath((Get-NenePixelLabPath 'evidence' -StartDirectory $PSScriptRoot)).TrimEnd('\', '/')
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $OutputDirectory.StartsWith($lab + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    (Test-Path -LiteralPath $OutputDirectory)) { throw 'Fresh lab output required.' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$collectorPath = Join-Path $PSScriptRoot 'measurements/measure-m2-frame.ps1'
$tokens = $null; $errors = $null
$collectorAst = [Management.Automation.Language.Parser]::ParseFile($collectorPath, [ref]$tokens, [ref]$errors)
if ($errors.Count -gt 0) { throw 'Collector parse failed.' }
$functions = @($collectorAst.EndBlock.Statements | Where-Object { $_ -is [Management.Automation.Language.FunctionDefinitionAst] })
foreach ($function in $functions) { . ([scriptblock]::Create($function.Extent.Text)) }
$script:cases = [Collections.Generic.List[object]]::new()
$watch = [Diagnostics.Stopwatch]::StartNew()
$result = [ordered]@{ status = 'failure'; group = $CaseGroup; cases = @(); elapsed_seconds = 0; source_sha256 = [ordered]@{} }
function Check([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Expect-Refusal([scriptblock]$Action) {
    $refused = $false; try { & $Action } catch { $refused = $true }
    Check $refused 'Expected operation refusal'
}
function Case([string]$Name, [scriptblock]$Action, [switch]$Refuse) {
    $failure = $null
    try { & $Action } catch { $failure = $_.Exception.Message }
    $passed = if ($Refuse) { $null -ne $failure } else { $null -eq $failure }
    $script:cases.Add([ordered]@{ name = $Name; passed = $passed; refusal = [bool]$Refuse; error = $failure })
    Check $passed "Case failed: $Name : $failure"
}
function New-Pixels([string]$Group, [bool]$Diagonal) {
    foreach ($cell in @(Get-P4LayerFrameProbeCells $Diagonal)) {
        $source = @(64 + $cell.x % 192; 64 + $cell.y % 192; 64 + ($cell.x + $cell.y) % 192)
        $background = if ($Group -ceq 'layers16') { @(15,15,15) } else {
            @($source | ForEach-Object { [int][Math]::Round(($_ * 128 + 220 * 127) / 255.0) })
        }
        $drawn = if ($cell.stroke) { @(0,0,0) } else { @($background) }
        [ordered]@{ x = $cell.x; y = $cell.y; stroke = $cell.stroke; empty = @(220,220,220);
            before = @($background); preview = @($drawn); commit = @($drawn) }
    }
}

try {
    if ($CaseGroup -ceq 'Pixels') {
        foreach ($group in @('layers16', 'underlay')) {
            foreach ($diagonal in @($false, $true)) {
                Case "valid $group diagonal=$diagonal" {
                    Test-P4LayerFrameProbePixels $group $diagonal @(New-Pixels $group $diagonal)
                }
            }
        }
        foreach ($kind in @('missing', 'reordered', 'stroke', 'background', 'stale-preview', 'missing-commit', 'off-stroke', 'channel')) {
            Case "refuse pixel $kind" {
                $pixels = @(New-Pixels 'underlay' $true)
                switch ($kind) {
                    'missing' { $pixels = $pixels[0..8] }
                    'reordered' { $pixels[0], $pixels[1] = $pixels[1], $pixels[0] }
                    'stroke' { $pixels[0].stroke = $false }
                    'background' { $pixels[5].before[0] += 8 }
                    'stale-preview' { $pixels[0].preview = @($pixels[0].before) }
                    'missing-commit' { $pixels[0].commit = @($pixels[0].before) }
                    'off-stroke' { $pixels[5].preview = @(0,0,0) }
                    'channel' { $pixels[0].commit[1] = 0.25 }
                }
                Test-P4LayerFrameProbePixels 'underlay' $true $pixels
            } -Refuse
        }
        Case 'empty pixel collection refused' { Test-P4LayerFrameProbePixels 'layers16' $false @() } -Refuse
        foreach ($elapsed in @(0.0, 254.0, 280.5, 284.0)) {
            Case "remaining native bound $elapsed" {
                $expected = [int][Math]::Min(30, [Math]::Floor(285 - $elapsed))
                Check ((Get-P4LayerSetupNativeTimeout $elapsed) -eq $expected) 'Budget derivation differs'
            }
        }
        foreach ($elapsed in @(285.0, 300.0, -1.0, [double]::NaN, [double]::PositiveInfinity)) {
            Case "expired/invalid native bound $elapsed" { Get-P4LayerSetupNativeTimeout $elapsed | Out-Null } -Refuse
        }
        Add-Type -AssemblyName System.Drawing
        foreach ($group in @('layers16', 'underlay')) {
            Case "actual PNG geometry and pixels $group" {
                $paths = [ordered]@{}; $rows = @(New-Pixels $group $true)
                $geometry = [pscustomobject]@{ OriginX = 100; OriginY = 100; Fit = 3 }
                foreach ($phase in @('empty', 'before', 'preview', 'commit')) {
                    $path = Join-Path $OutputDirectory "$group-$phase.png"
                    $bitmap = [Drawing.Bitmap]::new(1920,1200)
                    try {
                        foreach ($row in $rows) {
                            $rgb = $row[$phase]
                            $bitmap.SetPixel([int][Math]::Floor(100+($row.x+0.5)*3), [int][Math]::Floor(100+($row.y+0.5)*3),
                                [Drawing.Color]::FromArgb($rgb[0],$rgb[1],$rgb[2]))
                        }
                        $bitmap.Save($path, [Drawing.Imaging.ImageFormat]::Png)
                    } finally { $bitmap.Dispose() }
                    $paths[$phase] = $path
                }
                $frameContract = @{ group_id = $group }
                $observed = @(Assert-P4LayerFramePreviewImages @{ move_event_count = 16 } $geometry $paths)
                Check ($observed.Count -eq 10) 'Actual PNG probes lost rows'
            }
        }
    }
    if ($CaseGroup -ceq 'Fixtures') {
        if ([string]::IsNullOrWhiteSpace($StagingDirectory)) { throw 'Retained staging inputs required.' }
        foreach ($index in 1..8) {
            $inputPath = Join-Path $StagingDirectory ('case-{0:D3}-input.json' -f $index)
            $inputRecord = Get-Content -Raw -LiteralPath $inputPath | ConvertFrom-Json -AsHashtable
            $identityPath = Join-Path $OutputDirectory "fixture-$index.txt"
            $instrumentationPath = Join-Path $OutputDirectory "instrumentation-$index.txt"
            [IO.File]::WriteAllLines($identityPath, [string[]]$inputRecord.IdentityLines)
            [IO.File]::WriteAllLines($instrumentationPath, [string[]]$inputRecord.InstrumentationLines)
            $evidence = [ordered]@{ phase_context = $inputRecord.PhaseContext; identity_path = $identityPath;
                identity_sha256 = (Get-FileHash $identityPath).Hash.ToLowerInvariant(); instrumentation_path = $instrumentationPath;
                instrumentation_sha256 = (Get-FileHash $instrumentationPath).Hash.ToLowerInvariant() }
            $context = [ordered]@{}
            foreach ($key in @('protocol_id', 'slot_id', 'preflight_sha256', 'production_commit')) { $context[$key] = $inputRecord.PhaseContext[$key] }
            $context.baseline_reference = $null
            Case "retained fixture caller binding $index" {
                $actual = Read-P4LayerFrameSetupFixture $evidence $context ('c'*40) 'phase-staging-contract'
                Check ($actual.slot_id -ceq $context.slot_id) 'Wrong retained slot'
            }
        }
        foreach ($key in @('protocol_id', 'slot_id', 'preflight_sha256', 'production_commit')) {
            Case "foreign caller $key" {
                $bad = [ordered]@{}; foreach ($k in $context.Keys) { $bad[$k] = $context[$k] }; $bad[$key] = 'foreign'
                Read-P4LayerFrameSetupFixture $evidence $bad ('c'*40) 'phase-staging-contract' | Out-Null
            } -Refuse
        }
        Case 'foreign measurement build' { Read-P4LayerFrameSetupFixture $evidence $context ('f'*40) 'phase-staging-contract' | Out-Null } -Refuse
        Case 'foreign experiment' { Read-P4LayerFrameSetupFixture $evidence $context ('c'*40) 'foreign-experiment' | Out-Null } -Refuse
        foreach ($key in @('identity_sha256', 'instrumentation_sha256')) {
            Case "changed raw input $key" {
                $bad = [ordered]@{}; foreach ($k in $evidence.Keys) { $bad[$k] = $evidence[$k] }; $bad[$key] = 'f'*64
                Read-P4LayerFrameSetupFixture $bad $context ('c'*40) 'phase-staging-contract' | Out-Null
            } -Refuse
        }
    }
    if ($CaseGroup -cin @('Ui', 'UiRetained')) {
        $DeviceSerial = 'host-contract'; $remotePrefix = '/data/local/tmp/host-contract'
        $requiredRotation = 1; $requiredRootBounds = '[0,0][1920,1200]'
        $script:uiQueue = [Collections.Generic.Queue[object]]::new()
        $script:adbCalls = [Collections.Generic.List[object]]::new()
        function Get-CurrentEditorUi {
            if ($script:uiQueue.Count -eq 0) { throw 'Unexpected UI read.' }
            return $script:uiQueue.Dequeue()
        }
        function Invoke-TargetAdb {
            param([string[]]$AdbArguments)
            $script:adbCalls.Add(@($AdbArguments))
            if ($AdbArguments -contains 'resolve-activity') { return 'com.android.documentsui/.FilesActivity' }
        }
        function Queue-Ui([string]$Inner) {
            $script:uiQueue.Enqueue([xml]("<hierarchy rotation='1'><node bounds='[0,0][1920,1200]'>$Inner</node></hierarchy>"))
        }
        if ($CaseGroup -ceq 'Ui') { Case 'real picker drawer root and unique document click' {
            Queue-Ui "<node resource-id='com.android.documentsui:id/toolbar'><node class='android.widget.ImageButton' clickable='true' enabled='true' bounds='[0,0][20,20]'/></node>"
            Queue-Ui "<node clickable='true' enabled='true' bounds='[20,20][40,40]'><node resource-id='android:id/title' text='NENE-PIXEL Acceptance'/></node>"
            Queue-Ui "<node resource-id='com.android.documentsui:id/toolbar'><node text='NENE-PIXEL Acceptance'/></node><node clickable='true' enabled='true' bounds='[40,40][60,60]'><node text='i89-145-aaaaaaaaaaaa-frame-5-layers16.nenepixel'/></node>"
            Open-P4LayerSetupProviderDocument 'i89-145-aaaaaaaaaaaa-frame-5-layers16.nenepixel' 'application/octet-stream'
            Check ($script:uiQueue.Count -eq 0 -and $script:adbCalls.Count -eq 4) 'Picker sequence differs'
            Check (($script:adbCalls[3] -join ' ') -ceq 'shell cmd input tap 50 50') 'Picker did not use clickable ancestor'
        } }
        Case 'foreign fixture name makes no native call' {
            $before = $script:adbCalls.Count
            Expect-Refusal { Open-P4LayerSetupProviderDocument 'other.png' 'image/png' }
            Check ($script:adbCalls.Count -eq $before) 'Unpinned name reached native boundary'
        }
        if ($CaseGroup -ceq 'Ui') { Case 'ambiguous picker drawer refused' {
            Queue-Ui "<node resource-id='com.android.documentsui:id/toolbar'><node class='android.widget.ImageButton' clickable='true'/><node class='android.widget.ImageButton' clickable='true'/></node>"
            Open-P4LayerSetupProviderDocument 'i89-145-aaaaaaaaaaaa-frame-9-underlay.png' 'image/png'
        } -Refuse
        Case 'disabled clickable parent refused' {
            [xml]$ui = "<root><node enabled='false' clickable='true'><node text='target'/></node></root>"
            Invoke-P4LayerSetupClickable $ui.SelectSingleNode('//*[@text]')
        } -Refuse }
        $resolvedOutput = $OutputDirectory; $PhaseContext = @{ slot_id = 'frame-5-layers16-baseline-decision' }
        Case 'setup retains UI and successful result' {
            Invoke-P4LayerFrameSetup 'valid-setup' { Save-P4LayerSetupUi '<hierarchy/>' }
            $record = Get-Content -Raw (Join-Path $script:layerSetupDirectory 'valid-setup.json') | ConvertFrom-Json
            Check ($record.status -ceq 'pass' -and -not $script:layerSetupActive) 'Setup success state differs'
            Check (@(Get-ChildItem $script:layerSetupDirectory -Filter '*.xml').Count -eq 1) 'Setup XML absent'
        }
        Case 'failed setup retains prefix and propagates' {
            Expect-Refusal { Invoke-P4LayerFrameSetup 'failed-setup' { Save-P4LayerSetupUi '<partial/>'; throw 'injected-source-failure' } }
                $record = Get-Content -Raw (Join-Path $script:layerSetupDirectory 'failed-setup.json') | ConvertFrom-Json
                Check ($record.status -ceq 'failure' -and $record.error -ceq 'injected-source-failure' -and
                    -not $script:layerSetupActive) 'Setup failure evidence differs'
                Check (@(Get-ChildItem $script:layerSetupDirectory -Filter '*.xml').Count -eq 2) 'Failure lost UI prefix'
        }
        Case 'occupied setup result refuses action' {
            $script:actionRan = $false
            Expect-Refusal { Invoke-P4LayerFrameSetup 'valid-setup' { $script:actionRan = $true } }
            Check (-not $script:actionRan) 'Occupied setup executed its action'
        }
        Case 'nested setup refuses without losing outer result' {
            Expect-Refusal { Invoke-P4LayerFrameSetup 'outer-setup' { Invoke-P4LayerFrameSetup 'inner-setup' {} } }
                Check ((Test-Path (Join-Path $script:layerSetupDirectory 'outer-setup.json')) -and
                    -not (Test-Path (Join-Path $script:layerSetupDirectory 'inner-setup.json'))) 'Nested setup wrote unexpected output'
        }
    }
    if ($CaseGroup -cin @('WorkFlow', 'WorkFlowUnderlay')) {
        $script:flow = [Collections.Generic.List[string]]::new(); $script:panel = $false; $script:replace = $false
        $script:layerFixture = @{ fixture_identity = @{ fixture_name = 'pinned-name' } }
        function Get-CurrentEditorUi {
            $tags = @('editor_file', 'editor_load', 'editor_layers', 'editor_underlay_more', 'editor_underlay_replace',
                'editor_underlay_opacity', 'editor_layer_panel_close')
            if ($script:panel) { $tags += 'editor_layer_panel' }
            if (-not $script:replace) { $tags += 'editor_underlay_pick' }
            $nodes = ($tags | ForEach-Object { "<node resource-id='$_'/>" }) -join ''
            return [xml]("<hierarchy><node>$nodes<node resource-id='editor_underlay_visibility' content-desc='Hide underlay'/><node resource-id='editor_redo' enabled='false'/></node></hierarchy>")
        }
        function Invoke-NodeTap { param($Node)
            $id = $Node.GetAttribute('resource-id'); $script:flow.Add("tap:$id")
            if ($id -ceq 'editor_layers') { $script:panel = $true }
            if ($id -ceq 'editor_layer_panel_close') { $script:panel = $false }
        }
        function New-DocumentThroughUi { param($Spec); $script:flow.Add('new'); $script:panel = $false }
        function Wait-P4LayerSetupStatus { param($Text); $script:flow.Add("status:$Text"); return Get-CurrentEditorUi }
        function Open-P4LayerSetupProviderDocument { param($Name,$MimeType); Check ($Name -ceq 'pinned-name') 'Fixture name changed'; $script:flow.Add("open:$MimeType") }
        function Save-P4LayerSetupScreenshot { param($Name); $script:flow.Add("screenshot:$Name"); return 'empty.png' }
        function Assert-CleanWorkloadReady { param($Ui,$Workload); $script:flow.Add('clean'); return @{ Fit = 3 } }
        $groups = if ($CaseGroup -ceq 'WorkFlowUnderlay') { @('underlay') } else { @('single', 'layers16', 'underlay') }
        foreach ($group in $groups) {
            $frameContract = @{ group_id = $group }; $script:flow.Clear(); $script:panel = $false
            Case "normal work replacement $group" {
                $prepared = Reset-P4LayerFrameWork @{workload='family'} 'fixture' -CaptureEmpty
                $expected = switch ($group) {
                    'single' { 'new|status:New document created|clean' }
                    'layers16' { 'tap:editor_file|tap:editor_load|open:application/octet-stream|status:Project loaded|clean' }
                    'underlay' { 'new|status:New document created|screenshot:fixture-empty|tap:editor_layers|tap:editor_underlay_pick|open:image/png|status:Underlay image loaded|tap:editor_layer_panel_close|clean' }
                }
                Check (($script:flow -join '|') -ceq $expected -and $prepared.Geometry.Fit -eq 3) 'Production picker/setup order differs'
            }
        }
        Case 'remembered underlay explicitly replaced' {
            $script:flow.Clear(); $script:replace = $true
            Reset-P4LayerFrameWork @{workload='family'} 'replacement' | Out-Null
            Check (($script:flow -join '|').Contains('tap:editor_underlay_more|tap:editor_underlay_replace|open:image/png')) 'Old underlay not replaced'
        }
        if ($CaseGroup -ceq 'WorkFlow') { Case 'open panel refuses ready state' {
            $script:panel = $true; Assert-P4LayerSetupSurface (Get-CurrentEditorUi) @{workload='family'} | Out-Null
        } -Refuse
        Case 'redo history refuses ready state' {
            $script:panel = $false; $ui = Get-CurrentEditorUi
            (Get-ResourceNode $ui 'editor_redo').SetAttribute('enabled','true')
            Assert-P4LayerSetupSurface $ui @{workload='family'} | Out-Null
        } -Refuse }
    }
    if ($CaseGroup -cin @('Compatibility', 'CompatibilityLoops')) {
        $oldText = (git -C $repository show dff79afd5cc9e04b68fe5c0cc694ae190a08d16b:docs/quality/measurements/measure-m2-frame.ps1) -join "`n"
        if ($LASTEXITCODE -ne 0) { throw 'Cannot read pinned prior collector.' }
        $oldAst = [Management.Automation.Language.Parser]::ParseInput($oldText, [ref]$tokens, [ref]$errors)
        $oldFunctions = @($oldAst.EndBlock.Statements | Where-Object { $_ -is [Management.Automation.Language.FunctionDefinitionAst] })
        foreach ($old in @($oldFunctions | Where-Object { $CaseGroup -ceq 'Compatibility' })) {
            $current = @($functions | Where-Object { $_.Name -ceq $old.Name })
            Case "legacy function unchanged after explicit phase branch removal: $($old.Name)" {
                Check ($current.Count -eq 1) 'Function missing/duplicate'
                $text = $current[0].Extent.Text.Replace("`r`n", "`n")
                switch ($old.Name) {
                    'Invoke-TargetAdb' { $text = $text.Replace("    if (`$isLayerPhase -and `$script:layerSetupActive) {`n        return Invoke-P4LayerSetupAdb -AdbArguments `$AdbArguments`n    }`n", '') }
                    'Assert-CommittedResult' { $text = $text.Replace('"sample", "warmup", "preparation"', '"sample", "warmup"') }
                    'Get-CurrentEditorUi' { $text = $text.Replace("    if (`$isLayerPhase -and `$script:layerSetupActive) { Save-P4LayerSetupUi -Text `$text }`n", '') }
                    'New-DocumentThroughUi' {
                        $text = $text.Replace("    `$createdUi = if (`$isLayerPhase -and `$script:layerSetupActive) {`n        Wait-P4LayerSetupStatus 'New document created'`n    } else { Get-CurrentEditorUi }", '    $createdUi = Get-CurrentEditorUi')
                    }
                }
                Check ($text -ceq $old.Extent.Text.Replace("`r`n", "`n")) 'Historical function implementation changed'
            }
        }
        $variables = if ($CaseGroup -ceq 'CompatibilityLoops') { @('sampleIndex') } else { @('warmupIndex', 'sampleIndex') }
        foreach ($variable in $variables) {
            Case "unchanged actual $variable loop" {
                $find = { param($a) $a -is [Management.Automation.Language.ForEachStatementAst] -and $a.Variable.VariablePath.UserPath -ceq $variable }
                $oldLoop = @($oldAst.FindAll($find, $true)); $newLoop = @($collectorAst.FindAll($find, $true))
                Check ($oldLoop.Count -gt 0 -and $oldLoop.Count -eq $newLoop.Count) 'Timed/warmup loop count changed'
                for ($i = 0; $i -lt $oldLoop.Count; $i++) {
                    Check ($oldLoop[$i].Extent.Text.Replace("`r`n", "`n") -ceq $newLoop[$i].Extent.Text.Replace("`r`n", "`n")) 'Timed/warmup event sequence changed'
                }
            }
        }
        Case 'phase live barrier remains closed' {
            $text = [IO.File]::ReadAllText($collectorPath)
            Check ($text.Contains("if (`$isLayerPhase -and (-not (`$ValidateArtifactOnly -or `$ValidateExperimentOnly) -or `$InspectGeometryOnly))") -and
                $text.Contains("throw 'Layer-phase collection is not admitted:")) 'Phase live collection was opened'
            Check ($text.IndexOf('Read-P4LayerFrameSetupFixture -Evidence') -lt $text.LastIndexOf('$deviceIdentity = Get-PhysicalDeviceIdentity')) 'Fixture validation follows a device call'
        }
    }
    if ($CaseGroup -ceq 'Native') {
        $script:layerSetupDirectory = $OutputDirectory; $DeviceSerial = 'host-contract'
        $script:nativeCalls = [Collections.Generic.List[object]]::new(); $script:nativeExit = 0
        function Get-Command { param($Name,$CommandType); return [pscustomobject]@{Source='host-only-no-device.exe'} }
        function Invoke-BoundedNativeCommand {
            param([string]$RepositoryRoot,[string]$LogPath,[string]$ExecutablePath,[string[]]$NativeArguments,[int]$TimeoutSeconds)
            $script:nativeCalls.Add([ordered]@{ repository=$RepositoryRoot; log=$LogPath; executable=$ExecutablePath;
                arguments=$NativeArguments; timeout=$TimeoutSeconds })
            return [pscustomobject]@{ ExitCode=$script:nativeExit; OutputLines=@('host-output') }
        }
        Case 'actual setup native call uses shared bounded launcher' {
            $observed = @(Invoke-P4LayerSetupAdb @('shell','uiautomator','dump','host.xml'))
            $call = $script:nativeCalls[0]
            Check ($call.timeout -eq 30 -and $call.executable -ceq 'host-only-no-device.exe' -and
                ($call.arguments -join ' ') -ceq '-s host-contract shell uiautomator dump host.xml' -and
                [IO.Path]::GetFullPath($call.repository) -eq [IO.Path]::GetFullPath($repository) -and
                $observed.Count -eq 1 -and $observed[0] -ceq 'host-output') 'Native setup boundary differs'
        }
        Case 'native failure propagates once' {
            $script:nativeExit = 7
            Expect-Refusal { Invoke-P4LayerSetupAdb @('shell','false') | Out-Null }
            Check ($script:nativeCalls.Count -eq 2) 'Native failure retried or skipped'
        }
        Case 'expired preparation refuses before native invocation' {
            $script:layerSetupWatch = [pscustomobject]@{Elapsed=[pscustomobject]@{TotalSeconds=286.0}}
            Expect-Refusal { Invoke-P4LayerSetupAdb @('shell','true') | Out-Null }
            Check ($script:nativeCalls.Count -eq 2) 'Expired setup reached native boundary'
        }
        Case 'short remaining allowance reaches actual launcher' {
            $script:layerSetupWatch = [pscustomobject]@{Elapsed=[pscustomobject]@{TotalSeconds=280.5}}
            $script:nativeExit = 0
            Invoke-P4LayerSetupAdb @('shell','true') | Out-Null
            Check ($script:nativeCalls.Count -eq 3 -and $script:nativeCalls[2].timeout -eq 4) 'Remaining setup allowance not applied'
        }
    }
    if ($CaseGroup -ceq 'Sources') {
        $commits = @('8120c06fae1a372b23d2a7af4f50aa2b9cdfeff9', '169b59287ca60e77e07ac91690450dd1a44b9ba4',
            'f92b1006be5f7145a32258446474f8640b14b60b', '1f9bb1637058d3fa4a98122f4942406211bd1c69')
        $result.production_blobs = [ordered]@{}
        $core = 'core/application/src/main/kotlin/io/github/hideyukimori/nenepixel/core/application/'
        foreach ($path in @('persistence/PersistenceAutosaveFlow.kt', 'persistence/PersistenceSwitchCommitFlow.kt', 'editor/SwitchCommitTransitions.kt')) {
            Case "all four source bindings $path" {
                $blobs = @($commits | ForEach-Object { git -C $repository rev-parse "${_}:$core$path" })
                Check ($LASTEXITCODE -eq 0 -and @($blobs | Select-Object -Unique).Count -eq 1) 'Switch/autosave implementation differs'
                $result.production_blobs[$path] = $blobs
            }
        }
        foreach ($commit in $commits) {
            Case "normal switch waits publication $commit" {
                $source = (git -C $repository show "${commit}:$core`persistence/PersistenceSwitchFlow.kt") -join "`n"
                Check ($LASTEXITCODE -eq 0 -and $source.Contains('autosave.retryAfterPublication { applyStart(operations.beginLoad()) }') -and
                    $source.Contains('autosave.retryAfterPublication { applyStart(operations.beginNewDocument(request)) }')) 'Normal switch wait changed'
            }
            foreach ($locale in @('values', 'values-ja', 'values-b+zh+Hans')) {
                Case "actual status labels $commit $locale" {
                    $path = "presentation/compose/src/main/res/$locale/strings.xml"
                    [xml]$strings = (git -C $repository show "${commit}:$path") -join "`n"
                    Check ($LASTEXITCODE -eq 0) 'Status resource absent'
                    $labels = [ordered]@{ project_loaded = 'Project loaded'; new_document_created = 'New document created' }
                    if ($commit -cin $commits[2..3]) { $labels.underlay_picked = 'Underlay image loaded'; $labels.underlay_hide = 'Hide underlay' }
                    foreach ($key in $labels.Keys) {
                        $value = $strings.SelectSingleNode("/resources/string[@name='$key']").InnerText
                        Check ($value -cin @(Get-P4LayerSetupStatusLabels $labels[$key])) 'Actual resource is not an accepted setup label'
                    }
                    $result.production_blobs["$commit/$path"] = (git -C $repository rev-parse "${commit}:$path") -join ''
                }
            }
        }
        function Get-CurrentEditorUi { return $script:localizedUi }
        foreach ($status in @('Project loaded', 'New document created', 'Underlay image loaded')) {
            foreach ($label in @(Get-P4LayerSetupStatusLabels $status)) {
                Case "actual status wait $label" {
                    $script:localizedUi = [xml]("<root><node resource-id='editor_operation_status' text='$label'/></root>")
                    Check ($null -ne (Wait-P4LayerSetupStatus $status)) 'Localized successful operation refused'
                }
            }
        }
    }
    $result.status = 'pass'
} finally {
    $watch.Stop(); $result.elapsed_seconds = $watch.Elapsed.TotalSeconds; $result.cases = $script:cases.ToArray()
    foreach ($path in @($PSCommandPath, $collectorPath, (Join-Path $PSScriptRoot 'measurements/p4-layer-frame-preparation.ps1'))) {
        $result.source_sha256[$path] = (Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    [IO.File]::WriteAllText((Join-Path $OutputDirectory 'results.json'), ($result | ConvertTo-Json -Depth 15), [Text.UTF8Encoding]::new($false))
}
"PASS: $CaseGroup $($script:cases.Count) focused frame setup cases"

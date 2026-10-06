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
        foreach ($index in 1..4) {
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
            Queue-Ui "<node resource-id='com.android.documentsui:id/toolbar'><node text='NENE-PIXEL Acceptance'/></node><node clickable='true' enabled='true' bounds='[40,40][60,60]'><node text='i89-145-aaaaaaaaaaaa-frame-3-layers16.nenepixel'/></node>"
            Open-P4LayerSetupProviderDocument 'i89-145-aaaaaaaaaaaa-frame-3-layers16.nenepixel' 'application/octet-stream'
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
            Open-P4LayerSetupProviderDocument 'i89-145-aaaaaaaaaaaa-frame-5-underlay.png' 'image/png'
        } -Refuse
        Case 'disabled clickable parent refused' {
            [xml]$ui = "<root><node enabled='false' clickable='true'><node text='target'/></node></root>"
            Invoke-P4LayerSetupClickable $ui.SelectSingleNode('//*[@text]')
        } -Refuse }
        $resolvedOutput = $OutputDirectory; $PhaseContext = @{ slot_id = 'frame-3-layers16-baseline-decision' }
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
        # Pinned by hash, not by commit (a commit pin breaks under rebase/squash; #145 T7c). Each value is the SHA-256
        # of the LF-normalised text of a function (or timed loop) of the pre-layer collector (dff79af), recorded from
        # HEAD after checking HEAD (with the layer branches below removed) equal to that collector. Changing the
        # legacy baseline needs a commit that changes these tables.
        $legacyFunctionSha256 = [ordered]@{
            'Assert-M2ExperimentAttemptPolicy' = '334a069f66de767415cba1980b445d816d4d74e0aea8acc69831f407f66e91e6'
            'Get-M2ZipEntrySha256' = 'b5a7ee258a8f45e7ce79ecac8e4fa9dfcae2709065e50f6b906e9622344cf5e5'
            'Assert-M2PackagedArtifact' = '12aee1b7e65fbf4bfbbf672449f32139c155045ff63b78584e9777fc6668975e'
            'Get-RunState' = '178be91b36a8483075bdc0c84120aad50c691e1f148a1d34c5f720e4fd23f256'
            'Test-RunStateIdentity' = 'e8b197f47ba9f1b58dac4e973e5509858a0beb51012aa44ab25163e30e209dc5'
            'Get-OperationTiming' = 'f245fcf113aa553d6976df28661b4b1e96c4067916b2208901718d57e9beedf3'
            'Get-CompletedFamilyResult' = '1ecf8dac07036f032128119e3cf48856a3092192f765e1d53842cd19015705a2'
            'Complete-FrameSampleRecord' = '58a6a380e8e947496a979798c7dc88c02be64e1cd3f6af0b7d2e9d78e6e3f8b8'
            'Get-MeasuredOperationCount' = '1f0f5a460841cbefb268a92ee5a5f95357b5b32717c646062692f384ff87bba4'
            'Write-RunState' = '41283b21f6738571b235f4f957c6113e22bbcb694efdeb26bf67aad2d35d76be'
            'Invoke-TargetAdb' = '1ce12839133c5592dc95c8b3dd3fd808e587f50006601a9196dc6f945426a20a'
            'Assert-M2InstalledApkIdentity' = '186879337484492fcf005956eb53b384b34a9d94edbb574dcdfce2ee4cae94a6'
            'Get-TargetProperty' = '84f8c966a13c4c439bb44b3e313d5cc9e0f428ea438e77943e10d82b45eadc43'
            'Get-PhysicalDeviceIdentity' = '08d9d462a8217b99db0915562fe8e6a59a3f2bc45ec8453173be5d65ddefbb74'
            'Get-WindowRotationState' = '06f77135411b5c556800803cc9ec3ed6dfa39e2bed3c4ba1e2c7a2378b84ba3e'
            'Assert-PinnedLandscapeRotation' = '7cf9a8a0b696f19919f8621ed23d111bcedc071e930618415c0a0b28bca6cbd0'
            'Get-PhysicalCheckpoint' = 'd37e8516df0e589defd406bf951f308e4ac6141d3d487f39b8e2e69ea5007612'
            'Get-ResourceNode' = 'bc33e1aca3409c6bf648a5bc00f943e2da773011bd940cc856aa3ff40af3bc1c'
            'Get-ResourceNodeAttribute' = 'c5147b553da8f4ffa8369b1a7e85aa5cf6fa57ed2166533335ca578803ef2ace'
            'Get-ResourceControlNode' = '8210a56d10b1cff65a5478c1bacd00cdc49cd4e9d2d35edd5ed9b6849808c1b8'
            'Assert-LandscapeRootUi' = '6f80db8ee9a2812c2158adc9c01b8600b489834e4c235ae23244ed60e02bc019'
            'Get-WorkloadSpec' = '98fdd4a03a647cb53ac5ea0164fd7ab7168b84d4eefe10335e8917f96cbfa26d'
            'Assert-LandscapeEditorUi' = 'a970a616daecc63ecc2655de823f405bd88a87d5954af91d20ee91a55aea1645'
            'Get-InitialFitGeometry' = '9561781ffd68187c00020f3456d1f144c4c3f0d05511d5b38e7a54238d3939f2'
            'Format-InputCoordinate' = 'fadbdd1906e30a154b6eed0c72d945f2997c2c045829343d720ce26aca1ce82b'
            'Get-RequiredMatchValue' = '041a55b17da556453ffa708cd79566dad7645e82bb4d722b7ada2537bb0fc6d2'
            'Get-Bounds' = '71355982197c470ad7205245b9524a5faa44ae538b04fecbef682202d3577cfc'
            'Get-NearestRank' = 'd417b3ef8dcf667917d2a71134ea6946965e13aa699103e407e39fd8df0d71f3'
            'Get-FrameRows' = '65b56a918af04c754662604e33b7833770be0845ca5fde7ef119f3cdf9c33897'
            'Get-OperationPhaseCapture' = 'a985cef9e6ceb9dea1db584c46d2e33c08eb0bb4f4f9d23beff1dbd834a5550c'
            'Assert-UnchangedPhaseCapture' = '68e767eb2d384dfb95ee09feaf9f70dc4baf26c9d8fdb6afec9ff1c428a20b7a'
            'Set-LatestUiFailureSnapshot' = '8896a3142838ca08be12bca640a43ce7e88b35e5f24036f79c35e291ee37f218'
            'Write-Utf8CreateNew' = '1abee312bb94c1b684078c3235ddfd658e02e9a78091be901a1d511576e7c2a9'
            'Save-LatestUiFailureSnapshots' = '617a22e6cbf9c9d6c5c2e22dbc76ca56de07a28fe3b9a62f935085da35dc29fa'
            'Invoke-UndoToCleanCheckpoint' = 'cbd6f370a2c4157c1a900bec85c1437ca62062c09fd2a6b7704fc79f83c2f67d'
            'Assert-CommittedResult' = '3e258c2f1f974fc5fd237f1bad29eeb231e7f123092cb7fb40e1e7864d291d72'
            'Get-CurrentEditorUi' = 'ab2c2e261b6ad71b7fab51d35196988b3613c3fb00337c31d4eb8dfba073c984'
            'Assert-CleanWorkloadReady' = '99909a560878915287a7d5f84769dc01cf4532f3cb7b43dafba95d4722d1f9a5'
            'Invoke-NodeTap' = '125a1cf91daf867217a93518ea52e6c6110a1a16c588ffeb73f7304b52e2a24d'
            'Set-BoundedDimensionField' = 'eda1b972c23182ca77b062ead22c58f661f9b7c5216cf4b98411d01837cdd2a3'
            'New-DocumentThroughUi' = 'fdb03fd81ba5675d73e89133173ee7a865bb367dab49f0883883095670908885'
            'Invoke-MotionEvent' = 'c54156921eac4338b3be7eeda2d2e406d3999bd784b24b7a79f8eb9f712e34aa'
            'Invoke-PreviewEventSequence' = '2c4471db8e070f9eaee4f5636a60743c9b311f4cb963549ec0eee7006af5bd95'
            'Invoke-CommitEventSequence' = '8d45fbbf0415d0b121e7a08ed2a206c9d53a51e245e37ac0f817f5b0f39a0caa'
            'Add-ActualSizeWindowLog' = '2b8fd323defd812ffefaab235d5597388713a29220671c6ef7c3f43934079391'
            'Get-ActualSizeWindowUiStep' = '63408c2b736b13c623cf88a5dc4b65611493c15e13b927213a29b63c5105086f'
            'Get-ActualSizeWindowNodes' = '34fcd6187c7968224aac3c9bf1b5cf688a427ec3ef604fc635e3f5138c222d63'
            'Show-ActualSizeWindowAtScale' = 'b8c89b7113d4bb514fd1c0ed47c2080dadb0369eb6a3a7921a13ec83e7b051cb'
            'Hide-ActualSizeWindow' = '99f281b28156a97b4105006727a7109d25f0afda58a65f0e707632d896a4f937'
            'Write-RotationStateArtifact' = '84a132d03cdeb5d20ddc23e2f4ed1c3a2b607cf914edee790c63e9b2a5e1409f'
            'Initialize-PinnedRotation' = '4c3b8d0baf107f221f784065390f32b991a690d91d26416d329262eb1bb50e21'
            'Restore-OriginalRotation' = '815f00b60c198dd82a71e586afeddb569f8f13425e0c4c1a189d18d26ebe8a94'
            'Test-RemotePathExists' = '23578116c2fde38c4a25c0a0d0633f323629d2eb020d2e8ae4eb44abdfc32f53'
            'Get-PerfettoSessionMatchCount' = 'b01dd9f47da0ce7dba465b501cee5319c9c3db533048f55b926ebfd16a32c9bc'
            'Start-PhysicalPresentTrace' = '08f16fa0763990bbc396558bec1d4a25a876414f6987a1591b64047e79aa1c3f'
            'Stop-PhysicalPresentTrace' = '77204081d3e6435a8c32e7bb649ba3e8831563dd4fe1b6a1cb9bb6f5bddcf29c'
            'Get-NoSampleCanvasBounds' = '5e69d377e18e3600cd6a1d19e618294a7210eebe1e46ebc93a33e3ee55eac808'
            'ConvertTo-NoSampleInspectionRecord' = '953865c4a3bd61ed9994dab588a2be9f0b9b629dbcc4acd4dc971b0cb74f1916'
            'Restore-OriginalStayAwake' = '35dbdedf6faf19708ba92c68b2662a20820f71fd1172c590d0105ff284ac75d4'
            'Invoke-NoSampleGeometryInspection' = '13a65502a1ad01246bc4f3efe0eaacc4de15e29678df689103f36203928fd630'
        }
        $legacyLoopSha256 = [ordered]@{
            warmupIndex = @('2ab9234b23f5f59ede3023fa7d95bd6fbc84a49b2f47747abc92e1284351ebb7')
            sampleIndex = @('d3b9483d2a1854d72badb98ef9172fd9dae153921c0847101a0d166ca2046a41', 'eb9f05c778cead7804082198edff2a9f084848453e12d795d93980dc567744d3')
        }
        function Get-LegacyTextSha256([string]$Text) {
            [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($Text.Replace("`r`n", "`n")))).ToLowerInvariant()
        }
        foreach ($legacyName in @($legacyFunctionSha256.Keys | Where-Object { $CaseGroup -ceq 'Compatibility' })) {
            $current = @($functions | Where-Object { $_.Name -ceq $legacyName })
            if ($legacyName -cin @('Get-Bounds', 'Get-InitialFitGeometry')) {
                $shared = [Management.Automation.Language.Parser]::ParseFile(
                    (Join-Path $PSScriptRoot 'measurements/p4-indexed-frame-analysis.ps1'), [ref]$null, [ref]$null)
                $current = @($shared.EndBlock.Statements | Where-Object {
                    $_ -is [Management.Automation.Language.FunctionDefinitionAst] -and $_.Name -ceq $legacyName
                })
            }
            Case "legacy function unchanged after explicit phase branch removal: $legacyName" {
                Check ($current.Count -eq 1) 'Function missing/duplicate'
                $text = $current[0].Extent.Text.Replace("`r`n", "`n")
                switch ($legacyName) {
                    'Invoke-TargetAdb' { $text = $text.Replace("    if (`$isLayerPhase -and `$script:layerSetupActive) {`n        return Invoke-P4LayerSetupAdb -AdbArguments `$AdbArguments`n    }`n", '') }
                    'Assert-CommittedResult' { $text = $text.Replace('"sample", "warmup", "preparation"', '"sample", "warmup"') }
                    'Get-CurrentEditorUi' { $text = $text.Replace("    if (`$isLayerPhase -and `$script:layerSetupActive) { Save-P4LayerSetupUi -Text `$text }`n", '') }
                    'New-DocumentThroughUi' {
                        $text = $text.Replace("    `$createdUi = if (`$isLayerPhase -and `$script:layerSetupActive) {`n        Wait-P4LayerSetupStatus 'New document created'`n    } else { Get-CurrentEditorUi }", '    $createdUi = Get-CurrentEditorUi')
                    }
                }
                Check ((Get-LegacyTextSha256 $text) -ceq $legacyFunctionSha256[$legacyName]) 'Historical function implementation changed'
            }
        }
        $variables = if ($CaseGroup -ceq 'CompatibilityLoops') { @('sampleIndex') } else { @('warmupIndex', 'sampleIndex') }
        foreach ($variable in $variables) {
            Case "unchanged actual $variable loop" {
                $find = { param($a) $a -is [Management.Automation.Language.ForEachStatementAst] -and $a.Variable.VariablePath.UserPath -ceq $variable }
                $oldLoop = @($legacyLoopSha256[$variable]); $newLoop = @($collectorAst.FindAll($find, $true))
                Check ($oldLoop.Count -gt 0 -and $oldLoop.Count -eq $newLoop.Count) 'Timed/warmup loop count changed'
                for ($i = 0; $i -lt $oldLoop.Count; $i++) {
                    Check ((Get-LegacyTextSha256 $newLoop[$i].Extent.Text) -ceq $oldLoop[$i]) 'Timed/warmup event sequence changed'
                }
            }
        }
        Case 'phase live collection open, inspection outside the phase' {
            # #145 T7b-2: collection is gated by the preflight admission and reservation, not by a collector refusal.
            $text = [IO.File]::ReadAllText($collectorPath)
            Check (-not $text.Contains('is not admitted') -and $text.Contains("if (`$isLayerPhase -and `$InspectGeometryOnly) {") -and
                $text.Contains("throw 'Layer-phase runs have no no-sample geometry inspection;")) 'Phase collector barrier differs from T7b-2'
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

# Phase-only setup helpers for the existing frame collector. No standalone device entry point.
Set-StrictMode -Version Latest
$script:layerSetupWatch = [Diagnostics.Stopwatch]::new()
$script:layerSetupActive = $false
$script:layerSetupSequence = 0
$script:layerSetupDirectory = $null

function Read-P4LayerFrameSetupFixture {
    param([Collections.IDictionary]$Evidence, [Collections.IDictionary]$FrameContext,
        [string]$BuildCommit, [string]$Experiment)
    $keys = @('phase_context', 'identity_path', 'identity_sha256', 'instrumentation_path', 'instrumentation_sha256')
    if ($null -eq $Evidence -or (($Evidence.Keys | Sort-Object) -join ',') -cne (($keys | Sort-Object) -join ',')) {
        throw 'Frame fixture evidence must contain exactly the raw inputs and their context/hashes.'
    }
    foreach ($kind in @('identity', 'instrumentation')) {
        $path = $Evidence["${kind}_path"]; $hash = $Evidence["${kind}_sha256"]
        if ($path -isnot [string] -or -not [IO.Path]::IsPathRooted($path) -or
            -not [IO.File]::Exists($path) -or (Get-Item -LiteralPath $path).Length -gt 1048576 -or
            $hash -isnot [string] -or $hash -cnotmatch '\A[0-9a-f]{64}\z' -or
            (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $hash) {
            throw "Frame fixture $kind input is absent, excessive or changed."
        }
    }
    $result = Test-P4LayerFrameFixturePreparation -PhaseContext $Evidence.phase_context `
        -IdentityLines ([IO.File]::ReadAllLines($Evidence.identity_path)) `
        -InstrumentationLines ([IO.File]::ReadAllLines($Evidence.instrumentation_path))
    foreach ($key in @('protocol_id', 'slot_id', 'preflight_sha256', 'production_commit')) {
        if ($result.phase_context[$key] -cne $FrameContext[$key]) { throw "Frame fixture differs at $key." }
    }
    if ($result.phase_context.measurement_build_commit -cne $BuildCommit -or
        $result.phase_context.experiment_id -cne $Experiment) { throw 'Frame fixture belongs to another build/experiment.' }
    return $result
}

function Get-P4LayerSetupNativeTimeout {
    param([double]$ElapsedSeconds)
    if ([double]::IsNaN($ElapsedSeconds) -or [double]::IsInfinity($ElapsedSeconds) -or $ElapsedSeconds -lt 0) {
        throw 'Invalid cumulative frame setup clock.'
    }
    # The shared native launcher may spend ten seconds terminating and five draining after timeout.
    $remaining = [Math]::Floor(300 - $ElapsedSeconds - 15)
    if ($remaining -lt 1) { throw 'The cumulative 300-second frame UI preparation allowance is exhausted.' }
    return [int][Math]::Min(30, $remaining)
}

function Invoke-P4LayerSetupAdb {
    param([string[]]$AdbArguments)
    $timeout = Get-P4LayerSetupNativeTimeout $script:layerSetupWatch.Elapsed.TotalSeconds
    $script:layerSetupSequence++
    $log = Join-Path $script:layerSetupDirectory ('native-{0:D4}.log' -f $script:layerSetupSequence)
    $result = Invoke-BoundedNativeCommand -ExecutablePath (Get-Command adb -CommandType Application).Source `
        -NativeArguments (@('-s', $DeviceSerial) + $AdbArguments) -RepositoryRoot (Split-Path (Split-Path (Split-Path $PSScriptRoot))) `
        -LogPath $log -TimeoutSeconds $timeout
    if ($result.ExitCode -ne 0) { throw "Frame setup adb failed ($($result.ExitCode)); retained at $log." }
    return $result.OutputLines
}

function Invoke-P4LayerFrameSetup {
    param([string]$Label, [scriptblock]$Action)
    if ($Label -cnotmatch '\A[a-z0-9][a-z0-9_-]+\z' -or $script:layerSetupActive) { throw 'Invalid/nested phase setup.' }
    if ($null -eq $script:layerSetupDirectory) {
        $script:layerSetupDirectory = Join-Path $resolvedOutput 'phase-setup'
        if (Test-Path -LiteralPath $script:layerSetupDirectory) { throw 'Frame setup destination is occupied.' }
        [void][IO.Directory]::CreateDirectory($script:layerSetupDirectory)
    }
    $path = Join-Path $script:layerSetupDirectory "$Label.json"
    if (Test-Path -LiteralPath $path) { throw 'Frame setup result already exists.' }
    $record = [ordered]@{ schema = 'nene-pixel-p4-layer-frame-ui-setup-v1'; label = $Label;
        phase_context = $PhaseContext; status = 'failure'; cumulative_seconds = 0; error = $null }
    $script:layerSetupWatch.Start(); $script:layerSetupActive = $true
    try {
        Get-P4LayerSetupNativeTimeout $script:layerSetupWatch.Elapsed.TotalSeconds | Out-Null
        & $Action
        if ($script:layerSetupWatch.Elapsed.TotalSeconds -gt 300) { throw 'Frame UI preparation exceeded 300 seconds.' }
        $record.status = 'pass'
    } catch { $record.error = $_.Exception.Message; throw }
    finally {
        $script:layerSetupActive = $false; $script:layerSetupWatch.Stop()
        $record.cumulative_seconds = $script:layerSetupWatch.Elapsed.TotalSeconds
        Write-Utf8CreateNew -Path $path -Text (($record | ConvertTo-Json -Depth 12) + "`n")
    }
}

function Save-P4LayerSetupUi {
    param([string]$Text)
    $script:layerSetupSequence++
    Write-Utf8CreateNew -Path (Join-Path $script:layerSetupDirectory ('ui-{0:D4}.xml' -f $script:layerSetupSequence)) -Text $Text
}

function Find-P4LayerSetupNode {
    param([xml]$Ui, [string]$Identity)
    return @($Ui.SelectNodes('//*[@resource-id]') | Where-Object {
        $id = $_.GetAttribute('resource-id'); $id -ceq $Identity -or $id.EndsWith(":id/$Identity", [StringComparison]::Ordinal)
    })
}

function Wait-P4LayerSetupUi {
    param([scriptblock]$Condition)
    foreach ($attempt in 1..5) {
        $ui = Get-CurrentEditorUi
        if (& $Condition $ui) { return $ui }
        if ($attempt -lt 5) { Start-Sleep -Milliseconds 200 }
    }
    throw 'The bounded frame setup UI condition did not become true.'
}

function Confirm-P4LayerSetupDiscard {
    param([xml]$Ui)
    $nodes = @(Find-P4LayerSetupNode $Ui 'editor_discard_continue')
    if ($nodes.Count -gt 1) { throw 'Ambiguous setup discard confirmation.' }
    if ($nodes.Count -eq 1) { Invoke-NodeTap -Node (Get-ResourceControlNode $nodes[0]) }
}

function Get-P4LayerSetupStatusLabels {
    param([string]$Text)
    switch -CaseSensitive ($Text) {
        'Project loaded' { return @('Project loaded', 'プロジェクトを読み込みました', '已读取项目') }
        'New document created' { return @('New document created', '新しい作品を作成しました', '已创建新作品') }
        'Underlay image loaded' { return @('Underlay image loaded', '下敷きの画像を読み込みました', '已载入底图') }
        'Hide underlay' { return @('Hide underlay', '下敷きを非表示にする', '隐藏底图') }
        default { throw 'Unknown setup status identity.' }
    }
}

function Wait-P4LayerSetupStatus {
    param([string]$Text)
    $labels = @(Get-P4LayerSetupStatusLabels $Text)
    return Wait-P4LayerSetupUi {
        param($ui)
        Confirm-P4LayerSetupDiscard $ui
        $nodes = @(Find-P4LayerSetupNode $ui 'editor_operation_status')
        $nodes.Count -eq 1 -and $nodes[0].GetAttribute('text') -cin $labels
    }
}

function Open-P4LayerSetupProviderDocument {
    param([string]$Name, [ValidateSet('application/octet-stream', 'image/png')][string]$MimeType)
    if ($Name -cnotmatch '\Ai89-145-[0-9a-f]{12}-frame-[1-6]-(layers16\.nenepixel|underlay\.png)\z') {
        throw 'Unpinned frame fixture name.'
    }
    $lines = @(Invoke-TargetAdb -AdbArguments @('shell', 'cmd', 'package', 'resolve-activity', '--brief',
        '-a', 'android.intent.action.OPEN_DOCUMENT', '-c', 'android.intent.category.OPENABLE', '-t', $MimeType))
    $components = @($lines | Where-Object { $_ -cmatch '\A[a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+\z' })
    if ($components.Count -ne 1) { throw 'The real document picker component is ambiguous.' }
    $picker = $components[0].Split('/')[0]
    $ui = Wait-P4LayerSetupUi { param($u)
        Confirm-P4LayerSetupDiscard $u
        @($u.SelectNodes("//*[@resource-id='$picker`:id/toolbar']")).Count -eq 1
    }
    $toolbar = $ui.SelectSingleNode("//*[@resource-id='$picker`:id/toolbar']")
    $buttons = @($toolbar.SelectNodes(".//*[@class='android.widget.ImageButton' and @clickable='true']"))
    if ($buttons.Count -ne 1) { throw 'The real document picker drawer button is ambiguous.' }
    Invoke-NodeTap $buttons[0]
    $ui = Wait-P4LayerSetupUi { param($u)
        @($u.SelectNodes("//*[@resource-id='android:id/title' and @text='NENE-PIXEL Acceptance']")).Count -eq 1
    }
    Invoke-P4LayerSetupClickable $ui.SelectSingleNode("//*[@resource-id='android:id/title' and @text='NENE-PIXEL Acceptance']")
    $ui = Wait-P4LayerSetupUi { param($u)
        $root = $u.SelectSingleNode("//*[@resource-id='$picker`:id/toolbar']")
        $null -ne $root -and @($root.SelectNodes(".//*[@text='NENE-PIXEL Acceptance']")).Count -eq 1 -and
            @($u.SelectNodes("//*[@text='$Name']")).Count -eq 1
    }
    Invoke-P4LayerSetupClickable $ui.SelectSingleNode("//*[@text='$Name']")
}

function Invoke-P4LayerSetupClickable {
    param([System.Xml.XmlElement]$Node)
    $current = $Node
    while ($null -ne $current -and $current.GetAttribute('clickable') -cne 'true') {
        $current = if ($current.ParentNode -is [Xml.XmlElement]) { $current.ParentNode } else { $null }
    }
    if ($null -eq $current -or $current.GetAttribute('enabled') -cne 'true') { throw 'Setup target has no enabled clickable owner.' }
    Invoke-NodeTap $current
}

function Save-P4LayerSetupScreenshot {
    param([string]$Name)
    if ($Name -cnotmatch '\A[a-z0-9][a-z0-9_-]+\z') { throw 'Invalid setup screenshot name.' }
    $path = Join-Path $script:layerSetupDirectory "$Name.png"
    if (Test-Path -LiteralPath $path) { throw 'Setup screenshot already exists.' }
    $remote = "$remotePrefix-$Name.png"
    Invoke-TargetAdb -AdbArguments @('shell', 'screencap', '-p', $remote) | Out-Null
    Invoke-TargetAdb -AdbArguments @('pull', $remote, $path) | Out-Null
    return $path
}

function Assert-P4LayerSetupSurface {
    param([xml]$Ui, [object]$Spec)
    foreach ($tag in @('editor_layer_panel', 'editor_underlay_adjust_bar')) {
        if (@(Find-P4LayerSetupNode $Ui $tag).Count -ne 0) { throw "Setup overlay remains visible: $tag" }
    }
    $redo = Get-ResourceNode $Ui 'editor_redo'
    if ((Get-ResourceNodeAttribute $redo 'enabled') -cne 'false') { throw 'Work replacement left Redo history.' }
    return Assert-CleanWorkloadReady -Ui $Ui -Workload $Spec.workload
}

function Reset-P4LayerFrameWork {
    param([object]$Spec, [string]$Label, [switch]$CaptureEmpty)
    $group = $frameContract.group_id
    $empty = $null
    if ($group -ceq 'layers16') {
        $ui = Get-CurrentEditorUi
        Invoke-NodeTap (Get-ResourceNode $ui 'editor_file')
        $ui = Get-CurrentEditorUi
        Invoke-NodeTap (Get-ResourceNode $ui 'editor_load')
        Open-P4LayerSetupProviderDocument -Name $script:layerFixture.fixture_identity.fixture_name -MimeType 'application/octet-stream'
        $ui = Wait-P4LayerSetupStatus 'Project loaded'
    } else {
        New-DocumentThroughUi -Spec $Spec | Out-Null
        $ui = Wait-P4LayerSetupStatus 'New document created'
        if ($group -ceq 'underlay') {
            if ($CaptureEmpty) { $empty = Save-P4LayerSetupScreenshot "$Label-empty" }
            Invoke-NodeTap (Get-ResourceNode $ui 'editor_layers')
            $ui = Get-CurrentEditorUi
            $pick = @(Find-P4LayerSetupNode $ui 'editor_underlay_pick')
            if ($pick.Count -eq 1) { Invoke-NodeTap $pick[0] }
            else {
                Invoke-NodeTap (Get-ResourceNode $ui 'editor_underlay_more')
                Invoke-NodeTap (Get-ResourceNode (Get-CurrentEditorUi) 'editor_underlay_replace')
            }
            Open-P4LayerSetupProviderDocument -Name $script:layerFixture.fixture_identity.fixture_name -MimeType 'image/png'
            $ui = Wait-P4LayerSetupStatus 'Underlay image loaded'
            # Picking can close the layer panel. Open it only when absent, then prove Shown.
            if (@(Find-P4LayerSetupNode $ui 'editor_layer_panel').Count -eq 0) {
                Invoke-NodeTap (Get-ResourceNode $ui 'editor_layers'); $ui = Get-CurrentEditorUi
            }
            $visibility = Get-ResourceNode $ui 'editor_underlay_visibility'
            if ($visibility.GetAttribute('content-desc') -cnotin @(Get-P4LayerSetupStatusLabels 'Hide underlay')) { throw 'The picked underlay is not Shown.' }
            Get-ResourceNode $ui 'editor_underlay_opacity' | Out-Null
            Invoke-NodeTap (Get-ResourceNode $ui 'editor_layer_panel_close')
            $ui = Get-CurrentEditorUi
        }
    }
    $geometry = Assert-P4LayerSetupSurface $ui $Spec
    return [pscustomobject]@{ Geometry = $geometry; EmptyPath = $empty }
}

function Get-P4LayerFrameProbeCells {
    param([bool]$Diagonal)
    $cells = [Collections.Generic.List[object]]::new()
    foreach ($xy in @(@(0,0), @(51,51), @(127,127), @(205,205), @(255,255),
            @(17,53), @(61,193), @(119,37), @(183,221), @(239,89))) {
        $cells.Add([pscustomobject]@{ x = $xy[0]; y = $xy[1]; stroke = ($xy[0] -eq $xy[1] -and ($Diagonal -or $xy[0] -eq 0)) })
    }
    return $cells.ToArray()
}

function Test-P4LayerFrameProbePixels {
    param([ValidateSet('layers16', 'underlay')][string]$Group, [bool]$Diagonal, [object[]]$Pixels)
    $cells = @(Get-P4LayerFrameProbeCells $Diagonal)
    if ($null -eq $Pixels -or $Pixels.Count -ne $cells.Count) { throw 'Functional pixel population differs.' }
    for ($i = 0; $i -lt $cells.Count; $i++) {
        $pixel = $Pixels[$i]; $cell = $cells[$i]
        if ($pixel.x -cne $cell.x -or $pixel.y -cne $cell.y -or $pixel.stroke -isnot [bool] -or
            $pixel.stroke -ne $cell.stroke) { throw 'Functional pixel coordinate/order/stroke differs.' }
        $phases = @('before', 'preview', 'commit') + $(if ($Group -ceq 'underlay') { @('empty') } else { @() })
        foreach ($phase in $phases) {
            if ($null -eq $pixel[$phase] -or $pixel[$phase].Count -ne 3) { throw 'Functional pixel has no complete RGB triplet.' }
            foreach ($value in $pixel[$phase]) {
                if ($value -isnot [int] -or $value -lt 0 -or $value -gt 255) { throw 'Functional pixel has an invalid RGB channel.' }
            }
        }
        if ($Group -ceq 'underlay') {
            $source = @(64 + $pixel.x % 192; 64 + $pixel.y % 192; 64 + ($pixel.x + $pixel.y) % 192)
            $expected = @(0..2 | ForEach-Object { ($source[$_] * 128 + $pixel.empty[$_] * 127) / 255.0 })
        } else { $expected = @(15, 15, 15) }
        foreach ($channel in 0..2) {
            if ([Math]::Abs($pixel.before[$channel] - $expected[$channel]) -gt 2) { throw 'Pinned setup background pixel differs.' }
            foreach ($phase in @('preview', 'commit')) {
                $wanted = if ($pixel.stroke) { 0 } else { $pixel.before[$channel] }
                if ([Math]::Abs($pixel.$phase[$channel] - $wanted) -gt 1) { throw "Functional $phase pixel differs." }
            }
        }
    }
}

function Assert-P4LayerFramePreviewImages {
    param([object]$Spec, [object]$Geometry, [Collections.IDictionary]$Paths)
    Add-Type -AssemblyName System.Drawing
    $bitmaps = @{}; $pixels = [Collections.Generic.List[object]]::new()
    try {
        foreach ($key in $Paths.Keys) {
            if ($null -eq $Paths[$key]) { continue }
            $bitmaps[$key] = [Drawing.Bitmap]::new($Paths[$key])
            if ($bitmaps[$key].Width -ne 1920 -or $bitmaps[$key].Height -ne 1200) { throw 'Setup screenshot has foreign geometry.' }
        }
        foreach ($cell in @(Get-P4LayerFrameProbeCells ($Spec.move_event_count -gt 0))) {
            $x = [int][Math]::Floor($Geometry.OriginX + ($cell.x + 0.5) * $Geometry.Fit)
            $y = [int][Math]::Floor($Geometry.OriginY + ($cell.y + 0.5) * $Geometry.Fit)
            $row = [ordered]@{ x = $cell.x; y = $cell.y; screen_x = $x; screen_y = $y; stroke = $cell.stroke }
            foreach ($key in $bitmaps.Keys) {
                $color = $bitmaps[$key].GetPixel($x, $y)
                $row[$key] = @([int]$color.R, [int]$color.G, [int]$color.B)
            }
            $pixels.Add($row)
        }
        Test-P4LayerFrameProbePixels -Group $frameContract.group_id -Diagonal ($Spec.move_event_count -gt 0) -Pixels $pixels.ToArray()
        return $pixels.ToArray()
    } finally { foreach ($bitmap in $bitmaps.Values) { $bitmap.Dispose() } }
}

function Test-P4LayerFrameFunctionalPreview {
    param([object]$Spec, [object]$Prepared)
    $paths = [ordered]@{ empty = $Prepared.EmptyPath; before = Save-P4LayerSetupScreenshot "$($Spec.workload)-before" }
    Invoke-PreviewEventSequence -Spec $Spec -Geometry $Prepared.Geometry
    $paths.preview = Save-P4LayerSetupScreenshot "$($Spec.workload)-preview"
    Invoke-CommitEventSequence -Spec $Spec -Geometry $Prepared.Geometry
    $paths.commit = Save-P4LayerSetupScreenshot "$($Spec.workload)-commit"
    Assert-CommittedResult -JourneyKind 'preparation' -JourneyIndex 1 -Workload $Spec.workload
    $pixels = @(Assert-P4LayerFramePreviewImages -Spec $Spec -Geometry $Prepared.Geometry -Paths $paths)
    $hashes = [ordered]@{}
    foreach ($key in $paths.Keys) { if ($null -ne $paths[$key]) { $hashes[$key] = (Get-FileHash $paths[$key] -Algorithm SHA256).Hash.ToLowerInvariant() } }
    Write-Utf8CreateNew -Path (Join-Path $script:layerSetupDirectory "$($Spec.workload)-pixels.json") `
        -Text (([ordered]@{ schema = 'nene-pixel-p4-layer-frame-pixels-v1'; paths = $paths; sha256 = $hashes; pixels = $pixels } | ConvertTo-Json -Depth 10) + "`n")
}

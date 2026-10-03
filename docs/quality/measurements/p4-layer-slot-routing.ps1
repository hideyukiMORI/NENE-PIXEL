# Phase bindings shared by the existing collector/analyzer. Full live admission stays in preflight.
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'p4-indexed-frame-analysis.ps1')
. (Join-Path $PSScriptRoot 'p4-indexed-capture-seal.ps1')

function Get-P4LayerFrameGeometry {
    param([Collections.IDictionary]$Manifest, [string]$ArtifactRole)
    $roles = @('baseline_single', 'baseline_layers16', 'baseline_underlay', 'candidate')
    if ($Manifest.protocol.id -cne 'nene-pixel-p4-layer-phase-verification-v1' -or $ArtifactRole -cnotin $roles) {
        throw 'Unknown phase geometry role/protocol.'
    }
    Assert-P4FrameExactKeys $Manifest.frame_experiment.geometry $roles 'phase geometry'
    $geometry = $Manifest.frame_experiment.geometry[$ArtifactRole]
    $dimensions = if ($ArtifactRole -cin @('baseline_single', 'candidate')) { @(16,256) } else { @(256) }
    Assert-P4FrameExactKeys $geometry @($dimensions | ForEach-Object { "canvas${_}_bounds" }) 'artifact geometry'
    foreach ($dimension in $dimensions) {
        $bounds = $geometry["canvas${dimension}_bounds"]
        if ($bounds -isnot [string] -or $bounds -cnotmatch '\A\[(\d+),(\d+)\]\[(\d+),(\d+)\]\z') {
            throw 'Malformed phase canvas geometry.'
        }
        $values = @($Matches[1],$Matches[2],$Matches[3],$Matches[4] | ForEach-Object { [long]$_ })
        if ($values[0] -ge $values[2] -or $values[1] -ge $values[3] -or $values[2] -gt 1920 -or $values[3] -gt 1200) {
            throw 'Phase canvas geometry is outside the pinned landscape surface.'
        }
    }
    return $geometry
}

function Read-P4LayerCompletedAnalysis {
    param([Collections.IDictionary]$Manifest, [Collections.IDictionary]$Slot, [string]$ManifestSha256)
    $directory = Join-Path $Manifest.output_directory $Slot.id
    $completedPath = Join-Path $directory 'completed.json'
    Assert-P4SealPathNotLinked $completedPath
    if (Test-Path -LiteralPath (Join-Path $directory 'invalid.json')) { throw 'A prior phase slot is invalid.' }
    $completed = Get-Content -Raw -LiteralPath $completedPath | ConvertFrom-Json -AsHashtable
    $identity = [ordered]@{ protocol_id = $Manifest.protocol.id; slot_id = $Slot.id;
        preflight_sha256 = $ManifestSha256; role = $Slot.role }
    Assert-P4FramePhaseIdentity $completed $identity 'completed phase predecessor'
    if ($completed.status -cne 'completed') { throw 'Phase predecessor is incomplete.' }
    foreach ($entry in @(@('analysis.json','analysis_sha256'), @('restoration.json','restoration_sha256'),
            @('worktree-after.json','worktree_after_sha256'))) {
        $path = Join-Path $directory $entry[0]; $hash = $completed[$entry[1]]
        Assert-P4SealPathNotLinked $path
        if ($hash -isnot [string] -or $hash -cnotmatch '\A[0-9a-f]{64}\z' -or
            -not [IO.File]::Exists($path) -or (Get-FileSha256 $path) -cne $hash) { throw 'Phase predecessor file drifted.' }
    }
    $seal = Read-P4VerifiedCaptureSeal -SlotDirectory $directory -SlotId $Slot.id -ExpectedSha256 $completed.capture_seal_sha256
    $analysis = Get-Content -Raw -LiteralPath (Join-Path $directory 'analysis.json') | ConvertFrom-Json -AsHashtable
    Assert-P4FramePhaseIdentity $analysis $identity 'phase predecessor analysis'
    foreach ($key in $analysis.Keys) {
        if (-not $completed.Contains($key) -or
            (ConvertTo-Json -InputObject $completed[$key] -Depth 40 -Compress) -cne
            (ConvertTo-Json -InputObject $analysis[$key] -Depth 40 -Compress)) { throw 'Completed result differs from its analysis.' }
    }
    if ($analysis.capture_seal_sha256 -cne $completed.capture_seal_sha256) { throw 'Phase analysis refers to another seal.' }
    $raw = @(Get-P4LayerRawInputs $Manifest $Slot $ManifestSha256 $directory $seal)
    Assert-P4FrameExactProjection $analysis.raw_inputs $raw 'phase predecessor raw inputs'
    return [ordered]@{ analysis = $analysis; completed = $completed; seal = $seal; directory = $directory }
}

function Get-P4LayerRawInputs {
    param($Manifest, $Slot, [string]$ManifestSha256, [string]$Directory, $Seal)
    $plan = Get-P4DeviceLanePlan $Manifest $Slot $ManifestSha256
    $names = [Collections.Generic.List[string]]::new()
    if ($Slot.lane -ceq 'frame') {
        $names.Add('frame-slot.json'); $names.Add('fixture-preparation/staging-result.json')
        if ($Slot.group_id -cne 'single') {
            $names.Add('fixture-preparation/fixture.txt'); $names.Add('fixture-preparation/instrumentation.log')
        }
        $record = Get-Content -Raw -LiteralPath (Join-Path $Directory 'frame-slot.json') | ConvertFrom-Json -AsHashtable
        $contract = Get-P4FrameExecutionContract $Manifest.protocol.id $Slot.id
        $expected = [IO.Path]::GetFullPath((Join-Path $Manifest.frame_experiment.directory $contract.frame_directory_name))
        $external = @($Seal.external_directories | Where-Object { $_.name -ceq 'frame-slot' })
        if ($external.Count -ne 1 -or
            -not [string]::Equals([IO.Path]::GetFullPath($external[0].path),$expected,[StringComparison]::OrdinalIgnoreCase) -or
            -not [string]::Equals([IO.Path]::GetFullPath($record.slot_directory),$expected,[StringComparison]::OrdinalIgnoreCase)) {
            throw 'Sealed frame root differs from the canonical phase slot.'
        }
        foreach ($file in $record.files) { $names.Add("external/frame-slot/$($file.relative_path)") }
    } else {
        $names.Add('instrumentation.log')
        foreach ($file in $plan.private_files) { $names.Add($file.destination_name) }
    }
    $records = [Collections.Generic.List[object]]::new()
    foreach ($name in @($names | Sort-Object -CaseSensitive)) {
        $sealed = @($Seal.files | Where-Object { $_.relative_path -ceq $name })
        if ($sealed.Count -ne 1) { throw "Raw phase input is not sealed: $name" }
        $path = Resolve-P4SealedFilePath $Seal $Directory $name
        Assert-P4SealPathNotLinked $path
        $item = Get-Item -LiteralPath $path -Force
        $hash = Get-FileSha256 $path
        if ($item.Length -ne $sealed[0].byte_count -or $hash -cne $sealed[0].sha256) { throw "Raw phase input changed: $name" }
        $records.Add([ordered]@{ relative_path = $name; byte_count = $item.Length; sha256 = $hash })
    }
    return $records.ToArray()
}

function Invoke-P4LayerSlotAnalysis {
    param($Manifest, $Slot, [string]$ManifestSha256, [string]$Directory, [string]$BaselineAnalysisPath)
    $plan = Get-P4DeviceLanePlan $Manifest $Slot $ManifestSha256
    $source = $Manifest.roles[$plan.role]
    if ($Slot.lane -cne 'frame' -and -not [string]::IsNullOrEmpty($BaselineAnalysisPath)) { throw 'This phase slot has no frame baseline argument.' }
    switch ($Slot.lane) {
        'memory' {
            $prior = @(foreach ($earlier in @(Get-P4LayerMemorySlotCatalog $Manifest.protocol.id)) {
                if ($earlier.id -ceq $Slot.id) { break }
                (Read-P4LayerCompletedAnalysis $Manifest $earlier $ManifestSha256).analysis
            })
            $path = Join-Path $Directory 'instrumentation.log'
            $result = Test-P4MemoryCapture -Lines ([IO.File]::ReadAllLines($path)) -Family $Slot.family -RunIndex $Slot.run `
                -BuildCommit $source.build_commit -PriorRuns $prior -PhaseContext $plan.phase_context
            $result.capture_sha256 = Get-FileSha256 $path
        }
        'publication' {
            $result = Test-P4PublicationCapture -Lines ([IO.File]::ReadAllLines((Join-Path $Directory 'publication.csv'))) `
                -StatusLines ([IO.File]::ReadAllLines((Join-Path $Directory 'publication.status'))) -Role $Slot.role `
                -IdentityLines ([IO.File]::ReadAllLines((Join-Path $Directory 'identity.txt'))) `
                -InstrumentationLines ([IO.File]::ReadAllLines((Join-Path $Directory 'instrumentation.log'))) -PhaseContext $plan.phase_context
            $result.capture_sha256 = Get-FileSha256 (Join-Path $Directory 'publication.csv')
            $result.status_sha256 = Get-FileSha256 (Join-Path $Directory 'publication.status')
        }
        'saf-save' {
            $result = Test-P4SafSaveCapture -Lines ([IO.File]::ReadAllLines((Join-Path $Directory 'save.csv'))) `
                -StatusLines ([IO.File]::ReadAllLines((Join-Path $Directory 'save.status'))) `
                -IdentityLines ([IO.File]::ReadAllLines((Join-Path $Directory 'identity.txt'))) `
                -SetupLines ([IO.File]::ReadAllLines((Join-Path $Directory 'setup.csv'))) `
                -InstrumentationLines ([IO.File]::ReadAllLines((Join-Path $Directory 'instrumentation.log'))) -PhaseContext $plan.phase_context
            $result.capture_sha256 = Get-FileSha256 (Join-Path $Directory 'save.csv')
            $result.status_sha256 = Get-FileSha256 (Join-Path $Directory 'save.status')
        }
        'frame' { $result = Invoke-P4LayerFrameAnalysis $Manifest $Slot $ManifestSha256 $Directory $BaselineAnalysisPath }
        default { throw 'Unknown phase analysis lane.' }
    }
    return $result
}

function Invoke-P4LayerFrameAnalysis {
    param($Manifest, $Slot, [string]$ManifestSha256, [string]$Directory, [string]$BaselineAnalysisPath)
    $context = Get-P4LayerFramePhaseContext $Manifest $Slot $ManifestSha256
    $contract = Get-P4FrameExecutionContract $Manifest.protocol.id $Slot.id
    $source = $Manifest.roles[$contract.artifact_role]
    $baseline = ''
    if ($Slot.role -ceq 'candidate' -and $Slot.runner -ceq 'decision') {
        $baseline = [IO.Path]::GetFullPath((Join-Path (Join-Path $Manifest.output_directory $contract.baseline_slot_id) 'analysis.json'))
        if (-not [string]::IsNullOrEmpty($BaselineAnalysisPath) -and
            -not [string]::Equals([IO.Path]::GetFullPath($BaselineAnalysisPath),$baseline,[StringComparison]::OrdinalIgnoreCase)) {
            throw 'Candidate frame baseline must be its own canonical completed group slot.'
        }
    } elseif (-not [string]::IsNullOrEmpty($BaselineAnalysisPath)) { throw 'Only a candidate decision accepts a baseline path.' }
    $path = Join-Path $Directory 'frame-slot.json'; Assert-P4SealPathNotLinked $path
    $record = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json -AsHashtable
    if ($record.schema -cne 'nene-pixel-p4-frame-slot-v2' -or $record.slot_id -cne $Slot.id -or
        $record.artifact_role -cne $contract.artifact_role) { throw 'Frame record identity differs.' }
    Assert-P4FrameExactProjection $record.phase_context $context 'frame slot context'
    $expected = [IO.Path]::GetFullPath((Join-Path $Manifest.frame_experiment.directory $contract.frame_directory_name))
    if (-not [string]::Equals([IO.Path]::GetFullPath($record.slot_directory),$expected,[StringComparison]::OrdinalIgnoreCase)) {
        throw 'Frame slot path differs from its canonical directory.'
    }
    $geometry = Get-P4LayerFrameGeometry $Manifest $contract.artifact_role
    $bounds = [ordered]@{}
    foreach ($spec in $contract.workload_catalog) { $bounds[$spec.workload] = $geometry["canvas$($spec.canvas_width)_bounds"] }
    $result = Test-P4FrameCapture -SlotDirectory $expected -Role $Slot.role -Runner $Slot.runner -SequenceIndex $Slot.run `
        -BuildCommit $source.build_commit -ExpectedApkSha256 $source.artifacts.app_release_like.sha256 -ExpectedBounds $bounds `
        -ExperimentId $Manifest.experiment_id -BaselineAnalysisPath $baseline -PhaseContext $context
    Assert-P4FrameExactProjection $record.files $result.frame_files 'frame collector inventory'
    Assert-P4LayerFrameSetupEvidence $expected $context $bounds
    $stagingPath = Join-Path $Directory 'fixture-preparation/staging-result.json'; Assert-P4SealPathNotLinked $stagingPath
    $staging = Get-Content -Raw -LiteralPath $stagingPath | ConvertFrom-Json -AsHashtable
    if ($staging.schema -cne 'nene-pixel-p4-layer-frame-staging-result-v1' -or $staging.slot_id -cne $Slot.id -or
        $staging.status -cne 'success' -or $staging.native_accepted -isnot [bool] -or -not $staging.native_accepted -or
        $staging.errors -isnot [array] -or $staging.errors.Count -ne 0) { throw 'Frame source staging was not successful.' }
    if ($contract.group_id -ceq 'single') {
        if ($null -ne $staging.fixture_evidence) { throw 'Single frame unexpectedly staged a fixture.' }
    } else {
        . (Join-Path $PSScriptRoot 'p4-layer-frame-preparation.ps1')
        $plan = Get-P4DeviceLanePlan $Manifest $Slot $ManifestSha256
        Assert-P4FrameExactProjection $staging.fixture_evidence.phase_context $plan.phase_context 'frame fixture source context'
        foreach ($pair in @(@('identity_path','fixture.txt'),@('instrumentation_path','instrumentation.log'))) {
            $expectedPath = [IO.Path]::GetFullPath((Join-Path (Split-Path $stagingPath) $pair[1]))
            if (-not [string]::Equals($expectedPath,[IO.Path]::GetFullPath($staging.fixture_evidence[$pair[0]]),[StringComparison]::OrdinalIgnoreCase)) {
                throw 'Staging source path differs from its canonical raw input.'
            }
            Assert-P4SealPathNotLinked $expectedPath
        }
        Read-P4LayerFrameSetupFixture $staging.fixture_evidence $context $source.build_commit $Manifest.experiment_id | Out-Null
    }
    $result.capture_sha256 = Get-FileSha256 $path
    return $result
}

function Get-P4LayerFramePhaseContext {
    param([Collections.IDictionary]$Manifest, [Collections.IDictionary]$Slot, [string]$ManifestSha256)
    $contract = Get-P4FrameExecutionContract -ProtocolId $Manifest.protocol.id -SlotId $Slot.id
    $role = Resolve-P4ArtifactRole $Manifest.protocol.id $Slot.id
    $context = [ordered]@{ protocol_id = $Manifest.protocol.id; slot_id = $Slot.id; preflight_sha256 = $ManifestSha256;
        production_commit = $Manifest.roles[$role].production_commit; baseline_reference = $null }
    if ($Slot.role -ceq 'candidate' -and $Slot.runner -ceq 'decision') {
        $priorSlot = Get-P4ExecutionSlot $Manifest.protocol.id $contract.baseline_slot_id
        $prior = Read-P4LayerCompletedAnalysis $Manifest $priorSlot $ManifestSha256
        $baseline = $Manifest.roles[$priorSlot.artifact_role]
        $context.baseline_reference = [ordered]@{ build_commit = $baseline.build_commit; production_commit = $baseline.production_commit;
            apk_sha256 = $baseline.artifacts.app_release_like.sha256; analysis_sha256 = $prior.completed.analysis_sha256;
            capture_seal_sha256 = $prior.completed.capture_seal_sha256 }
        $experimentPath = Join-Path $Manifest.frame_experiment.directory 'experiment.json'
        Assert-P4SealPathNotLinked $experimentPath
        $experiment = Get-Content -Raw -LiteralPath $experimentPath | ConvertFrom-Json -NoEnumerate
        Assert-P4FrameExactProjection $experiment (Get-P4LayerFrameExperimentContract $Manifest.experiment_id $ManifestSha256) 'phase experiment'
        Read-P4LayerFrameBaselineReference -BaselineAnalysisPath (Join-Path $prior.directory 'analysis.json') `
            -ExperimentId $Manifest.experiment_id -ExperimentSha256 (Get-FileSha256 $experimentPath) `
            -Contract $contract -PhaseContext $context | Out-Null
    }
    Get-P4FrameCaptureContract -Role $Slot.role -Runner $Slot.runner -SequenceIndex $Slot.run `
        -BuildCommit $Manifest.roles[$role].build_commit -ExpectedApkSha256 $Manifest.roles[$role].artifacts.app_release_like.sha256 `
        -ExperimentId $Manifest.experiment_id -PhaseContext $context | Out-Null
    return $context
}

function Install-P4LayerFrameReleaseArtifact {
    param([string]$Directory, [string]$Serial, [string]$Role, [string]$ApkPath, [string]$ApkSha256, [string]$Package)
    . (Join-Path $PSScriptRoot 'p4-indexed-device-lanes.ps1')
    $output = Join-Path $Directory 'release-install'
    if (Test-Path -LiteralPath $output) { throw 'Phase release install output is occupied.' }
    [void][IO.Directory]::CreateDirectory($output)
    $context = [ordered]@{ repository_root = Split-Path (Split-Path (Split-Path $PSScriptRoot));
        output_directory = $output; adb_path = (Get-Command adb -CommandType Application).Source; serial = $Serial }
    $source = [ordered]@{ protocol = @{ id = 'nene-pixel-p4-layer-phase-verification-v1' }; roles = @{} }
    $source.roles[$Role] = @{ artifacts = @{ app_release_like = @{ target_package = $Package; path = $ApkPath; sha256 = $ApkSha256 } } }
    Assert-P4InstalledApk -Context $context -Manifest $source -Role $Role -Kind 'app_release_like' | Out-Null
}

function Invoke-P4LayerFrameStaging {
    param([Collections.IDictionary]$Context, [Collections.IDictionary]$Manifest, [Collections.IDictionary]$Plan)
    if ($Plan.protocol_id -cne 'nene-pixel-p4-layer-phase-verification-v1' -or $Plan.lane -cne 'frame') { throw 'Phase frame plan required.' }
    $directory = Join-Path $Context.output_directory 'fixture-preparation'
    if (Test-Path -LiteralPath $directory) { throw 'Frame fixture staging output is occupied.' }
    [void][IO.Directory]::CreateDirectory($directory)
    $child = [ordered]@{}; foreach ($key in $Context.Keys) { $child[$key] = $Context[$key] }; $child.output_directory = $directory
    $errors = [Collections.Generic.List[string]]::new(); $evidence = $null; $nativeAccepted = $false
    $staged = @($Plan.private_files).Count -gt 0
    try { Invoke-P4InstrumentationLane -Context $child -Manifest $Manifest -Plan $Plan | Out-Null; $nativeAccepted = $true }
    catch { $errors.Add($_.Exception.ToString()) }
    finally {
        if ($staged) {
            $index = 0
            foreach ($package in $Plan.quiescence_packages) {
                $index++
                try {
                    $stop = Invoke-P4BoundedAdb -Context $child -LogName "fixture-stop-$index.log" `
                        -AdbArguments @('shell','am','force-stop',$package) -TimeoutSeconds 30
                    if ($stop.ExitCode -ne 0) { throw "Stopping fixture package failed: $package" }
                } catch { $errors.Add($_.Exception.Message) }
            }
            try { Copy-P4LayerPrivateReports -Context $child -Plan $Plan | Out-Null }
            catch { $errors.Add($_.Exception.Message) }
        }
        if ($nativeAccepted -and $errors.Count -eq 0 -and $staged) {
            try {
                $identityPath = Join-Path $directory 'fixture.txt'; $instrumentationPath = Join-Path $directory 'instrumentation.log'
                Test-P4LayerFrameFixturePreparation -PhaseContext $Plan.phase_context `
                    -IdentityLines ([IO.File]::ReadAllLines($identityPath)) -InstrumentationLines ([IO.File]::ReadAllLines($instrumentationPath)) | Out-Null
                $evidence = [ordered]@{ phase_context = $Plan.phase_context; identity_path = $identityPath;
                    identity_sha256 = Get-FileSha256 $identityPath; instrumentation_path = $instrumentationPath;
                    instrumentation_sha256 = Get-FileSha256 $instrumentationPath }
            } catch { $errors.Add($_.Exception.Message) }
        }
        $record = [ordered]@{ schema = 'nene-pixel-p4-layer-frame-staging-result-v1'; slot_id = $Plan.slot_id;
            status = $(if ($nativeAccepted -and $errors.Count -eq 0) { 'success' } else { 'failure' });
            native_accepted = $nativeAccepted; fixture_evidence = $evidence; errors = $errors.ToArray() }
        Write-NewInvocationFile (Join-Path $directory 'staging-result.json') ($record | ConvertTo-Json -Depth 12)
    }
    if ($record.status -cne 'success') { throw "Phase frame staging failed: $($errors -join '; ')" }
    return $record
}

function Invoke-P4LayerFrameCollector {
    param([Collections.IDictionary]$Context, [Collections.IDictionary]$Manifest, [Collections.IDictionary]$Slot,
        [Collections.IDictionary]$Plan, [string]$ManifestSha256)
    $parameters = Get-P4FrameCollectorParameters $Manifest $Slot $ManifestSha256
    $contract = Get-P4FrameExecutionContract $Manifest.protocol.id $Slot.id
    $frameDirectory = Join-Path $parameters.ExperimentDirectory $contract.frame_directory_name
    if (Test-Path -LiteralPath $frameDirectory) { throw 'Phase frame attempt already exists.' }
    $staging = Invoke-P4LayerFrameStaging $Context $Manifest $Plan
    if ($null -ne $staging.fixture_evidence) { $parameters.PhaseFixtureEvidence = $staging.fixture_evidence }
    $previousPath = $env:PATH; $failure = $null; $inventoryFailure = $null
    Push-Location $Context.repository_root
    try {
        $env:PATH = (Split-Path $Manifest.tools.adb.path) + [IO.Path]::PathSeparator + $previousPath
        $resolvedAdb = (Get-Command adb -CommandType Application).Source
        if (-not [string]::Equals([IO.Path]::GetFullPath($resolvedAdb), [IO.Path]::GetFullPath($Manifest.tools.adb.path),
                [StringComparison]::OrdinalIgnoreCase)) { throw 'Frame ADB differs from the pinned tool.' }
        & $Manifest.tools.frame_collector.path @parameters | Out-Null
        if (-not (Test-Path -LiteralPath $frameDirectory -PathType Container)) { throw 'Frame collector produced no slot output.' }
    } catch { $failure = $_ }
    finally {
        $env:PATH = $previousPath; Pop-Location
        if (Test-Path -LiteralPath $frameDirectory -PathType Container) {
            try { New-P4FrameSlotRecord -Context $Context -FrameSlotDirectory $frameDirectory -PhaseContext $parameters.PhaseContext | Out-Null }
            catch { $inventoryFailure = $_.Exception.ToString(); if ($null -eq $failure) { $failure = $_ } }
        }
    }
    if ($null -ne $failure) {
        Write-NewInvocationFile (Join-Path $Context.output_directory 'frame-collector-failure.txt') ($failure.ToString() + "`nInventory: $inventoryFailure")
        throw $failure
    }
}

function Assert-P4LayerFrameSetupEvidence {
    param([string]$SlotDirectory, [Collections.IDictionary]$PhaseContext, [Collections.IDictionary]$ExpectedBounds)
    . (Join-Path $PSScriptRoot 'p4-layer-frame-preparation.ps1')
    $frameContract = Get-P4FrameExecutionContract $PhaseContext.protocol_id $PhaseContext.slot_id
    $directory = Join-Path $SlotDirectory 'phase-setup'
    $labels = [Collections.Generic.List[string]]::new(); $labels.Add('initial')
    $index = 0
    foreach ($spec in $frameContract.workload_catalog) {
        if ($index -gt 0) { $labels.Add("initial-$($spec.workload)") }
        if ($frameContract.group_id -cne 'single') { $labels.Add("functional-$($spec.workload)") }
        $labels.Add("after-warmups-$($spec.workload)"); $index++
    }
    $previousSeconds = 0.0
    foreach ($label in $labels) {
        $path = Join-Path $directory "$label.json"; Assert-P4SealPathNotLinked $path
        $record = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json -AsHashtable
        if ($record.schema -cne 'nene-pixel-p4-layer-frame-ui-setup-v1' -or $record.label -cne $label -or
            $record.status -cne 'pass' -or $null -ne $record.error) { throw 'Frame setup did not complete.' }
        Assert-P4FrameExactProjection $record.phase_context $PhaseContext 'retained frame setup context'
        $seconds = $record.cumulative_seconds
        if ($null -eq $seconds -or $seconds.GetType() -notin @([int],[long],[double],[decimal]) -or
            [double]::IsNaN($seconds) -or [double]::IsInfinity($seconds) -or $seconds -lt $previousSeconds -or $seconds -gt 300) {
            throw 'Frame setup cumulative allowance is invalid.'
        }
        $previousSeconds = $seconds
    }
    if ($frameContract.group_id -ceq 'single') { return }
    $geometryId = $frameContract.geometry_id; $requiredLogicalWidth = 1920; $requiredLogicalHeight = 1200
    foreach ($spec in $frameContract.workload_catalog) {
        $proofPath = Join-Path $directory "$($spec.workload)-pixels.json"; Assert-P4SealPathNotLinked $proofPath
        $proof = Get-Content -Raw -LiteralPath $proofPath | ConvertFrom-Json -AsHashtable
        if ($proof.schema -cne 'nene-pixel-p4-layer-frame-pixels-v1') { throw 'Frame pixel proof schema differs.' }
        Assert-P4FrameExactKeys $proof.paths @('empty','before','preview','commit') 'frame pixel paths'
        $kinds = @('before','preview','commit')
        if ($frameContract.group_id -ceq 'underlay') { $kinds += 'empty' }
        elseif ($null -ne $proof.paths.empty) { throw 'Layered frame supplied an underlay background.' }
        Assert-P4FrameExactKeys $proof.sha256 $kinds 'frame pixel hashes'
        foreach ($kind in $kinds) {
            $name = if ($kind -ceq 'empty') { 'initial-empty.png' } else { "$($spec.workload)-$kind.png" }
            $expected = [IO.Path]::GetFullPath((Join-Path $directory $name))
            if (-not [string]::Equals($expected,[IO.Path]::GetFullPath($proof.paths[$kind]),[StringComparison]::OrdinalIgnoreCase)) {
                throw 'Frame pixel proof names another capture.'
            }
            Assert-P4SealPathNotLinked $expected
            if ((Get-FileSha256 $expected) -cne $proof.sha256[$kind]) { throw 'Frame pixel PNG changed.' }
        }
        [xml]$node = "<node bounds='$($ExpectedBounds[$spec.workload])'/>"
        $geometry = Get-InitialFitGeometry -SurfaceBounds (Get-Bounds $node.DocumentElement) -Spec $spec
        $observed = @(Assert-P4LayerFramePreviewImages -Spec $spec -Geometry $geometry -Paths $proof.paths)
        Assert-P4FrameExactProjection $proof.pixels $observed 'retained frame pixels'
    }
}

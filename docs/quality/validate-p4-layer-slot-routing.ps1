# Focused host-only integration checks. No ADB, Gradle, or device measurement.
param([Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][ValidateSet('Maps','Chain','FrameChain','FrameAnalysis','Collector','Native','Dispatch','Setup','Compatibility','CompatibilityRemaining')][string]$CaseGroup)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-device-lanes.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-memory-analysis.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-publication-analysis.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-layer-slot-routing.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-layer-frame-preparation.ps1')
$repository = Split-Path (Split-Path $PSScriptRoot)
$lab = [IO.Path]::GetFullPath((Get-NenePixelLabPath 'evidence' -StartDirectory $PSScriptRoot)).TrimEnd('\','/')
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $OutputDirectory.StartsWith($lab + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
    (Test-Path -LiteralPath $OutputDirectory)) { throw 'Fresh lab output required.' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$script:cases = [Collections.Generic.List[object]]::new()
$watch = [Diagnostics.Stopwatch]::StartNew()
$result = [ordered]@{ status = 'failure'; group = $CaseGroup; cases = @(); elapsed_seconds = 0; sources = [ordered]@{} }
function Check([bool]$Condition,[string]$Message) { if (-not $Condition) { throw $Message } }
function Clone($Value) { ConvertFrom-Json -AsHashtable -InputObject ($Value | ConvertTo-Json -Depth 50) }
function Refuses([scriptblock]$Action) { $caught=$false; try { & $Action | Out-Null } catch { $caught=$true }; Check $caught 'Expected refusal' }
function Case([string]$CaseName,[scriptblock]$Action) {
    $errorText=$null; try { & $Action | Out-Null } catch { $errorText=$_.ToString() }
    $script:cases.Add([ordered]@{ name=$CaseName; passed=($null -eq $errorText); error=$errorText })
    Check ($null -eq $errorText) "Case failed: $CaseName : $errorText"
}
function Read-Ast([string]$Path) {
    $errors=$null; $ast=[Management.Automation.Language.Parser]::ParseFile($Path,[ref]$null,[ref]$errors)
    Check ($errors.Count -eq 0) "Parse failure $Path"; return $ast
}
function Function-Text($Ast,[string]$Name) {
    $node=$Ast.Find({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $Name},$true)
    Check ($null -ne $node) "Missing function $Name"; return $node.Extent.Text
}
function Save-Json([string]$Path,$Value) { [IO.File]::WriteAllText($Path,($Value | ConvertTo-Json -Depth 50),[Text.UTF8Encoding]::new($false)) }
$phase='nene-pixel-p4-layer-phase-verification-v1'; $hash='a'*64
$ast=Read-Ast (Join-Path $PSScriptRoot 'validate-p4-layer-device-lanes.ps1')
. ([scriptblock]::Create((Function-Text $ast 'New-Manifest')))
$manifest=New-Manifest
$manifest.output_directory=Join-Path $OutputDirectory 'slots'
$manifest.frame_experiment=[ordered]@{directory=(Join-Path $OutputDirectory 'frames'); hypothesis='synthetic'; expected_affected_cost='synthetic';
    correctness_risk='synthetic'; stop_conditions='synthetic'; geometry=[ordered]@{} }
foreach($role in $manifest.roles.Keys) {
    $manifest.frame_experiment.geometry[$role]=[ordered]@{canvas256_bounds='[100,100][868,868]'}
    if($role -cin @('baseline_single','candidate')) {$manifest.frame_experiment.geometry[$role].canvas16_bounds='[100,100][868,868]'}
    $manifest.roles[$role].production_tree_sha256='e'*64
    $manifest.roles[$role].profile=@{generation_commit='c'*40;generation_app_sha256='d'*64;generation_test_sha256='d'*64;
        acceptance=@{path='synthetic';sha256='e'*64};pair=@{sha256='e'*64};canonical=@{sha256='e'*64};packaged_prof_sha256='e'*64;packaged_profm_sha256='e'*64}
}
$slots=@(Get-P4SlotCatalog $phase)
function Seal-Fixture($Slot,[string]$Directory) {
    $files=@(Get-ChildItem -LiteralPath $Directory -File -Recurse | Where-Object {$_.Name -cnotin @('capture-seal.json','analysis.json','completed.json','restoration.json','worktree-after.json')} | ForEach-Object {
        [ordered]@{relative_path=[IO.Path]::GetRelativePath($Directory,$_.FullName).Replace('\','/');byte_count=$_.Length;sha256=Get-FileSha256 $_.FullName}
    })
    $externals=@()
    if($Slot.lane -ceq 'frame') {
        $record=Get-Content -Raw (Join-Path $Directory 'frame-slot.json')|ConvertFrom-Json -AsHashtable
        $externals=@(@{name='frame-slot';path=$record.slot_directory})
        $files+=@(foreach($file in $record.files) {[ordered]@{relative_path="external/frame-slot/$($file.relative_path)";byte_count=$file.byte_count;sha256=$file.sha256}})
    }
    $seal=[ordered]@{schema='nene-pixel-p4-capture-seal-v1';slot_id=$Slot.id;external_directories=$externals;files=@($files|Sort-Object relative_path -CaseSensitive)}
    Save-Json (Join-Path $Directory 'capture-seal.json') $seal; return $seal
}
function Complete-Fixture($Slot,$Analysis,[string]$Directory) {
    $seal=Seal-Fixture $Slot $Directory
    $Analysis.protocol_id=$phase; $Analysis.slot_id=$Slot.id; $Analysis.preflight_sha256=$hash
    $Analysis.capture_seal_sha256=Get-FileSha256 (Join-Path $Directory 'capture-seal.json')
    $Analysis.raw_inputs=@(Get-P4LayerRawInputs $manifest $Slot $hash $Directory $seal)
    Save-Json (Join-Path $Directory 'analysis.json') $Analysis
    Save-Json (Join-Path $Directory 'restoration.json') @{synthetic=$true}
    Save-Json (Join-Path $Directory 'worktree-after.json') @{synthetic=$true}
    $completed=Clone $Analysis; $completed.status='completed'
    foreach($pair in @(@('analysis.json','analysis_sha256'),@('restoration.json','restoration_sha256'),@('worktree-after.json','worktree_after_sha256'))) {
        $completed[$pair[1]]=Get-FileSha256 (Join-Path $Directory $pair[0])
    }
    Save-Json (Join-Path $Directory 'completed.json') $completed
}
function New-StagingFixture($Slot,$Plan,[string]$Directory) {
    [void][IO.Directory]::CreateDirectory($Directory)
    $evidence=$null
    if($Slot.group_id -cne 'single') {
        $ast=Read-Ast (Join-Path $PSScriptRoot 'validate-p4-layer-frame-staging.ps1')
        . ([scriptblock]::Create((Function-Text $ast 'New-Evidence')))
        $fixtureInput=New-Evidence $Slot
        foreach($key in $Plan.phase_context.Keys) {$fixtureInput.fields[$key]=$Plan.phase_context[$key]}
        $identity='P4_LAYER_FRAME_FIXTURE '+(($fixtureInput.fields.Keys|ForEach-Object {"$_=$($fixtureInput.fields[$_])"}) -join ' ')
        $instrumentation=@('INSTRUMENTATION_STATUS: class=fixture','INSTRUMENTATION_STATUS_CODE: 1',
            "INSTRUMENTATION_STATUS: p4LayerFrameFixture=$identity",'INSTRUMENTATION_STATUS_CODE: 3',
            'INSTRUMENTATION_STATUS: class=fixture','INSTRUMENTATION_STATUS_CODE: 0','OK (1 test)','INSTRUMENTATION_CODE: -1')
        $identityPath=Join-Path $Directory 'fixture.txt';$instrumentationPath=Join-Path $Directory 'instrumentation.log'
        [IO.File]::WriteAllLines($identityPath,[string[]]@($identity));[IO.File]::WriteAllLines($instrumentationPath,$instrumentation)
        $evidence=[ordered]@{phase_context=$Plan.phase_context;identity_path=$identityPath;identity_sha256=Get-FileSha256 $identityPath;
            instrumentation_path=$instrumentationPath;instrumentation_sha256=Get-FileSha256 $instrumentationPath}
    }
    $record=[ordered]@{schema='nene-pixel-p4-layer-frame-staging-result-v1';slot_id=$Slot.id;status='success';native_accepted=$true;fixture_evidence=$evidence;errors=@()}
    Save-Json (Join-Path $Directory 'staging-result.json') $record
    return $record
}
function New-SetupFixture($Slot,[string]$FrameDirectory,$Context) {
    $contract=Get-P4FrameExecutionContract $phase $Slot.id
    $directory=Join-Path $FrameDirectory 'phase-setup';[void][IO.Directory]::CreateDirectory($directory)
    $labels=[Collections.Generic.List[string]]::new();$labels.Add('initial');$index=0
    foreach($spec in $contract.workload_catalog) {
        if($index -gt 0) {$labels.Add("initial-$($spec.workload)")}
        if($contract.group_id -cne 'single') {$labels.Add("functional-$($spec.workload)")}
        $labels.Add("after-warmups-$($spec.workload)");$index++
    }
    $counter=0
    foreach($label in $labels) {$counter++;Save-Json (Join-Path $directory "$label.json") ([ordered]@{
        schema='nene-pixel-p4-layer-frame-ui-setup-v1';label=$label;status='pass';error=$null;phase_context=$Context;cumulative_seconds=$counter})}
    if($contract.group_id -ceq 'single') {return}
    $ast=Read-Ast (Join-Path $PSScriptRoot 'validate-p4-layer-frame-ui.ps1')
    . ([scriptblock]::Create((Function-Text $ast 'New-Pixels')))
    Add-Type -AssemblyName System.Drawing
    foreach($spec in $contract.workload_catalog) {
        $rows=@(New-Pixels $contract.group_id ($spec.move_event_count -gt 0))
        $paths=[ordered]@{empty=$null;before=$null;preview=$null;commit=$null};$hashes=[ordered]@{}
        $kinds=@('before','preview','commit');if($contract.group_id -ceq 'underlay') {$kinds+='empty'}
        foreach($kind in $kinds) {
            $name=if($kind -ceq 'empty') {'initial-empty.png'} else {"$($spec.workload)-$kind.png"}
            $path=Join-Path $directory $name;$bitmap=[Drawing.Bitmap]::new(1920,1200)
            try {
                foreach($row in $rows) {$rgb=$row[$kind];$x=[int][Math]::Floor(100+($row.x+0.5)*3);$y=[int][Math]::Floor(100+($row.y+0.5)*3)
                    $bitmap.SetPixel($x,$y,[Drawing.Color]::FromArgb($rgb[0],$rgb[1],$rgb[2]))}
                $bitmap.Save($path,[Drawing.Imaging.ImageFormat]::Png)
            } finally {$bitmap.Dispose()}
            $paths[$kind]=$path;$hashes[$kind]=Get-FileSha256 $path
        }
        foreach($row in $rows) {
            $row.screen_x=[int][Math]::Floor(100+($row.x+0.5)*3);$row.screen_y=[int][Math]::Floor(100+($row.y+0.5)*3)
            if($contract.group_id -cne 'underlay') {$row.Remove('empty')}
        }
        Save-Json (Join-Path $directory "$($spec.workload)-pixels.json") ([ordered]@{
            schema='nene-pixel-p4-layer-frame-pixels-v1';paths=$paths;sha256=$hashes;pixels=$rows})
    }
}
try {
    if($CaseGroup -ceq 'Maps') {
        foreach($slot in $slots) { Case "canonical role resolution $($slot.id)" {
            Check ((Get-P4ExecutionSlot $phase $slot.id).artifact_role -ceq $slot.artifact_role -and
                (Resolve-P4ArtifactRole $phase $slot.id) -ceq $slot.artifact_role) 'Artifact role differs'
        } }
        foreach($slot in @($slots|Where-Object {$_.lane -ceq 'frame' -and -not ($_.role -ceq 'candidate' -and $_.runner -ceq 'decision')})) {
            Case "frame parameters $($slot.id)" {
                $p=Get-P4FrameCollectorParameters $manifest $slot $hash
                Check ($p.SourceCommit -ceq $manifest.roles[$slot.artifact_role].build_commit -and $p.PhaseContext.slot_id -ceq $slot.id) 'Wrong context/source'
                Check ($p.Contains('BaselineCanvas16SurfaceBounds') -eq ($slot.group_id -ceq 'single')) 'Wrong optional geometry'
                $baselineRole=@(Get-P4FrameGroupCatalog $phase|Where-Object {$_.id -ceq $slot.group_id})[0].baseline_artifact_role
                Check ($p.BaselineProductionCommit -ceq $manifest.roles[$baselineRole].production_commit) 'Wrong group baseline'
            }
        }
        foreach($bad in @('[0,0][0,10]','[0,0][1921,1200]','[0,0][1920,1201]','[a,0][1,2]')) {
            Case "geometry refusal $bad" {$m=Clone $manifest;$m.frame_experiment.geometry.baseline_layers16.canvas256_bounds=$bad;Refuses {Get-P4LayerFrameGeometry $m 'baseline_layers16'}}
        }
        Case 'unused geometry refused' {$m=Clone $manifest;$m.frame_experiment.geometry.baseline_underlay.canvas16_bounds='[0,0][10,10]';Refuses {Get-P4LayerFrameGeometry $m 'baseline_underlay'}}
        Case 'phase live admission still closed' { Refuses {Assert-P4ManifestContract $manifest} }
    }
    if($CaseGroup -ceq 'Chain') {
        $ast=Read-Ast (Join-Path $PSScriptRoot 'validate-p4-layer-memory-evidence.ps1')
        foreach($name in @('Format-Record','New-Lines')) {. ([scriptblock]::Create((Function-Text $ast $name)))}
        $checkpoints=@('empty_idle','maximum_loaded_idle','long_preview_held','committed_idle','post_cycles_idle')
        foreach($slot in @($slots|Where-Object {$_.lane -ceq 'memory'})) {
            Case "real ordered memory analysis $($slot.id)" {
                $plan=Get-P4DeviceLanePlan $manifest $slot $hash
                $directory=Join-Path $manifest.output_directory $slot.id;[void][IO.Directory]::CreateDirectory($directory)
                [IO.File]::WriteAllLines((Join-Path $directory 'instrumentation.log'),[string[]](New-Lines -Sequence $slot.memory_sequence_index -Context $plan.phase_context))
                $analysis=Invoke-P4LayerSlotAnalysis $manifest $slot $hash $directory ''
                Check ($analysis.verdict -ceq 'pass' -and $analysis.run_index -eq $slot.run) 'Memory routing changed result'
                Complete-Fixture $slot $analysis $directory
                $read=Read-P4LayerCompletedAnalysis $manifest $slot $hash
                Check ($read.analysis.raw_inputs.Count -eq 1 -and $read.analysis.raw_inputs[0].relative_path -ceq 'instrumentation.log') 'Wrong memory raw binding'
            }
        }
        $first=@($slots|Where-Object {$_.lane -ceq 'memory'})[0]
        foreach($kind in @('raw','completed','analysis','restoration','worktree','invalid','foreign-preflight','raw-name')) {
            Case "predecessor refuses $kind" {
                $copyRoot=Join-Path $OutputDirectory "bad-$kind";[void][IO.Directory]::CreateDirectory($copyRoot)
                Copy-Item -LiteralPath (Join-Path $manifest.output_directory $first.id) -Destination $copyRoot -Recurse
                $bad=Clone $manifest;$bad.output_directory=$copyRoot;$dir=Join-Path $copyRoot $first.id
                switch($kind) {
                    'raw' {[IO.File]::AppendAllText((Join-Path $dir 'instrumentation.log'),'changed')}
                    'completed' {$c=Get-Content -Raw (Join-Path $dir 'completed.json')|ConvertFrom-Json -AsHashtable;$c.status='started';Save-Json (Join-Path $dir 'completed.json') $c}
                    'analysis' {[IO.File]::AppendAllText((Join-Path $dir 'analysis.json'),' ')}
                    'restoration' {[IO.File]::AppendAllText((Join-Path $dir 'restoration.json'),' ')}
                    'worktree' {[IO.File]::AppendAllText((Join-Path $dir 'worktree-after.json'),' ')}
                    'invalid' {Save-Json (Join-Path $dir 'invalid.json') @{status='invalid'}}
                    'foreign-preflight' {$c=Get-Content -Raw (Join-Path $dir 'completed.json')|ConvertFrom-Json -AsHashtable;$c.preflight_sha256='f'*64;Save-Json (Join-Path $dir 'completed.json') $c}
                    'raw-name' {
                        $a=Get-Content -Raw (Join-Path $dir 'analysis.json')|ConvertFrom-Json -AsHashtable;$a.raw_inputs[0].relative_path='foreign.log';Save-Json (Join-Path $dir 'analysis.json') $a
                        $c=Get-Content -Raw (Join-Path $dir 'completed.json')|ConvertFrom-Json -AsHashtable;$c.raw_inputs=$a.raw_inputs;$c.analysis_sha256=Get-FileSha256 (Join-Path $dir 'analysis.json');Save-Json (Join-Path $dir 'completed.json') $c
                    }
                }
                Refuses {Read-P4LayerCompletedAnalysis $bad $first $hash}
            }
        }
    }
    if($CaseGroup -cin @('Chain','FrameChain')) {
        $ast=Read-Ast (Join-Path $PSScriptRoot 'validate-p4-layer-frame-analysis.ps1')
        . ([scriptblock]::Create((Function-Text $ast 'New-LayerFrameFixture')))
        $protocol=$phase;$experimentId=$manifest.experiment_id;$preflightHash=$hash;$build='c'*40;$apk='d'*64
        $candidateProduction=$manifest.roles.candidate.production_commit;$boundsText='[100,100][868,868]';$primaryRoot=$manifest.frame_experiment.directory
        [void][IO.Directory]::CreateDirectory($primaryRoot)
        Save-Json (Join-Path $primaryRoot 'experiment.json') (Get-P4LayerFrameExperimentContract $experimentId $hash)
        $experimentHash=Get-FileSha256 (Join-Path $primaryRoot 'experiment.json')
        foreach($slot in @($slots|Where-Object {$_.lane -ceq 'frame' -and $_.runner -ceq 'decision' -and $_.role -ceq 'baseline' -and ($CaseGroup -ceq 'Chain' -or $_.group_id -cne 'single')})) {
            Case "completed baseline and candidate binding $($slot.group_id)" {
                $contract=Get-P4FrameExecutionContract $phase $slot.id;$frameDir=New-LayerFrameFixture $contract
                $context=Get-P4LayerFramePhaseContext $manifest $slot $hash;$bounds=[ordered]@{}
                foreach($spec in $contract.workload_catalog) {$bounds[$spec.workload]=$boundsText}
                $analysis=Test-P4FrameCapture -SlotDirectory $frameDir -Role $slot.role -Runner $slot.runner -SequenceIndex $slot.run `
                    -BuildCommit $build -ExpectedApkSha256 $apk -ExpectedBounds $bounds -ExperimentId $experimentId -PhaseContext $context
                $dir=Join-Path $manifest.output_directory $slot.id;[void][IO.Directory]::CreateDirectory($dir)
                New-P4FrameSlotRecord @{output_directory=$dir} $frameDir $context|Out-Null
                New-StagingFixture $slot (Get-P4DeviceLanePlan $manifest $slot $hash) (Join-Path $dir 'fixture-preparation')|Out-Null
                Complete-Fixture $slot $analysis $dir
                $candidate=@($slots|Where-Object {$_.lane -ceq 'frame' -and $_.group_id -ceq $slot.group_id -and $_.role -ceq 'candidate' -and $_.runner -ceq 'decision'})[0]
                $parameters=Get-P4FrameCollectorParameters $manifest $candidate $hash
                Check ($parameters.PhaseContext.baseline_reference.analysis_sha256 -ceq (Get-FileSha256 (Join-Path $dir 'analysis.json'))) 'Wrong baseline analysis'
                Check ($parameters.BaselineProductionCommit -ceq $manifest.roles[$slot.artifact_role].production_commit) 'Wrong baseline production'
            }
        }
        $candidate=@($slots|Where-Object {$_.lane -ceq 'frame' -and $_.group_id -ceq 'underlay' -and $_.role -ceq 'candidate' -and $_.runner -ceq 'decision'})[0]
        Case 'candidate refuses missing predecessor' {
            $bad=Clone $manifest;$bad.output_directory=Join-Path $OutputDirectory 'absent';Refuses {Get-P4LayerFramePhaseContext $bad $candidate $hash}
        }
        Case 'candidate refuses foreign group predecessor' {
            $bad=Clone $manifest;$bad.roles.baseline_underlay.build_commit='f'*40;Refuses {Get-P4LayerFramePhaseContext $bad $candidate $hash}
        }
    }
    if($CaseGroup -ceq 'Native') {
        $script:events=[Collections.Generic.List[string]]::new();$script:mode=''
        function Assert-P4RemotePackagesStopped {param($Context,$Packages,$Stage);$script:events.Add('stopped')}
        function Assert-P4InstalledApk {param($Context,$Manifest,$Role,$Kind);$script:events.Add("install:$Kind")}
        function Set-P4Dexopt {param($Context,$Package,$Mode);throw 'Frame staging must not dexopt'}
        function Assert-P4PrivateFileAbsent {param($Context,$Package,$RelativePath,$Stage);$script:events.Add('absent')}
        function Invoke-P4Instrumentation {param($Context,$AdbArguments,$TimeoutSeconds,$ExpectedTestCount)
            $script:events.Add("instrument:$TimeoutSeconds")
            if($script:mode -ceq 'instrument-failure') {throw 'synthetic instrumentation failure'}
        }
        function Copy-P4PrivateFile {throw 'No live private copy'}
        $single=@($slots|Where-Object {$_.lane -ceq 'frame' -and $_.group_id -ceq 'single'})[0]
        $layer=@($slots|Where-Object {$_.lane -ceq 'frame' -and $_.group_id -ceq 'layers16'})[0]
        foreach($slot in @($single,$layer)) {Case "actual staging instrumentation dispatch $($slot.group_id)" {
            $script:events.Clear();$plan=Get-P4DeviceLanePlan $manifest $slot $hash
            Invoke-P4InstrumentationLane @{output_directory=$OutputDirectory} $manifest $plan|Out-Null
            $expected=if($slot.group_id -ceq 'single') {'stopped|install:app_debug'} else {'stopped|install:app_debug|install:test_debug|absent|instrument:300'}
            Check (($script:events -join '|') -ceq $expected) 'Wrong staging call order/bound'
        }}
        # Retain valid raw reports once; the tested orchestration copies them only after stop attempts.
        $plan=Get-P4DeviceLanePlan $manifest $layer $hash
        $source=Join-Path $OutputDirectory 'native-source';New-StagingFixture $layer $plan $source|Out-Null
        function Invoke-P4InstrumentationLane {param($Context,$Manifest,$Plan)
            $script:events.Add('instrument')
            Copy-Item -LiteralPath (Join-Path $source 'instrumentation.log') -Destination (Join-Path $Context.output_directory 'instrumentation.log')
            if($script:mode -ceq 'instrument-failure') {throw 'synthetic instrumentation failure'}
        }
        function Invoke-P4BoundedAdb {param($Context,$LogName,$AdbArguments,$TimeoutSeconds)
            Check ($TimeoutSeconds -eq 30 -and ($AdbArguments[0..2] -join ',') -ceq 'shell,am,force-stop') 'Wrong stop call'
            $script:events.Add("stop:$($AdbArguments[3])")
            return @{ExitCode=$(if($script:mode -ceq 'stop-failure') {1} else {0})}
        }
        function Copy-P4LayerPrivateReports {param($Context,$Plan)
            $script:events.Add('capture')
            if($script:mode -ceq 'capture-failure') {throw 'synthetic capture failure'}
            Copy-Item -LiteralPath (Join-Path $source 'fixture.txt') -Destination (Join-Path $Context.output_directory 'fixture.txt')
            if($script:mode -ceq 'identity-failure') {[IO.File]::AppendAllText((Join-Path $Context.output_directory 'fixture.txt'),'bad')}
        }
        foreach($mode in @('success','instrument-failure','stop-failure','capture-failure','identity-failure')) {
            Case "staging retained outcome $mode" {
                $script:mode=$mode;$script:events.Clear();$dir=Join-Path $OutputDirectory "native-$mode";[void][IO.Directory]::CreateDirectory($dir)
                $context=@{output_directory=$dir}
                if($mode -ceq 'success') {$actual=Invoke-P4LayerFrameStaging $context $manifest $plan;Check ($null -ne $actual.fixture_evidence) 'Missing verified identity'}
                else {Refuses {Invoke-P4LayerFrameStaging $context $manifest $plan}}
                Check ($script:events.Count -eq 5 -and $script:events[0] -ceq 'instrument' -and $script:events[-1] -ceq 'capture') 'Failure skipped stop/capture'
                Check (@($script:events|Where-Object {$_ -clike 'stop:*'}).Count -eq 3) 'Not all writers stopped'
                $record=Get-Content -Raw (Join-Path $dir 'fixture-preparation/staging-result.json')|ConvertFrom-Json -AsHashtable
                Check ($record.status -ceq $(if($mode -ceq 'success') {'success'}else{'failure'})) 'Wrong retained outcome'
                if($mode -cne 'success') {Check ($record.errors.Count -gt 0 -and $null -eq $record.fixture_evidence) 'Failure accepted fixture'}
            }
        }
        Case 'staging collision starts no native work' {
            $script:events.Clear();Refuses {Invoke-P4LayerFrameStaging @{output_directory=(Join-Path $OutputDirectory 'native-success')} $manifest $plan}
            Check ($script:events.Count -eq 0) 'Collision reached native boundary'
        }
    }
    if($CaseGroup -ceq 'Setup') {
        foreach($slot in @($slots|Where-Object {$_.lane -ceq 'frame' -and $_.role -ceq 'baseline' -and $_.runner -ceq 'decision'})) {
            Case "retained setup and actual PNG replay $($slot.group_id)" {
                $contract=Get-P4FrameExecutionContract $phase $slot.id;$context=Get-P4LayerFramePhaseContext $manifest $slot $hash
                $directory=Join-Path $OutputDirectory $slot.id;New-SetupFixture $slot $directory $context
                $bounds=[ordered]@{};foreach($spec in $contract.workload_catalog) {$bounds[$spec.workload]='[100,100][868,868]'}
                Assert-P4LayerFrameSetupEvidence $directory $context $bounds
            }
        }
        $slot=@($slots|Where-Object {$_.lane -ceq 'frame' -and $_.group_id -ceq 'underlay' -and $_.role -ceq 'baseline' -and $_.runner -ceq 'decision'})[0]
        $context=Get-P4LayerFramePhaseContext $manifest $slot $hash;$directory=Join-Path $OutputDirectory $slot.id
        $bounds=@{canvas256_underlay_repeated_diagonal='[100,100][868,868]'}
        foreach($kind in @('failed-status','foreign-context','exceeded-budget','negative-time','reordered-time','png-hash','pixel-value','foreign-png-path')) {
            Case "setup refuses $kind" {
                $pixel=$kind -cin @('png-hash','pixel-value','foreign-png-path')
                $name=if($pixel) {'canvas256_underlay_repeated_diagonal-pixels.json'} elseif($kind -ceq 'reordered-time') {'after-warmups-canvas256_underlay_repeated_diagonal.json'} else {'initial.json'}
                $path=Join-Path $directory "phase-setup/$name";$original=[IO.File]::ReadAllText($path);$bad=$original|ConvertFrom-Json -AsHashtable
                switch($kind) {
                    'failed-status' {$bad.status='failure'}
                    'foreign-context' {$bad.phase_context.slot_id='foreign-slot'}
                    'exceeded-budget' {$bad.cumulative_seconds=301}
                    'negative-time' {$bad.cumulative_seconds=-1}
                    'reordered-time' {$bad.cumulative_seconds=0}
                    'png-hash' {$bad.sha256.preview='f'*64}
                    'pixel-value' {$bad.pixels[0].preview[0]=1}
                    'foreign-png-path' {$bad.paths.preview=$bad.paths.before}
                }
                Save-Json (Join-Path $OutputDirectory "retained-$kind.json") $bad
                Save-Json $path $bad
                try {Refuses {Assert-P4LayerFrameSetupEvidence $directory $context $bounds}} finally {[IO.File]::WriteAllText($path,$original)}
            }
        }
    }
    if($CaseGroup -ceq 'Dispatch') {
        . (Join-Path $PSScriptRoot 'measurements/collect-p4-indexed-slot.ps1') -ManifestPath unused -SlotId unused -OutputDirectory $OutputDirectory
        . (Join-Path $PSScriptRoot 'measurements/analyze-p4-indexed-slot.ps1') -ManifestPath unused -SlotId unused -OutputDirectory $OutputDirectory
        # Only full physical admission is mocked here; Maps checks that actual admission stays closed.
        function Assert-P4ManifestContract {param($Manifest)}
        $script:events=[Collections.Generic.List[object]]::new()
        function Invoke-P4InstrumentationLane {param($Context,$Manifest,$Plan);$script:events.Add(@{lane=$Plan.lane;role=$Plan.role;slot=$Plan.slot_id;output=$Context.output_directory})}
        function Invoke-P4LayerFrameCollector {param($Context,$Manifest,$Slot,$Plan,$ManifestSha256);$script:events.Add(@{lane='frame';role=$Plan.role;slot=$Slot.id;output=$Context.output_directory;hash=$ManifestSha256})}
        $manifest.tools=@{adb=@{path='C:/synthetic/adb.exe'}};$manifest.toolchain=@{jdk='C:/synthetic/jdk';android_sdk='C:/synthetic/sdk'}
        $manifestPath=Join-Path $OutputDirectory 'manifest.json';Save-Json $manifestPath $manifest;$manifestHash=Get-FileSha256 $manifestPath
        foreach($lane in @('memory','publication','saf-save','frame')) {
            $slot=@($slots|Where-Object {$_.lane -ceq $lane})[0]
            $dir=Join-Path $manifest.output_directory $slot.id;[void][IO.Directory]::CreateDirectory($dir)
            $plan=Get-P4DeviceLanePlan $manifest $slot $manifestHash
            $started=@{slot_id=$slot.id;status='started';attempt=1;preflight_sha256=$manifestHash;
                protocol_timeout_seconds=$slot.timeout_seconds;collector_timeout_seconds=$plan.collector_timeout_seconds;collector_budget=$plan.collector_budget}
            Save-Json (Join-Path $dir 'started.json') $started
            Case "collector entry dispatch $lane" {
                $script:events.Clear();Invoke-P4SlotCollector $manifestPath $slot.id $dir
                Check ($script:events.Count -eq 1 -and $script:events[0].lane -ceq $lane -and $script:events[0].role -ceq $slot.artifact_role) 'Entry chose wrong collector/source'
            }
        }
        foreach($field in @('preflight_sha256','collector_timeout_seconds','status')) {Case "collector refuses reserved $field" {
            $bad=Clone $started;$bad[$field]='foreign';Save-Json (Join-Path $dir 'started.json') $bad
            $script:events.Clear();Refuses {Invoke-P4SlotCollector $manifestPath $slot.id $dir};Check ($script:events.Count -eq 0) 'Bad reservation reached collector'
        }}
        foreach($lane in @('publication','saf-save')) {
            $slot=@($slots|Where-Object {$_.lane -ceq $lane})[0];$dir=Join-Path $manifest.output_directory $slot.id
            $context=(Get-P4DeviceLanePlan $manifest $slot $manifestHash).phase_context
            if($lane -ceq 'publication') {
                $ast=Read-Ast (Join-Path $PSScriptRoot 'validate-p4-publication-evidence.ps1')
                . ([scriptblock]::Create((Function-Text $ast 'New-P4SyntheticPublicationCapture')))
                $ast=Read-Ast (Join-Path $PSScriptRoot 'validate-p4-layer-publication-evidence.ps1')
                foreach($name in @('New-Lines','New-Identity','New-Instrumentation')) {. ([scriptblock]::Create((Function-Text $ast $name)))}
                $data=@{'publication.csv'=@(New-Lines);'publication.status'=@('complete');'identity.txt'=@(New-Identity);'instrumentation.log'=@(New-Instrumentation)}
            } else {
                $ast=Read-Ast (Join-Path $PSScriptRoot 'validate-p4-layer-saf-evidence.ps1')
                foreach($name in @('Copy-Record','New-Identity','New-Destination','New-Instrumentation','New-Dataset')) {. ([scriptblock]::Create((Function-Text $ast $name)))}
                $dataset=New-Dataset;$data=@{'save.csv'=$dataset.lines;'save.status'=$dataset.status;'identity.txt'=$dataset.identity;'setup.csv'=$dataset.setup;'instrumentation.log'=$dataset.instrumentation}
            }
            foreach($name in $data.Keys) {[IO.File]::WriteAllLines((Join-Path $dir $name),[string[]]@($data[$name]|ForEach-Object {$_.Replace(('a'*12),$manifestHash.Substring(0,12))}))}
            Seal-Fixture $slot $dir|Out-Null
            Case "actual analyzer entry and parser $lane" {
                Invoke-P4SlotAnalysis $manifestPath $slot.id $dir
                $analysis=Get-Content -Raw (Join-Path $dir 'analysis.json')|ConvertFrom-Json -AsHashtable
                Check ($analysis.protocol_id -ceq $phase -and $analysis.slot_id -ceq $slot.id -and $analysis.preflight_sha256 -ceq $manifestHash) 'Wrong analysis identity'
                Check ($analysis.raw_inputs.Count -eq $data.Count -and $analysis.phase_context.preflight_sha256 -ceq $manifestHash) 'Missing input/context binding'
            }
            Case "analysis refuses foreign baseline $lane" {Refuses {Invoke-P4LayerSlotAnalysis $manifest $slot $manifestHash $dir 'foreign.json'}}
        }
    }
    if($CaseGroup -ceq 'FrameAnalysis') {
        $ast=Read-Ast (Join-Path $PSScriptRoot 'validate-p4-layer-frame-analysis.ps1')
        . ([scriptblock]::Create((Function-Text $ast 'New-LayerFrameFixture')))
        $protocol=$phase;$experimentId=$manifest.experiment_id;$preflightHash=$hash;$build='c'*40;$apk='d'*64
        $candidateProduction=$manifest.roles.candidate.production_commit;$boundsText='[100,100][868,868]';$primaryRoot=$manifest.frame_experiment.directory
        [void][IO.Directory]::CreateDirectory($primaryRoot)
        Save-Json (Join-Path $primaryRoot 'experiment.json') (Get-P4LayerFrameExperimentContract $experimentId $hash)
        $experimentHash=Get-FileSha256 (Join-Path $primaryRoot 'experiment.json')
        $selected=@($slots|Where-Object {$_.lane -ceq 'frame' -and
            (($_.group_id -ceq 'layers16' -and $_.runner -ceq 'decision') -or
             ($_.group_id -ceq 'single' -and $_.role -ceq 'baseline' -and $_.runner -ceq 'decision') -or
             ($_.group_id -ceq 'underlay' -and $_.role -ceq 'baseline' -and $_.runner -ceq 'diagnostic'))})
        foreach($slot in $selected) {Case "complete frame analysis routing $($slot.id)" {
            $contract=Get-P4FrameExecutionContract $phase $slot.id;$frameDir=New-LayerFrameFixture $contract
            $context=Get-P4LayerFramePhaseContext $manifest $slot $hash;New-SetupFixture $slot $frameDir $context
            $dir=Join-Path $manifest.output_directory $slot.id;[void][IO.Directory]::CreateDirectory($dir)
            New-P4FrameSlotRecord @{output_directory=$dir} $frameDir $context|Out-Null
            New-StagingFixture $slot (Get-P4DeviceLanePlan $manifest $slot $hash) (Join-Path $dir 'fixture-preparation')|Out-Null
            $analysis=Invoke-P4LayerSlotAnalysis $manifest $slot $hash $dir ''
            Check ($analysis.slot_id -ceq $slot.id -and $analysis.complete_run) 'Incomplete/wrong frame result'
            Complete-Fixture $slot $analysis $dir
            $read=Read-P4LayerCompletedAnalysis $manifest $slot $hash
            Check ($read.analysis.raw_inputs.Count -gt $read.analysis.frame_files.Count) 'Frame and staging raw bindings absent'
        }}
        $slot=@($selected|Where-Object {$_.group_id -ceq 'layers16' -and $_.role -ceq 'candidate'})[0]
        $dir=Join-Path $manifest.output_directory $slot.id
        Case 'frame analysis refuses foreign group baseline path' {Refuses {Invoke-P4LayerFrameAnalysis $manifest $slot $hash $dir 'foreign/analysis.json'}}
        foreach($kind in @('role','context','directory','inventory')) {Case "frame record refuses $kind" {
            $path=Join-Path $dir 'frame-slot.json';$original=[IO.File]::ReadAllText($path);$bad=$original|ConvertFrom-Json -AsHashtable
            switch($kind) {
                'role' {$bad.artifact_role='baseline_layers16'}
                'context' {$bad.phase_context.preflight_sha256='f'*64}
                'directory' {$bad.slot_directory=Join-Path $manifest.frame_experiment.directory 'other'}
                'inventory' {$bad.files[0].byte_count++}
            }
            Save-Json (Join-Path $OutputDirectory "bad-frame-$kind.json") $bad;Save-Json $path $bad
            try {Refuses {Invoke-P4LayerFrameAnalysis $manifest $slot $hash $dir ''}} finally {[IO.File]::WriteAllText($path,$original)}
        }}
    }
    if($CaseGroup -cin @('Compatibility','CompatibilityRemaining')) {
        $old=[ordered]@{};$current=[ordered]@{}
        foreach($name in @('p4-indexed-device-lanes.ps1','measure-m2-frame.ps1','analyze-p4-indexed-slot.ps1')) {
            $text=(git -C $repository show "6f2b491:docs/quality/measurements/$name") -join "`n"
            Check ($LASTEXITCODE -eq 0) 'Pinned previous source unavailable'
            $path=Join-Path $OutputDirectory "previous-$name";[IO.File]::WriteAllText($path,$text)
            $old[$name]=Read-Ast $path;$current[$name]=Read-Ast (Join-Path $PSScriptRoot "measurements/$name")
        }
        $shared=Read-Ast (Join-Path $PSScriptRoot 'measurements/p4-indexed-frame-analysis.ps1')
        foreach($name in @(@('Get-Bounds','Get-InitialFitGeometry')|Where-Object {$CaseGroup -ceq 'Compatibility'})) {Case "canonical shared geometry unchanged $name" {
            Check ((Function-Text $old['measure-m2-frame.ps1'] $name).Replace("`r`n","`n") -ceq
                (Function-Text $shared $name).Replace("`r`n","`n")) 'Moved function changed'
            Check ($null -eq $current['measure-m2-frame.ps1'].Find({param($n)$n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $name},$true)) 'Second geometry implementation remains'
        }}
        if($CaseGroup -ceq 'Compatibility') {Case 'legacy analysis switch body unchanged' {
            $before=[Management.Automation.Language.Parser]::ParseInput((Function-Text $old['analyze-p4-indexed-slot.ps1'] 'Invoke-P4SlotAnalysis'),[ref]$null,[ref]$null)
            $after=[Management.Automation.Language.Parser]::ParseInput((Function-Text $current['analyze-p4-indexed-slot.ps1'] 'Invoke-P4SlotAnalysis'),[ref]$null,[ref]$null)
            $select={param($n)$n -is [Management.Automation.Language.SwitchStatementAst] -and $n.Condition.Extent.Text -ceq '$slot.lane'}
            Check ($before.Find($select,$true).Extent.Text.Replace("`r`n","`n") -ceq $after.Find($select,$true).Extent.Text.Replace("`r`n","`n")) 'Legacy parser routing changed'
        }}
        foreach($variable in @('warmupIndex','sampleIndex')) {Case "timed population loops unchanged $variable" {
            $select={param($n)$n -is [Management.Automation.Language.ForEachStatementAst] -and $n.Variable.VariablePath.UserPath -ceq $variable}
            $before=@($old['measure-m2-frame.ps1'].FindAll($select,$true));$after=@($current['measure-m2-frame.ps1'].FindAll($select,$true))
            Check ($before.Count -gt 0 -and $before.Count -eq $after.Count) 'Loop count changed'
            foreach($i in 0..($before.Count-1)) {Check ($before[$i].Extent.Text.Replace("`r`n","`n") -ceq $after[$i].Extent.Text.Replace("`r`n","`n")) 'Timed loop changed'}
        }}
        $ast=Read-Ast (Join-Path $PSScriptRoot 'validate-p4-frame-analysis.ps1')
        . ([scriptblock]::Create((Function-Text $ast 'New-P4LaneManifest')))
        $temporaryRoot=$OutputDirectory;$baselineCommit='1'*40;$candidateCommit='2'*40;$baselineApk='3'*64;$candidateApk='4'*64
        $baselineProduction='5'*40;$candidateProduction='6'*40;$applicationPackage='io.github.hideyukimori.nenepixel'
        $applicationTestPackage="$applicationPackage.test";$publicationTestPackage="$applicationPackage.adapters.persistence.test"
        $fixtureExperimentId='legacy-plan-contract';$canvas16Bounds='[100,200][300,400]';$canvas256Bounds='[80,180][320,420]'
        $legacyManifest=New-P4LaneManifest;$legacySlots=@(Get-P4FrameSlotCatalog)
        $module=New-Module -ScriptBlock ([scriptblock]::Create((Function-Text $old['p4-indexed-device-lanes.ps1'] 'Get-P4FrameCollectorParameters')))
        $expected=@(& $module {param($m,$s)foreach($slot in $s) {Get-P4FrameCollectorParameters $m $slot}} $legacyManifest $legacySlots)
        . (Join-Path $PSScriptRoot 'measurements/p4-indexed-device-lanes.ps1')
        foreach($i in 0..3) {Case "legacy frame parameters $($legacySlots[$i].id)" {
            $actual=Get-P4FrameCollectorParameters $legacyManifest $legacySlots[$i]
            Check (($actual|ConvertTo-Json -Depth 20 -Compress) -ceq ($expected[$i]|ConvertTo-Json -Depth 20 -Compress)) 'Legacy parameters drifted'
        }}
    }
    if($CaseGroup -ceq 'Collector') {
        $script:events=[Collections.Generic.List[string]]::new()
        function Invoke-P4InstrumentationLane {param($Context,$Manifest,$Plan)
            $script:events.Add('staging');if($stagingFails) {throw 'synthetic staging failure'}
        }
        function Get-Command {param($Name,$CommandType);Check ($Name -ceq 'adb') 'Unexpected tool resolution';return [pscustomobject]@{Source=$manifest.tools.adb.path}}
        $slot=@($slots|Where-Object {$_.lane -ceq 'frame' -and $_.group_id -ceq 'single' -and $_.role -ceq 'baseline' -and $_.runner -ceq 'decision'})[0]
        $parameters=Get-P4FrameCollectorParameters $manifest $slot $hash
        $fake=Join-Path $OutputDirectory 'host-collector.ps1'
        $body='param('+((@($parameters.Keys)|ForEach-Object {"`$$_"}) -join ',')+")`n"+@'
[void][IO.Directory]::CreateDirectory($frameDirectory)
[IO.File]::WriteAllText((Join-Path $frameDirectory 'partial.txt'),$SourceCommit)
if($collectorFails) {throw 'synthetic collector failure after partial output'}
'@
        [IO.File]::WriteAllText($fake,$body)
        $manifest.tools=@{adb=@{path=(Join-Path $OutputDirectory 'adb.exe')};frame_collector=@{path=$fake}}
        foreach($mode in @('success','collector-failure','staging-failure')) {Case "actual frame collector orchestration $mode" {
            $collectorFails=$mode -ceq 'collector-failure';$stagingFails=$mode -ceq 'staging-failure'
            $script:events.Clear();$dir=Join-Path $OutputDirectory $mode;[void][IO.Directory]::CreateDirectory($dir)
            $manifest.frame_experiment.directory=Join-Path $dir 'frames'
            $context=@{repository_root=$repository;output_directory=$dir};$plan=Get-P4DeviceLanePlan $manifest $slot $hash
            $beforePath=$env:PATH;$beforeLocation=(Get-Location).Path
            if($mode -ceq 'success') {Invoke-P4LayerFrameCollector $context $manifest $slot $plan $hash}
            else {Refuses {Invoke-P4LayerFrameCollector $context $manifest $slot $plan $hash}}
            Check ($env:PATH -ceq $beforePath -and (Get-Location).Path -ceq $beforeLocation) 'Collector leaked host environment'
            Check (($script:events -join '|') -ceq 'staging') 'Staging count changed'
            if($mode -ceq 'staging-failure') {Check (-not (Test-Path (Join-Path $dir 'frame-slot.json'))) 'Failed staging reached frame collector'}
            else {
                $record=Get-Content -Raw (Join-Path $dir 'frame-slot.json')|ConvertFrom-Json -AsHashtable
                Check ($record.schema -ceq 'nene-pixel-p4-frame-slot-v2' -and $record.slot_id -ceq $slot.id -and $record.files.Count -eq 1) 'Partial frame record lost'
                if($mode -ceq 'collector-failure') {Check (Test-Path (Join-Path $dir 'frame-collector-failure.txt')) 'Missing collector error'}
            }
        }}
        Case 'release install delegates exact role and retained intent boundary' {
            $ast=Read-Ast (Join-Path $PSScriptRoot 'measurements/p4-layer-slot-routing.ps1')
            $text=Function-Text $ast 'Install-P4LayerFrameReleaseArtifact'
            Check ($text.Contains('Assert-P4InstalledApk -Context $context -Manifest $source -Role $Role -Kind ''app_release_like''') -and
                $text.Contains('protocol = @{ id = ''nene-pixel-p4-layer-phase-verification-v1'' }')) 'Release install bypasses phase intent helper'
        }
    }
    $result.status='pass'
} finally {
    $watch.Stop();$result.elapsed_seconds=$watch.Elapsed.TotalSeconds;$result.cases=$script:cases.ToArray()
    foreach($name in @('p4-layer-slot-routing.ps1','p4-indexed-device-lanes.ps1','p4-indexed-frame-analysis.ps1','p4-indexed-preflight.ps1','collect-p4-indexed-slot.ps1','analyze-p4-indexed-slot.ps1','measure-m2-frame.ps1')) {
        $result.sources[$name]=Get-FileSha256 (Join-Path $PSScriptRoot "measurements/$name")
    }
    $result.sources.validator=Get-FileSha256 $PSCommandPath
    Save-Json (Join-Path $OutputDirectory 'results.json') $result
}
"$($result.status): $($script:cases.Count) focused host checks; $($watch.Elapsed.TotalSeconds) s"

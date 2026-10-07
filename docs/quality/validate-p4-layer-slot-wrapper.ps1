# Focused host-only checks of the slot wrapper's phase binding (Issue #145 R4, R7 point 2): the 18-slot
# phase catalog, the four artifact roles, the planner's context and budget, and the phase analysis
# identity, next to the unchanged v7 route. No ADB, Gradle, or device.
param([Parameter(Mandatory)][string]$OutputDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
$lab = [IO.Path]::GetFullPath((Get-NenePixelLabPath 'evidence' -StartDirectory $PSScriptRoot)).TrimEnd('\', '/')
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $OutputDirectory.StartsWith($lab + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    (Test-Path -LiteralPath $OutputDirectory)) { throw 'Fresh lab output required.' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
# The wrapper is dot-sourced without running its entry point (InvocationName '.').
$invokePath = Join-Path $PSScriptRoot 'measurements/invoke-p4-indexed-slot.ps1'
. $invokePath -ManifestPath fixture -SlotId fixture
# The capture seal is outside this check: its own validators cover the seal reading.
function Read-P4VerifiedCaptureSeal { param($SlotDirectory, $SlotId, $ExpectedSha256) }

$script:cases = [Collections.Generic.List[object]]::new()
$result = [ordered]@{ status = 'failure'; cases = @() }
function Check([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Case([string]$Group, [string]$CaseName, [scriptblock]$Action) {
    $errorText = $null; try { & $Action | Out-Null } catch { $errorText = $_.ToString() }
    $script:cases.Add([ordered]@{ group = $Group; name = $CaseName; passed = ($null -eq $errorText); error = $errorText })
    Check ($null -eq $errorText) "Case failed: $Group / $CaseName : $errorText"
}
function Refused([scriptblock]$Action, [string]$Pattern) {
    try { & $Action | Out-Null } catch { Check ("$_" -match $Pattern) "Refused for another reason: $_"; return }
    throw "Expected a refusal matching '$Pattern'."
}
function Save-Json([string]$Path, $Value) {
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 50), [Text.UTF8Encoding]::new($false))
}
function Json($Value) { return ($Value | ConvertTo-Json -Depth 20 -Compress) }

$phase = 'nene-pixel-p4-layer-phase-verification-v1'
$v7 = 'nene-pixel-p4-indexed-cutover-verification-v7'
$hash = 'a' * 64
$seal = 'b' * 64
$app = 'io.github.hideyukimori.nenepixel'
$expectedRoles = [ordered]@{
    'frame-1-single-baseline-decision' = 'baseline_single'; 'frame-2-single-candidate-decision' = 'candidate'
    'frame-3-layers16-baseline-decision' = 'baseline_layers16'; 'frame-4-layers16-candidate-decision' = 'candidate'
    'frame-5-underlay-baseline-decision' = 'baseline_underlay'; 'frame-6-underlay-candidate-decision' = 'candidate' }
foreach ($run in 1..5) { $expectedRoles["memory-layers16-baseline-$run"] = 'baseline_layers16' }
foreach ($run in 1..5) { $expectedRoles["memory-layers16-candidate-$run"] = 'candidate' }
$expectedRoles['publication-layers16-candidate'] = 'candidate'
$expectedRoles['saf-save-layers16-candidate'] = 'candidate'

function New-PhaseManifest {
    $kinds = @{
        app_debug = [ordered]@{ target_package = $app; test_package = 'none' }
        test_debug = [ordered]@{ target_package = $app; test_package = "$app.test" }
        app_release_like = [ordered]@{ target_package = $app; test_package = 'none' }
        publication_test = [ordered]@{ target_package = "$app.publication"; test_package = "$app.publication" } }
    $roles = [ordered]@{}
    $digit = 0
    foreach ($entry in @(Get-P4LayerArtifactCatalog $phase)) {
        $digit++
        $artifacts = [ordered]@{}
        foreach ($kind in $entry.artifact_kinds) {
            $artifacts[$kind] = [ordered]@{ target_package = $kinds[$kind].target_package
                test_package = $kinds[$kind].test_package; sha256 = ([string]$digit) * 64 }
        }
        $roles[$entry.role] = [ordered]@{ worktree = "D:/NENE-PIXEL/clones/t5b-$($entry.role)"
            production_commit = $entry.production_commit; build_commit = ([string]$digit) * 40; artifacts = $artifacts }
    }
    return [ordered]@{ protocol = [ordered]@{ id = $phase }; experiment_id = 'e145-t5b'
        output_directory = (Join-Path $OutputDirectory 'phase-root'); roles = $roles
        tools = [ordered]@{ adb = [ordered]@{ path = 'D:/NENE-PIXEL/tools/adb.exe' } }
        device = [ordered]@{ serial = 'T5BSERIAL'; asset_preservation = [ordered]@{ sha256 = 'c' * 64; session = 's145-t5b' } } }
}
function New-V7Manifest {
    return [ordered]@{ protocol = [ordered]@{ id = $v7 }
        roles = [ordered]@{ baseline = [ordered]@{ worktree = 'D:/NENE-PIXEL/clones/t5b-v7-baseline'; build_commit = '7' * 40 }
            candidate = [ordered]@{ worktree = 'D:/NENE-PIXEL/clones/t5b-v7-candidate'; build_commit = '8' * 40 } }
        tools = [ordered]@{ adb = [ordered]@{ path = 'D:/NENE-PIXEL/tools/adb.exe' } }
        device = [ordered]@{ serial = 'T5BSERIAL' } }
}
function Get-PhaseSlot([string]$Id) { return @(Get-P4SlotCatalog $phase | Where-Object { $_.id -ceq $Id })[0] }
function New-PhaseAnalysis($Slot, [string]$ArtifactRole) {
    $analysis = [ordered]@{ schema = 'synthetic'; protocol_id = $phase; slot_id = $Slot.id; preflight_sha256 = $hash
        role = $Slot.role; created_utc = [datetime]::UtcNow.AddSeconds(5).ToString('o'); capture_seal_sha256 = $seal
        verdict = switch ($Slot.lane) { 'frame' { if ($Slot.role -ceq 'baseline') { 'baseline-recorded' } else { 'pass' } }
            'publication' { 'valid-constants-retained' }; 'saf-save' { 'valid-descriptive' }; default { 'pass' } } }
    if ($Slot.lane -ceq 'frame') {
        $analysis.artifact_role = $ArtifactRole
        $analysis.gross_regression = $false
        $analysis.gross_regression_basis = [ordered]@{ families = @() }
    } else {
        $analysis.phase_context = [ordered]@{ protocol_id = $phase; slot_id = $Slot.id; preflight_sha256 = $hash
            artifact_role = $ArtifactRole }
    }
    return $analysis
}
function Read-Analysis([string]$Name, $Slot, $Analysis) {
    $path = Join-Path $OutputDirectory "$Name.json"
    $started = [datetime]::UtcNow
    Save-Json $path $Analysis
    return Read-P4FreshAnalysis $path $Slot $hash $started $seal
}
function Complete-PhaseSlot([string]$Root, $Slot, $Analysis) {
    $directory = Join-Path $Root $Slot.id
    [void][IO.Directory]::CreateDirectory($directory)
    Save-Json (Join-Path $directory 'analysis.json') $Analysis
    Save-Json (Join-Path $directory 'restoration.json') @{ restored = $true }
    Save-Json (Join-Path $directory 'worktree-after.json') @{ clean = $true }
    $completed = [ordered]@{ slot_id = $Slot.id; verdict = $Analysis.verdict; status = 'completed'; protocol_id = $phase
        preflight_sha256 = $hash; capture_seal_sha256 = $seal
        analysis_sha256 = Get-FileSha256 (Join-Path $directory 'analysis.json')
        restoration_sha256 = Get-FileSha256 (Join-Path $directory 'restoration.json')
        worktree_after_sha256 = Get-FileSha256 (Join-Path $directory 'worktree-after.json') }
    if ($Slot.lane -ceq 'frame') { $completed.complete_run = $true }
    Save-Json (Join-Path $directory 'completed.json') $completed
}

$phaseManifest = New-PhaseManifest
$v7Manifest = New-V7Manifest
try {
    # Route: the 18-slot phase catalog, the artifact role per slot, the comparison role kept apart.
    Case 'Route' 'phase catalog has 18 slots in the expected order' {
        $ids = @(Get-P4SlotCatalog $phase | ForEach-Object { $_.id })
        Check (($ids -join ',') -ceq (@($expectedRoles.Keys) -join ',')) "Catalog $($ids -join ',')"
    }
    Case 'Route' 'every phase slot selects the phase catalog and its artifact role' {
        foreach ($id in $expectedRoles.Keys) {
            $route = Get-P4IndexedSlotRoute -Manifest $phaseManifest -SlotId $id
            $role = $expectedRoles[$id]
            Check ($route.phase -and $route.protocol_id -ceq $phase -and @($route.catalog).Count -eq 18) "Route $id"
            Check ($route.slot.id -ceq $id -and $route.artifact_role -ceq $role) "Artifact role $id : $($route.artifact_role)"
            $comparison = if ($id -match '-baseline-') { 'baseline' } else { 'candidate' }
            Check ($route.comparison_role -ceq $comparison) "Comparison role $id : $($route.comparison_role)"
        }
    }
    Case 'Route' 'every phase slot reads the manifest record of its artifact role' {
        foreach ($id in $expectedRoles.Keys) {
            $source = Get-P4SlotSource $phaseManifest (Get-PhaseSlot $id)
            Check ($source.worktree -ceq $phaseManifest.roles[$expectedRoles[$id]].worktree) "Source $id : $($source.worktree)"
        }
    }
    Case 'Route' 'phase remote packages cover every artifact role' {
        $packages = @(Get-P4ManifestPackages $phaseManifest)
        Check (($packages -join ',') -ceq "$app,$app.publication,$app.test") "Packages $($packages -join ',')"
    }

    # Planner: the device context and the collector budget are the lane planner's own.
    Case 'Planner' 'device context carries the planner phase context and the role clone' {
        foreach ($id in $expectedRoles.Keys) {
            $slot = Get-PhaseSlot $id
            $context = Get-P4SlotDeviceContext -Manifest $phaseManifest -Slot $slot -Directory $OutputDirectory -ManifestSha256 $hash
            $plan = Get-P4LayerDeviceLanePlan $phaseManifest $slot $hash
            Check ((@($context.Keys) -join ',') -ceq 'repository_root,output_directory,adb_path,serial,phase_context') "Keys $id"
            Check ($context.repository_root -ceq [IO.Path]::GetFullPath($phaseManifest.roles[$expectedRoles[$id]].worktree)) "Root $id"
            Check ((Json $context.phase_context) -ceq (Json $plan.phase_context)) "Context $id"
            Check ($context.phase_context.artifact_role -ceq $expectedRoles[$id]) "Context role $id"
            $fields = if ($slot.lane -ceq 'publication') { 12 } else { 11 }
            Check (@($context.phase_context.Keys).Count -eq $fields) "Context fields $id : $(@($context.phase_context.Keys).Count)"
        }
    }
    Case 'Planner' 'collector budget is the planner budget for every phase slot' {
        foreach ($id in $expectedRoles.Keys) {
            $slot = Get-PhaseSlot $id
            $record = Get-P4SlotCollectorBudget -Manifest $phaseManifest -Slot $slot -ManifestSha256 $hash
            $plan = Get-P4LayerDeviceLanePlan $phaseManifest $slot $hash
            Check ((Json $record.budget) -ceq (Json $plan.collector_budget)) "Budget $id"
            Check ($record.plan.role -ceq $expectedRoles[$id] -and $record.plan.comparison_role -ceq $slot.role) "Plan roles $id"
        }
    }
    Case 'Planner' 'writer count follows the role (single 1, layers16 2, publication 3)' {
        $counts = foreach ($id in @('frame-1-single-baseline-decision', 'frame-3-layers16-baseline-decision',
                'publication-layers16-candidate')) {
            @((Get-P4SlotCollectorBudget $phaseManifest (Get-PhaseSlot $id) $hash).plan.quiescence_packages).Count
        }
        Check ((@($counts) -join ',') -ceq '1,2,3') "Writers $(@($counts) -join ',')"
        $single = (Get-P4SlotCollectorBudget $phaseManifest (Get-PhaseSlot 'frame-1-single-baseline-decision') $hash).budget
        $layers = (Get-P4SlotCollectorBudget $phaseManifest (Get-PhaseSlot 'frame-3-layers16-baseline-decision') $hash).budget
        Check ($single.probe_count -lt $layers.probe_count) 'Budget ignores the writer count'
    }

    # V7: the historical route is unchanged.
    Case 'V7' 'v7 frame slot keeps the four-slot frame catalog and comparison role' {
        $route = Get-P4IndexedSlotRoute -Manifest $v7Manifest -SlotId 'frame-1-baseline-decision'
        Check (-not $route.phase -and $route.protocol_id -ceq $v7 -and @($route.catalog).Count -eq 4) 'v7 frame route'
        Check ($route.artifact_role -ceq 'baseline' -and $route.comparison_role -ceq 'baseline') 'v7 frame role'
        Check ((Get-P4SlotSource $v7Manifest $route.slot).worktree -ceq $v7Manifest.roles.baseline.worktree) 'v7 frame source'
    }
    Case 'V7' 'v7 non-frame slot keeps the Issue #106 catalog' {
        $route = Get-P4IndexedSlotRoute -Manifest $v7Manifest -SlotId 'command-candidate'
        Check (@($route.catalog).Count -eq @(Get-P4SlotCatalog).Count -and $route.artifact_role -ceq 'candidate') 'v7 route'
        $noProtocol = [ordered]@{ roles = $v7Manifest.roles }
        Check ((Get-P4IndexedSlotRoute -Manifest $noProtocol -SlotId 'host-project-baseline').artifact_role -ceq 'baseline') 'Manifest without protocol'
    }
    Case 'V7' 'v7 does not reach phase slots' {
        Refused { Get-P4IndexedSlotRoute -Manifest $v7Manifest -SlotId 'memory-layers16-baseline-1' } 'Unknown slot'
        Refused { Get-P4IndexedSlotRoute -Manifest $v7Manifest -SlotId 'frame-1-single-baseline-decision' } 'Unknown slot'
    }
    Case 'V7' 'v7 device context and host budget are unchanged' {
        $slot = @(Get-P4SlotCatalog | Where-Object { $_.id -ceq 'host-project-baseline' })[0]
        $context = Get-P4SlotDeviceContext -Manifest $v7Manifest -Slot $slot -Directory $OutputDirectory
        Check ((@($context.Keys) -join ',') -ceq 'repository_root,output_directory,adb_path,serial') 'v7 context keys'
        Check ($context.repository_root -ceq [IO.Path]::GetFullPath($v7Manifest.roles.baseline.worktree)) 'v7 context root'
        $budget = (Get-P4SlotCollectorBudget -Manifest $v7Manifest -Slot $slot).budget
        Check ($budget.collector_timeout_seconds -eq 180 -and -not $budget.derived -and $budget.lane -ceq 'host') 'v7 host budget'
    }
    Case 'V7' 'v7 analysis identity is unchanged' {
        $slot = @(Get-P4FrameSlotCatalog)[0]
        $analysis = [ordered]@{ schema = 'synthetic'; protocol_id = $v7; slot_id = $slot.id; preflight_sha256 = $hash
            role = 'baseline'; verdict = 'baseline-recorded'; created_utc = [datetime]::UtcNow.AddSeconds(5).ToString('o')
            capture_seal_sha256 = $seal }
        Read-Analysis 'v7-ok' $slot $analysis | Out-Null
        $analysis.protocol_id = $phase
        Refused { Read-Analysis 'v7-phase-id' $slot $analysis } 'Analyzer identity does not match'
    }

    # Foreign: a slot, a manifest or a role of another protocol is refused before reservation.
    Case 'Foreign' 'phase slot with a v7 manifest is refused' {
        Refused { Get-P4SlotSource $v7Manifest (Get-PhaseSlot 'memory-layers16-baseline-1') } 'not to this manifest'
    }
    Case 'Foreign' 'v7 slot with a phase manifest is refused' {
        $slot = @(Get-P4SlotCatalog | Where-Object { $_.id -ceq 'command-baseline' })[0]
        Refused { Get-P4SlotSource $phaseManifest $slot } 'does not belong to the phase manifest'
    }
    Case 'Foreign' 'phase manifest missing an artifact role refuses only that role' {
        $broken = New-PhaseManifest
        $broken.roles.Remove('baseline_underlay')
        Refused { Get-P4IndexedSlotRoute -Manifest $broken -SlotId 'frame-5-underlay-baseline-decision' } 'does not declare artifact role baseline_underlay'
        Check ((Get-P4IndexedSlotRoute -Manifest $broken -SlotId 'frame-1-single-baseline-decision').artifact_role -ceq 'baseline_single') 'Other role refused'
    }
    Case 'Foreign' 'a role record of another production build is refused by the planner' {
        $broken = New-PhaseManifest
        $broken.roles.baseline_layers16.production_commit = $broken.roles.baseline_underlay.production_commit
        Refused { Get-P4SlotCollectorBudget $broken (Get-PhaseSlot 'frame-3-layers16-baseline-decision') $hash } 'Phase source identity differs'
    }
    Case 'Foreign' 'an unknown phase protocol id is not the phase' {
        $other = New-PhaseManifest
        $other.protocol.id = 'nene-pixel-p4-layer-phase-verification-v2'
        Check (-not (Test-P4PhaseManifest $other)) 'Unknown protocol treated as phase'
        Refused { Get-P4IndexedSlotRoute -Manifest $other -SlotId 'frame-1-single-baseline-decision' } 'Unknown slot'
    }

    # Analysis: the phase analysis names the phase protocol, manifest, comparison role and artifact role.
    Case 'Analysis' 'phase frame, memory and storage analyses are accepted' {
        foreach ($id in @('frame-3-layers16-baseline-decision', 'frame-4-layers16-candidate-decision',
                'memory-layers16-baseline-1', 'publication-layers16-candidate', 'saf-save-layers16-candidate')) {
            $slot = Get-PhaseSlot $id
            Read-Analysis "ok-$id" $slot (New-PhaseAnalysis $slot $expectedRoles[$id]) | Out-Null
        }
    }
    Case 'Analysis' 'wrong artifact role is refused' {
        $slot = Get-PhaseSlot 'frame-3-layers16-baseline-decision'
        Refused { Read-Analysis 'wrong-frame-role' $slot (New-PhaseAnalysis $slot 'candidate') } 'names artifact role candidate'
        $memory = Get-PhaseSlot 'memory-layers16-baseline-2'
        Refused { Read-Analysis 'wrong-memory-role' $memory (New-PhaseAnalysis $memory 'baseline_single') } 'names artifact role baseline_single'
        $both = New-PhaseAnalysis $slot 'baseline_layers16'
        $both.phase_context = [ordered]@{ artifact_role = 'baseline_underlay' }
        Refused { Read-Analysis 'disagreeing-role' $slot $both } 'names artifact role baseline_underlay'
        $none = New-PhaseAnalysis $slot 'baseline_layers16'
        $none.Remove('artifact_role')
        Refused { Read-Analysis 'missing-role' $slot $none } 'does not declare its artifact role'
    }
    Case 'Analysis' 'wrong comparison role, foreign manifest or v7 protocol is refused' {
        $slot = Get-PhaseSlot 'frame-3-layers16-baseline-decision'
        $wrong = New-PhaseAnalysis $slot 'baseline_layers16'; $wrong.role = 'candidate'
        Refused { Read-Analysis 'wrong-comparison' $slot $wrong } 'Phase analysis identity does not match'
        $foreign = New-PhaseAnalysis $slot 'baseline_layers16'; $foreign.preflight_sha256 = 'd' * 64
        Refused { Read-Analysis 'foreign-manifest' $slot $foreign } 'Phase analysis identity does not match'
        $old = New-PhaseAnalysis $slot 'baseline_layers16'; $old.protocol_id = $v7
        Refused { Read-Analysis 'v7-protocol' $slot $old } 'Phase analysis identity does not match'
        $memory = Get-PhaseSlot 'memory-layers16-candidate-1'
        $context = New-PhaseAnalysis $memory 'candidate'; $context.phase_context.preflight_sha256 = 'd' * 64
        Refused { Read-Analysis 'foreign-context' $memory $context } 'context differs at preflight_sha256'
    }

    # Chain: the completed chain re-proves each prior phase analysis by the same identity rule.
    Case 'Chain' 'phase chain with correct identities admits the next slot' {
        $root = Join-Path $OutputDirectory 'chain-ok'
        $catalog = @(Get-P4SlotCatalog $phase)
        foreach ($slot in $catalog[0..6]) { Complete-PhaseSlot $root $slot (New-PhaseAnalysis $slot $expectedRoles[$slot.id]) }
        Assert-P4CompletedChain -Root $root -Catalog $catalog -SlotId $catalog[7].id -ManifestHash $hash
    }
    Case 'Chain' 'a prior analysis of the wrong artifact role or manifest stops the chain' {
        $catalog = @(Get-P4SlotCatalog $phase)
        $root = Join-Path $OutputDirectory 'chain-wrong-role'
        Complete-PhaseSlot $root $catalog[0] (New-PhaseAnalysis $catalog[0] 'candidate')
        Refused { Assert-P4CompletedChain -Root $root -Catalog $catalog -SlotId $catalog[1].id -ManifestHash $hash } 'names artifact role candidate'
        $root = Join-Path $OutputDirectory 'chain-foreign'
        foreach ($slot in $catalog[0..5]) { Complete-PhaseSlot $root $slot (New-PhaseAnalysis $slot $expectedRoles[$slot.id]) }
        $foreign = New-PhaseAnalysis $catalog[6] 'baseline_layers16'; $foreign.phase_context.preflight_sha256 = 'd' * 64
        Complete-PhaseSlot $root $catalog[6] $foreign
        Refused { Assert-P4CompletedChain -Root $root -Catalog $catalog -SlotId $catalog[7].id -ManifestHash $hash } 'context differs at preflight_sha256'
    }

    # Wiring: Invoke-P4IndexedSlot uses the route, the hash-bound planner, and no comparison-role lookup.
    Case 'Wiring' 'wrapper routes through the phase binding' {
        $parseErrors = $null
        $ast = [Management.Automation.Language.Parser]::ParseFile($invokePath, [ref]$null, [ref]$parseErrors)
        Check ($parseErrors.Count -eq 0) 'Wrapper parse failure'
        $text = $ast.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and
            $n.Name -ceq 'Invoke-P4IndexedSlot' }, $true).Extent.Text
        Check ($text.Contains('Get-P4IndexedSlotRoute -Manifest $manifest -SlotId $SlotId')) 'Route not used'
        Check ($text.Contains('Get-P4SlotCollectorBudget -Manifest $manifest -Slot $slot -ManifestSha256 $manifestHash')) 'Budget not hash-bound'
        Check ($text.Contains('-not $route.phase -and $null -ne $lanePlan')) 'v7 quarantine reaches the phase'
        Check (-not ([IO.File]::ReadAllText($invokePath) -match 'roles\[\$[sS]lot\.role\]')) 'Comparison-role manifest lookup remains'
    }
    $result.status = 'success'
} finally {
    $result.cases = $script:cases.ToArray()
    Save-Json (Join-Path $OutputDirectory 'result.json') $result
    $passed = @($script:cases | Where-Object { $_.passed }).Count
    $groups = @($script:cases | Group-Object { $_.group } | ForEach-Object {
        "$($_.Name) $(@($_.Group | Where-Object { $_.passed }).Count)/$($_.Count)" }) -join ', '
    Write-Output "status=$($result.status) passed=$passed/$($script:cases.Count) groups: $groups"
}

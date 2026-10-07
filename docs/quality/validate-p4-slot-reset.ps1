param([Parameter(Mandatory)][string] $OutputDirectory,
    [ValidateSet('Plan', 'Native', 'Failures', 'Boundaries', 'Limits', 'Compatibility')][string] $CaseGroup = 'Plan')
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/p4-device-private-slot-reset.ps1')
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Reset validator output exists' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$script:checks = [Collections.Generic.List[string]]::new()
$script:refusals = [Collections.Generic.List[string]]::new()
$script:session = 'reset-session'; $script:slot = 'memory-layers16-candidate-1'
$script:fixedFiles = @('no_backup/nene-pixel-recovery-v1', 'no_backup/nene-pixel-recovery-v1.new',
    'no_backup/nene-pixel-recovery-v1.bak', 'files/profileinstaller_profileWrittenFor_lastUpdateTime.dat', 'files/profileInstalled')
$script:requests = [Collections.Generic.List[string]]::new()
$script:mode = ''; $script:map = $null; $script:fixtureOriginal = @(); $script:nativePlan = $null
$timer = [Diagnostics.Stopwatch]::StartNew(); $failure = $null; $status = 'failure'
function Check([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw "Reset validator: $Message" }
    $script:checks.Add($Message)
}
function Reject([scriptblock] $Action, [string] $Pattern, [string] $Message) {
    $caught = $null
    try { & $Action | Out-Null } catch { $caught = $_.Exception.Message }
    if ($null -eq $caught -or $caught -notlike $Pattern) { throw "Expected $Pattern for $Message; observed $caught" }
    $script:refusals.Add("$Message : $caught"); $script:checks.Add($Message)
}
function MakeDir([string] $Path) { return [pscustomobject]@{ Path = $Path; Type = 'directory' } }
function File([string] $Path, [int] $Seed) {
    return [pscustomobject]@{ Path = $Path; Type = 'file'; Size = [long]$Seed; Hash = ('a' * 62) + $Seed.ToString('x2');
        MtimeNs = '1720000000123456' + $Seed.ToString('000'); LinkCount = 1 }
}
function Original {
    $items = @(MakeDir 'files'; MakeDir 'no_backup'; MakeDir 'shared_prefs'; MakeDir 'no_backup/reference-underlays';
        File 'no_backup/reference-underlays/original.png' 1; File 'no_backup/reference-underlays/original.state' 2;
        File 'shared_prefs/user.xml' 3)
    for ($i = 0; $i -lt $script:fixedFiles.Count; $i++) { $items += File $script:fixedFiles[$i] (4 + $i) }
    return ,$items
}
function Current([object[]] $Original, [switch] $Empty) {
    $items = @((New-P4IsolationPlan $Original $script:session).Expected)
    if (-not $Empty) {
        $items += @(MakeDir 'no_backup/reference-underlays'; File 'no_backup/reference-underlays/new.png' 20;
            File 'no_backup/reference-underlays/new.state' 21; File 'files/unrelated-new' 22)
        for ($i = 0; $i -lt $script:fixedFiles.Count; $i++) { $items += File $script:fixedFiles[$i] (30 + $i) }
    }
    return ,$items
}
function FileIdentities([object[]] $Items) {
    return (@($Items | Where-Object Type -ceq 'file' | ForEach-Object { "$($_.Hash)|$($_.Size)|$($_.MtimeNs)|$($_.LinkCount)" } | Sort-Object) -join "`n")
}
function MakeContext([string] $Name) {
    $directory = Join-Path $OutputDirectory $Name
    [void][IO.Directory]::CreateDirectory($directory)
    return @{ repository_root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path;
        output_directory = $directory; adb_path = (Get-Command pwsh).Source; serial = 'reset-serial';
        package = 'io.github.hideyukimori.nenepixel'; operation_budget = New-P4OperationBudget 300 }
}
function MakePreservation {
    $context = MakeContext 'preservation'
    $script:fixtureOriginal = Original; $script:map = ConvertTo-P4InventoryMap $script:fixtureOriginal
    $snapshot = [ordered]@{ schema = 'nene-pixel-device-private-snapshot-v1'; status = 'verified-snapshot';
        serial = $context.serial; package = $context.package; stage = 'fixture'; installed_apk_path = '/data/app/fixture/base.apk';
        source_sha256 = Get-P4SnapshotSources; archive_transfer_record_sha256 = $null }
    foreach ($item in @(@('inventory', 'inventory-before.json'), @('inventory_after', 'inventory-after.json'),
            @('archive', 'original.tar'), @('apk', 'original.apk'))) {
        $path = Join-Path $context.output_directory "fixture-$($item[1])"
        if ($item[0] -in @('inventory', 'inventory_after')) { Write-P4SessionJson $path $script:fixtureOriginal }
        else { [IO.File]::WriteAllBytes($path, $(if ($item[0] -ceq 'archive') { [byte[]]::new(1024) } else { [byte[]]@(1, 2, 3) })) }
        $snapshot["$($item[0])_path"] = $path; $snapshot["$($item[0])_sha256"] = Get-P4SnapshotHash $path
    }
    $transfer = Join-Path $context.output_directory 'fixture-original-apk.json'
    Write-P4SessionJson $transfer @{ status = 'synthetic-fixture' }
    $snapshot.apk_transfer_record_sha256 = Get-P4SnapshotHash $transfer
    $snapshotPath = Join-Path $context.output_directory 'fixture-snapshot.json'
    Write-P4SessionJson $snapshotPath $snapshot
    $script:mockApkHash = $snapshot.apk_sha256
    $isolation = New-P4IsolationPlan $script:fixtureOriginal $script:session
    $script:moves = $isolation.Moves
    $zero = ConvertTo-P4InventoryMap (New-P4IsolationPlan $script:fixtureOriginal $script:session 0).Expected
    $script:directories = @($zero.Keys | Where-Object { -not $script:map.ContainsKey($_) } |
        Sort-Object @{ Expression = { $_.Split('/').Length } }, @{ Expression = { $_ } })
    $preservation = Invoke-P4PrivateIsolation $context $script:session 'reset-experiment' $snapshotPath
    $script:proofPath = $preservation.ResultPath
    $script:proofHash = Get-P4SnapshotHash $script:proofPath
    $script:mockApkHash = 'b' * 64
}
function MakeReset([string] $Name, [string] $Mode = '', [switch] $Empty) {
    $script:mode = $Mode; $script:requests.Clear()
    $context = MakeContext $Name
    $script:map = ConvertTo-P4InventoryMap (Current $script:fixtureOriginal -Empty:$Empty)
    $script:nativePlan = New-P4SlotResetPlan $script:fixtureOriginal $script:session $script:slot @(Get-P4Items $script:map)
    $script:moves = $script:nativePlan.Moves
    $zero = ConvertTo-P4InventoryMap (New-P4SlotResetPlan $script:fixtureOriginal $script:session $script:slot @(Get-P4Items $script:map) 0).Expected
    $script:directories = @($zero.Keys | Where-Object { -not $script:map.ContainsKey($_) } |
        Sort-Object @{ Expression = { $_.Split('/').Length } }, @{ Expression = { $_ } })
    return $context
}
function RunReset($Context, [string] $ProofHash = $script:proofHash) {
    return Invoke-P4PrivateSlotReset $Context $script:proofPath $ProofHash ('c' * 64) $script:slot ('b' * 64)
}
function Get-P4NativePrivateInventory {
    param($Context, $Stage)
    $script:requests.Add("inventory:$Stage")
    $observed = ConvertTo-P4InventoryMap @(Get-P4Items $script:map)
    if ($script:mode -ceq 'readback-drift' -and $Stage.EndsWith('move-0-post')) {
        $observed['shared_prefs/user.xml'].MtimeNs = '1'
    }
    return ,([object[]]@(Get-P4Items $observed))
}
function Get-P4SnapshotApkIdentity {
    param($Context, $Stage, $Step)
    $script:requests.Add("apk:$Step")
    $hash = if ($script:mode -ceq 'wrong-apk' -or ($script:mode -ceq 'final-apk' -and $Step -ceq 'final')) {
        'f' * 64
    } else { $script:mockApkHash }
    return [pscustomobject]@{ Path = '/data/app/fixture/base.apk'; Hash = $hash }
}
function Assert-P4RestorationStopped { param($Context, $Stage, $Step) $script:requests.Add("stopped:$Step") }
function Invoke-P4EncodedShellCapture {
    param($AdbPath, $Serial, $Script, $TimeoutSeconds, $DestinationPath, $RecordPath, $MaximumBytes)
    $name = [IO.Path]::GetFileNameWithoutExtension($DestinationPath)
    $script:requests.Add("mutation:$name")
    if ($TimeoutSeconds -lt 1 -or $TimeoutSeconds -gt 30 -or $MaximumBytes -ne 256 -or -not $Script.Contains('pidof')) {
        throw 'Mock mutation lost native bounds/guard'
    }
    [IO.File]::WriteAllBytes($DestinationPath, [byte[]]@())
    Write-P4SessionJson $RecordPath @{ status = 'mock-retained'; timeout_seconds = $TimeoutSeconds }
    if (($script:mode -ceq 'mkdir-fail' -and $name.EndsWith('mkdir-0')) -or
        ($script:mode -ceq 'move-fail' -and $name.EndsWith('move-1'))) { throw 'Synthetic mutation failure' }
    if ($name -cmatch 'mkdir-([0-9]+)$') {
        $path = $script:directories[[int]$Matches[1]]
        if (-not $Script.Contains('mkdir') -or -not $Script.Contains($path)) { throw 'Mock mkdir binding differs' }
        $script:map.Add($path, (MakeDir $path))
    } elseif ($name -cmatch 'move-([0-9]+)$') {
        $move = $script:moves[[int]$Matches[1]]
        if (-not $Script.Contains('mv -nT') -or -not $Script.Contains('stat -c %d') -or
            -not $Script.Contains($move.Source) -or -not $Script.Contains($move.Destination)) { throw 'Mock move binding differs' }
        $moving = @($script:map.Keys | Where-Object { $_ -ceq $move.Source -or $_.StartsWith("$($move.Source)/", [StringComparison]::Ordinal) })
        foreach ($oldPath in $moving) {
            $item = $script:map[$oldPath] | Select-Object *
            $item.Path = $move.Destination + $oldPath.Substring($move.Source.Length)
            $script:map.Add($item.Path, $item)
        }
        foreach ($oldPath in $moving) { [void]$script:map.Remove($oldPath) }
    } else { throw 'Unexpected mutation name' }
    return [pscustomobject]@{ status = 'success' }
}
try {
    if ($CaseGroup -ceq 'Plan') {
        $original = Original; $current = Current $original
        $plan = New-P4SlotResetPlan $original $script:session $script:slot $current
        $roots = @($script:fixedFiles) + @('no_backup/reference-underlays')
        Check ($plan.Moves.Count -eq 6 -and (($plan.Moves.Source | Sort-Object) -join '|') -ceq (($roots | Sort-Object) -join '|')) 'only the fixed six measurement roots move'
        $before = ConvertTo-P4InventoryMap $current; $after = ConvertTo-P4InventoryMap $plan.Expected
        Check ((FileIdentities $current) -ceq (FileIdentities $plan.Expected)) 'complete reset retains every byte/hash/mtime identity'
        foreach ($move in $plan.Moves) {
            Check (-not $after.ContainsKey($move.Source) -and $move.Destination -ceq "$($plan.Archive)/$($move.Source)" -and
                $after.ContainsKey($move.Destination)) "fresh archive destination $($move.Source)"
        }
        foreach ($path in @($before.Keys | Where-Object { $_ -like '*/original/*' -or $_ -ceq 'shared_prefs/user.xml' -or $_ -ceq 'files/unrelated-new' })) {
            Assert-P4SameEntry $before[$path] $after[$path]
        }
        Check $true 'guarded originals and unrelated entries stay at their exact paths'
        for ($i = 0; $i -le 6; $i++) {
            $prefix = New-P4SlotResetPlan $original $script:session $script:slot $current $i
            [void](Get-P4IsolatedState $original $script:session $prefix.Expected)
            $restoration = New-P4RestorationPlan $original $script:session $prefix.Expected
            $restored = ConvertTo-P4InventoryMap $restoration.Expected
            foreach ($entry in $original) { Assert-P4SameEntry $entry $restored[$entry.Path] }
            Check ((FileIdentities $current) -ceq (FileIdentities $restoration.Expected)) "reset move prefix $i remains lossless through final original restoration"
        }
        $empty = Current $original -Empty
        $noOp = New-P4SlotResetPlan $original $script:session $script:slot $empty
        Assert-P4ExactMap (ConvertTo-P4InventoryMap $empty) (ConvertTo-P4InventoryMap $noOp.Expected)
        Check ($noOp.Moves.Count -eq 0) 'no-op reset adds no remote directory'
        $changed = ConvertTo-P4InventoryMap $current
        $changed["no_backup/p4-user-preservation/$($script:session)/original/reference-underlays/original.state"].MtimeNs = '1'
        Reject { New-P4SlotResetPlan $original $script:session $script:slot @(Get-P4Items $changed) } '*Changed file identity*' 'original timestamp drift refused'
        $occupied = ConvertTo-P4InventoryMap $current; Add-P4Ancestors $occupied $plan.Archive
        Reject { New-P4SlotResetPlan $original $script:session $script:slot @(Get-P4Items $occupied) } '*archive already exists*' 'occupied slot archive refused'
        foreach ($path in @('no_backup/nene-pixel-recovery-v1', 'no_backup/reference-underlays')) {
            $wrong = @($empty) + @($(if ($path -like '*underlays') { File $path 25 } else { MakeDir $path }))
            Reject { New-P4SlotResetPlan $original $script:session $script:slot $wrong } '*Invalid original type*' "wrong measurement root type $path"
        }
        foreach ($count in @(-1, 7)) {
            Reject { New-P4SlotResetPlan $original $script:session $script:slot $current $count } '*completed slot reset move count*' "invalid reset prefix $count"
        }
        $ambiguous = @($empty) + @($original | Where-Object Path -ceq 'no_backup/nene-pixel-recovery-v1')
        Reject { New-P4SlotResetPlan $original $script:session $script:slot $ambiguous } '*Ambiguous live original*' 'byte-identical original at live destination refused'
        $old = ConvertTo-P4InventoryMap $original; Add-P4Ancestors $old "no_backup/p4-layer-slots/$($script:session)"
        $owned = @(Get-P4Items $old)
        Reject { New-P4SlotResetPlan $owned $script:session $script:slot (Current $owned) } '*belongs to original*' 'original-owned archive session refused'
    }
    if ($CaseGroup -ceq 'Native') {
        MakePreservation
        foreach ($empty in @($false, $true)) {
            $context = MakeReset "success-$empty" -Empty:$empty
            $initial = @(Get-P4Items $script:map)
            $result = RunReset $context
            $record = $result.Record
            Check ($record.status -ceq 'reset' -and $record.session -ceq $script:session -and
                $record.experiment_id -ceq 'reset-experiment' -and $record.preservation_sha256 -ceq $script:proofHash -and
                $record.preflight_sha256 -ceq ('c' * 64)) "actual reset consumes verified preservation identity empty=$empty"
            Check ($record.before_apk_sha256 -ceq ('b' * 64) -and $record.final_apk_sha256 -ceq ('b' * 64)) "actual reset verifies APK on both sides empty=$empty"
            Assert-P4ExactMap (ConvertTo-P4InventoryMap $script:nativePlan.Expected) $script:map
            Check ((FileIdentities $initial) -ceq (FileIdentities @(Get-P4Items $script:map))) "native reset keeps all file identities empty=$empty"
            $mutations = @($script:requests | Where-Object { $_ -like 'mutation:*' })
            Check ($mutations.Count -eq $record.steps.Count -and
                (($empty -and $mutations.Count -eq 0) -or (-not $empty -and $mutations.Count -eq $script:directories.Count + 6))) "native mkdir/move count empty=$empty"
            foreach ($step in $record.steps) {
                if ($step.status -cne 'success' -or (Get-P4SnapshotHash $step.intent_path) -cne $step.intent_sha256 -or
                    (Get-P4SnapshotHash $step.result_path) -cne $step.result_sha256) { throw 'Native step evidence differs' }
            }
            Check ((Get-P4SnapshotHash $record.final_inventory_path) -ceq $record.final_inventory_sha256 -and
                (Get-P4SnapshotHash $record.plan_path) -ceq $record.plan_sha256) "native plan/step/final evidence pins empty=$empty"
            $before = $script:requests.Count
            Reject { RunReset $context } '*stage already exists*' "same reset stage cannot repeat empty=$empty"
            Check ($script:requests.Count -eq $before) "occupied stage refuses before device work empty=$empty"
        }
    }
    if ($CaseGroup -ceq 'Failures') {
        MakePreservation
        foreach ($case in @(@('wrong-proof', '*preservation hash differs*', $false), @('wrong-apk', '*installed APK differs*', $false),
                @('mkdir-fail', '*Synthetic mutation failure*', $true), @('move-fail', '*Synthetic mutation failure*', $true),
                @('readback-drift', '*Changed file identity*', $true), @('final-apk', '*APK identity changed*', $true))) {
            $context = MakeReset $case[0] $case[0]
            $initial = @(Get-P4Items $script:map)
            $hash = if ($case[0] -ceq 'wrong-proof') { 'd' * 64 } else { $script:proofHash }
            Reject { RunReset $context $hash } $case[1] "$($case[0]) propagates refusal"
            $record = Get-Content (Join-Path $context.output_directory "p4-slot-reset-$($script:slot)-result.json") -Raw | ConvertFrom-Json
            Check ($record.status -ceq 'failure' -and -not [string]::IsNullOrWhiteSpace($record.reason)) "$($case[0]) retains failure result"
            Check ((@($script:requests | Where-Object { $_ -like 'mutation:*' }).Count -gt 0) -eq $case[2]) "$($case[0]) respects mutation boundary"
            $restoration = New-P4RestorationPlan $script:fixtureOriginal $script:session @(Get-P4Items $script:map)
            Check ((FileIdentities $initial) -ceq (FileIdentities $restoration.Expected)) "$($case[0]) prefix remains compatible with final original restoration"
            if ($case[0] -in @('mkdir-fail', 'move-fail', 'readback-drift')) {
                $last = $record.steps[-1]
                Check ($last.status -ceq 'failure' -and (Test-Path $last.intent_path) -and (Test-Path $last.result_path)) "$($case[0]) retains failed step intent/result"
            }
        }
        $context = MakeReset 'missing-budget'; $context.Remove('operation_budget')
        Reject { RunReset $context } '*explicit operation budget*' 'reset rejects missing operation budget'
        Check ($script:requests.Count -eq 0) 'missing budget starts no device work'
        $context = MakeReset 'reserved-time'; $context.operation_budget = New-P4OperationBudget 15
        # Observation mocks retain the actual operation clock; the real mutation boundary must refuse.
        Reject { RunReset $context } '*no native time*' 'reset cannot mutate during reserved termination time'
        Check (@($script:requests | Where-Object { $_ -like 'mutation:*' }).Count -eq 0) 'reserved time starts no mutation'
    }
    if ($CaseGroup -ceq 'Boundaries') {
        $original = Original; $current = Current $original
        $map = ConvertTo-P4InventoryMap $current
        $zero = ConvertTo-P4InventoryMap (New-P4SlotResetPlan $original $script:session $script:slot $current 0).Expected
        $directories = @($zero.Keys | Where-Object { -not $map.ContainsKey($_) } |
            Sort-Object @{ Expression = { $_.Split('/').Length } }, @{ Expression = { $_ } })
        foreach ($directory in $directories) {
            $map.Add($directory, (MakeDir $directory))
            $restoration = New-P4RestorationPlan $original $script:session @(Get-P4Items $map)
            Check ((FileIdentities $current) -ceq (FileIdentities $restoration.Expected)) "mkdir prefix through $directory remains restorable"
        }
        $plan = New-P4SlotResetPlan $original $script:session $script:slot $current
        $next = New-P4SlotResetPlan $original $script:session 'memory-layers16-candidate-2' $plan.Expected
        Assert-P4ExactMap (ConvertTo-P4InventoryMap $plan.Expected) (ConvertTo-P4InventoryMap $next.Expected)
        Check ($next.Moves.Count -eq 0) 'next empty slot retains the previous slot archive without a new mutation'
        $occupied = @($current) + @(File 'no_backup/p4-layer-slots' 50)
        Reject { New-P4SlotResetPlan $original $script:session $script:slot $occupied } '*Occupied directory*' 'non-directory archive parent refused'
        $extra = @($current) + @(File "no_backup/p4-user-preservation/$($script:session)/unexpected" 51)
        Reject { New-P4SlotResetPlan $original $script:session $script:slot $extra } '*Unexpected session entry*' 'foreign guarded entry refused'
        Reject { New-P4SlotResetPlan $original $script:session '../foreign' $current } '*Invalid session id*' 'unsafe slot path refused'
    }
    if ($CaseGroup -ceq 'Limits') {
        MakePreservation
        $context = MakeReset 'result-entry-cap'
        $remaining = 4096 - $script:map.Count
        for ($i = 0; $i -lt $remaining; $i++) {
            $path = "files/cap-$i"
            $script:map.Add($path, (File $path 60))
        }
        Reject { RunReset $context } '*4096-entry*' 'reset refuses directories that would exceed the final inventory cap'
        $record = Get-Content (Join-Path $context.output_directory "p4-slot-reset-$($script:slot)-result.json") -Raw | ConvertFrom-Json
        Check ($record.status -ceq 'failure' -and $record.steps.Count -eq 0 -and
            @($script:requests | Where-Object { $_ -like 'mutation:*' }).Count -eq 0 -and
            (Test-Path $record.pre_inventory_path)) 'final entry cap retains pre-inventory and starts no mutation'
    }
    if ($CaseGroup -ceq 'Compatibility') {
        foreach ($file in @('p4-device-private-preservation.ps1', 'p4-device-private-slot-reset.ps1')) {
            $tokens = $null; $errors = $null
            $path = Join-Path $PSScriptRoot "measurements/$file"
            $currentAst = [Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
            Check ($errors.Count -eq 0) "$file parses"
            if ($file -ceq 'p4-device-private-preservation.ps1') {
                # Hashes equal the accepted version (7780204) of each function body (Extent.Text, LF, UTF-8 SHA-256); not pinned to a
                # branch commit, which a squash merge removes. Changing the baseline requires a commit that changes this table.
                $acceptedFunctions = [ordered]@{
                    'New-P4Map' = 'cf49df5e0f9903c0906568e9e372c2529e51cf1efdbe58abd0e46ee44a2c2340'
                    'Assert-P4Path' = '4ca3c063b9b7f2c035a2535878e3eb9143c465781b732831e80837bc8860ad1c'
                    'ConvertTo-P4InventoryMap' = '4ea20068a016d0d77943e017721c4b920c16d1e6c9db37c86ddea7ff25ba7f2a'
                    'Get-P4Items' = '1de477a0bab8fd1ed83561256a784756435ec65633ab2124815147e8bf3657e5'
                    'Add-P4Directory' = '384aff0c90aec098eeffb165f14bb4ee5970e236e8d8726fa9e25c979ca0a52e'
                    'Add-P4Ancestors' = '23f83cc293dbb5ad54b2cee7c3578dd6d0d9859eea5c9a07997dfacb7ea906fa'
                    'Test-P4Below' = '1591ee9ae8cd73719fa47b15b3cb1de303c07bd5d44ebecd4f00ba5278fffc55'
                    'Assert-P4SameEntry' = 'a15dd9a55e645f3d365e0c5dfa3049c5ff672c3d748bdfb03eaba5eaf6295893'
                    'Assert-P4ExactMap' = 'a8a29ea13d1b971824a79e53826f2dce758f937961d2b1e49d89906b703f9dc0'
                    'Assert-P4Session' = 'f23eb20fe9cfdd4d277afbcf6acc2672a5306e91b2e10a11aee41d63d7e27e6d'
                    'Get-P4MoveRoots' = '345f6984e6cb68422c8809ca34cc10fb9e8a77253aefcafb12f506cfb313a7ea'
                    'Move-P4InventoryEntries' = 'e66622fbffb042eb7baa1c947dcf77c1dd2f7e4cb46f4e21f3fd631af96fe744'
                    'Assert-P4MoveCount' = '44e888613f9f78d54ee38f2fe77257790e6a16549cde9d3d7d911cffdb3f774f'
                    'New-P4IsolationPlan' = '4e336f909cdf3f0ed4e0da50dd470a05edea4cf34ade0956d0a53c26c0b65ed0'
                    'Assert-P4Isolation' = 'cf3c52c62a8be40eb7a9350a80ab5c4ab930ff3bf0588a54c144e0a11f721b60'
                    'Get-P4IsolatedState' = '10a71c2abc181a4a4236e5910559e9c0c1d94829e9099a83291ffa313cb3118e'
                    'New-P4RestorationExpectedMap' = '1e47b5326960ca09605220c56bf4a5bac6a7593e18cf12900c041976e14d7dc8'
                    'New-P4RestorationPlan' = 'a64249965a4aa7e072e8408c75a790a538206922ed6cb5bbcb23d97e710c7a7f'
                    'Assert-P4Restored' = '9fd7e366a8d05f88198abb25972b6929d4bb7007301b1806705d70bb360efd70'
                }
                $currentFunctions = @{}
                foreach ($node in $currentAst.FindAll({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] }, $false)) {
                    $currentFunctions[$node.Name] = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData(
                        [Text.Encoding]::UTF8.GetBytes($node.Extent.Text.Replace("`r`n", "`n")))).ToLowerInvariant()
                }
                foreach ($name in $acceptedFunctions.Keys) {
                    Check ($currentFunctions.ContainsKey($name) -and $currentFunctions[$name] -ceq $acceptedFunctions[$name]) "accepted policy function unchanged: $name"
                }
            }
        }
        # Accepted source inputs pinned by git blob hash (git hash-object), equal to the accepted version (7780204); not pinned
        # to a branch commit, which a squash merge removes. Changing the baseline requires a commit that changes this table.
        $acceptedBlobs = [ordered]@{
            'p4-device-private-session.ps1' = '7e995f9be155395b43b12c66f52e804347d188b4'
            'p4-device-private-restore.ps1' = '36d761de7d8082c8e3d25508fb20a98abf061506'
            'p4-device-private-native.ps1' = 'c449d927cbfbefce5c71081b6a298926ae906331'
            'p4-device-private-observation.ps1' = '0798480071e6b6ba715e58cdd13848dd5799d2b4'
            'p4-device-private-transport.ps1' = 'ed4572eb8945fba06e20e312016df547a8bd05ac'
            'p4-operation-budget.ps1' = 'f12e018cd9256b837f929f16cce25afc9d3ef992'
        }
        $repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
        foreach ($file in $acceptedBlobs.Keys) {
            $blob = & git -C $repository hash-object -- "docs/quality/measurements/$file"
            Check ($LASTEXITCODE -eq 0 -and $blob -ceq $acceptedBlobs[$file]) "$file keeps its accepted source input"
        }
    }
    $status = 'pass'
} catch { $failure = $_; throw }
finally {
    $result = [ordered]@{ group = $CaseGroup; status = $status; checks = $script:checks.Count; accepted = $script:checks.ToArray();
        refusals = $script:refusals.ToArray(); requests = $script:requests.ToArray(); elapsed_seconds = $timer.Elapsed.TotalSeconds;
        source_sha256 = [ordered]@{ snapshot = Get-P4SnapshotSources; session = Get-P4SnapshotHash $script:P4SessionHelperPath;
            restore = Get-P4SnapshotHash $script:P4RestorationHelperPath; reset = Get-P4SnapshotHash $script:P4SlotResetHelperPath };
        error = $(if ($null -eq $failure) { $null } else { [string]$failure }) }
    Write-P4SessionJson (Join-Path $OutputDirectory 'result.json') $result
    [pscustomobject]$result | Select-Object group, status, checks, elapsed_seconds | ConvertTo-Json -Compress | Write-Output
}

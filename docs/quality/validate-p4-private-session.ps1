param([string] $OutputDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/p4-device-private-session.ps1')

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Get-NenePixelLabPath ('evidence/145-native-isolation/' +
        (Get-Date -Format 'yyyyMMddTHHmmssfff') + '-' + [guid]::NewGuid().ToString('N')) `
        -StartDirectory $PSScriptRoot
}
if ([IO.File]::Exists($OutputDirectory) -or [IO.Directory]::Exists($OutputDirectory)) {
    throw 'Session validator evidence directory exists'
}
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$script:checks = 0
$script:calls = [Collections.Generic.List[string]]::new()
$script:mode = ''
$script:mockMap = $null
$script:original = $null
$script:moves = $null
$script:directories = $null
$script:apkHash = 'a' * 64
$script:apkPath = '/data/app/~~ab12/io.github.hideyukimori.nenepixel-xy_1/base.apk'
$script:repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$script:session = 'session1'
$script:experiment = 'experiment1'

function Check([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw "Session validator: $Message" }
    $script:checks++
}
function New-FileEntry([string] $Path, [int] $Seed) {
    $hash = ([char](97 + ($Seed % 6))).ToString() * 64
    return [pscustomobject]@{ Path = $Path; Type = 'file'; Size = [long]$Seed;
        Hash = $hash;
        MtimeNs = "17200000001234567$($Seed.ToString('00'))"; LinkCount = 1 }
}
function New-Fixture([string] $Name, [string] $Mode = '') {
    $directory = Join-Path $OutputDirectory $Name
    [void][IO.Directory]::CreateDirectory($directory)
    $script:mode = $Mode
    $script:calls.Clear()
    $items = [Collections.Generic.List[object]]::new()
    foreach ($root in @('files', 'no_backup', 'shared_prefs', 'databases')) {
        if ($Mode -eq 'absent' -and $root -ne 'files') { continue }
        $items.Add([pscustomobject]@{ Path = $root; Type = 'directory' })
    }
    if ($Mode -ne 'absent') {
        $items.Add([pscustomobject]@{ Path = 'no_backup/reference-underlays'; Type = 'directory' })
        $items.Add((New-FileEntry 'no_backup/reference-underlays/a' 1))
        $fixed = @('no_backup/nene-pixel-recovery-v1', 'no_backup/nene-pixel-recovery-v1.new',
            'no_backup/nene-pixel-recovery-v1.bak',
            'files/profileinstaller_profileWrittenFor_lastUpdateTime.dat', 'files/profileInstalled')
        for ($i = 0; $i -lt $fixed.Count; $i++) { $items.Add((New-FileEntry $fixed[$i] ($i + 2))) }
    }
    $items.Add((New-FileEntry 'files/untouched' 8))
    $script:original = $items.ToArray()
    $script:mockMap = ConvertTo-P4InventoryMap $script:original
    $plan = New-P4IsolationPlan $script:original $script:session
    $script:moves = $plan.Moves
    $zeroMap = ConvertTo-P4InventoryMap (New-P4IsolationPlan $script:original $script:session 0).Expected
    $script:directories = @($zeroMap.Keys | Where-Object { -not $script:mockMap.ContainsKey($_) } |
        Sort-Object @{ Expression = { $_.Split('/').Length } }, @{ Expression = { $_ } })
    $stage = 'snapshot1'
    $before = Join-Path $directory "$stage-inventory-before.json"
    $after = Join-Path $directory "$stage-inventory-after.json"
    $archive = Join-Path $directory "$stage-original.tar"
    $apk = Join-Path $directory "$stage-original.apk"
    $apkTransfer = Join-Path $directory "$stage-original-apk.json"
    $tarTransfer = Join-Path $directory "$stage-original-tar.json"
    Write-P4SessionJson $before $script:original
    Write-P4SessionJson $after $script:original
    [IO.File]::WriteAllBytes($archive, [byte[]](1, 2, 3))
    [IO.File]::WriteAllBytes($apk, [byte[]](4, 5, 6))
    [IO.File]::WriteAllText($apkTransfer, '{}')
    [IO.File]::WriteAllText($tarTransfer, '{}')
    $script:apkHash = Get-P4SnapshotHash $apk
    $snapshot = [ordered]@{ schema = 'nene-pixel-device-private-snapshot-v1';
        status = 'verified-snapshot'; serial = 'serial-01:5555';
        package = 'io.github.hideyukimori.nenepixel'; stage = $stage;
        source_sha256 = Get-P4SnapshotSources; inventory_path = $before;
        inventory_sha256 = Get-P4SnapshotHash $before; inventory_after_path = $after;
        inventory_after_sha256 = Get-P4SnapshotHash $after; archive_path = $archive;
        archive_sha256 = Get-P4SnapshotHash $archive;
        archive_transfer_record_sha256 = Get-P4SnapshotHash $tarTransfer;
        apk_path = $apk; apk_sha256 = $script:apkHash;
        apk_transfer_record_sha256 = Get-P4SnapshotHash $apkTransfer;
        installed_apk_path = $script:apkPath }
    $snapshotPath = Join-Path $directory "$stage-snapshot.json"
    Write-P4SessionJson $snapshotPath $snapshot
    return [pscustomobject]@{ Context = @{ repository_root = $script:repository;
            output_directory = $directory; adb_path = (Get-Command pwsh).Source;
            serial = 'serial-01:5555'; package = 'io.github.hideyukimori.nenepixel' };
        SnapshotPath = $snapshotPath; Snapshot = $snapshot; Directory = $directory }
}

# Only the native observation, installed-APK identity and encoded mutation boundary are mocked.
function Get-P4NativePrivateInventory {
    param([System.Collections.IDictionary] $Context, [string] $Stage)
    $script:calls.Add("inventory:$Stage")
    $items = @(Get-P4Items $script:mockMap)
    if ($script:mode -eq 'bad-readback' -and $Stage.EndsWith('move-0-post')) {
        $map = ConvertTo-P4InventoryMap $items
        $map.Remove($script:moves[0].Destination) | Out-Null
        return ,([object[]]@(Get-P4Items $map))
    }
    if ($script:mode -eq 'inventory-drift' -and $Stage.EndsWith('-original')) {
        $map = ConvertTo-P4InventoryMap $items
        $map['files/untouched'].Hash = 'f' * 64
        return ,([object[]]@(Get-P4Items $map))
    }
    return ,([object[]]$items)
}
function Get-P4SnapshotApkIdentity {
    param([System.Collections.IDictionary] $Context, [string] $Stage, [string] $Step)
    $script:calls.Add("apk:$Step")
    $hash = if ($script:mode -eq 'apk-drift') { 'f' * 64 } else { $script:apkHash }
    $path = if ($script:mode -eq 'apk-path-drift') { '/data/app/other/base.apk' } else { $script:apkPath }
    return [pscustomobject]@{ Path = $path; Hash = $hash }
}
function Invoke-P4EncodedShellCapture {
    param([string] $AdbPath, [string] $Serial, [string] $Script,
        [int] $TimeoutSeconds, [string] $DestinationPath, [string] $RecordPath,
        [long] $MaximumBytes)
    $name = [IO.Path]::GetFileNameWithoutExtension($DestinationPath)
    $script:calls.Add("mutation:$name")
    Check ($TimeoutSeconds -eq 30 -and $MaximumBytes -eq 256) 'bounded mutation'
    Check ($Script.Contains('pidof') -and $Script.Contains('p4_status') -and
        $Script.Contains('p4_pid') -and $Script.Contains('exit 73')) 'strict PID guard'
    if ($script:mode -eq 'mkdir-fail' -and $name.EndsWith('mkdir-0')) {
        [IO.File]::WriteAllBytes($DestinationPath, [byte[]]::new(0))
        [IO.File]::WriteAllText($RecordPath, '{"status":"failure"}')
        throw 'Synthetic mkdir failure'
    }
    if ($script:mode -eq 'move-fail' -and $name.EndsWith('move-0')) {
        [IO.File]::WriteAllBytes($DestinationPath, [byte[]]::new(0))
        [IO.File]::WriteAllText($RecordPath, '{"status":"failure"}')
        throw 'Synthetic move failure'
    }
    if ($name -match 'mkdir-([0-9]+)$') {
        $path = $script:directories[[int]$Matches[1]]
        Check ($Script.Contains('mkdir') -and $Script.Contains((ConvertTo-P4ShellWord $path))) 'mkdir command binding'
        Add-P4Directory $script:mockMap $path
    } elseif ($name -match 'move-([0-9]+)$') {
        $move = $script:moves[[int]$Matches[1]]
        Check ($Script.Contains('mv -nT') -and $Script.Contains('stat -c %d') -and
            $Script.Contains((ConvertTo-P4ShellWord $move.Source)) -and
            $Script.Contains((ConvertTo-P4ShellWord $move.Destination))) 'move command binding'
        # Apply the path rename to the mock's own map. The expected planner prefix is not its source.
        $moving = @($script:mockMap.Keys | Where-Object {
                $_ -ceq $move.Source -or $_.StartsWith("$($move.Source)/", [StringComparison]::Ordinal) })
        foreach ($old in $moving) {
            $new = $move.Destination + $old.Substring($move.Source.Length)
            $item = $script:mockMap[$old] | Select-Object *
            $item.Path = $new
            $script:mockMap.Add($new, $item)
        }
        foreach ($old in $moving) { $script:mockMap.Remove($old) | Out-Null }
    } else { throw "Unexpected mutation: $name" }
    [IO.File]::WriteAllBytes($DestinationPath, [byte[]]::new(0))
    [IO.File]::WriteAllText($RecordPath, '{"status":"success"}')
    return [pscustomobject]@{ status = 'success' }
}

function Expect-Refusal($Fixture, [string] $Reason, [bool] $MutationExpected) {
    $failed = $false
    try { [void](Invoke-P4PrivateIsolation $Fixture.Context $script:session $script:experiment $Fixture.SnapshotPath) }
    catch { $failed = $true }
    Check $failed "$Reason refused"
    $recordPath = Join-Path $Fixture.Directory "p4-isolation-$script:session-$script:experiment-result.json"
    Check ([IO.File]::Exists($recordPath)) "$Reason failure record"
    $record = Get-Content $recordPath -Raw | ConvertFrom-Json
    Check ($record.status -ceq 'failure' -and -not [string]::IsNullOrEmpty($record.reason)) "$Reason status"
    $mutations = @($script:calls | Where-Object { $_.StartsWith('mutation:') })
    Check (($mutations.Count -gt 0) -eq $MutationExpected) "$Reason mutation boundary"
    return $record
}

$success = New-Fixture 'all-six'
$out = Invoke-P4PrivateIsolation $success.Context $script:session $script:experiment $success.SnapshotPath
Check ($out.Record.status -ceq 'preserved' -and $out.Record.steps.Count -eq 9) 'six moves and three directories'
Check ((Get-P4SnapshotHash $out.Record.isolation_inventory_path) -ceq $out.Record.isolation_inventory_sha256) 'final inventory hash'
Check ($out.Record.source_sha256.session_helper -ceq (Get-P4SnapshotHash (Join-Path $PSScriptRoot 'measurements/p4-device-private-session.ps1'))) 'executor source hash'
Check ([bool](Assert-P4Isolation $script:original $script:session @(Get-P4Items $script:mockMap))) 'mock final isolation'
foreach ($step in $out.Record.steps) {
    Check ($step.status -ceq 'success' -and [IO.File]::Exists($step.intent_path) -and
        [IO.File]::Exists($step.result_path) -and
        (Get-P4SnapshotHash $step.intent_path) -ceq $step.intent_sha256 -and
        (Get-P4SnapshotHash $step.result_path) -ceq $step.result_sha256) 'step retained hash binding'
}
$script:calls.Clear()
$reused = $false
try { [void](Invoke-P4PrivateIsolation $success.Context $script:session $script:experiment $success.SnapshotPath) } catch { $reused = $true }
Check ($reused -and $script:calls.Count -eq 0) 'same session refuses before device calls'

foreach ($case in @('uppercase', 'mixedcase')) {
    $fixture = New-Fixture "host-collision-$case"
    $leaf = if ($case -ceq 'uppercase') {
        "P4-ISOLATION-$script:session-$script:experiment-move-0-result.json"
    } else { "P4-iSoLaTiOn-$script:session-$script:experiment-move-0-result.json" }
    $occupied = Join-Path $fixture.Directory $leaf
    [IO.File]::WriteAllBytes($occupied, [byte[]](0, 10, 13, 255))
    $occupiedHash = Get-P4SnapshotHash $occupied
    $beforeFiles = @([IO.Directory]::EnumerateFileSystemEntries($fixture.Directory) | Sort-Object)
    $reason = $null
    try { [void](Invoke-P4PrivateIsolation $fixture.Context $script:session $script:experiment $fixture.SnapshotPath) }
    catch { $reason = $_.Exception.Message }
    Check ($null -ne $reason -and $reason.StartsWith('Isolation host session is occupied:')) "$case host collision refused"
    Check ($script:calls.Count -eq 0) "$case host collision has zero native calls"
    Assert-P4ExactMap (ConvertTo-P4InventoryMap $script:original) $script:mockMap
    Check ($true) "$case host collision leaves source inventory unchanged"
    Check ((Get-P4SnapshotHash $occupied) -ceq $occupiedHash) "$case host collision bytes preserved"
    $afterFiles = @([IO.Directory]::EnumerateFileSystemEntries($fixture.Directory) | Sort-Object)
    Check (($afterFiles -join "`n") -ceq ($beforeFiles -join "`n")) "$case host collision creates no outputs"
}

$absent = New-Fixture 'absent' 'absent'
$out = Invoke-P4PrivateIsolation $absent.Context $script:session $script:experiment $absent.SnapshotPath
Check ($out.Record.status -ceq 'preserved' -and $out.Record.steps.Count -eq 4) 'absent roots create guard with zero moves'

foreach ($case in @('status', 'file-hash', 'source', 'binding', 'inventory-object', 'apk-drift',
        'apk-path-drift', 'inventory-drift')) {
    $fixture = New-Fixture $case $case
    switch ($case) {
        'status' { $fixture.Snapshot.status = 'failure' }
        'file-hash' { [IO.File]::AppendAllText($fixture.Snapshot.archive_path, 'changed') }
        'source' { $fixture.Snapshot.source_sha256.'p4-device-private-preservation.ps1' = 'f' * 64 }
        'binding' { $fixture.Snapshot.serial = 'other-serial' }
        'inventory-object' {
            [IO.File]::WriteAllText($fixture.Snapshot.inventory_path,
                (ConvertTo-Json -InputObject $script:original[0]))
            $fixture.Snapshot.inventory_sha256 = Get-P4SnapshotHash $fixture.Snapshot.inventory_path
        }
    }
    if ($case -in @('status', 'source', 'binding', 'inventory-object')) {
        [IO.File]::Delete($fixture.SnapshotPath)
        Write-P4SessionJson $fixture.SnapshotPath $fixture.Snapshot
    }
    [void](Expect-Refusal $fixture $case $false)
}
foreach ($case in @('mkdir-fail', 'move-fail', 'bad-readback')) {
    $fixture = New-Fixture $case $case
    $record = Expect-Refusal $fixture $case $true
    Check ($record.steps.Count -ge 1 -and $record.steps[-1].status -ceq 'failure') "$case failed step retained"
    $lastName = $record.steps[-1].name
    Check ([IO.File]::Exists((Join-Path $fixture.Directory "p4-isolation-$script:session-$script:experiment-$lastName-intent.json"))) "$case intent retained"
    if ($case -ne 'mkdir-fail') {
        Check (@($script:calls | Where-Object { $_ -like '*move-1*' }).Count -eq 0) "$case no later move"
    }
}

$summary = [ordered]@{ schema = 'nene-pixel-p4-session-host-validator-v1';
    status = 'pass'; checks = $script:checks; source_sha256 = Get-P4SnapshotSources }
Write-P4SessionJson (Join-Path $OutputDirectory 'validation.json') $summary
Write-Output "PASS: $($script:checks) private isolation checks."

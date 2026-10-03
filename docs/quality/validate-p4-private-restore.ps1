param([string] $OutputDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/p4-device-private-restore.ps1')
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Get-NenePixelLabPath ('evidence/145-native-restoration/' +
        (Get-Date -Format 'yyyyMMddTHHmmssfff') + '-' + [guid]::NewGuid().ToString('N')) -StartDirectory $PSScriptRoot
}
if ([IO.File]::Exists($OutputDirectory) -or [IO.Directory]::Exists($OutputDirectory)) { throw 'Validator evidence already exists' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$script:checks = 0
$script:calls = [Collections.Generic.List[string]]::new()
$script:map = $null
$script:mode = ''
$script:originalHash = ''
$script:installedHash = ''
$script:original = @()
$script:pre = @()
$script:session = 'session1'
$script:experiment = 'experiment1'
function Check([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw "Restoration validator: $Message" }
    $script:checks++
}
function New-FileEntry([string] $Path, [int] $Seed) {
    return [pscustomobject]@{ Path = $Path; Type = 'file'; Size = [long]$Seed;
        Hash = ([char](97 + ($Seed % 6))).ToString() * 64;
        MtimeNs = "17200000001234567$($Seed.ToString('00'))"; LinkCount = 1 }
}
function Add-MockDirectory([string] $Path) {
    if ($script:map.ContainsKey($Path)) { throw 'Mock occupied mkdir' }
    $parent = if ($Path.Contains('/')) { $Path.Substring(0, $Path.LastIndexOf('/')) } else { '' }
    if ($parent.Length -gt 0 -and (-not $script:map.ContainsKey($parent) -or $script:map[$parent].Type -cne 'directory')) {
        throw 'Mock missing parent'
    }
    $script:map.Add($Path, [pscustomobject]@{ Path = $Path; Type = 'directory' })
}
function Move-MockEntries([string] $Source, [string] $Destination) {
    if (-not $script:map.ContainsKey($Source) -or $script:map.ContainsKey($Destination)) { throw 'Mock source/destination conflict' }
    $parent = $Destination.Substring(0, $Destination.LastIndexOf('/'))
    if (-not $script:map.ContainsKey($parent) -or $script:map[$parent].Type -cne 'directory') { throw 'Mock move parent absent' }
    $moving = @($script:map.Keys | Where-Object { $_ -ceq $Source -or $_.StartsWith("$Source/", [StringComparison]::Ordinal) })
    foreach ($old in $moving) {
        $new = $Destination + $old.Substring($Source.Length)
        $item = $script:map[$old] | Select-Object *
        $item.Path = $new
        $script:map.Add($new, $item)
    }
    foreach ($old in $moving) { [void]$script:map.Remove($old) }
}
function Write-MockBytes([string] $Path, [byte[]] $Bytes) {
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try { $stream.Write($Bytes) } finally { $stream.Dispose() }
}
function New-Fixture([string] $Name) {
    $directory = Join-Path $OutputDirectory $Name
    [void][IO.Directory]::CreateDirectory($directory)
    $script:mode = $Name; $script:calls.Clear()
    $items = [Collections.Generic.List[object]]::new()
    if ($Name -ne 'empty') {
        $items.Add([pscustomobject]@{ Path = 'files'; Type = 'directory' })
        $items.Add((New-FileEntry 'files/untouched' 8))
        $items.Add([pscustomobject]@{ Path = 'files/existing'; Type = 'directory' })
    }
    if ($Name -notin @('absent', 'empty')) {
        foreach ($root in @('no_backup', 'shared_prefs', 'databases', 'no_backup/reference-underlays')) {
            $items.Add([pscustomobject]@{ Path = $root; Type = 'directory' })
        }
        $items.Add((New-FileEntry 'no_backup/reference-underlays/state' 1))
        $fixed = @('no_backup/nene-pixel-recovery-v1', 'no_backup/nene-pixel-recovery-v1.new',
            'no_backup/nene-pixel-recovery-v1.bak', 'files/profileinstaller_profileWrittenFor_lastUpdateTime.dat', 'files/profileInstalled')
        for ($i = 0; $i -lt $fixed.Count; $i++) { $items.Add((New-FileEntry $fixed[$i] ($i + 2))) }
    }
    $script:original = $items.ToArray()
    $script:map = ConvertTo-P4InventoryMap $script:original
    $snapshotStage = 'snapshot1'
    $before = Join-Path $directory "$snapshotStage-inventory-before.json"
    $after = Join-Path $directory "$snapshotStage-inventory-after.json"
    Write-P4SessionJson $before $script:original; Write-P4SessionJson $after $script:original
    $archive = Join-Path $directory "$snapshotStage-original.tar"
    $apkPath = Join-Path $directory "$snapshotStage-original.apk"
    Write-MockBytes $archive ([byte[]](1, 2, 3)); Write-MockBytes $apkPath ([byte[]](4, 5, 6))
    $apkTransfer = Join-Path $directory "$snapshotStage-original-apk.json"
    $archiveTransfer = Join-Path $directory "$snapshotStage-original-tar.json"
    Write-P4SessionJson $apkTransfer @{}; Write-P4SessionJson $archiveTransfer @{}
    $script:originalHash = Get-P4SnapshotHash $apkPath
    $script:installedHash = if ($Name -in @('needs-install', 'install-failure', 'install-output', 'install-stderr', 'install-wrong-hash', 'install-crlf')) { 'd' * 64 } else { $script:originalHash }
    $snapshot = [ordered]@{ schema = 'nene-pixel-device-private-snapshot-v1'; status = 'verified-snapshot';
        serial = 'serial-01:5555'; package = 'io.github.hideyukimori.nenepixel'; stage = $snapshotStage;
        inventory_path = $before; inventory_sha256 = Get-P4SnapshotHash $before;
        inventory_after_path = $after; inventory_after_sha256 = Get-P4SnapshotHash $after;
        archive_path = $archive; archive_sha256 = Get-P4SnapshotHash $archive;
        apk_path = $apkPath; apk_sha256 = $script:originalHash;
        apk_transfer_record_sha256 = Get-P4SnapshotHash $apkTransfer;
        archive_transfer_record_sha256 = Get-P4SnapshotHash $archiveTransfer;
        source_sha256 = Get-P4SnapshotSources;
        installed_apk_path = '/data/app/original/base.apk' }
    if ($Name -eq 'snapshot-source') { $snapshot.source_sha256.'p4-device-private-preservation.ps1' = 'f' * 64 }
    if ($Name -eq 'snapshot-status') { $snapshot.status = 'failure' }
    if ($Name -eq 'snapshot-hash') { $snapshot.archive_sha256 = 'f' * 64 }
    $snapshotPath = Join-Path $directory "$snapshotStage-snapshot.json"
    Write-P4SessionJson $snapshotPath $snapshot
    $isolation = New-P4IsolationPlan $script:original $script:session
    $stage = "p4-isolation-$script:session-$script:experiment"
    $steps = [Collections.Generic.List[object]]::new()
    $zero = ConvertTo-P4InventoryMap (New-P4IsolationPlan $script:original $script:session 0).Expected
    $operations = [Collections.Generic.List[object]]::new()
    $number = 0
    foreach ($path in @($zero.Keys | Where-Object { -not $script:map.ContainsKey($_) } |
            Sort-Object @{ Expression = { $_.Split('/').Length } }, @{ Expression = { $_ } })) {
        $operations.Add([pscustomobject]@{ Kind = 'mkdir'; Name = "mkdir-$number"; Source = $path; Destination = $null })
        $number++
    }
    for ($i = 0; $i -lt $isolation.Moves.Count; $i++) {
        $operations.Add([pscustomobject]@{ Kind = 'move'; Name = "move-$i"; Source = $isolation.Moves[$i].Source;
            Destination = $isolation.Moves[$i].Destination })
    }
    foreach ($operation in $operations) {
        $expected = @(Get-P4Items $script:map)
        if ($operation.Kind -ceq 'mkdir') {
            $command = New-P4IsolationMkdirCommand $snapshot.package $operation.Source
            Add-MockDirectory $operation.Source
        } else {
            $command = New-P4IsolationMoveCommand $snapshot.package $operation.Source $operation.Destination $script:map[$operation.Source].Type
            Move-MockEntries $operation.Source $operation.Destination
        }
        $intentPath = Join-Path $directory "$stage-$($operation.Name)-intent.json"
        $resultPath = Join-Path $directory "$stage-$($operation.Name)-result.json"
        $intent = [ordered]@{ kind = $operation.Kind; name = $operation.Name; script = $command;
            expected_inventory = $expected; next_inventory = @(Get-P4Items $script:map) }
        if ($Name -eq 'step-meaning' -and $operation.Name -ceq 'mkdir-0') { $intent.script += '; unexpected' }
        Write-P4SessionJson $intentPath $intent
        $result = [ordered]@{ status = 'success'; reason = $null; intent_path = $intentPath; intent_sha256 = Get-P4SnapshotHash $intentPath }
        if ($Name -eq 'step-status' -and $operation.Name -ceq 'mkdir-0') { $result.status = 'failure' }
        Write-P4SessionJson $resultPath $result
        $steps.Add([pscustomobject]@{ kind = $operation.Kind; name = $operation.Name; status = 'success'; reason = $null;
            intent_path = $intentPath; intent_sha256 = Get-P4SnapshotHash $intentPath;
            result_path = $resultPath; result_sha256 = Get-P4SnapshotHash $resultPath })
    }
    $planPath = Join-Path $directory "$stage-plan.json"
    if ($Name -eq 'plan-meaning') { $isolation.Guard += '-changed' }
    Write-P4SessionJson $planPath $isolation
    $inventoryPath = Join-Path $directory "$stage-inventory.json"
    $isolated = @(Get-P4Items $script:map)
    if ($Name -eq 'inventory-meaning') { $isolated = @($isolated | Where-Object { $_.Path -cne 'files/untouched' }) }
    Write-P4SessionJson $inventoryPath $isolated
    $preservation = [ordered]@{ schema = 'nene-pixel-device-preservation-v2'; status = 'preserved';
        serial = $snapshot.serial; package = $snapshot.package; session = $script:session; experiment_id = $script:experiment;
        created_utc = [DateTimeOffset]::UtcNow.ToString('o'); snapshot_path = $snapshotPath;
        snapshot_sha256 = Get-P4SnapshotHash $snapshotPath; plan_path = $planPath; plan_sha256 = Get-P4SnapshotHash $planPath;
        isolation_inventory_path = $inventoryPath; isolation_inventory_sha256 = Get-P4SnapshotHash $inventoryPath;
        original_apk_sha256 = $script:originalHash; source_sha256 = [ordered]@{ snapshot = Get-P4SnapshotSources;
            session_helper = Get-P4SnapshotHash $script:P4SessionHelperPath }; steps = @($steps.ToArray()); reason = $null }
    switch ($Name) {
        'preservation-status' { $preservation.status = 'failure' }
        'preservation-binding' { $preservation.serial = 'other' }
        'extra-binding' { $preservation.extra = 'unknown' }
        'missing-binding' { $preservation.Remove('original_apk_sha256') }
        'session-source' { $preservation.source_sha256.session_helper = 'f' * 64 }
        'source-extra' { $preservation.source_sha256.extra = 'f' * 64 }
        'plan-hash' { $preservation.plan_sha256 = 'f' * 64 }
        'inventory-hash' { $preservation.isolation_inventory_sha256 = 'f' * 64 }
        'step-hash' { $preservation.steps[0].result_sha256 = 'f' * 64 }
        'step-missing' { $preservation.steps = @($preservation.steps | Select-Object -Skip 1) }
        'artifact-path' { $preservation.plan_path = $inventoryPath; $preservation.plan_sha256 = Get-P4SnapshotHash $inventoryPath }
    }
    $preservationPath = Join-Path $directory "$stage-result.json"
    Write-P4SessionJson $preservationPath $preservation
    # Add measurement data under original directories, plus replacements of all isolated roots.
    if ($Name -ne 'empty') {
        Add-MockDirectory 'files/newdir'
        $script:map.Add('files/newdir/new', (New-FileEntry 'files/newdir/new' 21))
        $script:map.Add('files/existing/new', (New-FileEntry 'files/existing/new' 22))
        $script:map.Add('files/profileInstalled', (New-FileEntry 'files/profileInstalled' 23))
        Add-MockDirectory 'no_backup/reference-underlays'
        $script:map.Add('no_backup/reference-underlays/measurement', (New-FileEntry 'no_backup/reference-underlays/measurement' 24))
        foreach ($leaf in @('nene-pixel-recovery-v1', 'nene-pixel-recovery-v1.new', 'nene-pixel-recovery-v1.bak')) {
            $script:map.Add("no_backup/$leaf", (New-FileEntry "no_backup/$leaf" 25))
        }
        $script:map.Add('files/profileinstaller_profileWrittenFor_lastUpdateTime.dat', (New-FileEntry 'files/profileinstaller_profileWrittenFor_lastUpdateTime.dat' 26))
    }
    if ($Name -eq 'original-drift') { $script:map['files/untouched'].MtimeNs = '1720000000000000000' }
    if ($Name -eq 'archive-occupied') { Add-MockDirectory "no_backup/p4-user-preservation/$script:session/measurement" }
    if ($Name -eq 'partial-original') {
        $src = "no_backup/p4-user-preservation/$script:session/original/profileInstalled"
        $script:map['files/profileInstalled'] = $script:map[$src] | Select-Object *
        $script:map['files/profileInstalled'].Path = 'files/profileInstalled'
    }
    $script:pre = @(Get-P4Items $script:map)
    if ($Name -eq 'host-occupied') { Write-P4SessionJson (Join-Path $directory "p4-restoration-$script:session-$script:experiment-old.json") @{} }
    if ($Name -eq 'host-case-occupied') { Write-P4SessionJson (Join-Path $directory "P4-RESTORATION-$script:session-$script:experiment-old.json") @{} }
    return [pscustomobject]@{ Directory = $directory; PreservationPath = $preservationPath;
        ExpectedHash = $script:installedHash; Context = @{ repository_root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path;
            output_directory = $directory; adb_path = (Get-Command pwsh).Source; serial = $snapshot.serial; package = $snapshot.package } }
}

# The observer exposes the mock's independently changed map, never planner Expected.
function Get-P4NativePrivateInventory {
    param([Collections.IDictionary] $Context, [string] $Stage)
    $script:calls.Add("inventory:$Stage")
    if ($script:mode -eq 'bad-readback' -and $Stage.EndsWith('measurement-move-0-post')) {
        $bad = ConvertTo-P4InventoryMap @(Get-P4Items $script:map)
        [void]$bad.Remove('files/untouched')
        return ,([object[]]@(Get-P4Items $bad))
    }
    return ,([object[]]@(Get-P4Items $script:map))
}
function Get-P4SnapshotApkIdentity {
    param([Collections.IDictionary] $Context, [string] $Stage, [string] $Step)
    $script:calls.Add("apk:$Step")
    $hash = if ($script:mode -eq 'unknown-apk') { 'f' * 64 } else { $script:installedHash }
    if ($script:mode -eq 'final-apk-drift' -and $Step -ceq 'final-apk') { $hash = 'f' * 64 }
    return [pscustomobject]@{ Path = '/data/app/changed-install-location/base.apk'; Hash = $hash }
}
function Invoke-P4RawAdbCapture {
    param([string] $AdbPath, [string[]] $AdbArguments, [int] $TimeoutSeconds,
        [string] $DestinationPath, [string] $RecordPath, [long] $MaximumBytes)
    $install = $AdbArguments[2] -ceq 'install'
    $script:calls.Add($(if ($install) { 'install' } else { 'pid' }))
    $exit = 1; $output = ''; $stderr = ''; $status = 'failure'; $errorText = 'The private-file capture failed (1): '
    if ($install) {
        Check ($TimeoutSeconds -eq 120 -and $MaximumBytes -eq 4096) 'bounded install'
        Check (($AdbArguments[2..5] -join ' ') -ceq 'install -r -d -t') 'only preserving install flags'
        Check ((Get-P4SnapshotHash $AdbArguments[6]) -ceq $script:originalHash) 'pinned original APK install source'
        $exit = 0; $output = "Performing Streamed Install`nSuccess`n"; $status = 'success'; $errorText = $null
        if ($script:mode -eq 'install-crlf') { $output = $output.Replace("`n", "`r`n") }
        if ($script:mode -eq 'install-output') { $output = "Performing Streamed Install`nSuccess`nUnexpected`n" }
        if ($script:mode -eq 'install-stderr') { $stderr = 'unexpected' }
        if ($script:mode -eq 'install-failure') { $exit = 1; $status = 'failure'; $errorText = 'Synthetic install failure' }
        if ($script:mode -ne 'install-wrong-hash') { $script:installedHash = $script:originalHash }
    } else {
        Check ($TimeoutSeconds -eq 30 -and $MaximumBytes -eq 1024) 'bounded PID proof'
        Check (($AdbArguments[2..4] -join ' ') -ceq 'shell -T -n' -and $AdbArguments[5].StartsWith('pidof ')) 'native PID command'
        if ($script:mode -eq 'live-process') { $exit = 0; $status = 'success'; $errorText = $null; $output = "1234`n" }
        if ($script:mode -eq 'pid-stderr') { $stderr = 'error'; $errorText = 'The private-file capture failed (1): error' }
    }
    Write-MockBytes $DestinationPath ([Text.Encoding]::UTF8.GetBytes($output))
    Write-P4SessionJson "$RecordPath.command.json" ([ordered]@{ arguments = $AdbArguments; timeout_seconds = $TimeoutSeconds })
    $raw = [ordered]@{ status = $status; exit_code = $exit; error = $errorText; stderr = $stderr;
        destination_path = [IO.Path]::GetFullPath($DestinationPath); byte_count = ([IO.FileInfo]::new($DestinationPath)).Length;
        sha256 = Get-P4SnapshotHash $DestinationPath; timed_out = $false; byte_limit_exceeded = $false;
        job_quiescent_after_failure = $true }
    Write-P4SessionJson $RecordPath $raw
    if ($exit -ne 0) { throw $errorText }
    return [pscustomobject]$raw
}
function Invoke-P4NativeRunAs {
    param([Collections.IDictionary] $Context, [string] $Stage, [string] $Step, [string] $Script, [long] $MaximumBytes)
    $script:calls.Add("mutation:$Step")
    Check ($MaximumBytes -eq 256 -and $Script.Contains('pidof') -and $Script.Contains('p4_status') -and $Script.Contains('exit 73')) 'bounded guarded mutation'
    if (($script:mode -eq 'mkdir-failure' -and $Step -ceq 'mkdir-0') -or
        ($script:mode -eq 'move-failure' -and $Step -ceq 'measurement-move-0')) { throw 'Synthetic mutation failure' }
    if ($Script -match "mkdir '([^']+)'$") { Add-MockDirectory $Matches[1] }
    elseif ($Script -match "mv -nT '([^']+)' '([^']+)'$") {
        Check ($Script.Contains('stat -c %d') -and $Script.Contains('[ ! -L')) 'same filesystem and non-link move'
        Move-MockEntries $Matches[1] $Matches[2]
    } else { throw 'Unrecognized mock mutation' }
    return ,([byte[]]::new(0))
}
function Expect-Refusal($Fixture, [bool] $MutationExpected, [bool] $ResultExpected = $true) {
    $failed = $false
    try { [void](Invoke-P4PrivateRestoration $Fixture.Context $Fixture.PreservationPath $Fixture.ExpectedHash) }
    catch { $failed = $true }
    Check $failed "$script:mode refused"
    $path = Join-Path $Fixture.Directory "p4-restoration-$script:session-$script:experiment-result.json"
    Check ([IO.File]::Exists($path) -eq $ResultExpected) "$script:mode result retention"
    Check ((@($script:calls | Where-Object { $_.StartsWith('mutation:') }).Count -gt 0) -eq $MutationExpected) "$script:mode mutation boundary"
    if ($ResultExpected) {
        $record = Get-Content $path -Raw | ConvertFrom-Json
        Check ($record.status -ceq 'failure' -and $record.reason.Length -gt 0) "$script:mode failed result"
        return $record
    }
}

foreach ($case in @('all-six', 'absent', 'empty', 'needs-install', 'install-crlf')) {
    $fixture = New-Fixture $case
    $out = Invoke-P4PrivateRestoration $fixture.Context $fixture.PreservationPath $fixture.ExpectedHash
    Check ($out.Record.status -ceq 'restored') "$case restored"
    Check ($out.Record.expected_installed_apk_sha256 -ceq $fixture.ExpectedHash -and $out.Record.final_apk_sha256 -ceq $script:originalHash) "$case APK binding"
    Check ($out.Record.originally_absent_no_backup -eq ($case -in @('absent', 'empty'))) "$case originally absent exception"
    Check ((Get-P4SnapshotHash $out.Record.final_inventory_path) -ceq $out.Record.final_inventory_sha256) "$case final hash"
    Check ([bool](Assert-P4Restored $script:original $script:session $script:pre @(Get-P4Items $script:map))) "$case final originals/archive/mtimes"
    $seenOriginal = $false
    foreach ($step in $out.Record.steps) {
        Check ($step.status -ceq 'success' -and (Get-P4SnapshotHash $step.result_path) -ceq $step.result_sha256 -and
            (Get-P4SnapshotHash $step.intent_path) -ceq $step.intent_sha256) "$case step hash"
        if ($step.kind -ceq 'original-move') { $seenOriginal = $true }
        if ($step.kind -ceq 'measurement-move') { Check (-not $seenOriginal) 'measurement before originals' }
    }
    Check ((@($script:calls | Where-Object { $_ -ceq 'install' }).Count -eq 1) -eq ($case -in @('needs-install', 'install-crlf'))) "$case install selection"
    if ($case -eq 'all-six') {
        Check (@($out.Record.steps | Where-Object { $_.kind -ceq 'original-move' }).Count -eq 6) 'six protected originals returned'
        $script:calls.Clear()
        $completedHash = Get-P4SnapshotHash $out.ResultPath
        $refused = $false
        try { [void](Invoke-P4PrivateRestoration $fixture.Context $fixture.PreservationPath $fixture.ExpectedHash) }
        catch { $refused = $true }
        Check ($refused -and $script:calls.Count -eq 0 -and
            (Get-P4SnapshotHash $out.ResultPath) -ceq $completedHash) 'completed restoration cannot resume'
    }
}
foreach ($case in @('preservation-status', 'preservation-binding', 'extra-binding', 'missing-binding',
        'snapshot-status', 'snapshot-hash', 'snapshot-source', 'session-source', 'source-extra',
        'plan-hash', 'plan-meaning', 'inventory-hash', 'inventory-meaning', 'artifact-path',
        'step-meaning', 'step-status', 'step-hash', 'step-missing')) {
    $fixture = New-Fixture $case
    [void](Expect-Refusal $fixture $false)
    Check ($script:calls.Count -eq 0) "$case before device calls"
}
foreach ($case in @('unknown-apk', 'live-process', 'pid-stderr', 'install-failure', 'install-output', 'install-stderr', 'install-wrong-hash',
        'original-drift', 'archive-occupied', 'partial-original')) {
    $fixture = New-Fixture $case
    [void](Expect-Refusal $fixture $false)
    if ($case -in @('unknown-apk', 'live-process', 'pid-stderr')) {
        Check (@($script:calls | Where-Object { $_ -ceq 'install' }).Count -eq 0) "$case before install"
    }
}
foreach ($case in @('mkdir-failure', 'move-failure', 'bad-readback', 'final-apk-drift')) {
    $fixture = New-Fixture $case
    $record = Expect-Refusal $fixture $true
    if ($case -ne 'final-apk-drift') {
        Check ($record.steps[-1].status -ceq 'failure') "$case failed step retained"
        Check (@($script:calls | Where-Object { $_ -ceq 'mutation:measurement-move-1' -or $_.StartsWith('mutation:original-move') }).Count -eq 0) "$case stops at failed boundary"
    }
}
foreach ($case in @('host-occupied', 'host-case-occupied')) {
    $fixture = New-Fixture $case
    [void](Expect-Refusal $fixture $false $false)
    Check ($script:calls.Count -eq 0) "$case before device calls"
}
$fixture = New-Fixture 'invalid-expected-hash'
$fixture.ExpectedHash = 'A' * 64
[void](Expect-Refusal $fixture $false $false)
Check ($script:calls.Count -eq 0) 'invalid expected hash before device calls'
$summary = [ordered]@{ schema = 'nene-pixel-p4-restoration-host-validator-v1'; status = 'pass'; checks = $script:checks;
    source_sha256 = [ordered]@{ snapshot = Get-P4SnapshotSources; session_helper = Get-P4SnapshotHash $script:P4SessionHelperPath;
        restoration_helper = Get-P4SnapshotHash $script:P4RestorationHelperPath;
        validator = Get-P4SnapshotHash $PSCommandPath } }
Write-P4SessionJson (Join-Path $OutputDirectory 'validation.json') $summary
Write-Output "PASS: $($script:checks) private restoration checks. Evidence: $OutputDirectory"

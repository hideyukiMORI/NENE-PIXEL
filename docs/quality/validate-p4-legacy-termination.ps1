param([Parameter(Mandatory)][string] $OutputDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/p4-device-private-legacy-termination.ps1')
if ([IO.File]::Exists($OutputDirectory) -or [IO.Directory]::Exists($OutputDirectory)) {
    throw 'Validator evidence already exists'
}
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$script:checks = 0
$script:calls = [Collections.Generic.List[string]]::new()
$script:scripts = [Collections.Generic.List[string]]::new()
$script:map = $null
$script:mode = ''
$script:stage = "p4-legacy-$script:P4LegacySession"
$script:guarded = "$script:P4LegacyGuard/nene-pixel-recovery-v1"
$script:measured = "$script:P4LegacyArchive/measurement/no_backup/nene-pixel-recovery-v1"
$script:directories = @('no_backup/p4-user-preservation', $script:P4LegacyArchive,
    "$script:P4LegacyArchive/measurement", "$script:P4LegacyArchive/measurement/no_backup")
$script:identicalCount = 87

function Check([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw "Legacy termination validator: $Message" }
    $script:checks++
}
function Get-TextHash([string] $Text) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}
$script:h0 = Get-TextHash 'recovery-original'
$script:h1 = Get-TextHash 'recovery-live-measurement'
$script:apkHash = Get-TextHash 'installed-apk'

function New-FileEntry([string] $Path, [long] $Size, [string] $Hash, [int] $Seed) {
    return [pscustomobject]@{ Path = $Path; Type = 'file'; Size = $Size; Hash = $Hash
        MtimeNs = "1728400000$($Seed.ToString('000000000'))"; LinkCount = 1 }
}
function New-DirectoryEntry([string] $Path) { return [pscustomobject]@{ Path = $Path; Type = 'directory' } }

function New-Fixture([string] $Name) {
    $directory = Join-Path $OutputDirectory $Name
    [void][IO.Directory]::CreateDirectory($directory)
    $script:mode = $Name; $script:calls.Clear(); $script:scripts.Clear()
    $serial = 'serial-01:5555'; $package = 'io.github.hideyukimori.nenepixel'
    $items = [Collections.Generic.List[object]]::new()
    $original = [ordered]@{}
    $items.Add((New-DirectoryEntry 'files'))
    for ($i = 1; $i -le $script:identicalCount; $i++) {
        $path = "files/f-$($i.ToString('000'))"
        $hash = Get-TextHash $path
        $items.Add((New-FileEntry $path $i $hash $i))
        $original[$path] = [ordered]@{ bytes = [long]$i; sha256 = $hash }
    }
    for ($k = 0; $k -lt 2; $k++) {
        $marker = $script:P4LegacyMarkers[$k]
        $items.Add((New-FileEntry $marker (10 + $k) (Get-TextHash "marker-current-$k") (200 + $k)))
        $original[$marker] = [ordered]@{ bytes = [long](20 + $k); sha256 = Get-TextHash "marker-original-$k" }
    }
    $guardedHash = if ($Name -eq 'guarded-differs') { Get-TextHash 'other-recovery' } else { $script:h0 }
    $liveHash = if ($Name -eq 'live-ambiguous') { $script:h0 } else { $script:h1 }
    $items.Add((New-DirectoryEntry 'no_backup'))
    $items.Add((New-DirectoryEntry $script:P4LegacyGuard))
    $items.Add((New-FileEntry $script:guarded 399 $guardedHash 300))
    $items.Add((New-FileEntry $script:P4LegacyRecovery 401 $liveHash 301))
    $items.Add((New-FileEntry 'no_backup/x' 5 (Get-TextHash 'unrelated') 302))
    if ($Name -eq 'archive-occupied') {
        $items.Add((New-DirectoryEntry 'no_backup/p4-user-preservation'))
        $items.Add((New-DirectoryEntry $script:P4LegacyArchive))
    }
    $original[$script:P4LegacyRecovery] = [ordered]@{ bytes = [long]399; sha256 = $script:h0 }
    switch ($Name) {
        'marker-missing' { $original.Remove($script:P4LegacyMarkers[0]) }
        'original-path-missing' { $original['files/zz-missing'] = [ordered]@{ bytes = [long]1; sha256 = $script:h0 } }
        'original-file-changed' { $original['files/f-001'].sha256 = Get-TextHash 'changed' }
        'identical-count' { $original.Remove('files/f-087') }
    }
    $inventory = [ordered]@{ schema = 'nene-pixel-legacy-readonly-metadata-v1'; serial = $serial; package = $package
        installed_apk_sha256 = $script:apkHash; inventory = @($items.ToArray()) }
    if ($Name -eq 'inventory-schema') { $inventory.schema = 'nene-pixel-legacy-readonly-metadata-v0' }
    if ($Name -eq 'serial-mismatch') { $inventory.serial = 'serial-02:5555' }
    $manifest = [ordered]@{ schema = 'nene-pixel-device-preservation-v1'; state = 'preserved'; serial = $serial
        package = $package; guard = $script:P4LegacyGuard; original = $original
        recovery = [ordered]@{ 'no_backup/nene-pixel-recovery-v1' = [ordered]@{ bytes = [long]399
                sha256 = $script:h0 } } }
    if ($Name -eq 'manifest-state') { $manifest.state = 'restored' }
    if ($Name -eq 'guard-mismatch') { $manifest.guard = 'no_backup/issue-190-user-recovery-other' }
    $inputs = Join-Path $OutputDirectory "$Name-inputs"
    [void][IO.Directory]::CreateDirectory($inputs)
    $inventoryPath = Join-Path $inputs 'legacy-inventory.json'
    $manifestPath = Join-Path $inputs 'preservation.json'
    Write-P4SessionJson $inventoryPath $inventory
    Write-P4SessionJson $manifestPath $manifest
    $script:map = ConvertTo-P4InventoryMap @($items.ToArray())
    if ($Name -eq 'pre-drift') { $script:map['files/f-001'].MtimeNs = '1728400000999999999' }
    if ($Name -eq 'host-occupied') { Write-P4SessionJson (Join-Path $directory "$script:stage-old.json") @{} }
    return [pscustomobject]@{ Directory = $directory; InventoryPath = $inventoryPath
        InventoryHash = Get-P4SnapshotHash $inventoryPath; ManifestPath = $manifestPath
        ManifestHash = Get-P4SnapshotHash $manifestPath; ApkHash = $script:apkHash; Count = $items.Count
        Context = @{ repository_root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
            output_directory = $directory; adb_path = (Get-Command pwsh).Source; serial = $serial
            package = $package } }
}

# Mocks are defined after the dot-source so they shadow the device functions.
function Get-P4NativePrivateInventory {
    param([Collections.IDictionary] $Context, [string] $Stage)
    $script:calls.Add("inventory:$Stage")
    return ,([object[]]@(Get-P4Items $script:map))
}
function Get-P4SnapshotApkIdentity {
    param([Collections.IDictionary] $Context, [string] $Stage, [string] $Step)
    $script:calls.Add("apk:$Step")
    $hash = $script:apkHash
    if ($script:mode -eq 'apk-mismatch' -or ($script:mode -eq 'final-apk-drift' -and $Step -ceq 'final-apk')) {
        $hash = 'f' * 64
    }
    return [pscustomobject]@{ Path = '/data/app/legacy/base.apk'; Hash = $hash }
}
function Get-P4AdbServerListener([string] $AdbPath) {
    return ,([object[]] @([ordered]@{ local_address = '127.0.0.1'; local_port = 5037; owning_process = 4242
        process_name = 'adb.exe'; executable_path = [IO.Path]::GetFullPath($AdbPath) }))
}
function Invoke-P4RawAdbCapture {
    param([string] $AdbPath, [string[]] $AdbArguments, [int] $TimeoutSeconds,
        [string] $DestinationPath, [string] $RecordPath, [long] $MaximumBytes)
    if ($AdbArguments -ccontains 'install') { $script:calls.Add('install'); throw 'Install is never allowed' }
    $script:calls.Add('pid')
    Check (($AdbArguments[2..4] -join ' ') -ceq 'shell -T -n' -and $AdbArguments[5].StartsWith('pidof ')) 'PID command'
    $exit = 1; $output = ''; $status = 'failure'; $errorText = 'The private-file capture failed (1): '
    if ($script:mode -eq 'live-process') { $exit = 0; $status = 'success'; $errorText = $null; $output = "1234`n" }
    [IO.File]::WriteAllBytes($DestinationPath, [Text.Encoding]::UTF8.GetBytes($output))
    Write-P4SessionJson "$RecordPath.command.json" ([ordered]@{ arguments = $AdbArguments })
    $raw = [ordered]@{ status = $status; exit_code = $exit; error = $errorText; stderr = ''
        destination_path = [IO.Path]::GetFullPath($DestinationPath)
        byte_count = ([IO.FileInfo]::new($DestinationPath)).Length; sha256 = Get-P4SnapshotHash $DestinationPath
        timed_out = $false; byte_limit_exceeded = $false; job_quiescent_after_failure = $true }
    Write-P4SessionJson $RecordPath $raw
    if ($exit -ne 0) { throw $errorText }
    return [pscustomobject]$raw
}
function Move-MockFile([string] $Source, [string] $Destination) {
    $parent = $Destination.Substring(0, $Destination.LastIndexOf('/'))
    if (-not $script:map.ContainsKey($Source) -or $script:map.ContainsKey($Destination) -or
        -not $script:map.ContainsKey($parent)) { throw 'Mock move conflict' }
    $item = $script:map[$Source] | Select-Object *
    $item.Path = $Destination
    [void]$script:map.Remove($Source)
    $script:map.Add($Destination, $item)
}
function Invoke-P4NativeRunAs([Collections.IDictionary] $Context, [string] $Stage, [string] $Step,
    [string] $Script, [long] $MaximumBytes) {
    $script:calls.Add("mutation:$Step"); $script:scripts.Add($Script)
    Check ($MaximumBytes -eq 256 -and $Script.Contains('pidof') -and $Script.Contains('exit 73')) 'guarded mutation'
    if ($script:mode -eq 'mkdir-failure' -and $Step -ceq 'mkdir-0') { throw 'Synthetic mutation failure' }
    if ($script:mode -eq 'move-failure' -and $Step -ceq 'measurement-move-0') {
        return ,([Text.Encoding]::UTF8.GetBytes("moved`n"))
    }
    if ($script:mode -eq 'bad-readback' -and $Step -ceq 'original-move-0') { return ,([byte[]]::new(0)) }
    if ($Step -cmatch '^mkdir-(\d+)$') {
        $path = $script:directories[[int]$Matches[1]]
        Check ($Script.EndsWith("mkdir '$path'")) "$Step script target"
        $script:map.Add($path, (New-DirectoryEntry $path))
    } elseif ($Step -ceq 'measurement-move-0') {
        Check ($Script.EndsWith("mv -nT '$script:P4LegacyRecovery' '$script:measured'")) 'measurement move target'
        Move-MockFile $script:P4LegacyRecovery $script:measured
    } elseif ($Step -ceq 'original-move-0') {
        Check ($Script.EndsWith("mv -nT '$script:guarded' '$script:P4LegacyRecovery'")) 'original move target'
        Move-MockFile $script:guarded $script:P4LegacyRecovery
    } else { throw "Unrecognized mock mutation: $Step" }
    return ,([byte[]]::new(0))
}

function Invoke-Fixture($Fixture, [string] $InventoryHash = $Fixture.InventoryHash,
    [string] $ManifestHash = $Fixture.ManifestHash) {
    return Invoke-P4LegacyTermination -Context $Fixture.Context -ExpectedInventoryPath $Fixture.InventoryPath `
        -ExpectedInventorySha256 $InventoryHash -OriginalManifestPath $Fixture.ManifestPath `
        -OriginalManifestSha256 $ManifestHash -ExpectedInstalledApkHash $Fixture.ApkHash
}
function Get-Mutations { return @($script:calls | Where-Object { $_.StartsWith('mutation:') }) }
function Expect-Refusal($Fixture, [string] $Pattern, [bool] $ResultExpected,
    [string] $InventoryHash = $Fixture.InventoryHash, [string] $ManifestHash = $Fixture.ManifestHash) {
    $message = $null
    try { [void](Invoke-Fixture $Fixture $InventoryHash $ManifestHash) } catch { $message = $_.Exception.Message }
    Check ($null -ne $message -and $message -like $Pattern) "$script:mode refused with '$Pattern' (got '$message')"
    $path = Join-Path $Fixture.Directory "$script:stage-result.json"
    Check ([IO.File]::Exists($path) -eq $ResultExpected) "$script:mode result retention"
    Check (@($script:calls | Where-Object { $_ -ceq 'install' }).Count -eq 0) "$script:mode no install"
    if ($ResultExpected) {
        $record = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
        Check ($record.status -ceq 'failure' -and $record.reason -ceq $message) "$script:mode failed result reason"
        return $record
    }
}

# Success.
$fixture = New-Fixture 'success'
$out = Invoke-Fixture $fixture
$record = Get-Content -LiteralPath $out.ResultPath -Raw | ConvertFrom-Json
Check ($record.status -ceq 'terminated' -and $out.Record.status -ceq 'terminated') 'success terminated'
Check ($record.schema -ceq 'nene-pixel-legacy-termination-v1' -and
    $record.session -ceq $script:P4LegacySession) 'schema'
Check ($record.identical_original_files -eq $script:identicalCount) 'identical original files'
Check (@($record.markers).Count -eq 2 -and @($record.exceptions).Count -eq 2) 'markers and exceptions'
Check ((@($record.exceptions | ForEach-Object { $_.kind }) -join ',') -ceq
    'profile-installer-markers-retained,fractional-mtime-unproven') 'exception kinds'
foreach ($marker in $record.markers) {
    Check ($marker.original_sha256 -cne $marker.current_sha256) "marker $($marker.path) differs from original"
}
$plan = Get-Content -LiteralPath $record.plan_path -Raw | ConvertFrom-Json
Check ((@($plan.Directories) -join '|') -ceq ($script:directories -join '|')) 'plan directories'
Check ((Get-P4SnapshotHash $record.plan_path) -ceq $record.plan_sha256) 'plan hash'
$names = @(0..($script:directories.Count - 1) | ForEach-Object { "mkdir-$_" }) +
    @('measurement-move-0', 'original-move-0')
Check (@($record.steps).Count -eq $script:directories.Count + 2) 'step count'
Check ((@($record.steps | ForEach-Object { $_.name }) -join ',') -ceq ($names -join ',')) 'step order'
foreach ($step in $record.steps) {
    $stepResult = Get-Content -LiteralPath $step.result_path -Raw | ConvertFrom-Json
    Check ($step.status -ceq 'success' -and $stepResult.status -ceq 'success') "$($step.name) success"
    Check ((Get-P4SnapshotHash $step.result_path) -ceq $step.result_sha256 -and
        (Get-P4SnapshotHash $step.intent_path) -ceq $step.intent_sha256) "$($step.name) hashes"
}
Check ($record.final_apk_sha256 -ceq $script:apkHash -and
    $record.expected_installed_apk_sha256 -ceq $script:apkHash) 'APK'
Check ((Get-P4SnapshotHash $record.pre_inventory_path) -ceq $record.pre_inventory_sha256) 'pre inventory hash'
Check ((Get-P4SnapshotHash $record.final_inventory_path) -ceq $record.final_inventory_sha256) 'final inventory hash'
$final = Get-Content -LiteralPath $record.final_inventory_path -Raw | ConvertFrom-Json
Check (@($final).Count -eq $fixture.Count + $script:directories.Count -and
    $script:map.Count -eq @($final).Count) 'final map count'
Check ($script:map[$script:P4LegacyRecovery].Hash -ceq $script:h0) 'original recovery at live path'
Check ($script:map[$script:measured].Hash -ceq $script:h1) 'measurement copy archived'
Check (-not $script:map.ContainsKey($script:guarded) -and
    $script:map[$script:P4LegacyGuard].Type -ceq 'directory') 'guard'
Check ($script:map['no_backup/x'].Hash -ceq (Get-TextHash 'unrelated')) 'unrelated file untouched'
for ($k = 0; $k -lt 2; $k++) {
    $marker = $script:P4LegacyMarkers[$k]
    Check ($script:map[$marker].Hash -ceq (Get-TextHash "marker-current-$k")) "$marker retained"
}
Check (@(Get-Mutations).Count -eq $names.Count) 'mutation count'
Check (@($script:scripts | Where-Object { $_ -match '\brm\b' }).Count -eq 0) 'no rm'
Check (@($script:calls | Where-Object { $_ -ceq 'install' }).Count -eq 0) 'no install'
$script:calls.Clear()
$refused = $false
try { [void](Invoke-Fixture $fixture) } catch { $refused = $_.Exception.Message -ceq 'Legacy host session is occupied' }
Check ($refused -and $script:calls.Count -eq 0) 'completed termination cannot rerun'

# Host refusals: no device call, and no result record (refused before the record exists).
$hostCases = [ordered]@{
    'bad-hash-format' = 'Legacy input hash must be lowercase SHA-256'
    'inventory-hash' = 'Legacy expected inventory hash differs'
    'manifest-hash' = 'Legacy original manifest hash differs'
    'inventory-schema' = 'Legacy expected inventory binding differs'
    'manifest-state' = 'Legacy original manifest binding differs'
    'serial-mismatch' = 'Legacy expected inventory binding differs'
    'guard-mismatch' = 'Legacy original manifest binding differs'
    'guarded-differs' = 'Legacy guarded recovery differs from original'
    'live-ambiguous' = 'Legacy live recovery is ambiguous'
    'marker-missing' = 'Legacy marker missing'
    'original-path-missing' = 'Legacy original path missing: files/zz-missing'
    'original-file-changed' = 'Legacy original file changed: files/f-001'
    'identical-count' = 'Legacy identical count differs'
    'archive-occupied' = 'Legacy archive occupied'
    'host-occupied' = 'Legacy host session is occupied' }
foreach ($case in $hostCases.Keys) {
    $fixture = New-Fixture $case
    $inventoryHash = $fixture.InventoryHash; $manifestHash = $fixture.ManifestHash
    if ($case -eq 'bad-hash-format') { $inventoryHash = $inventoryHash.ToUpperInvariant() }
    if ($case -eq 'inventory-hash') { $inventoryHash = 'f' * 64 }
    if ($case -eq 'manifest-hash') { $manifestHash = 'f' * 64 }
    [void](Expect-Refusal $fixture $hostCases[$case] $false $inventoryHash $manifestHash)
    Check ($script:calls.Count -eq 0) "$case before device calls"
}

# Device refusals: a failure record is retained.
$deviceCases = [ordered]@{
    'live-process' = 'Native capture invalid: stopped-before'
    'apk-mismatch' = 'Installed APK differs from expected'
    'pre-drift' = 'Changed file identity: files/f-001'
    'mkdir-failure' = 'Synthetic mutation failure'
    'move-failure' = 'Mutation command emitted stdout'
    'bad-readback' = "Missing entry: $script:P4LegacyRecovery"
    'final-apk-drift' = 'Final installed APK differs from expected' }
foreach ($case in $deviceCases.Keys) {
    $fixture = New-Fixture $case
    $record = Expect-Refusal $fixture $deviceCases[$case] $true
    $mutations = @(Get-Mutations)
    switch ($case) {
        'live-process' {
            $apkCalls = @($script:calls | Where-Object { $_.StartsWith('apk:') })
            Check ($mutations.Count -eq 0 -and $apkCalls.Count -eq 0) "$case before APK and mutation"
        }
        'apk-mismatch' {
            $inventoryCalls = @($script:calls | Where-Object { $_.StartsWith('inventory:') })
            Check ($mutations.Count -eq 0 -and $inventoryCalls.Count -eq 0) "$case before inventory and mutation"
        }
        'pre-drift' { Check ($mutations.Count -eq 0 -and @($record.steps).Count -eq 0) "$case no move" }
        'final-apk-drift' {
            Check (@($record.steps | Where-Object { $_.status -cne 'success' }).Count -eq 0) "$case steps"
            Check ($record.final_apk_sha256 -ceq ('f' * 64) -and $null -eq $record.final_inventory_path) "$case record"
        }
        default {
            $failed = @{ 'mkdir-failure' = 'mkdir-0'; 'move-failure' = 'measurement-move-0'
                'bad-readback' = 'original-move-0' }[$case]
            $last = @($record.steps)[-1]
            $stepResult = Get-Content -LiteralPath $last.result_path -Raw | ConvertFrom-Json
            Check ($last.name -ceq $failed -and $last.status -ceq 'failure' -and
                $stepResult.status -ceq 'failure') "$case failed step retained"
            Check ($mutations[-1] -ceq "mutation:$failed") "$case stops at failed step"
        }
    }
}

$summary = [ordered]@{ schema = 'nene-pixel-p4-legacy-termination-host-validator-v1'; status = 'pass'
    checks = $script:checks; cases = @(@('success') + @($hostCases.Keys) + @($deviceCases.Keys))
    source_sha256 = [ordered]@{
        executor = Get-P4SnapshotHash (Join-Path $PSScriptRoot 'measurements/p4-device-private-legacy-termination.ps1')
        validator = Get-P4SnapshotHash $PSCommandPath } }
Write-P4SessionJson (Join-Path $OutputDirectory 'validation.json') $summary
Write-Output "PASS: $($script:checks) legacy termination checks. Evidence: $OutputDirectory"

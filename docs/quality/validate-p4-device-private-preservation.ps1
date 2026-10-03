Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/p4-device-private-preservation.ps1')

$script:checks = 0
$hashA = 'a' * 64
$hashB = 'b' * 64
function MakeDir([string] $path) { [pscustomobject]@{ Path = $path; Type = 'directory' } }
function File([string] $path, [string] $hash = $hashA, [string] $mtime = '1720000000123456789') {
    [pscustomobject]@{ Path = $path; Type = 'file'; Size = [long] 7; Hash = $hash; MtimeNs = $mtime; LinkCount = 1 }
}
function Clone($items) { @($items | ForEach-Object { $_ | Select-Object * }) }
function KeyMap($items) {
    $m = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
    foreach ($item in $items) { if ($m.ContainsKey($item.Path)) { throw "model duplicate: $($item.Path)" }; $m.Add($item.Path, ($item | Select-Object *)) }
    return $m
}
function EnsureParent($map, [string] $path) {
    $pieces = $path.Split('/')
    for ($i = 1; $i -lt $pieces.Length; $i++) {
        $parent = $pieces[0..($i - 1)] -join '/'
        if (-not $map.ContainsKey($parent)) { $map.Add($parent, (MakeDir $parent)) }
        elseif ($map[$parent].Type -cne 'directory') { throw "model parent collision: $parent" }
    }
}
function ModelMove($items, [string] $source, [string] $destination) {
    $map = KeyMap $items
    if (-not $map.ContainsKey($source) -or $map.ContainsKey($destination)) { throw "model move collision: $source -> $destination" }
    $moving = @($map.Keys | Where-Object { $_ -ceq $source -or $_.StartsWith("$source/", [System.StringComparison]::Ordinal) })
    EnsureParent $map $destination
    foreach ($path in $moving) {
        $copy = $map[$path] | Select-Object *
        $copy.Path = $destination + $path.Substring($source.Length)
        if ($map.ContainsKey($copy.Path)) { throw "model descendant collision: $($copy.Path)" }
        $map.Add($copy.Path, $copy)
    }
    foreach ($path in $moving) { $map.Remove($path) | Out-Null }
    return @($map.Values)
}
function Check([bool] $condition, [string] $label) {
    $script:checks++
    if (-not $condition) { throw "FAIL: $label" }
}
function Reject([scriptblock] $action, [string] $label) {
    $threw = $false
    try { & $action | Out-Null } catch { $threw = $true }
    Check $threw $label
}
function VerifyIdentity($expected, $actual, [string] $label) {
    $e = KeyMap $expected; $a = KeyMap $actual
    Check ($e.Count -eq $a.Count) "$label count"
    foreach ($path in $e.Keys) {
        Check ($a.ContainsKey($path)) "$label path $path"
        if ($e[$path].Type -ceq 'file') {
            Check ($a[$path].Hash -ceq $e[$path].Hash -and $a[$path].MtimeNs -ceq $e[$path].MtimeNs -and $a[$path].Size -eq $e[$path].Size) "$label identity $path"
        }
    }
}
function RunScenario([string] $name, $original, $created) {
    $session = 's-145-test'
    $isolation = New-P4IsolationPlan $original $session
    $isolated = Clone $original
    foreach ($move in $isolation.Moves) { $isolated = ModelMove $isolated $move.Source $move.Destination }
    $model = KeyMap $isolated
    EnsureParent $model "$($isolation.Guard)/sentinel"
    $isolated = @($model.Values)
    Check (Assert-P4Isolation $original $session $isolated) "$name isolated"
    VerifyIdentity $isolation.Expected $isolated "$name isolation model"

    $live = KeyMap $isolated
    foreach ($entry in $created) {
        EnsureParent $live $entry.Path
        if ($live.ContainsKey($entry.Path)) { throw "scenario collision: $($entry.Path)" }
        $live.Add($entry.Path, $entry)
    }
    $preRestore = @($live.Values)
    $restore = New-P4RestorationPlan $original $session $preRestore
    $after = $preRestore
    foreach ($move in $restore.MeasurementMoves) { $after = ModelMove $after $move.Source $move.Destination }
    $post = KeyMap $after
    EnsureParent $post "$($restore.Archive)/sentinel"
    $after = @($post.Values)
    foreach ($move in $restore.OriginalMoves) {
        Check (-not $post.ContainsKey($move.Destination)) "$name destination vacant before original restore $($move.Destination)"
    }
    foreach ($move in $restore.OriginalMoves) { $after = ModelMove $after $move.Source $move.Destination }
    Check (Assert-P4Restored $original $session $preRestore $after) "$name restored"
    VerifyIdentity $restore.Expected $after "$name final model"
    $final = KeyMap $after
    foreach ($entry in $original) {
        Check ($final.ContainsKey($entry.Path)) "$name original path $($entry.Path)"
        if ($entry.Type -ceq 'file') { Check ($final[$entry.Path].MtimeNs -ceq $entry.MtimeNs -and $final[$entry.Path].Hash -ceq $entry.Hash) "$name original exact $($entry.Path)" }
    }
    foreach ($entry in $created) {
        $archivePath = "$($restore.Archive)/$($entry.Path)"
        Check ($final.ContainsKey($archivePath)) "$name measurement retained $archivePath"
        if ($entry.Type -ceq 'file') { Check ($final[$archivePath].Hash -ceq $entry.Hash -and $final[$archivePath].MtimeNs -ceq $entry.MtimeNs) "$name measurement exact $archivePath" }
    }
    Check ($final.ContainsKey($restore.Guard) -and @($final.Keys | Where-Object { $_.StartsWith("$($restore.Guard)/", [System.StringComparison]::Ordinal) }).Count -eq 0) "$name original guard empty"
    return [pscustomobject]@{ Isolated = $isolated; PreRestore = $preRestore; Restored = $after; Plan = $restore }
}

$original = @(
    (MakeDir 'files'), (File 'files/original.nenepixel'),
    (File 'files/profileinstaller_profileWrittenFor_lastUpdateTime.dat' $hashA '1720000000123456788'),
    (File 'files/profileInstalled' $hashA '1720000000123456787'),
    (MakeDir 'no_backup'), (MakeDir 'no_backup/p4-user-preservation'),
    (MakeDir 'no_backup/p4-user-preservation/old'), (File 'no_backup/p4-user-preservation/old/saved'),
    (MakeDir 'no_backup/reference-underlays'),
    (File 'no_backup/reference-underlays/12345678901234567890123456789012.image'),
    (File 'no_backup/reference-underlays/12345678901234567890123456789012.state' $hashB '1720000000987654321'),
    (File 'no_backup/nene-pixel-recovery-v1'),
    (File 'no_backup/nene-pixel-recovery-v1.new'),
    (File 'no_backup/nene-pixel-recovery-v1.bak'),
    (MakeDir 'shared_prefs'), (MakeDir 'databases')
)
$created = @(
    (File 'files/profileinstaller_profileWrittenFor_lastUpdateTime.dat' $hashB '1720000000888888887'),
    (File 'files/profileInstalled' $hashB '1720000000888888886'),
    (File 'no_backup/nene-pixel-recovery-v1' $hashB '1720000000888888888'),
    (MakeDir 'no_backup/reference-underlays'), (File 'no_backup/reference-underlays/new.image' $hashB),
    (MakeDir 'no_backup/p4-quarantine'), (File 'no_backup/p4-quarantine/slot-output' $hashB),
    (MakeDir 'files/new-subdir'), (File 'files/new-subdir/output' $hashB)
)
$main = RunScenario 'present complete' $original $created
Check ((KeyMap $main.Restored)['no_backup/p4-user-preservation/old/saved'].Hash -ceq $hashA) 'old guard unchanged'
Check ($main.Plan.OriginalMoves.Count -eq 6) 'whole underlay plus complete recovery and profile sets'
Check ($main.Plan.MeasurementMoves.Count -eq 6) 'highest new measurement ancestors'
Check (@($main.Plan.MeasurementMoves | Where-Object { $_.Source -ceq 'no_backup/reference-underlays' }).Count -eq 1) 'new underlay moved as a unit'
foreach ($name in @('profileinstaller_profileWrittenFor_lastUpdateTime.dat', 'profileInstalled')) {
    $source = "files/$name"
    $guarded = "no_backup/p4-user-preservation/s-145-test/original/$name"
    $archived = "no_backup/p4-user-preservation/s-145-test/measurement/$source"
    Check (@($main.Plan.OriginalMoves | Where-Object { $_.Source -ceq $guarded -and $_.Destination -ceq $source }).Count -eq 1) "profile exact guard move $name"
    Check (@($main.Plan.MeasurementMoves | Where-Object { $_.Source -ceq $source -and $_.Destination -ceq $archived }).Count -eq 1) "profile replacement archive move $name"
    Check ((KeyMap $main.Restored)[$archived].Hash -ceq $hashB -and (KeyMap $main.Restored)[$source].Hash -ceq $hashA) "profile replacement retained before original $name"
}
RunScenario 'both profiles absent' @((MakeDir 'files'), (File 'files/other')) @((File 'files/profileinstaller_profileWrittenFor_lastUpdateTime.dat' $hashB), (File 'files/profileInstalled' $hashB)) | Out-Null
RunScenario 'one profile present' @((MakeDir 'files'), (File 'files/profileInstalled')) @((File 'files/profileInstalled' $hashB), (File 'files/profileinstaller_profileWrittenFor_lastUpdateTime.dat' $hashB)) | Out-Null
RunScenario 'empty underlay' @((MakeDir 'no_backup'), (MakeDir 'no_backup/reference-underlays')) @((File 'no_backup/nene-pixel-recovery-v1' $hashB)) | Out-Null
RunScenario 'absent underlay' @((MakeDir 'no_backup'), (File 'no_backup/nene-pixel-recovery-v1.bak')) @((MakeDir 'no_backup/reference-underlays'), (File 'no_backup/reference-underlays/new.state' $hashB)) | Out-Null
$absent = RunScenario 'absent no_backup' @((MakeDir 'files'), (File 'files/saved')) @((File 'no_backup/nene-pixel-recovery-v1' $hashB), (MakeDir 'shared_prefs'), (File 'shared_prefs/new' $hashB))
Check $absent.Plan.OriginallyAbsentNoBackup 'absent no_backup exception reported'
RunScenario 'empty inventory' @() @() | Out-Null

$damaged = Clone $main.Isolated
($damaged | Where-Object Path -eq 'files/original.nenepixel').Hash = $hashB
Reject { New-P4RestorationPlan $original 's-145-test' $damaged } 'byte drift'
$damaged = Clone $main.Isolated
($damaged | Where-Object Path -eq 'no_backup/p4-user-preservation/s-145-test/original/reference-underlays/12345678901234567890123456789012.state').MtimeNs = '1720000000987654322'
Reject { New-P4RestorationPlan $original 's-145-test' $damaged } 'timestamp-only drift'
$damaged = Clone $main.Isolated
($damaged | Where-Object Path -eq 'no_backup/p4-user-preservation/s-145-test/original/profileInstalled').MtimeNs = '1720000000123456789'
Reject { New-P4RestorationPlan $original 's-145-test' $damaged } 'guarded profile timestamp drift'
$damaged = Clone $main.Isolated
($damaged | Where-Object Path -eq 'no_backup/p4-user-preservation/s-145-test/original/profileinstaller_profileWrittenFor_lastUpdateTime.dat').Hash = $hashB
Reject { New-P4RestorationPlan $original 's-145-test' $damaged } 'guarded profile content drift'
$damaged = @(Clone $main.Isolated | Where-Object Path -ne 'files/original.nenepixel')
Reject { New-P4RestorationPlan $original 's-145-test' $damaged } 'missing original'
$damaged = @(Clone $main.Isolated) + (File 'no_backup/p4-user-preservation/s-145-test/unexpected')
Reject { New-P4RestorationPlan $original 's-145-test' $damaged } 'unexpected session entry'
$damaged = @(Clone $main.Isolated | Where-Object Path -ne 'no_backup/p4-user-preservation/s-145-test/original/nene-pixel-recovery-v1') + (File 'no_backup/nene-pixel-recovery-v1')
Reject { New-P4RestorationPlan $original 's-145-test' $damaged } 'partial isolation'
$damaged = @(Clone $main.PreRestore) + (MakeDir 'no_backup/p4-user-preservation/s-145-test/measurement')
Reject { New-P4RestorationPlan $original 's-145-test' $damaged } 'occupied archive'
$damaged = @(Clone $main.PreRestore | Where-Object Path -ne 'no_backup/p4-user-preservation/s-145-test/original/nene-pixel-recovery-v1')
Reject { New-P4RestorationPlan $original 's-145-test' $damaged } 'partial restore'
$ambiguous = @(Clone $main.PreRestore | Where-Object Path -cne 'no_backup/nene-pixel-recovery-v1') + (File 'no_backup/nene-pixel-recovery-v1')
$guardPath = 'no_backup/p4-user-preservation/s-145-test/original/nene-pixel-recovery-v1'
Check ((KeyMap $ambiguous).ContainsKey($guardPath) -and (KeyMap $ambiguous)['no_backup/nene-pixel-recovery-v1'].Hash -ceq $hashA) 'ambiguous restore retains guarded original and recreates identical live recovery file'
Reject { New-P4RestorationPlan $original 's-145-test' $ambiguous } 'ambiguous identical live original'
$damaged = @(Clone $main.Restored | Where-Object Path -ne 'no_backup/reference-underlays/12345678901234567890123456789012.image')
Reject { Assert-P4Restored $original 's-145-test' $main.PreRestore $damaged } 'missing restored original'
$damaged = Clone $main.Restored
($damaged | Where-Object Path -eq 'no_backup/reference-underlays/12345678901234567890123456789012.state').MtimeNs = '1720000000987654322'
Reject { Assert-P4Restored $original 's-145-test' $main.PreRestore $damaged } 'restored timestamp drift'
$damaged = Clone $main.Restored
($damaged | Where-Object Path -eq 'no_backup/p4-user-preservation/s-145-test/measurement/files/new-subdir/output').Hash = $hashA
Reject { Assert-P4Restored $original 's-145-test' $main.PreRestore $damaged } 'measurement archive byte drift'
$damaged = @(Clone $main.Restored) + (File 'files/unplanned')
Reject { Assert-P4Restored $original 's-145-test' $main.PreRestore $damaged } 'unexpected restored entry'
Reject { New-P4IsolationPlan (@($original) + (MakeDir 'no_backup/p4-user-preservation/s-145-test')) 's-145-test' } 'guard collision'
Reject { New-P4IsolationPlan @((MakeDir 'files'), (File 'files/x'), (File 'files/x')) 'safe' } 'duplicate path'
Check ((New-P4IsolationPlan @((MakeDir 'files'), (File 'files/X'), (File 'files/x')) 'safe').Original.Count -eq 3) 'paths are case-sensitive'
Check ((New-P4IsolationPlan @((MakeDir 'files'), ([pscustomobject]@{ Path='files/json'; Type='file'; Size=[long]7; Hash=$hashA; MtimeNs='1'; LinkCount=[long]1 })) 'safe').Original.Count -eq 2) 'JSON long link count accepted'
Reject { New-P4IsolationPlan @((MakeDir 'files')) 'bad/id' } 'bad session'
Reject { New-P4IsolationPlan @((File 'files/x')) 'safe' } 'missing parent'
Reject { New-P4IsolationPlan @((MakeDir 'files'), ([pscustomobject]@{ Path=17; Type='file'; Size=[long]7; Hash=$hashA; MtimeNs='1'; LinkCount=1 })) 'safe' } 'nonstring path'
Reject { New-P4IsolationPlan @((MakeDir 'files'), (File 'files/../x')) 'safe' } 'unsafe path'
Reject { New-P4IsolationPlan @((MakeDir 'files'), (File 'files/UPPER' ('A' * 64))) 'safe' } 'noncanonical hash'
Reject { New-P4IsolationPlan @((MakeDir 'files'), (File 'files/mtime' $hashA '1.0')) 'safe' } 'nondecimal mtime'
Reject { New-P4IsolationPlan @((MakeDir 'files'), ([pscustomobject]@{ Path='files/link'; Type='symlink' })) 'safe' } 'symlink'
Reject { New-P4IsolationPlan @((MakeDir 'files'), ([pscustomobject]@{ Path='files/hard'; Type='file'; Size=[long]7; Hash=$hashA; MtimeNs='1'; LinkCount=2 })) 'safe' } 'hardlink'
Reject { New-P4IsolationPlan @((MakeDir 'no_backup'), (File 'no_backup/reference-underlays')) 'safe' } 'underlay wrong kind'
Reject { New-P4IsolationPlan @((MakeDir 'files'), (MakeDir 'files/profileInstalled')) 'safe' } 'profileInstalled wrong kind'
Reject { New-P4IsolationPlan @((MakeDir 'files'), (MakeDir 'files/profileinstaller_profileWrittenFor_lastUpdateTime.dat')) 'safe' } 'profile timestamp file wrong kind'

Write-Output "PASS preservation synthetic checks=$script:checks"

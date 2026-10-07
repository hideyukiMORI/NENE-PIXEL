param([string] $OutputDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/p4-device-private-native.ps1')

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Get-NenePixelLabPath ('evidence/145-encoded-native/' +
        (Get-Date -Format 'yyyyMMddTHHmmssfff') + '-' + [guid]::NewGuid().ToString('N')) -StartDirectory $PSScriptRoot
}
if ([IO.Directory]::Exists($OutputDirectory) -or [IO.File]::Exists($OutputDirectory)) { throw 'Evidence directory exists' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$timer = [Diagnostics.Stopwatch]::StartNew()
$script:checks = 0
$script:firstFailures = [Collections.Generic.List[string]]::new()
$script:requests = [Collections.Generic.List[object]]::new()
$script:case = 'normal'
$script:nextOutput = $OutputDirectory

function Check([bool] $Condition, [string] $Description) {
    if (-not $Condition) { throw "Native validator: $Description" }
    $script:checks++
}
function Reject([scriptblock] $Action, [string] $Description) {
    $failed = $false
    try { & $Action | Out-Null } catch { $failed = $true; $script:firstFailures.Add("$Description : $($_.Exception.Message)") }
    Check $failed "$Description must refuse"
}
function Bytes([string] $Text) { return ,([Text.UTF8Encoding]::new($false).GetBytes($Text)) }
function Encoded([byte[]] $Decoded) {
    $base64 = [Convert]::ToBase64String($Decoded)
    $lines = [Collections.Generic.List[string]]::new()
    for ($i = 0; $i -lt $base64.Length; $i += 64) {
        $lines.Add($base64.Substring($i, [Math]::Min(64, $base64.Length - $i)))
    }
    return ,(Bytes $(if ($lines.Count -eq 0) { '' } else { ($lines -join "`r`n") + "`r`n" }))
}
function Paths([string[]] $Names) {
    $list = [Collections.Generic.List[byte]]::new()
    foreach ($name in $Names) { $list.AddRange((Bytes $name)); $list.Add(0) }
    return ,$list.ToArray()
}
function New-Case([string] $Name) {
    $directory = Join-Path $OutputDirectory $Name
    [void][IO.Directory]::CreateDirectory($directory)
    $script:case = $Name
    return @{ repository_root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path;
        output_directory = $directory; adb_path = (Get-Command pwsh).Source;
        serial = 'serial-01:5555'; package = 'io.github.hideyukimori.nenepixel' }
}

# A synthetic raw transport: it reserves the same three files and retains a result on failure.
# No process, adb, or device command is launched by this validator.
function Invoke-P4RawAdbCapture {
    param([string] $AdbPath, [string[]] $AdbArguments, [int] $TimeoutSeconds,
        [string] $DestinationPath, [string] $RecordPath, [long] $MaximumBytes)
    $leaf = [IO.Path]::GetFileName($DestinationPath)
    $step = $leaf.Substring(($script:case + '-').Length) -creplace '\.bin(\.base64)?$', ''
    $private = $leaf.EndsWith('.bin.base64', [StringComparison]::Ordinal)
    $script:requests.Add([pscustomobject]@{ Case = $script:case; Step = $step; Arguments = @($AdbArguments);
        Bound = $MaximumBytes; Timeout = $TimeoutSeconds })
    $names = @('files', 'files/a-b_1', 'files/zero', 'no_backup', 'shared_prefs', 'databases')
    $mtime = '2024-07-03 09:46:40.123456789 +0000'
    $meta = @("files|41ed|2|0|1720000000|$mtime", "files/a-b_1|81a4|1|4|1720000000|$mtime",
        "files/zero|81a4|1|0|1720000000|$mtime", "no_backup|41ed|2|0|1720000000|$mtime",
        "shared_prefs|41ed|2|0|1720000000|$mtime", "databases|41ed|2|0|1720000000|$mtime")
    $hash = @((('a' * 64) + '  files/a-b_1'), (('b' * 64) + '  files/zero'))
    if ($script:case -eq 'long-digest') {
        $names = $script:longNames
        $meta = $script:longMeta
        $hash = $script:longHash
    }
    $exit = 0; $stderr = ''; $status = 'success'; $errorText = $null
    $timeout = $false; $cap = $false
    $data = switch -Regex ($step) {
        '^features$' { Bytes "shell_v2`nstat_v2`n"; break }
        '^shell-proof$' { $exit = 73; $stderr = 'p4-err'; Bytes 'p4-out'; break }
        '^encoded-upstream-proof$' { $exit = 73; $stderr = 'p4-err'; Bytes ''; break }
        '^encoded-byte-proof$' { [byte[]]@(0..255); break }
        '^stopped-' { $exit = 1; Bytes ''; break }
        '^paths-' { Paths $names; break }
        '^stat-[0-9]+$' {
            if ($script:case -eq 'long-digest') {
                $batch = (New-P4NativeBatches $names)[[int]($step.Substring(5))]
                $lines = @($batch | ForEach-Object { $meta[[array]::IndexOf($names, $_)] })
                Bytes (($lines -join "`n") + "`n")
            } else { Bytes (($meta -join "`n") + "`n") }
            break
        }
        '^hash-[0-9]+$' {
            if ($script:case -eq 'long-digest') {
                $batch = (New-P4NativeBatches $script:longFiles)[[int]($step.Substring(5))]
                $lines = @($batch | ForEach-Object { $hash[[array]::IndexOf($script:longFiles, $_)] })
                Bytes (($lines -join "`n") + "`n")
            } else { Bytes (($hash -join "`n") + "`n") }
            break
        }
        default { throw "Unexpected mock step: $step" }
    }
    switch ($script:case) {
        'host-crlf-features' { if ($step -eq 'features') { $data = Bytes "shell_v2`r`nstat_v2`r`n" } }
        'bare-cr-features' { if ($step -eq 'features') { $data = Bytes "shell_v2`rstat_v2`n" } }
        'remote-crlf-stat' { if ($step -eq 'stat-0') { $data = Bytes (($meta -join "`r`n") + "`r`n") } }
        'remote-crlf-hash' { if ($step -eq 'hash-0') { $data = Bytes (($hash -join "`r`n") + "`r`n") } }
        'empty' { if ($step -like 'paths-*') { $data = [byte[]]::new(0) } }
        'missing-shell' { if ($step -eq 'features') { $data = Bytes "stat_v2`n" } }
        'bad-proof-exit' { if ($step -eq 'shell-proof') { $exit = 72 } }
        'bad-proof-stdout' { if ($step -eq 'shell-proof') { $data = Bytes 'wrong' } }
        'bad-proof-stderr' { if ($step -eq 'shell-proof') { $stderr = 'wrong' } }
        'bad-byte-proof' { if ($step -eq 'encoded-byte-proof') { $data[10] = 255 } }
        'short-byte-proof' { if ($step -eq 'encoded-byte-proof') { $data = [byte[]] $data[0..254] } }
        'bad-upstream-exit' { if ($step -eq 'encoded-upstream-proof') { $exit = 72 } }
        'bad-upstream-stderr' { if ($step -eq 'encoded-upstream-proof') { $stderr = 'wrong' } }
        'running' { if ($step -eq 'stopped-before') { $exit = 0; $data = Bytes "123`n" } }
        'undetermined' { if ($step -eq 'stopped-before') { $exit = 2 } }
        'command-failure' { if ($step -eq 'stat-0') { $exit = 1; $stderr = 'permission denied' } }
        'timeout' { if ($step -eq 'paths-before') { $timeout = $true } }
        'cap' { if ($step -eq 'paths-before') { $cap = $true } }
        'drift' { if ($step -eq 'paths-after') { $data = Paths @('files','files/a-b_1','files/zero','no_backup','databases') } }
        'bad-metadata' { if ($step -eq 'stat-0') { $data = Bytes (($meta[0..4] -join "`n") + "`n") } }
        'extra-metadata' { if ($step -eq 'stat-0') { $data = Bytes ((($meta + "files/extra|81a4|1|0|1720000000|$mtime") -join "`n") + "`n") } }
        'bad-hash' { if ($step -eq 'hash-0') { $data = Bytes ($hash[0] + "`n") } }
        'unsafe-path' { if ($step -eq 'paths-before') { $data = Paths @('files','files/bad name') } }
        'late-running' { if ($step -eq 'stopped-after') { $exit = 0; $data = Bytes "123`n" } }
    }
    if ($private) { $data = Encoded $data }
    if ($script:case -eq 'corrupt-encoded-byte-proof' -and $step -eq 'encoded-byte-proof') {
        $data[5] = [byte][char]'!'
    }
    if ($data.Length -gt $MaximumBytes) {
        $cap = $true
        $data = [byte[]] $data[0..([int]$MaximumBytes - 1)]
    }
    if ($exit -ne 0 -or $timeout -or $cap) {
        $status = 'failure'
        $errorText = if ($exit -ne 0) { "The private-file capture failed ($exit): $stderr" }
            else { 'synthetic raw capture failure' }
    }
    $destination = [IO.Path]::GetFullPath($DestinationPath)
    $record = [IO.Path]::GetFullPath($RecordPath)
    foreach ($path in @($destination, $record, "$record.command.json")) {
        if ([IO.File]::Exists($path) -or [IO.Directory]::Exists($path)) {
            throw "Capture output path already exists: $path"
        }
    }
    $stream = [IO.File]::Open($destination, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try { $stream.Write($data, 0, $data.Length) } finally { $stream.Dispose() }
    $result = [ordered]@{ schema = 'nene-pixel-p4-binary-capture-result-v1'; status = $status;
        exit_code = $exit; error = $errorText; stderr = $stderr; destination_path = $destination;
        byte_count = $data.Length; sha256 = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($data)).ToLowerInvariant();
        command_record_path = "$record.command.json"; timed_out = $timeout; byte_limit_exceeded = $cap;
        job_quiescent_after_failure = $(if ($status -eq 'failure') { $true } else { $null }) }
    if ($script:case -eq 'no-quiescence') {
        $result.job_quiescent_after_failure = $false
        $result.error = "$errorText Job quiescence was not confirmed."
    }
    $recordStream = [IO.File]::Open($record, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try {
        $encoded = Bytes ($result | ConvertTo-Json -Depth 5)
        $recordStream.Write($encoded, 0, $encoded.Length)
    } finally { $recordStream.Dispose() }
    $commandStream = [IO.File]::Open("$record.command.json", [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try {
        $encoded = Bytes (@{ arguments = $AdbArguments } | ConvertTo-Json -Depth 5)
        $commandStream.Write($encoded, 0, $encoded.Length)
    } finally { $commandStream.Dispose() }
    if ($script:case -like 'race-*') { throw 'Capture output path already exists: synthetic race' }
    if ($status -eq 'failure') { throw $result.error }
    return [pscustomobject]$result
}

$normal = New-Case 'normal'
$inventory = Get-P4NativePrivateInventory -Context $normal -Stage 'normal'
Check ($inventory.Count -eq 6 -and @($inventory | Where-Object Type -EQ 'file').Count -eq 2) 'populated roots and empty file'
Check (($inventory | Where-Object Path -EQ 'files/zero').Size -eq 0) 'zero-byte file'
$normalRequests = @($script:requests | Where-Object Case -EQ 'normal')
Check (($normalRequests.Step -join ',') -ceq 'features,shell-proof,stopped-before,encoded-byte-proof,encoded-upstream-proof,paths-before,stat-0,hash-0,paths-after,stopped-after') 'command sequence'
Check (@($normalRequests | Where-Object { $_.Arguments[0] -cne '-s' -or $_.Arguments[1] -cne 'serial-01:5555' }).Count -eq 0) 'serial prefix'
Check (@($normalRequests | Where-Object { $_.Step -ne 'features' -and ($_.Arguments[2..4] -join ',') -cnotlike 'shell,-T,-n*' }).Count -eq 0) 'shell T n flags'
$statCommand = @($normalRequests | Where-Object Step -EQ 'stat-0')[0].Arguments[-1]
Check ($statCommand -clike "sh -c 'set -o pipefail || exit; { run-as *" -and
    $statCommand.Contains('stat ') -and $statCommand.Contains('files/a-b_1') -and
    $statCommand.Contains(' | base64') -and -not $statCommand.Contains('rm ')) 'encoded run-as quoted stat'
Check (@($normalRequests | Where-Object { $_.Step -in @('encoded-byte-proof','paths-before','stat-0','hash-0','paths-after') -and
    $_.Arguments[-1] -cnotlike "sh -c 'set -o pipefail || exit; { * | base64'" }).Count -eq 0) `
    'all binary native commands use encoded wrapper'
Check (@($normalRequests | Where-Object { $_.Step -eq 'encoded-upstream-proof' -and
    $_.Arguments[-1] -cnotlike "sh -c 'set -o pipefail || exit; { printf p4-err >&2; exit 73; } | base64'" }).Count -eq 0) `
    'upstream proof exercises encoded pipefail wrapper'
$byteCommand = @($normalRequests | Where-Object Step -EQ 'encoded-byte-proof')[0].Arguments[-1]
$octets = [regex]::Matches($byteCommand, '\\[0-3][0-7][0-7]')
$octalSequence = @($octets | ForEach-Object { [Convert]::ToInt32($_.Value.Substring(1), 8) })
Check ($octets.Count -eq 256 -and ($octalSequence -join ',') -ceq ((0..255) -join ',')) `
    'remote probe command enumerates all 256 octal bytes'
Check (([IO.File]::ReadAllBytes((Join-Path $normal.output_directory 'normal-encoded-byte-proof.bin')) -join ',') -ceq
    ([byte[]]@(0..255) -join ',')) 'encoded probe recovers every byte'
Check ([IO.File]::Exists((Join-Path $normal.output_directory 'normal-paths-before.bin.base64')) -and
    [IO.File]::Exists((Join-Path $normal.output_directory 'normal-paths-before.json.encoded.json'))) `
    'encoded and decoded evidence retained'
Check (@($normalRequests | Where-Object { $_.Timeout -ne 30 -or $_.Bound -le 0 }).Count -eq 0) 'bounded captures'
$hostCrLf = New-Case 'host-crlf-features'
$hostCrLfInventory = Get-P4NativePrivateInventory -Context $hostCrLf -Stage 'host-crlf-features'
Check ($hostCrLfInventory.Count -eq $inventory.Count -and
    @($hostCrLfInventory | Where-Object Type -EQ 'file').Count -eq 2) 'host CRLF features complete normal observation'
Check ((@($script:requests | Where-Object Case -EQ 'host-crlf-features').Step -join ',') -ceq
    ($normalRequests.Step -join ',')) 'host CRLF features retain full command sequence'
$script:longFiles = [string[]] @(0..127 | ForEach-Object { 'files/f{0:D3}_{1}' -f $_, ('x' * 145) })
$script:longNames = [string[]] (@('files') + $script:longFiles)
$longMtime = '2024-07-03 09:46:40.123456789 +0000'
$script:longMeta = [string[]] (@("files|41ed|2|0|1720000000|$longMtime") + @(
    $script:longFiles | ForEach-Object { "$_|81a4|1|0|1720000000|$longMtime" }))
$script:longHash = [string[]] @($script:longFiles | ForEach-Object { ('c' * 64) + '  ' + $_ })
$long = New-Case 'long-digest'
$longInventory = Get-P4NativePrivateInventory -Context $long -Stage 'long-digest'
Check ($longInventory.Count -eq 129 -and @($longInventory | Where-Object Type -EQ 'file').Count -eq 128) `
    'long digest retains every file and parent'
Check (@($longInventory | Where-Object { $_.Type -eq 'file' -and
    ($_.Hash -cne ('c' * 64) -or $script:longFiles -cnotcontains $_.Path) }).Count -eq 0) `
    'long digest retains exact SHA-256 inventory'
$longRequests = @($script:requests | Where-Object Case -EQ 'long-digest')
Check (@($longRequests | Where-Object Step -Like 'stat-*').Count -eq 2 -and
    @($longRequests | Where-Object Step -Like 'hash-*').Count -eq 2) 'long paths split into bounded batches'
Check (@($longRequests | Where-Object { $_.Step -like 'hash-*' -and
    $_.Bound -eq (Get-P4EncodedMaximum 65536) }).Count -eq 2) `
    'long digest requests encoded bound for 64 KiB decoded cap'
$longHashOutputs = @(Get-ChildItem -LiteralPath $long.output_directory -Filter 'long-digest-hash-*.bin')
Check ($longHashOutputs.Count -eq 2 -and @($longHashOutputs | Where-Object {
    $_.Length -gt 16384 -and $_.Length -le 65536 }).Count -ge 1) `
    'long digest exceeds old cap and fits new cap'
$empty = New-Case 'empty'
$emptyInventory = Get-P4NativePrivateInventory -Context $empty -Stage 'empty'
Check ($emptyInventory.Count -eq 0) 'all roots absent'
Check (@($script:requests | Where-Object { $_.Case -eq 'empty' -and $_.Step -like 'stat-*' }).Count -eq 0) 'empty skips stat/hash'

foreach ($name in @('missing-shell','bad-proof-exit','bad-proof-stdout','bad-proof-stderr',
        'bad-byte-proof','short-byte-proof','corrupt-encoded-byte-proof',
        'bad-upstream-exit','bad-upstream-stderr',
        'running','undetermined','command-failure','timeout','cap','drift','bad-metadata',
        'extra-metadata','bad-hash','unsafe-path','late-running')) {
    $context = New-Case $name
    Reject { Get-P4NativePrivateInventory -Context $context -Stage $name } $name
    if ($name -in @('bad-byte-proof','short-byte-proof','corrupt-encoded-byte-proof',
            'bad-upstream-exit','bad-upstream-stderr')) {
        Check (@($script:requests | Where-Object { $_.Case -eq $name -and $_.Step -eq 'paths-before' }).Count -eq 0) `
            "$name refuses before enumeration"
    }
}
foreach ($scenario in @(@('bare-cr-features', 'features'), @('remote-crlf-stat', 'stat-0'),
        @('remote-crlf-hash', 'hash-0'))) {
    $name = $scenario[0]
    $context = New-Case $name
    Reject { Get-P4NativePrivateInventory -Context $context -Stage $name } $name
    Check ($script:requests[-1].Step -ceq $scenario[1]) "$name stops at $($scenario[1])"
    Check ($script:firstFailures[-1].Contains('Unexpected native line control byte')) "$name strict line refusal"
}
$bad = New-Case 'unsafe-serial'; $bad.serial = '-danger'
Reject { Get-P4NativePrivateInventory -Context $bad -Stage 'unsafe-serial' } 'unsafe serial before capture'
Check (@($script:requests | Where-Object Case -EQ 'unsafe-serial').Count -eq 0) 'invalid serial no invocation'

foreach ($expectedExit in @(73, 1)) {
    $step = if ($expectedExit -eq 73) { 'shell-proof' } else { 'stopped-before' }
    $stderr = if ($expectedExit -eq 73) { 'p4-err' } else { '' }
    $stdout = if ($expectedExit -eq 73) { 'p4-out' } else { '' }
    foreach ($kind in @('both-files', 'destination-file', 'record-file', 'command-file',
            'destination-directory', 'record-directory', 'command-directory')) {
        $name = "stale-$expectedExit-$kind"
        $context = New-Case $name
        $destination = Join-Path $context.output_directory "$name-$step.bin"
        $record = Join-Path $context.output_directory "$name-$step.json"
        $command = "$record.command.json"
        $stale = [ordered]@{ status = 'failure'; exit_code = $expectedExit;
            error = "The private-file capture failed ($expectedExit): $stderr"; stderr = $stderr;
            destination_path = [IO.Path]::GetFullPath($destination); byte_count = (Bytes $stdout).Length;
            timed_out = $false; byte_limit_exceeded = $false; job_quiescent_after_failure = $true }
        $original = @{}
        $directory = $null
        foreach ($entry in @(
                [pscustomobject]@{ Path = $destination; Data = (Bytes $stdout); Selected = ($kind -eq 'both-files' -or $kind -like 'destination-*') },
                [pscustomobject]@{ Path = $record; Data = (Bytes ($stale | ConvertTo-Json)); Selected = ($kind -eq 'both-files' -or $kind -like 'record-*') },
                [pscustomobject]@{ Path = $command; Data = (Bytes 'stale command'); Selected = ($kind -like 'command-*') })) {
            if (-not $entry.Selected) { continue }
            if ($kind -like '*-directory') {
                [void][IO.Directory]::CreateDirectory($entry.Path)
                $directory = $entry.Path
            } else {
                [IO.File]::WriteAllBytes($entry.Path, $entry.Data)
                $original[$entry.Path] = @{ Hash = (Get-FileHash -LiteralPath $entry.Path -Algorithm SHA256).Hash;
                    Bytes = [Convert]::ToBase64String([IO.File]::ReadAllBytes($entry.Path)) }
            }
        }
        $before = $script:requests.Count
        Reject { Invoke-P4NativeCapture -Context $context -Stage $name -Step $step `
            -AdbArguments @('shell', '-T', '-n', 'ignored') -MaximumBytes 64 `
            -ExpectedExit $expectedExit -ExpectedStdout $stdout -ExpectedStderr $stderr } $name
        Check ($script:requests.Count -eq $before) "$name refuses before raw call"
        foreach ($path in $original.Keys) {
            Check ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ceq $original[$path].Hash -and
                [Convert]::ToBase64String([IO.File]::ReadAllBytes($path)) -ceq $original[$path].Bytes) `
                "$name retains original bytes"
        }
        if ($null -ne $directory) { Check ([IO.Directory]::Exists($directory)) "$name retains directory" }
    }
    $name = if ($expectedExit -eq 73) { 'race-proof' } else { 'race-stopped' }
    $context = New-Case $name
    $before = $script:requests.Count
    Reject { Invoke-P4NativeCapture -Context $context -Stage $name -Step $step `
        -AdbArguments @('shell', '-T', '-n', 'ignored') -MaximumBytes 64 `
        -ExpectedExit $expectedExit -ExpectedStdout $stdout -ExpectedStderr $stderr } $name
    Check ($script:requests.Count -eq $before + 1) "$name rejects a colliding current exception"
}
$quiescence = New-Case 'no-quiescence'
Reject { Invoke-P4NativeCapture -Context $quiescence -Stage 'no-quiescence' -Step 'shell-proof' `
    -AdbArguments @('shell', '-T', '-n', 'ignored') -MaximumBytes 64 `
    -ExpectedExit 73 -ExpectedStdout 'p4-out' -ExpectedStderr 'p4-err' } 'job quiescence failure'
$timer.Stop()
$result = [ordered]@{ schema = 'nene-pixel-p4-private-native-validator-v1'; status = 'pass';
    checks = $script:checks; elapsed_milliseconds = $timer.ElapsedMilliseconds;
    native_sha256 = (Get-FileHash (Join-Path $PSScriptRoot 'measurements/p4-device-private-native.ps1') -Algorithm SHA256).Hash.ToLowerInvariant();
    observation_sha256 = (Get-FileHash (Join-Path $PSScriptRoot 'measurements/p4-device-private-observation.ps1') -Algorithm SHA256).Hash.ToLowerInvariant();
    transport_sha256 = (Get-FileHash (Join-Path $PSScriptRoot 'measurements/p4-indexed-device-lanes.ps1') -Algorithm SHA256).Hash.ToLowerInvariant();
    validator_sha256 = (Get-FileHash $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant();
    rejected_first_errors = @($script:firstFailures) }
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'validation.json'), ($result | ConvertTo-Json -Depth 5))
Write-Output "PASS: $($script:checks) native observer checks; $($timer.ElapsedMilliseconds) ms; evidence $OutputDirectory"

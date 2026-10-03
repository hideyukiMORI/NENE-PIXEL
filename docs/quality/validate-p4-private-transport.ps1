param([string] $OutputDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-device-private-transport.ps1')
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Get-NenePixelLabPath ('evidence/145-encoded-transfer/' +
        (Get-Date -Format 'yyyyMMddTHHmmssfff') + '-' + [guid]::NewGuid().ToString('N')) -StartDirectory $PSScriptRoot
}
if ([IO.File]::Exists($OutputDirectory) -or [IO.Directory]::Exists($OutputDirectory)) { throw 'Evidence directory exists.' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$timer = [Diagnostics.Stopwatch]::StartNew()
$script:checks = 0
$script:firstFailures = [Collections.Generic.List[string]]::new()
$script:calls = 0
$script:rawBytes = [byte[]]::new(0)
$script:rawExit = 0
$script:rawStderr = ''
$script:rawDelayMs = 0
$script:case = ''

function Check([bool] $Condition, [string] $Description) {
    if (-not $Condition) { throw "Encoded validator: $Description" }
    $script:checks++
}
function New-Case([string] $Name, [byte[]] $Payload) {
    $script:case = $Name
    $directory = Join-Path $OutputDirectory $Name
    [void][IO.Directory]::CreateDirectory($directory)
    $script:rawBytes = [Text.Encoding]::ASCII.GetBytes([Convert]::ToBase64String($Payload))
    $script:rawExit = 0; $script:rawStderr = ''; $script:rawDelayMs = 0
    return @{ destination = (Join-Path $directory 'decoded.bin'); record = (Join-Path $directory 'result.json') }
}
function Invoke-Case([hashtable] $Paths, [long] $MaximumBytes, [int] $TimeoutSeconds = 5) {
    return Invoke-P4EncodedShellCapture -AdbPath 'mock-adb' -Serial 'serial-01:5555' `
        -Script "printf '%s' 'quoted'" -TimeoutSeconds $TimeoutSeconds `
        -DestinationPath $Paths.destination -RecordPath $Paths.record -MaximumBytes $MaximumBytes
}
function Reject([hashtable] $Paths, [long] $MaximumBytes, [string] $Description,
    [int] $TimeoutSeconds = 5) {
    $errorText = $null
    try { [void](Invoke-Case $Paths $MaximumBytes $TimeoutSeconds) } catch { $errorText = $_.Exception.Message }
    Check ($null -ne $errorText) "$Description refuses"
    $script:firstFailures.Add("$Description : $errorText")
    $record = Get-Content -LiteralPath $Paths.record -Raw | ConvertFrom-Json
    Check ($record.status -ceq 'failure' -and $record.error -ceq $errorText) "$Description failure metadata"
    Check ([IO.File]::Exists($Paths.destination) -and [IO.File]::Exists("$($Paths.destination).base64")) "$Description partial files"
    Check ($record.sha256 -ceq (Get-FileHash -LiteralPath $Paths.destination -Algorithm SHA256).Hash.ToLowerInvariant()) "$Description partial hash"
    Check ($record.encoded_sha256 -ceq (Get-FileHash -LiteralPath "$($Paths.destination).base64" -Algorithm SHA256).Hash.ToLowerInvariant()) "$Description encoded hash"
    Check ($record.encoded_record_sha256 -ceq (Get-FileHash -LiteralPath "$($Paths.record).encoded.json" -Algorithm SHA256).Hash.ToLowerInvariant()) "$Description raw result link"
    return $record
}

# A strict synthetic raw result with the same file contract as Invoke-P4RawAdbCapture.
# It never launches a child, adb or a device command.
function Invoke-P4RawAdbCapture {
    param([string] $AdbPath, [string[]] $AdbArguments, [int] $TimeoutSeconds,
        [string] $DestinationPath, [string] $RecordPath, [long] $MaximumBytes)
    $script:calls++
    Check ($AdbArguments.Count -eq 6 -and $AdbArguments[0] -ceq '-s' -and
        $AdbArguments[1] -ceq 'serial-01:5555' -and $AdbArguments[2] -ceq 'shell' -and
        $AdbArguments[3] -ceq '-T' -and $AdbArguments[4] -ceq '-n' -and
        $AdbArguments[5] -ceq (New-P4EncodedShellCommand "printf '%s' 'quoted'")) 'one quoted shell command'
    $destination = [IO.Path]::GetFullPath($DestinationPath)
    $record = [IO.Path]::GetFullPath($RecordPath)
    $commandPath = "$record.command.json"
    $count = [int][Math]::Min([long]$script:rawBytes.Length, $MaximumBytes)
    $stream = [IO.File]::Open($destination, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $stream.Write($script:rawBytes, 0, $count) } finally { $stream.Dispose() }
    $cap = $script:rawBytes.Length -gt $MaximumBytes
    if ($script:rawDelayMs -gt 0) { Start-Sleep -Milliseconds $script:rawDelayMs }
    $status = if ($script:rawExit -eq 0 -and -not $cap) { 'success' } else { 'failure' }
    $errorText = if ($script:rawExit -ne 0) { "The private-file capture failed ($($script:rawExit)): $($script:rawStderr)" }
        elseif ($cap) { 'The private-file capture exceeded its byte limit.' } else { $null }
    $command = @{ schema = 'nene-pixel-p4-binary-capture-command-v1'; executable = $AdbPath;
        arguments = $AdbArguments; destination_path = $destination; maximum_bytes = $MaximumBytes;
        timeout_seconds = $TimeoutSeconds }
    [IO.File]::WriteAllText($commandPath, ($command | ConvertTo-Json -Depth 5))
    $result = [ordered]@{ schema = 'nene-pixel-p4-binary-capture-result-v1'; status = $status;
        exit_code = $script:rawExit; error = $errorText; stderr = $script:rawStderr;
        path = $destination; byte_count = $count; bytes_written = $count;
        sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant();
        timed_out = $false; byte_limit_exceeded = $cap; job_quiescent_after_failure = $(if ($status -eq 'failure') { $true } else { $null });
        elapsed_milliseconds = $script:rawDelayMs; destination_path = $destination;
        command_record_path = $commandPath }
    [IO.File]::WriteAllText($record, ($result | ConvertTo-Json -Depth 5))
    if ($status -eq 'failure') { throw $errorText }
    return $result
}

try {
    $all = [byte[]](0..255)
    foreach ($sample in @(@{name='all-bytes'; bytes=$all}, @{name='empty'; bytes=[byte[]]::new(0)},
            @{name='large'; bytes=([byte[]](0..255) * 513)})) {
        $paths = New-Case $sample.name $sample.bytes
        $result = Invoke-Case $paths $sample.bytes.Length
        Check ($result.status -ceq 'success' -and $result.byte_count -eq $sample.bytes.Length) "$($sample.name) success"
        Check ([Convert]::ToHexString([IO.File]::ReadAllBytes($paths.destination)) -ceq
            [Convert]::ToHexString($sample.bytes)) "$($sample.name) exact bytes"
        Check ($result.sha256 -ceq (Get-FileHash -LiteralPath $paths.destination -Algorithm SHA256).Hash.ToLowerInvariant()) "$($sample.name) decoded hash"
        Check ($result.encoded_record_sha256 -ceq (Get-FileHash -LiteralPath "$($paths.record).encoded.json" -Algorithm SHA256).Hash.ToLowerInvariant()) "$($sample.name) raw link"
    }
    foreach ($separator in @("`n", "`r`n")) {
        $name = if ($separator.Length -eq 1) { 'lf' } else { 'crlf' }
        $paths = New-Case $name $all
        $base64 = [Text.Encoding]::ASCII.GetString($script:rawBytes)
        $script:rawBytes = [Text.Encoding]::ASCII.GetBytes(($base64 -replace '.{64}', ('$0' + $separator)))
        $result = Invoke-Case $paths 256
        Check ([Convert]::ToHexString([IO.File]::ReadAllBytes($paths.destination)) -ceq
            [Convert]::ToHexString($all)) "$name exact bytes"
    }
    $paths = New-Case 'separator-chunk-edge' ([byte[]](0..255) * 513)
    $base64 = [Text.Encoding]::ASCII.GetString($script:rawBytes)
    $script:rawBytes = [Text.Encoding]::ASCII.GetBytes(($base64.Substring(0, 65533) + "`r`n" +
        $base64.Substring(65533)))
    $result = Invoke-Case $paths (256 * 513)
    Check ($result.byte_count -eq (256 * 513) -and
        [Convert]::ToHexString([IO.File]::ReadAllBytes($paths.destination)) -ceq
        [Convert]::ToHexString(([byte[]](0..255) * 513))) 'separator across 64KiB read'
    $invalid = @(
        @{name='alphabet'; text='QU?='}, @{name='tab'; text="QU`tJD"},
        @{name='invalid-nul'; text="QU$([char]0)D"}, @{name='high'; text='QUéD'},
        @{name='truncated'; text='QUJ'}, @{name='padding-position'; text='Q=JD'},
        @{name='padding-excess'; text='QQ==='}, @{name='after-padding'; text='QQ==QUJD'},
        @{name='padding-cross-chunk'; text=(('QUJD' * 16383) + 'QQ==' + 'QUJD')},
        @{name='truncated-cross-chunk'; text=(('QUJD' * 16384) + 'Q')})
    foreach ($item in $invalid) {
        $paths = New-Case $item.name ([byte[]]::new(0))
        $script:rawBytes = [Text.Encoding]::UTF8.GetBytes($item.text)
        [void](Reject $paths 100000 $item.name)
    }
    $paths = New-Case 'decoded-cap' ([byte[]](0..5))
    $record = Reject $paths 5 'decoded cap'
    Check ($record.byte_count -eq 5) 'decoded cap exact retained prefix'
    Check ([Convert]::ToHexString([IO.File]::ReadAllBytes($paths.destination)) -ceq '0001020304') 'decoded cap prefix bytes'
    $paths = New-Case 'encoded-cap' ([byte[]](0..255))
    $script:rawBytes = [Text.Encoding]::ASCII.GetBytes(('A' * 359))
    $record = Reject $paths 256 'encoded cap'
    Check ($record.encoded_byte_count -eq (Get-P4EncodedMaximum 256) -and $record.byte_count -eq 0) 'encoded cap retained bound'
    $paths = New-Case 'source-exit73' $all
    $script:rawExit = 73; $script:rawStderr = 'upstream failed'
    $record = Reject $paths 256 'source exit73'
    Check ($record.exit_code -eq 73 -and $record.byte_count -eq 0) 'source exit73 never decoded'
    $paths = New-Case 'deadline' $all
    $script:rawDelayMs = 1100
    $record = Reject $paths 256 'shared deadline' 1
    Check ($record.byte_count -eq 0 -and $record.elapsed_milliseconds -ge 1000) 'expired before decode'
    foreach ($suffix in @('destination', 'record', 'encoded', 'encoded-record', 'encoded-command')) {
        $paths = New-Case "collision-$suffix" $all
        $path = switch ($suffix) {
            'destination' { $paths.destination }
            'record' { $paths.record }
            'encoded' { "$($paths.destination).base64" }
            'encoded-record' { "$($paths.record).encoded.json" }
            'encoded-command' { "$($paths.record).encoded.json.command.json" }
        }
        [IO.File]::WriteAllText($path, 'sentinel')
        $before = $script:calls
        $errorText = $null
        try { [void](Invoke-Case $paths 256) } catch { $errorText = $_.Exception.Message }
        Check ($null -ne $errorText -and $script:calls -eq $before) "$suffix collision before child"
        Check ([IO.File]::ReadAllText($path) -ceq 'sentinel') "$suffix collision retains original"
    }
    $timer.Stop()
    $identity = [ordered]@{ schema = 'nene-pixel-p4-encoded-transfer-validation-v1'; status = 'PASS';
        checks = $script:checks; elapsed_milliseconds = $timer.ElapsedMilliseconds;
        first_failures = @($script:firstFailures); source_sha256 = @{
            transport = (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'measurements/p4-device-private-transport.ps1') -Algorithm SHA256).Hash.ToLowerInvariant();
            validator = (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant() } }
    [IO.File]::WriteAllText((Join-Path $OutputDirectory 'validation.json'), ($identity | ConvertTo-Json -Depth 6))
    Write-Output "PASS: $($script:checks) encoded-transfer checks, $($timer.ElapsedMilliseconds) ms; $OutputDirectory"
} catch {
    $timer.Stop()
    $failed = [ordered]@{ status = 'FAIL'; checks = $script:checks;
        elapsed_milliseconds = $timer.ElapsedMilliseconds; error = $_.Exception.Message;
        first_failures = @($script:firstFailures) }
    [IO.File]::WriteAllText((Join-Path $OutputDirectory 'validation.json'), ($failed | ConvertTo-Json -Depth 6))
    throw
}

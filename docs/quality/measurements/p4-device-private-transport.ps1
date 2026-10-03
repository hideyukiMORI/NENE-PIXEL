# Encoded, read-only private-byte transfer over Windows ADB shell_v2.
# Invoke-P4RawAdbCapture is supplied by p4-indexed-device-lanes.ps1.
Set-StrictMode -Version Latest

function ConvertTo-P4ShellWord([string] $Value) {
    $quote = [string][char]39
    $escapedQuote = $quote + [char]34 + $quote + [char]34 + $quote
    return $quote + $Value.Replace($quote, $escapedQuote) + $quote
}

function New-P4EncodedShellCommand([string] $Script) {
    if ([string]::IsNullOrWhiteSpace($Script)) { throw 'Encoded source script is empty.' }
    return 'sh -c ' + (ConvertTo-P4ShellWord ('set -o pipefail || exit; { ' + $Script + '; } | base64'))
}

function Get-P4EncodedMaximum([long] $MaximumBytes) {
    if ($MaximumBytes -lt 0) { throw 'Decoded maximum must be non-negative.' }
    $quartets = ([bigint]$MaximumBytes + 2) / 3
    $characters = $quartets * 4
    # Toybox wraps no more often than every 64 characters. Admit CRLF and a final CRLF.
    $lines = ($characters + 63) / 64
    $bound = $characters + 2 * $lines + 2
    if ($bound -gt [long]::MaxValue) { throw 'Encoded maximum overflows Int64.' }
    return [long]$bound
}

function Assert-P4EncodedDeadline([Diagnostics.Stopwatch] $Timer, [int] $TimeoutSeconds) {
    if ($Timer.ElapsedMilliseconds -ge ([long]$TimeoutSeconds * 1000)) {
        throw "The encoded private-file transfer exceeded its $TimeoutSeconds second bound."
    }
}

function Write-P4DecodedPrefix([IO.FileStream] $Stream, [byte[]] $Buffer, [int] $Count,
    [long] $MaximumBytes, [ref] $ByteCount) {
    $remaining = $MaximumBytes - [long]$ByteCount.Value
    $allowed = [int][Math]::Min([long]$Count, $remaining)
    if ($allowed -gt 0) { $Stream.Write($Buffer, 0, $allowed); $ByteCount.Value += $allowed }
    if ($allowed -ne $Count) { throw "Decoded output exceeded its $MaximumBytes byte limit." }
}

function ConvertFrom-P4EncodedFile {
    param([string] $EncodedPath, [IO.FileStream] $DecodedStream, [long] $MaximumBytes,
        [Diagnostics.Stopwatch] $Timer, [int] $TimeoutSeconds)
    $input = [IO.File]::Open($EncodedPath, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
    $transform = [Security.Cryptography.FromBase64Transform]::new(
        [Security.Cryptography.FromBase64TransformMode]::DoNotIgnoreWhiteSpaces)
    $buffer = [byte[]]::new(65536)
    # A previous read can retain seven characters while the next contributes 65,536.
    $compact = [byte[]]::new(65543)
    $decoded = [byte[]]::new(65536)
    $compactCount = 0
    $characters = [long]0
    $padding = 0
    $byteCount = [long]0
    try {
        while ($true) {
            Assert-P4EncodedDeadline $Timer $TimeoutSeconds
            $read = $input.Read($buffer, 0, $buffer.Length)
            Assert-P4EncodedDeadline $Timer $TimeoutSeconds
            if ($read -eq 0) { break }
            for ($i = 0; $i -lt $read; $i++) {
                $b = $buffer[$i]
                if ($b -eq 10 -or $b -eq 13) { continue }
                if ($b -eq 61) {
                    if ($padding -eq 2 -or ($characters % 4) -lt 2) { throw 'Invalid base64 padding position.' }
                    $padding++
                } elseif (($b -ge 65 -and $b -le 90) -or ($b -ge 97 -and $b -le 122) -or
                    ($b -ge 48 -and $b -le 57) -or $b -eq 43 -or $b -eq 47) {
                    if ($padding -ne 0) { throw 'Base64 data follows padding.' }
                } else { throw 'Invalid base64 alphabet or separator.' }
                $compact[$compactCount++] = $b
                $characters++
            }
            if ($compactCount -ge 8) {
                # Keep the final quartet for TransformFinalBlock, including any padding.
                $length = ($compactCount - 4) -band (-4)
                $count = $transform.TransformBlock($compact, 0, $length, $decoded, 0)
                Write-P4DecodedPrefix $DecodedStream $decoded $count $MaximumBytes ([ref]$byteCount)
                [Array]::Copy($compact, $length, $compact, 0, $compactCount - $length)
                $compactCount -= $length
            }
            Assert-P4EncodedDeadline $Timer $TimeoutSeconds
        }
        if ($characters % 4 -ne 0) { throw 'Truncated base64 quartet.' }
        if ($padding -gt 0 -and $compactCount -ne 4) { throw 'Invalid final base64 padding.' }
        if ($padding -eq 2 -and $compact[2] -ne 61) { throw 'Invalid double base64 padding.' }
        $final = $transform.TransformFinalBlock($compact, 0, $compactCount)
        Write-P4DecodedPrefix $DecodedStream $final $final.Length $MaximumBytes ([ref]$byteCount)
        Assert-P4EncodedDeadline $Timer $TimeoutSeconds
        return $byteCount
    } finally { $transform.Dispose(); $input.Dispose() }
}

function Invoke-P4EncodedShellCapture {
    param([Parameter(Mandatory)][string] $AdbPath, [Parameter(Mandatory)][string] $Serial,
        [Parameter(Mandatory)][string] $Script,
        [Parameter(Mandatory)][ValidateRange(1, 3600)][int] $TimeoutSeconds,
        [Parameter(Mandatory)][string] $DestinationPath,
        [Parameter(Mandatory)][string] $RecordPath,
        [Parameter(Mandatory)][long] $MaximumBytes)
    $encodedMaximum = Get-P4EncodedMaximum $MaximumBytes
    $destination = [IO.Path]::GetFullPath($DestinationPath)
    $record = [IO.Path]::GetFullPath($RecordPath)
    $encoded = "$destination.base64"
    $rawRecord = "$record.encoded.json"
    $rawCommand = "$rawRecord.command.json"
    $paths = @($destination, $record, $encoded, $rawRecord, $rawCommand)
    if (@($paths | Select-Object -Unique).Count -ne 5) { throw 'Encoded transfer output paths must be distinct.' }
    foreach ($path in $paths) {
        if ([IO.File]::Exists($path) -or [IO.Directory]::Exists($path)) {
            throw "Encoded transfer output path already exists: $path"
        }
    }
    $remote = New-P4EncodedShellCommand $Script
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $decodedStream = $null
    $resultStream = $null
    $raw = $null
    $rawDisk = $null
    $rawError = $null
    $failure = $null
    $decodedCount = [long]0
    try {
        # Reserve host outputs before a child can run. Failures retain all partial evidence.
        $decodedStream = [IO.File]::Open($destination, [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write, [IO.FileShare]::Read)
        $resultStream = [IO.File]::Open($record, [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write, [IO.FileShare]::Read)
        try {
            $raw = Invoke-P4RawAdbCapture -AdbPath $AdbPath `
                -AdbArguments @('-s', $Serial, 'shell', '-T', '-n', $remote) `
                -TimeoutSeconds $TimeoutSeconds -DestinationPath $encoded `
                -RecordPath $rawRecord -MaximumBytes $encodedMaximum
        } catch { $rawError = $_ }
        if (-not [IO.File]::Exists($encoded) -or -not [IO.File]::Exists($rawRecord) -or
            -not [IO.File]::Exists($rawCommand)) { throw 'Encoded raw capture evidence is incomplete.' }
        $rawDisk = Get-Content -LiteralPath $rawRecord -Raw | ConvertFrom-Json
        $encodedInfo = [IO.FileInfo]::new($encoded)
        $encodedHash = (Get-FileHash -LiteralPath $encoded -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($null -ne $rawError -or $rawDisk.status -cne 'success' -or $null -ne $rawDisk.error -or
            $rawDisk.exit_code -ne 0 -or $rawDisk.stderr -cne '' -or $rawDisk.timed_out -ne $false -or
            $rawDisk.byte_limit_exceeded -ne $false -or $rawDisk.destination_path -cne $encoded -or
            $rawDisk.command_record_path -cne $rawCommand -or
            $rawDisk.byte_count -ne $encodedInfo.Length -or $rawDisk.sha256 -cne $encodedHash) {
            throw $(if ($null -ne $rawError) { $rawError.Exception.Message } else { 'Encoded raw capture is invalid.' })
        }
        Assert-P4EncodedDeadline $timer $TimeoutSeconds
        $decodedCount = ConvertFrom-P4EncodedFile -EncodedPath $encoded -DecodedStream $decodedStream `
            -MaximumBytes $MaximumBytes -Timer $timer -TimeoutSeconds $TimeoutSeconds
        $decodedStream.Flush()
        Assert-P4EncodedDeadline $timer $TimeoutSeconds
    } catch { $failure = $_ }
    finally {
        if ($null -ne $decodedStream) { $decodedStream.Dispose() }
        $timer.Stop()
        if ($null -ne $resultStream) {
            $encodedInfo = if ([IO.File]::Exists($encoded)) { [IO.FileInfo]::new($encoded) } else { $null }
            $decodedInfo = if ([IO.File]::Exists($destination)) { [IO.FileInfo]::new($destination) } else { $null }
            $result = [ordered]@{
                schema = 'nene-pixel-p4-encoded-transfer-result-v1'
                status = $(if ($null -eq $failure) { 'success' } else { 'failure' })
                exit_code = $(if ($null -ne $rawDisk) { $rawDisk.exit_code } else { $null })
                error = $(if ($null -eq $failure) { $null } else { $failure.Exception.Message })
                path = $destination
                byte_count = $(if ($null -ne $decodedInfo) { $decodedInfo.Length } else { 0 })
                sha256 = $(if ($null -ne $decodedInfo) { (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant() } else { $null })
                maximum_bytes = $MaximumBytes
                encoded_path = $encoded
                encoded_byte_count = $(if ($null -ne $encodedInfo) { $encodedInfo.Length } else { $null })
                encoded_sha256 = $(if ($null -ne $encodedInfo) { (Get-FileHash -LiteralPath $encoded -Algorithm SHA256).Hash.ToLowerInvariant() } else { $null })
                encoded_maximum_bytes = $encodedMaximum
                encoded_record_path = $rawRecord
                encoded_record_sha256 = $(if ([IO.File]::Exists($rawRecord)) { (Get-FileHash -LiteralPath $rawRecord -Algorithm SHA256).Hash.ToLowerInvariant() } else { $null })
                encoded_command_path = $rawCommand
                elapsed_milliseconds = $timer.ElapsedMilliseconds
                timeout_seconds = $TimeoutSeconds
            }
            try {
                $json = [Text.UTF8Encoding]::new($false).GetBytes(($result | ConvertTo-Json -Depth 6))
                $resultStream.Write($json, 0, $json.Length)
            } finally { $resultStream.Dispose() }
        }
    }
    if ($null -ne $failure) { throw $failure }
    return $result
}

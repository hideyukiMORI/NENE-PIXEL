# Read-only, initially-stopped private inventory for ADR 0035. No lifecycle or mutation commands.
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'p4-device-private-observation.ps1')
. (Join-Path $PSScriptRoot 'p4-indexed-device-lanes.ps1')
. (Join-Path $PSScriptRoot 'nene-pixel-lab.ps1')

function ConvertTo-P4ShellWord([string] $Value) {
    $quote = [string][char]39
    $escapedQuote = $quote + [char]34 + $quote + [char]34 + $quote
    return $quote + $Value.Replace($quote, $escapedQuote) + $quote
}

function Assert-P4NativeContext([System.Collections.IDictionary] $Context, [string] $Stage) {
    foreach ($key in @('repository_root', 'output_directory', 'adb_path', 'serial', 'package')) {
        if (-not $Context.Contains($key) -or $Context[$key] -isnot [string] -or
            [string]::IsNullOrWhiteSpace($Context[$key])) { throw "Missing native context: $key" }
    }
    Assert-P4Session $Stage
    if ($Context.serial -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._:-]*$') { throw 'Unsafe adb serial' }
    [void] (Assert-P4PackageName -Value $Context.package -Name 'private inventory')
    $repository = [IO.Path]::GetFullPath($Context.repository_root)
    if (-not [IO.Directory]::Exists($repository) -or
        -not [IO.File]::Exists((Join-Path $repository 'docs/quality/measurements/p4-device-private-native.ps1'))) {
        throw 'Invalid native repository root'
    }
    $output = [IO.Path]::GetFullPath($Context.output_directory)
    $lab = [IO.Path]::GetFullPath((Get-NenePixelLabRoot -StartDirectory $repository))
    if (-not $output.StartsWith(($lab.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar + 'evidence' + [IO.Path]::DirectorySeparatorChar),
            [StringComparison]::OrdinalIgnoreCase) -or -not [IO.Directory]::Exists($output)) {
        throw 'Native output directory must already exist under lab evidence'
    }
    if (-not [IO.File]::Exists($Context.adb_path)) { throw 'Invalid adb path' }
}

function Read-P4NativeBytes([string] $Path) {
    return ,([IO.File]::ReadAllBytes($Path))
}

function ConvertFrom-P4NativeLines([byte[]] $Bytes, [switch] $AllowCrLf) {
    $decoder = [Text.UTF8Encoding]::new($false, $true)
    $value = $decoder.GetString($Bytes)
    if ($value.Length -eq 0) { return ,([string[]]@()) }
    if ($AllowCrLf) { $value = $value.Replace("`r`n", "`n") }
    if (-not $value.EndsWith("`n", [StringComparison]::Ordinal)) { throw 'Unterminated native line output' }
    $value = $value.Substring(0, $value.Length - 1)
    if ($value.Contains("`r") -or $value.Contains([char]0)) { throw 'Unexpected native line control byte' }
    return ,([string[]]$value.Split("`n"))
}

function Invoke-P4NativeCapture {
    param([System.Collections.IDictionary] $Context, [string] $Stage, [string] $Step,
        [string[]] $AdbArguments, [long] $MaximumBytes, [int] $ExpectedExit = 0,
        [string] $ExpectedStdout = $null, [string] $ExpectedStderr = $null)
    $destination = Join-Path $Context.output_directory "$Stage-$Step.bin"
    $recordPath = Join-Path $Context.output_directory "$Stage-$Step.json"
    foreach ($path in @($destination, $recordPath, "$recordPath.command.json")) {
        if ([IO.File]::Exists($path) -or [IO.Directory]::Exists($path)) {
            throw "Native capture output path already exists: $path"
        }
    }
    $captureError = $null
    try {
        [void] (Invoke-P4RawAdbCapture -AdbPath $Context.adb_path `
            -AdbArguments (@('-s', $Context.serial) + $AdbArguments) -TimeoutSeconds 30 `
            -DestinationPath $destination -RecordPath $recordPath -MaximumBytes $MaximumBytes)
    } catch { $captureError = $_ }
    if (-not [IO.File]::Exists($recordPath) -or -not [IO.File]::Exists($destination)) {
        $reason = if ($null -eq $captureError) { 'raw capture returned without both files' }
            else { $captureError.Exception.Message }
        throw "Native capture record missing: $Step; $reason"
    }
    $record = Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
    if ($record.timed_out -or $record.byte_limit_exceeded -or $record.exit_code -ne $ExpectedExit -or
        $record.destination_path -cne [IO.Path]::GetFullPath($destination) -or
        $record.byte_count -ne ([IO.FileInfo]::new($destination)).Length) {
        throw "Native capture invalid: $Step"
    }
    if ($ExpectedExit -eq 0) {
        if ($null -ne $captureError -or $record.status -cne 'success' -or $null -ne $record.error -or
            $record.stderr -cne '') { throw "Native command failed: $Step" }
    } else {
        $expectedError = "The private-file capture failed ($ExpectedExit): $($record.stderr)"
        if ($null -eq $captureError -or $record.status -cne 'failure' -or
            $record.error -cne $expectedError -or
            $captureError.Exception.Message -cne $expectedError -or
            $record.job_quiescent_after_failure -ne $true) {
            throw "Unproven expected native exit: $Step"
        }
    }
    $bytes = Read-P4NativeBytes $destination
    if ($PSBoundParameters.ContainsKey('ExpectedStdout') -or $PSBoundParameters.ContainsKey('ExpectedStderr')) {
        $decoded = [Text.UTF8Encoding]::new($false, $true).GetString($bytes)
        if ($decoded -cne $ExpectedStdout -or $record.stderr -cne $ExpectedStderr) {
            throw "Native stream proof failed: $Step"
        }
    } elseif ($ExpectedExit -ne 0 -and $record.stderr -cne '') {
        throw "Unexpected native stderr: $Step"
    }
    return ,$bytes
}

function New-P4NativeBatches([string[]] $Paths) {
    $batches = [Collections.Generic.List[object]]::new()
    $current = [Collections.Generic.List[string]]::new()
    $length = 0
    foreach ($path in $Paths) {
        Assert-P4Path $path
        $word = ConvertTo-P4ShellWord $path
        if ($word.Length -gt 20000) { throw 'Native path command too long' }
        if ($current.Count -ge 128 -or ($current.Count -gt 0 -and $length + $word.Length + 1 -gt 20000)) {
            $batches.Add($current.ToArray())
            $current = [Collections.Generic.List[string]]::new()
            $length = 0
        }
        $current.Add($path)
        $length += $word.Length + 1
    }
    if ($current.Count -gt 0) { $batches.Add($current.ToArray()) }
    return ,$batches.ToArray()
}

function Invoke-P4NativeRunAs([System.Collections.IDictionary] $Context, [string] $Stage,
    [string] $Step, [string] $Script, [long] $MaximumBytes) {
    $remote = 'run-as ' + (ConvertTo-P4ShellWord $Context.package) + ' sh -c ' +
        (ConvertTo-P4ShellWord $Script)
    return ,(Invoke-P4NativeCapture -Context $Context -Stage $Stage -Step $Step `
        -AdbArguments @('shell', '-T', '-n', $remote) -MaximumBytes $MaximumBytes)
}

function Get-P4NativePrivateInventory {
    param([Parameter(Mandatory)][System.Collections.IDictionary] $Context,
        [Parameter(Mandatory)][string] $Stage)
    Assert-P4NativeContext $Context $Stage
    $features = Invoke-P4NativeCapture -Context $Context -Stage $Stage -Step 'features' `
        -AdbArguments @('features') -MaximumBytes 16384
    $featureLines = ConvertFrom-P4NativeLines $features -AllowCrLf
    if (@($featureLines | Where-Object { $_ -ceq 'shell_v2' }).Count -ne 1) { throw 'ADB shell_v2 unavailable' }
    $proof = "printf p4-out; printf p4-err >&2; exit 73"
    [void] (Invoke-P4NativeCapture -Context $Context -Stage $Stage -Step 'shell-proof' `
        -AdbArguments @('shell', '-T', '-n', ('sh -c ' + (ConvertTo-P4ShellWord $proof))) `
        -MaximumBytes 64 -ExpectedExit 73 -ExpectedStdout 'p4-out' -ExpectedStderr 'p4-err')
    $pidCommand = 'pidof ' + (ConvertTo-P4ShellWord $Context.package)
    [void] (Invoke-P4NativeCapture -Context $Context -Stage $Stage -Step 'stopped-before' `
        -AdbArguments @('shell', '-T', '-n', $pidCommand) -MaximumBytes 1024 `
        -ExpectedExit 1 -ExpectedStdout '' -ExpectedStderr '')
    $scan = 'for root in files no_backup shared_prefs databases; do if [ -e "$root" ] || [ -L "$root" ]; then find "$root" -print0 || exit; fi; done'
    $paths = Invoke-P4NativeRunAs $Context $Stage 'paths-before' $scan 33554432
    $discovered = ConvertFrom-P4NulPaths $paths
    $metadata = [Collections.Generic.List[string]]::new()
    $batchNumber = 0
    foreach ($batch in (New-P4NativeBatches $discovered)) {
        $words = @($batch | ForEach-Object { ConvertTo-P4ShellWord $_ }) -join ' '
        $output = Invoke-P4NativeRunAs $Context $Stage "stat-$batchNumber" `
            ("stat -c '%n|%f|%h|%s|%Y|%y' " + $words) 65536
        foreach ($line in (ConvertFrom-P4NativeLines $output)) { $metadata.Add($line) }
        $batchNumber++
    }
    $regular = [Collections.Generic.List[string]]::new()
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $discoveredSet = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($path in $discovered) { [void]$discoveredSet.Add($path) }
    foreach ($line in $metadata) {
        $fields = $line.Split('|')
        if ($fields.Length -ne 6 -or $fields[1] -cnotmatch '^[0-9a-fA-F]+$') { throw 'Malformed native metadata' }
        $path = $fields[0]
        Assert-P4Path $path
        if (-not $discoveredSet.Contains($path) -or -not $seen.Add($path)) {
            throw 'Unlisted or duplicate native metadata'
        }
        try { $mode = [Convert]::ToUInt32($fields[1], 16) } catch { throw 'Invalid native mode' }
        if (($mode -band 0xF000) -eq 0x8000) { $regular.Add($path) }
    }
    if ($seen.Count -ne $discoveredSet.Count) { throw 'Native metadata coverage incomplete' }
    $hashes = [Collections.Generic.List[string]]::new()
    $batchNumber = 0
    foreach ($batch in (New-P4NativeBatches $regular.ToArray())) {
        $words = @($batch | ForEach-Object { ConvertTo-P4ShellWord $_ }) -join ' '
        $output = Invoke-P4NativeRunAs $Context $Stage "hash-$batchNumber" ('sha256sum ' + $words) 65536
        foreach ($line in (ConvertFrom-P4NativeLines $output)) { $hashes.Add($line) }
        $batchNumber++
    }
    $inventory = ConvertFrom-P4NativeInventory $paths $metadata.ToArray() $hashes.ToArray()
    $after = Invoke-P4NativeRunAs $Context $Stage 'paths-after' $scan 33554432
    $afterPaths = ConvertFrom-P4NulPaths $after
    if ($afterPaths.Count -ne $discovered.Count) { throw 'Native path set drift' }
    $set = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($path in $discovered) { [void]$set.Add($path) }
    foreach ($path in $afterPaths) { if (-not $set.Remove($path)) { throw 'Native path set drift' } }
    if ($set.Count -ne 0) { throw 'Native path set drift' }
    [void] (Invoke-P4NativeCapture -Context $Context -Stage $Stage -Step 'stopped-after' `
        -AdbArguments @('shell', '-T', '-n', $pidCommand) -MaximumBytes 1024 `
        -ExpectedExit 1 -ExpectedStdout '' -ExpectedStderr '')
    return ,([object[]]$inventory)
}

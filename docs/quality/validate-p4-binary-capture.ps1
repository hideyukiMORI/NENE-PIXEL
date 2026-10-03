param([string]$OutputDirectory)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-device-lanes.ps1')

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $lab = Get-NenePixelLabRoot -StartDirectory $PSScriptRoot
    $OutputDirectory = Join-Path $lab ('evidence/145-binary-capture/' +
        (Get-Date -Format 'yyyyMMddTHHmmssfff') + '-' + [guid]::NewGuid().ToString('N'))
}
if (Test-Path -LiteralPath $OutputDirectory) { throw "Evidence path already exists: $OutputDirectory" }
[IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null
$child = (Get-Command pwsh -ErrorAction Stop).Source
$checks = 0
$timer = [Diagnostics.Stopwatch]::StartNew()

function Assert-Capture {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Binary capture check failed: $Message" }
    $script:checks++
}

function New-Case {
    param([string]$Name)
    $path = Join-Path $OutputDirectory $Name
    [IO.Directory]::CreateDirectory($path) | Out-Null
    return $path
}

function Invoke-ChildCapture {
    param(
        [string]$Script,
        [string]$CaseDirectory,
        [long]$MaximumBytes = 1024,
        [int]$TimeoutSeconds = 15,
        [switch]$Memory
    )
    $args = @('-NoProfile', '-NonInteractive', '-Command', $Script)
    if ($Memory) {
        return Invoke-P4RawAdbCapture -AdbPath $child -AdbArguments $args `
            -TimeoutSeconds $TimeoutSeconds
    }
    return Invoke-P4RawAdbCapture -AdbPath $child -AdbArguments $args `
        -TimeoutSeconds $TimeoutSeconds -DestinationPath (Join-Path $CaseDirectory 'payload.bin') `
        -RecordPath (Join-Path $CaseDirectory 'capture.json') -MaximumBytes $MaximumBytes
}

$binaryScript = '$b=[Convert]::FromHexString("00ff0a0d80");[Console]::OpenStandardOutput().Write($b,0,$b.Length);[Console]::Error.Write("stderr-only")'
$expected = [Convert]::FromHexString('00ff0a0d80')
$memory = Invoke-ChildCapture -Script $binaryScript -CaseDirectory (New-Case 'memory') -Memory
Assert-Capture ([Convert]::ToHexString([byte[]]$memory) -ceq '00FF0A0D80') 'memory mode exact bytes'
try {
    Invoke-ChildCapture -Script '$null = 1' -CaseDirectory (New-Case 'memory-empty') -Memory | Out-Null
    throw 'Empty memory output was accepted.'
} catch {
    Assert-Capture ($_.Exception.Message -like '*returned no bytes*') 'memory mode empty rejection'
}

$binaryCase = New-Case 'file-binary'
$binary = Invoke-ChildCapture -Script $binaryScript -CaseDirectory $binaryCase
$binaryRecord = Get-Content -Raw (Join-Path $binaryCase 'capture.json') | ConvertFrom-Json
$commandRecord = Get-Content -Raw (Join-Path $binaryCase 'capture.json.command.json') | ConvertFrom-Json
Assert-Capture ([Convert]::ToHexString([IO.File]::ReadAllBytes($binary.path)) -ceq '00FF0A0D80') 'file mode exact bytes'
Assert-Capture ($binary.byte_count -eq 5 -and $binary.status -eq 'success' -and $binary.exit_code -eq 0) 'file return contract'
Assert-Capture ($binaryRecord.sha256 -ceq (Get-FileHash $binary.path -Algorithm SHA256).Hash.ToLowerInvariant()) 'file result hash'
Assert-Capture ($binaryRecord.stderr -ceq 'stderr-only') 'stderr stays separate from payload'
Assert-Capture (@($commandRecord.arguments).Count -eq 4 -and $commandRecord.arguments[3] -ceq $binaryScript) 'all native arguments recorded'

$emptyCase = New-Case 'file-empty'
$empty = Invoke-ChildCapture -Script '$null = 1' -CaseDirectory $emptyCase -MaximumBytes 0
Assert-Capture ($empty.byte_count -eq 0 -and (Get-Item $empty.path).Length -eq 0 -and $empty.status -eq 'success') 'zero-byte file accepted'

foreach ($occupied in @('payload.bin', 'capture.json', 'capture.json.command.json')) {
    $case = New-Case ('occupied-' + $occupied.Replace('.', '-'))
    $existing = Join-Path $case $occupied
    [IO.File]::WriteAllText($existing, 'sentinel')
    $marker = Join-Path $case 'child-started'
    $markerScript = '[IO.File]::WriteAllText(''' + $marker.Replace("'", "''") + ''',''started'')'
    try {
        Invoke-ChildCapture -Script $markerScript -CaseDirectory $case | Out-Null
        throw "Occupied path $occupied was accepted."
    } catch {
        Assert-Capture ($_.Exception.Message -like '*already exists*') "occupied $occupied refused"
    }
    Assert-Capture ([IO.File]::ReadAllText($existing) -ceq 'sentinel') "occupied $occupied unchanged"
    Assert-Capture (-not (Test-Path -LiteralPath $marker)) "occupied $occupied no process started"
}

$overflowCase = New-Case 'overflow'
try {
    Invoke-ChildCapture -Script $binaryScript -CaseDirectory $overflowCase -MaximumBytes 3 | Out-Null
    throw 'Byte overflow was accepted.'
} catch {
    Assert-Capture ($_.Exception.Message -like '*byte limit*') 'byte cap rejection'
}
$overflowRecord = Get-Content -Raw (Join-Path $overflowCase 'capture.json') | ConvertFrom-Json
Assert-Capture ((Get-Item (Join-Path $overflowCase 'payload.bin')).Length -eq 3 -and $overflowRecord.byte_count -eq 3) 'partial file capped exactly'
Assert-Capture ($overflowRecord.status -eq 'failure' -and $overflowRecord.byte_limit_exceeded -and $overflowRecord.job_quiescent_after_failure) 'overflow failure record and quiescence'
Assert-Capture ((Test-Path (Join-Path $overflowCase 'capture.json.command.json')) -and
    $overflowRecord.sha256 -ceq (Get-FileHash (Join-Path $overflowCase 'payload.bin') -Algorithm SHA256).Hash.ToLowerInvariant()) 'overflow command and partial hash retained'

$nonzeroCase = New-Case 'nonzero'
try {
    Invoke-ChildCapture -Script '[Console]::OpenStandardOutput().WriteByte(42);exit 7' -CaseDirectory $nonzeroCase | Out-Null
    throw 'Nonzero child exit was accepted.'
} catch {
    Assert-Capture ($_.Exception.Message -like '*failed (7)*') 'nonzero exit rejection'
}
$nonzeroRecord = Get-Content -Raw (Join-Path $nonzeroCase 'capture.json') | ConvertFrom-Json
Assert-Capture ($nonzeroRecord.exit_code -eq 7 -and $nonzeroRecord.byte_count -eq 1 -and $nonzeroRecord.status -eq 'failure') 'nonzero result and partial file retained'
Assert-Capture ((Test-Path (Join-Path $nonzeroCase 'capture.json.command.json')) -and
    (Get-Item (Join-Path $nonzeroCase 'payload.bin')).Length -eq 1) 'nonzero command and partial file retained'

$timeoutCase = New-Case 'timeout'
$pidPath = Join-Path $timeoutCase 'child.pid'
$timeoutScript = '[IO.File]::WriteAllText(''' + $pidPath.Replace("'", "''") + ''',[string]$PID);Start-Sleep -Seconds 8'
try {
    Invoke-ChildCapture -Script $timeoutScript -CaseDirectory $timeoutCase -TimeoutSeconds 2 | Out-Null
    throw 'Sleeping child completed within its deadline.'
} catch {
    Assert-Capture ($_.Exception.Message -like '*second bound*') 'single deadline timeout'
}
$timeoutRecord = Get-Content -Raw (Join-Path $timeoutCase 'capture.json') | ConvertFrom-Json
Assert-Capture ($timeoutRecord.timed_out -and $timeoutRecord.status -eq 'failure' -and $timeoutRecord.job_quiescent_after_failure) 'timeout record and job quiescence'
Assert-Capture ((Test-Path -LiteralPath (Join-Path $timeoutCase 'payload.bin')) -and (Test-Path -LiteralPath $pidPath)) 'timeout partial file and child identity retained'
$childPid = [int][IO.File]::ReadAllText($pidPath)
Assert-Capture ($null -eq (Get-Process -Id $childPid -ErrorAction SilentlyContinue)) 'timed-out child terminated'

$timer.Stop()
$identity = [ordered]@{
    schema = 'nene-pixel-p4-binary-capture-validator-v1'
    status = 'pass'
    checks = $checks
    elapsed_milliseconds = $timer.ElapsedMilliseconds
    source_sha256 = (Get-FileHash (Join-Path $PSScriptRoot 'measurements/p4-indexed-device-lanes.ps1') -Algorithm SHA256).Hash.ToLowerInvariant()
    validator_sha256 = (Get-FileHash $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
}
Write-NewInvocationFile (Join-Path $OutputDirectory 'validation.json') ($identity | ConvertTo-Json -Depth 5)
Write-Output "PASS: $checks binary-capture checks; $($timer.ElapsedMilliseconds) ms; evidence $OutputDirectory"

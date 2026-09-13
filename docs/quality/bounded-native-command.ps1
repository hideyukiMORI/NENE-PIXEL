Set-StrictMode -Version Latest
$script:RestorationBlockedDataKey = 'NenePixelRestorationBlocked'

# Shared Windows Job boundary. The child waits for assignment before starting any native work.
# Callers supply their own fixed policy; ordinary calls never acquire Gradle/cache flags here.
if ($null -eq ('NenePixelBaselineProfile.JobNativeMethods' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

namespace NenePixelBaselineProfile {
    [StructLayout(LayoutKind.Sequential)]
    public struct IoCounters {
        public ulong ReadOperationCount;
        public ulong WriteOperationCount;
        public ulong OtherOperationCount;
        public ulong ReadTransferCount;
        public ulong WriteTransferCount;
        public ulong OtherTransferCount;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct BasicLimitInformation {
        public long PerProcessUserTimeLimit;
        public long PerJobUserTimeLimit;
        public uint LimitFlags;
        public UIntPtr MinimumWorkingSetSize;
        public UIntPtr MaximumWorkingSetSize;
        public uint ActiveProcessLimit;
        public UIntPtr Affinity;
        public uint PriorityClass;
        public uint SchedulingClass;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct ExtendedLimitInformation {
        public BasicLimitInformation BasicLimitInformation;
        public IoCounters IoInfo;
        public UIntPtr ProcessMemoryLimit;
        public UIntPtr JobMemoryLimit;
        public UIntPtr PeakProcessMemoryUsed;
        public UIntPtr PeakJobMemoryUsed;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct BasicAccountingInformation {
        public long TotalUserTime;
        public long TotalKernelTime;
        public long ThisPeriodTotalUserTime;
        public long ThisPeriodTotalKernelTime;
        public uint TotalPageFaultCount;
        public uint TotalProcesses;
        public uint ActiveProcesses;
        public uint TotalTerminatedProcesses;
    }

    public static class JobNativeMethods {
        public const uint JobObjectLimitKillOnJobClose = 0x00002000;
        public const int JobObjectBasicAccountingInformation = 1;
        public const int JobObjectExtendedLimitInformation = 9;

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern IntPtr CreateJobObject(IntPtr jobAttributes, string name);

        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool SetInformationJobObject(
            IntPtr job,
            int informationClass,
            ref ExtendedLimitInformation information,
            uint informationLength
        );

        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool QueryInformationJobObject(
            IntPtr job,
            int informationClass,
            ref BasicAccountingInformation information,
            uint informationLength,
            out uint returnLength
        );

        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);

        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool TerminateJobObject(IntPtr job, uint exitCode);

        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool CloseHandle(IntPtr handle);
    }
}
'@
}

function Write-NewInvocationFile {
    param([Parameter(Mandatory = $true)][string]$Path, [AllowEmptyString()][string]$Text)
    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::CreateNew,
        [System.IO.FileAccess]::Write, [System.IO.FileShare]::Read)
    try {
        $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($Text)
        $stream.Write($bytes, 0, $bytes.Length)
    } finally { $stream.Dispose() }
}

function New-InvocationJob {
    $job = [NenePixelBaselineProfile.JobNativeMethods]::CreateJobObject([IntPtr]::Zero, $null)
    if ($job -eq [IntPtr]::Zero) {
        throw "CreateJobObject failed with Win32 error $([Runtime.InteropServices.Marshal]::GetLastWin32Error())."
    }
    $limits = [NenePixelBaselineProfile.ExtendedLimitInformation]::new()
    $limits.BasicLimitInformation.LimitFlags =
        [NenePixelBaselineProfile.JobNativeMethods]::JobObjectLimitKillOnJobClose
    $size = [Runtime.InteropServices.Marshal]::SizeOf($limits)
    if (-not [NenePixelBaselineProfile.JobNativeMethods]::SetInformationJobObject(
            $job,
            [NenePixelBaselineProfile.JobNativeMethods]::JobObjectExtendedLimitInformation,
            [ref]$limits,
            $size
        )) {
        $errorCode = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
        [NenePixelBaselineProfile.JobNativeMethods]::CloseHandle($job) | Out-Null
        throw "SetInformationJobObject failed with Win32 error $errorCode."
    }
    return $job
}

function Get-InvocationJobActiveProcessCount {
    param([Parameter(Mandatory = $true)][IntPtr]$Job)

    $accounting = [NenePixelBaselineProfile.BasicAccountingInformation]::new()
    $returnLength = [uint32]0
    $size = [Runtime.InteropServices.Marshal]::SizeOf($accounting)
    if (-not [NenePixelBaselineProfile.JobNativeMethods]::QueryInformationJobObject(
            $Job,
            [NenePixelBaselineProfile.JobNativeMethods]::JobObjectBasicAccountingInformation,
            [ref]$accounting,
            $size,
            [ref]$returnLength
        )) {
        throw "QueryInformationJobObject failed with Win32 error " +
            "$([Runtime.InteropServices.Marshal]::GetLastWin32Error())."
    }
    return [int]$accounting.ActiveProcesses
}

function Wait-InvocationJobEmpty {
    param(
        [Parameter(Mandatory = $true)][IntPtr]$Job,
        [Parameter(Mandatory = $true)][datetime]$DeadlineUtc
    )

    do {
        if ((Get-InvocationJobActiveProcessCount -Job $Job) -eq 0) {
            return $true
        }
        Start-Sleep -Milliseconds 100
    } while ([datetime]::UtcNow -lt $DeadlineUtc)
    return (Get-InvocationJobActiveProcessCount -Job $Job) -eq 0
}

function Invoke-BoundedNativeCommand {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$LogPath,
        [Parameter(Mandatory = $true)][string]$ExecutablePath,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$NativeArguments,
        [ValidateRange(1, 1800)][int]$TimeoutSeconds = 30,
        [scriptblock]$JobTerminator = {
            param([Parameter(Mandatory = $true)][IntPtr]$Job)

            if (-not [NenePixelBaselineProfile.JobNativeMethods]::TerminateJobObject($Job, 124)) {
                throw "TerminateJobObject failed with Win32 error " +
                    "$([Runtime.InteropServices.Marshal]::GetLastWin32Error())."
            }
        }
    )

    $nativeExecutable = [System.IO.Path]::GetFullPath($ExecutablePath)
    if (-not (Test-Path -LiteralPath $nativeExecutable -PathType Leaf)) {
        throw "Native executable is missing: $nativeExecutable"
    }
    $LogPath = [System.IO.Path]::GetFullPath($LogPath)
    $launcherPath = "$LogPath.launcher.ps1"
    $descriptorPath = "$LogPath.invocation.json"
    foreach ($newPath in @($LogPath, $launcherPath, $descriptorPath)) {
        if (Test-Path -LiteralPath $newPath) { throw "Invocation output already exists: $newPath" }
    }
    Write-NewInvocationFile -Path $LogPath -Text ''
    $descriptor = [ordered]@{ executable = $nativeExecutable; arguments = @($NativeArguments) }
    Write-NewInvocationFile -Path $descriptorPath -Text ($descriptor | ConvertTo-Json -Depth 5)
    $launcher = @'
param(
    [Parameter(Mandatory = $true)][string]$GateName,
    [Parameter(Mandatory = $true)][string]$RepositoryRoot,
    [Parameter(Mandatory = $true)][string]$LogPath,
    [Parameter(Mandatory = $true)][string]$InvocationPath
)
$ErrorActionPreference = 'Stop'
$gate = $null
try {
    $gate = [Threading.EventWaitHandle]::OpenExisting($GateName)
    if (-not $gate.WaitOne(30000)) {
        throw 'Timed out waiting for the parent-owned job assignment gate.'
    }
    Push-Location $RepositoryRoot
    try {
        $invocation = Get-Content -LiteralPath $InvocationPath -Raw | ConvertFrom-Json
        $invocationArguments = @($invocation.arguments)
        & $invocation.executable @invocationArguments *>> $LogPath
        exit $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
}
catch {
    if (Test-Path -LiteralPath $LogPath -PathType Leaf) {
        $_.Exception.ToString() | Add-Content -LiteralPath $LogPath -Encoding utf8NoBOM
    } else {
        $_.Exception.ToString() | Set-Content -LiteralPath $LogPath -Encoding utf8NoBOM
    }
    exit 125
}
finally {
    if ($null -ne $gate) {
        $gate.Dispose()
    }
}
'@
    Write-NewInvocationFile -Path $launcherPath -Text $launcher
    $gateName = "Local\NenePixelBaselineProfile-$([guid]::NewGuid().ToString('N'))"
    $gateCreated = $false
    $gate = [Threading.EventWaitHandle]::new(
        $false,
        [Threading.EventResetMode]::ManualReset,
        $gateName,
        [ref]$gateCreated
    )
    if (-not $gateCreated) {
        $gate.Dispose()
        throw 'Failed to create the unique invocation assignment gate.'
    }
    $job = [IntPtr]::Zero
    $process = [System.Diagnostics.Process]::new()
    $started = $false
    $assigned = $false
    $jobConfirmedEmpty = $false
    try {
        $job = New-InvocationJob
        $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $startInfo.FileName = Join-Path $PSHOME 'pwsh.exe'
        $startInfo.WorkingDirectory = $RepositoryRoot
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        foreach ($argument in @(
                '-NoProfile',
                '-File',
                $launcherPath,
                '-GateName',
                $gateName,
                '-RepositoryRoot',
                $RepositoryRoot,
                '-LogPath',
                $LogPath,
                '-InvocationPath',
                $descriptorPath
            )) {
            $startInfo.ArgumentList.Add($argument)
        }
        $process.StartInfo = $startInfo
        if (-not $process.Start()) {
            throw 'Native launcher process did not start.'
        }
        $started = $true
        if (-not [NenePixelBaselineProfile.JobNativeMethods]::AssignProcessToJobObject(
                $job,
                $process.Handle
            )) {
            throw "AssignProcessToJobObject failed with Win32 error " +
                "$([Runtime.InteropServices.Marshal]::GetLastWin32Error())."
        }
        $assigned = $true
        $gate.Set() | Out-Null
        $deadline = [datetime]::UtcNow.AddSeconds($TimeoutSeconds)
        $completed = Wait-InvocationJobEmpty -Job $job -DeadlineUtc $deadline
        if (-not $completed) {
            $terminationFailure = $null
            try {
                & $JobTerminator -Job $job
            }
            catch {
                $terminationFailure = $_.Exception.Message
            }
            $quiescent = Wait-InvocationJobEmpty -Job $job -DeadlineUtc ([datetime]::UtcNow.AddSeconds(10))
            $jobConfirmedEmpty = $quiescent
            if ($null -ne $terminationFailure -or -not $quiescent) {
                $message = "Native invocation timed out after $TimeoutSeconds seconds, and its job " +
                    'could not be confirmed terminated; tracked-file restoration is blocked.'
                if ($null -ne $terminationFailure) {
                    $message += " Termination failure: $terminationFailure"
                }
                $exception = [System.InvalidOperationException]::new($message)
                $exception.Data[$script:RestorationBlockedDataKey] = $true
                throw $exception
            }
            throw "Native invocation timed out after $TimeoutSeconds seconds; its job reached zero active processes."
        }
        $jobConfirmedEmpty = $true
        if (-not $process.WaitForExit(5000)) {
            throw 'Native capture did not drain within 5 seconds after its Job became empty.'
        }
        $exitCode = $process.ExitCode
        $outputLines = if (Test-Path -LiteralPath $LogPath -PathType Leaf) {
            @(Get-Content -LiteralPath $LogPath)
        } else {
            @()
        }
        $outputLines | ForEach-Object { Write-Host $_ }
        return [pscustomobject]@{ ExitCode = $exitCode; OutputLines = $outputLines }
    }
    catch {
        $originalException = $_.Exception
        if (
            $assigned -and
            -not $jobConfirmedEmpty -and
            $originalException.Data[$script:RestorationBlockedDataKey] -ne $true
        ) {
            $terminationFailure = $null
            try {
                & $JobTerminator -Job $job
            }
            catch {
                $terminationFailure = $_.Exception.Message
            }
            $quiescent = $false
            try {
                $quiescent = Wait-InvocationJobEmpty -Job $job `
                    -DeadlineUtc ([datetime]::UtcNow.AddSeconds(10))
            }
            catch {
                $terminationFailure = if ($null -eq $terminationFailure) {
                    $_.Exception.Message
                } else {
                    "$terminationFailure; $($_.Exception.Message)"
                }
            }
            $jobConfirmedEmpty = $quiescent
            if ($null -ne $terminationFailure -or -not $quiescent) {
                $message = 'Native job failure could not be followed by confirmed job termination; ' +
                    "tracked-file restoration is blocked. Original failure: $($originalException.Message)"
                if ($null -ne $terminationFailure) {
                    $message += " Termination failure: $terminationFailure"
                }
                $blockedException = [System.InvalidOperationException]::new($message, $originalException)
                $blockedException.Data[$script:RestorationBlockedDataKey] = $true
                $originalException = $blockedException
            }
        }
        if ($started -and -not $assigned) {
            try {
                if (-not $process.HasExited) {
                    try { $process.Kill() }
                    catch { if (-not $process.HasExited) { throw } }
                }
                if (-not $process.WaitForExit(10000)) {
                    throw 'Unassigned launcher exit was not confirmed within 10 seconds.'
                }
            }
            catch {
                $blockedException = [System.InvalidOperationException]::new(
                    "Unassigned launcher termination is unconfirmed; restoration is blocked. " +
                    "Termination failure: $($_.Exception.Message)", $originalException)
                $blockedException.Data[$script:RestorationBlockedDataKey] = $true
                $originalException = $blockedException
            }
        }
        if ($started -and $assigned -and $jobConfirmedEmpty -and -not $process.WaitForExit(10000)) {
            $message = 'The invocation job reached zero active processes, but its launcher exit was not ' +
                "confirmed within 10 seconds; tracked-file restoration is blocked. Original failure: " +
                $originalException.Message
            $blockedException = [System.InvalidOperationException]::new($message, $originalException)
            $blockedException.Data[$script:RestorationBlockedDataKey] = $true
            $originalException = $blockedException
        }
        try {
            if (Test-Path -LiteralPath $LogPath -PathType Leaf) {
                $originalException.ToString() | Add-Content -LiteralPath $LogPath -Encoding utf8NoBOM
            } else {
                $originalException.ToString() | Set-Content -LiteralPath $LogPath -Encoding utf8NoBOM
            }
        }
        catch {
            $message = 'The invocation failure log could not be finalized after bounded launcher ' +
                "termination; tracked-file restoration is blocked. Original failure: " +
                $originalException.Message + " Log failure: " + $_.Exception.Message
            $blockedException = [System.InvalidOperationException]::new($message, $originalException)
            $blockedException.Data[$script:RestorationBlockedDataKey] = $true
            $originalException = $blockedException
        }
        throw $originalException
    }
    finally {
        $gate.Dispose()
        $process.Dispose()
        if ($job -ne [IntPtr]::Zero) {
            [NenePixelBaselineProfile.JobNativeMethods]::CloseHandle($job) | Out-Null
        }
    }
}


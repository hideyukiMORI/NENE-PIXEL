[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$')]
    [string]$EvidenceId = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'baseline-profile-evidence.ps1')

$script:EvidenceSchema = 'nene-pixel-baseline-profile-evidence-v1'
$script:ProducerTask = ':quality:baseline-profile:connectedNonMinifiedReleaseAndroidTest'
$script:ProducerTimeoutSeconds = 1800
$script:ValidationTimeoutSeconds = 300
$script:RestorationBlockedDataKey = 'NenePixelRestorationBlocked'
$script:ProfileRelativePath = 'app/android/src/main/generated/baselineProfiles/baseline-prof.txt'
$script:HashRelativePath = 'app/android/src/main/generated/baselineProfiles.sha256'
$script:MergedRelativePath = 'app/android/build/intermediates/baselineprofiles/main/merged/baseline-prof.txt'
$script:ProducerOutputRelativePath =
    'quality/baseline-profile/build/outputs/connected_android_test_additional_output/nonMinifiedRelease'
$script:ProducerResultsRelativePath =
    'quality/baseline-profile/build/outputs/androidTest-results/connected/nonMinifiedRelease'
$script:AppApkRelativePath = 'app/android/build/outputs/apk/nonMinifiedRelease/android-nonMinifiedRelease.apk'
$script:TestApkRelativePath =
    'quality/baseline-profile/build/outputs/apk/nonMinifiedRelease/baseline-profile-nonMinifiedRelease.apk'

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

function Get-FileState {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        return [pscustomobject]@{ Exists = $true; Bytes = [System.IO.File]::ReadAllBytes($Path) }
    }
    return [pscustomobject]@{ Exists = $false; Bytes = $null }
}

function Restore-FileState {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$State
    )

    if ($State.Exists) {
        [System.IO.File]::WriteAllBytes($Path, $State.Bytes)
    } elseif (Test-Path -LiteralPath $Path -PathType Leaf) {
        Remove-Item -LiteralPath $Path -Force
    }
}

function Get-GradleTaskOutcomes {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string[]]$OutputLines
    )

    $outcomes = [ordered]@{}
    foreach ($line in $OutputLines) {
        $match = [regex]::Match(
            $line,
            '^> Task (?<task>\S+?)(?: (?<outcome>UP-TO-DATE|FROM-CACHE|SKIPPED|NO-SOURCE|FAILED))?$'
        )
        if ($match.Success) {
            $outcome = $match.Groups['outcome'].Value
            if ($outcome.Length -eq 0) {
                $outcome = 'EXECUTED'
            }
            $outcomes[$match.Groups['task'].Value] = $outcome
        }
    }
    return $outcomes
}

function Assert-FreshProducerOutput {
    param(
        [Parameter(Mandatory = $true)][int]$ExitCode,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string[]]$OutputLines,
        [Parameter(Mandatory = $true)][datetime]$StartedUtc,
        [Parameter(Mandatory = $true)][string]$ProducerOutputDirectory
    )

    if ($ExitCode -ne 0) {
        throw "Baseline Profile Gradle invocation failed with exit code $ExitCode."
    }
    $joinedOutput = [string]::Join("`n", $OutputLines)
    if ($joinedOutput -match '(?i)failed to pull') {
        throw 'Baseline Profile output pull failed.'
    }
    $outcomes = Get-GradleTaskOutcomes -OutputLines $OutputLines
    if (-not $outcomes.Contains($script:ProducerTask)) {
        throw "Gradle output did not report the producer task: $($script:ProducerTask)"
    }
    if ($outcomes[$script:ProducerTask] -ne 'EXECUTED') {
        throw "The producer task did not execute: $($outcomes[$script:ProducerTask])"
    }
    if (-not (Test-Path -LiteralPath $ProducerOutputDirectory -PathType Container)) {
        throw 'The producer output directory is missing.'
    }
    $freshProfiles = @(
        Get-ChildItem -LiteralPath $ProducerOutputDirectory -Recurse -File -Filter '*baseline-prof*.txt' |
            Where-Object { $_.LastWriteTimeUtc -ge $StartedUtc }
    )
    if ($freshProfiles.Count -eq 0) {
        throw 'No fresh pulled producer profile was written during this invocation.'
    }
    $records = @($freshProfiles | ForEach-Object { Get-CanonicalProfileRecord -Path $_.FullName })
    foreach ($record in $records | Select-Object -Skip 1) {
        if (-not (Test-CanonicalProfileEquality -First $records[0] -Second $record)) {
            throw 'Fresh producer profile copies disagree within one invocation.'
        }
    }
    return [pscustomobject]@{
        TaskOutcomes = $outcomes
        Profiles = $records
        CanonicalRecord = $records[0]
    }
}

function Assert-InvocationContent {
    param(
        [Parameter(Mandatory = $true)]$ProducerResult,
        [Parameter(Mandatory = $true)][string]$MergedProfilePath,
        [Parameter(Mandatory = $true)][string]$SourceProfilePath
    )

    $merged = Get-CanonicalProfileRecord -Path $MergedProfilePath
    $source = Get-CanonicalProfileRecord -Path $SourceProfilePath
    $producer = $ProducerResult.CanonicalRecord
    if (
        -not (Test-CanonicalProfileEquality -First $producer -Second $merged) -or
        -not (Test-CanonicalProfileEquality -First $producer -Second $source)
    ) {
        throw 'Fresh producer, merged, and source profiles must have exact canonical rule and flag equality.'
    }
    return [pscustomobject]@{
        Producer = $ProducerResult.CanonicalRecord
        Merged = $merged
        Source = $source
    }
}

function Assert-GenerationPair {
    param(
        [Parameter(Mandatory = $true)]$First,
        [Parameter(Mandatory = $true)]$Second
    )

    if ($First.SourceRevision -ne $Second.SourceRevision) {
        throw 'Generation source revisions differ.'
    }
    if ($First.AppApkSha256 -ne $Second.AppApkSha256 -or $First.TestApkSha256 -ne $Second.TestApkSha256) {
        throw 'Generation APK identities differ.'
    }
    if (-not (Test-CanonicalProfileEquality -First $First.Profile.Source -Second $Second.Profile.Source)) {
        throw 'Baseline Profile generation was not reproducible with exact rule and flag equality.'
    }
}

function New-ExclusiveEvidenceDirectory {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (Test-Path -LiteralPath $Path) {
        throw "Evidence path already exists and will not be overwritten: $Path"
    }
    New-Item -ItemType Directory -Path $Path | Out-Null
}

function Copy-AvailableEvidenceTree {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    if (-not (Test-Path -LiteralPath $Source -PathType Container)) {
        return
    }
    New-Item -ItemType Directory -Path $Destination | Out-Null
    Get-ChildItem -LiteralPath $Source -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $Destination -Recurse
    }
}

function Move-PreexistingEvidenceTree {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    if (-not (Test-Path -LiteralPath $Source -PathType Container)) {
        return
    }
    if (Test-Path -LiteralPath $Destination) {
        throw "Preexisting evidence destination already exists: $Destination"
    }
    Move-Item -LiteralPath $Source -Destination $Destination
}

function Write-JsonFile {
    param(
        [Parameter(Mandatory = $true)]$Value,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $Value | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $Path -Encoding utf8NoBOM
}

function Get-RetainedFileRecords {
    param([Parameter(Mandatory = $true)][string]$EvidenceDirectory)

    $root = [System.IO.Path]::GetFullPath($EvidenceDirectory)
    return @(
        Get-ChildItem -LiteralPath $root -Recurse -File | Sort-Object FullName | ForEach-Object {
            [ordered]@{
                path = [System.IO.Path]::GetRelativePath($root, $_.FullName).Replace('\', '/')
                byte_count = $_.Length
                sha256 = Get-FileSha256 -Path $_.FullName
            }
        }
    )
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

function Invoke-GradleCommand {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$LogPath,
        [Parameter(Mandatory = $true)][string[]]$GradleArguments,
        [ValidateRange(1, 1800)][int]$TimeoutSeconds = $script:ProducerTimeoutSeconds,
        [scriptblock]$JobTerminator = {
            param([Parameter(Mandatory = $true)][IntPtr]$Job)

            if (-not [NenePixelBaselineProfile.JobNativeMethods]::TerminateJobObject($Job, 124)) {
                throw "TerminateJobObject failed with Win32 error " +
                    "$([Runtime.InteropServices.Marshal]::GetLastWin32Error())."
            }
        }
    )

    $gradleWrapper = Join-Path $RepositoryRoot 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
        throw "Gradle Wrapper is missing: $gradleWrapper"
    }
    $launcherPath = "$LogPath.launcher.ps1"
    $launcher = @'
param(
    [Parameter(Mandatory = $true)][string]$GateName,
    [Parameter(Mandatory = $true)][string]$RepositoryRoot,
    [Parameter(Mandatory = $true)][string]$LogPath,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArguments
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
        & (Join-Path $RepositoryRoot 'gradlew.bat') @GradleArguments *> $LogPath
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
    [System.IO.File]::WriteAllText($launcherPath, $launcher, [System.Text.UTF8Encoding]::new($false))
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
                $LogPath
            ) + @($GradleArguments) + @('--no-daemon')) {
            $startInfo.ArgumentList.Add($argument)
        }
        $process.StartInfo = $startInfo
        if (-not $process.Start()) {
            throw 'Gradle launcher process did not start.'
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
                $message = "Gradle invocation timed out after $TimeoutSeconds seconds, and its job " +
                    'could not be confirmed terminated; tracked-file restoration is blocked.'
                if ($null -ne $terminationFailure) {
                    $message += " Termination failure: $terminationFailure"
                }
                $exception = [System.InvalidOperationException]::new($message)
                $exception.Data[$script:RestorationBlockedDataKey] = $true
                throw $exception
            }
            throw "Gradle invocation timed out after $TimeoutSeconds seconds; its job reached zero active processes."
        }
        $jobConfirmedEmpty = $true
        $process.WaitForExit()
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
                $message = 'Gradle job failure could not be followed by confirmed job termination; ' +
                    "tracked-file restoration is blocked. Original failure: $($originalException.Message)"
                if ($null -ne $terminationFailure) {
                    $message += " Termination failure: $terminationFailure"
                }
                $blockedException = [System.InvalidOperationException]::new($message, $originalException)
                $blockedException.Data[$script:RestorationBlockedDataKey] = $true
                $originalException = $blockedException
            }
        }
        if ($started -and -not $assigned -and -not $process.HasExited) {
            $process.Kill()
            $process.WaitForExit(10000) | Out-Null
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

function Get-ManifestProfileRecord {
    param([Parameter(Mandatory = $true)]$Record)

    return [ordered]@{
        raw_byte_count = $Record.RawByteCount
        raw_sha256 = $Record.RawSha256
        rule_count = $Record.RuleCount
        canonical_byte_count = $Record.CanonicalByteCount
        canonical_sha256 = $Record.CanonicalSha256
    }
}

function Invoke-GenerationInvocation {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$EvidenceRoot,
        [Parameter(Mandatory = $true)][string]$EvidenceIdentity,
        [Parameter(Mandatory = $true)][int]$Ordinal,
        [Parameter(Mandatory = $true)][scriptblock]$GradleInvoker
    )

    $invocationDirectory = Join-Path $EvidenceRoot ("invocation-{0}" -f $Ordinal)
    New-ExclusiveEvidenceDirectory -Path $invocationDirectory
    $startedUtc = [datetime]::UtcNow
    $endedUtc = $startedUtc
    $failures = [System.Collections.Generic.List[string]]::new()
    $sourceRevision = $null
    $logPath = Join-Path $invocationDirectory 'gradle-output.log'
    $gradle = $null
    $gradleExitCode = $null
    $gradleOutputLines = $null
    $taskOutcomes = [ordered]@{}
    $restorationBlocked = $false
    $producerOutput = Join-Path $RepositoryRoot $script:ProducerOutputRelativePath
    $producerResults = Join-Path $RepositoryRoot $script:ProducerResultsRelativePath
    $mergedPath = Join-Path $RepositoryRoot $script:MergedRelativePath
    $sourcePath = Join-Path $RepositoryRoot $script:ProfileRelativePath
    $producer = $null
    $profile = $null
    $appApkSha256 = $null
    $testApkSha256 = $null

    try {
        $sourceRevision = (& git -C $RepositoryRoot rev-parse HEAD).Trim()
    }
    catch {
        $failures.Add("source revision: $($_.Exception.Message)")
    }
    if ($failures.Count -eq 0) {
        try {
            Move-PreexistingEvidenceTree -Source $producerOutput `
                -Destination (Join-Path $invocationDirectory 'preexisting-producer-output')
            Move-PreexistingEvidenceTree -Source $producerResults `
                -Destination (Join-Path $invocationDirectory 'preexisting-producer-results')
        }
        catch {
            $failures.Add("preexisting evidence retention: $($_.Exception.Message)")
        }
    }
    if ($failures.Count -eq 0) {
        try {
            $gradle = & $GradleInvoker -RepositoryRoot $RepositoryRoot -LogPath $logPath `
                -GradleArguments @(':app:android:generateBaselineProfile', '--console=plain')
        }
        catch {
            $failures.Add("native invocation: $($_.Exception.Message)")
            $restorationBlocked = $_.Exception.Data[$script:RestorationBlockedDataKey] -eq $true
            if (-not (Test-Path -LiteralPath $logPath -PathType Leaf)) {
                $_.Exception.ToString() | Set-Content -LiteralPath $logPath -Encoding utf8NoBOM
            }
        }
    }
    $endedUtc = [datetime]::UtcNow

    if (-not $restorationBlocked) {
        foreach ($tree in @(
                @($producerOutput, 'producer-output'),
                @($producerResults, 'producer-results')
            )) {
            try {
                Copy-AvailableEvidenceTree -Source $tree[0] `
                    -Destination (Join-Path $invocationDirectory $tree[1])
            }
            catch {
                $failures.Add("$($tree[1]) retention: $($_.Exception.Message)")
            }
        }
        foreach ($entry in @(@($mergedPath, 'merged-profile.txt'), @($sourcePath, 'source-profile.txt'))) {
            try {
                if (Test-Path -LiteralPath $entry[0] -PathType Leaf) {
                    Copy-Item -LiteralPath $entry[0] -Destination (Join-Path $invocationDirectory $entry[1])
                }
            }
            catch {
                $failures.Add("$($entry[1]) retention: $($_.Exception.Message)")
            }
        }
    }

    if ($null -ne $gradle -and -not $restorationBlocked) {
        try {
            $gradleExitCode = [int]$gradle.ExitCode
            $gradleOutputLines = [string[]]@($gradle.OutputLines)
        }
        catch {
            $failures.Add("native output binding: $($_.Exception.Message)")
        }
    }
    if ($null -ne $gradleOutputLines) {
        try {
            $taskOutcomes = Get-GradleTaskOutcomes -OutputLines $gradleOutputLines
        }
        catch {
            $failures.Add("task outcome parsing: $($_.Exception.Message)")
        }
        try {
            $producer = Assert-FreshProducerOutput -ExitCode $gradleExitCode `
                -OutputLines $gradleOutputLines -StartedUtc $startedUtc `
                -ProducerOutputDirectory $producerOutput
        }
        catch {
            $failures.Add("producer freshness: $($_.Exception.Message)")
        }
    }
    if ($null -ne $producer) {
        try {
            $profile = Assert-InvocationContent -ProducerResult $producer -MergedProfilePath $mergedPath `
                -SourceProfilePath $sourcePath
        }
        catch {
            $failures.Add("profile content: $($_.Exception.Message)")
        }
    }
    if ($null -ne $gradle -and -not $restorationBlocked) {
        try {
            $appApkSha256 = Get-FileSha256 -Path (Join-Path $RepositoryRoot $script:AppApkRelativePath)
        }
        catch {
            $failures.Add("app APK identity: $($_.Exception.Message)")
        }
        try {
            $testApkSha256 = Get-FileSha256 -Path (Join-Path $RepositoryRoot $script:TestApkRelativePath)
        }
        catch {
            $failures.Add("test APK identity: $($_.Exception.Message)")
        }
    }

    $retainedFiles = @()
    try {
        $retainedFiles = Get-RetainedFileRecords -EvidenceDirectory $invocationDirectory
    }
    catch {
        $failures.Add("retained evidence hashing: $($_.Exception.Message)")
    }
    $failure = if ($failures.Count -eq 0) { $null } else { [string]::Join(' | ', $failures) }

    $manifest = [ordered]@{
        schema = $script:EvidenceSchema
        evidence_id = $EvidenceIdentity
        invocation = $Ordinal
        status = if ($null -eq $failure) { 'valid' } else { 'invalid' }
        failure = $failure
        started_utc = $startedUtc.ToString('o')
        ended_utc = $endedUtc.ToString('o')
        source_revision = $sourceRevision
        timeout_seconds = $script:ProducerTimeoutSeconds
        restoration_blocked = $restorationBlocked
        gradle_exit_code = $gradleExitCode
        task_outcomes = $taskOutcomes
        producer_profiles = if ($null -eq $producer) {
            $null
        } else {
            @($producer.Profiles | ForEach-Object { Get-ManifestProfileRecord $_ })
        }
        merged_profile = if ($null -eq $profile) { $null } else { Get-ManifestProfileRecord $profile.Merged }
        source_profile = if ($null -eq $profile) { $null } else { Get-ManifestProfileRecord $profile.Source }
        app_apk_sha256 = $appApkSha256
        test_apk_sha256 = $testApkSha256
        retained_files = $retainedFiles
    }
    $manifestPath = Join-Path $invocationDirectory 'manifest.json'
    Write-JsonFile -Value $manifest -Path $manifestPath
    if ($null -ne $failure) {
        $exception = [System.InvalidOperationException]::new(
            "Generation invocation $Ordinal is invalid: $failure"
        )
        if ($restorationBlocked) {
            $exception.Data[$script:RestorationBlockedDataKey] = $true
        }
        throw $exception
    }
    return [pscustomobject]@{
        SourceRevision = $sourceRevision
        AppApkSha256 = $appApkSha256
        TestApkSha256 = $testApkSha256
        Profile = $profile
        ManifestSha256 = Get-FileSha256 -Path $manifestPath
    }
}

function Invoke-BaselineProfileEvidenceGeneration {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$EvidenceIdentity,
        [scriptblock]$GradleInvoker = {
            param(
                [Parameter(Mandatory = $true)][string]$RepositoryRoot,
                [Parameter(Mandatory = $true)][string]$LogPath,
                [Parameter(Mandatory = $true)][string[]]$GradleArguments
            )

            $timeoutSeconds = if ($GradleArguments[0] -eq 'validateBaselineProfile') {
                $script:ValidationTimeoutSeconds
            } else {
                $script:ProducerTimeoutSeconds
            }
            Invoke-GradleCommand -RepositoryRoot $RepositoryRoot -LogPath $LogPath `
                -GradleArguments $GradleArguments -TimeoutSeconds $timeoutSeconds
        }
    )

    if (@(& git -C $RepositoryRoot status --porcelain).Count -ne 0) {
        throw 'Baseline Profile generation must start from a clean standalone clone.'
    }
    $evidenceRoot = Join-Path $RepositoryRoot "build/reports/baseline-profile-generation/$EvidenceIdentity"
    New-ExclusiveEvidenceDirectory -Path $evidenceRoot
    $profilePath = Join-Path $RepositoryRoot $script:ProfileRelativePath
    $hashPath = Join-Path $RepositoryRoot $script:HashRelativePath
    $originalProfile = Get-FileState -Path $profilePath
    $originalHash = Get-FileState -Path $hashPath
    if ($originalProfile.Exists) {
        [System.IO.File]::WriteAllBytes(
            (Join-Path $evidenceRoot 'pre-generation-source-profile.txt'),
            $originalProfile.Bytes
        )
    }
    if ($originalHash.Exists) {
        [System.IO.File]::WriteAllBytes(
            (Join-Path $evidenceRoot 'pre-generation-hash.txt'),
            $originalHash.Bytes
        )
    }

    try {
        $first = Invoke-GenerationInvocation -RepositoryRoot $RepositoryRoot -EvidenceRoot $evidenceRoot `
            -EvidenceIdentity $EvidenceIdentity -Ordinal 1 -GradleInvoker $GradleInvoker
        Restore-FileState -Path $profilePath -State $originalProfile
        Restore-FileState -Path $hashPath -State $originalHash
        $second = Invoke-GenerationInvocation -RepositoryRoot $RepositoryRoot -EvidenceRoot $evidenceRoot `
            -EvidenceIdentity $EvidenceIdentity -Ordinal 2 -GradleInvoker $GradleInvoker

        $pairFailure = $null
        try {
            Assert-GenerationPair -First $first -Second $second
        }
        catch {
            $pairFailure = $_.Exception.Message
        }
        $pairManifest = [ordered]@{
            schema = $script:EvidenceSchema
            evidence_id = $EvidenceIdentity
            status = if ($null -eq $pairFailure) { 'matched' } else { 'mismatch' }
            failure = $pairFailure
            source_revision = $first.SourceRevision
            app_apk_sha256 = $first.AppApkSha256
            test_apk_sha256 = $first.TestApkSha256
            canonical_rule_count = $first.Profile.Source.RuleCount
            canonical_sha256 = $first.Profile.Source.CanonicalSha256
            producer_timeout_seconds = $script:ProducerTimeoutSeconds
            validation_timeout_seconds = $script:ValidationTimeoutSeconds
            invocation_manifest_sha256 = @($first.ManifestSha256, $second.ManifestSha256)
        }
        $pairManifestPath = Join-Path $evidenceRoot 'pair-manifest.json'
        Write-JsonFile -Value $pairManifest -Path $pairManifestPath
        if ($null -ne $pairFailure) {
            throw $pairFailure
        }

        [System.IO.File]::WriteAllBytes($profilePath, $second.Profile.Source.CanonicalBytes)
        Set-Content -LiteralPath $hashPath -Value $second.Profile.Source.CanonicalSha256 -Encoding ascii

        $validationLogPath = Join-Path $evidenceRoot 'validation-output.log'
        $validation = & $GradleInvoker -RepositoryRoot $RepositoryRoot -LogPath $validationLogPath `
            -GradleArguments @('validateBaselineProfile', '--console=plain')
        if ($validation.ExitCode -ne 0) {
            throw "validateBaselineProfile failed with exit code $($validation.ExitCode)."
        }
        $acceptanceManifest = [ordered]@{
            schema = $script:EvidenceSchema
            evidence_id = $EvidenceIdentity
            status = 'accepted'
            pair_manifest_sha256 = Get-FileSha256 -Path $pairManifestPath
            validation_output_sha256 = Get-FileSha256 -Path $validationLogPath
            canonical_sha256 = $second.Profile.Source.CanonicalSha256
            producer_timeout_seconds = $script:ProducerTimeoutSeconds
            validation_timeout_seconds = $script:ValidationTimeoutSeconds
        }
        $pendingAcceptanceManifestPath = Join-Path $evidenceRoot 'acceptance-manifest.pending.json'
        $acceptanceManifestPath = Join-Path $evidenceRoot 'acceptance-manifest.json'
        Write-JsonFile -Value $acceptanceManifest -Path $pendingAcceptanceManifestPath
        Read-BaselineProfileAcceptanceEvidence -AcceptanceManifestPath $pendingAcceptanceManifestPath `
            -EvidenceRoot $evidenceRoot `
            -ExpectedAcceptanceManifestSha256 (Get-FileSha256 $pendingAcceptanceManifestPath) `
            -ExpectedSourceRevision $second.SourceRevision `
            -ExpectedAppApkSha256 $second.AppApkSha256 `
            -ExpectedTestApkSha256 $second.TestApkSha256 `
            -ExpectedPairManifestSha256 (Get-FileSha256 $pairManifestPath) `
            -ExpectedCanonicalSha256 $second.Profile.Source.CanonicalSha256 | Out-Null
        [System.IO.File]::Move($pendingAcceptanceManifestPath, $acceptanceManifestPath)
    }
    catch {
        if ($_.Exception.Data[$script:RestorationBlockedDataKey] -eq $true) {
            throw
        }
        Restore-FileState -Path $profilePath -State $originalProfile
        Restore-FileState -Path $hashPath -State $originalHash
        throw
    }
    Write-Output "BASELINE_PROFILE_EVIDENCE=pass"
    Write-Output "EVIDENCE_SCHEMA=$($script:EvidenceSchema)"
    Write-Output "EVIDENCE_DIRECTORY=$evidenceRoot"
    Write-Output "CANONICAL_SHA256=$($second.Profile.Source.CanonicalSha256)"
}

if ($MyInvocation.InvocationName -ne '.') {
    if ([string]::IsNullOrWhiteSpace($EvidenceId)) {
        throw 'EvidenceId is required.'
    }
    $repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
    Invoke-BaselineProfileEvidenceGeneration -RepositoryRoot $repositoryRoot -EvidenceIdentity $EvidenceId
}

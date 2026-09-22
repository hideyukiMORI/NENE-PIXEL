<#
tools/prepare-device-checkout.ps1 - build a worktree, install it keeping app data, preserve the
device recovery files, and record the NEXT launch target. It records; it does not launch.
Arguments
  -Worktree <path>  required; an existing git worktree root (created with -Ref when absent)
  -Ref <rev>        optional; used only for `git worktree add <path> <rev>`
  -Issue <int>      required; guard = issue-<Issue>-user-recovery-<yyyyMMdd-HHmmss>
  -Serial <serial>  optional; default = the only device in `adb devices`
  -Id <name>        optional; default = timestamp. Output: build/reports/device-checkout/<Id>/
                    under the repository root (must not exist yet)
  -WhatIf           print {"mode","steps","manifest_shape","preservation_shape"}; no git/gradle/adb
Output files
  manifest.json      schema nene-pixel-device-checkout-v1: worktree, commit, branch, dirty,
                     apk{path,sha256}, device{serial}, guard, preserved[], snapshot{sha256},
                     created_utc
  preservation.json  schema nene-pixel-device-checkout-preservation-v1: package, serial, guard,
                     directories, snapshot{path,sha256,byte_count}, preserved[], guard_listing
  snapshot.tar, gradle-assembleDebug.log, <NNN>-<label>.{command.json,stdout,stderr} per adb call
Exit codes
  0  prepared (or -WhatIf printed its plan)
  1  a build or device step failed after the preconditions held; nothing is removed
  2  precondition: bad arguments or worktree, no/ambiguous device, keyguard showing, install
     failed, run-as unavailable, app process live, guard or output directory already present
Examples
  pwsh -NoProfile -File tools/prepare-device-checkout.ps1 -Worktree ../wt -Issue 128 -WhatIf
  pwsh -NoProfile -File tools/prepare-device-checkout.ps1 -Worktree ../wt -Issue 128 -Id run1
Never done here
  launching the app (am start), starting a measurement, pm clear, uninstall, deleting files on
  the device or host, unlocking the keyguard, gradle clean / --rerun-tasks / --no-daemon.
  Recovery files are moved into no_backup/<guard> with run-as mv, never removed.
#>
[CmdletBinding()]
param(
    [string]$Worktree,
    [string]$Ref,
    [string]$Issue,
    [string]$Serial,
    [string]$Id,
    [switch]$WhatIf
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

class PreconditionFailure : System.Exception {
    PreconditionFailure([string]$message) : base($message) {}
}

$Package = 'io.github.hideyukimori.nenepixel'
$GradleTask = ':app:android:assembleDebug'
$DataDirectories = @('files', 'no_backup', 'shared_prefs', 'databases')
$RecoveryFiles = @('nene-pixel-recovery-v1', 'nene-pixel-recovery-v1.new', 'nene-pixel-recovery-v1.bak')
$script:CallIndex = 0
$script:OutputDirectory = $null
$script:PendingLogs = [System.Collections.Generic.List[object]]::new()

function Stop-Precondition([string]$Message) { throw [PreconditionFailure]::new($Message) }

function Write-CallLog($Entry) {
    if (-not $script:OutputDirectory) { $script:PendingLogs.Add($Entry); return }
    $stem = Join-Path $script:OutputDirectory ('{0:D3}-{1}' -f $Entry.index, $Entry.label)
    [ordered]@{ command = $Entry.command; exit_code = $Entry.exit_code } | ConvertTo-Json -Compress |
        Set-Content -LiteralPath "$stem.command.json" -Encoding utf8NoBOM
    [IO.File]::WriteAllBytes("$stem.stdout", $Entry.stdout_bytes)
    [IO.File]::WriteAllText("$stem.stderr", $Entry.stderr)
}

# The single native-process path for git, gradlew and adb. stdout bytes go to -StdoutPath when
# given (tar archive, gradle log). Calls with -Label are logged as <NNN>-<label>.* files.
function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$Arguments = @(),
        [string]$WorkingDirectory,
        [string]$Label,
        [string]$StdoutPath,
        [int[]]$AllowedExitCodes = @(0),
        [switch]$NoThrow
    )
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FilePath
    foreach ($argument in $Arguments) { $startInfo.ArgumentList.Add($argument) }
    if ($WorkingDirectory) { $startInfo.WorkingDirectory = $WorkingDirectory }
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($startInfo)
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $sink = if ($StdoutPath) { [IO.File]::Open($StdoutPath, [IO.FileMode]::CreateNew) } else { [IO.MemoryStream]::new() }
    try { $process.StandardOutput.BaseStream.CopyTo($sink) } finally { if ($StdoutPath) { $sink.Dispose() } }
    $process.WaitForExit()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    $bytes = [byte[]]::new(0)
    if (-not $StdoutPath) { $bytes = $sink.ToArray() }
    $record = [pscustomobject]@{
        command = @($FilePath) + $Arguments; exit_code = $process.ExitCode
        stdout = [Text.Encoding]::UTF8.GetString($bytes); stderr = $stderr
    }
    if ($Label) {
        $script:CallIndex++
        Write-CallLog ([pscustomobject]@{
            index = $script:CallIndex; label = $Label; command = $record.command
            exit_code = $record.exit_code; stdout_bytes = $bytes; stderr = $stderr
        })
    }
    if (-not $NoThrow -and $AllowedExitCodes -notcontains $process.ExitCode) {
        $name = if ($Label) { $Label } else { $FilePath }
        throw "${name}: exit $($process.ExitCode). $($stderr.Trim())"
    }
    return $record
}

function Get-Sha256([string]$Path) {
    $hash = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -cnotmatch '^[0-9a-f]{64}$') { throw "Unexpected SHA-256 for $Path." }
    return $hash
}

function ConvertTo-ComparablePath([string]$Path) {
    return [IO.Path]::GetFullPath($Path).TrimEnd('\', '/').Replace('\', '/').ToLowerInvariant()
}

$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$stamp = [DateTime]::Now.ToString('yyyyMMdd-HHmmss')

try {
    if (-not $Issue -or $Issue -notmatch '^[1-9][0-9]*$') { Stop-Precondition '-Issue <integer> is required.' }
    if (-not $Worktree) { Stop-Precondition '-Worktree <path> is required.' }
    if (-not $Id) { $Id = $stamp }
    if ($Id -notmatch '^[A-Za-z0-9._-]+$' -or $Id -in @('.', '..')) { Stop-Precondition "Invalid -Id '$Id'." }
    $guard = "issue-$Issue-user-recovery-$stamp"
    $guardPath = "no_backup/$guard"
    $outputPath = Join-Path $repositoryRoot "build/reports/device-checkout/$Id"
    if (Test-Path -LiteralPath $outputPath) { Stop-Precondition "Output directory already exists: $outputPath" }

    if ($WhatIf) {
        $steps = @(
            "resolve worktree '$Worktree' (git worktree add when absent and -Ref is given); require rev-parse --show-toplevel == path",
            'select the device: -Serial or the only entry of adb devices; adb get-state == device',
            'require isKeyguardShowing=false in adb shell dumpsys window (never unlock)',
            "create $outputPath (must be new)",
            'record git rev-parse HEAD, abbrev-ref HEAD and status --porcelain',
            "gradlew.bat $GradleTask --offline > gradle-assembleDebug.log",
            'hash the only APK under app/android/build/outputs/apk/debug',
            'adb install -r -d <apk> (keeps data; a signature mismatch stops here)',
            "require adb shell run-as $Package ls and an empty pidof $Package",
            "adb exec-out run-as $Package tar -cf - <existing of $($DataDirectories -join ', ')> > snapshot.tar",
            "run-as mkdir -p $guardPath (must be absent), then run-as mv each existing no_backup/{$($RecoveryFiles -join ', ')} into it",
            'write preservation.json and manifest.json, then stop'
        )
        [ordered]@{
            mode = 'whatif'
            output_directory = $outputPath
            steps = $steps
            manifest_shape = [ordered]@{
                schema = 'nene-pixel-device-checkout-v1'; id = $Id; issue = [int]$Issue
                worktree = '<absolute path>'; commit = '<40 hex>'; branch = '<name>'; dirty = $false
                apk = [ordered]@{ path = '<absolute path>'; sha256 = '<64 hex>' }
                device = [ordered]@{ serial = '<adb serial>' }
                guard = $guard; preserved = @('<file name>'); snapshot = [ordered]@{ sha256 = '<64 hex>' }
                created_utc = '<yyyy-MM-ddTHH:mm:ssZ>'
            }
            preservation_shape = [ordered]@{
                schema = 'nene-pixel-device-checkout-preservation-v1'; package = $Package
                serial = '<adb serial>'; guard = $guardPath; directories = $DataDirectories
                snapshot = [ordered]@{ path = 'snapshot.tar'; sha256 = '<64 hex>'; byte_count = 0 }
                preserved = @('<file name>'); guard_listing = @('<file name>'); state = 'preserved'
            }
        } | ConvertTo-Json -Depth 6 | Write-Output
        exit 0
    }

    # Worktree
    if (-not (Test-Path -LiteralPath $Worktree -PathType Container)) {
        if (-not $Ref) { Stop-Precondition "Worktree '$Worktree' does not exist and no -Ref was given." }
        $added = Invoke-Native -FilePath 'git' -Arguments @('-C', $repositoryRoot, 'worktree', 'add', $Worktree, $Ref) -NoThrow
        if ($added.exit_code -ne 0) { Stop-Precondition "git worktree add failed: $($added.stderr.Trim())" }
    }
    $worktreePath = (Resolve-Path -LiteralPath $Worktree).Path
    $top = Invoke-Native -FilePath 'git' -Arguments @('-C', $worktreePath, 'rev-parse', '--show-toplevel') -NoThrow
    if ($top.exit_code -ne 0 -or (ConvertTo-ComparablePath $top.stdout.Trim()) -ne (ConvertTo-ComparablePath $worktreePath)) {
        Stop-Precondition "'$worktreePath' is not the root of a git worktree."
    }

    # Device
    $adbCommand = @(Get-Command 'adb' -CommandType Application -ErrorAction SilentlyContinue)
    if ($adbCommand.Count -eq 0) { Stop-Precondition 'adb was not found on PATH.' }
    $adb = $adbCommand[0].Source
    if (-not $Serial) {
        $listed = Invoke-Native -FilePath $adb -Arguments @('devices') -Label 'devices'
        $attached = @($listed.stdout -split "`r?`n" | Where-Object { $_ -match '^\S+\s+device$' } |
            ForEach-Object { ($_ -split '\s+')[0] })
        if ($attached.Count -ne 1) { Stop-Precondition "Expected exactly one device, found $($attached.Count); pass -Serial." }
        $Serial = $attached[0]
    }
    $state = Invoke-Native -FilePath $adb -Arguments @('-s', $Serial, 'get-state') -Label 'get-state' -NoThrow
    if ($state.exit_code -ne 0 -or $state.stdout.Trim() -ne 'device') {
        Stop-Precondition "Device '$Serial' is not ready: $($state.stderr.Trim())"
    }
    $window = Invoke-Native -FilePath $adb -Arguments @('-s', $Serial, 'shell', 'dumpsys', 'window') -Label 'keyguard'
    if ($window.stdout -notmatch 'isKeyguardShowing=false') {
        Stop-Precondition 'The keyguard is showing (or unreadable). Ask hide to unlock the device, then rerun with a new -Id.'
    }

    New-Item -ItemType Directory -Path $outputPath | Out-Null
    $script:OutputDirectory = (Resolve-Path -LiteralPath $outputPath).Path
    foreach ($entry in $script:PendingLogs) { Write-CallLog $entry }
    $script:PendingLogs.Clear()

    # Commit identity and build
    $commit = (Invoke-Native -FilePath 'git' -Arguments @('-C', $worktreePath, 'rev-parse', 'HEAD')).stdout.Trim()
    if ($commit -cnotmatch '^[0-9a-f]{40}$') { throw "Unexpected worktree commit '$commit'." }
    $branch = (Invoke-Native -FilePath 'git' -Arguments @('-C', $worktreePath, 'rev-parse', '--abbrev-ref', 'HEAD')).stdout.Trim()
    $porcelain = (Invoke-Native -FilePath 'git' -Arguments @('-C', $worktreePath, 'status', '--porcelain')).stdout
    $dirty = -not [string]::IsNullOrWhiteSpace($porcelain)
    $gradlew = Join-Path $worktreePath 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradlew -PathType Leaf)) { Stop-Precondition "Missing $gradlew." }
    $gradleLog = Join-Path $script:OutputDirectory 'gradle-assembleDebug.log'
    $build = Invoke-Native -FilePath $gradlew -Arguments @($GradleTask, '--offline') -WorkingDirectory $worktreePath -StdoutPath $gradleLog -NoThrow
    Add-Content -LiteralPath $gradleLog -Value $build.stderr
    if ($build.exit_code -ne 0) { throw "assembleDebug failed (exit $($build.exit_code)); see $gradleLog." }
    $apkDirectory = Join-Path $worktreePath 'app/android/build/outputs/apk/debug'
    $apks = @(Get-ChildItem -LiteralPath $apkDirectory -Filter '*.apk' -File -ErrorAction SilentlyContinue)
    if ($apks.Count -ne 1) { Stop-Precondition "Expected exactly one debug APK in $apkDirectory, found $($apks.Count)." }
    $apkPath = $apks[0].FullName
    $apkSha = Get-Sha256 $apkPath

    # Install keeping data
    $install = Invoke-Native -FilePath $adb -Arguments @('-s', $Serial, 'install', '-r', '-d', $apkPath) -Label 'install' -NoThrow
    if ($install.exit_code -ne 0 -or ($install.stdout + $install.stderr) -notmatch '(?m)^Success') {
        Stop-Precondition "adb install -r -d failed (data kept): $(($install.stdout + $install.stderr).Trim())"
    }

    # Preservation
    $runAs = Invoke-Native -FilePath $adb -Arguments @('-s', $Serial, 'shell', 'run-as', $Package, 'ls') -Label 'run-as' -NoThrow
    if ($runAs.exit_code -ne 0) { Stop-Precondition "run-as $Package failed; install a debug build first. $($runAs.stderr.Trim())" }
    $live = Invoke-Native -FilePath $adb -Arguments @('-s', $Serial, 'shell', 'pidof', $Package) -Label 'pidof' -AllowedExitCodes @(0, 1)
    if ($live.stdout.Trim()) { Stop-Precondition "The app process is live (pid $($live.stdout.Trim())); nothing was moved." }
    function Test-DevicePath([string]$Path) {
        $probe = Invoke-Native -FilePath $adb -Arguments @('-s', $Serial, 'shell', 'run-as', $Package, 'ls', '-d', $Path) -Label 'exists' -AllowedExitCodes @(0, 1)
        return $probe.stdout.Trim() -eq $Path
    }
    if (Test-DevicePath $guardPath) { Stop-Precondition "Guard $guardPath already exists on the device." }
    $directories = @($DataDirectories | Where-Object { Test-DevicePath $_ })
    if ($directories.Count -eq 0) { throw 'No app data directory exists on the device.' }
    $snapshotPath = Join-Path $script:OutputDirectory 'snapshot.tar'
    $tarArguments = @('-s', $Serial, 'exec-out', 'run-as', $Package, 'tar', '-cf', '-') + $directories
    Invoke-Native -FilePath $adb -Arguments $tarArguments -Label 'snapshot' -StdoutPath $snapshotPath | Out-Null
    $snapshotSha = Get-Sha256 $snapshotPath
    $candidates = @($RecoveryFiles | Where-Object { Test-DevicePath "no_backup/$_" })
    Invoke-Native -FilePath $adb -Arguments @('-s', $Serial, 'shell', 'run-as', $Package, 'mkdir', '-p', $guardPath) -Label 'create-guard' | Out-Null
    $preserved = @()
    foreach ($name in $candidates) {
        Invoke-Native -FilePath $adb -Arguments @('-s', $Serial, 'shell', 'run-as', $Package, 'mv', "no_backup/$name", "$guardPath/$name") -Label 'isolate' | Out-Null
        $preserved += $name
    }
    $listing = Invoke-Native -FilePath $adb -Arguments @('-s', $Serial, 'shell', 'run-as', $Package, 'ls', $guardPath) -Label 'guard-listing'
    $guardListing = @($listing.stdout -split "`r?`n" | Where-Object { $_.Trim() } | ForEach-Object { $_.Trim() })
    foreach ($name in $preserved) {
        if ($guardListing -notcontains $name -or (Test-DevicePath "no_backup/$name")) { throw "Isolation of $name did not verify." }
    }
    [ordered]@{
        schema = 'nene-pixel-device-checkout-preservation-v1'; package = $Package; serial = $Serial
        guard = $guardPath; directories = $directories
        snapshot = [ordered]@{ path = 'snapshot.tar'; sha256 = $snapshotSha; byte_count = (Get-Item -LiteralPath $snapshotPath).Length }
        preserved = $preserved; guard_listing = $guardListing; adb_calls = $script:CallIndex; state = 'preserved'
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $script:OutputDirectory 'preservation.json') -Encoding utf8NoBOM

    # Next launch target: recorded only, never started here.
    $manifest = [ordered]@{
        schema = 'nene-pixel-device-checkout-v1'; id = $Id; issue = [int]$Issue
        worktree = $worktreePath; commit = $commit; branch = $branch; dirty = $dirty
        apk = [ordered]@{ path = $apkPath; sha256 = $apkSha }
        device = [ordered]@{ serial = $Serial }
        guard = $guard; preserved = $preserved; snapshot = [ordered]@{ sha256 = $snapshotSha }
        created_utc = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    }
    $json = $manifest | ConvertTo-Json -Depth 6
    $json | Set-Content -LiteralPath (Join-Path $script:OutputDirectory 'manifest.json') -Encoding utf8NoBOM
    $json | Write-Output
    exit 0
}
catch [PreconditionFailure] {
    [Console]::Error.WriteLine("prepare-device-checkout: precondition failed: $($_.Exception.Message)")
    exit 2
}
catch {
    [Console]::Error.WriteLine("prepare-device-checkout: failed: $($_.Exception.Message)")
    exit 1
}

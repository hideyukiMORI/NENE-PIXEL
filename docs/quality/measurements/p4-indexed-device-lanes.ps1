Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../bounded-native-command.ps1')

# S5 device lanes. Every entrypoint below is derived from the candidate and baseline androidTest sources;
# nothing here may be guessed. Sources of record:
#   P2AndroidRunIdentity.kt / P2AndroidMeasurementEnvironment.kt / P2AndroidFinalCommandProtocol.kt
#   P4HistoryRetentionWorkloads.kt / P2ProductionHistoryRetentionMeasurementTest.kt
#   P4LegacyImportRetentionMeasurementTest.kt
#   adapters/persistence AutosavePublicationDeviceEvidence.kt + AutosavePublicationEvidenceReportingRule.kt
$script:P4InstrumentationRunner = 'androidx.test.runner.AndroidJUnitRunner'
$script:P4PhysicalProfileId = 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36'
$script:P4CommandClass = 'io.github.hideyukimori.nenepixel.measurement.P2AndroidFinalCommandMeasurementTest'
$script:P4HistoryMemoryClass = 'io.github.hideyukimori.nenepixel.measurement.P2ProductionHistoryRetentionMeasurementTest'
$script:P4LegacyMemoryClass = 'io.github.hideyukimori.nenepixel.measurement.P4LegacyImportRetentionMeasurementTest'
$script:P4PublicationClass = 'io.github.hideyukimori.nenepixel.adapters.persistence.AutosavePublicationDeviceEvidence'
$script:P4CommandWarmups = 5
$script:P4CommandSamples = 200
$script:P4MemoryFamilyBySlot = @{
    'baseline-common' = 'baseline-common-drawing-history'
    'candidate-common' = 'candidate-common-indexed-history'
    'candidate-palette' = 'candidate-palette-history'
    'candidate-import' = 'candidate-legacy-import'
}
$script:P4InstallTimeoutSeconds = 120
$script:P4DexoptTimeoutSeconds = 120
$script:P4ProbeTimeoutSeconds = 30
$script:P4PrivateFileTimeoutSeconds = 120
$script:P4InnerTimeoutReserveSeconds = 20

function Assert-P4PackageName {
    param([Parameter(Mandatory = $true)][string]$Value, [Parameter(Mandatory = $true)][string]$Name)
    if ($Value -cnotmatch '^[a-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$') { throw "Invalid Android package for $Name : $Value" }
    return $Value
}

function Get-P4LanePackages {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Manifest,
        [Parameter(Mandatory = $true)][ValidateSet('baseline', 'candidate')][string]$Role
    )
    $artifacts = $Manifest.roles[$Role].artifacts
    foreach ($kind in @('app_debug', 'test_debug', 'app_release_like', 'publication_test')) {
        if (-not $artifacts.Contains($kind)) { throw "Manifest role $Role is missing artifact '$kind'." }
    }
    $application = Assert-P4PackageName ([string]$artifacts.app_debug.target_package) "$Role.app_debug.target_package"
    $applicationTest = Assert-P4PackageName ([string]$artifacts.test_debug.test_package) "$Role.test_debug.test_package"
    $publicationTest = Assert-P4PackageName ([string]$artifacts.publication_test.test_package) "$Role.publication_test.test_package"
    if ([string]$artifacts.app_release_like.target_package -cne $application -or
        [string]$artifacts.test_debug.target_package -cne $application) {
        throw "Manifest role $Role does not target one application package."
    }
    $distinct = @($application, $applicationTest, $publicationTest) | Select-Object -Unique
    if (@($distinct).Count -ne 3) { throw "Manifest role $Role reuses one package across the three device identities." }
    return [ordered]@{
        application = $application
        application_test = $applicationTest
        publication_test = $publicationTest
        publication_target = Assert-P4PackageName ([string]$artifacts.publication_test.target_package) "$Role.publication_test.target_package"
    }
}

function Get-P4InstrumentationArguments {
    param(
        [Parameter(Mandatory = $true)][string]$TestPackage,
        [Parameter(Mandatory = $true)][string]$Class,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Arguments
    )
    $list = [Collections.Generic.List[string]]::new()
    foreach ($token in @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $Class)) { $list.Add($token) }
    foreach ($key in $Arguments.Keys) {
        $value = [string]$Arguments[$key]
        if ([string]::IsNullOrWhiteSpace($value) -or $value -match '[\s"]') {
            throw "Instrumentation argument '$key' must be a single non-empty token."
        }
        $list.Add('-e')
        $list.Add([string]$key)
        $list.Add($value)
    }
    $list.Add("$TestPackage/$script:P4InstrumentationRunner")
    return $list.ToArray()
}

function Get-P4FrameCollectorParameters {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Manifest,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Slot
    )
    if (-not $Manifest.Contains('frame_experiment')) { throw 'The manifest declares no frame experiment.' }
    $experiment = $Manifest.frame_experiment
    foreach ($key in @('directory', 'hypothesis', 'expected_affected_cost', 'correctness_risk', 'stop_conditions')) {
        if (-not $experiment.Contains($key) -or [string]::IsNullOrWhiteSpace([string]$experiment[$key])) {
            throw "The frame experiment declaration is missing '$key'."
        }
    }
    $baseline = $Manifest.roles.baseline
    $candidate = $Manifest.roles.candidate
    $role = $Manifest.roles[$Slot.role]
    return [ordered]@{
        Variant = 'release-like'
        CompilationMode = 'speed-profile'
        DeviceSerial = [string]$Manifest.device.serial
        ApkPath = [string]$role.artifacts.app_release_like.path
        SourceCommit = [string]$role.build_commit
        ExperimentDirectory = [string]$experiment.directory
        ExperimentId = [string]$Manifest.experiment_id
        BaselineSourceCommit = [string]$baseline.build_commit
        CandidateSourceCommit = [string]$candidate.build_commit
        BaselineProductionCommit = [string]$baseline.production_commit
        CandidateProductionCommit = [string]$candidate.production_commit
        BaselineProductionTreeSha256 = [string]$baseline.production_tree_sha256
        CandidateProductionTreeSha256 = [string]$candidate.production_tree_sha256
        BaselineCanvas16SurfaceBounds = [string]$Manifest.device.baseline_canvas16_bounds
        BaselineCanvas256SurfaceBounds = [string]$Manifest.device.baseline_canvas256_bounds
        CandidateCanvas16SurfaceBounds = [string]$Manifest.device.candidate_canvas16_bounds
        CandidateCanvas256SurfaceBounds = [string]$Manifest.device.candidate_canvas256_bounds
        BaselineApkSha256 = [string]$baseline.artifacts.app_release_like.sha256
        CandidateApkSha256 = [string]$candidate.artifacts.app_release_like.sha256
        BaselineProfileGenerationSourceCommit = [string]$baseline.profile.generation_commit
        CandidateProfileGenerationSourceCommit = [string]$candidate.profile.generation_commit
        BaselineProfileGenerationAppApkSha256 = [string]$baseline.profile.generation_app_sha256
        CandidateProfileGenerationAppApkSha256 = [string]$candidate.profile.generation_app_sha256
        BaselineProfileGenerationTestApkSha256 = [string]$baseline.profile.generation_test_sha256
        CandidateProfileGenerationTestApkSha256 = [string]$candidate.profile.generation_test_sha256
        BaselineProfileAcceptanceManifestPath = [string]$baseline.profile.acceptance.path
        CandidateProfileAcceptanceManifestPath = [string]$candidate.profile.acceptance.path
        BaselineProfileAcceptanceManifestSha256 = [string]$baseline.profile.acceptance.sha256
        CandidateProfileAcceptanceManifestSha256 = [string]$candidate.profile.acceptance.sha256
        BaselineProfilePairManifestSha256 = [string]$baseline.profile.pair.sha256
        CandidateProfilePairManifestSha256 = [string]$candidate.profile.pair.sha256
        BaselineCanonicalProfileSha256 = [string]$baseline.profile.canonical.sha256
        CandidateCanonicalProfileSha256 = [string]$candidate.profile.canonical.sha256
        BaselinePackagedProfSha256 = [string]$baseline.profile.packaged_prof_sha256
        CandidatePackagedProfSha256 = [string]$candidate.profile.packaged_prof_sha256
        BaselinePackagedProfmSha256 = [string]$baseline.profile.packaged_profm_sha256
        CandidatePackagedProfmSha256 = [string]$candidate.profile.packaged_profm_sha256
        CandidateHypothesis = [string]$experiment.hypothesis
        ExpectedAffectedCost = [string]$experiment.expected_affected_cost
        CorrectnessRisk = [string]$experiment.correctness_risk
        StopConditions = [string]$experiment.stop_conditions
        RunKind = [string]$Slot.runner
        CandidateRole = [string]$Slot.role
        ComparisonSequenceIndex = [int]$Slot.run
        Attempt = 1
        SampleCount = [int]$Slot.samples
    }
}

function Get-P4DeviceLanePlan {
    <#
        Pure, device-free description of exactly what a device lane will do. The collector executes this
        plan; the no-device validator compares it against fixed expected values.
    #>
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Manifest,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Slot
    )
    $role = [string]$Slot.role
    $packages = Get-P4LanePackages -Manifest $Manifest -Role $role
    $buildCommit = [string]$Manifest.roles[$role].build_commit
    if ($buildCommit -cnotmatch '^[0-9a-f]{40}$') { throw "Manifest role $role has no measurement build commit." }
    $plan = [ordered]@{
        lane = [string]$Slot.lane
        slot_id = [string]$Slot.id
        role = $role
        runner = [string]$Slot.runner
        run = [int]$Slot.run
        timeout_seconds = [int]$Slot.timeout_seconds
        packages = $packages
        quiescence_packages = @($packages.application, $packages.application_test, $packages.publication_test)
        install_kinds = @()
        dexopt_packages = @()
        test_package = ''
        class = ''
        instrumentation_arguments = [ordered]@{}
        adb_arguments = @()
        private_files = @()
        # Every lane runs exactly one @Test method of one class; the OK summary must confirm it.
        expected_test_count = 1
        inner_timeout_seconds = [int]$Slot.timeout_seconds - $script:P4InnerTimeoutReserveSeconds
        frame_parameters = $null
    }
    switch ($Slot.lane) {
        'command' {
            $plan.install_kinds = @('app_debug', 'test_debug')
            $plan.dexopt_packages = @($packages.application)
            $plan.test_package = $packages.application_test
            $plan.class = $script:P4CommandClass
            $plan.instrumentation_arguments = [ordered]@{
                'nene.p4.commandRole' = "p4-indexed-command-$role-v1"
                'nene.p4.commandRunIndex' = '1'
                'nene.p4.measurementBuildCommit' = $buildCommit
                'nene.p2.physicalProfileId' = $script:P4PhysicalProfileId
                'nene.p2.warmupIterations' = [string]$script:P4CommandWarmups
                'nene.p2.sampleCount' = [string]$script:P4CommandSamples
            }
            $plan.private_files = @(
                [ordered]@{
                    package = $packages.application
                    relative_path = "files/p4-measurements/p4-indexed-command-$role-run-01.csv"
                    destination_name = "p4-indexed-command-$role-run-01.csv"
                }
            )
        }
        'memory' {
            $familyKey = "$role-$($Slot.runner)"
            if (-not $script:P4MemoryFamilyBySlot.ContainsKey($familyKey)) { throw "Unknown memory family slot '$familyKey'." }
            $family = $script:P4MemoryFamilyBySlot[$familyKey]
            $plan.install_kinds = @('app_debug', 'test_debug')
            $plan.dexopt_packages = @($packages.application)
            $plan.test_package = $packages.application_test
            $plan.class = if ($family -ceq 'candidate-legacy-import') { $script:P4LegacyMemoryClass } else { $script:P4HistoryMemoryClass }
            $plan.instrumentation_arguments = [ordered]@{
                'nene.p4.memoryFamily' = $family
                'nene.p4.memoryRunIndex' = [string][int]$Slot.run
                'nene.p4.measurementBuildCommit' = $buildCommit
                'nene.p2.physicalProfileId' = $script:P4PhysicalProfileId
            }
            $plan.private_files = @()
        }
        'publication' {
            $installs = [Collections.Generic.List[string]]::new()
            $installs.Add('publication_test')
            if ($packages.publication_target -cne $packages.publication_test) {
                # A non self-instrumenting publication APK would need its target application present too.
                $installs.Insert(0, 'app_debug')
            }
            $plan.install_kinds = $installs.ToArray()
            $plan.dexopt_packages = @($packages.publication_test)
            $plan.test_package = $packages.publication_test
            $plan.class = $script:P4PublicationClass
            $plan.instrumentation_arguments = [ordered]@{
                'nene.p4.publicationEvidence' = 'collect'
                'nene.p4.publicationEvidenceRole' = $role
            }
            $plan.private_files = @(
                [ordered]@{
                    package = $packages.publication_test
                    relative_path = "files/p4-indexed-publication-device-$role-v1.csv"
                    destination_name = "p4-indexed-publication-device-$role-v1.csv"
                },
                [ordered]@{
                    package = $packages.publication_test
                    relative_path = "files/p4-indexed-publication-device-$role-v1.status"
                    destination_name = "p4-indexed-publication-device-$role-v1.status"
                }
            )
        }
        'frame' {
            $plan.install_kinds = @()
            $plan.dexopt_packages = @()
            $plan.frame_parameters = Get-P4FrameCollectorParameters -Manifest $Manifest -Slot $Slot
            $plan.frame_slot_directory =
                Join-Path ([string]$Manifest.frame_experiment.directory) (
                    'slot-{0:D2}-{1}-{2}-attempt-1' -f [int]$Slot.run, [string]$Slot.runner, $role
                )
            return $plan
        }
        default { throw "Lane '$($Slot.lane)' is not a device lane." }
    }
    $plan.adb_arguments = @(
        Get-P4InstrumentationArguments -TestPackage $plan.test_package -Class $plan.class `
            -Arguments $plan.instrumentation_arguments
    )
    return $plan
}

function Invoke-P4BoundedAdb {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Context,
        [Parameter(Mandatory = $true)][string]$LogName,
        [Parameter(Mandatory = $true)][string[]]$AdbArguments,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )
    return Invoke-BoundedNativeCommand -RepositoryRoot $Context.repository_root `
        -LogPath (Join-Path $Context.output_directory $LogName) `
        -ExecutablePath $Context.adb_path `
        -NativeArguments (@('-s', $Context.serial) + $AdbArguments) `
        -TimeoutSeconds $TimeoutSeconds
}

function Invoke-P4RawAdbCapture {
    <#
        Binary-safe private-file capture. `adb exec-out` streams raw bytes, so this cannot go through
        the text launcher of Invoke-BoundedNativeCommand. It still runs inside the same kill-on-close
        Windows Job boundary: the Job is created first and the process assigned immediately after
        Start(), and both stdout and stderr drain asynchronously so WaitForExit stays the only bound.
        adb is a thin client over the already-running adb server, so the short window between Start()
        and assignment cannot leave an unbounded descendant behind.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [Parameter(Mandatory = $true)][string[]]$AdbArguments,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = [IO.Path]::GetFullPath($AdbPath)
    foreach ($argument in $AdbArguments) { $startInfo.ArgumentList.Add($argument) }
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $buffer = [IO.MemoryStream]::new()
    $job = [IntPtr]::Zero
    $timeoutMilliseconds = $TimeoutSeconds * 1000
    try {
        $job = New-InvocationJob
        if (-not $process.Start()) { throw 'The private-file capture did not start adb.' }
        if (-not [NenePixelBaselineProfile.JobNativeMethods]::AssignProcessToJobObject($job, $process.Handle)) {
            throw 'AssignProcessToJobObject failed with Win32 error ' +
                "$([Runtime.InteropServices.Marshal]::GetLastWin32Error())."
        }
        $copy = $process.StandardOutput.BaseStream.CopyToAsync($buffer)
        $standardErrorTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($timeoutMilliseconds)) {
            try { $process.Kill($true) } catch { }
            $terminated = [NenePixelBaselineProfile.JobNativeMethods]::TerminateJobObject($job, 124)
            $quiescent = $false
            if ($terminated) {
                $quiescent = Wait-InvocationJobEmpty -Job $job -DeadlineUtc ([datetime]::UtcNow.AddSeconds(10))
            }
            throw "The private-file capture exceeded its $TimeoutSeconds second bound " +
                "(job terminated: $terminated, quiescent: $quiescent)."
        }
        if (-not $copy.Wait($timeoutMilliseconds) -or -not $standardErrorTask.Wait($timeoutMilliseconds)) {
            throw 'The private-file capture did not drain within its bound.'
        }
        if ($process.ExitCode -ne 0) {
            throw "The private-file capture failed ($($process.ExitCode)): $($standardErrorTask.Result)"
        }
        $bytes = $buffer.ToArray()
        if ($bytes.Length -le 0) { throw 'The private-file capture returned no bytes.' }
        return $bytes
    }
    finally {
        $buffer.Dispose()
        $process.Dispose()
        if ($job -ne [IntPtr]::Zero) {
            # Kill-on-close: releasing the last handle terminates anything still assigned to the Job.
            [NenePixelBaselineProfile.JobNativeMethods]::CloseHandle($job) | Out-Null
        }
    }
}

function Assert-P4RemotePackagesStopped {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Context,
        [Parameter(Mandatory = $true)][string[]]$Packages,
        [Parameter(Mandatory = $true)][string]$Stage
    )
    foreach ($package in $Packages) {
        $result = Invoke-P4BoundedAdb -Context $Context -LogName "pidof-$Stage-$package.log" `
            -AdbArguments @('shell', 'pidof', $package) -TimeoutSeconds $script:P4ProbeTimeoutSeconds
        # Absence is exactly `pidof` exit 1 with no output. Any other exit code is undetermined, and an
        # undetermined device state may never admit a measurement.
        $output = @($result.OutputLines | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
        if ($result.ExitCode -ne 1 -or $output.Count -ne 0) {
            throw "Package $package is running or its process state is undetermined at stage $Stage " +
                "(exit $($result.ExitCode), $($output.Count) line(s))."
        }
    }
}

function Assert-P4InstalledApk {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Context,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Manifest,
        [Parameter(Mandatory = $true)][string]$Role,
        [Parameter(Mandatory = $true)][string]$Kind
    )
    $artifact = $Manifest.roles[$Role].artifacts[$Kind]
    $package = if ($Kind -in @('test_debug', 'publication_test')) {
        [string]$artifact.test_package
    } else {
        [string]$artifact.target_package
    }
    $expected = [string]$artifact.sha256
    if ($expected -cnotmatch '^[0-9a-f]{64}$') { throw "Artifact $Role.$Kind has no fixed SHA-256." }
    $observed = Get-P4InstalledApkSha256 -Context $Context -Package $package -Stage "$Kind-before"
    $installed = $false
    if ($observed -cne $expected) {
        $install = Invoke-P4BoundedAdb -Context $Context -LogName "install-$Kind.log" `
            -AdbArguments @('install', '-r', '-t', [string]$artifact.path) `
            -TimeoutSeconds $script:P4InstallTimeoutSeconds
        if ($install.ExitCode -ne 0) { throw "Installing $Role.$Kind failed with exit $($install.ExitCode)." }
        $installed = $true
        $observed = Get-P4InstalledApkSha256 -Context $Context -Package $package -Stage "$Kind-after"
        if ($observed -cne $expected) { throw "The installed $Role.$Kind base APK does not match its fixed identity." }
    }
    $record = [ordered]@{
        schema = 'nene-pixel-p4-device-install-v1'
        kind = $Kind
        role = $Role
        package = $package
        expected_sha256 = $expected
        observed_sha256 = $observed
        installed = $installed
        recorded_utc = [datetime]::UtcNow.ToString('o')
    }
    Write-NewInvocationFile (Join-Path $Context.output_directory "install-$Kind.json") ($record | ConvertTo-Json -Depth 5)
    return $record
}

function Get-P4InstalledApkSha256 {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Context,
        [Parameter(Mandatory = $true)][string]$Package,
        [Parameter(Mandatory = $true)][string]$Stage
    )
    $paths = Invoke-P4BoundedAdb -Context $Context -LogName "pm-path-$Stage.log" `
        -AdbArguments @('shell', 'pm', 'path', $Package) -TimeoutSeconds $script:P4ProbeTimeoutSeconds
    $lines = @($paths.OutputLines | ForEach-Object { $_.Trim() } | Where-Object { $_ -cmatch '^package:/' })
    if ($paths.ExitCode -ne 0 -or $lines.Count -eq 0) { return 'not-installed' }
    $base = @($lines | ForEach-Object { $_.Substring('package:'.Length) } | Where-Object { $_.EndsWith('/base.apk') })
    if ($base.Count -ne 1) { throw "Package $Package does not expose exactly one base APK." }
    $sum = Invoke-P4BoundedAdb -Context $Context -LogName "sha256-$Stage.log" `
        -AdbArguments @('shell', 'sha256sum', $base[0]) -TimeoutSeconds $script:P4ProbeTimeoutSeconds
    if ($sum.ExitCode -ne 0) { throw "Unable to hash the installed base APK for $Package." }
    $digest = @($sum.OutputLines | ForEach-Object {
            $match = [regex]::Match($_, '^([0-9a-f]{64})\s')
            if ($match.Success) { $match.Groups[1].Value }
        })
    if ($digest.Count -ne 1) { throw "The installed base APK for $Package did not report exactly one digest." }
    return $digest[0]
}

function Set-P4Dexopt {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Context,
        [Parameter(Mandatory = $true)][string]$Package,
        [Parameter(Mandatory = $true)][ValidateSet('verify')][string]$Mode
    )
    if (-not (Get-Command -Name 'Get-P4PackageDexoptStatus' -CommandType Function -ErrorAction SilentlyContinue)) {
        throw 'The shared device-state contract function Get-P4PackageDexoptStatus is unavailable.'
    }
    $compile = Invoke-P4BoundedAdb -Context $Context -LogName "dexopt-$Package.log" `
        -AdbArguments @('shell', 'cmd', 'package', 'compile', '-m', $Mode, '-f', $Package) `
        -TimeoutSeconds $script:P4DexoptTimeoutSeconds
    if ($compile.ExitCode -ne 0 -or (@($compile.OutputLines) -join "`n") -notmatch 'Success') {
        throw "Requesting $Mode compilation for $Package did not report success."
    }
    # S1 contract (owner B): -Stage is a short lowercase token and -RepositoryRoot bounds the probe.
    $observed = Get-P4PackageDexoptStatus -AdbPath $Context.adb_path -Serial $Context.serial `
        -Directory $Context.output_directory -Package $Package -Stage 'dexopt-verify' `
        -RepositoryRoot $Context.repository_root
    if ([string]$observed -cne $Mode) {
        throw "Package $Package reports dexopt '$observed' instead of the requested '$Mode'."
    }
    $record = [ordered]@{
        schema = 'nene-pixel-p4-device-dexopt-v1'
        package = $Package
        requested = $Mode
        observed = [string]$observed
        recorded_utc = [datetime]::UtcNow.ToString('o')
    }
    Write-NewInvocationFile (Join-Path $Context.output_directory "dexopt-$Package.json") ($record | ConvertTo-Json -Depth 5)
    return $record
}

function Assert-P4PrivateFileAbsent {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Context,
        [Parameter(Mandatory = $true)][string]$Package,
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][string]$Stage
    )
    $result = Invoke-P4BoundedAdb -Context $Context -LogName "private-ls-$Stage.log" `
        -AdbArguments @('shell', 'run-as', $Package, 'ls', $RelativePath) `
        -TimeoutSeconds $script:P4ProbeTimeoutSeconds
    $text = (@($result.OutputLines) -join "`n")
    if ($result.ExitCode -ne 0 -and $text -match 'No such file or directory') { return }
    if ($result.ExitCode -eq 0) {
        throw "A prior attempt already produced $Package/$RelativePath on the device; it is never deleted here."
    }
    throw "Unable to determine whether $Package/$RelativePath exists (exit $($result.ExitCode)): $text"
}

function Copy-P4PrivateFile {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Context,
        [Parameter(Mandatory = $true)][string]$Package,
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][string]$Destination
    )
    if (Test-Path -LiteralPath $Destination) { throw "Private-file destination already exists: $Destination" }
    $bytes = Invoke-P4RawAdbCapture -AdbPath $Context.adb_path `
        -AdbArguments @('-s', $Context.serial, 'exec-out', 'run-as', $Package, 'cat', $RelativePath) `
        -TimeoutSeconds $script:P4PrivateFileTimeoutSeconds
    $stream = [IO.File]::Open($Destination, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    return [ordered]@{ package = $Package; relative_path = $RelativePath; byte_count = $bytes.Length }
}

function Invoke-P4Instrumentation {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Context,
        [Parameter(Mandatory = $true)][string[]]$AdbArguments,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [ValidateRange(1, 64)][int]$ExpectedTestCount = 1
    )
    $result = Invoke-P4BoundedAdb -Context $Context -LogName 'instrumentation.log' `
        -AdbArguments $AdbArguments -TimeoutSeconds $TimeoutSeconds
    $text = (@($result.OutputLines) -join "`n")
    if ($result.ExitCode -ne 0) { throw "Instrumentation failed with exit $($result.ExitCode)." }
    # -1 is a JUnit failure and -2 an unrecoverable error; both invalidate the slot.
    if ($text -match '(?m)^INSTRUMENTATION_STATUS_CODE:\s*-(1|2)\s*$') {
        throw 'Instrumentation reported a failed or errored test.'
    }
    if ($text -match 'FAILURES!!!') { throw 'Instrumentation reported test failures.' }
    if ($text -match 'Process crashed') { throw 'Instrumentation reported a crashed process.' }
    if ($text -notmatch '(?m)^INSTRUMENTATION_CODE:\s*-1\s*$') {
        throw 'Instrumentation did not report a successful run code.'
    }
    # Positive confirmation: absence of failure markers is not evidence that the test actually ran.
    $summaries = [regex]::Matches($text, '(?m)^OK \((\d+) tests?\)\s*$')
    if ($summaries.Count -ne 1) { throw 'Instrumentation did not report exactly one OK summary.' }
    $observedTests = [int]$summaries[0].Groups[1].Value
    if ($observedTests -lt 1 -or $observedTests -ne $ExpectedTestCount) {
        throw "Instrumentation reported $observedTests tests instead of the expected $ExpectedTestCount."
    }
    return $result
}

function Invoke-P4InstrumentationLane {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Context,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Manifest,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Plan
    )
    Assert-P4RemotePackagesStopped -Context $Context -Packages $Plan.quiescence_packages -Stage 'lane-start'
    foreach ($kind in $Plan.install_kinds) {
        Assert-P4InstalledApk -Context $Context -Manifest $Manifest -Role $Plan.role -Kind $kind | Out-Null
    }
    foreach ($package in $Plan.dexopt_packages) {
        Set-P4Dexopt -Context $Context -Package $package -Mode 'verify' | Out-Null
    }
    $index = 0
    foreach ($file in $Plan.private_files) {
        $index += 1
        Assert-P4PrivateFileAbsent -Context $Context -Package $file.package `
            -RelativePath $file.relative_path -Stage "$($Plan.lane)-$index"
    }
    # The inner bound must expire before the outer slot bound so that private-file retrieval and the
    # wrapper's cleanup still fit inside the slot deadline.
    $innerTimeout = [int]$Plan.timeout_seconds - $script:P4InnerTimeoutReserveSeconds
    if ($innerTimeout -lt 1) { throw 'The slot timeout leaves no room for a bounded instrumentation run.' }
    Invoke-P4Instrumentation -Context $Context -AdbArguments $Plan.adb_arguments `
        -TimeoutSeconds $innerTimeout -ExpectedTestCount ([int]$Plan.expected_test_count) | Out-Null
    $copied = [Collections.Generic.List[object]]::new()
    foreach ($file in $Plan.private_files) {
        $copied.Add(
            (Copy-P4PrivateFile -Context $Context -Package $file.package -RelativePath $file.relative_path `
                -Destination (Join-Path $Context.output_directory $file.destination_name))
        )
    }
    return $copied.ToArray()
}

function New-P4FrameSlotRecord {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Context,
        [Parameter(Mandatory = $true)][string]$FrameSlotDirectory
    )
    $root = [IO.Path]::GetFullPath($FrameSlotDirectory)
    if (-not (Test-Path -LiteralPath $root -PathType Container)) {
        throw "The frame collector did not produce its slot directory: $root"
    }
    $files = [Collections.Generic.List[object]]::new()
    foreach ($item in @(Get-ChildItem -LiteralPath $root -Recurse -File -Force | Sort-Object -Property FullName)) {
        $full = [IO.Path]::GetFullPath($item.FullName)
        $relative = $full.Substring($root.Length).TrimStart([char]'\', [char]'/').Replace('\', '/')
        $files.Add([ordered]@{ relative_path = $relative; byte_count = $item.Length; sha256 = (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash.ToLowerInvariant() })
    }
    if ($files.Count -eq 0) { throw 'The frame slot directory retained no evidence.' }
    $record = [ordered]@{
        schema = 'nene-pixel-p4-frame-slot-v1'
        slot_directory = $root
        files = $files.ToArray()
        recorded_utc = [datetime]::UtcNow.ToString('o')
    }
    Write-NewInvocationFile (Join-Path $Context.output_directory 'frame-slot.json') ($record | ConvertTo-Json -Depth 6)
    return $record
}

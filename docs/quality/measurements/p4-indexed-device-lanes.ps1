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
# Protocol v4 bounds ONE instrumentation invocation at 300 s (protocol:174-175, 251-252) and gives the
# install/compile calls their own 120 s and the ordinary native calls 30 s. The collector therefore has
# to outlive the instrumentation it hosts: the inner bound is exactly `timeout_seconds` and the outer
# (wrapper Job) bound is DERIVED from the plan by Get-P4CollectorBudget. Using `timeout_seconds` for
# both would let the outer kill precede the inner bound and turn a protocol-legal run into INVALID.
# The reserve covers pwsh start-up, dot-sourcing, manifest parsing and the record writes between calls.
$script:P4CollectorReserveSeconds = 60
$script:P4CollectorBudgetSchema = 'nene-pixel-p4-collector-budget-v1'
# Must equal the ValidateRange ceiling of Invoke-BoundedNativeCommand -TimeoutSeconds in
# docs/quality/bounded-native-command.ps1:172 ([ValidateRange(1, 1800)]). A derived bound above it would
# otherwise only be caught by parameter binding at invoke-p4-indexed-slot.ps1's collector call - after
# the slot is reserved - and burn the slot's single attempt on a harness arithmetic mistake.
$script:P4BoundedNativeCapSeconds = 1800
# The recovery record the v1 baseline cannot read once the v2 candidate has written it. Only these
# exact live names are ever moved; every other no_backup entry (issue-89-*, issue-102-*, issue-106-*,
# and hide's guarded user recovery) is untouchable, and nothing is ever deleted.
$script:P4RecoveryPrivateDirectory = 'no_backup'
$script:P4RecoveryLiveNames = @(
    'nene-pixel-recovery-v1',
    'nene-pixel-recovery-v1.new',
    'nene-pixel-recovery-v1.bak'
)
$script:P4RecoveryQuarantineRoot = 'no_backup/p4-quarantine'
$script:P4RecoveryQuarantineSchema = 'nene-pixel-p4-recovery-quarantine-v1'
$script:P4PrivateFileQuarantineSchema = 'nene-pixel-p4-private-file-quarantine-v1'
$script:P4PrivateFileQuarantineRecordName = 'private-file-quarantine.json'

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

function Get-P4RecoveryQuarantinePlan {
    <#
        Pure description of the frame lane's recovery quarantine. The candidate (v2 writer) leaves a
        record the baseline build (2dd4e01, v1 only) cannot read, which makes the baseline editor show
        "recovery data unavailable" and disable New/Open - so the later baseline frame slots can never
        reach editor_create_document_title. Moving (never deleting) the live record aside restores the
        first-run state each frame slot requires.
    #>
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Manifest,
        [Parameter(Mandatory = $true)][ValidateSet('baseline', 'candidate')][string]$Role,
        [Parameter(Mandatory = $true)][string]$SlotId
    )
    $experimentId = [string]$Manifest.experiment_id
    if ($experimentId -cnotmatch '^[a-z0-9][a-z0-9-]{2,63}$') { throw 'Invalid experiment identity for quarantine.' }
    if ($SlotId -cnotmatch '^[a-z0-9][a-z0-9-]{2,63}$') { throw "Invalid slot identity for quarantine: $SlotId" }
    $packages = Get-P4LanePackages -Manifest $Manifest -Role $Role
    return [ordered]@{
        package = $packages.application
        install_kind = 'app_debug'
        source_directory = $script:P4RecoveryPrivateDirectory
        live_names = @($script:P4RecoveryLiveNames)
        quarantine_path = "$script:P4RecoveryQuarantineRoot/$experimentId/$SlotId"
    }
}

function Assert-P4PrivateRelativePath {
    param([Parameter(Mandatory = $true)][string]$Value, [Parameter(Mandatory = $true)][string]$Name)
    if ($Value -cnotmatch '^(?:[A-Za-z0-9._-]+/)*[A-Za-z0-9._-]+$') {
        throw "Invalid private-file path for $Name : $Value"
    }
    foreach ($segment in $Value.Split('/')) {
        if ($segment -ceq '.' -or $segment -ceq '..') { throw "Relative private-file path for $Name : $Value" }
    }
    return $Value
}

function Get-P4PrivateFileQuarantinePlan {
    <#
        Pure description of the instrumentation lanes' post-run private-file quarantine.

        The protocol's fail-if-present output reservation (Assert-P4PrivateFileAbsent, createNewFile in
        the producers) is what keeps a slot from silently reusing an older device output - but it only
        works while the device side is clean. A slot that failed or timed out after the test had already
        written its CSV would leave that file behind and refuse EVERY later slot, including the bounded
        recovery. So the wrapper's cleanup moves whatever the plan reserved into the app's own
        `no_backup/p4-quarantine/<experiment_id>/<slot_id>/`, on success and on failure alike.

        The rules are the frame lane's recovery quarantine rules (Invoke-P4RecoveryQuarantine): move,
        never delete; never reuse an existing quarantine directory; only the exact plan-listed names;
        the same bounded adb path (Invoke-P4BoundedAdb / Get-P4PrivateDirectoryListing).
    #>
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Manifest,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Plan
    )
    $experimentId = [string]$Manifest.experiment_id
    if ($experimentId -cnotmatch '^[a-z0-9][a-z0-9-]{2,63}$') { throw 'Invalid experiment identity for quarantine.' }
    $slotId = [string]$Plan.slot_id
    if ($slotId -cnotmatch '^[a-z0-9][a-z0-9-]{2,63}$') { throw "Invalid slot identity for quarantine: $slotId" }
    $quarantine = "$script:P4RecoveryQuarantineRoot/$experimentId/$slotId"
    $entries = [Collections.Generic.List[object]]::new()
    $directories = [Collections.Generic.List[object]]::new()
    $packages = [Collections.Generic.List[string]]::new()
    $seenDirectory = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($file in @($Plan.private_files)) {
        $package = Assert-P4PackageName ([string]$file.package) "$slotId private file package"
        $relative = Assert-P4PrivateRelativePath ([string]$file.relative_path) "$slotId private file"
        $separator = $relative.LastIndexOf('/')
        if ($separator -lt 1) { throw "A plan-listed private file has no owning directory: $relative" }
        $directory = $relative.Substring(0, $separator)
        $name = $relative.Substring($separator + 1)
        if (-not $packages.Contains($package)) { $packages.Add($package) }
        if ($seenDirectory.Add("$package/$directory")) {
            $directories.Add([ordered]@{ package = $package; path = $directory })
        }
        $entries.Add([ordered]@{
            package = $package
            relative_path = $relative
            source_directory = $directory
            name = $name
            quarantine_relative_path = "$quarantine/$name"
        })
    }
    # Two lanes may not collide inside one quarantine directory, and one lane may not reserve the same
    # leaf name twice: the move would overwrite, which this quarantine never does.
    $targets = @($entries | ForEach-Object { "$($_.package)/$($_.quarantine_relative_path)" })
    if (@($targets | Select-Object -Unique).Count -ne $targets.Count) {
        throw "Lane $slotId reserves the same quarantine target twice."
    }
    return [ordered]@{
        schema = $script:P4PrivateFileQuarantineSchema
        experiment_id = $experimentId
        slot_id = $slotId
        lane = [string]$Plan.lane
        role = [string]$Plan.role
        quarantine_path = $quarantine
        packages = $packages.ToArray()
        source_directories = $directories.ToArray()
        entries = $entries.ToArray()
    }
}

function Get-P4PrivateFileSha256 {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Context,
        [Parameter(Mandatory = $true)][string]$Package,
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][string]$LogName
    )
    $result = Invoke-P4BoundedAdb -Context $Context -LogName $LogName `
        -AdbArguments @('exec-out', 'run-as', $Package, 'sha256sum', $RelativePath) `
        -TimeoutSeconds $script:P4ProbeTimeoutSeconds
    $digests = @(@($result.OutputLines) | ForEach-Object {
            $match = [regex]::Match($_, '^([0-9a-f]{64})\s')
            if ($match.Success) { $match.Groups[1].Value }
        })
    if ($digests.Count -ne 1) {
        throw "Unable to hash $Package/$RelativePath before quarantine (exit $($result.ExitCode))."
    }
    return $digests[0]
}

function Invoke-P4PrivateFileQuarantine {
    <#
        Cleanup-phase quarantine for the command/memory/publication lanes. Runs after the remote
        packages are stopped and BEFORE the capture seal, so `private-file-quarantine.json` and the adb
        logs it produces are sealed capture, not post-seal wrapper output. Nothing is ever deleted and an
        existing quarantine directory is a refusal, never a reuse.
    #>
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Context,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Plan
    )
    $recordPath = Join-Path $Context.output_directory $script:P4PrivateFileQuarantineRecordName
    if (Test-Path -LiteralPath $recordPath) { throw "Private-file quarantine record already exists: $recordPath" }
    $quarantine = [string]$Plan.quarantine_path
    $directories = [Collections.Generic.List[object]]::new()
    $files = [Collections.Generic.List[object]]::new()
    $listings = @{}
    # A quarantine that throws halfway is exactly the state an operator has to reason about, so the
    # record is written either way: `completed` says whether the move finished, `stage` says how far it
    # got, and `error` carries the refusal. Written in `finally`, never twice (CreateNew semantics).
    $stage = 'start'
    $completed = $false
    $failure = ''
    try {
    $index = 0
    foreach ($directory in @($Plan.source_directories)) {
        $stage = 'listing-before'
        $index += 1
        $key = "$($directory.package)/$($directory.path)"
        $detail = Invoke-P4BoundedAdb -Context $Context -LogName "private-quarantine-before-la-$index.log" `
            -AdbArguments @('exec-out', 'run-as', [string]$directory.package, 'ls', '-la', [string]$directory.path) `
            -TimeoutSeconds $script:P4ProbeTimeoutSeconds
        $listing = Get-P4PrivateDirectoryListing -Context $Context -Package ([string]$directory.package) `
            -RelativePath ([string]$directory.path) -LogName "private-quarantine-before-ls-$index.log"
        $listings[$key] = $listing
        $directories.Add([ordered]@{
            package = [string]$directory.package
            path = [string]$directory.path
            present = ($null -ne $listing)
            before_entries = @(if ($null -eq $listing) { @() } else { $listing })
            before_listing = @($detail.OutputLines)
            before_listing_sha256 = Get-P4TextSha256 -Lines @($detail.OutputLines)
            after_entries = @()
            after_listing = @()
            after_listing_sha256 = ''
        })
    }
    $present = @(@($Plan.entries) | Where-Object {
            $listing = $listings["$($_.package)/$($_.source_directory)"]
            ($null -ne $listing) -and ([string]$_.name -cin $listing)
        })
    $stage = 'hashing'
    $hashIndex = 0
    foreach ($entry in $present) {
        $hashIndex += 1
        $files.Add([ordered]@{
            package = [string]$entry.package
            name = [string]$entry.name
            relative_path = [string]$entry.relative_path
            sha256 = Get-P4PrivateFileSha256 -Context $Context -Package ([string]$entry.package) `
                -RelativePath ([string]$entry.relative_path) -LogName "private-quarantine-sha256-$hashIndex.log"
            quarantine_relative_path = [string]$entry.quarantine_relative_path
        })
    }
    $stage = 'moving'
    $prepared = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $moveIndex = 0
    foreach ($entry in $present) {
        $package = [string]$entry.package
        if ($prepared.Add($package)) {
            # Presence, not emptiness: an earlier attempt that created the directory and then failed
            # before moving anything must still refuse this one.
            if (Test-P4PrivateEntryPresent -Context $Context -Package $package `
                    -RelativePath $quarantine -LogName "private-quarantine-target-ls-$($prepared.Count).log") {
                throw "A prior attempt already quarantined into $package/$quarantine; it is never reused or deleted."
            }
            $mkdir = Invoke-P4BoundedAdb -Context $Context -LogName "private-quarantine-mkdir-$($prepared.Count).log" `
                -AdbArguments @('shell', 'run-as', $package, 'mkdir', '-p', $quarantine) `
                -TimeoutSeconds $script:P4ProbeTimeoutSeconds
            if ($mkdir.ExitCode -ne 0) { throw "Unable to create $package/$quarantine (exit $($mkdir.ExitCode))." }
        }
        $moveIndex += 1
        $move = Invoke-P4BoundedAdb -Context $Context -LogName "private-quarantine-mv-$moveIndex.log" `
            -AdbArguments @('shell', 'run-as', $package, 'mv',
                [string]$entry.relative_path, [string]$entry.quarantine_relative_path) `
            -TimeoutSeconds $script:P4ProbeTimeoutSeconds
        if ($move.ExitCode -ne 0) {
            throw "Unable to quarantine $package/$($entry.relative_path) (exit $($move.ExitCode))."
        }
    }
    $stage = 'listing-after'
    $index = 0
    foreach ($directory in $directories) {
        $index += 1
        $detail = Invoke-P4BoundedAdb -Context $Context -LogName "private-quarantine-after-la-$index.log" `
            -AdbArguments @('exec-out', 'run-as', [string]$directory.package, 'ls', '-la', [string]$directory.path) `
            -TimeoutSeconds $script:P4ProbeTimeoutSeconds
        $listing = Get-P4PrivateDirectoryListing -Context $Context -Package ([string]$directory.package) `
            -RelativePath ([string]$directory.path) -LogName "private-quarantine-after-ls-$index.log"
        $directory.after_entries = @(if ($null -eq $listing) { @() } else { $listing })
        $directory.after_listing = @($detail.OutputLines)
        $directory.after_listing_sha256 = Get-P4TextSha256 -Lines @($detail.OutputLines)
    }
    $stage = 'verifying'
    foreach ($entry in @($Plan.entries)) {
        $after = @($directories | Where-Object {
                [string]$_.package -ceq [string]$entry.package -and [string]$_.path -ceq [string]$entry.source_directory
            })
        if ($after.Count -ne 1) { throw "The quarantine lost track of $($entry.relative_path)." }
        if ([string]$entry.name -cin @($after[0].after_entries)) {
            throw "The plan-listed private file is still present after quarantine: $($entry.relative_path)"
        }
    }
    $verifyIndex = 0
    foreach ($package in @($prepared)) {
        $verifyIndex += 1
        $quarantined = Get-P4PrivateDirectoryListing -Context $Context -Package $package `
            -RelativePath $quarantine -LogName "private-quarantine-verify-ls-$verifyIndex.log"
        $expected = @(@($files | Where-Object { [string]$_.package -ceq $package } | ForEach-Object { [string]$_.name }))
        if ($null -eq $quarantined -or @(Compare-Object $expected @($quarantined)).Count -ne 0) {
            throw "The quarantined private files for $package are not exactly what was moved aside."
        }
    }
    $stage = 'complete'
    $completed = $true
    $record = [ordered]@{
        schema = $script:P4PrivateFileQuarantineSchema
        experiment_id = [string]$Plan.experiment_id
        slot_id = [string]$Plan.slot_id
        lane = [string]$Plan.lane
        role = [string]$Plan.role
        packages = @($Plan.packages)
        quarantine_path = $quarantine
        reserved = @(@($Plan.entries) | ForEach-Object {
                [ordered]@{ package = [string]$_.package; relative_path = [string]$_.relative_path }
            })
        files = $files.ToArray()
        source_directories = @($directories)
        completed = $true
        stage = $stage
        error = ''
        deleted = $false
        recorded_utc = [datetime]::UtcNow.ToString('o')
    }
    Write-NewInvocationFile $recordPath ($record | ConvertTo-Json -Depth 8)
    return $record
    }
    catch { $failure = $_.Exception.Message; throw }
    finally {
        if (-not $completed -and -not (Test-Path -LiteralPath $recordPath)) {
            $partial = [ordered]@{
                schema = $script:P4PrivateFileQuarantineSchema
                experiment_id = [string]$Plan.experiment_id
                slot_id = [string]$Plan.slot_id
                lane = [string]$Plan.lane
                role = [string]$Plan.role
                packages = @($Plan.packages)
                quarantine_path = $quarantine
                reserved = @(@($Plan.entries) | ForEach-Object {
                        [ordered]@{ package = [string]$_.package; relative_path = [string]$_.relative_path }
                    })
                files = $files.ToArray()
                source_directories = @($directories)
                completed = $false
                stage = $stage
                error = $failure
                deleted = $false
                recorded_utc = [datetime]::UtcNow.ToString('o')
            }
            # A failure to record must not replace the refusal the caller is about to see.
            try { Write-NewInvocationFile $recordPath ($partial | ConvertTo-Json -Depth 8) } catch { }
        }
    }
}

function Assert-P4CollectorBoundWithinCap {
    <#
        The derived bound is handed straight to Invoke-BoundedNativeCommand -TimeoutSeconds, whose
        [ValidateRange(1, 1800)] would otherwise reject it only at the collector call - after the slot
        directory is reserved and the slot's single attempt is spent. Refusing here keeps an arithmetic
        mistake in the budget a pre-reservation refusal instead of an INVALID slot.
    #>
    param([Parameter(Mandatory = $true)][System.Collections.IDictionary]$Budget)
    $bound = [int]$Budget.collector_timeout_seconds
    if ($bound -lt 1 -or $bound -gt $script:P4BoundedNativeCapSeconds) {
        throw "The collector bound for lane '$([string]$Budget.lane)' is $bound s, outside the bounded " +
            "native range of 1..$($script:P4BoundedNativeCapSeconds) s."
    }
}

function Get-P4CollectorBudget {
    <#
        Pure derivation of the outer (wrapper Job) collector bound from a lane plan.

        Protocol v4 bounds one instrumentation invocation at `timeout_seconds` (300 s) and bounds the
        install/compile calls at 120 s and every ordinary native call at 30 s SEPARATELY. Applying
        `timeout_seconds` to the whole collector - as the wrapper used to - makes the outer kill able to
        precede the inner bound, so a protocol-legal instrumentation run would be recorded as INVALID.

        The derived bound is the exact worst case of the collector's own bounded calls plus a reserve:

          instrumentation : the inner bound, `timeout_seconds`
          install         : P4InstallTimeoutSeconds per install kind
          dexopt          : P4DexoptTimeoutSeconds per dexopt package
          probe           : P4ProbeTimeoutSeconds per bounded 30 s call -
                            one `pidof` per quiescence package,
                            four per install kind (`pm path` + `sha256sum`, before and after install),
                            one `dumpsys package` per dexopt package (Get-P4PackageDexoptStatus), and
                            one `run-as ls` per private file (Assert-P4PrivateFileAbsent)
          private capture : P4PrivateFileTimeoutSeconds per private file (Copy-P4PrivateFile)
          reserve         : P4CollectorReserveSeconds for pwsh start-up and the record writes

        The frame lane keeps `timeout_seconds` as the wrapper bound (protocol:338): its collector adds no
        Job of its own and measure-m2-frame.ps1 owns its own device restoration.
    #>
    param([Parameter(Mandatory = $true)][System.Collections.IDictionary]$Plan)
    $lane = [string]$Plan.lane
    $timeout = [int]$Plan.timeout_seconds
    if ($timeout -lt 1) { throw "Lane '$lane' has no positive slot timeout." }
    if ($lane -ceq 'frame') {
        $frameBudget = [ordered]@{
            schema = $script:P4CollectorBudgetSchema
            lane = $lane
            slot_id = [string]$Plan.slot_id
            derived = $false
            timeout_seconds = $timeout
            instrumentation_seconds = 0
            install_seconds = 0
            dexopt_seconds = 0
            probe_count = 0
            probe_seconds = 0
            private_capture_seconds = 0
            reserve_seconds = 0
            cap_seconds = $script:P4BoundedNativeCapSeconds
            collector_timeout_seconds = $timeout
        }
        Assert-P4CollectorBoundWithinCap -Budget $frameBudget
        return $frameBudget
    }
    if ($lane -cnotin @('command', 'memory', 'publication')) { throw "Lane '$lane' has no collector budget." }
    $installs = @($Plan.install_kinds).Count
    $dexopts = @($Plan.dexopt_packages).Count
    $privates = @($Plan.private_files).Count
    $probes = @($Plan.quiescence_packages).Count + (4 * $installs) + $dexopts + $privates
    $budget = [ordered]@{
        schema = $script:P4CollectorBudgetSchema
        lane = $lane
        slot_id = [string]$Plan.slot_id
        derived = $true
        timeout_seconds = $timeout
        instrumentation_seconds = $timeout
        install_seconds = $installs * $script:P4InstallTimeoutSeconds
        dexopt_seconds = $dexopts * $script:P4DexoptTimeoutSeconds
        probe_count = $probes
        probe_seconds = $probes * $script:P4ProbeTimeoutSeconds
        private_capture_seconds = $privates * $script:P4PrivateFileTimeoutSeconds
        reserve_seconds = $script:P4CollectorReserveSeconds
        cap_seconds = $script:P4BoundedNativeCapSeconds
    }
    $budget.collector_timeout_seconds = [int]($budget.instrumentation_seconds + $budget.install_seconds +
        $budget.dexopt_seconds + $budget.probe_seconds + $budget.private_capture_seconds + $budget.reserve_seconds)
    if ($budget.collector_timeout_seconds -le $timeout) {
        throw "The derived collector bound for lane '$lane' does not outlast its instrumentation bound."
    }
    Assert-P4CollectorBoundWithinCap -Budget $budget
    return $budget
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
        # Protocol v4: one instrumentation invocation is bounded at exactly `timeout_seconds`. The
        # install/dexopt/probe/private-file calls carry their own separate bounds and are paid for by
        # the derived collector budget below, never by shortening this one.
        inner_timeout_seconds = [int]$Slot.timeout_seconds
        recovery_quarantine = $null
        # Declared for every lane so StrictMode callers may read it unconditionally; only the three
        # instrumentation lanes fill it in (the frame lane keeps the recovery quarantine above).
        private_file_quarantine = $null
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
            # measure-m2-frame.ps1 installs the release-like APK itself; the quarantine step installs
            # the debug APK first because release-like is non-debuggable and cannot be reached by run-as.
            $plan.install_kinds = @()
            $plan.dexopt_packages = @()
            $plan.recovery_quarantine =
                Get-P4RecoveryQuarantinePlan -Manifest $Manifest -Role $role -SlotId ([string]$Slot.id)
            $plan.frame_parameters = Get-P4FrameCollectorParameters -Manifest $Manifest -Slot $Slot
            $plan.frame_slot_directory =
                Join-Path ([string]$Manifest.frame_experiment.directory) (
                    'slot-{0:D2}-{1}-{2}-attempt-1' -f [int]$Slot.run, [string]$Slot.runner, $role
                )
            $plan.collector_budget = Get-P4CollectorBudget -Plan $plan
            $plan.collector_timeout_seconds = [int]$plan.collector_budget.collector_timeout_seconds
            return $plan
        }
        default { throw "Lane '$($Slot.lane)' is not a device lane." }
    }
    $plan.adb_arguments = @(
        Get-P4InstrumentationArguments -TestPackage $plan.test_package -Class $plan.class `
            -Arguments $plan.instrumentation_arguments
    )
    $plan.private_file_quarantine = Get-P4PrivateFileQuarantinePlan -Manifest $Manifest -Plan $plan
    $plan.collector_budget = Get-P4CollectorBudget -Plan $plan
    $plan.collector_timeout_seconds = [int]$plan.collector_budget.collector_timeout_seconds
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
    # Protocol v4: the instrumentation gets exactly `timeout_seconds`. The outer wrapper Job is bounded
    # by the derived collector budget, which is strictly larger, so the outer kill can never precede it.
    $innerTimeout = [int]$Plan.inner_timeout_seconds
    if ($innerTimeout -ne [int]$Plan.timeout_seconds) {
        throw 'The instrumentation bound must equal the protocol slot timeout.'
    }
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

function Get-P4TextSha256 {
    param([Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Lines)
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($Lines -join "`n") + "`n")
        return [Convert]::ToHexString($sha256.ComputeHash($bytes)).ToLowerInvariant()
    }
    finally { $sha256.Dispose() }
}

function Get-P4PrivateDirectoryListing {
    <#
        Returns the entry names of a private directory, or $null when the directory itself is absent.
        `exec-out` avoids the pty line-ending rewriting of a plain `adb shell`.
    #>
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Context,
        [Parameter(Mandatory = $true)][string]$Package,
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][string]$LogName
    )
    $result = Invoke-P4BoundedAdb -Context $Context -LogName $LogName `
        -AdbArguments @('exec-out', 'run-as', $Package, 'ls', '-1', $RelativePath) `
        -TimeoutSeconds $script:P4ProbeTimeoutSeconds
    $text = (@($result.OutputLines) -join "`n")
    # `adb exec-out` does not propagate the remote exit code, so absence and errors are recognized from
    # the diagnostic text itself, never from the exit code alone. `run-as:` must be recognized too:
    # "run-as: Package 'x' is not debuggable" / "unknown package" arrive on stdout with exit 0, and a
    # bare `^ls:` test would accept them as entry names - a quarantine that silently did nothing.
    $lines = @($result.OutputLines | ForEach-Object { $_.Trim() } | Where-Object { $_.Length -gt 0 })
    $diagnostics = @($lines | Where-Object { $_ -cmatch '^(?:ls|run-as):' })
    if ($diagnostics.Count -gt 0) {
        # Only `ls: <path>: No such file or directory` is absence; every `run-as:` line falls through
        # to the throw below, because an unreachable sandbox is undetermined, not empty.
        if ($diagnostics.Count -eq 1 -and $lines.Count -eq 1 -and
            $diagnostics[0] -cmatch "^ls: (?:\S+: )?$([regex]::Escape($RelativePath)): No such file or directory$") {
            return $null
        }
        throw "Unable to list $Package/$RelativePath (exit $($result.ExitCode)): $text"
    }
    if ($result.ExitCode -ne 0) {
        throw "Unable to list $Package/$RelativePath (exit $($result.ExitCode)): $text"
    }
    return $lines
}

function Test-P4PrivateEntryPresent {
    <#
        Existence of the entry itself, independent of whether a directory holds anything: `ls -1` of an
        existing but EMPTY directory yields no lines, and an empty array returned from a function
        collapses to $null at the call site - so a quarantine directory left behind empty by an earlier
        attempt would read as absent and be silently reused. `ls -d` names the entry instead of its
        contents, so presence and emptiness stay separate facts.
    #>
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Context,
        [Parameter(Mandatory = $true)][string]$Package,
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][string]$LogName
    )
    $result = Invoke-P4BoundedAdb -Context $Context -LogName $LogName `
        -AdbArguments @('exec-out', 'run-as', $Package, 'ls', '-d', $RelativePath) `
        -TimeoutSeconds $script:P4ProbeTimeoutSeconds
    $text = (@($result.OutputLines) -join "`n")
    $lines = @($result.OutputLines | ForEach-Object { $_.Trim() } | Where-Object { $_.Length -gt 0 })
    $diagnostics = @($lines | Where-Object { $_ -cmatch '^(?:ls|run-as):' })
    if ($diagnostics.Count -gt 0) {
        if ($diagnostics.Count -eq 1 -and $lines.Count -eq 1 -and
            $diagnostics[0] -cmatch "^ls: (?:\S+: )?$([regex]::Escape($RelativePath)): No such file or directory$") {
            return $false
        }
        throw "Unable to probe $Package/$RelativePath (exit $($result.ExitCode)): $text"
    }
    if ($result.ExitCode -ne 0 -or $lines.Count -ne 1 -or $lines[0] -cne $RelativePath) {
        throw "Unable to probe $Package/$RelativePath (exit $($result.ExitCode)): $text"
    }
    return $true
}

function Invoke-P4RecoveryQuarantine {
    <#
        Frame lane precondition. The v2 candidate writes a recovery record the v1 baseline cannot read,
        after which the baseline editor refuses New/Open and no frame slot can create its document.
        This moves only the three exact live record names into a per-slot quarantine directory inside
        the app's own no_backup storage. Nothing is deleted, `pm clear` is never used, and every other
        no_backup entry - including hide's guarded user recovery - is left exactly as it is.
    #>
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Context,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Manifest,
        [Parameter(Mandatory = $true)][ValidateSet('baseline', 'candidate')][string]$Role,
        [Parameter(Mandatory = $true)][string]$SlotId
    )
    $plan = Get-P4RecoveryQuarantinePlan -Manifest $Manifest -Role $Role -SlotId $SlotId
    $package = [string]$plan.package
    $quarantine = [string]$plan.quarantine_path
    $recordPath = Join-Path $Context.output_directory 'recovery-quarantine.json'
    if (Test-Path -LiteralPath $recordPath) { throw "Recovery quarantine record already exists: $recordPath" }

    Assert-P4RemotePackagesStopped -Context $Context -Packages @(
        $plan.package, (Get-P4LanePackages -Manifest $Manifest -Role $Role).application_test,
        (Get-P4LanePackages -Manifest $Manifest -Role $Role).publication_test
    ) -Stage 'quarantine'
    # run-as needs the debuggable build; the release-like APK measure-m2-frame.ps1 installs afterwards
    # is not debuggable. Installing the debug APK here also replaces any release-like build in place.
    Assert-P4InstalledApk -Context $Context -Manifest $Manifest -Role $Role -Kind $plan.install_kind | Out-Null

    $beforeDetail = Invoke-P4BoundedAdb -Context $Context -LogName 'recovery-quarantine-before-la.log' `
        -AdbArguments @('exec-out', 'run-as', $package, 'ls', '-la', $plan.source_directory) `
        -TimeoutSeconds $script:P4ProbeTimeoutSeconds
    $beforeListing = Get-P4PrivateDirectoryListing -Context $Context -Package $package `
        -RelativePath $plan.source_directory -LogName 'recovery-quarantine-before-ls.log'
    # Assigning an `if` expression unwraps a one-element array into a scalar; keep it an array.
    $present = @(if ($null -eq $beforeListing) { @() } else {
        @($plan.live_names | Where-Object { $_ -cin $beforeListing })
    })
    foreach ($name in $present) {
        if ($name -cnotin $script:P4RecoveryLiveNames -or $name -match '[\\/]') {
            throw "Refusing to move an entry outside the fixed live recovery record set: $name"
        }
    }

    if ($present.Count -gt 0) {
        # Presence, not emptiness: an earlier attempt that created the directory and then failed before
        # moving anything must still refuse this one.
        if (Test-P4PrivateEntryPresent -Context $Context -Package $package `
                -RelativePath $quarantine -LogName 'recovery-quarantine-target-ls.log') {
            throw "A prior attempt already quarantined into $package/$quarantine; it is never reused or deleted."
        }
        $mkdir = Invoke-P4BoundedAdb -Context $Context -LogName 'recovery-quarantine-mkdir.log' `
            -AdbArguments @('shell', 'run-as', $package, 'mkdir', '-p', $quarantine) `
            -TimeoutSeconds $script:P4ProbeTimeoutSeconds
        if ($mkdir.ExitCode -ne 0) { throw "Unable to create $package/$quarantine (exit $($mkdir.ExitCode))." }
        $moveIndex = 0
        foreach ($name in $present) {
            $moveIndex += 1
            $move = Invoke-P4BoundedAdb -Context $Context -LogName "recovery-quarantine-mv-$moveIndex.log" `
                -AdbArguments @(
                    'shell', 'run-as', $package, 'mv',
                    "$($plan.source_directory)/$name", "$quarantine/$name"
                ) -TimeoutSeconds $script:P4ProbeTimeoutSeconds
            if ($move.ExitCode -ne 0) { throw "Unable to quarantine $package/$name (exit $($move.ExitCode))." }
        }
    }

    $afterDetail = Invoke-P4BoundedAdb -Context $Context -LogName 'recovery-quarantine-after-la.log' `
        -AdbArguments @('exec-out', 'run-as', $package, 'ls', '-la', $plan.source_directory) `
        -TimeoutSeconds $script:P4ProbeTimeoutSeconds
    $afterListing = Get-P4PrivateDirectoryListing -Context $Context -Package $package `
        -RelativePath $plan.source_directory -LogName 'recovery-quarantine-after-ls.log'
    $remaining = @(if ($null -eq $afterListing) { @() } else {
        @($plan.live_names | Where-Object { $_ -cin $afterListing })
    })
    if ($remaining.Count -ne 0) {
        throw "The live recovery record is still present after quarantine: $($remaining -join ', ')"
    }
    if ($present.Count -gt 0) {
        $quarantined = Get-P4PrivateDirectoryListing -Context $Context -Package $package `
            -RelativePath $quarantine -LogName 'recovery-quarantine-verify-ls.log'
        if ($null -eq $quarantined -or
            @(Compare-Object @($present) @($quarantined | Where-Object { $_ -cin $plan.live_names })).Count -ne 0) {
            throw 'The quarantined recovery record is not exactly what was moved aside.'
        }
    }

    $record = [ordered]@{
        schema = $script:P4RecoveryQuarantineSchema
        slot_id = $SlotId
        role = $Role
        package = $package
        source_directory = [string]$plan.source_directory
        quarantine_path = $quarantine
        live_names = @($plan.live_names)
        moved = @($present)
        before_listing_sha256 = Get-P4TextSha256 -Lines @($beforeDetail.OutputLines)
        after_listing_sha256 = Get-P4TextSha256 -Lines @($afterDetail.OutputLines)
        deleted = $false
        recorded_utc = [datetime]::UtcNow.ToString('o')
    }
    Write-NewInvocationFile $recordPath ($record | ConvertTo-Json -Depth 5)
    return $record
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

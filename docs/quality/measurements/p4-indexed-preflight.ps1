Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../bounded-native-command.ps1')
. (Join-Path $PSScriptRoot '../baseline-profile-evidence.ps1')
. (Join-Path $PSScriptRoot 'p4-indexed-device-state.ps1')

# Every Assert-* here refuses by throwing and writes nothing to the pipeline; callers never branch
# on a return value. Two recorded conventions: `observed_dexopt` describes the artifact's own
# test_package when it has one and its target_package otherwise (so app_debug and app_release_like
# share the single installed application package). Both roles therefore record the same package and
# the same live value per kind, because only one build of a package can be installed at a time.
# The measurement inventory is derived from each
# role clone's own build tree, so it holds that clone's measurement sources only - the P4 tooling
# itself lives at newer commits and is bound through `tools.*`, not through measurement_files.
$script:P4ProtocolId = 'nene-pixel-p4-indexed-cutover-verification-v7'
# Lane 3 revision (still v7 identity) takes its Issue/protocol agreement from Issue #120;
# Issue #106 carried the run5 agreement and is CLOSED.
$script:P4AgreementIssue = 120
$script:P4ManifestSchema = 'nene-pixel-p4-indexed-preflight-v7'
# Lane 3 revision (still v7 identity): baseline production is main at collection time (2f0b617, 2026-09-23).
# Issue #106's accepted baseline 2dd4e01 stays bound only to the preserved run5 evidence.
$script:P4BaselineProduction = '2f0b617e56f7bcf3d71b5a258a48e0edead354d9'

# Exactly one contract record per lane boundary. Absent, duplicate or unknown scopes are refusals.
$script:P4CollectorContractScopes = @(
    'host-analyzer', 'command-analyzer', 'memory-analyzer', 'publication-analyzer', 'frame-analyzer',
    'frame-collector-protocol', 'slot-boundary', 'preflight-contract', 'bounded-native', 'device-state'
)

# Fixed APK semantics. $null means "read it from the APK and require the manifest to agree".
$script:P4ApplicationPackage = 'io.github.hideyukimori.nenepixel'
$script:P4ArtifactContract = [ordered]@{
    app_debug = [ordered]@{ variant = 'debug'; target_package = $script:P4ApplicationPackage
        test_package = 'none'; requested_dexopt = 'verify'; debuggable = $true; instrumented = $false }
    test_debug = [ordered]@{ variant = 'debugAndroidTest'; target_package = $script:P4ApplicationPackage
        test_package = 'io.github.hideyukimori.nenepixel.test'; requested_dexopt = 'verify'
        debuggable = $true; instrumented = $true }
    app_release_like = [ordered]@{ variant = 'benchmarkRelease'; target_package = $script:P4ApplicationPackage
        test_package = 'none'; requested_dexopt = 'speed-profile'; debuggable = $false; instrumented = $false }
    publication_test = [ordered]@{ variant = 'debugAndroidTest'; target_package = $null
        test_package = $null; requested_dexopt = 'verify'; debuggable = $true; instrumented = $true }
}

# The measurement inventory is a closed set derived from the role's own build commit.
$script:P4MeasurementPathPatterns = @(
    '^docs/quality/[^/]+\.ps1$',
    '^docs/quality/measurements/[^\r\n]+$',
    '^app/android/src/androidTest/kotlin/io/github/hideyukimori/nenepixel/measurement/[^/]+\.kt$',
    ('^adapters/persistence/src/androidTest/kotlin/io/github/hideyukimori/nenepixel/adapters/persistence/' +
        'AutosavePublication[^/]*\.kt$')
)
$script:P4SharedHostEvidenceSources = @(
    'core/project-format/src/test/kotlin/io/github/hideyukimori/nenepixel/core/projectformat/ProjectFormatV1CodecHostEvidence.kt',
    'adapters/persistence/src/test/kotlin/io/github/hideyukimori/nenepixel/adapters/persistence/RecoveryRecordHostEvidence.kt'
)
# The legacy-import host runner exists only for the candidate; the slot catalog agrees.
$script:P4CandidateHostEvidenceSources = @(
    'core/application/src/test/kotlin/io/github/hideyukimori/nenepixel/core/application/persistence/LegacyImportHostEvidence.kt'
)

# The only directories whose class/jar output may appear in the host JavaExec inventory. This set is
# not guessed: it is the union of the worktree-relative roots the real resolved classpath used in the
# `-PneneP4ClasspathOnly=true` probes recorded under build/reports/issue-106/classpath-probe-<n>/, of
# which the validator replays the three newest candidate records. Gradle cache jars stay absolute and
# out of scope.
$script:P4CompiledJvmModules = @('core/domain', 'core/pixel-engine', 'core/application', 'core/project-format')
$script:P4CompiledDirectories = @(
    @($script:P4CompiledJvmModules | ForEach-Object {
        "$_/build/classes/java/main"
        "$_/build/classes/java/test"
        "$_/build/classes/kotlin/main"
        "$_/build/classes/kotlin/test"
        "$_/build/resources/main"
        "$_/build/resources/test"
        "$_/build/libs"
    }) + @(
        # The Android library module publishes through AGP intermediates, never through build/classes.
        'adapters/persistence/build/intermediates/built_in_kotlinc/debugUnitTest/compileDebugUnitTestKotlin/classes',
        'adapters/persistence/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes',
        'adapters/persistence/build/intermediates/java_res/debug/processDebugJavaRes/out',
        'adapters/persistence/build/intermediates/java_res/debugUnitTest/processDebugUnitTestJavaRes/out',
        'adapters/persistence/build/intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug',
        'adapters/persistence/build/intermediates/compile_and_runtime_r_class_jar/debugUnitTest/generateDebugUnitTestStubRFile'
    )
)
# Present in every role clone once the modules are built; absence is a build gap, not a role difference.
$script:P4RequiredCompiledDirectories = @(
    $script:P4CompiledJvmModules | ForEach-Object { "$_/build/classes/kotlin/main" }
)
# Each JVM module reaches another module's classpath as its published jar, so both must exist.
$script:P4RequiredCompiledFiles = @(
    $script:P4CompiledJvmModules | ForEach-Object { "$_/build/libs/$(Split-Path -Leaf $_).jar" }
)
$script:P4CompiledFileExtensions = @('.class', '.jar')

$script:P4PreservationSchema = 'nene-pixel-device-preservation-v1'
# The recovery guard already consumed for hide's own data. Reusing it would overwrite that backup.
$script:P4RetiredPreservationGuard = 'no_backup/issue-106-user-recovery-20260913-1757'
$script:P4PreservationFreshnessHours = 24

$script:P4NoSampleInspectionSchema = 'nene-pixel-p4-no-sample-inspection-v1'
$script:P4GeometryId = 'initial-fit-centered-v1'
$script:P4InspectionRootBounds = '[0,0][1920,1200]'
$script:P4InspectionRotation = 1

$script:P4HostClasspathSchema = 'nene-pixel-p4-host-classpath-v1'

function Get-P4FrameWrapperBound {
    <#
        Protocol v5 (Lane 3, Issue #120) derives the frame wrapper bound from the slot's own
        operation count instead of fixing it at 300/600 s:

            wrapper_bound = 300 s setup + 15 s x operations
            operations    = workload families x (warmups + samples)

        A decision slot measures families 1-2, so it is 2 x (5 + 50) = 110 operations -> 1950 s; a
        diagnostic slot measures families 1-3, so it is 3 x (5 + 10) = 45 operations -> 975 s. V4's
        fixed 300/600 s came from the ~122 s intentional dwell alone and ignored the per-operation
        gfxinfo/framestats/pull/UI cost and the 16 MOVE injections of the 256 by 256 families. Every
        reported per-frame metric comes from `gfxinfo`, so the bound changes no measured value; it
        guards a hang only.
    #>
    param(
        [Parameter(Mandatory = $true)][ValidateRange(1, 3)][int]$Families,
        [Parameter(Mandatory = $true)][ValidateRange(0, 1000)][int]$Warmups,
        [Parameter(Mandatory = $true)][ValidateRange(1, 1000)][int]$Samples
    )
    $operations = $Families * ($Warmups + $Samples)
    return [int](300 + 15 * $operations)
}

function Get-P4SlotCatalog {
    $slots = [System.Collections.Generic.List[object]]::new()
    foreach ($runner in @('project', 'recovery', 'legacy')) {
        $roles = if ($runner -eq 'legacy') { @('candidate') } else { @('baseline', 'candidate') }
        foreach ($role in $roles) {
            $slots.Add([ordered]@{ id = "host-$runner-$role"; lane = 'host'; role = $role; runner = $runner;
                run = 1; timeout_seconds = 180; warmups = 5; samples = 20 })
        }
    }
    foreach ($role in @('baseline', 'candidate')) {
        # Protocol v5 (protocol:196-199) bounds one command instrumentation invocation at 600 s: the
        # candidate role runs 2,200 samples whose palette-workload per-sample cost is unmeasured, so
        # the bound guards a hang only. Memory (protocol:274-276) and publication (protocol:509-511)
        # stay at 300 s.
        $slots.Add([ordered]@{ id = "command-$role"; lane = 'command'; role = $role; runner = 'command';
            run = 1; timeout_seconds = 600; warmups = 5; samples = 200 })
    }
    foreach ($family in @('baseline-common', 'candidate-common', 'candidate-palette', 'candidate-import')) {
        $parts = $family.Split('-')
        foreach ($run in 1..5) {
            $slots.Add([ordered]@{ id = "memory-$family-$run"; lane = 'memory'; role = $parts[0];
                runner = $parts[1]; run = $run; timeout_seconds = 300; warmups = 0; samples = 1 })
        }
    }
    foreach ($role in @('baseline', 'candidate')) {
        $slots.Add([ordered]@{ id = "publication-$role"; lane = 'publication'; role = $role; runner = 'publication';
            run = 1; timeout_seconds = 300; warmups = 5; samples = 20 })
    }
    return $slots.ToArray()
}

function Get-P4FrameSlotCatalog {
    <#
        Protocol v7 moved Lane 3 out of Issue #106's fixed order and acceptance to Issue #120, so these
        four frame slots are not part of `Get-P4SlotCatalog` and are neither reserved nor collected
        under Issue #106. Issue #120's Lane 3 revision (frame experiment schema v5) fixes the order
        decision baseline, decision candidate, diagnostic baseline, diagnostic candidate; the ids
        carry that order. The preserved run5 records keep their v6 ids (`frame-1-baseline-diagnostic`
        ... `frame-4-baseline-decision`) as historical names only.
    #>
    $slots = [System.Collections.Generic.List[object]]::new()
    $sequence = 0
    foreach ($slot in @('baseline-decision', 'candidate-decision', 'baseline-diagnostic', 'candidate-diagnostic')) {
        $sequence++
        $parts = $slot.Split('-')
        $diagnostic = $parts[1] -eq 'diagnostic'
        $families = if ($diagnostic) { 3 } else { 2 }
        $warmups = 5
        $samples = if ($diagnostic) { 10 } else { 50 }
        $slots.Add([ordered]@{ id = "frame-$sequence-$slot"; lane = 'frame'; role = $parts[0]; runner = $parts[1];
            run = $sequence;
            timeout_seconds = (Get-P4FrameWrapperBound -Families $families -Warmups $warmups -Samples $samples);
            warmups = $warmups; samples = $samples })
    }
    return $slots.ToArray()
}

function Assert-P4RequiredValue {
    param([AllowNull()]$Value, [string]$Name)
    if ($null -eq $Value) { throw "Missing preflight value: $Name" }
    if ($Value -is [string]) {
        if ([string]::IsNullOrWhiteSpace($Value) -or $Value -match 'UNSET_PREFLIGHT|TODO|PLACEHOLDER') {
            throw "Unresolved preflight value: $Name"
        }
    } elseif ($Value -is [System.Collections.IDictionary]) {
        if ($Value.Count -eq 0) { throw "Empty preflight inventory: $Name" }
        foreach ($key in $Value.Keys) { Assert-P4RequiredValue $Value[$key] "$Name.$key" }
    } elseif ($Value -is [System.Collections.IEnumerable]) {
        $items = @($Value)
        if ($items.Count -eq 0) { throw "Empty preflight inventory: $Name" }
        for ($i = 0; $i -lt $items.Count; $i++) { Assert-P4RequiredValue $items[$i] "$Name[$i]" }
    }
}

function Assert-P4RequiredKeys {
    param([System.Collections.IDictionary]$Record, [string[]]$Keys, [string]$Name)
    foreach ($key in $Keys) {
        if (-not $Record.Contains($key)) { throw "Missing preflight field: $Name.$key" }
        Assert-P4RequiredValue $Record[$key] "$Name.$key"
    }
}

function Assert-P4FileRecord {
    param([System.Collections.IDictionary]$Record, [string]$Name)
    Assert-P4RequiredKeys $Record @('path', 'byte_count', 'sha256') $Name
    if ($Record.sha256 -cnotmatch '^[0-9a-f]{64}$' -or [long]$Record.byte_count -le 0) {
        throw "Invalid file identity: $Name"
    }
    $file = Get-Item -LiteralPath $Record.path
    if ($file.PSIsContainer -or $file.Length -ne [long]$Record.byte_count -or
        (Get-FileSha256 $file.FullName) -cne $Record.sha256) { throw "Artifact drift: $Name" }
}

function Get-P4FileInventoryHash {
    param([object[]]$Files)
    $names = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $lines = foreach ($file in $Files) {
        Assert-P4RequiredKeys $file @('relative_path', 'path', 'byte_count', 'sha256') 'inventory'
        $name = [string]$file.relative_path
        if ($name -match '(^/|\\|(^|/)\.\.(/|$)|[\r\n\t])' -or -not $names.Add($name)) {
            throw 'Invalid or duplicate inventory relative path.'
        }
        Assert-P4FileRecord $file $name
        "$name`t$($file.byte_count)`t$($file.sha256)"
    }
    if (@($lines).Count -eq 0) { throw 'An artifact inventory must be nonempty.' }
    $ordered = [string[]]@($lines)
    [Array]::Sort($ordered, [StringComparer]::Ordinal)
    Get-Sha256Hex ([Text.UTF8Encoding]::new($false).GetBytes(($ordered -join "`n") + "`n"))
}

function Assert-P4NoReparsePath {
    <#
        An inventory path must resolve inside its role worktree and neither it nor any ancestor up to
        that worktree may be a reparse point: otherwise the bytes hashed here are not the bytes the
        build produced.
    #>
    param([string]$Path, [string]$Root, [string]$Name)

    $full = [IO.Path]::GetFullPath($Path)
    $rootFull = [IO.Path]::GetFullPath($Root).TrimEnd([char]'\', [char]'/')
    $prefix = $rootFull + [IO.Path]::DirectorySeparatorChar
    if (-not $full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Inventory path escapes its role worktree: $Name"
    }
    $current = $full
    while ($true) {
        $item = Get-Item -LiteralPath $current -Force -ErrorAction Stop
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Inventory path crosses a reparse point: $Name ($current)"
        }
        if ($current.TrimEnd([char]'\', [char]'/') -ieq $rootFull) { break }
        $parent = [IO.Path]::GetDirectoryName($current)
        if ([string]::IsNullOrEmpty($parent) -or $parent -ieq $current) {
            throw "Inventory path left its role worktree before reaching it: $Name"
        }
        $current = $parent
    }
}

function Get-P4TrackedPaths {
    param([string]$Worktree, [string]$Commit, [string]$Name)

    $tracked = @(& git -C $Worktree ls-tree -r --name-only --full-tree $Commit)
    if ($LASTEXITCODE -ne 0) { throw "Unable to list the $Name build tree." }
    foreach ($path in $tracked) {
        # git quotes unusual bytes; such a path can never be one of the fixed measurement inputs.
        if ($path.StartsWith('"')) { throw "The $Name build tree contains an unrepresentable path." }
    }
    return [string[]]$tracked
}

function Get-P4ExpectedMeasurementPaths {
    param([string]$Worktree, [string]$Commit, [string]$Name)

    $tracked = Get-P4TrackedPaths $Worktree $Commit $Name
    $trackedSet = [Collections.Generic.HashSet[string]]::new([string[]]$tracked, [StringComparer]::Ordinal)
    $expected = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($path in $tracked) {
        foreach ($pattern in $script:P4MeasurementPathPatterns) {
            if ($path -cmatch $pattern) { [void]$expected.Add($path); break }
        }
    }
    $required = @($script:P4SharedHostEvidenceSources)
    if ($Name -ceq 'candidate') { $required += @($script:P4CandidateHostEvidenceSources) }
    foreach ($path in $required) {
        if (-not $trackedSet.Contains($path)) { throw "The $Name build commit omits a host evidence source: $path" }
        [void]$expected.Add($path)
    }
    if ($expected.Count -eq 0) { throw "The $Name measurement inventory cannot be empty." }
    return , $expected
}

function Assert-P4InventorySetEquals {
    param([System.Collections.Generic.HashSet[string]]$Expected,
        [System.Collections.Generic.HashSet[string]]$Observed, [string]$Name)

    if ($Observed.SetEquals($Expected)) { return }
    $missing = @($Expected) | Where-Object { -not $Observed.Contains($_) }
    $unexpected = @($Observed) | Where-Object { -not $Expected.Contains($_) }
    throw ("$Name inventory set mismatch. Missing: $(($missing | Sort-Object) -join ', '). " +
        "Unexpected: $(($unexpected | Sort-Object) -join ', ').")
}

function Assert-P4TrackedBlob {
    <#
        The working-tree bytes must be the exact blob the build commit records for that path, so a
        measurement source cannot drift between the build and the inventory that names it.
    #>
    param([string]$Worktree, [string]$Commit, [string]$RelativePath, [string]$Path, [string]$Name)

    $blob = (& git -C $Worktree rev-parse "${Commit}:${RelativePath}" 2>$null)
    if ($LASTEXITCODE -ne 0) { throw "Measurement source is untracked at the build commit: $Name" }
    $blob = ([string]$blob).Trim()
    $actual = ([string](& git -C $Worktree hash-object -- $Path)).Trim()
    if ($LASTEXITCODE -ne 0 -or $blob -cnotmatch '^[0-9a-f]{40}$' -or $actual -cne $blob) {
        throw "Measurement source differs from its build-commit blob: $Name"
    }
}

function Assert-P4MeasurementInventory {
    param([System.Collections.IDictionary]$Role, [string]$Name)

    $expected = Get-P4ExpectedMeasurementPaths $Role.worktree $Role.build_commit $Name
    $observed = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($file in $Role.measurement_files) {
        $relative = [string]$file.relative_path
        if (-not $observed.Add($relative)) { throw "Duplicate measurement inventory path: $Name/$relative" }
        Assert-P4NoReparsePath $file.path $Role.worktree "$Name.measurement_files/$relative"
        $declared = [IO.Path]::GetFullPath((Join-Path $Role.worktree $relative))
        if ([IO.Path]::GetFullPath($file.path) -cne $declared) {
            throw "Measurement inventory path does not match its relative path: $Name/$relative"
        }
        Assert-P4TrackedBlob $Role.worktree $Role.build_commit $relative $file.path "$Name/$relative"
    }
    Assert-P4InventorySetEquals $expected $observed "$Name measurement"
}

function Get-P4ExpectedCompiledPaths {
    param([string]$Worktree, [string]$Name)

    $expected = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $found = 0
    foreach ($relativeDirectory in $script:P4CompiledDirectories) {
        $directory = [IO.Path]::GetFullPath((Join-Path $Worktree $relativeDirectory))
        if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
            if ($script:P4RequiredCompiledDirectories -ccontains $relativeDirectory) {
                throw "The $Name build lacks a required host classpath directory: $relativeDirectory"
            }
            continue
        }
        Assert-P4NoReparsePath $directory $Worktree "$Name.compiled_directory/$relativeDirectory"
        $found++
        foreach ($file in @(Get-ChildItem -LiteralPath $directory -Recurse -File -Force)) {
            if ($script:P4CompiledFileExtensions -cnotcontains $file.Extension.ToLowerInvariant()) { continue }
            $relative = ([IO.Path]::GetRelativePath($Worktree, $file.FullName)) -replace '\\', '/'
            [void]$expected.Add($relative)
        }
    }
    if ($found -eq 0 -or $expected.Count -eq 0) { throw "The $Name host classpath output is empty." }
    foreach ($relative in $script:P4RequiredCompiledFiles) {
        $archive = [IO.Path]::GetFullPath((Join-Path $Worktree $relative))
        if (-not (Test-Path -LiteralPath $archive -PathType Leaf) -or -not $expected.Contains($relative)) {
            throw "The $Name build lacks a required host classpath archive: $relative"
        }
    }
    return , $expected
}

function Assert-P4CompiledInventory {
    param([System.Collections.IDictionary]$Role, [string]$Name)

    $expected = Get-P4ExpectedCompiledPaths $Role.worktree $Name
    $observed = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($file in $Role.compiled_files) {
        $relative = [string]$file.relative_path
        if (-not $observed.Add($relative)) { throw "Duplicate compiled inventory path: $Name/$relative" }
        Assert-P4NoReparsePath $file.path $Role.worktree "$Name.compiled_files/$relative"
        $declared = [IO.Path]::GetFullPath((Join-Path $Role.worktree $relative))
        if ([IO.Path]::GetFullPath($file.path) -cne $declared) {
            throw "Compiled inventory path does not match its relative path: $Name/$relative"
        }
    }
    Assert-P4InventorySetEquals $expected $observed "$Name compiled"
}

function Read-P4HostClasspathRecord {
    param([string]$Path, [string]$Role)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "The host runner did not record its classpath evidence: $Path"
    }
    $lines = @(Get-Content -LiteralPath $Path)
    if ($lines.Count -lt 3 -or $lines[0] -cne $script:P4HostClasspathSchema -or $lines[1] -cne "role`t$Role") {
        throw "Host classpath evidence has the wrong schema or role: $Path"
    }
    return $lines
}

function Assert-P4HostClasspathAgreement {
    <#
        The manifest's compiled inventory must be exactly the in-worktree class/jar output that the
        real Gradle JavaExec classpath resolved to, recomputed here from the per-file record rather
        than trusted from the runner's own aggregate.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$OutputDirectory,
        [Parameter(Mandatory = $true)][ValidateSet('baseline', 'candidate')][string]$Role,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Manifest
    )

    $source = $Manifest.roles[$Role]
    $rootLines = Read-P4HostClasspathRecord (Join-Path $OutputDirectory 'classpath.txt') $Role
    if ($rootLines[2] -cnotmatch '^roots\t(?<count>\d+)$') { throw 'Host classpath roots are unreadable.' }
    $rootCount = [int]$Matches['count']
    # A range built from Count - 1 would run backwards when nothing follows the header.
    $roots = @()
    if ($rootLines.Count -gt 3) { $roots = @($rootLines[3..($rootLines.Count - 1)]) }
    if ($rootCount -lt 1 -or $roots.Count -ne $rootCount) { throw 'Host classpath root count disagrees with its list.' }
    foreach ($root in $roots) {
        if (-not [IO.Path]::IsPathFullyQualified($root)) { throw "Host classpath root is not absolute: $root" }
    }

    $hashLines = Read-P4HostClasspathRecord (Join-Path $OutputDirectory 'classpath-sha256.txt') $Role
    if ($hashLines[2] -cnotmatch '^files\t(?<count>\d+)$') { throw 'Host classpath file count is unreadable.' }
    $fileCount = [int]$Matches['count']
    if ($hashLines.Count -ne 4 + $fileCount) { throw 'Host classpath record length disagrees with its file count.' }
    $aggregateLine = $hashLines[$hashLines.Count - 1]
    if ($aggregateLine -cnotmatch '^aggregate\t(?<sha>[0-9a-f]{64})$') { throw 'Host classpath aggregate is unreadable.' }
    $recordedAggregate = $Matches['sha']

    $records = [Collections.Generic.List[string]]::new()
    $observed = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $declared = @{}
    foreach ($file in $source.compiled_files) { $declared[[string]$file.relative_path] = $file }
    for ($i = 3; $i -lt $hashLines.Count - 1; $i++) {
        $parts = $hashLines[$i] -split "`t"
        if ($parts.Count -ne 4 -or $parts[0] -cne 'file') { throw 'Host classpath file record is malformed.' }
        $path = $parts[1]
        if ($parts[2] -cnotmatch '^\d+$' -or $parts[3] -cnotmatch '^[0-9a-f]{64}$') {
            throw 'Host classpath file record carries an invalid identity.'
        }
        if ([IO.Path]::IsPathFullyQualified($path)) { continue }  # outside the worktree: locks cover it
        $extension = [IO.Path]::GetExtension($path).ToLowerInvariant()
        if ($script:P4CompiledFileExtensions -cnotcontains $extension) { continue }
        if (-not $observed.Add($path)) { throw "The host classpath repeats $path." }
        if (-not $declared.ContainsKey($path)) { throw "The host classpath used an undeclared file: $path" }
        $record = $declared[$path]
        if ([string]$record.byte_count -cne $parts[2] -or [string]$record.sha256 -cne $parts[3]) {
            throw "The host classpath file differs from the preflight inventory: $path"
        }
        $records.Add("$path`t$($parts[2])`t$($parts[3])")
    }
    # One slot runs one runner, whose classpath is a proper subset of the role's whole compiled
    # output: a project-format slot never loads the application module's classes. The relation is
    # therefore containment - every entry the slot really used must appear in the inventory with the
    # same bytes - not equality. The union's own completeness is proved by Assert-P4CompiledInventory,
    # and `compiled_sha256` stays the union's aggregate, so it is deliberately not compared here.
    if ($observed.Count -eq 0) { throw "The $Role host classpath used no compiled output of its own worktree." }

    $ordered = [string[]]@($records)
    [Array]::Sort($ordered, [StringComparer]::Ordinal)
    $aggregate = Get-Sha256Hex ([Text.UTF8Encoding]::new($false).GetBytes(($ordered -join "`n") + "`n"))
    if ($aggregate -cne $recordedAggregate) { throw 'The host runner aggregate does not match its own file records.' }
}

function Test-P4GitAncestor {
    param([string]$Worktree, [string]$Ancestor, [string]$Descendant)

    & git -C $Worktree merge-base --is-ancestor $Ancestor $Descendant 2>$null
    $code = $LASTEXITCODE
    if ($code -eq 0) { return $true }
    if ($code -eq 1) { return $false }
    throw "Unable to decide ancestry of $Ancestor in $Descendant."
}

function Assert-P4GitLineage {
    param([System.Collections.IDictionary]$Manifest)

    $baseline = $Manifest.roles.baseline
    $candidate = $Manifest.roles.candidate
    if (-not (Test-P4GitAncestor $baseline.worktree $script:P4BaselineProduction $baseline.build_commit)) {
        throw 'The baseline build is not a descendant of the accepted baseline production commit.'
    }
    if (-not (Test-P4GitAncestor $candidate.worktree $candidate.production_commit $candidate.build_commit)) {
        throw 'The candidate build does not descend from its declared production commit.'
    }
    if (-not (Test-P4GitAncestor $candidate.worktree $script:P4BaselineProduction $candidate.production_commit)) {
        throw 'The candidate production commit does not descend from the accepted baseline.'
    }
    if ($candidate.production_commit -ceq $script:P4BaselineProduction) {
        throw 'The candidate production commit cannot be the baseline itself.'
    }
}

function Invoke-P4Aapt2 {
    param([string]$Aapt2Path, [string[]]$Arguments)

    $output = @(& $Aapt2Path @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "aapt2 failed: $($Arguments -join ' ')" }
    return [string[]]$output
}

function Assert-P4ApkPackaging {
    param([string]$Aapt2Path, [System.Collections.IDictionary]$Artifact, [string]$Kind, [string]$Name)

    $contract = $script:P4ArtifactContract[$Kind]
    if ([string]$Artifact.variant -cne $contract.variant) {
        throw "$Name declares the wrong build variant: $($Artifact.variant)"
    }
    if ([string]$Artifact.requested_dexopt -cne $contract.requested_dexopt) {
        throw "$Name declares the wrong requested dexopt mode: $($Artifact.requested_dexopt)"
    }
    $badging = (Invoke-P4Aapt2 $Aapt2Path @('dump', 'badging', $Artifact.path)) -join "`n"
    $packageMatches = [regex]::Matches($badging, "(?m)^package: name='(?<name>[^']+)'")
    if ($packageMatches.Count -ne 1) { throw "$Name does not declare exactly one package." }
    $package = $packageMatches[0].Groups['name'].Value
    if (-not $package.StartsWith($script:P4ApplicationPackage, [StringComparison]::Ordinal)) {
        throw "$Name packages a foreign application: $package"
    }
    $debuggable = [regex]::IsMatch($badging, '(?m)^application-debuggable\s*$')
    if ($debuggable -ne $contract.debuggable) {
        throw "$Name debuggable flag disagrees with its variant contract."
    }

    $xmltree = (Invoke-P4Aapt2 $Aapt2Path @(
            'dump', 'xmltree', '--file', 'AndroidManifest.xml', $Artifact.path)) -join "`n"
    $instrumentation = [regex]::Matches($xmltree, '(?m)^\s*E: instrumentation \(line=\d+\)\s*$')
    if (-not $contract.instrumented) {
        if ($instrumentation.Count -ne 0) { throw "$Name must not declare instrumentation." }
        if ([string]$Artifact.test_package -cne 'none') { throw "$Name must record test_package 'none'." }
        if ([string]$Artifact.target_package -cne $contract.target_package -or
            $package -cne $contract.target_package) {
            throw "$Name target package disagrees with the APK: $package"
        }
        return
    }
    if ($instrumentation.Count -ne 1) { throw "$Name must declare exactly one instrumentation." }
    if (-not $xmltree.Contains('"androidx.test.runner.AndroidJUnitRunner"')) {
        throw "$Name does not use the accepted instrumentation runner."
    }
    $targetMatches = [regex]::Matches($xmltree, 'android:targetPackage\(0x01010021\)="(?<name>[^"]+)"')
    if ($targetMatches.Count -ne 1) { throw "$Name does not declare exactly one instrumentation target." }
    $target = $targetMatches[0].Groups['name'].Value
    $expectedTarget = if ($null -ne $contract.target_package) { $contract.target_package } else { $target }
    $expectedTest = if ($null -ne $contract.test_package) { $contract.test_package } else { $package }
    if ($target -cne $expectedTarget) { throw "$Name instruments the wrong target package: $target" }
    if ($package -cne $expectedTest) { throw "$Name carries the wrong test package: $package" }
    if ([string]$Artifact.target_package -cne $target -or [string]$Artifact.test_package -cne $package) {
        throw "$Name manifest packages disagree with the APK ($target / $package)."
    }
}

function Assert-P4ProfileSourceBinding {
    <#
        The acceptance reader proves the canonical rules; it does not expose the raw source bytes.
        Bind manifest `profile.source` to the exact file both accepted invocations consumed.
    #>
    param([System.Collections.IDictionary]$Profile, [string]$Name)

    $evidenceRoot = Split-Path -Parent (Resolve-Path -LiteralPath $Profile.acceptance.path).Path
    foreach ($ordinal in 1..2) {
        $manifestPath = Join-Path $evidenceRoot "invocation-$ordinal/manifest.json"
        if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
            throw "$Name profile acceptance evidence lacks invocation $ordinal."
        }
        $invocation = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
        if ([string]$invocation.source_profile.raw_sha256 -cne [string]$Profile.source.sha256 -or
            [string]$invocation.source_profile.raw_byte_count -cne [string]$Profile.source.byte_count) {
            throw "$Name profile.source is not the accepted generation source (invocation $ordinal)."
        }
    }
}

function Get-P4JsonProperty {
    <#
        ConvertFrom-Json yields PSCustomObject, where a missing member silently reads as $null even
        under StrictMode. Every field this preflight reads goes through here so an absent field is
        named in the refusal instead of turning into a confusing comparison failure.
    #>
    param([AllowNull()]$Object, [string]$Name, [string]$Context)

    if ($null -eq $Object) { throw "Missing evidence object: $Context" }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { throw "Missing evidence field: $Context.$Name" }
    if ($null -eq $property.Value) { throw "Empty evidence field: $Context.$Name" }
    return , $property.Value
}

function Read-P4Utc {
    param([string]$Text, [string]$Name)

    $parsed = [datetime]::MinValue
    if (-not [datetime]::TryParse($Text, [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::RoundtripKind, [ref]$parsed)) {
        throw "Unreadable UTC timestamp: $Name"
    }
    if ($parsed.Kind -eq [DateTimeKind]::Unspecified) {
        $parsed = [datetime]::SpecifyKind($parsed, [DateTimeKind]::Utc)
    }
    return $parsed.ToUniversalTime()
}

function Assert-P4DevicePreservation {
    param([System.Collections.IDictionary]$Record, [string]$Serial, [string]$CreatedUtc)

    if ([IO.Path]::GetFileName($Record.path) -cne 'preservation.json') {
        throw 'Device preservation evidence must be the canonical preservation.json.'
    }
    $preservation = Get-Content -LiteralPath $Record.path -Raw | ConvertFrom-Json
    if ([string]$preservation.schema -cne $script:P4PreservationSchema) {
        throw 'Device preservation evidence has the wrong schema.'
    }
    if ([string]$preservation.serial -cne $Serial) {
        throw 'Device preservation evidence belongs to another device.'
    }
    $guard = [string]$preservation.guard
    if ([string]::IsNullOrWhiteSpace($guard)) { throw 'Device preservation evidence declares no guard.' }
    if ($guard -ceq $script:P4RetiredPreservationGuard) {
        throw 'Device preservation reuses the consumed recovery guard; the original user data would be overwritten.'
    }
    $created = Read-P4Utc $CreatedUtc 'manifest.created_utc'
    $written = (Get-Item -LiteralPath $Record.path).LastWriteTimeUtc
    if ($written -gt $created) { throw 'Device preservation evidence postdates the manifest.' }
    if (($created - $written).TotalHours -gt $script:P4PreservationFreshnessHours) {
        throw 'Device preservation evidence is older than the accepted freshness window.'
    }
}

function Read-P4Bounds {
    param([string]$Text, [string]$Name)

    if ($Text -cnotmatch '^\[(?<l>-?\d+),(?<t>-?\d+)\]\[(?<r>-?\d+),(?<b>-?\d+)\]$') {
        throw "Unreadable bounds: $Name"
    }
    $bounds = [ordered]@{ left = [int]$Matches['l']; top = [int]$Matches['t']
        right = [int]$Matches['r']; bottom = [int]$Matches['b'] }
    if ($bounds.right -le $bounds.left -or $bounds.bottom -le $bounds.top) {
        throw "Bounds must have positive extent: $Name"
    }
    return $bounds
}

function Assert-P4NoSampleInspection {
    param([System.Collections.IDictionary]$Record, [System.Collections.IDictionary]$Manifest)

    # The inspection precedes every reservation, so it lives in its own directory: neither inside the
    # reserved experiment output nor inside the frame experiment it informs.
    $inspectionPath = [IO.Path]::GetFullPath($Record.path)
    foreach ($forbidden in @($Manifest.output_directory, $Manifest.frame_experiment.directory)) {
        $root = [IO.Path]::GetFullPath($forbidden).TrimEnd([char]'\', [char]'/')
        if ($inspectionPath.StartsWith($root + [IO.Path]::DirectorySeparatorChar,
                [StringComparison]::OrdinalIgnoreCase)) {
            throw "No-sample inspection evidence must live outside $root."
        }
    }
    $inspection = Get-Content -LiteralPath $Record.path -Raw | ConvertFrom-Json
    if ([string](Get-P4JsonProperty $inspection 'schema' 'no_sample_inspection') -cne
        $script:P4NoSampleInspectionSchema) {
        throw 'No-sample inspection evidence has the wrong schema.'
    }
    $root = Read-P4Bounds $script:P4InspectionRootBounds 'accepted root bounds'
    foreach ($role in @('baseline', 'candidate')) {
        $observed = Get-P4JsonProperty $inspection $role 'no_sample_inspection'
        $context = "no_sample_inspection.$role"
        if ([string](Get-P4JsonProperty $observed 'schema' $context) -cne $script:P4NoSampleInspectionSchema -or
            [string](Get-P4JsonProperty $observed 'role' $context) -cne $role) {
            throw "No-sample inspection $role record has the wrong schema or role."
        }
        $source = $Manifest.roles[$role]
        if ([string](Get-P4JsonProperty $observed 'apk_sha256' $context) -cne
            [string]$source.artifacts.app_release_like.sha256) {
            throw "No-sample inspection $role did not inspect the measured release-like APK."
        }
        if ([string](Get-P4JsonProperty $observed 'embedded_source_commit' $context) -cne
            [string]$source.build_commit) {
            throw "No-sample inspection $role was produced from another build."
        }
        if ([string](Get-P4JsonProperty $observed 'rotation' $context) -cne [string]$script:P4InspectionRotation) {
            throw "No-sample inspection $role was not captured at the pinned rotation."
        }
        if ([string](Get-P4JsonProperty $observed 'geometry_id' $context) -cne $script:P4GeometryId) {
            throw "No-sample inspection $role declares the wrong geometry contract."
        }
        if ([string](Get-P4JsonProperty $observed 'root_bounds' $context) -cne $script:P4InspectionRootBounds) {
            throw "No-sample inspection $role root bounds are not the accepted viewport."
        }
        foreach ($family in @('canvas16', 'canvas256')) {
            $boundsKey = "${role}_${family}_bounds"
            if (-not $Manifest.device.Contains($boundsKey)) { throw "The manifest omits device.$boundsKey." }
            $declared = [string]$Manifest.device[$boundsKey]
            $actual = [string](Get-P4JsonProperty $observed "${family}_bounds" $context)
            if ($actual -cne $declared) {
                throw "No-sample inspection $role $family bounds differ from the manifest: $actual vs $declared."
            }
            $bounds = Read-P4Bounds $actual "$role.$family"
            if ($bounds.left -lt $root.left -or $bounds.top -lt $root.top -or
                $bounds.right -gt $root.right -or $bounds.bottom -gt $root.bottom) {
                throw "No-sample inspection $role $family bounds fall outside the root viewport."
            }
        }
        # A name -> sha map, so each retained dump is attributable to the UI capture it came from.
        $dumps = Get-P4JsonProperty $observed 'ui_dump_sha256s' $context
        if ($dumps -isnot [System.Management.Automation.PSCustomObject]) {
            throw "No-sample inspection $role must record ui_dump_sha256s as a name-to-sha object."
        }
        $names = @($dumps.PSObject.Properties)
        if ($names.Count -lt 2) {
            throw "No-sample inspection $role must retain both UI dump identities."
        }
        foreach ($name in $names) {
            if ([string]::IsNullOrWhiteSpace($name.Name)) {
                throw "No-sample inspection $role names a UI dump with an empty key."
            }
            if ([string]$name.Value -cnotmatch '^[0-9a-f]{64}$') {
                throw "No-sample inspection $role UI dump identity is invalid: $($name.Name)"
            }
        }
        Read-P4Utc ([string](Get-P4JsonProperty $observed 'captured_utc' $context)) "$context.captured_utc" | Out-Null
    }
}

function Assert-P4CollectorContracts {
    param([System.Collections.IDictionary]$Manifest)

    $observed = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($record in @($Manifest.collector_contracts)) {
        $scope = [string]$record.scope
        if (-not $observed.Add($scope)) { throw "Duplicate collector contract scope: $scope" }
    }
    $expected = [Collections.Generic.HashSet[string]]::new(
        [string[]]$script:P4CollectorContractScopes, [StringComparer]::Ordinal)
    Assert-P4InventorySetEquals $expected $observed 'collector contract'
    if (@($Manifest.correctness).Count -lt 1) { throw 'At least one correctness record is required.' }
}

function Get-P4ArtifactDexoptPackage {
    param([System.Collections.IDictionary]$Artifact)

    $test = [string]$Artifact.test_package
    if ($test -ceq 'none') { return [string]$Artifact.target_package }
    return $test
}

function Assert-P4LiveDeviceAdmission {
    <#
        The only adb use in preflight. Raw dumps land beside the reserved output directory, which must
        not pre-exist, so nothing that a prior attempt recorded can be overwritten.
    #>
    param([System.Collections.IDictionary]$Manifest, [string]$RepositoryRoot)

    $directory = [IO.Path]::GetFullPath($Manifest.output_directory) + '-preflight'
    if (Test-Path -LiteralPath $directory) { throw 'Preflight device evidence already exists; replacement is prohibited.' }
    New-Item -ItemType Directory -Path $directory -ErrorAction Stop | Out-Null
    $adb = $Manifest.tools.adb.path
    $serial = [string]$Manifest.device.serial
    $state = Get-P4LiveDeviceState -AdbPath $adb -Serial $serial -Directory $directory -Stage 'preflight' `
        -RepositoryRoot $RepositoryRoot
    Assert-P4DeviceStateMatches -Expected $Manifest.device -Observed $state -Context 'preflight'

    $statuses = @{}
    foreach ($role in @('baseline', 'candidate')) {
        foreach ($kind in $script:P4ArtifactContract.Keys) {
            $artifact = $Manifest.roles[$role].artifacts[$kind]
            $package = Get-P4ArtifactDexoptPackage $artifact
            if (-not $statuses.ContainsKey($package)) {
                $statuses[$package] = Get-P4PackageDexoptStatus -AdbPath $adb -Serial $serial `
                    -Directory $directory -Package $package -Stage 'preflight' -RepositoryRoot $RepositoryRoot
            }
            if ([string]$artifact.observed_dexopt -cne $statuses[$package]) {
                throw ("Recorded dexopt state for $role.$kind ($package) is not the live state: " +
                    "$($artifact.observed_dexopt) vs $($statuses[$package]).")
            }
        }
    }
}

function Get-P4ProductionTreeHash {
    param([string]$RepositoryRoot, [ValidatePattern('^[0-9a-f]{40}$')][string]$Commit)
    $entries = @(& git -C $RepositoryRoot ls-tree -r --full-tree $Commit)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read production Git tree.' }
    # Only docs and explicitly test-owned sources are excluded. Build/config/runtime inputs remain.
    $production = @($entries | Where-Object {
        $path = ($_ -split "`t", 2)[1]
        $path -notmatch '^docs/' -and $path -notmatch '(^|/)src/(test|androidTest|testFixtures)/' -and
            $path -notmatch '(^|/)(AGENTS\.md|README\.md|CLAUDE\.md)$'
    })
    if ($production.Count -eq 0) { throw 'Production tree cannot be empty.' }
    Get-Sha256Hex ([Text.UTF8Encoding]::new($false).GetBytes(($production -join "`n") + "`n"))
}

function Assert-P4ApkIdentity {
    param([System.Collections.IDictionary]$Artifact, [string]$BuildCommit, [AllowNull()]$Profile)
    Assert-P4FileRecord $Artifact 'APK'
    $zip = [IO.Compression.ZipFile]::OpenRead($Artifact.path)
    try {
        $entry = $zip.GetEntry('META-INF/version-control-info.textproto')
        if ($null -eq $entry) { throw 'APK lacks required embedded source revision.' }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $matches = [regex]::Matches($text, '(?m)^\s*revision:\s*"([0-9a-f]{40})"\s*$')
        if ($matches.Count -ne 1 -or $matches[0].Groups[1].Value -cne $BuildCommit) {
            throw 'APK embedded revision does not match actual measurement build.'
        }
        if ($null -ne $Profile) {
            foreach ($kind in @('prof', 'profm')) {
                $entry = $zip.GetEntry("assets/dexopt/baseline.$kind")
                if ($null -eq $entry) { throw "Missing packaged profile: $kind" }
                $stream = $entry.Open()
                $sha = [Security.Cryptography.SHA256]::Create()
                try { $hash = [Convert]::ToHexString($sha.ComputeHash($stream)).ToLowerInvariant() }
                finally { $sha.Dispose(); $stream.Dispose() }
                if ($hash -cne $Profile["packaged_${kind}_sha256"]) { throw "Packaged profile drift: $kind" }
            }
        }
    } finally { $zip.Dispose() }
}

function Assert-P4RoleSource {
    param([System.Collections.IDictionary]$Role, [string]$Name, [string]$Aapt2Path)
    Assert-P4RequiredKeys $Role @('worktree', 'production_commit', 'build_commit', 'production_tree_sha256',
        'measurement_files', 'measurement_sha256', 'compiled_files', 'compiled_sha256', 'artifacts', 'profile') $Name
    foreach ($key in @('production_commit', 'build_commit')) {
        if ($Role[$key] -cnotmatch '^[0-9a-f]{40}$') { throw "Invalid $Name $key." }
    }
    $head = (& git -C $Role.worktree rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $head -cne $Role.build_commit) { throw "Wrong build HEAD: $Name" }
    $status = @(& git -C $Role.worktree status --porcelain --untracked-files=normal)
    if ($LASTEXITCODE -ne 0 -or $status.Count -ne 0) { throw "Unclean build worktree: $Name" }
    if ($Name -eq 'baseline' -and ($Role.production_commit -cne $script:P4BaselineProduction -or
        $Role.build_commit -ceq $Role.production_commit)) { throw 'Baseline must be an immutable test overlay atop the accepted baseline production commit.' }
    $sourceTree = Get-P4ProductionTreeHash $Role.worktree $Role.production_commit
    $buildTree = Get-P4ProductionTreeHash $Role.worktree $Role.build_commit
    if ($sourceTree -cne $buildTree -or $buildTree -cne $Role.production_tree_sha256) {
        throw "Production tree mismatch: $Name"
    }
    foreach ($prefix in @('measurement', 'compiled')) {
        if ((Get-P4FileInventoryHash $Role["${prefix}_files"]) -cne $Role["${prefix}_sha256"]) {
            throw "$Name $prefix aggregate mismatch."
        }
    }
    Assert-P4MeasurementInventory $Role $Name
    Assert-P4CompiledInventory $Role $Name
    foreach ($kind in @('app_debug', 'test_debug', 'app_release_like', 'publication_test')) {
        if (-not $Role.artifacts.Contains($kind)) { throw "Missing $Name APK: $kind" }
        Assert-P4FileRecord $Role.artifacts[$kind] "$Name.$kind"
        Assert-P4RequiredKeys $Role.artifacts[$kind] @('variant', 'target_package', 'test_package',
            'embedded_revision', 'requested_dexopt', 'observed_dexopt') "$Name.$kind"
        if ($Role.artifacts[$kind].embedded_revision -cne $Role.build_commit) { throw "APK source mismatch: $Name.$kind" }
        $packaged = if ($kind -eq 'app_release_like') { $Role.profile } else { $null }
        Assert-P4ApkIdentity $Role.artifacts[$kind] $Role.build_commit $packaged
        Assert-P4ApkPackaging $Aapt2Path $Role.artifacts[$kind] $kind "$Name.$kind"
    }
    Assert-P4RequiredKeys $Role.profile @('source', 'acceptance', 'pair', 'canonical', 'packaged_prof_sha256',
        'packaged_profm_sha256', 'generation_commit', 'generation_app_sha256', 'generation_test_sha256') "$Name.profile"
    foreach ($kind in @('source', 'acceptance', 'pair', 'canonical')) {
        Assert-P4FileRecord $Role.profile[$kind] "$Name.profile.$kind"
    }
    $profile = $Role.profile
    Read-BaselineProfileAcceptanceEvidence -AcceptanceManifestPath $profile.acceptance.path `
        -ExpectedAcceptanceManifestSha256 $profile.acceptance.sha256 `
        -ExpectedSourceRevision $profile.generation_commit -ExpectedAppApkSha256 $profile.generation_app_sha256 `
        -ExpectedTestApkSha256 $profile.generation_test_sha256 -ExpectedPairManifestSha256 $profile.pair.sha256 `
        -ExpectedCanonicalSha256 $profile.canonical.sha256 | Out-Null
    Assert-P4ProfileSourceBinding $profile "$Name.profile"
}

function Assert-P4ManifestContract {
    param([System.Collections.IDictionary]$Manifest)
    Assert-P4RequiredKeys $Manifest @('schema', 'protocol', 'created_utc', 'experiment_id', 'output_directory',
        'roles', 'tools', 'toolchain', 'device', 'frame_experiment', 'correctness', 'collector_contracts',
        'slots') 'manifest'
    Assert-P4RequiredValue $Manifest 'manifest'
    if ($Manifest.schema -cne $script:P4ManifestSchema -or $Manifest.protocol.id -cne $script:P4ProtocolId) {
        throw 'Wrong P4 manifest/protocol identity.'
    }
    if ($Manifest.experiment_id -cnotmatch '^[a-z0-9][a-z0-9-]{2,63}$') { throw 'Invalid experiment ID.' }
    Read-P4Utc ([string]$Manifest.created_utc) 'manifest.created_utc' | Out-Null
    Assert-P4RequiredKeys $Manifest.roles @('baseline', 'candidate') 'roles'
    Assert-P4RequiredKeys $Manifest.toolchain @('jdk', 'jvm', 'gradle', 'agp', 'kotlin', 'compose',
        'android_build_tools', 'android_sdk', 'os', 'wrapper', 'catalog', 'locks', 'verification_metadata') 'toolchain'
    Assert-P4RequiredKeys $Manifest.tools @('runner', 'analyzer', 'host_init', 'wrapper', 'validator',
        'bounded_native', 'frame_collector', 'frame_validator', 'adb', 'aapt2') 'tools'
    Assert-P4RequiredKeys $Manifest.device @('serial', 'profile', 'manufacturer', 'model', 'product', 'device',
        'api', 'fingerprint', 'security_patch', 'display_mode', 'width', 'height', 'refresh_rate', 'rotation',
        'thermal', 'power_save', 'interactive', 'usb_power', 'battery', 'locale', 'user_rotation', 'stay_awake',
        'asset_preservation', 'baseline_canvas16_bounds', 'baseline_canvas256_bounds', 'candidate_canvas16_bounds',
        'candidate_canvas256_bounds', 'initial_viewport', 'no_sample_inspection') 'device'
    if ($Manifest.device.profile -cne 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36' -or
        $Manifest.device.model -cne 'iPlay80miniPro' -or [int]$Manifest.device.api -ne 36 -or
        $Manifest.device.initial_viewport -cne $script:P4GeometryId) { throw 'Wrong device/geometry contract.' }
    foreach ($key in @('power_save', 'interactive', 'usb_power')) {
        if ([string]$Manifest.device[$key] -cne $script:P4DeviceStateRequiredValues[$key]) {
            throw "The manifest records an unusable measurement device state at device.$key."
        }
    }
    foreach ($role in @('baseline', 'candidate')) {
        foreach ($family in @('canvas16', 'canvas256')) {
            Read-P4Bounds ([string]$Manifest.device["${role}_${family}_bounds"]) "device.${role}_${family}_bounds" | Out-Null
        }
    }
    Assert-P4RequiredKeys $Manifest.frame_experiment @('directory', 'hypothesis', 'expected_affected_cost',
        'correctness_risk', 'stop_conditions') 'frame_experiment'
    $frameDirectory = [IO.Path]::GetFullPath($Manifest.frame_experiment.directory)
    $outputRoot = [IO.Path]::GetFullPath($Manifest.output_directory).TrimEnd([char]'\', [char]'/')
    if (-not $frameDirectory.StartsWith($outputRoot + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The frame experiment directory must live under the reserved experiment output directory.'
    }
    foreach ($key in @('hypothesis', 'expected_affected_cost', 'correctness_risk', 'stop_conditions')) {
        if ([string]::IsNullOrWhiteSpace([string]$Manifest.frame_experiment[$key])) {
            throw "The frame experiment must state $key before any frame slot is reserved."
        }
    }
    Assert-P4CollectorContracts $Manifest
    $expected = @(Get-P4SlotCatalog)
    if (@($Manifest.slots).Count -ne $expected.Count) { throw 'Wrong slot population.' }
    for ($i = 0; $i -lt $expected.Count; $i++) {
        foreach ($key in $expected[$i].Keys) {
            if ([string]$Manifest.slots[$i][$key] -cne [string]$expected[$i][$key]) {
                throw "Slot order/budget drift at $i / $key."
            }
        }
    }
}

# The 2026-09-13 handoff barrier (Assert-P4CollectionImplementationReady) was retired on 2026-09-16 with
# hide's decision, after the device collectors, frame analyzer, preflight and slot boundary passed two
# independent read-only reviews and their no-device validators.

function Assert-P4ManifestArtifacts {
    <#
        `reservation` is the one-time gate that consumes the device and the Issue agreement.
        `slot` re-proves every offline fact before each slot without touching the device again and
        without creating the reserved `-preflight` directory a second time.
    #>
    param(
        [System.Collections.IDictionary]$Manifest,
        [string]$RepositoryRoot,
        [ValidateSet('reservation', 'slot')][string]$Stage = 'reservation'
    )
    Assert-P4ManifestContract $Manifest
    Assert-P4FileRecord $Manifest.protocol 'protocol'
    if ((Get-FileSha256 (Join-Path $RepositoryRoot 'docs/quality/P4_INDEXED_CUTOVER_PROTOCOL.md')) -cne
        $Manifest.protocol.sha256) { throw 'Accepted canonical protocol bytes drifted.' }
    Assert-P4GitLineage $Manifest
    foreach ($key in $Manifest.tools.Keys) { Assert-P4FileRecord $Manifest.tools[$key] "tools.$key" }
    foreach ($role in @('baseline', 'candidate')) {
        Assert-P4RoleSource $Manifest.roles[$role] $role $Manifest.tools.aapt2.path
    }
    foreach ($key in @('wrapper', 'catalog', 'verification_metadata')) {
        Assert-P4FileRecord $Manifest.toolchain[$key] "toolchain.$key"
    }
    foreach ($file in $Manifest.toolchain.locks) { Assert-P4FileRecord $file 'dependency lock' }
    Assert-P4FileRecord $Manifest.device.asset_preservation 'device.asset_preservation'
    Assert-P4DevicePreservation $Manifest.device.asset_preservation ([string]$Manifest.device.serial) `
        ([string]$Manifest.created_utc)
    Assert-P4FileRecord $Manifest.device.no_sample_inspection 'device.no_sample_inspection'
    Assert-P4NoSampleInspection $Manifest.device.no_sample_inspection $Manifest
    foreach ($record in @($Manifest.correctness) + @($Manifest.collector_contracts)) {
        Assert-P4RequiredKeys $record @('source_commit', 'scope', 'command', 'exit_code', 'log') 'verification'
        if ([int]$record.exit_code -ne 0 -or $record.source_commit -cne $Manifest.roles.candidate.build_commit) {
            throw 'Correctness or collector contract evidence does not match candidate.'
        }
        Assert-P4FileRecord $record.log 'verification.log'
    }
    if ($Stage -ceq 'reservation') {
        # The body is judged inside jq; pwsh receives only ASCII, so the result does not depend on the console encoding.
        $agreementFilter = '[.state, (.body | contains("' + $script:P4ProtocolId + '"))] | @tsv'
        $agreement = @(& gh issue view $script:P4AgreementIssue --repo hideyukiMORI/NENE-PIXEL --json body,state --jq $agreementFilter) -join "`n"
        if ($LASTEXITCODE -ne 0 -or $agreement -cne "OPEN`ttrue") {
            throw 'Issue/protocol agreement is missing.'
        }
        Assert-P4LiveDeviceAdmission $Manifest $RepositoryRoot
    }
}

function New-P4ExperimentReservation {
    param([string]$ManifestPath, [string]$RepositoryRoot)
    $bytes = [IO.File]::ReadAllBytes($ManifestPath)
    $Manifest = [Text.UTF8Encoding]::new($false, $true).GetString($bytes) | ConvertFrom-Json -AsHashtable
    Assert-P4ManifestArtifacts $Manifest $RepositoryRoot -Stage reservation
    $root = [IO.Path]::GetFullPath($Manifest.output_directory)
    if (Test-Path -LiteralPath $root) { throw 'Experiment output already exists; attempt replacement is prohibited.' }
    New-Item -ItemType Directory -Path $root -ErrorAction Stop | Out-Null
    $stream = [IO.File]::Open((Join-Path $root 'preflight.json'), [IO.FileMode]::CreateNew,
        [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    return $root
}

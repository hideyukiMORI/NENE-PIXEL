[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1')
. (Join-Path $PSScriptRoot 'measurements/analyze-p4-indexed-slot.ps1') -ManifestPath fixture -SlotId fixture -OutputDirectory fixture

function Assert-P4TestRejects {
    param([scriptblock]$Action, [string]$Case)
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw "Negative fixture was accepted: $Case" }
}

function New-P4SyntheticHostRows {
    param([string]$Runner, [string]$Role)
    $schema = switch ($Runner) { 'project' { 'project-format' }; 'recovery' { 'recovery-record' }; 'legacy' { 'legacy-import' } }
    $lines = [Collections.Generic.List[string]]::new()
    foreach ($line in @("schema,nene-pixel-p4-$schema-host-v1", "role,$Role", 'java_version,synthetic',
        'java_vm,synthetic', 'os,synthetic', 'warmups,5', 'samples_per_group,20', 'group,sample,latency_nanos')) { $lines.Add($line) }
    $groups = @(Get-P4HostGroups $Runner $Role)
    foreach ($group in $groups) { foreach ($i in 0..19) { $lines.Add("$group,$i,$(1001 + $i)") } }
    foreach ($group in $groups) { $lines.Add("summary_min,$group,1001"); $lines.Add("summary_max,$group,1020") }
    return $lines.ToArray()
}

# Protocol v7 removed Lane 3 from Issue #106's fixed order and acceptance, so the reserved catalog is
# the 29 slots of the host, command, memory and publication lanes and carries no frame slot. The four
# frame slots stay defined, unreserved, in the retained Lane 3 catalog that Issue #120 owns.
$catalog = @(Get-P4SlotCatalog)
if ($catalog.Count -ne 29 -or $catalog[0].id -cne 'host-project-baseline' -or
    $catalog[28].id -cne 'publication-candidate' -or @($catalog | Where-Object lane -eq 'frame').Count -ne 0) {
    throw 'Fixed 29-slot operation catalog is incorrect.'
}
$frameCatalog = @(Get-P4FrameSlotCatalog)
if ($frameCatalog.Count -ne 4 -or $frameCatalog[0].id -cne 'frame-1-baseline-diagnostic' -or
    $frameCatalog[3].id -cne 'frame-4-baseline-decision' -or
    @($frameCatalog | Where-Object lane -cne 'frame').Count -ne 0) {
    throw 'Retained Lane 3 frame catalog is incorrect.'
}
# Protocol v7 finite budgets: 5 host slots at 180 s, 2 command at 600 s (Lane 1), and 20 memory plus
# 2 publication at 300 s. The retained Lane 3 wrapper bound is still derived from the operation count,
# 2 diagnostic at 750 s and 2 decision at 1950 s, and is checked against the retained catalog only.
if (@($catalog | Where-Object lane -eq 'memory').Count -ne 20 -or
    @($catalog | Where-Object { $_.timeout_seconds -eq 180 }).Count -ne 5 -or
    @($catalog | Where-Object { $_.timeout_seconds -eq 600 }).Count -ne 2 -or
    @($catalog | Where-Object { $_.timeout_seconds -eq 300 }).Count -ne 22 -or
    @($frameCatalog | Where-Object { $_.timeout_seconds -eq 750 }).Count -ne 2 -or
    @($frameCatalog | Where-Object { $_.timeout_seconds -eq 1950 }).Count -ne 2 -or
    (Get-P4FrameWrapperBound -Warmups 5 -Samples 10) -ne 750 -or
    (Get-P4FrameWrapperBound -Warmups 5 -Samples 50) -ne 1950) { throw 'Finite lane budget drift.' }
foreach ($runner in @('project', 'recovery', 'legacy')) {
    $roles = if ($runner -eq 'legacy') { @('candidate') } else { @('baseline', 'candidate') }
    foreach ($role in $roles) {
        $rows = @(New-P4SyntheticHostRows $runner $role)
        $result = Test-P4HostCapture $rows $runner $role
        if ($result.verdict -cne 'valid-descriptive') { throw 'Host observations cannot yield a product performance pass.' }
        $incomplete = $rows[0..($rows.Count - 2)]
        Assert-P4TestRejects { Test-P4HostCapture $incomplete $runner $role } 'truncated host'
        $duplicate = [string[]]$rows.Clone(); $duplicate[9] = $duplicate[8]
        Assert-P4TestRejects { Test-P4HostCapture $duplicate $runner $role } 'duplicate sample'
        $reordered = [string[]]$rows.Clone(); $reordered[8] = $rows[9]; $reordered[9] = $rows[8]
        Assert-P4TestRejects { Test-P4HostCapture $reordered $runner $role } 'reordered sample'
        $anomaly = [string[]]$rows.Clone(); $anomaly[8] = $anomaly[8] -replace ',1001$', ',1000000001'
        Assert-P4TestRejects { Test-P4HostCapture $anomaly $runner $role } 'completed gross anomaly'
        $negative = [string[]]$rows.Clone(); $negative[8] = $negative[8] -replace ',1001$', ',-1'
        Assert-P4TestRejects { Test-P4HostCapture $negative $runner $role } 'negative duration'
        $nan = [string[]]$rows.Clone(); $nan[8] = $nan[8] -replace ',1001$', ',NaN'
        Assert-P4TestRejects { Test-P4HostCapture $nan $runner $role } 'nonfinite duration'
        $wrongSummary = [string[]]$rows.Clone(); $wrongSummary[-1] = $wrongSummary[-1] -replace ',1020$', ',1019'
        Assert-P4TestRejects { Test-P4HostCapture $wrongSummary $runner $role } 'fabricated summary'
        $wrongRole = [string[]]$rows.Clone(); $wrongRole[1] = 'role,other'
        Assert-P4TestRejects { Test-P4HostCapture $wrongRole $runner $role } 'role mismatch'
        $extra = $rows + 'unused,1,1000'
        Assert-P4TestRejects { Test-P4HostCapture $extra $runner $role } 'extra row'
    }
}
Assert-P4TestRejects { Get-P4HostGroups legacy baseline } 'fabricated baseline import'
Assert-P4TestRejects { Assert-P4RequiredValue @{ valid = 'yes'; nested = @('UNSET_PREFLIGHT') } root } 'nested unset identity'
Assert-P4TestRejects { Assert-P4ManifestContract @{ schema = $script:P4ManifestSchema } } 'missing manifest fields'
Assert-P4TestRejects { Assert-P4RequiredValue @{} empty } 'empty inventory'
Assert-P4TestRejects { Read-P4PositiveInt64 '9223372036854775808' } 'overflow'

$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('nene-p4-contract-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$source = Join-Path $testRoot 'immutable.txt'
Write-NewInvocationFile $source 'source'
$record = [ordered]@{ relative_path = 'fixture/immutable.txt'; path = $source; byte_count = 6; sha256 = Get-FileSha256 $source }
$hash = Get-P4FileInventoryHash @($record)
if ($hash -cnotmatch '^[0-9a-f]{64}$') { throw 'Invalid inventory digest.' }
Assert-P4TestRejects { Write-NewInvocationFile $source 'replacement' } 'exclusive output collision'
if ([IO.File]::ReadAllText($source) -cne 'source') { throw 'Collision damaged existing evidence.' }
Assert-P4TestRejects { Get-P4FileInventoryHash @($record, $record) } 'duplicate artifact identity'
$escaping = [ordered]@{}; foreach ($key in $record.Keys) { $escaping[$key] = $record[$key] }; $escaping.relative_path = '../escape'
Assert-P4TestRejects { Get-P4FileInventoryHash @($escaping) } 'escaping aggregate path'
[IO.File]::WriteAllText($source, 'edited')
Assert-P4TestRejects { Assert-P4FileRecord $record fixture } 'same-length artifact tamper'

function New-P4MutableCopy {
    param([System.Collections.IDictionary]$Source)
    $copy = [ordered]@{}
    foreach ($key in $Source.Keys) { $copy[$key] = $Source[$key] }
    return $copy
}

# ---------------------------------------------------------------------------
# S1. Device state is read from the real read-only device dumps, never invented.
# ---------------------------------------------------------------------------
$fixtureRoot = [IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '../../build/reports/issue-106/device-fixtures-20260915'))
if (-not (Test-Path -LiteralPath $fixtureRoot -PathType Container)) {
    throw "The read-only device fixtures are required for the device-state contract: $fixtureRoot"
}
function Get-P4FixtureLines { param([string]$Name) @(Get-Content -LiteralPath (Join-Path $fixtureRoot $Name)) }
function Get-P4FixtureText { param([string]$Name) (Get-Content -LiteralPath (Join-Path $fixtureRoot $Name) -Raw) }

$fixtureDumps = @{
    Getprop = Get-P4FixtureLines 'getprop.txt'
    Display = Get-P4FixtureLines 'dumpsys_display.txt'
    Window = Get-P4FixtureLines 'dumpsys_window.txt'
    Thermal = Get-P4FixtureLines 'dumpsys_thermalservice.txt'
    Power = Get-P4FixtureLines 'dumpsys_power.txt'
    Battery = Get-P4FixtureLines 'dumpsys_battery.txt'
    LowPower = Get-P4FixtureText 'settings_get_global_low_power.txt'
    Locales = Get-P4FixtureText 'settings_get_system_system_locales.txt'
    UserRotation = Get-P4FixtureText 'settings_get_system_user_rotation.txt'
    StayAwake = Get-P4FixtureText 'settings_get_global_stay_on_while_plugged_in.txt'
}
$deviceState = ConvertFrom-P4DeviceDumps @fixtureDumps
$expectedDevice = [ordered]@{
    serial = 'T830128GB26321131293'; manufacturer = 'ALLDOCUBE'; model = 'iPlay80miniPro'
    product = 'iPlay80miniPro'; device = 'T830'; api = 36
    fingerprint = 'ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94111:user/release-keys'
    security_patch = '2026-08-05'; display_mode = 1; width = 1200; height = 1920; refresh_rate = '90.0'
    rotation = 0; thermal = 0; power_save = '0'; interactive = 'true'; usb_power = 'true'; battery = 72
    locale = 'ja-JP'; user_rotation = 0; stay_awake = '2'
}
foreach ($key in $expectedDevice.Keys) {
    if ([string]$deviceState[$key] -cne [string]$expectedDevice[$key]) {
        throw "Device fixture parsed $key as $($deviceState[$key]); expected $($expectedDevice[$key])."
    }
}
Assert-P4DeviceStateMatches $expectedDevice $deviceState 'fixture'
foreach ($case in @(
        @{ key = 'fingerprint'; value = 'OTHER/x/y:16/z/1:user/release-keys' },
        @{ key = 'serial'; value = 'T830000000000000000' },
        @{ key = 'display_mode'; value = 2 },
        @{ key = 'refresh_rate'; value = '60.0' },
        @{ key = 'locale'; value = 'en-US' },
        @{ key = 'width'; value = 1080 })) {
    $drifted = New-P4MutableCopy $deviceState
    $drifted[$case.key] = $case.value
    Assert-P4TestRejects { Assert-P4DeviceStateMatches $expectedDevice $drifted 'fixture' } "device drift $($case.key)"
}
foreach ($case in @(
        @{ key = 'thermal'; value = 2 }, @{ key = 'battery'; value = 19 },
        @{ key = 'power_save'; value = '1' }, @{ key = 'interactive'; value = 'false' },
        @{ key = 'usb_power'; value = 'false' })) {
    $unusable = New-P4MutableCopy $deviceState
    $unusable[$case.key] = $case.value
    $claimed = New-P4MutableCopy $expectedDevice
    $claimed[$case.key] = $case.value
    Assert-P4TestRejects { Assert-P4DeviceStateMatches $claimed $unusable 'fixture' } "unusable device $($case.key)"
}
# A rotation that only the collectors pin is recorded, never gated.
$rotated = New-P4MutableCopy $deviceState
$rotated.rotation = 1
Assert-P4DeviceStateMatches $expectedDevice $rotated 'fixture' | Out-Null

$mutatedDumps = @{}
foreach ($key in $fixtureDumps.Keys) { $mutatedDumps[$key] = $fixtureDumps[$key] }
$mutatedDumps.Thermal = @($fixtureDumps.Thermal | Where-Object { $_ -notmatch '^Thermal Status:' })
Assert-P4TestRejects { ConvertFrom-P4DeviceDumps @mutatedDumps } 'absent thermal status'
$mutatedDumps.Thermal = $fixtureDumps.Thermal
$mutatedDumps.Display = @($fixtureDumps.Display) + '    mActiveModeId=2'
Assert-P4TestRejects { ConvertFrom-P4DeviceDumps @mutatedDumps } 'ambiguous display mode'
$mutatedDumps.Display = $fixtureDumps.Display
$mutatedDumps.Window = @($fixtureDumps.Window -replace '^  DisplayFrames w=1200 h=1920 r=0$', '  DisplayFrames w=1200 h=1920 r=1')
Assert-P4TestRejects { ConvertFrom-P4DeviceDumps @mutatedDumps } 'rotation disagreement'
$mutatedDumps.Window = $fixtureDumps.Window
$mutatedDumps.UserRotation = '1'
Assert-P4TestRejects { ConvertFrom-P4DeviceDumps @mutatedDumps } 'user rotation disagreement'
$mutatedDumps.UserRotation = $fixtureDumps.UserRotation
$mutatedDumps.Locales = 'null'
Assert-P4TestRejects { ConvertFrom-P4DeviceDumps @mutatedDumps } 'absent locale'
$mutatedDumps.Locales = $fixtureDumps.Locales
$mutatedDumps.LowPower = ''
Assert-P4TestRejects { ConvertFrom-P4DeviceDumps @mutatedDumps } 'absent low power setting'

$packageDump = Get-P4FixtureText 'dumpsys_package_io_github_hideyukimori_nenepixel.txt'
if ((Get-P4PackageDexoptStatusFromText $packageDump 'io.github.hideyukimori.nenepixel') -cne 'verify') {
    throw 'The fixture dexopt status was misread.'
}
if ((Get-P4PackageDexoptStatusFromText $packageDump 'io.github.hideyukimori.nenepixel.test') -cne 'not-installed') {
    throw 'An absent package must read as not-installed.'
}
Assert-P4TestRejects {
    Get-P4PackageDexoptStatusFromText ($packageDump -replace '(?m)^Dexopt state:\s*$', 'Other state:') `
        'io.github.hideyukimori.nenepixel'
} 'package dump without dexopt state'

# ---------------------------------------------------------------------------
# S7. Inventories are bound to the role clone's Git blobs and its real file system.
# ---------------------------------------------------------------------------
$candidateWorktree = 'C:/n106-candidate-build'
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (Test-Path -LiteralPath $candidateWorktree -PathType Container) {
    # The candidate measurement build commit is whatever the clean clone is checked out at; a literal
    # here could never name the commit that contains this validator.
    $candidateBuild = (& git -C $candidateWorktree rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $candidateBuild -cnotmatch '^[0-9a-f]{40}$') { throw 'Unable to read the candidate clone HEAD.' }
    if (@(& git -C $candidateWorktree status --porcelain).Count -ne 0) { throw 'The candidate clone must be clean for real-data inventory cases.' }
    $expectedPaths = Get-P4ExpectedMeasurementPaths $candidateWorktree $candidateBuild 'candidate'
    if ($expectedPaths.Count -lt 20) { throw 'The fixed measurement pattern set collapsed.' }
    foreach ($required in @($script:P4SharedHostEvidenceSources) + @($script:P4CandidateHostEvidenceSources)) {
        if (-not $expectedPaths.Contains($required)) { throw "The expected set lost a host evidence source: $required" }
    }
    $baselinePaths = Get-P4ExpectedMeasurementPaths 'C:/n106-baseline-build' `
        '0b605481ad97ee3726864e556e6519f3a862271f' 'baseline'
    foreach ($candidateOnly in @($script:P4CandidateHostEvidenceSources)) {
        if ($baselinePaths.Contains($candidateOnly)) { throw 'The baseline must not claim the candidate-only lane.' }
    }
    $role = [ordered]@{
        worktree = $candidateWorktree; build_commit = $candidateBuild
        measurement_files = @(@($expectedPaths) | Sort-Object | ForEach-Object {
                [ordered]@{ relative_path = $_; path = (Join-Path $candidateWorktree $_) } })
    }
    Assert-P4MeasurementInventory $role 'candidate'
    $short = New-P4MutableCopy $role
    $short.measurement_files = @($role.measurement_files | Select-Object -Skip 1)
    Assert-P4TestRejects { Assert-P4MeasurementInventory $short 'candidate' } 'inventory set is short'
    $extra = New-P4MutableCopy $role
    $extra.measurement_files = @($role.measurement_files) + @([ordered]@{
            relative_path = 'settings.gradle.kts'; path = (Join-Path $candidateWorktree 'settings.gradle.kts') })
    Assert-P4TestRejects { Assert-P4MeasurementInventory $extra 'candidate' } 'inventory set is wide'
    $escaped = New-P4MutableCopy $role
    $escaped.measurement_files = @($role.measurement_files | Select-Object -Skip 1) + @([ordered]@{
            relative_path = @($expectedPaths)[0]; path = (Join-Path ([IO.Path]::GetTempPath()) 'escape.ps1') })
    Assert-P4TestRejects { Assert-P4MeasurementInventory $escaped 'candidate' } 'inventory path escape'
    Assert-P4TrackedBlob $candidateWorktree $candidateBuild 'docs/quality/measurements/m2-package-dexopt.ps1' `
        (Join-Path $candidateWorktree 'docs/quality/measurements/m2-package-dexopt.ps1') 'positive'
    Assert-P4TestRejects {
        Assert-P4TrackedBlob $candidateWorktree $candidateBuild 'docs/quality/measurements/m2-package-dexopt.ps1' `
            (Join-Path $candidateWorktree 'docs/quality/measurements/measure-m2-frame.ps1') 'negative'
    } 'tracked blob mismatch'
    Assert-P4TestRejects {
        Assert-P4TrackedBlob $candidateWorktree $candidateBuild 'docs/quality/measurements/absent.ps1' `
            (Join-Path $candidateWorktree 'docs/quality/measurements/m2-package-dexopt.ps1') 'negative'
    } 'untracked measurement source'

    if (-not (Test-P4GitAncestor $candidateWorktree $script:P4BaselineProduction $candidateBuild)) {
        throw 'The candidate build must descend from the accepted baseline production commit.'
    }
    if (Test-P4GitAncestor $candidateWorktree $candidateBuild $script:P4BaselineProduction) {
        throw 'Ancestry must not be symmetric.'
    }
    $lineage = [ordered]@{ roles = [ordered]@{
            baseline = [ordered]@{ worktree = 'C:/n106-baseline-build'
                build_commit = '0b605481ad97ee3726864e556e6519f3a862271f'
                production_commit = $script:P4BaselineProduction }
            candidate = [ordered]@{ worktree = $candidateWorktree; build_commit = $candidateBuild
                production_commit = $script:P4BaselineProduction } } }
    Assert-P4TestRejects { Assert-P4GitLineage $lineage } 'candidate production equals the baseline'
    $lineage.roles.candidate.production_commit = $candidateBuild
    $lineage.roles.candidate.build_commit = $script:P4BaselineProduction
    Assert-P4TestRejects { Assert-P4GitLineage $lineage } 'candidate build precedes its production commit'
} else {
    throw "The candidate role clone is required for the inventory contract: $candidateWorktree"
}

# Reparse points and worktree escapes are refused before anything is hashed.
$linkRoot = Join-Path $testRoot 'reparse'
New-Item -ItemType Directory -Path (Join-Path $linkRoot 'real') -Force | Out-Null
Write-NewInvocationFile (Join-Path $linkRoot 'real/inside.txt') 'inside'
Assert-P4NoReparsePath (Join-Path $linkRoot 'real/inside.txt') $linkRoot 'positive'
Assert-P4TestRejects { Assert-P4NoReparsePath (Join-Path $testRoot 'immutable.txt') $linkRoot 'outside' } 'worktree escape'
$junction = New-Item -ItemType Junction -Path (Join-Path $linkRoot 'link') `
    -Target (Join-Path $linkRoot 'real') -ErrorAction SilentlyContinue
if ($null -ne $junction) {
    Assert-P4TestRejects { Assert-P4NoReparsePath (Join-Path $linkRoot 'link/inside.txt') $linkRoot 'link' } 'reparse point'
} else {
    Write-Output 'REPARSE_CASE=skipped-no-junction-support'
}

# The compiled inventory is exactly the class/jar output of the fixed classpath directories.
$compiledWorktree = Join-Path $testRoot 'compiled'
$compiledRelatives = [Collections.Generic.List[string]]::new()
foreach ($module in $script:P4CompiledJvmModules) {
    $directory = Join-Path $compiledWorktree "$module/build/classes/kotlin/main"
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    Write-NewInvocationFile (Join-Path $directory 'Sample.class') "class-$module"
    Write-NewInvocationFile (Join-Path $directory 'notes.txt') 'ignored by the class/jar contract'
    $compiledRelatives.Add("$module/build/classes/kotlin/main/Sample.class")
    # Every module also reaches the other modules as its published jar.
    $archive = "$module/build/libs/$(Split-Path -Leaf $module).jar"
    New-Item -ItemType Directory -Path (Join-Path $compiledWorktree "$module/build/libs") -Force | Out-Null
    Write-NewInvocationFile (Join-Path $compiledWorktree $archive) "jar-$module"
    $compiledRelatives.Add($archive)
}
$compiledRole = [ordered]@{ worktree = $compiledWorktree; compiled_files = @(
        $compiledRelatives | ForEach-Object {
            [ordered]@{ relative_path = $_; path = (Join-Path $compiledWorktree $_) } }) }
Assert-P4CompiledInventory $compiledRole 'candidate'
$noArchive = Join-Path $testRoot 'no-archive'
foreach ($module in $script:P4CompiledJvmModules) {
    $directory = Join-Path $noArchive "$module/build/classes/kotlin/main"
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    Write-NewInvocationFile (Join-Path $directory 'Sample.class') "class-$module"
}
Assert-P4TestRejects {
    Assert-P4CompiledInventory ([ordered]@{ worktree = $noArchive; compiled_files = @(
                $script:P4CompiledJvmModules | ForEach-Object {
                    $r = "$_/build/classes/kotlin/main/Sample.class"
                    [ordered]@{ relative_path = $r; path = (Join-Path $noArchive $r) } }) }) 'candidate'
} 'host classpath without a published module jar'
$missingCompiled = New-P4MutableCopy $compiledRole
$missingCompiled.compiled_files = @($compiledRole.compiled_files | Select-Object -Skip 1)
Assert-P4TestRejects { Assert-P4CompiledInventory $missingCompiled 'candidate' } 'omitted compiled class'
$foreignRelative = 'core/domain/build/classes/kotlin/main/../../../../foreign/Foreign.class'
New-Item -ItemType Directory -Path (Join-Path $compiledWorktree 'core/domain/foreign') -Force | Out-Null
Write-NewInvocationFile (Join-Path $compiledWorktree 'core/domain/foreign/Foreign.class') 'foreign'
$foreignCompiled = New-P4MutableCopy $compiledRole
$foreignCompiled.compiled_files = @($compiledRole.compiled_files) + @([ordered]@{
        relative_path = 'core/domain/foreign/Foreign.class'
        path = (Join-Path $compiledWorktree 'core/domain/foreign/Foreign.class') })
Assert-P4TestRejects { Assert-P4CompiledInventory $foreignCompiled 'candidate' } 'class outside the fixed directories'
$emptyWorktree = Join-Path $testRoot 'unbuilt'
New-Item -ItemType Directory -Path $emptyWorktree -Force | Out-Null
Assert-P4TestRejects {
    Assert-P4CompiledInventory ([ordered]@{ worktree = $emptyWorktree; compiled_files = @() }) 'candidate'
} 'unbuilt host classpath'

# The recorded inventory must equal the real resolved JavaExec classpath.
$classpathSlot = Join-Path $testRoot 'host-slot'
New-Item -ItemType Directory -Path $classpathSlot -Force | Out-Null
$classpathRecords = @($compiledRole.compiled_files | ForEach-Object {
        $item = Get-Item -LiteralPath $_.path
        [ordered]@{ relative_path = $_.relative_path; path = $_.path; byte_count = $item.Length
            sha256 = Get-FileSha256 $item.FullName } })
$classpathAggregate = Get-P4FileInventoryHash $classpathRecords
$classpathManifest = [ordered]@{ roles = [ordered]@{ candidate = [ordered]@{
            worktree = $compiledWorktree; compiled_files = $classpathRecords
            compiled_sha256 = $classpathAggregate } } }
$rootLines = @($script:P4HostClasspathSchema, "role`tcandidate", "roots`t1", $compiledWorktree)
$fileLines = @($classpathRecords | ForEach-Object { "file`t$($_.relative_path)`t$($_.byte_count)`t$($_.sha256)" })
$sortedFiles = [string[]]@($fileLines); [Array]::Sort($sortedFiles, [StringComparer]::Ordinal)
$hashHeader = @($script:P4HostClasspathSchema, "role`tcandidate", "files`t$($sortedFiles.Count)")
$writeClasspath = {
    param([string]$Directory, [string[]]$Roots, [string[]]$Header, [string[]]$Files, [string]$Aggregate)
    New-Item -ItemType Directory -Path $Directory -Force | Out-Null
    Write-NewInvocationFile (Join-Path $Directory 'classpath.txt') (($Roots -join "`n") + "`n")
    Write-NewInvocationFile (Join-Path $Directory 'classpath-sha256.txt') `
        ((@($Header) + @($Files) + @("aggregate`t$Aggregate") -join "`n") + "`n")
}
& $writeClasspath $classpathSlot $rootLines $hashHeader $sortedFiles $classpathAggregate
Assert-P4HostClasspathAgreement -OutputDirectory $classpathSlot -Role candidate -Manifest $classpathManifest
$tamperedSlot = Join-Path $testRoot 'host-slot-tampered'
& $writeClasspath $tamperedSlot $rootLines $hashHeader $sortedFiles ('0' * 64)
Assert-P4TestRejects {
    Assert-P4HostClasspathAgreement -OutputDirectory $tamperedSlot -Role candidate -Manifest $classpathManifest
} 'fabricated classpath aggregate'
# A runner legitimately loads only part of the role's output, so a subset with its own recomputed
# aggregate is accepted; the same subset carrying the union's aggregate is not.
$subsetRecords = @($classpathRecords | Select-Object -Skip 1)
$shortFiles = [string[]]@($subsetRecords |
    ForEach-Object { "file`t$($_.relative_path)`t$($_.byte_count)`t$($_.sha256)" })
[Array]::Sort($shortFiles, [StringComparer]::Ordinal)
$shortHeader = @($script:P4HostClasspathSchema, "role`tcandidate", "files`t$($shortFiles.Count)")
$subsetSlot = Join-Path $testRoot 'host-slot-subset'
$subsetAggregate = Get-P4FileInventoryHash $subsetRecords
& $writeClasspath $subsetSlot $rootLines $shortHeader $shortFiles $subsetAggregate
Assert-P4HostClasspathAgreement -OutputDirectory $subsetSlot -Role candidate -Manifest $classpathManifest
$shortSlot = Join-Path $testRoot 'host-slot-short'
& $writeClasspath $shortSlot $rootLines $shortHeader $shortFiles $classpathAggregate
Assert-P4TestRejects {
    Assert-P4HostClasspathAgreement -OutputDirectory $shortSlot -Role candidate -Manifest $classpathManifest
} 'classpath subset carrying the union aggregate'
$emptySlot = Join-Path $testRoot 'host-slot-empty'
& $writeClasspath $emptySlot $rootLines @($script:P4HostClasspathSchema, "role`tcandidate", "files`t0") @() `
    (Get-Sha256Hex ([Text.UTF8Encoding]::new($false).GetBytes("`n")))
Assert-P4TestRejects {
    Assert-P4HostClasspathAgreement -OutputDirectory $emptySlot -Role candidate -Manifest $classpathManifest
} 'classpath using no compiled output of its own worktree'
$undeclaredSlot = Join-Path $testRoot 'host-slot-undeclared'
$undeclaredFiles = [string[]]@($sortedFiles + "file`tcore/domain/build/classes/kotlin/main/Extra.class`t5`t$('a' * 64)")
[Array]::Sort($undeclaredFiles, [StringComparer]::Ordinal)
$undeclaredHeader = @($script:P4HostClasspathSchema, "role`tcandidate", "files`t$($undeclaredFiles.Count)")
& $writeClasspath $undeclaredSlot $rootLines $undeclaredHeader $undeclaredFiles $classpathAggregate
Assert-P4TestRejects {
    Assert-P4HostClasspathAgreement -OutputDirectory $undeclaredSlot -Role candidate -Manifest $classpathManifest
} 'classpath used an undeclared class'
$wrongRoleSlot = Join-Path $testRoot 'host-slot-role'
& $writeClasspath $wrongRoleSlot @($script:P4HostClasspathSchema, "role`tbaseline", "roots`t1", $compiledWorktree) `
    @($script:P4HostClasspathSchema, "role`tbaseline", "files`t$($sortedFiles.Count)") $sortedFiles $classpathAggregate
Assert-P4TestRejects {
    Assert-P4HostClasspathAgreement -OutputDirectory $wrongRoleSlot -Role candidate -Manifest $classpathManifest
} 'classpath evidence for another role'

# ---------------------------------------------------------------------------
# Real classpath probes. `-PneneP4ClasspathOnly=true` recorded what Gradle actually resolved for the
# three host runners, so the fixed directory set is checked against measured data, not against a guess.
# Probe directories accumulate across runs, so the probe numbers are derived from what was recorded
# instead of being written down here. The worktree is read back from the records too, and then has to
# be this run's candidate clone: reading it back keeps a rebuilt clone from being replayed silently,
# and the comparison keeps the derivation from wandering off to some other clone on the machine.
# ---------------------------------------------------------------------------
function Select-P4RecordedProbeSet {
    <#
        The three newest `candidate` probes. A `baseline` probe is a legitimate record of the other
        role and simply does not belong to this check, so it is skipped by its own role line instead of
        by the reader's rejection; a record whose role line cannot be read is a failure, never a silent
        skip. Only a probe root holding no numbered directory at all yields nothing. Once probes exist,
        a set short of three - and a half-written probe newer than the three chosen - is a failure:
        the newest evidence must never be stepped over in favour of an older generation.
    #>
    param([Parameter(Mandatory = $true)][string]$ProbeRoot)

    if (-not (Test-Path -LiteralPath $ProbeRoot -PathType Container)) { return @() }
    $numbered = 0
    $recorded = [Collections.Generic.List[object]]::new()
    $unrecorded = [Collections.Generic.List[object]]::new()
    foreach ($directory in @(Get-ChildItem -LiteralPath $ProbeRoot -Directory)) {
        if ($directory.Name -cnotmatch '^classpath-probe-(?<number>\d+)$') { continue }
        $number = [int]$Matches['number']
        $numbered++
        $hashPath = Join-Path $directory.FullName 'classpath-sha256.txt'
        if (-not (Test-Path -LiteralPath $hashPath -PathType Leaf)) {
            $unrecorded.Add([ordered]@{ number = $number; path = $directory.FullName })
            continue
        }
        $header = @(Get-Content -LiteralPath $hashPath -TotalCount 2)
        if ($header.Count -ne 2 -or $header[0] -cne $script:P4HostClasspathSchema -or
            $header[1] -cnotmatch '^role\t(?<role>baseline|candidate)$') {
            throw "A recorded classpath probe has no readable role: $($directory.FullName)"
        }
        if ($Matches['role'] -cne 'candidate') { continue }
        $recorded.Add([ordered]@{ number = $number; path = $directory.FullName })
    }
    if ($numbered -eq 0) { return @() }
    if ($recorded.Count -lt 3) { throw 'The recorded classpath probes are incomplete.' }
    $selected = @($recorded | Sort-Object -Property { $_.number } -Descending | Select-Object -First 3)
    $oldestSelected = [int]$selected[-1].number
    foreach ($partial in $unrecorded) {
        if ($partial.number -gt $oldestSelected) {
            throw "A classpath probe newer than the replayed set recorded nothing: $($partial.path)"
        }
    }
    return $selected
}

function Get-P4RecordedProbeWorktree {
    <#
        The worktree the probes were taken in is read back from their own root lines: every root that
        ends in one of the fixed compiled directories must leave the same ancestor behind, across all
        three probes. A root that is a jar leaves none - neither a Gradle cache jar nor a module's own
        build/libs/*.jar ends in a compiled directory - so every probe must still name the worktree
        through at least one directory root of its own.
    #>
    param([Parameter(Mandatory = $true)][object[]]$RootSets)

    if ($RootSets.Count -ne 3) { throw "The worktree must be read back from all three probes: $($RootSets.Count) given." }
    $ancestors = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $worktree = $null
    foreach ($roots in $RootSets) {
        $named = $false
        foreach ($root in @($roots)) {
            if (-not [IO.Path]::IsPathFullyQualified($root)) {
                throw "A recorded classpath root is not absolute: $root"
            }
            $normalized = $root -replace '\\', '/'
            foreach ($directory in $script:P4CompiledDirectories) {
                if (-not $normalized.EndsWith("/$directory", [StringComparison]::OrdinalIgnoreCase)) { continue }
                $ancestor = $normalized.Substring(0, $normalized.Length - $directory.Length - 1)
                if ($ancestors.Add($ancestor)) { $worktree = $ancestor }
                $named = $true
                break
            }
        }
        if (-not $named) { throw 'A recorded classpath probe names no worktree of its own.' }
    }
    if ($ancestors.Count -ne 1) {
        throw "The recorded classpath probes do not agree on one worktree: $($ancestors.Count) found."
    }
    if (-not (Test-Path -LiteralPath $worktree -PathType Container)) {
        throw "The worktree the classpath probes recorded is gone: $worktree"
    }
    return $worktree
}

function Assert-P4ProbeRunnerCoverage {
    <#
        Three probes of one runner would replay one classpath three times. The three root sets, taken
        relative to their shared worktree, must therefore all differ.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Worktree,
        [Parameter(Mandatory = $true)][object[]]$RootSets
    )

    if ($RootSets.Count -ne 3) { throw "The host runners are covered by three probes: $($RootSets.Count) given." }
    $signatures = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($roots in $RootSets) {
        $relatives = [string[]]@(@($roots) |
            ForEach-Object { ([IO.Path]::GetRelativePath($Worktree, $_)) -replace '\\', '/' })
        [Array]::Sort($relatives, [StringComparer]::OrdinalIgnoreCase)
        if (-not $signatures.Add($relatives -join "`n")) {
            throw 'Two recorded classpath probes cover the same host runner.'
        }
    }
}

# The derivation answers to synthetic probe roots before it is trusted with the recorded ones.
$probeFixtureRoot = Join-Path $testRoot 'probe-selection'
$writeProbeRole = {
    param([string]$Directory, [string]$Name, [string]$Role)
    $slot = Join-Path $Directory $Name
    New-Item -ItemType Directory -Path $slot -Force | Out-Null
    Write-NewInvocationFile (Join-Path $slot 'classpath-sha256.txt') `
        (($script:P4HostClasspathSchema + "`nrole`t$Role`nfiles`t0`naggregate`t$('0' * 64)") + "`n")
}
$mixedRoot = Join-Path $probeFixtureRoot 'mixed'
foreach ($name in @('classpath-probe-5', 'classpath-probe-7', 'classpath-probe-40')) {
    & $writeProbeRole $mixedRoot $name 'candidate'
}
foreach ($name in @('classpath-probe-6', 'classpath-probe-41')) { & $writeProbeRole $mixedRoot $name 'baseline' }
# An abandoned probe older than the chosen three is spent evidence, and a directory whose name is not
# `classpath-probe-<N>` is not a probe at all.
New-Item -ItemType Directory -Path (Join-Path $mixedRoot 'classpath-probe-3') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $mixedRoot 'classpath-probe-4x') -Force | Out-Null
# 40 beats 7 as a number, not as a name, and the baseline records are neither chosen nor rejected.
$selectedFixture = @(Select-P4RecordedProbeSet $mixedRoot)
if ((@($selectedFixture | ForEach-Object { $_.number }) -join ',') -cne '40,7,5') {
    throw 'Probe selection does not take the three newest candidate probes.'
}
if (@(Select-P4RecordedProbeSet (Join-Path $probeFixtureRoot 'absent')).Count -ne 0) {
    throw 'Probe selection invented a probe set.'
}
$newerRoot = Join-Path $probeFixtureRoot 'newer-incomplete'
foreach ($name in @('classpath-probe-1', 'classpath-probe-2', 'classpath-probe-3')) {
    & $writeProbeRole $newerRoot $name 'candidate'
}
New-Item -ItemType Directory -Path (Join-Path $newerRoot 'classpath-probe-9') -Force | Out-Null
Assert-P4TestRejects { Select-P4RecordedProbeSet $newerRoot } 'probe newer than the replayed set left unrecorded'
$shortRoot = Join-Path $probeFixtureRoot 'short'
foreach ($name in @('classpath-probe-1', 'classpath-probe-2')) { & $writeProbeRole $shortRoot $name 'candidate' }
& $writeProbeRole $shortRoot 'classpath-probe-3' 'baseline'
Assert-P4TestRejects { Select-P4RecordedProbeSet $shortRoot } 'probe set short of all three runners'
$baselineOnlyRoot = Join-Path $probeFixtureRoot 'baseline-only'
& $writeProbeRole $baselineOnlyRoot 'classpath-probe-1' 'baseline'
Assert-P4TestRejects { Select-P4RecordedProbeSet $baselineOnlyRoot } 'probe root holding the other role only'
$unreadableRoot = Join-Path $probeFixtureRoot 'unreadable'
& $writeProbeRole $unreadableRoot 'classpath-probe-1' 'other'
Assert-P4TestRejects { Select-P4RecordedProbeSet $unreadableRoot } 'probe record with an unreadable role'
$fixtureCompiled = $script:P4CompiledDirectories[0] -replace '/', '\'
$fixtureRoots = @('project-format', 'recovery-record', 'legacy-import' | ForEach-Object {
        , [string[]]@((Join-Path $testRoot $fixtureCompiled), (Join-Path "$testRoot-gradle-cache" "$_.jar")) })
$fixtureWorktree = $testRoot -replace '\\', '/'
$jarOnlyRoots = [string[]]@((Join-Path "$testRoot-gradle-cache" 'jar-only.jar'))
Assert-P4TestRejects {
    Assert-P4ProbeRunnerCoverage -Worktree $fixtureWorktree -RootSets @($fixtureRoots[0], $fixtureRoots[0], $fixtureRoots[1])
} 'probe set replaying one runner twice'
Assert-P4ProbeRunnerCoverage -Worktree $fixtureWorktree -RootSets $fixtureRoots
if ((Get-P4RecordedProbeWorktree $fixtureRoots) -cne $fixtureWorktree) {
    throw 'The probe worktree is not read back from the recorded roots.'
}
# Every negative below keeps three root sets, so each one is refused for its own reason.
Assert-P4TestRejects {
    Get-P4RecordedProbeWorktree @($fixtureRoots[0], $fixtureRoots[1],
        [string[]]@((Join-Path "$testRoot-other" $fixtureCompiled)))
} 'probes from two worktrees'
Assert-P4TestRejects {
    Get-P4RecordedProbeWorktree @($fixtureRoots[0], $fixtureRoots[1], $jarOnlyRoots)
} 'one probe naming no worktree of its own'
Assert-P4TestRejects {
    Get-P4RecordedProbeWorktree @($jarOnlyRoots, $jarOnlyRoots, $jarOnlyRoots)
} 'probes naming no worktree at all'
Assert-P4TestRejects {
    Get-P4RecordedProbeWorktree @($fixtureRoots[0], $fixtureRoots[1], [string[]]@($fixtureCompiled))
} 'a recorded root that is not absolute'
$absentRoots = [string[]]@((Join-Path "$testRoot-absent" $fixtureCompiled))
Assert-P4TestRejects {
    Get-P4RecordedProbeWorktree @($absentRoots, $absentRoots, $absentRoots)
} 'probes naming a worktree that is gone'

$probeRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../build/reports/issue-106'))
$probeSet = @(Select-P4RecordedProbeSet $probeRoot)
$probeDirectories = @($probeSet | ForEach-Object { $_.path })
if ($probeDirectories.Count -eq 0) {
    Write-Output 'CLASSPATH_PROBE=SKIPPED-no-recorded-probe'
} else {
    Write-Output "CLASSPATH_PROBE_SET=$((@($probeSet | ForEach-Object { $_.number }) | Sort-Object) -join ',')"
    $probeRelatives = [Collections.Generic.List[string]]::new()
    $probeRootSets = [Collections.Generic.List[object]]::new()
    $probeUsed = @{}
    foreach ($probe in $probeDirectories) {
        # The reader must accept every real record, not just the synthetic one.
        $probeLines = Read-P4HostClasspathRecord (Join-Path $probe 'classpath-sha256.txt') 'candidate'
        $probeRootLines = Read-P4HostClasspathRecord (Join-Path $probe 'classpath.txt') 'candidate'
        if ($probeRootLines[2] -cnotmatch '^roots\t(?<count>[1-9]\d*)$') { throw "Unreadable probe root count: $probe" }
        if ($probeRootLines.Count -ne 3 + [int]$Matches['count']) { throw "Probe root list disagrees: $probe" }
        $probeRootSets.Add([string[]]@($probeRootLines[3..($probeRootLines.Count - 1)]))
        if ($probeLines[2] -cnotmatch '^files\t(?<count>\d+)$') { throw "Unreadable probe file count: $probe" }
        if ($probeLines.Count -ne 4 + [int]$Matches['count']) { throw "Probe record length disagrees: $probe" }
        $used = [Collections.Generic.List[string]]::new()
        foreach ($line in $probeLines) {
            $parts = $line -split "`t"
            if ($parts.Count -ne 4 -or $parts[0] -cne 'file') { continue }
            if ([IO.Path]::IsPathFullyQualified($parts[1])) { continue }
            $probeRelatives.Add($parts[1])
            if ($script:P4CompiledFileExtensions -ccontains [IO.Path]::GetExtension($parts[1]).ToLowerInvariant()) {
                $used.Add($parts[1])
            }
        }
        $probeUsed[$probe] = [string[]]@($used)
    }
    $probeWorktree = Get-P4RecordedProbeWorktree $probeRootSets
    Write-Output "CLASSPATH_PROBE_WORKTREE=$probeWorktree"
    # Reading the worktree back says where the probes were taken, not that it is the clone this run
    # measured. The pinned `$candidateWorktree` decides that, so the two must name the same tree.
    if (($probeWorktree -replace '\\', '/') -ine ($candidateWorktree -replace '\\', '/')) {
        throw "The recorded classpath probes were not taken in the candidate clone: $probeWorktree"
    }
    Assert-P4ProbeRunnerCoverage -Worktree $probeWorktree -RootSets $probeRootSets
    if ($probeRelatives.Count -lt 900) { throw 'The recorded classpath probes carry too few in-worktree files.' }
    $covered = 0
    foreach ($relative in $probeRelatives) {
        $inside = $false
        foreach ($directory in $script:P4CompiledDirectories) {
            if ($relative.StartsWith("$directory/", [StringComparison]::Ordinal)) { $inside = $true; break }
        }
        if (-not $inside) { throw "The real host classpath used a directory outside the fixed set: $relative" }
        $covered++
    }
    Write-Output "CLASSPATH_PROBE_FILES=$covered"

    # The inventory is the role's whole compiled output; each slot uses a subset of it. Build that
    # union straight from disk, exactly as the preflight does, and replay all three real probes.
    $unionPaths = @(Get-P4ExpectedCompiledPaths $probeWorktree 'candidate') | Sort-Object
    $unionFiles = @($unionPaths | ForEach-Object {
            $full = Join-Path $probeWorktree $_
            $bytes = [IO.File]::ReadAllBytes($full)
            [ordered]@{ relative_path = $_; path = $full; byte_count = "$($bytes.Length)"
                sha256 = (Get-Sha256Hex $bytes) } })
    $unionManifest = [ordered]@{ roles = [ordered]@{ candidate = [ordered]@{
                worktree = $probeWorktree; compiled_files = $unionFiles
                compiled_sha256 = (Get-P4FileInventoryHash $unionFiles) } } }
    Write-Output "CLASSPATH_UNION_FILES=$($unionFiles.Count)"
    foreach ($probe in $probeDirectories) {
        # Proves the Kotlin runner and Get-P4FileInventoryHash agree byte for byte on real output,
        # and that a runner's proper subset of the union is accepted.
        Assert-P4HostClasspathAgreement -OutputDirectory $probe -Role candidate -Manifest $unionManifest
    }

    # The heaviest of the three: the runner that loaded the most in-worktree class/jar entries. A tie
    # goes to the newest record, so the choice never depends on the order the directories were read in.
    $heaviestProbe = @($probeSet | Sort-Object -Descending -Property @{ Expression = { $probeUsed[$_.path].Count } },
        @{ Expression = { $_.number } })[0].path
    $heaviestProbeLines = @(Get-Content -LiteralPath (Join-Path $heaviestProbe 'classpath-sha256.txt'))
    if ($heaviestProbeLines[-1] -cnotmatch '^aggregate\t(?<sha>[0-9a-f]{64})$') { throw 'The probe aggregate is unreadable.' }
    Write-Output "CLASSPATH_PROBE_AGGREGATE=$($Matches['sha'])"
    $heaviestProbeUsed = $probeUsed[$heaviestProbe]
    if ($heaviestProbeUsed.Count -lt 900) { throw 'The heaviest probe carries too few class/jar entries.' }
    if ($heaviestProbeUsed.Count -ge $unionFiles.Count) { throw 'A single runner cannot use the whole role output.' }
    # Containment is still enforced: drop from the union one file the probe actually loaded.
    $omitted = $heaviestProbeUsed[0]
    $shortUnion = [ordered]@{ roles = [ordered]@{ candidate = [ordered]@{
                worktree = $probeWorktree
                compiled_files = @($unionFiles | Where-Object { $_.relative_path -cne $omitted })
                compiled_sha256 = $unionManifest.roles.candidate.compiled_sha256 } } }
    Assert-P4TestRejects {
        Assert-P4HostClasspathAgreement -OutputDirectory $heaviestProbe -Role candidate -Manifest $shortUnion
    } 'real classpath used a file the inventory omits'
    # The recorded aggregate must stay self-consistent. Recorded evidence is copied, never edited.
    $tamperedProbe = Join-Path $testRoot 'probe-tampered'
    New-Item -ItemType Directory -Path $tamperedProbe -Force | Out-Null
    Write-NewInvocationFile (Join-Path $tamperedProbe 'classpath.txt') `
        ([IO.File]::ReadAllText((Join-Path $heaviestProbe 'classpath.txt')))
    $tamperedLines = [string[]]@($heaviestProbeLines)
    $tamperedLines[$tamperedLines.Count - 1] = "aggregate`t$('0' * 64)"
    Write-NewInvocationFile (Join-Path $tamperedProbe 'classpath-sha256.txt') (($tamperedLines -join "`n") + "`n")
    Assert-P4TestRejects {
        Assert-P4HostClasspathAgreement -OutputDirectory $tamperedProbe -Role candidate -Manifest $unionManifest
    } 'recorded probe aggregate no longer matches its own records'
}

# ---------------------------------------------------------------------------
# S2/S7. APK semantics come from aapt2, not from the manifest's own claims.
# ---------------------------------------------------------------------------
$aapt2 = @(Get-ChildItem -Path 'C:/Users/info/AppData/Local/Android/Sdk/build-tools' -Directory -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'aapt2.exe' } |
    Where-Object { Test-Path -LiteralPath $_ -PathType Leaf })
if ($aapt2.Count -eq 0) { throw 'aapt2 is required for the APK packaging contract.' }
$aapt2Path = $aapt2[0]
$apks = [ordered]@{
    app_debug = "$candidateWorktree/app/android/build/outputs/apk/debug/android-debug.apk"
    test_debug = "$candidateWorktree/app/android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk"
    app_release_like = "$candidateWorktree/app/android/build/outputs/apk/benchmarkRelease/android-benchmarkRelease.apk"
    publication_test = "$candidateWorktree/adapters/persistence/build/outputs/apk/androidTest/debug/persistence-debug-androidTest.apk"
}
$declared = [ordered]@{
    app_debug = [ordered]@{ path = $apks.app_debug; variant = 'debug'
        target_package = 'io.github.hideyukimori.nenepixel'; test_package = 'none'; requested_dexopt = 'verify' }
    test_debug = [ordered]@{ path = $apks.test_debug; variant = 'debugAndroidTest'
        target_package = 'io.github.hideyukimori.nenepixel'
        test_package = 'io.github.hideyukimori.nenepixel.test'; requested_dexopt = 'verify' }
    app_release_like = [ordered]@{ path = $apks.app_release_like; variant = 'benchmarkRelease'
        target_package = 'io.github.hideyukimori.nenepixel'; test_package = 'none'
        requested_dexopt = 'speed-profile' }
    publication_test = [ordered]@{ path = $apks.publication_test; variant = 'debugAndroidTest'
        target_package = 'io.github.hideyukimori.nenepixel.adapters.persistence.test'
        test_package = 'io.github.hideyukimori.nenepixel.adapters.persistence.test'
        requested_dexopt = 'verify' }
}
foreach ($kind in $declared.Keys) {
    if (-not (Test-Path -LiteralPath $declared[$kind].path -PathType Leaf)) {
        throw "The measured APK is missing: $kind"
    }
    Assert-P4ApkPackaging $aapt2Path $declared[$kind] $kind "candidate.$kind"
}
$wrongVariant = New-P4MutableCopy $declared.app_debug; $wrongVariant.variant = 'release'
Assert-P4TestRejects { Assert-P4ApkPackaging $aapt2Path $wrongVariant 'app_debug' 'negative' } 'declared variant drift'
$wrongDexopt = New-P4MutableCopy $declared.app_release_like; $wrongDexopt.requested_dexopt = 'verify'
Assert-P4TestRejects { Assert-P4ApkPackaging $aapt2Path $wrongDexopt 'app_release_like' 'negative' } 'declared dexopt drift'
$notDebuggable = New-P4MutableCopy $declared.app_debug; $notDebuggable.path = $apks.app_release_like
Assert-P4TestRejects { Assert-P4ApkPackaging $aapt2Path $notDebuggable 'app_debug' 'negative' } 'release APK offered as debug'
$notInstrumented = New-P4MutableCopy $declared.test_debug; $notInstrumented.path = $apks.app_debug
Assert-P4TestRejects { Assert-P4ApkPackaging $aapt2Path $notInstrumented 'test_debug' 'negative' } 'app APK offered as test'
$instrumentedApp = New-P4MutableCopy $declared.app_debug; $instrumentedApp.path = $apks.test_debug
Assert-P4TestRejects { Assert-P4ApkPackaging $aapt2Path $instrumentedApp 'app_debug' 'negative' } 'test APK offered as app'
$wrongTarget = New-P4MutableCopy $declared.test_debug
$wrongTarget.target_package = 'io.github.hideyukimori.nenepixel.other'
Assert-P4TestRejects { Assert-P4ApkPackaging $aapt2Path $wrongTarget 'test_debug' 'negative' } 'declared target drift'
$wrongPublication = New-P4MutableCopy $declared.publication_test
$wrongPublication.target_package = 'io.github.hideyukimori.nenepixel'
Assert-P4TestRejects { Assert-P4ApkPackaging $aapt2Path $wrongPublication 'publication_test' 'negative' } 'publication target drift'
$crossedPublication = New-P4MutableCopy $declared.publication_test; $crossedPublication.path = $apks.test_debug
Assert-P4TestRejects { Assert-P4ApkPackaging $aapt2Path $crossedPublication 'publication_test' 'negative' } 'wrong test APK for publication'

# ---------------------------------------------------------------------------
# S7. Preservation, geometry inspection and the fixed collector contract set.
# ---------------------------------------------------------------------------
$preservationRoot = Join-Path $testRoot 'preservation'
New-Item -ItemType Directory -Path $preservationRoot -Force | Out-Null
$freshGuard = 'no_backup/issue-106-acceptance-20260915-0001'
function New-P4PreservationFixture {
    param([string]$Name, [string]$Guard, [string]$Serial, [string]$Schema)
    $directory = Join-Path $preservationRoot $Name
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $path = Join-Path $directory 'preservation.json'
    Write-NewInvocationFile $path (([ordered]@{ schema = $Schema; package = 'io.github.hideyukimori.nenepixel'
                serial = $Serial; guard = $Guard } | ConvertTo-Json -Depth 4))
    # Preservation always precedes the manifest it protects; the freshness window is what is tested.
    (Get-Item -LiteralPath $path).LastWriteTimeUtc = [datetime]::UtcNow.AddHours(-1)
    return [ordered]@{ path = $path }
}
$serial = 'T830128GB26321131293'
$createdUtc = [datetime]::UtcNow.ToString('o', [Globalization.CultureInfo]::InvariantCulture)
$goodPreservation = New-P4PreservationFixture 'fresh' $freshGuard $serial $script:P4PreservationSchema
Assert-P4DevicePreservation $goodPreservation $serial $createdUtc
$retired = New-P4PreservationFixture 'retired' $script:P4RetiredPreservationGuard $serial $script:P4PreservationSchema
Assert-P4TestRejects { Assert-P4DevicePreservation $retired $serial $createdUtc } 'reuse of the consumed recovery guard'
$foreign = New-P4PreservationFixture 'foreign' $freshGuard 'OTHERSERIAL0000' $script:P4PreservationSchema
Assert-P4TestRejects { Assert-P4DevicePreservation $foreign $serial $createdUtc } 'preservation from another device'
$wrongSchema = New-P4PreservationFixture 'schema' $freshGuard $serial 'nene-pixel-device-preservation-v0'
Assert-P4TestRejects { Assert-P4DevicePreservation $wrongSchema $serial $createdUtc } 'preservation schema drift'
$stale = New-P4PreservationFixture 'stale' $freshGuard $serial $script:P4PreservationSchema
(Get-Item -LiteralPath $stale.path).LastWriteTimeUtc = [datetime]::UtcNow.AddHours(-48)
Assert-P4TestRejects { Assert-P4DevicePreservation $stale $serial $createdUtc } 'stale preservation'
$future = New-P4PreservationFixture 'future' $freshGuard $serial $script:P4PreservationSchema
Assert-P4TestRejects {
    Assert-P4DevicePreservation $future $serial ([datetime]::UtcNow.AddHours(-2).ToString('o'))
} 'preservation postdates the manifest'
$misnamed = Join-Path $preservationRoot 'misnamed.json'
Write-NewInvocationFile $misnamed ([IO.File]::ReadAllText($goodPreservation.path))
Assert-P4TestRejects {
    Assert-P4DevicePreservation ([ordered]@{ path = $misnamed }) $serial $createdUtc
} 'non-canonical preservation path'

$inspectionOutput = Join-Path $testRoot 'experiment'
New-Item -ItemType Directory -Path (Join-Path $inspectionOutput 'frame') -Force | Out-Null
$inspectionManifest = [ordered]@{
    output_directory = $inspectionOutput
    frame_experiment = [ordered]@{ directory = (Join-Path $inspectionOutput 'frame') }
    roles = [ordered]@{
        baseline = [ordered]@{ build_commit = '0b605481ad97ee3726864e556e6519f3a862271f'
            artifacts = [ordered]@{ app_release_like = [ordered]@{ sha256 = ('b' * 64) } } }
        candidate = [ordered]@{ build_commit = '3642437c5f9817f70452a2a4d016db8d1962d184'
            artifacts = [ordered]@{ app_release_like = [ordered]@{ sha256 = ('c' * 64) } } } }
    device = [ordered]@{
        baseline_canvas16_bounds = '[100,200][300,400]'; baseline_canvas256_bounds = '[80,180][320,420]'
        candidate_canvas16_bounds = '[100,200][300,400]'; candidate_canvas256_bounds = '[80,180][320,420]' }
}
function New-P4InspectionRecord {
    param([string]$Name, [scriptblock]$Mutate, [string]$Directory = $testRoot)
    $document = [ordered]@{ schema = $script:P4NoSampleInspectionSchema }
    foreach ($role in @('baseline', 'candidate')) {
        $document[$role] = [ordered]@{
            schema = $script:P4NoSampleInspectionSchema; role = $role
            apk_sha256 = [string]$inspectionManifest.roles[$role].artifacts.app_release_like.sha256
            embedded_source_commit = [string]$inspectionManifest.roles[$role].build_commit
            rotation = 1; root_bounds = $script:P4InspectionRootBounds
            canvas16_bounds = [string]$inspectionManifest.device["${role}_canvas16_bounds"]
            canvas256_bounds = [string]$inspectionManifest.device["${role}_canvas256_bounds"]
            geometry_id = $script:P4GeometryId
            # A name -> sha map: each retained dump stays attributable to the capture it came from.
            ui_dump_sha256s = [ordered]@{
                'ui-inspect-16x16.xml' = ('d' * 64); 'ui-inspect-256x256.xml' = ('e' * 64) }
            captured_utc = $createdUtc }
    }
    if ($null -ne $Mutate) { & $Mutate $document }
    $path = Join-Path $Directory "inspection-$Name.json"
    Write-NewInvocationFile $path ($document | ConvertTo-Json -Depth 6)
    return [ordered]@{ path = $path }
}
Assert-P4NoSampleInspection (New-P4InspectionRecord 'valid' $null) $inspectionManifest
# S4 evidence precedes every reservation, so it may not hide inside the reserved directories.
Assert-P4TestRejects {
    Assert-P4NoSampleInspection (New-P4InspectionRecord 'in-output' $null $inspectionOutput) $inspectionManifest
} 'inspection inside the experiment output directory'
Assert-P4TestRejects {
    Assert-P4NoSampleInspection (New-P4InspectionRecord 'in-frame' $null `
            (Join-Path $inspectionOutput 'frame')) $inspectionManifest
} 'inspection inside the frame experiment directory'
Assert-P4TestRejects {
    Assert-P4NoSampleInspection (New-P4InspectionRecord 'bounds' {
            param($d) $d.candidate.canvas256_bounds = '[80,180][321,420]' }) $inspectionManifest
} 'inspection bounds mismatch'
Assert-P4TestRejects {
    Assert-P4NoSampleInspection (New-P4InspectionRecord 'outside' {
            param($d)
            $d.candidate.canvas256_bounds = '[80,180][1921,420]'
            $inspectionManifest.device.candidate_canvas256_bounds = '[80,180][1921,420]' }) $inspectionManifest
} 'inspection bounds outside the root viewport'
$inspectionManifest.device.candidate_canvas256_bounds = '[80,180][320,420]'
Assert-P4TestRejects {
    Assert-P4NoSampleInspection (New-P4InspectionRecord 'rotation' { param($d) $d.candidate.rotation = 0 }) $inspectionManifest
} 'inspection rotation drift'
Assert-P4TestRejects {
    Assert-P4NoSampleInspection (New-P4InspectionRecord 'apk' {
            param($d) $d.baseline.apk_sha256 = ('f' * 64) }) $inspectionManifest
} 'inspection of another APK'
Assert-P4TestRejects {
    Assert-P4NoSampleInspection (New-P4InspectionRecord 'commit' {
            param($d) $d.candidate.embedded_source_commit = ('0' * 40) }) $inspectionManifest
} 'inspection from another build'
Assert-P4TestRejects {
    Assert-P4NoSampleInspection (New-P4InspectionRecord 'geometry' {
            param($d) $d.candidate.geometry_id = 'initial-fit-centered-v2' }) $inspectionManifest
} 'inspection geometry drift'
Assert-P4TestRejects {
    Assert-P4NoSampleInspection (New-P4InspectionRecord 'dumps' {
            param($d) $d.baseline.ui_dump_sha256s = [ordered]@{ 'ui-inspect-16x16.xml' = ('d' * 64) } }) $inspectionManifest
} 'inspection retaining only one UI dump identity'
Assert-P4TestRejects {
    Assert-P4NoSampleInspection (New-P4InspectionRecord 'dump-array' {
            param($d) $d.baseline.ui_dump_sha256s = @(('d' * 64), ('e' * 64)) }) $inspectionManifest
} 'inspection recording UI dumps as an unattributable array'
Assert-P4TestRejects {
    Assert-P4NoSampleInspection (New-P4InspectionRecord 'dump-sha' {
            param($d) $d.candidate.ui_dump_sha256s = [ordered]@{
                'ui-inspect-16x16.xml' = ('d' * 64); 'ui-inspect-256x256.xml' = 'not-a-sha' } }) $inspectionManifest
} 'inspection UI dump identity is not a SHA-256'
Assert-P4TestRejects {
    Assert-P4NoSampleInspection (New-P4InspectionRecord 'dump-missing' {
            param($d) $d.candidate.Remove('ui_dump_sha256s') }) $inspectionManifest
} 'inspection omits ui_dump_sha256s'
Assert-P4TestRejects {
    Assert-P4NoSampleInspection (New-P4InspectionRecord 'role-missing' {
            param($d) $d.Remove('baseline') }) $inspectionManifest
} 'inspection omits a role'

function New-P4ContractManifest {
    param([string[]]$Scopes)
    return [ordered]@{
        correctness = @([ordered]@{ scope = 'unit' })
        collector_contracts = @($Scopes | ForEach-Object { [ordered]@{ scope = $_ } })
    }
}
Assert-P4CollectorContracts (New-P4ContractManifest $script:P4CollectorContractScopes)
Assert-P4TestRejects {
    Assert-P4CollectorContracts (New-P4ContractManifest (@($script:P4CollectorContractScopes) | Select-Object -Skip 1))
} 'missing collector contract scope'
Assert-P4TestRejects {
    Assert-P4CollectorContracts (New-P4ContractManifest (@($script:P4CollectorContractScopes) + 'device-state'))
} 'duplicate collector contract scope'
Assert-P4TestRejects {
    Assert-P4CollectorContracts (New-P4ContractManifest (@($script:P4CollectorContractScopes) + 'unreviewed-lane'))
} 'unknown collector contract scope'
$noCorrectness = New-P4ContractManifest $script:P4CollectorContractScopes
$noCorrectness.correctness = @()
Assert-P4TestRejects { Assert-P4CollectorContracts $noCorrectness } 'absent correctness evidence'

foreach ($bad in @('', '[0,0]', '[0,0][0,0]', '[0,0][10,0]', '[10,0][0,10]', 'x')) {
    Assert-P4TestRejects { Read-P4Bounds $bad 'negative' } "malformed bounds '$bad'"
}
if ((Read-P4Bounds '[0,0][1920,1200]' 'positive').right -ne 1920) { throw 'Accepted bounds were misread.' }
foreach ($bad in @('', 'yesterday', '2026-13-45T00:00:00Z')) {
    Assert-P4TestRejects { Read-P4Utc $bad 'negative' } "malformed timestamp '$bad'"
}

# An Assert-* refuses by throwing and never writes to the pipeline, so no caller can branch on a
# truthy return value and mistake "no output" for acceptance.
$silentAsserts = @(
    @{ name = 'Assert-P4DeviceStateMatches'
        action = { Assert-P4DeviceStateMatches $expectedDevice $deviceState 'fixture' } },
    @{ name = 'Assert-P4NoReparsePath'
        action = { Assert-P4NoReparsePath (Join-Path $linkRoot 'real/inside.txt') $linkRoot 'silent' } },
    @{ name = 'Assert-P4TrackedBlob'
        action = { Assert-P4TrackedBlob $candidateWorktree $candidateBuild `
                'docs/quality/measurements/m2-package-dexopt.ps1' `
                (Join-Path $candidateWorktree 'docs/quality/measurements/m2-package-dexopt.ps1') 'silent' } },
    @{ name = 'Assert-P4MeasurementInventory'; action = { Assert-P4MeasurementInventory $role 'candidate' } },
    @{ name = 'Assert-P4CompiledInventory'; action = { Assert-P4CompiledInventory $compiledRole 'candidate' } },
    @{ name = 'Assert-P4HostClasspathAgreement'
        action = { Assert-P4HostClasspathAgreement -OutputDirectory $classpathSlot -Role candidate `
                -Manifest $classpathManifest } },
    @{ name = 'Assert-P4ApkPackaging'
        action = { Assert-P4ApkPackaging $aapt2Path $declared.app_debug 'app_debug' 'silent' } },
    @{ name = 'Assert-P4DevicePreservation'
        action = { Assert-P4DevicePreservation $goodPreservation $serial $createdUtc } },
    @{ name = 'Assert-P4NoSampleInspection'
        action = { Assert-P4NoSampleInspection (New-P4InspectionRecord 'silent' $null) $inspectionManifest } },
    @{ name = 'Assert-P4CollectorContracts'
        action = { Assert-P4CollectorContracts (New-P4ContractManifest $script:P4CollectorContractScopes) } },
    @{ name = 'Assert-P4InventorySetEquals'
        action = { Assert-P4InventorySetEquals `
                ([Collections.Generic.HashSet[string]]::new([string[]]@('a'), [StringComparer]::Ordinal)) `
                ([Collections.Generic.HashSet[string]]::new([string[]]@('a'), [StringComparer]::Ordinal)) 'silent' } },
    @{ name = 'Assert-P4GitLineage'
        action = {
            Assert-P4GitLineage ([ordered]@{ roles = [ordered]@{
                        baseline = [ordered]@{ worktree = 'C:/n106-baseline-build'
                            build_commit = '0b605481ad97ee3726864e556e6519f3a862271f'
                            production_commit = $script:P4BaselineProduction }
                        candidate = [ordered]@{ worktree = $candidateWorktree; build_commit = $candidateBuild
                            production_commit = $candidateBuild } } })
        } }
)
foreach ($silent in $silentAsserts) {
    $emitted = @(& $silent.action)
    if ($emitted.Count -ne 0) {
        throw "$($silent.name) wrote $($emitted.Count) object(s) to the pipeline; Assert-* must return nothing."
    }
}

# ---------------------------------------------------------------------------
# The reserved template, filled in, is what the contract actually accepts.
# ---------------------------------------------------------------------------
$templatePath = [IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '../../build/reports/issue-106/p4-preflight-template.json'))
if (-not (Test-Path -LiteralPath $templatePath -PathType Leaf)) {
    throw "The reserved preflight template is required: $templatePath"
}
function Resolve-P4TemplatePlaceholders {
    param([AllowNull()]$Node, [string]$Name)
    if ($Node -is [System.Collections.IDictionary]) {
        foreach ($key in @($Node.Keys)) { $Node[$key] = Resolve-P4TemplatePlaceholders $Node[$key] $key }
        return , $Node
    }
    if ($Node -isnot [string] -and $Node -is [System.Collections.IEnumerable]) {
        $items = @($Node)
        for ($i = 0; $i -lt $items.Count; $i++) { $items[$i] = Resolve-P4TemplatePlaceholders $items[$i] $Name }
        return , $items
    }
    if ($Node -isnot [string] -or $Node -cne 'UNSET_PREFLIGHT') { return , $Node }
    switch -Regex ($Name) {
        '^sha256$|_sha256$' { return ('9' * 64) }
        '^byte_count$' { return '4096' }
        '^(production_commit|build_commit|source_commit|generation_commit)$' { return ('a' * 40) }
        '^exit_code$' { return '0' }
        '^path$' { return 'C:/n106-indexed/docs/quality/P4_INDEXED_CUTOVER_PROTOCOL.md' }
        default { return "synthetic-$Name" }
    }
}
$template = Get-Content -LiteralPath $templatePath -Raw | ConvertFrom-Json -AsHashtable
$filled = Resolve-P4TemplatePlaceholders $template 'manifest'
$experimentRoot = Join-Path $testRoot 'reserved-experiment'
$filled.created_utc = $createdUtc
$filled.experiment_id = 'p4-indexed-cutover-synthetic'
$filled.output_directory = $experimentRoot
$filled.frame_experiment.directory = Join-Path $experimentRoot 'frame-experiment'
$filled.device.profile = 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36'
$filled.device.model = 'iPlay80miniPro'
$filled.device.api = 36
$filled.device.initial_viewport = $script:P4GeometryId
$filled.device.power_save = '0'
$filled.device.interactive = 'true'
$filled.device.usb_power = 'true'
foreach ($role in @('baseline', 'candidate')) {
    $filled.device["${role}_canvas16_bounds"] = '[100,200][300,400]'
    $filled.device["${role}_canvas256_bounds"] = '[80,180][320,420]'
}
Assert-P4ManifestContract $filled
# The template's own placeholders must still be refused once they reach the contract.
$unresolved = Get-Content -LiteralPath $templatePath -Raw | ConvertFrom-Json -AsHashtable
Assert-P4TestRejects { Assert-P4ManifestContract $unresolved } 'unfilled preflight template'
$wrongCatalog = Get-Content -LiteralPath $templatePath -Raw | ConvertFrom-Json -AsHashtable
$wrongCatalog = Resolve-P4TemplatePlaceholders $wrongCatalog 'manifest'
foreach ($key in @('created_utc', 'experiment_id', 'output_directory')) { $wrongCatalog[$key] = $filled[$key] }
$wrongCatalog.frame_experiment.directory = $filled.frame_experiment.directory
foreach ($key in @('profile', 'model', 'api', 'initial_viewport', 'power_save', 'interactive', 'usb_power')) {
    $wrongCatalog.device[$key] = $filled.device[$key]
}
foreach ($role in @('baseline', 'candidate')) {
    $wrongCatalog.device["${role}_canvas16_bounds"] = '[100,200][300,400]'
    $wrongCatalog.device["${role}_canvas256_bounds"] = '[80,180][320,420]'
}
$wrongCatalog.slots[0].timeout_seconds = 181
Assert-P4TestRejects { Assert-P4ManifestContract $wrongCatalog } 'slot budget drift in the template'

# ---------------------------------------------------------------------------
# Stage dispatch: a slot re-proves the offline facts without touching the device.
# ---------------------------------------------------------------------------
$stageRoot = Join-Path $testRoot 'stage'
New-Item -ItemType Directory -Path $stageRoot -Force | Out-Null
$protocolPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'P4_INDEXED_CUTOVER_PROTOCOL.md'))
$stageFileRecord = [ordered]@{ path = $protocolPath; byte_count = 1; sha256 = (Get-FileSha256 $protocolPath) }
$stageManifest = [ordered]@{
    created_utc = $createdUtc
    output_directory = (Join-Path $stageRoot 'experiment')
    protocol = $stageFileRecord
    tools = [ordered]@{ adb = $stageFileRecord; aapt2 = $stageFileRecord }
    toolchain = [ordered]@{ wrapper = $stageFileRecord; catalog = $stageFileRecord
        verification_metadata = $stageFileRecord; locks = @($stageFileRecord) }
    device = [ordered]@{ serial = $serial; asset_preservation = $stageFileRecord
        no_sample_inspection = $stageFileRecord }
    roles = [ordered]@{ baseline = [ordered]@{ build_commit = ('a' * 40) }
        candidate = [ordered]@{ build_commit = ('a' * 40) } }
    correctness = @([ordered]@{ source_commit = ('a' * 40); scope = 'unit'; command = 'synthetic'
            exit_code = 0; log = $stageFileRecord })
    collector_contracts = @($script:P4CollectorContractScopes | ForEach-Object {
            [ordered]@{ source_commit = ('a' * 40); scope = $_; command = 'synthetic'; exit_code = 0
                log = $stageFileRecord } })
}
$script:P4StageCallLog = [Collections.Generic.List[string]]::new()
$invokeStage = {
    param([string]$Stage)
    $script:P4StageCallLog.Clear()
    & {
        # Synthetic call log only.
        function Assert-P4ManifestContract { param($Manifest) $script:P4StageCallLog.Add('contract') }
        function Assert-P4FileRecord { param($Record, $Name) $script:P4StageCallLog.Add("file:$Name") }
        function Assert-P4GitLineage { param($Manifest) $script:P4StageCallLog.Add('lineage') }
        function Assert-P4RoleSource { param($Role, $Name, $Aapt2Path) $script:P4StageCallLog.Add("role:$Name") }
        function Assert-P4DevicePreservation { param($Record, $Serial, $CreatedUtc) $script:P4StageCallLog.Add('preservation') }
        function Assert-P4NoSampleInspection { param($Record, $Manifest) $script:P4StageCallLog.Add('inspection') }
        function Assert-P4LiveDeviceAdmission { param($Manifest, $RepositoryRoot) $script:P4StageCallLog.Add('device') }
        Assert-P4ManifestArtifacts $stageManifest $repositoryRoot -Stage $Stage
    }
    return [string[]]@($script:P4StageCallLog)
}
$slotCalls = & $invokeStage 'slot'
foreach ($required in @('contract', 'lineage', 'role:baseline', 'role:candidate',
        'preservation', 'inspection', 'file:protocol')) {
    if ($slotCalls -cnotcontains $required) { throw "The slot stage skipped $required." }
}
if ($slotCalls -ccontains 'device') { throw 'The slot stage must not touch the device again.' }
if (Test-Path -LiteralPath ((Join-Path $stageRoot 'experiment') + '-preflight')) {
    throw 'The slot stage created the reserved preflight device directory.'
}
$reservationCalls = & $invokeStage 'reservation'
if ($reservationCalls -cnotcontains 'device') { throw 'The reservation stage must admit the live device.' }
Assert-P4TestRejects { & $invokeStage 'midway' } 'unknown preflight stage'

# The 2026-09-13 handoff barrier was retired on 2026-09-16 (hide's decision); preflight must not carry it.
if (Get-Command -Name 'Assert-P4CollectionImplementationReady' -CommandType Function -ErrorAction SilentlyContinue) {
    throw 'The retired collection readiness barrier is still defined.'
}

Write-Output 'P4_NO_DEVICE_CONTRACT_VALIDATION=pass'
Write-Output 'CASES=fixed-budget,five-host-populations,truncation,duplicate,reorder,gross-anomaly,negative,NaN,summary,role,extra-row,unfabricated-baseline,unset,missing,empty,overflow,exclusive-output,duplicate-artifact,path-escape,tamper'
Write-Output 'DEVICE_STATE_CASES=fixture-parse,exact-drift,thermal,battery,power-save,interactive,usb-power,rotation-recorded,absent-thermal,ambiguous-mode,rotation-disagreement,user-rotation-disagreement,absent-locale,absent-low-power,dexopt-verify,dexopt-not-installed,dexopt-section'
Write-Output 'INVENTORY_CASES=expected-set,baseline-lane-separation,set-short,set-wide,path-escape,blob-mismatch,untracked,ancestor,lineage-identity,lineage-order,reparse,compiled-set,compiled-foreign,compiled-unbuilt,compiled-no-module-jar,probe-directory-coverage,probe-reader,probe-union-subset-all-three,probe-aggregate,probe-omission,probe-aggregate-mismatch,probe-selection-newest-candidates,probe-selection-older-incomplete-ignored,probe-selection-absent,probe-selection-newer-incomplete,probe-set-short,probe-selection-baseline-only,probe-role-unreadable,probe-runner-duplicate,probe-runner-coverage-accepted,probe-worktree-derived,probe-worktree-ambiguous,probe-worktree-partial,probe-worktree-unnamed,probe-worktree-relative-root,probe-worktree-gone,classpath-agreement,classpath-aggregate,classpath-subset-accepted,classpath-subset-union-aggregate,classpath-empty,classpath-undeclared,classpath-role'
Write-Output 'PACKAGING_CASES=four-kinds,variant,dexopt,debuggable,instrumentation-absent,instrumentation-present,target-drift,publication-target,publication-apk'
Write-Output 'ADMISSION_CASES=preservation-fresh,retired-guard,foreign-serial,preservation-schema,stale,postdated,noncanonical-path,inspection-valid,inspection-in-output,inspection-in-frame,inspection-bounds,inspection-outside,inspection-rotation,inspection-apk,inspection-commit,inspection-geometry,inspection-dump-single,inspection-dump-array,inspection-dump-sha,inspection-dump-missing,inspection-role-missing,contract-scopes,contract-missing,contract-duplicate,contract-unknown,contract-correctness,bounds,timestamps,assert-returns-nothing,template-filled,template-unfilled,template-slot-drift,stage-slot-no-device,stage-reservation-device,stage-unknown,readiness-barrier'
Write-Output "SYNTHETIC_FIXTURE_DIRECTORY=$testRoot"

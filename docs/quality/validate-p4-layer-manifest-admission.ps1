# Issue #145 R4/R7 (T7a): no-device admission of the layer phase manifest (four artifact roles,
# 18 slots, verified preservation-v2 pin, one Baseline Profile pin) and the unchanged v7 route.
# A real temporary Git history supplies the four production/overlay pairs. Only the native aapt2 call,
# the profile acceptance reader and the preservation-v2 reader (each validated by its own validator)
# and the role production pins (the real ones are not in the temporary history) are replaced.
# Reservation (T7b-1, R7 ruling): the live stage replaces only gh (a simulated Issue body/state), the live
# device state reader and the dexopt reader; the rest of Assert-P4LiveDeviceAdmission runs for real.
# Usage: pwsh -NoProfile -File docs/quality/validate-p4-layer-manifest-admission.ps1 `
#   -OutputDirectory <fresh lab path> [-CaseGroup Contract|Artifacts|Historical|Reservation]
param([Parameter(Mandatory)][string]$OutputDirectory,
    [ValidateSet('Contract', 'Artifacts', 'Historical', 'Reservation')][string]$CaseGroup = 'Contract')
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
$repository = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$lab = [IO.Path]::GetFullPath((Get-NenePixelLabPath 'evidence' -StartDirectory $PSScriptRoot)).TrimEnd('\', '/')
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $OutputDirectory.StartsWith($lab + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    [IO.File]::Exists($OutputDirectory) -or [IO.Directory]::Exists($OutputDirectory)) { throw 'Admission contract needs a fresh lab directory.' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$source = Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1'
$phase = 'nene-pixel-p4-layer-phase-verification-v1'
$old = 'nene-pixel-p4-indexed-cutover-verification-v7'
# The last commit before the phase admission opened; the v7 bodies must equal it.
$checkpoint = 'b450f9f'
$script:cases = [Collections.Generic.List[object]]::new()
$watch = [Diagnostics.Stopwatch]::StartNew()
$summary = [ordered]@{ status = 'failure'; group = $CaseGroup; cases = @(); elapsed_seconds = 0; source_sha256 = [ordered]@{} }
$utf8 = [Text.UTF8Encoding]::new($false)

function Case([string]$Name, [scriptblock]$Action, [switch]$Refuse, [string]$Expect) {
    $errorText = $null
    try { & $Action | Out-Null } catch { $errorText = $_.Exception.Message }
    $passed = if ($Refuse) { $null -ne $errorText -and ([string]::IsNullOrEmpty($Expect) -or $errorText.Contains($Expect)) }
        else { $null -eq $errorText }
    $script:cases.Add([ordered]@{ name = $Name; passed = $passed; expected_refusal = [bool]$Refuse; error = $errorText })
    if (-not $passed) { throw "Admission case failed: $Name : $errorText" }
}
function Require([bool]$Value, [string]$Message) { if (-not $Value) { throw $Message } }
function Clone($Value) { ConvertFrom-Json -AsHashtable -InputObject (ConvertTo-Json -InputObject $Value -Depth 40) }
function Sha([string]$Path) { (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant() }
function Write-Bytes([string]$Path, [byte[]]$Bytes) {
    [void][IO.Directory]::CreateDirectory((Split-Path -Parent $Path)); [IO.File]::WriteAllBytes($Path, $Bytes)
}
function Write-Text([string]$Path, [string]$Text) { Write-Bytes $Path $utf8.GetBytes($Text) }
function File-Record([string]$Path) {
    [ordered]@{ path = $Path; byte_count = (Get-Item -LiteralPath $Path).Length; sha256 = Sha $Path }
}
function Invoke-Git([string]$Directory, [string[]]$Arguments) {
    $output = @(& git.exe -C $Directory -c user.name=P4-T7a -c user.email=p4-t7a@example.invalid -c commit.gpgsign=false `
        -c core.autocrlf=false @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Fixture Git failed: $($Arguments -join ' '): $($output -join ' ')" }
    return $output
}
function New-Zip([string]$Path, [object[]]$Entries) {
    [void][IO.Directory]::CreateDirectory((Split-Path -Parent $Path))
    $zip = [IO.Compression.ZipFile]::Open($Path, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($item in $Entries) {
            $stream = $zip.CreateEntry($item.name).Open()
            try { $stream.Write($item.bytes, 0, $item.bytes.Length) } finally { $stream.Dispose() }
        }
    } finally { $zip.Dispose() }
    return $Path
}

try {
    foreach ($path in @($source, $PSCommandPath)) {
        $tokens = $null; $parseErrors = $null
        [void][Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$parseErrors)
        Require (@($parseErrors).Count -eq 0) "AST failed: $path"
        $summary.source_sha256[$path] = Sha $path
    }
    . $source
    $script:realCatalog = @(Get-P4LayerArtifactCatalog $phase)
    Require ((@($script:realCatalog.role) -join ',') -ceq 'baseline_single,baseline_layers16,baseline_underlay,candidate') 'Real catalog roles'

    # --- Replaced boundaries ------------------------------------------------------------------
    $script:productions = @{}
    function Get-P4LayerArtifactCatalog {
        param([Parameter(Mandatory)][string]$ProtocolId)
        if ($ProtocolId -cne 'nene-pixel-p4-layer-phase-verification-v1') { throw 'Unknown P4 frame group protocol.' }
        foreach ($entry in $script:realCatalog) {
            [ordered]@{ role = $entry.role; production_commit = $script:productions[$entry.role]; artifact_kinds = $entry.artifact_kinds }
        }
    }
    $script:apkKinds = @{}
    $script:dropProvider = $false
    function Invoke-P4Aapt2 {
        param([string]$Aapt2Path, [string[]]$Arguments)
        $kind = $script:apkKinds[[IO.Path]::GetFullPath($Arguments[-1])]
        if ($null -eq $kind) { throw "aapt2 failed: unknown synthetic APK $($Arguments[-1])" }
        $contract = $script:P4ArtifactContract[$kind]
        $package = if ($kind -ceq 'publication_test') { 'io.github.hideyukimori.nenepixel.adapters.persistence.test' }
            elseif ($contract.instrumented) { $contract.test_package } else { $contract.target_package }
        if ($Arguments[1] -ceq 'badging') {
            $lines = @("package: name='$package' versionCode='1'")
            if ($contract.debuggable) { $lines += 'application-debuggable' }
            return [string[]]$lines
        }
        $lines = @('E: manifest (line=1)', '  E: application (line=2)')
        if ($kind -ceq 'test_debug' -and -not $script:dropProvider) {
            $lines += @('    E: provider (line=4)',
                '      A: android:name(0x01010003)="io.github.hideyukimori.nenepixel.acceptance.AcceptanceDocumentsProvider" (Raw: "io.github.hideyukimori.nenepixel.acceptance.AcceptanceDocumentsProvider")',
                '      A: android:permission(0x01010006)="android.permission.MANAGE_DOCUMENTS" (Raw: "android.permission.MANAGE_DOCUMENTS")',
                '      A: android:exported(0x01010010)=(type 0x12)0xffffffff',
                '      A: android:authorities(0x01010018)="io.github.hideyukimori.nenepixel.test.acceptance.documents" (Raw: "io.github.hideyukimori.nenepixel.test.acceptance.documents")',
                '      A: android:grantUriPermissions(0x0101001b)=(type 0x12)0xffffffff',
                '      E: intent-filter (line=10)', '        E: action (line=11)',
                '          A: android:name(0x01010003)="android.content.action.DOCUMENTS_PROVIDER" (Raw: "android.content.action.DOCUMENTS_PROVIDER")')
        }
        if ($contract.instrumented) {
            $target = if ($kind -ceq 'publication_test') { $package } else { $contract.target_package }
            $lines += @('  E: instrumentation (line=20)',
                '    A: android:name(0x01010003)="androidx.test.runner.AndroidJUnitRunner" (Raw: "androidx.test.runner.AndroidJUnitRunner")',
                "    A: android:targetPackage(0x01010021)=""$target"" (Raw: ""$target"")")
        }
        return [string[]]$lines
    }
    function Read-BaselineProfileAcceptanceEvidence {
        param([string]$AcceptanceManifestPath, [string]$ExpectedAcceptanceManifestSha256, [string]$ExpectedSourceRevision,
            [string]$ExpectedAppApkSha256, [string]$ExpectedTestApkSha256, [string]$ExpectedPairManifestSha256,
            [string]$ExpectedCanonicalSha256, [string]$EvidenceRoot = '')
        if ((Sha $AcceptanceManifestPath) -cne $ExpectedAcceptanceManifestSha256) { throw 'Profile acceptance drift.' }
        return $null
    }
    $script:preservationReads = 0
    function Read-P4VerifiedPreservation([string]$Path, [Collections.IDictionary]$Context) {
        $script:preservationReads++
        $record = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
        if ($record.schema -cne 'nene-pixel-device-preservation-v2' -or $record.status -cne 'preserved' -or
            $record.serial -cne $Context.serial -or $record.package -cne $Context.package) { throw 'Preservation binding/status differs' }
        return [pscustomobject]@{ Record = $record; Path = $Path; Hash = Sha $Path }
    }

    # --- A real four-role history: P1..P4 linear production, one test overlay per role ---------
    $git = Join-Path $OutputDirectory 'history'
    [void][IO.Directory]::CreateDirectory($git)
    Invoke-Git $git @('init', '--quiet') | Out-Null
    Write-Text (Join-Path $git 'README.md') "synthetic`n"
    $required = @(Get-P4LayerRequiredMeasurementPaths $phase)
    $fixtureRoot = Join-Path $PSScriptRoot 'fixtures/p4-layer-phase'
    $roles = @($script:realCatalog.role)
    for ($i = 0; $i -lt $roles.Count; $i++) {
        Write-Text (Join-Path $git 'app/android/src/main/kotlin/Product.kt') "production $($i + 1)`n"
        Invoke-Git $git @('add', '--all') | Out-Null
        Invoke-Git $git @('commit', '--quiet', '-m', "production $($i + 1)") | Out-Null
        $script:productions[$roles[$i]] = [string](@(Invoke-Git $git @('rev-parse', 'HEAD'))[0])
    }
    $script:builds = @{}; $script:worktrees = @{}
    foreach ($role in $roles) {
        $tree = Join-Path $OutputDirectory "role-$role"
        Invoke-Git $git @('worktree', 'add', '--quiet', '-b', "overlay-$role", $tree, $script:productions[$role]) | Out-Null
        foreach ($path in $required) {
            $target = Join-Path $tree $path
            if ($path.StartsWith('docs/quality/fixtures/p4-layer-phase/')) {
                Write-Bytes $target ([IO.File]::ReadAllBytes((Join-Path $fixtureRoot (Split-Path -Leaf $path))))
            } else { Write-Text $target "overlay $path`n" }
        }
        Write-Text (Join-Path $tree 'docs/quality/P4_LAYER_PHASE_PROTOCOL.md') "phase protocol`n"
        Invoke-Git $tree @('add', '--all') | Out-Null
        Invoke-Git $tree @('commit', '--quiet', '-m', "overlay $role") | Out-Null
        $script:builds[$role] = [string](@(Invoke-Git $tree @('rev-parse', 'HEAD'))[0])
        $script:worktrees[$role] = $tree
    }

    $fixtures = @('maximum-layered.nenepixel', 'underlay-grid.png' | ForEach-Object {
        [ordered]@{ name = "assets/$_"; bytes = [IO.File]::ReadAllBytes((Join-Path $fixtureRoot $_)) } })
    $profBytes = $utf8.GetBytes('synthetic baseline.prof'); $profmBytes = $utf8.GetBytes('synthetic baseline.profm')
    function New-Apk([string]$Role, [string]$Kind, [string]$Revision, [string]$Name, [switch]$ForceFixtures, [switch]$OmitFixtures) {
        $entries = @([ordered]@{ name = 'META-INF/version-control-info.textproto'; bytes = $utf8.GetBytes("revision: `"$Revision`"`n") },
            [ordered]@{ name = 'classes.dex'; bytes = $utf8.GetBytes("$Role/$Kind") })
        if ($Kind -ceq 'app_release_like') {
            $entries += @([ordered]@{ name = 'assets/dexopt/baseline.prof'; bytes = $profBytes },
                [ordered]@{ name = 'assets/dexopt/baseline.profm'; bytes = $profmBytes })
        }
        $withFixtures = ($Kind -cin @('test_debug', 'publication_test') -and -not $OmitFixtures) -or $ForceFixtures
        if ($withFixtures) { $entries += $fixtures }
        $path = New-Zip (Join-Path $OutputDirectory "apk/$Role/$Name.apk") $entries
        $script:apkKinds[[IO.Path]::GetFullPath($path)] = $Kind
        $contract = $script:P4ArtifactContract[$Kind]
        $packages = if ($Kind -ceq 'publication_test') { @('io.github.hideyukimori.nenepixel.adapters.persistence.test') * 2 }
            else { @($contract.target_package, $contract.test_package) }
        $record = File-Record $path
        $record.variant = $contract.variant; $record.target_package = $packages[0]; $record.test_package = $packages[1]
        $record.embedded_revision = $Revision; $record.requested_dexopt = $contract.requested_dexopt
        $record.observed_dexopt = $contract.requested_dexopt
        return $record
    }
    function New-ProfileRecord([string]$Role) {
        $directory = Join-Path $OutputDirectory "profile/$Role"
        foreach ($name in @('source.txt', 'acceptance.json', 'pair.json', 'canonical.txt')) { Write-Text (Join-Path $directory $name) "$Role $name`n" }
        $sourceRecord = File-Record (Join-Path $directory 'source.txt')
        foreach ($ordinal in 1..2) {
            Write-Text (Join-Path $directory "invocation-$ordinal/manifest.json") (ConvertTo-Json @{ source_profile = @{
                raw_sha256 = $sourceRecord.sha256; raw_byte_count = $sourceRecord.byte_count } })
        }
        [ordered]@{ source = $sourceRecord; acceptance = File-Record (Join-Path $directory 'acceptance.json')
            pair = File-Record (Join-Path $directory 'pair.json'); canonical = File-Record (Join-Path $directory 'canonical.txt')
            packaged_prof_sha256 = Get-Sha256Hex $profBytes; packaged_profm_sha256 = Get-Sha256Hex $profmBytes
            generation_commit = $script:productions[$Role]; generation_app_sha256 = 'a' * 64; generation_test_sha256 = 'b' * 64 }
    }
    $manifestRoles = [ordered]@{}
    foreach ($entry in @(Get-P4LayerArtifactCatalog $phase)) {
        $role = $entry.role; $tree = $script:worktrees[$role]
        $expectedPaths = Get-P4ExpectedMeasurementPaths $tree $script:builds[$role] $role $phase
        $files = @([string[]]$expectedPaths | Sort-Object | ForEach-Object {
            $record = File-Record (Join-Path $tree $_); $record.relative_path = $_; $record })
        $artifacts = [ordered]@{}
        foreach ($kind in $entry.artifact_kinds) { $artifacts[$kind] = New-Apk $role $kind $script:builds[$role] $kind }
        $manifestRoles[$role] = [ordered]@{ worktree = $tree; production_commit = $entry.production_commit
            build_commit = $script:builds[$role]; production_tree_sha256 = Get-P4ProductionTreeHash $tree $entry.production_commit
            measurement_files = $files; measurement_sha256 = Get-P4FileInventoryHash $files
            artifacts = $artifacts; profile = New-ProfileRecord $role }
    }

    # --- Preservation-v2 first, manifest afterwards --------------------------------------------
    $serial = 'T7ASERIAL'; $session = 's145-t7a'; $experiment = 'e145-t7a'
    $phaseDirectory = Join-Path $OutputDirectory 'phase'
    function New-Preservation([string]$Name, [hashtable]$Change) {
        $record = [ordered]@{ schema = 'nene-pixel-device-preservation-v2'; status = 'preserved'; serial = $serial
            package = 'io.github.hideyukimori.nenepixel'; session = $session; experiment_id = $experiment
            created_utc = '2026-10-07T00:00:00.0000000+00:00' }
        if ($null -ne $Change) { foreach ($key in $Change.Keys) { $record[$key] = $Change[$key] } }
        $path = Join-Path $phaseDirectory $Name
        Write-Text $path (ConvertTo-Json $record)
        return [ordered]@{ path = $path; sha256 = Sha $path; session = $session }
    }
    $preservation = New-Preservation "p4-isolation-$session-$experiment-result.json" $null
    $toolsDirectory = Join-Path $OutputDirectory 'tools'
    $tools = [ordered]@{}
    foreach ($key in @('runner', 'analyzer', 'wrapper', 'validator', 'bounded_native', 'frame_collector', 'frame_validator', 'adb', 'aapt2')) {
        Write-Text (Join-Path $toolsDirectory "$key.txt") "tool $key`n"; $tools[$key] = File-Record (Join-Path $toolsDirectory "$key.txt")
    }
    $toolchain = [ordered]@{ jdk = '21'; jvm = '21'; gradle = '9'; agp = '9'; kotlin = '2'; compose = '1'
        android_build_tools = '36'; android_sdk = '36'; os = 'Windows'; wrapper = $tools.wrapper; catalog = $tools.runner
        locks = @($tools.analyzer); verification_metadata = $tools.validator }
    $device = [ordered]@{ serial = $serial; profile = 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36'; manufacturer = 'ALLDOCUBE'
        model = 'iPlay80miniPro'; product = 'p'; device = 'd'; api = 36; fingerprint = 'f'; security_patch = 's'
        display_mode = 'm'; width = 1920; height = 1200; refresh_rate = 60; rotation = 1; thermal = 0; power_save = '0'
        interactive = 'true'; usb_power = 'true'; battery = 100; locale = 'ja-JP'; user_rotation = 1; stay_awake = 1
        asset_preservation = $preservation; initial_viewport = $script:P4GeometryId }
    $outputRoot = Join-Path $OutputDirectory 'phase-output'
    $candidate = $manifestRoles.candidate
    $built = [ordered]@{ schema = 'nene-pixel-p4-layer-preflight-v1'
        protocol = (File-Record (Join-Path $script:worktrees.candidate 'docs/quality/P4_LAYER_PHASE_PROTOCOL.md'))
        created_utc = '2026-10-07T01:00:00.0000000+00:00'; experiment_id = $experiment; output_directory = $outputRoot
        roles = $manifestRoles; tools = $tools; toolchain = $toolchain; device = $device
        frame_experiment = [ordered]@{ directory = Join-Path $outputRoot 'frame'; hypothesis = 'h'; expected_affected_cost = 'c'
            correctness_risk = 'r'; stop_conditions = 's'; geometry = [ordered]@{
                baseline_single = [ordered]@{ canvas16_bounds = '[0,0][10,10]'; canvas256_bounds = '[0,0][20,20]' }
                baseline_layers16 = [ordered]@{ canvas256_bounds = '[0,0][20,20]' }
                baseline_underlay = [ordered]@{ canvas256_bounds = '[0,0][20,20]' }
                candidate = [ordered]@{ canvas16_bounds = '[0,0][10,10]'; canvas256_bounds = '[0,0][20,20]' } } }
        baseline_profile = [ordered]@{ generation_commit = $candidate.profile.generation_commit
            canonical_sha256 = $candidate.profile.canonical.sha256; packaged_prof_sha256 = $candidate.profile.packaged_prof_sha256
            packaged_profm_sha256 = $candidate.profile.packaged_profm_sha256 }
        slots = @(Get-P4SlotCatalog -ProtocolId $phase) }
    $built.protocol.id = $phase
    $manifest = Clone $built
    $candidateRoot = $script:worktrees.candidate
    function Artifacts($Value) { Assert-P4ManifestArtifacts $Value $candidateRoot -Stage slot }

    if ($CaseGroup -ceq 'Contract') {
        Case 'four-role 18-slot manifest passes the phase contract' {
            $before = $script:preservationReads
            Assert-P4ManifestContract $manifest
            Require ($script:preservationReads -eq $before + 1) 'The preservation-v2 record was not re-read'
        }
        Case 'v7 protocol id under the phase schema' { $bad = Clone $manifest; $bad.protocol.id = $old; Assert-P4ManifestContract $bad } -Refuse -Expect 'Wrong P4 layer manifest/protocol identity.'
        Case 'phase manifest under the v7 schema meets the v7 contract' { $bad = Clone $manifest; $bad.schema = 'nene-pixel-p4-indexed-preflight-v7'; Assert-P4ManifestContract $bad } -Refuse -Expect 'manifest.correctness'
        Case 'comparison role keyed as a build' {
            $bad = Clone $manifest; $bad.roles.baseline = $bad.roles.baseline_single; [void]$bad.roles.Remove('baseline_single'); Assert-P4ManifestContract $bad
        } -Refuse -Expect 'layer artifact role inventory set mismatch'
        Case 'extra artifact role' { $bad = Clone $manifest; $bad.roles.publication = $bad.roles.candidate; Assert-P4ManifestContract $bad } -Refuse -Expect 'Unexpected: publication'
        Case 'missing artifact role' { $bad = Clone $manifest; [void]$bad.roles.Remove('baseline_underlay'); Assert-P4ManifestContract $bad } -Refuse -Expect 'Missing: baseline_underlay'
        Case 'unregistered top-level field' { $bad = Clone $manifest; $bad.collector_contracts = @('x'); Assert-P4ManifestContract $bad } -Refuse -Expect 'Unexpected layer manifest field'
        Case 'phase tool missing' { $bad = Clone $manifest; [void]$bad.tools.Remove('aapt2'); Assert-P4ManifestContract $bad } -Refuse -Expect 'tools.aapt2'
        Case 'frame geometry missing' { $bad = Clone $manifest; [void]$bad.frame_experiment.Remove('geometry'); Assert-P4ManifestContract $bad } -Refuse -Expect 'frame_experiment.geometry'
        Case 'seventeen slots' { $bad = Clone $manifest; $bad.slots = @($bad.slots[0..16]); Assert-P4ManifestContract $bad } -Refuse -Expect 'Layer slot catalog drift'
        Case 'nineteen slots' { $bad = Clone $manifest; $bad.slots = @($bad.slots) + @(Clone $bad.slots[17]); Assert-P4ManifestContract $bad } -Refuse -Expect 'Layer slot catalog drift'
        Case 'slot order swapped' { $bad = Clone $manifest; $bad.slots = @($bad.slots[1], $bad.slots[0]) + @($bad.slots[2..17]); Assert-P4ManifestContract $bad } -Refuse -Expect 'Layer slot catalog drift'
        Case 'slot timeout drift' { $bad = Clone $manifest; $bad.slots[6].timeout_seconds = 301; Assert-P4ManifestContract $bad } -Refuse -Expect 'Layer slot catalog drift'
        Case 'slot artifact role swapped to candidate' { $bad = Clone $manifest; $bad.slots[0].artifact_role = 'candidate'; Assert-P4ManifestContract $bad } -Refuse -Expect 'Layer slot catalog drift'
        Case 'slot comparison role swapped' { $bad = Clone $manifest; $bad.slots[0].role = 'candidate'; Assert-P4ManifestContract $bad } -Refuse -Expect 'Layer slot catalog drift'
        Case 'preservation session differs from the verified record' {
            $bad = Clone $manifest; $bad.device.asset_preservation.session = 's145-other'; Assert-P4ManifestContract $bad
        } -Refuse -Expect 'does not pin its verified preservation-v2 record'
        Case 'preservation hash differs from the verified record' {
            $bad = Clone $manifest; $bad.device.asset_preservation.sha256 = 'e' * 64; Assert-P4ManifestContract $bad
        } -Refuse -Expect 'does not pin its verified preservation-v2 record'
        Case 'attested session without the record it names' {
            $bad = Clone $manifest; $bad.device.asset_preservation.path = Join-Path $phaseDirectory 'absent-result.json'; Assert-P4ManifestContract $bad
        } -Refuse
        Case 'record of another session pinned by its own hash' {
            $bad = Clone $manifest; $bad.device.asset_preservation = New-Preservation 'other-session.json' @{ session = 's145-other' }
            Assert-P4ManifestContract $bad
        } -Refuse -Expect 'does not pin its verified preservation-v2 record'
        Case 'record of another experiment' {
            $bad = Clone $manifest; $bad.device.asset_preservation = New-Preservation 'other-experiment.json' @{ experiment_id = 'e145-other' }
            Assert-P4ManifestContract $bad
        } -Refuse -Expect 'does not pin its verified preservation-v2 record'
        Case 'unverified (failed) preservation record' {
            $bad = Clone $manifest; $bad.device.asset_preservation = New-Preservation 'failed.json' @{ status = 'failure' }
            Assert-P4ManifestContract $bad
        } -Refuse -Expect 'Preservation binding/status differs'
        Case 'manifest created before its preservation' {
            $bad = Clone $manifest; $bad.created_utc = '2026-10-06T23:59:59.0000000+00:00'; Assert-P4ManifestContract $bad
        } -Refuse -Expect 'predates its preservation-v2 record'
        Case 'extra preservation attestation field' {
            $bad = Clone $manifest; $bad.device.asset_preservation.verified = 'yes'; Assert-P4ManifestContract $bad
        } -Refuse -Expect 'exactly path, sha256 and session'
        Case 'candidate profile is not the pinned generation' {
            $bad = Clone $manifest; $bad.baseline_profile.packaged_prof_sha256 = 'd' * 64; Assert-P4ManifestContract $bad
        } -Refuse -Expect 'not the pinned one-time generation'
        Case 'candidate profile generation commit differs' {
            $bad = Clone $manifest; $bad.roles.candidate.profile.generation_commit = 'c' * 40; Assert-P4ManifestContract $bad
        } -Refuse -Expect 'not the pinned one-time generation'
        Case 'malformed profile pin' { $bad = Clone $manifest; $bad.baseline_profile.canonical_sha256 = 'XYZ'; Assert-P4ManifestContract $bad } -Refuse -Expect 'Malformed Baseline Profile pin.'
        Case 'wrong device' { $bad = Clone $manifest; $bad.device.model = 'other'; Assert-P4ManifestContract $bad } -Refuse -Expect 'Wrong device/geometry contract.'
    } elseif ($CaseGroup -ceq 'Artifacts') {
        Case 'four roles pass offline admission at the slot stage' { Artifacts $manifest }
        Case 'v7 protocol id under the phase schema' { $bad = Clone $manifest; $bad.protocol.id = $old; Artifacts $bad } -Refuse -Expect 'Wrong P4 layer manifest/protocol identity.'
        Case 'protocol bytes drift from the candidate tree' {
            $copy = Join-Path $OutputDirectory 'other-protocol.md'; Write-Text $copy "other protocol`n"
            $bad = Clone $manifest; $record = File-Record $copy; foreach ($key in $record.Keys) { $bad.protocol[$key] = $record[$key] }; Artifacts $bad
        } -Refuse -Expect 'layer phase protocol bytes drifted'
        foreach ($entry in @(Get-P4LayerArtifactCatalog $phase)) {
            $role = $entry.role
            $extraKind = if ($role -ceq 'baseline_single') { 'test_debug' } elseif ($role -ceq 'candidate') { 'unknown_kind' } else { 'publication_test' }
            Case "missing app_debug APK record $role" { $bad = Clone $manifest; [void]$bad.roles[$role].artifacts.Remove('app_debug'); Artifacts $bad } -Refuse -Expect 'not the exact required set'
            Case "extra $extraKind APK $role" {
                $bad = Clone $manifest; $bad.roles[$role].artifacts[$extraKind] = Clone $bad.roles[$role].artifacts.app_debug; Artifacts $bad
            } -Refuse -Expect 'not the exact required set'
            Case "APK file absent $role" {
                $bad = Clone $manifest; $bad.roles[$role].artifacts.app_release_like.path = Join-Path $OutputDirectory "absent-$role.apk"; Artifacts $bad
            } -Refuse
            Case "APK SHA-256 mismatch $role" { $bad = Clone $manifest; $bad.roles[$role].artifacts.app_release_like.sha256 = 'f' * 64; Artifacts $bad } -Refuse -Expect 'Artifact drift'
            Case "production-tree hash mismatch $role" { $bad = Clone $manifest; $bad.roles[$role].production_tree_sha256 = '0' * 64; Artifacts $bad } -Refuse -Expect "Production tree mismatch: $role"
            Case "embedded revision is the production, not the build $role" {
                $bad = Clone $manifest
                $record = New-Apk $role 'app_debug' $script:productions[$role] 'app_debug-production-revision'
                $record.embedded_revision = $script:builds[$role]; $bad.roles[$role].artifacts.app_debug = $record; Artifacts $bad
            } -Refuse -Expect 'APK embedded revision does not match actual measurement build.'
            Case "fixture inside the production APK $role" {
                $bad = Clone $manifest; $bad.roles[$role].artifacts.app_debug = New-Apk $role 'app_debug' $script:builds[$role] 'app_debug-fixture' -ForceFixtures; Artifacts $bad
            } -Refuse -Expect 'Phase fixture leaked into production APK'
            Case "source inventory entry omitted $role" {
                $bad = Clone $manifest; $files = @($bad.roles[$role].measurement_files | Select-Object -Skip 1)
                $bad.roles[$role].measurement_files = $files; $bad.roles[$role].measurement_sha256 = Get-P4FileInventoryHash $files; Artifacts $bad
            } -Refuse -Expect 'measurement inventory set mismatch'
        }
        foreach ($kind in @('test_debug', 'publication_test')) {
            Case "fixture missing from candidate $kind" {
                $bad = Clone $manifest; $bad.roles.candidate.artifacts[$kind] = New-Apk 'candidate' $kind $script:builds.candidate "$kind-no-fixture" -OmitFixtures; Artifacts $bad
            } -Refuse -Expect 'Missing, duplicate or wrong-size phase fixture APK entry'
        }
        Case 'packaged test manifest lacks the provider' {
            $script:dropProvider = $true
            try { Artifacts $manifest } finally { $script:dropProvider = $false }
        } -Refuse -Expect 'Missing, duplicate or split phase DocumentsProvider declaration.'
        Case 'build worktree with a foreign extra file' {
            $extra = Join-Path $script:worktrees.baseline_layers16 'stray.txt'; Write-Text $extra "stray`n"
            try { Artifacts $manifest } finally { Remove-Item -LiteralPath $extra }
        } -Refuse -Expect 'Unclean build worktree: baseline_layers16'
        Case 'overlay lineage on a foreign production' {
            $bad = Clone $manifest; $bad.roles.baseline_underlay.production_commit = $script:productions.baseline_layers16; Artifacts $bad
        } -Refuse -Expect 'lineage differs: baseline_underlay'
        Case 'admission passes again after the refusals' { Artifacts $manifest }
    } elseif ($CaseGroup -ceq 'Reservation') {
        # --- Live boundaries: a simulated Issue tracker, device state and dexopt readers -------------
        $script:issues = @{
            '142' = @{ state = 'OPEN'; body = "agreement $old" }
            '145' = @{ state = 'OPEN'; body = "agreement $phase" } }
        $script:ghIssues = [Collections.Generic.List[string]]::new()
        function gh {
            $issue = [string]$args[2]; $script:ghIssues.Add($issue)
            $filter = [string]$args[-1]
            Require ($filter -cmatch 'contains\("([^"]+)"\)') 'agreement filter shape'
            $global:LASTEXITCODE = 0
            if (-not $script:issues.ContainsKey($issue)) { $global:LASTEXITCODE = 1; return }
            return "$($script:issues[$issue].state)`t$($script:issues[$issue].body.Contains($Matches[1]).ToString().ToLowerInvariant())"
        }
        $script:deviceReads = 0
        function Get-P4LiveDeviceState {
            param($AdbPath, $Serial, $Directory, $Stage, $RepositoryRoot)
            $script:deviceReads++
            $state = [ordered]@{}
            foreach ($key in $script:P4DeviceStateExactKeys) { $state[$key] = $device[$key] }
            $state.thermal = 0; $state.battery = 100; $state.rotation = 1
            return $state
        }
        $script:live = @{ 'io.github.hideyukimori.nenepixel' = 'speed-profile'; 'io.github.hideyukimori.nenepixel.test' = 'verify'
            'io.github.hideyukimori.nenepixel.adapters.persistence.test' = 'verify' }
        $script:dexoptReads = [Collections.Generic.List[string]]::new()
        function Get-P4PackageDexoptStatus {
            param($AdbPath, $Serial, $Directory, $Package, $Stage, $RepositoryRoot)
            $script:dexoptReads.Add($Package)
            return $script:live[$Package]
        }
        # The reserved manifest records the live dexopt of each artifact's package (the v7 convention).
        $reserved = Clone $manifest
        $reserved.tools.adb = Clone $reserved.tools.adb
        foreach ($role in @($reserved.roles.Keys)) {
            foreach ($kind in @($reserved.roles[$role].artifacts.Keys)) {
                $artifact = $reserved.roles[$role].artifacts[$kind]
                $artifact.observed_dexopt = $script:live[(Get-P4ArtifactDexoptPackage $artifact)]
            }
        }
        function Reserve($Value, [string]$Name) {
            $Value.output_directory = Join-Path $OutputDirectory "reserve-$Name"
            $Value.frame_experiment.directory = Join-Path $Value.output_directory 'frame'
            Assert-P4ManifestArtifacts $Value $candidateRoot -Stage reservation
        }
        function Record-Path([string]$Name) { Join-Path $OutputDirectory "reserve-$Name-preflight/reservation.json" }

        Case 'phase reservation passes: Issue #145, four-role dexopt, reservation record' {
            $script:ghIssues.Clear(); $script:dexoptReads.Clear()
            Reserve (Clone $reserved) 'pass'
            Require (($script:ghIssues -join ',') -ceq '145') "agreement Issue asked: $($script:ghIssues -join ',')"
            $record = Get-Content -LiteralPath (Record-Path 'pass') -Raw | ConvertFrom-Json -AsHashtable
            Require ($record.schema -ceq 'nene-pixel-p4-layer-reservation-v1' -and $record.agreement.issue -eq 145 -and
                $record.agreement.state -ceq 'OPEN' -and $record.agreement.protocol_id_in_body) 'agreement record'
            Require ((@($record.roles.Keys) -join ',') -ceq 'baseline_single,baseline_layers16,baseline_underlay,candidate') 'four roles recorded'
            foreach ($role in @($record.roles.Keys)) {
                Require ((@($record.roles[$role].Keys) -join ',') -ceq (@($reserved.roles[$role].artifacts.Keys) -join ',')) "kinds $role"
                foreach ($kind in @($record.roles[$role].Keys)) {
                    $entry = $record.roles[$role][$kind]; $artifact = $reserved.roles[$role].artifacts[$kind]
                    Require ($entry.sha256 -ceq $artifact.sha256 -and $entry.live_dexopt -ceq $script:live[$entry.package] -and
                        $entry.recorded_dexopt -ceq $entry.live_dexopt) "record $role.$kind"
                }
            }
            Require ($record.roles.baseline_single.app_release_like.live_dexopt -ceq 'speed-profile' -and
                $record.roles.candidate.app_release_like.requested_dexopt -ceq 'speed-profile') 'frame release-like is speed-profile'
            Require ($record.preservation.session -ceq $session -and $record.preservation.sha256 -ceq $reserved.device.asset_preservation.sha256) 'preservation session'
            Require ($script:dexoptReads.Count -eq 3) "one dexopt read per package: $($script:dexoptReads -join ',')"
        }
        Case 'reservation record is never overwritten' { Reserve (Clone $reserved) 'pass' } -Refuse -Expect 'Preflight device evidence already exists'
        Case 'agreement only on Issue #142 is not the phase agreement' {
            $saved = $script:issues['145']; $script:issues['145'] = @{ state = 'OPEN'; body = "agreement $old" }
            $script:issues['142'] = @{ state = 'OPEN'; body = "agreement $old $phase" }
            $reads = $script:deviceReads
            try { Reserve (Clone $reserved) 'issue-142' } finally { $script:issues['145'] = $saved; Require ($script:deviceReads -eq $reads) 'device touched without agreement' }
        } -Refuse -Expect 'Issue #145/layer protocol agreement is missing.'
        Case 'closed Issue #145' {
            $saved = $script:issues['145']; $script:issues['145'] = @{ state = 'CLOSED'; body = "agreement $phase" }
            try { Reserve (Clone $reserved) 'issue-closed' } finally { $script:issues['145'] = $saved }
        } -Refuse -Expect 'Issue #145/layer protocol agreement is missing.'
        foreach ($role in @($reserved.roles.Keys)) {
            Case "recorded dexopt differs from live: $role.app_release_like" {
                $bad = Clone $reserved; $bad.roles[$role].artifacts.app_release_like.observed_dexopt = 'verify'
                try { Reserve $bad "dexopt-$role" } finally { Require (-not (Test-Path -LiteralPath (Record-Path "dexopt-$role"))) 'record written' }
            } -Refuse -Expect "Recorded dexopt state for $role.app_release_like"
        }
        Case 'live dexopt changed under the recorded state' {
            $script:live['io.github.hideyukimori.nenepixel.test'] = 'speed-profile'
            try { Reserve (Clone $reserved) 'dexopt-live' } finally { $script:live['io.github.hideyukimori.nenepixel.test'] = 'verify' }
        } -Refuse -Expect 'Recorded dexopt state for baseline_layers16.test_debug'
        Case 'preservation session differs from the verified record' {
            $bad = Clone $reserved; $bad.device.asset_preservation.session = 's145-other'; $reads = $script:deviceReads
            try { Reserve $bad 'preservation' } finally { Require ($script:deviceReads -eq $reads) 'device touched' }
        } -Refuse -Expect 'does not pin its verified preservation-v2 record'
        Case 'New-P4ExperimentReservation reserves the phase manifest and its record' {
            $value = Clone $reserved; $value.output_directory = Join-Path $OutputDirectory 'reserve-file'
            $value.frame_experiment.directory = Join-Path $value.output_directory 'frame'
            $path = Join-Path $OutputDirectory 'reserve-file-manifest.json'; Write-Text $path (ConvertTo-Json $value -Depth 40)
            $root = New-P4ExperimentReservation $path $candidateRoot
            Require ((Sha (Join-Path $root 'preflight.json')) -ceq (Sha $path)) 'reserved copy'
            Require (Test-Path -LiteralPath (Record-Path 'file')) 'reservation record'
        }
        Case 'v7 unchanged: P4AgreementIssue 142 and the two-role, four-kind dexopt loop' {
            Require ($script:P4AgreementIssue -eq 142 -and $script:P4LayerAgreementIssue -eq 145) 'agreement Issues'
            $v7 = [ordered]@{ output_directory = Join-Path $OutputDirectory 'reserve-v7'; tools = Clone $reserved.tools; device = Clone $reserved.device
                roles = [ordered]@{ baseline = Clone $reserved.roles.candidate; candidate = Clone $reserved.roles.candidate } }
            $script:dexoptReads.Clear()
            Assert-P4LiveDeviceAdmission $v7 $candidateRoot
            Require ($script:dexoptReads.Count -eq 3) 'v7 dexopt reads'
            $v7.output_directory = Join-Path $OutputDirectory 'reserve-v7-bad'
            $v7.roles.candidate.artifacts.publication_test.observed_dexopt = 'speed-profile'
            $refused = $null; try { Assert-P4LiveDeviceAdmission $v7 $candidateRoot } catch { $refused = $_.Exception.Message }
            Require ($refused -match 'Recorded dexopt state for candidate\.publication_test') "v7 refusal: $refused"
            Require (-not (Test-Path -LiteralPath (Join-Path $OutputDirectory 'reserve-v7-preflight/reservation.json'))) 'v7 wrote a phase record'
        }
    } else {
        $v7 = [ordered]@{ schema = 'nene-pixel-p4-indexed-preflight-v7'; protocol = [ordered]@{ id = $old }
            created_utc = '2026-09-29T00:00:00Z'; experiment_id = 'e142-v7-contract'; output_directory = $outputRoot
            roles = [ordered]@{ baseline = [ordered]@{ marker = 'b' }; candidate = [ordered]@{ marker = 'c' } }
            tools = [ordered]@{}; toolchain = Clone $toolchain; device = Clone $device
            frame_experiment = [ordered]@{ directory = Join-Path $outputRoot 'frame'; hypothesis = 'h'; expected_affected_cost = 'c'
                correctness_risk = 'r'; stop_conditions = 's' }
            correctness = @([ordered]@{ marker = 'c' })
            collector_contracts = @($script:P4CollectorContractScopes | ForEach-Object { [ordered]@{ scope = $_ } })
            slots = @(Get-P4SlotCatalog) }
        foreach ($key in @('runner', 'analyzer', 'host_init', 'wrapper', 'validator', 'bounded_native', 'frame_collector',
                'frame_validator', 'adb', 'aapt2')) { $v7.tools[$key] = [ordered]@{ marker = $key } }
        foreach ($key in @('baseline_canvas16_bounds', 'candidate_canvas16_bounds')) { $v7.device[$key] = '[0,0][10,10]' }
        foreach ($key in @('baseline_canvas256_bounds', 'candidate_canvas256_bounds')) { $v7.device[$key] = '[0,0][20,20]' }
        $v7.device.no_sample_inspection = [ordered]@{ marker = 'n' }
        $v7.device.asset_preservation = [ordered]@{ marker = 'p' }
        $v7 = Clone $v7
        Case 'v7 manifest passes the unchanged v7 contract through the dispatcher' {
            $before = $script:preservationReads
            Assert-P4ManifestContract $v7
            Require ($script:preservationReads -eq $before) 'The v7 route read a preservation-v2 record'
        }
        Case 'v7 artifact admission reaches the unchanged v7 body' {
            $copy = Join-Path $OutputDirectory 'v7-protocol.md'; Write-Text $copy "not the accepted v7 protocol`n"
            $bad = Clone $v7; $record = File-Record $copy; foreach ($key in $record.Keys) { $bad.protocol[$key] = $record[$key] }
            Assert-P4ManifestArtifacts $bad $repository -Stage slot
        } -Refuse -Expect 'Accepted canonical protocol bytes drifted.'
        Case 'v7 contract still refuses the phase id' { $bad = Clone $v7; $bad.protocol.id = $phase; Assert-P4ManifestContract $bad } -Refuse -Expect 'Wrong P4 manifest/protocol identity.'
        Case 'v7 contract still refuses four artifact roles' {
            $bad = Clone $v7; $bad.roles = Clone $manifest.roles; Assert-P4ManifestContract $bad
        } -Refuse -Expect 'roles.baseline'
        Case 'v7 slot population unchanged (29)' { Require (@($v7.slots).Count -eq 29) 'v7 population' }
        Case "v7 admission bodies are byte-identical to $checkpoint" {
            $oldText = (& git.exe -C $repository show "${checkpoint}:docs/quality/measurements/p4-indexed-preflight.ps1") -join "`n"
            Require ($LASTEXITCODE -eq 0) 'Checkpoint source unavailable'
            $tokens = $null; $errors = $null
            $oldAst = [Management.Automation.Language.Parser]::ParseInput($oldText, [ref]$tokens, [ref]$errors)
            $newAst = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$errors)
            foreach ($pair in @(@('Assert-P4ManifestContract', 'Assert-P4IndexedManifestContract'),
                    @('Assert-P4ManifestArtifacts', 'Assert-P4IndexedManifestArtifacts'))) {
                $oldName = $pair[0]; $newName = $pair[1]
                $before = $oldAst.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $oldName }, $true)
                $after = $newAst.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $newName }, $true)
                Require ($before.Body.Extent.Text.Replace("`r`n", "`n") -ceq $after.Body.Extent.Text.Replace("`r`n", "`n")) "Changed v7 body: $oldName"
            }
        }
    }
    $summary.status = 'pass'
} finally {
    $watch.Stop(); $summary.elapsed_seconds = $watch.Elapsed.TotalSeconds; $summary.cases = $script:cases.ToArray()
    [IO.File]::WriteAllText((Join-Path $OutputDirectory 'results.json'), ($summary | ConvertTo-Json -Depth 12), $utf8)
}
"PASS: $($script:cases.Count) layer manifest admission cases ($CaseGroup) in $($watch.Elapsed.TotalSeconds) seconds"

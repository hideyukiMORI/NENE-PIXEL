Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../bounded-native-command.ps1')
. (Join-Path $PSScriptRoot '../baseline-profile-evidence.ps1')

$script:P4ProtocolId = 'nene-pixel-p4-indexed-cutover-verification-v3'
$script:P4ManifestSchema = 'nene-pixel-p4-indexed-preflight-v3'
$script:P4BaselineProduction = '2dd4e01e3bbe88967237cde4e28412d2962fd590'

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
        $slots.Add([ordered]@{ id = "command-$role"; lane = 'command'; role = $role; runner = 'command';
            run = 1; timeout_seconds = 300; warmups = 5; samples = 200 })
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
    $sequence = 0
    foreach ($slot in @('baseline-diagnostic', 'candidate-diagnostic', 'candidate-decision', 'baseline-decision')) {
        $sequence++
        $parts = $slot.Split('-')
        $diagnostic = $parts[1] -eq 'diagnostic'
        $slots.Add([ordered]@{ id = "frame-$sequence-$slot"; lane = 'frame'; role = $parts[0]; runner = $parts[1];
            run = $sequence; timeout_seconds = $(if ($diagnostic) { 300 } else { 600 });
            warmups = 5; samples = $(if ($diagnostic) { 10 } else { 50 }) })
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
    param([System.Collections.IDictionary]$Role, [string]$Name)
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
        $Role.build_commit -ceq $Role.production_commit)) { throw 'Baseline must be an immutable test overlay atop 2dd4e01.' }
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
    foreach ($kind in @('app_debug', 'test_debug', 'app_release_like', 'publication_test')) {
        if (-not $Role.artifacts.Contains($kind)) { throw "Missing $Name APK: $kind" }
        Assert-P4FileRecord $Role.artifacts[$kind] "$Name.$kind"
        Assert-P4RequiredKeys $Role.artifacts[$kind] @('variant', 'target_package', 'test_package',
            'embedded_revision', 'requested_dexopt', 'observed_dexopt') "$Name.$kind"
        if ($Role.artifacts[$kind].embedded_revision -cne $Role.build_commit) { throw "APK source mismatch: $Name.$kind" }
        $packaged = if ($kind -eq 'app_release_like') { $Role.profile } else { $null }
        Assert-P4ApkIdentity $Role.artifacts[$kind] $Role.build_commit $packaged
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
}

function Assert-P4ManifestContract {
    param([System.Collections.IDictionary]$Manifest)
    Assert-P4RequiredKeys $Manifest @('schema', 'protocol', 'experiment_id', 'output_directory', 'roles',
        'tools', 'toolchain', 'device', 'correctness', 'collector_contracts', 'slots') 'manifest'
    Assert-P4RequiredValue $Manifest 'manifest'
    if ($Manifest.schema -cne $script:P4ManifestSchema -or $Manifest.protocol.id -cne $script:P4ProtocolId) {
        throw 'Wrong P4 manifest/protocol identity.'
    }
    if ($Manifest.experiment_id -cnotmatch '^[a-z0-9][a-z0-9-]{2,63}$') { throw 'Invalid experiment ID.' }
    Assert-P4RequiredKeys $Manifest.roles @('baseline', 'candidate') 'roles'
    Assert-P4RequiredKeys $Manifest.toolchain @('jdk', 'jvm', 'gradle', 'agp', 'kotlin', 'compose',
        'android_build_tools', 'android_sdk', 'os', 'wrapper', 'catalog', 'locks', 'verification_metadata') 'toolchain'
    Assert-P4RequiredKeys $Manifest.tools @('runner', 'analyzer', 'host_init', 'wrapper', 'validator',
        'bounded_native', 'frame_collector', 'frame_validator', 'adb') 'tools'
    Assert-P4RequiredKeys $Manifest.device @('serial', 'profile', 'manufacturer', 'model', 'product', 'device',
        'api', 'fingerprint', 'security_patch', 'display_mode', 'width', 'height', 'refresh_rate', 'rotation',
        'thermal', 'power_save', 'interactive', 'usb_power', 'battery', 'locale', 'asset_preservation',
        'baseline_canvas16_bounds', 'baseline_canvas256_bounds', 'candidate_canvas16_bounds',
        'candidate_canvas256_bounds', 'initial_viewport', 'no_sample_inspection') 'device'
    if ($Manifest.device.profile -cne 'NENE-P2-ALLDOCUBE-IPL80MP-A16-API36' -or
        $Manifest.device.model -cne 'iPlay80miniPro' -or [int]$Manifest.device.api -ne 36 -or
        $Manifest.device.initial_viewport -cne 'initial-fit-centered-v1') { throw 'Wrong device/geometry contract.' }
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

function Assert-P4CollectionImplementationReady {
    # Deliberate handoff barrier: the accepted protocol requires every lane to be executable
    # before reserving even the first host slot. Remove only after the recorded integration
    # review blockers and the full no-device collector/analyzer matrix are resolved.
    throw 'P4 collection is not ready: device collector/frame analyzer and reviewed preflight/sealing gaps remain. No acceptance slot may be reserved or consumed.'
}

function Assert-P4ManifestArtifacts {
    param([System.Collections.IDictionary]$Manifest, [string]$RepositoryRoot)
    Assert-P4ManifestContract $Manifest
    Assert-P4CollectionImplementationReady
    Assert-P4FileRecord $Manifest.protocol 'protocol'
    if ((Get-FileSha256 (Join-Path $RepositoryRoot 'docs/quality/P4_INDEXED_CUTOVER_PROTOCOL.md')) -cne
        $Manifest.protocol.sha256) { throw 'Accepted canonical protocol bytes drifted.' }
    foreach ($role in @('baseline', 'candidate')) { Assert-P4RoleSource $Manifest.roles[$role] $role }
    foreach ($key in $Manifest.tools.Keys) { Assert-P4FileRecord $Manifest.tools[$key] "tools.$key" }
    foreach ($key in @('wrapper', 'catalog', 'verification_metadata')) {
        Assert-P4FileRecord $Manifest.toolchain[$key] "toolchain.$key"
    }
    foreach ($file in $Manifest.toolchain.locks) { Assert-P4FileRecord $file 'dependency lock' }
    Assert-P4FileRecord $Manifest.device.asset_preservation 'device.asset_preservation'
    Assert-P4FileRecord $Manifest.device.no_sample_inspection 'device.no_sample_inspection'
    foreach ($record in @($Manifest.correctness) + @($Manifest.collector_contracts)) {
        Assert-P4RequiredKeys $record @('source_commit', 'scope', 'command', 'exit_code', 'log') 'verification'
        if ([int]$record.exit_code -ne 0 -or $record.source_commit -cne $Manifest.roles.candidate.build_commit) {
            throw 'Correctness or collector contract evidence does not match candidate.'
        }
        Assert-P4FileRecord $record.log 'verification.log'
    }
    $issue = (& gh issue view 106 --repo hideyukiMORI/NENE-PIXEL --json body,state | ConvertFrom-Json)
    if ($LASTEXITCODE -ne 0 -or $issue.state -cne 'OPEN' -or -not $issue.body.Contains($script:P4ProtocolId)) {
        throw 'Issue/protocol agreement is missing.'
    }
}

function New-P4ExperimentReservation {
    param([string]$ManifestPath, [string]$RepositoryRoot)
    $bytes = [IO.File]::ReadAllBytes($ManifestPath)
    $Manifest = [Text.UTF8Encoding]::new($false, $true).GetString($bytes) | ConvertFrom-Json -AsHashtable
    Assert-P4ManifestArtifacts $Manifest $RepositoryRoot
    $root = [IO.Path]::GetFullPath($Manifest.output_directory)
    if (Test-Path -LiteralPath $root) { throw 'Experiment output already exists; attempt replacement is prohibited.' }
    New-Item -ItemType Directory -Path $root -ErrorAction Stop | Out-Null
    $stream = [IO.File]::Open((Join-Path $root 'preflight.json'), [IO.FileMode]::CreateNew,
        [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    return $root
}

# Issue #145: no-device artifact-role, source-inventory and APK-entry boundaries.
param([Parameter(Mandatory)][string]$OutputDirectory,
    [ValidateSet('CatalogAndPackaging', 'RoleBoundaries')][string]$CaseGroup = 'CatalogAndPackaging')
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
$repository = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$lab = [IO.Path]::GetFullPath((Get-NenePixelLabPath 'evidence' -StartDirectory $PSScriptRoot)).TrimEnd('\', '/')
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $OutputDirectory.StartsWith($lab + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    [IO.File]::Exists($OutputDirectory) -or [IO.Directory]::Exists($OutputDirectory)) { throw 'Artifact contract needs a fresh lab directory.' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$source = Join-Path $PSScriptRoot 'measurements/p4-indexed-preflight.ps1'
$phase = 'nene-pixel-p4-layer-phase-verification-v1'
$old = 'nene-pixel-p4-indexed-cutover-verification-v7'
$script:cases = [Collections.Generic.List[object]]::new()
$watch = [Diagnostics.Stopwatch]::StartNew()
$summary = [ordered]@{ status = 'failure'; cases = @(); elapsed_seconds = 0; source_sha256 = [ordered]@{} }

function Case([string]$Name, [scriptblock]$Action, [switch]$Refuse) {
    $errorText = $null
    try { & $Action | Out-Null } catch { $errorText = $_.Exception.ToString() }
    $passed = if ($Refuse) { $null -ne $errorText } else { $null -eq $errorText }
    $script:cases.Add([ordered]@{ name = $Name; passed = $passed; expected_refusal = [bool]$Refuse; error = $errorText })
    if (-not $passed) { throw "Artifact case failed: $Name : $errorText" }
}
function Require([bool]$Value, [string]$Message) { if (-not $Value) { throw $Message } }
function Clone($Value) { ConvertFrom-Json -AsHashtable -InputObject (ConvertTo-Json -InputObject $Value -Depth 30) }
function New-Zip([string]$Name, [object[]]$Entries) {
    $path = Join-Path $OutputDirectory "$Name.apk"
    $zip = [IO.Compression.ZipFile]::Open($path, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($item in $Entries) {
            $entry = $zip.CreateEntry($item.name)
            $stream = $entry.Open()
            try { $stream.Write($item.bytes, 0, $item.bytes.Length) } finally { $stream.Dispose() }
        }
    } finally { $zip.Dispose() }
    return $path
}
function Invoke-FixtureGit([string[]]$Arguments) {
    $output = @(& git.exe -C $script:fixtureTree @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Fixture Git failed: $($output -join ' ')" }
    return $output
}

try {
    foreach ($path in @($source, $PSCommandPath)) {
        $tokens = $null; $parseErrors = $null
        [void][Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$parseErrors)
        Require (@($parseErrors).Count -eq 0) "AST failed: $path"
        $summary.source_sha256[$path] = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
    }
    . $source
    if ($CaseGroup -ceq 'CatalogAndPackaging') {
    Case 'four exact roles and fixed productions' {
        $roles = @(Get-P4LayerArtifactCatalog $phase)
        Require (($roles.role -join ',') -ceq 'baseline_single,baseline_layers16,baseline_underlay,candidate') 'Role order'
        Require (($roles.production_commit -join ',') -ceq ('8120c06fae1a372b23d2a7af4f50aa2b9cdfeff9,' +
            '169b59287ca60e77e07ac91690450dd1a44b9ba4,f92b1006be5f7145a32258446474f8640b14b60b,' +
            '1f9bb1637058d3fa4a98122f4942406211bd1c69')) 'Production commits'
        foreach ($role in $roles) {
            $expected = 'app_debug,test_debug,app_release_like' + $(if ($role.role -ceq 'candidate') { ',publication_test' } else { '' })
            Require (($role.artifact_kinds -join ',') -ceq $expected) 'APK kind set'
        }
    }
    Case 'unified schedule reuses exact subcatalog objects in 12-10-2 order' {
        $schedule = @(Get-P4SlotCatalog $phase)
        $parts = @(Get-P4FrameSlotCatalog $phase) + @(Get-P4LayerMemorySlotCatalog $phase) + @(Get-P4LayerStorageSlotCatalog $phase)
        Require ($schedule.Count -eq 24) 'Slot count'
        Require ((ConvertTo-Json $schedule -Depth 12 -Compress) -ceq (ConvertTo-Json $parts -Depth 12 -Compress)) 'Schedule projection drift'
        Require (($schedule[12..21].id -join ',') -ceq (((1..5 | ForEach-Object { "memory-layers16-baseline-$_" }) +
            (1..5 | ForEach-Object { "memory-layers16-candidate-$_" })) -join ',')) 'Memory sequence'
        Require ($schedule[22].id -ceq 'publication-layers16-candidate' -and $schedule[23].id -ceq 'saf-save-layers16-candidate') 'Storage sequence'
        foreach ($slot in $schedule) { Require ((Resolve-P4ArtifactRole $phase $slot.id) -ceq $slot.artifact_role) 'Artifact resolution' }
    }
    Case 'old schedule default remains 29 nonframe slots with explicit-v7 equality' {
        $default = @(Get-P4SlotCatalog)
        Require ($default.Count -eq 29) 'Old population'
        Require ((ConvertTo-Json $default -Compress) -ceq (ConvertTo-Json @(Get-P4SlotCatalog $old) -Compress)) 'Default changed'
        Require ((Resolve-P4ArtifactRole $old 'frame-1-baseline-decision') -ceq 'baseline') 'Old frame resolution'
        Require ((Resolve-P4ArtifactRole $old 'publication-candidate') -ceq 'candidate') 'Old publication resolution'
    }
    foreach ($unknown in @('', 'unknown', $phase.ToUpperInvariant(), "$phase-extra")) {
        Case "unknown protocol $unknown" { Get-P4SlotCatalog $unknown } -Refuse
    }
    Case 'phase rejects old slot' { Resolve-P4ArtifactRole $phase 'frame-1-baseline-decision' } -Refuse
    Case 'old rejects phase slot' { Resolve-P4ArtifactRole $old 'frame-1-single-baseline-decision' } -Refuse

    # An actual temporary Git tree exercises derivation and per-path/blob checks, not a mocked listing.
    $script:fixtureTree = Join-Path $OutputDirectory 'source-tree'
    [void][IO.Directory]::CreateDirectory($fixtureTree)
    $required = @(Get-P4LayerRequiredMeasurementPaths $phase)
    $extra = @('app/android/src/androidTest/res/xml/provider-paths.xml',
        'adapters/persistence/src/androidTest/kotlin/AutosaveLayerPublicationAdmission.kt',
        'docs/quality/contract-extra.ps1', 'docs/quality/measurements/contract-extra.ps1')
    $excluded = @('app/android/src/main/kotlin/Product.kt', 'adapters/persistence/src/test/kotlin/UnrelatedTest.kt', 'README.md')
    foreach ($path in @($required + $extra + $excluded)) {
        $target = Join-Path $fixtureTree $path
        [void][IO.Directory]::CreateDirectory((Split-Path -Parent $target))
        [IO.File]::WriteAllText($target, "contract fixture $path`n", [Text.UTF8Encoding]::new($false))
    }
    Invoke-FixtureGit @('init', '--quiet') | Out-Null
    Invoke-FixtureGit @('add', '--all') | Out-Null
    Invoke-FixtureGit @('-c', 'user.name=P4 Contract', '-c', 'user.email=p4-contract@example.invalid',
        '-c', 'commit.gpgsign=false', 'commit', '--quiet', '-m', 'Synthetic source inventory contract') | Out-Null
    $commit = @(Invoke-FixtureGit @('rev-parse', 'HEAD'))[0]
    $files = @($required + $extra | ForEach-Object {
        $path = Join-Path $fixtureTree $_
        [ordered]@{ relative_path = $_; path = $path; byte_count = (Get-Item $path).Length
            sha256 = (Get-FileHash -Algorithm SHA256 $path).Hash.ToLowerInvariant() }
    })
    $roleRecord = [ordered]@{ worktree = $fixtureTree; build_commit = $commit; measurement_files = $files }
    Case 'complete tracked Java manifest assets resources and new publication prefix admitted' {
        $expected = Get-P4ExpectedMeasurementPaths $fixtureTree $commit 'candidate' $phase
        Require ($expected.Count -eq ($required.Count + $extra.Count)) 'Inventory population'
        foreach ($path in $required + $extra) { Require ($expected.Contains($path)) "Missing $path" }
        foreach ($path in $excluded) { Require (-not $expected.Contains($path)) "Unexpected $path" }
        Assert-P4MeasurementInventory $roleRecord 'candidate' $phase
    }
    foreach ($path in $required + $extra) {
        Case "omitted inventory entry $path" {
            $bad = Clone $roleRecord
            $bad.measurement_files = @($bad.measurement_files | Where-Object { $_.relative_path -cne $path })
            Assert-P4MeasurementInventory $bad 'candidate' $phase
        } -Refuse
    }
    Case 'extra inventory entry refused' {
        $bad = Clone $roleRecord; $file = Clone $files[0]
        $file.relative_path = 'README.md'; $file.path = Join-Path $fixtureTree 'README.md'
        $bad.measurement_files += $file
        Assert-P4MeasurementInventory $bad 'candidate' $phase
    } -Refuse
    Case 'duplicate inventory entry refused' {
        $bad = Clone $roleRecord; $bad.measurement_files += (Clone $files[0])
        Assert-P4MeasurementInventory $bad 'candidate' $phase
    } -Refuse
    Case 'wrong artifact role refused' { Get-P4ExpectedMeasurementPaths $fixtureTree $commit 'baseline' $phase } -Refuse
    Case 'mixed inventory protocol refused' { Get-P4ExpectedMeasurementPaths $fixtureTree $commit 'candidate' 'unknown' } -Refuse
    Case 'working source drift refused against build blob' {
        [IO.File]::AppendAllText($files[0].path, 'drift')
        Assert-P4MeasurementInventory $roleRecord 'candidate' $phase
    } -Refuse

    $fixtureRoot = Join-Path $PSScriptRoot 'fixtures/p4-layer-phase'
    $entries = @('maximum-layered.nenepixel', 'underlay-grid.png' | ForEach-Object {
        [ordered]@{ name = "assets/$_"; bytes = [IO.File]::ReadAllBytes((Join-Path $fixtureRoot $_)) }
    })
    $validApk = New-Zip 'valid-test-assets' $entries
    foreach ($kind in @('test_debug', 'publication_test')) {
        Case "exact pinned assets in $kind" { Assert-P4LayerApkFixtureEntries $phase $validApk $kind }
    }
    $productionApk = New-Zip 'empty-production' @([ordered]@{ name = 'classes.dex'; bytes = [byte[]]@(1, 2, 3) })
    foreach ($kind in @('app_debug', 'app_release_like')) {
        Case "production has no fixture $kind" { Assert-P4LayerApkFixtureEntries $phase $productionApk $kind }
        Case "production fixture leak $kind" { Assert-P4LayerApkFixtureEntries $phase $validApk $kind } -Refuse
    }
    for ($index = 0; $index -lt 2; $index++) {
        $other = 1 - $index
        $missing = New-Zip "missing-$index" @($entries[$other])
        Case "missing fixture $index" { Assert-P4LayerApkFixtureEntries $phase $missing 'test_debug' } -Refuse
        $duplicate = New-Zip "duplicate-$index" @($entries + @($entries[$index]))
        Case "duplicate fixture $index" { Assert-P4LayerApkFixtureEntries $phase $duplicate 'test_debug' } -Refuse
        $short = [ordered]@{ name = $entries[$index].name; bytes = [byte[]]@(1) }
        $wrongSize = New-Zip "short-$index" @($entries[$other], $short)
        Case "wrong fixture size $index" { Assert-P4LayerApkFixtureEntries $phase $wrongSize 'test_debug' } -Refuse
        $bytes = [byte[]]$entries[$index].bytes.Clone(); $bytes[0] = $bytes[0] -bxor 1
        $changed = New-Zip "changed-$index" @($entries[$other], [ordered]@{ name = $entries[$index].name; bytes = $bytes })
        Case "same-size fixture byte drift $index" { Assert-P4LayerApkFixtureEntries $phase $changed 'test_debug' } -Refuse
        $alias = New-Zip "case-alias-$index" @($entries + @([ordered]@{ name = $entries[$index].name.ToUpperInvariant(); bytes = $entries[$index].bytes }))
        Case "case alias $index" { Assert-P4LayerApkFixtureEntries $phase $alias 'test_debug' } -Refuse
    }

    $xml = @'
E: manifest (line=1)
  E: application (line=2)
    E: provider (line=4)
      A: android:name(0x01010003)="io.github.hideyukimori.nenepixel.acceptance.AcceptanceDocumentsProvider" (Raw: "io.github.hideyukimori.nenepixel.acceptance.AcceptanceDocumentsProvider")
      A: android:permission(0x01010006)="android.permission.MANAGE_DOCUMENTS" (Raw: "android.permission.MANAGE_DOCUMENTS")
      A: android:exported(0x01010010)=(type 0x12)0xffffffff
      A: android:authorities(0x01010018)="io.github.hideyukimori.nenepixel.test.acceptance.documents" (Raw: "io.github.hideyukimori.nenepixel.test.acceptance.documents")
      A: android:grantUriPermissions(0x0101001b)=(type 0x12)0xffffffff
      E: intent-filter (line=10)
        E: action (line=11)
          A: android:name(0x01010003)="android.content.action.DOCUMENTS_PROVIDER" (Raw: "android.content.action.DOCUMENTS_PROVIDER")
'@
    [IO.File]::WriteAllText((Join-Path $OutputDirectory 'provider-valid.xmltree.txt'), $xml)
    Case 'one exact provider with real grant and action semantics' { Assert-P4LayerProviderXmlTree $xml }
    foreach ($pair in @(@('AcceptanceDocumentsProvider', 'ForeignProvider'), @('test.acceptance.documents', 'test.foreign.documents'),
            @('MANAGE_DOCUMENTS', 'INTERNET'), @('exported(0x01010010)=(type 0x12)0xffffffff', 'exported(0x01010010)=(type 0x12)0x0'),
            @('grantUriPermissions(0x0101001b)=(type 0x12)0xffffffff', 'grantUriPermissions(0x0101001b)=(type 0x12)0x0'),
            @('android.content.action.DOCUMENTS_PROVIDER', 'android.intent.action.VIEW'))) {
        Case "provider drift $($pair[0])" { Assert-P4LayerProviderXmlTree $xml.Replace($pair[0], $pair[1]) } -Refuse
    }
    Case 'duplicate provider' { Assert-P4LayerProviderXmlTree ($xml + "`n" + $xml) } -Refuse
    Case 'nested attribute cannot substitute missing provider attribute' {
        Assert-P4LayerProviderXmlTree $xml.Replace('      A: android:exported', '          A: android:exported')
    } -Refuse
    Case 'empty packaged provider declaration' { Assert-P4LayerProviderXmlTree '' } -Refuse
    } else {
        $catalog = @(Get-P4LayerArtifactCatalog $phase)
        $roles = [ordered]@{}
        foreach ($entry in $catalog) {
            $artifacts = [ordered]@{}
            foreach ($kind in $entry.artifact_kinds) { $artifacts[$kind] = [ordered]@{ path = 'not-read' } }
            $roles[$entry.role] = [ordered]@{ worktree = $repository; production_commit = $entry.production_commit
                build_commit = '8e11f931c8f16c15cdaa94fab520319ca3bfb8a8'; artifacts = $artifacts }
        }
        $lineage = [ordered]@{ protocol = [ordered]@{ id = $phase }; roles = $roles }
        Case 'actual Git four-role ancestry on fixed production commits' { Assert-P4GitLineage $lineage }
        foreach ($entry in $catalog) {
            Case "wrong fixed production $($entry.role)" {
                $bad = Clone $roles[$entry.role]; $bad.production_commit = ('a' * 40)
                Assert-P4RoleSource $bad $entry.role 'not-called' $phase
            } -Refuse
            Case "production is not a measurement overlay $($entry.role)" {
                $bad = Clone $roles[$entry.role]; $bad.build_commit = $bad.production_commit
                Assert-P4RoleSource $bad $entry.role 'not-called' $phase
            } -Refuse
            Case "missing required APK kind $($entry.role)" {
                $bad = Clone $roles[$entry.role]; $bad.artifacts.Remove('test_debug')
                Assert-P4RoleSource $bad $entry.role 'not-called' $phase
            } -Refuse
            Case "extra APK kind $($entry.role)" {
                $bad = Clone $roles[$entry.role]; $bad.artifacts.extra = [ordered]@{ path = 'not-read' }
                Assert-P4RoleSource $bad $entry.role 'not-called' $phase
            } -Refuse
            Case "foreign overlay lineage $($entry.role)" {
                $bad = Clone $lineage; $bad.roles[$entry.role].build_commit = ('a' * 40)
                Assert-P4GitLineage $bad
            } -Refuse
        }
        Case 'unknown role refused before source access' { Assert-P4RoleSource $roles.candidate 'baseline' 'not-called' $phase } -Refuse
        Case 'unknown role-source protocol refused' { Assert-P4RoleSource $roles.candidate 'candidate' 'not-called' 'unknown' } -Refuse
        Case 'historical still requires compiled inventory' {
            $role = Clone $roles.candidate
            $role.production_tree_sha256 = 'a' * 64; $role.measurement_files = @('present')
            $role.measurement_sha256 = 'a' * 64; $role.profile = [ordered]@{ present = $true }
            try { Assert-P4RoleSource $role 'candidate' 'not-called' $old; throw 'Unexpected success' }
            catch { Require ($_.Exception.Message -ceq 'Missing preflight field: candidate.compiled_files') 'Wrong historical boundary' }
        }
        Case 'unchanged production hash profile and APK identity routines match storage checkpoint' {
            $oldText = (& git.exe -C $repository show '8e11f931c8f16c15cdaa94fab520319ca3bfb8a8:docs/quality/measurements/p4-indexed-preflight.ps1') -join "`n"
            Require ($LASTEXITCODE -eq 0) 'Historical source unavailable'
            $tokens = $null; $errors = $null
            $oldAst = [Management.Automation.Language.Parser]::ParseInput($oldText, [ref]$tokens, [ref]$errors)
            $newAst = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$errors)
            foreach ($name in @('Get-P4ProductionTreeHash', 'Assert-P4ApkIdentity', 'Assert-P4ProfileSourceBinding',
                    'Assert-P4TrackedBlob', 'Assert-P4NoReparsePath', 'Assert-P4ManifestContract', 'Assert-P4ManifestArtifacts')) {
                $find = { param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $name }
                $before = $oldAst.Find($find, $true).Extent.Text.Replace("`r`n", "`n")
                $after = $newAst.Find($find, $true).Extent.Text.Replace("`r`n", "`n")
                Require ($before -ceq $after) "Changed reused function: $name"
            }
        }
        Case 'full layer phase still refuses legacy manifest admission' {
            $minimal = [ordered]@{ schema = 'nene-pixel-p4-layer-preflight-v1'; protocol = [ordered]@{ id = $phase } }
            try { Assert-P4ManifestContract $minimal; throw 'Unexpected success' }
            catch { Require ($_.Exception.Message -ne 'Unexpected success') 'Collection barrier removed' }
        }
    }
    $summary.status = 'pass'
} finally {
    $watch.Stop(); $summary.elapsed_seconds = $watch.Elapsed.TotalSeconds; $summary.cases = $script:cases.ToArray()
    [IO.File]::WriteAllText((Join-Path $OutputDirectory 'results.json'), ($summary | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
}
"PASS: $($script:cases.Count) layer artifact contract cases in $($watch.Elapsed.TotalSeconds) seconds"

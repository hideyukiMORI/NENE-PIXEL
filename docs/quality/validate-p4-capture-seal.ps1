# Device-free #145 read-only capture seal and direct completed-chain consumer checks.
param([string]$OutputDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'measurements/nene-pixel-lab.ps1')
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Get-NenePixelLabPath ('evidence/145-capture-seal/' +
        (Get-Date -Format 'yyyyMMddTHHmmssfff') + '-' + [guid]::NewGuid().ToString('N')) -StartDirectory $PSScriptRoot
}
$evidenceRoot = [IO.Path]::GetFullPath((Get-NenePixelLabPath 'evidence' -StartDirectory $PSScriptRoot)).TrimEnd('\', '/')
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $OutputDirectory.StartsWith($evidenceRoot + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) { throw 'Capture seal evidence must be inside lab evidence.' }
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Capture seal evidence already exists.' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$helper = Join-Path $PSScriptRoot 'measurements/p4-indexed-capture-seal.ps1'
$wrapper = Join-Path $PSScriptRoot 'measurements/invoke-p4-indexed-slot.ps1'
$script:checks = 0
$script:cases = [Collections.Generic.List[object]]::new()
$timer = [Diagnostics.Stopwatch]::StartNew()
$result = [ordered]@{
    schema = 'nene-pixel-p4-capture-seal-validator-v1'; status = 'FAIL'; checks = 0; elapsed_seconds = 0
    command = 'pwsh -NoProfile -File docs/quality/validate-p4-capture-seal.ps1'
    runtime = $PSVersionTable.PSVersion.ToString(); source_sha256 = [ordered]@{}; direct_imports = [ordered]@{}
    cases = $script:cases
}
function Check([bool]$Condition, [string]$Name) {
    if (-not $Condition) { throw "FAIL: $Name" }
    $script:checks++
}
function Write-Once([string]$Path, [string]$Text) {
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Path))
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Text)
        $stream.Write($bytes, 0, $bytes.Length)
    } finally { $stream.Dispose() }
}
function Write-JsonOnce([string]$Path, $Value) {
    Write-Once $Path (ConvertTo-Json -InputObject $Value -Depth 20)
}
function Inventory([string]$Directory) {
    # Do not traverse junctions: retain their link target and attributes as inventory entries.
    $rows = [Collections.Generic.List[object]]::new()
    $pending = [Collections.Generic.Stack[string]]::new(); $pending.Push($Directory)
    while ($pending.Count -gt 0) {
        foreach ($item in @(Get-ChildItem -LiteralPath $pending.Pop() -Force)) {
            $row = [ordered]@{ path = [IO.Path]::GetRelativePath($Directory, $item.FullName)
                attributes = [string]$item.Attributes }
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { $row.target = @($item.Target) }
            elseif ($item.PSIsContainer) { $pending.Push($item.FullName) }
            else { $row.bytes = $item.Length; $row.sha256 = Get-FileSha256 $item.FullName }
            $rows.Add($row)
        }
    }
    return ConvertTo-Json -InputObject @($rows | Sort-Object { $_.path }) -Depth 8 -Compress
}
function Boundary($Fixture, [scriptblock]$Action, [bool]$Accepted, [string]$MessagePattern = '') {
    $before = Inventory $Fixture.root
    $refused = $false; $value = $null; $message = ''
    try { $value = & $Action } catch { $refused = $true; $message = $_.Exception.Message }
    $after = Inventory $Fixture.root
    Write-Once (Join-Path $OutputDirectory "inventory/$($Fixture.name)-before.json") $before
    Write-Once (Join-Path $OutputDirectory "inventory/$($Fixture.name)-after.json") $after
    Check ($before -ceq $after) "$($Fixture.name): read boundary writes nothing"
    Check ($refused -eq (-not $Accepted)) "$($Fixture.name): expected acceptance=$Accepted ($message)"
    if (-not $Accepted -and $MessagePattern.Length -gt 0) {
        Check ($message -match $MessagePattern) "$($Fixture.name): expected refusal boundary ($message)"
    }
    $script:cases.Add([ordered]@{ name = $Fixture.name; accepted = $Accepted; refusal = $message
        inventory_before_sha256 = Get-Sha256Hex ([Text.Encoding]::UTF8.GetBytes($before))
        inventory_after_sha256 = Get-Sha256Hex ([Text.Encoding]::UTF8.GetBytes($after)) })
    return $value
}
function New-Fixture([string]$Name, [scriptblock]$Edit = {}, [string]$Payload = 'payload', [string]$SlotLeaf = 'slot') {
    $root = Join-Path $OutputDirectory $Name
    Check (-not (Test-Path -LiteralPath $root)) "${Name}: fresh fixture"
    $slot = Join-Path $root $SlotLeaf; $external = Join-Path $root 'frame-root'
    [void][IO.Directory]::CreateDirectory($slot); [void][IO.Directory]::CreateDirectory($external)
    Write-Once (Join-Path $slot 'payload.txt') $Payload
    Write-Once (Join-Path $slot 'empty.bin') ''
    Write-Once (Join-Path $external 'frame.txt') 'frame'
    $seal = [ordered]@{ schema = 'nene-pixel-p4-capture-seal-v1'; slot_id = 'frame-1-baseline-decision'
        files = @(
            [ordered]@{ relative_path = 'payload.txt'; byte_count = 7; sha256 = Get-Sha256Hex ([Text.Encoding]::UTF8.GetBytes('payload')) },
            [ordered]@{ relative_path = 'empty.bin'; byte_count = 0; sha256 = (Get-FileSha256 (Join-Path $slot 'empty.bin')) },
            [ordered]@{ relative_path = 'external/frame-slot/frame.txt'; byte_count = 5; sha256 = (Get-FileSha256 (Join-Path $external 'frame.txt')) })
        external_directories = @([ordered]@{ name = 'frame-slot'; path = $external }) }
    $fixture = [ordered]@{ name = $Name; root = $root; slot = $slot; external = $external; seal = $seal
        seal_text = $null; expected_hash = $null; expected_slot = $seal.slot_id }
    & $Edit $fixture
    $text = if ($null -ne $fixture.seal_text) { $fixture.seal_text } else { ConvertTo-Json -InputObject $fixture.seal -Depth 20 }
    Write-Once (Join-Path $slot 'capture-seal.json') $text
    if ($null -eq $fixture.expected_hash) { $fixture.expected_hash = Get-FileSha256 (Join-Path $slot 'capture-seal.json') }
    return $fixture
}
function Read-Fixture($Fixture, [bool]$Accepted = $false, [string]$Pattern = '') {
    return Boundary $Fixture {
        Read-P4VerifiedCaptureSeal -SlotDirectory $Fixture.slot -SlotId $Fixture.expected_slot -ExpectedSha256 $Fixture.expected_hash
    } $Accepted $Pattern
}
function Negative([string]$Name, [scriptblock]$Edit, [string]$Pattern = '') {
    Read-Fixture (New-Fixture $Name $Edit) $false $Pattern | Out-Null
}
function New-Chain([string]$Name, [string]$Drift = '') {
    $f = New-Fixture -Name $Name -Payload $(if ($Drift -ceq 'file') { 'changed' } else { 'payload' }) `
        -SlotLeaf 'frame-1-baseline-decision'
    $analysis = [ordered]@{ capture_seal_sha256 = $f.expected_hash; verdict = 'baseline-recorded'; complete_run = $true }
    if ($Drift -ceq 'binding') { $analysis.capture_seal_sha256 = 'b' * 64 }
    Write-JsonOnce (Join-Path $f.slot 'analysis.json') $analysis
    Write-JsonOnce (Join-Path $f.slot 'restoration.json') ([ordered]@{ schema = 'nene-pixel-p4-restoration-v1'; status = 'restored' })
    Write-JsonOnce (Join-Path $f.slot 'worktree-after.json') ([ordered]@{ schema = 'nene-pixel-p4-worktree-state-v1'; commit = 'a' * 40; clean = $true })
    $completed = [ordered]@{ slot_id = $f.expected_slot; verdict = 'baseline-recorded'; status = 'completed'
        protocol_id = $script:P4ProtocolId; preflight_sha256 = 'a' * 64; complete_run = $true
        capture_seal_sha256 = $f.expected_hash; analysis_sha256 = Get-FileSha256 (Join-Path $f.slot 'analysis.json')
        restoration_sha256 = Get-FileSha256 (Join-Path $f.slot 'restoration.json')
        worktree_after_sha256 = Get-FileSha256 (Join-Path $f.slot 'worktree-after.json') }
    switch ($Drift) {
        'seal' { $completed.capture_seal_sha256 = 'b' * 64 }
        'analysis' { $completed.analysis_sha256 = 'b' * 64 }
        'restoration' { $completed.restoration_sha256 = 'b' * 64 }
        'worktree' { $completed.worktree_after_sha256 = 'b' * 64 }
        'incomplete' { $completed.complete_run = $false }
    }
    Write-JsonOnce (Join-Path $f.slot 'completed.json') $completed
    return $f
}
try {
    $relativeSources = @('measurements/p4-indexed-capture-seal.ps1', 'measurements/invoke-p4-indexed-slot.ps1',
        'measurements/p4-indexed-preflight.ps1', 'baseline-profile-evidence.ps1', 'bounded-native-command.ps1',
        'measurements/p4-indexed-device-state.ps1', 'measurements/android-window-state.ps1',
        'measurements/m2-package-dexopt.ps1', 'measurements/p4-indexed-device-lanes.ps1',
        'measurements/nene-pixel-lab.ps1', 'validate-p4-capture-seal.ps1')
    foreach ($relative in $relativeSources) {
        $path = Join-Path $PSScriptRoot $relative
        $tokens = $null; $errors = $null
        $ast = [Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
        Check ($errors.Count -eq 0) "AST: $relative"
        $result.source_sha256[$relative] = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
        $result.direct_imports[$relative] = @($ast.FindAll({ param($node)
            $node -is [Management.Automation.Language.CommandAst] -and
            $node.InvocationOperator -eq [Management.Automation.Language.TokenKind]::Dot }, $false) | ForEach-Object { $_.Extent.Text })
    }
    . $wrapper -ManifestPath 'device-free-dummy-manifest.json' -SlotId 'device-free-dummy-slot'
    $f = New-Fixture 'valid-external-and-empty'
    $verified = Read-Fixture $f $true
    Check ($verified.files.Count -eq 3) 'all actual files returned'
    Check ($verified.files[1].byte_count -eq 0) 'zero-byte file accepted'
    Check ((Resolve-P4SealedFilePath $verified $f.slot 'payload.txt') -ceq (Join-Path $f.slot 'payload.txt')) 'local resolution'
    Check ((Resolve-P4SealedFilePath $verified $f.slot 'external/frame-slot/frame.txt') -ceq
        (Join-Path $f.external 'frame.txt')) 'external frame-root resolution'
    $f = New-Fixture 'valid-local-only' { param($f)
        $f.seal.files = @($f.seal.files[0], $f.seal.files[1]); $f.seal.external_directories = @()
    }
    Read-Fixture $f $true | Out-Null
    $f = [ordered]@{ name = 'missing-seal'; root = Join-Path $OutputDirectory 'missing-seal' }
    $f.slot = Join-Path $f.root 'slot'; $f.expected_slot = 'frame-1-baseline-decision'; $f.expected_hash = 'a' * 64
    [void][IO.Directory]::CreateDirectory($f.slot)
    Write-Once (Join-Path $f.slot 'payload.txt') 'payload'
    Read-Fixture $f $false 'capture seal' | Out-Null
    Negative 'seal-hash-drift' { param($f) $f.expected_hash = 'a' * 64 } 'capture seal'
    Negative 'seal-hash-format' { param($f) $f.expected_hash = 'z' * 64 }
    Negative 'schema-drift' { param($f) $f.seal.schema = 'foreign' } 'identity or inventory'
    Negative 'slot-drift' { param($f) $f.expected_slot = 'foreign' } 'identity or inventory'
    foreach ($field in @('schema', 'slot_id')) {
        Negative "$field-type" { param($f) $f.seal[$field] = 1 } 'identity or inventory'
        Negative "$field-missing" { param($f) $f.seal.Remove($field) } 'identity or inventory'
    }
    foreach ($field in @('files', 'external_directories')) {
        foreach ($kind in @('scalar', 'string', 'null', 'missing')) {
            Negative "$field-$kind" { param($f)
                switch ($kind) {
                    'scalar' { $f.seal[$field] = $f.seal[$field][0] }
                    'string' { $f.seal[$field] = 'foreign' }
                    'null' { $f.seal[$field] = $null }
                    'missing' { $f.seal.Remove($field) }
                }
            } 'identity or inventory'
        }
    }
    Negative 'empty-files' { param($f) $f.seal.files = @() } 'identity or inventory'
    Negative 'json-array-root' { param($f) $f.seal = @($f.seal) } 'identity or inventory'
    Negative 'invalid-json' { param($f) $f.seal_text = '{' }
    Negative 'null-json' { param($f) $f.seal_text = 'null' }
    Negative 'file-record-scalar' { param($f) $f.seal.files[0] = 'foreign' } 'malformed'
    foreach ($field in @('relative_path', 'sha256', 'byte_count')) {
        Negative "file-$field-missing" { param($f) $f.seal.files[0].Remove($field) }
    }
    Negative 'file-path-type' { param($f) $f.seal.files[0].relative_path = 1 } 'malformed'
    Negative 'file-hash-type' { param($f) $f.seal.files[0].sha256 = 1 } 'malformed'
    Negative 'file-hash-format' { param($f) $f.seal.files[0].sha256 = 'A' * 64 } 'malformed'
    Negative 'file-hash-drift' { param($f) $f.seal.files[0].sha256 = 'b' * 64 } 'sealed file changed'
    Negative 'file-size-drift' { param($f) $f.seal.files[0].byte_count = 8 } 'sealed file changed'
    Read-Fixture (New-Fixture 'file-payload-drift' {} 'changed') $false 'sealed file changed' | Out-Null
    Negative 'missing-file' { param($f) $f.seal.files[0].relative_path = 'missing.txt' } 'sealed file is missing'
    $badPaths = @('/rooted', '../payload.txt', 'sub/../payload.txt', 'sub\payload.txt', 'C:/payload.txt',
        '', '.', './payload.txt', 'sub//payload.txt', 'payload.txt/', 'payload.txt.', 'payload.txt ',
        "payload`n.txt", 'external/frame-slot', 'external/undefined/frame.txt')
    for ($i = 0; $i -lt $badPaths.Count; $i++) {
        Negative "relative-path-$i" { param($f) $f.seal.files[0].relative_path = $badPaths[$i] }
        $pathFixture = New-Fixture "resolver-path-$i"
        Boundary $pathFixture { Resolve-P4SealedFilePath $pathFixture.seal $pathFixture.slot $badPaths[$i] } $false | Out-Null
    }
    Negative 'duplicate-file-case' { param($f) $f.seal.files += [ordered]@{
        relative_path = 'PAYLOAD.txt'; byte_count = 7; sha256 = $f.seal.files[0].sha256 } } 'duplicate'
    Negative 'external-record-scalar' { param($f) $f.seal.external_directories[0] = 'foreign' } 'external evidence root'
    foreach ($field in @('name', 'path')) {
        Negative "external-$field-type" { param($f) $f.seal.external_directories[0][$field] = 1 } 'external evidence root'
        Negative "external-$field-missing" { param($f) $f.seal.external_directories[0].Remove($field) } 'external evidence root'
    }
    Negative 'external-duplicate-name' { param($f) $f.seal.external_directories += $f.seal.external_directories[0] } 'external evidence root'
    Negative 'external-name-format' { param($f) $f.seal.external_directories[0].name = '../frame-slot' } 'external evidence root'
    Negative 'external-relative-root' { param($f) $f.seal.external_directories[0].path = 'frame-root' } 'external evidence root'
    Negative 'external-missing-root' { param($f) $f.seal.external_directories[0].path = Join-Path $f.root 'missing' } 'external evidence root'
    Negative 'external-file-root' { param($f) $f.seal.external_directories[0].path = Join-Path $f.slot 'payload.txt' } 'external evidence root'
    Negative 'aliased-local-external-file' { param($f)
        $f.seal.external_directories += [ordered]@{ name = 'alias'; path = $f.slot }
        $f.seal.files += [ordered]@{ relative_path = 'external/alias/payload.txt'; byte_count = 7; sha256 = $f.seal.files[0].sha256 }
    } 'aliased sealed file'
    Negative 'aliased-external-file' { param($f)
        $f.seal.external_directories += [ordered]@{ name = 'alias'; path = $f.external }
        $f.seal.files += [ordered]@{ relative_path = 'external/alias/frame.txt'; byte_count = 5; sha256 = $f.seal.files[2].sha256 }
    } 'aliased sealed file'
    $badSizes = @('7', 7.25, $true, -1, $null, '__overflow__', @(), @(7), '07')
    for ($i = 0; $i -lt $badSizes.Count; $i++) {
        Negative "byte-count-$i" { param($f)
            $f.seal.files[0].byte_count = $badSizes[$i]
            if ($badSizes[$i] -ceq '__overflow__') {
                $f.seal_text = (ConvertTo-Json -InputObject $f.seal -Depth 20).Replace('"__overflow__"', '9223372036854775808')
            }
        } 'invalid sealed byte count'
    }
    Check $IsWindows 'declared Windows junction checks require Windows'
    Negative 'junction-file-ancestor' { param($f)
        New-Item -ItemType Junction -Path (Join-Path $f.slot 'linked') -Target $f.external | Out-Null
        $f.seal.files[0] = [ordered]@{ relative_path = 'linked/frame.txt'; byte_count = 5; sha256 = $f.seal.files[2].sha256 }
    } 'path is a link'
    Negative 'junction-external-root' { param($f)
        $linked = Join-Path $f.root 'linked-root'
        New-Item -ItemType Junction -Path $linked -Target $f.external | Out-Null
        $f.seal.external_directories[0].path = $linked
    } 'path is a link'
    $f = New-Fixture 'junction-slot-ancestor'
    $linked = Join-Path $f.root 'linked-slot'
    New-Item -ItemType Junction -Path $linked -Target $f.slot | Out-Null
    $f.slot = $linked
    Read-Fixture $f $false 'path is a link' | Out-Null
    # Compatibility is produced by the real writer once, before the read-only inventory boundary.
    $f = [ordered]@{ name = 'legacy-writer'; root = Join-Path $OutputDirectory 'legacy-writer' }
    $f.slot = Join-Path $f.root 'slot'; $f.external = Join-Path $f.root 'frame-root'
    [void][IO.Directory]::CreateDirectory($f.slot); [void][IO.Directory]::CreateDirectory($f.external)
    Write-Once (Join-Path $f.slot 'capture.txt') 'capture'
    Write-Once (Join-Path $f.external 'frame.txt') 'frame'
    Write-JsonOnce (Join-Path $f.slot 'frame-slot.json') ([ordered]@{ slot_directory = $f.external })
    New-P4CaptureSeal -Directory $f.slot -SlotId 'frame-1-baseline-decision' -Slot @{ lane = 'frame' } -RequireFrameSlot $true | Out-Null
    $f.expected_hash = Get-FileSha256 (Join-Path $f.slot 'capture-seal.json'); $f.expected_slot = 'frame-1-baseline-decision'
    $legacy = Read-Fixture $f $true
    Check ($legacy.lane -ceq 'frame' -and $legacy.files.Count -eq 3) 'legacy writer output compatibility'
    foreach ($drift in @('', 'file', 'seal', 'analysis', 'binding', 'restoration', 'worktree', 'incomplete')) {
        $f = New-Chain "completed-chain-$($drift)" $drift
        # Exercise the real consumer using a complete, correctly named prior slot directory.
        # Every catalog slot carries its lane; the consumer's phase-chain test (#145 R2) reads it on each slot.
        $catalog = @(@{ id = $f.expected_slot; lane = 'frame'; role = 'baseline'; runner = 'decision' }, @{ id = 'next'; lane = 'frame' })
        Boundary $f { Assert-P4CompletedChain -Root $f.root -Catalog $catalog -SlotId 'next' -ManifestHash ('a' * 64) } ($drift -ceq '') | Out-Null
    }
    foreach ($relative in $relativeSources) {
        Check ((Get-FileSha256 (Join-Path $PSScriptRoot $relative)) -ceq $result.source_sha256[$relative]) "source unchanged during run: $relative"
    }
    $result.status = 'PASS'
} catch {
    $result.error = $_.Exception.Message
    throw
} finally {
    $timer.Stop(); $result.checks = $script:checks; $result.elapsed_seconds = $timer.Elapsed.TotalSeconds
    Write-JsonOnce (Join-Path $OutputDirectory 'validation.json') $result
}
Write-Output "PASS: $($script:checks) capture seal assertions; $($script:cases.Count) read boundaries; $($timer.Elapsed.TotalSeconds.ToString('F3')) s. Evidence: $OutputDirectory"

<#
tools/assert-protected-unchanged.ps1 - reports, as one JSON object on stdout, whether protected
material changed. It reads the repository and the given files; only -WriteLedger writes a file.
Every list argument is ONE comma-separated token, because `pwsh -File` binds one token per parameter.

Arguments (choose one mode; path mode is the default)
  -BaseRef <rev>        path mode base. Default origin/main.
  -Paths <a,b>          pathspecs. Default docs/quality/*_HISTORICAL.md,*_EVIDENCE.md,*_PROOF.md.
  -Repository <dir>     repository root for path mode. Default: the parent of tools/.
  -Compare <a,b>        compare two JSON files structurally (objects recursively, arrays by length
                        and order, numbers as numbers, null distinct from missing).
  -AllowedFields <p,q>  dotted paths allowed to differ in -Compare (a path also covers its children).
  -WriteLedger <dir> -Ledger <file>  record every file under <dir> with SHA-256; never overwrites.
  -CheckLedger <file>   compare the recorded directory with the ledger.
Path mode reports committed diffs against -BaseRef plus uncommitted tracked changes; untracked or
ignored files (for example build/) are invisible to it, so protect them with the ledger mode.

Output
  path:    {"mode":"protected-paths","base_ref","patterns","committed":[..],"uncommitted":[..],"violations":["<path>"]}
  compare: {"mode":"compare","left","right","allowed_fields","allowed":[..],"violations":[{"path","kind","left","right"}]}
  ledger:  {"mode":"ledger-write"|"ledger-check","ledger","root","files","violations":[{"path","kind","expected","actual"}]}
  kind is changed|added|removed|type (compare) or added|removed|modified (ledger).

Exit codes: 0 no violation, 1 violation(s), 2 error (message on stderr).

Examples
  pwsh -NoProfile -File tools/assert-protected-unchanged.ps1 -BaseRef origin/main
  pwsh -NoProfile -File tools/assert-protected-unchanged.ps1 -Compare "old.json,new.json" -AllowedFields "generated_utc,summary.byte_count"
  pwsh -NoProfile -File tools/assert-protected-unchanged.ps1 -WriteLedger build/reports/issue-1/p4-experiment-a -Ledger ledger.json
#>
[CmdletBinding()]
param(
    [string]$BaseRef = 'origin/main',
    [string]$Paths = 'docs/quality/*_HISTORICAL.md,docs/quality/*_EVIDENCE.md,docs/quality/*_PROOF.md',
    [string]$Repository,
    [string]$Compare,
    [string]$AllowedFields = '',
    [string]$WriteLedger,
    [string]$Ledger,
    [string]$CheckLedger
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$LedgerSchema = 'nene-pixel-protected-ledger-v1'

function Split-ListArgument {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return @() }
    return @($Value -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_.Length -gt 0 })
}

function Write-Result {
    param($Result)
    $Result | ConvertTo-Json -Depth 64 | Write-Output
    if (@($Result.violations).Count -gt 0) { exit 1 }
    exit 0
}

function ConvertFrom-JsonElement {
    # Plain values for the report; numbers keep their JSON text precision where decimal allows.
    param([System.Text.Json.JsonElement]$Element)
    switch ($Element.ValueKind) {
        'Object' {
            $map = [ordered]@{}
            foreach ($p in $Element.EnumerateObject()) { $map[$p.Name] = ConvertFrom-JsonElement $p.Value }
            return $map
        }
        'Array' { return , @($Element.EnumerateArray() | ForEach-Object { ConvertFrom-JsonElement $_ }) }
        'String' { return $Element.GetString() }
        'Number' {
            $n = [long]0
            if ($Element.TryGetInt64([ref]$n)) { return $n }
            $d = [decimal]0
            if ($Element.TryGetDecimal([ref]$d)) { return $d }
            return $Element.GetDouble()
        }
        'True' { return $true }
        'False' { return $false }
        default { return $null }
    }
}

function Test-NumberEqual {
    param([System.Text.Json.JsonElement]$A, [System.Text.Json.JsonElement]$B)
    $x = [decimal]0; $y = [decimal]0
    if ($A.TryGetDecimal([ref]$x) -and $B.TryGetDecimal([ref]$y)) { return $x -eq $y }
    return $A.GetDouble() -eq $B.GetDouble()
}

function Compare-JsonElement {
    param([string]$Path, $A, $B, [System.Collections.Generic.List[object]]$Sink)
    # $A / $B are JsonElement or $null for "missing".
    if ($null -eq $A -or $null -eq $B) {
        $kind = if ($null -eq $A) { 'added' } else { 'removed' }
        $Sink.Add([ordered]@{
                path  = $Path; kind = $kind
                left  = if ($null -eq $A) { $null } else { ConvertFrom-JsonElement $A }
                right = if ($null -eq $B) { $null } else { ConvertFrom-JsonElement $B }
            })
        return
    }
    $ka = "$($A.ValueKind)"; $kb = "$($B.ValueKind)"
    $boolKinds = @('True', 'False')
    if ($ka -ne $kb -and -not ($boolKinds -contains $ka -and $boolKinds -contains $kb)) {
        $Sink.Add([ordered]@{ path = $Path; kind = 'type'; left = ConvertFrom-JsonElement $A; right = ConvertFrom-JsonElement $B })
        return
    }
    $equal = $true
    switch ($ka) {
        'Object' {
            $left = [ordered]@{}; $right = [ordered]@{}
            foreach ($p in $A.EnumerateObject()) { $left[$p.Name] = $p.Value }
            foreach ($p in $B.EnumerateObject()) { $right[$p.Name] = $p.Value }
            $names = [System.Collections.Generic.List[string]]::new()
            foreach ($n in $left.Keys) { $names.Add($n) }
            foreach ($n in $right.Keys) { if (-not $left.Contains($n)) { $names.Add($n) } }
            foreach ($n in $names) {
                $child = if ($Path.Length -eq 0) { $n } else { "$Path.$n" }
                $l = if ($left.Contains($n)) { $left[$n] } else { $null }
                $r = if ($right.Contains($n)) { $right[$n] } else { $null }
                Compare-JsonElement -Path $child -A $l -B $r -Sink $Sink
            }
            return
        }
        'Array' {
            $la = @($A.EnumerateArray()); $lb = @($B.EnumerateArray())
            $count = [Math]::Max($la.Count, $lb.Count)
            for ($i = 0; $i -lt $count; $i++) {
                $l = if ($i -lt $la.Count) { $la[$i] } else { $null }
                $r = if ($i -lt $lb.Count) { $lb[$i] } else { $null }
                Compare-JsonElement -Path "$Path[$i]" -A $l -B $r -Sink $Sink
            }
            return
        }
        'String' { $equal = $A.GetString() -ceq $B.GetString() }
        'Number' { $equal = Test-NumberEqual $A $B }
        default { $equal = $ka -eq $kb }
    }
    if (-not $equal) {
        $Sink.Add([ordered]@{ path = $Path; kind = 'changed'; left = ConvertFrom-JsonElement $A; right = ConvertFrom-JsonElement $B })
    }
}

function Test-AllowedPath {
    param([string]$Path, [string[]]$Allowed)
    foreach ($a in $Allowed) {
        if ($Path -ceq $a -or $Path.StartsWith("$a.", [StringComparison]::Ordinal) -or
            $Path.StartsWith("$a[", [StringComparison]::Ordinal)) { return $true }
    }
    return $false
}

function Read-JsonDocument {
    param([string]$File)
    $full = (Resolve-Path -LiteralPath $File).Path
    return [System.Text.Json.JsonDocument]::Parse([System.IO.File]::ReadAllText($full))
}

function Get-DirectoryHashes {
    param([string]$Root, [string]$Exclude)
    $entries = [System.Collections.Generic.SortedDictionary[string, object]]::new([StringComparer]::Ordinal)
    foreach ($f in Get-ChildItem -LiteralPath $Root -Recurse -File -Force) {
        if ($Exclude -and $f.FullName -eq $Exclude) { continue }
        $relative = [System.IO.Path]::GetRelativePath($Root, $f.FullName).Replace('\', '/')
        $entries[$relative] = [ordered]@{
            path   = $relative
            sha256 = (Get-FileHash -LiteralPath $f.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            bytes  = $f.Length
        }
    }
    return $entries
}

function Invoke-Git {
    param([string[]]$Arguments)
    $out = & git @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "git $($Arguments -join ' ') failed: $out" }
    $lines = @($out | Where-Object { $_ -isnot [System.Management.Automation.ErrorRecord] })
    return @($lines | ForEach-Object { [string]$_ } | Where-Object { $_.Trim().Length -gt 0 })
}

try {
    $modes = @(@($Compare, $WriteLedger, $CheckLedger) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($modes.Count -gt 1) { throw 'Use only one of -Compare, -WriteLedger or -CheckLedger.' }

    if ($Compare) {
        $operands = @(Split-ListArgument $Compare)
        if ($operands.Count -ne 2) { throw '-Compare requires "left.json,right.json" as one argument.' }
        $allowedList = @(Split-ListArgument $AllowedFields)
        $leftDoc = Read-JsonDocument $operands[0]
        $rightDoc = Read-JsonDocument $operands[1]
        $diffs = [System.Collections.Generic.List[object]]::new()
        Compare-JsonElement -Path '' -A $leftDoc.RootElement -B $rightDoc.RootElement -Sink $diffs
        $allowed = @($diffs | Where-Object { Test-AllowedPath $_.path $allowedList })
        $violations = @($diffs | Where-Object { -not (Test-AllowedPath $_.path $allowedList) })
        Write-Result ([ordered]@{
                mode = 'compare'; left = $operands[0]; right = $operands[1]
                allowed_fields = $allowedList; allowed = $allowed; violations = $violations
            })
    }

    if ($WriteLedger) {
        if (-not $Ledger) { throw '-WriteLedger requires -Ledger <file>.' }
        $root = (Resolve-Path -LiteralPath $WriteLedger).Path
        if (-not (Test-Path -LiteralPath $root -PathType Container)) { throw "Not a directory: $root" }
        $ledgerPath = [System.IO.Path]::GetFullPath($Ledger)
        if (Test-Path -LiteralPath $ledgerPath) { throw "Ledger already exists, refusing to overwrite: $ledgerPath" }
        $entries = Get-DirectoryHashes -Root $root -Exclude $ledgerPath
        $document = [ordered]@{ schema = $LedgerSchema; root = $root; files = @($entries.Values) }
        $json = $document | ConvertTo-Json -Depth 8
        [System.IO.File]::WriteAllText($ledgerPath, $json.Replace("`r`n", "`n") + "`n", [System.Text.UTF8Encoding]::new($false))
        Write-Result ([ordered]@{ mode = 'ledger-write'; ledger = $ledgerPath; root = $root; files = $entries.Count; violations = @() })
    }

    if ($CheckLedger) {
        $ledgerPath = (Resolve-Path -LiteralPath $CheckLedger).Path
        $recorded = Get-Content -Raw -LiteralPath $ledgerPath | ConvertFrom-Json
        if ($recorded.schema -ne $LedgerSchema) { throw "Unexpected ledger schema: $($recorded.schema)" }
        $root = [string]$recorded.root
        if (-not (Test-Path -LiteralPath $root -PathType Container)) { throw "Ledger root is missing: $root" }
        $current = Get-DirectoryHashes -Root $root -Exclude $ledgerPath
        $expected = @{}
        foreach ($f in @($recorded.files)) { $expected[[string]$f.path] = [string]$f.sha256 }
        $violations = [System.Collections.Generic.List[object]]::new()
        foreach ($p in ($expected.Keys | Sort-Object -CaseSensitive)) {
            if (-not $current.ContainsKey($p)) {
                $violations.Add([ordered]@{ path = $p; kind = 'removed'; expected = $expected[$p]; actual = $null })
            }
            elseif ($current[$p].sha256 -ne $expected[$p]) {
                $violations.Add([ordered]@{ path = $p; kind = 'modified'; expected = $expected[$p]; actual = $current[$p].sha256 })
            }
        }
        foreach ($p in $current.Keys) {
            if (-not $expected.ContainsKey($p)) {
                $violations.Add([ordered]@{ path = $p; kind = 'added'; expected = $null; actual = $current[$p].sha256 })
            }
        }
        Write-Result ([ordered]@{
                mode = 'ledger-check'; ledger = $ledgerPath; root = $root
                files = $expected.Count; violations = @($violations)
            })
    }

    $repositoryRoot = if ($Repository) { (Resolve-Path -LiteralPath $Repository).Path }
    else { (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path }
    $patterns = @(Split-ListArgument $Paths)
    if ($patterns.Count -eq 0) { throw '-Paths must contain at least one pathspec.' }

    $git = @('-C', $repositoryRoot, '-c', 'core.quotepath=false')
    $committed = @(Invoke-Git ($git + @('diff', '--name-only', $BaseRef, '--') + $patterns))
    $status = @(Invoke-Git ($git + @('status', '--porcelain', '--untracked-files=no', '--') + $patterns))
    $uncommitted = @($status | ForEach-Object { $_.Substring(3) -split ' -> ' } | Sort-Object -Unique -CaseSensitive)
    $violations = @(@($committed) + @($uncommitted) | Sort-Object -Unique -CaseSensitive)
    Write-Result ([ordered]@{
            mode = 'protected-paths'; base_ref = $BaseRef; patterns = $patterns
            committed = $committed; uncommitted = $uncommitted; violations = $violations
        })
}
catch {
    [Console]::Error.WriteLine("assert-protected-unchanged: $($_.Exception.Message)")
    exit 2
}

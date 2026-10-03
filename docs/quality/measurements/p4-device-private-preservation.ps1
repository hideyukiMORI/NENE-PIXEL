# Pure policy for ADR 0035. The caller observes and executes; this file does neither.
Set-StrictMode -Version Latest

function New-P4Map {
    return [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
}

function Assert-P4Path([string] $Path) {
    if ([string]::IsNullOrEmpty($Path) -or $Path.StartsWith('/') -or $Path.Contains('\')) { throw "Unsafe path: $Path" }
    $parts = $Path.Split('/')
    if ($parts[0] -cnotin @('files', 'no_backup', 'shared_prefs', 'databases')) { throw "Unprotected path: $Path" }
    foreach ($part in $parts) {
        if ($part -eq '.' -or $part -eq '..' -or $part -cnotmatch '^[A-Za-z0-9._-]+$') { throw "Unsafe component: $Path" }
    }
}

function ConvertTo-P4InventoryMap([object[]] $Inventory) {
    if ($null -eq $Inventory) { throw 'Inventory is null' }
    $map = New-P4Map
    foreach ($entry in $Inventory) {
        if ($null -eq $entry -or $entry.Path -isnot [string] -or $entry.Type -isnot [string]) { throw 'Incomplete inventory entry' }
        $path = $entry.Path
        Assert-P4Path $path
        if ($map.ContainsKey($path)) { throw "Duplicate path: $path" }
        if ($entry.Type -ceq 'directory') {
            $item = [pscustomobject]@{ Path = $path; Type = 'directory' }
        } elseif ($entry.Type -ceq 'file') {
            if ($entry.PSObject.Properties.Name -cnotcontains 'LinkCount' -or ($entry.LinkCount -isnot [int] -and $entry.LinkCount -isnot [long]) -or $entry.LinkCount -ne 1) { throw "Unproven single-link file: $path" }
            if ($entry.PSObject.Properties.Name -cnotcontains 'Size' -or $entry.Size -isnot [long] -and $entry.Size -isnot [int] -or $entry.Size -lt 0) { throw "Invalid size: $path" }
            if ($entry.Hash -isnot [string] -or $entry.Hash -cnotmatch '^[0-9a-f]{64}$') { throw "Invalid SHA-256: $path" }
            if ($entry.MtimeNs -isnot [string] -or $entry.MtimeNs -cnotmatch '^(0|[1-9][0-9]*)$') { throw "Invalid mtime_ns: $path" }
            $item = [pscustomobject]@{ Path = $path; Type = 'file'; Size = [long] $entry.Size; Hash = $entry.Hash; MtimeNs = $entry.MtimeNs; LinkCount = 1 }
        } else { throw "Unsupported kind: $path" }
        $map.Add($path, $item)
    }
    foreach ($path in $map.Keys) {
        $slash = $path.LastIndexOf('/')
        if ($slash -ge 0) {
            $parent = $path.Substring(0, $slash)
            if (-not $map.ContainsKey($parent) -or $map[$parent].Type -cne 'directory') { throw "Missing directory parent: $path" }
        } elseif ($map[$path].Type -cne 'directory') { throw "Root must be a directory: $path" }
    }
    return $map
}

function Get-P4Items($Map) {
    return @($Map.Values | Sort-Object -Property Path -CaseSensitive)
}

function Add-P4Directory($Map, [string] $Path) {
    if (-not $Map.ContainsKey($Path)) { $Map.Add($Path, [pscustomobject]@{ Path = $Path; Type = 'directory' }) }
    elseif ($Map[$Path].Type -cne 'directory') { throw "Occupied directory: $Path" }
}

function Add-P4Ancestors($Map, [string] $Path) {
    $parts = $Path.Split('/')
    for ($i = 1; $i -le $parts.Length; $i++) {
        Add-P4Directory $Map ($parts[0..($i - 1)] -join '/')
    }
}

function Test-P4Below([string] $Path, [string] $Root) {
    return $Path -ceq $Root -or $Path.StartsWith("$Root/", [System.StringComparison]::Ordinal)
}

function Assert-P4SameEntry($Expected, $Actual) {
    if ($Expected.Type -cne $Actual.Type) { throw "Changed type: $($Expected.Path)" }
    if ($Expected.Type -ceq 'file' -and ($Expected.Size -ne $Actual.Size -or $Expected.Hash -cne $Actual.Hash -or $Expected.MtimeNs -cne $Actual.MtimeNs)) {
        throw "Changed file identity: $($Expected.Path)"
    }
}

function Assert-P4ExactMap($Expected, $Actual) {
    if ($Expected.Count -ne $Actual.Count) { throw 'Inventory entry count changed' }
    foreach ($path in $Expected.Keys) {
        if (-not $Actual.ContainsKey($path)) { throw "Missing entry: $path" }
        Assert-P4SameEntry $Expected[$path] $Actual[$path]
    }
}

function Assert-P4Session([string] $Session) {
    if ($Session -cnotmatch '^[a-z0-9][a-z0-9-]*$') { throw 'Invalid session id' }
}

function Get-P4MoveRoots($Original) {
    $names = @('nene-pixel-recovery-v1', 'nene-pixel-recovery-v1.new', 'nene-pixel-recovery-v1.bak', 'reference-underlays')
    $roots = @()
    foreach ($name in $names) {
        $path = "no_backup/$name"
        if (-not $Original.ContainsKey($path)) { continue }
        $required = if ($name -ceq 'reference-underlays') { 'directory' } else { 'file' }
        if ($Original[$path].Type -cne $required) { throw "Invalid original type: $path" }
        $roots += $path
    }
    return $roots
}

function New-P4IsolationPlan([object[]] $OriginalInventory, [string] $Session) {
    Assert-P4Session $Session
    $original = ConvertTo-P4InventoryMap $OriginalInventory
    $sessionRoot = "no_backup/p4-user-preservation/$Session"
    foreach ($path in $original.Keys) {
        if (Test-P4Below $path $sessionRoot) { throw 'Session guard already exists' }
    }
    if ($original.ContainsKey('no_backup/p4-user-preservation') -and $original['no_backup/p4-user-preservation'].Type -cne 'directory') { throw 'Guard parent occupied' }
    $guard = "$sessionRoot/original"
    $expected = New-P4Map
    $moves = @()
    $roots = @(Get-P4MoveRoots $original)
    foreach ($item in $original.Values) {
        $root = @($roots | Where-Object { Test-P4Below $item.Path $_ } | Select-Object -First 1)
        $to = if ($root.Count -gt 0) { "$guard/$($item.Path.Substring('no_backup/'.Length))" } else { $item.Path }
        $expected.Add($to, ($item | Select-Object *))
        $expected[$to].Path = $to
    }
    Add-P4Ancestors $expected $guard
    foreach ($root in $roots) { $moves += [pscustomobject]@{ Source = $root; Destination = "$guard/$($root.Substring('no_backup/'.Length))" } }
    return [pscustomobject]@{ Session = $Session; Guard = $guard; SessionRoot = $sessionRoot; Moves = @($moves); Expected = @(Get-P4Items $expected); Original = @(Get-P4Items $original) }
}

function Assert-P4Isolation([object[]] $OriginalInventory, [string] $Session, [object[]] $CurrentInventory) {
    $plan = New-P4IsolationPlan $OriginalInventory $Session
    $expected = ConvertTo-P4InventoryMap $plan.Expected
    $actual = ConvertTo-P4InventoryMap $CurrentInventory
    Assert-P4ExactMap $expected $actual
    return $true
}

function Get-P4IsolatedState([object[]] $OriginalInventory, [string] $Session, [object[]] $CurrentInventory) {
    $plan = New-P4IsolationPlan $OriginalInventory $Session
    $base = ConvertTo-P4InventoryMap $plan.Expected
    $current = ConvertTo-P4InventoryMap $CurrentInventory
    foreach ($path in $base.Keys) {
        if (-not $current.ContainsKey($path)) { throw "Missing isolated original: $path" }
        Assert-P4SameEntry $base[$path] $current[$path]
    }
    $new = New-P4Map
    $original = ConvertTo-P4InventoryMap $plan.Original
    foreach ($path in $current.Keys) {
        if ($base.ContainsKey($path)) { continue }
        if (Test-P4Below $path $plan.SessionRoot) { throw "Unexpected session entry: $path" }
        # A byte-identical live original at its old destination is ambiguous after a partial restore.
        if ($original.ContainsKey($path) -and $original[$path].Type -ceq 'file') {
            $same = $true
            try { Assert-P4SameEntry $original[$path] $current[$path] } catch { $same = $false }
            if ($same) { throw "Ambiguous live original: $path" }
        }
        $new.Add($path, $current[$path])
    }
    return [pscustomobject]@{ Plan = $plan; Base = $base; Current = $current; New = $new }
}

function New-P4RestorationPlan([object[]] $OriginalInventory, [string] $Session, [object[]] $CurrentInventory) {
    $state = Get-P4IsolatedState $OriginalInventory $Session $CurrentInventory
    $plan = $state.Plan
    $archive = "$($plan.SessionRoot)/measurement"
    if ($state.Current.ContainsKey($archive)) { throw 'Measurement archive occupied' }
    $moveRoots = @()
    foreach ($path in @($state.New.Keys | Sort-Object -CaseSensitive)) {
        $parent = $path
        $isNested = $false
        while ($parent.Contains('/')) {
            $parent = $parent.Substring(0, $parent.LastIndexOf('/'))
            if ($state.New.ContainsKey($parent)) { $isNested = $true; break }
        }
        if (-not $isNested) { $moveRoots += $path }
    }
    $measurementMoves = @()
    $expected = New-P4Map
    foreach ($entry in $state.Base.Values) { $expected.Add($entry.Path, ($entry | Select-Object *)) }
    Add-P4Directory $expected $archive
    foreach ($root in $moveRoots) {
        $destination = "$archive/$root"
        $measurementMoves += [pscustomobject]@{ Source = $root; Destination = $destination }
        Add-P4Ancestors $expected $destination.Substring(0, $destination.LastIndexOf('/'))
    }
    foreach ($entry in $state.New.Values) {
        $newPath = "$archive/$($entry.Path)"
        $copy = $entry | Select-Object *
        $copy.Path = $newPath
        $expected.Add($newPath, $copy)
    }
    foreach ($move in $plan.Moves) {
        foreach ($path in @($expected.Keys)) {
            if (Test-P4Below $path $move.Destination) {
                $newPath = $move.Source + $path.Substring($move.Destination.Length)
                if ($expected.ContainsKey($newPath)) { throw "Original destination occupied: $newPath" }
                $copy = $expected[$path] | Select-Object *
                $copy.Path = $newPath
                $expected.Add($newPath, $copy)
                $expected.Remove($path) | Out-Null
            }
        }
    }
    return [pscustomobject]@{
        Session = $Session; Guard = $plan.Guard; Archive = $archive
        MeasurementMoves = @($measurementMoves); OriginalMoves = @($plan.Moves | ForEach-Object { [pscustomobject]@{ Source = $_.Destination; Destination = $_.Source } })
        Expected = @(Get-P4Items $expected); Measurement = @($expected.Values | Where-Object { Test-P4Below $_.Path $archive } | Sort-Object -Property Path -CaseSensitive)
        OriginallyAbsentNoBackup = -not (ConvertTo-P4InventoryMap $plan.Original).ContainsKey('no_backup')
    }
}

function Assert-P4Restored([object[]] $OriginalInventory, [string] $Session, [object[]] $PreRestoreInventory, [object[]] $CurrentInventory) {
    $plan = New-P4RestorationPlan $OriginalInventory $Session $PreRestoreInventory
    $expected = ConvertTo-P4InventoryMap $plan.Expected
    $actual = ConvertTo-P4InventoryMap $CurrentInventory
    Assert-P4ExactMap $expected $actual
    return $true
}

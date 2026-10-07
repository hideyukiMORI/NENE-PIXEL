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
    $fixed = @(
        @{ Path = 'no_backup/nene-pixel-recovery-v1'; Type = 'file' }
        @{ Path = 'no_backup/nene-pixel-recovery-v1.new'; Type = 'file' }
        @{ Path = 'no_backup/nene-pixel-recovery-v1.bak'; Type = 'file' }
        @{ Path = 'no_backup/reference-underlays'; Type = 'directory' }
        @{ Path = 'files/profileinstaller_profileWrittenFor_lastUpdateTime.dat'; Type = 'file' }
        @{ Path = 'files/profileInstalled'; Type = 'file' }
    )
    $roots = @()
    foreach ($entry in $fixed) {
        $path = $entry.Path
        if (-not $Original.ContainsKey($path)) { continue }
        if ($Original[$path].Type -cne $entry.Type) { throw "Invalid original type: $path" }
        $roots += $path
    }
    return $roots
}

function Move-P4InventoryEntries($Map, [string] $Source, [string] $Destination) {
    if (-not $Map.ContainsKey($Source) -or $Map.ContainsKey($Destination)) { throw "Move collision: $Source -> $Destination" }
    $moving = @($Map.Keys | Where-Object { Test-P4Below $_ $Source })
    foreach ($path in $moving) {
        $to = $Destination + $path.Substring($Source.Length)
        if ($Map.ContainsKey($to)) { throw "Move descendant occupied: $to" }
        $copy = $Map[$path] | Select-Object *
        $copy.Path = $to
        $Map.Add($to, $copy)
    }
    foreach ($path in $moving) { $Map.Remove($path) | Out-Null }
}

function Assert-P4MoveCount([Nullable[int]] $Count, [int] $Maximum, [string] $Name) {
    if ($null -ne $Count -and ($Count -lt 0 -or $Count -gt $Maximum)) { throw "Invalid ${Name}: $Count" }
}

function New-P4IsolationPlan([object[]] $OriginalInventory, [string] $Session, [Nullable[int]] $CompletedMoveCount = $null) {
    Assert-P4Session $Session
    $original = ConvertTo-P4InventoryMap $OriginalInventory
    $sessionRoot = "no_backup/p4-user-preservation/$Session"
    foreach ($path in $original.Keys) {
        if (Test-P4Below $path $sessionRoot) { throw 'Session guard already exists' }
    }
    if ($original.ContainsKey('no_backup/p4-user-preservation') -and $original['no_backup/p4-user-preservation'].Type -cne 'directory') { throw 'Guard parent occupied' }
    $guard = "$sessionRoot/original"
    $roots = @(Get-P4MoveRoots $original)
    $moves = @()
    foreach ($root in $roots) { $moves += [pscustomobject]@{ Source = $root; Destination = "$guard/$($root.Substring($root.LastIndexOf('/') + 1))" } }
    Assert-P4MoveCount $CompletedMoveCount $moves.Count 'completed isolation move count'
    $completed = if ($null -eq $CompletedMoveCount) { $moves.Count } else { $CompletedMoveCount }
    $expected = New-P4Map
    foreach ($item in $original.Values) { $expected.Add($item.Path, ($item | Select-Object *)) }
    Add-P4Ancestors $expected $guard
    for ($i = 0; $i -lt $completed; $i++) { Move-P4InventoryEntries $expected $moves[$i].Source $moves[$i].Destination }
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

function New-P4SlotResetPlan {
    param([object[]] $OriginalInventory, [string] $Session, [string] $SlotId,
        [object[]] $CurrentInventory, [Nullable[int]] $CompletedMoveCount = $null)
    Assert-P4Session $SlotId
    $state = Get-P4IsolatedState $OriginalInventory $Session $CurrentInventory
    $sessionArchive = "no_backup/p4-layer-slots/$Session"
    $archive = "$sessionArchive/$SlotId"
    foreach ($entry in $OriginalInventory) {
        if (Test-P4Below $entry.Path $sessionArchive) { throw 'Slot archive session belongs to original inventory' }
    }
    if ($state.Current.ContainsKey($archive)) { throw 'Slot archive already exists' }
    $moves = @()
    foreach ($root in @(Get-P4MoveRoots $state.New)) {
        $moves += [pscustomobject]@{ Source = $root; Destination = "$archive/$root" }
    }
    Assert-P4MoveCount $CompletedMoveCount $moves.Count 'completed slot reset move count'
    $completed = if ($null -eq $CompletedMoveCount) { $moves.Count } else { $CompletedMoveCount }
    $expected = ConvertTo-P4InventoryMap $CurrentInventory
    foreach ($move in $moves) {
        Add-P4Ancestors $expected ($move.Destination.Substring(0, $move.Destination.LastIndexOf('/')))
    }
    for ($i = 0; $i -lt $completed; $i++) {
        Move-P4InventoryEntries $expected $moves[$i].Source $moves[$i].Destination
    }
    return [pscustomobject]@{ Session = $Session; SlotId = $SlotId; Archive = $archive;
        Moves = @($moves); Expected = @(Get-P4Items $expected) }
}

function New-P4RestorationExpectedMap($Current, [string] $Archive, $MeasurementMoves, $OriginalMoves, [int] $CompletedMeasurementMoveCount, [int] $CompletedOriginalMoveCount) {
    $expected = New-P4Map
    foreach ($entry in $Current.Values) { $expected.Add($entry.Path, ($entry | Select-Object *)) }
    Add-P4Directory $expected $Archive
    foreach ($move in $MeasurementMoves) {
        Add-P4Ancestors $expected $move.Destination.Substring(0, $move.Destination.LastIndexOf('/'))
    }
    for ($i = 0; $i -lt $CompletedMeasurementMoveCount; $i++) {
        Move-P4InventoryEntries $expected $MeasurementMoves[$i].Source $MeasurementMoves[$i].Destination
    }
    for ($i = 0; $i -lt $CompletedOriginalMoveCount; $i++) {
        Move-P4InventoryEntries $expected $OriginalMoves[$i].Source $OriginalMoves[$i].Destination
    }
    return $expected
}

function New-P4RestorationPlan([object[]] $OriginalInventory, [string] $Session, [object[]] $CurrentInventory, [Nullable[int]] $CompletedMeasurementMoveCount = $null, [Nullable[int]] $CompletedOriginalMoveCount = $null) {
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
    foreach ($root in $moveRoots) {
        $destination = "$archive/$root"
        $measurementMoves += [pscustomobject]@{ Source = $root; Destination = $destination }
    }
    $originalMoves = @($plan.Moves | ForEach-Object { [pscustomobject]@{ Source = $_.Destination; Destination = $_.Source } })
    Assert-P4MoveCount $CompletedMeasurementMoveCount $measurementMoves.Count 'completed measurement move count'
    Assert-P4MoveCount $CompletedOriginalMoveCount $originalMoves.Count 'completed original move count'
    $completedMeasurement = if ($null -eq $CompletedMeasurementMoveCount) { $measurementMoves.Count } else { $CompletedMeasurementMoveCount }
    $completedOriginal = if ($null -eq $CompletedOriginalMoveCount) { $originalMoves.Count } else { $CompletedOriginalMoveCount }
    if ($completedOriginal -gt 0 -and $completedMeasurement -ne $measurementMoves.Count) { throw 'Original moves require complete measurement moves' }
    $full = New-P4RestorationExpectedMap $state.Current $archive $measurementMoves $originalMoves $measurementMoves.Count $originalMoves.Count
    $expected = if ($completedMeasurement -eq $measurementMoves.Count -and $completedOriginal -eq $originalMoves.Count) {
        $full
    } else {
        New-P4RestorationExpectedMap $state.Current $archive $measurementMoves $originalMoves $completedMeasurement $completedOriginal
    }
    return [pscustomobject]@{
        Session = $Session; Guard = $plan.Guard; Archive = $archive
        MeasurementMoves = @($measurementMoves); OriginalMoves = @($originalMoves)
        Expected = @(Get-P4Items $expected); Measurement = @($full.Values | Where-Object { Test-P4Below $_.Path $archive } | Sort-Object -Property Path -CaseSensitive)
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

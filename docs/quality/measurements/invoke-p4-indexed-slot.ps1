[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ManifestPath,
    [Parameter(Mandatory = $true)][string]$SlotId
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'p4-indexed-preflight.ps1')
. (Join-Path $PSScriptRoot 'android-window-state.ps1')

function Invoke-P4CheckedAdb {
    param($Manifest, [string]$Directory, [string]$Name, [string[]]$Arguments, [int]$Timeout = 30)
    $result = Invoke-BoundedNativeCommand -RepositoryRoot $Manifest.roles.candidate.worktree `
        -ExecutablePath $Manifest.tools.adb.path -NativeArguments (@('-s', $Manifest.device.serial) + $Arguments) `
        -LogPath (Join-Path $Directory "$Name.log") -TimeoutSeconds $Timeout
    if ($result.ExitCode -ne 0) { throw "ADB $Name failed with exit $($result.ExitCode)." }
    return ($result.OutputLines -join "`n").Trim()
}

function Get-P4OriginalDeviceSettings {
    param($Manifest, [string]$Directory)
    $window = Invoke-P4CheckedAdb $Manifest $Directory 'original-window' @('shell', 'dumpsys', 'window')
    $numeric = Invoke-P4CheckedAdb $Manifest $Directory 'original-numeric-rotation' @('shell', 'settings', 'get', 'system', 'user_rotation')
    $awake = Invoke-P4CheckedAdb $Manifest $Directory 'original-stay-awake' @('shell', 'settings', 'get', 'global', 'stay_on_while_plugged_in')
    $rotation = ConvertFrom-NeneWindowRotationState $numeric $window
    if ($awake -cnotmatch '^(null|[0-7])$' -or $rotation.numeric_user_rotation -ne $rotation.reported_user_rotation -or
        ($rotation.mode -eq 'locked' -and $rotation.current_rotation -ne $rotation.numeric_user_rotation)) {
        throw 'Original device settings are ambiguous.'
    }
    return [ordered]@{ rotation = $rotation; stay_awake = $awake }
}

function Restore-P4OriginalDeviceSettings {
    param($Manifest, [string]$Directory, $Original)
    $errors = [Collections.Generic.List[string]]::new()
    try {
        Invoke-P4CheckedAdb $Manifest $Directory 'restore-rotation-value' @('shell', 'wm', 'user-rotation', 'lock', "$($Original.rotation.numeric_user_rotation)") | Out-Null
        if ($Original.rotation.mode -eq 'free') {
            Invoke-P4CheckedAdb $Manifest $Directory 'restore-rotation-mode' @('shell', 'wm', 'user-rotation', 'free') | Out-Null
        }
    } catch { $errors.Add($_.Exception.Message) }
    try {
        $awakeCommand = if ($Original.stay_awake -eq 'null') {
            @('shell', 'settings', 'delete', 'global', 'stay_on_while_plugged_in')
        } else { @('shell', 'settings', 'put', 'global', 'stay_on_while_plugged_in', $Original.stay_awake) }
        Invoke-P4CheckedAdb $Manifest $Directory 'restore-stay-awake' $awakeCommand | Out-Null
    } catch { $errors.Add($_.Exception.Message) }
    try {
        $check = Get-P4OriginalDeviceSettings $Manifest $Directory
        if ($check.stay_awake -cne $Original.stay_awake -or $check.rotation.mode -cne $Original.rotation.mode -or
            $check.rotation.numeric_user_rotation -ne $Original.rotation.numeric_user_rotation -or
            $check.rotation.reported_user_rotation -ne $Original.rotation.reported_user_rotation -or
            ($Original.rotation.mode -eq 'locked' -and $check.rotation.current_rotation -ne $Original.rotation.current_rotation)) {
            throw 'Original rotation/stay-awake restoration could not be verified.'
        }
    } catch { $errors.Add($_.Exception.Message) }
    if ($errors.Count -gt 0) { throw ($errors -join '; ') }
}

function Stop-P4RemoteProcesses {
    param($Manifest, $Slot, [string]$Directory)
    $packages = @('io.github.hideyukimori.nenepixel', 'io.github.hideyukimori.nenepixel.test',
        'io.github.hideyukimori.nenepixel.adapters.persistence.test')
    $errors = [Collections.Generic.List[string]]::new()
    $i = 0
    foreach ($package in $packages) {
        $i++
        try {
            Invoke-P4CheckedAdb $Manifest $Directory "stop-remote-$i" @('shell', 'am', 'force-stop', $package) 10 | Out-Null
            $result = Invoke-BoundedNativeCommand -RepositoryRoot $Manifest.roles[$Slot.role].worktree `
                -ExecutablePath $Manifest.tools.adb.path -LogPath (Join-Path $Directory "remote-absence-$i.log") `
                -NativeArguments @('-s', $Manifest.device.serial, 'shell', 'pidof', $package) -TimeoutSeconds 10
            if ($result.ExitCode -ne 1 -or -not [string]::IsNullOrWhiteSpace(($result.OutputLines -join ''))) {
                throw "Remote process absence was not confirmed: $package"
            }
        } catch { $errors.Add($_.Exception.Message) }
    }
    if ($errors.Count -gt 0) { throw ($errors -join '; ') }
}

function Read-P4FreshAnalysis {
    param([string]$Path, $Slot, [string]$ManifestHash, [datetime]$StartedUtc)
    $file = Get-Item -LiteralPath $Path
    if ($file.CreationTimeUtc -lt $StartedUtc.AddSeconds(-1) -or $file.LastWriteTimeUtc -lt $StartedUtc.AddSeconds(-1)) {
        throw 'Analyzer output is not fresh.'
    }
    $result = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json -AsHashtable
    Assert-P4RequiredKeys $result @('schema', 'protocol_id', 'slot_id', 'preflight_sha256', 'role', 'verdict', 'created_utc') 'analysis'
    if ($result.protocol_id -cne $script:P4ProtocolId -or $result.slot_id -cne $Slot.id -or
        $result.role -cne $Slot.role -or $result.preflight_sha256 -cne $ManifestHash -or
        ([datetime]$result.created_utc).ToUniversalTime() -lt $StartedUtc.AddSeconds(-1)) {
        throw 'Analyzer identity does not match the reserved slot.'
    }
    $allowed = switch ($Slot.lane) {
        'host' { @('valid-descriptive') }
        'publication' { @('valid-constants-retained', 'valid-constants-revision-required') }
        'frame' { if ($Slot.runner -eq 'diagnostic') { @('inconclusive', 'PERFORMANCE_FAIL') } else { @('pass', 'PERFORMANCE_FAIL') } }
        default { @('pass', 'PERFORMANCE_FAIL') }
    }
    if ($result.verdict -cnotin $allowed) { throw 'Analyzer verdict is incompatible with this lane.' }
    return $result
}

function Invoke-P4IndexedSlot {
    param([string]$ManifestPath, [string]$SlotId)
    $manifestBytes = [IO.File]::ReadAllBytes($ManifestPath)
    $manifestHash = Get-Sha256Hex $manifestBytes
    $manifest = [Text.UTF8Encoding]::new($false, $true).GetString($manifestBytes) | ConvertFrom-Json -AsHashtable
    $root = [IO.Path]::GetFullPath($manifest.output_directory)
    $reserved = Join-Path $root 'preflight.json'
    if (-not (Test-Path -LiteralPath $reserved) -or (Get-FileSha256 $reserved) -cne $manifestHash) {
        throw 'Use the exact reserved preflight manifest.'
    }
    Assert-P4ManifestArtifacts $manifest $manifest.roles.candidate.worktree
    $catalog = @(Get-P4SlotCatalog)
    $selected = @($catalog | Where-Object { $_.id -ceq $SlotId })
    if ($selected.Count -ne 1) { throw 'Unknown slot; no substitute or extra attempt is permitted.' }
    $slot = $selected[0]
    $mutex = [Threading.Mutex]::new($false, 'Local\NenePixelP4EvidenceExclusive')
    $acquired = $false
    $directory = Join-Path $root $SlotId
    try {
        try { $acquired = $mutex.WaitOne(0) }
        catch [Threading.AbandonedMutexException] { $acquired = $true; throw 'Abandoned experiment lease requires review.' }
        if (-not $acquired) { throw 'Another experiment owns the device/build lease.' }
        foreach ($prior in $catalog) {
            if ($prior.id -ceq $SlotId) { break }
            $path = Join-Path $root "$($prior.id)/completed.json"
            if (-not (Test-Path -LiteralPath $path)) { throw "Prior slot is incomplete: $($prior.id)" }
            $result = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json -AsHashtable
            $expected = if ($prior.lane -eq 'host') { 'valid-descriptive' }
                elseif ($prior.lane -eq 'frame' -and $prior.runner -eq 'diagnostic') { 'inconclusive' }
                elseif ($prior.lane -eq 'publication') { 'valid-constants-retained' } else { 'pass' }
            if ($result.slot_id -cne $prior.id -or $result.verdict -cne $expected -or $result.status -cne 'completed' -or
                $result.preflight_sha256 -cne $manifestHash -or (Test-Path -LiteralPath (Join-Path $root "$($prior.id)/invalid.json"))) {
                throw "Prior slot stopped the experiment: $($prior.id)"
            }
        }
        if (Test-Path -LiteralPath $directory) { throw 'Slot already consumed; retries are prohibited.' }
        New-Item -ItemType Directory -Path $directory | Out-Null
        $started = [ordered]@{ schema = $script:P4ManifestSchema; slot_id = $SlotId; status = 'started'; attempt = 1;
            preflight_sha256 = $manifestHash; started_utc = [datetime]::UtcNow.ToString('o') }
        Write-NewInvocationFile (Join-Path $directory 'started.json') ($started | ConvertTo-Json)
        $original = $null
        $failure = $null
        $analysisResult = $null
        try {
            if ($slot.lane -ne 'host') { $original = Get-P4OriginalDeviceSettings $manifest $directory }
            $analysisPath = Join-Path $directory 'analysis.json'
            if (Test-Path -LiteralPath $analysisPath) { throw 'Analyzer output exists before collection.' }
            $execution = Invoke-BoundedNativeCommand -RepositoryRoot $manifest.roles[$slot.role].worktree `
                -ExecutablePath (Join-Path $PSHOME 'pwsh.exe') -LogPath (Join-Path $directory 'collector.log') `
                -TimeoutSeconds $slot.timeout_seconds -NativeArguments @('-NoProfile', '-File', $manifest.tools.runner.path,
                    '-ManifestPath', $reserved, '-SlotId', $SlotId, '-OutputDirectory', $directory)
            if ($execution.ExitCode -ne 0) { throw "Collector exited $($execution.ExitCode)." }
            if (Test-Path -LiteralPath $analysisPath) { throw 'Collector attempted to create analyzer verdict.' }
            $analysisStarted = [datetime]::UtcNow
            $analysis = Invoke-BoundedNativeCommand -RepositoryRoot $manifest.roles.candidate.worktree `
                -ExecutablePath (Join-Path $PSHOME 'pwsh.exe') -LogPath (Join-Path $directory 'analyzer.log') `
                -TimeoutSeconds 30 -NativeArguments @('-NoProfile', '-File', $manifest.tools.analyzer.path,
                    '-ManifestPath', $reserved, '-SlotId', $SlotId, '-OutputDirectory', $directory)
            if ($analysis.ExitCode -ne 0) { throw 'Analyzer rejected capture; preserve partial result.' }
            $analysisResult = Read-P4FreshAnalysis $analysisPath $slot $manifestHash $analysisStarted
        } catch { $failure = $_.Exception }
        finally {
            if ($null -ne $original) {
                $cleanupErrors = [Collections.Generic.List[string]]::new()
                try { Stop-P4RemoteProcesses $manifest $slot $directory } catch { $cleanupErrors.Add($_.Exception.Message) }
                try {
                    $restoreDirectory = Join-Path $directory 'restore'
                    New-Item -ItemType Directory -Path $restoreDirectory | Out-Null
                    Restore-P4OriginalDeviceSettings $manifest $restoreDirectory $original
                } catch { $cleanupErrors.Add($_.Exception.Message) }
                if ($cleanupErrors.Count -gt 0) {
                    $failure = [InvalidOperationException]::new("Device cleanup failed: $($cleanupErrors -join '; ')", $failure)
                }
            }
        }
        if ($null -ne $failure) {
            Write-NewInvocationFile (Join-Path $directory 'invalid.json') (([ordered]@{
                schema = $script:P4ManifestSchema; slot_id = $SlotId; status = 'invalid'; verdict = 'INVALID';
                message = $failure.ToString(); ended_utc = [datetime]::UtcNow.ToString('o')
            }) | ConvertTo-Json -Depth 8)
            throw $failure
        }
        $analysisResult.status = 'completed'
        $analysisResult.ended_utc = [datetime]::UtcNow.ToString('o')
        Write-NewInvocationFile (Join-Path $directory 'completed.json') ($analysisResult | ConvertTo-Json -Depth 15)
        return $analysisResult
    } finally {
        if ($acquired) { $mutex.ReleaseMutex() }
        $mutex.Dispose()
    }
}

if ($MyInvocation.InvocationName -ne '.') { Invoke-P4IndexedSlot $ManifestPath $SlotId }

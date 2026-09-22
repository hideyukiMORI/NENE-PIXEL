Set-StrictMode -Version Latest

# Issue #106 / P4: one device-state module for the whole acceptance protocol.
#
# The pure parser (`ConvertFrom-P4DeviceDumps`) is the only place that interprets device dumps.
# `Get-P4LiveDeviceState` is the only place that runs adb for state, always through the bounded
# native boundary. `Assert-P4DeviceStateMatches` is the only comparison against the manifest.
# Nothing here deletes or overwrites evidence: every artifact is written CreateNew.

. (Join-Path $PSScriptRoot '../bounded-native-command.ps1')
. (Join-Path $PSScriptRoot 'android-window-state.ps1')
. (Join-Path $PSScriptRoot 'm2-package-dexopt.ps1')

$script:P4DeviceStateSchema = 'nene-pixel-p4-device-state-v1'

# Exactly the keys the integration contract fixes. Order is part of the recorded evidence.
$script:P4DeviceStateKeys = @(
    'serial', 'manufacturer', 'model', 'product', 'device', 'api', 'fingerprint', 'security_patch',
    'display_mode', 'width', 'height', 'refresh_rate', 'rotation', 'thermal', 'power_save',
    'interactive', 'usb_power', 'battery', 'locale', 'user_rotation', 'stay_awake', 'captured_utc'
)

# Fields compared byte-for-byte against manifest.device.
$script:P4DeviceStateExactKeys = @(
    'serial', 'manufacturer', 'model', 'product', 'device', 'api', 'fingerprint', 'security_patch',
    'display_mode', 'width', 'height', 'refresh_rate', 'power_save', 'interactive', 'usb_power', 'locale'
)

# Fields whose accepted value is fixed by the protocol regardless of what the manifest claims.
$script:P4DeviceStateRequiredValues = [ordered]@{
    power_save = '0'
    interactive = 'true'
    usb_power = 'true'
}

$script:P4DeviceThermalMaximum = 1
$script:P4DeviceBatteryMinimum = 20

function Get-P4SingleMatch {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text,
        [Parameter(Mandatory = $true)][string]$Pattern,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $found = [regex]::Matches($Text, $Pattern)
    if ($found.Count -ne 1) {
        throw "Device dump must report exactly one $Description (found $($found.Count))."
    }
    return $found[0]
}

function Get-P4PropertyValue {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$GetpropText,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $pattern = '(?m)^\[' + [regex]::Escape($Name) + '\]:\s\[(?<value>[^\r\n]*)\]\s*\r?$'
    $match = Get-P4SingleMatch $GetpropText $pattern "property $Name"
    $value = $match.Groups['value'].Value
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Device property is empty: $Name" }
    return $value
}

function Read-P4SettingsValue {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyString()][AllowNull()][string]$Text,
        [Parameter(Mandatory = $true)][string]$Pattern,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $value = ([string]$Text).Trim()
    if ($value -cnotmatch $Pattern) { throw "Unusable $Description setting: '$value'" }
    return $value
}

function ConvertFrom-P4DeviceDumps {
    <#
        Pure parser over raw device dumps. No adb, no file system, no clock beyond the caller's
        supplied capture timestamp. Every fixture in
        build/reports/issue-106/device-fixtures-20260915/ must parse through exactly this path.
    #>
    param(
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][AllowEmptyString()][string[]]$Getprop,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][AllowEmptyString()][string[]]$Display,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][AllowEmptyString()][string[]]$Window,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][AllowEmptyString()][string[]]$Thermal,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][AllowEmptyString()][string[]]$Power,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][AllowEmptyString()][string[]]$Battery,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$LowPower,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Locales,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$UserRotation,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$StayAwake,
        [AllowNull()][string]$CapturedUtc = $null
    )

    $getpropText = ($Getprop -join "`n")
    $displayText = ($Display -join "`n")
    $windowText = ($Window -join "`n")
    $thermalText = ($Thermal -join "`n")
    $powerText = ($Power -join "`n")
    $batteryText = ($Battery -join "`n")

    $api = Get-P4PropertyValue $getpropText 'ro.build.version.sdk'
    if ($api -cnotmatch '^\d{1,3}$') { throw "Unusable API level: '$api'" }

    $securityPatch = Get-P4PropertyValue $getpropText 'ro.build.version.security_patch'
    if ($securityPatch -cnotmatch '^\d{4}-\d{2}-\d{2}$') { throw "Unusable security patch: '$securityPatch'" }

    $modeMatch = Get-P4SingleMatch $displayText '(?m)^\s*mActiveModeId=(?<id>\d+)\s*\r?$' 'active display mode'
    $displayMode = [int]$modeMatch.Groups['id'].Value

    $baseInfo = Get-P4SingleMatch $displayText '(?m)^\s*mBaseDisplayInfo=DisplayInfo\{[^\r\n]*$' 'base display info'
    $baseInfoText = $baseInfo.Value
    $realMatch = Get-P4SingleMatch $baseInfoText ',\sreal\s(?<w>\d+)\sx\s(?<h>\d+),' 'real display size'
    $width = [int]$realMatch.Groups['w'].Value
    $height = [int]$realMatch.Groups['h'].Value
    if ($width -le 0 -or $height -le 0) { throw 'Real display size must be positive.' }
    $rateMatch = Get-P4SingleMatch $baseInfoText ',\srenderFrameRate\s(?<rate>\d+(?:\.\d+)?),' 'render frame rate'
    $refreshRate = [double]::Parse(
        $rateMatch.Groups['rate'].Value,
        [System.Globalization.CultureInfo]::InvariantCulture
    )
    if ($refreshRate -le 0.0 -or -not [double]::IsFinite($refreshRate)) { throw 'Render frame rate must be finite and positive.' }

    # One parser for rotation across the collector and this module.
    $numericRotation = Read-P4SettingsValue $UserRotation '^[0-3]$' 'user_rotation'
    $rotationState = ConvertFrom-NeneWindowRotationState -NumericText $numericRotation -WindowText $windowText
    if ($rotationState.numeric_user_rotation -ne $rotationState.reported_user_rotation) {
        throw 'The user_rotation setting and WindowManager user rotation disagree.'
    }
    $expectedWidth = if ($rotationState.current_rotation % 2 -eq 0) { $width } else { $height }
    $expectedHeight = if ($rotationState.current_rotation % 2 -eq 0) { $height } else { $width }
    if ($rotationState.logical_width -ne $expectedWidth -or $rotationState.logical_height -ne $expectedHeight) {
        throw 'WindowManager display frames disagree with the real display size for the current rotation.'
    }

    $thermalMatch = Get-P4SingleMatch $thermalText '(?m)^\s*Thermal Status:\s*(?<status>\d+)\s*\r?$' 'thermal status'
    $wakefulnessMatch = Get-P4SingleMatch $powerText '(?m)^\s*mWakefulness=(?<state>[A-Za-z_]+)\s*\r?$' 'wakefulness'
    $usbMatch = Get-P4SingleMatch $batteryText '(?m)^\s*USB powered:\s*(?<value>true|false)\s*\r?$' 'USB power state'
    $levelMatch = Get-P4SingleMatch $batteryText '(?m)^\s*level:\s*(?<level>\d{1,3})\s*\r?$' 'battery level'
    $batteryLevel = [int]$levelMatch.Groups['level'].Value
    if ($batteryLevel -lt 0 -or $batteryLevel -gt 100) { throw "Unusable battery level: $batteryLevel" }

    $locale = Read-P4SettingsValue $Locales '^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*(?:,[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*)*$' 'system_locales'

    $state = [ordered]@{
        serial = Get-P4PropertyValue $getpropText 'ro.serialno'
        manufacturer = Get-P4PropertyValue $getpropText 'ro.product.manufacturer'
        model = Get-P4PropertyValue $getpropText 'ro.product.model'
        product = Get-P4PropertyValue $getpropText 'ro.product.name'
        device = Get-P4PropertyValue $getpropText 'ro.product.device'
        api = [int]$api
        fingerprint = Get-P4PropertyValue $getpropText 'ro.build.fingerprint'
        security_patch = $securityPatch
        display_mode = $displayMode
        width = $width
        height = $height
        refresh_rate = $refreshRate.ToString('F1', [System.Globalization.CultureInfo]::InvariantCulture)
        rotation = [int]$rotationState.current_rotation
        thermal = [int]$thermalMatch.Groups['status'].Value
        power_save = (Read-P4SettingsValue $LowPower '^[01]$' 'low_power')
        interactive = $(if ($wakefulnessMatch.Groups['state'].Value -ceq 'Awake') { 'true' } else { 'false' })
        usb_power = $usbMatch.Groups['value'].Value
        battery = $batteryLevel
        locale = $locale
        user_rotation = [int]$numericRotation
        stay_awake = (Read-P4SettingsValue $StayAwake '^(?:null|[0-7])$' 'stay_on_while_plugged_in')
        captured_utc = $(if ([string]::IsNullOrWhiteSpace($CapturedUtc)) {
                [datetime]::UtcNow.ToString('o', [System.Globalization.CultureInfo]::InvariantCulture)
            } else { $CapturedUtc })
    }
    foreach ($key in $script:P4DeviceStateKeys) {
        if (-not $state.Contains($key)) { throw "Device state is missing the contracted field: $key" }
    }
    if ($state.Count -ne $script:P4DeviceStateKeys.Count) { throw 'Device state carries unexpected fields.' }
    return $state
}

function Invoke-P4BoundedDeviceCommand {
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$LogPath,
        [Parameter(Mandatory = $true)][string[]]$ShellArguments,
        [ValidateRange(1, 300)][int]$TimeoutSeconds = 30
    )

    $result = Invoke-BoundedNativeCommand -RepositoryRoot $RepositoryRoot -LogPath $LogPath `
        -ExecutablePath $AdbPath -NativeArguments (@('-s', $Serial) + $ShellArguments) `
        -TimeoutSeconds $TimeoutSeconds
    if ($result.ExitCode -ne 0) {
        throw "Device command failed (exit $($result.ExitCode)): adb $($ShellArguments -join ' ')"
    }
    return @($result.OutputLines)
}

function Get-P4LiveDeviceState {
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$Serial,
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)][ValidatePattern('^[a-z][a-z0-9-]{1,31}$')][string]$Stage,
        [Parameter(Mandatory = $true)][string]$RepositoryRoot
    )

    $stateDirectory = [System.IO.Path]::GetFullPath($Directory)
    if (-not (Test-Path -LiteralPath $stateDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $stateDirectory -ErrorAction Stop | Out-Null
    }
    $capturedUtc = [datetime]::UtcNow.ToString('o', [System.Globalization.CultureInfo]::InvariantCulture)
    $dump = {
        param([string]$Name, [string[]]$Arguments)
        Invoke-P4BoundedDeviceCommand -AdbPath $AdbPath -Serial $Serial -RepositoryRoot $RepositoryRoot `
            -LogPath (Join-Path $stateDirectory "$Stage-$Name.txt") -ShellArguments $Arguments -TimeoutSeconds 30
    }

    $getprop = & $dump 'getprop' @('shell', 'getprop')
    $display = & $dump 'dumpsys-display' @('shell', 'dumpsys', 'display')
    $window = & $dump 'dumpsys-window' @('shell', 'dumpsys', 'window')
    $thermal = & $dump 'dumpsys-thermalservice' @('shell', 'dumpsys', 'thermalservice')
    $power = & $dump 'dumpsys-power' @('shell', 'dumpsys', 'power')
    $battery = & $dump 'dumpsys-battery' @('shell', 'dumpsys', 'battery')
    $lowPower = (& $dump 'settings-low-power' @('shell', 'settings', 'get', 'global', 'low_power')) -join ''
    $locales = (& $dump 'settings-system-locales' @('shell', 'settings', 'get', 'system', 'system_locales')) -join ''
    $userRotation = (& $dump 'settings-user-rotation' @('shell', 'settings', 'get', 'system', 'user_rotation')) -join ''
    $stayAwake = (& $dump 'settings-stay-awake' @(
            'shell', 'settings', 'get', 'global', 'stay_on_while_plugged_in')) -join ''

    $state = ConvertFrom-P4DeviceDumps -Getprop $getprop -Display $display -Window $window -Thermal $thermal `
        -Power $power -Battery $battery -LowPower $lowPower -Locales $locales -UserRotation $userRotation `
        -StayAwake $stayAwake -CapturedUtc $capturedUtc
    if ($state.serial -cne $Serial) {
        throw "Device serial mismatch: addressed $Serial but the device reports $($state.serial)."
    }
    $record = [ordered]@{ schema = $script:P4DeviceStateSchema; stage = $Stage }
    foreach ($key in $script:P4DeviceStateKeys) { $record[$key] = $state[$key] }
    Write-NewInvocationFile -Path (Join-Path $stateDirectory "device-state-$Stage.json") `
        -Text ($record | ConvertTo-Json -Depth 5)
    return $state
}

function Assert-P4DeviceStateMatches {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Expected,
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Observed,
        [Parameter(Mandatory = $true)][string]$Context
    )

    foreach ($key in $script:P4DeviceStateExactKeys) {
        if (-not $Expected.Contains($key)) { throw "Expected device state omits $key ($Context)." }
        if (-not $Observed.Contains($key)) { throw "Observed device state omits $key ($Context)." }
        if ([string]$Observed[$key] -cne [string]$Expected[$key]) {
            throw "Device drift at $key ($Context): expected $($Expected[$key]), observed $($Observed[$key])."
        }
    }
    foreach ($key in $script:P4DeviceStateRequiredValues.Keys) {
        if ([string]$Observed[$key] -cne $script:P4DeviceStateRequiredValues[$key]) {
            throw ("Device is not in the accepted measurement state at $key ($Context): " +
                "required $($script:P4DeviceStateRequiredValues[$key]), observed $($Observed[$key]).")
        }
    }
    $thermal = [int]$Observed.thermal
    if ($thermal -gt $script:P4DeviceThermalMaximum) {
        throw "Device thermal status $thermal exceeds the accepted maximum $($script:P4DeviceThermalMaximum) ($Context)."
    }
    $battery = [int]$Observed.battery
    if ($battery -lt $script:P4DeviceBatteryMinimum) {
        throw "Device battery $battery% is below the accepted minimum $($script:P4DeviceBatteryMinimum)% ($Context)."
    }
    # rotation is recorded, not gated here: the collectors pin and restore it themselves.
    # Assert-* refuses by throwing and never writes to the pipeline.
}

function Get-P4PackageDexoptStatusFromText {
    <#
        Pure reader over `dumpsys package <pkg>`. The dump repeats the package name in several
        unrelated sections (Key Set Manager, Compiler stats, ...), so the Dexopt state section is
        isolated first and only then handed to the shared M2 block reader.
    #>
    param(
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$DumpsysText,
        [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$Package
    )

    $normalized = $DumpsysText -replace "`r`n", "`n"
    $sectionMatch = [regex]::Match($normalized, '(?m)^Dexopt state:[ \t]*$')
    if (-not $sectionMatch.Success) { throw 'The package dump has no Dexopt state section.' }
    $start = $sectionMatch.Index + $sectionMatch.Length
    $nextSection = [regex]::Match($normalized.Substring($start), '(?m)^\S')
    $section = if ($nextSection.Success) {
        $normalized.Substring($start, $nextSection.Index)
    } else {
        $normalized.Substring($start)
    }
    $packagePattern = '(?m)^[ \t]*\[' + [regex]::Escape($Package) + '\][ \t]*$'
    if (-not [regex]::IsMatch($section, $packagePattern)) { return 'not-installed' }

    $block = Get-M2PackageDexoptBlock -DexoptText $section -PackageName $Package
    $statuses = [System.Collections.Generic.List[string]]::new()
    foreach ($statusMatch in [regex]::Matches($block, '\[status=(?<status>[A-Za-z0-9-]+)\]')) {
        $status = $statusMatch.Groups['status'].Value
        if (-not $statuses.Contains($status)) { $statuses.Add($status) }
    }
    if ($statuses.Count -eq 0) { throw "The Dexopt state block for $Package reports no status." }
    if ($statuses.Count -ne 1) {
        throw "The Dexopt state block for $Package reports conflicting statuses: $($statuses -join ', ')"
    }
    return $statuses[0]
}

function Get-P4PackageDexoptStatus {
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$Serial,
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)][ValidateNotNullOrEmpty()][string]$Package,
        [Parameter(Mandatory = $true)][ValidatePattern('^[a-z][a-z0-9-]{1,31}$')][string]$Stage,
        # Only the working directory of the bounded launcher; defaults to this repository.
        [string]$RepositoryRoot = ([System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..')))
    )

    $dexoptDirectory = [System.IO.Path]::GetFullPath($Directory)
    if (-not (Test-Path -LiteralPath $dexoptDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $dexoptDirectory -ErrorAction Stop | Out-Null
    }
    $logName = "$Stage-dumpsys-package-" + ($Package -replace '[^A-Za-z0-9]', '_') + '.txt'
    $lines = Invoke-P4BoundedDeviceCommand -AdbPath $AdbPath -Serial $Serial -RepositoryRoot $RepositoryRoot `
        -LogPath (Join-Path $dexoptDirectory $logName) -ShellArguments @('shell', 'dumpsys', 'package', $Package) `
        -TimeoutSeconds 30
    $text = ($lines -join "`n")
    if ([regex]::IsMatch($text, '(?m)^\s*Unable to find package:')) { return 'not-installed' }
    return Get-P4PackageDexoptStatusFromText -DumpsysText $text -Package $Package
}

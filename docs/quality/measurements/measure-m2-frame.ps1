[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("debug", "release-like")]
    [string]$Variant,

    [Parameter(Mandatory = $true)]
    [string]$DeviceSerial,

    [Parameter(Mandatory = $true)]
    [string]$ApkPath,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{40}$")]
    [string]$SourceCommit,

    [Parameter(Mandatory = $true)]
    [string]$ExperimentDirectory,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[a-z0-9][a-z0-9-]{2,63}$")]
    [string]$ExperimentId,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{40}$")]
    [string]$BaselineSourceCommit,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{40}$")]
    [string]$CandidateSourceCommit,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$BaselineApkSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9a-f]{64}$")]
    [string]$CandidateApkSha256,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$CandidateHypothesis,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$ExpectedAffectedCost,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$CorrectnessRisk,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$StopConditions,

    [Parameter(Mandatory = $true)]
    [ValidateSet("diagnostic", "decision")]
    [string]$RunKind,

    [Parameter(Mandatory = $true)]
    [ValidateSet("baseline", "candidate")]
    [string]$CandidateRole,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 4)]
    [int]$ComparisonSequenceIndex,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 2)]
    [int]$Attempt,

    [ValidateSet("speed-profile", "speed")]
    [string]$CompilationMode,

    [Parameter(Mandatory = $true)]
    [ValidateSet(10, 50)]
    [int]$SampleCount,

    [switch]$ValidateExperimentOnly,

    [string]$PhysicalPresentTraceProcessorPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $PSBoundParameters.ContainsKey("CompilationMode")) {
    $CompilationMode = if ($Variant -eq "release-like") { "speed-profile" } else { "speed" }
}

$packageName = "io.github.hideyukimori.nenepixel"
$activityName = "$packageName/.MainActivity"
$physicalProfileId = "NENE-P2-ALLDOCUBE-IPL80MP-A16-API36"
$expectedManufacturer = "ALLDOCUBE"
$expectedModel = "iPlay80miniPro"
$expectedProduct = "iPlay80miniPro"
$expectedDevice = "T830"
$expectedApiLevel = 36
$expectedDisplayWidth = 1200
$expectedDisplayHeight = 1920
$expectedDisplayModeId = 1
$expectedRefreshRateHertz = 90.0
$refreshRateToleranceHertz = 0.1
$maximumThermalStatus = 1
$warmupCount = 5
$previewWaitMilliseconds = 100
$drawWaitMilliseconds = 350
$undoWaitMilliseconds = 150
$undoAttemptLimit = 3
$profileInstallSuccessResult = 1
$inputInjection = "cmd-input-service-direct"
$remotePrefix = "/data/local/tmp/nene-m2-frame-$Variant-$CompilationMode"
$resolvedExperiment =
    if ([System.IO.Path]::IsPathRooted($ExperimentDirectory)) {
        [System.IO.Path]::GetFullPath($ExperimentDirectory)
    } else {
        [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $ExperimentDirectory))
    }
$physicalPresentEnabled = $PSBoundParameters.ContainsKey("PhysicalPresentTraceProcessorPath")
$physicalPresentSchema = "nene-pixel-m2-physical-present-v2"
$frameSchema = "nene-pixel-m2-actual-app-frame-v7"
$experimentSchema = "nene-pixel-m2-frame-experiment-v2"
$physicalPresentAnalyzer = Join-Path $PSScriptRoot "analyze-m2-physical-present.ps1"
$physicalTraceState = $null
$physicalAnalysis = $null

if ($physicalPresentEnabled) {
    throw "$physicalPresentSchema collection is exhausted and retained for historical analysis only."
}
$expectedSampleCount = if ($RunKind -eq "diagnostic") { 10 } else { 50 }
if ($SampleCount -ne $expectedSampleCount) {
    throw "$RunKind collection requires exactly $expectedSampleCount operation samples."
}
$comparisonOrder = @(
    "diagnostic:baseline",
    "diagnostic:candidate",
    "decision:candidate",
    "decision:baseline"
)
$comparisonIdentity = "$RunKind`:$CandidateRole"
if ($comparisonOrder[$ComparisonSequenceIndex - 1] -ne $comparisonIdentity) {
    throw "Comparison sequence $ComparisonSequenceIndex requires '$($comparisonOrder[$ComparisonSequenceIndex - 1])'."
}
if ($BaselineSourceCommit -eq $CandidateSourceCommit -or $BaselineApkSha256 -eq $CandidateApkSha256) {
    throw "The prospective experiment requires distinct baseline and candidate source/APK identities."
}
$expectedSourceCommit = if ($CandidateRole -eq "baseline") { $BaselineSourceCommit } else { $CandidateSourceCommit }
$expectedApkSha256 = if ($CandidateRole -eq "baseline") { $BaselineApkSha256 } else { $CandidateApkSha256 }
if ($SourceCommit -ne $expectedSourceCommit) {
    throw "The supplied source commit does not match the fixed $CandidateRole source identity."
}
$slotName = "slot-{0:D2}-{1}-{2}" -f $ComparisonSequenceIndex, $RunKind, $CandidateRole
$resolvedOutput = Join-Path $resolvedExperiment "$slotName-attempt-$Attempt"
$experimentManifestPath = Join-Path $resolvedExperiment "experiment.json"
$experimentManifest =
    [ordered]@{
        schema = $experimentSchema
        experiment_id = $ExperimentId
        baseline_source_commit = $BaselineSourceCommit
        candidate_source_commit = $CandidateSourceCommit
        baseline_apk_sha256 = $BaselineApkSha256
        candidate_apk_sha256 = $CandidateApkSha256
        candidate_hypothesis = $CandidateHypothesis
        expected_affected_cost = $ExpectedAffectedCost
        correctness_risk = $CorrectnessRisk
        stop_conditions = $StopConditions
        comparison_order = $comparisonOrder
        slot_budget = 4
        maximum_attempts_per_slot = 2
        replacement_rule = "attempt 2 only after attempt 1 is invalid before the first measured DOWN"
    }
$expectedManifestText = $experimentManifest | ConvertTo-Json -Depth 4
if ($ComparisonSequenceIndex -eq 1 -and $Attempt -eq 1) {
    if (-not (Test-Path -LiteralPath $resolvedExperiment)) {
        New-Item -ItemType Directory -Path $resolvedExperiment | Out-Null
        [System.IO.File]::WriteAllText(
            $experimentManifestPath,
            $expectedManifestText,
            [System.Text.UTF8Encoding]::new($false)
        )
    }
    elseif (-not (Test-Path -LiteralPath $experimentManifestPath -PathType Leaf)) {
        throw "An existing experiment directory must contain its fixed manifest."
    }
    else {
        $actualManifestText = Get-Content -Raw -LiteralPath $experimentManifestPath
        if (($actualManifestText | ConvertFrom-Json | ConvertTo-Json -Depth 4) -cne $expectedManifestText) {
            throw "The invocation does not match the fixed experiment manifest."
        }
    }
}
elseif (-not (Test-Path -LiteralPath $experimentManifestPath -PathType Leaf)) {
    throw "The fixed experiment manifest is missing: $experimentManifestPath"
}
else {
    $actualManifestText = Get-Content -Raw -LiteralPath $experimentManifestPath
    if (($actualManifestText | ConvertFrom-Json | ConvertTo-Json -Depth 4) -cne $expectedManifestText) {
        throw "The invocation does not match the fixed experiment manifest."
    }
}

function Get-RunState {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Directory
    )

    $path = Join-Path $Directory "run-state.json"
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        return $null
    }
    return Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
}

function Test-RunStateIdentity {
    param(
        [object]$State,
        [Parameter(Mandatory = $true)][int]$SequenceIndex,
        [Parameter(Mandatory = $true)][int]$ExpectedAttempt
    )

    return (
        $null -ne $State -and
        $State.schema -eq $experimentSchema -and
        $State.experiment_id -eq $ExperimentId -and
        [int]$State.comparison_sequence_index -eq $SequenceIndex -and
        [int]$State.attempt -eq $ExpectedAttempt
    )
}

function Get-OperationTiming {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$PreviewRows,

        [Parameter(Mandatory = $true)]
        [object[]]$CommitRows
    )

    if ($PreviewRows.Count -lt 1 -or $CommitRows.Count -lt 1) {
        throw "Operation timing requires at least one preview and commit frame."
    }
    $previewInputStart = [long]($PreviewRows.handle_input_start_nanos | Measure-Object -Minimum).Minimum
    $commitInputStart = [long]($CommitRows.handle_input_start_nanos | Measure-Object -Minimum).Minimum
    $committedResultCompletion = [long]($CommitRows.frame_completed_nanos | Measure-Object -Maximum).Maximum
    if ($previewInputStart -le 0 -or $commitInputStart -le $previewInputStart -or $committedResultCompletion -le $commitInputStart) {
        throw "Operation frame timestamps do not preserve DOWN, UP, and committed-result order."
    }
    return [pscustomobject]@{
        preview_input_start_nanos = $previewInputStart
        commit_input_start_nanos = $commitInputStart
        committed_result_completion_nanos = $committedResultCompletion
        input_to_committed_result_ms = ($committedResultCompletion - $commitInputStart) / 1000000.0
        down_to_committed_result_ms = ($committedResultCompletion - $previewInputStart) / 1000000.0
    }
}

if ($ComparisonSequenceIndex -gt 1) {
    $previousIdentity = $comparisonOrder[$ComparisonSequenceIndex - 2].Split(":")
    $previousSlot = "slot-{0:D2}-{1}-{2}" -f ($ComparisonSequenceIndex - 1), $previousIdentity[0], $previousIdentity[1]
    $previousAttempt = 1
    $previousState = Get-RunState -Directory (Join-Path $resolvedExperiment "$previousSlot-attempt-1")
    if ($null -ne $previousState -and $previousState.status -eq "invalid-before-samples") {
        $previousAttempt = 2
        $previousState = Get-RunState -Directory (Join-Path $resolvedExperiment "$previousSlot-attempt-2")
    }
    $requiredPreviousVerdict = if ($ComparisonSequenceIndex -eq 4) { "pass" } else { "inconclusive" }
    if (
        -not (Test-RunStateIdentity -State $previousState -SequenceIndex ($ComparisonSequenceIndex - 1) -ExpectedAttempt $previousAttempt) -or
        $previousState.status -ne "completed" -or
        $previousState.verdict -ne $requiredPreviousVerdict
    ) {
        throw "Sequence slot $ComparisonSequenceIndex requires completed slot $($ComparisonSequenceIndex - 1) verdict '$requiredPreviousVerdict'."
    }
}
if ($Attempt -eq 2) {
    $firstAttemptState = Get-RunState -Directory (Join-Path $resolvedExperiment "$slotName-attempt-1")
    if (
        -not (Test-RunStateIdentity -State $firstAttemptState -SequenceIndex $ComparisonSequenceIndex -ExpectedAttempt 1) -or
        $firstAttemptState.status -ne "invalid-before-samples" -or
        [int]$firstAttemptState.measured_down_count -ne 0
    ) {
        throw "Attempt 2 requires attempt 1 to be proven invalid before the first measured DOWN."
    }
}
if ($RunKind -eq "decision" -and ($Variant -ne "release-like" -or $CompilationMode -ne "speed-profile")) {
    throw "Decision collection requires release-like and speed-profile."
}
if ($ValidateExperimentOnly) {
    $modelTiming = Get-OperationTiming -PreviewRows @(
        [pscustomobject]@{
            handle_input_start_nanos = 1000000000L
            frame_completed_nanos = 1010000000L
        }
    ) -CommitRows @(
        [pscustomobject]@{
            handle_input_start_nanos = 1120000000L
            frame_completed_nanos = 1140000000L
        },
        [pscustomobject]@{
            handle_input_start_nanos = 1121000000L
            frame_completed_nanos = 1145000000L
        }
    )
    [pscustomobject]@{
        experiment_id = $ExperimentId
        slot = $slotName
        attempt = $Attempt
        validation = "pass"
        model_input_to_committed_result_ms = $modelTiming.input_to_committed_result_ms
        model_down_to_committed_result_ms = $modelTiming.down_to_committed_result_ms
    }
    return
}

$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
$requiredProfileEntries = @("assets/dexopt/baseline.prof", "assets/dexopt/baseline.profm")
$embeddedSourceCommit = $null
$apkArchive = [System.IO.Compression.ZipFile]::OpenRead($resolvedApk)
try {
    $apkEntryNames = @($apkArchive.Entries | ForEach-Object { $_.FullName })
    $versionControlEntry = $apkArchive.GetEntry("META-INF/version-control-info.textproto")
    if ($null -ne $versionControlEntry) {
        $versionControlReader = [System.IO.StreamReader]::new($versionControlEntry.Open())
        try {
            $versionControlText = $versionControlReader.ReadToEnd()
        }
        finally {
            $versionControlReader.Dispose()
        }
        $revisionMatch = [regex]::Match($versionControlText, '(?m)^\s*revision:\s*"([0-9a-f]{40})"\s*$')
        if ($revisionMatch.Success) {
            $embeddedSourceCommit = $revisionMatch.Groups[1].Value
        }
    }
}
finally {
    $apkArchive.Dispose()
}
$trackedStatus = @(& git status --porcelain --untracked-files=no 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "Unable to verify the repository worktree before frame collection."
}
if ($trackedStatus.Count -gt 0) {
    throw "Frame collection requires a clean tracked worktree."
}
$repositoryHead = (& git rev-parse HEAD 2>&1 | Select-Object -First 1).Trim()
if ($LASTEXITCODE -ne 0 -or $repositoryHead -ne $SourceCommit) {
    throw "The supplied source commit does not match the repository HEAD."
}
if ($Variant -eq "release-like") {
    if ($null -eq $embeddedSourceCommit) {
        throw "The release-like APK has no readable embedded Git revision."
    }
    if ($embeddedSourceCommit -ne $SourceCommit) {
        throw "The release-like APK Git revision does not match the supplied source commit."
    }
}
$missingProfileEntries = @($requiredProfileEntries | Where-Object { $_ -notin $apkEntryNames })
if ($CompilationMode -eq "speed-profile" -and $missingProfileEntries.Count -gt 0) {
    throw "speed-profile requires packaged APK entries: $($missingProfileEntries -join ', ')."
}
$apkHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedApk).Hash.ToLowerInvariant()
if ($apkHash -cne $expectedApkSha256) {
    throw "The APK does not match the fixed $CandidateRole SHA-256 identity."
}

if (Test-Path -LiteralPath $resolvedOutput) {
    throw "Frame output already exists: $resolvedOutput"
}

New-Item -ItemType Directory -Path $resolvedOutput | Out-Null
New-Item -ItemType Directory -Path (Join-Path $resolvedOutput "raw") | Out-Null
$runStatePath = Join-Path $resolvedOutput "run-state.json"
$script:measuredDownCount = 0
function Write-RunState {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Status,

        [Parameter(Mandatory = $true)]
        [string]$Verdict
    )

    $state =
        [ordered]@{
            schema = $experimentSchema
            experiment_id = $ExperimentId
            comparison_sequence_index = $ComparisonSequenceIndex
            attempt = $Attempt
            status = $Status
            verdict = $Verdict
            measured_down_count = $script:measuredDownCount
        }
    [System.IO.File]::WriteAllText(
        $runStatePath,
        ($state | ConvertTo-Json),
        [System.Text.UTF8Encoding]::new($false)
    )
}
Write-RunState -Status "running" -Verdict "unavailable"

function Invoke-TargetAdb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$AdbArguments
    )

    $commandOutput = @(& adb -s $DeviceSerial @AdbArguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed ($LASTEXITCODE): adb -s <physical-device> $($AdbArguments -join ' ')`n$($commandOutput -join "`n")"
    }
    return $commandOutput | ForEach-Object { $_.ToString() }
}

function Get-TargetProperty {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $value = (Invoke-TargetAdb -AdbArguments @("shell", "getprop", $Name) | Select-Object -First 1).Trim()
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Physical device property $Name is unavailable."
    }
    return $value
}

function Get-PhysicalDeviceIdentity {
    $emulatorFlag = (Invoke-TargetAdb -AdbArguments @("shell", "getprop", "ro.kernel.qemu") | Select-Object -First 1).Trim()
    if ($emulatorFlag -eq "1") {
        throw "The fixed frame protocol requires a physical device."
    }

    $manufacturer = Get-TargetProperty -Name "ro.product.manufacturer"
    $model = Get-TargetProperty -Name "ro.product.model"
    $product = Get-TargetProperty -Name "ro.product.name"
    $device = Get-TargetProperty -Name "ro.product.device"
    $apiLevelText = Get-TargetProperty -Name "ro.build.version.sdk"
    $apiLevel = 0
    if (-not [int]::TryParse($apiLevelText, [ref]$apiLevel)) {
        throw "Physical device API level is invalid: $apiLevelText."
    }
    $identityMatches =
        [string]::Equals($manufacturer, $expectedManufacturer, [System.StringComparison]::OrdinalIgnoreCase) -and
        [string]::Equals($model, $expectedModel, [System.StringComparison]::Ordinal) -and
        [string]::Equals($product, $expectedProduct, [System.StringComparison]::Ordinal) -and
        [string]::Equals($device, $expectedDevice, [System.StringComparison]::Ordinal) -and
        $apiLevel -eq $expectedApiLevel
    if (-not $identityMatches) {
        throw "The connected hardware does not match physical profile $physicalProfileId."
    }

    return [pscustomobject]@{
        manufacturer = $manufacturer
        model = $model
        product = $product
        device = $device
        api_level = $apiLevel
        build_fingerprint = Get-TargetProperty -Name "ro.build.fingerprint"
        security_patch = Get-TargetProperty -Name "ro.build.version.security_patch"
    }
}

function Get-PhysicalCheckpoint {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $wmText = (Invoke-TargetAdb -AdbArguments @("shell", "wm", "size")) -join "`n"
    $physicalSizeMatch = [regex]::Match($wmText, "(?m)^Physical size:\s*(\d+)x(\d+)\s*$")
    if (-not $physicalSizeMatch.Success) {
        throw "Physical display size is unavailable at checkpoint $Name."
    }
    $physicalWidth = [int]$physicalSizeMatch.Groups[1].Value
    $physicalHeight = [int]$physicalSizeMatch.Groups[2].Value

    $displayText = (Invoke-TargetAdb -AdbArguments @("shell", "dumpsys", "display")) -join "`n"
    $displayMatch =
        [regex]::Match(
            $displayText,
            "DisplayDeviceInfo\{.*?,\s*(\d+)\s+x\s+(\d+),\s+modeId\s+(\d+),.*?supportedModes\s+\[(.*?)\]",
            [System.Text.RegularExpressions.RegexOptions]::Singleline
        )
    if (-not $displayMatch.Success) {
        throw "Active display mode is unavailable at checkpoint $Name."
    }
    $activeWidth = [int]$displayMatch.Groups[1].Value
    $activeHeight = [int]$displayMatch.Groups[2].Value
    $displayModeId = [int]$displayMatch.Groups[3].Value
    $activeModeMatch =
        [regex]::Match(
            $displayMatch.Groups[4].Value,
            "\{id=$displayModeId,\s*width=$activeWidth,\s*height=$activeHeight,\s*fps=([0-9.]+)"
        )
    if (-not $activeModeMatch.Success) {
        throw "Active display refresh rate is unavailable at checkpoint $Name."
    }
    $refreshRateHertz =
        [double]::Parse($activeModeMatch.Groups[1].Value, [System.Globalization.CultureInfo]::InvariantCulture)

    $thermalText = (Invoke-TargetAdb -AdbArguments @("shell", "dumpsys", "thermalservice")) -join "`n"
    $thermalMatch = [regex]::Match($thermalText, "Thermal Status:\s*(\d+)")
    if (-not $thermalMatch.Success) {
        throw "Thermal status is unavailable at checkpoint $Name."
    }
    $thermalStatus = [int]$thermalMatch.Groups[1].Value

    $lowPowerText = (Invoke-TargetAdb -AdbArguments @("shell", "settings", "get", "global", "low_power") | Select-Object -First 1).Trim()
    if ($lowPowerText -notin @("0", "1")) {
        throw "Power-save state is unavailable at checkpoint $Name."
    }
    $powerSaveMode = $lowPowerText -eq "1"

    $powerText = (Invoke-TargetAdb -AdbArguments @("shell", "dumpsys", "power")) -join "`n"
    $interactive = $powerText -match "mWakefulness=Awake"

    $batteryText = (Invoke-TargetAdb -AdbArguments @("shell", "dumpsys", "battery")) -join "`n"
    $usbPoweredMatch = [regex]::Match($batteryText, "(?m)^\s*USB powered:\s*(true|false)\s*$")
    $batteryLevelMatch = [regex]::Match($batteryText, "(?m)^\s*level:\s*(\d+)\s*$")
    if (-not $usbPoweredMatch.Success -or -not $batteryLevelMatch.Success) {
        throw "Battery state is unavailable at checkpoint $Name."
    }
    $usbPowered = $usbPoweredMatch.Groups[1].Value -eq "true"
    $batteryLevel = [int]$batteryLevelMatch.Groups[1].Value

    if (
        $physicalWidth -ne $expectedDisplayWidth -or
        $physicalHeight -ne $expectedDisplayHeight -or
        $activeWidth -ne $expectedDisplayWidth -or
        $activeHeight -ne $expectedDisplayHeight -or
        $displayModeId -ne $expectedDisplayModeId -or
        [Math]::Abs($refreshRateHertz - $expectedRefreshRateHertz) -gt $refreshRateToleranceHertz
    ) {
        throw "Display state does not match physical profile $physicalProfileId at checkpoint $Name."
    }
    if ($thermalStatus -gt $maximumThermalStatus) {
        throw "Thermal status $thermalStatus exceeds the physical evidence limit at checkpoint $Name."
    }
    if ($powerSaveMode -or -not $interactive -or -not $usbPowered -or $batteryLevel -lt 0 -or $batteryLevel -gt 100) {
        throw "Power state does not match physical profile $physicalProfileId at checkpoint $Name."
    }

    return [pscustomobject]@{
        checkpoint = $Name
        host_timestamp = (Get-Date).ToString("o")
        physical_width = $physicalWidth
        physical_height = $physicalHeight
        active_width = $activeWidth
        active_height = $activeHeight
        display_mode_id = $displayModeId
        refresh_rate_hertz = $refreshRateHertz
        thermal_status = $thermalStatus
        power_save_mode = $powerSaveMode
        interactive = $interactive
        usb_powered = $usbPowered
        battery_level_percent = $batteryLevel
    }
}

function Get-RequiredMatchValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Text,

        [Parameter(Mandatory = $true)]
        [string]$Pattern,

        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $match = [regex]::Match($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if (-not $match.Success) {
        throw "Missing $Name in gfxinfo output."
    }
    return [long]$match.Groups[1].Value
}

function Get-Bounds {
    param(
        [Parameter(Mandatory = $true)]
        [System.Xml.XmlElement]$Node
    )

    $match = [regex]::Match($Node.GetAttribute("bounds"), "^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$")
    if (-not $match.Success) {
        throw "UI node has invalid bounds."
    }
    return [pscustomobject]@{
        Left = [int]$match.Groups[1].Value
        Top = [int]$match.Groups[2].Value
        Right = [int]$match.Groups[3].Value
        Bottom = [int]$match.Groups[4].Value
    }
}

function Get-NearestRank {
    param(
        [Parameter(Mandatory = $true)]
        [double[]]$Values,

        [Parameter(Mandatory = $true)]
        [double]$Percentile
    )

    if ($Values.Count -eq 0) {
        throw "Cannot calculate a percentile from an empty collection."
    }
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Ceiling($sorted.Count * $Percentile) - 1
    return $sorted[$index]
}

function Get-FrameRows {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string[]]$GfxInfo,

        [Parameter(Mandatory = $true)]
        [int]$SampleIndex,

        [Parameter(Mandatory = $true)]
        [ValidateSet("preview", "commit")]
        [string]$Phase
    )

    $headerIndex =
        0..($GfxInfo.Count - 1) |
            Where-Object { $GfxInfo[$_] -like "Flags,FrameTimelineVsyncId,*" } |
            Select-Object -First 1
    if ($null -eq $headerIndex) {
        throw "Sample $SampleIndex has no PROFILEDATA header."
    }

    $rows = [System.Collections.Generic.List[object]]::new()
    for ($lineIndex = $headerIndex + 1; $lineIndex -lt $GfxInfo.Count; $lineIndex += 1) {
        $line = $GfxInfo[$lineIndex]
        if ($line -eq "---PROFILEDATA---") {
            break
        }
        if ($line -notmatch "^\d+,") {
            continue
        }
        $frame = @($GfxInfo[$headerIndex], $line) | ConvertFrom-Csv
        $completed = [long]$frame.FrameCompleted
        $started = [long]$frame.FrameStartTime
        $intended = [long]$frame.IntendedVsync
        $deadline = [long]$frame.FrameDeadline
        $inputStarted = [long]$frame.HandleInputStart
        if ($completed -le 0 -or $started -le 0 -or $intended -le 0 -or $deadline -le 0 -or $inputStarted -le 0) {
            throw "Sample $SampleIndex contains unavailable required frame fields."
        }
        $rows.Add(
            [pscustomobject]@{
                variant = $Variant
                source_commit = $SourceCommit
                sample_index = $SampleIndex
                phase = $Phase
                row_index = $rows.Count + 1
                flags = [int]$frame.Flags
                frame_timeline_vsync_id = [long]$frame.FrameTimelineVsyncId
                intended_vsync_nanos = $intended
                frame_start_nanos = $started
                handle_input_start_nanos = $inputStarted
                draw_start_nanos = [long]$frame.DrawStart
                frame_deadline_nanos = $deadline
                frame_completed_nanos = $completed
                display_present_time_nanos = [long]$frame.DisplayPresentTime
                frame_duration_cpu_ms = ($completed - $started) / 1000000.0
                app_frame_total_ms = ($completed - $intended) / 1000000.0
                frame_overrun_ms = ($completed - $deadline) / 1000000.0
                input_start_to_completion_ms = ($completed - $inputStarted) / 1000000.0
            }
        )
    }
    return $rows
}

function Get-OperationPhaseCapture {
    param(
        [Parameter(Mandatory = $true)]
        [int]$SampleIndex,

        [Parameter(Mandatory = $true)]
        [ValidateSet("preview", "commit")]
        [string]$Phase,

        [Parameter(Mandatory = $true)]
        [ValidateSet("DOWN", "UP")]
        [string]$MotionEvent,

        [Parameter(Mandatory = $true)]
        [int]$WaitMilliseconds,

        [Parameter(Mandatory = $true)]
        [int]$CanvasX,

        [Parameter(Mandatory = $true)]
        [int]$CanvasY
    )

    Invoke-TargetAdb -AdbArguments @("shell", "dumpsys", "gfxinfo", $packageName, "reset") | Out-Null
    Invoke-TargetAdb -AdbArguments @(
        "shell",
        "cmd",
        "input",
        "motionevent",
        $MotionEvent,
        "$CanvasX",
        "$CanvasY"
    ) | Out-Null
    if ($MotionEvent -eq "DOWN") {
        $script:measuredDownCount += 1
        Write-RunState -Status "running" -Verdict "unavailable"
    }
    Start-Sleep -Milliseconds $WaitMilliseconds
    $gfxInfo = @(Invoke-TargetAdb -AdbArguments @("shell", "dumpsys", "gfxinfo", $packageName, "framestats"))
    $gfxText = $gfxInfo -join "`n"
    [System.IO.File]::WriteAllLines(
        (Join-Path $resolvedOutput ("raw/sample-{0:D2}-{1}.txt" -f $SampleIndex, $Phase)),
        $gfxInfo
    )

    $rows = @(Get-FrameRows -GfxInfo $gfxInfo -SampleIndex $SampleIndex -Phase $Phase)
    $validRows = @($rows | Where-Object { $_.flags -eq 0 })
    $totalFramesRendered =
        [int](Get-RequiredMatchValue -Text $gfxText -Pattern "^Total frames rendered:\s+(\d+)" -Name "total frame count")
    if ($totalFramesRendered -lt 1) {
        throw "Sample $SampleIndex $Phase phase must render at least one frame."
    }
    if ($rows.Count -ne $totalFramesRendered) {
        throw "Sample $SampleIndex $Phase raw row count must equal Total frames rendered."
    }
    if ($validRows.Count -ne $rows.Count) {
        throw "Sample $SampleIndex $Phase requires every raw PROFILEDATA row to be valid."
    }

    return [pscustomobject]@{
        Rows = $rows
        TotalFramesRendered = $totalFramesRendered
        JankyFrames =
            [int](Get-RequiredMatchValue -Text $gfxText -Pattern "^Janky frames:\s+(\d+)" -Name "janky frame count")
        DeadlineMissedFrames =
            [int](Get-RequiredMatchValue -Text $gfxText -Pattern "^Number Frame deadline missed:\s+(\d+)\s*$" -Name "deadline miss count")
    }
}

function Invoke-UndoToCleanCheckpoint {
    param(
        [Parameter(Mandatory = $true)]
        [int]$UndoX,

        [Parameter(Mandatory = $true)]
        [int]$UndoY
    )

    $checkpointRemote = "$remotePrefix-checkpoint.xml"
    foreach ($attempt in 1..$undoAttemptLimit) {
        Invoke-TargetAdb -AdbArguments @("shell", "cmd", "input", "tap", "$UndoX", "$UndoY") | Out-Null
        Start-Sleep -Milliseconds $undoWaitMilliseconds
        Invoke-TargetAdb -AdbArguments @("shell", "uiautomator", "dump", $checkpointRemote) | Out-Null
        [xml]$checkpointUi =
            (Invoke-TargetAdb -AdbArguments @("shell", "cat", $checkpointRemote)) -join "`n"
        $dirtyNode = $checkpointUi.SelectSingleNode("//node[@content-desc='Document dirty status']")
        $undoNode = $checkpointUi.SelectSingleNode("//node[@text='Undo']")
        if (
            $null -ne $dirtyNode -and
            $null -ne $undoNode -and
            $dirtyNode.GetAttribute("text") -eq "No unsaved changes" -and
            $undoNode.ParentNode.GetAttribute("enabled") -eq "false"
        ) {
            return
        }
    }
    throw "Undo did not restore the clean checkpoint after $undoAttemptLimit attempts."
}

function Assert-CommittedResult {
    param(
        [Parameter(Mandatory = $true)]
        [int]$SampleIndex
    )

    $checkpointRemote = "$remotePrefix-checkpoint.xml"
    Invoke-TargetAdb -AdbArguments @("shell", "uiautomator", "dump", $checkpointRemote) | Out-Null
    [xml]$checkpointUi =
        (Invoke-TargetAdb -AdbArguments @("shell", "cat", $checkpointRemote)) -join "`n"
    $dirtyNode = $checkpointUi.SelectSingleNode("//node[@content-desc='Document dirty status']")
    $undoNode = $checkpointUi.SelectSingleNode("//node[@text='Undo']")
    $redoNode = $checkpointUi.SelectSingleNode("//node[@text='Redo']")
    if (
        $null -eq $dirtyNode -or
        $null -eq $undoNode -or
        $null -eq $redoNode -or
        $dirtyNode.GetAttribute("text") -ne "Unsaved changes" -or
        $undoNode.ParentNode.GetAttribute("enabled") -ne "true" -or
        $redoNode.ParentNode.GetAttribute("enabled") -ne "false"
    ) {
        throw "Sample $SampleIndex did not expose the committed Pencil result."
    }
}

function Test-RemotePathExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $result =
        (Invoke-TargetAdb -AdbArguments @("shell", "if [ -e '$Path' ]; then echo exists; else echo absent; fi") |
            Select-Object -First 1).Trim()
    if ($result -notin @("exists", "absent")) {
        throw "Unable to determine whether an exact physical-present staging path exists."
    }
    return $result -eq "exists"
}

function Get-PerfettoSessionMatchCount {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SessionName
    )

    $serviceState = @(Invoke-TargetAdb -AdbArguments @("shell", "perfetto", "--query", "--long"))
    return @($serviceState | Where-Object { $_ -like "*$SessionName*" }).Count
}

function Start-PhysicalPresentTrace {
    $batchId = [guid]::NewGuid().ToString("D")
    $triggerName = "nene-m2-physical-present-$batchId"
    $remoteConfig = "/data/misc/perfetto-configs/nene-m2-physical-present-$batchId.txtpb"
    $remoteTrace = "/data/misc/perfetto-traces/nene-m2-physical-present-$batchId.perfetto-trace"
    $localConfig = Join-Path $resolvedOutput "physical-present-config.txtpb"
    $localTrace = Join-Path $resolvedOutput "physical-present.perfetto-trace"
    $toolPath = Join-Path $resolvedOutput "physical-present-tool.txt"
    foreach ($localPath in @($localConfig, $localTrace, $toolPath)) {
        if (Test-Path -LiteralPath $localPath) {
            throw "Physical-present collection refuses to overwrite $localPath."
        }
    }
    foreach ($remotePath in @($remoteConfig, $remoteTrace)) {
        if (Test-RemotePathExists -Path $remotePath) {
            throw "Physical-present collection found a pre-existing exact device staging path."
        }
    }

    $state = [pscustomobject]@{
        BatchId = $batchId
        TriggerName = $triggerName
        RemoteConfig = $remoteConfig
        RemoteTrace = $remoteTrace
        LocalConfig = $localConfig
        LocalTrace = $localTrace
        ToolPath = $toolPath
        LauncherReportedPid = 0
        Active = $false
        StopRequested = $false
    }
    $script:physicalTraceState = $state

    $config = @"
unique_session_name: "$triggerName"
buffers {
  size_kb: 65536
  fill_policy: RING_BUFFER
}
data_sources {
  config {
    name: "android.surfaceflinger.frametimeline"
    target_buffer: 0
  }
}
data_sources {
  config {
    name: "linux.ftrace"
    target_buffer: 0
    ftrace_config {
      ftrace_events: "sched/sched_switch"
      ftrace_events: "sched/sched_waking"
      ftrace_events: "sched/sched_wakeup_new"
      ftrace_events: "power/cpu_frequency"
      ftrace_events: "power/cpu_idle"
      atrace_categories: "am"
      atrace_categories: "binder_driver"
      atrace_categories: "freq"
      atrace_categories: "gfx"
      atrace_categories: "hal"
      atrace_categories: "idle"
      atrace_categories: "input"
      atrace_categories: "sched"
      atrace_categories: "view"
      atrace_apps: "$packageName"
      buffer_size_kb: 16384
      drain_period_ms: 250
      compact_sched {
        enabled: true
      }
    }
  }
}
data_sources {
  config {
    name: "linux.process_stats"
    target_buffer: 0
    process_stats_config {
      scan_all_processes_on_start: true
      proc_stats_poll_ms: 1000
    }
  }
}
builtin_data_sources {
  disable_clock_snapshotting: false
  disable_trace_config: false
  disable_system_info: false
}
flush_period_ms: 5000
trigger_config {
  trigger_mode: STOP_TRACING
  trigger_timeout_ms: 300000
  triggers {
    name: "$triggerName"
    stop_delay_ms: 1000
  }
}
"@
    [System.IO.File]::WriteAllText($localConfig, $config, [System.Text.UTF8Encoding]::new($false))
    Invoke-TargetAdb -AdbArguments @("push", $localConfig, $remoteConfig) | Out-Null
    $startCommand = "perfetto --background-wait --txt -c $remoteConfig -o $remoteTrace"
    $startOutput = @(Invoke-TargetAdb -AdbArguments @("shell", "perfetto", "--background-wait", "--txt", "-c", $remoteConfig, "-o", $remoteTrace))
    $pidMatches = @($startOutput | Where-Object { $_.Trim() -match "^\d+$" })
    if ($pidMatches.Count -ne 1) {
        throw "Perfetto did not return exactly one background tracing PID."
    }
    $launcherReportedPid = [int]$pidMatches[0].Trim()
    $state.LauncherReportedPid = $launcherReportedPid
    $state.Active = $true

    $toolLines = @(
        "schema=$physicalPresentSchema",
        "batch_id=$batchId",
        "source_commit=$SourceCommit",
        "trace_trigger=$triggerName",
        "remote_config=$remoteConfig",
        "remote_trace=$remoteTrace",
        "start_command=adb -s <physical-device> shell $startCommand",
        "launcher_reported_pid=$launcherReportedPid",
        "liveness_check=exact unique session name through perfetto --query --long",
        "stop_command=adb -s <physical-device> shell /system/bin/trigger_perfetto $triggerName",
        "normal_stop=trigger-only; no manual process kill"
    )
    [System.IO.File]::WriteAllLines($toolPath, $toolLines, [System.Text.UTF8Encoding]::new($false))
    $activeSessionCount = Get-PerfettoSessionMatchCount -SessionName $triggerName
    if ($activeSessionCount -ne 1) {
        throw "Perfetto service state does not expose exactly one named physical-present session before samples."
    }

    return $state
}

function Stop-PhysicalPresentTrace {
    param(
        [Parameter(Mandatory = $true)]
        [object]$State
    )

    if (-not $State.Active) {
        throw "Physical-present trace is not active."
    }
    if (-not $State.StopRequested) {
        Invoke-TargetAdb -AdbArguments @("shell", "/system/bin/trigger_perfetto", $State.TriggerName) | Out-Null
        $State.StopRequested = $true
    }
    $deadline = (Get-Date).AddSeconds(30)
    do {
        $activeSessionCount = Get-PerfettoSessionMatchCount -SessionName $State.TriggerName
        if ($activeSessionCount -eq 0) {
            break
        }
        if ($activeSessionCount -ne 1) {
            throw "Perfetto service state exposes an ambiguous physical-present session count."
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    if ($activeSessionCount -ne 0) {
        throw "Perfetto did not finalize within 30 seconds after the stop trigger."
    }

    $remoteBytesText =
        (Invoke-TargetAdb -AdbArguments @("shell", "stat", "-c", "%s", $State.RemoteTrace) |
            Select-Object -First 1).Trim()
    $remoteBytes = 0L
    if (-not [long]::TryParse($remoteBytesText, [ref]$remoteBytes) -or $remoteBytes -le 0) {
        throw "Perfetto finalized without a non-empty physical-present trace."
    }
    Invoke-TargetAdb -AdbArguments @("pull", $State.RemoteTrace, $State.LocalTrace) | Out-Null
    $localBytes = (Get-Item -LiteralPath $State.LocalTrace).Length
    if ($localBytes -ne $remoteBytes) {
        throw "The pulled physical-present trace length does not match the finalized device file."
    }
    $State.Active = $false
    $toolWriter = [System.IO.StreamWriter]::new(
        $State.ToolPath,
        $true,
        [System.Text.UTF8Encoding]::new($false)
    )
    try {
        $toolWriter.WriteLine("trace_finalized_before_pull=true")
        $toolWriter.WriteLine("remote_trace_bytes=$remoteBytes")
        $toolWriter.WriteLine("local_trace_bytes=$localBytes")
    }
    finally {
        $toolWriter.Dispose()
    }
}

$deviceIdentity = $null
$originalStayAwake = $null
$environmentRows = [System.Collections.Generic.List[object]]::new()
$environmentPath = Join-Path $resolvedOutput "environment.csv"
$frameRows = [System.Collections.Generic.List[object]]::new()
$sampleSummaries = [System.Collections.Generic.List[object]]::new()

try {
    $deviceIdentity = Get-PhysicalDeviceIdentity
    $originalStayAwake =
        (Invoke-TargetAdb -AdbArguments @("shell", "settings", "get", "global", "stay_on_while_plugged_in") |
            Select-Object -First 1).Trim()
    Invoke-TargetAdb -AdbArguments @("shell", "svc", "power", "stayon", "usb") | Out-Null
    Invoke-TargetAdb -AdbArguments @("shell", "cmd", "input", "keyevent", "WAKEUP") | Out-Null
    Start-Sleep -Milliseconds 250
    $environmentRows.Add((Get-PhysicalCheckpoint -Name "before_warmups"))
    Invoke-TargetAdb -AdbArguments @("install", "-r", "-d", $resolvedApk) | Out-Null
    Invoke-TargetAdb -AdbArguments @("shell", "pm", "clear", $packageName) | Out-Null
    Invoke-TargetAdb -AdbArguments @("shell", "cmd", "package", "compile", "--reset", $packageName) | Out-Null
    $profileInstallResult = "not-requested"
    if ($CompilationMode -eq "speed-profile") {
        $receiver = "$packageName/androidx.profileinstaller.ProfileInstallReceiver"
        $installOutput =
            Invoke-TargetAdb -AdbArguments @(
                "shell",
                "am",
                "broadcast",
                "-a",
                "androidx.profileinstaller.action.INSTALL_PROFILE",
                $receiver
            )
        $installText = $installOutput -join "`n"
        if ($installText -notmatch "result=$profileInstallSuccessResult(?:\D|$)") {
            throw "Packaged Baseline Profile installation did not report result $profileInstallSuccessResult."
        }
        $profileInstallResult = "success"
        Invoke-TargetAdb -AdbArguments @("shell", "am", "force-stop", $packageName) | Out-Null
    }
    $compileResult =
        Invoke-TargetAdb -AdbArguments @("shell", "cmd", "package", "compile", "-m", $CompilationMode, "-f", $packageName)
    if (($compileResult -join "`n") -notmatch "Success") {
        throw "$CompilationMode compilation did not report success."
    }

    Invoke-TargetAdb -AdbArguments @("shell", "am", "force-stop", $packageName) | Out-Null
    Invoke-TargetAdb -AdbArguments @("shell", "am", "start", "-W", "-n", $activityName) | Out-Null
    Start-Sleep -Milliseconds 1500

    $beforeRemote = "$remotePrefix-before.xml"
    $beforeLocal = Join-Path $resolvedOutput "ui-before.xml"
    Invoke-TargetAdb -AdbArguments @("shell", "uiautomator", "dump", $beforeRemote) | Out-Null
    Invoke-TargetAdb -AdbArguments @("pull", $beforeRemote, $beforeLocal) | Out-Null
    [xml]$beforeUi = Get-Content -Raw -LiteralPath $beforeLocal
    $canvasNode = $beforeUi.SelectSingleNode("//node[@content-desc='16 by 16 pixel canvas']")
    $dirtyNode = $beforeUi.SelectSingleNode("//node[@content-desc='Document dirty status']")
    $undoTextNode = $beforeUi.SelectSingleNode("//node[@text='Undo']")
    $redoTextNode = $beforeUi.SelectSingleNode("//node[@text='Redo']")
    $pencilNode = $beforeUi.SelectSingleNode("//node[@content-desc='Pencil tool']")
    if ($null -eq $canvasNode -or $null -eq $dirtyNode -or $null -eq $undoTextNode -or $null -eq $redoTextNode -or $null -eq $pencilNode) {
        throw "Required editor nodes are absent before measurement."
    }
    if ($dirtyNode.GetAttribute("text") -ne "No unsaved changes") {
        throw "The frame journey did not start at the clean checkpoint."
    }
    if ($undoTextNode.ParentNode.GetAttribute("enabled") -ne "false" -or $redoTextNode.ParentNode.GetAttribute("enabled") -ne "false") {
        throw "Undo and Redo must be disabled before the frame journey."
    }
    if ($pencilNode.ParentNode.GetAttribute("checked") -ne "true") {
        throw "Pencil must be selected before the frame journey."
    }

    $canvasBounds = Get-Bounds -Node $canvasNode
    $undoBounds = Get-Bounds -Node $undoTextNode.ParentNode
    $cellWidth = ($canvasBounds.Right - $canvasBounds.Left) / 16.0
    $cellHeight = ($canvasBounds.Bottom - $canvasBounds.Top) / 16.0
    $canvasX = [int][Math]::Floor($canvasBounds.Left + $cellWidth / 2.0)
    $canvasY = [int][Math]::Floor($canvasBounds.Top + $cellHeight / 2.0)
    $undoX = [int][Math]::Floor(($undoBounds.Left + $undoBounds.Right) / 2.0)
    $undoY = [int][Math]::Floor(($undoBounds.Top + $undoBounds.Bottom) / 2.0)

    foreach ($warmupIndex in 1..$warmupCount) {
        Invoke-TargetAdb -AdbArguments @("shell", "cmd", "input", "tap", "$canvasX", "$canvasY") | Out-Null
        Start-Sleep -Milliseconds $undoWaitMilliseconds
        Invoke-UndoToCleanCheckpoint -UndoX $undoX -UndoY $undoY
    }

    $environmentRows.Add((Get-PhysicalCheckpoint -Name "before_samples"))
    Invoke-TargetAdb -AdbArguments @("logcat", "-c") | Out-Null
    if ($physicalPresentEnabled) {
        $physicalTraceState = Start-PhysicalPresentTrace
    }
    foreach ($sampleIndex in 1..$sampleCount) {
        $previewCapture =
            Get-OperationPhaseCapture `
                -SampleIndex $sampleIndex `
                -Phase "preview" `
                -MotionEvent "DOWN" `
                -WaitMilliseconds $previewWaitMilliseconds `
                -CanvasX $canvasX `
                -CanvasY $canvasY
        $commitCapture =
            Get-OperationPhaseCapture `
                -SampleIndex $sampleIndex `
                -Phase "commit" `
                -MotionEvent "UP" `
                -WaitMilliseconds $drawWaitMilliseconds `
                -CanvasX $canvasX `
                -CanvasY $canvasY
        Assert-CommittedResult -SampleIndex $sampleIndex
        @($previewCapture.Rows) + @($commitCapture.Rows) | ForEach-Object { $frameRows.Add($_) }
        $operationTiming =
            Get-OperationTiming -PreviewRows @($previewCapture.Rows) -CommitRows @($commitCapture.Rows)
        $sampleSummaries.Add(
            [pscustomobject]@{
                variant = $Variant
                source_commit = $SourceCommit
                sample_index = $sampleIndex
                preview_frame_count = $previewCapture.Rows.Count
                commit_frame_count = $commitCapture.Rows.Count
                total_frames_rendered =
                    $previewCapture.TotalFramesRendered + $commitCapture.TotalFramesRendered
                janky_frames = $previewCapture.JankyFrames + $commitCapture.JankyFrames
                deadline_missed_frames =
                    $previewCapture.DeadlineMissedFrames + $commitCapture.DeadlineMissedFrames
                raw_row_count = $previewCapture.Rows.Count + $commitCapture.Rows.Count
                valid_row_count = $previewCapture.Rows.Count + $commitCapture.Rows.Count
                preview_input_start_nanos = $operationTiming.preview_input_start_nanos
                commit_input_start_nanos = $operationTiming.commit_input_start_nanos
                committed_result_completion_nanos = $operationTiming.committed_result_completion_nanos
                input_to_committed_result_ms = $operationTiming.input_to_committed_result_ms
                down_to_committed_result_ms = $operationTiming.down_to_committed_result_ms
            }
        )

        if ($sampleIndex % 10 -eq 0 -or $sampleIndex -eq $sampleCount) {
            $environmentRows.Add((Get-PhysicalCheckpoint -Name "after_$sampleIndex"))
        }

        if ($sampleIndex -lt $sampleCount) {
            Invoke-UndoToCleanCheckpoint -UndoX $undoX -UndoY $undoY
        }
    }

    $afterRemote = "$remotePrefix-after.xml"
    $screenRemote = "$remotePrefix-after.png"
    $afterLocal = Join-Path $resolvedOutput "ui-after.xml"
    $screenLocal = Join-Path $resolvedOutput "frame-after.png"
    Invoke-TargetAdb -AdbArguments @("shell", "uiautomator", "dump", $afterRemote) | Out-Null
    Invoke-TargetAdb -AdbArguments @("shell", "screencap", "-p", $screenRemote) | Out-Null
    Invoke-TargetAdb -AdbArguments @("pull", $afterRemote, $afterLocal) | Out-Null
    Invoke-TargetAdb -AdbArguments @("pull", $screenRemote, $screenLocal) | Out-Null
    [xml]$afterUi = Get-Content -Raw -LiteralPath $afterLocal
    $afterDirty = $afterUi.SelectSingleNode("//node[@content-desc='Document dirty status']")
    $afterUndo = $afterUi.SelectSingleNode("//node[@text='Undo']")
    $afterRedo = $afterUi.SelectSingleNode("//node[@text='Redo']")
    $afterCanvas = $afterUi.SelectSingleNode("//node[@content-desc='16 by 16 pixel canvas']")
    if ($null -eq $afterDirty -or $null -eq $afterUndo -or $null -eq $afterRedo -or $null -eq $afterCanvas) {
        throw "Required editor nodes are absent after measurement."
    }
    if (
        $afterDirty.GetAttribute("text") -ne "Unsaved changes" -or
        $afterUndo.ParentNode.GetAttribute("enabled") -ne "true" -or
        $afterRedo.ParentNode.GetAttribute("enabled") -ne "false" -or
        $afterCanvas.GetAttribute("bounds") -ne $canvasNode.GetAttribute("bounds")
    ) {
        throw "The final editor UI does not match the applied Pencil result."
    }

    $environmentRows.Add((Get-PhysicalCheckpoint -Name "after_samples"))

    $framesPath = Join-Path $resolvedOutput "frames.csv"
    $summariesPath = Join-Path $resolvedOutput "samples.csv"
    $frameRows | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath $framesPath
    $sampleSummaries | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath $summariesPath
    if ($physicalPresentEnabled) {
        Stop-PhysicalPresentTrace -State $physicalTraceState
        $physicalAnalysis =
            & $physicalPresentAnalyzer `
                -TracePath $physicalTraceState.LocalTrace `
                -FramesPath $framesPath `
                -TraceProcessorPath $resolvedPhysicalTraceProcessor `
                -OutputDirectory $resolvedOutput `
                -ExpectedSampleCount $sampleCount
    }

    $logcat = @(Invoke-TargetAdb -AdbArguments @("logcat", "-d", "-v", "threadtime"))
    $logcatPath = Join-Path $resolvedOutput "logcat.txt"
    [System.IO.File]::WriteAllLines($logcatPath, $logcat)
    $fatalCount = @($logcat | Select-String -Pattern "FATAL EXCEPTION|ANR in|Fatal signal|Process .* has died").Count

    $validFrames = @($frameRows | Where-Object { $_.flags -eq 0 })
    $totalRendered = ($sampleSummaries.total_frames_rendered | Measure-Object -Sum).Sum
    $totalJanky = ($sampleSummaries.janky_frames | Measure-Object -Sum).Sum
    $totalDeadlineMissed = ($sampleSummaries.deadline_missed_frames | Measure-Object -Sum).Sum
    if (
        $sampleSummaries.Count -ne $sampleCount -or
        $frameRows.Count -lt ($sampleCount * 2) -or
        $totalRendered -ne $frameRows.Count -or
        $validFrames.Count -ne $frameRows.Count -or
        @($sampleSummaries | Where-Object { $_.preview_frame_count -lt 1 -or $_.commit_frame_count -lt 1 }).Count -gt 0
    ) {
        throw "Aggregate frame counts must retain every preview and committed-result frame."
    }
    $cpuP95 = Get-NearestRank -Values @($validFrames.frame_duration_cpu_ms) -Percentile 0.95
    $totalP95 = Get-NearestRank -Values @($validFrames.app_frame_total_ms) -Percentile 0.95
    $overrunP95 = Get-NearestRank -Values @($validFrames.frame_overrun_ms) -Percentile 0.95
    $overrunP99 = Get-NearestRank -Values @($validFrames.frame_overrun_ms) -Percentile 0.99
    $inputP95 = Get-NearestRank -Values @($sampleSummaries.input_to_committed_result_ms) -Percentile 0.95
    $journeyP95 = Get-NearestRank -Values @($sampleSummaries.down_to_committed_result_ms) -Percentile 0.95
    $passed =
        $overrunP95 -le 0.0 -and
        $overrunP99 -le 16.67 -and
        $inputP95 -le 33.33 -and
        $fatalCount -eq 0

    $isDecisionLane = $Variant -eq "release-like" -and $CompilationMode -eq "speed-profile" -and $RunKind -eq "decision"
    $maximumFrameOverrun = ($validFrames.frame_overrun_ms | Measure-Object -Maximum).Maximum
    $maximumInputToCommitted = ($sampleSummaries.input_to_committed_result_ms | Measure-Object -Maximum).Maximum
    $grossRegression = $maximumFrameOverrun -gt 33.34 -or $maximumInputToCommitted -gt 100.0
    $acceptanceLane =
        if ($physicalPresentEnabled) { "attribution" } elseif ($isDecisionLane) { "decision" } else { "diagnostic" }
    $status =
        if ($physicalPresentEnabled) {
            $physicalAnalysis.Status
        }
        elseif ($isDecisionLane) {
            if ($passed) { "pass" } else { "fail" }
        }
        elseif ($grossRegression) {
            "gross-regression"
        }
        else {
            "inconclusive"
        }
    $metadata = [System.Collections.Generic.List[string]]::new()
    @(
        "schema=$frameSchema",
        "experiment_schema=$experimentSchema",
        "experiment_id=$ExperimentId",
        "status=$status",
        "acceptance_lane=$acceptanceLane",
        "threshold_status=$(if ($isDecisionLane) { if ($passed) { 'pass' } else { 'fail' } } elseif ($grossRegression) { 'gross-regression' } else { 'inconclusive' })",
        "variant=$Variant",
        "run_kind=$RunKind",
        "candidate_role=$CandidateRole",
        "comparison_sequence_index=$ComparisonSequenceIndex",
        "comparison_order=$($comparisonOrder -join '|')",
        "input_injection=$inputInjection",
        "source_commit=$SourceCommit",
        "physical_profile_id=$physicalProfileId",
        "device_evidence_class=physical_device",
        "device_manufacturer=$($deviceIdentity.manufacturer)",
        "device_model=$($deviceIdentity.model)",
        "device_product=$($deviceIdentity.product)",
        "device_name=$($deviceIdentity.device)",
        "device_api_level=$($deviceIdentity.api_level)",
        "device_build_fingerprint=$($deviceIdentity.build_fingerprint)",
        "device_security_patch=$($deviceIdentity.security_patch)",
        "apk_embedded_source_commit=$(if ($null -eq $embeddedSourceCommit) { 'unavailable' } else { $embeddedSourceCommit })",
        "apk_bytes=$((Get-Item -LiteralPath $resolvedApk).Length)",
        "apk_sha256=$apkHash",
        "compile_mode=$CompilationMode",
        "packaged_profile_install=$profileInstallResult",
        "warmup_cycles=$warmupCount",
        "sample_count=$sampleCount",
        "percentile_method=nearest-rank; diagnostic-p95-rank=10; decision-p95-rank=48",
        "environment_checkpoint_count=$($environmentRows.Count)",
        "raw_frame_rows=$($frameRows.Count)",
        "valid_frame_rows=$($validFrames.Count)",
        "aggregate_frames_rendered=$totalRendered",
        "aggregate_janky_frames=$totalJanky",
        "aggregate_deadline_missed_frames=$totalDeadlineMissed",
        "valid_cpu_frame_p95_ms=$('{0:F6}' -f $cpuP95)",
        "valid_app_frame_total_p95_ms=$('{0:F6}' -f $totalP95)",
        "valid_frame_overrun_p95_ms=$('{0:F6}' -f $overrunP95)",
        "valid_frame_overrun_p99_ms=$('{0:F6}' -f $overrunP99)",
        "valid_input_to_committed_result_p95_ms=$('{0:F6}' -f $inputP95)",
        "diagnostic_down_to_committed_result_p95_ms=$('{0:F6}' -f $journeyP95)",
        "maximum_frame_overrun_ms=$('{0:F6}' -f $maximumFrameOverrun)",
        "maximum_input_to_committed_result_ms=$('{0:F6}' -f $maximumInputToCommitted)",
        "diagnostic_gross_frame_overrun_boundary_ms=33.34-exclusive",
        "diagnostic_gross_input_to_committed_boundary_ms=100.0-exclusive",
        "fatal_anr_matches=$fatalCount",
        "canvas_bounds=$($canvasNode.GetAttribute('bounds'))",
        "display_present_time_available=$(@($validFrames | Where-Object { $_.display_present_time_nanos -gt 0 }).Count -gt 0)",
        "boundary=DOWN preview plus UP commit; every phase frame retained; acceptance latency starts at earliest UP HandleInputStart and completes at latest UP-associated FrameCompleted after committed UI verification; DOWN-to-commit including the intentional preview dwell is diagnostic only"
    ) | ForEach-Object { $metadata.Add($_) }
    if ($physicalPresentEnabled) {
        $metadata.Add("base_frame_row_schema=nene-pixel-m2-actual-app-frame-v5-fields")
        $metadata.Add("physical_present_schema=$($physicalAnalysis.Schema)")
        $metadata.Add("physical_present_status=$($physicalAnalysis.Status)")
        $metadata.Add("physical_present_trace_bytes=$($physicalAnalysis.TraceBytes)")
        $metadata.Add("physical_present_trace_sha256=$($physicalAnalysis.TraceSha256)")
        $metadata.Add("physical_present_correlated_frame_count=$($physicalAnalysis.CorrelatedFrameCount)")
        $metadata.Add("physical_present_minimum_frames_per_sample=$($physicalAnalysis.MinimumFramesPerSample)")
        $metadata.Add("physical_present_maximum_frames_per_sample=$($physicalAnalysis.MaximumFramesPerSample)")
        $metadata.Add("physical_input_to_present_p50_ms=$('{0:F6}' -f $physicalAnalysis.PhysicalP50Milliseconds)")
        $metadata.Add("physical_input_to_present_p95_ms=$('{0:F6}' -f $physicalAnalysis.PhysicalP95Milliseconds)")
        $metadata.Add("physical_input_to_present_p99_ms=$('{0:F6}' -f $physicalAnalysis.PhysicalP99Milliseconds)")
        $metadata.Add("physical_present_attribution_counts=$($physicalAnalysis.ClassCounts)")
        $metadata.Add("limitation=strict physical-present correlation retained for this ten-sample attribution population; does not replace the fifty-sample decision lane")
    }
    else {
        $metadata.Add("limitation=app-issued gfxinfo FrameTimeline only; no strict SurfaceFlinger physical-present correlation; diagnostic results are never acceptance PASS")
    }
    [System.IO.File]::WriteAllLines((Join-Path $resolvedOutput "metadata.txt"), $metadata)
    Write-RunState -Status "completed" -Verdict $status

    Invoke-TargetAdb -AdbArguments @("shell", "cmd", "input", "tap", "$undoX", "$undoY") | Out-Null
    if ($isDecisionLane -and -not $passed) {
        throw "The $Variant frame batch failed its fixed acceptance."
    }
    if ($RunKind -eq "diagnostic" -and $grossRegression) {
        throw "The diagnostic exceeded a predeclared gross-regression boundary."
    }
    $metadata
}
catch {
    $currentState = Get-RunState -Directory $resolvedOutput
    if ($null -eq $currentState -or $currentState.status -ne "completed") {
        $invalidStatus = if ($script:measuredDownCount -eq 0) { "invalid-before-samples" } else { "invalid-after-samples" }
        Write-RunState -Status $invalidStatus -Verdict "invalid"
    }
    throw
}
finally {
    if ($null -ne $physicalTraceState -and $physicalTraceState.Active) {
        try {
            Stop-PhysicalPresentTrace -State $physicalTraceState
        }
        catch {
            Write-Warning "Unable to finalize the active physical-present trace through its declared trigger-only stop path."
        }
    }
    if ($environmentRows.Count -gt 0) {
        $environmentRows | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath $environmentPath
    }
    if ($null -ne $originalStayAwake) {
        Invoke-TargetAdb -AdbArguments @("shell", "settings", "put", "global", "stay_on_while_plugged_in", $originalStayAwake) | Out-Null
    }
    Invoke-TargetAdb -AdbArguments @(
        "shell",
        "rm",
        "-f",
        "$remotePrefix-before.xml",
        "$remotePrefix-after.xml",
        "$remotePrefix-after.png",
        "$remotePrefix-checkpoint.xml"
    ) | Out-Null
    if ($null -ne $physicalTraceState -and -not $physicalTraceState.Active) {
        Invoke-TargetAdb -AdbArguments @(
            "shell",
            "rm",
            "-f",
            $physicalTraceState.RemoteConfig,
            $physicalTraceState.RemoteTrace
        ) | Out-Null
    }
}

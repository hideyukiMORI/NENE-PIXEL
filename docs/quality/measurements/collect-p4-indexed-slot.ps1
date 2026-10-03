[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ManifestPath,
    [Parameter(Mandatory = $true)][string]$SlotId,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'p4-indexed-preflight.ps1')
. (Join-Path $PSScriptRoot 'p4-indexed-device-state.ps1')
. (Join-Path $PSScriptRoot 'p4-indexed-device-lanes.ps1')
. (Join-Path $PSScriptRoot 'p4-layer-slot-routing.ps1')

function Invoke-P4SlotCollector {
param([string]$ManifestPath, [string]$SlotId, [string]$OutputDirectory)
$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json -AsHashtable
Assert-P4ManifestContract $manifest
# $matches is an automatic variable that regex operators overwrite; the slot lookup keeps its own name.
# Frame slots resolve from Issue #120's own four-slot catalog, outside Issue #106's order.
$slot = Get-P4ExecutionSlot $manifest.protocol.id $SlotId
$phase = $manifest.protocol.id -ceq 'nene-pixel-p4-layer-phase-verification-v1'
$manifestHash = Get-FileSha256 $ManifestPath
$expectedOutput = [IO.Path]::GetFullPath((Join-Path $manifest.output_directory $SlotId))
if ([IO.Path]::GetFullPath($OutputDirectory) -cne $expectedOutput -or
    -not (Test-Path -LiteralPath (Join-Path $expectedOutput 'started.json'))) { throw 'Collector requires its reserved outer slot.' }
$artifactRole = Resolve-P4ArtifactRole $manifest.protocol.id $SlotId
$source = $manifest.roles[$artifactRole]
if ($phase) {
    $startedPath = Join-Path $expectedOutput 'started.json'
    Assert-P4SealPathNotLinked $startedPath
    $started = Get-Content -Raw -LiteralPath $startedPath | ConvertFrom-Json -AsHashtable
    $plan = Get-P4DeviceLanePlan $manifest $slot $manifestHash
    if ($started.slot_id -cne $SlotId -or $started.status -cne 'started' -or $started.attempt -ne 1 -or
        $started.preflight_sha256 -cne $manifestHash -or $started.protocol_timeout_seconds -ne $slot.timeout_seconds -or
        $started.collector_timeout_seconds -ne $plan.collector_timeout_seconds) { throw 'Phase reservation identity or budget differs.' }
    Assert-P4FrameExactProjection $started.collector_budget $plan.collector_budget 'phase reserved collector budget'
}
$env:JAVA_HOME = [IO.Path]::GetFullPath($manifest.toolchain.jdk)
$env:ANDROID_HOME = [IO.Path]::GetFullPath($manifest.toolchain.android_sdk)

switch ($slot.lane) {
    'host' {
        $task = switch ($slot.runner) {
            'project' { ':core:project-format:issue106ProjectFormatHostEvidence' }
            'recovery' { ':adapters:persistence:issue106RecoveryRecordHostEvidence' }
            'legacy' { ':core:application:issue106LegacyImportHostEvidence' }
        }
        $arguments = @('-I', $manifest.tools.host_init.path, "-PneneP4EvidenceRole=$($slot.role)", '-PneneP4ClasspathOnly=false',
            "-PneneP4EvidenceOutputDirectory=$expectedOutput", $task, '--no-daemon')
        # QLT-012 scoped exception: this single-use daemon and JavaExec worker must inherit the
        # outer kill-on-close Job. Cached task/classes are retained; ordinary builds keep defaults.
        Push-Location $source.worktree
        try {
            & (Join-Path $source.worktree 'gradlew.bat') @arguments
            if ($LASTEXITCODE -ne 0) { throw "Host evidence task failed with exit $LASTEXITCODE." }
        } finally { Pop-Location }
    }
    { $_ -cin @('command', 'memory', 'publication', 'saf-save') } {
        $plan = Get-P4DeviceLanePlan -Manifest $manifest -Slot $slot -ManifestSha256 $manifestHash
        $context = [ordered]@{
            repository_root = [IO.Path]::GetFullPath($source.worktree)
            output_directory = $expectedOutput
            adb_path = [IO.Path]::GetFullPath($manifest.tools.adb.path)
            serial = [string]$manifest.device.serial
        }
        Invoke-P4InstrumentationLane -Context $context -Manifest $manifest -Plan $plan | Out-Null
    }
    'frame' {
        $plan = Get-P4DeviceLanePlan -Manifest $manifest -Slot $slot -ManifestSha256 $manifestHash
        $context = [ordered]@{
            repository_root = [IO.Path]::GetFullPath($source.worktree)
            output_directory = $expectedOutput
            adb_path = [IO.Path]::GetFullPath($manifest.tools.adb.path)
            serial = [string]$manifest.device.serial
        }
        if ($phase) {
            Invoke-P4LayerFrameCollector -Context $context -Manifest $manifest -Slot $slot -Plan $plan -ManifestSha256 $manifestHash
            break
        }
        # Precondition, before any frame work: the v2 candidate leaves a recovery record the v1
        # baseline cannot read, which disables New/Open and would make every later baseline slot
        # INVALID. This moves (never deletes) only the live record aside, and needs the debuggable
        # build, so it must run before measure-m2-frame.ps1 installs the release-like APK.
        Invoke-P4RecoveryQuarantine -Context $context -Manifest $manifest -Role $slot.role `
            -SlotId ([string]$slot.id) | Out-Null
        $collector = [IO.Path]::GetFullPath($manifest.tools.frame_collector.path)
        $parameters = $plan.frame_parameters
        # The outer wrapper already bounds this slot with its kill-on-close Job; the frame collector
        # owns its own device restoration and must not be wrapped in a second Job here.
        Push-Location $source.worktree
        try { & $collector @parameters | Out-Null } finally { Pop-Location }
        New-P4FrameSlotRecord -Context $context -FrameSlotDirectory $plan.frame_slot_directory | Out-Null
    }
    default { throw 'This device collection lane is not yet implemented; no sample may start.' }
}
}

if ($MyInvocation.InvocationName -ne '.') { Invoke-P4SlotCollector $ManifestPath $SlotId $OutputDirectory }

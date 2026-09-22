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

$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json -AsHashtable
Assert-P4ManifestContract $manifest
# $matches is an automatic variable that regex operators overwrite; the slot lookup keeps its own name.
$slotMatches = @(Get-P4SlotCatalog | Where-Object { $_.id -ceq $SlotId })
if ($slotMatches.Count -ne 1) { throw 'Unknown collector slot.' }
$slot = $slotMatches[0]
$expectedOutput = [IO.Path]::GetFullPath((Join-Path $manifest.output_directory $SlotId))
if ([IO.Path]::GetFullPath($OutputDirectory) -cne $expectedOutput -or
    -not (Test-Path -LiteralPath (Join-Path $expectedOutput 'started.json'))) { throw 'Collector requires its reserved outer slot.' }
$source = $manifest.roles[$slot.role]
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
    { $_ -cin @('command', 'memory', 'publication') } {
        $plan = Get-P4DeviceLanePlan -Manifest $manifest -Slot $slot
        $context = [ordered]@{
            repository_root = [IO.Path]::GetFullPath($source.worktree)
            output_directory = $expectedOutput
            adb_path = [IO.Path]::GetFullPath($manifest.tools.adb.path)
            serial = [string]$manifest.device.serial
        }
        Invoke-P4InstrumentationLane -Context $context -Manifest $manifest -Plan $plan | Out-Null
    }
    'frame' {
        $plan = Get-P4DeviceLanePlan -Manifest $manifest -Slot $slot
        $context = [ordered]@{
            repository_root = [IO.Path]::GetFullPath($source.worktree)
            output_directory = $expectedOutput
            adb_path = [IO.Path]::GetFullPath($manifest.tools.adb.path)
            serial = [string]$manifest.device.serial
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

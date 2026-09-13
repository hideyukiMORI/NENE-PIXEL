[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ManifestPath,
    [Parameter(Mandatory = $true)][string]$SlotId,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'p4-indexed-preflight.ps1')

$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json -AsHashtable
Assert-P4ManifestContract $manifest
$matches = @(Get-P4SlotCatalog | Where-Object { $_.id -ceq $SlotId })
if ($matches.Count -ne 1) { throw 'Unknown collector slot.' }
$slot = $matches[0]
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
        $arguments = @('-I', $manifest.tools.host_init.path, "-PneneP4EvidenceRole=$($slot.role)",
            "-PneneP4EvidenceOutputDirectory=$expectedOutput", $task, '--no-daemon')
        # QLT-012 scoped exception: this single-use daemon and JavaExec worker must inherit the
        # outer kill-on-close Job. Cached task/classes are retained; ordinary builds keep defaults.
        Push-Location $source.worktree
        try {
            & (Join-Path $source.worktree 'gradlew.bat') @arguments
            if ($LASTEXITCODE -ne 0) { throw "Host evidence task failed with exit $LASTEXITCODE." }
        } finally { Pop-Location }
    }
    default { throw 'This device collection lane is not yet implemented; no sample may start.' }
}

<#
tools/selftest/compare-screenshots.selftest.ps1 - self-test for tools/compare-screenshots.ps1.
Generates 10x10 PNGs in a temporary directory and runs four cases: identical, difference inside
-Inside, difference outside -Inside, size mismatch. Checks exit code and changed/bounds/inside.

Output: FAIL lines on stderr, then {"cases":4,"passed":N} on stdout.
Exit codes: 0 all cases passed, 1 any case failed.
Example: pwsh -NoProfile -File tools/selftest/compare-screenshots.selftest.ps1
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$script = Join-Path (Split-Path -Parent $PSScriptRoot) 'compare-screenshots.ps1'
$work = Join-Path ([System.IO.Path]::GetTempPath()) ("compare-screenshots-selftest-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null

function New-Png {
    param([string]$Name, [int]$Size, [int[]]$Changed)
    $bitmap = [System.Drawing.Bitmap]::new($Size, $Size)
    try {
        for ($y = 0; $y -lt $Size; $y++) {
            for ($x = 0; $x -lt $Size; $x++) { $bitmap.SetPixel($x, $y, [System.Drawing.Color]::White) }
        }
        if ($null -ne $Changed) { $bitmap.SetPixel($Changed[0], $Changed[1], [System.Drawing.Color]::Black) }
        $path = Join-Path $work $Name
        $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
        return $path
    }
    finally { $bitmap.Dispose() }
}

$cases = @(
    @{ name = 'identical'; after = @('same.png', 10, $null); exit = 0; changed = $false; bounds = $null; inside = $null },
    @{ name = 'inside'; after = @('in.png', 10, @(3, 3)); exit = 0; changed = $true; bounds = '3,3,3,3'; inside = $true },
    @{ name = 'outside'; after = @('out.png', 10, @(8, 8)); exit = 1; changed = $true; bounds = '8,8,8,8'; inside = $false },
    @{ name = 'size_mismatch'; after = @('small.png', 8, $null); exit = 2; changed = $null; bounds = $null; inside = $null }
)

$passed = 0
try {
    $base = New-Png -Name 'base.png' -Size 10 -Changed $null
    foreach ($case in $cases) {
        $after = New-Png -Name $case.after[0] -Size $case.after[1] -Changed $case.after[2]
        $stderrFile = Join-Path $work ($case.name + '.stderr')
        $stdout = & pwsh -NoProfile -File $script -Before $base -After $after -Inside '0,0,5,5' 2> $stderrFile
        $code = $LASTEXITCODE
        $problems = [System.Collections.Generic.List[string]]::new()
        if ($code -ne $case.exit) { $problems.Add("exit $code, expected $($case.exit)") }
        if ($case.name -eq 'size_mismatch') {
            if ((Get-Item $stderrFile).Length -eq 0) { $problems.Add('no stderr reason') }
        }
        else {
            $json = ($stdout -join "`n") | ConvertFrom-Json
            if ($json.changed -ne $case.changed) { $problems.Add("changed=$($json.changed)") }
            $bounds = if ($null -eq $json.bounds) { $null } else { $json.bounds -join ',' }
            if ($bounds -ne $case.bounds) { $problems.Add("bounds=$bounds") }
            if ($json.inside -ne $case.inside) { $problems.Add("inside=$($json.inside)") }
        }
        if ($problems.Count -eq 0) { $passed++ }
        else { [Console]::Error.WriteLine("FAIL $($case.name): " + ($problems -join '; ')) }
    }
}
finally { Remove-Item -LiteralPath $work -Recurse -Force }

[ordered]@{ cases = $cases.Count; passed = $passed } | ConvertTo-Json -Compress | Write-Output
if ($passed -ne $cases.Count) { exit 1 }
exit 0

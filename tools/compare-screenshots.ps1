<#
tools/compare-screenshots.ps1 - bounding rectangle of the pixels that differ between two PNGs.
Reads the two files only; writes nothing but stdout/stderr.

Arguments
  -Before <path>      PNG captured before the change. Required.
  -After <path>       PNG captured after the change. Required.
  -Inside "l,t,r,b"   Optional. ONE comma-separated argument; edges inclusive, 0 <= l <= r < width,
                      0 <= t <= b < height. Any other form is an argument error.
  -Tolerance <0-255>  Optional per-channel absolute tolerance. Default 0 (exact bytes).

Output (one JSON object on stdout)
  {"changed":<bool>,"bounds":[l,t,r,b]|null,"inside":<bool>|null,"size":[w,h]}
  "inside" is null when -Inside is omitted or nothing differs. Errors go to stderr; a size
  mismatch also prints {"error":"size_mismatch","before_size":[w,h],"after_size":[w,h]}.

Exit codes
  0  no difference; a difference without -Inside; a difference fully inside -Inside
  1  a difference not fully inside -Inside
  2  error: size mismatch, missing/unreadable file, malformed -Inside or -Tolerance

Example
  pwsh -NoProfile -File tools/compare-screenshots.ps1 -Before a.png -After b.png -Inside "0,0,5,5"
#>
[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$Before,
    [string]$After,
    [string]$Inside,
    [string]$Tolerance = '0',
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$Unexpected
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Stop-WithError {
    param([string]$Message)
    [Console]::Error.WriteLine("compare-screenshots: $Message")
    exit 2
}

trap { Stop-WithError $_.Exception.Message }

Add-Type -AssemblyName System.Drawing
function Get-ArgbBuffer {
    param([string]$Path)
    $resolved = (Resolve-Path -LiteralPath $Path).Path
    $bitmap = [System.Drawing.Bitmap]::new($resolved)
    try {
        $rectangle = [System.Drawing.Rectangle]::new(0, 0, $bitmap.Width, $bitmap.Height)
        $locked = $bitmap.LockBits($rectangle, [System.Drawing.Imaging.ImageLockMode]::ReadOnly,
            [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            if ($locked.Stride -le 0) { throw "Unsupported bottom-up bitmap stride for $Path." }
            $buffer = [byte[]]::new($locked.Stride * $bitmap.Height)
            [System.Runtime.InteropServices.Marshal]::Copy($locked.Scan0, $buffer, 0, $buffer.Length)
        }
        finally { $bitmap.UnlockBits($locked) }
        return @{ width = $bitmap.Width; height = $bitmap.Height; stride = $locked.Stride; bytes = $buffer }
    }
    finally { $bitmap.Dispose() }
}

function ConvertTo-StrictInt {
    param([string]$Text, [string]$Name)
    $value = 0
    if (-not [int]::TryParse($Text.Trim(), [System.Globalization.NumberStyles]::AllowLeadingSign,
            [cultureinfo]::InvariantCulture, [ref]$value)) {
        Stop-WithError "$Name value '$Text' is not an integer."
    }
    return $value
}

if ($null -ne $Unexpected -and $Unexpected.Count -gt 0) {
    Stop-WithError ("unexpected arguments '{0}'; use named parameters; -Inside is one argument 'l,t,r,b'." -f ($Unexpected -join ' '))
}
if ([string]::IsNullOrWhiteSpace($Before)) { Stop-WithError '-Before <path> is required.' }
if ([string]::IsNullOrWhiteSpace($After)) { Stop-WithError '-After <path> is required.' }
$toleranceValue = ConvertTo-StrictInt -Text $Tolerance -Name '-Tolerance'
if ($toleranceValue -lt 0 -or $toleranceValue -gt 255) { Stop-WithError '-Tolerance must be 0-255.' }

$insideRectangle = $null
if ($PSBoundParameters.ContainsKey('Inside')) {
    $fields = @($Inside -split ',')
    if ($fields.Count -ne 4) { Stop-WithError "-Inside must be one argument 'l,t,r,b'; got '$Inside'." }
    $insideRectangle = @($fields | ForEach-Object { ConvertTo-StrictInt -Text $_ -Name '-Inside' })
    if ($insideRectangle[0] -gt $insideRectangle[2] -or $insideRectangle[1] -gt $insideRectangle[3]) {
        Stop-WithError '-Inside must satisfy l <= r and t <= b.'
    }
}

$left = Get-ArgbBuffer -Path $Before
$right = Get-ArgbBuffer -Path $After

if ($left.width -ne $right.width -or $left.height -ne $right.height) {
    [ordered]@{
        error        = 'size_mismatch'
        before_size  = @($left.width, $left.height)
        after_size   = @($right.width, $right.height)
    } | ConvertTo-Json -Compress -Depth 4 | Write-Output
    Stop-WithError ("size mismatch: before {0}x{1}, after {2}x{3}." -f
        $left.width, $left.height, $right.width, $right.height)
}

$width = $left.width
$height = $left.height
if ($null -ne $insideRectangle -and ($insideRectangle[0] -lt 0 -or $insideRectangle[1] -lt 0 -or
        $insideRectangle[2] -ge $width -or $insideRectangle[3] -ge $height)) {
    Stop-WithError ("-Inside {0} lies outside the {1}x{2} image." -f ($insideRectangle -join ','), $width, $height)
}
$rowBytes = $width * 4
$rowLeft = [byte[]]::new($rowBytes)
$rowRight = [byte[]]::new($rowBytes)

$minX = [int]::MaxValue
$minY = [int]::MaxValue
$maxX = -1
$maxY = -1

for ($y = 0; $y -lt $height; $y++) {
    $offset = $y * $left.stride
    [Array]::Copy($left.bytes, $offset, $rowLeft, 0, $rowBytes)
    [Array]::Copy($right.bytes, $offset, $rowRight, 0, $rowBytes)
    if ([System.Linq.Enumerable]::SequenceEqual[byte]($rowLeft, $rowRight)) { continue }
    for ($x = 0; $x -lt $width; $x++) {
        $base = $x * 4
        $differs = $false
        for ($channel = 0; $channel -lt 4; $channel++) {
            $delta = [Math]::Abs([int]$rowLeft[$base + $channel] - [int]$rowRight[$base + $channel])
            if ($delta -gt $toleranceValue) { $differs = $true; break }
        }
        if (-not $differs) { continue }
        if ($x -lt $minX) { $minX = $x }
        if ($x -gt $maxX) { $maxX = $x }
        if ($y -lt $minY) { $minY = $y }
        if ($y -gt $maxY) { $maxY = $y }
    }
}

$changed = $maxX -ge 0
$bounds = $null
$insideResult = $null
if ($changed) {
    $bounds = @($minX, $minY, $maxX, $maxY)
    if ($null -ne $insideRectangle) {
        $insideResult = ($minX -ge $insideRectangle[0] -and $minY -ge $insideRectangle[1] -and
            $maxX -le $insideRectangle[2] -and $maxY -le $insideRectangle[3])
    }
}

[ordered]@{
    changed = $changed
    bounds  = $bounds
    inside  = $insideResult
    size    = @($width, $height)
} | ConvertTo-Json -Compress -Depth 4 | Write-Output

if ($changed -and $null -ne $insideResult -and -not $insideResult) { exit 1 }
exit 0

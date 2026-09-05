[CmdletBinding()]
param(
    [string]$BaselineImage,
    [string]$CandidateImage,
    [string]$BaselineUi,
    [string]$CandidateUi,
    [string]$OutputJson,
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$schema = "nene-pixel-m2-static-controls-color-tolerance-v1"
$packageName = "io.github.hideyukimori.nenepixel"
$maximumChangedPixels = 1442
$maximumRgbChannelDelta = 1

Add-Type -AssemblyName System.Drawing

function Get-Bounds {
    param([System.Xml.XmlElement]$Node)

    $value = $Node.GetAttribute("bounds")
    if ($value -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
        throw "Invalid UI bounds: $value"
    }
    [System.Drawing.Rectangle]::FromLTRB(
        [int]$Matches[1],
        [int]$Matches[2],
        [int]$Matches[3],
        [int]$Matches[4]
    )
}

function Get-UniqueNode {
    param(
        [xml]$Ui,
        [string]$XPath,
        [string]$Meaning
    )

    $nodes = @($Ui.SelectNodes($XPath))
    if ($nodes.Count -ne 1) {
        throw "Expected exactly one $Meaning node, found $($nodes.Count)."
    }
    $nodes[0]
}

function Get-NodeSignature {
    param([System.Xml.XmlElement]$Node)

    @(
        $Node.GetAttribute("package"),
        $Node.GetAttribute("class"),
        $Node.GetAttribute("text"),
        $Node.GetAttribute("content-desc"),
        $Node.GetAttribute("bounds"),
        $Node.GetAttribute("enabled"),
        $Node.GetAttribute("clickable"),
        $Node.GetAttribute("checked")
    ) -join "|"
}

function Get-ComparedAppNodes {
    param([xml]$Ui)

    @(
        $Ui.SelectNodes("//node[@package='$packageName']") |
            Where-Object {
                -not [string]::IsNullOrEmpty($_.GetAttribute("text")) -or
                -not [string]::IsNullOrEmpty($_.GetAttribute("content-desc")) -or
                $_.GetAttribute("clickable") -eq "true" -or
                $_.GetAttribute("class") -eq "android.widget.Button"
            }
    )
}

function Get-AllowedControlNodes {
    param([xml]$Ui)

    $prefix = "//node[@package='$packageName']"
    $nodes = [System.Collections.Generic.List[System.Xml.XmlElement]]::new()
    $nodes.Add((Get-UniqueNode $Ui "$prefix[@text='Active color']" "Active color"))
    $nodes.Add((Get-UniqueNode $Ui "$prefix[@content-desc='Active color swatch']" "active color swatch"))
    $nodes.Add((Get-UniqueNode $Ui "$prefix[@text='Palette']" "Palette"))
    foreach ($index in 1..8) {
        $nodes.Add(
            (
                Get-UniqueNode `
                    $Ui `
                    "$prefix[starts-with(@content-desc, 'Palette color $index, ')]" `
                    "palette color $index"
            )
        )
    }
    $nodes.Add((Get-UniqueNode $Ui "$prefix[@content-desc='Pencil tool']" "Pencil tool"))
    $nodes.Add((Get-UniqueNode $Ui "$prefix[@content-desc='Eraser tool']" "Eraser tool"))
    $nodes
}

function Test-InAnyRectangle {
    param(
        [int]$X,
        [int]$Y,
        [System.Drawing.Rectangle[]]$Rectangles
    )

    foreach ($rectangle in $Rectangles) {
        if ($rectangle.Contains($X, $Y)) {
            return $true
        }
    }
    $false
}

function Test-VisualPair {
    param(
        [string]$ReferenceImage,
        [string]$ActualImage,
        [string]$ReferenceUi,
        [string]$ActualUi
    )

    [xml]$baselineUiDocument = Get-Content -Raw -LiteralPath $ReferenceUi
    [xml]$candidateUiDocument = Get-Content -Raw -LiteralPath $ActualUi
    $reasons = [System.Collections.Generic.List[string]]::new()

    $baselineAppNodes = @(Get-ComparedAppNodes $baselineUiDocument)
    $candidateAppNodes = @(Get-ComparedAppNodes $candidateUiDocument)
    $hierarchyExact = $baselineAppNodes.Count -eq $candidateAppNodes.Count
    if ($hierarchyExact) {
        foreach ($index in 0..($baselineAppNodes.Count - 1)) {
            if ((Get-NodeSignature $baselineAppNodes[$index]) -ne (Get-NodeSignature $candidateAppNodes[$index])) {
                $hierarchyExact = $false
                break
            }
        }
    }
    if (-not $hierarchyExact) {
        $reasons.Add("ui-hierarchy-mismatch")
    }

    $baselineControls = @(Get-AllowedControlNodes $baselineUiDocument)
    $candidateControls = @(Get-AllowedControlNodes $candidateUiDocument)
    $allowedBounds = @($baselineControls | ForEach-Object { Get-Bounds $_ })
    foreach ($index in 0..($baselineControls.Count - 1)) {
        if ((Get-NodeSignature $baselineControls[$index]) -ne (Get-NodeSignature $candidateControls[$index])) {
            if (-not $reasons.Contains("allowed-control-mismatch")) {
                $reasons.Add("allowed-control-mismatch")
            }
        }
    }

    $baselineTitle = Get-UniqueNode $baselineUiDocument "//node[@package='$packageName' and @text='NENE-PIXEL']" "title"
    $candidateTitle = Get-UniqueNode $candidateUiDocument "//node[@package='$packageName' and @text='NENE-PIXEL']" "title"
    $baselineCanvas =
        Get-UniqueNode `
            $baselineUiDocument `
            "//node[@package='$packageName' and @content-desc='16 by 16 pixel canvas']" `
            "canvas"
    $candidateCanvas =
        Get-UniqueNode `
            $candidateUiDocument `
            "//node[@package='$packageName' and @content-desc='16 by 16 pixel canvas']" `
            "canvas"
    $titleBounds = Get-Bounds $baselineTitle
    $candidateTitleBounds = Get-Bounds $candidateTitle
    $canvasBounds = Get-Bounds $baselineCanvas
    $candidateCanvasBounds = Get-Bounds $candidateCanvas
    if ($titleBounds -ne $candidateTitleBounds -or $canvasBounds -ne $candidateCanvasBounds) {
        $reasons.Add("protected-bounds-mismatch")
    }

    $baselineBitmap = [System.Drawing.Bitmap]::new($ReferenceImage)
    $candidateBitmap = [System.Drawing.Bitmap]::new($ActualImage)
    try {
        $dimensionsExact =
            $baselineBitmap.Width -eq $candidateBitmap.Width -and
            $baselineBitmap.Height -eq $candidateBitmap.Height
        if (-not $dimensionsExact) {
            $reasons.Add("image-dimension-mismatch")
        }

        $changedPixels = 0
        $changedCanvasPixels = 0
        $changedOutsideAllowedPixels = 0
        $alphaChangedPixels = 0
        $maximumObservedRgbDelta = 0
        if ($dimensionsExact) {
            $startY = $titleBounds.Top
            $endY = $canvasBounds.Bottom
            if (
                $startY -lt 0 -or
                $endY -gt $baselineBitmap.Height -or
                $startY -ge $endY -or
                $canvasBounds.Right -gt $baselineBitmap.Width
            ) {
                throw "Canonical app bounds are outside the screenshot."
            }
            foreach ($y in $startY..($endY - 1)) {
                foreach ($x in 0..($baselineBitmap.Width - 1)) {
                    $baselinePixel = $baselineBitmap.GetPixel($x, $y)
                    $candidatePixel = $candidateBitmap.GetPixel($x, $y)
                    if ($baselinePixel.ToArgb() -eq $candidatePixel.ToArgb()) {
                        continue
                    }
                    $changedPixels += 1
                    $redDelta = [Math]::Abs([int]$baselinePixel.R - [int]$candidatePixel.R)
                    $greenDelta = [Math]::Abs([int]$baselinePixel.G - [int]$candidatePixel.G)
                    $blueDelta = [Math]::Abs([int]$baselinePixel.B - [int]$candidatePixel.B)
                    $rgbDelta = [Math]::Max($redDelta, [Math]::Max($greenDelta, $blueDelta))
                    $maximumObservedRgbDelta = [Math]::Max($maximumObservedRgbDelta, $rgbDelta)
                    if ($baselinePixel.A -ne $candidatePixel.A) {
                        $alphaChangedPixels += 1
                    }
                    if ($canvasBounds.Contains($x, $y)) {
                        $changedCanvasPixels += 1
                    }
                    if (-not (Test-InAnyRectangle $x $y $allowedBounds)) {
                        $changedOutsideAllowedPixels += 1
                    }
                }
            }
        }

        if ($changedPixels -gt $maximumChangedPixels) {
            $reasons.Add("changed-pixel-cap-exceeded")
        }
        if ($maximumObservedRgbDelta -gt $maximumRgbChannelDelta) {
            $reasons.Add("rgb-channel-delta-exceeded")
        }
        if ($alphaChangedPixels -ne 0) {
            $reasons.Add("alpha-changed")
        }
        if ($changedOutsideAllowedPixels -ne 0) {
            $reasons.Add("pixel-changed-outside-allowed-controls")
        }
        if ($changedCanvasPixels -ne 0) {
            $reasons.Add("canvas-pixel-changed")
        }

        [pscustomobject]@{
            schema = $schema
            verdict = if ($reasons.Count -eq 0) { "PASS" } else { "FAIL" }
            hierarchy_exact = $hierarchyExact
            changed_pixels = $changedPixels
            changed_pixel_cap = $maximumChangedPixels
            maximum_rgb_channel_delta = $maximumObservedRgbDelta
            allowed_rgb_channel_delta = $maximumRgbChannelDelta
            alpha_changed_pixels = $alphaChangedPixels
            changed_outside_allowed_pixels = $changedOutsideAllowedPixels
            changed_canvas_pixels = $changedCanvasPixels
            title_bounds = $titleBounds.ToString()
            canvas_bounds = $canvasBounds.ToString()
            failure_reasons = @($reasons)
        }
    }
    finally {
        $baselineBitmap.Dispose()
        $candidateBitmap.Dispose()
    }
}

function Invoke-SelfTest {
    $temporary = Join-Path ([System.IO.Path]::GetTempPath()) ("nene-ui-oracle-" + [guid]::NewGuid())
    New-Item -ItemType Directory -Path $temporary | Out-Null
    try {
        $xml = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes" ?>
<hierarchy rotation="0">
  <node package="io.github.hideyukimori.nenepixel" class="android.view.View" text="" content-desc="" bounds="[0,0][100,50]" enabled="true" clickable="false" checked="false">
    <node package="io.github.hideyukimori.nenepixel" class="android.widget.TextView" text="NENE-PIXEL" content-desc="" bounds="[0,0][20,5]" enabled="true" clickable="false" checked="false" />
    <node package="io.github.hideyukimori.nenepixel" class="android.widget.TextView" text="Active color" content-desc="" bounds="[0,5][40,45]" enabled="true" clickable="false" checked="false" />
    <node package="io.github.hideyukimori.nenepixel" class="android.view.View" text="" content-desc="Active color swatch" bounds="[0,5][40,45]" enabled="true" clickable="false" checked="false" />
    <node package="io.github.hideyukimori.nenepixel" class="android.widget.TextView" text="Palette" content-desc="" bounds="[0,5][40,45]" enabled="true" clickable="false" checked="false" />
    <node package="io.github.hideyukimori.nenepixel" class="android.view.View" text="" content-desc="Palette color 1, test" bounds="[0,5][40,45]" enabled="true" clickable="true" checked="true" />
    <node package="io.github.hideyukimori.nenepixel" class="android.view.View" text="" content-desc="Palette color 2, test" bounds="[0,5][40,45]" enabled="true" clickable="true" checked="false" />
    <node package="io.github.hideyukimori.nenepixel" class="android.view.View" text="" content-desc="Palette color 3, test" bounds="[0,5][40,45]" enabled="true" clickable="true" checked="false" />
    <node package="io.github.hideyukimori.nenepixel" class="android.view.View" text="" content-desc="Palette color 4, test" bounds="[0,5][40,45]" enabled="true" clickable="true" checked="false" />
    <node package="io.github.hideyukimori.nenepixel" class="android.view.View" text="" content-desc="Palette color 5, test" bounds="[0,5][40,45]" enabled="true" clickable="true" checked="false" />
    <node package="io.github.hideyukimori.nenepixel" class="android.view.View" text="" content-desc="Palette color 6, test" bounds="[0,5][40,45]" enabled="true" clickable="true" checked="false" />
    <node package="io.github.hideyukimori.nenepixel" class="android.view.View" text="" content-desc="Palette color 7, test" bounds="[0,5][40,45]" enabled="true" clickable="true" checked="false" />
    <node package="io.github.hideyukimori.nenepixel" class="android.view.View" text="" content-desc="Palette color 8, test" bounds="[0,5][40,45]" enabled="true" clickable="true" checked="false" />
    <node package="io.github.hideyukimori.nenepixel" class="android.view.View" text="" content-desc="Pencil tool" bounds="[0,5][40,45]" enabled="true" clickable="true" checked="true" />
    <node package="io.github.hideyukimori.nenepixel" class="android.view.View" text="" content-desc="Eraser tool" bounds="[0,5][40,45]" enabled="true" clickable="true" checked="false" />
    <node package="io.github.hideyukimori.nenepixel" class="android.view.View" text="" content-desc="16 by 16 pixel canvas" bounds="[60,20][100,50]" enabled="true" clickable="false" checked="false" />
  </node>
</hierarchy>
'@
        $baselineUiPath = Join-Path $temporary "baseline.xml"
        $candidateUiPath = Join-Path $temporary "candidate.xml"
        [System.IO.File]::WriteAllText($baselineUiPath, $xml)
        [System.IO.File]::WriteAllText($candidateUiPath, $xml)
        $baselineImagePath = Join-Path $temporary "baseline.png"
        $candidateImagePath = Join-Path $temporary "candidate.png"

        function New-TestImage {
            param(
                [string]$Path,
                [scriptblock]$Mutate
            )
            $image = [System.Drawing.Bitmap]::new(100, 50)
            try {
                $graphics = [System.Drawing.Graphics]::FromImage($image)
                try {
                    $graphics.Clear([System.Drawing.Color]::FromArgb(255, 10, 20, 30))
                }
                finally {
                    $graphics.Dispose()
                }
                & $Mutate $image
                $image.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
            }
            finally {
                $image.Dispose()
            }
        }

        New-TestImage $baselineImagePath { param($image) }
        $cases = @(
            @{ Name = "exact"; Expected = "PASS"; Mutate = { param($image) } },
            @{
                Name = "allowed-one-step"
                Expected = "PASS"
                Mutate = { param($image) $image.SetPixel(1, 6, [System.Drawing.Color]::FromArgb(255, 11, 20, 30)) }
            },
            @{
                Name = "two-step"
                Expected = "rgb-channel-delta-exceeded"
                Mutate = { param($image) $image.SetPixel(1, 6, [System.Drawing.Color]::FromArgb(255, 12, 20, 30)) }
            },
            @{
                Name = "outside"
                Expected = "pixel-changed-outside-allowed-controls"
                Mutate = { param($image) $image.SetPixel(45, 6, [System.Drawing.Color]::FromArgb(255, 11, 20, 30)) }
            },
            @{
                Name = "alpha"
                Expected = "alpha-changed"
                Mutate = { param($image) $image.SetPixel(1, 6, [System.Drawing.Color]::FromArgb(254, 10, 20, 30)) }
            },
            @{
                Name = "canvas"
                Expected = "canvas-pixel-changed"
                Mutate = { param($image) $image.SetPixel(65, 25, [System.Drawing.Color]::FromArgb(255, 11, 20, 30)) }
            },
            @{
                Name = "changed-pixel-cap"
                Expected = "changed-pixel-cap-exceeded"
                Mutate = {
                    param($image)
                    foreach ($index in 0..1442) {
                        $x = $index % 40
                        $y = 5 + [Math]::Floor($index / 40)
                        $image.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, 11, 20, 30))
                    }
                }
            }
        )
        foreach ($case in $cases) {
            New-TestImage $candidateImagePath $case.Mutate
            $result =
                Test-VisualPair `
                    $baselineImagePath `
                    $candidateImagePath `
                    $baselineUiPath `
                    $candidateUiPath
            if ($case.Expected -eq "PASS") {
                if ($result.verdict -ne "PASS") {
                    throw "Self-test $($case.Name) unexpectedly failed."
                }
            }
            elseif (-not ($result.failure_reasons -contains $case.Expected)) {
                throw "Self-test $($case.Name) did not report $($case.Expected)."
            }
        }

        $hierarchyMismatch = $xml.Replace('text="Active color"', 'text="Changed"')
        [System.IO.File]::WriteAllText($candidateUiPath, $hierarchyMismatch)
        New-TestImage $candidateImagePath { param($image) }
        try {
            $result =
                Test-VisualPair `
                    $baselineImagePath `
                    $candidateImagePath `
                    $baselineUiPath `
                    $candidateUiPath
            throw "Hierarchy self-test unexpectedly completed: $($result.verdict)"
        }
        catch {
            if ($_.Exception.Message -notmatch "Expected exactly one Active color node") {
                throw
            }
        }
        "visual-oracle-self-test=PASS cases=8"
    }
    finally {
        Remove-Item -LiteralPath $temporary -Recurse -Force
    }
}

if ($SelfTest) {
    Invoke-SelfTest
    exit 0
}

foreach ($required in @($BaselineImage, $CandidateImage, $BaselineUi, $CandidateUi, $OutputJson)) {
    if ([string]::IsNullOrWhiteSpace($required)) {
        throw "All artifact paths are required unless -SelfTest is used."
    }
}
if (Test-Path -LiteralPath $OutputJson) {
    throw "Output already exists: $OutputJson"
}

$result = Test-VisualPair $BaselineImage $CandidateImage $BaselineUi $CandidateUi
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $OutputJson -Encoding utf8NoBOM
if ($result.verdict -ne "PASS") {
    throw "Visual oracle failed: $($result.failure_reasons -join ', ')"
}
$result

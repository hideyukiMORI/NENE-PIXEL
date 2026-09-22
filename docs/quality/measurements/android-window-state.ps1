Set-StrictMode -Version Latest

# One parser for the actual-app collector and its timeout/restoration boundary.
function ConvertFrom-NeneWindowRotationState {
    param([string]$NumericText, [string]$WindowText)
    if ($NumericText -cnotmatch '^[0-3]$') { throw 'The numeric user_rotation setting is unavailable.' }
    $modeMatch =
        [regex]::Match(
            $windowText,
            "(?m)^\s*mUserRotationMode=(USER_ROTATION_FREE|USER_ROTATION_LOCKED)\s+mUserRotation=ROTATION_(0|90|180|270)(?:\s|$)"
        )
    $currentMatch = [regex]::Match($windowText, "(?m)^\s*mRotation=([0-3])(?:\s|$)")
    $displayFramesMatch =
        [regex]::Match($windowText, "(?m)^\s*DisplayFrames\s+w=(\d+)\s+h=(\d+)\s+r=([0-3])(?:\s|$)")
    if (-not $modeMatch.Success -or -not $currentMatch.Success -or -not $displayFramesMatch.Success) {
        throw "WindowManager rotation state is unavailable."
    }

    $currentRotation = [int]$currentMatch.Groups[1].Value
    $reportedUserRotation = [int]$modeMatch.Groups[2].Value / 90
    $displayFramesRotation = [int]$displayFramesMatch.Groups[3].Value
    if ($displayFramesRotation -ne $currentRotation) {
        throw "WindowManager rotation and display-frame rotation disagree."
    }
    [pscustomobject]@{
        mode = if ($modeMatch.Groups[1].Value -eq "USER_ROTATION_LOCKED") { "locked" } else { "free" }
        numeric_user_rotation = [int]$numericText
        reported_user_rotation = $reportedUserRotation
        current_rotation = $currentRotation
        logical_width = [int]$displayFramesMatch.Groups[1].Value
        logical_height = [int]$displayFramesMatch.Groups[2].Value
    }
}

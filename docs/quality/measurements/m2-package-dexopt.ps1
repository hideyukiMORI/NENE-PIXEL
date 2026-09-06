Set-StrictMode -Version Latest

function Get-M2PackageDexoptBlock {
    param(
        [Parameter(Mandatory = $true)]
        [string]$DexoptText,

        [Parameter(Mandatory = $true)]
        [ValidateNotNullOrEmpty()]
        [string]$PackageName
    )

    $packagePattern = "(?m)^(?<indent>[ `t]*)\[" + [regex]::Escape($PackageName) + "\][ `t]*`r?$"
    $packageMatches = [regex]::Matches($DexoptText, $packagePattern)
    if ($packageMatches.Count -ne 1) {
        throw "The dexopt output must contain exactly one block for package $PackageName."
    }

    $packageMatch = $packageMatches[0]
    $packageIndent = [regex]::Escape($packageMatch.Groups["indent"].Value)
    $nextPackageRegex = [regex]::new("(?m)^$packageIndent\[[^]=`r`n]+\][ `t]*`r?$")
    $nextPackageMatch = $nextPackageRegex.Match(
        $DexoptText,
        $packageMatch.Index + $packageMatch.Length
    )
    $packageEnd = if ($nextPackageMatch.Success) { $nextPackageMatch.Index } else { $DexoptText.Length }
    return $DexoptText.Substring($packageMatch.Index, $packageEnd - $packageMatch.Index)
}

function Assert-M2PackageSpeedProfile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$DexoptText,

        [Parameter(Mandatory = $true)]
        [ValidateNotNullOrEmpty()]
        [string]$PackageName
    )

    $packageDexopt = Get-M2PackageDexoptBlock -DexoptText $DexoptText -PackageName $PackageName
    if ($packageDexopt -notmatch "\[status=speed-profile\]") {
        throw "The installed package does not report the fixed speed-profile runtime state."
    }
    return $packageDexopt
}

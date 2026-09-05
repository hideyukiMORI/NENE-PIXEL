[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'generate-baseline-profile.ps1')

$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    'nene-baseline-profile-evidence-' + [guid]::NewGuid().ToString('N')
)

function Assert-Equal {
    param(
        [Parameter(Mandatory = $true)]$Expected,
        [Parameter(Mandatory = $true)]$Actual,
        [Parameter(Mandatory = $true)][string]$Message
    )

    if ($Expected -cne $Actual) {
        throw "$Message Expected=$Expected Actual=$Actual"
    }
}

function Assert-Rejected {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Operation,
        [Parameter(Mandatory = $true)][string]$ExpectedMessage
    )

    try {
        & $Operation
    }
    catch {
        if ($_.Exception.Message -notlike "*$ExpectedMessage*") {
            throw "Unexpected rejection. Expected '$ExpectedMessage', got '$($_.Exception.Message)'."
        }
        return
    }
    throw "Expected rejection containing: $ExpectedMessage"
}

function Write-FixtureProfile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Content,
        [Parameter(Mandatory = $true)][datetime]$LastWriteUtc
    )

    $parent = Split-Path -Parent $Path
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::SetLastWriteTimeUtc($Path, $LastWriteUtc)
}

function New-InvocationFixture {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Content,
        [Parameter(Mandatory = $true)][datetime]$StartedUtc
    )

    $root = Join-Path $temporaryRoot $Name
    $producer = Join-Path $root 'producer'
    $merged = Join-Path $root 'merged.txt'
    $source = Join-Path $root 'source.txt'
    Write-FixtureProfile -Path (Join-Path $producer 'journey-baseline-prof.txt') -Content $Content `
        -LastWriteUtc $StartedUtc.AddSeconds(1)
    Write-FixtureProfile -Path $merged -Content $Content -LastWriteUtc $StartedUtc.AddSeconds(1)
    Write-FixtureProfile -Path $source -Content $Content -LastWriteUtc $StartedUtc.AddSeconds(1)
    return [pscustomobject]@{ Producer = $producer; Merged = $merged; Source = $source }
}

try {
    New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
    $lf = "HSPLexample/Canvas;->draw()V`nSPLexample/Undo;->run()V"
    $crlf = $lf.Replace("`n", "`r`n")
    $lfPath = Join-Path $temporaryRoot 'lf.txt'
    $crlfPath = Join-Path $temporaryRoot 'crlf.txt'
    $now = [datetime]::UtcNow
    Write-FixtureProfile -Path $lfPath -Content $lf -LastWriteUtc $now
    Write-FixtureProfile -Path $crlfPath -Content $crlf -LastWriteUtc $now
    $lfRecord = Get-CanonicalProfileRecord -Path $lfPath
    $crlfRecord = Get-CanonicalProfileRecord -Path $crlfPath
    Assert-Equal $lfRecord.CanonicalSha256 $crlfRecord.CanonicalSha256 'LF/CRLF must normalize identically.'
    $crPath = Join-Path $temporaryRoot 'cr.txt'
    Write-FixtureProfile -Path $crPath -Content $lf.Replace("`n", "`r") -LastWriteUtc $now
    Assert-Rejected { Get-CanonicalProfileRecord -Path $crPath } 'only LF or CRLF'

    $existingEvidence = Join-Path $temporaryRoot 'existing-evidence'
    New-Item -ItemType Directory -Path $existingEvidence | Out-Null
    Assert-Rejected { New-ExclusiveEvidenceDirectory -Path $existingEvidence } 'will not be overwritten'

    $preexistingOutput = Join-Path $temporaryRoot 'preexisting-output'
    $preservedOutput = Join-Path $temporaryRoot 'preserved-output'
    New-Item -ItemType Directory -Path $preexistingOutput | Out-Null
    Set-Content -LiteralPath (Join-Path $preexistingOutput 'old.txt') -Value 'old' -Encoding utf8NoBOM
    Move-PreexistingEvidenceTree -Source $preexistingOutput -Destination $preservedOutput
    Assert-Equal $false (Test-Path -LiteralPath $preexistingOutput) 'Old output must leave the producer path.'
    Assert-Equal $true (Test-Path -LiteralPath (Join-Path $preservedOutput 'old.txt')) `
        'Old output must be retained before generation.'

    $restorePath = Join-Path $temporaryRoot 'restore.txt'
    [System.IO.File]::WriteAllText($restorePath, 'before', [System.Text.UTF8Encoding]::new($false))
    $restoreState = Get-FileState -Path $restorePath
    [System.IO.File]::WriteAllText($restorePath, 'failed-candidate', [System.Text.UTF8Encoding]::new($false))
    Restore-FileState -Path $restorePath -State $restoreState
    Assert-Equal 'before' ([System.IO.File]::ReadAllText($restorePath)) `
        'A failed acceptance must restore the pre-generation source.'

    $failedFixture = New-InvocationFixture -Name 'failed' -Content $lf -StartedUtc $now
    Get-ChildItem -LiteralPath $failedFixture.Producer -File | ForEach-Object {
        $_.LastWriteTimeUtc = $now.AddMinutes(-1)
    }
    Assert-Rejected {
        Assert-FreshProducerOutput -ExitCode 1 -OutputLines @(
            "> Task $script:ProducerTask FAILED"
        ) -StartedUtc $now -ProducerOutputDirectory $failedFixture.Producer
    } 'Gradle invocation failed'

    $staleFixture = New-InvocationFixture -Name 'stale' -Content $lf -StartedUtc $now
    Get-ChildItem -LiteralPath $staleFixture.Producer -File | ForEach-Object {
        $_.LastWriteTimeUtc = $now.AddMinutes(-1)
    }
    Assert-Rejected {
        Assert-FreshProducerOutput -ExitCode 0 -OutputLines @(
            "> Task $script:ProducerTask"
        ) -StartedUtc $now -ProducerOutputDirectory $staleFixture.Producer
    } 'No fresh pulled producer profile'

    $cachedProducerFixture = New-InvocationFixture -Name 'cached-producer' -Content $lf -StartedUtc $now
    Assert-Rejected {
        Assert-FreshProducerOutput -ExitCode 0 -OutputLines @(
            "> Task $script:ProducerTask UP-TO-DATE"
        ) -StartedUtc $now -ProducerOutputDirectory $cachedProducerFixture.Producer
    } 'producer task did not execute'

    $pullFixture = New-InvocationFixture -Name 'pull-failure' -Content $lf -StartedUtc $now
    Assert-Rejected {
        Assert-FreshProducerOutput -ExitCode 0 -OutputLines @(
            "> Task $script:ProducerTask",
            'Failed to pull producer output.'
        ) -StartedUtc $now -ProducerOutputDirectory $pullFixture.Producer
    } 'output pull failed'

    $firstFixture = New-InvocationFixture -Name 'first-valid' -Content $lf -StartedUtc $now
    $secondFixture = New-InvocationFixture -Name 'second-valid' -Content $crlf -StartedUtc $now
    $executedOutput = @(
        '> Task :app:android:compileNonMinifiedReleaseKotlin FROM-CACHE',
        "> Task $script:ProducerTask",
        '> Task :app:android:mergeBaselineProfile UP-TO-DATE',
        '> Task :app:android:generateBaselineProfile UP-TO-DATE'
    )
    $firstProducer = Assert-FreshProducerOutput -ExitCode 0 -OutputLines $executedOutput `
        -StartedUtc $now -ProducerOutputDirectory $firstFixture.Producer
    $secondProducer = Assert-FreshProducerOutput -ExitCode 0 -OutputLines $executedOutput `
        -StartedUtc $now -ProducerOutputDirectory $secondFixture.Producer
    $firstProfile = Assert-InvocationContent -ProducerResult $firstProducer `
        -MergedProfilePath $firstFixture.Merged -SourceProfilePath $firstFixture.Source
    $secondProfile = Assert-InvocationContent -ProducerResult $secondProducer `
        -MergedProfilePath $secondFixture.Merged -SourceProfilePath $secondFixture.Source

    $staleSourceFixture = New-InvocationFixture -Name 'stale-source' -Content $lf -StartedUtc $now
    Write-FixtureProfile -Path $staleSourceFixture.Merged -Content $lf.Replace('HSP', 'SP') `
        -LastWriteUtc $now.AddSeconds(1)
    Write-FixtureProfile -Path $staleSourceFixture.Source -Content $lf.Replace('HSP', 'SP') `
        -LastWriteUtc $now.AddSeconds(1)
    $staleSourceProducer = Assert-FreshProducerOutput -ExitCode 0 -OutputLines $executedOutput `
        -StartedUtc $now -ProducerOutputDirectory $staleSourceFixture.Producer
    Assert-Rejected {
        Assert-InvocationContent -ProducerResult $staleSourceProducer `
            -MergedProfilePath $staleSourceFixture.Merged -SourceProfilePath $staleSourceFixture.Source
    } 'producer, merged, and source profiles'
    $first = [pscustomobject]@{
        SourceRevision = '1' * 40
        AppApkSha256 = 'a' * 64
        TestApkSha256 = 'b' * 64
        Profile = $firstProfile
    }
    $second = [pscustomobject]@{
        SourceRevision = '1' * 40
        AppApkSha256 = 'a' * 64
        TestApkSha256 = 'b' * 64
        Profile = $secondProfile
    }
    Assert-GenerationPair -First $first -Second $second

    $driftFixture = New-InvocationFixture -Name 'flag-drift' -Content $lf.Replace('HSP', 'SP') `
        -StartedUtc $now
    $driftProducer = Assert-FreshProducerOutput -ExitCode 0 -OutputLines $executedOutput `
        -StartedUtc $now -ProducerOutputDirectory $driftFixture.Producer
    $driftProfile = Assert-InvocationContent -ProducerResult $driftProducer `
        -MergedProfilePath $driftFixture.Merged -SourceProfilePath $driftFixture.Source
    $drift = [pscustomobject]@{
        SourceRevision = '1' * 40
        AppApkSha256 = 'a' * 64
        TestApkSha256 = 'b' * 64
        Profile = $driftProfile
    }
    Assert-Rejected { Assert-GenerationPair -First $first -Second $drift } 'exact rule and flag equality'

    $changedApk = [pscustomobject]@{
        SourceRevision = '1' * 40
        AppApkSha256 = 'c' * 64
        TestApkSha256 = 'b' * 64
        Profile = $secondProfile
    }
    Assert-Rejected { Assert-GenerationPair -First $first -Second $changedApk } 'APK identities differ'

    Write-Output 'BASELINE_PROFILE_EVIDENCE_VALIDATION=pass'
    Write-Output (
        'CASES=lf-crlf,bare-cr,overwrite,preexisting-retention,failure-restore,failed-with-stale-source,' +
        'stale,cached-producer,pull-failure,' +
        'fresh-match,stale-source,flag-drift,apk-drift'
    )
}
finally {
    $resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)
    $resolvedSystemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTemporaryRoot.StartsWith($resolvedSystemTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

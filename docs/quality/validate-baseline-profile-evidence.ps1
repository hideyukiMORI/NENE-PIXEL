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
        [Parameter(Mandatory = $true)][AllowNull()]$Expected,
        [Parameter(Mandatory = $true)][AllowNull()]$Actual,
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

function Assert-FileStateEqual {
    param(
        [Parameter(Mandatory = $true)]$Expected,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Message
    )

    $actual = Get-FileState -Path $Path
    Assert-Equal $Expected.Exists $actual.Exists "$Message existence differs."
    if ($Expected.Exists) {
        Assert-Equal ([System.Convert]::ToBase64String($Expected.Bytes)) `
            ([System.Convert]::ToBase64String($actual.Bytes)) $Message
    }
}

function New-OrchestrationFixtureRepository {
    param([Parameter(Mandatory = $true)][string]$Name)

    $root = Join-Path $temporaryRoot ("orchestration-$Name")
    New-Item -ItemType Directory -Path $root | Out-Null
    $profilePath = Join-Path $root $script:ProfileRelativePath
    $hashPath = Join-Path $root $script:HashRelativePath
    $originalProfileContent = "HSPLOld/Profile;->keep()V`nSPLOld/Undo;->keep()V"
    Write-FixtureProfile -Path $profilePath -Content $originalProfileContent -LastWriteUtc ([datetime]::UtcNow)
    [System.IO.File]::WriteAllText($hashPath, 'original-hash', [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText(
        (Join-Path $root '.gitignore'),
        "**/build/`n",
        [System.Text.UTF8Encoding]::new($false)
    )

    foreach ($relativePath in @($script:AppApkRelativePath, $script:TestApkRelativePath)) {
        $path = Join-Path $root $relativePath
        New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
        [System.IO.File]::WriteAllBytes($path, [System.Text.Encoding]::ASCII.GetBytes("fixture-$relativePath"))
    }

    & git -C $root init --quiet
    & git -C $root config core.autocrlf false
    & git -C $root config user.name 'NENE fixture'
    & git -C $root config user.email 'fixture@example.invalid'
    & git -C $root add .gitignore $script:ProfileRelativePath $script:HashRelativePath
    & git -C $root -c commit.gpgsign=false commit --quiet -m 'fixture'
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to initialize orchestration fixture repository: $Name"
    }

    return [pscustomobject]@{
        Root = $root
        ProfilePath = $profilePath
        HashPath = $hashPath
        OriginalProfile = Get-FileState -Path $profilePath
        OriginalHash = Get-FileState -Path $hashPath
    }
}

function New-OrchestrationFixtureInvoker {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$EvidenceIdentity,
        [Parameter(Mandatory = $true)][string]$Mode,
        [Parameter(Mandatory = $true)][string]$FirstContent,
        [Parameter(Mandatory = $true)][string]$SecondContent
    )

    $state = [pscustomobject]@{
        GenerationCount = 0
        ValidationCount = 0
        FirstManifestSeenBeforeSecond = $false
    }
    $producerOutputRelativePath = $script:ProducerOutputRelativePath
    $mergedRelativePath = $script:MergedRelativePath
    $profileRelativePath = $script:ProfileRelativePath
    $producerTask = $script:ProducerTask
    $invoker = {
        param(
            [Parameter(Mandatory = $true)][string]$RepositoryRoot,
            [Parameter(Mandatory = $true)][string]$LogPath,
            [Parameter(Mandatory = $true)][string[]]$GradleArguments
        )

        if ($GradleArguments[0] -eq ':app:android:generateBaselineProfile') {
            $state.GenerationCount++
            if ($state.GenerationCount -eq 2) {
                $firstManifest = Join-Path $RepositoryRoot `
                    "build/reports/baseline-profile-generation/$EvidenceIdentity/invocation-1/manifest.json"
                $state.FirstManifestSeenBeforeSecond = Test-Path -LiteralPath $firstManifest -PathType Leaf
                if (-not $state.FirstManifestSeenBeforeSecond) {
                    throw 'Invocation 1 manifest was not preserved before invocation 2.'
                }
            }
            if ($Mode -eq 'launch-failure' -and $state.GenerationCount -eq 1) {
                throw [System.ComponentModel.Win32Exception]::new('synthetic native process start failure')
            }

            $content = if ($state.GenerationCount -eq 1) { $FirstContent } else { $SecondContent }
            $writtenUtc = [datetime]::UtcNow.AddSeconds(1)
            $producerPath = Join-Path (Join-Path $RepositoryRoot $producerOutputRelativePath) `
                'journey-baseline-prof.txt'
            foreach ($path in @(
                    $producerPath,
                    (Join-Path $RepositoryRoot $mergedRelativePath),
                    (Join-Path $RepositoryRoot $profileRelativePath)
                )) {
                New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
                [System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))
                [System.IO.File]::SetLastWriteTimeUtc($path, $writtenUtc)
            }
            $lines = @(
                '> Task :app:android:compileNonMinifiedReleaseKotlin FROM-CACHE',
                "> Task $producerTask",
                '> Task :app:android:mergeBaselineProfile UP-TO-DATE'
            )
            $lines | Set-Content -LiteralPath $LogPath -Encoding utf8NoBOM
            return [pscustomobject]@{ ExitCode = 0; OutputLines = $lines }
        }

        if ($GradleArguments[0] -eq 'validateBaselineProfile') {
            $state.ValidationCount++
            $exitCode = if ($Mode -eq 'validation-failure') { 1 } else { 0 }
            $lines = @("fixture validateBaselineProfile exit=$exitCode")
            $lines | Set-Content -LiteralPath $LogPath -Encoding utf8NoBOM
            return [pscustomobject]@{ ExitCode = $exitCode; OutputLines = $lines }
        }

        throw "Unexpected fixture Gradle arguments: $GradleArguments"
    }.GetNewClosure()

    return [pscustomobject]@{ State = $state; Invoker = $invoker }
}

function Assert-OrchestrationFailureRestored {
    param(
        [Parameter(Mandatory = $true)]$Repository,
        [Parameter(Mandatory = $true)][string]$EvidenceIdentity
    )

    Assert-FileStateEqual -Expected $Repository.OriginalProfile -Path $Repository.ProfilePath `
        -Message 'Failed orchestration must restore the tracked profile.'
    Assert-FileStateEqual -Expected $Repository.OriginalHash -Path $Repository.HashPath `
        -Message 'Failed orchestration must restore the tracked hash.'
    $acceptance = Join-Path $Repository.Root `
        "build/reports/baseline-profile-generation/$EvidenceIdentity/acceptance-manifest.json"
    Assert-Equal $false (Test-Path -LiteralPath $acceptance) `
        'Failed orchestration must not write acceptance evidence.'
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

    $orchestrationContent = "HSPLexample/Canvas;->draw()V`nSPLexample/Undo;->run()V"
    $successRepository = New-OrchestrationFixtureRepository -Name 'success'
    $successInvoker = New-OrchestrationFixtureInvoker -RepositoryRoot $successRepository.Root `
        -EvidenceIdentity 'success' -Mode 'success' -FirstContent $orchestrationContent `
        -SecondContent $orchestrationContent.Replace("`n", "`r`n")
    Invoke-BaselineProfileEvidenceGeneration -RepositoryRoot $successRepository.Root `
        -EvidenceIdentity 'success' -GradleInvoker $successInvoker.Invoker | Out-Null
    Assert-Equal 2 $successInvoker.State.GenerationCount 'Successful orchestration must run two invocations.'
    Assert-Equal 1 $successInvoker.State.ValidationCount 'Successful orchestration must run final validation.'
    Assert-Equal $true $successInvoker.State.FirstManifestSeenBeforeSecond `
        'Invocation 1 manifest must exist before invocation 2 starts.'
    $successEvidence = Join-Path $successRepository.Root 'build/reports/baseline-profile-generation/success'
    Assert-Equal $true (Test-Path -LiteralPath (Join-Path $successEvidence 'acceptance-manifest.json')) `
        'A valid identical fresh pair must write acceptance evidence.'
    Assert-Equal 'valid' ((Get-Content -LiteralPath (Join-Path $successEvidence 'invocation-1/manifest.json') `
            -Raw | ConvertFrom-Json).status) 'Invocation 1 must be valid.'
    Assert-Equal 'valid' ((Get-Content -LiteralPath (Join-Path $successEvidence 'invocation-2/manifest.json') `
            -Raw | ConvertFrom-Json).status) 'Invocation 2 must be valid.'

    $mismatchRepository = New-OrchestrationFixtureRepository -Name 'mismatch'
    $mismatchInvoker = New-OrchestrationFixtureInvoker -RepositoryRoot $mismatchRepository.Root `
        -EvidenceIdentity 'mismatch' -Mode 'mismatch' -FirstContent $orchestrationContent `
        -SecondContent $orchestrationContent.Replace('HSP', 'SP')
    Assert-Rejected {
        Invoke-BaselineProfileEvidenceGeneration -RepositoryRoot $mismatchRepository.Root `
            -EvidenceIdentity 'mismatch' -GradleInvoker $mismatchInvoker.Invoker
    } 'not reproducible with exact rule and flag equality'
    Assert-Equal $true $mismatchInvoker.State.FirstManifestSeenBeforeSecond `
        'Mismatch orchestration must preserve invocation 1 before invocation 2.'
    Assert-OrchestrationFailureRestored -Repository $mismatchRepository -EvidenceIdentity 'mismatch'

    $launchRepository = New-OrchestrationFixtureRepository -Name 'launch-failure'
    $launchInvoker = New-OrchestrationFixtureInvoker -RepositoryRoot $launchRepository.Root `
        -EvidenceIdentity 'launch-failure' -Mode 'launch-failure' -FirstContent $orchestrationContent `
        -SecondContent $orchestrationContent
    Assert-Rejected {
        Invoke-BaselineProfileEvidenceGeneration -RepositoryRoot $launchRepository.Root `
            -EvidenceIdentity 'launch-failure' -GradleInvoker $launchInvoker.Invoker
    } 'synthetic native process start failure'
    $launchEvidence = Join-Path $launchRepository.Root `
        'build/reports/baseline-profile-generation/launch-failure/invocation-1'
    $launchManifest = Get-Content -LiteralPath (Join-Path $launchEvidence 'manifest.json') -Raw | ConvertFrom-Json
    Assert-Equal 'invalid' $launchManifest.status 'A process-start failure must write an invalid manifest.'
    Assert-Equal $null $launchManifest.gradle_exit_code `
        'A process-start failure has no invented Gradle exit code.'
    $launchLog = Get-Content -LiteralPath (Join-Path $launchEvidence 'gradle-output.log') -Raw
    if ($launchLog -notmatch 'Win32Exception' -or $launchLog -notmatch 'synthetic native process start failure') {
        throw 'A process-start failure log must retain native exception type and detail.'
    }
    Assert-OrchestrationFailureRestored -Repository $launchRepository -EvidenceIdentity 'launch-failure'

    $validationRepository = New-OrchestrationFixtureRepository -Name 'validation-failure'
    $validationInvoker = New-OrchestrationFixtureInvoker -RepositoryRoot $validationRepository.Root `
        -EvidenceIdentity 'validation-failure' -Mode 'validation-failure' `
        -FirstContent $orchestrationContent -SecondContent $orchestrationContent
    Assert-Rejected {
        Invoke-BaselineProfileEvidenceGeneration -RepositoryRoot $validationRepository.Root `
            -EvidenceIdentity 'validation-failure' -GradleInvoker $validationInvoker.Invoker
    } 'validateBaselineProfile failed with exit code 1'
    Assert-Equal 2 $validationInvoker.State.GenerationCount `
        'Final-validation failure must occur after two real invocation orchestrations.'
    Assert-Equal $true $validationInvoker.State.FirstManifestSeenBeforeSecond `
        'Final-validation failure must preserve invocation 1 before invocation 2.'
    Assert-Equal 'matched' ((Get-Content -LiteralPath (Join-Path $validationRepository.Root `
                'build/reports/baseline-profile-generation/validation-failure/pair-manifest.json') `
            -Raw | ConvertFrom-Json).status) 'Final validation must follow a matched pair.'
    Assert-OrchestrationFailureRestored -Repository $validationRepository `
        -EvidenceIdentity 'validation-failure'

    Write-Output 'BASELINE_PROFILE_EVIDENCE_VALIDATION=pass'
    Write-Output (
        'CASES=lf-crlf,bare-cr,overwrite,preexisting-retention,failure-restore,failed-with-stale-source,' +
        'stale,cached-producer,pull-failure,' +
        'fresh-match,stale-source,flag-drift,apk-drift,' +
        'orchestration-success,orchestration-mismatch,orchestration-launch-failure,' +
        'orchestration-final-validation-failure,manifest-before-next-run,no-acceptance-after-failure'
    )
}
finally {
    $resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)
    $resolvedSystemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTemporaryRoot.StartsWith($resolvedSystemTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

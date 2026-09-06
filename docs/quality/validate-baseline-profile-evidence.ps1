[CmdletBinding()]
param(
    [string]$RetainedGradleLogPath = '',
    [string]$RetainedAcceptedEvidencePath = '',
    [string]$RetainedAcceptanceManifestSha256 = '',
    [string]$RetainedSourceRevision = '',
    [string]$RetainedAppApkSha256 = '',
    [string]$RetainedTestApkSha256 = '',
    [string]$RetainedPairManifestSha256 = '',
    [string]$RetainedCanonicalSha256 = ''
)

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

function Update-FixtureAcceptanceChain {
    param(
        [Parameter(Mandatory = $true)][string]$EvidenceRoot,
        [Parameter(Mandatory = $true)][ValidateRange(1, 2)][int]$Invocation
    )

    $invocationPath = Join-Path $EvidenceRoot "invocation-$Invocation/manifest.json"
    $pairPath = Join-Path $EvidenceRoot 'pair-manifest.json'
    $pair = Get-Content -LiteralPath $pairPath -Raw | ConvertFrom-Json
    $pair.invocation_manifest_sha256[$Invocation - 1] = Get-FileSha256 $invocationPath
    Write-JsonFile $pair $pairPath
    $acceptancePath = Join-Path $EvidenceRoot 'acceptance-manifest.json'
    $acceptance = Get-Content -LiteralPath $acceptancePath -Raw | ConvertFrom-Json
    $acceptance.pair_manifest_sha256 = Get-FileSha256 $pairPath
    Write-JsonFile $acceptance $acceptancePath
    return [pscustomobject]@{
        AcceptancePath = $acceptancePath
        AcceptanceSha256 = Get-FileSha256 $acceptancePath
        PairSha256 = Get-FileSha256 $pairPath
        Pair = $pair
        Acceptance = $acceptance
    }
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
        [Parameter(Mandatory = $true)][string]$SecondContent,
        [string]$RetainedLogPath = ''
    )

    $state = [pscustomobject]@{
        GenerationCount = 0
        ValidationCount = 0
        FirstManifestSeenBeforeSecond = $false
        FrozenInputsSeenBeforeSecond = $false
        FirstOutputDifferedFromFrozenInput = $false
    }
    $producerOutputRelativePath = $script:ProducerOutputRelativePath
    $producerResultsRelativePath = $script:ProducerResultsRelativePath
    $mergedRelativePath = $script:MergedRelativePath
    $profileRelativePath = $script:ProfileRelativePath
    $appApkRelativePath = $script:AppApkRelativePath
    $producerTask = $script:ProducerTask
    $retainedLogPathForInvoker = $RetainedLogPath
    $originalProfileHash = (& git -C $RepositoryRoot hash-object -- $profileRelativePath).Trim()
    $originalHashFileHash = (& git -C $RepositoryRoot hash-object -- $script:HashRelativePath).Trim()
    $hashRelativePath = $script:HashRelativePath
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
                $currentProfileHash = (& git -C $RepositoryRoot hash-object -- $profileRelativePath).Trim()
                $currentHashFileHash = (& git -C $RepositoryRoot hash-object -- $hashRelativePath).Trim()
                $state.FrozenInputsSeenBeforeSecond =
                    $currentProfileHash -ceq $originalProfileHash -and
                    $currentHashFileHash -ceq $originalHashFileHash
                if (-not $state.FrozenInputsSeenBeforeSecond) {
                    throw 'Frozen tracked profile inputs were not restored before invocation 2.'
                }
            }
            if ($Mode -eq 'launch-failure' -and $state.GenerationCount -eq 1) {
                throw [System.ComponentModel.Win32Exception]::new('synthetic native process start failure')
            }

            $content = if ($state.GenerationCount -eq 1) { $FirstContent } else { $SecondContent }
            $writtenUtc = [datetime]::UtcNow.AddSeconds(1)
            $producerPath = Join-Path (Join-Path $RepositoryRoot $producerOutputRelativePath) `
                'journey-baseline-prof.txt'
            $profilePaths = @(
                (Join-Path $RepositoryRoot $mergedRelativePath),
                (Join-Path $RepositoryRoot $profileRelativePath)
            )
            if ($Mode -ne 'missing-output') {
                $profilePaths = @($producerPath) + $profilePaths
            }
            foreach ($path in $profilePaths) {
                New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
                if ($Mode -eq 'invalid-utf8' -and $path -eq $producerPath) {
                    [System.IO.File]::WriteAllBytes($path, [byte[]]@(0xff, 0xfe, 0x00, 0x80))
                } else {
                    [System.IO.File]::WriteAllText(
                        $path,
                        $content,
                        [System.Text.UTF8Encoding]::new($false)
                    )
                }
                [System.IO.File]::SetLastWriteTimeUtc($path, $writtenUtc)
            }
            $testExitPath = Join-Path (Join-Path $RepositoryRoot $producerResultsRelativePath) `
                'test-result-exit-code.txt'
            New-Item -ItemType Directory -Path (Split-Path -Parent $testExitPath) -Force | Out-Null
            [System.IO.File]::WriteAllText($testExitPath, '0', [System.Text.Encoding]::ASCII)
            if ($state.GenerationCount -eq 1) {
                $firstOutputHash = (& git -C $RepositoryRoot hash-object -- $profileRelativePath).Trim()
                $state.FirstOutputDifferedFromFrozenInput = $firstOutputHash -cne $originalProfileHash
            }
            if ($Mode -eq 'missing-apk') {
                Remove-Item -LiteralPath (Join-Path $RepositoryRoot $appApkRelativePath) -Force
            }
            if ($Mode -eq 'retained-log-success') {
                [System.IO.File]::WriteAllBytes(
                    $LogPath,
                    [System.IO.File]::ReadAllBytes($retainedLogPathForInvoker)
                )
                $lines = @(Get-Content -LiteralPath $LogPath)
                return [pscustomobject]@{ ExitCode = 0; OutputLines = $lines }
            }
            $lines = @(
                '> Task :app:android:compileNonMinifiedReleaseKotlin FROM-CACHE',
                '',
                "> Task $producerTask",
                '> Task :app:android:mergeBaselineProfile UP-TO-DATE'
            )
            if ($Mode -eq 'pull-failure') {
                $lines += 'Failed to pull producer output.'
            }
            $exitCode = if ($Mode -eq 'nonzero-exit') { 1 } else { 0 }
            $lines | Set-Content -LiteralPath $LogPath -Encoding utf8NoBOM
            return [pscustomobject]@{ ExitCode = $exitCode; OutputLines = $lines }
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

function Assert-NativeTimeoutQuiescence {
    $root = Join-Path $temporaryRoot 'native-timeout'
    New-Item -ItemType Directory -Path $root | Out-Null
    $wrapper = Join-Path $root 'gradlew.bat'
    $lateWrite = Join-Path $root 'timeout-late-write.txt'
    $log = Join-Path $root 'gradle-output.log'
    $batch = @(
        '@echo off',
        'echo timeout-worker-started arguments=%*',
        'start "" /b cmd.exe /d /c "ping.exe 127.0.0.1 -n 4 >NUL & echo late-write>timeout-late-write.txt"',
        'exit /b 0'
    ) -join "`r`n"
    [System.IO.File]::WriteAllText($wrapper, $batch, [System.Text.Encoding]::ASCII)

    Assert-Rejected {
        Invoke-GradleCommand -RepositoryRoot $root -LogPath $log `
            -GradleArguments @('fixtureTask', '--console=plain') -TimeoutSeconds 1
    } 'timed out after 1 seconds'
    Start-Sleep -Seconds 4
    Assert-Equal $false (Test-Path -LiteralPath $lateWrite) `
        'A stopped invocation-owned process tree must not mutate output after timeout restoration.'
    $partialLog = Get-Content -LiteralPath $log -Raw
    if (
        $partialLog -notmatch 'timeout-worker-started' -or
        $partialLog -notmatch '--no-daemon' -or
        $partialLog -notmatch 'timed out after 1 seconds'
    ) {
        throw 'Timeout evidence must retain worker output, process identity, and the timeout failure.'
    }

    $failedTerminationRoot = Join-Path $temporaryRoot 'native-termination-failure'
    New-Item -ItemType Directory -Path $failedTerminationRoot | Out-Null
    $failedTerminationWrapper = Join-Path $failedTerminationRoot 'gradlew.bat'
    $failedTerminationLateWrite = Join-Path $failedTerminationRoot 'timeout-late-write.txt'
    [System.IO.File]::WriteAllText($failedTerminationWrapper, $batch, [System.Text.Encoding]::ASCII)
    $failedTerminationLog = Join-Path $failedTerminationRoot 'gradle-output.log'
    $syntheticFailedTerminator = {
        param([Parameter(Mandatory = $true)][IntPtr]$Job)

        [NenePixelBaselineProfile.JobNativeMethods]::TerminateJobObject($Job, 124) | Out-Null
        throw 'synthetic termination failure after terminating the fixture job'
    }
    $restorationBlocked = $false
    try {
        Invoke-GradleCommand -RepositoryRoot $failedTerminationRoot -LogPath $failedTerminationLog `
            -GradleArguments @('fixtureTask', '--console=plain') -TimeoutSeconds 1 `
            -JobTerminator $syntheticFailedTerminator
    }
    catch {
        if ($_.Exception.Message -notmatch 'synthetic termination failure') {
            throw
        }
        $restorationBlocked = $_.Exception.Data[$script:RestorationBlockedDataKey] -eq $true
    }
    Assert-Equal $true $restorationBlocked `
        'Any job-termination failure must block tracked-file restoration.'
    Start-Sleep -Seconds 4
    Assert-Equal $false (Test-Path -LiteralPath $failedTerminationLateWrite) `
        'The fixture terminator must leave no process leak while testing restoration blocking.'

    $successRoot = Join-Path $temporaryRoot 'native-success'
    New-Item -ItemType Directory -Path $successRoot | Out-Null
    $successWrapper = Join-Path $successRoot 'gradlew.bat'
    [System.IO.File]::WriteAllText(
        $successWrapper,
        "@echo off`r`necho native-success arguments=%*`r`necho.`r`nexit /b 0`r`n",
        [System.Text.Encoding]::ASCII
    )
    $successLog = Join-Path $successRoot 'gradle-output.log'
    $success = Invoke-GradleCommand -RepositoryRoot $successRoot -LogPath $successLog `
        -GradleArguments @('fixtureTask', '--console=plain') -TimeoutSeconds 5
    Assert-Equal 0 $success.ExitCode 'A successful invocation-owned process must retain exit code zero.'
    $successText = [string]::Join("`n", $success.OutputLines)
    if ($successText -notmatch 'native-success' -or $successText -notmatch '--no-daemon') {
        throw 'A successful native invocation must retain output and its no-daemon identity.'
    }
    Assert-Equal 1 @($success.OutputLines | Where-Object { $_ -eq '' }).Count `
        'Native output binding must preserve an empty output line.'
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

function Assert-InvalidInvocationManifest {
    param(
        [Parameter(Mandatory = $true)]$Repository,
        [Parameter(Mandatory = $true)]$Invoker,
        [Parameter(Mandatory = $true)][string]$EvidenceIdentity,
        [Parameter(Mandatory = $true)][string]$ExpectedFailure
    )

    Assert-Rejected {
        Invoke-BaselineProfileEvidenceGeneration -RepositoryRoot $Repository.Root `
            -EvidenceIdentity $EvidenceIdentity -GradleInvoker $Invoker.Invoker
    } $ExpectedFailure
    Assert-Equal 1 $Invoker.State.GenerationCount `
        "$EvidenceIdentity must stop before a second producer invocation."
    $evidenceRoot = Join-Path $Repository.Root `
        "build/reports/baseline-profile-generation/$EvidenceIdentity"
    $manifestPath = Join-Path $evidenceRoot 'invocation-1/manifest.json'
    Assert-Equal $true (Test-Path -LiteralPath $manifestPath -PathType Leaf) `
        "$EvidenceIdentity must fail closed with an invocation manifest."
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    Assert-Equal 'invalid' $manifest.status "$EvidenceIdentity must be recorded as invalid."
    if ($manifest.failure -notlike "*$ExpectedFailure*") {
        throw "$EvidenceIdentity manifest did not retain failure '$ExpectedFailure': $($manifest.failure)"
    }
    foreach ($relativePath in @(
            'invocation-2',
            'pair-manifest.json',
            'acceptance-manifest.json'
        )) {
        Assert-Equal $false (Test-Path -LiteralPath (Join-Path $evidenceRoot $relativePath)) `
            "$EvidenceIdentity must not advance after an invalid first invocation."
    }
    Assert-OrchestrationFailureRestored -Repository $Repository -EvidenceIdentity $EvidenceIdentity
}

try {
    New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
    Assert-NativeTimeoutQuiescence
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
    $readerFailureRepository = New-OrchestrationFixtureRepository -Name 'reader-failure'
    $readerFailureInvoker = New-OrchestrationFixtureInvoker -RepositoryRoot $readerFailureRepository.Root `
        -EvidenceIdentity 'reader-failure' -Mode 'success' -FirstContent $orchestrationContent `
        -SecondContent $orchestrationContent
    $originalReader = (Get-Item Function:Read-BaselineProfileAcceptanceEvidence).ScriptBlock
    Set-Item Function:Read-BaselineProfileAcceptanceEvidence -Value {
        throw 'synthetic acceptance graph failure before publication'
    }
    try {
        Assert-Rejected {
            Invoke-BaselineProfileEvidenceGeneration -RepositoryRoot $readerFailureRepository.Root `
                -EvidenceIdentity 'reader-failure' -GradleInvoker $readerFailureInvoker.Invoker
        } 'synthetic acceptance graph failure before publication'
    }
    finally {
        Set-Item Function:Read-BaselineProfileAcceptanceEvidence -Value $originalReader
    }
    Assert-OrchestrationFailureRestored -Repository $readerFailureRepository `
        -EvidenceIdentity 'reader-failure'
    $readerFailureEvidence = Join-Path $readerFailureRepository.Root `
        'build/reports/baseline-profile-generation/reader-failure'
    Assert-Equal $true (Test-Path -LiteralPath (Join-Path $readerFailureEvidence `
                'acceptance-manifest.pending.json') -PathType Leaf) `
        'A failed graph check must retain its provisional evidence without publishing acceptance.'

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
    Assert-Equal $true $successInvoker.State.FrozenInputsSeenBeforeSecond `
        'Frozen tracked profile inputs must be restored before invocation 2 starts.'
    Assert-Equal $true $successInvoker.State.FirstOutputDifferedFromFrozenInput `
        'The ordering fixture must first replace the frozen source with a different candidate.'
    $successEvidence = Join-Path $successRepository.Root 'build/reports/baseline-profile-generation/success'
    Assert-Equal $true (Test-Path -LiteralPath (Join-Path $successEvidence 'acceptance-manifest.json')) `
        'A valid identical fresh pair must write acceptance evidence.'
    $firstSuccessManifest = Get-Content -LiteralPath (Join-Path $successEvidence `
        'invocation-1/manifest.json') -Raw | ConvertFrom-Json
    Assert-Equal 'valid' $firstSuccessManifest.status 'Invocation 1 must be valid.'
    Assert-Equal $script:ProducerTimeoutSeconds $firstSuccessManifest.timeout_seconds `
        'Invocation manifest must record its producer timeout.'
    Assert-Equal 'valid' ((Get-Content -LiteralPath (Join-Path $successEvidence 'invocation-2/manifest.json') `
            -Raw | ConvertFrom-Json).status) 'Invocation 2 must be valid.'

    $corruptRetainedEvidence = Join-Path $temporaryRoot 'corrupt-retained-evidence'
    Copy-Item -LiteralPath $successEvidence -Destination $corruptRetainedEvidence -Recurse
    Add-Content -LiteralPath (Join-Path $corruptRetainedEvidence 'invocation-2/source-profile.txt') `
        -Value 'SPLexample/Injected;->drift()V' -Encoding utf8NoBOM
    $successAcceptancePath = Join-Path $successEvidence 'acceptance-manifest.json'
    $successAcceptance = Get-Content -LiteralPath $successAcceptancePath -Raw | ConvertFrom-Json
    $successPair = Get-Content -LiteralPath (Join-Path $successEvidence 'pair-manifest.json') `
        -Raw | ConvertFrom-Json
    Assert-Rejected {
        Read-BaselineProfileAcceptanceEvidence `
            -AcceptanceManifestPath (Join-Path $corruptRetainedEvidence 'acceptance-manifest.json') `
            -ExpectedAcceptanceManifestSha256 (Get-FileSha256 $successAcceptancePath) `
            -ExpectedSourceRevision $successPair.source_revision `
            -ExpectedAppApkSha256 $successPair.app_apk_sha256 `
            -ExpectedTestApkSha256 $successPair.test_apk_sha256 `
            -ExpectedPairManifestSha256 $successAcceptance.pair_manifest_sha256 `
            -ExpectedCanonicalSha256 $successAcceptance.canonical_sha256
    } 'Retained evidence byte count for source-profile.txt differs'

    $invalidInvocationEvidence = Join-Path $temporaryRoot 'invalid-invocation-evidence'
    Copy-Item -LiteralPath $successEvidence -Destination $invalidInvocationEvidence -Recurse
    $invalidInvocationPath = Join-Path $invalidInvocationEvidence 'invocation-2/manifest.json'
    $invalidInvocation = Get-Content -LiteralPath $invalidInvocationPath -Raw | ConvertFrom-Json
    $invalidInvocation.status = 'invalid'
    $invalidInvocation.failure = 'synthetic retained invalid invocation'
    Write-JsonFile $invalidInvocation $invalidInvocationPath
    $invalidPairPath = Join-Path $invalidInvocationEvidence 'pair-manifest.json'
    $invalidPair = Get-Content -LiteralPath $invalidPairPath -Raw | ConvertFrom-Json
    $invalidPair.invocation_manifest_sha256[1] = Get-FileSha256 $invalidInvocationPath
    Write-JsonFile $invalidPair $invalidPairPath
    $invalidAcceptancePath = Join-Path $invalidInvocationEvidence 'acceptance-manifest.json'
    $invalidAcceptance = Get-Content -LiteralPath $invalidAcceptancePath -Raw | ConvertFrom-Json
    $invalidAcceptance.pair_manifest_sha256 = Get-FileSha256 $invalidPairPath
    Write-JsonFile $invalidAcceptance $invalidAcceptancePath
    Assert-Rejected {
        Read-BaselineProfileAcceptanceEvidence `
            -AcceptanceManifestPath $invalidAcceptancePath `
            -ExpectedAcceptanceManifestSha256 (Get-FileSha256 $invalidAcceptancePath) `
            -ExpectedSourceRevision $invalidPair.source_revision `
            -ExpectedAppApkSha256 $invalidPair.app_apk_sha256 `
            -ExpectedTestApkSha256 $invalidPair.test_apk_sha256 `
            -ExpectedPairManifestSha256 (Get-FileSha256 $invalidPairPath) `
            -ExpectedCanonicalSha256 $invalidAcceptance.canonical_sha256
    } 'Invocation 2 status differs'

    $escapePathEvidence = Join-Path $temporaryRoot 'escape-path-evidence'
    Copy-Item -LiteralPath $successEvidence -Destination $escapePathEvidence -Recurse
    $escapeInvocationPath = Join-Path $escapePathEvidence 'invocation-1/manifest.json'
    $escapeInvocation = Get-Content -LiteralPath $escapeInvocationPath -Raw | ConvertFrom-Json
    $escapeInvocation.retained_files += [pscustomobject]@{
        path = '../outside-evidence.txt'
        byte_count = 1
        sha256 = '0' * 64
    }
    Write-JsonFile $escapeInvocation $escapeInvocationPath
    $escapeChain = Update-FixtureAcceptanceChain $escapePathEvidence 1
    Assert-Rejected {
        Read-BaselineProfileAcceptanceEvidence `
            -AcceptanceManifestPath $escapeChain.AcceptancePath `
            -ExpectedAcceptanceManifestSha256 $escapeChain.AcceptanceSha256 `
            -ExpectedSourceRevision $escapeChain.Pair.source_revision `
            -ExpectedAppApkSha256 $escapeChain.Pair.app_apk_sha256 `
            -ExpectedTestApkSha256 $escapeChain.Pair.test_apk_sha256 `
            -ExpectedPairManifestSha256 $escapeChain.PairSha256 `
            -ExpectedCanonicalSha256 $escapeChain.Acceptance.canonical_sha256
    } 'escapes its invocation directory'

    $exitCodeEvidence = Join-Path $temporaryRoot 'exit-code-evidence'
    Copy-Item -LiteralPath $successEvidence -Destination $exitCodeEvidence -Recurse
    $exitCodeRelativePath = 'producer-results/test-result-exit-code.txt'
    $exitCodePath = Join-Path (Join-Path $exitCodeEvidence 'invocation-1') $exitCodeRelativePath
    [System.IO.File]::WriteAllBytes($exitCodePath, [byte[]]@([byte][char]'1'))
    $exitInvocationPath = Join-Path $exitCodeEvidence 'invocation-1/manifest.json'
    $exitInvocation = Get-Content -LiteralPath $exitInvocationPath -Raw | ConvertFrom-Json
    $exitRecord = @($exitInvocation.retained_files | Where-Object { $_.path -eq $exitCodeRelativePath })[0]
    $exitRecord.byte_count = 1
    $exitRecord.sha256 = Get-FileSha256 $exitCodePath
    Write-JsonFile $exitInvocation $exitInvocationPath
    $exitChain = Update-FixtureAcceptanceChain $exitCodeEvidence 1
    Assert-Rejected {
        Read-BaselineProfileAcceptanceEvidence `
            -AcceptanceManifestPath $exitChain.AcceptancePath `
            -ExpectedAcceptanceManifestSha256 $exitChain.AcceptanceSha256 `
            -ExpectedSourceRevision $exitChain.Pair.source_revision `
            -ExpectedAppApkSha256 $exitChain.Pair.app_apk_sha256 `
            -ExpectedTestApkSha256 $exitChain.Pair.test_apk_sha256 `
            -ExpectedPairManifestSha256 $exitChain.PairSha256 `
            -ExpectedCanonicalSha256 $exitChain.Acceptance.canonical_sha256
    } 'producer test exit code must be exact one-byte ASCII 0'

    $timeoutRangeEvidence = Join-Path $temporaryRoot 'timeout-range-evidence'
    Copy-Item -LiteralPath $successEvidence -Destination $timeoutRangeEvidence -Recurse
    $timeoutRangeAcceptancePath = Join-Path $timeoutRangeEvidence 'acceptance-manifest.json'
    $timeoutRangeAcceptance = Get-Content -LiteralPath $timeoutRangeAcceptancePath -Raw | ConvertFrom-Json
    $timeoutRangeAcceptance.producer_timeout_seconds = 1801
    Write-JsonFile $timeoutRangeAcceptance $timeoutRangeAcceptancePath
    $timeoutRangePair = Get-Content -LiteralPath (Join-Path $timeoutRangeEvidence 'pair-manifest.json') `
        -Raw | ConvertFrom-Json
    Assert-Rejected {
        Read-BaselineProfileAcceptanceEvidence `
            -AcceptanceManifestPath $timeoutRangeAcceptancePath `
            -ExpectedAcceptanceManifestSha256 (Get-FileSha256 $timeoutRangeAcceptancePath) `
            -ExpectedSourceRevision $timeoutRangePair.source_revision `
            -ExpectedAppApkSha256 $timeoutRangePair.app_apk_sha256 `
            -ExpectedTestApkSha256 $timeoutRangePair.test_apk_sha256 `
            -ExpectedPairManifestSha256 (Get-FileSha256 (Join-Path $timeoutRangeEvidence `
                        'pair-manifest.json')) `
            -ExpectedCanonicalSha256 $timeoutRangeAcceptance.canonical_sha256
    } 'must be positive and no greater than 1800 seconds'

    $timeoutMismatchEvidence = Join-Path $temporaryRoot 'timeout-mismatch-evidence'
    Copy-Item -LiteralPath $successEvidence -Destination $timeoutMismatchEvidence -Recurse
    $timeoutMismatchPairPath = Join-Path $timeoutMismatchEvidence 'pair-manifest.json'
    $timeoutMismatchPair = Get-Content -LiteralPath $timeoutMismatchPairPath -Raw | ConvertFrom-Json
    $timeoutMismatchPair.producer_timeout_seconds = 1799
    Write-JsonFile $timeoutMismatchPair $timeoutMismatchPairPath
    $timeoutMismatchAcceptancePath = Join-Path $timeoutMismatchEvidence 'acceptance-manifest.json'
    $timeoutMismatchAcceptance = Get-Content -LiteralPath $timeoutMismatchAcceptancePath -Raw | ConvertFrom-Json
    $timeoutMismatchAcceptance.pair_manifest_sha256 = Get-FileSha256 $timeoutMismatchPairPath
    Write-JsonFile $timeoutMismatchAcceptance $timeoutMismatchAcceptancePath
    Assert-Rejected {
        Read-BaselineProfileAcceptanceEvidence `
            -AcceptanceManifestPath $timeoutMismatchAcceptancePath `
            -ExpectedAcceptanceManifestSha256 (Get-FileSha256 $timeoutMismatchAcceptancePath) `
            -ExpectedSourceRevision $timeoutMismatchPair.source_revision `
            -ExpectedAppApkSha256 $timeoutMismatchPair.app_apk_sha256 `
            -ExpectedTestApkSha256 $timeoutMismatchPair.test_apk_sha256 `
            -ExpectedPairManifestSha256 (Get-FileSha256 $timeoutMismatchPairPath) `
            -ExpectedCanonicalSha256 $timeoutMismatchAcceptance.canonical_sha256
    } 'Pair producer timeout differs'

    if (-not [string]::IsNullOrWhiteSpace($RetainedGradleLogPath)) {
        $resolvedRetainedLog = (Resolve-Path -LiteralPath $RetainedGradleLogPath).Path
        $retainedLines = @(Get-Content -LiteralPath $resolvedRetainedLog)
        if (@($retainedLines | Where-Object { $_ -eq '' }).Count -eq 0) {
            throw 'The retained Gradle regression log must contain at least one empty line.'
        }
        $retainedRepository = New-OrchestrationFixtureRepository -Name 'retained-log-success'
        $retainedInvoker = New-OrchestrationFixtureInvoker -RepositoryRoot $retainedRepository.Root `
            -EvidenceIdentity 'retained-log-success' -Mode 'retained-log-success' `
            -FirstContent $orchestrationContent -SecondContent $orchestrationContent `
            -RetainedLogPath $resolvedRetainedLog
        Invoke-BaselineProfileEvidenceGeneration -RepositoryRoot $retainedRepository.Root `
            -EvidenceIdentity 'retained-log-success' -GradleInvoker $retainedInvoker.Invoker | Out-Null
        Assert-Equal 2 $retainedInvoker.State.GenerationCount `
            'The retained-log regression must complete exactly two fixture producer invocations.'
        Assert-Equal $true $retainedInvoker.State.FrozenInputsSeenBeforeSecond `
            'The retained-log pair must restore frozen tracked profile inputs before invocation 2.'
        Assert-Equal $true $retainedInvoker.State.FirstOutputDifferedFromFrozenInput `
            'The retained-log ordering proof must be nonvacuous.'
        $retainedBytes = [System.Convert]::ToBase64String(
            [System.IO.File]::ReadAllBytes($resolvedRetainedLog)
        )
        foreach ($ordinal in 1..2) {
            $copiedLog = Join-Path $retainedRepository.Root `
                "build/reports/baseline-profile-generation/retained-log-success/invocation-$ordinal/gradle-output.log"
            Assert-Equal $retainedBytes ([System.Convert]::ToBase64String(
                    [System.IO.File]::ReadAllBytes($copiedLog)
                )) "Invocation $ordinal must retain the exact Gradle log bytes including empty lines."
            $retainedManifest = Get-Content -LiteralPath (Join-Path $retainedRepository.Root `
                    "build/reports/baseline-profile-generation/retained-log-success/invocation-$ordinal/manifest.json") `
                -Raw | ConvertFrom-Json
            Assert-Equal 'valid' $retainedManifest.status `
                "Invocation $ordinal must accept the retained blank-line Gradle output."
            Assert-Equal 'EXECUTED' $retainedManifest.task_outcomes.PSObject.Properties[$script:ProducerTask].Value `
                "Invocation $ordinal must parse the executed producer task from the retained log."
        }
    }

    foreach ($invalidCase in @(
            @('invalid-utf8', 'producer freshness:'),
            @('missing-output', 'producer freshness:'),
            @('pull-failure', 'producer freshness:'),
            @('nonzero-exit', 'producer freshness:'),
            @('missing-apk', 'app APK identity:'),
            @('task-parsing-failure', 'task outcome parsing:')
        )) {
        $caseName = $invalidCase[0]
        $invalidRepository = New-OrchestrationFixtureRepository -Name $caseName
        $invalidInvoker = New-OrchestrationFixtureInvoker -RepositoryRoot $invalidRepository.Root `
            -EvidenceIdentity $caseName -Mode $caseName -FirstContent $orchestrationContent `
            -SecondContent $orchestrationContent
        if ($caseName -eq 'task-parsing-failure') {
            $originalTaskParser = (Get-Item Function:Get-GradleTaskOutcomes).ScriptBlock
            Set-Item Function:Get-GradleTaskOutcomes -Value {
                throw 'synthetic task parser exception'
            }
            try {
                Assert-InvalidInvocationManifest -Repository $invalidRepository -Invoker $invalidInvoker `
                    -EvidenceIdentity $caseName -ExpectedFailure $invalidCase[1]
            }
            finally {
                Set-Item Function:Get-GradleTaskOutcomes -Value $originalTaskParser
            }
        } else {
            Assert-InvalidInvocationManifest -Repository $invalidRepository -Invoker $invalidInvoker `
                -EvidenceIdentity $caseName -ExpectedFailure $invalidCase[1]
        }
    }

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

    if (-not [string]::IsNullOrWhiteSpace($RetainedAcceptedEvidencePath)) {
        foreach ($required in @(
                $RetainedAcceptanceManifestSha256,
                $RetainedSourceRevision,
                $RetainedAppApkSha256,
                $RetainedTestApkSha256,
                $RetainedPairManifestSha256,
                $RetainedCanonicalSha256
            )) {
            if ([string]::IsNullOrWhiteSpace($required)) {
                throw 'All retained accepted-evidence identities are required together.'
            }
        }
        Read-BaselineProfileAcceptanceEvidence `
            -AcceptanceManifestPath (Join-Path $RetainedAcceptedEvidencePath 'acceptance-manifest.json') `
            -ExpectedAcceptanceManifestSha256 $RetainedAcceptanceManifestSha256 `
            -ExpectedSourceRevision $RetainedSourceRevision `
            -ExpectedAppApkSha256 $RetainedAppApkSha256 `
            -ExpectedTestApkSha256 $RetainedTestApkSha256 `
            -ExpectedPairManifestSha256 $RetainedPairManifestSha256 `
            -ExpectedCanonicalSha256 $RetainedCanonicalSha256 | Out-Null
    }

    Write-Output 'BASELINE_PROFILE_EVIDENCE_VALIDATION=pass'
    Write-Output (
        'CASES=root-exited-child-job-timeout,termination-failure-restoration-block,' +
        'native-success,lf-crlf,bare-cr,overwrite,preexisting-retention,' +
        'failure-restore,failed-with-stale-source,' +
        'stale,cached-producer,pull-failure,' +
        'fresh-match,stale-source,flag-drift,apk-drift,' +
        'blank-output-binding,retained-raw-log-pair,' +
        'invalid-manifest-utf8,invalid-manifest-missing-output,' +
        'invalid-manifest-pull,invalid-manifest-nonzero,' +
        'invalid-manifest-apk-hash,invalid-manifest-task-parsing,' +
        'orchestration-success,orchestration-mismatch,orchestration-launch-failure,' +
        'orchestration-final-validation-failure,reader-before-acceptance-publication,' +
        'retained-file-tamper,retained-path-escape,retained-exit-code,' +
        'timeout-range,timeout-record-mismatch,' +
        'retained-accepted-evidence,manifest-before-next-run,' +
        'no-acceptance-after-failure'
    )
}
finally {
    $resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)
    $resolvedSystemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTemporaryRoot.StartsWith($resolvedSystemTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

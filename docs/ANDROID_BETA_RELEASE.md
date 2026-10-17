# Android beta release procedure

Status: normative technical procedure under [ADR 0047](adr/0047-beta-release-signing-provenance.md).
Updated: 2026-10-17. This procedure is not a release result or execution authorization.

## Current readiness and roles

hide confirmed on 2026-10-17 at 21:59 JST that no release key exists and external APK distribution
will be the first. The selected initial beta is recorded in the version contract below; no tag
is created here. Actual new-key creation/provisioning, the expected public certificate fingerprint,
exact software-license terms and dependency/asset notices remain unresolved execution
preconditions. They block distributable-artifact production, not this documentation merge.
#233 remains the preparation Issue; #234 owns final artifact acceptance.

| Role | Existing behavior | Permitted interpretation |
| --- | --- | --- |
| release | Optimized, unsigned Gradle output | Sole shipping build input; externally sign and verify before final acceptance |
| benchmarkRelease | Optimized, profileable, debug-signed derivative | Device/performance proxy only; never relabel as beta |
| nonMinifiedRelease | Unoptimized Baseline Profile producer | Explicit producer operation only; never shipping beta |

Use [ADR 0001](adr/0001-initial-build-toolchain.md),
[ADR 0010](adr/0010-generated-critical-journey-baseline-profile.md),
[ADR 0013](adr/0013-optimized-shipping-release.md) and the
[setup](DEVELOPMENT_SETUP.md) without another signingConfig, flavor or optimizer. This slice
implements no additional wrapper; future focused tooling must keep the same route.
At this decision the pinned inputs are JDK 21, Gradle 9.7.1, AGP 9.4.0, Kotlin 2.4.20,
Compose BOM 2026.09.00, Build Tools 36.0.0, compile/target SDK 37 and minSdk 26.
The version catalog, wrapper checksum, dependency locks and verification metadata are authoritative;
future changes follow their focused decision process, not a release-local override.

## Candidate preparation and final readiness

An immutable untagged candidate may supply #145/#231 with source/tree, role, exact APK/test-APK,
signer, version, toolchain/dependencies/Profile and fixture/harness identities. Mark it
candidate=true and distributable=false. Follow the owning protocol's already admitted role and
signer; this runbook does not authorize device operations, collection or production-key use.

Before producing a final tagged beta, #234 records these readiness conditions:

- Required [beta matrix](BETA_ACCEPTANCE_MATRIX.md) functional, data-integrity and performance
  results are accepted or reusable with their original identities and a relevant-input comparison.
- Known limitations and release notes are fixed;
  [privacy/data handling](PRIVACY_AND_DATA_HANDLING.md) and adopted software license/credits
  plus third-party obligations are available. Licensing permission is not granted by this runbook.
- The new-key custodian and authorized generation/safe signing provision, expected public
  certificate fingerprint and reflection of the selected initial version in source are accepted.
- The selected checked-main source/tree and tag, required CI/merge association, finite command
  plan, fresh lab evidence identity and final artifact checks are fixed before production.

Then produce and verify the exact artifact below. #234 completes the artifact-dependent checks
after the APK exists, reviews any affected evidence invalidation, and records its final verdict.
Readiness itself is not beta acceptance. External distribution needs its separate accepted action;
Play Store production launch remains outside this scope.

## Version, tag and update contract

Read committed versionCode and versionName from app/android/build.gradle.kts and compare them
with the APK manifest. They are the only build-version authority. The design owner selects
versionCode 1, versionName 0.1.0-beta.1 and tag v0.1.0-beta.1 for the first external distribution.
The build file still says versionName 0.1.0; a later focused source-version change and verification
must reflect the selection before production. This slice changes no build value. Every later distributed
release increases versionCode. The final tag is `v<versionName>`, points exactly to the selected
checked-main commit and is retained with that commit/tree. Do not synthesize versions from a
timestamp, dirty git-describe, CI run number or local property.

Record the successful required PR quality run and its actual head/base/tree, then associate
that result with the merged commit/tree. The canonical CI command is check plus assembleDebug;
it proves neither release assembly nor signatures. Do not relabel a PR result as a new run at
the eventual tag/merge SHA. Tagging or adding prose does not alone invalidate equivalent evidence.

Keep applicationId io.github.hideyukimori.nenepixel. A higher versionCode does not overcome
different debug/release signers. Any install/update requires the applicable
[data-preservation protocol](quality/DEVICE_PRIVATE_PRESERVATION.md); no automatic uninstall,
data wipe, alternate application ID or key rotation bypass is part of this procedure.
[Android signing](https://developer.android.com/studio/publish/app-signing) describes signer
continuity; [versioning](https://developer.android.com/studio/publish/versioning) describes
increasing released versionCode values.

## Ordered artifact production

### 1. Admit the exact input and fresh evidence directory

Use a focused D-drive lab copy at the fixed checked-main commit, with no tracked/untracked source
changes. Record tag resolution, commit/tree, source manifest, version, toolchain/SDK binary hashes,
catalog/lock/verification-metadata hashes, accepted Profile provenance and required CI/merge link.
Resolve the lab through Get-NenePixelLabRoot; never introduce a tracked absolute lab path.
Refuse an existing evidence identity. Keep build/signing stages and all failed attempts distinct.

The current Profile is #190's adopted invocation, canonical SHA-256
088ebdc684d0fa48a5a77023e402ba513f50c4d575f5479af5054c72021232fc.
Bind the current committed profile and its adoption record rather than substituting historic P62.
Ordinary release packaging does not authorize regeneration; future needed generation remains
subject to ADR 0010 and the live phase-gate plan.

### 2. Build and retain the unsigned release

The following Windows template shows existing APIs, not an implemented release wrapper.
The execution plan supplies repositoryRoot, evidenceId and a pre-admitted fresh evidence path.
No signing credentials may be present in the Gradle process environment.

```powershell
. (Join-Path $repositoryRoot 'docs/quality/measurements/nene-pixel-lab.ps1')
. (Join-Path $repositoryRoot 'docs/quality/bounded-native-command.ps1')
$labRoot = Get-NenePixelLabRoot -StartDirectory $repositoryRoot
$evidence = Join-Path $labRoot ('outputs/issue-233/' + $evidenceId)
if (Test-Path -LiteralPath $evidence) { throw 'Evidence identity already exists.' }
[void][IO.Directory]::CreateDirectory($evidence)
$build = Invoke-BoundedNativeCommand -RepositoryRoot $repositoryRoot `
    -LogPath (Join-Path $evidence 'assemble-release.log') `
    -ExecutablePath (Join-Path $repositoryRoot 'gradlew.bat') `
    -NativeArguments @(':app:android:assembleRelease') -TimeoutSeconds 900
if ($build.ExitCode -ne 0) { throw 'Unsigned release build failed.' }
```

900 seconds is this procedure's maximum build attempt; a timeout remains failed evidence.
Keep normal cache/daemon defaults. Do not add clean, forced reruns, cache disabling, automatic
device tests or Profile collection. Do not duplicate the successful required full CI gate locally.
After timeout or uncertain termination, retain the work copy and partial outputs without hashing,
copying or reusing them until the actual Gradle writer is confirmed quiescent. An empty launcher
job alone does not prove a reused external Gradle daemon stopped writing. Do not kill a shared
daemon to bypass that admission; resolve the owned execution state before any new attempt.

Identify the single release APK from AGP's output metadata and verify its role, source revision,
applicationId, manifest version and SDK values. Refuse ambiguous or mismatched output. Copy it
without altering bytes to the fresh evidence directory and record its SHA-256 before signing.
Keep the raw build log and exact R8 mapping, configuration, usage, seeds, merged keep-rule inputs,
mapping ID and retrace-tool identity required by ADR 0013. Prove a controlled retrace round trip;
missing required evidence blocks acceptance rather than being silently omitted.

### 3. Check alignment before signing

Bind zipalign/apksigner from the canonical Build Tools version and record their exact identities.
AGP normally aligns its output, so check instead of rewriting a successful artifact.
The plan supplies zipalignPath and the retained unsignedApk path.

```powershell
$alignment = Invoke-BoundedNativeCommand -RepositoryRoot $repositoryRoot `
    -LogPath (Join-Path $evidence 'unsigned-alignment.log') `
    -ExecutablePath $zipalignPath `
    -NativeArguments @('-c', '-P', '16', '-v', '4', $unsignedApk) -TimeoutSeconds 60
if ($alignment.ExitCode -ne 0) { throw 'Unsigned APK alignment failed.' }
```

An alignment failure stops this attempt. Any necessary packaging correction is declared before
a new attempt; do not mutate a signed APK. These options follow the SDK
[zipalign reference](https://developer.android.com/tools/zipalign).

### 4. Custodian-controlled external signing

For first use, hide is the selected custodian of a password-protected portable keystore. Retain
a keystore backup and an independently recoverable password record separately. Actual locations,
safe password input and a backup/restore plan must be admitted before the separately authorized
new-key creation and first signing; this slice creates no key or backup. No password manager or
OS secret-store integration is adopted. Same-user DPAPI may protect a local copy, but is not by
itself a portable backup: Windows credential Export-Clixml is tied to the originating user and
computer, as described by [Microsoft](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.utility/export-clixml).

Only after Gradle has ended may the accepted custodian supply the separately authorized new
keystore and alias to the signing invocation. Supply passwords solely to that isolated child
process's environment: never the user/machine environment, a calling shell's inherited environment
or the Gradle parent. No key exists yet; this slice performs no credential search, key generation,
export or upload.

Bound the attempt and termination of its owned process before execution. The signing plan must
state its finite deadline, safe password-input method, nonlogging invocation, timeout cleanup and
public success/failure record. An owner-controlled isolated process with an explicit finite wait
and owned-process termination is sufficient; a new wrapper is not required. This documentation
implements no signer invoker. If safe operational provision cannot be admitted, stop before signing.

Do not call Invoke-BoundedNativeCommand for signing: it serializes arguments and captures complete
output. Keystore paths, passwords and secret-bearing signer diagnostics must never enter its
generic logger or a shell transcript. Disabling a transcript alone does not establish that other
shell/process logging is safe. Retain only approved public signing facts and an outcome, with
private custodian records held separately. On timeout/uncertain termination retain the failed
attempt and do not inspect, overwrite or reuse a possibly changing signed output.

Custodian command template, not a command to paste with unresolved placeholders:

```text
apksigner sign --ks <custodian-private-keystore> --ks-key-alias <accepted-alias>
  --ks-pass env:<store-password-variable> --key-pass env:<key-password-variable>
  --min-sdk-version 26 --v1-signing-enabled false --v2-signing-enabled true
  --v3-signing-enabled true --v4-signing-enabled false
  --out <fresh-signed-output.apk> <retained-unsigned-release.apk>
```

The isolated process plan selects the child-only environment-variable names; no repository signing
env/property loader is defined. Never substitute literal passwords, Gradle properties, private
paths in tracked files or secrets in a recorded command. The SDK's env: password format avoids
putting password values in signer arguments; it does not alone make an execution environment safe.
Keep the original unsigned APK and distinct signed output. The
[apksigner reference](https://developer.android.com/tools/apksigner) defines these options.

### 5. Verify the exact signed output

Use the existing bounded helper for nonsecret verification, with accepted apksignerPath/signedApk
and fresh log paths. The verification command needs no keystore or password.

```powershell
$signature = Invoke-BoundedNativeCommand -RepositoryRoot $repositoryRoot `
    -LogPath (Join-Path $evidence 'signed-verification.log') `
    -ExecutablePath $apksignerPath `
    -NativeArguments @('verify', '--verbose', '--print-certs', '-Werr',
        '--min-sdk-version', '26', $signedApk) -TimeoutSeconds 60
if ($signature.ExitCode -ne 0) { throw 'Signed APK verification failed.' }
$signedAlignment = Invoke-BoundedNativeCommand -RepositoryRoot $repositoryRoot `
    -LogPath (Join-Path $evidence 'signed-alignment.log') `
    -ExecutablePath $zipalignPath `
    -NativeArguments @('-c', '-P', '16', '-v', '4', $signedApk) -TimeoutSeconds 60
if ($signedAlignment.ExitCode -ne 0) { throw 'Signed APK alignment failed.' }
```

For this minSdk 26 APK-only route, v2 supplies the compatibility floor and v3 remains enabled;
v1 is disabled and v4 is explicitly disabled to avoid an implicit .idsig sidecar. Require successful
v2/v3 verification, the selected v1/v4 exclusions and the independently accepted public
certificate SHA-256 fingerprint. minSdk 26 is the existing compatibility floor; targetSdk 37
is not a maximum SDK and is not a reason to narrow the verifier's upper bound. Record actual
tool behavior and public certificate identity; do not treat exit zero alone as a fingerprint match.

Verify the manifest has the accepted version/application ID/SDKs and shipping debuggable/profileable
state. Match META-INF/version-control-info.textproto's exact revision to the actual build commit.
Verify assets/dexopt/baseline.prof and baseline.profm, their hashes, and the compiled profile's
size against ADR 0010's below-1.5-MB recommendation. Retain the Profile provenance and source hash.

Compare DEX/resources/profile entries between retained unsigned and signed APKs; enumerate expected
signature/container metadata differences. When reusing benchmark-proxy device results, separately
prove shipping/proxy payload equivalence with expected manifest/profileable/signature/container
differences and their original source/artifact/environment inputs. Existing artifact parsing in
[P4 preflight](quality/measurements/p4-indexed-preflight.ps1) can inform the check, but its measurement
role/protocol acceptance is not a general release verdict or a new release wrapper.

Record final APK SHA-256 after all checks. Do not change APK bytes afterward:
[apksigner](https://developer.android.com/tools/apksigner) signatures are invalidated by mutation.
Reproduction claims compare declared inputs and exact relevant payloads; no bit-identical
independently signed APK claim is made without a measured reproduction proof.

### 6. Complete the release record and final decision

| Evidence group | Required retained identity/result |
| --- | --- |
| Source and gate | Exact commit/tree/tag, clean-source observation, required CI actual inputs/run/result and merged-tree association |
| Build inputs | Wrapper/JDK/SDK/AGP/Kotlin/catalog, locks, verification metadata, role and bounded command/log identities |
| Artifacts | Original unsigned and final signed APK, applicable test APKs, SHA-256, embedded revision, manifest/version/SDK values |
| Signing | Accepted public certificate fingerprint, verified schemes, signer/zipalign tool identities and nonsecret outcome |
| Profile and optimizer | Accepted source Profile provenance/hash, both packaged assets/hash/size, R8 outputs, mapping ID and retrace round-trip proof |
| Reuse | Original result source/artifact/role/environment/harness/fixture/protocol/raw identities and explicit affected-input comparison |
| User-facing material | Release notes/known limits, adopted software license/credits and third-party notices, privacy reference, exact source link |
| Decision and failures | Readiness record, retained FAIL/invalid lineage, exact-artifact #234 verdict and separate distribution authorization |

Publish no private key, password, actual credential/machine path or unapproved private custodian
record. Public manifests use stable relative artifact references. Preserve original FAIL/invalid
results; an unchanged retry that later passes is instability under QLT-017, not accepted evidence.
Keep durable evidence outside disposable build/worktree directories. Cleanup follows
[setup](DEVELOPMENT_SETUP.md#keep-development-folders-in-the-development-lab) after integration
and preservation.

## Verification scope of this documentation slice

Static diff/link review and validateDocumentation check these documents and references.
Required final non-draft quality CI remains canonical check plus assembleDebug. No release build,
app/device test, signing operation or Profile regeneration is required merely to merge this prose.
A later executable signer/tool change needs a focused Issue and short relevant tool-contract checks.
Missing owner inputs remain visible in #233; no checklist here silently closes #233 or #234.

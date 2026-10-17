# ADR 0047: Beta release signing and provenance

- Status: accepted
- Date: 2026-10-17
- Issue: #233
- Affected rules: `ARC-001`, `ARC-012`, `QLT-001`, `QLT-003`, `QLT-004`,
  `QLT-011`, `QLT-012`, `QLT-015`, `QLT-017`, `QLT-018`, `QLT-019`

## Context

Main 9612c1371826fe1efb3fdd22c4693dcd18e595a1 has one accepted optimized shipping
release under [ADR 0013](0013-optimized-shipping-release.md), but that output is unsigned.
The optimized, profileable, debug-signed benchmarkRelease is a device proxy; the unoptimized
nonMinifiedRelease is the Baseline Profile producer. Neither is the distributable beta.
At that audited main, the app declared versionCode 1 and versionName 0.1.0. On 2026-10-17
at 21:59 JST, hide confirmed that no release key exists and external APK distribution will be
the first. The design owner selected the initial beta values below; the technical-procedure
slice left the build file unchanged.

Live #233 requires one signing/version/provenance procedure before #234 can verify a tagged
checked-main beta. The repository defines no release signingConfig or signing-secret loader.
The generic bounded native-command helper records arguments and complete output, so passing a
keystore path or secret-bearing signer invocation through it would violate the custody boundary.

At the initial technical-procedure decision, actual key custody/provisioning, the expected public
certificate fingerprint and exact software-license terms remained unresolved execution
preconditions. That decision did not accept those unresolved inputs or assert that an APK was
built, signed, tagged, verified or distributed. The dated first-party adoption below records the
later partial resolution.

## Decision

### One build and signing route

Keep the existing optimized unsigned :app:android:assembleRelease as the sole shipping build.
After artifact retention and alignment verification, sign that exact input externally with the
canonical SDK apksigner. Verify the signed output before any release decision. Do not add a
parallel Gradle signingConfig, build flavor, optimizer, dependency or plugin. This slice implements
no additional release wrapper; any later focused tooling must preserve this single route.
The [Android beta runbook](../ANDROID_BETA_RELEASE.md) owns the ordered procedure and checklist.

Use the pinned toolchain from [ADR 0001](0001-initial-build-toolchain.md), committed dependency
locks/verification metadata, and the accepted generated Profile from
[ADR 0010](0010-generated-critical-journey-baseline-profile.md). At this decision the Profile is
#190's adopted invocation, canonical SHA-256
088ebdc684d0fa48a5a77023e402ba513f50c4d575f5479af5054c72021232fc.
Historic P62 acceptance is not the current Profile identity. Ordinary builds must not generate
a Profile; a future necessary collection follows its owning Issue and phase-gate plan.

AGP supplies APK alignment; SDK zipalign checks it without rewriting a successful output.
External signing selects v1=false, v2=true, v3=true and v4=false. The existing minSdk 26 can use v2;
v3 remains enabled, while the APK-only route neither needs v1 nor creates a v4 .idsig sidecar.
SDK verification must prove the selected schemes and accepted public signer identity. Target SDK 37 is not a maximum
supported SDK and must not be used to narrow verification to hide an incompatible signature.
Keep unsigned and signed APK identities distinct; never alter the signed APK afterward.

### Custody and execution readiness

Keep keys, passwords and actual credential paths outside Git, generic invocation logs, manifests,
chat and published artifacts. No key search, generation, export, rotation or CI credential upload
is authorized here. The custodian supplies passwords solely to the isolated signing process's
environment, after Gradle has finished. Never place them in the user/machine environment, the
calling shell's inherited environment or the Gradle parent process.

apksigner supports environment-variable password references. These are signer options, not an
implemented repository env/property contract. A selected execution plan must bind the custodian's
provisioning method and finite secret-safe process/termination boundary before signing. The
existing generic logger is unsuitable for signing. An owner-controlled isolated process with a
finite wait and owned-process termination is sufficient; a new wrapper is not required. No signer
wrapper is implemented or claimed here. Absent an admitted safe boundary, stop before signing.

Missing new-key custody/provisioning, public fingerprint, adopted license/credits or
dependency/asset notices blocks final distributable-artifact production. It does not block
merging this procedure. Owner-approved licensing must preserve the confirmed distinction between
paid creative work/artwork sales and monetizing the application/code; this ADR grants no license.

### First-party terms adoption — 2026-10-17

Following hide's confirmed credit identity and application-linked donation restriction, the
design owner adopts the first-party terms in root [LICENSE](../../LICENSE) and the software
credit in [CREDITS](../../CREDITS.md). LICENSE is the sole authority for those terms; this ADR
does not reproduce or extend them. Its scope is the licensor's held rights in the application
and code, with accompanying first-party materials permitted as needed as part of their ordinary
use, study, modification and free redistribution. It neither adds a standalone-material sales
prohibition nor grants a blanket license for independent materials. Third-party parts retain
their original grants and applicable notices.

This adoption resolves only the first-party terms and software-credit decision. No additional
external material was declared in hide's reply; this is not comprehensive rights clearance.
Third-party and embedded/vendor provenance, readable in-app notice delivery and exact final
optimized APK/native/resource coverage remain open, as do new-key custody/provisioning, backup
and restore readiness, the accepted public signing certificate and final beta artifact gates.
Repository terms and credits alone do not authorize or prove beta production or distribution.

### Version, tag and checked-main identity

app/android/build.gradle.kts remains the only build-version authority. The selected first beta is
versionCode 1, versionName 0.1.0-beta.1 and tag v0.1.0-beta.1, based on hide's first-distribution
confirmation. The subsequent focused source-version slice reflects those values in the build
file. Actual packaged version remains an artifact check; no tag or distribution is implied.
Every subsequent distributed version increases versionCode. A final beta tag is `v<versionName>`
and resolves exactly to the selected checked-main commit. No local property, CI number or
timestamp silently overrides version. The application ID stays unchanged.

Record the final PR's required quality CI and its source/head/base plus merged commit/tree
association. CI checks and assembles debug through the canonical gate; it is not release-signing
proof. Do not claim a fresh CI run on a tag or merge commit when the result ran on a different
identity. A later tag or report alone does not invalidate equivalent evidence under QLT-012.

### Candidate and final beta

#233 may prepare immutable, untagged measurement-candidate identities for #145/#231. Such a
candidate is explicitly nondistributable and is never renamed into the final beta. It may use
only an artifact role and signer already admitted by the owning protocol; this ADR does not
authorize a candidate install, collection or production-key use.

Before final artifact production, #234 establishes readiness: the required functional/performance
verdicts or valid reuse, known limits, custody/version/license decisions, checked-main source/tag
and the bounded production plan are ready. It then produces the tagged artifact and verifies
that exact signed output, completing the artifact-dependent checks afterward. Final artifact
verification is not a prerequisite that must already have passed before the artifact exists.
An artifact-only failure remains failure; readiness does not itself accept the beta.

### Evidence and update boundary

Retain exact APK/test-APK, source/tree, embedded revision, manifest/version, signature/public
certificate, DEX/resources/Profile, R8 mapping/configuration/usage/seeds/keep-rule inputs/mapping
ID and controlled retrace proof. Keep dependency/toolchain and Profile provenance with the raw
command results and relevant immutable acceptance records. Missing mandatory evidence blocks
acceptance. Do not claim bit-identical signed reproduction without an actual declared proof.

Reuse records keep their original identities and compare affected source, build configuration,
payload, role, signer and environment inputs. A shipping APK and benchmark proxy need explicit
payload equivalence with enumerated manifest/profileable/signature/container differences;
successful debug tests or assembly alone do not prove optimized-release behavior.

The unchanged application ID does not make debug and release signers update-compatible.
Installation/update work requires its own data-preservation admission. Do not uninstall, wipe
private data or change application ID to bypass a signature conflict. This procedure adopts no
key rotation or destructive migration route.

## Rejected alternatives

### Ship the benchmark proxy

Its debug signer and profileable measurement role differ from the shipping artifact. Renaming it
would lose the accepted role and provenance distinction in ADR 0013.

### Add Gradle signing beside external signing

That creates two signing routes and brings secrets into Gradle configuration/cache inputs.
The existing unsigned build followed by one external signing step keeps the boundary explicit.

### Supply passwords or keystore paths through the generic logger

The helper persists command arguments and raw output. A redaction promise after capture is
insufficient; secret-bearing signing never enters that logger.

### Infer owner decisions from source inspection

The initial beta selection follows the explicit owner fact and design decision above. Key custody
and exact license terms cannot be inferred from source inspection or silently accepted with it.

## Consequences

### Benefits

- One existing optimized build and one external signing step preserve role ownership.
- Public artifact attribution and private credential custody remain separate.
- Final beta acceptance follows exact tagged bytes, while valid earlier evidence remains reusable.

### Costs and risks

- The original decision left new-key custody/provisioning, the signer fingerprint and exact
  license terms unresolved. The dated first-party adoption resolves only its stated portion;
  third-party notices, final payload and credential preconditions remain open.
- The selected initial source version is reflected; actual packaged version still needs verification.
- Secret-safe bounded signing execution and final artifact evidence are not implemented by prose.
- Toolchain or payload changes may invalidate affected optimized-runtime/performance evidence.
- Signing-key continuity matters for updates; a higher versionCode cannot repair a signer mismatch.

## Enforcement impact

This slice changes documentation only. Validate links, rule/ADR shapes and scope; the final
non-draft PR still needs required canonical quality CI under QLT-011 through QLT-019.
No app test, build, device collection, signing or Profile generation is justified solely by
these documents. The runbook uses the existing bounded helper for nonsecret operations where
safe; an absent executable guard is an execution precondition, not a claimed implemented check.
Any later tooling change gets its own focused scope and narrow tool-contract verification.

## Migration and rollback

No APK, version, credential or runtime data migrates in this slice. Existing debug, benchmark
and producer roles remain intact. ADR 0013's unsigned output description remains true and links
to this final-signing procedure. A later procedure change updates this ADR/runbook together,
retains original evidence and preserves one active route. Do not keep competing signing paths.

## Related

- Issue: [#233](https://github.com/hideyukiMORI/NENE-PIXEL/issues/233)
- Final beta gate: [#234](https://github.com/hideyukiMORI/NENE-PIXEL/issues/234)
- [Android beta release runbook](../ANDROID_BETA_RELEASE.md)
- [Beta acceptance matrix](../BETA_ACCEPTANCE_MATRIX.md)
- Primary SDK references: [apksigner](https://developer.android.com/tools/apksigner),
  [zipalign](https://developer.android.com/tools/zipalign),
  [app signing](https://developer.android.com/studio/publish/app-signing),
  [versioning](https://developer.android.com/studio/publish/versioning)
- Supersedes: none
- Superseded by: none

# M3 PNG export evidence

Issue: #88 / P3-05. Decision: [ADR 0019](../adr/0019-exact-png-export.md).
Base: a04b41e7872899c02e1ce1ede54217281a1c439c. Waivers: none.

## Prospective scope and verification

Changed paths: application persistence/editor export coordination; adapter PNG encoder, shared
fresh-output transport and typed picker; Android composition/Activity Result; Compose action/status;
associated contracts and governing documentation. Project/recovery schema, drawing algorithms,
supported limits, dependencies and profile are unchanged.

Applicable iteration checks, preserving failures:

- `:core:application:test --tests '*PngExport*'` and affected persistence/autosave contracts.
- `:adapters:persistence:testDebugUnitTest --tests '*Png*'` and shared project transport contracts.
- Affected application/adapter/app/presentation compile, ktlint and detekt tasks.
- Relevant app/presentation host tests; `validateDocumentation validateArchitecture`.
- Filtered Android MIME/result/ContentResolver and Export PNG UI functional tests. Record selected
  device, class list and artifact identity before execution; no latency population is collected.

QLT-011 format evidence uses exact maximum retained encoded-payload count and a bounded ownership/copy
inventory beside boundary tests. It is not a total-heap or latency claim. No autosave measurement is
repeated: #87's permission was consumed. No benchmark, profile, forced build or local full suite.
Final required quality CI runs `./gradlew check :app:android:assembleDebug --stacktrace`.

## Selected Android functional execution

Device: USB iPlay80miniPro, T830128GB26321131293, Android 16 / API 36. Preflight confirms the
application package is absent; the prior #87 adapter-test package/private CSV remains present.
Both invocations set `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` so test output
and installed packages are retained. No uninstall, app-data clear, or measurement class is selected.

Prepare APKs with normal cached assembleDebug/assembleDebugAndroidTest tasks and record SHA-256 before
execution. Select only these functional methods/classes:

- Adapter: `ProjectPickerIntentsAndroidTest`,
  `AndroidPersistenceFunctionalTest#actualContentResolverWritesClosesReadsAndDecodes`,
  `AndroidPersistenceFunctionalTest#actualResolverExportsPngWithExactUnpremultipliedPixel`.
- App: `PngPickerCancellationTest`, `PngExportUiTest`,
  `EditorRuntimeLifecycleTest#configurationRecreationRetainsTheOnlyDocumentAndWorkspaceOwners`.

Each artifact's first invocation is preserved independently with raw XML/log output and source hashes.
A failure is investigated before any affected rerun, and its original evidence is retained. These
are functional tests; no timing samples or performance acceptance verdict is collected.

## Results

The first two connected-test invocations completed successfully, but XML enumeration showed only
the first class in each comma-separated selector ran: four picker-intent tests and one claimed-picker
cancellation test. Preserve those outputs under `build/reports/issue-88/device-01`. This is incomplete
selection evidence, not evidence for the other requested tests. Run the four remaining methods/classes
individually, retaining packages and archiving each output before the next invocation overwrites it.
No completed device test or autosave measurement is repeated for this correction.

The individual UI run failed: the test injected Back before DocumentsUI became the active window;
logcat shows the editor destroyed and DocumentsUI displayed afterward. Preserve `device-ui-02.log`
and `device-ui-02-raw`. Correct only the harness to wait for the resolved picker package's active
accessibility root before Back. Rebuild/hash only the changed app test APK, then run this corrected
case once; production and adapter APKs remain equivalent. Other outstanding selections are unchanged.

The first synchronization correction (`device-ui-03`) failed before clicking: package visibility
made app-context `resolveActivity` return null. Keep its XML/log and APK identity. Resolve the handler
through instrumentation's shell package command (verified on this device), then retain the same
active-window predicate. This is a second harness-only correction; run the corrected UI case once
with a separately hashed test APK. No production permission or dependency is added.

The final candidate passes the selected 89 host and 9 Android functional tests. Required merge-candidate
quality CI remains the separate pre-merge gate recorded on the linked PR; there is no local full run.

## Host and representation evidence

Final selected host contracts are 89 unique tests, all passing with zero failures/errors/skips:

| Boundary | Count | Raw evidence under `build/reports/issue-88/` |
| --- | --- | --- |
| PNG workflow | 7 | `png-workflow-final.xml`, `prepare-02.log` |
| Existing persistence / autosave workflow | 18 / 20 | `host-02-core/test-results`, `host-02.log` |
| PNG encoder / PNG adapter / shared project adapter | 5 / 5 / 6 | `adapter-01/test-results`, `adapter-01.log` |
| App picker / user-operation worker / autosave schedule | 4 / 3 / 16 | `narrow-final-01.log`, `app-host-final` XML |
| Presentation new-document / recovery-offer consumers | 2 / 3 | `narrow-final-01.log`, `presentation-host-final` XML |

Commands use the existing offline Gradle toolchain during iteration:

```powershell
.\gradlew :adapters:persistence:testDebugUnitTest --tests '*Png*' --tests '*AndroidProjectStorageAdapterTest' --offline
.\gradlew :core:application:test --tests '*PngExport*' --tests '*EditorPersistenceWorkflowTest' --tests '*EditorAutosaveWorkflowTest' :app:android:testDebugUnitTest --tests '*ProjectPickerBrokerTest' --tests '*EditorOperationWorkerTest' :app:android:compileDebugKotlin --offline
.\gradlew :app:android:testDebugUnitTest --tests '*ProjectPickerBrokerTest' --tests '*EditorOperationWorkerTest' --tests '*Autosave*Test' :presentation:compose:testDebugUnitTest --tests '*RecoveryOfferStatusTest' --tests '*NewDocumentEditorControllerTest' :adapters:persistence:compileDebugAndroidTestKotlin :app:android:compileDebugAndroidTestKotlin :core:application:ktlintCheck :adapters:persistence:ktlintCheck :app:android:ktlintCheck :presentation:compose:ktlintCheck :core:application:detekt :adapters:persistence:detekt :app:android:detekt :presentation:compose:detekt validateDocumentation --offline
.\gradlew :core:application:test --tests '*PngExportWorkflowTest' :app:android:assembleDebugAndroidTest --offline
```

The respective successful Gradle wall times are 15 s, 18 s, 23 s, and 11 s. These are verification
wall times, not operation latency. The final workflow rerun corrected test discovery: a non-Unit
expression-bodied JUnit method had silently omitted the stale-completion case. Its final assertion
returns Unit; XML now enumerates all seven tests. The previous six-test results remain preserved.

The committed PNG fixtures have independently constructed bytes and provenance under
`adapters/persistence/src/test/resources/png-v1/README.md`. ImageIO decodes every pixel, including
all 65,536 pixels at 256 x 256, and hidden RGB without premultiplication. Maximum retained `PngBytes`
payload is exactly **263,756 bytes** (minimum 86), asserted on the production carrier. This is
retained encoded-payload evidence, not a measured total heap, allocation rate, or speedup.

Bounded primitive storage inventory at the maximum input:

| Owner / copy | Bound in bytes | Lifetime / access |
| --- | --- | --- |
| Existing immutable pixel snapshot | 262,144 packed payload | Existing document owner; export only borrows snapshot |
| Encoder defensive packed copy | 262,144 | Private scanline preparation; no mutable pixel owner escapes |
| Filtered RGBA rows | 262,400 | Private filter-None input including one byte per row |
| zlib stored-block output | 263,686 | Private IDAT payload including per-row framing and Adler-32 |
| PNG container output | 263,756 | Private encoder ByteBuffer backing array |
| Immutable PNG carrier | 263,756 | Defensive input copy; only count / copy API |
| Prepared transport copy | 263,756 | One fresh-output operation, retained across picker wait |
| Read-back scratch / returned copy | 263,756 each | Existing bounded reader; max-plus-one probe allocates no larger array |

Small fixed headers/type arrays, object headers, coroutine/provider storage, and platform overhead
are outside these payload counts. Rows describe allocation sites and ownership, not simultaneous
liveness or total retained heap. No new pixel work surface, dependency, module, or profile; project/recovery schemas remain v1.

## Review and preserved corrections

Read-only independent review identified claimed-picker late-result reuse and missing Job retention
while waiting behind autosave. Both were corrected in the shared path with regression tests. The
review also required a format-neutral shared output outcome and distinct PNG failure projection;
those are implemented. Final review found no remaining design blocker. Self-review covers the
still-manual ARC-004/005, CMD-001/002, KOT-004/013/014/017 obligations without a waiver or suppression.

Keep `compile-01.log` (typed launcher consumer), `core-01.log` (cross-module property smart cast),
`format-01/02.log` and `static-01/02.log` (format/ADR line-ending corrections), and all original
host/device selection results. Final successful narrow checks supersede affected failures; they do
not relabel them. No canonical gate or failure threshold has been weakened.

## Final Android results and immutable artifacts

Nine unique selected functional tests pass with zero failures/errors/skips in final applicable XML:

| Selection | Tests | Final raw archive | Gradle wall time |
| --- | --- | --- | --- |
| ProjectPickerIntentsAndroidTest | 4 | `device-01/adapters-raw-complete` | 15 s |
| PngPickerCancellationTest | 1 | `device-01/app-raw-complete` | 15 s |
| Functional ContentResolver project write/close/read/decode | 1 | `device-project-04-raw` | 14 s |
| Functional ContentResolver exact low-alpha PNG | 1 | `device-png-05-raw` | 14 s |
| PngExportUiTest, corrected active-window synchronization | 1 | `device-ui-06-raw` | 16 s |
| EditorRuntimeLifecycleTest selected configuration recreation | 1 | `device-lifecycle-07-raw` | 19 s |

Each archive includes an original-path/SHA-256 manifest, XML, and available logcat/protobuf/report
files. The initial Windows long-path copy attempts (`adapter-results`, `adapter-raw`) are incomplete;
`adapters-raw-complete` and `app-raw-complete` are the complete preserved copies. The selector omission
and two UI harness FAILs remain in their original logs/archives; only the corrected UI case was rerun.

After the initial comma-list selection omission, each command used exactly one class or method:

```powershell
$env:ANDROID_SERIAL='T830128GB26321131293'
.\gradlew :adapters:persistence:connectedDebugAndroidTest --offline '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' '-Pandroid.testInstrumentationRunnerArguments.class=io.github.hideyukimori.nenepixel.adapters.persistence.AndroidPersistenceFunctionalTest#actualContentResolverWritesClosesReadsAndDecodes'
.\gradlew :adapters:persistence:connectedDebugAndroidTest --offline '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' '-Pandroid.testInstrumentationRunnerArguments.class=io.github.hideyukimori.nenepixel.adapters.persistence.AndroidPersistenceFunctionalTest#actualResolverExportsPngWithExactUnpremultipliedPixel'
.\gradlew :app:android:connectedDebugAndroidTest --offline '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' '-Pandroid.testInstrumentationRunnerArguments.class=io.github.hideyukimori.nenepixel.PngExportUiTest'
.\gradlew :app:android:connectedDebugAndroidTest --offline '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' '-Pandroid.testInstrumentationRunnerArguments.class=io.github.hideyukimori.nenepixel.EditorRuntimeLifecycleTest#configurationRecreationRetainsTheOnlyDocumentAndWorkspaceOwners'
```

Device fingerprint: `ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94111:user/release-keys`.
Initial conditions: USB powered, battery 61%, 28.7 C; functional lane only. Local toolchain: Windows,
JBR 21.0.11, Gradle 9.7.1, compile SDK 37 / build tools 36.0.0, debug variants. Catalog, locks,
verification metadata, Gradle/build conventions and Baseline Profile bytes are unchanged from base
`a04b41e7872899c02e1ce1ede54217281a1c439c` and included in the source manifests.

| Artifact | SHA-256 |
| --- | --- |
| Adapter test APK, all adapter functional runs | `b5f5d208fcdef2cac608c0bdabd0866d8d5c151a169328f2e3363155b7658633` |
| App debug APK, all app functional runs | `5fc5aa9e13dd9cef1d78952070acebe67a94e46bc1fadd5f6655e637c8ed05c4` |
| App test APK, initial claimed-picker PASS and first UI FAIL | `f724dfe62ec25793d86dd7f7859d603c92c62f16fe3456bfb2903d91933d80f1` |
| App test APK, package-visibility harness FAIL | `09f64d462582ffd4bf53d5a6c64b20f70d43ca356c189519fb2514d6fec91a64` |
| App test APK, final UI and lifecycle PASS | `1515ff9ec74eeba31321f9c1ad5cb35d4141643d1030e35c9f067f3f883cfd77` |

These were built from the uncommitted candidate above base a04b41e, not relabeled as a later commit.
Exact source files and APK copies are archived in `device-01`, `device-03`, and `device-06` before
execution. Their `source-sha256.json` hashes are respectively:

- `8ddc352f394e61006035f6eb2e2d41ddb5d09631d72e637e9bca54e997fcce9a`
- `701615f5b1b56e78b51cac53eb5d782a677fda24b6436d944582cee29527a777`
- `57609486c642690f50a7f6186c6d12f72e1b6381fedda6a257b2d286b43a07a1`

File-hash comparison confirms that original-to-final production/build/adapter-test inputs are
identical; only the app UI harness, host stale-completion test, and evidence/fixture documentation
changed. Later evidence prose does not invalidate those APKs. The selected claimed-picker test and
its inputs remain identical in the final app test artifact. `prepare-03.log` records final app/core
ktlint, detekt, app-test compile/assemble and documentation PASS (13 s); final documentation validation
covers this results update. Visual inspection of `ui-final.png` confirms the native portrait layout
shows Export PNG alongside the existing actions without clipping on this device.

The pre/post/final private #87 output hashes match: CSV
`aa8919cf60a04f834539e0b2426ee9b7d8e9b5c0d615768795a379b7141d938d`, status
`eebbf6457e46a7f63acdf9b97390f790ba443d60cfa44b607da7e5c40aa1cc1d`.
No #87 measurement, profile generation, forced cold build, package uninstall or app-data clear ran.

## Remaining risks and next gate

Read-back proves exact visible bytes after close, not future provider/cloud/hardware durability.
A provider may fail best-effort deletion and leave a newly created partial output; typed outcomes
retain that cleanup fact. A claimed picker keeps the single-operation lease until ActivityResult
drains. Stored DEFLATE produces larger files than adaptive compression within the fixed 263,756-byte
bound. This evidence does not claim total heap, latency, broad device coverage or completed M3 MVP
acceptance. Issue #89 owns the remaining integrated acceptance. Waivers: none.

# M3 Autosave State Identity and Evidence Correction

Issue: #87 / P3-04. Rules: `CMD-008`, `ARC-001`, `ARC-004`, `ARC-011`, `QLT-006`,
`QLT-011` through `QLT-016`. Decision: [ADR 0018](../adr/0018-bounded-autosave-debounce-contract.md).
Active waivers: none.

## Scope fixed before verification

Starting candidate: `806d22e57859088d70b237ad29c4df93e5976ba1`, based on main
`4798a65b922a7fb51f0798965772187806e0e778`. Review found revision-only autosave
suppression/completion despite the existing exact-history-position contract, and a measurement
runner that emitted its rows only after every operation succeeded.

Changed paths are autosave/persistence coordination and its contracts in `core/application`,
the scheduler and its consumers/tests in `app/android`, measurement-only instrumentation in
`adapters/persistence/src/androidTest`, and the governing documentation. Production file/recovery
bytes, pixel operations, dependencies, and module graph remain unchanged.

## Applicable checks

1. Filtered application regression tests first demonstrate the old same-revision branch defect.
2. Affected application tests and app scheduler tests verify exact state identity, publication/save
   races, retirement invalidation, and bounded scheduling. Scheduler regressions also preserve a
   distinct pending capture's cap during publication and verify one structured observer plus one
   outstanding publication request. An independent review additionally checks verified save cleanup
   across offered/unavailable recovery branches, a publication that finishes between a busy user
   request and wait registration, and obsolete scheduler results. Run module
   ktlint/detekt and compile the
   affected public-API consumers; no unrelated test matrix is required.
3. Compile the corrected measurement runner. Its bounded functional tests use synthetic rows and
   injected failure/timeout rather than collecting device latency.
4. Changed recovery/lifecycle behavior receives focused Android functional tests. Preserve every
   class invocation's XML and source/APK identities outside Gradle's overwritten result directory.
5. Run `validateDocumentation` and `git diff --check` for the governing records.
6. Required final-candidate `quality` CI supplies `check :app:android:assembleDebug`; no duplicate
   local full run, forced cold build, profile generation, or drawing benchmark is scheduled.

The real publication observation remains a separate explicit opt-in under the revised
[publication protocol](M3_AUTOSAVE_PUBLICATION_EVIDENCE.md). Functional test success is not latency
evidence and does not fill that protocol's Result section.

## Historical evidence retained

The early-morning report records application 132 tests (one skip), adapter 39, presentation 22,
and app 7. The corresponding existing host XML has zero failures/errors. The report's 24 Android
functional passes cannot all be recovered from the last overwritten class XML.
The last adapter XML records an unrequested evidence-runner invocation as one
`AssumptionViolatedException` failure, not a successful skip. No publication CSV was collected.
These historical observations are not relabelled as results for this correction.

## Results

### Regression before the correction

With only the new regression tests added to the old production implementation, run:

```text
.\gradlew.bat :core:application:test --tests io.github.hideyukimori.nenepixel.core.application.persistence.EditorAutosaveWorkflowTest --offline
```

The valid regression run took 15.475 seconds and executed 18 tests, with the five intended
assertion failures: same-revision replacement after publication; same-revision replacement during
publication; return to the previous published state during publication; same-revision replacement
during save cleanup; and undo to a Candidate state that retirement removes. These are failures of
the old implementation, retained as regression evidence. An earlier 48.602-second attempt stopped
at test compilation because the new tests lacked an assertion import; it is not counted as a
behavioral regression result.

Local raw logs are retained under `build/reports/issue-87-correction/`. Before subsequent runs,
63 historical host/device result files were copied to `historical-806d22e/` there, with SHA-256 and
original write timestamps in its manifest. These ignored raw logs are not committed or relabelled.

### Functional device profile

The reference physical device was not connected when correction work began. Functional verification
uses the existing `Pixel_8_Pro_API_35` AVD, Android 15 / SDK 35, fingerprint
`google/sdk_gphone64_x86_64/emu64xa:15/AP31.240517.022/11948202:user/release-keys`.
This profile supplies functional evidence only and does not replace the named physical publication
measurement profile.

### Correction results

The final filtered host invocation ran:

```powershell
.\gradlew.bat :core:application:test `
  --tests io.github.hideyukimori.nenepixel.core.application.persistence.EditorAutosaveWorkflowTest `
  --tests io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceAutosaveFlowTest `
  :core:application:ktlintCheck :core:application:detekt `
  :app:android:testDebugUnitTest `
  --tests io.github.hideyukimori.nenepixel.AutosaveScheduleTest `
  --tests io.github.hideyukimori.nenepixel.AutosaveSchedulerTest `
  :app:android:ktlintCheck :app:android:detekt `
  :presentation:compose:compileDebugKotlin --offline
```

All 22 selected core tests and 16 app tests passed, with zero failures/errors/skips. Both modules'
ktlint/detekt and presentation consumer compilation passed. Total verification wall time was
13.609 seconds, recorded in `verification-scheduler-save-race-final4.log`. Tests include exact
state identity, save overlap, both recovery branches that bypass cleanup, a completed publication
before user-operation wait registration, bounded retry, continuous observation during suspended
publication, retained cap, obsolete results, and no spontaneous retry after an immediate
generation-exhausted outcome.

An earlier affected-module run passed 137 core tests (one existing skip), nine app tests, and their
static/consumer checks in 12.540 seconds. Later scheduler/API and save-race changes caused the
focused final invocation above; that later result is not represented as another full-module run.
Compilation/format/complexity errors found during iteration were corrected without suppressions
or gate changes; all attempt logs remain under the correction directory.

After the runner's mechanical file/visibility changes, this invocation passed in 11.981 seconds:

```text
.\gradlew.bat :adapters:persistence:compileDebugAndroidTestKotlin :adapters:persistence:ktlintAndroidTestSourceSetCheck :adapters:persistence:detekt validateDocumentation --offline
```

The module detekt task correctly reused unchanged production inputs. Runner compilation and
androidTest ktlint executed on the corrected files. Raw log: `verification-runner-docs-final.log`.

A read-only independent core review found two additional defects: cleanup-bypassing verified
Save As retained its exact pending capture, and a publication finishing between Busy and wait
registration lost the user-operation retry. Both fixes and their regressions were re-reviewed;
no blocking finding remains. A different operation can still acquire the lease before the single
retry, which returns the ordinary Busy result; this intentionally creates no unbounded wait/queue.

The shared Save As boundary and Busy retry also invalidate their existing persistence consumers,
so one additional focused class ran:

```text
.\gradlew.bat :core:application:test --tests io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflowTest --offline
```

All 18 tests passed with zero failures/errors/skips in 10.952 seconds of verification wall time.
The previous 22 core and 16 app results were preserved first in `host-final4/`, with source and
artifact manifests. The additional class XML/log is retained in `host-existing-persistence/`.

### App functional device contracts

On the frozen final sources, the following two invocations ran sequentially on the AVD above:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :app:android:connectedDebugAndroidTest --offline `
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.hideyukimori.nenepixel.EditorRuntimeLifecycleTest
.\gradlew.bat :app:android:connectedDebugAndroidTest --offline `
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.hideyukimori.nenepixel.EditorRecoveryOfferTest
```

| Class | Tests | Failures/errors/skips | Gradle wall time | Suite time | XML timestamp (UTC) |
| --- | --- | --- | --- | --- | --- |
| `EditorRuntimeLifecycleTest` | 3 | 0 / 0 / 0 | 33 s | 12.238 s | 2026-09-11T15:01:48 |
| `EditorRecoveryOfferTest` | 2 | 0 / 0 / 0 | 114 s | 97.727 s | 2026-09-11T15:04:16 |

These functional times include UI/instrumentation work and are not publication latency. No class
was retried. Each invocation's XML, logcat, HTML, Gradle log, source manifest, and both APKs were
copied before another invocation could overwrite them, under
`build/reports/issue-87-app-device/<class>/`.

Both classes used the same immutable artifacts:

- Debug app APK SHA-256:
  `6866700578c6ba7bdbc598f71d97e522e63bb0794fa60082a0f3205775e0de17`.
- Debug androidTest APK SHA-256:
  `ebc1bcccf4b9df5ed04c0922061e931d347aa83dad15477da59cb9aad66d8477`.
- Lifecycle class XML SHA-256:
  `90180cf760518862b141e561ab81716ba772199b8d08c22db57378e8436173c8`.
- Recovery class XML SHA-256:
  `582bbecca964540bdb39a0ab093c7f39aaec59a877c81a6254ce5e6609d7282f`.

### Measurement-runner functional contracts

The focused runner checks executed on 2026-09-11 with the explicit functional class selector:

```text
.\gradlew.bat :adapters:persistence:compileDebugAndroidTestKotlin --offline
.\gradlew.bat :adapters:persistence:ktlintAndroidTestSourceSetCheck --offline
.\gradlew.bat :adapters:persistence:connectedDebugAndroidTest --offline -Pandroid.testInstrumentationRunnerArguments.class=io.github.hideyukimori.nenepixel.adapters.persistence.AutosavePublicationEvidenceJournalTest
```

Compilation and ktlint passed, each reporting 9 seconds of Gradle wall time. Earlier compilation
and formatting failures were corrected; their raw logs remain in the correction directory. The
device invocation passed all 10 functional contracts, with zero failures/errors/skips, at XML
timestamp `2026-09-11T14:45:27` UTC. Gradle reported 23 seconds; test-suite execution was 0.376 seconds.
Neither number measures Candidate publication latency.

The immutable test APK SHA-256 was
`d5543c74c79998698e6a92ba64c31593be1d01611c207ffba87062ff7b5559da`.
The preserved class XML SHA-256 is
`ed5b963f8e05ed7f56c894556fd0f0353616e889b8ebaaa14bc66ac8a4794eb40`.
The class-specific result directory contains XML, logcat, and HTML plus a manifest under
`build/reports/issue-87-correction/AutosavePublicationEvidenceJournalTest/`.

After that invocation, the internal output-reservation type moved to its own file and runner-only
constants became private. Those mechanical changes preserve the executed contract bodies and
require fresh compilation/static checks, not another device invocation. Original source hashes
were not captured before the move and are not inferred. The manifest explicitly distinguishes
the executed APK/results from the post-move source hashes. This evidence is limited to the
synthetic reporting contracts, which do not execute core autosave or publication work.

### Final source identity

The source files were frozen before final Android execution. The following Git subtree objects
identify their exact content independently of later documentation/commit labels. The local full
manifest is `build/reports/issue-87-correction/final-source-tree-identity.json`.

| Input | Git subtree |
| --- | --- |
| `core/application/src` | `4306a333ae86861e1afa04a5404d7e1ff8591b25` |
| `core/domain/src` | `7ef189088ee6773d9d6b7c1057e1462e8d79ddfe` |
| `core/pixel-engine/src` | `9087790b383928157c6268a3b121034ba2d7b036` |
| `core/project-format/src` | `bc4a36cf1b7cca910e67e7ca361e6884d9491785` |
| `adapters/persistence/src` | `b02def43b866178fa2548e99b5fb1b1073395477` |
| `presentation/compose/src` | `b979a754cbcd37063388816942df576fd5521195` |
| `app/android/src` | `c82994379a453120c5ff765ee1923a82a534be7b` |
| `gradle` | `3ffeef733d14128b8b96ef6df8b5f2a2f405b848` |
| `build-logic` | `b8d25b613eb1fd6cb5d28706396454c416e8b075` |

The fixed toolchain is JDK 21, Gradle 9.7.1, Kotlin 2.4.20, AGP 9.4.0, and Compose BOM
2026.09.00. Module builds, dependencies, locks, verification metadata, and profiles are unchanged
from the starting candidate. No baseline profile was regenerated.

## Remaining decision

All scoped functional, static, consumer-compile, and documentation checks passed. The final
selected host classes total 56 tests; Android contracts total 15 tests, including the ten synthetic
runner contracts with the artifact/reuse limitation documented above. No changed behavior claims
the earlier full-module result as a new execution.

At the initial correction handoff, the reference physical device was unavailable and the real v2
observation was not collected. On 2026-09-12, hide connected iPlay80miniPro and explicitly authorized
one run. The [completed observation](M3_AUTOSAVE_PUBLICATION_EVIDENCE.md#result) retains all raw rows
and observes a 147.071808 ms maximum for the maximum-document group, so ADR 0018's timing constants
remain unchanged. No production source changed after the functional checks above. The final merge
candidate still requires successful `quality` CI. No duplicate local full suite was run.
Active waivers: none.

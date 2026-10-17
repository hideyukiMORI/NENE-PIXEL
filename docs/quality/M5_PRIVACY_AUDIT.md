# M5 privacy and external-file audit

Issue: #232 / P5-05. Source audit date: 2026-10-17.

The public [privacy and data statement](../PRIVACY_AND_DATA_HANDLING.md) and the existing
File → About this version dialog disclose the same data boundaries in English, Japanese and
Simplified Chinese. This is a technical review of current behavior, not a legal certification or
the complete M5 verdict. [The beta matrix](../BETA_ACCEPTANCE_MATRIX.md) remains authoritative.

## Source and artifact identity

Audited main: `7fb1e73a1f06aaec87d3dc0a10dade955db9fd7c`. Raw evidence is retained under the
configured lab's `outputs/issue-232/`: `source-inputs.json`, `source-audit.json`,
`apk-manifest.txt` and `apk-backup-rules.txt`. The source-input manifest records SHA-256 per path;
the audit report records source/tree, searches, locked runtime coordinates and artifact comparison.
Its searches are review aids, not a substitute for following the data-flow owners below.

The inspected immutable **debug** APK is the original #247 `implementation/check-5/apks/android-debug.apk`:
SHA-256 `24a30dd6035ba9e9db8518137e9fef91cd2369c491a964164185cb9aaaf07411`.
Its source manifest is #247 `implementation/check-2/source-manifest.json`, SHA-256
`9a8e0006ed5b5c464c999fffd7ada07b0e676defc8743724be9fdf15f5b8481f`.
The original build plan/log are #247 `implementation/check-2/plan.txt` and `gradle.log`.
The aggregate original invocation failed on new androidTest lint; its completed main
`packageDebug`/`assembleDebug` and immutable APK identity are not relabelled as an aggregate pass.
#249's `main-apk-reuse-admission.json` separately records that main-artifact reuse.

This audit reads the retained APK rather than rebuilding unchanged configuration solely for an
identity label. Its final candidate includes disclosure UI/resource changes; unchanged manifest,
backup, dependencies and storage routes support only those boundaries' reuse. #233/#234 must still
inspect the actual final shipping artifact, record its signature/version/source/profile identity,
and compare its relevant inputs. Debug evidence is not a release-signing or runtime-backup verdict.

Before integration this change was rebased onto main
`6e9b49f772484b65c93fbf176878be8dcdd1aa4a` (#245). That base adds dispatcher injection with the
same production defaults to the app factories and groups the same autosave-request bookkeeping;
it changes no storage location, backup declaration, permission or dependency. The presentation/core
sources, resources, native test and dependency inputs for the four-case UI result are identical
to check-3. The final `check-4/reused-source-equality.json` records the path/hash comparison;
the old APK and results retain their original identities.

## Data-flow findings

Source paths below are repository-relative.

| Boundary | Inspected canonical path | Finding and disclosure limit |
| --- | --- | --- |
| Permissions | `app/android/src/main/AndroidManifest.xml`; packaged manifest | No authored permission. The APK requests only the AndroidX-generated application-specific signature dynamic-receiver permission. No `INTERNET`, broad storage, media, location, camera or microphone permission. The launcher is exported; AndroidX startup is not. ProfileInstaller's exported receiver requires `android.permission.DUMP`. |
| Network and libraries | App/presentation/persistence/core production sources; build scripts; `gradle/libs.versions.toml`; app runtime lock entries | Existing Kotlin/coroutines/AndroidX/Compose runtime graph; no app network client, account, analytics, ad or automatic crash-submission integration found. Platform/provider/browser behavior is separate. Build-time repositories/tooling and instrumented test libraries are not user telemetry. A source audit is not a packet capture. |
| Backup | App manifest; `app/android/src/main/res/xml/data_extraction_rules.xml`; both APK XML dumps | `allowBackup=false`, `fullBackupContent=false`; cloud and device-transfer rules exclude `root` path `.`. Recovery and underlay additionally use `noBackupFilesDir`. This describes configuration, not universal OEM migration behavior. No custom BackupAgent is declared. |
| Private recovery | `EditorRuntimeViewModel.kt`; `AndroidRecoveryRecordAdapter.kt`, `RecoveryRecordWriter.kt`, `RecoveryRecordReader.kt` under the existing app/persistence packages | One fixed no-backup AtomicFile record, current v4 Candidate/Retired envelope, serialized checked publication/retirement. No external-file scan or multi-project archive. Latest pending edits and interrupted publication are not guaranteed durable; typed failure/uncertainty remains visible. ADR 0045 and #230 own lifecycle/interruption acceptance. |
| Underlay memory | Same app factory; persistence `AndroidUnderlayMemoryAdapter.kt`, `AndroidUnderlayRecordFiles.kt`, `UnderlayMemoryStore.kt`, `UnderlayMemoryHousekeeping.kt`, `UnderlayMemoryLayout.kt` | Decoded pixels and placement/opacity/visibility are copied into a separate no-backup pair per DocumentId. Hidden is retained; Remove requests forget. At most 16 works / 33,554,544 image-record bytes, with bounded housekeeping; failed I/O/removal is best effort and not projected as document-save failure. No forget-all control. External project/image deletion is independent; Save As shares identity and memory. |
| Settings/session | `AndroidAppLanguageStorage.kt`, `LegacyAppLanguagePreference.kt`, app composition and runtime | API26–32 language tag uses private SharedPreferences; API33+ uses OS LocaleManager. No OS-upgrade migration promise. Appearance, viewport, previews and history are session state. OS language backup/removal follows system policy rather than the private artwork record contract. |
| External files | `ProjectPickerIntents.kt`, `ProjectContentAccess.kt`, `AndroidProjectStorageAdapter.kt`, PNG/palette/reference-image adapters, existing application workflows | SAF open/create only, temporary selected URIs, no `takePersistableUriPermission`, directory scan or in-place save. Save As/export create fresh files; writes close and verify through their accepted adapters. Pre-existing files are preserved; new partial output deletion is best effort and cleanup status can fail. Provider network/sync/retention and post-write durability are outside app control. #230 owns the matrix's named native local-provider lanes; no blanket third-party qualification. |
| Files and metadata | [Project v4](../PROJECT_FORMAT_V4.md), palette JSON/PNG codecs and reference decoder | Project identity/revision, palette, frames/durations and layer names/pixels are persisted; no history, selection, underlay, URI, language or appearance. PNG uses visible frame composite and no underlay; palette JSON contains palette semantics. Underlay memory holds decoded pixels, not the original container metadata or persistent URI grant. Users can place private content in pixels/names themselves. |
| Logs/cache | Production source review and no-backup/file call sites | No custom persistent diagnostic log, artwork/URI log call or app-owned artwork cache directory found. Framework/library/provider diagnostics and ordinary runtime caches still exist; do not claim “no logs” or secure erasure. ProfileInstaller/Android may manage performance files outside the artwork store. |
| Disclosure link | `MvpInformationControls.kt` | A deliberate tap asks Android's URI handler to open this repository's statement in an external browser. Browser/provider network use is disclosed. Missing browser leaves the offline summary available with a localized message. No app network permission/client added. |

## Removal and platform limits

Clearing cache differs from clearing application storage. Recovery/underlay are not in a cache
directory. Normal app-data clearing/uninstall removes application-private records; external
SAF files survive independently and must be managed through their provider. Clearing a single
underlay is asynchronous best effort, not an audited secure-erase operation or a guarantee that
the original provider deleted its source. A Retired recovery marker is not a user-file deletion.

Android's [Auto Backup reference](https://developer.android.com/identity/data/autobackup) distinguishes
cloud and device transfer, warns of manufacturer differences with `allowBackup=false`, and excludes
`getNoBackupFilesDir()` from standard backup. The [app-specific storage reference](https://developer.android.com/training/data-storage/app-specific)
and [SAF reference](https://developer.android.com/training/data-storage/shared/documents-files)
describe private-data removal and the separate document-provider boundary. These platform contracts
support the wording; no fresh clear/uninstall/restore/device-transfer experiment was performed.

No source/config discrepancy requiring a changed privacy/storage policy was established by this
audit. Do not strengthen this into universal transfer prevention, secure deletion, encryption of
app-owned records or all-provider acceptance. A future behavior change needs the existing focused
Issue/ADR route. Software licensing is owned separately by #233.

## Selected verification and remaining admission

Changed paths: this audit, the trilingual statement and its README/user-guide links, the existing
About dialog, three localized resource files and `MvpInformationScreenTest`.
Before execution, selected checks and the regressions they detect are:

- `git diff --check`: malformed whitespace in the changed patch.
- `validateDocumentation`: broken internal links and governing-document references introduced by
  the statement/audit/entry links.
- `:presentation:compose:testDebugUnitTest --tests '*LocalizedResourceContractTest'`: missing
  locale keys, blank translations or incompatible resource arguments.
- Affected presentation compile/format/detekt tasks: invalid Compose/resource/URI-handler API
  usage, formatting and maintainability violations in the added dialog/test code.
- Native `MvpInformationScreenTest`: all three localized privacy headings/sections and the
  external-link control are reachable by scrolling; Close remains usable; the URI request is
  correct; a rejected browser request retains the offline disclosure and localized error.
  It uses a recording/rejecting URI handler and synthetic UI, not a real browser/network operation
  or app-data reset. Device admission and exact command/artifact/log are recorded before execution.

## Results and reuse ledger

All entries retain their original identities. No source/behavior retry on unchanged failed inputs
was accepted. The two development failures were corrected before the successful candidate.

| Original run | Result and use |
| --- | --- |
| `outputs/issue-232/check-1/plan.txt`, `source-manifest.json`, `gradle.log` | Documentation validation and main-source ktlint passed; the aggregate run failed because the new rejecting URI-handler override needed an expression body. Main-source formatting is reused unchanged. The later documentation edits are validated separately. |
| `outputs/issue-232/check-2/plan.txt`, `source-manifest.json`, `gradle.log` | Main Kotlin compilation passed; the aggregate failed because that new expression body exceeded 120 columns. The body was wrapped without a behavior change. Its failure log remains intact. |
| `outputs/issue-232/check-3/plan.txt`, `source-manifest.json`, `gradle.log` | Successful selected localization contract (1 test), androidTest ktlint, main/androidTest typed detekt, native compilation and test-APK assembly. 58 seconds; no full local suite. Source-manifest SHA-256 `9984fa16cca17ecc1d53058f4e3f098ecce120bda9c1adf28443e80d668bfbe2`. |
| `evidence/issue-232-native-1/plan.json`, `instrumentation.bin`, `instrumentation.json`, `result.json` | One functional invocation: **4/4 passed**, 46.317 seconds. Raw instrumentation SHA-256 `b1b05b2d7b4c45cb569bd11bb3fef139d474d7c4cfb79e25b87b0b430e9a752d`. Installed APK hash matched the preserved artifact. |
| `outputs/issue-232/check-4/plan.txt`, `source-manifest.json`, `gradle.log` | Final documentation-only follow-up: **aggregate FAIL**, `java.util.concurrent.TimeoutException` with delayed configuration-cache report writing. No explicit task PASS is claimed. The shared-host I/O incident is [#253](https://github.com/hideyukiMORI/NENE-PIXEL/issues/253); no unchanged local retry or cache-policy change was made. |

Check-4 printed `validateDocumentation` without a validation violation or task-specific failure,
but the available log does not explicitly establish the task body's PASS. Its retained
`gradle-thread-diagnostic.txt` shows the daemon in end-of-build
`FileTimeStampInspector.updateOnFinishBuild` / `beforeComplete` blocked in a native file write,
and the configuration-cache report writer in Windows `MoveFileEx0` / `CommonReport.moveSpoolFileTo`.
`daemon-tail-diagnostic.txt` retains the corresponding failure and completion context. This
supports the separately reported shared-host storage diagnosis; it does not turn the failed
aggregate into accepted validation evidence. The delayed patch check also found one trailing
blank line in the new statement; that whitespace was corrected and the corrected diff check
passed, with both original results retained. The final documentation/quality verdict is the
first required CI run for the amended final candidate; no second local validator, UI or native
execution is triggered by these report/whitespace edits.

The native artifact is `outputs/issue-232/check-3/artifacts/compose-debug-androidTest.apk`, SHA-256
`2e398e6e0b7844422b8493b85b7588ae9a5602cb55244080173444ba52991bef`.
This is a synthetic presentation test APK, package/target
`io.github.hideyukimori.nenepixel.presentation.compose.test`, not a beta artifact.
The exact native command was:

```text
adb -s emulator-5592 shell am instrument -w -r -e class io.github.hideyukimori.nenepixel.presentation.compose.editor.MvpInformationScreenTest io.github.hideyukimori.nenepixel.presentation.compose.test/androidx.test.runner.AndroidJUnitRunner
```

The dedicated fresh API35 lane was `NENE_I232_PRIVACY_API35` / `emulator-5592`, fingerprint
`google/sdk_gphone64_x86_64/emu64xa:15/AP31.240517.022/11948202:user/release-keys`.
Admission is retained in `evidence/release-20261017-232-environment/admission.json`, SHA-256
`2ab784b3c0d9b38962e3eeea16c29d5be05f0a39a83d2a04a87603c55442acfd`.
Initial environment observations refused execution while ADB registration/first boot was
unfinished; those originals remain in that environment directory. Only after fresh-lane
admission was the package installed, then the four functional cases ran once. No ordinary
artwork/data, OS language preference, external provider or browser was changed by these tests.

The unchanged debug app permission/backup artifact is reused only at the earlier #247 identity
recorded above. The new presentation APK proves the disclosure UI, not storage/backup/provider
behavior. Main-source formatting/compilation and selected host/static results remain reusable
across report/commit identities while their relevant inputs stay identical. Unrelated failure:
the check-4 shared-host I/O incident #253. No unrelated test failure was repaired in this change.
No waiver, suppression, baseline or gate was added.

Cost inspection: opening About adds three fixed text sections and one link control. Only an
explicit link tap asks the URI handler to open a browser. No document/pixel/history operation,
preview frame, autosave capture, storage path or per-operation scan/copy changes. No unrelated
app/device suite, performance collection, Profile generation or local canonical full run was
triggered. Required final merge-candidate `quality` CI is unchanged and remains the parent's
integration gate.

The remaining release boundary is #233/#234's final artifact read-back and equivalent-input check.
Actual backup/transfer/removal on arbitrary manufacturers and third-party/cloud providers remains
unverified and is explicitly excluded from the public promise. This audit does not close #230,
#145, #231, #233, #234 or M5. Rules: ARC-001/004/009/010/011/012; KOT-017/018/019;
QLT-011 through QLT-019; ADR 0014/0016/0021/0034/0041/0045. Storage/schema/dependency changes: none; added behavior is only
disclosure display and an explicit external-browser request. Waivers: none.

# M3 app localization evidence

Issue: #102 / P3-08. Decision: [ADR 0021](../adr/0021-app-language-resources.md).
Base: `ff173f07ff45086b789da911516deaf05fc82366`. Waivers: none.

## Scope and verification plan

The Issue recorded the implementation plan before production changes and device execution on
2026-09-12. The affected slice is app language ownership, Android resources, localized presentation,
saveable input/dialog mechanics and locale-independent test selectors. Rules: ARC-001/003/004/006/007/012,
CMD-001/002/009/012, KOT-001/002/008/014/017–019, QLT-006 and QLT-011–016.
The final malformed-preference regression and picker/confirmation consumer checks were scoped in
the Issue before their execution. Earlier planning-only prose is historical, not the active scope.

The app owns one setting adapter/controller: LocaleManager on API33+, one private preference on
API26–32. Presentation owns complete English, Japanese and Simplified Chinese strings, typed outcome
mapping, accessible descriptions and stable test tags. The app composition root supplies the selected
resource context, Configuration, LocalResources and layout direction. Existing document commands,
workspace actions, runtime, history, picker broker and autosave remain canonical. No core production
file, project/recovery v1 schema, supported limit, pixel representation or filename format changes.
The app declares its already-present Compose UI artifact directly; versions/locks are unchanged.

Affected host contracts, resource/static checks, consumer compilation and functional tests on both
Android API ranges apply. There is no performance claim, latency/memory measurement, Baseline
Profile regeneration, forced cold build or duplicate local full suite. The final non-draft PR must
pass required `quality` CI with canonical `./gradlew check :app:android:assembleDebug --stacktrace`.
Issue #89 remains the integrated M3 acceptance gate; #101 and long-press menus remain separate work.

## Artifact identity and reuse

Raw local evidence is ignored under `C:/n102-i18n/build/reports/issue-102/`. Each `candidate-NN`
contains the exact app/test APKs, `source.zip` and `identity.json`. These are uncommitted source trees
on the base above, not APKs built from a later commit. Gradle 9.7.1, JBR 21.0.11, SDK 37, build tools
36.0.0, dependency catalog/locks/verification metadata and the committed Baseline Profile are unchanged.
The source archives include their exact build inputs. Build variant: debug / debugAndroidTest.

| Artifact | SHA-256 |
| --- | --- |
| Candidate 03–05 app | `d7849bdf03a94f4b902cc0309dd2f1845a642314eabde24df3e63a7077dae8a6` |
| Candidate 06–07 app, delivered | `bc97c3a40e7e98c6c5ee2a83e86f133c5f3028b8f1a5b44209b81096745efde9` |
| Candidate 03 app test | `4a5ffd1d97613c860d75ade6dc7af02d7d3b749154296822a4a8be250538236a` |
| Candidate 04 app test | `e29e6becb1323fdfa0cfdfe71341bd229fa803f2272b17ba628718fd62edfda5` |
| Candidate 05 app test | `6b5a57e62536711fcd500384be7f0e62a374045592ec5e97cfdc6a762c142e0d` |
| Candidate 06 app test | `2d9ee274e99b944bac68dedb646ca7096f1b108e35b18ceeadd83d67f08760c4` |
| Candidate 07 app test | `fcf2c5686553aa978852551166bb5d59ad11ffa3fec112dc066e3c5a2e94a398` |
| Candidate 03–07 presentation test | `4d89bfd69bfe76eabd25fcb5551e105b7308648d66f41878874e22f72aad4518` |
| Candidate 03 source archive | `3afc61a5fa6aebbbb88306e292a48f299406e5b2674b96df018f823dbb116004` |
| Candidate 07 source archive | `0638210e0ccaf7ffb91ffe1a1c43be4005b2d8790446d05bc8b421f0dc20cc7a` |

Source comparison in `production-equivalence.json` finds only one changed production file between
03 and 07: `AndroidAppLanguageStorage.kt` adds a typed write failure for ClassCastException from
a malformed legacy preference. Successful reads/writes, native LocaleManager, resources, Compose
configuration, runtime and lifecycle paths are identical. Their passing earlier functional results
are reused under QLT-012; the new failure path is checked on API26. Candidate 04–07 test changes
correct native auto-recreation and picker/fixture mechanics and add bounded overlap/restart cases.
The unchanged presentation test APK is reused on both devices. Later evidence prose does not
invalidate device results and does not relabel the archived APKs.

## Host and static checks

Fourteen affected host cases passed: AppLanguageControllerTest (5), LegacyAppLanguagePreferenceTest
(3), LocalizedResourceContractTest (1), NewDocumentEditorControllerTest (2), RecoveryOfferStatusTest
(3). The resource contract checks all 96 string keys, positional format arguments and palette-count
plurals: English one/other, Japanese/Chinese other. Controller tests cover single-operation ordering,
coalesced refresh, no-op, failure/retry and publishing only successful writes; legacy commit tests
retain the old in-memory value when commit fails.

Commands use the committed wrapper with normal cache/daemon settings. Raw iteration logs retain
both successful and failed invocations; a failed invocation is not described as wholly passing.

| Applicable command/tasks | Result / raw log |
| --- | --- |
| App/presentation `testDebugUnitTest` for the five classes above | 14 PASS; `narrow-06.log`, module JUnit XML |
| `:presentation:compose:detekt`, architecture/documentation checks | PASS tasks in `narrow-06.log` |
| `:app:android:assembleDebug :app:android:assembleDebugAndroidTest :presentation:compose:assembleDebugAndroidTest :app:android:detekt :app:android:lintDebug :presentation:compose:lintDebug :quality:baseline-profile:compileBenchmarkReleaseKotlin validateDocumentation --continue` | PASS, 21 s, `narrow-07.log` |
| `:quality:baseline-profile:compileNonMinifiedReleaseKotlin` | PASS task in `narrow-01.log`; producer selector contract unchanged afterward |
| `:app:android:assembleDebug :app:android:assembleDebugAndroidTest :app:android:detekt :app:android:lintDebug` | PASS, 16 s, `narrow-11.log`, includes final exception normalization |
| `:app:android:assembleDebugAndroidTest :app:android:lintDebug` | PASS, 11 s, `test-compile-12.log`, final picker/fixture test code |
| Affected `ktlintFormat` then separate compilation/static commands | PASS, `format-01.log` through `format-15.log`; initial failures retained |
| `:app:android:ktlintCheck :presentation:compose:ktlintCheck :quality:baseline-profile:ktlintCheck :app:android:detekt validateDocumentation` | PASS, 16 s, `final-narrow.log` |

Initial compiler/formatter/detekt/document failures were corrected without gate weakening. Lint
corrections include AGP-generated LocaleConfig as the single declaration, supported-resource
filtering, offline App Bundle language inclusion, explicit LocalResources, dimension rather than
count wording, correct plural categories, and retaining the synchronous commit Boolean instead of
KTX's Unit-returning edit. No suppression, baseline, dependency exception or waiver was introduced.
The final focused format/document validation is recorded with the PR preparation report.

## Functional device results

The physical tablet is USB iPlay80miniPro, Android 16/API36, 1200x1920, existing density override 272,
system locale ja-JP. The dedicated headless `NENE_I102_API26` emulator is Android 8/API26 Google APIs
x86_64, 1200x1920 at 272 dpi, English system locale. No existing AVD or tablet system language/density
was changed. This is correctness evidence; reported run duration is wall time, not editor latency.

The harness is direct `adb -s SERIAL install -r -t APK` followed by
`adb -s SERIAL shell am instrument -w -r -e class CLASSES PACKAGE/androidx.test.runner.AndroidJUnitRunner`.
Each run directory records exact command arguments and raw `instrumentation.txt`. The process test
adds an explicit `am force-stop` between prepare and verify invocations and asserts different PIDs.
Installed packages and private user files are retained; no `pm clear` or uninstall is used.

| Raw run directory | Candidate | Result and coverage |
| --- | --- | --- |
| `device-03-api26` | 03 | 3 PASS + 1 API33-only SKIP: three-language picker, runtime/document/Undo preservation, localized raw-input validation across recreation, external setting and System fallback |
| `device-04-api36` | 03 | 3 PASS for those cases; native regional test FAIL from redundant test-driven recreation racing OS recreation |
| `device-05-api36-regional` | 04 | 1 PASS after removing redundant test recreation: ja-JP maps to Japanese, System clears native override |
| `device-06-api26-process-prepare/verify` | 04 | 2 PASS, language retained across actual process death |
| `device-07-api36-process-prepare/verify` | 04 | 2 PASS, language retained across actual process death |
| `device-08-api36-regression` | 04 | 6 PASS: recovery offer (2), editor runtime lifecycle (3), existing real PNG picker cancellation (1), 103.786 s |
| `device-09-api26-presentation` | 04 | 15 PASS: layout (6), exact pixel/history/palette/new-document behavior (9), 21.481 s |
| `device-11-api26-final` | 06 | Confirmation-language overlap PASS; two test-fixture failures retained and corrected below |
| `device-12-api36-final` | 06 | 2 PASS: confirmation request/document retained; language change while real picker is open retains operation lease and returns Cancelled, 9.177 s |
| `device-13-api36-presentation` | 06 | 15 PASS on Japanese tablet: translated layout and existing exact pixel/history assertions, 24.611 s |
| `device-14-api26-fixture-fix` | 07 | 3 PASS: malformed preference yields typed read/write failure without replacement; both PNG cancellation cases, 6.327 s |
| `device-15-api36-picker-fix` | 07 | 2 PASS with the final keyboard-aware harness: both PNG cancellation cases, 6.109 s |

All earlier failures remain raw evidence. `device-01-api26` failed because duplicate testTag modifiers
left the wrong stable identity; fixed by one identity per node. `device-02-api26` found an English
legacy Dialog despite Japanese selection; explicit app-owned LocalResources corrected the actual
resource boundary. `device-10-api26-overlap` passed confirmation preservation but its cross-app
Instrumentation key injection was denied. UiAutomation global Back replaces that injection.
`device-11-api26-final` exposed two fixture errors: instrumentation runs as the target UID, so the
test-package preference directory was unwritable; and Back first dismissed the picker keyboard.
The corruption fixture now uses an isolated preference namespace within the target UID. The picker
harness observes TYPE_INPUT_METHOD windows, dismisses the keyboard if present, then issues exactly
one Back for cancellation; it does not loop Back until success or mask duplicate picker launches.
The same runtime/lease/Cancelled/document assertions remain. No production permission was added.

## Visual, accessibility and font review

Native screenshots and UiAutomator trees are in `visual-api36/`: English/Japanese/Chinese appearance
panels in Dark, Chinese Light, Chinese new-document Light, and restored System/Japanese Dark.
They were visually inspected for readable glyphs, wrapping and clipping. Japanese's longer System
label wraps the Chinese language choice onto a second row without clipping; language targets have
a 48 dp minimum. Installed Roboto and Noto CJK fallback render all three UI languages. Typography
carries the selected locale list for appropriate CJK glyph forms on the legacy path. No font
download/binary/license payload is necessary. No TalkBack spoken-audio audit is claimed.

Native dialog nodes expose `editor_document_width`, `editor_document_height`, `editor_create` and
`editor_cancel` resource IDs with enabled/clickable semantics. This checks the exported selector
contract used by the profile consumers without regenerating or running performance profiles.
Inspection/search found no remaining `Text("...")`, literal contentDescription/stateDescription/
paneTitle, or enum.name display use in the changed production UI. Existing technical identifiers
and user input remain literal by design. No exhaustive pseudolocale, font-scale or RTL device matrix
is claimed; configuration preservation and explicit layout direction were reviewed in code.

## Review and remaining boundaries

The necessary independent review was read-only, with no delegated implementation or verification.
Findings corrected: regional tag normalization/System clearing, failed legacy commit memory rollback,
explicit layout direction, dialog-root resource-ID export, By.res consumer disappearance checks,
and app-owned configuration documentation. Final scoped review found no further issue; the main
agent's subsequent device runs identified and corrected the two fixture errors described above.

Simplified Chinese is the stated first-translation assumption; Traditional Chinese is not shipped.
Legacy-to-API33 OS-upgrade transfer is outside this first release: native app language becomes the
sole authority after upgrade. A malformed legacy setting reports failure and preserves the stored
value; this release does not offer a destructive preference-repair operation. Theme/layout remain
session settings, while language persists. Artwork/history/format semantics are unchanged.

## User-data preservation

The latest physical-device recovery record was backed up before testing, after a lifecycle flush,
and while stopped. It is 1089 bytes, SHA-256
`4f9960995cdf0fb2637ab8d4e4dd0b5bc7073dcb69adb693faee0d538de74f34`.
This is newer than historical #99 evidence; the historical hash was not used as a restore source.
The record was held at a unique app-private guard path during fixture execution, without user
recovery adoption or discard. `device-original/` retains the original archives and manifests.

After all physical tests, the app was stopped, its current private data archived, and the test
record preserved under a distinct name. The original guard was moved back to the canonical path
and verified byte-for-byte, then verified again after launch. The app remains installed with the
delivered APK, System language (Japanese on this tablet), and the original recovery offer unadopted.
`device-restored/restoration.json`, commands, screenshot and UI tree record this result.

The historical adapter measurement CSV and status remain unchanged, SHA-256 respectively
`aa8919cf60a04f834539e0b2426ee9b7d8e9b5c0d615768795a379b7141d938d` and
`eebbf6457e46a7f63acdf9b97390f790ba443d60cfa44b607da7e5c40aa1cc1d`.
Only the task's dedicated headless emulator was stopped after testing. Raw failures, archives,
APKs and the AVD are retained. The final merge-gate result belongs to the linked PR's required CI.

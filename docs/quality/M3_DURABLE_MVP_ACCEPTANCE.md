# M3 durable MVP acceptance

Issue: #89 / P3-06. Base: `45530c880a9995c14d0dd1f14e65fb1e33a374f2`.
Status: prospective functional acceptance protocol; completed results are in [M3 exit proof](M3_EXIT_PROOF.md).
Waivers: none.

## Scope and rules

The remaining M3 work is one integrated durable journey and user-facing limitations. Production
changes are confined to read-only information in the existing File panel, using the accepted
dialog/theme/resource path in English, Japanese and Simplified Chinese. No new public production
API, dependency, module, document state, serialization format or architecture exception is needed.
Storage/rendering defects, if discovered, belong to separate focused blocking Issues.

Rules: ARC-001/004/008–012, CMD-001/006–012, KOT-008/014/017–020, QLT-006/008/009/011–016.
Decisions: ADR 0014/0015/0016/0018/0019/0020/0021. Project and recovery v1 remain unchanged.

## Evidence reuse

The exact source/artifact identities and failures in the following records remain authoritative:

- [Project codec](M3_PROJECT_FORMAT_CODEC_EVIDENCE.md): deterministic v1/golden/CRC/bounds.
- [Persistence adapter](M3_PERSISTENCE_ADAPTER_EVIDENCE.md): record representation evidence.
- [Autosave correction](M3_AUTOSAVE_CORRECTION_EVIDENCE.md) and
  [publication](M3_AUTOSAVE_PUBLICATION_EVIDENCE.md): last-safe record, ordering/interruption,
  coalescing and scheduling evidence. Historical measurement authorization is consumed.
- [PNG export](M3_PNG_EXPORT_EVIDENCE.md): exact RGBA/hidden RGB, bounded transport, cancellation,
  real ContentResolver and unchanged user files on failed fresh output.
- [Tablet shell](M3_TABLET_SHELL_EVIDENCE.md) and [localization](M3_LOCALIZATION_EVIDENCE.md):
  canonical drawing/palette/history/viewport and configuration/picker/language preservation.

Before execution, `git diff b6b8a4530f5bf24f1e2c73e8c88bf1ea41c6f47f HEAD --
adapters/persistence/src/main core/project-format/src/main gradle/libs.versions.toml
gradle/verification-metadata.xml` is empty at this base. Adapter/format inputs remain equivalent
to the #88 artifacts; #99/#102 cover the subsequent workspace/presentation changes. Required #103
CI checked the complete current host contracts. No unchanged prior matrix is repeated here.
M2 performance evidence is inherited as documented evidence, not a new M3 measurement claim.

## New functional protocol

Target: USB iPlay80miniPro, serial `T830128GB26321131293`, Android 16/API36, existing 1200x1920 / 272 dpi.
Read current device, language and private files before use. No system locale/density changes.
Variants: app debug and debugAndroidTest, normal JDK 21 / Gradle wrapper caches.

The instrumentation APK alone declares a DocumentsProvider root named `NENE-PIXEL Acceptance`,
protected by MANAGE_DOCUMENTS with SAF URI grants. It stores only named acceptance fixtures under
its own private directory, refuses overwrite and is absent from the production APK. The production
MainActivity, Activity Result contracts, picker broker, ContentResolver adapters and runtime are
used unchanged. This is a test storage fixture, not another product persistence implementation.
The UI driver locates the real DocumentsUI root/actions with accessibility nodes and verifies the
picker package before acting. It never completes the broker directly or inserts a production hook.

The provider fixture is Android/Java only in `src/androidTest/java`: its separate test-package
process cannot load Kotlin runtime classes that AGP deduplicates into the target APK. This narrow
test fixture uses only framework/JDK classes; it does not add a production Java implementation,
module, dependency, runtime packaging override or compiler exception. The instrumentation tests
remain Kotlin. Compose-aware bounded waits advance the test frame clock before inspecting SAF.

One unique evidence ID names fresh outputs and raw reports. The staged journey is:

1. Create an 8x6 document through UI; choose colors, draw/erase, undo/redo and pan/zoom; assert
   independent exact expected pixels and clean/dirty/history ownership. Save As through the real
   picker to the isolated provider and assert the exact saved checkpoint.
2. Force-stop, launch in a different process, load the saved project through the picker and compare
   full pixels/identity/revision with empty history and clean state. Export PNG through the real
   picker and independently decode/compare exact pixels. Make one further unsaved edit and wait
   for verified Candidate publication.
3. With the editor stopped and no runtime writer alive, use framework AtomicFile.startWrite to
   leave a bounded unfinished temporary write beside the last verified test Candidate. Do not call
   finishWrite. Stop that fixture process. This simulates interrupted pre-commit recovery storage;
   it is not a claim of hardware power-loss testing.
4. Launch again, verify the previous valid Candidate is offered, explicitly recover it and compare
   all pixels with dirty state and empty history. Save As to another fresh output. Verify that the
   original saved file remains unchanged.

An additional focused UI case opens the information dialog in all three app languages and checks
the content, scrolling/close action and state preservation across configuration recreation.
Unsupported/corrupt/oversized input, stale completion and interrupted-output cases reuse the
unchanged identified contract evidence rather than duplicating its complete matrix.

Each stage runs once per candidate; a failed stage stops later stages. A functional wait is bounded
at 60 seconds and a stage at 180 seconds. A failure is retained and diagnosed before a scoped fix
and new evidence ID. There is no repeat-until-pass loop, performance population, threshold change,
Baseline Profile regeneration or forced cold build.

## User-data preservation and reporting

Archive current app private files and hashes, including the current AtomicFile base/temp/backup,
then stop the app and isolate only that recovery family under a unique private guard directory.
Never substitute a historical recovery hash for the current file. Retain existing #102 fixtures
and the adapter measurement CSV/status. No pm clear/uninstall, existing provider-file deletion or
overwrite is allowed. Test output names and directories must be new.

After execution, stop the app, archive test outputs/private state, restore the original recovery
family byte-for-byte and original app locale, and verify both before and after launch. Leave any
user recovery offer unadopted. Every failure and raw source/APK/command/device identity stays in
ignored `build/reports/issue-89/`; completion records link exact immutable artifacts rather than
rebuilding for a documentation revision.

## Applicable checks

During iteration: localized resource contract, affected presentation/app compile/ktlint/detekt/lint,
documentation validation, and only the new functional cases above. Test-only provider/driver code
uses the existing Android test stack; no new library or test framework. Review checks the provider
is absent from the production manifest and the UI adds no state mutation route.

The final non-draft PR requires `quality` CI running canonical
`./gradlew check :app:android:assembleDebug --stacktrace`. Do not duplicate the full suite locally.
Issue #89 closes M3 only after every MVP_SCOPE acceptance criterion has traceable passing evidence
and no critical data-loss finding remains. #101 and M4 production remain separate work.

# M4 palette editor evidence

Issue: #107 / P4-03. Decision: [ADR 0022](../adr/0022-indexed-palette-and-migration.md).
Branch: `feat/107-palette-editor`. Waivers: none.

## Scope and verification plan

The change connects the ADR 0022 draft session, single-command Apply and palette JSON exchange to
the editor UI. A draft session lives in workspace state with its own undo/redo timeline and
retention budget; Apply submits one `ReplacePaletteCommand`; JSON export and import use SAF adapters
behind application ports; the Compose panel edits RGBA slots, reorders, appends, removes and resolves
imported slots by number, nearest color or explicit assignment. Document meaning, v2 storage, the
JSON v1 schema and the mapping algorithms of ADR 0023 are unchanged. This is a functional UI change,
not a measured optimization.

Applicable rules are ARC-001/004/010/012, CMD-001/002/008/010 and QLT-011–016. Affected host
contracts, consumer compilation, formatter/static checks, documentation validation and focused
physical-device functional tests apply. No forced cold build, latency/memory measurement, Baseline
Profile generation or duplicate local full suite is authorized. The final non-draft PR uses required
canonical quality CI.

## Local evidence identity

Raw evidence is ignored under `build/reports/issue-107/`. The device checkout manifest
`build/reports/device-checkout/s8-1/manifest.json` records commit
`bc8adaf9358f320e4e4133ebc264d09e829cca78`; the only dirty paths were untracked working reports under
`docs/reports/`. The checkout installed the app debug APK below, moved the existing user recovery
record aside under guard `issue-107-user-recovery-20260923-194733` and recorded its snapshot. The
physical device is USB iPlay80miniPro `T830128GB26321131293`, Android 16/API 36.

Later runs use later commits of the same branch: `1c2820a` (device-02) and `663f407` (device-03).
Each connected task rebuilt the Compose instrumentation APK from its tree; only the last one remains
on disk. The Compose cases run against in-test fixtures (`TestNenePixelEditor` and fake persistence
ports), not against the installed app's document or recovery record.

| Artifact | SHA-256 |
| --- | --- |
| App debug APK, `bc8adaf`, installed by the checkout | `c708a05a0bcb0abb5f8cd8a6dc2d7af966f08afe01ee3061226a05d7a7dfb124` |
| Recovery snapshot under the guard | `d66fe3b1c33039c8d06fca3718a4a26fb8e646714c13bb08a6c60ef0030dc865` |
| Compose test APK, device-01 (`bc8adaf`) | not recorded (overwritten by later runs) |
| Compose test APK, device-02 (`1c2820a`) | not recorded (overwritten by device-03) |
| Compose test APK, device-03 (`663f407`) | `78e9a9d27bf072704299c7d88d2bc5cf8a732cd00d170eae9eea181afd10801e` |

## Host and static checks

Each stage ran only its affected checks with `--offline --console=plain`; logs are in
`build/reports/issue-107/s*-*.log`. The last passing counts per affected host contract:

- core application: `PaletteEditSessionTest` 35, `PendingPaletteImportTest` 10,
  `WorkspaceReducerTest` 27, `EditorRuntimeTest` 16, `RuntimePaletteOperationsTest` 8,
  `EditorPersistenceWorkflowTest` 21, `PngExportWorkflowTest` 7, `PaletteJsonExportWorkflowTest` 6,
  `PaletteJsonImportWorkflowTest` 6; all with 0 failures.
- persistence adapters: `AndroidPaletteJsonExportAdapterTest` 6,
  `AndroidPaletteJsonImportAdapterTest` 7, `AndroidPngExportAdapterTest` 5,
  `AndroidProjectStorageAdapterTest` 8; 0 failures.
- Compose: `PaletteEditorControllerTest` 9, `LocalizedResourceContractTest` 1 (all three locales),
  `RecoveryOfferStatusTest` 3, `NewDocumentEditorControllerTest` 2, `ViewportEditorControllerTest` 9,
  `EditorAppearanceControllerTest` 1; 0 failures.
- app: `:app:android:testDebugUnitTest` 36 in 8 suites; 0 failures.

ktlint and detekt passed for every touched module, and production, unit-test and instrumentation
compilation passed for `app:android`, `presentation:compose` and `adapters:persistence`. Intermediate
failures are preserved in the stage logs: a wrong byte budget in the design instruction (corrected
to 3080 B before the stage was accepted), detekt LongMethod/TooManyFunctions/MaxLineLength and
ktlint wrapping fixed by restructuring or formatting, a non-exhaustive Compose `when` over the
new `PaletteJsonExported` outcome corrected in the following stage, and one rejected duplicate of
the unresolved-slot rule in Compose, replaced by `PendingPaletteImport.unresolvedSources`. No
suppression, baseline or threshold change was added.

The S8-1r production correction was checked with the Compose controller and resource contracts,
ktlint, detekt and instrumentation compilation (PASS, 17 seconds, `s8-1r-verify.log`).

## Physical functional results

device-01 ran three classes against `bc8adaf`, one Gradle invocation per class.
`EditorScreenLayoutTest` (6) and `UndoRedoEditorTest` (9) passed as regressions.
`PaletteEditorScreenTest` passed 9 of 10: `recreatedActivityKeepsThePanelAndTheDraft` found no
`editor_palette_editor_slot_1` after activity recreation. The panel is `rememberSaveable` UI state
while the session is runtime state, so a session could be open with no panel shown. S8-1r
(`1c2820a`) adds one production line in `EditorScreen.kt`: an open session with no panel shows the
palette editor panel. Appearance or File may still open over it; closing them returns to the editor.

device-02 reran only `PaletteEditorScreenTest` against `1c2820a`. The panel reappeared after
recreation, and the same case failed at its next assertion: the hex field did not contain
`#FF000080`. The selected slot is already `rememberSaveable` in production; the test re-hosts a
manual `ComposeView`, so saveable restoration does not apply there. The test was aligned with the
case's intent in one line (`663f407`): after recreation it selects slot 2 again, then checks the
draft value. device-03 reran the class against `663f407` and passed 10 of 10. The regression classes
keep their device-01 results because S8-1r changes only the palette-session panel condition.

Both FAILs are retained unchanged in their raw directories. No FAIL was reinterpreted.

| Run | Source | Class | Result | Gradle wall time | Raw XML SHA-256 |
| --- | --- | --- | --- | --- | --- |
| device-01 | `bc8adaf` | `PaletteEditorScreenTest` | 9 PASS, 1 FAIL | 53 s | `3d6e667f6e8b3491fd19837da3c26f93bbc2e7b07756c7be9a2ada4e7eefd81a` |
| device-01 | `bc8adaf` | `EditorScreenLayoutTest` | 6 PASS | 31 s | `cb25d62aed128bf93a0ae9e520b8925067ae3422362d1dec193dce71d0d92ed2` |
| device-01 | `bc8adaf` | `UndoRedoEditorTest` | 9 PASS | 38 s | `a4e247b14d5b7ec0124178a8b895ad13de06e32afef84176c2a5562d370c1924` |
| device-02 | `1c2820a` | `PaletteEditorScreenTest` | 9 PASS, 1 FAIL | 47 s | `6369020612e5d1c44744a70b99a7659bf9b17ddee4822f87ad831d2019e53824` |
| device-03 | `663f407` | `PaletteEditorScreenTest` | 10 PASS | 41 s | `7d30b7f25d1acba63fae5fe128531ed379ef3c428d70abf3e91e33d1cb5e527f` |

Raw locations are `device-0N/<Class>-raw/connected/debug/` with the Gradle output in
`device-0N/connected-<Class>.log`. Connected invocations use
`:presentation:compose:connectedDebugAndroidTest`, one class through
`-Pandroid.testInstrumentationRunnerArguments.class=...`, and
`-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`. These are functional cases, not
timing or performance samples. On-device text is checked in Japanese only; the three locales are
covered by the host resource contract.

## Remaining scope

- hide's visual review of the RGBA editor, slot list and mapping table in dark/light and ja/en/zh
  is pending and precedes merge.
- Palette JSON export and import reuse the `Exporting` projection phase of PNG export; a distinct
  phase is recorded debt.
- Recovery adoption discards an open palette session.
- After a stale-base rejection the only exit is Cancel.
- Reorder in the UI is up/down buttons only.
- An already assigned import source can be reassigned only by cancelling the import.

Required full canonical quality remains a separate final PR merge-candidate CI gate; its result is
recorded by the linked PR, not inferred from these local checks. No schema or dependency change is
included.

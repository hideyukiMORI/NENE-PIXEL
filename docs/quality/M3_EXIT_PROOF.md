# M3 durable MVP exit proof

Issue: #89 / P3-06. Status: technical acceptance complete on 2026-09-12; required quality CI,
merge and live Issue state are recorded by [Issue #89](https://github.com/hideyukiMORI/NENE-PIXEL/issues/89).
The [prospective protocol](M3_DURABLE_MVP_ACCEPTANCE.md) defines execution scope, triggers and bounds.
Waivers: none. No critical product data-loss finding remains from this acceptance.

## Change and canonical path

Production changes add `MvpInformationControls` under the existing File panel, with 14 new strings
in each of English, Japanese and Simplified Chinese. Dialog visibility uses the accepted local
saveable UI mechanism. The [user guide / release notes](../MVP_USER_GUIDE.md) explain Save As-only
storage, recovery limits, empty reopened history, exact PNG, v1 compatibility and deferred features.
README links the accepted MVP. No document/workspace mutation, production public API, module,
dependency, drawing/storage algorithm, supported limit or project/recovery schema changes.

Rules: ARC-001/004/008–012, CMD-001/006–012, KOT-008/014/017–020, QLT-006/008/009/011–016.
ADRs: 0014/0015/0016/0018/0019/0020/0021. Test-only provider/driver decisions are in the protocol.
Independent read-only review covered the real SAF path, independent oracles, interruption semantics,
user-data isolation and the final Compose-wait/Java-fixture correction: no blocking code finding.

## Exact functional artifacts

Base: `45530c880a9995c14d0dd1f14e65fb1e33a374f2`. Raw retained evidence is ignored under
`C:/n89-mvp/build/reports/issue-89/`. Every candidate directory has exact APKs, `source.zip` and
`manifest.json`; device directories retain commands, stdout/stderr, failures and private snapshots.
These are source archives on the named base, not claims that a later documentation commit built
the tested APK. Final source changes after c04 are documentation only.

| c04 artifact | SHA-256 |
| --- | --- |
| App debug APK, installed | `877f0368fd21241945ecb2c806ece5f881e9fdbb59d26c02a6d093ee5348569a` |
| App debugAndroidTest APK | `bf46a5857c78c80eb7c95fd453c3a3748adde1a3caec516f63205662b716bba7` |
| Source archive | `f488da761db57c779e465eaca510bcdec9c3cc1f188ce6d0e9895b681c644253` |
| Saved v1 project, 234 bytes | `37345351533fa314043ebf709df4b78571e95da0bb6e91b2b67db4d633eb1c2c` |
| PNG, 304 bytes | `c98d09b102ff951e706ed3cb672f9714863616ef8184158bff9422edaa14998d` |
| Recovered v1 project, 234 bytes | `29d8c65331b6c47c0f82559c69f06bf9284ee9ff49ad58700daed8f1b5b1ea84` |

Device: iPlay80miniPro / T830128GB26321131293, Android 16/API36, 1200×1920, 272 dpi,
system ja-JP, app language initially System. JBR 21.0.11, Gradle 9.7.1, SDK37/build tools36.0.0,
normal caches. Real picker package: `com.google.android.documentsui`. Provider exists only in
the test APK, with MANAGE_DOCUMENTS protection and individual SAF grants. No network is needed.

## Integrated acceptance results

`run_m3_acceptance.py` ran c04 once, with a 60-second functional wait / 180-second stage limit.
The four instrumentation invocations were separated by target force-stop and checked distinct PIDs:
save 4126, load 4293, unfinished write 4447, recover 4495. Result: **5 tests PASS** including the
additional three-language information case. The host also compared the earlier saved file with its
original exact bytes after recovery Save As; it remained unchanged.

| MVP journey / exit requirement | Passing evidence |
| --- | --- |
| Offline launch, validated new canvas, palette, draw/erase, undo/redo, pan/zoom | c04 `createDrawAndSave`: real 8×6 UI creation, red stroke, blue pixel, erased pixel, reversible revision 3, zoom/center changed while document/history stayed equal; independent comparison of all 48 pixels. Validation boundaries also retain M2 evidence. |
| Save As through Android picker | Same stage selects the isolated provider in real DocumentsUI, writes fresh v1, compares independent magic/dimensions/ID/revision/RGBA/CRC and clean checkpoint with current history retained. |
| Restart/load without pixel changes | `restartLoadExportAndAutosave`: another PID, real picker open; exact prior ID/revision/pixels, clean and empty history. |
| Exact PNG | Same stage uses real picker create and BitmapFactory independent decode: 8×6 and every RGBA pixel equal; export leaves runtime state unchanged. Hidden RGB and partial alpha use the unchanged #88 golden/encoder evidence. |
| Interrupted autosave and explicit last-safe recovery | `InterruptedRecoveryWriteTest` leaves a bounded framework AtomicFile `.new` without finishWrite beside the previously verified Candidate; base bytes remain identical. A later PID offers, then explicitly recovers exact revision 4 / green added pixel, dirty with empty history, and saves a fresh file. This is pre-commit interruption simulation, not hardware power loss. |
| User-facing limitations and localization | `MvpInformationUiTest`: English/Japanese/Chinese titles, reachability of final section, close, configuration recreation and unchanged editor state. All three top screenshots were visually read: every section/body and Close visible, no missing glyphs or clipped text. |
| Corruption/version/size, typed rejection, interrupted output, atomicity/stale completion | Unchanged identified #85–#88 codec/transport/recovery/PNG contracts below; current complete host gate via required quality CI. |
| Named representative benchmark and supported-profile records | [M2 exit proof](M2_EXIT_PROOF.md), [representation/limit evidence](P2_REPRESENTATION_LIMIT_EVIDENCE.md), #99 shell and #102 API26/API36 functional evidence retained. No new performance claim or repeated measurement. |

On this portrait tablet all information fits, so `performScrollTo` needs no displacement. The three
top images suffice for visual content acceptance. English/Chinese bottom captures caught a transient
blank compositor surface; those raw images remain retained and are **not** visual evidence.
Japanese bottom is readable. No repeated journey was run just to replace those redundant captures.

## Failures retained and scoped corrections

| Attempt | Result and correction |
| --- | --- |
| narrow-01 | Android lint PluralsCandidate rejected `%d per axis`. Reworded the English dimension label as a numeric range; no suppression or baseline. |
| c01 | Host could not change a component's enabled state through shell. No journey ran. Use the isolated test package's supported enable/disable operation; APKs reused without rebuild. |
| c02 | Drawing/history/viewport checks passed, then picker wait timed out before Compose's pending launch frame advanced. Replaced blocking polling with Compose-aware bounded wait and asserted controls enabled. |
| c03 | Actual picker reached the root, but separate test-provider process lacked Kotlin Intrinsics, as proven by its crash log. Converted only that fixture to framework/Java; no runtime dependency or packaging/compiler override. |
| c04 | Four durable stages and information case passed; final output bytes unchanged. |
| Restoration audit | Overbroad all-private-file comparison flagged `files/profileInstalled`. The installed AndroidX ProfileVerifier class identifies this as a package-update cache. Original and changed bytes retained; artwork/recovery/history evidence still requires byte identity. No profile generation or measurement was performed. |

## Preservation and restoration

The actual initial recovery record was a **23-byte Retired** record, not the older #102 Candidate.
It was archived and isolated before testing, restored exactly, and verified again after launching:
`fcf2c722effe44dfc99d009a96388626bcb88eebe655ef1afe865591c933503e`.
Original app locale `[]` / System is restored. The pre-existing #102 private fixture remains
`c8e1009da0b267c90cfd8c2cf0aa1012fae14303dfd3286156a11746fff84ddd` (1089 bytes).
The historical adapter CSV/status remain respectively
`aa8919cf60a04f834539e0b2426ee9b7d8e9b5c0d615768795a379b7141d938d` (5269 bytes) and
`eebbf6457e46a7f63acdf9b97390f790ba443d60cfa44b607da7e5c40aa1cc1d` (8 bytes).
Test output/recovery records are retained under unique names. The empty user guard is removed;
the test APK is disabled so its provider root is not left in the normal picker. Production APK is
updated in place. No app-data clear, uninstall, existing user output overwrite, system locale/density change
or old recovery substitution occurred. The separate AndroidX package-update cache is naturally
rewritten by APK installation; it is neither user artwork nor regenerated Baseline Profile evidence.

## Narrow checks and reuse

Commands with normal caches, all passing after the recorded correction:

- `:presentation:compose:testDebugUnitTest --tests '*LocalizedResourceContractTest'` (1 test covering all resource keys/arguments in all three languages).
- `:app:android:assembleDebug :app:android:assembleDebugAndroidTest`.
- `:presentation:compose:detekt :app:android:detekt`, affected `ktlintCheck` / `lintDebug`.
- `validateDocumentation`; the final documentation-only update repeats only this check.
- `python docs/quality/run_m3_acceptance.py --adb <adb> --serial T830128GB26321131293
  --evidence-id c04 --output build/reports/issue-89/device-c04
  --app-apk build/reports/issue-89/candidate-04/android-debug.apk
  --test-apk build/reports/issue-89/candidate-04/android-debug-androidTest.apk`.

Logs narrow-02 through narrow-06 identify the applicable subsets; no local canonical full suite.
The final non-draft PR requires `quality`: `./gradlew check :app:android:assembleDebug --stacktrace`.

The empty production/toolchain diff from final #88 (`b6b8a4530f5bf24f1e2c73e8c88bf1ea41c6f47f`)
is retained as `reused-production.diff`. Reuse identities and historical failures remain in:
[codec](M3_PROJECT_FORMAT_CODEC_EVIDENCE.md), [adapter](M3_PERSISTENCE_ADAPTER_EVIDENCE.md),
[autosave correction](M3_AUTOSAVE_CORRECTION_EVIDENCE.md), [publication](M3_AUTOSAVE_PUBLICATION_EVIDENCE.md),
[PNG](M3_PNG_EXPORT_EVIDENCE.md), [shell](M3_TABLET_SHELL_EVIDENCE.md),
[localization](M3_LOCALIZATION_EVIDENCE.md). The new integration does not claim their full matrices
were rerun on c04 or upgrade the historical M2 performance population to a new measurement.

Remaining limits: one physical API36 integrated journey plus identified prior API26/API36 evidence;
not every external/cloud provider, hardware power failure, minimum-size/large-font dialog or OS
variation. Latest uncompleted autosave edits may be lost by the accepted contract. Fixed palette,
single frame/layer, no PNG import or in-place overwrite. #101/M4 remain separate focused work;
this proof does not implement palette JSON, indexed recoloring, long-press, layers or animation.

# M3 tablet editor shell evidence

Issue: #99 / P3-07. Decision: [ADR 0020](../adr/0020-tablet-editor-appearance.md).
Base: `4863269f20a1af9601bb99de3f1006989fb441e7`. Waivers: none.

## Scope and verification plan

The Issue fixed the plan before execution. Changed behavior is workspace appearance, its render
projection and preservation through runtime installation; bottom/physical-side controls; common
palette/file/appearance panels; safe system insets and system-bar contrast. The white artwork
backing, bitmap compositing algorithm, document commands, RGBA representation, codec, recovery
record and PNG bytes remain unchanged. `PixelCanvas` clips at its allocated surface and draws only
neutral presentation margins. This is a functional UI change, not a measured optimization.

Applicable rules are ARC-001/004/007/012, CMD-001/002/009/012, KOT-001/002/008/017–019,
QLT-006 and QLT-011–016. Host contracts, consumer compilation, formatter/static checks, Android lint,
documentation validation, and focused physical-device functional tests apply. No forced cold build,
latency/memory measurement, Baseline Profile generation or duplicate local full suite is authorized.
The final non-draft PR uses required canonical quality CI. Issue #89 remains the M3 acceptance gate.

## Local evidence identity

Raw evidence is ignored under `build/reports/issue-99/`. Every tested source is an uncommitted tree
on the base above, identified by `device-01/source-sha256.json`, `device-02/source-sha256.json` or
`device-03/source-sha256.json`;
these results must not be described as builds from a later documentation/commit SHA. Catalog,
locks, Gradle 9.7.1, JBR 21.0.11, SDK 37, build tools 36.0.0 and committed profile were unchanged.
The physical device is USB iPlay80miniPro, Android 16/API36, 1200x1920 pixels, density override 272.
All connected runs retain installed APKs and preserve existing private files. Device time is not
used as an operation latency metric. Build/test wall time is recorded in each Gradle log.

The existing app recovery Candidate was discovered before the app tests. Its first presence caused
the new-document fixture to request the correct discard confirmation, and the original-condition
lifecycle test failed its expected 5x3 canvas assertion. The record was verified unchanged against
its local backup, the app stopped, and the record moved to a unique app-private preserved name for
the isolated functional fixture. It was restored byte-for-byte before delivery. Both conditions
and all failures are retained; there is no discard of user artwork or reinterpretation of the FAIL.

## Host and static checks

The affected host contracts passed: appearance reducer (2), appearance runtime installation/history/
autosave (3), persistence workflow (18), recovery adoption (7), appearance controller (1), and viewport
controller (9). The earlier iteration also passed the existing workspace reducer (13) and editor
runtime (6). Later changes to those core inputs only extracted the identical appearance reduction
into a file-private function and formatted it; no pre-existing reducer/runtime behavior changed.

App/presentation production and instrumentation compilation, affected detekt, Android lint and
documentation validation passed. Lint's existing JVM-core-module advisory is not an Android lint
failure and did not introduce a dependency/plugin exception. Exact commands and outcomes are in
`iteration-01.log` through `iteration-06.log` and `format-05.log` through `format-07.log`.
Initial formatting/compiler/static failures are preserved. Corrections include vector drawable
assets, named theme tokens, cohesive control rendering and current Compose test rectangle access.
Formatting and subsequent compilation/static analysis run sequentially to avoid editing compiler
inputs during verification.

Final narrow command (PASS, 17 seconds, `final-narrow.log`):

```powershell
.\gradlew.bat :core:application:ktlintCheck :presentation:compose:ktlintCheck :app:android:ktlintCheck :app:android:detekt validateDocumentation --console=plain
```

This checks the corrected device harness and previously unchecked final formatting inputs. The
earlier successful affected host contracts, production lint and device results retain their own
input identities; this final command does not pretend to rerun them. `git diff --check` also passed.

## Physical functional results

The six `EditorScreenLayoutTest` cases passed: 600x400 and 600x900 safe-content surfaces,
both physical edges, real appearance/palette callbacks, all 32 currently supported tool-palette
entries and reopening the selected final entry, and the zoomed canvas not overpainting the left
dock. The 600x400 tabletop assertion retains at least 250dp canvas height instead of the previous
approximately 22dp. The surface itself is not the document rectangle: ADR 0004 owns aspect fit.

Eight `UndoRedoEditorTest` cases passed initially: stroke/history branching, pencil/eraser/no-op,
exact RGBA palette selection, and new-document valid/rejected/cancelled workflows. The ninth case
correctly transformed the viewport but then attempted to draw at the now-offscreen first pixel.
That test-only input was moved to visible center pixels; the isolated corrected case passed.
All other source inputs match between the two manifests, so unrelated successful cases were not
rerun. The original FAIL is retained in `device-01/drawing-raw/`.

The first layout-output copy hit Windows path length limits on four supplemental per-test logcat
files. Its aggregate XML/protobuf results and the other raw files were retained in
`device-01/layout-raw/`; those four logcats were not retained before the following test. Subsequent
copies use Windows extended paths. This does not recreate missing logs or change test outcomes.

| Artifact | SHA-256 |
| --- | --- |
| Initial Compose test APK, layout and first drawing cases | `37a8be121ada25f8bf34ccc5941eb863000c99985db450a29d62ce1817f7f4f7` |
| Corrected Compose test APK, visible post-zoom stroke | `79aa7e2738f210bb4e785525eddcfcbfe2c1961ed8fcab2f375c829c43659594` |
| App debug APK | `11ab7b0a1fc3dbb969b7b22bfcb86bc1b2e5b454f615e164267cceb51ec3e165` |
| Initial app test APK | `143b771cdc86cafd3437be9f8be2d5cb328250855be5d66e2967395fe013176f` |

The app lifecycle case passed with the original recovery Candidate temporarily preserved. The PNG
export action opened the Android document picker; cancellation preserved the document and the file
panel could be reopened. Both successful runs used the app artifacts above. The recovery offer is
also an affected footer interaction: its existing two device contracts passed with their input
at the visible canvas center instead of the former full-surface corner. Their exact recovery-pixel,
generation, history and dirty-state assertions remain intact. This test-only input adjustment gets
a third source/test-artifact identity; it does not invalidate already-passed production behavior.
The app APK stayed identical. The final app test APK SHA-256 is
`fb8959708857ab598c1dc214aa01cc7f7aaa2ed78d713d840110e01d5711f589`.

| Device scope | Result | Gradle wall time | Raw location |
| --- | --- | --- | --- |
| Layout, palette, both edges, canvas clipping | 6 PASS | 41 s | `device-01/layout-raw/` |
| Drawing/history/new-document initial cases | 8 PASS, 1 FAIL | 34 s | `device-01/drawing-raw/` |
| Corrected post-zoom stroke input | 1 PASS | 18 s | `device-02/drawing-correction-raw/` |
| Lifecycle with original Candidate present | 1 FAIL, original record unchanged | 25 s | `device-02/lifecycle-original-condition-raw/` |
| Lifecycle with Candidate preserved for the fixture | 1 PASS | 20 s | `device-02/lifecycle-isolated-raw/` |
| Android PNG picker and cancellation | 1 PASS | 20 s | `device-02/png-ui-raw/` |
| Lifecycle flush and recovery offer adoption | 2 PASS | 111 s | `device-03/recovery-ui-raw/` |

Connected invocations use `:presentation:compose:connectedDebugAndroidTest` or
`:app:android:connectedDebugAndroidTest`, one class/method through
`-Pandroid.testInstrumentationRunnerArguments.class=...`, and
`-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`, with normal cache/daemon defaults.
These are 19 distinct successful functional cases after the two targeted corrections, not timing
or performance samples. Missing initial supplemental layout logcats remain explicitly noted above.

## Native visual review and data restoration

The installed app was inspected on the same tablet using observed Android UI hierarchy bounds and
ADB screenshots. `visual/` retains dark/light tabletop, light left rail, dark right rail, both theme
panels, palette and file panels. Controls, shared dark panels, system-bar contrast and safe drawing
insets were visually checked. The final recovery offer was inspected without accepting or declining
the original work. No additional production correction was required by this review.

The original 1,089-byte recovery Candidate was restored after the fixture app stopped. Its SHA-256
before preservation and after restoration is
`59896fef3390542e03f243eb1a4bcfc872425c626fc471b086ac2737bea16266`.
`device-02/recovery-fixture.json` records restoration completed; the original backup and post-fixture
private-file archive remain local and ignored. The app was relaunched with the original recovery
offer available. The same verified production APK remains installed. No user recovery was accepted,
declined, cleared or replaced by the tests.

Historical measurement files in the persistence test package also remained byte-identical:

| File | Preserved SHA-256 |
| --- | --- |
| `m3-autosave-publication-device-v2.csv` | `aa8919cf60a04f834539e0b2426ee9b7d8e9b5c0d615768795a379b7141d938d` |
| `m3-autosave-publication-device-v2.status` | `eebbf6457e46a7f63acdf9b97390f790ba443d60cfa44b607da7e5c40aa1cc1d` |

Source comparison after device verification found no changed production/build inputs. Subsequent
documentation and commit identity do not relabel these APKs or trigger another device run.
Required full canonical quality remains a separate final PR merge-candidate CI gate; its result is
recorded by the linked PR, not inferred from these local checks.

## Independent review

A read-only review found canvas overflow into the left dock, file results hidden behind a modal,
and a completion effect that missed repeated singleton outcomes and could close another panel.
The canvas now clips, file actions dismiss their own panel at submission, and file status uses the
same immutable projection inside the panel. The outcome-observing effect was removed. Follow-up
review found the three concerns resolved and no additional blocker. Insets and system-bar contrast
are explicit platform/presentation responsibilities, not another workspace owner.

## Remaining scope

Appearance is retained for the current runtime session and configuration recreation, not across a
fresh process. Indexed document palettes, palette editing/JSON import/recolor/Undo, eyedropper,
long-press menus, PNG reduction, animation, tiles/maps and reference tracing remain follow-on work.
No schema or dependency change is included, and M3 completion is not implied.

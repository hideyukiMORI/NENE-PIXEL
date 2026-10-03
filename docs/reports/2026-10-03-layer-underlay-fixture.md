# 2026-10-03 — fixed layer-phase underlay fixture

Issue [#145](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145), Draft PR
[#187](https://github.com/hideyukiMORI/NENE-PIXEL/pull/187); phase acceptance remains OPEN.
Rules: ADR 0031/0032/0034/0035, ARC-001/007/009, QLT-011–019. Active waivers: none.
Implementation base: `d997627e5e4eb6eb070f8dfa556468d24bd83090`.

## Changed behavior and files

The [prospective protocol](../quality/P4_LAYER_PHASE_PROTOCOL.md#fixed-underlay-material-and-load-boundary)
now pins one opaque 1024-by-1024 PNG, its exact per-pixel formula, encoded and decoded hashes,
and the existing successful-pick placement on an empty 256-by-256 document. Every source 4-by-4
block maps to one document cell. No source pixel is black; palette index 0 remains opaque black.
Factory placement is exactly `(0,0,0.25)`, alpha 128, Shown and Resting. No opacity or adjust gesture
is introduced. The real test DocumentsProvider and SAF picker remain the required app load path.

The persistence host-test source gains `LayerPhaseUnderlayFixture`, its fresh-file exporter and
`LayerPhaseUnderlayFixtureTest`. The fixture uses only JDK ImageIO in test code. The checked PNG is
`docs/quality/fixtures/p4-layer-phase/underlay-grid.png`; it is synthetic test input, not a production
asset or private artwork. The existing measurement init script adds the explicit underlay exporter
and uses its existing shared directory mapping for both AndroidTest asset sets.

The persistence AndroidTest source gains `LayerPhaseUnderlayDecoderDeviceTest`. It checks packaged
bytes through the production decoder and compares the complete decoded RGBA hash. It is enabled
only with `p4LayerFixtureCheck=true`; otherwise it is skipped. It produces no timing verdict.
This turn compiles the test but does not execute it on a device.

Pinned identities:

| Representation | Size | SHA-256 |
| --- | --- | --- |
| PNG | 184,323 bytes | `05efb3fc8edf43f45dc5a8b7cae3be148680c5694b47c47d1cf4eb7cbe26c6fb` |
| Row-major big-endian RGBA8888 | 1,048,576 pixels / 4,194,304 bytes | `f107eb10700c55df2cb3a4dd1b2723b14bc773dcd59866f4a65a5b94d583aa78` |

No production Kotlin, dependency, plugin, module, public API, file schema or performance threshold
changed. No whole-document scan, size-proportional allocation/copy, per-pixel arithmetic or preview
frame work is added to the app. Fixture generation, hashing and full-pixel checks run only in test
preparation, outside every measured gesture. Existing dirty session reports were left separate.

## Verification selected before execution

The Issue's "Underlay fixture scope" was registered before checks and retained as `issue-scope.md`.
All output is preserved under development-lab
`evidence/145-underlay-fixture/20261003T191616454-414c31666fbb4722a1c838421c412bf1/`.
The following commands use the normal cache/daemon defaults and `--offline`.

1. `gradlew.bat -I docs/quality/measurements/p4-layer-phase-fixture.init.gradle :adapters:persistence:layerPhaseUnderlayFixtureExport :adapters:persistence:ktlintTestSourceSetCheck -Pp4UnderlayFixtureOutput=<fresh-absolute-file> --offline`
   — PASS, exit 0, 25.3245613 s (`export-ktlint.log`, `export-run.json`). The explicit new output was
   visually inspected, then copied to the previously absent checked asset path with matching hash.
2. `gradlew.bat -I docs/quality/measurements/p4-layer-phase-fixture.init.gradle :adapters:persistence:testDebugUnitTest --tests '*LayerPhaseUnderlayFixtureTest' :adapters:persistence:ktlintTestSourceSetCheck :adapters:persistence:compileDebugAndroidTestKotlin :adapters:persistence:ktlintAndroidTestSourceSetCheck :adapters:persistence:mergeDebugAndroidTestAssets :app:android:mergeDebugAndroidTestAssets --offline`
   — overall exit 1, 21.3808963 s, because the new Android test's chained asset-open line needed
   wrapping. The original failure remains in `fixture-check.log` / `fixture-check-run.json`.
   The two selected host tests PASS (0 failures/errors/skips, 0.348 s), retained in `host-test.xml`.
   They prove exact PNG bytes, allowed chunks/header, all decoded pixels, opacity/non-black source
   and the production placement factory. AndroidTest compilation and both asset merges also passed.
3. After the formatting-only correction,
   `gradlew.bat :adapters:persistence:ktlintTestSourceSetCheck :adapters:persistence:compileDebugAndroidTestKotlin :adapters:persistence:ktlintAndroidTestSourceSetCheck --offline`
   — PASS, exit 0, 44.4146636 s (`style-correction.log`, `style-correction-run.json`). The first run
   did not finish the host ktlint task; this invocation completes it. The passing host tests,
   exporter and asset merges were not repeated.
4. Read-only `Get-FileHash -Algorithm SHA256` and file-length inspection of the source PNG and
   `adapters/persistence` / `app/android` merged `debugAndroidTest` asset entries — all three match
   184,323 bytes and the pinned PNG hash (`merged-asset-hashes.json`). These are merged entries,
   not proof of a built APK entry or a successful Android decode.
5. Read-only `git rev-parse <revision>:<path>` comparison of the seven pick/placement/decoder source
   files between underlay baseline `f92b1006be5f7145a32258446474f8640b14b60b` and the implementation
   base — 7/7 Git blobs identical (`source-equivalence.json`). The paths are
   `RuntimeReferenceImageOperations`, `ReferenceUnderlay`, `UnderlayPlacement`, `UnderlayOpacity`,
   `ReferenceImage`, `AndroidBitmapReferenceImageDecoder` and `ReferenceImageLimits`.

Initial and final source SHA-256 identities and normalized Git blobs are retained in
`initial-check-source-hashes.json` / `final-source-hashes.json`. `reused-host-inputs.json` confirms
that only the Android test formatting changed after the host result; its generator, exporter,
host test, init script and source PNG stayed byte-identical. The source comparison and test prove
the shared factory contract, not device-visible setup or framework-grant behavior.

`gradlew.bat validateDocumentation --offline` PASS, exit 0, 13.6618696 s, after this report and its
handoff links were present (`validate-documentation.log`, `documentation-run.json`). Appending
these numbers changes no validated link or governing rule. Targeted whitespace inspection PASS.
No unrelated app test, full local
check/build, connected test, APK installation, profile generation or performance collection runs.
No new unrelated failure; existing typed-detekt debt remains separate in #186 and was not rerun.

## Reuse and remaining admission

The earlier frame collector/analyzer/seal and maximum-project/native-preservation results retain
their exact commands, source identities and logs in the
[frame connection report](2026-10-03-layer-frame-connection.md) and its linked reports. Their code
is unchanged by this slice, so no replay is required for the new asset, documentation or commit.
The maximum project's asset/source remains unchanged; its prior format correctness proof is reused.

The fixture and placement material is fixed. Full phase admission remains closed: actual provider
staging, real app URI grant/picker load, Android decode, visible correctness and asynchronous
recall/publication quiescence still require implementation and eventual device proof. A rounded
50-percent label is not a substitute for alpha 128. The existing v7 manifest does not admit the new
phase, and the phase collector's live refusal remains in force.

Live-editor memory/storage instrumentation, full four-artifact manifest/outer adapters/frame-slot-v2,
artifact/profile identities, complete budgets and stop rules, and preservation/restoration with
the last recorded installation remain under #145. Only after those agree can a current original
snapshot, bounded device run, restoration and phase verdict follow. No phase PASS or merge claimed.

Active waivers: none.

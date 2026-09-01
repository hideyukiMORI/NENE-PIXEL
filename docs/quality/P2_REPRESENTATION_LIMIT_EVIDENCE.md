# P2 Pixel Representation and Limit Evidence

Status: incomplete and blocking ADR 0005 acceptance. This ledger records evidence for `P2-01`
/ Issue #38. It does not define a production representation or numerical product limit.

## Decision boundary

ADR 0005 may become `accepted` only after one semantic, storage, and logical-limit policy has
passed the complete pre-fixed matrix on the named physical minimum Android profile. Host JVM
and emulator results are auxiliary. Missing physical ART, retained heap, PSS, GC, or frame
evidence is a blocker, not a waiver.

Current evidence state:

| Evidence | State | Decision use |
| --- | --- | --- |
| Immutable M1 sparse host reproduction | complete for the rerun recorded below | starting evidence only |
| Current-main P2 representation route | current canonical host route, preliminary physical command screening, and one final-protocol affected-frame batch complete | incomplete; full matrix, retained memory, and compositor correlation required |
| Analytical storage candidates | flat packed and tiled/COW square/rectangular snapshot/apply screening recorded; palette U8 contract tested | incomplete; duplicate/no-op, patch/inverse layout, semantics, retained and physical evidence required |
| Named emulator | ART command harness complete in explicit auxiliary mode | cannot satisfy physical evidence |
| Named physical minimum Android device | profile fixed, preliminary command screening and one final-protocol affected-frame batch recorded | valid physical evidence; complete physical matrix still blocking |
| Accepted representation or hard limits | none | ADR remains proposed |

Active waivers: none.

## Immutable M1 reproduction

The historical route was rerun from immutable merge commit
`37c0f57d59a73fd962c285e79e8e193a81402d31` in the isolated local worktree
`../NENE-PIXEL-m1-proof-38`. The worktree was detached at that exact commit. The command was:

```powershell
Push-Location ..\NENE-PIXEL-m1-proof-38
.\gradlew.bat measureM1VerticalSlice --rerun-tasks --no-build-cache
Pop-Location
```

Result: success, 26 tasks, 38 seconds. The measurement correctness assertions remained enabled.
This run reproduces the historical route; it is not a current-main P2 result and does not reuse
the meaning of any P2 viewport or representation metric.

### Raw artifacts and checksums

The CSV files are ignored build artifacts under the isolated worktree. Checksums use SHA-256
over the exact file bytes after the completed run:

| Raw path relative to isolated worktree | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/m1/core-measurement.csv` | 3,228 | `EBF1C41808DDFB8B24060B6712325BA7D8655F4774435D865C5A1BE4AC0E4C5E` |
| `build/reports/m1/interaction-measurement.csv` | 1,137 | `A61FB549C7BAFE298D4ABA67517586A026D9A9E995599193353517267851A68B` |

Checksum command:

```powershell
Get-FileHash build/reports/m1/core-measurement.csv -Algorithm SHA256
Get-FileHash build/reports/m1/interaction-measurement.csv -Algorithm SHA256
```

### Recorded profile and boundaries

| Item | CSV value |
| --- | --- |
| Schema | `nene-pixel-m1-measurement-v1` |
| Profile | `NENE-M1-WINDOWS-I9-10850K-JBR21` |
| OS | Windows 11 10.0 amd64 |
| JVM | OpenJDK 64-Bit Server VM 21.0.11+1-b1163.116 |
| Build variants | `core-jvm-test-output-in-presentation-debug-host-worker`; `presentation-debug-host-unit-test-worker` |
| Worker | isolated 512 MiB host test worker, one fork, as fixed by the immutable route |
| Warmup / samples | 20 / 50 per metric |
| Canvas fixtures | 16 x 16, 64 x 64, 256 x 256 for core; 16 x 16 for interaction |
| Allocation boundary | current HotSpot test-thread allocated bytes; retained heap and Android PSS excluded |

The metric boundaries are exactly those emitted by the immutable CSV:

| Metric | Included boundary | Important exclusions |
| --- | --- | --- |
| `snapshot_create` | `PixelSnapshot.create` defensive row-major ownership | input-list construction; retained heap |
| `patch_create` | canonical `rasterizeStroke`, change list, and patch creation | document transition; next snapshot |
| `command_apply_stroke` | gateway apply including patch, next snapshot, ChangeSet, and one-level history | fixture and command construction |
| `command_undo` | gateway recorded inverse transition and history move | prior stroke setup |
| `command_redo` | gateway recorded forward transition and history move | prior apply/undo setup |
| `translated_controller_commit` | M1 fixed offset translation, preview, one gateway command, and render projection | Compose pointer dispatch, draw, frame scheduling, GPU/compositor |

### Rerun observations

Latency and allocation values are descriptive observations, not pass thresholds or product-limit
claims. Allocation is current-thread HotSpot allocation, not retained bytes.

| Canvas | Metric | Positions | Median ns | p95 ns | Median allocated bytes | p95 allocated bytes |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 16 x 16 | snapshot create | 0 | 800 | 4,600 | 1,152 | 1,152 |
| 16 x 16 | patch create | 16 | 20,800 | 40,700 | 3,352 | 3,352 |
| 16 x 16 | command apply | 16 | 36,700 | 82,900 | 16,552 | 16,552 |
| 16 x 16 | command undo | 16 | 9,400 | 31,700 | 13,208 | 13,208 |
| 16 x 16 | command redo | 16 | 8,600 | 15,400 | 13,184 | 13,184 |
| 16 x 16 | translated controller commit | 16 | 81,100 | 174,600 | 23,088 | 23,088 |
| 64 x 64 | snapshot create | 0 | 3,300 | 13,800 | 16,512 | 16,512 |
| 64 x 64 | patch create | 64 | 50,000 | 77,100 | 12,544 | 12,544 |
| 64 x 64 | command apply | 64 | 70,200 | 101,000 | 47,568 | 47,568 |
| 64 x 64 | command undo | 64 | 41,400 | 47,400 | 35,032 | 35,032 |
| 64 x 64 | command redo | 64 | 40,500 | 82,700 | 35,008 | 35,008 |
| 256 x 256 | snapshot create | 0 | 44,400 | 68,700 | 262,272 | 262,272 |
| 256 x 256 | patch create | 256 | 34,500 | 100,300 | 44,728 | 44,760 |
| 256 x 256 | command apply | 256 | 609,200 | 783,300 | 576,608 | 576,648 |
| 256 x 256 | command undo | 256 | 486,200 | 631,100 | 531,888 | 531,888 |
| 256 x 256 | command redo | 256 | 495,600 | 602,300 | 531,864 | 531,864 |

The rerun remains consistent with the M1 conclusion: snapshot and command transitions contain
area-sized work even for a sparse diagonal, while patch construction scales with changed
positions. Normal host/JIT noise changes latency observations between runs; the fixed route makes
their boundaries comparable without turning them into pass/fail claims.

## Current-main P2 host route

The separately named current-main route was rerun from branch `adr/38-pixel-color-limits` at
source commit `e0b0b586c1e2f8c9adcf7ba272919afd8e8189dc`. The exact command was:

```powershell
.\gradlew.bat measureP2RepresentationLimits --rerun-tasks --no-build-cache
```

Result: final recorded rerun success in 18 seconds with separately named 512 MiB application and
pixel-engine test workers. Schema `nene-pixel-p2-representation-limits-host-current-v4` contains
21 metric summaries, 174 raw samples, and six typed current-structure analysis rows. Schema
`nene-pixel-p2-representation-limits-host-candidates-v3` contains 350 test-only candidate metric
summaries and 3,500 raw candidate samples. Every current-path sample retained its exact snapshot,
revision, patch/inverse, full-canvas affected region, unaffected pixels, history availability, and
typed no-op assertions.
Every candidate sample matched the independent row-major semantic pixels and exact inverse
digest; candidate correctness failures and cross-candidate digest mismatches were both zero.

Candidate buffers, copy-on-write workspaces, measurement, contract tests, and CSV generation are
self-contained under `:core:pixel-engine` test source, which preserves the ARC-005 mutation
enclave without adding a production implementation path. The `:core:application` test route owns
only canonical command/history measurements and logical retained-count analysis. The root task
orchestrates both reports without a cross-module test-fixture dependency.

The diagnostic route uses five warmups and ten samples for standard rows and three warmups and
seven samples for dense/history rows. Its p99 is therefore the observed maximum, not sufficient
physical-profile p99 evidence.

| Raw path | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/host-current.csv` | 48,962 | `FDCAD9F035492F9ED4F18B7717EB366B17A3EC8786327B25DFB99A1CDC298698` |
| `build/reports/p2/representation-limits/host-candidates.csv` | 1,663,359 | `6E2E5549BFD6FEADB5362411F9D3C4FA5DDEF25D71078456AE6D635619CE87AB` |

Selected observations show why this run cannot authorize current limits:

| Workload | Canvas / changes | Host p95 ns | Host p99 ns | p95 allocated bytes |
| --- | --- | ---: | ---: | ---: |
| sparse pencil-equivalent | 1024 x 1024 / 1,024 | 26,080,600 | 26,080,600 | 8,591,792 |
| dense pencil-equivalent | 256 x 256 / 65,536 | 31,640,400 | 31,640,400 | 13,196,248 |
| dense reference-clear fixture | 256 x 256 / 65,536 | 20,763,800 | 20,763,800 | 13,196,248 |
| dense same-color no-op | 256 x 256 / 0 changes | 4,300,300 | 4,300,300 | 4,670,280 |
| dense forward patch apply | 256 x 256 / 65,536 | 2,386,700 | 2,386,700 | 524,480 |

The v4 canonical gap rows distinguish raw path, patch, and history units. Black is only a
reference-clear fixture and is not an accepted blank or eraser semantic:

| Current canonical boundary | Raw positions | Effective changes | History entries | Host p95 ns | p95 allocated bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| duplicated full-canvas raw stroke | 131,072 | 65,536 | 1 | 16,922,000 | 13,196,272 |
| black-on-black reference-clear no-op | 65,536 | 0 | 0 | 4,186,100 | 4,670,280 |
| reverse-row-major standalone patch create | 0 | 65,536 | 0 | 3,108,500 | 3,735,224 |
| final-record standalone patch late conflict | 0 | 65,536 | 0 | 2,470,900 | 262,272 |

The duplicate route asserts raw `P=2N` collapses to canonical `C=N`, the no-op retains the full
document state and empty history, shuffled create equals the canonical patch and round-trips, and
late conflict returns the exact typed final-position mismatch while the complete source snapshot
and revision remain unchanged. Standalone patch rows deliberately record zero raw stroke positions
and zero history entries.

These are HotSpot current-thread observations. They exclude retained heap, renderer work, ART,
PSS, and frames. The candidate route separately screens square and area-equivalent rectangular
canvases at 4,096, 16,384, and 65,536 pixels. Snapshot build uses one semantic color, exactly 256
colors, and deterministic high-entropy RGBA. High-entropy apply uses one pixel, diagonal, full
row, full column, 25%, 50%, and 100% serpentine changes. Selected 256-square one-pixel rows show
the copy boundary without selecting a candidate:

| Test-only candidate | Host p95 ns | p95 allocated bytes | Logical snapshot payload | Copied payload |
| --- | ---: | ---: | ---: | ---: |
| current object-list fixture | 67,900 | 524,520 | 65,536 references | 65,536 references |
| flat packed RGBA8888 | 40,500 | 262,296 | 262,144 bytes | 262,144 bytes |
| tiled/COW RGBA8888, tile 16 | 9,600 | 2,400 | 262,144 bytes | 1,024 bytes |
| tiled/COW RGBA8888, tile 32 | 5,400 | 4,688 | 262,144 bytes | 4,096 bytes |
| tiled/COW RGBA8888, tile 64 | 4,800 | 16,784 | 262,144 bytes | 16,384 bytes |

The ten host samples are diagnostic and do not rank the candidates. The object-list row is a
test-only structural fixture; canonical production behavior remains in `host-current.csv`. The
common packed test-driver patch is not a measured candidate patch/inverse layout. Palette U8
pack/index correctness, unsigned index 255, and typed rejection of a 257th semantic color are
covered by contract tests, but palette performance remains blocked on semantic ownership. The
current CSV retains the logical reference/change analysis and explicit exclusions. Candidate
duplicate/no-op/reference-clear inputs, candidate-specific patch/inverse storage, analytical
history retention, retained heap, ART, PSS, and frames remain required.

### Pre-fixed candidate patch and inverse slice

Before collecting candidate schema v4, the next host slice fixes the snapshot and patch layout as
one test-only configuration. It compares the object-list snapshot with materialized object-change
inverse records against flat packed and tiled/COW snapshots with canonical packed triplets and a
directional inverse view over shared backing arrays. The five configuration IDs are distinct from
the snapshot-only candidate IDs and cover object-list, flat packed, and tiled/COW edges 16, 32,
and 64. Palette performance remains excluded.

The fixed operations are shuffled patch create, inverse create, forward apply, inverse apply,
exact forward/inverse round trip, and final-record late conflict. Every sample must preserve
canonical row-major ordering, exact affected region, source/applied/restored revision, complete
semantic and unaffected pixels, and atomic typed rejection. Application results are closed as
`Applied`, `ShapeMismatch`, `RevisionMismatch`, or `BeforeValueMismatch`; expected rejection is
evidence rather than an exception.

Schema v4 records configuration, snapshot representation, patch layout, inverse storage kind,
forward and inverse record counts, primitive payload bytes and reference slots in separate units,
shared backing payload, canonical-order digest, affected-region bounds, before/after/restored
revision and pixel digests, result or rejection kind, and raw timing/allocation samples. Reference
slots are not converted to estimated bytes. Input generation and full correctness verification
remain outside the timed interval. The HotSpot rows are diagnostic only and cannot select a
candidate or a hard limit.

This slice does not implement production history, candidate duplicate/no-op/reference-clear raw
paths, retained-history budgets, physical ART/PSS/frame comparisons, semantic color decisions, or
production migration. Those remain subsequent evidence gates while ADR 0005 is proposed.

## Auxiliary Android harness proof

The Android command harness compiled and ran on the `Pixel_8_Pro_API_35` AVD only after the
runner argument `nene.p2.allowEmulatorAuxiliary=true` was supplied. Without that argument, the
same emulator was rejected with `IllegalStateException` before measurement. The raw CSV records:

| Item | Recorded value |
| --- | --- |
| Schema | `nene-pixel-p2-android-command-measurement-v1` |
| Evidence class | `auxiliary_emulator` |
| Profile ID | `AUXILIARY-EMULATOR-PIXEL-API` |
| Device | Google `sdk_gphone64_x86_64`, Android 15 / API 35, `ranchu` |
| Emulator signals | `ro.kernel.qemu=1`, `ro.boot.qemu=1`, build/model/product signals |
| Runtime heap | 201,326,592-byte max; 192 MiB memory class |
| Protocol | one warmup, two samples, 5 workloads x 3 canvas sizes |
| Rows | one pre-workload post-GC baseline plus 30 sample rows |
| Retained boundary | current gateway, document, and one-level history remain strongly reachable through post-GC Java heap/PSS capture |
| On-device raw path | `files/p2-measurements/p2-android-command-measurement.csv` in the target app |
| Raw bytes / SHA-256 | 16,677 / `6042A4C0244336598C067FD1CBFDF6C4EE064ED063C0F606762254CF5E1AA46C` |

The auxiliary run asserted exact state, revision, history, ChangeSet revision, and typed no-op
behavior for sparse apply, dense apply, same-color no-op, undo, and redo at 16, 64, and 256.
It also proved the ART runtime-stat, post-GC Java heap, and `Debug.MemoryInfo` PSS fields can be
collected without granting emulator results physical-evidence status. It has too few samples,
includes no frame profile, and runs on virtualized x86_64 hardware, so none of its timings or
memory readings select a product representation or limit.

The managed smoke route is reproducible with the following PowerShell command. Each Gradle
property is quoted because PowerShell otherwise splits property names containing dots:

```powershell
.\gradlew.bat :app:android:connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=io.github.hideyukimori.nenepixel.measurement.P2AndroidCommandMeasurementTest" `
  "-Pandroid.testInstrumentationRunnerArguments.nene.p2.physicalProfileId=AUXILIARY-EMULATOR-PIXEL-API" `
  "-Pandroid.testInstrumentationRunnerArguments.nene.p2.allowEmulatorAuxiliary=true" `
  "-Pandroid.testInstrumentationRunnerArguments.nene.p2.warmupIterations=1" `
  "-Pandroid.testInstrumentationRunnerArguments.nene.p2.sampleCount=2"
```

For physical route screening, omit `nene.p2.allowEmulatorAuxiliary`, use the completed stable
physical profile ID, and retain the default five warmups and twenty samples. Final decision
evidence instead uses the expanded protocol fixed below. The harness rejects an emulator before
measurement unless the auxiliary flag is explicitly true. The managed connected-test task can uninstall its packages
after execution, so the on-device CSV must be copied before teardown when collecting the final
raw artifact set; its byte length and SHA-256 are then recorded in this ledger.

## Preliminary physical Android command screening

The command harness was run after the physical profile below was fixed, with test code unchanged
from commit `5b9675a5a77186c9ce5e5095bc88b5a485ad18fc` and the profile-recording document at
commit `5922535634033954bbb10319c6e63c019e48d799`. The app and test APKs were assembled, installed,
and invoked with the physical device selected explicitly. The raw device file and host copy had
identical byte lengths and SHA-256.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-core.csv` | 123,032 | `9292794F58CDDE42CF46E6AF733E7A524312C4045369BBBFA25C24ECB68E0645` |
| `app/android/build/outputs/apk/debug/android-debug.apk` | 11,698,623 | `E263F56E483541097206FCB0BB5004DEB3C9A833867CAB1429FDCC83F50EE7DF` |
| `app/android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk` | 1,211,189 | `51431D89A30DBDC76B5C38BE88BAA13CFEFDEBECED6E8EFF990EE9795E893025` |

The run used five warmups and twenty samples for each of five workloads at 16, 64, and 256,
for 300 measured samples. All exact state, revision, history, ChangeSet, and typed no-op
assertions passed. Twenty samples give a useful screening result, but p99 is the observed maximum
and is not final tail evidence. No representation or hard limit is selected from this run.

| 256-square workload | p95 latency | p99 latency | p95 ART allocation |
| --- | ---: | ---: | ---: |
| sparse apply | 21.639 ms | 21.987 ms | 6.594 MiB |
| dense apply | 120.381 ms | 121.785 ms | 20.156 MiB |
| dense same-color no-op | 35.879 ms | 45.864 ms | 5.141 MiB |
| undo | 34.776 ms | 37.276 ms | 8.344 MiB |
| redo | 34.293 ms | 42.377 ms | 8.344 MiB |

Every 16- and 64-square workload passed the command-latency screening targets. Every 256-square
workload failed p95 <= 8.0 ms and p99 <= 16.67 ms. Blocking-GC count remained zero for all
300 measured operations. Non-blocking GC occurred in 42 samples, including every 256-square
dense apply sample, with 79 collections and 2,548 ms total runtime-stat GC time.

The process baseline was 2,057,536 bytes post-GC Java heap and 89,903 KiB total PSS. Across
command samples, maximum post-GC heap was 7,174,000 bytes and total PSS ranged from 84,568 to
90,595 KiB. These command-only readings are numerically below the heap/PSS headroom targets, but
they exclude render projection and retained analytical history and therefore are not a formal
pass. Maximum heap minus baseline was 5,116,464 bytes, above the 2,684,355-byte churn threshold;
the required cap-rejection plus ten-cycle workload was not executed, so this is a signal for the
next matrix rather than a formal churn failure.

This screening does not cover rectangular and partial-density workloads, raw duplicate paths,
standalone patch boundaries, conflicts, limit rejection, multi-entry analytical retention,
renderer projection, frames, or packed representation candidates. Final tail collection will
use a larger pre-declared sample protocol, and PSS will use five independent invocation
checkpoints.

## Physical Android affected-frame result

Commit `25915073c76c6648706e10df3ed9ad9f7e3270b6` adds the separately named
`P2AndroidFrameMeasurementTest` and schema `nene-pixel-p2-android-frame-measurement-v1`.
The route compiles, passes app/presentation lint, detekt, and ktlint, and assembles the debug app
and test APKs.

The first physical invocation started at 2026-09-01 21:01:41 JST with thermal status 1, USB
power, an awake display, power saving disabled, and active 1200 x 1920 at 90 Hz. It stopped during
warmup generation 1 before any measured sample or environment checkpoint row because the test
thread entered its private condition wait before the Compose test clock had driven the published
state through recomposition and draw. The exact generation therefore timed out after 10 seconds.
No frame CSV was written and this attempt is invalid rather than performance evidence.

Commit `00355ef283624417416a4c2999cc554d1a323f18` fixes the orchestration without weakening frame
identity: it drives Compose to idle after publishing each measured generation, retains the Android
contractual `View.getDrawingTime()` to `FrameMetrics.VSYNC_TIMESTAMP` match, searches all exact
markers for the generation after the per-arm buffer baseline, and requires monotonically
increasing generations. Timeout diagnostics now retain marker/frame counts, nearest VSYNC delta,
both VSYNC timestamp forms, and dropped-report count. The corrected route is compiled and quality
checked.

The corrected invocation ran after a 2026-09-01 21:12:13 JST preflight and completed in 139.951
seconds with `OK (1 test)`. It used five warmups and 200 measured samples. The CSV reports ten
environment checkpoints: before samples, after every 25 samples, and after the batch. Every
checkpoint retained display mode 1 at 1200 x 1920 and 90 Hz, thermal status 1, power saving
disabled, an interactive display, USB power, and 89% battery. A post-run device query retained
thermal status 1, USB power, power saving disabled, and an awake display.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-frames.csv` | 123,670 | `27B0A3A5010B2E79796B42D0468AAFD5C6FF06B500D996FD1F63ABAC24E9E369` |
| `app/android/build/outputs/apk/debug/android-debug.apk` | 11,664,182 | `CD655DF7F6CE61821D6C485C5C0163153E81EF5AE4C639722EE022EA4E51C88F` |
| `app/android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk` | 1,216,157 | `9907F22061A8EF92CB4A4D2917157BCCD1495C155B14D9608AC3A2CE6335418E` |

The collection is valid renderer evidence but the current 256-square dense route fails the
pre-fixed affected-frame conditions:

| Observation over 200 nearest-rank samples | Result | Pre-fixed target | Outcome |
| --- | ---: | ---: | --- |
| Total affected-frame duration p95 | 204.600 ms | p95 meets the per-frame deadline | fail; 0 / 200 met the recorded 10.000 ms deadline |
| Affected-frame overrun p99 | 199.535 ms | <= 16.67 ms | fail |
| Command start to first exact app-issued frame p95 | 303.046 ms | <= 33.33 ms visible correctness | numerically fail and not compositor proof |

All 200 samples had exact render state, matching revision and snapshot hash, zero image mismatch
across all 65,536 logical pixels, a positive and unique API 36 frame-timeline vsync ID, and zero
FrameMetrics report drops. The direct command-to-frame value is specifically a test-clock-
synchronized app-issued latency because the Compose test rule drives recomposition to idle; it is
not claimed as natural production scheduling or physical SurfaceFlinger presentation latency.

The route uses a debug-only presentation bridge that delegates to the canonical `EditorScreen`
and `PixelCanvas`; it is absent from release compilation and adds no release API or dependency.
The test runs in the real `MainActivity` window, copies `FrameMetrics` immediately on its callback
thread, correlates exact state/draw generations, and verifies all logical pixel centers through
Compose image capture outside the timed interval. It records thermal, display, power-save,
interactive, USB-power, and battery checkpoints before samples, every 25 samples, and after the
batch. Missing required timing values, callback drops, thermal status above 1, display changes,
power-save mode, a non-interactive display, or loss of USB power invalidates the run.

The recorded physical invocation was:

```powershell
.\gradlew.bat :app:android:assembleDebug :app:android:assembleDebugAndroidTest --rerun-tasks --no-build-cache
adb -s <PHYSICAL_DEVICE_SERIAL> install -r -t app/android/build/outputs/apk/debug/android-debug.apk
adb -s <PHYSICAL_DEVICE_SERIAL> install -r -t app/android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk
adb -s <PHYSICAL_DEVICE_SERIAL> shell am instrument -w -r `
  -e class io.github.hideyukimori.nenepixel.measurement.P2AndroidFrameMeasurementTest `
  -e nene.p2.physicalProfileId NENE-P2-ALLDOCUBE-IPL80MP-A16-API36 `
  -e nene.p2.candidateId current-flat-dense-256-square `
  -e nene.p2.runIndex 1 `
  -e nene.p2.sourceCommit 00355ef283624417416a4c2999cc554d1a323f18 `
  -e nene.p2.frameWarmupIterations 5 `
  -e nene.p2.frameSampleCount 200 `
  io.github.hideyukimori.nenepixel.test/androidx.test.runner.AndroidJUnitRunner
```

The defaults are five warmups and 200 measured frames. The device CSV path is
`files/p2-measurements/p2-android-frame-measurement.csv`; it is copied to the planned
`device-frames.csv` host path and checksummed before teardown.

## Starting implementation evidence

This inventory names the current path that candidate measurements must compare and that a later
migration must either retain explicitly or replace atomically.

| Concern | Current evidence | Consequence to measure or close |
| --- | --- | --- |
| Semantic color | `ColorChannel` is U8; `PixelColor` has named RGBA channels but no accepted color-space or alpha contract | transparency, blank, eraser, equality, Compose, and future PNG/format meaning remain unresolved |
| Canvas validity | `CanvasWidth` and `CanvasHeight` accept every positive `Int`; `CanvasSize.pixelCount` is `Long` | a valid canvas can exceed all current `Int`-indexed materialization paths |
| Snapshot | `PixelSnapshot` defensively copies `List<PixelColor>` and queries row-major | area-sized object/reference ownership and equality cost |
| Mutable surface | `PixelSurface` converts `pixelCount` to `Int` and builds `MutableList<PixelColor>` | allocation safety depends on snapshot construction rather than a product canvas policy |
| Stroke | `Stroke` owns an arbitrary non-empty path list | duplicate and no-op raw samples can be unbounded independently of unique changes |
| Patch | `PixelPatch.create` sorts materialized candidate changes; `inverse()` maps a second list | sort/allocation occurs without a product cap and inverse storage is duplicated |
| ChangeSet/history | `ChangeSet` retains forward and materialized inverse patches; gateway has one-level history | entry count alone cannot justify retained-memory bounds |
| Projection | `toRenderedPixels` builds one `RenderedPixel` per canvas pixel; canvas draws every item | host core allocation misses area-sized presentation objects and frame cost |

The current product fixture uses opaque white blank and opaque red drawing in Android
composition. That is fixture evidence only; it is not an accepted alpha, blank, eraser, or
composition contract.

## Pre-fixed measurement contract

The following pass conditions are fixed before choosing a representation or hard product limit.
They apply only at explicitly measured workload candidates and do not themselves define a
canvas, patch, stroke, or history maximum.

| Condition | Pre-fixed target |
| --- | --- |
| Maximum-workload core latency | p95 <= 8.0 ms and p99 <= 16.67 ms |
| Affected-frame timing | p95 meets the device frame deadline and p99 overrun <= 16.67 ms |
| Command to visible correctness | command start to first correct frame p95 <= 33.33 ms |
| Steady ART live heap | render projection plus retained analytical history <= 50% of `Runtime.maxMemory` |
| Peak headroom | steady live heap plus operation p95 allocation <= 70% of `Runtime.maxMemory` |
| Blocking GC | zero increment for at least 95% of measured operations |
| PSS median | five-checkpoint median <= baseline + 50% of `memoryClass` |
| PSS individual checkpoint | every checkpoint <= baseline + 60% of `memoryClass` |
| Post-GC churn | after cap rejection and ten undo/redo-equivalent cycles, <= max(1 MiB, 1% of heap limit) |
| Correctness and stability | zero OOM, ANR, untyped exception, state mismatch, patch/inverse mismatch, affected/unaffected-pixel mismatch, or revision mismatch |

A candidate fails if any applicable condition fails. Results are not interpolated. A largest
passing measured candidate causes the matrix to be extended before a maximum is claimed.

### Final physical collection protocol

The twenty-sample command run above qualifies the physical route but does not supply final p99
tail evidence. Before any representation or hard-limit selection, final command and affected-
frame batches use five warmups and 200 measured samples per reported maximum workload. The
nearest-rank p95 and p99 are computed from the 200 raw rows; no outlier is discarded.

PSS decision evidence uses five independent instrumentation invocations and preserves one
run-indexed raw artifact per invocation. The final Android harness records thermal status and
active display mode before measured samples, after every 25 measured samples, and after the final
sample in the same raw CSV. A batch is invalid if the display mode changes, any checkpoint has
device-wide thermal status above 1, or the frame listener reports a dropped callback. Power-
saving mode remains disabled and the device remains USB powered and awake. No process kill,
network mutation, or device reset is introduced between checkpoints unless the entire five-
checkpoint protocol is restarted and the change is recorded.

The physical display is active at 90 Hz. The frame instrumentation uses the per-frame
platform `FrameMetrics.DEADLINE`, not a substituted 16.67 ms period. The separate p99 overrun
allowance remains 16.67 ms as pre-fixed above. Its CSV calls the direct boundary the first exact
app-issued frame, links exact `EditorRenderState` and a post-content draw generation to copied
frame metrics, and rejects unavailable timing fields or listener drops. Compose image capture
verifies the resulting pixels outside the timed interval.

An app-issued frame is not proof of physical SurfaceFlinger presentation. Final evidence for the
existing command-to-visible-correctness wording must additionally correlate the recorded API 36
frame-timeline vsync ID with a retained Perfetto/FrameTimeline artifact. Until that correlation is
present, app-issued timing is useful physical renderer evidence but cannot satisfy the visible-
frame acceptance condition by itself.

### Required workload matrix

- square and rectangular canvases, with axis and total-pixel candidates varied separately;
- blank plus one color, 256 colors, and deterministic high-entropy RGBA;
- sparse one-pixel, diagonal, row/column, 25%, 50%, and 100% dense serpentine changes;
- duplicate raw path, changed eraser-equivalent, blank/no-op eraser-equivalent, and same-color
  no-op;
- patch create, apply, invert, late conflict, and exact forward/inverse round trip;
- analytical retained entries at 0, 1, 8, 16, 32, and 64, with retained changes at N, 2N, 4N,
  and 8N without introducing production multi-step history;
- render projection and a representative correct-frame observation; and
- max-minus-one, max, max-plus-one, rectangular product overflow, pre-allocation rejection,
  shuffled ordering, and maximum patch/history interaction.

Every reported sample asserts deterministic pixels, canonical ordering, bounds, revisions,
inverse round trip, affected region, unaffected pixels, and atomic no-op or rejection behavior.

## P2 raw artifact contract

Current-main host measurements must use the separately named Gradle route
`measureP2RepresentationLimits`, a schema beginning `nene-pixel-p2-representation-limits-`, and
metric names that do not reuse `translated_controller_commit` or the P2 viewport transform
boundary. Planned ignored paths are fixed as follows before collection:

| Planned path relative to current repository | Required content |
| --- | --- |
| `build/reports/p2/representation-limits/host-current.csv` | current canonical host metrics and correctness columns |
| `build/reports/p2/representation-limits/host-candidates.csv` | analytical candidate metrics with candidate identity |
| `build/reports/p2/representation-limits/device-core.csv` | physical ART latency, allocation, GC, live-heap, and correctness observations |
| `build/reports/p2/representation-limits/device-memory-run-01.csv` through `device-memory-run-05.csv` | one immutable PSS/retained/post-GC checkpoint invocation each; individual checksums required |
| `build/reports/p2/representation-limits/device-memory.csv` | deterministic aggregate over the five run-indexed memory artifacts |
| `build/reports/p2/representation-limits/device-frames.csv` | frame deadline, overrun, and command-to-first-correct-frame observations |
| `build/reports/p2/representation-limits/device-profile.txt` | exact physical profile fields and their sources |
| `build/reports/p2/representation-limits/device-logcat.txt` | scoped fatal, ANR, GC, thermal, and measurement logs |

After each final collection, this document must add byte length and SHA-256 for every raw file.
An artifact is not decision evidence if its metric boundary, commit, candidate, workload, sample
count, or profile cannot be recovered from the file and this ledger.

## Named physical minimum profile

This profile was fixed from read-only device queries before the first physical instrumentation
measurement. Each value says whether it was reported by the device/tool or fixed by this
measurement protocol. The device's hardware serial is intentionally not retained in evidence.
This is the selected M2 performance-reference device, not evidence of the supported Android API
floor or a claim that every lower-memory device is supported. Its UMS9360 application behavior
is the reference used for the representation decision; compatibility floors remain governed by
the Android configuration and separate supported-device verification.

| Field | Value | Source / reported or inferred |
| --- | --- | --- |
| Stable profile ID | `NENE-P2-ALLDOCUBE-IPL80MP-A16-API36` | fixed by this ledger before instrumentation |
| Manufacturer and model | ALLDOCUBE iPlay80miniPro; product/device `iPlay80miniPro` / `T830` | `getprop` reported |
| SoC and ABI | Spreadtrum UMS9360 (`ums9360`); `arm64-v8a` | `getprop ro.soc.*`, `ro.board.platform`, and `ro.product.cpu.abilist` reported |
| Physical RAM | 7,937,848 kB reported by kernel (approximately 7.57 GiB usable) | `/proc/meminfo` `MemTotal` reported |
| Android release, API, and build fingerprint | Android 16; API 36; `ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94010:user/release-keys`; security patch 2026-05-05 | `getprop` reported |
| ART/runtime | ART 2.1.0 arm64 on a `user` build; heap growth limit 256 MiB, heap size 512 MiB, heap start 16 MiB | `dalvikvm -showversion` and `getprop dalvik.vm.*` reported; exact test-process values are emitted before workload sampling |
| App variant and commit | `debug` plus `debugAndroidTest`; `5b9675a5a77186c9ce5e5095bc88b5a485ad18fc` | Git and Gradle route reported |
| Display resolution and refresh rate | physical 1200 x 1920; active/supported mode 90 Hz; physical density 320 dpi with 272 dpi override | `wm` and `dumpsys display` reported |
| `memoryClass` and `Runtime.maxMemory` | 256 MiB and 268,435,456 bytes | `ActivityManager.memoryClass` and `Runtime.maxMemory()` reported in physical CSV metadata before workload samples |
| Power mode | awake; low-power mode disabled; USB powered | `dumpsys power`, `settings get global low_power`, and `dumpsys battery` reported at pre-run query |
| Battery level and charging state | 69%; USB powered/charging; battery temperature 30.7 C | `dumpsys battery` reported at 2026-09-01 19:10 JST |
| Thermal status before/during/after | pre-run overall status 1, skin 37.482 C, SoC 40.08 C; post-run overall status 1, skin 37.827 C, SoC 38.365 C; all named sensor statuses 1 | `dumpsys thermalservice` reported at 2026-09-01 19:10 and 19:18 JST; the preliminary CSV does not contain an in-run checkpoint |
| Background-process and network conditions | unconstrained user background/network state; no process kill, network mutation, or device reset; therefore preliminary screening only | reported non-invasive condition; final validity conditions are fixed in the protocol above |
| Warmup, sample, restart, and GC protocol | preliminary screening: five warmups and twenty samples; final tail batches: five warmups and 200 samples; a fresh gateway per sample; two explicit Java GC/finalization passes only before post-GC memory capture | preliminary route fixed before its run; expanded final protocol fixed before candidate or frame collection |
| Connected transport and measurement tools | one physical device over USB ADB; AndroidJUnitRunner; `Debug.getRuntimeStats`, `Debug.getMemoryInfo`, `Runtime`, `ActivityManager`, `dumpsys`, and host SHA-256 | device/tool reported and protocol-fixed |

## Current blocker

Host, emulator-smoke, and preliminary physical command evidence are currently available. The
physical screening does not include the complete workload matrix, retained-history memory, five
independent PSS checkpoints, or compositor correlation. The corrected affected-frame batch is
valid and fails the pre-fixed timing conditions for the current dense 256-square route; its
timeline IDs still require Perfetto correlation before any visible-frame claim.
The command screening's twenty samples per workload are also insufficient final p99 tail
evidence. The emulator remains useful only for functional interaction checks and cannot fill any
of those physical gaps.

Therefore:

- no representation candidate has been selected;
- no canvas, raw-stroke, patch, history-entry, or retained-change number is accepted;
- the pre-fixed target table is not a production contract;
- ADR 0005 must remain `proposed`; and
- P2-02 and representation-dependent P2-04 work must not treat any analytical fixture as a
  supported limit.

## Completion record template

Complete this section only after all required collection succeeds:

| Decision input | Selected evidence |
| --- | --- |
| Accepted semantic contract | pending |
| Accepted storage candidate | pending |
| Accepted axis and total-pixel limits | pending |
| Accepted raw-stroke and patch limits | pending |
| Accepted history-entry and retained-change limits | pending |
| Largest measured passing candidates | pending |
| Physical profile ID | `NENE-P2-ALLDOCUBE-IPL80MP-A16-API36`; identity fixed, complete evidence pending |
| Raw artifact checksum set | pending |
| Correctness result | pending |
| ADR 0005 acceptance commit | pending |

Related canonical evidence: [M1 Vertical-slice Baseline and Exit Proof](M1_EXIT_PROOF.md).

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
| Current-main P2 representation route | current canonical host route and preliminary physical command screening complete | incomplete; full matrix, renderer, frames, and retained-history evidence required |
| Analytical storage candidates | current object-graph structure rows recorded; packed candidates not yet measured | incomplete and required |
| Named emulator | ART command harness complete in explicit auxiliary mode | cannot satisfy physical evidence |
| Named physical minimum Android device | profile fixed and preliminary command screening recorded below | valid physical starting evidence; complete physical matrix still blocking |
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

The separately named current-main route was run from branch `adr/38-pixel-color-limits` with the
following command:

```powershell
.\gradlew.bat measureP2RepresentationLimits --rerun-tasks --no-build-cache
```

Result: final recorded rerun success in 12 seconds in one 512 MiB worker. Schema
`nene-pixel-p2-representation-limits-host-current-v1` contains 17 metric summaries and 146 raw
samples. Schema `nene-pixel-p2-representation-limits-host-candidates-v1` contains six typed
analysis rows. Every executed sample retained its exact snapshot, revision, patch/inverse,
full-canvas affected region, unaffected pixels, history availability, and typed no-op assertions.

The diagnostic route uses five warmups and ten samples for standard rows and three warmups and
seven samples for dense/history rows. Its p99 is therefore the observed maximum, not sufficient
physical-profile p99 evidence.

| Raw path | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/host-current.csv` | 38,455 | `A470F2D186F93993957E89DD93F74E4B55E16FB25F1B9D145BBD902DDDB2CB87` |
| `build/reports/p2/representation-limits/host-candidates.csv` | 3,186 | `3EFE2DE8FC2FF4C0BB7349F4FE32ACE997350EDB11FAAA485F8C90A36FCA05C7` |

Selected observations show why this run cannot authorize current limits:

| Workload | Canvas / changes | Host p95 ns | Host p99 ns | p95 allocated bytes |
| --- | --- | ---: | ---: | ---: |
| sparse pencil-equivalent | 1024 x 1024 / 1,024 | 21,175,200 | 21,175,200 | 8,591,792 |
| dense pencil-equivalent | 256 x 256 / 65,536 | 30,082,100 | 30,082,100 | 13,196,248 |
| dense eraser-equivalent | 256 x 256 / 65,536 | 22,886,300 | 22,886,300 | 13,196,248 |
| dense same-color no-op | 256 x 256 / 0 changes | 4,335,900 | 4,335,900 | 4,670,280 |
| dense forward patch apply | 256 x 256 / 65,536 | 2,828,700 | 2,828,700 | 524,480 |

These are HotSpot current-thread observations. They exclude retained heap, renderer work, ART,
PSS, and frames. The candidate CSV records current logical reference/change counts and explicitly
excludes unexecuted 512-square and 2048-square dense rows, a 50,000-square non-indexable current
canvas, and a 256-square / 32-entry retained workload. It does not yet compare flat packed or
tiled/COW implementations.

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

The physical display is active at 90 Hz. The required frame instrumentation will use the per-frame
platform `FrameMetrics.DEADLINE`, not a substituted 16.67 ms period. The separate p99 overrun
allowance remains 16.67 ms as pre-fixed above. Its CSV must call the direct boundary the first
exact app-issued frame, link exact `EditorRenderState` and a post-content draw generation to
copied frame metrics, and reject unavailable timing fields or listener drops. Compose image
capture must verify the resulting pixels outside the timed interval.

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
physical screening does not include the complete workload matrix, renderer/retained-history
memory, five independent PSS checkpoints, or frame/compositor evidence. Its twenty samples per
workload are also insufficient final p99 tail evidence. The emulator remains useful only for
functional interaction checks and cannot fill any of those physical gaps.

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

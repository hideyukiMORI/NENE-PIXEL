# M1 Vertical-slice Baseline and Exit Proof

Status: complete. Local implementation and exit evidence were verified on 2026-09-01 for `P1-08` / Issue #25. PR #36 passed required CI and merged to `main`; Issue #25 and the M1 milestone were then closed and read back with zero open M1 Issues.

## Measurement route

The historical M1 interaction boundary is immutable at merge commit `37c0f57d59a73fd962c285e79e8e193a81402d31`. Reproduce it from an isolated worktree so later replacement of the fixed translator cannot silently change the meaning of an M1 metric:

```powershell
git worktree add ..\NENE-PIXEL-m1-proof 37c0f57d59a73fd962c285e79e8e193a81402d31
Push-Location ..\NENE-PIXEL-m1-proof
.\gradlew.bat measureM1VerticalSlice --rerun-tasks --no-build-cache
Pop-Location
```

The route is owned by Gradle at that commit and uses the existing JUnit engines and project dependencies only. From ADR 0004 onward, current `main` MUST use the separately named P2 viewport measurement route and metric boundary; `measureM1VerticalSlice` and `translated_controller_commit` MUST NOT be reused with different mapping semantics.

The root task runs one isolated 512 MiB test worker and selects two normal test classes. `M1CoreMeasurementTest` measures snapshot construction, the canonical `rasterizeStroke` patch builder, and `CommandGateway.execute` for apply, undo, and redo. `M1InteractionMeasurementTest` measures offset translation, gesture preview reduction, one gateway command, and render-state projection. Using one worker keeps the JVM, heap, fork, and allocation-counter conditions identical across the core and interaction rows.

The presentation-owned measurement task composes the already compiled core and presentation test outputs and runtime classpaths for that worker. This is test execution wiring only: it declares no new project or external dependency, adds no production module edge or public API, and changes no dependency lock or verification metadata. Both classes remain part of their modules' normal test suites; the dedicated task only adds fixed measurement configuration and raw output.

The task writes ignored raw CSV files to `build/reports/m1/core-measurement.csv` and `build/reports/m1/interaction-measurement.csv`. Each file records its schema, profile, JVM, variant, warmup, sample count, canvas sizes, metric boundary, nanosecond observations, and current-thread allocation observations.

## Named profiles and workload

| Item | Recorded value |
| --- | --- |
| Host profile ID | `NENE-M1-WINDOWS-I9-10850K-JBR21` |
| Host OS | Windows 11 Pro 10.0.26200, amd64 |
| Host CPU | Intel Core i9-10850K, 10 cores / 20 logical processors, 3.60 GHz base |
| Host visible physical memory | 66,969,724 KiB reported by Win32 `TotalVisibleMemorySize` |
| Gradle runtime | JBR 21.0.11 (`21.0.11+1-b1163.116`) |
| Wrapper | Gradle 9.7.1 |
| Test worker | JVM 17 bytecode on JDK 21, `-Xms512m -Xmx512m`, one fork |
| Measurement variants | `core-jvm-test-output-in-presentation-debug-host-worker` and `presentation-debug-host-unit-test-worker` |
| Android build variant | `debug` |
| Smoke device | `Pixel_8_Pro_API_35` AVD, Pixel 8 Pro, Android 15 / API 35, x86_64, 1344 x 2992 |
| Warmup and samples | 20 unreported warmup operations, then 50 reported samples per metric |
| Canvas fixtures | 16 x 16, 64 x 64, and 256 x 256 |
| Stroke fixture | one ordered top-left to bottom-right diagonal; position count equals the canvas edge |

The 16 x 16 canvas is the only M1 product slice. The 64 x 64 and 256 x 256 canvases are analysis fixtures chosen to expose area-copy and sparse-patch scaling. They are not supported-size claims, minimums, maximums, or proposed production limits.

## Metric boundaries

| Metric | Included | Excluded |
| --- | --- | --- |
| `snapshot_create` | `PixelSnapshot.create` validation and defensive row-major ownership copy | input-list construction |
| `patch_create` | canonical `rasterizeStroke`, `PixelChange` discovery, canonical ordering, and `PixelPatch` creation | document transition and next snapshot |
| `command_apply_stroke` | gateway synchronization, validation, rasterization, patch application, next snapshot, complete `ChangeSet`, one-level history, and state commit | command/fixture construction |
| `command_undo` | gateway dispatch and recorded inverse-patch transition, result, history move, and state commit | prior stroke setup and command construction |
| `command_redo` | gateway dispatch and recorded forward-patch transition, result, history move, and state commit | prior apply/undo setup and command construction |
| `translated_controller_commit` | `Offset` translation, preview begin/extend, commit preparation, exactly one gateway apply, and render projection | Compose pointer dispatch, Canvas draw, frame scheduling, GPU/compositor work |

Latency is `System.nanoTime` elapsed time inside the named host test worker. Allocation is HotSpot `ThreadMXBean.getThreadAllocatedBytes` for the executing test thread only. It is not retained heap, live-set size, process RSS/PSS, Android ART allocation, or allocation performed by another thread. Fixture construction is outside both measurements. The observations include normal JIT, GC, scheduler, CPU-frequency, and host-load noise; median and p95 describe this run and are not pass/fail thresholds.

## Baseline observations

The following values are from the documented task on 2026-09-01. Latency is recorded in nanoseconds and allocation in bytes so the committed evidence matches the raw report without rounding.

| Canvas | Metric | Stroke positions | Median ns | p95 ns | Median allocated bytes | p95 allocated bytes |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 16 x 16 | snapshot create | 0 | 800 | 1,400 | 1,152 | 1,152 |
| 16 x 16 | patch create | 16 | 17,100 | 40,600 | 3,352 | 3,352 |
| 16 x 16 | command apply stroke | 16 | 28,500 | 58,400 | 16,552 | 16,552 |
| 16 x 16 | command undo | 16 | 10,100 | 20,100 | 13,208 | 13,208 |
| 16 x 16 | command redo | 16 | 8,200 | 28,300 | 13,184 | 13,184 |
| 16 x 16 | translated controller commit | 16 | 76,000 | 193,800 | 23,088 | 23,088 |
| 64 x 64 | snapshot create | 0 | 2,100 | 13,200 | 16,512 | 16,512 |
| 64 x 64 | patch create | 64 | 27,400 | 92,200 | 12,544 | 12,544 |
| 64 x 64 | command apply stroke | 64 | 69,700 | 123,900 | 47,568 | 47,568 |
| 64 x 64 | command undo | 64 | 42,600 | 72,000 | 35,032 | 35,032 |
| 64 x 64 | command redo | 64 | 39,700 | 69,800 | 35,008 | 35,008 |
| 256 x 256 | snapshot create | 0 | 56,500 | 88,100 | 262,272 | 262,272 |
| 256 x 256 | patch create | 256 | 50,700 | 184,200 | 44,800 | 45,024 |
| 256 x 256 | command apply stroke | 256 | 645,700 | 1,182,200 | 576,584 | 576,616 |
| 256 x 256 | command undo | 256 | 465,200 | 558,700 | 531,888 | 531,888 |
| 256 x 256 | command redo | 256 | 493,100 | 766,100 | 531,864 | 531,864 |

Every warmup and reported sample also asserts correctness. Snapshot creation equals the expected immutable value; the rasterized patch has the expected change count and applies to the exact next snapshot; apply commits the exact state; undo uses the recorded inverse and restores the exact initial pixels and revision; redo uses the recorded forward patch and restores the exact applied pixels and revision. Repeated inputs must equal the first complete result. The translated-controller result, gateway state, render snapshot, preview clearing, and history availability must equal an independently executed direct gateway command.

The results make no statement that one canvas or operation is fast enough. In particular, the non-monotonic latency samples show why this host-JVM measurement is descriptive evidence rather than a performance budget.

## M1 exit review

| Exit criterion | Evidence | Result |
| --- | --- | --- |
| Compose, test-adapter, and direct paths agree | `UndoRedoEditorTest` drives the real Compose surface and controls through draw/undo/redo; `FixedSliceEditorControllerTest` compares translated touch with reducer-plus-gateway execution; `M1InteractionMeasurementTest` compares every translated result and exact state with an independent direct gateway command | pass |
| Command replay is deterministic | `CommandGatewayHistoryTest` replays apply/undo/redo twice; both measurement tests compare every repeated complete result | pass |
| Rejected or out-of-bounds strokes are typed atomic no-ops | `StrokeTest` covers typed factory rejection before an invalid stroke can become a command; `CommandGatewayTest` covers command rejection categories and unchanged state; controller and workspace tests cover outside/cancel/invalid gesture transitions | pass |
| Patch apply/invert round-trips | `PixelPatchApplicationTest`, `PixelPatchCreationTest`, and `CommandGatewayHistoryTest` prove forward/inverse exact pixels and recorded revisions | pass |
| UI has no direct document or pixel-buffer mutation | `:presentation:compose` depends on application/domain projections, receives immutable state plus callbacks, and has no pixel-engine dependency; architecture validation and exhaustive gateway/reducer tests preserve the only mutation paths | pass |
| `QLT-007` and relevant `QLT-008` pass | apply/undo/redo valid and typed rejection matrices, rejection atomicity, complete change sets, unaffected-pixel checks, deterministic rasterization, and patch round-trips run under root `check` | pass |
| `QLT-006` through `QLT-010` remain satisfied | measurement behavior carries tests; no schema exists for `QLT-009`; this proof supplies reproducible named-profile evidence for `QLT-010` without an exception or unsupported claim | pass |

## Verification

```powershell
# Run from the isolated 37c0f57 worktree described above.
.\gradlew.bat measureM1VerticalSlice --rerun-tasks --no-build-cache
.\gradlew.bat :core:application:ktlintCheck :core:application:detekt :presentation:compose:ktlintCheck :presentation:compose:detekt
.\gradlew.bat check :app:android:assembleDebug --stacktrace
```

The final measurement run completed 22 tasks successfully in 8 seconds. Focused tests, ktlint, detekt, documentation validation, and architecture validation passed without suppression or baseline. A fresh no-build-cache canonical check and debug build executed all 183 considered tasks successfully in 1 minute 7 seconds. After the final measurement-harness and proof corrections, the exact documented canonical command considered 179 tasks, executed the seven changed tasks, reused 172 verified outputs, and succeeded in 8 seconds.

### Emulator draw, undo, and redo smoke

The `Pixel_8_Pro_API_35` AVD was cold-booted with snapshot loading and saving disabled, without wiping its data. `:app:android:installDebug` installed the current debug APK, and `am start -W` reported `Status: ok`, `LaunchState: COLD`, the expected `MainActivity`, and a 2,974 ms launch time.

One input swipe drew six visible red diagonal pixels. The UI hierarchy changed from both history controls disabled to Undo enabled and Redo disabled. Tapping Undo restored a blank canvas and changed availability to Undo disabled and Redo enabled. Tapping Redo restored the visible stroke and the original history availability. The drawn and redone screenshots had the same SHA-256, `10F4268DBB84D7081CBD38C0870052B592AFDC40D046C6C009E7ACCA7E82DA15`. The activity remained `topResumedActivity`, and the launch-through-redo log contained zero application fatal lines.

Ignored local evidence is under `app/android/build/smoke/issue25/`: `before.png`, `drawn.png`, `undone.png`, `redone.png`, the matching UI hierarchy XML files, and `app-logcat.txt`.

## P2-01 constraints carried forward

- P2-01 must evaluate immutable snapshot storage, semantic color representation, and document limits together; it must not infer a product limit from the three analysis fixtures.
- Sparse diagonal patches still cause area-sized next-snapshot allocation. Candidate storage decisions must separate area-owned snapshot cost from change-count-sized patch cost and re-run the same correctness assertions.
- A physical named minimum Android profile is required before accepting a user-visible latency or memory budget. Emulator smoke and host-JVM measurements are not substitutes for ART, retained heap, PSS, frame, or compositor evidence.
- A P2 benchmark set must add representative dense and tool-specific workloads before deciding worst-case canvas, patch, or multi-level-history limits.
- Any decision that changes ownership, public module APIs, canonical mutation flow, or the accepted representation contract requires the P2-01 ADR before implementation. This baseline authorizes no such change.

## External closure evidence

At the start of P1-08, GitHub milestone 2 had two open Issues: #24 and #25. After PR #35 merged, #24 closed and #25 became the only open M1 Issue. PR #36 then passed required `quality` run `33477940423` and merged by squash as `37c0f57d59a73fd962c285e79e8e193a81402d31`. The merge closed Issue #25. The milestone API was read back with `open_issues: 0` and `closed_issues: 10` before milestone 2 was closed at `2026-09-01T06:40:33Z`; its final state was read back as closed with zero open Issues. The subsequent `main` push `quality` run `33478612479` also succeeded.

Active waivers: none.

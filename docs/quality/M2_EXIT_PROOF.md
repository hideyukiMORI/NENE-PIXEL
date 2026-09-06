# M2 Core Drawing Exit Proof

Status: final integration candidate for `P2-07` / Issue #44. The scoped correctness, command,
history, viewport, process-recreation, and actual-app frame gates have passed. M2 remains open until
the exact stacked debug artifacts pass the final 9+1 emulator smoke, required CI passes, and the
merge, Issue, and milestone state is read back.

## Scope and authority

This proof reviews the non-persistent M2 editor delivered by Issues #38 through #43. The governing
behavior remains in the project charter, architecture constitution, command model, accepted ADRs
0003 through 0009, and the fixed `PixelLimits` policy. This record adds no state owner, mutation
route, product limit, persistence behavior, dependency, module, public API, or waiver.

The accepted journey is launch, valid and invalid new-document creation, palette selection, Pencil,
Eraser, pan/zoom/grid behavior, cancellation, multi-step undo/redo, and clean/dirty indication.
Save/load/autosave, project format, PNG export, and restoration of an unsaved document after process
death remain M3 work.

Active waiver: none.

## Change scope and verification policy

Issue #44 changes only this evidence record and focused test evidence. `QLT-011` through `QLT-016`
apply. A successful result is reused when the behavior, relevant implementation blobs, environment,
and assertion remain equivalent. A different aggregate Git revision is not by itself an
invalidation reason. Performance, correctness, retained memory, and frame observations remain
separate lanes.

The full canonical check/build is reserved for the final PR candidate and may be satisfied by its
required CI result. This integration uses documentation validation, affected compilation/static
checks, one normal-default debug app/AndroidTest build, and one predeclared 9+1 emulator smoke. It
does not regenerate P62 or repeat the exhausted physical frame experiment.

## Correctness acceptance matrix

| Requirement | Canonical evidence | Identity / current disposition |
| --- | --- | --- |
| Direct application command, controller adapter, pixels, revisions, `ChangeSet`, history, dirty state | `EditorRuntimeTest`, `CommandGatewayHistoryTest`, `CommandResultContractTest`, `ViewportEditorControllerTest` | Core runtime, gateway, controller, and adapter production blobs are unchanged from their accepted focused evidence; reuse under `QLT-012` |
| Zoom, pan, density, resize, grid, rectangular canvas, half-open edges, maximum canvas | `ViewportTransformGestureTest`, `ViewportTransformMappingTest`, `ViewportTransformPropertyTest`, `ViewportValueTest` | Viewport production and test blobs are unchanged; reuse under `QLT-012` |
| Compose journey: create/reject/cancel, palette, Pencil/Eraser/no-op, two-pointer cancellation, multi-step undo/redo, dirty state | `UndoRedoEditorTest` (9 tests) | Test blob `2efa845ed6b9e826523a127766a088fc5475bd83` is identical at accepted #77 source `7b512d0b8d75677b14d15d2bd4515ae5f0f22dc0` and frozen #76 head `b5edfd9385bcb0db2b74ef73e7369d0c22b44712`; the #44 stack does not edit it or its production presentation inputs. Its final 9-test execution remains part of the exact stacked-artifact emulator smoke |
| Configuration recreation retains the sole owners | `EditorRuntimeLifecycleTest.configurationRecreationRetainsTheOnlyDocumentAndWorkspaceOwners` | MainActivity, ViewModel, runtime, and the existing configuration-test method/assertions are unchanged; reuse the recorded PASS |
| New process creates one canonical initial runtime and `WorkspaceState` owner | host PID transition plus `EditorRuntimeLifecycleTest.processStageHasOneCanonicalInitialRuntimeAndWorkspaceOwner` | Recorded PASS on test source `14c7efe15ad696c695c7f97d2a92561fb506ee11`; see [Process recreation](#process-recreation) |
| Validated maximum new-document boundary | `EditorRuntimeLifecycleTest.emulatorNewDocumentCreationSmokeUsesValidatedMaximumBoundary` | The `+1` in the final emulator 9+1 smoke; process-recreation evidence remains a separately reused physical-device result |

The matrix is the integrated acceptance proof. No duplicate synthetic journey test is added while
these direct, adapter, and Compose assertions cover the same canonical results.

## Process recreation boundary and protocol

Process recreation means a new Android process creates exactly one application-owned
`EditorRuntime`, `EditorController`, and `WorkspaceState` owner in the scope-defined initial state:
16 x 16 blank document, revision zero, empty history, clean dirty state, Pencil, first palette entry,
and canonical initial viewport. It does not mean restoration of an unsaved M2 document.

`ActivityScenario.recreate()` proves configuration recreation only and is not process-recreation
evidence. The process check is one host-orchestrated two-stage invocation against the selected
device and one installed test APK identity:

1. Cold-launch `io.github.hideyukimori.nenepixel/.MainActivity`, wait for a stable PID, and record it
   as `before_pid`.
2. Run `adb shell am force-stop io.github.hideyukimori.nenepixel` and require `pidof` to return no PID.
3. Invoke only
   `io.github.hideyukimori.nenepixel.EditorRuntimeLifecycleTest#processStageHasOneCanonicalInitialRuntimeAndWorkspaceOwner`
   through `androidx.test.runner.AndroidJUnitRunner`, passing `before_pid` as instrumentation
   argument `m2PriorProcessId`. The Compose activity rule cold-launches `MainActivity` in the new
   instrumented target process. The argument is optional for ordinary focused test execution, but
   is mandatory for process-recreation acceptance; when present, malformed, zero, and negative
   values fail the test. Require the runner raw output to contain the `Instrumentation.sendStatus`
   Bundle field `m2ProcessStagePid`, require that positive PID to differ from `before_pid`, and
   require this one test and all owner and initial-state assertions above to pass.
4. Retain source revision, app APK SHA-256, test APK SHA-256, device serial/profile, all observed
   PIDs, exact commands, and raw instrumentation output together. Failure at any transition makes
   the process-recreation criterion fail; a configuration recreation PASS cannot replace it.

Acceptance requires all of the following in one retained record: the mandatory valid
`m2PriorProcessId` argument, the matching pre-stop PID, no package PID after force-stop, a different
positive `m2ProcessStagePid` from the runner status Bundle, and a PASS for exactly the process-stage
test. A partial record is not process-recreation evidence.

Attempt 01 on source `14c7efe15ad696c695c7f97d2a92561fb506ee11` is retained as
`INVALID_PARTIAL`. It proved a positive prior PID, a different positive runner-status PID, and the
single test PASS, but its host wrapper discarded the native `pidof` exit code and separate stderr.
Empty stdout alone cannot distinguish no process from an adb transport or shell-command failure,
so the attempt is not accepted and no PASS is inferred from it.

The reviewed strict host boundary requires a successful initial `adb get-state`, force-stop exit
zero with empty stderr, then `pidof` exit one with empty stdout and stderr, followed by a successful
`get-state`. Every subprocess records stdout, stderr, exit code, and timeout separately. A timeout
retains partial output, kills the bounded adb subprocess, force-stops the package, and records a
successful device connection plus absent package PID before releasing the device. Only one fresh
functional attempt is allowed for the corrected evidence identity.

The pre-execution proposal placed another `get-state` between the acceptance force-stop and
`pidof`. The executed wrapper did not perform that redundant call: the immediately preceding
successful adb-shell force-stop established command transport, the native `pidof` exit one plus
empty stdout/stderr established no matching process, and the immediately following successful
`get-state` established the continuing device connection. Review accepted this recorded order as
proving the same transport distinction; no repeat was performed merely to add the redundant call.

The test uses only existing test access to `MainActivity` and `ViewModelProvider`. It adds no
production probe, saved-state owner, or recovery path.

## Performance and limit lanes

Historical Issue #44 command and retained-history measurements from source `53841ff` remain useful
diagnostic evidence, but their production-tree equality alone is not sufficient for reuse under
`QLT-012`. The final review gives each evidence input an explicit disposition:

| Identity input | Recorded comparison | Disposition |
| --- | --- | --- |
| Production core | `:core:application/src/main` tree `71493da4f4ed90ba4b1d5860d9c60cb36fc2bcc4`, `:core:domain/src/main` tree `32e34d1f3c0b700ba7195c83fabd71bb8f828585`, and `:core:pixel-engine/src/main` tree `eefbe39b1398c5c634b2cc5e907e409f6aeb80fb` are identical at `53841ff`, measured non-frame source `3a4034f`, accepted #76 head `b5edfd9`, and the #44 stack | Reuse semantic/core correctness and the accepted core-only non-frame results; presentation and whole-app behavior are excluded |
| Core compile inputs | Gradle wrapper, `gradle.properties`, all three core build scripts, and `AndroidComposePlugin.kt` are identical; AGP 9.4.0, Kotlin 2.4.10, Compose BOM 2026.08.00, Activity Compose 1.13.0, and JUnit 6.1.2 remain fixed | No core compilation-input invalidation found |
| #62 build additions | #62 adds the Baseline Profile plugin/module/dependencies and generated profile to app packaging; the added benchmark/UI Automator dependencies belong to the new quality module and do not enter the three core runtime classpaths | Core correctness and host-core compilation remain equivalent; final APK and device compilation identity are changed inputs |
| Old M2 command artifact | `nene-pixel-m2-android-command-latency-v1`, debug/debugAndroidTest, six ordered workloads, 5 warmups plus 200 samples, physical profile `NENE-P2-ALLDOCUBE-IPL80MP-A16-API36`, raw SHA-256 `CF0B64A1FFB15AF507F44001699AFF6C3BA8EA26186EF29BDF65FA3AF71105CA` | Not accepted as final latency, ART allocation, or GC evidence |
| Command harness | #56 separated one correctness execution from latency fixtures. The old harness built and compared a complete expected `DocumentState` and computed document/snapshot hashes after every warmup and measured execution. These operations were outside each timer but changed allocation, cache, and GC state before later samples | `QLT-014` observer-effect correction invalidates the old population. The old CSV also has no exact app/test APK hashes or declared package compilation mode required by `QLT-012` |
| Retained history | Five `nene-pixel-m2-production-history-v1` files identify source, profile, 64 entries, and 524,288 changes; the subject is a retained `CommandGateway`, not Compose | Semantics and Java-heap meaning remain useful, but final acceptance reuse is not established: exact app/test APK hashes, toolchain invocation, and compilation state are absent from the retained records |
| Host viewport | Raw schema `nene-pixel-p2-viewport-measurement-v1`, profile `NENE-P2-WINDOWS-I9-10850K-JBR21`, OpenJDK 21.0.11, 20 warmups plus 50 samples, raw SHA-256 `5863665146AB95C0508B112FA00C05F00D5C70F916B15AA1504850A9F69D0BA6` | Not reused as final timing/allocation: its recorded source was `e178130`, while the current measurement-test blob differs and the raw file carries no source revision. Viewport semantic/property tests remain reusable |
| Runtime profile | Existing records establish that debuggable APKs contain no packaged profile, while the release-like actual-app decision lane explicitly installs the packaged profile and compiles `speed-profile` | Baseline Profile changes do not invalidate host/core correctness. They do invalidate release-like actual-app identity. The old debug command artifact did not record an explicit compilation mode, so timing reuse is still prohibited |

The replacement non-frame protocol is fixed at test/document source
`3a4034ffc1292f710308903d0091338a60394882`, layered on production candidate `374ad211`. Issue #70's
frame decision subsequently failed its unchanged absolute gate, so that presentation candidate is not
adopted. The retained historical debug artifact from `3a4034f` includes that rejected P70
presentation. The command and retained-history collectors do not start an Activity or initialize the
Compose presentation tree; they execute the production `CommandGateway` and three core trees
directly. The complete `app/android/src/androidTest` affected-input tree, the three core production
trees, and their core build inputs are byte-identical between `3a4034f` and the #44 stack. Reuse is
therefore limited to those core subjects and does not assert equivalence for UI memory, frame timing, or
presentation behavior. The final integrated UI contains the accepted #77 layout and #76 R8 role
policy, not the rejected P70 presentation. Root reviewed the exact source, artifact, harness, and
finite-budget identity before the recorded collection below:

The physical core lanes use the already-built 11,762,545-byte app APK with SHA-256
`E15684FFF220C669F788ED1898234E02CBAD9D4B87114E59F1B6021A9BE6D57B` and the 1,305,999-byte
AndroidTest APK with SHA-256
`413294CE1E9865676EB65A73B2F8E76A9D8781BC381E7F1F1D8C3207058513F2`. Later documentation-only
commits map to those immutable bytes and do not relabel them. Android treats a
debuggable package as VM safe mode and downgrades a requested `speed` filter to `verify`. The fixed
command/history compilation contract therefore requests `cmd package compile -m verify -f`, saves
the complete dexopt report, uses the canonical target-package block parser, and requires every
reported target status to equal `verify`. It also records the test package dexopt block without
requiring the same filter. The debug/JIT/profile state is part of this scoped identity; these core
numbers do not describe release performance. Actual-app release-like performance is accepted
separately by Issue #76 and ADR 0013 on the fixed post-#77 optimized consumer identity.
The first collection attempt requested `speed`, received command success but actual target
`[status=verify] [reason=cmdline]`, and stopped before any sample. It is retained as
`INVALID_PREFLIGHT`, not a performance result.

| Lane | Fixed current protocol | Decision rule |
| --- | --- | --- |
| Physical command | The sole `P2AndroidFinalCommandMeasurementTest` collector receives candidate `m2-production-command-256-lane-separated-v2`, schema `nene-pixel-m2-android-command-latency-v2`, run 1, and fail-if-present output `m2-production-command-256-lane-separated-run-01.csv`. Ordered 256-square workloads are sparse Pencil, dense Pencil, dense Eraser, same-color no-op, dense Undo, and dense Redo. Each has five untimed warmups and 200 measured samples, for 1,200 rows. One separate correctness execution per workload precedes warmup; latency fixtures do not construct a full expected document. Each sample times the sole `CommandGateway.execute`; cheap result/revision/history/`ChangeSet`/invalidation/no-op checks and ART deltas remain outside that timer. No per-sample full-document scan, hash, forced GC, or report write is permitted. Physical checkpoints occur before sampling, every 25 global samples, and after sampling. | For every workload, nearest-rank p95 <= 8.0 ms and p99 <= 16.67 ms, with no discarded rows; at least 95% of its 200 rows have zero blocking-GC increment. A numeric miss in an otherwise valid population is `PERFORMANCE_FAIL` and ends the finite budget. |
| Retained production history | Five independent instrumentation processes run indices 1 through 5. Each retains the production `CommandGateway` at 64 entries and 524,288 changes, proves the complete 64-step Undo/Redo round trip and ten additional cycles, and records two-pass post-GC baseline, retained, and post-cycle observations. | Every retained Java heap <= 50% of `Runtime.maxMemory`; median paired PSS delta <= 50% of `memoryClass`; every retained PSS <= its baseline plus 60% of `memoryClass`; every post-cycle heap growth <= max(1 MiB, 1% of max heap). No interpolation or discarded invocation. |
| Host viewport | One normal-default Gradle `measureP2ViewportInteraction` batch in a quiescent host window with no concurrent Gradle build or measurement: schema `nene-pixel-p2-viewport-measurement-v1`, profile `NENE-P2-WINDOWS-I9-10850K-JBR21`, debug host worker, 16 x 16 canvas, 1600 x 1600 surface, 2 px/dp, 20 warmups, and 50 samples. The canonical controller transform is timed; correctness stays outside timing. | Descriptive median/p95 latency and current-thread HotSpot allocation only; the accepted protocol defines no numeric viewport gate. Identity, row-count, deterministic-result, or raw-report failure invalidates the batch. |

The final host record binds the stacked source revision, exact debug app and AndroidTest APK
byte lengths and SHA-256 values for physical lanes, Gradle/JDK/AGP/Kotlin/dependency identity,
explicit package compilation mode, exact serial/profile/fingerprint/security patch, native command
exit codes, stdout, stderr, finite timeouts, raw byte lengths, and raw SHA-256 values. History uses
one batch ID, distinct PID/process-start pairs, `Runtime.maxMemory`, and `memoryClass` emitted before
the baseline through test-only runner status code 3; host PID polling is prohibited. After all three
memory snapshots, the history test emits the unchanged complete report through test-only runner
status code 4 and the exact Bundle key `m2HistoryReport`, before evaluating numeric policies. The
retained `println` is diagnostic compatibility only and is not the native
`adb shell am instrument -w -r` stdout contract. The host classifier requires exactly one code 4
report Bundle per invocation and the complete runner status sequence containing codes 1, 3, 4, and
either 0 or -2, so a valid numeric failure retains all values needed for host recomputation. The earlier
`b52baf53053596332ce1abe8a002369fa540fa32` APK pair was never sampled after its compilation-mode
preflight stopped; adding the explicit report status creates a new test-APK identity without changing
the report schema, workload, checkpoints, thresholds, or command collector.
Timeout preserves partial evidence and verifies
the device worker stopped. An invalid attempt remains recorded and requires root review before one
corrected finite attempt; it is never overwritten or promoted. The host viewport record binds the
source, measurement-test blob, toolchain/JVM/OS, command exit/output, and raw identity. Its execution
window starts only after other Gradle/build/measurement work is quiescent.

Missing, duplicate, or mismatched identity, collector structure, rows, conditions, or correctness
makes an attempt `INVALID`. A command latency/GC or history heap/PSS/growth threshold miss in a
complete otherwise-valid artifact is `PERFORMANCE_FAIL`; its raw result is retained, the finite
budget ends, and no favorable replacement attempt is collected. A JUnit assertion failure does not
turn a complete valid performance population into `INVALID`; the raw evidence decides the class.

The sole physical non-frame batch completed on serial `T830128GB26321131293` with the exact APKs
above and actual target compilation status `verify`. The 1,200-row command artifact has SHA-256
`AC7116E27C8A736F491EAF036E29A72297F1CD9B85C873E3C03F6B1527B42F18`; all workloads retained 200
rows and 200 zero-blocking-GC rows. Nearest-rank p95/p99 were 1.324/3.197 ms sparse Pencil,
6.374/7.105 ms dense Pencil, 6.434/6.620 ms dense Eraser, 1.014/1.078 ms same-color no-op,
4.203/4.588 ms dense Undo, and 2.505/4.349 ms dense Redo. Every fixed command gate passed.

All five fresh history processes completed with distinct PID/process-start pairs and exact status
codes 1, 3, 4, and 0. Each reported max heap 256 MiB, memory class 256 MiB, retained Java heap
9,487,024 bytes, and zero post-cycle heap growth. Paired PSS deltas were 5,628, 5,702, 5,656,
5,648, and 5,814 KiB; their median 5,656 KiB is below the fixed 131,072 KiB limit. Every individual
heap, PSS, and churn gate passed. The final history classification SHA-256 is
`7CD9C1510D0B99B825053584ACEB773934621EE18ACEC05FF58C9BD05793EB6C`; the complete native record
SHA-256 is `04FD5AAA7B5BBF01FA8169A04DA65A5DD0C796321749993EE01498FF9539C7C2`.
The immutable private evidence root is
`C:/Users/info/.codex/tmp/nene-pixel-sol44-20260906-artifacts/nonframe-physical-3a4034f-run-01/`.
An independent raw-only audit reproduced all six command percentile/GC decisions and all five
history identity, runner-status, heap, PSS, and churn decisions; it also matched all 14 recorded
artifact sizes and hashes. The audit performed no adb, build, analyzer, or measurement invocation.

The host viewport task has `outputs.upToDateWhen { false }`, so its sole normal-default invocation
executed a fresh 20-warmup/50-sample population instead of reusing an older report. The raw artifact
SHA-256 is `AC99B7BC0354B6F031067A6DC3C80F3D6087A57B7F234C0C6A323E3350A8E6E0`; controller-transform
latency median/p95 is 17.1/72.3 microseconds and current-thread allocation median/p95 is
1,816/1,816 bytes. The deterministic correctness assertions passed; this lane has no numeric gate.
Its actual source was `49ef11ace837dec91e5eb49855459c1cfe57a0b6`; the measurement-test blob was
`e211f36988898617a2e51f2f8b4f03a907a102a8` and shared runner blob was
`45c9b7f39e9d1d1d84be13fb9c2388c7833313d4`. The retained private raw path is
`C:/Users/info/.codex/tmp/nene-pixel-sol44-20260906-artifacts/host-viewport-49ef11a-run-01/viewport-interaction-measurement.csv`.

Schema v2 retains the existing command report columns and consumers and adds no alternate collector.
The historical five-workload P2 plan and its immutable output identity remain resolvable unchanged;
the plan now supplies its ordered workload kinds explicitly so the same collector can select either
that five-workload sequence or the new six-workload M2 sequence.

Issue #70's natural-size dirty-status label and two-entry text-layout cache candidates were evaluated
and are not part of the final integrated UI. The accepted Issue #44 protocol
defines no independent whole-composed-app heap/PSS or UI-allocation gate, and the history artifact
makes no such claim. No new memory lane is invented. Whole-app UI memory remains an explicitly
unmeasured limitation. Actual-app presentation risk is bounded by the accepted #77 layout
correctness evidence, #76 optimized-runtime correctness, and the fixed B/C frame experiment; it is
not inferred from the core-only non-frame artifacts.
Issue #58 long-stroke results are host/JDK component diagnostics and do not include `PixelCanvas`,
device frames, or physical presentation; they remain risk evidence and cannot replace #44 physical
acceptance.

The command, history-cap, retained heap/PSS, viewport, allocation, and process-recreation lanes have
their individual valid results above. Issue #76 also satisfies Issue #54's actual-app absolute frame
gate on the accepted post-#77 R8 path. M2 remains open only for the exact stacked-artifact 9+1 smoke,
the required final CI/merge, and external Issue/milestone read-back.

### Actual-app frame acceptance

Issue #76's fixed `issue76-optimized-release-v3-01` experiment completed the exact max-one B10,
C10, C50, B50 sequence with 120/120 operations, 240/240 valid app-issued `gfxinfo` FrameTimeline
rows, and no retry. Optimized C source `92c1f4e6ffe18a9c41d13215f21c237493628043` / proxy APK
SHA-256 `dadd1fb783678426ec92d8b425d698a89932a5275b460cc80ed6346348590452`
passed with frame-overrun p95 `-0.024291 ms`, p99 `1.482044 ms`, and operation p95
`9.670308 ms`. Unoptimized B source `b8b0e6a43f4bc66a97895098c81337c2fec66e02` / APK SHA-256
`dfbbbb68888d19638bb877c844597b57cabe9287a2b6f876234a72c72871b763`
also passed, with p95 `-0.183366 ms`, p99 `1.284123 ms`, and operation p95 `9.913846 ms`.

Frozen #76 head `b5edfd9385bcb0db2b74ef73e7369d0c22b44712` retains C's exact production
Kotlin/resources, R8 role policy, P62 input, and optimized-runtime three-test blob; subsequent
accepted changes are the max-one harness, result documentation, and whitespace-only formatting of
the unchanged R8 role-policy chain. The #44 stack changes AndroidTest evidence code and this proof
only. The immutable frame manifest SHA-256 is
`04ad474edce233ed892f68885f5056c42a274f1ad095866beb98caefa5db6c2f`, and the full record is
documented in [M2 Actual-app Frame Follow-up](M2_FRAME_FOLLOW_UP.md) and accepted
[ADR 0013](../adr/0013-optimized-shipping-release.md). [PR #79](https://github.com/hideyukiMORI/NENE-PIXEL/pull/79)
tracks #76 integration.

PR #79's initial CI run failed `:app:android:ktlintKotlinScriptCheck` on the single-line chain layout
in `app/android/build.gradle.kts`; it is retained as a failed required-CI attempt, not as a
functional or performance failure. Frozen head `b5edfd9` changes only that whitespace layout, with
all non-whitespace content identical. A passing replacement exact-head CI result remains required.

Both decision lanes pass the predeclared absolute gate, while B's frame p95 and p99 are slightly
lower than C's. The result does not establish that R8 caused the frame gap to close or that C is
faster. C's lower CPU and operation p95 values are descriptive only; its p95 has `0.024291 ms`
headroom, the evidence covers one device and one exhausted budget, and the writer does not provide
strict SurfaceFlinger physical-present completion.

## Evidence results

### Process recreation

Result: **PASS** for the M2 process-recreation boundary only.

| Field | Recorded value |
| --- | --- |
| test source | `14c7efe15ad696c695c7f97d2a92561fb506ee11` |
| device | `T830128GB26321131293` / `iPlay80miniPro` / `T830` |
| app APK SHA-256 | `9E2AF85B1FAD426CAAAB8CBD06DB095B11CC1E096005DC1FD916F3FB13D08995` |
| test APK SHA-256 | `172E0902AB9121A46C5CF8EB13580F354B19D213289E597B1115E59A86E6A16B` |
| strict host-wrapper SHA-256 | `17A678DC3C50189E6F0817814798B12B68C8E632448D55B727B771C0099404FB` |
| retained raw record SHA-256 | `6075016D3EE3C49D19ABF938BF381D1BD4B1B681372DEA76D3CBB6DDC3AB16CE` |
| old / new PID | `11420` / `11520` |
| instrumentation | one exact test, `OK (1 test)`, `INSTRUMENTATION_CODE: -1`, 1.592 seconds |

Every adb operation recorded `TIMED_OUT False`. Both APK installs returned exit zero and `Success`.
The normal activity launch returned exit zero and `LaunchState: COLD`; `pidof -s` returned exit zero
and PID `11420`. The acceptance force-stop returned exit zero with empty stderr. The following
`pidof` returned the required exit one with empty stdout and stderr, and the following `get-state`
returned exit zero and `device`. The exact one-test instrumentation invocation returned exit zero,
reported one test and `m2ProcessStagePid=11520`, passed all canonical initial-owner assertions, and
ended with `OK (1 test)` and an empty stderr. The new PID was positive and differed from the stopped
PID.

The raw record is retained privately at
`C:/Users/info/.codex/tmp/nene-pixel-sol44-20260906-artifacts/process-recreation-attempt-02-functional.txt`.
The strict wrapper is retained beside it as `run-process-recreation-strict.ps1`. These private raw
artifacts are not repository inputs; their hashes bind this ledger to the reviewed evidence.

The first host preflight of the corrected wrapper failed parameter binding before starting any adb
process. Its raw record has SHA-256
`11C279D19BE153B11D040BDBECFA4A62D773C355A8F54C840D7FF33D28DE082B`; it is retained separately
and is not a device attempt or acceptance evidence. The prior unexecuted wrapper draft had SHA-256
`330B33DF468AEEBB81CB412331A90FD95C9DE4B6F46AFBACC195C4FF3EC66430`. Argument binding was
corrected, producing the executed wrapper SHA-256 recorded in the table, and that identity was
executed once against the reserved device.

Command and retained-history gates are PASS, the host viewport lane is valid with descriptive values,
process recreation is PASS, and actual-app frame acceptance is PASS through Issue #76. Final stacked
artifact smoke and integration/CI read-back remain pending, so this proof does not yet close Issue
#44 or M2.

## Rule review and limitations

Unsaved process-death restoration is intentionally outside M2 and remains M3 work. Whole-app UI
heap/PSS remains unmeasured. The accepted frame result has the one-device, narrow p95-headroom,
non-causal B/C comparison, and app-issued-gfxinfo limitations recorded above. Active waivers: none.

## External completion read-back

Pending the exact stacked 9+1 emulator smoke, #44 PR, required CI, merge, and Issue/milestone state
transitions. Issue #44 and M2 remain open until those final criteria pass and are read back.

## Related evidence

- [P2 Pixel Representation and Limit Evidence](P2_REPRESENTATION_LIMIT_EVIDENCE.md)
- [M2 Actual-app Frame Follow-up](M2_FRAME_FOLLOW_UP.md)
- [M1 Vertical-slice Baseline and Exit Proof](M1_EXIT_PROOF.md)
- [ADR 0005](../adr/0005-pixel-color-representation-and-limits.md)
- [ADR 0009](../adr/0009-bounded-linear-history-clean-checkpoint.md)
- [ADR 0013](../adr/0013-optimized-shipping-release.md)

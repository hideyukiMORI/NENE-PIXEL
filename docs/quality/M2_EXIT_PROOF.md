# M2 Core Drawing Exit Proof

Status: technical acceptance complete for `P2-07` / Issue #44. Required CI, merge, and external
Issue/milestone completion are live facts recorded through Issue #44 read-back rather than embedded
as static status in this proof. The previously accepted
Kotlin 2.4.10 command/history, frame, and process-recreation numbers remain immutable historical
evidence; they are not current Kotlin 2.4.20 acceptance evidence. The affected Kotlin 2.4.20 core
command/history, actual-app frame, and process-recreation lanes now pass. Existing unaffected
correctness and the Kotlin 2.4.20 final ten-test emulator functional
evidence remain recorded below with their identities.

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

## Kotlin 2.4.20 command/history result

Result: **PASS**. The fixed source is
`bb124a7989d2489aa28128911f9d2a4582ed7428`; app APK SHA-256 is
`13891a2fd81ed91b4672f31bac2570386edfe2d6364476cc8fb41ec8893f41ca`; AndroidTest APK SHA-256 is
`cd56a0d6cb9e7143f22fc5b1693627c0dad22c1eb3d8cdfe22623d7f00b3ad89`. The physical serial is
`T830128GB26321131293`. The candidate argument remains
`m2-production-command-256-lane-separated-v2`, as required by `P2AndroidFinalCommandProtocol`.

The three core modules contain 273 classes: 168 application, 67 domain, and 38 pixel-engine.
Of these, 260 class files are byte-exact across the toolchain change; the other 13 changed at the
class-file byte level while their inspected executable views remained equal. Those observations do
not prove transitive runtime equivalence: the Kotlin stdlib inventory changed from 990 to 995
classes with 170 changed entries. Old timing values were therefore not transferred; this result is
the independent Kotlin 2.4.20 collection.

The exact private execution package is:

- wrapper
  `C:/Users/info/.codex/tmp/nene-pixel-sol44-20260906-artifacts/kotlin-2420-command-history-plan-01/run-m2-nonframe-physical-bb124a7.ps1`,
  SHA-256 `f4294daf35c3c4ea3b157329787498a86428700a14ece92638e7328cf6c2ce4b`;
- bounded native helper in the same directory, SHA-256
  `29736313b91a99a4a4d0a120214915ad90ad1c3938ad0f48c2a373e63be387da`;
- fixed-serial invocation `invoke-m2-nonframe-physical-bb124a7.ps1` in the same directory, SHA-256
  `49c4a71f6e9723486394a6189bb244231ba4f25b0ec6a0dd970d80ef5deec1ac`;
- command analyzer SHA-256
  `e0c79a1339fd4e918d3f04789728df6112715f28d2bbb740f38b4e3835b545a1` and history analyzer
  SHA-256 `fb3f590f9a5fe33e7cfe2f396a1a12b0a91409a91fa120184e666d4573935913`.

The completed measurement output is
`C:/Users/info/.codex/tmp/nene-pixel-sol44-20260906-artifacts/nonframe-physical-bb124a7-kotlin-2420-run-01`;
the sibling path ending `-outer` records invocation start, end, exit, error, release state, and the
complete artifacts ledger. They were fresh before the sole invocation. There was no retry. The
outer exit is zero; the invocation ran from `2026-09-08T01:19:11.1992331+09:00` through
`2026-09-08T01:19:56.7201437+09:00`.

The command lane performs real `cmd package compile -m verify -f`, then one instrumentation with
six workloads, five warmups per workload, and 200 measured samples per workload: exactly 1,200
rows. `P2AndroidFinalCommandMeasurementTest` runs per-workload correctness before warmup and asserts
warmup and measured outcomes. Each workload requires p95 <= 8,000,000 ns, p99 <= 16,670,000 ns,
and at least 190 of 200 samples with zero blocking GC. Structural identity, schema, cardinality,
compile status, and runner failures are `INVALID`; a valid numeric failure is `PERFORMANCE_FAIL`.
Either result stops before history.

Only after command `PASS`, history ran indices 1 through 5 in five fresh target processes. Each
retains 64 entries, applies 524,288 changes, proves the complete round trip, and performs ten extra
cycles. Every run requires retained Java heap <= 50% of runtime max, retained PSS <= baseline plus
60% of memory class, and post-cycle heap growth <= max(1 MiB, 1% of runtime max). The five-run median
PSS delta must be <= 50% of memory class. The first `INVALID` or `PERFORMANCE_FAIL` stops collection;
no invocation is discarded or repeated.

Native calls have 30-second ordinary, 120-second install/compile, and 300-second instrumentation
limits, followed when needed by at most 10 seconds for kill/wait and 5 seconds for capture drain.
Timeout, incomplete capture, native error, or failure to prove target-app PID absence and live device
state is `INVALID` and blocks later lanes. Host start/end, limits, complete native error text,
stdout, stderr, exit, timeout, and capture state are retained. Host viewport remains descriptive
without new collection. Frame and process-recreation are separate Kotlin 2.4.20 lanes recorded
below and were outside this 1,200-row plus five-history-run budget.

All six command workloads passed. Sparse Pencil p95/p99 was 1.401/3.216 ms; dense Pencil
6.791/7.179 ms; dense Eraser 6.449/7.038 ms; same-color no-op 1.040/2.479 ms; dense Undo
4.392/4.898 ms; and dense Redo 4.128/4.675 ms. Every workload recorded zero blocking GC in all
200 samples. Command classification SHA-256 is
`d41bff81873ebb1cfc3a0d6fa638a9c8b1b3a3c23459bf5d25a0af8a31f4b6c8`; the 1,200-row CSV
SHA-256 is `eb041795c764f04e1de5b1e38093d4e90ac6c700d49207fbf661a12626e2b60c`.

All five history runs passed with distinct PID/process-start pairs. PIDs were 32481, 32523, 32564,
32604, and 32644. Each retained 9,487,024 bytes of Java heap against the 134,217,728-byte limit and
recorded zero post-cycle heap growth against the 2,684,354-byte limit. Paired PSS deltas were 5,743,
5,625, 5,550, 5,547, and 5,963 KiB; median 5,625 KiB passed the 131,072-KiB limit. Population
classification SHA-256 is `de44a541c4ebd365def28265e4469f2dc07aa777f49e92669ad5c9493abd20bf`.
The native record SHA-256 is
`ad9fe5341232d69029935fbadc2894a7a2f26438aa0f8dda08ffd52422365fb1`. It contains exactly six
instrumentation commands, `COLLECTION_COMPLETE`, zero timeouts, zero incomplete captures, and zero
nonzero native-error counts. The 19-row output/outer/release ledger is 2,787 bytes with SHA-256
`7211883d958647f9cc579f84924de5449a189d841c08611f1effc5563123de75`. Post-run release checks
record app and test `pidof` exit one with blank stdout/stderr and `get-state` exit zero with `device`.

## Kotlin 2.4.20 final emulator functional result

Run 07 passed the exact nine `UndoRedoEditorTest` tests followed by the maximum-boundary lifecycle
test: ten tests total and no additional test. Its source is
`bb124a7989d2489aa28128911f9d2a4582ed7428`; app, target AndroidTest, and presentation AndroidTest
APK SHA-256 values are respectively
`13891a2fd81ed91b4672f31bac2570386edfe2d6364476cc8fb41ec8893f41ca`,
`cd56a0d6cb9e7143f22fc5b1693627c0dad22c1eb3d8cdfe22623d7f00b3ad89`, and
`358771a916dc31c920c7a7cfa4e15bf0bf7c8cf84a53c656d8b4a135b75f9e7c`. The outer exit was zero;
both instrumentation calls had exit zero, complete capture, no timeout, no native error, and one
exact terminal record. Presentation ended with `OK (9 tests)` and lifecycle ended with
`OK (1 test)`. Cleanup completed with no
emulator/QEMU process left. The immutable 1,175-row ledger SHA-256 is
`eef863610fa62b48b414af68324cc6b9dfe03d8ef22682035376f7fba909271a`. Runs 01 through 05 remain
preserved as `INVALID`/`UNKNOWN`; run 07 does not rewrite or relabel them.

## Kotlin 2.4.20 process-recreation result

Result: **PASS**. The single strict attempt used source
`bb124a7989d2489aa28128911f9d2a4582ed7428`, serial `T830128GB26321131293`, app APK SHA-256
`13891a2fd81ed91b4672f31bac2570386edfe2d6364476cc8fb41ec8893f41ca`, and AndroidTest APK
SHA-256 `cd56a0d6cb9e7143f22fc5b1693627c0dad22c1eb3d8cdfe22623d7f00b3ad89`. It imported the reviewed
core wrapper/helper identities `f4294daf35c3c4ea3b157329787498a86428700a14ece92638e7328cf6c2ce4b` /
`29736313b91a99a4a4d0a120214915ad90ad1c3938ad0f48c2a373e63be387da`.

The outer invocation ran once from `2026-09-08T01:40:10.0993185+09:00` through
`2026-09-08T01:40:16.9714522+09:00` and exited zero. Cold launch produced prior PID 12491. The
acceptance force-stop succeeded; exact `pidof` exit one with blank stdout/stderr and the following
`get-state=device` proved the boundary. The single process-stage test reported new Bundle PID 12576,
distinct from the prior PID, and ended with `OK (1 test)` plus `INSTRUMENTATION_CODE: -1`.

All ten native calls had no timeout, complete capture, zero native errors, and blank stderr. Native
record SHA-256 is `c60c4c6f6a0567a946d2944be4baeaaac1a7cf48037bc36e684f4c9b34870812`; the four-file
output/outer ledger is 793 bytes with SHA-256
`5c4ec7db16332c53de8022451048b566938f912f51a116806f49e878538891b8`. Evidence roots are
`C:/Users/info/.codex/tmp/nene-pixel-sol44-20260906-artifacts/process-recreation-bb124a7-kotlin2420-run-01`
and its sibling ending `-outer`. There was no retry or new APK build. This proves the M2 canonical
initial-owner/PID-transition boundary; it does not claim restoration of unsaved state after process
death. The historical Kotlin 2.4.10 process result below remains immutable historical evidence.

No M3 behavior is added: unsaved process-death restoration remains outside M2.
The optimized three-test observer covers configuration recreation but does not prove full coverage
of all 13,514 P62 descriptors or unsaved process-death persistence. Host viewport remains historical
and descriptive. Whole composed-app heap/PSS and strict SurfaceFlinger physical-present completion
remain unmeasured.

## Kotlin 2.4.20 actual-app frame result

Result: **PASS**. Experiment `issue81-kotlin-2-4-20-rotation-fixed-v3-02` completed the exact fixed
old-10, new-10, new-50, old-50 sequence with five warmups per slot, 120/120 measured operations,
240/240 valid app-issued `gfxinfo` FrameTimeline rows, and no retry. Old optimized source/APK was
`92c1f4e6ffe18a9c41d13215f21c237493628043` /
`dadd1fb783678426ec92d8b425d698a89932a5275b460cc80ed6346348590452`; new Kotlin 2.4.20 optimized
source/APK was `7a34b7e1f044ce52b50e7f8739cee8387d54088f` /
`ba82190d46824b195da578d20bf9d9efe00eabe5e9647fdb324fe67ca771ba38`.

Both ten-operation diagnostic slots were valid and remained inconclusive by design. New decision
slot 3 passed with frame-overrun p95 `-0.936989 ms`, p99 `-0.417696 ms`, and input-operation p95
`8.655923 ms`. Old decision slot 4 passed with frame-overrun p95 `-0.819432 ms`, p99
`0.521607 ms`, and input-operation p95 `9.128154 ms`. All four outer invocations exited zero and
all four saved rotation states were restored and verified. The immutable 293-file artifacts ledger
SHA-256 is `f759a00e8af1a5b922126befce7c09164af20b9be5a2c1018106c0d325443752`; evidence root is
`C:/Users/info/.codex/tmp/nene-pixel-81-experiments/81/kotlin-2-4-20-rotation-fixed-v3-02`.

The earlier experiment ending `v3-01` remains immutable `INCOMPLETE`. Slots 1 through 3 retain their
original valid statuses; slot 4 stopped at sample 29 as `INVALID`, with the exact UI predicate
`UNKNOWN`. A recorded landscape-to-portrait transition violated the fixed viewport premise; no
exact ordering between host and device clocks is asserted. Its partial slot-4 rows are not
replacement evidence. The corrected experiment pins and validates the fixed landscape/logical
viewport contract and restores the saved rotation policy.
The new current p95 headroom is `0.936989 ms`; the historical Kotlin 2.4.10 accepted result's
`0.024291 ms` headroom remains historical. The current old/new Kotlin optimized-APK comparison does
not establish that R8 or Kotlin caused a speed change, and app-issued `gfxinfo` is not strict
SurfaceFlinger physical-present proof.

## Change scope and verification policy

Issue #44 changes only this evidence record and focused test evidence. `QLT-011` through `QLT-016`
apply. A successful result is reused when the behavior, relevant implementation blobs, environment,
and assertion remain equivalent. A different aggregate Git revision is not by itself an
invalidation reason. Performance, correctness, retained memory, and frame observations remain
separate lanes.

After Issue #81 merged as `adc29146cc28656cdf974b312feb8f279adcce4e`, the #44 branch was rebased
to pre-proof head `5bc24127777a5c76f7c16345af1dcc0424dcbc0c`. A filesystem SHA-256 comparison
against frozen producer source `bb124a7989d2489aa28128911f9d2a4582ed7428` found the same 265
tracked non-document paths, zero set differences, and zero byte mismatches. This covers production,
Android tests, Gradle/build scripts, catalog, locks, verification metadata, wrapper, ProGuard/R8
role inputs, and packaged-profile source inputs. The six #44 test blobs are exact at Git blob IDs
`68e015560d6e9a3edbd0041610e0feb6bf83a336`, `38d0133b7d0e97921a0db0f5b586e16013f12b70`,
`349df574cfd240c8e6698c6f29b0e9d2a36bdfd5`, `e5c2d708f2cf867ab10224819b0856b04387ef7a`,
`89b0c8b87a149821ad5cb00dc306d3d6410ec307`, and
`9e59a8b69d6d8dce8f2eb1ae5f5945b0637748ee`. Document-side differences are the reviewed
rotation-fixed frame protocol/writer and this proof. They do not change the frozen APK bytes.
Accordingly, all accepted `bb124a7` APK-bound results retain their measured source/artifact identity
under `QLT-012`; they are not relabelled as results of the later documentation commit.

The full canonical check/build is reserved for the final PR candidate and may be satisfied by its
required CI result. This integration uses documentation validation, affected compilation/static
checks, one normal-default debug app/AndroidTest build, and one predeclared 9+1 emulator smoke. It
does not regenerate P62 or repeat the exhausted physical frame experiment.

## Correctness acceptance matrix

| Requirement | Canonical evidence | Identity / current disposition |
| --- | --- | --- |
| Direct application command, controller adapter, pixels, revisions, `ChangeSet`, history, dirty state | `EditorRuntimeTest`, `CommandGatewayHistoryTest`, `CommandResultContractTest`, `ViewportEditorControllerTest` | Core runtime, gateway, controller, and adapter production blobs are unchanged from their accepted focused evidence; reuse under `QLT-012` |
| Zoom, pan, density, resize, grid, rectangular canvas, half-open edges, maximum canvas | `ViewportTransformGestureTest`, `ViewportTransformMappingTest`, `ViewportTransformPropertyTest`, `ViewportValueTest` | Viewport production and test blobs are unchanged; reuse under `QLT-012` |
| Compose journey: create/reject/cancel, palette, Pencil/Eraser/no-op, two-pointer cancellation, multi-step undo/redo, dirty state | `UndoRedoEditorTest` (9 tests) | Kotlin 2.4.20 run 07 passed all nine tests with presentation AndroidTest APK SHA-256 `358771a916dc31c920c7a7cfa4e15bf0bf7c8cf84a53c656d8b4a135b75f9e7c` |
| Configuration recreation retains the sole owners | `EditorRuntimeLifecycleTest.configurationRecreationRetainsTheOnlyDocumentAndWorkspaceOwners` | MainActivity, ViewModel, runtime, and the existing configuration-test method/assertions are unchanged; reuse the recorded PASS |
| New process creates one canonical initial runtime and `WorkspaceState` owner | host PID transition plus `EditorRuntimeLifecycleTest.processStageHasOneCanonicalInitialRuntimeAndWorkspaceOwner` | Kotlin 2.4.20 strict one-attempt result PASS: prior PID 12491, new Bundle PID 12576; historical Kotlin 2.4.10 PASS is retained in [Process recreation](#process-recreation) |
| Validated maximum new-document boundary | `EditorRuntimeLifecycleTest.emulatorNewDocumentCreationSmokeUsesValidatedMaximumBoundary` | Kotlin 2.4.20 run 07 `+1` passed after its exact nine-test presentation lane passed |

The matrix is the integrated acceptance proof. No duplicate synthetic journey test is added while
these direct, adapter, and Compose assertions cover the same canonical results.

## Process recreation boundary and protocol

A new Android process creates the canonical runtime and owners. The process-stage test directly
asserts the same owner references/relations for `EditorRuntime`, `EditorController`, and
`WorkspaceState`, together with 16 x 16, revision zero, empty history, clean dirty state, Pencil,
palette index 0, and canonical canvas semantics. The test does not count every process object;
blank pixel contents and initial viewport numeric values are supplied by the canonical production
path and host contracts used by the test. This does not mean restoration of an unsaved M2 document.

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

## Canonical performance and limit protocols

### Historical Kotlin 2.4.10 identity comparison and compilation context

Historical Issue #44 command and retained-history measurements from source `53841ff` remain useful
diagnostic evidence, but their production-tree equality alone is not sufficient for reuse under
`QLT-012`. The final review gives each evidence input an explicit disposition:

| Identity input | Recorded comparison | Disposition |
| --- | --- | --- |
| Production core | `:core:application/src/main` tree `71493da4f4ed90ba4b1d5860d9c60cb36fc2bcc4`, `:core:domain/src/main` tree `32e34d1f3c0b700ba7195c83fabd71bb8f828585`, and `:core:pixel-engine/src/main` tree `eefbe39b1398c5c634b2cc5e907e409f6aeb80fb` are identical at `53841ff`, measured non-frame source `3a4034f`, accepted #76 head `b5edfd9`, and the old #44 stack | This supported the historical Kotlin 2.4.10 core-only result; it does not transfer timing to Kotlin 2.4.20 |
| Core compile inputs | Gradle wrapper, `gradle.properties`, all three core build scripts, and `AndroidComposePlugin.kt` were identical; AGP 9.4.0, Kotlin 2.4.10, Compose BOM 2026.08.00, Activity Compose 1.13.0, and JUnit 6.1.2 were fixed for that record | No compilation-input invalidation existed within the historical Kotlin 2.4.10 comparison |
| #62 build additions | #62 adds the Baseline Profile plugin/module/dependencies and generated profile to app packaging; the added benchmark/UI Automator dependencies belong to the new quality module and do not enter the three core runtime classpaths | Core correctness and host-core compilation remain equivalent; final APK and device compilation identity are changed inputs |
| Old M2 command artifact | `nene-pixel-m2-android-command-latency-v1`, debug/debugAndroidTest, six ordered workloads, 5 warmups plus 200 samples, physical profile `NENE-P2-ALLDOCUBE-IPL80MP-A16-API36`, raw SHA-256 `CF0B64A1FFB15AF507F44001699AFF6C3BA8EA26186EF29BDF65FA3AF71105CA` | Not accepted as final latency, ART allocation, or GC evidence |
| Command harness | #56 separated one correctness execution from latency fixtures. The old harness built and compared a complete expected `DocumentState` and computed document/snapshot hashes after every warmup and measured execution. These operations were outside each timer but changed allocation, cache, and GC state before later samples | `QLT-014` observer-effect correction invalidates the old population. The old CSV also has no exact app/test APK hashes or declared package compilation mode required by `QLT-012` |
| Retained history | Five `nene-pixel-m2-production-history-v1` files identify source, profile, 64 entries, and 524,288 changes; the subject is a retained `CommandGateway`, not Compose | Semantics and Java-heap meaning remain useful, but final acceptance reuse is not established: exact app/test APK hashes, toolchain invocation, and compilation state are absent from the retained records |
| Host viewport | Raw schema `nene-pixel-p2-viewport-measurement-v1`, profile `NENE-P2-WINDOWS-I9-10850K-JBR21`, OpenJDK 21.0.11, 20 warmups plus 50 samples, raw SHA-256 `5863665146AB95C0508B112FA00C05F00D5C70F916B15AA1504850A9F69D0BA6` | Not reused as final timing/allocation: its recorded source was `e178130`, while the then-current measurement-test blob differed and the raw file carried no source revision. Viewport semantic/property tests remained reusable |
| Runtime profile | Existing records establish that debuggable APKs contain no packaged profile, while the release-like actual-app decision lane explicitly installs the packaged profile and compiles `speed-profile` | Baseline Profile changes do not invalidate host/core correctness. They do invalidate release-like actual-app identity. The old debug command artifact did not record an explicit compilation mode, so timing reuse is still prohibited |

The historical replacement non-frame protocol was fixed at test/document source
`3a4034ffc1292f710308903d0091338a60394882`, layered on production candidate `374ad211`. Issue #70's
frame decision subsequently failed its unchanged absolute gate, so that presentation candidate is not
adopted. The retained historical debug artifact from `3a4034f` includes that rejected P70
presentation. The command and retained-history collectors do not start an Activity or initialize the
Compose presentation tree; they execute the production `CommandGateway` and three core trees
directly. The complete `app/android/src/androidTest` affected-input tree, the three core production
trees, and their core build inputs were byte-identical between `3a4034f` and the historical #44
stack. Historical reuse was therefore limited to those core subjects and did not assert equivalence
for UI memory, frame timing, or presentation behavior. The then-final integrated UI contained the accepted #77 layout and #76 R8 role
policy, not the rejected P70 presentation. Root reviewed the exact source, artifact, harness, and
finite-budget identity before the recorded collection below:

The historical physical core lanes used the already-built 11,762,545-byte app APK with SHA-256
`E15684FFF220C669F788ED1898234E02CBAD9D4B87114E59F1B6021A9BE6D57B` and the 1,305,999-byte
AndroidTest APK with SHA-256
`413294CE1E9865676EB65A73B2F8E76A9D8781BC381E7F1F1D8C3207058513F2`. Later documentation-only
commits map to those immutable bytes and do not relabel them. Android treats a
debuggable package as VM safe mode and downgrades a requested `speed` filter to `verify`. The fixed
command/history compilation contract therefore requests `cmd package compile -m verify -f`, saves
the complete dexopt report, uses the canonical target-package block parser, and requires every
reported target status to equal `verify`. It also records the test package dexopt block without
requiring the same filter. The debug/JIT/profile state is part of this scoped identity; these core
numbers do not describe release performance. Historical actual-app release-like performance was
accepted separately by Issue #76 and ADR 0013 on the fixed post-#77 optimized consumer identity.
The first collection attempt requested `speed`, received command success but actual target
`[status=verify] [reason=cmdline]`, and stopped before any sample. It is retained as
`INVALID_PREFLIGHT`, not a performance result.

### Canonical measurement rules

| Lane | Fixed protocol | Decision rule |
| --- | --- | --- |
| Physical command | The sole `P2AndroidFinalCommandMeasurementTest` collector receives candidate `m2-production-command-256-lane-separated-v2`, schema `nene-pixel-m2-android-command-latency-v2`, run 1, and fail-if-present output `m2-production-command-256-lane-separated-run-01.csv`. Ordered 256-square workloads are sparse Pencil, dense Pencil, dense Eraser, same-color no-op, dense Undo, and dense Redo. Each has five untimed warmups and 200 measured samples, for 1,200 rows. One separate correctness execution per workload precedes warmup; latency fixtures do not construct a full expected document. Each sample times the sole `CommandGateway.execute`; cheap result/revision/history/`ChangeSet`/invalidation/no-op checks and ART deltas remain outside that timer. No per-sample full-document scan, hash, forced GC, or report write is permitted. Physical checkpoints occur before sampling, every 25 global samples, and after sampling. | For every workload, nearest-rank p95 <= 8.0 ms and p99 <= 16.67 ms, with no discarded rows; at least 95% of its 200 rows have zero blocking-GC increment. A numeric miss in an otherwise valid population is `PERFORMANCE_FAIL` and ends the finite budget. |
| Retained production history | Five independent instrumentation processes run indices 1 through 5. Each retains the production `CommandGateway` at 64 entries and 524,288 changes, proves the complete 64-step Undo/Redo round trip and ten additional cycles, and records two-pass post-GC baseline, retained, and post-cycle observations. | Every retained Java heap <= 50% of `Runtime.maxMemory`; median paired PSS delta <= 50% of `memoryClass`; every retained PSS <= its baseline plus 60% of `memoryClass`; every post-cycle heap growth <= max(1 MiB, 1% of max heap). No interpolation or discarded invocation. |
| Host viewport | One normal-default Gradle `measureP2ViewportInteraction` batch in a quiescent host window with no concurrent Gradle build or measurement: schema `nene-pixel-p2-viewport-measurement-v1`, profile `NENE-P2-WINDOWS-I9-10850K-JBR21`, debug host worker, 16 x 16 canvas, 1600 x 1600 surface, 2 px/dp, 20 warmups, and 50 samples. The canonical controller transform is timed; correctness stays outside timing. | Descriptive median/p95 latency and current-thread HotSpot allocation only; the accepted protocol defines no numeric viewport gate. Identity, row-count, deterministic-result, or raw-report failure invalidates the batch. |

### Historical Kotlin 2.4.10 non-frame record

The historical host record binds the stacked source revision, exact debug app and AndroidTest APK
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

The sole historical Kotlin 2.4.10 physical non-frame batch completed on serial `T830128GB26321131293` with the exact APKs
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

The historical Kotlin 2.4.10 command, history-cap, retained heap/PSS, viewport, allocation,
process-recreation, frame, and 9+1 emulator records retain their original valid or invalid
classifications below. Current Kotlin 2.4.20 disposition is governed by the current-result sections
above; required final CI/merge and external Issue/milestone state remain live completion facts.

### Actual-app frame acceptance

Current Kotlin 2.4.20 disposition: **PASS**, recorded in the current-result section above. The
result below remains historical Kotlin 2.4.10 evidence only.

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
functional or performance failure. The replacement required CI run `34046587278`, job
`101522606855`, completed with `quality` SUCCESS. PR #79 is MERGED and its merge commit is
`1a6ff2516363f56abfb3d4a4c46b3d85449e229f` on `main`. Frozen head `b5edfd9` changes only that
whitespace layout, with all non-whitespace content identical.

Both decision lanes pass the predeclared absolute gate, while B's frame p95 and p99 are slightly
lower than C's. The result does not establish that R8 caused the frame gap to close or that C is
faster. C's lower CPU and operation p95 values are descriptive only; its p95 has `0.024291 ms`
headroom, the evidence covers one device and one exhausted budget, and the writer does not provide
strict SurfaceFlinger physical-present completion.

## Evidence results

### Process recreation

Historical result: **PASS** for the Kotlin 2.4.10 M2 process-recreation boundary only. Current
Kotlin 2.4.20 disposition: **PASS**, recorded in the current-result section above.

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

Kotlin 2.4.20 command, retained-history, and actual-app frame gates are PASS; the host viewport lane
remains valid with descriptive historical values; and Kotlin 2.4.20 final ten-test emulator smoke is
PASS. Kotlin 2.4.20 process recreation is PASS. Repository integration and
external state are read back through Issue #44 rather than embedded prospectively in this candidate.

### Historical Kotlin 2.4.10 final stacked-artifact emulator smoke

Result: **PASS** for the fixed 9+1 M2 emulator functional population.

Run-06 used build/execution source `2ffa71f7c72e2dfe30c46315ffa4feefe39c07f2`, tree
`ea15c56a792f0cd0857cd5b6097b052d3560bbfa`, app APK SHA-256
`254156641c88b34cc78451bd10f30fa5aa7bb42d6c856f41c733d094fdae8a6f`, app AndroidTest APK
SHA-256 `e8a853c73fc839a77a0aee734d3b21b2bad5ff6e6ce165b7937b475cb8c4efdf`, and #77
presentation AndroidTest APK SHA-256
`fe9282e0884dc90e330baf3b95a0481f5b5985a302510e3695ebb70619559a7b` from source
`7b512d0b8d75677b14d15d2bd4515ae5f0f22dc0`. The runner SHA-256 was
`15e10ed502a6b435b9b4d808ba33d3a724b34cc2c00fbcbd56c5fd8062fec06c`.

The exact nine-method `UndoRedoEditorTest` invocation completed with start/end 9/9, failure 0,
one terminal, one `OK`, fatal/ANR 0, and a matching installed APK pullback. Only after that complete
valid/PASS result, the one-method
`EditorRuntimeLifecycleTest#emulatorNewDocumentCreationSmokeUsesValidatedMaximumBoundary`
invocation completed with start/end 1/1, failure 0, one terminal, one `OK`, fatal/ANR 0, and matching
app/test APK pullbacks. The runner summary is `valid/PASS`, stop reason `completed`.

Cleanup retained its finite contract and passed. It observed three active owned Job processes,
terminated the Job, required active process count zero, confirmed Job empty and launcher exit, and
captured both output tasks as `RanToCompletion`. The 80 recorded native commands each have the
native sidecar set and had zero timeout, zero incomplete capture, and zero non-empty native error;
the separate emulator launcher command makes 81 total `*.command.txt` files. There were three
APK-install and two instrumentation commands, exactly the fixed maximum population. No retry, additional test, build,
profile, frame, or performance collection occurred. The 1,147-row raw ledger has zero missing,
size, hash, duplicate, or unlisted entries and SHA-256
`ACCAD9C87AC2E916922301D08E696DC538965AA623323DD3E0FFC6ADE1082884`.

Runs 01 through 05 remain immutable `invalid/UNKNOWN` history and are not functional results.
Run-05 established the API 35 window-policy parser defect and incomplete launcher capture; v6 fixed
the parser and added explicit descendant ownership before this single run-06. The private raw
directory is
`C:/Users/info/.codex/tmp/nene-pixel-sol44-20260906-artifacts/final-emulator-smoke/run-06/`.

## Rule review and limitations

Unsaved process-death restoration is intentionally outside M2 and remains M3 work. Whole-app UI
heap/PSS remains unmeasured. The historical Kotlin 2.4.10 frame result has the one-device, narrow
0.024291 ms p95 headroom, non-causal B/C comparison, and app-issued-gfxinfo limitations recorded
above. Current Kotlin 2.4.20 p95 headroom is 0.936989 ms, still on one physical device. Both records
use app-issued `gfxinfo`; strict SurfaceFlinger physical-present
completion remains unmeasured. Active waivers: none.

## External completion read-back

Required CI, merge, and Issue/M2 completion are live external facts recorded through
[Issue #44](https://github.com/hideyukiMORI/NENE-PIXEL/issues/44) read-back. Prospective plans and
current evidence identities are linked there; this proof does not freeze a transient external
status that would require a post-CI documentation commit.

## Related evidence

- [P2 Pixel Representation and Limit Evidence](P2_REPRESENTATION_LIMIT_EVIDENCE.md)
- [M2 Actual-app Frame Follow-up](M2_FRAME_FOLLOW_UP.md)
- [M1 Vertical-slice Baseline and Exit Proof](M1_EXIT_PROOF.md)
- [ADR 0005](../adr/0005-pixel-color-representation-and-limits.md)
- [ADR 0009](../adr/0009-bounded-linear-history-clean-checkpoint.md)
- [ADR 0013](../adr/0013-optimized-shipping-release.md)

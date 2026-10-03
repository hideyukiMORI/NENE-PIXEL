# #145 live-editor memory / maximum save integration probe

Read-only source investigation, 2026-10-03. Design input, not an accepted protocol or collection authorization.
Issue #145 / P4-05d; PR #187 Draft. HEAD `3f9d80bc92c8e15c60aaf5ade04f08b09db4f8de`, implementation tree `1772cfde4b79dedf61468f71f085cd50d719b474`.
Only the lab `worktrees/issue-145` was inspected. Existing dirty reports were left unchanged; the old C: checkout was not accessed.
Rules: ARC-001/004/005/007/009, CMD-001, ADR 0018/0030/0031/0035, QLT-011 through QLT-019. Active waivers: none.
Required project documents, current Issue body, phase protocol, handoff and earlier memory/save and fixture-delivery probes informed this report.
No production edit, device operation, build/test, commit, push, PR or GitHub mutation. This new report is the only authorized write.

## Conclusions

1. The live MainActivity/Compose owner graph can be measured with the existing app instrumentation sampler and activity lifecycle patterns. An independently decoded maximum document or CommandGateway does not represent that graph.
2. Both baseline `169b59287ca60e77e07ac91690450dd1a44b9ba4` and candidate expose the model/runtime/controller/autosave/operation APIs needed for five common checkpoints. A targeted compile/correctness check of the overlay is still required; source inspection is not execution evidence.
3. Reuse the existing publication run/journal/writer for maximum autosave. Its present misleadingly named `candidate_v3_max` group is only 74,827 bytes. The new group must consume the checked maximum asset and prove 1,182,885-byte Candidate publication.
4. Physical SAF save can reuse the public AndroidProjectStorageAdapter and the existing real DocumentsProvider APK. The test must pin the provider, destination freshness, actual caller grants and write/close/read-back boundary. The current persistence test APK has no provider manifest.
5. Historical schemas/groups/budgets do not admit this phase. Prospective protocol and existing collector/analyzer extensions must agree before sampling.

Paths below abbreviate Kotlin roots only: APP = app/android/src; PRES = presentation/compose/src; CORE = core; PERSIST = adapters/persistence/src. Package suffixes are the repository's existing io/github/hideyukimori/nenepixel paths.

## Actual retained owners and accounting

| Owner / source | What remains reachable | Interpretation |
| --- | --- | --- |
| APP/main/.../EditorRuntimeViewModel.kt:43-61,103-117 | one runtime/controller, persistence workflow, operation worker, autosave scheduler; current candidate also underlay scheduler | Root is the real activity's ViewModelStore, plus the test's reference to that same model. Do not construct a second model. |
| CORE/application/.../editor/RuntimeOwners.kt:18-34; EditorRuntime.kt:198-225 | CommandGateway document/history, WorkspaceState, clean checkpoint; current runtime installation selects top layer | At load, history is empty and clean. Preview is workspace truth. Clean checkpoint is identity, not a second raster. |
| CORE/domain/.../pixel/PixelSnapshot.kt:13-14,25-27 | each immutable snapshot owns packed indices and coverage bytes | Maximum document logical primitive pixels: 16 x (65,536 + 8,192) = 1,179,648 bytes; object/name/palette overhead is extra. |
| PRES/main/.../editor/EditorController.kt:16-27 and EditorRuntimeAdapter.kt | current immutable render projection and flow retaining document/preview references | Shared references are not additional document byte copies. Old test state snapshots can keep abandoned documents alive accidentally. |
| PRES/main/.../editor/EditorScreen.kt:125-181 | remembered CommittedBitmapCache shared by canvas/actual-size window | Keep the actual Compose composition and its rendered canvas alive through sampling. |
| PRES/main/.../editor/CommittedBitmapCache.kt:29-33,39-60 | current document/definition/session, one straight-ARGB IntArray and one Android Bitmap | At 256 square, Java array payload is 262,144 bytes. Nominal ARGB8888 bitmap pixel payload is another 262,144 bytes; measured PSS is not this sum. Intermediate DocumentCompositeImage is not a retained field. |
| PRES/main/.../editor/PixelCanvas.kt:37-40; PreviewBitmapCache.kt:19-28,31-65,99-127 | remembered preview raster/bitmap plus active preview/composite/document/base references | Once preview ends, forgetSources drops document, gesture, composite and committed-bitmap references, but raster and preview bitmap remain for reuse. |
| PRES/main/.../editor/PreviewRaster.kt:17-21 | base IntArray and working buffer IntArray | 524,288 bytes of Java primitive payload at 256 square; preview bitmap nominally adds 262,144 bytes. The List view wraps the existing buffer. |
| CORE/application/.../render/StrokePreviewComposite.kt; CORE/pixel-engine/.../composite/PrepareStrokeComposite.kt:56-65, StrokeComposite.kt:19-30; CORE/pixel-engine/.../PixelSurface.kt:92-93 | palette IntArray, visible surface array with target slot null; each other PixelSurface privately copies indices/coverage from snapshot | At 16 visible layers, active top-layer preview retains 15 x 73,728 = 1,105,920 copied pixel bytes plus 1,024 palette bytes. These are additional to the document. No two full-canvas lower/upper RGBA arrays exist in current StrokeComposite. |
| PRES/main/.../editor/PixelCanvas.kt:37-40 | geometry/grid/backdrop and underlay bitmap caches | Include ordinary UI/native/renderer cost in observed target-process PSS; underlay is absent for this family. Fix window visibility/layout/viewport/palette panel states identically. |

Source inspection of baseline169b592 confirms the same PixelSurface copying and preview cache lifetime. Its preview composites against an opaque background, while candidate preserves alpha; source inventories remain role-specific. The historical expected +512 KiB is neither an observed delta nor a PSS tolerance. Logical accounting, Java heap and PSS must be distinct fields.

## Concrete live-editor checkpoint route

Existing patterns:

- APP/androidTest/.../acceptance/DurableMvpJourneyTest.kt:48-52,150-183: `createEmptyComposeRule`, then `ActivityScenario.launch(MainActivity::class.java).use`, `scenario.onActivity`, and `ViewModelProvider(activity, EditorRuntimeViewModel.factory(activity.application))[...]` retrieve the actual model. `awaitIdle` checks PersistenceOperationPhase.Idle and Compose idle.
- APP/androidTest/.../EditorRuntimeLifecycleTest.kt:31-71 confirms the model/runtime/controller are the same references from repeated ViewModelProvider access. Do not replay its unrelated lifecycle tests for this probe.
- PRES/androidTest/.../editor/ActualSizeWindowTest.kt:98-115 shows separate `performTouchInput { down(...); moveBy(...) }`, idle/preview assertion, then `performTouchInput { up() }`. DOWN-only can use the same separation and retain the pointer between calls.
- APP/main/AndroidManifest.xml has no separate activity process. App instrumentation and MainActivity share the target process; `Debug.getMemoryInfo` samples that process. The DocumentsProvider test-APK process and system DocumentsUI are outside that target-process PSS.
- APP/androidTest/.../measurement/P2AndroidRuntimeMeasurement.kt:77-128 supplies the existing two-pass GC/finalization plus heap/Debug snapshot and reference sink. Call capture from the instrumentation thread with the existing model as retained argument; do not force GC in a main-thread onActivity callback.
- APP/androidTest/.../measurement/P2ProductionHistoryRetentionMeasurementTest.kt:14-43,49-77 supplies positive PID/start/maxHeap/memoryClass, exact runner arguments, physical-environment checkpoint, and status-code 3/4 identity/report. Extract only small shared report/identity helpers if needed; retain one sampler.

Proposed five checkpoints, subject to parent acceptance:

| ID | Common baseline/candidate state | Lifetime / contamination condition |
| --- | --- | --- |
| C0 initial_idle | MainActivity visible; initial clean document; recovery initialized; no preview | Resolve physical/admission conditions before launch. Fixture staging and provider startup happen at the same declared place in each role. |
| C1 maximum_loaded_idle | Actual SAF load successful; pinned ID/revision0/256 square/16 layers; top16; clean/historyNone; canvas drawn | Decode/coverage/hash correctness helper has returned; release temporary bytes, independent decoded document, snapshots and image copies before GC. |
| C2 down_held_preview | DOWN-only at fixed mapped cell; selected index0; runtime preview on top16; committed document still revision0; preview drawn | MainActivity stays RESUMED, composition live and pointer held. Source-derived owner inventory includes 15 copied surfaces. GC is in this separate memory lane, never frame timing. |
| C3 up_committed_idle | UP commits exactly one stroke; preview null; historyUndoAvailable; canvas drawn | Wait both phaseIdle and autosave pendingToken==null / !publishing, with a declared acceptable terminal outcome; otherwise capture is invalid. Keep no pre-commit test document reference. |
| C4 post_cycles_idle | Fixed number of undo/redo pairs, ending in the same committed document/history availability as C3; preview null | Wait the same persistence/autosave quiescence and rendered canvas. Cached preview raster/bitmap remain; copied preview surfaces should be released. Number and terminal state must be fixed. |

Baseline169b592 has all model/runtime/controller/operation/autosave fields above (read-only `git show`), and can perform this load/gesture/undo/redo flow without a layer panel. Both roles can use the identical overlay and five fresh-process invocations if accepted. Five is a proposed finite population, not historical authorization. Freshness is host stop/relaunch plus unique PID/start pair, never an Activity recreation alone.

Test-reference controls:

- Hold scenario/model throughout one invocation, and no second runtime or independent maximum document. Store primitive checkpoint reports only; do not accumulate EditorRuntimeState, EditorRenderState, DocumentState or ToolGesture across checkpoints.
- Stage bytes in a helper with narrow lifetime. Any decode oracle belongs in a returned helper or separate correctness invocation, not a test field. Structural scans/copyPackedIndices/copyCoverage release their arrays before sampling.
- Do not retain screenshot/ImageBitmap/native Bitmap evidence in the memory method. A separate correctness case proves actual preview pixels/fixture load. UI state/canvas tags and pinned renderer sources define this lane's owner meaning without reflection or production inspection API.
- The Compose test rule, UiAutomation, test runtime and reporter are process residents; normalize their use across both roles and disclose that overhead. Status bundles/report strings and sampler allocations also cost memory; use the same order and avoid retaining prior full reports.
- The existing `AcceptanceFixture.requireIsolation` checks an old m3 guard naming contract. Reuse its provider/picker mechanics, not its obsolete guard admission. The new phase must use preservation-v2 bindings.
- Quiescence after UP/cycles prevents an active codec readback/retained autosave capture from unpredictably widening the graph. Existing DurableMvpJourneyTest.kt:119-124 waits pending token null, !publishing and Published. Parent must define undo-return-to-published/no-pending outcomes too; always requiring a new Published generation would be incorrect for coalesced no-op publication.

## Maximum autosave: extension boundary

PERSIST/androidTest/.../AutosavePublicationDeviceEvidence.kt:

- `AutosavePublicationEvidenceRun.collect` currently builds a one-layer seeded maximum and a minimum, measures 5 warmups +20 samples each, and uses the historical schema name with `candidate_v3_max/min` groups. The single-layer maximum is explicitly 74,827 bytes (:272-275).
- Replace the maximum source only for a newly admitted phase branch/group with packaged asset -> ProjectFormatBytes.create -> production ProjectFormatCodec.decode -> DocumentImportSource.Current. Do not import the host fixture object's test package or create another device builder.
- `verifyFixture` already proves encoded Candidate size; require exactly 1,182,885 plus matching fixture identity and Candidate decode. Full scans/hash happen at fixed boundaries, not between timing samples.
- `publish` (:137-146) starts immediately before RecoveryRecordCodec.encodeCandidate, then calls RecoveryRecordWriter.publish and stops after it returns. Keep that interval: encode, atomic start/write/sync/finish, bounded read-back decode and exact Candidate/source predicate. Caller scheduling/debounce/lifecycle waiting is outside this metric.
- RecoveryRecordWriter.kt:16-36,39-55,77-95 and RecoveryPublicationVerifier implement the same canonical publication. Generation advances only after a Written result is validated; do not report uncertain/failed rows as valid samples.
- Use existing reporting RuleChain, finite journal, invalid initial status, output reservation and raw rows. A two-group run has 54 rows. New group population must set journal MAX_ROW_COUNT, accepted rows, schema/header/output names, timeout/anomaly limits and analyzer together; never append another 27 rows into the existing 54-row contract accidentally.
- Historical automatic temp-directory deletion is not permission to delete phase evidence. Parent must fix whether final test-private records are quarantined under the existing slot policy and preserve failed/interrupted outputs. No clear/uninstall or restoration fallback.

ADR0018: 250 ms is the observed maximum-publication boundary for re-deriving quiet/cap constants, not a universal save PASS gate. Its documented formula is quiet = ceil(4 x max /500) x500 ms and cap = ceil(20 x max /500) x500 ms. Accept the application to the new maximum before collection; do not edit constants based on an uncollected result or redefine the boundary as a user-save limit.

## Physical SAF: actual production path and provider constraints

PERSIST/main/.../AndroidProjectStorageAdapter.kt:25-49,113-128 -> FreshDocumentOutput.kt:16-61 -> FreshDocumentWriter.kt:13-16,47-89 -> ProjectContentAccess.kt:25-47 is encode/self-decode/equality, fresh selected destination, ContentResolver openOutputStream(uri,"w"), byte write, close, bounded read-back and exact byte equality. It has no explicit fsync guarantee. AtomicFile timings do not substitute for this provider transport.

Smallest practical timing choices:

- A pregranted fresh URI returned by a test-only `ProjectDocumentPicker` lets public `AndroidProjectStorageAdapter.create(resolver,picker,dispatcher).save(document)` measure encode+self-validation+fixed-picker return+write+close+read-back. Name this verified save-to-preselected-destination interval; it excludes user/picker wait and provider creation. Document creation and obtaining actual grants are setup outside the interval.
- If Issue wording requires transport-only write time, PERSIST's own androidTest can call existing internal FreshDocumentWriter.writeAndVerify(UriProjectLocation(uri), preparedBytes). Its interval includes destination open/write/close/query/read/equality and excludes encode/picker. Keep a separate correctness case through the public save adapter; do not claim transport-only timing is full user Save As time.
- A fixed test-only ProjectContentAccess wrapper can timestamp the production content callbacks without changing production behavior, but adds instrumentation and starts a more complex timing specification. Prefer one clearly named existing boundary unless separate encode/write numbers are required.

APP/androidTest/AndroidManifest.xml:3-13 and .../acceptance/AcceptanceDocumentsProvider.java:17-19,61-76,83-114 are an already-declared real DocumentsProvider, in the test APK's own process/storage, with real framework grants. It restricts filenames to `i89-[a-z0-9.-]{1,90}`, MIME to project/PNG, and rejects createNewFile collisions. Use a fresh unique destination for every operation (including warmups); never reopen one existing URI repeatedly and call it a fresh-save population.

Provider scope must be decided explicitly:

- The existing provider is local test-private SAF with real binder/file descriptor transport, not a claim about Downloads/public/cloud storage. Pin authority, provider APK hash/package/version, target/grantee identity, URI and zero-byte/fresh creation proof in artifact/setup evidence.
- A URI string or installed APK does not grant the caller access. Existing AcceptanceDocumentsUi.create/open provides real DocumentsUI grants to the actual requesting activity. For persistence self-instrumentation, installing app test provider alone does not grant the persistence package; a test-only activity must receive the relevant picker result/grant, or provider choice/grant setup must otherwise be fixed and demonstrated. Do not bypass MANAGE_DOCUMENTS by direct ungranted URI creation or ContentResolver.wrap.
- Provider grants obtained for the app are not automatically the persistence test APK's grants. Keep setup/correctness and acceptance timing separable, but bind them to the exact same identity/path. The existing provider offers no accepted tree-grant helper; do not assume ACTION_OPEN_DOCUMENT_TREE is supported without source/functional proof.
- The app test provider Java source intentionally uses framework-only code because Kotlin lives in target APK. No cross-module test package import, duplicated provider implementation, new production API or dependency is needed if its APK is admitted as an explicit artifact.
- Keeping SAF measurement inside app instrumentation would reuse grants/provider more simply, but publication host plan currently instruments the persistence APK. Parent decides the smaller phase integration route; never add a parallel host collector.
- Preserve per-operation URI/outcome/byte-count and failure evidence. Controlled cleanup must stay inside declared fresh test storage; no arbitrary provider deletion. On success production read-back equality proves 1,182,862 project bytes; outside timing a batch-boundary codec/hash proof ties output to the pinned input. Candidate envelope size is irrelevant to SAF project bytes.

## Existing host files/functions to extend

| File | Focused extension |
| --- | --- |
| APP/androidTest/.../measurement/P2AndroidRuntimeMeasurement.kt | Reuse sampler unchanged; small shared identity/environment/report helper only if duplication would grow. |
| New APP/androidTest/.../measurement layer-editor measurement class | Actual MainActivity, five checkpoint flow, fixed family/role/profile/fixture arguments, primitive heap/PSS report; no cache inspection hook. |
| Existing APP/androidTest acceptance provider/picker helpers | Tiny fixture staging/fresh-grant correctness helpers as necessary; preserve provider's no-overwrite restriction and new phase admission. |
| PERSIST/androidTest/.../AutosavePublicationDeviceEvidence.kt and reporting/journal helpers | New phase group using packaged maximum decode, versioned report/schema, bounded exact rows; reuse writer; add SAF group/class at the parent-selected instrumentation site. |
| docs/quality/measurements/p4-indexed-device-lanes.ps1 | Get-P4DeviceLanePlan(:571,634-676), family/class routing, arguments, exact installs/grantees/provider artifact, private output filenames; Get-P4CollectorBudget(:490) derives finite wrapper bound including added setup. Keep Invoke-P4InstrumentationLane(:1089) as one executor. |
| docs/quality/measurements/p4-indexed-memory-analysis.ps1 | Get-P4MemoryStatusBundles/ConvertFrom-P4MemoryReport/Test-P4MemoryCapture: new versioned role family and exact five checkpoints/facts; retain PID/start identity, field validation and delta arithmetic. Current 4-family schema, 5-run history median and historical limits are not silently inherited. |
| docs/quality/measurements/p4-indexed-publication-analysis.ps1 | Get-P4PublicationGroups/Read-P4PublicationRow/Test-P4PublicationCapture: currently demands v1/v2 groups and 54 rows. Add explicit phase contract for max v3 recovery and SAF bytes/metric/provider/outcome, retaining historical parser identity. |
| docs/quality/validate-p4-memory-evidence.ps1; validate-p4-publication-evidence.ps1; existing lane/preflight validators | Synthetic exact schema/order/count/binding/boundary and missing/extra/failure fixtures only for changed contracts. No separate collector/analyzer framework. |
| docs/quality/P4_LAYER_PHASE_PROTOCOL.md; existing preflight/phase schedule | Fixed five-checkpoint ownership, populations/role comparison/limits, provider/timing, budget/verdict, artifact/profile/preservation identity. Protocol first, source second. |

## Canonical fixture reuse and minimum implementation slices

`PERSIST/test/.../LayerPhaseFixture.kt`, LayerPhaseFixtureTest.kt and LayerPhaseFixtureExport.kt already define/export/check the only synthetic builder. `docs/quality/fixtures/p4-layer-phase/maximum-layered.nenepixel` is 1,182,862 bytes / SHA-256 `165f62d180533849ce1a4ef1625cd3971e445f2dca60ef7b9b46fedaafa0b3ec`. `p4-layer-phase-fixture.init.gradle` explicitly maps the same directory to both existing AndroidTest APKs. No new builder/plugin/module needed. Existing merged-asset proof is not yet APK-entry or actual SAF-load evidence.

1. Fix memory checkpoint/role/population/threshold meaning and SAF/provider/timing/ADR0018 decisions in protocol + Issue. Agree one new schema family and exact finite slot plan with analyzer/preflight.
2. Implement only live-editor memory family plus synthetic host agreement checks; reuse fixture/journal/sampler identities. Compile the new overlay for both production roles and run one scoped separate correctness flow after preservation/admission; avoid broad lifecycle regressions.
3. Implement only maximum Candidate group plus fixed rows/identity/analyzer tests; retain canonical writer and current fixture test result. Separately add fresh physical SAF save at the selected instrumentation site and targeted public-adapter/grant/readback correctness.
4. Extend existing lane planner/preflight/budget/manifest bindings and preservation-v2 orchestration, inspect packaged fixture/APK/profile, then use the one phase collection. No samples until all agreed.

## Parent decisions remaining (10)

1. Accept C0-C4 checkpoints and fixed undo/redo cycle count/end state; decide whether C0 means before or after fixed provider/fixture staging.
2. Accept baseline169b592 vs candidate, five fresh processes each, exact order and memory comparison/statistics/limits (absolute vs relative); historical four-family rules are not implicit.
3. Fix acceptable autosave/operation quiescence outcomes and timeout at C3/C4, including a return to already-published state.
4. Confirm logical payload accounting remains descriptive; no equality with observed heap/PSS or +512-KiB tolerance.
5. Select SAF measurement site (app test vs existing persistence publication APK) and provider (controlled test provider vs public provider).
6. Fix verified full-adapter save vs transport-only write interval, and grant/fresh URI creation setup/correctness evidence.
7. Fix phase autosave/SAF group counts/order/warmups, exact rows, anomaly/timeout/stop/retry budget and schema/outcome semantics.
8. Fix ADR0018 maximum-cost decision use and any independent SAF numeric verdict (250 ms does not provide it).
9. Pin APK/provider/fixture/profile/build variant/dexopt condition; current memory/publication lanes explicitly use verify compilation, not frame lane's packaged-profile state.
10. Bind preservation/restoration-v2 and test-private fixture/output retention to the new lane schedule; obsolete m3 guard and historical cleanup do not authorize this phase.

## Verification and completion

Commands executed: read-only Get-Content/rg, git status/rev-parse, git show baseline source, and `gh issue view 145 --json body,title,state`. No test/build/device commands or logs. Some guessed nonexistent source paths/wildcard lookups failed during inventory; these were source-discovery misses, not build/test failures, and were corrected through rg --files/existing paths.
Reused results: no checks rerun. Handoff's fixture/export/asset results and earlier native/transfer/remap successful results remain retained; this report neither relabels them nor supplies new acceptance evidence. Existing #186 detekt debt remains separate; no new unrelated test failure.
Only documentation added: this investigation report. No production behavior, serialization schema or threshold changed. Remaining risks: uncompiled overlay, live rendering/GC validity, test-reference contamination, different role residents, real grantee/provider admission, missing APK/load proof, historical/current parser disagreement and absent final phase budget/verdict. Waivers: none.
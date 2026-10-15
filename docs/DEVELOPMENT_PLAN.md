# Development Plan

Status: ordered backlog authority; live TODO state is GitHub Issues

## TODO ownership

GitHub Issues are the only source of truth for whether work is proposed, ready, active, blocked, or complete. This document defines stable work packages, dependencies, and evidence; it deliberately contains no completion checkboxes.

Rules:

- one focused GitHub Issue implements one work package or a smaller slice
- each Issue names its work-package ID and milestone
- Issue dependencies are explicit
- an Issue cannot be marked ready before prerequisite exit evidence exists
- closing an Issue does not complete a milestone unless every milestone exit criterion passes
- a new task must map to an existing milestone or update the roadmap first

## M0 work packages

| ID | Work package | Depends on | Required evidence |
| --- | --- | --- | --- |
| P0-01 | Accept initial build/toolchain ADR | Issue #3 plan | Compatibility sources, rejected alternatives, module creation order |
| P0-02 | Create Gradle/Android shell | P0-01 | Wrapper build, JDK 21 verification, launchable debug shell |
| P0-03 | Establish canonical local quality gates | P0-02 | Intentional-failure proofs for compiler, formatter, detekt, tests, lint, docs |
| P0-04 | Add GitHub Actions and merge protection | P0-03 | PR run, required checks, blocked failing test PR |
| P0-05 | Enforce initial architecture rules | P0-03 | Automated tests/rules for `ARC-002`, `ARC-003`, `KOT-011`, `KOT-022` |
| P0-06 | Verify fresh-clone developer setup | P0-04, P0-05 | Documented clean setup and canonical `check` from a fresh path |

Production-domain work starts only after P0-01 through P0-05 pass. P0-06 closes M0 before M1 completion is claimed.

## M1 work packages

| ID | Work package | Depends on | Required evidence |
| --- | --- | --- | --- |
| P1-00 | Accept M1 internal contract and execution plan | P0-06 | M0 evidence review, accepted ADR, focused M1 Issues |
| P1-01 | Define initial domain values and invariants | P1-00 | Boundary/equality/containment/overflow tests; stdlib-only dependency gate |
| P1-02 | Implement immutable snapshot plus bounded pixel surface and patch | P1-01 | Invariant, patch inverse, determinism, and boundary tests |
| P1-03 | Implement document state, command result, transition, and change set | P1-02 | Closed-result, state-construction, and transition contract tests |
| P1-04 | Implement `ApplyStrokeCommand`, handler, and gateway | P1-03 | Exhaustive dispatch, `QLT-007` contract tests, render invalidation result |
| P1-05 | Implement workspace reducer and gesture preview | P1-01 | Reducer determinism and state-ownership tests |
| P1-06 | Build Compose vertical slice | P1-04, P1-05 | Touch-to-command integration without direct mutation |
| P1-07 | Add canonical undo/redo for the slice | P1-04, P1-06 | Apply/invert/replay and UI integration tests |
| P1-08 | Record vertical-slice performance baseline | P1-06, P1-07 | Named profile, canvas sizes, latency/memory results |

## M2 work packages

| ID | Work package | Depends on | Required evidence |
| --- | --- | --- | --- |
| P2-00 | Accept M2 evidence and execution plan | P1-08 | M1 external completion read-back, unresolved-constraint review, focused M2 Issues |
| P2-01 | Accept pixel/color representation and limit ADR | P2-00 | Lane-separated physical evidence, flat packed migration, semantic and limit contracts |
| P2-02 | Implement validated new-document flow | P2-01 | Boundary/rejection tests and UI flow |
| P2-03 | Complete pencil and eraser semantics | P2-02, P2-05 | Gesture/no-op/cancellation/overlap tests |
| P2-04 | Implement palette/active-color workflow | P2-01 | Single state owner and color round-trip tests |
| P2-05 | Implement viewport pan/zoom/grid mapping | P2-00, P1-05 | Density/transform/property tests; no document mutation |
| P2-06 | Implement bounded multi-step history | P2-03, P2-04 | History limit, dirty-state, undo/redo replay tests |
| P2-07 | Run core drawing acceptance and performance review | P2-03 through P2-06 | MVP drawing journey plus updated benchmarks |
| P2-08 | Close the actual-app input-to-physical-present frame follow-up | P2-07 | Fixed physical protocol, exact FrameTimeline/SurfaceFlinger correlation, evidence-backed resolution |

## M3 work packages

| ID | Work package | Depends on | Required evidence |
| --- | --- | --- | --- |
| P3-01 | Accept project-format, storage, and recovery ADR | P2-08 | V1 byte authority, compatibility, Save As, runtime completion/install, and recovery decisions |
| P3-02 | Implement project-format v1 codec and fixtures | P3-01 | Exact golden/round-trip/version/corruption/boundary tests and bounded host evidence |
| P3-03 | Implement Android save/load adapter and retirement foundation | P3-02 | Fresh-URI write/close/read-back, typed failure, checkpoint race, exact private envelope/conditional Retired evidence, and atomic load |
| P3-04 | Implement safe autosave and recovery | P3-03 | Focused debounce/coalescing decision, Candidate publication, recovery acceptance, interruption, ordering, and last-safe evidence using the P3-03 record/writer |
| P3-05 | Implement exact PNG export | P2-08 | Golden pixel comparison and failure tests |
| P3-06 | Complete durable MVP acceptance | P3-04, P3-05 | All `MVP_SCOPE.md` journeys and M3 exit evidence |
| P3-07 | Adopt the tablet editor shell | P3-04, P3-05 | ADR 0020 workspace appearance, dark/light and bottom/side controls, functional device geometry and lifecycle evidence |
| P3-08 | Localize UI and retain app language | P3-07 | English/Japanese/Simplified Chinese resources, platform settings and locale-independent test identities (#102), [evidence](quality/M3_LOCALIZATION_EVIDENCE.md) |

hide requested P3-08 after testing P3-07 on 2026-09-12. It localizes the existing editor before
P3-06 acceptance, without changing document semantics or opening M4.

hide requested P3-07 before final P3-06 acceptance on 2026-09-12. It reorganizes existing MVP controls
and adds session appearance only; it does not open M4 or close the M3 acceptance gate. P3-06 uses the
resulting final shell for its integrated journey. Palette-indexed document editing and later
animation, tile/map and reference-image features retain their separate contract decisions.

## M4 initial work packages

M3 entry evidence is [the durable MVP exit proof](quality/M3_EXIT_PROOF.md), #89 / PR #104.
ADR 0022 / #101 accepts this first palette-oriented portion of M4 after that gate; it does not
claim M4 layers/frames are already planned in implementation detail or complete.
ADR 0030 / #140 adds the ordered-layer portion (P4-05), initially deferring frames.
ADR 0032 / #169 adds the reference underlay (P4-06), which the owner placed before the layer gate.
ADR 0033 / #176 adds PNG import (P4-07), requested by the owner on 2026-10-01.
ADR 0034 / #172 decides the underlay memory (P4-06c).
ADR 0041 / #215 accepts bounded frames and v4/envelope 4; #216 implements the atomic hierarchy,
storage and frame-editing interface. ADR 0042 / #217 consumes it for workspace-only Loop/Once preview
with one lifecycle adapter, bounded explicit-sample timing and separate edit/display focus.

| ID | Work package | Depends on | Required evidence |
| --- | --- | --- | --- |
| P4-00 | Accept indexed palette, history and migration contract (#101) | P3-06 | Accepted ADR 0022, palette JSON contract, explicit legacy-preservation policy and focused Issues |
| P4-01 | Define 2–256-color palette and bounded JSON codec (#105) | P4-00 | Domain ownership/boundaries, exact JSON golden/round-trip/error/resource tests, bounded host evidence |
| P4-02a | Implement complete palette remapping plans (#111) | P4-01 | Number/nearest/explicit/reorder/delete contracts, defensive complete maps and bounded host evidence |
| P4-02 | Cut over document/engine/history/rendering/storage to indexed pixels (#106) | P4-02a | Exact v2 compatibility ADR first; shared inverse, palette-only invalidation, old-file/recovery preservation, functional and prospectively accepted affected performance evidence |
| P4-03 | Add palette draft editor/history, remap preview and JSON SAF UI (#107) | P4-02 | Atomic apply/cancel/stale contracts, bounded transport, localized tablet/lifecycle verification |
| P4-04 | Add exact-slot eyedropper and extensible long-press selection (#108) | P4-02, P4-03 integration | Typed workspace ownership, gesture arbitration, one-finger and accessible alternate selection evidence |
| P4-05 | Accept ordered layers, `Empty` cells, composite and project v3 contract (#140) | P4-04 | Accepted ADR 0030, updated governing documents and focused Issues |
| P4-05a | Cut over document/engine/history/rendering/storage to layered documents with `Empty` cells and v3 (#142) | P4-05 | Every consumer at once; composite golden vectors and single-layer byte equality with v2 export; v3/envelope 3 golden, round-trip and rejection tests; history accounting; per-commit cost statement, with the single-layer latency workloads registered with P4-05d (QLT-019) |
| P4-05b | Add the five layer commands, active-layer selection and hidden-layer rules (#143) | P4-05a | Command/undo/reconcile contracts, typed rejections, `NoVisibleLayer` export |
| P4-05c | Add the opening layer panel (#144) | P4-05b | UI spec first; localized tablet and accessibility verification |
| P4-05d | Historical identifier of the same #145 performance gate, now P5-03 at the owner-directed M5 exit | P4-05a, P4-05b, P4-05c, P4-06a, P4-06b, P4-06c; current P5-03 admission | Existing protocol, registered workloads and historical failures retained; no second Issue, current performance PASS or feature-level collection |
| P4-06 | Accept the reference underlay contract (#169) | P4-05c | Accepted ADR 0032, updated governing documents and focused Issues |
| P4-06a | Choose an image and show it beneath the layers, with visibility, opacity and removal (#170) | P4-06 | Value/action contracts, bounded decode and typed rejection tests, pixel tests of the draw order, localized panel row; per-frame cost statement and the underlay workload registered with P4-05d (QLT-019) |
| P4-06b | Add the underlay adjust mode: move, scale, fit to picture (#171) | P4-06a | Adjust arithmetic and clamp tests, mode entry/exit and document/viewport neutrality tests, localized adjust bar; cost statement |
| P4-06c | Remember the underlay of each work across processes (#172) | P4-06b | ADR 0034 first (ARC-004 and ADR 0021 amended); bounded app-private store, restore, eviction and unreadable-record tests; the document paths unaffected by a memory failure; cost statement |
| P4-07 | Accept the PNG import contract (#176) | P4-05b, P4-03 | Accepted ADR 0033, updated governing documents and focused Issues |
| P4-07a | Pick a PNG and add it as a new layer, appending or converting colours (#177) | P4-07 | Raster and planner golden tests, command/undo/stale-plan/accounting contracts, bounded PNG reader JVM tests including the export round trip, localized dialog; per-operation cost statement (QLT-019) |
| P4-07b | Open a PNG as a new work (#178) | P4-07a | New-work planner golden tests, confirmation/installation/dirtiness contracts, localized dialog form; cost statement |
| P4-08 | Duplicate a layer directly above its source (#208) | P4-05c, P4-07a functional acceptance | ADR 0030 amendment; exact immutable snapshot/name/visibility preservation, independent subsequent edits, one history entry, bounded insertion and localized row-menu evidence; cost/workload registration with #145 |
| P4-09 | Select a rectangle and move/copy it within one layer (#211) | P4-08, layer/palette/import functional acceptance | ADR 0039; workspace-only draft and Apply/Cancel, exact Empty/index overlap semantics, one ordinary patch/history entry, shared Composite preview, cancellation/runtime boundary and localized secondary UI evidence; source-derived cost and #145 workload registration |
| P4-10 | Flip and quarter-turn the selected rectangle (#213) | P4-09 | ADR 0040; eight exact composed orientations, non-square bounds and inverse mapping, existing transfer/preview/history path, retained Apply/Cancel and localized temporary controls; focused equivalence and source-derived #145 workload registration |
| P4-11 | Accept bounded frames and project v4 design (#215) | P4-10 | ADR 0041 and exact v4/envelope 4 contract; global 16-record capacity, shared palette, frame/layer focus, captured PNG targets and atomic consuming plan; documentation validation |
| P4-11a | Atomically implement frame editing and v4/envelope 4 (#216) | P4-11 | Every domain/command/history/workspace/render/import/export/storage/UI consumer together; exact migration, structural byte accounting, focus/cancellation and cache lifetime; focused host/native evidence and source-derived #145 workload registration |
| P4-11b | Accept and implement deterministic playback (#217) | P4-11a | ADR 0042; Loop/Once, one workspace session and lifecycle clock, bounded explicit-sample scheduling and stale cleanup, unchanged edit/document/history/autosave, same-frame cache reuse and isolated UI/background evidence; #145 cost/workload registration |
| P4-12 | Accept M4 exit evidence and define M5 entry (#226) | P4-11b, integrated #219/#221/#222 | Four-condition proof with original provenance and lost-evidence limits; candidate-scope disposition, #145 M5 placement, focused beta Issues, final required CI and live milestone read-back |

Preparation keeps the sole M3 editable path. P4-02 changes every live consumer together, with no
parallel editable RGBA document or provisional indexed-to-v1 writer. P4-05a likewise moves every
consumer to layered documents at once. P4-08 adds duplication after the layer and import functional
results; P4-09 follows with same-layer rectangular Move/Copy, and P4-10 adds exact flips and quarter
turns through that same transfer without a format change. Selection drawing masks,
cross-layer/external clipboard, delete, arbitrary angles and scaling remain separate focused
refinements. #145 performance collection remains at the owner-directed M5 exit. Layer merge remains
separate; saved layer opacity needs a future version/ADR, and v4 contains frames only. P4-11a removes the former
document-level layer list and changes every current writer/consumer together, with no provisional
unsavable frames or active-frame alias. P4-11b consumes the separate ADR0042 playback decision with
no unused API or saved-data change. Existing v3 performance fixtures retain their identities; a new v4 publication
workload is registered separately with #145, without feature-level collection. Tiles/maps
requirements remain recorded in ADR 0022 without unused APIs. Palette generation from an image and
dithering stay undecided (ADR 0033). Underlay rotation, numeric placement
and colour sampling from the underlay are not planned.

## M5 work packages

Entry is P4-12's integrated [M4 exit proof](quality/M4_EXIT_PROOF.md), not the closure of a feature
Issue alone. M5 Issues define the concrete work; this table defines dependencies and required
evidence without another live status checklist. P5-00 does not change minSdk26 or targetSdk37 by
recording them. A supported-platform change still requires its accepted ADR.

The [beta acceptance matrix](BETA_ACCEPTANCE_MATRIX.md) is P5-00's canonical criterion/environment
map. It distinguishes support, planned qualification and tested evidence, and assigns API37,
physical-input and other missing coverage to the packages below. Its acceptance is planning only;
it does not mark the matrix tested or authorize final-phase collection.

| ID | Work package | Depends on | Required evidence |
| --- | --- | --- | --- |
| P5-00 | Accept the supported beta device/API and evidence matrix (#228) | P4-12 | Named supported versus tested configurations, minimum physical profile, bounded locale/screen/input/storage coverage, existing-result reuse and concrete gaps; focused execution order |
| P5-01 | Accept accessibility, keyboard and stylus behavior (#229) | P5-00 | Reachable/localized controls, focus and disabled-state meaning, large text/short surfaces, canonical gesture/cancel behavior, affected device evidence and explicit unsupported behavior |
| P5-02 | Accept lifecycle, recovery and destructive-action data integrity (#230) | P5-00 | Whole-v4 save/recovery and old-source preservation, rotation/background/process/low-memory boundaries, asynchronous capture/cancellation, clear loss/confirmation limits and preserved user data |
| P5-03 | Accept the consolidated drawing/memory/large-document/save gate (#145, historical P4-05d) | P5-00; collection after relevant P5-01/P5-02 fixes and P5-06 candidate preparation | Reconciled current protocol/fixture/harness, fixed candidate/Profile/role artifacts, registered workloads and preservation; valid named-minimum-profile verdicts with historical FAIL/invalid records |
| P5-04 | Accept startup, long-session and battery evidence in the final M5 phase (#231) | P5-00; collection after relevant functional fixes and P5-06 candidate preparation | Prospectively fixed metrics, populations, budgets, observers and stopping rules; coordinated bounded collection with P5-03, immutable raw evidence and verdicts |
| P5-05 | Accept privacy and external-file data-handling disclosure (#232) | P5-00 | Source/configuration data-flow review, private recovery/underlay/backup/provider boundaries, truthful localized privacy/retention limits and focused resolution of discrepancies |
| P5-06 | Prepare signing, versioning and reproducible beta artifact procedure (#233) | P5-00 | One accepted release-role path, secrets outside Git/logs, key custody and version/update policy, changelog/checklist and tagged-checked-main artifact/provenance procedure; eligible measurement candidate inputs |
| P5-07 | Review all M5 exit evidence and accept the tagged beta artifact (#234) | P5-00 through P5-06 | Criterion-to-original-evidence ledger, no open critical data-loss/replay defect, accepted matrix and performance budgets, reproducible tagged checked-main artifact, actual CI/Issue/milestone read-back |

P5-01/P5-02/P5-05/P5-06 may proceed after the matrix decision when scopes are independent.
P5-03 and P5-04 coordinate one final phase; neither requires the other Issue to be closed before
its lane can run. Protocol preparation may precede the final candidate, but collection waits for
relevant functional fixes, candidate/build policy and current preservation admission. Workloads
must be reconciled prospectively with the existing gate, not appended to an old executable recipe
after collection starts. Existing budgets and the historical #142 `PERFORMANCE_FAIL` stay intact.

P5-06 prepares the release path; P5-07 produces/verifies the actual distributable beta only from
a tagged, checked main commit. A measurement-role APK or untagged preparation build is not that
artifact. Post-collection source changes follow affected-input equivalence/invalidation under
QLT-012; a later tag or prose edit alone does not require recollection. Neither package introduces
Play Store production launch or distribution beyond the accepted release scope.

Deferred M4 opacity/merge, onion skin, animation export, extended selection and quantization are
outside this hardening milestone. They need later focused product decisions, not implicit promises
in the beta matrix. No M6 transport work is decomposed here.

## Later work packages

M6 and later product refinements are decomposed only when their entry gate is near. Creating
detailed Issues earlier would imply requirements that the preceding product has not yet tested.

The next-milestone planning Issue must:

- review evidence and unresolved constraints from the prior milestone
- refine only the next milestone into focused packages
- update `MILESTONES.md` when accepted scope changed
- keep later milestones at outcome level

## Historical M2 entry order

After M1 completion:

1. P2-00 reads back M1 completion evidence and creates focused P2-01 through P2-07 Issues; P2-08 is created only if P2-07 retains an unresolved actual-app frame condition.
2. P2-01 records physical Android, dense/tool-specific, and history-memory evidence and accepts ADR 0005's flat packed representation and conservative product limits.
3. P2-02 and P2-04 begin only after the P2-01 ADR is accepted. P2-05 may proceed independently after P2-00 because it changes only workspace behavior.
4. P2-03 follows the validated document flow and viewport pointer arbitration; P2-06 follows completed drawing and palette semantics; P2-07 reviews the complete M2 journey. When that review retains an unresolved actual-app frame condition, P2-08 closes it with one fixed attribution and resolution path.
5. Do not begin M3 project-format work before P2-08 satisfies the remaining M2 frame condition, and do not begin OpenAPI work before the M6 decision gate.

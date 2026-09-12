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

| ID | Work package | Depends on | Required evidence |
| --- | --- | --- | --- |
| P4-00 | Accept indexed palette, history and migration contract (#101) | P3-06 | Accepted ADR 0022, palette JSON contract, explicit legacy-preservation policy and focused Issues |
| P4-01 | Define 2–256-color palette and bounded JSON codec (#105) | P4-00 | Domain ownership/boundaries, exact JSON golden/round-trip/error/resource tests, bounded host evidence |
| P4-02a | Implement complete palette remapping plans (#111) | P4-01 | Number/nearest/explicit/reorder/delete contracts, defensive complete maps and bounded host evidence |
| P4-02 | Cut over document/engine/history/rendering/storage to indexed pixels (#106) | P4-02a | Exact v2 compatibility ADR first; shared inverse, palette-only invalidation, old-file/recovery preservation, functional and prospectively accepted affected performance evidence |
| P4-03 | Add palette draft editor/history, remap preview and JSON SAF UI (#107) | P4-02 | Atomic apply/cancel/stale contracts, bounded transport, localized tablet/lifecycle verification |
| P4-04 | Add exact-slot eyedropper and extensible long-press selection (#108) | P4-02, P4-03 integration | Typed workspace ownership, gesture arbitration, one-finger and accessible alternate selection evidence |

Preparation keeps the sole M3 editable path. P4-02 changes every live consumer together, with no
parallel editable RGBA document or provisional indexed-to-v1 writer. Further M4 layer/frame work
is refined after these contracts and interaction results; PNG import, animation, tiles/maps and
reference-image requirements remain recorded in ADR 0022 without unused APIs.

## Later work packages

Remaining M4 work and M5 through M6 are decomposed only when their entry gate is near. Creating
detailed Issues earlier would imply requirements that the preceding product has not yet tested.

The next-milestone planning Issue must:

- review evidence and unresolved constraints from the prior milestone
- refine only the next milestone into focused packages
- update `MILESTONES.md` when accepted scope changed
- keep later milestones at outcome level

## Immediate execution order

After M1 completion:

1. P2-00 reads back M1 completion evidence and creates focused P2-01 through P2-07 Issues; P2-08 is created only if P2-07 retains an unresolved actual-app frame condition.
2. P2-01 records physical Android, dense/tool-specific, and history-memory evidence and accepts ADR 0005's flat packed representation and conservative product limits.
3. P2-02 and P2-04 begin only after the P2-01 ADR is accepted. P2-05 may proceed independently after P2-00 because it changes only workspace behavior.
4. P2-03 follows the validated document flow and viewport pointer arbitration; P2-06 follows completed drawing and palette semantics; P2-07 reviews the complete M2 journey. When that review retains an unresolved actual-app frame condition, P2-08 closes it with one fixed attribution and resolution path.
5. Do not begin M3 project-format work before P2-08 satisfies the remaining M2 frame condition, and do not begin OpenAPI work before the M6 decision gate.

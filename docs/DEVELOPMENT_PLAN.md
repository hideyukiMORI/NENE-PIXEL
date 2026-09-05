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
| P3-01 | Accept project-format, storage, and recovery ADR | P2-08 | Schema/migration/atomic-save/recovery decisions |
| P3-02 | Implement project-format v1 codec and fixtures | P3-01 | Deterministic golden/round-trip/version/corruption tests |
| P3-03 | Implement Android save/load adapter | P3-02 | Document-boundary integration and failure normalization |
| P3-04 | Implement safe autosave and recovery | P3-03 | Interruption and last-known-good recovery tests |
| P3-05 | Implement exact PNG export | P2-08 | Golden pixel comparison and failure tests |
| P3-06 | Complete durable MVP acceptance | P3-04, P3-05 | All `MVP_SCOPE.md` journeys and M3 exit evidence |

## Later work packages

M4 through M6 are decomposed only when their entry gate is near. Creating detailed Issues earlier would imply requirements that the MVP has not yet tested.

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

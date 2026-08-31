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
| P2-01 | Accept pixel/color representation and limit ADR | P1-08 | Benchmarks, memory analysis, compatibility consequences |
| P2-02 | Implement validated new-document flow | P2-01 | Boundary/rejection tests and UI flow |
| P2-03 | Complete pencil and eraser semantics | P2-02 | Gesture/no-op/cancellation/overlap tests |
| P2-04 | Implement palette/active-color workflow | P2-01 | Single state owner and color round-trip tests |
| P2-05 | Implement viewport pan/zoom/grid mapping | P1-05 | Density/transform/property tests; no document mutation |
| P2-06 | Implement bounded multi-step history | P2-03, P2-04 | History limit, dirty-state, undo/redo replay tests |
| P2-07 | Run core drawing acceptance and performance review | P2-03 through P2-06 | MVP drawing journey plus updated benchmarks |

## M3 work packages

| ID | Work package | Depends on | Required evidence |
| --- | --- | --- | --- |
| P3-01 | Accept project-format, storage, and recovery ADR | P2-07 | Schema/migration/atomic-save/recovery decisions |
| P3-02 | Implement project-format v1 codec and fixtures | P3-01 | Deterministic golden/round-trip/version/corruption tests |
| P3-03 | Implement Android save/load adapter | P3-02 | Document-boundary integration and failure normalization |
| P3-04 | Implement safe autosave and recovery | P3-03 | Interruption and last-known-good recovery tests |
| P3-05 | Implement exact PNG export | P2-07 | Golden pixel comparison and failure tests |
| P3-06 | Complete durable MVP acceptance | P3-04, P3-05 | All `MVP_SCOPE.md` journeys and M3 exit evidence |

## Later work packages

M4 through M6 are decomposed only when their entry gate is near. Creating detailed Issues earlier would imply requirements that the MVP has not yet tested.

The next-milestone planning Issue must:

- review evidence and unresolved constraints from the prior milestone
- refine only the next milestone into focused packages
- update `MILESTONES.md` when accepted scope changed
- keep later milestones at outcome level

## Immediate execution order

After this plan is accepted:

1. Create GitHub Milestones M0 through M6 from `MILESTONES.md`.
2. Create focused GitHub Issues for P0-01 through P0-06.
3. Start only P0-01.
4. Do not create an Android Studio project manually before P0-01 fixes the canonical scaffold inputs.
5. Do not begin OpenAPI work; revisit it only at the M6 decision gate.

# Quality Gates

Status: normative; initial local and architecture enforcement active

`./gradlew check` is the single local source of truth. CI must call the same task rather than reconstructing a different check sequence.

The `P0-03` implementation provides compiler warning failure, ktlint formatting, detekt, Android lint, build-logic unit tests, documentation validation, baseline rejection, dependency locking, and SHA-256 dependency verification. `P0-05` adds the first architecture gates for the module graph, core platform isolation, generic naming, and suppression waivers. Contract, property, and domain test layers become executable when their owning modules and behavior are introduced by later work packages. Intentional-failure, restored-green, and clean-environment checks are recorded in [Initial Gate Proofs](quality/INITIAL_GATE_PROOFS.md), [Architecture Gate Proofs](quality/ARCHITECTURE_GATE_PROOFS.md), and [Fresh-clone and M0 Exit Proof](quality/FRESH_CLONE_PROOF.md).

## Gate integrity rules

### QLT-001 — Warnings fail

Compiler warnings in project-owned Kotlin code MUST be errors. Android lint, detekt, formatting, and test warnings configured as errors must fail CI.

### QLT-002 — Greenfield baselines are prohibited

NENE-PIXEL starts with no lint, detekt, architecture, dependency, or test baseline. A baseline cannot be introduced to make existing violations disappear. Narrow waivers are the only exception mechanism.

### QLT-003 — Local and CI checks are identical

Every required CI gate MUST be runnable locally through Gradle. CI-only shell logic is prohibited when the rule can be a Gradle task or test.

### QLT-004 — Generated drift fails

CI regenerates deterministic outputs or verifies their hashes and fails if the working tree would change.

### QLT-005 — Gate weakening is an architectural change

Changing a severity, exclusion, threshold, module boundary, test task, or required CI job requires explicit justification. A change that weakens a MUST rule requires an ADR or waiver as appropriate.

## Required check stack

The initial implementation must wire these layers into `check`:

| Layer | Purpose | Expected mechanism |
| --- | --- | --- |
| Compile | Type safety and exhaustiveness | Kotlin compiler, explicit API, warnings as errors |
| Format | Canonical textual form | ktlint-compatible formatter or Spotless; one chosen by ADR |
| Static analysis | Complexity and forbidden constructs | detekt with project config |
| Architecture | Module/import/state-route rules | Gradle graph checks plus custom detekt rules/tests |
| Android | Platform correctness | Android lint |
| Unit | Domain, command, reducer, pixel behavior | Kotlin test framework chosen by scaffold ADR |
| Contract | Project format and command mapping | golden/schema/round-trip tests |
| Property/invariant | Raster and state invariants | generated bounded inputs where valuable |
| Dependency | Reproducibility and known risks | dependency locking/verification and audit mechanism |
| Documentation | Links, rule IDs, ADR/waiver shape | repository validation task |

Tool choice is not permission to duplicate responsibility. Exactly one formatter and one authoritative configuration exist for each tool.

## Architecture rule matrix

The first scaffolding milestones must automate these rules in order:

| Rule | Status | Mechanical enforcement |
| --- | --- | --- |
| `ARC-002` | active | Gradle rejects unknown modules, forbidden project-dependency directions, and cycles |
| `ARC-003` | active | Gradle rejects Android/Compose dependencies declared by core configurations |
| `ARC-004` | planned | package/API checks plus state-transition tests |
| `ARC-005` | planned | mutable type/import/API rules scoped to pixel engine |
| `ARC-006` | planned | forbidden reflection/service-locator/singleton rules |
| `ARC-007` | planned | forbidden direct clock/random/locale/environment calls in core |
| `CMD-001` | planned | no public document mutation APIs outside command runtime |
| `CMD-002` | planned | no workspace setters outside reducer |
| `CMD-003` | planned | exhaustive command-handler registry test |
| `KOT-004` | planned | forbidden `!!`; nullable public API inspection |
| `KOT-011` | active | custom detekt rule rejects unconditional generic type suffixes and package segments |
| `KOT-013` | planned | detekt complexity thresholds |
| `KOT-014` | planned | forbidden coroutine APIs and dispatcher ownership checks |
| `KOT-017` | planned | Compose dependency/import restrictions |
| `KOT-022` | active | repository validator rejects file-level and unwaived suppressions |

Until a row is active, every PR touching that area must explicitly self-review it. Conditional `KOT-011` cases such as whether `Processor`, `Data`, `common`, or `base` has a precise domain meaning remain review obligations; the mechanical rule intentionally rejects only names that are always prohibited. Unimplemented enforcement is tracked as project work, not silently treated as complete.

## Test requirements

### QLT-006 — Behavior changes carry tests

Every behavior change requires a test at the narrowest stable boundary. Fixes include a failing regression test first when practical.

### QLT-007 — Commands have canonical contract tests

Every document command must test:

- valid application
- every typed rejection category
- atomicity on rejection/failure
- resulting `ChangeSet`
- undo and redo round-trip
- deterministic replay
- render invalidation bounds when applicable

### QLT-008 — Pixel algorithms test invariants

Pixel-engine algorithms must test relevant invariants:

- writes never escape canvas/selection bounds
- unaffected pixels remain identical
- repeated deterministic input produces identical output
- patch apply/invert round-trips
- flood fill terminates within bounded memory/work
- overlapping strokes have defined ordering
- empty/no-op operations have one canonical result

### QLT-009 — Serialization tests are golden and migratory

Each project-file schema version has committed minimal golden fixtures. Tests cover deterministic output, round-trip, previous-version migration, unsupported future versions, corruption, and size/resource limits.

### QLT-010 — Performance claims are measured

Performance-driven architecture exceptions require a reproducible benchmark, named target device/class, representative document size, before/after result, and correctness tests. “Faster” without measurement is not evidence.

## Merge gate

Once Git is initialized and CI exists, `main` must require:

- pull request
- successful canonical `check`
- successful Android build for the supported variant
- no uncommitted generated drift
- no expired waiver
- no unresolved required review finding

Direct pushes, force pushes, and branch deletion on `main` must be blocked.

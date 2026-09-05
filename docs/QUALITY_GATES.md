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
| `ARC-003` | active | Gradle rejects Android/Compose dependencies declared by core configurations and non-stdlib production dependencies declared by domain |
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

## Verification execution policy

The following rules are the single authority for verification frequency and performance evaluation,
accepted in [ADR 0011](adr/0011-change-scoped-verification.md). They govern future work, including
Issue #54. Historical evidence remains immutable. These are mandatory agent/reviewer obligations;
they are not claims that Gradle automatically detects every process violation.

### QLT-011 — Verification follows the changed behavior

Before execution, the Issue verification plan and work report MUST identify changed paths, affected
behavior, applicable checks, and why any device measurement is needed. Use the narrowest stable
boundary during iteration. The following triggers are mandatory:

| Change or event | Required verification |
| --- | --- |
| Code iteration | Affected contract/regression tests and relevant compile/static checks; include consumers when their contract is affected |
| Iteration completion, handoff, or review preparation | Affected narrow checks only; these events MUST NOT independently trigger the full suite |
| Issue's PR ready to merge | Required successful CI `quality` running canonical `./gradlew check :app:android:assembleDebug` for the final merge candidate; no duplicate local full run is required |
| Documentation-only change | Documentation validation during development; the same pre-merge CI gate applies when merging, with no device measurement or profile generation solely for prose changes |
| UI or lifecycle behavior | Relevant device/emulator functional tests in addition to host contracts |
| Rendering, command, history, or memory performance change | Before/after measurement of the affected representative workload, with separate correctness evidence |
| Representation, storage format, or supported limit change | Relevant boundary/round-trip/corruption tests and latency or retained-memory evidence affected by that decision |
| Baseline Profile update | Explicit generation and reproducibility verification under ADR 0010; ordinary builds verify the committed artifact |

QLT-006 through QLT-009 remain mandatory. A trigger does not require rerunning unrelated historical
candidate matrices. Existing mandatory tests MUST NOT be excluded or disconnected to save time.
Moving measurement-only work out of `check` requires a separate accepted decision preserving its
correctness contracts. Future serialization requirements apply when that behavior is implemented.

The routine full-suite boundary is the Issue's final PR merge candidate, not a function, file,
commit, candidate reversal, work report, or intermediate completed subtask. Keep unfinished work
out of the merge-ready CI phase. Required CI must validate the current candidate against its current
base under branch protection; changed head/base or invalid evidence requires a fresh applicable
result before merging. An old successful SHA, skipped job, or narrow check is not a passing full gate.
A local full run is optional only for a concrete integration diagnosis or CI-environment investigation;
record why narrow checks cannot answer it. It does not replace required CI branch protection.

An iteration completion unit is one reviewable behavior or contract slice: its implementation,
affected consumers, regression contracts, and governing documentation when those must change
together. It is not each edited function or file, and completing one such slice still triggers only
its affected narrow checks. Conversely, unrelated Issues or behavior changes MUST NOT be combined
into an oversized batch merely to defer verification. The full suite is triggered only after the
focused Issue has one coherent, non-draft final PR candidate.

### QLT-012 — Reuse verified work with explicit invalidation

Normal verification MUST use the committed cache and daemon defaults. `clean`, `--rerun-tasks`,
`--no-build-cache`, `--no-configuration-cache`, and `--no-daemon` MUST NOT be routine iteration flags.
An exceptional run MUST record the concrete reason, affected tasks, and scope: cache diagnosis,
independent clean-build proof, or a specific toolchain/protocol requirement. It MUST NOT silently
become the default for later runs. Build-cache reuse is distinct from runtime compilation/warmup
conditions; device protocol conditions MUST still be established.

After successful verification, repeat only checks invalidated by a relevant input change, failure,
or unresolved concern. The pre-merge gate still applies to final inputs. A later prose-only edit
requires revalidation of those documents, not a new device result.

Invalidation follows contracts, not file count. A producer contract change invalidates its affected
consumer compile/contracts even when those consumers were not edited. An implementation-only change
does not invalidate unrelated modules. Candidate withdrawal, a status report, handoff, or moving a
PR between draft states does not by itself invalidate successful narrow checks; only changed inputs,
behavior, or an unresolved result does.

Evidence reuse MUST identify the original source revision, exact APK/test-APK hashes where applicable,
build variant, toolchain/dependency/profile identity, harness/schema, device conditions, workload,
and raw result location. Reuse is permitted only when affected inputs and measured behavior remain
equivalent; document the comparison. Never relabel an old APK or result as built from a newer commit.
Documentation commits MUST reference the immutable measured artifact instead of requiring a rebuild
solely to change its revision label. If identity or equivalence cannot be established, evidence cannot
be reused for the changed behavior.

### QLT-013 — Diagnostics do not substitute for decision samples

Before collecting performance data, fix the metric, population, percentile calculation, thresholds,
warmups, sample count, comparison order, numeric diagnostic rejection boundary, decision batches,
and allowed invalid-run recovery in the Issue and versioned protocol. Keep every collected result.
Unspecified "margin" MUST NOT be a gate. Never rerun until green, drop slow samples, or select only
the favorable batch. Aborting after a diagnostic failure does not erase its evidence.

A small diagnostic batch is for detecting predeclared gross regressions, correctness failures, or
invalid collection. Boundary results MUST be marked inconclusive for final acceptance and proceed
to the predeclared decision batch when its validity prerequisites hold. Ten-sample nearest-rank p95
is the maximum; it MUST NOT impose a zero-miss admission requirement for a final 50-sample p95 gate.
A diagnostic pass alone MUST NOT close a performance Issue. Final numerical thresholds remain in
the owning accepted protocol; this policy does not relax them or retroactively change any verdict.

### QLT-014 — Measure operations and keep measurement lanes separate

Define an operation's start, committed-result completion, reset, timeout, and exact frame association
before collection. Collect all relevant frames of that operation. MUST NOT assume one frame per
gesture unless a documented behavioral contract requires that cardinality. Report per-frame
deadline overrun separately from input-to-committed-result presentation latency; never treat their
thresholds or populations as interchangeable. Preview presentation alone does not prove committed
result presentation. Ambiguous association is invalid evidence, not an application failure.

Correctness, latency, retained memory, and intrusive trace attribution MUST remain separate lanes.
Latency samples may check cheap outcomes/revisions/history/invalidation/identity. Full-document
comparison, pixel scans, hashes, forced GC, and expensive correctness or memory probes MUST NOT
run between timed latency samples. Put them in separate correctness runs or fixed batch-boundary
checkpoints. Timing exclusions alone do not remove observer effects. Intrusive traces explain
causes and MUST NOT replace an untraced acceptance population without an accepted protocol change.

### QLT-015 — Experiments are bounded and protocols must agree

Every performance experiment MUST state its hypothesis, expected affected cost, representative
workload, exact candidate/baseline, run budget, and stopping/decision conditions before execution.
Do not repeat an unchanged rejected candidate without new evidence and an explicitly revised plan.
Once the budget is exhausted or results are inconclusive, report that outcome and update the plan
before further collection. Do not start unrelated matrices as a precaution.

The Issue, accepted protocol, and executable harness MUST agree before new acceptance collection.
If they disagree, block that collection and scope the correction; ordinary development and applicable
correctness checks may continue. Historical run instructions and one-off permissions are evidence,
not authorization for a new run. Protocol revisions require a new identity and retain all previous
FAIL/invalid results. No new framework, module, or competing measurement route is authorized here.

### QLT-016 — Spend verification effort on demonstrated product risk

Optimization proposals MUST distinguish measured bottlenecks from code-inspection hypotheses, state
correctness risks, and preserve one canonical command/action/pixel implementation path. Workload
selection MUST cover the affected realistic stress case, such as long repeated strokes at the
supported canvas limit, rather than infer whole-editor performance from short taps alone.

Prioritize data integrity, bounded resource use, deterministic history, relevant lifecycle behavior,
and measured interaction delays. A drawing cache is disposable derived state, never a second owner
of document semantics. Full-copy removal MUST preserve immutable ownership and rejection atomicity.
Report verification wall time separately from measured operation latency, reused evidence and its
identity, remaining uncertainty, and the next decision. Do not claim unmeasured speedups or automatic
enforcement for review-only rules.

## Merge gate

Once Git is initialized and CI exists, `main` must require:

- pull request
- successful canonical `check`
- successful Android build for the supported variant
- no uncommitted generated drift
- no expired waiver
- no unresolved required review finding

Direct pushes, force pushes, and branch deletion on `main` must be blocked.

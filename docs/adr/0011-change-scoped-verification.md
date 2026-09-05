# ADR 0011: Change-scoped verification and prospective performance decisions

- Status: accepted
- Date: 2026-09-05
- Issue: #55
- Affected rules: `QLT-003`, `QLT-005`, `QLT-006`, `QLT-007`, `QLT-008`, `QLT-009`, `QLT-010`, `QLT-011`, `QLT-012`, `QLT-013`, `QLT-014`, `QLT-015`, `QLT-016`

## Context

hide explicitly approved making the investigated verification improvements mandatory on 2026-09-05.
Saved core test bodies take seconds, while repeated forced builds, candidate device diagnostics,
and measurement-harness repairs dominate the documented #54 iteration cycle. The current main
CI run 33797666115 completed in 2 minutes 35 seconds, including a 1 minute 59 second canonical
check/build step; this is historical evidence, not a guarantee for another source or machine.

Ten-sample nearest-rank p95 equals the maximum, whereas the 50-sample value is rank 48. Requiring
zero misses in diagnostics can therefore reject admission more strictly than the final percentile.
The frame ledger also records a legitimate multi-frame gesture invalidating an assumed single-frame
population. The retained command harness performs full comparisons/hashes between samples despite
the lane separation already required by ADR 0005. Strong correctness contracts remain inexpensive
and directly protect document integrity; loosening those contracts would not address these costs.

## Decision

Adopt QLT-011 through QLT-016 in the Quality Gates document as the sole normative execution policy.
Use change-scoped iteration, one canonical pre-merge gate, normal build caches, and
identity-proven artifact reuse. Require triggered device work, bounded predeclared experiments,
separate measurement lanes, operation-based frame populations, and explicit final decisions.
Small diagnostic boundary results are inconclusive for final acceptance, not zero-miss admission
gates. Final numerical thresholds and all existing historical verdicts remain unchanged.

hide further clarified on 2026-09-05 that the full suite belongs only at the Issue's PR merge boundary.
This supersedes this decision's earlier completion/handoff wording. Iteration, intermediate completion,
handoff, and review preparation require affected narrow checks. Required CI validates the final merge
candidate; its valid result needs no duplicate local full run. Changed candidate/base must satisfy the
protected merge gate again. A full local run requires a specific integration/CI diagnosis. Trigger
wiring must be aligned without allowing a skipped job to masquerade as full-suite success.

## Rejected alternatives

### Remove correctness tests or relax limits to pass

Rejected: atomicity, history, boundary, and deterministic replay contracts prevent product defects
and are not the dominant observed iteration cost. A near-zero overrun remains its historical FAIL.

### Require cold builds and full device matrices after every edit

Rejected: unchanged measured inputs provide no new product evidence, while disabling verified
incremental work and repeating harness collection substantially increases cost.

### Retry small batches until one passes

Rejected: selecting favorable runs obscures variance. Fix population, comparisons, budget, and
decision conditions before data collection and preserve all results.

## Consequences

The canonical implementation path, CI job, tests, dependencies, and runtime behavior remain intact.
Agent/reviewer obligations become explicit and reject unjustified expensive repetition. Protocol
migration requires care: existing harnesses are not automatically compliant merely because policy
changed. Macrobenchmark replacement, cache optimizations, preview improvements, and recovery work
remain separate implementation decisions with their own focused Issues and evidence.

## Enforcement impact

Update AGENTS.md, Quality Gates, Development Workflow, Development Setup, and the ADR index. Issue
#54's owning branch adds the frame ledger's prospective notice before any new collection. Existing documentation validation checks links, rule IDs, and
ADR shape; canonical check/build contents stay unchanged. Issue #55 aligns CI to non-draft
merge-ready PR events. The required `quality` job fails closed for draft state rather than using a
skipped job, because GitHub can treat skipped or neutral required checks as successful. Post-merge
`main` push and manual triggers are removed, and Gradle cache access becomes read-only instead of
reintroducing a trusted-main full run solely for cache writes. Execution triggers, experimental
budgets, and artifact equivalence remain mandatory review checks rather than claims of complete
Gradle enforcement.
No production/build/schema migration occurs in this policy-only change. Incompatible harnesses
MUST NOT produce new acceptance evidence until corrected and verified in their owning Issue.

## Migration and rollback

Apply the policy prospectively. Before new #54 measurement, reconcile its live Issue plan, protocol,
and executable collection/analysis. Before new formal command latency collection, remove the
between-sample full-state probes prohibited by ADR 0005 and validate that separation. Keep prior
raw artifacts and their original identities and verdicts. This is a blocked collection prerequisite,
not permission for a temporary competing measurement path; no waiver is introduced.

Rollback requires another accepted policy decision, restoration of one normative execution policy,
and explicit protocol identities. Never rewrite historical results or maintain two active policies.

## Related

- Issue: [#55](https://github.com/hideyukiMORI/NENE-PIXEL/issues/55)
- Affected measurement work: [#54](https://github.com/hideyukiMORI/NENE-PIXEL/issues/54)
- [Quality Gates](../QUALITY_GATES.md)
- [ADR 0005](0005-pixel-color-representation-and-limits.md)
- Supersedes: historical zero-miss diagnostic admission for future runs only; no historical verdict
- Superseded by: none

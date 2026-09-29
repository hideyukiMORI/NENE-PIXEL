# ADR 0031: Device performance is judged at a phase gate; hot-path cost is stated at implementation

- Status: accepted
- Date: 2026-09-29
- Issue: #155
- Affected rules: `QLT-011`, `QLT-015`, `QLT-016`, new `QLT-019`

## Context

[ADR 0011](0011-change-scoped-verification.md) made "before/after measurement of the affected
representative workload" the mandatory trigger for every rendering, command, history or memory
performance change. Applied per Issue, the layer phase (P4-05) stacked three device collections and
one Baseline Profile generation onto one feature phase:

| Issue | Change | Device collection it carried |
| --- | --- | --- |
| #142 | Cutover to layered documents | Single-layer drawing latency, collected 2026-09-29 (`PERFORMANCE_FAIL`), a second collection planned |
| #145 | 16-layer worst case | Latency, retained memory, save time, history bytes |
| #147 | Screen-fixed checkerboard | Drawing latency |

One Lane 3 collection takes about four hours on the one named device: about one and a half hours of
host preparation (role clones, four APKs per role, identity and contract logs) and about two and a
half hours of collection during which the device cannot be used. On 2026-09-29 the first collection
of #142, together with the evidence it depended on, took the working day. The owner judged the
number of collections excessive and asked for measurement and improvement to be batched per phase,
with performance kept in mind while each feature is implemented.

[Milestones](../MILESTONES.md) require the performance budgets to pass on the named minimum profile
at the M5 exit. No governing document requires a collection per Issue; the per-Issue trigger is
stricter than the milestone it serves, and it delays functional work without changing what the
release must satisfy.

The first collection of #142 also showed what a collection is good at and what it is not. It located
the added time in the commit frames of the 256 × 256 document, in the draw interval. The cause was
then found by reading the code: two whole-image copies and a per-pixel blend on every commit. That
reading costs minutes and could have been written down before the change merged.

## Decision

### Device performance collection belongs to a phase gate Issue

A device performance collection (latency, frame, retained memory or storage time on the named
profile, judged against a budget or a baseline) runs once per phase. A phase is a group of Issues
that deliver one capability together, named in the development plan. One Issue of the phase is its
gate: it owns the protocol binding, the workload list, the collection and the verdict.

A feature Issue carries no device collection of its own. When its change touches a measured path
(input to committed presentation, command execution, history, storage, retained memory), it
registers the affected workload with the gate Issue before it merges.

The gate collects after the phase's feature Issues have merged, in one experiment that covers every
registered workload. A Baseline Profile that the phase's code requires is generated once, before
that collection.

### A feature Issue states its cost instead

An Issue that changes a measured path states, in its completion report, the work one operation does
after the change against the work it did before: whole-document scans, allocations and copies
proportional to the document size, per-pixel arithmetic, and any work added to preview frames. The
statement is a code-inspection statement under QLT-016, never a claimed speedup.

Work that the statement shows to be avoidable is removed before the merge, not after a collection.
Where a cost property can be asserted deterministically on the host, such as the reference identity
of a committed bitmap during a stroke, it is a regression test of the Issue.

### When a separate collection is still required

A collection outside the gate is planned in its own Issue only when:

- a release or a milestone exit depends on its verdict before the phase ends; or
- the cost statement shows work proportional to the document size added to every preview frame, and
  the change cannot be reshaped to remove it.

### A failed gate is fixed as a batch

When the gate collection produces `PERFORMANCE_FAIL`, the result is retained. The retained frames
are read by phase and interval first, the corrections are made together, and one further collection
follows under the owning protocol's rules. Limits are not changed.

### What does not change

Budgets, thresholds, protocols, populations, the M5 exit criteria, QLT-013 through QLT-015, and the
retention of every `PERFORMANCE_FAIL` and `INVALID` stay as they are. Functional device tests for UI
and lifecycle behavior remain a per-Issue trigger under QLT-011.

## Rejected alternatives

### Keep a collection per Issue

Rejected: the cost is about four hours per collection on a single device, the milestone does not
require it, and the first collection of #142 shows that the correction came from reading the code,
which the cost statement moves before the merge.

### Drop the relative rule and judge only the absolute budget

Rejected: it changes a limit. The relative rule is what detects a slow drift that stays inside the
absolute budget.

### Replace device collection with host microbenchmarks per Issue

Rejected: host timing does not represent ART, the profile or the display pipeline of the named
device, and it would be a second measurement route (QLT-015).

## Consequences

### Benefits

- One collection and one profile generation per phase instead of one per Issue.
- Avoidable work is found at implementation, when it is cheapest to remove.
- Functional work is not blocked by the device.

### Costs and risks

- `main` carries changes whose device verdict is pending until the gate. Nothing is released from
  that state: the M5 exit still requires the budgets to pass.
- A failure at the gate has more changes behind it and is harder to attribute. The retained frame
  phases and intervals, the cost statements of the phase's Issues and the unchanged baseline commit
  bound that search.
- A cost statement can be wrong. It is a hypothesis, and the gate is the measurement.

## Enforcement impact

- `docs/QUALITY_GATES.md`: new `QLT-019`; the QLT-011 trigger rows for performance and representation
  changes point to it.
- `AGENTS.md`, `docs/DEVELOPMENT_WORKFLOW.md`, `docs/DEVELOPMENT_SETUP.md`: the rule range becomes
  QLT-011 through QLT-019; `AGENTS.md` gains the agent obligation.
- Review-only enforcement. No compiler, test, CI or dependency change.

## Migration and rollback

The layer phase is the first application. Issue #145 is its gate. The single-layer latency condition
of Issue #142 and the latency condition of Issue #147 move to #145; the `PERFORMANCE_FAIL` of
experiment `p4-indexed-v7-20260929-layered1` stays retained and is not judged again
([M4 layered cutover evidence](../quality/M4_LAYERED_CUTOVER_EVIDENCE.md)). Rollback is another
accepted policy decision; never two active trigger rules.

## Related

- Issue: [#155](https://github.com/hideyukiMORI/NENE-PIXEL/issues/155)
- PR:
- [ADR 0011](0011-change-scoped-verification.md), [ADR 0024](0024-differential-check-selection-and-result-reuse.md)
- Supersedes: none
- Superseded by: none

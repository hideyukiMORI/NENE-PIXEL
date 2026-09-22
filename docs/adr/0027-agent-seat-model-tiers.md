# ADR 0027: Agent seats, model tiers, and the owner's instruction form

- Status: accepted
- Date: 2026-09-23
- Issue: #128
- Affected rules: `QLT-005`, `QLT-011`, `QLT-012`, `QLT-017`, `QLT-018`

## Context

hide, the project owner, changed the agent operating policy on 2026-09-23 because the top
implementation model was being consumed too fast: work that contains no judgment, such as
inventories, output comparisons, checkout preparation, and mailbox moves, was still being
delegated to it. The design seat (the top model, currently Claude Fable 5.1) already owns
requirements, design, and acceptance under the dual-seat convention recorded in the daily reports
since 2026-09-09; what was missing was a rule that assigns every other kind of work to the cheapest
model that can do it, plus a rule that turns repeated preparation steps into scripts that cost no
model time at all.

The test for the tier is "who fixes it when it goes wrong": work that the design seat can verify by
comparison and correct itself may go to a lower tier; work whose error would be silently accepted
must stay with the design seat.

## Decision

### Seats and tiers

Every background agent is created with an explicit model chosen by the kind of work:

| Work | Seat / model |
| --- | --- |
| Adjudication, acceptance, scoring, wording of instructions and specifications, narrative, decisions about protected material | The design seat itself (top model); never delegated |
| Implementation (one task = one branch = one PR) and rework after a returned review | Opus 5 (`model: opus`) |
| Preparation: survey notes of the actual code, inventories, PNG or output comparison, preparing a device checkout, persona opinions, reconciling reports against evidence | Sonnet 5 (`model: sonnet`) |
| Pure mechanical work: moving mailbox items, matching test lists, enumerating diffs, confirming a zero diff on protected material | Haiku 4.5 (`model: haiku`) |

Bounds for lower tiers: a preparation note is at most 300 lines and lists at most 15 open questions;
persona seats are at most four, on Sonnet, at most 150 lines each. From the second returned review
onward, the instruction narrows to one changed proposition and one rerun set. A local Ollama model
cannot be called from a Claude Code agent; if used at all it is an auxiliary seat on another runner,
limited to XS/S tasks, and the design seat performs acceptance.

The design seat reads every lower-tier output as data to verify, never as a decision. Independent
acceptance reads the actual output of scripts and checks, not a summary of them.

### Scripts replace repeated preparation

When the same preparation procedure has been walked by a model twice, the third time is a `chore`
Issue for the implementation seat that turns it into a script under `tools/`. Candidates recorded
at acceptance:

1. Device checkout preparation: worktree, build, install of the debug artifact, preservation of the
   device guard, and updating only the next launch target; the script never launches the app or
   starts a measurement.
2. Zero-diff confirmation for protected material (historical evidence documents, raw measurement
   results, golden fixtures) and field-level comparison of golden or replay outputs that lists only
   the fields allowed to change.
3. Pixel comparison of before/after screenshots that reports, as JSON, the bounding rectangle of
   the difference and whether it lies inside a given rectangle.

A scripted procedure is run by the design seat directly, not delegated to a preparation seat.
`tools/` holds these scripts; the layout document records the directory when the first script lands.

### The owner's instruction form

An instruction from hide that changes operating policy is recorded in the same focused change as an
ADR, a section in the current handoff, and one line at the head of the next actions in the handoff.
GitHub Issues remain the TODO authority; the handoff is where the next session reads the policy first.

## Rejected alternatives

### Keep one model for every seat

Rejected: it is the observed cause of the consumption problem, and it buys nothing for work whose
error the design seat catches by comparison anyway.

### Let the implementation seat choose its own helpers' models

Rejected: tier choice is a judgment about who fixes the error, which belongs to the design seat; a
seat that spawns helpers also multiplies cost without a visible decision.

### Build a general automatic dispatcher

Rejected: the tier table is small and stable, and a dispatcher would be a second authority over the
same decision. The table lives in `AGENTS.md`.

## Consequences

### Benefits

- Top-model usage goes to judgment only; preparation and mechanical work cost less per session.
- Repeated preparation becomes reproducible scripts whose output the design seat can read directly.
- Every background seat has an explicit, reviewable model choice in the transcript.

### Costs and risks

- A lower tier can miss what a higher tier would notice; the design seat's comparison is the
  safeguard, so lower-tier notes stay short and bounded.
- Scripts under `tools/` need the same narrow checks as any developer tool (QLT-011) and must not
  become a second measurement or verification route (QLT-018).

## Enforcement impact

- `AGENTS.md`: a new "Agent seats and model tiers" section with the table and bounds.
- The current handoff gains a policy section and a first line under next actions.
- Issue #128 is the chore for the three scripts; `docs/PROJECT_LAYOUT.md` records `tools/` when the
  first script lands, in that change.
- No compiler, test, CI, or dependency change. Documentation validation covers links and ADR shape.

## Migration and rollback

Apply from the next background agent onward. Existing evidence, Issues, and scripts are unchanged.
Rollback is another accepted policy decision; never two active tier tables.

## Related

- Issue: [#128](https://github.com/hideyukiMORI/NENE-PIXEL/issues/128)
- PR:
- [ADR 0024](0024-differential-check-selection-and-result-reuse.md)
- [Development Workflow](../DEVELOPMENT_WORKFLOW.md)
- Supersedes: none
- Superseded by: none

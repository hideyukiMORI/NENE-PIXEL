# ADR 0009: Bounded linear history and clean-checkpoint identity

- Status: accepted
- Date: 2026-09-04
- Issue: #43
- Affected rules: `ARC-001`, `ARC-004`, `ARC-005`, `ARC-007`, `ARC-010` through
  `ARC-012`, `CMD-001`, `CMD-003`, `CMD-005` through `CMD-009`, `CMD-011`, `KOT-002`,
  `KOT-003`, `KOT-007`, `KOT-008`, `KOT-013`, `KOT-016`, `QLT-006` through `QLT-010`

## Context

The M1 gateway retains exactly one `HistoryEntry` in mutually exclusive Empty, Undo, or Redo
states. It cannot undo multiple gestures or represent simultaneous undo and redo availability.
`EditorRuntime` also marks the document dirty after every applied result, so undoing exactly to the
initial clean state incorrectly remains dirty.

ADR 0003 requires undo and redo to apply the exact recorded patches and revisions. It also explains
why `Revision` cannot identify a state across an abandoned and replacement branch: two different
states may both have revision 1. ADR 0005 accepts two simultaneous history budgets: at most 64
retained entries and at most 524,288 retained pixel changes. Both limits must govern the actual
multi-step owner without allowing a document commit whose own history entry is discarded.

P2-06 has no save operation, project schema, asynchronous writer, or branching-history product
model. It needs one bounded in-memory linear timeline and one clean checkpoint that can distinguish
replacement branches without inventing an external audit identity.

## Decision

### Linear history state

`BoundedLinearHistory` is the only history owner inside `CommandGateway`. It owns an immutable
ordered list of `HistoryEntry` values and a cursor between entries. Entries before the cursor are
undoable; entries at and after it are redoable. `HistoryAvailability` therefore has four closed
states: none, undo only, redo only, and both undo and redo.

Every entry is created only from one `CommandResult.Applied` and retains its canonical `ChangeSet`.
Undo selects the entry immediately before the cursor and applies its recorded inverse patch. Redo
selects the entry at the cursor and applies its recorded forward patch. A successful undo or redo
moves only the cursor after its document transition succeeds; rejection leaves both owners
unchanged.

A successful new forward document mutation first removes every redo entry at or after the cursor,
appends the new applied entry, and then evicts the oldest remaining entries until both ADR 0005
limits are satisfied. Eviction order is always oldest-first. Redo removal precedes budget evaluation, and the
new entry itself is never evicted. If one new entry alone exceeded the retained-change limit, the
gateway would return a typed history-limit rejection and commit neither document nor history. The
current patch maximum makes that rejection unreachable for a valid stroke, but keeping the result
closed preserves the atomic boundary.

The history policy computes counts with `Long`, selects one deterministic retained suffix, and
stores the resulting bounded `Int` retained-change count. It does not inspect runtime memory,
device identity, UI state, or revision values. Dropped entries, redo branches, snapshot stacks,
closures, and materialized inverse payloads are not retained.

### Local history positions and clean checkpoint

The gateway assigns each successfully appended entry one deterministic, runtime-local
`HistoryPosition`. The initial position is zero; a new branch receives a new position even when its
document revision and pixel count match an abandoned branch. Entries record exact before and after
positions, so undo and redo restore the corresponding position together with the recorded document
transition. Evicting an old entry advances the retained base position but does not reinterpret any
remaining entry.

`HistoryPosition` is internal application bookkeeping. It is not persisted, serialized, exposed as
an audit sequence, used for command stale-write validation, or substituted for `Revision`. Position
exhaustion is a typed atomic rejection before commit.

`EditorRuntime` owns one `DocumentCleanCheckpoint` containing the document identity and local
history position installed at runtime construction. `DocumentDirtyState` is derived by comparing
the gateway's current document/position pair with that checkpoint; it is not latched from the last
command and never compares revision alone. Consequently:

- a new document starts clean;
- an applied drawing command is dirty;
- undoing exactly to the checkpoint is clean;
- redo is dirty;
- a replacement branch at the same revision remains dirty; and
- once budget eviction makes the checkpoint unreachable, undoing to the retained base remains
  dirty.

Advancing the checkpoint after save/load is explicitly out of scope. That later capability must
define its persistence boundary in a focused Issue.

### Presentation

History controls derive their enabled state from the four-state availability projection. Both
controls may be enabled at an interior cursor. `EditorRenderState` also projects the derived dirty
state, and Compose displays a stable clean/unsaved status without owning another dirty flag.

## Rejected alternatives

### Keep separate undo and redo stacks

Rejected because two mutable collections duplicate cursor meaning, make branch truncation and
combined retention accounting easier to diverge, and are unnecessary for one linear timeline.

### Use Revision as the clean checkpoint

Rejected by ADR 0003 and by replacement-branch behavior. Revision 1 on a new branch is not the
abandoned revision-1 state.

### Compare complete snapshots to the saved state

Rejected because every dirty-state query would scan up to 65,536 pixels and retaining a separate
snapshot is unnecessary when the gateway already has exact transition positions.

### Reject every command when history is full

Rejected because the accepted product behavior is bounded rolling history. Oldest-first eviction
preserves the newest complete gesture and keeps drawing available at the cap.

### Commit the document but omit an oversized history entry

Rejected because it creates an applied but non-undoable mutation and violates the atomic gateway
contract.

## Consequences

### Benefits

- up to 64 complete gestures and 524,288 retained changes share one deterministic owner
- an interior cursor represents simultaneous undo and redo without duplicate stacks
- exact pixels and recorded revisions round-trip across multiple steps and replacement branches
- dirty state returns to clean only at the actual application-owned checkpoint
- selection, viewport, cancellation, rejection, and no-op paths remain outside history and dirty
  state

### Costs and risks

- append copies a bounded list of at most 64 lightweight entry references
- eviction intentionally makes older states unreachable while preserving a small position token for
  correct dirty derivation
- clean-checkpoint advancement remains unavailable until save/load defines that boundary
- P2-07 must remeasure the integrated drawing journey; this ADR makes no new latency claim

## Enforcement impact

- one-level history code and names are removed rather than retained as a compatibility path
- history tests cover cursor interiors, multiple undo/redo, redo truncation, exact revisions,
  entry-count and retained-change eviction boundaries, limit/overflow outcomes, and deterministic
  replay
- runtime tests cover initial/apply/undo/redo/branch/eviction dirty semantics and ensure workspace
  operations do not affect checkpoint identity
- Compose tests cover multi-step controls, simultaneous availability, clean/unsaved indication, and
  emulator smoke
- a physical Android measurement exercises the production gateway at both history caps, verifies
  exact 64-step round trips and ten undo/redo cycles, and checks post-GC heap and PSS against the
  ADR 0005 policy
- `COMMAND_MODEL.md`, `PROJECT_LAYOUT.md`, and `GLOSSARY.md` record the accepted ownership and terms
- no dependency, module, Gradle plugin, serialization format, quality-gate exception, or waiver is
  introduced

## Migration and rollback

The focused migration replaces `OneLevelHistoryState` atomically with `BoundedLinearHistory` and
migrates gateway, runtime, presentation, and tests. The old one-level type and mutually exclusive
availability interpretation are deleted. No persisted data requires migration.

Rollback reverts the whole unmerged change to the M1 one-level behavior and removes this ADR. A
partial rollback that keeps two history owners or revision-based dirty logic is prohibited.

## Related

- Issue: #43
- Builds on: ADR 0003 recorded revision semantics
- Builds on: ADR 0005 history limits and shared inverse storage
- Supersedes: none
- Superseded by: none

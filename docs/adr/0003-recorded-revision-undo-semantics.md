# ADR 0003: Recorded revision semantics for canonical undo and redo

- Status: accepted
- Date: 2026-09-01
- Issue: #24
- Affected rules: `ARC-004`, `ARC-007`, `ARC-010`, `ARC-011`, `ARC-012`, `CMD-003`, `CMD-007`, `CMD-008`

## Context

P1-07 is the first executable undo/redo implementation. ADR 0002 already assigns history to the application command runtime and requires undo/redo to use the change data recorded by an applied command. `CMD-008` likewise requires the recorded `ChangeSet` or its canonical inverse rather than a UI closure, snapshot stack, or handler-specific callback.

The implemented `PixelPatch` contract records both `beforeRevision` and `afterRevision`. Its canonical inverse swaps those revisions together with every pixel before/after value. Applying that inverse therefore restores the exact document state and revision that existed before the command; applying the recorded forward patch restores the exact state and revision after the command.

The glossary previously called `Revision` monotonic. That wording conflicts with the recorded inverse contract and Issue #24's requirement that apply, undo, and redo restore exact pixels and revisions. Leaving the ambiguity unresolved would invite a second undo path that rebuilds or rebases patches instead of using the canonical recorded transition.

## Decision

`Revision` is the version recorded on a specific committed `DocumentState` and its patches within the internal transition contract. A revision value does not uniquely identify a state across abandoned and replacement branches, and it is not a globally monotonic event, audit, or lineage sequence.

One-level undo applies the recorded `ChangeSet.inversePatch`, including its recorded after revision. Redo applies the same entry's recorded forward patch, including its recorded after revision. A successful new command after undo replaces the one-level history entry and clears redo, even when its resulting revision value is the same as the abandoned forward branch.

Document and history state remain owned and committed atomically by `CommandGateway`. Any future asynchronous automation, multi-branch history, audit ordering, or stale-write protection that cannot be expressed by the current document ID and exact-state revision must introduce a distinct lineage or event-sequence type through a later accepted ADR. It must not silently change `Revision` or rebase recorded history patches.

## Rejected alternatives

### Assign a new increasing revision to undo and redo

Rejected because the recorded inverse would no longer be applied canonically. The runtime would need to rebuild or rebase a second patch from history, creating another transition interpretation and breaking exact `ChangeSet` round-trip equality.

### Keep monotonic wording while allowing inverse revisions to decrease

Rejected because documentation and executable behavior would disagree. `Revision` cannot simultaneously mean an exact state version and a globally increasing event sequence.

### Add a lineage or audit token in M1

Rejected because M1 is a synchronous one-level in-memory slice with no asynchronous writer, persistence schema, or audit consumer. Adding a speculative identifier would expand the public contract without a current capability or measured requirement.

## Consequences

### Benefits

- undo and redo use one recorded patch representation without rebasing
- pixels, revisions, `ChangeSet`, and render invalidation round-trip deterministically
- document equality describes the exact restored state
- the glossary and command policy agree with the executable inverse contract

### Costs and risks

- undo may decrease a revision value
- a new command after undo may reuse a revision value from the abandoned branch
- document ID plus revision is not a future multi-branch lineage token; asynchronous writers will require a separate accepted contract

## Enforcement impact

- `COMMAND_MODEL.md` states the exact-state revision rule under `CMD-008`.
- `GLOSSARY.md` no longer describes `Revision` as a global monotonic sequence.
- application contract tests assert exact apply/undo/redo state and revision restoration, canonical patch equality, redo clearing, typed stale rejection, and deterministic replay.
- the existing exhaustive command gateway, architecture validation, strict static analysis, and canonical root `check` remain the enforcement path.

## Migration and rollback

There is no persisted project format or external API to migrate. P1-07 introduces the first history runtime and tests together with this clarification. Rollback removes the unmerged P1-07 implementation and this ADR; it does not leave a second revision or history interpretation in production.

## Related

- Issue: #24
- PR: #35
- Supersedes: none
- Superseded by: none

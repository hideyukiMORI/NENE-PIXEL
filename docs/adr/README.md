# Architecture Decision Records

ADRs record long-lived decisions with meaningful alternatives and consequences. They explain why the canonical implementation path exists.

## States

- `proposed`
- `accepted`
- `rejected`
- `superseded`

Only accepted ADRs are normative. An accepted ADR must not silently contradict the Architecture Constitution; it updates the affected canonical document in the same change.

## Naming

```text
NNNN-short-kebab-title.md
```

Numbers are never reused. Copy `0000-template.md`, assign the next number, and add it to the index below.

## Required content

An ADR must include:

- status and date
- Issue
- rule IDs affected
- context based on concrete evidence
- decision
- rejected alternatives
- consequences and risks
- enforcement impact
- migration/rollback plan

“Industry standard,” “best practice,” and “AI recommended it” are not sufficient evidence by themselves.

## Index

| ADR | Status | Decision |
| --- | --- | --- |
| [0001](0001-initial-build-toolchain.md) | accepted | Initial Android identity, toolchain, dependency authority, quality tools, and module creation order |
| [0002](0002-m1-internal-contract.md) | accepted | M1 value, snapshot, state, command, and module ownership with executable package order |
| [0003](0003-recorded-revision-undo-semantics.md) | accepted | Canonical undo/redo restores the revisions recorded by the committed ChangeSet |
| [0004](0004-bounded-workspace-viewport.md) | accepted | Bounded workspace viewport, canonical render/input mapping, and multi-touch arbitration |
| [0005](0005-pixel-color-representation-and-limits.md) | accepted | Straight sRGB RGBA8 semantics, flat packed storage/shared inverse, and conservative canvas, stroke, patch, and history limits |
| [0006](0006-validated-new-document-runtime.md) | accepted | Typed new-document input, canonical application runtime ownership, and Android lifecycle retention |
| [0007](0007-bounded-pencil-eraser-gesture.md) | accepted | Bounded interpolated gestures and one pencil/eraser Stroke effect path |
| [0008](0008-bounded-tool-palette.md) | accepted | Bounded immutable tool palette and one indexed workspace-selection path |
| [0009](0009-bounded-linear-history-clean-checkpoint.md) | accepted | Bounded linear history cursor, oldest-first eviction, and runtime-local clean-checkpoint identity |

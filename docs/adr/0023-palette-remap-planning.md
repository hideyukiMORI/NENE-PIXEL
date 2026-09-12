# ADR 0023: One complete palette remapping plan

- Status: accepted
- Date: 2026-09-13
- Issue: #111
- Affected rules: ARC-001/004/005/007/008/010/012, KOT-001–008/012/013, QLT-006/008/011–016

## Context

ADR 0022 fixes number-preserving sprite recoloring, nearest and explicit replacement, deletion and
appearance-preserving reorder. #105 delivers PaletteDefinition and JSON. Computing mappings
independently in a draft UI, command and migration would duplicate these meanings. The complete
bounded mapping operation can be verified before changing live pixel/history/file consumers; it
needs neither an indexed editable document nor a provisional v2 writer.

## Decision

### Complete value and one validation path

Domain owns PaletteRemap: source and target PaletteDefinition references plus one defensive
old-slot-to-new-slot list of PaletteIndex values. Position in the list is the source slot. Its
canonical `create(source, target, destinations)` uses DomainValueResult, validates exact source
entry count before taking a bounded copy, then checks every copied destination against the target.
Failure retains no input. Every source slot is mapped, including unused ones; many sources may map
to one destination. No unfilled entries, sentinels or mutable arrays cross the boundary.
`destinationAt` rejects a source index outside this exact source palette.

The value references existing immutable palettes rather than copying colors. Equality includes
both complete definitions/defaults and the ordered destinations. It is a conversion plan, not a
second palette owner, indexed PixelSnapshot, saved state or source-history token. No inverse API
exists: many-to-one tables cannot restore original pixel numbers. #106 must retain exact before
indices in ChangeSet and validate DocumentId/runtime generation/exact HistoryPosition.

### Planner ownership and operations

Pixel-engine owns PaletteRemapPlanner in its `palette` package. It computes plans only, without
runtime, workspace, command, file or UI access. Closed PaletteRemapResult is Planned(PaletteRemap)
or Rejected(PaletteRemapRejection). Domain factory failures are wrapped once as
InvalidMapping(DomainValueRejection); the planner does not repeat domain invariants. Other planning
failures describe invalid permutations/deletions.

| Entry point | Meaning |
| --- | --- |
| byNumber(source, target) | Identity destination for every old slot. Factory rejects the first missing target slot if target is shorter; caller must then supply a complete explicit map. No clamp/modulo/fallback. Adopt target default. |
| nearest(source, target) | Deterministic nearest destination for every old slot using ADR 0022. Adopt target default. |
| explicit(source, target, destinations) | The canonical PaletteRemap factory through the planner result vocabulary. Many-to-one and equal-RGBA distinct target slots retain their meaning. |
| reorder(source, newOrder) | newOrder lists old indices in requested new order. Require each old index exactly once. Reorder colors, derive old→new destinations and remap default; preserve exact appearance. |
| remove(source, removed, replacement = source.defaultIndex) | Both indices refer to the old palette. Require a surviving replacement distinct from removed; reject below two entries. Compact higher indices, move removed references to the survivor and remap default. Deleting the default requires another replacement. |

Input lists are size-checked before copying/scanning. The copied permutation is validated before
producing new values. Temporary arrays/collections remain private bounded pixel-engine workspace
under ARC-005. All outputs converge on PaletteRemap.create, including identity/no-visible-change
plans. A later command's no-op decision still considers definitions/defaults and exact pixel numbers.

Nearest first prefers exact RGBA (lowest destination on duplicate exact values), then minimizes
ADR 0022's integer premultiplied-sRGB/alpha distance with lowest-index ties. Widen before arithmetic:
the maximum metric is 25,369,503,750, exceeding Int but fitting Long. Hidden RGB at zero alpha wins
exact matches; otherwise fully transparent colors can tie and select the first slot. This is not
a perceptual distance claim. No dither, quantizer, floating-point policy or dependency is introduced.

### Delivery boundary

#111 is a focused P4-02 prerequisite after #105, before #106's live cutover. These public APIs deliver
the requested complete conversion operation and typed failures rather than empty future types.
The value is consumed by the planner now; #106's single command/import path consumes the planner.
There remains one M3 editable RGBA path. Plans cannot directly mutate it; no v2 bytes are emitted.
#106 stays open until its exact v2/recovery ADR, all live consumers, preservation UX and prospective
functional/performance evidence are complete. This is not simultaneous editable implementations.

## Rejected alternatives

- Duplicate mapping in UI, command and import: creates competing meanings.
- Match only by RGBA: loses equal-color slot identity in number/explicit operations.
- Partial tables or implicit missing-slot fallback: hides destructive choices.
- Invert the table for Undo: mathematically false for many-to-one conversion.
- Switch live runtime/storage consumers one at a time: violates atomic cutover.

## Consequences

The cutover reuses a tested conversion boundary with explicit direction, default and bounds. This
adds one immutable value and algorithm API without enabling the screen yet. Stale tokens, patches,
history budgets, rendering, storage and UX remain #106/#107 responsibilities. No project/JSON schema,
module, dependency, plugin, product limit or waiver is introduced.

## Enforcement impact

Contract tests cover admission/lookup/equality/aliasing, 2/256 bounds, complete maps, recolor versus
reorder, delete/default compaction, exact/duplicate/hidden-RGB, ties, Long overflow hazards and an
independent-reference nearest comparison. Maximum 256×256 unmatched palettes and maximum permutation/
deletion receive prospectively recorded bounded host evidence. No Android performance claim or
device/profile/cold run follows. Narrow checks precede final required canonical quality CI.

## Migration and rollback

No live document migration occurs here. Revert the whole value/planner slice before consumer
adoption if needed. After #106 adopts it, preserve the same canonical mapping/command path and
compatibility requirements. Historical M3/#105 evidence is not relabeled as live indexed behavior.

## Related

- [ADR 0022](0022-indexed-palette-and-migration.md)
- [Development plan](../DEVELOPMENT_PLAN.md): P4-02a, prerequisite of #106.

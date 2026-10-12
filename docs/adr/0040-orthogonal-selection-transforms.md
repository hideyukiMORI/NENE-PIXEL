# ADR 0040: Exact orthogonal selection transforms

- Status: accepted
- Date: 2026-10-12
- Issue: #213
- Affected rules: `ARC-001`, `ARC-004`, `ARC-005`, `ARC-007`, `ARC-008`, `ARC-010`, `ARC-011`,
  `CMD-001`, `CMD-002`, `CMD-003`, `CMD-005`, `CMD-006`, `CMD-007`, `CMD-008`, `CMD-010`,
  `KOT-013`, `QLT-006`, `QLT-007`, `QLT-008`, `QLT-011`, `QLT-012`, `QLT-017`, `QLT-019`

## Context

ADR 0039 introduced a retained rectangular selection, exact same-layer Move/Copy, one ordinary
patch command and a shared composite preview. M4 also calls for bounded transforms. The owner
delegated requirements/design and requested continued work on 2026-10-12. Adding another editable
pixel buffer, transform command handler, preview arithmetic or history representation would create
a second implementation path for the same rectangle replacement.

## Decision

### Scope and interface

Add horizontal flip, vertical flip and clockwise 90-degree rotation to the existing temporary
selection bar. These operations transform the currently displayed rectangle; their order matters.
Move/Copy, further pointer translation, Apply and Cancel keep their existing meanings. No permanent
dock control is added. There is no arbitrary angle, resampling, scaling, drawing mask, selection
deletion, external/cross-layer clipboard or document-format expansion in this change.

The bar displays three localized, accessible controls with the existing minimum touch target.
They are unavailable before a rectangle exists or during an unfinished pointer interaction. The
displayed size follows the current destination orientation. Rotation is unavailable when its new
width or height exceeds the canvas, with a concise explanation of that condition. Direct action
input still receives a typed rejection rather than trusting a disabled presentation control.

### One orientation and transfer meaning

`PixelOrientation` is the closed domain vocabulary for the eight distinct orthogonal orientations:
Identity, RotateClockwise, HalfTurn, RotateCounterClockwise, FlipHorizontal, FlipVertical,
Transpose and Transverse. The rotation names mean quarter, half and three-quarter turns; the two
diagonal reflections complete the outcomes obtainable by composing rotation and flips. Those two
reflections need no additional UI controls. An orientation is always relative to the original source
rectangle, not to a repeatedly transformed pixel buffer.

`then(next)` composes the current orientation followed by `next` on the displayed rectangle. This
single domain operation owns composition. Four clockwise rotations and two identical flips produce
Identity. A horizontal flip after a quarter turn differs from one before it. The domain also owns
whether the source axes swap and the resulting validated rectangle size.

`PixelPlacement` groups a typed destination position and orientation, defaulting to Identity.
Its factory takes those two values. `PixelTransfer.create(canvas, source, placement, mode)` remains
a four-argument factory under KOT-013; every existing caller adopts `PixelPlacement` and retains
its Identity meaning. No old-position overload remains. Private constructors likewise stay within
the four-input limit using cohesive validated geometry, not a parameter-count waiver. The factory
validates the source and the orientation-sized destination against the declared canvas. Invalid
direct input is rejected without clamping. Equality and hashing include orientation. One derived
`isIdentity` property means identical source/destination origin and Identity orientation, independent
of whether a non-identity operation happens to produce equal cell contents. All early no-op and
preview-admission consumers use this property instead of repeating a displacement-only test.

`TransferPixelsCommand` and its existing handler remain the only document mutation. The engine's
shared final-cell rule maps each destination-local coordinate back to the original immutable crop.
For source width W and height H, destination-local (x, y) maps to source-local:

| Orientation | Source x | Source y | Destination size |
| --- | --- | --- | --- |
| Identity | x | y | W by H |
| RotateClockwise | y | H-1-x | H by W |
| HalfTurn | W-1-x | H-1-y | W by H |
| RotateCounterClockwise | W-1-y | x | H by W |
| FlipHorizontal | W-1-x | y | W by H |
| FlipVertical | x | H-1-y | W by H |
| Transpose | y | x | H by W |
| Transverse | W-1-y | H-1-x | H by W |

This inverse mapping lives in the pixel-engine final-cell rule used by both patch planning and
preview. It preserves exact `PixelCell` identity, including Empty, covered alpha-zero and distinct
indices with equal RGBA. It never interpolates, quantizes or blends source cells. Destination wins
over source overlap. Move clears S outside D; Copy only replaces D. S and D have equal area but may
have different dimensions. The existing ordered-union traversal already handles separate rectangles
and must continue to avoid their distant bounding-box gap.

Only a transfer whose placement and orientation are Identity can take the existing early no-op.
Other transfers run the ordinary effective-difference planner; symmetric/all-empty contents may
still produce NoEffectiveChange with unchanged redo, revision, dirty state and autosave. A nonzero
orientation at zero displacement is a valid preview and Apply candidate.

### Workspace and bounded placement

`SelectionDraft` adds orientation alongside its source and destination position. Workspace retains
no pixel copy. `WorkspaceAction.TransformSelection` composes its supplied `PixelOrientation` with
the current draft. It rejects no session, no rectangle, an active pointer, or a resulting size too
large for the canvas atomically; it does not cancel a drag merely to accept a competing transform.

Transformation keeps the current displayed destination's top-left position where possible. If a
quarter turn makes an edge extend beyond the canvas, clamp the whole new destination inside it.
If the new size exceeds an axis of the canvas itself, reject instead of clipping or resizing.
This top-left anchoring and edge clamp are explicit workspace behavior, not hidden factory behavior.
The clamp can change position near an edge; returning orientation to Identity does not promise to
undo that independently observable position adjustment. Cancel still discards the whole draft.

Pointer translation clamps using the oriented destination size. A new source rectangle starts at
Identity. Pointer cancellation restores the complete previous draft, including orientation, and
pointer-up retains it. Mode changes preserve the orientation. Apply sends the same transfer and
creates one ordinary patch/history entry; all existing command/source rejection, save/checkpoint,
other-mode invalidation, Undo/Redo and session cancellation contracts remain ADR 0039's.

### Rendering and cost

Orientation changes do not change the source crop or other prepared visible-layer surfaces. Reuse
those preparations under the existing document/layer/source key. The same final-cell inverse rule
feeds the existing target-layer Composite. Render and restore the actual old/new source/destination
rectangles, including width/height swaps; a displacement-only fast path must not hide a transformed
preview at the source origin. Returning to geometric Identity displays committed pixels with the
existing bounded cache lifetime policy.

Mapping adds O(1) integer work per affected cell, not an intermediate rotated pixel image. Crop
size, affected area, ordered patch traversal, packed entries, immutable snapshot publication and
logical sparse/dense history costs retain ADR 0039's meaning. Canvas-sized committed rendering,
Android bitmap backing and publication costs remain. Report the actual implementation and register
the orientation/rectangular-overlap/zero-offset workloads with #145. Performance collection remains
at the owner-directed M5 exit; no speedup or phase PASS is inferred from this contract.

## Rejected alternatives

- Mutating a rotated clipboard on each operation would accumulate copies and create another pixel owner.
- A separate transform command or preview-only Android matrix would duplicate replacement/composite semantics.
- Rejecting every non-square rotation would remove a useful exact operation without a domain constraint.
- Clipping transformed pixels or silently enlarging the canvas would change data beyond the selected operation.
- Treating equal origins as a no-op would drop all in-place flips and rotations.

## Consequences

The feature adds bounded orientation state and no storage migration. Exact no-op/undo/redo and
committed-document capture continue through the existing command path. Noncommuting operations and
different source/destination dimensions require an independent non-square oracle and preview
equivalence tests. Top-left anchoring avoids ambiguous half-pixel center conventions; edge clamping
is visible and may alter placement. A draft remains ephemeral until Apply.

## Enforcement impact

Verify all eight mappings with independently stated non-square expected cells, composition order,
four rotations/two flips, size/bounds and invalid direct input. Check Move/Copy overlap, Empty and
covered-zero identity, deterministic patches/inversion and genuine no-op redo preservation. Workspace
checks cover edge clamp, impossible dimensions, active-pointer refusal, oriented translation, cancel,
source reselection and one Apply. Preview must equal an ordinary committed composite and retain the
source preparation across orientation changes; bitmap tests detect stale old rectangular footprints.

One native transform journey and a bounded-canvas refusal case cover the added controls and actual
consumer behavior. Reuse unaffected #211 results with their original source/artifact/log identities;
do not rerun unrelated storage or pointer journeys. The final non-draft candidate requires canonical
CI check plus Android assembly, without a duplicate local full run.

## Migration and rollback

Project v3, recovery envelope3, palette data, layer count and history representation are unchanged.
Documents edited by this feature contain ordinary indexed cells and remain readable if the UI
feature is later removed. Identity-default callers continue through the same transfer implementation.

## Related

- ADR 0004, ADR 0009, ADR 0030, ADR 0031 and ADR 0039
- Issue: #213
- Supersedes: ADR 0039's deferral of flips/quarter turns and equal-size destination restriction only
- Superseded by: none

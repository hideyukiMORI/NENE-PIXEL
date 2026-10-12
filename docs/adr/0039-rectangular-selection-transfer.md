# ADR 0039: Rectangular selection, move and copy

- Status: accepted
- Date: 2026-10-12
- Issue: #211
- Affected rules: `ARC-001`, `ARC-004`, `ARC-005`, `ARC-007`, `ARC-008`, `ARC-010`, `ARC-011`,
  `CMD-001`, `CMD-002`, `CMD-003`, `CMD-005`, `CMD-006`, `CMD-007`, `CMD-008`, `CMD-010`,
  `QLT-011`, `QLT-012`, `QLT-017`, `QLT-019`

## Context

The ordered-layer, palette, PNG-import and layer-duplication features are functionally accepted
through main `bfd2eea` (#208 / PR #210). M4 includes selection, move, copy and bounded transforms,
but their detailed contract was deliberately deferred. The owner delegated design decisions and
requested continued feature work on 2026-10-12. The #145 phase performance collection remains at
the owner-directed M5 exit; this feature does not admit that collection or claim M4 completion.

The existing domain already has a validated `PixelRegion`; the engine has exact `PixelCell`,
`PixelPatch` and the shared Composite arithmetic. Application owns source admission, sparse/dense
`LayerIndexChanges`, one `ChangeSet` history and the persistence checkpoint. Selection must use
these meanings rather than create an editable clipboard document, a second pixel owner or a
presentation-only move algorithm.

## Decision

### First scope and interface

The first scope is one axis-aligned rectangle on one visible layer, moved or copied within that
same layer. It does not add a drawing mask, an external or cross-layer clipboard, delete, rotate,
flip, scale, frames or a new file schema. Those capabilities require their own focused refinements.

The existing layer-row More menu gains **Select area** (localized). It is disabled for a hidden
layer. Starting from a row selects that layer through the existing `SelectLayer` route and closes
the panel. There is no new always-visible dock control: area editing is a secondary operation under
the Charter's interface principle.

A one-finger drag selects an inclusive rectangle. A drag starting inside the displayed rectangle
adjusts its destination; a drag starting elsewhere on the canvas starts a new rectangle and drops
the old uncommitted adjustment. A down outside the canvas is ignored. Extending a selection clamps
to the canvas edges. Translation clamps the whole destination rectangle to the canvas instead of
cutting off pixels. Signed coordinate arithmetic handles extreme samples without integer overflow.

Releasing the pointer retains the draft, permitting several position adjustments before one
commit. A temporary bar provides Move / Copy, Apply and Cancel, a concise selected-size/status
description, and a short explanation that empty cells also replace their destination. Apply is
available only for a completed, effectively displaced selection. Apply ends the session after one
command; Cancel and Back end it without a document change. Existing overlays and dialogs consume
Back before the selection session. Choosing a drawing tool exits selection through the same cancel
action. The canvas shows a clipped, pixel-aligned outline, using the existing viewport transform.

### One exact transfer value and command

`PixelTransferMode` is the closed `Move` / `Copy` vocabulary. `PixelTransfer` is an immutable,
validated value containing canvas size, source `PixelRegion`, destination `PixelPosition` and mode.
Its canonical four-argument factory checks that both rectangles fit the declared canvas. It never
silently clamps direct command input. The workspace's pointer policy explicitly clamps raw input
before constructing this same value. The engine checks declared canvas identity against the
snapshot on which it operates; a region valid for some other canvas does not bypass this check.

`TransferPixelsCommand(admission, layerId, transfer)` has one handler. Source admission remains the
existing exact runtime-owner/document/history-position contract. The handler rejects a missing or
hidden layer, invalid canvas input, exhausted revision or history limits with typed outcomes.

Let S be the source rectangle, D its translated destination and delta the translation. Every
source value is read from the same immutable starting layer snapshot, never from an earlier write:

1. In D, the final cell is the starting cell at position minus delta.
2. For Move, a cell in S outside D becomes `Empty`.
3. Every other cell is unchanged. Copy changes D only.

Destination precedence defines all overlap directions. `Empty` replaces the destination exactly;
covered alpha-zero slots remain covered with their original palette index and hidden RGB meaning.
There is no palette lookup for conversion, blending, quantization or skip-transparent alternative.
The mode and geometry planner, command and preview share this one final-cell rule.

The pixel engine produces one sorted patch containing only effective differences. It traverses
the ordered union of the rectangles, not the potentially much larger bounding box between them.
Zero offset, equal cells or all-empty replacements may return `NoEffectiveChange`, with no revision,
dirty, autosave, history or redo change. A direct no-op command still follows the typed command path.

The handler feeds that patch into the existing `LayerIndexChanges.select`, one `LayerChange` and
`ChangeSet`. Sparse/dense selection, byte accounting, bounded eviction, inverse/redo, render
invalidation and autosave remain canonical and unchanged. No special transfer-history payload or
workspace information is saved in history.

### Workspace draft, input and cancellation

The workspace owns the selection session, captured source admission, target layer, source/destination
geometry, Move/Copy mode and pointer phase. It owns no copied pixel payload. Any source crop or
prepared composite belongs only to a disposable render cache. Presentation translates events into
typed selection actions and projects this state; it does not hold a competing rectangle or offset.
Beginning a session is atomic with the canonical active-layer selection.

Pointer cancellation and session cancellation have distinct meanings. Pointer cancellation restores
the selection/offset from before that unfinished drag and is a no-op when no drag is active. Session
cancellation forgets the whole draft. This distinction is necessary because the existing pointer
input finally block sends cancel after a successful release. System cancel, an extra pointer and
surface changes must never apply pixels. Two-finger viewport interaction cancels the unfinished
drag and uses the existing viewport path while preserving a draft retained by an earlier release.
Layout/viewport changes reproject that geometry; the actual-size window remains a committed view.

The application selection-operation boundary applies the current draft under the existing runtime
lock and dispatches exactly one `TransferPixelsCommand` through the ordinary command gateway. The
successful command's common runtime follow-up ends the draft. A no-op application also closes the
session without clearing redo; a rejection leaves the document unchanged and is reported through
the existing typed result boundary.

Selection does not lock out unrelated document commands. Every successful command, including
Undo/Redo and commands invoked by internal transactions, cancels the draft in the shared locked
post-application reconciliation. Rejected commands do not silently discard it. Document replacement
starts without selection. Entering a palette edit, completing a pending PNG import, choosing a
different active-layer/tool/eyedropper mode, successfully opening quick-select or entering underlay
adjustment cancels through the workspace reducer. Merely opening a palette panel or selecting a
palette slot retains the draft; beginning the palette edit is the cancellation boundary. Internal
asynchronous workspace reductions use the same cancellation semantics;
they must not reject an import after its transport has reported successful completion. A selection
session cannot begin over an active palette edit or pending import. After validating the target and
those exclusions without mutation, explicit entry cancels an unfinished stroke, closes quick-select,
disarms the eyedropper and rests underlay adjustment through their canonical actions in the same
runtime lock. The existing `SelectLayer` action and selection entry then complete atomically.
`CanvasPointerIntent.SelectPixels` joins the closed pointer vocabulary; the reducer prevents it
from competing with those other pointer modes.

Save, PNG export and autosave keep their existing captured committed-document behavior and may
coexist with a draft. A later transfer leaves a save of an earlier capture dirty as before.
The existing final document-Switching exclusion applies; the color-editor-specific physical lease
rule is not generalized to selection. Runtime owner effects that cancel preview or replace owners
also cancel the applicable selection interaction/session without bypassing the reducer.

### Preview and measured-path work

The preview must equal the visible composite of committing the transfer at the displayed offset,
including hidden RGB, partial alpha, empty cells and layers above or below the target. It replaces
the target layer cell before evaluating the existing integer `contributeOver` rule. A translucent
bitmap over the committed image would be incorrect for clearing source cells or upper layers.

The existing stroke preview and the new transfer preview share the per-position target-layer
composite kernel; a multicolor transfer is never represented as a sequence of single-color strokes.
Preparing a session may capture one immutable selected crop and copy the other visible surfaces
once. Offset or mode updates reuse those preparations. The preview raster restores and updates the
bounded old/new source/destination regions, not their distant combined bounding box. Unchanged
state references incur no repaint or bitmap transfer. The existing stroke fast path keeps its
meaning and cost; focused parity evidence covers any shared helper extraction.

For canvas area N, selection area A and visible layer count V, planning visits at most the bounded
union of source/destination cells, O(A), with at most N patch changes. Preparation and application
still have real costs: immutable snapshot/coverage copying and validation, prepared surfaces,
O(A * V) affected-position composite evaluation, raster restoration and Android bitmap transfer.
Existing patch application, dense retention and ordinary committed composite may remain O(N) or
O(N * V). Report the actual implementation's allocations, copied bytes, update regions and history
charge; do not call the whole operation O(A) merely because its planner is. Register the finished
workload with #145. There is no feature-level performance run or Profile generation.

## Rejected alternatives

- An independent editable clipboard or RGBA layer would duplicate pixel truth and bypass exact
  indexed/Empty and history meanings.
- In-place source reads would corrupt overlapping moves and make traversal order observable.
- Ignoring empty cells or using Android bitmap source-over would change the rectangle's contents.
- Commit on every release would prevent several precise adjustments from forming one history step.
- A second full-canvas pointer overlay would compete with the existing two-finger/cancel arbitration.
- Blocking every physical storage operation would contradict the existing capture/checkpoint contract.

## Consequences

Selection is useful without a storage migration and composes with existing undo, recovery and export.
It adds workspace state and a second kind of preview, so source invalidation and pointer/session
cancellation must be checked at shared runtime boundaries. Empty-cell replacement needs visible
wording. A retained draft is deliberately ephemeral and disappears on replacement or process loss;
only Apply creates recoverable document truth. Performance is not accepted until the phase gate.

## Enforcement impact

Before execution, record a diff-derived check plan. Focused tests cover value and clamp bounds,
overlap in all directions, Empty/alpha-zero identity, no-op/redo preservation, one exact inverse,
sparse/dense accounting, source admission and hidden/missing layer rejection. Workspace/runtime
tests cover repeated adjustment, pointer versus session cancel, mode change, internal command and
import invalidation, save checkpoints and final Switching. Shared composite/raster tests prove
preview equals the ordinary committed composite and prevent unnecessary whole-canvas repaint.
Native acceptance covers row-target entry, select/adjust/apply/Undo/Redo, Copy, Cancel/Back and the
affected two-finger/system-cancel boundary. Run no unrelated broad journeys; reuse unchanged successes.
The final non-draft PR retains the required canonical CI check and Android assembly.

## Migration and rollback

Project v3, recovery envelope3, palette data and existing history meanings are unchanged. All live
consumers adopt the new action/command family together. Removing the feature later must not affect
documents already edited by it; they contain ordinary indexed cells and require no migration.

## Related

- ADR 0004, ADR 0007, ADR 0009, ADR 0016, ADR 0029, ADR 0030 and ADR 0031
- Issue: #211
- Supersedes: no existing file-format or drawing-tool contract
- Superseded by: none

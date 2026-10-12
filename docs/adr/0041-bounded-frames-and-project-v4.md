# ADR 0041: Bounded frames and project v4

- Status: accepted
- Date: 2026-10-12
- Issue: #215
- Affected rules: `ARC-001`, `ARC-004`, `ARC-005`, `ARC-007`, `ARC-008`, `ARC-009`,
  `ARC-010`, `ARC-011`, `CMD-001`, `CMD-002`, `CMD-003`, `CMD-005`, `CMD-006`,
  `CMD-008`, `CMD-009`, `CMD-010`, `KOT-003`, `KOT-007`, `KOT-013`, `QLT-006`,
  `QLT-007`, `QLT-008`, `QLT-009`, `QLT-011`, `QLT-012`, `QLT-015`, `QLT-019`

## Context

M4 still needs frame editing and animation after ordered layers, palette editing, PNG import and
selection transforms. ADR 0022 reserves stable frame identities with one shared palette; ADR 0030
currently stores one ordered layer list. hide delegated design and execution on 2026-10-12 and
requested continuous progress. The design seat accepts this bounded first frame model. The #145
performance collection remains at the owner-directed M5 exit.

This is an accepted target contract, activated by the atomic implementation described below.
Until that cutover, the existing single-frame runtime and v3 writer remain the sole implemented
path. This design-only change adds no unused public types, compatibility getters or alternative
writer. It needs documentation verification, not app/device execution.

## Decision

### One document, ordered frames, shared palette

`DocumentState` owns its identity, revision, one shared `PaletteDefinition` and an ordered nonempty
list of `Frame` values. A `Frame` owns `FrameId`, `FrameDuration` and a bottom-first nonempty list of
`Layer` values. Canvas size remains derived from the validated snapshots; every layer of every
frame has that size and uses the one shared palette. `Layer` and `PixelSnapshot` retain their exact
indexed/Empty meanings. The former document-level layer-list property is removed with all callers.
A single-frame construction convenience, if useful to existing fixtures, must forward to this one
representation and must not expose a second editable path.

| Value or resource | Bound / meaning |
| --- | --- |
| Frames per document | 1 through 16, chronological order |
| Layers per frame | 1 through 16, bottom first |
| Total layer records across the document | At most 16; logical records, even when immutable snapshots are shared |
| FrameId | Positive Int, independent of ordinal; unique in the document |
| LayerId | Remains positive and globally unique across every frame in the document |
| FrameDuration | Integer milliseconds, 10 through 60,000; default 100 ms |
| Canvas/palette/history | Existing axis 256, area 65,536, 2–256 palette entries, 64 history entries, sparse-change and 8 MiB logical-payload caps |

The global record cap preserves the maximum live packed pixel payload:
`16 * (65,536 + 8,192) = 1,179,648` bytes. It is not an independent 16-by-16 allowance. A 16-layer
legacy work cannot add a frame until records are removed; 16 frames can have only one layer each.
The interface must show the document-wide remaining capacity. No automatic flattening, clipping,
palette reduction or loss of layers makes room. Expanding this bound is a later explicit decision.

New frame IDs use the document's current maximum plus one; new layer IDs use the maximum across
all frames plus one. Duplicate-frame allocation reserves a consecutive block for all its layers
and rejects atomically if any ID would exceed Int.MAX_VALUE. Neither scope allocates by row index
or by the active frame's local maximum. IDs may be reused after their old records leave the current
document, as with existing layer IDs; exact command-source/history admission prevents stale reuse.
Undo/Redo restores the recorded IDs, never allocates again. There is no saved frame name, filename
reference, palette per frame, frame-specific canvas or frame-specific underlay.

### Commands, transition and history

The existing gateway, one command family/handler route, `DocumentTransition`, `ChangeSet`, linear
history and post-command follow-up remain authoritative. New source-admitted frame commands are:

| Command | Exact effect |
| --- | --- |
| AddFrameCommand | Insert after the named frame; default duration 100 ms; one visible all-Empty layer with empty name and fresh global layer ID |
| DuplicateFrameCommand | Insert after the named source; preserve duration and every ordered layer's name, visibility and exact cells; fresh frame and layer IDs |
| DeleteFrameCommand | Remove the named frame; reject removal of the last frame |
| MoveFrameCommand | Move the named frame to a zero-based position in the resulting chronological list; same position is NoEffectiveChange |
| SetFrameDurationCommand | Replace a validated duration; identical value is NoEffectiveChange |

Each effect is one command, one revision and one history entry. Missing frame, invalid position,
last frame, global capacity, frame/layer ID overflow, revision overflow and stale admission are
typed atomic rejections. Existing runtime exclusions remain applicable. Duplication shares only
immutable snapshots initially; editing either copy replaces that layer's snapshot independently.

One hierarchical structural transition replaces the unscoped layer transition: no structure,
a frame-targeted existing layer transition, or frame added/deleted/moved/duration-changed. Pixel
changes keep their globally unique LayerId addressing and resolve its one owning frame. The
transition validates one before revision, applies the existing palette/pixel and structural
changes, validates the complete resulting hierarchy and issues one after revision. No frame-local
gateway, undo stack, mutable document, compound sequence of per-layer commands or parallel apply
route is introduced. Add/duplicate/import-layer limits and ID allocation scan the whole document;
last-layer protection is per frame. A palette remap still covers all layers, including every hidden
layer in every non-active frame, in one command.

Logical history accounting retains the existing 32-byte ChangeSet header, palette formula,
per-layer sparse `6*K` versus dense `2*(N+ceil(N/8))` choice and eviction policy. New structural
payload beyond that header is charged as follows; integers are logical fixed-width fields, not an
estimate of the JVM heap:

| Structural payload | Additional logical bytes |
| --- | ---: |
| Frame-targeted layer transition | 4 for FrameId plus the existing layer-transition charge |
| Frame added or deleted | 12 for frame ID, duration and position, plus sum over its layers of `6 + UTF8(name).size + N + ceil(N/8)` |
| Frame moved | 12 for ID and before/after positions |
| Frame duration changed | 12 for ID and before/after durations |

Whole-frame insertion/deletion charges every retained snapshot, including all-Empty and shared
ones; inverses have the same frame payload charge. It does not extend the existing all-Empty
single-layer add shortcut to an arbitrarily retained frame. Lists are bounded by the same global
16-record cap. Existing layer-structure accounting is otherwise unchanged. The full 16-layer dense
palette remap still charges 2,359,296 pixel bytes; frame metadata does not duplicate those pixels.
Both retained-byte admission and exact inverse/replay are checked at their existing boundaries.

### Workspace focus and cancellation

The workspace `EditTarget` atomically owns active FrameId, LayerId and palette slot. It is not
stored in project/recovery or document history. A new/load/recovered installation selects the first
chronological frame and its top layer. `SelectFrame` is the sole frame-choice action: a different
valid frame selects its top layer; selecting the already active frame is Unchanged and retains its
layer. There is no per-frame remembered-layer map. `SelectLayer` is scoped to the active frame and
rejects a layer belonging to another one.

Changing frames cancels an unfinished stroke, selection session and competing pointer mode through
the canonical reducer, including underlay adjustment and quick-select interaction. No release from
the old frame can commit onto the new one. The one underlay image/placement remains for the work.
Frame choice preserves a document-bound palette edit session and its draft timeline: the same
draft palette may be previewed on another frame, using a frame-aware cache. It neither applies nor
cancels that palette draft. A pending PNG import keeps its captured target described below.
Actual-size follows the chosen frame; it excludes stroke/selection drafts while preserving the
existing shared palette-draft preview contract of ADR0022.

Post-command reconciliation runs under the existing runtime lock for ordinary commands and
Undo/Redo alike. Inserting a frame (including undo of deletion) selects it and its top layer.
Deleting the active frame selects the frame now at its old position, clamped to the last, and its
top layer; deleting another frame retains valid focus. Reorder and duration changes retain valid
focus. An inserted layer selects its owning frame and itself. Removal of the active layer selects
the layer at its former position, clamped to that frame's top. Other changes retain valid focus and
the existing palette-slot reconciliation. A successful command still cancels drawing/selection
drafts through the shared follow-up; no UI-only focus repair substitutes for it.

### Rendering and asynchronous operations

The committed composite, stroke/selection preview, palette draft preview, bitmap cache and
actual-size projection take explicit frame identity. They composite only that frame's layers
through the existing integer pixel-engine rule. Cache keys include the frame wherever a focus
change can alter output without changing document reference/revision. Disposable preparations
retain at most the active frame, with no eager bitmap/crop/composite for all frames. Old frame
preview footprints must be cleared when focus changes. Global LayerId remains sufficient for a
captured pixel target, but is not a replacement for the displayed-frame cache key.

PNG export captures the active FrameId and immutable committed DocumentState together inside the
runtime lock when the operation begins. The application-owned export capture is the one port
input. No-visible-layer rejection examines that captured frame, even if another frame is visible.
The picker, later focus changes, drafts or future playback cannot retarget it. The single PNG
remains the same bounded RGBA/sRGB raster, maximum 263,756 bytes; no animation/sprite-sheet export
is enabled. Its single-frame output remains byte-identical to the corresponding prior composite.

PNG import captures the active frame and insertion-anchor LayerId with its existing admitted
document/planning source when the pick begins. Choosing the layer form inserts above that captured
anchor in that captured frame and selects the result. A focus-only change while the picker or
confirmation is open does not retarget it. For the layer form, a document/history change invalidates
the admitted plan through the existing stale-source path; missing targets reject rather than
choosing a substitute. New-work import creates one default-duration frame and retains the existing
current-context confirmation/final-switch flow; its new document is not bound to the picked-from
document's frame/anchor/history. A pure raster/palette plan does not acquire a second live workspace
owner merely to carry a destination.

Save As, autosave and recovery still capture the whole committed document and its exact
history/runtime-generation identity. Focus changes neither dirty nor enqueue autosave; frame
commands do, through the same follow-up. No frame-specific storage lease, writer, checkpoint or
background queue is added. Existing source-preservation and final-Switching contracts are unchanged.

### Project v4 and recovery envelope 4

[Project Format v4](../PROJECT_FORMAT_V4.md) is the exact wire authority. Its frame count and
per-frame IDs, durations and layer counts precede the unchanged v3 layer records. The global
record cap is enforced before pixel allocation. Write-current becomes v4 only with the atomic
runtime cutover; there is no old-format writer accepting the new editable model.

V3 reads exactly into frame ID1, duration100 ms, preserving document identity/revision/palette and
all layer IDs/order/names/visibility/coverage/indices. V2 maps its existing fully covered layer into
that same default frame. V1 remains the existing uninstalled LegacyRgbaSource with its explicit
preservation/conversion flow; conversion produces one default frame. Lossless user-file migration
does not create a command/history entry or dirty checkpoint. Old files/recovery are not rewritten
merely by inspection or loading.

The exact maximum v4 file is 1,183,006 bytes and probe 1,183,007. It occurs at 16 frames with one
maximum-name layer each, distinct from maximum per-frame compositing (one frame with 16 layers).
Recovery keeps the same outer fields/generation/CRC and file identity; envelope4 carries exactly
project4. Readers keep all exact 1/1, 2/2 and 3/3 pairings, reject mismatches, and reject future
versions without fallback. New Candidate and Retired writes use envelope4. Retired stays23 bytes;
Candidate ranges94..1,183,029, probe1,183,030. Carrier, SAF read/read-back, recovery length policy,
dispatch and verification switch together; each older decoder retains its own smaller limit.

### Secondary frame interface

The existing layer-chip entry opens the layer panel. A Frames action in its heading opens a
separate secondary panel showing chronological rows labeled by localized ordinal and duration,
with stable FrameId keys. Selecting a row chooses the frame and returns to its layer panel. Row
More holds Duplicate, Move earlier/later, Duration and Delete; a fixed footer adds a blank frame.
Disabled actions derive from the actual frame count, global layer-record capacity, positions and
last-frame policy. The panel shows capacity as a document-wide limit, including why an existing
16-layer work cannot add a frame. Layer Add/Duplicate use that same capacity projection.

The bounded panel keeps the existing maximum360dp width, viewport-aware height, scrolling rows and
48dp actions. Duration editing uses a localized integer-ms input, explicit confirmation for one
command, validation at10..60,000, and Cancel/Back with no command. Thumbnail generation and a new
always-visible timeline/dock are not required. The chosen frame is shown in the layer-panel
heading; no permanently visible control is added. All normal app languages and accessibility
descriptions are updated in the consuming implementation, including obsolete single-frame copy.
When there are multiple frames, the palette editor provides a48dp selection-only frame chooser
above its existing scrolling controls, inside the same dialog. The row shows the localized preview
frame ordinal/count and opens a compact dropdown. It dispatches the same SelectFrame action while
retaining that dialog and its palette draft; it does not issue frame commands or require dismissing
a changed draft to preview it on another frame. It is disabled during a continuous color gesture,
like the existing competing palette controls. Apply/Cancel and draft Undo/Redo remain fixed.

### Delivery and playback boundary

1. This focused design Issue accepts the hierarchy, wire contract, interaction and cutover plan.
2. Focused implementation Issue #216 migrates domain, commands/history, workspace, every renderer and
   import/export consumer, v4/envelope4 and the frame-editing UI together. Every live consumer
   compiles against the one hierarchy. The first release can create/edit/save multiple frames;
   there is no temporary unsavable-frame UI or dual layer/frame document.
3. Separate playback Issue #217 first accepts its clock/interaction contract, then implements preview
   playback. The fixed boundary is one workspace-owned playback session, explicit monotonic
   platform samples, one platform lifecycle clock and the saved duration/order as timing data.
   Ticks are never commands/history/autosave or independent Compose frame counters. Loop/once,
   start/stop/focus restoration, delayed pulses, edit/picker/Back/lifecycle behavior and bounded
   scheduling are decided there before code. No unused playback API is added by frame editing.

Layer merge, saved opacity, onion skin, frame names, sprite sheets, animated exports and tile/maps
remain later decisions. In particular this ADR supersedes ADR0030's tentative assignment of saved
opacity to v4: v4 contains frames only, and opacity needs a future version/ADR. No field is reserved.

### Performance evidence and historical fixtures

Frame editing registers source-derived cost with #145, covering bounded ID/hierarchy scans, logical
history, full-palette remap, focus/cache replacement and maximum-byte save/Candidate publication.
No feature-level collection or Profile regeneration occurs. Existing one-frame16-layer drawing
workloads and historical v3 fixture/hash remain immutable. A new v4 maximum-byte fixture and
publication workload have their own versioned identity; no old artifact is regenerated in place or
relabeled as v4. The implementation must keep mandatory fixture correctness checks connected while
adapting consumers, and reconcile the accepted #145 protocol with its executable harness before
any later collection. A v3 pinned draw fixture can be decoded losslessly into the new hierarchy.

ADR0018's250ms decision boundary and formulas remain: if the prospectively selected maximum
publication exceeds250ms, `quiet=ceil(4*max/500)*500` and `cap=ceil(20*max/500)*500`. The existing
1000/5000ms constants remain until valid #145 collection decides otherwise. V4 maximum publication
is a new registered workload, not accepted by v2/v3 timings or by its similar byte size.

## Rejected alternatives

### Independent sixteen-frame and sixteen-layer capacities

This permits256 pixel records and multiplies live/dense-history/storage bounds by16 without
evidence or a product requirement. The first bounded model preserves the accepted pixel envelope
and exposes its capacity tradeoff. A later increase requires an explicit resource decision.

### Keep document.layers as a live alias or save frames as external files

An active-frame alias makes document semantics depend on workspace state, breaks immutable save
capture and hides non-active frames from remaps. Filename references break identity on rename and
require competing persistence ownership. The sole hierarchy and one versioned file avoid both.

### Implement playback, opacity and animated export in the same change

They add independent timing, blending and external-format decisions. Saved duration makes the
first frame editing useful and prepares the required truth without unused APIs for later behavior.

## Consequences

### Benefits

- Frames share the exact existing palette, pixels, command/history, composite and persistence path.
- Existing layered works migrate without losing indexed cells, names, identities or visibility.
- The first frame model has explicit limits, asynchronous target capture and bounded cache lifetime.

### Costs and risks

- The atomic hierarchy cutover touches every direct consumer; source inventories and narrow
  contract tests must cover them before the final required CI gate.
- Total-record capacity limits layered animation. It must be visible before Add/Duplicate can fail.
- Frame-switch and v4 publication costs remain unmeasured until #145; unchanged payload caps do not
  establish performance acceptance.

## Enforcement impact

Domain tests prove count/global-ID/canvas/palette/duration bounds and immutable hierarchy. Command
tests prove every typed rejection, atomicity, exact inverse/replay, structural accounting and global
capacity/ID allocation. Runtime tests prove focus reconciliation, cancellation, all-frame palette
remap, captured PNG targets and whole-document clean/autosave behavior. Renderer tests compare the
same document under two frame keys and assert bounded preparation lifetime. Codec tests use
independent v4 goldens, strict limits/checksum/allocation order, exact old migration and matched
recovery pairs. Focused native tests cover actual frame UI/UndoRedo, duration validation, focus/canvas
and short-height controls. Low-level unchanged algorithms reuse their recorded evidence (QLT-012).

No dependency/module/quality-rule/waiver changes. Documentation validation suffices for this
design's development checks; its final non-draft PR still requires canonical CI quality, without a
duplicate local full run. Implementation selects changed contracts before execution and registers
cost before merge. Existing mandatory tests and historical failure evidence are not disconnected.

## Migration and rollback

The consuming implementation changes every live owner/consumer once, removing document-level
editable layers and switching all current writes to v4/envelope4. V1/v2/v3 byte authorities and
goldens remain immutable. A rollback after any v4 data has been written must retain the v4 reader
and exact stored data; it cannot ship a reader that strands that work. No silent downgrade/export
to v3 is provided. Before that release, reverting the design alone changes no stored data.

## Related

- Issue: #215
- PR:
- Supersedes: ADR0030 document-level layer ownership, relevant workspace focus and write-current
  v3/envelope3 contracts at atomic cutover, and its tentative saved-opacity assignment to v4;
  ADR0019/0033 single-frame PNG target assumptions
- Superseded by: none

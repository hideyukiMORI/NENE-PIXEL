# ADR 0030: Ordered layers, empty pixels and project v3

- Status: accepted
- Date: 2026-09-28
- Issue: #140
- Affected rules: `ARC-001`, `ARC-004`, `ARC-005`, `ARC-007`, `ARC-008`, `ARC-009`, `ARC-010`,
  `ARC-011`, `ARC-012`, `CMD-001`, `CMD-002`, `CMD-005`, `CMD-006`, `CMD-007`, `CMD-008`, `CMD-009`,
  `CMD-010`, `CMD-012`, `KOT-007`, `KOT-012`, `KOT-013`, `QLT-006`, `QLT-011`, `QLT-014`, `QLT-015`,
  `QLT-016`

## Context

M4 lists multiple ordered layers, visibility, an opacity policy and reorder commands
(`MILESTONES.md`). Gate C is satisfied by the M3 exit proof, and the palette packages P4-00 through
P4-04 are complete (#108 / PR #139, main `a2a8f37`). The current document is single-layer by type:
`DocumentState` owns `id`, one `PaletteDefinition` and one `PixelSnapshot` of packed U8 indices
(ADR 0025). Eraser writes `defaultIndex` (ADR 0005, ADR 0022, CMD-005), so erasing to transparency
depends on the palette. `PixelLimits` caps one patch at 65,536 changes, history at 64 entries,
524,288 retained changes and an 8 MiB logical payload of 6 bytes per indexed change (ADR 0005,
ADR 0009, ADR 0022). Project v2 is at most 66,605 bytes and recovery envelope 2 carries exactly a
project-v2 payload (ADR 0025). PNG export is RGBA truecolor from `DocumentState` (ADR 0019).
`WorkspaceReducer` and `ProjectFormatV2Decoder` have 11 member functions and `EditorCallbacks` 10,
against the detekt `TooManyFunctions` limit of 11. Evidence: `docs/reports/2026-09-28-140-probe.md`
(local, untracked), rechecked by the design seat.

The repository owner decided on 2026-09-28: at most 16 layers per document; the first scope is
add, delete, reorder, rename, show/hide, active-layer selection, drawing on the active layer,
project v3 and PNG export of the visible composite, with duplicate and merge-down deferred; no
saved layer opacity yet, but a recorded plan for it; a panel that opens from the work area and shows
only the active layer name while closed; **erasing makes a pixel fully transparent**; visibility is
undoable document truth; hidden layers reject drawing and eyedropper picks; export with no visible
layer is rejected; partial-alpha overlaps may produce composite colors outside the palette.

## Decision

### Document structure

- `DocumentState` owns `id`, one shared `PaletteDefinition`, one document `Revision` and an ordered,
  non-empty list of at most `LayerLimits.MAX_LAYERS = 16` `Layer` values, bottom first. The single
  `snapshot` property is removed and the revision moves from `PixelSnapshot` to `DocumentState`;
  every consumer moves in the same change. `PixelPatch` no longer carries revisions and
  `PixelSurface.snapshot()` takes none: `DocumentTransition` checks the document `beforeRevision`
  once for the whole `ChangeSet`, then applies every per-layer change and issues one `afterRevision`.
  Stroke rasterization and patch application gate on that single document check, never per layer. No compatibility getter
  or second single-layer path remains (ARC-001).
- `Layer` holds `LayerId`, `LayerName`, `LayerVisibility` (`Visible` / `Hidden`, KOT-007) and one
  `PixelSnapshot`. Every layer has the document size. Construction validates unique ids, the count
  bound, equal sizes and palette membership of every covered index across all layers.
- `LayerId` is an `Int` in `1..Int.MAX_VALUE`, unique in the document and independent of order and
  name. A new layer takes the current maximum id plus one; when the maximum is already
  `Int.MAX_VALUE`, adding is a typed rejection.
- `LayerName` holds 0 through 32 Unicode code points with no control characters. The domain and the
  format carry no language: an empty name is displayed by presentation as the localized
  "Layer {id}".

### Empty pixels and Eraser

- A pixel cell is `Empty` or a covered `PaletteIndex`. `Empty` is fully transparent and independent
  of the palette. All 256 slots remain usable.
- `PixelSnapshot` owns packed U8 indices plus a packed one-bit-per-pixel coverage mask (row-major,
  least significant bit first). An `Empty` cell stores index byte zero. Both arrays are private,
  immutable after construction and copied on bulk read (ARC-005). `indexAt` returns a typed cell
  result; no caller reads a raw byte.
- One closed `PixelCell` value (`Empty` / `Covered(PaletteIndex)`) replaces bare `PaletteIndex` in
  every pixel-change type: `PixelChange` and `PixelPatch` before/after values, rasterizer output and
  `indexAt`. `StrokeEffect.Erase` carries no index; `StrokeEffect.Paint(PaletteIndex)` is unchanged.
- Eraser writes `Empty` to the active layer. This supersedes the Eraser target in ADR 0005,
  ADR 0022 and CMD-005. New documents and new layers start all `Empty`. With the default preset
  (transparent-black default) the new-document appearance and PNG bytes are unchanged.
- `defaultIndex` keeps its palette roles: the default survivor for slot deletion, the palette JSON
  default and remap bookkeeping (ADR 0022, ADR 0023). The legacy-conversion disclosure of the
  default slot's "erase color" (ADR 0022) is removed because erasing no longer uses it.
- Palette remaps map covered cells only; `Empty` is never remapped. `PickPaletteEntryAt` on an
  `Empty` cell returns a typed rejection and changes no selection.

### Composite

One composite function in `:core:pixel-engine` defines the visible image. The committed canvas,
actual-size window, stroke preview, palette remap preview and PNG export all use it. Android canvas
blending never substitutes for it. Showing the finished Composite over the display-only
transparency backdrop of [ADR 0026](0026-actual-size-window.md) is not compositing: the backdrop
is no layer and never reaches export.

While a gesture runs, every position it has touched shows the composite that committing the gesture
would produce: the layers below, the cell the stroke writes in place of the target layer's cell,
and the layers above. Untouched positions show the committed image. No preview-only tint or eraser
colour exists (owner decision, 2026-09-29), so a stroke hidden by an upper layer stays hidden while
it is drawn and an erased cell shows what lies below it. The per-position evaluation uses the same
validation, the same first-contribution rule and the same blend as the whole-image composite.

For each pixel the result starts with no contribution. Visible layers are visited bottom to top.
An `Empty` cell or a covered color with alpha 0 contributes nothing once a contribution exists.
The first covered color is copied exactly, hidden RGB included. After that, a color with alpha 255
replaces the result. A partial alpha `Sa` over result `(Dc, Da)` uses straight-alpha source-over
in integers:

- `N = Sa*255 + Da*(255 - Sa)`
- `outA = (N + 127) / 255`
- `outC = (2*(Sc*Sa*255 + Dc*Da*(255 - Sa)) + N) / (2*N)` for each of R, G and B

A pixel with no contribution is `(0, 0, 0, 0)`. Consequently a single visible layer composites
byte-for-byte to the v2 image of the same cells, and a partial-alpha overlap may produce an RGBA
value outside the palette. Layer pixels remain palette indices; only the composite leaves the
palette. PNG stays RGBA truecolor (ADR 0019). Export when no layer is visible returns the typed
`NoVisibleLayer` outcome.

### Workspace

- `WorkspaceState.activeLayerId` names the drawing, erasing and eyedropper target. It is not saved.
  Load and new start at the top layer. `WorkspaceAction.SelectLayer` is the only selection route;
  selecting a missing id is a typed rejection.
- When an applied change inserts a layer (a successful add, the redo of an add, or the undo of a
  delete), the inserted layer becomes the active layer.
- When a document change removes the active layer, the reconciliation that already runs inside the
  runtime lock for palettes (`ReconcileDocumentPalette`) also moves the selection. It picks the
  layer now at the removed position, clamped to the top, so the result is deterministic.
- `BeginGesturePreview` and `PickPaletteEntryAt` are rejected while the active layer is `Hidden`.
  A gesture captures its target `LayerId` with the effect and admission. Later selection changes
  do not alter it (CMD-005).
- The workspace-only "dim or hide all editable layers" display of ADR 0022 remains a separate
  future workspace state, outside this ADR.

### Commands and history

- New `DocumentCommand` types: `AddLayerCommand` (above the active layer, all `Empty`, id assigned
  by the handler), `DeleteLayerCommand` (rejects deleting the last layer), `RenameLayerCommand`,
  `MoveLayerCommand` (id and target position: the layer's zero-based position counted from the
  bottom in the resulting order, `0..count-1`; its current position is `NoEffectiveChange`, anything
  else outside the range a typed rejection) and `SetLayerVisibilityCommand`. Each has one handler
  (CMD-003). Identical values are typed `NoEffectiveChange` with no revision, history or dirty change.
- `ApplyStrokeCommand` carries its target `LayerId`. `ReplacePaletteCommand` remaps every layer,
  hidden layers included. Drawing, palette and layer operations share one linear history
  (CMD-008). An inserted layer is selected through the same post-command workspace path that
  palette application already uses, whether it arrives by an add, a redo or an undo.
- `ChangeSet` records one layer-structure transition (none, added, deleted, renamed, moved or
  visibility) and per-layer `IndexChanges`. The deleted layer's snapshot, id, name, visibility and
  position are retained so that undo restores it exactly.
- Per layer, pixel changes are retained as whichever is smaller: a **sparse** patch (6 bytes per
  change; the position word carries both coverage bits) or a **dense** pair of shared before/after
  `PixelSnapshot` references, charged `2 * (pixelCount + ceil(pixelCount / 8))` bytes. Sparse is
  used when `6 * changes <= dense bytes`. The choice is deterministic.
- Limits: `MAX_PATCH_CHANGES` applies per layer per command. `MAX_RETAINED_CHANGES` counts sparse
  changes only. The 8 MiB payload bound counts everything, including 32 bytes per transition, the
  existing palette-transition formula, `pixelCount + ceil(pixelCount / 8)` plus name bytes for a
  deleted layer, and name bytes for an add or rename. An added layer that holds at least one covered
  cell is charged like a deleted layer (amended 2026-10-01, Issue #176,
  [ADR 0033](0033-png-import.md)); an added all-`Empty` layer keeps the name-only charge. A worst-case remap of 16 full 256 by 256
  layers charges 2,359,296 pixel bytes, inside the bound.
  This amends the ADR 0005, ADR 0009 and ADR 0022 accounting.

### Project v3 and migration

Project v3 is big endian like v1 and v2 (ADR 0015) and has this layout:

| Field | Size |
| --- | --- |
| magic `NENEPIX\0`, version `3`, width, height, document id, revision, palette count, default index, palette | as v2 (41 + 4 × count) |
| layer count | U8, 1..16 |
| per layer, bottom first: id | U32, 1..2³¹−1, unique |
| flags | U8; bit 0 = visible; other bits must be 0 |
| name length | U8 UTF-8 bytes, ≤ 128, ≤ 32 code points, no control characters |
| name | UTF-8 |
| coverage | ⌈pixels / 8⌉ bytes; bits beyond the last pixel must be 0 |
| indices | pixels bytes; `Empty` cells must be 0; covered cells must name a palette slot |
| CRC32 | 4 |

The maximum is 41 + 1,024 + 1 + 16 × (6 + 128 + 8,192 + 65,536) + 4 = **1,182,862 bytes**. Any
violation is a typed format rejection; unsupported versions never fall through (ARC-009, ARC-010).

- Write-current becomes v3. The v1 and v2 bytes and decoders are unchanged.
- v2 decodes as `DocumentImportSource.Current` with one visible layer, id 1, an empty name and every
  cell covered. This is lossless, so no legacy path is involved. A user-file load is clean.
- v1 keeps its `Legacy` conversion (ADR 0025), whose result is one fully covered layer.
- Recovery envelope 3 carries exactly a project-v3 payload. Readers accept envelope 1 with project 1,
  2 with 2 and 3 with 3. New Candidate and Retired writes use envelope 3; Retired stays 23 bytes. The
  common bounded maximum for project-file and recovery reads rises to the largest v3 Candidate
  (1,182,885 bytes; probe one byte more). Version-specific maxima remain enforced.

### Future boundary

Saved layer opacity is planned for the next format version (v4), possibly together with frames. It
adds a stored value to `Layer`, and its effect on display and export is decided by that ADR. If
that ADR keeps opacity out of PNG export, the export flow must disclose it whenever a visible layer
has non-full opacity, so a display-only dimming is never mistaken for exported transparency. Frames
follow the glossary: a frame holds an ordered layer state and the palette stays shared (ADR 0022).
No opacity field, frame type, empty array or unused API is added now.

### Delivery

Delivery happens in four focused Issues, in this order:

1. **P4-05a cutover.** Layered `DocumentState`, `Empty` coverage, the composite function, v3 and
   envelope 3, v2 reading, Eraser to `Empty`, `activeLayerId`, and every consumer, together. Before
   this change the document is single-layer; after it, still one layer. The stroke preview keeps
   drawing over the committed image, which equals the composite while the active layer is the only
   one.
2. **P4-05b layer commands.** The five commands, `SelectLayer`, reconciliation, hidden-layer
   rejections, `NoVisibleLayer`, and the stroke preview through the composite (below, new cell,
   above) once layers can exist above the active one.
3. **P4-05c layer panel.** The opening panel, built from a UI spec written first.
4. **P4-05d worst-case evidence.** 16 full layers: M5 drawing-latency budget on the named minimum
   profile (#135), retained memory, autosave and save write time for 1.18 MB, and the history bytes of a
   full remap. Its protocol is fixed before collection.

## Rejected alternatives

### A reserved transparent palette slot

Requiring a transparent slot contradicts ADR 0022, under which transparency is not mandatory and a
palette may be 256 opaque entries. It would also shift user palettes on import.

### Treat `defaultIndex` as transparent on every layer

That changes the appearance of existing v1 and v2 documents whose default slot is opaque, so it
breaks exact migration. It also makes one slot unpaintable.

### Two bytes per pixel

It doubles live and dense-history bytes (16 layers: 2 MiB live) for one extra state per pixel, where
the coverage mask costs one eighth of that.

### Keep Eraser on `defaultIndex`

This was the first proposal and was rejected by the repository owner: erasing must always be
transparent.

### Composite with Android canvas blending

Its rounding is platform-defined, so the display could differ from PNG export. It also bypasses the
single deterministic path (ARC-007).

### Visibility as workspace state

Visibility changes what is exported and what is saved, so it is document truth (ARC-004). The
separate workspace "dim all" display stays available for a non-destructive preview.

## Consequences

### Benefits

- One document owner gains layers without losing the palette-index guarantees. The one-layer
  appearance and export are byte-identical.
- Eraser semantics no longer depend on the palette.
- A full-palette remap across 16 layers fits history through dense retention.

### Costs and risks

- The cutover touches every consumer of `DocumentState.snapshot` at once, like #106.
- File size grows from 66 KB to 1.18 MB at the limit. Autosave write time and stroke latency with
  15 composited layers are unmeasured; P4-05d must accept them before M4 exits.
- Classes at the detekt limit (`WorkspaceReducer`, `ProjectFormatV2Decoder`, `EditorCallbacks`)
  need their new behavior in new files (a layer reduction file, a v3 decoder, an
  `EditorLayerCallbacks` group). Rules are not relaxed.

## Enforcement impact

- Domain tests: layer invariants, `LayerId` assignment and overflow, name bounds, coverage
  normalization, cross-layer palette membership.
- Pixel-engine tests: composite golden vectors (exact first copy, alpha 0 and 255, partial rounding,
  hidden RGB, no contribution), and single-layer equality with the v2 export.
- History tests: sparse/dense selection boundary, deleted-layer restore, accounting at every limit.
- Format tests: v3 golden, round-trip, every rejection, v2 to v3 exactness, envelope 3 and mismatched
  payloads, reader maxima.
- `ARCHITECTURE_CONSTITUTION.md` ARC-005, `COMMAND_MODEL.md` CMD-005 and examples, `GLOSSARY.md` and
  `DEVELOPMENT_PLAN.md` change in the same PR as this ADR.

## Migration and rollback

v2 files open losslessly and are rewritten as v3 only by a save. v1 conversion is unchanged. A
rollback after P4-05a must keep the v3 reader so that saved v3 files and recovery stay readable
(ADR 0025's rule); there is never a period where two editable document representations coexist.

## Related

- Issue: #140
- PR:
- Supersedes: the Eraser target and new-document fill of ADR 0005, ADR 0022 and CMD-005; the
  retained-change accounting of ADR 0005, ADR 0009 and ADR 0022; the recovery reader maximum of
  ADR 0025 (in part)
- Superseded by: none

# ADR 0033: PNG import as a new work or as a new layer

- Status: accepted
- Date: 2026-10-01
- Issue: #176
- Affected rules: `ARC-001`, `ARC-003`, `ARC-004`, `ARC-005`, `ARC-008`, `ARC-010`, `CMD-002`, `KOT-007`, `KOT-012`,
  `KOT-013`, `QLT-011`, `QLT-016`, `QLT-019`; amends `ARC-005`, ADR 0022, ADR 0025 and ADR 0030

## Context

hide asked on 2026-10-01 to import a PNG file into a layer.

[ADR 0025](0025-indexed-project-compatibility.md) shipped the indexed cutover with the sentence "No
new PNG importer is included". [ADR 0022](0022-indexed-palette-and-migration.md) expected that "PNG
import later reuses this conversion boundary" and required that "image-generated
quantization/dithering algorithms require their own focused decision and golden tests before PNG
import ships". The probes of 2026-10-01 found:

- No production code reads a PNG as artwork. The reference-image decoder of
  [ADR 0032](0032-reference-underlay.md) subsamples, resizes with a filter, converts the colour
  space and reads premultiplied pixels, so it cannot return exact colours.
- The legacy conversion (`LegacyRgbaSource`, `LegacyImportPlanner`) covers every cell, counts a
  fully transparent pixel as a colour, chooses the default slot by looking for transparent black,
  and carries the identity and revision of a project file. None of that fits a PNG.
- `AddLayerCommand` adds an all-`Empty` layer. `ChangeSet` can hold a palette transition and a
  layer-structure transition together, but no factory builds both, and an added layer is charged
  its name bytes only ([ADR 0030](0030-ordered-layers-and-empty-pixels.md)).
- `PaletteDefinition` has no append, and the nearest metric of ADR 0022 is internal to the pixel
  engine.

The owner decided on 2026-10-01:

1. One entry on the file surface, beside Export PNG. The PNG is chosen first; its size and number
   of colours are shown, and then one of three ways is chosen.
2. **Open as a new work**: the current work is closed and a new work of the PNG's size is created.
   Its palette holds exactly the colours the PNG uses, and it has one layer. Unsaved changes are
   confirmed as for every document switch.
3. **Add as a layer, adding colours**: a new layer above the active one. A colour the palette
   already has keeps its slot, a new colour is appended, and only what does not fit takes the
   nearest colour. The counts are shown before the choice.
4. **Add as a layer, converting colours**: a new layer above the active one. The palette is not
   changed; every pixel takes the nearest colour of the current palette.
5. For 3 and 4 the PNG is never scaled. Its top-left corner is placed on the top-left corner of
   the picture; what lies outside the picture is not imported, and that is disclosed.
6. A PNG with more than 256 colours cannot be opened as a new work in the first form. Palette
   generation (quantization) is not part of this decision.

## Decision

Add **PNG import**: one bounded exact PNG read in `:adapters:persistence`, one deterministic
planner in `:core:pixel-engine`, one document command for the two layer forms, and one document
switch for the new-work form. No RGBA document, second editable runtime, project-format change or
dependency is added.

### Reading the file

- `PngImportPort.pick()` in `core/application/persistence` returns a closed outcome: `Picked` with
  an `ImportRaster`, `Cancelled`, `Rejected` with a typed reason (too many bytes, too many pixels,
  unsupported content) or `Failed` with the existing storage failure. `PersistencePorts` gains the
  port.
- `ImportRaster` in `core/domain` carries a width, a height and an immutable straight sRGB RGBA8888
  raster. Each side is 1 to 1024 pixels. `ImportRaster.create` is the only factory, copies its
  input and returns a typed result; the raster is private and bulk reads return a copy. It is an
  uninstalled import value like `LegacyRgbaSource`: never a document, never editable. ARC-005 is
  amended in this change to name it among the types that privately own packed storage, and the
  PNG-import read and decode among the bounded adapter buffers.
- `:adapters:persistence` implements the port with the Storage Access Framework open-document
  picker for `image/png` and a PNG reader of its own. It reads at most 8,388,608 encoded bytes
  through the maximum-plus-one bounded reader, validates the signature, chunk lengths and CRCs,
  reads the header before inflating, rejects a side above 1024, and inflates into a buffer whose
  size is fixed by the header, so no input can make it allocate more.
- Accepted content: bit depths up to 8 of colour types 0 (grey), 2 (truecolour), 3 (palette), 4
  (grey with alpha) and 6 (truecolour with alpha), with `PLTE` and `tRNS`, the five row filters,
  and no interlace. Sixteen-bit samples and Adam7 interlace are rejected as unsupported content.
  Sample values are taken as sRGB as they are stored: `gAMA`, `cHRM`, `iCCP`, `sRGB` and every other
  ancillary chunk are skipped and no colour is converted. A sample of fewer than 8 bits is scaled
  to 8 bits by bit replication, as the PNG specification describes. An unknown critical chunk is
  unsupported content.
- The platform decoder is not used. It premultiplies, converts colour spaces and reduces 16-bit
  samples in ways that differ by OS version, and it can be verified only on a device. The reader
  is verified on the JVM against the repository's own PNG writer (ADR 0019) and an independent
  decoder.
- The pick is a physical operation of the persistence workflow exactly like the reference-image
  pick of ADR 0032: it uses the picker broker and holds the one operation lease from the request
  until the outcome, so a second operation answers Busy. It cannot start while a palette edit
  session exists. The adapter keeps no URI permission.

### Mapping colours

`RasterImportPlanner` in `core/pixel-engine/importing` is the only owner of the mapping. It is
pure and deterministic, and its plan values are constructed only there.

- A pixel whose alpha is 0 becomes an `Empty` cell whatever its RGB is, and is not counted as a
  colour (ADR 0030). Every other pixel is a colour by its complete RGBA value.
- **New work.** The canvas is the raster's size, which must fit the document limits (256 per
  side). The palette lists the colours in order of first row-major occurrence. One colour gets a
  second duplicate slot, because a palette definition holds at least two entries (ADR 0022). The
  default slot is slot 0. No transparent or blank slot is added. More than 256 colours is a typed
  rejection that carries the count.
- **Layer forms.** Only the part of the raster that lies on the canvas, top-left aligned, takes
  part. A raster cell outside the canvas is dropped; the plan counts the dropped cells that are not
  `Empty`. Canvas cells the raster does not reach are `Empty`.
  - *Adding colours.* A colour equal to a palette entry takes the lowest such slot. Every other
    colour is appended in order of first row-major occurrence while the palette has fewer than 256
    entries. A colour that no longer fits takes the nearest entry of the resulting palette by the
    nearest metric v1 of ADR 0022. Existing slots, their order and the default slot are unchanged.
    The plan counts the appended colours and the colours that took a nearest entry.
  - *Converting colours.* Every colour takes the entry of the current palette that the nearest
    metric v1 selects (an exact match first). The palette is unchanged. The plan counts the colours
    without an exact match.
- A raster from which no covered cell would be imported is a typed rejection in every form.
- This is the focused decision ADR 0022 required before PNG import: no palette is generated from
  an image and nothing is dithered. Exact matching, appending and the existing nearest metric are
  the whole algorithm, and golden tests fix its output. Quantization and dithering remain
  undecided and are not reserved by any field or API.

### Adding a layer

- `ImportLayerCommand` is a new `DocumentCommand`. It carries the usual source admission, the layer
  the new one goes above, and a layer plan. The plan is bound to the palette definition and canvas
  size it was computed for; the handler rejects it when the document's definition or size differs,
  when the named layer is missing, and at 16 layers. It never recomputes colours.
- One command produces one `ChangeSet` with the palette transition (unchanged for the converting
  form) and one added layer that already holds its pixels, and therefore one history entry: one
  undo removes the layer and restores the palette. The new layer has the next layer id, an empty
  name and is visible; it becomes the active layer by the existing rule for inserted layers.
- Accounting (amends ADR 0030): an added layer that holds at least one covered cell is charged
  `pixelCount + ceil(pixelCount / 8)` bytes plus its name bytes, like a deleted layer. An added
  all-`Empty` layer keeps the name-only charge. The palette transition keeps its formula. The
  largest import charges 73,728 pixel bytes and 2,056 palette bytes.
- Existing cells of other layers are not remapped: appended slots do not move any index.

### Opening a new work

- The new-work form is a document switch like New and Load. It goes through the same confirmation
  policy (unsaved changes, an unadopted recovery candidate), the same commit and the same
  installation: a new `DocumentId`, revision zero, an empty history, one visible layer with an
  empty name, and a fresh workspace, so the underlay and the palette session rules of the
  installation apply unchanged.
- The installed work is unsaved (dirty): a PNG is not a project checkpoint, so autosave and the
  discard confirmation protect it from the first moment.
- `DocumentImportSource` stays the closed `Current` / `Legacy` value of project and recovery
  ports. A PNG never passes through it and `LegacyRgbaSource` is not reused: its identity,
  full coverage and default-slot rule belong to project v1. PNG import keeps the principle of that
  boundary: an immutable uninstalled candidate, a typed outcome and no editable RGBA runtime. This
  replaces the sentence of ADR 0022 that PNG import reuses the conversion boundary.

### Pending choice

- After a pick the application plans all three forms once, off the main thread, against the
  document that is installed, and stores one `PendingRasterImport` in `WorkspaceState`: the raster's
  size, its number of colours, and for each form either its plan or the typed reason it is not
  available. The raster itself is not retained. The field is absent by default, is set and cleared
  only through `WorkspaceAction`, is undo-neutral and dirty-neutral, and disappears with the
  workspace when another document is installed.
- A result that arrives after another document was installed is dropped, by the same lease rule as
  the reference-image pick.
- Choosing a layer form executes `ImportLayerCommand` through the normal command path and clears
  the pending value. Choosing the new-work form starts the switch, which then owns the plan, and
  clears the pending value. Cancelling clears it. A plan that has become stale is rejected by the
  command or the switch and changes nothing.

### Controls

- The file surface gains Import PNG beside Export PNG.
- While a pending import exists, one modal dialog shows the size and the number of colours and
  offers the three forms and Cancel. Each form states what it will do: the colours it appends, the
  colours that take a nearest colour, the pixels outside the picture that are not imported, or why
  it is unavailable (more than 256 colours or larger than 256 per side for a new work; 16 layers
  for the layer forms). Pressing a form acts at once: the layer forms can be undone, and the
  new-work form asks before discarding unsaved changes. The first form shows no picture preview.
- UI wording: `Import PNG` / `PNG を取り込む` / `导入 PNG`.

### Not decided here

- Palette generation from an image, dithering, formats other than PNG, importing into an existing
  layer, moving or scaling imported pixels, a picture preview in the dialog, naming the layer after
  the file, and reading layers or palettes from other tools' files are separate Issues. No field or
  API is reserved for them.

## Rejected alternatives

### Decode with the platform `BitmapFactory`

Rejected: exact colours are the contract. The platform premultiplies unless told otherwise,
converts embedded colour profiles and reduces deep samples, differs between OS versions, and can be
tested only on a device, which CI does not have. The repository already owns its PNG writer and a
bounded JPEG metadata parser for the same reasons.

### Reuse `LegacyRgbaSource` and the legacy conversion dialog

Rejected: the legacy path keeps a project's identity and revision, covers every cell, counts
transparent pixels as colours, appends a blank default slot and offers preset palettes with
original-copy preservation. Bending it to PNG would change project v1 migration, which ADR 0025
fixes byte for byte.

### Two commands, one for the palette and one for the layer

Rejected: two history entries would let an undo leave appended colours without their layer, and
the pair could be interrupted between its halves.

### Let the command compute the mapping when it runs

Rejected: the counts shown before the choice would be computed twice by two callers, and the
runtime lock would be held for the mapping. A plan bound to its source follows
`ReplacePaletteCommand` and its `PaletteRemap`.

### Hold the operation lease until the form is chosen

Rejected: it needs a new persistence phase in every exhaustive projection and couples a command to
a persistence operation. The dialog is modal, the pending value is small, and each form already has
a canonical path of its own.

### Scale or centre a PNG that does not match the canvas

Rejected by the owner: pixels keep their coordinates. Moving imported pixels is a tool of its own.

### Reduce a PNG of more than 256 colours to a preset or generated palette

Rejected for this decision by the owner: such pictures are photographs, which the underlay serves.
ADR 0022 keeps quantization a separate decision.

## Consequences

### Benefits

- Pixel art made elsewhere arrives with its exact colours, as a new work or beside existing layers.
- Every form is one undoable step or one confirmed switch through paths that already exist.
- The reader and the mapping are verified on the JVM.

### Costs and risks

- The adapter owns a PNG reader: about four small files and their tests, and the duty to keep it
  bounded against hostile input.
- PNG files outside the accepted content (16-bit, interlaced) are rejected rather than converted.
- A PNG with an embedded colour profile is read without conversion, so its colours can differ from
  a colour-managed viewer's rendering. This is accepted for pixel art, where the stored values are
  the artwork.
- One pick transiently holds the encoded bytes (up to 8 MiB), the inflated scanlines and one raster
  (up to 1024 x 1024 pixels, 4 MiB) until the plans exist. The pending value retains at most three
  canvas-sized snapshots and one palette.
- Planning costs at most one nearest search per distinct colour (65,536 colours by 256 entries in
  the worst case) and runs once per pick, off the main thread.
- `WorkspaceState` gains a tenth field, `PersistencePorts` a seventh port that every construction
  site must supply, and `DocumentCommand` a sixth kind.
- A work opened from a PNG is unsaved until the user saves it.

## Enforcement impact

- `core/domain`: `ImportRaster` and its rejection; contract tests for the bounds, the copy and the
  read.
- `core/pixel-engine`: `RasterImportPlanner`, its plans and rejections; golden tests for the slot
  order, the duplicate slot, `Empty` for transparent pixels, exact matching on duplicate entries,
  appending up to 256, overflow to the nearest entry of the resulting palette, ties, clipping
  counts, determinism and every rejection.
- `core/application`: `ImportLayerCommand` and its handler, the `ChangeSet` factory, the amended
  retained-byte charge, the pending value, its actions and reducer branches, the port, the pick
  flow and the switch kind; tests for undo and redo of both layer forms, stale plans, the layer
  limit, history accounting, dropping a stale pick, clearing on installation, and the confirmation
  and dirtiness of the new work.
- `:adapters:persistence`: the picker contract, the bounded reader and the PNG reader; JVM tests for
  every accepted colour type and bit depth, each filter, `tRNS`, every limit, truncated and corrupt
  input, and a round trip of the repository's own PNG export.
- `:presentation:compose`: the file-surface control, the dialog and three locales; instrumented
  tests of the dialog's facts, each form and Cancel.
- `docs/ARCHITECTURE_CONSTITUTION.md` (ARC-005), `docs/GLOSSARY.md`, `docs/COMMAND_MODEL.md`,
  `docs/PROJECT_LAYOUT.md`, `docs/DEVELOPMENT_PLAN.md`, `docs/MILESTONES.md`, `docs/ROADMAP.md`,
  and the amended sentences of ADR 0022, ADR 0025 and ADR 0030 in this change;
  `docs/INTERFACE_INVENTORY.md` rows with the Issues that add the controls.
- Performance (QLT-019): P4-07a and P4-07b state the per-operation cost above in their completion
  reports. No drawing path changes, no workload is registered and no feature Issue collects on the
  device.

## Migration and rollback

No data migrates and no file format changes. Rollback removes the port, the reader, the planner,
the command, the pending value and the controls. A work that contains an imported layer stays a
valid project v3.

## Related

- Issue: [#176](https://github.com/hideyukiMORI/NENE-PIXEL/issues/176)
- Implementation: [#177](https://github.com/hideyukiMORI/NENE-PIXEL/issues/177) (P4-07a),
  [#178](https://github.com/hideyukiMORI/NENE-PIXEL/issues/178) (P4-07b)
- PR:
- [ADR 0019](0019-exact-png-export.md)
- [ADR 0022](0022-indexed-palette-and-migration.md)
- [ADR 0025](0025-indexed-project-compatibility.md)
- [ADR 0030](0030-ordered-layers-and-empty-pixels.md)
- [ADR 0032](0032-reference-underlay.md)
- Supersedes: none
- Superseded by: none

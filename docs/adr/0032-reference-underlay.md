# ADR 0032: One reference underlay beneath the layers for tracing

- Status: accepted
- Date: 2026-09-30
- Issue: #169
- Affected rules: `ARC-001`, `ARC-003`, `ARC-004`, `ARC-005`, `ARC-008`, `ARC-010`, `CMD-002`, `KOT-007`, `KOT-012`,
  `KOT-013`, `QLT-011`, `QLT-016`, `QLT-019`

## Context

hide asked on 2026-09-30 for a picture to trace: an image chosen from the device that lies beneath
the artwork, can be moved, scaled and made more or less transparent, and is not part of the
artwork. Rotation was named as welcome but low priority.

[ADR 0022](0022-indexed-palette-and-migration.md) reserved the boundary in one sentence:
"Reference-image pixels remain a separate source with display opacity." Nothing else exists. The
probe of 2026-09-30 found no production code that decodes an image (`BitmapFactory` appears only in
instrumented tests), no continuous document coordinate in `ViewportTransform` (it maps whole cells),
and no rule that bounds a display bitmap. The canvas draws, in order, the surround, the transparency
backdrop, one bitmap and the grid ([ADR 0026](0026-actual-size-window.md)). One pointer draws and
two pointers transform the viewport ([ADR 0004](0004-bounded-workspace-viewport.md)); the armed
eyedropper already changes what one pointer means through `CanvasPointerIntent`
([ADR 0029](0029-quick-select-and-exact-eyedropper.md)). `AppPreferences` holds the app language
only ([ADR 0021](0021-app-language-resources.md)).

The owner decided on 2026-09-30:

1. The app remembers the underlay of a work and restores it when the work is opened again. It is
   not stored in the project file. The session-only form is built first; remembering follows.
2. The underlay lies beneath every layer and above the transparency backdrop.
3. Moving and scaling happen in an adjust mode: one finger moves the underlay, two fingers scale
   it. Opacity is a slider. One control fits the underlay to the picture.
4. The controls live in one row at the bottom of the layer panel.
5. Rotation is not part of this decision.

## Decision

Add one **reference underlay** as ephemeral workspace state and one display-only drawing beneath
the document bitmap. No document meaning, command, history entry or persisted project payload
changes.

### State

- `ReferenceImage` in `core/application/workspace/underlay` carries a width, a height and an
  immutable straight sRGB RGBA8888 raster. Each side is 1 to 1024 pixels. `ReferenceImage.create`
  is the only factory, copies its input and returns a typed result; the raster is private and is
  read through `copyPackedRgba8888()`, like `CompositeRaster`. Equality is identity: two images are
  the same only when they are the same instance.
- `UnderlayPlacement` names where the image lies in document-pixel coordinates: the position of
  its top-left corner and a scale in document pixels per image pixel, all doubles. It is the same
  at every zoom, so the underlay moves and scales with the picture. `UnderlayPlacement.create`
  clamps against the image and canvas sizes and normalizes non-finite input, and never rejects:
  the longer displayed side stays between one eighth of the longer canvas side (or the fitted
  size, when that is smaller) and sixteen times the longer canvas side, and the image rectangle
  always overlaps the document rectangle by at least one document pixel on each axis, so the
  underlay cannot be lost. `UnderlayPlacement.fitted` is the largest placement that lies wholly
  inside the document rectangle, centred; it is always a valid placement. `ReferenceUnderlay`
  keeps the canvas size it was placed against, so its derivations clamp without further input.
- `UnderlayOpacity` is an integer alpha from 26 to 255 (10% to 100%); `create` clamps. Hiding is
  the visibility, not an opacity of zero.
- `ReferenceUnderlay` carries the image, the placement, the opacity, a visibility and an
  interaction that is resting or adjusting. Its constructor is private: `ReferenceUnderlay.placed`
  builds the initial value (fitted, alpha 128, shown, resting) and named derivations produce every
  other value, so no public API takes a Boolean (KOT-007, KOT-013). A hidden underlay is never
  adjusting.
- `WorkspaceState.underlay` is the single owner and is absent by default.
  `WorkspaceAction.SetReferenceUnderlay(underlay)` and `WorkspaceAction.ClearReferenceUnderlay`
  are the only mutations; the reducer answers `Reduced` or `Unchanged` and never rejects. A
  transition into adjusting cancels an in-progress preview gesture in the same reduction, because
  it changes what a pointer means. Every other underlay change keeps the preview: the row is
  non-modal and the opacity can change while drawing.
- Until P4-06c the underlay belongs to the open document of the running process. Installing
  another document (new, load, recovery adoption) clears it, unlike `EditorAppearance` and the
  actual-size window, because a placement is only meaningful against the picture it was aligned
  to. A fresh process starts without one.
- The underlay is undo-neutral and dirty-neutral. Save, autosave, recovery, PNG export, the
  Composite, the actual-size window, the legacy import comparison and the eyedropper do not read
  it.

### Adjust mode

- `CanvasPointerIntent` gains `AdjustUnderlay`, derived when the underlay is shown and adjusting.
  It takes precedence over an armed eyedropper.
- Pointer arbitration is unchanged. In this intent a one-pointer drag translates the placement and
  a two-pointer gesture translates it and scales it uniformly about the gesture centroid. The
  viewport does not change while adjusting, and no stroke starts.
- The arithmetic is one pure function family in `core/application` that takes the placement, the
  surface points and the current `ViewportTransform`, and returns a clamped placement. Presentation
  owns no competing matrix (ARC-001). Each pointer move reduces one `SetReferenceUnderlay`.
- Leaving the mode is a named derivation (`rested`), reached by the Done control and by Back.
  Hiding or clearing the underlay also leaves it.

### Choosing the image

- `ReferenceImagePort.pick()` in `core/application/persistence` returns a closed outcome: `Picked`
  with a `ReferenceImage`, `Cancelled`, `Rejected` with a typed reason (too many bytes, too many
  pixels, unsupported content) or `Failed` with the existing storage failure. `PersistencePorts`
  gains the port. The pick uses the existing picker broker and the one-active-physical-operation
  rule of the persistence workflow, like the palette JSON import.
- `:adapters:persistence` implements it with the Storage Access Framework open-document picker for
  `image/png`, `image/jpeg` and `image/webp`. It reads at most 16,777,216 encoded bytes through the
  maximum-plus-one bounded reader, probes the dimensions before allocating, rejects a side above
  16,384 pixels, decodes with the platform `BitmapFactory` using power-of-two subsampling followed
  by one filtered resize so the result fits 1024 x 1024 without upscaling, applies the EXIF
  orientation through the platform `ExifInterface`, and converts to straight RGBA8888. No
  dependency is added. The adapter keeps no URI permission: the pixels are copied into the state.
- The application applies a `Picked` image only when the document that was open at the request is
  still installed; otherwise the result is dropped. The new underlay replaces any existing one.
- This is not the PNG importer that [ADR 0025](0025-indexed-project-compatibility.md) excluded: a
  reference image never becomes artwork, palette entries or a document source.

### Display

- The canvas draws in this order: the surround, the transparency backdrop, **the underlay**, one
  document bitmap with its alpha, then the grid. The underlay is clipped to the document
  rectangle, filtered (bilinear) and blended with its opacity by the platform canvas.
- `:presentation:compose` owns one disposable `UnderlayBitmapCache` keyed by the `ReferenceImage`
  instance. It builds one `Bitmap` when the image changes and on nothing else; placement, opacity
  and visibility are drawing parameters.
- The controls are one fixed row at the bottom of the layer panel, above Add layer: a visibility
  toggle, the label, a more menu (choose or replace the image, adjust, fit to picture, remove) and
  an opacity slider. With no underlay the row offers only the choice of an image. While adjusting,
  one bar over the work area shows the mode, the opacity slider, Fit to picture and Done.
- UI wording: `Underlay` / `下敷き` / `底图`. The glossary term is "reference underlay".

### Not decided here

- Remembering the underlay per work across processes (P4-06c) amends ARC-004 and ADR 0021 in its
  own change.
- Rotation, numeric placement input, more than one underlay, drawing the underlay above the
  layers, and sampling colours from it are separate Issues. No field or API is reserved for them.

## Rejected alternatives

### Make the underlay a document layer

Rejected: a layer is indexed artwork that is saved, exported and undone. A photograph has no
palette, would enter the Composite and PNG export, and would make a second meaning of "layer"
(ARC-001). ADR 0022 already keeps reference pixels a separate source.

### Store the underlay in the project file now

Rejected for this decision: it needs a new project format version, a new file-size envelope and
recovery handling for a payload hundreds of times the size of the artwork. The owner chose to keep
it out of the file.

### Keep only the content URI and decode again when needed

Rejected: a rotation of the device would need an asynchronous decode before the first frame, the
URI permission would have to be persisted and can be revoked, and the picture would disappear when
the source file moves. Copying up to 1024 x 1024 pixels into the state costs at most 4 MiB.

### Place the underlay in surface pixels or relative to the viewport

Rejected: the underlay would slide against the picture at every pan and zoom, which defeats
tracing. Document-pixel coordinates need no correction when the viewport changes.

### Let two fingers scale the underlay and the viewport in one mode

Rejected: one gesture with two meanings cannot be arbitrated (ADR 0004). The mode gives the two
pointers to the underlay and returns them on Done.

### Add an image loading library

Rejected: the platform decoder covers the three formats, and a library would add startup and size
cost to every session for a feature most sessions do not use (DEVELOPMENT_WORKFLOW dependency
rule).

### Reserve a rotation field

Rejected: ADR 0022 adds no unused API. Rotation changes the placement type, the clamp and the
adjust arithmetic together and is decided with its own Issue.

## Consequences

### Benefits

- A picture can be traced without touching the artwork, its palette, its file or its history.
- The placement follows the picture at every zoom with no extra state.
- One decode and one bitmap build per chosen image; frames only draw.

### Costs and risks

- While an underlay is shown, every canvas frame issues one clip and one filtered `drawBitmap`
  more than before. With no underlay, or a hidden one, the frame is unchanged.
- An underlay retains up to 4 MiB of raster in the workspace state and up to 4 MiB of bitmap in the
  presentation cache. Both are released when it is cleared.
- The underlay is lost when the process dies or another document is installed, until P4-06c.
- A partially transparent underlay lets the checkerboard show through; this is accepted and
  judged on the device.
- While adjusting, the viewport cannot be changed; the mode must be left first.
- `WorkspaceState` gains a ninth field, and `PersistencePorts` a port that every construction site
  must supply.

## Enforcement impact

- `core/application`: the four value types, the state field, the two actions, the reducer
  branches and no-change reasons, the intent, the adjust arithmetic, the clearing on document
  installation, the port and its outcome; contract tests for every clamp, non-finite input,
  idempotence, preview cancellation on entering the mode, preview preservation otherwise, clearing
  on installation and dropping a stale pick.
- `:adapters:persistence`: the picker contract and the decoder; tests for each byte and pixel
  limit, each supported format, EXIF orientation, a truncated file and unsupported content.
- `:presentation:compose`: the cache, the draw step, the panel row, the adjust bar and three
  locales; instrumented tests that the underlay shows only through transparent cells, that a
  hidden or cleared underlay draws nothing, that adjusting changes neither the document nor the
  viewport, and that the actual-size window never shows it.
- `docs/GLOSSARY.md`, `docs/COMMAND_MODEL.md`, `docs/PROJECT_LAYOUT.md`,
  `docs/DEVELOPMENT_PLAN.md`, `docs/MILESTONES.md` and `docs/ROADMAP.md` in this change;
  `docs/INTERFACE_INVENTORY.md` rows with the Issues that add the controls.
- Performance (QLT-019): P4-06a and P4-06b state the per-frame cost above in their completion
  reports and register one workload with the layer phase gate (#145): the underlay shown at alpha
  128 during `canvas256_repeated_diagonal`. No feature Issue collects on the device.

## Migration and rollback

No data migrates. Rollback removes the state field, the actions, the port, the adapter, the draw
step and the controls; no document or file changes.

## Related

- Issue: [#169](https://github.com/hideyukiMORI/NENE-PIXEL/issues/169)
- Implementation: [#170](https://github.com/hideyukiMORI/NENE-PIXEL/issues/170) (P4-06a),
  [#171](https://github.com/hideyukiMORI/NENE-PIXEL/issues/171) (P4-06b),
  [#172](https://github.com/hideyukiMORI/NENE-PIXEL/issues/172) (P4-06c)
- PR:
- [ADR 0004](0004-bounded-workspace-viewport.md)
- [ADR 0022](0022-indexed-palette-and-migration.md)
- [ADR 0026](0026-actual-size-window.md)
- [ADR 0029](0029-quick-select-and-exact-eyedropper.md)
- [ADR 0030](0030-ordered-layers-and-empty-pixels.md)
- [ADR 0031](0031-phase-gate-device-performance.md)
- Supersedes: none
- Superseded by: none

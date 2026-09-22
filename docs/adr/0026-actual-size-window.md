# ADR 0026: One actual-size window over the editor canvas

- Status: accepted
- Date: 2026-09-22
- Issue: #118
- Affected rules: `ARC-001`, `ARC-004`, `ARC-007`, `ARC-011`, `ARC-012`, `CMD-002`, `KOT-007`, `KOT-012`, `KOT-013`, `QLT-006`, `QLT-010`,
  `QLT-014`, `QLT-016`

## Context

hide asked on 2026-09-22 for a way to see the artwork at actual size while editing zoomed in: a small
floating window over the canvas showing the committed document at x1, x2, x4 or x8, movable by drag.
The editor today renders one committed bitmap per `(snapshot, definition)` pair inside `PixelCanvas`,
draws the in-progress gesture on top, and has no draggable overlay. `ViewportZoom` is a continuous
fit-relative factor (`scale = fit x zoom`, minimum 1.0), so it cannot express "one document pixel per
device pixel" on every surface. `WorkspaceState.preview` already names the in-progress gesture.
`ChangeSet.renderInvalidation` exists but no presentation code subscribes to it; the UI reacts to a
whole `EditorRenderState` replacement. The maximum document is 256 x 256 (ADR 0005), so x8 is 2048
device pixels on a side and exceeds the tablet's 1200 px short edge. The P4 frame lane measures the
UP-to-committed-result budget (33.33 ms p95) and the per-frame overrun population; a second bitmap
rebuild per commit would land inside that budget.

## Decision

Add one **actual-size window** as ephemeral workspace state and one derived drawing of the existing
committed bitmap. No document meaning, command, or persisted payload changes.

- `ActualSizeScale` is a closed enum `X1, X2, X4, X8` with `devicePixelsPerCell` 1, 2, 4, 8 in
  `core/application/workspace`. It is not a `ViewportZoom`; the two types keep their one meaning each.
- `ActualSizeWindow` carries visibility, scale and anchor and is a field of `WorkspaceState`. Its
  constructor is private: `ActualSizeWindow.initial` is the only entry point and `toggled`,
  `withScale` and `withAnchor` derive every other value, so no public API takes a Boolean parameter
  (KOT-007, KOT-013). `WindowAnchor` holds two doubles in `[0.0, 1.0]` naming the top-left corner of
  the window inside the free space of the editor work area, that is the area minus the window itself.
  Its axes are physical and independent of the text reading direction, like `EditorControlEdge`:
  `0.0` sits against the left or top edge and `1.0` against the right or bottom edge, so every
  created value keeps the window fully inside the area. `WindowAnchor.create` clamps instead of
  rejecting and normalizes `NaN` and non-finite input to the nearest bound.
  `WorkspaceAction.SetActualSizeWindow(window)` is the only mutation; the reducer answers `Reduced`,
  or `Unchanged(ActualSizeWindowAlreadySet)`, and never rejects. Setting the window keeps an
  in-progress `preview` gesture; the window is non-modal.
- The window survives document replacement like `EditorAppearance`: `RuntimeOwnerEffect.ReplaceOwners`
  re-applies it to the new owners. `ActualSizeWindow.initial` is hidden, `X2`, anchored at the
  physical top-right corner (`WindowAnchor.topRight`).
- The committed bitmap cache moves out of `PixelCanvas` into one presentation-owned
  `CommittedBitmapCache` keyed by `(snapshot, definition, backgroundArgb)`, the background being a
  render argument so a later transparency-display change cannot silently reuse a stale bitmap
  (ADR 0020). One instance is remembered by the work area and passed to both the canvas and the
  window, which also share its nearest-neighbour `Paint`; the window only changes the source and
  destination rectangles. The window reads `snapshot`, `definition` and the window state through one
  `derivedStateOf`, so a stroke in progress never redraws it; a commit redraws it once with the canvas
  in the same frame. The cache stays disposable derived state (QLT-016) and is never a second owner of
  document data.
- The window edge, including its 2dp frame, is at most half of the work area's shorter side. When the
  scaled document is larger, the window keeps whole cells only, shows the document's centre and clips
  the rest; every scale stays selectable and no panning state is added. The drawn content is an exact
  integer multiple of the committed bitmap: no interpolation and no partial cell at any scale. The
  frame is a fixed accent colour (ADR 0020 primary aubergine) in both themes, chosen for visibility
  against the canvas surround and the artwork alike.
- Interaction: the tool dock gets one toggle (visible / hidden) as the only always-visible control
  (Interface principle); it is selected while the window is open. On the window, drag moves it and a
  tap cycles the scale in enum order (`ActualSizeScale.next`). While a drag is in progress the offset
  lives in Compose as local mechanics, keyed by the window and its geometry so it cannot outlive
  either, and it is corrected to what the window can still travel so an overshoot never builds a dead
  zone. A movement below the touch slop is the tap; past it the drag starts from the distance already
  travelled beyond the slop. The release emits one `SetActualSizeWindow` with the clamped anchor. The
  window consumes its own pointer stream, so events never reach the canvas and drawing under the
  window is not possible; move the window instead.
- Wording: the glossary gains "actual-size window"; the word "preview" keeps its existing meaning.

Amendment 2026-09-22 (#126): the window carries its own affordances. `ActualSizeScale` becomes
`X1, X2, X4, X8, X16, X32` and `ActualSizeWindow.initial` opens at **X4**; the integer-multiple,
centre-clip and half-edge rules are unchanged. The window gains two always-visible chrome bands
outside the content: a grip handle along the top edge and a scale chip (`x4`) at the trailing bottom
corner, the chip a `Role.Button` that cycles the scale. Dragging the handle, the content or the
footer moves the window, and tapping the chip or the window body cycles the scale, so both existing
gestures keep working. The window keeps a minimum outer width so the two bands fit; a narrower
document is centred inside it, and the half-edge limit now applies to the outer size with the
content an exact `columns x scale` multiple inside it. The UI label becomes "Preview"
(`Preview` / `プレビュー` / `预览`) in all three locales; no type, key or command name changes.
Reason: hide used the #118 window on the device on 2026-09-22 and could not find either gesture. A
16 x 16 document at X2 is 38 device pixels with no scale readout and no handle, and the label
"actual size" did not describe what the window is for.

## Rejected alternatives

### Reuse `ViewportZoom` for the window scale

Rejected: zoom is fit-relative and surface-dependent; x1 for a 16 x 16 document would need a zoom
below the minimum, and one type would carry two meanings (ARC-001).

### Give the window its own bitmap cache

Rejected: every commit would rebuild the 65,536-pixel bitmap twice inside the committed-result budget.
Sharing the one derived bitmap costs one extra `drawBitmap` per commit.

### Publish window updates through a second flow after commit

Rejected: a second publisher of render state duplicates ownership (ARC-004). Narrowed subscription of
the existing state already isolates the window from stroke updates.

### Subscribe the window to `ChangeSet.renderInvalidation`

Rejected for this Issue: presentation has no region consumer today and `EditorRenderState` carries no
region. Wiring regional invalidation into the UI is a separate change with its own Issue.

### Disable scales that exceed the window limit

Rejected: for a 256 x 256 document both x4 and x8 would be unavailable, defeating the feature on the
largest documents. Centre clipping keeps every scale selectable without new state.

### A public data class with a `visible: Boolean` constructor parameter

Rejected: a public API constructor with a Boolean parameter is prohibited (KOT-013) and a generated
`copy` would bypass the single canonical factory (KOT-007). Named derivations carry the same
information with no second construction route.

## Consequences

### Benefits

- The artwork can be checked at exact device-pixel multiples while editing at any zoom.
- Putting the affordances on the window is compatible with the Interface principle's "keep complexity
  deeper": the front surface still carries only the dock toggle, and the handle and chip appear only
  once the window itself is open (amendment 2026-09-22, #126).
- One committed bitmap serves both views; stroke frames are untouched.
- The window state follows the same reducer, test and replacement pattern as `EditorAppearance`.

### Costs and risks

- One more `drawBitmap` per commit frame **when the window is visible**; the hidden default composes
  and draws nothing extra.
- At least one cell is always shown, so an extremely small work area can exceed the half-edge limit;
  this does not occur on supported tablets.
- The area under the window cannot receive strokes while it is visible.
- `PixelCanvas` loses a private cache; the shared cache must be keyed by every rendering input
  (ADR 0020) and stays presentation-owned.
- `EditorCallbacks` reaches the bounded function count, so its two viewport transform callbacks move
  into an internal `EditorViewportCallbacks` reached through one property. The public module API and
  the constructor signature are unchanged. Splitting the remaining callbacks by concern is a separate
  change with its own Issue, not a condition of this one.
- `CommittedBitmapCache` keys the snapshot and definition by identity and the background by value: a
  structurally equal but freshly allocated snapshot rebuilds the bitmap. That is correct but not
  minimal, and the window shares the same instance, so it inherits the same behaviour.
- `WorkspaceState.hashCode` allocates a `List` per call. It is not on a drawing path, and the shape
  matches `EditorRenderState`; a leaner form would be a separate, measured change.

## Enforcement impact

- `core/application`: `ActualSizeScale`, `WindowAnchor`, `ActualSizeWindow`, the `WorkspaceState` field
  and `withActualSizeWindow`, `WorkspaceAction.SetActualSizeWindow`, the reducer branch and
  `WorkspaceNoChangeReason.ActualSizeWindowAlreadySet`, `ReplaceOwners` carry-over; contract tests for
  every scale, anchor clamping, idempotence, gesture preservation and replacement carry-over.
- `presentation/compose`: `CommittedBitmapCache`, `ActualSizeWindowOverlay` inside the work-area
  `Box`, the dock toggle with icon and three locales, `EditorRenderState` and adapter fields,
  callbacks and `EditorViewportCallbacks`; a host controller contract for publication, scale cycling
  and anchor clamping; instrumented tests that every window cell equals the committed bitmap pixel
  scaled by the exact integer factor, that the toggle shows and hides the window, that the window
  stays inside the work area in both layouts and control edges, and that a scale above the limit
  clips the centre.
- `docs/GLOSSARY.md` entry; `docs/INTERFACE_INVENTORY.md` row for the toggle with its first-sight and
  every-session justification.
- Performance: independent review of the draw phase confirms the default (window hidden) path issues
  the same draw calls as before; no before/after measurement of existing workloads is triggered
  (QLT-011). The cost of a visible window is measured as an additional diagnostic family (window
  visible at X2 during `canvas256_repeated_diagonal`) in the Issue #120 v7 frame collection before any
  M5 budget acceptance; this ADR claims no speed-up.

## Migration and rollback

No data migrates. Rollback removes the window state, the toggle and the shared cache, restoring the
private cache in `PixelCanvas`; no document or file changes.

## Related

- Issue: [#118](https://github.com/hideyukiMORI/NENE-PIXEL/issues/118)
- Amendment: [#126](https://github.com/hideyukiMORI/NENE-PIXEL/issues/126) (2026-09-22)
- PR:
- [ADR 0005](0005-pixel-color-representation-and-limits.md)
- [ADR 0020](0020-tablet-editor-appearance.md)
- [Project Charter](../PROJECT_CHARTER.md) Interface principle
- Supersedes: none
- Superseded by: none

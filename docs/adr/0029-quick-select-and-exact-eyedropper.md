# ADR 0029: Quick-select control and exact eyedropper

- Status: accepted
- Date: 2026-09-28
- Issue: #108
- Affected rules: `ARC-001`, `ARC-004`, `ARC-005`, `ARC-007`, `ARC-008`, `ARC-011`, `ARC-012`, `CMD-002`,
  `CMD-005`, `CMD-006`, `CMD-009`, `CMD-010`, `CMD-012`, `KOT-007`, `KOT-012`, `KOT-013`, `QLT-006`,
  `QLT-011`, `QLT-014`, `QLT-016`

## Context

P4-04 asks for an eyedropper that reads the exact palette slot of the edited pixel and for a
one-finger press-and-hold selection that can later grow tool and zoom choices. The editor today
starts a `ToolGesture` on the first canvas pointer down (`BeginGesturePreview`), so a long press on
the canvas itself would compete with the start of every stroke. `WorkspaceState.activePaletteIndex`
changes only through `SelectPaletteEntry`. `PixelSnapshot.indexAt` already reads one slot without
exposing mutable storage and rejects positions outside the canvas, and the reducer receives the
admitted `DocumentState` through `CommandSourceAdmission`. The shell applies safe drawing insets and
places frequent controls along the bottom (Tabletop) or a selectable physical side (Handheld), with
`EditorControlEdge` naming the physical side (ADR 0020). The actual-size window (ADR 0026) is the
precedent for a non-modal overlay that owns its pointer stream.

`WorkspaceState` (8 `with` functions and 3 overrides) and `WorkspaceReducer` (11 member functions)
are at the detekt `TooManyFunctions` limit of 11, `EditorCallbacks` has 10, and the canvas pointer
session `ViewportPointerSession` has 11.

The repository owner chose on 2026-09-28: a floating control rather than a canvas long press, the
eight most recently used slots as menu content, and the eyedropper as a one-shot menu item rather
than a tool-dock tool.

## Decision

### Workspace state

- A new value `QuickSelection` in `core/application/workspace/quickselect` is one field of
  `WorkspaceState`. It holds `recent` (distinct `PaletteIndex` values, most recent first, at most
  `QuickSelection.RECENT_LIMIT = 8`), `eyedropper` (`EyedropperState.Idle` or `Armed`) and
  `menu` (`QuickSelectMenu?`). Its constructor is private; `QuickSelection.initial` is empty, idle and
  closed, and every other value is derived by named functions, so no public API takes a Boolean
  (KOT-007, KOT-013).
- `QuickSelectMenu` holds the `items` fixed when the menu opened and the `highlighted` item or
  `null`. `QuickSelectItem` is a sealed interface with `PaletteSlot(index)` and `Eyedropper`. It is the
  extension point: a later tool or zoom choice adds a case and its reduction, and the pointer
  recognizer never learns a palette-specific case. No case is added before a feature uses it.
- Opening builds `items` as `recent` in order followed by `Eyedropper`, so at most nine items.
- A stroke records its slot: when `PrepareGestureCommit` yields a commit for a `Paint` effect, the
  painted index moves to the front of `recent` and the ninth entry falls off. Erase does not record.
  Selecting a slot through the menu, the palette panel or the eyedropper does not record: recent means
  used for drawing.
- `WorkspaceState.canvasPointerIntent` is a derived property with the closed enum
  `CanvasPointerIntent { Draw, PickPaletteEntry }`. It is `PickPaletteEntry` exactly while the
  eyedropper is armed. Presentation translates a canvas pointer down by this intent instead of
  inspecting `QuickSelection` (CMD-010).
- `WorkspaceState` stays within 11 functions by replacing `withPreview(ToolGesture)` and
  `withoutPreview()` with one `withPreview(ToolGesture?)` and adding one `withQuickSelection`.

### Actions and reduction

A sealed family `WorkspaceAction.QuickSelectAction`, handed whole by `WorkspaceReducer.reduce` to one
file-level reduction next to the reducer (like `PaletteSessionAction`), so the reducer class gains no
member function:

- `OpenQuickSelect`: opens the menu with the items above. `Rejected` while a gesture preview exists or
  the eyedropper is armed; `Unchanged` when already open.
- `HighlightQuickSelectItem(item: QuickSelectItem?)`: `Rejected` when the menu is closed or the item
  is not one of its items; `Unchanged` for the current highlight.
- `ConfirmQuickSelect`: closes the menu and applies the highlight. `PaletteSlot` selects the slot with
  the same validation as `SelectPaletteEntry` (typed rejection outside the palette); `Eyedropper` arms
  the eyedropper; no highlight closes like a cancel. `Rejected` when the menu is closed.
- `CancelQuickSelect`: closes the menu and changes nothing else; `Unchanged` when closed.
- `PickPaletteEntryAt(position: PixelPosition)`: reads `source.document.snapshot.indexAt(position)`.
  A position outside the canvas is `Rejected` and the eyedropper stays armed. A read slot is selected
  (duplicate RGBA values stay distinct because the index is read, never the colour) and the
  eyedropper returns to idle, as `Reduced` even when the slot was already active. `Rejected` when the
  eyedropper is idle.
- `DisarmEyedropper`: returns to idle; `Unchanged` when idle.

`SelectTool` also returns the eyedropper to idle. `BeginGesturePreview` is `Rejected` while the menu
is open or the eyedropper is armed, so a second finger on the canvas cannot draw behind the menu.
During a palette edit session (ADR 0022) `OpenQuickSelect`, `HighlightQuickSelectItem`,
`ConfirmQuickSelect` and `PickPaletteEntryAt` are refused and `CancelQuickSelect` and
`DisarmEyedropper` pass. `ReconcileDocumentPalette` moves `recent` by the same policy as
`activePaletteIndex` (`PaletteSelectionPolicy`): a `ReplacePaletteCommand` maps every entry through its
remap and keeps the first of any entries that land on the same slot; any other palette change (undo or
redo) drops entries outside the palette instead of substituting the default slot, because recent means
slots that were painted. It also closes the menu and disarms. Document replacement builds a fresh `WorkspaceState`, so the selection
state is not carried over (unlike appearance and the actual-size window).

`CMD-002`'s selection sentence changes from "`SelectPaletteEntry` is its only mutation route" to "the
reducer's slot selection is its only mutation route, reached from `SelectPaletteEntry`,
`ConfirmQuickSelect` and `PickPaletteEntryAt`". Nothing in this ADR emits a `DocumentCommand` or
changes revision, history, dirty state or a saved payload.

### Canvas pointer translation

`EditorController.pointerDown` translates by `canvasPointerIntent`: `Draw` keeps
`BeginGesturePreview`; `PickPaletteEntry` maps the point and reduces `PickPaletteEntryAt`, then
acknowledges the pointer down as not drawing, so the canvas pointer session suppresses the rest of
that pointer stream. `ViewportPointerSession` and `PointerArbitration` do not change.

### Floating control

- One circular control of 56dp sits inside the editor work area at the bottom corner on the
  `EditorControlEdge` side, 16dp from both edges, in both layouts. It shows the active slot colour and,
  while armed, an eyedropper mark. It is a `Role.Button` with a localized description and state.
- Pressing it dispatches `OpenQuickSelect` immediately. The items fan out as an arc facing into the
  work area around the control. While the same pointer is down, the item nearest to the pointer within
  its hit radius is highlighted; back on the control or away from every item clears the highlight.
  Release dispatches `ConfirmQuickSelect`. A release within the tap timeout without passing the touch
  slop instead keeps the menu open in tap mode. The control consumes its own pointer stream, so no
  event reaches the canvas.
- Tap mode is the accessible alternative: items are buttons with localized descriptions; tapping an
  item dispatches `HighlightQuickSelectItem` then `ConfirmQuickSelect`; tapping the control, tapping
  outside through a transparent scrim over the work area, or Back dispatches `CancelQuickSelect`.
  Tapping the control while armed dispatches `DisarmEyedropper`.
- Back in tap mode uses `androidx.activity.compose.BackHandler`, enabled only while tap mode is open.
  `:presentation:compose` gains `implementation(libs.androidx.activity.compose)`, the catalog entry
  `:app:android` already ships at the same locked version (1.13.0), so the APK content, size and
  startup do not change. The API stays inside the quick-select composables and no activity type
  reaches a public API. Rejected: routing Back from `:app:android` through a callback, which would
  make the app root track Compose-local tap mode; and `onKeyEvent(Key.Back)`, which predictive back
  (target SDK 37) does not deliver as a key event and which would be a second Back path.
- Drag versus tap mode is Compose-local input mechanics; everything that selects, previews or cancels
  is workspace state through actions. Leaving composition or a cancelled pointer stream while the
  menu is open in drag mode dispatches `CancelQuickSelect`.
- The drag preview is the highlighted item shown at the control centre (slot colour and number); the
  active slot does not change until confirm, so a cancel needs no restore.
- Stylus and touch behave the same; no pointer-type branch is added.
- The overlay reads `quickSelection`, the active index and the palette definition through one
  `derivedStateOf`, so a stroke in progress never recomposes it.
- Strings in all three locales: control label and state, eyedropper, slot item description.

### Wording

The glossary gains "quick select" and "eyedropper".

## Rejected alternatives

### Long press on the canvas

Rejected by the repository owner: the first canvas pointer down starts a stroke, so detecting a hold
needs either a delayed stroke start or a retracted first dot, both of which change dot placement.

### Eyedropper as a `DrawingTool`

Rejected: `DrawingTool` selects a `StrokeEffect` (ADR 0007) and every tool produces an
`ApplyStrokeCommand`; a tool that emits no command would give the type two meanings (ARC-001). The
one-shot armed state keeps the tool dock unchanged.

### Preview by changing `activePaletteIndex` during the drag

Rejected: a cancel would need to restore the original selection and a lifecycle loss mid-drag could
leave the preview selected. The highlight is separate state and the selection changes once.

### Recent colours by RGBA value

Rejected: duplicate RGBA slots are distinct palette entries; recording indices keeps the exact-slot
meaning of the eyedropper.

### A new pointer phase in `ViewportPointerSession`

Rejected: the session is at the function limit and the floating control owns a separate pointer
stream, so the canvas recognizer needs no menu knowledge.

## Consequences

### Benefits

- One press-drag-release changes colour with one finger and never draws.
- Exact-slot picking and recent slots reuse the existing read-only snapshot and slot validation.
- A later tool or zoom item is one `QuickSelectItem` case and one reduction branch.

### Costs and risks

- One more always-visible control over the work area; it covers a 88dp corner of the canvas.
- Recent slots start empty in each document, so the first menu shows only the eyedropper.
- The hot path gains one enum read per canvas pointer down and one bounded list update per commit.

## Enforcement impact

- Reducer tests cover every `QuickSelectAction` result, recording on paint commit only, the limit of
  eight, reconciliation, palette-session gating and the rejected `BeginGesturePreview`.
- Runtime tests pick an exact duplicate-RGBA slot and reject outside the canvas through
  `EditorRuntime`.
- Compose instrumented tests cover press-drag-release, cancel by returning to the control, tap mode,
  Back, disarm, both control edges, and zero overlay recompositions during a stroke.
- Functional device inspection on the supported tablet covers both layouts and both control edges.
- No new frame collection is required: the changed hot-path work is constant and bounded, and the
  overlay isolation is verified by the recomposition test. A later frame-budget failure is judged by
  the M5 budget, not by this ADR.

## Migration and rollback

No persisted data changes. Rollback removes the control, the action family and the field.

## Related

ADR 0007, ADR 0008, ADR 0020, ADR 0022, ADR 0026. Waivers: none.

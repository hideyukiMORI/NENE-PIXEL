# Interface Inventory

Status: record of the current editor interface; not normative

This document records the editor surface as implemented today and how each control maps onto the
[Interface principle](PROJECT_CHARTER.md#interface-principle). The Charter is the authority. This file only
records current state and the move candidates that state exposes; it adds no rule.

The accepted target amendment below is separate from current-source evidence. ADR 0041 is its
authority; those frame controls are planned consuming implementation, not shipped interface.

Evidence is the Compose editor source under `presentation/compose/src/main/kotlin/.../presentation/compose/editor/`.
Each row names the file it was read from. `Primary?` means the control satisfies both conditions of the
principle: understood at first sight, and used in every session.

## Current placement

| Surface | Control | Primary? | Proposed placement | Note |
| --- | --- | --- | --- | --- |
| always-visible | Pencil | yes | always-visible | `EditorToolDock.kt` |
| always-visible | Eraser | yes | always-visible | `EditorToolDock.kt` |
| always-visible | Undo | yes | always-visible | `EditorToolDock.kt` |
| always-visible | Redo | yes | always-visible | `EditorToolDock.kt` |
| always-visible | Palette button showing the active color | yes | always-visible | `EditorToolDock.kt`; opens the palette surface |
| always-visible | Preview window toggle | yes | always-visible | `EditorToolDock.kt`; first sight: a preview-window icon labelled Preview, selected while the window is open; every session: checking the artwork at real size is part of every pixel-art session |
| always-visible | Canvas drawing, zoom, pan | yes | always-visible | `PixelCanvas.kt`; grid visibility follows zoom and has no control |
| always-visible | Layer chip showing the active layer name | yes | always-visible | `LayerChip.kt`; first sight: the active layer's name sits at the top corner of the work area, and pressing it opens the layer list; every session: switching the layer to draw on and checking the stacking with the eye icon, while only the name shows when the panel is closed |
| always-visible | File button | yes | always-visible | `EditorScreen.kt` header; opens the file surface |
| always-visible | Settings button | yes | always-visible | `EditorScreen.kt` header; opens the settings sheet |
| always-visible | Application title text | no | move candidate: settings sheet | `EditorScreen.kt` header; identity, not an action |
| always-visible | Canvas dimension readout | no | move candidate: file surface | `EditorScreen.kt` header; information display |
| always-visible | Dirty-state text | no | move candidate: compact marker on the file control | `DocumentStatusRow.kt`; the capability itself is required by MVP scope |
| always-visible | Persistence operation status text | no | move candidate: transient feedback plus the file surface | `DocumentStatusRow.kt`; the same text also renders inside the file surface |
| always-visible, conditional | Recovery offer with Recover and Discard | no | keep conditional | `RecoveryOffer.kt`; replaces the status texts only while an unsaved session exists |
| settings sheet | Theme choice | no | settings sheet | `AppearanceControls.kt` |
| settings sheet | Layout choice | no | settings sheet | `AppearanceControls.kt` |
| settings sheet | Control-edge choice | no | settings sheet | `AppearanceControls.kt` |
| settings sheet | Language choice and retry | no | settings sheet | `LanguageChoices.kt` |
| settings sheet | App version text | no | settings sheet | `AppVersionLine.kt`; read by the host from PackageManager, shown after the language choice |
| secondary: file surface | Save As | no | secondary | `PersistenceControls.kt` |
| secondary: file surface | Load | no | secondary | `PersistenceControls.kt` |
| secondary: file surface | New document and its dimension dialog | no | secondary | `NewDocumentControls.kt` |
| secondary: file surface | Export PNG | no | secondary | `PersistenceControls.kt`; ADR 0041's consuming implementation will capture the chosen frame with the committed document |
| secondary: file surface | Import PNG | no | secondary | `PersistenceControls.kt`; ADR 0041's layer form will retain its captured frame and layer anchor across focus-only changes |
| secondary: file surface | Cancel running operation | no | secondary | `PersistenceControls.kt`; shown only while an operation is cancellable |
| secondary: file surface | Operation status text | no | single owner needed | `EditorScreen.kt`; duplicates the always-visible status text |
| secondary: file surface | About this version | no | secondary | `MvpInformationControls.kt` |
| secondary: palette surface | Palette entry grid and entry count | no | secondary reached from a primary control | `PaletteControls.kt`; the primary action is the dock palette button |
| secondary: palette editor | Saturation/value plane, hue, opacity and HEX for the selected draft slot | no | secondary | `PaletteColorPicker.kt`, `PaletteColorEditor.kt`; exact RGBA channels expand on demand; one drag is one draft history step (#168) |
| secondary: palette editor | Draft Undo/Redo and Apply/Cancel | no | secondary | `PaletteEditorControls.kt`; footer stays reachable while slots and secondary operations scroll (#168) |
| secondary: actual-size window | Window drag and scale cycling, shown by the top grip handle and the trailing scale chip | no | secondary reached from a primary control | `ActualSizeWindowOverlay.kt`; the primary action is the dock toggle, placement and scale stay on the window itself; the handle starts a move and the chip is a Role.Button that cycles the scale (ADR 0026 amendment #126); ADR 0041 will add the chosen-frame render key while retaining the shared palette draft preview |
| secondary: layer panel | Layer rows with select and visibility toggle | no | secondary reached from a primary control | `LayerPanelRows.kt`, `LayerRow.kt`; the primary action is the layer chip; tapping a row makes it the active layer, and the eye icon toggles visibility |
| secondary: layer panel | Row more menu with Rename, Duplicate, Move up, Move down, and Delete | no | secondary | `LayerRowMenu.kt`; an item is disabled when its duplicate, move or delete is not possible; Duplicate copies that row directly above itself and selects the copy (#208) |
| secondary: layer panel | Select area in the row More menu | no | secondary | `LayerRowMenu.kt`; selects that visible row through the canonical active-layer route, then closes the panel; hidden rows disable it (#211, ADR 0039) |
| secondary: transient | Selection outline, Move/Copy, flips/quarter turn and Apply/Cancel bar | no | secondary | `SelectionOutline.kt`, `SelectionBar.kt` (#211/#213); appears only after Select area; repeated drag/orientation adjustments remain a draft until Apply, current dimensions and empty-cell replacement are disclosed, and Back cancels; rotation that cannot fit explains its refusal; short work areas scroll secondary controls while Apply/Cancel remain fixed; no permanent dock button |
| secondary: layer panel | Choose underlay image | no | secondary | `LayerPanelUnderlayRow.kt`; shown only while there is no underlay; follows the layer rows and scrolls with them, above Add layer; opens the image picker (#170) |
| secondary: layer panel | Underlay row with visibility toggle, more menu (Replace image, Move and scale, Fit to picture, Remove), and opacity slider | no | secondary | `LayerPanelUnderlayRow.kt`, `UnderlayRowMenu.kt`; shown only while there is an underlay; follows the layer rows and scrolls with them, above Add layer; never selectable as a drawing target; the slider is disabled while the underlay is hidden (#170); Move and scale is disabled while the underlay is hidden and closes the panel when chosen (#171) |
| secondary: transient | Underlay adjust bar with Fit to picture, Done, and opacity slider | no | secondary | `UnderlayAdjustBar.kt`; shown only while the underlay is being moved and scaled, at the bottom centre of the work area; Back also ends the mode (#171) |
| secondary: layer panel | Add layer | no | secondary | `LayerPanelAddRow.kt`; stays below the scrolling rows; disabled with a limit line at 16 layers; ADR 0041 will use the document-wide total across frames for this capacity |
| secondary: dialog | Discard-current confirmation | no | secondary | `PersistenceControls.kt`; shown only during a confirmed document switch |
| secondary: dialog | Layer rename | no | secondary | `LayerRenameDialog.kt`; opened from the row more menu |
| secondary: dialog | PNG import choice | no | secondary | `PngImportDialog.kt`; shown only while a picked PNG awaits the choice of a form |
| secondary: transient | Layer notice | no | secondary | `LayerNoticeHost.kt`; transient; it offers Undo after a delete and Show after an attempt to draw on a hidden layer; the dock Undo and the panel visibility toggle keep both actions after it disappears |

## Accepted frame target — consuming implementation pending

[ADR 0041](adr/0041-bounded-frames-and-project-v4.md#secondary-frame-interface) / #215 accepts
the following secondary interface for the atomic frame/v4 cutover. The current single-frame UI
above remains implemented until that cutover. These rows name target behavior rather than
nonexistent source files; they do not add an always-visible frame dock or timeline. Playback is
a separate decision and implementation after frame editing, not an action shipped by this target.

| Target surface | Target control | Primary? | Accepted behavior |
| --- | --- | --- | --- |
| secondary: layer-panel heading | Frames entry and chosen-frame ordinal | no | The existing layer-chip entry still opens the layer panel; its heading opens the separate frame panel and shows the chosen frame |
| secondary: frame panel | Chronological frame rows with ordinal and duration | no | 1–16 rows, stable FrameId keys and localized ordinal/ms labels; choosing a row selects its frame through the canonical workspace action and returns to that frame's layer panel |
| secondary: frame-row More | Duplicate, Move earlier/later, Duration and Delete | no | Row-ID targets; positions, last-frame protection, frame count and actual document-wide layer capacity govern availability; structural/duration changes use one document command each |
| secondary: frame-panel fixed footer | Add blank frame | no | Adds after the named frame with default 100 ms and one Empty layer; the add control stays reachable outside scrolling rows; it does not flatten or discard existing layers to make room |
| secondary: frame panel and layer panel | Document-wide layer capacity and reason | no | At most 16 total layer records across all frames, including shared snapshots; display remaining capacity and explain why a 16-layer work cannot add a frame. Frame duplicate needs room for all source layers; existing layer Add/Duplicate adopt the same capacity projection |
| secondary: duration dialog | Integer-ms field, Confirm and Cancel | no | Validated 10–60,000 ms; explicit confirmation submits one command, identical duration is a no-op, and Cancel/Back submits no command |
| secondary: palette editor | Preview-frame dropdown above the scrolling slot/editor body | no | Shown only for multiple frames, with localized chosen ordinal/count; uses the same SelectFrame action and remains inside the existing palette dialog, retaining the shared palette draft and its timeline; no frame commands or separate preview-frame owner |

The target keeps maximum 360dp width, viewport-aware height, scrolling rows and at least 48dp
actions, with a fixed blank-add footer. Localized labels, unit/error/disabled reasons, accessibility
descriptions and focus on the chosen stable row are part of the consuming implementation in all
existing app languages. Ordinal is display text, never identity; there is no saved frame name.
All frames share the document palette. Layer rows and visibility apply to the chosen frame;
capacity applies to the complete document. Existing single-frame/no-animation informational copy
is updated only when its consuming capability is implemented. No thumbnails are required.
The palette dropdown uses the existing menu mechanics and a compact 48dp row before the weighted
scroll body, preserving the fixed draft Undo/Redo and Apply/Cancel footer. Its popup-open state is
local presentation mechanics; selected FrameId remains canonical workspace state. Canvas and
actual-size follow that chosen frame with the same palette draft preview, excluding stroke and
selection drafts from actual-size as before.

## Move candidates

The rows marked `move candidate` are the only ones this inventory proposes to relocate. They are all
information display on the always-visible surface, not actions.

No relocation is implemented here. Each candidate needs its own focused Issue that states the target
surface, the canonical action or command path it keeps, and the accessibility and test impact.

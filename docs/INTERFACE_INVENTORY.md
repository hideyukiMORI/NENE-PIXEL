# Interface Inventory

Status: record of the current editor interface; not normative

This document records the editor surface as implemented today and how each control maps onto the
[Interface principle](PROJECT_CHARTER.md#interface-principle). The Charter is the authority. This file only
records current state and the move candidates that state exposes; it adds no rule.

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
| secondary: file surface | Export PNG | no | secondary | `PersistenceControls.kt` |
| secondary: file surface | Import PNG | no | secondary | `PersistenceControls.kt` |
| secondary: file surface | Cancel running operation | no | secondary | `PersistenceControls.kt`; shown only while an operation is cancellable |
| secondary: file surface | Operation status text | no | single owner needed | `EditorScreen.kt`; duplicates the always-visible status text |
| secondary: file surface | About this version | no | secondary | `MvpInformationControls.kt` |
| secondary: palette surface | Palette entry grid and entry count | no | secondary reached from a primary control | `PaletteControls.kt`; the primary action is the dock palette button |
| secondary: actual-size window | Window drag and scale cycling, shown by the top grip handle and the trailing scale chip | no | secondary reached from a primary control | `ActualSizeWindowOverlay.kt`; the primary action is the dock toggle, placement and scale stay on the window itself; the handle starts a move and the chip is a Role.Button that cycles the scale (ADR 0026 amendment #126) |
| secondary: layer panel | Layer rows with select and visibility toggle | no | secondary reached from a primary control | `LayerPanelRows.kt`, `LayerRow.kt`; the primary action is the layer chip; tapping a row makes it the active layer, and the eye icon toggles visibility |
| secondary: layer panel | Row more menu with Rename, Move up, Move down, and Delete | no | secondary | `LayerRowMenu.kt`; an item is disabled when its move or delete is not possible |
| secondary: layer panel | Choose underlay image | no | secondary | `LayerPanelUnderlayRow.kt`; shown only while there is no underlay; follows the layer rows and scrolls with them, above Add layer; opens the image picker (#170) |
| secondary: layer panel | Underlay row with visibility toggle, more menu (Replace image, Fit to picture, Remove), and opacity slider | no | secondary | `LayerPanelUnderlayRow.kt`, `UnderlayRowMenu.kt`; shown only while there is an underlay; follows the layer rows and scrolls with them, above Add layer; never selectable as a drawing target; the slider is disabled while the underlay is hidden (#170) |
| secondary: layer panel | Add layer | no | secondary | `LayerPanelAddRow.kt`; stays below the scrolling rows; disabled with a limit line at 16 layers |
| secondary: dialog | Discard-current confirmation | no | secondary | `PersistenceControls.kt`; shown only during a confirmed document switch |
| secondary: dialog | Layer rename | no | secondary | `LayerRenameDialog.kt`; opened from the row more menu |
| secondary: dialog | PNG import choice | no | secondary | `PngImportDialog.kt`; shown only while a picked PNG awaits the choice of a form |
| secondary: transient | Layer notice | no | secondary | `LayerNoticeHost.kt`; transient; it offers Undo after a delete and Show after an attempt to draw on a hidden layer; the dock Undo and the panel visibility toggle keep both actions after it disappears |

## Move candidates

The rows marked `move candidate` are the only ones this inventory proposes to relocate. They are all
information display on the always-visible surface, not actions.

No relocation is implemented here. Each candidate needs its own focused Issue that states the target
surface, the canonical action or command path it keeps, and the accessibility and test impact.

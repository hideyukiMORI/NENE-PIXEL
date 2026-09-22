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
| always-visible | Canvas drawing, zoom, pan | yes | always-visible | `PixelCanvas.kt`; grid visibility follows zoom and has no control |
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
| secondary: file surface | Cancel running operation | no | secondary | `PersistenceControls.kt`; shown only while an operation is cancellable |
| secondary: file surface | Operation status text | no | single owner needed | `EditorScreen.kt`; duplicates the always-visible status text |
| secondary: file surface | About this version | no | secondary | `MvpInformationControls.kt` |
| secondary: palette surface | Palette entry grid and entry count | no | secondary reached from a primary control | `PaletteControls.kt`; the primary action is the dock palette button |
| secondary: dialog | Discard-current confirmation | no | secondary | `PersistenceControls.kt`; shown only during a confirmed document switch |

## Move candidates

The rows marked `move candidate` are the only ones this inventory proposes to relocate. They are all
information display on the always-visible surface, not actions.

No relocation is implemented here. Each candidate needs its own focused Issue that states the target
surface, the canonical action or command path it keeps, and the accessibility and test impact.

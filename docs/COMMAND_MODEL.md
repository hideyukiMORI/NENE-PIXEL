# Canonical Command Model

Status: normative

This document defines the only valid behavior paths for editor state.

## Two state-changing languages

NENE-PIXEL has exactly two state-changing languages because persistent document truth and ephemeral workspace behavior have different semantics.

### Document commands

`DocumentCommand` changes saved or undoable document truth.

Examples:

- `ApplyStrokeCommand`
- `ApplyPixelPatchCommand`
- `AddLayerCommand`
- `RenameLayerCommand`
- `ReorderLayerCommand`
- `SetPaletteEntryCommand`
- `ReplaceDocumentCommand`

### Workspace actions

`WorkspaceAction` changes ephemeral interaction state that is not saved and not part of document undo history.

Examples:

- `SetActiveToolAction`
- `SetViewportAction`
- `SetHoveredPixelAction`
- `ShowStrokePreviewAction`
- `DismissDialogAction`

If a fact must survive save/load or participate in undo, it belongs to `DocumentState`. If not, it belongs to `WorkspaceState`. A fact must never exist authoritatively in both.

## Canonical flow

```text
Touch / Stylus / Mouse / Keyboard
                |
                v
           ToolController
                |
      preview -> WorkspaceAction
      commit  -> DocumentCommand
                |
                v
          CommandGateway  <----- future automation adapter
                |
                v
         CommandValidator
                |
                v
          CommandHandler
                |
                v
     DocumentTransition + ChangeSet
        |          |          |
        v          v          v
  DocumentState  History  Render invalidation
```

Persistence observes committed results through an application port. It does not create another mutation path.

## Mandatory rules

### CMD-001 — Document mutation uses commands

All changes to `DocumentState` MUST enter through `CommandGateway.execute(DocumentCommand)`. Direct setters, mutable collections, UI callbacks that alter the document, and repository-level partial mutations are prohibited.

### CMD-002 — Workspace mutation uses actions

All changes to `WorkspaceState` MUST enter through `WorkspaceReducer.reduce(WorkspaceAction)`. Composables MUST NOT directly modify workspace fields.

### CMD-003 — One command, one handler

Every concrete `DocumentCommand` MUST map to exactly one concrete handler. Handler discovery MUST be exhaustive and compile-time visible. Reflection and string-based handler lookup are prohibited.

### CMD-004 — Commands express domain intent

Commands MUST describe a complete user/domain operation, not UI events or storage operations. Names such as `OnButtonClickedCommand`, `UpdateDataCommand`, and `SaveToRoomCommand` are prohibited.

### CMD-005 — Drawing commits atomic patches

A pointer gesture MAY generate ephemeral previews, but MUST commit as one semantic drawing command. Per-move or per-pixel history entries are prohibited.

The default drawing unit is a stroke or an explicitly bounded `PixelPatch`.

### CMD-006 — Validation precedes transition

A handler MUST validate identifiers, dimensions, bounds, mode compatibility, and preconditions before changing state. Partial application is prohibited.

An invalid command returns a typed rejection and leaves all state unchanged.

### CMD-007 — Results carry the complete change

A successful command MUST return a `ChangeSet` containing enough information to:

- update `DocumentState`
- create the undo record
- derive render invalidation
- identify dirty/save state
- support deterministic tests
- support future human approval of automated changes

Consumers MUST NOT infer the change again from UI input.

### CMD-008 — Undo and redo use recorded transitions

Undo/redo MUST operate on committed `ChangeSet` records or their canonical inverse representation. UI-specific closures, arbitrary object snapshots per pixel, and handler-specific undo callbacks are prohibited.

Undo and redo themselves enter through the application command boundary.

`Revision` is the version recorded on a specific committed `DocumentState` and its patches in this transition contract. Applying a recorded canonical inverse restores its recorded before revision, and redo restores its recorded after revision. A revision value does not uniquely identify a state across abandoned and replacement branches, and it is not a globally monotonic event or audit sequence; any future asynchronous lineage or audit sequence requires a distinct type and an accepted ADR.

### CMD-009 — Queries never mutate

Queries MUST return immutable projections and MUST NOT modify document, workspace, history, caches visible outside their owner, or persistence state.

### CMD-010 — Adapters translate; they do not decide

Compose, Android lifecycle, filesystem, database, import/export, and future automation adapters MAY translate external input to typed commands or actions. They MUST NOT contain business invariants or alternate transition logic.

### CMD-011 — Command serialization is explicit

If commands later cross a process or automation boundary, serialized command DTOs MUST be versioned and mapped to internal command types at the boundary. Internal Kotlin class names are not an external protocol.

### CMD-012 — Cancellation is atomic

Cancellation before commit leaves `DocumentState` unchanged. Cancellation after an atomic commit does not silently roll the commit back; it is followed by an explicit undo command if reversal is required.

## Canonical result vocabulary

Concrete names may be introduced through the first implementation ADR, but the result algebra must remain closed:

```kotlin
sealed interface CommandResult {
    data class Applied(val changeSet: ChangeSet) : CommandResult
    data class Rejected(val reason: RejectionReason) : CommandResult
    data class Failed(val failure: CommandFailure) : CommandResult
}
```

`Rejected` represents an expected invalid operation. `Failed` represents an external or runtime failure. Neither is represented by `null`, `false`, or a generic exception.

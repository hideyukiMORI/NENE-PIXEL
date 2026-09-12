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

- `SelectTool`
- `SetViewportAction`
- `SetHoveredPixelAction`
- `ShowStrokePreviewAction`
- `DismissDialogAction`
- `SetAppearance`

If a fact must survive save/load or participate in undo, it belongs to `DocumentState`. If not, it belongs to `WorkspaceState`. A fact must never exist authoritatively in both.

## Canonical flow

ADR 0020 adds `WorkspaceAction.SetAppearance` for session theme, tablet layout and physical control
edge. `WorkspaceState.appearance` is the single owner; setting it cancels active preview atomically.
It is undo-neutral and preserved through new/load/recovery runtime installation via the reducer.
Only a fresh process resets appearance to Dark/Tabletop/Right; project files never store it.

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

## New-document runtime boundary

Creating a new document installs a newly constructed editor runtime; it is not an edit of the
previous `DocumentState`. One application `EditorRuntime` atomically owns the active
`CommandGateway`, `WorkspaceState`, and clean checkpoint. Its canonical new-document path creates a
blank initial-revision document with empty history, a clean derived dirty state, and the canonical
initial viewport, then replaces all owned runtime parts together.

Raw dimension text is accepted only by `NewDocumentRequest.create`. Rejection occurs before
identity generation, snapshot allocation, or runtime replacement. Cancellation does not invoke the
factory. Document identity is supplied as a validated `DocumentId` through the application-owned
`DocumentIdSource` port.

`CMD-001` governs all mutations inside the active document runtime. Installing an entirely new
runtime does not mutate the abandoned document and MUST NOT create cross-document history. New
document, validated project load, accepted recovery, and explicit discard use the one runtime-install
boundary defined by ADR 0014; they are not document commands against the abandoned runtime.

## Durable document boundary

Application-owned persistence ports exchange one immutable `DocumentState` capture or one fully
validated loaded candidate. The application module never depends on the project-format codec, and a
persistence adapter never reads from or mutates a live runtime. Encoding, provider/file I/O, and
decoding occur outside the runtime lock through suspend ports and an application-composed IO
dispatcher.

Every persistence operation carries an application-owned runtime generation and operation identity.
A save additionally captures the exact internal `HistoryPosition`. A successful durable save
completion may install that captured clean checkpoint only when its runtime and active operation
still match. Later editing remains allowed during normal save: it leaves the current position dirty,
and undoing to the saved position becomes clean. `Revision` alone never identifies a save completion.

Runtime generation, operation identity, switch-busy state, save capture, and recovery ordering are
private coordination bookkeeping inside the existing application owner. They are not a fourth state
category, document/workspace truth, or an adapter/UI-owned copy. `EditorRuntime` owns the sole mutable
operation flow; UI and adapters may only observe its read-only immutable projection and emit typed
requests carrying opaque handles.

Load validates the complete bounded file before installation. Editing may continue during the long
read/decode phase. Its source token captures `DocumentId`, runtime generation, active operation
identity, and starting `HistoryPosition`; all must match immediately before switching. Undo or redo
back to that exact position is the same source state and may proceed, while a different position or
new branch requires a typed stale/reconfirmation outcome and fresh discard consent. Generation-only
validation is prohibited. The short final destructive-switch phase rejects document commits and
other switches as typed busy, rejects cancellation as too late, cancels preview through
`WorkspaceReducer`, and executes recovery retirement plus atomic installation in one non-cancellable
workflow block. Failure to retire leaves the old runtime authoritative. A post-finish result whose
durable generation cannot be proven marks recovery lineage unknown and blocks another destructive
switch until inspection reconciles it. Loaded documents start clean. Explicitly accepted recovery
uses the same installation path but retains its valid recovery Candidate as the new runtime's
last-safe lineage and starts dirty because no user-file save checkpoint exists.

Cancelling a save/load/confirmation operation invalidates its opaque operation identity but retains
one physical-operation lease until the picker/transport call and cleanup have actually finished.
Late success cannot update a checkpoint or install a runtime, and another operation remains typed
busy while cancellation drains. An unadopted startup Candidate is preserved by Save As. P3-03 permits
new/load to retire it only after an explicit warning that the recovery data will be discarded; P3-04
later adds the recovery-accept path without changing this lineage rule.

Explicit user-file save is Save As to a fresh Android document only. Saved is returned only after
all encoded bytes are written and closed, then read back byte-for-byte and validated. Existing
content URIs are never truncated or replaced. Autosave derives only from committed command results
and writes the one bounded private recovery record selected by ADR 0014; it is not a second document
owner or command path.

## PNG export boundary

ADR 0019 adds `EditorPersistenceWorkflow.exportPng` through `PngExportPort`. The runtime captures an
immutable document and retains the existing physical-operation lease through export or cancellation
cleanup. Export permits editing and never modifies document, history, workspace, clean checkpoint,
or recovery. Matching completion only projects the typed export outcome. The adapter uses the one
fresh-destination writer and verifies exact PNG bytes before reporting success.

## Bounded autosave boundary

Every committed command result that changes the document records one immutable autosave capture in
the same persistence coordination, including committed undo and redo. A newer capture replaces the
pending one, so the pending set is never larger than one. Capture identity is the runtime generation
plus exact internal history position, never Revision alone. A committed return to the exact
published state clears the pending capture only when no active persistence operation can replace
or retire that Candidate. Otherwise the latest current state remains pending. A Candidate
publication is one persistence operation under the existing
one-active lease: a user operation waits for an active publication, a publication requested during a
user operation answers a typed deferred result and keeps the capture, and a publication requested
while an unadopted startup Candidate is still offered answers a typed offer-pending result so
autosave never overwrites the previous session's unsaved work. A verified explicit save at a clean
boundary drops only the exact captured runtime/history state and keeps any different pending state.
This also applies when an unadopted Candidate or unavailable recovery lineage prevents cleanup.
Retirement or unproven recovery lineage invalidates the previously published Candidate identity.

The platform owns the clock. `AutosavePolicy` in `:app:android` holds the only ADR 0018 quiet-window
and latency-cap numbers, the autosave scheduler observes opaque exact-state tokens in the read-only
autosave projection on the
ViewModel scope and keeps at most one outstanding request, and the activity `ON_STOP` event requests
one immediate publication of any pending capture. Core creates no scope, dispatcher, or timer and
reads no wall time. A structured platform observer keeps the single derived deadline state current
while a publication is in flight. The publishing token excludes that capture from the next request;
the cap for a distinct pending capture survives the preceding publication's completion.
An obsolete request result cannot suspend a newer observation.
Accepting the startup recovery offer installs the Candidate through the one
runtime-install boundary, retains its generation as the new runtime's last-safe lineage, and starts
dirty; declining retires that generation without replacing the runtime.

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

The accepted conservative MVP limits are one `PixelLimits` policy: at most 262,144 ordered raw
stroke positions and at most 65,536 unique effective patch changes. Stroke construction rejects an
oversized raw path before containment scan or ownership copy. Patch construction rejects an
oversized change set before sorting or packed ownership.

Pencil and Eraser are one closed `DrawingTool` selection vocabulary. `WorkspaceState` owns the
active tool, and `WorkspaceAction.SelectTool` is its only mutation route. Beginning a `ToolGesture`
captures either `StrokeEffect.Paint` with the color resolved from the current palette selection or
`StrokeEffect.Erase`; later tool or palette-selection changes do not alter that gesture.

`Palette` is bounded immutable tool configuration supplied by the composition root and retained by
`EditorRuntime`; it is not document or pixel truth. `WorkspaceState` owns only the typed
`activePaletteIndex`, and `WorkspaceAction.SelectPaletteEntry` is its only mutation route. The
reducer returns a typed rejection for an index outside the configured palette and a typed unchanged
result for the current index. Selection emits no document command and changes no revision, history,
or dirty state. Displayed active color is always derived from palette plus selection.

Accepted document-pixel samples are connected by the one endpoint-inclusive, direction-symmetric,
8-connected integer line rule in `ToolGesture`. The expanded count is checked against the raw-stroke
limit before accepting each sample. One completed gesture produces exactly one
`ApplyStrokeCommand`. Both effects enter the same handler, rasterizer, patch, and history path;
Erase derives canonical `PixelBlank` as its target. Painting an equal value and erasing blank share
`NoEffectiveChange` and change no revision, history, or dirty state.

### CMD-006 — Validation precedes transition

A handler MUST validate identifiers, dimensions, bounds, mode compatibility, and preconditions before changing state. Partial application is prohibited.

Canvas axes are limited to 256 and total area to 65,536 pixels before snapshot, work-buffer, or
render allocation. History accepts at most 64 committed entries and 524,288 total retained pixel
changes; both budgets are checked before document and history commit. Limits are deterministic and
MUST NOT branch on runtime free memory or adapter identity.

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

The canonical inverse is a directional view over the same packed position/before/after payload as
the forward patch. It swaps exact before/after values and revisions without materializing a second
change payload.

Undo and redo themselves enter through the application command boundary.

`CommandGateway` owns one `BoundedLinearHistory`: an immutable ordered entry list and one cursor
between entries. Entries before the cursor are undoable and entries at or after it are redoable, so
an interior cursor exposes both operations. A successful new command after undo removes the redo
suffix before appending its `ChangeSet`. The oldest retained entries are then evicted until both the
64-entry and 524,288-change budgets hold; the new entry is never silently discarded. A single entry
that could not fit is rejected before either document or history commit.

Each retained entry also records internal before/after `HistoryPosition` values. These positions
distinguish replacement branches only inside the active runtime and are not revision, persistence,
audit, or command-staleness identities. `EditorRuntime` derives `DocumentDirtyState` from the exact
current document/position pair and its application-owned `DocumentCleanCheckpoint`. Undo to that
checkpoint is clean; the same revision on a replacement branch is dirty; an evicted checkpoint is
not recreated by undoing to the retained base.

`Revision` is the version recorded on a specific committed `DocumentState` and its patches in this transition contract. Applying a recorded canonical inverse restores its recorded before revision, and redo restores its recorded after revision. A revision value does not uniquely identify a state across abandoned and replacement branches, and it is not a globally monotonic event or audit sequence; any future asynchronous lineage or audit sequence requires a distinct type and an accepted ADR.

### CMD-009 — Queries never mutate

Queries MUST return immutable projections and MUST NOT modify document, workspace, history, caches visible outside their owner, or persistence state.

### CMD-010 — Adapters translate; they do not decide

Compose, Android lifecycle, filesystem, database, import/export, and future automation adapters MAY translate external input to typed commands or actions. They MUST NOT contain business invariants or alternate transition logic.

### CMD-011 — Command serialization is explicit

If commands later cross a process or automation boundary, serialized command DTOs MUST be versioned and mapped to internal command types at the boundary. Internal Kotlin class names are not an external protocol.

### CMD-012 — Cancellation is atomic

Cancellation before commit leaves `DocumentState` unchanged. Cancellation after an atomic commit does not silently roll the commit back; it is followed by an explicit undo command if reversal is required.

Viewport pan and zoom enter only through `WorkspaceAction.SetViewport`. The reducer installs the viewport and removes any active drawing preview in one transition, before two-pointer transformation may begin. This action never emits or executes a `DocumentCommand`, and therefore leaves `DocumentState`, `Revision`, and history unchanged.

Presentation may own pointer identifiers and the local one-pointer/two-pointer/suppressed phase needed to translate raw events. It MUST derive mapping from the current workspace viewport and current Document canvas through the canonical core `ViewportTransform`; it MUST NOT retain an authoritative matrix, viewport snapshot, or alternate rounding rule. After a second or additional pointer interrupts drawing, drawing resumes only after all pointers are up and a fresh gesture begins.

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

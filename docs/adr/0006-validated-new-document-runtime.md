# ADR 0006: Validated new-document runtime and lifecycle ownership

- Status: accepted
- Date: 2026-09-04
- Issue: #39
- Affected rules: `ARC-001`, `ARC-003`, `ARC-004`, `ARC-006` through `ARC-008`,
  `ARC-010`, `ARC-012`, `CMD-001`, `CMD-003`, `CMD-006`, `CMD-007`, `CMD-010`,
  `KOT-001`, `KOT-002`, `KOT-004`, `KOT-005`, `KOT-007`, `KOT-008`, `KOT-014`,
  `KOT-015`, `KOT-017` through `KOT-019`, `QLT-006`

## Context

The M1 composition creates a fixed 16 x 16 document every time `MainActivity.onCreate` runs.
`CommandGateway` owns document and history, while a presentation session separately owns
`WorkspaceState`. This was sufficient for the fixed vertical slice, but it cannot provide one
authoritative editor across configuration recreation or atomically replace all state that belongs
to a newly created document.

P2-01 now accepts canvas axes `1..256` and a maximum area of 65,536 pixels. P2-02 must expose those
limits through one typed input boundary, create no area-sized state for rejected input, obtain
document identity without reading random or process state in core, and reset document, history,
dirty state, and the ADR 0004 viewport together.

A new document is not an edit of the previously open document. Treating it as a
`ReplaceDocumentCommand` would require a cross-document `ChangeSet`, put the abandoned document in
the new document's undo history, and make one command gateway own two document identities. Saved
document replacement, import, and multi-document behavior are outside Issue #39.

## Decision

### Typed request boundary

`:core:application` owns `NewDocumentRequest.create(rawWidth, rawHeight)`. This factory is the first
project-owned boundary for raw dimension text. It normalizes surrounding whitespace, distinguishes
missing input, non-decimal input, integer overflow, and supported-range rejection, and returns a
closed `NewDocumentRequestResult`. Only a created request containing validated `CanvasWidth`,
`CanvasHeight`, and `CanvasSize` may enter allocation or runtime construction.

Compose and non-Compose adapters call this same factory. They do not parse integers, copy limit
constants, or infer rejection meaning. User-visible text is a presentation mapping over the closed
rejection vocabulary.

### Canonical runtime owner

One application `EditorRuntime` owns the current:

- `CommandGateway`, including its `DocumentState` and history;
- `WorkspaceState`, changed only by its `WorkspaceReducer`;
- `DocumentDirtyState`.

The runtime exposes immutable state projections and synchronous `execute` and `reduce` entry
points. Presentation translates input and renders projections; it owns no document, history,
workspace, or dirty-state copy.

`EditorRuntime.create` and successful `createNewDocument` both use one private construction path.
That path obtains a typed `DocumentId` from a `DocumentIdSource` port, creates one canonical blank
snapshot at `Revision.initial()` through the single-allocation `PixelSnapshot.createFilled` factory,
creates an empty-history `CommandGateway`, creates a clean dirty state, and creates
`WorkspaceState` through `WorkspaceState.create`, which supplies the ADR 0004 initial viewport.

Successful new-document creation builds a complete candidate and then replaces the owned runtime
parts under the same application lock. The prior document, history, dirty state, workspace, and
viewport cease to be authoritative together. Rejected raw input never invokes the identity port or
the construction path. Cancellation invokes neither request creation nor runtime replacement.

`CMD-001` continues to govern every mutation within an owned document runtime. Installing a newly
constructed runtime for a new document is a composition operation, not a mutation of the abandoned
`DocumentState`, and therefore is not represented as a `DocumentCommand`. A future operation that
replaces content while retaining the same logical open-document workflow requires its own Issue
and command/change-set decision.

### Identity and Android lifecycle

Core owns the `DocumentIdSource` port and reads no clock, random generator, UUID API, or process
state. `:app:android` supplies a UUID-backed adapter that removes UUID separators and validates the
result before it crosses the port. Tests supply deterministic typed identifiers through the same
port.

The Android composition root stores exactly one `EditorRuntime` in an activity-scoped AndroidX
`ViewModel`. `MainActivity` obtains that owner before Compose content is installed and passes one
presentation controller backed by the retained runtime. Configuration recreation reuses the same
runtime and workspace owner. Process-death recovery remains part of the later persistence and
recovery milestone; P2-02 does not serialize editor truth into Android saved-state primitives.

`androidx.lifecycle:lifecycle-viewmodel` is declared directly and without an independent version in
`:app:android` because the app now uses that API as its lifecycle boundary. The accepted
`androidx.activity:activity-compose` graph and dependency lock select version 2.9.4, so this
declaration adds no second lifecycle implementation or version policy and makes the used API an
explicit dependency. It uses the same Google repository, AndroidX maintenance source, and license
family already present in the Android application graph.

## Rejected alternatives

### Keep workspace ownership in the presentation controller

Rejected because recreation could construct a second owner and presentation would remain the
authority for application behavior rather than an adapter over immutable state.

### Store authoritative editor state in `remember` or `rememberSaveable`

Rejected because Compose state is a rendering lifecycle mechanism, cannot own the command runtime,
and saved-state primitives are not the project persistence format.

### Use an Android `Application` singleton or service locator

Rejected because it creates an ambient process-wide dependency, weakens explicit composition, and
has broader lifetime than the one-editor activity scope.

### Implement new document as `ReplaceDocumentCommand`

Rejected because command history and `ChangeSet` are scoped to one document transition. Crossing
document identity would either retain the abandoned document as undo data or introduce a special
non-replayable command path.

### Parse and enforce limits separately in Compose

Rejected because future adapters could disagree on whitespace, overflow, or supported bounds and
because UI code would become a second business-rule implementation.

## Consequences

### Benefits

- all document-creation adapters share one typed request and rejection vocabulary;
- rejected and cancelled creation cannot allocate or replace document runtime state;
- document, history, dirty state, workspace, and viewport change atomically on success;
- Android configuration recreation retains exactly one authoritative owner;
- core creation remains deterministic under a supplied identifier port.

### Costs and risks

- the fixed-slice presentation controller migrates to an application-owned runtime facade;
- the Android app declares the lifecycle ViewModel API directly;
- process death cannot restore unsaved work until the persistence milestone;
- creating the maximum canvas still performs one bounded 65,536-pixel allocation on the UI thread;
  asynchronous construction is deferred unless measured UI evidence requires it.

## Enforcement impact

- `COMMAND_MODEL.md` distinguishes new-runtime installation from in-document commands.
- `PROJECT_LAYOUT.md` assigns runtime/workspace ownership to application and lifecycle retention to
  the Android composition root.
- `GLOSSARY.md` records the new request, runtime, dirty-state, and identity-port meanings.
- focused application tests cover request boundaries, port use, atomic replacement, initial state,
  and mutation ownership.
- presentation tests cover the shared typed result and visible rejection.
- Android tests cover configuration recreation and the successful creation smoke path.
- no module, serialization schema, quality gate, suppression, baseline, or waiver is introduced.

## Migration and rollback

The fixed bootstrap factory and presentation-owned workspace session are removed in the same
focused change. Existing drawing, history, viewport, and pointer adapters migrate to
`EditorRuntime`; no compatibility owner or second construction path remains.

Rollback reverts this focused change as a whole to the fixed M1 composition. It must not retain the
new UI while restoring presentation-owned state or keep both bootstrap factories.

## Related

- Issue: #39
- Depends on: ADR 0005 canvas and storage limits
- Uses: ADR 0004 canonical initial viewport
- Refines: ADR 0002 fixed M1 composition boundary
- Supersedes: none
- Superseded by: none

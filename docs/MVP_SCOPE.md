# MVP Scope

Status: scope authority for Milestones M1 through M3

## MVP definition

The first MVP is complete at the end of M3. It is a dependable single-frame, single-layer Android pixel editor with durable project files and exact PNG export.

The MVP proves the canonical architecture with a product that is small enough to finish and useful enough to expose real design and performance constraints.

## Primary user journey

1. Launch NENE-PIXEL offline.
2. Create a document with validated pixel dimensions.
3. Zoom and pan without changing document coordinates.
4. Choose a palette color.
5. Draw and erase pixel-precise strokes.
6. Undo and redo complete gestures.
7. Save the document through the Android document boundary.
8. Close/restart the app and load the same document without pixel changes.
9. Export an exact PNG.
10. Recover the last safe document after an interrupted autosave scenario.

## Included

- Android application
- offline operation
- one document open at a time
- one frame and one layer
- validated document creation
- pencil and eraser
- palette selection needed by the drawing workflow
- touch input; stylus behavior where it maps to the same canonical gesture contract
- zoom, pan, and optional grid presentation
- gesture-level undo and redo
- dirty-state indication
- versioned project-file format v1
- explicit save/load and safe autosave recovery
- PNG export
- typed, user-visible rejection/failure states
- deterministic core tests and representative performance measurements

## Excluded from the first MVP

- multiple layers
- multiple animation frames or playback
- selections and transforms
- text, vector, filters, effects, antialiasing, and non-pixel brushes
- cloud sync, accounts, collaboration, or network dependency
- plugin system or scripting
- MCP, HTTP API, OpenAPI, or remote control
- desktop/iOS/web targets
- arbitrary image-format import/export beyond the accepted PNG scope
- marketplace or Play Store production launch

Excluded items require later milestone entry criteria; they are not hidden TODOs inside MVP Issues.

## Product invariants

- Drawing is pixel-aligned and deterministic.
- One committed gesture creates one history operation.
- Preview state never changes saved document truth.
- Save/load/export cannot reinterpret colors or coordinates differently from the editor core.
- Failed/rejected operations leave the last committed document valid.
- App restart, rotation, or Android lifecycle events do not create a second authoritative document state.
- The app does not require network access for the primary journey.

## Limits and performance budgets

Exact canvas, history, file-size, and memory limits are not guessed in this document. M1/M2 benchmarks must establish representative limits before the project-format v1 contract freezes them.

Every limit must be:

- expressed by a validated domain type
- visible before an operation is committed
- identical across UI, application, project format, and future automation
- covered by boundary tests
- changeable only under the compatibility policy that applies after format v1

## MVP release evidence

M3 completion requires:

- automated acceptance tests for the primary journey where feasible
- golden project and PNG fixtures
- interruption/recovery tests
- device/emulator verification record
- benchmark record with named hardware/profile and document sizes
- zero critical data-loss findings
- known limitations and migration guarantees documented

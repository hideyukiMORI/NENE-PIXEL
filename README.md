# NENE-PIXEL

NENE-PIXEL is an Android-first pixel drawing tool built in Kotlin and Jetpack Compose.
It runs on Android 8.0 (API 26) or later and keeps editing and project storage on the device.

## Status

As of **2026-10-15**, M0 through M4 have documented technical acceptance. M5 Android Beta
qualification is planned in the [beta acceptance matrix](docs/BETA_ACCEPTANCE_MATRIX.md).
The current implementation includes indexed palettes, multiple frames and layers,
selection transforms, Loop/Once preview playback, PNG import and remembered reference underlays.
The [M4 exit proof](docs/quality/M4_EXIT_PROOF.md) records its accepted scope and evidence limits;
M5 and the distributable beta are **not yet accepted**. Current work is tracked by Issues.
The worst-case performance gate remains in [#145](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145),
with collection scheduled at the owner-directed M5 exit.

[GitHub Issues](https://github.com/hideyukiMORI/NENE-PIXEL/issues) are the authority for current
work and dependencies. The [M3 exit proof](docs/quality/M3_EXIT_PROOF.md) and
[M3 MVP user guide / release notes](docs/MVP_USER_GUIDE.md) describe the earlier single-layer
release; they are historical evidence, not a complete guide to the current editor.

## Current editor

- Canvases from 1 to 256 pixels per side, pencil and transparent eraser, gesture undo/redo,
  zoom/pan and an actual-size preview window.
- A shared indexed palette with 2–256 entries, graphical colour editing and remapping, palette JSON
  import/export, and exact eyedropper selection on the active layer.
- Up to 16 chronological frames with saved duration: add blank, duplicate, delete, reorder and select.
  All frames share the canvas and palette, with at most 16 layer records across the whole work.
- Loop/Once preview from the selected frame using saved durations, synchronized with the actual-size
  window. Stop restores the editing frame; pane exit and app background stop playback without resume.
- Ordered layers within each frame: add, duplicate, delete, rename, reorder, show/hide and select
  the drawing layer. Layer visibility is saved and undoable; erasing makes cells transparent.
- Rectangular selection with Move/Copy, horizontal/vertical flip and quarter-turn rotation;
  adjustments stay a preview until one undoable Apply.
- `.nenepixel` project open/save, autosave recovery with an explicit recovery choice, and PNG
  export of the selected frame's visible layer composite.
- PNG import as a new work or an added layer, with explicit colour-conversion choices. Layer
  imports keep the original pixel scale and disclose cropping and palette limits.
- A reference underlay with placement, opacity and visibility controls. The device remembers it
  per work; it is excluded from the project file and PNG export.

Animated/sprite-sheet export, saved layer opacity and merge-down remain future work.

Projects are written as **v4**, which stores frames, durations, ordered layers and empty-pixel
coverage. V3 projects open losslessly in one default-duration frame; v2 projects use one fully
covered layer in that frame. V1 projects use the explicit legacy conversion flow. The current
format and migration rules are defined in [ADR 0041](docs/adr/0041-bounded-frames-and-project-v4.md)
and [Project Format v4](docs/PROJECT_FORMAT_V4.md). The
[v1](docs/PROJECT_FORMAT_V1.md) and [v2](docs/PROJECT_FORMAT_V2.md) specifications remain references
for those older formats.

## Build

Use the Gradle Wrapper with **JDK 21**, **Android SDK Platform 37** and **Build Tools 36.0.0**.
From the repository root on Windows:

```powershell
.\gradlew.bat :app:android:assembleDebug
```

On macOS or Linux, use `./gradlew` with the same task. The
[development setup](docs/DEVELOPMENT_SETUP.md) defines toolchain setup, IDE compatibility and
installation. During development, run the checks that cover the change; documentation changes
use `validateDocumentation`. The final PR must pass the required CI `quality` gate under
[QLT-011 and QLT-018](docs/QUALITY_GATES.md); a passing required CI result does not need a duplicate
local full run. Device collection and Profile generation follow their separate accepted protocols.

## Architecture

The governing principle is:

> Give every meaning one canonical implementation path, and reject competing paths mechanically.

Persistent edits go through `DocumentCommand`; temporary editor state goes through
`WorkspaceAction`. Mutable pixel storage stays inside the pixel-engine boundary. Kotlin types, the module
graph, static analysis, tests and CI enforce these boundaries. HTTP, OpenAPI and MCP are not part
of the current product; their introduction depends on the [API strategy](docs/API_STRATEGY.md).

## Canonical documents

Read these documents in order:

1. [Project Charter](docs/PROJECT_CHARTER.md)
2. [Architecture Constitution](docs/ARCHITECTURE_CONSTITUTION.md)
3. [Command Model](docs/COMMAND_MODEL.md)
4. [Project Layout](docs/PROJECT_LAYOUT.md)
5. [Kotlin Coding Rules](docs/CODING_RULES.md)
6. [Quality Gates](docs/QUALITY_GATES.md)
7. [Development Workflow](docs/DEVELOPMENT_WORKFLOW.md)
8. [Development Setup](docs/DEVELOPMENT_SETUP.md)
9. [Glossary](docs/GLOSSARY.md)
10. [Roadmap](docs/ROADMAP.md)
11. [Milestones](docs/MILESTONES.md)
12. [MVP Scope](docs/MVP_SCOPE.md)
13. [Development Plan](docs/DEVELOPMENT_PLAN.md)
14. [API Strategy](docs/API_STRATEGY.md)
15. [Android Beta Acceptance Matrix](docs/BETA_ACCEPTANCE_MATRIX.md)

Architecture decisions live in [`docs/adr/`](docs/adr/README.md). Temporary and narrowly scoped rule exceptions live in [`docs/waivers/`](docs/waivers/README.md).

## Delivery planning

GitHub Issues are the source of truth for current TODO state. Planning documents define order, scope, dependencies, and exit criteria; they do not duplicate live task status.

## Priority order

When goals conflict, use this order:

1. Document integrity and user data safety
2. Deterministic, correct editor behavior
3. One canonical implementation path
4. Interactive drawing performance
5. Portability of non-UI modules
6. Feature delivery speed

Performance is not permission to bypass architecture. Performance-sensitive mutation is allowed only inside the controlled pixel-engine boundary defined by `ARC-005`.

# ADR 0008: Bounded immutable tool palette and indexed workspace selection

- Status: accepted
- Date: 2026-09-04
- Issue: #41
- Affected rules: `ARC-001`, `ARC-004`, `ARC-007`, `ARC-008`, `ARC-010`, `ARC-012`,
  `CMD-002`, `CMD-005`, `CMD-009`, `CMD-010`, `CMD-012`, `KOT-001` through `KOT-008`,
  `KOT-015`, `KOT-017` through `KOT-019`, `QLT-006`, `QLT-008`

## Context

ADR 0005 fixes pixel truth as exact straight-sRGB RGBA8 values and identifies palette content as
bounded immutable tool configuration rather than `DocumentState`. The M1 implementation still
stores one free-standing active `PixelColor` in `WorkspaceState`, however, and the UI can only show
that fixed color. It has no palette collection, upper bound, typed indexed lookup, or user selection
path.

Keeping both an active color and an active palette index would make the same selection fact
authoritative twice. Keeping selection in Compose would also allow gesture capture and rendered
selection to disagree with the application reducer. Palette-entry editing, persistence, imported
palettes, and palette-indexed pixels are outside P2-04.

## Decision

### Ownership and value model

`Palette` is one immutable `:core:domain` tool-configuration value. It owns an ordered defensive
copy of between 1 and 32 exact `PixelColor` values. The maximum is a conservative interaction bound,
not a pixel-storage or performance claim. Construction rejects empty and oversized inputs before
copying them. `PaletteIndex` remains the only index type, and indexed lookup returns a closed typed
result for an index outside the palette.

Entries are derived immutable `PaletteEntry` projections. An entry index is its zero-based position;
callers cannot provide conflicting entry metadata. Duplicate RGBA values are permitted because
entry identity is position and future named or grouped tool palettes may intentionally repeat a
color. Pixels continue to own RGBA values and never retain `PaletteIndex`.

The Android composition root supplies one fixed eight-entry MVP palette in this order: red, black,
white, green, blue, yellow, cyan, and magenta. All are opaque straight-sRGB RGBA8 values. The first
entry is the deterministic initial selection. This product configuration may be replaced in a
future focused Issue without changing document truth; editing it at runtime remains out of scope.

### Selection and gesture path

`WorkspaceState` owns only `activePaletteIndex`, not a second active `PixelColor` or palette copy.
It starts at the palette's guaranteed first index. `WorkspaceAction.SelectPaletteEntry` and
`WorkspaceReducer` are the only mutation route. Selecting an index outside the configured palette
is a typed rejection; selecting the current index is a typed unchanged result. Either outcome
preserves the exact state instance.

The palette is supplied to `WorkspaceReducer` and retained by `EditorRuntime` as immutable
configuration. The reducer resolves the selected entry only when a Pencil `ToolGesture` begins and
captures its exact `PixelColor` in `StrokeEffect.Paint`. A later selection does not recolor that
gesture's preview or committed stroke. Eraser behavior is independent of selection.

Palette selection emits no `DocumentCommand` and changes no `DocumentState`, revision, history, or
dirty state. Creating a new document reconstructs workspace state and therefore restores the first
palette entry, matching the existing deterministic new-runtime reset policy.

### Presentation projection

`EditorRenderState` projects the immutable palette plus `activePaletteIndex`; its displayed
`activeColor` is derived from those values. Compose retains only the normal immutable render-state
projection and does not own another mutable color or index. Every palette control emits
`SelectPaletteEntry`, exposes a stable RGBA accessibility description and selected semantics, and
renders the exact channels through the existing `PixelColor`-to-Compose adapter.

## Rejected alternatives

### Store both active index and active color

Rejected because selection could become internally inconsistent and gesture capture would need an
arbitrary authority rule.

### Put palette content in DocumentState

Rejected because ADR 0005 makes pixels exact RGBA values and P2-04 introduces no palette editing,
save/load, or document-visible palette semantics. It would also create undo and schema obligations
with no current product behavior.

### Let Compose own the selected swatch

Rejected because gesture capture is application behavior and all `WorkspaceState` changes must
enter through the reducer.

### Accept arbitrary or unbounded palette lists

Rejected because the requested MVP control is a bounded tool configuration, and an unbounded
projection would leave both resource and interaction behavior unspecified.

## Consequences

### Benefits

- palette content and active selection have distinct single owners
- invalid and repeated selection outcomes are exhaustive and testable
- exact RGBA values survive selection, gesture capture, rasterization, snapshot, and rendering
- the palette adds no document mutation, history, dirty-state, storage, or schema path

### Costs and risks

- runtime and presentation construction now require the palette configuration instead of one color
- the fixed eight-color MVP palette is intentionally small and has no editing or import workflow
- transparent palette entries are supported by the value path but the default configuration is
  opaque; richer transparency visualization belongs to a future presentation Issue

## Enforcement impact

- domain tests cover empty, 1, 32, and 33-entry construction, immutable input copying, typed lookup,
  and exact RGBA values
- reducer and runtime tests cover valid, unchanged, invalid, gesture-captured, and new-document reset
  behavior without document/history/dirty changes
- presentation tests cover callback routing, selected/unselected accessibility semantics, drawing,
  and exact Compose channel conversion
- `COMMAND_MODEL.md`, `PROJECT_LAYOUT.md`, and `GLOSSARY.md` record the ownership and vocabulary
- no dependency, Gradle plugin, module, serialization format, external API, quality-gate, or waiver is
  introduced

## Migration and rollback

The focused migration replaces `WorkspaceState.activeColor` and `ChangeActiveColor` atomically with
the palette-index path. All runtime, presentation, composition, and test callers migrate in the same
change; no compatibility constructor or alternate free-color path remains.

Rollback reverts the complete unmerged change to the fixed active-color implementation. No project
data requires migration because palette configuration and selection are not persisted.

## Related

- Issue: #41
- Builds on: ADR 0005 exact RGBA semantics and palette ownership
- Builds on: ADR 0007 gesture effect capture
- Supersedes: none
- Superseded by: none

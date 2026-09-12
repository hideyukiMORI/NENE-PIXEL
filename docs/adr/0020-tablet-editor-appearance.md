# ADR 0020: Workspace-owned tablet editor appearance

- Status: accepted
- Date: 2026-09-12
- Issue: #99
- Affected rules: `ARC-001`, `ARC-004`, `ARC-007`, `ARC-012`, `CMD-002`, `CMD-009`,
  `KOT-001`, `KOT-002`, `KOT-008`, `KOT-017`, `KOT-018`, `KOT-019`, `QLT-006`, `QLT-011` through `QLT-016`

## Context

hide accepted the tablet prototype and requested native implementation on 2026-09-12. The current
600x400dp layout test records about 22dp of canvas height after vertically stacked controls.
The approved design moves frequent operations to the bottom or a selectable physical side and uses
dark and light aubergine themes. Theme and layout must not acquire independent Compose state or
modify artwork, history, dirty state, persistence captures, or the canonical viewport transform.

## Decision

The public application vocabulary adds immutable `EditorAppearance` with three closed enums:
`EditorTheme` (Dark, Light), `EditorLayout` (Tabletop, Handheld), and `EditorControlEdge` (Left, Right).
`WorkspaceState.appearance` is its only owner. `WorkspaceAction.SetAppearance` is its sole mutation
route. Equal appearance without a preview returns `AppearanceAlreadySet`; any active preview is
cancelled atomically before publishing appearance, even for the same appearance. An appearance
change creates no document command, history entry, checkpoint, or autosave capture.

The initial appearance is Dark, Tabletop, Right. The existing activity-scoped runtime retains this
state through configuration recreation. Runtime installation preserves the latest appearance
through the workspace reducer while resetting document-specific selection, tool, preview, and
viewport as before. New/load/recovery do not reset how the editor is held. A fresh process begins
with the initial appearance; durable app preferences require a later focused decision, not project
schema fields or hidden I/O in Compose.

`EditorRenderState` exposes this immutable value and `EditorCallbacks` translates an appearance
selection to the action. Compose remembers only local drawer/dialog visibility and scroll/focus
mechanics. Drawers use the existing palette selection and file-operation callbacks. Opening a modal
panel cancels the current drawing preview through the existing cancellation callback.
Submitting a file action dismisses only its own panel. New-document input stays visible on typed
validation rejection and dismisses on submission; asynchronous discard confirmation is rendered at
the shell root. Completion outcomes never close an unrelated panel. File status is also available
inside the file panel from the same immutable projection. The shell applies safe drawing insets
inside its themed background, and the Android composition root sets system-bar icon contrast from
the selected theme. Fixed-size layout tests describe available safe content, not system-bar space.

Tabletop places frequent tools, history and palette access along the bottom. Handheld places the
same controls on the physical Left or Right edge, independent of text reading direction. File and
appearance settings are reachable from a compact header. One modal panel implementation hosts
scrollable palette, file and appearance content; it follows the chosen physical edge, fits within
the current window, and dismisses with Back or outside activation. Modal file confirmation remains
owned by the existing typed persistence workflow. The canvas fills the remaining allocated surface;
ADR 0004 alone calculates fit, letterboxing, grid, forward/inverse mapping and resize normalization.
Semantic descriptions for drawing tools, history, canvas, dirty/recovery state and palette slots
remain stable. Controls have at least 48dp touch targets.

The approved color values are semantic presentation tokens. Dark chrome is `#140C13`, rail
`#1C101A`, shared panels `#11090F`, inset `#0E080D`, primary fill `#5E2750`, and deep fill `#2C001E`.
Light chrome is `#F5F1F4`, rail `#FCFAFC`, panels white and inset `#EEE7EC`, using the same primary
aubergine fills. Text is light on dark panels and dark on light panels; selected swatches also show
a shape marker. Material surface tint must not silently lighten shared panel backgrounds.
Existing white artwork backing, RGBA compositing, grid and pixel algorithms remain unchanged in
this shell slice. Neutral surrounding margins are presentation only. A later transparency-display
change must key derived bitmap caches by every changed rendering input.

## Rejected alternatives

### Compose-owned theme and layout selections

Rejected because configuration recreation, modal transitions and document replacement could create
inconsistent editor behavior. Only panel visibility/scroll/focus are local presentation mechanics.

### A second canvas transform per layout or a fixed canvas aspect-ratio wrapper

Rejected because it duplicates geometry and restricts drawing to a small region. The allocated
surface is an input to the existing canonical fit transform, not a second document-coordinate owner.

### Include palette editing and indexed pixels in this shell change

Rejected because those change document/history/schema contracts. The approved recoloring requirement
needs its own representation and migration decision; this Issue must not partially implement it.

## Consequences

The same controls can move without changing domain behavior. Appearance survives runtime document
replacement but is session-only. Device tests must cover geometry and actual callbacks; HTML results
do not prove Android behavior. Existing tests that assumed all controls were permanently above the
canvas migrate to the new shell without losing their functional assertions. Historical performance
evidence remains historical; no speedup or current measurement acceptance is claimed.

## Enforcement impact

Host tests cover action no-op/cancellation, ownership and document-install preservation. Controller
contracts cover render publication. Device tests cover themes, edges, layout, palette/drawer/file
access, drawing/history and configuration recreation. Required CI and all existing gates remain.
No dependency, module, schema, pixel representation, benchmark protocol or waiver is added.

## Migration and rollback

Replace the top-stacked shell and its fixed theme in one change. Migrate all render-state consumers,
workspace copies and geometry assumptions; keep one palette and input path. Saved v1 projects and
recovery records are byte-identical. Rollback reverts this complete focused change, with no data
migration or persistent preference cleanup.

## Related

- Issue: #99 / M3 P3-07
- Refines: ADR 0004, ADR 0006, ADR 0008, ADR 0014
- Supersedes: none
- Superseded by: none

# ADR 0007: Bounded pencil and eraser gesture semantics

- Status: accepted
- Date: 2026-09-04
- Issue: #40
- Affected rules: `ARC-001`, `ARC-004`, `ARC-005`, `ARC-007`, `ARC-008`,
  `ARC-010` through `ARC-012`, `CMD-001` through `CMD-008`, `CMD-010`, `CMD-012`,
  `KOT-002`, `KOT-003`, `KOT-007`, `KOT-008`, `KOT-016` through `KOT-019`,
  `QLT-006` through `QLT-008`

## Context

The M1 drawing slice records only the pixel positions delivered by pointer events and gives every
`Stroke` one replacement color. It therefore leaves gaps when pointer sampling skips document
pixels, cannot distinguish pencil intent from eraser intent, and has no active-tool state. Its
`ToolGesture` also appends to a Kotlin `List` by copying the complete path for every move, giving a
long gesture quadratic aggregate growth.

ADR 0005 has since accepted exact pencil replacement, canonical transparent-black erasing, and a
maximum raw stroke volume of 262,144 positions. ADR 0004 has accepted the pointer arbitration that
cancels drawing when an additional pointer begins a viewport interaction. P2-03 must complete the
remaining document-pixel semantics without adding another command, rasterizer, history route, or
presentation-owned tool authority.

## Decision

### Tool identity and committed effect

`:core:domain` owns two related closed values with different meanings:

- `DrawingTool` is the workspace-selectable identity `Pencil` or `Eraser`.
- `StrokeEffect` is the gesture-captured document effect: `Paint(PixelColor)` or `Erase`.

`WorkspaceState` owns exactly one `activeTool`, initially `DrawingTool.Pencil`. It changes only
through `WorkspaceAction.SelectTool` and `WorkspaceReducer`. Active color remains a separate
workspace fact because selecting Eraser must not discard the color used when Pencil is selected
again.

Beginning a `ToolGesture` converts the current active tool and active color to one `StrokeEffect`.
The gesture and resulting `Stroke` retain that effect. Later tool or color selection changes affect
only later gestures. `StrokeEffect.Paint` replaces every effective target with its captured exact
straight-sRGB `PixelColor`; `StrokeEffect.Erase` replaces it with `PixelColor.blank`. The pixel
engine derives the target color exhaustively from that effect and uses the existing
`rasterizeStroke` and `PixelPatch` path for both tools.

Painting an already equal value and erasing an already blank value both produce the existing one
canonical `NoEffectiveChange` rejection. Neither advances revision, creates history, nor marks the
document dirty.

### Canonical interpolation and bound

`ToolGesture` is the only interpolation owner. It connects each pair of accepted sampled document
pixels with integer, endpoint-inclusive, 8-connected line traversal. The first gesture sample is
emitted once; every later segment excludes its previous endpoint and includes its new endpoint.
Endpoints are first ordered by document row then column. Each axis at step `i` of `n` advances by
the nearest integer to `delta * i / n`, with exact halves rounded away from zero. Reverse input
enumerates those same canonical positions in reverse. This fixes tie behavior and direction
symmetry for steep, shallow, horizontal, vertical, and diagonal segments without floating point.

The exact expanded position count is:

```text
1 + sum(max(abs(nextX - priorX), abs(nextY - priorY)))
```

The next count is calculated as `Long` and compared with
`PixelLimits.MAX_RAW_STROKE_POSITIONS` before accepting a sample or allocating an expanded path.
An over-limit extension returns a typed workspace rejection and preserves the existing gesture.
Consecutive duplicate samples use one typed unchanged result. Revisited and crossing samples
remain in deterministic gesture order; the existing bounded pixel-engine collector performs one
linear first-occurrence pass and emits the canonical row-major effective patch.

The active `ToolGesture` stores sampled endpoints as an immutable reverse-linked chain. Extending
it is constant work and creates one bounded node rather than copying the accumulated path. Preview
and commit enumerate the same interpolation implementation. Commit materializes the expanded path
once into the existing packed immutable `Stroke`; no mutable gesture buffer becomes application or
presentation state.

### Presentation and cancellation

Compose renders `activeTool` and emits only `SelectTool`. It holds no second mutable tool selection.
Pencil preview uses the gesture-captured paint color; Eraser has a presentation-only visible
preview treatment and does not invent a document color.

Existing ADR 0004 arbitration remains authoritative. Leaving the canvas, explicit cancellation,
surface invalidation, a second pointer, or additional-pointer suppression clears the preview
before any command commit. A completed one-pointer gesture emits exactly one
`ApplyStrokeCommand`; a no-op is still that command's typed outcome and creates no history entry.

## Rejected alternatives

### Add an erase command, handler, or rasterizer

Rejected because pencil and eraser differ only in their captured replacement effect. A second
route would duplicate validation, patch ordering, history, no-op, and inverse behavior.

### Store only a target color on Stroke

Rejected because a pencil using transparent black and an eraser would become the same domain
intent. The closed effect retains the semantic operation while still deriving one raster target.

### Interpolate in Compose or the pointer adapter

Rejected because direct application/controller tests and future adapters could produce different
strokes from the same samples. Surface coordinates and frame timing are adapter inputs; integer
document-pixel interpolation is application behavior.

### Append immutable Kotlin lists on every move

Rejected because copying the complete accumulated path per pointer event has quadratic aggregate
cost. The accepted chain keeps public state immutable while making extension constant work.

### Keep drawing after an additional pointer is released

Rejected because it would commit an operation whose input crossed a viewport transform. ADR 0004
requires all pointers to lift before a fresh drawing gesture begins.

## Consequences

### Benefits

- sampled gaps produce one deterministic connected pixel path;
- pencil and eraser share command, patch, history, invalidation, and no-op behavior;
- tool and color changes cannot alter an in-flight preview or commit;
- gesture extension is bounded and avoids accumulated-path copying;
- erase preserves ADR 0005 exact blank and inverse semantics.

### Costs and risks

- `Stroke` changes from a color property to a closed effect property;
- commit performs one bounded materialization from interpolated positions into packed Stroke
  storage;
- preview enumeration is linear in the expanded path and may be revisited by renderer work if
  P2-07 frame evidence identifies it as material;
- P2-06 is still responsible for replacing one-level history and completing clean-checkpoint
  dirty semantics.

## Enforcement impact

- `COMMAND_MODEL.md`, `PROJECT_LAYOUT.md`, and `GLOSSARY.md` record tool/effect ownership and the
  interpolation contract.
- domain tests cover closed effect values and Stroke ownership.
- workspace tests cover selection ownership, captured effect, interpolation boundaries,
  deterministic revisits, and maximum-volume rejection.
- pixel-engine and command tests cover pencil, eraser, no-op, inverse, atomicity, and replay through
  the same path.
- controller and Compose tests cover controls, preview capture, cancellation, and emulator smoke.
- no dependency, Gradle plugin, module, serialization schema, quality-gate, suppression, baseline,
  or waiver is introduced.

## Migration and rollback

All Stroke construction and consumers migrate from the color property to `StrokeEffect` in this
focused change. `WorkspaceState`, render projections, controller callbacks, and Compose migrate to
the one active-tool owner together; no fixed-pencil compatibility overload or erase-specific route
remains.

There is no persisted schema or external API to migrate. Rollback reverts this focused change as a
whole and must not retain both sampled-only and interpolated paths or both color-only and effect
Stroke contracts.

## Related

- Issue: #40
- Uses: ADR 0004 pointer arbitration
- Uses: ADR 0005 color, blank, storage, and bounds
- Uses: ADR 0006 runtime ownership
- Refines: ADR 0002 Stroke and WorkspaceState public contracts
- Supersedes: none
- Superseded by: none

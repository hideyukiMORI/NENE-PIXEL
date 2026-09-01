# ADR 0004: Bounded workspace viewport and canonical mapping

- Status: accepted
- Date: 2026-09-01
- Issue: #42
- Affected rules: `ARC-001`, `ARC-003`, `ARC-004`, `ARC-007`, `ARC-008`, `ARC-010`, `ARC-012`, `CMD-002`, `CMD-009`, `CMD-010`, `CMD-012`, `KOT-001` through `KOT-008`, `KOT-014`, `KOT-015`, `KOT-017` through `KOT-019`

## Context

ADR 0002 intentionally limited M1 to `FixedSliceViewport` and a fixed full-surface touch translator. That path proves touch-to-command integration but stretches rectangular canvases, duplicates rendering and inverse-input equations, has no bounded pan or zoom, and treats a second pointer only as cancellation. M2 requires one portable viewport contract before richer drawing gestures depend on pointer arbitration.

The current Document representation and product canvas limits remain under P2-01 review. Viewport behavior can proceed independently because it changes only ephemeral workspace state and consumes the already validated `CanvasSize`. It must not imply a new pixel-storage or product-limit decision.

The accepted contract needs to survive Android density and surface resizing without storing platform values in core state. It also needs to keep a document point under a pinch focal point, make rectangular pixels visually square, and give drawing and rendering the same half-open mapping.

The policy constants are grounded in the bounded M2 interaction problem rather than a storage or performance claim:

- zoom `1.0` is the only lower bound that preserves fit and prevents exposing space beyond the canvas on both axes
- zoom `64.0` permits six exact binary doubling steps from fit, reduces the visible limiting-axis extent of the fixed 16 x 16 M1 slice to one quarter of one document pixel, and bounds accidental pinch growth; P2-07 may propose a different product range only through a later ADR with device evidence
- the `1dp` full-separation gate prevents an arbitrarily large ratio when separation is no more than one density-independent logical unit
- an `8dp` grid threshold bounds a one-physical-pixel grid line to at most one eighth of a cell at mdpi; the existing M1 100-pixel-cell fixture remains visibly gridded, while the 32 x 32 / 400px / 2px-per-dp fixture is hidden at fit (`6.25dp`) and visible at 2x (`12.5dp`)

These are behavior-policy fixtures, not accepted canvas, latency, memory, or accessibility limits. Focused boundary tests and the Issue #42 emulator smoke are required evidence for this ADR to merge.

## Decision

ADR 0004 refines, and does not supersede, ADR 0002. The M1 fixed viewport and translator are removed when this decision is implemented.

### State ownership and public values

`WorkspaceState` owns exactly one `ViewportState`. A viewport state contains:

- `ViewportZoom`: a finite fit-relative factor in the closed range `1.0..64.0`
- `ViewportCenter`: a finite preferred point in continuous document-edge coordinates

`ViewportState.initial(CanvasSize)` uses zoom `1.0` and center `(width / 2, height / 2)`. Density change alone retains the document-coordinate center. The effective center is contextually clamped by the transform for the current surface; resize does not copy surface data into workspace state.

`ViewportCenter` deliberately has no canvas-containment invariant because `WorkspaceReducer` does not own or duplicate the current `CanvasSize`. `ViewportTransform.create` is the single boundary that resolves the preferred center against its explicit current canvas and exposes an effective, canvas-contained `ViewportState`. When the validated surface changes, presentation dispatches that effective state through `SetViewport` before accepting another pointer delta. This normalization retains zoom and applies only the minimum required center clamp; it prevents a later identity gesture from silently changing state.

`ViewportSurface` contains positive integer pixel width and height plus finite positive pixels-per-dp. `ViewportSurfacePoint` contains finite pixel coordinates. `ViewportGesture` contains the previous and current positions of exactly two pointers. Raw numbers enter through named factories and return `ViewportValueResult.Created` or `ViewportValueResult.Rejected` with a closed `ViewportValueRejection`. Negative zero is canonicalized to positive zero. Compose `Offset`, `Size`, `Density`, matrices, and Android types never enter a core API.

The public application vocabulary is limited to:

- `ViewportZoom`
- `ViewportCenter`
- `ViewportState`
- `ViewportSurface`
- `ViewportSurfacePoint`
- `ViewportGesture`
- `ViewportTransform`
- `ViewportSurfaceBounds`
- `ViewportGridVisibility`
- `ViewportMappingResult<T>`
- `ViewportValueResult<T>` and `ViewportValueRejection`

Its canonical signatures are:

```kotlin
ViewportZoom.create(value: Double): ViewportValueResult<ViewportZoom>
ViewportCenter.create(x: Double, y: Double): ViewportValueResult<ViewportCenter>
ViewportState.initial(canvas: CanvasSize): ViewportState
ViewportState.create(
    zoom: ViewportZoom,
    center: ViewportCenter,
): ViewportState
ViewportSurface.create(
    widthPixels: Int,
    heightPixels: Int,
    pixelsPerDp: Double,
): ViewportValueResult<ViewportSurface>
ViewportSurfacePoint.create(
    xPixels: Double,
    yPixels: Double,
): ViewportValueResult<ViewportSurfacePoint>
ViewportGesture.create(
    previousFirst: ViewportSurfacePoint,
    previousSecond: ViewportSurfacePoint,
    currentFirst: ViewportSurfacePoint,
    currentSecond: ViewportSurfacePoint,
): ViewportGesture
ViewportTransform.create(
    canvas: CanvasSize,
    surface: ViewportSurface,
    viewport: ViewportState,
): ViewportValueResult<ViewportTransform>
ViewportTransform.toSurfaceBounds(
    position: PixelPosition,
): ViewportMappingResult<ViewportSurfaceBounds>
ViewportTransform.toPixelPosition(
    point: ViewportSurfacePoint,
): ViewportMappingResult<PixelPosition>
ViewportTransform.apply(
    gesture: ViewportGesture,
): ViewportValueResult<ViewportState>
```

`ViewportSurfaceBounds` is the four finite physical-pixel edges `left`, `top`, `right`, and `bottom` of one document pixel; bounds may extend beyond the visible surface at zoom. `ViewportMappingResult` is `Mapped(value)`, `OutsideSurface`, or `OutsideCanvas`. `ViewportGridVisibility` is `Visible` or `Hidden`. Transform creation or application rejects unsafe non-finite derived arithmetic. Creation also rejects a transform whose canonical adjacent document edges are not strictly increasing in `Double`, because such a transform cannot preserve half-open ownership or pixel-center round trips before P2-01 establishes tighter canvas limits. Callers preserve the current viewport on rejection.

Surface dimensions, density, derived transform coefficients, pointer membership, and grid visibility are not stored in `WorkspaceState`.

### Coordinate spaces and canonical transform

Document space is continuous pixel-edge space `[0, canvasWidth) x [0, canvasHeight)`. Document pixel `(x, y)` occupies `[x, x + 1) x [y, y + 1)` and its center is `(x + 0.5, y + 0.5)`. Surface space is physical pixel space `[0, surfaceWidth) x [0, surfaceHeight)`.

For surface size `(Sw, Sh)`, canvas size `(W, H)`, and zoom `z`, every division below first widens its integer operands and uses real-number `Double` arithmetic:

```text
fit = min(Sw / W, Sh / H)
cellScale = fit * z
halfVisibleX = Sw / (2 * cellScale)
halfVisibleY = Sh / (2 * cellScale)
```

The effective center on each axis is the canvas midpoint when the visible extent covers that axis. Otherwise the preferred stored center is clamped so the surface cannot move beyond the canvas edge:

```text
effectiveCenterX =
    if 2 * halfVisibleX >= W then W / 2
    else clamp(centerX, halfVisibleX, W - halfVisibleX)
```

Y follows the same rule. The surface origin of document coordinate zero is:

```text
originX = Sw / 2 - effectiveCenterX * cellScale
originY = Sh / 2 - effectiveCenterY * cellScale
```

This one uniform `cellScale` gives square rendered pixels and centered letterboxing at fit. `ViewportTransform` owns these immutable derived coefficients and is the only implementation used for both forward rendering bounds and inverse pointer mapping.

Forward bounds for a contained `PixelPosition(x, y)` are:

```text
[originX + x * cellScale, originX + (x + 1) * cellScale)
x [originY + y * cellScale, originY + (y + 1) * cellScale)
```

Inverse mapping first applies the surface half-open bound, then computes `(surface - origin) / cellScale` and applies the document half-open bound. A `floor` quotient supplies an initial candidate, but binary floating-point may place a canonical forward edge immediately below its integer quotient. The implementation therefore compares the input with the same canonical forward edge function and adjusts to the greatest pixel index whose left/top edge is less than or equal to the input. A generic epsilon or unconditional `nextUp` bias is prohibited because it would move a value immediately before an edge into the next pixel.

An exact canonical internal edge belongs to the pixel on its right or bottom; the immediately preceding representable value belongs to the pixel on its left or top. Exact surface right/bottom is `OutsideSurface`. The representable value immediately before a surface edge is only the last surface coordinate and need not be in the final document pixel when zoomed. Exact projected document right/bottom within the surface is `OutsideCanvas`, while its immediately preceding representable value belongs to the final document pixel. `ViewportMappingResult` preserves these distinctions without null or Boolean results.

### Bounded two-pointer transform

`ViewportGesture` supplies two validated pointer pairs so presentation does not calculate scale or document coordinates. `ViewportTransform.apply`:

1. computes the previous and current centroids and full inter-pointer Euclidean distances
2. treats a previous full separation of at most `pixelsPerDp` (`1dp`) as pan-only to avoid an unstable ratio
3. computes `ratio = 1` for pan-only, otherwise `currentDistance / previousDistance`, then clamps `oldZoom * ratio` to zoom `1.0..64.0`
4. resolves the document point under the previous centroid
5. selects the next center so that document point remains under the current centroid
6. applies the contextual center clamp once

For previous effective center `C0`, previous scale `s0`, new scale `s1`, surface midpoint `M`, and centroids `b0` and `b1`:

```text
anchor = C0 + (b0 - M) / s0
rawNextCenter = anchor - (b1 - M) / s1
```

Creation and application reject non-finite derived values through the same closed viewport result vocabulary. A surface, all pointer coordinates, and pixels-per-dp scaled by the same factor produce the same viewport state and mapped document pixel.

The full scale-invariance contract requires surface dimensions, all pointer coordinates, and pixels-per-dp to be scaled by the same positive factor while preserving integer surface dimensions. Density alone leaves forward/inverse mapping unchanged but may change grid visibility and the `1dp` gesture classification. Surface size, density, or pointer membership changing during an active two-pointer interaction rebases the pointer baseline and emits no transform; the next pair from one unchanged `ViewportSurface` may produce a gesture.

### Grid policy

Grid visibility is derived and has no stored flag or preference. It is `Visible` when `cellScale / pixelsPerDp >= 8.0dp` and `Hidden` below that threshold. Equality is visible. Rendering consumes `ViewportGridVisibility` from the same `ViewportTransform` used for pixels and input.

### Workspace reduction and pointer arbitration

`WorkspaceAction.SetViewport(ViewportState)` is the sole viewport mutation route. The reducer atomically installs the viewport and removes an active gesture preview:

- a different viewport produces `Reduced`
- the same viewport with an active preview removes the preview and produces `Reduced`
- the same viewport without a preview produces `Unchanged(ViewportAlreadySet)`

It never calls `CommandGateway`, changes `DocumentState`, advances `Revision`, or creates history.

Presentation owns only pointer-ID tracking and the local gesture phase:

- the first pointer may begin drawing
- the first pointer enters drawing only after a typed acknowledgement that `BeginGesturePreview` produced `Reduced`
- arrival of a second pointer dispatches the current viewport once and enters transform only after a typed acknowledgement that preview cancellation/state reduction completed
- exactly two pointers may pan or pinch; a pointer-set change rebases previous positions and emits no delta
- a surface size or density change also normalizes the effective viewport, rebases pointer positions, and emits no gesture delta
- a third pointer suppresses the interaction until all pointers are up
- after any transform or suppression, drawing does not resume until all pointers are up and a fresh first pointer arrives

This phase is adapter-local input mechanics, not a second viewport or preview authority. An internal closed pointer result acknowledges accepted, ignored, cancelled, and rejected reductions so the local phase cannot advance against workspace state. Cancellation is enforced through a `finally`-equivalent path. The controller constructs each mapping or transform from the current `WorkspaceState.viewport` and current Document `CanvasSize`, so recomposition cannot leave pointer input using a stale matrix.

### Migration boundary

The implementation removes:

- `FixedSliceViewport`
- `FixedCanvasTouchTranslator`
- `TouchTranslation`
- their production and test-only mapping paths

The fixed M1 editor composition may retain its bootstrap Document until P2-02, but rendering, pointer input, controller integration, and interaction measurement migrate to the canonical viewport transform. The historical M1 result remains reproducible from its immutable merge commit; a current-main measurement uses an explicitly P2 viewport boundary.

## Rejected alternatives

### Presentation-owned transform or Compose matrix

Rejected because rendering and input could diverge, platform types would become behavioral authority, and tests would require the Compose runtime for portable geometry.

### Store surface pixels, density, or pixel pan in WorkspaceState

Rejected because surface and density are adapter inputs whose lifecycle differs from workspace state. Pixel pan changes meaning on resize and density change.

### Store normalized pan instead of document center

Rejected because normalized overflow preserves a relative scrollbar position rather than the document coordinate under the user's focal point. Document center has stable meaning across density and surface changes and makes focal preservation direct.

### Separate zoom and pan actions

Rejected because a pinch would expose ordering-dependent intermediate states and duplicate focal-point/clamp arithmetic. One derived next `ViewportState` is reduced atomically.

### Store grid visibility or a grid preference

Rejected because M2 has one effective threshold policy. A stored value would duplicate a fact already determined by scale and density.

### Keep the fixed translator as an alias or test fixture

Rejected because that preserves a second mapping path and lets tests pass against behavior production no longer uses.

## Consequences

### Benefits

- rectangular canvases retain square pixels with deterministic letterboxing
- render and pointer paths share one portable transform and one rounding rule
- viewport operations remain ephemeral and undo-neutral
- the preferred document center is retained until a surface normalization applies only the required contextual clamp
- multi-touch cancellation occurs in the same workspace transition as viewport ownership

### Costs and risks

- the public application vocabulary gains several small validated geometry values
- double-precision boundaries require explicit next-representable-value tests
- presentation needs a pointer-ID state machine and must rebase when membership changes
- the current center can be contextually clamped differently on each surface; tests must distinguish stored and effective center
- Compose gesture integration still requires emulator/device evidence in addition to pure core tests

## Enforcement impact

- `COMMAND_MODEL.md` records atomic viewport/preview reduction and the document-neutral transform rule.
- `PROJECT_LAYOUT.md` assigns portable viewport geometry to application and only raw-event translation to presentation.
- `GLOSSARY.md` adds the canonical viewport terms.
- P2-03 depends on P2-05 before adding further drawing pointer semantics.
- `M1_EXIT_PROOF.md` pins historical M1 interaction reproduction to merge commit `37c0f57`; current main uses a separately named P2 viewport metric boundary.
- Focused value, transform, reducer, controller, Compose, and emulator tests replace the fixed translator evidence.
- No dependency, Gradle plugin, module, schema, quality-gate, or waiver change is introduced.

## Migration and rollback

Implementation migrates all consumers and removes the fixed mapping files in Issue #42. No saved data exists for viewport state and no project schema changes. Rollback reverts the focused branch as a whole; it must not keep both mapping paths or a compatibility alias.

## Related

- Issue: #42
- Refines: ADR 0002
- Supersedes: none
- Superseded by: none

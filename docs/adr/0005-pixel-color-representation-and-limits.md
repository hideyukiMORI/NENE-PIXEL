# ADR 0005: Pixel and color representation with bounded document policy

- Status: accepted
- Date: 2026-09-03
- Issue: #38
- Affected rules: `ARC-001` through `ARC-005`, `ARC-007` through `ARC-012`, `CMD-005` through `CMD-010`, `KOT-001` through `KOT-003`, `KOT-005`, `KOT-007`, `KOT-008`, `KOT-013`, `KOT-016`, `KOT-020`, `QLT-006` through `QLT-010`

## Context

ADR 0002 deliberately left production pixel storage, semantic color compatibility, canvas size,
stroke and patch volume, and future history limits unfrozen until M1 measurements and P2-01.
The immutable M1 baseline proves the current sparse host path and exposes area-sized snapshot
costs, but it is not a product-limit study. It excludes dense and tool-specific workloads,
retained heap, Android ART and PSS, frame timing, and a named physical minimum device.

The current implementation also contains costs whose bounds are not expressed by a validated
product policy:

- `CanvasSize.pixelCount` is `Long`, while snapshot, surface, composition, and rendering paths
  convert it to `Int` for area-sized allocation.
- `PixelSnapshot` owns a defensive `List<PixelColor>` copy and `PixelSurface` owns a mutable
  `MutableList<PixelColor>` copy.
- stroke samples are unbounded independently of the number of unique changed pixels.
- patch construction materializes changes and sorts them before any product change-count limit.
- `ChangeSet` retains a materialized forward patch and a second materialized inverse patch.
- presentation materializes one `RenderedPixel` and issues one rectangle per canvas pixel,
  including pixels outside the visible viewport.
- `PixelColor` names four unsigned 8-bit channels, but the color space, alpha association,
  alpha-zero treatment, canonical blank, eraser target, and pencil composition semantics are
  not accepted contracts.

These facts do not by themselves identify a safe representation or a numerical maximum. The
starting measurements and the evidence still required for a decision are recorded in
[P2 Representation and Limit Evidence](../quality/P2_REPRESENTATION_LIMIT_EVIDENCE.md).

There are no active waivers and no persisted NENE-PIXEL project schema or external API to
migrate. Future PNG and project-format compatibility still constrains semantic choices because
save, load, and export must not reinterpret pixel colors.

## Decision

### Evidence determination

The linked evidence document supplies all of the following:

1. the immutable M1 route has been reproduced from commit `37c0f57` in an isolated worktree;
2. one exact semantic color contract has passed the correctness lane described below;
3. the current representation, flat packed RGBA8888, and one preselected best tiled or
   copy-on-write challenger have been compared under the same correctness contract;
4. separately collected physical latency/ART-tail and retained-memory/PSS evidence supports the
   selected representation and conservative MVP supported caps;
5. raw artifacts, their exact metric boundaries, profile metadata, and SHA-256 checksums are
   recorded without overwriting an earlier accepted or diagnostic artifact; and
6. exactly one semantic, storage, and logical-limit policy passes the applicable pre-fixed lane
   conditions without interpolation.

Host JVM and emulator observations remain auxiliary. The physical flat-packed kernel passes every
fixed latency tail, T16 fails dense apply, and the current object path fails the clean command
lane. At `N=65,536`, `H=64`, and `T=524,288`, flat packed with shared inverse and canonical bitmap
uses 9,541,424 bytes retained Java heap and a 7,570 KiB median paired PSS delta, respectively
80.67% and 80.17% below the current comparison. This ADR selects conservative MVP caps at or below
those explicitly measured points. It does not claim the largest possible or theoretical maximum.

### Semantic color contract

| Question | Accepted answer |
| --- | --- |
| Channel model | Four unsigned 8-bit channels in red, green, blue, alpha semantic order in the sRGB color space |
| Alpha | Straight, unassociated semantic alpha; core values are never premultiplied |
| Alpha zero | Hidden RGB is preserved and participates in equality, hash, save/load, and export |
| Blank | Transparent black, RGBA `(0,0,0,0)` |
| Eraser | Replace every effective target with canonical blank; blank-to-blank is a no-op |
| Pencil | Replace every effective target with the gesture-captured selected `PixelColor`; no blending or compositing occurs in document truth |
| Equality and hash | All four semantic channels participate, independent of storage layout |
| Palette | Each pixel owns its RGBA value, never a palette identity or index |
| Compatibility | Compose receives the same sRGB channels and may premultiply only inside disposable rendering; PNG and project formats encode/decode straight RGBA from `PixelSnapshot`, never from a rendered bitmap |

Palette content is a bounded immutable tool configuration, not `DocumentState` or pixel truth.
Only active palette selection is workspace state. Palette selection therefore changes no document,
revision, history, or dirty state. Palette-entry editing is not introduced by this ADR.

### Canonical storage decision

The decision comparison contains exactly the following three storage candidates:

| Candidate | Decision |
| --- | --- |
| Current flat object representation | Rejected: clean physical command tails fail and retained memory is materially higher |
| Flat packed RGBA8888 | Selected as the only production representation |
| Tiled/COW RGBA8888, edge 16 | Rejected: physical dense-apply p95 is 9.126 ms, above the fixed 8.0 ms target |

T32 and T64 were screened on the host and remain diagnostic; they do not expand the physical
decision matrix. Palette-index ownership, palette-edit
consequences, alpha compatibility, and future-format impact remain part of the semantic decision;
palette-index storage is not a fourth physical candidate for this MVP decision.

`PixelSnapshot` remains one final domain value and privately owns one row-major `IntArray` whose
bits are `RRGGBBAA`. Inputs are defensively copied or packed; the owned array is never mutated after
construction or exposed. A bulk read returns a defensive copy. `PixelSurface`, where a mutable
work surface is required, privately owns one row-major packed `IntArray` inside
`:core:pixel-engine`. `PixelPatch` privately owns row-major integer positions plus packed before
and after arrays. Its inverse is a directional view that shares those arrays and swaps both values
and exact recorded revisions; it is not a second materialized payload.

Snapshots are not tiled and share no mutable or structural backing across revisions. Patch order
is ascending row-major. Equality and hash use canvas, exact revision, direction, and semantic
packed content rather than primitive-array identity. Pixel positions, counts, and row-major indexes
use `Int`; accepted area is at most 65,536, so `y * width + x`, array sizes, and byte counts are
representable without narrowing. Domain-owned packed storage refines ADR 0002 and `ARC-005`:
immutable-by-construction private primitive storage is permitted in `PixelSnapshot`; mutable work
storage remains exclusive to the pixel engine. Defensive read copies are projections, not exposed
owned storage.

`Stroke` likewise packs its already validated ordered samples into one private row-major `IntArray`.
Semantic position iteration reconstructs typed positions, while the pixel engine reads primitive
indices without retaining or receiving the owned array. This removes per-command coordinate-object
traversal without changing gesture order, duplicate meaning, equality, or hash behavior.
Stroke rasterization proves containment, uniqueness, effective change, canonical order, and
affected bounds once. Its internal patch factory consumes those proven invariants and does not
repeat full-volume validation scans; the public arbitrary-change factory retains complete typed
validation.

Test-only current and tiled candidate implementations are not production APIs or runtime options
and are removed after their immutable evidence has been recorded.

### Conservative MVP limits

| Limit | Unit | Owning validation boundary | Accepted maximum |
| --- | --- | --- | --- |
| Canvas width | pixels on one axis | `CanvasWidth.create` before `CanvasSize` or allocation | 256 |
| Canvas height | pixels on one axis | `CanvasHeight.create` before `CanvasSize` or allocation | 256 |
| Canvas area | total pixels | invariant derived from validated axes and asserted by `CanvasSize` before allocation | 65,536 |
| Raw stroke volume | ordered samples per accepted stroke | `Stroke.create` before containment scan, ownership copy, or raster work | 262,144 |
| Patch volume | unique changed pixels per accepted patch | `PixelPatch.create`/stroke rasterization before sort, packed ownership, or commit | 65,536 |
| History capacity | retained committed entries | application history retention policy before atomic commit | 64 |
| Retained history volume | total retained pixel changes | application history retention policy before atomic commit | 524,288 |

All limits come from one canonical `PixelLimits` policy. Axis, raw-stroke, patch, and history owners
return closed typed rejections and test cap minus one, cap, and cap plus one before the bounded
resource is allocated, sorted, or committed. Canvas area is deliberately not a second reachable
validation path: two positive axes independently bounded at 256 mathematically imply area at most
65,536. `CanvasSize` asserts that invariant, while any integer rectangle attempting to exceed it
already receives the applicable typed axis rejection. Raw stroke and patch limits remain separate:
up to four full-canvas raw samples per pixel may collapse to at most one effective change per pixel.

History entry count alone is not a sufficient memory bound. The accepted policy must derive a
deterministic retained-change bound from the selected patch and inverse representation. An
accepted patch must not become a committed but undo-disabled mutation. Capacity and retained
volume are checked before the atomic document/history commit; no runtime free-memory reading or
device-specific branch becomes business policy.

Core owners consume the same typed policy. UI, future project-format mapping, and future automation
must map those results rather than duplicate or relax a constant.

These are conservative supported caps, not claims about the largest possible canvas or workload.
The migrated 256-square physical production lane supports canvas and patch caps with 21.1% p95
latency headroom at its slowest row and 58.1% p99 headroom. The raw-stroke cap is half the largest
passing 524,288-sample host fixture. The physical `H=64,T=524,288` flat owner supports both
history caps while using 3.55% of the fixed runtime maximum, leaving substantial retained-heap
headroom. No
interpolation or device-dependent runtime policy is permitted.

### Measurement contract

The evidence matrix and pass conditions in the linked evidence document are fixed before a
candidate or supported cap is selected. Evidence is collected in independent lanes so that
correctness work does not perturb latency, forced collection does not masquerade as natural GC,
and renderer timing does not block a core representation decision:

| Lane | Required boundary |
| --- | --- |
| Correctness | Exact deterministic pixels, semantic equality/hash, canonical ordering, containment, revisions, inverse round trip, affected region, unaffected pixels, and atomic no-op or typed rejection behavior at representative workloads and cap boundaries |
| Latency and ART tail | Warmup plus raw command samples with cheap per-sample outcome, revision, history, `ChangeSet`, invalidation, and no-op identity checks; no forced GC/finalization and no full-document equality scan, digest, or hash between timed samples |
| Retained memory and PSS | Independent invocations with an explicitly retained owner and post-GC checkpoints; forced GC/finalization is permitted only in this lane and is not reported as a per-command GC observation |
| Frame | Representative correct-frame and deadline evidence collected after the selected renderer path exists; strict SurfaceFlinger/Perfetto physical-present correlation is deferred to the renderer milestone and is not an ADR-0005 acceptance blocker |

The correctness lane remains mandatory: a fast incorrect result is a failure. Full-document scans
and hashes belong there or at fixed before/after batch checkpoints, never after every latency
sample. Existing artifacts collected under earlier combined protocols remain immutable diagnostic
evidence; they are not rewritten, silently reclassified, or used as substitutes for a missing
lane-specific result.

Device byte observations select one fixed logical product policy; they never create a different
canvas or history limit for each device. The measurement route adds no benchmark dependency,
plugin, module, production API, or APK payload unless this ADR is revised first to record that
separate decision.

### Relationship to accepted ADRs

- ADR 0001 remains unchanged.
- ADR 0002 remains normative and is refined only for private immutable-by-construction packed
  `PixelSnapshot` storage and defensive packed read copies. Palette-indexed document truth remains
  prohibited.
- ADR 0003 remains normative. This ADR refines physical inverse storage, but undo and redo
  must continue to apply recorded exact before/after values and revisions without rebuilding or
  rebasing a transition.
- ADR 0004 remains distinct. Its transform rejects unsafe derived mappings for any validated
  `CanvasSize`; viewport safety is not a second product canvas-limit authority.

## Rejected alternatives

### Accept limits from the M1 host baseline or emulator smoke

Rejected because neither route measures ART retained heap, PSS, GC, correct-frame timing, or a
named physical minimum device. The M1 16, 64, and 256 fixtures are not product-limit candidates.

### Infer a maximum between measured candidates

Rejected because allocation cliffs, GC, tiling boundaries, projection cost, and rectangular
shapes are not reliably interpolated. A supported cap must be at or below an explicitly measured
passing point with documented headroom. This ADR intentionally does not claim or search for the
largest possible maximum.

### Use device memory readings directly as runtime limits

Rejected because the same document would have different validity on different adapters and
devices. Physical evidence informs one deterministic logical policy.

### Keep current and replacement representations in production

Rejected because aliases, compatibility wrappers, and selectable backends preserve two
canonical meanings and prevent equality, limit, and format behavior from being enforced once.

### Treat palette indexes as a storage-only optimization

Rejected because palette-indexed pixels can change document semantics when a palette entry is
edited. That ownership choice must be decided explicitly and coordinated with P2-04.

### Bound multi-step history by entry count only

Rejected because entries can retain different numbers of changes and the current inverse is a
second materialized patch. Entry count alone does not give a deterministic retained-memory
argument.

## Consequences

### Benefits

- product caps will be based on reproducible, lane-separated correctness, memory, latency, and
  renderer evidence appropriate to the implementation stage
- semantic color remains stable across storage, Compose, PNG, and future format boundaries
- every accepted canvas is indexable and rejectable before area-sized allocation
- stroke, patch, and history costs receive separate deterministic bounds
- one atomic migration removes the old representation rather than leaving a permanent bridge

### Costs and risks

- the atomic packed migration touches domain, engine, application, presentation, and evidence code
- physical-device collection and the three-candidate analysis require more work than a host
  benchmark
- a primitive or shared representation may require coordinated public API and architecture-text
  changes even if semantic behavior is retained
- presentation projection may dominate a storage improvement; representative renderer evidence
  follows once that path exists, while strict compositor correlation is deferred to its renderer
  milestone

## Enforcement impact

This accepted ADR updates the affected canonical documents with the production migration:

- `GLOSSARY.md` records the accepted color, blank, representation, and limit meanings.
- `PROJECT_LAYOUT.md` records the selected ownership and any explicitly permitted private
  primitive storage.
- `COMMAND_MODEL.md` records pre-commit patch/history budget enforcement when bounded history is
  introduced.
- `MVP_SCOPE.md` and `DEVELOPMENT_PLAN.md` reference the exact accepted limits rather than local
  constants.
- focused tests cover semantic pack/unpack, alpha and blank behavior, overflow, rectangular
  canvas bounds, allocation-before-rejection, patch ordering and inversion, retained-budget
  determinism, and every limit boundary.
- architecture validation and root `check` reject duplicate storage or policy paths.
- physical evidence and its checksums remain linked from this ADR.

No quality gate, suppression, baseline, external API, or waiver is introduced.

## Migration and rollback

There is no persisted project data to convert. After this ADR has sufficient evidence and is
accepted, implementation proceeds in dependency order across the selected canonical path:

1. semantic color and validated canvas/limit values in `:core:domain`;
2. snapshot, surface, patch, raster, and inverse storage in `:core:pixel-engine` and its domain
   snapshot boundary;
3. `ChangeSet`, command rejection, and future bounded-history ownership in
   `:core:application`;
4. projection and callers in `:presentation:compose` and composition in `:app:android`; and
5. focused tests and evidence routes.

If representation or public APIs change, all production consumers migrate in the same focused
change and the old implementation, alias, adapter, and analytical candidate code are removed
before P2-02 becomes ready. No read-old/write-new or runtime-selectable path is retained.

Rollback reverts the whole unmerged focused change to the prior accepted ADR 0002/0003 path.
If physical verification invalidates the implementation, revert the complete focused migration
and return this ADR to a new proposed decision rather than keeping both representations.

## Related

- Issue: #38
- Evidence: [P2 Representation and Limit Evidence](../quality/P2_REPRESENTATION_LIMIT_EVIDENCE.md)
- Refines: ADR 0002 private snapshot storage/read boundary and ADR 0003 inverse physical storage
- Distinct from: ADR 0004 viewport bounds and mapping safety
- Supersedes: none
- Superseded by: none

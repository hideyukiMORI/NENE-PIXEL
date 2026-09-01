# ADR 0005: Pixel and color representation with bounded document policy

- Status: proposed
- Date: 2026-09-01
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

### Proposed evidence gate

While this ADR is `proposed`, it fixes the decision procedure only. It does not accept a new
pixel representation, color interpretation, public production API, or numerical limit.

This ADR must remain `proposed` until all of the following are present in the linked evidence
document:

1. the immutable M1 route has been reproduced from commit `37c0f57` in an isolated worktree;
2. a separately named current-main P2 route has measured the current representation and all
   required analytical candidates under the same correctness contract;
3. the required sparse, dense, eraser-equivalent, no-op, conflict, projection, and retained
   analytical-history workloads have results at every proposed boundary candidate;
4. a named physical minimum Android device has supplied ART latency, allocation and GC,
   retained heap, PSS, and frame evidence;
5. raw artifacts, their exact metric boundaries, profile metadata, and SHA-256 checksums are
   recorded; and
6. exactly one complete semantic, storage, and logical-limit policy passes every pre-fixed
   condition without interpolation.

Host JVM and emulator observations are auxiliary. They may expose regressions or eliminate a
candidate, but they cannot make this ADR `accepted`, select a hard product limit, or support a
user-visible performance claim. A hard maximum is the largest explicitly measured candidate
that passes every applicable pre-fixed condition. If the largest measured candidate passes,
the matrix is extended before it is called a maximum.

### Semantic decision surface

Before acceptance, this section must replace every unresolved item with one precise semantic
contract:

| Question | Required accepted answer | Current status |
| --- | --- | --- |
| Channel model | Exact RGBA8 channel order and named color space | unresolved |
| Alpha | Straight or premultiplied semantic alpha | unresolved |
| Alpha zero | Preserve hidden RGB or canonicalize to one transparent value | unresolved |
| Blank | Exact new-canvas and cleared-pixel value | unresolved |
| Eraser | Exact value written and its same-value no-op behavior | unresolved |
| Pencil | Replace, source-over, or another exact composition rule | unresolved |
| Equality and hash | Semantic equality independent of internal packing | unresolved |
| Palette | Pixels own semantic color or reference palette identity | unresolved |
| Compatibility | Exact Compose, PNG, and future project-format conversion meaning | unresolved |

Semantic `PixelColor` meaning remains separate from storage layout during evaluation. A storage
candidate must not silently determine the color space, alpha behavior, blank value, or palette
ownership.

### Storage decision surface

The current path and at least the following analytical candidates must be compared:

| Candidate | Required analysis |
| --- | --- |
| Current flat object representation | Defensive `List<PixelColor>` snapshot, mutable list surface, object changes, and materialized inverse |
| Flat packed RGBA8888 | Private primitive ownership, pack/unpack correctness, copy and equality cost, inverse representation, and ARC-005 impact |
| Packed tiled or copy-on-write | Tile size and ownership, sparse/dense behavior, deterministic equality/hash, snapshot sharing, and worst-case copy cost |
| Palette-index storage | Semantic ownership, palette-edit consequences, alpha compatibility, index width, and future-format impact; evaluated separately rather than assumed equivalent to RGBA storage |

Test-only candidates are analytical fixtures. None may be imported by production, exposed as a
public implementation interface, or kept as a second production path.

Acceptance must name exactly one canonical implementation for each of `PixelSnapshot`,
`PixelSurface`, `PixelPatch`, and inverse change data. It must also state:

- row-major query and canonical patch-order semantics;
- defensive ownership and whether any storage is structurally shared;
- equality and hash behavior;
- integer index type and overflow proof;
- where mutation is permitted and how mutable storage is prevented from escaping;
- whether the chosen private primitive ownership refines the literal ARC-005 and ADR 0002
  wording; and
- how the forward and inverse views preserve exact before and after revisions from ADR 0003.

### Limit decision surface

No numerical value in this table is accepted yet:

| Limit | Unit | Owning validation boundary | Value |
| --- | --- | --- | --- |
| Canvas width | pixels on one axis | validated canvas construction | unresolved |
| Canvas height | pixels on one axis | validated canvas construction | unresolved |
| Canvas area | total pixels | validated canvas construction before allocation | unresolved |
| Raw stroke volume | ordered samples per accepted stroke | stroke construction before ownership copy or raster work | unresolved |
| Patch volume | unique changed pixels per accepted patch | patch/raster boundary before unbounded sort or commit | unresolved |
| History capacity | retained committed entries | application history owner | unresolved |
| Retained history volume | total retained pixel changes | application history owner before document commit | unresolved |

Acceptance must name each exact type or factory, its closed typed rejection, and max-minus-one,
max, and max-plus-one behavior. Axis and area policies must prove all accepted allocation and
row-major indexes are representable. Patch and raw-stroke limits must be distinct so duplicate
and no-op samples cannot consume unbounded work while producing a small patch.

History entry count alone is not a sufficient memory bound. The accepted policy must derive a
deterministic retained-change bound from the selected patch and inverse representation. An
accepted patch must not become a committed but undo-disabled mutation. Capacity and retained
volume are checked before the atomic document/history commit; no runtime free-memory reading or
device-specific branch becomes business policy.

The same typed policy must be consumed by UI, core, future project-format mapping, and future
automation. Adapters may explain a rejection but may not duplicate or relax a constant.

### Measurement contract

The evidence matrix and pass conditions in the linked evidence document are fixed before a
candidate or hard limit is selected. Every sample must also assert deterministic pixels,
canonical ordering, containment, revisions, inverse round trip, affected region, unaffected
pixels, and atomic no-op or rejection behavior. A fast incorrect result is a failure.

Device byte observations select one fixed logical product policy; they never create a different
canvas or history limit for each device. The measurement route adds no benchmark dependency,
plugin, module, production API, or APK payload unless this ADR is revised first to record that
separate decision.

### Relationship to accepted ADRs

- ADR 0001 remains unchanged.
- ADR 0002 remains normative while this ADR is proposed. Acceptance must state whether it
  refines its semantic RGBA, immutable domain snapshot, and pixel-engine mutation boundary or
  explicitly supersedes identified sections. Palette-indexed document truth or storage outside
  the engine enclave cannot be introduced as an implementation detail.
- ADR 0003 remains normative. This ADR may refine physical inverse storage, but undo and redo
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
shapes are not reliably interpolated. Only an explicitly measured passing candidate can be
selected, and the matrix must extend when its largest candidate passes.

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

- product limits will be based on reproducible correctness, memory, latency, and frame evidence
- semantic color remains stable across storage, Compose, PNG, and future format boundaries
- every accepted canvas is indexable and rejectable before area-sized allocation
- stroke, patch, and history costs receive separate deterministic bounds
- one atomic migration removes the old representation rather than leaving a permanent bridge

### Costs and risks

- P2-02 and representation-dependent parts of P2-04 remain blocked while this ADR is proposed
- physical-device collection and candidate analysis require more work than a host benchmark
- a primitive or shared representation may require coordinated public API and architecture-text
  changes even if semantic behavior is retained
- presentation projection may dominate a storage improvement and must be measured in the same
  decision

## Enforcement impact

When the evidence is complete, the accepted revision of this ADR must update the affected
canonical documents before or with production migration. At minimum:

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

No quality gate, suppression, baseline, schema, external API, or waiver is introduced by this
proposed policy-first document.

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
If evidence invalidates the proposed decision before implementation, revise or reject this ADR;
do not partially migrate production. If physical evidence invalidates an implemented candidate,
revert the complete focused migration and return this ADR to a new proposed decision rather
than keeping both representations.

## Related

- Issue: #38
- Evidence: [P2 Representation and Limit Evidence](../quality/P2_REPRESENTATION_LIMIT_EVIDENCE.md)
- Refines: ADR 0002 and ADR 0003 only after acceptance; none while proposed
- Distinct from: ADR 0004 viewport bounds and mapping safety
- Supersedes: none
- Superseded by: none

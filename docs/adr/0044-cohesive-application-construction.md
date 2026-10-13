# ADR 0044: Cohesive application construction

- Status: accepted
- Date: 2026-10-13
- Issue: #222
- Affected rules: `ARC-001`, `ARC-004`, `ARC-006`, `ARC-007`, `KOT-003`, `KOT-007`,
  `KOT-008`, `KOT-013`, `QLT-001`, `QLT-011`, `QLT-012`, `QLT-017`, `QLT-019`

## Context

Typed application analysis during #217 recorded thirteen existing production findings: two
non-null checks and eleven long construction/factory signatures. The public persistence binding
grew to eight ports as capabilities were added. Several internal constructors similarly repeat
immutable values with a common origin or lifetime. The ordinary untyped check's success does not
resolve the typed findings, and the original failed diagnostic remains evidence.

The four-parameter bound must be met without weakening analysis, inventing a second state owner,
changing transition semantics, or moving validation out of its canonical boundary.

## Decision

### Public persistence binding

`PersistencePorts` takes four immutable capability bindings:

- `ProjectPersistencePorts`: project storage and the recovery record.
- `PngPersistencePorts`: PNG export and PNG import.
- `PalettePersistencePorts`: palette JSON export and import.
- `UnderlayPersistencePorts`: reference-image acquisition and per-work underlay memory.

The app composition root constructs these from the same injected port instances. The existing
workflow factory still takes the runtime, `PersistencePorts` and conversion dispatcher. All
production and test callers migrate together. Remove the flat eight-argument constructor; no
alternative overload, service locator, default port or runtime lookup is introduced. The bindings
do not perform I/O, validate documents, coordinate leases or own state. Existing port interfaces,
operation order, error vocabulary, autosave/recovery and underlay-memory isolation are unchanged.

### Internal construction

Keep public state getters, factories and behavior contracts unchanged outside that binding.
Internal immutable carriers may group values with a common meaning and lifetime:

- runtime-switch owner factories retain the same admitted source and captured creation inputs;
- persistence flow construction uses the existing runtime and injected capabilities, creating each
  flow once and retaining the existing shared autosave/switch/recovery dependencies;
- gesture admission groups its captured canvas/effect/layer/source. Create it once at gesture
  start and share it when extending the immutable path; never recapture the live edit target;
- workspace construction separates editing focus/current gesture, view/underlay presentation,
  and ephemeral sessions. `WorkspaceState` and its existing reducers remain the only owner and
  mutation route. Share unaffected carriers on copies;
- palette draft source, history and preview are immutable groups. Retain evicted-remap composition,
  timeline/cursor behavior, pending-import exclusivity and continuous-colour cancellation;
- underlay source geometry and presentation are immutable groups shared when unchanged. The sole
  placement clamp accepts an internal requested placement and its image/canvas context;
- viewport-derived geometry remains one bounded calculation with the same finite-value checks,
  edge safety, clamping and arithmetic order. Group its actual computation inputs/results rather
  than recomputing values or exposing unchecked geometry as public state.

Carriers are private or internal, with at most four constructor parameters. They do not duplicate
authoritative facts or expose mutable collections. Factories/reducers still construct valid public
states atomically. No public raw WorkspaceState constructor, generic copy API, or additional action
language is added. Preserve existing observable equality, hash values, cancellation/notice rules,
opaque identity comparisons and logical history accounting. The two history checks become
`checkNotNull` with their existing failure conditions and messages.

## Rejected alternatives

### Weaken typed analysis or retain long compatibility overloads

This would preserve the owned violations or a competing construction path. The same rules and
original FAIL records remain; there is no suppression, baseline or waiver.

### Unrelated bags of four values

Use actual capability, source, history, presentation and session concepts. A constructor's arity
does not justify additional public state, a dispatcher, or arbitrary nested data.

### Rebuild or copy captured data at every input event

Gesture admission and unchanged immutable carriers can be shared. Copying pixels, histories or
captured source state merely to shorten a signature would add avoidable measured-path work.

## Consequences

### Benefits

The persistence composition contract explicitly names its four capabilities. Internal construction
can reuse coherent immutable values while keeping the existing owners and validation paths.

### Costs and risks

App, adapter and presentation test fixtures that build persistence ports must migrate. Workspace
copy and palette-history paths require particular care: an omitted field could lose an active
session or change cancellation. New carriers are bounded constant-sized allocations, not evidence
of a performance improvement. Preserve source-derived before/after operation costs and register
affected gesture, workspace, history, viewport and persistence workloads with #145 before merge.

## Enforcement impact

Select affected history, switch/persistence, gesture/admission, workspace cancellation, palette
draft, underlay and viewport contracts. Compile direct public API consumers and test fixtures.
Run application typed analysis and affected formatting; validate these documents. Reuse equivalent
successful evidence. Required final CI remains canonical check plus Android assembly. No schema,
dependency, module, quality-gate, device-performance or Baseline Profile change is part of #222.

## Migration and rollback

Change the one composition binding and all current callers atomically in #222. Public state and
port behavior remain compatible, and saved data requires no migration. Rollback is a checked
whole-change revert, preserving a single construction path and all historical evidence.

## Related

- Issue: [#222](https://github.com/hideyukiMORI/NENE-PIXEL/issues/222)
- [Architecture Constitution](../ARCHITECTURE_CONSTITUTION.md)
- [Project Layout](../PROJECT_LAYOUT.md)
- [Development Workflow](../DEVELOPMENT_WORKFLOW.md)
- Supersedes: none
- Superseded by: none

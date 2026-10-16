# ADR 0043: Cohesive editor presentation inputs

- Status: accepted
- Date: 2026-10-13
- Issue: #221
- Affected rules: `ARC-001`, `ARC-004`, `ARC-006`, `KOT-008`, `KOT-013`, `KOT-017`,
  `KOT-019`, `QLT-001`, `QLT-011`, `QLT-012`, `QLT-017`, `QLT-019`

## Context

Typed Compose analysis recorded ten LongParameterList and two SpreadOperator findings during
#216. The current editor still exposes an eight-argument public composition entry and a
six-argument public file-exchange callback constructor. Their individual values already belong
to distinct capabilities. Internal projection constructors also repeat many fields from existing
owners. Suppression, baselines and changed thresholds would hide this debt without resolving it.

#217 adds a further constraint: a render projection contains visible playback state, never the
session's timing plan or previous sample. Construction cleanup must preserve that boundary and
the equality which suppresses publication during display-neutral timing samples.

## Decision

### Public composition inputs

The single `NenePixelEditor` entry takes `EditorControls`, `EditorPersistenceControls`,
`EditorSettingsInputs`, and the optional Compose modifier. These are immutable capability
bindings supplied by the existing app composition root:

- `EditorControls`: the read-only render-state flow and existing `EditorCallbacks`.
- `EditorPersistenceControls`: the read-only operation and autosave flows, existing
  `EditorPersistenceCallbacks`, and the retained app job's read-only availability projection
  added by [ADR 0046](0046-visible-file-operation-admission.md).
- `EditorSettingsInputs`: the existing app-language controls and app-version display.

Each binding exposes only what the presentation implementation needs. They introduce no owner,
mutable mirror, dispatcher, collection job, runtime access in a composable, or new lifecycle.
The same composition entry collects the flows and enforces the existing language-loading gate.
The internal screen consumes one immutable grouping of render State, callbacks, current storage
projection and settings. Existing remember/effect keys retain their meanings.

`FileExchangeCallbacks` takes `PngExchangeCallbacks` (export, import, open as a new work),
`PaletteExchangeCallbacks` (export and import), and the reference-image picker callback.
These capabilities still invoke the existing app operation launcher and persistence workflow.
Migrate all production and test callers together and remove the long signatures; there is no
compatibility overload or alternative file-operation route.

### Internal construction and projection

`EditorController` remains the sole callback composition owner. `EditorCallbacks` may receive
that controller and its existing runtime adapter, forwarding the same operations and constructing
the same subordinate callbacks. The controller's publication and editing-action seams are
internal only. Stop-before-edit, pointer acknowledgements and runtime locking are unchanged.

`EditorRenderState` receives the existing runtime snapshot and notice adapter as non-property
construction inputs, then eagerly extracts the same projected values. It must not retain either
input through a field, deferred getter or closure. Keep the existing projected-value equality and
hash ordering, notice freshness filters, `EditorFrameProjection`, and `FrameImageInputs`.
The canvas and actual-size view keep one shared bitmap cache; timing-only progress does not enter
the projection or change its equality. The redundant PixelCanvas size parameter is derived from
its existing render state at the same composition boundary.

Other private/internal inputs use their actual concepts: document ID/revision for notice
freshness without a retained document; three fan ring radii; pressed/released pointer contacts;
and a swatch's visible colour/number/selection/identity. Preserve algorithms, evaluation order,
pointer precedence and bounds. No new workspace state or alternate gesture classifier is added.

Quick-select labels have zero or one slot-number argument. Represent that shape explicitly and
use the ordinary localized resource overloads. A resolved text and stable identity use the same
central description/semantics attachment implementation as resource descriptions. No array spread,
manual localized string composition, duplicate semantics path or changed resource is needed.

## Rejected alternatives

### Arbitrary groups of four or a new dispatcher

Parameter count alone does not justify a collection of unrelated bags or an alternative command
sink. Use existing owners for callback construction and the actual capabilities at public seams.

### Retain the whole runtime snapshot in the render state

This would unnecessarily retain playback timing internals and could make every timing sample a
render update. Eager projection preserves ADR 0042 and the current observable equality boundary.

### Relax analysis or keep both public signatures

Neither resolves the owned findings through one canonical path. All callers migrate atomically;
the original failed reports remain historical FAIL evidence.

## Consequences

### Benefits

The public composition API names the three capabilities it actually consumes. Internal
construction can reuse existing sources while keeping immutable, bounded render projections.
The same four-parameter bound and typed analysis remain applicable without a waiver.

### Costs and risks

Current app and test callers must migrate. Small input values add bounded constant-sized objects;
no pixel scan, bitmap construction or document-size copy is added. The refactor must not change
callback wiring, remember keys, notice freshness, localized semantics or playback publication.
Source-inspected costs are registered with #145; they are not measured speedup claims.

## Enforcement impact

Run typed Compose analysis, affected formatting and consumer compilation. Select host contracts
for pointer classification, fan geometry/labels, notice validity, callback forwarding and
display-neutral playback identity. Exercise any changed Android semantics attachment with focused
functional evidence; a signature-only caller migration does not trigger unrelated device tests.
Required final CI still runs canonical check plus Android assembly. No quality configuration,
dependency, module, schema, performance protocol or Baseline Profile changes.

## Migration and rollback

Update the app root, presentation implementation and direct test callers in the same #221 change.
Remove the old long signatures immediately. No saved data changes or migration are required.
A rollback reverts this whole composition change through a checked PR, never keeps two APIs.

## Related

- Issue: [#221](https://github.com/hideyukiMORI/NENE-PIXEL/issues/221)
- [Development Workflow](../DEVELOPMENT_WORKFLOW.md)
- [Project Layout](../PROJECT_LAYOUT.md)
- [ADR 0042](0042-workspace-preview-playback.md)
- Supersedes: none
- Superseded by: none

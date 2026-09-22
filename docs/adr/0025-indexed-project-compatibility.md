# ADR 0025: Indexed project compatibility and preserved legacy conversion

- Status: accepted
- Date: 2026-09-13
- Issue: #106
- Affected rules: ARC-001/004/005/007–012, CMD-001/002/005–010/012,
  KOT-001–008/013/016–020, QLT-006–009/011–016

## Context

ADR 0022 accepts indexed document semantics and explicit preservation of nonrepresentable v1 data.
ADR 0023 delivers complete palette-to-palette remapping without changing the M3 editable runtime.
Base main 2dd4e01 still returns DocumentState from ProjectFormatV1Codec and dispatches recovery
Candidate payloads directly to that codec. Those public results cannot express a validated v1 raster
with more than 256 distinct RGBA values once the sole editable document becomes indexed.

The existing application already owns runtime generation, operation identity, exact HistoryPosition,
one physical-operation lease and one last-safe recovery lineage. New conversion phases must extend
those owners. A second editable RGBA runtime, adapter-owned import workflow, indexed-to-v1 writer or
generation-only confirmation would lose either slot meaning or existing user data.

## Decision

### Applicability and atomic delivery

This decision fixes compatibility before live implementation. The one focused #106 cutover changes
domain, engine, commands/history, runtime, rendering/PNG, project storage and recovery together.
There is no deployed interval with indexed editing and RGBA v1 saving. M3 remains the only editable
path until the complete candidate passes. Palette draft UI is #107, but safe legacy conversion and
recovery UX are required by #106. No new dependency, module, plugin, HTTP or MCP surface is introduced.

[Project Format v2](../PROJECT_FORMAT_V2.md) is the only v2 byte authority. V1 bytes and their complete
RGBA meaning remain fixed; their decoder result changes to an exact uninstalled import value.

### Typed values and ownership

- DocumentState owns one PaletteDefinition and one packed-U8 PixelSnapshot. Selection remains in
  WorkspaceState; the separate EditorRuntime.palette and WorkspaceReducer.palette configuration
  owners are removed. Rendered RGBA is derived from the document's definition.
  Initial app construction supplies its definition once. New Document inherits the current
  document's immutable definition and fills every pixel with its defaultIndex; it does not retain
  or reconstruct a second palette configuration. New runtime selection starts at slot zero as
  before. Accepted load/recovery uses the candidate's definition.
  The initial app definition preserves the existing eight opaque entries in their existing order
  and appends exact transparent black at slot 8, which is its default. Pencil initially selects
  red at slot 0. This preserves the prior initial drawing/blank/erase appearance while making the
  default a real stored palette slot.
- PixelSnapshot owns only size, revision and defensive immutable U8 storage. It does not retain a
  second palette or authoritative palette count. Its factory validates size/U8 representation;
  DocumentState's factory owns the cross-value invariant that every index names an actual entry.
  The factory may cache the maximum unsigned index as an internal immutable derived value while
  copying. DocumentState validates this maximum through the existing palette membership boundary
  without rescanning the full raster on every command. The cache has no independent setter and
  does not participate in semantic equality/hash. Codec preflight uses the same domain membership
  boundary before snapshot allocation; it does not define a second index-range policy.
- LegacyRgbaSource is a distinct bounded immutable domain import value containing exact id,
  revision, size and row-major straight RGBA. Its private packed storage is immutable after factory
  construction, never exposed, and not accepted by command/history/runtime owners. It carries no
  version DTO, URI, operation lease or independent editable state.
  Its copyPackedRgba8888(): IntArray bulk read returns a defensive copy for the original-copy
  encoder and pixel-engine planner; neither consumer receives the owned array.
  Its colorAt(PixelPosition): DomainValueResult<PixelColor> supplies constant-time exact preview
  lookup with typed outside-canvas rejection. Application preview projections delegate that read;
  they neither copy the whole raster per pixel nor retain another application-owned pixel array.
- One closed DocumentImportSource carries either Current(DocumentState) or Legacy(LegacyRgbaSource)
  at project-load/recovery ports. It is typed import-domain vocabulary, not serialization DTOs.
  Application owns the complete validated source inside its bounded import operation. Only raw
  codec/transport bytes remain in format/adapter. UI receives immutable derived previews and handles.
  The only load/admission surfaces are ProjectLoadOutcome.Loaded(source: DocumentImportSource)
  and RecoveryInspection.Candidate(generation, source: DocumentImportSource). V1 yields Legacy;
  v2 yields Current. Adapters do not classify distinct colors or convert either source.

ARC-005's narrowly permitted immutable domain packed storage therefore includes LegacyRgbaSource
only as an uninstalled import value bounded by the existing canvas limits. Mutable pixel conversion
workspaces remain solely inside pixel-engine. No array alias, public mutation, editable legacy
compatibility mode or generic unbounded image container is authorized.

### One format and conversion route

ProjectFormatCodec is the common no-I/O encode/decode boundary. encode(DocumentState) writes v2;
decode(ProjectFormatBytes) validates and explicitly dispatches v1/v2, returning
ProjectFormatResult<DocumentImportSource>. encodeLegacySource(LegacyRgbaSource) exposes only the
original-copy encoding required by the adapter, never an indexed input. Version decoders and the
original-v1 encoder implementations stay internal. The same bounded carrier
keeps the old maximum/probe. Unsupported versions never fall through to another decoder.

V1 decoding validates its unchanged header, lengths and CRC before constructing LegacyRgbaSource.
It does not quantize and does not call pixel-engine. The application's one legacy-import planner in
pixel-engine classifies the source and performs the exact row-major conversion accepted in ADR 0022.
At most 256 distinct full RGBA values produce a validated indexed document with the original id and
revision. One color gets a duplicate; transparent-black/default rules remain exactly ADR 0022.
The steps have a fixed order: collect distinct colors by first row-major occurrence; if there is
one entry append its duplicate; then choose the first exact transparent-black entry, or append
transparent black when there is room, otherwise use slot zero. Consequently transparent-black-only
input produces [black, black] with default zero; any other single color produces
[source, source, transparent-black] with default two. Hidden-RGB alpha-zero is not exact transparent
black. At 256 distinct colors with no exact transparent black the default is slot zero, and UI
discloses its actual erase color and alpha. No input order or duplicate is normalized differently.
User-file load is clean, accepted recovery dirty; both have empty history and use the existing
runtime-install path. No adapter, UI or codec duplicates that migration policy.

Above 256 distinct values the planner returns ConversionRequired with the exact source and distinct
RGBA count, without creating or installing DocumentState. Selecting an existing PaletteDefinition
produces an indexed reduction preview through pixel-engine using the single NearestPaletteEntry
metric. No oversized PaletteDefinition is invented. Exact-match/Long-distance/tie semantics are
those of ADR 0022/0023; generated quantization and dithering remain separate future decisions.

Inspection, full-raster conversion, preview construction and future-owner preparation run outside
the runtime lock on an injected CoroutineDispatcher. EditorPersistenceWorkflow receives that
dispatcher plus one named PersistencePorts value grouping the existing project/recovery/PNG ports;
the app composition alone selects its execution context. Core uses structured withContext, never
Dispatchers.Default/IO directly and never creates a scope or job. This refines the existing public
coroutine boundary without adding a dependency or algorithm port/strategy. The sole physical-operation
lease remains held until the worker and cancellation cleanup drain. Completion returns to the existing
application transition boundary, which rechecks operation/source/destination identities under lock.
Recovery inspection classifies legacy sources before presenting an adoption or discard option so an
unclassified >256-color Candidate cannot reach an unguarded old retirement path.

### Original preservation through the existing writer

The only original-copy export accepts LegacyRgbaSource, re-encodes exact canonical v1, and uses the
existing fresh-destination picker/write/close/read-back/cleanup path. It cannot accept editable
DocumentState and never advances its checkpoint. Format-owned encoded bytes and the current copy
work exist only while the existing operation lease is held. The source remains retained until the
copy has actually drained, even if cancellation was requested.

ProjectStoragePort adds copyLegacySource(LegacyRgbaSource): LegacySourceCopyOutcome. That closed
outcome is Copied, Cancelled or Failed(ProjectStorageFailure, PartialOutputCleanup), reusing the
existing transport/cleanup vocabulary. Copied means complete exact close/read-back verification.
It cannot enter the normal save-completion/checkpoint transition. The public workflow operation
copyLegacySource(PersistenceOperationHandle) captures the matching retained source under lock,
performs only the port call outside the lock, and accepts the outcome only for that operation/source.
CopyingLegacySource is a closed phase of that same operation, not a second persistence request.

For an external user-file source the existing URI is never overwritten. Original-copy export is
available before reduction. For a recovery-only source a verified exact original copy is mandatory
before retirement or lossy adoption: picker selection, write completion, PNG export or a cached
success from another candidate is not a preservation proof. A verified copy is bound to the exact
current source/operation and remains sufficient when the chosen destination palette changes.
Original copying preserves the raw source id/revision and every RGBA value, not a reduced preview.

Optional original PNG export, if offered, derives exact RGBA through the existing PNG policy and
does not substitute for the required exact project original copy. No new PNG importer is included.

### Conversion coordination, confirmation and cancellation

Presentation retains one EditorPersistenceCallbacks facade. Its construction groups callbacks by
their existing meanings: ProjectFileCallbacks (save/export/load/new), PersistenceDecisionCallbacks
(confirm/cancel/accept/decline recovery) and LegacyConversionCallbacks (copy/preview/accept/decline
preserved legacy recovery). Each group has at most four callbacks and owns no state or policy.
The app wires these to the one workflow and lifecycle worker; they are not separate workflows.
Immutable legacy-source/reduction projections support exact read-only comparisons without exposing
owned arrays. LegacyReductionHandle binds the existing operation and monotonic destination epoch.

The existing runtime persistence coordination owns one conversion source, its origin/lineage,
selected destination definition, at most one completed reduction preview and its confirmation
identity. It adds closed phases to the existing operation projection, not another workflow owner.
At most one physical picker/read/write/conversion worker is active. Other persistence requests are
Busy; autosave remains the existing single coalesced pending capture and is deferred while needed.

The current editable document remains authoritative throughout preview. Its drawing may continue
outside Switching, and final adoption must still check the existing load source token: DocumentId,
runtime generation, active operation identity and exact starting HistoryPosition. A changed history
position requires fresh replacement consent; undo back to the exact position is equivalent, a new
branch is not. There is no implicit rebase or generation-only shortcut.

Each completed reduction preview is bound to the retained source/operation and the exact selected
destination definition, including defaults, order and duplicate slots. A destination change clears
the old permit and advances a monotonic destination epoch before launching work. Returning from
definition A to B to A never makes the first A worker or permit current again. Late completion,
old confirm/cancel handles and recomposition
cannot revive it. Original-copy success and preview success are independent proofs. Apply is enabled
only for a completed current preview, current replacement consent and sufficient original
preservation. Any failed or stale precondition leaves all current owners and last-safe data unchanged.

The existing RuntimeSourceToken retains runtime generation, DocumentId and exact HistoryPosition;
ActivePersistenceOperation retains the operation handle. The retained import source has a separate
identity plus origin/recovery generation, and its preview adds the destination epoch. Value-equal
sources from different operations are not interchangeable. Verified original-copy proof is accepted
only for that handle, retained source identity and recovery lineage/generation; changing destination
does not erase this source-bound proof.

Approved reduction creates a new DocumentId through the existing source port only when preparing
adoption, revision zero, empty history and no clean checkpoint. It is a dirty derived work. Source
preview preserves the original identity; preview does not consume identities or modify history.
All future owners are validated/constructed before Switching. The existing non-cancellable
retirement/install critical phase rejects cancellation as TooLate, cancels active drawing preview
through WorkspaceReducer, conditionally resolves the current recovery lineage, and atomically
installs the prepared owners. Failures preserve the old runtime; uncertain post-finish results make
lineage unknown until inspection, exactly as before.

Recovery-only legacy data cannot be discarded through another entry point while awaiting conversion.
New/load/discard/decline and autosave publication must respect the same preservation obligation;
an ordinary recovery-discard confirmation cannot bypass the required verified original copy.
Cancel retains the unadopted startup candidate. Process death before copy/adoption leaves that
Candidate available at startup; after a verified copy, the exact original remains available even
if death falls between retirement and installation. No stale original-copy result retires a newer
recovery generation. Ordinary Save As of the active document continues to preserve an unadopted
recovery Candidate and never supplies an original-copy proof for it.

### Recovery envelope v2

The one AtomicFile path stays `noBackupFilesDir/nene-pixel-recovery-v1`. Its historical suffix is
not the envelope version; changing the filename would strand prior data. There is still one ordered
writer, process-shared mutex, generation policy, bounded reader and explicit sync/read-back path.

Envelope 2 preserves ADR 0016's prefix fields, offsets, state bytes, generation range and outer CRC
algorithm. Only the unsigned version at offset 8 becomes `2`; a Candidate's payload at offset 19
must be exactly a project-v2 file. New Candidate and Retired writes use envelope 2. Retired remains
exactly 23 bytes. V2 Candidate length is 23 plus its project payload: 77 through 66,628 bytes.

Readers explicitly accept envelope 1 with project 1 and envelope 2 with project 2. After common
maximum/prefix/magic/version/state/generation checks, Retired requires exactly 23 bytes; Candidate
requires its envelope-version-specific minimum/maximum bounds. The outer CRC is then verified.
Only after it succeeds are nested magic/version read and the nested codec invoked for its exact
length, inner CRC and domain validation. Candidate has no separate payload-length field, so its
exact expected length is decided by that nested codec rather than guessed before nested parsing.
A mismatched
payload version is corrupt, not a request for fallback. Both Retired versions retain their existing
meaning. Unknown envelope versions fail typed. Common reader maximum 262,209 and probe 262,210
continue admitting the largest v1 Candidate. Version-specific maxima remain enforced.

Inspection carries the typed import source and valid recovery generation. An unadopted v1 Candidate
is not rewritten merely by inspection, lossless planning or preview. Accepted lossless recovery
retains its original Candidate lineage until a later legitimate publication/save/retirement.
The next committed capture writes v2 through the one writer; generation ordering never resets.

### Indexed transitions and history

At ToolGesture start, Pencil captures the active PaletteIndex and Eraser captures that moment's
PaletteDefinition.defaultIndex. StrokeEffect.Paint(PaletteIndex) and Erase(PaletteIndex) keep both
effects on the existing drawing/patch route; Erase is no longer a singleton. Application ToolGesture
retains the gateway-issued CommandSourceAdmission beside the domain Stroke inputs, carrying it
through extension and CommitPrepared. Domain never depends on an application token.

EditorRuntime supplies the current definition and gateway admission to WorkspaceReducer inside
the same BeginGesturePreview runtime lock; the reducer does not retain another palette owner.
ApplyStrokeCommand.create(admission, stroke) consumes that captured admission, removing the old
id/revision-only constructor. The adapter never substitutes runtime.state read at commit time.
Gateway admission checks precede the handler; the handler validates the captured target with the
current Palette.entryAt boundary before rasterization. Palette changes and document installations
cancel gestures before adoption. Intervening commands make prepared input stale, while Undo back
to its exact admitted history position is equivalent. A same-RGBA/different-slot stroke is a real
mutation. Undo/Redo use the existing immediate history commands and need no long-lived admission.

ReplacePaletteCommand validates the complete source token and PaletteRemap before committing one
transition. The public gateway issues a non-constructible CommandSourceAdmission containing its
private per-gateway identity marker, DocumentId, exact HistoryPosition and immutable planning base.
Execution validates marker identity plus current id/position before planning or mutation. The
planning snapshot's reference is not admission identity: a new gateway can reuse it, and an undo
back to the exact history position may reconstruct an equal snapshot. EditorRuntime separately
checks the palette-session handle against its runtime generation and session identity under lock;
runtime generation is not copied into CommandGateway. A direct gateway caller therefore cannot
bypass source admission, while undo back to the exact position remains admissible.
EditorRuntime.captureSource() obtains this admission from its active gateway under the runtime
lock for prepared command clients, including ReplacePaletteCommand. It creates no parallel token
owner; execution still checks switching/busy state, gateway identity and exact history. Installation
replaces the gateway, so a capture from the abandoned runtime cannot enter its replacement.

ChangeSet records a closed palette transition plus optional indexed patch. Palette-only
changes use a closed no-index-change case rather than an invalid empty patch. Recorded before/after
palette/default and before/after indices share a directional inverse; many-to-one undo never inverts
the mapping table. A changed definition/default or changed slot advances revision/history/dirty,
invalidates dependent rendering and records autosave. Only exact equal definitions and unchanged
indices are no-op. Palette-only revision changes create a new immutable snapshot value without
sharing mutable storage. Logical retention and oldest-first eviction follow ADR 0022's 64-entry,
8 MiB payload and additional retained-change limits; performance evidence is still required.

Workspace selection reconciliation occurs through the reducer in the same runtime lock. Forward
replacement maps the active slot; undo/redo keeps a still-valid current selection or uses restored
default. Historical user selection is not document truth. Render/PNG resolve the current immutable
definition, and render cache keys bind both snapshot and palette identity rather than revision alone.

## Rejected alternatives

- An indexed-to-RGBA v1 writer loses duplicate-slot identity, unused entries and default meaning.
- An editable RGBA fallback duplicates document commands, history and storage.
- A format-to-engine dependency or adapter quantization relocates business policy across the graph.
- Reusing a palette remap as a many-to-one inverse loses original pixel indices.
- A new recovery filename/journal, generation reset or read-only inspection rewrite strands lineage.
- Applying a stale preview, treating a copy picker as saved, or retiring before verified preservation
  can lose the only exact source.

## Consequences

The cutover is cross-module and requires explicit conversion UX. Legacy sources may retain up to the
existing maximum RGBA raster beside the current document and one reduced preview during the bounded
operation. This is a measured resource cost, not a second editable state. Fresh-output read-back proves
visible bytes at completion, not arbitrary later provider durability. Interruption before autosave
can still lose the newest derived edits; the original is preserved by the rule above.

## Enforcement impact

Before execution, the Issue and report fix changed paths and narrow checks under QLT-011–016.
Contract tests cover v1/v2 goldens and rejection/allocation order, 1/256/257/distinct-count boundaries,
hidden RGB and duplicate slots, exact original copies, all pre/post-write failures, operation drain,
stale source/destination/replacement branches, recovery-only bypass attempts, atomic installation,
palette-only invalidation/autosave, mixed drawing/palette Undo and every retention bound.

UI/device tests cover conversion preview/copy/accept/cancel, disabled Apply, localized outcomes,
recreation and last-safe preservation on the current supported device. Device assets must be freshly
preserved before any test; historical fixtures are not a substitute. A separate prospective protocol
is fixed in [P4 Indexed Cutover Verification Protocol](../quality/P4_INDEXED_CUTOVER_PROTOCOL.md).
It records required collector changes before implementation and must agree with the implemented
harness and complete artifact preflight before any latency/retained-memory collection.
Existing M3/#111 measurements are not indexed-runtime evidence and are never relabeled.

Prospective protocol v3 corrects collector observation boundaries before any sample. Internal
HistoryPosition remains application-owned and is asserted by application correctness tests;
device latency checks public admission/revision/history/definition facts. Legacy host timing
starts with an existing editor and, for preview, an admitted exact candidate. Production planner
workspaces, operation result owners and lossless installed owners remain inside the relevant
interval. This is an explicit operation definition, not permission to precompute a reduction or
expose measurement-only production state. The uncollected v1 contract is archived unchanged;
numeric gates and sample budgets are unchanged. V3 additionally distinguishes stale preview
handles from actual release: device weak references check obsolete public projection owners at
the existing post-cycle GC checkpoint; owning application correctness checks old bound planner
preview/snapshot owners. No internal state is exposed to the Android measurement artifact.

The current documentation iteration runs only diff whitespace and validateDocumentation. Live code
iterations use affected contracts/static/consumer compile. The final coherent candidate requires
canonical quality CI, with no duplicate local full run, forced cold build or automatic profile refresh.
Waivers: none. Acceptance fixes the implementation contract; it does not assert implementation,
device verification or performance acceptance. Those remain required before the atomic candidate
can ship.

## Migration and rollback

### Conversion destination composition

`EditorPersistenceWorkflow.legacyImport` exposes the sole `LegacyImportWorkflow` capability with
`copySource(operation)`, `previewSource(operation, destination)`, `acceptReduction(handle)` and
`declineRecovery(operation)`. This immutable facade shares the existing coordinator and operation
projection; it owns no additional state or scope. The four direct legacy methods on the enclosing
workflow are removed rather than retained as compatibility routes. General load/new/confirmation,
autosave and recovery-offer entry points remain on the enclosing workflow.

The conversion-required, copying, reducing, preparing, legacy-confirmation and legacy-cancellation
phases form the closed `PersistenceOperationPhase.LegacyImport` family, sharing the immutable
`import` projection. Their individual phase identities and the enclosing operation stream remain
unchanged. Presentation maps that family to one set of conversion status labels and retains the
same source/preview projections through physical cancellation drain.

While a greater-than-256-color recovery candidate is unadopted, the shared New/Load admission guard
returns `RecoveryUnavailable` before allocating replacement owners or opening a load transport.
The recovery offer leads to its explicit preserve/convert or preserve/decline flow first. Save As
of the current document remains available and preserves the candidate; it never proves original
preservation. Presentation disables New/Load for this known recovery state and retains the recovery
offer so that the required next action is available.

The conversion screen offers the current document definition and three immutable, app-composed
preset definitions matching the reviewed prototype: Dusk 16 (transparent black at slot 0 followed
by its fifteen fixed swatches), Grayscale 32 (31 evenly spaced opaque grays and transparent black
at default slot 31), and Swatches 256 (the fixed six-level RGB cube followed by forty opaque gray
swatches, default slot 0). These are existing destination definitions, not image-dependent palette
generation or the deferred #107 palette editor. The 256-color preset has an opaque default, which
the screen discloses. `LegacyPalettePresets` is an immutable presentation input assembled by Android
composition and passed through the four-argument `EditorPersistenceCallbacks.create` factory.
It does not own or replace the active document palette. The operation coordinator owns the selected
destination and preview epoch; local UI acknowledgement resets whenever the preview handle changes.

Update the active representation/ownership claims in ADR 0005/0007/0008/0009, constitution, command
model, layout and glossary with the atomic implementation. Preserve historical evidence and v1 bytes.
Remove old live RGBA constructors/consumers and public v1 DocumentState writer paths in that change.
Before shipping, the entire unmerged candidate can be reverted. After any v2 file or recovery record
exists, rollback must retain both readers and safe original preservation; reverting to v1-only code
is prohibited. Palette-editor UI and later structured-art features remain separate Issues.

## Related

- Issue #106 / P4-02; prerequisites #101, #105 and #111
- [ADR 0022](0022-indexed-palette-and-migration.md), [ADR 0023](0023-palette-remap-planning.md)
- [Project Format v1](../PROJECT_FORMAT_V1.md), [Project Format v2](../PROJECT_FORMAT_V2.md)
- Refines ADR 0005/0007/0008/0009/0014/0015/0016/0018/0019 at the atomic cutover

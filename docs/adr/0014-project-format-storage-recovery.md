# ADR 0014: Project format, storage, and recovery boundary

- Status: accepted
- Date: 2026-09-08
- Issue: #83
- Affected rules: `ARC-001` through `ARC-012`, `CMD-001`, `CMD-006` through
  `CMD-012`, `KOT-001` through `KOT-008`, `KOT-014` through `KOT-016`, `KOT-020`,
  `QLT-006`, `QLT-009`, `QLT-011` through `QLT-016`

## Context

M2 closed with one immutable `DocumentState`, one `EditorRuntime`, bounded command history, exact
RGBA8 pixels, and a clean checkpoint based on `DocumentId` plus runtime-local `HistoryPosition`.
It deliberately has no project file, save operation, load/recovery installation contract, or
process-death restoration.

M3 must preserve those owners while introducing durable data. Application-to-format dependency,
adapter access to a live runtime, revision-only save completion, an undoable cross-document load,
or a second recovery pixel schema would each create a competing path. Long storage I/O also cannot
hold the runtime lock or run on the main thread, so delayed completion and replacement races need an
application-owned identity and ordering contract before implementation.

Android's Storage Access Framework does not provide a provider-independent atomic replacement
contract for an existing document URI. `ContentResolver` documents that write-mode semantics differ
by provider and that exclusive descriptors may be pipes or sockets. Document write/rename flags
advertise operations, not atomic rollback. In contrast, `ACTION_CREATE_DOCUMENT` is the platform
Save As flow and does not overwrite another same-name file. Framework `android.util.AtomicFile`
syncs and renames a completed app-private write, but provides no locking; the caller must serialize
access.

The M2 limits make a fixed uncompressed file at most 262,186 bytes. Compression, a serialization
library, extensible metadata, or an unbounded recovery journal would add failure modes without an M3
requirement.

## Decision

### Project format v1

[Project Format Version 1](../PROJECT_FORMAT_V1.md) is the single normative byte-contract authority.
The project-format codec uses portable Kotlin standard-library capabilities and the existing domain
types; no JDK-specific codec API, serialization dependency, or plugin is added. ADR 0014 owns the reasons,
module boundaries, durability behavior, and compatibility policy, and intentionally does not repeat
the byte table.

The format is a bounded, deterministic, uncompressed binary document followed by CRC-32/ISO-HDLC.
The CRC protects against accidental corruption, not hostile modification. V1 persists document
identity, revision, canvas dimensions, and exact pixels. Runtime history, palette, workspace,
timestamps, storage identity, and recovery coordination remain outside the project payload.

`:core:project-format` privately owns DTO/byte representation, deterministic mapping, codec, and
future migrations. Its bounded mutable byte scratch and defensively owned immutable encoded bytes,
together with bounded transport bytes inside `:adapters:persistence`, are the narrow non-pixel
refinement to `ARC-005`; they never expose mutable arrays or permit a second pixel work surface.
`:core:application` does not depend on project-format. Application-owned ports exchange immutable
`DocumentState` captures and fully validated loaded candidates with `:adapters:persistence`, which
alone joins application and project-format and normalizes I/O failures. Reserved modules are created
only in their implementation Issues.

### One runtime persistence protocol

`EditorRuntime` remains the only owner that captures a document for persistence, accepts a durable
save completion, and installs a loaded or recovered candidate. Runtime generation, one active
operation identity, and the captured internal `HistoryPosition` identify operations. `Revision`
alone is never a completion or checkpoint identity.

These identities, switch-busy state, captures, and recovery ordering are private coordination
bookkeeping inside the existing application owner. They do not add a state category beside
`DocumentState`, `WorkspaceState`, and `RenderCache`, do not duplicate document/workspace truth, and
are never owned or changed by UI or an adapter. External layers receive only immutable derived
operation projections and typed request/results.

Long reads, writes, and codec work occur outside the runtime lock on an owned lifecycle worker or
injected dispatcher. Core does not launch an unmanaged job or read platform process state. At most
one persistence operation is active and at most one latest autosave capture waits; newer eligible
captures coalesce deterministically. The pending values are immutable document references, not
pixel copies or alternate state owners.

Explicit save normally permits drawing to continue. A successful completion is accepted only for
the same runtime generation and active operation. It installs the checkpoint captured for that
save. If later edits have moved the current history position, the current state stays dirty; undoing
to the saved position becomes clean. Completion from an abandoned runtime or superseded operation
is typed stale and changes no owner.

A loaded project is fully read, bounded, decoded, and validated before replacement is considered.
Its source token captures `DocumentId`, runtime generation, active operation identity, and starting
`HistoryPosition`. Long load work may overlap editing. Final replacement requires all four values
to match immediately before the switch together with current user discard/replace intent. Undo/redo
back to the exact captured position may proceed because it restores the same document state; a
different position or a new branch requires a typed stale/reconfirmation result and fresh discard
consent. Generation-only validation is prohibited. New document, load, accepted recovery, and explicit discard
all use one runtime-install boundary. Immediately before a new-document, explicit-load, or discard
switch, a short explicit switch-in-progress state rejects document commits and other switches as
typed busy. All validation and candidate-owner construction complete before this phase. It cancels
any preview through the workspace reducer, durably retires the old recovery candidate, and performs
the no-fail atomic owner installation. Retirement failure leaves the old runtime authoritative and
clears busy.

Load retains the file's document identity, revision, canvas, and pixels, then installs empty history,
the canonical initial workspace, and a clean checkpoint. Accepted recovery uses the same loaded
installation mechanics, adopts the valid Candidate as the new runtime's last-safe recovery lineage,
and starts explicitly unsaved/dirty because it has no user-file clean checkpoint. The Candidate
remains available until a later autosave, verified explicit-save clean boundary, or explicit
new/load/discard retirement replaces it. Neither operation is a `DocumentCommand`, `ChangeSet`, or
cross-document undo entry; `CMD-001` continues to govern mutations inside the newly active runtime.

### Explicit user file save

The M3 explicit-save contract is Save As through `ACTION_CREATE_DOCUMENT` only. The runtime first
captures immutable `DocumentState`; the persistence adapter then uses the project-format codec to
encode and validate it before requesting a destination. The adapter writes every byte to the fresh
URI and closes the descriptor, then reopens and validates byte-for-byte identity and the format
checksum before returning Saved. Provider read-back proves the visible document bytes at that
boundary; it cannot promise later hardware or cloud durability controlled by the provider.

NENE-PIXEL never truncates or overwrites an existing content URI and never attempts a temporary
sibling plus provider rename/replace. Cancellation or failure preserves every pre-existing user file,
the active runtime, and its checkpoint. A newly created partial output is deleted best-effort and the
result reports both the typed save failure and cleanup status. Permission loss, a null/crashed
provider result, I/O failure, read-back mismatch, and a provider unable to support the contract are
normalized by the adapter. Successful save and recovery-retirement cleanup are separate outcomes;
a cleanup failure does not relabel verified user bytes as an unsuccessful save.

### One bounded private recovery record

Recovery uses one app-private `android.util.AtomicFile`, one serialized writer, and no added
dependency. Its versioned private envelope contains either one Candidate with the exact v1 project
payload or an explicit Retired marker. The envelope layout, state names in code, and autosave debounce
value must be fixed by P3-04's focused implementation contract before code is added; P3-04 must not
invent a second pixel schema or change the exported v1 bytes.

Before `startWrite`, the candidate document is encoded and validated. After writing, the adapter
calls `FileDescriptor.sync()` explicitly so sync failure remains observable before commit, then calls
`finishWrite`. Because the framework method returns `void` and only logs its own sync, close, or
rename failure, the adapter must reopen through `AtomicFile.openRead`, validate the bounded envelope
and complete v1 payload, and compare the expected bytes before reporting durable success. A failure
before `finishWrite` calls `failWrite`; a validation failure after it is a typed storage failure and
must not claim that rollback necessarily succeeded. Only the base record plus AtomicFile's bounded
temporary/backup state exists. Startup reads through AtomicFile's recovery path, then validates the
private envelope. A Candidate must also contain one completely valid v1 payload; Retired contains no
project payload. A valid Candidate is offered for explicit recovery, Retired produces no offer, and
corruption is a typed user-visible result. Wall time and document revision never select a candidate,
and the app does not scan external or partial files for fallbacks.

Recovery acceptance never overwrites a user-selected file and does not retire the Candidate it is
recovering. It adopts that Candidate into the new runtime generation so another process death before
the first edit cannot remove the only safe snapshot. New/load/discard durably write Retired before
their runtime switch. Reaching a verified explicit-save clean boundary also retires the matching
recovery generation, without deleting a newer post-capture candidate. Candidate capture/generation
identity belongs to the private versioned envelope and the application writer's ordering bookkeeping;
it is never part of exported v1. Candidate and Retired requests share one ordered writer. Retired is
conditional on the expected recovery generation: a newer actual generation produces a typed
stale/no-op and remains intact. Comparison and publication occur in the same writer order, so a C1
retirement queued after a C2 Candidate cannot erase C2. Generation and operation identities also
prevent an old completion or coalesced request from republishing another runtime's Candidate. A
retirement failure for a destructive switch preserves the old runtime. A verified explicit save
remains Saved even when a separate recovery retirement reports failure.

Autosave triggers only from committed command results under `ARC-011`. The platform-owned schedule
may use a clock and lifecycle dispatcher, while core receives explicit events and reads neither wall
time nor ambient coroutine state. One active write plus one latest capture bounds pressure. Exact
debounce timing is deferred to the P3-04 contract because no present evidence selects a number.

## Rejected alternatives

### JSON, compression, or a serialization library

Rejected because the single-snapshot MVP has a fixed 256 KiB pixel maximum and no extensible
metadata requirement. These choices add dependencies, require a separate canonicalization contract,
and enlarge the corruption surface without solving a current constraint.

### Application depends on project-format

Rejected because persistence representation would enter behavior coordination and reverse the
existing adapter boundary. The adapter already has the declared dependencies needed to join the
application port and codec.

### Save by truncating or replacing an existing SAF URI

Rejected because provider-specific write and rename behavior cannot guarantee that interruption
preserves the last valid user file. Save As creates a fresh destination and keeps old files outside
the failure domain.

### Mark clean by Revision or current state at completion

Rejected because replacement branches can share a revision and editing may continue during I/O.
The captured generation, operation, and history position identify the exact durable state.

### Load through ReplaceDocumentCommand or keep old history

Rejected because it would mix document identities in one undo timeline and retain the abandoned
document as command history. Runtime installation is the existing canonical cross-document boundary.

### Recovery journal, timestamp winner, or multiple candidate files

Rejected because the MVP does not need the additional selection and generation policy or the larger
set of partially durable states. One AtomicFile record and generation-aware ordered writer provide
one last-known-good owner.

### Hold the runtime lock during I/O

Rejected because provider and codec latency would block drawing and potentially the main thread.
Immutable captures and checked completion provide atomic owner changes without long lock holding.

## Consequences

### Benefits

- deterministic, bounded v1 bytes preserve every current document semantic exactly;
- corrupt, truncated, future, and oversized inputs fail before runtime replacement;
- no provider interruption can partially replace an existing user-selected file under the adopted
  Save As contract;
- delayed save/load/recovery work cannot update an abandoned runtime;
- history, workspace, and private recovery never become alternate document owners; and
- P3-02 through P3-04 each have a single implementation boundary and focused evidence plan.

### Costs and risks

- every explicit save asks for a new destination; in-place Save is outside the M3 contract;
- read-back doubles provider I/O for the bounded project file and providers without readable stable
  output are rejected;
- CRC detects accidental corruption but not deliberate replacement;
- AtomicFile integrity still depends on the application enforcing its one-writer rule;
- a process death before a pending autosave completes may lose edits since the last successfully
  completed autosave; and
- exact recovery-envelope bytes and debounce timing remain blocked on the focused P3-04 contract.

## Enforcement impact

- `ARCHITECTURE_CONSTITUTION.md` narrows `ARC-005` to permit only private bounded codec and transport
  byte storage for one project file or recovery record outside the pixel engine.
- `COMMAND_MODEL.md`, `PROJECT_LAYOUT.md`, and `GLOSSARY.md` define the one capture/completion/install
  path and its state ownership.
- P3-02 must implement every golden, boundary, corruption, determinism, CRC-conformance, and bounded
  allocation test listed in the format authority.
- P3-03 must test fresh-URI Save As, full close/read-back, cancellation, permission loss, null/crashed
  provider results, I/O failure, mismatch, partial-output cleanup reporting, exact checkpoint
  completion, continued editing, stale runtime/operation completion, long-load editing followed by
  a different position or replacement branch, exact-position undo/redo return, stale load
  reconfirmation, busy commit rejection, preview cancellation, and atomic loaded installation.
- P3-04 must first accept its private envelope/debounce contract, then test AtomicFile success/failure
  recovery plus explicit sync/read-back verification, Candidate/Retired startup behavior, corruption,
  explicit recovery acceptance with Candidate lineage retention, dirty restored state,
  retirement-before-switch failure, save/retirement outcome separation, one-writer ordering, the
  C1-save/C2-autosave/C1-retirement race, generation races, coalescing, and the
  one-active-plus-one-latest bound.
- P3-01 changes documentation only. Its narrow checks are `git diff --check` and
  `./gradlew validateDocumentation`. Codec/storage/device evidence is not triggered until its owning
  implementation Issue. The final merge-ready PR uses required `quality` CI; no duplicate local full
  suite is required.

## Migration and rollback

No project file or recovery record exists before this decision, so there is no user data migration
and no v0 reader. P3-02 creates the reserved project-format module and v1 codec; P3-03 creates the
reserved persistence adapter and application protocol; P3-04 adds the one private recovery envelope.
Each later Issue must remove any exploratory duplicate before merge.

Before any v2 writer is accepted, a separate ADR must preserve v1 golden semantics and define one
read-old/write-current migration path. Rollback before user files ship removes the unmerged
implementation and this decision together. After v1 ships, rollback must retain a v1 reader and
requires a compatibility ADR; silently reinterpreting or abandoning v1 is prohibited.

## Related

- Issue: #83
- PR: pending
- Format authority: [Project Format Version 1](../PROJECT_FORMAT_V1.md)
- Builds on: ADR 0005, ADR 0006, and ADR 0009
- Android storage evidence:
  [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files),
  [ContentResolver](https://developer.android.com/reference/android/content/ContentResolver),
  [DocumentsContract](https://developer.android.com/reference/android/provider/DocumentsContract),
  and [AtomicFile](https://developer.android.com/reference/android/util/AtomicFile)
- CRC reference: [RFC 1952 section 8](https://www.rfc-editor.org/rfc/rfc1952.html#section-8)
- Supersedes: none
- Superseded by: none

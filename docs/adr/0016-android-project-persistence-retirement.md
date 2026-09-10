# ADR 0016: Android project persistence and recovery retirement

- Status: accepted
- Date: 2026-09-09
- Issue: #86
- Affected rules: `ARC-001` through `ARC-012`, `CMD-001`, `CMD-006` through
  `CMD-012`, `KOT-001` through `KOT-008`, `KOT-014` through `KOT-020`, `QLT-006`,
  `QLT-009`, and `QLT-011` through `QLT-016`

## Context

ADR 0014 fixes Save As, checked save completion, fully validated load, one runtime-install boundary,
and one private AtomicFile recovery record. P3-02 has supplied the exact no-I/O project-format codec.
The present runtime still has no operation identity, runtime generation, persistence port, asynchronous
result projection, switch-busy phase, Android document adapter, or durable recovery retirement.

The original work-package split assigned the private recovery envelope to P3-04. That would make
P3-03's required load and new-document replacement impossible to land: every destructive switch must
durably retire the old recovery lineage before replacing runtime owners. Treating the absent current
implementation as an always-successful recovery port would be a production no-op, while merging all
autosave and recovery UI into P3-03 would destroy the focused work-package boundary.

The application cannot encode bytes or carry Android `Uri` values. The persistence adapter cannot
observe a live `EditorRuntime`. Provider and AtomicFile work must run outside the runtime lock, while
late completion still needs exact application-owned lineage checks. The current presentation also
assumes every callback returns synchronously, so asynchronous completion needs one retained derived
projection without creating another document owner.

## Decision

### Work-package landing boundary

P3-03 owns the complete private recovery-record byte contract, bounded record reader, and real
conditional Retired publication needed by Save As, load, and new-document installation. It also owns
the application operation/install protocol, Android SAF transport, and the minimum existing-editor UI
connection. P3-04 uses the same record codec, generation, AtomicFile, and ordered writer to add
Candidate publication, autosave scheduling/coalescing, startup recovery offer/accept/decline, and
recovery-specific UI. P3-04 may not introduce another envelope, writer, generation, or retirement
path.

P3-03 implements decoding for both Retired and Candidate records so conditional retirement never
overwrites an unrecognized valid Candidate. It may provide the internal deterministic Candidate
encoder needed for codec fixtures and P3-04 reuse, but it exposes no Candidate publication operation,
autosave trigger, or recovery acceptance UI. The application treats a startup Candidate as a retained
pending recovery lineage; P3-04 supplies the user decision that can consume it.

### Private recovery record v1

The private file is named `nene-pixel-recovery-v1` under app-private files storage and is managed by
one `android.util.AtomicFile`. It is not exported, backed up, scanned beside user files, or selected by
timestamp. Every multi-byte integer is big-endian. The checksum is CRC-32/ISO-HDLC over all preceding
record bytes and is stored as an unsigned 32-bit big-endian value.

Let `L` be the Candidate project-payload byte count. The record layout is:

| Offset | Length | Field | Canonical value or encoding |
| ---: | ---: | --- | --- |
| 0 | 8 | magic | `4E 45 4E 45 52 45 43 00` (`NENEREC` followed by NUL) |
| 8 | 2 | envelope version | unsigned 16-bit integer `1` |
| 10 | 1 | state | unsigned byte `1` for Candidate or `2` for Retired |
| 11 | 8 | recovery generation | unsigned value in `1..Long.MAX_VALUE`; high bit zero |
| 19 | state-dependent | payload | exact Project Format v1 bytes for Candidate; zero bytes for Retired |
| `19 + payload length` | 4 | checksum | CRC-32/ISO-HDLC of offsets `0` through the final payload byte |

There is no payload-length field, padding, timestamp, runtime generation, history position, document
revision winner, or URI. Retired is exactly 23 bytes. Candidate is `23 + L` bytes: 69 through 262,209
bytes because the nested v1 payload is 46 through 262,186 bytes. The bounded recovery transport reads
at most 262,210 bytes, one above the record maximum, and never uses an unbounded read-all operation.

Validation checks the record maximum, fixed prefix, magic, envelope version, state, positive recovery
generation, state-specific exact length, and outer checksum before mapping the state. Candidate then
constructs one `ProjectFormatBytes` and uses `ProjectFormatV1Codec.decode`; only a fully accepted
`DocumentState` is exposed. Missing, Retired, Candidate, unsupported version, corrupt/invalid record,
resource excess, and transport failure remain distinct typed outcomes. Unknown state is corrupt v1,
not an extension point. No v0 reader exists.

### Recovery generation and conditional retirement

`RecoveryGeneration` is application-owned and distinct from document revision, history position,
runtime generation, and persistence operation identity. Virtual generation zero means the AtomicFile
is missing; stored records begin at one. Every accepted Candidate or Retired publication increments
the previously observed generation. Exhaustion at `Long.MAX_VALUE` is typed and does not write.

One adapter-owned writer serializes both states. `inspect()` returns Missing at virtual generation
zero, a validated Retired generation, a fully validated Candidate and generation, or a typed failure.
The app starts record inspection in `viewModelScope`; destructive operations remain typed unavailable
until the runtime accepts that result. Inspection and transport I/O run on the injected IO dispatcher,
never in the ViewModel factory or main thread. A Candidate stays intact as an unadopted recovery
candidate. P3-03 does not offer recovery, but it does provide a closed current-version resolution:
Save As never retires the unadopted Candidate, while new document and load explicitly state that the
recovery candidate will be discarded and require confirmation before conditional retirement. A
cancelled confirmation preserves both the Candidate and current runtime.

`retire(expectedGeneration)` rereads the record in the same writer order. Missing compares as zero.
A different actual generation returns typed stale/no-op without writing. Corrupt, unsupported, or
unreadable current content is a failure rather than permission to overwrite an unknown lineage. A
match produces Retired at `expected + 1`.

Before `AtomicFile.startWrite`, the exact Retired bytes are encoded and validated. The adapter writes
all bytes, calls `FileDescriptor.sync()`, and then calls `finishWrite`. A failure before finish calls
`failWrite` and reports the primary phase plus its rollback outcome. Because `finishWrite` returns no
durability result, the adapter reopens through `openRead`, performs the bounded complete decode, and
compares exact expected bytes before reporting Retired. Post-finish mismatch or failure is typed and
does not claim rollback. It also leaves the application recovery lineage uncertain: the cached
generation cannot be used for another conditional write until `inspect()` re-establishes the actual
record. Missing, validated record, comparison, and publication all occur under the same writer
serialization.

### Application operation and runtime-install protocol

`EditorRuntime` privately owns monotonic `RuntimeGeneration`, `PersistenceOperationId`, at most one
active persistence operation, switch-busy state, and current `RecoveryGeneration` together with its
existing owners. These values are coordination bookkeeping. External layers observe only an immutable
`PersistenceOperationProjection` and closed typed results; numeric operation/runtime identities stay
inside opaque application tokens.

The application public persistence boundary is intentionally small:

```kotlin
public interface ProjectStoragePort {
    public suspend fun save(document: DocumentState): ProjectSaveOutcome
    public suspend fun load(): ProjectLoadOutcome
}

public interface RecoveryRecordPort {
    public suspend fun inspect(): RecoveryInspection
    public suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome
}
```

`ProjectSaveOutcome` is Saved, Cancelled, or Failed with a closed reason and partial-output cleanup
status. `ProjectLoadOutcome` is Loaded with one validated `DocumentState`, Cancelled, or Failed with a
closed reason. `RecoveryInspection` distinguishes Missing, Retired, Candidate with one validated
`DocumentState`, and typed failure. `RecoveryRetirementOutcome` distinguishes verified Retired, stale
generation, generation exhaustion, failure before finish with rollback status, and uncertain
post-finish failure. `RecoveryGeneration` has a non-public constructor and contains only a validated
positive `Long`. `ExpectedRecoveryLineage` is the closed Missing or Present(RecoveryGeneration)
comparison input. Missing compares as virtual generation zero inside the adapter; zero is never a
`RecoveryGeneration` and is never encoded. Platform exceptions, `null`, Boolean success, raw bytes,
`Uri`, and project-format types do not cross these ports.

The closed adapter vocabulary is fixed as follows:

- `ProjectStorageFailure`: InvalidPickerResult, UnexpectedPickerResultCode, PermissionDenied,
  ProviderUnavailable, IoFailure, UnsupportedProvider, ResourceLimitExceeded, ZeroProgress,
  PrematureEnd, UnsupportedProjectVersion, InvalidProject, and ReadBackMismatch. Permission,
  provider, and I/O failures carry one `ProjectTransportPhase`: picker result, source open/read/close,
  destination open/write/close, or read-back open/read/close/validation.
- `PartialOutputCleanup`: NotNeeded, Deleted, or DeleteFailed. It accompanies a failed Save As and
  never replaces its primary failure.
- `RecoveryInspectionFailure`: ResourceLimitExceeded, UnsupportedVersion, Corrupt, ReadFailed, or
  CloseFailed.
- `RecoveryRetirementFailure`: current-record inspection, Retired encoding, start-write, write, sync,
  finish, read-back, or read-back mismatch. A known pre-finish failure carries NotNeeded or Completed
  rollback; failed rollback, finish failure, and every post-finish verification failure are Uncertain.

`RecoveryRetirementOutcome.Stale` also makes the cached lineage unknown until inspection, because a
different valid record may be Candidate or Retired. GenerationExhausted leaves the known current
record intact. No exception message, provider class, path, or URI enters these outcomes.

`EditorPersistenceWorkflow` is the sole public application service coordinating runtime begin,
lock-free port call, and matching runtime completion. Its public surface is:

```kotlin
public class EditorPersistenceWorkflow {
    public val operation: StateFlow<PersistenceOperationProjection>

    public suspend fun initializeRecovery(): RecoveryInitializationResult
    public suspend fun saveAs(): PersistenceRequestResult
    public suspend fun load(): PersistenceRequestResult
    public suspend fun createNewDocument(request: NewDocumentRequestResult): PersistenceRequestResult
    public suspend fun confirm(request: PersistenceConfirmationRequest): PersistenceRequestResult
    public fun cancel(operation: PersistenceOperationHandle): PersistenceCancellationResult
}
```

`operation` delegates the `EditorRuntime`-owned read-only flow. The runtime owns the sole private
`MutableStateFlow`, active operation, immutable capture/candidate, and every transition; the workflow
does not mirror them. `PersistenceOperationProjection` contains exactly one phase, one last outcome,
and one recovery status. Its closed phases are Initializing, Idle, Saving, Loading,
NeedsConfirmation, Switching, and Cancelling. Non-idle phases carry an opaque
`PersistenceOperationHandle`; NeedsConfirmation also carries an opaque
`PersistenceConfirmationRequest` and its closed reason. Handle/request constructors and numeric
identities are non-public. An old cancel or confirmation is Stale. Cancellation during an active
Save As or load transport enters Cancelling. Confirmation and prepared-candidate phases have no
physical port call, so cancellation completes immediately. Cancellation in Switching is TooLate.

The last outcome is None, Saved with a separate `RecoveryCleanupOutcome`, Loaded,
NewDocumentCreated, Cancelled, or Failed with one closed `PersistenceFailure`. Recovery status is
Initializing, Clear, UnadoptedCandidate, or Unknown with its typed cause. Public request results are Completed,
AwaitingConfirmation, Busy, Stale, TooLate, invalid new-document Rejected, or
RecoveryUnavailable. Public cancellation results are CancellationStarted, Cancelled, Idle, Stale, or
TooLate. Recovery initialization is Ready, AlreadyReady, Busy, or Failed. These results never expose a mutable
runtime owner.

Save captures and loaded candidates remain private active-operation state, not public transfer types.
There is no save-current-destination overload or general runtime-replacement API.

A save begins under the runtime lock by capturing one immutable `DocumentState`, exact internal
`HistoryPosition`, runtime generation, active operation, document identity, and current recovery
generation. The adapter encodes and decodes the capture before a destination request is emitted.
The Android picker then supplies one fresh destination for this operation. After complete write,
close, bounded read-back, byte equality, and codec validation, the same generation and active
operation may install the captured clean checkpoint. Drawing during preparation, picker display, and
I/O remains allowed. Later positions stay dirty, and undo to the captured position becomes clean.
Cancellation/failure clears only the matching operation. A cancelled or superseded token makes a late
completion typed stale.

Load captures document identity, runtime generation, operation, starting HistoryPosition, and current
discard intent before picker/read work. A completely loaded `DocumentState` is used to construct the
future `CommandGateway`, empty history, canonical `WorkspaceState`, and clean checkpoint before busy.
If the four source values still match, the original exact-position consent remains usable, including
undo/redo return to that position. A different position or replacement branch returns a typed
reconfirmation request that binds fresh discard consent to the new exact position. No candidate is
installed yet.

The final install permit sets a short switch-busy phase under the runtime lock and cancels any preview
through `WorkspaceReducer`. Document commits, gesture previews/commits, another switch, and explicit
cancel return typed busy/too-late during this phase. The real conditional Retired operation and
matching install form one non-cancellable critical phase, although the port I/O still runs outside the
runtime lock. Matching success atomically replaces every owner, advances runtime generation, clears
the operation and busy state, preserves the loaded document identity/revision/pixels, and installs
empty history, canonical workspace, and clean state. Retirement failure or stale completion clears
busy and retains all old owners. An uncertain post-finish result additionally marks recovery lineage
unknown and blocks another destructive operation until a fresh inspection is accepted.

Validated new-document construction uses the same future-owner and retirement/install path. The
existing direct replacement method is removed; no second cross-document path remains. Starting
another persistence or switch operation while one is active is typed busy. Explicit cancellation
during an unchecked Save As or load transport returns CancellationStarted. The app then cancels its
owned job/picker broker; core owns no job and retains a Cancelling physical-operation lease. Another
operation remains Busy until that port call has actually returned and cleanup has completed. Its late
success cannot install a checkpoint or runtime. Cancellation while awaiting confirmation or holding a
prepared candidate has no physical work to unwind, so it clears the matching operation and returns
Cancelled immediately. A verified save's cleanup and the retirement/install Switching phase are
TooLate. This rule avoids
implicit cancellation, unbounded cancelled work/captures, and more than one physical operation.

Verified Save As remains Saved even if its separate conditional recovery cleanup fails or is stale.
The clean checkpoint is the verified user-file fact; cleanup is a separately projected outcome.

### Android transport and lifecycle integration

The persistence adapter is a plain Android library depending only on `:core:application`,
`:core:domain`, and `:core:project-format`. The direct domain edge is required because the adapter
implements ports whose signatures contain `DocumentState`; it does not rely on that type leaking
through another module's `implementation` configuration. A new `nene.android-library` convention
applies the existing Android SDK, Kotlin explicit-API, JUnit, ktlint, detekt, and strict-lock policy
without enabling Compose.

Save As emits the standard `ACTION_CREATE_DOCUMENT` intent with openable category,
`application/octet-stream`, and a `.nenepixel` suggested name. Load emits the standard
`ACTION_OPEN_DOCUMENT` intent with the same category and MIME restriction. The adapter-owned
`ProjectPickerIntents` object builds both intents and parses the result code before the returned data:
`RESULT_CANCELED` is Cancelled, while `RESULT_OK` with a missing `Uri` and any unexpected result code
are failures. This avoids the nullable-`Uri` parsing of the stock CreateDocument/OpenDocument
contracts, which would collapse provider-invalid success into user cancellation.

The adapter exposes one `ProjectDocumentPicker` suspension boundary with `createDocument(String)` and
`openDocument()`. Its closed result is Selected with one `Uri`, Cancelled, or Failed with the existing
`ProjectStorageFailure`. The app-owned broker implements this boundary with the app-owned
`CreateProjectDocumentContract<String, ProjectPickerResult>` and
`OpenProjectDocumentContract<Unit, ProjectPickerResult>`, thin `ActivityResultContract` wrappers that
delegate intent construction and result parsing to `ProjectPickerIntents`. Activity-not-found and
permission failures while launching are also resumed as Failed, so no suspended picker request can be
orphaned. Because the contract wrappers live in the app module, which already resolves
`androidx.activity`, the adapter's production graph contains only the three core modules and the
already resolved `kotlinx-coroutines-core`; it declares no androidx library at all.

That absence is deliberate. A catalog entry that pins a version older than the newest published one
is incompatible with the `GradleDependency` lint check that the merge gate runs, and raising a shipped
version is a dependency update rather than this work package's feature work. P3-03 therefore adds no
androidx catalog version: `lifecycle-viewmodel` keeps its version-less catalog alias, no
alignment-only constraint exists for lifecycle, savedstate, collection, or activity, the app keeps
receiving those versions through its existing graph, and `gradle/verification-metadata.xml` is
unchanged. The adapter's instrumentation tests reuse the Compose BOM platform, `ui-test-junit4`, and
the existing `activity-compose` alias as test-only dependencies so `AndroidJUnitRunner` and its
transitive graph resolve to the versions already verified for the app.

Only the current create result becomes a fresh destination; no URI association is retained and no
API accepts a prior destination for overwrite. The adapter writes and closes every byte, then reopens
the same result for bounded read-back. It separately distinguishes cancellation, permission loss,
RESULT_OK without Uri, unexpected result code, resolver null-open, I/O failure, unsupported behavior,
read-back mismatch, and cleanup status. A failed newly created output is deleted best-effort without
changing the primary failure.

Provider APIs may throw undocumented runtime exceptions. Each adapter boundary catches
`CancellationException` first and rethrows it, then normalizes other runtime exceptions as the closed
ProviderUnavailable failure. Best-effort deletion follows the same cancellation-first order, catches
only adapter-boundary exceptions, retains the primary save failure, and reports DeleteFailed. Every
adapter-opened stream closes through a finally-equivalent path even when read or write throws
`CancellationException`; close completes before cancellation is rethrown.

Known lengths above a bound reject before opening or allocation. Unknown streams read incrementally
into one private maximum-plus-one buffer and stop as soon as the probe byte is present. Zero-progress,
premature EOF, open failure, read exception, and close exception are normalized before bytes reach the
codec. Loaded bytes are handed to the one project-format carrier and codec path.

Host tests do not execute `android.jar` Uri or Intent implementations. The adapter converts a public
picker Selected `Uri` exactly once into a private opaque project location. Production and host tests
then use the same single internal content transport; tests inject that boundary only to supply streams
and faults. It is neither a second save/load implementation nor a public generic storage API. Focused
Android functional tests cover Uri/Intent/result parsing and the actual ContentResolver and AtomicFile
boundaries.

`EditorRuntimeViewModel` retains the one runtime, presentation controller/state holder, persistence
workflow, and picker request broker. It launches orchestration in its existing lifecycle-owned
`viewModelScope`; injected dispatchers place codec, provider, and AtomicFile work on IO and keep result
publication on main. Once Switching begins, the coordinator executes retirement plus completion in a
`NonCancellable` context so ViewModel cancellation cannot leave a verified Retired record paired with
the old runtime. The `EditorPersistenceWorkflow` itself enters that context before `beginSwitch` and
does not return until retire plus completion/install finishes; callers cannot omit the protection. The
Activity/one app-owned Compose host owns Activity Result launchers and observes picker requests; the
ViewModel never retains an Activity or launcher callback. Presentation renders the immutable operation
projection and emits typed Save As, load, cancel, and confirmation callbacks. It does not call the
adapter or codec. Configuration recreation reattaches to the retained operation and owner rather than
launching a second worker, picker request, runtime, or candidate.

App composition constructs `AndroidProjectStorageAdapter` from the application `ContentResolver`, the
retained picker broker, and the selected dispatcher. It constructs `AndroidRecoveryRecordAdapter`
from an `AtomicFile` at `noBackupFilesDir/nene-pixel-recovery-v1` and the same dispatcher. Recovery
inspection and retirement use one adapter-process-shared `Mutex` across all adapter instances. This
prevents a recreated ViewModel and adapter from racing an older non-cancellable retirement without
adding an Application singleton or moving recovery ownership into the app.

The app already directly uses `androidx.lifecycle:lifecycle-viewmodel:2.9.4`; that artifact exposes
`viewModelScope` and transitively resolved `kotlinx-coroutines-core` and `kotlinx-coroutines-android`
1.9.0 into the locked Android runtime graph. P3-03 adds direct `kotlinx-coroutines-core` declarations
to `:core:application` and `:adapters:persistence`, plus the direct Android declaration to
`:app:android`. A direct declaration needs a version literal in `gradle/libs.versions.toml`, and the
merge gate's lint `NewerVersionAvailable` check, the Maven Central counterpart of `GradleDependency`,
requires that literal to be the newest published release, so the declared version is 1.11.0 rather
than the previously resolved 1.9.0. The coroutines artifacts in the app runtime graph therefore move
from 1.9.0 to 1.11.0. The resolved Android runtime artifact set does not grow: the regenerated
`app/android/gradle.lockfile` keeps the same 97 debug and 96 release runtime modules, and only the
four `kotlinx-coroutines` version literals change. Code
reachability and R8 output may still change and are assessed by the normal build gate rather than
assumed to be size-neutral. Core creates no scope or dispatcher; it uses `StateFlow` for its read-only
projection and `withContext(NonCancellable)` only for the switch critical phase. The app composition selects and injects one
`Dispatchers.IO.limitedParallelism(1)` dispatcher for persistence ordering; it is a shared library
dispatcher and is not closed by the adapter or ViewModel. Coroutines stay at the Android lifecycle and
adapter boundaries except for the application-owned read-only `StateFlow`, suspend ports, and internal
critical-section implementation recorded above.

Existing Material buttons, status text, and AlertDialog patterns are sufficient for Save As, Load,
discard confirmation, stale-load reconfirmation, and failure projection. P3-03 makes no layout system,
visual language, navigation, or other UI-design decision.

## Rejected alternatives

### Always-successful no-recovery implementation

Rejected because load/new-document could replace runtime owners without durably resolving an actual
record. It would also create a production path that P3-04 must remove.

### Merge all autosave and recovery behavior into P3-03

Rejected because debounce, Candidate publication, recovery offer/acceptance, and process-death
journeys form a separate focused work package. P3-03 needs only the shared record/reader/writer
foundation and real retirement operation.

### Land P3-03 with inactive load or a fake retirement port

Rejected because a merge candidate must contain the complete user-visible operation and one real
canonical install path. A hidden, no-op, stub, or test-only production dependency is not completion.

### Keep the synchronous new-document replacement

Rejected because it would bypass switch busy, preview cancellation, recovery retirement, and atomic
generation replacement.

### Put URI, codec, or orchestration in presentation

Rejected because presentation would gain storage decisions and application would lose ownership of
completion identity. The app module performs only lifecycle, worker, and Activity Result composition.

### Build a local continuation runner or add storage/serialization frameworks

Rejected because a custom `ContinuationInterceptor`, executor lifecycle, and cancellation race policy
would duplicate structured-concurrency machinery and enlarge the integrity test surface. The already
resolved lifecycle/coroutines family supplies ViewModel cancellation and dispatcher switching. A new
serialization, storage, background-job, or scheduler framework remains unnecessary.

## Consequences

### Benefits

- P3-03 can land real atomic load/new-document behavior without a temporary recovery bypass;
- P3-04 extends one already durable envelope and writer rather than replacing P3-03 infrastructure;
- save/load I/O and codec work never hold the runtime lock or run on the main thread;
- exact history and operation identities preserve dirty state and reject late completions; and
- rotation preserves one owner and one in-flight operation.

### Costs and risks

- P3-03 includes the shared recovery record reader and Retired writer earlier than originally planned;
- app startup gains an asynchronous recovery-record inspection gate;
- provider behavior still requires focused Android contract evidence; and
- a valid Candidate cannot be accepted until P3-04 adds its explicit recovery UI and install request.

## Enforcement impact

- ADR 0014, `COMMAND_MODEL.md`, `PROJECT_LAYOUT.md`, `DEVELOPMENT_PLAN.md`, the #86/#87 contracts,
  module graph validation, and glossary must reflect the moved retirement responsibility before
  production code is added.
- Application tests cover generation/operation mismatch, exact save checkpoint, later edits and undo,
  delayed load at the same/different/replacement position, fresh reconfirmation, cancellation,
  Cancelling lease/late success, stale handles, switch busy/too-late cancellation, preview
  cancellation, new/load use of one install path, Candidate discard confirmation, retirement
  stale/failure/uncertain reconciliation, and atomic old-owner preservation.
- Adapter host tests cover bounded reads through maximum plus one, exact write/close/read-back,
  result-code/null-Uri and provider failure normalization, partial-output cleanup reporting, envelope
  goldens, every structural boundary/corruption, nested v1 validation, Missing/Present generation
  comparison/exhaustion, and every AtomicFile write/sync/finish/read-back failure seam.
- Focused Android tests cover actual ContentResolver transport, picker intent action/MIME/extension,
  app-private AtomicFile publication/read-back, delayed completion across configuration recreation,
  one retained runtime/workflow/picker operation, and hoisted projection updates that cannot be
  overwritten by an older asynchronous render snapshot.
- QLT-011 treats the new private envelope as a storage-format change. The unchanged nested v1 codec
  reuses Issue #85's artifact-identified evidence. Before any new collection, Issue #86 and a versioned
  protocol must fix one bounded descriptive host lane for only the new envelope encode/decode and
  retirement work, or choose equivalent retained-memory evidence. No Android performance PASS,
  device performance matrix, profile generation, forced cold build, or local full suite is triggered.
  UI/lifecycle behavior triggers only the focused functional Android tests. Required final-candidate
  `quality` CI remains the sole full gate.

## Migration and rollback

No released recovery record or user file exists. P3-03 creates v1 and its first real Retired record;
there is no v0 reader. P3-04 must read and write this same v1. Before changing these bytes or semantics,
a later accepted ADR must preserve existing Candidate/Retired interpretation and one deterministic
migration path.

Rollback before P3-04 removes the unshipped P3-03 persistence module, application protocol, record,
and this decision together. After P3-04 or user files ship, rollback must retain the project v1 reader,
recovery v1 reader, and safe retirement behavior.

## Related

- Issue: #86
- Follow-up: #87 / P3-04
- Builds on: ADR 0006, ADR 0009, ADR 0014, and ADR 0015
- Supersedes: none
- Superseded by: none

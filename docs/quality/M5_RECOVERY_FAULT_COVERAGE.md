# M5 recovery fault coverage

Status: bounded host and native fault integration scopes; parent lifecycle acceptance remains open

Issues: [#242](https://github.com/hideyukiMORI/NENE-PIXEL/issues/242),
[#244](https://github.com/hideyukiMORI/NENE-PIXEL/issues/244) and
[#247](https://github.com/hideyukiMORI/NENE-PIXEL/issues/247), slices of
[#230](https://github.com/hideyukiMORI/NENE-PIXEL/issues/230).
Contract: [ADR 0045](../adr/0045-android-termination-and-publication.md),
[ADR 0046](../adr/0046-visible-file-operation-admission.md),
[ADR 0018](../adr/0018-bounded-autosave-debounce-contract.md) and
[current v4 recovery](../adr/0041-bounded-frames-and-project-v4.md).

## Recovery adapter/workflow boundary — #242

The host tests connect the real `EditorRuntime`, `EditorPersistenceWorkflow`,
`AndroidRecoveryRecordAdapter`, recovery codec, writer and read-back verifier. Controlled
in-memory `RecoveryAtomicFileAccess` supplies the existing adapter-internal I/O boundary.
It can hold a write or raise an ordinary I/O failure at an exact phase. It does not implement
another recovery workflow, return a fabricated publication result or use Android storage.

The fixture loads the existing synthetic two-frame document through the canonical load path.
The resulting Retired record has generation 1. A frame-duration command creates C1, published
and verified as generation 2; a distinct command creates pending C2. Whole-document equality
includes both frames, all layers, palette, identity and revision. Opaque state-token equality
distinguishes captures independently of revision. No ordinary artwork or device state is used.

## Cases and oracles

These are host case descriptions. The owning Issue/PR records actual execution, source hashes,
commands and raw logs; a case listed here alone is not a passing result.

Sources: [cancellation cases](../../adapters/persistence/src/test/kotlin/io/github/hideyukimori/nenepixel/adapters/persistence/RecoveryWorkflowCancellationTest.kt),
[I/O-failure cases](../../adapters/persistence/src/test/kotlin/io/github/hideyukimori/nenepixel/adapters/persistence/RecoveryWorkflowFailureTest.kt)
and the [controlled fixture](../../adapters/persistence/src/test/kotlin/io/github/hideyukimori/nenepixel/adapters/persistence/RecoveryWorkflowFixture.kt).

| Case | Fault and expected observation |
| --- | --- |
| Caller cancellation | Hold C2 at the write boundary, cancel its caller, commit C3, then release. The real NonCancellable publication and runtime completion agree on exact C2/generation 3; C3 stays pending and can subsequently publish as generation 4. The held publishing token and later admission establish lease ownership/release; an Idle UI projection alone does not. |
| Port cancellation | Raise CancellationException from the controlled write. The real writer rolls back before rethrow; C1 bytes survive, C2 stays pending, the publishing token clears, and a later explicit request can publish C2 after the fault is removed. |
| Write or sync failure | Inject IOException before finish with successful rollback. Assert the exact Failed phase/rollback result and projected outcome, unchanged C1 bytes and published token, pending C2 and released lease. Removing the fault permits one explicit C2 retry. |
| Rollback failure | Fail sync and rollback. Assert Uncertain with failed rollback, unknown recovery lineage, no published token, retained C2 and no further write on a direct retry. The simulated storage contents are fixture behavior, not a guaranteed rollback. |
| Finish uncertainty | Fail finish before the controlled storage commits its buffered write. Assert Uncertain, retained C2, invalidated published identity and unavailable lineage. C1 remaining in this fixture does not prove rollback for every physical finish failure. A direct request cannot infer success or safely write again. |
| Read-back uncertainty | Complete the write but fail verification read-back. Assert Uncertain rather than Published, with the same retained-capture and no-blind-retry conditions. After fault removal, canonical reinspection offers exact C2/generation 3; publication remains OfferPending. Declining the offer retires generation 4 without discarding the current pending C2; explicit publication then verifies C2/generation 5. |

Phase coordination uses bounded latches, with release in cleanup. Fixed sleeps do not establish
publication completion. A host test cannot prove process-death durability or Android's physical
AtomicFile behavior. Cancellation raised by the injected I/O boundary covers the documented
rollback/rethrow path; it is not evidence of an ordinary storage defect throwing arbitrary errors.

## Retained request and visible admission — #244

The original actual-UI reproducer uses the production `NenePixelEditor`, ViewModel callbacks,
worker, autosave scheduler and workflow, with synthetic memory-only ports in a `ComponentActivity`
host. At main `384204cb310ea79fd85e92f0731b0419961d7aaf` plus the internal construction seam,
it holds publication, presses enabled PNG export, reopens File and presses enabled Save As.
Both clicks close File while status says storage is ready. After publication and the first export
finish, Save As has not reached its port. A subsequent Save As reaches it normally. The original
failure is a confirmed admission/display defect, not a document-corruption or storage-loss result.

The retained original is one failed native case, with source manifest SHA-256
`250aff36df0d6db00cf995ccc81e5914bb59551ad1d956094fefb5da695c9af2` and raw log SHA-256
`68b5a6fb4e5f38f9df40dc1cf20c08208fb358b35a8b200eb1b8d7fc3f4ac08d`.
Configured-lab `outputs/issue-230-admission/repro-build-2/` preserves its source/build evidence;
`native-repro-1/` preserves both original APKs, stage records, positive control and final assertion.
The runner took 51.973 seconds; the complete instrumentation command took 66.990 seconds.
These durations describe the functional check, not performance acceptance.

[ADR 0046](../adr/0046-visible-file-operation-admission.md) keeps the first job and projects its
existing ownership to every affected initial/continuation control. The corrected regression
boundary checks disabled competing actions, localized preparing status, the retained first
operation, canonical cancellation/cleanup, and renewed admission after completion. The original
failing test and the corrected policy-specific regression keep separate source/artifact identities.
The owning Issue/PR records the selected commands and actual corrected results; this case
description alone is not a passing result.

The execution lane is a newly allocated synthetic API35/Android15 Google Play x86_64 image
revision 6, 1344 x 2992 at 480 dpi, font scale 1.0 and en-US, with effective RAM 3072 MiB.
The image fingerprint is
`google/sdk_gphone64_x86_64/emu64xa:15/AP31.240517.022/11948202:user/release-keys`.
Factory userdata, absent app/test packages and absent private paths are recorded before installation.
Existing AVD metadata/configuration is unchanged and the ordinary tablet is excluded. Original
environment/bootstrap and formatting failures remain retained separately from native verdicts.

This lane uses no ordinary artwork, real recovery adapter, DocumentsUI, task exit or process-death
injection. Its memory-port cancellation cleanup does not close the physical picker-drain or
AtomicFile obligations below. It does not qualify API37, every locale or the complete beta matrix.

## Adapter faults through scheduling and visible status — #247

The native slice composes the real recovery adapter through its existing public
`create(AtomicFile, dispatcher)` factory. An app `androidTest` subclass delegates normal file
operations to Android's `AtomicFile` and injects a named failure at the write, finish or read-back
boundary. The adapter's writer, verifier and outcome mapping remain the production implementation.
No adapter-internal access, public injection API, reflection, friend path, copied scheduler or new
dependency is needed. Project and other external ports use synthetic data only.

Three cases connect that adapter to the actual runtime, workflow, ViewModel, worker, scheduler
and `NenePixelEditor` in a `ComponentActivity`. Two further cases compose the real scheduler with
its existing injected clock. The ViewModel's private scheduler has no clock injection: observing
file attempts alone would miss a retry loop whose `Unavailable` result returns before the port.
The direct scheduler cases therefore observe clock calls as well as file attempts in a bounded
real-time window longer than their explicitly shortened injected quiet/cap policy. The counting
clock delegates to `System.nanoTime`, so deadlines continue to advance with the real coroutine
timeouts while recovery or export I/O completes. Pending C2 triggers the initial automatic quiet
publication; a second initial flush cannot race it. The actual ViewModel cases retain the production
policy; the direct cases establish bounded inactivity and explicit
resumption, not production timing acceptance, infinite-time no-spin or a CPU-usage guarantee.

The synthetic two-frame fixture enters through canonical load, creating Retired generation 1.
A canonical command creates C1, whose verified Candidate is generation 2; a distinct command
creates pending C2. Oracles include whole-document equality, exact bytes and generation, opaque
pending/published/publishing tokens, typed outcomes and actual resource-backed screen text.
The selected Issue/PR binds these cases to source, APK, environment and raw logs. These descriptions
alone are not execution results.

| Case | Fault and expected observation |
| --- | --- |
| Visible write failure and flush recovery | Inject `IOException` on the real write session. Actual rollback gives `Failed(WRITE, COMPLETED)`; C1 bytes and published identity survive, exact C2 stays pending, and publishing clears. The actual editor displays its localized autosave-failure resource. Remove the fault and request the existing ViewModel flush; exact C2 becomes Candidate generation 3. |
| Visible finish uncertainty | After real write and sync, fail before delegating finish, closing the stream. Assert `Uncertain(FINISH, NOT_NEEDED)`, unknown lineage, retained C2 and no published/publishing identity. A flush cannot blindly write another Candidate. The actual display is the higher-priority recovery-unavailable resource. C1 remaining in this fixture does not guarantee rollback for every physical finish failure. |
| Visible read-back uncertainty and offer resolution | Let real finish commit C2, then fail its verification read. No `Published` result may be inferred from C2 bytes on disk. After fault removal, the fixture calls the existing workflow's reinspection and receives exact C2/generation 3. The actual decline callback retires generation 4; the resulting gate change permits pending C2 to publish as generation 5. Reinspection is not a new or claimed user-facing refresh feature. |
| Failed scheduler suspension and gate recovery | Compose the real scheduler, workflow and adapter with a counting monotonic clock. After write failure, observe stable clock/file-attempt counts across a measured real-time window exceeding the injected quiet/cap deadlines, with no new trigger. A real workflow operation-gate transition then resumes the pending publication. |
| Uncertain scheduler suspension | Observe the same scheduler boundary after read-back uncertainty, including clock activity that would expose port-free `Unavailable` retries. Explicit flush cannot write against unknown lineage. Reinspection and offer resolution use the canonical workflow before publication can resume. |

The finish exception is a controlled method-boundary fault: Android's `finishWrite` itself logs
several lower-level errors instead of throwing them. This case does not reproduce physical rename
failure or power loss. Direct `FileDescriptor.sync` failure has no injectable boundary here;
#242's existing SYNC host result remains separate. Actual files and normal atomic operations in
this slice do not establish behavior under every storage or process-death fault.

Execution uses a newly allocated synthetic API35 lane and a dedicated test fixture directory,
never the ordinary recovery filename or underlay/settings stores. Admission records factory
userdata, absent app/test packages and private paths before installation, plus post-install and
pre-launch storage. Preserve resulting file bytes and logs outside disposable build output before
stopping and cleaning the task resources. Existing AVDs and the ordinary device are excluded.

## Remaining parent acceptance

TERM-CANCEL and TERM-IO-FAILURE remain bounded: #242 supplies adapter/workflow host fault cases,
#244 supplies retained-job/UI admission, and #247 owns the five native adapter/scheduler/status
cases above. Their actual accepted execution identities belong to the owning Issues/PRs. These
scopes do not prove physical sync/rename faults, every locale/API boundary, cancellation through
the native file adapter, or process-death durability. No public injection seam, reflection,
copied scheduler or dependency is added; visible admission remains ADR 0046's existing behavior.

TERM-BACK, TERM-RECENTS, TERM-STOP-KILL, TERM-LOW-MEMORY and TERM-PICKER-CANCEL remain native
integration work under #230 and the [beta matrix](../BETA_ACCEPTANCE_MATRIX.md). Before native
execution, pin the candidate, exact fixture and lineage, phase synchronization, environment and
isolated process/storage; preserve and verify restoration of artwork, recovery, underlay and
settings. An old isolation flag or backup-directory existence alone does not establish that
killing a process or deleting a recovery record is safe. #229 owns shared API37 admission.

For the picker case, distinguish the broker callback slot until its claimed result drains from
the runtime operation lease through fresh-URI cleanup. The next canonical workflow must not
acquire the physical operation during cleanup. A permanently missing callback remains unresolved.
The Worker/UI candidate is now reproduced and its focused correction is owned by #244 / ADR 0046
as described above. This does not change the remaining termination/publication or picker oracles.

No historical result is relabelled as a current test. Historical FAIL/invalid and lost-raw limits
in the [M4 ledger](M4_EXIT_PROOF.md) remain. This slice does not accept #230, #145 or M5.

## Verification selection

For #242, the adapter host test classes and affected test-source formatting/static checks
detect adapter-to-runtime failure/cancellation propagation, false publication identity, unsafe
retry and leaked lease regressions. Reuse their unchanged accepted results during #244; a UI
admission change is not a trigger to repeat that host slice. For #244, select the changed worker
contracts, directly affected presentation conditions, API consumer compilation and the narrow
native admission cases. For #247, select the five new native fault cases, affected test-source
compilation/packaging and formatting/static checks, and documentation validation. This detects
fault-mapping, exact-capture/lineage, scheduler suspension/resumption and status-display regressions
at their combined boundary. Unchanged #242/#244 tests are not rerun merely to accept the new slice.
Documentation validation checks this coverage map and links.
Preserve the exact inputs and logs outside disposable build directories; reuse unchanged passing
results through review and commit. Required final non-draft CI runs canonical `check` plus Android
assembly without a duplicate local full run. QLT-011 through QLT-019 apply; active waivers: none.
#242 and #247 change no production, schema, API, dependencies or runtime cost. #244 changes the public
presentation binding and visible admission, with two constant-size availability publications per
retained job and no pixel scan/copy or per-preview-frame work. Its affected workload is registered
with #145 for the existing M5-exit gate; no performance collection or Profile generation is part
of these slices. Project and recovery schemas and dependencies are unchanged.

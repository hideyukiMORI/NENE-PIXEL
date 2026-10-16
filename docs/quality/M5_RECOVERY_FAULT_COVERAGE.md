# M5 recovery fault coverage

Status: bounded host and UI integration scopes; parent lifecycle acceptance remains open

Issues: [#242](https://github.com/hideyukiMORI/NENE-PIXEL/issues/242) and
[#244](https://github.com/hideyukiMORI/NENE-PIXEL/issues/244), slices of
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

## Remaining parent acceptance

TERM-CANCEL and TERM-IO-FAILURE remain partial: the #242 adapter fault cases do not execute
`AutosaveScheduler`, its flush/gate retry and no-spin behavior, or rendered/localized failure
status. The #244 admission cases connect the real scheduler but do not inject those adapter
faults. Their combined fault behavior still requires integrated evidence while preserving
app/adapter module boundaries. Neither slice adds a public injection seam, reflection, copied
scheduler or dependency; #244's visible-admission behavior follows ADR 0046.

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
native admission cases. Documentation validation checks this coverage map and links.
Preserve the exact inputs and logs outside disposable build directories; reuse unchanged passing
results through review and commit. Required final non-draft CI runs canonical `check` plus Android
assembly without a duplicate local full run. QLT-011 through QLT-019 apply; active waivers: none.
#242 changes no production, schema, API, dependencies or runtime cost. #244 changes the public
presentation binding and visible admission, with two constant-size availability publications per
retained job and no pixel scan/copy or per-preview-frame work. Its affected workload is registered
with #145 for the existing M5-exit gate; no performance collection or Profile generation is part
of either slice. Project and recovery schemas and dependencies are unchanged.

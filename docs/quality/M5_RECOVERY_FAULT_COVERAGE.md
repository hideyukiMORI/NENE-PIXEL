# M5 recovery fault coverage

Status: bounded host integration scope; parent lifecycle acceptance remains open

Issue: [#242](https://github.com/hideyukiMORI/NENE-PIXEL/issues/242), a slice of
[#230](https://github.com/hideyukiMORI/NENE-PIXEL/issues/230).
Contract: [ADR 0045](../adr/0045-android-termination-and-publication.md),
[ADR 0018](../adr/0018-bounded-autosave-debounce-contract.md) and
[current v4 recovery](../adr/0041-bounded-frames-and-project-v4.md).

## Integrated boundary

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

## Remaining parent acceptance

TERM-CANCEL and TERM-IO-FAILURE remain partial: these cases do not execute `AutosaveScheduler`,
its flush/gate retry and no-spin behavior, or rendered/localized status. Those require their own
integrated evidence while preserving app/adapter module boundaries. No public injection seam,
reflection, copied scheduler, added dependency or new runtime behavior is introduced here.

TERM-BACK, TERM-RECENTS, TERM-STOP-KILL, TERM-LOW-MEMORY and TERM-PICKER-CANCEL remain native
integration work under #230 and the [beta matrix](../BETA_ACCEPTANCE_MATRIX.md). Before native
execution, pin the candidate, exact fixture and lineage, phase synchronization, environment and
isolated process/storage; preserve and verify restoration of artwork, recovery, underlay and
settings. An old isolation flag or backup-directory existence alone does not establish that
killing a process or deleting a recovery record is safe. #229 owns shared API37 admission.

For the picker case, distinguish the broker callback slot until its claimed result drains from
the runtime operation lease through fresh-URI cleanup. The next canonical workflow must not
acquire the physical operation during cleanup. A permanently missing callback remains unresolved.
The Worker/UI admission-race candidate also needs a held-publication actual-enabled-callback
reproducer; source reachability is not a confirmed user-visible failure.

No historical result is relabelled as a current test. Historical FAIL/invalid and lost-raw limits
in the [M4 ledger](M4_EXIT_PROOF.md) remain. This slice does not accept #230, #145 or M5.

## Verification selection

Run the new adapter host test classes and affected test-source formatting/static checks. These
detect adapter-to-runtime failure/cancellation propagation, false publication identity, unsafe
retry and leaked lease regressions. Documentation validation checks this coverage map and links.
Preserve the exact inputs and logs outside disposable build directories; reuse unchanged passing
results through review and commit. Required final non-draft CI runs canonical `check` plus Android
assembly without a duplicate local full run. QLT-011 through QLT-019 apply; active waivers: none.
Production, schema, API, dependencies and per-operation runtime cost are unchanged. No device
collection, benchmark or Profile generation is part of this slice.

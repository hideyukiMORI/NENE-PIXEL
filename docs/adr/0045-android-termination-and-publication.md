# ADR 0045: Android termination and verified publication

- Status: accepted
- Date: 2026-10-15
- Issue: #237
- Affected rules: `ARC-001`, `ARC-004`, `ARC-007`, `ARC-008`, `ARC-009`, `ARC-010`, `ARC-012`,
  `KOT-005`, `CMD-009`, `CMD-010`, `QLT-011`, `QLT-012`, `QLT-017`, `QLT-019`

## Context

The owner's external review asks for one termination contract and fault-injection coverage for
each applicable exit path. Source review at main `e4231f1097d799d280715352005fff62213179aa`
confirms that `MainActivity.onStop` requests autosave and underlay flush without awaiting them.
This matches ADR 0018: a flush request is not a completed publication or a process-death guarantee.
The last uncompleted edits can be lost. Existing lifecycle tests wait for publication before
relaunch, so they do not prove survival of the latest edit at every immediate process kill.

The ordinary recovery I/O path converts expected storage failures into typed outcomes. A missing
catch in `AutosaveScheduler` does not establish that ordinary I/O silently terminates it. Unexpected
port exceptions remain a separate unconfirmed defect hypothesis; cancellation must propagate.
Existing adapter, workflow, scheduler and native tests cover different portions of this boundary.
Their presence does not establish complete end-to-end termination acceptance for current v4.

This ADR joins existing contracts and names the missing #230 acceptance cases. It does not adopt
synchronous lifecycle I/O, a stronger durability guarantee, a new shutdown service, or a new queue.

## Decision

### One publication path and one meaning of completion

`MainActivity.onStop` forwards the lifecycle request through
`EditorRuntimeViewModel.flushAutosave` to the existing `AutosaveScheduler`. The scheduler calls
`EditorPersistenceWorkflow.publishLatestCapture`; the runtime admits one persistence lease and
one latest immutable pending capture. The recovery port encodes and validates the whole document,
uses the ordered `AtomicFile` writer, syncs, finishes and verifies the complete read-back before
returning `Published`. Runtime completion records that exact captured state, never a newer edit
merely because it shares a revision. ADR 0014/0016/0018 and the current v4/envelope-4 contract in
ADR 0041 remain authoritative. There is no second lifecycle writer or direct UI document access.

A pending token, flush counter, dispatched coroutine or returned `onStop` is not publication
completion. A newer capture remains pending when an older publication completes. Startup recovery
offers, an active user operation, unavailable lineage/storage, and a suspended or killed process
can prevent publication. The existing 1 s quiet / 5 s cap are scheduling targets under eligibility,
not maximum data-loss bounds. A previously verified recovery record is the recovery point unless
a later complete valid record was published or the existing verified-save/retirement policy applies.

### Exit, cancellation and failure semantics

| Path | Existing contract and boundary |
| --- | --- |
| User Back or removal from recents | When the Activity receives `onStop`, use the same flush request. Back/task removal and Activity/ViewModel destruction depend on Android version and task state; do not assume all paths deliver a final callback or leave time for queued work. Already published recovery survives according to its record contract; the newest pending edit has no unconditional guarantee. |
| Process kill after `onStop` | No additional application callback or execution time is promised. Restart inspects the existing recovery record and offers a valid Candidate through the canonical recovery flow. A flush request alone cannot prove the latest capture survived. |
| Low-memory kill or crash | The same record/restart semantics apply, including kill before `onStop` or before the flush coroutine starts. `onDestroy`, `onTaskRemoved` or `onTrimMemory` must not be treated as guaranteed final-save hooks. |
| Coroutine cancellation | Once admitted publication starts, the existing `NonCancellable` region covers the port operation and runtime completion together. This prevents caller cancellation from splitting them while the process remains alive; it does not prevent OS kill. Port-raised cancellation releases the operation and is rethrown after applicable adapter cleanup. Cancellation before admission provides no publication guarantee. |
| Write, sync, finish or read-back failure | Expected failures return typed `Failed` or `Uncertain` outcomes. Pre-finish failure attempts rollback; failed rollback, finish and verification uncertainty must not be reported as proven success or proven rollback. Keep the exact pending capture and expose the existing persistence/recovery status. A suspended scheduler waits for the existing flush/gate trigger instead of spinning. |
| Claimed picker cancellation or late result | Keep the one physical operation until its claimed result drains, so a selected fresh URI can reach adapter cleanup. Identity-based claim admission and the single active slot remain in force. No arbitrary timeout or second-request queue is introduced; a missing callback is a residual acceptance concern. |
| Drawing device loss | Not an Android termination path in this application. Canvas/input faults are investigated through their own contract; no Windows device-loss shutdown routine is added. |

`flushUnderlayMemory` remains the separate, best-effort work-memory path of ADR 0034. It is not
document recovery, does not delay that writer through a shared I/O queue, and does not gain a new
completion guarantee here. Preview playback follows ADR 0042's lifecycle policy. Configuration
retention does not imply process persistence. Explicit Save As still uses a fresh provider output
and its existing verification/cleanup; neither private recovery nor provider success promises
hardware power-loss durability beyond the accepted storage contracts.

Android does not expose an application function through which every process death can pass.
The common review's single-exit instruction therefore means one canonical application publication
path for observable lifecycle requests, not an invented callback for arbitrary death. See the
official [Activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle)
and [Processes and app lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle).

### Fault-injection acceptance owned by #230

The following are prospective case identifiers, not implemented test names or passing results.
#230 selects and implements at least one integration fault case for each applicable path, retaining
the distinctions below. Pin the candidate, exact synthetic fixtures, interruption phase, expected
record lineage, and preservation/restoration admission before device execution. Use an isolated
test process/storage; never kill or clear the ordinary artwork session to obtain evidence.

For publication interruption, start with a verified C1, commit a distinct C2 and hold the real
publication at named boundaries. Before it starts, C1 remains the oracle. During physical write,
restart must not offer a partial/corrupt replacement as C2: retain/recover C1 or validate complete
C2 according to the interrupted atomic phase. After verified completion, exact C2 is the oracle.
Record bytes, lineage and projected outcomes; do not replace these with a fixed sleep. Force-stop
or a harness kill is a specified process-death injection, not proof of a real low-memory event or
of every manufacturer's recents behavior. Additional environmental I/O failure remains typed and
must not be mislabelled as successful recovery.

| Case | Required integration boundary and oracle | Existing starting point; remaining gap |
| --- | --- | --- |
| TERM-BACK / TERM-RECENTS | Exercise both user exits on selected Android versions while publication is held; record actual callbacks and ViewModel/task outcome, then inspect/relaunch with the phase-specific C1/C2 oracle. | `EditorRecoveryOfferTest` waits until Candidate exists. It is eventual background flush evidence, not immediate Back/recents interruption evidence. |
| TERM-STOP-KILL | Stop Activity, inject process death before publication starts and during physical publication, then verify the phase-specific recovery record and offer. | `InterruptedRecoveryWriteTest` leaves an unfinished framework temporary file beside a verified Candidate; it does not interrupt the actual Activity-to-writer chain. |
| TERM-LOW-MEMORY | Inject abrupt process death without relying on a closing callback, including a verified-completion restart control. Record the injection's scope and recover exact valid lineage. | Existing staged durable journeys wait for `Published` before restart; a callback-free interruption case is still needed. A simulated kill is not a claim that the OS low-memory killer was exercised. |
| TERM-CANCEL | Cancel the caller while the real workflow is held in publication; release it and assert coherent port/runtime completion, released lease and exact capture. Separately retain port-cancellation cleanup/rethrow coverage. | `EditorAutosaveWorkflowTest` covers held fake-port completion; integrate the real adapter boundary. `AndroidRecoveryRecordAdapterTest` covers cancellation cleanup separately. |
| TERM-IO-FAILURE | Inject write/sync and finish/read-back failures through adapter, workflow and scheduler. Assert typed status, retained pending capture, no false `Published` or busy spin, and permitted flush/gate recovery after removing the fault. | `AndroidRecoveryRecordAdapterTest` covers fault phases; `AutosaveSchedulerTest` covers a typed immediate outcome. Their combined propagation and user-visible status still need integration evidence. |
| TERM-PICKER-CANCEL | Cancel a claimed picker, attempt a competing operation, then deliver the late selected URI. Verify the slot/lease remains owned until canonical cleanup and no later request receives the old result. | `ProjectPickerBrokerTest` covers claim/drain and slot retention; adapter/workflow cleanup and Android callback delivery require the selected integration case. A permanently missing callback needs an explicit residual finding, not a fabricated PASS. |

Existing tests are source-level starting points here. Reuse an actual execution only when its
original source/artifact/log and relevant equivalence meet QLT-012. Lost historical raw files and
old-format observations do not satisfy these cases. The [beta matrix](../BETA_ACCEPTANCE_MATRIX.md)
owns the environment/evidence admission; this ADR does not authorize a new performance collection.
An actual regression must first have a failing focused reproducer and its own repair PR. A fake
port throwing an arbitrary programmer exception alone is not proof that ordinary storage failure
escapes production error mapping. Do not catch all `Throwable` or swallow cancellation to make a
test green.

## Rejected alternatives

### Synchronous `onStop` save or unconditional latest-edit survival

The accepted contract already distinguishes request and completion. Blocking the main thread does
not supply a missing callback or survival time, and would change lifecycle and latency behavior.
A stronger guarantee needs a concrete owner decision, design and evidence; it is not this factual
documentation correction.

### Separate shutdown writer, generic restart loop, timeout or queue

These introduce competing state/lease semantics or conceal a fault before a reachable defect has
been established. Preserve the typed outcomes, exact capture identity and physical cleanup path.

## Consequences

### Benefits

- Exit claims have an explicit completion boundary and one canonical implementation.
- #230 has named integration gaps without relabelling historical or partial tests as acceptance.

### Costs and risks

- Pending edits can still be lost on process death; missing callbacks and failed storage remain
  limits. This ADR does not close #230 or make M5 accepted.
- The isolated fault harness and Android-version cases require focused work and evidence before
  beta acceptance. Their absence remains visible in the owning Issue.

## Enforcement impact

Documentation validation checks the ADR/index and internal links. Independent source/contract
review checks the request/completion distinction and case ownership. Required final CI is
unchanged. #230 owns future integration tests above; no new execution result is claimed by this
documentation-only change. QLT-011–019 govern selection/reuse and existing phase gates continue.
No compiler, dependency, module, public API, schema, waiver, suppression or runtime change occurs.

## Migration and rollback

No data or code migration. The existing recovery and picker paths remain the only paths. Correct
this documentation if implementation evidence requires it; do not create a parallel shutdown
implementation as rollback. Any changed recovery guarantee needs its own accepted decision.

## Related

- Issues: #237 (this contract), #230 (lifecycle/fault acceptance), #228 (beta matrix), #145 (phase gate)
- Builds on: ADR 0014/0016/0018 persistence, ADR 0033 physical picker/operation, ADR 0034 work memory,
  ADR 0041 current format, ADR 0042 lifecycle playback
- Supersedes: none
- Superseded by: none

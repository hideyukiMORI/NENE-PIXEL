# ADR 0018: Bounded autosave debounce, coalescing, and lifecycle flush contract

- Status: accepted
- Date: 2026-09-11
- Issue: #87
- Affected rules: `ARC-011`, `CMD-001`, `QLT-006`, `QLT-011`, `QLT-013`, `QLT-015`

## Context

ADR 0014 fixed that autosave triggers only from committed command results, that the platform owns
the clock and lifecycle dispatcher while core reads neither, that at most one persistence operation
is active while at most one latest autosave capture waits, and that newer captures coalesce
deterministically. It deferred the exact debounce timing to P3-04 because no evidence selected a
number. ADR 0016 shipped the private recovery record v1 (envelope, generation, ordered writer,
bounded reader, conditional Retired publication) and left Candidate publication, autosave
scheduling, and the recovery offer to P3-04. Issue #87 requires that this Issue and an accepted
decision fix one concrete debounce/coalescing rule and its bounded evidence before production code.

Facts on `main` `9978dcf` that shape the rule:

- Production has no Candidate publication path. `RecoveryRecordPort` exposes `inspect` and `retire`;
  the internal `encodeCandidate` is exercised only by tests and the host evidence runner.
- A committed command does not pass through the persistence coordination. `EditorRuntime.execute`
  returns the `CommandResult` synchronously and the Compose controller publishes render state; there
  is no hook from a committed result to persistence.
- The app runs persistence on `viewModelScope` with `Dispatchers.IO.limitedParallelism(1)`; neither
  the activity nor the ViewModel observes `onStop`, so nothing flushes pending work when the app goes
  to the background, which is the moment before most process deaths.
- The largest v1 document is 256 × 256 pixels (`PixelLimits`), a 262,186-byte project payload and a
  262,209-byte Candidate record. Host evidence for #86 measured a maximum Candidate encode of
  5.93 ms and decode of 2.47 ms on the JVM; no device cost exists for encode, validate,
  `AtomicFile` write, `sync`, `finishWrite`, and bounded read-back verification as one operation.
- Existing device evidence concerns command commit latency (UP-input-to-committed-result p95 about
  10 ms to 12 ms) and frame overrun, not commit frequency or write cost. It is background, not a
  selector for a debounce number.

## Decision

### Trigger and capture (core, no clock)

Every committed command result that changes the document, including committed undo and redo,
records one immutable autosave capture in the runtime's persistence coordination: the current
`DocumentState` reference and its exact internal `HistoryPosition`, tagged with the runtime generation.
Recording replaces any earlier pending capture; the pending set is therefore never larger than one.
The runtime generation and history position together identify a captured state. Revision is never
used as this identity: an abandoned and a replacement branch can have the same revision.
A committed return to the exact published state clears the pending capture only when no active
persistence operation can replace or retire that Candidate. During an active operation the latest
capture is retained, including an undo to the previously published state.
Core exposes a read-only
autosave projection (opaque pending/published/publishing state tokens and last outcome) as a
`StateFlow` next to the existing persistence projection. Core creates no scope, dispatcher, or
timer and reads no wall time; it receives explicit `publishLatestCapture` requests from the
platform and answers with typed results.

### Publication and coalescing (core plus adapter)

`publishLatestCapture` takes the pending capture, encodes and fully validates it before
`AtomicFile.startWrite`, publishes it as a Candidate through the same ordered writer, generation,
and read-back verification that P3-03 uses for Retired, and then marks that exact state as published.
A newer capture that arrives while a publication is active waits as the single latest capture and
is published by the next request; it is never lost and never published twice. A publication is one
persistence operation under the existing one-active lease:

- A user operation (Save As, load, new document, confirm, cancel) requested while a Candidate
  publication is active waits for that publication to reach its outcome; the wait is bounded by the
  publication cost measured by the evidence protocol below and never blocks drawing.
  The bounded retry rechecks a busy request even if the publication completes between the initial
  busy result and registration of the wait. This adds no user-operation queue or unbounded retry.
- A `publishLatestCapture` request while a user operation is active returns a typed `Deferred`
  result, keeps the capture pending, and leaves scheduling to the platform.
- While a startup Candidate is still offered and undecided, `publishLatestCapture` returns a typed
  `OfferPending` result and keeps the capture pending: autosave never overwrites the previous
  session's unsaved work before the user accepts or declines it, matching the explicit-save rule of
  ADR 0014 that preserves an unadopted Candidate. The platform stops requesting until the offer is
  resolved.
- A verified explicit save at a clean boundary retires the matching generation as ADR 0014 already
  requires; only a pending capture matching the saved runtime generation and history position is
  dropped. Every different pending state remains, including a replacement branch at the same
  revision. Successful retirement invalidates the published Candidate identity. Stale, uncertain,
  or inspected lineage that cannot prove that Candidate also invalidates it.
  The exact saved capture is dropped at verified Save As completion even when an unadopted
  Candidate or unavailable lineage prevents recovery cleanup. Cleanup completion applies the
  same exact match again if a capture returned to the saved position while cleanup was in flight.

### State identity correction (2026-09-11)

Review of the unmerged implementation found that revision-based suppression and completion could
discard an unsaved replacement branch, or discard an undo performed while a different state was
being published. This correction restores CMD-008 and ADR 0014's exact-position semantics before
merge. One opaque immutable `AutosaveStateToken` represents runtime generation plus the existing
internal `HistoryPosition`; application constructors alone create it. Equality is its only public
identity operation. It exposes no numeric lineage, document, mutable state, or serialized form.
`AutosaveProjection` supplies these tokens to the platform scheduler, which compares them rather
than revisions. No second history counter or document snapshot is retained for published identity.
The publishing token identifies the capture held by the existing active operation; it is a derived
projection, not another capture or queue.

Regression evidence must cover same-revision replacement branches before and during publication,
return to a previous published state during publication, save cleanup overlapping a replacement
branch or undo, retirement followed by an undo to the former Candidate, and scheduler observation
of a changed token with an unchanged revision. Schema bytes, writer generation, and pixel/history
semantics are unchanged.

### Timing (platform, clock owner)

The app module owns one autosave scheduler that observes the autosave projection and applies
exactly these three rules with the constants fixed here:

| Rule | Constant | Behaviour |
| --- | --- | --- |
| Quiet window | `AUTOSAVE_QUIET_MS = 1_000` | Request publication when no new capture has been recorded for 1,000 ms after the latest one. |
| Latency cap | `AUTOSAVE_LATENCY_CAP_MS = 5_000` | Request publication no later than 5,000 ms after the oldest still-unpublished capture, even while captures keep arriving. |
| Lifecycle flush | none | On the activity `ON_STOP` event, request publication immediately if a capture is pending; the request runs on the ViewModel scope so it survives configuration changes. |

The scheduler issues at most one outstanding request at a time. One structured observer continues
folding projection changes and monotonic clock readings into the scheduler's single private immutable
deadline state while its request coroutine awaits publication. A capture already being published is
excluded from the next-request deadline. The first distinct pending capture starts its cap when
observed, and later coalesced captures retain that cap. If observation skips the intermediate start
event, the publishing/published token still identifies which prior pending deadline was consumed.
After publication completes with a still-pending newer capture, the quiet window restarts from that
completion, while an already established cap for that distinct pending capture is retained. There
is no second document owner, clock in core, unmanaged job, or concurrent publication request.
A request result cannot suspend a newer observation. If publication completes before its projection
is observed, the scheduler waits for that observation instead of repeatedly requesting the old state.
When a request is deferred because a user operation holds the persistence lease, the scheduler
retries one quiet window later and the cap resumes from that retry, so an active user operation
never turns the cap into an immediate re-request loop. Both timing constants live in one
`AutosavePolicy` value in the app module and are the only autosave timing numbers in the code base.

### Bounds and product consequence

While publication is eligible and the platform scheduler executes, these constants request a save
after 1 s of quiet or by the 5 s continuous-drawing cap, plus any publication already in flight.
They are scheduling targets, not a hard process-death loss guarantee: an undecided recovery offer,
an active user operation, unavailable storage/lineage, or a suspended/killed process can prevent
publication. `ON_STOP` requests a flush but cannot guarantee completion before process death.
Steady-state writes normally follow the quiet window or latency cap; lifecycle flush may request
an earlier write. The evidence protocol below must show that one maximum publication on the
reference device costs well under the quiet window; if the measured maximum exceeds 250 ms, the
constants are re-derived before merge as `AUTOSAVE_QUIET_MS = ceil(4 × max / 500) × 500`
and `AUTOSAVE_LATENCY_CAP_MS = ceil(20 × max / 500) × 500`, and this section is amended by a
dated paragraph rather than by rewriting the table.

### Evidence

Before Candidate publication code merges, one bounded device observation is collected under
[M3 Autosave Publication Evidence](../quality/M3_AUTOSAVE_PUBLICATION_EVIDENCE.md): one invocation on
the reference device, two fixed groups (maximum 256 × 256 document, minimum 1 × 1 document), five
warmups excluded from summary statistics plus twenty measured samples each of the Candidate publication through
the real `android.util.AtomicFile` adapter, descriptive minimum and maximum only, no PASS or FAIL
verdict, no speedup claim. Its only decision use is the constant re-derivation rule above.

## Rejected alternatives

### Publish on every committed command

Rejected because a stroke commits one command; continuous drawing would write a 262 KB record
several times per second, contradicting the one-active-plus-one-latest bound of ADR 0014 and adding
flash and battery cost with no product benefit over a five-second cap.

### Quiet window only, no latency cap

Rejected because continuous drawing never reaches a quiet window; a process death after minutes of
uninterrupted strokes would lose everything since the last pause.

### Periodic timer independent of commits

Rejected because it publishes unchanged documents, needs a clock inside core or a second owner of
"dirty since last publish", and violates ADR 0014's rule that autosave triggers only from committed
results.

### Publish on `ON_PAUSE` instead of `ON_STOP`

Rejected because `ON_PAUSE` fires during transient overlays (pickers, dialogs, multi-window focus)
while the process is not a death candidate; publishing there would race the Save As picker's own
operation. `ON_STOP` is the last reliable event before background death.

### Core-owned timer via an injected clock

Rejected because ADR 0014 assigns the clock and lifecycle dispatcher to the platform, and an
injected clock would still make core scheduling-aware. The projection plus explicit request keeps
core pure and testable without time.

### Selecting the constants without device evidence

Rejected by `QLT-013` and `QLT-015`: the constants must be justified against the measured cost of
the operation they bound, and the derivation rule must be declared before collection.

## Consequences

### Benefits

- One concrete policy with two timing constants, a lifecycle trigger, and one derivation rule,
  testable without a clock in core.
- Eligible scheduling targets and the conditions that can delay recovery are stated in product terms.
- Write pressure is bounded independently of drawing speed.
- The lifecycle flush covers the common background-death case that debounce alone misses.

### Costs and risks

- A user operation may wait for an in-flight publication; the evidence protocol bounds that wait.
- The activity must forward `ON_STOP`; forgetting it silently weakens recovery, so a device
  lifecycle test must cover it.
- The constants are product judgement backed by cost evidence, not by user studies; a later ADR may
  change them with new evidence.

## Enforcement impact

- `:core:application`: autosave capture in coordination, `publishLatestCapture` in the workflow,
  autosave projection, `RecoveryRecordPort.publishCandidate`, typed outcomes reusing the P3-03
  rollback and uncertain results; tests for coalescing, one-active-plus-one-latest, interrupted
  write at each boundary, C1 save then C2 autosave then C1 retirement, abandoned-runtime completion.
- `:adapters:persistence`: generalise the Retired writer into one ordered record writer that also
  publishes Candidate bytes with the same `sync`, `finishWrite`, and bounded read-back verification.
- `:app:android`: `AutosavePolicy` constants, the scheduler on the ViewModel scope, `ON_STOP`
  forwarding from `MainActivity`; device lifecycle test for the flush.
- `docs/quality/M3_AUTOSAVE_PUBLICATION_EVIDENCE.md`: the protocol above, its runner, and the
  single recorded result.
- `COMMAND_MODEL.md`, `PROJECT_LAYOUT.md`, and `GLOSSARY.md` gain the autosave capture and
  scheduler terms when the implementation lands.

## Migration and rollback

No stored bytes change: the envelope, generation, and exported v1 are those of ADR 0016. Rollback
before merge removes the capture, scheduler, and `publishCandidate`; a device that already holds a
Candidate written by this feature is still read by the P3-03 reader and offered by the P3-04
recovery flow, or retired by the existing conditional Retired path.

## Related

- Issue: #87
- Builds on: ADR 0014 project-format, storage, and recovery
- Builds on: ADR 0016 Android project persistence and retirement
- Supersedes: none
- Superseded by: none

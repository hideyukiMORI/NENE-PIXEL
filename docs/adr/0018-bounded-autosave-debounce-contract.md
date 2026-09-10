# ADR 0018: Bounded autosave debounce, coalescing, and lifecycle flush contract

- Status: proposed
- Date: 2026-09-10
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
`DocumentState` reference and its revision, tagged with the runtime generation. Recording replaces
any earlier pending capture; the pending set is therefore never larger than one. A committed result
that leaves the document at the last published revision records nothing. Core exposes a read-only
autosave projection (pending revision, published revision, active publication, last outcome) as a
`StateFlow` next to the existing persistence projection. Core creates no scope, dispatcher, or
timer and reads no wall time; it receives explicit `publishLatestCapture` requests from the
platform and answers with typed results.

### Publication and coalescing (core plus adapter)

`publishLatestCapture` takes the pending capture, encodes and fully validates it before
`AtomicFile.startWrite`, publishes it as a Candidate through the same ordered writer, generation,
and read-back verification that P3-03 uses for Retired, and then marks that revision as published.
A newer capture that arrives while a publication is active waits as the single latest capture and
is published by the next request; it is never lost and never published twice. A publication is one
persistence operation under the existing one-active lease:

- A user operation (Save As, load, new document, confirm, cancel) requested while a Candidate
  publication is active waits for that publication to reach its outcome; the wait is bounded by the
  publication cost measured by the evidence protocol below and never blocks drawing.
- A `publishLatestCapture` request while a user operation is active returns a typed `Deferred`
  result, keeps the capture pending, and leaves scheduling to the platform.
- A verified explicit save at a clean boundary retires the matching generation as ADR 0014 already
  requires; a pending capture whose revision equals the saved revision is dropped, a newer pending
  capture stays pending.

### Timing (platform, clock owner)

The app module owns one autosave scheduler that observes the autosave projection and applies
exactly these three rules with the constants fixed here:

| Rule | Constant | Behaviour |
| --- | --- | --- |
| Quiet window | `AUTOSAVE_QUIET_MS = 1_000` | Request publication when no new capture has been recorded for 1,000 ms after the latest one. |
| Latency cap | `AUTOSAVE_LATENCY_CAP_MS = 5_000` | Request publication no later than 5,000 ms after the oldest still-unpublished capture, even while captures keep arriving. |
| Lifecycle flush | none | On the activity `ON_STOP` event, request publication immediately if a capture is pending; the request runs on the ViewModel scope so it survives configuration changes. |

The scheduler issues at most one outstanding request at a time. After a publication completes with a
still-pending newer capture, the quiet window restarts from that completion; the latency cap keeps
counting from the oldest unpublished capture. Both constants live in one `AutosavePolicy` value in
the app module and are the only autosave numbers in the code base.

### Bounds and product consequence

With these constants, one process death loses at most the edits of the last 5 s of continuous
drawing, or of the last 1 s after a pause, plus any publication that was in flight. The writer is
invoked at most once per second while drawing pauses and at most once per five seconds under
continuous drawing, so the maximum steady write pressure is one 262 KB record per second and
normally far less. The evidence protocol below must show that one maximum publication on the
reference device costs well under the quiet window; if the measured maximum exceeds 250 ms, the
constants are re-derived before implementation as `AUTOSAVE_QUIET_MS = ceil(4 × max / 500) × 500`
and `AUTOSAVE_LATENCY_CAP_MS = ceil(20 × max / 500) × 500`, and this section is amended by a
dated paragraph rather than by rewriting the table.

### Evidence

Before Candidate publication code merges, one bounded device observation is collected under
[M3 Autosave Publication Evidence](../quality/M3_AUTOSAVE_PUBLICATION_EVIDENCE.md): one invocation on
the reference device, two fixed groups (maximum 256 × 256 document, minimum 1 × 1 document), five
unreported warmups plus twenty measured samples each of the complete Candidate publication through
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

- One concrete rule with three constants and one derivation rule, testable without a clock in core.
- Loss on process death is bounded to seconds and stated in product terms.
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

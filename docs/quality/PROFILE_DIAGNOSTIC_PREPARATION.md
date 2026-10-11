# Issue #190 Profile diagnostic preparation

Status: host preparation and analysis; live collection remains deferred.

## Purpose and boundary

The third generation's two valid invocations disagree by nine signatures and two flags. The retained
logs cannot distinguish runtime publication of intermediate autosave state, StateFlow conflation,
scheduler short-circuit paths or actual publication. UI Clean and `publishedConsumed` are not save
completion barriers. This slice prepares observation of those boundaries without changing autosave
policy, the Pencil/Undo journey, canonical Profile rules or acceptance thresholds.

Rules: ARC-001/003/004/007/011, CMD-001/008, QLT-004/006/011/012/013/015/017/018/019,
ADR 0010/0018/0024. Waivers: none.

## Selected design

Keep the observer as a source patch plus an exact input/output manifest under `docs/quality`.
Apply it only to a clean, separate worktree in the existing development lab, at the pinned commit
and tree. The preparation command must reject source drift, existing diagnostic output, an unsafe
path/link, or patch mismatch before changing files. Its fixed source/test allowlist also rejects
changes to the canonical guard, Profile, build configuration or any unlisted file. It never builds,
connects to a device, launches an app, generates a Profile, or cleans up another worktree.
A partial application remains marked
as diagnostic and is retained for inspection.

The patch instruments existing functions and uses an explicitly composed temporary observer. There
is no copied editor implementation, global mutable observer registry, new module, dependency or
shipping hook. The diagnostic patch is never merged as production source. Ordinary builds have no
new per-command, per-pixel, document-sized or preview-frame work. Diagnostic builds have bounded
recording, token lookup and log allocation; no speed claim is made.

The canonical `generate-baseline-profile.ps1` refuses the diagnostic marker or diagnostic source
before creating evidence or invoking Gradle. A clean Git status after committing the patch cannot
turn it into an acceptance artifact. The existing two-invocation comparison and accepted artifact
remain unchanged.

The [kit instructions](profile-diagnostic/README.md) describe the exact host preparation command,
event schema and limits. The pinned source already contains the refusal for the marker and all five
new diagnostic production files, so partial application is guarded before instrumentation starts.

## Observation requirements

- Producer: session, iteration, launch, Pencil input begin/end, Dirty observed, Undo input begin/end,
  Clean observed and UI-block end. These are named boundaries, not actual display-present times.
- Runtime: direct old/new projection publication, distinguishing equal assignment from changed
  values. Observe operation and autosave publication separately and retain their order.
- Scheduler: the sample it actually receives, its opaque token correspondence, flush/gate state,
  scheduling decision and request start/end. Do not reconstruct runtime events from StateFlow.
- Publication: operation/token identity, actual port call begin, typed port outcome and runtime
  completion, including cancellation/incomplete outcomes. A request alone is not a publication.
- Collector: later collection retains the pinned library's actual flush/save/stop log alongside
  these events. Do not invent per-iteration raw profiles or treat UI-block end as process stop.

Use session/iteration plus role, PID and monotonic recorder-origin identity. Producer and app use
Android elapsed-realtime nanoseconds for cross-process alignment. Within each emitting process,
sequence numbers are contiguous. The app shares one recorder across runtime, scheduler and
publication, mapping equal opaque tokens to the same bounded ordinal without serializing their
contents or hashes. The observer receives no document or pixel data. Event and token limits are
fixed in the payload; overflow invalidates the observation and never silently evicts token mappings.

No event stream proves that logging is non-intrusive. Missing or ambiguous process/token/event
correspondence is invalid evidence, not proof that autosave skipped or failed. Recorded historical
mismatch and invalid generations remain immutable.

## Host analysis contract

The canonical host analyzer is `analyze-profile-diagnostic.ps1`. It consumes one UTF-8 JSONL file
containing only the payloads of the `NeneProfileDiag` tag for one supplied session. A future capture
harness must retain the original log separately and export this file without repairing, sorting,
deduplicating or dropping diagnostic records. A malformed payload cannot be discarded as noise.
The analyzer performs no device, build, generation, restoration or network operation.

It validates the exact v1 field set, types, enum labels and bounds; duplicate JSON properties and
unknown fields/events are errors. It requires process-begin, contiguous observed sequence numbers,
nondecreasing elapsed-realtime timestamps, and consistent session/role/PID/origin/iteration identity.
Different origins identify different recorder streams, not necessarily different OS processes;
process restart/termination needs independent collector evidence. An app recorder belongs to one
iteration, and producer iteration zero is only initialization. App streams are bounded to 4,096 records each,
the single producer to 512, and the session to 15 app streams. Input is bounded before parsing.
Scheduler nanosecond values are checked in their labelled clock domain, never against `t_ns`.

The analyzer checks observed pairings for runtime assignment intent/completion, scheduler sample/
fold attempt/commit, request outcomes and publication begin/port outcome/runtime completion. An open
pair at the observed tail remains unresolved. Contradictory records, a missing predecessor inside
an otherwise contiguous stream, invalidation events, or ambiguous iteration/process binding fail
validation. It does not require scheduler observations of every runtime projection: StateFlow can
conflate values, and logging does not define a new linearization point.

Reports bind input SHA-256 and byte count to the pinned source/tree, observer patch and manifest.
Actual APK hashes may be supplied as explicit bindings; absent APK hashes remain unbound. This
records supplied identity without claiming that logs authenticate an APK. Reports contain fixed
labels, safe opaque ordinals and numeric boundaries only; validation errors name a fixed reason and
line number, never an arbitrary raw payload, exception message or user text.

A successful report means `structurally_valid_observed_prefix`, with
`tail_completeness=unproven`. It can establish a positively recorded port entry, typed port outcome,
runtime completion, branch call or producer boundary. A missing publication is always `unknown`,
never `did_not_publish`; UI Clean and `publishedConsumed` are never promoted to save completion.
No report grants Profile acceptance, ordinary-product timing evidence, or a mismatch-cause verdict.

## Prospective bounded diagnostic plan

This plan is prepared while hide's collection hold remains active; it grants no live execution.
The technical hypothesis is that the observed rule difference can be associated with differing
runtime/scheduler/publication events during the existing collector lifecycle. Association alone
cannot prove causality, especially because instrumentation perturbs timing.

The proposed diagnostic is one invocation of the existing patched producer, with its unchanged
Pencil/Undo journey, `maxIterations=15`, `stableIterations=3` and `strictStability=true`. Its wall-clock
cap is 1,800 seconds. There is no retry or correction allowance in this plan. Invalid, timed-out or
inconclusive output is retained and stops the run; a correction needs a new prospective identity and
plan. This is separate from the canonical two-invocation Profile acceptance operation, whose guard
continues to refuse the diagnostic source. Diagnostic profiles must never replace canonical bytes.

Before an executable live entry point can be admitted, it must bind all of the following:

- Exact source/tree and unchanged 17-file observer manifest/patch, prepared marker and posthashes.
- Exact app/test APK SHA-256, toolchain and AndroidX Benchmark 1.5.0 identity; no invented APK hashes.
- A fresh evidence directory and session, the named device and actual installed APK inventory.
- An original-data preservation/restoration plan that admits the retained guards and current state
  prospectively; the stopped restoration and its original manifest remain immutable.
- Original collector log, diagnostic JSONL, producer result/exit and all source hashes. Retain actual
  collector flush/save/stop messages; do not manufacture per-iteration raw profile attribution.
- An export that keeps original per-process sequence order. Stream validation and positive-only
  conclusions above are the selected response to the unprovable logcat tail; no terminal marker,
  arbitrary sleep, private collector fork or new data writer is added to claim completeness.

Refuse before native work when the hold remains active or any identity, preservation or harness
binding is absent. Stop on device disconnect, artifact drift, observer invalidation, unexpected
process correspondence, collector failure or timeout. Retain evidence before considering any next
step. Stop destructive follow-up after an uncertain restore result and preserve both original and
measurement copies. A successful diagnostic remains subject to separate interpretation and does
not authorize another canonical generation or #145 performance collection.

The hook timestamps name their recording boundaries. A scheduler on another thread can observe a
StateFlow value between its assignment and a following log call; logger sequence is not a new
linearization authority for application state. Runtime records retain the values and equality result
at the canonical assignment boundary, and fold attempts are distinguished from the attempt that
the existing scheduler update commits.

Logcat alone has a remaining tail-completeness limitation: the collector kills the app outside the
UI block, so the payload cannot emit a guaranteed final app event. Contiguous observed sequence
numbers do not prove that no later event was lost. Live admission therefore remains closed until
the collection plan establishes a sufficient terminal/completeness check or explicitly narrows its
conclusion to positively observed events. In particular, an absent publication event cannot prove
that no publication occurred. Do not add a collector fork, arbitrary sleep or private-data writer
to hide this limitation in the preparation slice.

## Host verification plan, fixed before execution

| Changed behavior | Narrow check and regression detected |
| --- | --- |
| Preparation and admission scripts | Synthetic lab repositories: exact pin/hash application; changed/dirty input and link/path rejection; refusal before native Gradle work even after diagnostic commit |
| Diagnostic recorder and hooks | Focused JVM contracts for opaque-token equality, bounds, ordered runtime events and publication outcomes; relevant module compile/static checks for patched source |
| Scheduler observation | Existing schedule/scheduler contracts plus focused diagnostic tests, because hook placement could alter evaluation or ordering |
| Producer timestamps and launch metadata | Producer compilation/static analysis only; no connected test or generation |
| Contract and ADR | `validateDocumentation` and `git diff --check` |
| Host event analyzer | Synthetic valid prefixes and adversarial schema/sequence/identity/pairing/overflow inputs; absence remains unknown, raw private text never reaches reports, and no native callback exists |

Normal caches and daemon settings apply. Passing checks are reused by identity across packaging,
review and push. The Draft PR does not trigger a local full suite. Device tests, APK assembly/install,
performance collection and Profile regeneration are outside this slice. A host compile does not
claim device correctness or diagnose the actual mismatch.

## Resumption conditions

Before any live diagnostic, reconcile Issue #190, this ADR and the executable harness. Pin the
production source, patch/observer, app/test APKs, toolchain and unchanged canonical profile. Set a
finite run/correction budget and explicit refusal/stop rules. Bind actual collector logs and verify
stream completeness; never infer an unobserved publication from a helper rule. Resolve the existing
ProfileInstaller-marker restoration admission using preserved original guards and a prospective
inventory/restoration plan. Do not repeat the interrupted restoration unchanged.

Host preparation does not grant a generation attempt. Profile acceptance and #145 candidate pin
remain blocked until their own prerequisites are met. #163 follows #145; #168 remains after M4.

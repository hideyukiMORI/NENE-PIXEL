# ADR 0042: Canonical workspace preview playback

- Status: accepted
- Date: 2026-10-12
- Issue: #217
- Affected rules: `ARC-001`, `ARC-004`, `ARC-007`, `ARC-008`, `ARC-011`, `CMD-001`,
  `CMD-002`, `CMD-009`, `CMD-010`, `KOT-003`, `KOT-007`, `KOT-013`, `QLT-006`,
  `QLT-011`, `QLT-012`, `QLT-016`, `QLT-017`, `QLT-018`, `QLT-019`

## Context

ADR0041 / #216 establishes saved chronological frames, shared palette and exact frame durations
in project v4. A useful preview must consume those values without changing editing focus or
creating a competing frame counter, time source, document mutation or persistence representation.
This decision consumes that frame model for preview only; #145 collection remains at the
owner-directed M5 exit.

## Decision

### State and authority

WorkspaceState owns one immutable PlaybackState containing ephemeral mode and an optional active
session. Mode is Loop by default; Loop and Once are the only choices. Mode follows existing
session-display preference carry across document installation in the retained runtime. The active
session, token, sampled time and progress never survive installation, process recreation or
persistence. No document field, schema, command, history item, clean checkpoint or autosave capture
is created by Start, Sample, mode choice or Stop.

A session owns a fresh opaque identity token and a bounded immutable timing plan: ordered FrameId
and cumulative duration endpoints derived once at Start from the committed document. It need not
retain pixel snapshots or another DocumentState. Tokens use object identity, not wall time, random
values or a reusable counter. The current source cannot change while the session survives: every
Applied document command/Undo/Redo, final Switching and owner installation ends it centrally.
NoEffectiveChange and rejected direct commands leave it unchanged.

The existing editTarget frame/layer/palette remains the editing authority during preview. A
separate derived displayedFrameId is the active playback frame, otherwise the editing frame.
Canvas and actual-size FrameImageInputs use that same displayedFrameId. Layer/frame operations,
PNG captures and command source admission keep the existing edit target. A pulse never dispatches
SelectFrame. Stop therefore restores the editing picture by projection without a focus-repair
command or a remembered second edit position.

### Timing and bounded catch-up

Start requires at least two saved frames, no active palette draft or pending raster-import form,
and no final Switching. It cancels unfinished drawing/selection, closes/idles quick-select and
rests underlay adjustment through the existing canonical reductions. It starts at the selected
editing frame. The first valid platform sample anchors time; the selected picture is already
visible at Start and remains so at that first sample.

One monotonic platform source supplies signed Long nanoseconds explicitly. Core reads no system
clock. The admission contract for consecutive accepted samples is a forward difference from 0
through Long.MAX_VALUE. Compute two's-complement `now - previous`; a negative result denotes
backwards time or an ambiguous gap of at least 2^63 ns. This admits a signed-boundary wrap and
negative origins without comparing absolute timestamps or using abs. Equal samples are valid.
Every accepted sample updates the previous sample even if the displayed frame stays unchanged.

For at most 16 frames, cumulative duration is at most 960000 ms (960000000000ns). The initial
position is the prefix sum before the selected frame. Intervals are half-open [start,end): exact
end advances to the next frame; exact total end stops Once. Once proceeds from the selected frame
through the final frame. The final frame has its full logical saved interval, including when
Start selected that frame; a blocked UI is not promised to present every skipped interval.

Loop reduces delta modulo the bounded full-cycle duration before adding to the bounded position,
then wraps once. Once compares delta with the bounded remaining-to-end before adding. Resolve a
late sample directly with at most 16 endpoint comparisons, without a loop per missed frame/cycle.
No unbounded elapsed accumulator, absolute deadline addition, per-frame raster preparation or
catch-up command replay is allowed. An active result always has strictly positive remaining
nanoseconds in its current interval, at most 60000000000.

### One canonical operation and one platform driver

Playback actions are a family on the existing WorkspaceAction/reducer path. Public controls Start,
Stop, StopIfCurrent(expectedToken) and SetPlaybackMode use that route. The explicit-sample action
may be internal, like existing source-admitted internal workspace actions. A single public
EditorRuntime.advancePlayback(token, nanos) provides the consuming platform seam and serializes
through the existing runtime lock. It invokes the sole reducer and returns a typed pulse result:
Continue(token, remainingNanos), Stopped(completed or clockDiscontinuity), or Stale.

Stale token validation precedes timing work and never changes a newer session. A backwards sample
can be rejected by the reducer without changing state, then mapped to canonical StopIfCurrent
inside that same runtime transaction. The public pulse operation returns a typed stopped fault.
It must never return a generic Rejected carrying a changed workspace. A driver must not leave a
Playing display with no future wake after a clock fault.

One Compose-owned coroutine samples withFrameNanos, calls that operation, delays by
ceil(remainingNanos/1000000) milliseconds and requests the next platform frame sample. Its only
session data is the token and disposable returned delay: it owns no index, elapsed timeline or
independent deadline. A late wake is resolved by core. Delay cancellation and token changes end
the old coroutine. Every asynchronous cleanup/disposal Stop uses its expected token; old cleanup
must not stop a new session started before that cleanup runs. Explicit user/lifecycle Stop
intentionally targets the current session. No clock is retained in the ViewModel.

The render projection contains only visible playback mode/session/frame information, not the
previous sample or fractional progress. Same-frame progress must not publish a different render
value, rebuild a composite or transfer a Bitmap. Reuse the existing active-only frame-aware caches;
replace their selected preparation at an observed frame boundary. Equal StateFlow projection
suppresses downstream publication but does not erase the real small workspace/projection cost.
Avoid unnecessary projection construction where the existing seam can reuse it coherently.

### Editing, transport and lifecycle

The incompatibility policy belongs in the common reducer, including RuntimeTransaction internal
reductions. A guard only at public EditorRuntime.reduce or in UI is insufficient. Direct pointer
Begin/Extend/Prepare and SetViewport while Playing are typed-refused, including continuation of
a gesture that began before Start. An actually successful incompatible workspace
transition ends playback together with that transition: palette/selection begin, accepted pending
raster import, quick-select menu/eyedropper arm, underlay adjustment, edit frame/layer/tool change.
Rejected and truly Unchanged actions retain their established atomicity. Appearance and actual-size
window controls can continue without ending preview; cancellation cleanup cannot stop a replacement
session by accident. Define this small set once rather than parallel public/internal guard trees.

UI editing intent may explicitly Stop before its existing operation, even when that subsequent
operation is unchanged or refused. These are two separate actions. Shared successful-command
follow-up stops before selection/focus reconciliation. RuntimeOwnerEffect.CancelPreview at final
Switching stops immediately, as does replacement; do not wait for a slow load to install new owners.
An async accepted SetPendingRasterImport stops together with installing its form, so direct/internal
completion cannot create a competing editing form during preview.

Any canvas Down while Playing stops and returns Ignored. Existing ViewportPointerSession then
stays Suppressed through all fingers up, consuming that whole gesture including a second finger.
A fresh gesture can draw, pick, select or pan/zoom. An already-running two-finger transform is
refused/suppressed on its next update; explicit viewport controls Stop before their action.
Ordinary Reduced-to-Accepted conversion would
incorrectly start Drawing after Stop and is prohibited for this boundary. No new gesture-state
owner or delay-before-classification redesign is added. Actual-size dragging uses its existing
separate input owner and may continue.

Opening file/edit/modal controls stops before presenting them, making the captured editing target
visible. Ordinary committed save/autosave still captures all frames and can finish while preview
runs; no blanket persistence lease lock is introduced. PNG export still captures the edit frame at
its existing begin-time runtime boundary. PNG layer import keeps its original captured frame and
anchor; playback neither retargets nor discards it. Entering a pending form stops playback.

The Compose playback adapter observes its existing LocalLifecycleOwner's ON_STOP and dispatches
the current-session Stop through its bound callbacks/runtime. A lifecycle Stop is an explicit
termination of the current owner's foreground session, not old-token asynchronous cleanup. The
observer is keyed by lifecycle owner and callback identity, remains installed while Frames is
composed even when stopped, and is not replaced for every playback token. Otherwise Start(B)
immediately before ON_STOP could survive an old observer's StopIfCurrent(A), with B's recomposition
delayed until RESUMED when the past ON_STOP would no longer be visible.

The same adapter owns the frame-clock coroutine; there is no second MainActivity or ViewModel
playback observer. LocalLifecycleOwner and LifecycleEventObserver are already on presentation's
locked 2.9.4 compile classpath; no dependency change is required. A lifecycle-keyed DisposableEffect
registers that observer before inspecting current lifecycle state, then removes it on disposal; a separate token-keyed DisposableEffect performs only token-checked
disposal cleanup. The sampling coroutine has no duplicate unconditional finally Stop. Android UI
Start admits only a lifecycle at least STARTED. Observer setup and driver entry/sample admission
below STARTED end an already-present token before sampling; recheck after every suspension and before advancePlayback. Core itself remains platform-independent;
it can be driven by synthetic samples without an Android lifecycle.

Background, rotation/disposal, process recreation and owner replacement do not resume playback or
catch up elapsed background time. MainActivity's existing ON_STOP autosave/underlay flush remains
unchanged. A stop itself does not make either memory dirty. Keeping the adapter inside the Frames
surface gives pane disposal the same cleanup and lets the self-targeted ComponentActivity native
fixture exercise the identical production ON_STOP/driver path without opening or resetting the
normal application's persistent data.

### Interface

Play/Stop, Loop/Once and the displayed ordinal belong only in the existing secondary Frames pane.
Keep Stop reachable at short height with the existing fixed footer/body scrolling pattern. Explain
why a one-frame work cannot start. Mode change during playback stops before adopting the new mode.
The editing frame selection remains identifiable separately from the displayed ordinal.

First Android Back during playback stops and stays on Frames. Later Back follows the existing
Frames→Layers→chip hierarchy. Explicit pane close/back arrow, row selection or More/duration
entry stops before navigating/opening. Leaving/hiding the Frames pane never leaves preview running.
No extra always-visible or transient work-area stop control is justified because the sole preview
surface keeps its stop control visible for the whole session. Existing dialog/window Back priority
continues. Localize controls and descriptions in EN/JA/zh-Hans; add no timeline dock, FPS override,
Pause, scrubber, thumbnail cache or independent per-frame palettes.

### Verification and phase workload

Select tests after the coherent diff and record each regression before executing, under QLT011/012.
Synthetic host samples cover unequal boundaries, start-middle/start-last Once, exact end and Loop
wrap, equal/backwards/negative-origin/sign-wrap/Long.MAX_VALUE delta, stale sample and old cleanup
after restart, mode/source/install/cancellation, internal competing entries and final Switching.
Assert no document/revision/history/checkpoint/autosave changes on display pulses and exact edit
focus preservation. Test same-frame cache/reference reuse and both picture consumers.

Use a small isolated native journey for Play/Stop/frame display, synchronized actual-size, the
whole first canvas gesture, Back/pane-close and lifecycle stop. Real-clock completion uses bounded
condition waiting, not an arbitrary sleep or an unrelated drawing tour. Preserve installed user
work/recovery. Reuse unchanged #216 storage/command/capture evidence with its original identity.
Required final ready-PR CI remains canonical check plus Android assembly; no local full replay.

The lifecycle case uses the existing self-targeted presentation test APK and an Android
ComponentActivity with the production FramePanel/playback adapter. ActivityScenario moves it to
CREATED while a token is running, then RESUMED; assert that token ended, edit focus/document/history
are unchanged and playback remains stopped. Separately remove/recreate the composition to verify
token-checked disposal, and cover the below-STARTED start race with the same lifecycle owner. No
normal-app recovery-clearing test, cloned lifecycle stop implementation or new application ID is
needed for this contract.

Include Start(B) immediately before ON_STOP while the last composed driver still has token A:
background composition need not run, and resume must find both canonical state and displayed
projection stopped. Stop/StopIfCurrent cleanup actions remain admitted through the public runtime
even during final Switching or a palette draft; idle/stale cleanup is Unchanged. Start and other
editing admission still follow their existing busy/draft exclusions.

Register per-sample and per-observed-frame costs in #145 before merge: bounded timing metadata,
small workspace/projection allocations, active-frame cache replacement, N-pixel selected stack
composition, and retained references. No full-document pixel scan is added to a same-frame pulse.
No feature-level performance collection, Profile regeneration or forced cold build. M5 protocol,
fixture and harness reconciliation still precedes measurement acceptance.

## Consequences

Preview is deterministic for explicit samples while accommodating skipped platform frames. Stored
frame timing remains the only duration authority; transient playback never alters a saved project.
The simple stop-and-consume gesture requires a second gesture to edit or zoom, and leaving the
Frames pane ends playback. Once restores editing focus instead of holding the last playback frame.
Those limits keep editing targets visible and avoid another timing/input owner.

No format, dependency, module, external transport API, budget, quality gate or waiver changes.
Onion-skin, opacity, animated/sprite-sheet export, audio, tiles/maps and playback scrubbing remain
separate decisions. This acceptance is not a performance verdict or M4/M5 exit declaration.

## Rejected alternatives

- A Compose-local frame/elapsed counter or per-pulse SelectFrame would create a second owner or
  mutate editing focus and invalidate command/capture meaning; workspace samples are the sole path.
- Fixed-rate ticking, unbounded catch-up loops and absolute deadlines add unnecessary samples or
  overflow risk. Bounded relative interval arithmetic consumes only saved timing and observed wakes.
- Holding the final displayed frame after Once, background resume and Pause require additional
  retained display/session policy. Once stops and restores the existing edit target.
- A persistent or transient work-area Stop overlay is unnecessary while Frames owns the visible
  controls and leaving it stops. Touch classification delay for pan during preview would enlarge
  input policy; the first whole canvas gesture stops and is consumed instead.
- A separate MainActivity stop route plus test-host imitation would duplicate the lifecycle adapter.
  The production Compose adapter and its existing lifecycle owner cover both app and isolated host.

## Enforcement impact

Add the consuming playback value/action/result types in core/application, one reducer/runtime
operation, and the presentation projection/driver/controls. Keep domain, pixel-engine, format and
persistence ownership unchanged. Update this ADR, architecture, command model, layout, glossary,
interface inventory, development plan and current README together. Deterministic synthetic sample
tests, targeted projection/cache/input contracts and isolated native lifecycle/UI checks enforce
the decision. Existing compiler, ktlint, detekt, Android lint and required CI gates remain intact;
no suppression, baseline or waiver is introduced. Cost/workload registration is required by QLT019.

## Migration and rollback

There is no saved-data migration: v4 frame order and duration already exist. New workspaces start
stopped with Loop; installed documents carry mode only. The implementation adds the consuming
Workspace route and replaces the prior no-playback presentation in one focused change. Rollback
removes playback controls, adapter, projection and core actions/state together, preserving v4 data
and all existing frame editing paths. Never keep a fallback UI counter or parallel playback engine.

## Related

- Issue: #217; prerequisite #216 / ADR0041
- Performance gate: #145 / ADR0031
- Supersedes: no ADR; consumes the deferred playback portion of ADR0041

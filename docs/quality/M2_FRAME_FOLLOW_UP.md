# M2 Actual-app Frame Follow-up

Status: schema-v7 tooling and historical results retained; new collection blocked pending prerequisites and a new evidence-backed candidate.

## Current-main integration boundary

This document integrates the truthful frame protocol and historical result ledger onto current
`main`. It does not integrate or recreate the generated Baseline Profile producer, profile artifact,
or its build dependencies. The retained ADR 0010 profile decision and ADR 0013 custom-View experiment
record remain on the preserved #62/#54 branch until that profile-dependent change is curated as one
coherent follow-up. Issue #62's fresh schema-v1 producer provenance is still pending.

The four scripts documented here have one retained canonical path. Their offline protocol and visual
fixtures are usable without a device. Actual collection is not currently authorized or ready from
`main`: it additionally requires an accepted and integrated profile prerequisite, an exact signed
APK/profile/source identity, a live Issue whose protocol agrees with the harness, and a newly fixed
candidate and budget under QLT-013 through QLT-015. Historical source, APK, trace, and local artifact
paths below are evidence records only; they are not relabelled as current-main artifacts or permission
to rerun a consumed experiment.

## Scope

Issue #44 found one correct but late first Pencil frame after a fresh install: 44.725066 ms in the
debug build and 33.597945 ms in a locally signed release-like build on profile
`NENE-P2-ALLDOCUBE-IPL80MP-A16-API36`. Both observations had an uncontrolled ART compilation
state, no interaction warmup, and only one frame. They are valid diagnostics, but they are not a
percentile batch and cannot by themselves satisfy or fail the accepted P2 frame protocol.

This follow-up first corrects that measurement boundary. It changes no renderer, command,
workspace, pixel, history, product limit, dependency, Gradle plugin, module, or public API. A
production optimization is permitted only if the fixed batch below still identifies redundant
work.

Android recommends measuring UI interactions out of process with Macrobenchmark and
`FrameTimingMetric`, and recommends compiling the app to a known state such as `speed-profile` or
`speed` before measurement. The platform also documents that `dumpsys gfxinfo ... framestats`
provides detailed recent frame timings. This focused M2 follow-up uses that dependency-free
platform route rather than adding a benchmark module or changing the accepted test toolchain:

- [Overview of measuring app performance](https://developer.android.com/topic/performance/measuring-performance)
- [Capture Macrobenchmark metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)
- [Slow rendering](https://developer.android.com/topic/performance/vitals/render)

## Prospective schema v7 protocol

Issue #54 adopts `nene-pixel-m2-actual-app-frame-v7` as the only prospective acceptance schema.
Schemas v1 through v5 and physical-present v1/v2 remain immutable historical evidence; the sole v2
collection is exhausted and the executable harness rejects another physical-present collection.
Schema v6 was reviewed before collection and retired with zero invocations: its operation latency
started at `DOWN` while the harness intentionally held preview for 100 ms, making both the unchanged
33.33 ms decision threshold and the 100.0 ms gross boundary structurally unreachable. No v6 artifact
or result exists. No further v7 collection is authorized until one source-attributed candidate with a fixed
hypothesis exists.

The operation is one Pencil gesture with separately injected `DOWN` and `UP`. `DOWN` begins the
operation and presents only disposable preview; `UP` commits through `ApplyStrokeCommand`. The
harness resets and reads `gfxinfo` independently around each phase, retains every valid frame in both
groups, and rejects a phase with zero frames, a flagged frame, or a mismatch between reported and raw
cardinality. One committed-result sample starts at the earliest `HandleInputStart` in its `UP` group
and completes at the latest `FrameCompleted` in that same group after the committed dirty/Undo-enabled
UI is observed. The earliest `DOWN` input and full DOWN-to-committed-result journey are retained as
separate diagnostics. The fixed waits are 100 ms after `DOWN` and 350 ms after `UP`; the intentional
preview dwell is therefore not misreported as application commit latency. Reset, report reads,
correctness UI queries, and Undo are outside the measured commit result.

Two populations stay distinct:

- per-frame deadline population: every preview and commit frame, with no one-frame assumption;
- committed-result population: one conservative UP-input-to-latest-commit-frame duration per Pencil
  gesture;
- full-journey diagnostic: earliest DOWN input to the same latest committed frame, including the
  intentional preview dwell, with no acceptance threshold.

Nearest-rank p95 is rank 10 (the maximum) for 10 diagnostics and rank 48 for 50 decisions. The
release-like `speed-profile` decision keeps the unchanged thresholds: per-frame platform-overrun p95
at most 0 ms, p99 at most 16.67 ms, and UP-input-to-committed-result p95 at most 33.33 ms.
Debug/full-AOT remains diagnostic only.

A diagnostic can return only `gross-regression`, `invalid`, or `inconclusive`; it never returns
acceptance PASS. The predeclared gross boundaries are any per-frame overrun above 33.34 ms or any
UP-input-to-committed-result duration above 100.0 ms. Equality at either boundary and every non-gross valid diagnostic
are inconclusive and proceed according to the fixed experiment plan; zero misses are not required.
The earlier generated-profile +0.023368 ms diagnostic remains its historical FAIL and is not
reinterpreted under these rules.

For one exact baseline/candidate pair, the finite order and budget are:

1. baseline diagnostic (10 operations);
2. candidate diagnostic (10 operations);
3. candidate decision (50 operations), only after a valid non-gross candidate diagnostic;
4. baseline decision (50 operations), under the same final inputs and environment.

Each slot is collected at most once. One replacement is allowed only for an invocation proven
invalid before the first measured `DOWN`; invalidity after sample collection begins consumes that
slot. All outputs use distinct no-overwrite directories. Correctness failure, artifact/profile or
environment mismatch, frame association ambiguity, and missing committed UI evidence are invalid,
not application-performance failures. Candidate selection still requires a separately documented
hypothesis, expected affected cost, exact source/APK/profile identity, and stop conditions before
slot 1. The executable harness fixes those fields in schema
`nene-pixel-m2-frame-experiment-v2`, including distinct baseline/candidate source commits and APK
SHA-256 identities. It derives the no-overwrite slot/attempt directory, requires the preceding slot's
recorded verdict before continuing ABBA, and permits attempt 2 only when attempt 1's `run-state.json`
records zero measured `DOWN` events and `invalid-before-samples`. `RunKind`, `CandidateRole`,
`ComparisonSequenceIndex`, `Attempt`, and the corresponding fixed sample count are mandatory. A
candidate decision FAIL or either gross diagnostic stops the remaining sequence. Preflight rejection
before a slot directory is opened consumes no attempt; every opened invocation records its state.

### Generated-profile v7 comparison result

The fixed comparison used baseline source `0febda425f894f016688922bb93710746fb6ba57`
(APK SHA-256 `93a5fc94e6fe1dfabb1ec9e155750e73be6f8b189d18d12c4769da325056176e`)
and generated-profile candidate source `efb8c36003a1c62e958da92cf4fb28c2b35dc261`
(APK SHA-256 `359a8f5a6975afae6f29e8680a69ae14f28164db72d36b250225f03d8f3de959`).
Both release-like APKs passed exact revision, signature, packaged-profile, installation, compilation,
environment, and final correctness checks. A first baseline attempt encountered the lock screen before
the first measured `DOWN`; it is retained as `invalid-before-samples` with zero measured inputs and
consumed the sole permitted replacement.

The replacement baseline diagnostic and candidate diagnostic each retained 10 operations and exactly
20 valid frames: one preview and one commit frame per operation. Their all-frame overrun p95 values
were 1.102854 ms and 0.953660 ms respectively, and their UP-input-to-committed-result p95 values were
11.981423 ms and 10.356115 ms. Both diagnostics are inconclusive as required; neither is relabelled
as acceptance evidence.

The candidate decision retained all 50 operations and all 100 valid frames, again exactly one preview
and one commit frame per operation. It passed correctness and environmental checkpoints with zero
fatal or ANR observations. The unchanged decision thresholds produced `FAIL`: all-frame platform
overrun p95 was 0.670013 ms, above the required 0 ms, while p99 was 1.512354 ms and
UP-input-to-committed-result p95 was 10.030615 ms. The full DOWN-to-commit journey diagnostic p95 was
397.004269 ms and is not an acceptance metric because it includes the intentional dwell.

The complete retained population localizes the miss distribution without claiming physical present.
Preview frames had overrun p95 -1.382467 ms with one miss; commit frames had overrun p95 0.886609 ms
with nine misses. The generated-profile architecture decision remains retained on the #62-dependent
branch and is not an integrated or usable current-main build path. Its earlier apparent equal-hash
reproducibility event used stale tracked output; fresh schema-v1 producer provenance is still pending
and is not retroactively PASS. The unchanged performance result
does not satisfy Issue #54. Per the predeclared stop rule, the baseline 50-operation slot is not run
and the candidate decision is not retried. Historical schema-v5 and physical-present results remain
unchanged.

Offline decomposition of the retained raw rows further narrows the unresolved boundary. Preview
versus commit p95 was 0.421730 / 2.249538 ms for animation-to-traversal, 0.708653 / 1.775230 ms for
draw-to-sync-queued, and 4.822577 / 5.315462 ms for issue-to-swap. These are independent percentile
ranks and are not added together. The earlier source-instrumented trace bounds the complete
`PixelCanvas` section at about 0.236 ms, including only about 0.136 ms for opaque Bitmap projection,
while the retained physical-present v2 population reports every SurfaceFlinger frame on time. Later
offline ordering analysis places its one `CreateGraphicsPipeline` event in the preview frame rather
than the following commit frame; it still does not identify a source rectangle.

That evidence does not support another in-scope production candidate large enough to close the
0.886609 ms commit-frame p95 miss. Reintroducing whole-Bitmap mutation, the rejected dirty-label or
history observation implementations, or a combination of them would repeat exhausted candidates;
incrementally patching the Bitmap could remove only a subset of the already bounded 0.136 ms
projection and cannot justify its additional transition contract. A renderer replacement or a
changed frame budget would require a new product/architecture decision. Until such a decision or new
source attribution exists, speculative production edits and further device collection remain stopped.

### Focused current-source attribution v1

The remaining permitted question is narrower than the exhausted acceptance and physical-present
experiments: on the generated-profile implementation with `DOWN` and `UP` separated, which current
source operation accounts for commit-only animation/traversal, draw recording, and RenderThread work,
and is any intermittent graphics-pipeline creation tied to preview or commit? The older
source-instrumented trace used another source/profile/configuration, so its 0.236 ms `PixelCanvas`
observation is historical context rather than a current upper bound.

Schema `nene-pixel-m2-source-attribution-v1` uses one exact-revision, locally signed release-like APK
containing temporary `android.os.Trace` sections around command preparation/execution, render-state
projection, dirty/history nodes, and each canonical `PixelCanvas` recording stage. It changes no
rendering value, order, state path, threshold, or dependency. The physical profile, packaged-profile
`speed-profile` compilation, five Pencil/Undo warmups, 16 x 16 document, top-left Pencil position,
100 ms preview dwell, 350 ms commit wait, Undo clean checkpoint, and environment/correctness checks
match v7. This is not a v7 rerun: its population is one intrusive 10-operation trace used only for
cause attribution, never acceptance or physical-present latency.

One invocation is authorized after all preflight checks. It retains every preview/commit gfxinfo row,
frame-timeline ID, the raw Perfetto trace, custom and platform slices, thread scheduling, environment,
UI correctness, exact source/APK/profile identity, and integrity statistics. The trace session is
bounded to 30 seconds. Empty/incomplete trace, data loss, missing custom sections, ambiguous phase
association, incorrect UI state, or an environment mismatch is invalid. A failure after trace start
consumes the only invocation; preflight failure before trace start may be corrected without consuming
it. If no stable controllable source slice is identified, collection stops with no candidate. No
candidate comparison is authorized until this attribution result supports a distinct implementation.

The sole invocation collected all 10 preview and commit gfxinfo groups, but the 30-second Perfetto
stop-trigger timeout expired before the host sent its final trigger. Perfetto finalized a zero-byte
trace, so no custom or platform slice exists. This is `invalid-after-start`, consumes the complete
source-attribution-v1 budget, and is not rerun. The collector had also failed to reject a zero-byte
pull before invoking Trace Processor; that fail-closed check is corrected after the retained run and
does not create a replacement entitlement. The valid gfxinfo groups remain intrusive diagnostic data
and are not used as acceptance evidence.

Existing physical-present v2 data still permits a new offline distinction without recollection. In
its only two-frame sample, the first `DrawFrames` begins before ACTION_UP delivery and contains the
5.330539 ms graphics-pipeline creation; commit recomposition begins afterward and produces the second
frame without pipeline creation. Across ordinary single frames, main-thread `Compose:recompose` is
0.50-1.37 ms, `AndroidOwner:measureAndLayout` 0.58-0.67 ms, and `AndroidOwner:draw` 0.65-0.82 ms,
whereas RenderThread `DrawFrames` is 5.26-6.11 ms. Its long `QueueSubmit` slices are 3.14-4.31 ms and
spend 2.85-3.56 ms sleeping; individual text, rounded-rectangle, fill, texture, and grid operations
are each much smaller. This supports a graphics-work submission hypothesis, not a main-thread Bitmap
projection hypothesis.

### Next candidate: offscreen retained static chrome

The earlier rejected static-chrome candidate used default `graphicsLayer()` behavior: it retained a
display list but still submitted the unchanged title, active swatch, palette, and tool controls as
their normal text and shape operations on every frame. The new candidate changes only that same
unchanged upper group to `CompositingStrategy.Offscreen`, so it is rasterized into one disposable
presentation layer and later frames composite the retained texture instead of replaying its
`AtlasTextOp` and `CircularRRectOp` population. This is a distinct GPU-submission hypothesis based on
the newly isolated QueueSubmit wait; it does not restore or combine the prior label, history,
elevation, Bitmap, alpha, or observation-scope implementations.

The layer remains a Compose-owned disposable `RenderCache`, not document state or another backend.
Exact child order, sizing, alignment, semantics, click targets, colors, opaque pixels, canvas bounds,
viewport/grid order, command/action ownership, history, and dirty state remain unchanged. Risks are
extra layer memory, clip/alpha differences, and a one-time rasterization cost. Existing UI behavior
tests plus before/after physical screenshots must reject any semantic, geometry, or visible-pixel
difference.

If those correctness checks pass, one new schema-v7 experiment compares baseline source
`efb8c36003a1c62e958da92cf4fb28c2b35dc261` with the exact candidate in the unchanged four-slot order:
baseline diagnostic 10, candidate diagnostic 10, candidate decision 50, then baseline decision 50.
The experiment identity is `nene-pixel-m2-offscreen-static-chrome-v7-01`; one invalid-before-DOWN
replacement and all v7 thresholds, gross boundaries, environment checks, phase association, and stop
rules remain unchanged. Candidate decision FAIL stops slot 4. No earlier candidate artifact is reused
as the new candidate result, and no acceptance collection begins before exact candidate source/APK
identity and the visual oracle are fixed.

### Offscreen candidate result

The implementation was fixed at source `f0bb36da2af1447673c5c70eee1f0dcce3e60865`; its clean detached
release build produced a locally signed APK with SHA-256
`52dc0c983dddf7d46eaeb83036cf0fffa65be65c0be01aa2b43484945e02038b` and valid v2/v3 signatures.
The focused Compose host/style/static checks and both module Android instrumentation classes passed.
The exact candidate APK also passed the fresh-document accessibility oracle: Pencil was initially
selected, dirty/Undo/Redo were clean/disabled/disabled, the top-left Pencil commit changed them to
dirty/enabled/disabled, and the canvas bounds remained `[41,688][1159,1806]`.

The predeclared visible-pixel oracle nevertheless failed. Against the retained exact-baseline final
screenshot, the app crop `x=0..1199, y=72..1847` contained 1,442 differing pixels, all within the
offscreen upper controls at `x=304..896, y=72..374`. Every changed channel differed by at most one,
while the document controls and canvas region from `y=376` onward were byte-identical. Small as the
difference is, the fixed oracle required zero changed app pixels and does not permit a post-result
tolerance. Therefore no timing slot in `nene-pixel-m2-offscreen-static-chrome-v7-01` ran, the
offscreen production edit is reverted, and the result is retained as a correctness rejection rather
than a performance verdict. It is not retried under another name.

### Accepted bounded-control oracle and revised candidate

[ADR 0012](../adr/0012-bounded-static-controls-color-tolerance.md) records hide's 2026-09-06
decision without changing the preceding historical result. The
new oracle identity is `nene-pixel-m2-static-controls-color-tolerance-v1`. Its allowed pixels are
the union of accessibility bounds for active-color, palette, and Pencil/Eraser operation controls;
the title is excluded from both the allowed region and the revised offscreen layer. Matching node
text, descriptions, class, bounds, enabled/clickable/checked state and exact alpha are required.
Within that union only, RGB channels may differ by at most 1 and no more than 1,442 pixels may differ.
Every pixel outside it from the title top through the canvas bottom is exact, including the complete
canvas bounds. This is an Issue-specific prospective admission oracle, not a general UI tolerance or
a retrospective PASS.

The revised experiment identity is `nene-pixel-m2-offscreen-selection-controls-v7-01`. It compares
the same exact generated-profile baseline with a new exact candidate that offscreen-retains only
`SelectionControls`. Hierarchy equality covers every text-bearing, described, clickable, or button
app node; an undescribed non-actionable render-layer container does not create an accessibility
meaning and is excluded. The new candidate requires its own source/APK hash; the prior full-upper-group
APK and screenshot remain evidence for the rejected oracle and are not relabelled. After the new
oracle passes, the unchanged v7 ABBA 10/10/50/50 population, `speed-profile` compilation, warmups,
phase association, gross boundaries, invalid recovery, absolute thresholds, and candidate-FAIL stop
apply. Permission to measure does not select the candidate, and relative improvement cannot replace
absolute threshold PASS.

The revised candidate is fixed at source `06f6af743d3821ff34e2ddd5c452f960c36a67d5` and locally signed
release-like APK SHA-256 `016a6459f62f6893ae6984313fb1a7a5608791af8e2d3c8430718e00e4cfdc29`.
The APK embeds the exact source revision, has valid v2/v3 signatures, and retains the unchanged
generated text Baseline Profile source; its compiled profile entries are candidate-APK-specific and
are not relabelled as the baseline binary profile. The baseline remains source
`efb8c36003a1c62e958da92cf4fb28c2b35dc261` / APK SHA-256
`359a8f5a6975afae6f29e8680a69ae14f28164db72d36b250225f03d8f3de959`.

Before timing, the exact candidate passed the fresh-document Pencil/dirty/Undo/Redo/canvas-bounds
state oracle. The bounded visual oracle passed with identical compared hierarchy, 1,369 changed
pixels within the canonical `SelectionControls` node union, maximum RGB channel delta 1, zero alpha
changes, zero changes outside the allowed controls, and zero changed canvas pixels. The title and
complete canvas were exact. The negative regression against the earlier title-inclusive offscreen
artifact failed closed for both changed-pixel-cap overflow and changes outside allowed controls.

The exact experiment manifest fixes hypothesis, affected cost, correctness risk, stop conditions,
pair identity, and the four-slot ABBA budget before slot 1. The executable schema-v7 harness remains
unchanged and enforces 10/10/50/50 counts, predecessor verdicts, one invalid-before-first-DOWN
replacement, candidate-FAIL stop, phase association, environment, artifact, and correctness checks.

### Selection-controls offscreen comparison result

The baseline diagnostic retained 10 operations / 20 frames and was non-gross/inconclusive: frame
overrun p95 0.655543 ms, p99 4.184963 ms, and committed-result p95 9.864385 ms. The candidate
diagnostic retained 10 / 20 and was also non-gross/inconclusive: overrun p95 -0.470829 ms, p99
0.175451 ms, and committed-result p95 9.468654 ms. These maximum-rank diagnostics permit the fixed
decision batch but do not prove comparative improvement or acceptance.

The candidate decision retained all 50 operations and all 100 preview/commit frames with exact
identity, environment, phase association, committed UI state, and zero fatal/ANR matches. It is
**FAIL** at the unchanged absolute gate: all-frame overrun p95 is 0.664984 ms (>0), p99 is
2.121810 ms, and committed-result p95 is 10.346193 ms. Preview had one miss with overrun p95
-1.652019 ms; commit had six misses with overrun p95 1.275436 ms. The overall maximum overrun is
3.992003 ms and maximum committed-result latency is 11.058615 ms.

The candidate's absolute FAIL triggers the predeclared stop before baseline decision slot 4. The
diagnostic pair cannot substitute for that stopped 50-operation baseline, so no final relative
improvement claim is made. The historical generated-profile decision remains separately FAIL at
0.670013 ms overrun p95; its numerical proximity is context, not a paired baseline comparison.
No retry, tolerance expansion, threshold change, row removal, or historical relabelling occurs.
The offscreen production wrapper is reverted, while ADR 0012, the fail-closed oracle, exact candidate
identity, visual PASS, and all performance artifacts remain as immutable Issue evidence. Issue #54
and dependent #44 remain open.

### Approved same-window custom-View canvas candidate

The retained #54 decision record later numbered ADR 0013 records hide's 2026-09-06 01:11 JST
approval for one new candidate, not its adoption. That profile-dependent ADR is deliberately not
integrated by this tooling/ledger change.
`PixelCanvas` replaces only its Compose `Canvas` recording node with a private custom Android `View`
hosted by `AndroidView`. It remains in the same Activity window, Compose root, HWUI root surface, and
FrameTimeline population. `SurfaceView`, another surface, GL/Vulkan code, a new render thread,
dependency, module, state owner, input transform, command/action path, or runtime fallback is outside
the experiment.

The admission oracle is exact for the complete canvas and document rendering, including translucent
preview overlap, eraser preview, grid, margins, rectangular canvases, viewport transform, density,
resize/recreation, semantics, and interaction bounds. ADR 0012's upper-control color tolerance does
not apply to this candidate. A mismatch stops before timing.

The prospective experiment identity is `nene-pixel-m2-same-window-view-canvas-v7-01`. Its baseline
is the unchanged generated-profile source `efb8c36003a1c62e958da92cf4fb28c2b35dc261` / APK SHA-256
`359a8f5a6975afae6f29e8680a69ae14f28164db72d36b250225f03d8f3de959`. Candidate source/APK and
packaged-profile hashes were to be fixed after the source and retained ADR-0010 two-run profile
generation pass and before slot 1; an unset identity prohibits collection. The exact manifest must
also fix the physical
profile, `speed-profile` compilation, five warmups, workload, separate DOWN-preview/UP-commit phase
association, 100 ms preview dwell, 350 ms commit wait, correctness/environment checkpoints, and the
single four-slot ABBA budget.

The executable schema-v7 harness remains the only collection path and enforces baseline diagnostic
10, candidate diagnostic 10, candidate decision 50, and baseline decision 50. Existing gross
boundaries, one invalid-before-first-DOWN replacement, invalid-after-start consumption, no-overwrite,
nearest-rank rules, and stop order remain unchanged. The candidate decision must meet all absolute
thresholds before the paired baseline decision: frame-overrun p95 <= 0 ms, p99 <= 16.67 ms, and
UP-input-to-committed-result p95 <= 33.33 ms. A favorable diagnostic is inconclusive; an absolute
FAIL stops and is reverted. Generated-profile v7, physical-present v2, source-attribution v1, #58
run02, and both offscreen experiment budgets remain consumed and are neither reused nor retried.

The causal hypothesis is deliberately bounded: moving canonical canvas recording from Compose
DrawScope to a same-surface View may change root display-list recording/submission, but may leave the
observed RenderThread `QueueSubmit` wait untouched. Older-source `PixelCanvas` and draw slice times
are context, not strict savings ceilings. Only the new fixed comparison can support adoption.

### Same-window custom-View candidate result

The candidate implementation was fixed locally at source
`0e547f41fdcfbce6b5426034d180536343442b35`. Focused presentation tests and static analysis passed.
Its first Baseline Profile producer attempt correctly failed because the canvas content description
was visible to Compose tests but absent from the external UiAutomator tree. Publishing the same
dynamic description on the private native View fixed that accessibility defect, and the producer's
canonical Pencil/Undo journey subsequently passed.

The required two successful profile generations did not produce identical canonical output. The
first contained 13,882 rules with CRLF-byte SHA-256
`37f466b35918cd19ce408c42c70dd3097c65d7fe1aa9c962edc2aebeb478a8a2` and canonical LF SHA-256
`bf93109500f7dd0f933b936e4754a6128f27183f6997c8b8a950b2d873fa98c3`; the second contained 13,876
rules with CRLF-byte SHA-256 `d14ecf7e18c78670920eab5ea4019c01063eba1e6fb96af0678b555027460421`
and canonical LF SHA-256 `f7bfed7245af53ab799dd66a696c01dd7520519e48a03e2a1264ba91e2e38414`.
The second invocation removed six signatures and changed the flags of the same six method signatures
from `HSP` to `SP`. A third generation was not run to search for a favorable result. Earlier
long-worktree pull failures produced no candidate profile and are not counted as successful
reproducibility evidence.

This is an admission FAIL under the retained ADR 0010 and ADR 0013 contracts. The candidate never
acquired a final source/APK/profile identity, so exact visual admission and every
`nene-pixel-m2-same-window-view-canvas-v7-01` timing slot remain unexecuted. No performance inference
is made. The production/profile candidate is focused-reverted; the protocol, implementation commit,
producer diagnostics, and mismatched hashes remain historical evidence. Issue #54 and dependent
#44 remain open, and no separate-surface alternative is authorized by this result.

## Historical fixed protocol (v1-v5)

The decision batch is fixed before collection:

| Field | Value |
| --- | --- |
| physical profile | `NENE-P2-ALLDOCUBE-IPL80MP-A16-API36` |
| device identity | non-emulated ALLDOCUBE `iPlay80miniPro` / `T830`, Android API 36 |
| display | mode 1, 1200 x 1920, 90 Hz, interactive USB power, power saving off |
| app | the exact Issue #54 source built as debug and unsigned release-like APKs |
| release signing | local debug certificate applied only to the ignored measurement APK |
| compilation | reset package compilation; use packaged-profile `speed-profile` for release-like and full-AOT `speed` for debug before launch |
| document | fresh-process canonical 16 x 16 blank document |
| workload | top-left-cell Pencil tap followed by Undo back to the clean checkpoint |
| warmup | 5 complete Pencil/Undo cycles, excluded from samples |
| samples | 50 independent Pencil journeys per build variant |
| reset verification | after every warmup/sample Undo, retry up to 3 times and require clean, Undo-disabled UI |
| frame reset | package `gfxinfo` reset immediately before each Pencil tap |
| collection | wait for completion, retain `dumpsys gfxinfo ... framestats`, then Undo |
| final correctness | leave sample 50 applied long enough to retain UI hierarchy and screenshot |
| publication | distinct ignored debug/release directories; never overwrite an accepted P2 artifact |

The fixed orchestration is [measure-m2-frame.ps1](measurements/measure-m2-frame.ps1). It accepts
the device selector only as an invocation input and never writes that selector into an artifact.
Its variant-aware defaults are 50 samples with packaged-profile `speed-profile` for release-like
and 50 samples with full-AOT `speed` for debug. The debug lane remains diagnostic. A different
compilation mode or sample count is explicitly labeled diagnostic and cannot pass or fail the
release decision lane; this permits bounded attribution runs without substituting them for the
fixed evidence. A `speed-profile` invocation preflights the APK for both packaged profile assets
before changing device state. Every invocation also requires a clean tracked worktree and requires
the supplied full source commit to equal the repository HEAD. The release-like lane additionally
reads `META-INF/version-control-info.textproto` from the APK and requires its embedded Git revision
to equal the same source commit. The debug APK does not contain that AGP release artifact, so its
retained APK hash and rerun build record remain the debug binary identity.

The script emits schema `nene-pixel-m2-actual-app-frame-v5` and retains `environment.csv` without
the ADB selector. It rejects an emulated or mismatched manufacturer/model/product/device/API before
installing the APK. Physical checkpoints are captured after wake/stay-awake setup, after warmups,
after every tenth sample and the final sample, and once more after the batch. Every checkpoint must
retain display mode 1 at physical 1200 x 1920 and 90 Hz within 0.1 Hz, device-wide thermal status at
most 1, disabled power saving, an interactive display, USB power, and available battery level. The
current build fingerprint and security patch are recorded but are not folded into the stable
hardware profile; results from different builds remain distinct rather than aggregated.

Coordinates are derived from the actual UI hierarchy rather than copied as product geometry. The
canvas is selected by content description `16 by 16 pixel canvas`; the sample point is the center
of its top-left cell. Undo is selected from the clickable parent of text `Undo`. The hierarchy
must begin clean with Pencil selected and must end with `Unsaved changes`, enabled Undo, disabled
Redo, and the same canvas bounds. The screenshot must show exactly the expected visible top-left
Pencil result.

Each Undo between journeys is verified through a newly captured UI hierarchy before the next
`gfxinfo` reset. A missed input may be retried at most three times; the batch is invalid if the
clean checkpoint cannot be observed. This enforces the already-fixed independent-journey boundary
and prevents an unchanged red cell from being measured as a Pencil no-op.

For every sample, the retained report records the aggregate frame/deadline counters and the raw
`PROFILEDATA` row. The sample must report exactly one rendered frame, exactly one raw row, and exactly
one row with platform `Flags = 0`; any other cardinality invalidates the whole invocation before
percentile computation. Derived values are:

- CPU frame duration: `FrameCompleted - FrameStartTime`;
- app-frame total: `FrameCompleted - IntendedVsync`;
- platform overrun: `FrameCompleted - FrameDeadline`; and
- input-handling start to app-frame completion: `FrameCompleted - HandleInputStart`.

Nearest-rank percentiles retain the existing P2 meaning. Both variants must have CPU/app-frame and
platform fields available, no fatal exception, fatal signal, ANR, or incorrect final UI/image. The
debug lane reports the same numeric thresholds without serving as a product-performance gate. The
locally signed release-like decision lane passes only with p95 platform overrun at most 0 ms, p99
platform overrun at most 16.67 ms, and input-start-to-completion p95 at most 33.33 ms.

### Decision-lane clarification

The first complete, clean-checkpoint debug rerun exposed a contradiction in the predeclared prose:
it said both that every variant must pass and that debug is diagnostic while release-like is the
performance decision lane. Before collecting the final release-like evidence, this section resolves
that contradiction in favor of the Android guidance already cited above: performance decisions use
a release build, while a debuggable build remains useful for test and diagnosis. The script records
the debug threshold result without suppressing or relabeling it, but only a release-like miss fails
the product-performance lane. The numeric thresholds, workload, warmups, sample count, percentile
method, correctness checks, and raw-row retention are unchanged.

A later packaging preflight found that AGP includes `assets/dexopt/baseline.prof` and
`baseline.profm` in the release-like APK but not in the debuggable APK. An emulator receiver probe
accordingly returned result `1` for release-like and result `0` for debug. Treating the latter as a
successful packaged-profile installation would misstate debug compilation. The fixed debug lane
therefore uses full-AOT `speed`, which is already an accepted script mode and provides a known
diagnostic compilation state; the release-like decision lane remains packaged-profile
`speed-profile`. This changes no release threshold, workload, warmup, sample count, percentile, or
correctness condition.

### Physical-profile enforcement

A pre-final harness audit found that schema v2 wrote the fixed physical profile ID without
executably rejecting an emulator, different hardware, changed display mode, thermal throttling,
power saving, a sleeping display, or lost USB power. No final candidate batch had been collected,
so schema v3 closes that evidence boundary before collection. The added read-only checkpoints run
outside each tap-to-frame interval and do not alter the workload or percentile population. An
environment mismatch invalidates the whole batch; it cannot be downgraded to a warning or retained
as decision evidence.

### Direct input-service injection

A post-diagnostic attribution trace found that the measured frame overlaps the process exit and ADB
work created by `adb shell input tap`. On the fixed Android 16 device, `/system/bin/input` is exactly
a 32-byte shell wrapper whose executable body is `cmd input "$@"`. Calling the wrapper therefore
adds a shell process but no input semantics. No final 50-sample decision batch has been collected.

Schema v4 removes only that wrapper by invoking `adb shell cmd input` directly for wake, Pencil, and
Undo events. It records `input_injection=cmd-input-service-direct` in metadata. Coordinate selection,
touch source and DOWN/UP generation, out-of-process boundary, warmups, waits, reset timing, sample
population, percentiles, correctness checks, and numeric thresholds are unchanged. Existing schema-v3
artifacts remain valid for their declared protocol and are not relabelled or overwritten. Diagnostics
collected before the exact-row correction retain schema v4; all later evidence uses v5.

### Compilation attribution and selected fix

After deferring canvas state reads to the draw phase, source
`c873a6ffda244af4c3852db1520042bc19a09409` reduced release-like
input-start-to-completion p95 from 31.435308 ms to 21.725153 ms. Its `speed-profile` batch still
missed the platform target: overrun p95 was 13.893362 ms and p99 was 17.910555 ms. A bounded
ten-sample `speed` diagnostic on source `de0026931ed4aae981b6cc25063cbf76f6dde211` then reduced
input p95 to 10.780884 ms and overrun p95 to 1.857334 ms. Because full AOT approximately halves
the remaining path without a second renderer, the next change targets compilation rather than
pixel or drawing semantics.

The app will add one AGP-native `app/android/src/main/baseline-prof.txt` rule restricted to the
project-owned `io.github.hideyukimori.nenepixel` namespace. AndroidX/Compose rules continue to come
from their existing dependency profiles. This adds no dependency, Gradle plugin, module, runtime
path, public API, or quality exception. The broad project-owned rule is acceptable while the app is
small and M2-bounded; M5 should replace it with generated critical-journey rules if the namespace
grows materially.

For `speed-profile`, the measurement script must clear the just-installed test package, reset ART,
install the APK's packaged profile through the existing Profile Installer receiver, require receiver
result `1`, force-stop, and only then compile. Without this explicit install, `adb install` followed
immediately by `cmd package compile -m speed-profile` can measure an empty app profile rather than
the profile shipped in the APK. The `speed` diagnostic remains full-AOT and does not install a
profile. This correction changes neither workload nor threshold; it makes the declared compilation
state executable and records the install result.

The first packaged-profile diagnostic reached 11.651461 ms input p95 and 3.417764 ms overrun p95.
The matching full-AOT diagnostic still had 1.541 ms p95 in the draw-recording phase. Inspection of
the canonical renderer found that every pixel frame recreates the same viewport transform, bitmap
destination, and 34 grid-line coordinates for the fixed 16 x 16 canvas. The next candidate caches
only this immutable presentation geometry by canvas, surface, and viewport equality. Pixel Bitmap
and preview drawing remain dynamic, draw order is unchanged, and a viewport or surface change
invalidates the geometry. This is distinct from the rejected separate-grid-layer candidate, which
did not reduce draw recording.

The ten-sample `speed-profile` diagnostic for the geometry cache, source
`e6777a33cf70c2e9a3e671ac040060537cb79e1d`, reduced input-start-to-draw-start p95 from
2.926615 ms to 1.771693 ms and input-start-to-completion p95 from 11.651461 ms to 10.983846 ms.
It therefore removes measured app-side preparation work, but the total overrun p95 remained above
the target at 3.649067 ms. The retained frame path still records 34 separate Compose `drawLine`
operations for the 16 x 16 grid on every pixel update. The next candidate stores the same ordered
line segments in the cached geometry as one Compose `Path` and submits it through one `drawPath`
operation. It changes neither grid coordinates, color, stroke width, visibility, clipping, nor draw
order, and adds no second renderer or retained mutable document state.

The path-batching diagnostic for source `5a3bf5349beb6db03191f66206e406640feee83c`
reduced the `speed-profile` overrun p95 from 3.417764 ms to 1.724677 ms and
input-start-to-completion p95 from 11.651461 ms to 10.723923 ms. The remaining default square
journey still composites an all-surface white background and then an all-surface scaled Bitmap.
Because the canvas surface has the document aspect ratio and the viewport never zooms below fit,
the Bitmap covers the whole surface in that journey. The next candidate composites document alpha
over the same opaque canvas color in the cached 16 x 16 presentation Bitmap, omits the redundant
background modifier, and draws canvas-color margins only when a constrained non-square surface is
not covered by the projected canvas. Domain pixels, stored alpha, export meaning, grid order, and
the existing alpha-preserving projection remain unchanged.

The opaque-display-Bitmap diagnostic for source
`4227e12946aa112fba266a12ecc8a12e7baaa7b4` reduced input-start-to-completion p95 to
9.729385 ms and overrun p95 to 0.746939 ms. Its ten valid rows contain four deadline misses. The
largest retained p95 phase is issue-to-swap at 4.999269 ms, while draw-start-to-sync-queued remains
1.755961 ms. Inspection found one remaining allocation in that path: `RenderedBitmapCache` creates
a new Android `Bitmap` for every changed `PixelSnapshot`, even when the validated canvas dimensions
are unchanged. The next candidate retains one mutable display-only Bitmap per canvas size and
replaces all of its pixels from the newly projected snapshot. A size change creates a replacement
Bitmap; an unchanged snapshot still returns the existing cached value. This cache is disposable
presentation `RenderCache`, never document or pixel-engine storage. The candidate preserves exact
opaque display pixels, source alpha and hidden RGB, viewport mapping, grid appearance and order,
command/history behavior, and the existing alpha-preserving export projection. It adds no second
renderer, public API, dependency, module, schema, suppression, or waiver.

`DisplayPresentTime` may remain unavailable on this vendor build. Therefore this route is
app-issued FrameTimeline evidence, not strict SurfaceFlinger physical-present correlation. ADR
0005 explicitly defers that correlation; this record does not relabel it as complete.

## Results

### Mutable display-Bitmap candidate diagnostic

Committed source `df77504585caaa61be1444c3f3b2b3407808549c` was collected once on the fixed
physical profile with five warmups, ten diagnostic samples, packaged-profile `speed-profile`, and
the schema-v3 identity/environment preflight. All ten samples produced one valid row. The four
environment checkpoints retained build `94111`, API 36, display mode 1 at 1200 x 1920 and 90 Hz,
thermal status 1, disabled power saving, an interactive USB-powered display, and 100% battery. The
final hierarchy and screenshot contain the exact top-left red pixel, dirty indication, enabled
Undo, disabled Redo, unchanged canvas bounds, opaque display composition, and grid. Fatal/ANR and
GC-pattern counts are zero.

| Metric | Opaque Bitmap source `4227e129` | Mutable reuse source `df775045` |
| --- | ---: | ---: |
| valid rows | 10 / 10 | 10 / 10 |
| deadline misses | 4 / 10 | 6 / 10 |
| CPU frame p95 | 9.735769 ms | 10.664039 ms |
| input-to-completion p95 | 9.729385 ms | 10.655231 ms |
| platform overrun p95 / p99 | 0.746939 / 0.746939 ms | 2.937041 / 2.937041 ms |
| draw-to-sync-queued p50 / p95 | 1.344808 / 1.755961 ms | 1.552653 / 1.653808 ms |
| animation-to-traversal p50 / p95 | 1.569462 / 1.844808 ms | 1.622462 / 2.914346 ms |
| issue-to-swap p50 / p95 | 4.290385 / 4.999269 ms | 4.375731 / 5.340461 ms |

The targeted draw-recording p95 decreased by only 0.102153 ms while its median increased by
0.207845 ms, and the independent total-frame condition moved farther from acceptance. The run is
retained at
`build/reports/m2/frame-follow-up/release-like-bitmap-reuse-profile-diagnostic-10-run-01/`.
Its metadata, frames, samples, environment, final PNG, logcat, and before/after hierarchy hashes are
retained with the ignored artifact and are not overwritten.

## Decision

The mutable display-Bitmap candidate is rejected. It did not pass the bounded diagnostic with
margin, so no 50-sample decision batch is run and the production cache change is reverted. The
schema-v3 measurement hardening remains because it independently closes compilation, source,
physical-device, and environment evidence gaps without changing production behavior.

Issue #54 remains pending. The prior opaque-display-Bitmap source still misses the strict platform
condition by 0.746939 ms at p95. Another production candidate is permitted only after raw evidence
identifies a distinct redundant operation; the rejected mutable Bitmap must not be combined with a
new candidate or rerun for favorable sample selection.

## Next candidate: endpoint-alpha projection

The retained opaque-display-Bitmap frame spends 1.755961 ms at p95 from draw start to sync queued.
The fixed 16 x 16 Pencil journey changes a blank document containing 256 fully transparent pixels
into a document containing 255 fully transparent pixels and one fully opaque palette pixel. The
current display projection nevertheless executes the general three-channel source-over arithmetic,
including integer division, for every pixel at both alpha endpoints. At alpha 0 the exact result is
the opaque canvas RGB regardless of hidden source RGB; at alpha 255 it is the exact source RGB with
an opaque alpha. Only alpha 1 through 254 requires the general formula.

The next candidate adds those two endpoint branches inside the existing canonical opaque projection.
It retains the same fresh immutable display `Bitmap`, the same generic partial-alpha formula, source
snapshot immutability, row-major order, dimensions, canvas color, draw order, viewport/grid behavior,
and command/history path. Tests must cover opaque source, fully transparent source with non-zero hidden
RGB, partial alpha, exact output, and unchanged source pixels. This adds no second renderer, mutable
cache, public API, dependency, plugin, module, schema, suppression, or waiver.

The candidate receives one five-warmup/ten-sample fixed diagnostic. It advances to the predeclared
50-sample decision batch only if the ten-sample run passes the independent total-frame condition with
margin. Otherwise it is recorded, reverted, and not combined with the rejected mutable Bitmap.

## Endpoint-alpha projection diagnostic

Committed source `d5b7b213214a9f63a7dfe85b5927b01da08a6eaa` was collected once on the fixed
physical profile with five warmups, ten diagnostic samples, packaged-profile `speed-profile`, and
the schema-v3 identity/environment preflight. All ten samples produced one valid row. Four valid
environment checkpoints retained the required physical identity, display mode, 90 Hz refresh,
thermal status 1, disabled power saving, interactive USB power, and sufficient battery. The final
hierarchy and screenshot contain the exact top-left red pixel, dirty indication, enabled Undo,
disabled Redo, unchanged canvas bounds, opaque display composition, and grid. Fatal/ANR and
GC-pattern counts are zero.

| Metric | Opaque Bitmap source `4227e129` | Endpoint-alpha source `d5b7b213` |
| --- | ---: | ---: |
| valid rows | 10 / 10 | 10 / 10 |
| deadline misses | 4 / 10 | 8 / 10 |
| CPU frame p95 | 9.735769 ms | 10.587192 ms |
| input-to-completion p95 | 9.729385 ms | 10.578423 ms |
| platform overrun p95 / p99 | 0.746939 / 0.746939 ms | 2.401976 / 2.401976 ms |
| draw-to-sync-queued p50 / p95 | 1.344808 / 1.755961 ms | 1.495884 / 1.661269 ms |
| animation-to-traversal p50 / p95 | 1.569462 / 1.844808 ms | 1.591924 / 2.863538 ms |
| issue-to-swap p50 / p95 | 4.290385 / 4.999269 ms | 4.692577 / 4.977000 ms |

The targeted draw-recording p95 decreased by only 0.094692 ms while its median increased by
0.151076 ms. The independent total-frame condition regressed, so the arithmetic endpoint branches
do not provide a measurable actual-app frame improvement. The run is retained at
`build/reports/m2/frame-follow-up/release-like-alpha-endpoints-profile-diagnostic-10-run-01/`
without overwriting any earlier diagnostic.

## Endpoint-alpha decision

The endpoint-alpha projection candidate is rejected. It did not pass the bounded diagnostic with
margin, so no 50-sample decision batch is run and its production/test change is reverted. The
generic opaque projection remains the one canonical implementation. No later candidate may combine
this endpoint branch with the rejected mutable Bitmap or rerun it for favorable sample selection.

## Next candidate: precomposed dirty labels

A read-only attribution trace on the restored production path used the debuggable APK compiled with
full-AOT `speed`. It is diagnostic only: tracing overhead and build type make its durations ineligible
for the acceptance table. The trace nevertheless names the work inside the clean-to-dirty frame. A
`TextStringSimpleNode::measure` slice occupies 1.790 ms inside `AndroidOwner:measureAndLayout` while
the status changes from `No unsaved changes` to `Unsaved changes`. The same single `Text` node
alternates between those strings on every measured Pencil/Undo cycle, so its one-entry text layout
state cannot retain both fixed results.

The next candidate keeps both constant status labels composed and centered in the same layout slot.
Each child clears its own semantics and uses a draw-only visibility modifier; the common parent
publishes exactly one `Document dirty status` description and the exact current semantic text. A
dirty-state change therefore updates draw visibility and semantics without replacing either text
input or changing the label slot's measured size. The visible strings, alignment, accessibility
meaning, clean/dirty transitions, Undo/Redo behavior, and all document/command paths remain exact.
Tests must prove that only the current status text exists in the merged semantics before drawing,
after drawing, after Undo, and after Redo. This adds no second renderer, state owner, public API,
dependency, plugin, module, schema, suppression, or waiver.

The candidate receives one five-warmup/ten-sample fixed diagnostic. It advances to the predeclared
50-sample decision batch only if the ten-sample run passes the independent total-frame condition with
margin. Otherwise it is recorded and reverted without combining earlier rejected candidates.

## Precomposed dirty-label diagnostic

Committed source `e1cf19df885753c9eec85432f82b5deca7542f36` was collected once on the fixed
physical profile with five warmups, ten diagnostic samples, packaged-profile `speed-profile`, and
the schema-v3 identity/environment preflight. All ten samples and all four environment checkpoints
are valid. The final hierarchy exposes exactly the current dirty-status semantic text, and the final
screenshot retains the centered `Unsaved changes` label, top-left red pixel, enabled Undo, disabled
Redo, canvas bounds, opaque display composition, and grid. Fatal/ANR and GC-pattern counts are zero.

| Metric | Opaque Bitmap source `4227e129` | Precomposed-label source `e1cf19df` |
| --- | ---: | ---: |
| valid rows | 10 / 10 | 10 / 10 |
| deadline misses | 4 / 10 | 5 / 10 |
| CPU frame p95 | 9.735769 ms | 9.516077 ms |
| input-to-completion p95 | 9.729385 ms | 9.507885 ms |
| platform overrun p95 / p99 | 0.746939 / 0.746939 ms | 1.494066 / 1.494066 ms |
| draw-to-sync-queued p50 / p95 | 1.344808 / 1.755961 ms | 0.895193 / 0.994384 ms |
| animation-to-traversal p50 / p95 | 1.569462 / 1.844808 ms | 1.739923 / 2.765807 ms |
| issue-to-swap p50 / p95 | 4.290385 / 4.999269 ms | 4.671385 / 5.117923 ms |

The targeted draw-recording p95 decreases by 0.761577 ms and CPU/input p95 each improve by about
0.22 ms. However, animation-to-traversal p95 increases by 0.920999 ms and the independent platform
overrun condition remains positive. The run is retained at
`build/reports/m2/frame-follow-up/release-like-precomposed-dirty-profile-diagnostic-10-run-01/`
without overwriting any earlier diagnostic.

## Precomposed dirty-label decision

The precomposed dirty-label candidate is rejected. A targeted phase improvement is insufficient
when the fixed total-frame condition fails, so no 50-sample decision batch is run and its
production/test change is reverted. No later candidate may combine this label implementation with
earlier rejected candidates or rerun it for favorable sample selection.

## Next candidate: leaf-scoped history observation

The restored implementation still observes canvas size, `canUndo`, `canRedo`, and dirty state as
one `DocumentInputs` value. The fixed Pencil frame changes dirty state and `canUndo`, which causes
`DocumentControls` and the whole `HistoryControls` call to re-enter even though the New Document
control and Redo state are unchanged. The retained precomposed-label diagnostic did not pass as a
whole, but independently confirms that its remaining animation-to-traversal p95 is 2.765807 ms and
that Compose re-entry, rather than the now-reverted label renderer, is the next attributable app
phase. The earlier read-only trace also names `Compose:recompose` and `Compose:applyChanges` in this
frame; its debug durations remain diagnostic only.

The next candidate retains the existing single `State<EditorRenderState>` owner and the existing
`derivedStateOf` mechanism, but narrows reads to separate canvas-size, dirty-status, Undo, and Redo
restart scopes. `DocumentControls` observes only canvas size. `HistoryControls` creates derived
states without reading them, and dedicated status/Undo/Redo children read only their own value. A
Pencil commit can then re-enter the status and Undo leaves without re-entering New Document, the
history container, or the unchanged Redo leaf. Visible labels, Material controls, semantics, layout,
callbacks, and command/history behavior remain unchanged. This adds no state owner, alternate UI,
public API, dependency, plugin, module, schema, suppression, or waiver.

The candidate receives one five-warmup/ten-sample fixed diagnostic. It advances to the predeclared
50-sample decision batch only if the ten-sample run passes the independent total-frame condition with
margin. Otherwise it is recorded and reverted without combining earlier rejected candidates.

## Leaf-scoped history diagnostic

Committed source `308c6e30e923056b51ded92de1d1d24ff33fc0e1` was collected once on the fixed
physical profile with five warmups, ten diagnostic samples, packaged-profile `speed-profile`, and
the schema-v3 identity/environment preflight. All ten samples and four environment checkpoints are
valid. The final hierarchy and screenshot retain the exact dirty status, top-left red pixel, Undo/
Redo states, canvas bounds, opaque display composition, and grid. Fatal/ANR and GC-pattern counts
are zero.

| Metric | Opaque Bitmap source `4227e129` | Leaf history source `308c6e30` |
| --- | ---: | ---: |
| valid rows | 10 / 10 | 10 / 10 |
| deadline misses | 4 / 10 | 6 / 10 |
| CPU frame p95 | 9.735769 ms | 10.448923 ms |
| input-to-completion p95 | 9.729385 ms | 10.442653 ms |
| platform overrun p95 / p99 | 0.746939 / 0.746939 ms | 1.263298 / 1.263298 ms |
| draw-to-sync-queued p50 / p95 | 1.344808 / 1.755961 ms | 1.487307 / 1.803577 ms |
| animation-to-traversal p50 / p95 | 1.569462 / 1.844808 ms | 1.706731 / 2.040846 ms |
| issue-to-swap p50 / p95 | 4.290385 / 4.999269 ms | 4.635808 / 5.395654 ms |

The narrower restart scopes do not reduce the targeted animation phase or the independent total
frame. The run is retained at
`build/reports/m2/frame-follow-up/release-like-history-leaf-profile-diagnostic-10-run-01/`
without overwriting any earlier diagnostic.

## Leaf-scoped history decision

The leaf-scoped history candidate is rejected. It did not pass the bounded diagnostic with margin,
so no 50-sample decision batch is run and its production change is reverted. The combined
`DocumentInputs` state observation remains canonical; the rejected leaf split is not combined with
other candidates or rerun for favorable sample selection.

## Schema-v4 direct-input diagnostic

After reverting all rejected production candidates, committed source
`0fd18ce55d7e20a36b3a6eae194141991731ec53` was collected once with the canonical production
renderer and the schema-v4 direct input-service boundary. Five warmups, ten diagnostic samples,
packaged-profile `speed-profile`, source/APK identity, and all four physical environment checkpoints
are valid. Metadata records `input_injection=cmd-input-service-direct`. The final hierarchy and
screenshot retain the exact Pencil result, dirty/Undo/Redo state, canvas bounds, opaque composition,
and grid. Fatal/ANR and GC-pattern counts are zero.

| Metric | Schema-v3 wrapper source `4227e129` | Schema-v4 direct source `0fd18ce5` |
| --- | ---: | ---: |
| valid rows | 10 / 10 | 10 / 10 |
| deadline misses | 4 / 10 | 7 / 10 |
| CPU frame p95 | 9.735769 ms | 10.196038 ms |
| input-to-completion p95 | 9.729385 ms | 10.189654 ms |
| platform overrun p95 / p99 | 0.746939 / 0.746939 ms | 1.063475 / 1.063475 ms |
| draw-to-sync-queued p50 / p95 | 1.344808 / 1.755961 ms | 1.525538 / 1.608769 ms |
| animation-to-traversal p50 / p95 | 1.569462 / 1.844808 ms | 1.665000 / 1.783692 ms |
| issue-to-swap p50 / p95 | 4.290385 / 4.999269 ms | 4.551154 / 5.019038 ms |

The wrapper removal lowers animation and draw p95 but does not pass the independent total-frame
condition, so no 50-sample decision batch is run. Cross-schema numbers remain diagnostics rather
than a production-candidate comparison. Schema v4 stays canonical because it removes a proven
no-op shell wrapper and records the input boundary without weakening any gate. Issue #54 remains
pending until a distinct retained operation is identified and a new production candidate passes.

Active waiver: none.

## Next candidate: premeasured single dirty-status label

A read-only attribution trace on restored production source `0fd18ce55d7e20a36b3a6eae194141991731ec53`
used the release-like APK after packaged-profile `speed-profile` compilation and the schema-v4 direct
input boundary. The trace is diagnostic only because tracing overhead changes frame duration. Within
the clean-to-dirty frame it nevertheless identifies 0.627 ms of `TextStringSimpleNode::measure`
inside 0.820 ms of `AndroidOwner:measureAndLayout`. RenderThread prepares and executes the retained
grid as one `AAHairlineOp` in only 0.009 ms and 0.013 ms respectively, so changing the grid API is
not an evidence-backed candidate.

The next candidate retains one dirty-status semantics and rendering node. It measures the two fixed
status strings once with the current Compose text style, density, font resolver, and layout direction,
retains both immutable `TextLayoutResult` values, gives the node their common maximum size, and draws
only the current result centered in that slot. A state change updates the node's exact visible text and
exact merged semantics without remeasuring either string. This is distinct from the rejected
precomposed-label candidate: it does not keep two text nodes composed, use draw-only visibility, or
combine that implementation. It also leaves the combined `DocumentInputs` observation unchanged and
therefore does not combine the rejected leaf-scoped history candidate.

Tests must prove that exactly the current status text is exposed before Pencil, after Pencil, after
Undo, and after Redo. The existing label strings, typography, centered alignment, status content
description, buttons, command/history behavior, document state, renderer, and measurement schema
remain exact. This adds no alternate document or canvas renderer, state owner, public API, dependency,
plugin, module, suppression, or waiver.

The candidate receives one five-warmup/ten-sample fixed schema-v4 diagnostic. It advances to the
predeclared 50-sample decision batch only if the ten-sample run passes the independent total-frame
condition with margin. Otherwise it is recorded and reverted without combining rejected candidates.

## Premeasured dirty-status diagnostic

Committed source `c86133c223589e4843fb1cbae2954d99a6649b3d` was collected once on the fixed
physical profile with five warmups, ten diagnostic samples, packaged-profile `speed-profile`, and
the schema-v4 identity/environment preflight. All ten samples and four environment checkpoints are
valid. The final hierarchy exposes only the current status text, and the final screenshot retains
the exact visible dirty label, top-left red pixel, Undo/Redo states, canvas bounds, opaque display
composition, and grid. Fatal/ANR and GC-pattern counts are zero.

| Metric | Schema-v4 canonical source `0fd18ce5` | Premeasured label source `c86133c2` |
| --- | ---: | ---: |
| valid rows | 10 / 10 | 10 / 10 |
| deadline misses | 7 / 10 | 1 / 10 |
| CPU frame p95 | 10.196038 ms | 9.645423 ms |
| input-to-completion p95 | 10.189654 ms | 9.638731 ms |
| platform overrun p95 / p99 | 1.063475 / 1.063475 ms | 0.381973 / 0.381973 ms |
| draw-to-sync-queued p50 / p95 | 1.525538 / 1.608769 ms | 0.978615 / 1.184770 ms |
| animation-to-traversal p50 / p95 | 1.665000 / 1.783692 ms | 1.648346 / 1.821615 ms |
| issue-to-swap p50 / p95 | 4.551154 / 5.019038 ms | 4.528615 / 4.843769 ms |

The candidate removes the traced status-text remeasurement and reduces the targeted draw p95 by
0.423999 ms. It also reduces CPU/input p95 by about 0.55 ms and platform overrun p95 by 0.681502 ms.
However, one of ten frames still misses its deadline and the independent p95 condition remains
positive. The run is retained at
`build/reports/m2/frame-follow-up/release-like-premeasured-status-profile-diagnostic-10-run-01/`
without overwriting an earlier diagnostic.

## Premeasured dirty-status decision

The premeasured single-label candidate is rejected. Its improvement is real but insufficient for
the predeclared zero-overrun condition with margin, so no 50-sample decision batch is run and its
production/test change is reverted. No later candidate may combine or rerun this label cache, the
two-node precomposed label, or the leaf-scoped history split for favorable sample selection. Issue
#54 remains pending until a distinct retained operation passes the bounded diagnostic.

## Next candidate: targeted simple-text compilation profile

Restored canonical source `d6e7dc06ae2db33925040243a8d2bf53502f6664` was collected once with
the same release-like APK, schema-v4 boundary, five warmups, and ten samples, changing only package
compilation from the acceptance lane's packaged-profile `speed-profile` to full-AOT `speed`. Eleven
valid rows were emitted because one sample contained an additional valid frame. The attribution run
has zero deadline misses, CPU p95 9.189269 ms, input p95 9.182423 ms, and platform overrun p95/p99
-0.001722 ms. It is neither a decision batch nor acceptance evidence: it uses the wrong compilation
mode, has only ten samples, and passes the zero-overrun boundary without margin. It does show that
compilation coverage can recover about one millisecond without changing renderer or UI semantics.

The merged packaged-profile text was then compared with the release-like attribution trace and the
physical runtime's expanded class/method profile. Existing dependency profiles already cover the
traced Recomposer, Composition, AndroidComposeView, measure/layout delegate, TextLayout, and static
layout classes. They do not cover `TextStringSimple` or `ParagraphLayoutCache`, even though the trace
places `TextStringSimpleNode::measure` on the target frame for 0.627 ms and the runtime profile marks
the associated methods hot, startup, and post-startup.

The next candidate adds exactly two AGP-native baseline-profile rules: the
`androidx.compose.foundation.text.modifiers.TextStringSimple` class family and the
`ParagraphLayoutCache` class family. The existing broad project-owned rule and all dependency rules
remain unchanged. The candidate changes only ahead-of-time compilation coverage for the existing
canonical `Text` implementation; it does not change or combine UI code from any rejected label or
state-observation candidate. It adds no dependency, plugin, module, runtime path, public API,
schema, suppression, or waiver. The no-cache release build must prove both rules survive merged-profile
and binary-profile compilation, while the APK identity and packaged-profile preflights remain exact.

The candidate receives one five-warmup/ten-sample fixed schema-v4 `speed-profile` diagnostic. It
advances to the predeclared 50-sample decision batch only if that run passes the independent
total-frame condition with margin. Otherwise the two rules are recorded and reverted without
combining rejected candidates.

## Targeted simple-text profile build result

The two proposed text rules survived as literal entries in the merged textual profile, increasing
that intermediate from 236,252 to 236,408 bytes. They produced no binary-profile change: both the
old and candidate `baseline.prof` are 11,692 bytes with SHA-256
`e20fa086534f8b736a1602bf8602a61695f2ca5a177746a44b068fa93ba15341`, and both
`baseline.profm` files are 466 bytes with SHA-256
`e362ebed73ccc90fb7ce71c10d01ea48906dcf4200dd8babe13a44be7924b066`. This proves that
the dependency profiles' existing expanded coverage already selects every surviving DEX method
matched by the two source rules.

The candidate is rejected at the build preflight, before APK packaging or physical measurement.
There is no executable difference to measure, and running the fixed journey would only select a
favorable sample from the same binary. The two no-op source rules are removed without a production
commit. The full-AOT difference cannot be assigned to these classes, and Issue #54 remains pending
for a distinct retained operation or an independently proven profile gap.

## Next candidate: physical journey hot-method profile supplement

The release-like APK was reinstalled with cleared application data and reset ART profiles. The
packaged profile was installed and dumped before the editor launched, yielding 45,544 expanded
class/method entries. After compiling with `speed-profile`, launching once, and executing 80 exact
Pencil/Undo cycles through the direct input service, the running process flushed a 50,039-entry
profile in 44 ms. Comparing method signatures without their H/S/P flags leaves 3,826 runtime-added
signatures, but only 23 runtime-added methods reached ART's hot classification. This separates the
repeated physical journey from thousands of merely loaded startup/post-startup entries.

The 23 hot methods belong to the actual control/recomposition path: Material3 Button, elevation,
content-style and ripple work; Compose `CompositionLocalMap`; collection lookup; coroutine resume,
channel, flow, job and context work; and emoji metadata lookup used by text. The next candidate adds
exactly those 23 observed method signatures to the existing AGP-native baseline profile with their
observed H/S/P classifications. It does not add broad AndroidX/Kotlin package rules or any loaded-only
entry. Dependency versions are locked, so the exact signatures are reproducible for this source.

This candidate changes only AOT coverage of methods already executed by the canonical Pencil/Undo
journey. It changes no UI, label cache, state observation, document/command path, renderer, dependency,
plugin, module, public API, measurement schema, suppression, or waiver, and does not combine a rejected
production candidate. M5 still owns replacement of manually bounded M2 rules with generated critical-
journey profiles if the application or dependency surface grows materially.

The no-cache release build must prove that the merged and binary profiles change and that both packaged
profile assets remain present. The candidate then receives one five-warmup/ten-sample fixed schema-v4
`speed-profile` diagnostic. It advances to the predeclared 50-sample decision batch only if that run
passes the independent total-frame condition with margin. Otherwise the 23 rules are recorded and
reverted without combining rejected candidates.

## Physical journey hot-method profile diagnostic

The 23-rule candidate changed the merged profile from 236,252 to 239,851 bytes and the compiled
`baseline.prof` from 11,692 to 11,816 bytes; `baseline.profm` remained 466 bytes. Committed source
`30cbcd70cb3b6a8e97cdeec3dadab35c2be62eb3` was then collected once on the fixed physical
profile with five warmups, ten diagnostic samples, packaged-profile `speed-profile`, and schema-v4
identity/environment preflight. All ten samples and four environment checkpoints are valid. The final
hierarchy and screenshot exactly match the canonical Pencil result; fatal/ANR and GC-pattern counts
are zero.

| Metric | Schema-v4 canonical source `0fd18ce5` | 23 hot methods source `30cbcd70` |
| --- | ---: | ---: |
| valid rows | 10 / 10 | 10 / 10 |
| deadline misses | 7 / 10 | 7 / 10 |
| CPU frame p95 | 10.196038 ms | 9.745500 ms |
| input-to-completion p95 | 10.189654 ms | 9.738154 ms |
| platform overrun p95 / p99 | 1.063475 / 1.063475 ms | 0.729125 / 0.729125 ms |
| draw-to-sync-queued p50 / p95 | 1.525538 / 1.608769 ms | 1.427731 / 1.701230 ms |
| animation-to-traversal p50 / p95 | 1.665000 / 1.783692 ms | 1.382115 / 1.651846 ms |
| issue-to-swap p50 / p95 | 4.551154 / 5.019038 ms | 4.708346 / 5.274654 ms |

The targeted compilation supplement reduces animation, CPU, and input p95, but the independent
deadline condition remains positive and draw plus issue-to-swap regress. The run is retained at
`build/reports/m2/frame-follow-up/release-like-physical-hot-profile-diagnostic-10-run-01/`
without overwriting earlier diagnostics.

## Physical journey hot-method profile decision

The 23-method profile supplement is rejected. It did not pass the bounded diagnostic with margin,
so no 50-sample decision batch is run and the source profile change is reverted. No later candidate
may combine or rerun these rules for favorable sample selection. The full-AOT attribution remains
diagnostic evidence only and does not authorize broad compilation as the acceptance configuration.

## Next candidate: retained static editor-chrome layer

A temporary release-like full-AOT attribution build placed trace sections around every operation in
the canonical PixelCanvas draw lambda, collected one post-warmup Pencil frame, and then removed all
trace calls. Tracing overhead makes the frame duration diagnostic only, but the nested section ratios
identify a distinct retained operation. `AndroidOwner:draw` occupies 1.623 ms while all PixelCanvas
sections total about 0.236 ms: state/geometry resolution 0.026 ms, margins 0.008 ms, opaque Bitmap
projection 0.136 ms, Bitmap recording 0.057 ms, preview 0.003 ms, and grid 0.032 ms. The remaining
approximately 1.39 ms records editor chrome and the dynamic history controls through the same owner.

The title, active-color indicator, palette controls, and tool controls do not change in the fixed
Pencil journey. They are nevertheless traversed when the dirty-status and Undo controls invalidate
the Compose owner. The next candidate groups only that unchanged upper chrome in one nested `Column`
backed by a default Compose `graphicsLayer`. The layer retains its display list so the parent can
reference it without re-recording every unchanged text, swatch, outline, and tool control. It does not
force an offscreen compositing strategy or cache document pixels.

The wrapper preserves exact child order, centered horizontal alignment, zero inter-child spacing,
total fixed height, semantics, click paths, colors, and the outer weighted-canvas layout. History,
New Document, PixelCanvas, render-state ownership, command/action paths, and all rejected label/profile
candidates remain unchanged. This adds no alternate renderer, dependency, plugin, module, public API,
schema, suppression, or waiver.

Existing UI tests and a physical screenshot must prove semantics, control behavior, canvas bounds, and
visual order. The candidate receives one five-warmup/ten-sample fixed schema-v4 `speed-profile`
diagnostic. It advances to the predeclared 50-sample decision batch only if that run passes the
independent total-frame condition with margin; otherwise it is recorded and reverted without combining
rejected candidates.

## Retained static editor-chrome layer diagnostic

Committed source `9a5ced42f623e77e3668add4609b99766afc8703` passed all 160 root quality
tasks and all nine physical editor UI tests. It was collected once on the fixed physical profile with
five warmups, ten diagnostic samples, packaged-profile `speed-profile`, and the schema-v4 identity/
environment preflight. All ten samples and four environment checkpoints are valid. The screenshot
retains exact visual order and canvas bounds, and the hierarchy retains the required controls and
state. Fatal/ANR and GC-pattern counts are zero.

| Metric | Schema-v4 canonical source `0fd18ce5` | Static chrome source `9a5ced42` |
| --- | ---: | ---: |
| valid rows | 10 / 10 | 10 / 10 |
| deadline misses | 7 / 10 | 9 / 10 |
| CPU frame p95 | 10.196038 ms | 9.674961 ms |
| input-to-completion p95 | 10.189654 ms | 9.668308 ms |
| platform overrun p95 / p99 | 1.063475 / 1.063475 ms | 2.196191 / 2.196191 ms |
| draw-to-sync-queued p50 / p95 | 1.525538 / 1.608769 ms | 1.368500 / 1.589346 ms |
| animation-to-traversal p50 / p95 | 1.665000 / 1.783692 ms | 1.755962 / 3.248346 ms |
| issue-to-swap p50 / p95 | 4.551154 / 5.019038 ms | 4.504192 / 4.909654 ms |

The retained layer reduces targeted draw p95 by only 0.019423 ms while animation p95 increases by
1.464654 ms and the independent total-frame condition regresses. The run is retained at
`build/reports/m2/frame-follow-up/release-like-static-chrome-layer-diagnostic-10-run-01/`
without overwriting earlier diagnostics.

## Retained static editor-chrome layer decision

The static chrome layer is rejected. It did not pass the bounded diagnostic with margin, so no
50-sample decision batch is run and its production change is reverted. No later candidate may combine
or rerun this layer for favorable sample selection.

## Next candidate: zero-elevation history buttons

The expanded physical journey profile independently identifies
`ButtonElevation.animateElevation`, `ButtonDefaults.buttonElevation`, coroutine continuation, flow,
channel, and job methods among the 23 methods that become hot only after repeated Pencil/Undo cycles.
The rejected profile supplement proved that compiling these methods earlier does not pass the total
frame, but the restored production path still creates and observes an elevation animation state for
both history buttons. Neither button has pointer interaction during the measured Pencil frame; the
Material3 default effective elevation for its normal and disabled states is 0 dp on this screen.

The next candidate passes `elevation = null` to only the Undo and Redo `Button` calls. This uses the
existing Material3 no-elevation path and removes redundant interaction collection and animation state
from the dirty/Undo availability transition. Button labels, enabled semantics, callbacks, shapes,
container/content colors, sizes, spacing, and all document/history behavior remain unchanged. A
physical screenshot must confirm that the zero-effective-elevation appearance remains exact.

The candidate changes no status label, state-observation scope, static layer, compilation profile,
document/command path, renderer, dependency, plugin, module, public API, measurement schema,
suppression, or waiver, and therefore combines none of the rejected candidates. It receives one
five-warmup/ten-sample fixed schema-v4 `speed-profile` diagnostic and advances to the predeclared
50-sample decision batch only if that run passes the independent total-frame condition with margin.
Otherwise it is recorded and reverted without a favorable rerun.

## Zero-elevation history-button diagnostic

Committed source `2e9c22dec74b8acb97cdb2fda73406c2052d64c9` passed all 160 root quality
tasks and all nine physical editor UI tests. Its release-like APK was rebuilt with all 142 build
tasks executed, contains the exact source revision and both packaged profile assets, and verifies
with APK signature schemes v2 and v3. The fixed schema-v4 diagnostic then collected all ten requested
rows and four environment checkpoints after five warmups with packaged-profile `speed-profile`.
The final hierarchy, screenshot, canvas bounds, dirty label, Undo/Redo states, and top-left red pixel
remain exact; fatal/ANR and GC-pattern counts are zero.

| Metric | Schema-v4 canonical source `0fd18ce5` | Zero-elevation source `2e9c22de` |
| --- | ---: | ---: |
| valid rows | 10 / 10 | 10 / 10 |
| deadline misses | 7 / 10 | 5 / 10 |
| CPU frame p95 | 10.196038 ms | 9.676308 ms |
| input-to-completion p95 | 10.189654 ms | 9.668885 ms |
| platform overrun p95 / p99 | 1.063475 / 1.063475 ms | 0.413393 / 0.413393 ms |
| draw-to-sync-queued p50 / p95 | 1.525538 / 1.608769 ms | 1.454346 / 1.737077 ms |
| animation-to-traversal p50 / p95 | 1.665000 / 1.783692 ms | 1.497423 / 1.721308 ms |
| issue-to-swap p50 / p95 | 4.551154 / 5.019038 ms | 4.434423 / 5.089039 ms |

Removing the redundant elevation state reduces CPU/input p95 by about 0.52 ms and platform overrun
p95 by 0.650082 ms, but five of ten frames still miss their deadline. Draw p95 also increases by
0.128308 ms, and the independent total-frame condition remains positive. The single run is retained
at `build/reports/m2/frame-follow-up/release-like-zero-elevation-history-diagnostic-10-run-01/`
without overwriting or rerunning any earlier diagnostic.

## Zero-elevation history-button decision

The zero-elevation history-button candidate is rejected. It did not pass the bounded diagnostic with
margin, so no 50-sample decision batch is run and its production change is reverted. No later
candidate may combine or rerun this change for favorable sample selection. The remaining full-AOT
attribution gap has no acceptance margin, and the traced canonical PixelCanvas work is already only
about 0.236 ms. Issue #54 therefore remains pending; another production candidate requires new,
operation-specific evidence rather than another unbounded UI or profile guess.

## Next protocol correction: exactly one frame row per sample

The full-AOT attribution diagnostic exposed an evidence-integrity gap without changing any accepted
result: one of ten independent Pencil samples emitted two valid `PROFILEDATA` rows, so its aggregate
contained eleven rows. Schema v4 retains and includes every valid row. That is transparent, but it no
longer gives each predeclared sample exactly one observation and can silently change percentile
weighting if unrelated app work creates an additional frame after `gfxinfo` reset.

Before any later diagnostic or decision batch, schema v5 requires each sample to report exactly one
rendered frame, exactly one raw `PROFILEDATA` row, and exactly one valid row with `Flags = 0`. A zero-
row, flagged-row, or multi-row sample invalidates the whole invocation; the script does not select one
row, retry the sample, or continue with a smaller population. The aggregate rendered-frame count and
raw/valid row counts must therefore all equal the requested sample count before percentiles are
calculated. The complete raw response remains retained for diagnosis.

This is a measurement-schema correction only. It changes no input, wait, warmup, clean checkpoint,
compilation mode, environment requirement, correctness check, percentile method, numeric threshold,
production code, dependency, plugin, module, public API, suppression, or waiver. Existing schema-v4
artifacts retain their declared identity and are not relabelled; all later evidence uses v5.

## Schema-v5 physical validation

Committed source `76c438493f484e07de96295d2d6ff8b94ced857f` passed the PowerShell parser and
all 160 root quality tasks. Debug and release-like APKs were then rebuilt with all 142 build tasks
executed. The locally signed release-like APK contains the exact source revision, both packaged
profile assets, and valid v2/v3 signatures.

One fixed physical validation invocation used five warmups and ten diagnostic samples with packaged-
profile `speed-profile`. Every sample reports exactly one rendered frame, one raw `PROFILEDATA` row,
and one valid row; the aggregate counts are therefore 10 / 10 / 10. All four environment checkpoints,
the final UI/canvas checks, and the fatal/ANR checks pass. The run remains a diagnostic and its
positive 3.106164 ms overrun p95 is not promoted to decision evidence. It is retained at
`build/reports/m2/frame-follow-up/release-like-schema-v5-cardinality-validation-10-run-01/`.

Schema v5 is accepted as the sole forward measurement path. The exact-cardinality checks do not make
Issue #54 pass; they ensure that a later evidence-producing operation cannot receive extra percentile
weight from unrelated frames.

## Next candidate: generated critical-journey Baseline Profile

The remaining packaged-profile/full-AOT gap is about 1 ms, while the traced canonical canvas work is
about 0.236 ms. A prior two-rule manual profile candidate was a measured no-op, and the current
packaged profile contains only 23 hot rules. These observations justify one operation-specific profile
producer, not another renderer, UI combination, threshold change, or broad full-AOT rule.

The retained #54 branch's ADR 0010 accepts a build-only `:quality:baseline-profile` module using the
AndroidX Baseline Profile plugin and `BaselineProfileRule`. It records the canonical Pencil/Undo
journey through stable UI
semantics, writes one generated main-source profile for `:app:android`, and removes the hand-written
wildcard profile. Ordinary builds do not invoke a device; the root gate verifies the committed
profile's cardinality and SHA-256 identity.

Acceptance requires two isolated physical-device generation invocations to produce identical
canonical ordered rule-and-flag content. The resulting signed release-like APK must contain the
exact source revision, valid v2/v3 signatures, both packaged profile assets, and a compiled profile
below 1.5 MB. It then
receives one five-warmup/ten-sample fixed schema-v5 `speed-profile` diagnostic. The candidate advances
to the predeclared 50-sample decision batch only if the diagnostic passes the independent total-frame
condition with margin; otherwise its result is recorded without a favorable rerun.

## Generated critical-journey Baseline Profile validation

Committed source `c4d63042bcabb3f3421441d851db73369c90e252` contains the 13,510-rule generated
Pencil/Undo profile retained by that unmerged ADR 0010 decision. The original comparison recorded
the 1,421,321-byte Windows CRLF SHA-256
`2ab1ca1413a5f835b5e30810636d25649dd749034d5b76a9f81aecf09de5cb2a`, but a later audit found
that its equal hashes came from the pre-existing source after failed generation/pull attempts, not
two proven fresh producer outputs. Issue #62 migrates the same ordered rule-and-flag content to
canonical LF SHA-256 `de1a637f6c9884b96ef242deaf0d56c5cd3864c83275e0850d766eb07be4130f`
without relabelling that historical provenance. A clean debug/release build then executed all 146
tasks without the build cache. The locally signed
release-like APK is 8,410,691 bytes with SHA-256
`a311275602f132432c2e623cb088c44db8bf32faa0b62e54e0eb88b926687eaf`; it has valid v2/v3
signatures, embeds the exact source revision, contains both required profile assets, and has a
14,996-byte compiled `baseline.prof`, below the 1.5 MB limit. Physical regression checks also pass
all 11 Compose UI tests and both targeted app lifecycle tests.

One fixed physical diagnostic invocation used five warmups and ten samples with packaged-profile
`speed-profile`. All four environment checkpoints and the final correctness checks pass, with no
fatal or ANR match. Each sample produced exactly one rendered frame, one raw `PROFILEDATA` row, and
one valid row. The result is:

| Metric | Generated critical-journey profile |
|---|---:|
| valid rows | 10 / 10 |
| deadline misses | 1 / 10 |
| CPU frame p95 | 9.177461 ms |
| input-to-completion p95 | 9.170923 ms |
| platform overrun p95 / p99 | 0.023368 / 0.023368 ms |

The single run is retained at
`build/reports/m2/frame-follow-up/release-like-generated-baseline-profile-diagnostic-10-run-01/`.

## Generated critical-journey profile decision

The generated profile does not advance to the 50-sample decision batch. Its platform overrun p95 is
positive by 0.023368 ms, so it does not pass the independent total-frame condition, let alone with
margin. No favorable rerun is permitted. The generated build path remains the intended profile
source in the retained accepted decision, but it is not integrated or usable on current `main`; the
old equal-hash event does not establish fresh producer reproducibility, and schema-v1 provenance
remains pending independently of this performance FAIL. Issue #54 remains open pending both valid
profile provenance and new operation-specific evidence.

## Next evidence: generated-profile scheduling attribution

The generated-profile diagnostic reduces platform overrun p95 to 0.023368 ms, but the sole missed
frame is still 0.023368 ms late. Earlier operation traces predate the generated profile and therefore
cannot distinguish remaining application work from RenderThread, GPU, or scheduler variation in the
new compilation state. Selecting another production change from those older traces would not satisfy
the issue's measured-redundancy requirement.

The next evidence operation is one predeclared release-like Perfetto attribution invocation using the
same exact source APK, packaged-profile installation, `speed-profile` compilation, five Pencil/Undo
warmups, and direct input service as the accepted frame protocol. It records ten subsequent Pencil/
Undo cycles in one system trace with scheduling, frequency, idle, input, view, graphics, RenderThread,
and hardware-composer events. Analysis includes every captured application frame and compares main-
thread work, RenderThread work, runnable delay, graphics submission, and presentation classification;
it does not select only a favorable or late frame.

Tracing overhead, the combined Pencil/Undo population, and the absence of isolated `gfxinfo` reset
boundaries make this attribution evidence only. It cannot satisfy or replace the ten-sample diagnostic
or 50-sample decision batch, has no acceptance threshold, and will not be rerun for a preferred trace.
No production candidate is authorized until this trace identifies a distinct controllable operation.

### Scheduling-attribution protocol correction

The first collection attempt retained all 20 requested input pairs, but a 150 ms post-Undo delay let
the Material button ripple continue for as much as approximately 468 ms. Except for the first Pencil
input, later Pencil state changes overlapped that continuing frame stream and had no unique input-event
to application-frame association. The trace is invalid for the declared isolated Pencil comparison
and remains only at
`build/reports/m2/frame-follow-up/generated-profile-scheduling-attribution-10-cycle-run-01/`.
It authorizes no production candidate and is not selected as a favorable result.

Before the only corrected collection, the post-Undo quiet delay is fixed at 1,200 ms, more than twice
the maximum continuation observed in the invalid trace, and the Perfetto duration is fixed at 35
seconds. The APK, profile installation, compilation, five warmups, input coordinates, ten Pencil/Undo
cycles, trace categories, whole-population analysis, and attribution-only status remain unchanged.
Every Pencil input must have a nonzero input-event ID associated with exactly one application frame;
otherwise the corrected trace is invalid and will not be rerun.

### Scheduling-attribution result

The corrected collection executed the exact APK/profile setup, five warmups, ten Pencil/Undo cycles,
1,200 ms post-Undo delays, and final clean-state check. Its local trace pull occurred approximately
three seconds before the fixed 35-second Perfetto session closed, however, and returned an empty file;
the required nonzero input-event associations therefore cannot be proved. The corrected collection is
invalid and is not rerun. Its empty artifact remains at
`build/reports/m2/frame-follow-up/generated-profile-scheduling-attribution-10-cycle-run-02/` for
auditability and authorizes no production change.

The valid generated-profile diagnostic already retains all ten raw `PROFILEDATA` rows. Decomposing
every row without another device invocation gives the following nearest-rank phase distribution:

| Frame phase | p50 | p95 | minimum | maximum |
|---|---:|---:|---:|---:|
| input handling to animation | 0.000770 ms | 0.001269 ms | 0.000692 ms | 0.001269 ms |
| animation to traversal | 1.011539 ms | 1.188269 ms | 0.830192 ms | 1.188269 ms |
| traversal to draw | 0.055692 ms | 0.066231 ms | 0.051538 ms | 0.066231 ms |
| draw to sync queued | 1.224769 ms | 1.398577 ms | 1.061423 ms | 1.398577 ms |
| sync queue wait | 0.358192 ms | 0.426923 ms | 0.085346 ms | 0.426923 ms |
| sync start to issue | 0.078192 ms | 0.092154 ms | 0.073270 ms | 0.092154 ms |
| issue to swap | 4.481423 ms | 5.096731 ms | 1.660038 ms | 5.096731 ms |
| swap to completion | 1.138384 ms | 1.396808 ms | 1.038923 ms | 1.396808 ms |

The sole deadline miss is the row with the maximum 5.096731 ms issue-to-swap phase. That phase varies
by 3.436693 ms across the fixed population and is larger than either application-side animation or
draw phase. A separate exploratory isolated-frame trace, retained outside acceptance evidence at
`build/reports/m2/frame-follow-up/generated-profile-perfetto-attribution-run-01/`, supports the same
boundary: its 6.603193 ms frame contains 3.419 ms of main-thread `doFrame`, 3.418 ms of RenderThread
work, and only 0.052 ms of 16 x 16 texture upload. The earlier instrumented production trace already
bounded all canonical PixelCanvas sections at approximately 0.236 ms.

There is therefore no newly identified one-millisecond application operation to remove. Previously
rejected label, observation-scope, layer, elevation, Bitmap, alpha, and manual-profile candidates may
not be recombined or rerun for favorable selection, and replacing the renderer remains out of scope.
The generated profile stays canonical, the 50-sample decision batch is not run, and Issue #54 remains
open with no further evidence-backed production candidate in the current scope.

## FrameTimeline physical-present attribution v1

Issue #54 now authorizes one renderer-milestone follow-up that closes the remaining boundary between
the generated-profile app frame and physical presentation. This exercises ADR 0005's expressly
deferred SurfaceFlinger/Perfetto correlation at P2-08; it does not change ADR 0005's acceptance
meaning, the command model, the renderer boundary, or any production implementation. The applicable
rules are M2 P2-08, ARC-012, and QLT-003 through QLT-006 plus QLT-010.

The fixed schema is `nene-pixel-m2-physical-present-v1`. Its only capture entry is
`docs/quality/measurements/measure-m2-frame.ps1`; the script invokes the retained internal analyzer
`analyze-m2-physical-present.ps1`. The lane is fixed to a revision-exact signed release-like APK,
both packaged Baseline Profile assets, successful profile installation, `speed-profile` compilation,
five Pencil/Undo warmups, direct input-service injection, and exactly ten isolated Pencil samples.
The existing schema-v5 frame checks, clean checkpoint between samples, display/power/thermal checks,
final dirty/Undo/Redo/canvas checks, and fatal/ANR scan remain mandatory and unchanged.

Perfetto starts only after the Activity, fixture, warmups, and `before_samples` checkpoint are
complete. It stays inside that Activity lifetime while the ten samples and intervening clean Undo
operations execute. The completed `frames.csv`, `samples.csv`, final UI evidence, and
`after_samples` checkpoint are published before the stop trigger. Collection then uses the unique
batch trigger with `/system/bin/trigger_perfetto`, waits for the background recorder PID to exit,
verifies a positive finalized device byte length, and only then pulls the trace. A manual kill,
timeout-as-stop, early pull, pre-existing exact staging path, or second collection for a preferred
result invalidates the operation.

The retained 64 MiB ring-buffer config records `android.surfaceflinger.frametimeline`, scheduler
switch/wakeup, CPU frequency/idle, Android graphics/view/input/binder/HAL slices, process state, clock
snapshots, and the trace config itself. Host analysis is pinned to Trace Processor
`v49.0-33a4fd078`, 10,479,616 bytes, SHA-256
`a881f3e2d4c6131493e85bfd1f36d1efe58e1478e2991825418d5d21614c1e48`. FrameTimeline parser or
pairing errors, trace-buffer overwrite/discard/loss/wrap, ftrace drop/overrun, failed final flush,
missing successful final flush, missing clock conversion, or any incomplete output invalidates the
whole population.

Each of the ten positive, unique schema-v5 `frame_timeline_vsync_id` values must select exactly one
buffer-marked app actual frame and exactly one app expected frame for the package. Its positive
display-frame token must select exactly one SurfaceFlinger actual display frame and one expected
display frame. The app layer identity must be non-empty and stable. Negative durations, non-positive
input-to-present values, `Dropped Frame`, `Unknown Present`, or any present type other than Early,
On-time, or Late invalidates the population. The physical endpoint is
`to_monotonic(surfaceflinger_actual.ts + surfaceflinger_actual.dur)`, joined through the exact app
surface token and display token rather than nearest timestamps.

Attribution is deterministic and retained per sample. A frame with neither app nor SurfaceFlinger
late-present/late-finish evidence is `on-time`. For a late app frame, relevant main/RenderThread
runnable time covering at least its positive actual-versus-expected overrun is `scheduler`; remaining
positive overrun is `app`; contradictory non-positive overrun is `unresolved`. The same rule applies
to a late SurfaceFlinger frame using its threads, producing `scheduler`, `surfaceflinger`, or
`unresolved`. Raw scheduler states and all overlapping app, RenderThread, SurfaceFlinger, composer,
HWC, and presentation slices are retained so the classification is auditable rather than replacing
the underlying measurements.

The immutable output set comprises the raw trace, exact config, collection/tool manifest, schema-v5
frames/samples/environment/UI/logcat evidence, exact correlation/scheduler/workload/integrity/clock
SQL, their machine-readable CSV outputs, Trace Processor log, and physical-present metadata with
trace/tool hashes and nearest-rank p50/p95/p99. This is an attribution population with no new pass
threshold. It cannot replace or reopen the rejected ten-sample generated-profile diagnostic, cannot
promote a 50-sample decision run, and authorizes no production change unless the retained whole
population identifies a distinct controllable operation.

### Physical-present startup correction

The first invocation, retained at
`build/reports/m2/frame-follow-up/release-like-physical-present-attribution-10-run-01/`, is invalid
before the sample population. Producer acknowledgement succeeded, but the harness applied
`kill -0` to the PID printed by `--background-wait` and treated its exit as recorder termination.
The finalized 399,779-byte trace, SHA-256
`bd3e406008440376a98c497d97ac5f35fef4a67b0c69b578c1725229e654a901`, proves that PID was the
short-lived launcher while a second same-command Perfetto process owned the service session. The
declared stop trigger produced a successful final flush with zero FrameTimeline parser/pairing and
packet-loss/wrap values. No Pencil sample ran, and the trace contains zero app actual frames, so this
failure exposes no performance outcome and cannot be used for selection.

Before the only corrected invocation, the config adds the unique trigger-derived session name.
After producer acknowledgement the harness requires exactly one matching session in `perfetto
--query --long`; after the unchanged trigger-only stop it requires that exact session to disappear
before reading the finalized remote byte length and pulling. The launcher-reported PID is retained
for audit but is no longer treated as the service-session lifetime. APK/profile mode, warmups, ten
samples, input/Undo timing, trace sources and buffer, query/analyzer, integrity gates, classification,
and attribution-only status remain unchanged. The corrected invocation is `run-02`; it is the last
authorized collection under this schema and will not be repeated for any result.

### Physical-present corrected collection result

The final authorized `run-02` passed the named-session startup guard and began the unchanged sample
loop. Samples 1 through 4 each produced one valid schema-v5 row. Sample 5 produced two valid rows for
one Pencil tap, with distinct positive FrameTimeline vsync IDs, so the fixed one-frame-per-sample
condition failed immediately. The harness stopped without running samples 6 through 10; none of the
six captured rows is accepted or substituted as the requested population.

The declared trigger did finalize and pull the raw trace before cleanup. A host-only manifest append
then used an unavailable three-argument `AppendAllLines` overload and emitted the cleanup warning;
the helper now uses an explicit UTF-8 append writer, with no measurement or analyzer change. The
retained trace is 11,841,418 bytes with SHA-256
`1528150d50163f5d3989e9150f0332b13dd22db41f22bd37868c947cdd371a47`. It records a successful
final flush and zero FrameTimeline parser/pairing, buffer overwrite/wrap, and packet-loss values, but
also records four discarded trace chunks. That independently violates the predeclared integrity
gate.

The raw trace, exact config/tool manifest, five raw `gfxinfo` files, six-row invalid-cardinality CSV,
exact integrity query/CSV, Trace Processor log, and invalid metadata remain under the `run-02`
directory. They are audit artifacts only. There is no valid ten-row app-to-SurfaceFlinger population,
no physical-present percentile or attribution classification, and no evidence-backed production
candidate. The schema permits no further collection, so Issue #54 remains open and the 50-sample
decision batch remains prohibited.

## FrameTimeline physical-present attribution v2

Issue #54 authorizes one new focused attribution schema after both v1 invocations were closed as
invalid. Schema `nene-pixel-m2-physical-present-v2` does not reinterpret either v1 artifact and does
not change the existing schema-v5 acceptance lane. It corrects two attribution-only assumptions
exposed before any valid physical-present population existed: a traced Pencil tap may schedule a
follow-up app frame, and the service-global informational `traced_chunks_discarded` counter is not a
per-buffer loss counter for the current trace.

The canonical capture entry, exact-revision signed release-like APK, packaged-profile installation,
`speed-profile` compilation, five warmups, ten clean-checkpoint Pencil taps, direct input service,
64 MiB ring buffer, data sources, named-session lifetime, trigger-only stop, finalized-byte check,
pinned Trace Processor, UI/environment checks, and no-favorable-rerun rule remain unchanged. The sole
v2 invocation is `release-like-physical-present-v2-10-run-01`; no second v2 collection is authorized.

For each sample, `dumpsys gfxinfo reset` still occurs immediately before one Pencil tap and the fixed
350 ms wait. The sample must contain at least one raw `PROFILEDATA` row, every row must have flags 0,
and `Total frames rendered` must equal the raw row count. Across the batch, all ten contiguous sample
indexes must exist, every positive FrameTimeline vsync ID must be globally unique, and every captured
row is retained. A second or later frame is not discarded, selected away, or treated as another
sample.

Each retained row must independently join one buffer-marked app actual frame, one app expected frame,
one SurfaceFlinger actual display frame, and one SurfaceFlinger expected display frame by its exact
surface and display tokens. The existing duration, layer, present-type, clock-conversion, scheduler,
and workload checks apply to every row. The per-sample physical duration is conservatively fixed as
the latest correlated SurfaceFlinger physical-present end minus the earliest `HandleInputStart` among
that sample's complete rows. Nearest-rank p50/p95/p99 use the ten resulting sample durations; no frame
or sample is removed as an outlier. Per-frame attribution remains `on-time`, `app`, `surfaceflinger`,
`scheduler`, or `unresolved`; the sample output retains the sorted set of every class it contains.

Trace integrity rejects every nonzero Trace Processor `data_loss` or `error` statistic, all
FrameTimeline parser/pairing errors, per-buffer overwrite/discard/wrap, packet loss, ftrace drop or
overrun, failed flush, missing successful final flush, or unavailable clock conversion. The
service-global informational `traced_chunks_discarded` and `traced_patches_discarded` counters are
retained but do not independently invalidate v2: Perfetto defines them as chunks or patches rejected
before attempted buffer commit, for example because a producer supplied an invalid buffer ID. This
distinction is fixed before collection and does not excuse any current-trace per-buffer or
severity-marked loss.

Before collection, the v2 analyzer was regressed against the retained v1 run-02 trace and its
six-row/five-sample invalid-cardinality input. It preserved all six correlations, aggregated one to
two frames per sample, and left the service-global four discarded chunks informational. This proves
the parser path only; its five-sample durations and attribution classes are not v2 measurement
evidence and cannot be used for a production decision.

V2 is still attribution-only. Its conservative physical-present percentiles have no pass threshold,
do not replace schema-v5 app-frame results, and cannot by themselves advance the 50-sample decision
lane. A production candidate requires one distinct controllable operation supported by the complete
v2 population; an invalid v2 result authorizes no further collection or production change.

### Physical-present v2 collection result

The sole authorized invocation, `release-like-physical-present-v2-10-run-01`, completed as
`valid-attribution` on source `dde10c1918258083563c03e86e035a4aaeb19f95`. The exact-revision signed
release-like APK is 8,402,300 bytes with SHA-256
`710b73c7e3cd1f5b09e84e2e310fce57ce58cd61c63eeb89ad9bfbcea8b2d2e7`; v2/v3 signatures, packaged
`baseline.prof` / `baseline.profm`, successful profile installation, and `speed-profile` compilation
were verified before the five warmups and ten Pencil samples. All four environment checkpoints held
the fixed 1200 x 1920, 90 Hz, USB-powered, non-power-save profile at thermal status 1. Correct final
pixel, dirty, Undo, Redo, and canvas state passed with zero fatal, ANR, or GC-pattern matches.

The ten samples retained 11 / 11 valid raw rows and exact app-expected, app-actual,
SurfaceFlinger-expected, and SurfaceFlinger-actual correlations. Samples 1–3 and 5–10 each contain one
frame; sample 4 contains two. Conservative input-to-last-physical-present durations in sample order
are 22.244501, 22.754385, 22.830732, 45.630577, 33.113385, 22.771308, 23.052654, 33.622231,
22.531115, and 33.540154 ms. Nearest-rank p50/p95/p99 are therefore 22.830732 / 45.630577 /
45.630577 ms. These values remain attribution-only and have no acceptance threshold.

All 11 SurfaceFlinger frames were on-time. Six app frames were on-time and five were late. Per-frame
classification is three `app`, five `scheduler`, two `on-time`, and one `unresolved`; the unresolved
row is sample 4's second, buffer-stuffed frame. Sample 4's first frame is the largest app miss at
5.804262 ms over deadline and contains a 5.330539 ms RenderThread `CreateGraphicsPipeline` below one
`FillRectOp`. The ACTION_UP arrives while that first frame is in progress and schedules the retained
second frame, making sample 4's last physical presentation the batch maximum. The pre-UP state is
consistent with the translucent gesture preview, but the complete trace carries no source tag that
uniquely ties the pipeline to that or another canonical rectangle, and the event occurred despite the
fixed five warmups. It therefore is not yet a distinct controllable production operation. The
separately visible SurfaceFlinger region-sampling workload also caused no late SurfaceFlinger frame.
No production candidate or 50-sample decision batch is authorized from v2.

The trigger-finalized trace is 25,989,794 bytes with SHA-256
`2d5aeb1a35a4e0123c3822e47b04cb7c4e7dcf666c8a3045c631afca23652a5d`. Final flush succeeded once;
all parser/pairing, per-buffer overwrite/discard/wrap, packet-loss, ftrace drop/overrun, failed-flush,
and severity-marked loss/error values are zero. Service-global `traced_chunks_discarded=5` and
`traced_patches_discarded=0` are retained as predeclared informational counters. The raw trace,
per-frame and per-sample correlations, scheduler and workload rows, clock snapshots, integrity rows,
UI evidence, logcat, and tool/config manifests are retained locally without a second v2 collection.

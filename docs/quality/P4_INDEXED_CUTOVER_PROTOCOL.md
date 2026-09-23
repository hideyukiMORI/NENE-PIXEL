# P4 Indexed Cutover Verification Protocol

Status: accepted contract for Issue #106. No sample is collected under this identity: it admits the
host, command, memory and publication results that experiment `p4-indexed-v6-20260917-run5`
collected under revision v6, and it owns no frame verdict.

Rules: QLT-006–009/011–016. Waivers: none.

This contract fixes metrics, workload definitions, order, warmups, samples, thresholds, finite
budgets, stopping rules, production/build identities and required collector changes before #106
collection. It is based on independent review of the current Issue, ADR 0018 and
existing M2/M3 executable collectors. Historical protocols/results remain unchanged.

The current collectors do not yet implement this contract. Ordinary implementation and narrow
correctness checks may proceed under QLT-015. Before any acceptance sample, a no-sample preflight
must prove agreement between the Issue, this accepted protocol, implemented harness/schema and
exact artifacts/profile/device identity. A placeholder or mismatch blocks collection.

## Overall experiment identity and admission

Protocol identity: `nene-pixel-p4-indexed-cutover-verification-v7`.

Revision v7 supersedes the
[frame-failed v6 contract](P4_INDEXED_CUTOVER_PROTOCOL_V6_HISTORICAL.md). Under v6, experiment
`p4-indexed-v6-20260917-run5` collected 32 of its 33 slots and stopped: its five host slots are
`valid-descriptive`, `command-baseline` and `command-candidate` are `pass`, its twenty memory slots
are `pass`, its two publication slots are `valid-constants-retained`,
`frame-1-baseline-diagnostic` and `frame-2-candidate-diagnostic` are `inconclusive`, and
`frame-3-candidate-decision` is `PERFORMANCE_FAIL` because its `canvas16_tap` all-frame overrun
nearest-rank p95 is 1.012 ms against the unchanged 0.0 ms gate. `frame-4-baseline-decision` never
ran. The baseline does not meet that same gate under the same conditions: its diagnostic overrun
p95 is 1.136 ms for `canvas16_tap` and 8.368 ms for `canvas256_repeated_diagonal`, against 1.077 and
8.628 ms for the candidate, and the committed-result p95 stays between 10 and 13 ms for every
family and role with zero fatal/ANR matches. The frame failure is therefore a measurement of main's
own frame budget against an absolute gate, not evidence of a candidate regression, and it is not a
harness, analyzer or wrapper contract defect, so neither bounded recovery below applies to it. On
hide's decision of 2026-09-22 the frame verdict and main's frame budget move to Issue #120, and
Issue #106 is accepted on its host, command, memory, publication and Lane 6 functional evidence.
**V7 changes exactly four things**: the protocol identity; the manifest schema, which becomes
`nene-pixel-p4-indexed-preflight-v7`; the removal of Lane 3 and its four frame slots from this
Issue's fixed order, stop order and acceptance, which Issue #120 now owns; and the admission of the
host, command, memory and publication results collected under v6 in run5 as this contract's
acceptance evidence, with no new collection. Populations, workloads, metrics, numeric gates,
warmups, sample counts, finite budgets and bounds, stopping rules and every lane schema name are
unchanged from v6, and every v6 verdict, the three frame verdicts included, remains reported exactly
as it was collected.

That admission is a QLT-012 identity-bound reuse of an existing record, not a relabelling and not a
new experiment. The admitted results are the twenty-nine non-frame slots of experiment
`p4-indexed-v6-20260917-run5`, whose raw records are preserved unchanged under
`build/reports/issue-106/p4-experiment-v6-run5/<slot>/`. That experiment was reserved by manifest
`build/reports/issue-106/p4-preflight-manifest-v6-3fd7817-run5b.json`, SHA-256
`4f2adc47d71c79538677760d40902264ef3062df8c53a43b20eb7d609b1c4653`, copied byte for byte to
`p4-experiment-v6-run5/preflight.json`, and it bound candidate production commit
`9fde9352e93f4421dc411f7a72a52fcf5a9a2ed7` built at `3fd7817e44910b6289556d9407f5426e7ffb0dbc`
against baseline production commit `2dd4e01e3bbe88967237cde4e28412d2962fd590` built at
`0b605481ad97ee3726864e556e6519f3a862271f`. Every admitted record carries
`nene-pixel-p4-indexed-cutover-verification-v6` as its `protocol_id` and that manifest hash as its
`preflight_sha256`; nothing is renamed to v7, re-judged, recomputed, pooled or collected again, and
[the v6 archive](P4_INDEXED_CUTOVER_PROTOCOL_V6_HISTORICAL.md) holds the bytes those records were
collected under. Reuse is admissible here because the populations, workloads, metrics, gates and
budgets of those four lanes are identical in v6 and v7 and the measured artifacts are unchanged.

V6 superseded the
[failed v5 contract](P4_INDEXED_CUTOVER_PROTOCOL_V5_HISTORICAL.md). Under v5, experiment
`p4-indexed-v5-20260917-run4` collected its five host slots as `valid-descriptive`, produced `pass`
for `command-baseline`, and produced `PERFORMANCE_FAIL` for `command-candidate`: against the
unchanged absolute gate of 8.0 ms p95 and 16.67 ms p99, `palette_recolor_full` measured 10.953 ms
p95, `palette_default_only` measured 11.030 ms p95, and `palette_many_to_one_dense` measured
17.141 ms p95 and 17.625 ms p99. The six common workloads passed for both roles. Collection stopped
there under step 10 of the fixed order and every collected slot is preserved exactly as collected.
That failure is a measurement of the candidate's own production behaviour, in the palette remap
path's per-pixel allocation and its full-raster scan of an identity remap, not a harness, analyzer
or wrapper contract defect, so neither bounded recovery below applies to it. The candidate
production source is corrected as a focused change under this same Issue, and collection against
that corrected build requires a new protocol identity and review, which was revision v6. **V6
changes nothing but the identity and the manifest schema**, which becomes
`nene-pixel-p4-indexed-preflight-v6`. Populations, workloads, metrics, numeric gates, warmups,
sample counts, finite budgets and bounds, fixed slot order, stopping rules and every lane schema
name are unchanged from v5, and the preserved v5 verdicts remain reported as they were collected.
V5 superseded the [superseded v4 contract](P4_INDEXED_CUTOVER_PROTOCOL_V4_HISTORICAL.md), whose
experiment `p4-indexed-v4-20260916-run3` is preserved as `superseded` because review found the frame
and command lane wrapper bounds defective before any device slot started; v5 derived the frame
wrapper bound from the operation count, raised the command instrumentation bound to 600 seconds and
added the pre-emptive supersede rule below.
V4 superseded the [invalidated v3 contract](P4_INDEXED_CUTOVER_PROTOCOL_V3_HISTORICAL.md), whose
experiment `p4-indexed-v3-20260916` is preserved as `INVALID` because the analyzer required 1-based
host sample numbering while the accepted host runners emit 0-based indices; v4 stated the 0-based
host CSV sample index and added the bounded recovery rule below.
V3 superseded the [uncollected v2 contract](P4_INDEXED_CUTOVER_PROTOCOL_V2_HISTORICAL.md).
V2 superseded the [uncollected v1 contract](P4_INDEXED_CUTOVER_PROTOCOL_V1_HISTORICAL.md).
No v1 or v2 sample or verdict exists. Independent implementation review found that device collectors
cannot observe application-internal HistoryPosition and that a preview operation requires an
already-admitted source. V2 fixes those observation and operation-precondition boundaries before
collection. All populations, metrics, numeric gates, budgets and stop rules remain unchanged.
Exact history-position correctness remains mandatory at its owning application boundary; no
measurement-only production API is introduced. The archived v1 bytes are historical, not an
alternative current collection route.
V3 makes the import-retention observation explicit: stale handles prove unusability, not release.
Device evidence combines old public-preview weak references with the fixed post-cycle GC and
owning application correctness for the underlying snapshot. No reflection, extra collection-cycle
GC, production observation API, threshold or sample-budget change is introduced.

Fixed production roles for the Issue #106 lanes (Lane 3 under Issue #120 binds its roles in the
Lane 3 section below):

| Role | Production source | Measurement build source |
| --- | --- | --- |
| baseline | `2dd4e01e3bbe88967237cde4e28412d2962fd590` | an immutable collector-overlay commit based on that production source, unset until preflight |
| candidate | the one coherent final #106 production commit, unset until preflight | an immutable collector/build commit whose production tree equals that production source, unset until preflight |

The baseline overlay may change measurement-only source but its production-tree
aggregate must equal the production tree at `2dd4e01`. Each clean worktree,
APK embedded revision and compiled/harness aggregate identifies the actual
measurement build commit. None is rewritten or labelled as `2dd4e01`; the
separate production-tree aggregate is the proof that baseline production is
unchanged.

The baseline is collected afresh for every comparative lane. No M2, M3, #105,
or #111 timing/memory record is reused as #106 evidence. Existing records
justify the unchanged route and threshold; they do not fill a #106 slot.

Before any collection, one fail-closed manifest whose schema is
`nene-pixel-p4-indexed-preflight-v7` must contain non-placeholder values for:

- protocol ID and hash of its accepted canonical bytes;
- baseline and candidate 40-character commits and clean tracked worktrees;
- relevant production-tree aggregate, measurement-source aggregate, compiled
  class aggregate, runner, analyzer, init-script, wrapper, and validator
  SHA-256 values;
- Gradle wrapper, version catalog, dependency locks, verification metadata,
  JDK/JVM, Gradle, AGP, Kotlin, Compose, Android build tools, and OS identity;
- app and AndroidTest APK paths, byte counts, SHA-256 values, embedded source
  revision, variant, target/test packages, and requested plus observed dexopt;
- committed Baseline Profile source/acceptance/pair manifests, canonical profile,
  packaged `.prof`/`.profm`, and their SHA-256 values for frame roles;
- device serial, physical profile, manufacturer/model/product/device, API,
  fingerprint, security patch, display mode/size/rate, rotation, thermal,
  power-save, interactive, USB-power, battery, and locale state;
- exact workload catalog/order, warmups, sample counts, thresholds, timeout and
  stop policy for the lane; and
- a new experiment/output directory whose path does not already exist.

All hash fields initially contain the literal state `UNSET_PREFLIGHT`; that
state rejects collection. The implementation phase replaces them only after
the final source, collectors, and artifacts exist. A later prose commit may
refer to an immutable artifact only after QLT-012 equivalence is proved; it may
not relabel it.

Correctness for a lane must pass first on the same candidate source. Wrong
identity, missing output reservation, non-quiescent concurrent build or
measurement, incomplete capture, wrong row order/count, ambiguous association,
device-condition drift, exception, crash/ANR/fatal signal, or timeout is
`INVALID`. A complete numeric threshold miss is `PERFORMANCE_FAIL`. Both are
preserved and stop every dependent later slot. No sample is removed or
replaced. Every slot has maximum attempt 1; any further collection requires a
new protocol identity and review.

One bounded recovery exists for a defect that is not a measurement. When an
`INVALID` is caused by a harness, analyzer or wrapper contract defect that is
unrelated to the measured values, and it occurs before any PERFORMANCE verdict
has been produced under that experiment identity, the affected slot and its
whole experiment id are preserved as `INVALID`, the defect is corrected under
QLT-015, and the fixed slot order restarts from its first slot under a new
experiment id. This is a new experiment, never a retry of a slot: no sample is
selected, discarded, replaced, repaired or pooled across experiment ids, no
population, threshold, budget or order changes, and the preserved `INVALID`
remains reported. Once any slot under an experiment identity has produced a
`PERFORMANCE_PASS` or `PERFORMANCE_FAIL`, this recovery is unavailable and
further collection again requires a new protocol identity and review. A defect
in the measured production behaviour, in the device or in the collection
conditions is not a harness contract defect and is not recoverable this way.

A second bounded rule covers the same class of defect found one step earlier.
When a harness, analyzer or wrapper contract defect is found by review before
any device slot of an experiment has been started, that is before any device
slot has written `started.json` under that experiment id, and therefore before
any PERFORMANCE verdict exists under that experiment identity, that whole
experiment is `superseded`: every slot it has already completed is preserved
exactly as collected and reported under the `superseded` experiment id, nothing
is pooled, selected, discarded, replaced or re-judged across experiment ids, the
defect is corrected under QLT-015, and the corrected fixed slot order restarts
from its first slot under a new experiment id. No slot record carries the
`superseded` state; it is recorded in the archived contract's preamble and in
the final report. This is the review-found counterpart of
the bounded recovery above, which requires an observed `INVALID`; both are
unavailable once any slot under an experiment identity has produced a
`PERFORMANCE_PASS` or `PERFORMANCE_FAIL`. Experiment
`p4-indexed-v4-20260916-run3` is the first application: its five host slots are
preserved as complete `valid-descriptive` captures, no device slot ran, and the
v5 frame and command bound corrections restart the fixed order under a new
experiment id.

## Lane 1: production command latency and ART blocking GC

Schema: `nene-pixel-p4-indexed-command-latency-v1`.

Actual entry to adapt:
`P2AndroidFinalCommandMeasurementTest` through
`P2AndroidFinalCommandProtocol`, `P2AndroidCommandWorkloads`, the existing
runner/report/validator, and a versioned bounded host wrapper. It remains the
one production `CommandGateway.execute` measurement path.

Fixed order is one baseline invocation, then one candidate invocation.
Baseline contains the six common workloads; candidate contains those six then
five indexed-only workloads:

| Order | Workload | Exact prepared operation |
| ---: | --- | --- |
| 1 | `sparse_apply_stroke` | 256 by 256; one diagonal, 256 positions |
| 2 | `dense_apply_stroke` | 256 by 256; all 65,536 pixels |
| 3 | `dense_eraser_stroke` | 256 by 256; all pixels to captured default |
| 4 | `dense_same_target_no_op` | all 65,536 pixels already contain the captured target |
| 5 | `dense_undo` | undo the prepared full-canvas drawing transition |
| 6 | `dense_redo` | redo the prepared full-canvas drawing transition |
| 7 | `palette_recolor_full` | candidate only; 256-slot definition, every pixel uses changed slot 0 |
| 8 | `palette_default_only` | candidate only; 256-slot definition, default 0 to 255, unchanged index raster |
| 9 | `palette_many_to_one_dense` | candidate only; 256 slots and all slot values present, map to a two-slot destination |
| 10 | `palette_many_to_one_undo` | candidate only; exact inverse of workload 9 |
| 11 | `palette_many_to_one_redo` | candidate only; exact forward replay of workload 9 |

The common fixtures have identical visible RGBA before and after in both roles.
Indexed slot identity and palette/default facts are additional candidate facts.
One separate full correctness execution per workload precedes warmup. Each
workload has exactly 5 untimed warmups and 200 measured samples. Baseline has
1,200 rows; candidate has 2,200. `System.nanoTime()` surrounds one prepared
`CommandGateway.execute` only. Fixture construction, full document/pixel
comparison, hashes, forced GC, and report I/O remain outside and between no
timed samples. Cheap post-timer facts use only public application contracts: prepared
source-admission/document revision, result kind, resulting document revision and history
availability, ChangeSet before/after revisions and invalidation, expected definition/default
identity, and unchanged DocumentState identity for no-op. Internal HistoryPosition and
PaletteTransition/IndexChanges are not exposed or probed by an additional execute operation.
Exact position movement, inverse restoration, replacement branches and stale-source rejection
remain mandatory core:application correctness tests before collection. ART snapshots are
outside the direct timer as in the current collector.

For each workload independently, using nearest-rank with all 200 rows:

- p95 must be at most 8.0 ms (rank 190);
- p99 must be at most 16.67 ms (rank 198); and
- at least 190 of 200 rows must have zero blocking-GC increment.

No cross-role ratio is a gate. Common-workload baseline/candidate deltas are
reported descriptively. Both roles must satisfy the unchanged absolute gate;
all five candidate-only workloads must also satisfy it.

The physical device/debug compilation contract stays the current profile:
debug app and test APK, target `verify` dexopt, the fixed iPlay80miniPro API 36
profile, and physical checkpoints before samples, every 25 global samples, and
after samples. One instrumentation invocation is bounded at 600 seconds. That
bound guards a hang only: the candidate role runs 2,200 samples whose
palette-workload per-sample cost is unmeasured, and no measured value depends on
the bound. Ordinary native calls use 30 seconds, install/compile calls 120
seconds, and post-timeout process kill/wait and capture drain use 10 and 5
seconds. A first invalid or performance-fail role stops the second role.

Required harness changes are a new candidate plan/schema/catalog, indexed
fixture construction, palette-transition cheap facts, fail-if-present output,
and a checked wrapper/analyzer that accepts exactly the 1,200/2,200 populations.

## Lane 2: retained history and import-operation memory

Schemas:

- `nene-pixel-p4-indexed-history-retention-v1`;
- `nene-pixel-p4-palette-history-retention-v1`; and
- `nene-pixel-p4-legacy-import-retention-v1`.

Actual entry to adapt: `P2ProductionHistoryRetentionMeasurementTest` and the
existing process-identity/report-status boundary. Add an import-retention method
or sibling class in the same measurement package; do not create a second
memory framework.

Run exactly five fresh instrumentation processes for each family, in this
order; run indices are 1 through 5 within each family:

1. baseline common drawing history: 64 entries, 8,192 changed pixels per entry,
   524,288 total changes;
2. candidate common indexed drawing history with the same canvas, visible
   colors, entry count, and change count;
3. candidate palette history: 64 alternating 256-to-256 definitions, 8,192
   changed indices per entry, complete many-to-one-safe before/after data, and
   524,288 total changes; and
4. candidate import checkpoint: maximum indexed current document, exact
   256-by-256 legacy source with 65,536 distinct RGBA values, one 256-entry
   reduced preview, its source/operation/lineage/destination identities, and no
   second source or preview.

The history families take a two-pass post-GC baseline, populate, prove the
complete 64-step Undo/Redo round trip outside memory capture, take retained
post-GC, execute 10 more Undo/Redo cycles, and take post-cycle post-GC. The
import family takes baseline, retains the one active conversion operation and
preview, takes retained post-GC, replaces the destination/preview 10 times
sequentially, and takes post-cycle post-GC. Each replacement proves that the old handle is stale
and the current operation publishes one selected preview. Retain only weak references to the ten
old public LegacyReductionProjection objects, then assert all are cleared after the one fixed
post-cycle two-pass GC. Do not force additional GC between replacements. Stale-handle and weak-
projection checks do not by themselves prove collection of an underlying snapshot retained
elsewhere. Mandatory application-boundary correctness separately verifies that replacement drops
the old bound planner preview/snapshot and projection owners. It uses existing internal completion
and transaction boundaries, without reflection, measurement-only getters or injected workspaces.
Full owner inventories occur only at fixed checkpoints.

The 8 MiB logical payload guard is not reachable under the other accepted
document-history guards. At 524,288 changes and 64 maximum 256-to-256 palette
transitions, the maximum is exactly:

```text
524,288 * 6 + 64 * (32 + 4 * (256 + 256) + 8)
= 3,279,360 logical bytes
```

Therefore the device lane must report that reachable value; it must not claim
to measure an 8 MiB retained history. Unit/property contracts verify the
8 MiB formula, checked arithmetic and reject/eviction order. The device lane
measures actual retained owners at the reachable simultaneous maximum.

Unchanged gates apply separately to every family:

- every retained Java heap is at most 50% of `Runtime.maxMemory`;
- every retained PSS is at most its paired baseline plus 60% of
  `ActivityManager.memoryClass`;
- the median of the five paired PSS deltas is at most 50% of memory class; and
- every post-cycle Java-heap growth is at most
  `max(1 MiB, 1% of Runtime.maxMemory)`.

Baseline/candidate common-history medians and individual deltas are descriptive
comparisons; each family must pass the absolute gates. Every invocation has a
300-second instrumentation timeout and the same 30/120/10/5-second native
limits. PID and process-start pairs must be positive and distinct. The first
invalid or numeric failure stops the remaining memory budget; no invocation is
discarded or substituted.

## Lane 3: actual-app frame and committed-result latency

Under v7 this lane is outside Issue #106's fixed order and outside its acceptance, and its four
slots are not reserved, collected or judged by that Issue. [Issue #120](https://github.com/hideyukiMORI/NENE-PIXEL/issues/120)
owns main's frame budget and the frame verdict. The run5 results collected under v6 stand unchanged
and are not re-judged: `frame-1-baseline-diagnostic` and `frame-2-candidate-diagnostic` are
`inconclusive` with no gross regression, `frame-3-candidate-decision` is `PERFORMANCE_FAIL` on a
`canvas16_tap` all-frame overrun p95 of 1.012 ms, and `frame-4-baseline-decision` was never run. The
v6 text under which those three records were collected is preserved unedited in the
[v6 historical contract](P4_INDEXED_CUTOVER_PROTOCOL_V6_HISTORICAL.md). The contract below is the
Lane 3 revision of 2026-09-23 under #120; it is #120's collection route, and every collection under
it is identified by frame experiment schema `nene-pixel-p4-indexed-frame-experiment-v5`.

Geometry admission is `initial-fit-centered-v1`. The Canvas semantics node describes its complete
surface, which may include margins around the document. Four mandatory preflight fields pin exact
integer surface bounds `[left,top][right,bottom]` for baseline/candidate and 16/256 canvases. The
experiment manifest binds these fields to each role's actual build identity. After device assets
are preserved, a no-sample UI inspection records them; historical canvas bounds are not reused.
Every warmup, sample and reset verifies the exact root bounds/rotation, stable
`editor_canvas_<edge>_<edge>` resource identity and the selected surface bounds. Setup creates each
required clean canvas through the tagged New/width/height/Create controls outside sampling.
At initial zoom 1 and midpoint center, cell scale is `min(surfaceWidth/edge,surfaceHeight/edge)` and
the document is centered in the surface. Input points are the finite centers of the designated
cells inside that projection. Fractional centers are valid; any integer input rounding must remain
strictly inside the same cell. Nonpositive/out-of-root surface bounds, mapping outside the chosen
cell, unverified initial viewport, or any later geometry drift rejects collection.

Deadline definition. Overrun is `FrameCompleted - FrameDeadline` per `framestats` row, unchanged
from the v7/v3 collector. On the named device the deadline is the intended vsync plus 10.0 ms
(`WorkloadTarget` = 10,000,000 ns in `framestats`; `FrameInterval` is about 11.11 ms at 90 Hz). The
10.0 ms is a property of the device and display mode, not a project budget: at another refresh rate
or display mode the same overrun value means something else, so overrun values are compared only
between roles measured on the same device, mode and rotation within one experiment. The run5
finding that main itself overruns this deadline (canvas16 by a median of about 0.16 ms in the commit
phase; canvas256 by up to 9 ms in the preview phase, proportional to the preview frame ordinal) is
recorded in Issue #120 and is the reason the verdict below is baseline-relative.

The current `measure-m2-frame.ps1` route is the sole actual-app collector; extend that file and
`p4-indexed-frame-analysis.ps1` in place. The identities are:

- frame schema `nene-pixel-p4-indexed-actual-app-frame-v8` (per-frame rows unchanged);
- experiment schema `nene-pixel-p4-indexed-frame-experiment-v5` (slot order, verdict inputs and
  the third family below are new).

V7/v3 and v8/v4 artifacts remain immutable historical evidence. No second script or
FrameMetrics/Macrobenchmark collector is added.

Every slot runs its workload families in this order; families 1 and 2 are the decision families,
family 3 is diagnostic only:

1. `canvas16_tap`: current clean 16 by 16 editor; DOWN at the top-left cell,
   100 ms preview dwell, UP, 350 ms commit dwell, then Undo to the exact clean
   checkpoint;
2. `canvas256_repeated_diagonal`: create a clean 256 by 256 document through the
   actual UI; DOWN at pixel (0,0), dwell 100 ms, then 16 MOVE events alternating
   (255,255) and (0,0), dwelling 20 ms after each MOVE, dwell 100 ms after the
   last MOVE, then UP at the final endpoint and dwell 350 ms. Endpoint-inclusive
   expansion is 4,081 raw positions (`1 + 16 * 255`), 256 effective changed
   pixels, below the 262,144 raw and 65,536 effective limits. Reset by one
   verified Undo, using at most three 150 ms state checks exactly as the tap
   family does. Warmups perform the identical event and dwell sequence;
3. `canvas256_repeated_diagonal_window_x2` (diagnostic slots only): the exact event, dwell,
   reset and warmup sequence of family 2 on the same clean 256 by 256 document, with the
   actual-size window shown at scale X2 at its default anchor for the whole family. Before the
   warmups the collector shows the window through the dock control `editor_actual_size_window_toggle`, then
   taps the chip `editor_actual_size_window_chip` until the window node
   `editor_actual_size_window` describes scale `x2` in its content description (the scale cycle is
   X4, X8, X16, X32, X1, X2; at most six taps, each verified through the semantics node); after the
   samples it hides the window through the same dock control and verifies that the window nodes are
   gone. The window is never dragged. The family's input points must lie outside the window's
   bounds; an input point inside the window, a window that does not describe `x2`, or a window
   still visible after the family is `INVALID` for that slot. The family exists to measure the commit-frame
   cost of the window's single `drawBitmap` against family 2 for the M5 budget (ADR 0026); it is
   never a decision input.

The long gesture resets `gfxinfo` before DOWN, retains all DOWN/MOVE preview
`framestats` rows as the preview phase, resets only after saving that complete
phase, then retains all UP-associated committed-result rows. It records
workload, operation, phase, event count and raw/valid frame counts. A mismatch
between `Total frames rendered` and raw rows, ring-buffer loss, a flagged row,
zero preview or commit rows, a frame that cannot be assigned uniquely, or an
UP completion that cannot be tied to verified committed UI is `INVALID`.

Per workload family and slot, warmups are 5. Decision sample count is 50 and diagnostic sample
count is 10. Samples are never pooled across families. The fixed four-slot sequence is:

1. decision baseline: 50 operations per decision family;
2. decision candidate: 50 per decision family;
3. diagnostic baseline: 10 per family, families 1 to 3;
4. diagnostic candidate: 10 per family, families 1 to 3.

Each slot has attempt 1 only. A slot starts only when the previous slot's run-state is complete;
a slot is complete when every one of its families was measured and its fatal/ANR/process-death
matches are zero. A `PERFORMANCE_FAIL` on slot 2 does not stop slots 3 and 4. Total measured
population is 260 operations (200 decision, 60 diagnostic); every associated preview and commit
frame remains in the raw frame population. Baseline is the `main` production commit at collection
time; candidate is that commit plus the change under judgment, named in the experiment manifest.
For the collection under Issue #120 that baseline production commit is
`2f0b617e56f7bcf3d71b5a258a48e0edead354d9` (main on 2026-09-23), and the preflight and the frame
collector pin it as the accepted baseline production commit. Each role's measurement build is an
immutable overlay commit whose production tree equals its production commit, so a build commit may
carry the Lane 3 tooling and the accepted protocol bytes while the production tree stays the one
under judgment. The preserved run5 evidence keeps Issue #106's baseline binding to `2dd4e01`.

The verdict is computed by the analyzer only. The collector records completeness and gross
regression and never a pass or fail. For the decision baseline slot, the analyzer records per family
the all-frame overrun nearest-rank p95 and p99 as the reference; if the baseline's UP
`HandleInputStart` to latest associated committed-result `FrameCompleted` nearest-rank p95 exceeds
33.33 ms or its fatal/ANR/process-death matches are not zero, the baseline is `baseline-invalid`,
the experiment stops, and main's budget is handled in its own Issue. Otherwise the slot is
`baseline-recorded`. For the decision candidate slot, for each decision family independently:

- all-frame overrun nearest-rank p95 is at most the baseline family's p95 plus 1.0 ms;
- all-frame overrun nearest-rank p99 is at most the baseline family's p99 plus 2.0 ms;
- UP `HandleInputStart` to latest associated committed-result `FrameCompleted` nearest-rank p95 is
  at most 33.33 ms; and
- fatal/ANR/process-death matches are zero.

The tolerances 1.0 ms and 2.0 ms are fixed before collection from the run5 diagnostic bootstrap
intervals (about 2.0 ms wide for `canvas16_tap` at 10 operations): a finer difference has no
detection power at 50 operations. Every decision family is measured before the verdict; the slot
is `pass` only when every decision family meets all four conditions, and a valid numeric miss on
any family is retained as `PERFORMANCE_FAIL` with no retry. The v6 absolute gates "overrun p95 at
most 0.0 ms" and "overrun p99 at most 16.67 ms" are not part of this verdict; they move to the M5
performance budget targets, where `canvas256_repeated_diagonal` is judged on absolute values only
after Issue #124.

For each diagnostic family, maximum overrun above 33.34 ms or maximum UP-to-committed result above
100.0 ms is `gross-regression` and stops the experiment. Other complete diagnostic results are
`inconclusive`, never PASS. The raw per-frame overrun column is retained in every slot for
diagnosis.

All roles are release-like, packaged-profile-installed, and explicitly compiled
`speed-profile`. Profile generation is not part of this experiment. The current
accepted profile may be used only when its source/acceptance/pair/canonical and
packaged identities pass the exact preflight for that role; otherwise the frame
experiment is blocked and profile handling is decided separately under ADR
0010. The recovery quarantine that precedes a frame slot needs a debuggable install (`run-as`), so
the quarantine installs `app_debug` and the collector then installs the role's `app_release_like`
artifact itself; run5's frame slots were collected that way (their `apk_sha256` is the role's
release-like artifact). Before every slot the collector additionally reads the installed package's
APK path from the device and verifies its SHA-256 against that artifact, so the installed variant
is evidence on the device, not only a host-side hash of the file that was installed.

The wrapper bound is derived from the slot's own operation count, not fixed:
`wrapper_bound = 300 seconds of setup + 15 seconds per operation`, where
operations are the slot's families times their five warmups plus their
samples. A decision slot is therefore 2 x (5 + 50) = 110 operations and 1,950 seconds; a
diagnostic slot is 3 x (5 + 10) = 45 operations and 975 seconds. The
decision workload has about 122 seconds of maximum intentional dwell even when
all three 150 ms Undo checks are used, but the dwell alone is not the bound: each
operation also pays repeated `gfxinfo` resets, `framestats` dumps, pulls and UI
verification, and the 256 by 256 families inject 16 MOVE events. The per-operation
allowance covers that ADB/UI cost without permitting an unlimited hang. Because
every reported per-frame metric comes from `gfxinfo`, the bound changes no
measured value; it guards a hang only. The bounded native invocation helper
accordingly admits timeouts up to 3,600 seconds. Each process runs in a
kill-on-close Job; timeout terminates its tree, records partial output and
run-state as invalid, allows at most 10 seconds for process absence and 5 seconds
for capture drain, and verifies restoration of rotation/stay-awake state.

Collections under this revision live under `build/reports/issue-120/` of the evidence worktree, one
experiment root per run, and reuse nothing from run5's frame slots except as historical reference.
The v8/v5 implementation is mandatory before collection. Preflight rejects the v8/v4 executable as
a #120 collector, while still recognizing it as the historical source of the unchanged metrics.

## Lane 4: host format, recovery, and legacy-import latency

These are descriptive JVM observations. They have no percentile or product
performance PASS. Each group runs 5 warmups then 20 samples. One
`System.nanoTime()` interval surrounds one operation; raw row emission follows
the timer and precedes cheap checking. Full equality, pixel scans, hashes, CRC
recalculation, GC and memory probes run before/after groups, never between timed
samples. Every runner has a 60-second JavaExec timeout and its wrapper has a
180-second timeout. A completed sample above 1 second is a gross-anomaly guard,
not a product threshold. One invocation per role/runner, attempt 1, no retry.

Every host CSV ends its fixed metadata with the row `group,sample,latency_nanos`
and then emits, for each group in catalog order, exactly its 20 measured rows in
ascending sample order. The `sample` field is the runner's own loop index, so it
is 0-based: the accepted values are 0 through 19, matching the
`0 until SAMPLE_COUNT` emission of every host runner. The collector, analyzer and
validator accept exactly that numbering; 1-based rows, gaps, duplicates or
out-of-order rows are `INVALID`. The sample count, warmups and group catalog are
unchanged.

### Project codec

Schema: `nene-pixel-p4-project-format-host-v1`.

Adapt `ProjectFormatV1CodecHostEvidence` to the common codec and retain one
test-only main. Baseline groups:

1. maximum v1 encode;
2. already-created maximum v1 decode.

Candidate groups:

1. maximum v1 exact-original encode from `LegacyRgbaSource`;
2. already-created maximum v1 decode to `DocumentImportSource.Legacy`;
3. minimal v2 encode;
4. already-created minimal v2 decode;
5. maximum v2 encode (256 by 256, 256 ordered slots, default 255);
6. already-created maximum v2 decode.

The common v1 maximum fixture uses 65,536 distinct deterministic RGBA values.
Common v1 groups are semantically comparable, but values remain descriptive.
V2 groups are candidate-only.

### Recovery record

Schema: `nene-pixel-p4-recovery-record-host-v1`.

Adapt `RecoveryRecordHostEvidence` and its existing private JavaExec route.
Baseline runs its five existing envelope-v1 groups: retired encode/decode,
maximum Candidate encode/decode, and conditional retirement publication through
the in-memory atomic-file seam. Candidate groups are:

1. envelope-v2 Retired encode;
2. envelope-v2 Retired decode;
3. maximum envelope-v1 Candidate decode to exact Legacy source;
4. maximum envelope-v2 Candidate encode;
5. maximum envelope-v2 Candidate decode to Current source; and
6. complete envelope-v2 Candidate publication through the same in-memory
   start/write/sync/finish/bounded-read-back/validation seam.

Cross-version mismatch, corruption, bounds, cancellation and uncertain writes
remain correctness cases, not timed samples.

### Legacy import planner/workflow

Schema: `nene-pixel-p4-legacy-import-host-v1`.

Add one internal main to the existing `:core:application` test artifact, using
the real pixel-engine planner and application completion transitions. Register
it only through an ignored reviewed init script; no permanent Gradle task or
new framework. Candidate-only groups are:

1. exact lossless conversion with 256 distinct colors including the full-no-
   transparent-black default fallback;
2. 257-distinct classification to `ConversionRequired` with no DocumentState;
3. 65,536-distinct classification retaining the exact source and count; and
4. one maximum reduction preview from 65,536 distinct RGBA values to a fixed
   256-entry palette with no exact matches, through the ADR 0022 nearest metric.

Immutable source/destination/identity/transport fixtures and the already-running editor/workflow
are untimed operation preconditions. Each sample has a fresh workflow so it cannot reuse a
classification result, reduction workspace or preview from a previous sample. Groups 1 through 3
time one real workflow load from transport invocation through classification and application
completion; group 1 includes preparation/installation of all resulting runtime owners. Group 4
first admits its exact ConversionRequired source outside the timer, then times one real
previewSource request through reduction completion/publication. This measures preview of an
already-open candidate, not application startup or a second classification. The harness retains
no fixture-only second source copy and performs no fixture pixel scans between samples.

All production classification/reduction and mutable planning-workspace allocations performed by
the measured operation are inside its interval. No private workspace, future installed owner or
precomputed reduction is injected or reused. Required operation/result owner allocations are
included; only the pre-existing current editor and group-4 admitted source are excluded. Cheap
checks are result kind, exact count, output size/palette/default, operation/source identity and
destination epoch. Full RGBA/reference comparison is outside the population. There is no baseline
operation for these new-only groups and no fabricated comparison.

## Lane 5: physical AtomicFile Candidate publication

Schema: `nene-pixel-p4-indexed-publication-device-v1`.

Actual entry to adapt: `AutosavePublicationDeviceEvidence`, its synchronized
journal/reporting rule/output reservation, and the production
`AndroidRecoveryRecordAdapter`. Use the same real `android.util.AtomicFile`
start/write/sync/finish and bounded read-back verification. A test-only baseline
overlay exercises baseline production without changing its production tree.

Fixed order is one fresh baseline invocation, then one candidate invocation.
Each has the following groups in order, 5 warmups plus 20 samples:

1. role-specific maximum 256-by-256 Candidate record;
2. role-specific minimum 1-by-1 Candidate record.

Baseline writes envelope/project v1, including the 262,209-byte maximum record.
Candidate writes envelope/project v2, including the 66,628-byte maximum record.
These are respective structural maxima, so their time delta is descriptive and
is not presented as a speedup for equivalent bytes. The minimum fixtures have
the same visible opaque color; candidate supplies the required two slots.

Metric starts immediately before Candidate encoding and ends immediately after
production read-back returns the accepted exact generation/source. Required
encode/self-validation/read-back is part of the operation. Cheap outcome and
generation checks follow timing. One untimed full decode follows each group.
Rows use the existing bounded in-memory journal; no row I/O occurs between
samples.

Each invocation has a test-owned 60-second JUnit timeout and a 5-second
post-operation anomaly guard. The surrounding native invocation is bounded at
300 seconds. Timeout freezes the journal, interrupts and drains the worker,
rejects late publication results, emits partial rows/status, and stops later
slots. A complete candidate maximum at or below 250 ms leaves ADR 0018's
1,000/5,000 ms constants unchanged. If its observed maximum exceeds 250 ms,
the valid population is retained and merge stops until the constants are
amended using exactly:

```text
AUTOSAVE_QUIET_MS = ceil(4 * max_ms / 500) * 500
AUTOSAVE_LATENCY_CAP_MS = ceil(20 * max_ms / 500) * 500
```

The 250 ms boundary is a constant-decision boundary, not PASS/FAIL. A sample
above 5 seconds makes the lane invalid and cannot be used for re-derivation.

## Lane 6: correctness, UI, lifecycle, and bounded ownership

This lane has no warmup, sample population, percentile, or performance verdict.
Every selected contract must pass on the candidate before its dependent
performance lane:

- indexed domain/snapshot/palette/document factories, defensive ownership,
  slot 0/255, count 2/256, same-RGBA distinct slots and hidden RGB;
- drawing target capture, same-slot no-op, same-RGBA/different-slot mutation,
  Eraser default capture, stale admission and gesture cancellation;
- palette replace/recolor/default/delete/reorder, many-to-one exact inverse,
  palette-only ChangeSet, 64/524,288/8 MiB accounting, eviction and rejection
  atomicity;
- renderer/PNG palette lookup, palette-only cache invalidation and maximum PNG
  exact pixels;
- v1/v2 project goldens, every structural boundary/rejection/allocation order,
  exact v1 original re-encoding and max-plus-one probes;
- recovery v1/v2 Retired/Candidate, strict nested pairing, CRC/bounds,
  generation, pre/post-finish failure, cancellation and read-back mismatch;
- <=256 exact import, 257/65,536 ConversionRequired, one-color/default rules,
  source preview, destination A/B/A, copy failure/cancel/success proof,
  recovery-only retirement bypasses and last-safe installation;
- Save As/autosave interaction, source/history/operation staleness, pending
  autosave drain, background/ON_STOP, configuration recreation and process
  recreation; and
- actual ContentResolver project v2 save/load, v1 load/original copy, PNG, and
  recovery record device I/O.

Run each Android functional class or method as one explicit selector; comma-list
selection is prohibited because prior tooling executed only the first selector.
Each invocation has a 300-second outer limit, fail-if-present archive, and exact
app/test APK identity. Functional failure is preserved and investigated; it is
not converted into a timing result or erased by a broad rerun.

Narrow host tests/static/consumer compile and documentation/architecture checks
follow the changed slice under QLT-011. The final coherent candidate requires
the protected canonical CI `quality` result. No routine clean, cache bypass,
forced cold build, duplicate local full run, or automatic profile regeneration
is part of this protocol.

## Exact stop and decision order

1. Freeze protocol and unimplemented harness contracts in Issue/docs.
2. Implement production and the listed collector changes through ordinary
   narrow correctness work. QLT-015 explicitly allows this development.
3. Complete candidate correctness and collector self-contracts.
4. Fill all preflight identity fields and validate Issue/protocol/harness
   agreement without collecting a sample.
5. Collect host codec/recovery/import roles in their fixed orders.
6. Collect command baseline then candidate.
7. Collect the four memory families, five processes each.
8. Collect physical publication baseline then candidate and apply the ADR 0018
   constant decision.
9. Stop at the first INVALID or PERFORMANCE_FAIL. Preserve it. Do not spend a
   later dependent slot or substitute a result. A pre-verdict harness-contract
   `INVALID` is recovered only through the bounded recovery rule in the
   admission section, by restarting this whole order from step 5 under a new
   experiment id; it never repairs, reuses or re-judges the preserved slot.

This order covers the twenty-nine slots of the four collected lanes: five host, two command, twenty
memory and two publication. The four Lane 3 frame slots are not part of it and belong to Issue #120.
Experiment `p4-indexed-v6-20260917-run5` completed every one of those twenty-nine slots under v6 and
they are admitted here without re-collection, so no step of this order is executed again under the
v7 identity.

Host descriptive lanes become acceptable evidence only when complete and
valid; they never yield PERFORMANCE_PASS. Issue acceptance requires all
candidate correctness, the command and history absolute gates, the publication
constant decision, the valid descriptive host populations, and final CI. The
frame gate is not part of Issue #106 acceptance under this revision.

## Governing decisions

- [ADR 0025](../adr/0025-indexed-project-compatibility.md) fixes the atomic compatibility cutover.
- [ADR 0022](../adr/0022-indexed-palette-and-migration.md) fixes indexed meaning and logical budgets.
- [Quality Gates](../QUALITY_GATES.md) fix verification scope, frequency and evidence preservation.

Acceptance of this contract is not itself a performance verdict. The verdicts it reports are the
ones experiment `p4-indexed-v6-20260917-run5` collected under revision v6 and this revision admits
unchanged; no result is claimed that a preserved record does not state, and the frame lane remains
open under Issue #120. Waivers: none.

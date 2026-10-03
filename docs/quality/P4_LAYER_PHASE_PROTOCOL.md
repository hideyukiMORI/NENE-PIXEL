# P4 Layer Phase Protocol

Status: prospective preparation for Issue #145. The decisions below govern preparation; the
complete protocol, executable agreement, artifacts, profile and finite collection schedule are
not yet admitted. No sample collection is authorized by this document's current state.

Rules: ADR 0030/0031/0035; QLT-011 through QLT-019. Active waivers: none.

## Scope and historical boundary

The phase owns the registered single-layer, fully covered 16-layer and alpha-128 underlay workloads,
retained heap/PSS, maximum autosave/user-save time and logical remap history bytes. It uses the
existing P4 collectors and analyzers. The v7 indexed protocol and all earlier FAIL/invalid evidence
remain historical; a new phase identity and executable agreement are required before collection.

The accepted frame comparators remain those in
[the prospective comparison decision](P4_INDEXED_CUTOVER_PROTOCOL.md#prospective-layer-phase-comparison-decisions-issue-145).
The fixed M5 gates remain UP-to-committed p95 at most 16.67 ms, all-frame overrun p95 within
baseline +1.0 ms and p99 within +2.0 ms, zero fatal/ANR/process-death and no defined gross regression.

## One maximum project fixture

One synthetic project asset is kept in `docs/quality/fixtures/p4-layer-phase/`. It is measurement
test data, not a user asset or generated build output. A single Kotlin definition in the persistence
host-test source constructs it through canonical domain factories and `ProjectFormatCodec.encode`.
The checked asset is compared byte-for-byte with that definition in a focused host contract test.
The exporter creates a new explicitly named file; it never overwrites an earlier artifact.

The document has these exact facts:

- ID `14500000000000000000000000000000`, revision 0, size 256 by 256, default palette index 0.
- 256 palette entries: slot 0 is opaque black; slots 1 through 255 are grayscale `(i,i,i,128)`.
- 16 visible layers, ordered IDs 1 through 16, all pixels covered, each layer filled with its own
  palette index equal to its ID. Every name is 32 copies of U+1F600, exactly 128 UTF-8 bytes.
- Project v3 length is exactly 1,182,862 bytes; its Candidate recovery envelope is 1,182,885 bytes.
  The checked asset's SHA-256 is
  `165f62d180533849ce1a4ef1625cd3971e445f2dca60ef7b9b46fedaafa0b3ec`.
  The exporter and both merged AndroidTest asset entries match that hash and length. This is not
  yet an APK-entry or successful device-load proof; those remain artifact admission checks.
- Canonical load selects top layer 16. Drawing with palette index 0 changes every one of the
  diagonal's 256 pixels; a single-cell DOWN changes one pixel. Revision 0 admits these commands.

This is a drawing-capable maximum fixture. The pre-existing format-boundary fixture uses
`Long.MAX_VALUE` revision and varying diagonal indices, so it is not substituted for this workload.
The partial-alpha layers exercise actual compositing despite every coverage bit being set.

One measurement init script borrows the existing persistence unit-test runtime classpath for the
host exporter and maps this same checked asset directory into the two existing `androidTest` APKs.
It is applied explicitly to the measurement builds. It adds no plugin, module, dependency, public
API or production asset, and does not edit historical roles' production build files. No Kotlin test
package is imported across module boundaries. Each APK's fixture entry must match the one pinned
asset hash before use. Normal host verification reads the checked asset directly.

Frame setup stages those exact bytes in the test DocumentsProvider's own fresh storage, verifies
their hash, and loads through the app's actual SAF picker. Baseline 169b592 has no layer panel;
do not add production UI to that comparator or claim that its nonexistent layer rows were observed.
Its fixture proof combines pinned bytes, production v3 decoding contracts, the canonical successful
load, canvas size, clean state and top-layer selection policy. Memory and publication lanes consume
the same decoded asset outside their measured intervals, rather than constructing another fixture.

## First-preview assessment decision

The layered group has two workloads on the same pinned maximum document: `canvas256_layers16_tap`
and `canvas256_layers16_repeated_diagonal`. The tap sends DOWN, waits the existing 100 ms preview
dwell, captures the preview interval, then sends UP and captures the committed interval. There is
no MOVE in its preview interval. The repeated diagonal retains the existing DOWN plus 16 alternating
MOVE events, 20 ms MOVE dwell and 100 ms post-MOVE dwell; its long preview is not split by an extra
host capture. Both roles use the identical event and capture sequence, outside all fixture setup.

The tap isolates the first preview's work on every fresh gesture. It uses warm rendering resources;
it is not process startup or a cold allocation metric. Preparation must prove the correct visible
preview on the pinned document before timing. Timed samples retain all valid app frames and refuse
empty, flagged, unassociated or non-monotonic intervals under the existing frame validity contract.
The earliest valid DOWN-only app frame is identified by its own input-start/completion timestamps
and frame identity. Report its service-to-completion latency and overrun distribution, with the
explicit limit that `gfxinfo` does not prove physical pixel presentation or injection-to-display time.
Do not pair independent timestamp minima from pooled long-stroke frames and call that first preview.

The tap and repeated diagonal each keep M5 UP-to-committed and all-frame relative gates. The first
DOWN-only frame is included in the tap's all-frame population and is additionally reported on its
own; no new numeric physical-presentation gate is invented. The existing single-layer families and
underlay diagonal remain registered. This decision fixes assessment semantics, but complete slot
populations, budgets, schema and executable association checks still precede phase admission.

## Fixed frame population and comparison roles

The prospective protocol identity is `nene-pixel-p4-layer-phase-verification-v1`. Frame records use
`nene-pixel-p4-indexed-actual-app-frame-v9`, experiment records use
`nene-pixel-p4-indexed-frame-experiment-v6`, and the verdict identity is
`layer-phase-2026-10-03-relative-m5`. These identities do not reclassify historical v7/v8/v5 data.
The existing frame collector, analyzer, preflight and slot wrapper remain the single route.

One phase experiment has three comparison groups in this exact order. `baseline` and `candidate`
remain comparison roles; an artifact role independently selects the immutable build. The candidate
artifact is the same in all three groups, rather than a rebuilt candidate per comparison.

| Group | Baseline artifact role / production commit | Decision families, in order | Diagnostic families, in order |
| --- | --- | --- | --- |
| `single` | `baseline_single` / `8120c06fae1a372b23d2a7af4f50aa2b9cdfeff9` | `canvas16_tap`, `canvas256_repeated_diagonal` | the same two, then `canvas256_repeated_diagonal_window_x2` |
| `layers16` | `baseline_layers16` / `169b59287ca60e77e07ac91690450dd1a44b9ba4` | `canvas256_layers16_tap`, `canvas256_layers16_repeated_diagonal` | the same two |
| `underlay` | `baseline_underlay` / `f92b1006be5f7145a32258446474f8640b14b60b` | `canvas256_underlay_repeated_diagonal` | the same one |

Within each group the order is decision baseline, decision candidate, diagnostic baseline,
diagnostic candidate. Each family has five warmups, then 50 decision or 10 diagnostic operations.
No samples are pooled between families, groups, or decision/diagnostic populations. This gives:

| Group | Measured operations | Warmups | Total operations | Decision slot bound | Diagnostic slot bound |
| --- | ---: | ---: | ---: | ---: | ---: |
| `single` | 260 | 50 | 310 | 1,950 s | 975 s |
| `layers16` | 240 | 40 | 280 | 1,950 s | 750 s |
| `underlay` | 120 | 20 | 140 | 1,125 s | 525 s |
| Total | 620 | 110 | 730 | — | — |

There are exactly 12 frame slots, numbered 1 through 12, each with attempt 1 only. Their IDs are
`frame-<sequence>-<group>-<comparison-role>-<runner>`. Each candidate decision slot references its
own group's preceding decision-baseline slot. Its baseline reference must bind the same experiment,
group, family catalog, device/display conditions and the group's fixed baseline production commit;
an analysis file for another group cannot substitute even if its numbers or artifact hash match.
The slot also records `artifact_role`, resolved to that group's baseline role or the shared
`candidate` role. Artifact selection must never change the meaning of comparison role.

The existing bound `300 + 15 * operations` seconds applies to each frame collector invocation.
The sum of collector bounds is 14,550 seconds; this is a hang bound, not an estimated duration.
The existing 90-second cleanup reserve and 120-second analysis bound apply to each slot separately.
The existing 3,600-second native invocation cap is unchanged. A missing or extra family, changed
population/order/bound, unrecognized group/role, or missing predecessor refuses admission.

Every complete decision population is retained before its numeric verdict. A candidate numeric
failure does not cancel that group's predeclared diagnostics. A gross diagnostic regression
(maximum overrun above 33.34 ms or maximum UP-to-committed above 100.0 ms), invalid association,
fatal error, ANR or process death stops subsequent slots. No automatic slot retry or new identity
to retry the same candidate is permitted. Any corrective collection needs a recorded new plan under
QLT-015/019; the failed and unexecuted portions of this experiment remain explicit.

The candidate decision families each require UP-to-committed nearest-rank p95 at most 16.67 ms,
all-frame overrun p95 at most the matched baseline plus 1.0 ms, and p99 at most baseline plus
2.0 ms. The diagnostic window family remains descriptive and has the same gross-regression stop.
The historical baseline admission guard remains p95 at most 33.33 ms and zero fatal/ANR/process-death
matches. A miss is retained as `baseline-invalid` with its numeric reason and stops later slots;
it cannot be retried under an invalid-harness recovery allowance. A recorded baseline is a
comparison reference, not an M5 acceptance of that old build. The candidate's stricter 16.67 ms
requirement is mandatory even when its matched baseline is slower. No threshold is relaxed.

## Executable DOWN-only association contract

The first-preview association is defined only for `canvas256_layers16_tap`. The analyzer's pure
association function takes the complete preview rows and the caller's expected capture identity
(workload, operation, ordinal, sample index, build/production commits, variant and raw row count).
It returns the first row's own frame ID, row index, input start, completion and deadline, together
with service-to-completion and overrun milliseconds. It never joins extrema from different rows.

Admission requires a nonempty exact row count; `preview`, event count 1, flags 0 and every expected
identity field on every row; consecutive row indices starting at 1; unique positive FrameTimeline
IDs; positive integer timestamps; and, within each row, intended vsync <= frame start <= input start
< completion and deadline > intended vsync. In recorded row order intended vsync, frame start,
input start and completion must each increase strictly. Ties, reordered or foreign rows are
refused rather than sorted, dropped or repaired. A negative overrun is valid. Derived per-row
overrun and input-service values must match those row's timestamps to the published six-decimal
precision. Integer fields must not be accepted through truncating or rounding conversion.

The first row's own association is descriptive; tap all-frame relative and committed-result gates
continue to use the full population. The helper does not prove an input was visibly presented.
Before collection, the caller must independently prove the DOWN-only sequence, setup quiescence,
preview/capture completeness and visible correctness. Full collector/analyzer integration must
reconcile the sample association with retained raw rows, and reject first-preview fields on a
diagonal or another workload. The pure helper alone cannot admit a capture or a phase.

## Frame preparation implementation slice

Issue #145 next implements the device-free group/slot catalog and its host contract. It extends
the existing preflight catalog with an explicit protocol selection; the historical default remains
unchanged. The new catalog supplies the fixed identities, comparison/artifact roles, predecessor
references and finite populations above. It does not admit a phase manifest while collector,
analyzer, preservation integration and remaining decisions are incomplete.

Verification: a focused no-device catalog validator checks exact role/commit/family bindings,
sequence, sample/warmup totals, derived bounds, predecessor isolation and rejection of unknown
protocols. The historical four-slot catalog is a direct consumer regression check, without
re-executing old device or analyzer evidence. Documentation validation checks this contract's links
and rule references. No product behavior test, full local suite or device action follows from this
slice. Earlier successful preservation and fixture checks are reused on unchanged inputs.

The next independent slice adds the pure DOWN-only association to the existing frame analyzer,
without changing historical analysis. Its focused no-device validator covers a multi-row capture
whose first row is not the fastest, own-row timestamp pairing, exact metric arithmetic, negative
overrun, missing/lost/flagged/foreign/reordered/tied rows, duplicate IDs, malformed integer fields
and wrong derived metrics. No device samples or current successful analyzer suite are rerun merely
to add this new schema's preparation contract.

## Frame execution contract and evidence binding

The existing preflight owns one `Get-P4FrameExecutionContract` resolver, selected by an explicit
protocol ID and canonical slot ID. It derives role, artifact role, group, global sequence and
group-local position, workload/event catalogs, directory identity, baseline reference and numeric
rules from the registered catalogs. The collector and analyzer consume that same definition.
Historical v7 resolves the original four slots, v8/v5 records and original workload/event catalogs;
the new phase resolves the twelve slots and v9/v6 records. Unknown or mixed identities are errors.
The legacy collector directory spelling is preserved. A phase frame directory is
`slot-<two-digit-global-sequence>-<group>-<runner>-<comparison-role>-attempt-1` below the single
frame experiment root; group changes never create a new experiment or an editable role projection.

The v6 `experiment.json` is a canonical projection of that same preflight: exactly `schema`,
`protocol_id`, `experiment_id`, `preflight_sha256`, the twelve-entry `comparison_order` and
`slot_catalog`, `slot_budget` 12, `maximum_attempts_per_slot` 1 and `replacement_rule` `none`.
The shared preflight helper constructs it; the writer and analyzer compare its complete contents.
Phase metadata and run state also record its actual `experiment_sha256`. It contains no independently
editable artifact copies. A phase frame-slot record uses `nene-pixel-p4-frame-slot-v2`, the checked
phase/slot identities and experiment hash, with the existing exact file path/size/hash inventory.

Phase analyzer callers provide a `PhaseContext` with exactly `protocol_id`, `slot_id`,
`preflight_sha256`, `production_commit` and `baseline_reference`. The protocol/slot resolve the
group and artifact role; the existing explicit role/runner/sequence/build/APK/experiment/bounds
arguments must agree. The preflight hash identifies the immutable complete phase manifest,
including the pinned device/display conditions, fixture and four artifact records. It is supplied
by the wrapper, never learned from a capture being checked. This context is an internal view,
not another manifest and not evidence that the full phase is admitted.

For a decision candidate, `baseline_reference` has exactly `build_commit`, `production_commit`,
`apk_sha256`, `analysis_sha256` and `capture_seal_sha256`. Other slots require a null reference.
The baseline analysis must have the expected file hash, a matching capture-seal hash, the same
preflight hash/experiment/group and the group's canonical decision-baseline slot/artifact role,
with the expected build/production/APK, v9/v6/verdict identity, full family population and valid
`baseline-recorded` result. The baseline production commit is also checked against the catalog.
The outer wrapper remains responsible for proving the completed prior chain and sealed files;
the inner analyzer must not accept a different path or group merely because its metrics match.
The two existing evidence roots remain distinct: wrapper `analysis.json`, `capture-seal.json` and
`completed.json` live under `<output_directory>/<canonical-slot-id>/`; the collector's raw files
live under `<frame_experiment.directory>/<frame-directory-name>/`. The seal binds raw files through
its existing `external/frame-slot/` inventory. The phase analyzer reads the canonical wrapper
baseline directory, not a second analysis or seal copied into the raw collector directory.

Every phase metadata record, run-state record, raw frame row and operation sample carries matching
`protocol_id`, `preflight_sha256`, `group_id`, `slot_id`, `artifact_role` and `experiment_id`.
Metadata, raw frames and samples also bind the expected production commit as well as build/APK
identity at their declared boundary. The analyzer validates raw/sample integer identities without
rounding, each phase's exact event count and row indices, timestamp order and each row's own
service/overrun arithmetic. Preview precedes commit; malformed counts or a foreign row are not
repaired. These checks apply to every v9 workload, not only the DOWN-only tap.
The writer rejects an unparseable nonblank PROFILEDATA row or a missing closing marker, and the
predecessor state requires native JSON integer/boolean types and its exact declared slot identity.
The two other emitted raw durations (`frame_duration_cpu_ms` and `app_frame_total_ms`) are also
reconciled to completion minus frame start/intended vsync at F6 precision. Operation-summary
timestamps and both UP-to-committed/DOWN-to-committed durations reconcile to the retained phases.
Gfxinfo janky/deadline counters remain summary observations: require exact nonnegative counts
bounded by the operation total and reconcile published aggregates, rather than infer them from
unrelated per-frame fields.

Only `canvas256_layers16_tap` samples populate the seven first-preview fields. Other v9 families
must leave those CSV columns empty (or omit them when no tap shares that CSV); nonempty orphan
fields are invalid. The analyzer calls the same pure first-preview association used by the writer,
compares every sample field to raw rows and reports first-preview service/overrun distributions
descriptively. The complete tap frame population still supplies the existing relative gates.
The tap family result adds `first_preview_operation_count`, `first_preview_service_p95_ms`,
`first_preview_overrun_p95_ms` and `first_preview_overrun_p99_ms`, from exactly that family's
predeclared 50 or 10 operations. Other families do not publish a first-preview population.

This connection work does not open collection admission. The phase entry points continue to refuse
live work until fixture setup, complete manifest/artifact/device bindings, all lane schemas and
preservation/restoration integration are implemented. Host checks cover the common execution
contract, legacy compatibility at the changed boundary, new-schema identity/timestamp/association
refusals and the 16.67 ms/+1/+2 ms boundary. No device run or full local build follows from this edit.

## Live-editor retained-memory decision

This is a separate, intrusive lane using the existing app instrumentation and two-pass post-GC
sampler, never a probe between frame-latency operations. The two artifact roles are
`baseline_layers16` and `candidate`, five fresh target processes each, in that order. Every
positive PID/process-start pair must be unique. Use `debug` artifacts compiled `verify` in both
roles; this lane does not report release-like frame latency. The schema is
`nene-pixel-p4-layer-editor-retention-v1`.

The real `MainActivity`, its existing ViewModel and Compose canvas stay alive throughout one run.
The test retrieves that model; it creates no second runtime. The same pinned maximum asset is
loaded through the actual SAF flow. Fixture/provider staging is complete before C0. The initial
workspace is a clean empty 256 by 256 document with the same initial-fit viewport, layout,
palette controls, hidden actual-size window and absent underlay in both roles.

| Checkpoint | Exact retained state |
| --- | --- |
| C0 `empty_idle` | Real empty editor rendered, initialized recovery and idle persistence, no preview/history. |
| C1 `maximum_loaded_idle` | Successful maximum-asset load, revision 0, clean empty history, top layer 16, paint slot 0 and rendered canvas. |
| C2 `long_preview_held` | DOWN plus the same 16 alternating diagonal MOVE events; pointer still held, preview rendered, document remains revision 0. |
| C3 `committed_idle` | One UP commits that gesture, revision 1, one undoable entry, dirty state, no preview, completed autosave and rendered canvas. |
| C4 `post_cycles_idle` | Ten Undo/Redo pairs through the normal controls, ending in C3's exact document/history position and availability; no preview and completed autosave. |

Each checkpoint uses exactly the existing two GC/finalization passes on the instrumentation thread,
then records primitive heap/PSS values while retaining the real model. No screenshots, copied pixel
arrays, independently decoded fixture, old document/render/workspace snapshots or prior preview
objects may survive in test fields across a checkpoint. The normal model/composition and test
runner remain present. Report target-process heap/PSS including that fixed instrumentation cost;
the external DocumentsProvider and system picker processes are outside this population.

At C0/C1 require operation idle and no pending/publishing autosave. At C3/C4 require operation idle,
no pending/publishing autosave, and the current capture is already published. A return to that
same published position requires no new generation. A storage/lineage failure, undecided recovery
offer or failure to reach the state within 15 seconds is invalid, not an excuse to omit the run.
Composition must be idle and the requested canvas/preview state must have rendered. Each whole
instrumentation invocation is bounded at 300 seconds; no extra GC or process substitution is allowed.

For C1, C2, C3 and C4 independently, preserve the established memory limits: every Java heap at
most 50% of that process's `Runtime.maxMemory`; every PSS at most that run's C0 PSS plus 60% of
`ActivityManager.memoryClass`; and the five-run median PSS delta from C0 at most 50% of memory class.
Each C4-minus-C3 Java-heap growth is at most `max(1 MiB, 1% of Runtime.maxMemory)`. Cross-artifact
differences are descriptive, not a new tolerance. The first invalid or numeric failure stops the
remaining memory/phase slots with all prior results retained. No five-run median is reported from
a shorter population.

The source-derived owner inventory accompanies observations. Maximum document primitive pixels are
1,179,648 bytes. A held top-layer preview additionally retains 15 copied non-target surfaces,
1,105,920 bytes of indices/coverage, and a 1,024-byte palette. The committed ARGB array, two preview
ARGB arrays and their Android bitmaps are separate owners; preview arrays/bitmap can remain cached
after release while preview source references are dropped. These logical counts exclude object,
renderer and native overhead. The earlier +512 KiB estimate is not a PSS tolerance or an assertion
that only that many extra bytes remain reachable.

## Maximum publication and physical SAF-save decisions

These descriptive storage lanes use the shared candidate production tree and real physical storage,
separately from frame timing and retained-memory GC. They follow the ten memory runs, in this order:

1. One fresh persistence-instrumentation invocation of the existing AtomicFile publication runner:
   maximum 16-layer fixture, then the canonical minimum v3 fixture, each five warmups and 20 samples.
   Schema `nene-pixel-p4-layer-publication-device-v1`; maximum Candidate 1,182,885 bytes, minimum
   Candidate 85 bytes. Decode the pinned maximum asset outside timing through `ProjectFormatCodec`.
   Time from immediately before Candidate encode through the production writer's accepted exact
   read-back result. Keep the existing 54-row journal (25 operation rows and min/max per group),
   60-second worker bound, 300-second native bound and 5-second completed-operation anomaly guard.
   No row/file I/O or full fixture scan is added between timed operations.
2. One fresh app-instrumentation invocation for 25 distinct preselected fresh SAF destinations:
   five warmups, then 20 samples on the same 1,182,862-byte maximum project. Schema
   `nene-pixel-p4-layer-saf-save-device-v1`. Use the existing test DocumentsProvider authority
   `io.github.hideyukimori.nenepixel.test.acceptance.documents`, with unique allowed
   `i89-145-...` names; pin the provider APK and grants to the actual target app identity. Prepare
   all fresh empty destinations and their real framework grants through a test-only activity-result
   registration on the existing app activity before timing. A URI string alone is not admission.

SAF timing calls the public `AndroidProjectStorageAdapter.save` with a test picker returning exactly
one previously unused granted destination. The interval includes encode, self-validation, that
fixed-picker return, real `ContentResolver` open/write/close, bounded read-back and exact byte
verification. It excludes document creation, user/DocsUI wait and preselection. No wrapped in-process
provider, direct private-file write or production observation API substitutes. This is verified save
to a preselected local SAF destination; it is not the full interactive Save As duration, a guarantee
for cloud/Downloads providers, or an fsync guarantee. Each grant/freshness/byte-count/outcome is bound
to its sample. Batch-boundary validation checks exact output bytes against the pinned fixture.

SAF setup has a fixed 300-second bound; the 25-operation worker has the same 60-second bound and
5-second completed-operation anomaly guard as publication. One native invocation is bounded at
420 seconds, covering those intervals and a 60-second reporting/drain reserve. Its bounded journal
contains 27 rows: 25 operations and descriptive min/max, with no per-row disk I/O during timing.
Failed/partial outputs are preserved under the existing slot evidence/quarantine policy. Neither
lane retries a slot, deletes successful/failed evidence, overwrites an old URI or clears an app.

For maximum AtomicFile publication, apply ADR 0018's unchanged 250 ms decision boundary to this
actual v3 maximum. At or below it keep 1,000/5,000 ms; above it retain the valid observations and
re-derive the two constants with the ADR's existing formulas before merge. This is a constant
decision, not a performance PASS label. SAF min/max is descriptive, with exact successful transport
and the predeclared anomaly guard required; the autosave 250 ms boundary is not a SAF acceptance
threshold. No speedup is claimed from different byte counts or transport paths.

These decisions do not yet authorize execution: both schemas, exact owner/quiescence contracts,
real grants and artifact entries must be implemented and checked at their narrow boundaries,
then bound into the one phase manifest and its preservation/restoration lifecycle. Storage and
memory keep the existing `Invoke-P4InstrumentationLane` executor, rather than another collector.

## Pending admission decisions

Collector/analyzer integration of first-preview association, the exact underlay image/placement
proof, executable memory/storage schema agreement and real SAF grant proof,
artifact/profile bindings, complete phase stop/budget rules and preservation-v2 integration must be
completed before collection. A verified read-only snapshot is not an isolated measurement session.

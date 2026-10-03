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

## Fixed underlay material and load boundary

The underlay group uses one checked synthetic PNG in the same test-asset directory:
`underlay-grid.png`, 1024 by 1024, 8-bit opaque RGB, non-interlaced. Its only PNG chunk kinds are
IHDR, IDAT and IEND; no orientation, density, gamma or embedded colour-profile metadata is allowed.
For source coordinate `(x,y)`, let `u=floor(x/4)` and `v=floor(y/4)`. Its exact sRGB RGBA pixel is
`(64 + u % 192, 64 + v % 192, 64 + (u+v) % 192, 255)`. Every 4-by-4 block is constant, and no
source pixel is black. Encoded bytes and the decoded row-major big-endian RGBA8888 byte stream
receive separate pinned SHA-256 identities; the artifact check must prove both where declared.

- Encoded PNG: 184,323 bytes, SHA-256
  `05efb3fc8edf43f45dc5a8b7cae3be148680c5694b47c47d1cf4eb7cbe26c6fb`.
- Decoded row-major RGBA8888: 1,048,576 pixels / 4,194,304 bytes, SHA-256
  `f107eb10700c55df2cb3a4dd1b2723b14bc773dcd59866f4a65a5b94d583aa78`.

The host test fixture is the sole generator, using the JDK's ImageIO PNG writer only in test code.
Its explicit exporter creates a fresh file. The checked PNG is test input, not a production asset.
Host verification checks its hash, metadata/chunk contract, every decoded pixel and the production
placement factory. An opt-in Android fixture test checks the same packaged bytes through the actual
`AndroidBitmapReferenceImageDecoder` and compares all decoded pixels by the separate RGBA hash.
Select `LayerPhaseUnderlayDecoderDeviceTest` with the exact instrumentation argument
`p4LayerFixtureCheck=true` only after device admission; without the argument it is skipped.
That functional check has no timing verdict. A compiled test or merged asset does not establish an
APK-entry, Android decode or successful picker-load proof.

Both comparison roles create a fresh empty 256-by-256 single-layer document through the existing
New-document UI and select opaque black at palette index 0. They pick the same verified PNG through
the existing test DocumentsProvider and real SAF picker. A fresh allowed `i89-145-...png` provider
name binds the phase/fixture identity; the provider's no-overwrite rule and framework URI grants
remain in force. No URI string, direct broker completion or provider permission bypass substitutes
for the actual app pick. Exact provider APK/manifest/source, asset entry, staged bytes and grant/load
outcome are admission evidence, outside timed samples.

Successful pick uses the existing `ReferenceUnderlay.placed` path: decoded 1024-by-1024 on the
256-by-256 document yields exactly left 0, top 0, scale 0.25, opacity alpha 128, Shown and Resting.
The host/source comparison proves the factory contract in both roles; runtime preparation must
also prove a fresh work, successful pick, correct pattern, shown control, closed panel and absent
adjust bar. A rounded 50-percent label is supplementary evidence, never proof of integer alpha 128.
No opacity slider, fit command or adjustment gesture is sent. The pick result replaces any restored
underlay through the production operation; a stale remembered image cannot stand in for the PNG.
Before timing, no pending recall/publication or setup frame may contaminate capture. The later
admission work must establish that quiescence and visible correctness on the actual device.

The existing repeated-diagonal and one-Undo reset sequence remains unchanged. PNG decode/hash,
full-pixel verification, fixture selection and setup observations stay outside every timed gesture.
No document scan, allocation/copy or draw step is added to application production code by this fixture.

### Shared real-SAF fixture preparation

The app instrumentation registers a temporary launcher on the real `MainActivity`'s existing
ActivityResultRegistry, using the production create-document contract. The existing DocumentsUI
helper selects the existing test provider and a unique `i89-145-...` name. The result must be that
authority's exact named document, with read and write grants for the target app's actual PID/UID,
the expected display name and zero bytes. Provider package/class/UID are checked against the
installed app-test package; its source, manifest and APK belong to the admitted artifact inventory.

Pinned asset bytes are staged through that granted ContentResolver destination and read back in
full with exact size/hash. The temporary launcher is unregistered in all outcomes. Setup retains
only the name/URI and primitive grant facts after byte verification returns; the production app's
normal picker then opens that named document. No provider-private file access, shell grant or
broker completion is used. Memory setup finishes before C0. The same helper creates the 25 fresh
empty granted SAF-save destinations before timing, without writing fixture bytes into them.

The public save adapter requests cleanup of a failed fresh output. For `i89-145-...` documents only,
the test provider implements that logical deletion by moving the existing file without overwrite
into its private `files/p4-layer-provider-quarantine/` directory. It disappears from the provider's
document listing while its bytes remain available to the phase evidence capture. Legacy fixture
deletion remains unchanged. A quarantine collision or failed move reports failed cleanup and leaves
the source in place; it must not delete or overwrite either file. These provider files, successful
documents and partial failures are retained with the slot. The production adapter is unmodified.

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
objects may survive in test fields across a checkpoint. The opaque autosave position token contains
only its runtime-generation and history-position values and may be retained to compare C3 with C4.
The normal model/composition and test
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

The app runner requires exact phase/experiment/slot/artifact/build/production, preflight SHA-256,
preservation SHA-256/session and both installed app/test APK hashes. It verifies those installed
files and the existing native preservation guard before launching the editor. These local checks
supplement the host's complete manifest and preservation-v2 verification; a directory or supplied
hash alone is never collection admission. Memory slots are `memory-layers16-baseline-1` through
`-5`, then `memory-layers16-candidate-1` through `-5`; families are respectively
`baseline-layer-editor-retention` and `candidate-layer-editor-retention`.

One status-code 3 bundle carries the existing seven process-identity fields plus `p4LayerIdentity`,
the exact eleven-field admission projection. Each completed checkpoint immediately emits one
status-code 5 `p4LayerMemoryCheckpoint` record containing its run/slot/name/index and all eight
primitive values from the unchanged post-GC sampler. Exactly five ordered checkpoint bundles are
required for a valid run. One final status-code 4 `p4MemoryReport` binds those observations to the
same identity, fixture URI/hash, actual grant/provider UIDs and fixed workload facts. Its five
heap/PSS pairs must equal the raw checkpoint records. Partial checkpoint records survive failure;
they cannot be padded, omitted or used as a complete run. Across all ten runs, PID/start pairs are
unique; within each role, limits and five-run medians use only its exact preceding runs, with
the same positive runtime maximum and Android memory class. A capacity change within an immutable
artifact role is invalid evidence. The analyzer receives the host-verified sealed predecessor chain;
it independently rechecks its identities, complete checkpoint populations and numeric verdicts.

The source-derived owner inventory accompanies observations. Maximum document primitive pixels are
1,179,648 bytes. A held top-layer preview additionally retains 15 copied non-target surfaces,
1,105,920 bytes of indices/coverage, and a 1,024-byte palette. The committed ARGB array, two preview
ARGB arrays and their Android bitmaps are separate owners; preview arrays/bitmap can remain cached
after release while preview source references are dropped. These logical counts exclude object,
renderer and native overhead. The earlier +512 KiB estimate is not a PSS tolerance or an assertion
that only that many extra bytes remain reachable.

### Memory preparation implementation status

The opt-in app instrumentation now implements the real-editor sequence, shared SAF staging,
five immediate checkpoint records and final report. The existing memory analyzer selects the new
contract only when an explicit phase context is supplied; its four historical families are unchanged.
The no-device validators cover numerical boundaries, malformed or foreign evidence, predecessor
order/freshness, and Android report-field agreement. Both candidate and fixed layer baseline compile
the same test overlay. See the [implementation report](../reports/2026-10-03-layer-editor-memory.md).

These checks do not establish device grant/load/render behavior or admit a phase. The manifest,
outer instrumentation wrapper, complete artifact inventories and preservation chain still need the
phase integration below. No measurement has run from this preparation slice.

## Maximum publication and physical SAF-save decisions

These descriptive storage lanes use the shared candidate production tree and real physical storage,
separately from frame timing and retained-memory GC. They follow the ten memory runs, in this order:

The exact slot IDs are `publication-layers16-candidate` (phase sequence 23, native bound 300 seconds)
and `saf-save-layers16-candidate` (sequence 24, native bound 420 seconds). Both use artifact role
`candidate`, attempt 1 only, and require the complete preceding successful memory chain.

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

### Publication artifact and output binding

The existing AtomicFile runner selects the layer phase only with `p4LayerCollect=publication-v1`
alongside its existing explicit publication collect/candidate arguments. The maximum is decoded
from the checked asset before the timed loop; the existing minimum factory is reused. CSV keeps
the eight columns `schema,role,group,index,kind,elapsed_ns,generation,outcome`, changes schema only
to the declared layer-publication identity, and uses groups `candidate_v3_max`, `candidate_v3_min`.
Generations are 1 through 50 across both groups; summary rows repeat their selected sample's
generation/index/elapsed value, choosing the first occurrence in a tie. Successful rows say
`written`; a failed write's row says `failed` and leaves the whole journal invalid.

Before timing, the runner records the eleven common phase identity fields, its own
`publication_apk_sha256`, actual self-instrumenting package/UID and PID/start pair, fixed profile,
fixture hash, both envelope sizes, output/work paths and the fixed population/bound facts.
Its installed persistence-test APK is hashed and its actual process UID is checked. App/test APK
hashes are the host-bound candidate identities; this separate UID does not claim to inspect the
main app's private preservation guard. Explicit preservation attestation supplements the host's
complete manifest and native preservation proof. Neither supplied hashes nor a local directory
admit collection on their own.

The prefix is `p4-layer-publication-<first twelve preflight SHA-256 characters>`. A fresh `files/`
directory with that name holds `publication.csv`, `publication.status`, and `identity.txt`; a fresh
`no_backup/` directory with the same name holds the real AtomicFile record. Reservations refuse
existing entries. Partial reservations and the final or failed record state are retained, including
zero-byte files. No phase finally block deletes them. The production writer's own AtomicFile
behavior remains part of the measured operation and is not intercepted. Identity is persisted and
emitted as one status-code 3 `p4LayerPublicationIdentity` line before the worker; the host analyzer
must reconcile those two copies exactly. Complete/invalid status and the bounded CSV are published
after the worker, so reporting I/O is outside timed operations. Historical publication selection,
file names, schemas and cleanup remain unchanged.

The publication preparation implementation and 63-case host parser contract are recorded in the
[publication report](../reports/2026-10-03-layer-publication.md). The real AndroidTest helper classes
also pass the no-device reservation/format boundary through the explicitly selected fixture init
script. This is compile/host evidence only; actual AtomicFile timings and complete phase admission
remain pending. Identity reconciliation requires exactly one JUnit start, identity bundle, test
completion and terminal successful instrumentation code, in that order.

### SAF worker and evidence binding

The opt-in app runner uses `p4LayerCollect=saf-save-v1`, candidate artifact role and the exact SAF
slot above. It reuses the app admission guard and shared real-SAF preparation. The actual editor
loads the staged maximum through its production picker; its immutable current document is the
save input. No new codec dependency or second runtime is introduced. The real activity stays alive
and quiescent. Setup, including fixture load, 25 fresh destinations and all grants, has one
300-second bound. The whole timed worker has one 60-second bound; the native invocation remains
420 seconds with the stated reporting/drain reserve. The same existing JUnit Timeout mechanism
bounds setup and worker independently. Interrupted/frozen journals cannot start another operation.

Names use `i89-145-<first twelve preflight SHA-256 characters>-saf-`: `source.nenepixel`, then
`warmup-1.nenepixel` through `warmup-5.nenepixel`, followed by `sample-1.nenepixel` through
`sample-20.nenepixel`. One sequential fixed picker returns each granted destination exactly once;
it rejects open requests, exhaustion or a non-project create request. The full public adapter save
call, including its fixed picker return, is the timed interval. Reporting and adapter construction
are outside it. A Saved outcome must have consumed exactly the planned destination.

The CSV columns are
`schema,role,index,kind,elapsed_ns,destination_uri,grantee_uid,provider_uid,initial_byte_count,accepted_byte_count,picker_consumed,outcome,cleanup`.
Indices are zero-based separately within warmups and samples. The two summaries select the first
minimum/maximum of the 20 measured rows and repeat all its fields except kind. Initial byte count
is the setup-observed zero. Accepted byte count is 1,182,862 only for Saved, otherwise `not_verified`;
it is not an observation of partial file size. Outcomes are `saved`, `cancelled`, `failed`, with
cleanup `not_needed`, `deleted` or `delete_failed` as returned. Phase provider `deleted` means the
already-declared logical quarantine move. A non-Saved outcome, unconsumed/reused picker, missing
row, anomaly, cancellation or incomplete byte validation makes the journal invalid and stops work.

Fresh `files/p4-layer-saf-<first twelve preflight SHA-256 characters>/` holds `save.csv`,
`save.status`, `identity.txt`, and `setup.csv`. Status starts invalid. Setup appends each successful
grant observation before timing, retaining a prefix on failure. One exact identity is persisted
and emitted as status-code 3 `p4LayerSafIdentity` after complete setup and before the worker. It
binds the eleven common phase fields, process PID/start, profile, source URI/hash/bytes, app/provider
UIDs, all counts/bounds and report path. The host reconciles both copies and the ordered setup
records; every CSV URI/UID must match that same setup. All 25 destinations receive exact pinned
byte verification only after the timed batch. Complete status is written only after that succeeds.
No partial/successful source, output, quarantine or report file is deleted by the test.

The opt-in runner and strict host parser now implement these contracts. Actual compiled pure
journal/output helpers and 81 focused parser/source-agreement cases pass without a device; see the
[SAF-save report](../reports/2026-10-03-layer-saf-save.md). The shared publication-context boundary
is checked directly, while its unchanged numeric algorithm reuses its prior result. Real grants,
save timings and complete phase admission remain pending.

## Frame fixture staging before release-like capture

Each of the eight layer/underlay frame slots stages one new provider document before its
release-like capture. Single-layer slots need no fixture staging. The opt-in app AndroidTest
`p4LayerCollect=frame-fixtures-v1` uses the same real-SAF helper and target activity as memory/save
preparation. It has a 240-second JUnit bound and a 300-second native bound, separate from the frame
operation population. This is one functional setup invocation per applicable slot, attempt 1 only;
it produces no latency sample or numerical performance verdict.

The immutable role's debug app and app-test APK are verified with the common eleven-field phase
admission and preservation guard. The slot ID supplies its group/sequence/comparison role; only
`layers16` with the maximum project or `underlay` with the PNG is accepted. Actual role must be
that group's baseline artifact role or candidate as declared. The fresh provider name is
`i89-145-<first twelve preflight SHA-256 characters>-frame-<sequence>-<group>` plus `.nenepixel`
or `.png`. The helper checks the empty destination and real target UID grants, writes pinned asset
bytes through ContentResolver and verifies their complete read-back. It uses no document model or
second runtime and performs no drawing gesture.

A fresh app-private `files/p4-layer-frame-fixture-<hash-prefix>-<sequence>/fixture.txt` is reserved
before launch. The file starts empty and records the common phase identity, group, fixture
name/asset/URI/bytes/hash, actual target/provider UIDs, PID/start, and report path only after staging
succeeds. The same line is emitted in status-code 3 as `p4LayerFrameFixture`. Empty/partial output
and the provider destination remain on failure. Host acceptance requires exact persisted/emitted
agreement and successful instrumentation including activity closure. Source staging does not prove
release-like load, underlay placement or rendered preview; those checks stay at the collector's
separate setup boundary, before warmups. A later release-like install cannot replace the required
normal app picker operation or its visible/quiescent checks.

These extra setup invocations/installations must be included in the derived phase wrapper budget
before admission. The already fixed frame gesture counts and numerical gates are unchanged.

The [staging implementation report](../reports/2026-10-03-layer-frame-staging.md) records candidate
and oldest-baseline compilation, actual spec/catalog agreement and retained identity checks.

## Release-like UI and functional preview preparation

Phase-only setup uses the collector's existing UI primitives and the production New/Load/picker
paths. Retained staging text and instrumentation output are revalidated with their pinned hashes,
full eleven-field context, slot, build and production before the normal release-like picker opens
the exact provider name. The five-field frame context remains unchanged. The setup record does
not itself attest preservation, native completion or full admission; the outer caller owns those.

Each non-single family performs one functional gesture before its warmups, with screenshots before,
while preview is held and after commit. This is twelve additional functional gestures across the
eight staged slots, outside the unchanged 620 measured and 110 warmup operations. Pixel probes at
fixed document-cell centres verify opaque black on the intended stroke and unchanged off-stroke
pixels. The layered background must match the pinned grayscale composite. The underlay pattern
must match its fixed source formula composited at alpha 128 over the actual empty-work screenshot
(at most two RGB levels of rendering-rounding difference). This visual check supports the separately
source-proven exact placement/alpha; it cannot distinguish adjacent alpha values by itself.

The underlay requires a fresh 256-square work, a successful explicit PNG pick, shown visibility,
closed layer panel and no adjust bar. Normal work replacement runs after the functional proof and
after each family's warmups. Successful New/Load uses the existing publication-wait and switch
commit path to clear autosave tracking. The unchanged commit/Undo sample sequence retains its
ordinary production autosave behavior; it is not relabelled as globally idle between operations.

The combined UI preparation has a 300-second cumulative allowance per slot, including native
calls, waits and host validation. It is separate from fixture staging and the gesture allowance;
native setup calls use the remaining allowance, capped at 30 seconds. Every setup XML/PNG and
failure prefix is retained under fresh names. Expiry invalidates the slot without an automatic
retry. This allowance is included in the complete collector budget in the routing section below.

ADR 0034 underlay memory runs on a separate asynchronous path and exposes no completion status
in the UI. A successful picker, stable screenshot or fixed sleep does not prove that its recall or
publication has settled. This remains an explicit admission gap: these preparation helpers leave
the frame live barrier closed until that proof and outer preservation/manifest routing are complete.

The [UI preparation report](../reports/2026-10-03-layer-frame-ui.md) records the host pixel,
picker, retained failure, source compatibility and bounded-call checks.

## Pending admission decisions

### Phase collector and analyzer routing

The existing entry points resolve the explicit phase's full ordered catalog and its separate
artifact role. `frame_experiment.geometry` has exactly the four artifact-role keys: baseline_single
and candidate require `canvas16_bounds` and `canvas256_bounds`; baseline_layers16 and
baseline_underlay require only `canvas256_bounds`. Unused 16-square collector arguments are omitted
for the latter groups. Legacy calls retain their existing required geometry behavior.

Each candidate decision derives its five-field frame context from its own group's completed
baseline, the pinned baseline build/production/APK, and verified analysis/capture-seal hashes.
Foreign groups, experiments, preflights, changed sealed bytes and incomplete predecessors refuse
before collection. Frame-slot-v2 records bind that context and the complete frame file inventory.
Memory analysis consumes every preceding memory analysis in order, each bound to the completed
slot's seal. Publication and SAF use the existing strict parsers with their full raw identity inputs.
Phase analyses declare canonical `raw_inputs` names, sizes and hashes. Frame analyses additionally
revalidate the retained setup sequence, bounded cumulative time and actual PNG pixels using the
same geometry implementation as collection. The [routing report](../reports/2026-10-04-layer-slot-routing.md)
records the focused checks and remaining outer admission requirements.

Non-single frame preparation runs in a fresh `fixture-preparation` directory. Its native setup is
followed by stopping all three writer packages and bounded report capture even on failure. Only
successful native execution plus a verified saved/emitted fixture identity may precede release-like
collection. These stopped calls add 210 seconds (three stops, three absence probes, one report),
and release-like installation's retained intent adds four identity probes, 120 seconds, in addition
to the existing frame collector's installed-APK verification. The resulting maximum collector bound
is 3540 seconds, within the 3600-second native cap. This is
still not the whole-session archive/restoration bound. Partial setup and install evidence remain
available for outer cleanup; no historical private-file quarantine runs for phase slots.

### Device-lane execution and retained output

The existing lane planner selects phase behavior only from the explicit phase protocol. It resolves
the canonical slot to its artifact role and derives the common eleven-field Android context from
that role, the exact reserved manifest hash and `device.asset_preservation.session`. The latter must
match the verified preservation-v2 record before admission; a caller-supplied attestation is not
sufficient. Baseline roles require only their declared three APKs; candidate adds publication.

Memory and SAF run on debug app/test with verify compilation. Publication is self-instrumenting
and uses only its candidate publication APK, also verify compiled. Frame preparation installs the
debug app and, for the eight staged slots, app-test; the existing frame collector later installs and
verifies release-like with speed-profile. A staged frame uses its additional fixed 300-second setup
invocation. These extra bounded calls are added to the fixed gesture-derived frame allowance.

The collector budget charges 120 seconds per install/dexopt, 30 seconds per quiescence/probe or
report-reservation check, the complete instrumentation/setup allowance and 60 seconds host reserve.
Each install reserves four identity probes for before/after pm-path and SHA-256. Frame collector's
own release-like work remains in its existing bound. Every derived collector bound must be at most
3600 seconds before a slot is reserved. This does not yet define the whole phase deadline: stopped
report capture, provider/work archives, original verification and restoration have separate pending
cleanup/session budgets and must be fully accounted before admission.

An explicitly supplied phase operation budget shares one running monotonic clock across its
native observation, snapshot, install and restoration calls. Its integer allowance is 1–3600
seconds. Each new native invocation uses the smaller of its existing ceiling and the whole
remaining seconds after reserving 15 seconds for native termination/draining. No positive whole
second, a stopped/malformed clock or an expired budget refuses before launching another command.
Host verification also checks expiry before reporting success. Budget omission preserves historical
call ceilings; an explicitly null budget is invalid. A phase inventory admits at most 4096 discovered
entries before any stat/hash batches; retained discovery and partial outputs are never replaced.
The budget helper is part of the immutable snapshot source inventory. This shared mechanism does
not select the final per-slot cleanup or whole-session allowance and does not reopen admission.

Phase install replacement writes an immutable intent before invoking install and retains its
result even when install/readback fails. A missing result means an incomplete attempt, never proof
that the old APK remains. The final restoration must reconcile that intent with the actual installed
APK. No clear/uninstall or data deletion is permitted.

Phase reports are copied by the outer cleanup after all writer packages are confirmed stopped,
on both collector success and failure. Each bounded encoded transfer retains empty/partial host
outputs and native evidence. Exact private paths are derived from the phase and slot; existing host
outputs refuse replacement. The remote read also checks process absence and regular, unlinked path
components. Missing reports are retained as missing evidence and invalidate an otherwise successful
slot. No phase report is moved or deleted, and legacy quarantine is not used for these unique paths.

### Measurement state between slots

Each slot's stopped cleanup uses the existing ADR0035 planner to move only measurement-created
recovery files, remembered underlays and the two ProfileInstaller files to the fresh
`no_backup/p4-layer-slots/<session>/<slot-id>/` archive. The original guard stays isolated until
the whole phase finishes or stops. Full inventory checks surround each no-overwrite mkdir/move;
the current installed APK, preservation record, preflight and slot bind the retained result.
No-op resets issue no mutation. A failure consumes no extra attempt and preserves every applied
prefix for the existing final restoration policy. Full app/provider host archives and the outer
finite lifecycle are still required before admission; a reset result is not phase acceptance.

### Four immutable artifact roles and complete source inventory

The phase preflight schema is `nene-pixel-p4-layer-preflight-v1`. Its role keys are exactly
`baseline_single`, `baseline_layers16`, `baseline_underlay`, and `candidate`, in that build order.
The baseline production commits remain the group table above. The shared candidate production is
`1f9bb1637058d3fa4a98122f4942406211bd1c69` (accepted #172). Each measurement build is a descendant
test overlay whose production-tree hash equals its declared production commit; documentation and
test-source changes cannot select another production implementation. A future correction requires
a recorded new candidate/collection plan, not an editable role alias inside this experiment.

Every role has `app_debug`, `test_debug`, and `app_release_like`. Only candidate has
`publication_test`, because no baseline publication is registered. Debug app/test are used for
fixture/grant preparation and, for the layer baseline and candidate, retained memory; release-like
is the measured frame APK. They keep the existing package, variant, debuggability, instrumentation
and dexopt contracts. APK embedded revision must equal that role's measurement build commit.
No unnecessary baseline publication APK or old host timing lane is built for the phase.

The measurement inventory is derived from each immutable build's tracked tree. It includes every
file in both app and persistence `androidTest` source sets (Kotlin, Java, manifests, resources and
assets), the existing quality PowerShell/measurement tooling, and the checked phase fixture directory.
It therefore includes the provider's Java implementation/helper and AndroidManifest, not only Kotlin
files matching the old publication name. Mandatory shared fixture/helper/provider paths cannot be
omitted; actual per-file hashes and build-commit blobs are checked. All role APK/source records are
closed sets: missing, duplicate, extra, aliased or drifting paths are refused. Production inputs
continue to be independently protected by the full production-tree hash.

Both app-test assets and candidate persistence-test assets contain exactly one
`assets/maximum-layered.nenepixel` and one `assets/underlay-grid.png` entry with the pinned byte
lengths/hashes above. Neither fixture may appear in debug or release-like production APK assets.
The packaged app-test manifest must bind the existing provider class/authority, exported and grant
flags, `MANAGE_DOCUMENTS` permission, and DocumentsProvider action. Source checks and APK-entry
checks remain distinct; generated bytes or test-source compilation alone do not prove packaging.

The existing preflight gains explicit phase catalogs/inventory checks before its complete admission
branch is enabled. Its historical default remains v7. The unified phase schedule is the existing
twelve frame slots, ten memory slots and two storage slots in order, with exactly one attempt each.
These additions do not reopen collection while frame setup, executable wrappers, current native
preservation and final profile/artifact/budget agreement are pending.

Collector/analyzer integration of first-preview association, the exact underlay image/placement
proof, executable memory/storage schema agreement and real SAF grant proof,
artifact/profile bindings, complete phase stop/budget rules and preservation-v2 integration must be
completed before collection. A verified read-only snapshot is not an isolated measurement session.

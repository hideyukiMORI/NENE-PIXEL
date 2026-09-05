# P2 Pixel Representation and Limit Evidence

Status: complete for `P2-01` / Issue #38 and supporting accepted ADR 0005. This ledger records the
decision evidence; ADR 0005 and the canonical governing documents define the production contract.

## Decision boundary

This section is the current continuation contract and supersedes the decision use of the earlier
"largest passing maximum", combined command/memory, exhaustive matrix, and paired-compositor
protocols retained later in this ledger. Those passages and all artifacts already collected under
them remain immutable chronological diagnostic evidence; they are not deleted, overwritten,
retroactively reclassified, or required to be recollected under their original protocol.

ADR 0005 may become `accepted` after one semantic, storage, and conservative MVP supported-cap
policy has passed the applicable lane-separated matrix on the named physical minimum Android
profile. The decision comparison is limited to the current representation, flat packed RGBA8888,
and one best tiled or copy-on-write challenger selected by a predeclared deterministic host
screening rule. Host JVM and emulator results are auxiliary. Physical command-tail and retained-
memory/PSS evidence remain required, but strict SurfaceFlinger/Perfetto correlation is deferred
until the selected renderer path exists and is not an ADR-0005 acceptance blocker.

The lanes are independent:

| Lane | Current collection rule |
| --- | --- |
| Correctness | Prove exact pixels, semantic equality/hash, ordering, containment, revisions, inverse round trip, affected/unaffected pixels, `ChangeSet`, invalidation, and atomic no-op or typed rejection behavior at representative and boundary workloads. |
| Latency and ART tail | Preserve raw warmup/sample rows and cheap public invariants. Do not force GC/finalization or perform a full-document equality scan, digest, or hash between measured samples. |
| Retained memory and PSS | Use independent invocations with an explicitly retained owner and post-GC checkpoints. Forced GC/finalization is allowed only here and is not a per-command GC result. |
| Frame | Measure representative correct-frame and deadline behavior after the chosen renderer path exists. Defer strict compositor/physical-present correlation to the renderer milestone. |

A supported cap must be at or below an explicitly measured passing point, include documented
headroom for the named MVP workload, and preserve typed cap-plus-one rejection. It is not a claim
of the largest possible or implementation-theoretical maximum. A passing largest measured point
does not require open-ended exploration before a conservative cap can be selected.

Current evidence state:

| Evidence | State | Decision use |
| --- | --- | --- |
| Immutable M1 sparse host reproduction | complete for the rerun recorded below | starting evidence only |
| Current-main P2 representation route | historical current-object routes and the clean 256-square command lane are complete | current baseline fails the clean command target and remains immutable diagnostic evidence only |
| Analytical storage candidates | flat packed and tiled/COW host screening, physical packed-candidate comparison, and palette U8 contract are recorded | T16 fails the dense physical kernel target; analytical candidate implementations were removed after evidence collection |
| Named emulator | ART command harness complete in explicit auxiliary mode | cannot satisfy physical evidence |
| Named physical minimum Android device | clean current command, flat/T16 kernel, three independent retained-memory comparisons, and migrated production run-06 recorded on the fixed profile | selected production command lane passes every p95/p99 target with zero blocking GC |
| Renderer/compositor evidence | canonical snapshot-to-bitmap and one nearest-neighbor bitmap draw are implemented and the selected-production handoff test passes on the named device | strict compositor evidence is deferred to the renderer milestone and is not an ADR-0005 acceptance blocker |
| Accepted representation or supported caps | flat packed RGBA8888 plus the one `PixelLimits` policy are accepted | width/height 256, derived area 65,536, raw stroke 262,144, patch 65,536, history 64, retained changes 524,288 |

Active waivers: none.

### Tiled/COW challenger screening decision

Before collecting any new physical candidate result, the immutable schema-v8 host artifact fixes
the tiled/COW screening rule: among T16, T32, and T64 rows for the 256x256 canvas that have a
non-empty p95 latency and passing correctness, minimize the maximum recorded p95 latency across
all operation rows; break a tie with the lower maximum p95 allocation, then the smaller tile edge.
This rule is risk-oriented, deterministic, and selects only the challenger for physical comparison;
it does not select the production representation or a supported cap.

Applying the rule to `host-candidates.csv` schema v8, SHA-256
`4DF70289D142C5982FFE75F9037A6D4EF84DD355E4708306C763DA1E8768792E`, gives:

| Tiled/COW candidate | Included 256x256 metric rows | Maximum p95 latency | Maximum p95 allocation |
| --- | ---: | ---: | ---: |
| T16 | 41 | 7,219,400 ns | 10,088,320 bytes |
| T32 | 41 | 12,177,200 ns | 10,088,320 bytes |
| T64 | 41 | 7,880,500 ns | 10,088,320 bytes |

T16 is therefore the only tiled/COW challenger admitted to the new physical comparison. T32 and
T64 remain immutable diagnostic rows and are not carried forward into another test matrix.

## Immutable M1 reproduction

The historical route was rerun from immutable merge commit
`37c0f57d59a73fd962c285e79e8e193a81402d31` in the isolated local worktree
`../NENE-PIXEL-m1-proof-38`. The worktree was detached at that exact commit. The command was:

```powershell
Push-Location ..\NENE-PIXEL-m1-proof-38
.\gradlew.bat measureM1VerticalSlice --rerun-tasks --no-build-cache
Pop-Location
```

Result: success, 26 tasks, 38 seconds. The measurement correctness assertions remained enabled.
This run reproduces the historical route; it is not a current-main P2 result and does not reuse
the meaning of any P2 viewport or representation metric.

### Raw artifacts and checksums

The CSV files are ignored build artifacts under the isolated worktree. Checksums use SHA-256
over the exact file bytes after the completed run:

| Raw path relative to isolated worktree | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/m1/core-measurement.csv` | 3,228 | `EBF1C41808DDFB8B24060B6712325BA7D8655F4774435D865C5A1BE4AC0E4C5E` |
| `build/reports/m1/interaction-measurement.csv` | 1,137 | `A61FB549C7BAFE298D4ABA67517586A026D9A9E995599193353517267851A68B` |

Checksum command:

```powershell
Get-FileHash build/reports/m1/core-measurement.csv -Algorithm SHA256
Get-FileHash build/reports/m1/interaction-measurement.csv -Algorithm SHA256
```

### Recorded profile and boundaries

| Item | CSV value |
| --- | --- |
| Schema | `nene-pixel-m1-measurement-v1` |
| Profile | `NENE-M1-WINDOWS-I9-10850K-JBR21` |
| OS | Windows 11 10.0 amd64 |
| JVM | OpenJDK 64-Bit Server VM 21.0.11+1-b1163.116 |
| Build variants | `core-jvm-test-output-in-presentation-debug-host-worker`; `presentation-debug-host-unit-test-worker` |
| Worker | isolated 512 MiB host test worker, one fork, as fixed by the immutable route |
| Warmup / samples | 20 / 50 per metric |
| Canvas fixtures | 16 x 16, 64 x 64, 256 x 256 for core; 16 x 16 for interaction |
| Allocation boundary | current HotSpot test-thread allocated bytes; retained heap and Android PSS excluded |

The metric boundaries are exactly those emitted by the immutable CSV:

| Metric | Included boundary | Important exclusions |
| --- | --- | --- |
| `snapshot_create` | `PixelSnapshot.create` defensive row-major ownership | input-list construction; retained heap |
| `patch_create` | canonical `rasterizeStroke`, change list, and patch creation | document transition; next snapshot |
| `command_apply_stroke` | gateway apply including patch, next snapshot, ChangeSet, and one-level history | fixture and command construction |
| `command_undo` | gateway recorded inverse transition and history move | prior stroke setup |
| `command_redo` | gateway recorded forward transition and history move | prior apply/undo setup |
| `translated_controller_commit` | M1 fixed offset translation, preview, one gateway command, and render projection | Compose pointer dispatch, draw, frame scheduling, GPU/compositor |

### Rerun observations

Latency and allocation values are descriptive observations, not pass thresholds or product-limit
claims. Allocation is current-thread HotSpot allocation, not retained bytes.

| Canvas | Metric | Positions | Median ns | p95 ns | Median allocated bytes | p95 allocated bytes |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 16 x 16 | snapshot create | 0 | 800 | 4,600 | 1,152 | 1,152 |
| 16 x 16 | patch create | 16 | 20,800 | 40,700 | 3,352 | 3,352 |
| 16 x 16 | command apply | 16 | 36,700 | 82,900 | 16,552 | 16,552 |
| 16 x 16 | command undo | 16 | 9,400 | 31,700 | 13,208 | 13,208 |
| 16 x 16 | command redo | 16 | 8,600 | 15,400 | 13,184 | 13,184 |
| 16 x 16 | translated controller commit | 16 | 81,100 | 174,600 | 23,088 | 23,088 |
| 64 x 64 | snapshot create | 0 | 3,300 | 13,800 | 16,512 | 16,512 |
| 64 x 64 | patch create | 64 | 50,000 | 77,100 | 12,544 | 12,544 |
| 64 x 64 | command apply | 64 | 70,200 | 101,000 | 47,568 | 47,568 |
| 64 x 64 | command undo | 64 | 41,400 | 47,400 | 35,032 | 35,032 |
| 64 x 64 | command redo | 64 | 40,500 | 82,700 | 35,008 | 35,008 |
| 256 x 256 | snapshot create | 0 | 44,400 | 68,700 | 262,272 | 262,272 |
| 256 x 256 | patch create | 256 | 34,500 | 100,300 | 44,728 | 44,760 |
| 256 x 256 | command apply | 256 | 609,200 | 783,300 | 576,608 | 576,648 |
| 256 x 256 | command undo | 256 | 486,200 | 631,100 | 531,888 | 531,888 |
| 256 x 256 | command redo | 256 | 495,600 | 602,300 | 531,864 | 531,864 |

The rerun remains consistent with the M1 conclusion: snapshot and command transitions contain
area-sized work even for a sparse diagonal, while patch construction scales with changed
positions. Normal host/JIT noise changes latency observations between runs; the fixed route makes
their boundaries comparable without turning them into pass/fail claims.

## Current-main P2 host route

The separately named current-main route was rerun from branch `adr/38-pixel-color-limits` at
source commit `691f8dd8b97bf9bf66a53339e172a94daae8e71e`. The exact command was:

```powershell
.\gradlew.bat measureP2RepresentationLimits --rerun-tasks --no-build-cache
```

Result: final recorded rerun success in 44 seconds with separately named 512 MiB application and
pixel-engine test workers. Schema `nene-pixel-p2-representation-limits-host-current-v4` contains
21 metric summaries, 174 raw samples, and six typed current-structure analysis rows. Schema
`nene-pixel-p2-representation-limits-host-candidates-v4` contains 380 test-only candidate metric
summaries and 3,800 raw candidate samples. Thirty metric summaries and 300 samples are the
pre-fixed candidate-native patch/inverse matrix. Every current-path sample retained its exact
snapshot, revision, patch/inverse, full-canvas affected region, unaffected pixels, history
availability, and typed no-op assertions. Every candidate sample matched the independent
row-major semantic pixels and exact inverse digest; candidate correctness failures,
cross-candidate digest mismatches, and raw-sample percentile recomputation mismatches were zero.

Candidate buffers, copy-on-write workspaces, measurement, contract tests, and CSV generation are
self-contained under `:core:pixel-engine` test source, which preserves the ARC-005 mutation
enclave without adding a production implementation path. The `:core:application` test route owns
only canonical command/history measurements and logical retained-count analysis. The root task
orchestrates both reports without a cross-module test-fixture dependency.

The diagnostic route uses five warmups and ten samples for standard rows and three warmups and
seven samples for dense/history rows. Its p99 is therefore the observed maximum, not sufficient
physical-profile p99 evidence.

| Raw path | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/host-current.csv` | 48,963 | `780E1B2295B80A790C2C34056B8B76F0B92F0B931FB41BAC9A1DD1B641A21C36` |
| `build/reports/p2/representation-limits/host-candidates.csv` | 3,688,628 | `F0C5DE2FEF339A0DE84265F563376683FBA93A271F90812BF1A909653468E3DB` |

Selected observations show why this run cannot authorize current limits:

| Workload | Canvas / changes | Host p95 ns | Host p99 ns | p95 allocated bytes |
| --- | --- | ---: | ---: | ---: |
| sparse pencil-equivalent | 1024 x 1024 / 1,024 | 23,060,000 | 23,060,000 | 8,591,792 |
| dense pencil-equivalent | 256 x 256 / 65,536 | 27,975,800 | 27,975,800 | 13,196,272 |
| dense reference-clear fixture | 256 x 256 / 65,536 | 17,130,200 | 17,130,200 | 13,196,248 |
| dense same-color no-op | 256 x 256 / 0 changes | 4,207,500 | 4,207,500 | 4,670,280 |
| dense forward patch apply | 256 x 256 / 65,536 | 22,804,400 | 22,804,400 | 524,480 |

The v4 canonical gap rows distinguish raw path, patch, and history units. Black is only a
reference-clear fixture and is not an accepted blank or eraser semantic:

| Current canonical boundary | Raw positions | Effective changes | History entries | Host p95 ns | p95 allocated bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| duplicated full-canvas raw stroke | 131,072 | 65,536 | 1 | 23,337,500 | 13,196,272 |
| black-on-black reference-clear no-op | 65,536 | 0 | 0 | 3,206,100 | 4,670,280 |
| reverse-row-major standalone patch create | 0 | 65,536 | 0 | 2,480,100 | 3,735,224 |
| final-record standalone patch late conflict | 0 | 65,536 | 0 | 2,566,500 | 262,272 |

The duplicate route asserts raw `P=2N` collapses to canonical `C=N`, the no-op retains the full
document state and empty history, shuffled create equals the canonical patch and round-trips, and
late conflict returns the exact typed final-position mismatch while the complete source snapshot
and revision remain unchanged. Standalone patch rows deliberately record zero raw stroke positions
and zero history entries.

These are HotSpot current-thread observations. They exclude retained heap, renderer work, ART,
PSS, and frames. The candidate route separately screens square and area-equivalent rectangular
canvases at 4,096, 16,384, and 65,536 pixels. Snapshot build uses one semantic color, exactly 256
colors, and deterministic high-entropy RGBA. High-entropy apply uses one pixel, diagonal, full
row, full column, 25%, 50%, and 100% serpentine changes. Selected 256-square one-pixel rows show
the copy boundary without selecting a candidate:

| Test-only candidate | Host p95 ns | p95 allocated bytes | Logical snapshot payload | Copied payload |
| --- | ---: | ---: | ---: | ---: |
| current object-list fixture | 102,500 | 524,520 | 65,536 references | 65,536 references |
| flat packed RGBA8888 | 74,900 | 262,312 | 262,144 bytes | 262,144 bytes |
| tiled/COW RGBA8888, tile 16 | 6,800 | 2,416 | 262,144 bytes | 1,024 bytes |
| tiled/COW RGBA8888, tile 32 | 4,000 | 4,704 | 262,144 bytes | 4,096 bytes |
| tiled/COW RGBA8888, tile 64 | 13,700 | 16,800 | 262,144 bytes | 16,384 bytes |

The ten host samples are diagnostic and do not rank the candidates. The object-list row is a
test-only structural fixture; canonical production behavior remains in `host-current.csv`. The
candidate-native dense patch/inverse slice below closes the previous common-driver gap for the five
fixed configurations. Palette U8 pack/index correctness, unsigned index 255, and typed rejection
of a 257th semantic color are covered by contract tests, but palette performance remains blocked
on semantic ownership. The current CSV retains the logical reference/change analysis and explicit
exclusions. The schema v5 raw-path slice below closes the dense candidate
duplicate/no-op/reference-clear input gap. Analytical history retention, retained heap, ART, PSS,
and frames remain required.

### Pre-fixed candidate patch and inverse slice

Before collecting candidate schema v4, the next host slice fixes five test-only snapshot/patch
configurations. Palette performance remains excluded.

| Configuration ID | Snapshot | Patch and inverse |
| --- | --- | --- |
| `current-object-list__int-position-object-records-materialized-inverse-v1` | object-list RGBA | analytical `Int`-position object records; materialized inverse records |
| `flat-packed-rgba8888__packed-triplets-shared-inverse-v1` | flat packed RGBA8888 | canonical position/before/after `IntArray` triplets; shared directional inverse |
| `tiled-cow-rgba8888-t16__packed-triplets-shared-inverse-v1` | tiled/COW RGBA8888, edge 16 | the same packed triplet and shared inverse contract |
| `tiled-cow-rgba8888-t32__packed-triplets-shared-inverse-v1` | tiled/COW RGBA8888, edge 32 | the same packed triplet and shared inverse contract |
| `tiled-cow-rgba8888-t64__packed-triplets-shared-inverse-v1` | tiled/COW RGBA8888, edge 64 | the same packed triplet and shared inverse contract |

The object-record patch is a logical analytical fixture, not a byte-for-byte model of production
`PixelChange`: it retains a primitive `Int` position plus `before` and `after` `PixelColor`
references. The snapshot portion remains the object-list candidate used by the preceding host
matrix.

The standalone workload is exactly one 256x256 canvas with 65,536 changed pixels and a full-canvas
affected region. Independent initial and after semantic colors are submitted in reverse row-major
order and retained canonically in row-major position order. The late conflict is injected at the
final canonical position, 65,535. Each operation has five warmups and ten diagnostic samples for
five configurations, producing 30 metric rows and 300 raw sample rows. For operation index `n`,
the five-configuration order starts at configuration index `n mod 5` and wraps once; a
configuration's ten samples remain contiguous rather than interleaved.

| Operation | Timed interval |
| --- | --- |
| shuffled patch create | shared order canonicalization, candidate-native record/array materialization, and defensive ownership |
| inverse create | materialized object-record inverse or packed directional inverse-view creation |
| forward apply | complete typed preflight validation followed by candidate-native forward snapshot apply |
| inverse apply | complete typed preflight validation followed by candidate-native inverse snapshot apply |
| exact round trip | forward apply, inverse creation, and inverse apply |
| final-record late conflict | complete typed preflight through the final before-value mismatch and rejection return; no output snapshot materialization |

Fixture construction, semantic input generation, and full correctness verification are outside
every timed interval. Every sample must preserve canonical ordering, exact affected region,
source/applied/restored revision, complete semantic and unaffected pixels, and atomic typed
rejection. Application results are closed as `Applied`, `SnapshotRepresentationMismatch`,
`ShapeMismatch`, `RevisionMismatch`, or `BeforeValueMismatch`; expected rejection is evidence
rather than an exception.

Schema v4 records configuration, snapshot representation, patch layout, inverse storage kind,
forward and inverse record counts, primitive payload bytes and reference slots in separate units,
shared backing payload, canonical-order digest, affected-region bounds, before/after/restored
revision and pixel digests, result or rejection kind, and raw timing/allocation samples. Logical
object storage counts each record's primitive position, two color-reference fields, one occupied
list-element reference slot, and the record itself. Logical packed storage counts the payload and
three owned primitive arrays. Wrapper, list, and array headers, spare list capacity, and shared
`PixelColor` referents are excluded. Forward, inverse-additional, shared, and retained-union counts
use those same units; reference slots are not converted to estimated bytes, and none of these
counts are retained-heap evidence. The HotSpot rows are diagnostic only and cannot select a
candidate or a hard limit.

#### Recorded schema v4 result

The recorded artifact contains all 30 unique operation/configuration metric pairs and 300 raw
standalone samples. Recomputing median, p95, and p99 latency and allocation from all 3,800 candidate
samples produced zero mismatches across the complete 380-metric report. Every standalone row has
the fixed 256x256 canvas, 65,536 changes, reverse-row-major input, full `0:0:256:256` region,
zero unaffected pixels, and lifecycle revision `0 -> 1 -> 0`. All correctness fields passed.

Each cell below is `p95 latency ns / p95 allocated bytes`. With ten diagnostic samples, p95 and
p99 are both the observed maximum and do not rank the candidates.

| Configuration | Create | Inverse create | Forward apply | Inverse apply | Round trip | Late conflict |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| object analytical/materialized | 6,509,100 / 7,993,136 | 512,100 / 1,835,208 | 1,179,000 / 524,520 | 2,187,100 / 524,520 | 5,088,000 / 2,884,224 | 1,258,700 / 56 |
| flat packed/shared | 5,029,400 / 7,401,256 | 17,400 / 160 | 511,600 / 262,312 | 412,500 / 262,312 | 906,700 / 524,760 | 437,500 / 56 |
| tiled/COW T16 packed/shared | 4,904,400 / 7,401,256 | 7,000 / 160 | 4,803,600 / 2,180,576 | 6,149,100 / 2,180,576 | 8,748,900 / 4,361,288 | 1,978,700 / 56 |
| tiled/COW T32 packed/shared | 5,615,800 / 7,401,256 | 4,700 / 160 | 3,291,200 / 1,644,768 | 3,537,900 / 1,644,768 | 9,990,900 / 3,289,672 | 1,305,300 / 56 |
| tiled/COW T64 packed/shared | 5,285,500 / 7,401,256 | 10,000 / 160 | 6,959,300 / 1,641,888 | 3,760,700 / 1,641,888 | 8,186,000 / 3,283,912 | 2,049,100 / 56 |

The separate-unit logical patch counts agree with the pre-fixed layout:

| Patch layout | Forward | Inverse additional | Shared | Retained union |
| --- | --- | --- | --- | --- |
| object/materialized | 262,144 primitive bytes; 196,608 reference slots; 65,536 records | same as forward | zero | 524,288 primitive bytes; 393,216 reference slots; 131,072 records |
| packed/shared | 786,432 primitive bytes; three arrays | zero | same as forward | same as forward |

All five late-conflict rows returned `BeforeValueMismatch` at position 65,535 with input/output
revision `0/0`, identical input/output SHA-256 pixel digests, and no materialized output snapshot.
Canonical-order, forward-patch, inverse-patch, lifecycle, operation-state, and unaffected-pixel
SHA-256 fields matched across all five configurations for each operation.

At schema v4, this slice did not implement production history, candidate
duplicate/no-op/reference-clear raw paths, retained-history budgets, physical ART/PSS/frame
comparisons, semantic color decisions, or production migration. The raw-path portion is addressed
by the separately pre-fixed schema v5 slice below; the other gates remain while ADR 0005 is
proposed.

### Pre-fixed candidate raw-path slice

Before collecting candidate schema v5, the next host slice fixes one test-only raw-path boundary
in front of the five configurations above. It mirrors the current `rasterizeStroke` contract:
ordered raw positions use first-occurrence-wins duplicate collapse, source-equal target colors are
filtered, effective changes alone enter the strict candidate-native patch factory, and an empty
effective set returns typed `NoChanges` without creating a patch. The strict factory continues to
reject duplicate or unchanged canonical patch input. This is one shared test-only path inside the
pixel-engine test boundary, not a second production implementation.

The canvas is exactly 256x256 (`N=65,536`). Opaque black is only a reference-clear fixture; this
slice does not select semantic blank or eraser behavior. Opaque red and opaque black are the only
input colors, so every workload is independent of the unresolved palette-ownership decision.

| Workload | Source to target | Raw order | Raw positions | Unique positions | Duplicates | Unchanged unique | Effective changes | Result |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| duplicate changed | black to red | paired row-major (`0,0,1,1,...`) | 131,072 | 65,536 | 65,536 | 0 | 65,536 | `Rasterized` |
| changed reference-clear | red to black | row-major | 65,536 | 65,536 | 0 | 0 | 65,536 | `Rasterized` |
| reference-clear no-op | black to black | row-major | 65,536 | 65,536 | 0 | 65,536 | 0 | `NoChanges` |
| same-color no-op | red to red | row-major | 65,536 | 65,536 | 0 | 65,536 | 0 | `NoChanges` |

Each workload has five warmups and ten diagnostic samples for all five configurations, producing
20 metric rows and 200 raw sample rows. For workload index `n`, configuration execution starts at
index `n mod 5` and wraps once; each configuration's samples remain contiguous. The timed interval
contains the raw position scan, duplicate collapse, source-color comparison, canonical change
collection, candidate-native patch materialization and defensive ownership when changes exist,
and the typed result return. Immutable snapshot/input fixture construction, digests, apply/inverse
round trip, and full correctness verification remain outside timing. These raw-only values are not
directly comparable with current `CommandGateway` rows that include command dispatch, apply, and
history commit.

Every sample must prove that the ordered raw-input digest and source snapshot revision/pixel
digest are unchanged. `Rasterized` must additionally prove exact change count, row-major order,
before/after values, full affected region, apply revision `0 -> 1`, exact inverse restoration to
revision 0, and zero unaffected pixels. `NoChanges` must prove no patch or inverse was created,
revision and every pixel remain unchanged, all `N` pixels are unaffected, affected-region fields
are absent, and every persistent patch-storage count is zero. Result and semantic evidence must
match across all five configurations.

Schema v5 adds `raw_input_digest_sha256`, `unique_path_positions`,
`duplicate_path_positions`, `unchanged_unique_positions`, and
`canonical_change_digest_sha256`. The raw digest includes canvas, source revision and row-major
source pixels, every ordered raw position including duplicates, and the target RGBA value. The
canonical-change digest includes ordered position/before/after triples; `NoChanges` uses a tagged
empty digest. These fields make `P -> C` and raw-input identity independently auditable while the
existing position-only canonical-order digest retains its v4 meaning.

#### Recorded schema v5 raw-path result

The pre-fixed route was collected from source commit
`d4ddc0a6bf01ff941e42632bc638f4cffdd740dd`. The schema v5 artifact contains 400 candidate metric
rows and 4,000 raw sample rows in total. The new raw-path portion contains all 20 unique
workload/configuration metric pairs and 200 samples. Recomputing median, p95, and p99 latency and
allocation from those samples produced zero mismatches. Raw matrix, typed-result, digest,
affected-region, lifecycle, no-op absence/storage, and cross-configuration failures were all zero.

| Raw path | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/host-current.csv` | 48,963 | `F4837AD6991B069DF26BA7DD8B82857E7445CC93B0D85C6976C710451973D41E` |
| `build/reports/p2/representation-limits/host-candidates.csv` | 4,168,816 | `25075A9152A5E5C319EF17471A18E95EABC2C25815654B1FDE0B77568CB2E0A5` |

Each cell below is `p95 latency ns / p95 allocated bytes`. Ten samples make p95 and p99 the
observed maximum; the values are diagnostic and do not rank candidates. The raw-only candidate
boundary must not be compared directly with current `CommandGateway` rows.

| Configuration | Duplicate changed | Reference-clear changed | Reference-clear no-op | Same-color no-op |
| --- | ---: | ---: | ---: | ---: |
| object analytical/materialized | 5,528,200 / 9,107,352 | 4,328,300 / 8,845,208 | 930,300 / 327,728 | 706,400 / 327,728 |
| flat packed/shared | 5,437,700 / 8,515,472 | 4,269,600 / 8,253,328 | 762,100 / 327,728 | 480,000 / 327,728 |
| tiled/COW T16 packed/shared | 7,511,300 / 8,515,472 | 5,951,000 / 8,253,328 | 1,430,400 / 327,728 | 1,179,300 / 327,728 |
| tiled/COW T32 packed/shared | 6,139,200 / 8,515,472 | 7,854,700 / 8,253,328 | 1,317,200 / 327,728 | 1,856,300 / 327,728 |
| tiled/COW T64 packed/shared | 7,957,700 / 8,515,472 | 6,774,800 / 8,253,328 | 1,103,100 / 327,728 | 1,748,200 / 327,728 |

All ten `Rasterized` rows collapsed or filtered the raw input to exactly 65,536 row-major changes,
reported the full `0:0:256:256` region, preserved the source during creation, applied at revision
`0 -> 1`, and restored every pixel and revision through the inverse. All ten `NoChanges` rows
retained lifecycle `0 -> 0 -> 0`, reported all 65,536 pixels unaffected, left region and patch
digests absent, and recorded zero in every patch-storage field. For each workload, raw-input,
canonical-change, patch, lifecycle, operation-state, and unaffected-pixel digests matched across
the five configurations. The existing strict patch factory continues to reject direct duplicate
or unchanged canonical input; only the upstream test-only raw boundary collapses and filters it.

This result closes only the dense host raw-input slice. It does not select black as blank or eraser,
compare equivalent production and candidate raw-only latency boundaries, implement product
history, or provide candidate retained-heap, ART, PSS, frame, sparse/rectangular native-patch,
semantic-color, maximum-boundary, or production-migration evidence.

### Pre-fixed retained analytical history slice

Before collecting candidate schema v6, the next host slice fixes test-only analytical retention
for the same five candidate configurations used by the native patch and raw-path slices. It does
not implement or change production `ChangeSet`, `CommandGateway`, `HistoryEntry`, history state,
public API, or command behavior. One analytical retained entry owns one already-prepared
candidate-native forward/inverse pair; the analytical history owner retains those entries and one
current candidate snapshot. It is evidence about entry-count and retained-change-count interaction,
not a second production multi-step history path.

The canvas is exactly 256x256 (`N=65,536`). An entry contains one canonical patch, whose unique
positions cannot exceed `N`; this follows both the current `PixelPatch` duplicate-position contract
and the candidate-native patch contract. Therefore a full Cartesian product of every positive
entry count and retained-change count would be invalid: one entry cannot retain `2N`, `4N`, or
`8N` changes without inventing multiple patches inside one entry. The executable matrix contains
every pair for which `T <= H * N`, where `H` is retained entries and `T` is total retained changes:

| Retained entries `H` | Total retained changes `T` | Changes per entry `C = T / H` | Workload points |
| ---: | --- | --- | ---: |
| 0 | 0 | 0 | 1 |
| 1 | `N` | 65,536 | 1 |
| 8 | `N`, `2N`, `4N`, `8N` | 8,192; 16,384; 32,768; 65,536 | 4 |
| 16 | `N`, `2N`, `4N`, `8N` | 4,096; 8,192; 16,384; 32,768 | 4 |
| 32 | `N`, `2N`, `4N`, `8N` | 2,048; 4,096; 8,192; 16,384 | 4 |
| 64 | `N`, `2N`, `4N`, `8N` | 1,024; 2,048; 4,096; 8,192 | 4 |

The `H=0, T=0` row is the only canonical empty case. Positive entries with zero changes,
zero entries with positive changes, and `H=1` with `T > N` are contract rejections rather than
measured rows. All 17 positive rows divide exactly, so every entry in one workload has the same
non-zero `C` and the sum is exactly `T`; there is no fractional, rounded, or extrapolated entry.
This matrix evaluates every Issue #38 entry candidate (`0/1/8/16/32/64`) and every positive
retained-change candidate (`N/2N/4N/8N`) without violating the one-entry/one-patch meaning.

Opaque black, red, and green are analytical fixture values only and do not select blank, eraser,
palette ownership, alpha, or composition semantics. A positive fixture starts with uniform opaque
black. Entry index `i` changes the first `C` row-major positions to opaque red when `i` is even and
opaque green when `i` is odd, so every entry has exactly `C` effective changes. Revisions form the
exact chain `0 -> 1 -> ... -> H`; the retained current snapshot is the state at revision `H`.
The empty fixture retains the unchanged revision-0 snapshot and no patch pair.

Logical storage is aggregated over all `H` entries, but remains separated into these non-additive
boundaries:

| Storage boundary | Meaning |
| --- | --- |
| retained snapshot | payload and reference slots of the one current candidate snapshot; never part of the patch union |
| retained forward patch | sum of storage owned by all forward patches |
| retained inverse additional | storage owned only by materialized inverses; zero for a shared directional inverse |
| retained shared patch | forward payload also referenced by directional inverses; zero for a materialized inverse |
| retained patch union | unique patch payload strongly reachable from all entries; forward plus inverse-additional for object/materialized, forward counted once for packed/shared |

For object/materialized entries, forward and inverse-additional primitive bytes, reference slots,
object records, and backing-array counts are reported separately; shared counts are zero. For
packed/shared entries, inverse-additional counts are zero, shared counts describe the same three
primitive arrays per entry as the forward view, and the retained union counts that payload once.
Forward and shared counts overlap for packed/shared storage and must not be added. Snapshot payload,
patch payload, reference slots, object records, and primitive backing arrays also remain separate
units. Wrapper/list/array headers, spare capacity, shared `PixelColor` referents, and analytical
owner/entry wrappers are excluded from these logical fields, consistently with schema v4/v5.

Each workload/configuration pair has five warmups and ten diagnostic samples. Workload order is
the table order above, with positive `T` ascending inside each `H`. For zero-based workload index
`n`, configuration execution starts at index `n mod 5` in the existing configuration order and
wraps once; one configuration's warmups and samples remain contiguous. Eighteen workloads across
five configurations produce exactly 90 metric rows and 900 raw sample rows. Added to the recorded
schema v5 matrix, schema v6 must therefore contain exactly 490 candidate metric rows and 4,900
raw candidate sample rows; `host-current.csv` receives no row or schema change from this slice.

The timed interval contains only defensive ownership of the prepared entry-reference sequence and
construction of the test-only analytical entry wrappers/history owner that strongly reach the one
prepared current snapshot and all prepared patch pairs. Snapshot, semantic input, patch, inverse,
and revision-chain fixture generation occurs outside timing. Full replay, inverse replay, digest,
aliasing, logical-count, and correctness verification also occurs outside timing. Reported
`allocated_bytes` is current HotSpot test-thread allocation for that wrapper/owner interval only;
it is not the logical retained payload and is not retained heap, ART allocation, Java live heap,
PSS, or physical-device memory evidence.

Candidate schema v6 keeps existing `history_entries`, `change_count`, and
`total_retained_changes`; retained rows use them for `H`, uniform per-entry `C`, and `T`, while
`path_positions` remains zero. Existing v5 snapshot-operation and single-patch storage columns are
left empty for retained rows rather than overloaded. Schema v6 adds:

- `retained_snapshot_primitive_bytes` and `retained_snapshot_reference_slots`;
- `retained_forward_patch_primitive_bytes`, `retained_forward_patch_reference_slots`,
  `retained_forward_patch_object_records`, and
  `retained_forward_patch_primitive_backing_arrays`;
- the same four suffixes under `retained_inverse_additional`, `retained_shared_patch`, and
  `retained_history_patch_union` prefixes; the last name deliberately avoids the existing v5
  single-patch `retained_patch_union` columns;
- `retained_entry_change_counts_digest_sha256`; and
- `retained_history_semantic_digest_sha256`.

The entry-count digest covers the ordered per-entry `C` values, including a tagged empty sequence.
The semantic digest covers canvas, configuration-independent ordered before/after revisions,
positions, colors, affected regions, and final snapshot pixels; its value must match across all
five configurations for one workload. Configuration, snapshot, patch, inverse-policy, result,
timing/allocation, and `correctness_status` fields retain their v5 meanings; retained rows use
`operation_kind=retained_analytical_history` and `result_kind=Retained`.

Every measured sample must prove the exact `H`, `C`, and `T`; canonical row-major ordering and
affected region for every entry; the complete revision chain; exact chronological forward replay
to the retained current snapshot; exact reverse inverse replay to revision 0; unchanged pixels
outside the first `C` positions; unmodified prepared inputs; and deterministic cross-configuration
semantic evidence. It must also prove materialized inverses do not share their forward backing,
directional inverses do share it, and every separate logical storage aggregate equals the sum or
set union defined above. The empty row must have no entry, patch, inverse, or affected region and
zero in every patch-storage field while retaining the revision-0 snapshot payload. Correctness,
matrix-pair, row-count, percentile-recomputation, logical-count, and cross-configuration failures
must all be zero.

All 18 workload points are explicit observations: interpolation between entry or retained-change
candidates is prohibited. Host timing, HotSpot allocation, and logical counts cannot select a
candidate or product limit. Retained heap, ART, PSS, GC, render projection, and frames remain
separate required physical evidence, and a largest passing physical candidate still extends the
matrix before it can be called a maximum.

#### Recorded schema v6 retained analytical history result

The pre-fixed clean host command was run from source commit
`661b0f663937a4ff0a8967d6be5661088441dbf3`:

```powershell
.\gradlew.bat measureP2RepresentationLimits --rerun-tasks --no-build-cache --no-configuration-cache
```

It completed with `BUILD SUCCESSFUL` and 14 executed tasks. The read-only artifact audit recorded:

| Artifact | Schema | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| `host-current.csv` | `nene-pixel-p2-representation-limits-host-current-v4` | 48,942 | `8CBC96524FEFA681A6593F2086FA85211EA1D74EB57FB9670C4A3EE370668145` |
| `host-candidates.csv` | `nene-pixel-p2-representation-limits-host-candidates-v6` | 5,723,100 | `71F1941C643B49667DF2B17C33C8EF51FCBBA0CB1A83C9589ED63F73C08F439B` |

Candidate schema v6 contains exactly 490 metric rows and 4,900 raw sample rows. The retained slice
accounts for 90 metrics and 900 samples: all 18 valid workloads occur once under each of the five
configurations and every metric has exactly ten samples. Across all 490 candidate metrics, an
independent nearest-rank median/p95/p99 recomputation for both latency and allocation found zero
mismatches. Metric/sample adjacency, sample indices, sample counts, and matrix membership also
found zero mismatches.

For retained rows, the audit found zero detailed correctness, storage, or digest failures. This
includes exact `H/C/T`, revision-chain, chronological forward and reverse-inverse replay,
row-major ordering, affected-region and unaffected-pixel checks, prepared-input aliasing rules,
separate logical storage aggregates, SHA-256 field format, and cross-configuration entry-count and
semantic digest equality. The existing schema v5 single-patch storage columns were empty in every
retained row, with zero violations. Empty-case, result/rejection, `correctness_status`, and negative
raw-value checks also had zero failures.

The following representative values are the retained history patch union at `H=64`. They exclude
the separately retained current snapshot and preserve each logical storage unit without conversion:

| Patch/inverse policy | `T` | Primitive payload bytes | Reference slots | Object records | Primitive backing arrays |
| --- | ---: | ---: | ---: | ---: | ---: |
| object/materialized | `N` (65,536) | 524,288 | 393,216 | 131,072 | 0 |
| packed/shared | `N` (65,536) | 786,432 | 0 | 0 | 192 |
| object/materialized | `8N` (524,288) | 4,194,304 | 3,145,728 | 1,048,576 | 0 |
| packed/shared | `8N` (524,288) | 6,291,456 | 0 | 0 | 192 |

The flat-packed and three tiled packed configurations have identical retained patch aggregates at
the same `H/T`: their directional inverse shares the forward patch's three primitive arrays per
entry, so the union counts those arrays and payload once. Their snapshot units remain distinct:

| Snapshot configuration | Snapshot primitive bytes | Snapshot reference slots |
| --- | ---: | ---: |
| object | 0 | 65,536 |
| flat packed | 262,144 | 0 |
| tiled packed, edge 16 | 262,144 | 256 |
| tiled packed, edge 32 | 262,144 | 64 |
| tiled packed, edge 64 | 262,144 | 16 |

The canonical `H=0, T=0` row has zero forward, inverse-additional, shared, and union patch counts,
while retaining the revision-0 snapshot with the corresponding counts above. Snapshot payload is
never added to the patch union.

Observed wrapper/owner-interval p95 allocation was independent of observed `T` at each `H`:

| Retained entries `H` | 0 | 1 | 8 | 16 | 32 | 64 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| p95 allocated bytes | 48 | 96 | 288 | 512 | 960 | 1,856 |

These are current HotSpot test-thread allocation values for defensive entry-reference ownership and
analytical wrapper construction only. They are not retained heap, ART live heap, PSS, or any other
physical-memory measurement. The completed logical retained matrix therefore does not select a
candidate, hard limit, or semantic policy and does not close the physical evidence blocker.

### Pre-fixed sparse/rectangular candidate-native patch slice

Before collecting candidate schema v7, the next host-only slice extends the standalone
candidate-native patch lifecycle evidence to the area-equivalent square, wide, and tall shapes
already exercised by the generic candidate matrix. It remains entirely inside test source and does
not implement or change production `ChangeSet`, `CommandGateway`, `HistoryEntry`, public API, or
command behavior.

The existing generic candidate `apply_*` rows already use a candidate-native patch prepared before
timing and measure only typed validation plus forward snapshot apply. For the six shapes and seven
workloads below, those 210 rows are the canonical standalone forward-apply timing evidence and must
not be measured again. The schema v4 standalone slice is different: it records all six lifecycle
operations, but only at the dense 256x256 anchor. That historical anchor remains unchanged and is
excluded from the new shape list rather than duplicated.

The new shape matrix contains two area classes and three aspect-ratio variants per class:

| Area class | Square | Tall | Wide | Pixels per shape |
| --- | --- | --- | --- | ---: |
| 4K | 64x64 | 16x256 | 256x16 | 4,096 |
| 16K | 128x128 | 64x256 | 256x64 | 16,384 |

Each shape uses all seven existing workload meanings. `C` is the exact canonical change count for
that shape; no workload is inferred from another:

| `native_patch_workload_kind` | Canonical position set | `C` |
| --- | --- | ---: |
| `one_pixel` | the existing single-position workload | 1 |
| `diagonal` | main-diagonal positions | `min(width, height)` |
| `full_row` | the existing complete middle row | `width` |
| `full_column` | the existing complete middle column | `height` |
| `quarter_serpentine` | the existing 25% serpentine set, canonicalized | `N / 4` |
| `half_serpentine` | the existing 50% serpentine set, canonicalized | `N / 2` |
| `full_canvas_serpentine` | the complete canvas set, canonicalized | `N` |

For every new standalone fixture, deterministic high-entropy RGBA before/after values reuse the
existing candidate semantic fixture. The canonical workload set is reversed before strict patch
creation, and the resulting candidate-native forward patch must be row-major with the exact `C`.
The existing generic forward-apply fixture may reach the same factory through its existing path
order because all factory work is outside its timed interval; v7 must prove outside timing that its
canonical patch is identical to the reverse-input standalone patch before reusing those rows.

Together, the sparse/rectangular evidence has the same six operation meanings as the dense anchor,
but only five operations add new timed rows:

| Operation | Evidence source | Timed interval |
| --- | --- | --- |
| shuffled patch create | new schema v7 row | shared order canonicalization, candidate-native record/array materialization, and defensive ownership |
| inverse create | new schema v7 row | materialized object-record inverse or packed directional inverse-view creation |
| forward apply | existing generic `apply_*` row | prepared canonical patch validation and forward snapshot apply only |
| inverse apply | new schema v7 row | prepared canonical inverse validation and inverse snapshot apply |
| exact round trip | new schema v7 row | forward apply, inverse creation, and inverse apply |
| final-record late conflict | new schema v7 row | typed validation through the final canonical changed-position mismatch and rejection return |

The round-trip interval includes a forward apply, but it is one composite lifecycle operation. It
neither replaces nor duplicates the existing standalone forward-apply boundary. The conflict
fixture changes the final position of the canonical patch before timing; rejection must scan to
that record, return `BeforeValueMismatch`, leave every source pixel and the source revision
unchanged, and materialize no output snapshot.

Semantic input, snapshots, position sets, expected values, and conflict fixtures are generated
outside timing. For inverse-create timing the forward patch is prepared outside timing. For
inverse-apply timing both the canonical forward patch and its inverse are prepared outside timing.
For round-trip timing, inverse creation remains inside the composite interval. Full correctness and
logical-storage verification occur after every warmup and sample, outside the recorded interval.

The new matrix has six shapes, seven workloads, five new operations, and five configurations. Each
combination has five warmups and ten diagnostic samples. In table order, the zero-based linear
work index is `((shape_index * 7) + workload_index) * 5 + operation_index`, where
`operation_index` follows the five new-operation order after omitting forward apply from the table
above. Configuration execution starts at `work_index mod 5` in the existing configuration order
and wraps once. One configuration's warmups and ten samples remain contiguous. The new slice
therefore adds exactly 1,050 metric rows and 10,500 raw sample rows. The 210 reused forward-apply
metrics and 2,100 samples remain part of the existing schema v6 base rather than being copied.
Starting from the recorded 490 metrics and 4,900 samples, candidate schema v7 must contain exactly
1,540 metric rows and 15,400 raw sample rows. `host-current.csv` receives no schema or row change.

Schema v7 adds only `native_patch_workload_kind`. Existing dense standalone patch rows use
`dense_full_canvas_anchor`; new standalone rows use one of the seven workload IDs in the workload
table; generic candidate apply rows and every other row leave it empty. Existing operation, shape,
path, storage, lifecycle, result, digest, timing, and correctness columns retain their v6 meanings.
The report and contract tests must reject a missing or duplicate shape/workload/operation/
configuration pair and must prove the exact new and total metric/sample counts.

Every new sample must additionally prove the reverse create input. Every new sample, and every
reused forward-apply sample under the shared correctness audit, must prove exact shape and `C`;
canonical row-major patch order; minimal exact affected region; complete affected and unaffected
pixels; `0 -> 1 -> 0` lifecycle revisions; forward/inverse position and before/after symmetry;
operation-specific state change; atomic typed final-record conflict where applicable;
deterministic logical storage; and canonical-order, patch, inverse, lifecycle, operation-state, and
unaffected-pixel digests. Equivalent workload digests and result evidence must agree across all
five configurations. Fixture data and prepared patches must remain unmodified and must obey the
materialized-versus-shared inverse aliasing contract.

Snapshot storage, forward patch, inverse-additional, shared patch, and retained patch union remain
separate logical units. Object/materialized counts and packed/shared counts use their existing
storage rules; overlapping packed forward/shared payload is counted once in the union. Primitive
bytes, reference slots, object records, and primitive arrays are not added to one another or
converted into heap estimates. Reported latency and allocation are HotSpot test-thread
observations, not retained heap, ART, PSS, GC, render, frame, or physical-device evidence.

All matrix points are explicit observations and interpolation is prohibited. This slice cannot
select a representation, semantic policy, canvas limit, patch limit, or product hard limit. It
does not change the recorded schema v4, v5, or v6 results and does not close any physical evidence
requirement.

### Recorded schema v7 sparse/rectangular candidate-native patch result

The pre-fixed clean host command was run from source commit
`97f0e959d60bb6f3c21dcfb2344c69b55ab3c327`:

```powershell
.\gradlew.bat measureP2RepresentationLimits --rerun-tasks --no-build-cache --no-configuration-cache
```

It completed with `BUILD SUCCESSFUL` in 2 minutes 58 seconds and 14 executed tasks. The exact
generated artifacts were:

| Artifact | Schema | Bytes | SHA-256 | Metrics / samples |
| --- | --- | ---: | --- | ---: |
| `host-current.csv` | `nene-pixel-p2-representation-limits-host-current-v4` | 48,945 | `872872F52B3EDDD87C846024AF5CE6A08BD33B6813ECF95DD09482DF3A7B0F48` | 21 / 174 |
| `host-candidates.csv` | `nene-pixel-p2-representation-limits-host-candidates-v7` | 26,168,308 | `22443C091625A7589AE4D664F92D489A854A40D2A999869927D69177D4C6525C` | 1,540 / 15,400 |

An independent read-only CSV audit found zero candidate metric-block, ten-sample index, or
nearest-rank median/p95/p99 mismatches for latency and allocation across all 1,540 metrics. The
current-v4 artifact also had zero percentile mismatches across its 21 metrics and 174 samples.
Candidate `correctness_status` failures were zero.

The native patch subset contained exactly 1,080 unique
shape/workload/operation/configuration metrics: 30 historical dense-anchor metrics plus 1,050 new
sparse/rectangular metrics. Removing configuration produced the expected 216 complete groups.
Exact semantic, canonical order, minimal region, affected/unaffected pixel, revision, inverse,
typed conflict, logical-storage, execution-rotation, digest, and cross-configuration audits all
reported zero mismatches. Missing and duplicate matrix pairs were zero.

Only the five historical dense-anchor `patch_apply_forward` metrics remain standalone forward
rows. The six sparse/rectangular shapes reuse exactly 210 generic forward-apply metrics in 42
shape/workload groups across five configurations; their shared correctness audit had zero
mismatches. No sparse/rectangular standalone forward row was added, so the generic forward timing
boundary remains canonical and is not duplicated. Non-native `native_patch_workload_kind`
violations were zero.

The following diagnostic timing/allocation rows are direct observations from candidate-v7. Each
p99 is the maximum of ten host samples; these values are not ranking or limit evidence:

| Boundary | Configuration | Shape / workload / `C` | Latency median / p95 / p99 (ns) | Allocation median / p95 / p99 (bytes) |
| --- | --- | --- | ---: | ---: |
| generic forward apply | flat packed/shared | 64x64 / one pixel / 1 | 10,600 / 27,100 / 27,100 | 16,552 / 16,552 / 16,552 |
| shuffled create | object/materialized | 64x64 / one pixel / 1 | 1,300 / 2,200 / 2,200 | 584 / 584 / 584 |
| final-record late conflict | tiled T32 packed/shared | 64x256 / full column / 256 | 4,300 / 5,000 / 5,000 | 56 / 56 / 56 |
| generic forward apply | flat packed/shared | 256x64 / full canvas / 16,384 | 87,300 / 140,600 / 140,600 | 65,704 / 65,704 / 65,704 |
| exact round trip | object/materialized | 256x64 / full canvas / 16,384 | 403,500 / 474,500 / 474,500 | 721,536 / 721,536 / 721,536 |
| exact round trip | flat packed/shared | 256x64 / full canvas / 16,384 | 189,400 / 273,600 / 273,600 | 131,544 / 131,544 / 131,544 |

Representative logical-storage rows below use `B/R/O/A` for primitive payload bytes, reference
slots, object records, and primitive backing arrays. Snapshot `B/R` is a separate unit and is
never added to a patch aggregate:

| Patch/inverse policy and `C` | Snapshot `B/R` | Forward `B/R/O/A` | Inverse-additional `B/R/O/A` | Shared `B/R/O/A` | Retained union `B/R/O/A` |
| --- | ---: | ---: | ---: | ---: | ---: |
| object/materialized, 1 | 0 / 4,096 | 4 / 3 / 1 / 0 | 4 / 3 / 1 / 0 | 0 / 0 / 0 / 0 | 8 / 6 / 2 / 0 |
| flat packed/shared, 1 | 16,384 / 0 | 12 / 0 / 0 / 3 | 0 / 0 / 0 / 0 | 12 / 0 / 0 / 3 | 12 / 0 / 0 / 3 |
| object/materialized, 16,384 | 0 / 16,384 | 65,536 / 49,152 / 16,384 / 0 | 65,536 / 49,152 / 16,384 / 0 | 0 / 0 / 0 / 0 | 131,072 / 98,304 / 32,768 / 0 |
| flat packed/shared, 16,384 | 65,536 / 0 | 196,608 / 0 / 0 / 3 | 0 / 0 / 0 / 0 | 196,608 / 0 / 0 / 3 | 196,608 / 0 / 0 / 3 |

These counts preserve the object/materialized and packed/shared inverse policies: the object
inverse is additional storage, while the packed directional inverse shares the forward backing
and the retained union counts it once. They are logical accounting units, not HotSpot heap, ART
live heap, PSS, GC, render, frame, or physical-device measurements.

The focused source gate was also run from the recorded source:

```powershell
.\gradlew.bat :core:pixel-engine:test :core:pixel-engine:ktlintCheck :core:pixel-engine:detekt --rerun-tasks --no-build-cache --no-configuration-cache
```

It completed with `BUILD SUCCESSFUL` in 2 minutes 55 seconds and 21 executed tasks. The source
commit changed test source only; it did not change production behavior, public API, dependencies,
or Gradle configuration. This recorded host result selects no representation, semantic policy,
canvas or patch hard limit, and does not close the physical evidence blocker.

### Pre-fixed duplicate raw-path amplification schema v8 slice

Before collecting candidate schema v8, this host-only slice isolates raw-position amplification
from unique effective changes. It extends only the candidate-native changed-color duplicate path;
the three existing reference-clear changed/no-op and same-color no-op rows remain fixed at
`256x256` and are neither relabeled nor multiplied.

The duplicate matrix uses the seven shared test-only shapes already fixed for snapshot, patch, and
projection analysis:

| Pixel count | Shapes |
| ---: | --- |
| 4,096 | `64x64`, `16x256`, `256x16` |
| 16,384 | `128x128`, `64x256`, `256x64` |
| 65,536 | `256x256` |

For each shape with `N` pixels, factors `F = 1, 2, 4, 8` produce exactly `P = F*N` raw positions,
`U = N` unique positions, `D = P-N` duplicate positions, zero unchanged unique positions, and
`C = N` effective changes from opaque black to opaque red. Raw position `i` is row-major logical
position `i/F`, so duplicates are adjacent runs. The fixed input-order identities are `row_major`,
`paired_row_major`, `quadrupled_row_major`, and `octupled_row_major`. These observations must not be
generalized to another duplicate distribution.

All five candidate configurations run at every shape/factor point with five warmups and ten raw
samples. This produces 140 duplicate metrics and 1,400 duplicate samples. The already-recorded
`256x256`, factor-two descriptor remains one of those 140 metrics with the same identity and is not
emitted a second time. The other three legacy raw operations contribute 15 unchanged metrics, so
the raw-path portion contains 155 metrics and 1,550 samples. Added to schema v7, schema v8 must
contain exactly 1,675 metric rows and 16,750 sample rows; `host-current.csv` receives no schema or
row change.

No report column is added. `path_positions`, `unique_path_positions`,
`duplicate_path_positions`, and `change_count` already identify `P`, `U`, `D`, and `C`. The
candidate schema identifier and exact total-count metadata advance to v8. Matrix keys include
operation, shape, all four counts, and configuration. Configuration rotation uses
`shapeIndex*4 + factorIndex` modulo five for duplicate workloads; this preserves the existing
`256x256` factor-two start. The three legacy operations preserve their existing offsets.

Every sample retains the current timed boundary: valid-input scan, first-occurrence collapse,
source-color filter, canonical change collection, candidate-native patch materialization,
defensive ownership, and typed result. Fixture and digest construction, apply/inverse replay, full
pixel verification, and report writing remain outside timing. `P` multiplication must be exact
for the fixed matrix before creating the raw `IntArray`; the largest fixture is 524,288 positions.

Within one shape/configuration, factors may differ only in raw-input identity and measured
latency/allocation. Canonical change/order, forward/inverse patch, lifecycle, full-canvas region,
storage, and final pixels must agree across factors. Within one shape/factor, raw and semantic
digests, state, result, and correctness must agree across configurations while layout-specific
storage may differ. Every rasterized result must preserve source revision/pixels, apply `0 -> 1`,
inverse to `0`, change all `N` positions, leave zero unaffected positions, and round-trip exactly.

Factor one cannot reuse `raw_reference_clear_changed`: that legacy workload is opaque red to the
black reference fixture, while this factor matrix is opaque black to red and therefore has distinct
raw, canonical, forward, and inverse digests. A shared test-only shape matrix must be reused or
extracted instead of introducing a third shape list.

This slice may change pixel-engine test source and candidate report metadata only. It adds no
production behavior, API, dependency, Gradle wiring, report column, worker-heap change, product
limit, or accepted ADR answer. HotSpot timing/allocation and logical storage do not select a
candidate or satisfy retained heap, ART, PSS, render, frame, or physical-device evidence.

#### Recorded duplicate raw-path amplification schema v8 result

The test-only matrix and report implementation were recorded at source commit
`48db02f4170c7aa0bd7e94ec4db096161d91a6be`. From that committed source, the complete host
aggregation was rerun without build or configuration cache:

```powershell
.\gradlew.bat measureP2RepresentationLimits --rerun-tasks --no-build-cache --no-configuration-cache
```

It completed with `BUILD SUCCESSFUL` in 3 minutes 29 seconds and 29 executed tasks. The exact
generated host artifacts were:

| Artifact | Schema | Bytes | SHA-256 | Metadata / metrics / samples |
| --- | --- | ---: | --- | ---: |
| `host-current.csv` | `nene-pixel-p2-representation-limits-host-current-v4` | 48,946 | `AFE0F95DA771786CB2687C59912152CAAD77DCDA8EDADA4597C3887687CE4E06` | 8 / 21 / 174 |
| `host-candidates.csv` | `nene-pixel-p2-representation-limits-host-candidates-v8` | 29,226,423 | `C09A5BB3DF4FFECE500FD985733AD1E417756DDC05D0F1C21435A18306339C31` | 25 / 1,675 / 16,750 |
| `host-projection.csv` | `nene-pixel-p2-representation-limits-host-projection-v1` | 165,648 | `9A88481D715EFCCE18B0822D18F5AE912EB1DF44B5649291F64D24ECE81D9A51` | 19 / 28 / 280 |

An independent read-only candidate CSV audit found zero metric-identity, ten-sample index, or
nearest-rank median/p95/p99 mismatches for latency and allocation across all 1,675 metrics. It also
found zero correctness failures, missing sample groups, or duplicate metric identities. The raw
subset contained exactly 155 metrics and 1,550 samples: 140 unique duplicate
shape/factor/configuration keys and the unchanged 15 legacy metrics. The report added no factor
column.

All 28 duplicate shape/factor groups contained the five expected configurations in the pre-fixed
rotation. Exact `P/U/D/C`, input-order identity, legacy-operation counts, and the seven-shape shared
matrix all matched the schema. Within every shape/factor group, semantic, raw, canonical, patch,
lifecycle, result, and final-pixel digests agreed across configurations. Within every
shape/configuration group, all four raw digests were distinct while canonical, patch, lifecycle,
region, logical-storage, result, and final-pixel values were factor-invariant. All of those audits
reported zero mismatches.

The following 256-square changed-color rows are direct diagnostic observations. Each p99 is the
maximum of ten host samples and is not physical tail or limit evidence:

| Configuration | Factor / `P` / `D` | Latency median / p95 / p99 (ns) | Allocation median / p95 / p99 (bytes) |
| --- | ---: | ---: | ---: |
| object/materialized | 1 / 65,536 / 0 | 3,668,800 / 5,192,900 / 5,192,900 | 8,845,192 / 8,845,192 / 8,845,192 |
| object/materialized | 2 / 131,072 / 65,536 | 3,681,700 / 6,930,401 / 6,930,401 | 9,107,336 / 9,107,336 / 9,107,336 |
| object/materialized | 4 / 262,144 / 196,608 | 4,191,900 / 4,585,699 / 4,585,699 | 9,631,624 / 9,631,624 / 9,631,624 |
| object/materialized | 8 / 524,288 / 458,752 | 5,070,100 / 5,430,599 / 5,430,599 | 10,680,200 / 10,680,200 / 10,680,200 |
| flat packed/shared | 1 / 65,536 / 0 | 3,121,200 / 3,294,501 / 3,294,501 | 8,253,312 / 8,253,312 / 8,253,312 |
| flat packed/shared | 2 / 131,072 / 65,536 | 3,325,699 / 3,637,001 / 3,637,001 | 8,515,456 / 8,515,456 / 8,515,456 |
| flat packed/shared | 4 / 262,144 / 196,608 | 3,914,200 / 6,223,400 / 6,223,400 | 9,039,744 / 9,039,744 / 9,039,744 |
| flat packed/shared | 8 / 524,288 / 458,752 | 4,670,100 / 6,196,200 / 6,196,200 | 10,088,320 / 10,088,320 / 10,088,320 |

Logical storage stayed constant across all four factors at `C = 65,536`. In `B/R/O/A` units,
object/materialized recorded snapshot `0/65,536`, forward and inverse-additional each
`262,144/196,608/65,536/0`, no shared storage, and retained union
`524,288/393,216/131,072/0`. Flat packed/shared recorded snapshot `262,144/0`, forward and shared
each `786,432/0/0/3`, no inverse-additional storage, and retained union `786,432/0/0/3`.

The focused source gate was also run from the recorded source:

```powershell
.\gradlew.bat :core:pixel-engine:test :core:pixel-engine:ktlintCheck :core:pixel-engine:detekt --rerun-tasks --no-build-cache --no-configuration-cache
```

It completed with `BUILD SUCCESSFUL` in 3 minutes 21 seconds and 21 executed tasks. This source
commit changed test code and candidate report metadata only; it added no production behavior,
public API, dependency, Gradle wiring, or report column. Adjacent duplicate runs are one fixed
distribution, and these HotSpot observations select no candidate or product limit. Retained heap,
ART, PSS, candidate projection, compositor, and complete physical-device evidence remain pending.

### Pre-fixed current canonical raw-acceptance amplification schema v5 slice

Before collecting current schema v5, the current host report contains one `256x256`, factor-two
duplicate raw-path row only at the larger `CommandGateway.execute` boundary. That row includes
patch application, snapshot creation, materialized inverse construction, and history commit. It
cannot isolate how valid raw input grows before the canonical patch exists. Candidate schema v8
isolates a similar stage for test-only representations, but it cannot substitute for the current
production `Stroke` and `rasterizeStroke` path.

This host-only slice therefore measures one new canonical boundary beginning with
`Stroke.create(canvas, rawPath, opaqueRed)` and continuing through exactly one
`rasterizeStroke(sourceSnapshot, stroke)` call. It includes valid-position containment scanning,
`Stroke` defensive path ownership, first-occurrence duplicate collapse, source-color filtering,
canonical row-major change collection, and `PixelPatch.create` sorting and ownership. Raw fixture,
source snapshot, expected patch, and digest construction are outside timing. Patch apply, inverse
creation and replay, full-pixel verification, report writing, `CommandGateway`, `PixelSurface`,
`ChangeSet`, and history are also outside timing.

The matrix reuses the seven already-fixed logical shapes as an application-test fixture:

| Pixel count | Shapes |
| ---: | --- |
| 4,096 | `64x64`, `16x256`, `256x16` |
| 16,384 | `128x128`, `64x256`, `256x64` |
| 65,536 | `256x256` |

For each shape with `N` pixels, factors `F = 1, 2, 4, 8` produce exactly `P = F*N` raw positions,
`U = N` unique positions, `D = P-N` duplicates, zero unchanged unique positions, and `C = N`
effective opaque-black-to-opaque-red changes. Raw entry `i` is row-major position `i/F`, so each
logical position appears in one adjacent run. Input-order identities remain `row_major`,
`paired_row_major`, `quadrupled_row_major`, and `octupled_row_major`. Workloads are emitted in the
table's shape order and ascending factor order. Exact multiplication must succeed before creating
the raw list; the largest fixed input is 524,288 positions.

The 28 workloads each use five warmups and ten raw samples, adding 28 metrics and 280 samples to
the existing 21 metrics and 174 samples. `host-current.csv` must therefore advance from schema
`nene-pixel-p2-representation-limits-host-current-v4` to `-v5` with exactly 49 metric rows and 454
sample rows. The metric name is `p2_current_raw_acceptance_duplicate_changed`. No report column is
added: factor, `U`, and `D` are recovered from the fixed protocol plus existing canvas,
`pixel_count`, `path_positions`, and `change_count` columns. Metadata must record the isolated
boundary, matrix, raw subtotal, exact report total, exclusions, and fixed ordering. Existing metric
identities, sample plans, analysis rows, and candidate/projection schemas remain unchanged.

Every warmup and sample must return the exact typed `Rasterized` result; leave source revision and
pixels unchanged; produce the expected `0 -> 1` patch with `C = N` and full-canvas affected region;
apply to exact opaque red; and inverse-replay to the original revision-zero opaque-black snapshot.
The source, raw fixture, expected patch, applied snapshot, and restored snapshot must agree before
and after sampling. Within one shape, all factors must have distinct raw-input digests while the
canonical patch, inverse, region, applied state, and restored state agree exactly. Missing or
duplicate shape/factor pairs, an untyped exception, a no-op/rejection, or any semantic mismatch
must fail measurement instead of producing a fast row.

This is a valid-input acceptance measurement, not a rejection or supported-limit test. It does not
replace the existing full-command duplicate row or the outside-first `Stroke.create`
characterization. Adjacent duplicate runs are one fixed distribution, and the largest passing
fixture does not establish a raw-path cap. This slice may change application test source and
current-report metadata only. It adds no production behavior, API, dependency, Gradle wiring,
report column, candidate conversion, product limit, or accepted ADR answer. HotSpot timing and
current-thread allocation do not satisfy ART, retained heap, PSS, render, frame, compositor, or
physical-device evidence.

#### Recorded current canonical raw-acceptance schema v5 result

The test-only current-path matrix and report implementation were recorded at source commit
`fde94f76e27aa4a87a3b64086da700e8be344579`. From that committed source, the complete host
aggregation was rerun without build or configuration cache:

```powershell
.\gradlew.bat measureP2RepresentationLimits --rerun-tasks --no-build-cache --no-configuration-cache
```

It completed with `BUILD SUCCESSFUL` in 3 minutes 38 seconds and 29 executed tasks. The exact
generated host artifacts were:

| Artifact | Schema | Bytes | SHA-256 | Metadata / metrics / samples |
| --- | --- | ---: | --- | ---: |
| `host-current.csv` | `nene-pixel-p2-representation-limits-host-current-v5` | 159,281 | `3C8690E061AA9E56C6452789431CB1260516353E08D38700EBE5489E44A1E0D5` | 13 / 49 / 454, plus 6 analysis rows |
| `host-candidates.csv` | `nene-pixel-p2-representation-limits-host-candidates-v8` | 29,226,396 | `4DF70289D142C5982FFE75F9037A6D4EF84DD355E4708306C763DA1E8768792E` | 25 / 1,675 / 16,750 |
| `host-projection.csv` | `nene-pixel-p2-representation-limits-host-projection-v1` | 165,646 | `C45EA3D8E917F0A62E8B675DFFF7F1D145539E906DF3AC49FE8DD35247F97B33` | 19 / 28 / 280 |

An independent read-only current CSV audit reconstructed all 49 metric identities and joined each
to its declared seven- or ten-sample block. It found zero missing groups, duplicate identities,
sample-count or index mismatches, or nearest-rank median/p95/p99 mismatches for latency and
allocation. The existing subset remained 21 metrics and 174 samples before the appended new
subset. Candidate v8 and projection v1 retained their exact schema and row counts.

The new subset contained exactly 28 metrics and 280 samples with 28 unique shape/factor keys in the
fixed shape-major, ascending-factor order. Existing columns recovered `F = P/N`; every row had
exact canvas area `N`, `P = F*N`, `C = N`, zero history entries and retained changes, five warmups,
ten samples, and the exact isolated boundary. Metadata recorded the matrix, order, subtotal, report
total, boundary, and exclusions. No report column was added.

The fixture contract and every measured operation required a typed `Rasterized` result equal to the
prebuilt canonical patch, inverse, full region, applied red snapshot, and restored revision-zero
black snapshot. Source and raw fixture digests remained unchanged. All four raw digests were
distinct within each shape while patch, inverse, region, applied state, and restored state agreed
across factors. Missing or duplicate descriptors were rejected by contract tests. These semantic
and factor-invariance checks completed without failure.

The following 256-square rows are direct diagnostic observations. Each p99 is the maximum of ten
host samples and is not physical tail or limit evidence:

| Factor / `P` / `D` | Latency median / p95 / p99 (ns) | Allocation median / p95 / p99 (bytes) |
| ---: | ---: | ---: |
| 1 / 65,536 / 0 | 5,709,600 / 20,339,500 / 20,339,500 | 11,098,760 / 11,098,760 / 11,098,760 |
| 2 / 131,072 / 65,536 | 7,038,700 / 22,980,000 / 22,980,000 | 11,360,904 / 11,360,904 / 11,360,904 |
| 4 / 262,144 / 196,608 | 10,556,900 / 18,979,500 / 18,979,500 | 11,885,192 / 11,885,192 / 11,885,192 |
| 8 / 524,288 / 458,752 | 16,999,700 / 34,657,800 / 34,657,800 | 12,933,768 / 12,933,768 / 12,933,768 |

The focused source gate was:

```powershell
.\gradlew.bat :core:application:test :core:application:ktlintCheck :core:application:detekt --rerun-tasks --no-build-cache --no-configuration-cache
```

It completed with `BUILD SUCCESSFUL` in 34 seconds and 23 executed tasks. This source commit changed
application test code and current-report metadata only; it changed no production behavior, public
API, dependency, plugin, module, Gradle wiring, report column, candidate schema, or projection
schema. Adjacent duplicate runs remain one fixed host distribution. The result selects no raw cap,
candidate, or product limit and does not close ART, retained heap, PSS, render, frame, compositor,
or physical-device evidence.

## Auxiliary Android harness proof

The Android command harness compiled and ran on the `Pixel_8_Pro_API_35` AVD only after the
runner argument `nene.p2.allowEmulatorAuxiliary=true` was supplied. Without that argument, the
same emulator was rejected with `IllegalStateException` before measurement. The raw CSV records:

| Item | Recorded value |
| --- | --- |
| Schema | `nene-pixel-p2-android-command-measurement-v1` |
| Evidence class | `auxiliary_emulator` |
| Profile ID | `AUXILIARY-EMULATOR-PIXEL-API` |
| Device | Google `sdk_gphone64_x86_64`, Android 15 / API 35, `ranchu` |
| Emulator signals | `ro.kernel.qemu=1`, `ro.boot.qemu=1`, build/model/product signals |
| Runtime heap | 201,326,592-byte max; 192 MiB memory class |
| Protocol | one warmup, two samples, 5 workloads x 3 canvas sizes |
| Rows | one pre-workload post-GC baseline plus 30 sample rows |
| Retained boundary | current gateway, document, and one-level history remain strongly reachable through post-GC Java heap/PSS capture |
| On-device raw path | `files/p2-measurements/p2-android-command-measurement.csv` in the target app |
| Raw bytes / SHA-256 | 16,677 / `6042A4C0244336598C067FD1CBFDF6C4EE064ED063C0F606762254CF5E1AA46C` |

The auxiliary run asserted exact state, revision, history, ChangeSet revision, and typed no-op
behavior for sparse apply, dense apply, same-color no-op, undo, and redo at 16, 64, and 256.
It also proved the ART runtime-stat, post-GC Java heap, and `Debug.MemoryInfo` PSS fields can be
collected without granting emulator results physical-evidence status. It has too few samples,
includes no frame profile, and runs on virtualized x86_64 hardware, so none of its timings or
memory readings select a product representation or limit.

The managed smoke route is reproducible with the following PowerShell command. Each Gradle
property is quoted because PowerShell otherwise splits property names containing dots:

```powershell
.\gradlew.bat :app:android:connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=io.github.hideyukimori.nenepixel.measurement.P2AndroidCommandMeasurementTest" `
  "-Pandroid.testInstrumentationRunnerArguments.nene.p2.physicalProfileId=AUXILIARY-EMULATOR-PIXEL-API" `
  "-Pandroid.testInstrumentationRunnerArguments.nene.p2.allowEmulatorAuxiliary=true" `
  "-Pandroid.testInstrumentationRunnerArguments.nene.p2.warmupIterations=1" `
  "-Pandroid.testInstrumentationRunnerArguments.nene.p2.sampleCount=2"
```

For physical route screening, omit `nene.p2.allowEmulatorAuxiliary`, use the completed stable
physical profile ID, and retain the default five warmups and twenty samples. Final decision
evidence instead uses the expanded protocol fixed below. The harness rejects an emulator before
measurement unless the auxiliary flag is explicitly true. The managed connected-test task can uninstall its packages
after execution, so the on-device CSV must be copied before teardown when collecting the final
raw artifact set; its byte length and SHA-256 are then recorded in this ledger.

## Preliminary physical Android command screening

The command harness was run after the physical profile below was fixed, with test code unchanged
from commit `5b9675a5a77186c9ce5e5095bc88b5a485ad18fc` and the profile-recording document at
commit `5922535634033954bbb10319c6e63c019e48d799`. The app and test APKs were assembled, installed,
and invoked with the physical device selected explicitly. The raw device file and host copy had
identical byte lengths and SHA-256.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-core.csv` | 123,032 | `9292794F58CDDE42CF46E6AF733E7A524312C4045369BBBFA25C24ECB68E0645` |
| `app/android/build/outputs/apk/debug/android-debug.apk` | 11,698,623 | `E263F56E483541097206FCB0BB5004DEB3C9A833867CAB1429FDCC83F50EE7DF` |
| `app/android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk` | 1,211,189 | `51431D89A30DBDC76B5C38BE88BAA13CFEFDEBECED6E8EFF990EE9795E893025` |

The run used five warmups and twenty samples for each of five workloads at 16, 64, and 256,
for 300 measured samples. All exact state, revision, history, ChangeSet, and typed no-op
assertions passed. Twenty samples give a useful screening result, but p99 is the observed maximum
and is not final tail evidence. No representation or hard limit is selected from this run.

| 256-square workload | p95 latency | p99 latency | p95 ART allocation |
| --- | ---: | ---: | ---: |
| sparse apply | 21.639 ms | 21.987 ms | 6.594 MiB |
| dense apply | 120.381 ms | 121.785 ms | 20.156 MiB |
| dense same-color no-op | 35.879 ms | 45.864 ms | 5.141 MiB |
| undo | 34.776 ms | 37.276 ms | 8.344 MiB |
| redo | 34.293 ms | 42.377 ms | 8.344 MiB |

Every 16- and 64-square workload passed the command-latency screening targets. Every 256-square
workload failed p95 <= 8.0 ms and p99 <= 16.67 ms. Blocking-GC count remained zero for all
300 measured operations. Non-blocking GC occurred in 42 samples, including every 256-square
dense apply sample, with 79 collections and 2,548 ms total runtime-stat GC time.

The process baseline was 2,057,536 bytes post-GC Java heap and 89,903 KiB total PSS. Across
command samples, maximum post-GC heap was 7,174,000 bytes and total PSS ranged from 84,568 to
90,595 KiB. These command-only readings are numerically below the heap/PSS headroom targets, but
they exclude render projection and retained analytical history and therefore are not a formal
pass. Maximum heap minus baseline was 5,116,464 bytes, above the 2,684,355-byte churn threshold;
the required cap-rejection plus ten-cycle workload was not executed, so this is a signal for the
next matrix rather than a formal churn failure.

This screening does not cover rectangular and partial-density workloads, raw duplicate paths,
standalone patch boundaries, conflicts, limit rejection, multi-entry analytical retention,
renderer projection, frames, or packed representation candidates. Final tail collection will
use a larger pre-declared sample protocol, and PSS will use five independent invocation
checkpoints.

## Physical Android affected-frame result

Commit `25915073c76c6648706e10df3ed9ad9f7e3270b6` adds the separately named
`P2AndroidFrameMeasurementTest` and schema `nene-pixel-p2-android-frame-measurement-v1`.
The route compiles, passes app/presentation lint, detekt, and ktlint, and assembles the debug app
and test APKs.

The first physical invocation started at 2026-09-01 21:01:41 JST with thermal status 1, USB
power, an awake display, power saving disabled, and active 1200 x 1920 at 90 Hz. It stopped during
warmup generation 1 before any measured sample or environment checkpoint row because the test
thread entered its private condition wait before the Compose test clock had driven the published
state through recomposition and draw. The exact generation therefore timed out after 10 seconds.
No frame CSV was written and this attempt is invalid rather than performance evidence.

Commit `00355ef283624417416a4c2999cc554d1a323f18` fixes the orchestration without weakening frame
identity: it drives Compose to idle after publishing each measured generation, retains the Android
contractual `View.getDrawingTime()` to `FrameMetrics.VSYNC_TIMESTAMP` match, searches all exact
markers for the generation after the per-arm buffer baseline, and requires monotonically
increasing generations. Timeout diagnostics now retain marker/frame counts, nearest VSYNC delta,
both VSYNC timestamp forms, and dropped-report count. The corrected route is compiled and quality
checked.

The corrected invocation ran after a 2026-09-01 21:12:13 JST preflight and completed in 139.951
seconds with `OK (1 test)`. It used five warmups and 200 measured samples. The CSV reports ten
environment checkpoints: before samples, after every 25 samples, and after the batch. Every
checkpoint retained display mode 1 at 1200 x 1920 and 90 Hz, thermal status 1, power saving
disabled, an interactive display, USB power, and 89% battery. A post-run device query retained
thermal status 1, USB power, power saving disabled, and an awake display.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-frames.csv` | 123,670 | `27B0A3A5010B2E79796B42D0468AAFD5C6FF06B500D996FD1F63ABAC24E9E369` |
| `app/android/build/outputs/apk/debug/android-debug.apk` | 11,664,182 | `CD655DF7F6CE61821D6C485C5C0163153E81EF5AE4C639722EE022EA4E51C88F` |
| `app/android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk` | 1,216,157 | `9907F22061A8EF92CB4A4D2917157BCCD1495C155B14D9608AC3A2CE6335418E` |

The collection is valid renderer evidence but the current 256-square dense route fails the
pre-fixed affected-frame conditions:

| Observation over 200 nearest-rank samples | Result | Pre-fixed target | Outcome |
| --- | ---: | ---: | --- |
| Total affected-frame duration p95 | 204.600 ms | p95 meets the per-frame deadline | fail; 0 / 200 met the recorded 10.000 ms deadline |
| Affected-frame overrun p99 | 199.535 ms | <= 16.67 ms | fail |
| Command start to first exact app-issued frame p95 | 303.046 ms | <= 33.33 ms visible correctness | numerically fail and not compositor proof |

All 200 samples had exact render state, matching revision and snapshot hash, zero image mismatch
across all 65,536 logical pixels, a positive and unique API 36 frame-timeline vsync ID, and zero
FrameMetrics report drops. The direct command-to-frame value is specifically a test-clock-
synchronized app-issued latency because the Compose test rule drives recomposition to idle; it is
not claimed as natural production scheduling or physical SurfaceFlinger presentation latency.

The route uses a debug-only presentation bridge that delegates to the canonical `EditorScreen`
and `PixelCanvas`; it is absent from release compilation and adds no release API or dependency.
The test runs in the real `MainActivity` window, copies `FrameMetrics` immediately on its callback
thread, correlates exact state/draw generations, and verifies all logical pixel centers through
Compose image capture outside the timed interval. It records thermal, display, power-save,
interactive, USB-power, and battery checkpoints before samples, every 25 samples, and after the
batch. Missing required timing values, callback drops, thermal status above 1, display changes,
power-save mode, a non-interactive display, or loss of USB power invalidates the run.

The recorded physical invocation was:

```powershell
.\gradlew.bat :app:android:assembleDebug :app:android:assembleDebugAndroidTest --rerun-tasks --no-build-cache
adb -s <PHYSICAL_DEVICE_SERIAL> install -r -t app/android/build/outputs/apk/debug/android-debug.apk
adb -s <PHYSICAL_DEVICE_SERIAL> install -r -t app/android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk
adb -s <PHYSICAL_DEVICE_SERIAL> shell am instrument -w -r `
  -e class io.github.hideyukimori.nenepixel.measurement.P2AndroidFrameMeasurementTest `
  -e nene.p2.physicalProfileId NENE-P2-ALLDOCUBE-IPL80MP-A16-API36 `
  -e nene.p2.candidateId current-flat-dense-256-square `
  -e nene.p2.runIndex 1 `
  -e nene.p2.sourceCommit 00355ef283624417416a4c2999cc554d1a323f18 `
  -e nene.p2.frameWarmupIterations 5 `
  -e nene.p2.frameSampleCount 200 `
  io.github.hideyukimori.nenepixel.test/androidx.test.runner.AndroidJUnitRunner
```

The defaults are five warmups and 200 measured frames. The device CSV path is
`files/p2-measurements/p2-android-frame-measurement.csv`; it is copied to the planned
`device-frames.csv` host path and checksummed before teardown.

## Starting implementation evidence

This inventory names the current path that candidate measurements must compare and that a later
migration must either retain explicitly or replace atomically.

| Concern | Current evidence | Consequence to measure or close |
| --- | --- | --- |
| Semantic color | `ColorChannel` is U8; `PixelColor` has named RGBA channels but no accepted color-space or alpha contract | transparency, blank, eraser, equality, Compose, and future PNG/format meaning remain unresolved |
| Canvas validity | `CanvasWidth` and `CanvasHeight` accept every positive `Int`; `CanvasSize.pixelCount` is `Long` | a valid canvas can exceed all current `Int`-indexed materialization paths |
| Snapshot | `PixelSnapshot` defensively copies `List<PixelColor>` and queries row-major | area-sized object/reference ownership and equality cost |
| Mutable surface | `PixelSurface` converts `pixelCount` to `Int` and builds `MutableList<PixelColor>` | allocation safety depends on snapshot construction rather than a product canvas policy |
| Stroke | `Stroke` owns an arbitrary non-empty path list | duplicate and no-op raw samples can be unbounded independently of unique changes |
| Patch | `PixelPatch.create` sorts materialized candidate changes; `inverse()` maps a second list | sort/allocation occurs without a product cap and inverse storage is duplicated |
| ChangeSet/history | `ChangeSet` retains forward and materialized inverse patches; gateway has one-level history | entry count alone cannot justify retained-memory bounds |
| Projection | `toRenderedPixels` builds one `RenderedPixel` per canvas pixel; canvas draws every item | host core allocation misses area-sized presentation objects and frame cost |

The current product fixture uses opaque white blank and opaque red drawing in Android
composition. That is fixture evidence only; it is not an accepted alpha, blank, eraser, or
composition contract.

### Pre-fixed current extreme-integer characterization slice

Before selecting any numerical product limit, this question-free slice distinguishes reachable
current integer behavior from the still-unbounded allocation policy. It records no maximum and
must allocate no area-sized extreme fixture.

For a validated canvas axis `A`, every contained coordinate is in `0..A-1`. Therefore the
current affected-region expression `maximum - minimum + 1` is mathematically in `1..A` for any
non-empty valid patch and cannot exceed `Int.MAX_VALUE`. The test must construct a sparse patch on
an `Int.MAX_VALUE` square using only the two opposite valid corners. Patch creation must succeed,
canonicalize unordered input, produce origin `(0, 0)` and exact `Int.MAX_VALUE` width and height,
and preserve that region through inversion without allocating a canvas-sized buffer.

A separate input combines a valid origin with `(Int.MAX_VALUE, Int.MAX_VALUE)`, which is outside
that maximum canvas and would overflow both affected-axis expressions if region construction ran.
It must return typed `PositionOutsideCanvas` without an untyped exception, proving that outside
validation precedes the region calculation.

The current snapshot factory check uses a rectangular `Int.MAX_VALUE x 2` canvas and a one-element
sentinel list whose element accessor fails if called. It must return
`PixelSnapshotSizeMismatch(expected=4,294,967,294, actual=1)` without reading or copying an
element. This proves only the existing size-mismatch-before-copy order. It is not the required
future product-area rejection: a matching accepted list still has an `Int` size, and very large
but `Int`-indexable ownership, surface, or render allocations may still fail with OOM.

The implementation may change only existing or new unit-test sources in `:core:domain` and
`:core:pixel-engine`. It must add no production behavior, limit value, public API, dependency,
Gradle wiring, schema row, or accepted ADR answer. Focused tests, ktlint, and detekt must pass
before recording the result.

#### Recorded current extreme-integer characterization result

The test-only implementation was recorded at commit
`4fe05a5fef9f7cc6207373b2091b2c3e7ba152a9`. The `Int.MAX_VALUE x 2` snapshot case reported
`PixelSnapshotSizeMismatch(expected=4,294,967,294, actual=1)` without invoking the sentinel
element accessor. The current factory therefore checks the `Long` pixel count against the list's
`Int` size before its defensive copy on this mismatch path.

Two changes at `(0, 0)` and `(Int.MAX_VALUE - 1, Int.MAX_VALUE - 1)` on an `Int.MAX_VALUE`
square produced one canonical patch with change count two and the exact full-canvas affected
region. The inverse preserved that region. Replacing the far valid corner with
`(Int.MAX_VALUE, Int.MAX_VALUE)` returned the exact typed `PositionOutsideCanvas` rejection
instead of evaluating an overflowing affected-region expression.

Together with the existing typed-coordinate invariants, these observations show that current
affected-region subtraction and addition do not overflow for a valid patch: its span cannot
exceed its validated canvas axis. A created current `PixelSnapshot` must also have
`pixelCount == pixels.size.toLong()`, so its downstream `pixelCount.toInt()` calls cannot
numerically wrap. This does not make the current allocation policy safe. A very large but
`Int`-indexable snapshot, mutable surface, or render projection may still exhaust memory, and
patch candidates are still materialized and sorted without an accepted product cap.

The independent focused gate was:

```text
.\gradlew.bat :core:domain:test :core:pixel-engine:test :core:domain:ktlintCheck :core:pixel-engine:ktlintCheck :core:domain:detekt :core:pixel-engine:detekt --rerun-tasks --no-build-cache --no-configuration-cache
BUILD SUCCESSFUL in 2m 58s
31 actionable tasks: 31 executed
```

This slice changed two unit-test files only. It added no production behavior, limit, dependency,
Gradle wiring, measurement schema, or raw artifact. Exact axis, area, patch, and pre-allocation
rejection policy remains unresolved pending the complete physical evidence and one accepted
limit policy.

### Pre-fixed current patch validation-order characterization slice

Before selecting a patch-change maximum or rejection policy, this question-free slice records the
current order between revision validation, source materialization/sort, and typed content
validation. It chooses no count, cap owner, rejection type, or multiple-violation priority.

One small test-only access-recording `List<PixelChange>` supplies an outside-canvas change first,
followed by valid unordered unique changes. `PixelPatch.create` must read every source element for
canonical materialization/sort before returning the exact typed `PositionOutsideCanvas` rejection.
The outside change is deliberately first in source order so the observation cannot be explained by
an input-order bounds scan. No affected region may be created and no canvas-sized allocation is
needed.

A separate source list fails immediately if any element is read. At `Long.MAX_VALUE` revision,
`PixelPatch.create` must return the exact typed `RevisionOverflow` rejection without reading the
source. This records revision overflow as the only characterized pre-materialization short circuit;
it does not establish the future priority among empty, outside, unchanged, duplicate, or cap
violations.

The implementation may change `PixelPatchCreationTest.kt` only. It must add no production
behavior, public API, dependency, Gradle wiring, measurement schema, raw artifact, limit value, or
accepted ADR answer. Focused pixel-engine tests, ktlint, and detekt must pass before recording the
result.

#### Recorded current patch validation-order characterization result

The one-file test-only characterization was recorded at source commit
`a7c936cda6c362044367a984f4e28b3c6eb0f810`. With an outside change first in a three-element
source, the access-recording list observed all source indices before `PixelPatch.create` returned
the exact `PositionOutsideCanvas` canvas and position. The current implementation therefore
materializes and canonicalizes the complete source before bounds validation on this path; it has no
pre-sort product patch cap.

At `Long.MAX_VALUE` revision, a one-element source whose accessor always fails instead returned the
exact `RevisionOverflow` rejection without reading an element. This confirms the current revision
overflow check precedes source materialization. It does not select a future cap or define the
priority among other validation failures.

The independent focused gate was:

```text
.\gradlew.bat :core:pixel-engine:test :core:pixel-engine:ktlintCheck :core:pixel-engine:detekt --rerun-tasks --no-build-cache --no-configuration-cache
BUILD SUCCESSFUL in 2m 58s
21 actionable tasks: 21 executed
```

This slice changed one existing unit-test file only. It added no production behavior, API,
dependency, Gradle wiring, measurement schema, raw artifact, or limit. Pre-sort size rejection,
the accepted patch maximum, its typed rejection, owner, and validation priority remain unresolved.

### Pre-fixed current Stroke outside-short-circuit characterization slice

Before selecting a raw-stroke sample maximum, this question-free slice records one current safe
rejection path without allocating or iterating a valid extreme path. A test-only
`List<PixelPosition>` reports `Int.MAX_VALUE` elements, returns an outside-canvas position at index
zero, and fails if any later element is read. `Stroke.create` must read only index zero, return the
exact typed `PixelPositionOutsideCanvas` canvas and position, and never enter its defensive-copy
path.

This observation does not make accepted strokes bounded. A fully contained input is still scanned
and defensively copied with no product sample cap, and later rasterization still deduplicates raw
positions only after that ownership boundary. The slice chooses no raw count, rejection type, cap
owner, or multiple-violation priority.

The implementation may change `StrokeTest.kt` only. It must add no production behavior, public
API, dependency, Gradle wiring, measurement schema, raw artifact, limit value, or accepted ADR
answer. Focused domain tests, ktlint, and detekt must pass before recording the result.

#### Recorded current Stroke outside-short-circuit characterization result

The one-file test-only characterization was recorded at source commit
`1dc566e18aafec1f3c5f8d69d3c10c51b3986a7a`. A source reporting `Int.MAX_VALUE` elements returned
an outside position at index zero and would fail on every later access. `Stroke.create` read exactly
index zero and returned the exact `PixelPositionOutsideCanvas` canvas and position. It therefore
did not scan the remaining declared path or enter defensive copying on this current rejection path.

The independent focused gate was:

```text
.\gradlew.bat :core:domain:test :core:domain:ktlintCheck :core:domain:detekt --rerun-tasks --no-build-cache --no-configuration-cache
BUILD SUCCESSFUL in 7s
19 actionable tasks: 19 executed
```

This slice changed one existing domain unit-test file only. It added no production behavior, API,
dependency, Gradle wiring, measurement schema, raw artifact, or limit. Valid contained strokes
remain uncapped and fully scanned/copied before rasterization; the accepted raw count, typed cap
rejection, owner, and validation priority remain unresolved.

### Pre-fixed semantic compatibility characterization slice

Before adding or selecting a semantic color policy, this question-free slice characterizes only
the exact behavior already present at three boundaries: domain `PixelColor` value identity, the
test-only packed RGBA8888 candidate conversion, and the current Compose adapter. It does not
choose a color space for document truth, straight versus premultiplied semantic alpha,
alpha-zero canonicalization, blank, eraser, pencil composition, palette ownership, PNG mapping,
or a project-format contract. Those decisions remain separate from storage layout and require one
complete accepted policy after the physical evidence gate is satisfied.

The fixed U8 edge set is `0, 1, 127, 128, 254, 255`. The domain and packed-candidate checks cover
the complete Cartesian product of the four named RGBA channels, exactly `6^4 = 1,296` colors.
For every color they must prove exact named-channel preservation through pack/unpack. Equal domain
values must have equal hashes, while alpha-zero colors with different hidden RGB values must
remain unequal under the current value contract. This is characterization, not acceptance of
hidden-RGB preservation as the future policy.

The current Compose adapter check uses the same 1,296 colors. It must prove that
`PixelColor.toComposeColor()` supplies each U8 channel unchanged to a Compose `Color`, that the
constructed color reports the Compose sRGB color space, and that conversion to the adapter's
32-bit ARGB observation preserves the exact alpha and RGB bytes, including distinct RGB bytes at
alpha zero. It does not assert raster blending, framebuffer storage, display color management, or
PNG encoding. Source inspection must also continue to show that no PNG or project-format adapter
exists yet; this slice must not add one.

The tests may strengthen existing test sources in `:core:domain`, `:core:pixel-engine`, and
`:presentation:compose`. They must add no production behavior, public API, dependency, Gradle
wiring, schema version, raw measurement row, or accepted ADR answer. Focused domain,
pixel-engine, and Compose unit tests plus ktlint and detekt must pass before the result is recorded.

#### Recorded semantic compatibility characterization result

The test-only implementation was recorded at commit
`20e9212d3a7da870788ffe4e4bfc387e8d949799`. The resolved Compose graphics dependency for this
observation was `androidx.compose.ui:ui-graphics:1.12.0` under BOM `2026.08.00`.

All `1,296` edge-set Cartesian RGBA values preserved their four exact named U8 channels in
domain `PixelColor`. Independently constructed equal values were equal with equal hashes. Two
alpha-zero values that differed only in hidden RGB remained unequal and occupied distinct set
entries under the current domain value contract.

The test-only packed RGBA8888 conversion produced exact `RRGGBBAA` bits and returned the original
four channels for all `1,296` values. The current Compose adapter reported sRGB, preserved every
channel within less than one-half U8 quantization step, and produced the exact expected
`AARRGGBB` value for every case. In particular, transparent black produced `0x00000000` and
transparent red produced `0x00ff0000`; the adapter therefore did not discard hidden RGB in this
32-bit observation.

The independent focused gate was:

```text
.\gradlew.bat :core:domain:test :core:pixel-engine:test :presentation:compose:testDebugUnitTest :core:domain:ktlintCheck :core:pixel-engine:ktlintCheck :presentation:compose:ktlintCheck :core:domain:detekt :core:pixel-engine:detekt :presentation:compose:detekt --rerun-tasks --no-build-cache --no-configuration-cache
BUILD SUCCESSFUL in 3m
60 actionable tasks: 60 executed
```

Source inspection found no PNG or project-format adapter in production source. This slice added
only three test-source changes; it changed no production behavior, dependency, Gradle wiring,
measurement schema, or raw artifact. It establishes current compatibility facts only. Document
color space and alpha meaning, alpha-zero policy, blank, eraser, pencil composition, palette
ownership, PNG/project-format conversion, and the accepted storage representation all remain
unresolved.

### Pre-fixed current host render-projection slice

Before collecting this slice, the current P2 host artifacts contain no isolated execution of
`PixelSnapshot.toRenderedPixels()`. The viewport-controller metric times state transformation only,
and the physical frame route is a composite UI/frame boundary. This host-only diagnostic therefore
measures the current area-sized `RenderedPixel` list and Compose color conversion separately. It
does not measure draw iteration, viewport clipping, Compose scheduling, Android ART, retained
heap, PSS, GPU/compositor work, or a physically visible frame.

The matrix reuses the seven canvas shapes already fixed for candidate snapshot and sparse-patch
analysis:

| Pixel count | Shapes |
| ---: | --- |
| 4,096 | `64x64`, `16x256`, `256x16` |
| 16,384 | `128x128`, `64x256`, `256x64` |
| 65,536 | `256x256` |

Every shape uses four independently generated semantic inputs:

- uniform opaque-white current reference blank;
- uniform opaque red as one non-blank semantic color;
- exactly 256 deterministic RGBA colors repeated row-major; and
- deterministic high-entropy RGBA.

The timed interval contains one call to prepared `PixelSnapshot.toRenderedPixels()` only. Snapshot
and expected-oracle construction, digest generation, and full verification are outside timing.
Each of the `7 shapes x 4 contents = 28` metrics uses five warmups and ten raw samples, producing
exactly 28 summary rows and 280 sample rows. Latency and current-thread HotSpot allocated bytes use
nearest-rank median, p95, and p99; p99 is the maximum of these ten diagnostic samples and is not
physical tail evidence.

Every warmup and sample must verify all `N` outputs against an oracle prepared independently of the
projection implementation: exact list count, row-major `(x, y)`, expected `AARRGGBB`, Compose sRGB,
first and last position, full projection SHA-256, unchanged source revision and pixel SHA-256, and
cross-sample deterministic digest. Any mismatch fails the measurement rather than becoming a fast
row.

The separately named task is `:presentation:compose:measureP2RenderProjection`, included by the
root `measureP2RepresentationLimits` task in its own single 512 MiB worker. It writes
`build/reports/p2/representation-limits/host-projection.csv` with schema
`nene-pixel-p2-representation-limits-host-projection-v1`. The report must identify the profile,
JVM/OS, heap, exact boundary, exclusions, sampling, shape/content, raw samples, nearest-rank
summaries, correctness status, and source/projection digests. Shared host measurement primitives
already present in the presentation test source must be reused or extracted rather than copied.

This slice may change presentation test source and the existing measurement-task wiring only. It
adds no production behavior, public API, dependency, plugin, module, APK payload, or accepted
limit. Candidate snapshots remain isolated in pixel-engine test source and cannot traverse the
production `PixelSnapshot.toRenderedPixels()` boundary. They must not be copied, exposed, or
converted to the current snapshot for this slice; candidate render projection remains pending a
single canonical representation/read boundary.

#### Recorded current host render-projection result

The test-only projection harness and existing measurement-task wiring were recorded at source
commit `e17813087fa68eb3103d7d1dedaf630958f4dd63`. From that source, the complete host aggregation
was rerun without build or configuration cache:

```powershell
.\gradlew.bat measureP2RepresentationLimits --rerun-tasks --no-build-cache --no-configuration-cache
```

It completed with `BUILD SUCCESSFUL` in 3 minutes 1 second and 29 executed tasks. The isolated
projection report was:

| Raw path | Bytes | SHA-256 | Schema / rows |
| --- | ---: | --- | --- |
| `build/reports/p2/representation-limits/host-projection.csv` | 165,644 | `346F4F48A120344F03FBA407C340D2A30B47F409B977B3E5402837048A7E1915` | `nene-pixel-p2-representation-limits-host-projection-v1`; 19 metadata, 28 metric, 280 sample rows |

An independent CSV audit found zero structural, identity, sample-index, correctness-status, digest-
format, or nearest-rank percentile mismatches across all 28 metrics. A separately compiled C#
oracle regenerated all four inputs without using the Kotlin fixture, then recomputed both SHA-256
encodings, exact color cardinality, pixel count, and first/last position and `AARRGGBB` for every
shape/content pair. It also found zero mismatches.

The largest shape produced these current host medians:

| `256x256` content | Projection median | Current-thread allocated median |
| --- | ---: | ---: |
| opaque-white reference blank | 582,500 ns | 3,407,912 bytes |
| opaque-red one-color | 578,600 ns | 3,407,912 bytes |
| exact 256 deterministic RGBA | 590,800 ns | 3,407,912 bytes |
| deterministic high-entropy RGBA | 610,100 ns | 3,407,912 bytes |

The focused presentation test, ktlint, and detekt gate completed in 15 seconds with 40 executed
tasks. The existing viewport measurement was separately rerun after extracting the shared host
runner and completed in 11 seconds with 25 executed tasks; its schema v1, 12-column report shape,
and single metric remained intact. The source commit adds no production behavior, public API,
dependency, plugin, module, or APK payload.

These timings and allocation values describe only one current HotSpot projection call. They do not
measure retained heap, Android ART or PSS, draw iteration, Compose scheduling, GPU/compositor work,
or a physical frame. Candidate render projection remains pending its canonical representation/read
boundary, so this result selects no representation or hard limit and does not close the physical
evidence blocker.

## Historical pre-fixed measurement contract

This section preserves the protocol that governed the completed diagnostic batches below. The
current continuation contract in [Decision boundary](#decision-boundary) governs all new
collection and ADR acceptance. In particular, new latency batches must not force GC/finalization
or perform full-document equality scans, digests, or hashes between samples; memory and frame
evidence use their separate lanes; and strict compositor correlation waits for the selected
renderer path.

The following pass conditions are fixed before choosing a representation or hard product limit.
They apply only at explicitly measured workload candidates and do not themselves define a
canvas, patch, stroke, or history maximum.

| Condition | Pre-fixed target |
| --- | --- |
| Maximum-workload core latency | p95 <= 8.0 ms and p99 <= 16.67 ms |
| Affected-frame timing | p95 meets the device frame deadline and p99 overrun <= 16.67 ms |
| Command to visible correctness | command start to first correct frame p95 <= 33.33 ms |
| Steady ART live heap | render projection plus retained analytical history <= 50% of `Runtime.maxMemory` |
| Peak headroom | steady live heap plus operation p95 allocation <= 70% of `Runtime.maxMemory` |
| Blocking GC | zero increment for at least 95% of measured operations |
| PSS median | five-checkpoint median <= baseline + 50% of `memoryClass` |
| PSS individual checkpoint | every checkpoint <= baseline + 60% of `memoryClass` |
| Post-GC churn | after cap rejection and ten undo/redo-equivalent cycles, <= max(1 MiB, 1% of heap limit) |
| Correctness and stability | zero OOM, ANR, untyped exception, state mismatch, patch/inverse mismatch, affected/unaffected-pixel mismatch, or revision mismatch |

A candidate fails if any applicable condition fails. Results are not interpolated. A largest
passing measured candidate causes the matrix to be extended before a maximum is claimed.

### Final physical collection protocol

The twenty-sample command run above qualifies the physical route but does not supply final p99
tail evidence. Before any representation or hard-limit selection, final command and affected-
frame batches use five warmups and 200 measured samples per reported maximum workload. The
nearest-rank p95 and p99 are computed from the 200 raw rows; no outlier is discarded.

PSS decision evidence uses five independent instrumentation invocations and preserves one
run-indexed raw artifact per invocation. The final Android harness records thermal status and
active display mode before measured samples, after every 25 measured samples, and after the final
sample in the same raw CSV. A batch is invalid if the display mode changes, any checkpoint has
device-wide thermal status above 1, or the frame listener reports a dropped callback. Power-
saving mode remains disabled and the device remains USB powered and awake. No process kill,
network mutation, or device reset is introduced between checkpoints unless the entire five-
checkpoint protocol is restarted and the change is recorded.

The physical display is active at 90 Hz. The frame instrumentation uses the per-frame
platform `FrameMetrics.DEADLINE`, not a substituted 16.67 ms period. The separate p99 overrun
allowance remains 16.67 ms as pre-fixed above. Its CSV calls the direct boundary the first exact
app-issued frame, links exact `EditorRenderState` and a post-content draw generation to copied
frame metrics, and rejects unavailable timing fields or listener drops. Compose image capture
verifies the resulting pixels outside the timed interval.

An app-issued frame is not proof of physical SurfaceFlinger presentation. Final evidence for the
existing command-to-visible-correctness wording must additionally correlate the recorded API 36
frame-timeline vsync ID with a retained Perfetto/FrameTimeline artifact. Until that correlation is
present, app-issued timing is useful physical renderer evidence but cannot satisfy the visible-
frame acceptance condition by itself.

### Pre-fixed paired Perfetto/FrameTimeline correlation contract

The next compositor slice reruns the unchanged final frame harness while a system trace is active;
an earlier app-issued timeline ID cannot be correlated to a trace captured later. The frame CSV
schema remains `nene-pixel-p2-android-frame-measurement-v1`. Its fixed identity is candidate
`current-canonical-dense-256-square`, run index 3, five warmups, 200 samples, the same physical
profile/build, and the full source commit used to build both APKs. A caller-generated canonical
UUID identifies the outer paired batch, the trace trigger, tool manifest, config, query, CSV, and
correlation report. APK byte lengths and SHA-256 values are recorded before installation.

The retained trace uses device Perfetto v49.0 and host Trace Processor
`v49.0-33a4fd078` (`33a4fd07897a9a648664926ea27769278a19ff13`). The pinned Windows binary is
10,479,616 bytes with SHA-256
`A881F3E2D4C6131493E85BFD1F36D1EFE58E1478E2991825418D5D21614C1E48`. Its trace config has one
64 MiB `RING_BUFFER`, requests `android.surfaceflinger.frametimeline`, uses a unique batch-derived
`STOP_TRACING` trigger with a 300-second trigger timeout, and records clock snapshots required to
map trace timestamps to `CLOCK_MONOTONIC`. The collector starts with `--background-wait`; the
instrumentation begins only after all producers acknowledge startup. The stop trigger is sent only
after the final physical checkpoint and complete frame CSV publication. A wall-time timeout, a
manual process kill, producer-start failure, missing closed trace, trace buffer overwrite/discard,
packet loss, parse error, or a trace interval that does not contain the complete instrumentation
interval invalidates the batch.

The unique config and trace staging paths are
`/data/misc/perfetto-configs/nene-p2-frame-<batch>.txtpb` and
`/data/misc/perfetto-traces/nene-p2-frame-<batch>.perfetto-trace`; either pre-existing path is a
failure. The retained tool manifest records the exact `perfetto --background-wait --txt -c ... -o
...` start command, returned tracing PID, batch-derived trigger name, and later
`/system/bin/trigger_perfetto <name>` command. This build's separate trigger client is 241,744
bytes with SHA-256 `DF6D2C72CD1E837CD67A23364405C4ADB504A5D05D4D359068792E78C772E29B`;
the `perfetto` recorder CLI does not implement a `--trigger` option. The trigger client is the only
normal stop path; the tracing process is not killed.

The original host `device-frames.csv`, `device-profile.txt`, `device-logcat.txt`, command, and
memory artifacts are immutable and must retain their recorded byte lengths and SHA-256 values.
The traced run may replace only the app-internal fixed frame-report path used by the existing test;
it is copied immediately to the distinct immutable host name below. No accepted host artifact is
overwritten. The paired artifact set is fixed as:

| Artifact | Fixed role |
| --- | --- |
| `build/reports/p2/representation-limits/device-frames-perfetto.csv` | unchanged frame schema from the traced 5/200 run |
| `build/reports/p2/representation-limits/device-frame-timeline.perfetto-trace` | closed raw FrameTimeline trace |
| `build/reports/p2/representation-limits/device-frame-correlation.csv` | schema `nene-pixel-p2-android-frame-perfetto-correlation-v1`; 200 joined rows plus metadata/summary |
| `build/reports/p2/representation-limits/device-frame-perfetto-config.txtpb` | exact trace config with batch-derived trigger |
| `build/reports/p2/representation-limits/device-frame-perfetto-query.sql` | exact retained PerfettoSQL extraction and validation query |
| `build/reports/p2/representation-limits/device-frame-perfetto-tool.txt` | batch/source/APK/device/Trace Processor identity and collection commands |

The retained SQL selects `actual_frame_timeline_slice` and `expected_frame_timeline_slice` rows for
the exact app package and its buffer-surface layer, never by nearest timestamp alone. Each of the
200 positive, unique CSV `frame_timeline_vsync_id` values must equal exactly one app surface-frame
token in each table. The selected app actual row must have `is_buffer=1` and one positive display-
frame token; that token must select exactly one SurfaceFlinger display actual row and exactly one
display expected row. All selected app rows use one stable non-empty layer identity. Missing or
duplicate rows/tokens, a different layer, null or non-positive tokens, negative durations, `Dropped Frame`,
`Unknown Present`, or an unrecognized present type invalidates the whole batch; no sample is
discarded or substituted.

For every selected SurfaceFlinger actual row, Trace Processor clock synchronization converts the
physical-present end `ts + dur` to `CLOCK_MONOTONIC`; the retained query records both trace time and
`to_monotonic(ts + dur)`, rejects a null conversion, and retains the applicable clock-snapshot
rows. The raw command-to-physical-present duration is
`monotonic_present_end - command_start_nanos` from the matching frame CSV row. It must be positive.
The correlation report retains all 200 raw joins, app/SF tokens and intervals, layer, jank/on-time/
present fields, clock-converted end, and duration. Nearest-rank p95 and p99 use all 200 durations
with no interpolation, outlier removal, or result substitution. The existing 33.33 ms visible-
correctness threshold is evaluated from that p95; correlation does not turn a numerical failure
into a pass.

After host byte/hash verification, only exact temporary trace/config/export staging is removed and
the recorded original stay-awake setting is restored and verified. Every paired artifact receives
a byte length and SHA-256 in this ledger. This slice can establish SurfaceFlinger correlation for
this single current dense 256-square run only; it does not provide candidate compositor evidence,
complete physical workload coverage, representation selection, or a product limit.

### Paired Perfetto diagnostic attempts

Four collection attempts were rejected before any paired compositor result was accepted. Batch
`c83971c6-522e-45d2-b470-c82fd266c7a4` proved that this device's recorder CLI has no `--trigger`
option; its trace was stopped with the separate trigger client and deleted as invalid. Batch
`38b5e11a-3c96-4cb8-bf00-ede9ff73e3be` let the 300-second trigger timeout expire immediately
before its 200-sample test completed, leaving a zero-byte output; it was also deleted as invalid.

Batches `9ef9fb49-e77f-4e42-9fdc-644f8b8ce027` and
`51c8e1a1-266c-4446-ad6b-b06ca963a7d4` started instrumentation immediately after producer
acknowledgement and completed their unchanged 5/200 tests in 138.030 and 138.342 seconds. In each
trace all 200 positive unique CSV tokens selected exactly one app actual row, app expected row,
SurfaceFlinger display actual row, and display expected row. Per-buffer overwrite, discard, packet
loss, and write-wrap values were zero, and final flush succeeded. Pinned Trace Processor v49.0
nevertheless reported `frame_timeline_event_parser_errors=4` in both traces. The rejected events
were subsequently attributed exactly, but the pre-fixed no-parse-error rule has no attribution
exception and therefore still invalidates both batches despite their complete 200-row joins. Each
trace contains four non-buffer actual-surface events emitted by PID 1370 `system_server` around
instrumentation Task transitions, outside the measured app-surface rows. All eight events across
the two traces have raw `present_type=0` (`PRESENT_UNSPECIFIED`), raw `prediction_type=2`,
`Is Buffer?=No`, and duration 2,000,000 ns. Pinned v49 accepts present-type values 1 through 5 and
increments the parser-error stat once for each raw zero, accounting for all four errors in each
trace; the other checked loss, pairing, and flush stats do not account for them. The start-side
events precede the first measured command by about 11.3 seconds and the end-side events finish
about 0.48 seconds after the last measured frame completes.

The system-wide FrameTimeline source exposes no package, layer, or buffer-only setup filter, so a
simple retry under the same whole-instrumentation trace boundary is expected to reproduce the same
error class. Excluding those rows, changing the pinned processor, or accepting parser errors would
violate the fixed contract. Moving trace collection inside the Activity lifetime, after setup and
before teardown, is a possible future docs-first boundary change but is held for an explicit
decision rather than applied after observing these traces. Their host files remain only under
explicit `.invalid-9ef9fb49` and `.invalid-51c8e1a1` diagnostic names and are not canonical evidence.
Exact device staging was removed and `stay_on_while_plugged_in` was restored to its original value
`0`. No paired physical-present percentile or visible-frame condition is reported from these
attempts.

### Refreshed-build current-command tail contract

The reference tablet updated after the preliminary command and frame collections. Read-only
queries on 2026-09-02 reported build fingerprint
`ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94110:user/release-keys` and security patch
2026-06-05. The stable hardware profile ID remains
`NENE-P2-ALLDOCUBE-IPL80MP-A16-API36`, but no result from build `94010` may be aggregated with a
build `94110` result. The exact fingerprint and patch level are emitted in each new raw artifact.

Before implementation, the final current-command tail route is fixed as follows:

- schema `nene-pixel-p2-android-final-command-measurement-v1`, candidate ID
  `current-canonical-command-256-square`, run index 1, and a caller-supplied full 40-character
  source commit that must match the installed APK and test APK source;
- fixed workload order `sparse_apply_stroke`, `dense_apply_stroke`,
  `dense_same_color_no_op`, `dense_undo`, and `dense_redo`, all at 256 x 256;
- five untimed warmups followed by 200 measured samples for each workload, with a fresh
  `PreparedCommandWorkload` and exactly one `CommandGateway.execute` call per iteration;
- one post-GC baseline before measured samples, then ART runtime-stat, Java-heap, PSS, and
  correctness observations outside the direct `CommandGateway.execute` latency boundary;
- one-based per-workload sample indices and one-based global indices across the 1,000 raw sample
  rows; nearest-rank percentiles are recomputed from raw rows and no row is discarded;
- physical checkpoints before samples, after every 25 global samples, and after the final sample,
  producing 42 deliberately named checkpoint rows including distinct `after_1000` and
  `after_samples` observations; and
- batch rejection if display mode, physical dimensions, or 90 Hz refresh differs from the
  baseline, device-wide thermal status exceeds 1, power saving is enabled, the display is not
  interactive, USB power is lost, or battery data is unavailable.

Every tail sample verifies the exact resulting `DocumentState`, complete pixel content, revision,
history availability, command result kind, applied `ChangeSet` revisions and render invalidation,
or exact no-effective-change rejection and unchanged state identity as applicable. Lower-level
canonical patch ordering, forward/inverse round trip, and unaffected-pixel requirements remain
owned by the existing isolated canonical core tests; this tail route does not add a public or
second access path to private patch storage. Per-sample post-GC PSS is diagnostic only and does not
satisfy the separately required five independent PSS invocations.

The new on-device command output is
`files/p2-measurements/p2-android-final-command-measurement.csv` and is copied unchanged to
`build/reports/p2/representation-limits/device-core.csv`. On the same source commit and refreshed
build, the existing frame route is rerun with candidate ID
`current-canonical-dense-256-square`, run index 2, five warmups, and 200 samples, then copied to
`device-frames.csv`. This paired collection neither supplies Perfetto correlation nor selects a
representation or hard product limit.

### Refreshed-build paired physical result

The contract above was implemented as Android-test-only source and collected from source commit
`16fa62101e6a76d64bab6cfe809b3e49f5ba7afa` on 2026-09-02. Both APKs were rebuilt from that commit
with `:app:android:assembleDebug` and `:app:android:assembleDebugAndroidTest`, installed together,
and invoked through `AndroidJUnitRunner`. The command invocation completed one test in 204.627
seconds; the frame invocation completed one test in 139.302 seconds.

The command artifact contains 45 metadata rows, one post-GC baseline, 42 ordered checkpoints, and
1,000 raw samples. Each of the five fixed workloads has exactly 200 one-based local indices and
the combined rows have unique global indices 1 through 1,000. All checkpoints retained mode 1 at
1200 x 1920 and 90 Hz, device-wide thermal status 1, power saving disabled, an interactive display,
USB power, and valid battery data. All command rows passed their exact state, complete-pixel,
revision, history, result-kind, public `ChangeSet`, invalidation, and no-op identity assertions.

Nearest-rank latency and ART diagnostics from the raw rows are:

| Current 256 x 256 command workload | Samples | p95 | p99 | Maximum | p95 ART allocated bytes | Zero blocking-GC increment |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| sparse apply stroke | 200 | 21.263 ms | 22.041 ms | 23.917 ms | 6,914,048 | 200 / 200 |
| dense apply stroke | 200 | 110.269 ms | 113.969 ms | 156.838 ms | 21,106,688 | 199 / 200 |
| dense same-color no-op | 200 | 36.974 ms | 39.003 ms | 39.449 ms | 5,390,336 | 199 / 200 |
| dense undo | 200 | 38.673 ms | 41.525 ms | 43.347 ms | 8,781,824 | 200 / 200 |
| dense redo | 200 | 33.095 ms | 39.607 ms | 41.870 ms | 23,859,200 | 200 / 200 |

The direct core-latency condition fails: the maximum-workload dense apply is above both the
pre-fixed 8.0 ms p95 and 16.67 ms p99 targets. The blocking-GC condition passes for this isolated
tail route because 998 of 1,000 operations had zero blocking-GC increment. The post-GC process
baseline was 2,213,648 Java-heap bytes and 83,846 kB PSS; per-sample maxima were 7,444,288 bytes and
89,193 kB. Those PSS values remain diagnostics and do not replace the five independent memory
invocations or retained-history live-heap evidence.

The paired frame artifact contains 30 metadata rows, 10 ordered checkpoints, and 200 raw frame
samples. Every sample had exact render state, matching revision and snapshot hash, zero mismatch
across all 65,536 checked logical pixels, one positive unique API 36 timeline-vsync ID, and zero
FrameMetrics report drops. All frame checkpoints retained the same valid display, thermal, power,
interactive, USB, and battery conditions as the command batch.

| Current dense 256-square frame observation | p95 | p99 | Maximum |
| --- | ---: | ---: | ---: |
| command start to first exact app-issued frame | 302.908 ms | 310.238 ms | 315.595 ms |
| FrameMetrics total duration | 201.633 ms | 207.068 ms | 210.231 ms |
| FrameMetrics deadline overrun | 191.633 ms | 197.068 ms | 200.231 ms |

The platform deadline was 10.0 ms for every row and all 200 rows missed it. The affected-frame
condition therefore fails, as does the 33.33 ms app-issued command-to-correct-frame threshold.
Because no Perfetto/FrameTimeline artifact was collected in this slice, the latter remains an
app-issued observation and is not promoted to a physical SurfaceFlinger visibility claim.

The retained raw artifacts are:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-core.csv` | 661,245 | `E2FD1D427DE191F14A8194FE4CF5CE5A929A388B7A13E439E731F0AF8180FCAD` |
| `build/reports/p2/representation-limits/device-frames.csv` | 126,738 | `5D5369215A3F65843B5E351E82BCCDBB1E24DFBA0EA61622CE9569C3FE9B8957` |
| `build/reports/p2/representation-limits/device-profile.txt` | 2,714 | `341676CB479298A209A511EF876469CDB55144829F108FBD097C07AC21CFE02A` |
| `build/reports/p2/representation-limits/device-logcat.txt` | 1,273,338 | `607260C1915B09EBF6EED110547F4FFA1916793B25FE97AA2AD74485B2FFE6C1` |

The time-scoped logcat covers 2026-09-02 16:42:30 JST through post-collection and contains 9,289
lines. A deterministic audit found zero fatal-exception, fatal-signal, ANR, GC-event-pattern, or
thermal-event-pattern matches. The only package-name matches were two install-time `vold` probes
for absent OBB directories; neither occurred in an instrumentation process or changed a result.
The device's original `stay_on_while_plugged_in=0` was recorded, temporarily set to USB-only value
2 for the paired collection, restored to 0 afterward, and verified. No process kill, network
mutation, or device reset was introduced.

### Pre-fixed five-invocation current retained-memory contract

The next physical slice measures the current canonical object/materialized representation only.
It does not introduce production multi-step history, copy the pixel-engine candidate test models,
or claim physical evidence for flat-packed or tiled candidates. Its invocation schema is
`nene-pixel-p2-android-memory-invocation-v1`; its aggregate schema is
`nene-pixel-p2-android-memory-aggregate-v1`. The fixed candidate and workload identities are
`current-canonical-object-materialized-v1` and
`retained_projection_256_square_h64_t8n`.

Five separate `am instrument` invocations use run indices 1 through 5, the same full 40-character
source commit, the same physical profile/build, and one caller-supplied canonical UUID batch ID.
Each invocation is a new instrumentation process and records its PID and process-start elapsed
time. It writes exactly one immutable run-indexed on-device file
`files/p2-measurements/p2-android-memory-run-0N.csv`; an existing final file is a failure, and a
successful run is never overwritten. The five host copies retain the corresponding
`device-memory-run-01.csv` through `device-memory-run-05.csv` names fixed below.

The retained fixture is fixed at 256 x 256, `N=65,536`, `H=64`, `C=8,192` effective positions per
entry, and `T=524,288=8N` total retained changes. One production `CommandGateway` executes 64 real
commands while the Android test owner retains each public `CommandResult.Applied.changeSet`:

- the canvas is divided into eight 256 x 32 row blocks;
- entry `i` changes block `i mod 8` to opaque red when `i / 8` is even and opaque white otherwise;
- every entry is effective, each pixel changes exactly eight times, and revisions advance exactly
  from 0 through 64; and
- each public render invalidation has origin `(0, (i mod 8) * 32)` and size 256 x 32, while the
  final revision-64 snapshot is entirely opaque white.

After full pixel, revision, history, result, and public-invalidation verification, the production
gateway, commands, strokes, input position lists, and intermediate snapshots are not retained.
The test-only owner strongly retains only the final `DocumentState`, the 64 opaque public
`ChangeSet` objects, and the current canonical materialized render projection. Private patch
counts, canonical ordering, inverse records/replay, and logical storage counts remain owned by the
existing isolated host schema-v6 evidence.

The projection is created by a release-absent debug-only opaque bridge that delegates directly to
the canonical `PixelSnapshot.toRenderedPixels()` function. It does not expose the list,
`RenderedPixel`, or mutable pixel storage. It reports only pixel count, first/last row-major
position and ARGB, full projection SHA-256, and mismatch count. The retained 256-square opaque-
white projection must match the existing host digest
`F1100EF5EA1ACC83FB6A15F08ECBDE5277293C83707B2C13B5F8FC963E7A0F74`.

Each invocation uses this exact sequence:

1. validate profile, candidate, run index, batch UUID, source commit, and physical-only evidence;
2. capture physical checkpoint `before_baseline`;
3. preload the same command/projection classes with one discarded 16-square one-pixel applied
   command and its canonical projection;
4. capture a two-pass post-GC Java-heap/PSS baseline;
5. construct and fully verify the fixed `H=64, T=8N` owner and projection;
6. capture a two-pass post-GC retained Java-heap/PSS checkpoint while that owner remains strongly
   reachable;
7. capture compatible physical checkpoint `after_retained`; and
8. validate all rows and atomically publish the immutable run file.

Both memory rows include Java heap used/committed, total/dalvik/native/other PSS, private/shared
dirty, `Runtime.maxMemory`, and `memoryClass`. Each raw also contains exact profile/build/source/
batch/process identity, `N/H/C/T`, block/color order, two physical checkpoints, 64 public entry
observations, final pixel and entry-descriptor digests, projection observables, GC and retained
boundaries, and correctness status. Fixture creation, verification, projection creation, and report
writing occur outside both memory checkpoints.

After the five immutable raws exist, a separate aggregate-only instrumentation invocation reads
them without taking a sixth memory observation. It rejects a missing or duplicate run index; any
batch, source, candidate, workload, profile, fingerprint, security-patch, schema, row-count, entry-
sequence, display, thermal, power, battery, correctness, digest, memory-field, process-identity, or
raw-checksum mismatch; and an existing aggregate output. It writes
`files/p2-measurements/p2-android-memory-aggregate.csv`, copied unchanged to `device-memory.csv`,
with each raw's byte length/SHA-256, five run rows, and one aggregate row.

For run `i`, paired PSS delta is `d_i = retained_total_pss_kib_i - baseline_total_pss_kib_i`.
Negative values remain raw and are not clamped. The nearest-rank median for five values is the
third sorted value; there is no interpolation or outlier removal. The median condition is
`2 * median(d_i) <= memoryClassKiB`, and the individual condition is
`5 * d_i <= 3 * memoryClassKiB` for every run. Steady ART live heap requires
`2 * retained_java_heap_used_bytes <= Runtime.maxMemory` for every run; median and maximum are also
reported. Peak headroom, cap-rejection churn, candidate retained memory, and candidate projection
remain unevaluated. Every raw fixes
`post_gc_churn_status=not_evaluated_cap_policy_unselected`; inventing a test-only cap before the
limit policy is selected would not satisfy the production condition.

### Current retained-memory physical result

The fixed current-canonical workload was collected on 2026-09-02 from source commit
`a252bd5d733a408c0d3577a3580d42a8f57ea832` with batch UUID
`6fb5de52-0a84-47f1-97fb-260389c76d44`. The source-identical build completed 95 tasks in 34
seconds. Its debug APK is 11,664,182 bytes with SHA-256
`71B732DE85D2984360CD6676184D66250D87EDA34444DB04EDA82E278BB5C796`; the AndroidTest APK is
1,289,357 bytes with SHA-256
`B8E82A2B8375391F0B9B4513281975466008A03E62D6BAAEF797C6EDA8F1CB81`.

All five run invocations and the aggregate-only invocation completed successfully in six distinct
PID/process-start pairs. Every raw contains exactly 27 metadata, two physical-checkpoint, two
memory-checkpoint, 64 retained-entry, and one retained-summary row. All 64 entry observations,
revisions, 256 x 32 invalidations, final all-white snapshot/projection, zero projection mismatches,
and the fixed pixel/projection and entry-descriptor digests passed. All ten physical checkpoints
had mode 1 at 1200 x 1920 and 90 Hz, overall thermal status 1, low-power mode false, interactive
USB power, and 100% battery.

| Run | Baseline post-GC PSS (KiB) | Retained post-GC PSS (KiB) | Paired delta (KiB) | Retained Java heap used (bytes) |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 88,768 | 125,891 | 37,123 | 43,185,968 |
| 2 | 88,790 | 125,955 | 37,165 | 43,185,968 |
| 3 | 88,594 | 125,964 | 37,370 | 43,190,064 |
| 4 | 88,886 | 125,333 | 36,447 | 43,185,968 |
| 5 | 88,610 | 125,815 | 37,205 | 43,185,968 |

The sorted paired-PSS median is 37,165 KiB and the maximum is 37,370 KiB. The median condition
passes because `2 * 37,165 = 74,330 <= 262,144 KiB`; every individual condition passes, with the
largest left side `5 * 37,370 = 186,850 <= 786,432 KiB`. The retained Java-heap median is
43,185,968 bytes and the maximum is 43,190,064 bytes. The steady ART condition passes for every
run; the largest left side is `2 * 43,190,064 = 86,380,128 <= 268,435,456 bytes`.

The aggregate-only Android audit and an independent host audit both passed exact row/order,
identity, checkpoint, workload, digest, correctness, raw-byte-length, raw-SHA-256, paired-delta,
median, maximum, and condition recomputation. The immutable host artifacts are:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-memory-run-01.csv` | 58,759 | `A1E28745ACF21B5ADFBD4E8BB95B8F0D57F9C5579D91BBCA9D14630B50705B81` |
| `build/reports/p2/representation-limits/device-memory-run-02.csv` | 58,759 | `7160414DF70B380F1D803A7CC7F33D7E4A202DF555E3676117F48100042B87D0` |
| `build/reports/p2/representation-limits/device-memory-run-03.csv` | 58,759 | `8F210444D87ACCB2599AEF623A6E7B907FE01336E79192A392FDF09518AC70FF` |
| `build/reports/p2/representation-limits/device-memory-run-04.csv` | 58,759 | `D1C5DF8A783E46CC57013C25D4937EA1A0DF873062380DA50AE02A977405AA79` |
| `build/reports/p2/representation-limits/device-memory-run-05.csv` | 58,759 | `F8CAFA68257B5C211D79C9A608249AE7536A71E95A92E0AE24758B1555B6807D` |
| `build/reports/p2/representation-limits/device-memory.csv` | 16,655 | `5536A65C32FB56F80BEE3F23AE51A60E3F8B9534F80C7CEB628F6B21E5BFBBA9` |

These results establish only the fixed current-canonical retained owner and projection at
`N=65,536`, `H=64`, and `T=8N`. Within the memory aggregate alone, peak headroom, post-cap churn,
candidate retained memory, and candidate projection remain explicitly unevaluated and cannot be
inferred from these passing steady-memory conditions. The separate current-canonical cross-artifact
peak audit is fixed and reported below.

### Pre-fixed current-canonical peak-headroom cross-artifact contract

The next source-free audit evaluates only the current-canonical peak-headroom condition by joining
the already accepted final-command and retained-memory evidence. It does not rewrite either input,
change `peak_headroom_status=not_evaluated` in the memory aggregate schema, measure a candidate, or
select a representation or product limit. Its inputs are fixed before calculation:

| Input artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-core.csv` | 661,245 | `E2FD1D427DE191F14A8194FE4CF5CE5A929A388B7A13E439E731F0AF8180FCAD` |
| `build/reports/p2/representation-limits/device-memory-run-01.csv` | 58,759 | `A1E28745ACF21B5ADFBD4E8BB95B8F0D57F9C5579D91BBCA9D14630B50705B81` |
| `build/reports/p2/representation-limits/device-memory-run-02.csv` | 58,759 | `7160414DF70B380F1D803A7CC7F33D7E4A202DF555E3676117F48100042B87D0` |
| `build/reports/p2/representation-limits/device-memory-run-03.csv` | 58,759 | `8F210444D87ACCB2599AEF623A6E7B907FE01336E79192A392FDF09518AC70FF` |
| `build/reports/p2/representation-limits/device-memory-run-04.csv` | 58,759 | `D1C5DF8A783E46CC57013C25D4937EA1A0DF873062380DA50AE02A977405AA79` |
| `build/reports/p2/representation-limits/device-memory-run-05.csv` | 58,759 | `F8CAFA68257B5C211D79C9A608249AE7536A71E95A92E0AE24758B1555B6807D` |
| `build/reports/p2/representation-limits/device-memory.csv` | 16,655 | `5536A65C32FB56F80BEE3F23AE51A60E3F8B9534F80C7CEB628F6B21E5BFBBA9` |

The command input must retain schema `nene-pixel-p2-android-final-command-measurement-v1`, valid
identity, five fixed workload names in their recorded order, exactly 200 samples per workload,
local indices 1 through 200, and global indices 1 through 1,000. For each workload, the audit sorts
all 200 non-negative `art_allocated_bytes_delta` values and selects nearest-rank p95 at one-based
rank `ceil(0.95 * 200) = 190`; zero is a valid unchanged ART counter while any negative value
invalidates the audit. There is no interpolation, clamp, exclusion, or outlier removal. The operation
allocation is the maximum of those five workload p95 values.

The memory inputs must remain exactly five independently checksummed raw invocations plus their
accepted aggregate. For each raw invocation, the audit selects the one `retained_post_gc` checkpoint
and requires its Java heap used and committed bytes and total PSS to be positive. The steady-live
heap operand is the maximum retained Java heap used value across all five runs, not the median. Raw
file names, byte lengths, and SHA-256 values must agree with all corresponding aggregate run rows.

The command source is `16fa62101e6a76d64bab6cfe809b3e49f5ba7afa`; the memory source is
`a252bd5d733a408c0d3577a3580d42a8f57ea832`. Because these differ, the audit requires an empty
production-source diff between them over `core/domain/src/main`, `core/pixel-engine/src/main`,
`core/application/src/main`, `presentation/compose/src/main`, and `app/android/src/main`. It also
requires identical physical profile, build fingerprint, security patch, `Runtime.maxMemory`, and
memory class across every input. A mismatch invalidates the audit rather than being normalized.

The pass condition is evaluated with overflow-safe integer arithmetic as
`10 * (maximum retained heap + maximum workload p95 allocation) <= 7 * Runtime.maxMemory`. The new
immutable output is
`build/reports/p2/representation-limits/device-current-peak-headroom.csv`, schema
`nene-pixel-p2-android-current-peak-headroom-v1`. It retains every input identity and checksum, all
five workload p95 values, all five retained-heap values, the two selected maxima, both integer sides,
and the pass/fail result. An independent `Import-Csv` recomputation must reproduce every derived
field with zero mismatch before the result is recorded. A valid pass applies only to the named
current-canonical 256-square command/history combination; candidate peak headroom, larger canvases,
the complete workload matrix, post-cap churn, and compositor evidence remain separate.

### Current-canonical peak-headroom cross-artifact result

The contract was fixed in commit `4151a93bebc3a83e03336699b09767bdc7805fbb`. Before any output
was written, source validation showed that the accepted command schema intentionally permits a zero
ART allocation delta. Commit `50480ba9da38ac8dffb5cb419b2ccfe8d988d359` therefore corrected the
input domain from positive to non-negative without changing the percentile, selected maxima, 70%
threshold, or any measured value. The audit then completed over the seven immutable inputs.

All input bytes and hashes matched the fixed table. Command rows retained the exact five-workload
order, 200 local indices per workload, and global indices 1 through 1,000. Forty-seven allocation
deltas were zero and none were negative. Nearest-rank p95 at rank 190 produced:

| Workload | p95 ART allocation bytes |
| --- | ---: |
| `sparse_apply_stroke` | 6,914,048 |
| `dense_apply_stroke` | 21,106,688 |
| `dense_same_color_no_op` | 5,390,336 |
| `dense_undo` | 8,781,824 |
| `dense_redo` | 23,859,200 |

Every memory raw contained exactly one positive `retained_post_gc` observation, and every raw
identity matched its aggregate run row:

| Run | Retained heap used bytes | Committed bytes | Total PSS KiB |
| ---: | ---: | ---: | ---: |
| 1 | 43,185,968 | 76,642,096 | 125,891 |
| 2 | 43,185,968 | 76,642,096 | 125,955 |
| 3 | 43,190,064 | 76,646,192 | 125,964 |
| 4 | 43,185,968 | 76,642,096 | 125,333 |
| 5 | 43,185,968 | 76,642,096 | 125,815 |

The named profile, build fingerprint, security patch, 268,435,456-byte runtime maximum, and 256 MiB
memory class matched across all inputs. The required production `src/main` diff between the two
source commits was empty. The conservative selected operands were 43,190,064 bytes of retained heap
and 23,859,200 bytes of operation p95 allocation, giving 67,049,264 bytes. Overflow-safe integer
evaluation was:

`10 * 67,049,264 = 670,492,640 <= 1,879,048,192 = 7 * 268,435,456`

The current-canonical 256-square peak-headroom condition therefore passes. Independent
`Import-Csv` recomputation found zero mismatches across the 29-row derived artifact. The memory
aggregate remains unchanged with its own `peak_headroom_status=not_evaluated`; this result is the
separate cross-artifact claim fixed above.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-current-peak-headroom.csv` | 16,018 | `5CFC4BE4D01D23185AE7EB85A26B81D84AA884E2104ED0DA1BEFFF8FB987E905` |

This pass does not cover a candidate, a larger canvas, the complete workload matrix, post-cap churn,
or compositor presentation.

### Pre-fixed refreshed-build current-canonical 64-square final-command tail contract

The next command-only physical slice refreshes the 64 x 64 current-canonical measured point on the
current source and build. The 64-square size is selected for this tail rerun because it is the
largest already-fixed preliminary screening size at which all five command workloads passed the
latency targets. It is one named current-path measurement only: it does not select a representation,
establish a product cap, or interpolate a result beyond 64 x 64.

Implementation must parameterize the one existing final-command harness path. It must not copy the
runner, workload execution, report, checkpoint, or correctness route into a second implementation.
Because column meanings and serialization remain unchanged, the schema remains
`nene-pixel-p2-android-final-command-measurement-v1`. The fixed identity is
`output_identity=device-core-current-64-square`, candidate
`current-canonical-command-64-square`, and run index 1. The caller supplies one exact full
40-character source commit, and that commit must be the source of both the installed app APK and
test APK. The tool artifact fixed below records each APK path, byte length, and SHA-256 before
installation.

The run is valid only on the already-fixed refreshed build fingerprint
`ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94110:user/release-keys`, security patch
2026-06-05, display mode 1 at 1200 x 1920 and 90 Hz, `Runtime.maxMemory()` 268,435,456 bytes, and
`ActivityManager.memoryClass` 256 MiB. Those exact values must appear in the raw metadata or
checkpoints as applicable; the stable profile ID alone cannot substitute for any of them.

The fixed build inputs and invocation are app APK
`app/android/build/outputs/apk/debug/android-debug.apk`, test APK
`app/android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk`, application
package `io.github.hideyukimori.nenepixel`, instrumentation package
`io.github.hideyukimori.nenepixel.test`, runner `androidx.test.runner.AndroidJUnitRunner`, and test
class `io.github.hideyukimori.nenepixel.measurement.P2AndroidFinalCommandMeasurementTest`. The
runner arguments are exactly `nene.p2.physicalProfileId=NENE-P2-ALLDOCUBE-IPL80MP-A16-API36`,
`nene.p2.warmupIterations=5`, `nene.p2.sampleCount=200`,
`nene.p2.candidateId=current-canonical-command-64-square`, `nene.p2.runIndex=1`, and
`nene.p2.sourceCommit=<the fixed full source commit>`; no auxiliary-emulator argument is present.

The production `src/main` diff between the accepted prior 256-square command source
`16fa62101e6a76d64bab6cfe809b3e49f5ba7afa` and the new source commit must be empty across
`core/domain/src/main`, `core/pixel-engine/src/main`, `core/application/src/main`,
`presentation/compose/src/main`, and `app/android/src/main`. A non-empty production diff invalidates
the batch rather than changing the meaning of the current-canonical comparison.

The workload and sample contract is fixed as follows:

- workload order is `sparse_apply_stroke`, `dense_apply_stroke`,
  `dense_same_color_no_op`, `dense_undo`, and `dense_redo`, all at exactly 64 x 64;
- each workload receives five untimed warmups and 200 measured samples; every warmup and sample
  creates a fresh `PreparedCommandWorkload` and invokes exactly one `CommandGateway.execute`;
- one post-GC process baseline is captured before measured samples; the existing ART runtime-stat,
  Java-heap, total/dalvik/native/other PSS, private/shared dirty, retained gateway/document/one-level-
  history, and correctness boundaries remain unchanged from the 256-square v1 route;
- workload preparation, post-GC heap/PSS capture, and correctness verification remain outside the
  direct `CommandGateway.execute` latency interval; no different timing boundary is introduced;
- local sample indices are one-based 1 through 200 for every workload, and global sample indices
  are unique and one-based 1 through 1,000 in the fixed workload order; and
- every workload uses all 200 rows for nearest-rank p95 at rank 190 and p99 at rank 198, with no
  interpolation, clamp, exclusion, outlier removal, or row discard.

The physical checkpoint plan also remains the single existing final-command plan: one checkpoint
before samples, one after each 25th global sample, and a final `after_samples` checkpoint after all
rows. The artifact therefore contains exactly 42 ordered checkpoint rows, including distinct
`after_1000` and `after_samples` observations. The batch is invalid if the active display mode or
physical dimensions change, refresh differs from the fixed 90 Hz profile, device-wide thermal
status exceeds 1, power saving is enabled, the display is not interactive, USB power is lost, or
battery data is unavailable at any checkpoint.

Every sample must retain the v1 route's exact resulting `DocumentState`, complete pixel content,
revision, history availability, result kind, applied `ChangeSet` revisions and render invalidation,
or no-effective-change rejection and unchanged state identity, as applicable. Any mismatch fails
the batch. The direct core-latency condition passes only if every workload has p95 <= 8.0 ms and
p99 <= 16.67 ms. The blocking-GC condition passes only if at least 950 of all 1,000 raw operations
have zero ART blocking-GC-count increment. GC increments remain per-sample ART observations; the
supporting logcat is not assigned a separate GC-event-zero rule.

The sparse row has `position_count=64`; every dense row has `position_count=4096`. Sparse apply and
dense apply are `applied` at revision 1 with `undo_available`, public `ChangeSet` revisions 0 to 1,
and invalidation `(0,0,64,64)`. Dense undo is `applied` at revision 0 with `redo_available`, public
revisions 1 to 0, and the same full invalidation. Dense redo is `applied` at revision 1 with
`undo_available`, public revisions 0 to 1, and the same full invalidation. Dense same-color no-op
is `rejected_no_effective_change` at revision 0 with history `none`, no public `ChangeSet` or
invalidation, and unchanged state identity. These values are contract data, not values inherited
from the former hard-coded 256-square route.

The immutable on-device target is
`files/p2-measurements/p2-android-final-command-64-square.csv`; an existing target is a failure and
is never overwritten. A successful file is copied byte-for-byte to the distinct immutable host
target `build/reports/p2/representation-limits/device-core-current-64-square.csv`. The previously
accepted `device-core.csv` remains unchanged at 661,245 bytes with SHA-256
`E2FD1D427DE191F14A8194FE4CF5CE5A929A388B7A13E439E731F0AF8180FCAD`.

Two distinct supporting host artifacts belong to the same immutable batch:

| Artifact | Fixed content |
| --- | --- |
| `build/reports/p2/representation-limits/device-core-current-64-square-tool.txt` | source commit; app/test APK paths, bytes, and SHA-256; package, runner, class, and exact arguments; physical profile/build identity; collection start/end; original and restored stay-awake value |
| `build/reports/p2/representation-limits/device-core-current-64-square-logcat.txt` | time-scoped diagnostics covering the complete instrumentation interval |

Collection first writes tool and logcat data under one unique batch staging name and leaves all
three final host targets absent. The fixed on-device CSV target must also be absent before the run.
After instrumentation, the on-device file is pulled into that staging batch. Only after the CSV,
source/build/APK identity, production diff, percentiles, checkpoints, correctness, ART conditions,
and log audit all validate are the three staged files published to their final names. All final
destinations are checked absent before the first publish, and every per-file move must preserve the
staged bytes and hash. A partial publish is rejected and its exact files are renamed with the same
`.invalid-<batch-id>` suffix; they are never treated as canonical targets. A rejected attempt also
removes only its exact on-device output after diagnostics are retained, so it cannot block a retry.

If any final host target already exists, collection fails without overwrite. The result ledger
records byte length and SHA-256 for the CSV, tool artifact, and logcat. A deterministic audit of the
scoped logcat must find zero fatal exceptions, fatal signals, ANRs, and untyped instrumentation
failures. Thermal-event-pattern matches are recorded as diagnostics only; thermal validity is
decided by the fixed checkpoint status <= 1 rule. The audit does not invent a logcat GC rule or
replace the raw per-sample ART blocking-GC calculation.

This docs-first phase does not authorize a second harness, production change, or evidence claim.
The active PR remains Draft, ADR 0005 remains `proposed`, and the existing representation,
product-limit, candidate-physical, full-matrix, compositor, P2-02, and representation-dependent
P2-04 holds remain in force.

### Refreshed-build current-canonical 64-square final-command tail result

The fixed 64-square command-only batch completed on the named physical profile. Batch
`81f5d0fb-64ec-4372-8679-5e07080bb0a6` used contract commit
`4239515ab7d8482526a78e3420f870e00e9865eb` and source commit
`3be887daa2c644c0e32fe194ec77bfbed1bd0058`. The required production `src/main` diff from prior
256-square source `16fa62101e6a76d64bab6cfe809b3e49f5ba7afa` was empty across all five fixed paths.
The app APK was 11,664,182 bytes with SHA-256
`71B732DE85D2984360CD6676184D66250D87EDA34444DB04EDA82E278BB5C796`; the test APK was
1,315,233 bytes with SHA-256
`1DE1108A01484D176858033D5305F9D0D8936CEE91E0A20DED0C25FB88B99605`.

The no-cache APK build passed in 46 seconds with all 95 tasks executed. The exact fixed
instrumentation invocation reported `OK (1 test)` in 60.807 seconds. Collection ran from
2026-09-02 20:58:18.277 JST through 21:00:20.931 JST on build `94110`, security patch 2026-06-05,
mode 1 at 1200 x 1920 and 90 Hz, runtime maximum 268,435,456 bytes, and memory class 256 MiB.

Independent `Import-Csv` recomputation found zero mismatches. The file has 53 columns and 1,088
data rows: 45 metadata, one process baseline, 42 ordered physical checkpoints, and 1,000 samples.
The workload order, 200 local indices per workload, global indices 1 through 1,000, exact 64 x 64
geometry, correctness state, full pixel content, revisions, history, results, `ChangeSet` data,
invalidations, and no-op identity all matched the fixed contract. Every checkpoint retained mode 1,
1200 x 1920, 90 Hz, thermal status 1, power saving disabled, an interactive display, USB power, and
100% battery.

Nearest-rank latency and allocation recomputation produced:

| Workload | p95 latency ns | p99 latency ns | p95 ART allocation bytes | Zero blocking-GC samples |
| --- | ---: | ---: | ---: | ---: |
| `sparse_apply_stroke` | 1,746,500 | 1,793,307 | 442,368 | 200 / 200 |
| `dense_apply_stroke` | 5,818,000 | 5,920,539 | 1,126,400 | 200 / 200 |
| `dense_same_color_no_op` | 1,418,923 | 1,443,192 | 278,528 | 200 / 200 |
| `dense_undo` | 2,437,039 | 2,494,116 | 589,824 | 200 / 200 |
| `dense_redo` | 2,316,423 | 2,380,153 | 540,672 | 200 / 200 |

All five workloads pass p95 <= 8.0 ms and p99 <= 16.67 ms. All 1,000 operations had zero
blocking-GC-count increment, exceeding the required 950, and every observed GC count and time delta
was zero. All allocation deltas were positive and matched their before/after counters. The baseline
Java heap was 2,217,744 used and 10,508,048 committed bytes with 85,528 KiB total PSS. The maximum
sample observations were 2,803,504 used and 11,093,808 committed bytes with 85,987 KiB total PSS;
these heap/PSS values are diagnostics only and are not a retained-memory or peak-headroom result.

The scoped 1,592-line log covers 20:58:18.030 through 21:00:20.731 JST. Deterministic audit found
zero fatal exceptions, fatal signals, ANRs, untyped instrumentation failures, and thermal-pattern
matches. The exact on-device CSV and collection-only device log were removed after verified
extraction. `stay_on_while_plugged_in` was restored from the temporary value 2 to its original value
0, and pre-run and post-run overall thermal status were both 1. The previously accepted
`device-core.csv` remained unchanged at 661,245 bytes with SHA-256
`E2FD1D427DE191F14A8194FE4CF5CE5A929A388B7A13E439E731F0AF8180FCAD`.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-core-current-64-square.csv` | 649,469 | `F9CDEDA09E7901BACCC58BC39094FEC5699FBC0ACBC11C91C40502EDFC3932C6` |
| `build/reports/p2/representation-limits/device-core-current-64-square-tool.txt` | 4,279 | `D0CAF501AB89987EB499D164CDF8A48CDAE5F413BD761C22331858F8A3B267D3` |
| `build/reports/p2/representation-limits/device-core-current-64-square-logcat.txt` | 220,962 | `D1515A3B10252D0B36B174407F025D700745B12890AD6C33097529E3DA70B79F` |

This is one passing measured point for the current canonical command path at exactly 64 x 64 and
one run. It does not select a representation, establish or interpolate a product maximum, cover a
candidate, prove retained memory or PSS, cover the complete physical matrix, or claim render or
compositor timing.

### Pre-fixed refreshed-build current-canonical 128-square final-command tail contract

Before any 128 x 128 observation, the next command-only slice is fixed as one additional explicit
current-canonical measured point between the passing 64-square and failing 256-square command
tails. It reuses the single parameterized final-command runner, measurement, checkpoint,
correctness, and report path; it must not copy or fork that harness. The schema remains
`nene-pixel-p2-android-final-command-measurement-v1`, with
`output_identity=device-core-current-128-square`, candidate
`current-canonical-command-128-square`, and run index 1. This point cannot be interpolated to
another size or treated as a representation choice, product cap, or maximum.

The caller supplies one exact full 40-character source commit that built both installed APKs. The
fixed inputs remain app APK `app/android/build/outputs/apk/debug/android-debug.apk`, test APK
`app/android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk`, application
package `io.github.hideyukimori.nenepixel`, instrumentation package
`io.github.hideyukimori.nenepixel.test`, runner `androidx.test.runner.AndroidJUnitRunner`, and test
class `io.github.hideyukimori.nenepixel.measurement.P2AndroidFinalCommandMeasurementTest`. The
exact runner arguments are
`nene.p2.physicalProfileId=NENE-P2-ALLDOCUBE-IPL80MP-A16-API36`,
`nene.p2.warmupIterations=5`, `nene.p2.sampleCount=200`,
`nene.p2.candidateId=current-canonical-command-128-square`, `nene.p2.runIndex=1`, and
`nene.p2.sourceCommit=<the fixed full source commit>`; no auxiliary-emulator argument is present.
The tool artifact records both APK paths, byte lengths, and SHA-256 values before installation.

The run is valid only with build fingerprint
`ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94110:user/release-keys`, security patch
2026-06-05, display mode 1 at 1200 x 1920 and 90 Hz, `Runtime.maxMemory()` 268,435,456 bytes, and
`ActivityManager.memoryClass` 256 MiB. These exact values must appear in metadata or checkpoints.
The production `src/main` diff from the accepted 64-square source
`3be887daa2c644c0e32fe194ec77bfbed1bd0058` to the new source commit must be empty across
`core/domain/src/main`, `core/pixel-engine/src/main`, `core/application/src/main`,
`presentation/compose/src/main`, and `app/android/src/main`; otherwise the batch is invalid.

The workload order remains `sparse_apply_stroke`, `dense_apply_stroke`,
`dense_same_color_no_op`, `dense_undo`, and `dense_redo`, all at exactly 128 x 128. Each workload
has five untimed warmups and 200 measured samples, and every iteration creates a fresh
`PreparedCommandWorkload` and makes exactly one `CommandGateway.execute` call. The unchanged v1
boundary captures one post-GC process baseline before measured samples; workload preparation,
heap/PSS and ART observations, and correctness checks stay outside direct execution latency. Local
indices are 1 through 200 per workload and global indices are unique 1 through 1,000 in fixed
order. Nearest-rank p95 uses rank 190 and p99 rank 198 from all 200 rows with no interpolation,
clamp, exclusion, outlier removal, or discard.

The unchanged checkpoint plan records `before_samples`, every 25th global sample, and distinct
`after_1000` and `after_samples` rows, for exactly 42 ordered checkpoints. A display mode or
physical-dimension change, refresh other than 90 Hz, device-wide thermal status above 1, enabled
power saving, non-interactive display, lost USB power, or unavailable battery data invalidates the
batch. The artifact must contain exactly 45 metadata rows, one process baseline, 42 checkpoints,
and 1,000 samples under the existing schema contract.

Every sample retains the v1 exact state, complete pixels, revision, history, result kind, public
`ChangeSet`, invalidation, and no-effect identity checks. Sparse rows have `position_count=128` and
dense rows `position_count=16384`. Sparse and dense apply are `applied` at revision 1 with
`undo_available`, public revisions 0 to 1, and invalidation `(0,0,128,128)`; undo is `applied` at
revision 0 with `redo_available` and public revisions 1 to 0; redo is `applied` at revision 1 with
`undo_available` and public revisions 0 to 1. Undo and redo use the same full invalidation. The
same-color no-op is `rejected_no_effective_change` at revision 0 with history `none`, no public
`ChangeSet` or invalidation, and unchanged state identity. Any mismatch invalidates the batch.
Core latency passes only when every workload has p95 <= 8.0 ms and p99 <= 16.67 ms. Blocking GC
passes only when at least 950 of all 1,000 operations have zero ART blocking-GC-count increment;
logcat neither replaces nor adds a separate GC condition.

The Android-test publication mode is `FailIfExists`. Its distinct immutable on-device CSV is
`files/p2-measurements/p2-android-final-command-128-square.csv`; it must be absent before the run
and is never overwritten. The distinct immutable host targets are:

| Artifact | Fixed content |
| --- | --- |
| `build/reports/p2/representation-limits/device-core-current-128-square.csv` | byte-for-byte pulled v1 command report |
| `build/reports/p2/representation-limits/device-core-current-128-square-tool.txt` | batch/source/APK/package/runner/argument/profile/build/time/stay-awake identity |
| `build/reports/p2/representation-limits/device-core-current-128-square-logcat.txt` | time-scoped diagnostics covering the complete instrumentation interval |

One unique batch stages all three host files while every final target remains absent. Publication
is allowed only after an independent deterministic audit validates CSV schema, identity, rows,
ordering, checkpoints, exact profile/build/APKs, production diff, percentiles, ART conditions,
correctness, log scope, and zero fatal exceptions, fatal signals, ANRs, or untyped instrumentation
failures. Thermal log matches are diagnostic; checkpoints remain authoritative. Before the first
move, all three final targets are checked absent; each no-overwrite move must preserve staged bytes
and SHA-256. A partial publication is rejected and every moved member is renamed with the same
`.invalid-<batch-id>` suffix. A rejected attempt removes only its exact on-device output after
diagnostics are retained. After successful hash-matched extraction, exact temporary device files
are removed and the recorded original stay-awake value is restored and verified.

All accepted 64-square and 256-square artifacts remain immutable. In particular,
`device-core-current-64-square.csv`, its tool file, and its logcat retain respectively
649,469 / `F9CDEDA09E7901BACCC58BC39094FEC5699FBC0ACBC11C91C40502EDFC3932C6`,
4,279 / `D0CAF501AB89987EB499D164CDF8A48CDAE5F413BD761C22331858F8A3B267D3`, and
220,962 / `D1515A3B10252D0B36B174407F025D700745B12890AD6C33097529E3DA70B79F`
bytes/SHA-256, while `device-core.csv` retains 661,245 bytes and SHA-256
`E2FD1D427DE191F14A8194FE4CF5CE5A929A388B7A13E439E731F0AF8180FCAD`.

This docs-first contract changes no production/API/dependency/Gradle/schema path. Until a valid
audited result is recorded, it makes no PASS claim. Even a passing result covers only this one
current-canonical 128-square command run: ADR 0005 remains `proposed`, the PR remains Draft, and
representation, product-limit, retained-memory/PSS, candidate-physical, full-matrix, render,
compositor, P2-02, and representation-dependent P2-04 holds remain in force.

### Refreshed-build current-canonical 128-square final-command tail result

The fixed 128-square command-only batch completed on the named physical profile. Batch
`1514ad25-78f2-4680-95f3-c1f3931ef563` used contract commit
`b53cb780da84fc41a8ab67df457d31625c345dac` and source commit
`af6e954cc343593e507b238a0fd121451c5017e6`. The required production `src/main` diff from accepted
64-square source `3be887daa2c644c0e32fe194ec77bfbed1bd0058` was empty across all five fixed paths. The app APK
was 11,664,182 bytes with SHA-256
`71B732DE85D2984360CD6676184D66250D87EDA34444DB04EDA82E278BB5C796`; the test APK was 1,316,261
bytes with SHA-256 `4BF8D3E73E79117FF7A13EB984303521C1692FEFD1DAA9DD998743C5556F35C6`.

The no-cache APK build passed in 46 seconds with all 95 tasks executed. The exact fixed
instrumentation invocation contained no auxiliary-emulator argument and reported `OK (1 test)` in
90.287 seconds. Collection ran from 2026-09-02 22:10:11.1727949 JST through 22:11:43.0330263 JST
on build `94110`, security patch 2026-06-05, mode 1 at 1200 x 1920 and 90 Hz, runtime maximum
268,435,456 bytes, and memory class 256 MiB.

Independent `Import-Csv` recomputation found zero structural or semantic mismatches. The artifact
has 53 columns and 1,088 data rows: 45 metadata, one process baseline, 42 ordered physical
checkpoints, and 1,000 samples. The workload order, 200 local indices per workload, global indices
1 through 1,000, exact 128 x 128 geometry, correctness hashes, outcomes, revisions, history,
`ChangeSet` data, full invalidations, and no-op identity all matched the fixed contract. Every
checkpoint retained mode 1, 1200 x 1920, 90 Hz, thermal status 1, power saving disabled, an
interactive display, USB power, and 100% battery.

Nearest-rank recomputation produced:

| Workload | Minimum ns | p95 latency ns | p99 latency ns | Maximum ns | p95 ART allocation bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| `sparse_apply_stroke` | 5,245,961 | 5,708,807 | 5,762,308 | 5,937,462 | 1,769,472 |
| `dense_apply_stroke` | 21,379,270 | 22,413,000 | 24,921,808 | 27,340,153 | 4,775,936 |
| `dense_same_color_no_op` | 5,212,231 | 5,526,923 | 5,590,962 | 5,652,500 | 1,130,496 |
| `dense_undo` | 8,627,923 | 9,182,346 | 9,290,808 | 9,721,731 | 2,260,992 |
| `dense_redo` | 8,042,884 | 11,540,615 | 13,355,077 | 13,475,769 | 2,260,992 |

The pre-fixed core-latency condition fails: `dense_apply_stroke` exceeds both limits, while
`dense_undo` and `dense_redo` exceed the p95 <= 8.0 ms limit. The outcome was recorded without
changing the p95 rank 190, p99 rank 198, or thresholds. All 1,000 operations had zero
blocking-GC-count increment, so the blocking-GC condition passes; all four observed GC count/time
deltas were zero for all 1,000 operations. Eleven `dense_redo` allocation deltas were zero and all
other allocation observations were positive; every value remained nonnegative and matched its
before/after counters as required.

The baseline Java heap was 2,217,744 used and 10,508,048 committed bytes with 85,806 KiB total
PSS. Maximum sample observations were 3,737,392 used and 12,027,696 committed bytes with 87,183
KiB total PSS; the minimum sample PSS was 85,615 KiB. These heap/PSS values are diagnostics only,
not a retained-memory or peak-headroom result.

The scoped 1,161-line log covers 22:10:11.197 through 22:11:42.979 JST and exactly matches the
declared-boundary extraction from the retained staging diagnostic. Deterministic audit found zero
fatal exceptions, fatal signals, ANRs, untyped instrumentation failures, and thermal-pattern
matches. The exact on-device CSV was removed after hash-matched extraction; no device log temp was
created. `stay_on_while_plugged_in` was restored from temporary value 2 to original value 0, the
device was returned from its temporary awake state to its original asleep state, and pre-run and
post-run overall thermal status were both 1. The accepted 64-square artifacts and prior
`device-core.csv` retained every pre-fixed byte length and SHA-256.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-core-current-128-square.csv` | 658,096 | `0A075A06E4F87AE3B4AF3D75DA70C17A0F86AD797D665B7F1B91EEF9A5357D11` |
| `build/reports/p2/representation-limits/device-core-current-128-square-tool.txt` | 6,008 | `4AA6E81B7A283C055E05259B744EDFF94EE6AB89AB9F07BEB96C04DA0509A958` |
| `build/reports/p2/representation-limits/device-core-current-128-square-logcat.txt` | 160,933 | `3E678B5AF6449EF33A5E2801E3DBFECD25BF2F455EAFCB5F5BB9F78EC368C12D` |

Among the three explicit current-canonical final-command points, 64 x 64 is the largest passing
point and both 128 x 128 and 256 x 256 fail their pre-fixed core-latency condition. This does not
interpolate an unmeasured boundary or establish a representation choice, supported canvas maximum,
or product cap. It also does not cover a candidate, retained memory/PSS, the complete physical
matrix, render timing, or compositor presentation.

### Pre-fixed refreshed-build current-canonical 4K rectangular final-command tails contract

Before either rectangular observation, the next command-only slice is fixed as the two 4,096-pixel
rectangles already present beside `64x64` in the canonical host shape matrix: `16x256` and
`256x16`. The host `P2CandidatePathKind.Diagonal` definition produces exactly
`min(width,height)` positions `(i,i)`, so both rectangular sparse workloads use the 16 positions
`(0,0)` through `(15,15)`. This is an aspect-ratio comparison at one fixed area, not an
interpolation from a square or a claim about another shape.

Implementation must generalize the one existing final-command plan, workload, measurement,
correctness, report, validation, and publication path from a square `canvasEdge` to an explicit
`shape(width,height)`. It must not copy or fork any of those paths. Existing 64 x 64, 128 x 128,
and 256 x 256 candidate routing, geometry, output identity, publication policy, and observable
behavior remain unchanged. The schema remains
`nene-pixel-p2-android-final-command-measurement-v1`; there is no production, API, dependency,
Gradle, or schema change.

Each rectangle is a separate immutable run-1 identity:

| Shape | Candidate ID | Output identity | Immutable on-device CSV |
| --- | --- | --- | --- |
| 16 x 256 | `current-canonical-command-16x256-rectangle` | `device-core-current-16x256-rectangle` | `files/p2-measurements/p2-android-final-command-16x256-rectangle.csv` |
| 256 x 16 | `current-canonical-command-256x16-rectangle` | `device-core-current-256x16-rectangle` | `files/p2-measurements/p2-android-final-command-256x16-rectangle.csv` |

The two shapes must never share a batch, report, staged file, final artifact, percentile
calculation, PASS/FAIL gate, or cleanup target. Completion or failure of one shape has no effect on
the other shape's result. The Android-test publication policy for each is `FailIfExists`; every
on-device output must be absent before its run and is never overwritten.

For each shape, the caller supplies one exact full 40-character source commit that built both
installed APKs. The fixed inputs are app APK
`app/android/build/outputs/apk/debug/android-debug.apk`, test APK
`app/android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk`, application
package `io.github.hideyukimori.nenepixel`, instrumentation package
`io.github.hideyukimori.nenepixel.test`, runner `androidx.test.runner.AndroidJUnitRunner`, and test
class `io.github.hideyukimori.nenepixel.measurement.P2AndroidFinalCommandMeasurementTest`. Each
shape uses exactly these runner arguments, substituting only its fixed candidate ID:

- `nene.p2.physicalProfileId=NENE-P2-ALLDOCUBE-IPL80MP-A16-API36`;
- `nene.p2.warmupIterations=5`;
- `nene.p2.sampleCount=200`;
- `nene.p2.candidateId=current-canonical-command-16x256-rectangle` or
  `nene.p2.candidateId=current-canonical-command-256x16-rectangle`, matching that batch;
- `nene.p2.runIndex=1`; and
- `nene.p2.sourceCommit=<that batch's fixed full source commit>`.

No auxiliary-emulator argument is present. The shape-specific tool artifact records the exact
source commit and both APK paths, byte lengths, and SHA-256 values before installation. The
production `src/main` diff from source commit
`af6e954cc343593e507b238a0fd121451c5017e6` to each new source commit must be empty across
`core/domain/src/main`, `core/pixel-engine/src/main`, `core/application/src/main`,
`presentation/compose/src/main`, and `app/android/src/main`; otherwise that batch is invalid.

Each run is valid only with build fingerprint
`ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94110:user/release-keys`, security patch
2026-06-05, display mode 1 at 1200 x 1920 and 90 Hz, `Runtime.maxMemory()` 268,435,456 bytes, and
`ActivityManager.memoryClass` 256 MiB. These exact values must appear in metadata or checkpoints.
The metadata `canvas` value must encode the exact ordered `widthxheight`, and every sample must
carry that width and height independently in the existing `canvas_width` and `canvas_height`
columns. An edge swap, square fallback, or disagreement with the candidate identity invalidates
the batch.

The workload order is `sparse_apply_stroke`, `dense_apply_stroke`,
`dense_same_color_no_op`, `dense_undo`, and `dense_redo`. Each workload has five untimed warmups
and 200 measured samples. Every iteration creates a fresh `PreparedCommandWorkload` and makes
exactly one `CommandGateway.execute` call. The unchanged v1 boundary captures one post-GC process
baseline before measured samples; workload preparation, heap/PSS and ART observations, and
correctness checks remain outside direct execution latency. Local indices are 1 through 200 per
workload and global indices are unique 1 through 1,000 in fixed order. Nearest-rank p95 uses rank
190 and p99 rank 198 from all 200 rows with no interpolation, clamp, exclusion, outlier removal,
or discard.

Both sparse workloads have `position_count=16`, using only the canonical `(i,i)` positions for
`i=0..15`. Their applied result is revision 1 with `undo_available`, public revisions 0 to 1, and
affected invalidation `(0,0,16,16)` for both shapes. That sparse region is not full-canvas
invalidation for either rectangle. Each dense, same-color no-op, undo, and redo workload has
`position_count=4096`. Dense apply is `applied` at revision 1 with `undo_available` and public
revisions 0 to 1. Undo is `applied` at revision 0 with `redo_available` and public revisions 1 to
0; redo is `applied` at revision 1 with `undo_available` and public revisions 0 to 1. Dense apply,
undo, and redo use full invalidation `(0,0,16,256)` for the 16 x 256 batch and
`(0,0,256,16)` for the 256 x 16 batch. The same-color no-op is
`rejected_no_effective_change` at revision 0 with history `none`, no public `ChangeSet`, no
invalidation, and unchanged state identity.

Every sample retains the v1 exact state, complete pixels, revision, history, result kind, public
`ChangeSet`, invalidation, and no-effect identity checks. Sparse verification must also prove that
only the 16 diagonal pixels changed; dense verification covers all 4,096 positions. Any position,
pixel, ordering, count, revision, history, result, `ChangeSet`, invalidation, or identity mismatch
invalidates only that shape's batch.

The existing physical checkpoint plan applies separately to each report: `before_samples`, every
25th global sample through `after_1000`, and a distinct `after_samples`, for exactly 42 ordered
checkpoints. Each artifact contains exactly 45 metadata rows, one process baseline, 42
checkpoints, and 1,000 samples. A display mode or physical-dimension change, refresh other than
90 Hz, device-wide thermal status above 1, enabled power saving, non-interactive display, lost USB
power, or unavailable battery data invalidates that batch. Core latency passes per shape only when
every workload has p95 <= 8.0 ms and p99 <= 16.67 ms. Blocking GC passes per shape only when at
least 950 of its 1,000 operations have zero ART blocking-GC-count increment; logcat neither
replaces nor adds a separate GC condition.

The distinct immutable host targets are:

| Shape | CSV | Tool identity | Scoped logcat |
| --- | --- | --- | --- |
| 16 x 256 | `build/reports/p2/representation-limits/device-core-current-16x256-rectangle.csv` | `build/reports/p2/representation-limits/device-core-current-16x256-rectangle-tool.txt` | `build/reports/p2/representation-limits/device-core-current-16x256-rectangle-logcat.txt` |
| 256 x 16 | `build/reports/p2/representation-limits/device-core-current-256x16-rectangle.csv` | `build/reports/p2/representation-limits/device-core-current-256x16-rectangle-tool.txt` | `build/reports/p2/representation-limits/device-core-current-256x16-rectangle-logcat.txt` |

For each shape, one unique batch stages its three host files while all three corresponding final
targets remain absent. Publication is allowed only after an independent deterministic audit of
that one shape validates the CSV schema, identity, row counts, ordering, checkpoints, exact
profile, source and APK identity, production diff, percentiles, ART conditions, correctness,
shape-specific invalidation, log scope, and zero fatal exceptions, fatal signals, ANRs, or untyped
instrumentation failures. Thermal log matches remain diagnostic; checkpoints are authoritative.
Before the first move, all three shape-specific final targets are checked absent. Each
no-overwrite move must preserve staged bytes and SHA-256. A partial publication is rejected and
every moved member of that batch is renamed with one shared `.invalid-<batch-id>` suffix.

A rejected attempt removes only its exact shape-specific on-device output after diagnostics are
retained. After successful hash-matched extraction, only that batch's exact temporary device files
are removed and the recorded original stay-awake value is restored and verified. Cleanup or
quarantine must not address the other rectangle or any accepted artifact.

All accepted 64-square, 128-square, and 256-square artifacts remain immutable. In particular, the
64-square CSV/tool/logcat retain respectively 649,469 /
`F9CDEDA09E7901BACCC58BC39094FEC5699FBC0ACBC11C91C40502EDFC3932C6`, 4,279 /
`D0CAF501AB89987EB499D164CDF8A48CDAE5F413BD761C22331858F8A3B267D3`, and 220,962 /
`D1515A3B10252D0B36B174407F025D700745B12890AD6C33097529E3DA70B79F` bytes/SHA-256. The
128-square CSV/tool/logcat retain respectively 658,096 /
`0A075A06E4F87AE3B4AF3D75DA70C17A0F86AD797D665B7F1B91EEF9A5357D11`, 6,008 /
`4AA6E81B7A283C055E05259B744EDFF94EE6AB89AB9F07BEB96C04DA0509A958`, and 160,933 /
`3E678B5AF6449EF33A5E2801E3DBFECD25BF2F455EAFCB5F5BB9F78EC368C12D` bytes/SHA-256. The
256-square `device-core.csv` retains 661,245 bytes and SHA-256
`E2FD1D427DE191F14A8194FE4CF5CE5A929A388B7A13E439E731F0AF8180FCAD`.

These claims and holds are fixed before either observation. Until its own valid audited result is
recorded, neither rectangle makes a PASS claim. A passing rectangle covers only one
current-canonical 4K command run and does not decide the other rectangle. ADR 0005 remains
`proposed`, the PR remains Draft, and representation, product-limit, retained-memory/PSS,
candidate-physical, complete-physical-matrix, render, compositor, P2-02, and
representation-dependent P2-04 holds remain in force regardless of either outcome.

### Refreshed-build current-canonical 4K rectangular final-command tails result

Both pre-fixed rectangular command-only batches completed on the named physical profile. The
16 x 256 batch `83da6a03-e527-468a-85a4-f5078f323f5b` and the 256 x 16 batch
`85065128-24bd-4b70-b15b-170225853de8` used contract commit
`1f8584833ade00e4c67cbe6d0bc7866e54a3c873` and source commit
`b8674a44c022630ab2925dcdda0780a598a7b4e8`. The required production `src/main` diff from
accepted 128-square source `af6e954cc343593e507b238a0fd121451c5017e6` was empty across all
five fixed paths. The app APK was 11,664,182 bytes with SHA-256
`71B732DE85D2984360CD6676184D66250D87EDA34444DB04EDA82E278BB5C796`; the test APK was
1,321,497 bytes with SHA-256
`97F4737A7B3327EE662FFA2E867D6022754229D8F940DF8001D87B4A63260226`.

The no-cache APK build passed in 1 minute 4 seconds with all 95 tasks executed. Both exact fixed
instrumentation invocations contained no auxiliary-emulator argument and reported `OK (1 test)`.
The per-shape collection identity was:

| Shape | Instrumentation seconds | Collection start JST | Collection end JST |
| --- | ---: | --- | --- |
| 16 x 256 | 61.793 | 2026-09-02 23:20:11.4154547 | 2026-09-02 23:21:14.7647200 |
| 256 x 16 | 61.169 | 2026-09-02 23:27:43.8065050 | 2026-09-02 23:28:46.5966794 |

Independent `Import-Csv` recomputation and both independent publication audits found zero
structural or semantic mismatches, with high/medium/low finding counts all zero for each batch.
Each artifact has 53 columns and 1,088 data rows: 45 metadata, one process baseline, 42 ordered
physical checkpoints, and 1,000 samples. The fixed workload order, 200 local indices per
workload, global indices 1 through 1,000, shape-specific geometry, position counts, correctness
hashes, outcomes, revisions, history, `ChangeSet` data, invalidations, and no-op identity all
matched the contract. Every checkpoint in both batches retained mode 1, 1200 x 1920, 90 Hz,
thermal status 1, power saving disabled, an interactive display, USB power, and 100% battery.

Nearest-rank recomputation for the 16 x 256 batch produced:

| Workload | Minimum ns | p95 latency ns | p99 latency ns | Maximum ns | p95 ART allocation bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| `sparse_apply_stroke` | 1,369,846 | 1,659,538 | 1,704,961 | 1,816,270 | 442,368 |
| `dense_apply_stroke` | 5,876,116 | 6,220,308 | 6,386,616 | 6,433,346 | 1,224,704 |
| `dense_same_color_no_op` | 1,522,462 | 1,694,153 | 1,730,654 | 1,754,653 | 311,296 |
| `dense_undo` | 2,173,616 | 2,392,808 | 2,428,077 | 2,460,654 | 589,824 |
| `dense_redo` | 2,114,077 | 2,363,385 | 2,385,846 | 2,451,500 | 540,672 |

Nearest-rank recomputation for the 256 x 16 batch produced:

| Workload | Minimum ns | p95 latency ns | p99 latency ns | Maximum ns | p95 ART allocation bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| `sparse_apply_stroke` | 1,381,500 | 1,853,077 | 6,431,423 | 6,743,462 | 442,368 |
| `dense_apply_stroke` | 5,576,000 | 5,861,538 | 5,989,808 | 6,760,346 | 1,191,936 |
| `dense_same_color_no_op` | 1,238,423 | 1,403,192 | 1,444,231 | 1,661,615 | 278,528 |
| `dense_undo` | 2,204,385 | 2,390,769 | 2,447,731 | 2,469,731 | 589,824 |
| `dense_redo` | 2,096,923 | 2,327,539 | 2,546,654 | 3,357,731 | 540,672 |

Every workload in both batches passes p95 <= 8.0 ms and p99 <= 16.67 ms. Each batch also had
1,000 / 1,000 operations with zero blocking-GC-count increment, so both blocking-GC conditions
pass; all four observed GC count/time deltas were zero for every sample. Neither batch had a zero
allocation observation, and every allocation delta matched its before/after counters.

For 16 x 256, the baseline Java heap was 2,221,840 used and 10,512,144 committed bytes with
85,727 KiB total PSS. Maximum sample observations were 2,803,504 used and 11,093,808 committed
bytes with 86,184 KiB total PSS; minimum sample PSS was 84,717 KiB. For 256 x 16, the baseline
values were 2,221,840 used, 10,512,144 committed, and 85,795 KiB total PSS. Maximum sample
observations were 2,803,504 used, 11,093,808 committed, and 86,267 KiB total PSS; minimum sample
PSS was 84,807 KiB. All of these heap/PSS observations are diagnostics only, not retained-memory
or peak-headroom evidence.

The 811-line 16 x 256 scoped log covers 23:20:11.663 through 23:21:14.572 JST; the 944-line
256 x 16 scoped log covers 23:27:43.980 through 23:28:46.395 JST. Deterministic audit of each log
found zero fatal exceptions, fatal signals, ANRs, untyped instrumentation failures, and
thermal-pattern matches. Each exact on-device CSV was removed after hash-matched extraction, and
no device log temp was created. `stay_on_while_plugged_in` was restored from temporary value 2 to
original value 0 after each batch. Collection began with wakefulness `Awake` and restored
`Awake`; pre-run and post-run overall thermal status were both 1 for both batches.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-core-current-16x256-rectangle.csv` | 658,021 | `CDD9E0CAF3C739441E59AF7130A7DE4DE54578EEB29188A643B241AA98A4F11D` |
| `build/reports/p2/representation-limits/device-core-current-16x256-rectangle-tool.txt` | 6,601 | `F189DF1A7D48D310E2DD9BBA1A538FABE2A3B87AA37188B3561A15CBCDE80864` |
| `build/reports/p2/representation-limits/device-core-current-16x256-rectangle-logcat.txt` | 111,534 | `604490B02BF04355D8AA1FC8771D20C100B22F2E03959C4AA9D41F6E7337BE84` |
| `build/reports/p2/representation-limits/device-core-current-256x16-rectangle.csv` | 658,391 | `193E04014CF0D780C2947D840B4860155EA6D1A95526E30CF5F562124DF31298` |
| `build/reports/p2/representation-limits/device-core-current-256x16-rectangle-tool.txt` | 6,601 | `23179DDB12B35120C933E3F0DCFEA5F9BCEFA05A658910AA2FB840E899C5477C` |
| `build/reports/p2/representation-limits/device-core-current-256x16-rectangle-logcat.txt` | 130,642 | `B3A3D16EF5F5B6D0F0979AAE73B3C8ACD8152CCEB4C56D120585E7AD968E5876` |

The three explicitly measured same-area 4,096-pixel current-canonical shapes, 64 x 64, 16 x 256,
and 256 x 16, all pass the command-only core-latency and blocking-GC gates. This is limited
evidence that, at 4,096 total pixels, a maximum axis of 256 and either tested aspect orientation
did not break those gates in these runs. Even together with the failing 128 x 128 and 256 x 256
square results, it does not prove area, axis, or shape causality and does not establish or
interpolate a threshold. It does not select a representation, candidate, or product maximum or
cap; prove retained memory/PSS; cover the complete physical matrix, post-cap churn, render, or
compositor timing; or release the P2-02 or representation-dependent P2-04 holds. ADR 0005 remains
`proposed` and the PR remains Draft.

### Pre-fixed refreshed-build current-canonical 16K rectangular final-command tails contract

Before either new observation, the next command-only slice is fixed as the two 16,384-pixel
rectangles already present beside `128x128` in the canonical host shape matrix: `64x256` and
`256x64`. The host `P2CandidatePathKind.Diagonal` definition produces exactly
`min(width,height)` positions `(i,i)`, so both rectangular sparse workloads use the 64 positions
`(0,0)` through `(63,63)`. This is an aspect-ratio comparison at one fixed area, not an
interpolation from a square or a claim about another shape.

Implementation must reuse the one explicit-shape final-command plan, workload, measurement,
correctness, report, validation, and publication path established by the 4K rectangular slice. It
must not copy or fork any of those paths. Existing square and 4K-rectangle candidate routing,
geometry, output identity, publication policy, and observable behavior remain unchanged. The
schema remains `nene-pixel-p2-android-final-command-measurement-v1`; there is no production, API,
dependency, Gradle, or schema change.

Each rectangle is a separate immutable run-1 identity:

| Shape | Candidate ID | Output identity | Immutable on-device CSV |
| --- | --- | --- | --- |
| 64 x 256 | `current-canonical-command-64x256-rectangle` | `device-core-current-64x256-rectangle` | `files/p2-measurements/p2-android-final-command-64x256-rectangle.csv` |
| 256 x 64 | `current-canonical-command-256x64-rectangle` | `device-core-current-256x64-rectangle` | `files/p2-measurements/p2-android-final-command-256x64-rectangle.csv` |

The two shapes must never share a batch, report, staged file, final artifact, percentile
calculation, PASS/FAIL gate, or cleanup target. Completion or failure of one shape has no effect on
the other shape's result. The Android-test publication policy for each is `FailIfExists`; every
on-device output must be absent before its run and is never overwritten.

For each shape, the caller supplies one exact full 40-character source commit that built both
installed APKs. The fixed inputs are app APK
`app/android/build/outputs/apk/debug/android-debug.apk`, test APK
`app/android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk`, application
package `io.github.hideyukimori.nenepixel`, instrumentation package
`io.github.hideyukimori.nenepixel.test`, runner `androidx.test.runner.AndroidJUnitRunner`, and test
class `io.github.hideyukimori.nenepixel.measurement.P2AndroidFinalCommandMeasurementTest`. Each
shape uses exactly these runner arguments, substituting only its fixed candidate ID:

- `nene.p2.physicalProfileId=NENE-P2-ALLDOCUBE-IPL80MP-A16-API36`;
- `nene.p2.warmupIterations=5`;
- `nene.p2.sampleCount=200`;
- `nene.p2.candidateId=current-canonical-command-64x256-rectangle` or
  `nene.p2.candidateId=current-canonical-command-256x64-rectangle`, matching that batch;
- `nene.p2.runIndex=1`; and
- `nene.p2.sourceCommit=<that batch's fixed full source commit>`.

No auxiliary-emulator argument is present. The APKs must come from one no-cache build of the fixed
source commit. The shape-specific tool artifact records the exact source commit and both APK paths,
byte lengths, and SHA-256 values before installation. The production `src/main` diff from parity
baseline `b8674a44c022630ab2925dcdda0780a598a7b4e8` to each new source commit must be empty across
`core/domain/src/main`, `core/pixel-engine/src/main`, `core/application/src/main`,
`presentation/compose/src/main`, and `app/android/src/main`; otherwise that batch is invalid.

Each run is valid only with build fingerprint
`ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94110:user/release-keys`, security patch
2026-06-05, display mode 1 at 1200 x 1920 and 90 Hz, `Runtime.maxMemory()` 268,435,456 bytes, and
`ActivityManager.memoryClass` 256 MiB. These exact values must appear in metadata or checkpoints.
The metadata `canvas` value must encode the exact ordered `widthxheight`, and every sample must
carry that width and height independently in the existing `canvas_width` and `canvas_height`
columns. An edge swap, square fallback, or disagreement with the candidate identity invalidates
the batch.

The workload order is `sparse_apply_stroke`, `dense_apply_stroke`,
`dense_same_color_no_op`, `dense_undo`, and `dense_redo`. Each workload has five untimed warmups
and 200 measured samples. Every iteration creates a fresh `PreparedCommandWorkload` and makes
exactly one `CommandGateway.execute` call. The unchanged v1 boundary captures one post-GC process
baseline before measured samples; workload preparation, heap/PSS and ART observations, and
correctness checks remain outside direct execution latency. Local indices are 1 through 200 per
workload and global indices are unique 1 through 1,000 in fixed order. Nearest-rank p95 uses rank
190 and p99 rank 198 from all 200 rows with no interpolation, clamp, exclusion, outlier removal,
or discard.

Both sparse workloads have `position_count=64`, using only the canonical `(i,i)` positions for
`i=0..63`. Their applied result is revision 1 with `undo_available`, public revisions 0 to 1, and
affected invalidation `(0,0,64,64)` for both shapes. That sparse region is not full-canvas
invalidation for either rectangle. Each dense, same-color no-op, undo, and redo workload has
`position_count=16384`. Dense apply is `applied` at revision 1 with `undo_available` and public
revisions 0 to 1. Undo is `applied` at revision 0 with `redo_available` and public revisions 1 to
0; redo is `applied` at revision 1 with `undo_available` and public revisions 0 to 1. Dense apply,
undo, and redo use full invalidation `(0,0,64,256)` for the 64 x 256 batch and
`(0,0,256,64)` for the 256 x 64 batch. The same-color no-op is
`rejected_no_effective_change` at revision 0 with history `none`, no public `ChangeSet`, no
invalidation, and unchanged state identity.

Every sample retains the v1 exact state, complete pixels, revision, history, result kind, public
`ChangeSet`, invalidation, and no-effect identity checks. Sparse verification must also prove that
only the 64 diagonal pixels changed; dense verification covers all 16,384 positions. Any position,
pixel, ordering, count, revision, history, result, `ChangeSet`, invalidation, or identity mismatch
invalidates only that shape's batch.

The existing physical checkpoint plan applies separately to each report: `before_samples`, every
25th global sample through `after_1000`, and a distinct `after_samples`, for exactly 42 ordered
checkpoints. Each artifact contains exactly 53 columns and 1,088 data rows: 45 metadata, one
process baseline, 42 checkpoints, and 1,000 samples. A display mode or physical-dimension change,
refresh other than 90 Hz, device-wide thermal status above 1, enabled power saving,
non-interactive display, lost USB power, or unavailable battery data invalidates that batch. Core
latency passes per shape only when every workload has p95 <= 8.0 ms and p99 <= 16.67 ms. Blocking
GC passes per shape only when at least 950 of its 1,000 operations have zero ART blocking-GC-count
increment; logcat neither replaces nor adds a separate GC condition.

The distinct immutable host targets are:

| Shape | CSV | Tool identity | Scoped logcat |
| --- | --- | --- | --- |
| 64 x 256 | `build/reports/p2/representation-limits/device-core-current-64x256-rectangle.csv` | `build/reports/p2/representation-limits/device-core-current-64x256-rectangle-tool.txt` | `build/reports/p2/representation-limits/device-core-current-64x256-rectangle-logcat.txt` |
| 256 x 64 | `build/reports/p2/representation-limits/device-core-current-256x64-rectangle.csv` | `build/reports/p2/representation-limits/device-core-current-256x64-rectangle-tool.txt` | `build/reports/p2/representation-limits/device-core-current-256x64-rectangle-logcat.txt` |

For each shape, one unique batch stages its three host files while all three corresponding final
targets remain absent. The tool artifact must retain the batch, source, APK, package, runner,
argument, profile, build, collection-time, and original/restored stay-awake identity; the scoped
logcat must cover the complete instrumentation interval. Publication is allowed only after an
independent deterministic audit of that one shape validates the CSV schema, identity, 53 columns,
row counts, ordering, checkpoints, exact profile, source and APK identity, production diff,
percentiles, ART conditions, correctness, shape-specific invalidation, log scope, and zero fatal
exceptions, fatal signals, ANRs, or untyped instrumentation failures. Thermal log matches remain
diagnostic; checkpoints are authoritative.

Before the first move, all three shape-specific final targets are checked absent. Each no-overwrite
move must preserve staged bytes and SHA-256. A partial publication is rejected and every moved
member of that batch is renamed with one shared `.invalid-<batch-id>` suffix. The result ledger
records byte length and SHA-256 for the CSV, tool artifact, and scoped logcat. A rejected attempt
removes only its exact shape-specific on-device output after diagnostics are retained. After
successful hash-matched extraction, only that batch's exact temporary device files are removed and
the recorded original stay-awake value is restored and verified. Cleanup or quarantine must not
address the other rectangle or any accepted artifact.

All previously accepted artifacts remain immutable. In particular, the accepted command-tail
square and 4K-rectangle artifacts retain these byte lengths and SHA-256 values:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-core-current-64-square.csv` | 649,469 | `F9CDEDA09E7901BACCC58BC39094FEC5699FBC0ACBC11C91C40502EDFC3932C6` |
| `build/reports/p2/representation-limits/device-core-current-64-square-tool.txt` | 4,279 | `D0CAF501AB89987EB499D164CDF8A48CDAE5F413BD761C22331858F8A3B267D3` |
| `build/reports/p2/representation-limits/device-core-current-64-square-logcat.txt` | 220,962 | `D1515A3B10252D0B36B174407F025D700745B12890AD6C33097529E3DA70B79F` |
| `build/reports/p2/representation-limits/device-core-current-128-square.csv` | 658,096 | `0A075A06E4F87AE3B4AF3D75DA70C17A0F86AD797D665B7F1B91EEF9A5357D11` |
| `build/reports/p2/representation-limits/device-core-current-128-square-tool.txt` | 6,008 | `4AA6E81B7A283C055E05259B744EDFF94EE6AB89AB9F07BEB96C04DA0509A958` |
| `build/reports/p2/representation-limits/device-core-current-128-square-logcat.txt` | 160,933 | `3E678B5AF6449EF33A5E2801E3DBFECD25BF2F455EAFCB5F5BB9F78EC368C12D` |
| `build/reports/p2/representation-limits/device-core.csv` | 661,245 | `E2FD1D427DE191F14A8194FE4CF5CE5A929A388B7A13E439E731F0AF8180FCAD` |
| `build/reports/p2/representation-limits/device-core-current-16x256-rectangle.csv` | 658,021 | `CDD9E0CAF3C739441E59AF7130A7DE4DE54578EEB29188A643B241AA98A4F11D` |
| `build/reports/p2/representation-limits/device-core-current-16x256-rectangle-tool.txt` | 6,601 | `F189DF1A7D48D310E2DD9BBA1A538FABE2A3B87AA37188B3561A15CBCDE80864` |
| `build/reports/p2/representation-limits/device-core-current-16x256-rectangle-logcat.txt` | 111,534 | `604490B02BF04355D8AA1FC8771D20C100B22F2E03959C4AA9D41F6E7337BE84` |
| `build/reports/p2/representation-limits/device-core-current-256x16-rectangle.csv` | 658,391 | `193E04014CF0D780C2947D840B4860155EA6D1A95526E30CF5F562124DF31298` |
| `build/reports/p2/representation-limits/device-core-current-256x16-rectangle-tool.txt` | 6,601 | `23179DDB12B35120C933E3F0DCFEA5F9BCEFA05A658910AA2FB840E899C5477C` |
| `build/reports/p2/representation-limits/device-core-current-256x16-rectangle-logcat.txt` | 130,642 | `B3A3D16EF5F5B6D0F0979AAE73B3C8ACD8152CCEB4C56D120585E7AD968E5876` |

These claims and holds are fixed before either observation. Until its own valid audited result is
recorded, neither rectangle makes a PASS claim. A passing rectangle covers only one
current-canonical 16K command run and does not decide the other rectangle or explain the observed
128-square result. ADR 0005 remains `proposed`, the PR remains Draft, and representation,
product-limit, retained-memory/PSS, candidate-physical, complete-physical-matrix, post-cap churn,
render, compositor, P2-02, and representation-dependent P2-04 holds remain in force regardless of
either outcome.

### Pre-fixed refreshed-device-build continuation for the 256 x 64 tail

A read-only preflight on 2026-09-03, before APK installation or the first 256 x 64
instrumentation attempt, found that the named physical tablet had advanced from build `94110`,
security patch 2026-06-05, to build fingerprint
`ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94111:user/release-keys` and security patch
2026-08-05. The final three 256 x 64 host targets remained absent. The application package was
still registered with PackageManager, but `run-as` reported that its private data directory did
not exist, so the fixed private on-device output was absent. No measurement row, batch UUID, or
256 x 64 result existed when this continuation was fixed.

The hardware profile ID remains `NENE-P2-ALLDOCUBE-IPL80MP-A16-API36`. The preflight still
reported physical 1200 x 1920, mode 1 at 90 Hz, device-wide thermal status 1, power saving off,
USB power, 100% battery, original `stay_on_while_plugged_in=0`, and an asleep display. The
instrumentation must continue to validate those conditions at every existing checkpoint and must
also report `Runtime.maxMemory()` 268,435,456 bytes and `ActivityManager.memoryClass` 256 MiB.

The unexecuted `current-canonical-command-256x64-rectangle` run is therefore continued only on
build `94111` and security patch 2026-08-05. Before measurement, the final-command AndroidTest
profile and its exact-value contract test advance to those two values. That implementation may
change AndroidTest source only; it changes no production source, public API, dependency, Gradle
wiring, module, report schema, candidate ID, geometry, workload, threshold, publication path, or
cleanup rule. Both APKs must then come from one no-cache build of the new full source commit, and
their byte lengths and SHA-256 values must be fixed in the new batch tool artifact before
installation. The production `src/main` diff from parity baseline
`b8674a44c022630ab2925dcdda0780a598a7b4e8` to that source must remain empty.

The profile preflight must also reject the run before sampling unless `Debug.getRuntimeStats()`
exposes exactly the same eight sorted string keys used by the 45-row metadata contract:
`art.gc.blocking-gc-count`, `art.gc.blocking-gc-count-rate-histogram`,
`art.gc.blocking-gc-time`, `art.gc.bytes-allocated`, `art.gc.bytes-freed`,
`art.gc.gc-count`, `art.gc.gc-count-rate-histogram`, and `art.gc.gc-time`. This check adds no
column or metric and does not reinterpret the five tracked per-sample counters. It prevents an OS
update from producing a different metadata count only after the full run has completed.

For this continuation, the earlier statement that every iteration makes exactly one
`CommandGateway.execute` call means exactly one measured call after untimed
`PreparedCommandWorkload` creation. Undo preparation performs one untimed apply, and redo
preparation performs one untimed apply plus one untimed undo before the single measured call. This
clarifies the already-recorded `measurement_boundary`; it changes neither the timed interval nor
the accepted 64 x 256 result.

All other terms of the pre-fixed 256 x 64 contract above remain unchanged, including its unique
run-1 batch, `FailIfExists`, 53 columns, 45 metadata plus one baseline plus 42 checkpoints plus
1,000 samples, exact 256 x 64 geometry, 64-position diagonal, shape-specific invalidation,
nearest-rank gates, independent audit, three-file no-overwrite publication, and exact cleanup.
The already accepted 64 x 256 batch remains immutable evidence from build `94110`; it is not
rerun, relabeled, or revalidated against the new AndroidTest profile.

Because the two 16K orientations now come from different device software builds, their raw rows
must not be aggregated or treated as a same-build paired comparison. Each remains an independent
current-canonical observation on the named hardware profile. Any numerical difference may reflect
build, run, orientation, or their interaction, so it cannot establish axis, area, shape, or build
causality. Completing the 256 x 64 observation still closes only the two explicitly requested
shape points; it selects no representation, product maximum, or cap and does not release any
existing ADR, PR, P2-02, P2-04, memory, render, compositor, or post-cap hold.

### Cross-build current-canonical 16K rectangular final-command tails result

Both pre-fixed 16K orientations completed as independent current-canonical observations on the
named hardware profile. The accepted 64 x 256 batch
`06f3c930-59bf-456f-90ec-874b5c8f4bd3` used source
`d45801142a5056075533a78cd3b66b7f6d36ad19`, build `94110`, and security patch 2026-06-05.
The accepted 256 x 64 batch `6a857f68-b614-46ec-899c-500de0f9f299` used source
`0b57206204ab1ccb1731bab7ac1b2aeac8c1d941`, build `94111`, and security patch 2026-08-05.
The production `src/main` diff from parity baseline
`b8674a44c022630ab2925dcdda0780a598a7b4e8` to each source was empty across all five fixed
paths. The later source passed CI run `33745640144`; its one no-cache APK build completed in 40
seconds with all 95 tasks executed, and the refreshed connected contract reported `OK (23 tests)`.

The 64 x 256 batch used an app APK of 11,664,182 bytes with SHA-256
`71B732DE85D2984360CD6676184D66250D87EDA34444DB04EDA82E278BB5C796` and a test APK of
1,323,073 bytes with SHA-256
`3B4FA96AE993DC7D910ADDA1AEB209A954330DF2B2A0DE25FFAE892F1B1867A5`. The 256 x 64
batch used the same app APK bytes and hash and a refreshed test APK of 1,323,649 bytes with SHA-256
`AFD715EA58AA62C508942047A97ED1DADAF2582FF8A53CA764EADFC29CE20F65`. Both exact
measurement invocations reported `OK (1 test)`; their instrumentation times were respectively
88.741 and 88.187 seconds.

Both CSVs have exactly 53 columns and 1,088 data rows: 45 metadata, one process baseline, 42
ordered physical checkpoints, and 1,000 samples. Independent deterministic recomputation found
zero mismatches and high/medium/low finding counts of zero for both batches. Metadata encoded the
fixed run identity, and every baseline, checkpoint, and sample row retained the corresponding
identity fields. The sample rows retained the fixed workload order, 200 local samples per
workload, global indices 1 through 1,000, shape-specific 64-position diagonal and 16,384-position
dense geometry, correctness hashes, outcomes, revisions, history, public `ChangeSet`, invalidation,
and no-op identity. All checkpoints retained mode 1, 1200 x 1920 at 90 Hz, thermal status 1, power
saving disabled, an interactive display, USB power, and 100% battery.

Nearest-rank recomputation produced the following command-only latency result. Each p95 uses rank
190 and each p99 rank 198 from 200 rows without interpolation or exclusion:

| Shape / workload | p95 latency (ns) | p99 latency (ns) | Fixed core-latency condition |
| --- | ---: | ---: | --- |
| 64 x 256 / sparse apply | 5,452,653 | 5,595,961 | PASS |
| 64 x 256 / dense apply | 24,382,193 | 24,541,538 | FAIL |
| 64 x 256 / same-color no-op | 7,150,577 | 7,195,577 | PASS |
| 64 x 256 / undo | 9,022,615 | 9,553,384 | FAIL |
| 64 x 256 / redo | 12,600,308 | 13,630,115 | FAIL |
| 256 x 64 / sparse apply | 5,631,577 | 5,760,731 | PASS |
| 256 x 64 / dense apply | 22,625,577 | 22,878,116 | FAIL |
| 256 x 64 / same-color no-op | 5,298,423 | 5,460,885 | PASS |
| 256 x 64 / undo | 9,135,923 | 9,847,192 | FAIL |
| 256 x 64 / redo | 13,043,000 | 13,953,731 | FAIL |

Core latency therefore fails for `dense_apply_stroke`, `dense_undo`, and `dense_redo` in each
batch. Blocking GC passes separately for both shapes with zero blocking-GC-count delta in all
1,000 operations; the 256 x 64 batch also had zero blocking-GC-time delta in all 1,000 operations.
The gate-external all-GC and allocation diagnostics were respectively 922/1,000 and 125/1,000 zero
samples for 64 x 256, and 904/1,000 and 105/1,000 zero samples for 256 x 64.

The immutable published artifacts are:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-core-current-64x256-rectangle.csv` | 662,233 | `9055A6D61F620C027C72080A22569341887C842925C3F3A0E05231E9E79A8DF2` |
| `build/reports/p2/representation-limits/device-core-current-64x256-rectangle-tool.txt` | 7,802 | `0BAE387F82FA248C5F2D864BFCFE2B6658BCEFA4D3A2A8F33C8B03871ED461CA` |
| `build/reports/p2/representation-limits/device-core-current-64x256-rectangle-logcat.txt` | 155,408 | `6424CF5AF9559B677BDA3897F5A506E15EB10D81900E2800FEAA7615C5FD525D` |
| `build/reports/p2/representation-limits/device-core-current-256x64-rectangle.csv` | 662,796 | `27727819298D9454429595C55D8E0FF652618966AEC6BA1421AFC042E67AB585` |
| `build/reports/p2/representation-limits/device-core-current-256x64-rectangle-tool.txt` | 8,545 | `AECE81CF0840D4F55B69248B90268390A7235F369FA85058FEC74155CA63FCE0` |
| `build/reports/p2/representation-limits/device-core-current-256x64-rectangle-logcat.txt` | 159,047 | `AA50F07A2785956EE46652A712E1FC4DA593512A81BF84A59C6EE24B4CCE5364` |

Both scoped logs covered their complete instrumentation interval and had zero fatal exceptions,
fatal signals, ANRs, untyped instrumentation failures, or thermal matches. Host/device CSV hashes
matched before exact on-device cleanup, and the original stay-awake and wakefulness states were
restored. The 256 x 64 publication used three no-overwrite moves after a separate publication
audit reported zero mismatches and high/medium/low findings of zero.

These results close only the two requested 16K shape observations. Because their device software
builds differ, the values above are not a paired orientation comparison and must not be aggregated;
they establish no axis, area, shape, or build causality. Neither result selects a representation,
product maximum, or cap. ADR 0005 remains `proposed`, PR #47 remains Draft, and every previously
recorded downstream hold remains in force.

### Lane isolation and canonical bitmap presentation

Source `4ede2a8b4a789ec95cf957ad0df65561e20ce215` separated correctness, command
latency/ART, retained-memory/PSS, and frame work. The command-latency route no longer forces GC or
finalization and no longer captures a post-GC retained owner between samples. Its existing sample
memory columns remain empty rather than being relabelled. Correctness digests stay outside the
timed sample boundary. Frame callbacks retain only constant-size generation, revision, reference,
and timing values; a full image correctness check runs only after the matching frame result.

The same source replaced the release presentation's per-pixel object projection and rectangle
loop with one `ARGB_8888` bitmap created once per immutable `PixelSnapshot` and one native
nearest-neighbor `drawBitmap` call. Workspace-only changes reuse the bitmap. Anti-aliasing,
dithering, and bitmap filtering are disabled. The former `RenderedPixel` projection was removed
after its immutable diagnostic artifact was recorded; it is not a release, debug, or test runtime
alternative. Physical `CanvasBitmapProjectionTest` passed on the named device. This removes known
nonessential draw and frame-verification work before choosing core storage, but it does not by
itself prove a frame deadline.

### Physical packed-candidate command-kernel result

The fixed physical comparison from source
`7cbefc11c2b430e36df01355f733a0252f053f2b` ran flat packed RGBA8888 and the preselected
tiled/COW T16 challenger in one schema
`nene-pixel-p2-android-packed-candidate-comparison-v1` artifact. It contains 1,000 samples: 100
samples for each candidate and each of sparse apply, dense apply, dense same-color no-op, undo,
and redo. Exact pixels, canonical change counts, revisions, inverse round trips, and outcomes
passed. No negative runtime counter was recorded.

| Candidate / workload | Median latency (ns) | p95 latency (ns) | p99 latency (ns) | p95 ART allocation (bytes) |
| --- | ---: | ---: | ---: | ---: |
| flat / sparse apply | 689,635 | 894,577 | 947,731 | 368,640 |
| flat / dense apply | 4,409,615 | 6,688,538 | 7,250,000 | 1,433,600 |
| flat / dense same-color no-op | 451,346 | 625,962 | 2,287,461 | 368,640 |
| flat / undo | 3,010,288 | 4,951,731 | 5,015,539 | 299,008 |
| flat / redo | 3,004,615 | 4,918,615 | 5,032,385 | 299,008 |
| T16 / sparse apply | 282,423 | 540,077 | 620,616 | 102,400 |
| T16 / dense apply | 6,714,135 | 9,126,154 | 9,958,692 | 2,256,896 |
| T16 / dense same-color no-op | 990,211 | 1,834,461 | 3,126,616 | 401,408 |
| T16 / undo | 3,951,365 | 6,159,308 | 6,428,192 | 2,846,720 |
| T16 / redo | 3,934,019 | 6,222,231 | 6,541,616 | 2,846,720 |

Flat packed passes the fixed p95 8.0 ms and p99 16.67 ms kernel targets for every workload. T16
wins the sparse row but fails the dense-apply p95 target before application-command overhead and
allocates more at each dense transition tail. T16 is therefore eliminated rather than retained as
a runtime-selectable sparse optimization. Flat packed is the sole surviving storage candidate;
its full canonical application-command lane must still pass after migration.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-packed-candidates-run-01.csv` | 123,492 | `C6F3A255C3B4E0D3D73B82ADD21461A11520FA59D5BC5C72C43DBC979BEED3CC` |

### Clean current-canonical 256-square command result

Source `463fec1b6ac4473e19bec871035d216d2cc5b0f1` added the immutable candidate
`current-canonical-command-256-clean-latency-v1`. The schema
`nene-pixel-p2-android-clean-command-latency-v1` artifact contains 1,000 samples, 45 metadata
rows, 42 physical checkpoints, and one process baseline. Every per-sample heap/PSS field is empty;
the measured boundary is one prepared `CommandGateway.execute` call with no forced GC,
finalization, full-document equality scan, digest, or hash between samples. All correctness,
outcome, ordering, checkpoint, and nonnegative-counter invariants passed.

| Workload | Median latency (ns) | p95 latency (ns) | p99 latency (ns) | p95 ART allocation (bytes) |
| --- | ---: | ---: | ---: | ---: |
| sparse apply | 21,813,980 | 24,143,000 | 24,803,577 | 6,979,584 |
| dense apply | 103,980,596 | 112,808,577 | 117,933,730 | 28,086,272 |
| dense same-color no-op | 33,083,577 | 34,087,154 | 35,571,000 | 5,390,336 |
| undo | 37,797,288 | 40,341,885 | 42,260,193 | 27,078,656 |
| redo | 34,641,404 | 41,163,385 | 41,978,923 | 33,480,704 |

The current object/materialized-inverse representation fails the clean physical command target in
all five rows. Removing the old per-sample post-GC capture corrected the measurement boundary but
did not explain away the current implementation's cost.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-clean-current-command-256-run-01.csv` | 628,484 | `33C070ABED7B1B89FE9FF0FE55941FFB902AFC8C3D000D2CBBD0E195EECC5F79` |

### Independent current-versus-flat retained-memory result

Source `4fba6a3f3d0d07607d5be53944339e29d9a8ec2e` fixed schema
`nene-pixel-p2-android-candidate-retained-memory-v1` and compared two explicitly retained owners:
the current object snapshots/materialized inverses plus canonical bitmap, and flat packed
RGBA8888/shared directional inverses plus the same canonical bitmap. Each independent invocation
retains a 256 x 256 final document, 64 transitions of 8,192 unique changes each, 524,288 total
retained changes, and revision 64. Candidate preload precedes the baseline; forced GC/finalization
occurs only at baseline and retained checkpoints. Exact final pixels, bitmap projection, every
change count, and every forward/inverse round trip passed. All six invocations had distinct process
IDs, stable display mode 1 at 1200 x 1920 and 90 Hz, thermal status no higher than 1, power saving
off, an interactive display, and USB power.

| Candidate | Median heap delta (bytes) | Maximum heap delta | Median retained heap | Maximum retained heap | Median PSS delta (KiB) | Maximum PSS delta |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| current object + materialized inverse + bitmap | 38,039,552 | 38,039,552 | 40,257,328 | 40,257,328 | 38,175 | 38,841 |
| flat packed + shared inverse + bitmap | 7,352,320 | 7,352,320 | 9,541,424 | 9,541,424 | 7,570 | 7,583 |

Both candidates pass the historical retained-heap and PSS headroom conditions. Flat packed reduces
the retained Java-heap delta by 80.67% and the median paired PSS delta by 80.17% against the current
owner under the same workload. Its retained Java heap is 3.55% of the fixed 268,435,456-byte
runtime maximum, versus 15.00% for current. These are candidate-comparison results, not a claim
that the device measurement directly defines a runtime-dependent product limit.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-candidate-memory-current-run-01.csv` | 9,385 | `C036FA01F478A30DDBACDBCC8EE753050E7EBF98B1204BC0508F733CC102801C` |
| `build/reports/p2/representation-limits/device-candidate-memory-current-run-02.csv` | 9,385 | `0CB617232D462C1F4F9A4D9DB93A6F370F55A2D3412AECE0B63D4CDC934F4189` |
| `build/reports/p2/representation-limits/device-candidate-memory-current-run-03.csv` | 9,359 | `7B520A0339D3B3CEFB4C5C83183323351C27893D075888768A73A07CF7CC23F7` |
| `build/reports/p2/representation-limits/device-candidate-memory-flat-packed-run-01.csv` | 9,506 | `B388FC878FACF0181C780AAF3A34F4C11B67E95024B33589E084E53DF42FD6E3` |
| `build/reports/p2/representation-limits/device-candidate-memory-flat-packed-run-02.csv` | 9,506 | `21F80DC3D6622B144C47B5992A3F2C7F2D9C8174EF5200FCD1D742C586DA003C` |
| `build/reports/p2/representation-limits/device-candidate-memory-flat-packed-run-03.csv` | 9,506 | `74A384A800A8E8BFA4DF444B6C9746B0DE157542A719D2FAD29183A65EBDFA4F` |

### Migrated flat-packed production command result

Source `dca5c14212e629b20dda2c0636d1be5876deac99` atomically migrated the canonical
domain, pixel-engine, application, and presentation path to flat packed RGBA8888, shared
directional inverse payloads, one canonical bitmap projection, and the accepted typed limits. The
old object/materialized-inverse path and host analytical candidate implementations were removed in
the same focused change. Later sources removed measured redundant work without adding a second
semantic or storage path:

| Run | Source | Focus | Dense-apply p95 / p99 | Decision |
| --- | --- | --- | ---: | --- |
| 01 | `dca5c14212e629b20dda2c0636d1be5876deac99` | initial canonical migration | 12.994577 / 13.705923 ms | p95 fail; continue profiling |
| 02 | `609f555ad6e756f0af792227f7dc32f073e96660` | skip already-proven ordering work and combine patch validation/write | 12.628038 / 13.206962 ms | p95 fail |
| 03 | `c24bffba4d5d751cf215d5056a5f4de2311133e0` | attempted callback-based snapshot copy | 17.024269 / 17.371576 ms | regression; reverted completely |
| 04 | `df9f9a0e68e5a5a2f92346831005d81e09e547e2` | retain validated stroke positions as packed row-major indexes | 9.322500 / 9.942885 ms | p95 fail |
| 05 | `fe9b4f0978c2c11eea28f9d7f1be71d421316de5` | derive contiguous invalidation bounds without a second coordinate scan | 8.174769 / 8.625423 ms | slight p95 fail |
| 06 | `94559fe852c55b45b570222218072fcbaecfe4c4` | consume rasterization's already-proven invariants once | 6.315231 / 6.985308 ms | pass |

Each run used the same named physical profile, 5 warmups and 200 samples for each of five
workloads, one prepared `CommandGateway.execute` measurement boundary, immutable no-overwrite
output, and the clean-latency schema. The chronology is retained rather than selecting a favorable
rerun. Run 03 demonstrates why the callback-copy idea was rejected; its implementation does not
remain in the selected path.

Run 06 contains 1,000 samples, 45 metadata rows, 42 fixed physical checkpoints, and one process
baseline. The device reported Android 16/API 36 build `94111`, security patch `2026-08-05`, mode 1
at 1200 x 1920 and 90 Hz, power saving off, interactive USB power, and thermal status no higher
than 1. Every exact outcome, revision, change count, history state, `ChangeSet`, invalidation, and
checkpoint invariant passed. Every operation had zero blocking-GC-count increment.

| Run-06 workload | p50 | p95 | p99 | Maximum | p95 ART allocation |
| --- | ---: | ---: | ---: | ---: | ---: |
| sparse apply | 0.761693 ms | 1.152961 ms | 2.752038 ms | 3.015538 ms | 901,120 bytes |
| dense apply | 3.897808 ms | 6.315231 ms | 6.985308 ms | 7.485538 ms | 8,925,184 bytes |
| dense same-color no-op | 0.765885 ms | 1.055461 ms | 2.677346 ms | 3.059115 ms | 6,967,296 bytes |
| undo | 2.107692 ms | 4.433769 ms | 5.055923 ms | 5.588347 ms | 565,248 bytes |
| redo | 2.099539 ms | 2.522961 ms | 5.031654 ms | 7.090769 ms | 9,396,224 bytes |

All rows pass p95 <= 8.0 ms and p99 <= 16.67 ms. The slowest p95 leaves 21.1% target
headroom; the slowest p99 leaves 58.1%. ART allocation remains descriptive rather than a pass
threshold; retained ownership is bounded by the independent result above.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/reports/p2/representation-limits/device-clean-flat-packed-command-256-run-01.csv` | 618,609 | `8243DAD55C083560951F0D7563D1B367C59C558C5F051F90833ABE21FEB4967F` |
| `build/reports/p2/representation-limits/device-clean-flat-packed-command-256-run-02.csv` | 618,636 | `9BFBB06781EE2EC3E5BADC9990FB5028FD3C2878D15D79551C4B35FDE762180E` |
| `build/reports/p2/representation-limits/device-clean-flat-packed-command-256-run-03.csv` | 620,149 | `3BE1425987E86716B1E5ACADC9743F8F6C38B7B41D43F940D33853C0872E9250` |
| `build/reports/p2/representation-limits/device-clean-flat-packed-command-256-run-04.csv` | 618,209 | `B153ACB4145A3B87AD00331D17D2BCCE597BFC49C22A4404C1557A87C08968F2` |
| `build/reports/p2/representation-limits/device-clean-flat-packed-command-256-run-05.csv` | 618,362 | `83E3184E4210409580411A02DAC11C13150D2972F1767C38CBF948F38E896226` |
| `build/reports/p2/representation-limits/device-clean-flat-packed-command-256-run-06.csv` | 618,212 | `9BCF82F6C1D0BAC9E5423EB342C53611B165E01DC3E9EA66846123F538AC24D1` |

### Prospective command-lane correction after run-06

Issue #56 audited the retained executable harness after QLT-014 became normative. Contrary to the
historical boundary description above, the retained source called full `DocumentState` equality and
both document and snapshot `hashCode` after every timed command and before the next sample. Timing
excluded those calls, but that did not remove their cache, allocation, or ART observer effects.

Run-06, its schema-v1 bytes, hashes, values, and historical PASS verdict remain immutable and are not
relabelled. They cannot be reused as new QLT-014-conforming acceptance evidence. The corrected
prospective harness uses schema `nene-pixel-p2-android-clean-command-latency-v2` and candidate
`flat-packed-command-256-lane-separated-v1`. Before any warmup or latency sample, it executes one
separate correctness case per workload and records exact full-document/pixel equality plus document
and snapshot hashes in `correctness` rows. Latency `sample` rows retain only result kind, revision,
history, public `ChangeSet` revisions, invalidation, no-op state identity, direct command time, and
the existing ART counters; their hash columns are blank.

Fixture construction remains outside direct command timing and necessarily creates an independent
canonical gateway for each sample. The latency factory does not create or retain the correctness-only
expected full `DocumentState`; that oracle is constructed only by the separate correctness factory.
The necessary command/path/initial-state fixture setup can still influence later runtime state, so
this correction makes no new latency claim and authorizes no device collection by itself. Physical
environment probes remain fixed checkpoints rather than per-command checks. Regression coverage
asserts for every workload that the latency factory has no correctness oracle and never enters
full-state verification, while the separate correctness factory does both. Any future collection
requires a separately reconciled live Issue and finite protocol under QLT-013 through QLT-015.

### Accepted limit and renderer handoff result

Focused contracts prove axis, raw-stroke, patch, history-entry, and retained-change cap minus one,
cap, and cap plus one. Rejection occurs before the relevant ownership copy, containment scan,
sort, allocation, or atomic commit. Canvas area is a derived invariant rather than a redundant
user-reachable rejection: two independently validated positive axes no larger than 256 imply at
most 65,536 pixels, and `CanvasSize` asserts that implication. The accepted policy is therefore:

| Owner | Accepted maximum | Supporting point and headroom |
| --- | ---: | --- |
| width / height | 256 pixels each | run-06 256 x 256 production command pass |
| derived canvas area | 65,536 pixels | same explicitly measured square; no second reachable area branch |
| raw stroke | 262,144 samples | half of the passing 524,288-sample host amplification fixture; at most four samples per supported pixel |
| patch | 65,536 unique changes | run-06 dense full-canvas production command pass |
| history | 64 entries | independent retained owner at exactly `H=64` |
| retained changes | 524,288 changes | independent retained owner at exactly `T=524,288`, 9,541,424-byte retained heap, 3.55% of the fixed runtime maximum |

On source `94559fe852c55b45b570222218072fcbaecfe4c4`, the physical
`CanvasBitmapProjectionTest` and `UndoRedoEditorTest` passed together through
`:presentation:compose:connectedDebugAndroidTest`. This verifies exact bitmap handoff and the
editor transition on the selected representation. It does not relabel earlier frame data or claim
strict compositor timing; that correlation remains deferred to the renderer milestone.

The retained historical comparison routes and analytical candidate models were removed after the
immutable artifacts above were recorded. The repository retains one physical production-command
route for QLT-010 reproducibility; it contains no current-object, tiled/COW, combined post-GC, or
per-frame alternative implementation.

### Required workload matrix

The current decision matrix is intentionally bounded. Earlier exhaustive square/rectangle,
density, and analytical-history sweeps remain diagnostic screening evidence; they do not create a
duty to search for a largest possible workload.

| Decision row | Candidates | Minimum required cases | Lane |
| --- | --- | --- | --- |
| Semantic and storage correctness | current baseline, flat packed RGBA8888, one preselected tiled/COW challenger | canonical blank, deterministic RGBA including alpha zero, sparse change, dense change, eraser-equivalent, same-color no-op, late conflict, exact forward/inverse round trip | correctness |
| Supported canvas cap | selected representation, with the current baseline retained for comparison | representative square plus the most demanding supported rectangle; cap-minus-one, cap, cap-plus-one, area/axis overflow, and pre-allocation typed rejection | correctness, then latency and memory |
| Command tail | all three decision candidates until one fails or is selected | sparse diagonal apply, dense apply, same-color no-op, undo, and redo at the representative cap workload | latency and ART tail |
| Retained ownership | current baseline and surviving candidate or candidates | canonical document plus the declared retained history/change budget at the representative cap; five independent checkpoints | retained memory and PSS |
| Raw stroke, patch, and history caps | selected representation | duplicate/no-op raw stroke; unique patch; entry and retained-change budget at cap-minus-one, cap, and cap-plus-one | correctness, then targeted latency or memory where the bound changes resource use |
| Renderer handoff | selected production representation after its renderer exists | representative correct frame and deadline behavior | frame; strict compositor correlation deferred |

Full-document pixel equality, semantic hashes, canonical ordering, containment, inverse storage,
and affected/unaffected-pixel checks run in the correctness rows. Latency rows retain only cheap
per-sample public outcome, revision, history, `ChangeSet`, invalidation, and no-op identity checks;
they must not force GC/finalization or compute a full-document digest/hash between samples.
Retained history evidence proves the declared entry and retained-change bounds; it does not need
to enumerate every analytical `T <= H * N` combination or introduce production multi-step
history.

## P2 raw artifact contract

The paths and checksums already recorded in this ledger are immutable diagnostic evidence. The
table below preserves the chronological artifact plan, including frame/compositor paths that are
now deferred. New lane-specific collection must use a new schema and distinct no-overwrite path;
it must never replace an existing host artifact or reuse a combined command/post-GC result as a
clean latency or retained-memory result. Strict compositor artifacts become required only by the
later renderer milestone if that issue adopts them.

Current-main host measurements must use the separately named Gradle route
`measureP2RepresentationLimits`, a schema beginning `nene-pixel-p2-representation-limits-`, and
metric names that do not reuse `translated_controller_commit` or the P2 viewport transform
boundary. Planned ignored paths are fixed as follows before collection:

| Planned path relative to current repository | Required content |
| --- | --- |
| `build/reports/p2/representation-limits/host-current.csv` | current canonical host metrics and correctness columns |
| `build/reports/p2/representation-limits/host-candidates.csv` | analytical candidate metrics with candidate identity |
| `build/reports/p2/representation-limits/host-projection.csv` | current Compose host projection metrics, full row-major/color correctness, and raw samples |
| `build/reports/p2/representation-limits/device-core.csv` | physical ART latency, allocation, GC, live-heap, and correctness observations |
| `build/reports/p2/representation-limits/device-core-current-64-square.csv` | immutable refreshed-build 64-square final-command tail observations |
| `build/reports/p2/representation-limits/device-core-current-64-square-tool.txt` | source/APK/package/runner/argument/device/time/stay-awake identity for the 64-square batch |
| `build/reports/p2/representation-limits/device-core-current-64-square-logcat.txt` | time-scoped fatal, ANR, thermal, and instrumentation diagnostics for the 64-square batch |
| `build/reports/p2/representation-limits/device-core-current-128-square.csv` | immutable refreshed-build 128-square final-command tail observations |
| `build/reports/p2/representation-limits/device-core-current-128-square-tool.txt` | source/APK/package/runner/argument/device/time/stay-awake identity for the 128-square batch |
| `build/reports/p2/representation-limits/device-core-current-128-square-logcat.txt` | time-scoped fatal, ANR, thermal, and instrumentation diagnostics for the 128-square batch |
| `build/reports/p2/representation-limits/device-core-current-16x256-rectangle.csv` | immutable refreshed-build 16 x 256 final-command tail observations |
| `build/reports/p2/representation-limits/device-core-current-16x256-rectangle-tool.txt` | source/APK/package/runner/argument/device/time/stay-awake identity for the 16 x 256 batch |
| `build/reports/p2/representation-limits/device-core-current-16x256-rectangle-logcat.txt` | time-scoped fatal, ANR, thermal, and instrumentation diagnostics for the 16 x 256 batch |
| `build/reports/p2/representation-limits/device-core-current-256x16-rectangle.csv` | immutable refreshed-build 256 x 16 final-command tail observations |
| `build/reports/p2/representation-limits/device-core-current-256x16-rectangle-tool.txt` | source/APK/package/runner/argument/device/time/stay-awake identity for the 256 x 16 batch |
| `build/reports/p2/representation-limits/device-core-current-256x16-rectangle-logcat.txt` | time-scoped fatal, ANR, thermal, and instrumentation diagnostics for the 256 x 16 batch |
| `build/reports/p2/representation-limits/device-core-current-64x256-rectangle.csv` | immutable refreshed-build 64 x 256 final-command tail observations |
| `build/reports/p2/representation-limits/device-core-current-64x256-rectangle-tool.txt` | source/APK/package/runner/argument/device/time/stay-awake identity for the 64 x 256 batch |
| `build/reports/p2/representation-limits/device-core-current-64x256-rectangle-logcat.txt` | time-scoped fatal, ANR, thermal, and instrumentation diagnostics for the 64 x 256 batch |
| `build/reports/p2/representation-limits/device-core-current-256x64-rectangle.csv` | immutable refreshed-build 256 x 64 final-command tail observations |
| `build/reports/p2/representation-limits/device-core-current-256x64-rectangle-tool.txt` | source/APK/package/runner/argument/device/time/stay-awake identity for the 256 x 64 batch |
| `build/reports/p2/representation-limits/device-core-current-256x64-rectangle-logcat.txt` | time-scoped fatal, ANR, thermal, and instrumentation diagnostics for the 256 x 64 batch |
| `build/reports/p2/representation-limits/device-memory-run-01.csv` through `device-memory-run-05.csv` | one immutable PSS/retained/post-GC checkpoint invocation each; individual checksums required |
| `build/reports/p2/representation-limits/device-memory.csv` | deterministic aggregate over the five run-indexed memory artifacts |
| `build/reports/p2/representation-limits/device-current-peak-headroom.csv` | immutable cross-artifact current-canonical peak-headroom audit |
| `build/reports/p2/representation-limits/device-frames.csv` | frame deadline, overrun, and command-to-first-correct-frame observations |
| `build/reports/p2/representation-limits/device-frames-perfetto.csv` | unchanged frame schema from the paired Perfetto collection under a distinct immutable name |
| `build/reports/p2/representation-limits/device-frame-timeline.perfetto-trace` | raw `android.surfaceflinger.frametimeline` trace for physical-present correlation |
| `build/reports/p2/representation-limits/device-frame-correlation.csv` | 200 raw token, clock, app-surface, display-frame, and physical-present correlations |
| `build/reports/p2/representation-limits/device-frame-perfetto-config.txtpb` | exact retained Perfetto config bytes |
| `build/reports/p2/representation-limits/device-frame-perfetto-query.sql` | exact retained correlation and validity query bytes |
| `build/reports/p2/representation-limits/device-frame-perfetto-tool.txt` | source/profile/build/APK/package/tool/command identity and paired artifact checksums |
| `build/reports/p2/representation-limits/device-profile.txt` | exact physical profile fields and their sources |
| `build/reports/p2/representation-limits/device-logcat.txt` | scoped fatal, ANR, GC, thermal, and measurement logs |

After each final collection, this document must add byte length and SHA-256 for every raw file.
An artifact is not decision evidence if its metric boundary, commit, candidate, workload, sample
count, or profile cannot be recovered from the file and this ledger.

## Named physical minimum profile

This profile was fixed from read-only device queries before the first physical instrumentation
measurement. Each value says whether it was reported by the device/tool or fixed by this
measurement protocol. The device's hardware serial is intentionally not retained in evidence.
This is the selected M2 performance-reference device, not evidence of the supported Android API
floor or a claim that every lower-memory device is supported. Its UMS9360 application behavior
is the reference used for the representation decision; compatibility floors remain governed by
the Android configuration and separate supported-device verification.

| Field | Value | Source / reported or inferred |
| --- | --- | --- |
| Stable profile ID | `NENE-P2-ALLDOCUBE-IPL80MP-A16-API36` | fixed by this ledger before instrumentation |
| Manufacturer and model | ALLDOCUBE iPlay80miniPro; product/device `iPlay80miniPro` / `T830` | `getprop` reported |
| SoC and ABI | Spreadtrum UMS9360 (`ums9360`); `arm64-v8a` | `getprop ro.soc.*`, `ro.board.platform`, and `ro.product.cpu.abilist` reported |
| Physical RAM | 7,937,848 kB reported by kernel (approximately 7.57 GiB usable) | `/proc/meminfo` `MemTotal` reported |
| Android release, API, and build fingerprint | Android 16; API 36; `ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94010:user/release-keys`; security patch 2026-05-05 | `getprop` reported |
| ART/runtime | ART 2.1.0 arm64 on a `user` build; heap growth limit 256 MiB, heap size 512 MiB, heap start 16 MiB | `dalvikvm -showversion` and `getprop dalvik.vm.*` reported; exact test-process values are emitted before workload sampling |
| App variant and commit | `debug` plus `debugAndroidTest`; `5b9675a5a77186c9ce5e5095bc88b5a485ad18fc` | Git and Gradle route reported |
| Display resolution and refresh rate | physical 1200 x 1920; active/supported mode 90 Hz; physical density 320 dpi with 272 dpi override | `wm` and `dumpsys display` reported |
| `memoryClass` and `Runtime.maxMemory` | 256 MiB and 268,435,456 bytes | `ActivityManager.memoryClass` and `Runtime.maxMemory()` reported in physical CSV metadata before workload samples |
| Power mode | awake; low-power mode disabled; USB powered | `dumpsys power`, `settings get global low_power`, and `dumpsys battery` reported at pre-run query |
| Battery level and charging state | 69%; USB powered/charging; battery temperature 30.7 C | `dumpsys battery` reported at 2026-09-01 19:10 JST |
| Thermal status before/during/after | pre-run overall status 1, skin 37.482 C, SoC 40.08 C; post-run overall status 1, skin 37.827 C, SoC 38.365 C; all named sensor statuses 1 | `dumpsys thermalservice` reported at 2026-09-01 19:10 and 19:18 JST; the preliminary CSV does not contain an in-run checkpoint |
| Background-process and network conditions | unconstrained user background/network state; no process kill, network mutation, or device reset; therefore preliminary screening only | reported non-invasive condition; final validity conditions are fixed in the protocol above |
| Warmup, sample, restart, and GC protocol | preliminary screening: five warmups and twenty samples; final tail batches: five warmups and 200 samples; a fresh gateway per sample; two explicit Java GC/finalization passes only before post-GC memory capture | preliminary route fixed before its run; expanded final protocol fixed before candidate or frame collection |
| Connected transport and measurement tools | one physical device over USB ADB; AndroidJUnitRunner; `Debug.getRuntimeStats`, `Debug.getMemoryInfo`, `Runtime`, `ActivityManager`, `dumpsys`, and host SHA-256 | device/tool reported and protocol-fixed |

The final tail collection refreshes only the mutable software and run-condition fields of that
stable hardware profile. Before installing a new measurement APK, read-only queries on 2026-09-02
reported Android 16/API 36 build `94110`, security patch 2026-06-05, 1200 x 1920 mode 1 at 90 Hz,
low-power mode 0, interactive USB power at 100% battery, and overall thermal status 1. The exact
run-time values and source commit remain report metadata and checkpoint data rather than being
inferred from the preliminary build `94010` rows.

## Completion determination

P2-01 has no remaining evidence or implementation blocker. The exact semantic contract, flat
packed storage, typed conservative caps, canonical bitmap handoff, boundary rejections, clean
physical command tails, and independent retained-memory/PSS result are complete. Historical
current-path, tiled/COW, combined-GC, frame, and rejected Perfetto observations remain diagnostic;
none is relabelled or used in place of the selected lane evidence.

Strict SurfaceFlinger/Perfetto physical-present correlation remains intentionally deferred to the
renderer milestone. Multi-step history behavior, new-document UI, pencil/eraser UI, and palette UI
remain owned by their later Issues. They do not justify more candidate matrices, maximum searches,
per-sample full-state verification, or a second storage path in P2-01.

P2-02 and representation-dependent P2-04 may become ready only after the focused PR is merged,
Issue #38 is closed, and the required external-state read-back confirms that state. This is a
workflow dependency, not an unresolved technical condition in ADR 0005.

## Completion record

| Decision input | Selected evidence |
| --- | --- |
| Accepted semantic contract | straight, unassociated sRGB RGBA8; hidden RGB at alpha zero preserved; transparent black blank; replacement pencil and canonical-blank eraser; value-owned palette semantics |
| Accepted storage candidate | one private row-major `RRGGBBAA` `IntArray` snapshot/surface path with packed patch triplets and shared directional inverse |
| Three-candidate comparison | current object baseline rejected; flat packed selected; preselected tiled/COW T16 rejected at dense-apply p95 9.126 ms |
| Conservative MVP axis and total-pixel caps | width/height 256; area 65,536 derived from typed axes; run-06 dense production p95 6.315231 ms and p99 6.985308 ms; typed axis cap-plus-one rejection |
| Conservative MVP raw-stroke and patch caps | raw 262,144 and patch 65,536; cap-minus-one/cap accepted and cap-plus-one typed before scan/copy/sort; dense patch supported by run-06 |
| Conservative MVP history-entry and retained-change caps | 64 entries and 524,288 changes; policy boundary tests plus independent retained owner at the exact caps; retained heap 9,541,424 bytes |
| Correctness lane | exact RGBA/alpha-zero/blank, pixels, equality/hash, order, containment, revisions, shared inverse round trip, invalidation, no-op identity, and atomic typed rejection pass |
| Latency and ART-tail lane | run-06 passes all five p95/p99 targets; 1,000/1,000 samples have zero blocking-GC increment; no per-sample forced GC or full-state scan/hash |
| Retained-memory and PSS lane | three independent invocations per candidate complete; flat median delta 7,352,320-byte heap and 7,570 KiB PSS, both about 80% below current |
| Renderer/frame handoff | selected-production `CanvasBitmapProjectionTest` and `UndoRedoEditorTest` pass on physical device; strict compositor correlation deferred |
| Physical profile ID | `NENE-P2-ALLDOCUBE-IPL80MP-A16-API36`; final run Android 16/API 36 build `94111`, security patch `2026-08-05` |
| Raw artifact checksum set | all prior diagnostic hashes plus six distinct migrated-production no-overwrite hashes recorded above |
| ADR 0005 acceptance commit | `dca5c14212e629b20dda2c0636d1be5876deac99`; final physical optimization source `94559fe852c55b45b570222218072fcbaecfe4c4` |

Related canonical evidence: [M1 Vertical-slice Baseline and Exit Proof](M1_EXIT_PROOF.md).

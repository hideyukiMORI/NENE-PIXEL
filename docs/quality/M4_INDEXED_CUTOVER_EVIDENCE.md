# M4 Indexed Cutover Evidence

Issue #106 / P4-02. Governing contracts: ADR 0022, ADR 0025 and
[P4 Indexed Cutover Verification Protocol](P4_INDEXED_CUTOVER_PROTOCOL.md) revision v7, identity
`nene-pixel-p4-indexed-cutover-verification-v7`.

This file records experiment `p4-indexed-v6-20260917-run5`, collected on 2026-09-22 under the
archived [revision v6](P4_INDEXED_CUTOVER_PROTOCOL_V6_HISTORICAL.md) and admitted unchanged by v7 as
the acceptance evidence of the host, command, memory and publication lanes. It states verdicts and
numbers exactly as the preserved records state them. It claims no result that a record does not
carry, and it does not re-judge, recompute or relabel a single slot.

## Experiment identity

| Field | Value |
| --- | --- |
| Experiment id | `p4-indexed-v6-20260917-run5` |
| Collected-under protocol | `nene-pixel-p4-indexed-cutover-verification-v6`, 36,164 bytes, SHA-256 `9eb541fd392ba989cea943fe9f96410938b84996649329acc82e815321fe7bd2` |
| Admitting protocol | `nene-pixel-p4-indexed-cutover-verification-v7` |
| Reservation manifest | `build/reports/issue-106/p4-preflight-manifest-v6-3fd7817-run5b.json`, schema `nene-pixel-p4-indexed-preflight-v6`, SHA-256 `4f2adc47d71c79538677760d40902264ef3062df8c53a43b20eb7d609b1c4653` |
| Manifest copy inside the experiment | `build/reports/issue-106/p4-experiment-v6-run5/preflight.json`, identical bytes |
| Reserved at | 2026-09-22T04:49:30Z |
| Raw records | `build/reports/issue-106/p4-experiment-v6-run5/<slot>/` |
| Candidate production / build commit | `9fde9352e93f4421dc411f7a72a52fcf5a9a2ed7` / `3fd7817e44910b6289556d9407f5426e7ffb0dbc` |
| Baseline production / build commit | `2dd4e01e3bbe88967237cde4e28412d2962fd590` / `0b605481ad97ee3726864e556e6519f3a862271f` |
| Candidate production tree | `917d08c404d04dd739dfcab3bd83ed3b2530069271fd96958887f7f0a352a57b` |
| Baseline production tree | `c5145871ff9d82bcaae1ef7f5d4664b28aa6decb46677a0f3a0311afdcdf062e` |
| Device | serial `T830128GB26321131293`, profile `NENE-P2-ALLDOCUBE-IPL80MP-A16-API36`, ALLDOCUBE iPlay80miniPro, API 36, fingerprint `ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94111:user/release-keys` |

The candidate production commit is `9fde935`; `2522b0c` and `3fd7817` are documentation and
measurement overlays whose production tree is byte-identical to it, which is why the build commit
differs from the production commit and neither aggregate is relabelled as the other.

The four candidate APKs of that build, recorded in `build/reports/issue-106/apk-identity-3fd7817.txt`:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `app/android` debug | 11,914,459 | `51f094270e6f4fae721f39927839da82cd3696e9cb123cd4fef0d131b6628764` |
| `app/android` androidTest debug | 1,335,833 | `e95b0fade020b184d76931a87aefcaafeb9063a949d1af24de421c8066a6bfd6` |
| `adapters/persistence` androidTest debug | 8,000,879 | `9acf17486a544af0ae5edcb1b0f887ca1448590d0389365c11be7de0caa895a7` |
| `app/android` benchmarkRelease | 1,406,545 | `b12454c4d9d88296e0e510b12bb407e47f64a58b7e4d9fa01ed98bc3e65bbd01` |

The frame lane ran the benchmarkRelease artifacts: `b12454c4...` for the candidate and
`b0f8e5ce...` for the baseline build `0b60548`.

## Collected slots

The fixed v6 order has 33 slots. Run5 collected 32 of them and stopped at the first
`PERFORMANCE_FAIL`; `frame-4-baseline-decision` was never started.

| Lane | Slots | Verdict |
| --- | ---: | --- |
| Host codec/recovery/import | 5 / 5 | `valid-descriptive` |
| Production command latency | 2 / 2 | `pass` |
| Retained history and import memory | 20 / 20 | `pass` |
| Physical AtomicFile publication | 2 / 2 | `valid-constants-retained` |
| Actual-app frame | 3 / 4 | 2 `inconclusive`, 1 `PERFORMANCE_FAIL` |

Under v7 the first four lanes, twenty-nine slots, are this Issue's acceptance evidence. The frame
lane is Issue #120's.

## Lane 1: production command latency and ART blocking GC

Schema `nene-pixel-p4-indexed-command-latency-v1`. Gate per workload, nearest-rank over all 200
rows: p95 at most 8.0 ms, p99 at most 16.67 ms, at least 190 of 200 rows with zero blocking-GC
increment. Baseline has 1,200 rows over six workloads, candidate 2,200 over eleven.

| Workload | Baseline p95 | Baseline p99 | Candidate p95 | Candidate p99 |
| --- | ---: | ---: | ---: | ---: |
| `sparse_apply_stroke` | 1.210 ms | 3.088 ms | 1.072 ms | 1.184 ms |
| `dense_apply_stroke` | 6.540 ms | 6.848 ms | 6.196 ms | 6.425 ms |
| `dense_eraser_stroke` | 6.707 ms | 7.032 ms | 6.344 ms | 6.536 ms |
| `dense_same_target_no_op` | 1.023 ms | 1.089 ms | 0.990 ms | 2.617 ms |
| `dense_undo` | 4.200 ms | 4.816 ms | 2.465 ms | 4.454 ms |
| `dense_redo` | 2.472 ms | 4.463 ms | 3.001 ms | 4.578 ms |
| `palette_recolor_full` | not applicable | not applicable | 0.404 ms | 0.498 ms |
| `palette_default_only` | not applicable | not applicable | 0.366 ms | 0.459 ms |
| `palette_many_to_one_dense` | not applicable | not applicable | 5.646 ms | 5.889 ms |
| `palette_many_to_one_undo` | not applicable | not applicable | 4.450 ms | 4.899 ms |
| `palette_many_to_one_redo` | not applicable | not applicable | 2.219 ms | 2.264 ms |

Every workload of both roles is inside the absolute gate. Blocking-GC increments are zero in 200 of
200 rows for every workload except `palette_many_to_one_undo`, which has 199 of 200 and therefore
also passes its 190-row floor. The common-workload cross-role deltas above are descriptive; no
ratio is a gate.

The three candidate-only palette workloads are the ones that produced the preserved
`PERFORMANCE_FAIL` of run4 under revision v5. Measured against the same unchanged gate:

| Workload | run4 p95 under v5, `PERFORMANCE_FAIL` | run5 p95 under v6, `pass` |
| --- | ---: | ---: |
| `palette_recolor_full` | 10.953 ms | 0.404 ms |
| `palette_default_only` | 11.030 ms | 0.366 ms |
| `palette_many_to_one_dense` | 17.141 ms, p99 17.625 ms | 5.646 ms, p99 5.889 ms |

The run4 records stay exactly as collected under experiment `p4-indexed-v5-20260917-run4`. The
difference is the corrected candidate production source at `9fde935`, which replaced the per-pixel
allocation and the full-raster scan of an identity remap with a packed destination table, an
identity short-circuit and an allocation-free primitive scan, collected under the new identity that
the contract requires after a PERFORMANCE verdict.

## Lane 2: retained history and import-operation memory

Twenty fresh instrumentation processes, five per family. `Runtime.maxMemory` is 268,435,456 B, so
the retained Java-heap gate is 134,217,728 B; `ActivityManager.memoryClass` is 256 MiB; the
post-cycle growth limit is 2,684,354 B.

| Family | Retained Java heap | Retained Java delta | Median paired PSS delta | Post-cycle heap growth |
| --- | ---: | ---: | ---: | ---: |
| `baseline-common-drawing-history` | 9,491,120 B | 7,106,560 B | 5,575 KiB | 0 B |
| `candidate-common-indexed-history` | 5,657,264 B | 3,436,544 B | 2,982 KiB | 0 B |
| `candidate-palette-history` | 5,657,264 B | 3,440,640 B | 2,961 KiB | 0 B |
| `candidate-legacy-import` | 2,667,248 to 2,671,344 B | 364,544 to 368,640 B | -1,188 KiB | 16,384 to 20,480 B |

All twenty runs are `pass`: every retained Java heap is far inside 50% of `Runtime.maxMemory`, every
retained PSS is inside its paired baseline plus 60% of the memory class, every family median PSS
delta is inside 50% of the memory class, and every post-cycle growth is inside
`max(1 MiB, 1% of Runtime.maxMemory)`. The candidate common-history family retains about 48% of the
baseline common-history family's Java heap for the same canvas, visible colors, entry count and
change count; that comparison is descriptive, and each family passes the absolute gates on its own.

## Lane 4: host format, recovery and legacy-import latency

Five descriptive JVM observations, 5 warmups and 20 samples per group, OpenJDK 64-Bit Server VM
21.0.11 on Windows 11 10.0 amd64. They yield no percentile and no product performance PASS.

| Slot | Groups | Verdict |
| --- | ---: | --- |
| `host-project-baseline` | 2, 40 rows | `valid-descriptive` |
| `host-project-candidate` | 6, 120 rows | `valid-descriptive` |
| `host-recovery-baseline` | 5, 100 rows | `valid-descriptive` |
| `host-recovery-candidate` | 6, 120 rows | `valid-descriptive` |
| `host-legacy-candidate` | 4, 80 rows | `valid-descriptive` |

Minimum and maximum per group, in nanoseconds, as recorded:

| Slot | Group | Minimum | Maximum |
| --- | --- | ---: | ---: |
| `host-project-baseline` | `v1_max_encode` | 1,652,500 | 2,292,200 |
| `host-project-baseline` | `v1_max_decode` | 1,876,200 | 2,235,300 |
| `host-project-candidate` | `v1_max_exact_original_encode` | 1,678,400 | 2,255,000 |
| `host-project-candidate` | `v1_max_decode_legacy` | 1,515,900 | 1,798,500 |
| `host-project-candidate` | `v2_min_encode` | 12,800 | 34,900 |
| `host-project-candidate` | `v2_min_decode` | 22,100 | 59,500 |
| `host-project-candidate` | `v2_max_encode` | 436,600 | 619,100 |
| `host-project-candidate` | `v2_max_decode` | 814,400 | 2,891,800 |
| `host-recovery-baseline` | `v1_retired_encode` | 10,200 | 23,300 |
| `host-recovery-baseline` | `v1_retired_decode` | 3,800 | 33,800 |
| `host-recovery-baseline` | `v1_max_candidate_encode` | 3,464,700 | 5,188,100 |
| `host-recovery-baseline` | `v1_max_candidate_decode` | 1,853,900 | 2,292,400 |
| `host-recovery-baseline` | `v1_retirement_publish` | 2,325,800 | 2,599,400 |
| `host-recovery-candidate` | `v2_retired_encode` | 7,700 | 23,900 |
| `host-recovery-candidate` | `v2_retired_decode` | 3,400 | 28,100 |
| `host-recovery-candidate` | `v1_max_candidate_decode_legacy` | 1,609,400 | 3,761,500 |
| `host-recovery-candidate` | `v2_max_candidate_encode` | 1,261,800 | 3,527,200 |
| `host-recovery-candidate` | `v2_max_candidate_decode_current` | 732,800 | 848,900 |
| `host-recovery-candidate` | `v2_max_candidate_publish` | 2,804,600 | 3,384,900 |
| `host-legacy-candidate` | `classify_256_lossless` | 2,312,100 | 3,099,900 |
| `host-legacy-candidate` | `classify_257_conversion_required` | 63,600 | 143,700 |
| `host-legacy-candidate` | `classify_65536_conversion_required` | 2,426,200 | 3,055,800 |
| `host-legacy-candidate` | `reduce_65536_to_256` | 67,054,600 | 77,085,700 |

No completed sample reached the one-second gross-anomaly guard. The v1 groups are semantically
comparable across roles; the v2 and legacy-import groups are candidate-only and have no baseline
operation, so no comparison is fabricated for them.

## Lane 5: physical AtomicFile Candidate publication

Schema `nene-pixel-p4-indexed-publication-device-v1`. Both slots are `valid-constants-retained`,
each with 40 rows over two groups.

| Slot | Group | Structural bytes | Minimum | Maximum |
| --- | --- | ---: | ---: | ---: |
| `publication-baseline` | `baseline_v1_max` | 262,209 | 135.327 ms | 150.193 ms |
| `publication-baseline` | `baseline_v1_min` | 69 | 1.250 ms | 1.733 ms |
| `publication-candidate` | `candidate_v2_max` | 66,628 | 89.096 ms | 202.955 ms |
| `publication-candidate` | `candidate_v2_min` | 77 | 1.437 ms | 4.077 ms |

The candidate maximum-document maximum is 202.955 ms, at or below the 250 ms constant-decision
boundary, so ADR 0018's `AUTOSAVE_QUIET_MS = 1000` and `AUTOSAVE_LATENCY_CAP_MS = 5000` are retained
unchanged and no re-derivation is performed. No sample approached the 5-second invalidating bound.
The two roles write their respective structural maxima, 262,209 and 66,628 bytes, so the time
difference between them is descriptive and is not a speedup for equivalent bytes.

## Lane 3: frame lane, collected under v6 and owned by Issue #120

Revision v7 removes this lane from Issue #106's fixed order and acceptance. The three collected
records are preserved exactly as collected and are reproduced here without re-judgement.

| Slot | Family | Overrun p95 | Overrun p99 | UP-to-committed p95 | Verdict |
| --- | --- | ---: | ---: | ---: | --- |
| `frame-1-baseline-diagnostic` | `canvas16_tap` | 1.136 ms | 1.756 ms | 10.797 ms | `inconclusive` |
| `frame-1-baseline-diagnostic` | `canvas256_repeated_diagonal` | 8.368 ms | 9.608 ms | 12.316 ms | `inconclusive` |
| `frame-2-candidate-diagnostic` | `canvas16_tap` | 1.077 ms | 1.290 ms | 10.384 ms | `inconclusive` |
| `frame-2-candidate-diagnostic` | `canvas256_repeated_diagonal` | 8.628 ms | 9.400 ms | 11.489 ms | `inconclusive` |
| `frame-3-candidate-decision` | `canvas16_tap` | 1.012 ms | 2.048 ms | 10.177 ms | `PERFORMANCE_FAIL` |

`frame-4-baseline-decision` was never started. Fatal, ANR and process-death matches are zero in
every collected slot, no diagnostic result reached the `gross-regression` boundary, and the
decision collector stops at the first failing family, so `canvas256_repeated_diagonal` has no
decision data.

The failing gate is the absolute all-frame overrun nearest-rank p95 of at most 0.0 ms. The baseline
does not meet it either under the same conditions, at 1.136 ms and 8.368 ms, against 1.077 ms and
8.628 ms for the candidate; the two roles differ by at most about 0.3 ms inside a family. The
committed-result p95 stays between 10.2 and 12.4 ms for every family and role, inside its 33.33 ms
gate. The preserved `PERFORMANCE_FAIL` is therefore evidence about main's frame budget on this
device and about the absolute gate, and it is not evidence of a candidate regression. Under hide's
decision of 2026-09-22 that question, and the baseline-relative frame criterion that will replace
the absolute gate, belong to
[Issue #120](https://github.com/hideyukiMORI/NENE-PIXEL/issues/120). The verdict is not converted,
softened or recollected here.

### Lane 3 revision: collection `p4-indexed-v7-20260923-frame1` under Issue #120

Collected on 2026-09-23 between 06:04 and 06:57 UTC on the same iPlay80miniPro (API 36, 90 Hz,
1920x1200, rotation pinned to 1 during capture), release-like `benchmarkRelease` artifacts with the
accepted packaged profile compiled `speed-profile`, preflight manifest `108d6132...`, protocol
identity v7 with the Lane 3 revision of 2026-09-23. Baseline: production `2f0b617` (main at
collection time), measurement build `96fb058` (the Lane 3 tooling overlay), APK `3dacd8e9...`.
Candidate: production `b6c4cd9` (Issue #124 preview raster), measurement build `ce46ec7`
(`b6c4cd9` plus the same tooling), APK `6c825355...`. Every slot completed (`complete_run=true`,
attempt 1), fatal, ANR and process-death matches are zero, no family reached the gross-regression
boundary, and the installed package sha was read back on the device against the role's release-like
artifact before every slot. Decision slots measured 5 warmups and 50 samples per family
(100 operations, 1,000 frame rows each); diagnostic slots 5 and 10 (30 operations, 380 rows each).

| Decision slot | Family | Overrun p95 | Overrun p99 | UP-to-committed p95 | Relative margin p95 / p99 | Verdict |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| `frame-1-baseline-decision` | `canvas16_tap` | 1.272 ms | 2.504 ms | 11.195 ms | reference | `baseline-recorded` |
| `frame-1-baseline-decision` | `canvas256_repeated_diagonal` | 9.026 ms | 10.063 ms | 11.058 ms | reference | `baseline-recorded` |
| `frame-2-candidate-decision` | `canvas16_tap` | 1.178 ms | 2.582 ms | 10.343 ms | -0.094 / +0.078 ms | `pass` |
| `frame-2-candidate-decision` | `canvas256_repeated_diagonal` | 1.258 ms | 1.803 ms | 10.881 ms | -7.768 / -8.260 ms | `pass` |

| Diagnostic slot | Family | Overrun p95 | Overrun p99 | UP-to-committed p95 | Verdict |
| --- | --- | ---: | ---: | ---: | --- |
| `frame-3-baseline-diagnostic` | `canvas16_tap` | 1.364 ms | 2.144 ms | 10.722 ms | `inconclusive` |
| `frame-3-baseline-diagnostic` | `canvas256_repeated_diagonal` | 9.541 ms | 10.049 ms | 10.993 ms | `inconclusive` |
| `frame-3-baseline-diagnostic` | `canvas256_repeated_diagonal_window_x2` | 9.202 ms | 10.168 ms | 14.183 ms | `inconclusive` |
| `frame-4-candidate-diagnostic` | `canvas16_tap` | 1.269 ms | 3.219 ms | 12.185 ms | `inconclusive` |
| `frame-4-candidate-diagnostic` | `canvas256_repeated_diagonal` | 1.168 ms | 1.630 ms | 10.739 ms | `inconclusive` |
| `frame-4-candidate-diagnostic` | `canvas256_repeated_diagonal_window_x2` | 2.067 ms | 2.664 ms | 11.508 ms | `inconclusive` |

The candidate decision verdict is `pass` under rule `lane3-2026-09-23-relative`: every family's
all-frame overrun p95 is within +1.0 ms and p99 within +2.0 ms of the baseline decision reference,
the UP-to-committed p95 stays under 33.33 ms, and no fatal or gross-regression condition occurred.
Diagnostic slots are `inconclusive` by definition and judge nothing.

Deadline definition and attribution (Issue #120 acceptance): the frame deadline on this device is
the `WorkloadTarget` of vsync + 10.0 ms at 90 Hz (period 11.11 ms), neither 11.11 nor 16.67 ms.
Main's `canvas256_repeated_diagonal` overrun p95 of 9.03 ms is a measurement. The candidate differs
from main only by Issue #124's preview drawing path (a `PreviewRaster` drawn with one `drawBitmap`
in place of per-pixel `drawPixel` calls), and the same workload on the candidate measures 1.26 ms,
so 7.8 ms of the baseline overrun p95 is attributed to per-pixel preview drawing by measurement, not
by inference. `canvas16_tap` is unchanged within 0.1 ms at p95. The actual-size window at x2
(diagnostic only, 10 operations) adds about 0.9 ms of overrun p95 on the candidate over the same
stroke without the window; on main it is hidden under the per-pixel cost. The relative tolerance of
+1.0 / +2.0 ms remains the provisional value fixed before collection. The run5 v6 records above are
unchanged. Evidence: `build/reports/issue-120/p4-experiment-v7-frame1/` (captures `599e826c...`,
`49926e58...`, `4414eaba...`, `e2f7f00b...`).

## Lane 6: correctness, UI, lifecycle and bounded ownership

This lane has no sample population and no performance verdict. The physical functional checkpoint
of 2026-09-13 passed all 18 selected persistence, bitmap, conversion-dialog, lifecycle and
recovery-offer tests as five separately selected invocations, and the durable journey then passed
its five isolated stages; its raw JUnit output, installed APK byte copies and the wrapper's own
retained summary-writing failure are recorded in the
[daily report](../reports/2026-09-13-indexed-cutover.md) and its
[handoff](../reports/2026-09-13-indexed-cutover-handoff.md). That checkpoint predates the run5
candidate build, so it is reported as the functional evidence it is and is not presented as a
verification of `3fd7817`.

The run5 reservation manifest separately binds, at the run5 candidate build commit, one narrow host
correctness record and ten collector self-contract records, every one with exit code 0:

| Scope | Command |
| --- | --- |
| `candidate-host-narrow-correctness` | `:core:application:test` for `PaletteHistoryRetentionContractTest`, `LegacyReductionOwnerReplacementTest` and `LegacyImportHostEvidenceContract*` |
| `host-analyzer`, `preflight-contract`, `device-state` | `docs/quality/validate-p4-indexed-evidence.ps1` |
| `command-analyzer` | `docs/quality/validate-p4-command-evidence.ps1` |
| `memory-analyzer` | `docs/quality/validate-p4-memory-evidence.ps1` |
| `publication-analyzer` | `docs/quality/validate-p4-publication-evidence.ps1` |
| `frame-analyzer` | `docs/quality/validate-p4-frame-analysis.ps1` |
| `frame-collector-protocol` | `docs/quality/validate-m2-frame-protocol.ps1` |
| `slot-boundary` | `docs/quality/validate-p4-slot-boundary.ps1` |
| `bounded-native` | `docs/quality/validate-baseline-profile-evidence.ps1` |

Those are synthetic contract checks of the collectors, not performance samples. The final coherent
candidate still requires the protected canonical CI `quality` result; this file does not stand in
for it.

## Operational record

Three operational facts of run5 are recorded because they shaped the collection, and because each
preserved failure stays preserved.

Preservation and the non-debuggable install. Run5 stopped on 2026-09-17 before its no-sample UI
inspection because the tablet showed the keyguard; the harness sends WAKEUP and a stay-on-while-
plugged setting but never dismisses a lock screen, and it was not made to. On resume, the
2026-09-17 19:02 asset-preservation guard was older than the 24-hour freshness window that the
manifest's `created_utc` is measured against, so a fresh guard had to be taken first. That attempt
failed at 13:16 JST with a not-debuggable refusal, because the earlier failed inspection had left
the baseline benchmarkRelease APK installed on the app package and the preservation snapshot goes
through `run-as`. The failed guard `issue-106-user-recovery-20260922-1316` is kept as a failed
attempt. The candidate debug APK `51f09427...` was reinstalled with a data-preserving
install-replace, an unchanged `firstInstallTime` and the debuggable flag were verified, and guard
`issue-106-user-recovery-20260922-1324` succeeded. No uninstall and no data clear was used at any
point.

Inspection. The failed inspection attempt `p4-inspection-11` is retained untouched and the
successful no-sample geometry inspection was written to the new directory `p4-inspection-12`,
followed by `device-state-preflight-4`. No inspection directory was reused or overwritten.

The S12 reservation failure and the fill literal. The first reservation attempt failed before it
touched any directory or the device, with a candidate production-tree mismatch raised by
`p4-indexed-preflight.ps1`. The untracked fill script still named the run4-era candidate production
commit `f5b41a8e...` while the run5 build contains `9fde935`, which changes `PaletteRemap.kt`,
`PixelSnapshot.kt` and `PaletteRemapApplication.kt`; the production trees are `34f84bdd...` for
`f5b41a8` and `917d08c4...` for `9fde935`, and the manifest's own `production_tree_sha256` was
already the latter. The literal was corrected to `9fde9352e93f4421dc411f7a72a52fcf5a9a2ed7`, the
manifest was regenerated as `p4-preflight-manifest-v6-3fd7817-run5b.json` from the same inputs, and
`p4-preflight-manifest-v6-3fd7817-run5.json` is kept as the failed attempt. The experiment id was
not consumed by the failed attempt, because no output or preflight directory had been created, so
the same id was reserved. The fail-closed manifest refused the collection rather than measuring the
wrong pair, which is the behaviour the contract asks for.

## Reuse statement

Admitting these results under v7 is a QLT-012 identity-bound reuse of an existing record, not a
relabelling and not a new experiment. Each of the twenty-nine admitted records carries
`nene-pixel-p4-indexed-cutover-verification-v6` as its `protocol_id` and
`4f2adc47d71c79538677760d40902264ef3062df8c53a43b20eb7d609b1c4653` as its `preflight_sha256`, and
each stays under its own slot directory in `p4-experiment-v6-run5/`. Nothing is renamed to v7, no
verdict is recomputed, no sample is selected, pooled, discarded or replaced, and no slot is
collected again. Reuse is admissible because v6 and v7 define identical populations, workloads,
metrics, numeric gates, warmups, sample counts and finite budgets for these four lanes, and because
the measured artifacts, the device profile and the collectors are unchanged between the collection
and this record. The v6 bytes the records were collected under are archived at
[P4_INDEXED_CUTOVER_PROTOCOL_V6_HISTORICAL.md](P4_INDEXED_CUTOVER_PROTOCOL_V6_HISTORICAL.md).

## Remaining work

The frame lane has no Issue #106 verdict and Issue #120 owns it. The final coherent candidate
requires the protected canonical CI `quality` result. No routine clean, cache bypass, forced cold
build, duplicate local full run or automatic profile regeneration was performed for this record, and
none is authorized by it.

Rules: QLT-006/008/010/011/012/013/014/015/016. Waivers: none.

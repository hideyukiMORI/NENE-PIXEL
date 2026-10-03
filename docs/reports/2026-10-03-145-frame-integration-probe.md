# #145 frame integration probe — 2026-10-03

Scope: read-only investigation except this new report. HEAD `3f9d80bc92c8e15c60aaf5ade04f08b09db4f8de`, Draft PR #187. No device, build, test, production edit, commit or external mutation. Issue #145 / P4-05d; ADR 0030/0031/0035; ARC-001/004/007/011; CMD-001/002/008; QLT-011–019. Active waiver: none. Decisions remain with the parent design seat.

Sources: worktree AGENTS.md and mandatory governing documents; live Issue #145 body; `2026-10-03-layer-gate-session-handoff.md`; earlier frame-phase/comparator probes; prospective P4 protocols; actual collector/analyzer/preflight/private-session code. Historical #142 FAIL remains unchanged. The earlier probe's unsplit-versus-split choice is superseded by the accepted separate DOWN-only tap.

## 1. Workloads, slots, budgets and schema

`measure-m2-frame.ps1:244–285` has actual-app-frame v8 / experiment v5, exactly two decision families and a third X2 diagnostic family. `p4-indexed-preflight.ps1:14–21,163–187` binds protocol/preflight v7, Issue #142 and one baseline `8120c06`; `Get-P4FrameSlotCatalog` returns four slots. `Get-P4FrameWrapperBound:108–130` derives `300 + 15 * families * (5 + samples)`, families 1..3. `p4-indexed-frame-analysis.ps1:6–20` binds verdict `lane3-2026-09-23-relative` and 33.33 ms. None implements #145.

The parent candidate uses one phase experiment, groups in order single / layers16 / underlay, each B decision → C decision → B diagnostic → C diagnostic. Arithmetic:

| Group | Decision families / diagnostic families | Measured operations, both roles | Warmups, both roles | Per-role decision / diagnostic bound |
| --- | --- | ---: | ---: | ---: |
| single | 2 / 3, X2 diagnostic | 260 | 50 | 1950 / 975 s |
| layers16 | 2 / 2, tap + diagonal | 240 | 40 | 1950 / 750 s |
| underlay | 1 / 1, diagonal | 120 | 20 | 1125 / 525 s |
| Total | 12 slots | 620 | 110 | All <=3600 s |

Single measured is **260**, correcting this probe's preliminary chat typo of 240; total 620 is unchanged. Total operation budget is 730. Collector bounds sum to 14,550 s; existing wrapper adds 90 s cleanup +120 s analysis per slot, yielding a 17,070 s frame-wrapper ceiling. This is a finite upper bound, not expected duration or the whole phase's memory/publication/profile/preservation budget. Fixture setup/provider install/hash/SAF selection must fit the declared 300 s setup reserve or receive a prospectively derived extra bound.

Six decision slots alone do not retain the registered X2 diagnostic. The 12-slot candidate retains existing four-slot semantics inside each comparison group without mixed per-family sample counts. One candidate slot with all five decision families would be 275 operations /4425 s and violate the current 3600 s cap; group partition avoids changing that cap.

Minimal contract additions in existing preflight: closed group catalog (workload specs, baseline production, fixture policy); closed 12-slot catalog (group, comparison role, artifact role, group-local index, phase ordinal, warmups/samples/bound); source/artifact references; new protocol/preflight/frame/experiment/verdict IDs. Preserve historical four-slot/default identities. Expose this as one pure contract consumed by existing plan/collector/analyzer, not duplicate arrays in each file. A preparatory contract/catalog-only slice may refuse all prospective collection until consumers are connected; it must not accidentally admit v7 as #145.

Collector touchpoints: parameters (`ComparisonSequenceIndex` range 1..4), constants/catalog, comparison order at :455–467, experiment identity construction :535–632, `Test-RunStateIdentity:674–701`, predecessor check :800–811, run-state :880–911, family loop :2592 onward, geometry-only inspection and metadata. Analyzer: workload order, `Read-P4FrameBaselineReference:132–167`, `Test-P4FrameCapture:169–511`. Resolve baseline analysis by exact same group, decision slot, experiment/schema, expected production/build/APK, fixture and retained seal/hash; the current lookup in `analyze-p4-indexed-slot.ps1:111–112` chooses the first baseline decision and becomes wrong with three groups.

Use new-schema M5 input p95 <=16.67 ms; relative all-frame p95 <=baseline+1 and p99 <=baseline+2. Historical schemas keep historical semantics. Collector's `Get-CompletedFamilyResult:794–798` also carries legacy 33.33 ms in its unused `Passed` property; prospective use must not expose that as acceptance. Analyzer remains sole verdict owner. Retain zero fatal/ANR/process-death and existing declared gross boundaries. Diagnostic numbers never become decision thresholds.

## 2. One manifest: group-local two-role projections versus four artifact records

Simply adding `group` to the existing global `roles.baseline/candidate` cannot encode three immutable baseline commits. Changing a manifest between groups violates immutable manifest/seal bindings. Three separate manifests/child experiments would additionally need a phase coordinator to prove global order/stop budget, one preservation lifetime and final installation; that is more than a field addition.

Two-role projection variant: store closed groups with each baseline source and a shared candidate reference; resolve one immutable group-local B/C projection for existing functions. No candidate source record should be independently copied three times. Most APIs can retain comparison-role `baseline/candidate`, but preflight must validate every baseline and the shared candidate, wrappers must use group-aware lookup, and the phase chain remains global. A group projection is an internal view, not a second editable manifest or child experiment.

Four-record variant: registry contains single-baseline, layers16-baseline, underlay-baseline and one candidate; slot has group, comparison_role (B/C), artifact_role and baseline reference. Keep B/C ValidateSet for comparison semantics; resolve immutable source record through one existing-preflight function. Do not repurpose Slot.role as artifact key everywhere: baseline-only checks and candidate-only inventories would otherwise silently change meaning.

Observed `.roles` reference **lines**, not estimated edits: preflight 8, device-lanes 6, wrapper 14, collector wrapper 1, slot analyzer 4: **33 lines across five files**. Many reference expressions require the same resolver. Additional hard-coded B/C loops and comparison-order logic are not included in that count.

Hard places shared by either one-manifest variant:

- `Assert-P4RoleSource:830–876`: baseline check :841 hard-codes one production. Make expected source explicit per group while keeping unchanged-production-tree and overlay identity checks. `Get-P4ExpectedMeasurementPaths:296` adds candidate-only host evidence by name; separate comparison role from artifact key. Baseline roles currently require all four APK kinds and shared host sources even when their phase purpose is frame only; whether to retain or reduce this requirement must be explicit, not skipped silently.
- `Assert-P4GitLineage:488` assumes one B/C pair; apply declared comparator ancestry/equality policy to all three comparisons. `Assert-P4ManifestArtifacts:942` loops two roles; validate each unique artifact record once.
- `Get-P4LanePackages`, `Get-P4DeviceLanePlan`, `Assert-P4InstalledApk`, `Get-P4FrameCollectorParameters:112–175` use role lookups. Frame parameters can still be resolved to exactly one B/C pair while receiving group identity and the chosen catalog.
- `invoke-p4-indexed-slot.ps1:Get-P4ManifestPackages:118`, admission/worktree checks, process quiescence, execution and capture seals must bind the resolved source, global phase ordinal and group. Tooling repository root must remain explicit; currently several commands use roles.candidate.worktree.
- `measure-m2-frame.ps1` experiment.json currently stores exactly one baseline/profile pair. It cannot accept the next group's changed baseline in the same experiment directory without a new schema holding the entire catalog or a group-bound immutable run-state. Preserve one phase root and include group in slot paths, raw metadata and checks.
- Global 1..12 ordinal and group-local 1..4 index are different facts. Existing :681 count choice (`SequenceIndex <=2`) and prior-slot lookup must consume slot specs; modulo arithmetic alone cannot validate predecessor source/group or stop conditions.
- `analyze-p4-indexed-slot.ps1:174–200` must resolve source, geometry, baseline and frame-slot inventory for the exact group. `New-P4FrameSlotRecord:1310` currently has only directory/files/time; new record should bind group/slot/phase identity.

The four-record registry with one group-aware resolution path expresses source reuse without independent candidate copies and without child experiment/session branches. This is a code-scope finding, not a final design choice. Artifact requirements for non-frame lanes remain to be decided by the parent.

## 3. 16-layer SAF setup and checkpoint

The pinned fixture is already defined, host-verified and asset-merged. It is revision 0, palette slot0 opaque black, 16 visible fully covered layers (IDs/fill indices1..16), v3 1,182,862 bytes, SHA256 `165f62d180533849ce1a4ef1625cd3971e445f2dca60ef7b9b46fedaafa0b3ec`. No fixture regeneration or host revalidation is needed for this investigation.

Current `New-DocumentThroughUi:1749–1789` only creates empty single-layer documents. Add a fixture branch in the same family preparation flow: stage exact packaged asset in fresh **test provider** storage, hash before app access, open File→Load→real system DocumentsUI picker→provider file, await successful load, assert canvas/clean/empty history/Pencil/slot0. Both layers16 families may reuse the same loaded document across one-Undo resets; initial setup and batch-boundary checks stay outside timings. No direct app-private document injection or test-controller install.

Existing provider: `app/android/src/androidTest/java/.../acceptance/AcceptanceDocumentsProvider.java`; authority `io.github.hideyukimori.nenepixel.test.acceptance.documents`; root `NENE-PIXEL Acceptance`; storage `files/m3-acceptance`; filename regex **i89-[a-z0-9.-]{1,90}**. It supports octet-stream and PNG, fresh-file creation and actual URI grants. Existing plain `maximum-layered.nenepixel` is rejected. Either use a fresh allowed i89-145... staging name (same bytes) or narrowly extend the test-only filename contract in every relevant overlay. Do not claim a new provider is necessary.

`test_debug` must be installed and its provider source/manifest/artifact proven on both historical overlays; the frame plan currently installs only app_debug for quarantine and release-like for timing. Provider Java is framework-only; running a Kotlin instrumentation workload against release-like is not needed just to serve bytes. Preflight `P4MeasurementPathPatterns` excludes this Java provider and androidTest manifest while production-tree hash excludes all androidTest: extend the existing measurement inventory to pin them and relevant fixture source/packaging entry, rather than leave a blind spot.

Asset packaging init script already maps `docs/quality/fixtures/p4-layer-phase` to both existing AndroidTest APKs. `Assert-P4ApkIdentity/Get-M2ZipEntrySha256` are existing ZIP/hash seams for a required `assets/maximum-layered.nenepixel` length/hash check. Merged asset proof is not APK-entry proof. Provider storage SHA must match APK entry and contract.

`Assert-CleanWorkloadReady:1706–1727` currently proves only geometry/clean/Undo/Pencil; add cheap slot0 selected check (`editor_palette_entry_1`, selected semantics) and fixture identity reference. `Assert-CommittedResult` checks dirty/Undo/Redo, not pixels. One `Invoke-UndoToCleanCheckpoint:1613` returns exact clean history position; full fixture/pixel/visual correctness oracle belongs in pre-timing and fixed family-boundary checks, not every sample.

Baseline169b592 has no layer panel. Canonical load already selects top layer16; prove pinned bytes + production decoder/load/top-selection policy + successful UI load/canvas/clean, and use visual preparation oracle. Candidate layer rows can be extra correctness evidence only; do not add a baseline panel or require nonexistent rows. Capturing/saving/re-decoding a checkpoint through actual SAF is possible outside timing if stronger exact content proof is required by design.

## 4. DOWN-only first-preview association

The protocol already fixes `canvas256_layers16_tap` DOWN→100ms→preview capture→UP→committed capture, no MOVE. The diagonal keeps DOWN+16 MOVEs,20ms MOVE dwell and100ms post-MOVE dwell, unsplit. `Invoke-PreviewEventSequence:1811` and `Invoke-CommitEventSequence:1838` already consume move_event_count; copying the event specs into the canonical group contract suffices for gesture shape.

`Get-FrameRows:1344–1431` retains row_index, FrameTimeline ID, intended/start/input/deadline/completion timestamps, event_count and per-row service/overrun. `Get-OperationPhaseCapture:1433–1491` rejects zero/missing/flagged/lost rows; `Get-OperationTiming:703` validates pooled preview/commit ordering but uses independent min input / max completion. It is not a first-preview accessor.

Minimal addition: for layers16 tap only, select the earliest valid preview **row**, retain that row's identity and its own HandleInputStart/FrameCompleted/FrameDeadline, and derive `(completed-input)/1e6` and `(completed-deadline)/1e6` from that row. Suggested sample fields: first_preview_frame_timeline_vsync_id, first_preview_row_index, first_preview_handle_input_start_nanos, first_preview_frame_completed_nanos, first_preview_deadline_nanos, first_preview_service_ms, first_preview_overrun_ms. All frames remain in tap all-frame population.

Make earliest ordering explicit (IntendedVsync/FrameStart ordered row, unique IDs; reject ties/reordered/non-monotonic ambiguous intervals); do not select one input minimum and an independent completion minimum. No first-preview physical gate is added. Analyzer independently finds the same row, validates sample fields exactly against raw frames, recomputes service/overrun and reports first-preview distributions. Existing analyzer verifies uniqueness/flags and recomputes UP latency, but does not currently recompute every raw per-frame overrun or prove full monotonic ordering; prospective contracts should validate raw positive/order fields and derived values too.

Pre-timing oracle must prove visible first preview on the pinned full-coverage fixture. `gfxinfo` is app-frame service evidence, not injection/physical pixel presentation. A DOWN-only interval is meaningful only with setup quiescence, immediate reset before input, no earlier in-flight setup draw entering the population, and no extra input/mutating UI between reset and capture. Define a bounded no-sample association proof/idle control at preparation, not expensive observers between timed samples. Executable negative cases: no rows; flagged/lost rows; tie/nonmonotonic order; repeated ID; foreign operation/event count; mixed row timestamp pair; commit preceding preview; orphan first-preview fields on long diagonal.

## 5. Underlay fixture operation and smallest proof

Parent candidate: one fixed synthetic 1024×1024 PNG, shown on a fresh256×256 empty single-layer document; factory fitted placement `(0,0,0.25)`, alpha128, resting; no slider/adjust gesture. This is the smallest existing UI route if image bytes/dimensions/render oracle are pinned and fresh load/pick is proved.

Actual production blobs, identical in baseline f92b100 and HEAD:

| File (core/application/... prefix) | Blob |
| --- | --- |
| editor/RuntimeReferenceImageOperations.kt | 4c5035325f638157245b896a39a3d22f97a961fd |
| workspace/underlay/ReferenceUnderlay.kt | 94d0aafc153a23a0229a512b19df64155956a1f7 |
| workspace/underlay/UnderlayPlacement.kt | 29ad1d94b5483fd627667da1a209935be589aafd |
| workspace/underlay/UnderlayOpacity.kt | e908ff4b239cff7a579b1ac0484ff09258371007 |

Successful image-pick completion uses `ReferenceUnderlay.placed(image, canvas)` (`RuntimeReferenceImageOperations:54`); placed uses fitted, DEFAULT128, Shown, Resting (`ReferenceUnderlay:82–95`). fitted uses min(canvas/image dimensions), centered (`UnderlayPlacement:43–54`). Therefore exactly1024²→256² gives .25/0/0 by construction. This source+input proof works before #172 and needs no remembered-state file as baseline oracle. Row `editor_underlay_visibility` offers Hide when currently shown; rounded50% may supplement but never establish exact128. No opacity input should be sent.

Use Layers→underlay_pick→actual SAF image pick through the same provider; verify successful image outcome, source/provider SHA, canvas, shown control/closed panel/absent adjust bar, and prepared visible pattern. `AndroidBitmapReferenceImageDecoder:sampleSize` leaves1024² at sample1; source/result bounds permit it. Prefer PNG with no density/orientation/color metadata ambiguity. Pin decoded dimensions/pixels separately before timing where necessary; image identity must not silently depend only on encoded dimensions. #172 candidate new-document/load clears then asynchronously attempts remembered restoration, so fresh work identity/no matching measurement memory and successful new pick must be established; preserved originals are isolated for the phase.

Underlay family uses existing diagonal events and clean one-Undo reset; add one underlay setup/verification branch in collector. Keep image read/hash, visual oracle and any candidate .state cross-check outside samples. Never replace baseline proof with remembered state. Parent must still fix exact PNG content/hash, document palette/paint index and visible preview/committed oracle.

## 6. Phase preservation-v2 / final-install binding

Existing canonical seams are `New-P4PrivateSnapshot`→`Invoke-P4PrivateIsolation`→`Invoke-P4PrivateRestoration`. `p4-device-private-session.ps1:151` returns preservation-v2 with status preserved, session/experiment/device/package/snapshot/plan/inventory/originalAPK/source hashes. `Read-P4VerifiedPreservation` in restore :45–117 already verifies those immutable bindings. Reuse it for prospective preflight; do not invent a weaker status-only validator.

Current `p4-indexed-preflight.ps1:615–639` requires leaf preservation.json, **v1**, serial/guard and24h freshness. It cannot read current native result names or schema. New admission needs v2 path/hash/context, status, exact phase experiment/session/serial/package, pinned implementations and retained snapshot artifacts. Do not relabel the historical160746 snapshot after source changes. Prospective reservation may validate prepared artifacts before isolation, while collection-stage admission requires completed isolation; define stages explicitly so manifest-before-snapshot versus snapshot-before-manifest does not become cyclic.

`Invoke-P4SlotRestoration:592` and frame collector finally restore rotation/stay-awake/process quiescence; they are **slot settings** cleanup, not original user-data/APK restoration. Keep phase guard live across all12frame and other phase slots. Existing recovery quarantine handles measurement output cross-version compatibility; it must never consume original guard. Candidate underlay records with reused IDs may also need bounded per-slot handling, retaining outputs under the existing quarantine contract rather than deleting memory or inventing a second policy.

There is no phase-wide caller of private restoration in existing slot scripts. Extend the existing reservation/slot orchestration with explicit phase lifetime and one terminal success/abort restoration boundary; do not run original restoration after each slot. Binding the final v2 restoration into phase completion is necessary even when all frame verdicts pass. No new competing collector is needed.

Critical install seam: `Assert-P4InstalledApk:929–967` writes immutable install-Kind.json immediately after install/hash readback; frame collector :2528 calls adb install directly and only records installed hash in final successful metadata (:2858). If it fails after release-like install but before metadata, a terminal caller has no trustworthy last-installed hash. Write immutable install intent/result immediately at that existing seam and bind its artifact/slot/phase/global order into a phase install ledger. Include quarantine's debug installs too. Pass the last established app-install expected hash to `Invoke-P4PrivateRestoration -ExpectedInstalledApkHash`; current device hash is an observation, not the source of expectation. An interrupted/failed install with uncertain resulting APK remains a refused terminal state; never guess or auto-resume.

Context.output_directory for snapshot/isolation/restoration must remain the same canonical phase preservation directory. Slot evidence can reside below the phase root but private helpers reject artifacts outside their pinned directory. Preserve failure records, all measurement outputs, original bytes/mtimes and originalAPK; phase completion is refused on failed/unknown restoration. Decide fixed stop/restore time budgets prospectively; frame slot operation budget alone does not bound private full-inventory/archive I/O.

## Open design points (9)

1. Final protocol/schema/verdict names and one-manifest registry/projection shape; retain unchanged historical IDs.
2. Exact global12slot order, stop rules and correction collection budget; phase-wide bounds including setup/GC/storage/preservation/profile work.
3. Narrow preparatory contract slice's fail-closed marker and which executable readiness checks eventually unlock it.
4. Which source records require all four legacy APK kinds/host evidence versus only phase-used artifacts, without weakening production/profile checks.
5. First-row ordering rule and bounded executable proof that DOWN-only population excludes preceding in-flight setup frames; pre-timing visible oracle.
6. Provider staging name policy (reuse allowed i89-145... versus test-only regex change), overlay provider pinning and fixed actual picker locale/root/file path.
7. Synthetic1024²PNG content/hash and decode oracle; fresh one-layer paint palette/index and underlay shown/resting invariance proof.
8. Frame/storage/memory complete phase reservation stages and preservation-v2 integration; per-slot measurement-underlay handling and test-provider output lifetime.
9. Immediate install ledger/failure semantics and one terminal restoration/completion record, including fixed restoration deadline.

## Verification/report status

Executed read-only commands: Get-Content/rg, git ls-tree/rev-parse/status and `gh issue view145 --json title,body`; no test or benchmark command. New report only; no behavior/schema/governing-document implementation change. No PASS/performance claim. Reused tests: none executed here; earlier successful fixture/private-transfer/native/snapshot/session/restoration results remain available under the session handoff identities. Unrelated failures: known #186 remains separate; none new. Risk: unresolved admission decisions prevent #145 collection. Active waivers: none.

# Issue #145 phase analyzer integration probe

Scope: read-only source investigation except this new report; HEAD `001337d32b13a2d36ba7d7d2b560b1039f71280e`. No tests, build, device, commit, push or external mutation. Parent owns manifest/collector design and adjudication. Issue #145 / P4-05d; ADR 0030/0031/0035; QLT-011–019. Active waivers: none. Phase admission stays CLOSED.

Source abbreviations below are repository-relative:
- A = `docs/quality/measurements/p4-indexed-frame-analysis.ps1`
- W = `docs/quality/measurements/analyze-p4-indexed-slot.ps1`
- C = `docs/quality/measurements/p4-indexed-preflight.ps1`
- T = `docs/quality/validate-p4-frame-analysis.ps1`
- M = `docs/quality/measurements/measure-m2-frame.ps1`
- I = `docs/quality/measurements/invoke-p4-indexed-slot.ps1`
- D = `docs/quality/measurements/p4-indexed-device-lanes.ps1`
- P = `docs/quality/P4_LAYER_PHASE_PROTOCOL.md`

## 1. Existing behavior and exact seams

P:87–90 fixes phase protocol v1 / frame v9 / experiment v6 / verdict `layer-phase-2026-10-03-relative-m5`; historical v7/v8/v5 identities remain historical. C:163–199 already defines all three groups and their source/family/schema identities. C:228–243 provides slot id, group_id, comparison role, artifact_role, global run 1..12, baseline_slot_id, families, attempt, populations and bound. Consume these definitions; do not duplicate group arrays.

A:6–20 still fixes v8/v5, historical families and 33.33 ms input guard. A:317–328 accepts comparison roles, SequenceIndex 1..4, build/APK/bounds/experiment and optional baseline path, but no expected production or group. A:348–352 constructs historical `slot-NN-runner-role-attempt-1`. A:369–381 validates fixed workload order; A:391–405 checks schema/build/APK but not metadata.production_commit or production_tree_sha256. A:451–468 checks source/variant on raw/sample rows but ignores their production_commit. A:629–655 emits no group/artifact/production/device or fixture identity.

A:280–314 baseline reader validates only baseline/decision/baseline-recorded, experiment_id, complete_run and historical family order. It ignores schema/verdict/build/APK/seal/preflight/group. Thus a same-experiment foreign group or build is not rejected at this boundary.

W:111–112 selects the first decision baseline from the historical default catalog. W:124–127 rejects phase manifests and resolves slots from that default catalog. W:186–196 chooses historical roles and bounds. W:177–183 verifies record schema and containment, but not exact slot identity. W:197–200 compares relative path/hash inventories; byte_count is not compared. W:212–215 adds seal/preflight hashes and a global historical protocol constant.

D:1310–1332 creates frame-slot-v1 with directory, inventory and recorded time only. M:535–536 constructs historical slot directories; M:545–620 defines a two-role experiment.json, which A does not read or hash. The phase directory/record shape therefore needs parent coordination; naming cannot be inferred from the wrapper slot id alone.

## 2. Minimal resolved context proposal (design input, not a schema decision)

Add one optional phase context to the existing Test-P4FrameCapture route; absence retains historical parameters/constants/outputs. Require explicit closed protocol selection, rather than inferring v9 from workload names or changing script globals. A phase context must be derived from the pinned manifest plus C catalogs, not trusted from capture rows. Suggested contents:

| Context component | Minimum values and origin |
| --- | --- |
| Phase identity | protocol_id, frame_schema, experiment_schema, verdict_id, experiment_id, pinned preflight_sha256 and experiment.json identity/hash |
| Slot identity | canonical slot_id, group_id, role, artifact_role, global sequence, attempt=1, runner, exact expected collector directory, preceding_slot_id and baseline_slot_id from C |
| Population | exact ordered slot families, warmups, samples and bound from C; resolved event counts/specs from collector contract |
| Current artifact | resolved build_commit, production_commit, production_tree_sha256 and release-like APK sha256; expected geometry/bounds and fixture preparation identity |
| Conditions | canonical device/display contract from the immutable manifest, plus preparation/checkpoint evidence identity sufficient to compare same conditions |
| Baseline (candidate decision only) | same group's decision baseline slot/artifact, fixed baseline production from C, resolved overlay build/tree/APK, exact ordered decision families, conditions/fixture identity, canonical analysis path and retained analysis/seal/preflight hashes |

Keep `Role` comparison semantics baseline/candidate. Resolve source through `artifact_role`; do not pass baseline_layers16 as Role. C:233 `run` is global sequence, not a group-local index. If collector retains local 1..4, the context needs a distinct explicit local field; do not overload existing comparison_sequence_index or accept modulo arithmetic as identity proof.

A:365–405 should reconcile phase context with run-state and metadata; A:451–468 should validate build AND production on every raw/sample row. Slot/group/artifact/protocol fields should be reconciled wherever parent chooses to persist them. Result A:629–655 must expose those checked identities so its baseline consumer can reject another group even when hashes/numbers match.

Existing device manifest names: C:975–979 has serial/profile/manufacturer/model/product/device/api/fingerprint/security_patch, display_mode/width/height/refresh_rate/rotation plus runtime conditions and inspection. M:2846–2854 publishes profile/device identity, and M:1046–1128 retains measured display checkpoints. A currently checks only physical_device class, not this identity. A canonical conditions object or its digest may avoid copies; an unvalidated caller string is insufficient proof. Exact field encoding belongs to parent.

## 3. Same-group baseline resolution and retained identity

W:94–118 should resolve the exact C slot.baseline_slot_id with explicit phase protocol, verify same group and decision/baseline comparison semantics, then locate its analysis under the same declared output root. Preserve historical default resolution for v8.

An explicit BaselineAnalysisPath must not bypass the expected slot/group/source/schema/conditions checks. Compare A baseline analysis against the resolved expected baseline context before reading percentiles. Require complete baseline-recorded run, exact decision family order and expected fixed baseline production. The candidate's own build/APK must never become the baseline expectation.

I:471–511 already verifies predecessor completed.json slot/protocol/preflight, analysis hash, capture seal hash/identity and each sealed file's size/hash. Reuse that retained chain through a shared read-only resolver/validator; do not create a parallel seal implementation or blindly trust completed.json hash fields. W currently reads baseline path independently of that check, so direct wrapper invocation needs the same binding. At minimum a resolved baseline reference must carry/check analysis_sha256, capture_seal_sha256, preflight_sha256 and slot_id against retained files/chain. Preserve all historical FAIL/invalid evidence.

W:197–200's collector inventory check remains useful for the current slot; compare byte_count as well when phase record identity is defined. Ensure phase output protocol comes from validated context instead of W:213's historical global constant.

## 4. First-preview raw/sample reconciliation and timestamp arithmetic

A:22–168 Get-P4FirstPreviewAssociation already validates the exact DOWN-only rows and returns seven fields. A:79–97 expects workload/operation/source_commit/production_commit/variant and operation_ordinal/sample_index/raw_row_count. Its raw_row_count is the PREVIEW population, not sample.raw_row_count (preview+commit total). Caller must set it from reconciled sample.preview_raw_frame_count and require preview_frame_count = preview_raw_frame_count = preview_valid_frame_count = retained preview rows.

Call at A:532–547 after grouping the retained operation's preview/commit rows. Build ExpectedCapture from canonical artifact context and verified workload/ordinal/index, not from unverified sample source/production values. Preserve recorded row order; helper rejects ties/reordering instead of sorting.

Require these seven sample association fields for layers16 tap: first_preview_frame_timeline_vsync_id, first_preview_row_index, first_preview_handle_input_start_nanos, first_preview_frame_completed_nanos, first_preview_deadline_nanos, first_preview_service_ms, first_preview_overrun_ms. Parse integers losslessly and compare every field to helper output. Compare published six-decimal service/overrun against own-row decimal subtraction. Missing/partially populated values must fail. Reject populated first_preview_* fields on other workloads; if shared CSV columns exist, blank cells are absence, not an orphan association.

A:124–152 helper already validates positive timestamps, within-row order, strictly increasing intended/start/input/completion, unique frame IDs and per-row service/overrun arithmetic. It is specifically restricted to layers16 tap preview. For the other v9 frame rows, own timestamp metric validation must run before A:550 trusts frame_overrun_ms. Share a generic row/metric primitive beneath the existing helper if extending this validation to every phase row; do not feed a fabricated layers16 identity to the helper.

M:1423–1426 defines four raw own-row metrics: (completed-start)/1e6, (completed-intended)/1e6, (completed-deadline)/1e6 and (completed-input)/1e6. Service and overrun are protocol-required by P:158–159; validating the other two emitted metrics would be reconciliation, with exact scope/precision explicitly settled by parent. Use decimal arithmetic before conversion to published F6; existing [int]/[long] casts throughout A can truncate/round malformed values and are not sufficient integer validation for v9.

M:715–734 separately defines existing operation-summary minima/maxima and previewCompletion < commitInput ordering. A:544–546 currently recomputes only input_to_committed_result_ms and ignores summary timestamp/down_to_committed fields. Reconcile existing summaries and phase order from raw rows through one shared operation-timing function; keep pooled committed-result semantics separate from first row's association. The helper alone does not prove visible preview/quiescence (P:161–166).

Record descriptive first-preview populations/distributions for tap in its family analysis; all preview+commit rows continue contributing to tap all-frame relative metrics. No added physical/first-preview gate. A:572 gate selection must distinguish v9 candidate decision 16.67 ms from historical baseline guard 33.33 ms; historical v8 stays 33.33 ms (P:135–142). This is the accepted protocol, not a new probe decision.

## 5. Existing test seams and narrowly affected coverage

T:47–263 New-P4FrameSlotFixture produces disk evidence with raw/source/production/timing fields; default v8 fixtures must remain unchanged. T:266–290 Invoke-P4FrameFixtureAnalysis wraps direct analyzer calls; T:293–299 Save-P4FrameAnalysis persists references. T:302–329 subprocess seam observes exit2 for missing/misplaced baseline. T:332 onward metadata-line mutation allows one-field negative cases.

T:351–409 New-P4LaneManifest is historical two-role input; T:412–418 catalog resolution is historical default. Current T does not dot-source W or exercise Resolve-P4FrameBaselineAnalysisPath/Invoke-P4SlotAnalysis; it checks analyzer and device-plan/preflight helpers. Add focused wrapper context/baseline tests at that direct boundary; do not claim existing T covers wrapper integration.

Current cases to retain: T:537–558 invalid/missing/foreign-experiment/diagnostic baseline; T:558–566 argument exit2; T:653–685 pooled rows and metric disagreement; T:694–725 APK/build/experiment/state/geometry drift. Phase additions: all three catalog bindings and same candidate, wrong-group baseline with identical values, same-group wrong build/production/schema/family/condition/fixture/slot/hash, exact 16.67 boundary while baseline 33.33 stays separate, missing/extra/reordered family, raw production drift, preview-total count confusion, multi-row first row not fastest, seven sample/raw mismatch cases and orphan fields on diagonal, malformed integer timestamps and forged own-row metrics. Scope to changed analyzer/wrapper paths; do not rerun unrelated plan/preservation/device sections.

Existing pure helper validator `docs/quality/validate-p4-first-preview-association.ps1`:21–46 expected/multi-row fixtures; :70–83 exact association; :130–216 malformed/lost/reordered/cross-row negatives. Existing catalog validator already covers immutable catalogs. Parent reports 281 and456 PASS respectively; neither rerun here. These prove pure primitives, not full capture/wrapper admission.

## 6. Shared-function placement and open design inputs

- C owns one manifest+slot-to-artifact/conditions/baseline context resolver, usable by collector parameter builder D:112 and W:174. A consumes plain validated expectation data, not a second manifest implementation.
- A owns generic lossless scalar/own-row arithmetic and operation timing; existing Get-P4FirstPreviewAssociation remains the only first-preview association path. Collector consumes those same pure helpers.
- Existing I predecessor seal logic should be exposed at a shared read-only boundary if direct W analysis must validate baseline completed evidence. Parent decides placement to avoid reverse wrapper dependencies.

## 7. Reconciliation with parent's subsequent provisional design

Parent proposes shared C.Get-P4FrameExecutionContract(ProtocolId,SlotId) for slot/group/families/baseline/gates/directory; optional A.PhaseContext = {protocol_id,slot_id,preflight_sha256,production_commit,baseline_reference}, with existing ExperimentId/BuildCommit/ExpectedApkSha256/ExpectedBounds arguments retained. baseline_reference (candidate decision only) = {build_commit,production_commit,apk_sha256,analysis_sha256,capture_seal_sha256}. Metadata/state/raw/sample must carry protocol_id/preflight_sha256/group_id/slot_id/artifact_role/experiment_id; production checked on raw/sample. Immutable manifest hash binds device/fixture declaration. Baseline schema/group/source/hash checks mandatory; admission remains closed.

This smaller context can express analyzer expectations without copying the full manifest: group/slot/artifact/families/schemas/attempt/gates derive from the shared contract; current source/APK/bounds remain existing arguments; production is explicit; baseline source/hash is explicit. No additional scalar field is demonstrably essential for this preparation slice if the following implementation conditions hold:
- Get-P4FrameExecutionContract must be available for direct A calls, not only W. A:3 currently imports only profile helpers; T subprocess :319 imports only A. C:3–5 imports bounded/profile/device-state helpers and does not import A, so A importing C is not presently cyclic, but avoid implicit caller-import reliance or mutation of shared globals. Test the direct phase boundary.
- baseline_reference must be required only for phase candidate decision and forbidden elsewhere. Existing BaselineAnalysisPath remains required for decision candidate; derived canonical baseline slot must not be bypassed by an explicit path. Its analysis hash must be recomputed before deserialize/use; retained seal hash must be recomputed against actual seal bytes AND match analysis.capture_seal_sha256. A matching string inside an analysis does not verify a seal. Reuse I:480–511 file/chain checks rather than duplicate.
- W must construct the expected preflight hash from the real manifest file; A must reject missing/malformed context, unrecognized protocol/slot and inconsistent role/runner/sequence arguments. The six mandatory phase identity fields must match every state/metadata/raw/sample record, including an empty/orphan sample association check before summarizing. Same preflight means same declared device/fixture; observed conditions/correctness proof remains collector/manifest responsibility.
- P:117 same family/device/display bindings remain required. Parent's same-preflight check provides declaration identity, but does not by itself prove M checkpoints/fixture preparation succeeded. Preserve their existing validation responsibilities during collector integration; no new measurement is proposed.
- A:348 historical directory shape and SequenceIndex ValidateRange(1,4) must become contract-aware without broadening historical acceptance. W:213 must not emit the historical global protocol on a phase analysis. Ensure candidate decision 16.67 gate is schema/role/runner-specific while baseline 33.33 guard remains.

Remaining open inputs (3): (1) Exact v9 frame-slot record identity and experiment.json v6 content/hash validation? (2) Shared seal-reader placement for direct wrapper baseline analysis while avoiding reverse wrapper dependencies? (3) Descriptive first-preview result names/populations and validation scope of frame_duration_cpu_ms/app_frame_total_ms? Parent's shared execution contract can settle directory naming without further context fields.

Verification: read-only Get-Content/rg/git rev-parse/status; no tests/build/device. Only this new report added; no behavior, production, governing-document or executable schema change. Reused result claims are parent-provided 281PASS association and456PASS catalog, not independently executed here; exact logs/tree identity remain in parent handoff. Unrelated failures: none new; known #186 remains separate. Remaining risk: context/collector/wrapper/preservation integration incomplete, so admission CLOSED. Active waivers: none.

日時: 2026-10-03 17:54:56 JST

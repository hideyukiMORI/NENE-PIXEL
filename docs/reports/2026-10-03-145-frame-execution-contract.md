# Issue #145 — shared frame execution contract

Rules: ARC-001/007/009; ADR 0030/0031/0035; QLT-011/012/014/015/016/017/018/019.
Active waivers: none. This is device-free preparation; phase collection remains refused.

## Registered scope and verification

The design seat registered this slice and its narrow checks in Issue #145 before execution.
The governing definition is [Frame execution contract and evidence binding](../quality/P4_LAYER_PHASE_PROTOCOL.md#frame-execution-contract-and-evidence-binding).
Only additive shared helpers in the existing preflight, their focused host validator and this report
are owned here. No app, dependency, device, manifest-admission, commit, push or external-state change.

The selected check detects collector/analyzer contract drift at the changed shared boundary:
exact ordered fields, all four legacy and twelve phase slots, schemas/verdicts, artifact/comparison
roles, comparator commits, global/group order, directory spelling, event populations, setup mappings,
finite numeric thresholds, experiment projection, unknown/mixed identity refusal and mutation isolation.
AST parsing covers the new validator, preflight and its imported source chain. No unrelated validator,
Gradle, full build, profile or device run. Source inspection establishes reuse of the old catalog check.

## Files and behavior

- `docs/quality/measurements/p4-indexed-preflight.ps1`: `Get-P4FrameExecutionContract` resolves only
  the canonical slot/group catalogs; omitted protocol retains v7. Legacy directories/v8/v5/verdict,
  33.33 ms gate and event records are preserved. Phase exposes v9/v6, same-group comparator references,
  group artifact roles, global directory/order identity and candidate 16.67 ms gate (+1/+2 ms relative).
- `Get-P4FrameWorkloadCatalog` owns the nine ordered event fields for six workload names; each call
  creates fresh records. The execution contract returns selected, decision and diagnostic catalogs;
  `setup_by_workload` describes the group's complete diagnostic family set, including window setup.
- `Get-P4LayerFrameExperimentContract` produces the canonical nine-field phase experiment-v6
  projection, original twelve slot records, budget 12, attempt 1 and replacement `none`. Experiment IDs
  use the existing 3..64-character grammar and preflight hashes require 64 lowercase hex characters;
  trailing newlines refuse. Complete artifact/device/fixture identity stays in the pinned preflight.
- `docs/quality/validate-p4-frame-execution-contract.ps1`: independent literal event and slot oracle,
  fresh lab evidence, runtime/source hashes and direct-import inventory. Existing 37 functions,
  catalog/default semantics, script constants and manifest admission are unchanged.
- This report records the preparation boundary. No project-file schema change; frame-v9/experiment-v6
  identities were already accepted in the parent-owned protocol. New validator evidence schema is
  `nene-pixel-p4-frame-execution-contract-validator-v1`.

## Results and immutable evidence

Command: `pwsh -NoProfile -File docs/quality/validate-p4-frame-execution-contract.ps1 -OutputDirectory <lab>/evidence/145-frame-execution-contract/20261003T180442086-5a1d0255dc8c429db9148cc6dff08144/contract`.

PASS: 2,502 assertions, 1.616701 seconds validator wall time, 2.6866011 seconds caller wall time;
PowerShell 7.6.6. Sixteen execution contracts; phase 620 measured + 110 warmup operations;
collector bounds 14,550 seconds. First execution passed; no failed or invalid result was discarded.
This wall time is host verification time, not measured application latency.

Evidence root: `evidence/145-frame-execution-contract/20261003T180442086-5a1d0255dc8c429db9148cc6dff08144/`.
`validator.log` retains stdout/stderr; `contract/validation.json` records command, runtime, hashes,
direct imports, assertion count, population and bounds. `catalog-reuse.json` records unchanged existing
function hashes and five reused input identities; `catalog-base-binding.json` binds the recorded source
to the current base. Targeted `git diff --check` passed. Documentation validation stays with the design seat.

Source base: `001337d32b13a2d36ba7d7d2b560b1039f71280e` plus this focused uncommitted change.
Verified preflight SHA-256: `3e0ede74d7cdaf20591d0993f485eb4667189b60c1fb88d14001bd9821c54253`.
Verified validator SHA-256: `d3e73aecb392204b7ef9fe37e4ae9d01e7bb94d85e01d9754181278391c086b3`.

Reused check: `pwsh -NoProfile -File docs/quality/validate-p4-layer-frame-catalog.ps1 -OutputDirectory <lab>/evidence/145-frame-catalog/20261003T172759001-495a77c4f1a5410c8ca480ea71cd83f8/catalog`.
456 PASS / 0.8470322 seconds; original source base `3f9d80bc92c8e15c60aaf5ade04f08b09db4f8de`
plus the catalog change. Tested preflight hash `c9f952429211e3146708fca64dc5f9fa1eed3904bcc164bb62c9bf0074baf7b1`
equals the LF source at current base `001337d32b13a2d36ba7d7d2b560b1039f71280e`. Every pre-existing
function and five recorded direct inputs remain identical; appended protocol prose does not alter this
catalog behavior. Log: `evidence/145-frame-catalog/20261003T172759001-495a77c4f1a5410c8ca480ea71cd83f8/validator.log`.
The historical catalog FAIL and performance FAIL/invalid evidence remain preserved.

Unrelated failures: none. Remaining risk: consumer integration and complete phase admission are
separate work; no successful device setup, numeric acceptance or measured speedup is established here.
Per-operation application cost is unchanged: no app scans, allocations, copies, per-pixel arithmetic
or preview-frame work is added. These host resolvers construct small catalogs outside timed operations.

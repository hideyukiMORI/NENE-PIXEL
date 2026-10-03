# 2026-10-03 — layer frame connection handoff

Issue [#145](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145) is the task-state authority;
Draft PR [#187](https://github.com/hideyukiMORI/NENE-PIXEL/pull/187) remains preparation.
Read the [session report](2026-10-03-layer-frame-connection.md) and linked verification reports.
Base revision: `001337d32b13a2d36ba7d7d2b560b1039f71280e`; use the PR head for the current commit.
Work is in development lab `worktrees/issue-145`, branch `perf/145-layer-gate`.
Do not reset or edit the separate historical `perf/54-compose-frame` checkout.

## Current boundary

The shared execution contract covers historical four-slot v8/v5 and phase twelve-slot v9/v6.
`measure-m2-frame.ps1` now consumes it and writes phase identity, lossless rows, tap association
and strict run-state records. Only `ValidateExperimentOnly` / `ValidateArtifactOnly` permit a
PhaseContext. Phase live collection and geometry inspection remain refused.

`Test-P4FrameCapture` accepts the documented five-field PhaseContext, compares the actual parent
experiment projection/hash, verifies row/sample identity/counts/arithmetic and same-group baseline
analysis/seal identity. The baseline guard stays p95<=33.33 ms and zero fatal matches; candidate
decisions require p95<=16.67 ms and +1/+2 ms relative overrun bounds. Gross diagnostic results stop
the phase; a decision-baseline outlier does not silently become a new admission gate.

The seal reader is shared with the existing completed-chain consumer. Canonical wrapper analysis,
seal and completion stay under the outer `<output_directory>/<slot_id>/`; raw files stay under
the frame experiment's `<frame_directory_name>/`. External frame inventory binds them.
Do not put analysis or a second seal into the raw slot directory.

## Continuation

Keep hide's active design/delegation instructions and QLT-011–019 scoped result reuse. No full
local suite or device run follows from a report, commit, push or change of agent.

The next integration must settle the exact underlay material and implement the live-editor
memory/storage and fixture setup contracts before complete phase admission. The manifest still
admits historical v7 only. The existing outer frame adapters still use legacy role fields; do not
force the phase through them or create a disposable two-role manifest projection. Implement the
four artifact roles and canonical frame-slot-v2 record when the full phase manifest is specified,
then connect the existing wrapper/collector/analyzer with one immutable manifest hash.

The original device state has not been changed. Historical read-only snapshot and original APK
identities stay historical. Before future isolation, obtain a current-source/current-device snapshot
and verify the complete preservation/restore/last-install chain. Do not infer authorization to
start new measurement from old recipes or successful host fixtures.

## Evidence reuse and preserved work

Execution contract 2,502, collector 548 and seal 371 checks pass at the source hashes recorded in
their reports. The analyzer report identifies the full result, focused baseline/state corrections,
legacy boundary checks and first-preview 281 result; reuse only the matching verified functions.
Catalog 456 remains unchanged with explicit AST/import identity proof. Prior native preservation,
restoration and maximum fixture results remain reusable on unchanged code and artifacts.

All validation output is retained under fresh lab `evidence/145-*` directories. Never delete or
overwrite it. Earlier failed validators and wrong-layout synthetic results retain their original
status and input identity. The two dirty layer-gate-session reports and older untracked notes were
present before this work and must remain separate from focused commits. Index only named files.

Remaining risks are incomplete outer admission/fixture/device integration and unmeasured numeric
performance. No project schema, dependency or app cost changes in this slice. Active waivers: none.

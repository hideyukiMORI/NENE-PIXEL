# Issue #145 layer-frame analyzer integration

Scope recorded before execution: existing `measurements/p4-indexed-frame-analysis.ps1` phase-v9/v6
boundary, generic own-row validation beneath the unchanged first-preview API, shared operation timing
and pure capture contract; new `validate-p4-layer-frame-analysis.ps1`. No production, device, build,
wrapper, collector, manifest, commit, push or GitHub mutation in this seat. Base HEAD
`001337d32b13a2d36ba7d7d2b560b1039f71280e`; parent owns Issue scope and protocol decisions.

Issue #145 / P4-05d; ADR 0030/0031/0035; QLT-011–019. Active waivers: none.

Selected checks: layer-frame validator for v9/v6 identity, experiment/baseline seal binding, lossless
integer/metric timing and first-preview sample reconciliation, all three groups/twelve slots,
16.67/33.33/+1/+2 boundaries and diagnostic prefix semantics; focused legacy fixture functions imported
through AST without executing unrelated device-plan tests. Existing first-preview validator once after
the final raw-core refactor detects regression in its 281 pure-helper assertions. Both include AST parse.
No device measurement is needed: only host evidence parsing and preparation contracts changed.

Implemented behavior: the same `Test-P4FrameCapture` accepts a closed optional PhaseContext and
canonical v9/v6 experiment projection; absence retains v8/v5 shape, gates and sequence1..4 restriction.
The shared pure capture contract validates caller identity for collector consumers. Phase run-state
counts are native JSON integers; CSV/metadata integers are parsed losslessly. Every retained phase row
uses own-row decimal timing, exact event/operation identity and original order. Samples reconcile all
timing/count/event facts and the seven tap associations; other families reject orphan/unknown fields.
Tap distributions are descriptive, and full frame populations supply the existing gates.

The baseline boundary binds actual analysis bytes and the shared verified capture seal, native
identity/count expectations and same-group full families before percentiles. Analysis/seal reside in
the canonical **outer slot_id directory**, while raw frame evidence remains in the separate experiment
frame_directory_name; the seal's `external/frame-slot` inventory binds that raw root. The initial
synthetic one-root layout was corrected after parent integration review; it is not the canonical route.

Evidence below is relative to the lab's `evidence/145-layer-frame-analysis/`. Commands are
`pwsh -NoProfile -File docs/quality/validate-p4-layer-frame-analysis.ps1` plus the listed selector,
except association uses `docs/quality/validate-p4-first-preview-association.ps1`.

| Scope / selector | Result / wall seconds | Immutable log directory |
| --- | --- | --- |
| Initial combined synthetic matrix, no selector | 295 PASS / 34.374 | `complete-20261003-182842-738aa412ae054f9dafa1dde560c62593/layer.log` |
| Pure association after final raw-core refactor | 281 PASS / 2.940 | `association-20261003-182403-bfa46d4488e444f59df14a9a7e1f42e9/association.log` |
| `-DiagnosticBoundariesOnly` | 6 PASS / 3.973 | `gross-boundaries-20261003-183055-264ee10501c148f7abfec828ea3acd82/layer.log` |
| `-BaselineBindingOnly`, canonical two roots | 110 PASS / 27.135; 1 proposal-specific assertion withdrawn, 109 remain applicable | `baseline-two-root-20261003-183346-c92ba16b21b04d038a33806ad77a9ff5/layer.log` |
| `-StateNativeIntegersOnly` | 8 PASS / 7.689 | `state-native-20261003-183637-5e759b59af9a474f9fdb9ed3e5592eb5/layer.log` |
| `-BaselineGrossReferenceOnly` | 4 PASS / 9.294 | `baseline-gross-20261003-183820-38650c8fad994e1a88a5ff093424c388/layer.log` |

The initial 295 PASS covered all twelve groups/roles, raw/sample/first-preview refusals, exact candidate
16.670000/16.670001 and baseline33.330000/33.330001, +1/+2 boundaries, prefixes, root/container/identity
errors and focused v8 legacy fixtures. Its one-root baseline fixture pass is retained as pre-integration
history; only unchanged raw/sample/legacy/gate scopes are reused. Canonical baseline/path/seal cases
were invalidated and replaced by the two-root selector. The subsequent state-only stricter JSON rule
was checked narrowly and does not invalidate successful baseline artifact/path checks. No combined
current-layout whole-validator rerun was performed solely for handoff.

Parent proposed forbidding any baseline gross=true reference, then withdrew it after protocol review:
baseline admission remains input p95<=33.33 and zero fatal, independently of diagnostic gross stops.
The temporary false-only condition and its single negative assertion are withdrawn. A real retained
one-frame40ms-overrun/50ms-service outlier still has16ms input p95 and -1ms overrun p95/p99; the new
four-assertion selector proves baseline-recorded/gross=true and successful candidate reference. This
is resolved adjudication, not an outstanding finding or a new gate.

All failed attempts are preserved, not retried unchanged: `driver-20261003-182002-e488e6a113a544beb2c52c131477b823`
(12.015s, commit-one-row scalarization); `final-20261003-182341-1171f2b0241540bcaf698710ed9aa82b`
(14.114s, underlay-one-key scalarization); `verified-20261003-182710-10c699a3976b4189802e84a15e8eeccc`
(36.768s, invalid synthetic gross deadline). Each `layer.log`/`run.json` and its synthetic population
remain. The relevant source or fixture was corrected before the next execution; no unchanged-input
rerun or unrelated suite was used.

Final source identity: base HEAD above; analyzer SHA256
`e8c2b5fe5e7d645a9ca540b0512d96a186417ac26f724d79b87b62e02ae2ff8e`; validator SHA256
`0b19d86a7a9c9f97933dd6ea283ade539de94b78b7896f26adb830e93399d46b`.
Immutable snapshots and seven function-body SHA256s are in
`source-ledger-20261003-183927-0923ff2b0bde48d48c35454f5cbefd40/` (`analyzer.ps1`, `validator.ps1`,
`function-sha256.json`, `log-sha256.json`). Association/own-row/scalar/timing bodies are unchanged since281 PASS;
Get-P4FrameCaptureContract and those bodies are unchanged since parent-provided collector548 PASS
at analyzer SHA256 `09dfad0a38fe9361717ab1f52022e2c35ca75eee65ed53041d0e9c6b7a694a0f`.
That collector result is parent-owned, not executed by this seat. Final AST parse: zero errors;
`git diff --check -- docs/quality/measurements/p4-indexed-frame-analysis.ps1`: PASS.

Documentation/schema change: this report, executable v9/v6 preparation only; governing protocol,
preflight contracts and shared seal are parent/other-seat ownership. No production measured-path
cost changed. Reused results are the scoped records above; unrelated failures: none new, historical
#186 separate. Risks: full phase admission and observed fixture/device correctness remain parent
integration; synthetic results do not claim device acceptance. Collection remains CLOSED. Waivers: none.

日時: 2026-10-03 18:40:18 JST

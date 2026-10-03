# Issue #145: pure DOWN-only first-preview association

## Scope and verification plan (before execution)

Issue #145 / P4-05d; ADR 0031; ARC-007; QLT-011/012/014/015/016/017/019. Active waivers: none.
Contract: [Executable DOWN-only association](../quality/P4_LAYER_PHASE_PROTOCOL.md#executable-down-only-association-contract).
Actual-code basis: [integration probe section 4](2026-10-03-145-frame-integration-probe.md#4-down-only-first-preview-association).

Changed paths: add `Get-P4FirstPreviewAssociation` only to
`docs/quality/measurements/p4-indexed-frame-analysis.ps1`, add its focused synthetic validator
`docs/quality/validate-p4-first-preview-association.ps1`, and this report. Historical constants,
functions and `Test-P4FrameCapture` remain untouched. No caller integration, product code,
dependency, public API, device collection, Gradle or full analyzer suite.

Planned command: `pwsh -NoProfile -File docs/quality/validate-p4-first-preview-association.ps1`.
Write stdout/stderr to an immutable timestamped lab evidence directory under
`evidence/145-first-preview-association/`; inspect only PASS summaries or failing lines with `rg`.
The validator AST-parses both changed scripts and checks:

- Complete multi-row captures return the first chronological row's ID and own timestamps/metrics,
  including when a later row is faster; input rows are unchanged.
- Negative/zero overrun, positive/negative rounding midpoints, nanosecond boundaries, finite numeric
  and scientific CSV metric text, and near-Int64-limit timestamps preserve exact decimal arithmetic.
- Empty/lost/null/flagged/foreign/wrong-phase/event captures, missing fields, count/index drift,
  each timestamp tie/reordering, repeated IDs, malformed/fractional/overflow integers and altered
  metrics fail closed, including errors on later rows.
- Expected identity cannot admit another workload, variant, malformed commit or operation shape.
  Integer parsing never uses PowerShell's rounding casts; metric matching uses invariant finite
  decimal values at F6 precision, without requiring raw text to contain exactly six decimal digits.

No device measurement is needed: this is a new unconnected host pure function. It performs one
row traversal and a frame-ID set allocation proportional to row count; no document scan, pixel work
or work in the app's preview frames is added. Setup quiescence, visible correctness and complete
collector/raw/sample reconciliation remain future admission requirements.

## Results

Initial execution passed 273 assertions in 2.2792757 s, retained at
`evidence/145-first-preview-association/20261003T173239040-e3c8eebe20d34f0baec6a581feb69105/validator.log`.
Static review then identified an unverified concern: PowerShell can unwrap a one-element array
field as a scalar. The helper now preserves member container identity and requires scalar integer
types and scalar phase text; four focused refusal cases were added. This relevant source/contract
change invalidates the initial result for current source, so the new helper validator runs again
under a fresh retained evidence identity (QLT-012). The prior PASS remains historical, not replaced.

Second execution: PASS, 277 assertions, 2.3532526 s, retained at
`evidence/145-first-preview-association/20261003T173442690-d2bcc53498374647be00b6c11b64440a/validator.log`.
Parent source review then identified the same container ambiguity in the metric parser's fallback
string conversion. Static inspection confirmed it could accept a single-element metric array.
Scalar string/CLR numeric types are now explicit; both metrics gain single/empty-array refusal
cases. This related source change invalidates the second result for final source (QLT-012).

Final current-source result: **PASS, 281 assertions, 2.4569314 s**, command
`pwsh -NoProfile -File docs/quality/validate-p4-first-preview-association.ps1`.
No FAIL or unstable retry occurred. AST parsing is included for the helper source and validator.

Retained evidence:
`evidence/145-first-preview-association/20261003T173658924-639e5e3edd1b4d29b0d0f6007fc83a25/`:
`validator.log`, `run.json`, `source-hashes.json`, `historical-content-check.log`, `diff-check.log`.
HEAD is `3f9d80bc92c8e15c60aaf5ade04f08b09db4f8de`; current tested input SHA-256 identities are:

| Path | SHA-256 |
| --- | --- |
| `docs/quality/measurements/p4-indexed-frame-analysis.ps1` | `d3d2092ed2326605bb1ba4648671320d46b1597c188b7570458d3ad90533eb8e` |
| `docs/quality/validate-p4-first-preview-association.ps1` | `6d87c41001766870788bdd277cf3cf13df47a5dbea0dfeeacba123b3aa7320d2` |

Static content comparison with `git show HEAD:docs/quality/measurements/p4-indexed-frame-analysis.ps1`
proved the entire historical file identical after removing only the added function and normalizing
line endings. `git diff --check -- docs/quality/measurements/p4-indexed-frame-analysis.ps1` passed.
Thus historical functions/constants, including `Test-P4FrameCapture`, retain their exact source.
No existing analyzer, device or Gradle check was executed. Documentation validation is coordinated
by the parent for the combined governing-document changes; it is not duplicated in this slice.

Reused results: none needed for this new helper; all unrelated prior passing results retain their
original identities. Unrelated failures: none new (existing #186 remains separate).
Documentation/schema: this report only; no capture schema change or admission claim.
Remaining risk: the helper alone cannot establish visible input association or phase readiness.
Active waiver IDs: none. No commit, push, PR or external-state change was performed.

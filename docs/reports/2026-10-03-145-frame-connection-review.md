# Issue #145 — independent frame connection review

Final findings: **2 resolved should findings; 0 unresolved blockers/should/nits**.
Base: 001337d32b13a2d36ba7d7d2b560b1039f71280e plus the focused uncommitted change.
Rules: ARC-001/007/009, ADR 0030/0031/0035, QLT-011–019. Active waivers: none.

## Scope and method

Read-only review of the shared execution resolver, collector phase identity/experiment/raw/sample/state
connection, phase-v9 analyzer and same-group baseline binding, shared capture-seal reader and its
completed-chain consumer, and four dedicated validators. The governing reference was
[P4 phase protocol](../quality/P4_LAYER_PHASE_PROTOCOL.md#frame-execution-contract-and-evidence-binding).
Review compared source and recorded evidence; no tests, device operations, builds, GitHub operations,
commits or pushes ran. The only review-owned change is this new report. Existing session reports and
untracked files were preserved. No application behavior, project schema or measured operation changed.

## Resolved findings

### R1 — should: JSON run-state integers had different reader semantics

Source evidence: docs/quality/measurements/p4-indexed-frame-analysis.ps1:34 accepted numeric strings
through the CSV-compatible integer parser, while measure-m2-frame.ps1:713 required native JSON
integer types. Reproduction: quote an otherwise valid phase run-state comparison_sequence_index,
attempt, measured_operation_count or measured_workload_counts value. Before correction the analyzer
accepted that value; the collector predecessor reader rejected the same v6 record. This violated the
native JSON predecessor contract in docs/quality/P4_LAYER_PHASE_PROTOCOL.md:240.

Resolution confirmed: p4-indexed-frame-analysis.ps1:64 defines the native-only JSON integer parser;
:593 and :596 apply it to all affected run-state counts. CSV/metadata still accept exact numeric text.
validate-p4-layer-frame-analysis.ps1:179–190 has five quoted-number negatives and a native-positive case.
Owner evidence: -StateNativeIntegersOnly, 8 PASS / 7.689 s; no reviewer rerun.

### R2 — should: temporary false-only gross check added a baseline admission gate

Source evidence: the temporary baseline family check rejected gross_regression=true, although
P4_LAYER_PHASE_PROTOCOL.md:138 defines admission as input p95 <=33.33 ms and zero fatal matches.
The analyzer baseline verdict at p4-indexed-frame-analysis.ps1:985 follows that p95/fatal guard;
measure-m2-frame.ps1:2836 stops a gross result only in diagnostics. Reproduction: one retained frame
with 40 ms overrun and 50 ms service among a population with 16 ms input p95. The baseline is valid
and recorded, but the temporary reader refused its candidate reference. This was an additional gate.

Resolution confirmed: the parent withdrew that proposal. p4-indexed-frame-analysis.ps1:440 keeps
strict boolean validation and accepts true or false; the accepted p95/fatal gate is unchanged.
The validator's real raw outlier fixture at :193–205 proves baseline-recorded/gross=true and a valid
candidate reference. Owner evidence: -BaselineGrossReferenceOnly, 4 PASS / 9.294 s; no reviewer rerun.

## Remaining reviewed boundaries and evidence reuse

No further actionable findings. The already-known baseline layout correction was verified at
p4-indexed-frame-analysis.ps1:407: analysis/seal use canonical outer slot_id; raw collector evidence
uses frame_directory_name and the seal's external/frame-slot inventory. It is not a new finding.
Review also checked lossless own-row timing, first-preview-only fields, exact event and sample
populations, original legacy identities, shared thresholds, sealed size/hash/path/link identity,
completed-chain linkage and early refusal while full phase admission remains closed.

Recorded commands are pwsh -NoProfile -File docs/quality/<validator>.ps1 with their recorded output
paths/selectors. Results were read and reused, not executed in this review:

| Validator / selector | Recorded result | Source and immutable log record |
| --- | --- | --- |
| validate-p4-frame-execution-contract | 2,502 PASS | [Execution contract report](2026-10-03-145-frame-execution-contract.md) |
| validate-p4-frame-collector-contract | 548 PASS / 9.1575738 s | [Collector report](2026-10-03-145-frame-collector-contract.md) |
| validate-p4-capture-seal | 371 PASS / 5.257 s | [Seal report](2026-10-03-145-capture-seal-boundary.md) |
| validate-p4-layer-frame-analysis / changed selectors | 109 applicable baseline-binding assertions; 8 native-state; 4 gross-reference | [Analyzer report](2026-10-03-145-layer-frame-analysis.md) |

Each linked report records commands, base/tree or source hashes and relative immutable log locations.
The parent confirmed the first three current source identities match their passing results; the
analyzer ledger records unchanged collector/association helper bodies for result reuse under QLT-012.
Final reviewed analyzer SHA-256: e8c2b5fe5e7d645a9ca540b0512d96a186417ac26f724d79b87b62e02ae2ff8e.
Final analyzer validator SHA-256: 0b19d86a7a9c9f97933dd6ea283ade539de94b78b7896f26adb830e93399d46b.
Both matched the owner's final report by read-only Get-FileHash inspection.

Remaining risk: full manifest, adapters, fixtures and observed device correctness are still closed
integration work; these host results do not establish device performance acceptance. Historical
FAIL/invalid results remain preserved. Unrelated failures from this review: none. Waivers: none.

日時: 2026-10-03 18:42:57 JST

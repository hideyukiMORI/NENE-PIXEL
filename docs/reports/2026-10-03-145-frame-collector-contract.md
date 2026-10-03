# Issue #145 — collector execution and CSV contract

Rules: ARC-001/007/009, ADR 0030/0031/0035, QLT-011–019. Active waivers: none.

## Scope and selected verification

The Issue's frame connection scope was recorded before these checks. The existing
`measure-m2-frame.ps1` now consumes the preflight-owned execution contract for historical four-slot
and prospective twelve-slot catalogs. Only host preparation is enabled for the phase; live collection
and geometry inspection refuse before artifact/device/experiment work. Complete phase admission,
fixture setup and outer manifest integration remain required.

The changed boundaries are catalog/role/sequence selection, v6 experiment projection, previous-state
identity and integer/boolean types, v9 raw/sample identities and tap-only first-preview fields.
The writer uses the analyzer's lossless integer, own-row metric, association and operation-timing
helpers. Unparseable nonblank PROFILEDATA rows and missing closing markers refuse in phase mode.
Historical raw/sample columns, directory spelling and v5 experiment projection remain intact.
Metadata families/counts/surface bounds are emitted from the chosen contract; run state also binds
the actual experiment hash. Numeric candidate decisions remain in the analyzer.

The focused validator calls the real collector with `-ValidateExperimentOnly` for all 16 contracts.
It loads the actual writer/read functions by AST without executing the device body, uses the existing
profile fixture setup only, and retains every synthetic file below a fresh lab evidence directory.
No device mocks, old test body, cleanup, application test or full build is invoked.

## Results and identities

Command: `pwsh -NoProfile -File docs/quality/validate-p4-frame-collector-contract.ps1 -OutputDirectory <lab>/evidence/145-frame-collector-contract/20261003T182904377-6f18630e29ec45e39b8be24b7f8b2765/validator`.

**PASS: 548 assertions, 9.1575738 seconds**, PowerShell 7.6.6, exit 0.
Log: `evidence/145-frame-collector-contract/20261003T182904377-6f18630e29ec45e39b8be24b7f8b2765/run.log`.
Exact source/runtime/command/result records: that directory's `validator/validation.json`.
The source base is `001337d32b13a2d36ba7d7d2b560b1039f71280e` plus this focused uncommitted edit.

The check covers actual experiment/state writing and reading for all four legacy and twelve phase
slots, unchanged UP timing that excludes preview dwell, all declared workload writers, lossless
64-bit frame IDs above double's exact integer range, nanosecond-derived metrics, and actual CSV
round trips into the analyzer row boundary. It also checks early live refusal with no experiment
output, foreign state group/slot/artifact/experiment/preflight identity, root arrays, text booleans,
floating counts, invalid flags/integers, truncated raw data and tap-only association columns.

| Verified input | SHA-256 |
| --- | --- |
| `measure-m2-frame.ps1` | `f65ec790800250a106fa88402c3940fc0ccfac9cdb8852beacaecee6c91212ee` |
| `validate-p4-frame-collector-contract.ps1` | `c912d000eb1fdbd29f375098526b31c48a343fcc652a9f2de42d43d5118dd494` |
| `p4-indexed-preflight.ps1` | `3e0ede74d7cdaf20591d0993f485eb4667189b60c1fb88d14001bd9821c54253` |
| `p4-indexed-frame-analysis.ps1` | `09dfad0a38fe9361717ab1f52022e2c35ca75eee65ed53041d0e9c6b7a694a0f` |

The result records all ten loaded/test source hashes, including the reused legacy fixture source.
Later baseline-reference/state analysis changes do not affect this validator's called capture-contract,
row, association or timing helpers. The analyzer's final source ledger and owner comparison confirm
those seven bodies unchanged, so this result is reused without another collector run.

Initial validator FAIL is retained at
`evidence/145-frame-collector-contract/20261003T182840773-e08f9a4ca69243fba46020cae8b03b53/run.log`.
Its extracted fixture script block had an empty `PSScriptRoot`. The validator now binds the fixture's
quality-script directory explicitly. This changed validator code before the successful execution;
the failure was not retried on unchanged inputs or discarded.

## Changes and limits

Files: the existing collector, the new focused validator and this report. Governing frame-v9 and
experiment-v6 rules are in `P4_LAYER_PHASE_PROTOCOL.md`; no product schema, dependency or app code
changes. Validator result schema: `nene-pixel-p4-frame-collector-contract-validator-v1`.
Application cost per operation is unchanged: no new document scan, app allocation/copy, per-pixel
arithmetic or preview work. Added host checks run after capture or before collection.

This is a synthetic contract result, not device/performance evidence. Full manifest, outer adapter,
fixture and preservation integration remain closed. Other unchanged preparation results are reused
as recorded in the session report; no unrelated failures were found or repaired here. Waivers: none.

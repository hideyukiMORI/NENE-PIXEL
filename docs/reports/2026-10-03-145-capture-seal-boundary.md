# Issue #145 — verified capture seal read boundary

Rules: ADR 0031/0035; ARC-007; QLT-011–019. Active waivers: none.

## Registered scope and selected verification

Issue #145's frame connection scope includes the shared read-only seal boundary and its direct
completed-chain consumer. This seat owns only `docs/quality/validate-p4-capture-seal.ps1` and this
report; the parent owns `measurements/p4-indexed-capture-seal.ps1` and the wrapper.

The selected device-free check detects changed-boundary regressions: actual local/external/empty
file identity, seal hash/schema/slot and strict inventory types, exact size/hash, malformed or
escaping paths, duplicate and aliased files, undeclared/invalid external roots, strict integral byte
counts and reparse-point ancestors. Fresh write-once fixtures exercise the real legacy seal writer
and the real frame baseline completed-chain consumer, including file/seal/analysis/binding and
restoration/worktree/incomplete-run drift. Inventory hashes before/after every boundary check unchanged
file content, size, path, type and junction targets. AST checks and source/import hashes cover the
validator and its loaded source chain.

Command: `pwsh -NoProfile -File docs/quality/validate-p4-capture-seal.ps1`.
Fixtures and logs use fresh `evidence/145-capture-seal/<timestamp-guid>/` below the development lab.
No device, APK, build, old validator, GitHub edit, commit or push. Prior catalog/association and
historical FAIL/invalid evidence remains unchanged; reused results for this new helper: none.

## Results

PASS: **371 assertions, 98 real read boundaries, 5.257 seconds**, PowerShell **7.6.6**, exit 0.
All 11 source/dependency AST checks passed; all 11 input hashes remained unchanged during execution.
Three Windows junction checks ran and rejected links; no declared check was skipped. The eight direct
chain cases cover accepted baseline plus sealed file, seal, analysis, analysis-binding, restoration,
worktree and incomplete-run refusal. Legacy writer output and zero-byte/local-only inventories passed.

Command: `pwsh -NoProfile -File docs/quality/validate-p4-capture-seal.ps1 -OutputDirectory <lab>/evidence/145-capture-seal/20261003T181853838-ca3eae87929b47c1b5c4acb60837c645/fixtures`.
Log: `evidence/145-capture-seal/20261003T181853838-ca3eae87929b47c1b5c4acb60837c645/run.log`.
Result, full imported-source hashes, direct dot-source declarations, case outcomes and paired inventory
hashes: the same evidence folder's `fixtures/validation.json`; actual before/after inventories are in
`fixtures/inventory/`. Every case occupies its own fresh folder. All evidence is retained write-once.
Tracked patch whitespace check: `git diff --check -- docs/quality/validate-p4-capture-seal.ps1 docs/reports/2026-10-03-145-capture-seal-boundary.md`, exit 0.
Direct trailing-whitespace inspection of both new files also passed. Report inspection found no
absolute lab paths or pending markers. Final helper/wrapper/validator hashes still match this result;
appended report prose does not invalidate it under QLT-012.

### Retained failures and corrective invalidation

1. Initial validator stopped after 12 assertions / 1.000 seconds on its timestamp-sensitive inventory
   comparison. Evidence: `evidence/145-capture-seal/20261003T181601445-28ed9309439b4a3db51217dc7f9dac3c/run.log`.
   A narrowly scoped diagnostic directly read the same seal; its two full inventories matched and are
   retained in that folder's `diagnostic-ffeb4f4051dc4d1a81a4a0420c032ff8/`. The original mismatch's
   exact metadata difference was not captured. The validator was corrected to compare the requested
   path/type/size/hash inventory and retain both full observations outside each fixture. This changed
   validator input, rather than an unchanged retry. No assertion of timestamp stability is made.
2. Corrected validator stopped after 87 assertions / 1.665 seconds because a single-element JSON root
   array was accepted as a seal object. Evidence:
   `evidence/145-capture-seal/20261003T181733952-554b735530ca461da2e80890e530f1e8/run.log`.
   The finding was returned to the parent; the parent added `ConvertFrom-Json -NoEnumerate` to the shared
   reader. This preserves the root array type for rejection. Only this targeted validator ran after
   the relevant reader correction and additional local-only/missing-seal/array-byte-count cases.

Neither failed run is replaced or relabelled. Prior catalog/association validators were not rerun.
Base revision: `001337d`; exact uncommitted input identities below define the passing result.

### Verified SHA-256 inputs

Paths are relative to `docs/quality/`. `validation.json` records each direct import expression.
The helper imports `baseline-profile-evidence.ps1`; the wrapper imports preflight, seal, window state,
device state and device lanes. Preflight imports bounded-native, baseline-profile and device state;
device state imports bounded-native, window state and package-dexopt; device lanes imports bounded-native.
The validator imports lab resolution and dot-sources the wrapper with dummy mandatory arguments,
which defines functions without invoking `Invoke-P4IndexedSlot` or any native/device function.

| Input | SHA-256 |
| --- | --- |
| `measurements/p4-indexed-capture-seal.ps1` | `b6420221ce810be3375601842a4e5e5e6cdca54f30799d6327e8791744c3b59c` |
| `measurements/invoke-p4-indexed-slot.ps1` | `b5457d9799aca38f4ad88d68496c2fb05515e9d89487fbe2d82a8c40840e3679` |
| `measurements/p4-indexed-preflight.ps1` | `3e0ede74d7cdaf20591d0993f485eb4667189b60c1fb88d14001bd9821c54253` |
| `baseline-profile-evidence.ps1` | `02fce3f4e071b49c9247e548df7dd28b8a60ad24e49c30002d82267c5b1c8d31` |
| `bounded-native-command.ps1` | `1d292803e63bc7db64fe9265764ba1d2e7265d017da82cd6b84acb20eba6ceea` |
| `measurements/p4-indexed-device-state.ps1` | `df2c4bc0c0a61b00b5f0661788639890a2392568600e14c6521774521f6d0941` |
| `measurements/android-window-state.ps1` | `add0ff0b61ab22c9d18b819a7f531e3b0e1072ada85eec05d563243f15abe413` |
| `measurements/m2-package-dexopt.ps1` | `ba9b232be6dc4b4a125c61c0d03a3e77fe8abc4a817f99a4089b10a288bf3259` |
| `measurements/p4-indexed-device-lanes.ps1` | `905d6d931914404324a78b46f88ea54a24d8d7792f588100be7e81128b671b52` |
| `measurements/nene-pixel-lab.ps1` | `5f5dd2ff62e19bfa9f869f3beff45a8b59a2d181887aae28128012606af3d9ec` |
| `validate-p4-capture-seal.ps1` | `8c37d4bf430aceaa8ae812c9f7bc02f194773b2bd5993ddd0446e3032c4322b7` |

## Changes and limits

The validator and report are additive. No app behavior, dependency, project-file schema or performance
threshold changes. The validator result schema is `nene-pixel-p4-capture-seal-validator-v1`.
No measured operation work changes: this host-only read performs file inventory hashing outside
device samples. Remaining limit: fixture checks do not authorize phase admission or device collection.
Unrelated failures: none. Active waivers: none.

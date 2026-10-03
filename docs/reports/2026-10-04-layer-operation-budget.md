# P4-05d shared operation budget

Issue: #145. PR: #187 (Draft). Rules: ADR0035, ARC007, QLT-011–QLT-019.
Waivers: none. This is host preparation; phase and frame live admission remain closed.

## Change and scope

The existing native observation, snapshot, install, isolation/restoration and stopped-report
paths now accept an explicit shared operation clock. `p4-operation-budget.ps1` creates and validates
the running monotonic clock. Each new native invocation uses its existing ceiling or the whole
remaining seconds after a 15-second termination/drain reserve, whichever is smaller. A malformed,
stopped, explicitly null or exhausted budget refuses before launching another native command.
Host verification checks expiry before accepting success. Omitting the budget preserves historical
native parameters and results.

Phase private inventories stop at 4096 entries before stat/hash batches, retaining discovered paths.
Original-APK installation records the actual granted timeout in its intent and raw result. Partial
transfer and failed mutation records remain retained. Snapshot source bindings now include the
budget helper (eight sources); the actual snapshot reader rejects historical seven-source records.

Changed files: the new budget helper and validator; `p4-indexed-device-lanes.ps1`;
`p4-device-private-native.ps1`, `p4-device-private-snapshot.ps1`,
`p4-device-private-session.ps1`, `p4-device-private-restore.ps1`; and the phase protocol/report.
No production Android code, dependency, persisted document schema, numeric analyzer,
inventory/restoration planner or byte-transport algorithm changed. The new operation context is
host-local and contains a live Stopwatch, not a serialized preservation record.

## Focused verification

The Issue recorded the scope and selected checks before implementation. Run each group with:

```powershell
pwsh -NoProfile -File docs/quality/validate-p4-operation-budget.ps1 `
  -CaseGroup <group> -OutputDirectory <fresh-evidence-directory>
```

| Group | Checks | Result | Seconds |
| --- | ---: | --- | ---: |
| Math | 29 | PASS | 1.0849362 |
| Dispatch | 29 | PASS | 1.3288256 |
| Inventory | 7 | PASS | 1.6271978 |
| Records | 11 | PASS | 1.8670912 |
| Compatibility | 12 | PASS | 0.4078522 |
| Legacy | 6 | PASS | 0.6152283 |

All 94 focused host checks passed. Compatibility includes parsing the six changed measurement
scripts and checking unchanged planner/observation/transport sources. The six legacy comparisons
execute the actual pre-change functions from `23a1e53` and current functions with identical mocked
native boundaries, comparing parameters and returned values. No device/native process is launched.
The initial Math console summary projected a dictionary incorrectly and printed null fields;
its retained `result.json` correctly records all 29 PASS checks. The summary rendering was fixed
without rerunning accepted checks.

Evidence root:
`D:/NENE-PIXEL/evidence/145-layer-operation-budget/20261004T010434888-2a690c7888494a72947e3c9d079e3c1d`.
All groups retain their individual checks, expected refusals, mocked calls and source hashes.
`gradlew.bat validateDocumentation --offline` passed with configuration cache reuse; its log and
wall time are recorded separately in this evidence root. Source reconciliation confirms the eight
snapshot source hashes match all six accepted groups; the validator parses and `git diff --check`
passes. Commit/push do not invalidate these unchanged inputs.

## Remaining work and limits

The outer wrapper still must attach the clock and record the selected slot cleanup/session bounds.
This change alone is not a whole-session deadline and does not authorize device collection.
Full outer archive/reset/original-restoration routing, immutable artifacts/profiles and ADR0034
asynchronous underlay quiescence remain pending. No APK build/install, profile generation,
device snapshot/isolation/restoration or performance sample ran in this slice. Prior successful
numerical, transport, preservation and slot-routing checks were reused on unchanged behavior.

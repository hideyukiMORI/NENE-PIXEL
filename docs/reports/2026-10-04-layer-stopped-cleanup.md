# P4-05d stopped capture, archive and reset

Issue #145; PR #187 remains Draft. Rules: ADR0035, ARC007, QLT-011–QLT-019.
Waivers: none. Full phase and frame live admission remain closed.

## Change

`p4-layer-slot-cleanup.ps1` composes the existing stop, install intent/readback, report transfer,
read-only private/APK snapshot and six-root reset boundaries. The canonical device plan selects the
actual artifact role and archive packages. Every slot archives the app; slots installing `test_debug`
also archive the test/provider package; publication archives its self-instrumenting package.
No auxiliary package is installed for archival.

The helper verifies canonical output/worktree/ADB/device identity, owns one 3000-second monotonic
clock and confirms all three writers stopped. It verifies immutable preservation-v2, restores exact
role-specific debug access through the existing install boundary, then confirms writers remain
stopped. Each full snapshot uses the existing verifier with private/APK caps of 256/128 MiB and the
4096-entry inventory limit. Remaining archives are attempted after component failure within the
same clock. Reset starts only after every required capture succeeds. Original guards stay intact.
`nene-pixel-p4-layer-slot-cleanup-v1` retains plan, source hashes, component references and failures.

Successful frames consume the existing captured staging evidence. `Read-P4LayerFrameStagingEvidence`
was extracted from the analyzer so both boundaries use the same verifier. Failed frames recover a
unique fixture report in a fresh diagnostic directory. Canonical partial frame outputs gain the
existing frame-slot-v2 record when a killed collector could not write one; existing records are
verified and never replaced. Numeric analysis and frame populations remain unchanged.

The phase protocol was updated before implementation. No Android production code, dependency,
project schema, thresholds, snapshot/transport/reset implementation or legacy default changed.
These are internal measurement helpers/evidence schemas, not new collection entry points.

## Focused verification

The Issue recorded both cleanup scope and shared-reader extraction before the respective changes.

```powershell
pwsh -NoProfile -File docs/quality/validate-p4-layer-slot-cleanup.ps1 `
  -CaseGroup <group> -OutputDirectory <fresh-evidence-directory>
```

| Group | Accepted checks | Result | Seconds |
| --- | ---: | --- | ---: |
| Maps | 26 | PASS | 0.6045301 |
| Flow, memory prefix | 6 | PASS prefix | full first-run wall time not recorded |
| Flow, publication/SAF/single prefix | 18 | PASS prefix | failed group 1.6702043 |
| Flow, remaining layers/underlay | 12 | PASS | 1.6282399 |
| Failures | 43 | PASS | 1.6880711 |
| Frame | 16 | PASS | 2.3308862 |
| Compatibility | 22 | PASS | 1.0833992 |

143 distinct focused checks passed. Device boundaries are mocked; orchestration, canonical plans,
partial frame inventories and shared staging verification are real. Failures cover writer stop/
absence, preservation, debug replacement/restart, reports, archives, shared-clock expiry, reset,
foreign context and incomplete/foreign frame records. Dependency hashes and exact function/body
comparisons justify reuse of unchanged numeric, snapshot, transport and reset results.

The first Flow run's validator replaced its event list with a projected array after the six accepted
memory checks, then failed on the next case and final summary. Its original validator source,
component records and a failure summary remain retained. The next run accepted 18 checks, then its
imported fixture could not resolve `PSScriptRoot`. Fixing the validator's variable/path references
required no production change. Continuations used `-FlowStartIndex 1` and then `4`; successful
prefixes were not repeated. All completed component records and failure JSON remain unchanged.

Evidence root:
`D:/NENE-PIXEL/evidence/145-layer-stopped-cleanup/20261004T015152204-f512da1bf04140e782f2be733531fcb2`.
Documentation and source reconciliation are recorded separately there.
`gradlew.bat validateDocumentation --offline` passed in 14.9922525 seconds (exit 0).
Source reconciliation verifies all 143 accepted checks against current helper/dependency hashes,
including the retained initial six-check component record. `git diff --check` passed.

## Remaining work

Wire this composition into the existing outer slot/chain, settings restoration and finite session
deadline, then finish immutable artifacts/profiles and asynchronous underlay quiescence admission.
Original data/APK restoration remains a phase-final operation. No device/private snapshot/reset,
APK build/install, profile generation or performance sample ran in this slice. This does not pass
or complete the phase gate.

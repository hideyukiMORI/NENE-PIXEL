# P4-05d measurement state between slots

Issue #145; PR #187 remains Draft. Rules: ADR0035, ARC007, QLT-011–QLT-019.
Waivers: none. Full phase and frame live admission remain closed.

## Change

The canonical `p4-device-private-preservation.ps1` gains `New-P4SlotResetPlan`. It verifies isolated
originals, then selects only measurement-created entries at the existing six recovery, underlay and
ProfileInstaller roots. Entries move without overwrite to
`no_backup/p4-layer-slots/<session>/<slot-id>/<original-relative-path>`. The slot archive must be
absent and its session must not belong to the original inventory. No-op plans add no directories.
Every directory/move prefix remains compatible with the existing final original-restoration plan.

`p4-device-private-slot-reset.ps1` consumes the actual verified preservation-v2 reader, expected
installed APK and explicit shared operation clock. It uses the existing native isolation step for
each mkdir/move, including full stopped inventories before and after. The complete proposed inventory
must fit the 4096-entry cap before any mutation. Immutable plan, pre/final inventories, source hashes,
step intents/results, APK identities and failures are retained in `nene-pixel-device-slot-reset-v1`.
The original guard is never consumed by a slot reset.

The private-preservation contract and phase protocol were updated before implementation.
No existing policy function, native byte transport, final restoration algorithm, Android code,
dependency, document schema or performance threshold changed. The new schema is measurement
evidence only. This helper has no collection/admission entry point; the outer wrapper still owns
canonical slot/preflight validation, host archives and final session restoration.

## Focused verification

The Issue registered the scope before implementation/checks. Command:

```powershell
pwsh -NoProfile -File docs/quality/validate-p4-slot-reset.ps1 `
  -CaseGroup <group> -OutputDirectory <fresh-evidence-directory>
```

| Group | Checks | Result | Seconds |
| --- | ---: | --- | ---: |
| Plan | 25 | PASS | 1.6833946 |
| Native | 14 | PASS | 3.302828 |
| Failures | 31 | PASS | 6.9678174 |
| Boundaries | 9 | PASS | 0.8825564 |
| Limits | 2 | PASS | 5.9973957 |
| Compatibility | 27 | PASS | 0.3125299 |

All 108 checks passed. Native fixtures create synthetic preservation records through the existing
isolation executor and read them through the real preservation reader. Only native observations,
APK identity and encoded moves are mocked. Cases cover complete/no-op execution, all move/mkdir
prefixes, exact file identities, no second attempt, drift, collisions, installed APK changes,
reserved time, missing clock and final inventory growth. Nineteen existing policy functions are
AST-identical to `7780204`; six native/preservation/budget sources are unchanged.

The first Plan invocation stopped before any accepted check because fixture helper `Dir` collided
with PowerShell's alias. It remains retained as a failure. Renaming the fixture helper to `MakeDir`
resolved it; production code did not change. Successful groups were not repeated.

Evidence root:
`D:/NENE-PIXEL/evidence/145-layer-slot-reset/20261004T012848707-5ace7f2bcf604b1691db941c895e0d42`.
`gradlew.bat validateDocumentation --offline` passed in 13.3220224 seconds with configuration cache
reuse. Source reconciliation matches all six accepted groups against the current helper inputs;
validator parsing and `git diff --check` passed. The initial failure remains in the same evidence
root. These results remain reusable for commit/push without repeating unchanged checks.

## Remaining work

Connect the existing outer slot/chain, stopped full app/provider archives, finite lifecycle budgets
and original restoration. Bind final immutable artifacts/profiles and prove asynchronous underlay
quiescence before actual collection. No device snapshot, isolation, reset, APK installation, profile
generation or performance sample was executed. These host checks do not pass the phase gate.

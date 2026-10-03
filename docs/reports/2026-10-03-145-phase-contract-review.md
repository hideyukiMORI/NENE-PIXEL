# #145 prospective phase contract independent review

Read-only review, 2026-10-03. Only this new report is an authorized write.
Reviewed HEAD `3f9d80bc92c8e15c60aaf5ade04f08b09db4f8de` plus the current uncommitted documentation diff:
`P4_LAYER_PHASE_PROTOCOL.md` lines 85–297 and ADR 0018's prospective evidence note.
Implementation agents' preflight/association changes are outside this review; old dirty reports were not edited.
Reviewed documentation SHA-256:
- Phase protocol: `66ecc12f9164cad81217b8ede6305aa2870196fd27c019fabb4fd4a0998ffe17`.
- ADR 0018: `e17e5c590d102f62354a9981be6e58c28d1d821e91168241751ce1d498e7d93d`.

Issue #145 / P4-05d; ADR 0018/0030/0031/0035; ARC-001/004/005/007/009/011;
CMD-001/002/008; QLT-011/012/013/014/015/016/019. Active waivers: none.
Final acceptance and collection authorization remain with the parent design seat.

## Findings

- Blocker: none.
- Should: none.
- Nit: none.

No concrete contract contradiction, arithmetic error, impossible state transition, new production
API, competing measurement route or implicit permission to alter originals was identified.
This is a prospective design review, not executable agreement or device acceptance.

## Frame population and verdict meaning

Protocol lines 93–126 distinguish comparison roles from immutable artifact roles and bind each
candidate decision to its own group's baseline. Three groups times four slots gives 12 slots.
Per-role decision counts are 110/110/55 operations including warmups; diagnostic counts are
45/30/15. The paired group totals are 310/280/140, hence 620 measured + 110 warmups = 730.
Applying `300 + 15 * operations` to all slots gives
`2*(1950+975) + 2*(1950+750) + 2*(1125+525) = 14550` seconds.
The 90-second cleanup and 120-second analysis reserves are additional per-slot bounds;
the collector sum is expressly a hang bound, not a duration estimate or a complete phase budget.

Lines 128–142 preserve full decision populations, diagnostics after candidate numeric failure,
gross-diagnostic/invalid/fatal stops and no retry. The historical baseline guard is 33.33 ms;
the candidate gate is independently 16.67 ms plus matched +1/+2-ms overrun limits.
This does not accept the baseline under M5 or lower the M5 budget. These meanings agree with
the final M5 section of `M2_FRAME_FOLLOW_UP.md` and the live Issue #145 body.

Lines 146–166 bind the first chronological DOWN-only row to its own identity/timestamps, reject
missing/foreign/reordered/tied/malformed rows and do not turn gfxinfo service into physical
presentation evidence. Complete raw/sample reconciliation and visible correctness remain
admission prerequisites. The helper alone is explicitly insufficient.

## Memory population and ownership

Lines 192–233 define baseline then candidate, five fresh PID/start pairs each, five checkpoints,
two GC/finalization passes each, actual MainActivity/ViewModel/Compose ownership and fixed debug/verify
conditions. C2 holds the long preview; C3 commits once; C4 returns after ten Undo/Redo pairs.
Published-position quiescence allows the exact already-published state without demanding a new
generation, consistent with ADR 0018's history-position identity.

Checkpoint-specific limits and five-run medians are not pooled across roles or substituted with
a shorter population. C4-minus-C3 heap growth is distinct from PSS delta and absolute heap limits.
Source inventory arithmetic is `16*(65536+8192)=1179648` primitive pixel bytes and
`15*(65536+8192)=1105920` additional non-target preview surface bytes plus 1024 palette bytes.
Separate ARGB arrays and Android bitmaps are identified. Logical payload is neither PSS nor a
512-KiB allowance. Test-owned snapshots/arrays and provider processes are excluded as stated,
while normal target-process instrumentation overhead is disclosed.

## Publication, SAF and preservation

Lines 248–255 keep one maximum/minimum AtomicFile invocation, 25 operations plus two summary rows
per group (54 rows), actual maximum Candidate 1182885 bytes and minimum 85 bytes.
Lines 256–278 select app instrumentation, the existing real DocumentsProvider and 25 distinct
fresh empty destinations with framework grants to the actual target app, before timing.
The public save-adapter interval includes encode/self-validation, picker return, write/close and
bounded exact read-back; destination creation and human wait are explicitly outside it.
This agrees with the memory/save integration probe and makes no cloud-provider or fsync claim.
SAF bounds sum to `300+60+60=420` seconds; its journal is `25+2=27` rows.

Lines 280–285 and ADR 0018's added note apply the existing 250-ms maximum-publication constant
decision and unchanged formulas to the v3 maximum. SAF is not subjected to that autosave threshold.
Neither old one-layer v3 results nor earlier v1/v2 observations are relabelled as this maximum.

Lines 170–188 and 287–297 explicitly defer executable integration, artifact/profile bindings,
real grant proof, underlay proof, complete phase stop/budget and preservation-v2 integration.
Those are known admission work, not findings against the present documentation slice.
No historical snapshot is presented as isolation; no slot cleanup owns the original guard.

## Verification and completion

Commands: read-only `Get-Content`, `rg`, `git diff HEAD`, `git status`, `git rev-parse HEAD`,
`Get-FileHash` and `gh issue view 145 --json title,body`. Manual arithmetic/source-contract
comparison completed. No tests, build, device command, commit, push or external mutation.
One guessed ADR filename failed during source discovery and was resolved via `rg --files`.
No new check result or performance PASS is claimed; reused executable results: none.
Documentation/schema change here: this review report only. Production behavior changed: none.
Unrelated failures: none new; existing #186 is not re-executed or absorbed.
Remaining risk: pending admission and executable/live boundary proof remain required before collection;
the review does not waive preservation/restoration or establish actual device grants/render state.
Active waiver IDs: none.

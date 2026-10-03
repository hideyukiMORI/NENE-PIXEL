# 2026-10-03 layer gate session handoff

Read `2026-10-03-layer-gate-session.md` for the current results and their evidence identities.
Issue #145 is the TODO authority; Draft PR #187 remains preparation. Waivers: none.

## Current state

- Active branch: `perf/145-layer-gate`, in the development lab's `worktrees/issue-145`.
  The historical `C:/Users/info/WORKS/NENE-PIXEL` checkout is dirty and must not be edited/reset.
- #172 is complete and merged through #185 at `1f9bb1637058d3fa4a98122f4942406211bd1c69`.
  All four owner visual confirmations are already recorded; do not ask for them again.
- hide also confirmed the app saved/stopped before the read-only snapshot. No preservation move,
  install, app start/stop or performance collection has occurred. The original APK remains installed.
- Native binary-safe transport/observer, snapshot, isolation and restoration are implemented.
  Source review is complete: one host case-collision finding fixed; no remaining restoration finding.
  Current checks: transport134/native106/planner2177/session141/snapshot125/restoration563 PASS.
  Older raw28/archive40/history2 results are reused, not repeated by another seat.
- The one drawable maximum fixture has SHA-256
  `165f62d180533849ce1a4ef1625cd3971e445f2dca60ef7b9b46fedaafa0b3ec`, length 1,182,862 bytes.
  Exported/checked/both merged AndroidTest assets match. Candidate length is 1,182,885 bytes.
  Focused fixture contract, ktlint, export and asset tasks PASS; APK-entry/device-load proof is pending.
- Typed persistence detekt has 27 unchanged findings; new fixture sources have zero. Together with
  the prior 13 core findings, separate Issue #186 owns 40. Do not fix them inside #145 or retry suites.
- Governing documentation validation PASS14.789 s. New completion prose does not change its contract.

## Next concrete work

1. Reconcile current Issue #145, `DEVICE_PRIVATE_PRESERVATION.md`, `P4_LAYER_PHASE_PROTOCOL.md`
   and the existing P4 preflight/slot collector. Keep one collector path. Wire preservation-v2 and
   restoration-v2, binding the expected installed APK from the phase's last recorded install.
   Snapshot/isolation/restore APIs share one evidence directory and current helper identities.
2. Finish prospective phase semantics before modifying measurement behavior: exact populations,
   ordering and finite budgets; new schema/verdict with M5 16.67 ms and unchanged relative limits;
   pinned underlay image/placement/alpha128; actual editor memory owners/checkpoints; v3 max autosave
   and real SAF user-save timing boundaries plus ADR 0018 constant decision. First-preview method
   is decided: DOWN-only layered tap, earliest valid app frame's own timestamps, not physical display
   latency. Preserve the long repeated-diagonal sequence. Underlay baseline predates #172, so its
   remembered state files cannot be used as an oracle; default construction alpha128 is available.
3. Implement each narrow collector/analyzer/validator slice in the existing harness after its scope
   is registered. No historical recipe authorizes a new run. Required frame baselines remain
   single-layer `8120c06`, layered `169b592`, nearest-neighbour underlay `f92b100`; full SHAs are in
   the prospective comparison section. Both layered roles load the same fixture through actual SAF.
   Baseline169b592 has no layer panel; do not invent a row-observation proof or add production UI.
4. After protocol/harness agreement, pin measurement overlays with unchanged production trees,
   APKs, packaged fixture/profile hashes, device and preflight. Generate a required profile once.
   Then take a fresh snapshot for the current helper/state identity, isolate, collect within the
   fixed budget and restore through the verified consumer. The earlier read-only snapshot remains
   historical and must not be silently upgraded or overwritten. Never move data before this admission.
5. Preserve every FAIL/invalid and partial artifact. Interrupted preservation/restoration refuses
   automatic resume; do not clear, delete, overwrite or improvise a replay. The actual run must retain
   measurement files and prove original bytes/mtimes/APK at completion. Phase #145 stays open until
   its own measured acceptance is fulfilled. Full required CI belongs to the final non-draft PR.

## Working instructions

hide explicitly delegated implementation/research/checks to suitable SOL/LUNA seats in this task;
NENE-PIXELサナ owns design and final judgment. Use a fresh small seat per probe/implementation/rework
stage as instructed, preserve concise source/evidence reports, and do not rerun successful checks
because a seat, commit or handoff changed. No image/design skill has been needed for this work.
All user-facing messages use hide and end with a fresh JST timestamp. The ordinary default remains
single-agent outside the user's current delegated scope. Work reports and lab evidence are retained;
do not bulk-stage unrelated untracked reports or delete earlier attempts.

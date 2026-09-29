# Evidence Locations

Issue #149 (2026-09-29) moved every development clone, worktree and raw evidence directory into one
folder, the development lab (see [Development Setup](../DEVELOPMENT_SETUP.md)). The lab was first
created beside the repository as `NENE-PIXEL-LAB` and was copied the same day to a short path
directly under a drive root, because device pulls fail beyond 259 characters (Issue #151); both
moves kept every file byte-identical. This document is the single map from the locations
that older evidence documents, handoffs and recorded manifests name to the current locations. Those
older records are history and are not rewritten: read a path they name through this map.

Paths on the right are relative to the lab root. `~` is the user's home directory.

## Moved on 2026-09-29

Every evidence directory was inventoried (relative path, byte count, SHA-256) before and after the
move and the two inventories are identical. Clones and worktrees were compared by `HEAD`, branch and
`git status`. The inventories and the move log are kept in `evidence/149-migration/`.

### Raw evidence

| Location named by older records | Current location |
| --- | --- |
| `C:/n73-diagnostic` | `evidence/m2/n73-diagnostic` |
| `C:/n76-functional` | `evidence/m2/n76-functional` |
| `C:/n76-post77-evidence` | `evidence/m2/n76-post77-evidence` |
| `C:/n76-post77-r8-evidence` | `evidence/m2/n76-post77-r8-evidence` |
| `C:/n76-stable5-evidence` | `evidence/m2/n76-stable5-evidence` |
| `C:/n77-evidence` | `evidence/m2/n77-evidence` |
| `C:/n77-functional` | `evidence/m2/n77-functional` |
| `C:/n81-functional` | `evidence/m2/n81-functional` |
| `~/.codex/tmp/nene-pixel-sol-20260905-180549` (`artifacts/`, `experiments/`, `host-fixtures/`, reviews, run records) | `evidence/m2/nene-pixel-sol-20260905-180549` |
| `~/.codex/tmp/nene-pixel-sol44-20260906-artifacts` | `evidence/m2/nene-pixel-sol44-20260906-artifacts` |
| `~/.codex/tmp/nene-pixel-sol44-20260907-fresh-user-home` | `evidence/m2/nene-pixel-sol44-20260907-fresh-user-home` |
| `~/.codex/tmp/nene-pixel-sol44-20260907-integration` | `evidence/m2/nene-pixel-sol44-20260907-integration` |
| `~/.codex/tmp/nene-pixel-81-artifacts` | `evidence/m2/nene-pixel-81-artifacts` |
| `~/.codex/tmp/nene-pixel-81-dexdiff` | `evidence/m2/nene-pixel-81-dexdiff` |
| `~/.codex/tmp/nene-pixel-81-experiments` | `evidence/m2/nene-pixel-81-experiments` |
| `~/.codex/tmp/nene-pixel-81-forensics` | `evidence/m2/nene-pixel-81-forensics` |
| `~/.codex/tmp/nene-81-current-state-forensic-20260908-0036` | `evidence/m2/nene-81-current-state-forensic-20260908-0036` |
| `~/.codex/tmp/nene-81-rotation-readback-20260908-0047` | `evidence/m2/nene-81-rotation-readback-20260908-0047` |
| `~/.codex/tmp/nene-pixel-qlt012-class-javap-20260908-0027` | `evidence/m2/nene-pixel-qlt012-class-javap-20260908-0027` |
| `~/.codex/tmp/nene-pixel-issue-85-evidence` | `evidence/m2/nene-pixel-issue-85-evidence` |
| `~/.codex/tmp/nene-pixel-design-rina-20260906`, `-20260907` | `evidence/m2/nene-pixel-design-rina-20260906`, `-20260907` |
| `~/.codex/tmp/nene-pixel-ui-design-20260912` | `evidence/m2/nene-pixel-ui-design-20260912` |
| `build/reports/issue-106/` of the evidence worktree `C:/n106-indexed` | `evidence/106-indexed-cutover/issue-106` |
| `build/reports/issue-120/` of the evidence worktree `C:/n106-indexed` | `evidence/120-frame-budget/issue-120` |
| `build/reports/device-checkout-128/` of `C:/n106-indexed` | `evidence/128-device-checkout/device-checkout-128` |
| `build/reports/device-checkout/` of the Issue #108, #124 and #142 worktrees | `evidence/108-device-check/`, `evidence/124-device-check/`, `evidence/142-device-check/` |

### Measurement clones and the tooling root

| Location named by older records | Current location |
| --- | --- |
| `C:/n106-indexed` (tooling root and evidence worktree) | `clones/tooling` |
| `C:/n106-baseline-build`, `C:/n106-candidate-build` | `clones/n106-baseline-build`, `clones/n106-candidate-build` |
| `C:/n120-baseline`, `C:/n120-candidate` | `clones/n120-baseline`, `clones/n120-candidate` |
| `C:/n142-baseline`, `C:/n142-candidate` | `clones/n142-baseline`, `clones/n142-candidate` |
| `~/.codex/tmp/nene-pixel-sol-20260905-180549/build-clones/*`, `run-clones/*` | `clones/m2/*` |

### Issue worktrees

| Location named by older records | Current location |
| --- | --- |
| `C:/n106-baseline`, `C:/n106-candidate` | `worktrees/n106-baseline`, `worktrees/n106-candidate` |
| `~/.codex/tmp/nene-pixel-sol-20260923/worktrees/issue-120`, `issue-124` | `worktrees/issue-120`, `worktrees/issue-124` |
| `~/.codex/tmp/nene-pixel-sol-20260928/worktrees/issue-108`, `issue-140`, `issue-142`, `issue-143` | `worktrees/issue-108`, `issue-140`, `issue-142`, `issue-143` |
| `~/.codex/tmp/nene-pixel-sol-20260929/worktrees/issue-142-latency` | `worktrees/issue-142-latency` |

## Lost before the move

A disk cleanup on 2026-09-28 judged directories by Git state alone and deleted clones whose ignored
`build/reports/` held raw evidence. The following locations are named by tracked documents and no
longer exist anywhere. Their recorded hashes and results stay valid as records; the raw files cannot
be read again.

| Location named by tracked documents | What it held | What remains |
| --- | --- | --- |
| `C:/n62-tap100/build/reports/baseline-profile-generation/issue-62-20260906-1611-384af834/` | The accepted Baseline Profile generation evidence of Issue #62: source profile, pair manifest, acceptance manifest | Hashes and byte counts in [M2 frame follow-up](M2_FRAME_FOLLOW_UP.md) and in the Issue #120 preflight manifest (`evidence/120-frame-budget/issue-120/`). The canonical profile in the repository is unchanged |
| `C:/n70/build/reports/baseline-profile-generation/issue-70-20260906-1640-374ad211/` | The candidate-profile generation evidence of Issue #70 | Hashes in [M2 frame follow-up](M2_FRAME_FOLLOW_UP.md) |
| `C:/n89-mvp/build/reports/issue-89/` | Raw durable-MVP acceptance evidence of Issue #89 | The summary in [M3 exit proof](M3_EXIT_PROOF.md) |
| `C:/n102-i18n/build/reports/issue-102/` | Raw localization evidence of Issue #102 | The summary in [M3 localization evidence](M3_LOCALIZATION_EVIDENCE.md) |

A preflight that requires the Issue #62 generation evidence as files cannot pass until the Baseline
Profile is generated again under [ADR 0010](../adr/0010-generated-critical-journey-baseline-profile.md).

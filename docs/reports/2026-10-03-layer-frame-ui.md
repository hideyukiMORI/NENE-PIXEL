# Layer-phase frame UI preparation

Issue #145 / Draft PR #187. Rules ADR 0031/0032/0034/0035, ARC-007, QLT-011 through
QLT-019. Active waivers: none. This is preparation; full phase admission remains closed.

## Change and limits

The existing frame collector now has explicit phase-only preparation for real New/Load and the
test provider picker. A staged fixture's retained raw identity and instrumentation log are hashed
again and bound to the frame slot, experiment, build and production. Preparation validates these
inputs before its first device call. The five-field frame context remains unchanged.

The host helper retains setup XML, PNG, bounded native logs and success/failure records. Each
non-single family performs one untimed preview/commit proof, checking ten fixed pixel positions.
The underlay uses the actual empty-work background and the pinned pattern at alpha 128; the
two-level RGB rounding allowance supports the independently proven factory, rather than proving
an exact alpha by itself. Normal work replacement follows that proof and each family's warmups.
Undo/Redo must be empty, overlays closed and the underlay visibly Shown. The app's actual English,
Japanese and Simplified Chinese success/visibility resources are bound to all applicable roles.

This adds twelve functional gestures outside the unchanged 620 measured and 110 warmup operations.
The shared UI preparation allowance is 300 seconds per slot, using bounded native calls with the
remaining time and termination reserve. Frame collector bounds add 300 seconds; maximum 3210.
No production code, app dependency, public API, storage format, threshold, scan/allocation/copy or
per-pixel production cost changed. New records are measurement-only UI-setup/pixel schema v1.

The normal switch waits active autosave publication and clears its tracking. However, ADR 0034
underlay memory has its own asynchronous recall/publication and no user-visible completion state.
Neither the status label nor screenshots establish that it has settled. That proof, the complete
outer wrapper/manifest, preservation/restoration and artifact/profile admission remain required.
The explicit phase live barrier is retained. No ADB, APK build/install, profile or performance run
was performed in this change; synthetic PNGs and mocked native/UI boundaries are host checks.

## Verification and retained failures

Lab evidence: `evidence/145-layer-frame-ui/20261003T232447717-b9b783fe1b3b45e4b6a52033d56a5be1/`.
The Issue scope was saved and published before checks. There are 161 distinct accepted checks:

| Command selection | Accepted checks | Validator seconds |
| --- | ---: | ---: |
| `validate-p4-layer-frame-ui.ps1 -CaseGroup Pixels` | 24 | 0.5891267 |
| `-CaseGroup Fixtures` using the retained staging input directory | 16 | 0.6625882 |
| `-CaseGroup Ui`, then strengthened `UiRetained` assertions | 8 distinct | 0.3138058 / 0.1697024 |
| `-CaseGroup WorkFlow`, then affected `WorkFlowUnderlay` after localized visibility | 6 distinct | 0.1508328 / 0.0947683 |
| `-CaseGroup Native` | 4 | 0.1137434 |
| `-CaseGroup Compatibility` successful prefix | 61 | 0.510125 |
| `-CaseGroup CompatibilityLoops` | 2 | 0.3953109 |
| `-CaseGroup Sources` | 28 | 1.5618197 |
| `validate-p4-layer-device-lanes.ps1 -CaseGroup FrameBudgets` | 12 | 0.3352437 |

All commands run with `pwsh -NoProfile -File docs/quality/<script>` and a fresh
`-OutputDirectory` below the evidence root; exact inputs, source hashes, logs and JSON results are
retained. Fixtures use `145-layer-frame-staging/.../identity-initial`'s eight accepted raw inputs;
the former stage's forty-nine parser cases are not replayed.

The initial compatibility invocation accepted sixty historical function comparisons and the
warmup loop, then failed because its validator assumed one `sampleIndex` loop. The collector also
has a later metadata loop. Both loops are now compared in source order; the two affected checks
pass. The failure and successful prefix are retained, and the sixty-one checks were not repeated.
The UI refusal checks were strengthened to assert preserved state outside the exception catcher;
five checks reran, replacing their original results rather than being counted twice.
The one-off source reconciliation initially refused because of PowerShell join/comparison grouping;
the corrected exact-set comparison passed. Its failed record is retained beside the final ledger.
Unchanged pixel, fixture and picker helper functions match the retained initial source exactly;
the later surface/status changes are covered by the workflow and resource-binding results.

The three unchanged switch/autosave source blobs are identical across all four productions.
Both New/Load publication-wait calls also match in the fourth file; its candidate-only imported-work
addition does not change those calls. The actual timed event and warmup source is unchanged.

Prior numerical analyzers, Android compilation/provider/fixture contracts, lane install/capture
and preservation algorithms retain their previously recorded evidence on unchanged paths. Only
the twelve affected frame budgets reran from the device-lane set. No unrelated test, full local
suite or existing detekt debt from #186 was rerun.

`gradlew.bat validateDocumentation --offline` — PASS, exit 0, 15.3254388 seconds.
The log and run record are retained at the evidence root. `git diff --check` passed.
Appending these numbers changes no links or rule references and does not repeat the check.

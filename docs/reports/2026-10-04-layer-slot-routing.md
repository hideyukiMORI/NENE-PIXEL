# Layer-phase collector and analyzer routing

Issue #145 / Draft PR #187. ADR 0031/0035, ARC-007, QLT-011 through QLT-019.
Active waivers: none. This is harness preparation; physical collection remains unadmitted.

## Change and evidence boundaries

The existing collector and analyzer now resolve the explicit phase's 24-slot catalog and separate
artifact roles. The collector verifies its reserved manifest hash and derived budget before dispatch.
Frame parameters use their group's baseline source/profile and only the registered canvas sizes.
Candidate decisions require the canonical completed baseline of the same group, with verified
analysis, restoration, worktree and capture-seal hashes. Memory analysis verifies every preceding
memory slot, in order, through the same retained-byte boundary.

`p4-layer-slot-routing.ps1` connects the existing numerical parsers. The analyzer binds canonical
raw input names, byte counts and hashes to the verified capture seal. Frame analysis also checks the
canonical external directory, full frame inventory, staged saved/emitted identity, setup sequence
and cumulative allowance, then decodes the retained PNGs again to check the fixed pixel probes.
The two existing geometry functions moved unchanged from the collector to the shared frame helper;
there is one implementation. Numerical algorithms, thresholds and measured/warmup loops are unchanged.

Frame preparation retains a staging-result-v1 record on success or failure. Non-single slots stop
all three writers and attempt stopped capture even after failed instrumentation; only successful
staging enters release-like collection. Release installation delegates to the previously verified
phase intent/readback helper. Frame-slot-v2 binds context, artifact role and inventory, including
partial collector output when an exception unwinds normally. Forced outer termination still needs
the pending outer wrapper to discover and seal that partial directory.

The additional stopped staging calls cost 210 seconds; release installation adds four identity
probes (120 seconds) to the existing frame verification. The resulting maximum collector allowance
is 3540 seconds, within the existing 3600 cap. The initial draft counted only two additional probes;
source reconciliation corrected that count before budget checks. The whole-session cleanup bound
is still pending. No app code, dependency, public API, file format or production runtime cost changed.

## Focused validation

Scope and selected checks were recorded in the Issue before execution. Evidence root:
`evidence/145-layer-slot-routing/20261004T000231206-d400fa0ea19e493188e8e138ba480c17/`.
All tests below use synthetic data or mocked native boundaries, with no ADB/device operation.

`pwsh -NoProfile -File docs/quality/validate-p4-layer-slot-routing.ps1 -CaseGroup <group>
-OutputDirectory <fresh-lab-directory>` produced 114 distinct accepted checks:

| Selected group | Accepted checks | Validator seconds |
| --- | ---: | ---: |
| Maps | 39 | 1.4248923 |
| Chain successful prefix | 19 | 13.3514342 |
| FrameChain (remaining groups/refusals) | 4 | 6.8485484 |
| Native | 8 | 0.5880255 |
| Setup | 11 | 1.7489623 |
| Dispatch | 11 | 2.1898125 |
| FrameAnalysis | 9 | 18.598775 |
| Compatibility successful prefix | 3 | 0.9306679 |
| CompatibilityRemaining | 6 | 0.9166149 |
| Collector | 4 | 1.0657183 |

The existing `validate-p4-layer-device-lanes.ps1 -CaseGroup FrameBudgets` adds 12 accepted checks
(0.3655668 s), for 126 total. Seven changed measurement scripts also passed parsing. Validation
source hashes and retained failed cases are recorded beside results. Existing Android compilation,
numerical boundary, install readback, encoded transport and preservation checks are reused on
unchanged implementations. No APK, profile, performance run, broad suite or cold build was started.

Retained validator failures were: automatic `$input` shadowing in synthetic staging generation
after 19 successes; a dot-sourced entry's dummy output argument overwriting the validator output
path before any checks; a `Case` parameter hiding its caller's function name; and selecting `for`
instead of `foreach` when comparing the unchanged timed loops, after three successes. The fixture
variables, invocation argument and AST selector were corrected. Only unfinished checks ran again;
successful prefixes were retained. The initial output-path error prevented its normal finally record,
so its native error and zero accepted count are separately retained.

`gradlew.bat validateDocumentation --offline` passed (exit 0, configuration cache reused).
`git diff --check` passed. Final source reconciliation verifies all 126 accepted checks against the
unchanged measurement sources and records successful parsing of the three affected validators.

## Remaining admission work

The complete manifest and frame live barriers remain closed. The outer wrapper still needs phase
reservation/completed-chain dispatch, stopped output capture, provider/work archives, measurement
state reset, original asset preservation/restoration, and finite cleanup/session budgets. Immutable
APK/profile bindings and physical acceptance also remain pending. ADR 0034 asynchronous underlay
recall/publication quiescence is unresolved; neither stable screenshots nor a status label is claimed
as its completion proof. This change does not declare the phase gate passed.

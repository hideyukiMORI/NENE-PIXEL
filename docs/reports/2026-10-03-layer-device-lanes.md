# 2026-10-03 — phase device-lane plans and retained reports

Issue [#145](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145), Draft PR
[#187](https://github.com/hideyukiMORI/NENE-PIXEL/pull/187). Rules ADR 0031/0035, ARC-007,
QLT-011–019. Waivers: none. Base: `749124f6f5eb806cf0fca52880e694ae60d24af2`.
Acceptance remains OPEN. No device call, app APK build/install, profile generation or sample ran.

## Behavior and files

The existing `p4-indexed-device-lanes.ps1` resolves the complete phase catalog to its actual artifact
role, exact APK set, eleven-field admission context and real Android runner. It reserves the exact
report paths and additional 300-second frame fixture setup. Memory/publication/SAF collector bounds
are 1080/930/1320 seconds. Single-frame plans add 390 seconds and staged-frame plans add 960 seconds
to the unchanged gesture-derived allowance; maximum is 2910 seconds, below the 3600-second native
cap. Report transfer belongs to stopped cleanup, so is not silently charged against instrumentation.
The [protocol](../quality/P4_LAYER_PHASE_PROTOCOL.md#device-lane-execution-and-retained-output)
records this prospective derivation and the remaining whole-session budget boundary.

The existing install function writes phase intent before mutation and records success/failure plus
actual post-attempt identity even after native failure. A failed readback is explicitly unconfirmed.
Historical install records retain v1 behavior. The instrumentation route reserves reports and returns
without live copies for phase plans. The new stopped-report helper checks all writer packages first,
then uses the unchanged bounded encoded transport with process, path-component link and single-link
regular-file guards. It retains every output and continues remaining reports after one transfer
fails. Occupied host outputs refuse all mutation; no report is moved or deleted.

`validate-p4-layer-device-lanes.ps1` owns focused plan, native-call orchestration, compatibility and
source-binding checks. Native functions are replaced only inside that standalone host validator;
it never calls ADB. The actual encoded transport's previously verified byte protocol is unchanged.
No production Kotlin, dependency, public API, project format or numeric gate changes. No scan,
allocation/copy, pixel arithmetic or preview work is added to the timed application.

## Selected verification

Scope was recorded in Issue #145 before execution. Lab root:
`evidence/145-layer-device-lanes/20261003T225213153-4d7a98a16acf423fa21358d7e460cc8a/`.
The command is `pwsh -NoProfile -File docs/quality/validate-p4-layer-device-lanes.ps1 -OutputDirectory <fresh>`
with the following separate `-CaseGroup` values. Inputs, actual plans, records and logs remain.

- `Plans`: 43 PASS, 1.8370395 s wall time. All 24 canonical plans, exact bounds, role/context/package
  refusals and capture ownership. Initial invocation failed before cases because a PowerShell join
  expression lacked grouping parentheses; source and failure remain. Only that comparison changed.
- `NativeBoundaries`: 12 PASS, 1.7315362 s. Actual orchestration functions with controlled native
  results check existing/install-success/nonzero/timeout/readback-failure/mismatch, intent collision,
  legacy install, stopped complete/partial/running-process capture and output collision. A native
  failure with an actually replaced APK remains failure and records the observed new identity.
- `Compatibility`: 28 legacy device plans match the pinned pre-change implementation exactly in
  `compatibility-scope-correction/`. The overall invocation then failed in a new source-name regex,
  which omitted digits in SHA256 argument names. Initial comparison-module command shadowing had
  also been corrected; its first invocation is not accepted as compatibility evidence. Both failures
  and validator sources remain. The corrected 28 plan results are reused without another replay.
- `SourceBindings`: six PASS, 1.5998925 s, after the regex correction; actual common Android argument
  keys, four one-test runner classes and opt-ins agree. Numerical frame/memory/storage analyzers and
  encoded transport have no diff. This group does not repeat the 28 successful legacy comparisons.
- `LaneDispatch`: six PASS, 1.4677828 s. Each memory/publication/SAF success and failure uses the
  actual existing instrumentation route, exact role/install/compile/reservation/timeout sequence;
  phase never calls the legacy live private copy and native failure is propagated.

The 95 accepted checks concern only this changed boundary. Earlier Kotlin, fixture, numerical,
provider, preservation and encoded transport successes are reused; no broad test suite ran.

`gradlew.bat validateDocumentation --offline` — PASS, exit 0, 13.2728696 s. `git diff --check`
passes. `source-reconciliation.json` binds all five result groups to unchanged device-lane source.
Documentation logs/run record remain in the lab root; this numerical addition changes no links or
contract and reuses the successful check.

## Remaining work

These helpers are not full collection admission. The outer wrapper must pass the reserved manifest
hash, perform stopped report capture in cleanup, retain provider/work archives, verify original
preservation and reset only measurement state between slots. It must connect the real release-like
picker/render/quiescence path, frame-slot-v2 and all analyzers, finish finite cleanup/session budgets
and reconcile last install evidence for final restoration. Current manifest and frame live barriers
remain closed. Actual immutable artifacts, profile, grants and physical acceptance remain pending.

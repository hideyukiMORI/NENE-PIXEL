# 2026-10-03 — maximum physical SAF-save preparation

Issue [#145](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145), Draft PR
[#187](https://github.com/hideyukiMORI/NENE-PIXEL/pull/187). Rules ADR 0018/0031/0035,
ARC-001/007/009, QLT-011–019. Waivers: none. Base: memory checkpoint `6133871`.
Phase acceptance remains OPEN. No device timing sample or new performance result is claimed.

## Changed behavior and files

Eight app AndroidTest helpers named `P4LayerSaf*` implement the prospectively fixed SAF lane.
The actual activity loads the pinned maximum through its production picker. Its immutable current
document is saved through the public `AndroidProjectStorageAdapter`, with a fixed picker consuming
25 distinct, fresh, genuinely granted provider destinations. Five warmups and 20 samples time the
whole public save call. Setup/picker UI and subsequent full-byte batch verification remain outside
that interval. Setup/worker/reporting have 300/60/60-second bounds; the native bound is 420 seconds.
No second runtime, codec dependency or production observation API is introduced.

The journal retains only bounded sample facts, rejects wrong order, duplicate URIs, interruption
and later writes after freeze, and selects the first minimum/maximum from measured samples only.
Saved requires one consumed destination; non-Saved or a completed operation above five seconds
invalidates the run while retaining its prefix. Reporting reserves a fresh phase directory with
initial invalid status. Setup records and identity are persisted before timing; all outputs remain
on failure. Complete status requires 25 successful saves and exact final-byte verification.
Native instrumentation success is separately required, including activity-close/reporting failures.

The existing publication analyzer gains explicit `Test-P4SafSaveCapture`, sharing only storage
context and instrumentation identity validation. It reconciles saved/emitted identity, all 26
ordered setup observations, exact URI/UID/byte/outcome correspondence, 27 CSV rows and first-tie
summaries. Its verdict is descriptive validity; ADR 0018's 250 ms decision remains publication-only.
`validate-p4-layer-saf-evidence.ps1` tests that contract. The existing fixture init script adds an
explicit JavaExec task against the actual pure AndroidTest journal/output/sample helpers.
The [protocol](../quality/P4_LAYER_PHASE_PROTOCOL.md#saf-worker-and-evidence-binding) records the
schema and prospective bindings before collection. The existing memory admission, real-SAF fixture
helper and provider quarantine implementation are reused unchanged.

No production Kotlin, dependency, plugin, module, public API, project format, autosave constant or
threshold changes. No scan, allocation/copy, per-pixel work or preview work is added to app code.
Test-side hash/byte verification and report I/O are outside measured operations; production encode,
validation, resolver write/close and accepted read-back remain inside the save interval.

## Verification selected before execution

Scope and exact Issue readback are retained as `issue-scope.md` under lab
`evidence/145-layer-saf-save/20261003T211523608-3c31819f82cf4654bc0d4097166cb305/`.

- Temporary formatting selected only the eight new `P4LayerSaf*` files. It passed; no persistent
  gate filter, exclusion or waiver was added. Source inspection confirmed that the existing app
  detekt task has no AndroidTest input, so an unrelated detekt run is not claimed as coverage.
- `gradlew.bat -I docs/quality/measurements/p4-layer-phase-fixture.init.gradle :app:android:layerPhaseSafHostContract :app:android:ktlintAndroidTestSourceSetCheck -Pp4SafContractOutput=<fresh-absolute-directory> --offline`
  — PASS on the first attempt, exit 0, 21.2750746 s; `saf-compile-host-style-initial.log` and run JSON.
  It compiles AndroidTest Kotlin and tests actual pure helpers for complete populations, warmup
  exclusion/first ties, ordering, duplicate URIs, freeze/interruption, failed/cancelled prefixes,
  five-second boundary, partial setup, reservation collisions and complete reports. Fixtures remain
  in `host-contract/`. No Android resolver/URI grant method or unit-test matrix ran on the host.
- `pwsh -NoProfile -File docs/quality/validate-p4-layer-saf-evidence.ps1 -OutputDirectory <fresh> -PublicationEvidenceDirectory <retained-publication-analyzer-directory>`
  — PASS on the first attempt, 81 cases, 5.4694347 s; `analyzer-initial.log`, `results.json` and
  retained input/result files. Coverage includes valid descriptive timing above 250 ms, five-second
  boundary/anomaly, malformed/partial/wrong-order rows, first ties, setup correspondence, strict
  integer types, all context fields, fixed identity facts and null input. Fourteen direct cases
  check the shared publication context/dispatch/key boundary with the previously retained capture.
  Android fixed-field agreement is source-derived; it is not actual device-output evidence.

`saf-compiled-source-hashes.json`, `saf-compiled-reuse.json` and
`saf-analyzer-source-hashes.json` bind exact source identities to those passes. Final source
reconciliation also checks publication Kotlin, the unchanged publication task body, and the
previous memory/provider compile inputs. No successful numerical test is repeated for a report,
commit or handoff. The [publication report](2026-10-03-layer-publication.md) retains its initial
task-classpath configuration failure and subsequent pass; no earlier failure is rewritten.

`gradlew.bat validateDocumentation --offline` — PASS, exit 0, 14.1593776 s;
`storage-documentation.log` and `storage-documentation-run.json`. `git diff --check` passes.
`storage-final-source-reconciliation.json` proves 21 compiled Kotlin/Java inputs unchanged and the
publication init projection's exact original SHA-256. The first bookkeeping attempt found that
the recorded Git blob hash had not been stored as an object; the byte SHA-256 ledger supplied the
comparison instead. A file-write flag mismatch was corrected before record creation. Neither
affected implementation or caused any test rerun. This numerical report addition reuses the
successful documentation result; it adds no link or governed-contract change.
No full suite, unrelated detekt debt, APK build/install, profile generation or device action ran.

## Remaining risks and next work

Real framework grant/load/save behavior and timing still require the admitted physical session.
Host contracts and compilation cannot prove those. Complete four-role artifacts, profile identity,
frame setup, outer wrapper, finite schedule and current preservation/restoration admission remain
pending. The wrappers continue to refuse collection until those agree. Continue directly with that
integration, retain historical FAIL/invalid evidence, and leave unrelated dirty session reports and
the other checkout untouched. GitHub #145 remains the task-state authority.

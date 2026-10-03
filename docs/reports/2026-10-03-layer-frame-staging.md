# 2026-10-03 — frame fixture staging contract

Issue [#145](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145), Draft PR
[#187](https://github.com/hideyukiMORI/NENE-PIXEL/pull/187). Rules ADR 0031/0032/0035,
ARC-007 and QLT-011–019. Waivers: none. Base: `52bd2f074ec25815166caa1a333b3f382c48f675`.
Acceptance remains OPEN. No APK build, device operation, profile generation or performance sample ran.

## Behavior and source scope

The three new `P4LayerFrameFixture*.kt` AndroidTest files derive the eight non-single frame
preparations from the fixed slot identity. They reserve a fresh report, stage the one pinned project
or PNG through the existing real-SAF helper and actual target-app grants, then persist and emit the
same primitive identity. The activity closes before native success. Existing names and reports
cannot be overwritten. The test has a 240-second bound; host integration reserves 300 seconds per
preparation. Single-layer slots need no staged asset.

`P4LayerRunAdmission` admits the four artifact roles; `P4LayerMemoryJournal` first restricts its own
lane to baseline_layers16/candidate. Existing APK, preservation and identity checks remain. The
measurement init script adds a pure JVM spec contract task without dependencies or APK tasks.
Preflight requires the spec and staging test in the closed measurement inventory.

The existing frame analyzer adds `Test-P4LayerFrameFixturePreparation`. It checks the fixed catalog,
asset/name/path/URI/size/hash, distinct UIDs, process facts, exact saved/emitted identity and complete
native success. It reuses the existing context and native-status helpers. This establishes a staged
source only; it does not establish a measured release-like app's subsequent load, rendering or grant.
The [protocol](../quality/P4_LAYER_PHASE_PROTOCOL.md#frame-fixture-staging-before-release-like-capture)
records that boundary.

No production code, public API, dependency, serialization format or gate changes. No document scan,
allocation/copy, pixel arithmetic or rendering operation is added to production or timed gestures.

## Selected checks and retained results

Scope was recorded in Issue #145 before execution. Lab root:
`evidence/145-layer-frame-staging/20261003T222828496-af74b4925bb846f5aa13e51d3661a74a/`.

- `gradlew.bat -I docs/quality/measurements/p4-layer-phase-fixture.init.gradle :app:android:layerPhaseFrameFixtureHostContract :app:android:compileDebugAndroidTestJavaWithJavac :app:android:ktlintAndroidTestSourceSetCheck -Pp4FrameFixtureContractOutput=<fresh> --offline`
  — PASS, 19.7332126 s. Actual spec code accepts eight slots and refuses 31 mismatches; its retained
  eight-row CSV is consumed by the host analyzer validator. Android Kotlin/Java compilation and
  source style pass. A temporary formatting init script selects only the changed AndroidTest files.
- On a fresh detached `8120c06fae1a372b23d2a7af4f50aa2b9cdfeff9` worktree, copy only the nine shared
  provider/SAF/admission/staging AndroidTest files, then
  `gradlew.bat :app:android:compileDebugAndroidTestKotlin :app:android:compileDebugAndroidTestJavaWithJavac --offline`
  — PASS, 43.9009009 s. The oldest baseline compiles this same preparation route; production diff is
  empty. Exact source/destination hashes and outputs remain in `baseline-single-overlay.json` and
  `baseline-single-compile-initial.log` with its run record.
- `pwsh -NoProfile -File docs/quality/validate-p4-layer-frame-staging.ps1 -OutputDirectory <fresh> -HostContractDirectory <retained>`
  — all 49 identity cases PASS, 1.6635003 s inside validator. The overall initial invocation fails
  afterward in a static source assertion because its field-name regex omitted digits in `sha256`.
  Initial source, inputs, per-case results and failure are retained. No device or analyzer failure
  occurred. Correcting the regex and invoking only `-SourceAgreementOnly` passes in 1.4794282 s wall
  time, checking emitted fields, bounds, memory-role guard and mandatory inventory paths. The 49
  successful cases are not repeated. A later message-only correction does not invalidate the pass.
- `source-reconciliation.json` — PASS: eight verified sources unchanged; all nine oldest-baseline
  overlay files match; all 23 pre-existing frame functions are identical after newline normalization;
  memory and storage analyzers have no diff. Prior numerical and preservation results are reused.

`gradlew.bat validateDocumentation --offline` — PASS, exit 0, 14.2181284 s; `documentation.log`
and its run record are retained. `git diff --check` passes. This numerical report addition changes
no link or contract and reuses that documentation check.

## Remaining work

The outer wrapper must account for this finite setup, install the measured release-like artifact,
open the staged file through the normal picker and prove visible correctness/quiescence before
timing. It must also connect storage capture, current native preservation and complete artifact,
profile and budget admission. Full phase admission and the frame collector's live barrier remain
closed until that work is complete. Real picker/decode/render evidence remains pending.

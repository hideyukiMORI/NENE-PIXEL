# 2026-10-03 — live-editor layer memory preparation

Issue [#145](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145), Draft PR
[#187](https://github.com/hideyukiMORI/NENE-PIXEL/pull/187). Rules ADR 0018/0031/0032/0034/0035,
ARC-001/007/009, CMD-001/002, QLT-011–019. Active waivers: none.
Implementation base: `de2b8e24f1944b675777cdc82dedc4761a631da2`. Phase acceptance remains OPEN.

## Behavior, ownership and files

The six new `P4Layer*.kt` app AndroidTest files implement an explicitly selected measurement on
the real MainActivity/ViewModel/Compose editor: empty idle, loaded maximum, held 16-MOVE preview,
committed/published position and ten Undo/Redo cycles returning to that position. No second runtime
is created. Assertions release copied pixels and state projections before the unchanged two-pass
post-GC sampler runs. Only primitive samples, names/URIs and an opaque autosave token persist.
The sources are `P4LayerEditorRetentionMeasurementTest`, `P4LayerEditorSession`,
`P4LayerEditorStateChecks`, `P4LayerFixtureDocuments`, `P4LayerMemoryJournal`, `P4LayerRunAdmission`.

Fixture staging uses the production create-document contract registered temporarily on the actual
activity, real DocumentsUI, a fresh provider document and actual target-UID read/write grants.
Pinned asset bytes are read back before the normal app Load picker is used. The same helper can
prepare later SAF-save destinations. `AcceptanceDocumentsProvider.java` delegates its file policy
to new `AcceptanceDocumentFiles.java`: logical deletion of phase `i89-145-*` files moves them
without overwrite into private quarantine. Partial and empty outputs survive; legacy deletion is
unchanged. The host filesystem validator exercises this exact Java helper.

`p4-indexed-preflight.ps1` adds the fixed ten memory slots. `p4-indexed-memory-analysis.ps1` adds
explicit phase identity, exact five-point raw/final reconciliation and sealed-predecessor input
contracts. It checks every C1–C4 numerical limit, five-run medians, C4–C3 growth and all ten process
identity pairs. The historical parser body and four families remain unchanged. Two focused host
validators check synthetic evidence and historical/Android field compatibility. The
[protocol](../quality/P4_LAYER_PHASE_PROTOCOL.md) records shared SAF staging, quarantine and output
contracts; within-role runtime maximum/memory-class stability is an evidence-validity condition.

No production implementation, public API, dependency, module, plugin, project format or numerical
threshold changes. No production per-operation scan, allocation/copy, pixel arithmetic or preview
work is added. The memory lane is intentionally intrusive; fixture scans and GC are outside every
latency lane. Android grant/load/render correctness and phase performance remain unmeasured.

## Scoped verification and retained failures

The Issue scope was recorded before checks (`issue-scope.md`). Evidence is retained under lab
`evidence/145-layer-editor-memory/20261003T194653240-bbeef2e4d13b4150ac0522e84fa36201/`.
No APK build/install, device operation, profile regeneration or full local suite ran.

| Command / direct boundary | Result and retained evidence |
| --- | --- |
| `gradlew.bat :app:android:compileDebugAndroidTestKotlin :app:android:compileDebugAndroidTestJavaWithJavac :app:android:ktlintAndroidTestSourceSetCheck --offline` | PASS, exit 0, 17.6798338 s; `app-compile-style-final.log` / `app-compile-style-final-run.json`. Initial formatting failures at 20.5888703 s and 26.3405579 s remain retained. The formatter was temporarily scoped to the six new files; no gate exclusion was committed. |
| Same two compile tasks on detached layer baseline `169b59287ca60e77e07ac91690450dd1a44b9ba4` with identical test overlay | PASS, 41.5624904 s; `baseline-overlay-compile.log` / `baseline-overlay-compile-run.json`, empty production diff, `baseline-overlay.json`. |
| `pwsh -NoProfile -File docs/quality/validate-p4-provider-quarantine.ps1 -OutputDirectory <fresh>` | PASS, 18 assertions, including exact partial bytes, preserved empty file, collision, legacy behavior and invalid names. `provider-empty-preservation.log`; all filesystem fixtures retained. Earlier 17-assertion result was followed by an explicit empty-file existence assertion. |
| `pwsh -NoProfile -File docs/quality/validate-p4-layer-memory-evidence.ps1 -OutputDirectory <fresh>` at the limits-only script revision | PASS, 17 cases, 9.02081 s; `analyzer-process-variable-correction/results.json`. Exact boundaries and one-unit misses for all four states, median populations, process chain and integer floors. Initial reserved `$PID` variable collision remains in `analyzer-initial/results.json`; corrected before this pass. |
| Same script with `-CaseGroup Integrity -PriorCaptureDirectory <passing-limit-fixtures> -OutputDirectory <fresh>` | PASS, 68 cases, 5.4166713 s; `analyzer-integrity-initial/results.json`. Passing limit cases were not repeated; all raw and prior inputs/results are retained. |
| `pwsh -NoProfile -File docs/quality/validate-p4-layer-memory-compatibility.ps1 -SyntheticCapture <passing-baseline-record> -OutputDirectory <fresh>` | PASS, 30 cases, 0.5909829 s; `analyzer-compatibility-line-ending-correction/results.json`. Exact legacy AST body and four result objects plus Android fields/order/facts. Initial comparison failed only because its saved historical text used different line endings; normalize CRLF/LF in that validator, with the failure retained. |

`app-verified-source-hashes.json`, `app-reuse-at-report.json` and `host-verified-source-hashes.json`
record source SHA-256/Git blobs. App sources stayed byte-identical after the compile/style pass;
baseline overlay hashes match. The analyzer stayed unchanged between successful numerical and
integrity/compatibility checks. Shared sampler/environment/physical-checkpoint sources remain the
existing ones. The prior fixture/underlay checks and unrelated detekt debt tracked in #186 were
not repeated. Synthetic/source agreement does not claim actual Android output or framework grants.

## Remaining work

Continue with maximum AtomicFile publication and preselected physical SAF-save instrumentation,
then the complete four-artifact phase manifest, existing outer wrappers, frame setup, artifact and
profile identities, bounds and native preservation/restoration admission. The legacy manifest and
phase live-collector refusal remain in force. No performance acceptance is claimed. Keep the
separate historical checkout, earlier dirty session reports, all failed evidence and baseline
overlay worktree intact. GitHub #145 remains the task-state authority.

Documentation: `gradlew.bat validateDocumentation --offline` PASS, exit 0 (reported Gradle duration
14 s); `validate-documentation.log` / `documentation-run.json`. `git diff --check` also passes.
Parent source review found no retained pixel/snapshot fields or alternate production owner.

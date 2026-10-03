# 2026-10-03 — layer frame connection

Issue [#145](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145), Draft PR
[#187](https://github.com/hideyukiMORI/NENE-PIXEL/pull/187); P4-05d remains OPEN.
Rules: ARC-001/007/009, ADR 0030/0031/0035, QLT-011–019. Active waivers: none.
The implementation base is `001337d32b13a2d36ba7d7d2b560b1039f71280e`.

## Changed behavior

The existing preflight, collector and analyzer now share the prospective frame execution contract.
The collector supports device-free contract validation for the three groups/twelve slots, writes
the new identity and first-preview columns through the existing row/sample path, and uses lossless
own-row arithmetic. The phase analyzer consumes v9/v6 evidence, checks the exact experiment
projection and identities, verifies each sample against its retained rows, and applies the existing
16.67-ms candidate gate and same-group +1/+2-ms relative limits.

Baseline analysis and its capture seal are read from the canonical outer wrapper slot; raw evidence
remains in the separate collector directory. One shared read-only seal verifier now serves the
existing completed chain and phase baseline reader, checking actual file size/hash and refusing
escaping, aliased or linked paths. No alternate collection or seal writer was introduced.

Full phase admission remains closed. No app code, dependency, project-file format, APK, profile or
device state changed. The phase setup/outer manifest/lane integration is a later dependency; these
host checks are preparation evidence, not performance acceptance. Historical results remain intact.

Files changed are the governing `P4_LAYER_PHASE_PROTOCOL.md`, existing preflight/frame collector/
frame analyzer/slot wrapper, the shared seal reader, four focused validators and their reports.
The previous handoff's absolute lab location was replaced with a portable description.
Unrelated pre-existing dirty session reports and evidence notes were preserved outside this change.

## Verification selected from the diff

The Issue recorded the frame connection scope before checks. Each validator targets a changed
contract or its direct caller. The detailed immutable commands, source hashes, results and retained
failures are in these reports:

- [Execution contract](2026-10-03-145-frame-execution-contract.md): 2,502 assertions PASS, 1.616701 s;
  four historical and twelve phase contracts, exact catalog/order/population/budget bindings.
- [Collector](2026-10-03-145-frame-collector-contract.md): 548 assertions PASS, 9.1575738 s;
  actual host invocation/state writing and CSV writer-to-analyzer boundary.
- [Capture seal](2026-10-03-145-capture-seal-boundary.md): 371 assertions / 98 read boundaries PASS,
  5.257 s, including the actual legacy writer and completed-chain consumer.
- [Phase analyzer](2026-10-03-145-layer-frame-analysis.md): phase/gate/association, strict state and
  canonical baseline binding results, including narrowly scoped corrections and result reuse.
- [Independent source review](2026-10-03-145-frame-connection-review.md): identity/verdict integration
  and direct caller review; verification is reused rather than repeated for review.

Catalog 456 PASS remains reusable: pre-existing catalog functions and five imported inputs are
unchanged, with the AST/hash comparison retained by the execution-contract report. The first-preview
281-assertion validator ran after its raw-core refactor; later baseline/state changes do not change
those pure helpers. Collector and seal checks are likewise reused when unrelated baseline/state
analysis changes leave their called functions and dependencies unchanged. Exact commands, input
identities and log paths are recorded in the linked reports. Earlier native preservation, restoration
and fixture results remain at their prior identities; none was rerun for this host-only change.

Initial validator failures were preserved and corrected only in their affected scope: root-array
JSON handling, extracted fixture directory binding, fixture timestamp validity and array cardinality.
The baseline reader's synthetic one-root layout was corrected to the existing two-root layout.
The analyzer's quoted JSON integer acceptance was corrected to match the writer. A proposed stricter
baseline gross-outlier gate was withdrawn after review against the accepted p95/fatal-only baseline
admission rule; no performance threshold was changed.

`gradlew.bat validateDocumentation --offline` PASS (exit 0), 14.1549209 s, after the final report
links were present. Log and source/result identity: `evidence/145-frame-connection-final/20261003T184122597-84066f747cc54acfa44520fe623dc897/validate-documentation.log`
and adjacent `validation.json`. Appending this result changes no validated link or governing rule.
Final targeted whitespace inspection passes; the implementation and recorded successful inputs
remain unchanged. Independent review records two resolved findings and zero unresolved findings.
No app tests, unrelated suite, full local canonical build, device operation or performance run.
Unrelated failures: none new; existing detekt debt remains separately tracked by #186.

## Cost, schema and remaining work

Per-operation app cost is unchanged: no document scan, size-proportional app allocation/copy,
per-pixel arithmetic or preview-frame work is added. Host validation adds small contract records
and file/row checks outside app timed intervals. Phase/frame/experiment/verdict identities are the
already-declared prospective versions. The protocol now specifies the internal PhaseContext,
canonical v6 projection, two evidence roots, native JSON state types and raw/sample reconciliation.

Remaining admission work is tracked by #145: exact underlay asset/placement/alpha, live-editor
memory and storage instrumentation, real SAF grants and fixture setup, complete four-artifact
manifest and outer adapter/frame-slot-v2 bindings, artifact/profile identities, overall bounds and
stop rules, and native preservation/restoration integration. Only after those agree can a fresh
original snapshot, bounded device collection, restoration and final acceptance take place.
No performance PASS, phase completion or merge is claimed. Waivers: none.

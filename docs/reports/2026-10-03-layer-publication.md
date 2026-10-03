# 2026-10-03 — maximum layer publication preparation

Issue [#145](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145), Draft PR
[#187](https://github.com/hideyukiMORI/NENE-PIXEL/pull/187). Rules ADR 0018/0031/0035,
ARC-001/007/009, QLT-011–019. Waivers: none. Base: memory checkpoint `6133871`.
Phase acceptance remains OPEN; this change collected no Android timing samples.

## Changed behavior and files

The existing `AutosavePublicationDeviceEvidence.kt` selects the layer phase explicitly. It decodes
the pinned maximum asset before timing, reuses the existing minimum factory, then runs the same
encode / production `RecoveryRecordWriter.publish` / accepted exact read-back interval. Maximum
and minimum envelopes are 1,182,885 and 85 bytes. Each group retains five warmups, 20 samples and
two summaries; generation spans 1–50 and the journal stays bounded at 54 rows. Worker/native/anomaly
bounds and ADR 0018's 250 ms constant decision remain unchanged.

New AndroidTest helpers are `AutosaveLayerPublicationAdmission`, `AutosaveLayerPublicationOutputs`,
`AutosavePublicationEvidenceFormat` and `AutosaveLayerPublicationHostContract`. They bind the
eleven phase fields plus the persistence-test APK hash, verify that installed APK and process UID,
write and emit the same identity, reserve fresh phase report/work directories, and retain partial
reservations and final/failed AtomicFile state. The phase has no test cleanup deletion. A failed or
wrong-generation write emits `failed`; legacy formatting/selection/cleanup remains unchanged.
The reporting rule, bounded journal and production writer/codec are reused without changes.

The existing `p4-indexed-publication-analysis.ps1` selects its new identity/schema/groups explicitly
and reuses the same row/summary/constant algorithm. It reconciles saved and emitted identity,
requires successful instrumentation, exact populations, canonical integers and chronological
generations, checks all summaries and keeps old-format semantics. `p4-indexed-preflight.ps1` adds
publication slot 23 and SAF slot 24 with their fixed 300/420-second bounds. The
[protocol](../quality/P4_LAYER_PHASE_PROTOCOL.md#publication-artifact-and-output-binding) records
these prospective bindings. The existing fixture init script adds one explicit JavaExec contract
task using compiled AndroidTest helper classes and the locked unit-test runtime classpath.
`validate-p4-layer-publication-evidence.ps1` provides the focused synthetic/compatibility checks.

No production Kotlin, dependency, plugin, module, public API, project format or threshold changes.
No app operation gains a scan, allocation/copy, per-pixel calculation or preview work. Test fixture
decode/hash and identity/report file I/O stay outside timed operations; the production writer's own
read-back/AtomicFile behavior remains included. No alternate writer or transport is introduced.

## Verification selected before execution

Issue scope and exact readback are retained as `issue-scope.md` under lab
`evidence/145-layer-publication/20261003T204613669-6775876df3264572b3cd3aaafd3bd270/`.

- `gradlew.bat -I <temporary-new-file-format-scope> :adapters:persistence:ktlintAndroidTestSourceSetFormat --offline`
  — PASS; `format-publication.log`. Only changed publication files were selected for formatting;
  no persistent gate filter or exclusion was added.
- `gradlew.bat -I docs/quality/measurements/p4-layer-phase-fixture.init.gradle :adapters:persistence:layerPhasePublicationHostContract :adapters:persistence:ktlintAndroidTestSourceSetCheck -Pp4PublicationContractOutput=<fresh-absolute-directory> --offline`
  — PASS, exit 0, 18.8800103 s; `publication-compile-host-style-classpath.log` and matching run JSON.
  The task compiles the AndroidTest code and executes only the actual pure reservation and row
  helpers. It checks empty CSV/invalid status/exact identity, existing-directory/file collisions,
  unavailable parent, preserved bytes, and legacy/layer rows for Written, wrong-generation,
  Failed and Uncertain results. All fixture files remain in `host-contract/`.
- The first attempt of that command failed at task configuration (15.875743 s): a DirectoryProperty
  needed wrapping as a FileCollection before classpath addition. No host test or compilation ran.
  The init-script correction produced the passing result above; the original log/run JSON remains.
- `pwsh -NoProfile -File docs/quality/validate-p4-layer-publication-evidence.ps1 -OutputDirectory <fresh>`
  — PASS, 63 cases, 4.8093129 s; `analyzer-initial.log`, `analyzer-initial/results.json` and every
  synthetic input/result. Cases include 250 ms / +1 ns, five-second boundary/anomaly, unchanged
  ADR formulas, first tie, population isolation, incomplete/foreign/duplicate/reordered records,
  APK/process/fixture/path identity, exact saved/emitted projection, and old baseline/candidate
  result objects against the pinned historical analyzer. Android output-field/fact agreement is
  checked from source; it does not claim actual device emission.

`publication-compiled-source-hashes.json`, `publication-compiled-reuse.json` and
`analyzer-source-hashes.json` bind these results to exact SHA-256/Git blobs. Compiled Kotlin stayed
unchanged after the pass. The init script subsequently adds the independent SAF host task; removing
only that addition reproduces the verified publication init input. The shared storage identity
parser was factored for SAF, then its publication dispatch, eleven context fields, own APK hash and
instrumentation identity key were checked in 14 direct cases with retained publication inputs.
The unchanged publication numeric/summary algorithm's 63-case result is reused. Final source
reconciliation is retained with the [SAF report](2026-10-03-layer-saf-save.md). Prior memory/provider results in the
[memory report](2026-10-03-layer-editor-memory.md) and fixture results in the
[underlay report](2026-10-03-layer-underlay-fixture.md) remain reusable; the unchanged journal and
reporting rule were not retested. No full suite, unrelated detekt debt, device operation, APK
build/install or profile generation ran. `git diff --check` passes.

## Remaining risks and next work

The persistence test runs in its own UID. Its local APK/identity checks supplement host admission;
they cannot inspect the main app's private preservation guard or prove real physical timing. The
phase manifest and outer wrappers still refuse new collection. Preselected physical SAF-save
instrumentation is implemented alongside this slice. Continue with artifact/profile/frame-setup/budget and native
preservation/restoration integration before collecting the bounded phase. Preserve the existing
dirty session reports, other checkout, historical failures and all lab outputs. GitHub #145 remains
the task-state authority.

# 2026-10-03: layer gate native session and shared fixture

Issue #145 / P4-05d remains open; Draft PR #187 contains preparation, not a performance verdict.
Rules: ADR 0030/0031/0035, ARC-007, QLT-011 through QLT-019. Active waivers: none.
This report continues `2026-10-03-layer-gate-native.md`; its older failures/results remain intact.

## Result and changed behavior

The measurement tools now compose one binary-safe native observer, read-only snapshot, canonical
isolation session and restoration consumer. `p4-device-private-transport.ps1` encodes shell stdout
as base64 with pipefail and strictly decodes into new bounded host artifacts. Native Windows shell
stdout was observed to translate 256 constant bytes into 257; normalization cannot preserve binary
data. The new encoding retained all 256 bytes and the remote upstream failure status.

`p4-device-private-snapshot.ps1` captures the original APK, exact durable inventory and verified tar
while stopped. `p4-device-private-session.ps1` admits only its bound successful snapshot and derives
the fixed move sequence from `p4-device-private-preservation.ps1`. Each mkdir/move has immutable
intent/result records and full before/after inventory verification against a canonical prefix.
`p4-device-private-restore.ps1` verifies the complete preserved sequence and the caller's expected
installed APK identity, restores the captured APK first, then archives measurement data and returns
originals. It verifies final bytes, file mtimes and APK identity. No deletion, overwrite or resume
of interrupted plans is permitted. Host path collisions are case-insensitive on Windows; device
inventory identity remains case-sensitive.

The four new private helpers and validators, native observer integration, planner prefix support,
and their preservation documentation are the tool changes. Application production paths are
unchanged: no additional per-operation scan, allocation/copy, pixel arithmetic or preview work.
Evidence schemas are encoded-transfer-result-v1, device-private-snapshot-v1,
device-preservation-v2 and device-restoration-v2; project serialization is unchanged.

Three `LayerPhaseFixture*` persistence host-test sources define, export and verify one drawable
maximum project through the production codec. The checked asset is
`docs/quality/fixtures/p4-layer-phase/maximum-layered.nenepixel`, 1,182,862 bytes, SHA-256
`165f62d180533849ce1a4ef1625cd3971e445f2dca60ef7b9b46fedaafa0b3ec`.
It has 16 visible fully covered 256-square layers, revision 0, 256 palette entries, 128-byte layer
names and a black pencil that changes every measured diagonal cell. Its Candidate envelope is
1,182,885 bytes. The explicit `p4-layer-phase-fixture.init.gradle` uses one checked source directory
for both AndroidTest asset merges; their exact hashes match. No dependency, plugin, module, public
API or production build-file change was added. Packaged APK entry and actual SAF load proof remain.

`P4_LAYER_PHASE_PROTOCOL.md` pins that fixture and first-preview assessment: a separate DOWN-only
layered tap reports the earliest valid app frame's own service-to-completion timestamps; the long
16-MOVE diagonal is unchanged. M5 UP-to-committed 16.67 ms and all-frame relative gates remain.
This is not a physical pixel-presentation claim. The complete collection protocol is still pending.

## Verification and retained evidence

Scopes/checks were registered on Issue #145 before execution. Paths below are relative to the
development lab's `evidence/` directory. Wall times are verification time, not performance samples.

| Command or directly checked boundary | Result | Evidence / wall time |
| --- | --- | --- |
| `pwsh -NoProfile -File docs/quality/validate-p4-private-transport.ps1` | PASS 134 | `145-encoded-transfer/20261003T155055686-6532a59020df4b1daa2cbb236c64d173/validation.json`, 1.964 s |
| `pwsh -NoProfile -File docs/quality/validate-p4-private-native.ps1` | PASS 106 | `145-encoded-native/20261003T155407352-aed38cd895e54333a3b86b246d0a3e6b/validation.json`, 3.098 s |
| `pwsh -NoProfile -File docs/quality/validate-p4-device-private-preservation.ps1` | PASS 2,177 | `145-move-prefix/20261003-161626/validation.log`; full and each completed prefix |
| `pwsh -NoProfile -File docs/quality/validate-p4-private-session.ps1` | PASS 141 | `145-host-collision-case/20261003T164116/session.log`, 3.469 s |
| `pwsh -NoProfile -File docs/quality/validate-p4-private-snapshot.ps1` | PASS 125 | `145-host-collision-case/20261003T164116/snapshot.log`, 2.249 s |
| `pwsh -NoProfile -File docs/quality/validate-p4-private-restore.ps1` | PASS 563 | `145-native-restoration/20261003T164324125-923ca2a5fae84769918fc61f0ff11cbe/validation.json`; wall time not separately recorded |
| Focused `:adapters:persistence:testDebugUnitTest --tests '*LayerPhaseFixtureTest'` + test-source ktlint | PASS 1 test, 0 errors/failures | `145-shared-fixture/20261003T163300JST/contract-ktlint-corrected.log`, 17.801 s |
| Explicit init script: fixture export, test-source ktlint and both `mergeDebugAndroidTestAssets` tasks | PASS, all four assets byte-identical | `145-shared-fixture/20261003T163700JST/export-assets-ktlint.log`, 21.142 s |
| `:adapters:persistence:detektDebugUnitTest --offline` | FAIL 27 in 17 unchanged files; 0 new-source findings | Same fixture directory, `detekt-debug-unit-test.log` and `debugUnitTest.xml`, 21.815 s; separated in #186 |
| `gradlew.bat validateDocumentation --offline` | PASS | `145-native-session-docs/20261003T164527461/validate-documentation.log`, 14.789 s |

Fixture commands use `-I docs/quality/measurements/p4-layer-phase-fixture.init.gradle --offline`;
export adds `-Pp4LayerFixtureOutput=<new lab evidence path>`. Exact complete commands and source
hashes are in `145-shared-fixture/20261003T163700JST/verification-manifest.txt`.
Prior raw capture 28 / archive 40 checks and the 2-test remap history result are reused unchanged.
Commit, review and handoff do not invalidate these inputs. No full local suite or cold build ran.
Documentation validation covered the final governing contract; this completion report adds only
already-defined rule references and retained results.

Independent session review found one Windows case-collision precheck defect. The focused fix above
passed once per affected validator; older 131/113 results are historical. Independent restoration
review found no actionable defect and verified the retained 563-check source identities, without
retesting. The restoration helper SHA-256 is
`cbe093dc6ada8bf2d1009a1b4b9ccc916251189f3ffe837393526a52c14963d4`.
All initial failures are retained, including formatting/locale/configuration corrections and the
synthetic restoration assertion failure. No unchanged failure was retried into a PASS.
Issue #186 now records 40 unrelated typed-detekt findings (13 core application +27 persistence),
with unchanged-file blob proof; no such repair or gate weakening is included in #145.

## Physical observation and remaining limits

hide confirmed saved/stopped before the read-only observation. The successful observation at
`145-native-device-readonly/20261003T155545623` contains 149 entries /90 files /59 directories,
10,188,099 file bytes and fractional file mtimes. The successful snapshot at
`145-native-snapshot-device/20261003T160746439/original-snapshot.json` verified exact before/after
inventories. Its archive is 10,335,232 bytes, SHA-256
`77d6b85eb4238f059d243ff5344c86766d919e11198414743c400c0abde72ebd`.
The retained original APK is 12,402,152 bytes, SHA-256
`44e1d211b3692ce15edae6003d371be38d41f15e3108b6e7647bfde130a88c94`, the owner-checked #172 artifact.
No device data move, install, app launch/force-stop, profile generation or performance collection
was performed. Earlier refusals remain; this snapshot is not relabelled after helper changes.

Actual isolation/restoration, phase-caller installation bindings, native preflight integration,
packaged fixture/load proof, complete frame schedule/schema/16.67-ms analyzer, underlay fixture,
memory owners/checkpoints, maximum autosave and physical SAF-save boundaries, profile and artifact
identities remain. Mocks and a read-only snapshot do not prove those results. No performance PASS
or Issue #145 completion is claimed. The original device data and owner APK remain live and intact.

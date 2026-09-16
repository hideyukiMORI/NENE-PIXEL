# 2026-09-13 indexed compatibility and conversion design

Issue: #106 / P4-02. Worktree: `C:/n106-indexed`, branch `feat/106-indexed-document`.
Base: `2dd4e01e3bbe88967237cde4e28412d2962fd590`. Waivers: none.

## Scope and decisions

This report records the compatibility/prototype preparation and subsequent contract acceptance;
it does not claim that the atomic cutover has shipped.
The original dirty `perf/54-compose-frame` checkout is not the implementation worktree.

- Accepted [ADR 0024](../adr/0024-indexed-project-compatibility.md) fixes typed legacy import,
  exact original-copy verification, preserved recovery lineage, one physical-operation lease,
  conversion-preview admission, cancellation drain and source/history identity.
- Normative [Project Format v2](../PROJECT_FORMAT_V2.md) fixes the byte table, shared v1/v2 carrier,
  version-specific bounds, CRC and palette-membership-before-snapshot allocation. V1 bytes stay
  exact. Only the future common writer accepts indexed DocumentState; the separate original-copy
  input is LegacyRgbaSource, never an editable RGBA fallback.
- Raw v1 data over 256 distinct RGBA values stays uninstalled. Reduction is explicit, creates a
  fresh dirty work with empty history, and cannot retire a recovery-only original without an exact
  verified project copy. New/load/decline/autosave and final retirement share this obligation.
- A destination epoch rejects A/B/A late previews. Original-copy proof is tied to retained source,
  operation and recovery lineage; destination changes do not invalidate a valid source copy.
- The gateway issues opaque source admission using private gateway identity, DocumentId and exact
  HistoryPosition. A gesture captures its Pencil slot or Eraser default and that admission at start;
  the adapter cannot substitute the state read at commit time. Undo to the exact admitted history
  position is equivalent, while a new branch is stale.

Rules: ARC-001/004/005/007–012, CMD-001/002/005–010/012,
KOT-001–008/013/016–020, QLT-006–009/011–016.

## Design workflow recovered from September 12 logs

Yesterday's actual workflow used Windows Claude Code 2.1.269 with `--chrome`, ordinary design
work and the `claude-in-chrome` connection. Sana independently used `cua.createBrowserTab("chrome",
...)`. Neither `/design` nor `/design-canvas` generated the design: `/design` managed consent/revoke,
`/design-canvas` was absent, and cached frontend-design SKILL.md was reference reading only.
The September 12 source/handoff is under
`C:/Users/info/.codex/tmp/nene-pixel-ui-design-20260912/`.

The initial September 13 attempt unnecessarily rediscovered that known limitation and stopped.
After hide's instruction to check the logs and continue, the established ordinary-design/Chrome
workflow resumed. No consent/revoke, plugin installation or settings change was needed.

Today's local design source is
`build/reports/issue-106/design/legacy-conversion.html`, served on loopback port 8770.
Claude session `009e5f96-f2c3-48fa-af09-7bc7a7d1080e` uses `--model opus`
(reported model `claude-opus-5`); it is not reported as a Fable execution. The mock has ja/en/zh,
dark/light, 600x400/600x960, exact original/reduced comparisons and separate external scenario
controls. Example data is a synthetic 64x64 source with 3,349 distinct RGBA values, not user artwork.

## Verification and evidence boundaries

The scoped plan was recorded before execution in ignored `build/reports/issue-106/work-plan.md`.
Two independent read-only SOL reviews found no remaining compatibility/storage/source-admission
contradiction after the documented corrections. Their reports are runtime-investigation.md and
review-storage.md in the same evidence directory. This is design review, not implementation proof.

- `git diff --check`: PASS, including the two new documents via intent-to-add.
- `./gradlew.bat validateDocumentation --console=plain`: PASS on JBR 21.0.11 in 9 seconds for the
  reviewed ADR/v2 inputs. The preceding attempt selected an IDE JBR 25 and failed the existing
  required-JDK-21 gate before validation; both logs are retained. No gate was weakened.
- Independent Python byte-fixture calculation passed earlier; the byte layout did not change in
  this review. It is not a Kotlin codec test or a performance result.
- Chrome actual clicks checked original/reduced comparison, copy picker/writing/failure blocking,
  verified-copy admission and current-editor staleness. Keyboard navigation and dark Japanese /
  light Chinese layout were inspected. The initial mock's immediate copy cancellation, history
  consent reset and copy-proof ownership differences were returned to Lina for correction.
- After Lina's corrections, Sana independently verified additional-copy failure preserving the
  earlier proof, exact Undo-back consent restoration, queued copy after preview, cancellation drain,
  and actual adoption as a new unsaved work with disabled Undo/Redo. Product button targets were
  corrected to at least 48 dp under ADR 0020 and checked in the DOM; English/light 600x400 and
  600x960 layouts were visually inspected. Inline JavaScript compilation through Node vm.Script
  passed. These are prototype checks, not the production application's contract suite.

Final local HTML SHA-256:
`213C9382C200F8FE628AA699185B90BA8A917626A372E3E9A71D4EFB0C45A857`.
The final documentation invocation, including this report, is retained as
`build/reports/issue-106/documentation-final.log`; its actual result must be checked before handoff.

Design corrections and final visual evidence are recorded in the local design handoff/review.
The mock never writes a project file. Its simulated success is not Android SAF, process-death,
durability, TalkBack, physical touch or performance evidence.

## Remaining work and verification preparation

At the prototype handoff, the ADR/v2 documents were proposed; live domain,
engine, commands/history, rendering, PNG, storage/recovery, UI and architecture enforcement have
not been migrated. No production file, APK, device data, Baseline Profile or dependency changed in
this iteration. No commit, push, PR, merge or Issue completion is claimed.

The prospective verification preparation is ignored
`build/reports/issue-106/prospective-verification-plan.md`. It identifies the affected command,
history/import memory, render and storage lanes; it is not an accepted collection protocol. The
current actual-app frame collector DOES exist as `docs/quality/measurements/measure-m2-frame.ps1`;
it must not be confused with an absent historical instrumentation-class name. Its 16x16 Pencil
journey is not the full indexed/legacy-conversion workload. Exact additional workloads, harness
mapping and immutable artifact identities still need agreement before new acceptance collection.
Historical M2/M3/#111 results are preserved and are not indexed-runtime evidence.

## Implementation-contract acceptance (2026-09-13, continuation)

Issue #106 remains the task authority. ADR 0024 and PROJECT_FORMAT_V2 are now accepted/normative.
Constitution, command model, layout, glossary, v1 mapping clarification and explicit scoped
refinements to ADR 0005/0007/0008/0009 now agree on indexed ownership, source admission, exact
inverse/history budgets and preserved import. Historical wire bytes and measurement verdicts are
unchanged. New Document inherits the current definition/default; only the initial app supplies its
first definition, so removing runtime palette configuration creates no hidden second owner.

This fixes the implementation contract, not an implementation or performance PASS. The next
verification is documentation validation plus affected module contracts/static/consumer compilation
as recorded prospectively in the local work plan. Device acceptance collection still requires an
accepted versioned protocol, matching executable harness and complete immutable artifact preflight.
No user decision is pending for these implementation choices. Waivers: none.

## Implementation checkpoint (2026-09-13, 17:50 JST)

The indexed cutover is implemented across domain, pixel engine, application commands/history,
v2 project and recovery codecs, Android persistence, Compose rendering and app composition.
DocumentState now owns the immutable palette and packed indices. Palette replacement follows
DocumentCommand with exact inverse history; gestures carry source admission and their captured
slot. PNG and display resolve the same document palette. The v1 import path preserves exact RGBA,
automatically adopts lossless bounded sources, and requires explicit reduction for larger sources.
The native conversion dialog includes source-copy verification, comparison, acknowledgement and
physical-operation cancellation drain. Recovery-only originals remain protected until exact copy
verification. The initial app palette and three conversion presets are composed at the app boundary.

Relevant narrow verification passed with JBR 21.0.11 and normal Gradle cache/daemon:

- Domain 41 and pixel-engine 51 host tests; project-format 43 tests.
- Application 170 tests passed with one existing opt-in measurement skipped, followed by the two
  new focused history-retention/preview-owner tests on unchanged production source.
- Persistence 56 host tests; app/presentation 56 host tests (24/32).
- Affected module ktlint/detekt, AndroidTest compilation and Android lint passed. Two English
  PluralsCandidate findings were corrected before the successful UI lint rerun.
- `validateDocumentation validateArchitecture :presentation:compose:detekt
  :adapters:persistence:compileDebugAndroidTestKotlin :adapters:persistence:ktlintCheck
  :adapters:persistence:detekt`: PASS, `production-freeze-narrow-1.log`.

Detailed commands, failed attempts and final logs remain under ignored
`build/reports/issue-106/`; the scope and check selection preceded execution in `work-plan.md`.
No check, lint suppression or waiver was weakened. Schema changes are the normative project v2
and recovery envelope v2, with historical v1 read/original-copy support retained.

The accepted performance protocol on this date was `nene-pixel-p4-indexed-cutover-verification-v3`.
It was superseded on 2026-09-16 by `nene-pixel-p4-indexed-cutover-verification-v4`, after the first
host slot of experiment `p4-indexed-v3-20260916` was preserved as INVALID for a harness sample-index
contract defect; the v3 bytes are archived as
[P4_INDEXED_CUTOVER_PROTOCOL_V3_HISTORICAL.md](../quality/P4_INDEXED_CUTOVER_PROTOCOL_V3_HISTORICAL.md).
The currently accepted protocol is `nene-pixel-p4-indexed-cutover-verification-v5`, which superseded
v4 on the same date after review found the frame and command lane wrapper bounds defective before
any device slot ran; experiment `p4-indexed-v4-20260916-run3` is preserved as superseded with its
five host slots intact, and the v4 bytes are archived as
[P4_INDEXED_CUTOVER_PROTOCOL_V4_HISTORICAL.md](../quality/P4_INDEXED_CUTOVER_PROTOCOL_V4_HISTORICAL.md).
Earlier uncollected protocol bytes are archived. Collector and bounded-process contract fixtures
are synthetic checks, not performance samples. Android functional execution, fresh user-asset
preservation, immutable APK/profile/preflight identities, all accepted performance lanes and final
required quality CI remain outstanding. Device contact so far only started the ADB server and
read the device inventory; no app data, app install, setting or measurement was changed.
This checkpoint does not close Issue #106 or claim device/performance PASS. Waivers: none.

## End-of-day functional checkpoint

The subsequent physical-device checks passed all 18 selected persistence, bitmap, conversion-dialog,
lifecycle and recovery tests. The existing durable journey then passed its five isolated stages,
including real system file picking, saved-byte preservation, PNG export, interrupted recovery and
three-language information UI. The selected-test wrapper's summary-writing failure is retained;
its successful raw JUnit output was independently bound to four installed APK byte copies without
rerunning those tests. Exact evidence and artifact identities are in the
[daily report](2026-09-13-indexed-cutover.md) and [handoff](2026-09-13-indexed-cutover-handoff.md).

The original 20 durable device files were restored and verified, including the install-managed
profile marker whose change correctly stopped the first restoration before mutation. Test outputs
remain separately archived. After launch the original 1,089-byte recovery record still matched its
preserved hash, and its recovery offer was left unadopted. Performance collection remains unstarted
and explicitly blocked at admission pending the documented integration review gaps. At hide's
request, work stops after the daily report and handoff are finalized. Issue #106 remains open.

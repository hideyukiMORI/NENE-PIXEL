# M4 structured pixel-art exit proof

Issue: [#226](https://github.com/hideyukiMORI/NENE-PIXEL/issues/226), P4-12.
Reviewed implementation baseline: `e9a8c6d0d55a35b086dee980fc8f48d42415e99a` (2026-10-14).
This is the four-criterion technical acceptance ledger and M5 entry decision. Formal M4 closure
also requires final PR CI, integration and the external read-back below. It is not M5 performance
acceptance or a beta release.

Rules: `ARC-001/004/007`, the canonical command/history contracts, `QLT-011` through `QLT-019`,
the [workflow](../DEVELOPMENT_WORKFLOW.md) and [milestone boundaries](../MILESTONES.md).
Active waivers: none. No runtime, schema, dependency, platform, public API, budget or gate changes.

## Inherited M3 acceptance and evidence limits

M3 was accepted by #89 / PR #104 before indexed editing began. Its [exit proof](M3_EXIT_PROOF.md)
records the exact c04 source/APK/file identities and five passing journeys. The #89/#102 raw
directories were lost before the lab move, as recorded in
[Evidence Locations](EVIDENCE_LOCATIONS.md#lost-before-the-move). Their tracked summaries/hashes
remain historical records; they are not surviving raw files, freshly verified hashes or current-v4
device results.

The design decision is to retain that historical M3 acceptance and judge M4's changes with the
surviving indexed/layered/v4 and later command/playback evidence. Current migration is supported
by independent old-version fixtures and the accepted original-preserving legacy flow, not a claim
to reload the missing c04 file. The later #142 durable journey is separate v3 evidence. The whole
beta device/lifecycle matrix belongs to P5-00/P5-02. No concrete M4 criterion failure has been
identified from the lost raw records alone, so an unrelated full M3 rerun is not selected.

Commands, manifests, failures and corrections retain their original identities. Lab-relative paths
below resolve through [Development Setup](../DEVELOPMENT_SETUP.md) and Evidence Locations. This
is a chain of affected-contract evidence from different source generations, not one test run on
the baseline above. The 2026-10-14 provenance review is retained in `outputs/issue-226/`.

## Four required exit criteria

| Existing criterion | Contract and accepted evidence | Decision and limits |
| --- | --- | --- |
| Exact M3 source/RGBA migration; explicit conversion for nonrepresentable sources | [ADR 0022](../adr/0022-indexed-palette-and-migration.md), [ADR 0025](../adr/0025-indexed-project-compatibility.md), [indexed cutover evidence](M4_INDEXED_CUTOVER_EVIDENCE.md), [ADR 0030](../adr/0030-ordered-layers-and-empty-pixels.md) and [ADR 0041](../adr/0041-bounded-frames-and-project-v4.md). #216 cases cover old-version admission, independent v4 goldens, corruption/resource refusal and recovery version pairing. The unchanged legacy workflow contract, final canonical CI and original conversion UI evidence are separately identified below. | Supported. At most 256 distinct complete RGBA values migrate exactly, including hidden RGB; more remain an uninstalled read-only conversion candidate. Explicit reduction creates a new dirty work and cannot retire a recovery-only original without verified preservation. V2/v3 load into one default frame with exact pixels, identity and revision. This does not accept current-v4 performance or all beta storage providers. |
| Every new persistent operation follows command/change-set/history | Palette Apply (#107), layers (#143), PNG layer import (#177), duplication (#208), rectangular Move/Copy (#211), transforms (#213) and frame commands (#216) use the canonical gateway/transition/history. Evidence covers inverse/replay, no-op/rejection atomicity, stale capture, dirty/autosave follow-up and retention. Later #221/#222 construction changes have separate evidence. | Supported. Preview/focus/playback and accepted underlay work-memory actions keep their existing non-document paths. PNG new-work import uses the accepted installation boundary. No second persistent mutation route is accepted. |
| Layer/frame operations remain deterministic and bounded | ADR 0030/0041, #208 and #216: global IDs/capacity, exact duplication and later edit isolation, order/durations, structural history accounting, all-frame remap, focus/cancellation, captured targets and active-only caches. | Supported at the fixed limits below. Logical bounds are not measured heap, drawing, save/publication or battery acceptance. #145 remains open at M5 exit. |
| Preview never becomes a second timing authority for document data | [ADR 0042](../adr/0042-workspace-preview-playback.md), #217 / PR #223: explicit samples reduce one workspace session; saved order/durations remain timing data. Synthetic edge cases and the isolated production-adapter journey prove neutral document/history/autosave state, bounded late samples, edit/display separation, stale cleanup and lifecycle stop. | Supported. One lifecycle adapter drives the canonical runtime/reducer. Same-frame display/cache is reused and background/resume does not restart playback. Functional test duration is not a performance result. |

Bounds remain 1–16 frames, 1–16 layers per frame and **at most 16 layer records in the whole
document**, globally unique layer IDs, canvas axes 1–256, palette 2–256 entries and duration
10–60,000 ms (default 100). Existing history entry/changed-cell/8 MiB logical-payload caps remain.
Sixteen frames do not each receive sixteen layers. Maximum visible stack (one frame × sixteen
layers) and maximum-byte v4 (sixteen one-layer frames) are separately identified fixtures.

## Accepted source and result chain

| Scope | Integrated source / original result | Evidence and attribution |
| --- | --- | --- |
| Indexed/legacy and layered foundations | #106 / PR #122, main `14320260`; #142 / PR #146, main `aee6294a` | [Indexed](M4_INDEXED_CUTOVER_EVIDENCE.md) and [layered](M4_LAYERED_CUTOVER_EVIDENCE.md) evidence. #142's surviving v3 durable journey has five PASS, plus recovery two and lifecycle three. The broader tracked 56/17/10 functional summary is historical; it is not wholly attributed to initial XML that still contains failures. Historical performance remains FAIL. |
| Palette, layer and import operations | #107 / PR #137, #143 / PR #157, #177 / PR #181 and #178 / PR #182 | [Palette evidence](M4_PALETTE_EDITOR_EVIDENCE.md) and accepted PR command/capture/undo records. V4 consuming tests cover changed hierarchy and captured frame target. Older pending notes are read with later Issue/PR acceptance, not presented as new executions. |
| Duplication | #208 / PR #210, main `bfd2eeaa`; 34 host + 10 isolated native | `outputs/layer-duplicate/implementation/`: original arguments/logs, source/APK manifests, native results and completion record. Exact metadata/cells, immutable sharing and later edit independence, one history entry and bounded refusal. |
| Selection and transforms | #211 / PR #212, main `50d10025`; 65 host + 11 native. #213 / PR #214, main `a21ffa2c`; 36 host + 5 native | `outputs/selection-move/implementation/` and `outputs/selection-transform/implementation/`: source/commands/APKs/results and reuse records. Exact Empty/index/overlap, preview/commit equality, eight orientations/64 compositions, Apply/Undo/Redo and bounded placement/cancellation. |
| Frames/v4 and consumers | #216 / PR #220, main `a4a27d3ef8ae03c58f5efd7fa5cffd58c68692b2`; candidate `b5b5ac8da7dcbacbd856bbdecda9922aaf4cbee8`, tree `8926ff8a08e97278891cf4495881f8fb6c83cc95`; required CI [38258995756](https://github.com/hideyukiMORI/NENE-PIXEL/actions/runs/38258995756) | `outputs/frames/implementation/evidence-reuse.md`, `completion-status.md`, `source-check-N.json`, `check-N-arguments.json`, logs/XML and `native-N/`. Final distinct coverage is **234 host + 6 native**: original 232 plus two corrected CI-discovered oracles. Repeated methods are not added again. |
| Loop/Once | #217 / PR #223, main `3356ffab7a07d989d44d13ed1f3957d481fb7807`; candidate `00a1495535040530da737645b7f4af720e9a1c56`, tree `d4c855f8b2abd48af1bdfa7db10e88e78a2d809b`; required CI [38269213488](https://github.com/hideyukiMORI/NENE-PIXEL/actions/runs/38269213488) | `outputs/frames/playback/evidence-reuse.md`, `issue-217-completion.md`, core/UI arguments/manifests/logs and `native-1/`. 35 host (23 core + 12 presentation) + 6 native. Unchanged #216 storage/codec results retain their original identity and recorded source comparison. |
| Typed quality and construction boundaries | #219 / PR #224, #221 / PR #225, #222 / PR #227; final main `e9a8c6d0d55a35b086dee980fc8f48d42415e99a`, candidate `b32e8834e57746c5645e17213f6ba3f018521d54`, equal tree `bb6b18dee5a9b30131bb09bb0b2fc60467f9d632` | `outputs/issue-219/`, `outputs/issue-221/`, `outputs/issue-222/` and `outputs/quality-cleanup-2026-10-13/completion-report.md`. 11 / 49 / 303 selected cases, affected typed checks at zero findings, caller compilation and independent review. #222 has 39 suites, correcting early 40-suite prose. Required CI [38396779157](https://github.com/hideyukiMORI/NENE-PIXEL/actions/runs/38396779157), [38399381840](https://github.com/hideyukiMORI/NENE-PIXEL/actions/runs/38399381840), [38404613801](https://github.com/hideyukiMORI/NENE-PIXEL/actions/runs/38404613801) passed; all three Issues are closed. |

### Original identities and corrections

#216's ledger binds checks 1–4 to the original 232-host subsets. Check 8 corrects only the v1
unsupported-version oracle 4→5; check 9 corrects the old other-layer gesture-retention oracle to
ADR0041 cancellation. Their manifests are `source-check-8.json` and `source-check-9.json`, with
SHA-256 `6daa250bc16335509eb83a64d35f60696f5dda94df28395f9042eeb74d3f7900` and
`c0e4535091eb1f328bb87eed5e35b3302a95e291bf67c819c25ae93072bf2e6f` respectively.
The final layout/lint correction and native capacity case bind `source-check-11.json`, SHA-256
`557d61ba878db757b1e43822184dcb9d5e811d3b85f8f2f1669a4c3050659e3d`, app APK
`343298c80921cbec4ff7067c52a015f4355dc844c8f98854277d13faab549ad5`, test APK
`99f157698563b03723f8eae5e710477d587fd627da45d366b8ff142b5f7efc3d`.
Five apparent PNG passes with invalid fixture bindings are excluded; corrected six-case evidence
is used. Native 1/2/4/5 cover six distinct accepted cases with explicit unchanged-input reuse.
Original CI/development failures stay retained.

#217's source-manifest SHA-256 is
`99daeb15cc7e416c060eb65d3b5eb2fe73d64ba2c6c166028900c221c0de2497`; app APK
`5d638a7363816041587d67a42d37dea392a9f1f4abdece3b68d1410cf756afe1`; test APK
`0f01299d2fdfb17136fdcd422a70af3f31c0bca0756f2f4034de514ab71339a1`.
Commands are in `core-check-1/2-arguments.json`, `ui-check-1/2/3-arguments.json` and the native
record; logs and `candidate-equivalence.json` explain the subsets. `normal-reflection-2/` records
guarded installation and equality of all 75 protected private files. That normal APK is still the
last reflected artifact; no old native result is labelled as built from `e9a8c6d0`.

The #142 raw journey resides at `evidence/142-latency/issue-142/m3-acceptance-i142-a/`.
The initial adapter/UndoRedo XML contains historical failures; later success records are distinct
and are not replacements for those XML. Similarly, original typed failures separated into
#219/#221/#222 remain failed at their original invocation. #222's initial final-CI failure was
corrected in one app composition method before CI38404613801 passed; it was not an unchanged retry.
The provenance audit identifies the checked individual files and hashes rather than claiming a
complete rehash of every historical artifact.

### More-than-256-colour preservation evidence

`LegacyImportWorkflowTest` explicitly covers a 257-RGBA uninstalled candidate, fresh dirty derived
installation, required verified original copy for recovery, failed-copy proof handling, cancellation
drain and rejection of proof from another operation. Its source SHA-256
`169731337b79d7019cefc38c45a5f37234eee3550248b389d3536433afc594a6` matches the #217 manifest,
accepted `3356ffab` and current `e9a8c6d0`. #222's final canonical CI38404613801 runs
`:core:application:test` successfully; `outputs/issue-222/ci-final.log` retains that gate result.
This is current committed-test/canonical-gate evidence, not a claim that all those methods were
in #216's 234 selected host results or that a new native conversion journey ran.

The original indexed conversion UI has three passing `LegacyConversionDialogTest` cases at
source `f5b41a8e974752fba2adb75c76d8daced52b67b0`. Its
`evidence/106-indexed-cutover/issue-106/functional-device-1/` contains `conversion-dialog.log`,
`invocation.json`, `verified-results.json` and `presentation_test-tested.apk`.
The log SHA-256 is `0e076542b798e6b652e6692c91de37ef3fdb13128685b60a4430e615d9846607`;
APK SHA-256 is `d50c602f78172f836ca74c9ccced6df6a02fbca7c3ec88d96b7361c7b50603b7`.
Both match the preserved record. The original wrapper-summary helper error remains recorded;
accepted raw JUnit success is distinct from that wrapper failure. This is historical indexed UI
evidence, not a current-v4 APK. The later v4 codec/recovery and current workflow gate provide the
changed-boundary evidence without relabelling it.

## Delivered scope and deferred candidates

M4 accepts the implemented coherent slice: indexed palette/draft/JSON editing, exact-slot
selection, ordered visible layers and duplication, a separate remembered reference underlay,
bounded exact-colour PNG import, same-layer rectangular Move/Copy and flips/quarter turns,
bounded frames/v4 and Loop/Once preview. These retain the accepted state/command boundaries.

The following are outside that slice and outside mandatory M5 beta hardening:

- saved layer opacity and merge; underlay opacity is a separate existing display operation;
- onion skin, sprite-sheet/animated export, playback scrub/Pause/audio and thumbnail caches;
- selection masks, cross-layer/external clipboard, delete, arbitrary-angle/scaled selection,
  image-generated quantization/dithering and tile/map features.

They need later focused product decisions. Saved opacity needs a future schema/ADR; v4 is the
frame format. No promise to implement these during M5 or speculative M6 backlog is created here.

## M5 entry constraints and performance placement

The [2026-10-12 owner decision](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145#issuecomment-6117166968)
places #145's combined collection at **M5 exit**. Historical P4-05d and current P5-03 identify the
same Issue, not two gates. Moving its GitHub milestone changes no result or threshold.

- #142 diagonal all-frame p95 remains `PERFORMANCE_FAIL`: 2.183 ms against 2.104 ms. Correctness
  fixes, Profile adoption and CI do not establish a new latency verdict.
- #190's accepted one-invocation Profile and raw records remain fixed. This proof, a handoff or
  commit does not authorize regeneration.
- V3 fixture/collector byte expectations cannot produce accepted v4 publication/SAF evidence.
  Reconcile protocol, fixture, correctness admission and harness before collection. Keep old v3
  bytes immutable and separately identify maximum-byte v4: 1,183,006 project / 1,183,029 Candidate
  bytes. These byte bounds are not timings.
- Registered #163/#168/#208/#211/#213/#216/#217/#221/#222 operations and source costs remain in
  the prospective review. Pin the actual candidate/Profile/role APKs and bounded preservation
  only at the accepted collection boundary. Historical eighteen-slot budgets are preparation,
  not permission to execute an unreconciled expanded workload.
- Beta device/API, accessibility/keyboard/stylus, lifecycle/low-memory, recovery/destructive
  review, startup/battery, privacy and a signed tagged artifact remain unaccepted. The next
  packages are defined in [Development Plan](../DEVELOPMENT_PLAN.md#m5-work-packages).

Beta disclosure must retain Save As-only output, empty reopened history, possible loss of latest
unfinished autosave/underlay changes, same-DocumentId underlay memory on Save As, recovery's
palette-draft cancellation and existing stale/pending-import policies. Existing tests do not
prove every provider, hardware power failure, screen or input configuration. P5-00 defines the
actual matrix; P5-02/P5-05 review the data promises.

## Verification and formal closure

This change is documentation and planning state only. Development checks are provenance
inspection, read-only independent review, diff/link review and `validateDocumentation` using
normal caches. No historical functional matrix, device benchmark, Profile generation or duplicate
local `check` runs. There is no runtime cost change. Original results and failures retain their
identities above and in their reuse ledgers.

Formal closure proceeds in this order:

1. Complete criterion/provenance review and focused M5 definitions; account for integrated
   #219/#221/#222 and move #145 to its accepted M5 placement.
2. Pass documentation validation and final non-draft required `quality`
   (`./gradlew check :app:android:assembleDebug`) against the current base.
3. Squash-merge; read back main/tree, required CI and #226's closed state.
4. Read back GitHub M4 with zero open Issues, then close and read back that milestone. Keep M5
   and #145 open. New M5 work is planning until prerequisites are accepted.
5. Preserve raw verification/read-back in `outputs/issue-226/` and record the actual verdict in
   the Issue. Preserve branches/evidence before auditing and removing the disposable worktree.

The live Issue carries actual integration/closure state. This condition does not claim a future
CI result inside the candidate that CI has yet to evaluate.

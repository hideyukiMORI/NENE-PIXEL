# Android Beta Acceptance Matrix

Status: M5 qualification plan; execution verdicts belong to the owning Issues

Issue: [#228](https://github.com/hideyukiMORI/NENE-PIXEL/issues/228), P5-00.
Entry: [M4 exit proof](quality/M4_EXIT_PROOF.md), integrated by #226 / PR #235 at
`b56d2da5871ff042688ead5f401de96b96d03d51`. M4 closure does not accept M5.

## Platform contract and meaning of coverage

The platform remains Android, minimum API26, compile/target API37, as accepted in
[ADR 0001](adr/0001-initial-build-toolchain.md). This matrix selects representative beta
qualification work; it neither raises the minimum API nor adds an upper installation limit,
desktop target or new hardware requirement. A supported-platform change needs the
[Workflow decision](DEVELOPMENT_WORKFLOW.md#decision-policy) and its ADR first.

Keep these claims separate:

- **Supported contract:** the existing Android/API, input and document contracts below.
- **Required qualification:** the named environments and criterion-specific checks planned here.
  All required gaps must be resolved before #234 accepts the beta matrix.
- **Tested:** an actual result bound to its original source, artifact, environment and oracle.
  A build, installed AVD image, historical summary or host test is not a current device result.

The inventory below was read on 2026-10-15 without starting a device or emulator. Availability
means retained configuration/evidence, not a live device admission. No row currently claims
complete M5 qualification. Intermediate API versions, other OEMs, foldables, multi-window,
unlisted providers and later Android versions have no additional qualification claim merely
because the APK can install. This does not silently exclude them from the existing platform
contract; a concrete failure is triaged on its affected boundary.

## Required environments

| ID | Named environment and role | Existing coverage and remaining admission |
| --- | --- | --- |
| `legacy26` | `NENE_I102_API26`, Google APIs x86_64 emulator, API26; configured 1200 x 1920 px, 272 dpi, 2048 MiB RAM | Image/configuration retained. Exercises the minimum API and API26-32 app-private language backing. #102 localization is historical summary with lost raw data; #229/#230 need current affected native evidence. |
| `phone35` | `Pixel_8_Pro_API_35`, Google Play x86_64 emulator, API35; configured 1344 x 2992 px, 480 dpi, 2048 MiB RAM | Image/configuration retained; M0 proof covers the old shell only. #229/#230 use this phone lane for current controls, insets, recreation and provider integration. Emulator RAM is not a physical low-memory budget. |
| `tablet36` | Physical ALLDOCUBE `iPlay80miniPro` / `T830`, API36, profile `NENE-P2-ALLDOCUBE-IPL80MP-A16-API36`; recorded 1200 x 1920 px, density override 272 dpi, 90 Hz | Named minimum **performance profile**, not a minimum RAM/resolution requirement for every user device. Retained M4 feature evidence is reusable only at its original identity. Reconfirm build, density, refresh, power/thermal state and preservation before device execution. |
| `target37` | API37 x86_64 emulator, prospective phone lane matching `phone35` geometry/density; exact image revision and AVD identity fixed before execution | No API37 system image or running-device evidence was found in the inspected lab. #229 owns obtaining/admitting this environment; #230 shares it for lifecycle/storage. SDK Platform37 alone does not satisfy this row. If unavailable, retain an explicit blocker for #234; do not substitute API36 or infer success from compilation. |

Emulators provide functional evidence only. #145 and #231 collect physical performance on
`tablet36`; no emulator timing, injected low-memory event, or workstation energy measurement
substitutes for that profile. USB/pen/keyboard availability and accessibility-service version
are unconfirmed and must be recorded by #229 before the corresponding physical claim.

## Bounded screen, language and input selection

These are prospective checks, not new minimum-display or font limits. Record actual window and
safe-content dp, density, font scale, orientation and insets with each native result. Pixel
resolution alone does not identify available layout space. #229 combines existing Compose
layout tests with native checks, selecting the smallest cases that cover these boundaries:

| Boundary | Required configuration and oracle | Owning evidence |
| --- | --- | --- |
| Normal surfaces | `phone35` and `tablet36`, portrait and landscape, font scale 1.0; Tabletop and Handheld on both physical edges, light and dark across the selected cases | Controls remain reachable with their 48dp targets; canvas mapping stays exact; panels scroll within safe insets; dialogs and their confirm/cancel actions remain usable. |
| Compact / large text | Constrained Compose safe content 360 x 640 dp and 640 x 360 dp, font scales 1.0 and 2.0; native `phone35` landscape at 2.0 and `tablet36` portrait at 2.0 | No clipped or unreachable primary, dialog, frame-duration, palette or file/recovery action; scrolling is permitted. Existing 600 x 400 / 600 x 900 tests do not prove this new boundary. Failure needs a scoped fix or an explicit contract decision, not a silent smaller matrix. |
| Languages | English, Japanese and Simplified Chinese (`en`, `ja`, `zh-Hans`); System selection plus explicit overrides on both `legacy26` and API33+ (`tablet36` or `phone35`) | Current editor and confirmations use the selected resources, including errors and disabled-state descriptions. Check all three languages on the compact large-text surface; cover legacy dialog/restart and OS locale/recreation boundaries with the owning native tests. Preserve the disclosed OS-upgrade language-transfer limitation of ADR 0021. |
| Touch / pen contact | `tablet36` touch; real stylus contact if available, with exact device/tool identity; canonical down/move/up/cancel, bounds exit/re-entry and two-contact arbitration | Same pixel coordinates and one history operation per committed gesture; cancellation makes no partial command. Synthetic pointer tests prove the reducer/adapter contract only, not physical stylus behavior. Hover, pressure brushes and pen-button shortcuts are not adopted features. |
| Keyboard / accessibility | `phone35` functional keyboard events and TalkBack, plus an identified physical keyboard on `tablet36` when claiming hardware coverage | Tab/Shift-Tab focus, Enter/Space activation where applicable, Back/Escape dismissal, focus restoration, labels, selected/disabled meaning and error/confirmation announcements. All existing control surfaces, including palette, layers, frames, selection, playback and file/recovery, remain operable through the same callbacks. No editor shortcut vocabulary or second canvas mutation path is invented. |

This is a boundary set, not the Cartesian product of every setting. #229 records a concrete
case-to-boundary map before execution and reuses unchanged coverage. It must resolve missing
pen/keyboard hardware and the exact TalkBack interaction scope, including the canvas, explicitly.
An untested required interaction is a gap, not an accepted limitation. Missing or unusable
essential controls block qualification. New assistive drawing behavior or a support reduction
requires a focused decision before it can change the above contract.

## Criterion, oracle and evidence ownership

All rows use synthetic artwork. A downstream Issue fixes its exact selected command/test methods,
fixture hashes and candidate before execution; this document does not authorize a broad suite.

| Requirement | Package / environment | Acceptance oracle and reusable foundation | Concrete outstanding work |
| --- | --- | --- | --- |
| Reachable, localized input | P5-01 [#229](https://github.com/hideyukiMORI/NENE-PIXEL/issues/229); screen/input rows above, API26 and API37 boundaries | ADR 0020/0021, canonical gesture/action contracts and existing presentation/native tests; M4 original results below | Current TalkBack/focus, keyboard/physical pen, compact/large text, EN/JA/zh-Hans and API37 evidence. Fix demonstrated control failures within their owning boundary. |
| Rotation, background and recreation | P5-02 [#230](https://github.com/hideyukiMORI/NENE-PIXEL/issues/230); `legacy26`, `phone35`, `tablet36`, `target37` with API-specific cases | One runtime owner; committed document survives configuration changes; preview/draft retention follows the accepted action; playback stops on background with no catch-up. #216/#217 host/native evidence provides a foundation, not all combinations | Separate rotation, background/ON_STOP, process recreation and controlled low-memory/termination. State the last durable revision and expected discarded transient state before each case; injected memory events do not simulate hardware power failure. |
| Project, recovery and destructive operations | P5-02 #230; host codec/workflow boundaries plus named native provider lanes on `legacy26`, `phone35` and `target37`; `tablet36` integration | Exact whole-v4 round-trip, versioned recovery lineage, atomic stale/cancelled completion, original-preserving v1 conversion including more than 256 RGBA values, typed failure and correct discard confirmation; reuse M4 evidence by input equivalence | Select actual missing end-to-end v4/provider/interruption cases. Keep failed/partial output, original-copy failure, cancellation and late completion observable. Review each destructive entry point; no critical data-loss/replay defect may remain. |
| Drawing, memory, large work and save | P5-03 [#145](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145); `tablet36` only for device verdict | Existing accepted protocols and [M5 drawing budget](quality/M2_FRAME_FOLLOW_UP.md#m5-drawing-latency-budget--fixed-2026-09-23-issue-135); deterministic host limits separately | Reconcile v4 fixtures/collectors and every registered workload, candidate/Profile/role APK and preservation before the final phase. Historical #142 2.183 ms versus 2.104 ms `PERFORMANCE_FAIL` remains failed. |
| Startup, long-session and battery | P5-04 [#231](https://github.com/hideyukiMORI/NENE-PIXEL/issues/231); `tablet36` final phase with #145 | Prospectively accepted metric/population/budget/observer and bounded stopping rules; launch logs or CI are not acceptance measurements | Define the missing protocol and budgets before samples, reconcile orchestration with #145 and account for collection/preservation cost. No threshold is chosen after results. |
| Privacy and external files | P5-05 [#232](https://github.com/hideyukiMORI/NENE-PIXEL/issues/232); source/config audit, affected native checks only if behavior requires them | Manifest/dependency/data-flow review, app-private recovery/underlay, Android backup and SAF boundaries; truthful localized disclosure | Audit merged artifact permissions and backup rules; explain retention/removal, provider-controlled network/storage, errors and logs. Resolve discrepancies under the existing ADR process; no legal certification claim. |
| Signing, version and artifact | P5-06 [#233](https://github.com/hideyukiMORI/NENE-PIXEL/issues/233); canonical build/role path | ADR 0001/0010/0013, strict dependency evidence and accepted Profile; keys outside Git/logs | Fix key custody, version/update policy, changelog/checklist and reproducible tagged-checked-main procedure. Supply immutable measurement-candidate inputs without calling that APK the beta. |
| Complete beta exit | P5-07 [#234](https://github.com/hideyukiMORI/NENE-PIXEL/issues/234); all required rows | All four [M5 exit criteria](MILESTONES.md#m5--android-beta), criterion-to-original-result ledger, actual defect review, tagged artifact provenance and live CI/Issue/milestone read-back | Close every required coverage/performance/data-integrity gap; produce/verify the beta only from tagged checked main. This plan, M4 closure or an untagged measurement build cannot supply the verdict. |

### Storage-provider scope

The required native lane is Android's local DocumentsUI/device-storage provider on each named
API boundary above, with its package/version, selected root and URI grant mode recorded before
use. Test the existing SAF create/open route for project Save As/load, palette JSON, PNG export
and image import at the affected boundaries. Synthetic controlled streams cover read/write,
read-back mismatch, denied/revoked access and partial-output cleanup failure; they are not proof
that every real provider behaves identically. #230 owns exact case selection and provider identity.

The app's offline journey does not control a selected cloud provider's network or retention.
Removable storage, third-party/cloud providers and persistently remembered external-file grants
have no blanket qualification claim. A provider outside the tested local set still receives the
same canonical typed I/O/failure behavior; #232 states this limit and #230 handles actual defects.
No in-place overwrite or new provider implementation is introduced by this matrix.

## Existing operating limits and disclosure

The current editor has 1-256 pixels per axis, a shared 2-256-entry palette, 1-16 frames and at
most **16 total layer records across the entire document**, not 16 layers times 16 frames.
Frame duration is 10-60,000 ms. The precise bounds, history accounting, byte limits and migration
semantics remain in [ADR 0041](adr/0041-bounded-frames-and-project-v4.md) and
[Project Format v4](PROJECT_FORMAT_V4.md); this document does not redefine them.

Beta disclosure and #230/#232 acceptance retain these existing boundaries:

- Save As creates a fresh output. Opened/recovered history is empty. V3/v2 migration is lossless
  under the format contract; v1 uses explicit conversion and verified original preservation
  where required. PNG exports the chosen frame's visible composite, without the underlay.
- Recovery is the last safely published record. Latest unfinished autosave/underlay changes may
  be lost; neither a force-stop test nor atomic app-private publication promises hardware
  power-loss durability for every external provider. Palette draft recovery/cancellation and
  stale or pending import policy remain as accepted.
  [ADR 0045](adr/0045-android-termination-and-publication.md) maps Back/recents, process death,
  cancellation and storage failure to the existing publication path and the unaccepted #230
  integration fault cases; a returned lifecycle callback is not proof of completed publication.
  The [M5 recovery fault coverage](quality/M5_RECOVERY_FAULT_COVERAGE.md) records #242's bounded
  adapter/workflow host slice, #244's actual-UI admission defect/correction, #247's native
  adapter/scheduler/status fault cases, #249's selected native caller/port cancellation boundaries,
  and #251's actual-MainActivity TERM-STOP-KILL preparation at before-publication, partial-write
  and verified-completion phases. #251 requires separately admitted synthetic userdata, exact-PID
  injection, stopped byte preservation and normal-factory recovery; its source alone is no PASS.
  Physical storage faults, other cancellation phases and native lifecycle obligations
  beyond those bounded cases remain explicit; their existence alone is not acceptance.
- Underlay memory is device-local and separate from project/recovery/export; Save As may retain
  the same DocumentId and its remembered underlay. Appearance is session state, while app language
  uses the API-selected preference backing. #232 must explain actual backup/removal behavior.
- Saved layer opacity/merge, onion skin, animated/sprite export, extended selection, quantization,
  cloud sync and M6 transport remain outside this beta hardening scope.

## Reuse ledger and limits of provenance

The [M4 exit ledger](quality/M4_EXIT_PROOF.md#original-identities-and-corrections) is the canonical
index of original commands, source manifests, APK hashes and raw paths. Paths below are relative
to the configured development lab. They identify evidence, not files to regenerate in place.

| Evidence | Identity / retained location | Permitted use in M5 |
| --- | --- | --- |
| #216 frames/v4 | Accepted main `a4a27d3ef8ae03c58f5efd7fa5cffd58c68692b2`; `outputs/frames/implementation/`, manifest `source-check-11.json` SHA-256 `557d61ba878db757b1e43822184dcb9d5e811d3b85f8f2f1669a4c3050659e3d`; original selected commands/results in its reuse ledger | 234 host + 6 accepted native cases across explicitly identified subsets. Compare affected sources/fixtures/dependencies before reuse; five invalid PNG bindings stay excluded. The mixed `native-1` invocation remains failed; only its four accepted methods and separately corrected results count. Not a new API matrix run. |
| #217 playback | Accepted main `3356ffab7a07d989d44d13ed1f3957d481fb7807`; `outputs/frames/playback/`, `core-check-1/2-arguments.json`, `ui-check-1/2/3-arguments.json`, `native-1/`; APK SHA-256 `5d638a7363816041587d67a42d37dea392a9f1f4abdece3b68d1410cf756afe1` | 35 host + 6 native cases, with their original source/test-APK/log identity and equivalence record. Last recorded normal-device reflection is this APK; no claim that it is current main. |
| #219/#221/#222 quality contracts | `outputs/issue-219/`, `outputs/issue-221/`, `outputs/issue-222/`; final #222 candidate `b32e8834e57746c5645e17213f6ba3f018521d54`, tree `bb6b18dee5a9b30131bb09bb0b2fc60467f9d632`; 11/49/303 selected host cases | Current construction/consumer boundaries after source-equivalence review. Canonical CI checks compilation/contracts; it does not supply native, physical input or performance evidence. |
| API26 localization / API35 shell | #102 [localization summary](quality/M3_LOCALIZATION_EVIDENCE.md), [M0 proof](quality/FRESH_CLONE_PROOF.md) | Historical test scope only. #89/#102 raw data was lost before lab migration; do not present it as re-readable files or current-v4 acceptance. See [evidence locations](quality/EVIDENCE_LOCATIONS.md#lost-before-the-move). |
| Physical profile / old performance | #145's retained registry, #142 layered result and #190 accepted one-invocation Profile provenance | Fix the environment and comparison lineage. Preserve every old FAIL/invalid and protocol identity; these do not establish a final-M5 pass or authorize Profile regeneration. |

Reuse under QLT-012 records the original command, source/artifact/test-APK, variant, dependency/
toolchain/Profile, harness/fixture, environment and log identities plus the relevant-input
comparison. Missing raw evidence may remain historical context; it cannot be silently upgraded
to reproducible new evidence. A later commit/tag or report alone does not invalidate a result.

The surviving #142 v3 durable logs remain historical supplementary evidence. Current APK bytes
in its old candidate clone differ from that run's recorded hashes; do not identify them as the
original artifacts. #230 needs current-v4 provider/recovery evidence, not a relabelled v3 run.

### Existing check entry points

These names guide narrow selection; their existence is not execution evidence. The owning Issue
chooses methods and relevant callers after comparing the original inputs. Missing native cases
extend these canonical boundaries rather than creating another acceptance framework.

| Gap owner / boundary | Existing check implementations to inspect |
| --- | --- |
| #229 input and controls | `:presentation:compose` host `PointerArbitrationTest`, `QuickSelectSemanticsTest`, `LocalizedResourceContractTest`; native `EditorScreenLayoutTest`, `OutsideCanvasPointerTest`, `PaletteColorPickerScreenTest`, `FrameScreenTest`, `SelectionTransformScreenTest`; `:app:android` native `AppLanguageUiTest` and `AppLanguageProcessTest` for both locale backings |
| #230 lifecycle | `:app:android` host `AutosaveSchedulerTest`, `UnderlayMemorySchedulerTest`, `EditorOperationWorkerTest`; native `EditorRuntimeLifecycleTest`, `EditorRecoveryOfferTest`; `:presentation:compose` native `PlaybackLifecycleScreenTest`; compare synthetic lifecycle fixtures with the required actual process/recreation cases |
| #230 files and recovery | `:core:project-format` host `ProjectFormatV4CodecGoldenTest`, `ProjectFormatV4CodecRejectionTest`, `ProjectFormatV4AllocationTest`; application `LegacyImportWorkflowTest`, `FrameImportCaptureTest`; persistence `RecoveryRecordEnvelopeV4Test`, `FrameFormatMaximumFixtureTest`; native `AndroidPersistenceFunctionalTest`, `ProjectPickerIntentsAndroidTest`, `DurableMvpJourneyTest` and `InterruptedRecoveryWriteTest` with explicit current fixture identity |
| #232/#233 artifact boundaries | Authored/merged manifest, `data_extraction_rules.xml`, app build/Android convention and locked dependency inputs; existing profile-hash/build-convention checks only when their relevant inputs change. No release signature or runtime backup verdict follows from source inspection alone. |

## Execution order and candidate admission

1. Integrate #228 after semantic review, documentation validation and required CI `quality`.
   #229/#230/#232/#233 may then prepare their independent scopes. #231 and #145 may prepare
   protocols; collection remains gated. #234 waits for all required verdicts.
2. #229 admits missing API37 and input environments; #230 uses those shared identities and
   selects lifecycle/provider gaps. Before any device mutation, reconcile the live device,
   installed app and data with the [preservation protocol](adr/0035-measurement-private-data-preservation.md)
   and applicable [setup](DEVELOPMENT_SETUP.md). Preserve ordinary artwork, recovery, underlay
   records and language settings; use synthetic fixtures and verify restoration. Historical
   session exceptions and the last 75-file equality report are not a new preservation admission.
3. Resolve relevant functional/privacy/release-policy changes, then #233 pins the candidate
   commit/tree, variant, APK/test-APK hashes, signing identity, version, toolchain/dependencies,
   packaged Profile and fixture/harness identities. Each functional result retains its own
   candidate; final release reuse needs explicit equivalence, not a renamed artifact.
4. #145/#231 reconcile protocols, registry, v4 bytes, role artifacts and bounded preservation
   before one coordinated final phase. Neither Issue must close before the other's lane runs.
   Any required Profile generation is explicitly planned once before collection, not triggered
   by this matrix. Historical eighteen-slot recipes are not executable admission for new work.
5. #234 checks the complete ledger, defect state and valid minimum-profile budgets, then follows
   #233's procedure for a tagged checked-main beta artifact. Post-collection changes invalidate
   only affected evidence under QLT-012/015/017. External distribution is a separate accepted
   release action; M5 closure does not open M6 or authorize Play Store production launch.

## Verification of this plan

Changed paths are this matrix and its entry links/status in README, Milestones and Development
Plan. `validateDocumentation` detects broken links and decision/work-package references;
source/evidence review checks support claims, missing requirement ownership and dependency
cycles. Final non-draft required CI runs canonical `check` plus Android assembly, with no duplicate
local full run. No runtime/schema/API/limit/budget/waiver changes, device collection or Profile
generation are part of #228. Rules: ARC-001/004/007 and QLT-011 through QLT-019; waivers: none.

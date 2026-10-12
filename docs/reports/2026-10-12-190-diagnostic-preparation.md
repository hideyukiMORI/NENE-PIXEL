# Issue #190: diagnostic host preparation, 2026-10-12

The temporary observer kit is implemented and host-verified. Issue #190 remains open and PR #193
remains Draft: the Profile mismatch is unresolved, live collection remains deferred, and no new
generation/correction budget is granted. GitHub Issues remain the task-state authority.

## Change and identity

Rules: ARC-001/003/004/007/011, CMD-001/008, KOT-013,
QLT-004/006/011/012/013/015/017/018/019; ADR 0010/0018/0024. Active waivers: none.

- ADR 0010 and [the diagnostic contract](../quality/PROFILE_DIAGNOSTIC_PREPARATION.md) select an
  exact-source patch for a separate lab worktree. The [kit README](../quality/profile-diagnostic/README.md)
  documents the preparation command, event meaning, limits and remaining admission conditions.
- `prepare-profile-diagnostic.ps1` requires the pinned commit/tree, a clean linked worktree in the
  lab, the fixed 17-file allowlist and existing/new categories, safe paths, exact event schema and
  raw pre/post hashes. It marks partial application before mutation and marks success only after
  posthash verification. It performs no build or device operation.
- `generate-baseline-profile.ps1` refuses the marker or any of the five new diagnostic production
  source files before evidence creation/native work, even after an accidental clean commit.
  `validate-profile-diagnostic.ps1` exercises these host contracts with synthetic lab repositories.
- `observer.patch` modifies the existing runtime, persistence flow, scheduler, composition and
  producer; its 17 files include six new files and focused tests. The shipping source tree contains
  no diagnostic observer. There is no copied journey, new module, dependency or permanent API.

Pinned source: `314bcbdacc3b0816e56d5c7e2cd7f8d020c5b511`, tree
`323df6a7364d2097adcd5360aea6519b11bcac20`. This source already contains the full canonical guard.
It follows the main integration `294734095367bacb6a090e088eb3bdaea561e0f2` and first guard/ADR
bootstrap `178a3edbd6fa0cf4602bf19ebe78eaa890788168`.

Patch SHA-256: `94f5dc8d8f4f93087832b0c10f422837ecb90a01592462816a63acf11ee63d4d`.
Manifest SHA-256: `bb7e189835a8edbc2a7ab5cd94521759c1622cd72273df5987bc436efd2aa26c`.
`manifest.json` contains raw pre/post file hashes and LF-normalized source identities. All 17 applied
files match the tested source after line-ending normalization; their raw source hashes also match
the final Kotlin verification record. Packaging and review do not invalidate those test results.

The temporary event schema is `nene-pixel-profile-diagnostic-v1`; the patch/marker schema is
`nene-pixel-profile-diagnostic-patch-v1`. There is no persistent project-format change.
The canonical Profile and its recorded hash are unchanged:
`3f195727a018eb2a64a2609c473e6423dae3635a738e8b976b2d8065024805ce`.

## Behavior and cost

Runtime records bracket the canonical projection assignments with old/new values, equality and a
shared set ID. Scheduler records name the actual samples, fold attempts, committed attempt,
short-circuit/helper branches, deadlines and requests. Publication records bracket the actual port
call and distinguish its typed result from runtime completion or incomplete cancellation/exception.
Producer records surround the existing launch/Pencil/Undo and UI-observation boundaries.

One app recorder shares a lock and sequence, bounded equal-token ordinals and operation/gate IDs.
App limits are 4,096 events and 128 entries per identity registry; producer limit is 512 events.
Overflow/unsupported gates invalidate the stream and stop recording. User document/pixel contents,
token internals/hashes, titles, paths and exception messages are not serialized. Session/iteration
flow from the producer's launch intent to app composition. Elapsed-realtime event timestamps and
the scheduler's existing nano-time domain are explicitly distinguished.

Code-inspection cost statement (QLT-016/019): ordinary product operations gain no observer work,
document scan/copy, per-pixel computation or preview-frame work. Only applying this nonshipping
patch adds bounded identity comparisons and event/string allocation at the observed boundaries.
Instrumentation changes timing; no speedup, ordinary-product performance or Profile acceptance is
claimed. The original three-argument runtime factory and no-op test composition remain callable.

## Narrow verification and reuse

The scope/check plan was fixed in the Issue, ADR/contract and retained Kotlin verification plan
before execution. Commands used normal caches, offline dependencies and `--max-workers=2`.
Logs and final test XML are retained under lab `evidence/190-baseline-profile/preparation-20261012/`.
The `kotlin/` and `tooling/` subdirectories contain full commands, input hashes and iteration results.

| Command/check | Result and retained record |
| --- | --- |
| `pwsh -NoProfile -File docs/quality/validate-profile-diagnostic.ps1` | PASS, 28 cases, native Gradle callback 0; 13.92 s. `tooling/host-validation-record.txt` records the original tool output and hashes, not an invented original shell log. |
| `:app:android:testDebugUnitTest --tests '*ProfileDiagnosticRecorderTest' --tests '*AutosaveScheduleTest' --tests '*AutosaveSchedulerTest' :app:android:compileDebugAndroidTestKotlin` | PASS, 26 tests, 0 failures/errors; 22 s. `kotlin/focused-verification-5.log`, three XML suites. Corrected-API Android test compilation executed in run 4 and was UP-TO-DATE in run 5. |
| `:core:application:compileTestKotlin` | PASS after the runtime-factory compatibility correction; `kotlin/static-verification-1.log`, reused in run 4. This command's other app-formatting task failed, so its overall command is not reported as a pass. |
| App/core detekt and app ktlint | Passing tasks in `kotlin/static-verification-3.log`; the command overall failed only on producer LongMethod. Final app `ktlintCheck detekt` after the last test edit: PASS, 22 s, `kotlin/app-static-final.log`. |
| `:core:application:ktlintCheck` | PASS, 20 s, `kotlin/core-ktlint-final.log`. The previous combined invocation stopped before this aggregate task finished; its partial runner output was not accepted as a pass. |
| `:quality:baseline-profile:ktlintCheck :quality:baseline-profile:detekt :quality:baseline-profile:compileNonMinifiedReleaseKotlin` | PASS, 21 s, `kotlin/producer-verification-2.log`. |
| Exact packaging and public preparation entry | Initial packaging PASS, 6.72 s, `tooling/package-run-second.log`. Final blank-context representation PASS, 3.13 s, `tooling/entry2-run.log`. All 17 source identities match; the clean entry worktree has marker `prepared` and 17 exact posthashes. |
| Independent read-only review | Guard coverage, allowed path set and branch clock-domain label findings were corrected; runtime/port/scheduler boundaries, bounded recording, locking, privacy and producer action order were reviewed. |
| `validateDocumentation --offline --console=plain --max-workers=2` and `git diff --check` | Final documentation/whitespace results are recorded in `documentation.log` and `final-host-checks.json` alongside this report. |

The synthetic 28-case result predates only the literal pin update; it supplies fixture pins and is
reused for unchanged host logic. The real public-entry application validates the new default pin.
No successful Kotlin check was repeated merely for packaging, reviewer change or push.

The final patch uses per-command Git `diff.suppressBlankEmpty=true`: only 30 blank context prefixes
were removed, with zero source changes, so the committed artifact passes the same whitespace gate.
The earlier patch/manifest/marker remain in `tooling/initial-package/`. The manifest is UTF-8/LF as
required by `.gitattributes`; parsed JSON equality and unchanged patch/source/post identities are
recorded in `tooling/manifest-lf-identity.json`. No extra code test or application was invalidated
by JSON newline normalization.

Failures remain in the archive. Corrections included an unsupported PowerShell fixture parameter,
stronger negative assertions, the header test/serialization format, runtime-factory trailing-lambda
binding, formatting/static limits and a reserved `$Args` variable in the packaging helper. The
runtime-factory failure was initially misclassified in an agent update as pre-existing; that claim
is withdrawn. It was caused and fixed by this patch. A producer format/check invocation also exposed
an ordering mistake; the subsequent check followed completed formatting. No unrelated failure or
waiver was used to bypass a gate. Verification wall times above are not measured operation latency.

## Remaining admission and retained work

No APK assembly/install, device connection, Profile generation, restoration attempt, performance
sample or full local gate ran in this slice. The last valid pair still differs by nine signatures
and two flags (17,801 / 17,810 rules); all historical mismatch/invalid evidence is retained.

Logcat has no guaranteed final app event when the collector kills the process. Contiguous observed
sequences cannot establish tail completeness, and an absent publication event cannot prove that
publication did not occur. Live execution still requires an agreed harness, exact APK/observer
identity, finite budget/stop conditions, collector logs and a completeness criterion or a conclusion
limited to positively observed events. The original ProfileInstaller-marker restoration admission
remains unresolved; do not repeat the old restoration or reinterpret retained original guards.

The #190 clone and `worktrees/n190diag` remain because the Issue is unaccepted, unmerged and still
needs its prospective live plan. The tested dirty diagnostic source is also fully represented by
the committed kit. C workspace edits are preserved. #145 candidate pin/collection remains blocked
on #190 acceptance; #163 follows #145 and #168 remains after M4.

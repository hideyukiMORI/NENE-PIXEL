# Issue #190: diagnostic analysis and restoration investigation

This host continuation follows the [observer-kit preparation](2026-10-12-190-diagnostic-preparation.md).
The current task state remains in Issue #190. Collection is still held, the historical mismatch is
unresolved, and PR #193 remains Draft. hide subsequently authorized only the prepared read-only
device metadata observation. No app lifecycle change, APK build/install, restoration, Profile
generation or performance collection was performed.

Rules: ARC-001/003/007, QLT-004/011/012/013/015/017/018/019; ADR 0010/0018/0024/0035.
Active waivers: none.

## Scope and decisions

ADR 0010 and the [diagnostic contract](../quality/PROFILE_DIAGNOSTIC_PREPARATION.md) define one strict
JSONL host analyzer and a prospective bounded diagnostic. The unchanged source kit still pins
`314bcbdacc3b0816e56d5c7e2cd7f8d020c5b511`, tree `323df6a7364d2097adcd5360aea6519b11bcac20`;
patch SHA-256 `94f5dc8d8f4f93087832b0c10f422837ecb90a01592462816a63acf11ee63d4d` and manifest SHA-256
`bb7e189835a8edbc2a7ab5cd94521759c1622cd72273df5987bc436efd2aa26c`.

The selected conclusion is positive observation only. A structurally valid observed prefix cannot
prove that logcat retained the tail, and an absent publication remains unknown. Port entry, typed
port result and runtime completion are separate facts. Recorder origin identifies the recorder's
creation, not OS process start; collector evidence is needed for process termination/restart.
Supplied APK hashes are bindings, not authentication of the log producer. No diagnostic report can
grant Profile acceptance or claim ordinary-product performance or mismatch causality.

The proposed future diagnostic is one invocation of the same producer (at most 15 iterations,
1,800 seconds, zero retry/correction allowance), subject to the existing hold and complete live
admission. This is a prepared plan, not a granted run. The canonical two-invocation acceptance
wrapper continues to refuse the observer. No product, document format, dependency or observer
source changes in this follow-up; ordinary operation and preview costs are unchanged.

## Restoration investigation

Read-only host checks examined the retained v1 preservation at lab
`evidence/190-baseline-profile/operator-issue-190/issue-190-user-recovery-20261008-2241/`.
They read metadata and streamed archive hashes; no private assets were extracted or printed.
Detailed source identities and results are retained separately in the follow-up evidence archive.

The stopped restoration record is still `admission`, with `private_mutations_started=false` and
matching original guard bytes. Its retained snapshot has 88 unchanged protected files, two changed
ProfileInstaller marker files, one new live recovery file and no missing protected file. All three
original PC archives match their respective 90-entry byte inventories. These are historical
snapshot facts and say nothing about the device's current state.

The old Issue-local Python helper refuses this pair of marker changes. Expanding its allowlist
alone would also be incorrect: its restoration body restores only `files/profileInstalled`, not
`files/profileinstaller_profileWrittenFor_lastUpdateTime.dat`. The latter would remain absent after
archival and fail final identity. The old helper and evidence are preserved unchanged.

The canonical v2 tools already preserve both marker files prospectively, but they require verified
v2 snapshot/preservation/APK/source bindings that the old v1 record does not have. ADR 0035 forbids
falling back to an Issue-local helper. No v1 record has been converted, relabelled or admitted as v2.

The old manifest records size and hash only. Its original tar records integer-second mtimes and
contains no fractional PAX mtime for the four underlay files. These artifacts cannot prove original
nanosecond mtime. A fresh inventory can establish current nanoseconds; it cannot reconstruct missing
original metadata. Exact v2 restoration is not claimed. For the two retained underlay state files,
the original tar seconds are distinct (`1790998943` and `1791001586`) and each matches a2's current
seconds. Their relative LRU order is therefore preserved regardless of the missing fractional
values: the implementation orders pairs by state-file mtime. This does not prove exact timestamps.

The bounded read-only observation used the canonical native observer after hide expressly lifted
the hold for this action alone. It required an already stopped app and the expected installed APK,
without force-stop, launch, install, write or restoration. A fresh inventory is now available for
the specific legacy-session termination plan. Any later transition must first be recorded under ADR
0035, reuse the canonical policy and native steps, archive current replacements before returning
originals to vacant destinations, retain every original/measurement copy and stop on uncertainty.
No such restoration executor or policy relaxation is introduced by this investigation.

The first attempt (`legacy-observation-20261012-a1`) failed before private enumeration: ADB exited
zero in 5,175 ms but printed its daemon cold-start messages to stderr, which the canonical helper
refused. The enclosing nested PowerShell call did not finish after its child failed; its owned
wrapper was terminated about 132 seconds after process creation. The 120-second native-work budget
did not bound that outer wait. No later device command ran, and the shared ADB server was retained.
This environmental/invocation issue is recorded separately as #199, not hidden by a passing rerun.

A new prospective identity (`legacy-observation-20261012-a2`) required the now-running ADB server
and listening port before admission, used a direct PowerShell invocation instead of a nested shell,
and allowed zero automatic retries. The corrected observation passed in 5.129 seconds, yielding
151 entries / 91 regular files and the expected installed APK SHA-256
`cb9df0902c74e35bd10dc140b45e16def7d0348af640bb58be2f8a003a2a5219`.
Both attempts and their exact operator source/command records remain in lab evidence. PID absence
at the observation boundaries and matching path coverage are not an atomic snapshot or proof that
no process briefly ran between reads. This observation alone admits neither restoration nor a run.

The a2 inventory contains all 90 original file paths: 87 original files retain their bytes,
the two ProfileInstaller markers differ, and the measurement-created live recovery differs.
The isolated protected set has 88 matching files (including the intact original recovery in its
guard) and exactly those two changed markers. The guard copy is the sole additional file relative
to the original path set; no original file is missing. All 60 directories match the isolated
archive. The other 87 files' current mtime seconds match the original tar, but their original
fractional nanoseconds were never recorded. The full metadata comparison is retained as
`restoration/current-inventory-comparison.json` and `.md` in the follow-up archive.

A byte-exact legacy completion could be designed with both markers restored and the current live
recovery separately preserved. It cannot establish exact original nanosecond metadata from these
artifacts. Such a change needs an explicit accepted legacy policy and hide's decision on that
limitation before live restoration; current authorization remains read-only.

## Verification and reuse

The Issue and contract fixed the changed scope and applicable checks before implementation.
The analyzer command was
`pwsh -NoProfile -File docs/quality/validate-profile-diagnostic-analysis.ps1 -FixtureDirectory D:/NENE-PIXEL/outputs/190-diagnostic-followup-20261012/analyzer/fixture-lab`.
It passed 31 synthetic cases. The final record is
`analyzer/fixture-lab/d61a59ffbadf4ab2a0dca4185b5047d8/validation-record.json` in the follow-up archive,
with each input's hash and byte count. Analyzer SHA-256:
`4ca0a4f30fee1f33b29965ab7989f0c6f950c75b14ae178245e7e314c03ec445`; validator SHA-256:
`afc59a5cd89c90149c3b5c7b43b5d985a061b9a5a87041bff73cd4f47fbc8d4c`.

Cases cover valid open/completed and interleaved boundaries, fold retries, distinct port/runtime
results, sequence/schema/type/enum/identity/pair contradictions, private-text rejection, UTF-8,
line/ordinal/event bounds and occupied output preservation. Independent read-only review of these
exact script hashes found no remaining blocker. Early findings on event sorting, enum overlap,
pairing and clock fields were corrected before this final result. Suggestions to make gate labels
uniquely identify an ordinal or make autosave equality depend only on serialized labels were
retracted: omitted payloads can distinguish the original values.

Documentation uses `validateDocumentation` and `git diff --check`; their final result is recorded
in the follow-up archive. Prior failed/incomplete fixture runs are retained. No successful result
was repeated solely for review, packaging or a new report.

The previous 28 preparation cases, 26 focused JVM tests, affected compilation/static checks and
17-file applied-source identity are reused from the preparation report: their implementation,
observer bytes and dependencies are unchanged. No stage, report, reviewer or commit transition
invalidates them. There is no reason to run a full local suite or repeat a device measurement.

Remaining risks are observer perturbation, unsealed event tails, unbound future APK identity,
the observation's non-atomic scope, the interrupted legacy session and irrecoverable metadata precision
in the supplied v1 evidence. Historical mismatch/invalid results and canonical Profile SHA-256
`3f195727a018eb2a64a2609c473e6423dae3635a738e8b976b2d8065024805ce` remain unchanged.

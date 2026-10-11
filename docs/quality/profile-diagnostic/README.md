# Temporary Profile observer kit

Issue #190 host preparation only. Read the
[diagnostic contract](../PROFILE_DIAGNOSTIC_PREPARATION.md) before using this kit.
The source patch is an intrusive observer of the existing implementation. It is not a new accepted
Profile, a shipping feature, or a live collection runner.

## Prepare an isolated source tree

Run from the repository containing this kit. The lab must already be configured as described in
[Development Setup](../../DEVELOPMENT_SETUP.md). Choose an unused worktree name inside that lab.

```powershell
$toolRepository = (git rev-parse --show-toplevel).Trim()
. (Join-Path $toolRepository 'docs/quality/measurements/nene-pixel-lab.ps1')
$lab = Get-NenePixelLabRoot -StartDirectory $toolRepository
$manifest = Get-Content -Raw (Join-Path $toolRepository 'docs/quality/profile-diagnostic/manifest.json') |
    ConvertFrom-Json
$destination = Join-Path $lab 'worktrees/n190obs'
git -C $toolRepository worktree add --detach $destination $manifest.source_revision
if ($LASTEXITCODE -ne 0) { throw 'Worktree creation failed.' }
& (Join-Path $toolRepository 'docs/quality/prepare-profile-diagnostic.ps1') -RepositoryRoot $destination
```

The preparation command pins both source commit and tree independently of the manifest. It admits
exactly the listed source/test files, their existing/new categories, and raw input/output hashes.
The pin already contains the canonical-generation refusal, including all five new diagnostic
production files. It is not necessary to apply a guard after instrumentation. The kit intentionally
refuses a different checkout representation or source revision; do not edit hashes to bypass that
refusal. Git line-ending conversion during packaging is recorded separately from source equivalence.

Success returns `.nene-pixel-profile-diagnostic.json` with status `prepared`. A failure after mutation
leaves status `invalid-partial`; retain that tree for inspection. Do not treat it as usable, remove
the marker to retry, or commit the patch into a shipping branch. Reapplication is refused. The
command does not build, install, collect, restore data, or delete worktrees.

## Event contract

`manifest.json` identifies the patch and exact source bytes. Both process roles emit JSON with
schema `nene-pixel-profile-diagnostic-v1` to log tag `NeneProfileDiag`.

| Field or boundary | Meaning |
| --- | --- |
| `session`, `iteration` | One supplied session; producer iteration 0 is process initialization, workload iterations are 1 through 15 |
| `role`, `pid`, `origin_ns` | Recorder stream identity; correlate these together, never PID alone. Origin is recorder creation, not OS process start |
| `seq`, `t_ns` | Contiguous recorder sequence and Android elapsed-realtime nanoseconds at recording |
| `runtime_*_set` | Old/new projections and equality, with a paired `set_id` and `intent`/`complete` stages around the canonical assignment |
| `scheduler_sample` | The combined sample actually delivered to the scheduler |
| `fold_branch`, `fold_committed` | Attempt-specific short-circuit/helper observations and the attempt committed by the existing update |
| `scheduler_decision` | Actual decision clock reading, due result, deadlines and suspension state |
| `publication_*` | Actual port entry, typed outcome, runtime completion or incomplete operation |
| Producer input/UI events | Recording around existing launch/Pencil/Undo/Dirty/Clean/UI-block boundaries, not display presentation or process termination |

App recording is bounded to 4,096 events, 128 equal-token ordinals, 128 operation ordinals and 128
safe gate projections. The producer is bounded to 512 events. A limit emits `diagnostic_invalid`
and stops recording; mappings are not evicted. Gate phases/statuses/outcomes are fixed labels.
Unsupported source-bearing gates invalidate the stream before the recorder retains them. No token
contents, hashes, document pixels, user titles, paths or exception messages are serialized.

Scheduler deadlines and `fold_now_ns`/`decision_now_ns` use its existing `System.nanoTime` clock,
labelled `deadline_clock=scheduler_nano_time`; they must not be directly subtracted from `t_ns`.
The logging calls do not define a new state linearization point. A concurrent scheduler observation
can occur between a runtime assignment and its `complete` event.

The producer supplies `nene_profile_diag_session` and `nene_profile_diag_iteration` launch metadata.
The instrumented activity requires this metadata. Direct manual launch is not a normal-app test.
Host test composition can explicitly use the default no-op observer to preserve existing callers.

## Analyze retained event payloads

The host analyzer accepts one strict UTF-8 JSON object per line, containing only the diagnostic tag's
payloads for one session. Retain original collector/logcat output separately; do not drop malformed
diagnostic records, repair order, sort timestamps or deduplicate the input. This command does not
capture logs or contact a device:

```powershell
$session = 'diag-190-example'
$evidence = "D:/NENE-PIXEL/evidence/190-baseline-profile/$session"
pwsh -NoProfile -File docs/quality/analyze-profile-diagnostic.ps1 `
    -InputPath (Join-Path $evidence 'diagnostic.jsonl') `
    -Session $session `
    -OutputPath (Join-Path $evidence 'analysis.json')
```

Use an unused output path. The input, patch, manifest and source identities are recorded in the
report. Optional `-AppApkSha256` and `-TestApkSha256` bind supplied artifact hashes; omitted hashes
remain unbound, and supplying them does not authenticate the logs. The report never grants Profile
acceptance. A valid observed prefix can prove the named positive event occurred; its unobserved
tail and absent publication remain unknown. See the [analysis contract](../PROFILE_DIAGNOSTIC_PREPARATION.md)
for exact meaning, limits, correlation and the prospective finite diagnostic plan.

## Host checks and live admission

`pwsh -NoProfile -File docs/quality/validate-profile-diagnostic.ps1` checks preparation and canonical
generation refusal with synthetic repositories inside the lab. Its native-work callback must stay
at zero for every rejection. Focused Kotlin commands, source identities and outcomes are recorded in
the preparation report; reuse them only when affected source/dependency inputs are equivalent.

`pwsh -NoProfile -File docs/quality/validate-profile-diagnostic-analysis.ps1 -FixtureDirectory D:/NENE-PIXEL/outputs/190-diagnostic-analysis`
exercises the analyzer with synthetic events only. It checks valid prefixes and rejects schema, identity, sequence,
pairing and bound violations without exposing arbitrary input text. It does not rerun the Kotlin
or preparation tests, whose unchanged implementations retain their recorded results.

No command for live collection is provided. Before one is added, Issue #190, ADR 0010 and the harness
must agree on artifact identity, budget, stop rules, collector logs, stream completeness and device
restoration admission. Logcat has no guaranteed final app event when the collector kills the app.
Missing events cannot prove missing publication; even contiguous observed sequences cannot prove
that the tail is complete. Historical mismatch/invalid evidence and the canonical Profile stay intact.

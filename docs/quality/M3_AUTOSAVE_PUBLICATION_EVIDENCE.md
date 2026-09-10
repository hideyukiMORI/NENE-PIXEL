# M3 Autosave Publication Evidence

This document is the versioned protocol for the single bounded device observation that
[ADR 0018](../adr/0018-bounded-autosave-debounce-contract.md) requires before Candidate publication
code merges for Issue #87 (P3-04). It follows `QLT-011`, `QLT-013`, `QLT-014`, and `QLT-015`. It
defines no product latency target, produces no PASS or FAIL verdict, and supports no comparative
speedup claim. Its only decision use is the constant re-derivation rule in ADR 0018.

## Scope and verification trigger

P3-04 adds Candidate publication on top of the P3-03 record, writer, and reader. The autosave
constants in ADR 0018 bound the frequency of that publication, so the cost of one complete
publication on the reference device is the quantity that justifies them. Encode and decode costs
on the host JVM are already recorded in
[M3 Persistence Adapter Evidence](M3_PERSISTENCE_ADAPTER_EVIDENCE.md); this lane measures the
device part that the host lane deliberately excluded.

## Prospective device publication protocol

Schema identity: `nene-pixel-p3-autosave-publication-device-v1`.

### Purpose and hypothesis

Hypothesis: one complete Candidate publication of the largest v1 document on the reference device,
through the real `android.util.AtomicFile` adapter, completes in well under the 1,000 ms quiet
window of ADR 0018, so the quiet window and the 5,000 ms latency cap are at least four and twenty
times the maximum observed cost respectively. If the observed maximum exceeds 250 ms, ADR 0018's
derivation rule recomputes the constants; that is the only decision this observation feeds.

### Artifact and runner

One instrumentation runner class in the `:adapters:persistence` `androidTest` source set,
`AutosavePublicationDeviceEvidence`, executed on the reference device by
`connectedDebugAndroidTest` with the instrumentation runner argument
`class=io.github.hideyukimori.nenepixel.adapters.persistence.AutosavePublicationDeviceEvidence`
and the runner argument `nene.p3.autosaveEvidence=collect`. Without that argument the class is
skipped, so routine focused test runs and CI (which has no device lane) never collect. No Gradle
task, configuration, dependency, plugin, lock, or verification-metadata change is introduced.

The runner uses the production adapter path only: `RecoveryRecordCodec.encodeCandidate`, the
ordered record writer with `startWrite`, explicit `sync`, `finishWrite`, and bounded `openRead`
read-back verification, on an `AtomicFile` under the instrumentation target's `noBackupFilesDir`
in a directory created for the run and deleted afterwards. It never touches the app's real recovery
record path.

Before collection, record the exact Git revision, production and androidTest tree hashes, device
serial, model, Android version, build fingerprint, and the exact invocation. Gradle and
instrumentation start-up wall time is recorded separately from publication latency.

### Metric, population, and cheap facts

Metric: elapsed `System.nanoTime()` from immediately before `encodeCandidate` to immediately after
the read-back verification returns an accepted outcome, for one publication. Population: twenty
measured publications per group after five unreported warmups, in fixed order. Cheap facts checked
inside the timed region are the outcome type and the generation; no pixel comparison, hash, forced
GC, or memory probe runs between samples (`QLT-014`). After each group, one untimed full decode of
the final record verifies the payload bytes.

Reported statistics: minimum and maximum per group, plus every raw sample. No percentile is used
for a decision; the derivation rule reads the maximum of the maximum-document group.

### Workloads and order

| Order | Group | Document | Generation |
| --- | --- | --- | --- |
| 1 | `candidate_publish_max` | 256 × 256 pixels, deterministic pseudo-random opaque RGBA (seed 87), revision 1 | increments from 1 per publication |
| 2 | `candidate_publish_min` | 1 × 1 pixel, one opaque colour, revision 1 | continues incrementing |

Both groups publish into the same `AtomicFile`, so each sample overwrites the previous record the
way autosave does in production.

### Budget and stopping conditions

The budget is one invocation, twenty samples times two groups, with no retry, replacement,
selective drop, or favourable rerun. A five-second per-sample post-operation anomaly check and a
sixty-second outer instrumentation timeout bound the run. Any exception, non-accepted outcome,
wrong cheap fact, sample over five seconds, timeout, missing row, or order mismatch stops collection
and makes the observation invalid. Completed rows remain evidence, and a new attempt requires an
explicit protocol revision with a new schema identity.

Collection additionally requires that Issue #87, this document, and the runner agree before the
single run, and that hide explicitly authorises the run. Historical permissions do not authorise it.

### Output

The runner logs one CSV block to logcat with tag `nene-p3-autosave-evidence` and writes the same
bytes to the instrumentation target's files directory as
`m3-autosave-publication-device-v1.csv`; the file is pulled with `adb` and committed at
`docs/quality/measurements/m3-autosave-publication-device-v1.csv`. Columns:
`schema,group,index,kind,elapsed_ns,generation,outcome` with `kind` in `warmup`, `sample`,
`summary_min`, `summary_max`.

## Planned narrow verification

Correctness of Candidate publication is fixed by adapter unit tests on the host seam and by the
existing `AndroidPersistenceFunctionalTest` extended with one real Candidate publish and read-back;
those run independently of this lane and do not time anything.

## Result

Not collected. This section is completed by the single authorised run.

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

Schema identity: `nene-pixel-p3-autosave-publication-device-v2`.

The 2026-09-11 correction supersedes the uncollected v1 protocol. V1's unrequested runner
invocation was recorded by Android test tooling as an assumption failure; it is retained as
historical evidence and is not a latency population. V2 fixes failure/timeout row retention and
states the real production read-back work explicitly. Workloads, sample budget, and the 250 ms
constant re-derivation boundary are unchanged.

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
and the runner argument `nene.p3.autosaveEvidence=collect`. Without that argument the runner
rejects the invocation before creating an observation or output file. Routine focused functional
invocations name their functional classes explicitly and do not invoke this measurement class;
CI has no device lane. A rejected invocation is never described as a successful skip or evidence.
For this invocation, set
`-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` so Gradle's post-test uninstall
does not destroy the private CSV/status before they are pulled. The 2026-09-12 preflight verified
this stable BooleanOption and its default `false` in the installed AGP 9.4.0 artifact, and verified
that the device-test task passes it to the keep-installed setting. This fixes the concrete command
for the existing output-retention requirement before the first v2 sample; metric, population,
budget, schema, and measured code remain unchanged. No Gradle task, build-script setting,
dependency, plugin, lock, or verification-metadata change is introduced.

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
measured publications per group after five warmups excluded from summary statistics, in fixed order.
The measured production operation includes exact byte read-back, complete envelope/nested-v1
validation, and the same Candidate/document equality predicate as the production writer. These
are required operation semantics, not additional correctness probes. The runner checks the
returned outcome and generation after timing. It adds no document scan, hash, forced GC, or memory
probe between samples (`QLT-014`). After each group, one untimed full decode of the final record
verifies the payload bytes. Row recording uses a bounded in-memory journal; CSV/log I/O occurs only
when the invocation completes or fails, not between timed operations.

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
sixty-second outer JUnit test timeout bound the run. A test-owned outer reporting rule surrounds the
timeout rule, freezes a synchronized bounded row journal, and emits its completed rows on success,
exception, or timeout. Timeout interrupts the worker and makes the observation invalid; recording
is closed and no follow-on publication may start. An already executing platform I/O call may finish
while interruption drains; its late result cannot enter the frozen report or authorize another run.
Any exception, non-accepted outcome,
wrong cheap fact, sample over five seconds, timeout, missing row, or order mismatch stops collection
and makes the observation invalid. Completed rows remain evidence, and a new attempt requires an
explicit protocol revision with a new schema identity.

Collection additionally requires that Issue #87, this document, and the runner agree before the
single run, and that hide explicitly authorises the run. Historical permissions do not authorise it.

### Output

The runner logs one CSV block to logcat with tag `nene-p3-autosave-evidence` and writes the same
bytes to the instrumentation target's files directory as
`m3-autosave-publication-device-v2.csv`; a companion `.status` file records `complete` or `invalid`.
Existing output files are never overwritten. The CSV and status are pulled with `adb`; the raw CSV
is committed at `docs/quality/measurements/m3-autosave-publication-device-v2.csv`, including partial
rows for an invalid observation. A missing status, `invalid` status, truncated output, or failure to
write the report cannot produce an accepted observation. Columns:
`schema,group,index,kind,elapsed_ns,generation,outcome` with `kind` in `warmup`, `sample`,
`summary_min`, `summary_max`.

## Planned narrow verification

Correctness of Candidate publication is fixed by adapter unit tests on the host seam and by the
existing `AndroidPersistenceFunctionalTest` extended with one real Candidate publish and read-back;
those run independently of this lane and do not time anything.

Runner functional contracts use synthetic rows and an injected failing or interrupted statement.
They prove prefix retention after failure and timeout, unchanged successful row order, closed
recording after completion, preservation of the primary failure, and rejection without the explicit
collection argument. They never publish real Candidate samples and never fill this Result section.

## Result

Collected once on 2026-09-12 after hide's explicit authorization in the current session and its
record in Issue #87. No prior v2 observation existed and no retry was performed. The raw
[CSV](measurements/m3-autosave-publication-device-v2.csv) and
[status](measurements/m3-autosave-publication-device-v2.status) are retained unchanged.

### Fixed artifact and device

- Measured source commit: `45828df198837f3aaf8fd712b31c85cd9540282c`.
- Adapter production tree (`adapters/persistence/src/main`):
  `d63af353c2ea307458824f0137c4c3d941b5e5a5`.
- Adapter androidTest tree: `3f896ea4addb6cf52f1cc7e178253c3f37127429`.
- Debug androidTest APK: 7,932,794 bytes, SHA-256
  `a37876a107121f62dc57261f9a377158ccf668979d46c226711ba33b26563e91`.
- Package/target: `io.github.hideyukimori.nenepixel.adapters.persistence.test`.
- Device: iPlay80miniPro, serial `T830128GB26321131293`, Android 16 / SDK 36,
  security patch 2026-08-05, fingerprint
  `ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94111:user/release-keys`.
- Preflight: one physical device, awake, USB power, charging at 59%, battery 27.7 C,
  power saving off, overall thermal status 1. Test package and prior v2 outputs were absent.
- Toolchain: JDK 21, Gradle 9.7.1, Kotlin 2.4.20, AGP 9.4.0. Dependencies and profiles unchanged.

The source/dependency subtree identities in
[Correction Evidence](M3_AUTOSAVE_CORRECTION_EVIDENCE.md#final-source-identity) also match this
artifact. The pre-collection output-retention clarification changed only this document and the
invocation option; it changed no measured source. Later evidence/documentation commits do not
relabel this APK as newly built.

### Exact invocation and preservation

```powershell
$env:ANDROID_SERIAL='T830128GB26321131293'
.\gradlew :adapters:persistence:connectedDebugAndroidTest --offline `
  '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' `
  '-Pandroid.testInstrumentationRunnerArguments.class=io.github.hideyukimori.nenepixel.adapters.persistence.AutosavePublicationDeviceEvidence' `
  '-Pandroid.testInstrumentationRunnerArguments.nene.p3.autosaveEvidence=collect'
```

The separately prepared `assembleDebugAndroidTest --offline` took 10.400 seconds. The one device
invocation started at 00:20:51.609 JST and ended at 00:21:11.161 JST, with Gradle wall time
19.451 seconds. Instrumentation executed one test in 4.055 seconds, with zero failures/errors/skips
and XML timestamp `2026-09-11T15:21:02` UTC. These setup/suite times are not publication latency.
All packaging tasks were up-to-date during collection; only the connected-test task executed.

Private CSV/status, XML, logcat, Gradle output, the actual APK, preflight, commands, and source/hash
manifests are preserved locally under `build/reports/issue-87-publication-v2/20260912-001/`.
The CSV SHA-256 is `aa8919cf60a04f834539e0b2426ee9b7d8e9b5c0d615768795a379b7141d938d`;
the status SHA-256 is `eebbf6457e46a7f63acdf9b97390f790ba443d60cfa44b607da7e5c40aa1cc1d`.
The preserved XML SHA-256 is
`41190710f935ff7aba7e5c50e3deeb35799e24df6c37f889dced4b1aeb1c3c08`.
Device and host CSV/status hashes match. The 35-file archive manifest was rechecked with zero
mismatches; its SHA-256 is `16d18908e2c1bdcd00534c886712df26f912ee45a73b8d715adf75389baca2ba`.

### Observation and decision

The status is `complete`. Independent host validation confirmed all 54 rows plus the header:
two ordered groups, five warmups and twenty samples each, consecutive generations, exact indices,
`written` outcomes, and each min/max summary matching its original measured row. No row exceeded
the five-second anomaly bound. Warmups remain in the raw CSV and are excluded from these statistics.

| Group | Measured samples | Minimum (ms) | Maximum (ms) |
| --- | --- | --- | --- |
| Maximum 256 x 256 | 20 | 135.757769 | 147.071808 |
| Minimum 1 x 1 | 20 | 1.188500 | 1.430077 |

The maximum-document observed maximum, 147.071808 ms, is below the predeclared 250 ms re-derivation
boundary. ADR 0018 therefore retains the 1,000 ms quiet window and 5,000 ms continuous-edit cap.
This is a valid descriptive observation used for that one decision, with no performance PASS/FAIL
verdict, percentile target, or speedup claim. One device and one bounded population do not guarantee
worst-case storage latency or recovery completion before Android kills/suspends the process.

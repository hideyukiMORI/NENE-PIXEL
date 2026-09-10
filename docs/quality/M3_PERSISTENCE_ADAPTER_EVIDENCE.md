# M3 Persistence Adapter Evidence

Status: complete for P3-03 / Issue #86

## Scope and verification trigger

Issue #86 creates `:adapters:persistence`, the Android save/load adapter, the private recovery record
v1 envelope, its bounded reader, and the one serialized conditional Retired writer. The private
envelope is a new storage representation, so QLT-011 requires boundary, round-trip, corruption, and
the smallest affected latency or retained-memory evidence for that representation. ADR 0016 records
the same obligation and requires a versioned protocol to fix one bounded descriptive host lane before
any new collection.

Correctness uses the committed host tests for the codec, bounded reader, storage adapter, and
recovery record adapter: exact byte layout, generation validation, state-specific length, CRC,
nested v1 acceptance, corruption and unsupported-version rejection, writer order, pre-finish rollback,
post-finish uncertainty, cancellation symmetry, and read-back comparison. The host observation below
is a separate descriptive latency lane and cannot replace those tests.

No Android device behavior, provider I/O, lifecycle, rendering, command, history, or UI behavior is
measured here, so device work, profile generation, drawing measurement, and a local full suite are
not triggered. The retained-memory alternative is not taken: the bounded reader's maximum-plus-one
probe and known-length rejection are already fixed as contract tests, so a separate retained-memory
number would add no reviewable information.

## Prospective host latency protocol

### Purpose and hypothesis

The observation checks that recovery envelope v1 encode, decode, and the host part of one conditional
Retired publication add only a bounded constant cost over the nested project-format v1 codec already
described for P3-02, without an obvious runaway implementation cost. It defines no Android,
`AtomicFile`, SAF, provider, UI, or product latency target, produces no PASS or FAIL verdict, and
supports no comparative speedup claim.

### Artifact and runner

One internal main runner is compiled in the `:adapters:persistence` unit-test artifact at
`adapters/persistence/src/test/kotlin/io/github/hideyukimori/nenepixel/adapters/persistence/RecoveryRecordHostEvidence.kt`.
It is not annotated as a test, and no repository Gradle task connects it to routine `check`. The
committed init script
[`measurements/issue86-host-evidence.init.gradle.kts`](measurements/issue86-host-evidence.init.gradle.kts)
adds one private `JavaExec` task `issue86HostEvidence` for a single reviewed invocation. That task
borrows the existing `testDebugUnitTest` runtime classpath and test classes directories, so it creates
no new Gradle configuration, adds no dependency, plugin, or permanent task, and changes no dependency
lock or verification metadata. Unlike the P3-02 collection, the init script is versioned in this
repository so the artifact identity is reproducible without an external file.

The runner never touches `android.util.AtomicFile` or any other `android.*` API. It injects a minimal
in-memory implementation of the internal `RecoveryAtomicFileAccess` seam, which is the same host
boundary used by the committed adapter tests. Host Android I/O is deliberately not measured.

Before collection, record the exact Git revision, production/test tree hashes, runner and init script
hashes, JDK/JVM/OS identity, and the exact invocation. Gradle compilation and configuration wall time
is recorded separately from recovery record operation latency.

### Workloads and order

One invocation runs these five groups in fixed order:

1. `retired_encode` — encode the Retired record at recovery generation one, producing exactly 23
   bytes;
2. `retired_decode` — decode that already-created 23-byte Retired record;
3. `candidate_encode` — encode the Candidate record at recovery generation one carrying one generated
   256 by 256 maximum document, producing exactly 262,209 bytes;
4. `candidate_decode` — decode that already-created maximum Candidate record, including the outer
   envelope validation and the nested project-format v1 decode; and
5. `retirement_publish` — one complete conditional retirement round trip through
   `AndroidRecoveryRecordAdapter` over the in-memory seam, from a stored Candidate at generation one
   to a verified Retired record at generation two, including encode, start-write, write, sync, finish,
   bounded read-back, exact byte comparison, and decode verification.

The maximum document uses deterministic row-major exact pixels from the committed adapter test
fixture. Both encode groups include the codec's own post-encode self-verification decode, because
that decode is part of the canonical production encode path and is not separable from it.

Before timing, the encoded Retired and Candidate byte counts, their exact re-encoded bytes, their
decoded record identity, and one complete retirement round trip returning the verified Retired record
at generation two are all checked. These full checks occur before and after all timed samples, never
between them. For group five only, the in-memory record is reset to the stored Candidate at
generation one before each warmup and each sample; that reset is outside the timed region.

Each group runs five unreported warmups followed by exactly 20 measured operations. The timer uses
`System.nanoTime()` immediately around the one measured operation. Metadata is emitted and flushed
before sampling. Immediately after each timer ends, the raw sample row is emitted and flushed before
verification or the anomaly check, so a failing sample and all earlier completed rows remain evidence.
This output occurs between samples; full byte equality, document equality, CRC recomputation, pixel
scans, hashing, and other expensive checks remain prohibited there. After the raw row, each sample
checks only a cheap fact: the encoded byte count, the decoded record kind and generation value, or the
returned retirement outcome. The final report publishes the minimum and maximum for each complete
group; it computes no percentile or acceptance threshold.

### Budget and stopping conditions

The budget is one invocation, 20 samples times five groups, with no retry, replacement, selective
drop, or favorable rerun. An outer task timeout of 60 seconds bounds the complete runner and excludes
Gradle compilation and configuration time. The runner also checks each completed sample against one
second after timing; this is a post-operation anomaly check, not a preemptive hard timeout.

Any exception, non-accepted result, wrong cheap fact, sample over one second, outer timeout, missing
row, or order mismatch stops collection and makes the observation invalid. Completed rows remain
evidence, and a new attempt requires an explicit protocol revision and review. Numeric values below
one second remain descriptive; they do not produce a performance PASS verdict.

Collection additionally requires that Issue #86, this document, the runner, and the init script agree
before the single run, and that the run is explicitly authorized. QLT-015 makes that agreement and the
recorded budget part of the evidence rather than a later justification.

## Planned narrow verification

- filtered persistence adapter, codec, and bounded reader tests during iteration;
- `:adapters:persistence:check` for module compile, test, ktlint, and detekt;
- `:adapters:persistence:compileDebugAndroidTestKotlin` for the instrumentation source set compile;
- root `validateArchitecture` for the ARC-002 module graph and `validateDocumentation` for this
  document; and
- required final-candidate `quality` CI instead of a duplicate local full suite.

Normal Gradle cache and daemon defaults apply. Forced clean, cache bypass, device tests, profile
generation, and unrelated domain/application suites are excluded.

## Result

The one permitted invocation completed on 2026-09-10 JST. All 100 raw rows appeared in the
predeclared group order with sample indexes zero through 19, followed by all five summaries and no
trailing rows. Every summary equals the minimum and maximum of its own committed raw rows. The runner
stderr file is empty, the runner completed normally inside its 60-second task timeout, and every
sample passed its one-second post-operation anomaly check with a widest sample of 5,926,200
nanoseconds. No retry, replacement, or favorable rerun invocation was made.

The exact invocation was:

```text
./gradlew -I docs/quality/measurements/issue86-host-evidence.init.gradle.kts \
    :adapters:persistence:issue86HostEvidence --offline --no-configuration-cache
```

`--no-configuration-cache` is a QLT-012 exceptional flag recorded in advance rather than added after a
failure: the init script's `JavaExec` task redirects the runner's standard and error streams from its
task actions, and that configuration cache compatibility could only be checked with `--dry-run` before
the one permitted run, so the flag kept a configuration cache mismatch from invalidating a collection
this protocol allows exactly once. The exception was scoped to the single
`:adapters:persistence:issue86HostEvidence` task in that single invocation. It is not a routine
iteration flag and does not apply to the narrow verification lane above, which keeps the committed
cache and daemon defaults.

The observation used Windows 11 10.0 amd64 (build 10.0.26200.9278), OpenJDK 64-Bit Server VM
21.0.11, and Gradle 9.7.1. Its artifact identity was base Git revision
`3a4ea6519b99e74cad8f20a577156ea2a684414d` on branch `feat/86-android-project-save-load`, with the
adapter module still an uncommitted working-tree addition on that revision, production-tree SHA-256
`9c4445ade375ab42e5d4dd478710d92399e0215a8eb9ce7d2ddb9cd08e0fc199` over the 19 files of
`adapters/persistence/src/main`, and test-tree SHA-256
`060f07eeda888657bf3d6160270de3203e3ca54d8cb33e37ab25e5500c7c870b` over the six files of
`adapters/persistence/src/test`. Each tree hash is a deterministic aggregate that sorts the contained
files by repository-relative slash-separated path and feeds each path, one newline, and then the exact
Windows checkout file bytes into one SHA-256; it is a source identity rather than a Git object ID. The
runner file SHA-256 was
`1069bcbf4c1e23654a9ace9a6c60a87031557cba5907768567483a9beb95f9f9` and the committed init script
SHA-256 was `1bc1f3b8f418135a6306a114363d45e255c838d584879f1dc163ec0a16ee5aab`.

The separately recorded Gradle preparation wall time was 10,913 ms, measured as one `--dry-run` of the
same invocation immediately before collection, and the complete Gradle invocation containing the timed
Java process took 10,576 ms. These values include Gradle work and are not recovery record operation
latency.

| Group | Minimum ns | Maximum ns |
| --- | ---: | ---: |
| retired_encode | 6,800 | 31,099 |
| retired_decode | 2,799 | 23,000 |
| candidate_encode | 3,475,899 | 5,926,200 |
| candidate_decode | 1,880,100 | 2,467,800 |
| retirement_publish | 2,032,000 | 3,817,900 |

The exact metadata, 100 raw rows, and five summaries are committed in
[`measurements/m3-recovery-record-host-latency-v1.csv`](measurements/m3-recovery-record-host-latency-v1.csv).
The collected CRLF raw output is 3,093 bytes with SHA-256
`95c95f3bf7854715c22573db94c653700dfbcdbae4d99ad1b146ce3011ff6390`. Git's required LF normalization
retains the same 112 lines and sample values in a 2,981-byte committed blob with SHA-256
`470e8d772033c9184af7ff559a5d0c8ad907046e7aa959f7e01d4bb1769c12e2`. These host-JVM minima and maxima
are descriptive observations only. They establish no Android, `AtomicFile`, SAF, or product
performance PASS and support no speedup claim.

# M3 Project Format Codec Evidence

Status: complete for P3-02 / Issue #85

## Scope and verification trigger

Issue #85 creates `:core:project-format`, its public bounded-byte and codec contract, exact v1
mapping, fixtures, tests, and Gradle graph integration. It changes a representation and storage
format implementation, so QLT-011 requires boundary, round-trip, corruption, and the smallest
affected latency or retained-memory evidence. No Android, I/O, lifecycle, rendering, command,
history, or UI behavior changes, so device work, profile generation, drawing measurement, and a
local full suite are not triggered.

Correctness uses the committed golden, deterministic, round-trip, rejection, ownership,
allocation-order, maximum-boundary, and CRC tests. The host observation below is a separate
descriptive latency lane and cannot replace those tests.

## Prospective host latency protocol

### Purpose and hypothesis

The observation checks that the deliberately simple portable loops and immutable `PixelColor` list
mapping complete bounded minimal and maximum v1 work without an obvious runaway implementation
cost. It defines no Android, provider, UI, or product latency target and supports no comparative
speedup claim.

### Artifact and runner

One internal main runner is compiled in the `:core:project-format` test artifact. It is not annotated
as a test and no repository Gradle task connects it to routine `check`. A temporary external Gradle
init script may add one private `JavaExec` invocation using the existing test runtime classpath. No
benchmark framework, dependency, plugin, permanent task, device, or alternate codec path is added.

Before collection, record the exact Git revision or production/test tree hashes, runner file hash,
JDK/JVM/OS identity, and invocation. Gradle compilation/configuration wall time is recorded
separately from codec operation latency.

### Workloads and order

One invocation runs these four groups in fixed order:

1. encode the one-pixel document represented by the committed minimal fixture;
2. decode the already-created 46-byte carrier read from that committed fixture;
3. encode one generated 256 by 256 maximum document; and
4. decode its already-created 262,186-byte carrier.

Before timing, the minimal encoder output must equal the committed fixture byte-for-byte. The
maximum document uses deterministic row-major exact pixels. Fixture creation, carrier creation, full
equality, CRC, and complete round-trip checks occur before or after all timed samples, never between
them.

Each group runs five unreported warmups followed by exactly 20 measured operations. The timer uses
`System.nanoTime()` immediately around one encode or decode call. Metadata is emitted and flushed
before sampling. Immediately after each timer ends, the raw sample row is emitted and flushed before
verification or the anomaly check so a failing sample and all earlier completed rows remain evidence.
This output occurs between samples; full equality, CRC, pixel scans, hashing, and other expensive
checks remain prohibited there. After the raw row, each sample checks only the expected document
identity for decode or byte count for encode. The final report publishes the minimum and maximum for
each complete group; it computes no percentile or acceptance threshold.

### Budget and stopping conditions

The budget is one invocation, 20 samples times four groups, with no retry, replacement, selective
drop, or favorable rerun. An outer host-process timeout of 60 seconds bounds the complete runner and
excludes Gradle compilation/configuration time. The runner also checks each completed sample against
one second after timing; this is a post-operation anomaly check, not a preemptive hard timeout.

Any exception, non-accepted result, wrong cheap fact, sample over one second, outer timeout, missing
row, or order mismatch stops collection and makes the observation invalid. Completed rows remain
evidence, and a new attempt requires an explicit protocol revision and review. Numeric values below
one second remain descriptive; they do not produce a performance PASS verdict.

## Planned narrow verification

- filtered project-format contract tests during iteration;
- `:core:project-format:check` for module compile, test, ktlint, and detekt;
- the affected architecture-validator contract test and root `validateArchitecture`;
- `validateDocumentation` and `git diff --check`; and
- required final-candidate `quality` CI instead of a duplicate local full suite.

Normal Gradle cache and daemon defaults apply. Forced clean, cache bypass, device tests, profile
generation, and unrelated domain/application suites are excluded.

## Result

The one permitted invocation completed on 2026-09-09 JST. The wrapper found all 80 raw rows in
the predeclared order, all four summaries, no trailing rows, and empty runner stderr. The runner
completed normally within its 60-second task timeout, and every sample passed its one-second
post-operation anomaly check. No retry or replacement invocation was made.

The observation used Windows 11 10.0 amd64 and OpenJDK 64-Bit Server VM 21.0.11. Its artifact
identity was base Git revision `4eb519dc4504a5e453eb59c59484d13db6fe0168`, production-tree SHA-256
`e34eea25693e0467a9e4c39e07e2406262eaeb124b4099af35465b3c36fcfbf9`, test-tree SHA-256
`4ff9d4b19030e894118a2b414bf665f2aa0ae1ec7ad951cc92cd915cfdbcaaa1`, and runner SHA-256
`3526a575bc83a3033cb33fd5851dd81e8c1027dc154d8d416ff6e01b26e3b58a`. The temporary init script
and wrapper SHA-256 values were respectively
`bec7f4f7e8810bd27b27773052aa69508910457c614f506b155ab2e6c8c0ca2e` and
`27cbaf26ecd48f37d482944cd5213b8f9dae92d90f66649c10fcb85c1520b4d0`.
The production/test hashes are deterministic aggregates over the exact Windows collection-checkout
file bytes and relative paths; they are source identities rather than Git object IDs.

The separately recorded Gradle preparation wall time was 8,767 ms, and the complete Gradle
invocation containing the timed Java process took 9,082 ms. These values include Gradle work and
are not codec-operation latency. The exact invocation added the private task through the reviewed
external init script and ran `:core:project-format:issue85HostEvidence`.

| Group | Minimum ns | Maximum ns |
| --- | ---: | ---: |
| minimal encode | 6,300 | 72,000 |
| minimal decode | 13,800 | 37,400 |
| maximum encode | 1,599,600 | 1,893,500 |
| maximum decode | 1,825,700 | 4,622,900 |

The exact metadata, 80 raw rows, and summaries are committed in
[`measurements/m3-project-format-host-latency-v1.csv`](measurements/m3-project-format-host-latency-v1.csv).
The collected CRLF raw output is 2,370 bytes with SHA-256
`2e93572daeec8abb974870ceb02cfd9c7b3fb12e8997ca309caf5511772e212d`. Git's required LF
normalization retains the same 91 lines and sample values in a 2,279-byte committed blob with
SHA-256 `8c919065844af7cd9685532ee8e9ca692913e9b52c4626660ea2a542aab868c2`. These host-JVM minima
and maxima are descriptive observations only. They establish no Android or product performance
PASS and support no speedup claim.

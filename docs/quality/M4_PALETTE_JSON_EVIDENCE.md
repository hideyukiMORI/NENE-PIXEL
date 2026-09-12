# M4 Palette Definition and JSON Evidence

Issue #105 / P4-01. Governing contracts: ADR 0022 and PALETTE_JSON_V1.

## Scope and checks recorded before execution

The change extends lower-level Palette capacity from 32 to 256, adds immutable PaletteDefinition
with minimum 2 and validated default, and implements a no-I/O JSON codec in core/project-format.
Existing M3 drawing, RGBA storage/history, fixed eight-color composition, Android UI and lifecycle
do not change. QLT-011 representation/format/limit triggers require boundary/round-trip/corruption
contracts and affected bounded host evidence. This is not a device/drawing performance change.

Iteration checks are filtered Palette/PaletteJson tests, domain/project-format ktlint/detekt,
validateDocumentation/validateArchitecture and affected consumer compile/palette contracts. The
final PR uses required canonical quality CI, without a duplicate local full build. No device,
profile regeneration, cache bypass, cold build or historical measurement recipe is authorized.

## Prospective host protocol: palette-json-host-v1

Hypothesis: the bounded palette construction and schema-specific codec finish the supported
boundary workload without a runaway loop or allocation cost. These host-JVM observations are
descriptive, establish no Android/product latency PASS, and claim no speedup.

Use one internal main in the existing project-format test artifact, never a routine test or
permanent Gradle task. A temporary external init script registers a JavaExec using its test runtime
classpath; no dependency/plugin/framework is added. Record exact source and runner hashes, Git
base, JDK/JVM/OS, invocation and runner/init identities before collection. Keep raw stdout/stderr
and record Gradle wall time separately from operation latency.

One invocation runs seven groups in this fixed order:

1. Palette.create from an existing 32-color immutable list (old-cap reference workload).
2. Palette.create from an existing 256-color immutable list (new-cap workload).
3. Encode the two-color golden definition.
4. Decode its prebuilt bounded carrier.
5. Encode a 256-slot definition with deterministic RGBA and default 255.
6. Decode its prebuilt canonical carrier.
7. Decode the same definition padded with JSON whitespace to exactly 16,384 bytes.

The first two groups compare supported sizes in the candidate; they are not old/new artifact
performance results. Full golden/equality/round-trip and resource assertions run before and after
all groups. Each group has exactly five unreported warmups and 20 samples. System.nanoTime wraps
one factory/encode/decode call. Immediately after timing, print and flush every raw row, then check
only accepted result plus entry count/default or byte count. No full equality, scans, hashing,
GC or memory probes occur between timed samples. Report each group's min/max, no percentiles.

Budget: one invocation, 140 measured rows, outer Java process timeout 60 seconds, no retries or
sample replacement. A completed sample over 1 second is a post-operation gross-anomaly guard,
not a product threshold or preemptive timeout. Wrong cheap facts, non-accepted results, exceptions,
timeouts, missing/out-of-order rows or guard failure invalidate the observation and preserve all
rows. Further collection requires an explicit revised protocol and review; never rerun until green.

## Bounded ownership and allocation audit

Palette copies caller-owned lists only after the 1..256 admission check. PaletteDefinition retains
that immutable Palette reference and one index, with no second color array. Decoder retains at most
256 PixelColor values; it checks the 257th position before parsing another color. Ordered duplicates
and hidden RGB remain intact. The schema parser sees only one object and one array, and has no
recursive value parser. Its seen-field set has at most four enum values.

PaletteJsonBytes copies at most 16,385 input bytes; oversize decode rejects before another copy or
UTF-8 conversion. Accepted decode makes one defensive byte copy and one strict UTF-8 string of at
most 16,384 UTF-16 code units. A scanned raw string span and its exactly pre-sized StringBuilder
cannot exceed that envelope; escaped content cannot expand. Tokens are released after each field
or color. Colors are built in a capacity-256 list then copied once into Palette; the builder is
not returned. Final decoded retention is the definition, Palette and its bounded color graph,
without source JSON/cursor/builder references. Encode derives at most 256 entries and produces a
bounded canonical string, temporary UTF-8 bytes and one owned output copy. The raw evidence records
actual canonical byte counts. This is an ownership/copy bound, not total retained heap measurement.
The documented standard-library CharacterCodingException is normalized to InvalidUtf8 at this
boundary under ARC-010; the schema reader has no exception-based parse control flow.

## Results

The single prospective invocation completed on 2026-09-13 JST with all 140 rows in order, seven
correct summaries, empty stderr and no one-second guard or 60-second timeout violation. No retry,
replacement or performance-protocol revision was used. The complete Gradle invocation took
11,997 ms; the preceding consumer/test-artifact preparation took 34 seconds. These build times are
separate from operation latency. Canonical output sizes were 96 bytes (golden, two colors/default 0)
and 3,146 bytes (256 colors/default 255); padded input was exactly 16,384 bytes.

| Group | Minimum ns | Maximum ns |
| --- | ---: | ---: |
| Palette.create, 32 entries | 2,800 | 21,300 |
| Palette.create, 256 entries | 1,900 | 37,800 |
| Encode, 2 entries | 7,100 | 41,000 |
| Decode, 2 entries | 38,300 | 113,000 |
| Encode, 256 entries | 161,300 | 845,400 |
| Decode, 256 entries | 303,800 | 601,400 |
| Decode, padded 16 KiB | 248,000 | 555,500 |

These values are valid descriptive host observations only, not a product-performance PASS or a
comparative speedup result. Read [all raw rows](measurements/m4-palette-json-host-v1.csv).

### Exact identity

Base main was `a622dcd86e59a95448eaca809544dfb63cdf320d`. Collection used the uncommitted #105 candidate,
with exact Windows source-file manifests captured before execution and rechecked afterward. The
source aggregate covers domain/format sources and fixtures, build logic, toolchain/configuration,
owning build/lock files and temporary runner scripts. The class aggregate covers domain main plus
format main/test compiled classes; neither hash is mislabeled as a Git revision.

| Artifact | SHA-256 |
| --- | --- |
| Source/configuration aggregate | `1fb22f80b8425a4db86bcc2bc4d9bea1e7017132414d3f4671808691ac53c90e` |
| Compiled-class aggregate | `b60b11ca33a4eb81f1afa4ebd0de431240b62bdca66182ca18cae4a442684810` |
| PaletteJsonHostEvidence.kt | `69da3cea3821f4ddbcece91901a16e6d347162664f1847df2533318aee7fcd90` |
| Temporary init script | `257974d66a88617be773e73e0f571ff731da8f1d7072c7fef159452e2bf668cd` |
| Temporary collection wrapper | `c48e0e13cd4e9983baf112b1fbece718d088e4d9dcf261cfdc0d40ac516311ec` |
| Raw CRLF stdout | `3aa064410eb84680d9e9f3d4ba77f32a12046daf875328b27d55cd3c26cee498` |
| Committed LF CSV, identical rows | `5158e406b00d556c738ef3944da4d5ba4f1b97fd08e527830b38e435c08fa1ff` |

Environment: Windows 11 10.0 amd64; OpenJDK 64-Bit Server VM 21.0.11; repository Gradle/Kotlin
toolchain and locks unchanged. Exact invocation was `gradlew.bat -q -I
build/reports/issue-105/host.init.gradle.kts :core:project-format:issue105HostEvidence`.
Local raw manifests/process records remain under `build/reports/issue-105/host-v1-*` in the owning
worktree. No APK was built or installed for this observation, and no private device files changed.

### Correctness and integration results

The affected host contracts passed: domain `*Palette*` (10 tests), format `*PaletteJson*` (17 tests,
including all 255 admitted sizes, every golden truncation and a fixed 1,024-input malformed corpus),
application `WorkspaceReducerTest`/`EditorRuntimeTest` (19 tests) and presentation
`ViewportEditorControllerTest` (9 tests). Total: 55 tests, no failures/errors/skips.

Commands used (normal cache/daemon defaults):

```text
./gradlew :core:domain:test --tests '*Palette*' :core:project-format:test --tests '*PaletteJson*'
./gradlew :core:domain:ktlintCheck :core:project-format:ktlintCheck :core:domain:detekt :core:project-format:detekt
./gradlew :core:application:test --tests '*WorkspaceReducerTest' --tests '*EditorRuntimeTest' :presentation:compose:testDebugUnitTest --tests '*ViewportEditorControllerTest' :app:android:compileDebugKotlin
./gradlew validateDocumentation validateArchitecture
```

The first compiler run rejected nullable end-of-input characters; corrected token lookahead.
Initial narrow runs stopped on return-count/complexity/line-length checks before test completion.
Shared bounded delimiter traversal, token boundaries and formatting resolved them; no gate was
weakened. The successful combined narrow run took 16 seconds. Later test-runner-only formatting
failures were corrected before compilation or the one permitted observation. These logs are kept
as implementation feedback, not mislabeled performance samples. A read-only independent review
found one Issue-contract ambiguity about standard UTF-8 decoder exceptions; the existing ARC-010
normalization rule was clarified in ADR 0022 and the Issue, and read-back resolved the finding.

The final PR still requires canonical quality CI. Source/hash equality allows this observation
to accompany later prose/commit metadata without recollection. The live indexed editor, palette
draft UI, SAF import/export and v2 storage remain #106/#107 work; no device behavior is claimed here.

Waivers: none

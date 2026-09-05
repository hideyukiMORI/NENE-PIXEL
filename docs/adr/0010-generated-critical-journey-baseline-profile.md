# ADR 0010: Generated critical-journey Baseline Profile

- Status: accepted
- Date: 2026-09-05
- Issue: #54
- Affected rules: `ARC-002`, `QLT-001` through `QLT-005`, `QLT-010`

## Context

Issue #54 measures the canonical Pencil/Undo journey on the physical
`NENE-P2-ALLDOCUBE-IPL80MP-A16-API36` profile. The retained schema-v5
`speed-profile` diagnostic still misses five of ten deadlines, with platform overrun p95 of
3.106164 ms. A bounded full-AOT attribution run reduced CPU-frame p95 by about 1 ms, while the
traced canonical `PixelCanvas` work was about 0.236 ms. This points to incomplete packaged profile
coverage rather than a second renderer or document path.

The application currently owns one hand-written wildcard rule in
`app/android/src/main/baseline-prof.txt`. Adding two narrower text rules did not improve the
diagnostic, and the packaged profile contained only 23 hot rules. Rule guessing is therefore not a
reliable canonical path.

Android's Baseline Profile tooling can collect executed code from an instrumented critical journey,
write a deterministic source artifact into the consumer application, and package the compiled
profile through the existing release build. The Android Gradle Plugin 9 new DSL used by ADR 0001 is
supported by AndroidX Benchmark 1.5; the latest available 1.5 release candidate is required until a
stable 1.5 release is available. AndroidX Benchmark 1.4 requires the prohibited legacy
`android.newDsl=false` compatibility switch for this producer shape.

## Decision

### Ownership and module boundary

Create one build-only Android test module, `:quality:baseline-profile`, as the sole Baseline Profile
producer. It owns only the out-of-process, deterministic Pencil/Undo collection journey. It does not
import a core module, call a document or workspace state owner, duplicate renderer geometry, or
become a runtime dependency.

`:app:android` is the sole consumer. Two Gradle build edges implement that relationship: its
`baselineProfile` configuration references `:quality:baseline-profile`, and the producer's generated
`testedApks` configuration references `:app:android` from `targetProjectPath`. An `implementation`,
`api`, test, or other production dependency in either direction is forbidden. The root architecture
validator knows these exact build-only exceptions and continues to reject unknown modules, cycles,
and every other unlisted edge.

A shared `nene.android-test` convention owns Android test-module compiler, SDK, formatting,
static-analysis, dependency-locking, and JVM policy. Module-local divergence from the established
Android policy is prohibited.

### Toolchain

The version catalog is the only version authority. This change pins:

- AndroidX Baseline Profile Gradle plugin and Benchmark Macro JUnit4 to `1.5.0-rc02`;
- AndroidX Test JUnit extension to `1.3.0`; and
- AndroidX Test UI Automator to `2.4.0`.

The Baseline Profile and Benchmark release-candidate exception is limited to the build and
instrumentation toolchain. It does not authorize pre-release runtime APIs or the AGP legacy DSL.
The first compatible stable AndroidX Benchmark 1.5 release replaces it in a focused dependency
change.

Dependency locking and SHA-256 verification cover the new plugins and libraries. No module-local
repository, dynamic version, version range, or automatic dependency update path is introduced.

### Generated artifact

The producer launches the installed application and finds the canonical editor through stable
accessibility semantics. It derives the top-left pixel center from the reported canvas bounds,
performs the Pencil mutation, verifies the dirty state, performs Undo, and verifies the clean state.
Those actions exercise the one production UI-to-command path. Fixed screen coordinates and direct
state access are prohibited.

The producer sets `includeInStartupProfile = false`; startup-profile classification and startup
benchmarking are out of scope. The consumer merges into `main`, saves the generated output in
source, and disables automatic generation during ordinary builds. Profile generation is an explicit
physical-device operation.

The generated directory under `app/android/src/main/generated/baselineProfiles/` is the only
project-owned Baseline Profile source after migration. The hand-written `baseline-prof.txt` is
removed in the same focused change; retaining manual and generated rule paths together is
prohibited.

Two isolated generation invocations on the declared physical device must produce identical
canonical profile content before the artifact is accepted. Canonical profile content is UTF-8
without a byte-order mark, joins rules with LF and no terminal separator, retains the producer's
deterministic rule order, and preserves every complete rule including its `H`, `S`, and `P` flags.
CRLF and LF encodings of the same ordered rules have one canonical hash; a changed rule, flag,
order, duplicate, malformed UTF-8 input, or bare CR separator does not. No package or framework
rule is excluded from this comparison.

The one documented generation command runs exactly two producer invocations. Before starting the
second invocation it stores the first invocation's fresh raw producer output, merged/source output,
available instrumentation results and logs, task outcomes, source revision, tested APK hashes, and
a manifest under a caller-supplied evidence identity. It does the same for the second invocation
before comparing them. Producer output that predates an invocation is first moved into that
invocation's evidence directory, so it cannot qualify as fresh and is not discarded. Evidence schema
`nene-pixel-baseline-profile-evidence-v1` refuses an existing identity or invocation directory
instead of overwriting it.

An invocation is fresh only when Gradle succeeds, the connected producer task actually executes,
the output pull reports no failure, and a newly written producer profile is present. Compilation,
packaging, merge, or lifecycle tasks may legitimately be `UP-TO-DATE` or `FROM-CACHE`; those labels
neither prove nor disprove fresh producer execution. Identical pre/post source hashes are valid when
fresh producer evidence proves that the regenerated content is identical. A failed producer or
pull followed by hashing an old source file is always rejected.

The tracked source and hash are snapshotted before either invocation. They are replaced with the
matched canonical result only for the final narrow artifact validation. A pair or validation failure
restores the snapshot while retaining the failed evidence. Only successful final validation writes
the acceptance manifest; a matched pair manifest alone is not an acceptance result.

The producer enables strict internal stability. This makes failure to converge within the
producer's bounded iterations fatal, but does not replace the two-invocation comparison and does not
claim that internal convergence prevents process-to-process rule variation.

The canonical local `check` verifies that the manual file is absent, exactly one generated text
profile exists, it is non-empty canonical UTF-8, and its recorded canonical SHA-256 matches.
Generated drift therefore fails locally and in CI without asking CI to provision a device. Exact
source revision embedded in the signed release-like APK remains a later packaging/measurement
acceptance check; the generation manifest records the Git source revision and exact tested APK
hashes without creating a second revision authority.

The signed release-like APK must retain the exact source revision, valid v2/v3 signatures, and the
two expected packaged profile assets. Its compiled baseline profile must remain below Android's
1.5 MB recommendation. The existing fixed schema-v5 physical diagnostic is run once after packaging;
the established thresholds and 50-sample promotion condition do not change.

## Rejected alternatives

### Keep tuning hand-written wildcard rules

Rejected because the two-rule experiment was a measured no-op and manual guesses do not prove that
the canonical Pencil/Undo journey is represented.

### Keep manual and generated profiles together

Rejected because two project-owned rule paths obscure provenance, allow stale wildcard rules to
survive regeneration, and violate one canonical implementation path.

### Compile the whole application with broad full-AOT rules

Rejected because the full-AOT run is attribution evidence, not an acceptable shipped profile. Broad
rules increase install work and binary profile size without identifying the exercised critical path.

### Generate automatically during every build or in current CI

Rejected because generation requires a controlled physical device and would make the canonical
local/CI gate depend on unavailable external state. Ordinary builds verify the committed artifact;
the explicit generation operation refreshes it.

### Disable the AGP 9 new DSL

Rejected because ADR 0001 selected AGP 9.4 with built-in Kotlin. A compatibility escape hatch would
create a second, temporary build model and postpone the required migration.

### Add a Macrobenchmark or startup-profile work package

Rejected as broader than the operation-specific evidence. Issue #54 needs one profile producer for
the already fixed Pencil/Undo journey; benchmark metric migration and startup optimization require
their own focused Issues.

## Consequences

### Benefits

- shipped profile rules originate from the actual canonical UI, command, history, and rendering path;
- regeneration has one explicit producer, versioned retained evidence, and an exact canonical
  rule-and-flag acceptance check;
- normal local and CI builds remain device-independent while still rejecting committed drift;
- production modules gain no test-tool imports or alternate state access; and
- the existing physical frame protocol can evaluate the packaged result without changing its schema.

### Costs and risks

- AndroidX Benchmark `1.5.0-rc02` is a temporary pre-release build-tool dependency;
- generation requires the named physical device and can fail when accessibility semantics or the
  canonical journey intentionally change;
- compiler/runtime updates may legitimately alter generated rules and require the same two-run
  review; and
- generated evidence consumes ignored local storage and must be retained outside tracked source;
- a generated profile can still fail the frame threshold, in which case Issue #54 remains open and
  the result is recorded without a favorable rerun.

## Enforcement impact

- `validateArchitecture` permits only the named build-only profile edge and rejects production use;
- build-logic tests cover the Android test convention;
- the producer has focused instrumentation assertions for launch, Pencil dirty state, Undo, and the
  restored clean state;
- the producer rejects internal non-convergence, and the canonical host wrapper rejects stale,
  failed, nonfresh, overwritten, or rule/flag-divergent invocation evidence;
- `check` validates generated artifact cardinality, canonical encoding, and SHA-256 identity;
- dependency locks and verification metadata cover every new artifact; and
- no warning, lint, detekt, architecture, frame threshold, suppression, baseline exception, or waiver
  is weakened.

## Migration and rollback

Migration adds the accepted module and convention, generates and verifies the source profile twice,
then removes the manual wildcard file before packaging. No production state, public API, document
schema, or user data migrates.

The evidence-contract correction in Issue #62 changes the recorded hash for the existing 13,510
ordered rules from Windows CRLF bytes to their canonical UTF-8/LF bytes. It does not add, remove,
reorder, or edit a generated rule and does not relabel any historical generation or performance
result. This representation proof does not retroactively establish fresh producer provenance for
the earlier unversioned runs. Existing logs remain historical evidence; only later authorized
generations may produce schema-v1 manifests.

Rollback removes the producer, consumer plugin/edge, generated profile and hash, convention, catalog
entries, dependency state, architecture exception, and this ADR together, then restores the prior
single manual profile only if Issue #54 explicitly returns to that accepted baseline. A partial
rollback that leaves two profile sources or an unvalidated generated artifact is prohibited.

## Related

- Issue: #54
- Evidence correction: #62
- Builds on: ADR 0001 initial build toolchain
- Builds on: ADR 0007 canonical Pencil gesture
- Builds on: ADR 0009 bounded history and clean checkpoint
- Supersedes: hand-written `app/android/src/main/baseline-prof.txt`
- Superseded by: none

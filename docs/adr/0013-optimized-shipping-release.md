# ADR 0013: Optimized shipping release

- Status: proposed
- Date: 2026-09-06
- Issue: #76
- Affected rules: `ARC-001`, `ARC-004`, `ARC-009`, `QLT-011`, `QLT-012`, `QLT-013`, `QLT-014`, `QLT-015`, `QLT-016`

## Context

NENE-PIXEL's application module does not configure release optimization. The pinned Android Gradle
Plugin 9.4 `ApplicationBuildType.optimization` DSL defines `enable` as the application switch for R8
code shrinking and resource optimization and defaults it to `false`. The current shipping release is
therefore unoptimized. This official production path remains untested after the source-level Compose
candidates evaluated for Issue #54 failed its unchanged absolute frame gate.

Baseline Profile generation and profile consumption have separate identities. The accepted P62
artifact records a completed two-process generation pair at source
`384af834c74189dcca9d79da1cf1ac90d0363082`, producer app SHA-256
`f7390cf56f38a36e0ac2ed6e7dfb14c73f75292bef212a149a1648d2fbab3b79`, producer test SHA-256
`fc0c57cffa3ac7b232f19638e372f33841d92f199f977b0eaf47fb87a41ed691`, and canonical profile
SHA-256 `3be9f24e5c485364787c1319c3ec6bd2138ed589a30ff245100ce9a283c653ee`. QLT-011 requires a
new accepted generation pair when the generated artifact is updated. QLT-012 permits that immutable,
already accepted artifact to be consumed by a later build when affected inputs and behavior are
shown equivalent and every producer and consumer identity remains explicit. A consumer build never
relabels the historical generation source or APKs.

Issue #77 changed the `EditorCanvas` call-site modifier so the canvas fits its bounded landscape
area. It invalidated earlier app, correctness, and frame identities. It did not change a Kotlin/JVM
method descriptor. Two separately authorized post-#77 producer pairs were rejected because their
exact ordered rule flags differed across processes. Across all four individually valid invocations,
the same 13,514 descriptors remained in the same order; only `H` on
`LayoutNode.<init>(ZI)V` and `SemanticsConfiguration.getOrElseNullable(...)` varied, and the direction
reversed between pairs. Those invocations are diagnostic evidence only and cannot accept or replace
P62. They do establish that #77 did not introduce an unresolved or new profile descriptor. The
stable-five producer experiment did not improve cross-process equality and is not adopted.

The exact local AGP 9.4 API JAR confirms that `ApplicationBuildType` exposes
`optimization(Action<Optimization>)`, and that `Optimization` exposes `enable`. This avoids relying
on a legacy DSL shape or adding a custom optimizer path.

A first host-only implementation at source `b74421361757ab9b6af38c61925dab1049603882` disproved one
interoperability assumption. The release optimization block built successfully, but the Baseline
Profile plugin 1.5.0-rc02 synthetic `nonMinifiedRelease` also ran R8. Its retained unoptimized
8,403,124-byte output became a 1,266,693-byte APK with a 28,885,590-byte mapping and obfuscated app
classes. The plugin's app-target `finalizeDsl` implementation copies the release build type and clears
legacy minify/shrink properties; it does not clear AGP 9.4's separate `Optimization.enable`, whose
merge behavior preserves `true`. This is a producer-invariance failure before device use, not a
performance result. Zero device tests and zero performance operations ran.

## Decision

If the prospective correctness and performance experiment passes, one role policy registered after
the existing app-target plugin's synthetic-build-type callback becomes the canonical application
optimization configuration. It sets shipping `release` and device proxy `benchmarkRelease` to
optimized and keeps Baseline Profile producer `nonMinifiedRelease` unoptimized:

```kotlin
pluginManager.withPlugin("androidx.baselineprofile.apptarget") {
    androidComponents.finalizeDsl { extension ->
        val releaseOptimization = extension.buildTypes.named("release").get().optimization
        val benchmarkOptimization = extension.buildTypes.named("benchmarkRelease").get().optimization
        val producerOptimization = extension.buildTypes.named("nonMinifiedRelease").get().optimization
        check(releaseOptimization !== benchmarkOptimization)
        check(releaseOptimization !== producerOptimization)
        check(benchmarkOptimization !== producerOptimization)
        releaseOptimization.enable = true
        benchmarkOptimization.enable = true
        producerOptimization.enable = false
        check(releaseOptimization.enable && benchmarkOptimization.enable && !producerOptimization.enable)
    }
}
```

`withPlugin` orders registration after the app-target plugin application and its own finalizer
registration. The pinned AGP registrar executes finalizers in FIFO order; the pinned plugin creates
the synthetic types in its callback. The pinned `OptimizationImpl.initWith` copies the primitive
enable value rather than sharing one object. `named(...).get()`, explicit reference-identity checks,
and the post-set value check fail closed if a type is absent, state is shared, or the correction is
ineffective. The role policy does not create a build type or change the accepted producer. There is
no preceding standalone release optimization block that the plugin can copy into the producer.

The application has no release signing configuration. The canonical optimized shipping `release`
output therefore remains unsigned. Device correctness and performance use the existing Baseline
Profile plugin's optimized, profileable, debug-signed `benchmarkRelease` derivative as a proxy,
never as a relabelled shipping APK. The unoptimized B reference is also a `benchmarkRelease` built
from post-#77 production inputs without the optimization policy. B and C use byte-identical P62
source-profile input and equivalent production Kotlin/resources, while their Git/config and packaged
APK identities stay distinct.

Before device use, DEX, resources, and packaged-profile payloads must be equivalent between the
optimized shipping output and proxy, with only the expected profileable, signature, and container
metadata differences enumerated. Every optimized output retains exact APK/source/signature state and
packaged prof/profm identities together with R8 mapping, configuration, usage, seeds, merged keep-rule
inputs, mapping ID, and retrace tool identity. A controlled retrace round trip must prove the mapping
is usable. The exact accepted P62 text fed to B and C remains byte-identical; its historical generation
identities are never relabelled. Neither rejected post-#77 pair supplies acceptance evidence.

The benchmark proxy must pass focused optimized-runtime editor correctness and the unchanged Issue
#54 frame-v3 absolute performance gate before this ADR can become accepted behavior.

## Rejected alternatives

### Keep shipping release unoptimized without evaluation

Rejected as a prospective decision. The current default is conservative for runtime compatibility,
but it leaves an official whole-program release optimization path unevaluated while the product is
trying to close a finite frame-performance gap.

### Replace the AGP 9.4 DSL with legacy minify and resource flags

Rejected. The pinned toolchain provides one application build-type optimization switch that owns
both code shrinking and resource optimization. Parallel legacy configuration would obscure the
canonical build contract.

### Rely on the Baseline Profile plugin's legacy non-minified override

Rejected by the first host attempt. Under AGP 9.4 it leaves `Optimization.enable=true` on the copied
synthetic build type, so both the producer and the supposed reference are optimized.

### Accept either rejected post-#77 profile invocation

Rejected. Each invocation was internally stable, but neither outer pair met the required exact
cross-process equality gate. Byte equality between one invocation and P62 is compatibility evidence,
not new generation acceptance. Flag normalization, union, another retry, and a larger stability window
would weaken or repeat the failed contract.

### Add custom R8 rules, package scope, full-AOT compilation, or another measurement path

Rejected. No evidence currently requires a keep-rule exception or narrowed package scope. Full AOT
would change the runtime condition, and another collector would violate the single evidence path.

## Consequences

### Benefits

- R8 can remove unused application/library code and resources and optimize the shipping DEX.
- The shipping release follows Android's documented Baseline Profile rewrite path.
- Mapping and retrace artifacts make optimized crashes diagnosable and reviewable.

### Costs and risks

- Whole-program shrinking can expose missing keep rules, reflection/resource assumptions, or startup
  failures that debug and unoptimized tests cannot detect.
- R8 rewrites DEX and packaged profile identities, so the optimized APK needs a new immutable artifact
  record even though the accepted P62 source profile remains unchanged.
- The installable benchmark proxy differs from the unsigned shipping container in profileable and
  signing metadata, so payload equivalence must be proved explicitly.
- A smaller APK or successful build is not enough; correctness and the fixed frame threshold can fail.
- P62's two `H` flags classify generic Compose methods as hot, but that classification alone does not
  prove their duration, ownership, frame effect, or any speedup from this candidate.

## Enforcement impact

Issue #76 and `M2_FRAME_FOLLOW_UP.md` define the prospective comparison before implementation. Host
checks must validate the exact AGP DSL, build unoptimized and optimized `benchmarkRelease` roles from
post-#77 production inputs, build the unsigned optimized shipping output, keep `nonMinifiedRelease`
unoptimized, and reject R8 warnings/errors. They must retain payload-equivalence, mapping/retrace, and
packaged-profile identities and prove the exact accepted P62 text is the input to each consumer.

A correctness-only exact-class UIAutomator class with three bounded journeys lives in the existing
`:quality:baseline-profile` `benchmarkRelease` source set; it does not use `BaselineProfileRule` or
enter the producer app source. It covers Pencil/Undo/Redo, one finite full-canvas diagonal through
palette/Pencil/Eraser/two-entry history, and new-document state across one verified-and-restored
orientation recreation. It does not claim raw-stroke-limit or multi-touch viewport coverage. Direct
installation and instrumentation must prove the class ran three tests with zero skipped against the
exact optimized benchmark APK used by the frame writer. No dependency, plugin, toolchain, module,
schema, threshold, suppression, or waiver changes.

The supporting sources are the [AGP 9.4 Optimization API](https://developer.android.com/reference/tools/gradle-api/9.4/com/android/build/api/dsl/Optimization),
the [AGP 9.4 ApplicationBuildType API](https://developer.android.com/reference/tools/gradle-api/9.4/com/android/build/api/dsl/ApplicationBuildType),
and the [Android Baseline Profiles overview](https://developer.android.com/topic/performance/baselineprofiles/overview).

## Migration and rollback

After this proposed ADR and the protocol are reviewed, add the single post-app-target role policy and
the variant-scoped correctness test. Package B from post-#77 main without the policy and C from the
same production Kotlin/resources plus the policy, both with the exact accepted P62 profile input.
The producer/non-minified control must show no R8 mapping and expected unobfuscated classes. It
intentionally excludes the project-generated P62 text while retaining dependency profile input; the
P62 consumers are B `benchmarkRelease` plus C `release` and `benchmarkRelease`. This consumer reuse
does not run or publish a new producer pair.

If host packaging, shipping/proxy payload equivalence, mapping/retrace, optimized-runtime correctness,
a gross diagnostic, or the absolute candidate decision fails, remove the focused role policy and
retain the failed artifacts without a runtime toggle or second shipping path. If the candidate passes
every gate, update this ADR to accepted in the same focused change and keep optimized `release` as the
sole shipping path. Future keep-rule or package-scope changes require a separate evidence-backed Issue
and ADR update.

## Related

- Issue: [#76](https://github.com/hideyukiMORI/NENE-PIXEL/issues/76)
- Parent: [#54](https://github.com/hideyukiMORI/NENE-PIXEL/issues/54)
- [ADR 0011](0011-change-scoped-verification.md)
- [M2 frame follow-up](../quality/M2_FRAME_FOLLOW_UP.md)
- PR: pending
- Supersedes: none
- Superseded by: none

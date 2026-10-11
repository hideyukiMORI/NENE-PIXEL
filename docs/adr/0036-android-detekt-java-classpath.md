# ADR 0036: Android Java outputs in typed detekt analysis

- Status: accepted
- Date: 2026-10-09
- Issue: #194
- Affected rules: `ARC-001`, `QLT-001`, `QLT-002`, `QLT-003`, `QLT-005`, `QLT-006`, `QLT-011`, `QLT-012`

## Context

After #189, `:app:android:detektDebugAndroidTest` reports no rule findings but eight
compiler-analysis errors referring to the Java `AcceptanceDocumentsProvider`. Android test
compilation succeeds. The same diagnostics existed before #189; their absence from the
findings report does not establish complete type analysis.

The pinned detekt 2.0.0-alpha.6 Android integration supplies the component's Kotlin sources and
Kotlin compilation outputs. Its analysis environment builds its source module from Kotlin
source roots, so adding Java source files alone does not provide the missing Java symbols.
The Java compiler already produces the authoritative classes for the same Android component.

## Decision

The Android quality convention adds each component's Java compiler destination to the classpath
of its existing typed detekt task. Bind the destination through the JavaCompile task provider,
preserving Gradle's producer dependency and input tracking. Use the component name to select
the existing AGP JavaCompile and detekt tasks; never hard-code an intermediates directory.
Append through the named task after Android/Kotlin task registration, so the plugin's existing
classpath convention is established before the Java destination is added.

Apply the same function to application, library and standalone test conventions, including
nested test components. Preserve the plugin's existing Kotlin sources, classpath, friend paths,
compiler flags and reports. Do not create another analysis task or change `check`/CI wiring.

This repairs analysis inputs under ADR 0001. No plugin upgrade, dependency addition, rule
exclusion, severity change, baseline, suppression or Java-to-Kotlin fixture conversion is
authorized by this decision.

## Rejected alternatives

### Add Java source directories only

The pinned analysis environment does not add those Java roots to its source module. Supplying
the actual compiler output resolves Java symbols through the existing binary dependency path.

### Convert the test provider to Kotlin

The provider intentionally uses Java/framework classes in the test APK process, where Kotlin
is deduplicated into the target APK. Changing its language would alter an unrelated runtime
contract to accommodate build tooling.

### Add a literal build directory or run javac manually first

A literal directory can be missing or stale and loses the producing task dependency. Requiring
an earlier manual compilation makes a successful analysis depend on worktree history.

## Consequences

### Benefits

Mixed Java/Kotlin Android components use one compilation and one typed analysis path. Java
changes invalidate their own compiler output and the corresponding analysis input.

### Costs and risks

Typed detekt now requests the matching JavaCompile task, which may do additional compilation
when Java sources changed; Kotlin-only components normally have no Java work. AGP task naming
is version-bound and must be checked when upgrading the pinned toolchain. Product code and
per-operation runtime cost are unchanged.

## Enforcement impact

A focused Gradle fixture resolves a Java test class and rejects a configured forbidden call
to that Java class through the type-dependent Kotlin rule. The real app androidTest analysis
must have neither compiler-analysis errors nor rule findings. Existing CI and rules stay intact.
The fixture uses detekt's process-isolated Worker API so analyzed JARs are released before
JUnit deletes the temporary directory on Windows; ordinary project analysis is unchanged.

## Migration and rollback

Apply the convention to current Android consumers in this change. No application data or APK
source migration is required. Remove this augmentation only after a pinned upstream integration
provides equivalent Java symbols and the mixed-source regression remains green. A rollback
must preserve the recorded incomplete-analysis diagnostics; it must not call them complete.

## Related

- Issue: [#194](https://github.com/hideyukiMORI/NENE-PIXEL/issues/194)
- [ADR 0001](0001-initial-build-toolchain.md)
- [ADR 0024](0024-differential-check-selection-and-result-reuse.md)
- Upstream: [detekt type resolution](https://detekt.dev/docs/gettingstarted/type-resolution/)
- Supersedes: none; repairs the Android analysis inputs established by ADR 0001
- Superseded by: none

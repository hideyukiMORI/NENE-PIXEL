# ADR 0001: Initial Android build and toolchain

- Status: accepted
- Date: 2026-08-31
- Issue: #5
- Affected rules: `ARC-002`, `ARC-003`, `ARC-006`, `ARC-012`, `KOT-008`, `QLT-001`, `QLT-002`, `QLT-003`, `QLT-005`

## Context

NENE-PIXEL needs one reproducible Android scaffold before production code exists. The Android Studio wizard must not silently choose the package, module graph, language plugin, dependency versions, or quality tools.

The development host has Android Studio `AI-261.26222.65.2613.15948027`, JBR 21.0.11, and Android SDK platforms 34 and 35. The selected Compose release requires a newer SDK, so the existing local SDK is evidence about setup work, not a reason to select an older project contract.

The version decision uses these primary sources:

- [Android Gradle plugin 9.2 release notes](https://developer.android.com/build/releases/agp-9-2-0-release-notes): AGP 9.2 supports API 37 and requires Gradle 9.4.1, SDK Build Tools 36.0.0, and JDK 17 or newer. The 9.2.1 patch fixes an R8 class-loading defect in 9.2.0.
- [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html): Gradle can run on Java 21 from Gradle 8.5 onward.
- [AGP built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin): AGP 9 enables built-in Kotlin and removes the need for `org.jetbrains.kotlin.android`.
- [AGP 9.2 fixed issues](https://developer.android.com/build/releases/agp-9-2-0-release-notes#fixed_issues): AGP 9.2 updates its Kotlin Gradle plugin dependency to 2.3.10.
- [Compose setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler): Compose 1.12 requires compile SDK 37 and AGP 9; the current stable Compose BOM is `2026.08.00`.
- [Compose BOM guidance](https://developer.android.com/develop/ui/compose/bom): the BOM is the canonical way to keep Compose libraries compatible, while the Compose compiler follows the Kotlin compiler version.
- [ktlint Gradle plugin releases](https://github.com/JLLeitschuh/ktlint-gradle/releases): plugin 14.2.0 supports Gradle 9 and AGP built-in Kotlin.
- [ktlint releases](https://github.com/ktlint/ktlint/releases/tag/1.8.0): ktlint 1.8.0 is the current formatter engine.
- [detekt compatibility](https://detekt.dev/docs/introduction/compatibility/): detekt 2.0 alphas support AGP 9 built-in Kotlin; 2.0.0-alpha.6 is the current release and is tested on the current Kotlin/Gradle generation.

## Decision

### Identity and Android support

- Package root: `io.github.hideyukimori.nenepixel`
- Android application ID: `io.github.hideyukimori.nenepixel`
- Root project name: `NENE-PIXEL`
- Display name: `NENE-PIXEL`
- Minimum SDK: 26
- Compile SDK: 37
- Target SDK: 37
- SDK Build Tools: use AGP 9.2.1's default, currently 36.0.0; do not override it in a module

Package names below the root follow the owning module and capability. A second root, abbreviated application ID, or debug-only application ID is prohibited unless an ADR changes the identity contract.

### Toolchain matrix

| Concern | Selected value | Canonical use |
| --- | --- | --- |
| Gradle runtime JDK | JDK 21 | Android Studio Gradle JDK, `JAVA_HOME`, and CI |
| Java toolchain | 21 | toolchain selection for project-owned compilation |
| Android JVM bytecode target | 17 | Java `sourceCompatibility`/`targetCompatibility` and Kotlin `jvmTarget` |
| Gradle Wrapper | 9.4.1 | the only supported Gradle entry point |
| Android Gradle plugin | 9.2.1 | all Android modules |
| Kotlin | AGP built-in Kotlin 2.3.10 | Android modules; no `org.jetbrains.kotlin.android` plugin |
| Kotlin JVM plugin | 2.3.10 | future non-Android core/build modules, matching the Android compiler line |
| Compose compiler plugin | 2.3.10 | Compose modules, matching built-in Kotlin |
| Compose libraries | BOM 2026.08.00 | all Compose dependency constraints |

JDK 21 is the build runtime and toolchain. Android bytecode remains at JVM 17 because it is the conservative Android contract supported by the selected ecosystem and avoids making Java 21 bytecode a hidden minimum. Project Kotlin code uses progressive mode only after a separate, evidence-backed decision; warnings are errors from the first source file.

AGP built-in Kotlin is mandatory. `org.jetbrains.kotlin.android`, `kotlin-android`, `kapt`, `android.builtInKotlin=false`, and `android.newDsl=false` are prohibited. Annotation processing is absent until a concrete capability and ADR justify KSP.

### Dependency and plugin authority

`gradle/libs.versions.toml` is the only authority for dependency and Gradle plugin versions. It contains explicit versions and plugin aliases. Exceptions are values whose owning tool requires another location:

- the Gradle distribution version and checksum live in wrapper properties
- Android SDK levels live in the owning Android convention once two Android modules exist; until then they occur once in `:app:android`
- dependency verification checksums and signatures live in Gradle verification metadata
- dependency lock state lives in Gradle lockfiles

Dynamic versions, version ranges, `latest.*`, snapshots, module-local repositories, and dependency versions written directly in module build files are prohibited. `pluginManagement` uses `google()`, `mavenCentral()`, and `gradlePluginPortal()`. Dependency resolution uses only `google()` and `mavenCentral()` and fails when a project declares its own repository.

Root build scripts declare plugin aliases with `apply false`. Repeated build policy moves to tested convention plugins only when a second real consumer exists. `buildSrc`, copied configuration blocks, and empty convention plugins are prohibited.

### Quality tool authority

`./gradlew check` is the canonical local gate and aggregates the following without baselines:

| Concern | Canonical mechanism |
| --- | --- |
| Compile | Kotlin compiler with warnings as errors; explicit API in library modules |
| Format | `org.jlleitschuh.gradle.ktlint` 14.2.0 with ktlint 1.8.0 and root `.editorconfig` |
| Static analysis | `dev.detekt` 2.0.0-alpha.6 with one root config and type resolution |
| Host tests | `kotlin.test` API backed by JUnit Jupiter |
| Android device/UI tests | AndroidX Test runner and Compose UI test APIs |
| Android correctness | AGP Android lint with warnings as errors, dependency checks, and no baseline |
| Documentation | a cross-platform Gradle task that validates internal links, rule references, ADR/waiver shapes, and work-package references |
| Dependencies | Gradle dependency locking and dependency verification; no dynamic or changing versions |

ktlint owns formatting. detekt's formatting wrapper and Spotless are prohibited. JUnit Jupiter is the only host test engine; JUnit 4 is allowed only where an AndroidX device/test API requires its runner contract, not as a competing host test stack.

detekt 2.0.0-alpha.6 is a deliberate pre-release exception because detekt 1.23.8 predates AGP 9 built-in Kotlin support. It is pinned, must have no baseline, and must be replaced by the first compatible stable detekt 2.x release in a focused dependency PR. This exception authorizes only the tool version, not suppressions or unstable production APIs.

### Initial creation order

Modules and build logic are created only with their first executable responsibility:

1. `P0-02`: create the Gradle root and launchable `:app:android` shell. Its placeholder Compose content contains no editor behavior and is removed when real presentation exists.
2. `P0-03`: add tested build logic only for the canonical quality aggregation and documentation validation that now have real consumers.
3. `P0-05`: add `:quality:architecture-rules` with the first executable custom architecture rule.
4. `P1-01`: add `:core:domain` with the first domain values and invariants.
5. `P1-02`: add `:core:pixel-engine` with the first bounded pixel surface.
6. `P1-03`: add `:core:application` with the command gateway and typed results.
7. `P1-06`: add `:presentation:compose` with the first touch-to-command vertical slice and reduce `:app:android` to composition.
8. Later modules are created in the work package that introduces their first documented responsibility.

Core modules begin as Kotlin/JVM modules restricted to multiplatform-compatible APIs. Kotlin Multiplatform, desktop targets, serialization, persistence, dependency injection frameworks, code generation, OpenAPI, and automation adapters remain absent until their existing decision gates.

### Upgrade policy

- Every version is exact. Automated update PRs may propose versions but never merge them automatically.
- A toolchain, compiler, Compose BOM, target SDK, or static-analysis upgrade is isolated from feature work and runs the complete canonical gate.
- A major tool upgrade, minimum SDK change, language-mode change, or gate-semantics change requires an ADR. A compatible patch update does not.
- Release notes and compatibility tables from primary sources are required evidence.
- When a selected version is unavailable or incompatible, downgrade is not implicit. Record the failure, update this ADR or supersede it, then keep one version path.

## Rejected alternatives

### Generate the project from Android Studio defaults

Rejected because wizard defaults vary by Android Studio release and would make identity, version, and module decisions implicit and difficult to review.

### Stay on AGP 8.x to match the installed API 35 SDK

Rejected because the SDK is replaceable local state, while Compose 1.12 requires compile SDK 37 and AGP 9. Selecting an older application contract to avoid one setup step would create an immediate upgrade branch.

### Run Gradle on JBR 25

Rejected because JDK 21 is already installed, is supported by Gradle 9.4.1, and provides a narrower reproducible runtime. Android Studio's current JBR does not become the project contract merely because it launches the IDE.

### Apply `org.jetbrains.kotlin.android`

Rejected because AGP 9 provides Kotlin directly and official migration guidance removes the plugin. Opting out would retain a path AGP 10 plans to remove.

### Start with Kotlin Multiplatform or desktop

Rejected because Android is the current product target. Kotlin/JVM core boundaries preserve portability without adding empty source sets, target-specific plugins, packaging, and test matrices before a second target exists.

### Use Spotless or detekt formatting

Rejected because direct ktlint provides the required canonical Kotlin formatter with fewer ownership layers. A second formatter would create competing textual authorities.

### Use stable detekt 1.23.8

Rejected because it is aligned with the pre-AGP-9 toolchain and does not provide the accepted built-in Kotlin integration. The pinned 2.0 alpha risk is explicit, contained to build tooling, and executable in `check`.

### Create all reserved modules immediately

Rejected because empty modules make future boundaries look decided without executable responsibilities and violate the project layout's creation rule.

## Consequences

### Benefits

- Scaffold inputs are reviewable, reproducible, and independent of IDE defaults.
- Android and future JVM modules use one Kotlin compiler line.
- One catalog, formatter, static analyzer, test route per environment, and aggregate check remove tool ambiguity.
- The module graph grows only with working code and executable enforcement.

### Costs and risks

- API 37 and Build Tools 36.0.0 must be installed before the first Android build.
- The detekt plugin is pre-release and may require an isolated upgrade sooner than other tools.
- JVM target 17 does not allow Java 21 bytecode features even though the build runs on JDK 21.
- A future non-Android target requires an ADR and a focused Kotlin Multiplatform migration.

## Enforcement impact

- `P0-02` must create wrapper 9.4.1, the version catalog, repository policy, Android API levels, built-in Kotlin, Compose compiler/BOM, toolchains, and the launchable app shell.
- `P0-03` must add ktlint, detekt, lint strictness, test engines, dependency controls, documentation validation, and the aggregate `check` with intentional-failure evidence.
- `P0-04` must run the same wrapper and `check` on JDK 21 in CI.
- `P0-05` must add the first architecture-rule module rather than an empty placeholder.
- Review and automated checks must reject forbidden plugins, repositories, dynamic versions, baselines, and unapproved module names.

## Migration and rollback

There is no production code or prior Gradle build to migrate. `P0-02` implements this decision directly.

If a selected tool cannot produce the initial clean build, stop the scaffold change, attach the exact incompatibility evidence to Issue #6, and supersede this ADR before selecting another matrix. Do not keep both configurations, opt-out flags, or alternate wrapper scripts. Rollback removes the failed scaffold as one Issue branch; it does not weaken gates on `main`.

## Related

- Issue: #5
- PR: #11
- Supersedes: none
- Superseded by: none

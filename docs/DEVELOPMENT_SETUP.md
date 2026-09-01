# Development Setup

Status: normative

This is the single setup path for the Android build established by [ADR 0001](adr/0001-initial-build-toolchain.md).

## Prerequisites

- The Gradle Wrapper CLI is the canonical build path; Android Studio is optional and must explicitly support Android Gradle plugin 9.4.0
- JDK 21 for the Gradle runtime and project toolchain
- Android SDK Platform 37
- Android SDK Build Tools 36.0.0
- Android Emulator with a Pixel 8 Pro, API 35 Google Play x86_64 AVD named `Pixel_8_Pro_API_35` for the fixed M0 smoke profile

Do not commit a machine-specific JDK path or Android SDK path. `JAVA_HOME`, a compatible Android Studio's Gradle JDK setting, and CI must all select JDK 21. Android Studio may continue to run the IDE itself on its bundled JBR.

As of 2026-09-02, the official stable compatibility table lists Android Studio Quail 3 / 2026.1.3 as supporting AGP only through 9.3. The installed `AI-261.26222.65.2613.15948027` build therefore must not be used to sync or run this AGP 9.4.0 project. Until a stable Android Studio release explicitly lists AGP 9.4 support, use the wrapper and `adb` workflow below. A compatible preview IDE may be installed alongside the stable IDE for optional evaluation, but it is not the canonical path until its exact build passes sync and the canonical gate.

When using a compatible Android Studio release, open **Settings > Build, Execution, Deployment > Build Tools > Gradle** and select JDK 21 or the `JAVA_HOME` macro. Use **Settings > Languages & Frameworks > Android SDK** to install SDK Platform 37 and Build Tools 36.0.0. In **Device Manager**, create the fixed smoke AVD from the Pixel 8 Pro hardware profile and the API 35 Google Play x86_64 image, then name it `Pixel_8_Pro_API_35`.

## Verify the toolchain

From the repository root on Windows:

```powershell
.\gradlew.bat --version
.\gradlew.bat projects
```

`--version` must report Gradle 9.7.1 running on JVM 21. The settings script rejects another Java major version before configuring the project.

On macOS or Linux, use the same wrapper through `./gradlew`.

## Build and run the shell

Build the canonical debug artifact:

```powershell
.\gradlew.bat :app:android:assembleDebug
```

To launch it from the canonical command-line path with a configured device and `adb` on `PATH`:

```powershell
.\gradlew.bat :app:android:installDebug
adb shell am start -W -n io.github.hideyukimori.nenepixel/.MainActivity
```

With a compatible Android Studio release, the equivalent optional path is to select the `app.android` configuration and a device, then choose **Run**. The expected smoke result is one activity displaying `NENE-PIXEL` with no editor or domain behavior.

The M0 reproducibility smoke profile is a Pixel 8 Pro AVD running API 35 / Android 15 at 1344 x 2992. A physical or newer virtual device is useful additional coverage but does not replace this fixed profile when re-running the M0 proof.

The verified fresh-clone transcript, including cold-launch and clean-tree evidence, is recorded in [Fresh-clone and M0 Exit Proof](quality/FRESH_CLONE_PROOF.md).

## Run the canonical quality gate

Run every required local check through one command:

```powershell
.\gradlew.bat check
```

`check` is the only merge-gate authority. It compiles project-owned Kotlin with warnings as errors, verifies formatting, runs detekt and Android lint with warnings as errors, runs architecture-rule and build-logic unit tests, validates module dependencies and suppression waivers, validates documentation and baseline policy, and checks the committed dependency locks and SHA-256 verification metadata.

When formatting is the only failure, apply the one authoritative formatter and then rerun the complete gate:

```powershell
.\gradlew.bat ktlintFormat :app:android:ktlintFormat :quality:architecture-rules:ktlintFormat :build-logic:ktlintFormat
.\gradlew.bat check
```

The following narrow commands are diagnostic tools, not substitutes for `check`:

```powershell
.\gradlew.bat :app:android:compileDebugKotlin
.\gradlew.bat :app:android:ktlintMainSourceSetCheck
.\gradlew.bat :app:android:detekt
.\gradlew.bat validateArchitecture
.\gradlew.bat :quality:architecture-rules:test
.\gradlew.bat :build-logic:test
.\gradlew.bat :app:android:lintDebug
.\gradlew.bat validateDocumentation validateNoBaselines
```

Do not create lint or detekt baselines and do not exclude project-owned source sets. Initial intentional-failure evidence is recorded in [Initial Gate Proofs](quality/INITIAL_GATE_PROOFS.md) and [Architecture Gate Proofs](quality/ARCHITECTURE_GATE_PROOFS.md).

## Update dependencies reproducibly

Dependency or plugin upgrades belong in a focused change. Edit exact versions only in `gradle/libs.versions.toml`, review primary-source release notes, and regenerate all dependency evidence with:

```powershell
.\gradlew.bat check --write-locks --write-verification-metadata sha256 --no-configuration-cache
.\gradlew.bat check
```

Review every lock and checksum change before committing it. The first command is intentionally exceptional: normal builds must never write dependency state.

For platform-specific tools such as AAPT2, verification metadata must cover every supported development and CI host: Windows, Linux, and macOS. Prefer running the exceptional regeneration command once on each supported operating system and merging only the resulting checksum entries. When a host is unavailable, download the exact classifier artifact from its canonical repository, independently compute SHA-256, and record the artifact URL and hash in the Issue or PR. A normal strict-verification build on every available host, including Linux CI, remains required evidence.

## Local-only files

Android Studio may create `.idea/`, `.gradle/`, `.kotlin/`, `local.properties`, module `build/` directories, and `*.iml`. These are local state and must remain ignored. Dependency/plugin versions belong only in `gradle/libs.versions.toml`; local files are never a second version authority.

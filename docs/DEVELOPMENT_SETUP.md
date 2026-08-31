# Development Setup

Status: normative

This is the single setup path for the Android build established by [ADR 0001](adr/0001-initial-build-toolchain.md).

## Prerequisites

- Android Studio compatible with Android Gradle plugin 9.3.2
- JDK 21 for the Gradle runtime and project toolchain
- Android SDK Platform 37
- Android SDK Build Tools 36.0.0

Do not commit a machine-specific JDK path or Android SDK path. `JAVA_HOME`, Android Studio's Gradle JDK setting, and CI must all select JDK 21. Android Studio may continue to run the IDE itself on its bundled JBR.

In Android Studio, open **Settings > Build, Execution, Deployment > Build Tools > Gradle** and select JDK 21 or the `JAVA_HOME` macro. Use **Settings > Languages & Frameworks > Android SDK** to install SDK Platform 37 and Build Tools 36.0.0.

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

To launch it, select the `app.android` configuration and a device in Android Studio, then choose **Run**. The expected smoke result is one activity displaying `NENE-PIXEL` with no editor or domain behavior.

With a configured device and `adb` on `PATH`, the equivalent command-line path is:

```powershell
.\gradlew.bat :app:android:installDebug
adb shell am start -n io.github.hideyukimori.nenepixel/.MainActivity
```

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

## Local-only files

Android Studio may create `.idea/`, `.gradle/`, `.kotlin/`, `local.properties`, module `build/` directories, and `*.iml`. These are local state and must remain ignored. Dependency/plugin versions belong only in `gradle/libs.versions.toml`; local files are never a second version authority.

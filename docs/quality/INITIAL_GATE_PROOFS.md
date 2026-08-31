# Initial Gate Proofs

Status: verification evidence

- Date: 2026-09-01
- Issue: #7
- Work package: `P0-03`
- Environment: Windows, JDK 21, Gradle Wrapper 9.7.1, Android SDK 37

Each proof below introduced one temporary violation, executed the owning Gradle task, observed a non-zero exit, and immediately restored the source. No proof mutation, baseline, suppression, or exclusion remains in the repository.

| Gate | Command | Temporary violation | Observed failure |
| --- | --- | --- | --- |
| Kotlin compiler | `.\gradlew.bat :app:android:compileDebugKotlin --rerun-tasks --no-build-cache --no-configuration-cache` | Called a function marked `Deprecated` | Exit 1; `warnings found and -Werror specified` |
| Formatter | `.\gradlew.bat :app:android:ktlintMainSourceSetCheck --rerun-tasks --no-build-cache --no-configuration-cache` | Removed required spacing around `=` | Exit 1; `Missing spacing around "="` |
| Static analysis | `.\gradlew.bat :app:android:detekt --rerun-tasks --no-build-cache --no-configuration-cache` | Added a function nested beyond the configured maximum | Exit 1; `NestedBlockDepth` and `MagicNumber` findings |
| Unit test | `.\gradlew.bat :build-logic:test --rerun-tasks --no-build-cache --no-configuration-cache` | Replaced a valid-document assertion with an impossible expected violation | Exit 1; six tests ran and one failed |
| Android lint | `.\gradlew.bat :app:android:lintDebug --rerun-tasks --no-build-cache --no-configuration-cache` | Used an API 27 theme attribute while minimum SDK is 26 | Exit 1; `NewApi` error |
| Documentation | `.\gradlew.bat validateDocumentation --rerun-tasks --no-build-cache --no-configuration-cache` | Added a local Markdown link to a missing file | Exit 1; `broken local link` |

After all temporary mutations were removed, `.\gradlew.bat check --no-configuration-cache` completed successfully with all aggregate gates active. The normal canonical command remains `.\gradlew.bat check`; cache-disabling flags above exist only to force each proof task to execute.

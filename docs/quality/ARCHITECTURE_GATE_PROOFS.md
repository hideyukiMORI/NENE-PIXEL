# Architecture Gate Proofs

Status: verified on 2026-09-01 for `P0-05` / Issue #9 and extended by `P1-01` / Issue #18

These checks prove that each initial architecture rule is connected to the real Gradle `check` graph. Every violation was introduced temporarily, observed failing with the expected Rule ID, removed, and followed by a complete green build. The committed rule tests retain separate compliant and failing fixtures.

## Executable coverage

| Rule | Compliant fixture | Intentional repository violation | Observed gate |
| --- | --- | --- | --- |
| `ARC-002` | canonical graph in `ModuleArchitectureValidatorTest` | `:quality:architecture-rules` declared `implementation` on `:app:android` | `validateArchitecture` failed: `ARC-002 prohibits implementation dependency` |
| `ARC-003` | platform-free and stdlib-only domain graph in `ModuleArchitectureValidatorTest` | temporary `:core:domain` declared `androidx.compose.runtime:runtime`; P1-01 separately declared `org.jetbrains:annotations` as a production dependency | `validateArchitecture` rejected both with the applicable `ARC-003` message |
| `KOT-011` | `compliant-name.fixture`, including precise `Factory` and conditional `Processor`/`Data` names | temporary `LayerManager` type | `:app:android:detekt` failed with `ForbiddenGenericName` and `KOT-011` |
| `KOT-022` | unsuppressed and exact-scope active-waiver fixtures | temporary declaration-level `@Suppress` without a waiver comment | `validateArchitecture` failed: `KOT-022 suppression requires an adjacent active waiver comment` |

Additional focused tests reject an `ARC-002` cycle, a forbidden package segment, file-level suppression, and waivers scoped to another file or declaration. A valid XML fixture proves that an adjacent element waiver is matched to its `android:id`.

## Commands used

The negative proofs used `--rerun-tasks` and disabled the configuration cache so stale task state could not hide the temporary change:

```powershell
.\gradlew.bat validateArchitecture --rerun-tasks --no-configuration-cache
.\gradlew.bat :app:android:detekt --rerun-tasks --no-configuration-cache
```

After every temporary violation was removed, the canonical verification was:

```powershell
.\gradlew.bat check
.\gradlew.bat :app:android:assembleDebug
git diff --check
```

## False-positive boundary

The naming rule uses Kotlin syntax through a custom detekt extension rather than scanning arbitrary text. It rejects only unconditional generic names from `KOT-011`. `Processor`, `Data`, `common`, and `base` are conditional in the normative rule and deliberately remain semantic review items. The suppression validator scans only Kotlin/Gradle Kotlin/XML source, skips generated/cache directories, requires an exact active waiver, and never accepts a file-level suppression.

The custom extension follows detekt's documented `RuleSetProvider` service-loading model and is compiled in a separate pure Kotlin module: <https://detekt.dev/docs/introduction/extensions/>.

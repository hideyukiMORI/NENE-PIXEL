# Fresh-clone and M0 Exit Proof

Status: verified on 2026-09-01 for `P0-06` / Issue #10

This proof used public `main` commit `0a9870f63899baba071ac4190b62262aff3cf29f`, a new checkout under the operating-system temporary directory, and a separate initially empty Gradle user home. It did not reuse the development checkout, its project `.gradle` directory, its build outputs, or a committed machine path.

## Prerequisites observed

| Item | Observed value |
| --- | --- |
| Host | Windows 11, amd64 |
| Gradle runtime | JBR 21.0.11 |
| Wrapper | Gradle 9.7.1, downloaded by the fresh checkout |
| Android compile SDK | Platform 37.0 |
| Android build tools | 36.0.0 |
| Smoke device | Pixel 8 Pro AVD, Android 15 / API 35, 1344 x 2992 |
| Machine-specific project file | no `local.properties`, `.idea`, or `*.iml` tracked or required |

`JAVA_HOME` and `ANDROID_HOME` selected the documented installations outside the repository. No absolute checkout, JDK, or SDK path was written to a project file.

## Fresh build

The checkout began at the expected commit with zero status lines. The empty Gradle user home fetched the declared wrapper before this command ran:

```powershell
.\gradlew.bat check :app:android:assembleDebug --no-configuration-cache --warning-mode all
```

Result: `BUILD SUCCESSFUL in 3m 49s`; 91 tasks were considered, with 88 executed and three same-build cache reuses. The run covered compiler warnings-as-errors, formatting, detekt, architecture and waiver validation, focused tests, Android lint, dependency locks, and SHA-256 dependency verification.

The debug artifact was 11,515,905 bytes with SHA-256 `8080BDF9AA77E2634D82CD003ADAAF693B972B4D2BDD126CF7C2CB946EA3FB7F`.

Post-build repository checks reported:

```text
STATUS_LINES=0
WORKTREE_DIFF_EXIT=0
INDEX_DIFF_EXIT=0
TRACKED_LOCAL_STATE=0
SECRET_PATTERN_HITS=0
```

Build outputs remained ignored, and dependency verification created no tracked drift.

## Emulator smoke

The fixed AVD was cold-booted without loading or saving a snapshot. The fresh checkout then ran:

```powershell
.\gradlew.bat :app:android:installDebug --no-configuration-cache
adb shell am start -W -n io.github.hideyukimori.nenepixel/.MainActivity
```

Observed evidence:

```text
Installed on 1 device.
Status: ok
LaunchState: COLD
Activity: io.github.hideyukimori.nenepixel/.MainActivity
TotalTime: 2406
topResumedActivity: io.github.hideyukimori.nenepixel/.MainActivity
state=RESUMED
UI_TEXT_NENE_PIXEL=True
FATAL_LOG_LINES=0
```

This establishes that the shell installed, launched, became the focused resumed activity, rendered its expected text, and emitted no fatal startup exception.

## M0 exit review

| Exit criterion | Evidence | Result |
| --- | --- | --- |
| Fresh clone builds with documented JDK 21 commands | isolated build above | pass |
| Canonical `check` succeeds locally and in CI | isolated build above; [main run 33417773004](https://github.com/hideyukiMORI/NENE-PIXEL/actions/runs/33417773004) | pass |
| Formatting, compiler-warning, test, and documentation violations fail | [Initial Gate Proofs](INITIAL_GATE_PROOFS.md) | pass |
| Forbidden import is physically rejected | temporary `android.app.Activity` import in the pure Kotlin quality module failed `compileKotlin` with `Unresolved reference 'android'`; mutation removed | pass |
| Initial architecture violations fail | [Architecture Gate Proofs](ARCHITECTURE_GATE_PROOFS.md) | pass |
| Android shell launches on the fixed profile | cold-launch evidence above | pass |
| Failing required CI prevents merge to `main` | [CI and Main Protection Proofs](CI_PROOFS.md) and active [main-protection ruleset](https://github.com/hideyukiMORI/NENE-PIXEL/rules/21941071) | pass |
| M0 has zero baselines and zero waivers | canonical validation; waiver index empty | pass |

All M0 deliverables and exit criteria are satisfied. Gate A is open for the planned M1 vertical slice; this does not authorize alternate state, mutation, build, or API paths.

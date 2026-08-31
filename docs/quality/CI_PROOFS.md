# CI and Main Protection Proofs

Status: verified

- Date: 2026-09-01
- Issue: #8
- Work package: `P0-04`
- Required check: `quality`

The proof procedure was intentionally limited to the Issue branch: establish a green run, activate the ruleset, push one failing unit-test assertion, confirm that `main` merge is blocked, restore the assertion, and obtain a final green run. The squash merge contains only the restored source.

## Positive CI proof

- Candidate revision: `98fc8c6`
- Run: [33411489659](https://github.com/hideyukiMORI/NENE-PIXEL/actions/runs/33411489659)
- Job: [quality](https://github.com/hideyukiMORI/NENE-PIXEL/actions/runs/33411489659/job/99551875467)
- Result: success in 4 minutes 44 seconds
- Executed contract: JDK 21, Android SDK Platform 37.0, `./gradlew check :app:android:assembleDebug --stacktrace`

The run used the committed wrapper, dependency locks, and SHA-256 verification metadata from an otherwise fresh GitHub-hosted Ubuntu 24.04 runner. Pull-request cache access was read-only.

## Blocked red-check proof

- Intentional failing revision: `8082032`
- Run: [33412050986](https://github.com/hideyukiMORI/NENE-PIXEL/actions/runs/33412050986)
- Job: [quality](https://github.com/hideyukiMORI/NENE-PIXEL/actions/runs/33412050986/job/99553727036)
- Injected violation: the valid-document unit test expected an impossible violation
- Result: failure; six tests completed and one failed in `DocumentationValidatorTest`
- Protection result: PR #14 reported `mergeStateStatus: BLOCKED` while `quality` reported `conclusion: FAILURE`

The assertion was restored immediately after the read-back. No failing assertion, suppression, baseline, or bypass remains.

## Repository ruleset read-back

- Ruleset: [main-protection](https://github.com/hideyukiMORI/NENE-PIXEL/rules/21941071)
- ID: `21941071`
- Enforcement: `active`
- Target: `~DEFAULT_BRANCH`
- Bypass actors: none; API read-back reported `current_user_can_bypass: never`
- Required status: `quality` with strict up-to-date policy
- Pull request: required; unresolved review threads block merge
- Merge method: squash only
- Destructive updates: branch deletion and non-fast-forward pushes are blocked
- Workflow token defaults: `contents: read`; workflows cannot approve pull requests

The final restored revision must receive a new successful `quality` result before squash merge. Repository configuration was read back through the GitHub REST API after creation; no UI-only assumption is part of this evidence.

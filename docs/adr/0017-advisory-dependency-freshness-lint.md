# ADR 0017: Advisory dependency-freshness lint and a deliberate refresh cadence

- Status: accepted
- Date: 2026-09-11
- Issue: #93
- Affected rules: `QLT-001`, `QLT-003`, `QLT-005`

## Context

ADR 0001 adopted AGP Android lint with warnings as errors, dependency checks, and no baseline. That
configuration is correct for checks whose input is the repository. Two lint checks have a different
input: `GradleDependency` (Google Maven artifacts) and `NewerVersionAvailable` (Maven Central
artifacts) compare every version-catalog literal with the newest version published at the moment
the check runs. They query a remote index on each run and report nothing under `--offline`. With
`warningsAsErrors`, their warning becomes a merge-blocking error.

Evidence that this makes the merge gate depend on the calendar rather than on the change:

- 2026-09-02: `main` failed the required `quality` job when AGP 9.4.0 shipped while the catalog
  pinned 9.3.2. Issue #48 / PR #49 restored CI with a focused toolchain update.
- 2026-09-10 20:36 JST: a clean `main` worktree (`3a4ea65`) failed `:app:android:lintDebug` with network
  access (exit 1, three errors, no other lint finding) because `androidx.benchmark` 1.5.0 and Compose BOM
  2026.09.00 had shipped. PR #91 failed three times on the same errors plus two
  `NewerVersionAvailable` findings for `kotlinx-coroutines`. Issue #92 / PR #94 restored CI.
- The Compose BOM ships monthly, so the failure recurs at least monthly without any source change.

Three project rules conflict with the current severity. `QLT-003` requires local and CI checks to be
identical, but the local `--offline` gate cannot reproduce these findings. `DEVELOPMENT_WORKFLOW.md`
requires dependency updates to stay in focused PRs, but the gate forces the newest upstream release
into whichever PR happens to be open. `QLT-005` requires an ADR for a severity change, which is why
this decision is recorded here rather than applied silently.

## Decision

`GradleDependency` and `NewerVersionAvailable` are `informational` in the shared Android lint
convention (`build-logic` `AndroidConvention.kt`). They still run in every `lintDebug` invocation and
still appear in the lint report; they no longer fail the build. Every other lint check keeps its
severity, and `abortOnError`, `checkDependencies`, `checkReleaseBuilds`, and `warningsAsErrors` stay
enabled. No baseline, `lint.xml`, suppression, or waiver is introduced.

Catalog freshness is enforced by a deliberate cadence instead of by unrelated PRs:

- The maintainer refreshes the version catalog through one focused `build(toolchain)` Issue and PR,
  in the shape of #48 and #92: catalog literals, locks regenerated with the documented exceptional
  command, verification metadata appended, the affected ADR updated with a dated paragraph, and
  historical evidence documents left unchanged.
- The cadence is at least monthly, aligned with the Compose BOM release, or earlier when a needed fix
  ships. A network-enabled `:app:android:lintDebug` report is the input that lists the outdated
  literals.
- A newly introduced dependency is declared at the newest published release in the PR that
  introduces it (precedent: `kotlinx-coroutines` 1.11.0 in #86).

`QUALITY_GATES.md` records the carve-out under `QLT-001` and the refresh cadence under a new
Dependency refresh section. ADR 0001 remains the toolchain authority; only the severity of these two
checks is superseded.

## Rejected alternatives

### Keep both checks as errors and refresh on demand

Rejected because the gate then fails on Google's release calendar, blocks unrelated PRs, cannot be
reproduced offline, and forces dependency updates into feature PRs. Two incidents in eight days
(#48, #92) each cost a focused recovery PR before any feature could merge.

### Disable both checks

Rejected because the report is useful input for the refresh cadence. Informational severity keeps
the findings visible at zero gate cost; disabling them would hide the only automated freshness
signal the project has.

### Baseline or `lint.xml` path exclusion

Rejected by `QLT-002` and the waiver policy: baselines and file-wide exclusions are prohibited, and
a waiver is a temporary exception for one declaration, not a policy for a calendar-driven check.

### Run lint with `--offline` in CI

Rejected because it would silence the checks by accident rather than by decision, would still leave
the local and CI configurations different from the documented convention, and would make the
freshness signal disappear from the report.

### Automated dependency bot PRs

Not adopted now. The project has no bot integration, dependency verification requires reviewed
SHA-256 metadata on every change, and a focused maintainer PR already satisfies the cadence. A bot
can be added later without changing this decision.

## Consequences

### Benefits

- `main` stays green when upstream releases ship; a PR fails only for its own changes.
- Local `check` and CI `check` agree on the outcome of these two checks (`QLT-003`).
- Dependency updates happen in focused, reviewed PRs on the project's schedule, not upstream's.
- The lint report keeps listing outdated literals, so the refresh cadence has an automated input.

### Costs and risks

- Freshness is now a maintainer obligation. If the cadence lapses, the catalog ages silently; the
  informational findings in the report are the mitigation.
- Two lint check IDs are named in build logic; if AGP renames them the override becomes inert. The
  build-logic functional test asserts the configured set so a rename is visible.

## Enforcement impact

- `build-logic`: `AndroidConvention.kt` adds the two IDs to `lint.informational`; the shared
  functional test asserts that exactly these two IDs are informational and that
  `warningsAsErrors`, `abortOnError`, `checkDependencies`, and `checkReleaseBuilds` remain `true`.
- Intentional-failure and restored-green proof, recorded in PR #95. An unused catalog
  entry `androidx.collection:collection` 1.4.0 (newest 1.6.0) was added temporarily so that lint
  resolution stays inside the locked graph. With network access, `:app:android:lintDebug` on this
  branch reported the finding as a hint and passed (2026-09-11 00:57 JST, exit 0,
  `0 errors, 0 warnings, 1 hint`); with the `informational` line removed it failed
  (00:59 JST, exit 1, `Lint found 1 error`, `GradleDependency`). The entry and the line were then
  restored and the catalog diff is empty. The original incident is the same failure on main
  `3a4ea65` (2026-09-10 20:36 JST, exit 1, three errors; CI run 34391610354).
- `docs/QUALITY_GATES.md`: `QLT-001` carve-out sentence and the Dependency refresh section.
- `docs/adr/0001-initial-build-toolchain.md`: dated paragraph and `Superseded by` entry limited to
  this severity.
- No change to CI workflow, detekt, ktlint, dependency locking, or verification metadata.

## Migration and rollback

No code or data migrates. The change is two lint severities in one convention file. Rollback
removes the two IDs from `lint.informational` and the test assertion, and reverts the
`QUALITY_GATES.md` and ADR 0001 wording; the freshness cadence then becomes the merge gate again,
with the incidents above as the known cost.

## Related

- Issue: #93
- Incidents: #48, #92
- PR: #95
- Builds on: ADR 0001 initial build toolchain
- Supersedes: ADR 0001 for the severity of `GradleDependency` and `NewerVersionAvailable` only
- Superseded by: none

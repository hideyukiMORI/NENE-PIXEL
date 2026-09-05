# Development Workflow

Status: normative

The workflow is part of the constraint system. A technically correct patch created outside the traceable workflow is incomplete.

## Work unit

Every production-code, build, dependency, policy, or user-visible documentation change MUST start from one focused Issue.

The Issue must state:

- problem and evidence
- intended outcome
- rules and modules affected
- acceptance criteria
- verification plan
- explicitly excluded work

Exploration may happen without an Issue when it is read-only and produces no repository change.

## Standard flow

1. Create or select one focused Issue.
2. Read the canonical documents and relevant ADRs/waivers.
3. Identify the canonical implementation path before editing.
4. Create a branch from current `main`.
5. Change policy/ADR first when the accepted design changes.
6. Implement the smallest complete change.
7. Add or update tests and generated contracts.
8. Record the change scope and verification triggers under QLT-011 through QLT-016, then run the narrowest useful checks during development.
9. Complete affected narrow checks for handoff/review preparation; reserve the full canonical suite for the final PR merge candidate under QLT-011.
10. Self-review against every affected rule ID.
11. Open a PR linked to the Issue.
12. Merge only after required CI `quality` passes canonical `./gradlew check :app:android:assembleDebug` for the current merge candidate. Do not duplicate that full run locally. Changed head/base must satisfy branch protection again.
13. Return local `main` to a clean, synchronized state.

## Continuous integration

Verification frequency, device-measurement triggers, artifact reuse, bounded experiments, and
prospective performance decisions MUST follow the [verification execution policy](QUALITY_GATES.md#verification-execution-policy).
The Issue verification plan MUST be fixed before expensive execution. Review MUST reject unexplained
forced rebuilds, repeated successful checks, untriggered device runs, and evidence collected with a
protocol/harness mismatch. A local documentation update is not permission to run a new benchmark.
These review obligations supplement the unchanged automated canonical gate.

Performance policy changes MUST preserve historical verdicts and give revised protocols distinct
identities. A proposed implementation or tool replacement is not approved merely because a policy
permits evaluating it. Track necessary harness corrections in the owning Issue before collection.

The normative full-suite trigger is the final merge-ready PR candidate under QLT-011. The `quality`
job uses JDK 21 and the committed Gradle Wrapper to execute canonical `check` plus
`:app:android:assembleDebug`; it does not recreate quality policy in workflow shell steps.
The workflow targets `main` pull requests on `opened`, `reopened`, `synchronize`, `edited`,
`ready_for_review`, and `converted_to_draft`. The required `quality` job fails closed before toolchain
setup while the PR is draft; it is never skipped or replaced by a successful no-op. A non-draft
event proceeds through the complete canonical body. Keep unfinished work in draft, convert it back
to draft before iterative pushes, and use `ready_for_review` only when the Issue has one final
candidate. Non-draft final fixes and branch updates emit `synchronize` and require fresh quality.
Every non-draft `edited` event reruns quality so base retargeting cannot reuse a result for a different
merge candidate; non-base edits intentionally take the same safe path instead of a successful no-op.
The default pull-request checkout uses GitHub's merge ref, so the canonical body validates the PR
head combined with its current base rather than testing the head in isolation.
The active strict up-to-date ruleset blocks merge after the base advances until the branch is updated
and the resulting merge candidate passes again. No post-merge `main` push or manual-dispatch full run
duplicates or bypasses this gate. Handoff does not require a local full run, and a valid required CI
result MUST NOT be duplicated locally as a routine precaution.

External actions are limited to official checkout, Java setup, and Gradle setup actions and are pinned to immutable commit SHAs. The workflow token has only `contents: read`. Gradle uses its open-source basic cache provider in read-only mode; a `main` full run is not reintroduced merely to populate cache. Concurrency is scoped to the workflow and pull request, and a newer event cancels stale work for the same PR, including when the PR is converted back to draft.

The required status-check context is exactly `quality`. The active repository ruleset for the default branch requires a pull request, an up-to-date successful `quality` result, and resolved review threads; it rejects force pushes and branch deletion. Squash merge is the only merge method, and the resulting commit subject must follow the commit convention below. Ruleset state is repository configuration and must be read back through the GitHub API when changed. Initial positive and negative evidence is recorded in [CI and Main Protection Proofs](quality/CI_PROOFS.md).

## Branch and commit conventions

Branch format:

```text
<type>/<issue-number>-<short-kebab-summary>
```

Examples:

```text
docs/12-architecture-constitution
feat/41-apply-stroke-command
fix/73-flood-fill-bounds
build/88-detekt-architecture-rules
```

Commit format follows Conventional Commits:

```text
<type>(<optional-scope>): <Japanese description> (#<issue-number>)
```

Each commit should be coherent and pass the relevant narrow checks. A PR must not mix architecture migration, unrelated cleanup, dependency updates, and feature work.

## Decision policy

### ADR required

An ADR is required before changing:

- a constitutional rule or dependency direction
- module boundaries or state ownership
- canonical command/action flow
- public module API
- project-file schema strategy
- dependency injection/composition strategy
- concurrency or threading model
- formatter, linter, test framework, serialization library, or major Gradle plugin
- supported platform/target
- security, privacy, recovery, or destructive-operation behavior
- a rule enforcement mechanism when alternatives have meaningful trade-offs

Follow `docs/adr/README.md`.

### ADR not required

An ADR is normally unnecessary for:

- implementation details fully inside an accepted boundary
- bug fixes that restore documented behavior
- tests and documentation that do not change policy
- small dependency updates with no contract or tool-policy change

## Rule change protocol

Changing a MUST/MUST NOT rule requires:

1. Issue describing the conflict and evidence
2. accepted ADR
3. updated canonical documents
4. enforcement update
5. code migration
6. regression tests proving the old competing path is rejected

These should land together. If migration must be staged, create a time-bounded waiver for the temporary dual state.

## Dependency policy

A new runtime dependency is accepted only when:

- the capability is concrete and currently required
- implementing it locally would be riskier or less maintainable
- license and maintenance status are acceptable
- API usage can be isolated behind the correct boundary
- transitive cost and Android size/startup impact are understood
- deterministic tests do not require uncontrolled global state
- version is locked/reproducible according to Gradle policy
- an ADR exists when required above

Convenience alone is insufficient. Dependencies must not leak their types into core public APIs unless the relevant ADR explicitly adopts that type as a contract.

## PR contract

Every PR description must contain:

```text
Issue:
Purpose:
Canonical path used:
Rule IDs:
Behavior/schema changes:
Verification:
Performance evidence (if relevant):
Waivers: none | WVR-NNNN
Remaining risks:
```

Review must reject:

- a second path for an existing meaning
- undocumented state ownership
- weakened or skipped gates
- unbounded suppression
- adapter-specific business logic
- manual generated-code edits
- public APIs added “for possible future use”
- unrelated cleanup hidden in feature changes

## Documentation policy

Important decisions must not remain only in chat, Issue comments, commit messages, or an AI transcript.

- Long-lived invariants belong in canonical documents.
- Trade-off decisions belong in ADRs.
- Temporary deviations belong in waivers.
- Current task state belongs in Issues/PRs.
- Code comments explain local non-obvious behavior, not project policy.

When concise tool configuration and documentation disagree, documentation is the decision record but the merge must remain blocked until both are aligned.

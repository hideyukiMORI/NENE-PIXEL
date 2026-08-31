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
8. Run the narrowest useful checks during development.
9. Run the complete canonical `./gradlew check` before handoff/PR.
10. Self-review against every affected rule ID.
11. Open a PR linked to the Issue.
12. Merge only after required gates pass.
13. Return local `main` to a clean, synchronized state.

## Continuous integration

Every pull request and every push to `main` runs the `quality` job in `.github/workflows/ci.yml`. The job uses JDK 21 and the committed Gradle Wrapper to execute the canonical `check` task plus `:app:android:assembleDebug`; it does not recreate quality policy in workflow shell steps.

External actions are limited to official checkout, Java setup, and Gradle setup actions and are pinned to immutable commit SHAs. The workflow token has only `contents: read`. Gradle uses its open-source basic cache provider: pull requests may restore cache entries but cannot write them, while trusted `main` runs may update them. Concurrency is scoped to the workflow and pull request or ref, and a newer run cancels the stale run for the same scope.

The required status-check context is exactly `quality`. The active repository ruleset for the default branch requires a pull request, an up-to-date successful `quality` result, and resolved review threads; it rejects force pushes and branch deletion. Ruleset state is repository configuration and must be read back through the GitHub API when changed. Initial positive and negative evidence is recorded in [CI and Main Protection Proofs](quality/CI_PROOFS.md).

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

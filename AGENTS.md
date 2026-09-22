# NENE-PIXEL Agent Guide

This file is the mandatory entry point for AI agents and automation working on NENE-PIXEL.

## Project identity

- Codename: `NENE-PIXEL`
- Product: Android-first pixel drawing tool
- Language: Kotlin
- Governing principle: one meaning, one canonical implementation path

## Required reading

Before proposing or changing production code, read all of the following:

1. `docs/PROJECT_CHARTER.md`
2. `docs/ARCHITECTURE_CONSTITUTION.md`
3. `docs/COMMAND_MODEL.md`
4. `docs/PROJECT_LAYOUT.md`
5. `docs/CODING_RULES.md`
6. `docs/QUALITY_GATES.md`
7. `docs/DEVELOPMENT_WORKFLOW.md`
8. `docs/DEVELOPMENT_SETUP.md`
9. `docs/GLOSSARY.md`
10. `docs/PROJECT_FORMAT_V1.md`
11. `docs/ROADMAP.md`
12. `docs/MILESTONES.md`
13. `docs/MVP_SCOPE.md`
14. `docs/DEVELOPMENT_PLAN.md`
15. `docs/API_STRATEGY.md`
16. `docs/PROJECT_FORMAT_V2.md`

Read the active GitHub Issue, relevant accepted ADRs, and active waivers after the documents above.

## Agent rules

- Do not invent a second implementation path because it is locally convenient.
- Do not add production code before an Issue defines the focused change.
- Do not change an architectural rule and its implementation in the opposite order. Update the governing document or add an ADR first, in the same focused change.
- Do not add dependencies, Gradle plugins, modules, serialization formats, or public APIs without recording the decision required by `docs/DEVELOPMENT_WORKFLOW.md`.
- Do not access document state directly from Compose UI, Android components, persistence adapters, or future MCP adapters.
- Do not bypass `DocumentCommand` for persistent/undoable mutations or `WorkspaceAction` for ephemeral editor-state changes.
- Do not expose mutable pixel storage outside the pixel-engine implementation boundary.
- Do not add `@Suppress`, lint baselines, detekt baselines, generated-code exclusions, or dependency exceptions without an approved waiver.
- Do not weaken a quality gate to make a change pass.
- Do not treat a Markdown checklist as current task state. GitHub Issues are the TODO authority.
- Do not introduce OpenAPI, HTTP, or MCP before the decision gate in `docs/API_STRATEGY.md` is satisfied.
- Do not commit secrets, local SDK paths, signing materials, generated build output, IDE state, or private user assets.
- Prefer the smallest change that fully follows the canonical path.
- MUST follow QLT-011 through QLT-018 in `docs/QUALITY_GATES.md` when planning, running, and reporting verification. Record the change scope and applicable checks before execution.
- MUST select checks from the diff. Name the changed behavior and the regression each check detects in the changed code or in a direct dependent or caller; do not run a check that cannot be explained that way. Documentation, comment, and rule changes need no app behavior tests. A hook or developer-tool change needs only that tool's short check.
- MUST reuse a passing result across assignee, work stage, appended documentation, and commit-identity changes while the verified paths and their relevant dependencies are unchanged. Rerun only for a relevant change, a failure, or a concrete unverified concern. Record the reused command, tree identity, and log location.
- MUST NOT run the full canonical check/build by default. Run it locally only when narrow checks cannot show the impact, such as a shared build-logic, toolchain, or cross-module contract change, and state the scope and reason in one line first without adding approval ceremony. Required CI `quality` on the Issue's final non-draft PR candidate is the single full gate; a valid result is not duplicated locally. Follow QLT-012 for invalidation.
- MUST NOT derail into unrelated failures. Fix failures the diff causes; record others with evidence in a separate Issue and continue. A check that passes on a rerun with unchanged inputs is failing: record the instability instead of the pass.
- MUST NOT add hooks or automation that run an unscoped full suite or force a rerun of verified work at push, review, or merge. Prefer existing target selection and recorded result reuse; do not build automatic test-selection infrastructure or disable verification wholesale.
- MUST NOT start device performance measurement, profile regeneration, or forced cold builds merely because a commit, documentation, or handoff changed. Apply the documented trigger and artifact-identity rules.
- MUST NOT treat historical measurement recipes as current authorization. Reconcile the Issue, accepted protocol, and executable harness before collecting new acceptance evidence; preserve historical FAIL/invalid results.

## Agent seats and model tiers

The design seat (the top model) owns adjudication, acceptance, scoring, the wording of instructions
and specifications, narrative, and decisions about protected material; it never delegates them.
Every background agent is created with an explicit model by the kind of work, accepted in
[ADR 0027](docs/adr/0027-agent-seat-model-tiers.md):

| Work | Model |
| --- | --- |
| Implementation (one task = one branch = one PR) and rework after a returned review | `opus` |
| Preparation: survey notes, inventories, output or PNG comparison, device checkout preparation, persona opinions, reconciling reports against evidence | `sonnet` |
| Pure mechanical work: mailbox moves, test-list matching, diff enumeration, zero-diff confirmation on protected material | `haiku` |

The tier test is who fixes the error: work the design seat verifies by comparison may go down a
tier. A preparation note is at most 300 lines with at most 15 open questions; persona seats are at
most four on `sonnet`, at most 150 lines each; from the second returned review on, an instruction
names one changed proposition and one rerun set. A procedure walked by a model twice becomes a
`tools/` script through a `chore` Issue, and the design seat runs scripts directly. An owner
instruction that changes operating policy is recorded as an ADR, a handoff section, and the first
line of the handoff's next actions in one focused change.

## Required completion report

Every completed change must report:

- Issue and rule IDs involved
- files and behavior changed
- verification commands and results
- reused results with their command, tree identity, and log, or `none`
- unrelated failures recorded in their own Issue, or `none`
- documentation or schema changes
- remaining risks
- active waiver IDs, or `none`

Investigation-only requests do not authorize editing, committing, pushing, opening PRs, or changing external state.

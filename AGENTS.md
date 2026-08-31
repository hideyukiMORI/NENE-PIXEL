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
8. `docs/GLOSSARY.md`
9. `docs/ROADMAP.md`
10. `docs/MILESTONES.md`
11. `docs/MVP_SCOPE.md`
12. `docs/DEVELOPMENT_PLAN.md`
13. `docs/API_STRATEGY.md`

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

## Required completion report

Every completed change must report:

- Issue and rule IDs involved
- files and behavior changed
- verification commands and results
- documentation or schema changes
- remaining risks
- active waiver IDs, or `none`

Investigation-only requests do not authorize editing, committing, pushing, opening PRs, or changing external state.

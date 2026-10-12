# NENE-PIXEL Agent Guide

Mandatory entry point for agents working on NENE-PIXEL, an Android-first Kotlin pixel drawing tool.
Governing principle: one meaning, one canonical implementation path.

## Start from current facts

Read the focused live GitHub Issue/PR and the relevant sections of
[Development Workflow](docs/DEVELOPMENT_WORKFLOW.md) and
[Quality Gates](docs/QUALITY_GATES.md#verification-execution-policy). Issues are the TODO authority;
handoffs and memories are dated restart aids. Confirm the active branch/worktree before editing.
Do not reset or switch an existing dirty checkout to obtain a newer entry file.

Read by affected contract, as accepted in [ADR 0038](docs/adr/0038-current-agent-guidance.md):

| When needed | Canonical guidance |
| --- | --- |
| Project purpose or ownership is unfamiliar | [Charter](docs/PROJECT_CHARTER.md), [Layout](docs/PROJECT_LAYOUT.md), [Glossary](docs/GLOSSARY.md) |
| Production code, state, command, or module boundary | [Architecture](docs/ARCHITECTURE_CONSTITUTION.md), [Command Model](docs/COMMAND_MODEL.md), [Coding Rules](docs/CODING_RULES.md), affected consumers and accepted ADRs |
| Persistence, import/export, or migration | Current frames/v4/recovery contract in [ADR 0041](docs/adr/0041-bounded-frames-and-project-v4.md), [Project Format v4](docs/PROJECT_FORMAT_V4.md), [Layout](docs/PROJECT_LAYOUT.md), and affected legacy contracts in [ADR 0030](docs/adr/0030-ordered-layers-and-empty-pixels.md), [ADR 0025](docs/adr/0025-indexed-project-compatibility.md) and v1/v2 specifications |
| Build, device, or working-copy operations | [Development Setup](docs/DEVELOPMENT_SETUP.md), applicable device/data-preservation protocol |
| Scope, sequencing, or milestone acceptance | [Roadmap](docs/ROADMAP.md), [Milestones](docs/MILESTONES.md), [MVP Scope](docs/MVP_SCOPE.md), [Development Plan](docs/DEVELOPMENT_PLAN.md) |
| Public/external API decision | [API Strategy](docs/API_STRATEGY.md) and [Workflow](docs/DEVELOPMENT_WORKFLOW.md#decision-policy) |

Read applicable [accepted ADRs](docs/adr/README.md) and [active waivers](docs/waivers/README.md)
before acting on their boundaries. Reuse unchanged guidance already read in this task; expand the
reading scope when understanding is incomplete. There is no per-edit whole-document reading ritual.

## Implementation boundaries

- Do not invent a second implementation path because it is locally convenient.
- A focused Issue precedes production, build, policy, or documentation changes. Update the governing document or ADR before an architectural implementation change, in the same focused change.
- Dependencies, Gradle plugins, modules, formats, and public APIs require the decision recorded by Development Workflow.
- Compose UI, Android components, persistence adapters, and future MCP adapters do not access document state directly. Use DocumentCommand for persistent/undoable mutations and WorkspaceAction for ephemeral editor changes.
- Mutable pixel storage stays inside the pixel-engine implementation boundary.
- Do not add suppressions, lint/detekt baselines, generated-code exclusions, or dependency exceptions without an approved waiver. Do not weaken a quality gate to pass a change.
- OpenAPI, HTTP, and MCP wait for the decision gate in API Strategy.
- Do not commit secrets, local SDK paths, signing material, build output, IDE state, or private assets.
- Use the owner's D-drive development lab for additional copies and outputs; never fall back to C or system temp. Preserve raw evidence outside disposable build directories. Do not put private absolute lab paths in tracked files or delete/overwrite lab evidence.
- Prefer the smallest change that fully follows the canonical path.

## Work ownership and completion

The current NENE-PIXEL assignment from hide is Astra as design Sana, with suitable SOL/LUNA
execution. The design seat owns requirements, design decisions, acceptance, integration, and
communication. Delegate useful bounded work at the required precision using available models;
keep small or tightly coupled work local. Do not force new agents per file, stage, or correction.
Follow runtime restrictions, and use read-only independent review when risk warrants it.
[ADR 0038](docs/adr/0038-current-agent-guidance.md) supersedes the old Claude-specific seat tables.

Delegations name the outcome, owned paths, applicable rules, D-drive location, selected checks,
and completion/cleanup conditions. Avoid concurrent edits or Gradle runs sharing mutable outputs.
Helpers do not recursively delegate without the parent's instruction. Read actual diffs and
verification evidence before accepting their reports. Visual design uses requested Claude Code
capabilities or an available equivalent; report unavailable skills/quota honestly.

Continue authorized work through applicable verification, PR, required CI, integration, and
cleanup. Reuse successful evidence at handoff/review. Keep unfinished acceptance in draft; state
real dependencies on hide precisely and continue independent work. After integration and artifact
preservation, remove disposable task copies after checking absolute paths, ignored/untracked or
unique files, active references, and links. Retain uncertain/active copies with the path and reason.

## Verification

QLT-011 through QLT-019 in Quality Gates are mandatory and remain the authority:

- Before execution, record changed behavior/paths and the regression each check detects, including affected callers. Use narrow checks; documentation-only changes need documentation validation, not app/device tests.
- Reuse equivalent passing results across assignees, stages, prose, and commit identities. Record the original command, source/artifact identity, and log. Rerun only for changed relevant inputs, failure, or a concrete unresolved concern (QLT-012).
- The final non-draft PR candidate needs successful required CI quality running canonical check plus Android assembly. Do not duplicate it locally. A local full run needs a concrete cross-cutting impact or CI diagnosis that narrow checks cannot resolve, stated before execution.
- Fix failures caused by the diff. Record unrelated failures separately and continue within scope. An unchanged retry that passes is instability, not accepted evidence (QLT-017).
- Hooks and automation follow the same selection/reuse policy; do not create a second test-selection system or disable checks (QLT-018).
- Register measured-path workloads with the phase gate and report per-operation cost; remove avoidable work. No feature-level performance collection, Profile regeneration, or forced cold build merely for handoff/commit/prose (QLT-019).
- Historical recipes are not execution authorization. Reconcile the live Issue, accepted protocol, and harness before new acceptance collection; retain historical FAIL/invalid results.

## Required completion report

Report Issue/rule IDs; changed files and behavior; commands/results; reused evidence with original
identity/log or none; unrelated failures and their Issues or none; documentation/schema changes;
remaining risks; active waiver IDs or none. Handoffs record the next action and dated state, not a
second copy of the rulebook.

Investigation-only requests do not authorize edits, commits, pushes, PRs, or external state changes.

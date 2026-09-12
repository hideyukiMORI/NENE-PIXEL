# NENE-PIXEL

NENE-PIXEL is an Android-first pixel drawing tool built in Kotlin under a deliberately constrained architecture.

The project does not copy NENE2's PHP or API architecture. It inherits one idea:

> Give every meaning one canonical implementation path, and reject competing paths mechanically.

Human memory and code-review attention are not enforcement mechanisms. The Kotlin type system, Gradle module graph, static analysis, tests, and CI must make invalid implementations difficult or impossible to express.

## Status

Milestones 0 through 3 have documented technical acceptance: the first internal MVP supports
single-frame drawing, durable v1 projects, explicit recovery and exact PNG export. See the
[M3 exit proof](docs/quality/M3_EXIT_PROOF.md) and [MVP user guide / release notes](docs/MVP_USER_GUIDE.md).
Required merge checks and current work remain authoritative in GitHub Issues. Structured palette,
layer and animation work enters through focused M4 design and migration decisions.

## Canonical documents

Read these documents in order:

1. [Project Charter](docs/PROJECT_CHARTER.md)
2. [Architecture Constitution](docs/ARCHITECTURE_CONSTITUTION.md)
3. [Command Model](docs/COMMAND_MODEL.md)
4. [Project Layout](docs/PROJECT_LAYOUT.md)
5. [Kotlin Coding Rules](docs/CODING_RULES.md)
6. [Quality Gates](docs/QUALITY_GATES.md)
7. [Development Workflow](docs/DEVELOPMENT_WORKFLOW.md)
8. [Development Setup](docs/DEVELOPMENT_SETUP.md)
9. [Glossary](docs/GLOSSARY.md)
10. [Project Format Version 1](docs/PROJECT_FORMAT_V1.md)

Architecture decisions live in [`docs/adr/`](docs/adr/README.md). Temporary and narrowly scoped rule exceptions live in [`docs/waivers/`](docs/waivers/README.md).

## Delivery planning

- [Roadmap](docs/ROADMAP.md)
- [Milestones](docs/MILESTONES.md)
- [MVP Scope](docs/MVP_SCOPE.md)
- [Development Plan](docs/DEVELOPMENT_PLAN.md)
- [API Strategy](docs/API_STRATEGY.md)

GitHub Issues are the source of truth for current TODO state. Planning documents define order, scope, dependencies, and exit criteria; they do not duplicate live task status.

## Priority order

When goals conflict, use this order:

1. Document integrity and user data safety
2. Deterministic, correct editor behavior
3. One canonical implementation path
4. Interactive drawing performance
5. Portability of non-UI modules
6. Feature delivery speed

Performance is not permission to bypass architecture. Performance-sensitive mutation is allowed only inside the controlled pixel-engine boundary defined by `ARC-005`.

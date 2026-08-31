# NENE-PIXEL

NENE-PIXEL is an Android-first pixel drawing tool built in Kotlin under a deliberately constrained architecture.

The project does not copy NENE2's PHP or API architecture. It inherits one idea:

> Give every meaning one canonical implementation path, and reject competing paths mechanically.

Human memory and code-review attention are not enforcement mechanisms. The Kotlin type system, Gradle module graph, static analysis, tests, and CI must make invalid implementations difficult or impossible to express.

## Status

The project is in the governance and architecture-definition phase. Production code must not be added until the initial Gradle structure and quality gates implement the rules defined here.

## Canonical documents

Read these documents in order:

1. [Project Charter](docs/PROJECT_CHARTER.md)
2. [Architecture Constitution](docs/ARCHITECTURE_CONSTITUTION.md)
3. [Command Model](docs/COMMAND_MODEL.md)
4. [Project Layout](docs/PROJECT_LAYOUT.md)
5. [Kotlin Coding Rules](docs/CODING_RULES.md)
6. [Quality Gates](docs/QUALITY_GATES.md)
7. [Development Workflow](docs/DEVELOPMENT_WORKFLOW.md)
8. [Glossary](docs/GLOSSARY.md)

Architecture decisions live in [`docs/adr/`](docs/adr/README.md). Temporary and narrowly scoped rule exceptions live in [`docs/waivers/`](docs/waivers/README.md).

## Priority order

When goals conflict, use this order:

1. Document integrity and user data safety
2. Deterministic, correct editor behavior
3. One canonical implementation path
4. Interactive drawing performance
5. Portability of non-UI modules
6. Feature delivery speed

Performance is not permission to bypass architecture. Performance-sensitive mutation is allowed only inside the controlled pixel-engine boundary defined by `ARC-005`.


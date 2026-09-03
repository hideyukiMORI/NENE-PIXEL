# Architecture Constitution

Status: normative

Change policy: accepted ADR plus migration plan required

## Normative language

- **MUST / MUST NOT**: mandatory; violations must eventually fail a mechanical gate.
- **SHOULD / SHOULD NOT**: default; deviation requires written reasoning in the PR.
- **MAY**: allowed but not required.

Every mandatory rule has a stable identifier. Tooling, tests, ADRs, PRs, and waivers must cite these identifiers rather than paraphrasing the rule.

## Constitutional rules

### ARC-001 — One canonical implementation path

Each domain meaning MUST have exactly one canonical type, owner, and behavior path. Parallel services, duplicate models, adapter-specific business logic, and aliases for the same concept are prohibited.

When a replacement is necessary, migrate callers and remove the old path in the same change unless a time-bounded waiver describes the transition.

### ARC-002 — Dependency direction is physical

Dependencies MUST follow the module graph in `PROJECT_LAYOUT.md`. Prohibited dependencies must be impossible to import, not merely discouraged in review.

No cyclic Gradle module dependency is permitted.

### ARC-003 — Core behavior is platform-independent

Domain, command, history, and pixel-operation semantics MUST NOT depend on Android, Compose, filesystem APIs, databases, network clients, wall-clock reads, locale defaults, or global coroutine scopes.

Platform behavior enters through an explicitly typed port.

### ARC-004 — State has one owner

Every state value MUST belong to exactly one of these categories:

| State | Meaning | Owner | Mutation route |
| --- | --- | --- | --- |
| `DocumentState` | Saved and undoable project truth | application command runtime | `DocumentCommand` only |
| `WorkspaceState` | Ephemeral editor/session state | workspace reducer | `WorkspaceAction` only |
| `RenderCache` | Derived, disposable acceleration data | renderer/pixel engine | internal cache API only |

The same fact MUST NOT be independently stored in more than one category. Derived state must be recomputed or cached with explicit invalidation.

### ARC-005 — Controlled mutation enclave

Externally visible domain and application state MUST be immutable. `PixelSnapshot` MAY privately own defensive packed primitive storage that is never mutated after construction and is never exposed; any bulk read returns a copy. Mutable pixel buffers and algorithm workspaces MAY exist only inside `:core:pixel-engine` implementation classes.

The pixel engine MUST:

- never expose owned mutable arrays, buffers, iterators, or collections
- accept validated value types
- return immutable `PixelPatch`, `PixelSnapshot`, or typed results
- produce deterministic output for the same input
- have invariant, boundary, and performance tests
- prevent concurrent unsynchronized access

Local mutation elsewhere requires a waiver. This rule exists to preserve interactive performance without spreading mutation through the architecture.

### ARC-006 — Explicit composition

Dependencies MUST be supplied at composition roots. Service locators, reflective discovery, runtime classpath scanning, ambient singletons, and mutable global registries are prohibited.

Dependency injection frameworks require an ADR and must not hide the dependency graph.

### ARC-007 — Determinism by default

Core behavior MUST NOT read current time, random values, locale, timezone, device density, environment variables, or process state directly. These values enter through ports or explicit parameters.

Commands executed against identical starting state and inputs must produce identical outcomes.

### ARC-008 — Boundaries validate once

Untrusted data MUST be parsed and validated at the first project-owned boundary, then converted to domain types. Invalid raw values MUST NOT travel through core modules.

Validation MUST NOT be repeated with different rules in UI, persistence, and automation adapters.

### ARC-009 — Contracts are versioned

Project files, exported structured data, and future external command contracts MUST have explicit versions and deterministic migration rules. Serialized DTOs MUST be separate from domain models.

Generated contract code MUST be clearly isolated and MUST NOT be hand-edited.

### ARC-010 — Errors are typed

Expected outcomes MUST use closed result types. Exceptions are reserved for programming defects, cancellation, and unexpected lower-level faults. Adapter/library exceptions from I/O or decoding MUST be normalized to typed failures at the first project-owned boundary before they reach application callers.

`null`, Boolean flags, strings, and generic exceptions MUST NOT encode multiple business outcomes.

### ARC-011 — Observability follows the command boundary

History, audit information, autosave triggers, render invalidation, and future automation review MUST derive from the canonical command result. Adapters MUST NOT recreate these effects independently.

### ARC-012 — Architecture precedes convenience

A framework shortcut, library feature, or performance optimization MUST NOT bypass the state owner, command route, module graph, or contract boundary. If the architecture is wrong, change it explicitly by ADR; do not create a local exception disguised as utility code.

## Enforcement order

Rules should be enforced at the earliest reliable layer:

1. Kotlin types and visibility
2. Gradle module dependencies and source sets
3. Compiler options
4. Static analysis and custom rules
5. Architecture and contract tests
6. CI merge gates
7. Code review for judgments that cannot yet be automated

Review-only enforcement is temporary and must be tracked in `QUALITY_GATES.md`.

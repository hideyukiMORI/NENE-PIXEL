# Product Roadmap

Status: planning authority for delivery order

## North star

NENE-PIXEL should become a dependable offline-first Android pixel drawing tool whose document behavior is deterministic, undoable, versioned, and available through one canonical application boundary.

The roadmap grows from an executable constraint system to a minimal vertical slice, then to a durable drawing MVP. Structured animation and external automation come only after the editor core is proven.

## Delivery sequence

| Milestone | Outcome | User-visible capability | Contract maturity |
| --- | --- | --- | --- |
| M0 — Executable Foundation | Reproducible Android/Kotlin build with enforced rules | Launchable shell only | Build and architecture contracts |
| M1 — Minimal Vertical Slice | One stroke travels through the complete canonical path | Draw and undo on a fixed test canvas | First internal command contract |
| M2 — Core Drawing | Coherent single-frame, single-layer editor | Create, draw, erase, choose color, pan/zoom, undo/redo | Stable core editing vocabulary |
| M3 — Durable MVP | Documents survive process/device lifecycle and can be exported | Save, load, recover, export PNG | Project format v1 |
| M4 — Structured Pixel Art | Multi-layer and animation-oriented documents | Layers, frames, preview, selection/transform | Expanded document/command contract |
| M5 — Android Beta | Measured, accessible, recoverable distributable build | Installable beta suitable for real work | Compatibility and release policy |
| M6 — Automation Boundary | Controlled external operation through the same command gateway | Optional MCP/HTTP automation with approval | Versioned external contract; OpenAPI only if HTTP exists |

Each milestone depends on all prior milestone exit criteria. Parallel work is allowed only when it does not create a second state owner, contract, tool configuration, or implementation path.

## Decision gates

### Gate A — Production Kotlin may begin

Requires M0 compiler, formatter, static analysis, test, Android lint, documentation, and CI gates to be executable. Architecture rules that cannot yet be automated must have explicit tracked Issues.

### Gate B — Project format may be frozen as v1

Requires the M2 document model, command semantics, pixel representation, and limits to be measured and accepted. Prototype serialization is not called v1.

### Gate C — Layers and frames may expand the model

Requires M3 migration and recovery tests. New structured-editing concepts must not invalidate saved MVP documents.

### Gate D — External automation may begin

Requires a stable command catalog, typed authorization decisions, change preview/approval, deterministic serialization, and an ADR choosing the transport. OpenAPI is selected only if that transport is HTTP.

## OpenAPI position

OpenAPI is not the first planning artifact. It describes an HTTP boundary, while the first product is an in-process Android application.

The contract order is:

1. Kotlin domain values and sealed command/result types
2. command contract tests and deterministic replay
3. versioned project-file DTOs and golden fixtures
4. versioned external automation DTO schema, if M6 begins
5. OpenAPI 3.1 for an accepted HTTP adapter, if one is justified

This order prevents transport concerns from defining the editor model and preserves `ARC-001`, `ARC-009`, and `CMD-011`.

## Roadmap change policy

Moving scope earlier requires evidence that all prerequisite exit criteria remain satisfied. Adding a supported platform, external transport, project schema, or architectural dependency requires the ADR defined by `DEVELOPMENT_WORKFLOW.md`.

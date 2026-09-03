# Project Layout and Dependency Rules

Status: normative

Package root: `io.github.hideyukimori.nenepixel` (fixed by [ADR 0001](adr/0001-initial-build-toolchain.md))

The module graph is part of the architecture. A package convention alone is not an adequate dependency boundary.

## Canonical Gradle modules

```text
:app:android
    Android application and composition root

:presentation:compose
    Compose screens, input adapters, view models/presenters

:core:application
    Command gateway, handlers, workspace reducer, queries, ports

:core:domain
    Document model, value types, privately packed immutable pixel snapshot, invariants, closed outcomes

:core:pixel-engine
    Mutable pixel surface, patches, raster algorithms, controlled mutation

:core:project-format
    Versioned project DTOs, codecs, migrations

:adapters:persistence
    Android/filesystem persistence implementations

:adapters:automation
    Reserved for future automation/MCP translation; absent until an ADR enables it

:quality:architecture-rules
    Active custom detekt rules and their focused rule tests
```

Do not create empty future modules. The names above are reserved canonical destinations and are created only when their first concrete responsibility exists.

The root `validateArchitecture` task reads the configured Gradle project graph and rejects unapproved module names, forbidden project dependencies, cycles, and platform dependencies in `:core:*`. The `:quality:architecture-rules` module is a build-only exception: application modules may load it through the `detektPlugins` configuration, but production code may not depend on it.

## Allowed dependency graph

```text
:core:domain
    -> Kotlin standard library only

:core:pixel-engine
    -> :core:domain

:core:application
    -> :core:domain
    -> :core:pixel-engine

:core:project-format
    -> :core:domain

:adapters:persistence
    -> :core:application
    -> :core:project-format

:presentation:compose
    -> :core:application
    -> :core:domain (read-only display types only)

:adapters:automation
    -> :core:application

:app:android
    -> all modules needed only for explicit composition
```

Any dependency not listed is forbidden. In particular:

- core -> Android or Compose: forbidden
- domain -> application: forbidden
- domain -> pixel engine: forbidden
- application -> persistence adapter: forbidden
- presentation -> persistence adapter: forbidden
- presentation -> project-format codec: forbidden
- automation -> domain mutation API: forbidden
- adapter -> another adapter: forbidden

## Module responsibilities

### `:core:domain`

Owns semantic truth:

- strongly typed identifiers and coordinates
- canvas, frame, layer, palette, and animation value types
- immutable document state and invariants
- immutable `PixelSnapshot` value with private row-major `RRGGBBAA` storage, typed queries, and defensive bulk copies
- immutable `Stroke` value with private row-major integer samples and semantic position iteration
- typed rejection/failure vocabulary shared by core modules

It does not own UI state, serialization annotations, database entities, Android resources, or pixel work buffers. Its private snapshot and stroke arrays are immutable by construction and never escape.

### `:core:pixel-engine`

Owns performance-sensitive raster behavior:

- flat packed pixel-surface implementation
- private mutable row-major pixel storage loaded from and returned as domain snapshots
- brush rasterization
- flood fill
- selection masks and bounded transforms
- patch calculation and application
- render invalidation regions

This is the only controlled mutation enclave. Its public API returns domain `PixelSnapshot` values and flat packed `PixelPatch` values with shared directional inverses and never leaks owned storage.

### `:core:application`

Owns behavior coordination:

- `CommandGateway`
- command handlers and validation
- `WorkspaceReducer`
- validated workspace viewport values and the portable canonical forward/inverse transform
- history and undo/redo coordination
- query projections
- ports for persistence, clocks, identifiers, and future external effects

It does not know Compose, Android, SQL, files, JSON libraries, or automation protocols.

### `:core:project-format`

Owns project-file compatibility:

- versioned serialization DTOs
- deterministic codecs
- schema migrations
- corruption and compatibility errors

It maps to/from domain snapshots. Domain types do not carry serialization annotations.

### `:presentation:compose`

Owns display and interaction translation:

- screens and composables
- view models/presenters
- pointer/stylus/keyboard input adapters
- translation of raw surface, density, and pointer data into validated viewport inputs
- tool previews
- accessibility and Android-facing presentation behavior

It renders immutable state and emits commands/actions. Rendering and input consume the same application-owned viewport transform; presentation owns no competing matrix or rounding policy. It contains no persistence calls or document transition logic.

### `:adapters:persistence`

Implements application ports for project storage and recovery. It may use Android/filesystem APIs and the project-format module. It never performs domain mutations outside the command gateway.

### `:app:android`

Is the composition root. It wires concrete adapters to ports, configures lifecycle ownership, and launches the UI. Business rules in this module are prohibited.

## Source-set rules

- Core modules SHOULD use Kotlin Multiplatform-compatible APIs where practical.
- Android source sets MUST contain only behavior that requires Android.
- `expect`/`actual` requires an ADR when used for domain-visible behavior.
- Platform-specific types MUST NOT cross a core public API.

## Package rules

Inside a module, packages are organized by domain capability first and technical role second. Generic dumping grounds are prohibited.

Forbidden package names:

- `utils`
- `helpers`
- `managers`
- `common` when it means unrelated code
- `misc`
- `base` without a named abstraction contract

Cross-module access must use the declared public API. Importing another module's `internal`, generated, test-fixture, or implementation package is prohibited.

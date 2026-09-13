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
    Versioned project DTOs, bounded byte codecs and typed decode dispatch

:adapters:persistence
    Android/filesystem persistence implementations

:adapters:automation
    Reserved for future automation/MCP translation; absent until an ADR enables it

:quality:architecture-rules
    Active custom detekt rules and their focused rule tests

:quality:baseline-profile
    Build-only Baseline Profile producer for the canonical Pencil/Undo journey
```

Do not create empty future modules. The names above are reserved canonical destinations and are created only when their first concrete responsibility exists.

The root `validateArchitecture` task reads the configured Gradle project graph and rejects unapproved module names, forbidden project dependencies, cycles, and platform dependencies in `:core:*`. The `:quality:architecture-rules` module is a build-only exception: application modules may load it through the `detektPlugins` configuration, but production code may not depend on it. `:quality:baseline-profile` is a separate build-only exception accepted by ADR 0010: `:app:android` references the producer only through `baselineProfile`, the producer references the target application only through the generated `testedApks` configuration, and no production or test source set may depend on either module through those edges.

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
    -> :core:domain
    -> :core:project-format

:presentation:compose
    -> :core:application
    -> :core:domain (read-only display types only)

:adapters:automation
    -> :core:application

:app:android
    -> all modules needed only for explicit composition
    -[baselineProfile build edge]-> :quality:baseline-profile

:quality:baseline-profile
    -[testedApks build edge]-> :app:android
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
- canvas, frame, layer, animation, and bounded immutable palette value types
- immutable document state and invariants
- immutable `PixelSnapshot` with private row-major U8 indices, typed queries and defensive bulk copies
- one `PaletteDefinition` owned by `DocumentState`, with cross-value index membership validation
- bounded immutable uninstalled `LegacyRgbaSource` with private exact RGBA storage and the closed
  `DocumentImportSource` admission vocabulary of ADR 0024
- immutable `Stroke` value with private row-major integer samples, a closed pencil/eraser effect,
  and semantic position iteration
- typed rejection/failure vocabulary shared by core modules

It does not own UI state, serialization annotations, database entities, Android resources, or pixel
work buffers. Its private snapshot, stroke and legacy-source arrays are immutable by construction
and never escape. LegacyRgbaSource is not accepted by editable runtime or command owners.

### `:core:pixel-engine`

Owns performance-sensitive raster behavior:

- flat packed-U8 pixel-surface implementation
- private mutable row-major pixel storage loaded from and returned as domain snapshots
- brush rasterization
- flood fill
- selection masks and bounded transforms
- patch calculation and application
- exact legacy color classification/conversion and explicit nearest reduction using the one
  PaletteRemapPlanner metric, never adapter or codec quantization
- render invalidation regions

This is the only controlled mutation enclave. Its public API returns domain `PixelSnapshot` values and flat packed `PixelPatch` values with shared directional inverses and never leaks owned storage.

ADR 0023 adds bounded palette-to-palette remapping in its `palette` package. PaletteRemapPlanner
returns a complete domain PaletteRemap through closed planning outcomes. It has no live runtime or
document mutation entry point. Its private permutation/matching workspace stays in pixel-engine;
the immutable remap references existing PaletteDefinition values and owns only its bounded mapping.

### `:core:application`

Owns behavior coordination:

- `CommandGateway`
- command handlers and validation
- `WorkspaceReducer`
- active drawing-tool and palette-index ownership with bounded document-pixel gesture interpolation
- gesture-captured Pencil/default Eraser index and exact gateway source admission
- the single current `EditorRuntime` owner for command, workspace, and derived clean-checkpoint state
- validated new-document requests, canonical blank runtime construction, and identifier ports
- validated workspace viewport values and the portable canonical forward/inverse transform
- bounded linear history, entry/change/logical-byte eviction, exact-position undo/redo, and clean-checkpoint coordination
- query projections
- workspace-owned session appearance, with Compose-only panel visibility and scroll/focus mechanics (ADR 0020)
- ports for persistence, clocks, identifiers, and future external effects
- private immutable save capture/candidate, runtime/operation identity, checked completion, and the
  one loaded/recovered runtime-install protocol
- one runtime-owned mutable operation flow exposed only as a read-only derived projection
- bounded persistence ordering: one active physical operation and at most one coalesced latest
  autosave capture identified by runtime generation and exact history position, published as a
  recovery Candidate on an explicit platform request; opaque equality tokens let the scheduler
  observe state changes without exposing history or document ownership

The retained import source, source-bound original-copy proof, destination epoch and at most one
completed reduced preview extend the same persistence owner under ADR 0024. Heavy conversion and
future-owner preparation use an injected dispatcher outside the runtime lock while retaining the
existing physical-operation lease. `PersistencePorts` groups project/recovery/PNG ports for the
workflow factory; the app injects that group and the worker dispatcher. No second workflow is added.

It does not know Compose, Android, SQL, files, project-format bytes/codecs, JSON libraries, storage
URIs, or automation protocols. It may use platform-neutral coroutines for suspend ports, its read-only
`StateFlow`, and the non-cancellable retirement/install critical section, but creates no scope or
dispatcher.

### `:core:project-format`

Owns project-file compatibility:

- versioned serialization DTOs
- deterministic codecs
- typed current/legacy version dispatch; pixel conversion belongs to pixel-engine
- corruption and compatibility errors
- bounded private encoding/decoding byte storage permitted by `ARC-005`

It maps to/from typed domain documents and uninstalled legacy sources. Domain types do not carry serialization annotations. Encoded
values defensively own bytes that are immutable after construction and expose no mutable storage.
The module performs no I/O and is never a dependency of `:core:application` or presentation.
ADR 0024 refines ADR 0015's boundary to one `ProjectFormatBytes` carrier, one closed
`ProjectFormatResult` and `ProjectFormatCodec`. It reads v1/v2 to DocumentImportSource, writes only v2
from DocumentState, and exposes exact original-v1 encoding only from LegacyRgbaSource. Version
implementations are internal. The carrier retains the v1 maximum-plus-one probe; each decoder
separately enforces its exact structural/checksum/domain admission order before raster allocation.

ADR 0022 adds one separate `palette` codec package here for PaletteJsonBytes, PaletteJsonCodec and
closed format outcomes under [Palette JSON v1](PALETTE_JSON_V1.md). It maps to the domain
PaletteDefinition, contains no I/O/Android/JSON dependency, and does not make application depend on
this module. Domain Palette continues owning colors; PaletteDefinition supplies validated document
default semantics. No parallel tool-color list or raw JSON node crosses a public domain/app boundary.

The #106 indexed cutover changes document ownership and all consumers together. Legacy RGBA import
is bounded uninstalled input with a typed lossless/conversion-required outcome, never an alternative
editable runtime. Future frames, tiles/maps and reference images use the boundaries in ADR 0022;
no empty module or unused frame API is introduced by palette preparation.

### `:presentation:compose`

Owns display and interaction translation:

- screens and composables
- view models/presenters
- pointer/stylus/keyboard input adapters
- translation of raw surface, density, and pointer data into validated viewport inputs
- tool previews
- accessibility and Android-facing presentation behavior

It renders immutable state and emits commands/actions. Rendering and input consume the same
application-owned viewport transform; presentation owns no competing matrix, rounding policy,
document runtime, workspace state, active palette selection, or dirty state. Palette controls render
the immutable projection and emit the canonical workspace selection action. The startup recovery
offer replaces the status-row content while an unadopted Candidate exists and emits typed accept and
decline requests through the same persistence callbacks. It contains no persistence calls or
document transition logic.

### `:adapters:persistence`

Implements application ports for project storage, recovery, and PNG export (ADR 0019). The internal
PNG encoder resolves the supplied immutable document's indices through its palette; PNG, project
Save As and exact legacy-source copying share one typed
fresh-destination picker and verified writer. It depends directly on application,
domain, and project-format because the suspend port signatures contain `DocumentState`; it never
relies on domain types leaking through another module's implementation dependency. It may use
Android/filesystem APIs and privately own bounded transport bytes under `ARC-005`. It maps only an
immutable `DocumentState` supplied to `save`, returns one fully validated `DocumentImportSource`
from `load`, or copies the exact supplied `LegacyRgbaSource`; it never obtains a live runtime or
performs domain mutations. Copied requires exact completed read-back verification and does not
advance a document checkpoint. The Android adapter owns typed
SAF result contracts, fresh-document Save As, read-back verification, maximum-plus-one bounded stream
reads, and failure normalization before invoking the no-I/O codec. App-private recovery uses the one
serialized framework `AtomicFile` record and conditional Retired writer fixed by ADR 0016/0024.
The existing filename is retained; inspection accepts envelope 1/project 1 and envelope 2/project 2,
and new writes use envelope 2. Inspection never rewrites a source or decides whether to reduce it.

### `:app:android`

Is the composition root. It wires concrete adapters to ports, retains the one activity-scoped
application `EditorRuntime` and persistence workflow through an AndroidX `ViewModel`, and launches
the UI. It owns `viewModelScope`, the picker-request broker/Activity Result launcher connection, and
selection of the injected serialized IO dispatcher; it does not own persistence transition rules. It
also owns the autosave clock: the one `AutosavePolicy` value holding the ADR 0018 quiet window and
latency cap, the autosave scheduler that turns the read-only autosave projection into at most one
outstanding publication request on `viewModelScope`, and the activity `ON_STOP` flush.
Android UUID generation implements the application `DocumentIdSource` port here. The initial
PaletteDefinition is supplied to fresh document construction, not retained as a second runtime
palette owner. Business rules in this module are
prohibited.

### `:quality:baseline-profile`

Owns the single out-of-process Baseline Profile collection journey accepted by ADR 0010. It launches
`:app:android`, locates the canonical editor through accessibility semantics, derives input from the
reported canvas bounds, and exercises Pencil followed by Undo. It does not import core or
presentation implementation, access document state directly, classify code for a startup profile,
or ship in the application runtime.

ADR 0021 places the app-language adapter and retained controller in `:app:android`. The public
immutable language projection/callback and localized resource rendering live in `:presentation:compose`;
core does not receive Android locale or resource IDs.

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

The app composition root owns localized Android Context/Configuration and layout direction (ADR 0021).

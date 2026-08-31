# Kotlin Coding Rules

Status: normative

These rules narrow Kotlin to the subset approved for NENE-PIXEL. Official Kotlin style applies where this document is silent.

## Types and state

### KOT-001 — Primitive obsession is prohibited at boundaries

Domain and application APIs MUST use dedicated types for identifiers, coordinates, dimensions, palette indexes, frame indexes, durations, opacity, and schema versions.

Use `@JvmInline value class` where its semantics and representation are stable. Validate values at construction; do not pass invalid primitives deeper and validate later.

### KOT-002 — Invalid state must be unrepresentable

Use enums or sealed hierarchies for closed alternatives. Multiple Booleans, magic integers, and strings MUST NOT encode modes or state machines.

Exhaustive `when` expressions over closed domain types MUST NOT use `else`.

### KOT-003 — Public state is immutable

- Public properties MUST be `val`.
- Domain and application types MUST NOT expose mutable collections, arrays, buffers, or mutable flows.
- `var` is prohibited in domain models and public application state.
- Local `var` is allowed only when it expresses a bounded algorithm more clearly than recursion or copying.
- Mutable storage is otherwise restricted by `ARC-005`.

Kotlin read-only collection interfaces are not proof of deep immutability. Constructors and factories must defensively own or copy externally supplied data where aliasing is possible.

### KOT-004 — Null has one meaning

`null` MAY mean only “this optional value is absent.” It MUST NOT mean invalid, not loaded, failed, unknown mode, deleted, or rejected.

Public domain/application returns use typed closed results for meaningful alternatives. `!!` is prohibited. Platform nulls must be normalized at the adapter boundary.

### KOT-005 — Expected outcomes are not exceptions

Expected validation, lookup, command rejection, version incompatibility, and user-correctable conditions MUST use typed results. Catching `Exception` or `Throwable` is prohibited except at a documented top-level boundary that maps it to a typed failure or crash report.

Cancellation exceptions must be rethrown immediately.

### KOT-006 — Generic data bags are prohibited

The following are prohibited in domain and application APIs:

- `Any`
- `Map<String, Any?>`
- untyped JSON nodes
- positional `Pair` or `Triple` where the values have domain meaning
- generic string-key metadata used instead of a declared type

Use a named type.

## Construction and visibility

### KOT-007 — Construction preserves invariants

Types with invariants MUST have a private or internal constructor and a single canonical factory. Secondary constructors that recreate validation are prohibited.

Factories return the same typed result vocabulary used elsewhere; they do not throw for expected invalid input.

### KOT-008 — Visibility is minimal

- The default declaration visibility is `internal`.
- `public` is allowed only for a documented module API.
- `private` is preferred for implementation details.
- Library modules enable Kotlin explicit API mode.
- A public declaration requires a contract test or public-API justification.

### KOT-009 — Extensions do not create hidden APIs

- Private file-local extensions are allowed.
- Internal extensions are allowed only beside the type/capability they support.
- Public extensions require an ADR or an existing canonical extension policy.
- Extensions that mutate receivers, perform I/O, hide expensive work, or imitate members are prohibited.

### KOT-010 — Language magic is restricted

The following require an ADR and mechanical enforcement before use:

- custom operator overloads, except mathematically obvious value operations
- `infix` functions
- reflection
- delegated properties with hidden I/O or state mutation
- annotation-driven runtime discovery
- compiler plugins that alter program semantics

## Naming and structure

### KOT-011 — Names state the domain role

Prohibited type suffixes and names:

- `Manager`
- `Helper`
- `Util` / `Utils`
- `Common`
- `Processor` unless processing is the glossary-defined domain operation
- `Data` when a more precise noun exists

Approved role suffixes include `Command`, `Handler`, `Query`, `Port`, `Adapter`, `Factory`, `Codec`, `Mapper`, `Reducer`, `Policy`, and `Renderer`, but only when the type performs that exact role.

The mechanical `ForbiddenGenericName` rule rejects the unconditional type suffixes `Manager`, `Helper`, `Util`, `Utils`, and `Common`, plus the package segments `utils`, `helpers`, `managers`, and `misc`. Conditional terms such as `Processor`, `Data`, `common`, and `base` require semantic review until a reliable domain-aware rule exists; they must not be rejected by a broad text scan.

### KOT-012 — One primary declaration per file

A file SHOULD contain one primary public/internal type and closely coupled private declarations. File names match the primary type. Grab-bag files and generic `Extensions.kt` are prohibited.

### KOT-013 — Complexity is bounded

Unless a stricter tool rule applies:

- cognitive complexity per function: maximum 10
- nesting depth: maximum 3
- function length: maximum 40 logical lines
- parameters: maximum 4; use a named input type when the values form one concept
- Boolean parameters: prohibited in public APIs

Pixel algorithms may exceed a threshold only through a waiver backed by focused tests and measured performance evidence. Artificially splitting a coherent algorithm solely to satisfy a metric is not acceptable.

## Coroutines and concurrency

### KOT-014 — Structured concurrency only

`GlobalScope`, unmanaged scopes, fire-and-forget jobs, and blocking calls on the main thread are prohibited.

Scopes and dispatchers are owned at a lifecycle/composition boundary and injected where needed. Core functions prefer suspendable or synchronous deterministic APIs rather than launching work internally.

### KOT-015 — State publication is read-only

Owners MAY use mutable flows internally but expose only `StateFlow`/`Flow`. A flow value follows the state ownership rules in `ARC-004`; flows must not create a duplicate source of truth.

### KOT-016 — Pixel writes are serialized

Concurrent writes to the same document/pixel surface are prohibited. The command runtime defines a single ordered commit sequence. Parallel computation MAY be used internally only when output ordering and determinism are tested.

## Compose rules

### KOT-017 — Compose renders state and emits intent

Composable functions MUST NOT:

- access repositories, codecs, files, databases, or service locators
- mutate `DocumentState` or pixel storage
- contain domain validation or command transition logic
- launch unowned long-lived work

Composables receive immutable render models and callbacks or typed intent sinks.

### KOT-018 — UI state is classified explicitly

`remember` is for local presentation mechanics only. State that affects editor behavior belongs to `WorkspaceState`; saved/undoable state belongs to `DocumentState`.

Moving state into `remember` to avoid the canonical reducer or command route is prohibited.

### KOT-019 — Side effects are named and keyed

Compose side-effect APIs must use stable, semantically correct keys and call a named application/presentation function. Hidden persistence or document mutation inside a Compose effect is prohibited.

## Serialization and generated code

### KOT-020 — DTOs are not domain models

Serialization/database DTOs stay in their owning adapter or project-format module. Mapping to domain types occurs once at the boundary. Domain models MUST NOT contain persistence, JSON, database, or Android annotations.

### KOT-021 — Generated code is reproducible

Generated files MUST:

- live in a clearly named generated source directory
- contain a generated marker
- be produced by a pinned tool and canonical Gradle task
- be reproducible in CI
- never be hand-edited

## Suppression

### KOT-022 — Suppression is an exception, not a tool

File-level suppression is prohibited. A declaration-level `@Suppress`, lint ignore, static-analysis exclusion, or generated-code exclusion requires an active waiver ID in an adjacent comment and the PR description.

The adjacent source comment is `Waiver: WVR-NNNN` on the line immediately before the suppression. Its waiver file must be active, unexpired, and have an exact `Scope` matching both the source path and suppressed declaration or XML element. Wildcards and file-level suppressions are not accepted by the gate.

`TODO`, `FIXME`, and commented-out code must include an Issue number or be removed before merge.

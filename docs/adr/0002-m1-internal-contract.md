# ADR 0002: M1 internal contract ownership and execution order

- Status: accepted
- Date: 2026-09-01
- Issue: #17
- Affected rules: `ARC-001`, `ARC-002`, `ARC-004`, `ARC-005`, `ARC-008`, `ARC-010`, `ARC-012`, `CMD-001`, `CMD-003`, `CMD-007`, `CMD-008`, `KOT-001`, `KOT-007`, `KOT-008`

## Context

M0 established the module graph and quality gates without production types. M1 is the first point where public cross-module Kotlin contracts become real. The existing plan left two material ambiguities that cannot be resolved safely inside an implementation PR.

First, `DocumentState` is durable domain truth, while the mutable raster implementation belongs to the pixel-engine enclave. Placing `PixelSnapshot` only in `:core:pixel-engine` would make a domain-owned `DocumentState` depend in the forbidden direction. Moving `DocumentState` into application would make future project-format mapping depend on runtime coordination or require a duplicate durable model.

Second, `P1-03` asked for a gateway before the first concrete command, and `P1-07` promised UI integration without depending on the Compose slice. An empty gateway, dummy production command, or later retrofit would create scaffolding without executable responsibility.

M0 exit evidence is complete in [Fresh-clone and M0 Exit Proof](../quality/FRESH_CLONE_PROOF.md). There are no active waivers or legacy product APIs to migrate.

## Decision

### Ownership

`:core:domain` owns semantic values and immutable document truth:

- `DocumentId`
- `CanvasWidth`, `CanvasHeight`, and `CanvasSize`
- `PixelX`, `PixelY`, and `PixelPosition`
- `PixelRegion`
- `ColorChannel` and `PixelColor`
- `PaletteIndex` and `PaletteEntry`
- `Revision`
- `PixelSnapshot`
- `Stroke`
- `DocumentState`

`PixelSnapshot` is one final immutable value with row-major pixel semantics. Its factory defensively owns input and its API exposes only typed queries; it exposes no array, buffer, mutable collection, or implementation interface. The domain implementation never mutates its owned content after construction.

`:core:pixel-engine` owns `PixelSurface`, `PixelPatch`, `PixelChange`, rasterization, and all mutable pixel storage. A surface is created from a domain snapshot and produces a new domain snapshot plus an immutable patch. Arrays and mutable collections remain private to this module.

`:core:application` owns the current `DocumentState` reference, `DocumentCommand`, `CommandResult`, `DocumentTransition`, `ChangeSet`, `CommandGateway`, command handlers, history, `WorkspaceState`, `WorkspaceAction`, and `WorkspaceReducer`. Holding the current value is runtime ownership; it does not move the immutable `DocumentState` type out of domain.

`:presentation:compose` renders immutable projections and translates gestures to workspace actions and document commands. `:app:android` only constructs the accepted graph.

### Initial value contract

Invalid primitives do not enter core APIs. In P1-01:

- `DocumentId` accepts exactly 32 lowercase hexadecimal characters. Generation is a later application port; core never reads random or process state.
- canvas dimensions are positive typed integers; `CanvasSize.pixelCount` is calculated as `Long` without allocating storage.
- pixel axes are non-negative typed integers; containment in a particular canvas is checked once by `CanvasSize`.
- `PixelRegion` uses half-open right/bottom bounds and is created only when its positive size is fully contained by a canvas.
- `ColorChannel` is an unsigned 8-bit channel and `PixelColor` is four explicitly named RGBA channels. This is semantic color, not the pixel-engine's storage representation.
- palette indexes and revisions are non-negative typed values. Revision zero is the single initial value, and advancing past `Long.MAX_VALUE` is a typed rejection.
- `Stroke` is one non-empty ordered path with one `PixelColor`; it defensively owns the path and is rejected atomically if any position is outside the target canvas.
- invariant-bearing factories return `DomainValueResult.Created` or `DomainValueResult.Rejected` with a closed `DomainValueRejection`; they do not return null/Boolean or throw for expected input.

M1 composition uses one fixed 16 x 16 canvas and one fixed palette entry. It does not expose arbitrary document creation. Production canvas, history, and memory limits remain intentionally unfrozen until the P1-08 measurements and P2-01 ADR.

### Command contract

`DocumentCommand` and all result/reason vocabularies are sealed in `:core:application`. `CommandGateway.execute` is the only document mutation entry. Each internal handler also uses the method name `execute`; the gateway dispatches with an explicit exhaustive `when`, not reflection, a string key, a service locator, or a runtime registry.

P1-03 introduces only concrete state, transition, and closed result contracts that have real data from P1-02. It does not create an empty gateway or placeholder command. P1-04 introduces `ApplyStrokeCommand`, its one handler, and the first final `CommandGateway` together, so exhaustive dispatch is executable from its first revision.

`ChangeSet` owns the complete committed `PixelPatch`, inverse information, before/after revisions, and render invalidation. History records that change; it does not store UI closures or independently recalculate a stroke.

Undo and redo enter through concrete commands added in P1-07 and use recorded change data. P1-07 therefore depends on the P1-06 UI slice as well as P1-04.

### API status and package order

These are public module APIs only where another allowed module consumes them. They are internal application contracts, not external or serialized compatibility promises. Changing their meaning still requires an ADR; project-file compatibility does not begin before Gate B.

The executable order is:

1. P1-01 creates `:core:domain` with the initial values and factory results.
2. P1-02 adds domain `PixelSnapshot` and creates `:core:pixel-engine` with `PixelSurface` and `PixelPatch`.
3. P1-03 adds domain `DocumentState` and creates `:core:application` with transition/result contracts.
4. P1-04 adds the first command, handler, and gateway.
5. P1-05 adds the workspace action/reducer path.
6. P1-06 creates `:presentation:compose` and replaces the Android placeholder with the vertical slice.
7. P1-07 adds command-routed one-level undo/redo and UI integration.
8. P1-08 measures the complete slice and reviews M1 exit criteria.

Every new module applies the existing compiler, explicit API, formatter, detekt, test, lock, and verification policy and joins root `check` in the same Issue that creates it.

## Rejected alternatives

### Put `PixelSnapshot` only in pixel engine

Rejected because domain `DocumentState` would need a forbidden dependency on `:core:pixel-engine`, or another pixel snapshot type would be required for persistence. Both outcomes violate the physical graph and one-meaning rule.

### Put `DocumentState` in application

Rejected because durable document meaning would be owned by coordination code. Future project-format mapping would either depend on application or introduce a second domain document representation.

### Define an open snapshot interface in domain

Rejected because any consuming module could create another implementation and storage policy. A final immutable value provides one construction and equality path.

### Create an empty gateway or dummy command in P1-03

Rejected because neither has product responsibility, and the dummy would become a competing mutation vocabulary. The gateway lands atomically with the first real command.

### Freeze production canvas and storage limits now

Rejected because M1 has no measurements. The fixed vertical-slice fixture is bounded test/product scaffolding, not a claimed production limit.

### Use arbitrary strings or `java.util.UUID` as DocumentId

Rejected because arbitrary strings permit multiple spellings and unbounded input, while `java.util.UUID` makes a JVM type part of the portable core API. Fixed lowercase hexadecimal text gives one locale-independent representation; generation remains an injected application capability.

## Consequences

### Benefits

- durable truth remains domain-owned and future project-format mapping keeps its allowed dependency direction
- mutable pixel work remains physically isolated
- the command gateway is exhaustive from its first implementation
- value validation and command rejection use distinct closed result vocabularies
- every M1 module is created with a concrete consumer and test

### Costs and risks

- the first immutable snapshot may copy row-major content; P1-08 must measure this cost before P2-01 accepts storage and limit policy
- P1-02 and P1-03 each touch two allowed modules to introduce a complete cross-module contract
- internal public APIs are deliberately narrow and may require a superseding ADR before project format v1 if measurements contradict the design

## Enforcement impact

- `PROJECT_LAYOUT.md` records domain snapshot ownership and the pixel-engine mutation boundary.
- `DEVELOPMENT_PLAN.md` adds P1-00, makes P1-03 depend on P1-02, moves gateway evidence to P1-04, and makes P1-07 depend on P1-06.
- `GLOSSARY.md` gains the exact initial value and factory-result names.
- P1-01 through P1-08 Issues must cite this ADR and their affected rules.
- Module graph validation, explicit API, tests, detekt, and root `check` reject competing boundaries as each module appears.

## Migration and rollback

There is no product code or stored document to migrate. P1 work implements this decision in dependency order. If a P1 measurement invalidates ownership or representation, stop dependent work and supersede this ADR before changing the API. Rollback removes the unmerged Issue branch; it does not keep parallel snapshot, state, gateway, or value types.

## Related

- Issue: #17
- PR: pending
- Supersedes: none
- Superseded by: none

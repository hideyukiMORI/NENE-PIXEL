# Project Charter

Status: normative

Codename: `NENE-PIXEL`

## Mission

Build a dependable Android-first pixel drawing tool whose behavior is understandable and safely changeable by both humans and AI agents.

NENE-PIXEL deliberately limits implementation freedom. For each domain meaning, the project defines one name, one type, one state owner, and one mutation path. Alternative paths are removed through visibility, module boundaries, static analysis, tests, and CI.

## Product direction

The initial product is an Android application. Non-UI editor modules should remain portable Kotlin where this does not weaken Android behavior or complicate the canonical design. Desktop or other targets require a separate accepted ADR; they are not implicit deliverables.

The editor is expected to support a progressively implemented subset of:

- pixel-precise drawing and erasing
- palette-based color selection
- layers, frames, and animation-oriented document structure
- selection and transform operations
- deterministic undo and redo
- versioned project save/load
- deterministic raster export
- future automation through the same application boundary used by the UI

This list is direction, not a claim that the features already exist.

## Definition of strictness

Strictness means all of the following:

- A concept has one canonical term in `GLOSSARY.md`.
- A state value has one owner.
- A persistent mutation has one command route.
- An ephemeral editor-state change has one action/reducer route.
- A dependency crosses one declared port.
- A serialized fact has one authoritative schema.
- A rule has a named mechanical enforcement mechanism.
- An exception is explicit, narrow, reviewable, and removable.

Strictness does not mean maximizing indirection, forbidding all local mutation, or creating abstractions without a second concrete need.

## Success criteria

The architecture is succeeding when:

1. A new contributor can find the only valid place for a change without guessing.
2. UI, persistence, import/export, tests, and automation cannot mutate document state through different business paths.
3. A complete editing operation can be replayed deterministically from its command and starting state.
4. Undo/redo is derived from recorded change data, not from UI-specific callbacks.
5. Invalid identifiers, coordinates, modes, and state combinations are unrepresentable or rejected at a named boundary.
6. Architectural violations fail locally and in CI before merge.
7. Performance-sensitive code remains confined to a tested boundary and does not leak mutation across the project.

## Non-goals

- Copying NENE2's PHP modules, HTTP runtime, or directory layout
- Reproducing a general-purpose image editor in the first release
- Supporting arbitrary plugins before the editor contract is stable
- Introducing an HTTP API or MCP surface before the command contract is stable
- Using frameworks or code generation to hide control flow
- Treating documentation volume as a substitute for executable rules
- Preserving multiple equivalent implementation styles for developer preference

## Governance

The documents listed as canonical in the root `../README.md` are project policy. `ARCHITECTURE_CONSTITUTION.md` has precedence over all other project documents. An accepted ADR may refine the constitution but must not silently contradict it.

If code and policy disagree, the repository is inconsistent. Fix the governing decision and implementation together; do not describe the mismatch as an alternative style.

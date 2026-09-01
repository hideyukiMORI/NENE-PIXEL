# Milestones

Status: normative delivery boundaries

The milestone order is fixed by `ROADMAP.md`. GitHub Milestones mirror these definitions; GitHub Issues carry live task status.

## M0 — Executable Foundation

### Purpose

Turn written rules into a reproducible build and merge gate before product behavior grows.

### Required deliverables

- ADR fixing package root, Android application ID, JDK 21, Gradle/AGP/Kotlin/Compose compatibility, and initial module creation order
- Gradle Wrapper and version catalog or other single dependency-version authority chosen by ADR
- minimal Android application shell that launches without product behavior
- canonical `check` task covering compilation, formatting, detekt, unit tests, Android lint, and documentation validation
- warnings-as-errors and explicit API mode where required
- dependency locking/verification policy
- GitHub Actions using the same canonical Gradle tasks
- required PR checks and `main` protection after CI succeeds
- zero baselines and zero waivers

### Exit criteria

- fresh clone builds with documented commands using JDK 21
- `./gradlew check` succeeds locally and in CI
- an intentional formatting, forbidden-import, compiler-warning, test, and documentation-rule violation each makes the appropriate check fail
- Android debug shell launches on the supported emulator/device profile
- `main` cannot merge a PR with failing required checks

### Exit evidence

M0 passed all exit criteria on 2026-09-01. The command-by-command review is recorded in [Fresh-clone and M0 Exit Proof](quality/FRESH_CLONE_PROOF.md). M1 work may begin without weakening any M0 gate.

## M1 — Minimal Vertical Slice

### Purpose

Prove the architecture end to end with the smallest meaningful editing operation.

### Required deliverables

- first validated domain primitives for document ID, canvas size, pixel position, palette entry, and revision
- bounded `PixelSurface`, immutable `PixelSnapshot`, and reversible `PixelPatch`
- one pencil-style `ApplyStrokeCommand` and exactly one handler
- command gateway, typed result, change set, history entry, and render invalidation
- workspace reducer for active color, gesture preview, and viewport state used by the slice
- Compose canvas that translates touch input to preview actions and one committed stroke command
- one-level undo and redo through the canonical history path

### Exit criteria

- a stroke drawn through Compose, a test adapter, and direct application test produces the same command result
- command replay is deterministic
- rejected/out-of-bounds strokes are atomic no-ops with typed reasons
- patch apply/invert round-trips
- UI has no direct document or pixel-buffer mutation
- all `QLT-007` and relevant `QLT-008` tests pass

### Exit evidence

M1 implementation, reproducible baseline, and exit-criterion evidence are recorded in [M1 Vertical-slice Baseline and Exit Proof](quality/M1_EXIT_PROOF.md). The M1 milestone is closed only after the final required CI and GitHub Issue state are read back in the order defined by that proof.

## M2 — Core Drawing

### Purpose

Create a coherent single-frame, single-layer editor before persistence complexity.

### Required deliverables

- validated new-document creation
- pencil and eraser tools
- explicit active-color/palette workflow
- bounded zoom, pan, grid, and pixel-coordinate mapping
- multi-step bounded undo/redo history
- dirty-state derivation from committed changes
- lifecycle-safe workspace ownership
- no-op, gesture cancellation, and overlapping-input semantics
- representative pixel-engine benchmarks and memory measurements

### Exit criteria

- the complete MVP drawing workflow in `MVP_SCOPE.md` works without persistence
- every document mutation is represented by a command and reversible change set
- input coordinates remain correct across zoom/pan/density changes
- performance targets are recorded from representative hardware before hard limits are frozen
- process recreation does not create a second document state owner

## M3 — Durable MVP

### Purpose

Make the core editor safe for real documents and complete the first internal MVP.

### Required deliverables

- accepted project-format ADR and schema version 1
- deterministic project codec and domain mapping
- atomic save and explicit load commands/use cases through application ports
- autosave and crash/interruption recovery policy
- golden fixtures, round-trip, corruption, version, and resource-limit tests
- Android document picker/storage adapter
- deterministic PNG export
- user-visible error/recovery states

### Exit criteria

- every acceptance journey in `MVP_SCOPE.md` passes on the supported Android profile
- save interruption never replaces the last valid document with a partial file
- supported older fixtures migrate deterministically
- unsupported future/corrupt documents fail with typed user-visible outcomes
- exported PNG matches the document pixels exactly
- MVP limitations are documented in-app and in release notes

## M4 — Structured Pixel Art

### Purpose

Expand the proven durable document model for structured pixel-art work.

### Candidate deliverables

- multiple ordered layers, visibility, opacity policy, and reorder commands
- multiple frames and deterministic animation playback
- frame duration/playback mode types
- selection, move, copy, and bounded transform operations
- palette editing and replacement policies
- sprite-sheet or animation export chosen by focused Issues

### Exit criteria

- project-format migration preserves all M3 documents
- every new operation follows command/change-set/history semantics
- layer/frame operations remain deterministic and bounded
- animation preview never becomes a second timing authority for document data

## M5 — Android Beta

### Purpose

Harden the app for sustained real-world use.

### Required deliverables

- startup, drawing latency, memory, large-document, and battery measurements
- accessibility, keyboard/stylus behavior, rotation, backgrounding, and low-memory testing
- recovery and destructive-action confirmation review
- release signing/configuration policy with no secrets in Git
- privacy statement and data-handling review
- versioning, changelog, release checklist, and reproducible beta artifact

### Exit criteria

- no open critical data-loss or deterministic-replay defect
- supported device/API matrix is documented and tested
- performance budgets pass on the named minimum profile
- release artifact is built only from a tagged, checked `main` commit

## M6 — Automation Boundary

### Purpose

Allow controlled automation without introducing another document mutation path.

### Entry criteria

- M5 beta contract is stable
- command catalog ownership and compatibility policy are accepted
- preview, approval, cancellation, and audit requirements are accepted
- threat model and authorization boundaries are documented

### Candidate deliverables

- versioned external command DTO schema
- automation adapter translating only to `CommandGateway`
- read-only query projections separate from mutation commands
- MCP catalog derived from the accepted external schema
- optional HTTP adapter and OpenAPI 3.1 contract only after an HTTP ADR
- contract and conformance tests preventing adapter-specific business behavior

### Exit criteria

- UI and automation produce identical command semantics
- automation cannot access mutable pixel storage, persistence internals, or domain constructors
- every mutation can be previewed, approved/rejected, audited, and undone
- external schema compatibility is tested independently of internal Kotlin class names

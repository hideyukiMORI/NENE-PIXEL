# Architecture Decision Records

ADRs record long-lived decisions with meaningful alternatives and consequences. They explain why the canonical implementation path exists.

## States

- `proposed`
- `accepted`
- `rejected`
- `superseded`

Only accepted ADRs are normative. An accepted ADR must not silently contradict the Architecture Constitution; it updates the affected canonical document in the same change.

## Naming

```text
NNNN-short-kebab-title.md
```

Numbers are never reused. Copy `0000-template.md`, assign the next number, and add it to the index below.

## Required content

An ADR must include:

- status and date
- Issue
- rule IDs affected
- context based on concrete evidence
- decision
- rejected alternatives
- consequences and risks
- enforcement impact
- migration/rollback plan

“Industry standard,” “best practice,” and “AI recommended it” are not sufficient evidence by themselves.

## Index

| ADR | Status | Decision |
| --- | --- | --- |
| [0001](0001-initial-build-toolchain.md) | accepted | Initial Android identity, toolchain, dependency authority, quality tools, and module creation order |
| [0002](0002-m1-internal-contract.md) | accepted | M1 value, snapshot, state, command, and module ownership with executable package order |
| [0003](0003-recorded-revision-undo-semantics.md) | accepted | Canonical undo/redo restores the revisions recorded by the committed ChangeSet |
| [0004](0004-bounded-workspace-viewport.md) | accepted | Bounded workspace viewport, canonical render/input mapping, and multi-touch arbitration |
| [0005](0005-pixel-color-representation-and-limits.md) | accepted | Straight sRGB RGBA8 semantics, flat packed storage/shared inverse, and conservative canvas, stroke, patch, and history limits |
| [0006](0006-validated-new-document-runtime.md) | accepted | Typed new-document input, canonical application runtime ownership, and Android lifecycle retention |
| [0007](0007-bounded-pencil-eraser-gesture.md) | accepted | Bounded interpolated gestures and one pencil/eraser Stroke effect path |
| [0008](0008-bounded-tool-palette.md) | accepted | Bounded immutable tool palette and one indexed workspace-selection path |
| [0009](0009-bounded-linear-history-clean-checkpoint.md) | accepted | Bounded linear history cursor, oldest-first eviction, and runtime-local clean-checkpoint identity |
| [0010](0010-generated-critical-journey-baseline-profile.md) | accepted | One generated critical-journey Baseline Profile producer and verified source artifact |
| [0011](0011-change-scoped-verification.md) | accepted | Change-scoped verification, immutable evidence reuse, and bounded prospective performance decisions |
| [0012](0012-bounded-static-controls-color-tolerance.md) | accepted | Issue-#54-only one-step RGB tolerance for canonical static operation controls before the offscreen performance decision |
| [0013](0013-optimized-shipping-release.md) | accepted | One role-aware AGP optimization policy and an immutable accepted-profile consumer comparison |
| [0014](0014-project-format-storage-recovery.md) | accepted | Project-format v1, fresh-document Save As, checked runtime persistence, and one bounded recovery record |
| [0015](0015-project-format-v1-codec-contract.md) | accepted | One bounded project-format byte carrier, closed v1 codec result, and allocation-after-validation mapping |
| [0016](0016-android-project-persistence-retirement.md) | accepted | Android Save As/load operations, one runtime-install path, and real conditional recovery retirement |
| [0017](0017-advisory-dependency-freshness-lint.md) | accepted | Informational `GradleDependency` / `NewerVersionAvailable` / `AndroidGradlePluginVersion` lint and a deliberate monthly catalog refresh cadence |
| [0018](0018-bounded-autosave-debounce-contract.md) | accepted | Bounded autosave capture, 1 s quiet window, 5 s latency cap, `ON_STOP` flush, and one device publication evidence lane |
| [0019](0019-exact-png-export.md) | accepted | Deterministic exact RGBA PNG and one fresh-output/physical-operation path |
| [0020](0020-tablet-editor-appearance.md) | accepted | One workspace appearance owner, aubergine themes and bottom/physical-side tablet controls |
| [0021](0021-app-language-resources.md) | accepted | App language ownership, localized resources and stable UI test identities |
| [0022](0022-indexed-palette-and-migration.md) | accepted | Indexed palette target, atomic draft/document history, explicit v1 conversion and bounded palette JSON |
| [0023](0023-palette-remap-planning.md) | accepted | Complete immutable palette remap and bounded number/nearest/explicit/reorder/delete planning before live cutover |
| [0024](0024-differential-check-selection-and-result-reuse.md) | accepted | Diff-derived check selection, cross-stage reuse of passing results, unrelated-failure scope discipline, and policy-bound automation |
| [0025](0025-indexed-project-compatibility.md) | accepted | Exact indexed v2, typed legacy import and original-preserving recovery conversion before atomic cutover |
| [0026](0026-actual-size-window.md) | accepted | One actual-size window over the canvas: closed integer scales, workspace-owned placement, shared committed bitmap |
| [0027](0027-agent-seat-model-tiers.md) | superseded | Historical Claude model tiers and preparation scripts; current operating policy is ADR 0038 |
| [0028](0028-implementation-seat-shape.md) | superseded | Historical per-stage implementation seats and Opus quota trial; current operating policy is ADR 0038 |
| [0029](0029-quick-select-and-exact-eyedropper.md) | accepted | Floating quick-select control with recent painted slots, workspace-owned menu and one-shot exact-slot eyedropper |
| [0030](0030-ordered-layers-and-empty-pixels.md) | accepted | Up to 16 ordered layers, palette-independent `Empty` cells, one integer composite, project v3 and recovery envelope 3 |
| [0031](0031-phase-gate-device-performance.md) | accepted | Device performance collection once per phase in a gate Issue; feature Issues state per-operation cost and register workloads |
| [0032](0032-reference-underlay.md) | accepted | One workspace-owned reference underlay beneath the layers: bounded decoded image, document-pixel placement, adjust mode, display only |
| [0033](0033-png-import.md) | accepted | PNG import as a new work or a new layer: own bounded exact PNG reader, deterministic colour planner without quantization, one command or one switch |
| [0034](0034-underlay-memory.md) | accepted | The device remembers the reference underlay of each work: a fifth state category, two bounded app-private records per work, least-recently-used eviction, outside the persistence lease |
| [0035](0035-measurement-private-data-preservation.md) | accepted | Phase measurements isolate original underlays and recovery, verify exact file identity and mtime, and retain measurement data before guarded restoration |
| [0036](0036-android-detekt-java-classpath.md) | accepted | Existing typed Android detekt tasks consume the matching Java compiler outputs through tracked task providers |
| [0037](0037-legacy-preservation-session-termination.md) | accepted | The one legacy v1 preservation session ends by fixed guarded moves, with the owner's recorded marker and fractional-mtime exceptions |
| [0038](0038-current-agent-guidance.md) | accepted | Owner-directed roles, task-scoped reading, coherent delegation, and separate rule/state/memory authority; historical recipes do not authorize new work |
| [0039](0039-rectangular-selection-transfer.md) | accepted | Workspace-only rectangular draft, exact same-layer Move/Copy through one patch command, explicit Apply/Cancel, shared Composite preview and bounded transfer work |
| [0040](0040-orthogonal-selection-transforms.md) | accepted | Eight composed exact orientations extend the existing selection transfer with flips and quarter turns; bounded oriented placement, one patch/preview/history path and no schema change |
| [0041](0041-bounded-frames-and-project-v4.md) | accepted | Ordered frames sharing one palette and at most 16 total layer records; one command/history path, captured frame targets, atomic v4/envelope4 cutover and separate playback delivery |
| [0042](0042-workspace-preview-playback.md) | accepted | One workspace Loop/Once session with explicit monotonic samples, bounded catch-up, separate edit/display focus and one lifecycle-aware preview adapter |
| [0043](0043-cohesive-editor-presentation-inputs.md) | accepted | One editor composition entry with explicit editor, persistence and settings capabilities; cohesive file callbacks and eager bounded render projection |

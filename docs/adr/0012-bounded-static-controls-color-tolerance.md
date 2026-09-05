# ADR 0012: Bounded static-control color tolerance for the offscreen diagnostic

- Status: accepted
- Date: 2026-09-06
- Issue: #54
- Affected rules: `ARC-001`, `ARC-004`, `ARC-005`, `ARC-012`, `QLT-005`, `QLT-006`, `QLT-010`, `QLT-011`, `QLT-012`, `QLT-013`, `QLT-014`, `QLT-015`, `QLT-016`

## Context

Issue #54's generated-profile schema-v7 decision remains a fixed FAIL at 0.670013 ms frame-overrun
p95. Retained physical-present-v2 evidence places the long ordinary tail in RenderThread graphics
submission: 3.14-4.31 ms `QueueSubmit` slices spend 2.85-3.56 ms sleeping, while individual graphics
operations are smaller and SurfaceFlinger frames are on time. The source-attributed candidate
therefore retained unchanged upper controls in one Compose offscreen layer to reduce repeated GPU
operation submission without changing document or canvas rendering.

The first exact-pixel oracle rejected source `f0bb36da2af1447673c5c70eee1f0dcce3e60865` before timing.
Within its previously declared app crop, 1,442 upper-control pixels differed from the exact baseline
by at most one value in each unsigned 8-bit RGB channel. The document controls and canvas region were
byte-identical. That result remains a historical correctness FAIL under its original zero-difference
oracle and is not reclassified.

On 2026-09-06 at 00:04 JST, hide accepted a new, narrower prospective question: preserve exact
document/canvas output, geometry, text, semantics, interaction, and alpha, but allow the static upper
operation controls to differ by at most one of 255 channel steps solely to determine whether
offscreen retention improves the unchanged frame thresholds. Permission to measure is not automatic
permission to adopt.

## Decision

Adopt one Issue-#54-only visual oracle identity,
`nene-pixel-m2-static-controls-color-tolerance-v1`, for the new offscreen comparison. It does not
change document color semantics in ADR 0005 or establish a general UI rendering tolerance.

The candidate offscreen layer contains `SelectionControls` only. The `NENE-PIXEL` title,
`DocumentControls`, and `PixelCanvas` remain outside it. The allowed region is not a fixed screen
rectangle: it is the union of the baseline accessibility bounds for these canonical control nodes:

- `Active color` and `Active color swatch`;
- `Palette` and palette-color controls 1 through 8;
- `Pencil tool` and `Eraser tool`.

The baseline and candidate UI hierarchies must expose exactly matching text, content descriptions,
class, bounds, enabled, clickable, and checked values for every text-bearing, described, clickable,
or button app node. Undescribed, non-actionable layout/render-layer containers are excluded because
offscreen retention necessarily adds one such implementation node without adding an accessibility
meaning. Image dimensions must match. Within the allowed union, alpha must be exact and each red,
green, and blue channel may
differ by at most 1. At most 1,442 pixels may differ. Every pixel outside that union, from the
canonical `NENE-PIXEL` title top through the canonical canvas bottom, must be exact. The complete
canvas accessibility bounds must be exact, and every pixel in those bounds must be byte-identical.
The title and all document controls are consequently exact even though they share the inspected app
range.

The oracle compares the exact signed candidate APK with the retained exact baseline source/APK under
the same named physical profile, fresh 16 x 16 document, selected Pencil, top-left committed pixel,
dirty/history state, viewport, and grid. A mismatch is a correctness rejection before latency
collection. Passing this oracle only admits the candidate to the separately fixed performance
experiment; it does not make the candidate acceptable.

Performance experiment identity is `nene-pixel-m2-offscreen-selection-controls-v7-01`. It retains
schema-v7's exact release-like `speed-profile` conditions, five warmups, separately associated DOWN
preview and UP commit frames, 100 ms preview dwell, 350 ms commit wait, four-slot ABBA order
(baseline diagnostic 10, candidate diagnostic 10, candidate decision 50, baseline decision 50),
nearest-rank calculations, one invalid-before-first-DOWN replacement, gross boundaries, and stop
rules. Candidate decision must meet the unchanged absolute thresholds: frame-overrun p95 <= 0 ms,
p99 <= 16.67 ms, and committed-result p95 <= 33.33 ms. Improvement without absolute PASS is failure.

## Rejected alternatives

### Reclassify the prior zero-difference result

Rejected. The prior oracle and result are immutable. The approved tolerance receives a new identity
and prospective comparison.

### Apply one-step tolerance to the canvas or whole screen

Rejected. Document pixels, preview/grid/canvas rendering, title, document controls, system regions,
geometry, alpha, and semantics are outside hide's approval. A screen-wide tolerance could conceal a
real rendering defect.

### Treat the visual decision as a performance-threshold relaxation

Rejected. The numerical frame and committed-result thresholds and supported limits remain exactly
unchanged. The visual oracle is an admission prerequisite, not a latency verdict.

### Keep both render paths at runtime

Rejected by ARC-001 and ARC-004. The candidate is one compile-time implementation. If its fixed
comparison fails, it is reverted; no runtime switch or second renderer remains.

## Consequences

The candidate may be measured despite bounded compositor rounding in the static operation controls.
The tolerance is executable, spatially bounded by canonical UI nodes, capped by observed changed
pixel count, and unable to hide canvas or document-control changes. The candidate adds one disposable
Compose-owned render cache and potential layer memory; it does not own state or alter command,
workspace, history, pixel, dependency, module, or backend paths.

A passing visual oracle and performance decision may justify adoption. Failure at correctness,
gross diagnostic, absolute candidate decision, or comparative baseline stops the experiment and
retains all evidence. A future control layout or label change invalidates the oracle evidence because
the canonical node set/bounds no longer match.

## Enforcement impact

Add one fail-closed PowerShell oracle beside the existing measurement harness and offline regression
fixtures for hierarchy, allowed one-step control color, excessive channel delta, excessive changed
pixel count, change outside the allowed nodes, alpha change, and canvas change. Update the Issue and
frame follow-up before collection. The performance harness, frame thresholds, Gradle dependency
graph, and production ownership rules do not change.

QLT-005 is satisfied by this accepted, candidate-scoped decision. No waiver or suppression is
introduced. QLT-011 through QLT-016 still govern evidence identity, reuse, bounded collection, and
historical-result preservation.

## Migration and rollback

Restore only the offscreen `SelectionControls` presentation wrapper after this ADR and oracle are in
place. Do not restore temporary attribution traces. Build/sign a new exact candidate because the
layer no longer includes the title. If the oracle or fixed comparison fails, revert the presentation
wrapper while retaining this ADR, the immutable results, and the executable oracle as Issue evidence.

If adopted, the offscreen wrapper is the sole presentation path. Reverting it later removes the
single layer without document/schema migration. Expanding tolerance to another screen, control set,
channel delta, changed-pixel cap, or alpha requires another accepted decision.

## Experiment result

The revised `SelectionControls` candidate passed this bounded oracle with identical compared
hierarchy, 1,369 changed pixels inside the canonical allowed-node union, maximum RGB channel delta
1, exact alpha, no changes outside the allowed controls, and an exact canvas. That visual PASS only
admitted the fixed timing experiment.

The candidate decision then retained 50 operations and 100 preview/commit frames and failed the
unchanged absolute gate: frame-overrun p95 was +0.664984 ms, p99 was 2.121810 ms, and committed-result
p95 was 10.346193 ms. The predeclared stop rule prevented the final baseline decision, so no paired
relative-improvement claim exists. The candidate production wrapper was reverted. This ADR and oracle
remain historical decision/evidence records; they do not authorize another collection or make the
failed candidate current behavior.

## Related

- Issue: [#54](https://github.com/hideyukiMORI/NENE-PIXEL/issues/54)
- [ADR 0005](0005-pixel-color-representation-and-limits.md)
- [ADR 0011](0011-change-scoped-verification.md)
- [M2 frame follow-up](../quality/M2_FRAME_FOLLOW_UP.md)
- Supersedes: none; the earlier exact-pixel oracle remains historical
- Superseded by: none

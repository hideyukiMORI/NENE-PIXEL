# ADR 0022: Indexed document palette, atomic history and explicit migration

- Status: accepted
- Date: 2026-09-12
- Issue: #101
- Affected rules: ARC-001/004/005/007/008/009/011/012, CMD-001/002/005–010/012,
  KOT-001–008/013/016–020, QLT-006–009/011–016

## Context

hide requires a replacement palette to recolor a whole sprite by keeping its numbers. Existing
pixels must always refer to the current palette; palette editing needs preview, draft Undo/Redo
and an atomic commit in drawing history. The M3 MVP has exact RGBA pixels, an external fixed
eight-color tool palette (value cap 32), and v1 RGBA files. JSON alone cannot deliver the new
meaning. M3 #89/PR #104 passed on main fa1b235; its evidence remains historical and unchanged.

V1 can contain up to 65,536 distinct RGBA values including hidden RGB. A single 256-entry palette
cannot represent every such file losslessly. This mathematical conflict must be visible before
cutover, not disguised as a successful migration or solved with two editable pixel authorities.

## Decision

### Delivery and applicability

This ADR accepts the M4 target and the bounded preparation work below. It does not claim that the
current M3 editor already uses indexed pixels. The focused cutover replaces all live document,
command, history, renderer and storage consumers together; M3 remains the sole editable path until
then. No feature switch or dual editable RGBA/indexed DocumentState is authorized. Future types
described here are introduced only when the owning implementation Issue consumes them.

First, extend the existing `Palette` value's capacity to 256 and add one `PaletteDefinition`:
an existing immutable `Palette` plus a validated `defaultIndex`. Definitions contain **2–256 actual
entries**, including opaque, partial-alpha and alpha-zero colors. `Palette` still admits one color
as a lower-level value for the existing M3 tool configuration; only `PaletteDefinition` is valid
for interchange or an indexed document. Colors are owned once by Palette, not copied into another
palette model. JSON maps to this definition. Preparation changes no current eight-color UI or
document semantics, and the old 32 cap is removed in that capacity change.

### Document and pixel meaning after cutover

`DocumentState` owns one PaletteDefinition and one indexed PixelSnapshot. A pixel is a validated
PaletteIndex in `0 until entryCount`; zero is a real slot, never a separate transparency sentinel.
PaletteIndex stays the sole slot vocabulary. The snapshot privately owns packed U8 indices, decoded
unsigned at reads, and cannot expose or retain mutable backing. Pixel-engine alone owns mutable
index surfaces/workspace; RGBA is derived by looking up the current definition. No RGBA shadow
snapshot is document truth. PixelColor retains exact straight-sRGB RGBA8 and hidden RGB semantics.

New document creation requires a definition; its defaultIndex fills the canvas and is the Eraser
target. It may identify any RGBA value; transparency is not mandatory. A transparent default is the
initial product preset: transparent black followed by the previous eight colors. Active Pencil
selection is independent and initially red in that preset. Changing the default alone changes
document truth but does not repaint already-existing pixels. Selection remains WorkspaceState.

Pencil captures the selected slot and the gesture's palette/source identity. Palette adoption or
document installation cancels preview under the runtime lock before changing the palette; an old
gesture must not commit into a different definition. Same RGBA at different slots is still a
meaningful document change. Eyedropper reads the editable target's exact slot and emits the existing
selection action; sampling a reference/composited image is a separate import/color-conversion intent.

### Palette edit operations and replacement

| Operation | Pixel/default/selection meaning |
| --- | --- |
| Change slot RGBA | Keep every index; all uses recolor, including alpha/hidden RGB. |
| Append slot | Preserve existing indices; reject above 256. |
| Delete slot | Require an explicit survivor target for references to the removed slot; default is the current defaultIndex when it survives. Otherwise require another target. Decrement higher positions and remap default/selection consistently. Reject fewer than 2 entries. |
| Reorder | Require a complete permutation; reorder colors and apply the same old-to-new mapping to pixels/default/selection, preserving exact appearance. |
| Replace by number (default import mode) | Keep index `i` when `i < newCount`; every out-of-range source index, even currently unused ones, needs an explicit mapping before Apply. The imported defaultIndex becomes the new default. No modulo, clamp, drop or hidden transparency fallback. |
| Replace by nearest color | Map every old slot deterministically to the chosen palette using the metric below; duplicates are not merged in the palette. Imported default becomes the new default. |
| Replace by specified numbers | Require one valid destination for every old slot, allow many-to-one, and use the imported default. |

The complete mapping also relocates the active workspace selection in the same runtime lock via
WorkspaceReducer. Undo/redo revalidate selection (retain it if in range, otherwise select restored
default) through the same action path; undo does not restore historical user tool selections.
Deletion/reorder carry exact inverse index changes, not an inverse of a many-to-one mapping.

Nearest metric v1: exact RGBA match wins first (lowest slot on duplicates). Otherwise minimize
`(r1*a1-r2*a2)^2 + (g1*a1-g2*a2)^2 + (b1*a1-b2*a2)^2 + 3*(255*(a1-a2))^2`
using integer channels and signed 64-bit arithmetic; ties use the lowest destination slot. Its
maximum fits Long. This is a deterministic premultiplied-sRGB/alpha distance, not a perceptual
colorimetric claim. Fully transparent colors retain hidden RGB in exact matches; nearest conversion
may lose it and is explicitly lossy. Dithering is a separate import option, never implicit in palette
replacement, reorder or v1 migration. Image-generated quantization/dithering algorithms require their
own focused decision and golden tests before PNG import ships.

### Draft, command and history ownership

WorkspaceState owns an optional bounded PaletteEditSession with a base token and draft timeline.
The base token includes DocumentId, runtime generation and exact HistoryPosition, not revision alone.
Draft operations change only this session through WorkspaceAction. Preview is derived from the
unchanged document plus draft definition/mapping; it is never saved or autosaved as document truth.
Only one palette session may exist; drawing commits and document-switch requests are disabled while
it is active, while app language/viewport can still change. Cancelling drops the session only.
External changes invalidate its token rather than silently rebasing; Undo back to the exact base
position is equivalent, a replacement branch is not.

Apply validates the complete source token and mapping before allocation/commit and executes one
ReplacePaletteCommand. Pixel indices, palette/default, recorded revisions, history entry, workspace
selection reconciliation, preview removal, invalidation and autosave capture commit atomically.
Failure/staleness leaves all owners unchanged. A successful Apply closes the draft session. Palette
entry edits/default-only edits with identical values and identity mapping are no-ops; equal visible
RGBA is insufficient to call a slot change a no-op.

ChangeSet adds a closed palette transition alongside the indexed patch: exact before/after
definitions and index changes, with a directional shared inverse. No UI closures or full document
snapshot history stacks. Palette-only edits advance revision/history/dirty and invalidate every
dependent rendered pixel even when the index buffer did not change. Cache keys include exact palette
identity and snapshot identity, never revision alone; cache is disposable. Autosave/save captures
the complete immutable document through existing coordination and one physical-operation lease.

Both document history and draft history have 64-entry / 8 MiB **logical retained-payload** budgets.
An indexed patch charges 6 bytes per changed pixel (position I32 + before U8 + after U8), plus 32
bytes transition bookkeeping. Palette transition charges `4*(beforeCount+afterCount)+8` bytes
(ordered RGBA and two default I32); a draft replacement additionally charges 4 bytes per source-slot
mapping, even if references are shared. Count payload once for the shared inverse; conservatively
charge shared palettes per entry. This is deterministic accounting, not total-heap measurement.
Retain the 65,536 patch / 262,144 raw-stroke caps and 524,288 retained changed-pixel cap as additional
guards. Oldest-first eviction, redo truncation and reject-before-commit follow ADR 0009. The new
entry may never become committed but un-undoable. Candidate limits require the cutover Issue's
prospectively accepted latency/retained-memory evidence before shipping; this design supplies no
new performance verdict or inherited measurement permission.

### V1 preservation and v2 admission

V1 bytes and its decoder semantics are immutable. Every valid v1 still receives exact validation and
lossless preview/original preservation. Read-old/write-current does **not** imply that reduction is
lossless migration:

- At most 256 distinct complete RGBA values: deterministic exact conversion assigns slots by first
  row-major occurrence, including hidden RGB. A one-color image gets a second duplicate slot to meet
  minimum 2. Choose the first existing exact transparent-black slot as default; otherwise append
  transparent black if below 256; at a full palette use slot 0 and disclose its exact erase color
  and alpha (it need not be opaque). Preserve DocumentId/revision, exact RGBA and empty history.
  An explicit user-file load starts clean; accepted recovery stays dirty with valid Candidate
  lineage because it has no user-file checkpoint, through the existing installation boundary.
- More than 256 distinct RGBA values: return a typed conversion-required candidate without replacing
  the current runtime or checkpoint. Keep the complete validated source under the application's
  bounded import-operation ownership. Show its exact read-only preview and count; offer cancel,
  lossless original-copy/PNG export, or a separate explicit reduction preview and acceptance. No
  editable RGBA document is installed. Original source URI is never overwritten, and an in-memory
  recovery-only source cannot be retired until a verified lossless original copy exists.
- The first legacy-reduction path selects an existing PaletteDefinition and maps each source RGBA
  by the nearest metric above. It does not require a provisional image-generated quantizer; later
  PNG import may add that separately decided option. Both source preview and reduced preview remain
  available before acceptance. A verified original copy is sufficient preservation even if optional
  PNG export is unavailable; no failed export authorizes retiring a recovery-only source.
- Approved lossy conversion installs a **new DocumentId, revision zero, dirty, empty history**;
  it is a new derived work, not a clean migrated original. Confirmation is bound to current source
  and destination tokens. Cancel/rejection preserves the old runtime and last-safe recovery. Keep
  the exact source until the chosen operation safely completes; bounded bytes cannot be discarded
  in exchange for an unverified write or to satisfy a memory cap.

The nonrepresentable source is an immutable uninstalled import candidate, never another mutable
DocumentState or palette authority. DTO bytes remain in format/adapter; application ports carry
typed immutable import-domain values rather than JSON nodes or format classes. PNG import later
reuses this conversion boundary. The source is capped at the already-supported v1 dimensions and
byte limit; no unbounded preview buffer or recursive container is authorized.

V2 is reserved for indexed document/default/palette persistence. Its exact byte table, carrier
bounds, CRC/goldens, recovery-envelope version dispatch and allocation-before-validation proof must
be accepted in the cutover's compatibility ADR **before** a v2 writer or live indexed runtime ships.
This ADR defines migration semantics, not an invented provisional v2 writer. The same runtime
cutover ships read-v1/write-v2 plus v1/v2 recovery inspection; no interval writes indexed content
as v1 RGBA and loses slot identity. Explicit saved paths remain fresh Save As. After v2 files exist,
rollback must retain v1/v2 readers or ship no rollback that abandons data.

### JSON interchange and extensibility

[Palette JSON v1](../PALETTE_JSON_V1.md) is the single palette-file contract, separate from project
storage and future animation metadata. `:core:project-format` owns the no-I/O codec and typed errors;
`:core:domain` owns Palette/PaletteDefinition validation. Application later exchanges definitions
through one palette-file port; adapters perform only bounded SAF transport. Import stages a draft;
export of a definition never mutates document/dirty state. No import applies immediately.

No dependency/module/plugin is added. A schema-specific, bounded JSON reader handles only the four
defined fields, JSON strings/escapes, integer tokens and the color array; it is not a reusable generic
JSON framework. Unknown/duplicate keys and unsupported forms fail typed, nesting is fixed, and strict
UTF-8 is validated. This deliberate small grammar keeps duplicate-field rejection, resource bounds
and domain-free serialization without a compiler plugin or externally visible JSON object model.

The document palette is shared by future frames; palette changes affect all frames. FrameId/TileId
are stable references independent of order/filename, and a future manifest can expose directory-like
exports without making sequence filenames the editable identity. Tiles/maps reference stable assets
and frames. No empty frame/layer/map arrays, dummy module or unused public frame API is added now.
Reference-image pixels remain a separate source with display opacity. Temporarily dimming/hiding all
editable layers is workspace display state, never destruction of artwork or a palette member.
Long-press selection uses a typed quick-menu context/action vocabulary extensible to color/tool/zoom;
its first implementation stays separate from the indexed migration.

## Rejected alternatives

- Recolor RGBA pixels by old color equality: loses distinct slots and cannot preserve duplicate-color
  meanings or many-to-one history. A mutable indexed canonical document is required.
- Keep RGBA and indexed editable documents indefinitely: duplicates drawing/history/save semantics.
- Clamp missing indices or silently reduce old v1: corrupts meaning without a reviewable decision.
- Store draft in Compose or apply edits one at a time: defeats cancel, atomic history and stale checks.
- Store animation as filename references in the core: renames/reordering would change identity.

## Consequences

Benefits are predictable sprite recoloring and exact Undo with one state owner. Costs include a
cross-module cutover, explicit legacy-reduction UX and indexed palette-aware invalidation.
The one-color lower-level Palette is transitional compatibility, not a second indexed definition.

## Enforcement impact

Preparation verifies 2/256 boundaries, ownership, JSON golden/deterministic/error/resource cases,
plus relevant narrow static/consumer checks. Cutover tests same-RGBA distinct slots, palette-only
revisions, many-to-one inverse, reorder/delete/default/selection, stale branch/runtime tokens,
history-byte boundaries, v1 distinct-color boundaries and interruption/recovery before/after
conversion. It updates custom architecture checks where ownership/storage vocabulary changes;
no waiver, suppression, gate weakening or per-edit full suite. Each final PR needs quality CI.

## Migration and rollback

The preparation Issue consumes PaletteDefinition with JSON while leaving M3 runtime unchanged.
Revert that whole preparation before consumer adoption if needed. The later atomic runtime cutover
removes tool-config palette ownership and RGBA stroke/patch storage, updating ADR 0005/0007/0008/0009
claims and tests together. Current historical evidence is not rewritten as indexed evidence.

## Focused delivery Issues

1. P4-01 / #105 adds PaletteDefinition and bounded palette JSON only; current M3 editing is unchanged.
2. P4-02 / #106 first accepts exact v2/recovery compatibility and prospective measurement contracts,
   then performs the single indexed runtime/storage cutover including safe legacy conversion UX.
3. P4-03 / #107 connects draft editing/history, mapping preview and JSON SAF transport to that runtime.
4. P4-04 / #108 adds exact-slot eyedropper and extensible long-press selection to the resulting shell.

Issue state remains in GitHub. Layers/frames, PNG-generated quantization, tiles/maps and reference
images retain outcome-level requirements; palette completion alone cannot close M4.

## Related

- Issue #101; M3 completion #89 / PR #104
- Refines ADR 0005/0007/0008/0009/0014/0015/0016/0018/0019 at the stated cutover boundary
- [Palette JSON v1](../PALETTE_JSON_V1.md)

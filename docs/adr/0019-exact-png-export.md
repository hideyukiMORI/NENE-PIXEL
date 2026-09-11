# ADR 0019: Exact PNG export

- Status: accepted
- Date: 2026-09-12
- Issue: #88
- Affected rules: `ARC-001`, `ARC-003` through `ARC-010`, `ARC-012`, `CMD-009`,
  `CMD-010`, `KOT-001` through `KOT-008`, `KOT-014`, `KOT-017` through `KOT-020`,
  `QLT-006`, `QLT-008`, `QLT-011` through `QLT-016`

## Context

The merged snapshot contains straight sRGB RGBA8, including significant hidden RGB at alpha zero.
P3-03 already supplies fresh-destination selection, bounded transport, close/read-back and cleanup.
PNG must preserve pixels without marking a project saved or retiring recovery. Premultiplied Bitmap
rendering cannot guarantee that contract. Platform compression adds a variable encoder authority.

## Decision

One internal `PngEncoder` in `:adapters:persistence` reads a defensive packed copy of the supplied
immutable snapshot without modifying it. PNG encoding policy v1 is the following fixed contract:

- PNG signature, then exactly IHDR, sRGB, IDAT, IEND.
- IHDR: bit depth 8, color type 6, compression/filter method 0, non-interlaced.
- sRGB: fixed relative-colorimetric intent 1; no color conversion or other metadata.
- Each row: filter None (0), exact row-major RGBA bytes, including hidden RGB and low alpha.
- IDAT: zlib header 78 01, one byte-aligned stored DEFLATE block per row, then Adler-32.
  LEN/NLEN are little endian; BFINAL is set only on the final row. Chunk lengths/CRC are big endian.
- Existing JDK/Android CRC32 and Adler32 compute checksums; no compression library, dependency,
  module or plugin is added. No clock, locale, density, Bitmap or renderer enters the encoder.
- Exact output length: `76 + height * (4 * width + 6)`, from 86 to 263,756 bytes. Row payload is at
  most 1,025 bytes. `PngBytes` defensively owns immutable bytes and exposes only copies and count.

ARC-005 now also permits private bounded PNG encoding/transport bytes in the existing adapter under
the project/recovery byte ownership restrictions. It does not permit mutable pixel work surfaces.
PNG uses the standard format without a private version chunk; project/recovery v1 stay unchanged.

`PngExportPort.export(DocumentState): PngExportOutcome` is the one application-owned port, with
Exported, Cancelled, and Failed using existing typed storage failure and partial-output-cleanup
vocabulary. `EditorPersistenceWorkflow.exportPng()` captures one immutable document under the runtime
lock and acquires the existing physical-operation lease. Exporting and PngExported are derived phase
and last outcome. Completion checks operation/runtime identity and never changes document, history,
workspace, clean checkpoint, autosave capture or recovery. Editing continues. Other operations remain
Busy; active autosave uses the existing bounded wait/one retry. Cancellation retains the lease until
the port and cleanup drain, and late completion cannot report success.

One typed `DocumentCreationRequest` selects PROJECT or PNG and a suggested filename through the
existing picker broker and Activity Result launcher. PNG uses image/png and .png; project retains its
MIME/extension. A claimed picker request drains the actual Activity Result after cancellation; an unclaimed request
is removed immediately. Any cancelled Selected fresh URI reaches adapter cleanup before the lease
is released, so an old result cannot complete a new request. No existing URI is accepted for overwrite. One shared fresh-output writer closes then
reads back bytes under the selected bound. Exact equality with prepared output proves identical
decoding, avoiding a second production PNG decoder. Project save retains pre-picker encode/decode
validation; the writer's repeated project decode is replaced by this shared identity proof.
The ViewModel retains one user-operation Job before starting it, including while waiting for autosave.
Duplicate requests are not queued; cancellation reaches this job after a delayed lease acquisition.
The existing dispatcher/ViewModel own I/O and completion; UI adds an Export PNG action in existing
controls. New APIs are limited to that port/outcome, workflow phase/action and typed picker input.

## Rejected alternatives

- Bitmap compression can lose hidden RGB and low-alpha channels.
- A general image/compression dependency is unnecessary for the bounded stored-block encoder.
- PNG in project-format mixes raster export with project compatibility ownership.
- Independent export state/job/writer duplicates the existing physical lease and SAF safety path.
- Marking clean after export is wrong because PNG does not preserve the project contract.

## Consequences

All RGBA values and encoded bytes are reproducible. Stored DEFLATE trades compression for a small,
bounded encoder: maximum PNG is about 258 KiB. Provider waiting can delay autosave under the existing
single-operation policy. Read-back proves visible output at completion, not future hardware/cloud
durability. Failed best-effort deletion can leave a partial newly created output.

## Enforcement impact

The Issue and `M3_PNG_EXPORT_EVIDENCE.md` record scope/commands before execution. Independent golden
bytes and JDK ImageIO decode cover dimensions, row/channel order, hidden RGB, alpha 0/1/127/254/255,
rectangular/minimum/maximum inputs, checksums, deterministic metadata, defensive ownership and exact
retained encoded-payload count. This count is not a total JVM heap or latency measurement. Application
contracts cover unchanged owners/checkpoint/recovery, edits, Busy, cancellation drain and stale
completion. Shared transport contracts cover permission/provider/I/O faults, bounded reads, close,
read-back mismatch and cleanup failure. Focused Android tests cover MIME/result/ContentResolver/UI.
No performance collection, profile generation, forced cold build or local full suite is triggered.
Required final-candidate quality CI is the full gate. No waiver is needed.

## Migration and rollback

Update all workflow/picker consumers and remove the old writer path in this change. No project or
recovery migration is needed. Rollback removes PNG port/operation/encoder/UI together while retaining
one tested project Save As writer.

## Related

- Issue: #88 / P3-05; builds on ADR 0014, ADR 0016 and ADR 0018
- [PNG specification](https://www.w3.org/TR/png-3/): sections 6.2, 7, 10, 11.2, 11.3.2.5
- [RFC 1951](https://www.rfc-editor.org/rfc/rfc1951): stored blocks, section 3.2.4
- [RFC 1950](https://www.rfc-editor.org/rfc/rfc1950): zlib header and Adler-32

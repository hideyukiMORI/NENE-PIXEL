# NENE-PIXEL Project Format Version 4

Status: normative byte-contract authority for schema version 4; ADR 0041 / Issue #215

This is write-current with Issue #216's atomic frame runtime/storage/UI cutover under
[ADR 0041](adr/0041-bounded-frames-and-project-v4.md), following design Issue #215.
[V1](PROJECT_FORMAT_V1.md), [v2](PROJECT_FORMAT_V2.md) and the v3 byte table in
[ADR 0030](adr/0030-ordered-layers-and-empty-pixels.md#project-v3-and-migration) remain their exact
historical byte authorities. V4 does not amend their bytes or version-specific limits.

## Meaning

V4 stores one DocumentId, Revision, CanvasSize, shared ordered PaletteDefinition and chronological
list of frames. Each frame stores its stable ID, integer-millisecond duration and bottom-first
ordered layer list. Layer records retain v3's exact IDs, names, visibility, coverage and palette
indices. All frames use the same canvas and palette. All palette slots, including duplicates and
unused colors, retain their order and exact straight-sRGB RGBA8 channels; alpha-zero hidden RGB is
significant. Empty is palette-independent transparency, with index byte zero.

The extension and MIME type remain `.nenepixel` and `application/octet-stream`. Multibyte integers
are big-endian. There is no compression, alignment padding, extension container or reserved field.
Workspace focus, frame playback state/mode, history, selection, caches, underlay, URI, runtime and
operation identities, recovery generation and layer opacity are not in the file.

## Canonical byte layout

Let `P` be palette count, `N = width * height`, `B = ceil(N/8)`, `F` frame count and `L` total
layer-record count across all frames. `F` and each per-frame count are in1..16; `L <= 16`.

| Offset | Length | Field | Encoding |
| ---: | ---: | --- | --- |
| 0 | 8 | magic | `4E 45 4E 45 50 49 58 00` (`NENEPIX` plus NUL) |
| 8 | 2 | version | unsigned16-bit4 |
| 10 | 2 | width | unsigned16-bit1..256 |
| 12 | 2 | height | unsigned16-bit1..256 |
| 14 | 16 | DocumentId | exact decoded pairs of its32 lowercase hexadecimal characters |
| 30 | 8 | Revision | unsigned0..Long.MAX_VALUE, high bit zero |
| 38 | 2 | palette count P | unsigned16-bit2..256;256 is `01 00` |
| 40 | 1 | defaultIndex | unsigned slot strictly below P |
| 41 | `4*P` | palette | ordered exact R G B A bytes |
| `41+4*P` | 1 | frame count F | unsigned1..16 |
| next | variable | frames | F frame records below, chronological order |
| final | 4 | checksum | unsigned big-endian CRC-32/ISO-HDLC of all preceding bytes |

For each frame:

| Relative offset | Length | Field | Encoding |
| ---: | ---: | --- | --- |
| 0 | 4 | FrameId | unsigned1..Int.MAX_VALUE, document-unique; high bit zero |
| 4 | 4 | duration | unsigned milliseconds10..60,000 |
| 8 | 1 | layer count | unsigned1..16; running document sum may not exceed16 |
| 9 | variable | layers | that many records below, bottom first |

For each layer (the v3 record, unchanged):

| Relative offset | Length | Field | Encoding |
| ---: | ---: | --- | --- |
| 0 | 4 | LayerId | unsigned1..Int.MAX_VALUE; globally unique across every frame |
| 4 | 1 | flags | bit0 is visible; all other bits zero |
| 5 | 1 | name byte count T | unsigned0..128 |
| 6 | T | name | strict UTF-8,0..32 Unicode code points, no control characters |
| `6+T` | B | coverage | row-major, least-significant bit first in each byte; unused high bits zero |
| `6+T+B` | N | indices | row-major U8; Empty cells zero, covered cells strictly below P |

IDs are not ordinal positions and need not increase in file order. Names need not be unique.
Layers may all be hidden; that is a valid saved work, though exporting its frame has the existing
NoVisibleLayer outcome. Covered palette slot0 differs from Empty even when slot0 is transparent.
Changing a name's Unicode normalization, sorting records or deduplicating colors is not permitted.

The exact length is:

```text
41 + 4*P + 1 + 9*F + sum_layers(6 + T + B + N) + 4
```

Minimum: `41+8+1+9+(6+0+1+1)+4 = 71` bytes for1x1, two colors, one frame and one empty-name layer.
Maximum: `41+1024+1+16*9+16*(6+128+8192+65536)+4 = 1,183,006` bytes. It uses16 frames with one
maximum-name layer each. Maximum visible-stack compositing instead uses one frame with16 layers;
its file is135 bytes smaller. Both have the same maximum packed pixel payload1,179,648 bytes.
Length arithmetic is checked Long before narrowing; malformed counts never justify larger storage.
The CRC algorithm/reflection/initialization/final XOR and `123456789` check vector are exactly v1's.

## Dispatch, validation and allocation order

The common ProjectFormatBytes carrier accepts at most the1,183,007-byte oversize probe. The shared
codec file cap is1,183,006. Known excessive transport length is rejected before read/copy; an unknown
stream stops after cap-plus-one. Bounded carriers remain untrusted until decoding succeeds.
V1/v2/v3 keep their own respective262,186 /66,605 /1,182,862-byte file limits. The shared larger
carrier never relaxes an older decoder. Current project reads and verified write read-backs share
the same bounded reader.

The one ProjectFormatCodec preserves common dispatch precedence: first common maximum, then the
38-byte common minimum, exact magic, unsigned wire version and explicit version dispatch. Inputs
shorter than38 return Truncated(required38) before magic/version inspection. Unknown versions,
including zero and future5, return the existing typed unsupported-version result, never a fallback.
For a dispatched v4 body the order is:

1. Enforce v4 maximum and the41-byte fixed header requirement.
2. Validate dimensions, document identity/revision, palette count and default index through the
   same canonical bounds as earlier indexed versions.
3. Require the complete palette span and frame-count byte. Validate F before frame metadata
   allocation. This bounded structural pass may retain at most16 frame descriptors and16 layer
   descriptors, not domain snapshots or raster arrays.
4. Walk each9-byte frame header and each6-byte layer header with checked offsets. Validate frame
   ID/uniqueness, duration, nonempty layer count and running global count before accepting payload
   spans. Validate global layer ID/uniqueness, flag bits and name byte count. Require each full
   name/coverage/index span before reading or skipping it. A truncated header/span yields a typed
   required-length result; no read beyond input occurs.
5. Require exactly four checksum bytes after the final record, rejecting truncation or trailing
   data, then verify CRC over the exact preceding body.
6. Construct the small ordered Palette/PaletteDefinition. Validate every layer name's strict
   UTF-8/code-point/control-character rules, coverage padding, zero Empty indices and covered
   palette membership in chronological-frame, bottom-first-layer, row-major-cell order. This
   validation of all records completes before any PixelSnapshot is constructed.
7. Construct snapshots, Layers, Frames and DocumentState through their sole validated factories.
   Structural scan, bytes and typed state cannot disagree; no partially valid document escapes.

Expected failures are closed typed outcomes: invalid frame count/ID/duration, duplicate frame ID,
invalid per-frame or total layer count, invalid/duplicate global layer ID, flags/name/coverage/cell,
dimension/revision/palette, length/checksum and unsupported version. Existing applicable diagnostics
are reused; a frame or layer position may identify an invalid raw field without inventing a valid
domain ID. Invalid unsigned32-bit values must be reported before conversion to a signed Int.
The decoder never clamps, drops records, changes duration or normalizes names to accept input.
Internal snapshot-mapper seams may verify allocation ordering; they are not public strategies.

## Write-current and migration

ProjectFormatCodec.encode(DocumentState) emits only v4. Identical
documents yield identical bytes, independent of workspace focus, clock, locale or renderer. Frame
and layer iteration follow their saved orders. A visible composite never substitutes for indexed
records. There is no editable-model v1/v2/v3 writer or silent downgrade to a single frame.

- V3's exact DocumentId, Revision, PaletteDefinition and ordered layers are wrapped in frame ID1,
  duration100 ms. Layer IDs/names/visibility/coverage/indices are unchanged.
- V2 retains its existing lossless fully covered visible layer ID1/empty name, now in that same
  default frame. Its palette/default and exact indexed colors remain unchanged.
- V1 remains an uninstalled LegacyRgbaSource with the existing explicit lossless/conversion-required
  choice and original-preservation flow. Its conversion creates one default frame. Its exact v1
  encoder remains restricted to copying that original source.

Default duration/FrameId assignment is migration, not a document command. A lossless user-file
load remains clean and acquires no history entry. Saving may write the new format through the
existing fresh destination flow. Inspection/loading does not overwrite the original file or
recovery. Recovery adoption retains its existing checkpoint/dirty meaning, not user-load semantics.

## Recovery envelope4

The outer envelope layout, generation identity, checksum and file identity are unchanged from
ADR0016/0025/0030. Envelope version4 Candidate contains exactly one project-v4 payload; Retired has
none. New Candidate and Retired records both use version4. Readers accept only matching1/1,2/2,3/3
and4/4 pairings. After the common length admission, a future envelope is unsupported. Inside a
known Candidate envelope, any nested project-version mismatch (including zero or a future version)
is corrupt before project decoding, preserving the existing pairing-check precedence.

| Envelope | Project version | Candidate minimum | Candidate maximum | Retired |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 1 | 69 | 262,209 | 23 |
| 2 | 2 | 77 | 66,628 | 23 |
| 3 | 3 | 85 | 1,182,885 | 23 |
| 4 | 4 | 94 | 1,183,029 | 23 |

Candidate is exactly project length plus23 bytes; common recovery probe is1,183,030. Older
version-specific bounds and exact pairing remain enforced even inside this larger carrier. Current
write/read-back verification, physical-operation ordering, conditional generation retirement and
AtomicFile rollback are unchanged. A corrupt/future record does not silently retire or replace an
undecided Candidate. The writer, decoder, bounded reader and publication verifier switch together.

## Independent byte examples and required evidence

Both examples use DocumentId `00000000000000000000000000000001` and revision0. These bytes are
independent Python struct/zlib calculations for the contract, not evidence that a production v4
codec has been implemented or tested.

Minimal1x1: palette `[00000000, ff0000ff]`, default0, frame ID1 duration100, layer ID1 visible with
empty name, coverage01 and index01. Length71; checksum `2559ac80`:

```text
4e454e455049580000040001000100000000000000000000000000000001000000000000000000020000000000ff0000ff0100000001000000640100000001010001012559ac80
```

Two3x2 frames: palette `[11223300, ff0000ff, ff0000ff]`, default2. First frame ID4 duration100 has
visible layer ID7/name `A`, coverage2d and indices `[0,0,2,1,0,0]`; cells1 and4 are Empty while
cells0 and5 cover alpha-zero slot0. Second frame ID2 duration250 has hidden layer ID9/empty name,
coverage3f and indices `[0,1,2,2,1,0]`. Length103; checksum `c67f056d`:

```text
4e454e455049580000040003000200000000000000000000000000000001000000000000000000030211223300ff0000ffff0000ff02000000040000006401000000070101412d00000201000000000002000000fa010000000900003f000102020100c67f056d
```

The consuming implementation supplies committed independent goldens and exact round trips,
including these vectors,16-frame maximum and1-frame16-layer cases; every count/ID/duration/name/
coverage/index limit; late invalid-record allocation rejection; truncation/trailing/CRC and probes;
v1/v2/v3 migration with unchanged old golden bytes; all envelope pairings, maxima and read-back
failure. Single-frame PNG output, captured-frame asynchronous export/import and all-frame palette
remapping are separate consumer contracts. Old v3 phase fixture bytes/hash are retained. Any new
v4 maximum-byte performance fixture receives a distinct versioned identity under #145 before
collection; documentation and codec correctness do not establish a performance result.

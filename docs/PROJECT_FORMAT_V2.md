# NENE-PIXEL Project Format Version 2

Status: normative byte-contract authority for schema version 2; ADR 0024 / Issue #106

This contract is consumed only by the atomic indexed runtime/storage cutover in
[ADR 0024](adr/0024-indexed-project-compatibility.md). It does not enable a writer before that cutover.
[Project Format v1](PROJECT_FORMAT_V1.md) remains the immutable authority for version 1.

## Meaning

V2 stores exactly one DocumentId, Revision, CanvasSize, ordered PaletteDefinition and row-major
palette-index raster. The extension and MIME type remain `.nenepixel` and `application/octet-stream`.
All multibyte integers are big-endian. There is no compression, padding or extension container.
Palette colors are exact straight-sRGB RGBA8, including alpha-zero hidden RGB. Duplicate colors
remain distinct slots. Index zero is an ordinary slot; only defaultIndex defines new-fill/erase.
All actual entries are stored, including unused entries. Neither sorting nor deduplication is valid.

History, workspace, selection, cache, URI, runtime/operation identity and recovery generation are not
stored. No layer/frame arrays, reserved fields, second color raster or premultiplied pixels exist.

## Canonical byte layout

Let `P` be palette entry count, `N = width * height`. The exact file length is `45 + 4*P + N` bytes.

| Offset | Length | Field | Encoding |
| ---: | ---: | --- | --- |
| 0 | 8 | magic | `4E 45 4E 45 50 49 58 00` (`NENEPIX` plus NUL) |
| 8 | 2 | version | unsigned 16-bit `2` |
| 10 | 2 | width | unsigned 16-bit `1..256` |
| 12 | 2 | height | unsigned 16-bit `1..256` |
| 14 | 16 | DocumentId | exact decoded pairs of the 32 lowercase hexadecimal characters |
| 30 | 8 | Revision | unsigned value in `0..Long.MAX_VALUE`, high bit zero |
| 38 | 2 | palette count | unsigned 16-bit `2..256`; `256` is `01 00`, never zero |
| 40 | 1 | defaultIndex | unsigned 8-bit slot, strictly below P |
| 41 | `4*P` | palette | ordered exact `R G B A` bytes per slot |
| `41 + 4*P` | N | pixels | unsigned 8-bit indices in increasing y, then increasing x; each below P |
| `41 + 4*P + N` | 4 | checksum | unsigned big-endian CRC-32/ISO-HDLC of every preceding byte |

Valid v2 files contain 54 through 66,605 bytes. The minimum is a 1x1 canvas with two entries;
the maximum is 256x256 with 256 entries. All counts and expected lengths use checked Long arithmetic
before narrowing. The CRC algorithm, reflection, initialization, final XOR and `123456789` check
vector are exactly those already fixed in v1. There is no alternate CRC or format-specific polynomial.

## Dispatch and resource bounds

The common bounded carrier retains the v1 maximum 262,186 and maximum-plus-one probe 262,187.
A smaller v2 file must not make valid v1 sources unreadable. Known oversized input is rejected before
read/copy; unknown streams stop at the probe. The carrier is not proof of a valid version or body.

One codec preserves the existing v1 diagnostic precedence: after the common maximum check, any
input shorter than 38 bytes returns Truncated(actualByteCount, requiredByteCount=38), including
0..7, 8..9 and 10..37-byte inputs. It does not inspect magic/version in a shorter input. With at
least 38 bytes it checks magic and unsigned wire version, then dispatches explicitly to the
corresponding internal version decoder. Unknown versions, including zero, return the existing
typed unsupported-version outcome. V1 uses its unchanged validation and byte interpretation.
V2 independently rejects bytes above 66,605, even when they fit the common carrier.

Before constructing a v2 document:

1. Enforce the common transport/carrier maximum, the 38-byte common minimum and exact magic.
2. Decode the wire version before interpreting a version-specific body; select v2 only for `2`.
3. Enforce the v2 maximum and fixed 41-byte header requirement.
4. Validate dimensions through canonical domain factories, then identity/revision.
5. Validate P in `2..256` and defaultIndex below P before palette or raster allocation.
6. Calculate exact length with checked Long arithmetic; reject truncation and trailing bytes.
7. Verify the stored checksum over the entire exact body.
8. Construct the immutable ordered Palette and PaletteDefinition through their domain factories.
9. Validate every raw unsigned pixel index through that Palette's entryAt membership boundary,
   before domain snapshot allocation.
10. Construct the packed-U8 PixelSnapshot and validated DocumentState through their domain factories.
    No partially valid state escapes.

Input-length, unsupported-version, canvas/revision, palette-count/default/index, exact-length and
checksum failures are closed typed outcomes. Diagnostics may identify the offending row-major pixel
and slot, but never install a fallback, clamp a value, discard an entry or use exceptions for rejection.
The additional ProjectFormatRejection cases are InvalidPaletteEntryCount(actualCount, minimum=2,
maximum=256), DefaultIndexOutsidePalette(index: PaletteIndex, entryCount), and
PixelIndexOutsidePalette(position: PixelPosition, index: PaletteIndex, entryCount). Count fields
are diagnostic integers, not unchecked palette definitions. Raw unsigned slots convert through the
existing PaletteIndex factory; membership uses the existing domain Palette.entryAt boundary.
The implementation must keep palette membership policy consistent with the domain factory; internal
mapper injection may prove allocation order in tests but is not a public strategy interface.

## Write-current and original-copy separation

Encoding an editable DocumentState always emits v2. The encoder preserves every slot, index,
default, channel, identity and recorded revision. Identical documents produce identical bytes.
It never reads locale, clock, runtime settings or renderer output.

The version-1 encoder may be retained only for exact original-copy export of the distinct immutable
legacy-import source type. It cannot accept indexed DocumentState. Every accepted v1 byte string has
one canonical re-encoding: fixed fields, exact pixels and deterministic CRC leave no unrepresented
optional bytes. Original-copy tests must prove byte-for-byte equality with the decoded source.
This original preservation operation never marks the active or derived work clean.

## Required evidence

Proposed independent fixture calculations (DocumentId `00000000000000000000000000000001`,
Revision zero) give these exact bytes. The first is 1x1, palette [transparent black, opaque red],
default zero, pixel one, length 54 and checksum `6516fb65`:

```text
4e454e455049580000020001000100000000000000000000000000000001000000000000000000020000000000ff0000ff016516fb65
```

The second is 3x2, palette [`11223300`, `ff0000ff`, `ff0000ff`], default two,
pixels [0,1,2,2,1,0], length 63 and checksum `16daec25`:

```text
4e454e455049580000020003000200000000000000000000000000000001000000000000000000030211223300ff0000ffff0000ff00010202010016daec25
```

These are byte-contract examples independently calculated with Python struct/zlib, not a claim that
the Kotlin codec exists or passes. Production golden tests must verify them independently of encoding.

- Independent hand-checkable minimal and rectangular v2 golden bytes, plus duplicate colors,
  hidden RGB, unused slots, nontransparent default, slot 255 and count 256.
- Deterministic encode/decode, exact same-RGBA/different-slot preservation and palette/default-only
  differences; all current v1 goldens retain their exact bytes and semantic source values.
- Every structural truncation, extra byte, version, count, default/index, checksum and resource
  rejection, including valid-v1 inputs larger than the v2 maximum and maximum-plus-one probes.
- Instrumented mapper contracts showing structural/checksum/membership failure before snapshot
  allocation, and factory aliasing/equality/boundary contracts.
- Original-copy re-encoding is exact for all accepted v1 fixtures, including maximum-size sources
  with more than 256 distinct RGBA values. Such sources are not silently converted into documents.
- Cross-version recovery, atomic runtime installation, lossless/lossy migration, cancellation and
  last-safe preservation follow ADR 0024 and the owning Issue's scoped verification plan.

No result is claimed before implementation and its required evidence. Changing this layout after
acceptance and v2 shipping requires another compatibility ADR; rollback must retain both readers.

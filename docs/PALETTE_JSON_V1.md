# NENE-PIXEL Palette JSON Version 1

Status: normative interchange contract accepted by [ADR 0022](adr/0022-indexed-palette-and-migration.md), Issue #101.
This is a palette definition, not a project, tool-selection preset or animation manifest.

```json
{"format":"nene-pixel-palette","version":1,"defaultIndex":0,"colors":["#00000000","#ff0000ff"]}
```

## Meaning

Exactly four case-sensitive object members are required, in any input order:

| Member | Type and meaning |
| --- | --- |
| `format` | Exact string `nene-pixel-palette` |
| `version` | Integer token `1`; unsupported versions are rejected |
| `defaultIndex` | Zero-based integer slot, `0 <= value < colors.length`; fills new canvases and is the Eraser target |
| `colors` | Ordered array of 2–256 strings, exactly `#RRGGBBAA`; hex digits case-insensitive on input |

Numbers used as slots are zero-based; the UI may display one-based labels with explicit conversion
at presentation only. RGBA is straight sRGB; alpha is always required. Hidden RGB and duplicate RGBA
entries are preserved byte-for-byte as semantic values and are never deduplicated or sorted.
Alpha-zero/partial-alpha entries count toward the real 256 limit. defaultIndex may be nontransparent.

Missing/unknown/duplicate members, a different root type, nulls, booleans, nested color arrays,
comments, trailing commas/data, short hex, named colors, floats/exponents, malformed UTF-8 and
invalid JSON escapes are rejected. Integer grammar is JSON's `-?(0|[1-9][0-9]*)`; negative/out-of-range
values are typed semantic rejections, overflow a typed numeric rejection. Object names/strings
support standard JSON escapes, including equivalent escaped ASCII; duplicate detection uses decoded
names. Non-ASCII strings that are valid JSON still fail their required schema value/name checks.
JSON whitespace is space/tab/CR/LF only. One leading UTF-8 BOM is accepted; never emitted.

## Canonical bytes and limits

UTF-8, no BOM; key order is `format`, `version`, `defaultIndex`, `colors`; decimal integers without
leading zeros; lowercase eight-digit hex; no insignificant whitespace; exactly one final LF.
The example above plus LF is the minimal two-entry golden. Encoding the same definition always
produces identical bytes, independent of locale, time, original whitespace or property order.

Input/output bound is **16,384 bytes** including any BOM/LF/whitespace. A defensive immutable
PaletteJsonBytes carrier accepts at most one 16,385-byte oversize probe, which decode rejects before
UTF-8 decoding or domain allocation. The transport reads at most that probe limit. No source
string, token, collection or output buffer can exceed this fixed envelope. The color reader stops
at a 257th entry before constructing another PixelColor or Palette. It parses one root and one
array only; unexpected nesting fails without recursive descent. At most 256 RGBA values are retained.
PaletteJsonCodec is no-I/O and consumes/returns the domain PaletteDefinition through a closed
success/rejection result. Error messages are localized only in presentation, not encoded into files.

## Import/export behavior

Import chooses a replacement mode and stages a draft preview; it never changes a live document on
decode. Default mode preserves slot numbers, with explicit assignments for missing source slots.
Nearest and complete explicit mappings are alternatives under ADR 0022. Apply is one document
command; draft and committed Undo have the different owners defined there. Export writes a fresh
palette JSON destination, preserving current document/history/checkpoint. Suggested suffix is
`.nenepalette.json`, MIME `application/json`; filenames and extensions are not validation authority.
Unknown future versions require a versioned migration decision; there is no permissive fallback.

## Verification before release

Independent golden bytes; deterministic encode/decode; all 256 slots with duplicate/partial-alpha/
hidden-RGB cases; 1/2/256/257 boundaries; default 0/last/outside; escaped strings and duplicate keys;
bad UTF-8/BOM/number/JSON shapes; exact envelope/probe limits and defensive byte/list ownership.
Malformed input returns typed results without changing caller state. A format implementation must
record bounded allocation/copy evidence under QLT-011; no performance claim follows from this spec.

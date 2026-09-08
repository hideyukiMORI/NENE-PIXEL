# NENE-PIXEL Project Format Version 1

Status: normative byte-contract authority for schema version 1

This document is the only authoritative byte layout for exported NENE-PIXEL project files. ADR 0014
records why this format and its surrounding save/load boundary were chosen; it does not duplicate
the byte table below.

## File identity

- File extension: `.nenepixel`
- MIME type: `application/octet-stream`
- Byte order for every multi-byte integer: big-endian
- Schema version written by the v1 encoder: `1`
- Compression: none
- Pixel order: row-major, increasing `y` and then increasing `x`
- Pixel bytes: exact straight-sRGB `R`, `G`, `B`, `A`, one unsigned byte per channel

History, `HistoryPosition`, clean checkpoints, workspace state, palette configuration, viewport,
render caches, timestamps, storage URIs, recovery metadata, layers, frames, and reserved extension
fields are not serialized in v1. The single-frame, single-layer MVP document is exactly its
`DocumentId`, `Revision`, `CanvasSize`, and pixels.

## Canonical byte layout

Let `N = width * height`. The only valid v1 length is `42 + 4 * N` bytes.

| Offset | Length | Field | Canonical value or encoding |
| ---: | ---: | --- | --- |
| 0 | 8 | magic | `4E 45 4E 45 50 49 58 00` (`NENEPIX` followed by NUL) |
| 8 | 2 | schema version | unsigned 16-bit integer `1` |
| 10 | 2 | width | unsigned 16-bit canvas width, `1..256` |
| 12 | 2 | height | unsigned 16-bit canvas height, `1..256` |
| 14 | 16 | document identity | the 32 lowercase hexadecimal `DocumentId` characters decoded pair-by-pair, first pair first; leading zero bytes are retained |
| 30 | 8 | revision | unsigned 64-bit value in `0..Long.MAX_VALUE`; the high bit is always zero |
| 38 | `4 * N` | pixels | `N` exact RGBA8 pixels in canonical row-major order |
| `38 + 4 * N` | 4 | checksum | unsigned CRC-32/ISO-HDLC of every preceding byte from offset 0 through the final pixel byte |

Because `CanvasSize` permits at most 65,536 pixels, valid files range from 46 through 262,186 bytes.
No padding, payload-length field, duplicate pixel count, compression marker, or trailing data is
permitted. Width and height determine the pixel and total byte counts with checked `Long`
arithmetic.

## CRC-32 contract

The checksum is CRC-32/ISO-HDLC with width 32, polynomial `0x04C11DB7`, initial register
`0xFFFFFFFF`, input and output reflection enabled, and final XOR `0xFFFFFFFF`. A reflected
implementation uses polynomial `0xEDB88320`. The check value for ASCII `123456789` is
`0xCBF43926`. The stored checksum is the resulting unsigned value in big-endian byte order.

This is the CRC-32 algorithm demonstrated by [RFC 1952, section 8](https://www.rfc-editor.org/rfc/rfc1952.html#section-8).
It detects accidental corruption; it is not authentication, encryption, or protection against an
attacker who can replace and recompute the file.

## Deterministic encoding

Encoding accepts one validated immutable `DocumentState`. It writes exactly the table above and no
environment-derived value. Identical document values produce identical bytes on every supported
platform. The encoder must not read a clock, locale, timezone, density, process identity, storage
URI, or random source.

The encoder converts `DocumentId` pairs without numeric widening, preserves the stored
`Revision`, and emits the exact packed `RRGGBBAA` meaning already owned by `PixelSnapshot`. It does
not premultiply alpha, discard RGB under zero alpha, reorder channels, change revision, or invent
layer/frame containers.

## Bounded decoding and validation order

An input stream is read with a hard bound of 262,187 bytes: one byte beyond the maximum valid file
is enough to return the typed resource-limit result. A known larger length is rejected before a
read. A decoder must not use an unbounded read-all operation.

Before allocating the domain pixel snapshot, decoding validates in this order:

1. enough bytes for the fixed 38-byte header;
2. exact magic;
3. schema version;
4. width and height through the canonical domain factories, including the 65,536-pixel area limit;
5. document identity and revision through their canonical domain factories;
6. expected pixel and total lengths with checked `Long` arithmetic;
7. absence of truncation or trailing data; and
8. CRC-32 across the exact header and pixel payload.

Only then may the decoder construct exact `PixelColor` values and one `PixelSnapshot` through the
canonical domain boundary. Bad magic, unsupported version, invalid canvas, invalid revision,
resource excess, truncation, trailing data, checksum mismatch, and lower-level read failure remain
distinct typed outcomes. Exceptions and `null` do not encode these expected results.

Schema `0` and every value other than `1` are unsupported. There is no historical v0 format and no
synthetic v0 migration. When a later schema is accepted, a separate ADR must define one
deterministic read-old/write-current migration path. The v1 bytes and semantics remain fixed and
are protected by committed golden fixtures.

## Byte ownership

The project-format implementation may use bounded private mutable byte scratch while encoding or
decoding and may return a format value that defensively owns bytes which are immutable after
construction. The persistence adapter may use an equivalently bounded private transport buffer for
one project file or versioned recovery record. Constructors copy caller-owned arrays; no owned
array, mutable buffer, or mutable iterator escapes; bulk access returns a copy. This permission does
not include mutable pixel work surfaces, domain state, shared storage, or adapter-owned business
state. Untrusted input stays local to the codec/transport boundary until typed validation succeeds.

The application layer never depends on the project-format module. Application persistence ports
exchange immutable `DocumentState` captures and fully validated loaded candidates. The persistence
adapter depends on both application and project-format, performs the one mapping/codec call, and
normalizes storage failures. It never obtains a live runtime or mutates document state.

## Required P3-02 evidence

The codec implementation must add:

- a hand-checkable minimal golden file plus rectangular/transparent-hidden-RGB golden coverage;
- deterministic byte-for-byte encoding and encode/decode round trips;
- the `123456789` CRC conformance vector and independent stored-checksum byte-order assertions;
- exact `DocumentId` leading-zero, maximum revision, rectangular canvas, transparent hidden-RGB,
  and row/channel-order cases;
- one focused typed test for every rejection category and proof that domain pixel allocation occurs
  only after header, length, and checksum validation;
- truncation at every structural boundary, trailing-byte, corrupt-header, corrupt-pixel, invalid
  axis/area, future-version, and maximum-plus-one input cases; and
- a generated maximum-boundary fixture, structural proof of the maximum-plus-one bound and
  allocation-after-validation order, plus the smallest affected latency or retained-memory evidence
  needed to assess the chosen implementation; no device run unless later evidence demonstrates an
  Android-specific risk.

No P3-01 documentation change authorizes codec implementation, dependency addition, device
measurement, or a second serialization route.

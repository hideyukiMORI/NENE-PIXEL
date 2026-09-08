# ADR 0015: Project format v1 codec contract

- Status: accepted
- Date: 2026-09-09
- Issue: #85
- Affected rules: `ARC-001` through `ARC-005`, `ARC-007` through `ARC-010`, `ARC-012`,
  `KOT-001` through `KOT-008`, `KOT-013`, `KOT-020`, `QLT-006`, `QLT-009`, and
  `QLT-011` through `QLT-016`

## Context

ADR 0014 and `PROJECT_FORMAT_V1.md` fix the v1 bytes, module direction, and ownership boundary.
P3-02 must now expose the smallest codec API that P3-03 can consume without admitting unbounded
input, mutable storage, I/O, a second pixel representation, or failure categories that cannot occur.

The persistence transport must retain exactly one maximum-plus-one probe of 262,187 bytes so the
codec can return a typed resource-limit result. A carrier limited to the 262,186-byte valid-file
maximum would make that required codec outcome unreachable. Conversely, copying an arbitrarily large
caller array before rejection would violate the bounded ownership contract.

Every validated `DocumentState` is encodable. Sixteen document-identity bytes map totally to 32
lowercase hexadecimal characters, so v1 decoding has no invalid-identity outcome. Pixel corruption
is detected by the checksum and needs no second corruption category.

## Decision

### One bounded byte carrier

`ProjectFormatBytes` is the only public byte carrier. Its private constructor defensively owns one
`ByteArray`; `copyBytes()` is its only bulk access and returns a copy. Content equality and hashing
compare bytes. No owned array or mutable iterator escapes.

`ProjectFormatBytes.create(ByteArray)` returns `ProjectFormatResult<ProjectFormatBytes>`. It rejects
an input longer than the 262,187-byte probe maximum before copying and reports that actual factory
boundary. Inputs through the probe maximum are copied, including the 262,187-byte value needed to
exercise codec resource rejection. The carrier publishes the valid-file and probe maxima needed by
the P3-03 bounded transport. There is no trusted/adopt/no-copy constructor path.

### One closed result vocabulary

`ProjectFormatResult<T>` has only `Accepted<T>` and `Rejected(ProjectFormatRejection)` outcomes.
The byte factory and decoder use the same result and rejection vocabulary. Expected invalid input is
never represented by `null` or an exception.

`ProjectFormatRejection` contains only the reachable v1 categories:

- `ResourceLimitExceeded`, with diagnostic actual and effective maximum byte counts;
- `Truncated`, with diagnostic actual and required byte counts;
- `InvalidMagic`;
- `UnsupportedVersion`, carrying a `ProjectFormatVersion` value;
- `InvalidCanvas`;
- `InvalidRevision`;
- `TrailingData`, with diagnostic actual and expected byte counts; and
- `ChecksumMismatch`, with diagnostic computed and stored unsigned bit patterns.

`ProjectFormatVersion` is the meaning-bearing value for the complete unsigned 16-bit wire range. Its
constructor and total wire factory are module-internal; callers receive it only as typed diagnostic
data. Raw canvas dimensions do not cross the public rejection boundary.

### One v1 codec and mapper path

`ProjectFormatV1Codec.encode(DocumentState)` is a total function returning `ProjectFormatBytes`.
It uses the same defensive carrier factory as external input. It reads the domain-provided
`copyPackedRgba8888()` projection without modifying it and writes the fixed v1 bytes with portable
Kotlin standard-library operations.

`ProjectFormatV1Codec.decode(ProjectFormatBytes)` returns
`ProjectFormatResult<DocumentState>`. It enforces the 262,186-byte valid-file maximum, then follows
the validation order in `PROJECT_FORMAT_V1.md`. Only after exact length and CRC validation succeeds
does one module-internal mapper construct an immutable `List<PixelColor>`, call the existing
`PixelSnapshot.create` factory, and create the `DocumentState`. The list is a one-use immutable
projection into the domain factory, not a mutable pixel surface. The codec does not build a mutable
pixel `IntArray`.

The decoder's mapper injection is internal and exists only so contract tests can prove that invalid
input never reaches pixel allocation. Production has one fixed mapper and no strategy selection.
CRC-32 and big-endian integer operations use local Kotlin arithmetic; production code imports no
file, stream, provider, Android, JDK codec, buffer, or checksum API.

### Module graph

P3-02 creates `:core:project-format` with one production dependency on `:core:domain`. It adds no
runtime library or Gradle plugin. The root check includes the module, and the architecture-validator
contract fixture includes the already-approved project-format-to-domain edge. P3-03 is the first
production consumer; no current application or presentation module depends on this API.

## Rejected alternatives

### Carrier capped at the valid-file maximum

Rejected because a maximum-plus-one input could not reach the decoder's typed resource-limit path.

### Separate trusted and untrusted byte constructors

Rejected because an adopt/no-copy route would weaken one ownership rule for an unmeasured
optimization and create two ways to construct the same value.

### Public DTO or pluggable mapper

Rejected because v1 has one domain mapping and no second consumer need. Public strategies or DTO
construction would expose representation choices and allow competing paths.

### Mutable packed-pixel decode buffer

Rejected because ARC-005 permits bounded byte scratch here, not another mutable pixel work surface.
The existing immutable color-list domain factory is sufficient and its bounded cost is assessed by
the P3-02 host evidence.

## Consequences

### Benefits

- maximum-plus-one rejection is reachable without admitting unbounded owned bytes;
- every expected invalid input has one closed, reachable result;
- encoded and caller-supplied arrays have the same defensive-copy rule;
- domain pixel allocation is structurally delayed until validation succeeds; and
- P3-03 receives the exact byte limits and copy boundary it needs without I/O entering core.

### Costs and risks

- defensive construction copies encoded bytes once more before returning them;
- maximum decode temporarily creates immutable `PixelColor` objects before the domain snapshot; and
- the host latency observation is descriptive and does not establish Android storage or UI latency.

## Enforcement impact

- exact golden, corruption, boundary, ownership, allocation-order, and CRC tests enforce the API;
- the Gradle graph and architecture-validator fixture enforce the one domain dependency;
- normal module compile, detekt, ktlint, and test tasks reject API or implementation drift; and
- the separate bounded host observation records the cost without joining routine `check`.

## Migration and rollback

No codec or project files exist before P3-02, so there is no data or API migration. Rollback before
P3-03 removes this module, its graph entries, and ADR 0015 together. After a consumer lands, rollback
must keep the v1 reader and requires the compatibility decision mandated by ADR 0014.

## Related

- Issue: #85
- PR: pending
- Byte authority: [Project Format Version 1](../PROJECT_FORMAT_V1.md)
- Builds on: ADR 0005 and ADR 0014
- Supersedes: none
- Superseded by: none

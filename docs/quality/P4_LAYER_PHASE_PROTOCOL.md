# P4 Layer Phase Protocol

Status: prospective preparation for Issue #145. The decisions below govern preparation; the
complete protocol, executable agreement, artifacts, profile and finite collection schedule are
not yet admitted. No sample collection is authorized by this document's current state.

Rules: ADR 0030/0031/0035; QLT-011 through QLT-019. Active waivers: none.

## Scope and historical boundary

The phase owns the registered single-layer, fully covered 16-layer and alpha-128 underlay workloads,
retained heap/PSS, maximum autosave/user-save time and logical remap history bytes. It uses the
existing P4 collectors and analyzers. The v7 indexed protocol and all earlier FAIL/invalid evidence
remain historical; a new phase identity and executable agreement are required before collection.

The accepted frame comparators remain those in
[the prospective comparison decision](P4_INDEXED_CUTOVER_PROTOCOL.md#prospective-layer-phase-comparison-decisions-issue-145).
The fixed M5 gates remain UP-to-committed p95 at most 16.67 ms, all-frame overrun p95 within
baseline +1.0 ms and p99 within +2.0 ms, zero fatal/ANR/process-death and no defined gross regression.

## One maximum project fixture

One synthetic project asset is kept in `docs/quality/fixtures/p4-layer-phase/`. It is measurement
test data, not a user asset or generated build output. A single Kotlin definition in the persistence
host-test source constructs it through canonical domain factories and `ProjectFormatCodec.encode`.
The checked asset is compared byte-for-byte with that definition in a focused host contract test.
The exporter creates a new explicitly named file; it never overwrites an earlier artifact.

The document has these exact facts:

- ID `14500000000000000000000000000000`, revision 0, size 256 by 256, default palette index 0.
- 256 palette entries: slot 0 is opaque black; slots 1 through 255 are grayscale `(i,i,i,128)`.
- 16 visible layers, ordered IDs 1 through 16, all pixels covered, each layer filled with its own
  palette index equal to its ID. Every name is 32 copies of U+1F600, exactly 128 UTF-8 bytes.
- Project v3 length is exactly 1,182,862 bytes; its Candidate recovery envelope is 1,182,885 bytes.
  The checked asset's SHA-256 is
  `165f62d180533849ce1a4ef1625cd3971e445f2dca60ef7b9b46fedaafa0b3ec`.
  The exporter and both merged AndroidTest asset entries match that hash and length. This is not
  yet an APK-entry or successful device-load proof; those remain artifact admission checks.
- Canonical load selects top layer 16. Drawing with palette index 0 changes every one of the
  diagonal's 256 pixels; a single-cell DOWN changes one pixel. Revision 0 admits these commands.

This is a drawing-capable maximum fixture. The pre-existing format-boundary fixture uses
`Long.MAX_VALUE` revision and varying diagonal indices, so it is not substituted for this workload.
The partial-alpha layers exercise actual compositing despite every coverage bit being set.

One measurement init script borrows the existing persistence unit-test runtime classpath for the
host exporter and maps this same checked asset directory into the two existing `androidTest` APKs.
It is applied explicitly to the measurement builds. It adds no plugin, module, dependency, public
API or production asset, and does not edit historical roles' production build files. No Kotlin test
package is imported across module boundaries. Each APK's fixture entry must match the one pinned
asset hash before use. Normal host verification reads the checked asset directly.

Frame setup stages those exact bytes in the test DocumentsProvider's own fresh storage, verifies
their hash, and loads through the app's actual SAF picker. Baseline 169b592 has no layer panel;
do not add production UI to that comparator or claim that its nonexistent layer rows were observed.
Its fixture proof combines pinned bytes, production v3 decoding contracts, the canonical successful
load, canvas size, clean state and top-layer selection policy. Memory and publication lanes consume
the same decoded asset outside their measured intervals, rather than constructing another fixture.

## First-preview assessment decision

The layered group has two workloads on the same pinned maximum document: `canvas256_layers16_tap`
and `canvas256_layers16_repeated_diagonal`. The tap sends DOWN, waits the existing 100 ms preview
dwell, captures the preview interval, then sends UP and captures the committed interval. There is
no MOVE in its preview interval. The repeated diagonal retains the existing DOWN plus 16 alternating
MOVE events, 20 ms MOVE dwell and 100 ms post-MOVE dwell; its long preview is not split by an extra
host capture. Both roles use the identical event and capture sequence, outside all fixture setup.

The tap isolates the first preview's work on every fresh gesture. It uses warm rendering resources;
it is not process startup or a cold allocation metric. Preparation must prove the correct visible
preview on the pinned document before timing. Timed samples retain all valid app frames and refuse
empty, flagged, unassociated or non-monotonic intervals under the existing frame validity contract.
The earliest valid DOWN-only app frame is identified by its own input-start/completion timestamps
and frame identity. Report its service-to-completion latency and overrun distribution, with the
explicit limit that `gfxinfo` does not prove physical pixel presentation or injection-to-display time.
Do not pair independent timestamp minima from pooled long-stroke frames and call that first preview.

The tap and repeated diagonal each keep M5 UP-to-committed and all-frame relative gates. The first
DOWN-only frame is included in the tap's all-frame population and is additionally reported on its
own; no new numeric physical-presentation gate is invented. The existing single-layer families and
underlay diagonal remain registered. This decision fixes assessment semantics, but complete slot
populations, budgets, schema and executable association checks still precede phase admission.

## Pending admission decisions

The fixed frame-family order/counts and executable first-preview association, underlay image/placement,
memory checkpoints and ownership, SAF provider/timing boundary, versioned schemas/verdicts,
artifact/profile bindings, finite stop/retry budget and preservation-v2 integration must be
completed before collection. A verified read-only snapshot is not an isolated measurement session.

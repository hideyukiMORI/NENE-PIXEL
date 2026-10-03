# ADR 0035: Preserve remembered underlays across a phase measurement

- Status: accepted
- Date: 2026-10-03
- Issue: #145
- Affected rules: `QLT-011`, `QLT-012`, `QLT-015`, `QLT-016`, `QLT-019`

## Context

ADR 0034 added `no_backup/reference-underlays`: recalling a work updates its state-file mtime,
and remembering a work can evict older pairs. The previous measurement procedure backed up durable
directories but isolated only the three recovery filenames. Leaving remembered underlays live lets
measurement fixtures alter or evict the owner's records. Restoring bytes alone loses LRU order.

`tools/prepare-device-checkout.ps1` prepares a manual functional checkout. Hiding remembered
underlays by default there would prevent the owner from checking the feature itself. The old
Issue-local preservation executable is historical evidence, not a maintained measurement path.

## Decision

The phase measurement has one private-data preservation contract, specified in
[Device Private Preservation](../quality/DEVICE_PRIVATE_PRESERVATION.md), and one policy implementation
in `docs/quality/measurements/p4-device-private-preservation.ps1`. Its first slice is a device-free
inventory, isolation and restoration planner. Native execution and phase-preflight integration must
consume that policy; a passing planner test alone does not authorize a device session.

Before measurement installation or launch, archive all durable app data and pin an exact inventory.
With the app stopped, isolate the whole underlay directory and each live recovery file in a fresh
guard. Guarded originals retain their names, bytes and file modification times. Other original
durable data remains protected. Restoration refuses missing or changed originals, occupied
destinations, ambiguous paths and an interrupted operation. It first retains measurement-created
data separately and then returns originals only to vacant paths. No recovery step deletes evidence.

The same guard also isolates the two ProfileInstaller 1.4.0 regular files named in the contract:
`files/profileinstaller_profileWrittenFor_lastUpdateTime.dat` and `files/profileInstalled`.
The existing frame collector installs the packaged profile, and that library writes these files
using package update/profile state. Exact original preservation therefore requires moving them
before measurement. Their measurement replacements are archived before their originals return.
This follows the library's [installer source](https://raw.githubusercontent.com/androidx/androidx/androidx-main/profileinstaller/profileinstaller/src/main/java/androidx/profileinstaller/ProfileInstaller.java)
and [verification source](https://raw.githubusercontent.com/androidx/androidx/androidx-main/profileinstaller/profileinstaller/src/main/java/androidx/profileinstaller/ProfileVerifier.java),
and was checked against the repository-resolved 1.4.0 AAR; no dependency is changed.

The original snapshot and preservation record are immutable; verification and restoration produce
new records. The device, package, experiment and archive hashes bind them to one session. A native
executor must verify the actual device's mtime precision before isolation, and verify the same file
identity after each move. A seconds-only timestamp does not satisfy ADR 0034's millisecond ordering.

The manual checkout tool keeps its present behavior. The phase-level original guard and the
existing per-slot quarantine of measurement outputs have different lifetimes; slot cleanup never
owns or consumes the original guard. A new phase protocol must explicitly bind preservation before
it admits samples. The current v1 preservation validator and historical verdicts are not relabelled.

## Rejected alternatives

### Archive bytes but leave underlays live

Rejected: recall changes mtime and the bounded store may evict the original pair during a run.

### Hide underlays in every manual checkout

Rejected: that changes the manual feature being inspected and prevents persistence checks.

### Copy an Issue-local script for each experiment

Rejected: fixes and restore rules diverge. One repository-owned policy supplies the phase executor.

## Consequences

Original user assets are outside measurement eviction and their LRU timestamps can be checked.
Interrupted or conflicting restoration requires inspection; the tool never guesses which copy wins.
Inventories and archives contain private assets and stay in the development lab, never in Git.
Snapshots cost host space and device I/O before and after collection, outside timed populations.

## Enforcement impact

A narrow no-device validator exercises the inventory and move plans, including negative cases.
Native execution, evidence hashing and prospective preflight admission remain required before use.
No application, project schema, dependency, public API or performance threshold changes here.

## Migration and rollback

Issue #145 is the first consumer. Historical v1 records and helpers remain evidence only; they do
not admit a new phase session with underlay memory. Incomplete integration blocks new collection.
Rollback stops new collection and leaves every original guard, archive and failed record intact;
it never falls back to the historical executable or changes manual checkout defaults.

## Related

- Issue: [#145](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145)
- [ADR 0031](0031-phase-gate-device-performance.md), [ADR 0034](0034-underlay-memory.md)
- Supersedes: none
- Superseded by: none

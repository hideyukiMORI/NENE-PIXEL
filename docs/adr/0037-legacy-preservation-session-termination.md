# ADR 0037: Terminate the one legacy v1 preservation session by guarded moves

- Status: accepted
- Date: 2026-10-12
- Issue: #190
- Affected rules: `QLT-011`, `QLT-012`, `QLT-015`, `QLT-017`

## Context

Before ADR 0035, the Issue-local v1 helper preserved the owner's app data on the test device for
the #190 Baseline Profile generation (guard `no_backup/issue-190-user-recovery-20261008-2241`).
It archived two roots, recorded size and hash only, and moved exactly one file into its guard: the
live recovery file. The underlay records and the two ProfileInstaller marker files stayed live.
Its restoration stopped at admission on 2026-10-09 because both markers had changed.

The canonical read-only observation of 2026-10-12 established the current state: all 90 original
paths exist, 87 keep their bytes and modification seconds, the guarded original recovery is intact,
a measurement-created recovery occupies its former live path, and only the two markers differ. The
markers are library bookkeeping that ProfileInstaller rewrites on the next launch after an install
change; their original bytes exist only in the host archive. The v1 record never stored fractional
modification times, so exact original nanoseconds cannot be proven from any retained artifact.

ADR 0035 forbids falling back to the historical helper, forbids relabelling a v1 record as v2, and
restores only by non-overwriting moves on the same filesystem. Its restoration executor requires a
verified v2 preservation record that this session does not have. Leaving the session open would
tangle the next v2 snapshot with a historical guard.

## Decision

One repository-owned legacy termination executor,
`docs/quality/measurements/p4-device-private-legacy-termination.ps1`, ends this single session.
It is bound to fixed constants (session, guard path, recovery path, archive path, marker paths)
and never accepts a caller-provided move list. Its inputs are the pinned read-only observation
record, the pinned v1 manifest and the expected installed APK hash; every input is verified by file
hash and schema before any device command.

Host admission proves, from those records, that the guarded recovery equals the v1 original, that
the live recovery differs from it, that every original path exists and exactly 87 original files
are unchanged, that both markers are present, and that the archive destination is vacant. Device
admission requires the stopped application, the expected installed APK and a fresh inventory equal
to the pinned observation; any drift is a refusal before the first move.

The plan is fixed: create the missing ancestors of
`no_backup/p4-user-preservation/legacy-190-20261008-2241/measurement/no_backup`, move the
measurement recovery there, then move the guarded original recovery back to its live path. Each
step uses the existing isolation step with its stopped-process guard, vacant destination, same
filesystem, `mv -nT`, before/after inventory equality and write-once intent/result records. The
empty legacy guard directory remains; nothing is deleted, overwritten or installed.

The termination record `nene-pixel-legacy-termination-v1` states two explicit exceptions decided by
the owner: both ProfileInstaller markers keep their current bytes (original and current hashes are
recorded side by side), and exact original fractional modification times are not claimed. The record
asserts seconds equality and the preserved relative underlay order instead. The result is a closed
legacy session, not a v2 preservation record and not an admission for any measurement.

## Rejected alternatives

### Widen the historical helper's allowlist

Rejected: it restores only one marker and ADR 0035 forbids the fallback.

### Write the original marker bytes back from the host archive

Rejected: it needs a new host-to-device write primitive used once, contradicts the move-only
principle, and the library overwrites the bytes on the next launch.

### Take a v2 snapshot of the current state and protect the legacy guard as original data

Rejected: the historical guard would become part of every later v2 session.

## Consequences

The owner's recovery returns to its original bytes and modification time by rename. The measurement
recovery and every original archive remain retained. Future preservation starts from a clean v2
snapshot. The executor is single-purpose and is not reused for other sessions.

## Enforcement impact

`validate-p4-legacy-termination.ps1` exercises the host and device refusals and the successful
fixed plan with mocked command boundaries. No application, project schema, dependency or public
API changes.

## Migration and rollback

The executor runs once on the named device after review. A refusal or failed step leaves the
retained prefix, journals and all archives in place; the session then needs inspection, never an
automatic retry. Rollback is to leave the legacy guard as it is.

## Related

- Issue: [#190](https://github.com/hideyukiMORI/NENE-PIXEL/issues/190)
- [ADR 0034](0034-underlay-memory.md), [ADR 0035](0035-measurement-private-data-preservation.md)
- Supersedes: none
- Superseded by: none

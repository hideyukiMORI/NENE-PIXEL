# Device Private Preservation

Status: accepted prospective preservation contract for #145, under
[ADR 0035](../adr/0035-measurement-private-data-preservation.md).
Rules: QLT-011/012/015/016/019. Waivers: none.

This is a prerequisite for a future phase session, not a collection protocol or an instruction to
run the device. The first implementation is a device-free planner; the native executor, verified
archive, mtime probe, experiment binding and final restore integration must exist before use.

## One original inventory

The protected roots are exactly `files`, `no_backup`, `shared_prefs`, and `databases`. An inventory
contains each present root and every descendant directory or regular file. Root absence is derived
from the absence of its entry. Directory times are not identity: moving children changes them.
The planner's file entry fields are `Path`, `Type` (`file`), `Size` (non-negative integer bytes),
`Hash` (lowercase SHA-256), `MtimeNs` (decimal string of nanoseconds since the Unix epoch), and
`LinkCount` (integer 1, attested by the observer). Native observation must preserve at least
millisecond precision; no rounded tar-header timestamp may supply this field. A directory has fields
`Path` and `Type` (`directory`). Symbolic links, hard links, other entry types, duplicate paths,
missing parent entries, absolute or non-canonical paths and paths outside the four roots are refused.

Paths use `/` and nonempty ASCII components from `[A-Za-z0-9._-]`; `.` and `..` components are
forbidden. Refusing an unusual name is safe; rewriting or skipping it is not. Inventories compare
paths case-sensitively. Empty directories and originally absent roots are meaningful.

The executor must stop before mutation if the app is live, the inventory is incomplete or the
snapshot differs from it. Lifecycle flushing happens before the stopped snapshot; a fixed sleep
alone is not proof that an in-flight save completed. No measurement APK is installed first.

The initial native entry point accepts an already-stopped application only. It never initiates a
lifecycle transition, launches instrumentation, or force-stops a live application to obtain access.
Its preservation claim starts at the observed durable inventory; PID absence does not retrospectively
prove that a previous application session saved its in-memory state. A live-session handoff remains
inadmissible until a separate observable flush-completion contract exists.

## Isolation plan

The planner takes the original inventory and a unique session id containing only lowercase letters,
digits and hyphens, beginning with a letter or digit. The original guard is
`no_backup/p4-user-preservation/<session>/original`. The whole session subtree must be absent.
No existing guard, including an older session, is reused or changed.

Move each present `no_backup/nene-pixel-recovery-v1`, its `.new` and `.bak` files, and the entire
present `no_backup/reference-underlays` directory into the original guard under its basename.
Also move the present regular files `files/profileinstaller_profileWrittenFor_lastUpdateTime.dat`
and `files/profileInstalled` under their basenames. ProfileInstaller 1.4.0 writes these when the
measurement APK/profile is installed or verified; leaving them live would alter protected originals.
These six fixed basenames are distinct. Other original entries remain protected in place.
Recovery/profile entries must be files and the underlay entry must be a directory. An absent original stays
absent; an empty original directory is preserved. No pair is opened by the application during this
step. The plan fixes these moves and the expected original inventory; callers cannot substitute an
arbitrary move list. The phase session root contains only `original` before restoration.

Isolation verification compares the full observed inventory with the planned transformation:
all original files keep size, hash and mtime, all original directories retain their paths except
the deliberately moved subtree, no live isolated path exists, and only the necessary new guard
ancestors are allowed. `no_backup` may be created if originally absent; its prior absence is kept.

## Restoration plan and verification

The planner receives the original inventory, the session id and the current full inventory. It
derives the isolation plan again. It first proves exact guarded-original identity and unchanged
original files/directories outside that guard. A missing or changed original, extra guarded entry,
unexpected session entry, prior restoration archive, or partially restored original is a refusal.
An identical original file present both in its guard and at its former live path is ambiguous and
also refused; the planner does not guess whether it is a measurement file or a partial copy-back.

All new live files and directories are measurement data. Plan moves of their highest new ancestors
to `no_backup/p4-user-preservation/<session>/measurement`, preserving their original relative paths
and exact inventory. New descendants of an existing original directory are retained individually
or under their highest entirely new ancestor; the original directory is never moved. Live recovery
and underlay entries are measurement data even when they occupy an original's eventual destination.
The archive is new and must not already exist. Every measurement move precedes original restoration.
Then return every isolated original file and the whole original underlay directory to its now-vacant
live destination. These are non-overwriting moves on the same filesystem, not lossy reconstruction.

The verifier proves the final original inventory at its live paths, exact bytes and file mtimes,
the measurement archive's full identity, and empty original guard. It permits only the planned guard
scaffolding in addition to those inventories. If `no_backup` was absent originally, the sole retained
exception is the new guard/measurement archive scaffolding; no live original is invented. Report that
exception explicitly. Any interruption remains a failed, retained operation; do not automatically
repeat a partly applied plan or label a plan as a successful restoration.

## Native execution and phase admission requirements

The future native executor is the sole consumer of these plans. It uses bounded, logged commands,
new lab evidence paths, a byte-exact host archive and pinned hash/inventory records, and write-once
per-move intent/result records. It verifies stopped-process, source and vacant destination immediately
before each move and reads the result back. Failure leaves all data and logs in place. No `rm`, clear,
uninstall, overwrite or automatic retry is part of preservation. A measurement abort still routes to
this guarded restoration flow; it does not rewrite sample evidence.

The session also pins and retains the originally installed application APK and its hash before the
first measurement install. Restore that verified application version while it remains stopped and
before returning user records to their live paths; a historical baseline must never be launched
against the returned current records. Final native verification includes the installed APK identity.
APK restoration and lifecycle-flush proof are executor obligations, not claims of the pure planner.

Binary archive/APK transfer reuses `Invoke-P4RawAdbCapture` in
`measurements/p4-indexed-device-lanes.ps1`. Its file destination mode must create a new file, stream
bytes without text redirection, stop at an explicit byte limit, and share one wall-clock deadline
for stdout, stderr and process completion. A Windows Job bounds the same process tree used by the
existing in-memory caller. The call writes new command/result records, including timeout/failure
and the retained partial file; it never deletes or overwrites either. The existing in-memory caller
keeps its nonempty-byte result contract. This transport does not prove archive contents, remote
command status, lifecycle completion or preservation admission; those checks remain with the executor.

### Native observation and archive validation

The observer discovers every path using a binary NUL-delimited list before constructing any command
from a path. Strict UTF-8 decoding, the canonical path policy and exact case-sensitive coverage apply;
an invalid name is a refusal, never a skipped file. Native metadata records contain path, hexadecimal
mode, link count, byte count, epoch seconds and the timezone-bearing fractional modification time.
Only directory and single-link regular-file modes are admitted. The seconds and calendar portions
must agree, and all nine fractional digits are retained as `MtimeNs`; floating-point conversion is
forbidden. A separate per-file SHA-256 list must cover exactly the discovered regular files.

The host archive validator reads the tar stream without extracting it, using the platform
`System.Formats.Tar` reader. It compares every regular file's path, size and streamed SHA-256 with
the native inventory and every directory's presence. It refuses links, unsupported entries,
duplicates, missing/extra entries, invalid headers, truncation and nonzero content after the tar end
marker. Tar modification times are not the precision authority: the native inventory retains those.
A stopped before/after native inventory comparison must still prove that capture saw one stable
source. Passing synthetic metadata/archive tests is not a real-device capability or snapshot proof.

The read-only native observer uses that same binary capture path for every probe. Before private
enumeration it verifies `shell_v2` support and a deliberate nonzero remote exit with separate stdout
and stderr, then verifies exact PID absence. It uses `adb shell -T -n` without `-x`, a quoted
`run-as` shell, NUL enumeration of the four roots, and bounded batches of metadata and digest calls.
Every discovered path must appear in the metadata, every regular file in the digest list, and a
second NUL enumeration must match the first. The process must remain stopped at the end. These
checks produce an observation, not a stable snapshot or preservation admission: the snapshot
executor still needs matching inventories on both sides of the verified archive capture.

Preservation evidence uses `nene-pixel-device-preservation-v2` with device/package/session identity,
creation time, original archive and inventory hashes, plan identity and verified `preserved` state.
Verification/restoration records reference its hash. The prospective phase preflight must check all
these bindings and the verified isolation, not merely filename, serial and freshness. No existing
v1 record is upgraded in place. This schema describes measurement evidence only, not project files.

## Narrow verification

`validate-p4-device-private-preservation.ps1` uses synthetic inventories without adb, Gradle or
private assets. It covers present/absent/empty underlays, exact image/state mtimes, complete recovery
sets, protected drift, timestamp-only drift, unexpected guard data, occupied destinations/archives,
partial moves, non-canonical or ambiguous paths, preservation of measurement-created files, and
successful final restoration. Manual checkout source remains unchanged. Documentation validation
checks this contract and ADR links. Planner success is not a device result or an archive-integrity
proof; those remain native-executor acceptance requirements.

The profile-cache extension is checked with present/absent originals, wrong entry types, exact
guard destinations, measurement replacement archival, timestamp drift and final restoration.
The archive validator includes Ustar, GNU long-path and PAX fixtures because the native tar producer
may emit extension headers. Only the directly changed validator is rerun for these additions.

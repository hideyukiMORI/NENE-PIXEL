# ADR 0034: The device remembers the reference underlay of each work

- Status: accepted
- Date: 2026-10-03
- Issue: #172
- Affected rules: `ARC-001`, `ARC-003`, `ARC-004`, `ARC-005`, `ARC-008`, `ARC-010`, `CMD-002`,
  `KOT-007`, `KOT-012`, `KOT-014`, `QLT-011`, `QLT-019`; amends `ARC-004`, `ARC-005`, ADR 0021 and
  ADR 0032

## Context

[ADR 0032](0032-reference-underlay.md) records the owner's decision of 2026-09-30: "The app
remembers the underlay of a work and restores it when the work is opened again. It is not stored in
the project file. The session-only form is built first; remembering follows." The session-only form
is complete (P4-06a, P4-06b). Today the underlay is lost when the process dies or another document
is installed.

The probe of 2026-10-03 found:

- `ARC-004` knows four state categories. `AppPreferences` is "platform-wide settings independent of
  the open document" ([ADR 0021](0021-app-language-resources.md)); a per-work fact does not fit it.
- A `DocumentId` is created for a blank start, a new document, a legacy conversion and a PNG opened
  as a new work. A project load, a second load of the same file, Save As and an accepted recovery
  keep the id that the bytes carry. A Save As copy therefore has the id of its original.
- The only app-private record is the recovery record: one fixed file, `AtomicFile`, a process-wide
  mutex. An unreadable recovery record blocks destructive switches by design. No production code
  lists or deletes app-private files.
- Installing a runtime replaces the workspace inside the runtime lock, where no I/O may run. Nothing
  reads a store after an installation, and nothing publishes workspace changes to an observer
  except the autosave projection, which follows committed commands.
- A `ReferenceImage` holds at most 1024 x 1024 straight RGBA8888 pixels, 4,194,304 bytes. The
  adapter keeps no URI permission (ADR 0032), so only a copy of the pixels can be remembered.

The owner delegated the remaining choices to the design seat on 2026-10-03.

## Decision

Add **underlay memory**: for each work, the device keeps one bounded app-private record of the
reference underlay and restores it when that work is installed. The document, its file, its export,
its history and its recovery record are unchanged and never read the memory.

### State category

- `ARC-004` gains a fifth category, `WorkMemory`: what this device remembers about a work between
  sessions, outside the document. Its owner is an app-private memory boundary behind an
  application port; its mutation route is a typed remember or forget request derived from
  `WorkspaceState`. This decision introduces only the reference underlay in it.
- `ARC-004` keeps its sentence "The same fact MUST NOT be independently stored in more than one
  category." The memory is not an independent store: `WorkspaceState` stays the single live owner
  of the underlay, the memory is restored into the workspace at most once per installation, and it
  is written only from the workspace value. While a work is installed nothing else reads the
  memory. `ARC-004` gains this paragraph: "ADR 0034 introduces only the reference underlay in
  `WorkMemory`. `WorkspaceState` stays the single live owner of the underlay; the memory is
  restored into it at most once per installation and is written only from it, so it is the resting
  form of the same fact and not a second owner. No document, command, history, export or recovery
  record reads it. This category does not authorize remembering other workspace facts."
- `AppPreferences` keeps its meaning. It is not widened to per-work facts.

### What is remembered, and for which work

- The key is the `DocumentId`. A Save As copy shares the id of its original and therefore its
  remembered underlay: the later writer wins, and Remove in either forgets it for both. A work
  that was never saved has a record that no file leads back to; it leaves through the eviction
  below. The blank document of a fresh process has a new id, so it has nothing to recall.
- A record holds the exact image, the placement (left, top, scale), the opacity and the
  visibility. The interaction is not remembered: a restored underlay is resting. The canvas size is
  not remembered: the placement is clamped against the installed document by the existing
  derivations.
- `RememberedUnderlay` in `core/application/workspace/underlay` is the value that crosses the
  port. Its one factory is the only validator of the numbers: left and top are finite, scale is
  finite and above zero, and it returns a typed result (KOT-007). It is built from a
  `ReferenceUnderlay` and turned back into one against a canvas through the existing derivations:
  `placed`, `withPlacement`, `withOpacity`, and `toggledVisibility` for a hidden one. No second
  constructor of `ReferenceUnderlay` is added.

### Port and storage

- `UnderlayMemoryPort` in `core/application/persistence` has three suspend functions: `recall` of a
  `DocumentId`, returning `Remembered` with a `RememberedUnderlay` or `Absent`; `remember` of a
  `DocumentId` and a `RememberedUnderlay`; and `forget` of a `DocumentId`. `remember` and `forget`
  return a closed `Stored` / `Failed` outcome that is never projected to the user. `recall` has no
  failure outcome: the adapter answers every I/O failure, every unreadable record and a failed
  allocation as `Absent`.
  No function of the port throws for an expected failure. `PersistencePorts` gains the port.
- `:adapters:persistence` implements it in the directory `reference-underlays` under the
  application's no-backup files directory, the location of the recovery record: it is not included
  in backups and the OS does not evict it. The adapter is created like the other adapters, from
  the no-backup directory and an injected dispatcher; the adapter alone names the subdirectory. One work has two records, each written through
  `AtomicFile`, named by the 32 lower-case hexadecimal characters of the id:
  - `<id>.image`: the magic `NPUI` (4 bytes), a version (unsigned 16-bit, value 1), the width and
    the height (unsigned 16-bit each, 1 to 1024), the pixels in row-major order with four bytes
    per pixel in the order red, green, blue, alpha (straight alpha, the byte order of the packed
    RGBA8888 value from its highest byte), and the CRC-32 of all preceding bytes. Its length is
    `14 + 4 * width * height`, at most 4,194,318 bytes.
  - `<id>.state`: the magic `NPUS` (4 bytes), a version (unsigned 16-bit, value 1), the CRC-32
    field of the image record it belongs to (4 bytes), left, top and scale as IEEE 754 binary64 (8
    bytes each), the opacity (one byte, 26 to 255), the visibility (one byte, 0 hidden or 1
    shown), and the CRC-32 of all preceding bytes. Exactly 40 bytes.
  All integers are big-endian. CRC-32 is the IEEE 802.3 checksum of `java.util.zip.CRC32`, stored
  as an unsigned 32-bit value. Reads are bounded by the maximum length plus one byte.
- `remember` writes the image record first, and only when the stored image record is absent or
  has another CRC-32; then it writes the state record. Moving, scaling, fading or hiding the
  underlay therefore rewrites 40 bytes, not the image. The adapter keeps, for the image instance
  it last wrote or recalled for a work, its CRC-32, so an unchanged image is neither copied nor
  hashed again.
- `recall` returns `Absent` for a missing pair. A pair is unreadable when either record is
  missing, truncated or over-long, fails its CRC-32, has an unknown magic or version, when the
  state names another image, or when `RememberedUnderlay` or `ReferenceImage` rejects its values.
  An unreadable pair is deleted and answered as `Absent`. Nothing is shown to the user: the memory
  is disposable. A successful `recall` marks the pair as used now.
- Limits: at most 16 works, and at most 33,554,544 bytes of image records (eight records of the
  largest size). After each `remember` the adapter first deletes every file of the directory that
  does not belong to a complete pair, then deletes whole pairs, least recently used first, until
  both limits hold; the pair just written is never deleted. "Used" is the modification time of the
  state record, which the adapter sets on `remember` and on a successful `recall`; equal times are
  ordered by id. A failure to set the time is ignored. Only the adapter reads the clock, through
  the file system; core reads no time. A clock that moves backwards only changes which pair leaves
  first.
- `forget` deletes the pair. One adapter-owned mutex serializes `recall`, `remember`, `forget` and
  the sweep. These bounded byte buffers are added to the adapter permissions of `ARC-005`.

### Coordination

- The application keeps `UnderlayMemoryTracking` in `EditorRuntime`, under the runtime lock and
  beside the persistence coordination, not inside it: a persistence transition replaces the
  coordination, and the memory must not be part of a persistence operation. It is bookkeeping,
  not a state category. It holds what the store is known to hold for the installed work
  (unknown, nothing, or a value), whether an underlay action was reduced while that was unknown,
  and at most one departing capture. `EditorRuntime` exposes the read-only `underlayMemory`
  projection: `Settled`, `RecallPending` or `PublishPending`, each pending form with an opaque
  equality token.
- The blank document of a fresh process starts with the store known to hold nothing. Every later
  installation (new, load, recovery adoption, legacy conversion, PNG as a new work) starts with
  the store unknown, which is `RecallPending`. Nothing is published for a work while its store is
  unknown, so the empty workspace of a fresh installation never deletes a record.
- A recall completion for the current installation makes the store known. When no underlay
  action was reduced since the installation, a recalled value is also reduced into the workspace
  as one `SetReferenceUnderlay` inside a runtime transaction, and the workspace then equals the
  known value. When an underlay action was reduced meanwhile, the workspace is left alone and the
  known value is compared with it like any later change: an image chosen meanwhile is published,
  and a Remove publishes a forget of the old record. A completion for another installation is
  dropped. A completion that arrives while a switch is installing another work is dropped whole:
  the store stays unknown, so the empty workspace is never mistaken for a Remove, and that
  installation is not recalled again, also when the switch then fails and the work stays
  installed: its underlay is then neither restored nor published in that installation, and an
  underlay action in it is still carried to the store as the departing capture of the next
  installation (amended 2026-10-03 during implementation).
- The projection is `PublishPending` when a departing capture exists, or when the store is
  known, the workspace underlay differs from it and the underlay is not being adjusted. While adjusting,
  nothing is requested; leaving the mode publishes the final placement once. Clearing the underlay
  (Remove) publishes a `forget`.
- An installation takes the departing work's unpublished underlay, adjusting or not, as the one
  departing capture (work id and value, or cleared), replacing an older one. A departing capture
  is published before anything else: while one exists the projection is `PublishPending` even
  when the store of the installed work is unknown, that publication writes the departing capture
  only, and the recall follows it. Loading the same work again therefore recalls what was just
  written, not the older record (amended 2026-10-03 during implementation).
- A publication completion, `Stored` or `Failed`, makes its value the known one when it belongs
  to the current installation. A failed value is therefore not requested again until the underlay
  changes; the projection never stays pending on a value that failed.
- The projection is derived where the runtime changes the workspace: the public reduction, a
  reduction inside a transaction, and an installation. The flow is written only when a
  reference-underlay action was reduced, the tracking changed, or an installation happened; a
  drawing or viewport reduction adds one reference comparison and publishes nothing.
- `EditorPersistenceWorkflow` exposes `underlayMemory` with `recall()`, `publish()` and `flush()`.
  `flush()` is a publication that also writes the value of an underlay that is being adjusted.
  None of them takes the physical-operation lease or changes `PersistenceOperationProjection` or
  its last outcome: Save As, load, export, autosave publication and recovery neither wait for the
  memory nor fail because of it, and a memory failure is not a persistence failure. No I/O runs
  inside the runtime lock. The store uses its own serialized dispatcher, composed in
  `:app:android`, so an image write never queues in front of a document save.
- `:app:android` owns `UnderlayMemoryScheduler` and `UnderlayMemoryPolicy`, held by the ViewModel
  like the autosave scheduler without a new constructor parameter. The scheduler observes the
  projection on the ViewModel scope with at most one outstanding request: a recall runs at once; a
  publication runs after the policy's quiet window of 500 ms without a newer token, so one slider
  drag is one write; the activity `ON_STOP` event calls `flush()` at once. It holds no rule.
  A process that dies before a publication completes loses that last change; the memory is best
  effort and never affects the document.

### Controls and privacy

- No control is added. Remove on the underlay row forgets the record of that work; a hidden
  underlay is remembered as hidden. The first form has no "forget all".
- The remembered image is a copy of a picture the user chose. It stays in app-private storage that
  is excluded from backups, leaves the device with no file and no export, and is deleted by Remove,
  by eviction and with the app's data. Deleting a work's file elsewhere does not delete its
  record; it leaves through eviction.

### Not decided here

- A control to forget every remembered underlay, compression of the image record, remembering
  other workspace facts (viewport, active layer, tool), and carrying the underlay between devices
  are separate Issues. No field, version or API is reserved for them.

## Rejected alternatives

### Store the underlay in the project file

Rejected by the owner on 2026-09-30: the underlay is not part of the picture, and the file format
stays unchanged.

### Widen `AppPreferences` to per-work facts

Rejected: ADR 0021 defines it as independent of the open document and backs it with platform
settings. A per-work image record has another key, another size and another lifetime.

### Keep the content URI and decode again

Rejected in ADR 0032: a persisted URI permission can be revoked and the source can move.

### One record per work

Rejected: every move, scale or opacity change would rewrite up to 4 MiB. Two records make the
frequent write 40 bytes.

### Publish every change at once

Rejected: the opacity slider of the layer panel changes the underlay on every step outside the
adjust mode. A quiet window in the platform scheduler, like the autosave one, makes a drag one
write; `ON_STOP` and the departing capture cover the cases a window would lose.

### Keep the tracking inside the persistence coordination

Rejected: a persistence transition replaces the coordination with its result, which would drop a
change recorded by a reduction inside the same transaction, and it would tie the memory to the
operation it must stay independent of.

### The cache directory

Rejected: the OS may delete it at any time, which makes the stated limit meaningless, and a
half-deleted pair would look like corruption.

### Run the memory under the persistence operation lease

Rejected: a restore after every installation would answer Busy to the user's next operation, and a
memory failure would appear in the operation projection. The recovery record blocks switches when
it is unreadable; the underlay memory must do the opposite.

### Let presentation or the ViewModel decide when to write

Rejected: `:app:android` holds no business rule and presentation owns no persistence. The reducer
is the only route of workspace changes (CMD-002), so the application derives the pending state
there and the platform only supplies the scope.

## Consequences

### Benefits

- A work opened again shows its underlay where it was left, without a file-format change.
- The document paths are untouched: no lease, no outcome, no failure of theirs involves the memory.
- The frequent write is 40 bytes, at most once per quiet window.

### Costs and risks

- Up to about 32 MiB of app-private storage holds copies of chosen pictures. A record outlives
  the deletion of its work's file until it is evicted.
- A Save As copy shares the remembered underlay of its original; Remove in one forgets it for
  both.
- The last change before a process death can be lost. A recall dropped because a switch was
  installing another work is not repeated for that installation.
- The underlay appears a moment after the installed picture, when the recall completes.
- `PersistencePorts` gains an eighth port that every construction site must supply, and
  `EditorRuntime` a third read-only projection and one more bookkeeping value.
- The adapter owns file listing and deletion for the first time, bounded to one directory.

## Enforcement impact

- `core/application`: `RememberedUnderlay` and its factory; `UnderlayMemoryPort`; the tracking, its
  projection and the three workflow entries. Tests: every rejection of the factory; round trip of
  a shown and of a hidden `ReferenceUnderlay` through `RememberedUnderlay` against the same and
  another canvas; a fresh process starts settled; recall pending after every kind of installation;
  no publication while the store is unknown; a restored value is not published again; a late
  recall for another installation is dropped; a recall after the user chose an image publishes
  that image; a recall after choose-and-remove publishes a forget and restores nothing; a recall
  during a switch is not reduced; no request while adjusting and one after leaving the mode;
  `flush()` while adjusting; Remove publishes a forget; the departing capture is written first
  and survives a second installation without a newer one; a `Failed` completion settles and is
  not requested again; a stale completion; two installations in a row; that a drawing reduction
  writes no projection; and that no recall, publication or failure changes the persistence
  operation projection, the lease or the document.
- `:adapters:persistence`: the two record codecs and the store. JVM tests with golden bytes for
  both records (lengths 14 + 4wh and 40), every unreadable case above with deletion of the pair,
  an image without a state and a state without an image, a leftover file removed by the sweep,
  the image record not rewritten and not hashed again for an unchanged image, both limits with
  the largest records, the eviction order with equal times, the protection of the pair just
  written, the use mark of a recall, an I/O failure of `recall` answered as `Absent`, and a write
  failure answered as `Failed` without an exception.
- `:app:android`: the scheduler, the policy and the composition of the separate dispatcher; tests
  that one request is outstanding at a time, that a newer token restarts the quiet window, that a
  recall does not wait for it, and that `ON_STOP` flushes at once.
- `:presentation:compose`: no control changes. One instrumented test that an underlay set in one
  runtime is shown again, with its placement, opacity and visibility, after the same work is
  loaded into a new runtime.
- `docs/ARCHITECTURE_CONSTITUTION.md` (ARC-004, ARC-005), `docs/GLOSSARY.md`,
  `docs/COMMAND_MODEL.md`, `docs/PROJECT_LAYOUT.md`, `docs/DEVELOPMENT_PLAN.md`, and the amended
  sentences of ADR 0021 and ADR 0032 in this change.
- Performance (QLT-019): the drawing path is unchanged. A drawing or viewport reduction adds one
  reference comparison of the underlay. The cost statement of the implementation covers the
  recall after an installation (one bounded read of at most 4,194,318 + 40 bytes off the main
  thread) and the two writes. No workload is registered and no device collection runs for this
  Issue.

## Migration and rollback

No data migrates and no file format changes. Rollback removes the port, the store, the tracking and
the scheduler; the directory `reference-underlays` is then unused and is deleted with the app's
data. A work stays a valid project of its version.

## Related

- Issue: [#172](https://github.com/hideyukiMORI/NENE-PIXEL/issues/172) (P4-06c)
- PR:
- [ADR 0014](0014-project-format-storage-recovery.md)
- [ADR 0018](0018-bounded-autosave-debounce-contract.md)
- [ADR 0021](0021-app-language-resources.md)
- [ADR 0032](0032-reference-underlay.md)
- Supersedes: none
- Superseded by: none

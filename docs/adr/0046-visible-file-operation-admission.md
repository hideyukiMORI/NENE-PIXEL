# ADR 0046: Visible file-operation admission

- Status: accepted
- Date: 2026-10-17
- Issue: #244
- Affected rules: `ARC-001`, `ARC-004`, `ARC-007`, `ARC-012`, `KOT-008`, `KOT-013`,
  `KOT-014`, `KOT-015`, `KOT-017`, `KOT-019`, `QLT-011`, `QLT-012`, `QLT-017`, `QLT-019`

## Context

The #230 Worker/UI candidate is reproduced on a fresh synthetic API35 emulator against main
`384204cb310ea79fd85e92f0731b0419961d7aaf`, with only an internal ViewModel construction seam
and the reproducer added. The actual editor screen, ViewModel callbacks, retained worker,
autosave scheduler and persistence workflow are connected to controlled memory ports.

While publication is held, the actual enabled PNG export closes File and waits in the worker.
Reopening File exposes an enabled Save As. Pressing it closes the panel, but the worker suppresses
the request. After publication and the first export finish, the save-port count is still zero.
The same Save As control then reaches that port in the positive control. The status during both
waiting clicks says storage is ready. The retained failing assertion and stage records establish
an admission/display defect; they do not establish document corruption or physical-storage failure.
Original source, APKs and logs are retained in the configured lab under
`outputs/issue-230-admission/native-repro-1/` and `repro-build-2/`.

Autosave correctly projects no user operation while publishing. The app worker nevertheless owns
the first user job, including its wait for publication. The screen currently observes only the
runtime operation phase, so it cannot render that additional app admission constraint.

## Decision

### Project the existing owner

`EditorOperationWorker` remains the sole owner and admission authority for its retained user job.
Expose a read-only `StateFlow<EditorOperationAvailability>` with `Available` and `Busy` values.
This is a derived projection of the existing job reference, not a second operation coordinator,
workspace value, persistence phase or saved state. Admission continues to inspect the job under
the existing lock, never the projected value.

Publish Busy under that lock immediately after reserving the lazy job and before starting it.
Install the completion callback before start. Only the matching job's completion clears ownership
and publishes Available under the same lock. Cancellation alone does not release admission:
claimed-result draining, non-cancellable cleanup and controller synchronization still complete
inside the retained job. Do not derive availability from `Job.isActive`; a reserved lazy job and
a cancelled job still doing cleanup both own admission.

The public presentation vocabulary lives beside `EditorPersistenceControls`. That existing
capability binding gains the explicit read-only flow as its fourth input. The ViewModel forwards
the worker's flow directly; the one `NenePixelEditor` entry collects it with the existing operation
and autosave flows. It adds no second collector or owner in the ViewModel, dispatcher, queue,
clock, polling or default-Available production overload. Migrate every composition caller.
An explicitly synthetic presentation-only fixture may supply Available because it does not
compose the Android worker; app integration tests use the actual worker projection.

### Render admission at every affected request

Initial File, palette JSON, reference-image pick/replace and recovery requests require Available
in addition to their existing runtime/source conditions. Continue and Create buttons in open
dialogs, PNG New Work and legacy copy/preview/accept/decline requests also observe Available;
keep their existing phase-specific eligibility. A confirmation or legacy continuation is not
required to have an Idle runtime phase.

Panel navigation remains available. File and document status use the same localized preparing
message when the runtime phase is Idle and the worker is Busy. Preserve the existing specific
messages for exporting, saving, cancellation and other active phases. A displayed recovery offer
keeps its candidate and disabled choices, with the same waiting explanation where applicable.
English, Japanese and Simplified Chinese resources change together.

Existing cancellation and local command/workspace actions keep their canonical paths. Do not
disable drawing, palette draft actions, PNG layer forms, underlay visibility/opacity/adjust/fit/
remove, or dialog cancellation merely because a file job is retained. No new cancellation handle
or cancellation guarantee is introduced for the initial wait behind autosave.

Carry availability with the affected intent where an internal callback crosses several controls.
Keep the four-parameter bound through cohesive storage inputs and typed pick/new-work actions.
Do not insert admission into document-derived layer/render projections or rescan pixels for a
job transition. Legacy source data may be derived from its existing phase instead of being
passed twice. UI state is a snapshot: the worker's duplicate guard remains necessary for stale
callbacks before recomposition. This decision does not promise to queue every input event.

## Rejected alternatives

### Queue all callbacks or remove duplicate suppression

Neither follows from a display mismatch. A queue changes cancellation, stale source, destructive
confirmation and physical-operation ownership. Removing the guard would submit competing jobs
to the runtime. Keep the first job and show the existing admission constraint.

### Pretend the runtime is exporting or block every autosave

A waiting job has no export handle yet; synthesizing a runtime phase in the app would misstate
ownership and cancellation. Disabling requests throughout every background publication also
removes the supported first-request wait. Expose the actual worker constraint separately.

### Fix only the File Save As button

The same worker accepts palette, underlay, recovery and dialog continuation requests. Leaving
their enabled states independent would retain the same mismatch at another entry point.

## Consequences

### Benefits

The first request stays retained through autosave and cleanup. Further unavailable requests are
visibly disabled, the waiting status is truthful, and completion restores existing controls.
One app owner, one runtime lease and one persistence path remain in force.

### Costs and risks

Each admitted job adds two constant-size availability publications and their affected UI updates.
There is no document scan/copy, per-pixel work or per-playback-frame publication. Register the
affected file-operation/presentation workload with #145 before integration; collect performance
only at its phase gate. The public composition binding and all callers must migrate together.
Asynchronous UI snapshots still need the existing duplicate guard; this is not a new durability,
picker-timeout, process-death or arbitrary input-delivery guarantee.

## Enforcement impact

Extend the existing worker tests for reservation before dispatch, held autosave, duplicate
suppression, cancellation/cleanup and release. Native app tests exercise the actual composed
controls, retained first request, disabled competing actions, status, cancellation and subsequent
admission. Check directly affected presentation conditions and compile all changed API consumers.
Retain the original failing reproducer separately from corrected regression evidence. Run only
selected checks under QLT-011 through QLT-019, plus documentation validation and the unchanged
required final CI. No dependency, module, schema, quality threshold or waiver changes.

## Migration and rollback

Update the app worker, composition binding and all affected controls/tests in this focused change.
There is no saved-data migration or compatibility overload. A rollback reverts the complete
binding/consumer change through a checked PR and records the reintroduced defect; it does not
keep competing admission implementations.

## Related

- Issues: #244 (this correction), #230 (remaining beta lifecycle acceptance), #145 (phase gate)
- [ADR 0043](0043-cohesive-editor-presentation-inputs.md),
  [ADR 0045](0045-android-termination-and-publication.md)
- Supersedes: none
- Superseded by: none

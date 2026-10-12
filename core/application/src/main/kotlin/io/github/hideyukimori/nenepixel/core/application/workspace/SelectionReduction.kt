package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionGeometry
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionPointerPolicy
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionSession
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTransformPolicy

/** Selection copies are geometry-only and retain every unrelated validated workspace owner. */
internal fun WorkspaceState.withSelectionSession(session: SelectionSession?): WorkspaceState =
    WorkspaceState(
        editTarget,
        activeTool,
        viewport,
        preview,
        appearance,
        actualSizeWindow,
        paletteEditSession,
        quickSelection,
        underlay,
        pendingImport,
        session,
    )

/** The sole workspace reduction for rectangle/destination input (ADR 0039). */
internal fun reduceSelection(
    state: WorkspaceState,
    action: WorkspaceAction.SelectionAction,
): WorkspaceReductionResult =
    when (action) {
        is BeginSelectionSession -> {
            WorkspaceReductionResult.Reduced(
                state.withSelectionSession(SelectionSession.begin(action.admission, state.activeLayerId)),
            )
        }

        WorkspaceAction.CancelSelectionSession -> {
            cancelSession(state)
        }

        WorkspaceAction.CancelSelectionPointer -> {
            cancelPointer(state)
        }

        is WorkspaceAction.BeginSelectionPointer -> {
            beginPointer(state, action)
        }

        is WorkspaceAction.UpdateSelectionPointer -> {
            updatePointer(state, action)
        }

        WorkspaceAction.FinishSelectionPointer -> {
            finishPointer(state)
        }

        is WorkspaceAction.SetSelectionMode -> {
            setMode(state, action)
        }

        is WorkspaceAction.TransformSelection -> {
            transform(state, action)
        }
    }

private fun beginPointer(
    state: WorkspaceState,
    action: WorkspaceAction.BeginSelectionPointer,
): WorkspaceReductionResult {
    val session = state.selectionSession ?: return rejected(state, SelectionRejection.NoSession)
    val canvas = session.admission.document.size
    return when {
        session.geometry.pointer != null -> {
            rejected(state, SelectionRejection.PointerAlreadyActive)
        }

        !canvas.contains(action.position) -> {
            WorkspaceReductionResult.Rejected(
                state,
                WorkspaceActionRejection.PreviewPositionOutsideCanvas(canvas, action.position),
            )
        }

        else -> {
            changed(state, SelectionPointerPolicy.begin(session, action.position))
        }
    }
}

private fun updatePointer(
    state: WorkspaceState,
    action: WorkspaceAction.UpdateSelectionPointer,
): WorkspaceReductionResult {
    val session = state.selectionSession ?: return rejected(state, SelectionRejection.NoSession)
    val pointer = session.geometry.pointer
    return if (pointer == null) {
        rejected(state, SelectionRejection.NoPointer)
    } else {
        changed(state, SelectionPointerPolicy.update(session, pointer, action.point))
    }
}

private fun finishPointer(state: WorkspaceState): WorkspaceReductionResult {
    val session = state.selectionSession ?: return rejected(state, SelectionRejection.NoSession)
    return if (session.geometry.pointer == null) {
        rejected(state, SelectionRejection.NoPointer)
    } else {
        changed(state, session.withGeometry(SelectionGeometry(session.geometry.draft, null)))
    }
}

private fun cancelPointer(state: WorkspaceState): WorkspaceReductionResult {
    val session = state.selectionSession
    if (session?.geometry?.pointer == null) {
        return WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.NoSelectionPointer)
    }
    return changed(state, session.cancelPointer())
}

private fun cancelSession(state: WorkspaceState): WorkspaceReductionResult =
    if (state.selectionSession == null) {
        WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.NoSelectionSession)
    } else {
        WorkspaceReductionResult.Reduced(state.withSelectionSession(null))
    }

private fun setMode(
    state: WorkspaceState,
    action: WorkspaceAction.SetSelectionMode,
): WorkspaceReductionResult {
    val session = state.selectionSession ?: return rejected(state, SelectionRejection.NoSession)
    val next = if (session.mode == action.mode) session else session.cancelPointer().withMode(action.mode)
    return changed(state, next)
}

private fun changed(
    state: WorkspaceState,
    session: SelectionSession,
): WorkspaceReductionResult =
    if (state.selectionSession === session) {
        WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.SelectionUnchanged)
    } else {
        WorkspaceReductionResult.Reduced(state.withSelectionSession(session))
    }

private fun transform(
    state: WorkspaceState,
    action: WorkspaceAction.TransformSelection,
): WorkspaceReductionResult {
    val session = state.selectionSession ?: return rejected(state, SelectionRejection.NoSession)
    val draft = session.geometry.draft
    return when {
        session.geometry.pointer != null -> {
            rejected(state, SelectionRejection.PointerActive)
        }

        draft == null -> {
            rejected(state, SelectionRejection.NoRectangle)
        }

        !session.canTransform(action.operation) -> {
            rejected(state, SelectionRejection.TransformedRegionOutsideCanvas)
        }

        else -> {
            val next = SelectionTransformPolicy.transform(session.admission.document.size, draft, action.operation)
            val changedSession = if (next == draft) session else session.withGeometry(SelectionGeometry(next, null))
            changed(state, changedSession)
        }
    }
}

private fun rejected(
    state: WorkspaceState,
    reason: SelectionRejection,
): WorkspaceReductionResult =
    WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.SelectionRejected(reason))

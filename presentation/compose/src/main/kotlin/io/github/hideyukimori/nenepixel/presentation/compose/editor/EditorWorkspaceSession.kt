package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReducer
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition

internal class EditorWorkspaceSession(
    private val commandGateway: CommandGateway,
    private val workspaceReducer: WorkspaceReducer,
    initialWorkspaceState: WorkspaceState,
) {
    var workspaceState: WorkspaceState = initialWorkspaceState
        private set

    val renderState: EditorRenderState
        get() = createRenderState()

    fun reduce(action: WorkspaceAction): PointerInputAcknowledgement {
        val hadPreview = workspaceState.preview != null
        val reduction = workspaceReducer.reduce(workspaceState, action)
        workspaceState = reduction.nextState
        val nextRenderState = createRenderState()
        return when (reduction) {
            is WorkspaceReductionResult.Reduced -> {
                if (hadPreview && reduction.nextState.preview == null) {
                    PointerInputAcknowledgement.Cancelled(nextRenderState)
                } else {
                    PointerInputAcknowledgement.Accepted(nextRenderState)
                }
            }

            is WorkspaceReductionResult.Unchanged -> {
                PointerInputAcknowledgement.Ignored(nextRenderState)
            }

            is WorkspaceReductionResult.Rejected -> {
                PointerInputAcknowledgement.Rejected(nextRenderState)
            }

            is WorkspaceReductionResult.CommitPrepared -> {
                PointerInputAcknowledgement.Accepted(nextRenderState)
            }
        }
    }

    fun finishGesture(position: PixelPosition): PointerInputAcknowledgement {
        val extension = workspaceReducer.reduce(workspaceState, WorkspaceAction.ExtendGesturePreview(position))
        workspaceState = extension.nextState
        return when (extension) {
            is WorkspaceReductionResult.Reduced,
            is WorkspaceReductionResult.Unchanged,
            -> prepareAndExecute()

            is WorkspaceReductionResult.Rejected -> reduce(WorkspaceAction.CancelGesturePreview)

            is WorkspaceReductionResult.CommitPrepared -> unexpectedExtensionResult()
        }
    }

    fun ignored(): PointerInputAcknowledgement = PointerInputAcknowledgement.Ignored(createRenderState())

    fun rejected(): PointerInputAcknowledgement = PointerInputAcknowledgement.Rejected(createRenderState())

    private fun prepareAndExecute(): PointerInputAcknowledgement {
        val preparation = workspaceReducer.reduce(workspaceState, WorkspaceAction.PrepareGestureCommit)
        workspaceState = preparation.nextState
        return when (preparation) {
            is WorkspaceReductionResult.CommitPrepared -> {
                val commandResult = execute(preparation)
                PointerInputAcknowledgement.Accepted(createRenderState(), commandResult)
            }

            is WorkspaceReductionResult.Reduced,
            is WorkspaceReductionResult.Unchanged,
            is WorkspaceReductionResult.Rejected,
            -> {
                rejected()
            }
        }
    }

    private fun execute(preparation: WorkspaceReductionResult.CommitPrepared): CommandResult {
        val target = commandGateway.runtimeState.documentState
        val command = ApplyStrokeCommand.create(target.id, target.revision, preparation.stroke)
        return commandGateway.execute(command)
    }

    private fun createRenderState(): EditorRenderState {
        val runtimeState = commandGateway.runtimeState
        return EditorRenderState(
            snapshot = runtimeState.documentState.snapshot,
            activeColor = workspaceState.activeColor,
            preview = workspaceState.preview,
            viewport = workspaceState.viewport,
            canUndo = runtimeState.historyAvailability == HistoryAvailability.UndoAvailable,
            canRedo = runtimeState.historyAvailability == HistoryAvailability.RedoAvailable,
        )
    }

    private fun unexpectedExtensionResult(): Nothing =
        error("ExtendGesturePreview unexpectedly prepared a document commit.")
}

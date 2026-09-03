package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.RedoCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequest
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition

internal class EditorRuntimeAdapter(
    private val runtime: EditorRuntime,
) {
    val renderState: EditorRenderState
        get() = createRenderState()

    fun reduce(action: WorkspaceAction): PointerInputAcknowledgement {
        val hadPreview = runtime.state.workspaceState.preview != null
        val reduction = runtime.reduce(action)
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
        val extension = runtime.reduce(WorkspaceAction.ExtendGesturePreview(position))
        return when (extension) {
            is WorkspaceReductionResult.Reduced,
            is WorkspaceReductionResult.Unchanged,
            -> prepareAndExecute()

            is WorkspaceReductionResult.Rejected -> reduce(WorkspaceAction.CancelGesturePreview)

            is WorkspaceReductionResult.CommitPrepared -> unexpectedExtensionResult()
        }
    }

    fun undo(): EditorRenderState {
        val target = runtime.state.documentState
        runtime.execute(UndoCommand.create(target.id, target.revision))
        return createRenderState()
    }

    fun redo(): EditorRenderState {
        val target = runtime.state.documentState
        runtime.execute(RedoCommand.create(target.id, target.revision))
        return createRenderState()
    }

    fun createNewDocument(
        rawWidth: String,
        rawHeight: String,
    ): NewDocumentSubmission =
        when (val result = runtime.createNewDocument(NewDocumentRequest.create(rawWidth, rawHeight))) {
            is NewDocumentResult.Created -> {
                NewDocumentSubmission.Created(createRenderState())
            }

            is NewDocumentResult.Rejected -> {
                NewDocumentSubmission.Rejected(createRenderState(), result.rejection.toUserMessage())
            }
        }

    fun ignored(): PointerInputAcknowledgement = PointerInputAcknowledgement.Ignored(createRenderState())

    fun rejected(): PointerInputAcknowledgement = PointerInputAcknowledgement.Rejected(createRenderState())

    private fun prepareAndExecute(): PointerInputAcknowledgement {
        val preparation = runtime.reduce(WorkspaceAction.PrepareGestureCommit)
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
        val target = runtime.state.documentState
        val command = ApplyStrokeCommand.create(target.id, target.revision, preparation.stroke)
        return runtime.execute(command)
    }

    private fun createRenderState(): EditorRenderState {
        val state = runtime.state
        return EditorRenderState(
            snapshot = state.documentState.snapshot,
            activeColor = state.workspaceState.activeColor,
            activeTool = state.workspaceState.activeTool,
            preview = state.workspaceState.preview,
            viewport = state.workspaceState.viewport,
            canUndo = state.historyAvailability == HistoryAvailability.UndoAvailable,
            canRedo = state.historyAvailability == HistoryAvailability.RedoAvailable,
        )
    }

    private fun unexpectedExtensionResult(): Nothing =
        error("ExtendGesturePreview unexpectedly prepared a document commit.")
}

package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReducer
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition

public class FixedSliceEditorController private constructor(
    private val commandGateway: CommandGateway,
    private val historyCommandAdapter: HistoryCommandAdapter,
    private val workspaceReducer: WorkspaceReducer,
    initialWorkspaceState: WorkspaceState,
) {
    private var currentWorkspaceState: WorkspaceState = initialWorkspaceState

    public val renderState: EditorRenderState
        get() = createRenderState()

    public val callbacks: EditorCallbacks =
        EditorCallbacks(
            pointerDown = { position -> pointerDown(position).renderState },
            pointerMove = { position -> pointerMove(position).renderState },
            pointerEnd = { position -> pointerEnd(position).renderState },
            pointerCancel = { pointerCancel().renderState },
            undo = { commandExecuted(historyCommandAdapter.undo()).renderState },
            redo = { commandExecuted(historyCommandAdapter.redo()).renderState },
        )

    internal val documentState: DocumentState
        get() = commandGateway.runtimeState.documentState

    internal val workspaceState: WorkspaceState
        get() = currentWorkspaceState

    internal fun pointerDown(position: PixelPosition): EditorInteractionResult =
        reduceWorkspace(
            WorkspaceAction.BeginGesturePreview(commandGateway.runtimeState.documentState.size, position),
        )

    internal fun pointerMove(position: PixelPosition): EditorInteractionResult =
        reduceWorkspace(WorkspaceAction.ExtendGesturePreview(position))

    internal fun pointerEnd(position: PixelPosition): EditorInteractionResult {
        val extension = workspaceReducer.reduce(currentWorkspaceState, WorkspaceAction.ExtendGesturePreview(position))
        currentWorkspaceState = extension.nextState
        return when (extension) {
            is WorkspaceReductionResult.Reduced -> prepareAndExecute()
            is WorkspaceReductionResult.Unchanged -> prepareAndExecute()
            is WorkspaceReductionResult.Rejected -> reduceWorkspace(WorkspaceAction.CancelGesturePreview)
            is WorkspaceReductionResult.CommitPrepared -> unexpectedExtensionResult()
        }
    }

    internal fun pointerCancel(): EditorInteractionResult = reduceWorkspace(WorkspaceAction.CancelGesturePreview)

    private fun prepareAndExecute(): EditorInteractionResult {
        val preparation = workspaceReducer.reduce(currentWorkspaceState, WorkspaceAction.PrepareGestureCommit)
        currentWorkspaceState = preparation.nextState
        return when (preparation) {
            is WorkspaceReductionResult.CommitPrepared -> execute(preparation)
            is WorkspaceReductionResult.Reduced -> workspaceReduced(preparation)
            is WorkspaceReductionResult.Unchanged -> workspaceReduced(preparation)
            is WorkspaceReductionResult.Rejected -> workspaceReduced(preparation)
        }
    }

    private fun execute(preparation: WorkspaceReductionResult.CommitPrepared): EditorInteractionResult {
        val target = commandGateway.runtimeState.documentState
        val command = ApplyStrokeCommand.create(target.id, target.revision, preparation.stroke)
        val result = commandGateway.execute(command)
        return commandExecuted(result)
    }

    private fun commandExecuted(result: CommandResult): EditorInteractionResult =
        EditorInteractionResult.CommandExecuted(
            renderState = createRenderState(),
            commandResult = result,
        )

    private fun reduceWorkspace(action: WorkspaceAction): EditorInteractionResult {
        val reduction = workspaceReducer.reduce(currentWorkspaceState, action)
        currentWorkspaceState = reduction.nextState
        return workspaceReduced(reduction)
    }

    private fun workspaceReduced(reduction: WorkspaceReductionResult): EditorInteractionResult =
        EditorInteractionResult.WorkspaceReduced(createRenderState(), reduction)

    private fun createRenderState(): EditorRenderState {
        val runtimeState = commandGateway.runtimeState
        return EditorRenderState(
            snapshot = runtimeState.documentState.snapshot,
            activeColor = currentWorkspaceState.activeColor,
            preview = currentWorkspaceState.preview,
            canUndo = runtimeState.historyAvailability == HistoryAvailability.UndoAvailable,
            canRedo = runtimeState.historyAvailability == HistoryAvailability.RedoAvailable,
        )
    }

    private fun unexpectedExtensionResult(): Nothing =
        error("ExtendGesturePreview unexpectedly prepared a document commit.")

    public companion object {
        public fun create(
            commandGateway: CommandGateway,
            workspaceReducer: WorkspaceReducer,
            initialWorkspaceState: WorkspaceState,
        ): FixedSliceEditorController =
            FixedSliceEditorController(
                commandGateway,
                HistoryCommandAdapter(commandGateway),
                workspaceReducer,
                initialWorkspaceState,
            )
    }
}

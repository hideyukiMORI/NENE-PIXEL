package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.AddFrameCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.DeleteFrameCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.DocumentCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.DuplicateFrameCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.MoveFrameCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.command.SetFrameDurationCommand
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

/** Frame structure is one document command; selecting a frame is one canonical workspace action. */
internal class EditorFrameCallbacks(
    private val runtime: EditorRuntime,
    private val adapter: EditorRuntimeAdapter,
    private val publish: (EditorRenderState) -> EditorRenderState,
) {
    fun onSelect(id: FrameId): WorkspaceReductionResult {
        val result = runtime.reduce(WorkspaceAction.SelectFrame(id))
        adapter.frameNotice =
            if (result is WorkspaceReductionResult.Rejected) FrameEditorNotice.Rejected(result.rejection) else null
        adapter.layerNotices.recordSelection(result, runtime.state.documentState)
        publish(adapter.renderState)
        return result
    }

    fun onAdd(id: FrameId): EditorRenderState = execute(AddFrameCommand.create(runtime.captureSource(), id))

    fun onDuplicate(id: FrameId): EditorRenderState = execute(DuplicateFrameCommand.create(runtime.captureSource(), id))

    fun onDelete(id: FrameId): EditorRenderState = execute(DeleteFrameCommand.create(runtime.captureSource(), id))

    fun onMove(
        id: FrameId,
        position: Int,
    ): EditorRenderState = execute(MoveFrameCommand.create(runtime.captureSource(), id, position))

    fun onDuration(
        id: FrameId,
        duration: FrameDuration,
    ): EditorRenderState = execute(SetFrameDurationCommand.create(runtime.captureSource(), id, duration))

    private fun execute(command: DocumentCommand): EditorRenderState {
        adapter.frameNotice =
            when (val result = runtime.execute(command)) {
                is CommandResult.Applied -> {
                    null
                }

                is CommandResult.Rejected -> {
                    if (result.reason == RejectionReason.NoEffectiveChange) {
                        null
                    } else {
                        FrameEditorNotice.CommandRejected(result.reason)
                    }
                }

                is CommandResult.Failed -> {
                    FrameEditorNotice.Failed(result.failure)
                }
            }
        return publish(adapter.renderState)
    }
}

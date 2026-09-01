package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult

internal sealed interface EditorInteractionResult {
    val renderState: EditorRenderState

    data class WorkspaceReduced(
        override val renderState: EditorRenderState,
        val reduction: WorkspaceReductionResult,
    ) : EditorInteractionResult

    data class CommandExecuted(
        override val renderState: EditorRenderState,
        val commandResult: CommandResult,
    ) : EditorInteractionResult
}

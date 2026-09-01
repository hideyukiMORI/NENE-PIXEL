package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult

internal sealed interface PointerInputAcknowledgement {
    val renderState: EditorRenderState

    data class Accepted(
        override val renderState: EditorRenderState,
        val commandResult: CommandResult? = null,
    ) : PointerInputAcknowledgement

    data class Ignored(
        override val renderState: EditorRenderState,
    ) : PointerInputAcknowledgement

    data class Cancelled(
        override val renderState: EditorRenderState,
    ) : PointerInputAcknowledgement

    data class Rejected(
        override val renderState: EditorRenderState,
    ) : PointerInputAcknowledgement
}

package io.github.hideyukimori.nenepixel.presentation.compose.editor

internal sealed interface NewDocumentSubmission {
    val renderState: EditorRenderState

    data class Created(
        override val renderState: EditorRenderState,
    ) : NewDocumentSubmission

    data class Rejected(
        override val renderState: EditorRenderState,
        val userMessage: String,
    ) : NewDocumentSubmission
}

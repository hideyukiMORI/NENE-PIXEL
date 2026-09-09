package io.github.hideyukimori.nenepixel.presentation.compose.editor

internal sealed interface NewDocumentSubmission {
    data object Submitted : NewDocumentSubmission

    data class Rejected(
        val userMessage: String,
    ) : NewDocumentSubmission
}

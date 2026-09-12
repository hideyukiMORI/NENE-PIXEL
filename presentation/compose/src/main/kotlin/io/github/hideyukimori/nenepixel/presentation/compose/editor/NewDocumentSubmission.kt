package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRejection

internal sealed interface NewDocumentSubmission {
    data object Submitted : NewDocumentSubmission

    data class Rejected(
        val rejection: NewDocumentRejection,
    ) : NewDocumentSubmission
}

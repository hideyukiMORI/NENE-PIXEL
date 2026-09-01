package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason

internal sealed interface DocumentTransitionResult {
    data class Created(
        val transition: DocumentTransition,
    ) : DocumentTransitionResult

    data class Rejected(
        val reason: RejectionReason,
    ) : DocumentTransitionResult
}

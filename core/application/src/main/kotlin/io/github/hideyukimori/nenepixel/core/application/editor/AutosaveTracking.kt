package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveLastOutcome
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

internal data class AutosaveCapture(
    val document: DocumentState,
    val revision: Long,
    val runtimeGeneration: Long,
)

internal data class AutosaveTracking(
    val pending: AutosaveCapture?,
    val publishedRevision: Long?,
    val publishedGeneration: Long,
    val lastOutcome: AutosaveLastOutcome,
) {
    fun recorded(capture: AutosaveCapture): AutosaveTracking = copy(pending = capture)

    fun abandoned(): AutosaveTracking = copy(pending = null)

    fun droppedAt(revision: Long): AutosaveTracking =
        if (pending != null && pending.revision == revision) copy(pending = null) else this

    fun withOutcome(outcome: AutosaveLastOutcome): AutosaveTracking = copy(lastOutcome = outcome)

    fun published(
        capture: AutosaveCapture,
        outcome: AutosaveLastOutcome,
    ): AutosaveTracking =
        droppedAt(capture.revision).copy(
            publishedRevision = capture.revision,
            publishedGeneration = capture.runtimeGeneration,
            lastOutcome = outcome,
        )

    companion object {
        fun initial(runtimeGeneration: Long): AutosaveTracking =
            AutosaveTracking(null, null, runtimeGeneration, AutosaveLastOutcome.None)
    }
}

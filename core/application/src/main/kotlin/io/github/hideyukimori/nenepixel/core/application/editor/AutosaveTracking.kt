package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveStateToken
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

internal data class AutosaveCapture(
    val document: DocumentState,
    val stateToken: AutosaveStateToken,
) {
    val revision: Long
        get() = document.revision.value
}

internal data class AutosaveTracking(
    val pending: AutosaveCapture?,
    val publishedStateToken: AutosaveStateToken?,
    val lastOutcome: AutosaveLastOutcome,
) {
    fun recorded(capture: AutosaveCapture): AutosaveTracking = copy(pending = capture)

    fun abandoned(): AutosaveTracking = copy(pending = null)

    fun droppedAt(stateToken: AutosaveStateToken): AutosaveTracking =
        if (pending?.stateToken == stateToken) copy(pending = null) else this

    fun withoutPublishedState(): AutosaveTracking = copy(publishedStateToken = null)

    fun withOutcome(outcome: AutosaveLastOutcome): AutosaveTracking = copy(lastOutcome = outcome)

    fun published(
        capture: AutosaveCapture,
        outcome: AutosaveLastOutcome,
    ): AutosaveTracking =
        droppedAt(capture.stateToken).copy(
            publishedStateToken = capture.stateToken,
            lastOutcome = outcome,
        )

    companion object {
        fun initial(): AutosaveTracking = AutosaveTracking(null, null, AutosaveLastOutcome.None)
    }
}

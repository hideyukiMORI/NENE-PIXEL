package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

internal sealed interface DocumentOutputStart {
    data class Started(
        val handle: PersistenceOperationHandle,
        val document: DocumentState,
    ) : DocumentOutputStart

    data object Busy : DocumentOutputStart

    data object RecoveryUnavailable : DocumentOutputStart

    data object IdentityExhausted : DocumentOutputStart
}

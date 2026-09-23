package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle

internal sealed interface DocumentOutputLease {
    data class Started(
        val handle: PersistenceOperationHandle,
    ) : DocumentOutputLease

    data object Busy : DocumentOutputLease

    data object RecoveryUnavailable : DocumentOutputLease

    data object IdentityExhausted : DocumentOutputLease
}

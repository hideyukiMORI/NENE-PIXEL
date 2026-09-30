package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle

internal sealed interface ReferenceImagePickStart {
    data class Started(
        val handle: PersistenceOperationHandle,
    ) : ReferenceImagePickStart

    data object Busy : ReferenceImagePickStart

    data object RecoveryUnavailable : ReferenceImagePickStart

    data object IdentityExhausted : ReferenceImagePickStart
}

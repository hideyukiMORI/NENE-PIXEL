package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle

internal sealed interface PngImportPickStart {
    data class Started(
        val handle: PersistenceOperationHandle,
    ) : PngImportPickStart

    data object Busy : PngImportPickStart

    data object RecoveryUnavailable : PngImportPickStart

    data object IdentityExhausted : PngImportPickStart

    data object PaletteSessionActive : PngImportPickStart
}

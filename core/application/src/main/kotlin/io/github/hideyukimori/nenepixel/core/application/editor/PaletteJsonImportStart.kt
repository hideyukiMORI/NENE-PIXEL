package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle

internal sealed interface PaletteJsonImportStart {
    data class Started(
        val handle: PersistenceOperationHandle,
    ) : PaletteJsonImportStart

    data object Busy : PaletteJsonImportStart

    data object RecoveryUnavailable : PaletteJsonImportStart

    data object IdentityExhausted : PaletteJsonImportStart
}

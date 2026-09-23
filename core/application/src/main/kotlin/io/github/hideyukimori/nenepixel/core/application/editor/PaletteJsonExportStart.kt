package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition

internal sealed interface PaletteJsonExportStart {
    data class Started(
        val handle: PersistenceOperationHandle,
        val definition: PaletteDefinition,
    ) : PaletteJsonExportStart

    data object Busy : PaletteJsonExportStart

    data object RecoveryUnavailable : PaletteJsonExportStart

    data object IdentityExhausted : PaletteJsonExportStart
}

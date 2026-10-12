package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.LayerImportTarget

internal sealed interface PngImportPickStart {
    data class Started(
        val handle: PersistenceOperationHandle,
        val target: LayerImportTarget,
    ) : PngImportPickStart

    data object Busy : PngImportPickStart

    data object RecoveryUnavailable : PngImportPickStart

    data object IdentityExhausted : PngImportPickStart

    data object PaletteSessionActive : PngImportPickStart
}

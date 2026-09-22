package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

internal data class ReconcileDocumentPalette(
    val index: PaletteIndex,
) : WorkspaceAction

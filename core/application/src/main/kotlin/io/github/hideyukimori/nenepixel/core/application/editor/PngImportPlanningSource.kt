package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition

/** The installed document's canvas size and palette definition that a picked PNG is planned against (ADR 0033). */
internal data class PngImportPlanningSource(
    val canvas: CanvasSize,
    val definition: PaletteDefinition,
)

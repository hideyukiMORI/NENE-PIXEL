package io.github.hideyukimori.nenepixel.core.pixelengine.importing

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.importing.NewWorkImportPlan
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/**
 * Plans [raster] as a new work (ADR 0033): the canvas is the raster's size, the palette lists the
 * colours in order of first row-major occurrence (one colour gets a duplicate second slot) and
 * the default slot is slot 0.
 */
internal fun planNewWork(raster: ImportRaster): NewWorkImportResult {
    val canvas = canvasOf(raster) ?: return NewWorkImportResult.AboveCanvasLimit
    val region = RasterImportRegion(raster, canvas)
    val colors = region.colors
    return when {
        colors.isEmpty() -> {
            NewWorkImportResult.NothingToImport
        }

        colors.size > PaletteLimits.MAX_ENTRY_COUNT -> {
            NewWorkImportResult.TooManyColors(colors.size)
        }

        else -> {
            val snapshot = region.snapshot(IntArray(colors.size) { it }).requiredValue()
            NewWorkImportResult.Planned(NewWorkImportPlan.create(definitionOf(colors), snapshot).requiredValue())
        }
    }
}

private fun canvasOf(raster: ImportRaster): CanvasSize? {
    val width = CanvasWidth.create(raster.width)
    val height = CanvasHeight.create(raster.height)
    return if (width is DomainValueResult.Created && height is DomainValueResult.Created) {
        CanvasSize.create(width.value, height.value)
    } else {
        null
    }
}

private fun definitionOf(colors: IntArray): PaletteDefinition {
    val entries = colors.map(PixelColor::fromPackedRgba8888)
    val slots = if (entries.size == 1) entries + entries else entries
    return PaletteDefinition.create(Palette.create(slots).requiredValue(), PaletteIndex.first).requiredValue()
}

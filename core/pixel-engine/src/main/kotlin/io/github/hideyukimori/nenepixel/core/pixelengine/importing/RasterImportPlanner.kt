package io.github.hideyukimori.nenepixel.core.pixelengine.importing

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.importing.LayerImportLoss
import io.github.hideyukimori.nenepixel.core.domain.importing.LayerImportPlan
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.NearestPaletteEntry

/**
 * The only owner of the colour mapping of a picture import (ADR 0033). Pure and deterministic.
 */
public object RasterImportPlanner {
    /** The number of distinct colours of the whole raster; transparent pixels are not colours. */
    public fun colorCount(raster: ImportRaster): Int = RasterColors.sortDistinct(raster.copyPackedRgba8888())

    /** Plans [raster] as a new work whose canvas is the raster's size. */
    public fun newWork(raster: ImportRaster): NewWorkImportResult = planNewWork(raster)

    /** Plans a layer that appends its new colours to [source] while the palette has room. */
    public fun appending(
        raster: ImportRaster,
        canvas: CanvasSize,
        source: PaletteDefinition,
    ): LayerImportResult {
        val region = RasterImportRegion(raster, canvas)
        return planned(region, source) { appendingMapping(region.colors, source) }
    }

    /** Plans a layer whose colours take the nearest entry of [source]. */
    public fun converting(
        raster: ImportRaster,
        canvas: CanvasSize,
        source: PaletteDefinition,
    ): LayerImportResult {
        val region = RasterImportRegion(raster, canvas)
        return planned(region, source) { convertingMapping(region.colors, source) }
    }

    private inline fun planned(
        region: RasterImportRegion,
        source: PaletteDefinition,
        mapping: () -> RasterColorMapping,
    ): LayerImportResult =
        if (region.colors.isEmpty()) {
            LayerImportResult.NothingToImport
        } else {
            val chosen = mapping()
            val loss = LayerImportLoss.create(chosen.nearestColorCount, region.droppedPixelCount).requiredValue()
            val snapshot = region.snapshot(chosen.slots).requiredValue()
            LayerImportResult.Planned(LayerImportPlan.create(source, chosen.target, snapshot, loss).requiredValue())
        }

    private fun appendingMapping(
        colors: IntArray,
        source: PaletteDefinition,
    ): RasterColorMapping {
        val sourceColors = packedColors(source)
        val room = PaletteLimits.MAX_ENTRY_COUNT - sourceColors.size
        val appended = ArrayList<PixelColor>()
        val slots =
            IntArray(colors.size) { position ->
                val exact = sourceColors.indexOf(colors[position])
                when {
                    exact >= 0 -> {
                        exact
                    }

                    appended.size < room -> {
                        appended += PixelColor.fromPackedRgba8888(colors[position])
                        sourceColors.size + appended.lastIndex
                    }

                    else -> {
                        PENDING
                    }
                }
            }
        val target = if (appended.isEmpty()) source else extended(source, appended)
        return RasterColorMapping(target, slots, resolvePending(colors, slots, target))
    }

    private fun resolvePending(
        colors: IntArray,
        slots: IntArray,
        target: PaletteDefinition,
    ): Int {
        val entries = target.palette.entries()
        var resolved = 0
        slots.indices.filter { slots[it] == PENDING }.forEach { position ->
            slots[position] = NearestPaletteEntry.find(PixelColor.fromPackedRgba8888(colors[position]), entries).value
            resolved += 1
        }
        return resolved
    }

    private fun convertingMapping(
        colors: IntArray,
        source: PaletteDefinition,
    ): RasterColorMapping {
        val entries = source.palette.entries()
        val sourceColors = packedColors(source)
        var nearest = 0
        val slots =
            IntArray(colors.size) { position ->
                val exact = sourceColors.indexOf(colors[position])
                if (exact >= 0) {
                    exact
                } else {
                    nearest += 1
                    NearestPaletteEntry.find(PixelColor.fromPackedRgba8888(colors[position]), entries).value
                }
            }
        return RasterColorMapping(source, slots, nearest)
    }

    private fun extended(
        source: PaletteDefinition,
        appended: List<PixelColor>,
    ): PaletteDefinition {
        val colors = source.palette.entries().map { it.color } + appended
        return PaletteDefinition.create(Palette.create(colors).requiredValue(), source.defaultIndex).requiredValue()
    }

    private fun packedColors(definition: PaletteDefinition): IntArray =
        definition.palette
            .entries()
            .map { it.color.toPackedRgba8888() }
            .toIntArray()

    private const val PENDING: Int = -1
}

/** Unwraps a value the planner has already validated; a rejection is a planner bug. */
internal fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("A validated picture-import invariant was rejected: $rejection")
    }

package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal object PaletteRemapTestValues {
    fun palette(
        vararg rgba: Long,
        default: Int = 0,
    ): PaletteDefinition = definition(rgba.map { PixelColor.fromPackedRgba8888(it.toInt()) }, default)

    fun definition(
        colors: List<PixelColor>,
        default: Int = 0,
    ): PaletteDefinition = value(PaletteDefinition.create(value(Palette.create(colors)), index(default)))

    fun index(number: Int): PaletteIndex = value(PaletteIndex.create(number))

    fun indices(vararg values: Int): List<PaletteIndex> = values.map(::index)

    fun planned(result: PaletteRemapResult): PaletteRemap =
        when (result) {
            is PaletteRemapResult.Planned -> result.remap
            is PaletteRemapResult.Rejected -> error("Unexpected planner rejection: ${result.rejection}")
        }

    fun rejection(result: PaletteRemapResult): PaletteRemapRejection =
        when (result) {
            is PaletteRemapResult.Planned -> error("Expected planner rejection")
            is PaletteRemapResult.Rejected -> result.rejection
        }

    fun <T> value(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Unexpected fixture rejection: ${result.rejection}")
        }
}

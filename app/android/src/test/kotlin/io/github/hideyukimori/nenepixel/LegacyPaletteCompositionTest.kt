package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LegacyPaletteCompositionTest {
    @Test
    fun fixedConversionPresetsKeepTheAcceptedSizesDefaultsAndTransparencyRules() {
        val presets = createLegacyPalettePresets()

        assertPreset(presets.dusk, 16, 0, transparent = true)
        assertPreset(presets.grayscale, 32, 31, transparent = true)
        assertPreset(presets.swatches, 256, 0, transparent = false)
    }

    private fun assertPreset(
        definition: io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition,
        count: Int,
        defaultIndex: Int,
        transparent: Boolean,
    ) {
        assertEquals(count, definition.palette.entryCount)
        assertEquals(defaultIndex, definition.defaultIndex.value)
        val defaultColor =
            definition.palette
                .entryAt(PaletteIndex.create(defaultIndex).value())
                .value()
                .color
        assertEquals(if (transparent) PixelColor.blank else OPAQUE_BLACK, defaultColor)
    }

    private fun <T> io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult<T>.value(): T =
        when (this) {
            is io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult.Created -> {
                value
            }

            is io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult.Rejected -> {
                error("Invalid preset test fixture: $rejection")
            }
        }

    private companion object {
        val OPAQUE_BLACK: PixelColor = PixelColor.fromPackedRgba8888(0x000000ff)
    }
}

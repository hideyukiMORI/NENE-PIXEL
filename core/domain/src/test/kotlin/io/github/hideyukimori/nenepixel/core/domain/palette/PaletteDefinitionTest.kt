package io.github.hideyukimori.nenepixel.core.domain.palette

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class PaletteDefinitionTest {
    @Test
    fun `definition accepts two and 256 actual slots including duplicates and hidden rgb`() {
        listOf(2, 256).forEach { count ->
            val colors = MutableList(count) { PixelColor.fromPackedRgba8888(0x12345600) }
            val palette = created(Palette.create(colors))
            val last = created(PaletteIndex.create(count - 1))
            val definition = created(PaletteDefinition.create(palette, last))
            colors.clear()

            assertSame(palette, definition.palette)
            assertEquals(count, definition.palette.entryCount)
            assertEquals(last, definition.defaultIndex)
            assertEquals(0x12345600, created(palette.entryAt(last)).color.toPackedRgba8888())
        }
        assertEquals(256, PaletteLimits.MAX_ENTRY_COUNT)
    }

    @Test
    fun `one color remains a valid tool palette but cannot define an interchange palette`() {
        val palette = created(Palette.create(listOf(PixelColor.blank)))
        val failure = rejected(PaletteDefinition.create(palette, PaletteIndex.first))
        val below = assertInstanceOf(DomainValueRejection.PaletteBelowDefinitionMinimum::class.java, failure)
        assertEquals(1, below.attemptedCount)
        assertEquals(2, below.minimum)
    }

    @Test
    fun `default must refer to this palette and can be nontransparent`() {
        val palette = created(Palette.create(listOf(PixelColor.blank, PixelColor.fromPackedRgba8888(-1))))
        val default = created(PaletteIndex.create(1))
        assertEquals(default, created(PaletteDefinition.create(palette, default)).defaultIndex)
        assertInstanceOf(
            DomainValueRejection.PaletteIndexOutsidePalette::class.java,
            rejected(PaletteDefinition.create(palette, created(PaletteIndex.create(2)))),
        )
    }

    @Test
    fun `definition equality includes default and exact ordered colors`() {
        val colors = listOf(PixelColor.blank, PixelColor.fromPackedRgba8888(0x12345600))
        val palette = created(Palette.create(colors))
        val first = created(PaletteDefinition.create(palette, PaletteIndex.first))
        val equal = created(PaletteDefinition.create(created(Palette.create(colors)), PaletteIndex.first))
        assertEquals(first, equal)
        assertEquals(first.hashCode(), equal.hashCode())
        assertNotEquals(first, created(PaletteDefinition.create(palette, created(PaletteIndex.create(1)))))
        assertNotEquals(
            first,
            created(PaletteDefinition.create(created(Palette.create(colors.reversed())), PaletteIndex.first)),
        )
    }
}

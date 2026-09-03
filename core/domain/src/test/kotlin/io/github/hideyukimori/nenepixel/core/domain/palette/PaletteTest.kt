package io.github.hideyukimori.nenepixel.core.domain.palette

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.color
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class PaletteTest {
    private val exactColor = color(1, 2, 3, 4)
    private val otherColor = color(5, 6, 7, 8)

    @Test
    fun `palette accepts one and maximum entries then rejects empty and maximum plus one`() {
        val one = created(Palette.create(listOf(exactColor)))
        val maximum = created(Palette.create(List(PaletteLimits.MAX_ENTRY_COUNT) { exactColor }))
        val empty = rejected(Palette.create(emptyList()))
        val oversized = rejected(Palette.create(List(PaletteLimits.MAX_ENTRY_COUNT + 1) { exactColor }))

        assertEquals(1, one.entryCount)
        assertEquals(PaletteLimits.MAX_ENTRY_COUNT, maximum.entryCount)
        assertEquals(DomainValueRejection.EmptyPalette, empty)
        val above = assertInstanceOf(DomainValueRejection.PaletteAboveSupportedMaximum::class.java, oversized)
        assertEquals(PaletteLimits.MAX_ENTRY_COUNT + 1, above.attemptedCount)
        assertEquals(PaletteLimits.MAX_ENTRY_COUNT, above.maximum)
    }

    @Test
    fun `palette defensively copies input and derives ordered indexed entries`() {
        val input = mutableListOf(exactColor, otherColor)
        val palette = created(Palette.create(input))
        input[0] = otherColor

        val entries = palette.entries()

        assertEquals(PaletteIndex.first, entries[0].index)
        assertEquals(exactColor, entries[0].color)
        assertEquals(created(PaletteIndex.create(1)), entries[1].index)
        assertEquals(otherColor, entries[1].color)
        assertEquals(color(1, 2, 3, 4), entries[0].color)
        assertEquals(palette, created(Palette.create(listOf(exactColor, otherColor))))
        assertEquals(palette.hashCode(), created(Palette.create(listOf(exactColor, otherColor))).hashCode())
        assertNotEquals(palette, created(Palette.create(listOf(otherColor, exactColor))))
    }

    @Test
    fun `typed lookup preserves exact rgba and rejects index outside palette`() {
        val palette = created(Palette.create(listOf(exactColor)))
        val found = created(palette.entryAt(PaletteIndex.first))
        val outsideIndex = created(PaletteIndex.create(1))
        val rejected = rejected(palette.entryAt(outsideIndex))

        assertEquals(exactColor, found.color)
        assertEquals(0x01020304, found.color.toPackedRgba8888())
        val outside = assertInstanceOf(DomainValueRejection.PaletteIndexOutsidePalette::class.java, rejected)
        assertEquals(outsideIndex, outside.attemptedIndex)
        assertEquals(1, outside.entryCount)
    }
}

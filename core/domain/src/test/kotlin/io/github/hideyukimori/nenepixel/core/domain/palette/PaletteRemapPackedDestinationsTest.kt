package io.github.hideyukimori.nenepixel.core.domain.palette

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PaletteRemapPackedDestinationsTest {
    @Test
    fun `packed destinations carry every entry as an unsigned byte`() {
        val source = definition(MAX_ENTRIES)
        val target = definition(MAX_ENTRIES)
        val remap = created(PaletteRemap.create(source, target, List(MAX_ENTRIES) { index(MAX_ENTRIES - 1 - it) }))

        val packed = remap.copyPackedDestinations()

        assertEquals(MAX_ENTRIES, packed.size)
        assertArrayEquals(ByteArray(MAX_ENTRIES) { (MAX_ENTRIES - 1 - it).toByte() }, packed)
        assertEquals(remap.destinations(), packed.map { created(PaletteIndex.create(it.toInt() and U8_MASK)) })
    }

    @Test
    fun `each packed bulk read returns an independent copy`() {
        val definition = definition(3)
        val remap = created(PaletteRemap.create(definition, definition, listOf(index(2), index(0), index(1))))

        val first = remap.copyPackedDestinations()
        first[0] = 0

        assertArrayEquals(byteArrayOf(2, 0, 1), remap.copyPackedDestinations())
        assertEquals(listOf(index(2), index(0), index(1)), remap.destinations())
    }

    private fun definition(count: Int): PaletteDefinition =
        created(PaletteDefinition.create(created(Palette.create(List(count) { PixelColor.blank })), index(0)))

    private fun index(number: Int): PaletteIndex = created(PaletteIndex.create(number))

    private companion object {
        const val MAX_ENTRIES: Int = PaletteLimits.MAX_ENTRY_COUNT
        const val U8_MASK: Int = 0xff
    }
}

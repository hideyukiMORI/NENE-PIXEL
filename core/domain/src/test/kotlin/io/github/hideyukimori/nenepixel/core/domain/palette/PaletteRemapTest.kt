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

internal class PaletteRemapTest {
    @Test
    fun `complete maps accept two and 256 entries and retain exact definitions`() {
        listOf(2, 256).forEach { count ->
            val source = definition(count, 0)
            val target = definition(count, count - 1)
            val entries = List(count) { index(count - 1 - it) }
            val remap = created(PaletteRemap.create(source, target, entries))
            assertSame(source, remap.source)
            assertSame(target, remap.target)
            assertEquals(entries, remap.destinations())
            assertEquals(index(count - 1), created(remap.destinationAt(PaletteIndex.first)))
        }
    }

    @Test
    fun `construction and reads do not expose owned mutable list`() {
        val definition = definition(3, 1)
        val input = mutableListOf(index(2), index(0), index(0))
        val remap = created(PaletteRemap.create(definition, definition, input))
        input.clear()
        val returned = remap.destinations() as MutableList<PaletteIndex>
        returned.clear()
        assertEquals(listOf(index(2), index(0), index(0)), remap.destinations())
    }

    @Test
    fun `missing and excess entries reject before touching caller elements`() {
        val definition = definition(2, 0)
        listOf(0, 1, 3, Int.MAX_VALUE).forEach { count ->
            val unreadable =
                object : AbstractList<PaletteIndex>() {
                    override val size: Int = count

                    override fun get(index: Int): PaletteIndex = error("Oversized or incomplete input must not be read")
                }
            val failure = rejected(PaletteRemap.create(definition, definition, unreadable))
            val mismatch = assertInstanceOf(DomainValueRejection.PaletteRemapSizeMismatch::class.java, failure)
            assertEquals(2, mismatch.expectedCount)
            assertEquals(count, mismatch.attemptedCount)
        }
    }

    @Test
    fun `destination and source lookup indices are checked against their own palette`() {
        val source = definition(3, 0)
        val target = definition(2, 1)
        val failure = rejected(PaletteRemap.create(source, target, listOf(index(0), index(1), index(2))))
        val outside = assertInstanceOf(DomainValueRejection.PaletteRemapDestinationOutsidePalette::class.java, failure)
        assertEquals(index(2), outside.sourceIndex)
        assertEquals(index(2), outside.destinationIndex)
        assertEquals(2, outside.targetEntryCount)
        val remap = created(PaletteRemap.create(source, target, listOf(index(0), index(1), index(1))))
        assertInstanceOf(
            DomainValueRejection.PaletteIndexOutsidePalette::class.java,
            rejected(remap.destinationAt(index(3))),
        )
    }

    @Test
    fun `equality includes both defaults and mapping even with duplicate RGBA`() {
        val source = definition(2, 0)
        val target = definition(2, 1)
        val identity = listOf(index(0), index(1))
        val remap = created(PaletteRemap.create(source, target, identity))
        val equal = created(PaletteRemap.create(definition(2, 0), definition(2, 1), identity))
        assertEquals(remap, equal)
        assertEquals(remap.hashCode(), equal.hashCode())
        assertNotEquals(remap, created(PaletteRemap.create(source, target, listOf(index(1), index(0)))))
        assertNotEquals(remap, created(PaletteRemap.create(target, target, identity)))
        assertNotEquals(remap, created(PaletteRemap.create(source, source, identity)))
    }

    private fun definition(
        count: Int,
        default: Int,
    ): PaletteDefinition =
        created(PaletteDefinition.create(created(Palette.create(List(count) { PixelColor.blank })), index(default)))

    private fun index(number: Int): PaletteIndex = created(PaletteIndex.create(number))
}

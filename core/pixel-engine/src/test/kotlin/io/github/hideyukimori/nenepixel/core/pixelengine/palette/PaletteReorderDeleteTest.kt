package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.definition
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.index
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.indices
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.palette
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.planned
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.rejection
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class PaletteReorderDeleteTest {
    private val source = palette(0, 0xff0000ff, 0x00ff00ff, 0x0000ffff, default = 2)

    @Test
    fun `reorder translates new-order old indices into old-to-new mapping and preserves color`() {
        val remap = planned(PaletteRemapPlanner.reorder(source, indices(2, 0, 3, 1)))
        assertEquals(indices(1, 3, 0, 2), remap.destinations())
        assertEquals(0, remap.target.defaultIndex.value)
        source.palette.entries().forEach { old ->
            val destination = value(remap.destinationAt(old.index))
            assertEquals(old.color, value(remap.target.palette.entryAt(destination)).color)
        }
    }

    @Test
    fun `maximum reverse permutation keeps all 256 colors and remaps default`() {
        val definition = definition(List(256) { PixelColor.fromPackedRgba8888(it shl 24) }, default = 127)
        val order = (255 downTo 0).map(::index).toMutableList()
        val remap = planned(PaletteRemapPlanner.reorder(definition, order))
        order.clear()
        assertEquals(128, remap.target.defaultIndex.value)
        assertEquals((255 downTo 0).toList(), remap.destinations().map { it.value })
        assertEquals(
            definition.palette
                .entries()
                .map { it.color }
                .reversed(),
            remap.target.palette
                .entries()
                .map { it.color },
        )
    }

    @Test
    fun `permutation size is rejected before element access`() {
        val unreadable =
            object : AbstractList<PaletteIndex>() {
                override val size: Int = Int.MAX_VALUE

                override fun get(index: Int): PaletteIndex = error("Over-limit order must not be read")
            }
        assertInstanceOf(
            PaletteRemapRejection.OrderSizeMismatch::class.java,
            rejection(PaletteRemapPlanner.reorder(source, unreadable)),
        )
    }

    @Test
    fun `reorder rejects omitted repeated and out-of-range old indices`() {
        assertInstanceOf(
            PaletteRemapRejection.OrderSizeMismatch::class.java,
            rejection(PaletteRemapPlanner.reorder(source, indices(0, 1, 2))),
        )
        assertInstanceOf(
            PaletteRemapRejection.RepeatedOrderIndex::class.java,
            rejection(PaletteRemapPlanner.reorder(source, indices(0, 1, 1, 3))),
        )
        assertInstanceOf(
            PaletteRemapRejection.OrderIndexOutsidePalette::class.java,
            rejection(PaletteRemapPlanner.reorder(source, indices(0, 1, 2, 4))),
        )
    }

    @Test
    fun `deletion uses the old default survivor then compacts all higher positions`() {
        val remap = planned(PaletteRemapPlanner.remove(source, index(1)))
        assertEquals(indices(0, 1, 1, 2), remap.destinations())
        assertEquals(1, remap.target.defaultIndex.value)
        assertEquals(3, remap.target.palette.entryCount)
        assertEquals(
            source.palette
                .entries()
                .filter { it.index != index(1) }
                .map { it.color },
            remap.target.palette
                .entries()
                .map { it.color },
        )
    }

    @Test
    fun `deleting default requires explicit surviving old slot and remaps it`() {
        assertEquals(
            PaletteRemapRejection.ReplacementIsRemovedIndex,
            rejection(PaletteRemapPlanner.remove(source, index(2))),
        )
        val remap = planned(PaletteRemapPlanner.remove(source, index(2), index(3)))
        assertEquals(indices(0, 1, 2, 2), remap.destinations())
        assertEquals(2, remap.target.defaultIndex.value)
    }

    @Test
    fun `deleting the last slot can use transparent first slot`() {
        val remap = planned(PaletteRemapPlanner.remove(source, index(3), index(0)))
        assertEquals(indices(0, 1, 2, 0), remap.destinations())
        assertEquals(PixelColor.blank, value(remap.target.palette.entryAt(index(0))).color)
        assertEquals(2, remap.target.defaultIndex.value)
    }

    @Test
    fun `delete rejects invalid source targets and fewer than two colors`() {
        assertInstanceOf(
            PaletteRemapRejection.RemovedIndexOutsidePalette::class.java,
            rejection(PaletteRemapPlanner.remove(source, index(4))),
        )
        assertInstanceOf(
            PaletteRemapRejection.ReplacementIndexOutsidePalette::class.java,
            rejection(PaletteRemapPlanner.remove(source, index(0), index(4))),
        )
        assertEquals(
            PaletteRemapRejection.BelowDefinitionMinimum,
            rejection(PaletteRemapPlanner.remove(palette(0, 1), index(1))),
        )
    }
}

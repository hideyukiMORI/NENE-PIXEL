package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.definition
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.indices
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.palette
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.planned
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.random.Random

internal class PaletteNearestTest {
    @Test
    fun exactHiddenRgbWinsBeforeTransparentDistanceAndDuplicateTies() {
        val source = palette(0x12345600, 0x65432100)
        val target = palette(0xabcdef00, 0x12345600, 0x12345600)
        val remap = planned(PaletteRemapPlanner.nearest(source, target))
        assertEquals(indices(1, 0), remap.destinations())
        assertEquals(3, remap.target.palette.entryCount)
    }

    @Test
    fun equidistantOpaqueTargetsChooseTheirFirstSlot() {
        val source = palette(0x010000ff, 0x010000ff)
        val forward = palette(0x000000ff, 0x020000ff)
        val reverse = palette(0x020000ff, 0x000000ff)
        assertEquals(indices(0, 0), planned(PaletteRemapPlanner.nearest(source, forward)).destinations())
        assertEquals(indices(0, 0), planned(PaletteRemapPlanner.nearest(source, reverse)).destinations())
    }

    @Test
    fun alphaAndColorSquaresDoNotOverflowInt() {
        val source = palette(0xffffffff, 0xffffffff)
        val target = palette(0xffffff00, 0x000000ff)
        assertEquals(indices(1, 1), planned(PaletteRemapPlanner.nearest(source, target)).destinations())
        val partial = palette(0xff000080, 0xff000080)
        val alternatives = palette(0xfe000080, 0xff0000ff)
        assertEquals(indices(0, 0), planned(PaletteRemapPlanner.nearest(partial, alternatives)).destinations())
    }

    @Test
    fun fullMappingsMatchAnArbitraryPrecisionReference() {
        val random = Random(111)
        for (count in listOf(2, 17, 256)) {
            val sources = List(count) { PixelColor.fromPackedRgba8888(random.nextInt()) }
            val targets = List(count) { PixelColor.fromPackedRgba8888(random.nextInt()) }
            val source = definition(sources, count - 1)
            val target = definition(targets, count - 1)
            val result = planned(PaletteRemapPlanner.nearest(source, target))
            val expected = sources.map { reference(it, targets) }
            assertEquals(expected, result.destinations().map { it.value })
            assertEquals(result, planned(PaletteRemapPlanner.nearest(source, target)))
            assertEquals(target.defaultIndex, result.target.defaultIndex)
        }
    }

    private fun reference(
        color: PixelColor,
        targets: List<PixelColor>,
    ): Int {
        val exact = targets.indexOf(color)
        return if (exact >= 0) exact else targets.indices.minBy { distance(color, targets[it]) }
    }

    private fun distance(
        left: PixelColor,
        right: PixelColor,
    ): BigInteger {
        val differences = vector(left).zip(vector(right)) { a, b -> a.subtract(b) }
        return differences.fold(BigInteger.ZERO) { sum, difference -> sum.add(difference.pow(2)) }
    }

    private fun vector(color: PixelColor): List<BigInteger> {
        val alpha = BigInteger.valueOf(color.alpha.value.toLong())
        return listOf(color.red.value.toInt(), color.green.value.toInt(), color.blue.value.toInt(), 255, 255, 255)
            .map { BigInteger.valueOf(it.toLong()).multiply(alpha) }
    }
}

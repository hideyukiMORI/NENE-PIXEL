package io.github.hideyukimori.nenepixel.core.domain.drawing

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.canvasSize
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.color
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.pixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class StrokeTest {
    @Test
    fun `stroke rejects an empty path and the first position outside its target canvas`() {
        val canvas = canvasSize(2, 2)

        assertEquals(
            DomainValueRejection.EmptyStrokePath,
            rejected(Stroke.create(canvas, emptyList(), BLACK)),
        )
        val rejection =
            rejected(
                Stroke.create(
                    canvas,
                    listOf(pixelPosition(0, 0), pixelPosition(2, 0), pixelPosition(0, 2)),
                    BLACK,
                ),
            )
        val outside = assertInstanceOf(DomainValueRejection.PixelPositionOutsideCanvas::class.java, rejection)
        assertEquals(canvas, outside.canvas)
        assertEquals(pixelPosition(2, 0), outside.position)
    }

    @Test
    fun `outside first extreme path short circuits before defensive copy`() {
        val canvas = canvasSize(1, 1)
        val outsidePosition = pixelPosition(1, 0)
        val accessedIndices = mutableListOf<Int>()
        val path =
            object : AbstractList<PixelPosition>() {
                override val size: Int = Int.MAX_VALUE

                override fun get(index: Int): PixelPosition {
                    accessedIndices.add(index)
                    return if (index == 0) outsidePosition else error("Extreme path read index $index.")
                }
            }

        val rejection = rejected(Stroke.create(canvas, path, BLACK))
        val outside = assertInstanceOf(DomainValueRejection.PixelPositionOutsideCanvas::class.java, rejection)

        assertEquals(listOf(0), accessedIndices)
        assertEquals(canvas, outside.canvas)
        assertEquals(outsidePosition, outside.position)
    }

    @Test
    fun `stroke defensively owns one ordered path and has value equality`() {
        val canvas = canvasSize(2, 2)
        val input = mutableListOf(pixelPosition(1, 1), pixelPosition(0, 0), pixelPosition(1, 1))
        val stroke = created(Stroke.create(canvas, input, RED))
        val equal = created(Stroke.create(canvas, input.toList(), RED))

        input.clear()

        assertEquals(listOf(pixelPosition(1, 1), pixelPosition(0, 0), pixelPosition(1, 1)), stroke.positions())
        assertEquals(3, stroke.positionCount)
        assertEquals(RED, stroke.color)
        assertEquals(equal, stroke)
        assertEquals(equal.hashCode(), stroke.hashCode())
        assertNotEquals(
            stroke,
            created(Stroke.create(canvas, listOf(pixelPosition(0, 0), pixelPosition(1, 1)), RED)),
        )
        assertNotEquals(
            stroke,
            created(Stroke.create(canvasSize(3, 2), stroke.positions(), RED)),
        )
        assertNotEquals(stroke, created(Stroke.create(canvas, stroke.positions(), BLACK)))
    }

    private fun Stroke.positions(): List<PixelPosition> = buildList { forEachPosition(::add) }

    private companion object {
        val BLACK = color(0, 0, 0, 255)
        val RED = color(255, 0, 0, 255)
    }
}

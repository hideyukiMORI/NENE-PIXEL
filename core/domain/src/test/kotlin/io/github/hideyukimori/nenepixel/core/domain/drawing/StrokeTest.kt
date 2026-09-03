package io.github.hideyukimori.nenepixel.core.domain.drawing

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.canvasSize
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.color
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.pixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
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
            rejected(Stroke.create(canvas, emptyList(), paint(BLACK))),
        )
        val rejection =
            rejected(
                Stroke.create(
                    canvas,
                    listOf(pixelPosition(0, 0), pixelPosition(2, 0), pixelPosition(0, 2)),
                    paint(BLACK),
                ),
            )
        val outside = assertInstanceOf(DomainValueRejection.PixelPositionOutsideCanvas::class.java, rejection)
        assertEquals(canvas, outside.canvas)
        assertEquals(pixelPosition(2, 0), outside.position)
    }

    @Test
    fun `raw path cap plus one rejects before containment or defensive copy`() {
        val canvas = canvasSize(1, 1)
        val accessedIndices = mutableListOf<Int>()
        val path =
            object : AbstractList<PixelPosition>() {
                override val size: Int = PixelLimits.MAX_RAW_STROKE_POSITIONS + 1

                override fun get(index: Int): PixelPosition {
                    accessedIndices.add(index)
                    error("Oversized path read index $index.")
                }
            }

        val rejection = rejected(Stroke.create(canvas, path, paint(BLACK)))

        assertEquals(emptyList<Int>(), accessedIndices)
        assertEquals(
            DomainValueRejection.StrokePathAboveSupportedMaximum(
                PixelLimits.MAX_RAW_STROKE_POSITIONS + 1,
                PixelLimits.MAX_RAW_STROKE_POSITIONS,
            ),
            rejection,
        )
    }

    @Test
    fun `raw path cap minus one and cap are accepted`() {
        val canvas = canvasSize(1, 1)
        val position = pixelPosition(0, 0)

        assertEquals(
            PixelLimits.MAX_RAW_STROKE_POSITIONS - 1,
            created(Stroke.create(canvas, List(PixelLimits.MAX_RAW_STROKE_POSITIONS - 1) { position }, paint(BLACK)))
                .positionCount,
        )
        assertEquals(
            PixelLimits.MAX_RAW_STROKE_POSITIONS,
            created(Stroke.create(canvas, List(PixelLimits.MAX_RAW_STROKE_POSITIONS) { position }, paint(BLACK)))
                .positionCount,
        )
    }

    @Test
    fun `stroke defensively owns one ordered path and has value equality`() {
        val canvas = canvasSize(2, 2)
        val input = mutableListOf(pixelPosition(1, 1), pixelPosition(0, 0), pixelPosition(1, 1))
        val stroke = created(Stroke.create(canvas, input, paint(RED)))
        val equal = created(Stroke.create(canvas, input.toList(), paint(RED)))

        input.clear()

        assertEquals(listOf(pixelPosition(1, 1), pixelPosition(0, 0), pixelPosition(1, 1)), stroke.positions())
        assertEquals(listOf(3, 0, 3), List(stroke.positionCount, stroke::rowMajorIndexAt))
        assertEquals(3, stroke.positionCount)
        assertEquals(paint(RED), stroke.effect)
        assertEquals(equal, stroke)
        assertEquals(equal.hashCode(), stroke.hashCode())
        assertNotEquals(
            stroke,
            created(Stroke.create(canvas, listOf(pixelPosition(0, 0), pixelPosition(1, 1)), paint(RED))),
        )
        assertNotEquals(
            stroke,
            created(Stroke.create(canvasSize(3, 2), stroke.positions(), paint(RED))),
        )
        assertNotEquals(stroke, created(Stroke.create(canvas, stroke.positions(), paint(BLACK))))
        assertNotEquals(stroke, created(Stroke.create(canvas, stroke.positions(), StrokeEffect.Erase)))
    }

    private fun Stroke.positions(): List<PixelPosition> = buildList { forEachPosition(::add) }

    private fun paint(color: io.github.hideyukimori.nenepixel.core.domain.color.PixelColor): StrokeEffect =
        StrokeEffect.Paint(color)

    private companion object {
        val BLACK = color(0, 0, 0, 255)
        val RED = color(255, 0, 0, 255)
    }
}

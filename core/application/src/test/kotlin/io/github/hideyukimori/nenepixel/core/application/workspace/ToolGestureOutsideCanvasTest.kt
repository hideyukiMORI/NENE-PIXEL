package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.DocumentPoint
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ToolGestureOutsideCanvasTest {
    @Test
    fun `exit outside travel and reentry follow the actual path without a border line`() {
        val started = begin(position(2, 1))
        val exited = extend(started, DocumentPoint.create(5, 1))
        val travelled = extend(exited, DocumentPoint.create(5, 3))
        val reentered = extend(travelled, DocumentPoint.create(2, 3))
        val expected = listOf(position(2, 1), position(3, 1), position(3, 3), position(2, 3))

        assertEquals(expected, reentered.positions())
        assertEquals(9, reentered.positionCount)
        assertEquals(expected, buildList { reentered.prepareStroke().forEachPosition(::add) })
        assertEquals(listOf(position(2, 1)), started.positions())
        assertEquals(listOf(position(2, 1), position(3, 1)), exited.positions())
    }

    @Test
    fun `ending outside emits only the contained traversal through the edge`() {
        val gesture = extend(begin(position(1, 1)), DocumentPoint.create(-3, 1))

        assertEquals(listOf(position(1, 1), position(0, 1)), gesture.positions())
        assertEquals(5, gesture.positionCount)
        assertEquals(gesture.positions().size, gesture.prepareStroke().positionCount)
    }

    @Test
    fun `signed half ties retain canonical nearest rounding in both directions`() {
        val positiveDelta = extend(begin(position(2, 2)), DocumentPoint.create(-2, 0))
        val negativeDelta = extend(begin(position(1, 0)), DocumentPoint.create(-1, 4))

        assertEquals(listOf(position(2, 2), position(1, 2), position(0, 1)), positiveDelta.positions())
        assertEquals(listOf(position(1, 0), position(0, 1), position(0, 2)), negativeDelta.positions())
    }

    @Test
    fun `segments with negative endpoints emit the identical contained path in reverse`() {
        repeat(4) { y ->
            repeat(4) { x ->
                for (outsideY in -4..4) {
                    for (outsideX in -4 until 0) {
                        val started = begin(position(x, y))
                        val outward = extend(started, DocumentPoint.create(outsideX, outsideY))
                        val returned = extend(outward, DocumentPoint.create(x, y))
                        val inward = returned.positions().drop(outward.positions().size)

                        assertEquals(outward.positions(), inward.reversed())
                        assertTrue(returned.positions().all(returned.canvas::contains))
                    }
                }
            }
        }
    }

    @Test
    fun `outside cells reach the exact work limit and overflow rejection retains the chain`() {
        val started = begin(position(0, 0))
        val almost = extend(started, DocumentPoint.create(PixelLimits.MAX_RAW_STROKE_POSITIONS - 2, 0))
        val full = extend(almost, DocumentPoint.create(PixelLimits.MAX_RAW_STROKE_POSITIONS - 1, 0))
        val rejected =
            assertInstanceOf(
                ToolGestureExtensionResult.AboveSupportedMaximum::class.java,
                full.extend(DocumentPoint.create(PixelLimits.MAX_RAW_STROKE_POSITIONS, 0)),
            )

        assertEquals(PixelLimits.MAX_RAW_STROKE_POSITIONS - 1, almost.positionCount)
        assertEquals(PixelLimits.MAX_RAW_STROKE_POSITIONS, full.positionCount)
        assertEquals(PixelLimits.MAX_RAW_STROKE_POSITIONS.toLong() + 1, rejected.attemptedCount)
        assertEquals(ToolGestureExtensionResult.Duplicate, full.extend(full.lastPosition))
        assertEquals(List(4) { position(it, 0) }, full.positions())
        assertEquals(4, full.prepareStroke().positionCount)
        assertEquals(1, started.positionCount)
    }

    @Test
    fun `wide accepted interpolation multiplies in Long and preserves visible cells`() {
        val gesture = extend(begin(position(0, 0)), DocumentPoint.create(262_143, 131_071))

        assertEquals(PixelLimits.MAX_RAW_STROKE_POSITIONS, gesture.positionCount)
        assertEquals(listOf(position(0, 0), position(1, 0), position(2, 1), position(3, 1)), gesture.positions())
    }

    @Test
    fun `extreme signed Int endpoints reject with exact Long counts before enumeration`() {
        val started = begin(position(1, 1))
        val endpoints = listOf(Int.MIN_VALUE, Int.MAX_VALUE)
        val expectedCounts = listOf(2_147_483_650L, 2_147_483_647L)
        endpoints.zip(expectedCounts).forEach { (coordinate, count) ->
            val rejected =
                assertInstanceOf(
                    ToolGestureExtensionResult.AboveSupportedMaximum::class.java,
                    started.extend(DocumentPoint.create(coordinate, coordinate)),
                )
            assertEquals(count, rejected.attemptedCount)
        }
        assertEquals(listOf(position(1, 1)), started.positions())
        assertEquals(1, started.positionCount)
    }

    private fun begin(start: PixelPosition): ToolGesture {
        val canvas = canvas(4, 4)
        return ToolGesture.begin(
            ToolGestureAdmission(
                canvas,
                StrokeEffect.Paint(redIndex),
                LayerId.first(),
                CommandGateway.create(state(canvas)).captureSource(),
            ),
            start,
        )
    }

    private fun extend(
        gesture: ToolGesture,
        point: DocumentPoint,
    ): ToolGesture = assertInstanceOf(ToolGestureExtensionResult.Extended::class.java, gesture.extend(point)).gesture

    private fun ToolGesture.positions(): List<PixelPosition> = buildList { forEachPosition(::add) }
}

package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.max

internal class ToolGestureInterpolationTest {
    @Test
    fun `every bounded endpoint pair is deterministic connected and direction symmetric`() {
        val canvas = canvas(PROPERTY_EDGE, PROPERTY_EDGE)
        repeat(PROPERTY_EDGE) { startY ->
            repeat(PROPERTY_EDGE) { startX ->
                repeat(PROPERTY_EDGE) { endY ->
                    repeat(PROPERTY_EDGE) { endX ->
                        val start = position(startX, startY)
                        val end = position(endX, endY)
                        val forward = gesturePositions(canvas, start, end)
                        val replay = gesturePositions(canvas, start, end)
                        val reverse = gesturePositions(canvas, end, start)

                        assertEquals(start, forward.first())
                        assertEquals(end, forward.last())
                        assertEquals(segmentLength(start, end) + 1, forward.size)
                        assertEquals(forward, replay)
                        assertEquals(forward, reverse.reversed())
                        forward.zipWithNext().forEach { (prior, next) ->
                            assertEquals(1, segmentLength(prior, next))
                        }
                        assertTrue(forward.all(canvas::contains))
                    }
                }
            }
        }
    }

    @Test
    fun `revisited segment preserves ordered overlap and prepares the identical stroke path`() {
        val canvas = canvas(6, 3)
        val started = ToolGesture.begin(canvas, position(0, 0), StrokeEffect.Paint(red))
        val outward = extended(started, position(5, 2))
        val returned = extended(outward, position(0, 0))
        val positions = returned.positions()

        assertEquals(11, returned.positionCount)
        assertEquals(position(0, 0), positions.first())
        assertEquals(position(5, 2), positions[5])
        assertEquals(position(0, 0), positions.last())
        assertEquals(positions, returned.prepareStroke().positions())
    }

    private fun gesturePositions(
        canvas: io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize,
        start: PixelPosition,
        end: PixelPosition,
    ): List<PixelPosition> {
        val gesture = ToolGesture.begin(canvas, start, StrokeEffect.Paint(red))
        return if (start == end) gesture.positions() else extended(gesture, end).positions()
    }

    private fun extended(
        gesture: ToolGesture,
        position: PixelPosition,
    ): ToolGesture =
        when (val result = gesture.extend(position)) {
            is ToolGestureExtensionResult.Extended -> result.gesture
            ToolGestureExtensionResult.Duplicate -> error("Distinct property endpoint was treated as duplicate.")
            is ToolGestureExtensionResult.AboveSupportedMaximum -> error("Bounded property path exceeded the limit.")
        }

    private fun segmentLength(
        start: PixelPosition,
        end: PixelPosition,
    ): Int = max(abs(end.x.value - start.x.value), abs(end.y.value - start.y.value))

    private fun ToolGesture.positions(): List<PixelPosition> = buildList { forEachPosition(::add) }

    private fun io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke.positions(): List<PixelPosition> =
        buildList { forEachPosition(::add) }

    private companion object {
        const val PROPERTY_EDGE: Int = 8
    }
}

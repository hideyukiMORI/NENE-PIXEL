package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

internal class QuickSelectFanLayoutTest {
    @Test
    fun `a single item sits in the middle of the quarter for both corners`() {
        val half = SINGLE * sqrt(2.0) / 2
        val right = layout().itemCenters(CONTROL, 1, EditorControlEdge.Right).single()
        assertPoint(CONTROL.x - half, CONTROL.y - half, right)
        val left = layout().itemCenters(CONTROL, 1, EditorControlEdge.Left).single()
        assertPoint(CONTROL.x + half, CONTROL.y - half, left)
    }

    @Test
    fun `four items share one ring from left to up with equal chords`() {
        val centers = layout().itemCenters(CONTROL, 4, EditorControlEdge.Right)
        assertEquals(4, centers.size)
        assertPoint(CONTROL.x - SINGLE, CONTROL.y.toDouble(), centers.first())
        assertPoint(CONTROL.x.toDouble(), CONTROL.y - SINGLE, centers.last())
        val chord = 2 * SINGLE * sin(PI / 12)
        centers.zipWithNext().forEach { (first, second) -> assertEquals(chord, distance(first, second), TOLERANCE) }
        centers.forEach { center -> assertEquals(SINGLE, distance(CONTROL, center), TOLERANCE) }
    }

    @Test
    fun `five items put the first four on the inner ring and the fifth in the middle of the outer ring`() {
        val centers = layout().itemCenters(CONTROL, 5, EditorControlEdge.Right)
        centers.take(4).forEach { center -> assertEquals(INNER, distance(CONTROL, center), TOLERANCE) }
        val half = OUTER * sqrt(2.0) / 2
        assertPoint(CONTROL.x - half, CONTROL.y - half, centers[4])
    }

    @Test
    fun `nine items put four on the inner ring and five across the whole outer quarter`() {
        val centers = layout().itemCenters(CONTROL, 9, EditorControlEdge.Right)
        centers.take(4).forEach { center -> assertEquals(INNER, distance(CONTROL, center), TOLERANCE) }
        centers.drop(4).forEach { center -> assertEquals(OUTER, distance(CONTROL, center), TOLERANCE) }
        assertPoint(CONTROL.x - INNER, CONTROL.y.toDouble(), centers[0])
        assertPoint(CONTROL.x.toDouble(), CONTROL.y - INNER, centers[3])
        assertPoint(CONTROL.x - OUTER, CONTROL.y.toDouble(), centers[4])
        assertPoint(CONTROL.x.toDouble(), CONTROL.y - OUTER, centers[8])
    }

    @Test
    fun `the left corner mirrors the right corner and runs from up to right`() {
        (1..9).forEach { count ->
            val right = layout().itemCenters(CONTROL, count, EditorControlEdge.Right)
            val left = layout().itemCenters(CONTROL, count, EditorControlEdge.Left)
            ringsOf(count).forEach { ring ->
                ring.forEachIndexed { position, index ->
                    val mirrored = right[ring[ring.size - 1 - position]]
                    assertPoint(2.0 * CONTROL.x - mirrored.x, mirrored.y.toDouble(), left[index])
                }
            }
        }
        val left = layout().itemCenters(CONTROL, 4, EditorControlEdge.Left)
        assertPoint(CONTROL.x.toDouble(), CONTROL.y - SINGLE, left.first())
        assertPoint(CONTROL.x + SINGLE, CONTROL.y.toDouble(), left.last())
    }

    @Test
    fun `a ring beyond the maximum radius only shrinks its radius`() {
        val limit = 100.0 * DENSITY
        val nine = layout(maxRadius = limit).itemCenters(CONTROL, 9, EditorControlEdge.Right)
        nine.take(4).forEach { center -> assertEquals(INNER, distance(CONTROL, center), TOLERANCE) }
        nine.drop(4).forEach { center -> assertEquals(limit, distance(CONTROL, center), TOLERANCE) }
        assertPoint(CONTROL.x - limit, CONTROL.y.toDouble(), nine[4])
        assertPoint(CONTROL.x.toDouble(), CONTROL.y - limit, nine[8])
        val small = 50.0 * DENSITY
        val four = layout(maxRadius = small).itemCenters(CONTROL, 4, EditorControlEdge.Right)
        four.forEach { center -> assertEquals(small, distance(CONTROL, center), TOLERANCE) }
        assertPoint(CONTROL.x - small, CONTROL.y.toDouble(), four.first())
    }

    @Test
    fun `hit testing returns null on the control, the nearest item within reach, or null elsewhere`() {
        val fan = layout()
        val centers = fan.itemCenters(CONTROL, 4, EditorControlEdge.Right)
        val nearControl = QuickSelectFanPoint(CONTROL.x - 10f * DENSITY.toFloat(), CONTROL.y)
        assertNull(fan.hitTest(nearControl, centers, CONTROL))
        val nearItem = QuickSelectFanPoint(centers[2].x + 20f * DENSITY.toFloat(), centers[2].y)
        assertEquals(2, fan.hitTest(nearItem, centers, CONTROL))
        val away = QuickSelectFanPoint(CONTROL.x - 300f * DENSITY.toFloat(), CONTROL.y - 300f * DENSITY.toFloat())
        assertNull(fan.hitTest(away, centers, CONTROL))
    }

    private fun ringsOf(count: Int): List<List<Int>> =
        if (count <= QuickSelectFanLayout.SINGLE_RING_CAPACITY) {
            listOf((0 until count).toList())
        } else {
            listOf((0 until 4).toList(), (4 until count).toList())
        }

    private fun layout(maxRadius: Double = 1_000.0 * DENSITY): QuickSelectFanLayout =
        QuickSelectFanLayout(
            singleRingRadius = SINGLE.toFloat(),
            innerRingRadius = INNER.toFloat(),
            outerRingRadius = OUTER.toFloat(),
            itemHitRadius = (28.0 * DENSITY).toFloat(),
            controlHitRadius = (28.0 * DENSITY).toFloat(),
            maxRadius = maxRadius.toFloat(),
        )

    private fun assertPoint(
        x: Double,
        y: Double,
        actual: QuickSelectFanPoint,
    ) {
        assertEquals(x, actual.x.toDouble(), TOLERANCE)
        assertEquals(y, actual.y.toDouble(), TOLERANCE)
    }

    private fun distance(
        first: QuickSelectFanPoint,
        second: QuickSelectFanPoint,
    ): Double = hypot((first.x - second.x).toDouble(), (first.y - second.y).toDouble())

    private companion object {
        const val DENSITY = 2.5
        const val SINGLE = 96.0 * DENSITY
        const val INNER = 88.0 * DENSITY
        const val OUTER = 144.0 * DENSITY
        const val TOLERANCE = 1e-3
        val CONTROL = QuickSelectFanPoint(x = 1_000f, y = 2_000f)
    }
}

package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure geometry of the quick-select fan (ADR 0029, #108 UI spec "扇（fan）").
 *
 * Every length is in pixels; the caller converts dp to px. Angles are screen angles (y grows downward):
 * a control in the bottom-right corner fans from 180° (left) to 270° (up), a control in the bottom-left
 * corner from 270° (up) to 360° (right). Up to [SINGLE_RING_CAPACITY] items share one ring; more items put
 * the first [SINGLE_RING_CAPACITY] on the inner ring and the rest on the outer ring. A ring larger than
 * [maxRadius] only shrinks its radius; angles never change.
 */
internal data class QuickSelectFanLayout(
    val singleRingRadius: Float,
    val innerRingRadius: Float,
    val outerRingRadius: Float,
    val itemHitRadius: Float,
    val controlHitRadius: Float,
    val maxRadius: Float,
) {
    init {
        require(maxRadius > 0f) { "The fan needs a positive maximum radius." }
    }

    /** Item centres in input order for [count] items around [control] in the [edge] corner. */
    fun itemCenters(
        control: QuickSelectFanPoint,
        count: Int,
        edge: EditorControlEdge,
    ): List<QuickSelectFanPoint> {
        require(count in 1..MAX_ITEMS) { "The fan holds 1 to $MAX_ITEMS items." }
        return if (count <= SINGLE_RING_CAPACITY) {
            ring(control, count, singleRingRadius, edge)
        } else {
            ring(control, SINGLE_RING_CAPACITY, innerRingRadius, edge) +
                ring(control, count - SINGLE_RING_CAPACITY, outerRingRadius, edge)
        }
    }

    /**
     * The index of the item under [pointer], or null when the pointer is on the control or outside every
     * item's hit radius.
     */
    fun hitTest(
        pointer: QuickSelectFanPoint,
        centers: List<QuickSelectFanPoint>,
        control: QuickSelectFanPoint,
    ): Int? {
        val nearest =
            centers.indices
                .minByOrNull { index -> distance(pointer, centers[index]) }
                ?.takeIf { index -> distance(pointer, centers[index]) <= itemHitRadius }
        return nearest.takeUnless { distance(pointer, control) <= controlHitRadius }
    }

    private fun ring(
        control: QuickSelectFanPoint,
        count: Int,
        radius: Float,
        edge: EditorControlEdge,
    ): List<QuickSelectFanPoint> {
        val effectiveRadius = min(radius, maxRadius).toDouble()
        val start =
            when (edge) {
                EditorControlEdge.Right -> RIGHT_CORNER_START_DEGREES
                EditorControlEdge.Left -> LEFT_CORNER_START_DEGREES
            }
        return List(count) { index ->
            val degrees =
                if (count == 1) {
                    start + QUARTER_DEGREES / 2
                } else {
                    start + QUARTER_DEGREES * index / (count - 1)
                }
            val radians = degrees * PI / HALF_TURN_DEGREES
            QuickSelectFanPoint(
                x = (control.x + effectiveRadius * cos(radians)).toFloat(),
                y = (control.y + effectiveRadius * sin(radians)).toFloat(),
            )
        }
    }

    private fun distance(
        first: QuickSelectFanPoint,
        second: QuickSelectFanPoint,
    ): Float = hypot(first.x - second.x, first.y - second.y)

    companion object {
        const val MAX_ITEMS = 9
        const val SINGLE_RING_CAPACITY = 4
        private const val QUARTER_DEGREES = 90.0
        private const val HALF_TURN_DEGREES = 180.0
        private const val RIGHT_CORNER_START_DEGREES = 180.0
        private const val LEFT_CORNER_START_DEGREES = 270.0
    }
}

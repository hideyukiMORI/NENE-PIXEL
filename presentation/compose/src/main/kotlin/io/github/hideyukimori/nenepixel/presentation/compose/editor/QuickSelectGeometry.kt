package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem

/**
 * Where the quick-select control sits in the work area, in pixels (#108 UI spec "Layout").
 *
 * [origin] is the control's top-left corner in work-area coordinates; the fan geometry works in control-local
 * coordinates, so a pointer position reported to the control's own input handler hit-tests without conversion.
 */
internal data class QuickSelectGeometry(
    val origin: IntOffset,
    val controlSize: Int,
    val layout: QuickSelectFanLayout,
    val edge: EditorControlEdge,
) {
    val controlCenter: QuickSelectFanPoint
        get() = QuickSelectFanPoint(controlSize / 2f, controlSize / 2f)

    fun placement(items: List<QuickSelectItem>): QuickSelectFanPlacement =
        QuickSelectFanPlacement(items, layout.itemCenters(controlCenter, items.size, edge), this)

    companion object {
        val CONTROL_SIZE: Dp = 56.dp
        val MARGIN: Dp = 16.dp
        private val SINGLE_RING_RADIUS: Dp = 96.dp
        private val INNER_RING_RADIUS: Dp = 88.dp
        private val OUTER_RING_RADIUS: Dp = 144.dp
        private val HIT_RADIUS: Dp = 28.dp

        /**
         * The control in the bottom corner on the [edge] side of [area], [MARGIN] from both edges; the fan radius
         * shrinks to the work area's shorter side less the margin and the control.
         */
        fun create(
            area: IntSize,
            edge: EditorControlEdge,
            density: Density,
        ): QuickSelectGeometry =
            with(density) {
                val margin = MARGIN.roundToPx()
                val size = CONTROL_SIZE.roundToPx()
                val left =
                    when (edge) {
                        EditorControlEdge.Left -> margin
                        EditorControlEdge.Right -> area.width - margin - size
                    }
                val maxRadius = (minOf(area.width, area.height) - margin - size).coerceAtLeast(1)
                val layout =
                    QuickSelectFanLayout(
                        singleRingRadius = SINGLE_RING_RADIUS.toPx(),
                        innerRingRadius = INNER_RING_RADIUS.toPx(),
                        outerRingRadius = OUTER_RING_RADIUS.toPx(),
                        itemHitRadius = HIT_RADIUS.toPx(),
                        controlHitRadius = HIT_RADIUS.toPx(),
                        maxRadius = maxRadius.toFloat(),
                    )
                QuickSelectGeometry(IntOffset(left, area.height - margin - size), size, layout, edge)
            }
    }
}

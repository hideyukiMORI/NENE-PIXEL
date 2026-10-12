package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTransform
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion

/** Display-only outline at the same pixel boundaries as the grid; PixelCanvas clips it to the work area. */
internal fun DrawScope.drawSelectionOutline(
    region: PixelRegion?,
    transform: ViewportTransform,
) {
    if (region != null) {
        val first = transform.surfaceBounds(region.origin) ?: return
        val lastPosition =
            pixelPosition(
                region.origin.x.value + region.size.width.value - 1,
                region.origin.y.value + region.size.height.value - 1,
            )
        val last = transform.surfaceBounds(lastPosition) ?: return
        val origin = Offset(first.left.toFloat(), first.top.toFloat())
        val extent = Size((last.right - first.left).toFloat(), (last.bottom - first.top).toFloat())
        drawRect(Color.Black, origin, extent, style = Stroke(OUTER_WIDTH.dp.toPx()))
        drawRect(Color.White, origin, extent, style = Stroke(INNER_WIDTH.dp.toPx()))
    }
}

private const val OUTER_WIDTH: Int = 4
private const val INNER_WIDTH: Int = 2

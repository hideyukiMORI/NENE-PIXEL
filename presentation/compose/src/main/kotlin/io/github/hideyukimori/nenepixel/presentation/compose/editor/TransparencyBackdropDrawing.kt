package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

/**
 * Fills [area] with [backdrop] (ADR 0026, Issue #147), origin at the top-left corner of [area], cell
 * size from this scope's density. The caller keeps [backdrop] and [area] across frames, so nothing is
 * allocated here.
 */
internal fun DrawScope.drawTransparencyBackdrop(
    backdrop: TransparencyBackdrop,
    area: RectF,
) {
    val cellPx = TransparencyBackdrop.cellPx(density)
    drawIntoCanvas { canvas -> backdrop.draw(canvas.nativeCanvas, area, cellPx) }
}

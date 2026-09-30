package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayPlacement
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize

/**
 * Draws the reference underlay between the transparency backdrop and the picture (ADR 0032,
 * Issue #170): nothing without an underlay or while it is hidden, otherwise one filtered bitmap at
 * the underlay's opacity, clipped to the document rectangle [destination]. Without an underlay the
 * [cache] lets go of its bitmap.
 */
internal fun DrawScope.drawUnderlay(
    cache: UnderlayBitmapCache,
    underlay: ReferenceUnderlay?,
    destination: RectF,
    canvas: CanvasSize,
) {
    when {
        underlay == null -> cache.release()
        underlay.visibility == UnderlayVisibility.Shown -> drawShownUnderlay(cache, underlay, destination, canvas)
        else -> Unit
    }
}

/**
 * Where the underlay image lands on the surface, the only transform presentation applies to it:
 * `cell = document width / canvas width` (and the same vertically), `left = document left +
 * placement.left * cell`, `right = left + image width * placement.scale * cell`.
 */
internal fun underlayBounds(
    document: UnderlayBounds,
    canvas: CanvasSize,
    image: ReferenceImage,
    placement: UnderlayPlacement,
): UnderlayBounds {
    val cellX = (document.right - document.left).toDouble() / canvas.width.value
    val cellY = (document.bottom - document.top).toDouble() / canvas.height.value
    val left = document.left + placement.left * cellX
    val top = document.top + placement.top * cellY
    return UnderlayBounds(
        left = left.toFloat(),
        top = top.toFloat(),
        right = (left + image.width * placement.scale * cellX).toFloat(),
        bottom = (top + image.height * placement.scale * cellY).toFloat(),
    )
}

private fun DrawScope.drawShownUnderlay(
    cache: UnderlayBitmapCache,
    underlay: ReferenceUnderlay,
    destination: RectF,
    canvas: CanvasSize,
) {
    val bitmap = cache.render(underlay.image)
    cache.place(destination, canvas, underlay)
    cache.paint.alpha = underlay.opacity.alpha
    drawIntoCanvas { target ->
        val native = target.nativeCanvas
        val saved = native.save()
        native.clipRect(destination)
        native.drawBitmap(bitmap, null, cache.shown, cache.paint)
        native.restoreToCount(saved)
    }
}

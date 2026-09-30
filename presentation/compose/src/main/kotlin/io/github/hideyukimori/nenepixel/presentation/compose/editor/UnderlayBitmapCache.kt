package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize

/**
 * The reference underlay's image as one bitmap for the canvas (ADR 0032, Issue #170). It is derived
 * state keyed by the image's identity, never a second owner of the underlay (QLT-016). The paint is
 * the underlay's own bilinear one; the committed picture keeps its nearest-neighbour paint.
 */
internal class UnderlayBitmapCache {
    /** Bilinear paint; its alpha is set to the underlay's opacity on every draw. */
    val paint: Paint =
        Paint().apply {
            isAntiAlias = false
            isDither = false
            isFilterBitmap = true
        }

    /** Reused target rectangle of the shown image, so drawing allocates no `RectF`. */
    val shown: RectF = RectF()

    private val edges = FloatArray(UnderlayBoundsCache.EDGE_COUNT)
    private val bounds = UnderlayBoundsCache()
    private var source: ReferenceImage? = null
    private var rendered: Bitmap? = null

    /**
     * Sets [shown] to where [underlay] lands inside the document rectangle [destination] of [canvas].
     * Unchanged inputs reuse the last result, so a steady frame computes and allocates nothing.
     */
    fun place(
        destination: RectF,
        canvas: CanvasSize,
        underlay: ReferenceUnderlay,
    ) {
        edges[UnderlayBoundsCache.LEFT] = destination.left
        edges[UnderlayBoundsCache.TOP] = destination.top
        edges[UnderlayBoundsCache.RIGHT] = destination.right
        edges[UnderlayBoundsCache.BOTTOM] = destination.bottom
        val placed = bounds.resolve(edges, canvas, underlay.image, underlay.placement)
        shown.set(placed.left, placed.top, placed.right, placed.bottom)
    }

    /** The bitmap of [image]; rebuilt only when the image reference changes. */
    fun render(image: ReferenceImage): Bitmap {
        if (source !== image) {
            source = image
            val argb = image.copyPackedRgba8888()
            for (index in argb.indices) {
                argb[index] = argb[index].rgbaToArgb8888()
            }
            rendered = Bitmap.createBitmap(argb, image.width, image.height, Bitmap.Config.ARGB_8888)
        }
        return requireNotNull(rendered)
    }

    /** Lets go of the bitmap once there is no underlay; the next [render] builds a new one. */
    fun release() {
        source = null
        rendered = null
        bounds.forget()
    }
}

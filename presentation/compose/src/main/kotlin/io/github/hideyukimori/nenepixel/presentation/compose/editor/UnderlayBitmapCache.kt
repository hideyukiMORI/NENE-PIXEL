package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage

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

    private var source: ReferenceImage? = null
    private var rendered: Bitmap? = null

    /** The bitmap of [image]; rebuilt only when the image reference changes. */
    fun render(image: ReferenceImage): Bitmap {
        if (source !== image) {
            source = image
            val argb = image.copyPackedRgba8888()
            argb.indices.forEach { index -> argb[index] = argb[index].rgbaToArgb8888() }
            rendered = Bitmap.createBitmap(argb, image.width, image.height, Bitmap.Config.ARGB_8888)
        }
        return requireNotNull(rendered)
    }

    /** Lets go of the bitmap once there is no underlay; the next [render] builds a new one. */
    fun release() {
        source = null
        rendered = null
    }
}

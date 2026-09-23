package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture

/**
 * One disposable rendering of the running gesture preview (Issue #124), kept beside
 * [CommittedBitmapCache] with the same lifetime. The raster is repainted only when the preview
 * reference or its colour changes, so an unchanged frame costs one bitmap transfer, not one draw
 * call per stroke position.
 */
internal class PreviewBitmapCache {
    private var raster: PreviewRaster? = null
    private var rendered: Bitmap? = null
    private var source: ToolGesture? = null
    private var sourceArgb: Int? = null

    fun render(
        preview: ToolGesture?,
        canvasWidth: Int,
        canvasHeight: Int,
        argb: Int,
    ): Bitmap? =
        if (preview == null) {
            null
        } else {
            val target = rasterFor(canvasWidth, canvasHeight)
            val bitmap = requireNotNull(rendered)
            if (source !== preview || sourceArgb != argb) {
                source = preview
                sourceArgb = argb
                target.paint(preview, argb)
                target.transferChangedRows { pixels, rows ->
                    bitmap.setPixels(
                        pixels,
                        rows.first * canvasWidth,
                        canvasWidth,
                        0,
                        rows.first,
                        canvasWidth,
                        rows.count(),
                    )
                }
            }
            bitmap
        }

    private fun rasterFor(
        canvasWidth: Int,
        canvasHeight: Int,
    ): PreviewRaster {
        val current = raster
        return if (current != null && current.width == canvasWidth && current.height == canvasHeight) {
            current
        } else {
            source = null
            sourceArgb = null
            rendered = transparentMutableBitmap(canvasWidth, canvasHeight)
            PreviewRaster(canvasWidth, canvasHeight).also { raster = it }
        }
    }
}

/**
 * Uses the platform colour-array factory already used by the committed projection and copies it
 * once into a mutable bitmap, so no undeclared KTX dependency is introduced (Issue #124).
 */
private fun transparentMutableBitmap(
    width: Int,
    height: Int,
): Bitmap =
    Bitmap
        .createBitmap(IntArray(width * height), width, height, Bitmap.Config.ARGB_8888)
        .copy(Bitmap.Config.ARGB_8888, true)

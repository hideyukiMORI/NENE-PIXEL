package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import io.github.hideyukimori.nenepixel.core.application.render.CompositePixelTransform
import io.github.hideyukimori.nenepixel.core.application.render.DocumentCompositeImage

internal fun DocumentCompositeImage.toRenderedBitmap(): Bitmap {
    val colors = mapPackedRgba8888(CompositePixelTransform { packedRgba8888 -> packedRgba8888.rgbaToArgb8888() })
    return Bitmap.createBitmap(colors, size.width.value, size.height.value, Bitmap.Config.ARGB_8888)
}

internal fun DocumentCompositeImage.toOpaqueRenderedBitmap(backgroundArgb: Int): Bitmap =
    Bitmap.createBitmap(toOpaqueArgb(backgroundArgb), size.width.value, size.height.value, Bitmap.Config.ARGB_8888)

/** Row-major opaque ARGB of the composite over [backgroundArgb]; the one place the canvas colour is applied. */
internal fun DocumentCompositeImage.toOpaqueArgb(backgroundArgb: Int): IntArray =
    mapPackedRgba8888(OpaqueCompositeColor(backgroundArgb))

private fun Int.rgbaToArgb8888(): Int = ((this and CHANNEL_MASK) shl ALPHA_SHIFT) or (this ushr CHANNEL_SHIFT)

/** Packed RGBA8888 composited over the opaque [backgroundArgb], as opaque ARGB8888. */
internal fun Int.rgbaOverOpaqueArgb(backgroundArgb: Int): Int {
    val sourceAlpha = this and CHANNEL_MASK
    val red = compositeChannel(this ushr RED_SHIFT, backgroundArgb ushr ARGB_RED_SHIFT, sourceAlpha)
    val green = compositeChannel(this ushr GREEN_SHIFT, backgroundArgb ushr ARGB_GREEN_SHIFT, sourceAlpha)
    val blue = compositeChannel(this ushr BLUE_SHIFT, backgroundArgb, sourceAlpha)
    return (CHANNEL_MASK shl ALPHA_SHIFT) or (red shl ARGB_RED_SHIFT) or (green shl ARGB_GREEN_SHIFT) or blue
}

private fun compositeChannel(
    source: Int,
    background: Int,
    sourceAlpha: Int,
): Int {
    val sourceChannel = source and CHANNEL_MASK
    val backgroundChannel = background and CHANNEL_MASK
    return (
        sourceChannel * sourceAlpha +
            backgroundChannel * (CHANNEL_MASK - sourceAlpha) +
            COMPOSITE_ROUNDING_BIAS
    ) / CHANNEL_MASK
}

private const val ALPHA_SHIFT: Int = 24
private const val RED_SHIFT: Int = 24
private const val GREEN_SHIFT: Int = 16
private const val BLUE_SHIFT: Int = 8
private const val ARGB_RED_SHIFT: Int = 16
private const val ARGB_GREEN_SHIFT: Int = 8
private const val CHANNEL_SHIFT: Int = 8
private const val CHANNEL_MASK: Int = 0xff
private const val COMPOSITE_ROUNDING_BIAS: Int = 127

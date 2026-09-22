package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot

internal fun PixelSnapshot.toRenderedBitmap(definition: PaletteDefinition): Bitmap {
    val width = size.width.value
    val indices = copyPackedIndices()
    val palette = definition.palette.entries().map { it.color.toPackedRgba8888().rgbaToArgb8888() }
    val colors = IntArray(indices.size) { index -> palette[indices[index].toInt() and CHANNEL_MASK] }
    return Bitmap.createBitmap(colors, width, size.height.value, Bitmap.Config.ARGB_8888)
}

internal fun PixelSnapshot.toOpaqueRenderedBitmap(
    definition: PaletteDefinition,
    backgroundArgb: Int,
): Bitmap {
    val width = size.width.value
    val indices = copyPackedIndices()
    val palette = definition.palette.entries().map { it.color.toPackedRgba8888().rgbaOverOpaqueArgb(backgroundArgb) }
    val colors = IntArray(indices.size) { index -> palette[indices[index].toInt() and CHANNEL_MASK] }
    return Bitmap.createBitmap(colors, width, size.height.value, Bitmap.Config.ARGB_8888)
}

private fun Int.rgbaToArgb8888(): Int = ((this and CHANNEL_MASK) shl ALPHA_SHIFT) or (this ushr CHANNEL_SHIFT)

private fun Int.rgbaOverOpaqueArgb(backgroundArgb: Int): Int {
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

package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import io.github.hideyukimori.nenepixel.core.application.render.CompositePixelTransform
import io.github.hideyukimori.nenepixel.core.application.render.DocumentCompositeImage

/**
 * The composite as a bitmap that keeps its alpha (ADR 0026). The platform premultiplies the
 * straight-alpha colours; the transparency backdrop shows through where the picture is transparent.
 */
internal fun DocumentCompositeImage.toRenderedBitmap(): Bitmap =
    Bitmap.createBitmap(toStraightArgb(), size.width.value, size.height.value, Bitmap.Config.ARGB_8888)

/** Row-major straight-alpha ARGB8888 of the composite: only a channel reorder, no blending. */
internal fun DocumentCompositeImage.toStraightArgb(): IntArray = mapPackedRgba8888(RGBA_TO_ARGB)

/** Packed RGBA8888 reordered to straight-alpha ARGB8888 (shift and or only). */
internal fun Int.rgbaToArgb8888(): Int = ((this and CHANNEL_MASK) shl ALPHA_SHIFT) or (this ushr CHANNEL_SHIFT)

private val RGBA_TO_ARGB: CompositePixelTransform = CompositePixelTransform { packed -> packed.rgbaToArgb8888() }

private const val ALPHA_SHIFT: Int = 24
private const val CHANNEL_SHIFT: Int = 8
private const val CHANNEL_MASK: Int = 0xff

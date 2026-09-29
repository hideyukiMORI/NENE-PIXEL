package io.github.hideyukimori.nenepixel.core.pixelengine.composite

/**
 * The per-pixel accumulation rule of ADR 0030, Composite: the first contribution is copied as is and
 * every later contribution is placed with [blendOver]. Returns the new packed RGBA8888 result.
 */
internal fun contributeOver(
    contributed: Boolean,
    resultRgba: Int,
    sourceRgba: Int,
): Int = if (contributed) blendOver(resultRgba, sourceRgba) else sourceRgba

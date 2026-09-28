package io.github.hideyukimori.nenepixel.core.pixelengine.composite

private const val CHANNEL_MASK: Int = 0xff
private const val CHANNEL_MAX: Int = 255
private const val HALF_CHANNEL_MAX: Int = 127
private const val RED_SHIFT: Int = 24
private const val GREEN_SHIFT: Int = 16
private const val BLUE_SHIFT: Int = 8

/**
 * Places [sourceRgba] over a result that already has a contribution (ADR 0030, Composite).
 * Both values are packed RGBA8888. Alpha 0 keeps the result, alpha 255 replaces it and a partial
 * alpha uses straight-alpha source-over with integer rounding only.
 */
internal fun blendOver(
    destinationRgba: Int,
    sourceRgba: Int,
): Int =
    when (val sourceAlpha = sourceRgba and CHANNEL_MASK) {
        0 -> destinationRgba
        CHANNEL_MAX -> sourceRgba
        else -> blendPartial(destinationRgba, sourceRgba, sourceAlpha)
    }

private fun blendPartial(
    destinationRgba: Int,
    sourceRgba: Int,
    sourceAlpha: Int,
): Int {
    val sourceWeight = sourceAlpha * CHANNEL_MAX
    val destinationWeight = (destinationRgba and CHANNEL_MASK) * (CHANNEL_MAX - sourceAlpha)
    val weights = BlendWeights(sourceWeight, destinationWeight)
    val alpha = (sourceWeight + destinationWeight + HALF_CHANNEL_MAX) / CHANNEL_MAX
    return (weights.channel(sourceRgba, destinationRgba, RED_SHIFT) shl RED_SHIFT) or
        (weights.channel(sourceRgba, destinationRgba, GREEN_SHIFT) shl GREEN_SHIFT) or
        (weights.channel(sourceRgba, destinationRgba, BLUE_SHIFT) shl BLUE_SHIFT) or
        alpha
}

private class BlendWeights(
    private val source: Int,
    private val destination: Int,
) {
    private val total: Int = source + destination

    fun channel(
        sourceRgba: Int,
        destinationRgba: Int,
        shift: Int,
    ): Int {
        val sourceChannel = (sourceRgba ushr shift) and CHANNEL_MASK
        val destinationChannel = (destinationRgba ushr shift) and CHANNEL_MASK
        val weighted = sourceChannel * source + destinationChannel * destination
        return (2 * weighted + total) / (2 * total)
    }
}

package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.render.CompositePixelTransform

/**
 * Packed RGBA8888 of the Composite placed over the opaque canvas colour, as opaque ARGB8888.
 * Equal consecutive inputs reuse the previous result, so a run of one colour costs one blend.
 */
internal class OpaqueCompositeColor(
    private val backgroundArgb: Int,
) : CompositePixelTransform {
    private var hasPrevious: Boolean = false
    private var previousInput: Int = 0
    private var previousOutput: Int = 0

    override fun map(packedRgba8888: Int): Int {
        if (!hasPrevious || packedRgba8888 != previousInput) {
            previousInput = packedRgba8888
            previousOutput = packedRgba8888.rgbaOverOpaqueArgb(backgroundArgb)
            hasPrevious = true
        }
        return previousOutput
    }
}

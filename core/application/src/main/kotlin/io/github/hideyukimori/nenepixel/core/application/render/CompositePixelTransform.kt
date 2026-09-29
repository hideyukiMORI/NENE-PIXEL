package io.github.hideyukimori.nenepixel.core.application.render

/** Maps one packed RGBA8888 value of the document's Composite to the value a caller needs. */
public fun interface CompositePixelTransform {
    public fun map(packedRgba8888: Int): Int
}

package io.github.hideyukimori.nenepixel.core.pixelengine.composite

/** Maps one packed RGBA8888 value of a composite to the value a caller needs. */
public fun interface PackedRgbaTransform {
    public fun map(packedRgba8888: Int): Int
}

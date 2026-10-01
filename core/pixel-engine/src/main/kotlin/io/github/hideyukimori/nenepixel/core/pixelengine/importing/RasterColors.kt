package io.github.hideyukimori.nenepixel.core.pixelengine.importing

/**
 * Primitive helpers over packed RGBA8888 values (ADR 0033): a pixel whose alpha is 0 is
 * transparent and is not a colour, whatever its RGB is.
 */
internal object RasterColors {
    fun isColor(packedRgba8888: Int): Boolean = packedRgba8888 and ALPHA_MASK != 0

    fun count(values: IntArray): Int = values.count(::isColor)

    /**
     * Sorts [values] in place and moves its distinct colours to the front in ascending order;
     * returns how many there are. Transparent values are skipped.
     */
    fun sortDistinct(values: IntArray): Int {
        values.sort()
        var distinct = 0
        values.forEach { value ->
            if (isColor(value) && (distinct == 0 || values[distinct - 1] != value)) {
                values[distinct] = value
                distinct += 1
            }
        }
        return distinct
    }

    private const val ALPHA_MASK: Int = 0xff
}

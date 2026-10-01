package io.github.hideyukimori.nenepixel.adapters.persistence

/**
 * The PNG colour types that the import accepts (ADR 0033), each with its `IHDR` code, the samples
 * of one pixel and the bit depths accepted for it.
 */
internal enum class PngImportColorType(
    val code: Int,
    val samplesPerPixel: Int,
    val bitDepths: Set<Int>,
) {
    GREY(GREY_CODE, 1, setOf(1, 2, FOUR_BITS, EIGHT_BITS)),
    TRUECOLOUR(TRUECOLOUR_CODE, RGB_SAMPLES, setOf(EIGHT_BITS)),
    INDEXED(INDEXED_CODE, 1, setOf(1, 2, FOUR_BITS, EIGHT_BITS)),
    GREY_ALPHA(GREY_ALPHA_CODE, 2, setOf(EIGHT_BITS)),
    TRUECOLOUR_ALPHA(TRUECOLOUR_ALPHA_CODE, RGBA_SAMPLES, setOf(EIGHT_BITS)),
    ;

    companion object {
        /** The colour type whose `IHDR` code is [code], or null when the import has none for it. */
        fun fromCode(code: Int): PngImportColorType? = entries.firstOrNull { it.code == code }
    }
}

private const val GREY_CODE: Int = 0
private const val TRUECOLOUR_CODE: Int = 2
private const val INDEXED_CODE: Int = 3
private const val GREY_ALPHA_CODE: Int = 4
private const val TRUECOLOUR_ALPHA_CODE: Int = 6
private const val RGB_SAMPLES: Int = 3
private const val RGBA_SAMPLES: Int = 4
private const val FOUR_BITS: Int = 4
private const val EIGHT_BITS: Int = 8

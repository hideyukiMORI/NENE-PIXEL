package io.github.hideyukimori.nenepixel.acceptance

/**
 * Hand-written project v3 expectations for the acceptance journey document: 8x6 pixels,
 * the 9-entry application palette, one visible unnamed layer. Offsets are big-endian bytes
 * from the start of the file (ADR 0030). They are fixed here on purpose instead of being
 * derived from the production encoder or decoder.
 *
 * - 0..7 magic, 8..9 version (3), 10..11 width, 12..13 height, 14..29 document id,
 *   30..37 revision, 38..39 palette entry count, 40 default index, 41..76 palette (9 x RGBA8888).
 * - 77 layer count, then the single layer: 78..81 layer id, 82 flags (bit 0 = visible),
 *   83 name byte length (0, so no name bytes follow).
 * - 84..89 coverage: one bit per row-major pixel, least significant bit first; 1 = covered.
 * - 90..137 palette index per row-major pixel; an Empty pixel stores index 0.
 * - 138..141 CRC32 (ISO-HDLC) over bytes 0..137. The file is 142 bytes long.
 */
internal object AcceptanceProjectV3 {
    const val VERSION: Int = 3
    const val FILE_BYTE_COUNT: Int = 142
    const val PIXEL_COUNT: Int = 48
    const val LAYER_COUNT_OFFSET: Int = 77
    const val LAYER_COUNT: Int = 1
    const val LAYER_ID_OFFSET: Int = 78
    const val LAYER_ID: Int = 1
    const val LAYER_FLAGS_OFFSET: Int = 82
    const val VISIBLE_FLAGS: Int = 0x01
    const val LAYER_NAME_LENGTH_OFFSET: Int = 83
    const val COVERAGE_OFFSET: Int = 84
    const val COVERAGE_BYTE_COUNT: Int = 6
    const val INDICES_OFFSET: Int = 90
    const val CHECKSUM_OFFSET: Int = 138

    /**
     * Coverage bytes for the drawn pixels 9 and 11 (byte 1, bits 1 and 3 = 0x0a) and 29
     * (byte 3, bit 5 = 0x20); the recovered document also covers pixel 38 (byte 4, bit 6 = 0x40).
     */
    fun expectedCoverage(recovered: Boolean = false): IntArray =
        intArrayOf(0x00, 0x0a, 0x00, 0x20, if (recovered) 0x40 else 0x00, 0x00)

    /** Palette indices: red (0) at 9 and 11, entry 4 at 29, entry 3 at recovered 38; Empty pixels store 0. */
    fun expectedIndices(recovered: Boolean = false): IntArray =
        IntArray(PIXEL_COUNT).apply {
            this[29] = 4
            if (recovered) this[38] = 3
        }
}

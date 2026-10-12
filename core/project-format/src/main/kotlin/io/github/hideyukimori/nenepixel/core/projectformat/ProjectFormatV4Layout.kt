package io.github.hideyukimori.nenepixel.core.projectformat

/** V4 adds nine bytes per frame; the v3 layer record remains unchanged. */
internal object ProjectFormatV4Layout {
    const val VERSION: Int = 4
    const val FIXED_BYTE_COUNT: Int = 41
    const val MAX_FILE_BYTE_COUNT: Int = 1_183_006
    const val FRAME_FIXED_BYTE_COUNT: Int = 9
    const val DURATION_OFFSET: Int = 4
    const val LAYER_COUNT_OFFSET: Int = 8
    const val MAX_FRAME_COUNT: Int = 16
    const val MAX_TOTAL_LAYER_COUNT: Int = 16
    private const val UNSIGNED_INT_MASK: Long = 0xffff_ffffL

    fun unsignedInt(
        source: ProjectFormatBytes,
        offset: Int,
    ): Long = ProjectFormatBigEndian.readInt(source, offset).toLong() and UNSIGNED_INT_MASK
}

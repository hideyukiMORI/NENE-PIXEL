package io.github.hideyukimori.nenepixel.core.projectformat

internal object ProjectFormatV2Layout {
    const val VERSION: Int = 2
    const val VERSION_OFFSET: Int = 8
    const val WIDTH_OFFSET: Int = 10
    const val HEIGHT_OFFSET: Int = 12
    const val DOCUMENT_ID_OFFSET: Int = 14
    const val DOCUMENT_ID_BYTE_COUNT: Int = 16
    const val REVISION_OFFSET: Int = 30
    const val PALETTE_COUNT_OFFSET: Int = 38
    const val DEFAULT_INDEX_OFFSET: Int = 40
    const val PALETTE_OFFSET: Int = 41
    const val FIXED_BYTE_COUNT: Int = PALETTE_OFFSET
    const val MIN_PALETTE_ENTRY_COUNT: Int = 2
    const val MAX_PALETTE_ENTRY_COUNT: Int = 256
    const val MAX_CANVAS_AXIS: Int = 256
    const val PALETTE_ENTRY_BYTE_COUNT: Int = Int.SIZE_BYTES
    const val MIN_FILE_BYTE_COUNT: Int = FIXED_BYTE_COUNT + 4 * MIN_PALETTE_ENTRY_COUNT + 1 + Int.SIZE_BYTES
    const val MAX_FILE_BYTE_COUNT: Int =
        FIXED_BYTE_COUNT + PALETTE_ENTRY_BYTE_COUNT * MAX_PALETTE_ENTRY_COUNT +
            MAX_CANVAS_AXIS * MAX_CANVAS_AXIS + Int.SIZE_BYTES

    fun expectedByteCount(
        pixelCount: Long,
        paletteEntryCount: Int,
    ): Long {
        check(pixelCount >= 0L)
        check(paletteEntryCount in MIN_PALETTE_ENTRY_COUNT..MAX_PALETTE_ENTRY_COUNT)
        return FIXED_BYTE_COUNT.toLong() + PALETTE_ENTRY_BYTE_COUNT * paletteEntryCount + pixelCount + Int.SIZE_BYTES
    }

    fun writeMagic(destination: ByteArray) {
        MAGIC.copyInto(destination)
    }

    fun hasMagic(source: ProjectFormatBytes): Boolean =
        MAGIC.indices.all { index -> source.byteAt(index) == MAGIC[index] }

    private val MAGIC: ByteArray =
        byteArrayOf(0x4e, 0x45, 0x4e, 0x45, 0x50, 0x49, 0x58, 0x00)
}

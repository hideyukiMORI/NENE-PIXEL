package io.github.hideyukimori.nenepixel.core.projectformat

internal object ProjectFormatV1Layout {
    const val VERSION: Int = 1
    const val VERSION_OFFSET: Int = 8
    const val WIDTH_OFFSET: Int = 10
    const val HEIGHT_OFFSET: Int = 12
    const val DOCUMENT_ID_OFFSET: Int = 14
    const val DOCUMENT_ID_BYTE_COUNT: Int = 16
    const val REVISION_OFFSET: Int = 30
    const val PIXEL_OFFSET: Int = 38
    const val FIXED_BYTE_COUNT: Int = 42
    const val MIN_FILE_BYTE_COUNT: Int = 46

    private val magic: ByteArray =
        byteArrayOf(0x4e, 0x45, 0x4e, 0x45, 0x50, 0x49, 0x58, 0x00)

    fun writeMagic(destination: ByteArray) {
        magic.copyInto(destination)
    }

    fun hasMagic(source: ProjectFormatBytes): Boolean =
        magic.indices.all { index -> source.byteAt(index) == magic[index] }

    fun expectedByteCount(pixelCount: Long): Long {
        check(pixelCount >= 0L && pixelCount <= (Long.MAX_VALUE - FIXED_BYTE_COUNT) / Int.SIZE_BYTES) {
            "Pixel count cannot be represented by the v1 length formula: $pixelCount"
        }
        return FIXED_BYTE_COUNT + Int.SIZE_BYTES * pixelCount
    }
}

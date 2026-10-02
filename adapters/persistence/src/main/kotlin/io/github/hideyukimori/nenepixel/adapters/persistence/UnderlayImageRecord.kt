package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult

/**
 * The `<id>.image` record of ADR 0034: `NPUI`, version, width, height, the pixels as red, green,
 * blue and alpha bytes (the packed RGBA8888 value from its highest byte), and the CRC-32.
 */
internal object UnderlayImageRecord {
    /** Returns a newly allocated record of [image]. */
    fun encode(image: ReferenceImage): ByteArray {
        val pixels = image.copyPackedRgba8888()
        val bytes = ByteArray(recordLength(image.width, image.height).toInt())
        UnderlayMemoryLayout.writePrefix(bytes, UnderlayMemoryLayout.IMAGE_MAGIC)
        RecoveryRecordBigEndian.writeUnsignedShort(bytes, WIDTH_OFFSET, image.width)
        RecoveryRecordBigEndian.writeUnsignedShort(bytes, HEIGHT_OFFSET, image.height)
        pixels.forEachIndexed { index, pixel ->
            RecoveryRecordBigEndian.writeInt(bytes, pixelOffset(index), pixel)
        }
        UnderlayMemoryLayout.seal(bytes)
        return bytes
    }

    /** The CRC-32 field of an image [record] (the value the state record names). */
    fun checksum(record: ByteArray): Int = UnderlayMemoryLayout.storedChecksum(record)

    /** Checks the length, magic, version, header and CRC-32 before allocating the pixels. */
    fun decode(bytes: ByteArray): UnderlayRecordDecodeResult<ReferenceImage> =
        if (hasReadableFrame(bytes) && UnderlayMemoryLayout.hasValidChecksum(bytes)) {
            decodePixels(bytes)
        } else {
            UnderlayRecordDecodeResult.Unreadable
        }

    private fun hasReadableFrame(bytes: ByteArray): Boolean =
        bytes.size in UnderlayMemoryLayout.IMAGE_MIN_BYTE_COUNT..UnderlayMemoryLayout.IMAGE_MAX_BYTE_COUNT &&
            UnderlayMemoryLayout.hasPrefix(bytes, UnderlayMemoryLayout.IMAGE_MAGIC) &&
            recordLength(width(bytes), height(bytes)) == bytes.size.toLong()

    private fun decodePixels(bytes: ByteArray): UnderlayRecordDecodeResult<ReferenceImage> {
        val width = width(bytes)
        val height = height(bytes)
        val pixels = IntArray(width * height) { index -> RecoveryRecordBigEndian.readInt(bytes, pixelOffset(index)) }
        return when (val created = ReferenceImage.create(width, height, pixels)) {
            is ReferenceImageResult.Created -> UnderlayRecordDecodeResult.Decoded(created.image)
            is ReferenceImageResult.Rejected -> UnderlayRecordDecodeResult.Unreadable
        }
    }

    private fun width(bytes: ByteArray): Int = RecoveryRecordBigEndian.readUnsignedShort(bytes, WIDTH_OFFSET)

    private fun height(bytes: ByteArray): Int = RecoveryRecordBigEndian.readUnsignedShort(bytes, HEIGHT_OFFSET)

    private fun recordLength(
        width: Int,
        height: Int,
    ): Long =
        UnderlayMemoryLayout.IMAGE_HEADER_BYTE_COUNT +
            UnderlayMemoryLayout.IMAGE_BYTES_PER_PIXEL.toLong() * width * height +
            UnderlayMemoryLayout.CHECKSUM_BYTE_COUNT

    private fun pixelOffset(index: Int): Int =
        UnderlayMemoryLayout.IMAGE_HEADER_BYTE_COUNT + UnderlayMemoryLayout.IMAGE_BYTES_PER_PIXEL * index

    private const val WIDTH_OFFSET: Int = 6
    private const val HEIGHT_OFFSET: Int = 8
}

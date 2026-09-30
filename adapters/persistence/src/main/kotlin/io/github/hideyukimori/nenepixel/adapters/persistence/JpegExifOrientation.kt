package io.github.hideyukimori.nenepixel.adapters.persistence

import kotlin.math.min

/**
 * Reads the EXIF `Orientation` tag of a JPEG file with a bounded parser of its own (ADR 0032).
 *
 * The segments after the start marker are walked up to the start of scan, the end marker, the end of
 * the bytes, or [MAX_SEGMENTS] segments. Only the first APP1 segment whose payload starts with
 * `Exif\0\0` is read: its TIFF header, then at most [MAX_ENTRIES] entries of IFD0. Every read is
 * range checked; anything that cannot be read is [NORMAL]. Nothing is thrown.
 */
internal object JpegExifOrientation {
    const val NORMAL: Int = 1
    const val FLIP_HORIZONTAL: Int = 2
    const val ROTATE_180: Int = 3
    const val FLIP_VERTICAL: Int = 4
    const val TRANSPOSE: Int = 5
    const val ROTATE_90: Int = 6
    const val TRANSVERSE: Int = 7
    const val ROTATE_270: Int = 8

    private const val MAX_SEGMENTS: Int = 64
    private const val MAX_ENTRIES: Int = 256
    private const val START_OF_IMAGE: Long = 0xFFD8
    private const val START_OF_SCAN: Long = 0xFFDA
    private const val END_OF_IMAGE: Long = 0xFFD9
    private const val APP1: Long = 0xFFE1
    private const val MARKER_BYTES: Int = 2
    private const val LENGTH_BYTES: Int = 2
    private const val SHORT_BYTES: Int = 2
    private const val LONG_BYTES: Int = 4
    private const val INTEL_ORDER: Long = 0x4949
    private const val MOTOROLA_ORDER: Long = 0x4D4D
    private const val TIFF_MAGIC: Long = 42
    private const val MAGIC_POSITION: Int = 2
    private const val IFD_OFFSET_POSITION: Int = 4
    private const val ENTRY_BYTES: Int = 12
    private const val TYPE_POSITION: Int = 2
    private const val COUNT_POSITION: Int = 4
    private const val VALUE_POSITION: Int = 8
    private const val ORIENTATION_TAG: Long = 0x0112
    private const val SHORT_TYPE: Long = 3
    private const val BYTE_BITS: Int = 8
    private const val BYTE_MASK: Long = 0xFF
    private val exifHeader: ByteArray = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)

    /** The orientation (1 to 8) of [encoded], or [NORMAL] when it is not a JPEG or the tag cannot be read. */
    fun orientation(encoded: ByteArray): Int =
        if (unsigned(encoded, 0, MARKER_BYTES, false) == START_OF_IMAGE) {
            exifTiff(encoded)?.let(::tiffOrientation) ?: NORMAL
        } else {
            NORMAL
        }

    /** The TIFF bytes of the first Exif APP1 segment within the first [MAX_SEGMENTS] segments. */
    private fun exifTiff(jpeg: ByteArray): ByteArray? {
        var position = MARKER_BYTES
        var visited = 0
        var tiff: ByteArray? = null
        while (tiff == null && visited < MAX_SEGMENTS) {
            val end = segmentEnd(jpeg, position)
            if (end == null) {
                visited = MAX_SEGMENTS
            } else {
                tiff = exifPayload(jpeg, position, end)
                position = end
                visited++
            }
        }
        return tiff
    }

    /** The end of the length-carrying segment at [position], or null at a scan, an end, or a broken segment. */
    private fun segmentEnd(
        jpeg: ByteArray,
        position: Int,
    ): Int? =
        unsigned(jpeg, position, MARKER_BYTES, false)
            ?.takeIf { it shr BYTE_BITS == BYTE_MASK && it != START_OF_SCAN && it != END_OF_IMAGE }
            ?.let { unsigned(jpeg, position + MARKER_BYTES, LENGTH_BYTES, false) }
            ?.takeIf { it >= LENGTH_BYTES }
            ?.let { position + MARKER_BYTES + it.toInt() }
            ?.takeIf { it <= jpeg.size }

    private fun exifPayload(
        jpeg: ByteArray,
        position: Int,
        end: Int,
    ): ByteArray? {
        val payload = position + MARKER_BYTES + LENGTH_BYTES
        val tiffStart = payload + exifHeader.size
        val isExif =
            unsigned(jpeg, position, MARKER_BYTES, false) == APP1 &&
                tiffStart <= end &&
                exifHeader.indices.all { jpeg[payload + it] == exifHeader[it] }
        return if (isExif) jpeg.copyOfRange(tiffStart, end) else null
    }

    private fun tiffOrientation(tiff: ByteArray): Int? =
        byteOrder(tiff)?.let { littleEndian ->
            unsigned(tiff, IFD_OFFSET_POSITION, LONG_BYTES, littleEndian)
                ?.takeIf { unsigned(tiff, MAGIC_POSITION, SHORT_BYTES, littleEndian) == TIFF_MAGIC }
                ?.takeIf { it < tiff.size }
                ?.let { ifdOrientation(tiff, it.toInt(), littleEndian) }
        }

    /** True for little-endian (`II`), false for big-endian (`MM`), null for anything else. */
    private fun byteOrder(tiff: ByteArray): Boolean? =
        when (unsigned(tiff, 0, SHORT_BYTES, false)) {
            INTEL_ORDER -> true
            MOTOROLA_ORDER -> false
            else -> null
        }

    private fun ifdOrientation(
        tiff: ByteArray,
        ifd: Int,
        littleEndian: Boolean,
    ): Int? =
        unsigned(tiff, ifd, SHORT_BYTES, littleEndian)?.let { count ->
            (0 until min(count.toInt(), MAX_ENTRIES))
                .asSequence()
                .map { ifd + SHORT_BYTES + it * ENTRY_BYTES }
                .takeWhile { it <= tiff.size - ENTRY_BYTES }
                .firstOrNull { unsigned(tiff, it, SHORT_BYTES, littleEndian) == ORIENTATION_TAG }
                ?.let { entryOrientation(tiff, it, littleEndian) }
        }

    /** The value of a SHORT, count 1 orientation entry when it is 1 to 8; null otherwise. */
    private fun entryOrientation(
        tiff: ByteArray,
        entry: Int,
        littleEndian: Boolean,
    ): Int? =
        if (unsigned(tiff, entry + TYPE_POSITION, SHORT_BYTES, littleEndian) == SHORT_TYPE &&
            unsigned(tiff, entry + COUNT_POSITION, LONG_BYTES, littleEndian) == 1L
        ) {
            unsigned(tiff, entry + VALUE_POSITION, SHORT_BYTES, littleEndian)
                ?.toInt()
                ?.takeIf { it in NORMAL..ROTATE_270 }
        } else {
            null
        }

    /** The unsigned [width]-byte value at [offset], or null when it does not lie inside [bytes]. */
    private fun unsigned(
        bytes: ByteArray,
        offset: Int,
        width: Int,
        littleEndian: Boolean,
    ): Long? =
        if (offset < 0 || offset > bytes.size - width) {
            null
        } else {
            (0 until width).fold(0L) { value, step ->
                val index = if (littleEndian) offset + width - 1 - step else offset + step
                (value shl BYTE_BITS) or (bytes[index].toLong() and BYTE_MASK)
            }
        }
}

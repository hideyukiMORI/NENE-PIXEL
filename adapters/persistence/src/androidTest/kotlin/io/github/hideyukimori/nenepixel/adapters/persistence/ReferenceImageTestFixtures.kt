package io.github.hideyukimori.nenepixel.adapters.persistence

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Encoded sources for the reference image decoder tests, shared by their test classes. */
internal object ReferenceImageTestFixtures {
    private const val FULL_QUALITY: Int = 100
    private const val APP1_SEGMENT_BYTES: Int = 36
    private const val TIFF_MAGIC: Short = 42
    private const val IFD0_OFFSET: Int = 8
    private const val ORIENTATION_TAG: Short = 0x0112
    private const val SHORT_TYPE: Short = 3

    /** Compresses [bitmap] at full quality and recycles it. */
    fun encoded(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
    ): ByteArray =
        try {
            val output = ByteArrayOutputStream()
            check(bitmap.compress(format, FULL_QUALITY, output))
            output.toByteArray()
        } finally {
            bitmap.recycle()
        }

    /**
     * Puts an APP1 Exif segment right after the JPEG start marker. Its big-endian TIFF holds one IFD0
     * entry: the orientation tag, type SHORT, count 1.
     */
    fun withOrientation(
        jpeg: ByteArray,
        orientation: Int,
    ): ByteArray {
        val app1 =
            ByteBuffer
                .allocate(APP1_SEGMENT_BYTES)
                .putShort(0xFFE1.toShort())
                .putShort((APP1_SEGMENT_BYTES - 2).toShort())
                .put("Exif\u0000\u0000".toByteArray(Charsets.US_ASCII))
                .put("MM".toByteArray(Charsets.US_ASCII))
                .putShort(TIFF_MAGIC)
                .putInt(IFD0_OFFSET)
                .putShort(1)
                .putShort(ORIENTATION_TAG)
                .putShort(SHORT_TYPE)
                .putInt(1)
                .putShort(orientation.toShort())
                .putShort(0)
                .putInt(0)
                .array()
        return jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size)
    }
}

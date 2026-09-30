package io.github.hideyukimori.nenepixel.adapters.persistence

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
public class AndroidBitmapReferenceImageDecoderTest {
    private val decoder: ReferenceImageDecoder = AndroidBitmapReferenceImageDecoder

    @Test
    public fun pngDecodesToItsSize() {
        val decoded = decoded(encoded(solid(12, 7, Color.RED), Bitmap.CompressFormat.PNG))

        assertEquals(12, decoded.width)
        assertEquals(7, decoded.height)
        assertEquals(12 * 7, decoded.packedRgba8888.size)
    }

    @Test
    public fun jpegDecodesToItsSize() {
        val decoded = decoded(encoded(solid(16, 8, Color.RED), Bitmap.CompressFormat.JPEG))

        assertEquals(16, decoded.width)
        assertEquals(8, decoded.height)
        assertOpaqueRedish(decoded.packedRgba8888[0])
    }

    @Test
    public fun webpDecodesToItsSize() {
        val format = webpLosslessFormat()
        assumeTrue("lossless WebP compression needs API 30", format != null)
        val decoded = decoded(encoded(solid(9, 5, Color.RED), checkNotNull(format)))

        assertEquals(9, decoded.width)
        assertEquals(5, decoded.height)
        assertEquals(RED_RGBA, decoded.packedRgba8888[0])
    }

    @Test
    public fun largeSourceShrinksToTheResultLimitKeepingItsAspect() {
        val decoded = decoded(encoded(solid(2000, 1000, Color.RED), Bitmap.CompressFormat.PNG))

        assertEquals(1024, decoded.width)
        assertEquals(512, decoded.height)
    }

    @Test
    public fun smallSourceIsNotEnlarged() {
        val decoded = decoded(encoded(solid(100, 50, Color.RED), Bitmap.CompressFormat.PNG))

        assertEquals(100, decoded.width)
        assertEquals(50, decoded.height)
    }

    @Test
    public fun translucentPngKeepsItsAlpha() {
        val color = Color.argb(0x80, 0x20, 0x40, 0x60)
        val packed = decoded(encoded(solid(4, 4, color), Bitmap.CompressFormat.PNG)).packedRgba8888[0]

        assertEquals(0x80, packed and 0xFF)
        assertNear(0x20, packed ushr 24)
        assertNear(0x40, (packed ushr 16) and 0xFF)
        assertNear(0x60, (packed ushr 8) and 0xFF)
    }

    @Test
    public fun exifRotationOfNinetyDegreesSwapsTheSides() {
        val source = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        for (y in 0 until 20) {
            for (x in 0 until 40) {
                source.setPixel(x, y, if (x < 20) Color.RED else Color.BLUE)
            }
        }
        val decoded = decoded(withOrientation(encoded(source, Bitmap.CompressFormat.JPEG), ROTATE_90))

        assertEquals(20, decoded.width)
        assertEquals(40, decoded.height)
        assertOpaqueRedish(decoded.packedRgba8888[5 * 20 + 10])
    }

    @Test
    public fun truncatedBytesAreUnsupported() {
        val png = encoded(solid(8, 8, Color.RED), Bitmap.CompressFormat.PNG)

        assertSame(ReferenceImageDecodeResult.Unsupported, decoder.decode(png.copyOf(TRUNCATED_BYTE_COUNT)))
    }

    @Test
    public fun bytesThatAreNotAPictureAreUnsupported() {
        assertSame(ReferenceImageDecodeResult.Unsupported, decoder.decode("not a picture".toByteArray()))
    }

    @Test
    public fun emptyBytesAreUnsupported() {
        assertSame(ReferenceImageDecodeResult.Unsupported, decoder.decode(ByteArray(0)))
    }

    @Test
    public fun pngHeaderClaimingTooLargeASourceIsTooManyPixels() {
        assertSame(ReferenceImageDecodeResult.TooManyPixels, decoder.decode(pngHeader(20_000, 20_000)))
    }

    @Test
    public fun opaqueRedPacksAsRgba() {
        val decoded = decoded(encoded(solid(3, 2, Color.RED), Bitmap.CompressFormat.PNG))

        assertTrue(decoded.packedRgba8888.all { it == RED_RGBA })
    }

    private fun decoded(encoded: ByteArray): ReferenceImageDecodeResult.Decoded {
        val result = decoder.decode(encoded)
        assertTrue("expected Decoded, was $result", result is ReferenceImageDecodeResult.Decoded)
        return result as ReferenceImageDecodeResult.Decoded
    }

    private fun solid(
        width: Int,
        height: Int,
        color: Int,
    ): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun encoded(
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

    private fun webpLosslessFormat(): Bitmap.CompressFormat? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSLESS else null

    /**
     * Puts an APP1 Exif segment right after the JPEG start marker. Its big-endian TIFF holds one IFD0
     * entry: the orientation tag, type SHORT, count 1.
     */
    private fun withOrientation(
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

    /** A PNG signature, an IHDR chunk with the given sides, a tiny IDAT chunk, and IEND. */
    private fun pngHeader(
        width: Int,
        height: Int,
    ): ByteArray {
        val ihdr =
            ByteBuffer
                .allocate(IHDR_LENGTH)
                .putInt(width)
                .putInt(height)
                .put(byteArrayOf(8, 6, 0, 0, 0))
                .array()
        val output = ByteArrayOutputStream()
        output.write(PNG_SIGNATURE)
        output.write(chunk("IHDR", ihdr))
        output.write(chunk("IDAT", byteArrayOf(0x78, 0x9C.toByte(), 0x03, 0x00, 0x00, 0x00, 0x00, 0x01)))
        output.write(chunk("IEND", ByteArray(0)))
        return output.toByteArray()
    }

    private fun chunk(
        type: String,
        data: ByteArray,
    ): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc =
            CRC32().apply {
                update(typeBytes)
                update(data)
            }
        return ByteBuffer
            .allocate(CHUNK_OVERHEAD + data.size)
            .putInt(data.size)
            .put(typeBytes)
            .put(data)
            .putInt(crc.value.toInt())
            .array()
    }

    private fun assertOpaqueRedish(packed: Int) {
        assertEquals(0xFF, packed and 0xFF)
        assertTrue("red dominates in ${Integer.toHexString(packed)}", (packed ushr 24) > 0xC0)
        assertTrue("blue is low in ${Integer.toHexString(packed)}", ((packed ushr 8) and 0xFF) < 0x40)
    }

    private fun assertNear(
        expected: Int,
        actual: Int,
    ) {
        val message = "expected $expected +/- $CHANNEL_TOLERANCE, was $actual"
        assertTrue(message, abs(expected - actual) <= CHANNEL_TOLERANCE)
    }

    private companion object {
        const val RED_RGBA: Int = 0xFF0000FF.toInt()
        const val FULL_QUALITY: Int = 100
        const val ROTATE_90: Int = JpegExifOrientation.ROTATE_90
        const val APP1_SEGMENT_BYTES: Int = 36
        const val TIFF_MAGIC: Short = 42
        const val IFD0_OFFSET: Int = 8
        const val ORIENTATION_TAG: Short = 0x0112
        const val SHORT_TYPE: Short = 3
        const val TRUNCATED_BYTE_COUNT: Int = 20
        const val IHDR_LENGTH: Int = 13
        const val CHUNK_OVERHEAD: Int = 12
        const val CHANNEL_TOLERANCE: Int = 2
        val PNG_SIGNATURE: ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}

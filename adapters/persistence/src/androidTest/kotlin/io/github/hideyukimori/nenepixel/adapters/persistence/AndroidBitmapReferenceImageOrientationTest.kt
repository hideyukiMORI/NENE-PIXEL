package io.github.hideyukimori.nenepixel.adapters.persistence

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hideyukimori.nenepixel.adapters.persistence.ReferenceImageTestFixtures.encoded
import io.github.hideyukimori.nenepixel.adapters.persistence.ReferenceImageTestFixtures.withOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Subsampling and the eight EXIF orientations of [AndroidBitmapReferenceImageDecoder], read by pixel
 * position. The EXIF sources are landscape JPEG files whose left half is red and right half is blue;
 * each half is a large fill and the tests read points a quarter of the long side away from the seam.
 */
@RunWith(AndroidJUnit4::class)
public class AndroidBitmapReferenceImageOrientationTest {
    private val decoder: ReferenceImageDecoder = AndroidBitmapReferenceImageDecoder

    @Test
    public fun sourceLongerThanTwiceTheResultLimitIsSubsampledAndKeepsItsAspect() {
        assertTrue(LONG_SOURCE_WIDTH / 2 >= ReferenceImageLimits.MAX_RESULT_SIDE)
        val source =
            Bitmap.createBitmap(LONG_SOURCE_WIDTH, LONG_SOURCE_HEIGHT, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.RED)
            }
        val decoded = decoded(encoded(source, Bitmap.CompressFormat.PNG))

        assertEquals(ReferenceImageLimits.MAX_RESULT_SIDE, decoded.width)
        assertTrue("height ${decoded.height}", decoded.height in SUBSAMPLED_HEIGHT_RANGE)
        assertEquals(decoded.width * decoded.height, decoded.packedRgba8888.size)
    }

    @Test
    public fun normalAndVerticalFlipKeepRedOnTheLeft() {
        assertRedAt(JpegExifOrientation.NORMAL, NEAR, MIDDLE)
        assertRedAt(JpegExifOrientation.FLIP_VERTICAL, NEAR, MIDDLE)
    }

    @Test
    public fun horizontalFlipAndHalfTurnPutRedOnTheRight() {
        assertRedAt(JpegExifOrientation.FLIP_HORIZONTAL, FAR, MIDDLE)
        assertRedAt(JpegExifOrientation.ROTATE_180, FAR, MIDDLE)
    }

    @Test
    public fun transposeAndClockwiseQuarterTurnPutRedOnTop() {
        assertRedAt(JpegExifOrientation.TRANSPOSE, MIDDLE, NEAR)
        assertRedAt(JpegExifOrientation.ROTATE_90, MIDDLE, NEAR)
    }

    @Test
    public fun transverseAndCounterClockwiseQuarterTurnPutRedAtTheBottom() {
        assertRedAt(JpegExifOrientation.TRANSVERSE, MIDDLE, FAR)
        assertRedAt(JpegExifOrientation.ROTATE_270, MIDDLE, FAR)
    }

    /**
     * Decodes the red-and-blue source with [orientation] and checks the sides and that red is at the
     * fractional point ([redX], [redY]) of the result while blue is at the point mirrored through the
     * centre. Orientations 5 to 8 swap width and height.
     */
    private fun assertRedAt(
        orientation: Int,
        redX: Float,
        redY: Float,
    ) {
        val decoded = decoded(withOrientation(halvesJpeg(), orientation))
        val swapped = orientation >= JpegExifOrientation.TRANSPOSE
        val label = "orientation $orientation"

        assertEquals(label, if (swapped) HALVES_HEIGHT else HALVES_WIDTH, decoded.width)
        assertEquals(label, if (swapped) HALVES_WIDTH else HALVES_HEIGHT, decoded.height)
        val red = decoded.at(redX, redY)
        val blue = decoded.at(1f - redX, 1f - redY)
        assertTrue("$label red at ($redX, $redY): ${Integer.toHexString(red)}", red.isMostly(RED_SHIFT, BLUE_SHIFT))
        assertTrue("$label blue opposite: ${Integer.toHexString(blue)}", blue.isMostly(BLUE_SHIFT, RED_SHIFT))
    }

    private fun halvesJpeg(): ByteArray {
        val source = Bitmap.createBitmap(HALVES_WIDTH, HALVES_HEIGHT, Bitmap.Config.ARGB_8888)
        for (y in 0 until HALVES_HEIGHT) {
            for (x in 0 until HALVES_WIDTH) {
                source.setPixel(x, y, if (x < HALVES_WIDTH / 2) Color.RED else Color.BLUE)
            }
        }
        return encoded(source, Bitmap.CompressFormat.JPEG)
    }

    private fun decoded(encoded: ByteArray): ReferenceImageDecodeResult.Decoded {
        val result = decoder.decode(encoded)
        assertTrue("expected Decoded, was $result", result is ReferenceImageDecodeResult.Decoded)
        return result as ReferenceImageDecodeResult.Decoded
    }

    private fun ReferenceImageDecodeResult.Decoded.at(
        fractionX: Float,
        fractionY: Float,
    ): Int = packedRgba8888[(fractionY * height).toInt() * width + (fractionX * width).toInt()]

    /** Straight RGBA8888: opaque, the [strong] channel high, and the [weak] channel low. */
    private fun Int.isMostly(
        strong: Int,
        weak: Int,
    ): Boolean =
        (this and CHANNEL_MASK) == CHANNEL_MASK &&
            ((this ushr strong) and CHANNEL_MASK) > CHANNEL_HIGH &&
            ((this ushr weak) and CHANNEL_MASK) < CHANNEL_LOW

    private companion object {
        const val LONG_SOURCE_WIDTH: Int = 4100
        const val LONG_SOURCE_HEIGHT: Int = 100

        /** 100 * 1024 / 4100 is about 24.98; the sampled source height may round either way. */
        val SUBSAMPLED_HEIGHT_RANGE: IntRange = 24..26
        const val HALVES_WIDTH: Int = 64
        const val HALVES_HEIGHT: Int = 32
        const val NEAR: Float = 0.25f
        const val MIDDLE: Float = 0.5f
        const val FAR: Float = 0.75f
        const val RED_SHIFT: Int = 24
        const val BLUE_SHIFT: Int = 8
        const val CHANNEL_MASK: Int = 0xFF

        /** JPEG error allowance: the strong channel stays above this and the weak one below [CHANNEL_LOW]. */
        const val CHANNEL_HIGH: Int = 0xC0
        const val CHANNEL_LOW: Int = 0x40
    }
}

package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.PointF
import org.junit.Assert.assertTrue
import kotlin.math.abs
import kotlin.math.floor

/**
 * Expected colours where the picture meets the transparency backdrop (ADR 0026, Issue #147).
 * Bitmaps are premultiplied by the platform, so a partially transparent pixel may differ from the
 * integer expectation by one step per channel; opaque and fully transparent pixels match exactly.
 */
internal object TransparencyExpectation {
    /** What the screen shows for straight-alpha [argb] over the opaque [backdropArgb] (integer source-over). */
    fun shown(
        argb: Int,
        backdropArgb: Int,
    ): Int =
        when (val alpha = argb ushr ALPHA_SHIFT) {
            CHANNEL_MAX -> argb
            0 -> backdropArgb
            else -> sourceOver(argb, backdropArgb, alpha)
        }

    /** Steps per channel allowed for source [sourceArgb]: 1 when partially transparent, else 0. */
    fun toleranceFor(sourceArgb: Int): Int = if ((sourceArgb ushr ALPHA_SHIFT) in 1 until CHANNEL_MAX) 1 else 0

    /**
     * Backdrop colour the shader samples at device pixel ([x], [y]) when the backdrop starts at
     * [origin]: the pixel centre is mapped back to the backdrop and floored, as nearest sampling does.
     */
    fun backdropAt(
        x: Int,
        y: Int,
        origin: PointF,
        cellPx: Int,
    ): Int =
        TransparencyBackdrop.colorAt(
            floor(x + HALF_PIXEL - origin.x).toInt(),
            floor(y + HALF_PIXEL - origin.y).toInt(),
            cellPx,
        )

    fun assertArgbNear(
        message: String,
        expected: Int,
        actual: Int,
        tolerance: Int,
    ) {
        val near =
            CHANNEL_SHIFTS.all { shift ->
                abs(((expected ushr shift) and CHANNEL_MAX) - ((actual ushr shift) and CHANNEL_MAX)) <= tolerance
            }
        assertTrue("$message: expected ${hex(expected)}, was ${hex(actual)} (tolerance $tolerance)", near)
    }

    /** Bitmap pixels read back: exact where [expected] is opaque or transparent, else within 1. */
    fun assertPixelsNear(
        message: String,
        expected: IntArray,
        actual: IntArray,
    ) {
        assertTrue("$message: ${expected.size} pixels expected, ${actual.size} read", expected.size == actual.size)
        expected.indices.forEach { index ->
            assertArgbNear("$message pixel $index", expected[index], actual[index], toleranceFor(expected[index]))
        }
    }

    private fun sourceOver(
        argb: Int,
        backdropArgb: Int,
        alpha: Int,
    ): Int {
        var result = CHANNEL_MAX shl ALPHA_SHIFT
        COLOUR_SHIFTS.forEach { shift ->
            val source = (argb ushr shift) and CHANNEL_MAX
            val backdrop = (backdropArgb ushr shift) and CHANNEL_MAX
            val channel = (source * alpha + backdrop * (CHANNEL_MAX - alpha) + ROUNDING_BIAS) / CHANNEL_MAX
            result = result or (channel shl shift)
        }
        return result
    }

    private fun hex(argb: Int): String = "0x%08X".format(argb)

    private const val ALPHA_SHIFT: Int = 24
    private const val CHANNEL_MAX: Int = 0xff
    private const val ROUNDING_BIAS: Int = 127
    private const val HALF_PIXEL: Float = 0.5f
    private val COLOUR_SHIFTS: IntArray = intArrayOf(16, 8, 0)
    private val CHANNEL_SHIFTS: IntArray = intArrayOf(24, 16, 8, 0)
}

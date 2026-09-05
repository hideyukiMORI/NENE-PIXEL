package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class CanvasBitmapProjectionTest {
    @Test
    fun bitmapProjectionPreservesRowMajorArgbDimensionsAndSourceSnapshot() {
        val expected = intArrayOf(OPAQUE_RED, OPAQUE_GREEN, HALF_ALPHA_BLUE, TRANSPARENT_BLACK)
        val source = snapshot(expected)

        val rendered = source.toRenderedBitmap()

        assertEquals(CANVAS_WIDTH, rendered.width)
        assertEquals(CANVAS_HEIGHT, rendered.height)
        val actual = IntArray(expected.size)
        rendered.getPixels(actual, 0, CANVAS_WIDTH, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT)
        assertArrayEquals(expected, actual)
        assertEquals(Revision.initial(), source.revision)
        assertArrayEquals(expected, source.argbPixels())
    }

    @Test
    fun opaqueBitmapProjectionCompositesDisplayAlphaOverCanvasColorWithoutChangingSource() {
        val sourcePixels = intArrayOf(OPAQUE_RED, OPAQUE_GREEN, HALF_ALPHA_BLUE, TRANSPARENT_BLACK)
        val source = snapshot(sourcePixels)

        val rendered = source.toOpaqueRenderedBitmap(OPAQUE_WHITE)

        val actual = IntArray(sourcePixels.size)
        rendered.getPixels(actual, 0, CANVAS_WIDTH, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT)
        assertArrayEquals(
            intArrayOf(OPAQUE_RED, OPAQUE_GREEN, HALF_ALPHA_BLUE_OVER_WHITE, OPAQUE_WHITE),
            actual,
        )
        assertArrayEquals(sourcePixels, source.argbPixels())
    }

    private fun snapshot(argb: IntArray): PixelSnapshot {
        val size =
            CanvasSize.create(
                CanvasWidth.create(CANVAS_WIDTH).requiredValue(),
                CanvasHeight.create(CANVAS_HEIGHT).requiredValue(),
            )
        return PixelSnapshot
            .create(size, Revision.initial(), argb.map(::pixelColor))
            .requiredValue()
    }

    private fun PixelSnapshot.argbPixels(): IntArray =
        IntArray(size.pixelCount.toInt()) { index ->
            val position = pixelPosition(index % size.width.value, index / size.width.value)
            colorAt(position).requiredValue().argb
        }

    private fun pixelColor(argb: Int): PixelColor =
        PixelColor.create(
            channel(argb ushr RED_SHIFT),
            channel(argb ushr GREEN_SHIFT),
            channel(argb ushr BLUE_SHIFT),
            channel(argb ushr ALPHA_SHIFT),
        )

    private fun channel(value: Int): ColorChannel = ColorChannel.create(value and UBYTE_MASK).requiredValue()

    private val PixelColor.argb: Int
        get() =
            (alpha.value.toInt() shl ALPHA_SHIFT) or
                (red.value.toInt() shl RED_SHIFT) or
                (green.value.toInt() shl GREEN_SHIFT) or
                blue.value.toInt()

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Invalid bitmap projection test fixture: $rejection")
        }

    private companion object {
        const val CANVAS_WIDTH: Int = 2
        const val CANVAS_HEIGHT: Int = 2
        const val UBYTE_MASK: Int = 0xff
        const val ALPHA_SHIFT: Int = 24
        const val RED_SHIFT: Int = 16
        const val GREEN_SHIFT: Int = 8
        const val BLUE_SHIFT: Int = 0
        const val OPAQUE_RED: Int = -0x10000
        const val OPAQUE_GREEN: Int = -0xff0100
        const val HALF_ALPHA_BLUE: Int = -0x7fffff01
        const val TRANSPARENT_BLACK: Int = 0x00000000
        const val HALF_ALPHA_BLUE_OVER_WHITE: Int = -0x808001
        const val OPAQUE_WHITE: Int = -0x1
    }
}

package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class CanvasBitmapProjectionTest {
    @Test
    fun bitmapProjectionResolvesRowMajorIndicesAgainstDefinition() {
        val expected = intArrayOf(OPAQUE_RED, OPAQUE_GREEN, HALF_ALPHA_BLUE, TRANSPARENT_BLACK)
        val source = snapshot(intArrayOf(0, 1, 2, 3))

        val rendered = source.toRenderedBitmap(definition(expected))

        assertEquals(CANVAS_WIDTH, rendered.width)
        assertEquals(CANVAS_HEIGHT, rendered.height)
        val actual = IntArray(expected.size)
        rendered.getPixels(actual, 0, CANVAS_WIDTH, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT)
        assertArrayEquals(expected, actual)
        assertEquals(Revision.initial(), source.revision)
        assertArrayEquals(intArrayOf(0, 1, 2, 3), source.indices())
    }

    @Test
    fun opaqueBitmapProjectionCompositesDisplayAlphaOverCanvasColorWithoutChangingSource() {
        val sourcePixels = intArrayOf(OPAQUE_RED, OPAQUE_GREEN, HALF_ALPHA_BLUE, TRANSPARENT_BLACK)
        val source = snapshot(intArrayOf(0, 1, 2, 3))

        val rendered = source.toOpaqueRenderedBitmap(definition(sourcePixels), OPAQUE_WHITE)

        val actual = IntArray(sourcePixels.size)
        rendered.getPixels(actual, 0, CANVAS_WIDTH, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT)
        assertArrayEquals(
            intArrayOf(OPAQUE_RED, OPAQUE_GREEN, HALF_ALPHA_BLUE_OVER_WHITE, OPAQUE_WHITE),
            actual,
        )
        assertArrayEquals(intArrayOf(0, 1, 2, 3), source.indices())
    }

    @Test
    fun sameSnapshotRendersDifferentlyWhenOnlyThePaletteDefinitionChanges() {
        val source = snapshot(intArrayOf(0))
        val first = source.toRenderedBitmap(definition(intArrayOf(OPAQUE_RED, OPAQUE_GREEN)))
        val second = source.toRenderedBitmap(definition(intArrayOf(OPAQUE_GREEN, OPAQUE_RED)))

        assertEquals(OPAQUE_RED, first.getPixel(0, 0))
        assertEquals(OPAQUE_GREEN, second.getPixel(0, 0))
    }

    @Test
    fun transparentDefaultPreviewPreservesAlphaAndOpaquePreviewCompositesIt() {
        val source = snapshot(intArrayOf(1))
        val transparentDefault = definition(intArrayOf(OPAQUE_RED, TRANSPARENT_BLACK), defaultIndex = 1)

        assertEquals(TRANSPARENT_BLACK, source.toRenderedBitmap(transparentDefault).getPixel(0, 0))
        assertEquals(OPAQUE_WHITE, source.toOpaqueRenderedBitmap(transparentDefault, OPAQUE_WHITE).getPixel(0, 0))
    }

    private fun snapshot(indices: IntArray): PixelSnapshot {
        val edge = if (indices.size == 1) 1 else CANVAS_WIDTH
        val size =
            CanvasSize
                .create(
                    CanvasWidth.create(edge).requiredValue(),
                    CanvasHeight.create(edge).requiredValue(),
                )
        return PixelSnapshot
            .create(size, Revision.initial(), indices.map { PaletteIndex.create(it).requiredValue() })
            .requiredValue()
    }

    private fun PixelSnapshot.indices(): IntArray = copyPackedIndices().map { it.toInt() and UBYTE_MASK }.toIntArray()

    private fun definition(
        argb: IntArray,
        defaultIndex: Int = 0,
    ): PaletteDefinition =
        PaletteDefinition
            .create(
                Palette.create(argb.map(::pixelColor)).requiredValue(),
                PaletteIndex.create(defaultIndex).requiredValue(),
            ).requiredValue()

    private fun pixelColor(argb: Int): PixelColor =
        PixelColor.create(
            channel(argb ushr RED_SHIFT),
            channel(argb ushr GREEN_SHIFT),
            channel(argb ushr BLUE_SHIFT),
            channel(argb ushr ALPHA_SHIFT),
        )

    private fun channel(value: Int): ColorChannel = ColorChannel.create(value and UBYTE_MASK).requiredValue()

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

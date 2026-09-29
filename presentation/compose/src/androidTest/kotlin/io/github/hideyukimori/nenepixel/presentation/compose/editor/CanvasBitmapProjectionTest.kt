package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hideyukimori.nenepixel.core.application.render.DocumentComposite
import io.github.hideyukimori.nenepixel.core.application.render.DocumentCompositeImage
import io.github.hideyukimori.nenepixel.core.application.render.DocumentCompositeRenderResult
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class CanvasBitmapProjectionTest {
    @Test
    fun bitmapProjectionResolvesRowMajorIndicesAgainstDefinition() {
        val expected = intArrayOf(OPAQUE_RED, OPAQUE_GREEN, HALF_ALPHA_BLUE, TRANSPARENT_BLACK)
        val definition = definition(expected)

        val rendered = composite(document(definition, coveredLayer(intArrayOf(0, 1, 2, 3)))).toRenderedBitmap()

        assertEquals(CANVAS_WIDTH, rendered.width)
        assertEquals(CANVAS_HEIGHT, rendered.height)
        assertArrayEquals(expected, rendered.pixels())
    }

    @Test
    fun opaqueBitmapProjectionCompositesDisplayAlphaOverCanvasColor() {
        val definition = definition(intArrayOf(OPAQUE_RED, OPAQUE_GREEN, HALF_ALPHA_BLUE, TRANSPARENT_BLACK))
        val image = composite(document(definition, coveredLayer(intArrayOf(0, 1, 2, 3))))

        val rendered = image.toOpaqueRenderedBitmap(OPAQUE_WHITE)

        assertArrayEquals(
            intArrayOf(OPAQUE_RED, OPAQUE_GREEN, HALF_ALPHA_BLUE_OVER_WHITE, OPAQUE_WHITE),
            rendered.pixels(),
        )
    }

    @Test
    fun sameDocumentRendersDifferentlyWhenOnlyThePaletteDefinitionChanges() {
        val first = definition(intArrayOf(OPAQUE_RED, OPAQUE_GREEN))
        val second = definition(intArrayOf(OPAQUE_GREEN, OPAQUE_RED))
        val source = document(first, coveredLayer(intArrayOf(0, 0, 0, 0)))
        val cache = CommittedBitmapCache()

        assertEquals(OPAQUE_RED, cache.render(source, first, OPAQUE_WHITE).getPixel(0, 0))
        assertEquals(OPAQUE_GREEN, cache.render(source, second, OPAQUE_WHITE).getPixel(0, 0))
    }

    @Test
    fun transparentCoveredColorPreservesAlphaAndOpaqueProjectionCompositesIt() {
        val definition = definition(intArrayOf(OPAQUE_RED, TRANSPARENT_BLACK))
        val image = composite(document(definition, coveredLayer(intArrayOf(1, 1, 1, 1))))

        assertEquals(TRANSPARENT_BLACK, image.toRenderedBitmap().getPixel(0, 0))
        assertEquals(OPAQUE_WHITE, image.toOpaqueRenderedBitmap(OPAQUE_WHITE).getPixel(0, 0))
    }

    /**
     * Bottom: red, red, Empty, Empty. Top: half blue, Empty, half blue, Empty. A hidden opaque
     * green layer covers everything above both. ADR 0030 blend of half blue over opaque red:
     * weights 128*255 = 32640 and 255*127 = 32385 (total 65025), alpha (65025 + 127) / 255 = 255,
     * red (2 * 255 * 32385 + 65025) / 130050 = 127, blue (2 * 255 * 32640 + 65025) / 130050 = 128.
     * Half blue alone stays (0, 0, 255, 128) and becomes 0xFF7F7FFF over white; Empty is white.
     */
    @Test
    fun committedBitmapCompositesVisibleLayersOverTheCanvasColor() {
        val definition = definition(intArrayOf(OPAQUE_RED, HALF_ALPHA_BLUE, OPAQUE_GREEN))
        val source =
            document(
                definition,
                cellLayer(intArrayOf(0, 0, 0, 0), coveredMask = 0b0011, id = 1),
                cellLayer(intArrayOf(1, 0, 1, 0), coveredMask = 0b0101, id = 2),
                coveredLayer(intArrayOf(2, 2, 2, 2), id = 3, visibility = LayerVisibility.Hidden),
            )

        val rendered = CommittedBitmapCache().render(source, definition, OPAQUE_WHITE)

        assertArrayEquals(
            intArrayOf(HALF_BLUE_OVER_RED, OPAQUE_RED, HALF_ALPHA_BLUE_OVER_WHITE, OPAQUE_WHITE),
            rendered.pixels(),
        )
    }

    @Test
    fun emptyPixelsShowTheCanvasColor() {
        val definition = definition(intArrayOf(OPAQUE_RED, OPAQUE_WHITE))
        val source = document(definition, cellLayer(intArrayOf(0, 0, 0, 0), coveredMask = 0, id = 1))

        val rendered = CommittedBitmapCache().render(source, definition, CANVAS_COLOR)

        assertArrayEquals(IntArray(PIXEL_COUNT) { CANVAS_COLOR }, rendered.pixels())
    }

    @Test
    fun hiddenLayersAreNotDrawn() {
        val definition = definition(intArrayOf(OPAQUE_RED, OPAQUE_GREEN))
        val source =
            document(
                definition,
                coveredLayer(intArrayOf(0, 0, 0, 0), id = 1),
                coveredLayer(intArrayOf(1, 1, 1, 1), id = 2, visibility = LayerVisibility.Hidden),
            )

        val rendered = CommittedBitmapCache().render(source, definition, OPAQUE_WHITE)

        assertArrayEquals(IntArray(PIXEL_COUNT) { OPAQUE_RED }, rendered.pixels())
    }

    @Test
    fun cacheIsRebuiltWhenTheDocumentReferenceChanges() {
        val definition = definition(intArrayOf(OPAQUE_RED, OPAQUE_GREEN))
        val first = document(definition, coveredLayer(intArrayOf(0, 0, 0, 0)))
        val equalCopy = document(definition, coveredLayer(intArrayOf(0, 0, 0, 0)))
        val changed = document(definition, coveredLayer(intArrayOf(1, 1, 1, 1)))
        val cache = CommittedBitmapCache()

        val initial = cache.render(first, definition, OPAQUE_WHITE)

        assertSame(initial, cache.render(first, definition, OPAQUE_WHITE))
        assertEquals(first, equalCopy)
        assertNotSame(initial, cache.render(equalCopy, definition, OPAQUE_WHITE))
        assertArrayEquals(
            IntArray(PIXEL_COUNT) { OPAQUE_GREEN },
            cache.render(changed, definition, OPAQUE_WHITE).pixels(),
        )
    }

    private fun composite(document: DocumentState): DocumentCompositeImage =
        when (val result = DocumentComposite.render(document, document.definition)) {
            is DocumentCompositeRenderResult.Rendered -> result.image
            DocumentCompositeRenderResult.IndexOutsidePalette -> error("Invalid bitmap projection test fixture")
        }

    private fun Bitmap.pixels(): IntArray {
        val actual = IntArray(width * height)
        getPixels(actual, 0, width, 0, 0, width, height)
        return actual
    }

    private fun document(
        definition: PaletteDefinition,
        vararg layers: Layer,
    ): DocumentState =
        DocumentState
            .createLayered(
                DocumentId.create(DOCUMENT_ID).requiredValue(),
                Revision.initial(),
                definition,
                layers.toList(),
            ).requiredValue()

    private fun coveredLayer(
        indices: IntArray,
        id: Int = 1,
        visibility: LayerVisibility = LayerVisibility.Visible,
    ): Layer = cellLayer(indices, coveredMask = FULL_COVERAGE, id = id, visibility = visibility)

    private fun cellLayer(
        indices: IntArray,
        coveredMask: Int,
        id: Int,
        visibility: LayerVisibility = LayerVisibility.Visible,
    ): Layer {
        val snapshot =
            PixelSnapshot
                .createPackedCells(
                    canvasSize(),
                    ByteArray(indices.size) { indices[it].toByte() },
                    byteArrayOf(coveredMask.toByte()),
                ).requiredValue()
        return Layer.create(LayerId.create(id).requiredValue(), LayerName.empty, visibility, snapshot)
    }

    private fun canvasSize(): CanvasSize =
        CanvasSize.create(
            CanvasWidth.create(CANVAS_WIDTH).requiredValue(),
            CanvasHeight.create(CANVAS_HEIGHT).requiredValue(),
        )

    private fun definition(argb: IntArray): PaletteDefinition =
        PaletteDefinition
            .create(
                Palette.create(argb.map(::pixelColor)).requiredValue(),
                PaletteIndex.create(0).requiredValue(),
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
        const val PIXEL_COUNT: Int = CANVAS_WIDTH * CANVAS_HEIGHT
        const val FULL_COVERAGE: Int = 0b1111
        const val DOCUMENT_ID: String = "11111111111111111111111111111111"
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
        const val HALF_BLUE_OVER_RED: Int = -0x80ff80
        const val CANVAS_COLOR: Int = -0xefdfd0
        const val OPAQUE_WHITE: Int = -0x1
    }
}

package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class NewDocumentRequestTest {
    @Test
    fun `minimum maximum-minus-one and maximum dimensions create typed requests`() {
        val minimum = created(NewDocumentRequest.create("1", "1"))
        val belowMaximum = created(NewDocumentRequest.create("255", "255"))
        val maximum = created(NewDocumentRequest.create("256", "256"))

        assertEquals(PixelLimits.MIN_CANVAS_AXIS, minimum.canvas.width.value)
        assertEquals(PixelLimits.MIN_CANVAS_AXIS, minimum.canvas.height.value)
        assertEquals(PixelLimits.MAX_CANVAS_AXIS - 1, belowMaximum.canvas.width.value)
        assertEquals(PixelLimits.MAX_CANVAS_AXIS - 1, belowMaximum.canvas.height.value)
        assertEquals(PixelLimits.MAX_CANVAS_AXIS, maximum.canvas.width.value)
        assertEquals(PixelLimits.MAX_CANVAS_AXIS, maximum.canvas.height.value)
        assertEquals(PixelLimits.MAX_CANVAS_PIXELS.toLong(), maximum.canvas.pixelCount)
    }

    @Test
    fun `maximum-plus-one rejects each dimension with the canonical supported range`() {
        val width = rejected(NewDocumentRequest.create("257", "1"))
        val height = rejected(NewDocumentRequest.create("1", "257"))

        assertOutsideRange(width, NewDocumentDimension.Width, PixelLimits.MAX_CANVAS_AXIS + 1)
        assertOutsideRange(height, NewDocumentDimension.Height, PixelLimits.MAX_CANVAS_AXIS + 1)
    }

    @Test
    fun `integer overflow is distinct from non-decimal and missing input`() {
        val widthOverflow = rejected(NewDocumentRequest.create("2147483648", "1"))
        val heightOverflow = rejected(NewDocumentRequest.create("1", "-2147483649"))
        val nonDecimal = rejected(NewDocumentRequest.create("16px", "1"))
        val missing = rejected(NewDocumentRequest.create("1", "   "))

        assertEquals(NewDocumentRejection.IntegerOverflow(NewDocumentDimension.Width), widthOverflow)
        assertEquals(NewDocumentRejection.IntegerOverflow(NewDocumentDimension.Height), heightOverflow)
        assertEquals(NewDocumentRejection.NotDecimalInteger(NewDocumentDimension.Width), nonDecimal)
        assertEquals(NewDocumentRejection.Required(NewDocumentDimension.Height), missing)
    }

    @Test
    fun `surrounding whitespace is normalized before validation`() {
        val request = created(NewDocumentRequest.create(" 16 ", "\t8\n"))

        assertEquals(16, request.canvas.width.value)
        assertEquals(8, request.canvas.height.value)
    }

    private fun assertOutsideRange(
        rejection: NewDocumentRejection,
        dimension: NewDocumentDimension,
        attemptedValue: Int,
    ) {
        val outside =
            assertInstanceOf(
                NewDocumentRejection.OutsideSupportedRange::class.java,
                rejection,
            )
        assertEquals(dimension, outside.dimension)
        assertEquals(attemptedValue, outside.attemptedValue)
        assertEquals(PixelLimits.MIN_CANVAS_AXIS, outside.minimum)
        assertEquals(PixelLimits.MAX_CANVAS_AXIS, outside.maximum)
    }

    private fun created(result: NewDocumentRequestResult): NewDocumentRequest =
        assertInstanceOf(NewDocumentRequestResult.Created::class.java, result).request

    private fun rejected(result: NewDocumentRequestResult): NewDocumentRejection =
        assertInstanceOf(NewDocumentRequestResult.Rejected::class.java, result).rejection
}

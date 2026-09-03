package io.github.hideyukimori.nenepixel.core.domain.geometry

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.canvasSize
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.pixelPosition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class GeometryValueTest {
    @Test
    fun `dimensions enforce supported axis boundaries and bounded area`() {
        val maximumSize = canvasSize(PixelLimits.MAX_CANVAS_AXIS, PixelLimits.MAX_CANVAS_AXIS)

        assertEquals(PixelLimits.MAX_CANVAS_PIXELS.toLong(), maximumSize.pixelCount)
        assertEquals(PixelLimits.MAX_CANVAS_AXIS - 1, created(CanvasWidth.create(255)).value)
        assertEquals(PixelLimits.MAX_CANVAS_AXIS, created(CanvasHeight.create(256)).value)
        assertInstanceOf(
            DomainValueRejection.NonPositiveCanvasWidth::class.java,
            rejected(CanvasWidth.create(0)),
        )
        assertInstanceOf(
            DomainValueRejection.NonPositiveCanvasHeight::class.java,
            rejected(CanvasHeight.create(-1)),
        )
        assertEquals(
            DomainValueRejection.CanvasWidthAboveSupportedMaximum(257, PixelLimits.MAX_CANVAS_AXIS),
            rejected(CanvasWidth.create(257)),
        )
        assertEquals(
            DomainValueRejection.CanvasHeightAboveSupportedMaximum(257, PixelLimits.MAX_CANVAS_AXIS),
            rejected(CanvasHeight.create(257)),
        )
    }

    @Test
    fun `axes reject negatives and positions have value equality`() {
        val first = pixelPosition(2, 3)
        val same = PixelPosition.create(created(PixelX.create(2)), created(PixelY.create(3)))

        assertEquals(first, same)
        assertInstanceOf(DomainValueRejection.NegativePixelX::class.java, rejected(PixelX.create(-1)))
        assertInstanceOf(DomainValueRejection.NegativePixelY::class.java, rejected(PixelY.create(-1)))
    }

    @Test
    fun `canvas containment includes zero and excludes right and bottom bounds`() {
        val canvas = canvasSize(4, 3)

        assertTrue(canvas.contains(pixelPosition(0, 0)))
        assertTrue(canvas.contains(pixelPosition(3, 2)))
        assertFalse(canvas.contains(pixelPosition(4, 2)))
        assertFalse(canvas.contains(pixelPosition(3, 3)))
    }

    @Test
    fun `region is half open canvas contained and structurally equal`() {
        val canvas = canvasSize(4, 3)
        val region = created(PixelRegion.create(canvas, pixelPosition(1, 1), canvasSize(3, 2)))
        val same = created(PixelRegion.create(canvas, pixelPosition(1, 1), canvasSize(3, 2)))

        assertEquals(region, same)
        assertEquals(region.hashCode(), same.hashCode())
        assertTrue(region.contains(pixelPosition(1, 1)))
        assertTrue(region.contains(pixelPosition(3, 2)))
        assertFalse(region.contains(pixelPosition(4, 2)))
        assertFalse(region.contains(pixelPosition(3, 3)))
    }

    @Test
    fun `region outside maximum supported canvas is rejected`() {
        val canvas = canvasSize(PixelLimits.MAX_CANVAS_AXIS, PixelLimits.MAX_CANVAS_AXIS)
        val rejection =
            rejected(
                PixelRegion.create(
                    canvas = canvas,
                    origin = pixelPosition(PixelLimits.MAX_CANVAS_AXIS - 1, PixelLimits.MAX_CANVAS_AXIS - 1),
                    size = canvasSize(2, 2),
                ),
            )

        assertInstanceOf(DomainValueRejection.PixelRegionOutsideCanvas::class.java, rejection)
    }
}

package io.github.hideyukimori.nenepixel.core.domain.geometry

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.canvasSize
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.pixelPosition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class GeometryValueTest {
    @Test
    fun `dimensions are positive and pixel count uses Long arithmetic`() {
        val maximumSize = canvasSize(Int.MAX_VALUE, Int.MAX_VALUE)

        assertEquals(Int.MAX_VALUE.toLong() * Int.MAX_VALUE.toLong(), maximumSize.pixelCount)
        assertInstanceOf(
            DomainValueRejection.NonPositiveCanvasWidth::class.java,
            rejected(CanvasWidth.create(0)),
        )
        assertInstanceOf(
            DomainValueRejection.NonPositiveCanvasHeight::class.java,
            rejected(CanvasHeight.create(-1)),
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
    fun `region outside canvas is rejected without integer overflow`() {
        val canvas = canvasSize(Int.MAX_VALUE, Int.MAX_VALUE)
        val rejection =
            rejected(
                PixelRegion.create(
                    canvas = canvas,
                    origin = pixelPosition(Int.MAX_VALUE, Int.MAX_VALUE),
                    size = canvas,
                ),
            )

        assertInstanceOf(DomainValueRejection.PixelRegionOutsideCanvas::class.java, rejection)
    }
}

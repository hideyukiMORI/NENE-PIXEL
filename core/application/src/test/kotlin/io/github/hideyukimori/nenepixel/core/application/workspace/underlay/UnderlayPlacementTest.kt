package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class UnderlayPlacementTest {
    @Test
    fun `fitted scales a landscape and a portrait image to the square canvas and centres it`() {
        assertPlacement(0.0, 64.0, 1.28, UnderlayPlacement.fitted(image(200, 100), canvas(256, 256)))
        assertPlacement(64.0, 0.0, 1.28, UnderlayPlacement.fitted(image(100, 200), canvas(256, 256)))
    }

    @Test
    fun `fitted scales a square image down and across wide and tall canvases`() {
        assertPlacement(0.0, 0.0, 0.25, UnderlayPlacement.fitted(image(64, 64), canvas(16, 16)))
        assertPlacement(64.0, 0.0, 2.0, UnderlayPlacement.fitted(image(64, 64), canvas(256, 128)))
        assertPlacement(0.0, 96.0, 0.64, UnderlayPlacement.fitted(image(200, 100), canvas(128, 256)))
    }

    @Test
    fun `fitted handles extremely thin images`() {
        assertPlacement(127.875, 0.0, 0.25, UnderlayPlacement.fitted(image(1, 1024), canvas(256, 256)))
        assertPlacement(0.0, 7.9921875, 1.0 / 64.0, UnderlayPlacement.fitted(image(1024, 1), canvas(16, 16)))
    }

    @Test
    fun `create leaves every fitted placement unchanged`() {
        for ((imageWidth, imageHeight) in IMAGE_SIZES) {
            for ((canvasWidth, canvasHeight) in CANVAS_SIZES) {
                val image = image(imageWidth, imageHeight)
                val canvas = canvas(canvasWidth, canvasHeight)
                val fitted = UnderlayPlacement.fitted(image, canvas)

                assertEquals(
                    fitted,
                    UnderlayPlacement.create(
                        RequestedUnderlayPlacement(fitted.left, fitted.top, fitted.scale),
                        UnderlaySource(image, canvas),
                    ),
                    "image ${imageWidth}x$imageHeight on canvas ${canvasWidth}x$canvasHeight",
                )
            }
        }
    }

    @Test
    fun `scale is clamped between an eighth of the canvas and sixteen canvases on the long side`() {
        val image = image(64, 64)
        val canvas = canvas(256, 256)

        assertEquals(
            0.5,
            UnderlayPlacement.create(RequestedUnderlayPlacement(0.0, 0.0, 0.1), UnderlaySource(image, canvas)).scale,
            EPSILON,
        )
        assertEquals(
            64.0,
            UnderlayPlacement.create(RequestedUnderlayPlacement(0.0, 0.0, 1000.0), UnderlaySource(image, canvas)).scale,
            EPSILON,
        )
        assertEquals(
            2.0,
            UnderlayPlacement.create(RequestedUnderlayPlacement(0.0, 0.0, 2.0), UnderlaySource(image, canvas)).scale,
            EPSILON,
        )
    }

    @Test
    fun `the lower scale limit is never stricter than the fitted size`() {
        val image = image(1, 1024)
        val canvas = canvas(256, 16)

        assertEquals(
            1.0 / 64.0,
            UnderlayPlacement.create(RequestedUnderlayPlacement(0.0, 0.0, 0.0001), UnderlaySource(image, canvas)).scale,
            EPSILON,
        )
    }

    @Test
    fun `a non-finite or non-positive scale falls back to the fitted scale`() {
        val image = image(64, 64)
        val canvas = canvas(256, 256)

        for (scale in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -1.0)) {
            assertEquals(
                4.0,
                UnderlayPlacement
                    .create(
                        RequestedUnderlayPlacement(0.0, 0.0, scale),
                        UnderlaySource(image, canvas),
                    ).scale,
                EPSILON,
                "scale $scale",
            )
        }
    }

    @Test
    fun `left and top keep at least one pixel of overlap with the document`() {
        val image = image(64, 64)
        val canvas = canvas(256, 256)

        assertPlacement(
            -63.0,
            255.0,
            1.0,
            UnderlayPlacement.create(RequestedUnderlayPlacement(-1000.0, 5000.0, 1.0), UnderlaySource(image, canvas)),
        )
        assertPlacement(
            255.0,
            -63.0,
            1.0,
            UnderlayPlacement.create(RequestedUnderlayPlacement(5000.0, -1000.0, 1.0), UnderlaySource(image, canvas)),
        )
        assertPlacement(
            10.5,
            20.25,
            1.0,
            UnderlayPlacement.create(RequestedUnderlayPlacement(10.5, 20.25, 1.0), UnderlaySource(image, canvas)),
        )
    }

    @Test
    fun `an image thinner than one pixel keeps its whole width over the document`() {
        val image = image(1, 1024)
        val canvas = canvas(256, 256)

        assertPlacement(
            0.0,
            -255.0,
            0.25,
            UnderlayPlacement.create(RequestedUnderlayPlacement(-10.0, -1000.0, 0.25), UnderlaySource(image, canvas)),
        )
        assertPlacement(
            255.75,
            255.0,
            0.25,
            UnderlayPlacement.create(RequestedUnderlayPlacement(1000.0, 1000.0, 0.25), UnderlaySource(image, canvas)),
        )
    }

    @Test
    fun `non-finite left and top fall back to the fitted offsets`() {
        val image = image(200, 100)
        val canvas = canvas(256, 256)
        val fitted = UnderlayPlacement.fitted(image, canvas)

        val nanLeft =
            UnderlayPlacement.create(
                RequestedUnderlayPlacement(Double.NaN, Double.POSITIVE_INFINITY, fitted.scale),
                UnderlaySource(image, canvas),
            )
        val allNonFinite =
            UnderlayPlacement.create(
                RequestedUnderlayPlacement(Double.NEGATIVE_INFINITY, Double.NaN, Double.NaN),
                UnderlaySource(image, canvas),
            )

        assertEquals(fitted, nanLeft)
        assertEquals(fitted, allNonFinite)
    }

    @Test
    fun `equality compares the values`() {
        val image = image(64, 64)
        val canvas = canvas(256, 256)
        val first = UnderlayPlacement.create(RequestedUnderlayPlacement(1.0, 2.0, 3.0), UnderlaySource(image, canvas))
        val second = UnderlayPlacement.create(RequestedUnderlayPlacement(1.0, 2.0, 3.0), UnderlaySource(image, canvas))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(
            first,
            UnderlayPlacement.create(RequestedUnderlayPlacement(1.0, 2.0, 4.0), UnderlaySource(image, canvas)),
        )
        assertEquals("UnderlayPlacement(left=1.0, top=2.0, scale=3.0)", first.toString())
    }

    @Test
    fun `negative zero offsets equal positive zero offsets and share the hash code`() {
        val image = image(64, 64)
        val canvas = canvas(256, 256)
        val negative =
            UnderlayPlacement.create(
                RequestedUnderlayPlacement(-0.0, -0.0, 1.0),
                UnderlaySource(image, canvas),
            )
        val positive =
            UnderlayPlacement.create(
                RequestedUnderlayPlacement(0.0, 0.0, 1.0),
                UnderlaySource(image, canvas),
            )

        assertEquals(positive, negative)
        assertEquals(positive.hashCode(), negative.hashCode())
        assertEquals(0.0.toRawBits(), negative.left.toRawBits())
        assertEquals(0.0.toRawBits(), negative.top.toRawBits())
    }

    private fun assertPlacement(
        left: Double,
        top: Double,
        scale: Double,
        placement: UnderlayPlacement,
    ) {
        assertEquals(left, placement.left, EPSILON, "left")
        assertEquals(top, placement.top, EPSILON, "top")
        assertEquals(scale, placement.scale, EPSILON, "scale")
    }

    private fun canvas(
        width: Int,
        height: Int,
    ): CanvasSize = ApplicationTestValues.canvas(width, height)

    private fun image(
        width: Int,
        height: Int,
    ): ReferenceImage {
        val result = ReferenceImage.create(width, height, IntArray(width * height))
        check(result is ReferenceImageResult.Created) { "expected Created, got $result" }
        return result.image
    }

    private companion object {
        /** Allowed error for computed coordinates and scales, in document pixels. */
        const val EPSILON: Double = 1e-9

        val IMAGE_SIZES: List<Pair<Int, Int>> =
            listOf(200 to 100, 100 to 200, 64 to 64, 1 to 1024, 1024 to 1)

        val CANVAS_SIZES: List<Pair<Int, Int>> =
            listOf(256 to 256, 16 to 16, 256 to 64)
    }
}

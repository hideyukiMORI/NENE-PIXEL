package io.github.hideyukimori.nenepixel.core.domain.selection

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PixelOrientationTest {
    @Test
    fun `all 64 compositions operate on the displayed non-square rectangle in order`() {
        val original = listOf(listOf(1, 2, 3), listOf(4, 5, 6))
        PixelOrientation.entries.forEach { current ->
            PixelOrientation.entries.forEach { next ->
                assertEquals(
                    orient(orient(original, current), next),
                    orient(original, current.then(next)),
                    "$current then $next",
                )
            }
        }
        assertEquals(
            PixelOrientation.Transverse,
            PixelOrientation.FlipHorizontal.then(PixelOrientation.RotateClockwise),
        )
        assertEquals(PixelOrientation.Transpose, PixelOrientation.RotateClockwise.then(PixelOrientation.FlipHorizontal))
    }

    @Test
    fun `four rotations and two identical reflections return Identity`() {
        var orientation = PixelOrientation.Identity
        repeat(4) { orientation = orientation.then(PixelOrientation.RotateClockwise) }
        assertEquals(PixelOrientation.Identity, orientation)
        listOf(PixelOrientation.FlipHorizontal, PixelOrientation.FlipVertical).forEach {
            assertEquals(PixelOrientation.Identity, it.then(it))
        }
    }

    @Test
    fun `axis swaps preserve validated extreme dimensions and pixel count`() {
        val size = CanvasSize.create(value(CanvasWidth.create(256)), value(CanvasHeight.create(1)))
        val swapping =
            setOf(
                PixelOrientation.RotateClockwise,
                PixelOrientation.RotateCounterClockwise,
                PixelOrientation.Transpose,
                PixelOrientation.Transverse,
            )
        PixelOrientation.entries.forEach { orientation ->
            val oriented = orientation.orientedSize(size)
            assertEquals(orientation in swapping, orientation.swapsAxes)
            assertEquals(if (orientation in swapping) 1 else 256, oriented.width.value)
            assertEquals(if (orientation in swapping) 256 else 1, oriented.height.value)
            assertEquals(size.pixelCount, oriented.pixelCount)
        }
    }

    // An independent row/column oracle, rather than the production orientation's signed matrix.
    private fun orient(
        rows: List<List<Int>>,
        orientation: PixelOrientation,
    ): List<List<Int>> =
        when (orientation) {
            PixelOrientation.Identity -> rows
            PixelOrientation.RotateClockwise -> transpose(rows.reversed())
            PixelOrientation.HalfTurn -> rows.reversed().map { it.reversed() }
            PixelOrientation.RotateCounterClockwise -> transpose(rows).reversed()
            PixelOrientation.FlipHorizontal -> rows.map { it.reversed() }
            PixelOrientation.FlipVertical -> rows.reversed()
            PixelOrientation.Transpose -> transpose(rows)
            PixelOrientation.Transverse -> transpose(rows.reversed().map { it.reversed() })
        }

    private fun transpose(rows: List<List<Int>>): List<List<Int>> =
        rows.first().indices.map { column -> rows.map { it[column] } }

    private fun <T> value(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid fixture: ${result.rejection}")
        }
}

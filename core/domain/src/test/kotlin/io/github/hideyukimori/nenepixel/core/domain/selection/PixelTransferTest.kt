package io.github.hideyukimori.nenepixel.core.domain.selection

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelPlacement
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class PixelTransferTest {
    @Test
    fun `valid modes retain canvas source and exact destination geometry`() {
        val canvas = size(5, 4)
        val source = value(PixelRegion.create(canvas, position(0, 0), size(2, 2)))
        PixelTransferMode.entries.forEach { mode ->
            val transfer = value(PixelTransfer.create(canvas, source, PixelPlacement.create(position(3, 2)), mode))
            assertEquals(canvas, transfer.canvas)
            assertEquals(source, transfer.source)
            assertEquals(position(3, 2), transfer.destination)
            assertEquals(size(2, 2), transfer.destinationRegion.size)
            assertEquals(mode, transfer.mode)
            assertEquals(
                transfer,
                value(PixelTransfer.create(canvas, source, PixelPlacement.create(position(3, 2)), mode)),
            )
        }
    }

    @Test
    fun `destination full fit is rejected instead of silently clamped`() {
        val canvas = size(5, 4)
        val source = value(PixelRegion.create(canvas, position(0, 0), size(2, 2)))
        listOf(position(4, 2), position(3, 3)).forEach { destination ->
            assertInstanceOf(
                DomainValueResult.Rejected::class.java,
                PixelTransfer.create(canvas, source, PixelPlacement.create(destination), PixelTransferMode.Move),
            )
        }
    }

    @Test
    fun `source made on another canvas must also fit the declared canvas`() {
        val large = size(6, 6)
        val source = value(PixelRegion.create(large, position(4, 4), size(2, 2)))
        assertInstanceOf(
            DomainValueResult.Rejected::class.java,
            PixelTransfer.create(size(5, 5), source, PixelPlacement.create(position(0, 0)), PixelTransferMode.Copy),
        )
    }

    @Test
    fun `oriented target bounds reject edges and impossible swapped axes`() {
        val canvas = size(5, 4)
        val source = value(PixelRegion.create(canvas, position(0, 0), size(3, 2)))
        val rotated = PixelPlacement.create(position(3, 1), PixelOrientation.RotateClockwise)
        val transfer = value(PixelTransfer.create(canvas, source, rotated, PixelTransferMode.Move))
        assertEquals(size(2, 3), transfer.destinationRegion.size)
        assertEquals(PixelOrientation.RotateClockwise, transfer.orientation)
        assertInstanceOf(
            DomainValueResult.Rejected::class.java,
            PixelTransfer.create(
                canvas,
                source,
                PixelPlacement.create(position(3, 2), rotated.orientation),
                transfer.mode,
            ),
        )
        val narrow = size(256, 1)
        val full = value(PixelRegion.create(narrow, position(0, 0), narrow))
        assertInstanceOf(
            DomainValueResult.Rejected::class.java,
            PixelTransfer.create(
                narrow,
                full,
                PixelPlacement.create(position(0, 0), rotated.orientation),
                transfer.mode,
            ),
        )
    }

    @Test
    fun `identity combines origin and orientation independently of mode and equality retains orientation`() {
        val canvas = size(4, 4)
        val source = value(PixelRegion.create(canvas, position(1, 1), size(2, 2)))
        PixelTransferMode.entries.forEach { mode ->
            val identity = value(PixelTransfer.create(canvas, source, PixelPlacement.create(source.origin), mode))
            assertEquals(true, identity.isIdentity)
            val flip =
                value(
                    PixelTransfer.create(
                        canvas,
                        source,
                        PixelPlacement.create(source.origin, PixelOrientation.FlipHorizontal),
                        mode,
                    ),
                )
            assertEquals(false, flip.isIdentity)
            assertEquals(false, identity == flip)
            assertEquals(
                flip,
                value(
                    PixelTransfer.create(canvas, source, PixelPlacement.create(source.origin, flip.orientation), mode),
                ),
            )
            assertEquals(
                flip.hashCode(),
                value(
                    PixelTransfer.create(canvas, source, PixelPlacement.create(source.origin, flip.orientation), mode),
                ).hashCode(),
            )
            val displaced = value(PixelTransfer.create(canvas, source, PixelPlacement.create(position(2, 1)), mode))
            assertEquals(false, displaced.isIdentity)
        }
    }

    private fun size(
        width: Int,
        height: Int,
    ): CanvasSize = CanvasSize.create(value(CanvasWidth.create(width)), value(CanvasHeight.create(height)))

    private fun position(
        x: Int,
        y: Int,
    ): PixelPosition = PixelPosition.create(value(PixelX.create(x)), value(PixelY.create(y)))

    private fun <T> value(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid fixture: ${result.rejection}")
        }
}

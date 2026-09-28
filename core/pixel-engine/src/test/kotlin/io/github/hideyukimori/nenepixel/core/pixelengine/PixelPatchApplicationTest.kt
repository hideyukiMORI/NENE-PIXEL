package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.black
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.green
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.position
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.red
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchAssertions.applicationRejected
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchAssertions.applied
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchAssertions.created
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class PixelPatchApplicationTest {
    @Test
    fun `apply changes only named pixels`() {
        val canvas = canvas(3, 1)
        val original = snapshot(canvas, pixels = listOf(black, green, black))
        val patch =
            created(
                PixelPatch.create(
                    canvas,
                    changes =
                        listOf(
                            PixelChange.create(position(0, 0), PixelCell.Covered(black), PixelCell.Covered(red)),
                            PixelChange.create(position(2, 0), PixelCell.Covered(black), PixelCell.Covered(green)),
                        ),
                ),
            )

        val result = applied(patch.applyTo(original))

        assertEquals(PixelCell.Covered(red), result.color(position(0, 0)))
        assertEquals(PixelCell.Covered(green), result.color(position(1, 0)))
        assertEquals(PixelCell.Covered(green), result.color(position(2, 0)))
    }

    @Test
    fun `inverse application restores exact snapshot`() {
        val original = snapshot(canvas(2, 1))
        val patch =
            created(
                PixelPatch.create(
                    original.size,
                    listOf(PixelChange.create(position(1, 0), PixelCell.Covered(black), PixelCell.Covered(red))),
                ),
            )

        val changed = applied(patch.applyTo(original))
        val restored = applied(patch.inverse().applyTo(changed))

        assertEquals(original, restored)
    }

    @Test
    fun `unsigned byte boundary survives packed apply and inverse exactly`() {
        val source = PixelEngineTestValues.index(0)
        val target = PixelEngineTestValues.index(255)
        val original = snapshot(canvas(1, 1), pixels = listOf(source))
        val patch =
            created(
                PixelPatch.create(
                    original.size,
                    listOf(PixelChange.create(position(0, 0), PixelCell.Covered(source), PixelCell.Covered(target))),
                ),
            )

        val changed = applied(patch.applyTo(original))
        val restored = applied(patch.inverse().applyTo(changed))

        assertEquals(PixelCell.Covered(target), changed.color(position(0, 0)))
        assertEquals(original, restored)
    }

    @Test
    fun `before conflict rejects atomically and leaves source unchanged`() {
        val original = snapshot(canvas(2, 1), pixels = listOf(black, green))
        val patch =
            created(
                PixelPatch.create(
                    original.size,
                    listOf(
                        PixelChange.create(position(0, 0), PixelCell.Covered(black), PixelCell.Covered(red)),
                        PixelChange.create(position(1, 0), PixelCell.Covered(black), PixelCell.Covered(red)),
                    ),
                ),
            )

        assertInstanceOf(
            PixelPatchApplicationRejection.BeforeValueMismatch::class.java,
            applicationRejected(patch.applyTo(original)),
        )
        assertEquals(PixelCell.Covered(black), original.color(position(0, 0)))
        assertEquals(PixelCell.Covered(green), original.color(position(1, 0)))
    }

    @Test
    fun `canvas mismatch is rejected`() {
        val patch =
            created(
                PixelPatch.create(
                    canvas(1, 1),
                    listOf(PixelChange.create(position(0, 0), PixelCell.Covered(black), PixelCell.Covered(red))),
                ),
            )

        assertInstanceOf(
            PixelPatchApplicationRejection.CanvasMismatch::class.java,
            applicationRejected(patch.applyTo(snapshot(canvas(2, 1)))),
        )
    }

    @Test
    fun `repeated identical application is deterministic`() {
        val original = snapshot(canvas(1, 1))
        val patch =
            created(
                PixelPatch.create(
                    original.size,
                    listOf(PixelChange.create(position(0, 0), PixelCell.Covered(black), PixelCell.Covered(red))),
                ),
            )

        assertEquals(applied(patch.applyTo(original)), applied(patch.applyTo(original)))
    }

    private fun io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot.color(
        position: io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition,
    ): PixelCell =
        when (val result = cellAt(position)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Test position was rejected: ${result.rejection}")
        }
}

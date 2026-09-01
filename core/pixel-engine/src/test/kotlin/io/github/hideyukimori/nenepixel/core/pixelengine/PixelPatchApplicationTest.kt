package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.black
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.green
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.position
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.red
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.revision
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
    fun `apply changes only named pixels and advances revision`() {
        val canvas = canvas(3, 1)
        val original = snapshot(canvas, pixels = listOf(black, green, black))
        val patch =
            created(
                PixelPatch.create(
                    canvas,
                    beforeRevision = original.revision,
                    changes =
                        listOf(
                            PixelChange.create(position(0, 0), black, red),
                            PixelChange.create(position(2, 0), black, green),
                        ),
                ),
            )

        val result = applied(patch.applyTo(original))

        assertEquals(red, result.color(position(0, 0)))
        assertEquals(green, result.color(position(1, 0)))
        assertEquals(green, result.color(position(2, 0)))
        assertEquals(revision(1L), result.revision)
    }

    @Test
    fun `inverse application restores exact snapshot and revision`() {
        val original = snapshot(canvas(2, 1))
        val patch =
            created(
                PixelPatch.create(
                    original.size,
                    original.revision,
                    listOf(PixelChange.create(position(1, 0), black, red)),
                ),
            )

        val changed = applied(patch.applyTo(original))
        val restored = applied(patch.inverse().applyTo(changed))

        assertEquals(original, restored)
    }

    @Test
    fun `before conflict rejects atomically and leaves source unchanged`() {
        val original = snapshot(canvas(2, 1), pixels = listOf(black, green))
        val patch =
            created(
                PixelPatch.create(
                    original.size,
                    original.revision,
                    listOf(
                        PixelChange.create(position(0, 0), black, red),
                        PixelChange.create(position(1, 0), black, red),
                    ),
                ),
            )

        assertInstanceOf(
            PixelPatchApplicationRejection.BeforeValueMismatch::class.java,
            applicationRejected(patch.applyTo(original)),
        )
        assertEquals(black, original.color(position(0, 0)))
        assertEquals(green, original.color(position(1, 0)))
        assertEquals(Revision.initial(), original.revision)
    }

    @Test
    fun `canvas and revision mismatch are rejected`() {
        val patch =
            created(
                PixelPatch.create(
                    canvas(1, 1),
                    revision(0L),
                    listOf(PixelChange.create(position(0, 0), black, red)),
                ),
            )

        assertInstanceOf(
            PixelPatchApplicationRejection.CanvasMismatch::class.java,
            applicationRejected(patch.applyTo(snapshot(canvas(2, 1)))),
        )
        assertInstanceOf(
            PixelPatchApplicationRejection.RevisionMismatch::class.java,
            applicationRejected(patch.applyTo(snapshot(canvas(1, 1), revision(1L)))),
        )
    }

    @Test
    fun `repeated identical application is deterministic`() {
        val original = snapshot(canvas(1, 1))
        val patch =
            created(
                PixelPatch.create(
                    original.size,
                    original.revision,
                    listOf(PixelChange.create(position(0, 0), black, red)),
                ),
            )

        assertEquals(applied(patch.applyTo(original)), applied(patch.applyTo(original)))
    }

    private fun io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot.color(
        position: io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition,
    ): io.github.hideyukimori.nenepixel.core.domain.color.PixelColor =
        when (val result = colorAt(position)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Test position was rejected: ${result.rejection}")
        }
}

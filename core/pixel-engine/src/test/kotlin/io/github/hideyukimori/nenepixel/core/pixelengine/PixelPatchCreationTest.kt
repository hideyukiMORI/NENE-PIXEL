package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.black
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.green
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.position
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.red
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.revision
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchAssertions.created
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchAssertions.creationRejected
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class PixelPatchCreationTest {
    @Test
    fun `changes are defensively owned and canonicalized in row major order`() {
        val canvas = canvas(3, 2)
        val later = PixelChange.create(position(2, 1), black, red)
        val earlier = PixelChange.create(position(1, 0), black, green)
        val mutableInput = mutableListOf(later, earlier)
        val fromUnordered = created(PixelPatch.create(canvas, revision(4L), mutableInput))
        val fromCanonical = created(PixelPatch.create(canvas, revision(4L), listOf(earlier, later)))

        mutableInput.clear()

        assertEquals(fromCanonical, fromUnordered)
        assertEquals(fromCanonical.hashCode(), fromUnordered.hashCode())
        assertEquals(2, fromUnordered.changeCount)
        assertEquals(revision(5L), fromUnordered.afterRevision)
    }

    @Test
    fun `empty and unchanged patches are rejected`() {
        val canvas = canvas(1, 1)
        val unchanged = PixelChange.create(position(0, 0), black, black)

        assertEquals(
            PixelPatchCreationRejection.EmptyPatch,
            creationRejected(PixelPatch.create(canvas, revision(0L), emptyList())),
        )
        assertInstanceOf(
            PixelPatchCreationRejection.UnchangedPixel::class.java,
            creationRejected(PixelPatch.create(canvas, revision(0L), listOf(unchanged))),
        )
    }

    @Test
    fun `outside and duplicate positions are rejected`() {
        val canvas = canvas(1, 1)
        val outside = PixelChange.create(position(1, 0), black, red)
        val first = PixelChange.create(position(0, 0), black, red)
        val second = PixelChange.create(position(0, 0), black, green)

        assertInstanceOf(
            PixelPatchCreationRejection.PositionOutsideCanvas::class.java,
            creationRejected(PixelPatch.create(canvas, revision(0L), listOf(outside))),
        )
        assertInstanceOf(
            PixelPatchCreationRejection.DuplicatePosition::class.java,
            creationRejected(PixelPatch.create(canvas, revision(0L), listOf(first, second))),
        )
    }

    @Test
    fun `revision overflow is rejected`() {
        val change = PixelChange.create(position(0, 0), black, red)

        assertEquals(
            PixelPatchCreationRejection.RevisionOverflow,
            creationRejected(PixelPatch.create(canvas(1, 1), revision(Long.MAX_VALUE), listOf(change))),
        )
    }
}

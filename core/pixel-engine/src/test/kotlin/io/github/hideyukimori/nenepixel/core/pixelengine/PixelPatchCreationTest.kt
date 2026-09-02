package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.black
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.green
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.position
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.red
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.region
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
    fun `affected region is the deterministic minimum bound and survives inversion`() {
        val canvas = canvas(4, 3)
        val patch =
            created(
                PixelPatch.create(
                    canvas,
                    revision(0L),
                    listOf(
                        PixelChange.create(position(3, 2), black, red),
                        PixelChange.create(position(1, 0), black, green),
                    ),
                ),
            )
        val expected = region(canvas, position(1, 0), canvas(3, 3))

        assertEquals(expected, patch.affectedRegion)
        assertEquals(expected, patch.inverse().affectedRegion)
    }

    @Test
    fun `maximum square valid corners create an exact non overflowing affected region`() {
        val maximumCanvas = canvas(Int.MAX_VALUE, Int.MAX_VALUE)
        val origin = position(0, 0)
        val oppositeCorner = position(Int.MAX_VALUE - 1, Int.MAX_VALUE - 1)
        val first = PixelChange.create(origin, black, green)
        val last = PixelChange.create(oppositeCorner, black, red)
        val fromUnordered = created(PixelPatch.create(maximumCanvas, revision(0L), listOf(last, first)))
        val fromCanonical = created(PixelPatch.create(maximumCanvas, revision(0L), listOf(first, last)))
        val expectedRegion = region(maximumCanvas, origin, maximumCanvas)

        assertEquals(fromCanonical, fromUnordered)
        assertEquals(2, fromUnordered.changeCount)
        assertEquals(expectedRegion, fromUnordered.affectedRegion)
        assertEquals(expectedRegion, fromUnordered.inverse().affectedRegion)
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
    fun `outside first input is fully materialized before typed validation`() {
        val canvas = canvas(3, 2)
        val outsidePosition = position(3, 0)
        val changes =
            AccessRecordingList(
                listOf(
                    PixelChange.create(outsidePosition, black, red),
                    PixelChange.create(position(2, 1), black, green),
                    PixelChange.create(position(0, 0), black, red),
                ),
            )

        val rejection = creationRejected(PixelPatch.create(canvas, revision(0L), changes))
        val outside =
            assertInstanceOf(
                PixelPatchCreationRejection.PositionOutsideCanvas::class.java,
                rejection,
            )

        assertEquals(setOf(0, 1, 2), changes.accessedIndices.toSet())
        assertEquals(canvas, outside.canvas)
        assertEquals(outsidePosition, outside.position)
    }

    @Test
    fun `maximum square rejects an outside corner before overflowing affected region arithmetic`() {
        val maximumCanvas = canvas(Int.MAX_VALUE, Int.MAX_VALUE)
        val originChange = PixelChange.create(position(0, 0), black, green)
        val outsidePosition = position(Int.MAX_VALUE, Int.MAX_VALUE)
        val outsideChange = PixelChange.create(outsidePosition, black, red)

        val rejection =
            creationRejected(
                PixelPatch.create(
                    maximumCanvas,
                    revision(0L),
                    listOf(outsideChange, originChange),
                ),
            )
        val outside =
            assertInstanceOf(
                PixelPatchCreationRejection.PositionOutsideCanvas::class.java,
                rejection,
            )

        assertEquals(maximumCanvas, outside.canvas)
        assertEquals(outsidePosition, outside.position)
    }

    @Test
    fun `revision overflow is rejected before reading source changes`() {
        val unreadableChanges =
            object : AbstractList<PixelChange>() {
                override val size: Int = 1

                override fun get(index: Int): PixelChange = error("Revision overflow read source change $index.")
            }

        assertEquals(
            PixelPatchCreationRejection.RevisionOverflow,
            creationRejected(PixelPatch.create(canvas(1, 1), revision(Long.MAX_VALUE), unreadableChanges)),
        )
    }

    private class AccessRecordingList<T>(
        private val values: List<T>,
    ) : AbstractList<T>() {
        val accessedIndices: MutableList<Int> = mutableListOf()

        override val size: Int
            get() = values.size

        override fun get(index: Int): T {
            accessedIndices.add(index)
            return values[index]
        }
    }
}

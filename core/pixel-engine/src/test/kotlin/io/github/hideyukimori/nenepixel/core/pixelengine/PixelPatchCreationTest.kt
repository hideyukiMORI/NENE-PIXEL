package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.black
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.green
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.index
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.position
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.red
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.region
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchAssertions.created
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchAssertions.creationRejected
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class PixelPatchCreationTest {
    @Test
    fun `patch rejects an index above packed U8 storage`() {
        val rejection =
            creationRejected(
                PixelPatch.create(
                    canvas(1, 1),
                    listOf(PixelChange.create(position(0, 0), PixelCell.Covered(black), PixelCell.Covered(index(256)))),
                ),
            )

        assertEquals(
            PixelPatchCreationRejection.IndexAboveStorageMaximum(position(0, 0), index(256), 255),
            rejection,
        )
    }

    @Test
    fun `changes are defensively owned and canonicalized in row major order`() {
        val canvas = canvas(3, 2)
        val later = PixelChange.create(position(2, 1), PixelCell.Covered(black), PixelCell.Covered(red))
        val earlier = PixelChange.create(position(1, 0), PixelCell.Covered(black), PixelCell.Covered(green))
        val mutableInput = mutableListOf(later, earlier)
        val fromUnordered = created(PixelPatch.create(canvas, mutableInput))
        val fromCanonical = created(PixelPatch.create(canvas, listOf(earlier, later)))

        mutableInput.clear()

        assertEquals(fromCanonical, fromUnordered)
        assertEquals(fromCanonical.hashCode(), fromUnordered.hashCode())
        assertEquals(2, fromUnordered.changeCount)
    }

    @Test
    fun `affected region is the deterministic minimum bound and survives inversion`() {
        val canvas = canvas(4, 3)
        val patch =
            created(
                PixelPatch.create(
                    canvas,
                    listOf(
                        PixelChange.create(position(3, 2), PixelCell.Covered(black), PixelCell.Covered(red)),
                        PixelChange.create(position(1, 0), PixelCell.Covered(black), PixelCell.Covered(green)),
                    ),
                ),
            )
        val expected = region(canvas, position(1, 0), canvas(3, 3))

        assertEquals(expected, patch.affectedRegion)
        assertEquals(expected, patch.inverse().affectedRegion)
    }

    @Test
    fun `maximum supported square corners create an exact affected region`() {
        val maximumCanvas = canvas(PixelLimits.MAX_CANVAS_AXIS, PixelLimits.MAX_CANVAS_AXIS)
        val origin = position(0, 0)
        val oppositeCorner = position(PixelLimits.MAX_CANVAS_AXIS - 1, PixelLimits.MAX_CANVAS_AXIS - 1)
        val first = PixelChange.create(origin, PixelCell.Covered(black), PixelCell.Covered(green))
        val last = PixelChange.create(oppositeCorner, PixelCell.Covered(black), PixelCell.Covered(red))
        val fromUnordered = created(PixelPatch.create(maximumCanvas, listOf(last, first)))
        val fromCanonical = created(PixelPatch.create(maximumCanvas, listOf(first, last)))
        val expectedRegion = region(maximumCanvas, origin, maximumCanvas)

        assertEquals(fromCanonical, fromUnordered)
        assertEquals(2, fromUnordered.changeCount)
        assertEquals(expectedRegion, fromUnordered.affectedRegion)
        assertEquals(expectedRegion, fromUnordered.inverse().affectedRegion)
    }

    @Test
    fun `empty and unchanged patches are rejected`() {
        val canvas = canvas(1, 1)
        val unchanged = PixelChange.create(position(0, 0), PixelCell.Covered(black), PixelCell.Covered(black))

        assertEquals(
            PixelPatchCreationRejection.EmptyPatch,
            creationRejected(PixelPatch.create(canvas, emptyList())),
        )
        assertInstanceOf(
            PixelPatchCreationRejection.UnchangedPixel::class.java,
            creationRejected(PixelPatch.create(canvas, listOf(unchanged))),
        )
    }

    @Test
    fun `outside and duplicate positions are rejected`() {
        val canvas = canvas(1, 1)
        val outside = PixelChange.create(position(1, 0), PixelCell.Covered(black), PixelCell.Covered(red))
        val first = PixelChange.create(position(0, 0), PixelCell.Covered(black), PixelCell.Covered(red))
        val second = PixelChange.create(position(0, 0), PixelCell.Covered(black), PixelCell.Covered(green))

        assertInstanceOf(
            PixelPatchCreationRejection.PositionOutsideCanvas::class.java,
            creationRejected(PixelPatch.create(canvas, listOf(outside))),
        )
        assertInstanceOf(
            PixelPatchCreationRejection.DuplicatePosition::class.java,
            creationRejected(PixelPatch.create(canvas, listOf(first, second))),
        )
    }

    @Test
    fun `outside first input is fully materialized before typed validation`() {
        val canvas = canvas(3, 2)
        val outsidePosition = position(3, 0)
        val changes =
            AccessRecordingList(
                listOf(
                    PixelChange.create(outsidePosition, PixelCell.Covered(black), PixelCell.Covered(red)),
                    PixelChange.create(position(2, 1), PixelCell.Covered(black), PixelCell.Covered(green)),
                    PixelChange.create(position(0, 0), PixelCell.Covered(black), PixelCell.Covered(red)),
                ),
            )

        val rejection = creationRejected(PixelPatch.create(canvas, changes))
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
    fun `maximum supported square rejects an outside corner`() {
        val maximumCanvas = canvas(PixelLimits.MAX_CANVAS_AXIS, PixelLimits.MAX_CANVAS_AXIS)
        val originChange = PixelChange.create(position(0, 0), PixelCell.Covered(black), PixelCell.Covered(green))
        val outsidePosition = position(PixelLimits.MAX_CANVAS_AXIS, PixelLimits.MAX_CANVAS_AXIS)
        val outsideChange = PixelChange.create(outsidePosition, PixelCell.Covered(black), PixelCell.Covered(red))

        val rejection =
            creationRejected(
                PixelPatch.create(
                    maximumCanvas,
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
    fun `patch cap plus one rejects before reading or sorting changes`() {
        val accessed = mutableListOf<Int>()
        val changes =
            object : AbstractList<PixelChange>() {
                override val size: Int = PixelLimits.MAX_PATCH_CHANGES + 1

                override fun get(index: Int): PixelChange {
                    accessed += index
                    error("Oversized patch read index $index.")
                }
            }

        val rejection = creationRejected(PixelPatch.create(canvas(1, 1), changes))

        assertEquals(emptyList<Int>(), accessed)
        assertEquals(
            PixelPatchCreationRejection.ChangeCountAboveSupportedMaximum(
                PixelLimits.MAX_PATCH_CHANGES + 1,
                PixelLimits.MAX_PATCH_CHANGES,
            ),
            rejection,
        )
    }

    @Test
    fun `patch cap minus one and cap are accepted`() {
        val maximumCanvas = canvas(PixelLimits.MAX_CANVAS_AXIS, PixelLimits.MAX_CANVAS_AXIS)
        val changes =
            List(PixelLimits.MAX_PATCH_CHANGES) { index ->
                PixelChange.create(
                    position(index % PixelLimits.MAX_CANVAS_AXIS, index / PixelLimits.MAX_CANVAS_AXIS),
                    PixelCell.Covered(black),
                    PixelCell.Covered(red),
                )
            }

        assertEquals(
            PixelLimits.MAX_PATCH_CHANGES - 1,
            created(PixelPatch.create(maximumCanvas, changes.dropLast(1))).changeCount,
        )
        assertEquals(
            PixelLimits.MAX_PATCH_CHANGES,
            created(PixelPatch.create(maximumCanvas, changes)).changeCount,
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

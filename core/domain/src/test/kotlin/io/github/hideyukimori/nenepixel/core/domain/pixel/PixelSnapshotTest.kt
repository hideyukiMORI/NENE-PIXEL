package io.github.hideyukimori.nenepixel.core.domain.pixel

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.canvasSize
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.pixelPosition
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class PixelSnapshotTest {
    @Test
    fun `snapshot stores unsigned U8 indices in row major order`() {
        val values = listOf(index(0), index(127), index(128), index(255))
        val snapshot = created(PixelSnapshot.create(canvasSize(2, 2), Revision.initial(), values))

        assertEquals(index(0), created(snapshot.indexAt(pixelPosition(0, 0))))
        assertEquals(index(127), created(snapshot.indexAt(pixelPosition(1, 0))))
        assertEquals(index(128), created(snapshot.indexAt(pixelPosition(0, 1))))
        assertEquals(index(255), created(snapshot.indexAt(pixelPosition(1, 1))))
        assertEquals(listOf(0, 127, -128, -1), snapshot.copyPackedIndices().map(Byte::toInt))
    }

    @Test
    fun `snapshot rejects mismatch before list access and rejects index above U8`() {
        val sentinel =
            object : AbstractList<PaletteIndex>() {
                override val size: Int = 1

                override fun get(index: Int): PaletteIndex = error("must not read $index")
            }
        assertInstanceOf(
            DomainValueRejection.PixelSnapshotSizeMismatch::class.java,
            rejected(PixelSnapshot.create(canvasSize(2, 2), Revision.initial(), sentinel)),
        )

        val rejection = rejected(PixelSnapshot.create(canvasSize(1, 1), Revision.initial(), listOf(index(256))))
        assertEquals(DomainValueRejection.PixelSnapshotIndexAboveStorageMaximum(0, index(256), 255), rejection)
    }

    @Test
    fun `packed input output and revision copy never alias backing storage`() {
        val input = byteArrayOf(0, 255.toByte())
        val snapshot = created(PixelSnapshot.createPackedIndices(canvasSize(2, 1), Revision.initial(), input))
        input[0] = 12
        val output = snapshot.copyPackedIndices()
        output[1] = 12
        val later = snapshot.withRevision(created(Revision.create(1)))

        assertEquals(index(0), created(snapshot.indexAt(pixelPosition(0, 0))))
        assertEquals(index(255), created(snapshot.indexAt(pixelPosition(1, 0))))
        assertEquals(listOf<Byte>(0, 255.toByte()), later.copyPackedIndices().toList())
        assertNotEquals(snapshot, later)
    }

    @Test
    fun `filled snapshot validates U8 and outside query is typed`() {
        val snapshot = created(PixelSnapshot.createFilled(canvasSize(2, 1), Revision.initial(), index(255)))
        assertEquals(listOf(255, 255), snapshot.copyPackedIndices().map { it.toInt() and 0xff })
        assertInstanceOf(
            DomainValueRejection.PixelPositionOutsideCanvas::class.java,
            rejected(snapshot.indexAt(pixelPosition(2, 0))),
        )
        assertInstanceOf(
            DomainValueRejection.PixelSnapshotIndexAboveStorageMaximum::class.java,
            rejected(PixelSnapshot.createFilled(canvasSize(1, 1), Revision.initial(), index(256))),
        )
    }

    private fun index(value: Int): PaletteIndex = created(PaletteIndex.create(value))
}

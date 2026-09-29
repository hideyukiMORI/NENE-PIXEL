package io.github.hideyukimori.nenepixel.core.domain.pixel

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.canvasSize
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.pixelPosition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class PixelSnapshotCoverageTest {
    @Test
    fun `existing factories produce fully covered snapshots`() {
        val size = canvasSize(3, 3)
        val snapshots =
            listOf(
                created(PixelSnapshot.create(size, List(9) { index(it) })),
                created(PixelSnapshot.createPackedIndices(size, ByteArray(9) { 7 })),
                created(PixelSnapshot.createFilled(size, index(4))),
            )

        snapshots.forEach { snapshot ->
            assertArrayEquals(byteArrayOf(-1, 1), snapshot.copyCoverage())
        }
        assertEquals(PixelCell.Covered(index(8)), created(snapshots[0].cellAt(pixelPosition(2, 2))))
    }

    @Test
    fun `full coverage on a multiple of eight pixels has no partial byte`() {
        val snapshot = created(PixelSnapshot.createFilled(canvasSize(4, 2), index(0)))

        assertArrayEquals(byteArrayOf(-1), snapshot.copyCoverage())
    }

    @Test
    fun `createEmpty yields empty cells with zero indices`() {
        val snapshot = PixelSnapshot.createEmpty(canvasSize(3, 3))

        assertArrayEquals(ByteArray(2), snapshot.copyCoverage())
        assertArrayEquals(ByteArray(9), snapshot.copyPackedIndices())
        assertEquals(index(0), snapshot.maximumIndex)
        assertEquals(PixelCell.Empty, created(snapshot.cellAt(pixelPosition(1, 2))))
        assertEquals(PixelCell.Empty, created(snapshot.cellAt(pixelPosition(0, 0))))
    }

    @Test
    fun `createPackedCells reads coverage row major LSB first`() {
        val indices = byteArrayOf(0, 5, 0, 0, 0, 0, 0, 0, 200.toByte())
        val coverage = byteArrayOf(0b0000_0010, 0b0000_0001)
        val snapshot = created(PixelSnapshot.createPackedCells(canvasSize(3, 3), indices, coverage))

        assertEquals(PixelCell.Empty, created(snapshot.cellAt(pixelPosition(0, 0))))
        assertEquals(PixelCell.Covered(index(5)), created(snapshot.cellAt(pixelPosition(1, 0))))
        assertEquals(PixelCell.Empty, created(snapshot.cellAt(pixelPosition(2, 0))))
        assertEquals(PixelCell.Covered(index(200)), created(snapshot.cellAt(pixelPosition(2, 2))))
        assertEquals(index(200), snapshot.maximumIndex)
    }

    @Test
    fun `createPackedCells accepts covered zero index distinct from empty`() {
        val snapshot =
            created(
                PixelSnapshot.createPackedCells(canvasSize(2, 1), ByteArray(2), byteArrayOf(1)),
            )

        assertEquals(PixelCell.Covered(index(0)), created(snapshot.cellAt(pixelPosition(0, 0))))
        assertEquals(PixelCell.Empty, created(snapshot.cellAt(pixelPosition(1, 0))))
    }

    @Test
    fun `createPackedCells rejects index length before coverage checks`() {
        val rejection =
            rejected(PixelSnapshot.createPackedCells(canvasSize(3, 3), ByteArray(8), ByteArray(0)))

        assertEquals(DomainValueRejection.PixelSnapshotSizeMismatch(9, 8), rejection)
    }

    @Test
    fun `createPackedCells rejects coverage length mismatch`() {
        val size = canvasSize(3, 3)

        assertEquals(
            DomainValueRejection.PixelCoverageSizeMismatch(2, 1),
            rejected(PixelSnapshot.createPackedCells(size, ByteArray(9), ByteArray(1))),
        )
        assertEquals(
            DomainValueRejection.PixelCoverageSizeMismatch(2, 3),
            rejected(PixelSnapshot.createPackedCells(size, ByteArray(9), ByteArray(3))),
        )
    }

    @Test
    fun `createPackedCells rejects trailing bits before empty index check`() {
        val indices = ByteArray(9) { 1 }
        val coverage = byteArrayOf(0, 0b0000_0010)

        assertEquals(
            DomainValueRejection.PixelCoverageTrailingBitsSet,
            rejected(PixelSnapshot.createPackedCells(canvasSize(3, 3), indices, coverage)),
        )
    }

    @Test
    fun `createPackedCells rejects first empty pixel with non zero index`() {
        val indices = byteArrayOf(3, 0, 0, 9, 0, 4, 0, 0, 0)
        val coverage = byteArrayOf(0b0000_0001, 0)

        assertEquals(
            DomainValueRejection.EmptyPixelIndexNotZero(3),
            rejected(PixelSnapshot.createPackedCells(canvasSize(3, 3), indices, coverage)),
        )
    }

    @Test
    fun `cellAt rejects position outside canvas`() {
        val size = canvasSize(3, 3)
        val snapshot = PixelSnapshot.createEmpty(size)

        assertEquals(
            DomainValueRejection.PixelPositionOutsideCanvas(size, pixelPosition(3, 0)),
            rejected(snapshot.cellAt(pixelPosition(3, 0))),
        )
    }

    @Test
    fun `createPackedCells owns inputs and copyCoverage is defensive`() {
        val indices = byteArrayOf(0, 6)
        val coverage = byteArrayOf(0b10)
        val snapshot = created(PixelSnapshot.createPackedCells(canvasSize(2, 1), indices, coverage))
        indices[1] = 9
        coverage[0] = 0b11
        snapshot.copyCoverage()[0] = 0b11

        assertArrayEquals(byteArrayOf(0b10), snapshot.copyCoverage())
        assertEquals(PixelCell.Empty, created(snapshot.cellAt(pixelPosition(0, 0))))
        assertEquals(PixelCell.Covered(index(6)), created(snapshot.cellAt(pixelPosition(1, 0))))
    }

    @Test
    fun `equality and hash include coverage`() {
        val size = canvasSize(2, 1)
        val covered = created(PixelSnapshot.createPackedIndices(size, ByteArray(2)))
        val empty = PixelSnapshot.createEmpty(size)
        val sameCovered =
            created(PixelSnapshot.createPackedCells(size, ByteArray(2), byteArrayOf(3)))

        assertNotEquals(covered, empty)
        assertEquals(covered, sameCovered)
        assertEquals(covered.hashCode(), sameCovered.hashCode())
        assertEquals(empty, PixelSnapshot.createEmpty(size))
    }

    private fun index(value: Int): PaletteIndex = created(PaletteIndex.create(value))
}

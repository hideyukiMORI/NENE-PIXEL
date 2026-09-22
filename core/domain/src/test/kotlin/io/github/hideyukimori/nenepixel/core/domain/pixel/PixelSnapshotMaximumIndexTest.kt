package io.github.hideyukimori.nenepixel.core.domain.pixel

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.canvasSize
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PixelSnapshotMaximumIndexTest {
    @Test
    fun `maximum index reports the largest stored index for every factory`() {
        val size = canvasSize(2, 2)
        val listed = created(PixelSnapshot.create(size, Revision.initial(), listOf(0, 255, 7, 3).map(::index)))
        val packed =
            created(
                PixelSnapshot.createPackedIndices(size, Revision.initial(), byteArrayOf(0, 255.toByte(), 7, 3)),
            )
        val filled = created(PixelSnapshot.createFilled(size, Revision.initial(), index(9)))

        assertEquals(index(255), listed.maximumIndex)
        assertEquals(index(255), packed.maximumIndex)
        assertEquals(index(9), filled.maximumIndex)
    }

    @Test
    fun `maximum index survives a revision copy and stays zero for a blank raster`() {
        val size = canvasSize(2, 1)
        val blank = created(PixelSnapshot.createPackedIndices(size, Revision.initial(), ByteArray(2)))
        val populated = created(PixelSnapshot.createPackedIndices(size, Revision.initial(), byteArrayOf(4, 1)))

        assertEquals(PaletteIndex.first, blank.maximumIndex)
        assertEquals(index(4), populated.withRevision(created(Revision.create(1))).maximumIndex)
    }

    private fun index(value: Int): PaletteIndex = created(PaletteIndex.create(value))
}

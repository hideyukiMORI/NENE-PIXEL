package io.github.hideyukimori.nenepixel.core.domain.importing

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ImportRasterTest {
    @Test
    fun `one by one raster is created and reads its exact values`() {
        val raster = created(ImportRaster.create(1, 1, intArrayOf(0x01020304)))

        assertEquals(1, raster.width)
        assertEquals(1, raster.height)
        assertArrayEquals(intArrayOf(0x01020304), raster.copyPackedRgba8888())
        assertEquals("ImportRaster(width=1, height=1)", raster.toString())
    }

    @Test
    fun `maximum side raster is created`() {
        val side = ImportRaster.MAX_SIDE
        val input = IntArray(side * side) { it }
        val raster = created(ImportRaster.create(side, side, input))

        assertEquals(1024, side)
        assertEquals(side, raster.width)
        assertEquals(side, raster.height)
        assertArrayEquals(input, raster.copyPackedRgba8888())
    }

    @Test
    fun `values are read back in row-major order`() {
        val expected = intArrayOf(0x11223344, -1, 0, 0x7F000080, 0x00FF00FF, 0x0000FFFF)
        val raster = created(ImportRaster.create(3, 2, expected.copyOf()))

        assertEquals(3, raster.width)
        assertEquals(2, raster.height)
        assertArrayEquals(expected, raster.copyPackedRgba8888())
    }

    @Test
    fun `width outside the side range is rejected`() {
        for (width in intArrayOf(0, -1, ImportRaster.MAX_SIDE + 1)) {
            val pixels = IntArray(maxOf(width, 0))
            assertEquals(
                DomainValueRejection.ImportRasterSideOutOfRange(width, 1),
                rejected(ImportRaster.create(width, 1, pixels)),
            )
        }
    }

    @Test
    fun `height outside the side range is rejected`() {
        for (height in intArrayOf(0, -1, ImportRaster.MAX_SIDE + 1)) {
            val pixels = IntArray(maxOf(height, 0))
            assertEquals(
                DomainValueRejection.ImportRasterSideOutOfRange(1, height),
                rejected(ImportRaster.create(1, height, pixels)),
            )
        }
    }

    @Test
    fun `pixel count mismatch is rejected`() {
        assertEquals(
            DomainValueRejection.ImportRasterSizeMismatch(6, 5),
            rejected(ImportRaster.create(3, 2, IntArray(5))),
        )
        assertEquals(
            DomainValueRejection.ImportRasterSizeMismatch(6, 7),
            rejected(ImportRaster.create(3, 2, IntArray(7))),
        )
    }

    @Test
    fun `changing the input after creation does not change the raster`() {
        val input = intArrayOf(1, 2)
        val raster = created(ImportRaster.create(2, 1, input))
        input[0] = 9

        assertArrayEquals(intArrayOf(1, 2), raster.copyPackedRgba8888())
    }

    @Test
    fun `changing a returned copy does not change the raster`() {
        val raster = created(ImportRaster.create(2, 1, intArrayOf(1, 2)))
        val output = raster.copyPackedRgba8888()
        output[1] = 9

        assertArrayEquals(intArrayOf(1, 2), raster.copyPackedRgba8888())
    }
}

package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class ReferenceImageTest {
    @Test
    fun `sides of 1 and 1024 are accepted`() {
        assertEquals(1, created(1, 1).width)
        val largest = created(ReferenceImage.MAX_SIDE, 1)

        assertEquals(ReferenceImage.MAX_SIDE, largest.width)
        assertEquals(1, largest.height)
        assertEquals(ReferenceImage.MAX_SIDE, created(1, ReferenceImage.MAX_SIDE).height)
    }

    @Test
    fun `sides of 0 and 1025 are rejected as invalid size`() {
        val invalid = ReferenceImageResult.Rejected(ReferenceImageRejection.InvalidSize)

        assertEquals(invalid, ReferenceImage.create(0, 1, IntArray(0)))
        assertEquals(invalid, ReferenceImage.create(1, 0, IntArray(0)))
        assertEquals(invalid, ReferenceImage.create(-1, -1, IntArray(1)))
        assertEquals(invalid, ReferenceImage.create(TOO_LONG, 1, IntArray(TOO_LONG)))
        assertEquals(invalid, ReferenceImage.create(1, TOO_LONG, IntArray(TOO_LONG)))
    }

    @Test
    fun `a raster whose length is not width times height is rejected`() {
        val mismatch = ReferenceImageResult.Rejected(ReferenceImageRejection.PixelCountMismatch)

        assertEquals(mismatch, ReferenceImage.create(2, 2, IntArray(3)))
        assertEquals(mismatch, ReferenceImage.create(2, 2, IntArray(5)))
    }

    @Test
    fun `the image copies its input`() {
        val input = intArrayOf(RED, GREEN)
        val image = createdFrom(2, 1, input)

        input[0] = BLUE

        assertArrayEquals(intArrayOf(RED, GREEN), image.copyPackedRgba8888())
    }

    @Test
    fun `changing a returned raster does not change the image`() {
        val image = createdFrom(2, 1, intArrayOf(RED, GREEN))

        image.copyPackedRgba8888()[1] = BLUE

        assertArrayEquals(intArrayOf(RED, GREEN), image.copyPackedRgba8888())
    }

    @Test
    fun `equality is identity`() {
        val first = createdFrom(1, 1, intArrayOf(RED))
        val second = createdFrom(1, 1, intArrayOf(RED))

        assertEquals(first, first)
        assertNotEquals(first, second)
    }

    @Test
    fun `toString shows only the size`() {
        assertEquals("ReferenceImage(width=2, height=1)", createdFrom(2, 1, intArrayOf(RED, GREEN)).toString())
    }

    private fun created(
        width: Int,
        height: Int,
    ): ReferenceImage = createdFrom(width, height, IntArray(width * height))

    private fun createdFrom(
        width: Int,
        height: Int,
        packed: IntArray,
    ): ReferenceImage {
        val result = ReferenceImage.create(width, height, packed)
        check(result is ReferenceImageResult.Created) { "expected Created, got $result" }
        return result.image
    }

    private companion object {
        const val RED: Int = 0xff0000ff.toInt()
        const val GREEN: Int = 0x00ff00ff
        const val BLUE: Int = 0x0000ffff
        const val TOO_LONG: Int = ReferenceImage.MAX_SIDE + 1
    }
}

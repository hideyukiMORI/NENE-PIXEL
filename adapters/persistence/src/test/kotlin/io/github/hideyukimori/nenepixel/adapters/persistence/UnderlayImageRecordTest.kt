package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.zip.CRC32

internal class UnderlayImageRecordTest {
    @Test
    fun `two by one image matches exact golden bytes`() {
        val encoded = UnderlayImageRecord.encode(goldenImage())

        assertEquals(GOLDEN_IMAGE_HEX, encoded.toHexadecimal())
        val independent = CRC32()
        independent.update(encoded, 0, encoded.size - 4)
        assertEquals(GOLDEN_IMAGE_CHECKSUM, independent.value)
        assertEquals(GOLDEN_IMAGE_CHECKSUM.toInt(), UnderlayImageRecord.checksum(encoded))
    }

    @Test
    fun `golden bytes decode to the same size and pixels including alpha`() {
        val image = decoded(GOLDEN_IMAGE_HEX.decodeHex())

        assertEquals(2, image.width)
        assertEquals(1, image.height)
        assertArrayEquals(goldenPixels(), image.copyPackedRgba8888())
    }

    @Test
    fun `the highest byte of a packed pixel is written first as red`() {
        val encoded = UnderlayImageRecord.encode(image(2, 1, intArrayOf(RED_PIXEL, BLUE_PIXEL)))

        assertEquals("ff000080", encoded.copyOfRange(10, 14).toHexadecimal())
        assertEquals("0000ff80", encoded.copyOfRange(14, 18).toHexadecimal())
    }

    @Test
    fun `largest image round trips at the largest record length`() {
        val side = ReferenceImage.MAX_SIDE
        val pixels = IntArray(side * side) { index -> index * PIXEL_STRIDE }
        val encoded = UnderlayImageRecord.encode(image(side, side, pixels))

        assertEquals(4_194_318, encoded.size)
        assertEquals(UnderlayMemoryLayout.IMAGE_MAX_BYTE_COUNT, encoded.size)
        val image = decoded(encoded)
        assertEquals(side, image.width)
        assertEquals(side, image.height)
        assertArrayEquals(pixels, image.copyPackedRgba8888())
    }

    @Test
    fun `encode returns a new array each time`() {
        val image = goldenImage()
        val first = UnderlayImageRecord.encode(image)
        val second = UnderlayImageRecord.encode(image)

        assertNotSame(first, second)
        assertArrayEquals(first, second)
    }

    @Test
    fun `a record one byte short is unreadable`() {
        val golden = GOLDEN_IMAGE_HEX.decodeHex()

        assertUnreadable(golden.copyOf(golden.size - 1))
    }

    @Test
    fun `a record one byte long is unreadable`() {
        val golden = GOLDEN_IMAGE_HEX.decodeHex()

        assertUnreadable(golden.copyOf(golden.size + 1))
    }

    @Test
    fun `an empty record and a record above the largest length are unreadable`() {
        assertUnreadable(ByteArray(0))
        assertUnreadable(ByteArray(UnderlayMemoryLayout.IMAGE_MAX_BYTE_COUNT + 1))
    }

    @Test
    fun `another magic is unreadable`() {
        val bytes = GOLDEN_IMAGE_HEX.decodeHex()
        bytes[3] = 0x53

        assertUnreadable(resealed(bytes))
    }

    @Test
    fun `version two is unreadable`() {
        val bytes = GOLDEN_IMAGE_HEX.decodeHex()
        bytes[5] = 2

        assertUnreadable(resealed(bytes))
    }

    @Test
    fun `a wrong checksum is unreadable`() {
        val bytes = GOLDEN_IMAGE_HEX.decodeHex()
        bytes[bytes.lastIndex] = (bytes[bytes.lastIndex].toInt() xor 1).toByte()

        assertUnreadable(bytes)
    }

    @Test
    fun `a changed pixel without a new checksum is unreadable`() {
        val bytes = GOLDEN_IMAGE_HEX.decodeHex()
        bytes[10] = 0

        assertUnreadable(bytes)
    }

    @Test
    fun `width zero is unreadable`() {
        assertUnreadable(sealedRecord(0, 1, 1))
    }

    @Test
    fun `width 1025 with a matching length and checksum is unreadable`() {
        assertUnreadable(sealedRecord(ReferenceImage.MAX_SIDE + 1, 1, ReferenceImage.MAX_SIDE + 1))
    }

    @Test
    fun `a length that does not match width and height is unreadable`() {
        assertUnreadable(sealedRecord(2, 2, 2))
    }

    private fun sealedRecord(
        width: Int,
        height: Int,
        pixelCount: Int,
    ): ByteArray {
        val bytes = ByteArray(14 + 4 * pixelCount + 4)
        "4e5055490001".decodeHex().copyInto(bytes)
        bytes[6] = (width ushr 8).toByte()
        bytes[7] = width.toByte()
        bytes[8] = (height ushr 8).toByte()
        bytes[9] = height.toByte()
        return resealed(bytes)
    }

    private fun resealed(bytes: ByteArray): ByteArray {
        val checksum = CRC32()
        checksum.update(bytes, 0, bytes.size - 4)
        val value = checksum.value
        repeat(4) { index ->
            bytes[bytes.size - 4 + index] = (value ushr ((3 - index) * 8)).toByte()
        }
        return bytes
    }

    private fun assertUnreadable(bytes: ByteArray) {
        assertEquals(UnderlayRecordDecodeResult.Unreadable, UnderlayImageRecord.decode(bytes))
    }

    private fun decoded(bytes: ByteArray): ReferenceImage {
        val result = UnderlayImageRecord.decode(bytes)
        return when (result) {
            is UnderlayRecordDecodeResult.Decoded -> result.value
            UnderlayRecordDecodeResult.Unreadable -> fail("the record was unreadable")
        }
    }

    private fun goldenImage(): ReferenceImage = image(2, 1, goldenPixels())

    private fun goldenPixels(): IntArray = intArrayOf(0x11223344, 0xaabbcc00.toInt())

    private fun image(
        width: Int,
        height: Int,
        pixels: IntArray,
    ): ReferenceImage {
        val created = ReferenceImage.create(width, height, pixels) as ReferenceImageResult.Created
        return created.image
    }

    private fun ByteArray.toHexadecimal(): String = joinToString("") { byte -> "%02x".format(byte) }

    private fun String.decodeHex(): ByteArray =
        ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }

    private companion object {
        const val GOLDEN_IMAGE_HEX: String = "4e50554900010002000111223344aabbcc005998be12"
        const val GOLDEN_IMAGE_CHECKSUM: Long = 0x5998be12L
        const val RED_PIXEL: Int = 0xff000080.toInt()
        const val BLUE_PIXEL: Int = 0x0000ff80
        const val PIXEL_STRIDE: Int = 0x01020305
    }
}

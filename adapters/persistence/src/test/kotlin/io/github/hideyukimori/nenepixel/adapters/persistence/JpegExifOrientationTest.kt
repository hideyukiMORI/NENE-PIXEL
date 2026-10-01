package io.github.hideyukimori.nenepixel.adapters.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class JpegExifOrientationTest {
    @Test
    fun `big-endian orientation is read`() {
        assertEquals(6, orientationOf(jpeg(exifApp1(tiff(ByteOrder.BIG_ENDIAN, listOf(orientationEntry(6)))))))
    }

    @Test
    fun `little-endian orientation is read`() {
        assertEquals(6, orientationOf(jpeg(exifApp1(tiff(ByteOrder.LITTLE_ENDIAN, listOf(orientationEntry(6)))))))
    }

    @Test
    fun `each of the eight values is read in both byte orders`() {
        for (order in listOf(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN)) {
            for (value in 1..8) {
                assertEquals(value, orientationOf(jpeg(exifApp1(tiff(order, listOf(orientationEntry(value)))))))
            }
        }
    }

    @Test
    fun `orientation entry after other entries is read`() {
        val entries = listOf(entry(0x010F, 2, 4, 0), entry(0x0110, 2, 4, 0), orientationEntry(8))

        assertEquals(8, orientationOf(jpeg(exifApp1(tiff(ByteOrder.BIG_ENDIAN, entries)))))
    }

    @Test
    fun `JPEG without EXIF is normal`() {
        assertEquals(1, orientationOf(jpeg(app0())))
        assertEquals(1, orientationOf(jpeg()))
    }

    @Test
    fun `APP1 after APP0 is read`() {
        val app1 = exifApp1(tiff(ByteOrder.LITTLE_ENDIAN, listOf(orientationEntry(3))))

        assertEquals(3, orientationOf(jpeg(app0(), app1)))
    }

    @Test
    fun `non-Exif APP1 is skipped and the following Exif APP1 is read`() {
        val xmp = segment(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<x/>".toByteArray(Charsets.US_ASCII))

        assertEquals(5, orientationOf(jpeg(xmp, exifApp1(tiff(ByteOrder.BIG_ENDIAN, listOf(orientationEntry(5)))))))
    }

    @Test
    fun `only the first Exif APP1 is read`() {
        val first = exifApp1(tiff(ByteOrder.BIG_ENDIAN, emptyList()))
        val second = exifApp1(tiff(ByteOrder.BIG_ENDIAN, listOf(orientationEntry(6))))

        assertEquals(1, orientationOf(jpeg(first, second)))
    }

    @Test
    fun `APP1 after the start of scan is not read`() {
        val app1 = exifApp1(tiff(ByteOrder.BIG_ENDIAN, listOf(orientationEntry(6))))

        assertEquals(1, orientationOf(bytes(SOI, segment(0xDA, ByteArray(4)), app1, EOI)))
    }

    @Test
    fun `segments beyond the sixty-fourth are not read`() {
        val app1 = exifApp1(tiff(ByteOrder.BIG_ENDIAN, listOf(orientationEntry(6))))

        assertEquals(6, orientationOf(jpeg(*Array(63) { app0() }, app1)))
        assertEquals(1, orientationOf(jpeg(*Array(64) { app0() }, app1)))
    }

    @Test
    fun `bytes that are not a JPEG are normal`() {
        assertEquals(1, orientationOf("not a picture".toByteArray()))
        assertEquals(1, orientationOf(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals(1, orientationOf(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun `empty bytes are normal`() {
        assertEquals(1, orientationOf(ByteArray(0)))
    }

    @Test
    fun `segment whose length runs past the end is normal`() {
        val app1 = exifApp1(tiff(ByteOrder.BIG_ENDIAN, listOf(orientationEntry(6))))
        val cut = bytes(SOI, app1).copyOf(SOI.size + app1.size - 1)

        assertEquals(1, orientationOf(cut))
    }

    @Test
    fun `every prefix of a tagged JPEG is read without an exception`() {
        val full = jpeg(app0(), exifApp1(tiff(ByteOrder.LITTLE_ENDIAN, listOf(orientationEntry(6)))))

        for (size in 0..full.size) {
            assertTrue(orientationOf(full.copyOf(size)) in setOf(1, 6))
        }
    }

    @Test
    fun `segment length below two is normal`() {
        assertEquals(1, orientationOf(bytes(SOI, byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0, 1), EOI)))
    }

    @Test
    fun `IFD offset outside the TIFF is normal`() {
        for (offset in listOf(0x7FFF_FFFF, -1, 26)) {
            val tiff = tiff(ByteOrder.BIG_ENDIAN, listOf(orientationEntry(6)), ifdOffset = offset)

            assertEquals(1, orientationOf(jpeg(exifApp1(tiff))))
        }
    }

    @Test
    fun `IFD entry count larger than the entries is normal`() {
        val tiff = tiff(ByteOrder.BIG_ENDIAN, listOf(entry(0x010F, 2, 4, 0)), declaredCount = 0xFFFF)

        assertEquals(1, orientationOf(jpeg(exifApp1(tiff))))
    }

    @Test
    fun `IFD entries beyond the two hundred fifty-sixth are not read`() {
        val filler = List(256) { entry(0x010F, 2, 4, 0) }
        val early = tiff(ByteOrder.BIG_ENDIAN, listOf(orientationEntry(6)) + filler, declaredCount = 0xFFFF)
        val late = tiff(ByteOrder.BIG_ENDIAN, filler + orientationEntry(6), declaredCount = 257)

        assertEquals(6, orientationOf(jpeg(exifApp1(early))))
        assertEquals(1, orientationOf(jpeg(exifApp1(late))))
    }

    @Test
    fun `entry with another type or count is normal`() {
        val long = entry(ORIENTATION_TAG, 4, 1, 6)
        val pair = entry(ORIENTATION_TAG, 3, 2, 6)

        assertEquals(1, orientationOf(jpeg(exifApp1(tiff(ByteOrder.BIG_ENDIAN, listOf(long))))))
        assertEquals(1, orientationOf(jpeg(exifApp1(tiff(ByteOrder.LITTLE_ENDIAN, listOf(pair))))))
    }

    @Test
    fun `values zero and nine are normal`() {
        assertEquals(1, orientationOf(jpeg(exifApp1(tiff(ByteOrder.BIG_ENDIAN, listOf(orientationEntry(0)))))))
        assertEquals(1, orientationOf(jpeg(exifApp1(tiff(ByteOrder.LITTLE_ENDIAN, listOf(orientationEntry(9)))))))
    }

    @Test
    fun `unknown byte order or magic is normal`() {
        val broken = tiff(ByteOrder.BIG_ENDIAN, listOf(orientationEntry(6)))
        val badOrder = broken.copyOf().apply { this[0] = 'X'.code.toByte() }
        val badMagic = broken.copyOf().apply { this[3] = 43 }

        assertEquals(1, orientationOf(jpeg(exifApp1(badOrder))))
        assertEquals(1, orientationOf(jpeg(exifApp1(badMagic))))
    }

    @Test
    fun `Exif header without a TIFF is normal`() {
        assertEquals(1, orientationOf(jpeg(exifApp1(ByteArray(0)))))
        assertEquals(1, orientationOf(jpeg(segment(0xE1, "Exif".toByteArray(Charsets.US_ASCII)))))
    }

    private fun orientationOf(encoded: ByteArray): Int = JpegExifOrientation.orientation(encoded)

    private fun jpeg(vararg segments: ByteArray): ByteArray =
        bytes(SOI, *segments, segment(0xDA, ByteArray(4)), byteArrayOf(0x11, 0x22), EOI)

    private fun bytes(vararg parts: ByteArray): ByteArray = parts.fold(ByteArray(0)) { all, part -> all + part }

    private fun segment(
        marker: Int,
        payload: ByteArray,
    ): ByteArray =
        ByteBuffer
            .allocate(4 + payload.size)
            .put(0xFF.toByte())
            .put(marker.toByte())
            .putShort((payload.size + 2).toShort())
            .put(payload)
            .array()

    private fun app0(): ByteArray =
        segment(0xE0, "JFIF\u0000".toByteArray(Charsets.US_ASCII) + byteArrayOf(1, 1, 0, 0, 1, 0, 1, 0, 0))

    private fun exifApp1(tiff: ByteArray): ByteArray =
        segment(0xE1, "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII) + tiff)

    /** A TIFF header and IFD0 holding [entries]; each entry is 12 bytes written in [order]. */
    private fun tiff(
        order: ByteOrder,
        entries: List<(ByteBuffer) -> Unit>,
        ifdOffset: Int = 8,
        declaredCount: Int = entries.size,
    ): ByteArray {
        val buffer = ByteBuffer.allocate(8 + 2 + entries.size * 12 + 4).order(order)
        buffer.put(if (order == ByteOrder.LITTLE_ENDIAN) "II".toByteArray() else "MM".toByteArray())
        buffer.putShort(42).putInt(ifdOffset).putShort(declaredCount.toShort())
        entries.forEach { it(buffer) }
        buffer.putInt(0)
        return buffer.array()
    }

    private fun orientationEntry(value: Int): (ByteBuffer) -> Unit = entry(ORIENTATION_TAG, 3, 1, value)

    /** An IFD entry whose value field starts with a SHORT [value] followed by two zero bytes. */
    private fun entry(
        tag: Int,
        type: Int,
        count: Int,
        value: Int,
    ): (ByteBuffer) -> Unit =
        { buffer ->
            buffer.putShort(tag.toShort()).putShort(type.toShort()).putInt(count)
            buffer.putShort(value.toShort()).putShort(0)
        }

    private companion object {
        const val ORIENTATION_TAG: Int = 0x0112
        val SOI: ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val EOI: ByteArray = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    }
}

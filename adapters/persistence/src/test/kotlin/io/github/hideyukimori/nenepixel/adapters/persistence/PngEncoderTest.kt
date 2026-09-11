package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

internal class PngEncoderTest {
    @Test
    fun `minimal PNG matches independently constructed golden and decoded pixels`() {
        val snapshot = PersistenceTestValues.minimalDocument.snapshot
        assertGolden("minimal.png", snapshot)
        assertEquals(86, PngEncoder.encode(snapshot).byteCount)
    }

    @Test
    fun `rectangular PNG preserves hidden RGB and low alpha exactly`() {
        val snapshot = rectangle()
        assertGolden("transparent-rectangle.png", snapshot)
    }

    @Test
    fun `maximum PNG has bounded retained payload and every decoded pixel matches`() {
        val snapshot = PersistenceTestValues.maximumDocument().snapshot
        val encoded = PngEncoder.encode(snapshot)
        assertEquals(263_756, encoded.byteCount)
        assertEquals(PngBytes.MAX_BYTE_COUNT, encoded.byteCount)
        assertPixels(snapshot, encoded.copyBytes())
    }

    @Test
    fun `all channel values and alpha zero remain deterministic`() {
        val size = CanvasSize.create(created(CanvasWidth.create(256)), created(CanvasHeight.create(1)))
        val pixels = IntArray(256) { value -> (value shl 24) or ((255 - value) shl 16) or (value shl 8) }
        val snapshot = created(PixelSnapshot.createPackedRgba8888(size, created(Revision.create(0)), pixels))
        val bytes = PngEncoder.encode(snapshot).copyBytes()
        assertPixels(snapshot, bytes)
        assertArrayEquals(bytes, PngEncoder.encode(snapshot).copyBytes())
    }

    @Test
    fun `PNG carrier owns input and output copies`() {
        val input = PngEncoder.encode(rectangle()).copyBytes()
        val expected = input.copyOf()
        val carrier = PngBytes.create(input)
        input.fill(0)
        carrier.copyBytes().fill(0)
        assertArrayEquals(expected, carrier.copyBytes())
    }

    private fun assertGolden(
        name: String,
        snapshot: PixelSnapshot,
    ) {
        val resource = checkNotNull(javaClass.getResourceAsStream("/png-v1/$name"))
        val golden = resource.use { it.readBytes() }
        val encoded = PngEncoder.encode(snapshot).copyBytes()
        assertArrayEquals(golden, encoded)
        assertPixels(snapshot, encoded)
    }

    private fun assertPixels(
        snapshot: PixelSnapshot,
        bytes: ByteArray,
    ) {
        val image = checkNotNull(ImageIO.read(ByteArrayInputStream(bytes)))
        assertEquals(snapshot.size.width.value, image.width)
        assertEquals(snapshot.size.height.value, image.height)
        assertFalse(image.isAlphaPremultiplied)
        val expected = snapshot.copyPackedRgba8888()
        expected.forEachIndexed { index, rgba ->
            val argb = (rgba ushr 8) or (rgba shl 24)
            assertEquals(argb, image.getRGB(index % image.width, index / image.width), "pixel $index")
        }
    }

    private fun rectangle(): PixelSnapshot {
        val size = CanvasSize.create(created(CanvasWidth.create(3)), created(CanvasHeight.create(2)))
        return created(
            PixelSnapshot.createPackedRgba8888(
                size,
                created(Revision.create(0)),
                intArrayOf(
                    0x10203000,
                    0xabcdef01.toInt(),
                    0x1122337f,
                    0xdeadbefe.toInt(),
                    0x010203ff,
                    0,
                ),
            ),
        )
    }

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid PNG fixture")
        }
}

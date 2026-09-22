package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
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
        val document = PersistenceTestValues.minimalDocument
        assertGolden("minimal.png", document)
        assertEquals(86, PngEncoder.encode(document).byteCount)
    }

    @Test
    fun `rectangular PNG preserves hidden RGB and low alpha exactly`() {
        val document = rectangle()
        assertGolden("transparent-rectangle.png", document)
    }

    @Test
    fun `maximum PNG has bounded retained payload and every decoded pixel matches`() {
        val document = PersistenceTestValues.maximumDocument()
        val encoded = PngEncoder.encode(document)
        assertEquals(263_756, encoded.byteCount)
        assertEquals(PngBytes.MAX_BYTE_COUNT, encoded.byteCount)
        assertPixels(document, encoded.copyBytes())
    }

    @Test
    fun `all channel values and alpha zero remain deterministic`() {
        val size = CanvasSize.create(created(CanvasWidth.create(256)), created(CanvasHeight.create(1)))
        val pixels = IntArray(256) { value -> (value shl 24) or ((255 - value) shl 16) or (value shl 8) }
        val document = document(size, pixels, ByteArray(256) { it.toByte() })
        val bytes = PngEncoder.encode(document).copyBytes()
        assertPixels(document, bytes)
        assertArrayEquals(bytes, PngEncoder.encode(document).copyBytes())
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
        document: DocumentState,
    ) {
        val resource = checkNotNull(javaClass.getResourceAsStream("/png-v1/$name"))
        val golden = resource.use { it.readBytes() }
        val encoded = PngEncoder.encode(document).copyBytes()
        assertArrayEquals(golden, encoded)
        assertPixels(document, encoded)
    }

    private fun assertPixels(
        document: DocumentState,
        bytes: ByteArray,
    ) {
        val image = checkNotNull(ImageIO.read(ByteArrayInputStream(bytes)))
        assertEquals(document.size.width.value, image.width)
        assertEquals(document.size.height.value, image.height)
        assertFalse(image.isAlphaPremultiplied)
        val colors =
            document.definition.palette
                .entries()
                .map { it.color.toPackedRgba8888() }
        val expected = document.snapshot.copyPackedIndices().map { colors[it.toInt() and 0xff] }
        expected.forEachIndexed { index, rgba ->
            val argb = (rgba ushr 8) or (rgba shl 24)
            assertEquals(argb, image.getRGB(index % image.width, index / image.width), "pixel $index")
        }
    }

    private fun rectangle(): DocumentState {
        val size = CanvasSize.create(created(CanvasWidth.create(3)), created(CanvasHeight.create(2)))
        return document(
            size,
            intArrayOf(
                0x10203000,
                0xabcdef01.toInt(),
                0x1122337f,
                0xdeadbefe.toInt(),
                0x010203ff,
                0,
            ),
            byteArrayOf(0, 1, 2, 3, 4, 5),
        )
    }

    private fun document(
        size: CanvasSize,
        colors: IntArray,
        indices: ByteArray,
    ): DocumentState {
        val palette = created(Palette.create(colors.map(PixelColor::fromPackedRgba8888)))
        val definition = created(PaletteDefinition.create(palette, created(PaletteIndex.create(0))))
        val snapshot = created(PixelSnapshot.createPackedIndices(size, created(Revision.create(0)), indices))
        return created(DocumentState.create(created(DocumentId.create("0".repeat(32))), definition, snapshot))
    }

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid PNG fixture")
        }
}

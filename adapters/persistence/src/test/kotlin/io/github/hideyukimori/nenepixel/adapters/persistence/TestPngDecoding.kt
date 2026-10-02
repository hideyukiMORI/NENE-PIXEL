package io.github.hideyukimori.nenepixel.adapters.persistence

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** Assertions on [PngImportDecoder.decode] for the import decoder tests. */
internal object TestPngDecoding {
    private const val ALPHA_SHIFT: Int = 24
    private const val COLOR_SHIFT: Int = 8

    /** Decodes [bytes] and asserts the result is `Decoded` with the given sides and [expected] pixels. */
    fun assertDecoded(
        bytes: ByteArray,
        width: Int,
        height: Int,
        expected: IntArray,
    ): PngImportDecodeResult.Decoded {
        val decoded = assertInstanceOf(PngImportDecodeResult.Decoded::class.java, PngImportDecoder.decode(bytes))
        assertEquals(width, decoded.width)
        assertEquals(height, decoded.height)
        assertArrayEquals(expected.map(::hex).toTypedArray(), decoded.packedRgba8888.map(::hex).toTypedArray())
        return decoded
    }

    /** Asserts that `ImageIO` reads [bytes] into the same straight RGBA as [expected]. */
    fun assertImageIo(
        bytes: ByteArray,
        expected: IntArray,
    ) {
        val image = checkNotNull(ImageIO.read(ByteArrayInputStream(bytes)))
        assertFalse(image.isAlphaPremultiplied)
        val actual =
            IntArray(image.width * image.height) { index ->
                val argb = image.getRGB(index % image.width, index / image.width)
                (argb shl COLOR_SHIFT) or (argb ushr ALPHA_SHIFT)
            }
        assertArrayEquals(expected.map(::hex).toTypedArray(), actual.map(::hex).toTypedArray())
    }

    /** Asserts that [bytes] decode to `Unsupported`. */
    fun assertUnsupported(bytes: ByteArray) {
        assertEquals(PngImportDecodeResult.Unsupported, PngImportDecoder.decode(bytes))
    }

    private fun hex(value: Int): String = "%08x".format(value)
}

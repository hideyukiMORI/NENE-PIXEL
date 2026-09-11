package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import java.nio.ByteBuffer
import java.util.zip.CRC32

internal object PngEncoder {
    fun encode(snapshot: PixelSnapshot): PngBytes {
        val width = snapshot.size.width.value
        val height = snapshot.size.height.value
        val output = ByteBuffer.allocate(FIXED_BYTE_COUNT + height * (CHANNEL_COUNT * width + ROW_OVERHEAD))
        output.putLong(SIGNATURE)
        val header =
            ByteBuffer
                .allocate(HEADER_BYTE_COUNT)
                .putInt(width)
                .putInt(height)
                .put(BIT_DEPTH)
                .put(COLOR_TYPE)
                .put(0)
                .put(0)
                .put(0)
                .array()
        chunk(output, "IHDR", header)
        chunk(output, "sRGB", byteArrayOf(RELATIVE_COLORIMETRIC))
        chunk(output, "IDAT", PngScanlines.encode(snapshot))
        chunk(output, "IEND", byteArrayOf())
        check(!output.hasRemaining())
        return PngBytes.create(output.array())
    }

    private fun chunk(
        output: ByteBuffer,
        type: String,
        payload: ByteArray,
    ) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(payload)
        output
            .putInt(payload.size)
            .put(typeBytes)
            .put(payload)
            .putInt(crc.value.toInt())
    }

    private const val SIGNATURE: Long = -8552249625308161526L
    private const val FIXED_BYTE_COUNT: Int = 76
    private const val ROW_OVERHEAD: Int = 6
    private const val CHANNEL_COUNT: Int = 4
    private const val HEADER_BYTE_COUNT: Int = 13
    private const val BIT_DEPTH: Byte = 8
    private const val COLOR_TYPE: Byte = 6
    private const val RELATIVE_COLORIMETRIC: Byte = 1
}

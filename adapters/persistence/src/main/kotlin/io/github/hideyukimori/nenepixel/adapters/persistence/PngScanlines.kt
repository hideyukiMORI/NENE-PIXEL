package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import java.nio.ByteBuffer
import java.util.zip.Adler32

internal object PngScanlines {
    fun encode(snapshot: PixelSnapshot): ByteArray {
        val rowBytes = CHANNEL_COUNT * snapshot.size.width.value + 1
        val height = snapshot.size.height.value
        val raw = filteredRows(snapshot, rowBytes)
        val output = ByteBuffer.allocate(ZLIB_OVERHEAD + raw.size + BLOCK_OVERHEAD * height)
        output.put(ZLIB_METHOD).put(ZLIB_FLAGS)
        repeat(height) { row ->
            output.put(if (row == height - 1) FINAL_BLOCK else CONTINUING_BLOCK)
            littleEndianShort(output, rowBytes)
            littleEndianShort(output, rowBytes.inv())
            output.put(raw, row * rowBytes, rowBytes)
        }
        val adler = Adler32()
        adler.update(raw)
        output.putInt(adler.value.toInt())
        check(!output.hasRemaining())
        return output.array()
    }

    private fun filteredRows(
        snapshot: PixelSnapshot,
        rowBytes: Int,
    ): ByteArray {
        val pixels = snapshot.copyPackedRgba8888()
        val width = snapshot.size.width.value
        return ByteArray(rowBytes * snapshot.size.height.value) { offset ->
            val columnByte = offset % rowBytes
            if (columnByte == 0) {
                0
            } else {
                val pixel = pixels[(offset / rowBytes) * width + (columnByte - 1) / CHANNEL_COUNT]
                val shift = (CHANNEL_COUNT - 1 - (columnByte - 1) % CHANNEL_COUNT) * BYTE_BITS
                (pixel ushr shift).toByte()
            }
        }
    }

    private fun littleEndianShort(
        output: ByteBuffer,
        value: Int,
    ) {
        output.put(value.toByte()).put((value ushr BYTE_BITS).toByte())
    }

    private const val CHANNEL_COUNT: Int = 4
    private const val BYTE_BITS: Int = 8
    private const val ZLIB_OVERHEAD: Int = 6
    private const val BLOCK_OVERHEAD: Int = 5
    private const val ZLIB_METHOD: Byte = 0x78
    private const val ZLIB_FLAGS: Byte = 0x01
    private const val FINAL_BLOCK: Byte = 1
    private const val CONTINUING_BLOCK: Byte = 0
}

package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.Frame
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteEntry
import java.nio.ByteBuffer
import java.util.zip.CRC32

/** Historical wire fixtures only, never a production writer or live document owner. */
internal object HistoricalProjectTestBytes {
    /** Historical single-layer wire fixture, independent of the production current-version encoder. */
    fun v2ProjectBytes(document: DocumentState): ByteArray {
        val layer = singleDefaultFrame(document).layers.single()
        check(layer.name.value.isEmpty() && layer.visibility == LayerVisibility.Visible)
        check(
            layer.snapshot.copyCoverage().withIndex().all { (byte, bits) ->
                val remaining = document.size.pixelCount.toInt() - byte * Byte.SIZE_BITS
                val expected = if (remaining >= Byte.SIZE_BITS) 255 else (1 shl remaining) - 1
                bits.toInt() and 255 == expected
            },
        )
        val entries = document.definition.palette.entries()
        val output = ByteBuffer.allocate(41 + 4 * entries.size + document.size.pixelCount.toInt() + 4)
        writeHeader(output, document, entries, 2)
        output.put(layer.snapshot.copyPackedIndices())
        return finish(output)
    }

    /** Historical v3 records, admitted only for one default frame, independent of the current writer. */
    fun v3ProjectBytes(document: DocumentState): ByteArray {
        val frame = singleDefaultFrame(document)
        val names = frame.layers.map { it.name.value.encodeToByteArray() }
        val entries = document.definition.palette.entries()
        val pixels = document.size.pixelCount.toInt()
        val length = 41 + 4 * entries.size + 1 + names.sumOf { 6 + it.size + (pixels + 7) / 8 + pixels } + 4
        val output = ByteBuffer.allocate(length)
        writeHeader(output, document, entries, 3)
        output.put(frame.layers.size.toByte())
        frame.layers.forEachIndexed { index, layer -> writeLayer(output, layer, names[index]) }
        return finish(output)
    }

    private fun singleDefaultFrame(document: DocumentState): Frame {
        val frame = document.frames.single()
        check(frame.id == FrameId.first())
        check(frame.duration == FrameDuration.default)
        return frame
    }

    private fun writeHeader(
        output: ByteBuffer,
        document: DocumentState,
        entries: List<PaletteEntry>,
        version: Short,
    ) {
        output.put(byteArrayOf(0x4e, 0x45, 0x4e, 0x45, 0x50, 0x49, 0x58, 0)).putShort(version)
        output
            .putShort(
                document.size.width.value
                    .toShort(),
            ).putShort(
                document.size.height.value
                    .toShort(),
            )
        document.id.value
            .chunked(2)
            .forEach { output.put(it.toInt(16).toByte()) }
        output.putLong(document.revision.value).putShort(entries.size.toShort())
        output.put(
            document.definition.defaultIndex.value
                .toByte(),
        )
        entries.forEach { output.putInt(it.color.toPackedRgba8888()) }
    }

    private fun writeLayer(
        output: ByteBuffer,
        layer: Layer,
        name: ByteArray,
    ) {
        output.putInt(layer.id.value).put(if (layer.visibility == LayerVisibility.Visible) 1.toByte() else 0.toByte())
        output.put(name.size.toByte()).put(name)
        output.put(layer.snapshot.copyCoverage()).put(layer.snapshot.copyPackedIndices())
    }

    private fun finish(output: ByteBuffer): ByteArray {
        val crc = CRC32().also { it.update(output.array(), 0, output.position()) }
        output.putInt(crc.value.toInt())
        return output.array()
    }
}

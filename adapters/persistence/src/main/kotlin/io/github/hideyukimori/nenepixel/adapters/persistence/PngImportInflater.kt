package io.github.hideyukimori.nenepixel.adapters.persistence

import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Inflates the `IDAT` data of a PNG file to import (ADR 0033) into one buffer of exactly
 * [PngImportHeader.inflatedByteCount] bytes. The data of each chunk is given to the [Inflater] in
 * place, without a copy. Inflation stops when the buffer is full; after that, only a one-byte probe
 * is inflated to find output beyond it.
 */
internal object PngImportInflater {
    /**
     * The inflated rows (filter bytes included), or null when the zlib stream is malformed, asks for
     * a dictionary, ends before the buffer is full, has output beyond it or does not end.
     */
    fun inflate(
        encoded: ByteArray,
        structure: PngImportStructure,
    ): ByteArray? {
        val output = ByteArray(structure.header.inflatedByteCount)
        val inflater = Inflater()
        return try {
            val run = PngImportInflation(inflater, output)
            PngImportChunks.forEachData(encoded, structure.data) { offset, length -> run.feed(encoded, offset, length) }
            output.takeIf { run.isComplete() }
        } catch (_: DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }
}

/** The state of one inflation into [output]. */
private class PngImportInflation(
    private val inflater: Inflater,
    private val output: ByteArray,
) {
    private var filled: Int = 0
    private var excess: Boolean = false
    private val probe: ByteArray = ByteArray(1)

    fun feed(
        encoded: ByteArray,
        offset: Int,
        length: Int,
    ) {
        if (!excess && !inflater.finished()) {
            inflater.setInput(encoded, offset, length)
            drain()
        }
    }

    /** True when the stream ended exactly when the buffer was full. */
    fun isComplete(): Boolean = !excess && filled == output.size && inflater.finished()

    /** False when the stream ended, needs the next chunk, or asks for a dictionary (never given). */
    private fun canInflate(): Boolean = !inflater.finished() && !inflater.needsInput() && !inflater.needsDictionary()

    private fun drain() {
        while (!excess && canInflate()) {
            if (filled < output.size) {
                filled += inflater.inflate(output, filled, output.size - filled)
            } else {
                excess = inflater.inflate(probe) > 0
            }
        }
    }
}

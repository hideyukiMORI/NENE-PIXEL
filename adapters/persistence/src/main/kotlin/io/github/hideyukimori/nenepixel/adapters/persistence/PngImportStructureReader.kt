package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster

/**
 * Reads the structure of a PNG file to import with a bounded parser of its own (ADR 0033): the
 * signature, `IHDR`, `PLTE`, `tRNS`, where the `IDAT` chunks lie and `IEND`. Nothing is inflated here.
 *
 * Every chunk length is compared with the bytes that remain before the chunk is read, and the CRC
 * of every chunk is verified. The sides are checked as soon as `IHDR` is read. Bytes after `IEND`
 * are ignored. Nothing is thrown.
 */
internal object PngImportStructureReader {
    private const val HEADER_BYTES: Int = 13
    private const val HEIGHT_POSITION: Int = 4
    private const val DEPTH_POSITION: Int = 8
    private const val COLOR_TYPE_POSITION: Int = 9
    private const val METHODS_POSITION: Int = 10
    private const val BYTE_BITS: Int = 8
    private const val BYTE_MASK: Int = 0xFF
    private const val INT_BYTES: Int = 4

    /** The eight signature bytes, one ISO-8859-1 character each. */
    private const val SIGNATURE_TEXT: String = "\u0089PNG\r\n\u001A\n"
    private val signature: ByteArray = SIGNATURE_TEXT.toByteArray(Charsets.ISO_8859_1)

    fun read(encoded: ByteArray): PngImportStructureResult =
        encoded
            .takeIf(::hasSignature)
            ?.let { PngImportChunks.read(it, signature.size) }
            ?.takeIf { it.type == PngImportChunk.HEADER && it.length == HEADER_BYTES }
            ?.let { headerResult(encoded, it) }
            ?: PngImportStructureResult.Unsupported

    private fun hasSignature(encoded: ByteArray): Boolean =
        encoded.size >= signature.size && signature.indices.all { encoded[it] == signature[it] }

    private fun headerResult(
        encoded: ByteArray,
        chunk: PngImportChunk,
    ): PngImportStructureResult {
        val at = chunk.dataOffset
        val width = intAt(encoded, at)
        val height = intAt(encoded, at + HEIGHT_POSITION)
        val bitDepth = encoded[at + DEPTH_POSITION].toInt() and BYTE_MASK
        val methodsAreZero = (at + METHODS_POSITION until at + HEADER_BYTES).all { encoded[it] == 0.toByte() }
        val colorType =
            PngImportColorType
                .fromCode(encoded[at + COLOR_TYPE_POSITION].toInt() and BYTE_MASK)
                ?.takeIf { methodsAreZero && bitDepth in it.bitDepths }
        return when {
            width > ImportRaster.MAX_SIDE || height > ImportRaster.MAX_SIDE -> {
                PngImportStructureResult.TooManyPixels
            }

            width < 1 || height < 1 || colorType == null -> {
                PngImportStructureResult.Unsupported
            }

            else -> {
                walk(encoded, PngImportHeader(width, height, bitDepth, colorType), chunk.end)
            }
        }
    }

    /** Walks the chunks from [start] up to `IEND`; a chunk that cannot be read or is refused ends the walk. */
    private fun walk(
        encoded: ByteArray,
        header: PngImportHeader,
        start: Int,
    ): PngImportStructureResult {
        val walk = PngImportChunkWalk(encoded, header)
        var chunk = PngImportChunks.read(encoded, start)
        while (chunk != null && chunk.type != PngImportChunk.END && walk.accept(chunk)) {
            chunk = PngImportChunks.read(encoded, chunk.end)
        }
        return if (chunk != null && chunk.type == PngImportChunk.END) {
            walk.finish(chunk)
        } else {
            PngImportStructureResult.Unsupported
        }
    }

    /** The signed big-endian four-byte value at [offset], which the caller has range checked. */
    private fun intAt(
        bytes: ByteArray,
        offset: Int,
    ): Int =
        (0 until INT_BYTES).fold(0) { value, step ->
            (value shl BYTE_BITS) or (bytes[offset + step].toInt() and BYTE_MASK)
        }
}

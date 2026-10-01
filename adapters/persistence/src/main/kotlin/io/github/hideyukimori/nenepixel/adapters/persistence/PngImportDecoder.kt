package io.github.hideyukimori.nenepixel.adapters.persistence

/**
 * Decodes a PNG file to import with the reader of its own (ADR 0033): reads the structure,
 * inflates the `IDAT` data into one buffer of exactly the expected length, reverses the row
 * filters in place and converts the samples into straight RGBA8888. No colour is converted and
 * nothing is thrown.
 */
internal object PngImportDecoder {
    fun decode(encoded: ByteArray): PngImportDecodeResult =
        try {
            when (val result = PngImportStructureReader.read(encoded)) {
                is PngImportStructureResult.Parsed -> {
                    decodeStructure(encoded, result.structure)
                }

                PngImportStructureResult.TooManyPixels -> {
                    PngImportDecodeResult.TooManyPixels
                }

                PngImportStructureResult.Unsupported -> {
                    PngImportDecodeResult.Unsupported
                }
            }
        } catch (_: OutOfMemoryError) {
            PngImportDecodeResult.TooManyPixels
        }

    private fun decodeStructure(
        encoded: ByteArray,
        structure: PngImportStructure,
    ): PngImportDecodeResult {
        val header = structure.header
        val pixels =
            PngImportInflater
                .inflate(encoded, structure)
                ?.takeIf { PngImportUnfilter.unfilter(it, header) }
                ?.let { PngImportPixels.toRgba(it, structure) }
        return pixels?.let { PngImportDecodeResult.Decoded(header.width, header.height, it) }
            ?: PngImportDecodeResult.Unsupported
    }
}

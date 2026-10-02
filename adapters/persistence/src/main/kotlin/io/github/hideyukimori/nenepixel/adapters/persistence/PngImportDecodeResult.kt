package io.github.hideyukimori.nenepixel.adapters.persistence

/** The outcome of [PngImportDecoder.decode] (ADR 0033). */
internal sealed interface PngImportDecodeResult {
    /** Straight (not premultiplied) RGBA8888, row-major, red in the highest byte. */
    class Decoded(
        val width: Int,
        val height: Int,
        val packedRgba8888: IntArray,
    ) : PngImportDecodeResult

    /** A side above `ImportRaster.MAX_SIDE`, or the memory for the pixels could not be allocated. */
    data object TooManyPixels : PngImportDecodeResult

    /** Malformed bytes or content outside ADR 0033. */
    data object Unsupported : PngImportDecodeResult
}

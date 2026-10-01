package io.github.hideyukimori.nenepixel.adapters.persistence

/**
 * The one place for the PNG import source limits (ADR 0033). The side limit is
 * `ImportRaster.MAX_SIDE`, which [PngImportDecoder] applies; it is not repeated here (ARC-008).
 */
internal object PngImportLimits {
    /** The largest encoded PNG file, in bytes. */
    const val MAX_ENCODED_BYTE_COUNT: Int = 8_388_608

    /** One byte past the limit, so a longer file is seen without reading all of it. */
    const val MAX_PROBE_BYTE_COUNT: Int = MAX_ENCODED_BYTE_COUNT + 1
}

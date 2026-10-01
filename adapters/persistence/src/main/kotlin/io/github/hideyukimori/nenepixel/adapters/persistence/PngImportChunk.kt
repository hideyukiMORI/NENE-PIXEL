package io.github.hideyukimori.nenepixel.adapters.persistence

/** A chunk whose length lies inside the encoded bytes and whose CRC matched (ADR 0033). */
internal class PngImportChunk(
    val type: String,
    val dataOffset: Int,
    val length: Int,
) {
    /** The offset just after the CRC of this chunk. */
    val end: Int = dataOffset + length + CRC_BYTES

    /** True when the first letter of the type is upper case. */
    val isCritical: Boolean = type.first().isUpperCase()

    fun dataOf(encoded: ByteArray): ByteArray = encoded.copyOfRange(dataOffset, dataOffset + length)

    companion object {
        const val HEADER: String = "IHDR"
        const val PALETTE: String = "PLTE"
        const val TRANSPARENCY: String = "tRNS"
        const val DATA: String = "IDAT"
        const val END: String = "IEND"
        const val CRC_BYTES: Int = 4
    }
}

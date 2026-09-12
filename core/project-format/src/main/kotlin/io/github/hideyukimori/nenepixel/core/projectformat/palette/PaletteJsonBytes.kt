package io.github.hideyukimori.nenepixel.core.projectformat.palette

public class PaletteJsonBytes private constructor(
    private val bytes: ByteArray,
) {
    public val byteCount: Int get() = bytes.size

    public fun copyBytes(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean =
        this === other || (other is PaletteJsonBytes && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "PaletteJsonBytes(byteCount=$byteCount)"

    public companion object {
        public const val MAX_FILE_BYTE_COUNT: Int = 16_384
        public const val MAX_PROBE_BYTE_COUNT: Int = MAX_FILE_BYTE_COUNT + 1

        public fun create(bytes: ByteArray): PaletteJsonResult<PaletteJsonBytes> =
            if (bytes.size > MAX_PROBE_BYTE_COUNT) {
                rejected(PaletteJsonRejection.ResourceLimitExceeded(bytes.size, MAX_PROBE_BYTE_COUNT))
            } else {
                accepted(PaletteJsonBytes(bytes.copyOf()))
            }
    }
}

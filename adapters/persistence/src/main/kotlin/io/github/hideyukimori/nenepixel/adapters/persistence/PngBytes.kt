package io.github.hideyukimori.nenepixel.adapters.persistence

internal class PngBytes private constructor(
    private val bytes: ByteArray,
) {
    val byteCount: Int get() = bytes.size

    fun copyBytes(): ByteArray = bytes.copyOf()

    companion object {
        fun create(bytes: ByteArray): PngBytes {
            check(bytes.size <= MAX_BYTE_COUNT)
            return PngBytes(bytes.copyOf())
        }

        const val MAX_BYTE_COUNT: Int = 263_756
    }
}

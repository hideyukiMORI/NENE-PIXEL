package io.github.hideyukimori.nenepixel.adapters.persistence

/**
 * Converts unfiltered PNG rows into straight (not premultiplied) RGBA8888 (ADR 0033). A grey
 * sample becomes equal red, green and blue, scaled to 8 bits by bit replication. A `tRNS` key of
 * types 0 and 2 is compared with the stored samples before any scaling. No colour is converted.
 */
internal object PngImportPixels {
    private const val OPAQUE: Int = 0xFF
    private const val TRANSPARENT: Int = 0
    private const val BYTE_BITS: Int = 8
    private const val BYTE_MASK: Int = 0xFF
    private const val RED_SHIFT: Int = 24
    private const val GREEN_SHIFT: Int = 16
    private const val BLUE_SHIFT: Int = 8
    private const val BLUE: Int = 2
    private const val ALPHA: Int = 3
    private const val ENTRY_BYTES: Int = 3

    /** The pixels of [data], the unfiltered rows of [structure], or null when a palette index is beyond `PLTE`. */
    fun toRgba(
        data: ByteArray,
        structure: PngImportStructure,
    ): IntArray? {
        val header = structure.header
        val samples = PngImportSamples(data, header)
        val count = header.width * header.height
        return when (header.colorType) {
            PngImportColorType.GREY -> {
                grey(samples, count, header.bitDepth, structure.transparency)
            }

            PngImportColorType.TRUECOLOUR -> {
                truecolour(samples, count, structure.transparency)
            }

            PngImportColorType.INDEXED -> {
                indexed(samples, count, checkNotNull(structure.palette), structure.transparency)
            }

            PngImportColorType.GREY_ALPHA -> {
                IntArray(count) { samples.sample(it, 0).let { g -> rgba(g, g, g, samples.sample(it, 1)) } }
            }

            PngImportColorType.TRUECOLOUR_ALPHA -> {
                IntArray(count) {
                    rgba(
                        samples.sample(it, 0),
                        samples.sample(it, 1),
                        samples.sample(it, BLUE),
                        samples.sample(it, ALPHA),
                    )
                }
            }
        }
    }

    private fun grey(
        samples: PngImportSamples,
        count: Int,
        bitDepth: Int,
        transparency: ByteArray?,
    ): IntArray {
        val scale = OPAQUE / ((1 shl bitDepth) - 1)
        val key = transparency?.let { unsigned16(it, 0) }
        return IntArray(count) { pixel ->
            val raw = samples.sample(pixel, 0)
            val grey = raw * scale
            rgba(grey, grey, grey, if (raw == key) TRANSPARENT else OPAQUE)
        }
    }

    private fun truecolour(
        samples: PngImportSamples,
        count: Int,
        transparency: ByteArray?,
    ): IntArray {
        val key = transparency?.let { bytes -> IntArray(ENTRY_BYTES) { unsigned16(bytes, it * 2) } }
        return IntArray(count) { pixel ->
            val red = samples.sample(pixel, 0)
            val green = samples.sample(pixel, 1)
            val blue = samples.sample(pixel, BLUE)
            val keyed = key != null && red == key[0] && green == key[1] && blue == key[BLUE]
            rgba(red, green, blue, if (keyed) TRANSPARENT else OPAQUE)
        }
    }

    private fun indexed(
        samples: PngImportSamples,
        count: Int,
        palette: ByteArray,
        transparency: ByteArray?,
    ): IntArray? {
        val entries = palette.size / ENTRY_BYTES
        return if ((0 until count).all { samples.sample(it, 0) < entries }) {
            IntArray(count) { pixel ->
                val at = samples.sample(pixel, 0) * ENTRY_BYTES
                val alpha = transparency?.getOrNull(at / ENTRY_BYTES)?.let { it.toInt() and BYTE_MASK } ?: OPAQUE
                rgba(byte(palette, at), byte(palette, at + 1), byte(palette, at + BLUE), alpha)
            }
        } else {
            null
        }
    }

    private fun byte(
        bytes: ByteArray,
        at: Int,
    ): Int = bytes[at].toInt() and BYTE_MASK

    private fun unsigned16(
        bytes: ByteArray,
        at: Int,
    ): Int = (byte(bytes, at) shl BYTE_BITS) or byte(bytes, at + 1)

    private fun rgba(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ): Int = (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or (blue shl BLUE_SHIFT) or alpha
}

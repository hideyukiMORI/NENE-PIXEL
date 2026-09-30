package io.github.hideyukimori.nenepixel.adapters.persistence

/**
 * Turns encoded source bytes into a straight sRGB RGBA8888 raster (ADR 0032).
 *
 * A [ReferenceImageDecodeResult.Decoded] result has sides in `1..ReferenceImageLimits.MAX_RESULT_SIDE`
 * and exactly `width * height` row-major packed values. A source whose sides exceed
 * `ReferenceImageLimits.MAX_SOURCE_SIDE` is [ReferenceImageDecodeResult.TooManyPixels]; bytes that
 * are not a readable picture are [ReferenceImageDecodeResult.Unsupported].
 */
internal fun interface ReferenceImageDecoder {
    fun decode(encoded: ByteArray): ReferenceImageDecodeResult
}

package io.github.hideyukimori.nenepixel.adapters.persistence

internal sealed interface ReferenceImageDecodeResult {
    class Decoded(
        val width: Int,
        val height: Int,
        val packedRgba8888: IntArray,
    ) : ReferenceImageDecodeResult

    data object TooManyPixels : ReferenceImageDecodeResult

    data object Unsupported : ReferenceImageDecodeResult
}

package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage

/** The one place for the reference image source limits (ADR 0032). */
internal object ReferenceImageLimits {
    /** The largest encoded source file, in bytes. */
    const val MAX_ENCODED_BYTE_COUNT: Int = 16_777_216

    /** One byte past the limit, so a longer source is seen without reading all of it. */
    const val MAX_PROBE_BYTE_COUNT: Int = MAX_ENCODED_BYTE_COUNT + 1

    /** The largest width or height of the encoded source picture. */
    const val MAX_SOURCE_SIDE: Int = 16_384

    /** The largest width or height of the decoded result. */
    const val MAX_RESULT_SIDE: Int = ReferenceImage.MAX_SIDE

    /** The source types the picker offers and the decoder accepts, in the order the picker lists them. */
    val SUPPORTED_MIME_TYPES: List<String> = listOf("image/png", "image/jpeg", "image/webp")
}

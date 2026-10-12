package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

/** Existing single-frame PNG vectors keep explicit frame admission under the new encoder API. */
internal object PngEncoderTestValues {
    fun encodeSingleFrame(document: DocumentState): PngBytes = PngEncoder.encode(document, document.frames.single())
}

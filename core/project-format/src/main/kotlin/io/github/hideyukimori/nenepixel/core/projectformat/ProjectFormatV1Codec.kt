package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

public object ProjectFormatV1Codec {
    private val decoder: ProjectFormatV1Decoder = ProjectFormatV1Decoder()

    public fun encode(document: DocumentState): ProjectFormatBytes = ProjectFormatV1Encoder.encode(document)

    public fun decode(source: ProjectFormatBytes): ProjectFormatResult<DocumentState> = decoder.decode(source)
}

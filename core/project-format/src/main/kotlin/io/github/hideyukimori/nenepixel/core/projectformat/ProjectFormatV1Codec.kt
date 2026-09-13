package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource

/** @deprecated Use [ProjectFormatCodec]. */
@Deprecated("Use ProjectFormatCodec")
public object ProjectFormatV1Codec {
    public fun decode(source: ProjectFormatBytes): ProjectFormatResult<DocumentImportSource> =
        ProjectFormatCodec.decode(source)
}

package io.github.hideyukimori.nenepixel.core.domain.document

public sealed interface DocumentImportSource {
    public data class Current(
        public val document: DocumentState,
    ) : DocumentImportSource

    public data class Legacy(
        public val source: LegacyRgbaSource,
    ) : DocumentImportSource
}

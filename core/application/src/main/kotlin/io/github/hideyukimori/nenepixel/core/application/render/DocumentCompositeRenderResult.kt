package io.github.hideyukimori.nenepixel.core.application.render

/** Result of composing a document's layers with a draft palette definition. */
public sealed interface DocumentCompositeRenderResult {
    public data class Rendered internal constructor(
        public val image: DocumentCompositeImage,
    ) : DocumentCompositeRenderResult

    /** A layer uses a palette index that the draft definition does not contain. */
    public data object IndexOutsidePalette : DocumentCompositeRenderResult
}

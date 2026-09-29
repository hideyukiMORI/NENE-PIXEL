package io.github.hideyukimori.nenepixel.core.application.render

/** Result of composing a document with the draft of a palette edit session. */
public sealed interface PaletteDraftCompositeResult {
    /** The picture an Apply of the draft would show. */
    public class Rendered internal constructor(
        public val image: DocumentCompositeImage,
    ) : PaletteDraftCompositeResult

    /** The document's palette is not the palette the session started from; show the document as it is. */
    public data object SourceMismatch : PaletteDraftCompositeResult
}

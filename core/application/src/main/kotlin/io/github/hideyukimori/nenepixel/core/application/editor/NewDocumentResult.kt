package io.github.hideyukimori.nenepixel.core.application.editor

public sealed interface NewDocumentResult {
    public val state: EditorRuntimeState

    public data class Created internal constructor(
        override val state: EditorRuntimeState,
    ) : NewDocumentResult

    public data class Rejected internal constructor(
        public val rejection: NewDocumentRejection,
        override val state: EditorRuntimeState,
    ) : NewDocumentResult
}

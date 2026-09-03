package io.github.hideyukimori.nenepixel.core.application.editor

public sealed interface NewDocumentRequestResult {
    public data class Created internal constructor(
        public val request: NewDocumentRequest,
    ) : NewDocumentRequestResult

    public data class Rejected internal constructor(
        public val rejection: NewDocumentRejection,
    ) : NewDocumentRequestResult
}

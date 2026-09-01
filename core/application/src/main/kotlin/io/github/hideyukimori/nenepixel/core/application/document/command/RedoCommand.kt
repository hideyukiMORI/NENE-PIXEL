package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.Revision

public data class RedoCommand private constructor(
    public val targetDocumentId: DocumentId,
    public val targetRevision: Revision,
) : DocumentCommand {
    public companion object {
        public fun create(
            targetDocumentId: DocumentId,
            targetRevision: Revision,
        ): RedoCommand = RedoCommand(targetDocumentId, targetRevision)
    }
}

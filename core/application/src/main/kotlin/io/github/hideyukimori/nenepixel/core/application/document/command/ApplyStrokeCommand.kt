package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke

public data class ApplyStrokeCommand private constructor(
    public val targetDocumentId: DocumentId,
    public val targetRevision: Revision,
    public val stroke: Stroke,
) : DocumentCommand {
    public companion object {
        public fun create(
            targetDocumentId: DocumentId,
            targetRevision: Revision,
            stroke: Stroke,
        ): ApplyStrokeCommand = ApplyStrokeCommand(targetDocumentId, targetRevision, stroke)
    }
}

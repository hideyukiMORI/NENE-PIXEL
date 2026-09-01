package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition

public sealed interface RejectionReason {
    public data class TargetDocumentMismatch internal constructor(
        public val expected: DocumentId,
        public val actual: DocumentId,
    ) : RejectionReason

    public data class CanvasMismatch internal constructor(
        public val expected: CanvasSize,
        public val actual: CanvasSize,
    ) : RejectionReason

    public data class RevisionMismatch internal constructor(
        public val expected: Revision,
        public val actual: Revision,
    ) : RejectionReason

    public data class PixelBeforeValueMismatch internal constructor(
        public val position: PixelPosition,
        public val expected: PixelColor,
        public val actual: PixelColor,
    ) : RejectionReason

    public data object NoEffectiveChange : RejectionReason

    public data object RevisionOverflow : RejectionReason

    public data object NoUndoAvailable : RejectionReason

    public data object NoRedoAvailable : RejectionReason
}

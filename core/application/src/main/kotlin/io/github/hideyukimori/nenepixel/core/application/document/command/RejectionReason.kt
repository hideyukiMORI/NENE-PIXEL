package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection

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
        public val expected: PaletteIndex,
        public val actual: PaletteIndex,
    ) : RejectionReason

    public data object NoEffectiveChange : RejectionReason

    public data object RevisionOverflow : RejectionReason

    public data object NoUndoAvailable : RejectionReason

    public data object NoRedoAvailable : RejectionReason

    public data class HistoryEntryAboveRetainedChangeMaximum internal constructor(
        public val attemptedCount: Int,
        public val maximum: Int,
    ) : RejectionReason

    public data object HistoryPositionExhausted : RejectionReason

    public data object SourceOwnerMismatch : RejectionReason

    public data object SourceHistoryMismatch : RejectionReason

    public data class PaletteSourceMismatch internal constructor(
        public val expected: PaletteDefinition,
        public val actual: PaletteDefinition,
    ) : RejectionReason

    public data class InvalidIndexedValue internal constructor(
        public val rejection: DomainValueRejection,
    ) : RejectionReason

    public data class HistoryEntryAboveRetainedPayloadMaximum internal constructor(
        public val attemptedBytes: Long,
        public val maximum: Long,
    ) : RejectionReason
}

package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransition
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.pixelengine.StrokeRasterizationRejection
import io.github.hideyukimori.nenepixel.core.pixelengine.StrokeRasterizationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.rasterizeStroke

internal class ApplyStrokeCommandHandler {
    fun execute(
        currentState: DocumentState,
        command: ApplyStrokeCommand,
    ): DocumentTransitionResult =
        when {
            command.targetDocumentId != currentState.id -> {
                rejected(RejectionReason.TargetDocumentMismatch(command.targetDocumentId, currentState.id))
            }

            command.targetRevision != currentState.revision -> {
                rejected(RejectionReason.RevisionMismatch(command.targetRevision, currentState.revision))
            }

            else -> {
                rasterize(currentState, command)
            }
        }

    private fun rasterize(
        currentState: DocumentState,
        command: ApplyStrokeCommand,
    ): DocumentTransitionResult =
        when (val result = rasterizeStroke(currentState.snapshot, command.stroke)) {
            is StrokeRasterizationResult.Rasterized -> DocumentTransition.create(currentState, result.patch)
            StrokeRasterizationResult.NoChanges -> rejected(RejectionReason.NoEffectiveChange)
            is StrokeRasterizationResult.Rejected -> rejected(result.rejection.toReason())
        }

    private fun StrokeRasterizationRejection.toReason(): RejectionReason =
        when (this) {
            is StrokeRasterizationRejection.CanvasMismatch -> {
                RejectionReason.CanvasMismatch(expected, actual)
            }

            StrokeRasterizationRejection.RevisionOverflow -> {
                RejectionReason.RevisionOverflow
            }
        }

    private fun rejected(reason: RejectionReason): DocumentTransitionResult = DocumentTransitionResult.Rejected(reason)
}

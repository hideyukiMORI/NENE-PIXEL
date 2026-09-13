package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.transition.ChangeSet
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransition
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.StrokeRasterizationRejection
import io.github.hideyukimori.nenepixel.core.pixelengine.StrokeRasterizationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.rasterizeStroke

internal class ApplyStrokeCommandHandler {
    fun execute(
        currentState: DocumentState,
        command: ApplyStrokeCommand,
    ): DocumentTransitionResult =
        when (val entry = currentState.definition.palette.entryAt(command.stroke.effect.targetIndex)) {
            is DomainValueResult.Created -> rasterize(currentState, command)
            is DomainValueResult.Rejected -> rejected(RejectionReason.InvalidIndexedValue(entry.rejection))
        }

    private fun rasterize(
        currentState: DocumentState,
        command: ApplyStrokeCommand,
    ): DocumentTransitionResult =
        when (val result = rasterizeStroke(currentState.snapshot, command.stroke)) {
            is StrokeRasterizationResult.Rasterized -> {
                DocumentTransition.create(
                    currentState,
                    ChangeSet.create(result.patch),
                )
            }

            StrokeRasterizationResult.NoChanges -> {
                rejected(RejectionReason.NoEffectiveChange)
            }

            is StrokeRasterizationResult.Rejected -> {
                rejected(result.rejection.toReason())
            }
        }

    private fun StrokeRasterizationRejection.toReason(): RejectionReason =
        when (this) {
            is StrokeRasterizationRejection.CanvasMismatch -> {
                RejectionReason.CanvasMismatch(expected, actual)
            }

            StrokeRasterizationRejection.RevisionOverflow -> {
                RejectionReason.RevisionOverflow
            }

            is StrokeRasterizationRejection.TargetIndexAboveStorageMaximum -> {
                error("An admitted palette entry exceeded indexed storage: $this")
            }
        }

    private fun rejected(reason: RejectionReason): DocumentTransitionResult = DocumentTransitionResult.Rejected(reason)
}

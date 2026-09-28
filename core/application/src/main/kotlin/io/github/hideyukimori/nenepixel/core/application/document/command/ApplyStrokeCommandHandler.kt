package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.transition.ChangeSet
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransition
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionResult
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerChange
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerIndexChanges
import io.github.hideyukimori.nenepixel.core.application.document.transition.PaletteTransition
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.StrokeRasterizationRejection
import io.github.hideyukimori.nenepixel.core.pixelengine.StrokeRasterizationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.rasterizeStroke

internal class ApplyStrokeCommandHandler {
    fun execute(
        currentState: DocumentState,
        command: ApplyStrokeCommand,
    ): DocumentTransitionResult =
        when (val effect = command.stroke.effect) {
            is StrokeEffect.Paint -> {
                when (val entry = currentState.definition.palette.entryAt(effect.targetIndex)) {
                    is DomainValueResult.Created -> rasterize(currentState, command)
                    is DomainValueResult.Rejected -> rejected(RejectionReason.InvalidIndexedValue(entry.rejection))
                }
            }

            StrokeEffect.Erase -> {
                rasterize(currentState, command)
            }
        }

    private fun rasterize(
        currentState: DocumentState,
        command: ApplyStrokeCommand,
    ): DocumentTransitionResult {
        // Interim target: the bottom layer (migrating documents have one). Replace with the LayerId the gesture
        // captures in Issue #142 S7.
        val layer = currentState.layers.first()
        return when (val result = rasterizeStroke(layer.snapshot, command.stroke)) {
            is StrokeRasterizationResult.Rasterized -> {
                val changes = LayerChange(layer.id, LayerIndexChanges.select(layer.snapshot, result.patch))
                DocumentTransition.create(
                    currentState,
                    ChangeSet.create(
                        currentState,
                        result.patch.afterRevision,
                        PaletteTransition.Unchanged,
                        listOf(changes),
                    ),
                )
            }

            StrokeRasterizationResult.NoChanges -> {
                rejected(RejectionReason.NoEffectiveChange)
            }

            is StrokeRasterizationResult.Rejected -> {
                rejected(result.rejection.toReason())
            }
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

package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.transition.ChangeSet
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransition
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionResult
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerChange
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerIndexChanges
import io.github.hideyukimori.nenepixel.core.application.document.transition.PaletteTransition
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelTransferRasterizationRejection
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelTransferRasterizationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.rasterizePixelTransfer

/** Reuses the ordinary per-layer patch, transition and bounded history route for one transfer. */
internal class TransferPixelsCommandHandler {
    fun execute(
        currentState: DocumentState,
        command: TransferPixelsCommand,
    ): DocumentTransitionResult {
        val layer =
            currentState.frameContainingLayer(command.layerId)?.layers?.firstOrNull { it.id == command.layerId }
                ?: return rejected(RejectionReason.LayerNotFound(command.layerId))
        return when (layer.visibility) {
            LayerVisibility.Hidden -> rejected(RejectionReason.LayerHidden(layer.id))
            LayerVisibility.Visible -> rasterize(currentState, layer, command)
        }
    }

    private fun rasterize(
        currentState: DocumentState,
        layer: Layer,
        command: TransferPixelsCommand,
    ): DocumentTransitionResult =
        when (val result = rasterizePixelTransfer(layer.snapshot, command.transfer)) {
            is PixelTransferRasterizationResult.Rasterized -> {
                val changes = LayerChange(layer.id, LayerIndexChanges.select(layer.snapshot, result.patch))
                transition(currentState, changes)
            }

            PixelTransferRasterizationResult.NoChanges -> {
                rejected(RejectionReason.NoEffectiveChange)
            }

            is PixelTransferRasterizationResult.Rejected -> {
                when (val rejection = result.rejection) {
                    is PixelTransferRasterizationRejection.CanvasMismatch -> {
                        rejected(RejectionReason.CanvasMismatch(rejection.expected, rejection.actual))
                    }
                }
            }
        }

    private fun transition(
        currentState: DocumentState,
        changes: LayerChange,
    ): DocumentTransitionResult =
        when (val next = currentState.revision.advance()) {
            is DomainValueResult.Created -> {
                DocumentTransition.create(
                    currentState,
                    ChangeSet.create(currentState, next.value, PaletteTransition.Unchanged, listOf(changes)),
                )
            }

            is DomainValueResult.Rejected -> {
                rejected(RejectionReason.RevisionOverflow)
            }
        }

    private fun rejected(reason: RejectionReason): DocumentTransitionResult = DocumentTransitionResult.Rejected(reason)
}

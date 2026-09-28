package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.transition.ChangeSet
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransition
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionResult
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerChange
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerIndexChanges
import io.github.hideyukimori.nenepixel.core.application.document.transition.PaletteTransition
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationRejection
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapApplicationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.applyPaletteRemap

internal class ReplacePaletteCommandHandler {
    fun execute(
        currentState: DocumentState,
        command: ReplacePaletteCommand,
    ): DocumentTransitionResult =
        if (currentState.definition != command.remap.source) {
            rejected(RejectionReason.PaletteSourceMismatch(command.remap.source, currentState.definition))
        } else {
            remapLayers(currentState, command.remap)
        }

    // Every layer is remapped, hidden ones included; only layers whose cells change are recorded.
    private fun remapLayers(
        currentState: DocumentState,
        remap: PaletteRemap,
    ): DocumentTransitionResult {
        val changes = ArrayList<LayerChange>(currentState.layers.size)
        for (layer in currentState.layers) {
            val result = applyPaletteRemap(layer.snapshot, remap)
            if (result is PaletteRemapApplicationResult.Rejected) {
                return rejected(result.rejection.toReason())
            }
            if (result is PaletteRemapApplicationResult.Changed) {
                changes += LayerChange(layer.id, LayerIndexChanges.select(layer.snapshot, result.patch))
            }
        }
        return transition(currentState, remap, changes)
    }

    private fun transition(
        currentState: DocumentState,
        remap: PaletteRemap,
        changes: List<LayerChange>,
    ): DocumentTransitionResult {
        val palette =
            if (remap.source ==
                remap.target
            ) {
                PaletteTransition.Unchanged
            } else {
                PaletteTransition.Changed(remap.source, remap.target)
            }
        if (palette == PaletteTransition.Unchanged && changes.isEmpty()) {
            return rejected(RejectionReason.NoEffectiveChange)
        }
        return when (val next = currentState.revision.advance()) {
            is DomainValueResult.Created -> {
                DocumentTransition.create(
                    currentState,
                    ChangeSet.create(currentState, next.value, palette, changes),
                )
            }

            is DomainValueResult.Rejected -> {
                rejected(RejectionReason.RevisionOverflow)
            }
        }
    }

    private fun rejected(reason: RejectionReason): DocumentTransitionResult = DocumentTransitionResult.Rejected(reason)
}

private fun PaletteRemapApplicationRejection.toReason(): RejectionReason =
    when (this) {
        PaletteRemapApplicationRejection.RevisionOverflow -> {
            RejectionReason.RevisionOverflow
        }

        is PaletteRemapApplicationRejection.SourceIndexOutsidePalette -> {
            error("An admitted document index was outside its remap source: $this")
        }
    }

package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.transition.ChangeSet
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransition
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionResult
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTransition
import io.github.hideyukimori.nenepixel.core.application.document.transition.PaletteTransition
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.importing.LayerImportPlan
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** Applies a [LayerImportPlan] as it was computed; the plan's colours are never recomputed (ADR 0033). */
internal class ImportLayerCommandHandler {
    fun execute(
        currentState: DocumentState,
        command: ImportLayerCommand,
    ): DocumentTransitionResult {
        val plan = command.plan
        return when {
            currentState.definition != plan.source -> {
                rejected(RejectionReason.PaletteSourceMismatch(plan.source, currentState.definition))
            }

            currentState.size != plan.snapshot.size -> {
                rejected(RejectionReason.CanvasMismatch(plan.snapshot.size, currentState.size))
            }

            else -> {
                when (val insertion = planLayerInsertion(currentState.layers, command.aboveLayerId)) {
                    is LayerInsertion.Accepted -> transition(currentState, plan, insertion)
                    is LayerInsertion.Refused -> rejected(insertion.reason)
                }
            }
        }
    }

    private fun transition(
        currentState: DocumentState,
        plan: LayerImportPlan,
        insertion: LayerInsertion.Accepted,
    ): DocumentTransitionResult {
        val layer = Layer.create(insertion.id, LayerName.empty, LayerVisibility.Visible, plan.snapshot)
        val palette =
            if (plan.target == plan.source) {
                PaletteTransition.Unchanged
            } else {
                PaletteTransition.Changed(plan.source, plan.target)
            }
        return when (val next = currentState.revision.advance()) {
            is DomainValueResult.Created -> {
                val added = LayerStructureTransition.Added(layer, insertion.position)
                DocumentTransition.create(
                    currentState,
                    ChangeSet.createImportedLayer(currentState, next.value, palette, added),
                )
            }

            is DomainValueResult.Rejected -> {
                rejected(RejectionReason.RevisionOverflow)
            }
        }
    }

    private fun rejected(reason: RejectionReason): DocumentTransitionResult = DocumentTransitionResult.Rejected(reason)
}

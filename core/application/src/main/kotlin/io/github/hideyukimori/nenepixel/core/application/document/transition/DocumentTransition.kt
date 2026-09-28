package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal data class DocumentTransition private constructor(
    val nextState: DocumentState,
    val changeSet: ChangeSet,
) {
    companion object {
        fun create(
            currentState: DocumentState,
            changeSet: ChangeSet,
        ): DocumentTransitionResult {
            val palette = changeSet.paletteTransition
            return when {
                currentState.size != changeSet.canvas -> {
                    rejected(RejectionReason.CanvasMismatch(changeSet.canvas, currentState.size))
                }

                currentState.revision != changeSet.beforeRevision -> {
                    rejected(RejectionReason.RevisionMismatch(changeSet.beforeRevision, currentState.revision))
                }

                palette is PaletteTransition.Changed && currentState.definition != palette.before -> {
                    rejected(RejectionReason.PaletteSourceMismatch(palette.before, currentState.definition))
                }

                else -> {
                    applyLayers(currentState, changeSet)
                }
            }
        }

        private fun applyLayers(
            currentState: DocumentState,
            changeSet: ChangeSet,
        ): DocumentTransitionResult {
            val layers = currentState.layers.toMutableList()
            val rejection =
                changeSet.layerChanges.firstNotNullOfOrNull { change -> applyChange(layers, change) }
                    ?: changeSet.structure.applyTo(layers)
            return if (rejection == null) createState(currentState, changeSet, layers) else rejected(rejection)
        }

        /** Replaces the changed layer in [layers] and returns null, or returns why the change does not apply. */
        private fun applyChange(
            layers: MutableList<Layer>,
            change: LayerChange,
        ): RejectionReason? {
            val position = layers.indexOfFirst { it.id == change.layerId }
            if (position < 0) {
                return RejectionReason.LayerNotFound(change.layerId)
            }
            return when (val applied = change.changes.applyTo(change.layerId, layers[position].snapshot)) {
                is LayerIndexChanges.Application.Applied -> {
                    layers[position] = layers[position].withSnapshot(applied.snapshot)
                    null
                }

                is LayerIndexChanges.Application.Rejected -> {
                    applied.reason
                }
            }
        }

        private fun createState(
            currentState: DocumentState,
            changeSet: ChangeSet,
            layers: List<Layer>,
        ): DocumentTransitionResult =
            when (
                val result =
                    DocumentState.createLayered(
                        currentState.id,
                        changeSet.afterRevision,
                        changeSet.targetDefinition(currentState),
                        layers,
                    )
            ) {
                is DomainValueResult.Created -> {
                    DocumentTransitionResult.Created(
                        DocumentTransition(result.value, changeSet),
                    )
                }

                is DomainValueResult.Rejected -> {
                    rejected(RejectionReason.InvalidIndexedValue(result.rejection))
                }
            }

        private fun rejected(reason: RejectionReason): DocumentTransitionResult =
            DocumentTransitionResult.Rejected(reason)
    }
}

private fun ChangeSet.targetDefinition(currentState: DocumentState): PaletteDefinition =
    when (val transition = paletteTransition) {
        PaletteTransition.Unchanged -> currentState.definition
        is PaletteTransition.Changed -> transition.after
    }

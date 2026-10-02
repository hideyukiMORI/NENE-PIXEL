package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.transition.ChangeSet
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransition
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionResult
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTransition
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** Handles every [LayerCommand]; the family shares one handler so [CommandGateway] keeps one branch for it. */
internal class LayerCommandHandler {
    fun execute(
        currentState: DocumentState,
        command: LayerCommand,
    ): DocumentTransitionResult =
        when (val plan = plan(currentState, command)) {
            is LayerCommandPlan.Accepted -> transition(currentState, plan.structure)
            is LayerCommandPlan.Refused -> DocumentTransitionResult.Rejected(plan.reason)
        }

    private fun plan(
        state: DocumentState,
        command: LayerCommand,
    ): LayerCommandPlan =
        when (command) {
            is AddLayerCommand -> planAdd(state, command.aboveLayerId)
            is DeleteLayerCommand -> planDelete(state.layers, command.layerId)
            is RenameLayerCommand -> planRename(state.layers, command)
            is MoveLayerCommand -> planMove(state.layers, command)
            is SetLayerVisibilityCommand -> planVisibility(state.layers, command)
        }

    private fun planAdd(
        state: DocumentState,
        aboveLayerId: LayerId,
    ): LayerCommandPlan =
        when (val insertion = planLayerInsertion(state.layers, aboveLayerId)) {
            is LayerInsertion.Accepted -> {
                val snapshot = PixelSnapshot.createEmpty(state.size)
                val layer = Layer.create(insertion.id, LayerName.empty, LayerVisibility.Visible, snapshot)
                LayerCommandPlan.Accepted(LayerStructureTransition.Added(layer, insertion.position))
            }

            is LayerInsertion.Refused -> {
                LayerCommandPlan.Refused(insertion.reason)
            }
        }

    private fun planDelete(
        layers: List<Layer>,
        layerId: LayerId,
    ): LayerCommandPlan {
        val position = layers.positionOf(layerId)
        return when {
            position < 0 -> LayerCommandPlan.Refused(RejectionReason.LayerNotFound(layerId))
            layers.size == 1 -> LayerCommandPlan.Refused(RejectionReason.LastLayerNotDeletable)
            else -> LayerCommandPlan.Accepted(LayerStructureTransition.Deleted(layers[position], position))
        }
    }

    private fun planRename(
        layers: List<Layer>,
        command: RenameLayerCommand,
    ): LayerCommandPlan {
        val position = layers.positionOf(command.layerId)
        return when {
            position < 0 -> {
                LayerCommandPlan.Refused(RejectionReason.LayerNotFound(command.layerId))
            }

            layers[position].name == command.name -> {
                LayerCommandPlan.Refused(RejectionReason.NoEffectiveChange)
            }

            else -> {
                val before = layers[position].name
                LayerCommandPlan.Accepted(LayerStructureTransition.Renamed(command.layerId, before, command.name))
            }
        }
    }

    private fun planMove(
        layers: List<Layer>,
        command: MoveLayerCommand,
    ): LayerCommandPlan {
        val from = layers.positionOf(command.layerId)
        val to = command.toPosition
        return when {
            from < 0 -> LayerCommandPlan.Refused(RejectionReason.LayerNotFound(command.layerId))
            to !in layers.indices -> LayerCommandPlan.Refused(RejectionReason.LayerPositionOutOfRange(to, layers.size))
            from == to -> LayerCommandPlan.Refused(RejectionReason.NoEffectiveChange)
            else -> LayerCommandPlan.Accepted(LayerStructureTransition.Moved(command.layerId, from, to))
        }
    }

    private fun planVisibility(
        layers: List<Layer>,
        command: SetLayerVisibilityCommand,
    ): LayerCommandPlan {
        val position = layers.positionOf(command.layerId)
        return when {
            position < 0 -> {
                LayerCommandPlan.Refused(RejectionReason.LayerNotFound(command.layerId))
            }

            layers[position].visibility == command.visibility -> {
                LayerCommandPlan.Refused(RejectionReason.NoEffectiveChange)
            }

            else -> {
                val before = layers[position].visibility
                LayerCommandPlan.Accepted(
                    LayerStructureTransition.VisibilityChanged(command.layerId, before, command.visibility),
                )
            }
        }
    }

    private fun transition(
        currentState: DocumentState,
        structure: LayerStructureTransition,
    ): DocumentTransitionResult =
        when (val next = currentState.revision.advance()) {
            is DomainValueResult.Created -> {
                DocumentTransition.create(currentState, ChangeSet.createStructural(currentState, next.value, structure))
            }

            is DomainValueResult.Rejected -> {
                DocumentTransitionResult.Rejected(RejectionReason.RevisionOverflow)
            }
        }
}

private sealed interface LayerCommandPlan {
    data class Accepted(
        val structure: LayerStructureTransition,
    ) : LayerCommandPlan

    data class Refused(
        val reason: RejectionReason,
    ) : LayerCommandPlan
}

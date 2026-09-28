package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility

/** Reduces the active-layer actions (ADR 0030); no document command is emitted. */
internal fun reduceLayer(
    state: WorkspaceState,
    action: WorkspaceAction.LayerAction,
    document: DocumentState,
): WorkspaceReductionResult =
    when (action) {
        is WorkspaceAction.SelectLayer -> selectLayer(state, action.layerId, document)
    }

/** `true` when [layerId] names a hidden layer of this document; drawing and picks on it are refused (ADR 0030). */
internal fun DocumentState.isLayerHidden(layerId: LayerId): Boolean =
    layers.any { it.id == layerId && it.visibility == LayerVisibility.Hidden }

private fun selectLayer(
    state: WorkspaceState,
    layerId: LayerId,
    document: DocumentState,
): WorkspaceReductionResult =
    when {
        document.layers.none { it.id == layerId } -> {
            WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.LayerNotFound(layerId))
        }

        layerId == state.activeLayerId -> {
            WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.ActiveLayerAlreadySelected)
        }

        else -> {
            WorkspaceReductionResult.Reduced(state.withEditTarget(state.editTarget.withLayer(layerId)))
        }
    }

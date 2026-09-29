package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTransition
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility

/** Chooses the workspace's active layer when the runtime installs or transitions a document (ADR 0030). */
internal object LayerSelectionPolicy {
    /** A newly installed document (new, load, recovery adoption) starts on its top layer. */
    fun onInstall(document: DocumentState): LayerId = document.layers.last().id

    /**
     * The active layer after an applied command, `null` meaning unchanged. [structure] is the transition in the
     * direction it was applied, so an undo arrives inverted. A layer the transition inserted (an add, the redo of an
     * add, or the undo of a delete) is selected; a present active layer is kept; a removed active layer is replaced
     * by the layer now at its position, clamped to the top; any other loss falls back to the top layer.
     */
    fun afterApplied(
        current: LayerId,
        document: DocumentState,
        structure: LayerStructureTransition,
    ): LayerId? =
        when {
            structure is LayerStructureTransition.Added -> {
                structure.layer.id
            }

            document.layers.any { it.id == current } -> {
                null
            }

            structure is LayerStructureTransition.Deleted -> {
                document.layers[minOf(structure.position, document.layers.lastIndex)].id
            }

            else -> {
                onInstall(document)
            }
        }

    /** A gesture keeps drawing only while its captured layer is still in the document and visible. */
    fun gestureTargetLost(
        gestureLayer: LayerId,
        document: DocumentState,
    ): Boolean = document.layers.none { it.id == gestureLayer && it.visibility == LayerVisibility.Visible }
}

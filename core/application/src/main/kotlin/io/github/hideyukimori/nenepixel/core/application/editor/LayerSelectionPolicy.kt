package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentStructureTransition
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTransition
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility

/** Global layer identity resolves one frame, so one focus policy reconciles both components. */
internal object LayerSelectionPolicy {
    fun onInstall(document: DocumentState): LayerId =
        document.frames
            .first()
            .layers
            .last()
            .id

    fun afterApplied(
        current: LayerId,
        document: DocumentState,
        structure: DocumentStructureTransition,
    ): LayerId? =
        when (structure) {
            is DocumentStructureTransition.FrameAdded -> {
                structure.frame.layers
                    .last()
                    .id
            }

            is DocumentStructureTransition.FrameDeleted -> {
                if (document.frameContainingLayer(current) != null) {
                    null
                } else {
                    document.frames[minOf(structure.position, document.frames.lastIndex)]
                        .layers
                        .last()
                        .id
                }
            }

            is DocumentStructureTransition.LayerChanged -> {
                afterLayer(current, document, structure)
            }

            else -> {
                if (document.frameContainingLayer(current) != null) null else onInstall(document)
            }
        }

    private fun afterLayer(
        current: LayerId,
        document: DocumentState,
        change: DocumentStructureTransition.LayerChanged,
    ): LayerId? {
        val structure = change.transition
        return when {
            structure is LayerStructureTransition.Added -> {
                structure.layer.id
            }

            document.frameContainingLayer(current) != null -> {
                null
            }

            structure is LayerStructureTransition.Deleted -> {
                val layers = requireNotNull(document.frameById(change.frameId)).layers
                layers[minOf(structure.position, layers.lastIndex)].id
            }

            else -> {
                onInstall(document)
            }
        }
    }

    fun gestureTargetLost(
        gestureLayer: LayerId,
        document: DocumentState,
    ): Boolean =
        document.frameContainingLayer(gestureLayer)?.layers?.none {
            it.id == gestureLayer &&
                it.visibility == LayerVisibility.Visible
        } !=
            false
}

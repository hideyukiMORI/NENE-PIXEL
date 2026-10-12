package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.Frame
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
            val frames = currentState.frames.toMutableList()
            val pixelRejection =
                if (changeSet.layerChanges.isEmpty()) {
                    null
                } else {
                    FrameLayerDrafts(frames).applyChanges(changeSet.layerChanges)
                }
            val rejection = pixelRejection ?: changeSet.structure.applyTo(frames)
            return if (rejection == null) createState(currentState, changeSet, frames) else rejected(rejection)
        }

        private fun createState(
            currentState: DocumentState,
            changeSet: ChangeSet,
            frames: List<Frame>,
        ): DocumentTransitionResult =
            when (
                val result =
                    DocumentState.create(
                        currentState.id,
                        changeSet.afterRevision,
                        changeSet.targetDefinition(currentState),
                        frames,
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

/** Transition-local staging: one layer-list copy and one successful rebuild per affected frame. */
private class FrameLayerDrafts(
    private val frames: MutableList<Frame>,
) {
    private val drafts = arrayOfNulls<MutableList<Layer>>(frames.size)

    fun applyChanges(changes: List<LayerChange>): RejectionReason? =
        changes.firstNotNullOfOrNull(::applyChange) ?: finish()

    private fun applyChange(change: LayerChange): RejectionReason? {
        val frameIndex = frames.indexOfFirst { frame -> frame.layers.any { it.id == change.layerId } }
        if (frameIndex < 0) return RejectionReason.LayerNotFound(change.layerId)
        val layers = drafts[frameIndex] ?: frames[frameIndex].layers.toMutableList().also { drafts[frameIndex] = it }
        val index = layers.indexOfFirst { it.id == change.layerId }
        return when (val applied = change.changes.applyTo(change.layerId, layers[index].snapshot)) {
            is LayerIndexChanges.Application.Applied -> {
                layers[index] = layers[index].withSnapshot(applied.snapshot)
                // Malformed dense payloads keep the original per-change size refusal before later input errors.
                if (layers.size > 1 && applied.snapshot.size !=
                    frames[frameIndex]
                        .layers
                        .first()
                        .snapshot.size
                ) {
                    rebuild(frameIndex, layers)
                } else {
                    null
                }
            }

            is LayerIndexChanges.Application.Rejected -> {
                applied.reason
            }
        }
    }

    private fun finish(): RejectionReason? =
        frames.indices.firstNotNullOfOrNull { index ->
            drafts[index]?.let { rebuild(index, it) }
        }

    private fun rebuild(
        index: Int,
        layers: List<Layer>,
    ): RejectionReason? {
        val frame = frames[index]
        return frames.replaceFrame(index, Frame.create(frame.id, frame.duration, layers))
    }
}

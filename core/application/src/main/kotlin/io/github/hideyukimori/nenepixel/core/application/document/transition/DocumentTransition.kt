package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchApplicationRejection
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchApplicationResult

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
                currentState.size != changeSet.indexChanges.canvas -> {
                    rejected(RejectionReason.CanvasMismatch(changeSet.indexChanges.canvas, currentState.size))
                }

                currentState.revision != changeSet.beforeRevision -> {
                    rejected(RejectionReason.RevisionMismatch(changeSet.beforeRevision, currentState.revision))
                }

                palette is PaletteTransition.Changed && currentState.definition != palette.before -> {
                    rejected(RejectionReason.PaletteSourceMismatch(palette.before, currentState.definition))
                }

                else -> {
                    applyIndices(currentState, changeSet)
                }
            }
        }

        private fun applyIndices(
            currentState: DocumentState,
            changeSet: ChangeSet,
        ): DocumentTransitionResult =
            when (val indices = changeSet.indexChanges) {
                is IndexChanges.NoIndexChanges -> {
                    createState(currentState, changeSet, currentState.snapshot.withRevision(changeSet.afterRevision))
                }

                is IndexChanges.Changed -> {
                    when (val result = indices.patch.applyTo(currentState.snapshot)) {
                        is PixelPatchApplicationResult.Applied -> createState(currentState, changeSet, result.snapshot)
                        is PixelPatchApplicationResult.Rejected -> rejected(result.rejection.toReason())
                    }
                }
            }

        private fun createState(
            currentState: DocumentState,
            changeSet: ChangeSet,
            snapshot: PixelSnapshot,
        ): DocumentTransitionResult {
            check(
                snapshot.revision == changeSet.afterRevision,
            ) { "Recorded patch revision differs from its ChangeSet." }
            return when (
                val result =
                    DocumentState.create(
                        currentState.id,
                        changeSet.targetDefinition(currentState),
                        snapshot,
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

private fun PixelPatchApplicationRejection.toReason(): RejectionReason =
    when (this) {
        is PixelPatchApplicationRejection.CanvasMismatch -> {
            RejectionReason.CanvasMismatch(expected, actual)
        }

        is PixelPatchApplicationRejection.RevisionMismatch -> {
            RejectionReason.RevisionMismatch(expected, actual)
        }

        is PixelPatchApplicationRejection.BeforeValueMismatch -> {
            RejectionReason.PixelBeforeValueMismatch(
                position,
                expected,
                actual,
            )
        }
    }

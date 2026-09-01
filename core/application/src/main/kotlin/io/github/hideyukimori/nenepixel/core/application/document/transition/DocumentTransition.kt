package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatch
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchApplicationRejection
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchApplicationResult

internal data class DocumentTransition private constructor(
    val nextState: DocumentState,
    val changeSet: ChangeSet,
) {
    companion object {
        fun create(
            currentState: DocumentState,
            patch: PixelPatch,
        ): DocumentTransitionResult =
            when (val result = patch.applyTo(currentState.snapshot)) {
                is PixelPatchApplicationResult.Applied -> {
                    DocumentTransitionResult.Created(
                        DocumentTransition(
                            nextState = DocumentState.create(currentState.id, result.snapshot),
                            changeSet = ChangeSet.create(patch),
                        ),
                    )
                }

                is PixelPatchApplicationResult.Rejected -> {
                    DocumentTransitionResult.Rejected(result.rejection.toReason())
                }
            }
    }
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
            RejectionReason.PixelBeforeValueMismatch(position, expected, actual)
        }
    }

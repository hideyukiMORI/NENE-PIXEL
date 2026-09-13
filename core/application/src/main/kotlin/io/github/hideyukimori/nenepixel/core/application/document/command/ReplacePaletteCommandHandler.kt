package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.transition.ChangeSet
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransition
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionResult
import io.github.hideyukimori.nenepixel.core.application.document.transition.IndexChanges
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
            when (val result = applyPaletteRemap(currentState.snapshot, command.remap)) {
                is PaletteRemapApplicationResult.Changed -> {
                    transition(currentState, command.remap, IndexChanges.Changed(result.patch))
                }

                PaletteRemapApplicationResult.NoIndexChanges -> {
                    transition(currentState, command.remap, IndexChanges.NoIndexChanges(currentState.size))
                }

                is PaletteRemapApplicationResult.Rejected -> {
                    rejected(result.rejection.toReason())
                }
            }
        }

    private fun transition(
        currentState: DocumentState,
        remap: PaletteRemap,
        indices: IndexChanges,
    ): DocumentTransitionResult {
        val palette =
            if (remap.source ==
                remap.target
            ) {
                PaletteTransition.Unchanged
            } else {
                PaletteTransition.Changed(remap.source, remap.target)
            }
        if (palette == PaletteTransition.Unchanged && indices is IndexChanges.NoIndexChanges) {
            return rejected(RejectionReason.NoEffectiveChange)
        }
        return when (val next = currentState.revision.advance()) {
            is DomainValueResult.Created -> {
                DocumentTransition.create(
                    currentState,
                    ChangeSet.create(currentState.revision, next.value, palette, indices),
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

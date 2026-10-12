package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.transition.ChangeSet
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentStructureTransition
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransition
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.Frame
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** All saved frame edits share one planner, hierarchical transition and ordinary history path. */
internal class FrameCommandHandler {
    fun execute(
        state: DocumentState,
        command: FrameCommand,
    ): DocumentTransitionResult {
        val frame =
            state.frameById(command.frameId)
                ?: return rejected(RejectionReason.FrameNotFound(command.frameId))
        return when (val plan = plan(state, frame, command)) {
            is FramePlan.Accepted -> transition(state, plan.structure)
            is FramePlan.Refused -> rejected(plan.reason)
        }
    }

    private fun plan(
        state: DocumentState,
        frame: Frame,
        command: FrameCommand,
    ): FramePlan =
        when (command) {
            is AddFrameCommand, is DuplicateFrameCommand -> {
                inserted(state, frame, command)
            }

            is DeleteFrameCommand -> {
                if (state.frames.size == 1) {
                    FramePlan.Refused(RejectionReason.LastFrameNotDeletable)
                } else {
                    FramePlan.Accepted(DocumentStructureTransition.FrameDeleted(frame, state.frames.indexOf(frame)))
                }
            }

            is MoveFrameCommand -> {
                moved(state, frame, command.toPosition)
            }

            is SetFrameDurationCommand -> {
                if (frame.duration ==
                    command.duration
                ) {
                    FramePlan.Refused(RejectionReason.NoEffectiveChange)
                } else {
                    FramePlan.Accepted(
                        DocumentStructureTransition.FrameDurationChanged(frame.id, frame.duration, command.duration),
                    )
                }
            }
        }

    private fun moved(
        state: DocumentState,
        frame: Frame,
        to: Int,
    ): FramePlan {
        val from = state.frames.indexOf(frame)
        return when {
            to !in state.frames.indices -> {
                FramePlan.Refused(
                    RejectionReason.FramePositionOutOfRange(to, state.frames.size),
                )
            }

            from == to -> {
                FramePlan.Refused(RejectionReason.NoEffectiveChange)
            }

            else -> {
                FramePlan.Accepted(DocumentStructureTransition.FrameMoved(frame.id, from, to))
            }
        }
    }

    private fun inserted(
        state: DocumentState,
        source: Frame,
        command: FrameCommand,
    ): FramePlan {
        val count = if (command is DuplicateFrameCommand) source.layers.size else 1
        return when (val insertion = planFrameInsertion(state, source.id, count)) {
            is FrameInsertion.Refused -> {
                FramePlan.Refused(insertion.reason)
            }

            is FrameInsertion.Accepted -> {
                val layers = insertedLayers(state, source, command, insertion)
                val duration = if (command is DuplicateFrameCommand) source.duration else FrameDuration.default
                val frame = required(Frame.create(insertion.id, duration, layers))
                FramePlan.Accepted(DocumentStructureTransition.FrameAdded(frame, insertion.position))
            }
        }
    }

    private fun insertedLayers(
        state: DocumentState,
        source: Frame,
        command: FrameCommand,
        insertion: FrameInsertion.Accepted,
    ): List<Layer> =
        if (command is DuplicateFrameCommand) {
            source.layers.mapIndexed { index, layer ->
                Layer.create(insertion.layerIds[index], layer.name, layer.visibility, layer.snapshot)
            }
        } else {
            listOf(
                Layer.create(
                    insertion.layerIds.single(),
                    LayerName.empty,
                    LayerVisibility.Visible,
                    PixelSnapshot.createEmpty(state.size),
                ),
            )
        }

    private fun transition(
        state: DocumentState,
        structure: DocumentStructureTransition,
    ): DocumentTransitionResult =
        when (val revision = state.revision.advance()) {
            is DomainValueResult.Created -> {
                DocumentTransition.create(
                    state,
                    ChangeSet.createStructural(state, revision.value, structure),
                )
            }

            is DomainValueResult.Rejected -> {
                rejected(RejectionReason.RevisionOverflow)
            }
        }

    private fun rejected(reason: RejectionReason): DocumentTransitionResult = DocumentTransitionResult.Rejected(reason)

    private fun <T> required(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Planned frame rejected: ${result.rejection}")
        }
}

private sealed interface FramePlan {
    data class Accepted(
        val structure: DocumentStructureTransition,
    ) : FramePlan

    data class Refused(
        val reason: RejectionReason,
    ) : FramePlan
}

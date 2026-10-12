package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.domain.frame.Frame
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** One hierarchical structure payload; every inverse uses recorded identities and positions (ADR0041). */
internal sealed interface DocumentStructureTransition {
    val retainedByteCount: Long

    fun inverse(): DocumentStructureTransition

    fun applyTo(frames: MutableList<Frame>): RejectionReason?

    data object None : DocumentStructureTransition {
        override val retainedByteCount: Long = 0L

        override fun inverse(): DocumentStructureTransition = this

        override fun applyTo(frames: MutableList<Frame>): RejectionReason? = null
    }

    data class LayerChanged(
        val frameId: FrameId,
        val transition: LayerStructureTransition,
    ) : DocumentStructureTransition {
        override val retainedByteCount: Long get() = 4L + transition.retainedByteCount

        override fun inverse(): DocumentStructureTransition = LayerChanged(frameId, transition.inverse())

        override fun applyTo(frames: MutableList<Frame>): RejectionReason? {
            val index = frames.indexOfFirst { it.id == frameId }
            if (index < 0) return RejectionReason.FrameNotFound(frameId)
            val frame = frames[index]
            val layers = frame.layers.toMutableList()
            val refusal = transition.applyTo(layers)
            return if (refusal !=
                null
            ) {
                refusal
            } else {
                frames.replaceFrame(index, Frame.create(frame.id, frame.duration, layers))
            }
        }
    }

    data class FrameAdded(
        val frame: Frame,
        val position: Int,
    ) : DocumentStructureTransition {
        override val retainedByteCount: Long get() = frame.payloadBytes()

        override fun inverse(): DocumentStructureTransition = FrameDeleted(frame, position)

        override fun applyTo(frames: MutableList<Frame>): RejectionReason? =
            if (frames.any { it.id == frame.id } || position !in 0..frames.size) {
                RejectionReason.FrameStructureMismatch(frame.id)
            } else {
                frames.add(position, frame)
                null
            }
    }

    data class FrameDeleted(
        val frame: Frame,
        val position: Int,
    ) : DocumentStructureTransition {
        override val retainedByteCount: Long get() = frame.payloadBytes()

        override fun inverse(): DocumentStructureTransition = FrameAdded(frame, position)

        override fun applyTo(frames: MutableList<Frame>): RejectionReason? =
            when {
                frames.none { it.id == frame.id } -> {
                    RejectionReason.FrameNotFound(frame.id)
                }

                frames.getOrNull(position) != frame -> {
                    RejectionReason.FrameStructureMismatch(frame.id)
                }

                else -> {
                    frames.removeAt(position)
                    null
                }
            }
    }

    data class FrameMoved(
        val frameId: FrameId,
        val from: Int,
        val to: Int,
    ) : DocumentStructureTransition {
        override val retainedByteCount: Long = 12L

        override fun inverse(): DocumentStructureTransition = FrameMoved(frameId, to, from)

        override fun applyTo(frames: MutableList<Frame>): RejectionReason? {
            val current = frames.indexOfFirst { it.id == frameId }
            return when {
                current < 0 -> {
                    RejectionReason.FrameNotFound(frameId)
                }

                current != from || to !in frames.indices -> {
                    RejectionReason.FrameStructureMismatch(frameId)
                }

                else -> {
                    frames.add(to, frames.removeAt(from))
                    null
                }
            }
        }
    }

    data class FrameDurationChanged(
        val frameId: FrameId,
        val before: FrameDuration,
        val after: FrameDuration,
    ) : DocumentStructureTransition {
        override val retainedByteCount: Long = 12L

        override fun inverse(): DocumentStructureTransition = FrameDurationChanged(frameId, after, before)

        override fun applyTo(frames: MutableList<Frame>): RejectionReason? {
            val index = frames.indexOfFirst { it.id == frameId }
            return when {
                index < 0 -> {
                    RejectionReason.FrameNotFound(frameId)
                }

                frames[index].duration != before -> {
                    RejectionReason.FrameStructureMismatch(frameId)
                }

                else -> {
                    frames[index] = frames[index].withDuration(after)
                    null
                }
            }
        }
    }
}

internal fun MutableList<Frame>.replaceFrame(
    index: Int,
    result: DomainValueResult<Frame>,
): RejectionReason? =
    when (result) {
        is DomainValueResult.Created -> {
            this[index] = result.value
            null
        }

        is DomainValueResult.Rejected -> {
            RejectionReason.InvalidIndexedValue(result.rejection)
        }
    }

private fun Frame.payloadBytes(): Long =
    FRAME_FIELDS_BYTES +
        layers.sumOf { layer ->
            val count = layer.snapshot.size.pixelCount
            LAYER_FIELDS_BYTES +
                layer.name.value
                    .encodeToByteArray()
                    .size + count + (count + BITS_PER_BYTE - 1L) / BITS_PER_BYTE
        }

private const val FRAME_FIELDS_BYTES: Long = 12L
private const val LAYER_FIELDS_BYTES: Long = 6L
private const val BITS_PER_BYTE: Long = 8L

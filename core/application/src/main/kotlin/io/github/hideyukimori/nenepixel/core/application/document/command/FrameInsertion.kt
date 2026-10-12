package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameLimits
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** Reserves every identity before allocation; shared immutable pixels still consume logical records. */
internal sealed interface FrameInsertion {
    data class Accepted(
        val id: FrameId,
        val layerIds: List<LayerId>,
        val position: Int,
    ) : FrameInsertion

    data class Refused(
        val reason: RejectionReason,
    ) : FrameInsertion
}

internal fun planFrameInsertion(
    document: DocumentState,
    afterFrameId: FrameId,
    count: Int,
): FrameInsertion {
    val position = document.frames.indexOfFirst { it.id == afterFrameId }
    val highestFrame = document.frames.maxOf { it.id.value }
    val highestLayer = document.frames.maxOf { frame -> frame.layers.maxOf { it.id.value } }
    return when {
        position < 0 -> {
            FrameInsertion.Refused(RejectionReason.FrameNotFound(afterFrameId))
        }

        document.frames.size >= FrameLimits.MAX_FRAMES -> {
            FrameInsertion.Refused(RejectionReason.FrameLimitReached)
        }

        document.totalLayerCount.toLong() + count > FrameLimits.MAX_TOTAL_LAYER_RECORDS -> {
            FrameInsertion.Refused(RejectionReason.LayerLimitReached)
        }

        highestFrame == Int.MAX_VALUE -> {
            FrameInsertion.Refused(RejectionReason.FrameIdOverflow)
        }

        highestLayer.toLong() + count > Int.MAX_VALUE -> {
            FrameInsertion.Refused(RejectionReason.LayerIdOverflow)
        }

        else -> {
            FrameInsertion.Accepted(
                required(FrameId.create(highestFrame + 1)),
                List(count) { required(LayerId.create(highestLayer + it + 1)) },
                position + 1,
            )
        }
    }
}

private fun <T> required(result: DomainValueResult<T>): T =
    when (result) {
        is DomainValueResult.Created -> result.value
        is DomainValueResult.Rejected -> error("Reserved frame identity rejected: ${result.rejection}")
    }

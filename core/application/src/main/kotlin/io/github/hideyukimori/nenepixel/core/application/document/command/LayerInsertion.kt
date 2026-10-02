package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** Where and under which id a new layer goes; shared by every command that inserts a layer (ADR 0030, ADR 0033). */
internal sealed interface LayerInsertion {
    /** [position] counts from the bottom after the insertion. */
    data class Accepted(
        val id: LayerId,
        val position: Int,
    ) : LayerInsertion

    data class Refused(
        val reason: RejectionReason,
    ) : LayerInsertion
}

/** Places a new layer directly above [aboveLayerId], refusing a missing anchor, a full list or exhausted ids. */
internal fun planLayerInsertion(
    layers: List<Layer>,
    aboveLayerId: LayerId,
): LayerInsertion {
    val below = layers.positionOf(aboveLayerId)
    return when {
        below < 0 -> {
            LayerInsertion.Refused(RejectionReason.LayerNotFound(aboveLayerId))
        }

        layers.size >= LayerLimits.MAX_LAYERS -> {
            LayerInsertion.Refused(RejectionReason.LayerLimitReached)
        }

        else -> {
            when (val next = layers.highestId().next()) {
                is DomainValueResult.Created -> LayerInsertion.Accepted(next.value, below + 1)
                is DomainValueResult.Rejected -> LayerInsertion.Refused(RejectionReason.LayerIdOverflow)
            }
        }
    }
}

internal fun List<Layer>.positionOf(layerId: LayerId): Int = indexOfFirst { it.id == layerId }

// A new layer takes the id after the largest one present; gaps left by deleted lower ids are not filled.
private fun List<Layer>.highestId(): LayerId {
    val highest = maxBy { it.id.value }
    return highest.id
}

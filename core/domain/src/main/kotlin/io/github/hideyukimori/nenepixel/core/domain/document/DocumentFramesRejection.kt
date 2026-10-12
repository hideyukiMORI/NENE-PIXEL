package io.github.hideyukimori.nenepixel.core.domain.document

import io.github.hideyukimori.nenepixel.core.domain.frame.Frame
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameLimits
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection

internal fun documentFramesRejection(
    definition: PaletteDefinition,
    frames: List<Frame>,
): DomainValueRejection? {
    if (frames.size !in 1..FrameLimits.MAX_FRAMES) {
        return DomainValueRejection.DocumentFrameCountOutOfRange(frames.size)
    }
    val count = frames.sumOf { it.layers.size }
    return when {
        count > FrameLimits.MAX_TOTAL_LAYER_RECORDS -> {
            DomainValueRejection.DocumentLayerRecordCountOutOfRange(count)
        }

        frames.distinctBy { it.id }.size != frames.size -> {
            val duplicate =
                frames
                    .groupBy { it.id }
                    .values
                    .first { it.size > 1 }
                    .first()
                    .id
            DomainValueRejection.DuplicateFrameId(duplicate.value)
        }

        else -> {
            allLayerRejection(definition, frames.flatMap { it.layers })
        }
    }
}

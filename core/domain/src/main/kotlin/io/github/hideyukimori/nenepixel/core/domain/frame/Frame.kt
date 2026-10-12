package io.github.hideyukimori.nenepixel.core.domain.frame

import io.github.hideyukimori.nenepixel.core.domain.document.layerListRejection
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

/** One immutable chronological frame; palette and canvas compatibility belong to its document. */
public class Frame private constructor(
    public val id: FrameId,
    public val duration: FrameDuration,
    public val layers: List<Layer>,
) {
    public fun withDuration(duration: FrameDuration): Frame = Frame(id, duration, layers)

    override fun equals(other: Any?): Boolean =
        this === other || (other is Frame && id == other.id && duration == other.duration && layers == other.layers)

    override fun hashCode(): Int = 31 * (31 * id.hashCode() + duration.hashCode()) + layers.hashCode()

    override fun toString(): String = "Frame(id=$id, duration=$duration, layers=$layers)"

    public companion object {
        public fun create(
            id: FrameId,
            duration: FrameDuration,
            layers: List<Layer>,
        ): DomainValueResult<Frame> =
            layerListRejection(layers)?.let { rejected(it) } ?: created(Frame(id, duration, layers.toList()))
    }
}

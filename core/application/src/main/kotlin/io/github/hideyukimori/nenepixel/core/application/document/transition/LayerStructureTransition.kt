package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility

/**
 * One change to the ordered layer list (ADR 0030). Positions count from the bottom, starting at 0.
 * [Added.position] and [Moved.to] are positions after the change; [Deleted.position] and [Moved.from] are
 * positions before it.
 */
internal sealed interface LayerStructureTransition {
    /** Bytes this transition keeps in history beyond its [ChangeSet] header. */
    val retainedByteCount: Long

    fun inverse(): LayerStructureTransition

    /** Changes [layers] (bottom first) in place and returns null, or returns why the transition does not apply. */
    fun applyTo(layers: MutableList<Layer>): RejectionReason?

    data object None : LayerStructureTransition {
        override val retainedByteCount: Long = 0L

        override fun inverse(): LayerStructureTransition = this

        override fun applyTo(layers: MutableList<Layer>): RejectionReason? = null
    }

    data class Added(
        val layer: Layer,
        val position: Int,
    ) : LayerStructureTransition {
        // An all-Empty added layer is rebuilt from its size, so only its name is retained. A layer covering a cell
        // (an imported layer, or the undo of a delete) keeps its pixels like a deleted layer, so an add and its
        // inverse charge the same (ADR 0033). Decided once at construction, not per read.
        override val retainedByteCount: Long =
            if (layer.snapshot.copyCoverage().any { it != NO_COVERAGE }) {
                layer.pixelByteCount()
            } else {
                layer.name.utf8ByteCount()
            }

        override fun inverse(): LayerStructureTransition = Deleted(layer, position)

        override fun applyTo(layers: MutableList<Layer>): RejectionReason? =
            if (layers.any { it.id == layer.id } || position !in 0..layers.size) {
                RejectionReason.LayerStructureMismatch(layer.id)
            } else {
                layers.add(position, layer)
                null
            }
    }

    data class Deleted(
        val layer: Layer,
        val position: Int,
    ) : LayerStructureTransition {
        override val retainedByteCount: Long
            get() = layer.pixelByteCount()

        override fun inverse(): LayerStructureTransition = Added(layer, position)

        override fun applyTo(layers: MutableList<Layer>): RejectionReason? =
            when {
                layers.none { it.id == layer.id } -> {
                    RejectionReason.LayerNotFound(layer.id)
                }

                layers.getOrNull(position) != layer -> {
                    RejectionReason.LayerStructureMismatch(layer.id)
                }

                else -> {
                    layers.removeAt(position)
                    null
                }
            }
    }

    data class Renamed(
        val layerId: LayerId,
        val before: LayerName,
        val after: LayerName,
    ) : LayerStructureTransition {
        override val retainedByteCount: Long
            get() = before.utf8ByteCount() + after.utf8ByteCount()

        override fun inverse(): LayerStructureTransition = Renamed(layerId, after, before)

        override fun applyTo(layers: MutableList<Layer>): RejectionReason? =
            layers.replaceLayer(layerId, { it.name == before }) { it.withName(after) }
    }

    data class Moved(
        val layerId: LayerId,
        val from: Int,
        val to: Int,
    ) : LayerStructureTransition {
        override val retainedByteCount: Long = 0L

        override fun inverse(): LayerStructureTransition = Moved(layerId, to, from)

        override fun applyTo(layers: MutableList<Layer>): RejectionReason? {
            val current = layers.indexOfFirst { it.id == layerId }
            return when {
                current < 0 -> {
                    RejectionReason.LayerNotFound(layerId)
                }

                current != from || to !in layers.indices -> {
                    RejectionReason.LayerStructureMismatch(layerId)
                }

                else -> {
                    layers.add(to, layers.removeAt(from))
                    null
                }
            }
        }
    }

    data class VisibilityChanged(
        val layerId: LayerId,
        val before: LayerVisibility,
        val after: LayerVisibility,
    ) : LayerStructureTransition {
        override val retainedByteCount: Long = 0L

        override fun inverse(): LayerStructureTransition = VisibilityChanged(layerId, after, before)

        override fun applyTo(layers: MutableList<Layer>): RejectionReason? =
            layers.replaceLayer(layerId, { it.visibility == before }) { it.withVisibility(after) }
    }
}

private const val BITS_PER_BYTE: Long = 8L

private fun LayerName.utf8ByteCount(): Long = value.encodeToByteArray().size.toLong()

private const val NO_COVERAGE: Byte = 0

private fun Layer.pixelByteCount(): Long {
    val pixelCount = snapshot.size.pixelCount
    return pixelCount + (pixelCount + BITS_PER_BYTE - 1L) / BITS_PER_BYTE + name.utf8ByteCount()
}

private fun MutableList<Layer>.replaceLayer(
    layerId: LayerId,
    matches: (Layer) -> Boolean,
    update: (Layer) -> Layer,
): RejectionReason? {
    val position = indexOfFirst { it.id == layerId }
    return when {
        position < 0 -> {
            RejectionReason.LayerNotFound(layerId)
        }

        !matches(this[position]) -> {
            RejectionReason.LayerStructureMismatch(layerId)
        }

        else -> {
            this[position] = update(this[position])
            null
        }
    }
}

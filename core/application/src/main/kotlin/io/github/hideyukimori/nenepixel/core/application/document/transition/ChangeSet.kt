package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

public class ChangeSet private constructor(
    public val beforeRevision: Revision,
    public val afterRevision: Revision,
    internal val paletteTransition: PaletteTransition,
    private val pixels: Pixels,
) {
    internal val canvas: CanvasSize
        get() = pixels.canvas

    /** At most one entry per layer id; empty when no layer's pixels change. */
    internal val layerChanges: List<LayerChange>
        get() = pixels.layerChanges

    public val renderInvalidation: PixelRegion
        get() {
            val only = layerChanges.singleOrNull()?.changes
            return if (paletteTransition is PaletteTransition.Unchanged && only is LayerIndexChanges.Sparse) {
                only.patch.affectedRegion
            } else {
                fullCanvasRegion()
            }
        }

    internal val retainedChangeCount: Int
        get() = layerChanges.sumOf { it.changes.changeCount }

    internal val retainedByteCount: Long
        get() =
            TRANSITION_BYTES + layerChanges.sumOf { it.changes.retainedByteCount } +
                paletteTransition.retainedByteCount

    internal fun inverse(): ChangeSet =
        ChangeSet(
            afterRevision,
            beforeRevision,
            paletteTransition.inverse(),
            Pixels(canvas, layerChanges.map { it.inverse() }),
        )

    private fun fullCanvasRegion(): PixelRegion {
        val origin = PixelPosition.create(required(PixelX.create(0)), required(PixelY.create(0)))
        return required(PixelRegion.create(canvas, origin, canvas))
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is ChangeSet && beforeRevision == other.beforeRevision && afterRevision == other.afterRevision &&
                    paletteTransition == other.paletteTransition && pixels == other.pixels
            )

    override fun hashCode(): Int =
        HASH_MULTIPLIER *
            (
                HASH_MULTIPLIER * (HASH_MULTIPLIER * beforeRevision.hashCode() + afterRevision.hashCode()) +
                    paletteTransition.hashCode()
            ) + pixels.hashCode()

    override fun toString(): String =
        "ChangeSet(beforeRevision=$beforeRevision, afterRevision=$afterRevision, " +
            "layerChangeCount=${layerChanges.size}, renderInvalidation=$renderInvalidation)"

    // Canvas and layer changes travel together so the constructor stays within four parameters.
    private data class Pixels(
        val canvas: CanvasSize,
        val layerChanges: List<LayerChange>,
    )

    public companion object {
        private const val HASH_MULTIPLIER: Int = 31
        private const val TRANSITION_BYTES: Long = 32L

        /** Records a transition from [source]; its revision and canvas are what [DocumentTransition] checks. */
        internal fun create(
            source: DocumentState,
            afterRevision: Revision,
            paletteTransition: PaletteTransition,
            layerChanges: List<LayerChange>,
        ): ChangeSet {
            val owned = layerChanges.toList()
            require(
                owned.distinctBy { it.layerId }.size == owned.size,
            ) { "A layer may change at most once per ChangeSet." }
            return ChangeSet(source.revision, afterRevision, paletteTransition, Pixels(source.size, owned))
        }

        private fun <T> required(result: DomainValueResult<T>): T =
            when (result) {
                is DomainValueResult.Created -> {
                    result.value
                }

                is DomainValueResult.Rejected -> {
                    error(
                        "Validated change produced an invalid region: ${result.rejection}",
                    )
                }
            }
    }
}

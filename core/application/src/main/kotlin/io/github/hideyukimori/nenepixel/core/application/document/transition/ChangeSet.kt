package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatch

public class ChangeSet private constructor(
    public val beforeRevision: Revision,
    public val afterRevision: Revision,
    internal val paletteTransition: PaletteTransition,
    internal val indexChanges: IndexChanges,
) {
    public val renderInvalidation: PixelRegion
        get() =
            if (paletteTransition is PaletteTransition.Unchanged && indexChanges is IndexChanges.Changed) {
                indexChanges.patch.affectedRegion
            } else {
                fullCanvasRegion()
            }

    internal val retainedChangeCount: Int
        get() = indexChanges.changeCount

    internal val retainedByteCount: Long
        get() = TRANSITION_BYTES + INDEX_CHANGE_BYTES * retainedChangeCount + paletteTransition.retainedByteCount

    internal fun inverse(): ChangeSet =
        ChangeSet(afterRevision, beforeRevision, paletteTransition.inverse(), indexChanges.inverse())

    private fun fullCanvasRegion(): PixelRegion {
        val origin = PixelPosition.create(required(PixelX.create(0)), required(PixelY.create(0)))
        return required(PixelRegion.create(indexChanges.canvas, origin, indexChanges.canvas))
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is ChangeSet && beforeRevision == other.beforeRevision && afterRevision == other.afterRevision &&
                    paletteTransition == other.paletteTransition && indexChanges == other.indexChanges
            )

    override fun hashCode(): Int =
        HASH_MULTIPLIER *
            (
                HASH_MULTIPLIER * (HASH_MULTIPLIER * beforeRevision.hashCode() + afterRevision.hashCode()) +
                    paletteTransition.hashCode()
            ) + indexChanges.hashCode()

    override fun toString(): String =
        "ChangeSet(beforeRevision=$beforeRevision, afterRevision=$afterRevision, renderInvalidation=$renderInvalidation)"

    public companion object {
        private const val HASH_MULTIPLIER: Int = 31
        private const val TRANSITION_BYTES: Long = 32L
        private const val INDEX_CHANGE_BYTES: Long = 6L

        internal fun create(patch: PixelPatch): ChangeSet =
            ChangeSet(
                patch.beforeRevision,
                patch.afterRevision,
                PaletteTransition.Unchanged,
                IndexChanges.Changed(patch),
            )

        internal fun create(
            beforeRevision: Revision,
            afterRevision: Revision,
            paletteTransition: PaletteTransition,
            indexChanges: IndexChanges,
        ): ChangeSet = ChangeSet(beforeRevision, afterRevision, paletteTransition, indexChanges)

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

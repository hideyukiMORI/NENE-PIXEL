package io.github.hideyukimori.nenepixel.core.domain.importing

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

/**
 * What a layer import loses (ADR 0033): the number of colours that take a nearest palette entry
 * and the number of pixels that are not transparent and lie outside the canvas.
 */
public data class LayerImportLoss private constructor(
    public val nearestColorCount: Int,
    public val droppedPixelCount: Int,
) {
    public companion object {
        /** Rejects a negative count; never throws (KOT-007, ARC-008). */
        public fun create(
            nearestColorCount: Int,
            droppedPixelCount: Int,
        ): DomainValueResult<LayerImportLoss> =
            when {
                nearestColorCount < 0 -> {
                    rejected(DomainValueRejection.NegativeImportCount(nearestColorCount))
                }

                droppedPixelCount < 0 -> {
                    rejected(DomainValueRejection.NegativeImportCount(droppedPixelCount))
                }

                else -> {
                    created(LayerImportLoss(nearestColorCount, droppedPixelCount))
                }
            }
    }
}

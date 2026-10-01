package io.github.hideyukimori.nenepixel.core.domain.importing

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

/**
 * The planned result of importing a picture as a new layer (ADR 0033).
 *
 * [target] is [source] unchanged or with colours appended; [snapshot] holds the new layer's cells
 * in [target]'s indices and covers at least one cell. Equality is identity: two plans are the same
 * only when they are the same instance.
 */
public class LayerImportPlan private constructor(
    public val source: PaletteDefinition,
    public val target: PaletteDefinition,
    public val snapshot: PixelSnapshot,
    public val loss: LayerImportLoss,
) {
    public val appendedColorCount: Int = target.palette.entryCount - source.palette.entryCount

    override fun toString(): String =
        "LayerImportPlan(sourceCount=${source.palette.entryCount}, targetCount=${target.palette.entryCount}, " +
            "size=${snapshot.size}, nearestColorCount=${loss.nearestColorCount}, " +
            "droppedPixelCount=${loss.droppedPixelCount})"

    public companion object {
        /** Validates the palette extension, the coverage and the indices; never throws (KOT-007, ARC-008). */
        public fun create(
            source: PaletteDefinition,
            target: PaletteDefinition,
            snapshot: PixelSnapshot,
            loss: LayerImportLoss,
        ): DomainValueResult<LayerImportPlan> =
            when {
                !extends(source, target) -> {
                    rejected(DomainValueRejection.LayerImportPlanPaletteNotExtended)
                }

                snapshot.copyCoverage().none { it != NO_COVERAGE } -> {
                    rejected(DomainValueRejection.ImportPlanWithoutPixels)
                }

                snapshot.maximumIndex.value >= target.palette.entryCount -> {
                    rejected(
                        DomainValueRejection.PaletteIndexOutsidePalette(
                            snapshot.maximumIndex,
                            target.palette.entryCount,
                        ),
                    )
                }

                else -> {
                    created(LayerImportPlan(source, target, snapshot, loss))
                }
            }

        private fun extends(
            source: PaletteDefinition,
            target: PaletteDefinition,
        ): Boolean {
            val sourceCount = source.palette.entryCount
            return target.palette.entryCount >= sourceCount &&
                target.defaultIndex == source.defaultIndex &&
                target.palette.entries().take(sourceCount) == source.palette.entries()
        }

        private const val NO_COVERAGE: Byte = 0
    }
}

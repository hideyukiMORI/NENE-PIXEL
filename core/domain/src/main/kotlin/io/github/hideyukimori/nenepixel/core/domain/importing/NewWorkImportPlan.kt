package io.github.hideyukimori.nenepixel.core.domain.importing

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

/**
 * The planned result of opening a picture as a new work (ADR 0033).
 *
 * The work's canvas is [snapshot]'s size; [snapshot] holds its single layer's cells in
 * [definition]'s indices and covers at least one cell. Equality is identity: two plans are the
 * same only when they are the same instance.
 */
public class NewWorkImportPlan private constructor(
    public val definition: PaletteDefinition,
    public val snapshot: PixelSnapshot,
) {
    override fun toString(): String =
        "NewWorkImportPlan(entryCount=${definition.palette.entryCount}, size=${snapshot.size})"

    public companion object {
        /** Validates the coverage and the indices; never throws (KOT-007, ARC-008). */
        public fun create(
            definition: PaletteDefinition,
            snapshot: PixelSnapshot,
        ): DomainValueResult<NewWorkImportPlan> =
            when {
                snapshot.copyCoverage().none { it != NO_COVERAGE } -> {
                    rejected(DomainValueRejection.ImportPlanWithoutPixels)
                }

                snapshot.maximumIndex.value >= definition.palette.entryCount -> {
                    rejected(
                        DomainValueRejection.PaletteIndexOutsidePalette(
                            snapshot.maximumIndex,
                            definition.palette.entryCount,
                        ),
                    )
                }

                else -> {
                    created(NewWorkImportPlan(definition, snapshot))
                }
            }

        private const val NO_COVERAGE: Byte = 0
    }
}

package io.github.hideyukimori.nenepixel.core.application.workspace.palette

import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapPlanner
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapRejection
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapResult

/** The planned outcome of one draft operation; `Planned.remap.target` is the next draft. */
internal sealed interface PaletteDraftPlan {
    data class Planned(
        val remap: PaletteRemap,
    ) : PaletteDraftPlan

    data object Unchanged : PaletteDraftPlan

    data class Rejected(
        val rejection: WorkspaceActionRejection,
    ) : PaletteDraftPlan
}

/** Plans every draft operation through `PaletteRemapPlanner`, so each entry carries an old-to-new remap. */
internal object PaletteDraftPlanner {
    fun plan(
        before: PaletteDefinition,
        operation: PaletteDraftOperation,
    ): PaletteDraftPlan =
        when (operation) {
            is PaletteDraftOperation.SetSlotColor -> setSlotColor(before, operation)
            is PaletteDraftOperation.SetDefault -> setDefault(before, operation)
            is PaletteDraftOperation.AppendSlot -> appendSlot(before, operation)
            is PaletteDraftOperation.RemoveSlot -> removeSlot(before, operation)
            is PaletteDraftOperation.Reorder -> reorder(before, operation)
        }

    private fun setSlotColor(
        before: PaletteDefinition,
        operation: PaletteDraftOperation.SetSlotColor,
    ): PaletteDraftPlan =
        when (val entry = before.palette.entryAt(operation.index)) {
            is DomainValueResult.Rejected -> {
                outsidePalette(before, operation.index)
            }

            is DomainValueResult.Created -> {
                if (entry.value.color == operation.color) {
                    PaletteDraftPlan.Unchanged
                } else {
                    val colors =
                        before.palette.entries().map { if (it.index == operation.index) operation.color else it.color }
                    byNumber(before, colors, before.defaultIndex)
                }
            }
        }

    private fun setDefault(
        before: PaletteDefinition,
        operation: PaletteDraftOperation.SetDefault,
    ): PaletteDraftPlan =
        when (before.palette.entryAt(operation.index)) {
            is DomainValueResult.Rejected -> {
                outsidePalette(before, operation.index)
            }

            is DomainValueResult.Created -> {
                if (operation.index == before.defaultIndex) {
                    PaletteDraftPlan.Unchanged
                } else {
                    byNumber(before, before.palette.entries().map { it.color }, operation.index)
                }
            }
        }

    private fun appendSlot(
        before: PaletteDefinition,
        operation: PaletteDraftOperation.AppendSlot,
    ): PaletteDraftPlan {
        val colors = before.palette.entries().map { it.color } + operation.color
        return byNumber(before, colors, before.defaultIndex)
    }

    private fun removeSlot(
        before: PaletteDefinition,
        operation: PaletteDraftOperation.RemoveSlot,
    ): PaletteDraftPlan =
        planned(PaletteRemapPlanner.remove(before, operation.removed, operation.replacement ?: before.defaultIndex))

    private fun reorder(
        before: PaletteDefinition,
        operation: PaletteDraftOperation.Reorder,
    ): PaletteDraftPlan = planned(PaletteRemapPlanner.reorder(before, operation.newOrder))

    private fun byNumber(
        before: PaletteDefinition,
        colors: List<PixelColor>,
        defaultIndex: PaletteIndex,
    ): PaletteDraftPlan {
        val after =
            when (val palette = Palette.create(colors)) {
                is DomainValueResult.Rejected -> palette
                is DomainValueResult.Created -> PaletteDefinition.create(palette.value, defaultIndex)
            }
        return when (after) {
            is DomainValueResult.Rejected -> definitionRejected(after)
            is DomainValueResult.Created -> planned(PaletteRemapPlanner.byNumber(before, after.value))
        }
    }

    private fun planned(result: PaletteRemapResult): PaletteDraftPlan =
        when (result) {
            is PaletteRemapResult.Planned -> {
                PaletteDraftPlan.Planned(result.remap)
            }

            is PaletteRemapResult.Rejected -> {
                PaletteDraftPlan.Rejected(WorkspaceActionRejection.PaletteDraftRejected(translate(result.rejection)))
            }
        }

    private fun definitionRejected(result: DomainValueResult.Rejected): PaletteDraftPlan =
        PaletteDraftPlan.Rejected(
            WorkspaceActionRejection.PaletteDraftRejected(PaletteDraftRejection.InvalidDefinition(result.rejection)),
        )

    private fun translate(rejection: PaletteRemapRejection): PaletteDraftRejection =
        when (rejection) {
            is PaletteRemapRejection.InvalidMapping -> {
                PaletteDraftRejection.InvalidDefinition(rejection.rejection)
            }

            is PaletteRemapRejection.OrderSizeMismatch -> {
                PaletteDraftRejection.OrderSizeMismatch(rejection.expectedCount, rejection.attemptedCount)
            }

            is PaletteRemapRejection.OrderIndexOutsidePalette -> {
                PaletteDraftRejection.OrderIndexOutsidePalette(rejection.index)
            }

            is PaletteRemapRejection.RepeatedOrderIndex -> {
                PaletteDraftRejection.RepeatedOrderIndex(rejection.index)
            }

            is PaletteRemapRejection.RemovedIndexOutsidePalette -> {
                PaletteDraftRejection.RemovedIndexOutsidePalette(rejection.index)
            }

            is PaletteRemapRejection.ReplacementIndexOutsidePalette -> {
                PaletteDraftRejection.ReplacementIndexOutsidePalette(rejection.index)
            }

            PaletteRemapRejection.ReplacementIsRemovedIndex -> {
                PaletteDraftRejection.ReplacementIsRemovedIndex
            }

            PaletteRemapRejection.BelowDefinitionMinimum -> {
                PaletteDraftRejection.BelowDefinitionMinimum
            }
        }

    private fun outsidePalette(
        before: PaletteDefinition,
        index: PaletteIndex,
    ): PaletteDraftPlan =
        PaletteDraftPlan.Rejected(WorkspaceActionRejection.PaletteIndexOutsidePalette(index, before.palette.entryCount))
}

/** Draft-history payload estimate: definition 4 B/slot for before and after, mapping 4 B/source slot, plus 8 B. */
internal object PaletteDraftPayload {
    private const val BYTES_PER_SLOT: Long = 4L
    private const val ENTRY_OVERHEAD_BYTES: Long = 8L

    fun bytes(
        before: PaletteDefinition,
        after: PaletteDefinition,
    ): Long =
        BYTES_PER_SLOT * (before.palette.entryCount + after.palette.entryCount) +
            BYTES_PER_SLOT * before.palette.entryCount +
            ENTRY_OVERHEAD_BYTES
}

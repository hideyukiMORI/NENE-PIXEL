package io.github.hideyukimori.nenepixel.core.application.workspace.palette

import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** One bounded starting definition and slot; pointer samples are never retained (ADR 0022 / #168). */
internal data class PaletteColorEdit(
    val start: PaletteDefinition,
    val index: PaletteIndex,
)

internal fun PaletteEditSession.beginColorEdit(index: PaletteIndex): PaletteDraftTransition =
    when {
        isColorEditActive -> {
            colorEditRejected(PaletteDraftRejection.ColorEditActive)
        }

        pendingImport != null -> {
            colorEditRejected(PaletteDraftRejection.ImportPending)
        }

        else -> {
            when (draft.palette.entryAt(index)) {
                is DomainValueResult.Created -> {
                    PaletteDraftTransition.Changed(withPreview(edit = PaletteColorEdit(draft, index)))
                }

                is DomainValueResult.Rejected -> {
                    PaletteDraftTransition.Rejected(
                        WorkspaceActionRejection.PaletteIndexOutsidePalette(index, draft.palette.entryCount),
                    )
                }
            }
        }
    }

internal fun PaletteEditSession.updateColorEdit(color: PixelColor): PaletteDraftTransition {
    val edit = colorEdit ?: return colorEditRejected(PaletteDraftRejection.NoColorEdit)
    return when (val plan = PaletteDraftPlanner.plan(draft, PaletteDraftOperation.SetSlotColor(edit.index, color))) {
        is PaletteDraftPlan.Planned -> PaletteDraftTransition.Changed(withPreview(plan.remap.target, edit = edit))
        PaletteDraftPlan.Unchanged -> PaletteDraftTransition.Unchanged
        is PaletteDraftPlan.Rejected -> PaletteDraftTransition.Rejected(plan.rejection)
    }
}

internal fun PaletteEditSession.finishColorEdit(): PaletteDraftTransition {
    val edit = colorEdit ?: return colorEditRejected(PaletteDraftRejection.NoColorEdit)
    val restored = withPreview(edit.start)
    return when (val entry = draft.palette.entryAt(edit.index)) {
        is DomainValueResult.Created -> {
            when (val transition = restored.edit(PaletteDraftOperation.SetSlotColor(edit.index, entry.value.color))) {
                PaletteDraftTransition.Unchanged -> PaletteDraftTransition.Changed(restored)
                is PaletteDraftTransition.Changed -> transition
                is PaletteDraftTransition.Rejected -> transition
            }
        }

        is DomainValueResult.Rejected -> {
            error("Active colour edit slot disappeared: ${entry.rejection}")
        }
    }
}

internal fun PaletteEditSession.cancelColorEdit(): PaletteDraftTransition {
    val edit = colorEdit ?: return colorEditRejected(PaletteDraftRejection.NoColorEdit)
    return PaletteDraftTransition.Changed(withPreview(edit.start))
}

private fun colorEditRejected(reason: PaletteDraftRejection): PaletteDraftTransition =
    PaletteDraftTransition.Rejected(WorkspaceActionRejection.PaletteDraftRejected(reason))

package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.importing.NewWorkImportOption
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.RasterImportFacts
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.RasterImportOption
import io.github.hideyukimori.nenepixel.core.domain.importing.LayerImportPlan
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerLimits
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** What the PNG import dialog shows for one picked PNG (ADR 0033 "Controls"). */
internal data class PngImportDialogModel(
    val facts: RasterImportFacts,
    val append: PngImportFormModel,
    val convert: PngImportFormModel,
    val newWork: PngImportFormModel,
)

/**
 * One import form (a layer form or the new work): whether it can be chosen, and the lines that state what it does or
 * why it is unavailable.
 */
internal data class PngImportFormModel(
    val enabled: Boolean,
    val lines: List<PngImportLine>,
)

/** One explanation line: a plain string [resource], or a plurals [resource] formatted with [count]. */
internal data class PngImportLine(
    val resource: Int,
    val count: Int? = null,
)

/**
 * Builds the dialog for [pending] in a document of [layerCount] layers. A layer form is unavailable at the layer
 * limit first, then when nothing would be imported. The new work does not depend on [layerCount]; it is unavailable
 * only for the reason its plan states. An unavailable form shows only its reason.
 */
internal fun pngImportDialogModel(
    pending: PendingRasterImport,
    layerCount: Int,
): PngImportDialogModel =
    PngImportDialogModel(
        facts = pending.facts,
        append = formModel(pending.appending, layerCount, ::appendLines),
        convert = formModel(pending.converting, layerCount, ::convertLines),
        newWork = newWorkModel(pending.newWork),
    )

private fun formModel(
    option: RasterImportOption,
    layerCount: Int,
    lines: (LayerImportPlan) -> List<PngImportLine>,
): PngImportFormModel =
    when {
        layerCount >= LayerLimits.MAX_LAYERS -> {
            PngImportFormModel(false, listOf(PngImportLine(R.plurals.layer_limit, LayerLimits.MAX_LAYERS)))
        }

        option is RasterImportOption.Available -> {
            PngImportFormModel(true, lines(option.plan))
        }

        else -> {
            PngImportFormModel(false, listOf(PngImportLine(R.string.png_import_nothing)))
        }
    }

private fun newWorkModel(option: NewWorkImportOption): PngImportFormModel =
    when (option) {
        is NewWorkImportOption.Available -> {
            PngImportFormModel(true, listOf(PngImportLine(R.string.png_import_new_work_note)))
        }

        NewWorkImportOption.AboveCanvasLimit -> {
            PngImportFormModel(false, listOf(PngImportLine(R.string.png_import_new_work_too_large)))
        }

        NewWorkImportOption.TooManyColors -> {
            PngImportFormModel(false, listOf(PngImportLine(R.string.png_import_new_work_too_many_colors)))
        }

        NewWorkImportOption.NothingToImport -> {
            PngImportFormModel(false, listOf(PngImportLine(R.string.png_import_nothing)))
        }
    }

private fun appendLines(plan: LayerImportPlan): List<PngImportLine> =
    buildList {
        val appended = plan.appendedColorCount
        add(
            if (appended > 0) {
                PngImportLine(R.plurals.png_import_appended, appended)
            } else {
                PngImportLine(R.string.png_import_palette_unchanged)
            },
        )
        countLine(R.plurals.png_import_nearest, plan.loss.nearestColorCount)?.let(::add)
        countLine(R.plurals.png_import_dropped, plan.loss.droppedPixelCount)?.let(::add)
    }

private fun convertLines(plan: LayerImportPlan): List<PngImportLine> =
    buildList {
        val nearest = countLine(R.plurals.png_import_nearest, plan.loss.nearestColorCount)
        add(nearest ?: PngImportLine(R.string.png_import_exact))
        countLine(R.plurals.png_import_dropped, plan.loss.droppedPixelCount)?.let(::add)
    }

private fun countLine(
    resource: Int,
    count: Int,
): PngImportLine? = if (count > 0) PngImportLine(resource, count) else null

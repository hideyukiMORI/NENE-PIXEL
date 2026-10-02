package io.github.hideyukimori.nenepixel.presentation.compose.editor

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
)

/** One layer form: whether it can be chosen, and the lines that state what it does or why it is unavailable. */
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
 * Builds the dialog for [pending] in a document of [layerCount] layers. A form is unavailable at the layer limit
 * first, then when nothing would be imported; an unavailable form shows only its reason.
 */
internal fun pngImportDialogModel(
    pending: PendingRasterImport,
    layerCount: Int,
): PngImportDialogModel =
    PngImportDialogModel(
        facts = pending.facts,
        append = formModel(pending.appending, layerCount, ::appendLines),
        convert = formModel(pending.converting, layerCount, ::convertLines),
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

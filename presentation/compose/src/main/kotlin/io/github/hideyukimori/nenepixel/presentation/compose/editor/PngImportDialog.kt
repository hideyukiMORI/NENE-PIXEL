package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * The choice of form for a picked PNG (ADR 0033 "Controls"). It is shown only while a pending import exists, states
 * the PNG's size and number of colours, and offers the two layer forms, opening the PNG as a new work, and Cancel.
 * Pressing a layer form acts at once (the import can be undone); opening as a new work closes the current work, and
 * unsaved changes ask for the switch confirmation first. Cancel, a tap outside and Back clear the pending import. The
 * content scrolls, so a small window still reaches Cancel.
 */
@Composable
internal fun PngImportDialog(
    state: State<EditorRenderState>,
    callbacks: EditorImportCallbacks,
    onOpenAsNewWork: () -> Unit,
) {
    val pending by remember(state) { derivedStateOf { state.value.pendingImport } }
    val layerCount by remember(state) { derivedStateOf { state.value.document.layers.size } }
    pending?.let { picked ->
        val model = pngImportDialogModel(picked, layerCount)
        Dialog(onDismissRequest = { callbacks.onCancel() }) {
            Surface(
                modifier = Modifier.semantics { testTagsAsResourceId = true },
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                PngImportContent(model, callbacks, onOpenAsNewWork)
            }
        }
    }
}

@Composable
private fun PngImportContent(
    model: PngImportDialogModel,
    callbacks: EditorImportCallbacks,
    onOpenAsNewWork: () -> Unit,
) {
    val facts = model.facts
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.png_import_title), style = MaterialTheme.typography.titleLarge)
        Column {
            Text(stringResource(R.string.png_import_size, facts.width, facts.height))
            Text(pluralStringResource(R.plurals.png_import_colors, facts.colorCount, facts.colorCount))
        }
        PngImportForm(R.string.png_import_append, model.append) { callbacks.onAppend() }
        PngImportForm(R.string.png_import_convert, model.convert) { callbacks.onConvert() }
        PngImportForm(R.string.png_import_new_work, model.newWork) { onOpenAsNewWork() }
        TextButton(
            onClick = { callbacks.onCancel() },
            modifier = Modifier.heightIn(min = 48.dp).editorDescription(R.string.cancel, identity = CANCEL_TAG),
        ) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
private fun PngImportForm(
    label: Int,
    form: PngImportFormModel,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EditorActionButton(label, form.enabled, onClick)
        form.lines.forEach { line -> Text(line.text(), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun PngImportLine.text(): String =
    count?.let { quantity -> pluralStringResource(resource, quantity, quantity) } ?: stringResource(resource)

private const val CANCEL_TAG: String = "editor_png_import_cancel"

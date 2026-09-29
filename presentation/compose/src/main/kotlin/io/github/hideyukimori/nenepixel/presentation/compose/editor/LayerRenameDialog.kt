package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * The rename dialog for the layer [target] names (#144 UI spec "名前の変更", U7). It is composed only while [target]
 * holds a layer that is still among [rows]; when undo / redo takes that layer away, the dialog closes. The target is
 * plain `remember` state of the overlay, so process death and rotation do not bring the dialog back.
 */
@Composable
internal fun LayerRenameHost(
    target: MutableState<LayerId?>,
    rows: List<LayerRowModel>,
    callbacks: EditorLayerCallbacks,
) {
    val id = target.value
    if (id != null) {
        val row = rows.firstOrNull { candidate -> candidate.id == id }
        LaunchedEffect(row == null) { if (row == null) target.value = null }
        if (row != null) {
            key(id.value) {
                LayerRenameDialog(
                    row,
                    onRename = { name -> callbacks.onRename(id, name) },
                    onDismiss = { target.value = null },
                )
            }
        }
    }
}

/**
 * The field starts with the current name (empty for an empty name) fully selected. Confirming ("Rename" or the IME
 * Done) validates only then: the current name closes the dialog without a command, another valid name renames the
 * layer and closes it, and a refused name shows its problem under the field and keeps the dialog open. Editing the
 * text clears the problem. Cancel, a tap outside and Back close it and change nothing.
 */
@Composable
private fun LayerRenameDialog(
    row: LayerRowModel,
    onRename: (LayerName) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = row.name.value
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    var problem by remember { mutableStateOf<LayerRenameProblem?>(null) }
    val confirm: () -> Unit = {
        when (val submission = layerRenameSubmissionOf(field.text, row.name)) {
            LayerRenameSubmission.Unchanged -> {
                onDismiss()
            }

            is LayerRenameSubmission.Renamed -> {
                onRename(submission.name)
                onDismiss()
            }

            is LayerRenameSubmission.Rejected -> {
                problem = submission.problem
            }
        }
    }
    val onChange = { value: TextFieldValue ->
        if (value.text != field.text) problem = null
        field = value
    }
    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.layer_rename_title)) },
        text = { LayerNameField(LayerNameFieldState(field, problem, row.id), onChange, confirm) },
        confirmButton = { EditorActionButton(R.string.layer_rename_confirm, onClick = confirm) },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.editorDescription(R.string.cancel)) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun LayerNameField(
    state: LayerNameFieldState,
    onChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val fallback = stringResource(R.string.layer_default_name, state.id.value)
    Column {
        OutlinedTextField(
            value = state.field,
            onValueChange = onChange,
            label = { Text(stringResource(R.string.layer_rename_title)) },
            supportingText = { Text(stringResource(R.string.layer_rename_hint, fallback)) },
            isError = state.problem != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(INPUT_TAG),
        )
        state.problem?.let { problem ->
            Text(
                text = problem.text(),
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .padding(top = ERROR_TOP_PADDING)
                        .testTag(REJECTION_TAG)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** What the name field shows: the input, the problem of the last confirmation, and the layer for the fallback. */
private data class LayerNameFieldState(
    val field: TextFieldValue,
    val problem: LayerRenameProblem?,
    val id: LayerId,
)

private val ERROR_TOP_PADDING = 8.dp
private const val INPUT_TAG: String = "editor_layer_name_input"
private const val REJECTION_TAG: String = "editor_layer_name_rejection"

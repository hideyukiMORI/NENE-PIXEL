package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentDimension
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRejection
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequest
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequestResult
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.presentation.compose.R

@Composable
internal fun NewDocumentControls(
    canvasSize: CanvasSize,
    callbacks: EditorPersistenceCallbacks,
    enabled: Boolean,
    submitted: () -> Unit,
) {
    val state = rememberSaveable(saver = NewDocumentControlState.Saver) { NewDocumentControlState() }
    NewDocumentButton(enabled = enabled, onClick = { state.open(canvasSize) })
    if (state.dialogVisible) {
        NewDocumentDialog(
            state = state.dialogState,
            callbacks =
                NewDocumentDialogCallbacks(
                    onWidthChanged = { value -> state.widthInput = value },
                    onHeightChanged = { value -> state.heightInput = value },
                    onCreate = {
                        val result = callbacks.onCreateNewDocument(state.widthInput, state.heightInput)
                        state.accept(result)
                        if (result == NewDocumentSubmission.Submitted) submitted()
                    },
                    onCancel = state::cancel,
                ),
        )
    }
}

@Composable
private fun NewDocumentButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    EditorActionButton(R.string.new_document, enabled, onClick)
}

@Composable
private fun NewDocumentDialog(
    state: NewDocumentDialogState,
    callbacks: NewDocumentDialogCallbacks,
) {
    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        onDismissRequest = callbacks.onCancel,
        title = {
            Text(
                stringResource(R.string.create_document_title),
                modifier = Modifier.editorDescription(R.string.create_document_title),
            )
        },
        text = { NewDocumentFields(state, callbacks) },
        confirmButton = {
            EditorActionButton(R.string.create, onClick = callbacks.onCreate)
        },
        dismissButton = {
            TextButton(onClick = callbacks.onCancel, modifier = Modifier.editorDescription(R.string.cancel)) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun DimensionField(
    dimension: NewDocumentDimension,
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        label = { Text(stringResource(dimension.labelResource())) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier =
            modifier
                .padding(horizontal = FIELD_HORIZONTAL_PADDING)
                .editorDescription(
                    when (dimension) {
                        NewDocumentDimension.Width -> R.string.document_width
                        NewDocumentDimension.Height -> R.string.document_height
                    },
                ),
    )
}

private val FIELD_HORIZONTAL_PADDING = 4.dp
private val ERROR_TOP_PADDING = 8.dp

private data class NewDocumentDialogState(
    val widthInput: String,
    val heightInput: String,
    val rejection: NewDocumentRejection?,
)

private data class NewDocumentDialogCallbacks(
    val onWidthChanged: (String) -> Unit,
    val onHeightChanged: (String) -> Unit,
    val onCreate: () -> Unit,
    val onCancel: () -> Unit,
)

private class NewDocumentControlState {
    var dialogVisible by mutableStateOf(false)
    var widthInput by mutableStateOf("")
    var heightInput by mutableStateOf("")
    private var rejection by mutableStateOf<NewDocumentRejection?>(null)

    val dialogState: NewDocumentDialogState
        get() = NewDocumentDialogState(widthInput, heightInput, rejection)

    companion object {
        val Saver =
            listSaver<NewDocumentControlState, Any>(
                save = { listOf(it.dialogVisible, it.widthInput, it.heightInput, it.rejection != null) },
                restore = { saved ->
                    NewDocumentControlState().apply {
                        dialogVisible = saved[0] as Boolean
                        widthInput = saved[1] as String
                        heightInput = saved[2] as String
                        if (saved[3] as Boolean) {
                            rejection =
                                (
                                    NewDocumentRequest.create(
                                        widthInput,
                                        heightInput,
                                    ) as? NewDocumentRequestResult.Rejected
                                )?.rejection
                        }
                    }
                },
            )
    }

    fun open(canvasSize: CanvasSize) {
        widthInput = canvasSize.width.value.toString()
        heightInput = canvasSize.height.value.toString()
        rejection = null
        dialogVisible = true
    }

    fun accept(submission: NewDocumentSubmission) {
        when (submission) {
            NewDocumentSubmission.Submitted -> {
                cancel()
            }

            is NewDocumentSubmission.Rejected -> {
                rejection = submission.rejection
            }
        }
    }

    fun cancel() {
        dialogVisible = false
        rejection = null
    }
}

@Composable
private fun NewDocumentFields(
    state: NewDocumentDialogState,
    callbacks: NewDocumentDialogCallbacks,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            DimensionField(
                NewDocumentDimension.Width,
                state.widthInput,
                callbacks.onWidthChanged,
                Modifier.weight(1f),
            )
            DimensionField(
                NewDocumentDimension.Height,
                state.heightInput,
                callbacks.onHeightChanged,
                Modifier.weight(1f),
            )
        }
        state.rejection?.let { rejection ->
            Text(
                text = rejection.userMessage(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = ERROR_TOP_PADDING).testTag("editor_dimension_rejection"),
            )
        }
    }
}

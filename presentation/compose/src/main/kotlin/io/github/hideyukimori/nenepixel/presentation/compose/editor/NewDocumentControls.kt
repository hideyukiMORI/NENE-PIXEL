package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun NewDocumentControls(
    renderState: EditorRenderState,
    callbacks: EditorCallbacks,
    onRenderStateChanged: (EditorRenderState) -> Unit,
) {
    val state = remember { NewDocumentControlState() }
    NewDocumentButton(onClick = { state.open(renderState) })
    if (state.dialogVisible) {
        NewDocumentDialog(
            state = state.dialogState,
            callbacks =
                NewDocumentDialogCallbacks(
                    onWidthChanged = { value -> state.widthInput = value },
                    onHeightChanged = { value -> state.heightInput = value },
                    onCreate = {
                        state.accept(
                            callbacks.onCreateNewDocument(state.widthInput, state.heightInput),
                            onRenderStateChanged,
                        )
                    },
                    onCancel = state::cancel,
                ),
        )
    }
}

@Composable
private fun NewDocumentButton(onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text("New document")
    }
}

@Composable
private fun NewDocumentDialog(
    state: NewDocumentDialogState,
    callbacks: NewDocumentDialogCallbacks,
) {
    AlertDialog(
        onDismissRequest = callbacks.onCancel,
        title = { Text("Create new document") },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    DimensionField("Width", state.widthInput, callbacks.onWidthChanged, Modifier.weight(1f))
                    DimensionField("Height", state.heightInput, callbacks.onHeightChanged, Modifier.weight(1f))
                }
                state.rejectionMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = ERROR_TOP_PADDING),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = callbacks.onCreate) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = callbacks.onCancel) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun DimensionField(
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier =
            modifier
                .padding(horizontal = FIELD_HORIZONTAL_PADDING)
                .semantics { contentDescription = "Document ${label.lowercase()}" },
    )
}

private val FIELD_HORIZONTAL_PADDING = 4.dp
private val ERROR_TOP_PADDING = 8.dp

private data class NewDocumentDialogState(
    val widthInput: String,
    val heightInput: String,
    val rejectionMessage: String?,
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
    private var rejectionMessage by mutableStateOf<String?>(null)

    val dialogState: NewDocumentDialogState
        get() = NewDocumentDialogState(widthInput, heightInput, rejectionMessage)

    fun open(renderState: EditorRenderState) {
        widthInput = renderState.canvasWidthText()
        heightInput = renderState.canvasHeightText()
        rejectionMessage = null
        dialogVisible = true
    }

    fun accept(
        submission: NewDocumentSubmission,
        onRenderStateChanged: (EditorRenderState) -> Unit,
    ) {
        when (submission) {
            is NewDocumentSubmission.Created -> {
                onRenderStateChanged(submission.renderState)
                cancel()
            }

            is NewDocumentSubmission.Rejected -> {
                rejectionMessage = submission.userMessage
            }
        }
    }

    fun cancel() {
        dialogVisible = false
        rejectionMessage = null
    }
}

private fun EditorRenderState.canvasWidthText(): String =
    snapshot.size.width.value
        .toString()

private fun EditorRenderState.canvasHeightText(): String =
    snapshot.size.height.value
        .toString()

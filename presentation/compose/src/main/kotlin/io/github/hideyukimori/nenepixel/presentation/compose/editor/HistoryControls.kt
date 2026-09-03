package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState

@Composable
internal fun HistoryControls(
    renderState: EditorRenderState,
    callbacks: EditorCallbacks,
    onRenderStateChanged: (EditorRenderState) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(STATUS_SPACING),
        modifier = Modifier.padding(bottom = CONTROL_BOTTOM_PADDING),
    ) {
        Text(
            text =
                when (renderState.dirtyState) {
                    DocumentDirtyState.Clean -> "No unsaved changes"
                    DocumentDirtyState.Dirty -> "Unsaved changes"
                },
            modifier = Modifier.semantics { contentDescription = "Document dirty status" },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(CONTROL_SPACING),
        ) {
            Button(
                enabled = renderState.canUndo,
                onClick = { onRenderStateChanged(callbacks.onUndo()) },
            ) {
                Text("Undo")
            }
            Button(
                enabled = renderState.canRedo,
                onClick = { onRenderStateChanged(callbacks.onRedo()) },
            ) {
                Text("Redo")
            }
        }
    }
}

private val CONTROL_SPACING = 8.dp
private val CONTROL_BOTTOM_PADDING = 16.dp
private val STATUS_SPACING = 4.dp

package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun HistoryControls(
    canUndo: Boolean,
    canRedo: Boolean,
    callbacks: EditorCallbacks,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CONTROL_SPACING),
        modifier = Modifier.padding(bottom = CONTROL_BOTTOM_PADDING),
    ) {
        Button(
            enabled = canUndo,
            onClick = { callbacks.onUndo() },
        ) {
            Text("Undo")
        }
        Button(
            enabled = canRedo,
            onClick = { callbacks.onRedo() },
        ) {
            Text("Redo")
        }
    }
}

private val CONTROL_SPACING = 8.dp
private val CONTROL_BOTTOM_PADDING = 16.dp

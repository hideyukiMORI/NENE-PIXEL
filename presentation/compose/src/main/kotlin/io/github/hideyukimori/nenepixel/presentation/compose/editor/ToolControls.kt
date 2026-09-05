package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool

@Composable
internal fun ToolControls(
    activeTool: DrawingTool,
    callbacks: EditorCallbacks,
    onRenderStateChanged: (EditorRenderState) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(TOOL_SPACING)) {
        ToolButton(
            "Pencil",
            activeTool == DrawingTool.Pencil,
        ) { onRenderStateChanged(callbacks.onSelectTool(DrawingTool.Pencil)) }
        ToolButton(
            "Eraser",
            activeTool == DrawingTool.Eraser,
        ) { onRenderStateChanged(callbacks.onSelectTool(DrawingTool.Eraser)) }
    }
}

@Composable
private fun ToolButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val modifier =
        Modifier.semantics {
            contentDescription = "$label tool"
            this.selected = selected
        }
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

private val TOOL_SPACING = 8.dp

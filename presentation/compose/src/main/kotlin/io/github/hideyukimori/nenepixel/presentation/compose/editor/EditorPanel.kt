package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge

internal enum class EditorPanel { Palette, File, Appearance }

internal data class EditorPanelPlacement(
    val panel: EditorPanel,
    val edge: EditorControlEdge,
)

@Composable
internal fun EditorPanelSurface(
    placement: EditorPanelPlacement,
    dismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                    .clickable(onClick = dismiss)
                    .semantics { contentDescription = "Dismiss panel" },
            )
            Surface(
                modifier =
                    Modifier
                        .align(placement.alignment())
                        .padding(8.dp)
                        .widthIn(max = 360.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .semantics { paneTitle = placement.panel.name },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(16.dp)) {
                    PanelHeader(placement.panel, dismiss)
                    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(
    panel: EditorPanel,
    dismiss: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(panel.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = dismiss, modifier = Modifier.semantics { contentDescription = "Close panel" }) {
            EditorSymbol(EditorIcon.Close)
        }
    }
}

private fun EditorPanelPlacement.alignment(): Alignment =
    when (edge) {
        EditorControlEdge.Left -> AbsoluteAlignment.CenterLeft
        EditorControlEdge.Right -> AbsoluteAlignment.CenterRight
    }

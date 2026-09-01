package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun EditorScreen(
    renderState: EditorRenderState,
    onRenderStateChanged: (EditorRenderState) -> Unit,
    callbacks: EditorCallbacks,
    modifier: Modifier,
) {
    MaterialTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(PresentationPalette.editorBackground)
                        .padding(SCREEN_PADDING),
            ) {
                Text(text = "NENE-PIXEL", style = MaterialTheme.typography.headlineSmall)
                ActiveColorIndicator(renderState)
                HistoryControls(renderState, callbacks, onRenderStateChanged)
                PixelCanvas(
                    renderState = renderState,
                    callbacks = callbacks,
                    onRenderStateChanged = onRenderStateChanged,
                    modifier = Modifier.fillMaxWidth().aspectRatio(FIXED_CANVAS_ASPECT_RATIO),
                )
            }
        }
    }
}

@Composable
private fun ActiveColorIndicator(renderState: EditorRenderState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ACTIVE_COLOR_SPACING),
        modifier = Modifier.padding(vertical = ACTIVE_COLOR_PADDING),
    ) {
        Text(text = "Active color")
        Box(
            modifier =
                Modifier
                    .size(ACTIVE_COLOR_SWATCH_SIZE)
                    .background(renderState.activeColor.toComposeColor())
                    .semantics { contentDescription = "Active color swatch" },
        )
    }
}

private val SCREEN_PADDING = 24.dp
private val ACTIVE_COLOR_PADDING = 16.dp
private val ACTIVE_COLOR_SPACING = 8.dp
private val ACTIVE_COLOR_SWATCH_SIZE = 28.dp
private const val FIXED_CANVAS_ASPECT_RATIO: Float = 1.0f

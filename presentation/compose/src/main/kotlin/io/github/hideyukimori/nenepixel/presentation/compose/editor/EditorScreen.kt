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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

@Composable
internal fun EditorScreen(
    renderState: State<EditorRenderState>,
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
                SelectionControls(renderState, callbacks, onRenderStateChanged)
                DocumentControls(renderState, callbacks, onRenderStateChanged)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    EditorCanvas(
                        renderState = renderState,
                        callbacks = callbacks,
                        onRenderStateChanged = onRenderStateChanged,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionControls(
    renderState: State<EditorRenderState>,
    callbacks: EditorCallbacks,
    onRenderStateChanged: (EditorRenderState) -> Unit,
) {
    val inputs by
        remember(renderState) {
            derivedStateOf {
                val current = renderState.value
                SelectionInputs(current.activeColor, current.palette, current.activePaletteIndex, current.activeTool)
            }
        }
    ActiveColorIndicator(inputs.activeColor)
    PaletteControls(
        palette = inputs.palette,
        activePaletteIndex = inputs.activePaletteIndex,
        callbacks = callbacks,
        onRenderStateChanged = onRenderStateChanged,
    )
    ToolControls(inputs.activeTool, callbacks, onRenderStateChanged)
}

@Composable
private fun DocumentControls(
    renderState: State<EditorRenderState>,
    callbacks: EditorCallbacks,
    onRenderStateChanged: (EditorRenderState) -> Unit,
) {
    val inputs by
        remember(renderState) {
            derivedStateOf {
                val current = renderState.value
                DocumentInputs(current.snapshot.size, current.canUndo, current.canRedo, current.dirtyState)
            }
        }
    NewDocumentControls(inputs.canvasSize, callbacks, onRenderStateChanged)
    HistoryControls(
        canUndo = inputs.canUndo,
        canRedo = inputs.canRedo,
        dirtyState = inputs.dirtyState,
        callbacks = callbacks,
        onRenderStateChanged = onRenderStateChanged,
    )
}

@Composable
private fun EditorCanvas(
    renderState: State<EditorRenderState>,
    callbacks: EditorCallbacks,
    onRenderStateChanged: (EditorRenderState) -> Unit,
    modifier: Modifier,
) {
    val canvasSize by
        remember(renderState) {
            derivedStateOf { renderState.value.snapshot.size }
        }
    PixelCanvas(
        renderState = renderState,
        canvasSize = canvasSize,
        callbacks = callbacks,
        onRenderStateChanged = onRenderStateChanged,
        modifier = modifier.aspectRatio(canvasSize.aspectRatio()),
    )
}

@Composable
private fun ActiveColorIndicator(activeColor: PixelColor) {
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
                    .background(activeColor.toComposeColor())
                    .semantics { contentDescription = "Active color swatch" },
        )
    }
}

private val SCREEN_PADDING = 24.dp
private val ACTIVE_COLOR_PADDING = 16.dp
private val ACTIVE_COLOR_SPACING = 8.dp
private val ACTIVE_COLOR_SWATCH_SIZE = 28.dp

private data class SelectionInputs(
    val activeColor: PixelColor,
    val palette: Palette,
    val activePaletteIndex: PaletteIndex,
    val activeTool: DrawingTool,
)

private data class DocumentInputs(
    val canvasSize: CanvasSize,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val dirtyState: DocumentDirtyState,
)

private fun io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize.aspectRatio(): Float =
    width.value.toFloat() / height.value.toFloat()

package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent

@Composable
public fun P2FrameMeasurementEditor(
    renderState: EditorRenderState,
    callbacks: EditorCallbacks,
    generation: Long,
    onRenderStateChanged: (EditorRenderState) -> Unit,
    onCanonicalContentDrawn: (Long, EditorRenderState) -> Unit,
    modifier: Modifier = Modifier,
) {
    EditorScreen(
        renderState = renderState,
        onRenderStateChanged = onRenderStateChanged,
        callbacks = callbacks,
        modifier =
            modifier.drawWithContent {
                drawContent()
                onCanonicalContentDrawn(generation, renderState)
            },
    )
}

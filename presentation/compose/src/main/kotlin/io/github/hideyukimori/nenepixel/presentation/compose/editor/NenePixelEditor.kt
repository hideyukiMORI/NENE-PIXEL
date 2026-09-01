package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
public fun NenePixelEditor(
    initialState: EditorRenderState,
    callbacks: EditorCallbacks,
    modifier: Modifier = Modifier,
) {
    var renderState by remember(callbacks) { mutableStateOf(initialState) }
    EditorScreen(
        renderState = renderState,
        onRenderStateChanged = { nextState -> renderState = nextState },
        callbacks = callbacks,
        modifier = modifier,
    )
}

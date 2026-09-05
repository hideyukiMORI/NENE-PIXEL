package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
public fun NenePixelEditor(
    initialState: EditorRenderState,
    callbacks: EditorCallbacks,
    modifier: Modifier = Modifier,
) {
    val renderState = remember(callbacks) { mutableStateOf(initialState) }
    EditorScreen(
        renderState = renderState,
        onRenderStateChanged = { nextState -> renderState.value = nextState },
        callbacks = callbacks,
        modifier = modifier,
    )
}

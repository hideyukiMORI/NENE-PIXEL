package io.github.hideyukimori.nenepixel.presentation.compose.editor

import kotlinx.coroutines.flow.StateFlow

/** The existing editor's immutable render stream and intent callbacks at the composition root. */
public class EditorControls(
    internal val renderStates: StateFlow<EditorRenderState>,
    internal val callbacks: EditorCallbacks,
)

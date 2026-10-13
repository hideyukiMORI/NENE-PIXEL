package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.State

/** The screen consumes current projections and the same editor, storage and app-settings capabilities. */
internal data class EditorScreenInputs(
    val state: State<EditorRenderState>,
    val callbacks: EditorCallbacks,
    val storage: EditorStorageInputs,
    val settings: EditorSettingsInputs,
)

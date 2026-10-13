package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection
import kotlinx.coroutines.flow.StateFlow

/** Read-only storage status and the platform's existing file-operation intents. */
public class EditorPersistenceControls(
    internal val operations: StateFlow<PersistenceOperationProjection>,
    internal val autosave: StateFlow<AutosaveProjection>,
    internal val callbacks: EditorPersistenceCallbacks,
)

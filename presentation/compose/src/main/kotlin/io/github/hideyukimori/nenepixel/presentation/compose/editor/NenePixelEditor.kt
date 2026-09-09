package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection
import kotlinx.coroutines.flow.StateFlow

@Composable
public fun NenePixelEditor(
    renderStates: StateFlow<EditorRenderState>,
    persistenceOperations: StateFlow<PersistenceOperationProjection>,
    callbacks: EditorCallbacks,
    persistenceCallbacks: EditorPersistenceCallbacks,
    modifier: Modifier = Modifier,
) {
    val renderState = renderStates.collectAsState()
    val persistenceOperation = persistenceOperations.collectAsState()
    EditorScreen(
        renderState = renderState,
        persistenceOperation = persistenceOperation.value,
        callbacks = callbacks,
        persistenceCallbacks = persistenceCallbacks,
        modifier = modifier,
    )
}

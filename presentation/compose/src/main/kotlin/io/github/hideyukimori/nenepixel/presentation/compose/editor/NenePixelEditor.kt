package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection
import kotlinx.coroutines.flow.StateFlow

@Composable
public fun NenePixelEditor(
    renderStates: StateFlow<EditorRenderState>,
    persistenceOperations: StateFlow<PersistenceOperationProjection>,
    autosaveStates: StateFlow<AutosaveProjection>,
    callbacks: EditorCallbacks,
    persistenceCallbacks: EditorPersistenceCallbacks,
    language: AppLanguageControls,
    modifier: Modifier = Modifier,
) {
    val renderState = renderStates.collectAsState()
    val persistenceOperation = persistenceOperations.collectAsState()
    val autosave = autosaveStates.collectAsState()
    val languageSettings = language.settings.collectAsState()
    if (languageSettings.value.status == AppLanguageStatus.Loading) {
        LanguageLoadingScreen()
    } else {
        EditorScreen(
            renderState = renderState,
            persistenceOperation = persistenceOperation.value,
            autosave = autosave.value,
            callbacks = callbacks,
            persistenceCallbacks = persistenceCallbacks,
            modifier = modifier,
            language = language,
        )
    }
}

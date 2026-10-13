package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
public fun NenePixelEditor(
    editor: EditorControls,
    persistence: EditorPersistenceControls,
    settings: EditorSettingsInputs,
    modifier: Modifier = Modifier,
) {
    val renderState = editor.renderStates.collectAsState()
    val persistenceOperation = persistence.operations.collectAsState()
    val autosave = persistence.autosave.collectAsState()
    val languageSettings = settings.language.settings.collectAsState()
    val storage =
        remember(persistenceOperation.value, autosave.value, persistence.callbacks) {
            EditorStorageInputs(persistenceOperation.value, autosave.value, persistence.callbacks)
        }
    if (languageSettings.value.status == AppLanguageStatus.Loading) {
        LanguageLoadingScreen()
    } else {
        EditorScreen(EditorScreenInputs(renderState, editor.callbacks, storage, settings), modifier)
    }
}

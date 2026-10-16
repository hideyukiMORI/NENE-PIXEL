package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection

/** File actions retain the editing frame even when the canvas projects a playback frame. */
@Composable
internal fun EditorFilePanel(
    state: EditorRenderState,
    storage: EditorStorageInputs,
    dismiss: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        PersistenceControls(
            storage,
            FrameImageInputs.committed(state.document, state.activeFrameId),
            dismiss,
        )
        Text(
            stringResource(storage.operation.statusResource(storage.autosave, storage.availability)),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp),
        )
        MvpInformationControls()
    }
}

@Composable
internal fun EditorFileDialogs(
    state: State<EditorRenderState>,
    callbacks: EditorCallbacks,
    storage: EditorStorageInputs,
) {
    val newWork =
        remember(storage.available, storage.callbacks) {
            EditorFileAction(storage.available, storage.callbacks.openPngAsNewWork)
        }
    PersistenceConfirmation(storage.operation, storage.callbacks, storage.availability)
    LegacyConversionDialog(storage, state.value.definition)
    PngImportDialog(
        state,
        callbacks.rasterImport,
        newWork,
    )
}

internal data class EditorStorageInputs(
    val operation: PersistenceOperationProjection,
    val autosave: AutosaveProjection,
    val callbacks: EditorPersistenceCallbacks,
    val availability: EditorOperationAvailability,
) {
    val available: Boolean get() = availability == EditorOperationAvailability.Available
    val idle: Boolean get() = operation.phase is PersistenceOperationPhase.Idle
    val canStart: Boolean get() = idle && available
    val preparing: Boolean get() = idle && !available
}

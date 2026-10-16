package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.presentation.compose.R

@Composable
internal fun DocumentStatusRow(
    dirtyState: DocumentDirtyState,
    storage: EditorStorageInputs,
) {
    val operation = storage.operation
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(STATUS_SPACING),
    ) {
        if (operation.offersRecovery()) {
            RecoveryOffer(enabled = operation.recoveryOfferEnabled(storage.availability), callbacks = storage.callbacks)
        } else {
            DocumentDirtyStatus(dirtyState)
        }
        if (!operation.offersRecovery() || storage.preparing) {
            Text(
                style = MaterialTheme.typography.labelSmall,
                text = stringResource(operation.statusResource(storage.autosave, storage.availability)),
                modifier = Modifier.editorDescription(R.string.operation_status),
            )
        }
    }
}

@Composable
private fun DocumentDirtyStatus(dirtyState: DocumentDirtyState) {
    Text(
        style = MaterialTheme.typography.labelSmall,
        text =
            when (dirtyState) {
                DocumentDirtyState.Clean -> stringResource(R.string.clean_document)
                DocumentDirtyState.Dirty -> stringResource(R.string.dirty_document)
            },
        modifier =
            Modifier.editorDescription(
                R.string.dirty_status,
                identity =
                    when (dirtyState) {
                        DocumentDirtyState.Clean -> "editor_clean_document"
                        DocumentDirtyState.Dirty -> "editor_dirty_document"
                    },
            ),
    )
}

private val STATUS_SPACING = 8.dp

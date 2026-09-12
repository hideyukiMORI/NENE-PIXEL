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
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection
import io.github.hideyukimori.nenepixel.presentation.compose.R

@Composable
internal fun DocumentStatusRow(
    dirtyState: DocumentDirtyState,
    operation: PersistenceOperationProjection,
    autosave: AutosaveProjection,
    callbacks: EditorPersistenceCallbacks,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(STATUS_SPACING),
    ) {
        if (operation.offersRecovery()) {
            RecoveryOffer(enabled = operation.recoveryOfferEnabled(), callbacks = callbacks)
        } else {
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
            Text(
                style = MaterialTheme.typography.labelSmall,
                text = stringResource(operation.statusResource(autosave)),
                modifier = Modifier.editorDescription(R.string.operation_status),
            )
        }
    }
}

private val STATUS_SPACING = 8.dp

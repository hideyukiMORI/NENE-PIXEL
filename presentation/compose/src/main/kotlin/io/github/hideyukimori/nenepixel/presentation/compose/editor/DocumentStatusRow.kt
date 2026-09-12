package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection

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
                        DocumentDirtyState.Clean -> "No unsaved changes"
                        DocumentDirtyState.Dirty -> "Unsaved changes"
                    },
                modifier = Modifier.semantics { contentDescription = "Document dirty status" },
            )
            Text(
                style = MaterialTheme.typography.labelSmall,
                text = operation.statusText(autosave),
                modifier = Modifier.semantics { contentDescription = "Project operation status" },
            )
        }
    }
}

private val STATUS_SPACING = 8.dp

package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection

@Composable
internal fun DocumentStatusRow(
    dirtyState: DocumentDirtyState,
    operation: PersistenceOperationProjection,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(STATUS_SPACING)) {
        Text(
            text =
                when (dirtyState) {
                    DocumentDirtyState.Clean -> "No unsaved changes"
                    DocumentDirtyState.Dirty -> "Unsaved changes"
                },
            modifier = Modifier.semantics { contentDescription = "Document dirty status" },
        )
        Text(
            text = operation.statusText(),
            modifier = Modifier.semantics { contentDescription = "Project operation status" },
        )
    }
}

private val STATUS_SPACING = 8.dp

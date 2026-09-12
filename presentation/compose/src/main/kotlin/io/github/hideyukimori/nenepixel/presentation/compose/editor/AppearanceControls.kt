package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorAppearance
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorLayout
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorTheme

@Composable
internal fun AppearanceControls(
    appearance: EditorAppearance,
    callbacks: EditorCallbacks,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppearanceChoice("Theme", EditorTheme.entries, appearance.theme) {
            callbacks.onSetAppearance(appearance.copy(theme = it))
        }
        AppearanceChoice("Layout", EditorLayout.entries, appearance.layout) {
            callbacks.onSetAppearance(appearance.copy(layout = it))
        }
        Text(
            "Tabletop keeps tools below the canvas. Handheld moves them to the side.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppearanceChoice("Control edge", EditorControlEdge.entries, appearance.controlEdge) {
            callbacks.onSetAppearance(appearance.copy(controlEdge = it))
        }
        Text(
            "These choices are kept for this session.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun <T : Enum<T>> AppearanceChoice(
    label: String,
    values: List<T>,
    selected: T,
    select: (T) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                FilterChip(
                    selected = selected == value,
                    onClick = { select(value) },
                    label = { Text(value.name) },
                    modifier =
                        Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = "$label: ${value.name}"
                        },
                )
            }
        }
    }
}

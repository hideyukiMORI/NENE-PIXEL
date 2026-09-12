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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorAppearance
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorLayout
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorTheme
import io.github.hideyukimori.nenepixel.presentation.compose.R

@Composable
internal fun AppearanceControls(
    appearance: EditorAppearance,
    callbacks: EditorCallbacks,
    language: AppLanguageControls,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppearanceChoice(
            R.string.theme,
            EditorTheme.entries.map {
                AppearanceOption(it, it.labelResource())
            },
            appearance.theme,
        ) {
            callbacks.onSetAppearance(appearance.copy(theme = it))
        }
        AppearanceChoice(
            R.string.layout,
            EditorLayout.entries.map {
                AppearanceOption(it, it.labelResource())
            },
            appearance.layout,
        ) {
            callbacks.onSetAppearance(appearance.copy(layout = it))
        }
        AppearanceHint(R.string.layout_hint)
        AppearanceChoice(
            R.string.control_edge,
            EditorControlEdge.entries.map {
                AppearanceOption(it, it.labelResource())
            },
            appearance.controlEdge,
        ) {
            callbacks.onSetAppearance(appearance.copy(controlEdge = it))
        }
        AppearanceHint(R.string.session_hint)
        LanguageChoices(language) { callbacks.onPointerCancel() }
    }
}

@Composable
private fun <T : Enum<T>> AppearanceChoice(
    label: Int,
    values: List<AppearanceOption<T>>,
    selected: T,
    select: (T) -> Unit,
) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { option ->
                val value = option.value
                val description =
                    stringResource(R.string.choice_description, stringResource(label), stringResource(option.label))
                FilterChip(
                    selected = selected == value,
                    onClick = { select(value) },
                    label = { Text(stringResource(option.label)) },
                    modifier =
                        Modifier.heightIn(min = 48.dp).editorDescription(option.label).semantics {
                            contentDescription = description
                        },
                )
            }
        }
    }
}

private data class AppearanceOption<T>(
    val value: T,
    val label: Int,
)

private fun EditorTheme.labelResource(): Int =
    when (this) {
        EditorTheme.Dark -> R.string.dark
        EditorTheme.Light -> R.string.light
    }

private fun EditorLayout.labelResource(): Int =
    when (this) {
        EditorLayout.Tabletop -> R.string.tabletop
        EditorLayout.Handheld -> R.string.handheld
    }

private fun EditorControlEdge.labelResource(): Int =
    when (this) {
        EditorControlEdge.Left -> R.string.left
        EditorControlEdge.Right -> R.string.right
    }

@Composable
private fun AppearanceHint(resource: Int) {
    Text(
        stringResource(resource),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

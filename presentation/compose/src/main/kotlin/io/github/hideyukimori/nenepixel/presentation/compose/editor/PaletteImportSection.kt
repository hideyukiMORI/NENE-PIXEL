package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteImportMode
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PendingPaletteImport
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * The mapping section of a pending palette import (design S6b 1): the imported slots, the mode, one destination
 * choice per draft slot the mode maps through assignments, and Confirm / Cancel. Tapping a draft slot row picks it;
 * tapping an imported slot then assigns it. The runtime decides whether Confirm resolves.
 */
@Composable
internal fun PaletteImportSection(
    draft: PaletteDefinition,
    pending: PendingPaletteImport,
    callbacks: EditorPaletteCallbacks,
) {
    val pickedState = rememberSaveable { mutableIntStateOf(NO_SOURCE) }
    var picked by pickedState
    val sources = pending.unresolvedSources(draft)
    val unresolved = sources.size
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.palette_import_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.editorDescription(R.string.palette_import_title),
        )
        PaletteImportModes(pending.mode, callbacks)
        PaletteImportTargets(pending) { destination ->
            sources.firstOrNull { it.value == picked }?.let { callbacks.onAssignImport(it, destination) }
            picked = NO_SOURCE
        }
        if (unresolved > 0) {
            val unresolvedText = pluralStringResource(R.plurals.palette_import_unresolved, unresolved, unresolved)
            Text(
                unresolvedText,
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .testTag("editor_palette_import_unresolved")
                        .semantics { contentDescription = unresolvedText },
            )
        }
        PaletteImportSources(draft, sources, pickedState)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaletteEditorButton(R.string.palette_import_confirm, identity = "editor_palette_editor_import_confirm") {
                callbacks.onConfirmImport()
            }
            PaletteEditorButton(R.string.palette_import_cancel, identity = "editor_palette_editor_import_cancel") {
                callbacks.onCancelImport()
            }
        }
    }
}

@Composable
private fun PaletteImportTargets(
    pending: PendingPaletteImport,
    assign: (PaletteIndex) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pending.target.palette.entries().forEach { entry ->
            val number = entry.index.value + 1
            ImportSwatch(entry.color, number, false, "editor_palette_editor_import_slot_$number") {
                assign(entry.index)
            }
        }
    }
}

@Composable
private fun PaletteImportSources(
    draft: PaletteDefinition,
    sources: List<PaletteIndex>,
    pickedState: MutableIntState,
) {
    var picked by pickedState
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sources.forEach { source ->
            val number = source.value + 1
            val identity = "editor_palette_editor_assign_$number"
            ImportSwatch(draft.colorAt(source), number, source.value == picked, identity) {
                picked = if (picked == source.value) NO_SOURCE else source.value
            }
        }
    }
}

@Composable
private fun PaletteImportModes(
    current: PaletteImportMode,
    callbacks: EditorPaletteCallbacks,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IMPORT_MODES.forEach { (mode, label, identity) ->
            PaletteEditorButton(label, enabled = mode != current, identity = identity) { callbacks.onImportMode(mode) }
        }
    }
}

@Composable
private fun ImportSwatch(
    color: PixelColor,
    number: Int,
    selected: Boolean,
    identity: String,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    Column(
        modifier =
            Modifier
                .size(48.dp)
                .selectable(selected, role = Role.Button, onClick = onClick)
                .editorDescription(R.string.palette_editor_slot, number, identity = identity)
                .border(if (selected) 3.dp else 1.dp, borderColor, RoundedCornerShape(4.dp))
                .padding(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).background(color.toComposeColor()))
        Text(stringResource(R.string.entry_number, number), style = MaterialTheme.typography.labelSmall)
    }
}

private fun PaletteDefinition.colorAt(index: PaletteIndex): PixelColor =
    palette.entries().first { it.index == index }.color

private const val NO_SOURCE: Int = -1

private val IMPORT_MODES: List<Triple<PaletteImportMode, Int, String>> =
    listOf(
        Triple(PaletteImportMode.ByNumber, R.string.palette_import_mode_number, "editor_palette_editor_mode_number"),
        Triple(PaletteImportMode.Nearest, R.string.palette_import_mode_nearest, "editor_palette_editor_mode_nearest"),
        Triple(
            PaletteImportMode.Explicit,
            R.string.palette_import_mode_explicit,
            "editor_palette_editor_mode_explicit",
        ),
    )

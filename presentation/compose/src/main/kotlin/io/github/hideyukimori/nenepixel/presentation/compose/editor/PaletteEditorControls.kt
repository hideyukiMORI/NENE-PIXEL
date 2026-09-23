package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteEntry
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * The palette editor panel (design ruling 6): draft slots, the RGBA editor for the selected slot, slot operations and
 * the draft history, Apply and Cancel, the palette JSON buttons and the latest notice. Every change goes through
 * [EditorPaletteCallbacks]; a pending import disables editing until its mapping section (S6b) confirms or cancels it.
 */
@Composable
internal fun PaletteEditorControls(
    session: PaletteEditSession,
    notice: PaletteEditorNotice?,
    callbacks: EditorPaletteCallbacks,
    json: PaletteJsonActions,
) {
    val selectedState = rememberSaveable { mutableIntStateOf(0) }
    val choosingState = rememberSaveable { mutableStateOf(false) }
    val choice = remember(selectedState, choosingState) { PaletteEditorChoice(selectedState, choosingState) }
    val selection = PaletteEditorSelection.of(session, choice.selected)
    val actions = PaletteEditorActions(selection, choice, callbacks)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PaletteEditorSlots(selection, Modifier.weight(1f), actions::slotTapped)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            session.pendingImport?.let { PaletteImportSection(session.draft, it, callbacks) }
            if (choice.choosingReplacement) {
                Text(
                    stringResource(R.string.palette_editor_replacement),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.editorDescription(R.string.palette_editor_replacement),
                )
            }
            PaletteColorEditor(selection.selectedEntry.color, selection.editable, actions::setColor)
            PaletteEditorSlotActions(selection, actions)
            PaletteEditorSessionActions(session, callbacks)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaletteEditorButton(R.string.export_palette_json, json.idle, onClick = json::export)
                PaletteEditorButton(R.string.import_palette_json, json.idle, onClick = json::import)
            }
            notice?.let { PaletteEditorNoticeText(it) }
        }
    }
}

@Composable
private fun PaletteEditorSlots(
    selection: PaletteEditorSelection,
    modifier: Modifier,
    tap: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(56.dp),
        modifier = modifier.fillMaxWidth().editorDescription(R.string.palette_colors),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(selection.entries, key = { _, entry -> entry.index.value }) { position, entry ->
            PaletteEditorSlot(entry, position == selection.selected, entry.index == selection.defaultIndex) {
                tap(position)
            }
        }
    }
}

@Composable
private fun PaletteEditorSlot(
    entry: PaletteEntry,
    selected: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit,
) {
    val number = entry.index.value + 1
    val borderColor = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .size(56.dp)
                .selectable(selected, role = Role.Button, onClick = onClick)
                .editorDescription(
                    R.string.palette_editor_slot,
                    number,
                    identity = "editor_palette_editor_slot_$number",
                ).border(if (selected) 3.dp else 1.dp, borderColor, RoundedCornerShape(4.dp))
                .padding(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).background(entry.color.toComposeColor())) {
            if (isDefault) {
                Text(
                    "★",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopEnd).background(Color.Black).padding(horizontal = 2.dp),
                )
            }
        }
        Text(stringResource(R.string.entry_number, number), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PaletteEditorSlotActions(
    selection: PaletteEditorSelection,
    actions: PaletteEditorActions,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PaletteEditorButton(
            R.string.palette_editor_set_default,
            selection.editable && !selection.selectedIsDefault,
            onClick = actions::setDefault,
        )
        PaletteEditorButton(R.string.palette_editor_append, selection.canAppend, onClick = actions::append)
        PaletteEditorButton(R.string.palette_editor_remove, selection.canRemove, onClick = actions::remove)
        PaletteEditorButton(R.string.palette_editor_move_up, selection.canMove(-1)) { actions.move(-1) }
        PaletteEditorButton(R.string.palette_editor_move_down, selection.canMove(1)) { actions.move(1) }
    }
}

@Composable
private fun PaletteEditorSessionActions(
    session: PaletteEditSession,
    callbacks: EditorPaletteCallbacks,
) {
    val editable = session.pendingImport == null
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PaletteEditorButton(R.string.palette_editor_undo, editable && session.canUndoDraft) { callbacks.onUndo() }
        PaletteEditorButton(R.string.palette_editor_redo, editable && session.canRedoDraft) { callbacks.onRedo() }
        PaletteEditorButton(R.string.palette_editor_apply, editable) { callbacks.onApply() }
        PaletteEditorButton(R.string.palette_editor_cancel) { callbacks.onCancel() }
    }
}

@Composable
private fun PaletteEditorNoticeText(notice: PaletteEditorNotice) {
    Text(
        stringResource(notice.noticeResource()),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.editorDescription(notice.noticeResource(), identity = "editor_palette_editor_notice"),
    )
}

/** The palette JSON buttons' route; they follow the File panel's rule and wait while a file operation runs. */
internal class PaletteJsonActions(
    private val callbacks: EditorPersistenceCallbacks,
    operation: PersistenceOperationProjection,
) {
    val idle: Boolean = operation.phase is PersistenceOperationPhase.Idle

    fun export() {
        callbacks.onExportPaletteJson()
    }

    fun import() {
        callbacks.onImportPaletteJson()
    }
}

/** A palette-editor action button; the resource name is its identity unless [identity] names another. */
@Composable
internal fun PaletteEditorButton(
    label: Int,
    enabled: Boolean = true,
    identity: String? = null,
    onClick: () -> Unit,
) {
    Button(
        colors = editorButtonColors(),
        modifier = Modifier.editorDescription(label, identity = identity),
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(stringResource(label))
    }
}

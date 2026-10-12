package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * Issue #168: one scrolling body and bounded real slot grid; history/Apply/Cancel remain reachable in the footer.
 * Slot and JSON operations are secondary. Only the runtime owns colours and draft history.
 */
@Composable
internal fun PaletteEditorControls(
    view: PaletteEditorView,
    callbacks: EditorPaletteCallbacks,
    json: PaletteJsonActions,
) {
    val session = view.session
    val notice = view.notice
    val selectedState = rememberSaveable { mutableIntStateOf(0) }
    val choosingState = rememberSaveable { mutableStateOf(false) }
    val choice = remember(selectedState, choosingState) { PaletteEditorChoice(selectedState, choosingState) }
    val selection = PaletteEditorSelection.of(session, choice.selected)
    val actions = PaletteEditorActions(selection, choice, callbacks)
    val content = PaletteEditorContent(session, selection, actions, callbacks)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PaletteFrameDropdown(view.frames, !session.isColorEditActive)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PaletteEditorSlots(selection, Modifier.height(120.dp)) {
                if (session.isColorEditActive) callbacks.colorEdit.cancel()
                actions.slotTapped(it)
            }
            PaletteEditorBody(content, json)
            if (choice.choosingReplacement) PaletteReplacementPrompt()
            notice?.let { PaletteEditorNoticeText(it) }
        }
        PaletteEditorSessionActions(session, callbacks)
    }
}

private data class PaletteEditorContent(
    val session: PaletteEditSession,
    val selection: PaletteEditorSelection,
    val actions: PaletteEditorActions,
    val callbacks: EditorPaletteCallbacks,
)

@Composable
private fun PaletteEditorBody(
    content: PaletteEditorContent,
    json: PaletteJsonActions,
) {
    val session = content.session
    val selection = content.selection
    var operationsExpanded by remember { mutableStateOf(false) }
    session.pendingImport?.let { PaletteImportSection(session.draft, it, content.callbacks) }
    key(selection.selected) {
        val model =
            PaletteColorEditorModel(
                selection.selectedEntry.color,
                session.pendingImport == null && json.idle,
                selection.editable,
                session.isColorEditActive,
            )
        PaletteColorEditor(model, PaletteColorEditCallbacks(selection.selectedEntry.index, content.callbacks))
    }
    PaletteEditorDisclosure(R.string.palette_editor_operations, operationsExpanded) {
        operationsExpanded =
            !operationsExpanded
    }
    if (operationsExpanded) {
        PaletteEditorSlotActions(selection, content.actions)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val enabled = json.idle && selection.editable
            PaletteEditorButton(R.string.export_palette_json, enabled, onClick = json::export)
            PaletteEditorButton(R.string.import_palette_json, enabled, onClick = json::import)
        }
    }
}

@Composable
private fun PaletteReplacementPrompt() {
    Text(
        stringResource(R.string.palette_editor_replacement),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.editorDescription(R.string.palette_editor_replacement),
    )
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
            PaletteEditorSlot(
                entry,
                PaletteSlotAppearance(
                    position == selection.selected,
                    entry.index == selection.defaultIndex,
                ),
                tap,
            )
        }
    }
}

@Composable
private fun PaletteEditorSlot(
    entry: PaletteEntry,
    appearance: PaletteSlotAppearance,
    tap: (Int) -> Unit,
) {
    val number = entry.index.value + 1
    val borderColor =
        if (appearance.selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .size(56.dp)
                .selectable(appearance.selected, role = Role.Button, onClick = { tap(entry.index.value) })
                .editorDescription(
                    R.string.palette_editor_slot,
                    number,
                    identity = "editor_palette_editor_slot_$number",
                ).border(if (appearance.selected) 3.dp else 1.dp, borderColor, RoundedCornerShape(4.dp))
                .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .paletteCheckerboard()
                .background(entry.color.toComposeColor()),
        ) {
            if (appearance.isDefault) {
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

private data class PaletteSlotAppearance(
    val selected: Boolean,
    val isDefault: Boolean,
)

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
    val editable = session.pendingImport == null && !session.isColorEditActive
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaletteFooterButton(R.string.palette_editor_undo, editable && session.canUndoDraft, Modifier.weight(1f)) {
                callbacks.onUndo()
            }
            PaletteFooterButton(R.string.palette_editor_redo, editable && session.canRedoDraft, Modifier.weight(1f)) {
                callbacks.onRedo()
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaletteFooterButton(R.string.palette_editor_apply, editable, Modifier.weight(1f)) { callbacks.onApply() }
            PaletteFooterButton(R.string.palette_editor_cancel, true, Modifier.weight(1f)) { callbacks.onCancel() }
        }
    }
}

@Composable
private fun PaletteFooterButton(
    label: Int,
    enabled: Boolean,
    modifier: Modifier,
    click: () -> Unit,
) {
    Button(
        onClick = click,
        enabled = enabled,
        colors = editorButtonColors(),
        modifier = modifier.heightIn(min = 48.dp).editorDescription(label),
    ) {
        Text(stringResource(label))
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
        modifier = Modifier.heightIn(min = 48.dp).editorDescription(label, identity = identity),
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(stringResource(label))
    }
}

package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorAppearance
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorControlEdge
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorLayout

@Composable
internal fun EditorScreen(
    renderState: State<EditorRenderState>,
    persistenceOperation: PersistenceOperationProjection,
    autosave: AutosaveProjection,
    callbacks: EditorCallbacks,
    persistenceCallbacks: EditorPersistenceCallbacks,
    modifier: Modifier,
) {
    val appearance by remember(renderState) { derivedStateOf { renderState.value.appearance } }
    var panel by remember { mutableStateOf<EditorPanel?>(null) }
    val openPanel: (EditorPanel) -> Unit = {
        callbacks.onPointerCancel()
        panel = it
    }
    val storage =
        remember(persistenceOperation, autosave, persistenceCallbacks) {
            EditorStorageInputs(persistenceOperation, autosave, persistenceCallbacks)
        }
    MaterialTheme(colorScheme = appearance.theme.colorScheme()) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding()) {
                EditorHeader(renderState, openPanel)
                Box(Modifier.weight(1f)) {
                    EditorWorkArea(renderState, appearance, callbacks) { openPanel(EditorPanel.Palette) }
                }
                EditorStatus(renderState, storage)
            }
            panel?.let { current ->
                EditorPanelSurface(EditorPanelPlacement(current, appearance.controlEdge), { panel = null }) {
                    PanelContent(current, EditorPanelInputs(renderState, callbacks, storage)) { panel = null }
                }
            }
            PersistenceConfirmation(persistenceOperation, persistenceCallbacks)
        }
    }
}

@Composable
private fun EditorHeader(
    state: State<EditorRenderState>,
    openPanel: (EditorPanel) -> Unit,
) {
    val size by remember(state) { derivedStateOf { state.value.snapshot.size } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("NENE-PIXEL", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            "${size.width.value} × ${size.height.value}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(
            onClick = { openPanel(EditorPanel.File) },
            modifier =
                Modifier.semantics {
                    contentDescription =
                        "File"
                },
        ) {
            EditorSymbol(EditorIcon.File)
        }
        IconButton(
            onClick = { openPanel(EditorPanel.Appearance) },
            modifier =
                Modifier.semantics {
                    contentDescription =
                        "Appearance"
                },
        ) {
            EditorSymbol(EditorIcon.Settings)
        }
    }
}

@Composable
private fun EditorWorkArea(
    state: State<EditorRenderState>,
    appearance: EditorAppearance,
    callbacks: EditorCallbacks,
    openPalette: () -> Unit,
) {
    if (appearance.layout == EditorLayout.Tabletop) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth()) { EditorCanvas(state, callbacks) }
            Surface(color = appearance.theme.railColor(), modifier = Modifier.fillMaxWidth()) {
                Box(contentAlignment = Alignment.Center) { EditorToolDock(state, callbacks, openPalette) }
            }
        }
    } else {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Absolute.Left) {
            if (appearance.controlEdge == EditorControlEdge.Left) {
                SideDock(state, callbacks, openPalette)
            }
            Box(Modifier.weight(1f)) { EditorCanvas(state, callbacks) }
            if (appearance.controlEdge == EditorControlEdge.Right) {
                SideDock(state, callbacks, openPalette)
            }
        }
    }
}

@Composable
private fun SideDock(
    state: State<EditorRenderState>,
    callbacks: EditorCallbacks,
    openPalette: () -> Unit,
) {
    val theme by remember(state) { derivedStateOf { state.value.appearance.theme } }
    Surface(color = theme.railColor(), modifier = Modifier.fillMaxHeight()) {
        EditorToolDock(state, callbacks, openPalette)
    }
}

@Composable
private fun EditorCanvas(
    state: State<EditorRenderState>,
    callbacks: EditorCallbacks,
) {
    val size by remember(state) { derivedStateOf { state.value.snapshot.size } }
    PixelCanvas(state, size, callbacks, Modifier.fillMaxSize())
}

@Composable
private fun EditorStatus(
    state: State<EditorRenderState>,
    storage: EditorStorageInputs,
) {
    val dirty by remember(state) { derivedStateOf { state.value.dirtyState } }
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        DocumentStatusRow(dirty, storage.operation, storage.autosave, storage.callbacks)
    }
}

@Composable
private fun PanelContent(
    panel: EditorPanel,
    inputs: EditorPanelInputs,
    dismiss: () -> Unit,
) {
    val state = inputs.state.value
    when (panel) {
        EditorPanel.Palette -> {
            PaletteControls(state.palette, state.activePaletteIndex, inputs.callbacks, dismiss)
        }

        EditorPanel.Appearance -> {
            AppearanceControls(state.appearance, inputs.callbacks)
        }

        EditorPanel.File -> {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PersistenceControls(inputs.storage.operation, state.snapshot.size, inputs.storage.callbacks, dismiss)
                Text(
                    inputs.storage.operation.statusText(inputs.storage.autosave),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

private data class EditorStorageInputs(
    val operation: PersistenceOperationProjection,
    val autosave: AutosaveProjection,
    val callbacks: EditorPersistenceCallbacks,
)

private data class EditorPanelInputs(
    val state: State<EditorRenderState>,
    val callbacks: EditorCallbacks,
    val storage: EditorStorageInputs,
)

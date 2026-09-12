package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorLayout
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool

@Composable
internal fun EditorToolDock(
    state: State<EditorRenderState>,
    callbacks: EditorCallbacks,
    openPalette: () -> Unit,
) {
    val controls by remember(state) {
        derivedStateOf {
            DockInputs(state.value.activeTool, state.value.canUndo, state.value.canRedo, state.value.activeColor)
        }
    }
    val modifier = Modifier.padding(4.dp)
    val layout by remember(state) { derivedStateOf { state.value.appearance.layout } }
    if (layout == EditorLayout.Tabletop) {
        Row(
            modifier = modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { DockButtons(controls, callbacks, openPalette) }
    } else {
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { DockButtons(controls, callbacks, openPalette) }
    }
}

@Composable
private fun DockButtons(
    inputs: DockInputs,
    callbacks: EditorCallbacks,
    openPalette: () -> Unit,
) {
    DockButton(DockControl("Pencil", EditorIcon.Pencil, inputs.activeTool == DrawingTool.Pencil)) {
        callbacks.onSelectTool(DrawingTool.Pencil)
    }
    DockButton(DockControl("Eraser", EditorIcon.Eraser, inputs.activeTool == DrawingTool.Eraser)) {
        callbacks.onSelectTool(DrawingTool.Eraser)
    }
    DockButton(DockControl("Undo", EditorIcon.Undo, enabled = inputs.canUndo)) { callbacks.onUndo() }
    DockButton(DockControl("Redo", EditorIcon.Redo, enabled = inputs.canRedo)) { callbacks.onRedo() }
    DockButton(DockControl("Palette", EditorIcon.Palette), inputs.activeColor, openPalette)
}

@Composable
private fun DockButton(
    control: DockControl,
    activeColor: PixelColor? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val foreground =
        (if (control.selected) scheme.onPrimaryContainer else scheme.onSurface)
            .copy(alpha = if (control.enabled) 1f else 0.38f)
    val background = if (control.selected) scheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .width(60.dp)
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    background,
                ).selectable(control.selected, enabled = control.enabled, role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = control.description()
                    activeColor?.let { color ->
                        stateDescription =
                            "Active RGBA ${color.red.value}, ${color.green.value}, " +
                            "${color.blue.value}, ${color.alpha.value}"
                    }
                }.padding(4.dp),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides
                foreground,
        ) {
            DockSymbol(control.icon, activeColor)
        }
        Text(
            control.label,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
        )
    }
}

@Composable
private fun DockSymbol(
    icon: EditorIcon,
    activeColor: PixelColor?,
) {
    if (activeColor == null) {
        EditorSymbol(icon)
    } else {
        Box(
            Modifier
                .size(24.dp)
                .border(1.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp))
                .padding(2.dp)
                .background(activeColor.toComposeColor(), RoundedCornerShape(2.dp)),
        )
    }
}

private data class DockInputs(
    val activeTool: DrawingTool,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val activeColor: PixelColor,
)

private data class DockControl(
    val label: String,
    val icon: EditorIcon,
    val selected: Boolean = false,
    val enabled: Boolean = true,
) {
    fun description(): String =
        when (icon) {
            EditorIcon.Pencil, EditorIcon.Eraser -> "$label tool"
            EditorIcon.Palette -> "Open palette"
            EditorIcon.Undo, EditorIcon.Redo, EditorIcon.File, EditorIcon.Settings, EditorIcon.Close -> label
        }
}

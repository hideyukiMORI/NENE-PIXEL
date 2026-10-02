package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * The underlay row's "more" button and its menu (#170 A7): replace the image ([onPick]), move and scale it (#171,
 * ADR 0032 "Adjust mode"), fit it to the picture again, and remove it. Open or closed is local Compose state;
 * choosing an item closes the menu first. "Move and scale" sets the adjusting underlay and then calls [onClosePanel]
 * so the layer panel no longer covers the picture; it is disabled while the underlay is hidden. Every other item is
 * always enabled.
 */
@Composable
internal fun UnderlayRowMenu(
    underlay: ReferenceUnderlay,
    callbacks: EditorUnderlayCallbacks,
    onPick: () -> Unit,
    onClosePanel: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.editorDescription(R.string.underlay_more)) {
            EditorSymbol(EditorIcon.More)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val choose = { action: () -> Unit ->
                expanded = false
                action()
            }
            UnderlayMenuItem(R.string.underlay_replace, REPLACE_TAG) { choose(onPick) }
            val shown = underlay.visibility == UnderlayVisibility.Shown
            UnderlayMenuItem(R.string.underlay_adjust, ADJUST_TAG, enabled = shown) {
                choose {
                    callbacks.onSet(underlay.adjusting())
                    onClosePanel()
                }
            }
            UnderlayMenuItem(R.string.underlay_fit, FIT_TAG) { choose { callbacks.onSet(underlay.fitted()) } }
            UnderlayMenuItem(R.string.underlay_remove, REMOVE_TAG) { choose { callbacks.onClear() } }
        }
    }
}

@Composable
private fun UnderlayMenuItem(
    label: Int,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = onClick,
        modifier = Modifier.testTag(tag),
        enabled = enabled,
    )
}

private const val REPLACE_TAG: String = "editor_underlay_replace"
private const val ADJUST_TAG: String = "editor_underlay_adjust"
private const val FIT_TAG: String = "editor_underlay_fit"
private const val REMOVE_TAG: String = "editor_underlay_remove"

package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.R
import kotlin.math.roundToInt

/**
 * The panel's underlay row (#170 A7), after the last layer row inside the same scroll, so it scrolls with the rows;
 * the add row below stays fixed. A 1dp `outlineVariant` line separates it from the layer rows.
 *
 * Without an [underlay] it is one full-width text button that calls [onPick]. With one it is two lines: the
 * visibility toggle, the "Underlay" label and the "more" menu (replace, move and scale, fit, remove), then the
 * opacity slider over alpha 26 to 255, disabled while the underlay is hidden. The row is never selectable, since
 * the underlay is never a drawing target, and has no background. Every change derives the next value from
 * [underlay] and hands it to [callbacks]; the row reads nothing else, and [underlay] is unchanged by strokes. The
 * menu's "move and scale" calls [onClosePanel] after it sets the adjusting underlay (#171).
 */
@Composable
internal fun LayerPanelUnderlayRow(
    underlay: ReferenceUnderlay?,
    callbacks: EditorUnderlayCallbacks,
    onPick: () -> Unit,
    onClosePanel: () -> Unit,
) {
    Column {
        HorizontalDivider(thickness = LayerGeometry.BORDER, color = MaterialTheme.colorScheme.outlineVariant)
        if (underlay == null) {
            TextButton(
                onClick = onPick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = PICK_HEIGHT)
                        .editorDescription(R.string.underlay_pick),
            ) {
                Text(stringResource(R.string.underlay_pick))
            }
        } else {
            UnderlayHeading(underlay, callbacks, onPick, onClosePanel)
            UnderlayOpacitySlider(underlay, callbacks)
        }
    }
}

@Composable
private fun UnderlayHeading(
    underlay: ReferenceUnderlay,
    callbacks: EditorUnderlayCallbacks,
    onPick: () -> Unit,
    onClosePanel: () -> Unit,
) {
    val hidden = underlay.visibility == UnderlayVisibility.Hidden
    val toggle = if (hidden) R.string.underlay_show else R.string.underlay_hide
    Row(Modifier.fillMaxWidth().heightIn(min = HEADING_MIN_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { callbacks.onSet(underlay.toggledVisibility()) },
            modifier = Modifier.editorDescription(toggle, identity = VISIBILITY_TAG),
        ) {
            EditorSymbol(if (hidden) EditorIcon.Hidden else EditorIcon.Visible)
        }
        Text(
            stringResource(R.string.underlay),
            color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = NAME_GAP),
        )
        UnderlayRowMenu(underlay, callbacks, onPick, onClosePanel)
    }
}

/**
 * Speaks "Underlay opacity" and the alpha as a rounded percentage of 255. The panel row and the adjust bar (#171)
 * share it; [identity] is the test tag, `editor_underlay_opacity` when absent.
 */
@Composable
internal fun UnderlayOpacitySlider(
    underlay: ReferenceUnderlay,
    callbacks: EditorUnderlayCallbacks,
    identity: String? = null,
) {
    val alpha = underlay.opacity.alpha
    val percent = (alpha * PERCENT / UnderlayOpacity.MAX.alpha.toFloat()).roundToInt()
    val state = stringResource(R.string.underlay_opacity_state, percent)
    Slider(
        value = alpha.toFloat(),
        onValueChange = { value ->
            val next = value.roundToInt()
            if (next != alpha) callbacks.onSet(underlay.withOpacity(UnderlayOpacity.create(next)))
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .editorDescription(R.string.underlay_opacity, identity = identity)
                .semantics { stateDescription = state },
        enabled = underlay.visibility == UnderlayVisibility.Shown,
        valueRange = UnderlayOpacity.MIN.alpha.toFloat()..UnderlayOpacity.MAX.alpha.toFloat(),
    )
}

private val PICK_HEIGHT: Dp = 48.dp
private val HEADING_MIN_HEIGHT: Dp = 56.dp
private val NAME_GAP: Dp = 4.dp
private const val PERCENT: Int = 100
private const val VISIBILITY_TAG: String = "editor_underlay_visibility"

package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

/**
 * The panel's row list (#144 UI spec "パネル" 2): [rows] top to bottom, front-most first as `layerRowsOf` orders them,
 * each with its "more" menu. Only this list scrolls. It is composed each time the panel opens; after the first
 * layout it moves focus to the active layer's row. Whenever the active layer changes (opening, add, a row tap,
 * undo / redo) it scrolls that row into view after the next layout (#144 U5 ruling).
 *
 * [trailing] follows the last (back-most) row inside the same scroll (#170 A7 revised ruling: the underlay row), so
 * the rows keep their positions and only the scroll range grows. The list takes the remaining height of the panel's
 * column without filling it, as before.
 */
@Composable
internal fun ColumnScope.LayerPanelRows(
    rows: List<LayerRowModel>,
    activeLayerId: LayerId,
    actions: LayerRowActions,
    trailing: @Composable ColumnScope.() -> Unit,
) {
    val activeFocus = remember { FocusRequester() }
    val activeView = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        activeFocus.requestFocus()
    }
    LaunchedEffect(activeLayerId) {
        withFrameNanos { }
        activeView.bringIntoView()
    }
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        rows.forEachIndexed { index, row ->
            key(row.id.value) {
                val current = row.id == activeLayerId
                val target =
                    if (current) Modifier.bringIntoViewRequester(activeView).focusRequester(activeFocus) else Modifier
                val entry = LayerRowEntry(row, current, position = rows.size - 1 - index, layerCount = rows.size)
                LayerRow(entry, actions.callbacks, target) { LayerRowMenu(entry, actions) }
            }
        }
        trailing()
    }
}

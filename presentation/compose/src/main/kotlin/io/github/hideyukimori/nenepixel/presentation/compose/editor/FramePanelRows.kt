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
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

/** Focus and scroll follow the canonical chosen row; no per-frame remembered-layer or selected-frame map. */
@Composable
internal fun ColumnScope.FramePanelRows(
    model: FramePanelModel,
    actions: FramePanelActions,
    duration: (FrameId) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val view = remember { BringIntoViewRequester() }
    LaunchedEffect(model.active) {
        withFrameNanos { }
        focus.requestFocus()
        view.bringIntoView()
    }
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        model.rows.forEachIndexed { index, row ->
            key(row.id.value) {
                val current = row.id == model.active
                val target = if (current) Modifier.focusRequester(focus).bringIntoViewRequester(view) else Modifier
                FrameRow(FrameRowEntry(row, current, index, model.capacity), actions, { duration(row.id) }, target)
            }
        }
    }
}

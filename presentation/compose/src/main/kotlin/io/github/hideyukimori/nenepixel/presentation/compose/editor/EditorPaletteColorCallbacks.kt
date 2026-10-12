package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

/** Continuous colour-edit capability sharing the palette adapter's sole reduce/publication route. */
internal class EditorPaletteColorCallbacks(
    private val reduce: (WorkspaceAction.PaletteSessionAction) -> EditorRenderState,
    private val active: () -> Boolean,
    private val current: () -> EditorRenderState,
) {
    fun begin(index: PaletteIndex): EditorRenderState = reduce(WorkspaceAction.BeginPaletteColorEdit(index))

    fun update(color: PixelColor): EditorRenderState = reduce(WorkspaceAction.UpdatePaletteColorEdit(color))

    fun finish(): EditorRenderState = reduce(WorkspaceAction.FinishPaletteColorEdit)

    /** Disposal may follow an explicit slot/panel cancellation; do not publish a second refusal. */
    fun cancel(): EditorRenderState = if (active()) reduce(WorkspaceAction.CancelPaletteColorEdit) else current()
}

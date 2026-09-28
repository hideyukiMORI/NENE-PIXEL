package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem

/**
 * The quick-select menu and eyedropper route (ADR 0029), reached through [EditorCallbacks.quickSelect]. Every call
 * reduces one `WorkspaceAction.QuickSelectAction` and publishes the resulting render state; canvas picks go through
 * the controller's pointer translation instead.
 */
internal class EditorQuickSelectCallbacks(
    private val adapter: EditorRuntimeAdapter,
    private val publish: (EditorRenderState) -> EditorRenderState,
) {
    fun onOpen(): EditorRenderState = reduce(WorkspaceAction.OpenQuickSelect)

    fun onHighlight(item: QuickSelectItem?): EditorRenderState = reduce(WorkspaceAction.HighlightQuickSelectItem(item))

    fun onConfirm(): EditorRenderState = reduce(WorkspaceAction.ConfirmQuickSelect)

    fun onCancel(): EditorRenderState = reduce(WorkspaceAction.CancelQuickSelect)

    fun onDisarm(): EditorRenderState = reduce(WorkspaceAction.DisarmEyedropper)

    private fun reduce(action: WorkspaceAction.QuickSelectAction): EditorRenderState =
        publish(adapter.reduce(action).renderState)
}

package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay

/**
 * The reference-underlay route (#170), reached through [EditorCallbacks.underlay]. Every call reduces at most one
 * `WorkspaceAction.ReferenceUnderlayAction` and publishes the resulting render state. The UI names the next value
 * through the named `ReferenceUnderlay` derivations; there is no function per derivation.
 *
 * [onUpdate] applies the derivation to the underlay the runtime holds when it is called (#171), so an action never
 * writes back a value composed earlier, for example while another finger moves the underlay. [onSet] still takes a
 * whole value, for the panel row and its menu.
 */
internal class EditorUnderlayCallbacks(
    private val runtime: EditorRuntime,
    private val adapter: EditorRuntimeAdapter,
    private val publish: (EditorRenderState) -> EditorRenderState,
) {
    fun onSet(underlay: ReferenceUnderlay): EditorRenderState = reduce(WorkspaceAction.SetReferenceUnderlay(underlay))

    /** Reduces [derive] of the runtime's current underlay; without an underlay nothing is reduced. */
    fun onUpdate(derive: (ReferenceUnderlay) -> ReferenceUnderlay): EditorRenderState {
        val current = runtime.state.workspaceState.underlay ?: return publish(adapter.renderState)
        return onSet(derive(current))
    }

    fun onClear(): EditorRenderState = reduce(WorkspaceAction.ClearReferenceUnderlay)

    private fun reduce(action: WorkspaceAction.ReferenceUnderlayAction): EditorRenderState =
        publish(adapter.reduce(action).renderState)
}

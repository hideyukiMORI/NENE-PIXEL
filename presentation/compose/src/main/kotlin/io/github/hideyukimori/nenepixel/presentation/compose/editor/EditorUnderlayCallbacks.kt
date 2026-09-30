package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay

/**
 * The reference-underlay route (#170), reached through [EditorCallbacks.underlay]. Every call reduces one
 * `WorkspaceAction.ReferenceUnderlayAction` and publishes the resulting render state. The UI derives the next value
 * through the named `ReferenceUnderlay` derivations and hands it to [onSet]; there is no function per derivation.
 */
internal class EditorUnderlayCallbacks(
    private val adapter: EditorRuntimeAdapter,
    private val publish: (EditorRenderState) -> EditorRenderState,
) {
    fun onSet(underlay: ReferenceUnderlay): EditorRenderState = reduce(WorkspaceAction.SetReferenceUnderlay(underlay))

    fun onClear(): EditorRenderState = reduce(WorkspaceAction.ClearReferenceUnderlay)

    private fun reduce(action: WorkspaceAction.ReferenceUnderlayAction): EditorRenderState =
        publish(adapter.reduce(action).renderState)
}

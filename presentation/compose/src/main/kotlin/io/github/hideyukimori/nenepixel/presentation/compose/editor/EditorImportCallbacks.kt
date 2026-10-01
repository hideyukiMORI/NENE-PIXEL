package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.ImportLayerCommand
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.RasterImportOption

/**
 * The pending PNG import route (ADR 0033 "Pending choice"), reached through [EditorCallbacks.rasterImport]. Choosing a
 * layer form executes `ImportLayerCommand` with that form's plan above the active layer, records the result in the
 * adapter's layer notice, and then clears the pending import, whether the command was applied or refused. Cancelling
 * only clears it. Without a pending import, or when the chosen form is not available, nothing changes.
 */
internal class EditorImportCallbacks(
    private val runtime: EditorRuntime,
    private val adapter: EditorRuntimeAdapter,
    private val publish: (EditorRenderState) -> EditorRenderState,
) {
    fun onAppend(): EditorRenderState = choose(PendingRasterImport::appending)

    fun onConvert(): EditorRenderState = choose(PendingRasterImport::converting)

    fun onCancel(): EditorRenderState = publish(adapter.reduce(WorkspaceAction.ClearPendingRasterImport).renderState)

    private fun choose(form: (PendingRasterImport) -> RasterImportOption): EditorRenderState {
        val workspace = runtime.state.workspaceState
        val option = workspace.pendingImport?.let(form) as? RasterImportOption.Available ?: return adapter.renderState
        val command = ImportLayerCommand.create(runtime.captureSource(), workspace.activeLayerId, option.plan)
        val result = runtime.execute(command)
        adapter.layerNotices.recordCommand(result, deletion = false, runtime.state.documentState)
        return onCancel()
    }
}

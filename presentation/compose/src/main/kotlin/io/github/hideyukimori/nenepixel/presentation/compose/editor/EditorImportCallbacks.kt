package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.editor.RasterImportApplyResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.LayerImportMode

/** Layer forms consume the pick-begin admission/anchor; later focus never retargets the operation. */
internal class EditorImportCallbacks(
    private val runtime: EditorRuntime,
    private val adapter: EditorRuntimeAdapter,
    private val publish: (EditorRenderState) -> EditorRenderState,
) {
    fun onAppend(): EditorRenderState = choose(LayerImportMode.Appending)

    fun onConvert(): EditorRenderState = choose(LayerImportMode.Converting)

    fun onCancel(): EditorRenderState {
        adapter.importNotice = null
        adapter.importNoticeSource = null
        return publish(adapter.reduce(WorkspaceAction.ClearPendingRasterImport).renderState)
    }

    private fun choose(mode: LayerImportMode): EditorRenderState {
        val pending = runtime.state.workspaceState.pendingImport
        adapter.importNotice =
            when (val result = runtime.rasterImportOperations.applyLayerImport(mode)) {
                RasterImportApplyResult.Applied -> null
                is RasterImportApplyResult.Rejected -> FrameEditorNotice.Rejected(result.reason)
                is RasterImportApplyResult.CommandRejected -> FrameEditorNotice.CommandRejected(result.reason)
                is RasterImportApplyResult.CommandFailed -> FrameEditorNotice.Failed(result.failure)
            }
        adapter.importNoticeSource = if (adapter.importNotice == null) null else pending
        return publish(adapter.renderState)
    }
}

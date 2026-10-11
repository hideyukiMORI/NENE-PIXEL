package io.github.hideyukimori.nenepixel.measurement

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntimeState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility

/** Short-lived assertions only: no document, projection, preview or pixel array is kept in a field. */
internal object P4LayerEditorStateChecks {
    fun empty(state: EditorRuntimeState) {
        common(state)
        check(state.documentState.revision.value == 0L)
        check(state.documentState.layers.size == 1)
        check(
            state.documentState.layers
                .single()
                .snapshot
                .copyCoverage()
                .all { it == 0.toByte() },
        )
        check(state.historyAvailability == HistoryAvailability.None)
        check(state.dirtyState == DocumentDirtyState.Clean)
        check(state.workspaceState.preview == null)
    }

    fun maximum(
        state: EditorRuntimeState,
        committed: Boolean,
    ) {
        common(state)
        val document = state.documentState
        check(document.id.value == "14500000000000000000000000000000")
        check(document.revision.value == if (committed) 1L else 0L)
        check(document.layers.size == 16)
        check(state.workspaceState.activeLayerId.value == 16)
        check(
            state.historyAvailability == if (committed) HistoryAvailability.UndoAvailable else HistoryAvailability.None,
        )
        check(state.dirtyState == if (committed) DocumentDirtyState.Dirty else DocumentDirtyState.Clean)
        checkPalette(document)
        checkMaximumLayers(document, committed)
    }

    private fun checkMaximumLayers(
        document: DocumentState,
        committed: Boolean,
    ) {
        document.layers.forEachIndexed { index, layer ->
            val id = index + 1
            check(layer.id.value == id && layer.visibility == LayerVisibility.Visible)
            check(layer.name.value == "\uD83D\uDE00".repeat(32))
            check(layer.snapshot.copyCoverage().all { it.toInt() and 0xFF == 0xFF })
            val pixels = layer.snapshot.copyPackedIndices()
            for (position in pixels.indices) {
                val expected = if (committed && id == 16 && position / 256 == position % 256) 0 else id
                check(pixels[position].toInt() and 0xFF == expected)
            }
        }
    }

    fun preview(state: EditorRuntimeState) {
        check(state.documentState.revision.value == 0L)
        val preview = checkNotNull(state.workspaceState.preview)
        check(preview.layerId.value == 16 && preview.positionCount == 4081)
    }

    private fun common(state: EditorRuntimeState) {
        val document = state.documentState
        val workspace = state.workspaceState
        check(document.size.width.value == 256 && document.size.height.value == 256)
        check(workspace.activePaletteIndex.value == 0)
        check(workspace.activeTool == DrawingTool.Pencil)
        check(workspace.viewport == ViewportState.initial(document.size))
        check(!workspace.actualSizeWindow.visible && workspace.paletteEditSession == null)
        check(workspace.quickSelection.menu == null)
    }

    private fun checkPalette(document: DocumentState) {
        check(document.definition.defaultIndex.value == 0)
        val palette = document.definition.palette
        check(palette.entryCount == 256)
        palette.entries().forEachIndexed { index, entry ->
            val expected = if (index == 0) 0x000000FF else index * 0x01010100 or 128
            check(entry.index.value == index && entry.color.toPackedRgba8888() == expected)
        }
    }
}

package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

/** Snapshot-free row metadata and global record capacity; neither is an editable frame owner. */
internal data class FramePanelModel(
    val rows: List<FrameRowModel>,
    val frames: EditorFrameProjection,
    val capacity: FrameCapacity,
    val notice: FrameEditorNotice?,
) {
    val active: FrameId get() = frames.editingFrameId
    val playback get() = frames.playback
}

internal fun framePanelModel(state: EditorRenderState): FramePanelModel =
    FramePanelModel(
        state.document.frames.map { FrameRowModel(it.id, it.duration, it.layers.size) },
        state.frames,
        FrameCapacity(state.document.frames.size, state.document.totalLayerCount),
        state.frameNotice,
    )

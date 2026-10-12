package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

/** Snapshot-free row metadata and global record capacity; neither is an editable frame owner. */
internal data class FramePanelModel(
    val rows: List<FrameRowModel>,
    val active: FrameId,
    val capacity: FrameCapacity,
    val notice: FrameEditorNotice?,
)

internal fun framePanelModel(state: EditorRenderState): FramePanelModel =
    FramePanelModel(
        state.document.frames.map { FrameRowModel(it.id, it.duration, it.layers.size) },
        state.activeFrameId,
        FrameCapacity(state.document.frames.size, state.document.totalLayerCount),
        state.frameNotice,
    )

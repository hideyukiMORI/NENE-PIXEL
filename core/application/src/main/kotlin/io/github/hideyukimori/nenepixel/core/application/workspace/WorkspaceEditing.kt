package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool

/** Stable editing focus and tool; transient gesture paths are kept separately to share this per sample. */
internal data class WorkspaceEditing(
    val editTarget: EditTarget,
    val activeTool: DrawingTool,
)

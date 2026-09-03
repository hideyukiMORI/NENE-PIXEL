package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot

public class EditorRenderState internal constructor(
    public val snapshot: PixelSnapshot,
    public val activeColor: PixelColor,
    public val activeTool: DrawingTool,
    public val preview: ToolGesture?,
    public val viewport: ViewportState,
    public val canUndo: Boolean,
    public val canRedo: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is EditorRenderState &&
                    snapshot == other.snapshot &&
                    activeColor == other.activeColor &&
                    activeTool == other.activeTool &&
                    preview == other.preview &&
                    viewport == other.viewport &&
                    canUndo == other.canUndo &&
                    canRedo == other.canRedo
            )

    override fun hashCode(): Int =
        listOf(snapshot, activeColor, activeTool, preview, viewport, canUndo, canRedo)
            .fold(INITIAL_HASH) { hash, value -> hash * HASH_MULTIPLIER + (value?.hashCode() ?: 0) }

    override fun toString(): String =
        "EditorRenderState(" +
            "snapshot=$snapshot, activeColor=$activeColor, activeTool=$activeTool, " +
            "preview=$preview, viewport=$viewport, " +
            "canUndo=$canUndo, canRedo=$canRedo)"

    private companion object {
        const val INITIAL_HASH: Int = 1
        const val HASH_MULTIPLIER: Int = 31
    }
}

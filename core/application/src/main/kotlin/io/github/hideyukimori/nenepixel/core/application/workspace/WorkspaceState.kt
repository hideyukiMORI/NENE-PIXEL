package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize

public class WorkspaceState private constructor(
    public val activeColor: PixelColor,
    public val activeTool: DrawingTool,
    public val viewport: ViewportState,
    public val preview: ToolGesture?,
) {
    internal fun withActiveColor(activeColor: PixelColor): WorkspaceState =
        WorkspaceState(activeColor, activeTool, viewport, preview)

    internal fun withActiveTool(activeTool: DrawingTool): WorkspaceState =
        WorkspaceState(activeColor, activeTool, viewport, preview)

    internal fun withPreview(preview: ToolGesture): WorkspaceState =
        WorkspaceState(activeColor, activeTool, viewport, preview)

    internal fun withoutPreview(): WorkspaceState = WorkspaceState(activeColor, activeTool, viewport, null)

    internal fun withViewport(viewport: ViewportState): WorkspaceState =
        WorkspaceState(activeColor, activeTool, viewport, null)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is WorkspaceState &&
                    activeColor == other.activeColor &&
                    activeTool == other.activeTool &&
                    viewport == other.viewport &&
                    preview == other.preview
            )

    override fun hashCode(): Int =
        (((activeColor.hashCode() * HASH_MULTIPLIER) + activeTool.hashCode()) * HASH_MULTIPLIER + viewport.hashCode()) *
            HASH_MULTIPLIER + (preview?.hashCode() ?: 0)

    override fun toString(): String =
        "WorkspaceState(activeColor=$activeColor, activeTool=$activeTool, viewport=$viewport, preview=$preview)"

    public companion object {
        private const val HASH_MULTIPLIER: Int = 31

        public fun create(
            activeColor: PixelColor,
            canvas: CanvasSize,
        ): WorkspaceState = WorkspaceState(activeColor, DrawingTool.Pencil, ViewportState.initial(canvas), null)
    }
}

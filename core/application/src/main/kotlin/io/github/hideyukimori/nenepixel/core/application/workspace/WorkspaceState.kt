package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

public class WorkspaceState private constructor(
    public val activePaletteIndex: PaletteIndex,
    public val activeTool: DrawingTool,
    public val viewport: ViewportState,
    public val preview: ToolGesture?,
) {
    internal fun withActivePaletteIndex(activePaletteIndex: PaletteIndex): WorkspaceState =
        WorkspaceState(activePaletteIndex, activeTool, viewport, preview)

    internal fun withActiveTool(activeTool: DrawingTool): WorkspaceState =
        WorkspaceState(activePaletteIndex, activeTool, viewport, preview)

    internal fun withPreview(preview: ToolGesture): WorkspaceState =
        WorkspaceState(activePaletteIndex, activeTool, viewport, preview)

    internal fun withoutPreview(): WorkspaceState = WorkspaceState(activePaletteIndex, activeTool, viewport, null)

    internal fun withViewport(viewport: ViewportState): WorkspaceState =
        WorkspaceState(activePaletteIndex, activeTool, viewport, null)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is WorkspaceState &&
                    activePaletteIndex == other.activePaletteIndex &&
                    activeTool == other.activeTool &&
                    viewport == other.viewport &&
                    preview == other.preview
            )

    override fun hashCode(): Int =
        (
            ((activePaletteIndex.hashCode() * HASH_MULTIPLIER) + activeTool.hashCode()) * HASH_MULTIPLIER +
                viewport.hashCode()
        ) *
            HASH_MULTIPLIER + (preview?.hashCode() ?: 0)

    override fun toString(): String =
        "WorkspaceState(" +
            "activePaletteIndex=$activePaletteIndex, activeTool=$activeTool, viewport=$viewport, preview=$preview)"

    public companion object {
        private const val HASH_MULTIPLIER: Int = 31

        public fun create(canvas: CanvasSize): WorkspaceState =
            WorkspaceState(PaletteIndex.first, DrawingTool.Pencil, ViewportState.initial(canvas), null)
    }
}

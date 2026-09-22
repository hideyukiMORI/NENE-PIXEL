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
    public val appearance: EditorAppearance,
    public val actualSizeWindow: ActualSizeWindow,
) {
    internal fun withActivePaletteIndex(activePaletteIndex: PaletteIndex): WorkspaceState =
        WorkspaceState(activePaletteIndex, activeTool, viewport, preview, appearance, actualSizeWindow)

    internal fun withActiveTool(activeTool: DrawingTool): WorkspaceState =
        WorkspaceState(activePaletteIndex, activeTool, viewport, preview, appearance, actualSizeWindow)

    internal fun withPreview(preview: ToolGesture): WorkspaceState =
        WorkspaceState(activePaletteIndex, activeTool, viewport, preview, appearance, actualSizeWindow)

    internal fun withoutPreview(): WorkspaceState =
        WorkspaceState(activePaletteIndex, activeTool, viewport, null, appearance, actualSizeWindow)

    internal fun withViewport(viewport: ViewportState): WorkspaceState =
        WorkspaceState(activePaletteIndex, activeTool, viewport, null, appearance, actualSizeWindow)

    internal fun withAppearance(appearance: EditorAppearance): WorkspaceState =
        WorkspaceState(activePaletteIndex, activeTool, viewport, null, appearance, actualSizeWindow)

    /** The actual-size window is non-modal: it never cancels an in-progress gesture (ADR 0026). */
    internal fun withActualSizeWindow(actualSizeWindow: ActualSizeWindow): WorkspaceState =
        WorkspaceState(activePaletteIndex, activeTool, viewport, preview, appearance, actualSizeWindow)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is WorkspaceState &&
                    activePaletteIndex == other.activePaletteIndex &&
                    activeTool == other.activeTool &&
                    viewport == other.viewport &&
                    preview == other.preview &&
                    appearance == other.appearance &&
                    actualSizeWindow == other.actualSizeWindow
            )

    override fun hashCode(): Int =
        listOf(activePaletteIndex, activeTool, viewport, preview, appearance, actualSizeWindow)
            .fold(INITIAL_HASH) { hash, value -> hash * HASH_MULTIPLIER + (value?.hashCode() ?: 0) }

    override fun toString(): String =
        "WorkspaceState(" +
            "activePaletteIndex=$activePaletteIndex, activeTool=$activeTool, viewport=$viewport, " +
            "preview=$preview, appearance=$appearance, actualSizeWindow=$actualSizeWindow)"

    public companion object {
        private const val INITIAL_HASH: Int = 1
        private const val HASH_MULTIPLIER: Int = 31

        public fun create(canvas: CanvasSize): WorkspaceState =
            WorkspaceState(
                PaletteIndex.first,
                DrawingTool.Pencil,
                ViewportState.initial(canvas),
                null,
                EditorAppearance.initial,
                ActualSizeWindow.initial,
            )
    }
}

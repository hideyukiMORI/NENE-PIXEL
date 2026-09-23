package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
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
    public val paletteEditSession: PaletteEditSession?,
) {
    internal fun withActivePaletteIndex(activePaletteIndex: PaletteIndex): WorkspaceState =
        WorkspaceState(
            activePaletteIndex,
            activeTool,
            viewport,
            preview,
            appearance,
            actualSizeWindow,
            paletteEditSession,
        )

    internal fun withActiveTool(activeTool: DrawingTool): WorkspaceState =
        WorkspaceState(
            activePaletteIndex,
            activeTool,
            viewport,
            preview,
            appearance,
            actualSizeWindow,
            paletteEditSession,
        )

    internal fun withPreview(preview: ToolGesture): WorkspaceState =
        WorkspaceState(
            activePaletteIndex,
            activeTool,
            viewport,
            preview,
            appearance,
            actualSizeWindow,
            paletteEditSession,
        )

    internal fun withoutPreview(): WorkspaceState =
        WorkspaceState(activePaletteIndex, activeTool, viewport, null, appearance, actualSizeWindow, paletteEditSession)

    internal fun withViewport(viewport: ViewportState): WorkspaceState =
        WorkspaceState(activePaletteIndex, activeTool, viewport, null, appearance, actualSizeWindow, paletteEditSession)

    internal fun withAppearance(appearance: EditorAppearance): WorkspaceState =
        WorkspaceState(activePaletteIndex, activeTool, viewport, null, appearance, actualSizeWindow, paletteEditSession)

    /** The actual-size window is non-modal: it never cancels an in-progress gesture (ADR 0026). */
    internal fun withActualSizeWindow(actualSizeWindow: ActualSizeWindow): WorkspaceState =
        WorkspaceState(
            activePaletteIndex,
            activeTool,
            viewport,
            preview,
            appearance,
            actualSizeWindow,
            paletteEditSession,
        )

    /** The palette edit session is non-modal: opening or closing it never cancels an in-progress gesture (ADR 0022). */
    internal fun withPaletteEditSession(paletteEditSession: PaletteEditSession?): WorkspaceState =
        WorkspaceState(
            activePaletteIndex,
            activeTool,
            viewport,
            preview,
            appearance,
            actualSizeWindow,
            paletteEditSession,
        )

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is WorkspaceState &&
                    activePaletteIndex == other.activePaletteIndex &&
                    activeTool == other.activeTool &&
                    viewport == other.viewport &&
                    preview == other.preview &&
                    appearance == other.appearance &&
                    actualSizeWindow == other.actualSizeWindow &&
                    paletteEditSession == other.paletteEditSession
            )

    override fun hashCode(): Int =
        listOf(activePaletteIndex, activeTool, viewport, preview, appearance, actualSizeWindow, paletteEditSession)
            .fold(INITIAL_HASH) { hash, value -> hash * HASH_MULTIPLIER + (value?.hashCode() ?: 0) }

    override fun toString(): String =
        "WorkspaceState(" +
            "activePaletteIndex=$activePaletteIndex, activeTool=$activeTool, viewport=$viewport, " +
            "preview=$preview, appearance=$appearance, actualSizeWindow=$actualSizeWindow, " +
            "paletteEditSession=$paletteEditSession)"

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
                null,
            )
    }
}

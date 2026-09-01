package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize

public class WorkspaceState private constructor(
    public val activeColor: PixelColor,
    public val viewport: ViewportState,
    public val preview: ToolGesture?,
) {
    internal fun withActiveColor(activeColor: PixelColor): WorkspaceState =
        WorkspaceState(activeColor, viewport, preview)

    internal fun withPreview(preview: ToolGesture): WorkspaceState = WorkspaceState(activeColor, viewport, preview)

    internal fun withoutPreview(): WorkspaceState = WorkspaceState(activeColor, viewport, null)

    internal fun withViewport(viewport: ViewportState): WorkspaceState = WorkspaceState(activeColor, viewport, null)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is WorkspaceState &&
                    activeColor == other.activeColor &&
                    viewport == other.viewport &&
                    preview == other.preview
            )

    override fun hashCode(): Int =
        ((activeColor.hashCode() * HASH_MULTIPLIER) + viewport.hashCode()) * HASH_MULTIPLIER +
            (preview?.hashCode() ?: 0)

    override fun toString(): String = "WorkspaceState(activeColor=$activeColor, viewport=$viewport, preview=$preview)"

    public companion object {
        private const val HASH_MULTIPLIER: Int = 31

        public fun create(
            activeColor: PixelColor,
            canvas: CanvasSize,
        ): WorkspaceState = WorkspaceState(activeColor, ViewportState.initial(canvas), null)
    }
}

package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor

public class WorkspaceState private constructor(
    public val activeColor: PixelColor,
    public val preview: ToolGesture?,
) {
    public val viewport: FixedSliceViewport = FixedSliceViewport

    internal fun withActiveColor(activeColor: PixelColor): WorkspaceState = WorkspaceState(activeColor, preview)

    internal fun withPreview(preview: ToolGesture): WorkspaceState = WorkspaceState(activeColor, preview)

    internal fun withoutPreview(): WorkspaceState = WorkspaceState(activeColor, null)

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

        public fun create(activeColor: PixelColor): WorkspaceState = WorkspaceState(activeColor, null)
    }
}

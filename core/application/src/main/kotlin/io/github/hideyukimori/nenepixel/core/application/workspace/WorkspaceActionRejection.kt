package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition

public sealed interface WorkspaceActionRejection {
    public data object PreviewAlreadyActive : WorkspaceActionRejection

    public data object NoActivePreview : WorkspaceActionRejection

    public data class PreviewPositionOutsideCanvas internal constructor(
        public val canvas: CanvasSize,
        public val position: PixelPosition,
    ) : WorkspaceActionRejection

    public data class PreviewPathAboveSupportedMaximum internal constructor(
        public val attemptedCount: Long,
        public val maximum: Int,
    ) : WorkspaceActionRejection
}

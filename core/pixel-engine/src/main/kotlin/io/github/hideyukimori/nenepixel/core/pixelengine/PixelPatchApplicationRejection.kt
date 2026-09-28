package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell

public sealed interface PixelPatchApplicationRejection {
    public data class CanvasMismatch internal constructor(
        public val expected: CanvasSize,
        public val actual: CanvasSize,
    ) : PixelPatchApplicationRejection

    public data class BeforeValueMismatch internal constructor(
        public val position: PixelPosition,
        public val expected: PixelCell,
        public val actual: PixelCell,
    ) : PixelPatchApplicationRejection
}

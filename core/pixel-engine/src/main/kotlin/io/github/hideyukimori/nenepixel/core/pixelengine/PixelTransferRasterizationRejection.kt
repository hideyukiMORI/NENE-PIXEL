package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize

public sealed interface PixelTransferRasterizationRejection {
    public data class CanvasMismatch internal constructor(
        public val expected: CanvasSize,
        public val actual: CanvasSize,
    ) : PixelTransferRasterizationRejection
}

package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition

public sealed interface PixelPatchApplicationRejection {
    public data class CanvasMismatch internal constructor(
        public val expected: CanvasSize,
        public val actual: CanvasSize,
    ) : PixelPatchApplicationRejection

    public data class RevisionMismatch internal constructor(
        public val expected: Revision,
        public val actual: Revision,
    ) : PixelPatchApplicationRejection

    public data class BeforeValueMismatch internal constructor(
        public val position: PixelPosition,
        public val expected: PixelColor,
        public val actual: PixelColor,
    ) : PixelPatchApplicationRejection
}

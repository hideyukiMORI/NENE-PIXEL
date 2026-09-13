package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

public sealed interface PixelPatchCreationRejection {
    public data object EmptyPatch : PixelPatchCreationRejection

    public data object RevisionOverflow : PixelPatchCreationRejection

    public data class ChangeCountAboveSupportedMaximum internal constructor(
        public val attemptedCount: Int,
        public val maximum: Int,
    ) : PixelPatchCreationRejection

    public data class PositionOutsideCanvas internal constructor(
        public val canvas: CanvasSize,
        public val position: PixelPosition,
    ) : PixelPatchCreationRejection

    public data class DuplicatePosition internal constructor(
        public val position: PixelPosition,
    ) : PixelPatchCreationRejection

    public data class UnchangedPixel internal constructor(
        public val position: PixelPosition,
    ) : PixelPatchCreationRejection

    public data class IndexAboveStorageMaximum internal constructor(
        public val position: PixelPosition,
        public val attemptedIndex: PaletteIndex,
        public val maximum: Int,
    ) : PixelPatchCreationRejection
}

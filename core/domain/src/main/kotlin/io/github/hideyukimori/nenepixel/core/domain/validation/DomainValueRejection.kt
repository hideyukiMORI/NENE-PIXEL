package io.github.hideyukimori.nenepixel.core.domain.validation

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition

public sealed interface DomainValueRejection {
    public data class InvalidDocumentIdLength internal constructor(
        public val actualLength: Int,
    ) : DomainValueRejection

    public data class InvalidDocumentIdCharacter internal constructor(
        public val index: Int,
        public val character: Char,
    ) : DomainValueRejection

    public data class NonPositiveCanvasWidth internal constructor(
        public val attemptedValue: Int,
    ) : DomainValueRejection

    public data class NonPositiveCanvasHeight internal constructor(
        public val attemptedValue: Int,
    ) : DomainValueRejection

    public data class NegativePixelX internal constructor(
        public val attemptedValue: Int,
    ) : DomainValueRejection

    public data class NegativePixelY internal constructor(
        public val attemptedValue: Int,
    ) : DomainValueRejection

    public data class PixelRegionOutsideCanvas internal constructor(
        public val canvas: CanvasSize,
        public val origin: PixelPosition,
        public val size: CanvasSize,
    ) : DomainValueRejection

    public data class ColorChannelOutsideRange internal constructor(
        public val attemptedValue: Int,
    ) : DomainValueRejection

    public data class NegativePaletteIndex internal constructor(
        public val attemptedValue: Int,
    ) : DomainValueRejection

    public data class NegativeRevision internal constructor(
        public val attemptedValue: Long,
    ) : DomainValueRejection

    public data object RevisionOverflow : DomainValueRejection
}

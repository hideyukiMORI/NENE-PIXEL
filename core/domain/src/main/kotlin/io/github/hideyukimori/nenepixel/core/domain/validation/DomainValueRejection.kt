package io.github.hideyukimori.nenepixel.core.domain.validation

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

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

    public data class CanvasWidthAboveSupportedMaximum internal constructor(
        public val attemptedValue: Int,
        public val maximum: Int,
    ) : DomainValueRejection

    public data class NonPositiveCanvasHeight internal constructor(
        public val attemptedValue: Int,
    ) : DomainValueRejection

    public data class CanvasHeightAboveSupportedMaximum internal constructor(
        public val attemptedValue: Int,
        public val maximum: Int,
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

    public data class PixelSnapshotSizeMismatch internal constructor(
        public val expectedPixelCount: Long,
        public val actualPixelCount: Int,
    ) : DomainValueRejection

    public data class PixelSnapshotIndexAboveStorageMaximum internal constructor(
        public val position: Int,
        public val attemptedIndex: PaletteIndex,
        public val maximum: Int,
    ) : DomainValueRejection

    public data class PixelCoverageSizeMismatch internal constructor(
        public val expected: Int,
        public val actual: Int,
    ) : DomainValueRejection

    public data object PixelCoverageTrailingBitsSet : DomainValueRejection

    public data class EmptyPixelIndexNotZero internal constructor(
        public val rowMajorIndex: Int,
    ) : DomainValueRejection

    public data class LegacyRgbaSourceSizeMismatch internal constructor(
        public val expectedPixelCount: Long,
        public val actualPixelCount: Int,
    ) : DomainValueRejection

    public data class ImportRasterSideOutOfRange internal constructor(
        public val width: Int,
        public val height: Int,
    ) : DomainValueRejection

    public data class ImportRasterSizeMismatch internal constructor(
        public val expectedPixelCount: Int,
        public val actualPixelCount: Int,
    ) : DomainValueRejection

    public data class PixelPositionOutsideCanvas internal constructor(
        public val canvas: CanvasSize,
        public val position: PixelPosition,
    ) : DomainValueRejection

    public data object EmptyStrokePath : DomainValueRejection

    public data class StrokePathAboveSupportedMaximum internal constructor(
        public val attemptedCount: Int,
        public val maximum: Int,
    ) : DomainValueRejection

    public data class ColorChannelOutsideRange internal constructor(
        public val attemptedValue: Int,
    ) : DomainValueRejection

    public data class NegativePaletteIndex internal constructor(
        public val attemptedValue: Int,
    ) : DomainValueRejection

    public data object EmptyPalette : DomainValueRejection

    public data class PaletteBelowDefinitionMinimum internal constructor(
        public val attemptedCount: Int,
        public val minimum: Int,
    ) : DomainValueRejection

    public data class PaletteAboveSupportedMaximum internal constructor(
        public val attemptedCount: Int,
        public val maximum: Int,
    ) : DomainValueRejection

    public data class PaletteIndexOutsidePalette internal constructor(
        public val attemptedIndex: PaletteIndex,
        public val entryCount: Int,
    ) : DomainValueRejection

    public data class PaletteRemapSizeMismatch internal constructor(
        public val expectedCount: Int,
        public val attemptedCount: Int,
    ) : DomainValueRejection

    public data class PaletteRemapDestinationOutsidePalette internal constructor(
        public val sourceIndex: PaletteIndex,
        public val destinationIndex: PaletteIndex,
        public val targetEntryCount: Int,
    ) : DomainValueRejection

    public data class NegativeRevision internal constructor(
        public val attemptedValue: Long,
    ) : DomainValueRejection

    public data object RevisionOverflow : DomainValueRejection

    public data class InvalidLayerId internal constructor(
        public val value: Int,
    ) : DomainValueRejection

    public data object LayerIdOverflow : DomainValueRejection

    public data class LayerNameTooLong internal constructor(
        public val codePointCount: Int,
    ) : DomainValueRejection

    public data class LayerNameControlCharacter internal constructor(
        public val codePointIndex: Int,
    ) : DomainValueRejection

    public data class LayerNameInvalidSurrogate internal constructor(
        public val charIndex: Int,
    ) : DomainValueRejection

    public data class DocumentLayerCountOutOfRange internal constructor(
        public val count: Int,
    ) : DomainValueRejection

    public data class DuplicateLayerId internal constructor(
        public val layerId: Int,
    ) : DomainValueRejection

    public data class LayerSizeMismatch internal constructor(
        public val layerId: Int,
        public val expected: CanvasSize,
        public val actual: CanvasSize,
    ) : DomainValueRejection
}

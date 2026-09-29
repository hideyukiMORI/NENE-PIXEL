package io.github.hideyukimori.nenepixel.core.domain.pixel

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

public class PixelSnapshot private constructor(
    public val size: CanvasSize,
    private val packedIndices: ByteArray,
    private val coverage: ByteArray,
) {
    public val maximumIndex: PaletteIndex = maximumOf(packedIndices)

    public fun cellAt(position: PixelPosition): DomainValueResult<PixelCell> =
        if (size.contains(position)) {
            created(cellAtRowMajor(position.rowMajorIndex(size)))
        } else {
            rejected(DomainValueRejection.PixelPositionOutsideCanvas(size, position))
        }

    public fun copyPackedIndices(): ByteArray = packedIndices.copyOf()

    public fun copyCoverage(): ByteArray = coverage.copyOf()

    private fun cellAtRowMajor(rowMajorIndex: Int): PixelCell =
        if (coverage.isCoveredAt(rowMajorIndex)) {
            PixelCell.Covered(PaletteIndex.createWithinPalette(packedIndices[rowMajorIndex].toInt() and U8_MASK))
        } else {
            PixelCell.Empty
        }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PixelSnapshot &&
                    size == other.size &&
                    packedIndices.contentEquals(other.packedIndices) &&
                    coverage.contentEquals(other.coverage)
            )

    override fun hashCode(): Int =
        size.hashCode() * HASH_MULTIPLIER * HASH_MULTIPLIER +
            packedIndices.contentHashCode() * HASH_MULTIPLIER +
            coverage.contentHashCode()

    override fun toString(): String = "PixelSnapshot(size=$size)"

    public companion object {
        private const val HASH_MULTIPLIER: Int = 31
        private const val U8_MASK: Int = 0xff

        public fun create(
            size: CanvasSize,
            indices: List<PaletteIndex>,
        ): DomainValueResult<PixelSnapshot> =
            if (size.pixelCount != indices.size.toLong()) {
                rejected(DomainValueRejection.PixelSnapshotSizeMismatch(size.pixelCount, indices.size))
            } else {
                createOwned(size, indices)
            }

        private fun createOwned(
            size: CanvasSize,
            indices: List<PaletteIndex>,
        ): DomainValueResult<PixelSnapshot> {
            val invalidPosition = indices.indexOfFirst { it.value > U8_MASK }
            return if (invalidPosition >= 0) {
                rejected(
                    DomainValueRejection.PixelSnapshotIndexAboveStorageMaximum(
                        invalidPosition,
                        indices[invalidPosition],
                        U8_MASK,
                    ),
                )
            } else {
                val packed = ByteArray(indices.size) { indices[it].value.toByte() }
                created(PixelSnapshot(size, packed, fullCoverage(packed.size)))
            }
        }

        public fun createPackedIndices(
            size: CanvasSize,
            packedIndices: ByteArray,
        ): DomainValueResult<PixelSnapshot> =
            if (size.pixelCount == packedIndices.size.toLong()) {
                val owned = packedIndices.copyOf()
                created(PixelSnapshot(size, owned, fullCoverage(owned.size)))
            } else {
                rejected(DomainValueRejection.PixelSnapshotSizeMismatch(size.pixelCount, packedIndices.size))
            }

        public fun createFilled(
            size: CanvasSize,
            index: PaletteIndex,
        ): DomainValueResult<PixelSnapshot> =
            if (index.value <= U8_MASK) {
                created(
                    PixelSnapshot(
                        size,
                        ByteArray(size.pixelCount.toInt()) { index.value.toByte() },
                        fullCoverage(size.pixelCount.toInt()),
                    ),
                )
            } else {
                rejected(DomainValueRejection.PixelSnapshotIndexAboveStorageMaximum(0, index, U8_MASK))
            }

        public fun createEmpty(size: CanvasSize): PixelSnapshot {
            val pixelCount = size.pixelCount.toInt()
            return PixelSnapshot(size, ByteArray(pixelCount), ByteArray(coverageByteCount(pixelCount)))
        }

        public fun createPackedCells(
            size: CanvasSize,
            packedIndices: ByteArray,
            coverage: ByteArray,
        ): DomainValueResult<PixelSnapshot> =
            if (size.pixelCount == packedIndices.size.toLong()) {
                createOwnedCells(size, packedIndices.copyOf(), coverage.copyOf())
            } else {
                rejected(DomainValueRejection.PixelSnapshotSizeMismatch(size.pixelCount, packedIndices.size))
            }

        private fun createOwnedCells(
            size: CanvasSize,
            packedIndices: ByteArray,
            coverage: ByteArray,
        ): DomainValueResult<PixelSnapshot> =
            cellRejection(packedIndices, coverage)?.let(::rejected)
                ?: created(PixelSnapshot(size, packedIndices, coverage))

        private fun maximumOf(packed: ByteArray): PaletteIndex {
            var maximum = 0
            packed.forEach { value -> maximum = maxOf(maximum, value.toInt() and U8_MASK) }
            return PaletteIndex.createWithinPalette(maximum)
        }

        private fun PixelPosition.rowMajorIndex(size: CanvasSize): Int = y.value * size.width.value + x.value
    }
}

package io.github.hideyukimori.nenepixel.core.domain.pixel

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection

private const val BITS_PER_BYTE: Int = 8
private const val BIT_INDEX_MASK: Int = 7
private const val BYTE_SHIFT: Int = 3
private const val U8_MASK: Int = 0xff

internal fun coverageByteCount(pixelCount: Int): Int = (pixelCount + BIT_INDEX_MASK) / BITS_PER_BYTE

internal fun fullCoverage(pixelCount: Int): ByteArray {
    val coverage = ByteArray(coverageByteCount(pixelCount)) { U8_MASK.toByte() }
    val trailingBits = pixelCount and BIT_INDEX_MASK
    if (trailingBits != 0) {
        coverage[coverage.lastIndex] = ((1 shl trailingBits) - 1).toByte()
    }
    return coverage
}

internal fun ByteArray.isCoveredAt(rowMajorIndex: Int): Boolean =
    (this[rowMajorIndex ushr BYTE_SHIFT].toInt() ushr (rowMajorIndex and BIT_INDEX_MASK)) and 1 == 1

internal fun cellRejection(
    packedIndices: ByteArray,
    coverage: ByteArray,
): DomainValueRejection? {
    val pixelCount = packedIndices.size
    val expectedCoverage = coverageByteCount(pixelCount)
    return when {
        coverage.size != expectedCoverage -> {
            DomainValueRejection.PixelCoverageSizeMismatch(expectedCoverage, coverage.size)
        }

        hasTrailingBitsSet(coverage, pixelCount) -> {
            DomainValueRejection.PixelCoverageTrailingBitsSet
        }

        else -> {
            firstEmptyWithNonZeroIndex(packedIndices, coverage)?.let(DomainValueRejection::EmptyPixelIndexNotZero)
        }
    }
}

private fun hasTrailingBitsSet(
    coverage: ByteArray,
    pixelCount: Int,
): Boolean {
    val trailingBits = pixelCount and BIT_INDEX_MASK
    return trailingBits != 0 &&
        (coverage[coverage.lastIndex].toInt() and U8_MASK) ushr trailingBits != 0
}

private fun firstEmptyWithNonZeroIndex(
    packedIndices: ByteArray,
    coverage: ByteArray,
): Int? = packedIndices.indices.firstOrNull { !coverage.isCoveredAt(it) && packedIndices[it] != 0.toByte() }

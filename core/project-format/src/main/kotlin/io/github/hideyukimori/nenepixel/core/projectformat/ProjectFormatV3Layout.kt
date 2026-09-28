package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal object ProjectFormatV3Layout {
    const val VERSION: Int = 3
    const val FIXED_BYTE_COUNT: Int = ProjectFormatV2Layout.FIXED_BYTE_COUNT
    const val LAYER_COUNT_BYTE_COUNT: Int = 1
    const val MIN_LAYER_COUNT: Int = 1
    const val MAX_LAYER_COUNT: Int = 16
    const val MIN_LAYER_ID: Long = 1L
    const val MAX_LAYER_ID: Long = Int.MAX_VALUE.toLong()
    const val VISIBLE_FLAG: Int = 0x01
    const val FLAGS_OFFSET: Int = Int.SIZE_BYTES
    const val NAME_LENGTH_OFFSET: Int = FLAGS_OFFSET + 1
    const val NAME_OFFSET: Int = NAME_LENGTH_OFFSET + 1
    const val LAYER_FIXED_BYTE_COUNT: Int = NAME_OFFSET
    const val MAX_NAME_BYTE_COUNT: Int = 128
    private const val MAX_PIXEL_COUNT: Int =
        ProjectFormatV2Layout.MAX_CANVAS_AXIS * ProjectFormatV2Layout.MAX_CANVAS_AXIS
    private const val MAX_COVERAGE_BYTE_COUNT: Int = MAX_PIXEL_COUNT / Byte.SIZE_BITS
    private const val MAX_LAYER_BYTE_COUNT: Int =
        LAYER_FIXED_BYTE_COUNT + MAX_NAME_BYTE_COUNT + MAX_COVERAGE_BYTE_COUNT + MAX_PIXEL_COUNT
    const val MAX_FILE_BYTE_COUNT: Int =
        FIXED_BYTE_COUNT +
            ProjectFormatV2Layout.PALETTE_ENTRY_BYTE_COUNT * ProjectFormatV2Layout.MAX_PALETTE_ENTRY_COUNT +
            LAYER_COUNT_BYTE_COUNT + MAX_LAYER_COUNT * MAX_LAYER_BYTE_COUNT + Int.SIZE_BYTES
    private const val U8_MASK: Int = 0xff

    fun layerCountOffset(paletteEntryCount: Int): Int =
        FIXED_BYTE_COUNT + ProjectFormatV2Layout.PALETTE_ENTRY_BYTE_COUNT * paletteEntryCount

    fun coverageByteCount(pixelCount: Int): Int = (pixelCount + Byte.SIZE_BITS - 1) / Byte.SIZE_BITS

    fun layerByteCount(
        nameByteCount: Int,
        pixelCount: Int,
    ): Int = LAYER_FIXED_BYTE_COUNT + nameByteCount + coverageByteCount(pixelCount) + pixelCount

    fun unsignedByteAt(
        source: ProjectFormatBytes,
        index: Int,
    ): Int = source.byteAt(index).toInt() and U8_MASK

    fun position(
        size: CanvasSize,
        pixelIndex: Int,
    ): PixelPosition =
        PixelPosition.create(
            created(PixelX.create(pixelIndex % size.width.value)),
            created(PixelY.create(pixelIndex / size.width.value)),
        )

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Expected a valid v3 position: ${result.rejection}")
        }
}

package io.github.hideyukimori.nenepixel.core.pixelengine.importing

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/**
 * The part of an [ImportRaster] that lies on a canvas when their top-left corners meet (ADR 0033).
 *
 * [colors] lists the distinct colours of the region in order of first row-major occurrence;
 * [droppedPixelCount] counts the raster pixels outside the region that are not transparent.
 */
internal class RasterImportRegion(
    raster: ImportRaster,
    private val canvas: CanvasSize,
) {
    private val packed: IntArray = raster.copyPackedRgba8888()
    private val rasterWidth: Int = raster.width
    private val canvasWidth: Int = canvas.width.value
    private val width: Int = minOf(raster.width, canvasWidth)
    private val height: Int = minOf(raster.height, canvas.height.value)
    private val sortedColors: IntArray
    val droppedPixelCount: Int

    init {
        val values = IntArray(width * height)
        var position = 0
        forEachRegionPixel { _, value ->
            values[position] = value
            position += 1
        }
        droppedPixelCount = RasterColors.count(packed) - RasterColors.count(values)
        sortedColors = values.copyOf(RasterColors.sortDistinct(values))
    }

    private val orderOfSorted: IntArray = IntArray(sortedColors.size) { UNSEEN }
    val colors: IntArray = orderedColors()

    /** Builds the canvas cells; [slots] holds one palette index per entry of [colors]. */
    fun snapshot(slots: IntArray): DomainValueResult<PixelSnapshot> {
        val cellCount = canvas.pixelCount.toInt()
        val indices = ByteArray(cellCount)
        val coverage = ByteArray((cellCount + BIT_INDEX_MASK) / BITS_PER_BYTE)
        forEachRegionPixel { cell, value ->
            if (RasterColors.isColor(value)) {
                indices[cell] = slots[orderOfSorted[sortedColors.binarySearch(value)]].toByte()
                val byte = cell ushr BYTE_SHIFT
                coverage[byte] = (coverage[byte].toInt() or (1 shl (cell and BIT_INDEX_MASK))).toByte()
            }
        }
        return PixelSnapshot.createPackedCells(canvas, indices, coverage)
    }

    private fun orderedColors(): IntArray {
        val ordered = IntArray(sortedColors.size)
        var next = 0
        forEachRegionPixel { _, value ->
            val key = if (RasterColors.isColor(value)) sortedColors.binarySearch(value) else UNSEEN
            if (key != UNSEEN && orderOfSorted[key] == UNSEEN) {
                orderOfSorted[key] = next
                ordered[next] = value
                next += 1
            }
        }
        return ordered
    }

    private inline fun forEachRegionPixel(action: (cell: Int, value: Int) -> Unit) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                action(y * canvasWidth + x, packed[y * rasterWidth + x])
            }
        }
    }

    private companion object {
        const val UNSEEN: Int = -1
        const val BITS_PER_BYTE: Int = 8
        const val BIT_INDEX_MASK: Int = 7
        const val BYTE_SHIFT: Int = 3
    }
}

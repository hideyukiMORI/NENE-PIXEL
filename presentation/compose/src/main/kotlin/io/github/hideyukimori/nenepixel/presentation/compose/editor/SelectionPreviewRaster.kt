package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.render.SelectionPreviewImage
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode

/** Bounded rectangle updates for the shared base/buffer raster, never a second pixel/composite owner. */
internal class SelectionPreviewRaster(
    private val width: Int,
    private val base: IntArray,
    private val buffer: IntArray,
) {
    private var painted: List<PixelRegion> = emptyList()
    private val dirty = mutableListOf<PixelRegion>()

    internal var lastRestoredPixelCount: Int = 0
        private set

    fun forget() {
        painted = emptyList()
        dirty.clear()
    }

    fun clear() {
        lastRestoredPixelCount = 0
        for (region in painted) {
            val span = region.size.width.value
            repeat(region.size.height.value) { row ->
                val start = (region.origin.y.value + row) * width + region.origin.x.value
                System.arraycopy(base, start, buffer, start, span)
            }
            lastRestoredPixelCount += region.size.pixelCount.toInt()
        }
        painted.forEach(::markDirty)
        painted = emptyList()
    }

    fun paint(image: SelectionPreviewImage) {
        image.forEachPosition { position ->
            buffer[position.y.value * width + position.x.value] = image.packedRgba8888At(position).rgbaToArgb8888()
        }
        val transfer = image.transfer
        painted =
            if (transfer.mode == PixelTransferMode.Move) {
                listOf(transfer.source, transfer.destinationRegion)
            } else {
                listOf(transfer.destinationRegion)
            }
        painted.forEach(::markDirty)
    }

    fun transfer(target: (IntArray, PixelRegion) -> Unit) {
        dirty.forEach { target(buffer, it) }
        dirty.clear()
    }

    private fun markDirty(region: PixelRegion) {
        if (region !in dirty) dirty.add(region)
    }
}

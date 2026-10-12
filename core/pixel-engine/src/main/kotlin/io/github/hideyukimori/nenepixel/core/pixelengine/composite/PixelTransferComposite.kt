package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransfer
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelSurface
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelTransferCells
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelTransferPositions

/** Immutable source preparation reused for every placement, orientation and mode of one selected rectangle. */
public class PixelTransferComposite internal constructor(
    private val size: CanvasSize,
    private val palette: IntArray,
    surfaces: Array<PixelSurface?>,
    private val cells: PixelTransferCells,
) {
    private val composite = TargetLayerComposite(palette, surfaces)

    /** Visits each affected position once, in row-major order, without scanning any bounding-box gap. */
    public fun forEachPosition(
        transfer: PixelTransfer,
        consume: (PixelPosition) -> Unit,
    ) {
        require(transfer.canvas == size) { "Transfer canvas changed after preparation" }
        cells.requireSource(transfer)
        PixelTransferPositions(transfer).forEachIndex { pixel ->
            consume(PixelTransferCells.position(pixel % size.width.value, pixel / size.width.value))
        }
    }

    /** Packed RGBA8888 for an affected position, with the final target cell substituted before compositing. */
    public fun packedRgba8888At(
        position: PixelPosition,
        transfer: PixelTransfer,
    ): Int {
        require(
            transfer.canvas == size && size.contains(position),
        ) { "Transfer position/canvas changed after preparation" }
        cells.requireSource(transfer)
        require(
            transfer.destinationRegion.contains(position) ||
                (transfer.mode == PixelTransferMode.Move && transfer.source.contains(position)),
        ) { "Position is outside the transfer union" }
        val pixel = position.y.value * size.width.value + position.x.value
        val target = cells.afterAt(pixel, transfer)
        return composite.at(
            pixel,
            target and PixelTransferCells.COVERED_BIT != 0,
            palette[
                target and
                    PixelTransferCells.INDEX_MASK,
            ],
        )
    }
}

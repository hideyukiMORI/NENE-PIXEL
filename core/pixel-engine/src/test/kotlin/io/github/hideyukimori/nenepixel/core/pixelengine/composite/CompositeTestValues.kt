package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal object CompositeTestValues {
    private const val BITS_PER_BYTE: Int = 8

    fun canvas(
        width: Int,
        height: Int,
    ): CanvasSize = CanvasSize.create(value(CanvasWidth.create(width)), value(CanvasHeight.create(height)))

    fun palette(vararg rgba: Long): PaletteDefinition =
        value(
            PaletteDefinition.create(
                value(Palette.create(rgba.map { PixelColor.fromPackedRgba8888(it.toInt()) })),
                PaletteIndex.first,
            ),
        )

    /** Builds a layer from row-major cells; `null` is an Empty cell. */
    fun layer(
        id: Int,
        size: CanvasSize,
        cells: List<Int?>,
        visibility: LayerVisibility = LayerVisibility.Visible,
    ): Layer = Layer.create(layerId(id), value(LayerName.create("Layer $id")), visibility, snapshot(size, cells))

    fun filledLayer(
        id: Int,
        size: CanvasSize,
        index: Int,
    ): Layer =
        Layer.create(
            layerId(id),
            value(LayerName.create("Layer $id")),
            LayerVisibility.Visible,
            value(PixelSnapshot.createFilled(size, value(PaletteIndex.create(index)))),
        )

    fun layerId(id: Int): LayerId = value(LayerId.create(id))

    fun composited(result: CompositeResult): IntArray =
        when (result) {
            is CompositeResult.Composited -> result.raster.copyPackedRgba8888()
            is CompositeResult.Rejected -> error("Unexpected composite rejection: ${result.rejection}")
        }

    fun rejection(result: CompositeResult): CompositeRejection =
        when (result) {
            is CompositeResult.Composited -> error("Expected composite rejection")
            is CompositeResult.Rejected -> result.rejection
        }

    fun <T> value(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Unexpected fixture rejection: ${result.rejection}")
        }

    private fun snapshot(
        size: CanvasSize,
        cells: List<Int?>,
    ): PixelSnapshot {
        val indices = ByteArray(cells.size) { (cells[it] ?: 0).toByte() }
        val coverage = ByteArray((cells.size + BITS_PER_BYTE - 1) / BITS_PER_BYTE)
        cells.forEachIndexed { pixel, cell ->
            if (cell != null) {
                val byte = pixel / BITS_PER_BYTE
                coverage[byte] = (coverage[byte].toInt() or (1 shl (pixel % BITS_PER_BYTE))).toByte()
            }
        }
        return value(PixelSnapshot.createPackedCells(size, indices, coverage))
    }
}

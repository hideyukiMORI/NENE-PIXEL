package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal object ProjectFormatV3TestValues {
    private const val GOLDEN_ID: String = "00000000000000000000000000000001"

    /** One 128-byte name: 32 code points of U+1F600, four UTF-8 bytes each. */
    val longestName: String = "😀".repeat(32)

    /** layered-v3.hex: 3x1, two layers, the upper one hidden and named with a two-byte code point. */
    val layeredDocument: DocumentState =
        document(
            size = ProjectFormatTestValues.canvas(3, 1),
            revision = 7L,
            colors = intArrayOf(0x11223344, 0xff0000ff.toInt()),
            layers =
                listOf(
                    layer(1, "A", LayerVisibility.Visible, ProjectFormatTestValues.canvas(3, 1), listOf(0, null, 1)),
                    layer(5, "é", LayerVisibility.Hidden, ProjectFormatTestValues.canvas(3, 1), listOf(null, 1, null)),
                ),
        )

    fun maximumLayeredDocument(): DocumentState {
        val size = ProjectFormatTestValues.canvas(256, 256)
        val layers = List(16) { layerIndex -> maximumLayer(size, layerIndex) }
        return document(size, Long.MAX_VALUE, IntArray(256) { index -> index * 0x01010101 }, layers)
    }

    /** Odd layers leave every other cell empty; the top layer uses the largest id. */
    private fun maximumLayer(
        size: CanvasSize,
        layerIndex: Int,
    ): Layer {
        val sparse = layerIndex % 2 == 1
        val cells =
            List(size.pixelCount.toInt()) { pixel ->
                ((pixel * 7 + layerIndex) and 0xff).takeUnless { sparse && pixel % 2 == 1 }
            }
        val visibility = if (layerIndex % 3 == 0) LayerVisibility.Hidden else LayerVisibility.Visible
        val id = if (layerIndex == 15) Int.MAX_VALUE else layerIndex + 1
        return layer(id, longestName, visibility, size, cells)
    }

    fun document(
        size: CanvasSize,
        revision: Long,
        colors: IntArray,
        layers: List<Layer>,
    ): DocumentState {
        check(layers.all { layer -> layer.snapshot.size == size })
        val palette = created(Palette.create(colors.map(PixelColor::fromPackedRgba8888)))
        val definition = created(PaletteDefinition.create(palette, created(PaletteIndex.create(0))))
        return created(
            DocumentState.createLayered(
                created(DocumentId.create(GOLDEN_ID)),
                created(Revision.create(revision)),
                definition,
                layers,
            ),
        )
    }

    /** Builds a layer from cells written as palette slots, with `null` for `Empty`. */
    fun layer(
        id: Int,
        name: String,
        visibility: LayerVisibility,
        size: CanvasSize,
        cells: List<Int?>,
    ): Layer {
        val indices = ByteArray(cells.size) { pixel -> (cells[pixel] ?: 0).toByte() }
        val coverage = ByteArray((cells.size + 7) / 8)
        cells.forEachIndexed { pixel, cell ->
            if (cell != null) {
                coverage[pixel / 8] = (coverage[pixel / 8].toInt() or (1 shl (pixel % 8))).toByte()
            }
        }
        return Layer.create(
            created(LayerId.create(id)),
            created(LayerName.create(name)),
            visibility,
            created(PixelSnapshot.createPackedCells(size, indices, coverage)),
        )
    }

    /** Rewrites the trailing CRC32 so that a mutation reaches the check under test. */
    fun withChecksum(bytes: ByteArray): ByteArray {
        val checksumOffset = bytes.size - Int.SIZE_BYTES
        ProjectFormatBigEndian.writeInt(bytes, checksumOffset, Crc32IsoHdlc.checksum(bytes, checksumOffset).toInt())
        return bytes
    }

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Test domain value was rejected: ${result.rejection}")
        }
}

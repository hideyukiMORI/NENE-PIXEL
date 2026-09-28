package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/**
 * The v3 goldens are assembled by hand from the ADR 0030 table, not copied from the encoder:
 *
 * minimal-v3.hex (62 bytes) = magic 4e454e4550495800, version 0003, width 0001, height 0001,
 * id 00..01 (16 bytes), revision 0 (8 bytes), palette count 0002, default 00,
 * palette 00000000 ff0000ff, layer count 01,
 * layer [id 00000001, flags 01, name length 00, coverage 01, indices 01], CRC32 7b95663f.
 *
 * layered-v3.hex (77 bytes) = the same header with width 0003, revision 7,
 * palette 11223344 ff0000ff, layer count 02,
 * layer [id 00000001, flags 01, name length 01, "A" 41, coverage 05 (bits 0 and 2), indices 00 00 01],
 * layer [id 00000005, flags 00, name length 02, "e acute" c3a9, coverage 02 (bit 1), indices 00 01 00],
 * CRC32 e9f91d2e. Each CRC32 (ISO-HDLC) covers every byte before it.
 */
internal class ProjectFormatV3CodecGoldenTest {
    @Test
    fun `minimal v3 golden is exact and decodes one covered layer`() {
        val golden = ProjectFormatTestValues.golden("minimal-v3.hex")
        val document = ProjectFormatTestValues.v2MinimalDocument

        assertEquals(62, golden.size)
        assertArrayEquals(golden, ProjectFormatCodec.encode(document).copyBytes())
        assertEquals(document, current(ProjectFormatTestValues.carrier(golden)))
    }

    @Test
    fun `layered v3 golden is exact and decodes order names visibility and empty cells`() {
        val golden = ProjectFormatTestValues.golden("layered-v3.hex")

        assertEquals(77, golden.size)
        assertArrayEquals(golden, ProjectFormatCodec.encode(ProjectFormatV3TestValues.layeredDocument).copyBytes())
        val decoded = current(ProjectFormatTestValues.carrier(golden))
        assertEquals(ProjectFormatV3TestValues.layeredDocument, decoded)
        val (bottom, top) = decoded.layers
        assertEquals(listOf(1, 5), decoded.layers.map { layer -> layer.id.value })
        assertEquals("A", bottom.name.value)
        assertEquals("é", top.name.value)
        assertEquals(LayerVisibility.Visible, bottom.visibility)
        assertEquals(LayerVisibility.Hidden, top.visibility)
        assertEquals(listOf(covered(0), PixelCell.Empty, covered(1)), cells(decoded, 0))
        assertEquals(listOf(PixelCell.Empty, covered(1), PixelCell.Empty), cells(decoded, 1))
    }

    @Test
    fun `layered documents round trip exactly`() {
        listOf(
            ProjectFormatTestValues.minimalDocument,
            ProjectFormatTestValues.rectangularDocument,
            ProjectFormatV3TestValues.layeredDocument,
        ).forEach { document ->
            assertEquals(document, current(ProjectFormatCodec.encode(document)))
        }
    }

    @Test
    fun `maximum v3 round trips at the common maximum and one more byte is rejected`() {
        val document = ProjectFormatV3TestValues.maximumLayeredDocument()
        val encoded = ProjectFormatCodec.encode(document)

        assertEquals(1_182_862, ProjectFormatV3Layout.MAX_FILE_BYTE_COUNT)
        assertEquals(ProjectFormatBytes.MAX_FILE_BYTE_COUNT, encoded.byteCount)
        assertEquals(ProjectFormatV3Layout.MAX_FILE_BYTE_COUNT, encoded.byteCount)
        assertEquals(document, current(encoded))

        val oversized = ProjectFormatTestValues.carrier(encoded.copyBytes().copyOf(encoded.byteCount + 1))
        val resource =
            assertInstanceOf(ProjectFormatRejection.ResourceLimitExceeded::class.java, rejected(oversized))
        assertEquals(ProjectFormatBytes.MAX_FILE_BYTE_COUNT + 1, resource.actualByteCount)
        assertEquals(ProjectFormatBytes.MAX_FILE_BYTE_COUNT, resource.maximumByteCount)
    }

    @Test
    fun `one byte after a smaller v3 file is trailing data`() {
        val golden = ProjectFormatTestValues.golden("layered-v3.hex")

        assertEquals(
            ProjectFormatRejection.TrailingData(78, 77),
            rejected(ProjectFormatTestValues.carrier(golden.copyOf(78))),
        )
    }

    @Test
    fun `v2 decodes as one covered layer and re-encodes as v3 with the same cells`() {
        val golden = ProjectFormatTestValues.golden("rectangular-duplicates-v2.hex")
        val fromV2 = current(ProjectFormatTestValues.carrier(golden))
        val layer = fromV2.layers.single()
        assertEquals(LayerId.first(), layer.id)
        assertEquals(LayerName.empty, layer.name)
        assertEquals(LayerVisibility.Visible, layer.visibility)

        val rewritten = ProjectFormatCodec.encode(fromV2)
        assertEquals(ProjectFormatV3Layout.VERSION, ProjectFormatBigEndian.readUnsignedShort(rewritten, 8))
        val fromV3 = current(rewritten)
        assertEquals(fromV2, fromV3)
        val v2Indices = golden.copyOfRange(golden.size - Int.SIZE_BYTES - 6, golden.size - Int.SIZE_BYTES)
        assertEquals(v2Indices.map { index -> covered(index.toInt()) }, cells(fromV3, 0))
    }

    private fun cells(
        document: DocumentState,
        layerIndex: Int,
    ): List<PixelCell> {
        val snapshot = document.layers[layerIndex].snapshot
        return List(snapshot.size.pixelCount.toInt()) { pixel ->
            val position =
                PixelPosition.create(
                    created(PixelX.create(pixel % snapshot.size.width.value)),
                    created(PixelY.create(pixel / snapshot.size.width.value)),
                )
            created(snapshot.cellAt(position))
        }
    }

    private fun covered(index: Int): PixelCell = PixelCell.Covered(created(PaletteIndex.create(index)))

    private fun current(source: ProjectFormatBytes): DocumentState =
        when (val result = ProjectFormatCodec.decode(source)) {
            is ProjectFormatResult.Accepted -> (result.value as DocumentImportSource.Current).document
            is ProjectFormatResult.Rejected -> error("Expected current document, got ${result.rejection}")
        }

    private fun rejected(source: ProjectFormatBytes): ProjectFormatRejection =
        when (val result = ProjectFormatCodec.decode(source)) {
            is ProjectFormatResult.Accepted -> error("Expected rejection")
            is ProjectFormatResult.Rejected -> result.rejection
        }

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Expected a valid domain value: ${result.rejection}")
        }
}

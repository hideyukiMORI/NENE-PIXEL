package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteEntry

internal object ProjectFormatV3Encoder {
    private const val HEX_RADIX: Int = 16
    private const val HEX_PAIR_LENGTH: Int = 2

    fun encode(document: DocumentState): ProjectFormatBytes = createCarrier(encodeBytes(document))

    private fun encodeBytes(document: DocumentState): ByteArray {
        val entries = document.definition.palette.entries()
        val names = document.layers.map { layer -> layer.name.value.encodeToByteArray() }
        val pixelCount = document.size.pixelCount.toInt()
        val layerCountOffset = ProjectFormatV3Layout.layerCountOffset(entries.size)
        val firstLayerOffset = layerCountOffset + ProjectFormatV3Layout.LAYER_COUNT_BYTE_COUNT
        val byteCount =
            firstLayerOffset +
                names.sumOf { name -> ProjectFormatV3Layout.layerByteCount(name.size, pixelCount) } +
                Int.SIZE_BYTES
        val encoded = ByteArray(byteCount)
        writeHeader(encoded, document, entries.size)
        writePalette(encoded, entries)
        encoded[layerCountOffset] = document.layers.size.toByte()
        var offset = firstLayerOffset
        document.layers.forEachIndexed { layerIndex, layer ->
            offset = writeLayer(encoded, offset, layer, names[layerIndex])
        }
        val checksumOffset = byteCount - Int.SIZE_BYTES
        ProjectFormatBigEndian.writeInt(encoded, checksumOffset, Crc32IsoHdlc.checksum(encoded, checksumOffset).toInt())
        return encoded
    }

    private fun createCarrier(encoded: ByteArray): ProjectFormatBytes =
        when (val result = ProjectFormatBytes.create(encoded)) {
            is ProjectFormatResult.Accepted -> result.value
            is ProjectFormatResult.Rejected -> error("Valid v3 encoding exceeded its byte carrier: ${result.rejection}")
        }

    private fun writeHeader(
        destination: ByteArray,
        document: DocumentState,
        paletteEntryCount: Int,
    ) {
        ProjectFormatV2Layout.writeMagic(destination)
        ProjectFormatBigEndian.writeUnsignedShort(
            destination,
            ProjectFormatV2Layout.VERSION_OFFSET,
            ProjectFormatV3Layout.VERSION,
        )
        ProjectFormatBigEndian.writeUnsignedShort(
            destination,
            ProjectFormatV2Layout.WIDTH_OFFSET,
            document.size.width.value,
        )
        ProjectFormatBigEndian.writeUnsignedShort(
            destination,
            ProjectFormatV2Layout.HEIGHT_OFFSET,
            document.size.height.value,
        )
        writeDocumentId(destination, document.id.value)
        ProjectFormatBigEndian.writeLong(destination, ProjectFormatV2Layout.REVISION_OFFSET, document.revision.value)
        ProjectFormatBigEndian.writeUnsignedShort(
            destination,
            ProjectFormatV2Layout.PALETTE_COUNT_OFFSET,
            paletteEntryCount,
        )
        destination[ProjectFormatV2Layout.DEFAULT_INDEX_OFFSET] =
            document.definition.defaultIndex.value
                .toByte()
    }

    private fun writePalette(
        destination: ByteArray,
        entries: List<PaletteEntry>,
    ) {
        entries.forEachIndexed { entryIndex, entry ->
            ProjectFormatBigEndian.writeInt(
                destination,
                ProjectFormatV2Layout.PALETTE_OFFSET + entryIndex * ProjectFormatV2Layout.PALETTE_ENTRY_BYTE_COUNT,
                entry.color.toPackedRgba8888(),
            )
        }
    }

    private fun writeLayer(
        destination: ByteArray,
        offset: Int,
        layer: Layer,
        name: ByteArray,
    ): Int {
        ProjectFormatBigEndian.writeInt(destination, offset, layer.id.value)
        destination[offset + ProjectFormatV3Layout.FLAGS_OFFSET] =
            if (layer.visibility == LayerVisibility.Visible) ProjectFormatV3Layout.VISIBLE_FLAG.toByte() else 0
        destination[offset + ProjectFormatV3Layout.NAME_LENGTH_OFFSET] = name.size.toByte()
        name.copyInto(destination, offset + ProjectFormatV3Layout.NAME_OFFSET)
        val coverageOffset = offset + ProjectFormatV3Layout.NAME_OFFSET + name.size
        val coverage = layer.snapshot.copyCoverage()
        coverage.copyInto(destination, coverageOffset)
        val indices = layer.snapshot.copyPackedIndices()
        indices.copyInto(destination, coverageOffset + coverage.size)
        return coverageOffset + coverage.size + indices.size
    }

    private fun writeDocumentId(
        destination: ByteArray,
        documentId: String,
    ) {
        repeat(ProjectFormatV2Layout.DOCUMENT_ID_BYTE_COUNT) { byteIndex ->
            val characterIndex = byteIndex * HEX_PAIR_LENGTH
            destination[ProjectFormatV2Layout.DOCUMENT_ID_OFFSET + byteIndex] =
                documentId.substring(characterIndex, characterIndex + HEX_PAIR_LENGTH).toInt(HEX_RADIX).toByte()
        }
    }
}

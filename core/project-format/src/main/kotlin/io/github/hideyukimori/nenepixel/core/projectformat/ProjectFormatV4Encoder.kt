package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.Frame
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteEntry

internal object ProjectFormatV4Encoder {
    private const val HEX_RADIX: Int = 16
    private const val HEX_PAIR_LENGTH: Int = 2

    fun encode(document: DocumentState): ProjectFormatBytes = createCarrier(encodeBytes(document))

    private fun encodeBytes(document: DocumentState): ByteArray {
        val entries = document.definition.palette.entries()
        val layers = document.frames.flatMap { it.layers }
        val names = layers.map { it.name.value.encodeToByteArray() }
        val pixelCount = document.size.pixelCount.toInt()
        val countOffset = ProjectFormatV3Layout.layerCountOffset(entries.size)
        val byteCount =
            countOffset.toLong() + 1 +
                document.frames.size.toLong() * ProjectFormatV4Layout.FRAME_FIXED_BYTE_COUNT +
                names.sumOf { ProjectFormatV3Layout.layerByteCount(it.size, pixelCount).toLong() } + Int.SIZE_BYTES
        check(byteCount <= ProjectFormatV4Layout.MAX_FILE_BYTE_COUNT)
        val encoded = ByteArray(byteCount.toInt())
        writeHeader(encoded, document, entries.size)
        writePalette(encoded, entries)
        encoded[countOffset] = document.frames.size.toByte()
        var offset = countOffset + 1
        val nameIterator = names.iterator()
        document.frames.forEach { frame -> offset = writeFrame(encoded, offset, frame, nameIterator) }
        ProjectFormatBigEndian.writeInt(encoded, offset, Crc32IsoHdlc.checksum(encoded, offset).toInt())
        return encoded
    }

    private fun writeFrame(
        destination: ByteArray,
        start: Int,
        frame: Frame,
        names: Iterator<ByteArray>,
    ): Int {
        ProjectFormatBigEndian.writeInt(destination, start, frame.id.value)
        ProjectFormatBigEndian.writeInt(
            destination,
            start + ProjectFormatV4Layout.DURATION_OFFSET,
            frame.duration.value,
        )
        destination[start + ProjectFormatV4Layout.LAYER_COUNT_OFFSET] = frame.layers.size.toByte()
        var offset = start + ProjectFormatV4Layout.FRAME_FIXED_BYTE_COUNT
        frame.layers.forEach { layer -> offset = writeLayer(destination, offset, layer, names.next()) }
        return offset
    }

    private fun createCarrier(encoded: ByteArray): ProjectFormatBytes =
        when (val result = ProjectFormatBytes.create(encoded)) {
            is ProjectFormatResult.Accepted -> result.value
            is ProjectFormatResult.Rejected -> error("Valid v4 encoding exceeded its byte carrier: ${result.rejection}")
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
            ProjectFormatV4Layout.VERSION,
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

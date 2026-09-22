package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

internal object ProjectFormatV2Encoder {
    private const val HEX_RADIX: Int = 16
    private const val HIGH_NIBBLE_SHIFT: Int = 4

    fun encode(document: DocumentState): ProjectFormatBytes = createCarrier(encodeBytes(document))

    private fun encodeBytes(document: DocumentState): ByteArray {
        val entries = document.definition.palette.entries()
        val indices = document.snapshot.copyPackedIndices()
        val byteCount = ProjectFormatV2Layout.expectedByteCount(indices.size.toLong(), entries.size).toInt()
        val encoded = ByteArray(byteCount)
        writeHeader(encoded, document, entries.size)
        writePalette(encoded, entries)
        indices.copyInto(encoded, ProjectFormatV2Layout.PALETTE_OFFSET + entries.size * Int.SIZE_BYTES)
        val checksumOffset = byteCount - Int.SIZE_BYTES
        ProjectFormatBigEndian.writeInt(encoded, checksumOffset, Crc32IsoHdlc.checksum(encoded, checksumOffset).toInt())
        return encoded
    }

    private fun createCarrier(encoded: ByteArray): ProjectFormatBytes =
        when (val result = ProjectFormatBytes.create(encoded)) {
            is ProjectFormatResult.Accepted -> result.value
            is ProjectFormatResult.Rejected -> error("Valid v2 encoding exceeded its byte carrier: ${result.rejection}")
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
            ProjectFormatV2Layout.VERSION,
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
        entries: List<io.github.hideyukimori.nenepixel.core.domain.palette.PaletteEntry>,
    ) {
        entries.forEachIndexed { entryIndex, entry ->
            ProjectFormatBigEndian.writeInt(
                destination,
                ProjectFormatV2Layout.PALETTE_OFFSET + entryIndex * Int.SIZE_BYTES,
                entry.color.toPackedRgba8888(),
            )
        }
    }

    private fun writeDocumentId(
        destination: ByteArray,
        documentId: String,
    ) {
        repeat(ProjectFormatV2Layout.DOCUMENT_ID_BYTE_COUNT) { byteIndex ->
            val characterIndex = byteIndex * 2
            destination[ProjectFormatV2Layout.DOCUMENT_ID_OFFSET + byteIndex] =
                (
                    (
                        documentId[characterIndex].digitToInt(
                            HEX_RADIX,
                        ) shl HIGH_NIBBLE_SHIFT
                    ) or documentId[characterIndex + 1].digitToInt(HEX_RADIX)
                ).toByte()
        }
    }
}

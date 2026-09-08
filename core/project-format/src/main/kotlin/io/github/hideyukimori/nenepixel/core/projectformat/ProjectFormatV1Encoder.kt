package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

internal object ProjectFormatV1Encoder {
    private const val HEX_RADIX: Int = 16
    private const val HIGH_NIBBLE_SHIFT: Int = 4

    fun encode(document: DocumentState): ProjectFormatBytes {
        val byteCount = ProjectFormatV1Layout.expectedByteCount(document.size.pixelCount).toInt()
        val encoded = ByteArray(byteCount)
        writeHeader(encoded, document)
        writePixels(encoded, document.snapshot.copyPackedRgba8888())
        val checksumOffset = byteCount - Int.SIZE_BYTES
        val checksum = Crc32IsoHdlc.checksum(encoded, checksumOffset)
        ProjectFormatBigEndian.writeInt(encoded, checksumOffset, checksum.toInt())
        return createCarrier(encoded)
    }

    private fun writeHeader(
        destination: ByteArray,
        document: DocumentState,
    ) {
        ProjectFormatV1Layout.writeMagic(destination)
        ProjectFormatBigEndian.writeUnsignedShort(
            destination,
            ProjectFormatV1Layout.VERSION_OFFSET,
            ProjectFormatV1Layout.VERSION,
        )
        ProjectFormatBigEndian.writeUnsignedShort(
            destination,
            ProjectFormatV1Layout.WIDTH_OFFSET,
            document.size.width.value,
        )
        ProjectFormatBigEndian.writeUnsignedShort(
            destination,
            ProjectFormatV1Layout.HEIGHT_OFFSET,
            document.size.height.value,
        )
        writeDocumentId(destination, document.id.value)
        ProjectFormatBigEndian.writeLong(destination, ProjectFormatV1Layout.REVISION_OFFSET, document.revision.value)
    }

    private fun writeDocumentId(
        destination: ByteArray,
        documentId: String,
    ) {
        repeat(ProjectFormatV1Layout.DOCUMENT_ID_BYTE_COUNT) { byteIndex ->
            val characterIndex = byteIndex * 2
            val high = documentId[characterIndex].digitToInt(HEX_RADIX)
            val low = documentId[characterIndex + 1].digitToInt(HEX_RADIX)
            destination[ProjectFormatV1Layout.DOCUMENT_ID_OFFSET + byteIndex] =
                ((high shl HIGH_NIBBLE_SHIFT) or low).toByte()
        }
    }

    private fun writePixels(
        destination: ByteArray,
        packedPixels: IntArray,
    ) {
        packedPixels.forEachIndexed { pixelIndex, pixel ->
            val offset = ProjectFormatV1Layout.PIXEL_OFFSET + pixelIndex * Int.SIZE_BYTES
            ProjectFormatBigEndian.writeInt(destination, offset, pixel)
        }
    }

    private fun createCarrier(encoded: ByteArray): ProjectFormatBytes =
        when (val result = ProjectFormatBytes.create(encoded)) {
            is ProjectFormatResult.Accepted -> result.value
            is ProjectFormatResult.Rejected -> error("Valid v1 encoding exceeded its byte carrier: ${result.rejection}")
        }
}

package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal class ProjectFormatV2Decoder {
    fun decode(source: ProjectFormatBytes): ProjectFormatResult<DocumentImportSource> =
        validateEnvelope(source)
            .andThen { fields -> validateBody(source, fields) }
            .andThen { fields -> mapDocument(source, fields) }

    private fun validateEnvelope(source: ProjectFormatBytes): ProjectFormatResult<ValidatedProjectFormatV2> =
        when {
            source.byteCount > ProjectFormatV2Layout.MAX_FILE_BYTE_COUNT -> {
                rejected(
                    ProjectFormatRejection.ResourceLimitExceeded(
                        source.byteCount,
                        ProjectFormatV2Layout.MAX_FILE_BYTE_COUNT,
                    ),
                )
            }

            source.byteCount < ProjectFormatV2Layout.FIXED_BYTE_COUNT -> {
                rejected(
                    ProjectFormatRejection.Truncated(
                        source.byteCount,
                        ProjectFormatV2Layout.FIXED_BYTE_COUNT,
                    ),
                )
            }

            else -> {
                decodeFixedFields(source)
            }
        }

    private fun validateBody(
        source: ProjectFormatBytes,
        fields: ValidatedProjectFormatV2,
    ): ProjectFormatResult<ValidatedProjectFormatV2> {
        val expectedByteCount =
            ProjectFormatV2Layout
                .expectedByteCount(fields.size.pixelCount, fields.paletteEntryCount)
                .toInt()
        return when {
            source.byteCount < expectedByteCount -> {
                rejected(ProjectFormatRejection.Truncated(source.byteCount, expectedByteCount))
            }

            source.byteCount > expectedByteCount -> {
                rejected(ProjectFormatRejection.TrailingData(source.byteCount, expectedByteCount))
            }

            else -> {
                verifyChecksum(source, fields, expectedByteCount)
            }
        }
    }

    private fun verifyChecksum(
        source: ProjectFormatBytes,
        fields: ValidatedProjectFormatV2,
        expectedByteCount: Int,
    ): ProjectFormatResult<ValidatedProjectFormatV2> {
        val checksumOffset = expectedByteCount - Int.SIZE_BYTES
        val computed = Crc32IsoHdlc.checksum(source, checksumOffset)
        val stored = ProjectFormatBigEndian.readInt(source, checksumOffset).toUInt()
        return if (computed == stored) {
            accepted(fields)
        } else {
            rejected(ProjectFormatRejection.ChecksumMismatch(computed, stored))
        }
    }

    private fun mapDocument(
        source: ProjectFormatBytes,
        fields: ValidatedProjectFormatV2,
    ): ProjectFormatResult<DocumentImportSource> =
        createPalette(source, fields).andThen { palette ->
            when (val result = PaletteDefinition.create(palette, fields.defaultIndex)) {
                is DomainValueResult.Created -> {
                    validateIndices(source, fields, palette).andThen {
                        createDocument(source, fields, result.value)
                    }
                }

                is DomainValueResult.Rejected -> {
                    error("Validated v2 definition failed domain mapping: ${result.rejection}")
                }
            }
        }

    private fun createPalette(
        source: ProjectFormatBytes,
        fields: ValidatedProjectFormatV2,
    ): ProjectFormatResult<Palette> {
        val colors =
            List(fields.paletteEntryCount) { entry ->
                PixelColor.fromPackedRgba8888(
                    ProjectFormatBigEndian.readInt(
                        source,
                        ProjectFormatV2Layout.PALETTE_OFFSET + entry * Int.SIZE_BYTES,
                    ),
                )
            }
        return when (val result = Palette.create(colors)) {
            is DomainValueResult.Created -> accepted(result.value)
            is DomainValueResult.Rejected -> error("Validated v2 palette failed domain mapping: ${result.rejection}")
        }
    }

    private fun validateIndices(
        source: ProjectFormatBytes,
        fields: ValidatedProjectFormatV2,
        palette: Palette,
    ): ProjectFormatResult<Unit> {
        var firstRejection: ProjectFormatRejection? = null
        for (pixelIndex in 0 until fields.size.pixelCount.toInt()) {
            val index = readIndex(source, fields, pixelIndex)
            val position = position(fields.size, pixelIndex)
            if (firstRejection == null && palette.entryAt(index) is DomainValueResult.Rejected) {
                firstRejection =
                    ProjectFormatRejection.PixelIndexOutsidePalette(
                        position,
                        index,
                        fields.paletteEntryCount,
                    )
            }
        }
        return firstRejection?.let(::rejected) ?: accepted(Unit)
    }

    private fun createDocument(
        source: ProjectFormatBytes,
        fields: ValidatedProjectFormatV2,
        definition: PaletteDefinition,
    ): ProjectFormatResult<DocumentImportSource> {
        val indices = ByteArray(fields.size.pixelCount.toInt())
        for (pixelIndex in indices.indices) {
            indices[pixelIndex] = readIndex(source, fields, pixelIndex).value.toByte()
        }
        val snapshot =
            when (val result = PixelSnapshot.createPackedIndices(fields.size, fields.revision, indices)) {
                is DomainValueResult.Created -> {
                    result.value
                }

                is DomainValueResult.Rejected -> {
                    error("Validated v2 indices failed snapshot mapping: ${result.rejection}")
                }
            }
        return when (val result = DocumentState.create(fields.id, definition, snapshot)) {
            is DomainValueResult.Created -> accepted(DocumentImportSource.Current(result.value))
            is DomainValueResult.Rejected -> error("Validated v2 document failed domain mapping: ${result.rejection}")
        }
    }

    private fun decodeFixedFields(source: ProjectFormatBytes): ProjectFormatResult<ValidatedProjectFormatV2> =
        decodeCanvas(source).andThen { size ->
            decodeRevision(source).andThen { revision ->
                decodePaletteHeader(source).andThen { header ->
                    accepted(
                        ValidatedProjectFormatV2(
                            decodeDocumentId(source),
                            size,
                            revision,
                            header.count,
                            header.defaultIndex,
                        ),
                    )
                }
            }
        }

    private fun decodeCanvas(source: ProjectFormatBytes): ProjectFormatResult<CanvasSize> {
        val widthResult =
            CanvasWidth.create(
                ProjectFormatBigEndian.readUnsignedShort(source, ProjectFormatV2Layout.WIDTH_OFFSET),
            )
        val heightResult =
            CanvasHeight.create(
                ProjectFormatBigEndian.readUnsignedShort(source, ProjectFormatV2Layout.HEIGHT_OFFSET),
            )
        return when {
            widthResult is DomainValueResult.Created && heightResult is DomainValueResult.Created -> {
                accepted(CanvasSize.create(widthResult.value, heightResult.value))
            }

            else -> {
                rejected(ProjectFormatRejection.InvalidCanvas)
            }
        }
    }

    private fun decodePaletteHeader(source: ProjectFormatBytes): ProjectFormatResult<PaletteHeader> {
        val count =
            ProjectFormatBigEndian.readUnsignedShort(
                source,
                ProjectFormatV2Layout.PALETTE_COUNT_OFFSET,
            )
        if (count !in ProjectFormatV2Layout.MIN_PALETTE_ENTRY_COUNT..ProjectFormatV2Layout.MAX_PALETTE_ENTRY_COUNT) {
            return rejected(
                ProjectFormatRejection.InvalidPaletteEntryCount(
                    count,
                    ProjectFormatV2Layout.MIN_PALETTE_ENTRY_COUNT,
                    ProjectFormatV2Layout.MAX_PALETTE_ENTRY_COUNT,
                ),
            )
        }
        val defaultIndex =
            when (val result = PaletteIndex.create(source.unsignedByteAt(ProjectFormatV2Layout.DEFAULT_INDEX_OFFSET))) {
                is DomainValueResult.Created -> result.value
                is DomainValueResult.Rejected -> error("Unsigned v2 default index was rejected: ${result.rejection}")
            }
        return if (defaultIndex.value >= count) {
            rejected(ProjectFormatRejection.DefaultIndexOutsidePalette(defaultIndex, count))
        } else {
            accepted(PaletteHeader(count, defaultIndex))
        }
    }
}

private data class ValidatedProjectFormatV2(
    val id: DocumentId,
    val size: CanvasSize,
    val revision: Revision,
    val paletteEntryCount: Int,
    val defaultIndex: PaletteIndex,
)

private data class PaletteHeader(
    val count: Int,
    val defaultIndex: PaletteIndex,
)

private const val HEXADECIMAL: String = "0123456789abcdef"
private const val NIBBLE_SHIFT: Int = 4
private const val NIBBLE_MASK: Int = 0x0f
private const val U8_MASK: Int = 0xff

private fun readIndex(
    source: ProjectFormatBytes,
    fields: ValidatedProjectFormatV2,
    pixelIndex: Int,
): PaletteIndex =
    when (
        val result =
            PaletteIndex.create(
                source.unsignedByteAt(
                    ProjectFormatV2Layout.PALETTE_OFFSET + fields.paletteEntryCount * Int.SIZE_BYTES + pixelIndex,
                ),
            )
    ) {
        is DomainValueResult.Created -> result.value
        is DomainValueResult.Rejected -> error("Unsigned v2 palette index was rejected: ${result.rejection}")
    }

private fun decodeRevision(source: ProjectFormatBytes): ProjectFormatResult<Revision> =
    when (
        val result =
            Revision.create(
                ProjectFormatBigEndian.readLong(source, ProjectFormatV2Layout.REVISION_OFFSET),
            )
    ) {
        is DomainValueResult.Created -> accepted(result.value)
        is DomainValueResult.Rejected -> rejected(ProjectFormatRejection.InvalidRevision)
    }

private fun decodeDocumentId(source: ProjectFormatBytes): DocumentId {
    val value =
        buildString(ProjectFormatV2Layout.DOCUMENT_ID_BYTE_COUNT * 2) {
            repeat(ProjectFormatV2Layout.DOCUMENT_ID_BYTE_COUNT) { byteIndex ->
                val byte = source.unsignedByteAt(ProjectFormatV2Layout.DOCUMENT_ID_OFFSET + byteIndex)
                append(HEXADECIMAL[byte ushr NIBBLE_SHIFT])
                append(HEXADECIMAL[byte and NIBBLE_MASK])
            }
        }
    return when (val result = DocumentId.create(value)) {
        is DomainValueResult.Created -> {
            result.value
        }

        is DomainValueResult.Rejected -> {
            error("Sixteen identity bytes failed total hexadecimal mapping: ${result.rejection}")
        }
    }
}

private fun position(
    size: CanvasSize,
    pixelIndex: Int,
): PixelPosition {
    val x = created(PixelX.create(pixelIndex % size.width.value))
    val y = created(PixelY.create(pixelIndex / size.width.value))
    return PixelPosition.create(x, y)
}

private fun ProjectFormatBytes.unsignedByteAt(index: Int): Int = byteAt(index).toInt() and U8_MASK

private fun <T> created(result: DomainValueResult<T>): T =
    when (result) {
        is DomainValueResult.Created -> result.value
        is DomainValueResult.Rejected -> error("Expected a valid domain value: ${result.rejection}")
    }

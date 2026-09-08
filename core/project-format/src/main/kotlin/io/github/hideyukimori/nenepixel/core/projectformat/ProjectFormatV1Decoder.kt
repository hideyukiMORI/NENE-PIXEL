package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal class ProjectFormatV1Decoder(
    private val mapper: ProjectFormatV1DomainMapper = CanonicalProjectFormatV1DomainMapper,
) {
    fun decode(source: ProjectFormatBytes): ProjectFormatResult<DocumentState> =
        when (val validated = validate(source)) {
            is ProjectFormatResult.Accepted -> accepted(mapper.map(source, validated.value))
            is ProjectFormatResult.Rejected -> validated
        }

    private fun validate(source: ProjectFormatBytes): ProjectFormatResult<ValidatedProjectFormatV1> {
        val byteRejection = validateByteBounds(source)
        return if (byteRejection != null) {
            rejected(byteRejection)
        } else {
            validateMagic(source)
        }
    }

    private fun validateMagic(source: ProjectFormatBytes): ProjectFormatResult<ValidatedProjectFormatV1> =
        if (ProjectFormatV1Layout.hasMagic(source)) {
            validateVersionAndFields(source)
        } else {
            rejected(ProjectFormatRejection.InvalidMagic)
        }

    private fun validateVersionAndFields(source: ProjectFormatBytes): ProjectFormatResult<ValidatedProjectFormatV1> {
        val versionRejection = validateVersion(source)
        if (versionRejection != null) return rejected(versionRejection)
        return when (val fixedFields = ProjectFormatV1FixedFieldsDecoder.decode(source)) {
            is ProjectFormatResult.Accepted -> validateLengthAndChecksum(source, fixedFields.value)
            is ProjectFormatResult.Rejected -> fixedFields
        }
    }

    private fun validateByteBounds(source: ProjectFormatBytes): ProjectFormatRejection? =
        when {
            source.byteCount > ProjectFormatBytes.MAX_FILE_BYTE_COUNT -> {
                ProjectFormatRejection.ResourceLimitExceeded(
                    source.byteCount,
                    ProjectFormatBytes.MAX_FILE_BYTE_COUNT,
                )
            }

            source.byteCount < ProjectFormatV1Layout.PIXEL_OFFSET -> {
                ProjectFormatRejection.Truncated(source.byteCount, ProjectFormatV1Layout.PIXEL_OFFSET)
            }

            else -> {
                null
            }
        }

    private fun validateVersion(source: ProjectFormatBytes): ProjectFormatRejection.UnsupportedVersion? {
        val wireValue =
            ProjectFormatBigEndian.readUnsignedShort(source, ProjectFormatV1Layout.VERSION_OFFSET)
        return if (wireValue == ProjectFormatV1Layout.VERSION) {
            null
        } else {
            ProjectFormatRejection.UnsupportedVersion(ProjectFormatVersion.fromWireValue(wireValue))
        }
    }

    private fun validateLengthAndChecksum(
        source: ProjectFormatBytes,
        fields: ValidatedProjectFormatV1,
    ): ProjectFormatResult<ValidatedProjectFormatV1> {
        val expectedByteCount = ProjectFormatV1Layout.expectedByteCount(fields.pixelCount.toLong()).toInt()
        return when {
            source.byteCount < expectedByteCount -> {
                rejected(ProjectFormatRejection.Truncated(source.byteCount, expectedByteCount))
            }

            source.byteCount > expectedByteCount -> {
                rejected(ProjectFormatRejection.TrailingData(source.byteCount, expectedByteCount))
            }

            else -> {
                validateChecksum(source, fields, expectedByteCount)
            }
        }
    }

    private fun validateChecksum(
        source: ProjectFormatBytes,
        fields: ValidatedProjectFormatV1,
        expectedByteCount: Int,
    ): ProjectFormatResult<ValidatedProjectFormatV1> {
        val checksumOffset = expectedByteCount - Int.SIZE_BYTES
        val computed = Crc32IsoHdlc.checksum(source, checksumOffset)
        val stored = ProjectFormatBigEndian.readInt(source, checksumOffset).toUInt()
        return if (computed == stored) {
            accepted(fields)
        } else {
            rejected(ProjectFormatRejection.ChecksumMismatch(computed, stored))
        }
    }
}

internal object ProjectFormatV1FixedFieldsDecoder {
    private const val BITS_PER_HEXADECIMAL_DIGIT = 4
    private const val UNSIGNED_BYTE_MASK = 0xff
    private const val LOW_NIBBLE_MASK = 0x0f
    private const val HEXADECIMAL = "0123456789abcdef"

    fun decode(source: ProjectFormatBytes): ProjectFormatResult<ValidatedProjectFormatV1> =
        when (val size = decodeCanvas(source)) {
            is ProjectFormatResult.Accepted -> decode(source, size.value)
            is ProjectFormatResult.Rejected -> size
        }

    private fun decode(
        source: ProjectFormatBytes,
        size: CanvasSize,
    ): ProjectFormatResult<ValidatedProjectFormatV1> =
        when (val revision = decodeRevision(source)) {
            is ProjectFormatResult.Accepted -> {
                accepted(
                    ValidatedProjectFormatV1(
                        id = decodeDocumentId(source),
                        size = size,
                        revision = revision.value,
                        pixelCount = size.pixelCount.toInt(),
                    ),
                )
            }

            is ProjectFormatResult.Rejected -> {
                revision
            }
        }

    private fun decodeCanvas(source: ProjectFormatBytes): ProjectFormatResult<CanvasSize> {
        val widthValue = ProjectFormatBigEndian.readUnsignedShort(source, ProjectFormatV1Layout.WIDTH_OFFSET)
        val heightValue = ProjectFormatBigEndian.readUnsignedShort(source, ProjectFormatV1Layout.HEIGHT_OFFSET)
        return when (val width = CanvasWidth.create(widthValue)) {
            is DomainValueResult.Created -> decodeCanvas(width.value, heightValue)
            is DomainValueResult.Rejected -> rejected(ProjectFormatRejection.InvalidCanvas)
        }
    }

    private fun decodeCanvas(
        width: CanvasWidth,
        heightValue: Int,
    ): ProjectFormatResult<CanvasSize> =
        when (val height = CanvasHeight.create(heightValue)) {
            is DomainValueResult.Created -> accepted(CanvasSize.create(width, height.value))
            is DomainValueResult.Rejected -> rejected(ProjectFormatRejection.InvalidCanvas)
        }

    private fun decodeRevision(source: ProjectFormatBytes): ProjectFormatResult<Revision> {
        val wireValue = ProjectFormatBigEndian.readLong(source, ProjectFormatV1Layout.REVISION_OFFSET)
        return when (val revision = Revision.create(wireValue)) {
            is DomainValueResult.Created -> accepted(revision.value)
            is DomainValueResult.Rejected -> rejected(ProjectFormatRejection.InvalidRevision)
        }
    }

    private fun decodeDocumentId(source: ProjectFormatBytes): DocumentId {
        val value =
            buildString(ProjectFormatV1Layout.DOCUMENT_ID_BYTE_COUNT * 2) {
                repeat(ProjectFormatV1Layout.DOCUMENT_ID_BYTE_COUNT) { byteIndex ->
                    val byte =
                        source.byteAt(ProjectFormatV1Layout.DOCUMENT_ID_OFFSET + byteIndex).toInt() and
                            UNSIGNED_BYTE_MASK
                    append(HEXADECIMAL[byte ushr BITS_PER_HEXADECIMAL_DIGIT])
                    append(HEXADECIMAL[byte and LOW_NIBBLE_MASK])
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
}

internal data class ValidatedProjectFormatV1(
    val id: DocumentId,
    val size: CanvasSize,
    val revision: Revision,
    val pixelCount: Int,
)

internal fun interface ProjectFormatV1DomainMapper {
    fun map(
        source: ProjectFormatBytes,
        fields: ValidatedProjectFormatV1,
    ): DocumentState
}

internal object CanonicalProjectFormatV1DomainMapper : ProjectFormatV1DomainMapper {
    override fun map(
        source: ProjectFormatBytes,
        fields: ValidatedProjectFormatV1,
    ): DocumentState {
        val pixels =
            List(fields.pixelCount) { pixelIndex ->
                val offset = ProjectFormatV1Layout.PIXEL_OFFSET + pixelIndex * Int.SIZE_BYTES
                PixelColor.fromPackedRgba8888(ProjectFormatBigEndian.readInt(source, offset))
            }
        val snapshot =
            when (val result = PixelSnapshot.create(fields.size, fields.revision, pixels)) {
                is DomainValueResult.Created -> {
                    result.value
                }

                is DomainValueResult.Rejected -> {
                    error("Validated v1 pixels failed snapshot mapping: ${result.rejection}")
                }
            }
        return DocumentState.create(fields.id, snapshot)
    }
}

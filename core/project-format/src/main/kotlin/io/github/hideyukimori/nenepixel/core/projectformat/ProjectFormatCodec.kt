package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource

public object ProjectFormatCodec {
    private val v1Decoder: ProjectFormatV1Decoder = ProjectFormatV1Decoder()
    private val v2Decoder: ProjectFormatV2Decoder = ProjectFormatV2Decoder()

    public fun encode(document: DocumentState): ProjectFormatBytes = ProjectFormatV2Encoder.encode(document)

    public fun decode(source: ProjectFormatBytes): ProjectFormatResult<DocumentImportSource> =
        when {
            source.byteCount > ProjectFormatBytes.MAX_FILE_BYTE_COUNT -> {
                rejected(
                    ProjectFormatRejection.ResourceLimitExceeded(
                        actualByteCount = source.byteCount,
                        maximumByteCount = ProjectFormatBytes.MAX_FILE_BYTE_COUNT,
                    ),
                )
            }

            source.byteCount < ProjectFormatV1Layout.PIXEL_OFFSET -> {
                rejected(ProjectFormatRejection.Truncated(source.byteCount, ProjectFormatV1Layout.PIXEL_OFFSET))
            }

            !ProjectFormatV1Layout.hasMagic(source) -> {
                rejected(ProjectFormatRejection.InvalidMagic)
            }

            else -> {
                when (
                    val version =
                        ProjectFormatBigEndian.readUnsignedShort(
                            source,
                            ProjectFormatV1Layout.VERSION_OFFSET,
                        )
                ) {
                    ProjectFormatV1Layout.VERSION -> {
                        v1Decoder.decode(source)
                    }

                    ProjectFormatV2Layout.VERSION -> {
                        v2Decoder.decode(source)
                    }

                    else -> {
                        rejected(
                            ProjectFormatRejection.UnsupportedVersion(ProjectFormatVersion.fromWireValue(version)),
                        )
                    }
                }
            }
        }

    public fun encodeLegacySource(source: LegacyRgbaSource): ProjectFormatBytes = ProjectFormatV1Encoder.encode(source)
}

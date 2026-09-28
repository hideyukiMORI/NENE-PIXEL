package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal class ProjectFormatV3Decoder {
    fun decode(source: ProjectFormatBytes): ProjectFormatResult<DocumentImportSource> =
        validateEnvelope(source)
            .andThen { ProjectFormatV3HeaderDecoder.decode(source) }
            .andThen { header ->
                ProjectFormatV3Structure
                    .scan(source, header)
                    .andThen { spans -> ProjectFormatV3LayerDecoder(source, header).decode(spans) }
                    .andThen { layers -> createDocument(header, layers) }
            }

    private fun validateEnvelope(source: ProjectFormatBytes): ProjectFormatResult<Unit> =
        when {
            source.byteCount > ProjectFormatV3Layout.MAX_FILE_BYTE_COUNT -> {
                rejected(
                    ProjectFormatRejection.ResourceLimitExceeded(
                        source.byteCount,
                        ProjectFormatV3Layout.MAX_FILE_BYTE_COUNT,
                    ),
                )
            }

            source.byteCount < ProjectFormatV3Layout.FIXED_BYTE_COUNT -> {
                rejected(ProjectFormatRejection.Truncated(source.byteCount, ProjectFormatV3Layout.FIXED_BYTE_COUNT))
            }

            else -> {
                accepted(Unit)
            }
        }

    private fun createDocument(
        header: ProjectFormatV3Header,
        layers: List<Layer>,
    ): ProjectFormatResult<DocumentImportSource> =
        when (val result = DocumentState.createLayered(header.id, header.revision, header.definition, layers)) {
            is DomainValueResult.Created -> accepted(DocumentImportSource.Current(result.value))
            is DomainValueResult.Rejected -> error("Validated v3 document failed domain mapping: ${result.rejection}")
        }
}

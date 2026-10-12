package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.Frame
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal class ProjectFormatV4Decoder(
    private val mapper: ProjectFormatV4SnapshotMapper = CanonicalProjectFormatV4SnapshotMapper,
) {
    fun decode(source: ProjectFormatBytes): ProjectFormatResult<DocumentImportSource> =
        envelope(source).andThen {
            ProjectFormatIndexedHeaderDecoder.decode(source).andThen { header ->
                ProjectFormatV4Structure(source, header).scan().andThen { spans ->
                    val definition =
                        ProjectFormatIndexedHeaderDecoder.createDefinition(
                            source,
                            header.palette.defaultIndex,
                        )
                    ProjectFormatV4LayerValidation(source, header).validate(spans).andThen { frames ->
                        val mapped = frames.map { mapFrame(source, header, it) }
                        accepted(
                            DocumentImportSource.Current(
                                required(DocumentState.create(header.id, header.revision, definition, mapped)),
                            ),
                        )
                    }
                }
            }
        }

    private fun envelope(source: ProjectFormatBytes): ProjectFormatResult<Unit> =
        when {
            source.byteCount > ProjectFormatV4Layout.MAX_FILE_BYTE_COUNT -> {
                rejected(
                    ProjectFormatRejection.ResourceLimitExceeded(
                        source.byteCount,
                        ProjectFormatV4Layout.MAX_FILE_BYTE_COUNT,
                    ),
                )
            }

            source.byteCount < ProjectFormatV4Layout.FIXED_BYTE_COUNT -> {
                rejected(ProjectFormatRejection.Truncated(source.byteCount, ProjectFormatV4Layout.FIXED_BYTE_COUNT))
            }

            else -> {
                accepted(Unit)
            }
        }

    private fun mapFrame(
        source: ProjectFormatBytes,
        header: ProjectFormatIndexedHeader,
        admitted: ProjectFormatV4ValidatedFrame,
    ): Frame {
        val layers =
            admitted.layers.map { layer ->
                Layer.create(layer.span.id, layer.name, layer.span.visibility, mapper.map(source, header.size, layer))
            }
        return required(Frame.create(admitted.span.id, admitted.span.duration, layers))
    }

    private fun <T> required(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Admitted v4 document rejected: ${result.rejection}")
        }
}

package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition

internal class ProjectFormatV3Header(
    val id: DocumentId,
    val size: CanvasSize,
    val revision: Revision,
    val definition: PaletteDefinition,
) {
    val paletteEntryCount: Int
        get() = definition.palette.entryCount
}

/** V3 keeps palette materialization at its original pre-structure stage. */
internal object ProjectFormatV3HeaderDecoder {
    fun decode(source: ProjectFormatBytes): ProjectFormatResult<ProjectFormatV3Header> =
        ProjectFormatIndexedHeaderDecoder.decode(source).andThen { header ->
            accepted(
                ProjectFormatV3Header(
                    header.id,
                    header.size,
                    header.revision,
                    ProjectFormatIndexedHeaderDecoder.createDefinition(source, header.palette.defaultIndex),
                ),
            )
        }
}

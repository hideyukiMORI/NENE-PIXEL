package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

internal class FakePngExportPort : PngExportPort {
    val documents = mutableListOf<DocumentState>()
    var handler: suspend (DocumentState) -> PngExportOutcome = { PngExportOutcome.Exported }

    override suspend fun export(document: DocumentState): PngExportOutcome {
        documents += document
        return handler(document)
    }
}

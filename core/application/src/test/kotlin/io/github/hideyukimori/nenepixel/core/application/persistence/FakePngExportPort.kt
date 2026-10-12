package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

internal class FakePngExportPort : PngExportPort {
    val captures = mutableListOf<PngExportCapture>()
    val documents = mutableListOf<DocumentState>()
    var handler: suspend (DocumentState) -> PngExportOutcome = { PngExportOutcome.Exported }

    override suspend fun export(capture: PngExportCapture): PngExportOutcome {
        captures += capture
        val document = capture.document
        documents += document
        return handler(document)
    }
}

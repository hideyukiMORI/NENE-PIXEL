package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImageOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImagePort

/** The two distinct image-import capabilities of an editor fixture. */
internal data class TestImageImportPorts(
    val referenceImage: ReferenceImagePort = ReferenceImagePort { ReferenceImageOutcome.Cancelled },
    val pngImport: PngImportPort = PngImportPort { PngImportOutcome.Cancelled },
)

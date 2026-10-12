package io.github.hideyukimori.nenepixel.presentation.compose.editor

/** Test-only inputs; the host resolves defaults into the canonical application PersistencePorts once. */
internal data class TestPersistenceConfiguration(
    val document: TestDocumentPersistence = TestDocumentPersistence(),
    val paletteJson: TestPaletteJsonPorts = TestPaletteJsonPorts(),
    val images: TestImageImportPorts = TestImageImportPorts(),
    val underlayMemory: TestUnderlayMemory? = null,
)

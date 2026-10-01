package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStoragePort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImageOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImagePort
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import io.github.hideyukimori.nenepixel.presentation.compose.requiredValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * ADR 0033 "Pending choice": a PNG picked through the real workflow waits in the workspace; choosing a layer form
 * executes `ImportLayerCommand` and clears it, cancelling only clears it.
 */
internal class EditorImportCallbacksTest {
    @Test
    fun `appending adds a layer and the missing colour and clears the pending import`() {
        val fixture = pickedFixture()
        val rasterImport = fixture.controller.callbacks.rasterImport

        val appended = rasterImport.onAppend()

        assertEquals(2, appended.document.layers.size)
        assertEquals(PALETTE_SIZE + 1, appended.palette.entryCount)
        assertNull(appended.pendingImport)
        assertNull(fixture.runtime.state.workspaceState.pendingImport)
        assertNull(appended.layerNotice)
        assertSame(appended, fixture.controller.renderState)
    }

    @Test
    fun `converting adds a layer, keeps the palette and clears the pending import`() {
        val fixture = pickedFixture()
        val rasterImport = fixture.controller.callbacks.rasterImport

        val converted = rasterImport.onConvert()

        assertEquals(2, converted.document.layers.size)
        assertEquals(fixture.initialDocument.definition, converted.definition)
        assertNull(converted.pendingImport)
        assertNull(fixture.runtime.state.workspaceState.pendingImport)
    }

    @Test
    fun `cancelling keeps the document and clears the pending import`() {
        val fixture = pickedFixture()
        val rasterImport = fixture.controller.callbacks.rasterImport

        val cancelled = rasterImport.onCancel()

        assertSame(fixture.initialDocument, cancelled.document)
        assertNull(cancelled.pendingImport)
        assertNull(fixture.runtime.state.workspaceState.pendingImport)
    }

    @Test
    fun `appending without a pending import changes nothing`() {
        val fixture = PresentationTestValues.fixture()
        val before = fixture.controller.renderState
        val rasterImport = fixture.controller.callbacks.rasterImport

        val after = rasterImport.onAppend()

        assertSame(fixture.initialDocument, after.document)
        assertEquals(before, after)
    }

    @Test
    fun `appending at sixteen layers is refused, clears the pending import and shows a notice`() {
        val fixture = PresentationTestValues.fixture()
        val layers = fixture.controller.callbacks.layers
        repeat(MAX_LAYERS - 1) { layers.onAdd() }
        pick(fixture)
        val full = fixture.runtime.state.documentState
        assertNotNull(fixture.runtime.state.workspaceState.pendingImport)
        val rasterImport = fixture.controller.callbacks.rasterImport

        val refused = rasterImport.onAppend()

        assertSame(full, refused.document)
        assertNull(refused.pendingImport)
        assertNull(fixture.runtime.state.workspaceState.pendingImport)
        assertEquals(LayerNotice.Kind.Failed, refused.layerNotice?.kind)
    }

    private fun pickedFixture(): EditorFixture {
        val fixture = PresentationTestValues.fixture()
        pick(fixture)
        assertNotNull(fixture.runtime.state.workspaceState.pendingImport)
        return fixture
    }

    /** Picks [raster] through the workflow's PNG import, as the file surface's Import PNG button does. */
    private fun pick(fixture: EditorFixture) {
        val workflow =
            EditorPersistenceWorkflow.create(
                fixture.runtime,
                PersistencePorts(
                    ImportTestProjectStoragePort,
                    ImportTestRecoveryRecordPort,
                    PngExportPort { PngExportOutcome.Cancelled },
                    PaletteJsonExportPort { PaletteJsonExportOutcome.Cancelled },
                    PaletteJsonImportPort { PaletteJsonImportOutcome.Cancelled },
                    ReferenceImagePort { ReferenceImageOutcome.Cancelled },
                    PngImportPort { PngImportOutcome.Picked(raster()) },
                ),
                Dispatchers.Unconfined,
            )
        runBlocking {
            workflow.initializeRecovery()
            workflow.pngImport.pick()
        }
        fixture.controller.synchronizeWithRuntime()
    }

    /** Red, which the fixture palette holds, blue, which it does not, and one transparent pixel. */
    private fun raster(): ImportRaster =
        ImportRaster.create(2, 2, intArrayOf(OPAQUE_RED, OPAQUE_BLUE, TRANSPARENT, OPAQUE_RED)).requiredValue()

    private companion object {
        const val PALETTE_SIZE: Int = 9
        const val MAX_LAYERS: Int = 16
        const val OPAQUE_RED: Int = 0xFF0000FF.toInt()
        const val OPAQUE_BLUE: Int = 0x0000FFFF
        const val TRANSPARENT: Int = 0
    }
}

private data object ImportTestProjectStoragePort : ProjectStoragePort {
    override suspend fun save(document: DocumentState): ProjectSaveOutcome = ProjectSaveOutcome.Cancelled

    override suspend fun load(): ProjectLoadOutcome = ProjectLoadOutcome.Cancelled

    override suspend fun copyLegacySource(source: LegacyRgbaSource): LegacySourceCopyOutcome =
        LegacySourceCopyOutcome.Cancelled
}

private data object ImportTestRecoveryRecordPort : RecoveryRecordPort {
    override suspend fun inspect(): RecoveryInspection = RecoveryInspection.Missing

    override suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome =
        RecoveryRetirementOutcome.Retired(generation())

    override suspend fun publishCandidate(
        expected: ExpectedRecoveryLineage,
        document: DocumentState,
    ): RecoveryPublicationOutcome = RecoveryPublicationOutcome.Published(generation())

    private fun generation(): RecoveryGeneration =
        when (val result = RecoveryGeneration.create(1L)) {
            is RecoveryGenerationResult.Created -> result.generation
            RecoveryGenerationResult.Rejected -> error("Invalid test recovery generation")
        }
}

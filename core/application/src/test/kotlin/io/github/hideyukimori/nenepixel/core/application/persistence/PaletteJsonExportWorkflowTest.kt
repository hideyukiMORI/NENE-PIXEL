package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.editor.PaletteJsonExportStart
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class PaletteJsonExportWorkflowTest {
    @Test
    fun `export without a session writes the document definition and changes nothing else`() =
        runBlocking {
            val fixture = initializedFixture()
            apply(fixture.runtime, position(0, 0), red)
            val before = fixture.runtime.state
            val autosave = fixture.workflow.autosave.value
            assertCompleted(PersistenceLastOutcome.PaletteJsonExported, fixture.workflow.paletteJson.export())
            assertEquals(listOf(before.documentState.definition), fixture.paletteExporter.definitions)
            assertEquals(before, fixture.runtime.state)
            assertEquals(autosave, fixture.workflow.autosave.value)
            assertTrue(fixture.recovery.retireCalls.isEmpty())
            assertTrue(fixture.recovery.publishCalls.isEmpty())
            assertTrue(fixture.exporter.documents.isEmpty())
        }

    @Test
    fun `export during a session writes the draft and keeps the session open`() =
        runBlocking {
            val fixture = initializedFixture()
            assertInstanceOf(
                WorkspaceReductionResult.Reduced::class.java,
                fixture.runtime.paletteOperations.beginPaletteEdit(),
            )
            assertInstanceOf(
                WorkspaceReductionResult.Reduced::class.java,
                fixture.runtime.reduce(
                    WorkspaceAction.EditPaletteDraft(PaletteDraftOperation.SetSlotColor(redIndex, green)),
                ),
            )
            val before = fixture.runtime.state
            val draft = before.workspaceState.paletteEditSession?.draft ?: fail("Palette session was closed")
            assertNotEquals(before.documentState.definition, draft)
            assertCompleted(PersistenceLastOutcome.PaletteJsonExported, fixture.workflow.paletteJson.export())
            assertEquals(listOf(draft), fixture.paletteExporter.definitions)
            assertEquals(before, fixture.runtime.state)
        }

    @Test
    fun `cancelled port outcome completes as cancelled`() =
        runBlocking {
            val fixture = initializedFixture()
            fixture.paletteExporter.handler = { PaletteJsonExportOutcome.Cancelled }
            assertCompleted(PersistenceLastOutcome.Cancelled, fixture.workflow.paletteJson.export())
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
        }

    @Test
    fun `failed port outcome is typed as a palette JSON export failure`() =
        runBlocking {
            val fixture = initializedFixture()
            val before = fixture.runtime.state
            fixture.paletteExporter.handler = {
                PaletteJsonExportOutcome.Failed(
                    ProjectStorageFailure.ReadBackMismatch,
                    PartialOutputCleanup.DELETE_FAILED,
                )
            }
            val failure =
                assertInstanceOf(
                    PersistenceFailure.PaletteJsonExport::class.java,
                    assertFailed(fixture.workflow.paletteJson.export()),
                )
            assertEquals(ProjectStorageFailure.ReadBackMismatch, failure.failure)
            assertEquals(PartialOutputCleanup.DELETE_FAILED, failure.cleanup)
            assertEquals(before, fixture.runtime.state)
        }

    @Test
    fun `other IO stays busy while palette JSON export is in flight`() =
        runBlocking {
            val fixture = initializedFixture()
            val gate = CompletableDeferred<Unit>()
            fixture.paletteExporter.handler = {
                gate.await()
                PaletteJsonExportOutcome.Exported
            }
            val pending = async(start = CoroutineStart.UNDISPATCHED) { fixture.workflow.paletteJson.export() }
            assertInstanceOf(PersistenceOperationPhase.Exporting::class.java, fixture.workflow.operation.value.phase)
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.saveAs())
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.load())
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.exportPng())
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.paletteJson.export())
            gate.complete(Unit)
            assertCompleted(PersistenceLastOutcome.PaletteJsonExported, pending.await())
            assertEquals(1, fixture.paletteExporter.definitions.size)
            assertTrue(fixture.exporter.documents.isEmpty())
        }

    @Test
    fun `stale palette JSON completion cannot clear a newer operation`() =
        runBlocking {
            val fixture = initializedFixture()
            val operations = fixture.runtime.paletteJsonOperations
            val first = assertInstanceOf(PaletteJsonExportStart.Started::class.java, operations.begin())
            operations.complete(first.handle, PaletteJsonExportOutcome.Exported)
            val second = assertInstanceOf(PaletteJsonExportStart.Started::class.java, operations.begin())
            val before = fixture.workflow.operation.value
            assertEquals(
                PersistenceRequestResult.Stale,
                operations.complete(first.handle, PaletteJsonExportOutcome.Cancelled),
            )
            assertEquals(before, fixture.workflow.operation.value)
            assertCompleted(
                PersistenceLastOutcome.Cancelled,
                operations.complete(second.handle, PaletteJsonExportOutcome.Cancelled),
            )
        }
}

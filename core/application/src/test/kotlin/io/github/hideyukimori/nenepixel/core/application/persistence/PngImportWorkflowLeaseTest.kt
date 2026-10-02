package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.editor.PngImportPickStart
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** ADR 0033: the PNG pick shares the document-output lease, so busy, stale and cancel follow it. */
internal class PngImportWorkflowLeaseTest {
    private val raster = importRaster()

    @Test
    fun `pick is busy while another operation holds the lease and never asks the port`() =
        runBlocking {
            val fixture = initializedFixture()
            val gate = CompletableDeferred<Unit>()
            fixture.exporter.handler = {
                gate.await()
                PngExportOutcome.Exported
            }
            val export = async(start = CoroutineStart.UNDISPATCHED) { fixture.workflow.exportPng() }

            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.pngImport.pick())

            assertEquals(0, fixture.pngImports.calls)
            gate.complete(Unit)
            assertCompleted(PersistenceLastOutcome.PngExported, export.await())
            assertNull(fixture.runtime.state.workspaceState.pendingImport)
        }

    @Test
    fun `other IO stays busy while the pick is in flight`() =
        runBlocking {
            val fixture = initializedFixture()
            val gate = CompletableDeferred<Unit>()
            fixture.pngImports.handler = {
                gate.await()
                PngImportOutcome.Picked(raster)
            }
            val workflow = fixture.workflow.pngImport
            val pending = async(start = CoroutineStart.UNDISPATCHED) { workflow.pick() }

            assertInstanceOf(PersistenceOperationPhase.Exporting::class.java, fixture.workflow.operation.value.phase)
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.saveAs())
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.load())
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.exportPng())
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.referenceImage.pick())
            assertEquals(PersistenceRequestResult.Busy, workflow.pick())
            gate.complete(Unit)

            assertCompleted(PersistenceLastOutcome.PngImportRead, pending.await())
            assertEquals(1, fixture.pngImports.calls)
            assertNotNull(fixture.runtime.state.workspaceState.pendingImport)
        }

    @Test
    fun `a pick completed after the document was replaced is stale and sets no pending import`() =
        runBlocking {
            val fixture = initializedFixture()
            val operations = fixture.runtime.pngImportOperations
            val first = assertInstanceOf(PngImportPickStart.Started::class.java, operations.beginPick())
            val planned = planned(fixture)
            operations.cancelPick(first.handle)
            fixture.workflow.createNewDocument(newRequest(3, 2))
            assertEquals(canvas(3, 2), fixture.runtime.state.documentState.size)
            val projection = fixture.workflow.operation.value
            val state = fixture.runtime.state

            assertEquals(
                PersistenceRequestResult.Stale,
                operations.completePick(first.handle, PngImportOutcome.Picked(raster), planned),
            )

            assertEquals(projection, fixture.workflow.operation.value)
            assertEquals(state, fixture.runtime.state)
            assertNull(fixture.runtime.state.workspaceState.pendingImport)
        }

    @Test
    fun `a palette session opened during the pick keeps the pending import out`() =
        runBlocking {
            val fixture = initializedFixture()
            val operations = fixture.runtime.pngImportOperations
            val start = assertInstanceOf(PngImportPickStart.Started::class.java, operations.beginPick())
            val planned = planned(fixture)
            assertInstanceOf(
                WorkspaceReductionResult.Reduced::class.java,
                fixture.runtime.paletteOperations.beginPaletteEdit(),
            )

            assertCompleted(
                PersistenceLastOutcome.PngImportRead,
                operations.completePick(start.handle, PngImportOutcome.Picked(raster), planned),
            )

            assertNull(fixture.runtime.state.workspaceState.pendingImport)
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
        }

    @Test
    fun `cancelling the operation during the pick drops the picked raster`() =
        runBlocking {
            val fixture = initializedFixture()
            val gate = CompletableDeferred<Unit>()
            fixture.pngImports.handler = {
                gate.await()
                PngImportOutcome.Picked(raster)
            }
            val pending = async(start = CoroutineStart.UNDISPATCHED) { fixture.workflow.pngImport.pick() }
            val phase =
                assertInstanceOf(
                    PersistenceOperationPhase.Exporting::class.java,
                    fixture.workflow.operation.value.phase,
                )

            assertEquals(PersistenceCancellationResult.CancellationStarted, fixture.workflow.cancel(phase.operation))
            gate.complete(Unit)

            assertCompleted(PersistenceLastOutcome.Cancelled, pending.await())
            assertNull(fixture.runtime.state.workspaceState.pendingImport)
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
        }

    @Test
    fun `coroutine cancellation releases the lease without a pending import`() =
        runBlocking {
            val fixture = initializedFixture()
            fixture.pngImports.handler = {
                CompletableDeferred<Unit>().await()
                PngImportOutcome.Picked(raster)
            }
            val pending = async(start = CoroutineStart.UNDISPATCHED) { fixture.workflow.pngImport.pick() }

            pending.cancelAndJoin()

            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
            assertEquals(PersistenceLastOutcome.Cancelled, fixture.workflow.operation.value.lastOutcome)
            assertNull(fixture.runtime.state.workspaceState.pendingImport)
            fixture.pngImports.handler = { PngImportOutcome.Picked(raster) }
            assertCompleted(PersistenceLastOutcome.PngImportRead, fixture.workflow.pngImport.pick())
        }

    private fun planned(fixture: Fixture): PendingRasterImport {
        val source = fixture.runtime.pngImportOperations.planningSource()
        return PendingRasterImport.planned(raster, source.canvas, source.definition)
    }
}

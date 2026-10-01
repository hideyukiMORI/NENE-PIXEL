package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/** ADR 0033: the PNG pick outcomes and the pending choice each leaves on the workspace. */
internal class PngImportWorkflowTest {
    @Test
    fun `picked raster is planned against the installed document without touching it`() =
        runBlocking {
            val fixture = initializedFixture()
            fixture.workflow.createNewDocument(newRequest(3, 2))
            val before = fixture.runtime.state
            val raster = importRaster()
            fixture.pngImports.handler = { PngImportOutcome.Picked(raster) }

            assertCompleted(PersistenceLastOutcome.PngImportRead, fixture.workflow.pngImport.pick())

            val after = fixture.runtime.state
            val pending = after.workspaceState.pendingImport ?: fail("Pending import was not set")
            assertEquals(2, pending.facts.colorCount)
            assertPlannedFor(pending, raster, canvas(3, 2), before.documentState.definition)
            assertSame(before.documentState, after.documentState)
            assertEquals(before.historyAvailability, after.historyAvailability)
            assertEquals(before.dirtyState, after.dirtyState)
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
            assertEquals(PersistenceLastOutcome.PngImportRead, fixture.workflow.operation.value.lastOutcome)
            assertEquals(1, fixture.pngImports.calls)
        }

    @Test
    fun `cancelled pick completes as cancelled and leaves the state alone`() =
        runBlocking {
            val fixture = initializedFixture()
            val before = fixture.runtime.state

            assertCompleted(PersistenceLastOutcome.Cancelled, fixture.workflow.pngImport.pick())

            assertEquals(before, fixture.runtime.state)
            assertNull(fixture.runtime.state.workspaceState.pendingImport)
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
        }

    @Test
    fun `each rejection is typed with its reason and sets no pending import`() =
        runBlocking {
            val fixture = initializedFixture()
            val before = fixture.runtime.state
            val reasons =
                listOf(
                    PngImportSourceRejection.TooManyBytes,
                    PngImportSourceRejection.TooManyPixels,
                    PngImportSourceRejection.Unsupported,
                )

            val failures =
                reasons.map { reason ->
                    fixture.pngImports.handler = { PngImportOutcome.Rejected(reason) }
                    assertFailed(fixture.workflow.pngImport.pick())
                }

            assertEquals(reasons.map { PersistenceFailure.PngImportRejected(it) }, failures)
            assertEquals(before, fixture.runtime.state)
            assertNull(fixture.runtime.state.workspaceState.pendingImport)
        }

    @Test
    fun `failed pick is typed as a PNG import pick failure`() =
        runBlocking {
            val fixture = initializedFixture()
            val before = fixture.runtime.state
            fixture.pngImports.handler = { PngImportOutcome.Failed(ProjectStorageFailure.InvalidPickerResult) }

            val failure =
                assertInstanceOf(
                    PersistenceFailure.PngImportPick::class.java,
                    assertFailed(fixture.workflow.pngImport.pick()),
                )

            assertEquals(ProjectStorageFailure.InvalidPickerResult, failure.failure)
            assertEquals(before, fixture.runtime.state)
            assertNull(fixture.runtime.state.workspaceState.pendingImport)
        }

    @Test
    fun `palette session rejects the pick without asking the port or taking the lease`() =
        runBlocking {
            val fixture = initializedFixture()
            fixture.pngImports.handler = { PngImportOutcome.Picked(importRaster()) }
            assertInstanceOf(
                WorkspaceReductionResult.Reduced::class.java,
                fixture.runtime.paletteOperations.beginPaletteEdit(),
            )

            assertEquals(PersistenceRequestResult.PaletteSessionActive, fixture.workflow.pngImport.pick())

            assertEquals(0, fixture.pngImports.calls)
            assertNull(fixture.runtime.read { transaction -> transaction.coordination.activeOperation })
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
            assertNull(fixture.runtime.state.workspaceState.pendingImport)
            assertCompleted(PersistenceLastOutcome.Cancelled, fixture.workflow.referenceImage.pick())
        }
}

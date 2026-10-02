package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.RedoCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.application.editor.PngImportPickStart
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.NewWorkImportOption
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.importing.NewWorkImportPlan
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

private const val WIDE: Int = 257
private const val OPAQUE_RED: Int = 0xFF0000FF.toInt()

/** ADR 0033: `PngImportWorkflow.openAsNewWork` opens the pending new-work plan as a work switch. */
internal class PngImportNewWorkWorkflowTest {
    @Test
    fun `a clean work opens the plan at once and drops the underlay`() =
        runBlocking {
            val fixture = initializedFixture()
            fixture.runtime.reduce(WorkspaceAction.SetReferenceUnderlay(underlay()))
            assertNotNull(fixture.runtime.state.workspaceState.underlay)
            val plan = setPendingNewWork(fixture)
            val before = fixture.runtime.state.documentState.id

            assertCompleted(PersistenceLastOutcome.NewDocumentCreated, fixture.workflow.pngImport.openAsNewWork())

            assertOpenedFrom(fixture, plan, before)
        }

    @Test
    fun `a dirty work asks first and the confirmation opens the plan`() =
        runBlocking {
            val fixture = initializedFixture()
            val plan = setPendingNewWork(fixture)
            apply(fixture.runtime, position(1, 1), red)
            val before = fixture.runtime.state.documentState

            val confirmation = assertAwaiting(fixture.workflow.pngImport.openAsNewWork())

            assertNull(fixture.runtime.state.workspaceState.pendingImport)
            assertEquals(before, fixture.runtime.state.documentState)
            assertCompleted(PersistenceLastOutcome.NewDocumentCreated, fixture.workflow.confirm(confirmation))
            assertOpenedFrom(fixture, plan, before.id)
        }

    @Test
    fun `cancelling the confirmation keeps the work and does not bring the plan back`() =
        runBlocking {
            val fixture = initializedFixture()
            setPendingNewWork(fixture)
            apply(fixture.runtime, position(1, 1), red)
            val before = fixture.runtime.state.documentState
            val confirmation = assertAwaiting(fixture.workflow.pngImport.openAsNewWork())

            assertEquals(PersistenceCancellationResult.Cancelled, fixture.workflow.cancel(confirmation.operation))

            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
            assertEquals(PersistenceLastOutcome.Cancelled, fixture.workflow.operation.value.lastOutcome)
            assertEquals(before, fixture.runtime.state.documentState)
            assertEquals(DocumentDirtyState.Dirty, fixture.runtime.state.dirtyState)
            assertNull(fixture.runtime.state.workspaceState.pendingImport)
            assertEquals(PersistenceRequestResult.Stale, fixture.workflow.confirm(confirmation))
        }

    @Test
    fun `without a pending import the result is stale and nothing changes`() =
        runBlocking {
            val fixture = initializedFixture()
            val state = fixture.runtime.state
            val operation = fixture.workflow.operation.value

            assertEquals(PersistenceRequestResult.Stale, fixture.workflow.pngImport.openAsNewWork())

            assertEquals(state, fixture.runtime.state)
            assertEquals(operation, fixture.workflow.operation.value)
        }

    @Test
    fun `an unavailable new-work form is stale and keeps the pending import`() =
        runBlocking {
            val fixture = initializedFixture()
            val pending = pending(wideRaster())
            assertSame(NewWorkImportOption.AboveCanvasLimit, pending.newWork)
            fixture.runtime.reduce(WorkspaceAction.SetPendingRasterImport(pending))
            val document = fixture.runtime.state.documentState

            assertEquals(PersistenceRequestResult.Stale, fixture.workflow.pngImport.openAsNewWork())

            assertSame(pending, fixture.runtime.state.workspaceState.pendingImport)
            assertEquals(document, fixture.runtime.state.documentState)
        }

    @Test
    fun `another operation's lease makes it busy and keeps the pending import`() =
        runBlocking {
            val fixture = initializedFixture()
            setPendingNewWork(fixture)
            val pending = fixture.runtime.state.workspaceState.pendingImport
            assertInstanceOf(PngImportPickStart.Started::class.java, fixture.runtime.pngImportOperations.beginPick())

            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.pngImport.openAsNewWork())

            assertSame(pending, fixture.runtime.state.workspaceState.pendingImport)
        }

    @Test
    fun `an open palette session rejects it and keeps the pending import`() =
        runBlocking {
            val fixture = initializedFixture()
            setPendingNewWork(fixture)
            val pending = fixture.runtime.state.workspaceState.pendingImport
            assertInstanceOf(
                WorkspaceReductionResult.Reduced::class.java,
                fixture.runtime.paletteOperations.beginPaletteEdit(),
            )
            val document = fixture.runtime.state.documentState

            assertEquals(PersistenceRequestResult.PaletteSessionActive, fixture.workflow.pngImport.openAsNewWork())

            assertSame(pending, fixture.runtime.state.workspaceState.pendingImport)
            assertEquals(document, fixture.runtime.state.documentState)
        }

    @Test
    fun `the opened work has its own history`() =
        runBlocking {
            val fixture = initializedFixture()
            val plan = setPendingNewWork(fixture)
            assertCompleted(PersistenceLastOutcome.NewDocumentCreated, fixture.workflow.pngImport.openAsNewWork())

            apply(fixture.runtime, position(0, 1), black)
            assertNotEquals(plan.snapshot, onlySnapshot(fixture))
            undo(fixture.runtime)

            val state = fixture.runtime.state
            assertEquals(plan.snapshot, onlySnapshot(fixture))
            assertFalse(state.historyAvailability.canUndo)
            assertTrue(state.historyAvailability.canRedo)
            val redo = RedoCommand.create(state.documentState.id, state.documentState.revision)
            assertInstanceOf(CommandResult.Applied::class.java, fixture.runtime.execute(redo))
            assertNotEquals(plan.snapshot, onlySnapshot(fixture))
        }
}

internal fun assertOpenedFrom(
    fixture: Fixture,
    plan: NewWorkImportPlan,
    previousId: DocumentId,
) {
    val state = fixture.runtime.state
    val document = state.documentState
    val raster = importRaster()
    assertNotEquals(previousId, document.id)
    assertEquals(Revision.initial(), document.revision)
    assertEquals(1, document.layers.size)
    assertEquals(raster.width, document.size.width.value)
    assertEquals(raster.height, document.size.height.value)
    assertEquals(plan.definition, document.definition)
    assertEquals(plan.snapshot, document.layers.single().snapshot)
    assertEquals(DocumentDirtyState.Dirty, state.dirtyState)
    assertFalse(state.historyAvailability.canUndo)
    assertFalse(state.historyAvailability.canRedo)
    assertNull(state.workspaceState.pendingImport)
    assertNull(state.workspaceState.underlay)
}

private fun onlySnapshot(fixture: Fixture): PixelSnapshot =
    fixture.runtime.state.documentState.layers
        .single()
        .snapshot

internal fun setPendingNewWork(fixture: Fixture): NewWorkImportPlan {
    val pending = pending(importRaster())
    val option = assertInstanceOf(NewWorkImportOption.Available::class.java, pending.newWork)
    fixture.runtime.reduce(WorkspaceAction.SetPendingRasterImport(pending))
    assertSame(pending, fixture.runtime.state.workspaceState.pendingImport)
    return option.plan
}

private fun pending(raster: ImportRaster): PendingRasterImport =
    PendingRasterImport.planned(raster, canvas(4, 4), defaultDefinition)

private fun underlay(): ReferenceUnderlay = ReferenceUnderlay.placed(referenceImage(8, 4), canvas(4, 4))

private fun wideRaster(): ImportRaster =
    when (val result = ImportRaster.create(WIDE, 1, IntArray(WIDE) { OPAQUE_RED })) {
        is DomainValueResult.Created -> result.value
        is DomainValueResult.Rejected -> fail("Wide raster rejected: ${result.rejection}")
    }

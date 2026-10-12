package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.initializedFixture
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.begin
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.move
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.paint
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.select
import io.github.hideyukimori.nenepixel.core.domain.importing.ImportRaster
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/** Directly affected save checkpoint and asynchronous workspace-completion boundaries, not a persistence sweep. */
internal class SelectionPersistenceContractTest {
    @Test
    fun `save captures committed pixels and a later transfer keeps the new capture dirty`() =
        runBlocking {
            val fixture = initializedFixture()
            val runtime = fixture.runtime
            paint(runtime)
            val captured = runtime.state.documentState
            val gate = CompletableDeferred<Unit>()
            fixture.storage.saveHandler = {
                gate.await()
                ProjectSaveOutcome.Saved
            }
            val saving = async(start = CoroutineStart.UNDISPATCHED) { fixture.workflow.saveAs() }
            begin(runtime)
            select(runtime)
            move(runtime)
            assertSame(SelectionApplyResult.Applied, runtime.selectionOperations.applySelection())
            val changed = runtime.state.documentState
            val token = runtime.autosaveProjection.value.pendingStateToken
            gate.complete(Unit)
            assertInstanceOf(PersistenceRequestResult.Completed::class.java, saving.await())

            assertEquals(listOf(captured), fixture.storage.savedDocuments)
            assertSame(changed, runtime.state.documentState)
            assertEquals(DocumentDirtyState.Dirty, runtime.state.dirtyState)
            assertEquals(token, runtime.autosaveProjection.value.pendingStateToken)
            assertInstanceOf(
                CommandResult.Applied::class.java,
                runtime.execute(UndoCommand.create(changed.id, changed.revision)),
            )
            assertEquals(captured, runtime.state.documentState)
            assertEquals(DocumentDirtyState.Clean, runtime.state.dirtyState)
        }

    @Test
    fun `successful JSON completion cancels selection through internal palette reduction`() =
        runBlocking {
            val fixture = initializedFixture()
            val runtime = fixture.runtime
            val operations = RuntimePaletteJsonOperations(runtime)
            val lease = assertInstanceOf(PaletteJsonImportStart.Started::class.java, operations.beginImport())
            begin(runtime)
            select(runtime)
            val document = runtime.state.documentState

            assertEquals(
                PersistenceRequestResult.Completed(PersistenceLastOutcome.PaletteJsonImported),
                operations.completeImport(lease.handle, PaletteJsonImportOutcome.Imported(defaultDefinition)),
            )

            assertNull(runtime.state.workspaceState.selectionSession)
            assertSame(
                defaultDefinition,
                runtime.state.workspaceState.paletteEditSession
                    ?.pendingImport
                    ?.target,
            )
            assertSame(document, runtime.state.documentState)
        }

    @Test
    fun `cancelled JSON completion keeps the selection draft`() =
        runBlocking {
            val fixture = initializedFixture()
            val runtime = fixture.runtime
            val operations = RuntimePaletteJsonOperations(runtime)
            val lease = assertInstanceOf(PaletteJsonImportStart.Started::class.java, operations.beginImport())
            begin(runtime)
            select(runtime)
            val selection = runtime.state.workspaceState.selectionSession

            assertEquals(
                PersistenceRequestResult.Completed(PersistenceLastOutcome.Cancelled),
                operations.completeImport(lease.handle, PaletteJsonImportOutcome.Cancelled),
            )

            assertSame(selection, runtime.state.workspaceState.selectionSession)
            assertNull(runtime.state.workspaceState.paletteEditSession)
        }

    @Test
    fun `successful PNG completion keeps pending import and cancels selection atomically`() =
        runBlocking {
            val fixture = initializedFixture()
            val runtime = fixture.runtime
            val operations = RuntimePngImportOperations(runtime)
            val lease = assertInstanceOf(PngImportPickStart.Started::class.java, operations.beginPick())
            begin(runtime)
            select(runtime)
            val document = runtime.state.documentState
            val raster = value(ImportRaster.create(1, 1, intArrayOf(0xFF0000FF.toInt())))
            val pending = PendingRasterImport.planned(raster, document.size, document.definition)

            assertEquals(
                PersistenceRequestResult.Completed(PersistenceLastOutcome.PngImportRead),
                operations.completePick(lease.handle, PngImportOutcome.Picked(raster), pending),
            )

            assertNull(runtime.state.workspaceState.selectionSession)
            assertSame(pending, runtime.state.workspaceState.pendingImport)
            assertSame(document, runtime.state.documentState)
            assertEquals(DocumentDirtyState.Clean, runtime.state.dirtyState)
            val before = runtime.state
            assertInstanceOf(
                WorkspaceReductionResult.Rejected::class.java,
                runtime.selectionOperations.beginSelection(LayerId.first()),
            )
            assertEquals(before, runtime.state)
            assertNotNull(runtime.state.workspaceState.pendingImport)
        }

    private fun <T> value(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Invalid PNG fixture: ${result.rejection}")
        }
}

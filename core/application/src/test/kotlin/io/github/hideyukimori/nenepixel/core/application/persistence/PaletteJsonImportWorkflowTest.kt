package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.editor.PaletteJsonImportStart
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteImportMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class PaletteJsonImportWorkflowTest {
    private val imported = definition(paletteIndex(1), green, red)
    private val replacement = definition(paletteIndex(0), red, green)

    @Test
    fun `import without a session opens one and stages the definition without touching the document`() =
        runBlocking {
            val fixture = initializedFixture()
            apply(fixture.runtime, position(0, 0), red)
            val before = fixture.runtime.state
            fixture.paletteImporter.handler = { PaletteJsonImportOutcome.Imported(imported) }

            assertCompleted(PersistenceLastOutcome.PaletteJsonImported, fixture.workflow.paletteJson.import())

            val after = fixture.runtime.state
            val session = after.workspaceState.paletteEditSession ?: fail("Palette session was not opened")
            val pending = session.pendingImport ?: fail("Import was not staged")
            assertSame(imported, pending.target)
            assertEquals(PaletteImportMode.ByNumber, pending.mode)
            assertEquals(before.documentState.definition, session.draft)
            assertSame(before.documentState, after.documentState)
            assertEquals(before.dirtyState, after.dirtyState)
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
            assertEquals(1, fixture.paletteImporter.calls)
        }

    @Test
    fun `import during a session keeps the draft timeline and replaces the pending import`() =
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
            fixture.paletteImporter.handler = { PaletteJsonImportOutcome.Imported(imported) }
            assertCompleted(PersistenceLastOutcome.PaletteJsonImported, fixture.workflow.paletteJson.import())
            val first = fixture.runtime.state.workspaceState.paletteEditSession ?: fail("Palette session was closed")
            fixture.paletteImporter.handler = { PaletteJsonImportOutcome.Imported(replacement) }

            assertCompleted(PersistenceLastOutcome.PaletteJsonImported, fixture.workflow.paletteJson.import())

            val second = fixture.runtime.state.workspaceState.paletteEditSession ?: fail("Palette session was closed")
            assertSame(imported, first.pendingImport?.target)
            assertSame(replacement, second.pendingImport?.target)
            assertEquals(first.draft, second.draft)
            assertEquals(first.timeline, second.timeline)
            assertEquals(first.cursor, second.cursor)
            assertEquals(first.base, second.base)
        }

    @Test
    fun `cancelled port outcome completes as cancelled and opens no session`() =
        runBlocking {
            val fixture = initializedFixture()
            val before = fixture.runtime.state
            fixture.paletteImporter.handler = { PaletteJsonImportOutcome.Cancelled }

            assertCompleted(PersistenceLastOutcome.Cancelled, fixture.workflow.paletteJson.import())

            assertEquals(before, fixture.runtime.state)
            assertEquals(PersistenceOperationPhase.Idle, fixture.workflow.operation.value.phase)
        }

    @Test
    fun `failed port outcome is typed as a palette JSON import failure`() =
        runBlocking {
            val fixture = initializedFixture()
            val before = fixture.runtime.state
            fixture.paletteImporter.handler = {
                PaletteJsonImportOutcome.Failed(ProjectStorageFailure.InvalidPaletteJson)
            }

            val failure =
                assertInstanceOf(
                    PersistenceFailure.PaletteJsonImport::class.java,
                    assertFailed(fixture.workflow.paletteJson.import()),
                )

            assertEquals(ProjectStorageFailure.InvalidPaletteJson, failure.failure)
            assertEquals(before, fixture.runtime.state)
        }

    @Test
    fun `other IO stays busy while palette JSON import is in flight`() =
        runBlocking {
            val fixture = initializedFixture()
            val gate = CompletableDeferred<Unit>()
            fixture.paletteImporter.handler = {
                gate.await()
                PaletteJsonImportOutcome.Imported(imported)
            }
            val pending = async(start = CoroutineStart.UNDISPATCHED) { fixture.workflow.paletteJson.import() }

            assertInstanceOf(PersistenceOperationPhase.Exporting::class.java, fixture.workflow.operation.value.phase)
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.saveAs())
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.load())
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.exportPng())
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.paletteJson.export())
            assertEquals(PersistenceRequestResult.Busy, fixture.workflow.paletteJson.import())
            gate.complete(Unit)

            assertCompleted(PersistenceLastOutcome.PaletteJsonImported, pending.await())
            assertEquals(1, fixture.paletteImporter.calls)
            assertTrue(fixture.paletteExporter.definitions.isEmpty())
            assertTrue(fixture.exporter.documents.isEmpty())
        }

    @Test
    fun `stale palette JSON import completion neither clears a newer operation nor stages`() =
        runBlocking {
            val fixture = initializedFixture()
            val operations = fixture.runtime.paletteJsonOperations
            val first = assertInstanceOf(PaletteJsonImportStart.Started::class.java, operations.beginImport())
            operations.completeImport(first.handle, PaletteJsonImportOutcome.Cancelled)
            val second = assertInstanceOf(PaletteJsonImportStart.Started::class.java, operations.beginImport())
            val projection = fixture.workflow.operation.value
            val state = fixture.runtime.state

            assertEquals(
                PersistenceRequestResult.Stale,
                operations.completeImport(first.handle, PaletteJsonImportOutcome.Imported(imported)),
            )

            assertEquals(projection, fixture.workflow.operation.value)
            assertEquals(state, fixture.runtime.state)
            assertNull(fixture.runtime.state.workspaceState.paletteEditSession)
            assertCompleted(
                PersistenceLastOutcome.PaletteJsonImported,
                operations.completeImport(second.handle, PaletteJsonImportOutcome.Imported(imported)),
            )
            val staged = fixture.runtime.state.workspaceState.paletteEditSession ?: fail("Palette session is missing")
            assertSame(imported, staged.pendingImport?.target)
        }
}

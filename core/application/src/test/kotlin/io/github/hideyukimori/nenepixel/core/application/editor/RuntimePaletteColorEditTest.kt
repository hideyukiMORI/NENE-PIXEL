package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.initializedFixture
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteImportMode
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RuntimePaletteColorEditTest {
    @Test
    fun `reducer rejects interleaving while document history and autosave stay unchanged`() {
        val runtime = openedSession()
        val before = runtime.state
        val autosave = runtime.autosaveProjection.value
        val source = runtime.read { it.switchContext().source }
        reduced(runtime, WorkspaceAction.BeginPaletteColorEdit(redIndex))
        reduced(runtime, WorkspaceAction.UpdatePaletteColorEdit(green))
        val preview = runtime.state.workspaceState.paletteEditSession

        blockedActions().forEach { action ->
            assertEquals(
                WorkspaceActionRejection.PaletteDraftRejected(PaletteDraftRejection.ColorEditActive),
                assertInstanceOf(WorkspaceReductionResult.Rejected::class.java, runtime.reduce(action)).rejection,
            )
            assertSame(preview, runtime.state.workspaceState.paletteEditSession)
        }
        assertEquals(
            PaletteApplyResult.Rejected(
                WorkspaceActionRejection.PaletteDraftRejected(PaletteDraftRejection.ColorEditActive),
            ),
            runtime.paletteOperations.applyPaletteDraft(),
        )
        assertSame(before.documentState, runtime.state.documentState)
        assertEquals(source, runtime.read { it.switchContext().source })
        assertEquals(autosave, runtime.autosaveProjection.value)
        assertEquals(before.dirtyState, runtime.state.dirtyState)
        reduced(runtime, WorkspaceAction.FinishPaletteColorEdit)
        assertSame(before.documentState, runtime.state.documentState)
        assertSame(PaletteApplyResult.Applied, runtime.paletteOperations.applyPaletteDraft())
        assertEquals(
            green,
            runtime.state.documentState.definition.palette
                .entries()[redIndex.value]
                .color,
        )
    }

    @Test
    fun `reducer color cancellation restores session and whole panel cancellation drops preview`() {
        val runtime = openedSession()
        val start = runtime.state.workspaceState.paletteEditSession
        reduced(runtime, WorkspaceAction.BeginPaletteColorEdit(redIndex))
        reduced(runtime, WorkspaceAction.UpdatePaletteColorEdit(black))
        reduced(runtime, WorkspaceAction.CancelPaletteColorEdit)
        assertEquals(start, runtime.state.workspaceState.paletteEditSession)
        reduced(runtime, WorkspaceAction.BeginPaletteColorEdit(redIndex))
        reduced(runtime, WorkspaceAction.UpdatePaletteColorEdit(green))
        reduced(runtime, WorkspaceAction.CancelPaletteEdit)
        assertNull(runtime.state.workspaceState.paletteEditSession)
        assertEquals(defaultDefinition, runtime.state.documentState.definition)
    }

    @Test
    fun `json import and export refuse active color edit before a physical lease`() =
        runBlocking {
            val fixture = initializedFixture()
            val runtime = fixture.runtime
            assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, runtime.paletteOperations.beginPaletteEdit())
            reduced(runtime, WorkspaceAction.BeginPaletteColorEdit(redIndex))
            reduced(runtime, WorkspaceAction.UpdatePaletteColorEdit(green))
            val state = runtime.state
            val coordination = runtime.read { it.coordination }

            assertSame(PersistenceRequestResult.Busy, fixture.workflow.paletteJson.import())
            assertSame(PersistenceRequestResult.Busy, fixture.workflow.paletteJson.export())

            assertEquals(0, fixture.paletteImporter.calls)
            assertTrue(fixture.paletteExporter.definitions.isEmpty())
            assertEquals(state, runtime.state)
            assertEquals(coordination, runtime.read { it.coordination })
        }

    @Test
    fun `import lease blocks color begin and completion stages its accepted palette`() =
        runBlocking {
            val fixture = initializedFixture()
            val runtime = fixture.runtime
            assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, runtime.paletteOperations.beginPaletteEdit())
            val operations = RuntimePaletteJsonOperations(runtime)
            val lease = assertInstanceOf(PaletteJsonImportStart.Started::class.java, operations.beginImport())
            val state = runtime.state

            assertEquals(
                WorkspaceActionRejection.PersistenceBusy,
                assertInstanceOf(
                    WorkspaceReductionResult.Rejected::class.java,
                    runtime.reduce(WorkspaceAction.BeginPaletteColorEdit(redIndex)),
                ).rejection,
            )
            assertEquals(state, runtime.state)
            assertEquals(
                PersistenceRequestResult.Completed(PersistenceLastOutcome.PaletteJsonImported),
                operations.completeImport(lease.handle, PaletteJsonImportOutcome.Imported(defaultDefinition)),
            )
            assertSame(
                defaultDefinition,
                runtime.state.workspaceState.paletteEditSession
                    ?.pendingImport
                    ?.target,
            )
            assertFalse(
                runtime.state.workspaceState.paletteEditSession
                    ?.isColorEditActive ?: true,
            )
        }

    @Test
    fun `export lease blocks color begin until physical completion`() =
        runBlocking {
            val fixture = initializedFixture()
            val runtime = fixture.runtime
            assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, runtime.paletteOperations.beginPaletteEdit())
            val operations = RuntimePaletteJsonOperations(runtime)
            val lease = assertInstanceOf(PaletteJsonExportStart.Started::class.java, operations.begin())

            assertEquals(
                WorkspaceActionRejection.PersistenceBusy,
                assertInstanceOf(
                    WorkspaceReductionResult.Rejected::class.java,
                    runtime.reduce(WorkspaceAction.BeginPaletteColorEdit(redIndex)),
                ).rejection,
            )
            operations.cancel(lease.handle)
            reduced(runtime, WorkspaceAction.BeginPaletteColorEdit(redIndex))
        }

    private fun blockedActions(): List<WorkspaceAction> =
        listOf(
            WorkspaceAction.BeginPaletteColorEdit(blackIndex),
            WorkspaceAction.EditPaletteDraft(PaletteDraftOperation.SetSlotColor(blackIndex, green)),
            WorkspaceAction.EditPaletteDraft(PaletteDraftOperation.AppendSlot(black)),
            WorkspaceAction.EditPaletteDraft(PaletteDraftOperation.SetDefault(redIndex)),
            WorkspaceAction.UndoPaletteDraft,
            WorkspaceAction.RedoPaletteDraft,
            WorkspaceAction.ImportPaletteDraft(defaultDefinition),
            WorkspaceAction.SetPaletteImportMode(PaletteImportMode.ByNumber),
            WorkspaceAction.AssignPaletteImportSlot(blackIndex, redIndex),
            WorkspaceAction.ConfirmPaletteImport,
            WorkspaceAction.CancelPaletteImport,
        )

    private fun openedSession(): EditorRuntime {
        val runtime = EditorRuntime.create(canvas(2, 2), defaultDefinition, ColorEditDocumentIdSource())
        assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, runtime.paletteOperations.beginPaletteEdit())
        return runtime
    }

    private fun reduced(
        runtime: EditorRuntime,
        action: WorkspaceAction,
    ) {
        assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, runtime.reduce(action))
    }
}

private class ColorEditDocumentIdSource : DocumentIdSource {
    override fun nextDocumentId(): DocumentId = defaultDocumentId
}

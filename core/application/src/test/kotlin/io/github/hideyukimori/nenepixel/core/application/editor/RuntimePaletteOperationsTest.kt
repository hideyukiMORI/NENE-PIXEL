package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.greenIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.indexAt
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.workspace.BeginPaletteEdit
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftRejection
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class RuntimePaletteOperationsTest {
    @Test
    fun `apply commits the draft as one command and closes the session`() {
        val runtime = openedPaletteSession()
        val before = historyPosition(runtime)
        edit(runtime, PaletteDraftOperation.SetSlotColor(redIndex, green))
        val session = runtime.state.workspaceState.paletteEditSession ?: fail("Palette session was closed")
        val draft = session.draft

        assertSame(PaletteApplyResult.Applied, runtime.paletteOperations.applyPaletteDraft())

        assertEquals(draft, runtime.state.documentState.definition)
        assertEquals(before.value + 1L, historyPosition(runtime).value)
        assertEquals(DocumentDirtyState.Dirty, runtime.state.dirtyState)
        assertNull(runtime.state.workspaceState.paletteEditSession)
        assertInstanceOf(CommandResult.Applied::class.java, runtime.execute(onePixel(runtime, redIndex)))
    }

    @Test
    fun `apply remaps pixels palette order and the active palette index`() {
        val runtime = newRuntime()
        assertInstanceOf(CommandResult.Applied::class.java, runtime.execute(onePixel(runtime, redIndex)))
        runtime.reduce(WorkspaceAction.SelectPaletteEntry(redIndex))
        begin(runtime)
        edit(runtime, PaletteDraftOperation.Reorder(listOf(redIndex, blackIndex, greenIndex)))

        assertSame(PaletteApplyResult.Applied, runtime.paletteOperations.applyPaletteDraft())

        val document = runtime.state.documentState
        assertEquals(blackIndex, indexAt(document.snapshot, position(0, 0)))
        assertEquals(redIndex, indexAt(document.snapshot, position(1, 1)))
        val colors =
            document.definition.palette
                .entries()
                .map { it.color }
        assertEquals(listOf(red, black, green), colors)
        assertEquals(blackIndex, runtime.state.workspaceState.activePaletteIndex)
    }

    @Test
    fun `apply of an identity draft closes the session without a command`() {
        val runtime = openedPaletteSession()
        val before = historyPosition(runtime)
        edit(runtime, PaletteDraftOperation.SetSlotColor(redIndex, green))
        runtime.reduce(WorkspaceAction.UndoPaletteDraft)

        assertSame(PaletteApplyResult.NoChange, runtime.paletteOperations.applyPaletteDraft())

        assertEquals(before, historyPosition(runtime))
        assertEquals(defaultDefinition, runtime.state.documentState.definition)
        assertNull(runtime.state.workspaceState.paletteEditSession)
    }

    @Test
    fun `apply rejects a draft opened against a stale source token`() {
        val runtime = newRuntime()
        val current = runtime.read { transaction -> transaction.switchContext().source }
        val stale = current.copy(runtimeGeneration = current.runtimeGeneration + 1L)
        assertInstanceOf(
            WorkspaceReductionResult.Reduced::class.java,
            runtime.reduce(BeginPaletteEdit(stale, defaultDefinition)),
        )
        edit(runtime, PaletteDraftOperation.SetSlotColor(redIndex, green))
        val session = runtime.state.workspaceState.paletteEditSession
        val document = runtime.state.documentState

        assertEquals(
            PaletteApplyResult.Rejected(
                WorkspaceActionRejection.PaletteDraftRejected(PaletteDraftRejection.StaleBase),
            ),
            runtime.paletteOperations.applyPaletteDraft(),
        )

        assertSame(document, runtime.state.documentState)
        assertSame(session, runtime.state.workspaceState.paletteEditSession)
    }

    @Test
    fun `apply with a pending import is rejected and keeps the session`() {
        val runtime = openedPaletteSession()
        edit(runtime, PaletteDraftOperation.SetSlotColor(redIndex, green))
        assertInstanceOf(
            WorkspaceReductionResult.Reduced::class.java,
            runtime.reduce(WorkspaceAction.ImportPaletteDraft(definition(blackIndex, black, red))),
        )
        val session = runtime.state.workspaceState.paletteEditSession
        assertNotNull(session?.pendingImport)

        assertEquals(
            PaletteApplyResult.Rejected(
                WorkspaceActionRejection.PaletteDraftRejected(PaletteDraftRejection.ImportPending),
            ),
            runtime.paletteOperations.applyPaletteDraft(),
        )

        assertEquals(defaultDefinition, runtime.state.documentState.definition)
        assertSame(session, runtime.state.workspaceState.paletteEditSession)
    }

    @Test
    fun `apply without a palette session is rejected`() {
        val runtime = newRuntime()

        assertEquals(
            PaletteApplyResult.Rejected(WorkspaceActionRejection.NoPaletteSession),
            runtime.paletteOperations.applyPaletteDraft(),
        )
    }

    @Test
    fun `successful apply cancels a live gesture preview`() {
        val runtime = newRuntime()
        assertInstanceOf(
            WorkspaceReductionResult.Reduced::class.java,
            runtime.reduce(WorkspaceAction.BeginGesturePreview(runtime.state.documentState.size, position(0, 0))),
        )
        begin(runtime)
        edit(runtime, PaletteDraftOperation.SetSlotColor(redIndex, green))
        assertNotNull(runtime.state.workspaceState.preview)

        assertSame(PaletteApplyResult.Applied, runtime.paletteOperations.applyPaletteDraft())

        assertNull(runtime.state.workspaceState.preview)
        assertNull(runtime.state.workspaceState.paletteEditSession)
    }

    @Test
    fun `apply during a document switch is rejected and keeps the session`() {
        val runtime = openedPaletteSession()
        edit(runtime, PaletteDraftOperation.SetSlotColor(redIndex, green))
        val session = runtime.state.workspaceState.paletteEditSession
        assertNotNull(session)
        forceSwitching(runtime)

        assertEquals(
            PaletteApplyResult.Rejected(WorkspaceActionRejection.PersistenceBusy),
            runtime.paletteOperations.applyPaletteDraft(),
        )

        assertEquals(defaultDefinition, runtime.state.documentState.definition)
        assertSame(session, runtime.state.workspaceState.paletteEditSession)
    }

    private fun newRuntime(): EditorRuntime =
        EditorRuntime.create(canvas(2, 2), defaultDefinition, FixedDocumentIdSource())

    private fun openedPaletteSession(): EditorRuntime {
        val runtime = newRuntime()
        begin(runtime)
        return runtime
    }

    private fun begin(runtime: EditorRuntime) {
        assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, runtime.paletteOperations.beginPaletteEdit())
    }

    private fun edit(
        runtime: EditorRuntime,
        operation: PaletteDraftOperation,
    ) {
        assertInstanceOf(
            WorkspaceReductionResult.Reduced::class.java,
            runtime.reduce(WorkspaceAction.EditPaletteDraft(operation)),
        )
    }

    private fun onePixel(
        runtime: EditorRuntime,
        index: PaletteIndex,
    ): ApplyStrokeCommand =
        ApplyStrokeCommand.create(
            runtime.captureSource(),
            stroke(runtime.state.documentState.size, listOf(position(0, 0)), index),
        )

    private fun historyPosition(runtime: EditorRuntime): HistoryPosition =
        runtime.read { transaction -> transaction.historyPosition() }

    private fun forceSwitching(runtime: EditorRuntime) {
        runtime.transact { transaction ->
            val creation =
                assertInstanceOf(
                    OperationHandleCreation.Created::class.java,
                    transaction.coordination.nextOperationHandle(),
                )
            val candidate = RuntimeOwners.create(canvas(2, 2), defaultDefinition, FixedDocumentIdSource())
            val switching =
                ActivePersistenceOperation.Switch.Switching(creation.handle, candidate, SwitchKind.NewDocument)
            PersistenceTransition(creation.next.withActive(switching), Unit)
        }
    }
}

private class FixedDocumentIdSource : DocumentIdSource {
    override fun nextDocumentId(): DocumentId =
        when (val result = DocumentId.create("1".repeat(DOCUMENT_ID_LENGTH))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Document ID fixture was rejected: ${result.rejection}")
        }
}

private const val DOCUMENT_ID_LENGTH: Int = 32

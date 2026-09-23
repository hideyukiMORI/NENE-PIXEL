package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.RedoCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.command.ReplacePaletteCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.eraserStroke
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.greenIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class EditorRuntimeTest {
    private val toolDefinition = defaultDefinition

    @Test
    fun `palette only command advances dirty history autosave and invalidates full canvas`() {
        val runtime = EditorRuntime.create(canvas(2, 1), toolDefinition, SequentialDocumentIdSource())
        val target = definition(blackIndex, black, red, black)
        val remap =
            PaletteRemap
                .create(
                    toolDefinition,
                    target,
                    listOf(blackIndex, redIndex, greenIndex),
                ).value()

        val result =
            assertInstanceOf(
                CommandResult.Applied::class.java,
                runtime.execute(ReplacePaletteCommand.create(runtime.captureSource(), remap)),
            )

        assertEquals(1L, runtime.state.documentState.revision.value)
        assertEquals(target, runtime.state.documentState.definition)
        assertEquals(runtime.state.documentState.size, result.changeSet.renderInvalidation.size)
        assertEquals(DocumentDirtyState.Dirty, runtime.state.dirtyState)
        assertEquals(HistoryAvailability.UndoAvailable, runtime.state.historyAvailability)
        assertNotEquals(null, runtime.autosaveProjection.value.pendingStateToken)
    }

    @Test
    fun `initial runtime uses one canonical blank clean empty-history construction`() {
        val canvas = canvas(2, 3)
        val ids = SequentialDocumentIdSource()

        val state = EditorRuntime.create(canvas, toolDefinition, ids).state

        assertEquals(1, ids.callCount)
        assertEquals(ids.first, state.documentState.id)
        assertEquals(canvas, state.documentState.size)
        assertEquals(0L, state.documentState.revision.value)
        assertEquals(
            List(6) { 0 },
            state.documentState.snapshot
                .copyPackedIndices()
                .map { it.toInt() and 0xff },
        )
        assertEquals(HistoryAvailability.None, state.historyAvailability)
        assertEquals(DocumentDirtyState.Clean, state.dirtyState)
        assertEquals(paletteIndex(0), state.workspaceState.activePaletteIndex)
        assertEquals(DrawingTool.Pencil, state.workspaceState.activeTool)
        assertEquals(ViewportState.initial(canvas), state.workspaceState.viewport)
        assertNull(state.workspaceState.preview)
    }

    @Test
    fun `applied command is dirty undo to checkpoint is clean and redo is dirty`() {
        val runtime = EditorRuntime.create(canvas(2, 2), toolDefinition, SequentialDocumentIdSource())
        val initial = runtime.state
        val foreignRuntime = EditorRuntime.create(canvas(2, 2), toolDefinition, SequentialDocumentIdSource())
        val stroke = stroke(initial.documentState.size, listOf(position(0, 0)), redIndex)

        val rejected =
            runtime.execute(
                ApplyStrokeCommand.create(foreignRuntime.captureSource(), stroke),
            )
        assertEquals(
            RejectionReason.SourceOwnerMismatch,
            assertInstanceOf(CommandResult.Rejected::class.java, rejected).reason,
        )
        assertEquals(DocumentDirtyState.Clean, runtime.state.dirtyState)

        applyOnePixel(runtime)
        assertEquals(DocumentDirtyState.Dirty, runtime.state.dirtyState)

        val afterApply = runtime.state.documentState
        assertInstanceOf(
            CommandResult.Applied::class.java,
            runtime.execute(UndoCommand.create(afterApply.id, afterApply.revision)),
        )
        assertEquals(DocumentDirtyState.Clean, runtime.state.dirtyState)

        val afterUndo = runtime.state.documentState
        assertInstanceOf(
            CommandResult.Applied::class.java,
            runtime.execute(RedoCommand.create(afterUndo.id, afterUndo.revision)),
        )
        assertEquals(DocumentDirtyState.Dirty, runtime.state.dirtyState)
    }

    @Test
    fun `replacement branch stays dirty even when revision equals abandoned state`() {
        val runtime = EditorRuntime.create(canvas(2, 1), toolDefinition, SequentialDocumentIdSource())
        apply(runtime, position(0, 0), redIndex)
        val first = runtime.state.documentState
        apply(runtime, position(1, 0), greenIndex)
        val abandoned = runtime.state.documentState
        assertInstanceOf(
            CommandResult.Applied::class.java,
            runtime.execute(UndoCommand.create(abandoned.id, abandoned.revision)),
        )
        apply(runtime, position(1, 0), redIndex)

        assertEquals(abandoned.revision, runtime.state.documentState.revision)
        assertNotEquals(abandoned.snapshot, runtime.state.documentState.snapshot)
        assertEquals(DocumentDirtyState.Dirty, runtime.state.dirtyState)

        val branch = runtime.state.documentState
        assertInstanceOf(
            CommandResult.Applied::class.java,
            runtime.execute(UndoCommand.create(branch.id, branch.revision)),
        )
        assertEquals(first, runtime.state.documentState)
        assertEquals(DocumentDirtyState.Dirty, runtime.state.dirtyState)
        val afterFirstUndo = runtime.state.documentState
        assertInstanceOf(
            CommandResult.Applied::class.java,
            runtime.execute(UndoCommand.create(afterFirstUndo.id, afterFirstUndo.revision)),
        )
        assertEquals(DocumentDirtyState.Clean, runtime.state.dirtyState)
    }

    @Test
    fun `evicting the clean checkpoint keeps oldest reachable state dirty`() {
        val runtime = EditorRuntime.create(canvas(1, 1), toolDefinition, SequentialDocumentIdSource())

        repeat(PixelLimits.MAX_HISTORY_ENTRIES + 1) { index ->
            apply(runtime, position(0, 0), if (index % 2 == 0) redIndex else greenIndex)
        }
        repeat(PixelLimits.MAX_HISTORY_ENTRIES) {
            val current = runtime.state.documentState
            assertInstanceOf(
                CommandResult.Applied::class.java,
                runtime.execute(UndoCommand.create(current.id, current.revision)),
            )
        }

        assertEquals(1L, runtime.state.documentState.revision.value)
        assertEquals(HistoryAvailability.RedoAvailable, runtime.state.historyAvailability)
        assertEquals(DocumentDirtyState.Dirty, runtime.state.dirtyState)
    }

    @Test
    fun `already blank erase leaves clean runtime and empty history`() {
        val runtime = EditorRuntime.create(canvas(2, 2), toolDefinition, SequentialDocumentIdSource())
        val initial = runtime.state
        val result =
            runtime.execute(
                ApplyStrokeCommand.create(
                    runtime.captureSource(),
                    eraserStroke(initial.documentState.size, listOf(position(0, 0))),
                ),
            )
        val rejection = assertInstanceOf(CommandResult.Rejected::class.java, result)

        assertEquals(RejectionReason.NoEffectiveChange, rejection.reason)
        assertSame(initial.documentState, runtime.state.documentState)
        assertEquals(HistoryAvailability.None, runtime.state.historyAvailability)
        assertEquals(DocumentDirtyState.Clean, runtime.state.dirtyState)
    }

    @Test
    fun `palette selection changes no document history or dirty state`() {
        val runtime = EditorRuntime.create(canvas(2, 2), toolDefinition, SequentialDocumentIdSource())
        val before = runtime.state

        runtime.reduce(WorkspaceAction.SelectPaletteEntry(paletteIndex(1)))
        val after = runtime.state

        assertSame(before.documentState, after.documentState)
        assertEquals(before.historyAvailability, after.historyAvailability)
        assertEquals(before.dirtyState, after.dirtyState)
        assertEquals(paletteIndex(1), after.workspaceState.activePaletteIndex)
    }

    @Test
    fun `begin palette edit captures the runtime source token and document definition`() {
        val runtime = EditorRuntime.create(canvas(2, 2), toolDefinition, SequentialDocumentIdSource())
        applyOnePixel(runtime)
        val expectedBase = runtime.read { transaction -> transaction.switchContext().source }

        val result = assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, runtime.beginPaletteEdit())

        val session = result.nextState.paletteEditSession ?: fail("Palette session was not opened")
        assertEquals(expectedBase, session.base)
        assertSame(runtime.state.documentState.definition, session.draft)
        assertSame(result.nextState, runtime.state.workspaceState)
    }

    @Test
    fun `second begin palette edit is rejected and keeps the first session`() {
        val runtime = EditorRuntime.create(canvas(2, 2), toolDefinition, SequentialDocumentIdSource())
        runtime.beginPaletteEdit()
        val opened = runtime.state.workspaceState

        val result = assertInstanceOf(WorkspaceReductionResult.Rejected::class.java, runtime.beginPaletteEdit())

        assertEquals(WorkspaceActionRejection.PaletteSessionAlreadyActive, result.rejection)
        assertSame(opened, runtime.state.workspaceState)
    }

    private fun applyOnePixel(runtime: EditorRuntime) {
        apply(runtime, position(0, 0), redIndex)
    }

    private fun apply(
        runtime: EditorRuntime,
        position: io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition,
        index: PaletteIndex,
    ) {
        val target = runtime.state.documentState
        val result =
            runtime.execute(
                ApplyStrokeCommand.create(
                    runtime.captureSource(),
                    stroke(target.size, listOf(position), index),
                ),
            )
        assertInstanceOf(CommandResult.Applied::class.java, result)
    }
}

private class SequentialDocumentIdSource : DocumentIdSource {
    val first: DocumentId = documentId('1')
    val second: DocumentId = documentId('2')
    var callCount: Int = 0
        private set

    override fun nextDocumentId(): DocumentId {
        val next =
            when (callCount) {
                0 -> first
                1 -> second
                else -> documentId('3')
            }
        callCount += 1
        return next
    }
}

private fun documentId(character: Char): DocumentId =
    when (val result = DocumentId.create(character.toString().repeat(32))) {
        is DomainValueResult.Created -> result.value
        is DomainValueResult.Rejected -> fail("Document ID fixture was rejected: ${result.rejection}")
    }

private fun <T> DomainValueResult<T>.value(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> fail("Test value was rejected: $rejection")
    }

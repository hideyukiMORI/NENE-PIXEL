package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.colorAt
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.otherDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class CommandGatewayHistoryTest {
    @Test
    fun `apply undo redo use canonical patches and restore exact document states`() {
        val initial = state(canvas(2, 1), pixels = listOf(black, green))
        val gateway = CommandGateway.create(initial)

        val original = applied(gateway.execute(strokeCommand(initial, position(0, 0), red)))
        val afterStroke = gateway.runtimeState.documentState
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)

        val undo = applied(gateway.execute(UndoCommand.create(afterStroke.id, afterStroke.revision)))
        assertEquals(initial, gateway.runtimeState.documentState)
        assertEquals(original.inversePatch, undo.patch)
        assertEquals(original.patch, undo.inversePatch)
        assertEquals(original.renderInvalidation, undo.renderInvalidation)
        assertEquals(HistoryAvailability.RedoAvailable, gateway.runtimeState.historyAvailability)

        val redoState = gateway.runtimeState.documentState
        val redo = applied(gateway.execute(RedoCommand.create(redoState.id, redoState.revision)))
        assertEquals(afterStroke, gateway.runtimeState.documentState)
        assertEquals(original.patch, redo.patch)
        assertEquals(original.inversePatch, redo.inversePatch)
        assertEquals(original.renderInvalidation, redo.renderInvalidation)
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
    }

    @Test
    fun `empty history rejects undo and redo atomically`() {
        val initial = state(canvas(1, 1))
        val undoGateway = CommandGateway.create(initial)
        val redoGateway = CommandGateway.create(initial)

        assertEquals(
            RejectionReason.NoUndoAvailable,
            rejected(undoGateway.execute(UndoCommand.create(initial.id, initial.revision))),
        )
        assertEquals(initial, undoGateway.runtimeState.documentState)
        assertEquals(HistoryAvailability.None, undoGateway.runtimeState.historyAvailability)
        assertEquals(
            RejectionReason.NoRedoAvailable,
            rejected(redoGateway.execute(RedoCommand.create(initial.id, initial.revision))),
        )
        assertEquals(initial, redoGateway.runtimeState.documentState)
        assertEquals(HistoryAvailability.None, redoGateway.runtimeState.historyAvailability)
    }

    @Test
    fun `undo target and stale revision validation precede history availability`() {
        val initial = state(canvas(1, 1))
        val gateway = CommandGateway.create(initial)

        val targetMismatch = rejected(gateway.execute(UndoCommand.create(otherDocumentId, revision(1L))))
        val revisionMismatch = rejected(gateway.execute(UndoCommand.create(initial.id, revision(1L))))

        assertInstanceOf(RejectionReason.TargetDocumentMismatch::class.java, targetMismatch)
        assertInstanceOf(RejectionReason.RevisionMismatch::class.java, revisionMismatch)
        assertEquals(initial, gateway.runtimeState.documentState)
        assertEquals(HistoryAvailability.None, gateway.runtimeState.historyAvailability)
    }

    @Test
    fun `redo target and stale revision validation precede history availability`() {
        val initial = state(canvas(1, 1))
        val gateway = CommandGateway.create(initial)

        val targetMismatch = rejected(gateway.execute(RedoCommand.create(otherDocumentId, revision(1L))))
        val revisionMismatch = rejected(gateway.execute(RedoCommand.create(initial.id, revision(1L))))

        assertInstanceOf(RejectionReason.TargetDocumentMismatch::class.java, targetMismatch)
        assertInstanceOf(RejectionReason.RevisionMismatch::class.java, revisionMismatch)
        assertEquals(initial, gateway.runtimeState.documentState)
        assertEquals(HistoryAvailability.None, gateway.runtimeState.historyAvailability)
    }

    @Test
    fun `one level history replaces the prior entry`() {
        val initial = state(canvas(2, 1))
        val gateway = CommandGateway.create(initial)
        applied(gateway.execute(strokeCommand(initial, position(0, 0), red)))
        val afterFirst = gateway.runtimeState.documentState
        applied(gateway.execute(strokeCommand(afterFirst, position(1, 0), green)))
        val afterSecond = gateway.runtimeState.documentState

        applied(gateway.execute(UndoCommand.create(afterSecond.id, afterSecond.revision)))

        assertEquals(afterFirst, gateway.runtimeState.documentState)
        assertEquals(red, colorAt(gateway.runtimeState.documentState.snapshot, position(0, 0)))
        assertEquals(black, colorAt(gateway.runtimeState.documentState.snapshot, position(1, 0)))
    }

    @Test
    fun `successful new stroke after undo clears redo`() {
        val initial = state(canvas(2, 1))
        val gateway = CommandGateway.create(initial)
        applied(gateway.execute(strokeCommand(initial, position(0, 0), red)))
        val afterStroke = gateway.runtimeState.documentState
        applied(gateway.execute(UndoCommand.create(afterStroke.id, afterStroke.revision)))
        val afterUndo = gateway.runtimeState.documentState

        applied(gateway.execute(strokeCommand(afterUndo, position(1, 0), green)))

        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
        assertEquals(
            RejectionReason.NoRedoAvailable,
            rejected(
                gateway.execute(
                    RedoCommand.create(
                        gateway.runtimeState.documentState.id,
                        gateway.runtimeState.documentState.revision,
                    ),
                ),
            ),
        )
        assertEquals(black, colorAt(gateway.runtimeState.documentState.snapshot, position(0, 0)))
        assertEquals(green, colorAt(gateway.runtimeState.documentState.snapshot, position(1, 0)))
    }

    @Test
    fun `repeated undo and redo reject without moving history or document`() {
        val initial = state(canvas(1, 1))
        val gateway = CommandGateway.create(initial)
        applied(gateway.execute(strokeCommand(initial, position(0, 0), red)))
        val afterStroke = gateway.runtimeState.documentState
        applied(gateway.execute(UndoCommand.create(afterStroke.id, afterStroke.revision)))
        val afterUndo = gateway.runtimeState

        assertEquals(
            RejectionReason.NoUndoAvailable,
            rejected(gateway.execute(UndoCommand.create(initial.id, initial.revision))),
        )
        assertEquals(afterUndo, gateway.runtimeState)
        applied(gateway.execute(RedoCommand.create(initial.id, initial.revision)))
        val afterRedo = gateway.runtimeState
        assertEquals(
            RejectionReason.NoRedoAvailable,
            rejected(gateway.execute(RedoCommand.create(afterStroke.id, afterStroke.revision))),
        )
        assertEquals(afterRedo, gateway.runtimeState)
    }

    @Test
    fun `rejected new stroke after undo preserves redo`() {
        val initial = state(canvas(1, 1))
        val gateway = CommandGateway.create(initial)
        applied(gateway.execute(strokeCommand(initial, position(0, 0), red)))
        applied(gateway.execute(UndoCommand.create(initial.id, revision(1L))))
        val afterUndo = gateway.runtimeState.documentState

        assertEquals(
            RejectionReason.NoEffectiveChange,
            rejected(gateway.execute(strokeCommand(afterUndo, position(0, 0), black))),
        )
        assertEquals(HistoryAvailability.RedoAvailable, gateway.runtimeState.historyAvailability)
        applied(gateway.execute(RedoCommand.create(afterUndo.id, afterUndo.revision)))
        assertEquals(red, colorAt(gateway.runtimeState.documentState.snapshot, position(0, 0)))
    }

    @Test
    fun `apply undo redo replay is deterministic`() {
        val initial = state(canvas(1, 1))
        val first = replay(initial)
        val second = replay(initial)

        assertEquals(first, second)
    }

    private fun replay(initial: DocumentState): ReplayOutcome {
        val gateway = CommandGateway.create(initial)
        val applyResult = gateway.execute(strokeCommand(initial, position(0, 0), red))
        val afterApply = gateway.runtimeState.documentState
        val undoResult = gateway.execute(UndoCommand.create(afterApply.id, afterApply.revision))
        val afterUndo = gateway.runtimeState.documentState
        val redoResult = gateway.execute(RedoCommand.create(afterUndo.id, afterUndo.revision))
        return ReplayOutcome(
            results = listOf(applyResult, undoResult, redoResult),
            state = gateway.runtimeState.documentState,
            availability = gateway.runtimeState.historyAvailability,
        )
    }

    private fun strokeCommand(
        state: DocumentState,
        position: PixelPosition,
        color: io.github.hideyukimori.nenepixel.core.domain.color.PixelColor,
    ): ApplyStrokeCommand =
        ApplyStrokeCommand.create(
            state.id,
            state.revision,
            stroke(state.size, listOf(position), color),
        )

    private data class ReplayOutcome(
        val results: List<CommandResult>,
        val state: DocumentState,
        val availability: HistoryAvailability,
    )
}

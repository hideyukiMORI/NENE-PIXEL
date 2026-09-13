package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.greenIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.indexAt
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.otherDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.document.transition.ChangeSet
import io.github.hideyukimori.nenepixel.core.application.document.transition.IndexChanges
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class CommandGatewayHistoryTest {
    @Test
    fun `admission revives only at exact undo position and stays stale on replacement branch`() {
        val initial = state(canvas(3, 1))
        val gateway = CommandGateway.create(initial)
        val initialAdmission = gateway.captureSource()
        applied(gateway.execute(strokeCommand(gateway, position(0, 0), redIndex)))
        val afterFirst = gateway.runtimeState.documentState
        val restoredAdmission = gateway.captureSource()
        applied(gateway.execute(strokeCommand(gateway, position(1, 0), greenIndex)))
        val afterSecond = gateway.runtimeState.documentState
        val abandonedAdmission = gateway.captureSource()

        applied(gateway.execute(UndoCommand.create(afterSecond.id, afterSecond.revision)))
        applied(
            gateway.execute(
                capturedStroke(restoredAdmission, position(2, 0), greenIndex),
            ),
        )
        assertEquals(
            RejectionReason.SourceHistoryMismatch,
            rejected(
                gateway.execute(
                    capturedStroke(abandonedAdmission, position(2, 0), redIndex),
                ),
            ),
        )

        val branch = gateway.runtimeState.documentState
        applied(gateway.execute(UndoCommand.create(branch.id, branch.revision)))
        applied(gateway.execute(UndoCommand.create(afterFirst.id, afterFirst.revision)))
        applied(
            gateway.execute(
                capturedStroke(initialAdmission, position(2, 0), redIndex),
            ),
        )
    }

    private fun capturedStroke(
        admission: CommandSourceAdmission,
        position: PixelPosition,
        index: io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex,
    ): ApplyStrokeCommand =
        ApplyStrokeCommand.create(admission, stroke(admission.document.size, listOf(position), index))

    @Test
    fun `apply undo redo use canonical patches and restore exact document states`() {
        val initial = state(canvas(2, 1), indices = listOf(blackIndex, greenIndex))
        val gateway = CommandGateway.create(initial)

        val original = applied(gateway.execute(strokeCommand(gateway, position(0, 0), redIndex)))
        val afterStroke = gateway.runtimeState.documentState
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)

        val undo = applied(gateway.execute(UndoCommand.create(afterStroke.id, afterStroke.revision)))
        assertEquals(initial, gateway.runtimeState.documentState)
        assertEquals(changedPatch(original).inverse(), changedPatch(undo))
        assertEquals(original.renderInvalidation, undo.renderInvalidation)
        assertEquals(HistoryAvailability.RedoAvailable, gateway.runtimeState.historyAvailability)

        val redoState = gateway.runtimeState.documentState
        val redo = applied(gateway.execute(RedoCommand.create(redoState.id, redoState.revision)))
        assertEquals(afterStroke, gateway.runtimeState.documentState)
        assertEquals(changedPatch(original), changedPatch(redo))
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
    fun `multiple steps expose undo and redo together and restore every exact state`() {
        val initial = state(canvas(3, 1))
        val gateway = CommandGateway.create(initial)
        applied(gateway.execute(strokeCommand(gateway, position(0, 0), redIndex)))
        val afterFirst = gateway.runtimeState.documentState
        applied(gateway.execute(strokeCommand(gateway, position(1, 0), greenIndex)))
        val afterSecond = gateway.runtimeState.documentState
        applied(gateway.execute(strokeCommand(gateway, position(2, 0), redIndex)))
        val afterThird = gateway.runtimeState.documentState

        applied(gateway.execute(UndoCommand.create(afterThird.id, afterThird.revision)))
        assertEquals(afterSecond, gateway.runtimeState.documentState)
        assertEquals(HistoryAvailability.UndoAndRedoAvailable, gateway.runtimeState.historyAvailability)

        applied(gateway.execute(UndoCommand.create(afterSecond.id, afterSecond.revision)))
        assertEquals(afterFirst, gateway.runtimeState.documentState)
        assertEquals(HistoryAvailability.UndoAndRedoAvailable, gateway.runtimeState.historyAvailability)

        applied(gateway.execute(RedoCommand.create(afterFirst.id, afterFirst.revision)))
        assertEquals(afterSecond, gateway.runtimeState.documentState)
        assertEquals(HistoryAvailability.UndoAndRedoAvailable, gateway.runtimeState.historyAvailability)

        applied(gateway.execute(UndoCommand.create(afterSecond.id, afterSecond.revision)))
        assertEquals(afterFirst, gateway.runtimeState.documentState)
        applied(gateway.execute(UndoCommand.create(afterFirst.id, afterFirst.revision)))
        assertEquals(initial, gateway.runtimeState.documentState)
        assertEquals(HistoryAvailability.RedoAvailable, gateway.runtimeState.historyAvailability)
    }

    @Test
    fun `successful new stroke after undo clears redo`() {
        val initial = state(canvas(2, 1))
        val gateway = CommandGateway.create(initial)
        applied(gateway.execute(strokeCommand(gateway, position(0, 0), redIndex)))
        val afterStroke = gateway.runtimeState.documentState
        applied(gateway.execute(UndoCommand.create(afterStroke.id, afterStroke.revision)))
        val afterUndo = gateway.runtimeState.documentState

        applied(gateway.execute(strokeCommand(gateway, position(1, 0), greenIndex)))

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
        assertEquals(blackIndex, indexAt(gateway.runtimeState.documentState.snapshot, position(0, 0)))
        assertEquals(greenIndex, indexAt(gateway.runtimeState.documentState.snapshot, position(1, 0)))
    }

    @Test
    fun `branch after undo keeps a unique history position despite reusing revision`() {
        val initial = state(canvas(3, 1))
        val gateway = CommandGateway.create(initial)
        applied(gateway.execute(strokeCommand(gateway, position(0, 0), redIndex)))
        val afterFirst = gateway.runtimeState.documentState
        applied(gateway.execute(strokeCommand(gateway, position(1, 0), greenIndex)))
        val abandoned = gateway.runtimeState.documentState
        applied(gateway.execute(UndoCommand.create(abandoned.id, abandoned.revision)))

        applied(gateway.execute(strokeCommand(gateway, position(2, 0), greenIndex)))
        val branched = gateway.runtimeState.documentState

        assertEquals(abandoned.revision, branched.revision)
        assertNotEquals(abandoned.snapshot, branched.snapshot)
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
        assertEquals(
            RejectionReason.NoRedoAvailable,
            rejected(gateway.execute(RedoCommand.create(branched.id, branched.revision))),
        )
        applied(gateway.execute(UndoCommand.create(branched.id, branched.revision)))
        assertEquals(afterFirst, gateway.runtimeState.documentState)
        applied(gateway.execute(RedoCommand.create(afterFirst.id, afterFirst.revision)))
        assertEquals(branched, gateway.runtimeState.documentState)
    }

    @Test
    fun `entry cap evicts the oldest command and keeps exactly sixty four undo steps`() {
        val initial = state(canvas(1, 1))
        val gateway = CommandGateway.create(initial)

        repeat(PixelLimits.MAX_HISTORY_ENTRIES + 1) { index ->
            val current = gateway.runtimeState.documentState
            val color = if (index % 2 == 0) redIndex else greenIndex
            applied(gateway.execute(strokeCommand(gateway, position(0, 0), color)))
        }

        assertEquals(PixelLimits.MAX_HISTORY_ENTRIES, gateway.runtimeState.historyEntryCount)
        assertEquals(PixelLimits.MAX_HISTORY_ENTRIES, gateway.runtimeState.retainedHistoryChangeCount)
        repeat(PixelLimits.MAX_HISTORY_ENTRIES) {
            val current = gateway.runtimeState.documentState
            applied(gateway.execute(UndoCommand.create(current.id, current.revision)))
        }
        assertEquals(1L, gateway.runtimeState.documentState.revision.value)
        assertEquals(redIndex, indexAt(gateway.runtimeState.documentState.snapshot, position(0, 0)))
        assertEquals(
            RejectionReason.NoUndoAvailable,
            rejected(
                gateway.execute(
                    UndoCommand.create(
                        gateway.runtimeState.documentState.id,
                        gateway.runtimeState.documentState.revision,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `retained change workload stays at policy cap and evicts one full canvas entry`() {
        val size = canvas(PixelLimits.MAX_CANVAS_AXIS, PixelLimits.MAX_CANVAS_AXIS)
        val initial = state(size)
        val gateway = CommandGateway.create(initial)
        val fullCanvasPath = fullCanvasPath()

        repeat(9) { index ->
            val current = gateway.runtimeState.documentState
            val color = if (index % 2 == 0) redIndex else greenIndex
            applied(
                gateway.execute(
                    ApplyStrokeCommand.create(
                        gateway.captureSource(),
                        stroke(size, fullCanvasPath, color),
                    ),
                ),
            )
        }

        assertEquals(8, gateway.runtimeState.historyEntryCount)
        assertEquals(PixelLimits.MAX_RETAINED_CHANGES, gateway.runtimeState.retainedHistoryChangeCount)
        repeat(8) {
            val current = gateway.runtimeState.documentState
            applied(gateway.execute(UndoCommand.create(current.id, current.revision)))
        }
        assertEquals(1L, gateway.runtimeState.documentState.revision.value)
        assertEquals(redIndex, indexAt(gateway.runtimeState.documentState.snapshot, position(0, 0)))
        val oldestRetained = gateway.runtimeState.documentState
        assertEquals(
            RejectionReason.NoUndoAvailable,
            rejected(gateway.execute(UndoCommand.create(oldestRetained.id, oldestRetained.revision))),
        )
    }

    @Test
    fun `repeated undo and redo reject without moving history or document`() {
        val initial = state(canvas(1, 1))
        val gateway = CommandGateway.create(initial)
        applied(gateway.execute(strokeCommand(gateway, position(0, 0), redIndex)))
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
        applied(gateway.execute(strokeCommand(gateway, position(0, 0), redIndex)))
        applied(gateway.execute(UndoCommand.create(initial.id, revision(1L))))
        val afterUndo = gateway.runtimeState.documentState

        assertEquals(
            RejectionReason.NoEffectiveChange,
            rejected(gateway.execute(strokeCommand(gateway, position(0, 0), blackIndex))),
        )
        assertEquals(HistoryAvailability.RedoAvailable, gateway.runtimeState.historyAvailability)
        applied(gateway.execute(RedoCommand.create(afterUndo.id, afterUndo.revision)))
        assertEquals(redIndex, indexAt(gateway.runtimeState.documentState.snapshot, position(0, 0)))
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
        val applyResult = gateway.execute(strokeCommand(gateway, position(0, 0), redIndex))
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
        gateway: CommandGateway,
        position: PixelPosition,
        index: io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex,
    ): ApplyStrokeCommand =
        ApplyStrokeCommand.create(
            gateway.captureSource(),
            stroke(gateway.runtimeState.documentState.size, listOf(position), index),
        )

    private fun changedPatch(changeSet: ChangeSet) = (changeSet.indexChanges as IndexChanges.Changed).patch

    private fun fullCanvasPath(): List<PixelPosition> =
        List(PixelLimits.MAX_CANVAS_PIXELS) { index ->
            position(index % PixelLimits.MAX_CANVAS_AXIS, index / PixelLimits.MAX_CANVAS_AXIS)
        }

    private data class ReplayOutcome(
        val results: List<CommandResult>,
        val state: DocumentState,
        val availability: HistoryAvailability,
    )
}

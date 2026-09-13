package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryEntry
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.greenIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class UndoRedoCommandHandlerTest {
    @Test
    fun `undo recorded patch canvas mismatch is typed and atomic`() {
        val fixture = historyFixture()
        val current = state(canvas(2, 1), revision(1L))

        val reason =
            assertRejected(
                UndoCommandHandler().execute(
                    current,
                    UndoCommand.create(current.id, current.revision),
                    fixture.entry,
                ),
            )

        assertInstanceOf(RejectionReason.CanvasMismatch::class.java, reason)
        assertEquals(revision(1L), current.revision)
    }

    @Test
    fun `redo recorded patch canvas mismatch is typed and atomic`() {
        val fixture = historyFixture()
        val current = state(canvas(2, 1))

        val reason =
            assertRejected(
                RedoCommandHandler().execute(
                    current,
                    RedoCommand.create(current.id, current.revision),
                    fixture.entry,
                ),
            )

        assertInstanceOf(RejectionReason.CanvasMismatch::class.java, reason)
        assertEquals(revision(0L), current.revision)
    }

    @Test
    fun `undo recorded patch revision mismatch is typed and atomic`() {
        val fixture = historyFixture()
        val current = state(fixture.initial.size, revision(2L))

        val reason =
            assertRejected(
                UndoCommandHandler().execute(
                    current,
                    UndoCommand.create(current.id, current.revision),
                    fixture.entry,
                ),
            )

        val mismatch = assertInstanceOf(RejectionReason.RevisionMismatch::class.java, reason)
        assertEquals(revision(1L), mismatch.expected)
        assertEquals(revision(2L), mismatch.actual)
        assertEquals(revision(2L), current.revision)
    }

    @Test
    fun `redo recorded patch revision mismatch is typed and atomic`() {
        val fixture = historyFixture()
        val current = state(fixture.initial.size, revision(2L))

        val reason =
            assertRejected(
                RedoCommandHandler().execute(
                    current,
                    RedoCommand.create(current.id, current.revision),
                    fixture.entry,
                ),
            )

        val mismatch = assertInstanceOf(RejectionReason.RevisionMismatch::class.java, reason)
        assertEquals(revision(0L), mismatch.expected)
        assertEquals(revision(2L), mismatch.actual)
        assertEquals(revision(2L), current.revision)
    }

    @Test
    fun `undo rejects a conflicting current pixel atomically`() {
        val fixture = historyFixture()
        val conflicting = state(fixture.initial.size, revision(1L), listOf(greenIndex))
        val command = UndoCommand.create(conflicting.id, conflicting.revision)

        val result = UndoCommandHandler().execute(conflicting, command, fixture.entry)

        val reason = assertRejected(result)
        val mismatch = assertInstanceOf(RejectionReason.PixelBeforeValueMismatch::class.java, reason)
        assertEquals(position(0, 0), mismatch.position)
        assertEquals(redIndex, mismatch.expected)
        assertEquals(greenIndex, mismatch.actual)
        assertEquals(revision(1L), conflicting.revision)
    }

    @Test
    fun `redo rejects a conflicting current pixel atomically`() {
        val fixture = historyFixture()
        val conflicting = state(fixture.initial.size, indices = listOf(greenIndex))
        val command = RedoCommand.create(conflicting.id, conflicting.revision)

        val result = RedoCommandHandler().execute(conflicting, command, fixture.entry)

        val reason = assertRejected(result)
        val mismatch = assertInstanceOf(RejectionReason.PixelBeforeValueMismatch::class.java, reason)
        assertEquals(position(0, 0), mismatch.position)
        assertEquals(blackIndex, mismatch.expected)
        assertEquals(greenIndex, mismatch.actual)
        assertEquals(revision(0L), conflicting.revision)
    }

    private fun historyFixture(): HandlerFixture {
        val initial = state(canvas(1, 1))
        val gateway = CommandGateway.create(initial)
        val command =
            ApplyStrokeCommand.create(
                gateway.captureSource(),
                stroke(initial.size, listOf(position(0, 0)), redIndex),
            )
        val result = gateway.execute(command)
        val appliedResult = assertInstanceOf(CommandResult.Applied::class.java, result)
        return HandlerFixture(
            initial,
            HistoryEntry.create(
                appliedResult,
                HistoryPosition.initial,
                HistoryPosition.create(1L),
            ),
        )
    }

    private fun assertRejected(result: DocumentTransitionResult): RejectionReason =
        assertInstanceOf(DocumentTransitionResult.Rejected::class.java, result).reason

    private data class HandlerFixture(
        val initial: io.github.hideyukimori.nenepixel.core.domain.document.DocumentState,
        val entry: HistoryEntry,
    )
}

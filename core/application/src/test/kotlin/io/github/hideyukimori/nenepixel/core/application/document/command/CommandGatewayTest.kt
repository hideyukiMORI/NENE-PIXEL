package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.eraserStroke
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.greenIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.indexAt
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.document.transition.IndexChanges
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatch
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchApplicationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class CommandGatewayTest {
    @Test
    fun `valid command commits one complete deterministic transition`() {
        val initial = state(canvas(3, 1), indices = listOf(blackIndex, greenIndex, blackIndex))
        val firstGateway = CommandGateway.create(initial)
        val secondGateway = CommandGateway.create(initial)
        val draw = stroke(initial.size, listOf(position(2, 0), position(0, 0)), redIndex)

        val firstResult = firstGateway.execute(command(firstGateway, draw))
        val secondResult = secondGateway.execute(command(secondGateway, draw))
        val changeSet = applied(firstResult)
        val forward = changedPatch(changeSet.indexChanges)
        val restored = appliedSnapshot(forward.inverse().applyTo(firstGateway.runtimeState.documentState.snapshot))

        assertEquals(firstResult, secondResult)
        assertEquals(firstGateway.runtimeState.documentState, secondGateway.runtimeState.documentState)
        assertEquals(revision(0), changeSet.beforeRevision)
        assertEquals(revision(1), changeSet.afterRevision)
        assertEquals(redIndex, indexAt(firstGateway.runtimeState.documentState.snapshot, position(0, 0)))
        assertEquals(greenIndex, indexAt(firstGateway.runtimeState.documentState.snapshot, position(1, 0)))
        assertEquals(redIndex, indexAt(firstGateway.runtimeState.documentState.snapshot, position(2, 0)))
        assertEquals(initial.snapshot, restored)
    }

    @Test
    fun `cross gateway admission is rejected even for same document instance`() {
        val initial = state(canvas(1, 1))
        val first = CommandGateway.create(initial)
        val second = CommandGateway.create(initial)
        val foreign = command(first, stroke(initial.size, listOf(position(0, 0)), redIndex))

        assertEquals(RejectionReason.SourceOwnerMismatch, rejected(second.execute(foreign)))
        assertEquals(initial, second.runtimeState.documentState)
    }

    @Test
    fun `stroke canvas mismatch and no effective change are typed and atomic`() {
        val initial = state(canvas(1, 1))
        val gateway = CommandGateway.create(initial)
        val larger = canvas(2, 1)
        val mismatch = command(gateway, stroke(larger, listOf(position(0, 0)), redIndex))
        assertInstanceOf(RejectionReason.CanvasMismatch::class.java, rejected(gateway.execute(mismatch)))

        val noChange = command(gateway, stroke(initial.size, listOf(position(0, 0)), blackIndex))
        assertEquals(RejectionReason.NoEffectiveChange, rejected(gateway.execute(noChange)))
        assertEquals(initial, gateway.runtimeState.documentState)
    }

    @Test
    fun `eraser applies captured default and undo redo replay the recorded transition`() {
        val initial = state(canvas(2, 1), indices = listOf(redIndex, greenIndex))
        val gateway = CommandGateway.create(initial)
        applied(
            gateway.execute(
                command(gateway, eraserStroke(initial.size, listOf(position(1, 0), position(0, 0)))),
            ),
        )
        val erased = gateway.runtimeState.documentState

        assertEquals(blackIndex, indexAt(erased.snapshot, position(0, 0)))
        assertEquals(blackIndex, indexAt(erased.snapshot, position(1, 0)))
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
        applied(gateway.execute(UndoCommand.create(erased.id, erased.revision)))
        assertEquals(initial, gateway.runtimeState.documentState)
        applied(gateway.execute(RedoCommand.create(initial.id, initial.revision)))
        assertEquals(erased, gateway.runtimeState.documentState)
    }

    @Test
    fun `effective change at maximum revision rejects atomically`() {
        val initial = state(canvas(1, 1), revision(Long.MAX_VALUE))
        val gateway = CommandGateway.create(initial)
        assertEquals(
            RejectionReason.RevisionOverflow,
            rejected(gateway.execute(command(gateway, stroke(initial.size, listOf(position(0, 0)), redIndex)))),
        )
        assertEquals(initial, gateway.runtimeState.documentState)
    }

    @Test
    fun `stale command cannot overwrite and fresh admission can continue`() {
        val initial = state(canvas(1, 1))
        val gateway = CommandGateway.create(initial)
        val redCommand = command(gateway, stroke(initial.size, listOf(position(0, 0)), redIndex))
        val staleGreen = command(gateway, stroke(initial.size, listOf(position(0, 0)), greenIndex))
        applied(gateway.execute(redCommand))
        assertEquals(RejectionReason.SourceHistoryMismatch, rejected(gateway.execute(staleGreen)))
        applied(gateway.execute(command(gateway, stroke(initial.size, listOf(position(0, 0)), greenIndex))))
        assertEquals(greenIndex, indexAt(gateway.runtimeState.documentState.snapshot, position(0, 0)))
    }

    @Test
    fun `concurrent commands serialize into one commit`() {
        repeat(CONCURRENCY_ATTEMPTS) {
            val initial = state(canvas(2, 1))
            val gateway = CommandGateway.create(initial)
            val admission = gateway.captureSource()
            val first = ApplyStrokeCommand.create(admission, stroke(initial.size, listOf(position(0, 0)), redIndex))
            val second = ApplyStrokeCommand.create(admission, stroke(initial.size, listOf(position(1, 0)), greenIndex))
            val results = executeConcurrently(gateway, first, second)

            assertEquals(1, results.count { it is CommandResult.Applied })
            assertEquals(1, results.count { it is CommandResult.Rejected })
            assertEquals(
                RejectionReason.SourceHistoryMismatch,
                rejected(results.single { it is CommandResult.Rejected }),
            )
            assertEquals(revision(1), gateway.runtimeState.documentState.revision)
            assertTrue(
                indexAt(gateway.runtimeState.documentState.snapshot, position(0, 0)) == redIndex ||
                    indexAt(gateway.runtimeState.documentState.snapshot, position(1, 0)) == greenIndex,
            )
        }
    }

    private fun command(
        gateway: CommandGateway,
        stroke: io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke,
    ): ApplyStrokeCommand = ApplyStrokeCommand.create(gateway.captureSource(), stroke)

    private fun changedPatch(changes: IndexChanges): PixelPatch =
        assertInstanceOf(IndexChanges.Changed::class.java, changes).patch

    private fun appliedSnapshot(result: PixelPatchApplicationResult): PixelSnapshot =
        when (result) {
            is PixelPatchApplicationResult.Applied -> result.snapshot
            is PixelPatchApplicationResult.Rejected -> fail("Patch rejected: ${result.rejection}")
        }

    private fun executeConcurrently(
        gateway: CommandGateway,
        first: ApplyStrokeCommand,
        second: ApplyStrokeCommand,
    ): List<CommandResult> {
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        return try {
            val firstResult =
                executor.submit<CommandResult> {
                    start.await()
                    gateway.execute(first)
                }
            val secondResult =
                executor.submit<CommandResult> {
                    start.await()
                    gateway.execute(second)
                }
            start.countDown()
            listOf(
                firstResult.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                secondResult.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
        } finally {
            executor.shutdownNow()
        }
    }

    private companion object {
        const val CONCURRENCY_ATTEMPTS: Int = 20
        const val TIMEOUT_SECONDS: Long = 5
    }
}

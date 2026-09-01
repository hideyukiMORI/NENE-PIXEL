package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.colorAt
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.otherDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
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
        val initial = state(canvas(3, 1), pixels = listOf(black, green, black))
        val command =
            command(
                initial,
                stroke(initial.size, listOf(position(2, 0), position(0, 0)), red),
            )
        val firstGateway = CommandGateway.create(initial)
        val secondGateway = CommandGateway.create(initial)

        val firstResult = firstGateway.execute(command)
        val secondResult = secondGateway.execute(command)
        val changeSet = applied(firstResult)
        val restored = appliedSnapshot(changeSet.inversePatch.applyTo(firstGateway.runtimeState.documentState.snapshot))

        assertEquals(firstResult, secondResult)
        assertEquals(firstGateway.runtimeState.documentState, secondGateway.runtimeState.documentState)
        assertEquals(revision(0L), changeSet.beforeRevision)
        assertEquals(revision(1L), changeSet.afterRevision)
        assertEquals(region(initial, position(0, 0), canvas(3, 1)), changeSet.renderInvalidation)
        assertEquals(red, colorAt(firstGateway.runtimeState.documentState.snapshot, position(0, 0)))
        assertEquals(green, colorAt(firstGateway.runtimeState.documentState.snapshot, position(1, 0)))
        assertEquals(red, colorAt(firstGateway.runtimeState.documentState.snapshot, position(2, 0)))
        assertEquals(initial.snapshot, restored)
        assertEquals(
            firstGateway.runtimeState.documentState.snapshot,
            appliedSnapshot(changeSet.patch.applyTo(restored)),
        )
    }

    @Test
    fun `target document mismatch is typed and atomic`() {
        val initial = state(canvas(1, 1))
        val gateway = CommandGateway.create(initial)
        val command =
            ApplyStrokeCommand.create(
                targetDocumentId = otherDocumentId,
                targetRevision = initial.revision,
                stroke = stroke(initial.size, listOf(position(0, 0)), red),
            )

        val reason = rejected(gateway.execute(command))

        val mismatch = assertInstanceOf(RejectionReason.TargetDocumentMismatch::class.java, reason)
        assertEquals(otherDocumentId, mismatch.expected)
        assertEquals(defaultDocumentId, mismatch.actual)
        assertEquals(initial, gateway.runtimeState.documentState)
    }

    @Test
    fun `target revision mismatch is typed and atomic`() {
        val initial = state(canvas(1, 1), revision(2L))
        val gateway = CommandGateway.create(initial)
        val command =
            ApplyStrokeCommand.create(
                targetDocumentId = initial.id,
                targetRevision = revision(1L),
                stroke = stroke(initial.size, listOf(position(0, 0)), red),
            )

        val reason = rejected(gateway.execute(command))

        val mismatch = assertInstanceOf(RejectionReason.RevisionMismatch::class.java, reason)
        assertEquals(revision(1L), mismatch.expected)
        assertEquals(revision(2L), mismatch.actual)
        assertEquals(initial, gateway.runtimeState.documentState)
    }

    @Test
    fun `stroke canvas mismatch is typed and atomic`() {
        val initial = state(canvas(1, 1))
        val gateway = CommandGateway.create(initial)
        val largerCanvas = canvas(2, 1)
        val command =
            command(
                initial,
                stroke(largerCanvas, listOf(position(0, 0), position(1, 0)), red),
            )

        val reason = rejected(gateway.execute(command))

        val mismatch = assertInstanceOf(RejectionReason.CanvasMismatch::class.java, reason)
        assertEquals(largerCanvas, mismatch.expected)
        assertEquals(initial.size, mismatch.actual)
        assertEquals(initial, gateway.runtimeState.documentState)
    }

    @Test
    fun `no effective change has one typed result and no commit`() {
        val initial = state(canvas(1, 1), revision(Long.MAX_VALUE), listOf(red))
        val gateway = CommandGateway.create(initial)
        val command = command(initial, stroke(initial.size, listOf(position(0, 0), position(0, 0)), red))

        assertEquals(RejectionReason.NoEffectiveChange, rejected(gateway.execute(command)))
        assertEquals(initial, gateway.runtimeState.documentState)
    }

    @Test
    fun `effective change at maximum revision rejects atomically`() {
        val initial = state(canvas(1, 1), revision(Long.MAX_VALUE))
        val gateway = CommandGateway.create(initial)
        val command = command(initial, stroke(initial.size, listOf(position(0, 0)), red))

        assertEquals(RejectionReason.RevisionOverflow, rejected(gateway.execute(command)))
        assertEquals(initial, gateway.runtimeState.documentState)
    }

    @Test
    fun `sequential overlap is ordered and a stale command cannot overwrite`() {
        val initial = state(canvas(1, 1))
        val gateway = CommandGateway.create(initial)
        val redCommand = command(initial, stroke(initial.size, listOf(position(0, 0)), red))
        val staleGreenCommand = command(initial, stroke(initial.size, listOf(position(0, 0)), green))
        val currentGreenCommand =
            ApplyStrokeCommand.create(
                initial.id,
                revision(1L),
                stroke(initial.size, listOf(position(0, 0)), green),
            )

        applied(gateway.execute(redCommand))
        assertInstanceOf(RejectionReason.RevisionMismatch::class.java, rejected(gateway.execute(staleGreenCommand)))
        applied(gateway.execute(currentGreenCommand))

        assertEquals(revision(2L), gateway.runtimeState.documentState.revision)
        assertEquals(green, colorAt(gateway.runtimeState.documentState.snapshot, position(0, 0)))
    }

    @Test
    fun `concurrent commands are serialized into one commit sequence`() {
        repeat(CONCURRENCY_ATTEMPTS) {
            val initial = state(canvas(2, 1))
            val gateway = CommandGateway.create(initial)
            val redCommand =
                command(initial, stroke(initial.size, listOf(position(0, 0), position(1, 0)), red))
            val greenCommand =
                command(initial, stroke(initial.size, listOf(position(0, 0), position(1, 0)), green))
            val results = executeConcurrently(gateway, redCommand, greenCommand)

            assertEquals(1, results.count { result -> result is CommandResult.Applied })
            assertEquals(1, results.count { result -> result is CommandResult.Rejected })
            assertEquals(
                RejectionReason.RevisionMismatch::class.java,
                rejected(results.single { result -> result is CommandResult.Rejected }).javaClass,
            )
            assertEquals(revision(1L), gateway.runtimeState.documentState.revision)
            val first = colorAt(gateway.runtimeState.documentState.snapshot, position(0, 0))
            val second = colorAt(gateway.runtimeState.documentState.snapshot, position(1, 0))
            assertEquals(first, second)
            assertTrue(first == red || first == green)
            assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
            val committed = gateway.runtimeState.documentState
            applied(gateway.execute(UndoCommand.create(committed.id, committed.revision)))
            assertEquals(initial, gateway.runtimeState.documentState)
            assertEquals(HistoryAvailability.RedoAvailable, gateway.runtimeState.historyAvailability)
        }
    }

    private fun executeConcurrently(
        gateway: CommandGateway,
        firstCommand: ApplyStrokeCommand,
        secondCommand: ApplyStrokeCommand,
    ): List<CommandResult> {
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        return try {
            val firstResult =
                executor.submit<CommandResult> {
                    start.await()
                    gateway.execute(firstCommand)
                }
            val secondResult =
                executor.submit<CommandResult> {
                    start.await()
                    gateway.execute(secondCommand)
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

    private fun command(
        state: DocumentState,
        stroke: io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke,
    ): ApplyStrokeCommand = ApplyStrokeCommand.create(state.id, state.revision, stroke)

    private fun region(
        state: DocumentState,
        origin: io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition,
        size: io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize,
    ): PixelRegion =
        when (val result = PixelRegion.create(state.size, origin, size)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Test region was rejected: ${result.rejection}")
        }

    private fun appliedSnapshot(result: PixelPatchApplicationResult) =
        when (result) {
            is PixelPatchApplicationResult.Applied -> result.snapshot
            is PixelPatchApplicationResult.Rejected -> fail("Test inverse was rejected: ${result.rejection}")
        }

    private companion object {
        const val CONCURRENCY_ATTEMPTS: Int = 20
        const val TIMEOUT_SECONDS: Long = 5L
    }
}

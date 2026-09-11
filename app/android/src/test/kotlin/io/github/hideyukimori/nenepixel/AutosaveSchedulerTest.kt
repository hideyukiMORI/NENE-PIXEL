package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStoragePort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInitializationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal class AutosaveSchedulerTest {
    @Test
    fun schedulerObservesCoalescedCapturesWhilePublicationIsAwaited() =
        runBlocking {
            val recovery = BlockingRecoveryRecordPort()
            val fixture = SchedulerFixture(recovery)
            fixture.initialize()
            val clock = AtomicLong(START)
            val policy = AutosavePolicy(quietMillis = 2_000L, latencyCapMillis = 5_000L)
            fixture.applyNext()
            val scheduler = AutosaveScheduler(fixture.workflow, policy, clock::get)
            val schedulerJob = scheduler.launchIn(this)

            try {
                scheduler.flush()
                recovery.awaitPublication()

                clock.set(START + CAPTURE_OFFSET)
                fixture.applyNext()
                delay(OBSERVER_SETTLE_MILLIS)
                clock.set(START + COALESCED_OFFSET)
                val latestDocument = fixture.applyNext()
                delay(OBSERVER_SETTLE_MILLIS)

                clock.set(START + CAPTURE_OFFSET + policy.latencyCapNanos)
                assertEquals(1, recovery.publicationCount)
                recovery.completePublication()

                assertEquals(latestDocument, recovery.awaitPublication())
                assertEquals(2, recovery.publicationCount)
            } finally {
                recovery.completePublication()
                schedulerJob.cancelAndJoin()
            }
        }

    @Test
    fun immediatePublicationFailureDoesNotRetryUntilAFlushOrGateChange() =
        runBlocking {
            val recovery = GenerationExhaustedRecoveryRecordPort()
            val fixture = SchedulerFixture(recovery)
            fixture.initialize()
            fixture.applyNext()
            val scheduler = AutosaveScheduler(fixture.workflow, AutosavePolicy(1L, 2L))
            val schedulerJob = scheduler.launchIn(this)

            try {
                scheduler.flush()
                recovery.awaitPublication()
                delay(FAILURE_OBSERVATION_MILLIS)

                assertEquals(1, recovery.publicationCount)
            } finally {
                schedulerJob.cancelAndJoin()
            }
        }

    private companion object {
        const val START: Long = 1_000_000_000_000L
        const val CAPTURE_OFFSET: Long = 100_000_000L
        const val COALESCED_OFFSET: Long = 200_000_000L
        const val OBSERVER_SETTLE_MILLIS: Long = 25L
        const val FAILURE_OBSERVATION_MILLIS: Long = 50L
    }
}

private class SchedulerFixture(
    recovery: RecoveryRecordPort,
) {
    private val runtime = createEditorRuntime()
    val workflow =
        EditorPersistenceWorkflow.create(
            runtime,
            object : ProjectStoragePort {
                override suspend fun save(document: DocumentState): ProjectSaveOutcome = ProjectSaveOutcome.Cancelled

                override suspend fun load(): ProjectLoadOutcome = ProjectLoadOutcome.Cancelled
            },
            recovery,
        )
    private var nextX: Int = 0

    suspend fun initialize() {
        check(workflow.initializeRecovery() == RecoveryInitializationResult.Ready)
    }

    fun applyNext(): DocumentState {
        val current = runtime.state.documentState
        val position =
            PixelPosition.create(
                PixelX.create(nextX).schedulerRequired(),
                PixelY.create(0).schedulerRequired(),
            )
        val color =
            runtime.palette
                .entries()
                .first()
                .color
        val stroke =
            Stroke
                .create(current.size, listOf(position), StrokeEffect.Paint(color))
                .schedulerRequired()
        val result = runtime.execute(ApplyStrokeCommand.create(current.id, current.revision, stroke))
        check(result is CommandResult.Applied)
        nextX += 1
        return runtime.state.documentState
    }
}

private class BlockingRecoveryRecordPort : RecoveryRecordPort {
    private val publications = Channel<DocumentState>(Channel.UNLIMITED)
    private val completions = Channel<Unit>(Channel.UNLIMITED)
    private val completedPublicationCount = AtomicInteger(0)
    val publicationCount: Int
        get() = completedPublicationCount.get()

    override suspend fun inspect(): RecoveryInspection = RecoveryInspection.Missing

    override suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome =
        RecoveryRetirementOutcome.GenerationExhausted

    override suspend fun publishCandidate(
        expected: ExpectedRecoveryLineage,
        document: DocumentState,
    ): RecoveryPublicationOutcome {
        completedPublicationCount.incrementAndGet()
        publications.send(document)
        completions.receive()
        return RecoveryPublicationOutcome.Published(nextGeneration(expected))
    }

    suspend fun awaitPublication(): DocumentState = withTimeout(1_000L) { publications.receive() }

    fun completePublication() {
        completions.trySend(Unit)
    }

    private fun nextGeneration(expected: ExpectedRecoveryLineage): RecoveryGeneration {
        val value =
            when (expected) {
                ExpectedRecoveryLineage.Missing -> 1L
                is ExpectedRecoveryLineage.Present -> expected.generation.value + 1L
            }
        return when (val result = RecoveryGeneration.create(value)) {
            is RecoveryGenerationResult.Created -> result.generation
            RecoveryGenerationResult.Rejected -> error("Expected a valid recovery generation")
        }
    }
}

private class GenerationExhaustedRecoveryRecordPort : RecoveryRecordPort {
    private val publications = Channel<Unit>(Channel.UNLIMITED)
    private val completedPublicationCount = AtomicInteger(0)
    val publicationCount: Int
        get() = completedPublicationCount.get()

    override suspend fun inspect(): RecoveryInspection = RecoveryInspection.Missing

    override suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome =
        RecoveryRetirementOutcome.GenerationExhausted

    override suspend fun publishCandidate(
        expected: ExpectedRecoveryLineage,
        document: DocumentState,
    ): RecoveryPublicationOutcome {
        completedPublicationCount.incrementAndGet()
        publications.send(Unit)
        return RecoveryPublicationOutcome.GenerationExhausted
    }

    suspend fun awaitPublication() {
        withTimeout(1_000L) { publications.receive() }
    }
}

private fun <T> DomainValueResult<T>.schedulerRequired(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Autosave scheduler fixture was rejected: $rejection")
    }

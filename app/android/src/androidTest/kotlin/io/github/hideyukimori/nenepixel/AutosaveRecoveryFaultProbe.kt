package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveLastOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** One real scheduler, with its existing clock seam; no copied scheduling algorithm (#247). */
internal class AutosaveRecoveryFaultProbe(
    private val fixture: RecoveryFaultFixture,
) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val owner = SupervisorJob()
    private val failure = AtomicReference<Throwable?>(null)
    private val scope =
        CoroutineScope(owner + dispatcher + CoroutineExceptionHandler { _, cause -> failure.set(cause) })
    private val reads = AtomicInteger()
    private val failureObserved = CompletableDeferred<Unit>()

    // Short policy is only a suspension oracle; this does not accept the production 1-s/5-s timing.
    private val policy = AutosavePolicy(quietMillis = 20L, latencyCapMillis = 100L)
    private val scheduler = AutosaveScheduler(fixture.workflow, policy, ::clockNanos)
    private val schedulerJob = scheduler.launchIn(scope)

    private fun flush() {
        scheduler.flush()
    }

    fun launchExport() = scope.async { fixture.workflow.exportPng() }

    suspend fun settle() {
        barrier()
    }

    suspend fun flushAndObserve() {
        val before = reads.get()
        flush()
        withTimeout(BUDGET_MILLIS) {
            while (reads.get() == before && schedulerJob.isActive) delay(1L)
        }
        barrier()
        assertActive()
    }

    /**
     * The counted platform clock advances with the real coroutine timer. This 250-ms window
     * exceeds the 100-ms test cap and only observes absence of clock/I/O activity; it never
     * supplies a publication-completion oracle.
     */
    suspend fun observeNoSpin(stage: String) {
        withTimeout(BUDGET_MILLIS) { failureObserved.await() }
        barrier()
        val before = reads.get()
        val starts = fixture.file.startCalls
        val opens = fixture.file.readCalls
        val started = System.nanoTime()
        delay(OBSERVATION_MILLIS)
        barrier()
        val elapsed = System.nanoTime() - started
        assertTrue("observation crosses the test cap", elapsed >= policy.latencyCapNanos)
        assertActive()
        assertEquals("scheduler clock activity within bounded window", before, reads.get())
        assertEquals(starts, fixture.file.startCalls)
        assertEquals(opens, fixture.file.readCalls)
        fixture.record(stage, "clockCalls=$before elapsedNanos=$elapsed observationMillis=$OBSERVATION_MILLIS")
    }

    suspend fun close() {
        fixture.ports.export.release()
        try {
            withTimeout(BUDGET_MILLIS) { owner.cancelAndJoin() }
        } finally {
            dispatcher.close()
        }
    }

    private fun clockNanos(): Long {
        // An instrumentation budget makes a broken non-suspending loop terminate as a test failure.
        check(reads.incrementAndGet() <= CLOCK_CALL_BUDGET) { "Scheduler exceeded the clock observation budget" }
        val outcome = fixture.workflow.autosave.value.lastOutcome
        if (outcome is AutosaveLastOutcome.Failed || outcome is AutosaveLastOutcome.Uncertain) {
            failureObserved.complete(Unit)
        }
        return System.nanoTime()
    }

    private suspend fun barrier() {
        // The same serial dispatcher drains the failure continuation and already-queued observer.
        withTimeout(BUDGET_MILLIS) {
            withContext(dispatcher) { }
            withContext(dispatcher) { }
        }
    }

    private fun assertActive() {
        assertNull("scheduler failed", failure.get())
        assertTrue("scheduler remains active", schedulerJob.isActive)
    }

    private companion object {
        const val BUDGET_MILLIS: Long = 10_000L
        const val OBSERVATION_MILLIS: Long = 250L
        const val CLOCK_CALL_BUDGET: Int = 1_000
    }
}

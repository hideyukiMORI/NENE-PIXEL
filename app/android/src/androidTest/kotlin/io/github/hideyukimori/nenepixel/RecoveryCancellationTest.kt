package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveRequestResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/** Actual Android partial writes with the real adapter, writer, verifier and runtime/workflow (#249). */
internal class RecoveryCancellationTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    @Test
    fun callerCancellationCompletesExactHeldC2AndPreservesNewerC3() =
        runBlocking {
            runFixture("caller-cancellation") { fixture ->
                fixture.prepare()
                val second = fixture.capture(1, 260)
                val gate = fixture.file.hold(cancelFromPort = false)
                val caller = fixture.launchCaller(this)
                gate.awaitEntered()
                fixture.assertHeld(second, second)
                fixture.record("c2-partial-before-caller-cancel")
                val marker = CancellationException("#249 caller cancellation while actual write is held")
                caller.cancel(marker)
                assertTrue(caller.isCancelled)
                assertFalse(caller.isCompleted)
                val newer = fixture.capture(0, 120)
                assertNotEquals(fixture.first.token, second.token)
                assertNotEquals(second.token, newer.token)
                fixture.assertHeld(second, newer)
                fixture.record(
                    "cancelled-c2-held-c3-pending",
                    "callerCancelled=${caller.isCancelled} callerCompleted=${caller.isCompleted}",
                )
                gate.release()
                val cancellation = awaitCancellation(caller)
                assertIdentity(marker, cancellation)
                assertTrue(caller.isCancelled && caller.isCompleted)
                assertEquals(0, fixture.file.throwCalls)
                assertEquals(0, fixture.file.failCalls)
                fixture.assertPublished(second, 3L, newer)
                fixture.record("cancelled-caller-coherent-c2", "publishedIsC2=true pendingIsC3=true currentIsC3=true")
                fixture.file.removeFault()
                fixture.publish(newer, 4L)
                assertEquals(4, fixture.file.startCalls)
                fixture.record("released-lease-published-c3", "publishedIsC3=true pendingEmpty=true")
            }
        }

    @Test
    fun portCancellationRollsBackActualPartialWriteRethrowsAndAdmitsExactC2Retry() =
        runBlocking {
            runFixture("port-cancellation") { fixture ->
                fixture.prepare()
                val second = fixture.capture(1, 260)
                val gate = fixture.file.hold(cancelFromPort = true)
                val caller = fixture.launchCaller(this)
                gate.awaitEntered()
                fixture.assertHeld(second, second)
                assertTrue(caller.isActive)
                assertFalse(caller.isCancelled)
                fixture.record("c2-partial-before-port-cancel", "callerActive=true callerCancelled=false")
                gate.release()
                val cancellation = awaitCancellation(caller)
                assertIdentity(fixture.file.portCancellation, cancellation)
                assertTrue(caller.isCancelled && caller.isCompleted)
                fixture.assertRolledBack(second)
                fixture.record("port-cancelled-c1-restored", "pendingIsC2=true publishedIsC1=true markerIdentity=true")
                fixture.file.removeFault()
                fixture.publish(second, 3L)
                assertEquals(4, fixture.file.startCalls)
                assertArrayEquals(fixture.file.request(3).readBytes(), fixture.file.baseFile.readBytes())
                fixture.record("released-lease-published-c2-retry", "publishedIsC2=true pendingEmpty=true")
            }
        }

    private suspend fun awaitCancellation(caller: Deferred<AutosaveRequestResult>): Throwable {
        val failure =
            withTimeout(WAIT_MILLIS) {
                caller.join()
                runCatching { caller.await() }.exceptionOrNull()
            }
        assertTrue("publication caller must actually raise cancellation", failure is CancellationException)
        return requireNotNull(failure)
    }

    private fun assertIdentity(
        marker: CancellationException,
        failure: Throwable,
    ) {
        // Coroutine stack recovery may wrap an exception; the actual injected object must remain in its chain.
        assertTrue(
            "the exact cancellation marker is retained",
            generateSequence(failure) { it.cause }.any { it === marker },
        )
    }

    private suspend fun runFixture(
        case: String,
        block: suspend CoroutineScope.(RecoveryCancellationFixture) -> Unit,
    ) = supervisorScope {
        val fixture = RecoveryCancellationFixture(case)
        // Test resource boundary: preserve the original assertion/cancellation and attach cleanup failures.
        val outcome = runCatching { block(fixture) }
        val cleanup = runCatching { fixture.close() }
        val primary = outcome.exceptionOrNull()
        if (primary != null) {
            cleanup.exceptionOrNull()?.let(primary::addSuppressed)
            throw primary
        }
        cleanup.getOrThrow()
    }

    private companion object {
        const val WAIT_MILLIS: Long = 10_000L
    }
}

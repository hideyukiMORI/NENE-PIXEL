package io.github.hideyukimori.nenepixel.core.application.persistence

import kotlinx.coroutines.CompletableDeferred

/** Holds one [FakeUnderlayMemoryPort.recall] until the test releases it. */
internal class FakeUnderlayRecallGate {
    private val started = CompletableDeferred<Unit>()
    private val released = CompletableDeferred<Unit>()

    val hasStarted: Boolean
        get() = started.isCompleted

    /** Suspends until the held recall has been called. */
    suspend fun awaitStarted() {
        started.await()
    }

    /** Lets the held recall read the store and answer. */
    fun release() {
        released.complete(Unit)
    }

    /** Called by the fake: marks the recall as started and waits for [release]. */
    suspend fun pass() {
        started.complete(Unit)
        released.await()
    }
}
